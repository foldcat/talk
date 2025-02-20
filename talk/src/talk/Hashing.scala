package org.maidagency.talk.hashing

import com.outr.scalapass.Argon2PasswordFactory
import zio.*

object Hash:
  val factory = Argon2PasswordFactory()

  def hash(password: String) =
    ZIO.succeed(factory.hash(password))

  def verify(password: String, hash: String) =
    ZIO.succeed(factory.verify(password, hash))
