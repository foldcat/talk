package org.maidagency.talk.database

import scalasql.Table
import scalasql.H2Dialect.*
import zio.*
import org.maidagency.talk.generator.*

case class Users[T[_]](
    id: T[Long],
    username: T[String],
    password: T[String]
)
object Users extends Table[Users]

object Database:
  // scalasql seems to be less insane that whatever quill is

  def connect() =
    ZIO.attemptBlocking:
      val dataSource = new org.sqlite.SQLiteDataSource()
      val tmpDb = java.nio.file.Files.createTempDirectory("sqlite")
      dataSource.setUrl(s"jdbc:sqlite:$tmpDb/file.db")
      new scalasql.DbClient.DataSource(
        dataSource,
        config = new scalasql.Config {}
      )

  def setup(client: scalasql.DbClient.DataSource) =
    ZIO.attemptBlocking:
      client.transaction: db =>
        db.updateRaw("""
         CREATE TABLE users (
          id LONG PRIMARY KEY,
          username VARCHAR(255),
          password VARCHAR(255)
         );
       """)
