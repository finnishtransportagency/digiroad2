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

  test("rowToString works correctly for few basic fields") {
    CsvImporter.rowToString(Map(
      "Valtakunnallinen ID" -> "ID",
      "Pysäkin nimi" -> "Nimi"
    )) should equal("Valtakunnallinen ID: 'ID', Pysäkin nimi: 'Nimi'")
  }

  test("update name by CSV import", Tag("db")) {
    val asset = createAsset(roadLinkId = 5771, properties = Map("vaikutussuunta" -> "2"))
    val asset2 = createAsset(roadLinkId = 5771, properties = Map("vaikutussuunta" -> "2"))
    val csv = createCSV(Map("Valtakunnallinen ID" -> asset.externalId, "Pysäkin nimi" -> "AssetName"),
                        Map("Valtakunnallinen ID" -> asset2.externalId, "Pysäkin nimi" -> "Asset2Name"))
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
    val csv = createCSV(Map("Valtakunnallinen ID" -> asset.externalId))
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
      val assetFields = Map("Valtakunnallinen ID" -> asset.externalId, "Pysäkin tyyppi" -> ",")
      val invalidCsv = csvToInputStream(createCSV(assetFields))
      CsvImporter.importAssets(invalidCsv, assetProvider) should equal(ImportResult(
        malformedAssets = List(MalformedAsset(
          malformedParameters = List("Pysäkin tyyppi"),
          csvRow = rowToString(DefaultFields ++ assetFields)))))
    } finally {
      removeAsset(asset.id, assetProvider)
    }
  }

  test("validation fails if type contains illegal characters", Tag("db")) {
    val asset = createAsset(roadLinkId = 5771, properties = Map("vaikutussuunta" -> "2", "pysakin_tyyppi" -> "1"))
    try {
      val assetFields = Map("Valtakunnallinen ID" -> asset.externalId, "Pysäkin tyyppi" -> "2,a")
      val invalidCsv = csvToInputStream(createCSV(assetFields))
      CsvImporter.importAssets(invalidCsv, assetProvider) should equal(ImportResult(
        malformedAssets = List(MalformedAsset(
          malformedParameters = List("Pysäkin tyyppi"),
          csvRow = rowToString(DefaultFields ++ assetFields)))))
    } finally {
      removeAsset(asset.id, assetProvider)
    }
  }

  test("validation fails when asset type is unknown", Tag("db")) {
    val asset = createAsset(roadLinkId = 5771, properties = Map("vaikutussuunta" -> "2", "pysakin_tyyppi" -> "1"))
    try {
      val assetFields = Map("Valtakunnallinen ID" -> asset.externalId, "Pysäkin tyyppi" -> "2,10")
      val invalidCsv = csvToInputStream(createCSV(assetFields))
      CsvImporter.importAssets(invalidCsv, assetProvider) should equal(ImportResult(
        malformedAssets = List(MalformedAsset(
          malformedParameters = List("Pysäkin tyyppi"),
          csvRow = rowToString(DefaultFields ++ assetFields)))))
    } finally {
      removeAsset(asset.id, assetProvider)
    }
  }

  test("update asset type by CSV import", Tag("db")) {
    val asset = createAsset(roadLinkId = 5771, properties = Map("vaikutussuunta" -> "2", "pysakin_tyyppi" -> "99"))
    try {
      val csv = csvToInputStream(createCSV(Map("Valtakunnallinen ID" -> asset.externalId, "Pysäkin tyyppi" ->  "1,2 , 3 ,4")))
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
      val csv = csvToInputStream(createCSV(Map("Valtakunnallinen ID" -> asset.externalId, "Ylläpitäjän tunnus" -> "2222222")))
      CsvImporter.importAssets(csv, assetProvider) should equal(ImportResult())
      val assetAdminId = assetProvider.getAssetByExternalId(asset.externalId).get.getPropertyValue("yllapitajan_tunnus")
      assetAdminId should equal(Some("2222222"))
    } finally {
      removeAsset(asset.id, assetProvider)
    }
  }

  test("update asset LiVi id by CSV import", Tag("db")) {
    val asset = createAsset(roadLinkId = 5771, properties = Map("vaikutussuunta" -> "2", "yllapitajan_koodi" -> "Livi123456"))
    try {
      val csv = csvToInputStream(createCSV(Map("Valtakunnallinen ID" -> asset.externalId, "LiVi-tunnus" -> "Livi987654")))
      CsvImporter.importAssets(csv, assetProvider) should equal(ImportResult())
      val assetLiviId = assetProvider.getAssetByExternalId(asset.externalId).get.getPropertyValue("yllapitajan_koodi")
      assetLiviId should equal(Some("Livi987654"))
    } finally {
      removeAsset(asset.id, assetProvider)
    }
  }

  test("update asset stop code by CSV import", Tag("db")) {
    val asset = createAsset(roadLinkId = 5771, properties = Map("vaikutussuunta" -> "2", "yllapitajan_koodi" -> "H155"))
    try {
      val csv = csvToInputStream(createCSV(Map("Valtakunnallinen ID" -> asset.externalId, "Matkustajatunnus" -> "H156")))
      CsvImporter.importAssets(csv, assetProvider) should equal(ImportResult())
      val assetStopCode = assetProvider.getAssetByExternalId(asset.externalId).get.getPropertyValue("matkustajatunnus")
      assetStopCode should equal(Some("H156"))
    } finally {
      removeAsset(asset.id, assetProvider)
    }
  }

  test("raise an error when updating non-existent asset", Tag("db")) {
    val assetFields = Map("Valtakunnallinen ID" -> "600000", "Pysäkin nimi" -> "AssetName")
    val csv = createCSV(assetFields)

    val inputStream = new ByteArrayInputStream(csv.getBytes)
    val result = CsvImporter.importAssets(inputStream, assetProvider)

    result should equal(ImportResult(nonExistingAssets = List(NonExistingAsset(externalId = 600000, csvRow = rowToString(DefaultFields ++ assetFields)))))
  }

  test("raise an error when csv row does not define required parameter", Tag("db")) {
    val asset = createAsset(roadLinkId = 5771, properties = Map("vaikutussuunta" -> "2", "nimi_suomeksi" -> "AssetName"))
    val csv =
      s"Valtakunnallinen ID;Ylläpitäjän tunnus;LiVi-tunnus;Matkustajatunnus;Pysäkin tyyppi\n" +
      s"${asset.externalId};;;;;;\n"
    try {
      val inputStream = new ByteArrayInputStream(csv.getBytes)
      val result = CsvImporter.importAssets(inputStream, assetProvider)
      result should equal(ImportResult(
        incompleteAssets = List(IncompleteAsset(missingParameters = List("Pysäkin nimi"), csvRow = rowToString(DefaultFields - "Pysäkin nimi" ++ Map("Valtakunnallinen ID" -> asset.externalId))))))

      val assetName = getAssetName(assetProvider.getAssetByExternalId(asset.externalId).get)
      assetName should equal(Some("AssetName"))
    } finally {
      removeAsset(asset.id, assetProvider)
    }
  }

  test("ignore updates on other road types than streets when import is limited to streets") {
    val roadId = 5806
    val asset = createAsset(roadLinkId = roadId, properties = Map("vaikutussuunta" -> "2", "nimi_suomeksi" -> "AssetName"))
    val assetFields = Map("Valtakunnallinen ID" -> asset.externalId, "Pysäkin nimi" -> "NewName")
    val csv = createCSV(assetFields)
    try {
      val inputStream = new ByteArrayInputStream(csv.getBytes)
      val result = CsvImporter.importAssets(inputStream, assetProvider, limitImportToStreets = true)
      result should equal(ImportResult(
        excludedAssets = List(ExcludedAsset(affectedRoadLinkType = "Road", csvRow = rowToString(DefaultFields ++ assetFields)))))

      val assetName = getAssetName(assetProvider.getAssetByExternalId(asset.externalId).get)
      assetName should equal(Some("AssetName"))
    } finally {
      removeAsset(asset.id, assetProvider)
    }
  }

  test("update asset on street when import is limited to streets") {
    val streetId = 5821
    val asset = createAsset(roadLinkId = streetId, properties = Map("vaikutussuunta" -> "2", "nimi_suomeksi" -> "AssetName"))
    val csv = createCSV(Map("Valtakunnallinen ID" -> asset.externalId, "Pysäkin nimi" -> "NewName"))
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

  val DefaultFields = Map(
    "Valtakunnallinen ID" -> "",
    "Pysäkin nimi" -> "",
    "Ylläpitäjän tunnus" -> "",
    "LiVi-tunnus" -> "",
    "Matkustajatunnus" -> "",
    "Pysäkin tyyppi" -> ""
  )

  private def createCSV(assets: Map[String, Any]*): String = {
    val headers = "Valtakunnallinen ID;Pysäkin nimi;Ylläpitäjän tunnus;LiVi-tunnus;Matkustajatunnus;Pysäkin tyyppi\n"
    val rows = assets.map { asset =>
      List(
        asset.getOrElse("Valtakunnallinen ID", ""),
        asset.getOrElse("Pysäkin nimi", ""),
        asset.getOrElse("Ylläpitäjän tunnus", ""),
        asset.getOrElse("LiVi-tunnus", ""),
        asset.getOrElse("Matkustajatunnus", ""),
        asset.getOrElse("Pysäkin tyyppi", "")
      ).mkString(";")
    }.mkString("\n")
    headers + rows
  }

  private def createAsset(roadLinkId: Long, properties: Map[String, String]): AssetWithProperties = {
    val propertySeq = properties.map { case (key, value) => SimpleProperty(publicId = key, values = Seq(PropertyValue(value))) }.toSeq
    assetProvider.createAsset(10, 0, 0, roadLinkId, 180, "CsvImportApiSpec", propertySeq)
  }

  private def csvToInputStream(csv: String): InputStream = new ByteArrayInputStream(csv.getBytes())

  private def getAssetName(asset: AssetWithProperties): Option[String] = asset.getPropertyValue("nimi_suomeksi")

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
