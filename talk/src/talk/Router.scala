package org.maidagency.talk.router

import org.maidagency.talk.logging.*
import org.maidagency.talk.login.*
import org.maidagency.talk.register.*
import zio.*
import zio.concurrent.ConcurrentMap
import zio.http.*

case class Router(
    dbclient: scalasql.DbClient.DataSource,
    tokenStore: ConcurrentMap[String, String]
):
  val routes =
    Routes(
      Method.GET / Root -> handler(Response.text("-")),
      Method.POST / "api" / "register" ->
        handler: (req: Request) =>
          for
            _ <- Logger.info("got register")
            reg <- Register.register(req, dbclient)
          yield reg,
      Method.GET / "api" / "login" ->
        handler: (req: Request) =>
          for
            _ <- Logger.info("got login")
            response <- Login.login(req, dbclient, tokenStore)
          yield response
    )

object Router:
  val layer = ZLayer.derive[Router] // what????
