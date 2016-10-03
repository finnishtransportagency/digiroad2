package fi.liikennevirasto.viite.util

import java.util.Properties

import fi.liikennevirasto.digiroad2.{DummyEventBus, DummySerializer, RoadLinkService, VVHClient}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.viite.dao.RoadAddressDAO
import fi.liikennevirasto.viite.process.{ContinuityChecker, LinkRoadAddressCalculator}
import fi.liikennevirasto.viite.util.AssetDataImporter.Conversion
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
      val roads = RoadAddressDAO.fetchByRoadPart(roadNumber, partNumber)
      val adjusted = LinkRoadAddressCalculator.recalculate(roads)
      val (changed, unchanged) = adjusted.partition(ra =>
        roads.exists(oldra => ra.id == oldra.id && (oldra.startAddrMValue != ra.startAddrMValue || oldra.endAddrMValue != ra.endAddrMValue))
      )
      println(s"Road $roadNumber, part $partNumber: ${changed.size} updated, ${unchanged.size} kept unchanged")
      changed.foreach(RoadAddressDAO.update)
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

  def importRoadAddresses(): Unit = {
    println(s"\nCommencing road address import from conversion at time: ${DateTime.now()}")
    val vvhClient = new VVHClient(dr2properties.getProperty("digiroad2.VVHRestApiEndPoint"))
    dataImporter.importRoadAddressData(Conversion.database(), vvhClient)
    println(s"Road address import complete at time: ${DateTime.now()}")
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
      case Some ("import_road_addresses") =>
        importRoadAddresses()
      case Some ("recalculate_addresses") =>
        recalculate()
      case _ => println("Usage: DataFixture import_road_addresses | recalculate_addresses")
    }
  }
}
