package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.util.AssetDataImporter.{Conversion, TemporaryTables}
import org.joda.time.DateTime
import scala.concurrent.forkjoin.ForkJoinPool
import java.util.Properties
import com.googlecode.flyway.core.Flyway
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase._
import scala.Some
import java.io.{File, PrintWriter}
import scala.collection.parallel.ForkJoinTaskSupport
import scala.slick.driver.JdbcDriver.backend.{Database, DatabaseDef, Session}
import scala.slick.jdbc.{StaticQuery => Q, _}
import Database.dynamicSession

object DataFixture {
  val TestAssetId = 300000
  lazy val properties: Properties = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/bonecp.properties"))
    props
  }

  def flyway: Flyway = {
    val flyway = new Flyway()
    flyway.setDataSource(ds)
    flyway.setInitVersion("-1")
    flyway.setInitOnMigrate(true)
    flyway.setLocations("db.migration")
    flyway
  }

  def migrateTo(version: String) = {
    val migrator = flyway
    migrator.setTarget(version.toString)
    migrator.migrate()
  }

  def migrateAll() = {
    flyway.migrate()
  }

  def tearDown() {
    flyway.clean()
  }

  def setUpTest() {
    migrateAll()
    SqlScriptRunner.runScripts(List(
      "insert_test_fixture.sql",
      "insert_users.sql",
      "kauniainen_road_links.sql",
      "kauniainen_speed_limits.sql",
      "rovaniemi_road_links.sql",
      "rovaniemi_bus_stops.sql"))
  }

  def setUpFull() {
    migrateAll()
    SqlScriptRunner.runScripts(List("insert_users.sql"))
  }

  def importRoadlinksFromConversion(dataImporter: AssetDataImporter, taskPool: ForkJoinPool) {
    println("\nCommencing road link import from conversion at time: ")
    println(DateTime.now())
    dataImporter.importRoadlinks(Conversion, taskPool)
    println("Road link import complete at time: ")
    println(DateTime.now())
    println("\n")
  }

  def importBusStopsFromConversion(dataImporter: AssetDataImporter, taskPool: ForkJoinPool) {
    println("\nCommencing bus stop import from conversion at time: ")
    println(DateTime.now())
    dataImporter.importBusStops(Conversion, taskPool)
    println("Bus stop import complete at time: ")
    println(DateTime.now())
    println("\n")
  }

  def importSpeedLimitsFromConversion(dataImporter: AssetDataImporter, taskPool: ForkJoinPool) {
    print("\nCommencing speed limit import from conversion: ")
    println(DateTime.now())
    dataImporter.importSpeedLimits(Conversion, taskPool)
    print("Speed limit import complete: ")
    println(DateTime.now())
    println("\n")
  }

  def importMunicipalityCodes() {
    println("\nCommencing municipality code import at time: ")
    println(DateTime.now())
    new MunicipalityCodeImporter().importMunicipalityCodes()
    println("Municipality code import complete at time: ")
    println(DateTime.now())
    println("\n")
  }

  def main(args:Array[String]) : Unit = {
    import scala.util.control.Breaks._
    val username = properties.getProperty("bonecp.username")
    if (!username.startsWith("dr2dev")) {
      println("***********************************************************************************")
      println("YOU ARE RUNNING FIXTURE RESET AGAINST NON-DEVELOPER DATABASE, TYPE 'YES' TO PROCEED")
      println("***********************************************************************************")
      breakable {
        while (true) {
          val input = Console.readLine()
          if (input.trim() == "YES") {
            break()
          }
        }
      }
    }

    val dataImporter = new AssetDataImporter
    args.headOption match {
      case Some("test") =>
        tearDown()
        setUpTest()
        val typeProps = dataImporter.getTypeProperties
        BusStopTestData.generateTestData.foreach(x => dataImporter.insertBusStops(x, typeProps))
        BusStopIconImageData.insertImages("testdataimport")
        importMunicipalityCodes()
      case Some("full") =>
        tearDown()
        setUpFull()
        val taskPool = new ForkJoinPool(1)
        dataImporter.importRoadlinks(TemporaryTables, taskPool)
        dataImporter.importBusStops(TemporaryTables, taskPool)
        BusStopIconImageData.insertImages("fulltestdataimport")
        importMunicipalityCodes()
      case Some("conversion") =>
        tearDown()
        migrateAll()
        val taskPool = new ForkJoinPool(8)
        importRoadlinksFromConversion(dataImporter, taskPool)
        importBusStopsFromConversion(dataImporter, taskPool)
        BusStopIconImageData.insertImages("dr1conversion")
        importMunicipalityCodes()
      case Some("busstops") =>
        val taskPool = new ForkJoinPool(8)
        importBusStopsFromConversion(dataImporter, taskPool)
      case Some("speedlimits") =>
        val taskPool = new ForkJoinPool(8)
        importSpeedLimitsFromConversion(dataImporter, taskPool)
      case Some("AdminIdUpdate") =>
        Database.forDataSource(ds).withDynSession {
          val adminCodeWriter = new PrintWriter(new File("admincode.sql"))
          val adminWriter = new PrintWriter(new File("admins.sql"))
          new AssetAdminImporter().getAssetIds(AssetAdminImporter.toAdminUpdateSql, AssetAdminImporter.getAdminCodesFromDr1).foreach(x => {
            adminCodeWriter.write(x._1 + "\n")
            adminWriter.write(x._2 + "\n")
          })
          adminWriter.close()
          adminCodeWriter.close()
       }
      case Some("FunctionalClasses") =>
        Database.forDataSource(ds).withDynSession {
          val functionalClassWriter = new PrintWriter(new File("functional_classes.sql"))
          new AssetAdminImporter()
            .getFunctionalClasses(AssetAdminImporter.toFunctionalClassUpdate, AssetAdminImporter.getFunctionalClassesFromDr1)
            .foreach(x => {
              functionalClassWriter.write(x + "\n")
            })
          functionalClassWriter.close()
        }
      case Some("NameUpdate") =>
        Database.forDataSource(ds).withDynSession {
          val nameWriter = new PrintWriter(new File("names.sql"))
          new AssetAdminImporter().getAssetIds(AssetAdminImporter.toNameUpdateSql, AssetAdminImporter.getNamesFromDr1)
            .foreach(x => {
            nameWriter.write(x._1)
            nameWriter.write(x._2)
          })
          nameWriter.close()
        }
      case _ => println("Usage: DataFixture test | full | conversion | AdminIdUpdate | NameUpdate | speedlimits")
    }
  }
}
