package org.maidagency.talk.register

import zio.http.*
import zio.*
import org.maidagency.talk.logging.*
import org.maidagency.talk.generator.*
import org.maidagency.talk.database.*
import org.maidagency.talk.hashing.*
import scalasql.Table
import scalasql.SqliteDialect.*
import scalasql.DbClient.*

enum RegisterError:
  case FailedToParseJson
  case FailedToReadRequest
  case DatabaseError
  case UsernameExist

case class RegObj(
    username: String,
    password: String
)

object Register:
  def createUser(user: RegObj, dbconn: DataSource) =
    for
      id <- Generator.generate()
      hashed_password <- Hash.hash(user.password)

      query = Users.insert.columns(
        _.username := user.username,
        _.id := id,
        _.password := hashed_password
      )

      _ <- ZIO
        .attemptBlocking(dbconn.transaction(db => db.run(query)))
        .catchAll(_ => ZIO.fail(RegisterError.DatabaseError))
    yield ()

  // returns true wrapped in a ZIO if the user is not found
  // (which means we can register the user as normal)
  def getUser(username: String, dbconn: DataSource) =
    ZIO
      .succeed(Users.select.filter(_.username === username))
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

  def formatJson(json: fabric.Json) =
    for // im sure theres a better way to do this
      username <- json.get("username").map(_.asString) match
        case None =>
          ZIO.fail(RegisterError.FailedToParseJson)
        case Some(value) =>
          ZIO.succeed(value)
      password <- json.get("password").map(_.asString) match
        case None =>
          ZIO.fail(RegisterError.FailedToParseJson)
        case Some(value) =>
          ZIO.succeed(value)
    yield RegObj(username, password)

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
      .flatMap(formatJson)
      .tap((user: RegObj) => getUser(user.username, dbconn))
      .tap((user: RegObj) => createUser(user, dbconn))
      .map: _ =>
        Response.text("registeration success")
      .catchAll: err => // if we somehow fail
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
