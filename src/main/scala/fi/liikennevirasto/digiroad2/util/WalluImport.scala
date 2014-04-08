package fi.liikennevirasto.digiroad2.util

import java.io._
import fi.liikennevirasto.digiroad2.user.oracle.OracleUserProvider
import fi.liikennevirasto.digiroad2.asset.oracle.OracleSpatialAssetProvider
import fi.liikennevirasto.digiroad2.asset.AssetWithProperties

object WalluImport {

  val userProvider = new OracleUserProvider
  val provider = new OracleSpatialAssetProvider(userProvider)

  def writeCsvToFile() = {
    val printer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("digiroad_stops.csv"), "UTF-8"))
    // BOM for excel
    printer.write("\uFEFF")
    getMunicipalities.foreach(x => {
      getAssetsForMunicipality(x)
        .map(getCsvRowsForAsset _)
        .foreach(x => printer.write(x + "\n"))
    })
    printer.close
  }

  def getCsvRowsForAsset(asset: AssetWithProperties) = {
    AssetCsvFormatter.formatFromAssetWithPropertiesValluCsv(asset)
  }

  def getAssetsForMunicipality(municipality: Int) = {
    println(s"Get assets for municipality $municipality")
    provider.getAssetsByMunicipality(municipality)
  }

  def getMunicipalities = {
    provider.getMunicipalities
  }

  def main(args:Array[String]) : Unit = {
    WalluImport.writeCsvToFile()
  }
}
