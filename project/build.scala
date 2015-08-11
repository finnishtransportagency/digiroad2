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
  val ScalaVersion = "2.11.7"
  val ScalatraVersion = "2.3.1"
  val env = if (System.getProperty("digiroad2.env") != null) System.getProperty("digiroad2.env") else "dev"
  val testEnv = if (System.getProperty("digiroad2.env") != null) System.getProperty("digiroad2.env") else "test"
  lazy val geoJar = Project (
    Digiroad2GeoName,
    file(Digiroad2GeoName),
    settings = Defaults.defaultSettings ++ Seq(
      organization := Organization,
      name := Digiroad2GeoName,
      version := Version,
      scalaVersion := ScalaVersion,
      scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature"),
      resolvers ++= Seq(
        Classpaths.typesafeReleases,
        "opengeo" at "http://repo.opengeo.org/",
        "osgeo" at "http://download.osgeo.org/webdav/geotools/"),
      libraryDependencies ++= Seq(
        "org.joda" % "joda-convert" % "1.2",
        "joda-time" % "joda-time" % "2.2",
        "com.typesafe.akka" %% "akka-actor" % "2.3.2",
        "org.geotools" % "gt-graph" % "13.1",
        "org.scalatest" % "scalatest_2.11" % "2.2.4" % "test"
      )
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
      testOptions in Test ++= (
        if (System.getProperty("digiroad2.nodatabase", "false") == "true") Seq(Tests.Argument("-l"), Tests.Argument("db")) else Seq()),
      libraryDependencies ++= Seq(
        "org.apache.commons" % "commons-lang3" % "3.2",
        "commons-codec" % "commons-codec" % "1.9",
        "com.jolbox" % "bonecp" % "0.8.0.RELEASE",
        "org.scalatest" % "scalatest_2.11" % "2.2.4" % "test",
        "com.typesafe.slick" %% "slick" % "3.0.0",
        "org.json4s"   %% "json4s-jackson" % "3.2.11",
        "org.joda" % "joda-convert" % "1.2",
        "joda-time" % "joda-time" % "2.2",
        "com.github.tototoshi" %% "slick-joda-mapper" % "2.0.0",
        "com.github.tototoshi" %% "scala-csv" % "1.0.0",
        "org.apache.httpcomponents" % "httpclient" % "4.3.3",
        "com.newrelic.agent.java" % "newrelic-api" % "3.1.1",
        "org.mockito" % "mockito-core" % "1.9.5" % "test",
        "com.googlecode.flyway" % "flyway-core" % "2.3" % "test"
      ),
      unmanagedResourceDirectories in Compile += baseDirectory.value / "conf" /  env,
      unmanagedResourceDirectories in Test += baseDirectory.value / "conf" /  testEnv
    )
  ) dependsOn(geoJar)

  lazy val warProject = Project (
    Digiroad2Name,
    file("."),
    settings = Defaults.defaultSettings
      ++ assemblySettings
      ++ net.virtualvoid.sbt.graph.Plugin.graphSettings
      ++ ScalatraPlugin.scalatraWithJRebel ++ Seq(
      organization := Organization,
      name := Digiroad2Name,
      version := Version,
      scalaVersion := ScalaVersion,
      resolvers += Classpaths.typesafeReleases,
      scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature"),
      parallelExecution in Test := false,
      fork in (Compile,run) := true,
      testOptions in Test ++= (
        if (System.getProperty("digiroad2.nodatabase", "false") == "true") Seq(Tests.Argument("-l"), Tests.Argument("db")) else Seq()),
      libraryDependencies ++= Seq(
        "org.scalatra" %% "scalatra" % ScalatraVersion,
        "org.scalatra" %% "scalatra-json" % ScalatraVersion,
        "org.json4s"   %% "json4s-jackson" % "3.2.11",
        "org.scalatest" % "scalatest_2.11" % "2.2.4" % "test",
        "org.scalatra" %% "scalatra-scalatest" % ScalatraVersion % "test",
        "org.scalatra" %% "scalatra-auth" % ScalatraVersion,
        "org.mockito" % "mockito-core" % "1.9.5" % "test",
        "ch.qos.logback" % "logback-classic" % "1.0.6" % "runtime",
        "commons-io" % "commons-io" % "2.4",
        "com.newrelic.agent.java" % "newrelic-api" % "3.1.1",
        "org.apache.httpcomponents" % "httpclient" % "4.3.3",
        "org.eclipse.jetty" % "jetty-webapp" % "8.1.14.v20131031" % "container;compile",
        "org.eclipse.jetty" % "jetty-servlets" % "8.1.14.v20131031" % "container;compile",
        "io.gatling.highcharts" % "gatling-charts-highcharts" % "2.1.7",
        "org.eclipse.jetty.orbit" % "javax.servlet" % "3.0.0.v201112011016" % "container;provided;test" artifacts (Artifact("javax.servlet", "jar", "jar"))
      ),
      unmanagedResourceDirectories in Compile += baseDirectory.value / "conf" /  env,
      unmanagedResourceDirectories in Test += baseDirectory.value / "conf" /  testEnv
    )
  ) dependsOn(geoJar, oracleJar) aggregate(geoJar, oracleJar)

  val assemblySettings = sbtassembly.Plugin.assemblySettings ++ Seq(
    mainClass in assembly := Some("fi.liikennevirasto.digiroad2.ProductionServer"),
    test in assembly := {},
    mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
    {
      case x if x.endsWith("about.html") => MergeStrategy.discard
      case x => old(x)
    } }
  )
}
