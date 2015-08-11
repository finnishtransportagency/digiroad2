package fi.liikennevirasto.digiroad2.dataimport

import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import org.mockito.Matchers
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.mock.MockitoSugar
import org.scalatest.{BeforeAndAfter, Tag}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.asset.PropertyValue
import fi.liikennevirasto.digiroad2.user.oracle.OracleUserProvider
import fi.liikennevirasto.digiroad2.asset.oracle.{DatabaseTransaction, OracleSpatialAssetProvider}
import fi.liikennevirasto.digiroad2.user.{UserProvider, Configuration, User}
import fi.liikennevirasto.digiroad2._
import java.io.{InputStream, ByteArrayInputStream}
import fi.liikennevirasto.digiroad2.dataimport.CsvImporter._
import scala.slick.driver.JdbcDriver.backend.Database
import scala.slick.driver.JdbcDriver.backend.Database.dynamicSession
import org.mockito.Matchers._
import org.mockito.Mockito._

class CsvImporterSpec extends AuthenticatedApiSpec with BeforeAndAfter {
  val passThroughTransaction = new DatabaseTransaction {
    override def withDynTransaction[T](f: => T): T = f
  }
  val MunicipalityKauniainen = 235
  val testUserProvider = new OracleUserProvider
  val assetProvider = new OracleSpatialAssetProvider(new DummyEventBus, testUserProvider, passThroughTransaction)
  val mandatoryBusStopProperties = Map("vaikutussuunta" -> "2", "nimi_suomeksi" -> "AssetName", "pysakin_tyyppi" -> "2")
  val csvImporter = importerWithNullService()

  private def importerWithService(service: MassTransitStopService, rlService: RoadLinkService = MockitoSugar.mock[RoadLinkService]) : CsvImporter = {
    new CsvImporter {
      override val massTransitStopService: MassTransitStopService = service
      override val userProvider: UserProvider = testUserProvider
      override val roadLinkService: RoadLinkService = rlService
    }
  }

  private def importerWithNullService() : CsvImporter = {
    new CsvImporter {
      override val massTransitStopService: MassTransitStopService = MockitoSugar.mock[MassTransitStopService]
      override val userProvider: UserProvider = testUserProvider
      override val roadLinkService: RoadLinkService = MockitoSugar.mock[RoadLinkService]
    }
  }

  private def runWithCleanup(test: => Unit): Unit = {
    Database.forDataSource(OracleDatabase.ds).withDynTransaction {
      test
      dynamicSession.rollback()
    }
  }

  before {
    testUserProvider.setCurrentUser(User(id = 1, username = "CsvImportApiSpec", configuration = Configuration(authorizedMunicipalities = Set(MunicipalityKauniainen))))
  }

  test("rowToString works correctly for few basic fields") {
    csvImporter.rowToString(Map(
      "Valtakunnallinen ID" -> "ID",
      "Pysäkin nimi" -> "Nimi"
    )) should equal("Valtakunnallinen ID: 'ID', Pysäkin nimi: 'Nimi'")
  }

  val defaultKeys = "Valtakunnallinen ID" :: csvImporter.mappings.keys.toList

  val defaultValues = defaultKeys.map { key => key -> "" }.toMap

  private def createCSV(assets: Map[String, Any]*): String = {
    val headers = defaultKeys.mkString(";") + "\n"
    val rows = assets.map { asset =>
      defaultKeys.map { key => asset.getOrElse(key, "") }.mkString(";")
    }.mkString("\n")
    headers + rows
  }

  test("update name by CSV import", Tag("db")) {
    val mockService = MockitoSugar.mock[MassTransitStopService]
    when(mockService.getMassTransitStopByNationalId(Matchers.eq(1l), anyObject())).thenReturn(Some(MassTransitStopWithProperties(1, 1, Nil, 0.0, 0.0, None, None, None, false, Nil)))
    when(mockService.getMassTransitStopByNationalId(Matchers.eq(2l), anyObject())).thenReturn(Some(MassTransitStopWithProperties(2, 2, Nil, 0.0, 0.0, None, None, None, false, Nil)))

    val importer = importerWithService(mockService)
    val csv = createCSV(Map("Valtakunnallinen ID" -> 1, "Pysäkin nimi" -> "UpdatedAssetName"),
      Map("Valtakunnallinen ID" -> 2, "Pysäkin nimi" -> "Asset2Name"))

    val inputStream = new ByteArrayInputStream(csv.getBytes)
    val result = importer.importAssets(inputStream, assetProvider)
    result should equal(ImportResult())

    val properties1 = Set(SimpleProperty("nimi_suomeksi", Seq(PropertyValue("UpdatedAssetName"))))
    val properties2 = Set(SimpleProperty("nimi_suomeksi", Seq(PropertyValue("Asset2Name"))))
    verify(mockService).updateExistingById(Matchers.eq(1l), Matchers.eq(None), Matchers.eq(properties1), Matchers.eq("CsvImportApiSpec"), anyObject())
    verify(mockService).updateExistingById(Matchers.eq(2l), Matchers.eq(None), Matchers.eq(properties2), Matchers.eq("CsvImportApiSpec"), anyObject())
  }

  test("do not update name if field is empty in CSV", Tag("db")) {
    val mockService = MockitoSugar.mock[MassTransitStopService]
    when(mockService.getMassTransitStopByNationalId(Matchers.eq(1l), anyObject())).thenReturn(Some(MassTransitStopWithProperties(1, 1, Nil, 0.0, 0.0, None, None, None, false, Nil)))

    val importer = importerWithService(mockService)
    val csv = createCSV(Map("Valtakunnallinen ID" -> 1))

    val inputStream = new ByteArrayInputStream(csv.getBytes)
    val result = importer.importAssets(inputStream, assetProvider)
    result should equal(ImportResult())

    verify(mockService).updateExistingById(Matchers.eq(1l), Matchers.eq(None), Matchers.eq(Set.empty), Matchers.eq("CsvImportApiSpec"), anyObject())
  }

  test("validation fails if type is undefined", Tag("db")) {
    val assetFields = Map("Valtakunnallinen ID" -> 1, "Pysäkin tyyppi" -> ",")
    val invalidCsv = csvToInputStream(createCSV(assetFields))
    csvImporter.importAssets(invalidCsv, assetProvider) should equal(ImportResult(
      malformedAssets = List(MalformedAsset(
        malformedParameters = List("Pysäkin tyyppi"),
        csvRow = csvImporter.rowToString(defaultValues ++ assetFields)))))
  }

  test("validation fails if type contains illegal characters", Tag("db")) {
    val assetFields = Map("Valtakunnallinen ID" -> 1, "Pysäkin tyyppi" -> "2,a")
    val invalidCsv = csvToInputStream(createCSV(assetFields))
    csvImporter.importAssets(invalidCsv, assetProvider) should equal(ImportResult(
      malformedAssets = List(MalformedAsset(
        malformedParameters = List("Pysäkin tyyppi"),
        csvRow = csvImporter.rowToString(defaultValues ++ assetFields)))))
  }

  test("validation fails when asset type is unknown", Tag("db")) {
    val assetFields = Map("Valtakunnallinen ID" -> 1, "Pysäkin tyyppi" -> "2,10")
    val invalidCsv = csvToInputStream(createCSV(assetFields))
    csvImporter.importAssets(invalidCsv, assetProvider) should equal(ImportResult(
      malformedAssets = List(MalformedAsset(
        malformedParameters = List("Pysäkin tyyppi"),
        csvRow = csvImporter.rowToString(defaultValues ++ assetFields)))))
  }

  test("update asset type by CSV import", Tag("db")) {
    val mockService = MockitoSugar.mock[MassTransitStopService]
    when(mockService.getMassTransitStopByNationalId(Matchers.eq(1l), anyObject())).thenReturn(Some(MassTransitStopWithProperties(1, 1, Nil, 0.0, 0.0, None, None, None, false, Nil)))

    val importer = importerWithService(mockService)
    val csv = csvToInputStream(createCSV(Map("Valtakunnallinen ID" -> 1, "Pysäkin tyyppi" -> "1,2 , 3 ,4")))

    importer.importAssets(csv, assetProvider) should equal(ImportResult())

    val properties = Set(SimpleProperty("pysakin_tyyppi", Seq(PropertyValue("4"), PropertyValue("3"), PropertyValue("2"), PropertyValue("1"))))
    verify(mockService).updateExistingById(Matchers.eq(1l), Matchers.eq(None), Matchers.eq(properties), Matchers.eq("CsvImportApiSpec"), anyObject())
  }

  test("update asset admin id by CSV import", Tag("db")) {
    val mockService = MockitoSugar.mock[MassTransitStopService]
    when(mockService.getMassTransitStopByNationalId(Matchers.eq(1l), anyObject())).thenReturn(Some(MassTransitStopWithProperties(1, 1, Nil, 0.0, 0.0, None, None, None, false, Nil)))

    val importer = importerWithService(mockService)
    val csv = csvToInputStream(createCSV(Map("Valtakunnallinen ID" -> 1, "Ylläpitäjän tunnus" -> "NewAdminId")))

    importer.importAssets(csv, assetProvider) should equal(ImportResult())
    val properties = Set(SimpleProperty("yllapitajan_tunnus", Seq(PropertyValue("NewAdminId"))))
    verify(mockService).updateExistingById(Matchers.eq(1l), Matchers.eq(None), Matchers.eq(properties), Matchers.eq("CsvImportApiSpec"), anyObject())
  }

  test("update asset LiVi id by CSV import", Tag("db")) {
    val mockService = MockitoSugar.mock[MassTransitStopService]
    when(mockService.getMassTransitStopByNationalId(Matchers.eq(1l), anyObject())).thenReturn(Some(MassTransitStopWithProperties(1, 1, Nil, 0.0, 0.0, None, None, None, false, Nil)))

    val importer = importerWithService(mockService)
    val csv = csvToInputStream(createCSV(Map("Valtakunnallinen ID" -> 1, "LiVi-tunnus" -> "Livi987654")))

    importer.importAssets(csv, assetProvider) should equal(ImportResult())
    val properties = Set(SimpleProperty("yllapitajan_koodi", Seq(PropertyValue("Livi987654"))))
    verify(mockService).updateExistingById(Matchers.eq(1l), Matchers.eq(None), Matchers.eq(properties), Matchers.eq("CsvImportApiSpec"), anyObject())
  }

  test("update asset stop code by CSV import", Tag("db")) {
    val mockService = MockitoSugar.mock[MassTransitStopService]
    when(mockService.getMassTransitStopByNationalId(Matchers.eq(1l), anyObject())).thenReturn(Some(MassTransitStopWithProperties(1, 1, Nil, 0.0, 0.0, None, None, None, false, Nil)))

    val importer = importerWithService(mockService)
    val csv = csvToInputStream(createCSV(Map("Valtakunnallinen ID" -> 1, "Matkustajatunnus" -> "H156")))

    importer.importAssets(csv, assetProvider) should equal(ImportResult())
    val properties = Set(SimpleProperty("matkustajatunnus", Seq(PropertyValue("H156"))))
    verify(mockService).updateExistingById(Matchers.eq(1l), Matchers.eq(None), Matchers.eq(properties), Matchers.eq("CsvImportApiSpec"), anyObject())
  }

  test("update additional information by CSV import", Tag("db")) {
    val mockService = MockitoSugar.mock[MassTransitStopService]
    when(mockService.getMassTransitStopByNationalId(Matchers.eq(1l), anyObject())).thenReturn(Some(MassTransitStopWithProperties(1, 1, Nil, 0.0, 0.0, None, None, None, false, Nil)))

    val importer = importerWithService(mockService)
    val csv = csvToInputStream(createCSV(Map("Valtakunnallinen ID" -> 1, "Lisätiedot" -> "Updated additional info")))

    importer.importAssets(csv, assetProvider) should equal(ImportResult())
    val properties = Set(SimpleProperty("lisatiedot", Seq(PropertyValue("Updated additional info"))))
    verify(mockService).updateExistingById(Matchers.eq(1l), Matchers.eq(None), Matchers.eq(properties), Matchers.eq("CsvImportApiSpec"), anyObject())
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
    val mockService = MockitoSugar.mock[MassTransitStopService]
    when(mockService.getMassTransitStopByNationalId(Matchers.eq(1l), anyObject())).thenReturn(Some(MassTransitStopWithProperties(1, 1, Nil, 0.0, 0.0, None, None, None, false, Nil)))

    val importer = importerWithService(mockService)
    val csv = csvToInputStream(createCSV(Map("Valtakunnallinen ID" -> 1) ++ csvImporter.mappings.mapValues(exampleValues(_)._2)))

    importer.importAssets(csv, assetProvider) should equal(ImportResult())
    val properties: Set[SimpleProperty] = exampleValues.map { case (key, value) =>
      SimpleProperty(key, Seq(PropertyValue(value._2)))
    }.toSet

    verify(mockService).updateExistingById(Matchers.eq(1l), Matchers.eq(None), Matchers.eq(properties), Matchers.eq("CsvImportApiSpec"), anyObject())
  }

  test("raise an error when updating non-existent asset", Tag("db")) {
    val mockService = MockitoSugar.mock[MassTransitStopService]
    when(mockService.getMassTransitStopByNationalId(Matchers.eq(6l), anyObject())).thenReturn(None)
    val assetFields = Map("Valtakunnallinen ID" -> "6", "Pysäkin nimi" -> "AssetName")

    val importer = importerWithService(mockService)
    val csv = createCSV(assetFields)

    val inputStream = new ByteArrayInputStream(csv.getBytes)
    val result = importer.importAssets(inputStream, assetProvider)

    result should equal(ImportResult(nonExistingAssets = List(NonExistingAsset(externalId = 6, csvRow = importer.rowToString(defaultValues ++ assetFields)))))
  }

  test("raise an error when csv row does not define required parameter", Tag("db")) {
    runWithCleanup {
      val missingRequiredKeys = defaultKeys.filterNot(Set("Pysäkin nimi"))
      val csv =
        missingRequiredKeys.mkString(";") + "\n" +
          s"${1}" + missingRequiredKeys.map(_ => ";").mkString + "\n"
      val inputStream = new ByteArrayInputStream(csv.getBytes)
      val result = csvImporter.importAssets(inputStream, assetProvider)
      result should equal(ImportResult(
        incompleteAssets = List(IncompleteAsset(missingParameters = List("Pysäkin nimi"), csvRow = csvImporter.rowToString(defaultValues - "Pysäkin nimi" ++ Map("Valtakunnallinen ID" -> 1))))))

      val assetName = getAssetName(assetProvider.getAssetByExternalId(1).get)
      assetName should equal(None)
    }
  }

  private val RoadId = 7082
  private val StreetId = 7118
  private val PrivateRoadId = 7078

  private def mockWithMassTransitStops(stops: Seq[(Long, AdministrativeClass)]): (MassTransitStopService, RoadLinkService) = {
    val mockMassTransitStopService = MockitoSugar.mock[MassTransitStopService]
    stops.foreach { case (id, administrativeClass) =>
      when(mockMassTransitStopService.getByNationalId(Matchers.eq(id), anyObject(), anyObject())).thenAnswer(new Answer[Option[Object]] {
        override def answer(invocation: InvocationOnMock): Option[Object] = {
          val transformation: PersistedMassTransitStop => Object = invocation.getArguments()(2).asInstanceOf[PersistedMassTransitStop => Object]
          val stop = PersistedMassTransitStop(id, id, id, Nil, 235, 0.0, 0.0, 0.0, None, None, None, false, Modification(None, None), Modification(None, None), Nil)
          Some(transformation(stop))
        }
      })
    }

    val roadLinkService = MockitoSugar.mock[RoadLinkService]
    stops.foreach { case(id, administrativeClass) =>
      when(roadLinkService.fetchVVHRoadlink(Matchers.eq(id))).thenReturn(Some(VVHRoadlink(id, 235, Nil, administrativeClass, TrafficDirection.BothDirections, FeatureClass.AllOthers)))
    }

    (mockMassTransitStopService, roadLinkService)
  }

  test("ignore updates on other road types than streets when import is limited to streets") {
    val (mockMassTransitStopService, mockRoadLinkService) = mockWithMassTransitStops(Seq((1l, State)))
    val importer = importerWithService(mockMassTransitStopService, mockRoadLinkService)
    val assetFields = Map("Valtakunnallinen ID" -> 1, "Pysäkin nimi" -> "NewName")

    val csv = createCSV(assetFields)
    val inputStream = new ByteArrayInputStream(csv.getBytes)
    val result = importer.importAssets(inputStream, assetProvider, roadTypeLimitations = Set(Municipality))

    result should equal(ImportResult(
      excludedAssets = List(ExcludedAsset(affectedRoadLinkType = "State", csvRow = importer.rowToString(defaultValues ++ assetFields)))))

    verify(mockMassTransitStopService, never).updateExistingById(anyLong(), anyObject(), anyObject(), anyString(), anyObject())
  }

  test("update asset on street when import is limited to streets") {
    val (mockMassTransitStopService, mockRoadLinkService) = mockWithMassTransitStops(Seq((1l, Municipality)))
    val importer = importerWithService(mockMassTransitStopService, mockRoadLinkService)

    val csv = createCSV(Map("Valtakunnallinen ID" -> 1, "Pysäkin nimi" -> "NewName"))
    val inputStream = new ByteArrayInputStream(csv.getBytes)
    val result = importer.importAssets(inputStream, assetProvider, roadTypeLimitations = Set(Municipality))
    result should equal(ImportResult())

    val properties = Set(SimpleProperty("nimi_suomeksi", Seq(PropertyValue("NewName"))))
    verify(mockMassTransitStopService).updateExistingById(Matchers.eq(1l), Matchers.eq(None), Matchers.eq(properties), Matchers.eq("CsvImportApiSpec"), anyObject())
  }

  test("update asset on roads and streets when import is limited to roads and streets") {
    val (mockMassTransitStopService, mockRoadLinkService) = mockWithMassTransitStops(Seq((1l, Municipality), (2l, State)))
    val importer = importerWithService(mockMassTransitStopService, mockRoadLinkService)

    val csv = createCSV(Map("Valtakunnallinen ID" -> 1, "Pysäkin nimi" -> "NewName1"), Map("Valtakunnallinen ID" -> 2, "Pysäkin nimi" -> "NewName2"))
    val inputStream = new ByteArrayInputStream(csv.getBytes)
    val result = importer.importAssets(inputStream, assetProvider, roadTypeLimitations = Set(State, Municipality))
    result should equal(ImportResult())

    val properties1 = Set(SimpleProperty("nimi_suomeksi", Seq(PropertyValue("NewName1"))))
    val properties2 = Set(SimpleProperty("nimi_suomeksi", Seq(PropertyValue("NewName2"))))
    verify(mockMassTransitStopService).updateExistingById(Matchers.eq(1l), Matchers.eq(None), Matchers.eq(properties1), Matchers.eq("CsvImportApiSpec"), anyObject())
    verify(mockMassTransitStopService).updateExistingById(Matchers.eq(2l), Matchers.eq(None), Matchers.eq(properties2), Matchers.eq("CsvImportApiSpec"), anyObject())
  }

  test("ignore updates on all other road types than private roads when import is limited to private roads") {
    val (mockMassTransitStopService, mockRoadLinkService) = mockWithMassTransitStops(Seq((1l, Municipality), (2l, State)))
    val importer = importerWithService(mockMassTransitStopService, mockRoadLinkService)

    val assetOnStreetFields = Map("Valtakunnallinen ID" -> 1, "Pysäkin nimi" -> "NewName1")
    val assetOnRoadFields = Map("Valtakunnallinen ID" -> 2, "Pysäkin nimi" -> "NewName2")
    val csv = createCSV(assetOnStreetFields, assetOnRoadFields)

    val inputStream = new ByteArrayInputStream(csv.getBytes)
    val result = importer.importAssets(inputStream, assetProvider, roadTypeLimitations = Set(Private))
    result should equal(ImportResult(
      excludedAssets = List(ExcludedAsset(affectedRoadLinkType = "State", csvRow = csvImporter.rowToString(defaultValues ++ assetOnRoadFields)),
        ExcludedAsset(affectedRoadLinkType = "Municipality", csvRow = csvImporter.rowToString(defaultValues ++ assetOnStreetFields)))))

    verify(mockMassTransitStopService, never).updateExistingById(anyLong(), anyObject(), anyObject(), anyString(), anyObject())
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
