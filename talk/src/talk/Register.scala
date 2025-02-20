package org.maidagency.talk.register

import fabric.*
import org.maidagency.talk.database.*
import org.maidagency.talk.generator.*
import org.maidagency.talk.hashing.*
import org.maidagency.talk.logging.*
import scalasql.DbClient.*
import scalasql.SqliteDialect.*
import scalasql.Table
import zio.*
import zio.http.*

enum RegisterError:
  case FailedToParseJson(json: String)
  case FailedToReadRequest
  case DatabaseError
  case UsernameExist(username: String)

case class RegObj(
    username: String,
    password: String
)

object Register:
  def createUser(user: RegObj, dbconn: DataSource) =
    for
      id <- Generator.generateId()
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
      .flatMap: user =>
        if user.length == 1 then ZIO.fail(RegisterError.UsernameExist(username))
        else if user.length == 0 then ZIO.succeed(true)
        else ZIO.fail(RegisterError.DatabaseError)
    // ^ probably demands a bug report on sql's front
    // not enforcing primary key correctly...
    // should NOT ever happen, i hope...

  def formatJson(input: String) =
    for // im sure theres a better way to do this
      json <- ZIO.succeed(fabric.io.JsonParser(input, fabric.io.Format.Json))
      username <- json.get("username").map(_.asString) match
        case None =>
          ZIO.fail(RegisterError.FailedToParseJson(input))
        case Some(value) =>
          ZIO.succeed(value)
      password <- json.get("password").map(_.asString) match
        case None =>
          ZIO.fail(RegisterError.FailedToParseJson(input))
        case Some(value) =>
          ZIO.succeed(value)
    yield RegObj(username, password)

  def logSuccess(username: String) =
    Logger.info(s"$username successfully registered")

  def register(req: Request, dbconn: DataSource) =
    req.body.asString
      .catchAll(_ => ZIO.fail(RegisterError.FailedToReadRequest))
      .flatMap(formatJson)
      .tap(user => getUser(user.username, dbconn))
      .tap(user => createUser(user, dbconn))
      .tap(user => logSuccess(user.username))
      .map: _ =>
        Response.text(obj("success" -> true).toString())
      .catchAll: err => // if we somehow fail
        val (errorMsg, addInfo) = err match
          case RegisterError.FailedToParseJson(input) =>
            ("failed to parse json", Some(input))
          case RegisterError.FailedToReadRequest =>
            ("failed to read request", None)
          case RegisterError.DatabaseError =>
            ("internal server error", None)
          case RegisterError.UsernameExist(username) =>
            ("username exists", Some(username))

        ZIO
          .succeed(obj("success" -> false, "reason" -> errorMsg).toString())
          .map(Response.text(_))
          .tap: _ =>
            Logger.error(
              s"got an error while registering: $err, additionally $addInfo"
            )
