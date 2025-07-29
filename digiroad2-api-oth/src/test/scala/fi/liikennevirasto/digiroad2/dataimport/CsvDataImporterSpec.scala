package fi.liikennevirasto.digiroad2.dataimport

import fi.liikennevirasto.digiroad2.Digiroad2Context.userProvider
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.kgv.{KgvMunicipalityBorderClient, MunicipalityBorders}
import fi.liikennevirasto.digiroad2.client.{FeatureClass, RoadLinkClient, RoadLinkFetched}
import fi.liikennevirasto.digiroad2.csvDataImporter.{RoadLinkCsvImporter, TrafficLightsCsvImporter, TrafficSignCsvImporter}
import fi.liikennevirasto.digiroad2.dao.{ComplementaryLinkDAO, RoadLinkOverrideDAO}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.middleware.UpdateOnlyStartDates
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.user.{Configuration, User}
import fi.liikennevirasto.digiroad2.util.{GeometryTransform, LinkIdGenerator}
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfter, Tag}
import slick.driver.JdbcDriver.backend.Database
import slick.driver.JdbcDriver.backend.Database.dynamicSession

import java.io.{ByteArrayInputStream, InputStream}
import javax.sql.DataSource


object sTestTransactions {
  def runWithRollback(ds: DataSource = PostGISDatabase.ds)(f: => Unit): Unit = {
    Database.forDataSource(ds).withDynTransaction {
      f
      dynamicSession.rollback()
    }
  }
  def withDynTransaction[T](ds: DataSource = PostGISDatabase.ds)(f: => T): T = {
    Database.forDataSource(ds).withDynTransaction {
      f
    }
  }
  def withDynSession[T](ds: DataSource = PostGISDatabase.ds)(f: => T): T = {
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
  private val mockComplementaryLinkDAO = MockitoSugar.mock[ComplementaryLinkDAO]
  private val mockMunicipalityBorderClient = MockitoSugar.mock[KgvMunicipalityBorderClient]

  val (linkId1, linkId2) = (LinkIdGenerator.generateRandom(), LinkIdGenerator.generateRandom())
  val roadLinkFetcheds = Seq(RoadLinkFetched(linkId1, 235, Seq(Point(2, 2), Point(4, 4)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers))
  val roadLink = Seq(RoadLink(linkId2, Seq(Point(2, 2), Point(4, 4)), 3.5, Municipality, 1, TrafficDirection.BothDirections, Motorway,  None, None, Map("MUNICIPALITYCODE" -> BigInt(408))))

  val updateOnlyStartDatesFalse: UpdateOnlyStartDates = UpdateOnlyStartDates(false)

  when(mockRoadLinkService.getClosestRoadlinkForCarTraffic(any[User], any[Point], any[Boolean], any[Boolean])).thenReturn(roadLink)
  when(mockRoadLinkService.enrichFetchedRoadLinks(any[Seq[RoadLinkFetched]], any[Boolean])).thenReturn(roadLink)

  def runWithRollback(test: => Unit): Unit = sTestTransactions.runWithRollback()(test)

  object trafficSignCsvImporter extends TrafficSignCsvImporter(mockRoadLinkService, mockEventBus) {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def eventBus: DigiroadEventBus = mockEventBus
  }

  object trafficLightsCsvImporter extends TrafficLightsCsvImporter(mockRoadLinkService, mockEventBus) {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def eventBus: DigiroadEventBus = mockEventBus
    override val municipalityBorderClient: KgvMunicipalityBorderClient = mockMunicipalityBorderClient
  }

  object roadLinkCsvImporter extends RoadLinkCsvImporter(mockRoadLinkService, mockEventBus) {
    override lazy val roadLinkClient: RoadLinkClient = MockitoSugar.mock[RoadLinkClient]
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

  private def createCsvForTrafficLights(assets: Map[String, Any]*): String = {
    val headers = trafficLightsCsvImporter.mappings.keys.toList.mkString(";") + "\n"
    val rows = assets.map { asset =>
      trafficLightsCsvImporter.mappings.keys.toList.map { key => asset.getOrElse(key, "") }.mkString(";")
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
    val newLinkId1 = LinkIdGenerator.generateRandom()
    val municipalityCode = 564
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val attributes1 = Map("OBJECTID" -> BigInt(99))

    val newRoadLink1 = RoadLinkFetched(newLinkId1, municipalityCode, List(Point(0.0, 0.0), Point(20.0, 0.0)), administrativeClass, trafficDirection, FeatureClass.DrivePath, None, attributes1)
    
    when(mockComplementaryLinkDAO.fetchByLinkId(any[String])).thenReturn(Some(newRoadLink1))

    val assetFields = Map("linkin id" -> 1, "liikennevirran suunta" -> "a")
    val invalidCsv = csvToInputStream(roadLinkCsvImporter.createCSV(assetFields))
    roadLinkCsvImporter.processing(invalidCsv, testUser.username) should equal(roadLinkCsvImporter.ImportResultRoadLink(
      malformedRows = List(MalformedRow(
        malformedParameters = List("liikennevirran suunta"),
        csvRow = roadLinkCsvImporter.rowToString(roadLinkCsvImporter.defaultValues ++ assetFields)))))
  }

  test("validation fails if administrative class is 1 on database", Tag("db")) {
    val newLinkId1 = LinkIdGenerator.generateRandom()
    val municipalityCode = 564
    val administrativeClass = State
    val trafficDirection = TrafficDirection.BothDirections
    val attributes1 = Map("OBJECTID" -> BigInt(99))

    val newRoadLink1 = RoadLinkFetched(newLinkId1, municipalityCode, List(Point(0.0, 0.0), Point(20.0, 0.0)), administrativeClass, trafficDirection, FeatureClass.DrivePath, None, attributes1)
    
    when(mockRoadLinkService.fetchComplimentaryByLinkId(any[String])).thenReturn(Some(newRoadLink1))

    val assetFields = Map("linkin id" -> 1, "hallinnollinen luokka" -> 2)
    val invalidCsv = csvToInputStream(roadLinkCsvImporter.createCSV(assetFields))
    roadLinkCsvImporter.processing(invalidCsv, testUser.username) should equal(roadLinkCsvImporter.ImportResultRoadLink(
      excludedRows = List(ExcludedRow(affectedRows = "AdminClass value State found on Database", csvRow = roadLinkCsvImporter.rowToString(roadLinkCsvImporter.defaultValues ++ assetFields)))))
  }

  test("validation fails if administrative class = 1 on CSV", Tag("db")) {
    val newLinkId1 = LinkIdGenerator.generateRandom()
    val municipalityCode = 564
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val attributes1 = Map("OBJECTID" -> BigInt(99))

    val newRoadLink1 = RoadLinkFetched(newLinkId1, municipalityCode, List(Point(0.0, 0.0), Point(20.0, 0.0)), administrativeClass, trafficDirection, FeatureClass.DrivePath, None, attributes1)
    when(mockRoadLinkService.fetchComplimentaryByLinkId(any[String])).thenReturn(Some(newRoadLink1))

    val assetFields = Map("linkin id" -> 1, "hallinnollinen luokka" -> 1)
    val invalidCsv = csvToInputStream(roadLinkCsvImporter.createCSV(assetFields))
    roadLinkCsvImporter.processing(invalidCsv, testUser.username) should equal(roadLinkCsvImporter.ImportResultRoadLink(
      excludedRows = List(ExcludedRow(affectedRows = "AdminClass value State found on CSV", csvRow = roadLinkCsvImporter.rowToString(roadLinkCsvImporter.defaultValues ++ assetFields)))))
  }

  test("validation fails if administrative class = 1 on CSV and database", Tag("db")) {
    val newLinkId1 = LinkIdGenerator.generateRandom()
    val municipalityCode = 564
    val administrativeClass = State
    val trafficDirection = TrafficDirection.BothDirections
    val attributes1 = Map("OBJECTID" -> BigInt(99))

    val newRoadLink1 = RoadLinkFetched(newLinkId1, municipalityCode, List(Point(0.0, 0.0), Point(20.0, 0.0)), administrativeClass, trafficDirection, FeatureClass.DrivePath, None, attributes1)
    
    when(mockRoadLinkService.fetchComplimentaryByLinkId(any[String])).thenReturn(Some(newRoadLink1))

    val assetFields = Map("linkin id" -> 1, "hallinnollinen luokka" -> 1)
    val invalidCsv = csvToInputStream(roadLinkCsvImporter.createCSV(assetFields))
    roadLinkCsvImporter.processing(invalidCsv, testUser.username) should equal(roadLinkCsvImporter.ImportResultRoadLink(
      excludedRows = List(ExcludedRow(affectedRows = "AdminClass value State found on Database/AdminClass value State found on CSV", csvRow = roadLinkCsvImporter.rowToString(roadLinkCsvImporter.defaultValues ++ assetFields)))))
  }

  test("update functionalClass by CSV import", Tag("db")) {
    val newLinkId1 = LinkIdGenerator.generateRandom()
    val municipalityCode = 564
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val attributes1 = Map("OBJECTID" -> BigInt(99))

    val newRoadLink1 = RoadLinkFetched(newLinkId1, municipalityCode, List(Point(0.0, 0.0), Point(20.0, 0.0)), administrativeClass, trafficDirection, FeatureClass.DrivePath, None, attributes1)
    
    runWithRollback {
      when(mockRoadLinkService.fetchComplimentaryByLinkId(any[String])).thenReturn(Some(newRoadLink1))

      val link_id = LinkIdGenerator.generateRandom()
      val functionalClassValue = 3
      RoadLinkOverrideDAO.insert(RoadLinkOverrideDAO.FunctionalClass, link_id, Some("unit_test"), 2)

      val csv = csvToInputStream(roadLinkCsvImporter.createCSV(Map("linkin id" -> link_id, "toiminnallinen luokka" -> functionalClassValue)))
      roadLinkCsvImporter.processing(csv, testUser.username) should equal(roadLinkCsvImporter.ImportResultRoadLink())
      RoadLinkOverrideDAO.get(RoadLinkOverrideDAO.FunctionalClass, link_id) should equal (Some(functionalClassValue))
    }
  }

  test("insert functionalClass by CSV import", Tag("db")) {
    val newLinkId1 = LinkIdGenerator.generateRandom()
    val municipalityCode = 564
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val attributes1 = Map("OBJECTID" -> BigInt(99))

    val newRoadLink1 = RoadLinkFetched(newLinkId1, municipalityCode, List(Point(0.0, 0.0), Point(20.0, 0.0)), administrativeClass, trafficDirection, FeatureClass.DrivePath, None, attributes1)

    runWithRollback {
      when(mockRoadLinkService.fetchComplimentaryByLinkId(any[String])).thenReturn(Some(newRoadLink1))

      val link_id = LinkIdGenerator.generateRandom()
      val functionalClassValue = 3

      val csv = csvToInputStream(roadLinkCsvImporter.createCSV(Map("linkin id" -> link_id, "toiminnallinen luokka" -> functionalClassValue)))
      roadLinkCsvImporter.processing(csv, testUser.username) should equal(roadLinkCsvImporter.ImportResultRoadLink())
      RoadLinkOverrideDAO.get(RoadLinkOverrideDAO.FunctionalClass, link_id) should equal (Some(functionalClassValue))
    }
  }

  test("update linkType by CSV import", Tag("db")) {
    val newLinkId1 = LinkIdGenerator.generateRandom()
    val municipalityCode = 564
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val attributes1 = Map("OBJECTID" -> BigInt(99))

    val newRoadLink1 = RoadLinkFetched(newLinkId1, municipalityCode, List(Point(0.0, 0.0), Point(20.0, 0.0)), administrativeClass, trafficDirection, FeatureClass.DrivePath, None, attributes1)

    runWithRollback {
      when(mockRoadLinkService.fetchComplimentaryByLinkId(any[String])).thenReturn(Some(newRoadLink1))
      val link_id = LinkIdGenerator.generateRandom()
      val linkTypeValue = 3
      RoadLinkOverrideDAO.insert(RoadLinkOverrideDAO.LinkType, link_id, Some("unit_test"), 2)

      val csv = csvToInputStream(roadLinkCsvImporter.createCSV(Map("linkin id" -> link_id, "tielinkin tyyppi" ->linkTypeValue)))
      roadLinkCsvImporter.processing(csv, testUser.username) should equal(roadLinkCsvImporter.ImportResultRoadLink())
      RoadLinkOverrideDAO.get(RoadLinkOverrideDAO.LinkType, link_id) should equal (Some(linkTypeValue))
    }
  }

  test("insert linkType by CSV import", Tag("db")) {
    val newLinkId1 = LinkIdGenerator.generateRandom()
    val municipalityCode = 564
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val attributes1 = Map("OBJECTID" -> BigInt(99))

    val newRoadLink1 = RoadLinkFetched(newLinkId1, municipalityCode, List(Point(0.0, 0.0), Point(20.0, 0.0)), administrativeClass, trafficDirection, FeatureClass.DrivePath, None, attributes1)

    runWithRollback {
      when(mockRoadLinkService.fetchComplimentaryByLinkId(any[String])).thenReturn(Some(newRoadLink1))
      val link_id = LinkIdGenerator.generateRandom()
      val linkTypeValue = 3

      val csv = csvToInputStream(roadLinkCsvImporter.createCSV(Map("linkin id" -> link_id, "tielinkin tyyppi" -> linkTypeValue)))
      roadLinkCsvImporter.processing(csv, testUser.username) should equal(roadLinkCsvImporter.ImportResultRoadLink())
      RoadLinkOverrideDAO.get(RoadLinkOverrideDAO.LinkType, link_id) should equal (Some(linkTypeValue))
    }
  }

  test("delete trafficDirection (when already exist in db) by CSV import", Tag("db")) {
    val newLinkId1 = LinkIdGenerator.generateRandom()
    val municipalityCode = 564
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val attributes1 = Map("OBJECTID" -> BigInt(99))

    val newRoadLink1 = RoadLinkFetched(newLinkId1, municipalityCode, List(Point(0.0, 0.0), Point(20.0, 0.0)), administrativeClass, trafficDirection, FeatureClass.DrivePath, None, attributes1)
    
    runWithRollback {
      when(mockRoadLinkService.fetchComplimentaryByLinkId(any[String])).thenReturn(Some(newRoadLink1))
      val link_id = "4d146477-876b-4ab5-ad11-f29d16a9b300:1"
      RoadLinkOverrideDAO.insert(RoadLinkOverrideDAO.TrafficDirection, link_id, Some("unit_test"), 1)
      val csv = csvToInputStream(roadLinkCsvImporter.createCSV(Map("linkin id" -> link_id, "liikennevirran suunta" -> 3)))

      roadLinkCsvImporter.processing(csv, testUser.username) should equal(roadLinkCsvImporter.ImportResultRoadLink())
      RoadLinkOverrideDAO.get(RoadLinkOverrideDAO.TrafficDirection, link_id) should equal (None)
    }
  }

  test("update OTH by CSV import", Tag("db")) {
    val newLinkId1 = LinkIdGenerator.generateRandom()
    val municipalityCode = 564
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val attributes1 = Map("OBJECTID" -> BigInt(99))

    val newRoadLink1 = RoadLinkFetched(newLinkId1, municipalityCode, List(Point(0.0, 0.0), Point(20.0, 0.0)), administrativeClass, trafficDirection, FeatureClass.DrivePath, None, attributes1)
    
    runWithRollback {
      when(mockRoadLinkService.fetchComplimentaryByLinkId(any[String])).thenReturn(Some(newRoadLink1))
      val link_id = LinkIdGenerator.generateRandom()
      val linkTypeValue = 3
      RoadLinkOverrideDAO.insert(RoadLinkOverrideDAO.LinkType, link_id, Some("unit_test"), 2)

      val csv = csvToInputStream(roadLinkCsvImporter.createCSV(Map("linkin id" -> link_id, "tielinkin tyyppi" -> linkTypeValue, "kuntanumero" -> 2,
        "liikennevirran suunta" -> 1, "hallinnollinen luokka" -> 2)))
      roadLinkCsvImporter.processing(csv, testUser.username) should equal(roadLinkCsvImporter.ImportResultRoadLink())
      RoadLinkOverrideDAO.get(RoadLinkOverrideDAO.LinkType, link_id) should equal(Some(linkTypeValue))
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
        asset.csvRow should be (csvRow)
    }
  }

  test("validation for traffic sign import fails if user try to create in an authorize municipality", Tag("db")) {
    val assetFields = Map("koordinaatti x" -> 1, "koordinaatti y" -> 1, "liikennemerkin tyyppi" -> "F37", "suuntima" -> "40")
    val invalidCsv = csvToInputStream(createCsvForTrafficSigns(assetFields))
    val defaultValues = trafficSignCsvImporter.mappings.keys.toList.map { key => key -> "" }.toMap
    when(mockRoadLinkService.getClosestRoadlinkForCarTraffic(any[User], any[Point], any[Boolean], any[Boolean])).thenReturn(Seq())

    trafficSignCsvImporter.processing(invalidCsv, Set(), testUser) should equal(trafficSignCsvImporter.ImportResultPointAsset(
      notImportedData = List(trafficSignCsvImporter.NotImportedData(
        reason = "No Rights for Municipality or nonexistent road links near asset position",
        csvRow = trafficSignCsvImporter.rowToString(defaultValues ++ assetFields)))))
  }

  test("validation for traffic sign import with sign roadwork and dates", Tag("db")) {
    val assetFields = Map("koordinaatti x" -> 1, "koordinaatti y" -> 1, "liikennemerkin tyyppi" -> "A11", "suuntima" -> "40",
      "alkupaivamaara" -> "03.08.2020", "loppupaivamaara" -> "07.08.2020", "kunnan id" -> "408")

    val validCsv = csvToInputStream(createCsvForTrafficSigns(assetFields))

    runWithRollback {
      when(mockRoadLinkService.getClosestRoadlinkForCarTraffic(any[User], any[Point], any[Boolean], any[Boolean])).thenReturn(roadLink)
      when(mockRoadLinkService.filterRoadLinkByBearing(any[Option[Int]], any[Option[Int]], any[Point], any[Seq[RoadLink]])).thenReturn(roadLink)

      val result = trafficSignCsvImporter.processing(validCsv, Set(), testUser)

      result.incompleteRows.size should be(0)
      result.malformedRows.size should be(0)
      result.excludedRows.size should be(0)
      result.notImportedData.size should be(0)
      result.createdData.size should be(1)

      result.createdData.head.properties.find(_.columnName == "startDate").get.value should be("03.08.2020")
      result.createdData.head.properties.find(_.columnName == "endDate").get.value should be("07.08.2020")
    }
  }

  test("validation for traffic sign import with only start dates", Tag("db")) {
    val assetFields = Map("koordinaatti x" -> 1, "koordinaatti y" -> 1, "liikennemerkin tyyppi" -> "A13", "suuntima" -> "40",
      "alkupaivamaara" -> "03.08.2020", "loppupaivamaara" -> "", "kunnan id" -> "408")

    val validCsv = csvToInputStream(createCsvForTrafficSigns(assetFields))

    runWithRollback {
      when(mockRoadLinkService.getClosestRoadlinkForCarTraffic(any[User], any[Point], any[Boolean], any[Boolean])).thenReturn(roadLink)
      when(mockRoadLinkService.filterRoadLinkByBearing(any[Option[Int]], any[Option[Int]], any[Point], any[Seq[RoadLink]])).thenReturn(roadLink)

      val result = trafficSignCsvImporter.processing(validCsv, Set(), testUser)

      result.incompleteRows.size should be(0)
      result.malformedRows.size should be(0)
      result.excludedRows.size should be(0)
      result.notImportedData.size should be(0)
      result.createdData.size should be(1)

      result.createdData.head.properties.find(_.columnName == "startDate").get.value should be("03.08.2020")
      result.createdData.head.properties.find(_.columnName == "endDate").get.value should be("")
    }
  }

  test("validation for traffic sign import without dates", Tag("db")) {
    val assetFields = Map("koordinaatti x" -> 1, "koordinaatti y" -> 1, "liikennemerkin tyyppi" -> "A13", "suuntima" -> "40",
      "alkupaivamaara" -> "", "loppupaivamaara" -> "", "kunnan id" -> "408")

    val validCsv = csvToInputStream(createCsvForTrafficSigns(assetFields))

    runWithRollback {
      when(mockRoadLinkService.getClosestRoadlinkForCarTraffic(any[User], any[Point], any[Boolean], any[Boolean])).thenReturn(roadLink)
      when(mockRoadLinkService.filterRoadLinkByBearing(any[Option[Int]], any[Option[Int]], any[Point], any[Seq[RoadLink]])).thenReturn(roadLink)

      val result = trafficSignCsvImporter.processing(validCsv, Set(), testUser)

      result.incompleteRows.size should be(0)
      result.malformedRows.size should be(0)
      result.excludedRows.size should be(0)
      result.notImportedData.size should be(0)
      result.createdData.size should be(1)

      result.createdData.head.properties.find(_.columnName == "startDate").get.value should be("")
      result.createdData.head.properties.find(_.columnName == "endDate").get.value should be("")
    }
  }

  test("validation for traffic sign import with arvo", Tag("db")) {
    val assetFields = Map("koordinaatti x" -> 1, "koordinaatti y" -> 1, "liikennemerkin tyyppi" -> "C32", "suuntima" -> "40",
      "arvo" -> "50", "kunnan id" -> "408")

    val validCsv = csvToInputStream(createCsvForTrafficSigns(assetFields))

    runWithRollback {
      when(mockRoadLinkService.getClosestRoadlinkForCarTraffic(any[User], any[Point], any[Boolean], any[Boolean])).thenReturn(roadLink)
      when(mockRoadLinkService.filterRoadLinkByBearing(any[Option[Int]], any[Option[Int]], any[Point], any[Seq[RoadLink]])).thenReturn(roadLink)

      val result = trafficSignCsvImporter.processing(validCsv, Set(), testUser)

      result.incompleteRows.size should be(0)
      result.malformedRows.size should be(0)
      result.excludedRows.size should be(0)
      result.notImportedData.size should be(0)
      result.createdData.size should be(1)

      result.createdData.head.properties.find( _.columnName == "value").get.value should be("50")
    }
  }

  test("validation for traffic sign import with arvo empty", Tag("db")) {
    val assetFields = Map("koordinaatti x" -> 1, "koordinaatti y" -> 1, "liikennemerkin tyyppi" -> "C32", "suuntima" -> "40",
      "arvo" -> "", "kunnan id" -> "408")

    val validCsv = csvToInputStream(createCsvForTrafficSigns(assetFields))

    runWithRollback {
      when(mockRoadLinkService.getClosestRoadlinkForCarTraffic(any[User], any[Point], any[Boolean], any[Boolean])).thenReturn(roadLink)
      when(mockRoadLinkService.filterRoadLinkByBearing(any[Option[Int]], any[Option[Int]], any[Point], any[Seq[RoadLink]])).thenReturn(roadLink)

      val result = trafficSignCsvImporter.processing(validCsv, Set(), testUser)

      result.incompleteRows.size should be(0)
      result.malformedRows.size should be(0)
      result.excludedRows.size should be(0)
      result.notImportedData.size should be(1)
      result.createdData.size should be(0)

      result.notImportedData.head.reason should equal( "Arvo field not ok.")
    }
  }

  test("Validation for traffic sign import when importing additional panel as main sign", Tag("db")) {
    val assetFields = Map("koordinaatti x" -> 1, "koordinaatti y" -> 1, "liikennemerkin tyyppi" -> "H22.2", "suuntima" -> "40",
      "arvo" -> "", "kunnan id" -> "408")

    val inValidCsv = csvToInputStream(createCsvForTrafficSigns(assetFields))

    runWithRollback {
      when(mockRoadLinkService.getClosestRoadlinkForCarTraffic(any[User], any[Point], any[Boolean], any[Boolean])).thenReturn(roadLink)
      when(mockRoadLinkService.filterRoadLinkByBearing(any[Option[Int]], any[Option[Int]], any[Point], any[Seq[RoadLink]])).thenReturn(roadLink)

      val result = trafficSignCsvImporter.processing(inValidCsv, Set(), testUser)

      result.incompleteRows.size should be(0)
      result.malformedRows.size should be(0)
      result.excludedRows.size should be(0)
      result.notImportedData.size should be(1)
      result.createdData.size should be(0)

      result.notImportedData.head.reason should equal("Invalid trafficSignType for main sign")
    }
  }


  test("validation for traffic light import fails if mandatory parameters are missing", Tag("db")) {
    when(trafficLightsCsvImporter.municipalityBorderClient.fetchAllMunicipalities()).thenReturn(Seq(MunicipalityBorders(99, List())))
    val assetFields = Map("koordinaatti x" -> "", "koordinaatti y" -> "", "liikennevalo tyyppi" -> "")
    val invalidCsv = csvToInputStream(createCsvForTrafficLights(assetFields))
    val defaultValues = trafficLightsCsvImporter.mappings.keys.toList.map { key => key -> "" }.toMap
    val assets = trafficLightsCsvImporter.processing(invalidCsv, testUser)

    assets.malformedRows.flatMap(_.malformedParameters) should contain allOf ("koordinaatti x", "koordinaatti y", "liikennevalo tyyppi")
    assets.malformedRows.foreach {
      asset =>
        asset.csvRow should be (trafficLightsCsvImporter.rowToString(defaultValues ++ assetFields))
    }
  }

  test("validation for traffic light import fails if lane number is invalid", Tag("db")) {
    when(trafficLightsCsvImporter.municipalityBorderClient.fetchAllMunicipalities()).thenReturn(Seq(MunicipalityBorders(99, List())))
    val assetFields = Map("koordinaatti x" -> 52828, "koordinaatti y" -> 58285, "liikennevalo tyyppi" -> 4.2, "kaista" -> 54)
    val invalidCsv = csvToInputStream(createCsvForTrafficLights(assetFields))
    val defaultValues = trafficLightsCsvImporter.mappings.keys.toList.map { key => key -> "" }.toMap
    val assets = trafficLightsCsvImporter.processing(invalidCsv, testUser)

    assets.notImportedData.map(_.reason).head should be ("Invalid lane")
  }

  test("validation for traffic light import fails if lane number and lane type don't match", Tag("db")) {
    when(trafficLightsCsvImporter.municipalityBorderClient.fetchAllMunicipalities()).thenReturn(Seq(MunicipalityBorders(99, List())))
    val assetFields = Map("koordinaatti x" -> 52828, "koordinaatti y" -> 58285, "liikennevalo tyyppi" -> 4.2, "kaista" -> 12, "kaistan tyyppi" -> 1)
    val invalidCsv = csvToInputStream(createCsvForTrafficLights(assetFields))
    val defaultValues = trafficLightsCsvImporter.mappings.keys.toList.map { key => key -> "" }.toMap
    val assets = trafficLightsCsvImporter.processing(invalidCsv, testUser)

    assets.notImportedData.map(_.reason).head should be ("Invalid lane and lane type match")
  }



  val mockGeometryTransform = MockitoSugar.mock[GeometryTransform]

  private def csvToInputStream(csv: String): InputStream = new ByteArrayInputStream(csv.getBytes())
}
