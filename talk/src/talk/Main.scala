package org.maidagency.talk.main

import zio.*
import zio.Console.*
import zio.http.*
import org.maidagency.talk.logging.*
import org.maidagency.talk.database.*
import org.maidagency.talk.generator.*
import org.maidagency.talk.router.*

// abandon all hopes, ye who enters here

object Main extends ZIOAppDefault:

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
