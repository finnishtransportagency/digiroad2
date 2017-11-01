import io.gatling.sbt.GatlingPlugin
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
      scalacOptions ++= Seq("-unchecked", "-feature"),
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
      scalacOptions ++= Seq("-unchecked", "-feature"),
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
      unmanagedResourceDirectories in Test += baseDirectory.value / "conf" /  testEnv,
      unmanagedResourceDirectories in Compile += baseDirectory.value / ".." / "conf" /  env
    )
  ) dependsOn(geoJar)

  val Digiroad2ViiteName = "digiroad2-viite"
  lazy val viiteJar = Project (
    Digiroad2ViiteName,
    file(Digiroad2ViiteName),
    settings = Defaults.defaultSettings ++ Seq(
      organization := Organization,
      name := Digiroad2ViiteName,
      version := Version,
      scalaVersion := ScalaVersion,
      resolvers += Classpaths.typesafeReleases,
      scalacOptions ++= Seq("-unchecked", "-feature"),
      parallelExecution in Test := false,
      testOptions in Test ++= (
        if (System.getProperty("digiroad2.nodatabase", "false") == "true") Seq(Tests.Argument("-l"), Tests.Argument("db")) else Seq()),
      libraryDependencies ++= Seq(
        "org.scalatra" %% "scalatra" % ScalatraVersion,
        "org.scalatra" %% "scalatra-json" % ScalatraVersion,
        "org.json4s"   %% "json4s-jackson" % "3.2.11",
        "org.scalatest" % "scalatest_2.11" % "2.2.4" % "test",
        "org.scalatra" %% "scalatra-scalatest" % ScalatraVersion % "test",
        "org.scalatra" %% "scalatra-auth" % ScalatraVersion % "test",
        "org.mockito" % "mockito-core" % "1.9.5" % "test",
        "com.typesafe.akka" %% "akka-testkit" % "2.3.2" % "test",
        "ch.qos.logback" % "logback-classic" % "1.0.6" % "runtime",
        "commons-io" % "commons-io" % "2.4",
        "com.newrelic.agent.java" % "newrelic-api" % "3.1.1",
        "org.apache.httpcomponents" % "httpclient" % "4.3.3"
      ),
      unmanagedResourceDirectories in Compile += baseDirectory.value / "conf" /  env,
      unmanagedResourceDirectories in Test += baseDirectory.value / "conf" /  testEnv,
      unmanagedResourceDirectories in Compile += baseDirectory.value / ".." / "conf" /  env
    )
  ) dependsOn(geoJar, oracleJar % "compile->compile;test->test")

  val Digiroad2ApiName = "digiroad2-api-common"
  lazy val commonApiJar = Project (
    Digiroad2ApiName,
    file(Digiroad2ApiName),
    settings = Defaults.defaultSettings ++ Seq(
      organization := Organization,
      name := Digiroad2ApiName,
      version := Version,
      scalaVersion := ScalaVersion,
      resolvers += Classpaths.typesafeReleases,
      scalacOptions ++= Seq("-unchecked", "-feature"),
      //      parallelExecution in Test := false,
      testOptions in Test ++= (
        if (System.getProperty("digiroad2.nodatabase", "false") == "true") Seq(Tests.Argument("-l"), Tests.Argument("db")) else Seq()),
      libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-actor" % "2.3.2",
        "org.apache.httpcomponents" % "httpclient" % "4.3.3",
        "org.scalatest" % "scalatest_2.11" % "2.2.4" % "compile,test",
        "org.scalatra" %% "scalatra-scalatest" % ScalatraVersion % "test",
        "org.scalatra" %% "scalatra-json" % ScalatraVersion,
        "org.scalatra" %% "scalatra-auth" % ScalatraVersion,
        "org.mockito" % "mockito-core" % "1.9.5" % "test",
        "org.joda" % "joda-convert" % "1.2",
        "joda-time" % "joda-time" % "2.2",
        "org.eclipse.jetty" % "jetty-webapp" % "9.2.10.v20150310" % "compile",
        "org.eclipse.jetty" % "jetty-servlets" % "9.2.10.v20150310" % "compile",
        "org.eclipse.jetty" % "jetty-proxy" % "9.2.10.v20150310" % "compile",
        "org.eclipse.jetty.orbit" % "javax.servlet" % "3.0.0.v201112011016" % "provided;test" artifacts (Artifact("javax.servlet", "jar", "jar"))
      ),
      unmanagedResourceDirectories in Compile += baseDirectory.value / "conf" /  env,
      unmanagedResourceDirectories in Test += baseDirectory.value / "conf" /  testEnv,
      unmanagedResourceDirectories in Compile += baseDirectory.value / ".." / "conf" /  env
    )
  ) dependsOn(geoJar, oracleJar, viiteJar)

  val Digiroad2ViiteApiName = "digiroad2-api-viite"
  lazy val viiteApiJar = Project (
    Digiroad2ViiteApiName,
    file(Digiroad2ViiteApiName),
    settings = Defaults.defaultSettings ++ Seq(
      organization := Organization,
      name := Digiroad2ViiteApiName,
      version := Version,
      scalaVersion := ScalaVersion,
      resolvers += Classpaths.typesafeReleases,
      scalacOptions ++= Seq("-unchecked", "-feature"),
      //      parallelExecution in Test := false,
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
        "com.typesafe.akka" %% "akka-testkit" % "2.3.2" % "test",
        "ch.qos.logback" % "logback-classic" % "1.0.6" % "runtime",
        "commons-io" % "commons-io" % "2.4",
        "com.newrelic.agent.java" % "newrelic-api" % "3.1.1",
        "org.apache.httpcomponents" % "httpclient" % "4.3.3"
      ),
      unmanagedResourceDirectories in Compile += baseDirectory.value / "conf" /  env,
      unmanagedResourceDirectories in Test += baseDirectory.value / "conf" /  testEnv,
      unmanagedResourceDirectories in Compile += baseDirectory.value / ".." / "conf" /  env
    )
  ) dependsOn(geoJar, oracleJar, viiteJar, commonApiJar % "compile->compile;test->test")

  val Digiroad2OTHApiName = "digiroad2-api-oth"
  lazy val othApiJar = Project (
    Digiroad2OTHApiName,
    file(Digiroad2OTHApiName),
    settings = Defaults.defaultSettings ++ Seq(
      organization := Organization,
      name := Digiroad2OTHApiName,
      version := Version,
      scalaVersion := ScalaVersion,
      resolvers += Classpaths.typesafeReleases,
      scalacOptions ++= Seq("-unchecked", "-feature"),
      //      parallelExecution in Test := false,
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
        "com.typesafe.akka" %% "akka-testkit" % "2.3.2" % "test",
        "ch.qos.logback" % "logback-classic" % "1.0.6" % "runtime",
        "commons-io" % "commons-io" % "2.4",
        "com.newrelic.agent.java" % "newrelic-api" % "3.1.1",
        "org.apache.httpcomponents" % "httpclient" % "4.3.3"
      ),
      unmanagedResourceDirectories in Compile += baseDirectory.value / "conf" /  env,
      unmanagedResourceDirectories in Test += baseDirectory.value / "conf" /  testEnv,
      unmanagedResourceDirectories in Compile += baseDirectory.value / ".." / "conf" /  env
    )
  ) dependsOn(geoJar, oracleJar, commonApiJar % "compile->compile;test->test")

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
      scalacOptions ++= Seq("-unchecked", "-feature"),
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
        "com.typesafe.akka" %% "akka-testkit" % "2.3.2" % "test",
        "ch.qos.logback" % "logback-classic" % "1.0.6" % "runtime",
        "commons-io" % "commons-io" % "2.4",
        "com.newrelic.agent.java" % "newrelic-api" % "3.1.1",
        "org.apache.httpcomponents" % "httpclient" % "4.3.3",
        "org.eclipse.jetty" % "jetty-webapp" % "9.2.10.v20150310" % "container;compile",
        "org.eclipse.jetty" % "jetty-servlets" % "9.2.10.v20150310" % "container;compile",
        "org.eclipse.jetty" % "jetty-proxy" % "9.2.10.v20150310" % "container;compile",
        "org.eclipse.jetty.orbit" % "javax.servlet" % "3.0.0.v201112011016" % "container;provided;test" artifacts (Artifact("javax.servlet", "jar", "jar"))
      ),
      unmanagedResourceDirectories in Compile += baseDirectory.value / "conf" /  env,
      unmanagedResourceDirectories in Test += baseDirectory.value / "conf" /  testEnv
    )
  ) dependsOn(geoJar, oracleJar, viiteJar, commonApiJar, viiteApiJar, othApiJar) aggregate
    (geoJar, oracleJar, viiteJar, commonApiJar, viiteApiJar, othApiJar)

  lazy val gatling = project.in(file("digiroad2-gatling"))
    .enablePlugins(GatlingPlugin)
    .settings(scalaVersion := ScalaVersion)
    .settings(libraryDependencies ++= Seq(
    "io.gatling.highcharts" % "gatling-charts-highcharts" % "2.1.7" % "test",
    "io.gatling" % "gatling-test-framework" % "2.1.7" % "test"))

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
