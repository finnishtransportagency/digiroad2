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

import java.io.{InputStream, InputStreamReader}

import com.github.tototoshi.csv.{CSVReader, DefaultCSVFormat}
import fi.liikennevirasto.digiroad2.{AssetProperty, DigiroadEventBus, ExcludedRow, FloatingReason, GeometryUtils, IncompleteRow, MalformedRow, Point, Status}
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, FloatingAsset, Position, PropertyValue, SimpleProperty, TrafficDirection, Unknown}
import fi.liikennevirasto.digiroad2.client.tierekisteri.TierekisteriMassTransitStopClient
import fi.liikennevirasto.digiroad2.client.vvh.{VVHClient, VVHRoadlink}
import fi.liikennevirasto.digiroad2.dao.{MassTransitStopDao, MunicipalityDao}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.{RoadAddressService, RoadLinkService}
import fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop.{MassTransitStopService, MassTransitStopWithProperties, NewMassTransitStop, PersistedMassTransitStop}
import fi.liikennevirasto.digiroad2.user.User
import fi.liikennevirasto.digiroad2.util.GeometryTransform
import org.apache.commons.lang3.StringUtils.isBlank

class MassTransitStopCsvImporterSpec extends AuthenticatedApiSpec with BeforeAndAfter {
  val MunicipalityKauniainen = 235
  val testUserProvider = userProvider
  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]

  val mockService = MockitoSugar.mock[MassTransitStopService]
  val mockVVHClient = MockitoSugar.mock[VVHClient]

  val massTransitStopCsvOperation = new TestMassTransitStopCsvOperation(mockVVHClient, mockRoadLinkService, mockEventBus, mockService)
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

    def csvRead(assetFields: Map[String, Any]*) : List[Map[String, String]]   = {

      val csv = csvToInputStream(createCSV(assetFields.flatten.toMap))
// shouldn't be necessary this read with windos-1252
      val streamReader = new InputStreamReader(csv, "UTF-8")
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

  class TestMassTransitStopCsvOperation(vvhClientImpl: VVHClient, roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus, massTransitStopServiceImpl: MassTransitStopService) extends
    MassTransitStopCsvOperation(vvhClientImpl: VVHClient, roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) {
    override lazy val propertyUpdater = new Updater(vvhClientImpl: VVHClient, roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) {
      override def withDynTransaction[T](f: => T): T = f
      override def withDynSession[T](f: => T): T = f
      override def roadLinkService: RoadLinkService = roadLinkServiceImpl
      override def vvhClient: VVHClient = vvhClientImpl
      override def eventBus: DigiroadEventBus = eventBusImpl

      override val massTransitStopService: MassTransitStopService = massTransitStopServiceImpl
    }
    override lazy val positionUpdater = new PositionUpdater(vvhClientImpl: VVHClient, roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) {
      override def withDynTransaction[T](f: => T): T = f
      override def withDynSession[T](f: => T): T = f
      override def roadLinkService: RoadLinkService = roadLinkServiceImpl
      override def vvhClient: VVHClient = vvhClientImpl
      override def eventBus: DigiroadEventBus = eventBusImpl

      override val massTransitStopService: MassTransitStopService = massTransitStopServiceImpl
    }
    override lazy val creator = new Creator(vvhClientImpl: VVHClient, roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) {
      override def withDynTransaction[T](f: => T): T = f
      override def withDynSession[T](f: => T): T = f
      override def roadLinkService: RoadLinkService = roadLinkServiceImpl
      override def vvhClient: VVHClient = vvhClientImpl
      override def eventBus: DigiroadEventBus = eventBusImpl

      override val massTransitStopService: MassTransitStopService = massTransitStopServiceImpl
    }
  }

  private def mockWithMassTransitStops(stops: Seq[(Long, AdministrativeClass)]): (MassTransitStopService, VVHClient) = {
    val mockVVHClient = MockitoSugar.mock[VVHClient]
    val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
    val mockTierekisteriClient = MockitoSugar.mock[TierekisteriMassTransitStopClient]

    when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
    stops.foreach { case(id, administrativeClass) =>
      when(mockVVHRoadLinkClient.fetchByLinkId(ArgumentMatchers.eq(id))).thenReturn(Some(VVHRoadlink(id, 235, Nil, administrativeClass, TrafficDirection.BothDirections, FeatureClass.AllOthers)))
    }

    val mockMassTransitStopDao = MockitoSugar.mock[MassTransitStopDao]
    val mockGeometryTransform = MockitoSugar.mock[GeometryTransform]
    when(mockMassTransitStopDao.getAssetAdministrationClass(any[Long])).thenReturn(None)

    class TestMassTransitStopService(val eventbus: DigiroadEventBus, val roadLinkService: RoadLinkService) extends MassTransitStopService {
      override def withDynSession[T](f: => T): T = f
      override def withDynTransaction[T](f: => T): T = f
      override val tierekisteriClient: TierekisteriMassTransitStopClient = mockTierekisteriClient
      override val massTransitStopDao: MassTransitStopDao = MockitoSugar.mock[MassTransitStopDao]
      override val municipalityDao: MunicipalityDao = MockitoSugar.mock[MunicipalityDao]
      override val geometryTransform: GeometryTransform = mockGeometryTransform
    }

    when(mockGeometryTransform.resolveAddressAndLocation(any[Point], any[Int], any[Double], any[Long], any[Int], any[Option[Int]], any[Option[Int]])).thenReturn((RoadAddress(None, 0, 0, Track.Unknown, 0, None) , RoadSide.Right))
    val mockMassTransitStopService = MockitoSugar.mock[MassTransitStopService]
    stops.foreach { case (id, administrativeClass) =>
      when(mockMassTransitStopService.getByNationalId(ArgumentMatchers.eq(id), anyObject(), anyObject(), anyBoolean())).thenAnswer(new Answer[Option[Object]] {
        override def answer(invocation: InvocationOnMock): Option[Object] = {
          val transformation: PersistedMassTransitStop => (Object, Object) = invocation.getArguments()(2).asInstanceOf[PersistedMassTransitStop => (Object, Object)]
          val stop = PersistedMassTransitStop(id, id, id, Nil, 235, 0.0, 0.0, 0.0, None, None, None, false, 0, Modification(None, None), Modification(None, None), Nil, NormalLinkInterface)
          Some(transformation(stop)._1)
        }
      })
    }

    when(mockMassTransitStopService.isFloating(any[PersistedPointAsset], any[Option[VVHRoadlink]])).thenAnswer(new Answer[Object] {
      override def answer(invocation: InvocationOnMock): Object = {
        val persistedPointAsset: PersistedPointAsset  = invocation.getArguments()(0).asInstanceOf[PersistedPointAsset]
        val vvhRoadlink: Option[VVHRoadlink]  = invocation.getArguments()(1).asInstanceOf[Option[VVHRoadlink]]

        val testMassTransitStopService = new TestMassTransitStopService(new DummyEventBus, MockitoSugar.mock[RoadLinkService])
        testMassTransitStopService.isFloating(persistedPointAsset, vvhRoadlink)
      }
    })

    (mockMassTransitStopService, mockVVHClient)
  }

  test("check mass TransitStop process") {
    var process : String = ""
    class TestMassTransitStopCsvOperation(vvhClientImpl: VVHClient, roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends
      MassTransitStopCsvOperation(vvhClientImpl: VVHClient, roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) {
      override lazy val propertyUpdater = new Updater(vvhClientImpl: VVHClient, roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) {
        override def importAssets(csvReader: List[Map[String, String]], fileName: String,  user: User, logId: Long, roadTypeLimitations: Set[AdministrativeClass]): Unit = {
          process = "Updater"
        }
      }
      override lazy val positionUpdater = new PositionUpdater(vvhClientImpl: VVHClient, roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) {
        override def importAssets(csvReader: List[Map[String, String]], fileName: String, user: User, logId: Long, roadTypeLimitations: Set[AdministrativeClass]): Unit = {
          process = "PositionUpdater"
        }
      }
      override lazy val creator = new Creator(vvhClientImpl: VVHClient, roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) {
        override def importAssets(csvReader: List[Map[String, String]], fileName: String, user: User, logId: Long, roadTypeLimitations: Set[AdministrativeClass]): Unit = {
          process = "Creator"
        }
      }
    }

    val massTransitStopCsvOperation = new TestMassTransitStopCsvOperation(mockVVHClient, mockRoadLinkService, mockEventBus)
    val updaterCsv = massTransitStopImporterUpdate.createCSV(Map("valtakunnallinen id" -> 1))

    val updateInput = new ByteArrayInputStream(updaterCsv)
    massTransitStopCsvOperation.importAssets(updateInput, "", testUser, 1, Set())

    process should be("Updater")

    val creatorCsv = massTransitStopImporterCreate.createCSV(Map("koordinaatti x" -> 10000, "koordinaatti y" -> 1000))
    val createInput = new ByteArrayInputStream(creatorCsv)
    massTransitStopCsvOperation.importAssets(createInput, "", testUser, 1, Set())

    process should be("Creator")

    val positionCsv = massTransitStopImporterPosition.createCSV(Map("valtakunnallinen id" -> 1, "koordinaatti x" -> 1000, "koordinaatti y" -> 1000))
    val positionInput = new ByteArrayInputStream(positionCsv)
    massTransitStopCsvOperation.importAssets(positionInput, "", testUser, 1, Set())

    process should be("PositionUpdater")
  }
//
//  test("create busStop fails if type is undefined") {
//    val assetFields = Map("koordinaatti x" -> 10000, "koordinaatti y" -> 1000, "pysäkin tyyppi" -> ",", "tietojen ylläpitäjä" -> "1")
//    val invalidCsv = massTransitStopImporterCreate.csvRead(assetFields)
//
//    val result = massTransitStopCsvOperation.creator.processing(invalidCsv, testUser, Set())
//    result.malformedRows.size should be (1)
//    result.malformedRows.head.malformedParameters.size should be (1)
//    result.malformedRows.head.malformedParameters.head should be ("pysäkin tyyppi")
//    result.incompleteRows.isEmpty should be (true)
//    result.excludedRows.isEmpty should be (true)
//    result.notImportedData.isEmpty should be (true)
//  }
//
//  test("validation fails when asset type is unknown") {
//    val massTransitStopCsvOperation = new TestMassTransitStopCsvOperation(mockVVHClient, mockRoadLinkService, mockEventBus, mockService)
//
//    val assetFields = Map("valtakunnallinen id" -> 1, "pysäkin tyyppi" -> "2,10")
//    val invalidCsv = massTransitStopImporterUpdate.csvRead(assetFields)
//
//    val result = massTransitStopCsvOperation.propertyUpdater.processing(invalidCsv, testUser)
//    result.malformedRows.size should be (1)
//    result.malformedRows.head.malformedParameters.size should be (1)
//    result.malformedRows.head.malformedParameters.head should be ("pysäkin tyyppi")
//    result.incompleteRows.isEmpty should be (true)
//    result.excludedRows.isEmpty should be (true)
//    result.notImportedData.isEmpty should be (true)
//  }
//
//  test("update asset admin id by CSV import") {
//    val massTransitStopCsvOperation = new TestMassTransitStopCsvOperation(mockVVHClient, mockRoadLinkService, mockEventBus, mockService)
//
//    when(mockService.getMassTransitStopByNationalId(ArgumentMatchers.eq(1l), anyObject(), anyBoolean())).thenReturn(Some(MassTransitStopWithProperties(1, 1, Nil, 0.0, 0.0, None, None, None, false, Nil))).thenReturn(Some(MassTransitStopWithProperties(1, 1, Nil, 0.0, 0.0, None, None, None, false, Nil)))
//    val csv = massTransitStopImporterUpdate.csvRead(Map("valtakunnallinen id" -> 1, "ylläpitäjän tunnus" -> "NewAdminId"))
//    massTransitStopCsvOperation.propertyUpdater.processing(csv, testUser, Set())
//
//    val properties = Set(SimpleProperty("yllapitajan_tunnus", Seq(PropertyValue("NewAdminId"))))
//    verify(mockService).updateExistingById(ArgumentMatchers.eq(1l), ArgumentMatchers.eq(None), ArgumentMatchers.eq(properties), ArgumentMatchers.eq("CsvDataImportApiSpec"), anyObject(), anyBoolean())
//  }
//
//  test("Should not update asset LiVi id by CSV import (after Tierekisteri integration)") {
//    val massTransitStopCsvOperation = new TestMassTransitStopCsvOperation(mockVVHClient, mockRoadLinkService, mockEventBus, mockService)
//
//    when(mockService.getMassTransitStopByNationalId(ArgumentMatchers.eq(1l), anyObject(), anyBoolean())).thenReturn(Some(MassTransitStopWithProperties(1, 1, Nil, 0.0, 0.0, None, None, None, false, Nil)))
//    val csv = massTransitStopImporterUpdate.csvRead(Map("valtakunnallinen id" -> 1, "liVi-tunnus" -> "Livi987654"))
//
//    massTransitStopCsvOperation.propertyUpdater.processing(csv, testUser, Set()) should equal(massTransitStopCsvOperation.propertyUpdater.ImportResultPointAsset())
//    val properties = Set(SimpleProperty("yllapitajan_koodi", Seq(PropertyValue("Livi987654")))).filterNot(_.publicId == "yllapitajan_koodi")
//    verify(mockService).updateExistingById(ArgumentMatchers.eq(1l), ArgumentMatchers.eq(None), ArgumentMatchers.eq(properties), ArgumentMatchers.eq("CsvDataImportApiSpec"), anyObject(), anyBoolean())
//  }
//
//  test("update asset stop code by CSV import") {
//    val massTransitStopCsvOperation = new TestMassTransitStopCsvOperation(mockVVHClient, mockRoadLinkService, mockEventBus, mockService)
//
//    when(mockService.getMassTransitStopByNationalId(ArgumentMatchers.eq(1l), anyObject(), anyBoolean())).thenReturn(Some(MassTransitStopWithProperties(1, 1, Nil, 0.0, 0.0, None, None, None, false, Nil)))
//    val csv = massTransitStopImporterUpdate.csvRead(Map("valtakunnallinen id" -> 1, "matkustajatunnus" -> "H156"))
//
//    massTransitStopCsvOperation.propertyUpdater.processing(csv, testUser) should equal(massTransitStopCsvOperation.propertyUpdater.ImportResultPointAsset())
//    val properties = Set(SimpleProperty("matkustajatunnus", Seq(PropertyValue("H156"))))
//    verify(mockService).updateExistingById(ArgumentMatchers.eq(1l), ArgumentMatchers.eq(None), ArgumentMatchers.eq(properties), ArgumentMatchers.eq("CsvDataImportApiSpec"), anyObject(), anyBoolean())
//  }
//
//  test("update additional information by CSV import", Tag("db")) {
//    val massTransitStopCsvOperation = new TestMassTransitStopCsvOperation(mockVVHClient, mockRoadLinkService, mockEventBus, mockService)
//
//    when(mockService.getMassTransitStopByNationalId(ArgumentMatchers.eq(1l), anyObject(), anyBoolean())).thenReturn(Some(MassTransitStopWithProperties(1, 1, Nil, 0.0, 0.0, None, None, None, false, Nil)))
//
//    val csv = massTransitStopImporterUpdate.csvRead(Map("valtakunnallinen id" -> 1, "lisätiedot" -> "Updated additional info"))
//
//    massTransitStopCsvOperation.propertyUpdater.processing(csv, testUser) should equal(massTransitStopCsvOperation.propertyUpdater.ImportResultPointAsset())
//    val properties = Set(SimpleProperty("lisatiedot", Seq(PropertyValue("Updated additional info"))))
//    verify(mockService).updateExistingById(ArgumentMatchers.eq(1l), ArgumentMatchers.eq(None), ArgumentMatchers.eq(properties), ArgumentMatchers.eq("CsvDataImportApiSpec"), anyObject(), anyBoolean())
//  }
//
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
    "tietojen_yllapitaja" -> ("1", "3"),
    "korotettu" -> ("1", "2"),
    "roska_astia" -> ("1", "2"),
    "vyohyketieto" -> ("InfoZone", "InfoZone ready")
  )

  when(mockGeometryTransform.resolveAddressAndLocation(any[Point], any[Int], any[Double], any[Long], any[Int], any[Option[Int]], any[Option[Int]])).thenReturn((RoadAddress(None, 0, 0, Track.Unknown, 0, None) , RoadSide.Right))

  when(mockService.getByNationalId(ArgumentMatchers.eq(1), anyObject(), anyObject(), anyBoolean())).thenAnswer(new Answer[Option[Object]] {
    override def answer(invocation: InvocationOnMock): Option[Object] = {
      val transformation: PersistedMassTransitStop => (Object, Object) = invocation.getArguments()(2).asInstanceOf[PersistedMassTransitStop => (Object, Object)]
      val stop = PersistedMassTransitStop(1, 1, 1, Nil, 235, 0.0, 0.0, 0.0, None, None, None, false, 0, Modification(None, None), Modification(None, None), Nil, NormalLinkInterface)
      Some(transformation(stop)._1)
    }
  })

//  test("update asset's properties in a generic manner", Tag("db")) {
//    val massTransitStopCsvOperation = new TestMassTransitStopCsvOperation(mockVVHClient, mockRoadLinkService, mockEventBus, mockService)
//
//    when(mockService.getMassTransitStopByNationalId(ArgumentMatchers.eq(1l), anyObject(), anyBoolean())).thenReturn(Some(MassTransitStopWithProperties(1, 1, Nil, 0.0, 0.0, None, None, None, false, Nil)))
//
//    val csv =  massTransitStopImporterUpdate.csvRead(Map("valtakunnallinen id" -> 1) ++ exampleValues)
//
//    massTransitStopCsvOperation.propertyUpdater.processing(csv, testUser, Set()) should equal(massTransitStopCsvOperation.propertyUpdater.ImportResultPointAsset())
//    val properties: Set[SimpleProperty] = exampleValues.map { case (key, value) =>
//      SimpleProperty(key, Seq(PropertyValue(value._2)))
//    }.filterNot(_.publicId == "yllapitajan_koodi").toSet
//
//    verify(mockService).updateExistingById(ArgumentMatchers.eq(1l), ArgumentMatchers.eq(None), ArgumentMatchers.eq(properties), ArgumentMatchers.eq("CsvDataImportApiSpec"), anyObject(), anyBoolean())
//  }

//  test("raise an error when updating non-existent asset", Tag("db")) {
//    val massTransitStopCsvOperation = new TestMassTransitStopCsvOperation(mockVVHClient, mockRoadLinkService, mockEventBus, mockService)
//
//    when(mockService.getMassTransitStopByNationalId(ArgumentMatchers.eq(6l), anyObject(), anyBoolean())).thenReturn(None)
//    val assetFields = Map("valtakunnallinen id" -> "6", "pysäkin nimi" -> "AssetName")
//    val csv =  massTransitStopImporterUpdate.csvRead(assetFields)
//
//    val result = massTransitStopCsvOperation.propertyUpdater.processing(csv, testUser, Set())
//
//    result should equal(massTransitStopCsvOperation.propertyUpdater.ImportResultPointAsset(
//      notImportedData = List(massTransitStopCsvOperation.propertyUpdater.NotImportedData(reason = "Asset not found -> 6",
//        csvRow = massTransitStopCsvOperation.propertyUpdater.rowToString(massTransitStopImporterUpdate.defaultValues(assetFields))))))
//  }

  test("raise an error when csv row does not define required parameter", Tag("db")) {
    val massTransitStopCsvOperation = new TestMassTransitStopCsvOperation(mockVVHClient, mockRoadLinkService, mockEventBus, mockService)

    when(mockService.getMassTransitStopByNationalId(ArgumentMatchers.eq(1l), anyObject(), anyBoolean())).thenReturn(Some(MassTransitStopWithProperties(1, 1, Nil, 0.0, 0.0, None, None, None, false, Nil)))
    val assetFields = massTransitStopImporterUpdate.defaultValues(Map("valtakunnallinen id" -> 1))
    val csv = massTransitStopImporterUpdate.csvRead(assetFields).map(fields => fields.filterNot(field => field._1 == "pysäkin nimi"))

    val result = massTransitStopCsvOperation.propertyUpdater.processing(csv, testUser, Set())
    result should equal(massTransitStopCsvOperation.propertyUpdater.ImportResultPointAsset(
      incompleteRows = List(IncompleteRow(missingParameters = List("pysäkin nimi"),
        csvRow = massTransitStopCsvOperation.propertyUpdater.rowToString(massTransitStopImporterUpdate.defaultValues(Map()) - "pysäkin nimi" ++ Map("valtakunnallinen id" -> 1))))))

    verify(mockService, never).updateExistingById(anyLong(), anyObject(), anyObject(), anyString(), anyObject(), anyBoolean())
  }
  /*
    test("ignore updates on other road types than streets when import is limited to streets") {
      val (mockMassTransitStopService, mockVVHClient) = mockWithMassTransitStops(Seq((1l, State)))
      val massTransitStopCsvOperation = new TestMassTransitStopCsvOperation(mockVVHClient, mockRoadLinkService, mockEventBus, mockMassTransitStopService)
      val assetFields = Map("valtakunnallinen id" -> 1, "pysäkin nimi" -> "NewName")
      val csv =  massTransitStopImporterUpdate.csvRead(assetFields)

      val result = massTransitStopCsvOperation.propertyUpdater.processing(csv, testUser,  roadTypeLimitations = Set(Municipality))
      result should equal(massTransitStopCsvOperation.propertyUpdater.ImportResultPointAsset(
        excludedRows = List(ExcludedRow(affectedRows = "State", csvRow = massTransitStopCsvOperation.propertyUpdater.rowToString(massTransitStopImporterUpdate.defaultValues(assetFields))))))

      verify(mockService, never).updateExistingById(anyLong(), anyObject(), anyObject(), anyString(), anyObject(), anyBoolean())
    }

    test("update asset on street when import is limited to streets") {
      val (mockMassTransitStopService, mockVVHClient) = mockWithMassTransitStops(Seq((1l, Municipality)))
      val massTransitStopCsvOperation = new TestMassTransitStopCsvOperation(mockVVHClient, mockRoadLinkService, mockEventBus, mockMassTransitStopService)
      val csv = massTransitStopImporterUpdate.csvRead(Map("valtakunnallinen id" -> 1, "pysäkin nimi" -> "NewName"))

      val result = massTransitStopCsvOperation.propertyUpdater.processing(csv, testUser, roadTypeLimitations = Set(Municipality))
      result should equal(massTransitStopCsvOperation.propertyUpdater.ImportResultPointAsset())
      val properties = Set(SimpleProperty("nimi_suomeksi", Seq(PropertyValue("NewName"))))
      verify(mockMassTransitStopService).updateExistingById(ArgumentMatchers.eq(1l), ArgumentMatchers.eq(None), ArgumentMatchers.eq(properties), ArgumentMatchers.eq("CsvDataImportApiSpec"), anyObject(), anyBoolean())
    }

    test("update asset on roads and streets when import is limited to roads and streets") {
      val (mockMassTransitStopService, mockVVHClient) = mockWithMassTransitStops(Seq((1l, Municipality), (2l, State)))
      val massTransitStopCsvOperation = new TestMassTransitStopCsvOperation(mockVVHClient, mockRoadLinkService, mockEventBus, mockMassTransitStopService)
      val csv = massTransitStopImporterUpdate.csvRead(Map("valtakunnallinen id" -> 1, "pysäkin nimi" -> "NewName1"), Map("valtakunnallinen id" -> 2, "pysäkin nimi" -> "NewName2"))

      val result = massTransitStopCsvOperation.propertyUpdater.processing(csv, testUser, roadTypeLimitations = Set(State, Municipality))
      result should equal(massTransitStopCsvOperation.propertyUpdater.ImportResultPointAsset())

      val properties1 = Set(SimpleProperty("nimi_suomeksi", List(PropertyValue("NewName1", None, false))))
      val properties2 = Set(SimpleProperty("nimi_suomeksi", List(PropertyValue("NewName2", None, false))))
      verify(mockMassTransitStopService).updateExistingById(ArgumentMatchers.eq(1l), ArgumentMatchers.eq(None), ArgumentMatchers.eq(properties1), ArgumentMatchers.eq("CsvDataImportApiSpec"), anyObject(), anyBoolean())
      verify(mockMassTransitStopService).updateExistingById(ArgumentMatchers.eq(2l), ArgumentMatchers.eq(None), ArgumentMatchers.eq(properties2), ArgumentMatchers.eq("CsvDataImportApiSpec"), anyObject(), anyBoolean())
    }


    test("ignore updates on all other road types than private roads when import is limited to private roads") {
      val (mockMassTransitStopService, mockVVHClient) = mockWithMassTransitStops(Seq((1l, Municipality), (2l, State)))
      val massTransitStopCsvOperation = new TestMassTransitStopCsvOperation(mockVVHClient, mockRoadLinkService, mockEventBus, mockMassTransitStopService)
      val assetOnStreetFields = Map("valtakunnallinen id" -> 1, "pysäkin nimi" -> "NewName1")
      val assetOnRoadFields = Map("valtakunnallinen id" -> 2, "pysäkin nimi" -> "NewName2")
      val csv = massTransitStopImporterUpdate.csvRead(assetOnStreetFields, assetOnRoadFields)

      val result = massTransitStopCsvOperation.propertyUpdater.processing(csv, testUser, roadTypeLimitations = Set(Private))
      result should equal(massTransitStopCsvOperation.propertyUpdater.ImportResultPointAsset(
        excludedRows = List(ExcludedRow(affectedRows = "State", csvRow = massTransitStopCsvOperation.propertyUpdater.rowToString(massTransitStopImporterUpdate.defaultValues(assetOnRoadFields))),
          ExcludedRow(affectedRows = "Municipality", csvRow = massTransitStopCsvOperation.propertyUpdater.rowToString(massTransitStopImporterUpdate.defaultValues(assetOnStreetFields))))))

      verify(mockMassTransitStopService, never).updateExistingById(anyLong(), anyObject(), anyObject(), anyString(), anyObject(), anyBoolean())
    }*/

  private def csvToInputStream(csv: String): InputStream = new ByteArrayInputStream(csv.getBytes())
}
