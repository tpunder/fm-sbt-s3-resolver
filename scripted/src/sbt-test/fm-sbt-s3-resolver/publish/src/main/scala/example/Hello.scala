package example

/**
 * Hello is an application that can be ran
 */
object Hello extends Greeting with App {
  println(greeting)
}

trait Greeting {
  lazy val greeting: String = "hello"
}