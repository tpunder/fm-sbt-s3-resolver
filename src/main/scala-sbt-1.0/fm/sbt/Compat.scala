package fm.sbt

object Compat extends Compat

trait Compat {
  type Logger = sbt.util.Logger
  val Logger = sbt.util.Logger

  val Level = sbt.util.Level

  type ConsoleLogger = sbt.internal.util.ConsoleLogger
  val ConsoleLogger = sbt.internal.util.ConsoleLogger

  val Using = sbt.io.Using
}