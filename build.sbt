import sbt.Resolver

name := "aws-sqsd"

version := "0.1"

scalaVersion := "2.12.5"

resolvers ++= Seq(
  Resolver.bintrayRepo("hseeberger", "maven"),
)

licenses += ("MIT", url("http://opensource.org/licenses/MIT"))

organization := "br.com.gzvr"
crossScalaVersions := Seq("2.11.8", "2.12.5")

enablePlugins(JavaAppPackaging)

fork in run := true

val akkaV = "2.5.11"
val akkaHttpV = "10.1.0"

lazy val commonDependencies = Seq(
  "org.slf4j" % "slf4j-api" % "1.7.16",
  "ch.qos.logback" % "logback-classic" % "1.1.3",
  "joda-time" % "joda-time" % "2.8",
  "org.scalacheck" %% "scalacheck" % "1.13.4" % "test",
  "org.scalamock" %% "scalamock-scalatest-support" % "3.4.2" % "test",
  "org.scalatest" %% "scalatest" % "3.0.1" % "test"
)

lazy val akkaDependencies = Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaV,
  "com.typesafe.akka" %% "akka-http-jackson" % akkaHttpV,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpV,
  "com.typesafe.akka" %% "akka-slf4j" % akkaV,
  "com.typesafe.akka" %% "akka-stream" % akkaV,
  "com.typesafe.akka" %% "akka-stream-testkit" % akkaV,
  "com.typesafe.akka" %% "akka-testkit" % akkaV
)

lazy val akkaHttpDependencies = Seq(
  "com.typesafe.akka" %% "akka-http" % akkaHttpV,
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpV % "test"
)

lazy val akkaComplementsDependencies = Seq(
  "de.heikoseeberger" %% "akka-http-json4s" % "1.11.0",
  "de.heikoseeberger" %% "akka-sse" % "3.0.0"
)

lazy val alpakkaDependencies = Seq(
  "com.lightbend.akka" %% "akka-stream-alpakka-sqs" % "0.17"
)

libraryDependencies ++= (
  commonDependencies ++
    akkaDependencies ++
    akkaHttpDependencies ++
    akkaComplementsDependencies ++
    alpakkaDependencies
  )
