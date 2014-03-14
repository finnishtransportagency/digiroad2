package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.util.AssetDataImporter.{Conversion, TemporaryTables}
import org.joda.time.DateTime
import scala.concurrent.forkjoin.ForkJoinPool
import java.util.Properties
import com.googlecode.flyway.core.Flyway
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase._
import scala.Some

object DataFixture {
  val TestAssetId = 300000
  val TestAssetTypeId = 10
  val MunicipalityKauniainen = 235
  val MunicipalityEspoo = 49
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
    SqlScriptRunner.runScripts(List("insert_test_fixture.sql", "insert_users.sql"))
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
            break
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
        migrateTo("0.1")
        val taskPool = new ForkJoinPool(8)
        importRoadlinksFromConversion(dataImporter, taskPool)
        importBusStopsFromConversion(dataImporter, taskPool)
        BusStopIconImageData.insertImages("dr1conversion")
        importMunicipalityCodes()
        migrateAll()
      case Some("busstops") =>
        val taskPool = new ForkJoinPool(8)
        importBusStopsFromConversion(dataImporter, taskPool)
      case _ => println("Usage: DataFixture test | full | conversion")
    }
  }
}
