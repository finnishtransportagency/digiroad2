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
import org.scalatest.BeforeAndAfter
import slick.driver.JdbcDriver.backend.Database
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import fi.liikennevirasto.digiroad2.Digiroad2Context.userProvider
import fi.liikennevirasto.digiroad2.client.tierekisteri.TierekisteriMassTransitStopClient
import fi.liikennevirasto.digiroad2.csvDataImporter.{Creator, MassTransitStopCsvImporter, MassTransitStopCsvOperation, PositionUpdater, Updater}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop.{MassTransitStopService, MassTransitStopWithProperties}
import fi.liikennevirasto.digiroad2.util.GeometryTransform
import org.mockito.ArgumentMatchers


class MassTransitStopCsvImporterSpec extends AuthenticatedApiSpec with BeforeAndAfter {
  val MunicipalityKauniainen = 235
  private val testUserProvider = userProvider
  private val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  private val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  private val mockVVHClient = MockitoSugar.mock[VVHClient]
  private val mockMassTransitStopService = MockitoSugar.mock[MassTransitStopService]
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

  val massTransitStopCsvOperation = new TestMassTransitStopCsvOperation(mockRoadLinkService, mockEventBus, mockMassTransitStopService)

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

  private def municipalityValidation(municipality: Int)(user: User): Unit = {
  }

  test("update asset admin id by CSV import") {
    when(mockMassTransitStopService.getMassTransitStopByNationalId(ArgumentMatchers.eq(1l), anyObject(), anyBoolean())).thenReturn(Some(MassTransitStopWithProperties(1, 1, Nil, 0.0, 0.0, None, None, None, false, Nil))).thenReturn(Some(MassTransitStopWithProperties(1, 1, Nil, 0.0, 0.0, None, None, None, false, Nil)))
    val csv = massTransitStopImporterUpdate.csvRead(Map("Valtakunnallinen ID" -> 1, "Ylläpitäjän tunnus" -> "NewAdminId"))

    massTransitStopCsvOperation.propertyUpdater.processing(csv, testUser, Set())

    val properties = Set(SimpleProperty("yllapitajan_tunnus", Seq(PropertyValue("NewAdminId"))), SimpleProperty("external_id",Seq(PropertyValue("1"))))
    verify(mockMassTransitStopService).updateExistingById(ArgumentMatchers.eq(1l), ArgumentMatchers.eq(None), ArgumentMatchers.eq(properties), ArgumentMatchers.eq("CsvDataImportApiSpec"), anyObject(), anyBoolean())
      }

  private def csvToInputStream(csv: String): InputStream = new ByteArrayInputStream(csv.getBytes())
}
