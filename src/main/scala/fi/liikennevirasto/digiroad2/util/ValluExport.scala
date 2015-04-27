package fi.liikennevirasto.digiroad2.util

import java.io._

import fi.liikennevirasto.digiroad2.Digiroad2Context
import fi.liikennevirasto.digiroad2.asset.AssetWithProperties
import fi.liikennevirasto.digiroad2.asset.oracle.OracleSpatialAssetDao
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase.ds
import fi.liikennevirasto.digiroad2.user.oracle.OracleUserProvider
import fi.liikennevirasto.digiroad2.util.AssetValluCsvFormatter._

import scala.collection.immutable
import scala.slick.driver.JdbcDriver.backend.Database

object ValluExport {
  val userProvider = new OracleUserProvider
  val header = "STOP_ID;ADMIN_STOP_ID;STOP_CODE;NAME_FI;NAME_SV;COORDINATE_X;COORDINATE_Y;ADDRESS;ROAD_NUMBER;BEARING;BEARING_DESCRIPTION;DIRECTION;LOCAL_BUS;EXPRESS_BUS;NON_STOP_EXPRESS_BUS;VIRTUAL_STOP;EQUIPMENT;REACHABILITY;SPECIAL_NEEDS;MODIFIED_TIMESTAMP;MODIFIED_BY;VALID_FROM;VALID_TO;ADMINISTRATOR_CODE;MUNICIPALITY_CODE;MUNICIPALITY_NAME;COMMENTS;CONTACT_EMAILS;LIVI_ID;ROAD_TYPE"

  def writeCsvToFile() = {
    val printer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("digiroad_stops.csv"), "UTF-8"))
    val fileName = "valluNamesAndBusStopIds.csv"
    val valluComplementaryBusStopNames : Map[Long, String] = readValluBusStopNames(fileName)

    // BOM for excel
    printer.write("\uFEFF")
    printer.write(header + "\n")

    val municipalities = Database.forDataSource(ds).withDynSession {
      getMunicipalities
    }
    municipalities.foreach(municipalityId => {
      val municipalityName = Database.forDataSource(ds).withDynSession {
        OracleSpatialAssetDao.getMunicipalityNameByCode(municipalityId)
      }
      val assets = Database.forDataSource(ds).withDynSession {
        getAssetsForMunicipality(municipalityId)
      }
      valluCsvRowsFromAssets(municipalityId, municipalityName, assets, valluComplementaryBusStopNames).foreach(x => printer.write(x + "\n"))
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

  def assetToValluCsvMassTransitStop(asset: AssetWithProperties): ValluCsvMassTransitStop = {
    ValluCsvMassTransitStop(nationalId = asset.nationalId, propertyData = asset.propertyData,
      lon = asset.lon, lat = asset.lat, bearing = asset.bearing, validityDirection = asset.validityDirection,
      created = asset.created, modified = asset.modified, administrativeClass = asset.roadLinkType)
  }

  def getAssetsForMunicipality(municipality: Int): immutable.Iterable[ValluCsvMassTransitStop] = {
    println(s"Get assets for municipality $municipality")
    OracleSpatialAssetDao.getAssetsByMunicipality(municipality).map(assetToValluCsvMassTransitStop)
  }

  def getMunicipalities = {
    OracleSpatialAssetDao.getMunicipalities
  }

  def main(args:Array[String]) : Unit = {
    if (Digiroad2Context.useVVHGeometry) {
      println("*** Vallu CSV export is currently disabled on a system using geometry from VVH server ***")
      System.exit(1)
    } else {
      ValluExport.writeCsvToFile()
      System.exit(0)
    }
  }
}
