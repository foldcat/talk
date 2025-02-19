package org.maidagency.talk.logging

import zio.*

object Logger:  
  // i refuse to use zio logging
  // scribe is THE superior logging library
  def error(text: String) =
    ZIO.succeed(scribe.error(text))
  def warn(text: String) =
    ZIO.succeed(scribe.warn(text))
  def info(text: String) =
    ZIO.succeed(scribe.info(text))
  def debug(text: String) =
    ZIO.succeed(scribe.debug(text))
  def trace(text: String) =
    ZIO.succeed(scribe.trace(text))

  def setup() =
    ZIO.succeed:
      scribe.Logger.root
        .clearHandlers()
        .clearModifiers()
        // causes junk to be logged 
        .withHandler(minimumLevel = Some(scribe.Level.Info))
        .replace()
