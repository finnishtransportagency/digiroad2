package fi.liikennevirasto.viite

import fi.liikennevirasto.digiroad2.FeatureClass.AllOthers
import fi.liikennevirasto.digiroad2.RoadLinkType.NormalRoadLinkType
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.ConstructionType.InUse
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset.TrafficDirection.{AgainstDigitizing, BothDirections}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.Sequences
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.viite.dao._
import fi.liikennevirasto.viite.model.{Anomaly, RoadAddressLink, RoadAddressLinkPartitioner}
import fi.liikennevirasto.viite.process.RoadAddressFiller
import fi.liikennevirasto.viite.process.RoadAddressFiller.LRMValueAdjustment
import org.joda.time.DateTime
import org.mockito.ArgumentCaptor
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSuite, Matchers}
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

      val roadLinks = Seq(RoadAddressLink(0,5171208,Seq(Point(532837.14110884,6993543.6296834,0.0),Point(533388.14110884,6994014.1296834,0.0)),0.0,Municipality, UnknownLinkType, NormalRoadLinkType, InUse, NormalLinkInterface, RoadType.MunicipalityStreetRoad,None,None,Map("linkId" ->5171208, "segmentId" -> 63298 ),5,205,1,0,0,0,1,"2015-01-01","2016-01-01",0.0,0.0,SideCode.Unknown,None,None, Anomaly.None, 0))
      val partitionedRoadLinks = RoadAddressLinkPartitioner.partition(roadLinks)
      partitionedRoadLinks.map {
        _.map(roadAddressLinkToApi)
      }
      val roadPartNumber = partitionedRoadLinks.head.head.roadPartNumber
      val roadNumber = partitionedRoadLinks.head.head.roadNumber
      val trackCode = partitionedRoadLinks.head.head.trackCode
      val segmentId = partitionedRoadLinks.head.head.id
      val constructionType = partitionedRoadLinks.head.head.constructionType.value

      segmentId should not be None
      roadNumber should be (5)
      roadPartNumber should be (205)
      trackCode should be (1)
      constructionType should be (0)
    }
  }

  test("test createMissingRoadAddress should not add two equal roadAddresses"){
    runWithRollback {
      val roadAddressLinks = Seq(
        RoadAddressLink(0, 1611616, Seq(Point(374668.195, 6676884.282, 24.48399999999674), Point(374643.384, 6676882.176, 24.42399999999907)), 297.7533188814259, State, SingleCarriageway, NormalRoadLinkType, InUse, NormalLinkInterface, RoadType.PrivateRoadType,  Some("22.09.2016 14:51:28"), Some("dr1_conversion"), Map("linkId" -> 1611605, "segmentId" -> 63298), 1, 3, 0, 0, 0, 0, 0, "", "", 0.0, 0.0, SideCode.Unknown, None, None, Anomaly.None, 0)
      )
      val oldMissingRA = RoadAddressDAO.getMissingRoadAddresses(Set()).size
      roadAddressLinks.foreach { links =>
        RoadAddressDAO.createMissingRoadAddress(
          MissingRoadAddress(links.linkId, Some(links.startAddressM), Some(links.endAddressM), RoadType.PublicRoad, Some(links.roadNumber),
            Some(links.roadPartNumber), None, None, Anomaly.NoAddressGiven))
      }
      val linksFromDB = getSpecificMissingRoadAddresses(roadAddressLinks(0).linkId)
      RoadAddressDAO.getMissingRoadAddresses(Set()) should have size(oldMissingRA)
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

  test("Check the correct return of a RoadAddressLink by Municipality") {
    val municipalityId = 235

    val modifificationDate = "1455274504000l"
    val modificationUser = "testUser"
    runWithRollback {
      val (linkId) = sql""" Select pos.LINK_ID
                                From ROAD_ADDRESS ra inner join LRM_POSITION pos on ra.LRM_POSITION_ID = pos.id
                                Order By ra.id asc""".as[Long].firstOption.get
      val roadLink = RoadLink(linkId, Seq(Point(50200, 7630000.0, 0.0), Point(50210, 7630000.0, 10.0)), 0, Municipality, 0, TrafficDirection.TowardsDigitizing, Freeway, Some(modifificationDate), Some(modificationUser), attributes = Map("MUNICIPALITYCODE" -> BigInt(235)))

      when(mockRoadLinkService.getViiteRoadLinksFromVVHByMunicipality(municipalityId)).thenReturn(Seq(roadLink))
      val roadAddressLink = roadAddressService.getRoadAddressesLinkByMunicipality(municipalityId)

      roadAddressLink.isInstanceOf[Seq[RoadAddressLink]] should be(true)
      roadAddressLink.nonEmpty should be(true)
      roadAddressLink.head.linkId should be(linkId)
      roadAddressLink.head.attributes.contains("MUNICIPALITYCODE") should be (true)
      roadAddressLink.head.attributes.get("MUNICIPALITYCODE") should be (Some(municipalityId))
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
      Point(532635.575,6998631.749,100.07700000000477)), 355.82666256921844, State, 99, BothDirections, UnknownLinkType, Some("25.11.2013 02:00:00"), Some("vvh_modified"), Map("MUNICIPALITYCODE" -> BigInt(235))),
      RoadLink(l2, Seq(Point(532686.507,6997280.405,99.28599999999278), Point(532682.145,6997307.366,98.99700000000303),
        Point(532673.695,6997367.113,99.11299999999756), Point(532665.336,6997428.384,99.31699999999546), Point(532655.448,6997496.461,99.58400000000256),
        Point(532647.278,6997553.917,99.76600000000326), Point(532640.024,6997604.115,99.93700000000536), Point(532635.796,6997630.174,100.08000000000175),
        Point(532635.575,6997631.749,100.07700000000477)), 355.02666256921844, State, 99, BothDirections, UnknownLinkType, Some("25.11.2013 02:00:00"), Some("vvh_modified"), Map("MUNICIPALITYCODE" -> BigInt(235))),
      RoadLink(l3, Seq(Point(532686.507,6997280.405,99.28599999999278), Point(532682.145,6997307.366,98.99700000000303),
        Point(532673.695,6997367.113,99.11299999999756), Point(532665.336,6997428.384,99.31699999999546), Point(532655.448,6997496.461,99.58400000000256),
        Point(532647.278,6997553.917,99.76600000000326), Point(532640.024,6997604.115,99.93700000000536), Point(532635.796,6997630.174,100.08000000000175),
        Point(532635.575,6997631.749,100.07700000000477)), 355.02666256921844, State, 99, BothDirections, UnknownLinkType, Some("25.11.2013 02:00:00"), Some("vvh_modified"), Map("MUNICIPALITYCODE" -> BigInt(235))),
      RoadLink(l4, Seq(Point(532686.507,6997280.405,99.28599999999278), Point(532682.145,6997307.366,98.99700000000303),
        Point(532673.695,6997367.113,99.11299999999756), Point(532665.336,6997428.384,99.31699999999546), Point(532655.448,6997496.461,99.58400000000256),
        Point(532647.278,6997553.917,99.76600000000326), Point(532640.024,6997604.115,99.93700000000536), Point(532635.796,6997630.174,100.08000000000175),
        Point(532635.575,6997632.749,100.07700000000477)), 355.02666256921844, State, 99, BothDirections, UnknownLinkType, Some("25.11.2013 02:00:00"), Some("vvh_modified"), Map("MUNICIPALITYCODE" -> BigInt(235))),
      RoadLink(l5, Seq(Point(532686.507,6997280.405,99.28599999999278), Point(532682.145,6997307.366,98.99700000000303),
        Point(532673.695,6997367.113,99.11299999999756), Point(532665.336,6997428.384,99.31699999999546), Point(532655.448,6997496.461,99.58400000000256),
        Point(532647.278,6997553.917,99.76600000000326), Point(532640.024,6997604.115,99.93700000000536), Point(532635.796,6997630.174,100.08000000000175),
        Point(532635.575,6997632.749,100.07700000000477)), 355.02666256921844, State, 99, BothDirections, UnknownLinkType, Some("25.11.2013 02:00:00"), Some("vvh_modified"), Map("MUNICIPALITYCODE" -> BigInt(235)))
    )
    val roadAddressLinksMap = Map(l2 -> Seq(RoadAddressLink(333012, l2, Seq(Point(532686.507,6997280.405,0.0), Point(532682.145,6997307.366,0.0),
      Point(532673.695,6997367.113,0.0),Point(532665.336,6997428.384,0.0), Point(532655.448,6997496.461,0.0),
      Point(532647.278,6997553.917,0.0),Point(532640.024,6997604.115,0.0), Point(532635.796,6997630.174,0.0),
      Point(532635.575,6997631.749,0.0)), 355.02666256921844, State, UnknownLinkType, NormalRoadLinkType, InUse, NormalLinkInterface, RoadType.PublicRoad, Some("29.10.2015 17:34:02"), Some("vvh_modified"), Map("linkId" -> 1611605, "segmentId" -> 63298), 5, 206, 0, 8, 5, 3446, 3800, "", "", 0.0, 355.027, SideCode.BothDirections, None, None, Anomaly.None, 0)),
      l1 -> Seq(RoadAddressLink(333013, l1, Seq(Point(532686.507,6997280.405,0.0), Point(532682.145,6997307.366,0.0),
        Point(532673.695,6997367.113,0.0),Point(532665.336,6997428.384,0.0), Point(532655.448,6997496.461,0.0),
        Point(532647.278,6997553.917,0.0),Point(532640.024,6997604.115,0.0), Point(532635.796,6997630.174,0.0),
        Point(532635.575,6997631.749,0.0)), 355.02666256921844, State, UnknownLinkType, NormalRoadLinkType, InUse, NormalLinkInterface, RoadType.PublicRoad, Some("29.10.2015 17:34:02"), Some("vvh_modified"), Map("linkId" -> 1611605, "segmentId" -> 63298), 5, 206, 0, 8, 5, 3446, 3800, "", "", 0.0, 355.027, SideCode.BothDirections, None, None, Anomaly.None, 0)),
      l4 -> Seq(RoadAddressLink(333014, l4, Seq(Point(532686.507,6997280.405,0.0), Point(532682.145,6997307.366,0.0),
        Point(532673.695,6997367.113,0.0),Point(532665.336,6997428.384,0.0), Point(532655.448,6997496.461,0.0),
        Point(532647.278,6997553.917,0.0),Point(532640.024,6997604.115,0.0), Point(532635.796,6997630.174,0.0),
        Point(532635.575,6997631.749,0.0)), 355.02666256921844, State, UnknownLinkType, NormalRoadLinkType, InUse, NormalLinkInterface, RoadType.PublicRoad, Some("29.10.2015 17:34:02"), Some("vvh_modified"), Map("linkId" -> 1611605, "segmentId" -> 63298), 5, 206, 0, 8, 5, 3446, 3800, "", "", 354.0276, 355.029, SideCode.BothDirections, None, None,Anomaly.None, 0)),
      l3 -> Seq(RoadAddressLink(333015, l3, Seq(Point(532686.507,6997280.405,0.0), Point(532682.145,6997307.366,0.0),
        Point(532673.695,6997367.113,0.0),Point(532665.336,6997428.384,0.0), Point(532655.448,6997496.461,0.0),
        Point(532647.278,6997553.917,0.0),Point(532640.024,6997604.115,0.0), Point(532635.796,6997630.174,0.0),
        Point(532637.575,6996631.749,0.0)), 355.02666256921844, State, UnknownLinkType, NormalRoadLinkType, InUse, NormalLinkInterface, RoadType.PublicRoad, Some("29.10.2015 17:34:02"), Some("vvh_modified"), Map("linkId" -> 1611605, "segmentId" -> 63298), 5, 206, 0, 8, 5, 3446, 3800, "", "", 355.82666256921844, 355.927, SideCode.BothDirections, None, None, Anomaly.None, 0)),
      l5 -> Seq(RoadAddressLink(333016, l5, Seq(Point(532686.507,6997280.405,0.0), Point(532682.145,6997307.366,0.0),
        Point(532673.695,6997367.113,0.0),Point(532665.336,6997428.384,0.0), Point(532655.448,6997496.461,0.0),
        Point(532647.278,6997553.917,0.0),Point(532640.024,6997604.115,0.0), Point(532635.796,6997630.174,0.0)),
        352.0, State, UnknownLinkType, NormalRoadLinkType, InUse, NormalLinkInterface, RoadType.PublicRoad, Some("29.10.2015 17:34:02"), Some("vvh_modified"), Map("linkId" -> 1611605, "segmentId" -> 63298), 5, 206, 0, 8, 5, 3446, 3800, "", "", 355.82666256921844, 355.927, SideCode.BothDirections, None, None, Anomaly.None, 0))
    )

    val (topology, changeSet) = RoadAddressFiller.fillTopology(roadLinksSeq, roadAddressLinksMap)
    changeSet.adjustedMValues.size should be (2)
    changeSet.toFloatingAddressIds.size should be (1)
    changeSet.toFloatingAddressIds.contains(333015L) should be (true)
    changeSet.adjustedMValues.map(_.linkId) should be (Seq(l4, l5))
  }

  test("LRM modifications are published"){
    val localMockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val localMockEventBus = MockitoSugar.mock[DigiroadEventBus]
    val localRoadAddressService = new RoadAddressService(localMockRoadLinkService,localMockEventBus)
    val boundingRectangle = BoundingRectangle(Point(533341.472,6988382.846), Point(533333.28,6988419.385))
    val filter = OracleDatabase.boundingBoxFilter(boundingRectangle, "geometry")
    runWithRollback {
      val modificationDate = "1455274504000l"
      val modificationUser = "testUser"
      val query = s"""select pos.LINK_ID, pos.end_measure
        from ROAD_ADDRESS ra inner join LRM_POSITION pos on ra.LRM_POSITION_ID = pos.id
        where $filter and (ra.valid_to > sysdate or ra.valid_to is null) order by ra.id asc"""
      val (linkId, endM) = StaticQuery.queryNA[(Long, Double)](query).firstOption.get
      val roadLink = RoadLink(linkId, Seq(Point(0.0, 0.0), Point(endM + .5, 0.0)), endM + .5, Municipality, 1, TrafficDirection.TowardsDigitizing, Freeway, Some(modificationDate), Some(modificationUser), attributes = Map("MUNICIPALITYCODE" -> BigInt(235)))
      when(localMockRoadLinkService.getViiteRoadLinksFromVVH(any[BoundingRectangle], any[Seq[(Int,Int)]], any[Set[Int]], any[Boolean], any[Boolean])).thenReturn(Seq(roadLink))
      when(localMockRoadLinkService.getComplementaryRoadLinksFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn(Seq.empty)
      when(localMockRoadLinkService.getViiteRoadLinksHistoryFromVVH(any[Set[Long]])).thenReturn(Seq.empty)
      val captor: ArgumentCaptor[Iterable[Any]] = ArgumentCaptor.forClass(classOf[Iterable[Any]])
      reset(localMockEventBus)
      val links = localRoadAddressService.getRoadAddressLinks(boundingRectangle, Seq(), Set())
      links.size should be (1)
      verify(localMockEventBus, times(3)).publish(any[String], captor.capture)
      val capturedAdjustments = captor.getAllValues
      val missing = capturedAdjustments.get(0)
      val adjusting = capturedAdjustments.get(1)
      val floating = capturedAdjustments.get(2)
      missing.size should be (0)
      adjusting.size should be (1)
      floating.size should be (0)
      adjusting.head.asInstanceOf[LRMValueAdjustment].endMeasure should be (Some(endM+.5))
    }
  }

  test("Floating check gets geometry updated") {
    val roadLink = VVHRoadlink(5171359L, 1, Seq(Point(0.0, 0.0), Point(0.0, 31.045)), State, TrafficDirection.BothDirections,
      AllOthers, None, Map(), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    when(mockRoadLinkService.getCurrentAndComplementaryVVHRoadLinks(Set(5171359L))).thenReturn(Seq(roadLink))
    runWithRollback {
      val addressList = RoadAddressDAO.fetchByLinkId(Set(5171359L))
      addressList should have size (1)
      val address = addressList.head
      address.floating should be (false)
      address.geom shouldNot be (roadLink.geometry)
      roadAddressService.checkRoadAddressFloatingWithoutTX(Set(address.id))
      dynamicSession.rollback()
      val addressUpdated = RoadAddressDAO.queryById(Set(address.id)).head
      addressUpdated.geom shouldNot be (address.geom)
      addressUpdated.geom should be(roadLink.geometry)
      addressUpdated.floating should be (false)
    }
  }

  test("Floating check gets floating flag updated, not geometry") {
    when(mockRoadLinkService.getCurrentAndComplementaryVVHRoadLinks(Set(5171359L))).thenReturn(Nil)
    runWithRollback {
      val addressList = RoadAddressDAO.fetchByLinkId(Set(5171359L))
      addressList should have size (1)
      val address = addressList.head
      address.floating should be (false)
      roadAddressService.checkRoadAddressFloatingWithoutTX(Set(address.id))
      dynamicSession.rollback()
      val addressUpdated = RoadAddressDAO.queryById(Set(address.id)).head
      addressUpdated.geom should be (address.geom)
      addressUpdated.floating should be (true)
    }
  }

  test("save road link project and get form info") {
    val roadlink = RoadLink(5175306,Seq(Point(535605.272,6982204.22,85.90899999999965))
      ,540.3960283713503,State,99,AgainstDigitizing,UnknownLinkType,Some("25.06.2015 03:00:00"), Some("vvh_modified"),Map("MUNICIPALITYCODE" -> BigInt.apply(749)),
      InUse,NormalLinkInterface)
    when(mockRoadLinkService.getRoadLinksByLinkIdsFromVVH(Set(5175306L))).thenReturn(Seq(roadlink))
    runWithRollback{
      val id = Sequences.nextViitePrimaryKeySeqValue

      val roadAddressProject = RoadAddressProject(id, 1, "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", 1, 1, 1)
      val result = roadAddressService.saveRoadLinkProject(roadAddressProject)
      result.size should be (3)
      result.get("project").get should not be None
      result.get("projectAddresses").get should be (None)
      result.get("formInfo").get should not be None
      result.get("formInfo").size should be(1)
    }
  }

  test("save road link project without values") {
    runWithRollback{
      val id = Sequences.nextViitePrimaryKeySeqValue
      val roadAddressProject = RoadAddressProject(id, 1, "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", 0, 0, 0)
      val result = roadAddressService.saveRoadLinkProject(roadAddressProject)
      result.size should be (3)
      result.get("project").get should not be None
      result.get("projectAddresses").get should be (None)
      result.get("formInfo").get should be (None)
    }
  }

  test("save road link project without valid roadParts") {
    val roadlink = RoadLink(5175306,Seq(Point(535605.272,6982204.22,85.90899999999965))
      ,540.3960283713503,State,99,AgainstDigitizing,UnknownLinkType,Some("25.06.2015 03:00:00"), Some("vvh_modified"),Map("MUNICIPALITYCODE" -> BigInt.apply(749)),
      InUse,NormalLinkInterface)
    when(mockRoadLinkService.getRoadLinksByLinkIdsFromVVH(Set(5175306L))).thenReturn(Seq(roadlink))
    runWithRollback{
      val id = Sequences.nextViitePrimaryKeySeqValue
      val roadAddressProject = RoadAddressProject(id, 1, "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", 1, 3, 5)
      val result = roadAddressService.saveRoadLinkProject(roadAddressProject)
      result.size should be (3)
      result.get("project").get should not be None
      result.get("projectAddresses").get should be (None)
      result.get("formInfo").get should not be None
    }
  }

  ignore("merge road addresses - ignored because rollback doesn't do what it's supposed to do") {
    runWithRollback {
      val addressList = RoadAddressDAO.fetchByLinkId(Set(5171285L, 5170935L, 5171863L))
      addressList should have size (3)
      val address = addressList.head
      val newAddr = address.copy(id = -1000L, startAddrMValue = addressList.map(_.startAddrMValue).min,
        endAddrMValue = addressList.map(_.endAddrMValue).max)
      val merger = RoadAddressMerge(addressList.map(_.id).toSet, Seq(newAddr))
      roadAddressService.mergeRoadAddressInTX(merger)
      val addressListMerged = RoadAddressDAO.fetchByLinkId(Set(5171285L, 5170935L, 5171863L))
      addressListMerged should have size (1)
      addressListMerged.head.linkId should be (address.linkId)
      dynamicSession.rollback()
      RoadAddressDAO.fetchByLinkId(Set(5171285L, 5170935L, 5171863L)) should have size (3)
    }
  }
}
