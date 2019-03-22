package fi.liikennevirasto.digiroad2.dataimport

import java.io.{ByteArrayInputStream, InputStream}

import javax.sql.DataSource
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.{dao, _}
import fi.liikennevirasto.digiroad2.client.vvh._
import fi.liikennevirasto.digiroad2.dao.{MassTransitStopDao, MunicipalityDao, RoadLinkDAO}
import fi.liikennevirasto.digiroad2.user.{Configuration, User, UserProvider}
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
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.service.RoadLinkService
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
    private val defaultKeys = "Linkin ID" :: mappings.keys.toList
    val defaultValues = defaultKeys.map { key => key -> "" }.toMap

    def createCSV(assets: Map[String, Any]*): String = {
      val headers = defaultKeys.mkString(";") + "\n"
      val rows = assets.map { asset =>
        defaultKeys.map { key => asset.getOrElse(key, "") }.mkString(";")
      }.mkString("\n")
      headers + rows
    }
  }

  object massTransitStopImporter extends MassTransitStopCsvImporter(mockRoadLinkService, mockEventBus) {
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def eventBus: DigiroadEventBus = mockEventBus
    override def withDynTransaction[T](f: => T): T = f
    val defaultKeys = "Valtakunnallinen ID" :: mappings.keys.toList

    val defaultValues = defaultKeys.map { key => key -> "" }.toMap
    def createCSV(assets: Map[String, Any]*): String = {
      val headers = defaultKeys.mkString(";") + "\n"
      val rows = assets.map { asset =>
        defaultKeys.map { key => asset.getOrElse(key, "") }.mkString(";")
      }.mkString("\n")
      headers + rows
    }
  }


  private def importerWithService(service: MassTransitStopService, testVVHClient: VVHClient) : MassTransitStopCsvImporter = {
    new MassTransitStopCsvImporter(mockRoadLinkService, mockEventBus) {
      override lazy val massTransitStopService: MassTransitStopService = service
      override val vvhClient: VVHClient = testVVHClient
    }
  }


  private val testUser: User = User(id = 1, username = "CsvDataImportApiSpec", configuration = Configuration(authorizedMunicipalities = Set(MunicipalityKauniainen)))

  before {
    testUserProvider.setCurrentUser(testUser)
  }

  test("rowToString works correctly for few basic fields") {
    roadLinkCsvImporter.rowToString(Map(
      "Hallinnollinen luokka" -> "Hallinnollinen",
      "Toiminnallinen luokka" -> "Toiminnallinen"
    )) should equal("Hallinnollinen luokka: 'Hallinnollinen', Toiminnallinen luokka: 'Toiminnallinen'")
  }

  private def createCsvForTrafficSigns(assets: Map[String, Any]*): String = {
    val headers = trafficSignCsvImporter.mappings.keys.toList.mkString(";") + "\n"
    val rows = assets.map { asset =>
      trafficSignCsvImporter.mappings.keys.toList.map { key => asset.getOrElse(key, "") }.mkString(";")
    }.mkString("\n")
    headers + rows
  }
  test("validation for traffic sign import fails if type contains illegal characters", Tag("db")) {
    val assetFields = Map("koordinaatti x" -> 1, "koordinaatti y" -> 1, "liikennemerkin tyyppi" -> "a")
    val invalidCsv = csvToInputStream(createCsvForTrafficSigns(assetFields))
    val defaultValues = trafficSignCsvImporter.mappings.keys.toList.map { key => key -> "" }.toMap

    trafficSignCsvImporter.processing(invalidCsv, Set(), testUser) should equal(trafficSignCsvImporter.ImportResultTrafficSign(
      malformedRows = List(MalformedRow(
        malformedParameters = List("liikennemerkin tyyppi"),
        csvRow = trafficSignCsvImporter.rowToString(defaultValues ++ assetFields)))))
  }



  test("validation fails if field type \"Linkin ID\" is not filled", Tag("db")) {
    val roadLinkFields = Map("Tien nimi (suomi)" -> "nimi", "Liikennevirran suunta" -> "5")
    val invalidCsv = csvToInputStream(roadLinkCsvImporter.createCSV(roadLinkFields))
    roadLinkCsvImporter.processing(invalidCsv, testUser.username) should equal(roadLinkCsvImporter.ImportResultRoadLink(
      malformedRows = List(MalformedRow(
        malformedParameters = List("Linkin ID"),
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

    val assetFields = Map("Linkin ID" -> 1, "Liikennevirran suunta" -> "a")
    val invalidCsv = csvToInputStream(roadLinkCsvImporter.createCSV(assetFields))
    roadLinkCsvImporter.processing(invalidCsv, testUser.username) should equal(roadLinkCsvImporter.ImportResultRoadLink(
      malformedRows = List(MalformedRow(
        malformedParameters = List("Liikennevirran suunta"),
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

    val assetFields = Map("Linkin ID" -> 1, "Hallinnollinen luokka" -> 2)
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

    val assetFields = Map("Linkin ID" -> 1, "Hallinnollinen luokka" -> 1)
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

    val assetFields = Map("Linkin ID" -> 1, "Hallinnollinen luokka" -> 1)
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

      val csv = csvToInputStream(roadLinkCsvImporter.createCSV(Map("Linkin ID" -> link_id, "Toiminnallinen luokka" -> functionalClassValue)))
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

      val csv = csvToInputStream(roadLinkCsvImporter.createCSV(Map("Linkin ID" -> link_id, "Toiminnallinen luokka" -> functionalClassValue)))
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

      val csv = csvToInputStream(roadLinkCsvImporter.createCSV(Map("Linkin ID" -> link_id, "Tielinkin tyyppi" ->linkTypeValue)))
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

      val csv = csvToInputStream(roadLinkCsvImporter.createCSV(Map("Linkin ID" -> link_id, "Tielinkin tyyppi" -> linkTypeValue)))
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
      val csv = csvToInputStream(roadLinkCsvImporter.createCSV(Map("Linkin ID" -> link_id, "Liikennevirran suunta" -> 3)))

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

      val csv = csvToInputStream(roadLinkCsvImporter.createCSV(Map("Linkin ID" -> link_id, "Tielinkin tyyppi" -> linkTypeValue, "Kuntanumero" -> 2,
        "Liikennevirran suunta" -> 1, "Hallinnollinen luokka" -> 2)))
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
    val assetFields = Map("koordinaatti x" -> 1, "koordinaatti y" -> 1, "liikennemerkin tyyppi" -> "671")
    val invalidCsv = csvToInputStream(createCsvForTrafficSigns(assetFields))
    val defaultValues = trafficSignCsvImporter.mappings.keys.toList.map { key => key -> "" }.toMap
    when(mockRoadLinkService.getClosestRoadlinkForCarTrafficFromVVH(any[User], any[Point])).thenReturn(Seq())

    trafficSignCsvImporter.processing(invalidCsv, Set(), testUser) should equal(trafficSignCsvImporter.ImportResultTrafficSign(
      notImportedData = List(trafficSignCsvImporter.NotImportedData(
        reason = "Unathorized municipality or nonexistent roadlink near asset position",
        csvRow = trafficSignCsvImporter.rowToString(defaultValues ++ assetFields)))))
  }

  test("mts: rowToString works correctly for few basic fields") {
    massTransitStopImporter.rowToString(Map(
      "Valtakunnallinen ID" -> "ID",
      "Pysäkin nimi" -> "Nimi"
    )) should equal("Valtakunnallinen ID: 'ID', Pysäkin nimi: 'Nimi'")
  }



  test("update name by CSV import", Tag("db")) {
    val mockService = MockitoSugar.mock[MassTransitStopService]
    val mockVVHClient = MockitoSugar.mock[VVHClient]

    when(mockService.getMassTransitStopByNationalId(ArgumentMatchers.eq(1l), anyObject())).thenReturn(Some(MassTransitStopWithProperties(1, 1, Nil, 0.0, 0.0, None, None, None, false, Nil)))
    when(mockService.getMassTransitStopByNationalId(ArgumentMatchers.eq(2l), anyObject())).thenReturn(Some(MassTransitStopWithProperties(2, 2, Nil, 0.0, 0.0, None, None, None, false, Nil)))

    val csv = massTransitStopImporter.createCSV(Map("Valtakunnallinen ID" -> 1, "Pysäkin nimi" -> "UpdatedAssetName"),
      Map("Valtakunnallinen ID" -> 2, "Pysäkin nimi" -> "Asset2Name"))

    val importer = importerWithService(mockService, mockVVHClient)

    val inputStream = new ByteArrayInputStream(csv.getBytes)
    val result = importer.processing(inputStream, testUser)
    result should equal(importer.ImportResultMassTransitStop())

    val properties1 = Set(SimpleProperty("nimi_suomeksi", Seq(PropertyValue("UpdatedAssetName"))))
    val properties2 = Set(SimpleProperty("nimi_suomeksi", Seq(PropertyValue("Asset2Name"))))
    verify(mockService).updateExistingById(ArgumentMatchers.eq(1l), ArgumentMatchers.eq(None), ArgumentMatchers.eq(properties1), ArgumentMatchers.eq("CsvDataImportApiSpec"), anyObject())
    verify(mockService).updateExistingById(ArgumentMatchers.eq(2l), ArgumentMatchers.eq(None), ArgumentMatchers.eq(properties2), ArgumentMatchers.eq("CsvDataImportApiSpec"), anyObject())
  }

  test("do not update name if field is empty in CSV", Tag("db")) {
    val mockService = MockitoSugar.mock[MassTransitStopService]
    val mockVVHClient = MockitoSugar.mock[VVHClient]

    when(mockService.getMassTransitStopByNationalId(ArgumentMatchers.eq(1l), anyObject())).thenReturn(Some(MassTransitStopWithProperties(1, 1, Nil, 0.0, 0.0, None, None, None, false, Nil)))

    val importer = importerWithService(mockService, mockVVHClient)
    val csv = massTransitStopImporter.createCSV(Map("Valtakunnallinen ID" -> 1))

    val inputStream = new ByteArrayInputStream(csv.getBytes)
    val result = importer.processing(inputStream, testUser)
    result should equal(importer.ImportResultMassTransitStop())

    verify(mockService).updateExistingById(ArgumentMatchers.eq(1l), ArgumentMatchers.eq(None), ArgumentMatchers.eq(Set.empty), ArgumentMatchers.eq("CsvDataImportApiSpec"), anyObject())
  }

  test("validation fails if type is undefined", Tag("db")) {
    val assetFields = Map("Valtakunnallinen ID" -> 1, "Pysäkin tyyppi" -> ",")
    val invalidCsv = csvToInputStream(massTransitStopImporter.createCSV(assetFields))
    massTransitStopImporter.processing(invalidCsv, testUser) should equal(massTransitStopImporter.ImportResultMassTransitStop(
      malformedRows = List(MalformedRow(
        malformedParameters = List("Pysäkin tyyppi"),
        csvRow = massTransitStopImporter.rowToString(massTransitStopImporter.defaultValues ++ assetFields)))))
  }

  test("mts: validation fails if type contains illegal characters", Tag("db")) {
    val assetFields = Map("Valtakunnallinen ID" -> 1, "Pysäkin tyyppi" -> "2,a")
    val invalidCsv = csvToInputStream(massTransitStopImporter.createCSV(assetFields))
    massTransitStopImporter.processing(invalidCsv, testUser) should equal(massTransitStopImporter.ImportResultMassTransitStop(
      malformedRows = List(MalformedRow(
        malformedParameters = List("Pysäkin tyyppi"),
        csvRow = massTransitStopImporter.rowToString(massTransitStopImporter.defaultValues ++ assetFields)))))
  }

  test("validation fails when asset type is unknown", Tag("db")) {
    val assetFields = Map("Valtakunnallinen ID" -> 1, "Pysäkin tyyppi" -> "2,10")
    val invalidCsv = csvToInputStream(massTransitStopImporter.createCSV(assetFields))
    massTransitStopImporter.processing(invalidCsv, testUser) should equal(massTransitStopImporter.ImportResultMassTransitStop(
      malformedRows = List(MalformedRow(
        malformedParameters = List("Pysäkin tyyppi"),
        csvRow = massTransitStopImporter.rowToString(massTransitStopImporter.defaultValues ++ assetFields)))))
  }


  test("update asset admin id by CSV import", Tag("db")) {
    val mockService = MockitoSugar.mock[MassTransitStopService]
    val mockVVHClient = MockitoSugar.mock[VVHClient]

    when(mockService.getMassTransitStopByNationalId(ArgumentMatchers.eq(1l), anyObject())).thenReturn(Some(MassTransitStopWithProperties(1, 1, Nil, 0.0, 0.0, None, None, None, false, Nil)))

    val importer = importerWithService(mockService, mockVVHClient)
    val csv = csvToInputStream(massTransitStopImporter.createCSV(Map("Valtakunnallinen ID" -> 1, "Ylläpitäjän tunnus" -> "NewAdminId")))

    importer.processing(csv, testUser) should equal(importer.ImportResultMassTransitStop())
    val properties = Set(SimpleProperty("yllapitajan_tunnus", Seq(PropertyValue("NewAdminId"))))
    verify(mockService).updateExistingById(ArgumentMatchers.eq(1l), ArgumentMatchers.eq(None), ArgumentMatchers.eq(properties), ArgumentMatchers.eq("CsvDataImportApiSpec"), anyObject())
  }

  test("Should not update asset LiVi id by CSV import (after Tierekisteri integration)", Tag("db")) {
    val mockService = MockitoSugar.mock[MassTransitStopService]
    val mockVVHClient = MockitoSugar.mock[VVHClient]

    when(mockService.getMassTransitStopByNationalId(ArgumentMatchers.eq(1l), anyObject())).thenReturn(Some(MassTransitStopWithProperties(1, 1, Nil, 0.0, 0.0, None, None, None, false, Nil)))

    val importer = importerWithService(mockService, mockVVHClient)
    val csv = csvToInputStream(massTransitStopImporter.createCSV(Map("Valtakunnallinen ID" -> 1, "LiVi-tunnus" -> "Livi987654")))

    importer.processing(csv, testUser) should equal(importer.ImportResultMassTransitStop())
    val properties = Set(SimpleProperty("yllapitajan_koodi", Seq(PropertyValue("Livi987654")))).filterNot(_.publicId == "yllapitajan_koodi")
    verify(mockService).updateExistingById(ArgumentMatchers.eq(1l), ArgumentMatchers.eq(None), ArgumentMatchers.eq(properties), ArgumentMatchers.eq("CsvDataImportApiSpec"), anyObject())
  }

  test("update asset stop code by CSV import", Tag("db")) {
    val mockService = MockitoSugar.mock[MassTransitStopService]
    val mockVVHClient = MockitoSugar.mock[VVHClient]

    when(mockService.getMassTransitStopByNationalId(ArgumentMatchers.eq(1l), anyObject())).thenReturn(Some(MassTransitStopWithProperties(1, 1, Nil, 0.0, 0.0, None, None, None, false, Nil)))

    val importer = importerWithService(mockService, mockVVHClient)
    val csv = csvToInputStream(massTransitStopImporter.createCSV(Map("Valtakunnallinen ID" -> 1, "Matkustajatunnus" -> "H156")))

    importer.processing(csv, testUser) should equal(importer.ImportResultMassTransitStop())
    val properties = Set(SimpleProperty("matkustajatunnus", Seq(PropertyValue("H156"))))
    verify(mockService).updateExistingById(ArgumentMatchers.eq(1l), ArgumentMatchers.eq(None), ArgumentMatchers.eq(properties), ArgumentMatchers.eq("CsvDataImportApiSpec"), anyObject())
  }

  test("update additional information by CSV import", Tag("db")) {
    val mockService = MockitoSugar.mock[MassTransitStopService]
    val mockVVHClient = MockitoSugar.mock[VVHClient]

    when(mockService.getMassTransitStopByNationalId(ArgumentMatchers.eq(1l), anyObject())).thenReturn(Some(MassTransitStopWithProperties(1, 1, Nil, 0.0, 0.0, None, None, None, false, Nil)))

    val importer = importerWithService(mockService, mockVVHClient)
    val csv = csvToInputStream(massTransitStopImporter.createCSV(Map("Valtakunnallinen ID" -> 1, "Lisätiedot" -> "Updated additional info")))

    importer.processing(csv, testUser) should equal(importer.ImportResultMassTransitStop())
    val properties = Set(SimpleProperty("lisatiedot", Seq(PropertyValue("Updated additional info"))))
    verify(mockService).updateExistingById(ArgumentMatchers.eq(1l), ArgumentMatchers.eq(None), ArgumentMatchers.eq(properties), ArgumentMatchers.eq("CsvDataImportApiSpec"), anyObject())
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
    "tietojen_yllapitaja" -> ("1", "3"),
    "korotettu" -> ("1", "2"),
    "roska_astia" -> ("1", "2"),
    "vyohyketieto" -> ("InfoZone", "InfoZone ready")
  )

  test("update asset's properties in a generic manner", Tag("db")) {
    val mockService = MockitoSugar.mock[MassTransitStopService]
    val mockVVHClient = MockitoSugar.mock[VVHClient]

    when(mockService.getMassTransitStopByNationalId(ArgumentMatchers.eq(1l), anyObject())).thenReturn(Some(MassTransitStopWithProperties(1, 1, Nil, 0.0, 0.0, None, None, None, false, Nil)))

    val importer = importerWithService(mockService, mockVVHClient)
    val csv = csvToInputStream(massTransitStopImporter.createCSV(Map("Valtakunnallinen ID" -> 1) ++ massTransitStopImporter.mappings.mapValues(exampleValues(_)._2)))

    importer.processing(csv, testUser) should equal(importer.ImportResultMassTransitStop())
    val properties: Set[SimpleProperty] = exampleValues.map { case (key, value) =>
      SimpleProperty(key, Seq(PropertyValue(value._2)))
    }.filterNot(_.publicId == "yllapitajan_koodi").toSet

    verify(mockService).updateExistingById(ArgumentMatchers.eq(1l), ArgumentMatchers.eq(None), ArgumentMatchers.eq(properties), ArgumentMatchers.eq("CsvDataImportApiSpec"), anyObject())
  }

  test("raise an error when updating non-existent asset", Tag("db")) {
    val mockService = MockitoSugar.mock[MassTransitStopService]
    val mockVVHClient = MockitoSugar.mock[VVHClient]

    when(mockService.getMassTransitStopByNationalId(ArgumentMatchers.eq(6l), anyObject())).thenReturn(None)
    val assetFields = Map("Valtakunnallinen ID" -> "6", "Pysäkin nimi" -> "AssetName")

    val importer = importerWithService(mockService, mockVVHClient)
    val csv = massTransitStopImporter.createCSV(assetFields)

    val inputStream = new ByteArrayInputStream(csv.getBytes)
    val result = importer.processing(inputStream, testUser)

    result should equal(importer.ImportResultMassTransitStop(nonExistingAssets = List(importer.NonExistingAsset(externalId = 6, csvRow = importer.rowToString(massTransitStopImporter.defaultValues ++ assetFields)))))
  }

  test("raise an error when csv row does not define required parameter", Tag("db")) {
    val mockService = MockitoSugar.mock[MassTransitStopService]
    val mockVVHClient = MockitoSugar.mock[VVHClient]

    when(mockService.getMassTransitStopByNationalId(ArgumentMatchers.eq(1l), anyObject())).thenReturn(Some(MassTransitStopWithProperties(1, 1, Nil, 0.0, 0.0, None, None, None, false, Nil)))

    val importer = importerWithService(mockService, mockVVHClient)
    val missingRequiredKeys = massTransitStopImporter.defaultKeys.filterNot(Set("Pysäkin nimi"))
    val csv =
      missingRequiredKeys.mkString(";") + "\n" +
        s"${1}" + missingRequiredKeys.map(_ => ";").mkString + "\n"
    val inputStream = new ByteArrayInputStream(csv.getBytes)
    val result = importer.processing(inputStream, testUser)

    result should equal(importer.ImportResultMassTransitStop(
      incompleteRows = List(IncompleteRow(missingParameters = List("Pysäkin nimi"), csvRow = importer.rowToString(massTransitStopImporter.defaultValues - "Pysäkin nimi" ++ Map("Valtakunnallinen ID" -> 1))))))

    verify(mockService, never).updateExistingById(anyLong(), anyObject(), anyObject(), anyString(), anyObject())
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
    val mockMunicipalityDao = MockitoSugar.mock[MunicipalityDao]
    val mockGeometryTransform = MockitoSugar.mock[GeometryTransform]
    when(mockMassTransitStopDao.getAssetAdministrationClass(any[Long])).thenReturn(None)

    class TestMassTransitStopService(val eventbus: DigiroadEventBus, val roadLinkService: RoadLinkService) extends MassTransitStopService {
      override def withDynSession[T](f: => T): T = f
      override def withDynTransaction[T](f: => T): T = f
      override val tierekisteriClient: TierekisteriMassTransitStopClient = mockTierekisteriClient
      override val massTransitStopDao: MassTransitStopDao = mockMassTransitStopDao
      override val municipalityDao: MunicipalityDao = mockMunicipalityDao
      override val geometryTransform: GeometryTransform = mockGeometryTransform
    }

    when(mockGeometryTransform.resolveAddressAndLocation(any[Point], any[Int], any[Double], any[Long], any[Int], any[Option[Int]], any[Option[Int]])).thenReturn((RoadAddress(None, 0, 0, Track.Unknown, 0, None) , RoadSide.Right))
    val mockMassTransitStopService = MockitoSugar.mock[MassTransitStopService]
    stops.foreach { case (id, administrativeClass) =>
      when(mockMassTransitStopService.getByNationalId(ArgumentMatchers.eq(id), anyObject(), anyObject())).thenAnswer(new Answer[Option[Object]] {
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

  test("ignore updates on other road types than streets when import is limited to streets") {
    val (mockMassTransitStopService, mockVVHClient) = mockWithMassTransitStops(Seq((1l, State)))
    val importer = importerWithService(mockMassTransitStopService, mockVVHClient)
    val assetFields = Map("Valtakunnallinen ID" -> 1, "Pysäkin nimi" -> "NewName")

    val csv = massTransitStopImporter.createCSV(assetFields)
    val inputStream = new ByteArrayInputStream(csv.getBytes)
    val result = importer.processing(inputStream, testUser, roadTypeLimitations = Set(Municipality))

    result should equal(importer.ImportResultMassTransitStop(
      excludedRows = List(ExcludedRow(affectedRows = "State", csvRow = importer.rowToString(massTransitStopImporter.defaultValues ++ assetFields)))))

    verify(mockMassTransitStopService, never).updateExistingById(anyLong(), anyObject(), anyObject(), anyString(), anyObject())
  }

  test("update asset on street when import is limited to streets") {
    val (mockMassTransitStopService, mockVVHClient) = mockWithMassTransitStops(Seq((1l, Municipality)))
    val importer = importerWithService(mockMassTransitStopService, mockVVHClient)

    val csv = massTransitStopImporter.createCSV(Map("Valtakunnallinen ID" -> 1, "Pysäkin nimi" -> "NewName"))
    val inputStream = new ByteArrayInputStream(csv.getBytes)
    val result = importer.processing(inputStream, testUser, roadTypeLimitations = Set(Municipality))
    result should equal(importer.ImportResultMassTransitStop())

    val properties = Set(SimpleProperty("nimi_suomeksi", Seq(PropertyValue("NewName"))))
    verify(mockMassTransitStopService).updateExistingById(ArgumentMatchers.eq(1l), ArgumentMatchers.eq(None), ArgumentMatchers.eq(properties), ArgumentMatchers.eq("CsvDataImportApiSpec"), anyObject())
  }

  test("update asset on roads and streets when import is limited to roads and streets") {
    val (mockMassTransitStopService, mockVVHClient) = mockWithMassTransitStops(Seq((1l, Municipality), (2l, State)))
    val importer = importerWithService(mockMassTransitStopService, mockVVHClient)

    val csv = massTransitStopImporter.createCSV(Map("Valtakunnallinen ID" -> 1, "Pysäkin nimi" -> "NewName1"), Map("Valtakunnallinen ID" -> 2, "Pysäkin nimi" -> "NewName2"))
    val inputStream = new ByteArrayInputStream(csv.getBytes)
    val result = importer.processing(inputStream, testUser, roadTypeLimitations = Set(State, Municipality))
    result should equal(importer.ImportResultMassTransitStop())

    val properties1 = Set(SimpleProperty("nimi_suomeksi", Seq(PropertyValue("NewName1"))))
    val properties2 = Set(SimpleProperty("nimi_suomeksi", Seq(PropertyValue("NewName2"))))
    verify(mockMassTransitStopService).updateExistingById(ArgumentMatchers.eq(1l), ArgumentMatchers.eq(None), ArgumentMatchers.eq(properties1), ArgumentMatchers.eq("CsvDataImportApiSpec"), anyObject())
    verify(mockMassTransitStopService).updateExistingById(ArgumentMatchers.eq(2l), ArgumentMatchers.eq(None), ArgumentMatchers.eq(properties2), ArgumentMatchers.eq("CsvDataImportApiSpec"), anyObject())
  }

  test("ignore updates on all other road types than private roads when import is limited to private roads") {
    val (mockMassTransitStopService, mockVVHClient) = mockWithMassTransitStops(Seq((1l, Municipality), (2l, State)))
    val importer = importerWithService(mockMassTransitStopService, mockVVHClient)

    val assetOnStreetFields = Map("Valtakunnallinen ID" -> 1, "Pysäkin nimi" -> "NewName1")
    val assetOnRoadFields = Map("Valtakunnallinen ID" -> 2, "Pysäkin nimi" -> "NewName2")
    val csv = massTransitStopImporter.createCSV(assetOnStreetFields, assetOnRoadFields)

    val inputStream = new ByteArrayInputStream(csv.getBytes)
    val result = importer.processing(inputStream, testUser, roadTypeLimitations = Set(Private))
    result should equal(importer.ImportResultMassTransitStop(
      excludedRows = List(ExcludedRow(affectedRows = "State", csvRow = massTransitStopImporter.rowToString(massTransitStopImporter.defaultValues ++ assetOnRoadFields)),
        ExcludedRow(affectedRows = "Municipality", csvRow = massTransitStopImporter.rowToString(massTransitStopImporter.defaultValues ++ assetOnStreetFields)))))

    verify(mockMassTransitStopService, never).updateExistingById(anyLong(), anyObject(), anyObject(), anyString(), anyObject())
  }

  private def csvToInputStream(csv: String): InputStream = new ByteArrayInputStream(csv.getBytes())
}
