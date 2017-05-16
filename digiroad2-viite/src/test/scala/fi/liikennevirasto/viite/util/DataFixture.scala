package fi.liikennevirasto.viite.util

import java.util.Properties

import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.util.SqlScriptRunner
import fi.liikennevirasto.digiroad2.{DummyEventBus, DummySerializer, RoadLinkService, VVHClient}
import fi.liikennevirasto.viite.dao.RoadAddressDAO
import fi.liikennevirasto.viite.process.{ContinuityChecker, FloatingChecker, InvalidAddressDataException, LinkRoadAddressCalculator}
import fi.liikennevirasto.viite.util.AssetDataImporter.Conversion
import fi.liikennevirasto.viite.{RoadAddressLinkBuilder, RoadAddressService}
import org.joda.time.DateTime
import slick.jdbc.{StaticQuery => Q}

object DataFixture {
  val TestAssetId = 300000
  lazy val properties: Properties = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/bonecp.properties"))
    props
  }
  lazy val dr2properties: Properties = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/digiroad2.properties"))
    props
  }

  val dataImporter = new AssetDataImporter
  lazy val vvhClient: VVHClient = {
    new VVHClient(dr2properties.getProperty("digiroad2.VVHRestApiEndPoint"))
  }

  lazy val continuityChecker = new ContinuityChecker(new RoadLinkService(vvhClient, new DummyEventBus, new DummySerializer))

  private def loopRoadParts(roadNumber: Int) = {
    var partNumberOpt = RoadAddressDAO.fetchNextRoadPartNumber(roadNumber, 0)
    while (partNumberOpt.nonEmpty) {
      val partNumber = partNumberOpt.get
      val roads = RoadAddressDAO.fetchByRoadPart(roadNumber, partNumber, true)
      try {
        val adjusted = LinkRoadAddressCalculator.recalculate(roads)
        assert(adjusted.size == roads.size) // Must not lose any
        val (changed, unchanged) = adjusted.partition(ra =>
            roads.exists(oldra => ra.id == oldra.id && (oldra.startAddrMValue != ra.startAddrMValue || oldra.endAddrMValue != ra.endAddrMValue))
          )
        println(s"Road $roadNumber, part $partNumber: ${changed.size} updated, ${unchanged.size} kept unchanged")
        changed.foreach(addr => RoadAddressDAO.update(addr, None))
      } catch {
        case ex: InvalidAddressDataException => println(s"!!! Road $roadNumber, part $partNumber contains invalid address data - part skipped !!!")
          ex.printStackTrace()
      }
      partNumberOpt = RoadAddressDAO.fetchNextRoadPartNumber(roadNumber, partNumber)
    }
  }

  def recalculate():Unit = {
    OracleDatabase.withDynTransaction {
      var roadNumberOpt = RoadAddressDAO.fetchNextRoadNumber(0)
      while (roadNumberOpt.nonEmpty) {
        loopRoadParts(roadNumberOpt.get)
        roadNumberOpt = RoadAddressDAO.fetchNextRoadNumber(roadNumberOpt.get)
      }
    }
  }

  def importRoadAddresses(isDevDatabase: Boolean): Unit = {
    println(s"\nCommencing road address import from conversion at time: ${DateTime.now()}")
    val vvhClient = new VVHClient(dr2properties.getProperty("digiroad2.VVHRestApiEndPoint"))
    val vvhClientProd = if (isDevDatabase) {
      Some(new VVHClient(dr2properties.getProperty("digiroad2.VVHProdRestApiEndPoint", "http://172.17.204.39:6080/arcgis/rest/services/VVH_OTH/")))
    } else {
      None
    }
    dataImporter.importRoadAddressData(Conversion.database(), vvhClient, vvhClientProd)
    println(s"Road address import complete at time: ${DateTime.now()}")
    println()
  }

  def updateMissingRoadAddresses(): Unit = {
    println(s"\nUpdating missing road address table at time: ${DateTime.now()}")
    val vvhClient = new VVHClient(dr2properties.getProperty("digiroad2.VVHRestApiEndPoint"))
    dataImporter.updateMissingRoadAddresses(vvhClient)
    println(s"Missing address update complete at time: ${DateTime.now()}")
    println()
  }

  def updateRoadAddressesGeometry(filterRoadAddresses: Boolean): Unit = {
    println(s"\nUpdating road address table geometries at time: ${DateTime.now()}")
    val vVHClient = new VVHClient(dr2properties.getProperty("digiroad2.VVHRestApiEndPoint"))
    dataImporter.updateRoadAddressesGeometry(vvhClient, filterRoadAddresses)
    println(s"Road addresses geometry update complete at time: ${DateTime.now()}")
    println()
  }

  def findFloatingRoadAddresses(): Unit = {
    println(s"\nFinding road addresses that are floating at time: ${DateTime.now()}")
    val vvhClient = new VVHClient(dr2properties.getProperty("digiroad2.VVHRestApiEndPoint"))
    val roadLinkService = new RoadLinkService(vvhClient, new DummyEventBus, new DummySerializer)
    val roadAddressService = new RoadAddressService(roadLinkService, new DummyEventBus)
    OracleDatabase.withDynTransaction {
      val checker = new FloatingChecker(roadLinkService)
      val roads = checker.checkRoadNetwork()
      println(s"${roads.size} segment(s) found")
      roadAddressService.checkRoadAddressFloatingWithoutTX(roads.map(_.id).toSet)
    }
    println(s"\nRoad Addresses floating field update complete at time: ${DateTime.now()}")
    println()
  }

  private def importComplementaryRoadAddress(): Unit ={
    println(s"\nCommencing complementary road address import at time: ${DateTime.now()}")
    OracleDatabase.withDynTransaction {
      OracleDatabase.setSessionLanguage()
    }
    SqlScriptRunner.runViiteScripts(List(
      "insert_complementary_geometry_data.sql"
    ))
    println(s"complementary road address import completed at time: ${DateTime.now()}")
    println()
  }

  private def combineMultipleSegmentsOnLinks(): Unit ={
    println(s"\nCombining multiple segments on links at time: ${DateTime.now()}")
    OracleDatabase.withDynTransaction {
      OracleDatabase.setSessionLanguage()
      RoadAddressDAO.getValidRoadNumbers.foreach( road => {
        val roadAddresses = RoadAddressDAO.fetchMultiSegmentLinkIds(road).groupBy(_.linkId)
        val replacements = roadAddresses.mapValues(RoadAddressLinkBuilder.fuseRoadAddress)
        roadAddresses.foreach{ case (linkId, list) =>
          val currReplacement = replacements(linkId)
          if (list.size != currReplacement.size) {
            val (kept, removed) = list.partition(ra => currReplacement.exists(_.id == ra.id))
            val (created) = currReplacement.filterNot(ra => kept.exists(_.id == ra.id))
            RoadAddressDAO.remove(removed)
            RoadAddressDAO.create(created, "Automatic_merged")
          }
        }
      })
    }
    println(s"\nFinished the combination of multiple segments on links at time: ${DateTime.now()}")
  }

  private def importRoadAddressChangeTestData(): Unit ={
    println(s"\nCommencing road address change test data import at time: ${DateTime.now()}")
    OracleDatabase.withDynTransaction {
      OracleDatabase.setSessionLanguage()
    }
    SqlScriptRunner.runViiteScripts(List(
      "insert_road_address_change_test_data.sql"
    ))
    println(s"Road Address Change Test Data import completed at time: ${DateTime.now()}")
    println()
  }

  def main(args:Array[String]) : Unit = {
    import scala.util.control.Breaks._
    val username = properties.getProperty("bonecp.username")
    if (!username.startsWith("dr2dev")) {
      println("*************************************************************************************")
      println("YOU ARE RUNNING FIXTURE RESET AGAINST A NON-DEVELOPER DATABASE, TYPE 'YES' TO PROCEED")
      println("*************************************************************************************")
      breakable {
        while (true) {
          val input = Console.readLine()
          if (input.trim() == "YES") {
            break()
          }
        }
      }
    }

    args.headOption match {
      case Some ("find_floating_road_addresses") =>
        findFloatingRoadAddresses()
      case Some ("import_road_addresses") =>
        importRoadAddresses(username.startsWith("dr2dev") || username.startsWith("dr2test"))
      case Some("import_complementary_road_address") =>
        importComplementaryRoadAddress()
      case Some ("recalculate_addresses") =>
        recalculate()
      case Some ("update_missing") =>
        updateMissingRoadAddresses()
      case Some("fuse_multi_segment_road_addresses") =>
        combineMultipleSegmentsOnLinks()
      case Some("update_road_addresses_geometry_no_complementary") =>
        updateRoadAddressesGeometry(true)
      case Some("update_road_addresses_geometry") =>
        updateRoadAddressesGeometry(false)
      case Some ("import_road_address_change_test_data") =>
        importRoadAddressChangeTestData()
      case _ => println("Usage: DataFixture import_road_addresses | recalculate_addresses | update_missing | " +
        "find_floating_road_addresses | import_complementary_road_address | fuse_multi_segment_road_addresses " +
        "| update_road_addresses_geometry_no_complementary | update_road_addresses_geometry | import_road_address_change_test_data")
    }
  }
}
