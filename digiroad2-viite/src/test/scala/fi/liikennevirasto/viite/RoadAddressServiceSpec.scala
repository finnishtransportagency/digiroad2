package fi.liikennevirasto.viite

import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.TrafficDirection.BothDirections
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.viite.dao.{CalibrationPoint, MissingRoadAddress, RoadAddressDAO}
import fi.liikennevirasto.viite.model.{Anomaly, RoadAddressLink, RoadAddressLinkPartitioner}
import fi.liikennevirasto.viite.process.RoadAddressFiller
import fi.liikennevirasto.viite.process.RoadAddressFiller.AddressChangeSet
import org.scalatest.{FunSuite, Matchers}
import org.scalatest.mock.MockitoSugar
import slick.driver.JdbcDriver.backend.Database
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery
import slick.jdbc.StaticQuery.interpolation

class RoadAddressServiceSpec extends FunSuite with Matchers{
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  val roadAddressService = new RoadAddressService(mockRoadLinkService,mockEventBus)
  def runWithRollback(f: => Unit): Unit = {
    Database.forDataSource(OracleDatabase.ds).withDynTransaction {
      f
      dynamicSession.rollback()
    }
  }

  private def calibrationPoint(geometry: Seq[Point], calibrationPoint: Option[CalibrationPoint]) = {
    calibrationPoint match {
      case Some(point) =>
        val mValue = point.segmentMValue match {
          case 0.0 => 0.0
          case _ => Math.min(point.segmentMValue, GeometryUtils.geometryLength(geometry))
        }
        Option(Seq(("point", GeometryUtils.calculatePointFromLinearReference(geometry, mValue)), ("value", point.addressMValue)).toMap)
      case _ => None
    }
  }

  def roadAddressLinkToApi(roadLink: RoadAddressLink): Map[String, Any] = {
    Map(
      "segmentId" -> roadLink.id,
      "linkId" -> roadLink.linkId,
      "mmlId" -> roadLink.attributes.get("MTKID"),
      "points" -> roadLink.geometry,
      "calibrationPoints" -> Seq(calibrationPoint(roadLink.geometry, roadLink.startCalibrationPoint),
        calibrationPoint(roadLink.geometry, roadLink.endCalibrationPoint)),
      "administrativeClass" -> roadLink.administrativeClass.toString,
      "linkType" -> roadLink.linkType.value,
      "functionalClass" -> roadLink.functionalClass,
      "trafficDirection" -> roadLink.trafficDirection.toString,
      "modifiedAt" -> roadLink.modifiedAt,
      "modifiedBy" -> roadLink.modifiedBy,
      "municipalityCode" -> roadLink.attributes.get("MUNICIPALITYCODE"),
      "verticalLevel" -> roadLink.attributes.get("VERTICALLEVEL"),
      "roadNameFi" -> roadLink.attributes.get("ROADNAME_FI"),
      "roadNameSe" -> roadLink.attributes.get("ROADNAME_SE"),
      "roadNameSm" -> roadLink.attributes.get("ROADNAME_SM"),
      "minAddressNumberRight" -> roadLink.attributes.get("FROM_RIGHT"),
      "maxAddressNumberRight" -> roadLink.attributes.get("TO_RIGHT"),
      "minAddressNumberLeft" -> roadLink.attributes.get("FROM_LEFT"),
      "maxAddressNumberLeft" -> roadLink.attributes.get("TO_LEFT"),
      "roadNumber" -> roadLink.roadNumber,
      "roadPartNumber" -> roadLink.roadPartNumber,
      "elyCode" -> roadLink.elyCode,
      "trackCode" -> roadLink.trackCode,
      "startAddressM" -> roadLink.startAddressM,
      "endAddressM" -> roadLink.endAddressM,
      "discontinuity" -> roadLink.discontinuity,
      "endDate" -> roadLink.endDate)
  }

  test("testGetCalibrationPoints") {
    //TODO
  }

  test("testRoadClass") {
    //TODO
  }

  test("test getRoadLinkFromVVH should have specific fields (still to be defined) not empty"){

    OracleDatabase.withDynTransaction {

      val roadLinks = Seq(RoadAddressLink(0,5171208,Seq(Point(532837.14110884,6993543.6296834,0.0),Point(533388.14110884,6994014.1296834,0.0)),0.0,Municipality,0,TrafficDirection.UnknownDirection,UnknownLinkType,None,None,Map("linkId" ->5171208, "segmentId" -> 63298 ),5,205,1,0,0,0,1,"2016-01-01",0.0,0.0,SideCode.Unknown,None,None))
      val partitionedRoadLinks = RoadAddressLinkPartitioner.partition(roadLinks)
      partitionedRoadLinks.map {
        _.map(roadAddressLinkToApi)
      }
      val roadPartNumber = partitionedRoadLinks.head.head.roadPartNumber
      val roadNumber = partitionedRoadLinks.head.head.roadNumber
      val trackCode = partitionedRoadLinks.head.head.trackCode
      val segmentId = partitionedRoadLinks.head.head.id

      segmentId should not be None
      roadNumber should be (5)
      roadPartNumber should be (205)
      trackCode should be (1)
    }
  }

  test("test createMissingRoadAddress should not add two equal roadAddresses"){
    runWithRollback {
      val roadAddressLinks = Seq(
        RoadAddressLink(0, 1611616, Seq(Point(374668.195, 6676884.282, 24.48399999999674), Point(374643.384, 6676882.176, 24.42399999999907)), 297.7533188814259, State, 3, TrafficDirection.BothDirections, SingleCarriageway, Some("22.09.2016 14:51:28"), Some("dr1_conversion"), Map("linkId" -> 1611605, "segmentId" -> 63298), 1, 3, 0, 0, 0, 0, 0, "", 0.0, 0.0, SideCode.Unknown, None, None)
      )
      roadAddressLinks.foreach { links =>
        RoadAddressDAO.createMissingRoadAddress(
          MissingRoadAddress(links.linkId, Some(links.startAddressM), Some(links.endAddressM), Some(links.roadNumber),
            Some(links.roadPartNumber), None, None, Anomaly.apply(1)))
      }
      val linksFromDB = getSpecificMissingRoadAddresses(roadAddressLinks(0).linkId);

      linksFromDB(0)._2 should be(0)
      linksFromDB(0)._3 should be(0)
      linksFromDB(0)._4 should be(1)
      linksFromDB(0)._5 should be(3)
      linksFromDB(0)._6 should be(1)
    }
  }

  private def getSpecificMissingRoadAddresses(linkId :Long): List[(Long, Long, Long, Long, Long, Int)] = {
    sql"""
          select link_id, start_addr_m, end_addr_m, road_number, road_part_number, anomaly_code
            from missing_road_address where link_id = $linkId
      """.as[(Long, Long, Long, Long, Long, Int)].list
  }

  test("test anomaly code"){
    runWithRollback {
      val roadAddressLinks = Seq(
        RoadAddressLink(0, 1611615, Seq(Point(374668.195, 6676884.282, 24.48399999999674), Point(374643.384, 6676882.176, 24.42399999999907)), 297.7533188814259, State, 3, TrafficDirection.BothDirections, SingleCarriageway, Some("22.09.2016 14:51:28"), Some("dr1_conversion"), Map("linkId" -> 1611605, "segmentId" -> 63298), 0, 0, 0, 0, 0, 0, 0, "", 0.0, 0.0, SideCode.Unknown, None, None)
      )
      roadAddressService.getAnomalyCodeByLinkId(roadAddressLinks(0).linkId, roadAddressLinks(0).roadPartNumber) should be(Anomaly.NoAddressGiven)
    }
  }

  test("check PO temporary restrictions"){

    val l1: Long = 5168616
    val l2: Long = 5168617
    val l3: Long = 5168618 //meet dropSegmentsOutsideGeometry restrictions
    val l4: Long = 5168619 //meet extendToGeometry restrictions
    val l5: Long = 5168620 //meet capToGeometry restrictions

    val roadLinksSeq = Seq(RoadLink(l1, Seq(Point(532686.507,6997280.405,99.28599999999278), Point(532682.145,6997307.366,98.99700000000303),
      Point(532673.695,6997367.113,99.11299999999756), Point(532665.336,6997428.384,99.31699999999546), Point(532655.448,6997496.461,99.58400000000256),
      Point(532647.278,6997553.917,99.76600000000326), Point(532640.024,6997604.115,99.93700000000536), Point(532635.796,6997630.174,100.08000000000175),
      Point(532635.575,6998631.749,100.07700000000477)), 355.82666256921844, State, 99, BothDirections, UnknownLinkType, Some("25.11.2013 02:00:00"), Some("vvh_modified"), Map()),
      RoadLink(l2, Seq(Point(532686.507,6997280.405,99.28599999999278), Point(532682.145,6997307.366,98.99700000000303),
        Point(532673.695,6997367.113,99.11299999999756), Point(532665.336,6997428.384,99.31699999999546), Point(532655.448,6997496.461,99.58400000000256),
        Point(532647.278,6997553.917,99.76600000000326), Point(532640.024,6997604.115,99.93700000000536), Point(532635.796,6997630.174,100.08000000000175),
        Point(532635.575,6997631.749,100.07700000000477)), 355.02666256921844, State, 99, BothDirections, UnknownLinkType, Some("25.11.2013 02:00:00"), Some("vvh_modified"), Map()),
      RoadLink(l3, Seq(Point(532686.507,6997280.405,99.28599999999278), Point(532682.145,6997307.366,98.99700000000303),
        Point(532673.695,6997367.113,99.11299999999756), Point(532665.336,6997428.384,99.31699999999546), Point(532655.448,6997496.461,99.58400000000256),
        Point(532647.278,6997553.917,99.76600000000326), Point(532640.024,6997604.115,99.93700000000536), Point(532635.796,6997630.174,100.08000000000175),
        Point(532635.575,6997631.749,100.07700000000477)), 355.02666256921844, State, 99, BothDirections, UnknownLinkType, Some("25.11.2013 02:00:00"), Some("vvh_modified"), Map()),
      RoadLink(l4, Seq(Point(532686.507,6997280.405,99.28599999999278), Point(532682.145,6997307.366,98.99700000000303),
        Point(532673.695,6997367.113,99.11299999999756), Point(532665.336,6997428.384,99.31699999999546), Point(532655.448,6997496.461,99.58400000000256),
        Point(532647.278,6997553.917,99.76600000000326), Point(532640.024,6997604.115,99.93700000000536), Point(532635.796,6997630.174,100.08000000000175),
        Point(532635.575,6997632.749,100.07700000000477)), 355.02666256921844, State, 99, BothDirections, UnknownLinkType, Some("25.11.2013 02:00:00"), Some("vvh_modified"), Map()),
      RoadLink(l5, Seq(Point(532686.507,6997280.405,99.28599999999278), Point(532682.145,6997307.366,98.99700000000303),
        Point(532673.695,6997367.113,99.11299999999756), Point(532665.336,6997428.384,99.31699999999546), Point(532655.448,6997496.461,99.58400000000256),
        Point(532647.278,6997553.917,99.76600000000326), Point(532640.024,6997604.115,99.93700000000536), Point(532635.796,6997630.174,100.08000000000175),
        Point(532635.575,6997632.749,100.07700000000477)), 355.02666256921844, State, 99, BothDirections, UnknownLinkType, Some("25.11.2013 02:00:00"), Some("vvh_modified"), Map())
    )
    val roadAddressLinksMap = Map(l2 -> Seq(RoadAddressLink(333012, l2, Seq(Point(532686.507,6997280.405,0.0), Point(532682.145,6997307.366,0.0),
      Point(532673.695,6997367.113,0.0),Point(532665.336,6997428.384,0.0), Point(532655.448,6997496.461,0.0),
      Point(532647.278,6997553.917,0.0),Point(532640.024,6997604.115,0.0), Point(532635.796,6997630.174,0.0),
      Point(532635.575,6997631.749,0.0)), 355.02666256921844, State, 99, BothDirections, UnknownLinkType, Some("29.10.2015 17:34:02"), Some("vvh_modified"), Map("linkId" -> 1611605, "segmentId" -> 63298), 5, 206, 0, 8, 5, 3446, 3800, null, 0.0, 355.027, SideCode.BothDirections, None, None)),
      l1 -> Seq(RoadAddressLink(333013, l1, Seq(Point(532686.507,6997280.405,0.0), Point(532682.145,6997307.366,0.0),
        Point(532673.695,6997367.113,0.0),Point(532665.336,6997428.384,0.0), Point(532655.448,6997496.461,0.0),
        Point(532647.278,6997553.917,0.0),Point(532640.024,6997604.115,0.0), Point(532635.796,6997630.174,0.0),
        Point(532635.575,6997631.749,0.0)), 355.02666256921844, State, 99, BothDirections, UnknownLinkType, Some("29.10.2015 17:34:02"), Some("vvh_modified"), Map("linkId" -> 1611605, "segmentId" -> 63298), 5, 206, 0, 8, 5, 3446, 3800, null, 0.0, 355.027, SideCode.BothDirections, None, None)),
      l4 -> Seq(RoadAddressLink(333014, l4, Seq(Point(532686.507,6997280.405,0.0), Point(532682.145,6997307.366,0.0),
        Point(532673.695,6997367.113,0.0),Point(532665.336,6997428.384,0.0), Point(532655.448,6997496.461,0.0),
        Point(532647.278,6997553.917,0.0),Point(532640.024,6997604.115,0.0), Point(532635.796,6997630.174,0.0),
        Point(532635.575,6997631.749,0.0)), 355.02666256921844, State, 99, BothDirections, UnknownLinkType, Some("29.10.2015 17:34:02"), Some("vvh_modified"), Map("linkId" -> 1611605, "segmentId" -> 63298), 5, 206, 0, 8, 5, 3446, 3800, null, 354.0276, 355.029, SideCode.BothDirections, None, None)),
      l3 -> Seq(RoadAddressLink(333015, l3, Seq(Point(532686.507,6997280.405,0.0), Point(532682.145,6997307.366,0.0),
        Point(532673.695,6997367.113,0.0),Point(532665.336,6997428.384,0.0), Point(532655.448,6997496.461,0.0),
        Point(532647.278,6997553.917,0.0),Point(532640.024,6997604.115,0.0), Point(532635.796,6997630.174,0.0),
        Point(532637.575,6996631.749,0.0)), 355.02666256921844, State, 99, BothDirections, UnknownLinkType, Some("29.10.2015 17:34:02"), Some("vvh_modified"), Map("linkId" -> 1611605, "segmentId" -> 63298), 5, 206, 0, 8, 5, 3446, 3800, null, 355.82666256921844, 355.927, SideCode.BothDirections, None, None)),
      l5 -> Seq(RoadAddressLink(333016, l5, Seq(Point(532686.507,6997280.405,0.0), Point(532682.145,6997307.366,0.0),
        Point(532673.695,6997367.113,0.0),Point(532665.336,6997428.384,0.0), Point(532655.448,6997496.461,0.0),
        Point(532647.278,6997553.917,0.0),Point(532640.024,6997604.115,0.0), Point(532635.796,6997630.174,0.0)),
        352.0, State, 99, BothDirections, UnknownLinkType, Some("29.10.2015 17:34:02"), Some("vvh_modified"), Map("linkId" -> 1611605, "segmentId" -> 63298), 5, 206, 0, 8, 5, 3446, 3800, null, 355.82666256921844, 355.927, SideCode.BothDirections, None, None))
    )

    val (topology, changeSet) = RoadAddressFiller.fillTopology(roadLinksSeq, roadAddressLinksMap)
    changeSet.adjustedMValues.size should be (2)
    changeSet.toFloatingAddressIds.size should be (1)
    changeSet.toFloatingAddressIds.contains(333015L) should be (true)
    changeSet.adjustedMValues.map(_.linkId) should be (Seq(l4, l5))
  }

}
