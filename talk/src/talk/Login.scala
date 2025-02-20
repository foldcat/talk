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
  case IncorrectPassword
  case DatabaseError
  case UserNotFound
  case FailedToReadRequest
  case FailedToParseJson

object Login:
  // you could ask scalasql to fetch only one data but it throws when
  // nothing is found so you gotta roll the below
  //
  // i don't even want to catch that assertion error
  def getFirst[T](coll: Seq[T]) =
    if coll.length == 1 then ZIO.succeed(coll.head)
    else if coll.length == 0 then ZIO.fail(LoginError.UserNotFound)
    else ZIO.fail(LoginError.DatabaseError)

  def storeToken(
      username: String,
      token: String,
      storage: ConcurrentMap[String, String]
  ) =
    storage.put(token, username)

  def formatJson(json: fabric.Json) =
    for // im sure theres a better way to do this
      username <- json.get("username").map(_.asString) match
        case None =>
          ZIO.fail(LoginError.FailedToParseJson)
        case Some(value) =>
          ZIO.succeed(value)
      password <- json.get("password").map(_.asString) match
        case None =>
          ZIO.fail(LoginError.FailedToParseJson)
        case Some(value) =>
          ZIO.succeed(value)
    yield (username, password)

  def extractFields(req: Request) =
    req.body.asString
      .catchAll(_ => ZIO.fail(LoginError.FailedToReadRequest))
      .map: result =>
        fabric.io.JsonParser(result, fabric.io.Format.Json)
      .flatMap(formatJson)

  def mkErrJson(reason: String) =
    obj("success" -> false, "reason" -> reason).toString

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
        .flatMap(getFirst)
        .tap: user => // failes the entire thing
          ZIO.ifZIO(Hash.verify(password, user.password))(
            onFalse = ZIO.fail(LoginError.IncorrectPassword),
            onTrue = ZIO.unit
          )
        .flatMap(_ => Generator.generateToken())
        .tap: token =>
          storeToken(username, token, storage)
        .map: token =>
          obj("success" -> true, "token" -> token).toString
        .map(Response.text(_))
    yield res

    response.catchAll: err =>
      val errJson = err match
        case LoginError.IncorrectPassword =>
          mkErrJson("incorrect password or username")
        case LoginError.UserNotFound =>
          mkErrJson("incorrect password or username")
        case LoginError.DatabaseError =>
          mkErrJson("incorrect password or username")
        case LoginError.FailedToReadRequest =>
          mkErrJson("failed to read request")
        case LoginError.FailedToParseJson =>
          mkErrJson("failed to parse json")
      ZIO.succeed(Response.text(errJson))
