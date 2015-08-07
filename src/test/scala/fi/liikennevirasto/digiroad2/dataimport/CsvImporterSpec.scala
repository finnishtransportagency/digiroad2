package fi.liikennevirasto.digiroad2.dataimport

import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import org.scalatest.{BeforeAndAfter, Tag}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.asset.PropertyValue
import fi.liikennevirasto.digiroad2.user.oracle.OracleUserProvider
import fi.liikennevirasto.digiroad2.asset.oracle.{DatabaseTransaction, OracleSpatialAssetProvider}
import fi.liikennevirasto.digiroad2.user.{Configuration, User}
import fi.liikennevirasto.digiroad2.{DummyEventBus, AuthenticatedApiSpec}
import java.io.{InputStream, ByteArrayInputStream}
import fi.liikennevirasto.digiroad2.dataimport.CsvImporter._
import scala.slick.driver.JdbcDriver.backend.Database
import scala.slick.driver.JdbcDriver.backend.Database.dynamicSession

class CsvImporterSpec extends AuthenticatedApiSpec with BeforeAndAfter {
  val passThroughTransaction = new DatabaseTransaction {
    override def withDynTransaction[T](f: => T): T = f
  }
  val MunicipalityKauniainen = 235
  val userProvider = new OracleUserProvider
  val assetProvider = new OracleSpatialAssetProvider(new DummyEventBus, userProvider, passThroughTransaction)
  val mandatoryBusStopProperties = Map("vaikutussuunta" -> "2", "nimi_suomeksi" -> "AssetName", "pysakin_tyyppi" -> "2")

  private def runWithCleanup(test: => Unit): Unit = {
    Database.forDataSource(OracleDatabase.ds).withDynTransaction {
      test
      dynamicSession.rollback()
    }
  }

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
    runWithCleanup {
      val csv = createCSV(Map("Valtakunnallinen ID" -> 1, "Pysäkin nimi" -> "UpdatedAssetName"),
        Map("Valtakunnallinen ID" -> 2, "Pysäkin nimi" -> "Asset2Name"))

      val inputStream = new ByteArrayInputStream(csv.getBytes)
      val result = CsvImporter.importAssets(inputStream, assetProvider)
      result should equal(ImportResult())

      val assetName = getAssetName(assetProvider.getAssetByExternalId(1).get)
      assetName should equal(Some("UpdatedAssetName"))

      val assetName2 = getAssetName(assetProvider.getAssetByExternalId(2).get)
      assetName2 should equal(Some("Asset2Name"))
    }
  }

  test("do not update name if field is empty in CSV", Tag("db")) {
    runWithCleanup {
      val csv = createCSV(Map("Valtakunnallinen ID" -> 1))

      val inputStream = new ByteArrayInputStream(csv.getBytes)
      val result = CsvImporter.importAssets(inputStream, assetProvider)
      result should equal(ImportResult())

      val assetName = getAssetName(assetProvider.getAssetByExternalId(1).get)
      assetName should equal(None)
    }
  }

  test("validation fails if type is undefined", Tag("db")) {
    runWithCleanup {
      val assetFields = Map("Valtakunnallinen ID" -> 1, "Pysäkin tyyppi" -> ",")
      val invalidCsv = csvToInputStream(createCSV(assetFields))
      CsvImporter.importAssets(invalidCsv, assetProvider) should equal(ImportResult(
        malformedAssets = List(MalformedAsset(
          malformedParameters = List("Pysäkin tyyppi"),
          csvRow = rowToString(defaultValues ++ assetFields)))))
    }
  }

  test("validation fails if type contains illegal characters", Tag("db")) {
    runWithCleanup {
      val assetFields = Map("Valtakunnallinen ID" -> 1, "Pysäkin tyyppi" -> "2,a")
      val invalidCsv = csvToInputStream(createCSV(assetFields))
      CsvImporter.importAssets(invalidCsv, assetProvider) should equal(ImportResult(
        malformedAssets = List(MalformedAsset(
          malformedParameters = List("Pysäkin tyyppi"),
          csvRow = rowToString(defaultValues ++ assetFields)))))
    }
  }

  test("validation fails when asset type is unknown", Tag("db")) {
    runWithCleanup {
      val assetFields = Map("Valtakunnallinen ID" -> 1, "Pysäkin tyyppi" -> "2,10")
      val invalidCsv = csvToInputStream(createCSV(assetFields))
      CsvImporter.importAssets(invalidCsv, assetProvider) should equal(ImportResult(
        malformedAssets = List(MalformedAsset(
          malformedParameters = List("Pysäkin tyyppi"),
          csvRow = rowToString(defaultValues ++ assetFields)))))
    }
  }

  test("update asset type by CSV import", Tag("db")) {
    runWithCleanup {
      val csv = csvToInputStream(createCSV(Map("Valtakunnallinen ID" -> 1, "Pysäkin tyyppi" -> "1,2 , 3 ,4")))
      CsvImporter.importAssets(csv, assetProvider) should equal(ImportResult())
      val assetType = getAssetType(assetProvider.getAssetByExternalId(1).get)
      assetType should contain only("1", "2", "3", "4")
    }
  }

  test("update asset admin id by CSV import", Tag("db")) {
    runWithCleanup {
      val csv = csvToInputStream(createCSV(Map("Valtakunnallinen ID" -> 1, "Ylläpitäjän tunnus" -> "2222222")))
      CsvImporter.importAssets(csv, assetProvider) should equal(ImportResult())
      val assetAdminId = assetProvider.getAssetByExternalId(1).get.getPropertyValue("yllapitajan_tunnus")
      assetAdminId should equal(Some("2222222"))
    }
  }

  test("update asset LiVi id by CSV import", Tag("db")) {
    runWithCleanup {
      val csv = csvToInputStream(createCSV(Map("Valtakunnallinen ID" -> 1, "LiVi-tunnus" -> "Livi987654")))
      CsvImporter.importAssets(csv, assetProvider) should equal(ImportResult())
      val assetLiviId = assetProvider.getAssetByExternalId(1).get.getPropertyValue("yllapitajan_koodi")
      assetLiviId should equal(Some("Livi987654"))
    }
  }

  test("update asset stop code by CSV import", Tag("db")) {
    runWithCleanup {
      val csv = csvToInputStream(createCSV(Map("Valtakunnallinen ID" -> 1, "Matkustajatunnus" -> "H156")))
      CsvImporter.importAssets(csv, assetProvider) should equal(ImportResult())
      val assetStopCode = assetProvider.getAssetByExternalId(1).get.getPropertyValue("matkustajatunnus")
      assetStopCode should equal(Some("H156"))
    }
  }

  test("update additional information by CSV import", Tag("db")) {
    runWithCleanup {
      val csv = csvToInputStream(createCSV(Map("Valtakunnallinen ID" -> 1, "Lisätiedot" -> "Updated additional info")))
      CsvImporter.importAssets(csv, assetProvider) should equal(ImportResult())
      val assetAdditionalInfo = assetProvider.getAssetByExternalId(1).get.getPropertyValue("lisatiedot")
      assetAdditionalInfo should equal(Some("Updated additional info"))
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
    runWithCleanup {
      val csv = csvToInputStream(createCSV(Map("Valtakunnallinen ID" -> 1) ++ mappings.mapValues(exampleValues(_)._2)))
      CsvImporter.importAssets(csv, assetProvider) should equal(ImportResult())
      val updatedAsset = assetProvider.getAssetByExternalId(1).get
      exampleValues.foreach { case (k, v) =>
        updatedAsset.getPropertyValue(k) should equal(Some(v._2))
      }
    }
  }

  test("raise an error when updating non-existent asset", Tag("db")) {
    runWithCleanup {
      val assetFields = Map("Valtakunnallinen ID" -> "600000", "Pysäkin nimi" -> "AssetName")
      val csv = createCSV(assetFields)

      val inputStream = new ByteArrayInputStream(csv.getBytes)
      val result = CsvImporter.importAssets(inputStream, assetProvider)

      result should equal(ImportResult(nonExistingAssets = List(NonExistingAsset(externalId = 600000, csvRow = rowToString(defaultValues ++ assetFields)))))
    }
  }

  test("raise an error when csv row does not define required parameter", Tag("db")) {
    runWithCleanup {
      val missingRequiredKeys = defaultKeys.filterNot(Set("Pysäkin nimi"))
      val csv =
        missingRequiredKeys.mkString(";") + "\n" +
          s"${1}" + missingRequiredKeys.map(_ => ";").mkString + "\n"
      val inputStream = new ByteArrayInputStream(csv.getBytes)
      val result = CsvImporter.importAssets(inputStream, assetProvider)
      result should equal(ImportResult(
        incompleteAssets = List(IncompleteAsset(missingParameters = List("Pysäkin nimi"), csvRow = rowToString(defaultValues - "Pysäkin nimi" ++ Map("Valtakunnallinen ID" -> 1))))))

      val assetName = getAssetName(assetProvider.getAssetByExternalId(1).get)
      assetName should equal(None)
    }
  }

  private val RoadId = 7082
  private val StreetId = 7118
  private val PrivateRoadId = 7078

  test("ignore updates on other road types than streets when import is limited to streets") {
    runWithCleanup {
      val asset = createAsset(roadLinkId = RoadId, mandatoryBusStopProperties)
      val assetFields = Map("Valtakunnallinen ID" -> asset.nationalId, "Pysäkin nimi" -> "NewName")
      val csv = createCSV(assetFields)
      val inputStream = new ByteArrayInputStream(csv.getBytes)
      val result = CsvImporter.importAssets(inputStream, assetProvider, roadTypeLimitations = Set(Municipality))
      result should equal(ImportResult(
        excludedAssets = List(ExcludedAsset(affectedRoadLinkType = "State", csvRow = rowToString(defaultValues ++ assetFields)))))

      val assetName = getAssetName(assetProvider.getAssetByExternalId(asset.nationalId).get)
      assetName should equal(Some("AssetName"))
    }
  }

  test("update asset on street when import is limited to streets") {
    runWithCleanup {
      val csv = createCSV(Map("Valtakunnallinen ID" -> 1, "Pysäkin nimi" -> "NewName"))
      val inputStream = new ByteArrayInputStream(csv.getBytes)
      val result = CsvImporter.importAssets(inputStream, assetProvider, roadTypeLimitations = Set(Municipality))
      result should equal(ImportResult())

      val assetName = getAssetName(assetProvider.getAssetByExternalId(1).get)
      assetName should equal(Some("NewName"))
    }
  }

  test("update asset on road when import is limited to roads") {
    runWithCleanup {
      val asset = createAsset(roadLinkId = RoadId, mandatoryBusStopProperties)
      val csv = createCSV(Map("Valtakunnallinen ID" -> asset.nationalId, "Pysäkin nimi" -> "NewName"))
      val inputStream = new ByteArrayInputStream(csv.getBytes)
      val result = CsvImporter.importAssets(inputStream, assetProvider, roadTypeLimitations = Set(State))
      result should equal(ImportResult())

      val assetName = getAssetName(assetProvider.getAssetByExternalId(asset.nationalId).get)
      assetName should equal(Some("NewName"))
    }
  }

  test("ignore updates on other road types than roads when import is limited to roads") {
    runWithCleanup {
      val assetFields = Map("Valtakunnallinen ID" -> 1, "Pysäkin nimi" -> "NewName")
      val csv = createCSV(assetFields)
      val inputStream = new ByteArrayInputStream(csv.getBytes)
      val result = CsvImporter.importAssets(inputStream, assetProvider, roadTypeLimitations = Set(State))
      result should equal(ImportResult(
        excludedAssets = List(ExcludedAsset(affectedRoadLinkType = "Municipality", csvRow = rowToString(defaultValues ++ assetFields)))))

      val assetName = getAssetName(assetProvider.getAssetByExternalId(1).get)
      assetName should equal(None)
    }
  }

  test("update asset on private road when import is limited to private roads") {
    runWithCleanup {
      val asset = createAsset(roadLinkId = PrivateRoadId, mandatoryBusStopProperties)
      val csv = createCSV(Map("Valtakunnallinen ID" -> asset.nationalId, "Pysäkin nimi" -> "NewName"))
      val inputStream = new ByteArrayInputStream(csv.getBytes)
      val result = CsvImporter.importAssets(inputStream, assetProvider, roadTypeLimitations = Set(Private))
      result should equal(ImportResult())

      val assetName = getAssetName(assetProvider.getAssetByExternalId(asset.nationalId).get)
      assetName should equal(Some("NewName"))
    }
  }

  test("ignore updates on other road types than private roads when import is limited to private roads") {
    runWithCleanup {
      val assetFields = Map("Valtakunnallinen ID" -> 1, "Pysäkin nimi" -> "NewName")
      val csv = createCSV(assetFields)
      val inputStream = new ByteArrayInputStream(csv.getBytes)
      val result = CsvImporter.importAssets(inputStream, assetProvider, roadTypeLimitations = Set(Private))
      result should equal(ImportResult(
        excludedAssets = List(ExcludedAsset(affectedRoadLinkType = "Municipality", csvRow = rowToString(defaultValues ++ assetFields)))))

      val assetName = getAssetName(assetProvider.getAssetByExternalId(1).get)
      assetName should equal(None)
    }
  }

  test("update asset on roads and streets when import is limited to roads and streets") {
    runWithCleanup {
      val assetOnStreet = createAsset(roadLinkId = StreetId, mandatoryBusStopProperties + ("nimi_suomeksi" -> "AssetName1"))
      val assetOnRoad = createAsset(roadLinkId = RoadId, mandatoryBusStopProperties + ("nimi_suomeksi" -> "AssetName2"))
      val csv = createCSV(Map("Valtakunnallinen ID" -> assetOnStreet.nationalId, "Pysäkin nimi" -> "NewName1"), Map("Valtakunnallinen ID" -> assetOnRoad.nationalId, "Pysäkin nimi" -> "NewName2"))
      val inputStream = new ByteArrayInputStream(csv.getBytes)
      val result = CsvImporter.importAssets(inputStream, assetProvider, roadTypeLimitations = Set(State, Municipality))
      result should equal(ImportResult())

      val assetOnStreetName = getAssetName(assetProvider.getAssetByExternalId(assetOnStreet.nationalId).get)
      val assetOnRoadName = getAssetName(assetProvider.getAssetByExternalId(assetOnRoad.nationalId).get)
      assetOnStreetName should equal(Some("NewName1"))
      assetOnRoadName should equal(Some("NewName2"))
    }
  }

  test("ignore updates on all other road types than private roads when import is limited to private roads") {
    runWithCleanup {
      val assetOnStreet = createAsset(roadLinkId = StreetId, mandatoryBusStopProperties + ("nimi_suomeksi" -> "AssetName1"))
      val assetOnRoad = createAsset(roadLinkId = RoadId, mandatoryBusStopProperties + ("nimi_suomeksi" -> "AssetName2"))
      val assetOnStreetFields = Map("Valtakunnallinen ID" -> assetOnStreet.nationalId, "Pysäkin nimi" -> "NewName1")
      val assetOnRoadFields = Map("Valtakunnallinen ID" -> assetOnRoad.nationalId, "Pysäkin nimi" -> "NewName2")
      val csv = createCSV(assetOnStreetFields, assetOnRoadFields)
      val inputStream = new ByteArrayInputStream(csv.getBytes)
      val result = CsvImporter.importAssets(inputStream, assetProvider, roadTypeLimitations = Set(Private))
      result should equal(ImportResult(
        excludedAssets = List(ExcludedAsset(affectedRoadLinkType = "State", csvRow = rowToString(defaultValues ++ assetOnRoadFields)),
          ExcludedAsset(affectedRoadLinkType = "Municipality", csvRow = rowToString(defaultValues ++ assetOnStreetFields)))))
      val assetOnStreetName = getAssetName(assetProvider.getAssetByExternalId(assetOnStreet.nationalId).get)
      val assetOnRoadName = getAssetName(assetProvider.getAssetByExternalId(assetOnRoad.nationalId).get)
      assetOnStreetName should equal(Some("AssetName1"))
      assetOnRoadName should equal(Some("AssetName2"))
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

  // TODO: Warn about nonused fields
  // TODO: Should vallu message be sent when assets are updated using csv?
}
