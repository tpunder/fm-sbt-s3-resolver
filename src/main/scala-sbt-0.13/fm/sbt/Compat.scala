package fm.sbt

object Compat extends Compat

trait Compat {
  type Logger = sbt.Logger
  val Logger = sbt.Logger

  val Level = sbt.Level

  type ConsoleLogger = sbt.ConsoleLogger
  val ConsoleLogger = sbt.ConsoleLogger

  val Using = sbt.Using
}