package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.user.oracle.OracleUserProvider
import fi.liikennevirasto.digiroad2.asset.oracle.OracleSpatialAssetProvider
import fi.liikennevirasto.digiroad2.asset.AssetWithProperties
import java.io.{FileOutputStream, OutputStreamWriter, BufferedWriter}

object LMJImport {

  val printer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("stops.txt"), "UTF-8"))
  val userProvider = new OracleUserProvider
  val provider = new OracleSpatialAssetProvider(userProvider)

  def getAssetsForMunicipality(municipality: Int) = {
    print(s"$municipality,")
    provider.getAssetsByMunicipality(municipality)
  }

  def getLMJRowsForAsset(asset: AssetWithProperties) = {
    AssetLMJFormatter.formatFromAssetWithProperties(asset)
  }

  def getMunicipalities = {
    provider.getMunicipalities
  }

  def writeAssetByMunicipality(municipalityCode: Int) {
    getAssetsForMunicipality(municipalityCode).map(getLMJRowsForAsset _).foreach { x =>
      printer.write(x + '\n')
    }
  }

  def main(args:Array[String]) : Unit = {
    if (args.length > 0) {
      println("Get assets for municipality:");
      if (args.head == "all") {
        getMunicipalities.foreach {
          x =>
            writeAssetByMunicipality(x)
        }
      } else args.foreach {
        x =>
          writeAssetByMunicipality(x.toInt)
      }
    } else {
      println("Usage: parameters <env> <all> or <env> <municipalitycode1> <municipalitycode2>")
      println("example './LMJ_import.sh dev 179 167'")

    }
  }
}
