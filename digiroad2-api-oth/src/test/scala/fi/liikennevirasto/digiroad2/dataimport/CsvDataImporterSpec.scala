package fi.liikennevirasto.digiroad2.dataimport

import java.io.{ByteArrayInputStream, InputStream}

import fi.liikennevirasto.digiroad2.dataimport.DataCsvImporter.RoadLinkCsvImporter._
import fi.liikennevirasto.digiroad2.{AuthenticatedApiSpec, VVHClient}
import fi.liikennevirasto.digiroad2.roadlinkservice.oracle.RoadLinkServiceDAO
import fi.liikennevirasto.digiroad2.user.{Configuration, User, UserProvider}
import fi.liikennevirasto.digiroad2.user.oracle.OracleUserProvider
import fi.liikennevirasto.digiroad2.util.TestTransactions
import org.scalatest.mock.MockitoSugar
import org.scalatest.{BeforeAndAfter, Tag}

class CsvDataImporterSpec extends AuthenticatedApiSpec with BeforeAndAfter {
  val MunicipalityKauniainen = 235
  val testUserProvider = new OracleUserProvider
  val roadLinkCsvImporter = importerWithNullService()

  private def importerWithService(testVVHClient: VVHClient) : RoadLinkCsvImporter = {
    new RoadLinkCsvImporter {
      override val userProvider: UserProvider = testUserProvider
      override val vvhClient: VVHClient = testVVHClient
    }
  }
  private def importerWithNullService() : RoadLinkCsvImporter = {
    new RoadLinkCsvImporter {
      override val userProvider: UserProvider = testUserProvider
      override val vvhClient: VVHClient = MockitoSugar.mock[VVHClient]
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

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  test("validation fails if field type \"Linkin ID\" is not filled", Tag("db")) {
    val roadLinkFields = Map("Tien nimi (suomi)" -> "nimi", "Liikennevirran suunta" -> "5")
    val invalidCsv = csvToInputStream(createCSV(roadLinkFields))
    roadLinkCsvImporter.importLinkAttribute(invalidCsv) should equal(ImportResult(
      malformedLinks = List(MalformedLink(
        malformedParameters = List("Linkin ID"),
        csvRow = roadLinkCsvImporter.rowToString(defaultValues ++ roadLinkFields)))))
  }

  test("validation fails if type contains illegal characters", Tag("db")) {
    val assetFields = Map("Linkin ID" -> 1, "Liikennevirran suunta" -> "a")
    val invalidCsv = csvToInputStream(createCSV(assetFields))
    roadLinkCsvImporter.importLinkAttribute(invalidCsv) should equal(ImportResult(
      malformedLinks = List(MalformedLink(
        malformedParameters = List("Liikennevirran suunta"),
        csvRow = roadLinkCsvImporter.rowToString(defaultValues ++ assetFields)))))
  }

  test("validation fails if type contains administrative class = 1", Tag("db")) {
    val assetFields = Map("Linkin ID" -> 1, "Hallinnollinen luokka" -> 1)
    val invalidCsv = csvToInputStream(createCSV(assetFields))
    roadLinkCsvImporter.importLinkAttribute(invalidCsv) should equal(ImportResult(
      excludedLinks = List(ExcludedLink(affectedRoadLinkType = "State", csvRow = roadLinkCsvImporter.rowToString(defaultValues ++ assetFields)))))
  }

  test("update functionalClass by CSV import", Tag("db")) {
    runWithRollback {
      val link_id = 1000
      val functionalClassValue = 3
      RoadLinkServiceDAO.insertFunctionalClass(link_id, Some("unit_test"), 2)

      val csv = csvToInputStream(createCSV(Map("Linkin ID" -> link_id, "Toiminnallinen luokka" -> functionalClassValue)))
      roadLinkCsvImporter.importLinkAttribute(csv) should equal(ImportResult())
      RoadLinkServiceDAO.getFunctionalClassValue(link_id) should equal (Some(functionalClassValue))
    }
  }

  test("insert functionalClass by CSV import", Tag("db")) {
    runWithRollback {
      val link_id = 1000
      val functionalClassValue = 3

      val csv = csvToInputStream(createCSV(Map("Linkin ID" -> link_id, "Toiminnallinen luokka" -> functionalClassValue)))
      roadLinkCsvImporter.importLinkAttribute(csv) should equal(ImportResult())
      RoadLinkServiceDAO.getFunctionalClassValue(link_id) should equal (Some(functionalClassValue))
    }
  }

  test("update linkType by CSV import", Tag("db")) {
    runWithRollback {
      val link_id = 1000
      val linkTypeValue = 3
      RoadLinkServiceDAO.insertLinkType(link_id, Some("unit_test"), 2)

      val csv = csvToInputStream(createCSV(Map("Linkin ID" -> link_id, "Tielinkin tyyppi" ->linkTypeValue)))
      roadLinkCsvImporter.importLinkAttribute(csv) should equal(ImportResult())
      RoadLinkServiceDAO.getLinkTypeValue(link_id) should equal (Some(linkTypeValue))
    }
  }

  test("insert linkType by CSV import", Tag("db")) {
    runWithRollback {
      val link_id = 1000
      val linkTypeValue = 3

      val csv = csvToInputStream(createCSV(Map("Linkin ID" -> link_id, "Tielinkin tyyppi" -> linkTypeValue)))
      roadLinkCsvImporter.importLinkAttribute(csv) should equal(ImportResult())
      RoadLinkServiceDAO.getLinkTypeValue(link_id) should equal (Some(linkTypeValue))
    }
  }

  test("delete trafficDirection (when already exist in db) by CSV import", Tag("db")) {
    runWithRollback {
      val link_id = 1611388
      RoadLinkServiceDAO.insertTrafficDirection(link_id, Some("unit_test"), 1)
      val csv = csvToInputStream(createCSV(Map("Linkin ID" -> link_id, "Liikennevirran suunta" -> 3)))

      roadLinkCsvImporter.importLinkAttribute(csv) should equal(ImportResult())
      RoadLinkServiceDAO.getTrafficDirectionValue(link_id) should equal (None)
    }
  }

  test("update OTH and VVH by CSV import", Tag("db")) {
    runWithRollback {
      val link_id = 1000
      val linkTypeValue = 3
      RoadLinkServiceDAO.insertLinkType(link_id, Some("unit_test"), 2)

      val csv = csvToInputStream(createCSV(Map("Linkin ID" -> link_id, "Tielinkin tyyppi" ->linkTypeValue, "Kuntanumero" -> 2,
        "Liikennevirran suunta" -> 1, "Hallinnollinen luokka" -> 2)))
      roadLinkCsvImporter.importLinkAttribute(csv) should equal(ImportResult())
      RoadLinkServiceDAO.getLinkTypeValue(link_id) should equal (Some(linkTypeValue))
    }
  }

  private def csvToInputStream(csv: String): InputStream = new ByteArrayInputStream(csv.getBytes())
}
