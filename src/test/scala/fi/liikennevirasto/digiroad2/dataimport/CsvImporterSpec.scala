package fi.liikennevirasto.digiroad2.dataimport

import org.scalatest.{BeforeAndAfter, Tag}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.asset.PropertyValue
import fi.liikennevirasto.digiroad2.user.oracle.OracleUserProvider
import fi.liikennevirasto.digiroad2.asset.oracle.OracleSpatialAssetProvider
import fi.liikennevirasto.digiroad2.user.{Configuration, User}
import fi.liikennevirasto.digiroad2.{DummyEventBus, AuthenticatedApiSpec}
import java.io.{InputStream, ByteArrayInputStream}
import fi.liikennevirasto.digiroad2.dataimport.CsvImporter._

class CsvImporterSpec extends AuthenticatedApiSpec with BeforeAndAfter {
  val MunicipalityKauniainen = 235
  val userProvider = new OracleUserProvider
  val assetProvider = new OracleSpatialAssetProvider(new DummyEventBus, userProvider)

  before {
    userProvider.setCurrentUser(User(id = 1, username = "CsvImportApiSpec", configuration = Configuration(authorizedMunicipalities = Set(MunicipalityKauniainen))))
  }

  test("update name by CSV import", Tag("db")) {
    val asset = createAsset(roadLinkId = 5771, properties = Map("vaikutussuunta" -> "2"))
    val asset2 = createAsset(roadLinkId = 5771, properties = Map("vaikutussuunta" -> "2"))
    val csv =
      s"Valtakunnallinen ID;Pysäkin nimi\n" +
      s"${asset.externalId};AssetName\n" +
      s"${asset2.externalId};Asset2Name"
    try {
      val inputStream = new ByteArrayInputStream(csv.getBytes)
      val result = CsvImporter.importAssets(inputStream, assetProvider)
      result should equal(ImportResult())

      val assetName = getAssetName(assetProvider.getAssetByExternalId(asset.externalId).get)
      assetName should equal(Some("AssetName"))

      val assetName2 = getAssetName(assetProvider.getAssetByExternalId(asset2.externalId).get)
      assetName2 should equal(Some("Asset2Name"))
    } finally {
      removeAsset(asset.id, assetProvider)
      removeAsset(asset2.id, assetProvider)
    }
  }

  test("do not update name if field is empty in CSV", Tag("db")) {
    val asset = createAsset(roadLinkId = 5771, properties = Map("vaikutussuunta" -> "2", "nimi_suomeksi" -> "AssetName"))
    val csv =
      s"Valtakunnallinen ID;Pysäkin nimi\n" +
      s"${asset.externalId};\n"
    try {
      val inputStream = new ByteArrayInputStream(csv.getBytes)
      val result = CsvImporter.importAssets(inputStream, assetProvider)
      result should equal(ImportResult())

      val assetName = getAssetName(assetProvider.getAssetByExternalId(asset.externalId).get)
      assetName should equal(Some("AssetName"))
    } finally {
      removeAsset(asset.id, assetProvider)
    }
  }

  test("validation fails if type is undefined", Tag("db")) {
    val asset = createAsset(roadLinkId = 5771, properties = Map("vaikutussuunta" -> "2", "pysakin_tyyppi" -> "1"))
    try {
      val invalidCsv = csvToInputStream(
        s"Valtakunnallinen ID;Pysäkin nimi;Pysäkin tyyppi\n" +
        s"${asset.externalId};;,\n")
      CsvImporter.importAssets(invalidCsv, assetProvider) should equal(ImportResult(
        malformedAssets = List(MalformedAsset(
          malformedParameters = List("Pysäkin tyyppi"),
          csvRow = s"Valtakunnallinen ID: '${asset.externalId}', Pysäkin nimi: '', Pysäkin tyyppi: ','"))))
    } finally {
      removeAsset(asset.id, assetProvider)
    }
  }

  test("validation fails if type contains illegal characters", Tag("db")) {
    val asset = createAsset(roadLinkId = 5771, properties = Map("vaikutussuunta" -> "2", "pysakin_tyyppi" -> "1"))
    try {
      val invalidCsv = csvToInputStream(
        s"Valtakunnallinen ID;Pysäkin nimi;Pysäkin tyyppi\n" +
        s"${asset.externalId};;2,a\n")
      CsvImporter.importAssets(invalidCsv, assetProvider) should equal(ImportResult(
        malformedAssets = List(MalformedAsset(
          malformedParameters = List("Pysäkin tyyppi"),
          csvRow = s"Valtakunnallinen ID: '${asset.externalId}', Pysäkin nimi: '', Pysäkin tyyppi: '2,a'"))))
    } finally {
      removeAsset(asset.id, assetProvider)
    }
  }

  test("validation fails when asset type is unknown", Tag("db")) {
    val asset = createAsset(roadLinkId = 5771, properties = Map("vaikutussuunta" -> "2", "pysakin_tyyppi" -> "1"))
    try {
      val invalidCsv = csvToInputStream(
        s"Valtakunnallinen ID;Pysäkin nimi;Pysäkin tyyppi\n" +
        s"${asset.externalId};;2,10\n")
      CsvImporter.importAssets(invalidCsv, assetProvider) should equal(ImportResult(
        malformedAssets = List(MalformedAsset(
          malformedParameters = List("Pysäkin tyyppi"),
          csvRow = s"Valtakunnallinen ID: '${asset.externalId}', Pysäkin nimi: '', Pysäkin tyyppi: '2,10'"))))
    } finally {
      removeAsset(asset.id, assetProvider)
    }
  }

  test("update asset type by CSV import", Tag("db")) {
    val asset = createAsset(roadLinkId = 5771, properties = Map("vaikutussuunta" -> "2", "pysakin_tyyppi" -> "99"))
    try {
      val csv = csvToInputStream(
        s"Valtakunnallinen ID;Pysäkin nimi;Pysäkin tyyppi\n" +
        s"${asset.externalId};; 1,2 , 3 ,4\n")
      CsvImporter.importAssets(csv, assetProvider) should equal(ImportResult())
      val assetType = getAssetType(assetProvider.getAssetByExternalId(asset.externalId).get)
      assetType should contain only ("1", "2", "3", "4")
    } finally {
      removeAsset(asset.id, assetProvider)
    }
  }

  test("update asset admin id by CSV import", Tag("db")) {
    val asset = createAsset(roadLinkId = 5771, properties = Map("vaikutussuunta" -> "2", "yllapitajan_tunnus" -> "1111111"))
    try {
      val csv = csvToInputStream(
        s"Valtakunnallinen ID;Pysäkin nimi;Ylläpitäjän tunnus\n" +
        s"${asset.externalId};;2222222\n")
      CsvImporter.importAssets(csv, assetProvider) should equal(ImportResult())
      val assetAdminId = getAssetAdminId(assetProvider.getAssetByExternalId(asset.externalId).get)
      assetAdminId should equal(Some("2222222"))
    } finally {
      removeAsset(asset.id, assetProvider)
    }
  }

  test("update asset LiVi id by CSV import", Tag("db")) {
    val asset = createAsset(roadLinkId = 5771, properties = Map("vaikutussuunta" -> "2", "yllapitajan_koodi" -> "Livi123456"))
    try {
      val csv = csvToInputStream(
        s"Valtakunnallinen ID;Pysäkin nimi;LiVi-tunnus\n" +
          s"${asset.externalId};;Livi987654\n")
      CsvImporter.importAssets(csv, assetProvider) should equal(ImportResult())
      val assetLiviId = getAssetLiviId(assetProvider.getAssetByExternalId(asset.externalId).get)
      assetLiviId should equal(Some("Livi987654"))
    } finally {
      removeAsset(asset.id, assetProvider)
    }
  }

  test("update asset stop code by CSV import", Tag("db")) {
    val asset = createAsset(roadLinkId = 5771, properties = Map("vaikutussuunta" -> "2", "yllapitajan_koodi" -> "H155"))
    try {
      val csv = csvToInputStream(
        s"Valtakunnallinen ID;Pysäkin nimi;Matkustajatunnus\n" +
          s"${asset.externalId};;H156\n")
      CsvImporter.importAssets(csv, assetProvider) should equal(ImportResult())
      val assetStopCode = getAssetStopCode(assetProvider.getAssetByExternalId(asset.externalId).get)
      assetStopCode should equal(Some("H156"))
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

    result should equal(ImportResult(nonExistingAssets = List(NonExistingAsset(externalId = 600000, csvRow = "Valtakunnallinen ID: '600000', Pysäkin nimi: 'AssetName'"))))
  }

  test("raise an error when csv row does not define required parameter", Tag("db")) {
    val asset = createAsset(roadLinkId = 5771, properties = Map("vaikutussuunta" -> "2", "nimi_suomeksi" -> "AssetName"))
    val csv =
      s"Valtakunnallinen ID;Pysäkin nimi\n" +
      s"${asset.externalId}\n"
    try {
      val inputStream = new ByteArrayInputStream(csv.getBytes)
      val result = CsvImporter.importAssets(inputStream, assetProvider)
      result should equal(ImportResult(
        incompleteAssets = List(IncompleteAsset(missingParameters = List("Pysäkin nimi"), csvRow = s"Valtakunnallinen ID: '${asset.externalId}'"))))

      val assetName = getAssetName(assetProvider.getAssetByExternalId(asset.externalId).get)
      assetName should equal(Some("AssetName"))
    } finally {
      removeAsset(asset.id, assetProvider)
    }
  }

  test("ignore updates on other road types than streets when import is limited to streets") {
    val roadId = 5806
    val asset = createAsset(roadLinkId = roadId, properties = Map("vaikutussuunta" -> "2", "nimi_suomeksi" -> "AssetName"))
    val csv =
      s"Valtakunnallinen ID;Pysäkin nimi\n" +
      s"${asset.externalId};NewName\n"
    try {
      val inputStream = new ByteArrayInputStream(csv.getBytes)
      val result = CsvImporter.importAssets(inputStream, assetProvider, limitImportToStreets = true)
      result should equal(ImportResult(
        excludedAssets = List(ExcludedAsset(affectedRoadLinkType = "Road", csvRow = s"Valtakunnallinen ID: '${asset.externalId}', Pysäkin nimi: 'NewName'"))))

      val assetName = getAssetName(assetProvider.getAssetByExternalId(asset.externalId).get)
      assetName should equal(Some("AssetName"))
    } finally {
      removeAsset(asset.id, assetProvider)
    }
  }

  test("update asset on street when import is limited to streets") {
    val streetId = 5821
    val asset = createAsset(roadLinkId = streetId, properties = Map("vaikutussuunta" -> "2", "nimi_suomeksi" -> "AssetName"))
    val csv =
      s"Valtakunnallinen ID;Pysäkin nimi\n" +
      s"${asset.externalId};NewName\n"
    try {
      val inputStream = new ByteArrayInputStream(csv.getBytes)
      val result = CsvImporter.importAssets(inputStream, assetProvider, limitImportToStreets = true)
      result should equal(ImportResult())

      val assetName = getAssetName(assetProvider.getAssetByExternalId(asset.externalId).get)
      assetName should equal(Some("NewName"))
    } finally {
      removeAsset(asset.id, assetProvider)
    }
  }

  private def createAsset(roadLinkId: Long, properties: Map[String, String]): AssetWithProperties = {
    val propertySeq = properties.map { case (key, value) => SimpleProperty(publicId = key, values = Seq(PropertyValue(value))) }.toSeq
    assetProvider.createAsset(10, 0, 0, roadLinkId, 180, "CsvImportApiSpec", propertySeq)
  }

  private def csvToInputStream(csv: String): InputStream = new ByteArrayInputStream(csv.getBytes())

  private def getAssetName(asset: AssetWithProperties): Option[String] = getAssetPropertyValue("nimi_suomeksi", asset)

  private def getAssetAdminId(asset: AssetWithProperties): Option[String] = getAssetPropertyValue("yllapitajan_tunnus", asset)

  private def getAssetLiviId(asset: AssetWithProperties): Option[String] = getAssetPropertyValue("yllapitajan_koodi", asset)

  private def getAssetStopCode(asset: AssetWithProperties): Option[String] = getAssetPropertyValue("matkustajatunnus", asset)

  private def getAssetPropertyValue(propertyName: String, asset: AssetWithProperties): Option[String] = {
     asset.propertyData.find(_.publicId.equals(propertyName))
       .flatMap(_.values.headOption.map(_.propertyValue))
  }

  private def getAssetType(asset: AssetWithProperties): List[String] = {
    asset.propertyData.find(property => property.publicId.equals("pysakin_tyyppi")).toList.flatMap { property =>
      property.values.toList.map { value =>
        value.propertyValue
      }
    }
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
