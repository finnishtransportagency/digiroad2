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

  val dataImporter = new AssetDataImporter

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
      "kauniainen_production_speed_limits.sql",
      "kauniainen_total_weight_limits.sql",
      "adjusted_traffic_directions.sql",
      "adjusted_functional_classes.sql"))
  }

  def importSpeedLimitsFromConversion(taskPool: ForkJoinPool) {
    print("\nCommencing speed limit import from conversion: ")
    println(DateTime.now())
    dataImporter.importSpeedLimits(Conversion, taskPool)
    print("Speed limit import complete: ")
    println(DateTime.now())
    println("\n")
  }

  def importTotalWeightLimitsFromConversion() {
    print("\nCommencing total weight limit import from conversion: ")
    println(DateTime.now())
    dataImporter.importTotalWeightLimits(Conversion.database())
    print("Total weight limit import complete: ")
    println(DateTime.now())
    println("\n")
  }

  def importWeightLimitsFromConversion() {
    print("\nCommencing weight limit import from conversion: ")
    println(DateTime.now())
    dataImporter.importNumericalLimits(Conversion.database(), 20, 40)
    dataImporter.importNumericalLimits(Conversion.database(), 21, 50)
    dataImporter.importNumericalLimits(Conversion.database(), 24, 60)
    print("Weight limit import complete: ")
    println(DateTime.now())
    println("\n")
  }

  def importDimensionLimitsFromConversion() {
    print("\nCommencing dimension limit import from conversion: ")
    println(DateTime.now())
    dataImporter.importNumericalLimits(Conversion.database(), 18, 70)
    dataImporter.importNumericalLimits(Conversion.database(), 19, 80)
    dataImporter.importNumericalLimits(Conversion.database(), 23, 90)
    print("Dimension limit import complete: ")
    println(DateTime.now())
    println("\n")
  }

  def importManoeuvresFromConversion() {
    print("\nCommencing manoeuvre import from conversion: ")
    println(DateTime.now())
    dataImporter.importManoeuvres(Conversion.database())
    print("Manoeuvre import complete: ")
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
      println("*************************************************************************************")
      println("YOU ARE RUNNING FIXTURE RESET AGAINST A NON-DEVELOPER DATABASE, TYPE 'YES' TO PROCEED")
      println("*************************************************************************************")
      breakable {
        while (true) {
          val input = Console.readLine()
          if (input.trim() == "YES") {
            break()
          }
        }
      }
    }

    args.headOption match {
      case Some("test") =>
        tearDown()
        setUpTest()
        val typeProps = dataImporter.getTypeProperties
        BusStopTestData.generateTestData.foreach(x => dataImporter.insertBusStops(x, typeProps))
        BusStopIconImageData.insertImages("testdataimport")
        importMunicipalityCodes()
      case Some("speedlimits") =>
        val taskPool = new ForkJoinPool(8)
        importSpeedLimitsFromConversion(taskPool)
      case Some("totalweightlimits") =>
        importTotalWeightLimitsFromConversion()
      case Some("weightlimits") =>
        importWeightLimitsFromConversion()
      case Some("dimensionlimits") =>
        importDimensionLimitsFromConversion()
      case Some("manoeuvres") =>
        importManoeuvresFromConversion()
      case _ => println("Usage: DataFixture test | speedlimits | totalweightlimits | weightlimits | dimensionlimits")
    }
  }
}
