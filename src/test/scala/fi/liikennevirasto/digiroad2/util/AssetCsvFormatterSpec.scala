package fi.liikennevirasto.digiroad2.util

import org.scalatest._
import fi.liikennevirasto.digiroad2.asset.AssetWithProperties
import fi.liikennevirasto.digiroad2.asset.oracle.OracleSpatialAssetProvider
import fi.liikennevirasto.digiroad2.user.oracle.OracleUserProvider

class AssetCsvFormatterSpec extends FlatSpec with MustMatchers with BeforeAndAfter with BeforeAndAfterAll {
  val userProvider = new OracleUserProvider
  val provider = new OracleSpatialAssetProvider(userProvider)
  var assetsByMunicipality: Seq[AssetWithProperties] = null
  before {
    assetsByMunicipality = provider.getAssetsByMunicipality(235)
  }

  it must "return correct csv entries from test data" in {
    val csv = AssetCsvFormatter.formatFromAssetWithPropertiesValluCsv(assetsByMunicipality).tail
    csv.length must be (4)

    val propertyValueOfFirst = extractPropertyValue(assetsByMunicipality.drop(1).head, _: String)
    val firstCreated = parseCreated(propertyValueOfFirst("created"))
    val firstValidFrom = propertyValueOfFirst("validFrom")
    val firstValidTo = propertyValueOfFirst("validTo")
    csv.head must equal("300001;5;5;;;374792.096855508;6677566.77442972;;;210;;2;1;1;1;0;;Ei tiedossa;;" + firstCreated + ";dr1conversion;" + firstValidFrom + ";" + firstValidTo + ";;235;;;")
    val propertyValueOfSecond = extractPropertyValue(assetsByMunicipality.drop(2).head, _: String)
    val secondCreated = parseCreated(propertyValueOfSecond("created"))
    val secondValidFrom = propertyValueOfSecond("validFrom")
    val secondValidTo = propertyValueOfSecond("validTo")
    csv.drop(1).head must equal("300004;2;2;;;374483.666384383;6677247.88841149;;;85;;2;1;0;0;0;katos;Ei tiedossa;;" + secondCreated + ";dr1conversion;" + secondValidFrom + ";" + secondValidTo + ";;235;;;")
  }

  private def extractPropertyValue(asset: AssetWithProperties, propertyId: String): String = {
    asset.propertyData.find(_.propertyId == propertyId).get.values.head.propertyDisplayValue
  }

  private def parseCreated(s: String): String = {
    s.split(" ").drop(1).mkString(" ")
  }
}
