package org.maidagency.talk.logging

import zio.*

object Logger:
  // i refuse to use zio logging
  // scribe is THE superior logging library
  def fatal(text: String) =
    ZIO.succeed(scribe.fatal(text))
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

  // use this for the default logger
  val logger = scribe.Logger("ZScribe")

  val addSimpleLogger: ZLayer[Any, Nothing, Unit] =
    Runtime.addLogger(
      (
          _trace,
          fiberId,
          loglevel,
          message: () => Any,
          cause,
          context,
          spans,
          annotations
      ) =>
        loglevel match
          // dry enjoyer in shambles
          case LogLevel(50000, label, syslog) =>
            logger.fatal(s"${fiberId.threadName}: ${message()}")
          case LogLevel(40000, label, syslog) =>
            logger.error(s"${fiberId.threadName}: ${message()}")
          case LogLevel(30000, label, syslog) =>
            logger.warn(s"${fiberId.threadName}: ${message()}")
          case LogLevel(20000, label, syslog) =>
            logger.info(s"${fiberId.threadName}: ${message()}")
          case LogLevel(10000, label, syslog) =>
            logger.debug(s"${fiberId.threadName}: ${message()}")
          case LogLevel(0, label, syslog) =>
            logger.trace(s"${fiberId.threadName}: ${message()}")
    )
