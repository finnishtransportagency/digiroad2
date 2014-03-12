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

  lazy val flyway: Flyway = {
    val flyway = new Flyway()
    flyway.setDataSource(ds)
    flyway.setInitOnMigrate(true)
    flyway.setLocations("db.migration")
    flyway
  }

  def tearDown() {
    flyway.clean()
  }

  def setUpTest() {
    flyway.migrate()
    SqlScriptRunner.runScripts(List("drop_and_insert_test_fixture.sql", "insert_users.sql"))
  }

  def setUpFull() {
    flyway.migrate()
    SqlScriptRunner.runScripts(List("insert_users.sql"))
  }

  def importRoadlinksFromConversion(dataImporter: AssetDataImporter, taskPool: ForkJoinPool) {
    println("<roadlinks>")
    println(DateTime.now())
    dataImporter.importRoadlinks(Conversion, taskPool)
    println(DateTime.now())
    println("</roadlinks>")
  }

  def importBusStopsFromConversion(dataImporter: AssetDataImporter, taskPool: ForkJoinPool) {
    println("<importBusStops>")
    println(DateTime.now())
    dataImporter.importBusStops(Conversion, taskPool)
    println(DateTime.now())
    println("</importBusStops>")
  }

  def importMunicipalityCodes() {
    println("<importMunicipalityCodes>")
    println(DateTime.now())
    new MunicipalityCodeImporter().importMunicipalityCodes()
    println(DateTime.now())
    println("<importMunicipalityCodes>")
  }

  def createIndices() {
    println("<createIndices>")
    println(DateTime.now())
    SqlScriptRunner.runScripts(List("create_indices.sql"))
    println(DateTime.now())
    println("<createIndices>")
  }

  def main(args:Array[String]) = {
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
        importMunicipalityCodes()
        createIndices()
      case Some("full") =>
        tearDown()
        setUpFull()
        val taskPool = new ForkJoinPool(1)
        dataImporter.importRoadlinks(TemporaryTables, taskPool)
        dataImporter.importBusStops(TemporaryTables, taskPool)
        importMunicipalityCodes()
        createIndices()
      case Some("conversion") =>
        tearDown()
        setUpFull()
        val taskPool = new ForkJoinPool(8)
        importRoadlinksFromConversion(dataImporter, taskPool)
        importBusStopsFromConversion(dataImporter, taskPool)
        importMunicipalityCodes()
        createIndices()
      case Some("busstops") =>
        val taskPool = new ForkJoinPool(8)
        importBusStopsFromConversion(dataImporter, taskPool)
      case _ => println("Usage: DataFixture test | full | conversion")
    }
  }
}
