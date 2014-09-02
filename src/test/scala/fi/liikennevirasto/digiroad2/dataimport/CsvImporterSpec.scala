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
  val mandatoryBusStopProperties = Map("vaikutussuunta" -> "2", "nimi_suomeksi" -> "AssetName", "pysakin_tyyppi" -> "2")

  before {
    userProvider.setCurrentUser(User(id = 1, username = "CsvImportApiSpec", configuration = Configuration(authorizedMunicipalities = Set(MunicipalityKauniainen))))
  }

  test("rowToString works correctly for few basic fields") {
    CsvImporter.rowToString(Map(
      "Valtakunnallinen ID" -> "ID",
      "Pysäkin nimi" -> "Nimi"
    )) should equal("Valtakunnallinen ID: 'ID', Pysäkin nimi: 'Nimi'")
  }

  val defaultKeys = "Valtakunnallinen ID" :: mappings.keys.toList

  val defaultValues = defaultKeys.map { key => key -> "" }.toMap

  private def createCSV(assets: Map[String, Any]*): String = {
    val headers = defaultKeys.mkString(";") + "\n"
    val rows = assets.map { asset =>
      defaultKeys.map { key => asset.getOrElse(key, "") }.mkString(";")
    }.mkString("\n")
    headers + rows
  }

  test("update name by CSV import", Tag("db")) {
    val asset = createAsset(roadLinkId = 5771, mandatoryBusStopProperties)
    val asset2 = createAsset(roadLinkId = 5771, mandatoryBusStopProperties)
    val csv = createCSV(Map("Valtakunnallinen ID" -> asset.externalId, "Pysäkin nimi" -> "UpdatedAssetName"),
                        Map("Valtakunnallinen ID" -> asset2.externalId, "Pysäkin nimi" -> "Asset2Name"))
    try {
      val inputStream = new ByteArrayInputStream(csv.getBytes)
      val result = CsvImporter.importAssets(inputStream, assetProvider)
      result should equal(ImportResult())

      val assetName = getAssetName(assetProvider.getAssetByExternalId(asset.externalId).get)
      assetName should equal(Some("UpdatedAssetName"))

      val assetName2 = getAssetName(assetProvider.getAssetByExternalId(asset2.externalId).get)
      assetName2 should equal(Some("Asset2Name"))
    } finally {
      removeAsset(asset.id, assetProvider)
      removeAsset(asset2.id, assetProvider)
    }
  }

  test("do not update name if field is empty in CSV", Tag("db")) {
    val asset = createAsset(roadLinkId = 5771, mandatoryBusStopProperties)
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
    val asset = createAsset(roadLinkId = 5771, mandatoryBusStopProperties)
    try {
      val assetFields = Map("Valtakunnallinen ID" -> asset.externalId, "Pysäkin tyyppi" -> ",")
      val invalidCsv = csvToInputStream(createCSV(assetFields))
      CsvImporter.importAssets(invalidCsv, assetProvider) should equal(ImportResult(
        malformedAssets = List(MalformedAsset(
          malformedParameters = List("Pysäkin tyyppi"),
          csvRow = rowToString(defaultValues ++ assetFields)))))
    } finally {
      removeAsset(asset.id, assetProvider)
    }
  }

  test("validation fails if type contains illegal characters", Tag("db")) {
    val asset = createAsset(roadLinkId = 5771, mandatoryBusStopProperties)
    try {
      val assetFields = Map("Valtakunnallinen ID" -> asset.externalId, "Pysäkin tyyppi" -> "2,a")
      val invalidCsv = csvToInputStream(createCSV(assetFields))
      CsvImporter.importAssets(invalidCsv, assetProvider) should equal(ImportResult(
        malformedAssets = List(MalformedAsset(
          malformedParameters = List("Pysäkin tyyppi"),
          csvRow = rowToString(defaultValues ++ assetFields)))))
    } finally {
      removeAsset(asset.id, assetProvider)
    }
  }

  test("validation fails when asset type is unknown", Tag("db")) {
    val asset = createAsset(roadLinkId = 5771, mandatoryBusStopProperties)
    try {
      val assetFields = Map("Valtakunnallinen ID" -> asset.externalId, "Pysäkin tyyppi" -> "2,10")
      val invalidCsv = csvToInputStream(createCSV(assetFields))
      CsvImporter.importAssets(invalidCsv, assetProvider) should equal(ImportResult(
        malformedAssets = List(MalformedAsset(
          malformedParameters = List("Pysäkin tyyppi"),
          csvRow = rowToString(defaultValues ++ assetFields)))))
    } finally {
      removeAsset(asset.id, assetProvider)
    }
  }

  test("update asset type by CSV import", Tag("db")) {
    val asset = createAsset(roadLinkId = 5771, mandatoryBusStopProperties)
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
    val asset = createAsset(roadLinkId = 5771, mandatoryBusStopProperties + ("yllapitajan_tunnus" -> "1111111"))
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
    val asset = createAsset(roadLinkId = 5771, mandatoryBusStopProperties + ("yllapitajan_koodi" -> "Livi123456"))
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
    val asset = createAsset(roadLinkId = 5771, mandatoryBusStopProperties + ("matkustajatunnus" -> "H155"))
    try {
      val csv = csvToInputStream(createCSV(Map("Valtakunnallinen ID" -> asset.externalId, "Matkustajatunnus" -> "H156")))
      CsvImporter.importAssets(csv, assetProvider) should equal(ImportResult())
      val assetStopCode = assetProvider.getAssetByExternalId(asset.externalId).get.getPropertyValue("matkustajatunnus")
      assetStopCode should equal(Some("H156"))
    } finally {
      removeAsset(asset.id, assetProvider)
    }
  }

  test("update additional information by CSV import", Tag("db")) {
    val asset = createAsset(roadLinkId = 5771, mandatoryBusStopProperties + ("lisatiedot" -> "Additional info"))
    try {
      val csv = csvToInputStream(createCSV(Map("Valtakunnallinen ID" -> asset.externalId, "Lisätiedot" -> "Updated additional info")))
      CsvImporter.importAssets(csv, assetProvider) should equal(ImportResult())
      val assetAdditionalInfo = assetProvider.getAssetByExternalId(asset.externalId).get.getPropertyValue("lisatiedot")
      assetAdditionalInfo should equal(Some("Updated additional info"))
    } finally {
      removeAsset(asset.id, assetProvider)
    }
  }

  val exampleValues = Map(
    "nimi_suomeksi" -> ("Passila", "Pasila"),
    "yllapitajan_tunnus" -> ("1234", "1281"),
    "yllapitajan_koodi" -> ("LiVV", "LiVi123"),
    "matkustajatunnus" -> ("sdjkal", "9877"),
    "pysakin_tyyppi" -> ("2", "1"),
    "nimi_ruotsiksi" -> ("Bölle", "Böle"),
    "liikennointisuunta" -> ("Itään", "Pohjoiseen"),
    "katos" -> ("1", "2"),
    "aikataulu" -> ("1", "2"),
    "mainoskatos" -> ("1", "2"),
    "penkki" -> ("1", "2"),
    "pyorateline" -> ("1", "2"),
    "sahkoinen_aikataulunaytto" -> ("1", "2"),
    "valaistus" -> ("1", "2"),
    "saattomahdollisuus_henkiloautolla" -> ("1", "2"),
    "lisatiedot" -> ("qwer", "asdf"),
    "tietojen_yllapitaja" -> ("1", "3")
  )

  test("update asset's properties in a generic manner", Tag("db")) {
    val asset = createAsset(roadLinkId = 5771, properties = exampleValues.mapValues(_._1) ++ Map("vaikutussuunta" -> "2"))
    try {
      val csv = csvToInputStream(createCSV(Map("Valtakunnallinen ID" -> asset.externalId) ++ mappings.mapValues(exampleValues(_)._2)))
      CsvImporter.importAssets(csv, assetProvider) should equal(ImportResult())
      val updatedAsset = assetProvider.getAssetByExternalId(asset.externalId).get
      exampleValues.foreach { case (k, v) =>
        updatedAsset.getPropertyValue(k) should equal(Some(v._2))
      }
    } finally {
      removeAsset(asset.id, assetProvider)
    }
  }

  test("raise an error when updating non-existent asset", Tag("db")) {
    val assetFields = Map("Valtakunnallinen ID" -> "600000", "Pysäkin nimi" -> "AssetName")
    val csv = createCSV(assetFields)

    val inputStream = new ByteArrayInputStream(csv.getBytes)
    val result = CsvImporter.importAssets(inputStream, assetProvider)

    result should equal(ImportResult(nonExistingAssets = List(NonExistingAsset(externalId = 600000, csvRow = rowToString(defaultValues ++ assetFields)))))
  }

  test("raise an error when csv row does not define required parameter", Tag("db")) {
    val missingRequiredKeys = defaultKeys.filterNot(Set("Pysäkin nimi"))
    val asset = createAsset(roadLinkId = 5771, mandatoryBusStopProperties)
    val csv =
      missingRequiredKeys.mkString(";") + "\n" +
      s"${asset.externalId}" + missingRequiredKeys.map(_ => ";").mkString + "\n"
    try {
      val inputStream = new ByteArrayInputStream(csv.getBytes)
      val result = CsvImporter.importAssets(inputStream, assetProvider)
      result should equal(ImportResult(
        incompleteAssets = List(IncompleteAsset(missingParameters = List("Pysäkin nimi"), csvRow = rowToString(defaultValues - "Pysäkin nimi" ++ Map("Valtakunnallinen ID" -> asset.externalId))))))

      val assetName = getAssetName(assetProvider.getAssetByExternalId(asset.externalId).get)
      assetName should equal(Some("AssetName"))
    } finally {
      removeAsset(asset.id, assetProvider)
    }
  }

  private val RoadId = 5806
  private val StreetId = 5821
  private val PrivateRoadId = 5804

  test("ignore updates on other road types than streets when import is limited to streets") {
    val asset = createAsset(roadLinkId = RoadId, mandatoryBusStopProperties)
    val assetFields = Map("Valtakunnallinen ID" -> asset.externalId, "Pysäkin nimi" -> "NewName")
    val csv = createCSV(assetFields)
    try {
      val inputStream = new ByteArrayInputStream(csv.getBytes)
      val result = CsvImporter.importAssets(inputStream, assetProvider, roadTypeLimitations = Set(Street))
      result should equal(ImportResult(
        excludedAssets = List(ExcludedAsset(affectedRoadLinkType = "Road", csvRow = rowToString(defaultValues ++ assetFields)))))

      val assetName = getAssetName(assetProvider.getAssetByExternalId(asset.externalId).get)
      assetName should equal(Some("AssetName"))
    } finally {
      removeAsset(asset.id, assetProvider)
    }
  }

  test("update asset on street when import is limited to streets") {
    val asset = createAsset(roadLinkId = StreetId, mandatoryBusStopProperties)
    val csv = createCSV(Map("Valtakunnallinen ID" -> asset.externalId, "Pysäkin nimi" -> "NewName"))
    try {
      val inputStream = new ByteArrayInputStream(csv.getBytes)
      val result = CsvImporter.importAssets(inputStream, assetProvider, roadTypeLimitations = Set(Street))
      result should equal(ImportResult())

      val assetName = getAssetName(assetProvider.getAssetByExternalId(asset.externalId).get)
      assetName should equal(Some("NewName"))
    } finally {
      removeAsset(asset.id, assetProvider)
    }
  }

  test("update asset on road when import is limited to roads") {
    val asset = createAsset(roadLinkId = RoadId, mandatoryBusStopProperties)
    val csv = createCSV(Map("Valtakunnallinen ID" -> asset.externalId, "Pysäkin nimi" -> "NewName"))
    try {
      val inputStream = new ByteArrayInputStream(csv.getBytes)
      val result = CsvImporter.importAssets(inputStream, assetProvider, roadTypeLimitations = Set(Road))
      result should equal(ImportResult())

      val assetName = getAssetName(assetProvider.getAssetByExternalId(asset.externalId).get)
      assetName should equal(Some("NewName"))
    } finally {
      removeAsset(asset.id, assetProvider)
    }
  }

  test("ignore updates on other road types than roads when import is limited to roads") {
    val asset = createAsset(roadLinkId = StreetId, mandatoryBusStopProperties)
    val assetFields = Map("Valtakunnallinen ID" -> asset.externalId, "Pysäkin nimi" -> "NewName")
    val csv = createCSV(assetFields)
    try {
      val inputStream = new ByteArrayInputStream(csv.getBytes)
      val result = CsvImporter.importAssets(inputStream, assetProvider, roadTypeLimitations = Set(Road))
      result should equal(ImportResult(
        excludedAssets = List(ExcludedAsset(affectedRoadLinkType = "Street", csvRow = rowToString(defaultValues ++ assetFields)))))

      val assetName = getAssetName(assetProvider.getAssetByExternalId(asset.externalId).get)
      assetName should equal(Some("AssetName"))
    } finally {
      removeAsset(asset.id, assetProvider)
    }
  }

  test("update asset on private road when import is limited to private roads") {
    val asset = createAsset(roadLinkId = PrivateRoadId, mandatoryBusStopProperties)
    val csv = createCSV(Map("Valtakunnallinen ID" -> asset.externalId, "Pysäkin nimi" -> "NewName"))
    try {
      val inputStream = new ByteArrayInputStream(csv.getBytes)
      val result = CsvImporter.importAssets(inputStream, assetProvider, roadTypeLimitations = Set(PrivateRoad))
      result should equal(ImportResult())

      val assetName = getAssetName(assetProvider.getAssetByExternalId(asset.externalId).get)
      assetName should equal(Some("NewName"))
    } finally {
      removeAsset(asset.id, assetProvider)
    }
  }

  test("ignore updates on other road types than private roads when import is limited to private roads") {
    val asset = createAsset(roadLinkId = StreetId, mandatoryBusStopProperties)
    val assetFields = Map("Valtakunnallinen ID" -> asset.externalId, "Pysäkin nimi" -> "NewName")
    val csv = createCSV(assetFields)
    try {
      val inputStream = new ByteArrayInputStream(csv.getBytes)
      val result = CsvImporter.importAssets(inputStream, assetProvider, roadTypeLimitations = Set(PrivateRoad))
      result should equal(ImportResult(
        excludedAssets = List(ExcludedAsset(affectedRoadLinkType = "Street", csvRow = rowToString(defaultValues ++ assetFields)))))

      val assetName = getAssetName(assetProvider.getAssetByExternalId(asset.externalId).get)
      assetName should equal(Some("AssetName"))
    } finally {
      removeAsset(asset.id, assetProvider)
    }
  }

  test("update asset on roads and streets when import is limited to roads and streets") {
    val assetOnStreet = createAsset(roadLinkId = StreetId, mandatoryBusStopProperties + ("nimi_suomeksi" -> "AssetName1"))
    val assetOnRoad = createAsset(roadLinkId = RoadId, mandatoryBusStopProperties + ("nimi_suomeksi" -> "AssetName2"))
    val csv = createCSV(Map("Valtakunnallinen ID" -> assetOnStreet.externalId, "Pysäkin nimi" -> "NewName1"), Map("Valtakunnallinen ID" -> assetOnRoad.externalId, "Pysäkin nimi" -> "NewName2"))
    try {
      val inputStream = new ByteArrayInputStream(csv.getBytes)
      val result = CsvImporter.importAssets(inputStream, assetProvider, roadTypeLimitations = Set(Road, Street))
      result should equal(ImportResult())

      val assetOnStreetName = getAssetName(assetProvider.getAssetByExternalId(assetOnStreet.externalId).get)
      val assetOnRoadName = getAssetName(assetProvider.getAssetByExternalId(assetOnRoad.externalId).get)
      assetOnStreetName should equal(Some("NewName1"))
      assetOnRoadName should equal(Some("NewName2"))
    } finally {
      removeAsset(assetOnStreet.id, assetProvider)
      removeAsset(assetOnRoad.id, assetProvider)
    }
  }

  test("ignore updates on all other road types than private roads when import is limited to private roads") {
    val assetOnStreet = createAsset(roadLinkId = StreetId, mandatoryBusStopProperties + ("nimi_suomeksi" -> "AssetName1"))
    val assetOnRoad = createAsset(roadLinkId = RoadId, mandatoryBusStopProperties + ("nimi_suomeksi" -> "AssetName2"))
    val assetOnStreetFields = Map("Valtakunnallinen ID" -> assetOnStreet.externalId, "Pysäkin nimi" -> "NewName1")
    val assetOnRoadFields = Map("Valtakunnallinen ID" -> assetOnRoad.externalId, "Pysäkin nimi" -> "NewName2")
    val csv = createCSV(assetOnStreetFields, assetOnRoadFields)
    try {
      val inputStream = new ByteArrayInputStream(csv.getBytes)
      val result = CsvImporter.importAssets(inputStream, assetProvider, roadTypeLimitations = Set(PrivateRoad))
      result should equal(ImportResult(
        excludedAssets = List(ExcludedAsset(affectedRoadLinkType = "Road", csvRow = rowToString(defaultValues ++ assetOnRoadFields)),
                              ExcludedAsset(affectedRoadLinkType = "Street", csvRow = rowToString(defaultValues ++ assetOnStreetFields)))))
      val assetOnStreetName = getAssetName(assetProvider.getAssetByExternalId(assetOnStreet.externalId).get)
      val assetOnRoadName = getAssetName(assetProvider.getAssetByExternalId(assetOnRoad.externalId).get)
      assetOnStreetName should equal(Some("AssetName1"))
      assetOnRoadName should equal(Some("AssetName2"))
    } finally {
      removeAsset(assetOnStreet.id, assetProvider)
      removeAsset(assetOnRoad.id, assetProvider)
    }
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
