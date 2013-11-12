import sbt._
import sbt.Keys._
import org.scalatra.sbt._
import sbtassembly.Plugin.AssemblyKeys._
import sbtassembly.Plugin.MergeStrategy
import org.scalatra.sbt.PluginKeys._

object Digiroad2Build extends Build {
  val Organization = "fi.liikennevirasto"
  val Digiroad2Name = "digiroad2"
  val Digiroad2GeoName = "digiroad2-geo"
  val Version = "0.1.0-SNAPSHOT"
  val ScalaVersion = "2.10.2"
  val ScalatraVersion = "2.2.1"
  val env = if (System.getProperty("digiroad2.env") != null) System.getProperty("digiroad2.env") else "dev"

  lazy val geoJar = Project (
    Digiroad2GeoName,
    file(Digiroad2GeoName),
    settings = Defaults.defaultSettings ++ Seq(
      organization := Organization,
      name := Digiroad2GeoName,
      version := Version,
      scalaVersion := ScalaVersion,
      scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature"),
      resolvers += Classpaths.typesafeReleases
    )
  )

  val Digiroad2OracleName = "digiroad2-oracle"
  lazy val oracleJar = Project (
    Digiroad2OracleName,
    file(Digiroad2OracleName),
    settings = Defaults.defaultSettings ++ Seq(
      organization := Organization,
      name := Digiroad2OracleName,
      version := Version,
      scalaVersion := ScalaVersion,
      resolvers += Classpaths.typesafeReleases,
      scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature"),
      libraryDependencies ++= Seq(
        "com.jolbox" % "bonecp" % "0.8.0.RELEASE",
        "org.scalatest" % "scalatest_2.10" % "2.0" % "test",
        "com.typesafe.slick" %% "slick" % "1.0.1"
      ), unmanagedResourceDirectories in Compile += baseDirectory.value / "conf" /  env
    )
  ) dependsOn(geoJar)

  lazy val warProject = Project (
    Digiroad2Name,
    file("."),
    settings = Defaults.defaultSettings
      ++ assemblySettings
      ++ ScalatraPlugin.scalatraWithJRebel ++ Seq(
      organization := Organization,
      name := Digiroad2Name,
      version := Version,
      scalaVersion := ScalaVersion,
      resolvers += Classpaths.typesafeReleases,
      scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature"),
      testOptions in Test ++= (
        if (System.getProperty("nodatabase", "false") == "true") Seq(Tests.Argument("-l"), Tests.Argument("db")) else Seq()),
      libraryDependencies ++= Seq(
        "org.scalatra" %% "scalatra" % ScalatraVersion,
        "org.scalatra" %% "scalatra-json" % ScalatraVersion,
        "org.json4s"   %% "json4s-jackson" % "3.2.4",
        "org.scalatra" %% "scalatra-scalatest" % ScalatraVersion % "test",
        "ch.qos.logback" % "logback-classic" % "1.0.6" % "runtime",
        "org.eclipse.jetty" % "jetty-webapp" % "8.1.8.v20121106" % "container;compile",
        "org.eclipse.jetty.orbit" % "javax.servlet" % "3.0.0.v201112011016" % "container;provided;test" artifacts (Artifact("javax.servlet", "jar", "jar"))
      )
    )
  ) dependsOn(geoJar, oracleJar)

  val assemblySettings = sbtassembly.Plugin.assemblySettings ++ Seq(
    mainClass in assembly := Some("fi.liikennevirasto.digiroad2.DigiroadServer"),
    test in assembly := {},
    mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
    {
      case x if x.endsWith("about.html") => MergeStrategy.discard
      case x => old(x)
    } }
  )
}
