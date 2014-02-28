package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.dataimport.AssetDataImporter
import fi.liikennevirasto.digiroad2.dataimport.AssetDataImporter.{Conversion, TemporaryTables}
import org.joda.time.DateTime

object DataFixture {
  val TestAssetId = 300000
  val TestAssetTypeId = 10
  val MunicipalityKauniainen = 235
  val MunicipalityEspoo = 49

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
    val dataImporter = new AssetDataImporter
    args.headOption match {
      case Some("test") => {
        tearDown()
        setUpTest()
        val typeProps = dataImporter.getTypeProperties
        BusStopTestData.generateTestData.foreach(x => dataImporter.insertBusStops(x, typeProps))
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
        println("<roadlinks>")
        println(DateTime.now())
        dataImporter.importRoadlinks(Conversion)
        println("</roadlinks>")
        println(DateTime.now())
        println("<importBusStops>")
        dataImporter.importBusStops(Conversion)
        println("</importBusStops>")
      }
      case _ => println("Usage: DataFixture test | full | conversion")
    }
  }
}
