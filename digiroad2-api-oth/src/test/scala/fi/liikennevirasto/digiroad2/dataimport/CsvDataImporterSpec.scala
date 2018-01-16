package fi.liikennevirasto.digiroad2.dataimport

import java.io.{ByteArrayInputStream, InputStream}
import javax.sql.DataSource

import fi.liikennevirasto.digiroad2.asset.{Municipality, State, TrafficDirection}
import fi.liikennevirasto.digiroad2.dataimport.DataCsvImporter.RoadLinkCsvImporter._
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.client.vvh.{FeatureClass, VVHClient, VVHComplementaryClient, VVHRoadlink}
import fi.liikennevirasto.digiroad2.dao.{OracleUserProvider, RoadLinkServiceDAO}
import fi.liikennevirasto.digiroad2.user.{Configuration, User, UserProvider}
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{BeforeAndAfter, Tag}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import slick.driver.JdbcDriver.backend.Database
import slick.driver.JdbcDriver.backend.Database.dynamicSession

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
  val testUserProvider = new OracleUserProvider
  val roadLinkCsvImporter = importerWithNullService()
  val trafficSignCsvImporter : TrafficSignCsvImporter = new TrafficSignCsvImporter

  private def importerWithNullService() : RoadLinkCsvImporter = {
    new RoadLinkCsvImporter {
      override lazy val vvhClient: VVHClient = MockitoSugar.mock[VVHClient]
      override def withDynTransaction[T](f: => T): T = f
    }
  }

  before {
    testUserProvider.setCurrentUser(User(id = 1, username = "CsvDataImportApiSpec", configuration = Configuration(authorizedMunicipalities = Set(MunicipalityKauniainen))))
  }

  test("rowToString works correctly for few basic fields") {
    roadLinkCsvImporter.rowToString(Map(
      "Hallinnollinen luokka" -> "Hallinnollinen",
      "Toiminnallinen luokka" -> "Toiminnallinen"
    )) should equal("Hallinnollinen luokka: 'Hallinnollinen', Toiminnallinen luokka: 'Toiminnallinen'")
  }

  val defaultKeys = "Linkin ID" :: roadLinkCsvImporter.mappings.keys.toList

  val defaultValues = defaultKeys.map { key => key -> "" }.toMap

  private def createCSV(assets: Map[String, Any]*): String = {
    val headers = defaultKeys.mkString(";") + "\n"
    val rows = assets.map { asset =>
      defaultKeys.map { key => asset.getOrElse(key, "") }.mkString(";")
    }.mkString("\n")
    headers + rows
  }

  private def createCsvForTrafficSigns(assets: Map[String, Any]*): String = {
    val headers = trafficSignCsvImporter.mappings.keys.toList.mkString(";") + "\n"
    val rows = assets.map { asset =>
      trafficSignCsvImporter.mappings.keys.toList.map { key => asset.getOrElse(key, "") }.mkString(";")
    }.mkString("\n")
    headers + rows
  }

  def runWithRollback(test: => Unit): Unit = sTestTransactions.runWithRollback(roadLinkCsvImporter.dataSource)(test)

  test("validation fails if field type \"Linkin ID\" is not filled", Tag("db")) {
    val roadLinkFields = Map("Tien nimi (suomi)" -> "nimi", "Liikennevirran suunta" -> "5")
    val invalidCsv = csvToInputStream(createCSV(roadLinkFields))
    roadLinkCsvImporter.importLinkAttribute(invalidCsv) should equal(roadLinkCsvImporter.ImportResult(
      malformedLinks = List(roadLinkCsvImporter.MalformedLink(
        malformedParameters = List("Linkin ID"),
        csvRow = roadLinkCsvImporter.rowToString(defaultValues ++ roadLinkFields)))))
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
    val invalidCsv = csvToInputStream(createCSV(assetFields))
    roadLinkCsvImporter.importLinkAttribute(invalidCsv) should equal(roadLinkCsvImporter.ImportResult(
      malformedLinks = List(roadLinkCsvImporter.MalformedLink(
        malformedParameters = List("Liikennevirran suunta"),
        csvRow = roadLinkCsvImporter.rowToString(defaultValues ++ assetFields)))))
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
    val invalidCsv = csvToInputStream(createCSV(assetFields))
    roadLinkCsvImporter.importLinkAttribute(invalidCsv) should equal(roadLinkCsvImporter.ImportResult(
      excludedLinks = List(roadLinkCsvImporter.ExcludedLink(unauthorizedAdminClass = List("AdminClass value State found on  VVH"), csvRow = roadLinkCsvImporter.rowToString(defaultValues ++ assetFields)))))
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
    val invalidCsv = csvToInputStream(createCSV(assetFields))
    roadLinkCsvImporter.importLinkAttribute(invalidCsv) should equal(roadLinkCsvImporter.ImportResult(
      excludedLinks = List(roadLinkCsvImporter.ExcludedLink(unauthorizedAdminClass = List("AdminClass value State found on  CSV"), csvRow = roadLinkCsvImporter.rowToString(defaultValues ++ assetFields)))))
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
    val invalidCsv = csvToInputStream(createCSV(assetFields))
    roadLinkCsvImporter.importLinkAttribute(invalidCsv) should equal(roadLinkCsvImporter.ImportResult(
      excludedLinks = List(roadLinkCsvImporter.ExcludedLink(unauthorizedAdminClass = List("AdminClass value State found on  VVH", "AdminClass value State found on  CSV"), csvRow = roadLinkCsvImporter.rowToString(defaultValues ++ assetFields)))))
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
      RoadLinkServiceDAO.insertFunctionalClass(link_id, Some("unit_test"), 2)

      val csv = csvToInputStream(createCSV(Map("Linkin ID" -> link_id, "Toiminnallinen luokka" -> functionalClassValue)))
      roadLinkCsvImporter.importLinkAttribute(csv) should equal(roadLinkCsvImporter.ImportResult())
      RoadLinkServiceDAO.getFunctionalClassValue(link_id) should equal (Some(functionalClassValue))
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

      val csv = csvToInputStream(createCSV(Map("Linkin ID" -> link_id, "Toiminnallinen luokka" -> functionalClassValue)))
      roadLinkCsvImporter.importLinkAttribute(csv) should equal(roadLinkCsvImporter.ImportResult())
      RoadLinkServiceDAO.getFunctionalClassValue(link_id) should equal (Some(functionalClassValue))
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
      RoadLinkServiceDAO.insertLinkType(link_id, Some("unit_test"), 2)

      val csv = csvToInputStream(createCSV(Map("Linkin ID" -> link_id, "Tielinkin tyyppi" ->linkTypeValue)))
      roadLinkCsvImporter.importLinkAttribute(csv) should equal(roadLinkCsvImporter.ImportResult())
      RoadLinkServiceDAO.getLinkTypeValue(link_id) should equal (Some(linkTypeValue))
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

      val csv = csvToInputStream(createCSV(Map("Linkin ID" -> link_id, "Tielinkin tyyppi" -> linkTypeValue)))
      roadLinkCsvImporter.importLinkAttribute(csv) should equal(roadLinkCsvImporter.ImportResult())
      RoadLinkServiceDAO.getLinkTypeValue(link_id) should equal (Some(linkTypeValue))
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
      RoadLinkServiceDAO.insertTrafficDirection(link_id, Some("unit_test"), 1)
      val csv = csvToInputStream(createCSV(Map("Linkin ID" -> link_id, "Liikennevirran suunta" -> 3)))

      roadLinkCsvImporter.importLinkAttribute(csv) should equal(roadLinkCsvImporter.ImportResult())
      RoadLinkServiceDAO.getTrafficDirectionValue(link_id) should equal (None)
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
      RoadLinkServiceDAO.insertLinkType(link_id, Some("unit_test"), 2)

      val csv = csvToInputStream(createCSV(Map("Linkin ID" -> link_id, "Tielinkin tyyppi" -> linkTypeValue, "Kuntanumero" -> 2,
        "Liikennevirran suunta" -> 1, "Hallinnollinen luokka" -> 2)))
      roadLinkCsvImporter.importLinkAttribute(csv) should equal(roadLinkCsvImporter.ImportResult())
      RoadLinkServiceDAO.getLinkTypeValue(link_id) should equal(Some(linkTypeValue))
    }
  }

  test("validation for traffic sign import fails if type contains illegal characters", Tag("db")) {
    val assetFields = Map("koordinaatti x" -> 1, "koordinaatti y" -> 1, "liikennemerkin tyyppi" -> "a")
    val invalidCsv = csvToInputStream(createCsvForTrafficSigns(assetFields))
    val defaultValues = trafficSignCsvImporter.mappings.keys.toList.map { key => key -> "" }.toMap

    trafficSignCsvImporter.importTrafficSigns(invalidCsv) should equal(trafficSignCsvImporter.ImportResult(
      malformedAssets = List(trafficSignCsvImporter.MalformedAsset(
        malformedParameters = List("liikennemerkin tyyppi"),
        csvRow = trafficSignCsvImporter.rowToString(defaultValues ++ assetFields)))))
  }

  test("validation for traffic sign import fails if mandatory parameters are missing", Tag("db")) {
    val assetFields = Map("koordinaatti x" -> "", "koordinaatti y" -> "", "liikennemerkin tyyppi" -> "")
    val invalidCsv = csvToInputStream(createCsvForTrafficSigns(assetFields))
    val defaultValues = trafficSignCsvImporter.mappings.keys.toList.map { key => key -> "" }.toMap
    val csvRow = trafficSignCsvImporter.rowToString(defaultValues ++ assetFields)
    val assets = trafficSignCsvImporter.importTrafficSigns(invalidCsv)

    assets.malformedAssets.flatMap(_.malformedParameters) should contain allOf ("koordinaatti x", "koordinaatti y", "liikennemerkin tyyppi")
    assets.malformedAssets.foreach {
      asset =>
        asset.csvRow should be (trafficSignCsvImporter.rowToString(defaultValues ++ assetFields))
    }
  }

  private def csvToInputStream(csv: String): InputStream = new ByteArrayInputStream(csv.getBytes())
}
