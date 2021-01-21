import mill._
import mill.scalalib._

object app extends ScalaModule {
  def scalaVersion = "2.13.4"

  def ivyDeps = Agg(
    ivy"org.postgresql:postgresql:9.4-1206-jdbc42",
    ivy"org.typelevel::cats-core:2.1.1",
    ivy"com.lihaoyi::requests::0.6.5",
    ivy"com.lihaoyi::cask:0.7.8",
  )
  object test extends Tests{
    def testFrameworks = Seq("utest.runner.Framework")

    def ivyDeps = Agg(
      ivy"com.lihaoyi::utest::0.7.5",
    )
  }
}
