
package fi.liikennevirasto.digiroad2.dataimport

import java.io.{ByteArrayInputStream, InputStream, InputStreamReader}

import com.github.tototoshi.csv.{CSVReader, DefaultCSVFormat}
import javax.sql.DataSource
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.client.vvh._
import fi.liikennevirasto.digiroad2.dao.{MassTransitStopDao, MunicipalityDao, RoadLinkDAO}
import fi.liikennevirasto.digiroad2.user.{Configuration, User}
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfter, Tag}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import slick.driver.JdbcDriver.backend.Database
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import fi.liikennevirasto.digiroad2.Digiroad2Context.userProvider
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.client.tierekisteri.TierekisteriMassTransitStopClient
import fi.liikennevirasto.digiroad2.csvDataImporter.{Creator, MassTransitStopCsvImporter, MassTransitStopCsvOperation, PositionUpdater, RoadLinkCsvImporter, TrafficSignCsvImporter, Updater}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.service.{RoadAddressService, RoadLinkService}
import fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop.{MassTransitStopService, MassTransitStopWithProperties, PersistedMassTransitStop}
import fi.liikennevirasto.digiroad2.util.{GeometryTransform, RoadAddress, RoadSide, Track}
import org.mockito.ArgumentMatchers
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer

object sTestTransactions {
  def runWithRollback(ds: DataSource = OracleDatabase.ds)(f: => Unit): Unit = {
    Database.forDataSource(ds).withDynTransaction {
      f
      dynamicSession.rollback()
    }
  }
  def withDynTransaction[T](ds: DataSource = OracleDatabase.ds)(f: => T): T = {
    Database.forDataSource(ds).withDynTransaction {
      f
    }
  }
  def withDynSession[T](ds: DataSource = OracleDatabase.ds)(f: => T): T = {
    Database.forDataSource(ds).withDynSession {
      f
    }
  }
}

class CsvDataImporterSpec extends AuthenticatedApiSpec with BeforeAndAfter {
  val MunicipalityKauniainen = 235
  private val testUserProvider = userProvider
  private val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  private val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  private val mockVVHClient = MockitoSugar.mock[VVHClient]

  val vvHRoadlink = Seq(VVHRoadlink(1611400, 235, Seq(Point(2, 2), Point(4, 4)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers))
  val roadLink = Seq(RoadLink(1, Seq(Point(2, 2), Point(4, 4)), 3.5, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None))

  when(mockRoadLinkService.getClosestRoadlinkForCarTrafficFromVVH(any[User], any[Point])).thenReturn(vvHRoadlink)
  when(mockRoadLinkService.enrichRoadLinksFromVVH(any[Seq[VVHRoadlink]], any[Seq[ChangeInfo]])).thenReturn(roadLink)

  def runWithRollback(test: => Unit): Unit = sTestTransactions.runWithRollback()(test)

  object trafficSignCsvImporter extends TrafficSignCsvImporter(mockRoadLinkService, mockEventBus) {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def eventBus: DigiroadEventBus = mockEventBus

  }

  object roadLinkCsvImporter extends RoadLinkCsvImporter(mockRoadLinkService, mockEventBus) {
    override lazy val vvhClient: VVHClient = MockitoSugar.mock[VVHClient]
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def eventBus: DigiroadEventBus = mockEventBus
    override def withDynTransaction[T](f: => T): T = f

    private val defaultKeys = "linkin id" :: mappings.keys.toList
    val defaultValues = defaultKeys.map { key => key -> "" }.toMap

    def createCSV(assets: Map[String, Any]*): String = {
      val headers = defaultKeys.mkString(";") + "\n"
      val rows = assets.map { asset =>
        defaultKeys.map { key => asset.getOrElse(key, "") }.mkString(";")
      }.mkString("\n")
      headers + rows
    }
  }

  private val testUser: User = User(id = 1, username = "CsvDataImportApiSpec", configuration = Configuration(authorizedMunicipalities = Set(MunicipalityKauniainen)))

  before {
    testUserProvider.setCurrentUser(testUser)
  }

  test("rowToString works correctly for few basic fields") {
    roadLinkCsvImporter.rowToString(Map(
      "hallinnollinen luokka" -> "Hallinnollinen",
      "toiminnallinen luokka" -> "Toiminnallinen"
    )) should equal("hallinnollinen luokka: 'Hallinnollinen', toiminnallinen luokka: 'Toiminnallinen'")
  }

  private def createCsvForTrafficSigns(assets: Map[String, Any]*): String = {
    val headers = trafficSignCsvImporter.mappings.keys.toList.mkString(";") + "\n"
    val rows = assets.map { asset =>
      trafficSignCsvImporter.mappings.keys.toList.map { key => asset.getOrElse(key, "") }.mkString(";")
    }.mkString("\n")
    headers + rows
  }

  test("validation fails if field type \"Linkin ID\" is not filled", Tag("db")) {
    val roadLinkFields = Map("tien nimi (suomi)" -> "nimi", "liikennevirran suunta" -> "5")
    val invalidCsv = csvToInputStream(roadLinkCsvImporter.createCSV(roadLinkFields))
    roadLinkCsvImporter.processing(invalidCsv, testUser.username) should equal(roadLinkCsvImporter.ImportResultRoadLink(
      malformedRows = List(MalformedRow(
        malformedParameters = List("linkin id"),
        csvRow = roadLinkCsvImporter.rowToString(roadLinkCsvImporter.defaultValues ++ roadLinkFields)))))
  }

  test("validation fails if type contains illegal characters", Tag("db")) {
    val newLinkId1 = 5000
    val municipalityCode = 564
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val attributes1 = Map("OBJECTID" -> BigInt(99))

    val newRoadLink1 = VVHRoadlink(newLinkId1, municipalityCode, List(Point(0.0, 0.0), Point(20.0, 0.0)), administrativeClass, trafficDirection, FeatureClass.DrivePath, None, attributes1)
    val mockVVHComplementaryClient = MockitoSugar.mock[VVHComplementaryClient]

    when(roadLinkCsvImporter.vvhClient.complementaryData).thenReturn(mockVVHComplementaryClient)
    when(mockVVHComplementaryClient.fetchByLinkId(any[Long])).thenReturn(Some(newRoadLink1))

    val assetFields = Map("linkin id" -> 1, "liikennevirran suunta" -> "a")
    val invalidCsv = csvToInputStream(roadLinkCsvImporter.createCSV(assetFields))
    roadLinkCsvImporter.processing(invalidCsv, testUser.username) should equal(roadLinkCsvImporter.ImportResultRoadLink(
      malformedRows = List(MalformedRow(
        malformedParameters = List("liikennevirran suunta"),
        csvRow = roadLinkCsvImporter.rowToString(roadLinkCsvImporter.defaultValues ++ assetFields)))))
  }

  test("validation fails if administrative class = 1 on VVH", Tag("db")) {
    val newLinkId1 = 5000
    val municipalityCode = 564
    val administrativeClass = State
    val trafficDirection = TrafficDirection.BothDirections
    val attributes1 = Map("OBJECTID" -> BigInt(99))

    val newRoadLink1 = VVHRoadlink(newLinkId1, municipalityCode, List(Point(0.0, 0.0), Point(20.0, 0.0)), administrativeClass, trafficDirection, FeatureClass.DrivePath, None, attributes1)
    val mockVVHComplementaryClient = MockitoSugar.mock[VVHComplementaryClient]

    when(roadLinkCsvImporter.vvhClient.complementaryData).thenReturn(mockVVHComplementaryClient)
    when(mockVVHComplementaryClient.fetchByLinkId(any[Long])).thenReturn(Some(newRoadLink1))

    val assetFields = Map("linkin id" -> 1, "hallinnollinen luokka" -> 2)
    val invalidCsv = csvToInputStream(roadLinkCsvImporter.createCSV(assetFields))
    roadLinkCsvImporter.processing(invalidCsv, testUser.username) should equal(roadLinkCsvImporter.ImportResultRoadLink(
      excludedRows = List(ExcludedRow(affectedRows = "AdminClass value State found on  VVH", csvRow = roadLinkCsvImporter.rowToString(roadLinkCsvImporter.defaultValues ++ assetFields)))))
  }

  test("validation fails if administrative class = 1 on CSV", Tag("db")) {
    val newLinkId1 = 5000
    val municipalityCode = 564
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val attributes1 = Map("OBJECTID" -> BigInt(99))

    val newRoadLink1 = VVHRoadlink(newLinkId1, municipalityCode, List(Point(0.0, 0.0), Point(20.0, 0.0)), administrativeClass, trafficDirection, FeatureClass.DrivePath, None, attributes1)
    val mockVVHComplementaryClient = MockitoSugar.mock[VVHComplementaryClient]

    when(roadLinkCsvImporter.vvhClient.complementaryData).thenReturn(mockVVHComplementaryClient)
    when(mockVVHComplementaryClient.fetchByLinkId(any[Long])).thenReturn(Some(newRoadLink1))

    val assetFields = Map("linkin id" -> 1, "hallinnollinen luokka" -> 1)
    val invalidCsv = csvToInputStream(roadLinkCsvImporter.createCSV(assetFields))
    roadLinkCsvImporter.processing(invalidCsv, testUser.username) should equal(roadLinkCsvImporter.ImportResultRoadLink(
      excludedRows = List(ExcludedRow(affectedRows = "AdminClass value State found on  CSV", csvRow = roadLinkCsvImporter.rowToString(roadLinkCsvImporter.defaultValues ++ assetFields)))))
  }

  test("validation fails if administrative class = 1 on CSV and VVH", Tag("db")) {
    val newLinkId1 = 5000
    val municipalityCode = 564
    val administrativeClass = State
    val trafficDirection = TrafficDirection.BothDirections
    val attributes1 = Map("OBJECTID" -> BigInt(99))

    val newRoadLink1 = VVHRoadlink(newLinkId1, municipalityCode, List(Point(0.0, 0.0), Point(20.0, 0.0)), administrativeClass, trafficDirection, FeatureClass.DrivePath, None, attributes1)
    val mockVVHComplementaryClient = MockitoSugar.mock[VVHComplementaryClient]

    when(roadLinkCsvImporter.vvhClient.complementaryData).thenReturn(mockVVHComplementaryClient)
    when(mockVVHComplementaryClient.fetchByLinkId(any[Long])).thenReturn(Some(newRoadLink1))

    val assetFields = Map("linkin id" -> 1, "hallinnollinen luokka" -> 1)
    val invalidCsv = csvToInputStream(roadLinkCsvImporter.createCSV(assetFields))
    roadLinkCsvImporter.processing(invalidCsv, testUser.username) should equal(roadLinkCsvImporter.ImportResultRoadLink(
      excludedRows = List(ExcludedRow(affectedRows = "AdminClass value State found on  VVH/AdminClass value State found on  CSV", csvRow = roadLinkCsvImporter.rowToString(roadLinkCsvImporter.defaultValues ++ assetFields)))))
  }

  test("update functionalClass by CSV import", Tag("db")) {
    val newLinkId1 = 5000
    val municipalityCode = 564
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val attributes1 = Map("OBJECTID" -> BigInt(99))

    val newRoadLink1 = VVHRoadlink(newLinkId1, municipalityCode, List(Point(0.0, 0.0), Point(20.0, 0.0)), administrativeClass, trafficDirection, FeatureClass.DrivePath, None, attributes1)
    val mockVVHComplementaryClient = MockitoSugar.mock[VVHComplementaryClient]

    runWithRollback {
      when(roadLinkCsvImporter.vvhClient.complementaryData).thenReturn(mockVVHComplementaryClient)
      when(mockVVHComplementaryClient.fetchByLinkId(any[Long])).thenReturn(Some(newRoadLink1))

      val link_id = 1000
      val functionalClassValue = 3
      RoadLinkDAO.insert(RoadLinkDAO.FunctionalClass, link_id, Some("unit_test"), 2)

      val csv = csvToInputStream(roadLinkCsvImporter.createCSV(Map("linkin id" -> link_id, "toiminnallinen luokka" -> functionalClassValue)))
      roadLinkCsvImporter.processing(csv, testUser.username) should equal(roadLinkCsvImporter.ImportResultRoadLink())
      RoadLinkDAO.get(RoadLinkDAO.FunctionalClass, link_id) should equal (Some(functionalClassValue))
    }
  }

  test("insert functionalClass by CSV import", Tag("db")) {
    val newLinkId1 = 5000
    val municipalityCode = 564
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val attributes1 = Map("OBJECTID" -> BigInt(99))

    val newRoadLink1 = VVHRoadlink(newLinkId1, municipalityCode, List(Point(0.0, 0.0), Point(20.0, 0.0)), administrativeClass, trafficDirection, FeatureClass.DrivePath, None, attributes1)
    val mockVVHComplementaryClient = MockitoSugar.mock[VVHComplementaryClient]

    runWithRollback {
      when(roadLinkCsvImporter.vvhClient.complementaryData).thenReturn(mockVVHComplementaryClient)
      when(mockVVHComplementaryClient.fetchByLinkId(any[Long])).thenReturn(Some(newRoadLink1))

      val link_id = 1000
      val functionalClassValue = 3

      val csv = csvToInputStream(roadLinkCsvImporter.createCSV(Map("linkin id" -> link_id, "toiminnallinen luokka" -> functionalClassValue)))
      roadLinkCsvImporter.processing(csv, testUser.username) should equal(roadLinkCsvImporter.ImportResultRoadLink())
      RoadLinkDAO.get(RoadLinkDAO.FunctionalClass, link_id) should equal (Some(functionalClassValue))
    }
  }

  test("update linkType by CSV import", Tag("db")) {
    val newLinkId1 = 5000
    val municipalityCode = 564
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val attributes1 = Map("OBJECTID" -> BigInt(99))

    val newRoadLink1 = VVHRoadlink(newLinkId1, municipalityCode, List(Point(0.0, 0.0), Point(20.0, 0.0)), administrativeClass, trafficDirection, FeatureClass.DrivePath, None, attributes1)
    val mockVVHComplementaryClient = MockitoSugar.mock[VVHComplementaryClient]

    runWithRollback {
      when(roadLinkCsvImporter.vvhClient.complementaryData).thenReturn(mockVVHComplementaryClient)
      when(mockVVHComplementaryClient.fetchByLinkId(any[Long])).thenReturn(Some(newRoadLink1))
      val link_id = 1000
      val linkTypeValue = 3
      RoadLinkDAO.insert(RoadLinkDAO.LinkType, link_id, Some("unit_test"), 2)

      val csv = csvToInputStream(roadLinkCsvImporter.createCSV(Map("linkin id" -> link_id, "tielinkin tyyppi" ->linkTypeValue)))
      roadLinkCsvImporter.processing(csv, testUser.username) should equal(roadLinkCsvImporter.ImportResultRoadLink())
      RoadLinkDAO.get(RoadLinkDAO.LinkType, link_id) should equal (Some(linkTypeValue))
    }
  }

  test("insert linkType by CSV import", Tag("db")) {
    val newLinkId1 = 5000
    val municipalityCode = 564
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val attributes1 = Map("OBJECTID" -> BigInt(99))

    val newRoadLink1 = VVHRoadlink(newLinkId1, municipalityCode, List(Point(0.0, 0.0), Point(20.0, 0.0)), administrativeClass, trafficDirection, FeatureClass.DrivePath, None, attributes1)
    val mockVVHComplementaryClient = MockitoSugar.mock[VVHComplementaryClient]

    runWithRollback {
      when(roadLinkCsvImporter.vvhClient.complementaryData).thenReturn(mockVVHComplementaryClient)
      when(mockVVHComplementaryClient.fetchByLinkId(any[Long])).thenReturn(Some(newRoadLink1))
      val link_id = 1000
      val linkTypeValue = 3

      val csv = csvToInputStream(roadLinkCsvImporter.createCSV(Map("linkin id" -> link_id, "tielinkin tyyppi" -> linkTypeValue)))
      roadLinkCsvImporter.processing(csv, testUser.username) should equal(roadLinkCsvImporter.ImportResultRoadLink())
      RoadLinkDAO.get(RoadLinkDAO.LinkType, link_id) should equal (Some(linkTypeValue))
    }
  }

  test("delete trafficDirection (when already exist in db) by CSV import", Tag("db")) {
    val newLinkId1 = 5000
    val municipalityCode = 564
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val attributes1 = Map("OBJECTID" -> BigInt(99))

    val newRoadLink1 = VVHRoadlink(newLinkId1, municipalityCode, List(Point(0.0, 0.0), Point(20.0, 0.0)), administrativeClass, trafficDirection, FeatureClass.DrivePath, None, attributes1)

    val mockVVHComplementaryClient = MockitoSugar.mock[VVHComplementaryClient]

    runWithRollback {
      when(roadLinkCsvImporter.vvhClient.complementaryData).thenReturn(mockVVHComplementaryClient)
      when(mockVVHComplementaryClient.updateVVHFeatures(any[Map[String , String]])).thenReturn( Left(List(Map("key" -> "value"))))
      when(mockVVHComplementaryClient.fetchByLinkId(any[Long])).thenReturn(Some(newRoadLink1))
      val link_id = 1611388
      RoadLinkDAO.insert(RoadLinkDAO.TrafficDirection, link_id, Some("unit_test"), 1)
      val csv = csvToInputStream(roadLinkCsvImporter.createCSV(Map("linkin id" -> link_id, "liikennevirran suunta" -> 3)))

      roadLinkCsvImporter.processing(csv, testUser.username) should equal(roadLinkCsvImporter.ImportResultRoadLink())
      RoadLinkDAO.get(RoadLinkDAO.TrafficDirection, link_id) should equal (None)
    }
  }

  test("update OTH and VVH by CSV import", Tag("db")) {
    val newLinkId1 = 5000
    val municipalityCode = 564
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val attributes1 = Map("OBJECTID" -> BigInt(99))

    val newRoadLink1 = VVHRoadlink(newLinkId1, municipalityCode, List(Point(0.0, 0.0), Point(20.0, 0.0)), administrativeClass, trafficDirection, FeatureClass.DrivePath, None, attributes1)

    val mockVVHComplementaryClient = MockitoSugar.mock[VVHComplementaryClient]

    runWithRollback {
      when(roadLinkCsvImporter.vvhClient.complementaryData).thenReturn(mockVVHComplementaryClient)
      when(mockVVHComplementaryClient.updateVVHFeatures(any[Map[String , String]])).thenReturn( Left(List(Map("key" -> "value"))))
      when(mockVVHComplementaryClient.fetchByLinkId(any[Long])).thenReturn(Some(newRoadLink1))
      val link_id = 1000
      val linkTypeValue = 3
      RoadLinkDAO.insert(RoadLinkDAO.LinkType, link_id, Some("unit_test"), 2)

      val csv = csvToInputStream(roadLinkCsvImporter.createCSV(Map("linkin id" -> link_id, "tielinkin tyyppi" -> linkTypeValue, "kuntanumero" -> 2,
        "liikennevirran suunta" -> 1, "hallinnollinen luokka" -> 2)))
      roadLinkCsvImporter.processing(csv, testUser.username) should equal(roadLinkCsvImporter.ImportResultRoadLink())
      RoadLinkDAO.get(RoadLinkDAO.LinkType, link_id) should equal(Some(linkTypeValue))
    }
  }

  test("validation for traffic sign import fails if mandatory parameters are missing", Tag("db")) {
    val assetFields = Map("koordinaatti x" -> "", "koordinaatti y" -> "", "liikennemerkin tyyppi" -> "")
    val invalidCsv = csvToInputStream(createCsvForTrafficSigns(assetFields))
    val defaultValues = trafficSignCsvImporter.mappings.keys.toList.map { key => key -> "" }.toMap
    val csvRow = trafficSignCsvImporter.rowToString(defaultValues ++ assetFields)
    val assets = trafficSignCsvImporter.processing(invalidCsv, Set(), testUser)

    assets.malformedRows.flatMap(_.malformedParameters) should contain allOf ("koordinaatti x", "koordinaatti y", "liikennemerkin tyyppi")
    assets.malformedRows.foreach {
      asset =>
        asset.csvRow should be (trafficSignCsvImporter.rowToString(defaultValues ++ assetFields))
    }
  }

  test("validation for traffic sign import fails if user try to create in an authorize municipality", Tag("db")) {
    val assetFields = Map("koordinaatti x" -> 1, "koordinaatti y" -> 1, "liikennemerkin tyyppi" -> "F37")
    val invalidCsv = csvToInputStream(createCsvForTrafficSigns(assetFields))
    val defaultValues = trafficSignCsvImporter.mappings.keys.toList.map { key => key -> "" }.toMap
    when(mockRoadLinkService.getClosestRoadlinkForCarTrafficFromVVH(any[User], any[Point])).thenReturn(Seq())

    trafficSignCsvImporter.processing(invalidCsv, Set(), testUser) should equal(trafficSignCsvImporter.ImportResultPointAsset(
      notImportedData = List(trafficSignCsvImporter.NotImportedData(
        reason = "No Rights for Municipality or nonexistent road links near asset position",
        csvRow = trafficSignCsvImporter.rowToString(defaultValues ++ assetFields)))))
  }

  val mockGeometryTransform = MockitoSugar.mock[GeometryTransform]
  val mockTierekisteriClient = MockitoSugar.mock[TierekisteriMassTransitStopClient]

  private def csvToInputStream(csv: String): InputStream = new ByteArrayInputStream(csv.getBytes())
}
