package org.maidagency.talk.generator

import java.security.SecureRandom
import java.time.Instant
import java.util.Base64

import com.softwaremill.id.DefaultIdGenerator
import zio.*

object Generator:
  val generator = new DefaultIdGenerator

  def generateId() =
    ZIO.succeed(generator.nextId())

  val secureRandom = new SecureRandom()
  val base64Encoder = Base64.getUrlEncoder()

  def generateToken() =
    val byte = new Array[Byte](24)
    secureRandom.nextBytes(byte)
    val encoded = base64Encoder.encodeToString(byte)
    val now = Instant.now().toEpochMilli().toString()
    ZIO.succeed(now + "-" + encoded)
