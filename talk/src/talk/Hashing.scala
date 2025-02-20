package org.maidagency.talk.hashing

import com.outr.scalapass.Argon2PasswordFactory
import zio.*

object Hash:
  val factory = Argon2PasswordFactory()

  def hash(password: String) =
    ZIO.attemptBlocking(factory.hash(password))

  def verify(password: String, hash: String) =
    ZIO.attemptBlocking(factory.verify(password, hash))
