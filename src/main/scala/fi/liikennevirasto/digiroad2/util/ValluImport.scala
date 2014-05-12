package fi.liikennevirasto.digiroad2.util

import java.io._
import fi.liikennevirasto.digiroad2.user.oracle.OracleUserProvider
import fi.liikennevirasto.digiroad2.asset.oracle.OracleSpatialAssetProvider
import fi.liikennevirasto.digiroad2.asset.AssetWithProperties
import fi.liikennevirasto.digiroad2.asset.PropertyValue
import fi.liikennevirasto.digiroad2.DummyEventBus

object ValluImport {

  val userProvider = new OracleUserProvider
  val provider = new OracleSpatialAssetProvider(new DummyEventBus, userProvider)
  val header = "STOP_ID;ADMIN_STOP_ID;STOP_CODE;NAME_FI;NAME_SV;COORDINATE_X;COORDINATE_Y;ADDRESS;ROAD_NUMBER;BEARING;BEARING_DESCRIPTION;DIRECTION;LOCAL_BUS;EXPRESS_BUS;NON_STOP_EXPRESS_BUS;VIRTUAL_STOP;EQUIPMENT;REACHABILITY;SPECIAL_NEEDS;MODIFIED_TIMESTAMP;MODIFIED_BY;VALID_FROM;VALID_TO;ADMINISTRATOR_CODE;MUNICIPALITY_CODE;MUNICIPALITY_NAME;COMMENTS;CONTACT_EMAILS"

  def writeCsvToFile() = {
    val printer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("digiroad_stops.csv"), "UTF-8"))
    val fileName = "valluNamesAndBusStopIds.csv"
    val valluComplementaryBusStopNames : Map[Long, String] = readValluBusStopNames(fileName)

    // BOM for excel
    printer.write("\uFEFF")
    printer.write(header + "\n")
    getMunicipalities.foreach(municipality => {
      AssetValluCsvFormatter.valluCsvRowsFromAssets(getAssetsForMunicipality(municipality), valluComplementaryBusStopNames).foreach(x => printer.write(x + "\n"))
    })
    printer.close
  }


  def readValluBusStopNames(fileName: String): Map[Long, String] = {
    if (new java.io.File(fileName).exists) {
      val valluImportData = scala.io.Source.fromFile(fileName).getLines
      valluImportData
        .map(_.split(";"))
        .foldLeft(Map[Long, String]()) { (map, entry) =>
          map + (entry(1).toLong -> entry(0))
        }
    } else {
      Map()
    }
  }

  def getAssetsForMunicipality(municipality: Int) = {
    println(s"Get assets for municipality $municipality")
    provider.getAssetsByMunicipality(municipality)
  }

  def getMunicipalities = {
    provider.getMunicipalities
  }

  def main(args:Array[String]) : Unit = {
    ValluImport.writeCsvToFile()
  }
}
