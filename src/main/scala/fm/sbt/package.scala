package fm

package object sbt {
  val logger = {
    import fm.sbt.Compat._
    val l = ConsoleLogger(System.out)
    l.setLevel(Level.Info)
    l
  }
}
