import mill._
import mill.scalalib._

object talk extends ScalaModule {
  def scalaVersion = "3.4.2"
  def ivyDeps = Agg(
    ivy"dev.zio::zio:2.1.15",
    ivy"dev.zio::zio-http:3.0.1",
    ivy"com.outr::scribe:3.16.0",
    ivy"com.outr::scribe-slf4j:3.16.0",
    ivy"com.outr::scalapass:1.2.8",
    ivy"dev.zio::zio-logging-slf4j-bridge:2.4.0",
    ivy"com.softwaremill.common::id-generator:1.4.0",
    ivy"com.lihaoyi::scalasql:0.1.15",
    ivy"org.typelevel::fabric-core:1.15.9",
    ivy"org.typelevel::fabric-io:1.15.9",
    ivy"org.xerial:sqlite-jdbc:3.49.0.0"
  )
}
