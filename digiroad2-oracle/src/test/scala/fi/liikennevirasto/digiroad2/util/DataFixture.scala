package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.dataimport.AssetDataImporter
import fi.liikennevirasto.digiroad2.dataimport.AssetDataImporter.{Conversion, TemporaryTables}
import java.util.Properties

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

  def tearDown() {
    SqlScriptRunner.runScript("drop_tables.sql")
  }

  def setUpTest() {
    SqlScriptRunner.runScripts(List("create_tables.sql", "drop_and_insert_test_fixture.sql", "insert_bus_stop_properties.sql", "insert_users.sql"))
  }

  def setUpFull() {
    SqlScriptRunner.runScripts(List("create_tables.sql", "insert_bus_stop_properties.sql", "insert_users.sql"))
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
      case Some("test") => {
        tearDown()
        setUpTest()
        dataImporter.insertBusStops(BusStopTestData.generateTestData)
      }
      case Some("full") => {
        tearDown()
        setUpFull()
        dataImporter.importRoadlinks(TemporaryTables)
        dataImporter.importBusStops(TemporaryTables)
      }
      case Some("conversion") => {
        tearDown()
        setUpFull()
        dataImporter.importRoadlinks(Conversion)
        dataImporter.importBusStops(Conversion)
      }
      case _ => println("Usage: DataFixture test | full | conversion")
    }
  }
}
