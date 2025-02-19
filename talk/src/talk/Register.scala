package org.maidagency.talk.register

import zio.http.*
import zio.*
import org.maidagency.talk.logging.*
import org.maidagency.talk.generator.*
import org.maidagency.talk.database.*
import scalasql.Table
import scalasql.SqliteDialect.*
import scalasql.DbClient.*

enum RegisterError:
  case FailedToParseJson
  case FailedToReadRequest
  case DatabaseError
  case UsernameExist

object Register:
  def createUser(username: String, dbconn: DataSource) =
    Generator
      .generate() // generates a snowflake id
      .map: id => // this DSL is CRAZY
        // not that i got any other choices...
        Users.insert.columns(
          _.name := username,
          _.id := id
        )
      .flatMap: query => // runs the query insert with high complexity
        ZIO.attemptBlocking(dbconn.transaction(db => db.run(query)))

  // returns true wrapped in a ZIO if the user is not found
  // (which means we can register the user as normal)
  def getUser(username: String, dbconn: DataSource) =
    ZIO
      .succeed(Users.select.filter(_.name === username))
      .flatMap: query =>
        ZIO
          .attemptBlocking(dbconn.transaction(db => db.run(query)))
          .catchAll(_ => ZIO.fail(RegisterError.DatabaseError))
      .tap(_ => Logger.info("got user"))
      .flatMap: user =>
        if user.length == 1 then ZIO.fail(RegisterError.UsernameExist)
        else if user.length == 0 then ZIO.succeed(true)
        else ZIO.fail(RegisterError.DatabaseError)
        // ^ probably demands a bug report on sql's front
        // not enforcing primary key correctly...
        // should NOT ever happen, i hope...
      .tap(stat => Logger.info(s"$username search status: $stat"))

  def register(req: Request, dbconn: DataSource) =
    ZIO
      .succeed(req)
      .flatMap: req =>
        req.body.asString.catchAll(_ =>
          ZIO.fail(RegisterError.FailedToReadRequest)
        )
      .tap: req =>
        Logger.info(req)
      .map: result =>
        fabric.io.JsonParser(result, fabric.io.Format.Json)
      .flatMap: json =>
        json.get("username").map(_.asString) match
          case None =>
            ZIO.fail(RegisterError.FailedToParseJson)
          case Some(value) =>
            ZIO.succeed(value)
      .tap(getUser(_, dbconn))
      .map: str =>
        Response.text(str)
      .catchAll: err =>
        val response = err match
          case RegisterError.FailedToParseJson =>
            Response.text("failed to parse json")
          case RegisterError.FailedToReadRequest =>
            Response.text("failed to read request")
          case RegisterError.DatabaseError =>
            Response.text("internal server error")
          case RegisterError.UsernameExist =>
            Response.text("username exists")
        ZIO
          .succeed(response)
          .tap(_ => Logger.error(s"got an error: $err"))
