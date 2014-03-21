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
    val csv = AssetCsvFormatter.formatFromAssetWithPropertiesValluCsv(assetsByMunicipality)
    csv.length must be (5)
    csv.head must equal("300000;1;1;;;374635.608258218;6677267.45072414;;;80;;2;1;0;0;0;katos;Ei tiedossa;;18.03.2014 15:55:29;dr1conversion;2013-03-18 00:00:00.0;2017-03-18 00:00:00.0;;235;;;")
    csv.drop(1).head must equal("300001;5;5;;;374792.096855508;6677566.77442972;;;210;;2;1;1;1;0;;Ei tiedossa;;18.03.2014 15:55:31;dr1conversion;2012-03-18 00:00:00.0;2013-03-18 00:00:00.0;;235;;;")
  }
}
