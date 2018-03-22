import akka.actor.ActorSystem
import akka.pattern.{Backoff, BackoffSupervisor}
import stream.StreamWrapperActor
import scala.concurrent.duration._

import common.SqsSettings
object Boot extends App {
  require(SqsSettings.queueURL.nonEmpty)

  implicit val system: ActorSystem = ActorSystem("aws-sqsd")

  val supervisorProps = BackoffSupervisor.props(
    Backoff.onStop(
      childProps = StreamWrapperActor.props,
      childName = StreamWrapperActor.name,
      minBackoff = 3.seconds,
      maxBackoff = 1.minute,
      randomFactor = 0.2
    )
  )
  val supervisor = system.actorOf(supervisorProps, name = "streamActorSupervisor")

}
