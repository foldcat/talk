package org.maidagency.talk.main

import org.maidagency.talk.database.*
import org.maidagency.talk.generator.*
import org.maidagency.talk.logging.*
import org.maidagency.talk.router.*
import zio.Console.*
import zio.*
import zio.http.*

// abandon all hopes, ye who enters here

object Main extends ZIOAppDefault:

  override val bootstrap =
    Runtime.enableLoomBasedExecutor
      ++ Runtime.enableLoomBasedBlockingExecutor
      ++ Runtime.removeDefaultLoggers
      ++ Logger.addSimpleLogger

  val config = Server.Config.default
    .port(29834)

  val configLayer = ZLayer.succeed(config)

  def run =
    for
      _ <- Logger.setup()
      _ <- Logger.info("starting talk")

      dblayer = ZLayer.fromZIO:
        Database
          .connect()
          .tap(Database.setup)

      // d*pendency injection
      _ <- ZIO
        .serviceWithZIO[Router](router => Server.serve(router.routes))
        .provide(configLayer, Server.live, Router.layer, dblayer)
    yield ()
