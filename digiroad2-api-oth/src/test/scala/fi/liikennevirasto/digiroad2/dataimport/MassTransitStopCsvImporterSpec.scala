package fi.liikennevirasto.digiroad2.dataimport

import java.io.{ByteArrayInputStream, InputStream, InputStreamReader}

import com.github.tototoshi.csv.{CSVReader, DefaultCSVFormat}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.client.vvh._
import fi.liikennevirasto.digiroad2.dao.{MassTransitStopDao, MunicipalityDao}
import fi.liikennevirasto.digiroad2.user.{Configuration, User}
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfter, Tag}
import slick.driver.JdbcDriver.backend.Database
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import fi.liikennevirasto.digiroad2.Digiroad2Context.userProvider
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.client.tierekisteri.TierekisteriMassTransitStopClient
import fi.liikennevirasto.digiroad2.csvDataImporter.{Creator, MassTransitStopCsvImporter, MassTransitStopCsvOperation, PositionUpdater, Updater}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop.{MassTransitStopService, MassTransitStopWithProperties, PersistedMassTransitStop}
import fi.liikennevirasto.digiroad2.util.{GeometryTransform, RoadAddress, RoadSide, Track}
import org.mockito.ArgumentMatchers
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer

class MassTransitStopCsvImporterSpec extends AuthenticatedApiSpec with BeforeAndAfter {
  val MunicipalityKauniainen = 235
  private val testUserProvider = userProvider
  private val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  private val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]

  private val mockService = MockitoSugar.mock[MassTransitStopService]
  val vvHRoadlink = Seq(VVHRoadlink(1611400, 235, Seq(Point(2, 2), Point(4, 4)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers))
  val roadLink = Seq(RoadLink(1, Seq(Point(2, 2), Point(4, 4)), 3.5, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None))

  when(mockRoadLinkService.getClosestRoadlinkForCarTrafficFromVVH(any[User], any[Point])).thenReturn(vvHRoadlink)
  when(mockRoadLinkService.enrichRoadLinksFromVVH(any[Seq[VVHRoadlink]], any[Seq[ChangeInfo]])).thenReturn(roadLink)

  def runWithRollback(test: => Unit): Unit = sTestTransactions.runWithRollback()(test)

  trait MassTransitStopImporterTest extends MassTransitStopCsvImporter {
    val defaultKeys : List[String]

    def createCSV(assets: Map[String, Any]*): String = {
      val headers = defaultKeys.mkString(";") + "\n"
      val rows = assets.map { asset =>
        defaultKeys.map { key => asset.getOrElse(key, "") }.mkString(";")
      }.mkString("\n")
      headers + rows
    }

    def csvRead(assetFields: Map[String, Any]) : List[Map[String, String]]   = {
      val csv = csvToInputStream(createCSV(assetFields))

      val streamReader = new InputStreamReader(csv, "windows-1252")
      val csvReader = CSVReader.open(streamReader)(new DefaultCSVFormat {
        override val delimiter: Char = ';'
      })
      csvReader.allWithHeaders()
    }
  }

  object massTransitStopImporterCreate extends MassTransitStopImporterTest {
    override lazy val vvhClient: VVHClient = MockitoSugar.mock[VVHClient]
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def eventBus: DigiroadEventBus = mockEventBus
    override def withDynTransaction[T](f: => T): T = f

    override val defaultKeys = mappings.keys.toList ::: coordinateMappings.keys.toList

    def defaultValues(assets: Map[String, Any]) : Map[String, Any]  = {
      defaultKeys.map { key => key -> assets.getOrElse(key, "") }.toMap
    }
  }

  object massTransitStopImporterUpdate extends MassTransitStopImporterTest {
    override lazy val vvhClient: VVHClient = MockitoSugar.mock[VVHClient]
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def eventBus: DigiroadEventBus = mockEventBus
    override def withDynTransaction[T](f: => T): T = f
    override val defaultKeys = mappings.keys.toList ::: externalIdMapping.keys.toList

    def defaultValues(assets: Map[String, Any]) : Map[String, Any]  = {
      defaultKeys.map { key => key -> assets.getOrElse(key, "") }.toMap
    }
  }

  object massTransitStopImporterPosition extends MassTransitStopImporterTest {
    override lazy val vvhClient: VVHClient = MockitoSugar.mock[VVHClient]
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def eventBus: DigiroadEventBus = mockEventBus
    override def withDynTransaction[T](f: => T): T = f
    override val defaultKeys = mappings.keys.toList ::: coordinateMappings.keys.toList ::: externalIdMapping.keys.toList

    def defaultValues(assets: Map[String, Any]) : Map[String, Any]  = {
      defaultKeys.map { key => key -> assets.getOrElse(key, "") }.toMap
    }
  }

  private val testUser: User = User(id = 1, username = "CsvDataImportApiSpec", configuration = Configuration(authorizedMunicipalities = Set(MunicipalityKauniainen)))

  before {
    testUserProvider.setCurrentUser(testUser)
  }

  val mockGeometryTransform = MockitoSugar.mock[GeometryTransform]
  val mockTierekisteriClient = MockitoSugar.mock[TierekisteriMassTransitStopClient]

  class TestMassTransitStopService(val eventbus: DigiroadEventBus, val roadLinkService: RoadLinkService) extends MassTransitStopService {
    override def withDynSession[T](f: => T): T = f
    override def withDynTransaction[T](f: => T): T = f
    override val tierekisteriClient: TierekisteriMassTransitStopClient = mockTierekisteriClient
    override val massTransitStopDao: MassTransitStopDao = MockitoSugar.mock[MassTransitStopDao]
    override val municipalityDao: MunicipalityDao = MockitoSugar.mock[MunicipalityDao]
    override val geometryTransform: GeometryTransform = mockGeometryTransform
  }

  class TestMassTransitStopCsvOperation(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus, massTransitStopServiceImpl: MassTransitStopService) extends
    MassTransitStopCsvOperation(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) {
    override lazy val propertyUpdater = new Updater(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) {
      override def withDynTransaction[T](f: => T): T = f
      override def withDynSession[T](f: => T): T = f
      override def roadLinkService: RoadLinkService = roadLinkServiceImpl
      override def vvhClient: VVHClient = roadLinkServiceImpl.vvhClient
      override def eventBus: DigiroadEventBus = eventBusImpl

      override val massTransitStopService: MassTransitStopService = massTransitStopServiceImpl
    }
    override lazy val positionUpdater = new PositionUpdater(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) {
      override def withDynTransaction[T](f: => T): T = f
      override def withDynSession[T](f: => T): T = f
      override def roadLinkService: RoadLinkService = roadLinkServiceImpl
      override def vvhClient: VVHClient = roadLinkServiceImpl.vvhClient
      override def eventBus: DigiroadEventBus = eventBusImpl

      override val massTransitStopService: MassTransitStopService = massTransitStopServiceImpl
    }
    override lazy val creator = new Creator(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) {
      override def withDynTransaction[T](f: => T): T = f
      override def withDynSession[T](f: => T): T = f
      override def roadLinkService: RoadLinkService = roadLinkServiceImpl
      override def vvhClient: VVHClient = roadLinkServiceImpl.vvhClient
      override def eventBus: DigiroadEventBus = eventBusImpl

      override val massTransitStopService: MassTransitStopService = massTransitStopServiceImpl
    }
  }

  val massTransitStopCsvOperation = new TestMassTransitStopCsvOperation(mockRoadLinkService, mockEventBus, mockService)

  test("check mass TransitStop process") {
    var process : String = ""
    class TestMassTransitStopCsvOperation(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends MassTransitStopCsvOperation(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) {
      override lazy val propertyUpdater = new Updater(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) {
        override def importAssets(csvReader: List[Map[String, String]], fileName: String, user: User, logId: Long, roadTypeLimitations: Set[AdministrativeClass]): Unit = {
          process = "Updater"
        }
      }
      override lazy val positionUpdater = new PositionUpdater(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) {
        override def importAssets(csvReader: List[Map[String, String]], fileName: String, user: User, logId: Long, roadTypeLimitations: Set[AdministrativeClass]): Unit = {
          process = "PositionUpdater"
        }
      }
      override lazy val creator = new Creator(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) {
        override def importAssets(csvReader: List[Map[String, String]], fileName: String, user: User, logId: Long, roadTypeLimitations: Set[AdministrativeClass]): Unit = {
          process = "Creator"
        }
      }
    }

    val massTransitStopCsvOperation = new TestMassTransitStopCsvOperation(mockRoadLinkService, mockEventBus)
    val updaterCsv = massTransitStopImporterUpdate.createCSV(Map("Valtakunnallinen ID" -> 1))

    val updateInput = new ByteArrayInputStream(updaterCsv.getBytes)
    massTransitStopCsvOperation.importAssets(updateInput, "", testUser, 1, Set())

    process should be("Updater")

    val creatorCsv = massTransitStopImporterCreate.createCSV(Map("Koordinaatti X" -> 10000, "Koordinaatti Y" -> 1000))
    val createInput = new ByteArrayInputStream(creatorCsv.getBytes)
    massTransitStopCsvOperation.importAssets(createInput, "", testUser, 1, Set())

    process should be("Creator")

    val positionCsv = massTransitStopImporterPosition.createCSV(Map("Valtakunnallinen ID" -> 1, "Koordinaatti X" -> 1000, "Koordinaatti Y" -> 1000))
    val positionInput = new ByteArrayInputStream(positionCsv.getBytes)
    massTransitStopCsvOperation.importAssets(positionInput, "", testUser, 1, Set())

    process should be("PositionUpdater")
  }

  test("create busStop fails if type is undefined") {
    val assetFields = Map("Koordinaatti X" -> 10000, "Koordinaatti Y" -> 1000, "Pysäkin tyyppi" -> ",", "Tietojen ylläpitäjä" -> "1")
    val invalidCsv = massTransitStopImporterCreate.csvRead(assetFields)

    val result = massTransitStopCsvOperation.creator.processing(invalidCsv, testUser, Set())
    result.malformedRows.size should be (1)
    result.malformedRows.head.malformedParameters.size should be (1)
    result.malformedRows.head.malformedParameters.head should be ("Pysäkin tyyppi")
    result.incompleteRows.isEmpty should be (true)
    result.excludedRows.isEmpty should be (true)
    result.notImportedData.isEmpty should be (true)
  }

  test("validation fails when asset type is unknown") {
    val assetFields = Map("Valtakunnallinen ID" -> 1, "Pysäkin tyyppi" -> "2,10")
    val invalidCsv = massTransitStopImporterUpdate.csvRead(assetFields)

    val result = massTransitStopCsvOperation.propertyUpdater.processing(invalidCsv, testUser)
    result.malformedRows.size should be (1)
    result.malformedRows.head.malformedParameters.size should be (1)
    result.malformedRows.head.malformedParameters.head should be ("Pysäkin tyyppi")
    result.incompleteRows.isEmpty should be (true)
    result.excludedRows.isEmpty should be (true)
    result.notImportedData.isEmpty should be (true)
  }

  test("update asset admin id by CSV import") {
    when(mockService.getMassTransitStopByNationalId(ArgumentMatchers.eq(1l), anyObject(), anyBoolean())).thenReturn(Some(MassTransitStopWithProperties(1, 1, Nil, 0.0, 0.0, None, None, None, false, Nil))).thenReturn(Some(MassTransitStopWithProperties(1, 1, Nil, 0.0, 0.0, None, None, None, false, Nil)))
    val csv = massTransitStopImporterUpdate.csvRead(Map("Valtakunnallinen ID" -> 1, "Ylläpitäjän tunnus" -> "NewAdminId"))
    massTransitStopCsvOperation.propertyUpdater.processing(csv, testUser, Set())

    val properties = Set(SimpleProperty("yllapitajan_tunnus", Seq(PropertyValue("NewAdminId"))))
    verify(mockService).updateExistingById(ArgumentMatchers.eq(1l), ArgumentMatchers.eq(None), ArgumentMatchers.eq(properties), ArgumentMatchers.eq("CsvDataImportApiSpec"), anyObject(), anyBoolean())
  }

  test("Should not update asset LiVi id by CSV import (after Tierekisteri integration)") {
    when(mockService.getMassTransitStopByNationalId(ArgumentMatchers.eq(1l), anyObject(), anyBoolean())).thenReturn(Some(MassTransitStopWithProperties(1, 1, Nil, 0.0, 0.0, None, None, None, false, Nil)))
    val csv = massTransitStopImporterUpdate.csvRead(Map("Valtakunnallinen ID" -> 1, "LiVi-tunnus" -> "Livi987654"))

    massTransitStopCsvOperation.propertyUpdater.processing(csv, testUser, Set()) should equal(massTransitStopCsvOperation.propertyUpdater.ImportResultPointAsset())
    val properties = Set(SimpleProperty("yllapitajan_koodi", Seq(PropertyValue("Livi987654")))).filterNot(_.publicId == "yllapitajan_koodi")
    verify(mockService).updateExistingById(ArgumentMatchers.eq(1l), ArgumentMatchers.eq(None), ArgumentMatchers.eq(properties), ArgumentMatchers.eq("CsvDataImportApiSpec"), anyObject(), anyBoolean())
  }

  test("update asset stop code by CSV import") {
    when(mockService.getMassTransitStopByNationalId(ArgumentMatchers.eq(1l), anyObject(), anyBoolean())).thenReturn(Some(MassTransitStopWithProperties(1, 1, Nil, 0.0, 0.0, None, None, None, false, Nil)))
    val csv = massTransitStopImporterUpdate.csvRead(Map("Valtakunnallinen ID" -> 1, "Matkustajatunnus" -> "H156"))

    massTransitStopCsvOperation.propertyUpdater.processing(csv, testUser) should equal(massTransitStopCsvOperation.propertyUpdater.ImportResultPointAsset())
    val properties = Set(SimpleProperty("matkustajatunnus", Seq(PropertyValue("H156"))))
    verify(mockService).updateExistingById(ArgumentMatchers.eq(1l), ArgumentMatchers.eq(None), ArgumentMatchers.eq(properties), ArgumentMatchers.eq("CsvDataImportApiSpec"), anyObject(), anyBoolean())
  }

  test("update additional information by CSV import", Tag("db")) {
    when(mockService.getMassTransitStopByNationalId(ArgumentMatchers.eq(1l), anyObject(), anyBoolean())).thenReturn(Some(MassTransitStopWithProperties(1, 1, Nil, 0.0, 0.0, None, None, None, false, Nil)))

    val csv = massTransitStopImporterUpdate.csvRead(Map("Valtakunnallinen ID" -> 1, "Lisätiedot" -> "Updated additional info"))

    massTransitStopCsvOperation.propertyUpdater.processing(csv, testUser) should equal(massTransitStopCsvOperation.propertyUpdater.ImportResultPointAsset())
    val properties = Set(SimpleProperty("lisatiedot", Seq(PropertyValue("Updated additional info"))))
    verify(mockService).updateExistingById(ArgumentMatchers.eq(1l), ArgumentMatchers.eq(None), ArgumentMatchers.eq(properties), ArgumentMatchers.eq("CsvDataImportApiSpec"), anyObject(), anyBoolean())
  }

  val exampleValues = Map(
    "Nimi_suomeksi" -> ("Passila", "Pasila"),
    "Yllapitajan_tunnus" -> ("1234", "1281"),
    "Yllapitajan_koodi" -> ("LiVV", "LiVi123"),
    "Matkustajatunnus" -> ("sdjkal", "9877"),
    "Pysakin_tyyppi" -> ("2", "1"),
    "Nimi_ruotsiksi" -> ("Bölle", "Böle"),
    "Liikennointisuunta" -> ("Itään", "Pohjoiseen"),
    "Katos" -> ("1", "2"),
    "Aikataulu" -> ("1", "2"),
    "Mainoskatos" -> ("1", "2"),
    "Penkki" -> ("1", "2"),
    "Pyorateline" -> ("1", "2"),
    "Sahkoinen_aikataulunaytto" -> ("1", "2"),
    "Valaistus" -> ("1", "2"),
    "Saattomahdollisuus_henkiloautolla" -> ("1", "2"),
    "Lisatiedot" -> ("qwer", "asdf"),
    "Tietojen_yllapitaja" -> ("1", "3"),
    "Korotettu" -> ("1", "2"),
    "Roska_astia" -> ("1", "2"),
    "Vyohyketieto" -> ("InfoZone", "InfoZone ready")
  )

  when(mockGeometryTransform.resolveAddressAndLocation(any[Point], any[Int], any[Double], any[Long], any[Int], any[Option[Int]], any[Option[Int]])).thenReturn((RoadAddress(None, 0, 0, Track.Unknown, 0, None) , RoadSide.Right))

  when(mockService.getByNationalId(ArgumentMatchers.eq(1), anyObject(), anyObject(), anyBoolean())).thenAnswer(new Answer[Option[Object]] {
    override def answer(invocation: InvocationOnMock): Option[Object] = {
      val transformation: PersistedMassTransitStop => (Object, Object) = invocation.getArguments()(2).asInstanceOf[PersistedMassTransitStop => (Object, Object)]
      val stop = PersistedMassTransitStop(1, 1, 1, Nil, 235, 0.0, 0.0, 0.0, None, None, None, false, 0, Modification(None, None), Modification(None, None), Nil, NormalLinkInterface)
      Some(transformation(stop)._1)
    }
  })

  test("update asset's properties in a generic manner", Tag("db")) {
    when(mockService.getMassTransitStopByNationalId(ArgumentMatchers.eq(1l), anyObject(), anyBoolean())).thenReturn(Some(MassTransitStopWithProperties(1, 1, Nil, 0.0, 0.0, None, None, None, false, Nil)))

    val csv =  massTransitStopImporterUpdate.csvRead(Map("Valtakunnallinen ID" -> 1) ++ exampleValues)

    massTransitStopCsvOperation.propertyUpdater.processing(csv, testUser) should equal(massTransitStopCsvOperation.propertyUpdater.ImportResultPointAsset())
    val properties: Set[SimpleProperty] = exampleValues.map { case (key, value) =>
      SimpleProperty(key, Seq(PropertyValue(value._2)))
    }.filterNot(_.publicId == "yllapitajan_koodi").toSet

    verify(mockService).updateExistingById(ArgumentMatchers.eq(1l), ArgumentMatchers.eq(None), ArgumentMatchers.eq(properties), ArgumentMatchers.eq("CsvDataImportApiSpec"), anyObject(), anyBoolean())
  }

  test("raise an error when updating non-existent asset", Tag("db")) {
    when(mockService.getMassTransitStopByNationalId(ArgumentMatchers.eq(6l), anyObject(), anyBoolean())).thenReturn(None)
    val assetFields = Map("Valtakunnallinen ID" -> "6", "Pysäkin nimi" -> "AssetName")
    val csv =  massTransitStopImporterUpdate.csvRead(assetFields)

    val result = massTransitStopCsvOperation.propertyUpdater.processing(csv, testUser)

    result should equal(massTransitStopCsvOperation.propertyUpdater.ImportResultPointAsset(
      notImportedData = List(massTransitStopCsvOperation.propertyUpdater.NotImportedData(reason = "Asset not found -> 6",
        csvRow = massTransitStopCsvOperation.propertyUpdater.rowToString(massTransitStopImporterUpdate.defaultValues(assetFields))))))
  }

  test("raise an error when csv row does not define required parameter", Tag("db")) {
    when(mockService.getMassTransitStopByNationalId(ArgumentMatchers.eq(1l), anyObject(), anyBoolean())).thenReturn(Some(MassTransitStopWithProperties(1, 1, Nil, 0.0, 0.0, None, None, None, false, Nil)))
    val assetFields = massTransitStopImporterUpdate.defaultValues(Map()).filterNot(Set("Pysäkin nimi"))
    val csv =  massTransitStopImporterUpdate.csvRead(assetFields)

    val result = massTransitStopCsvOperation.propertyUpdater.processing(csv, testUser, Set())
    result should equal(massTransitStopCsvOperation.propertyUpdater.ImportResultPointAsset(
      incompleteRows = List(IncompleteRow(missingParameters = List("Pysäkin nimi"),
        csvRow = massTransitStopCsvOperation.propertyUpdater.rowToString(massTransitStopImporterUpdate.defaultValues(Map()) - "Pysäkin nimi" ++ Map("Valtakunnallinen ID" -> 1))))))

    verify(mockService, never).updateExistingById(anyLong(), anyObject(), anyObject(), anyString(), anyObject(), anyBoolean())
  }

  test("ignore updates on other road types than streets when import is limited to streets") {
    val assetFields = Map("Valtakunnallinen ID" -> 1, "Pysäkin nimi" -> "NewName")
    val csv =  massTransitStopImporterUpdate.csvRead(assetFields)

    val result = massTransitStopCsvOperation.propertyUpdater.processing(csv, testUser,  roadTypeLimitations = Set(Municipality))
    result should equal(massTransitStopCsvOperation.propertyUpdater.ImportResultPointAsset(
      excludedRows = List(ExcludedRow(affectedRows = "State", csvRow = massTransitStopCsvOperation.propertyUpdater.rowToString(massTransitStopImporterUpdate.defaultValues(assetFields))))))

    verify(mockService, never).updateExistingById(anyLong(), anyObject(), anyObject(), anyString(), anyObject(), anyBoolean())
  }

  test("update asset on street when import is limited to streets") {
    val csv = massTransitStopImporterUpdate.csvRead(Map("Valtakunnallinen ID" -> 1, "Pysäkin nimi" -> "NewName"))

    val result = massTransitStopCsvOperation.propertyUpdater.processing(csv, testUser, roadTypeLimitations = Set(Municipality))
    result should equal(massTransitStopCsvOperation.propertyUpdater.ImportResultPointAsset())
    val properties = Set(SimpleProperty("nimi_suomeksi", Seq(PropertyValue("NewName"))))
    verify(mockService).updateExistingById(ArgumentMatchers.eq(1l), ArgumentMatchers.eq(None), ArgumentMatchers.eq(properties), ArgumentMatchers.eq("CsvDataImportApiSpec"), anyObject(), anyBoolean())
  }

  test("update asset on roads and streets when import is limited to roads and streets") {
    val csv = csvToInputStream(massTransitStopImporterUpdate.createCSV(Map("Valtakunnallinen ID" -> 1, "Pysäkin nimi" -> "NewName1"), Map("Valtakunnallinen ID" -> 2, "Pysäkin nimi" -> "NewName2")))
    val streamReader = new InputStreamReader(csv, "windows-1252")
    val csvReader = CSVReader.open(streamReader)(new DefaultCSVFormat {
      override val delimiter: Char = ';'
    })
    val inputStream =  csvReader.allWithHeaders()

    val result = massTransitStopCsvOperation.propertyUpdater.processing(inputStream, testUser, roadTypeLimitations = Set(State, Municipality))
    result should equal(massTransitStopCsvOperation.propertyUpdater.ImportResultPointAsset())

    val properties1 = Set(SimpleProperty("nimi_suomeksi", Seq(PropertyValue("NewName1"))))
    val properties2 = Set(SimpleProperty("nimi_suomeksi", Seq(PropertyValue("NewName2"))))
    verify(mockService).updateExistingById(ArgumentMatchers.eq(1l), ArgumentMatchers.eq(None), ArgumentMatchers.eq(properties1), ArgumentMatchers.eq("CsvDataImportApiSpec"), anyObject(), anyBoolean())
    verify(mockService).updateExistingById(ArgumentMatchers.eq(2l), ArgumentMatchers.eq(None), ArgumentMatchers.eq(properties2), ArgumentMatchers.eq("CsvDataImportApiSpec"), anyObject(), anyBoolean())
  }

  test("ignore updates on all other road types than private roads when import is limited to private roads") {
    val assetOnStreetFields = Map("Valtakunnallinen ID" -> 1, "Pysäkin nimi" -> "NewName1")
    val assetOnRoadFields = Map("Valtakunnallinen ID" -> 2, "Pysäkin nimi" -> "NewName2")
    val csv = csvToInputStream(massTransitStopImporterUpdate.createCSV(assetOnStreetFields, assetOnRoadFields))
    val streamReader = new InputStreamReader(csv, "windows-1252")
    val csvReader = CSVReader.open(streamReader)(new DefaultCSVFormat {
      override val delimiter: Char = ';'
    })
    val inputStream =  csvReader.allWithHeaders()

    val result = massTransitStopCsvOperation.propertyUpdater.processing(inputStream, testUser, roadTypeLimitations = Set(Private))
    result should equal(massTransitStopCsvOperation.propertyUpdater.ImportResultPointAsset(
      excludedRows = List(ExcludedRow(affectedRows = "State", csvRow = massTransitStopCsvOperation.propertyUpdater.rowToString(massTransitStopImporterUpdate.defaultValues(assetOnRoadFields))),
        ExcludedRow(affectedRows = "Municipality", csvRow = massTransitStopCsvOperation.propertyUpdater.rowToString(massTransitStopImporterUpdate.defaultValues(assetOnStreetFields))))))

    verify(mockService, never).updateExistingById(anyLong(), anyObject(), anyObject(), anyString(), anyObject(), anyBoolean())
  }

  private def csvToInputStream(csv: String): InputStream = new ByteArrayInputStream(csv.getBytes())
}
