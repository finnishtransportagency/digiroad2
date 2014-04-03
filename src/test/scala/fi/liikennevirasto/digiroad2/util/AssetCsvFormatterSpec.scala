package fi.liikennevirasto.digiroad2.util

import org.scalatest._
import fi.liikennevirasto.digiroad2.asset.AssetWithProperties
import fi.liikennevirasto.digiroad2.asset.oracle.OracleSpatialAssetProvider
import fi.liikennevirasto.digiroad2.user.oracle.OracleUserProvider

class AssetCsvFormatterSpec extends FlatSpec with MustMatchers with BeforeAndAfter with BeforeAndAfterAll {
  val userProvider = new OracleUserProvider
  val provider = new OracleSpatialAssetProvider(userProvider)
  var assetsByMunicipality: Iterable[AssetWithProperties] = null
  before {
    assetsByMunicipality = provider.getAssetsByMunicipality(235)
  }

  it must "return correct csv entries from test data" in {
    val csvAll = AssetCsvFormatter.formatAssetsWithProperties(assetsByMunicipality)
    csvAll.size must be > 3
    val csv = csvAll.find(_.startsWith("1")).get

    val propertyValue = extractPropertyValue(assetsByMunicipality.find(_.externalId.get == 1).get, _: String)
    val created = parseCreated(propertyValue("created"))
    val validFrom = propertyValue("validFrom")
    val validTo = propertyValue("validTo")

    csv must equal("1;;;;;374635.608258218;6677267.45072414;;;80;;2;1;0;0;0;katos;Ei tiedossa;;" + created + ";dr1conversion;" + validFrom + ";" + validTo + ";;235;;;")
  }

  private def extractPropertyValue(asset: AssetWithProperties, propertyId: String): String = {
    asset.propertyData.find(_.propertyId == propertyId).get.values.head.propertyDisplayValue
  }

  private def parseCreated(s: String): String = {
    s.split(" ").drop(1).mkString(" ")
  }
}
