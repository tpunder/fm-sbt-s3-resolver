package app

object App {
  def main(args: Array[String]): Unit = {
    val res: String = lib.Lib.helloWorld()
    println("lib.Lib.helloWorld(): " + res)
    if (res != "Hello World!") System.exit(1)
  }
}