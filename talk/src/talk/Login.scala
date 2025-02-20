package org.maidagency.talk.login

import fabric.*
import fabric.io.*
import org.maidagency.talk.database.*
import org.maidagency.talk.generator.*
import org.maidagency.talk.hashing.*
import org.maidagency.talk.logging.*
import scalasql.DbClient.*
import scalasql.SqliteDialect.*
import scalasql.Table
import zio.*
import zio.concurrent.ConcurrentMap
import zio.http.*

enum LoginError:
  case IncorrectPassword(username: String)
  case DatabaseError
  case UserNotFound(username: String)
  case FailedToReadRequest
  case FailedToParseJson(json: String)
  case FailedToHash

object Login:
  // you could ask scalasql to fetch only one data but it throws when
  // nothing is found so you gotta roll the below
  //
  // i don't even want to catch that assertion error
  def getFirst[T](coll: Seq[T], searchTarget: String) =
    if coll.length == 1 then ZIO.succeed(coll.head)
    else if coll.length == 0 then
      ZIO.fail(LoginError.UserNotFound(searchTarget))
    else ZIO.fail(LoginError.DatabaseError)

  def storeToken(
      username: String,
      token: String,
      storage: ConcurrentMap[String, String]
  ) =
    storage.put(token, username)

  def formatJson(input: String) =
    for
      json <- ZIO.succeed(fabric.io.JsonParser(input, fabric.io.Format.Json))
      username <- json.get("username").map(_.asString) match
        case None =>
          ZIO.fail(LoginError.FailedToParseJson(input))
        case Some(value) =>
          ZIO.succeed(value)
      password <- json.get("password").map(_.asString) match
        case None =>
          ZIO.fail(LoginError.FailedToParseJson(input))
        case Some(value) =>
          ZIO.succeed(value)
    yield (username, password)

  def extractFields(req: Request) =
    req.body.asString
      .catchAll(_ => ZIO.fail(LoginError.FailedToReadRequest))
      .flatMap(formatJson)

  def mkErrJson(reason: String) =
    obj("success" -> false, "reason" -> reason).toString

  def logSuccess(username: String) =
    Logger.info(s"$username successfully logged in")

  def login(
      req: Request,
      dbconn: DataSource,
      storage: ConcurrentMap[String, String]
  ) =
    val response = for
      (username, password) <- extractFields(req)
      res <- ZIO
        .succeed(Users.select.filter(_.username === username))
        .flatMap: query =>
          ZIO
            .attemptBlocking(dbconn.transaction(db => db.run(query)))
            .catchAll(_ => ZIO.fail(LoginError.DatabaseError))
        .flatMap(getFirst(_, username))
        .tap: user => // failes the entire thing
          ZIO
            .ifZIO(
              Hash
                .verify(password, user.password)
                // should NEVER pop
                .catchAll(_ => ZIO.fail(LoginError.FailedToHash))
            )(
              onFalse = ZIO.fail(LoginError.IncorrectPassword(user.username)),
              onTrue = ZIO.unit
            )
          // should NEVER pop
        .flatMap(_ => Generator.generateToken())
        .tap: token =>
          storeToken(username, token, storage)
        .map: token =>
          obj("success" -> true, "token" -> token).toString
        .map(Response.text(_))
        .tap(_ => logSuccess(username))
    yield res

    response.catchAll: err =>
      val errMsg = err match
        case LoginError.IncorrectPassword(username) =>
          "incorrect password or username"
        case LoginError.UserNotFound(username) =>
          "incorrect password or username"
        case LoginError.DatabaseError =>
          "incorrect password or username"
        case LoginError.FailedToReadRequest =>
          "failed to read request"
        case LoginError.FailedToParseJson(json) =>
          "failed to parse json"
        case LoginError.FailedToHash =>
          "internal server error"
      ZIO
        .succeed(obj("success" -> false, "reason" -> errMsg).toString())
        .map(Response.text(_))
        .tap(_ => Logger.error(s"login error $err"))
