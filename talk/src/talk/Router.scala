package org.maidagency.talk.router

import zio.http.*
import zio.*
import org.maidagency.talk.logging.*
import org.maidagency.talk.register.*

case class Router(
    dbclient: scalasql.DbClient.DataSource
):
  val routes =
    Routes(
      Method.GET / Root -> handler(Response.text("-")),
      Method.GET / "api" / "register" ->
        handler: (req: Request) =>
          for
            _ <- Logger.info("got register")
            reg <- Register.register(req, dbclient)
          yield reg
    )

object Router:
  val layer = ZLayer.derive[Router] // what????
