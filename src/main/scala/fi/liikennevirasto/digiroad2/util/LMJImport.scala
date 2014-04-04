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
    println(s"Get assets for municipality $municipality")
    provider.getAssetsByMunicipality(municipality)
  }

  def getLMJRowsForAsset(asset: AssetWithProperties) = {
    AssetLMJFormatter.formatFromAssetWithProperties(asset)
  }

  def getMunicipalities = {
    provider.getMunicipalities
  }

  def main(args:Array[String]) : Unit = {
    getMunicipalities.foreach { x =>
      getAssetsForMunicipality(x).map(getLMJRowsForAsset _).foreach { x =>
        printer.write(x + '\n')
      }
    }
    printer.close()
  }
}
