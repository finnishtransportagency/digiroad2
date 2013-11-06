import sbt._
import sbt.Keys._
import org.scalatra.sbt._

object Digiroad2Build extends Build {
  val Organization = "fi.liikennevirasto"
  val Name = "digiroad2"
  val Version = "0.1.0-SNAPSHOT"
  val ScalaVersion = "2.10.2"
  val ScalatraVersion = "2.2.1"
  val env = if (System.getProperty("digiroad2.env") != null) System.getProperty("digiroad2.env") else "dev"


  lazy val project = Project (
    "digiroad2",
    file("."),
    settings = Defaults.defaultSettings ++ ScalatraPlugin.scalatraWithJRebel ++ Seq(
      organization := Organization,
      name := Name,
      version := Version,
      scalaVersion := ScalaVersion,
      resolvers += Classpaths.typesafeReleases,
      libraryDependencies ++= Seq(
        "com.jolbox" % "bonecp" % "0.8.0.RELEASE",
        "org.scalatra" %% "scalatra" % ScalatraVersion,
        "org.scalatra" %% "scalatra-json" % ScalatraVersion,
        "org.json4s"   %% "json4s-jackson" % "3.2.4",
        "org.scalatra" %% "scalatra-scalatest" % ScalatraVersion % "test",
        "ch.qos.logback" % "logback-classic" % "1.0.6" % "runtime",
        "org.eclipse.jetty" % "jetty-webapp" % "8.1.8.v20121106" % "container",
        "org.eclipse.jetty.orbit" % "javax.servlet" % "3.0.0.v201112011016" % "container;provided;test" artifacts (Artifact("javax.servlet", "jar", "jar"))
      ), unmanagedResourceDirectories in Compile += baseDirectory.value / "conf" / "local" /  env
    )
  )
}
