package stream

import java.net.URL

import scala.concurrent.Future
import scala.concurrent.duration._
import akka.Done
import akka.actor.{Actor, ActorLogging, ActorSystem, PoisonPill, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes.{Found, OK}
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.alpakka.sqs.scaladsl.{SqsAckSink, SqsSource}
import akka.stream.alpakka.sqs.{MessageAction, MessageAttributeName, SqsSourceSettings}
import akka.stream.scaladsl.{Balance, Broadcast, Flow, GraphDSL, Merge, Sink, Source, Zip}
import akka.stream._
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.sqs.model.Message
import com.amazonaws.services.sqs.{AmazonSQSAsync, AmazonSQSAsyncClientBuilder}
import common.SqsSettings
import stream.StreamWrapperActor.{Healthcheck, Start}

import scala.collection.JavaConverters._

class StreamWrapperActor extends Actor with ActorLogging {

  implicit val system: ActorSystem = context.system

  val decider: Supervision.Decider = {
    case e: Throwable =>
      log.error(e, "Stream failed, stopping actor")
      self ! PoisonPill
      Supervision.Stop
    case _ =>
      log.error("Stream failed. Unknown error. Stopping actor")
      self ! PoisonPill
      Supervision.Stop
  }

  implicit val materializer: ActorMaterializer = {
    ActorMaterializer(ActorMaterializerSettings(context.system))
  }
  val client: AmazonSQSAsync = AmazonSQSAsyncClientBuilder
    .standard()
    .withCredentials(new DefaultAWSCredentialsProviderChain())
    .withEndpointConfiguration(new EndpointConfiguration(SqsSettings.queueURL, SqsSettings.region))
    .build()

  val sqsSourceSettings = SqsSourceSettings(
    messageAttributeNames = Seq(MessageAttributeName("All")),
    waitTimeSeconds = SqsSettings.waitTimeSeconds,
    maxBufferSize = SqsSettings.workerConcurrency,
    maxBatchSize = 10
  )

  val contentType = ContentType.parse(SqsSettings.workerHttpRequestContentType) match {
    case Right(content) => content
    case _ => ContentTypes.`application/json`
  }

  val mediaType = MediaType.parse(SqsSettings.workerHttpRequestContentType) match {
    case Right(media) => media
    case _ => ContentTypes.`application/json`
  }

  val source = SqsSource(SqsSettings.queueURL, sqsSourceSettings)(client)
  val sharedKillSwitch = KillSwitches.shared("my-kill-switch")

  override def preStart(): Unit = {
    log.info(s"Starting actor stream for SQS ${SqsSettings.queueURL}")
    self ! Healthcheck
  }

  def receive = {
    case Healthcheck =>
      log.info("Checking for application is ready")
      implicit val dispatcher = system.dispatchers.lookup("http-dispatcher")

      Http().singleRequest(HttpRequest(uri = SqsSettings.workerHealthUrl)).onComplete {
        case util.Success(response) if response.status == OK || response.status == Found =>
          response.discardEntityBytes()
          context.become(start)
          system.scheduler.scheduleOnce(SqsSettings.workerHealthWaitTime.seconds, self, Healthcheck)
          self ! Start
        case util.Success(response) =>
          response.discardEntityBytes()
          log.info("Application isn't ready. Restarting")
          self ! PoisonPill
        case _ =>
          log.info("Application isn't ready. Restarting")
          self ! PoisonPill
      }

    case _ =>
      self ! PoisonPill
  }

  def start: Receive = {
    case Healthcheck =>
      implicit val dispatcher = system.dispatchers.lookup("http-dispatcher")
      Http().singleRequest(HttpRequest(uri = SqsSettings.workerHealthUrl)).onComplete {
        case util.Success(response) if response.status == OK || response.status == Found =>
          response.discardEntityBytes()
          system.scheduler.scheduleOnce(SqsSettings.workerHealthWaitTime.seconds, self, Healthcheck)
        case util.Success(response) =>
          response.discardEntityBytes()
          log.info("Application isn't ready. Restarting")
          sharedKillSwitch.abort(new RuntimeException("Application isn't ready. Restarting"))
          self ! PoisonPill
        case _ =>
          log.info("Application isn't ready. Restarting")
          sharedKillSwitch.abort(new RuntimeException("Application isn't ready. Restarting"))
          self ! PoisonPill
      }

    case Start =>
      log.info("Application Ok. Starting consuming!!!")
      val connectionSettings = ConnectionPoolSettings(SqsSettings.config).withIdleTimeout(SqsSettings.workerTimeout.seconds)

      val url = new URL(SqsSettings.workerHttpUrl)
      val host = url.getHost
      val protocol = url.getProtocol

      val httpFlow = if (protocol.startsWith("https")) Http().cachedHostConnectionPoolHttps[String](host = host, port = SqsSettings.workerHttpPort, settings = connectionSettings)
      else Http().cachedHostConnectionPool[String](host = host, port = SqsSettings.workerHttpPort, settings = connectionSettings)

      val flow = Flow.fromGraph(GraphDSL.create() { implicit builder =>
        import GraphDSL.Implicits._

        val flowHttp = Flow[Message]
          .map { msg =>
            val body = msg.getBody

            val attrs = msg.getMessageAttributes.asScala
              .toList
              .filterNot(_._2.getDataType == "Binary")
              .map { case (name, value) => RawHeader(s"X-Aws-Sqsd-Attr-$name", Option(value.getStringValue).getOrElse("")) }

            val headers = List(
              `User-Agent`("aws-sqsd/1.1"),
              RawHeader("X-Aws-Sqsd-Msgid", msg.getMessageId)
            ) ++ attrs

            val httpRequest: (HttpRequest, String) = {
              val entity = HttpEntity(body).withContentType(contentType)
              HttpRequest(method = HttpMethods.POST, uri = SqsSettings.workerHttpPath, headers = headers, entity = entity) -> body
            }

            implicit val dispatcher = system.dispatchers.lookup("http-dispatcher")

            Source
              .single(httpRequest)
              .via(httpFlow)
              .runWith(Sink.head)
              .map { tuple =>
                val responseTry = tuple._1
                responseTry match {
                  case util.Success(response) =>
                    response.discardEntityBytes()
                    response.status match {
                      case OK =>
                        log.info(s"POST $OK. Deleting message ${msg.getMessageId}")
                        MessageAction.delete
                      case other =>
                        log.error(s"Error $other when post body $body. Leaving message on queue")
                        MessageAction.ignore
                    }
                  case util.Failure(e) =>
                    log.error(e, s"Error when post body $body. Leaving message on queue")
                    MessageAction.ignore
                }
              }
          }

        val balancer = builder.add(Balance[Message](SqsSettings.workerConcurrency))
        val merge = builder.add(Merge[(Message, MessageAction)](SqsSettings.workerConcurrency))

        for (i <- 0 until SqsSettings.workerConcurrency) {
          val broadcast = builder.add(Broadcast[Message](2))
          val zip = builder.add(Zip[Message, MessageAction])

          balancer.out(i) ~> broadcast ~> zip.in0
          broadcast ~> flowHttp.mapAsync(1)(action => action) ~> zip.in1
          zip.out ~> merge
        }

        FlowShape(balancer.in, merge.out)
      })

      val done: Future[Done] = source
        .via(sharedKillSwitch.flow)
        .via(flow)
        .runWith(SqsAckSink(SqsSettings.queueURL)(client))

      import context.dispatcher

      done.onComplete {
        case util.Failure(e) =>
          log.error(e, "Stream failed. Restarting")
          self ! PoisonPill
        case _ =>
      }
  }
}

object StreamWrapperActor {
  val name = "stream-wrapper-actor"

  def props: Props = Props[StreamWrapperActor]

  case object Start

  case object Healthcheck

}
