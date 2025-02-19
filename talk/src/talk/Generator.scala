package org.maidagency.talk.generator

import zio.*
import com.softwaremill.id.DefaultIdGenerator

object Generator:
  val generator = new DefaultIdGenerator
  def generate() =
    ZIO.succeed(generator.nextId())
