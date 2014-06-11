package fi.liikennevirasto.digiroad2.dataimport

import org.scalatest.{BeforeAndAfter, Tag}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.asset.PropertyValue
import fi.liikennevirasto.digiroad2.user.oracle.OracleUserProvider
import fi.liikennevirasto.digiroad2.asset.oracle.OracleSpatialAssetProvider
import fi.liikennevirasto.digiroad2.user.{Configuration, User}
import fi.liikennevirasto.digiroad2.{DummyEventBus, AuthenticatedApiSpec}
import java.io.ByteArrayInputStream

class CsvImporterSpec extends AuthenticatedApiSpec with BeforeAndAfter {
  val MunicipalityKauniainen = 235
  val userProvider = new OracleUserProvider
  val assetProvider = new OracleSpatialAssetProvider(new DummyEventBus, userProvider)

  before {
    userProvider.setCurrentUser(User(id = 1, username = "CsvImportApiSpec", configuration = Configuration(authorizedMunicipalities = Set(MunicipalityKauniainen))))
  }

  test("update defined values in CSV import", Tag("db")) {
    val asset = assetProvider.createAsset(10, 0, 0, 5771, 180, "CsvImportApiSpec", Seq(SimpleProperty(publicId = "vaikutussuunta", values = Seq(PropertyValue("2")))))
    val asset2 = assetProvider.createAsset(10, 0, 0, 5771, 180, "CsvImportApiSpec", Seq(SimpleProperty(publicId = "vaikutussuunta", values = Seq(PropertyValue("2")))))
    val csv =
      s"Valtakunnallinen ID;Pysäkin nimi\n" +
      s"${asset.externalId};AssetName\n" +
      s"${asset2.externalId};Asset2Name"
    try {
      val inputStream = new ByteArrayInputStream(csv.getBytes)
      CsvImporter.importAssets(inputStream, assetProvider)

      val assetName = getAssetName(assetProvider.getAssetByExternalId(asset.externalId))
      assetName should equal(Some("AssetName"))

      val assetName2 = getAssetName(assetProvider.getAssetByExternalId(asset2.externalId))
      assetName2 should equal(Some("Asset2Name"))
    } finally {
      assetProvider.removeAsset(asset.id)
      assetProvider.removeAsset(asset2.id)
    }
  }

  test("do not update values if field is empty in CSV", Tag("db")) {
    val asset = assetProvider.createAsset(10, 0, 0, 5771, 180, "CsvImportApiSpec", Seq(
      SimpleProperty(publicId = "vaikutussuunta", values = Seq(PropertyValue("2"))),
      SimpleProperty(publicId = "nimi_suomeksi", values = Seq(PropertyValue("AssetName")))))
    val csv =
      s"Valtakunnallinen ID;Pysäkin nimi\n" +
        s"${asset.externalId};\n"
    try {
      val inputStream = new ByteArrayInputStream(csv.getBytes)
      CsvImporter.importAssets(inputStream, assetProvider)

      val assetName = getAssetName(assetProvider.getAssetByExternalId(asset.externalId))
      assetName should equal(Some("AssetName"))
    } finally {
      assetProvider.removeAsset(asset.id)
    }
  }

  private def getAssetName(optionalAsset: Option[AssetWithProperties]): Option[String] = {
    optionalAsset.flatMap(asset => asset.propertyData.find(property => property.publicId.equals("nimi_suomeksi"))
      .flatMap(property => property.values.headOption.map(value => value.propertyValue)))
  }

  // TODO: Error updating asset that does not exist
  // TODO: Error when entry has less / more fields than in headers
  // TODO: Warn about nonused fields
  // TODO: Test updating position
}
