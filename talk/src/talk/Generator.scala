package org.maidagency.talk.generator

import com.softwaremill.id.DefaultIdGenerator
import zio.*

object Generator:
  val generator = new DefaultIdGenerator
  def generate() =
    ZIO.succeed(generator.nextId())
