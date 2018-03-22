package common

import java.net.URL

import com.typesafe.config.ConfigFactory

object SqsSettings {
  lazy val config = ConfigFactory.load()

  lazy val workerHttpUrl = config.getString("app.workerHttpUrl")
  lazy val workerHealthUrl = config.getString("app.workerHealthUrl")

  private lazy val httpUrl = new URL(workerHttpUrl)
  private lazy val healthUrl = new URL(workerHealthUrl)

  lazy val region = config.getString("app.region")
  lazy val workerHttpProtocol = httpUrl.getProtocol
  lazy val workerHttpHost = httpUrl.getHost
  lazy val workerHttpPort = httpUrl.getPort
  lazy val workerHttpPath = httpUrl.getPath
  lazy val workerHttpRequestContentType = config.getString("app.workerHttpRequestContentType")

  lazy val waitTimeSeconds = config.getInt("app.waitTimeSeconds")
  lazy val workerTimeout = config.getInt("app.workerTimeout")
  lazy val workerConcurrency = config.getInt("app.workerConcurrency")

  lazy val workerHealthProtocol = healthUrl.getProtocol
  lazy val workerHealthHost = healthUrl.getHost
  lazy val workerHealthPort = healthUrl.getPort
  lazy val workerHealthPath = healthUrl.getPath

  lazy val workerHealthWaitTime = config.getInt("app.workerHealthWaitTime")

  lazy val queueURL = config.getString("app.queueURL")
}
