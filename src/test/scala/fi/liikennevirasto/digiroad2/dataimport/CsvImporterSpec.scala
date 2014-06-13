package fi.liikennevirasto.digiroad2.dataimport

import org.scalatest.{BeforeAndAfter, Tag}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.asset.PropertyValue
import fi.liikennevirasto.digiroad2.user.oracle.OracleUserProvider
import fi.liikennevirasto.digiroad2.asset.oracle.OracleSpatialAssetProvider
import fi.liikennevirasto.digiroad2.user.{Configuration, User}
import fi.liikennevirasto.digiroad2.{DummyEventBus, AuthenticatedApiSpec}
import java.io.ByteArrayInputStream
import fi.liikennevirasto.digiroad2.dataimport.CsvImporter.{IncompleteAsset, NonExistingAsset, ImportResult}

class CsvImporterSpec extends AuthenticatedApiSpec with BeforeAndAfter {
  val MunicipalityKauniainen = 235
  val userProvider = new OracleUserProvider
  val assetProvider = new OracleSpatialAssetProvider(new DummyEventBus, userProvider)

  before {
    userProvider.setCurrentUser(User(id = 1, username = "CsvImportApiSpec", configuration = Configuration(authorizedMunicipalities = Set(MunicipalityKauniainen))))
  }

  test("update name by CSV import", Tag("db")) {
    val asset = assetProvider.createAsset(10, 0, 0, 5771, 180, "CsvImportApiSpec", Seq(SimpleProperty(publicId = "vaikutussuunta", values = Seq(PropertyValue("2")))))
    val asset2 = assetProvider.createAsset(10, 0, 0, 5771, 180, "CsvImportApiSpec", Seq(SimpleProperty(publicId = "vaikutussuunta", values = Seq(PropertyValue("2")))))
    val csv =
      s"Valtakunnallinen ID;Pysäkin nimi\n" +
      s"${asset.externalId};AssetName\n" +
      s"${asset2.externalId};Asset2Name"
    try {
      val inputStream = new ByteArrayInputStream(csv.getBytes)
      val result = CsvImporter.importAssets(inputStream, assetProvider)
      result should equal(ImportResult(List(), List()))

      val assetName = getAssetName(assetProvider.getAssetByExternalId(asset.externalId))
      assetName should equal(Some("AssetName"))

      val assetName2 = getAssetName(assetProvider.getAssetByExternalId(asset2.externalId))
      assetName2 should equal(Some("Asset2Name"))
    } finally {
      removeAsset(asset.id, assetProvider)
      removeAsset(asset2.id, assetProvider)
    }
  }

  test("do not update name if field is empty in CSV", Tag("db")) {
    val asset = assetProvider.createAsset(10, 0, 0, 5771, 180, "CsvImportApiSpec", Seq(
      SimpleProperty(publicId = "vaikutussuunta", values = Seq(PropertyValue("2"))),
      SimpleProperty(publicId = "nimi_suomeksi", values = Seq(PropertyValue("AssetName")))))
    val csv =
      s"Valtakunnallinen ID;Pysäkin nimi\n" +
      s"${asset.externalId};\n"
    try {
      val inputStream = new ByteArrayInputStream(csv.getBytes)
      val result = CsvImporter.importAssets(inputStream, assetProvider)
      result should equal(ImportResult(List(), List()))

      val assetName = getAssetName(assetProvider.getAssetByExternalId(asset.externalId))
      assetName should equal(Some("AssetName"))
    } finally {
      removeAsset(asset.id, assetProvider)
    }
  }

  test("raise an error when updating non-existent asset", Tag("db")) {
    val csv =
      s"Valtakunnallinen ID;Pysäkin nimi\n" +
      s"600000;AssetName\n"

    val inputStream = new ByteArrayInputStream(csv.getBytes)
    val result = CsvImporter.importAssets(inputStream, assetProvider)

    result should equal(ImportResult(nonExistingAssets = List(NonExistingAsset(externalId = 600000, csvRow = "Valtakunnallinen ID: '600000', Pysäkin nimi: 'AssetName'")), List()))
  }

  test("raise an error when csv row does not define required parameter", Tag("db")) {
    val asset = assetProvider.createAsset(10, 0, 0, 5771, 180, "CsvImportApiSpec", Seq(
      SimpleProperty(publicId = "vaikutussuunta", values = Seq(PropertyValue("2"))),
      SimpleProperty(publicId = "nimi_suomeksi", values = Seq(PropertyValue("AssetName")))))
    val csv =
      s"Valtakunnallinen ID;Pysäkin nimi\n" +
      s"${asset.externalId}\n"
    try {
      val inputStream = new ByteArrayInputStream(csv.getBytes)
      val result = CsvImporter.importAssets(inputStream, assetProvider)
      result should equal(ImportResult(
        nonExistingAssets = List(),
        incompleteAssets = List(IncompleteAsset(missingParameters = List("Pysäkin nimi"), csvRow = s"Valtakunnallinen ID: '${asset.externalId}'"))))

      val assetName = getAssetName(assetProvider.getAssetByExternalId(asset.externalId))
      assetName should equal(Some("AssetName"))
    } finally {
      removeAsset(asset.id, assetProvider)
    }
  }

  private def getAssetName(optionalAsset: Option[AssetWithProperties]): Option[String] = {
    optionalAsset.flatMap(asset => asset.propertyData.find(property => property.publicId.equals("nimi_suomeksi"))
      .flatMap(property => property.values.headOption.map(value => value.propertyValue)))
  }

  private def removeAsset(assetId: Long, provider: OracleSpatialAssetProvider) = {
    try {
      assetProvider.removeAsset(assetId)
    } catch {
      // TODO: Remove handling of this exception once LRM position removal does not fail in test runs
      case e: LRMPositionDeletionFailed => println("Removing LRM Position of asset " + assetId + " failed: " + e.reason)
    }
  }

  // TODO: Warn about nonused fields
  // TODO: Should vallu message be sent when assets are updated using csv?
}
