package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.dao.pointasset.{OraclePedestrianCrossingDao, PedestrianCrossing, PersistedTrafficSign}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.process.PedestrianCrossingValidator
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.pointasset.TrafficSignService
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import org.joda.time
import org.joda.time.DateTime

class PedestrianCrossingValidatorSpec extends FunSuite with Matchers{
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockVVHClient = MockitoSugar.mock[VVHClient]
  val mockTrafficSignService = MockitoSugar.mock[TrafficSignService]
  val mockPedestrianCrossingDao: OraclePedestrianCrossingDao = MockitoSugar.mock[OraclePedestrianCrossingDao]


  class TestPedestrianCrossingValidator extends PedestrianCrossingValidator {
    override lazy val dao: OraclePedestrianCrossingDao = mockPedestrianCrossingDao
    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
    override lazy val vvhClient: VVHClient = mockVVHClient
  }

  val pedestrianValidator = new TestPedestrianCrossingValidator
  val typeId = PedestrianCrossings.typeId

  val roadLink1 = RoadLink(1001l, Seq(Point(0.0, 0.0), Point(10, 0.0)), 10, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
  val roadLink2 = RoadLink(1002l, Seq(Point(10.0, 0.0), Point(20, 0.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
  val roadLink3 = RoadLink(1003l, Seq(Point(20.0, 0.0), Point(50.0, 0.0)), 30.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
  val roadLink4 = RoadLink(1004l, Seq(Point(50.0, 0.0), Point(70.0, 0.0)), 20.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

  when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn(Seq(roadLink1, roadLink2, roadLink3))

  test("Pedestrian crossing traffic sign without match asset") {
    OracleDatabase.withDynTransaction {

      val propTrafficSign = Seq(TrafficSignProperty(0, "trafficSigns_type", "", false, Seq(TextPropertyValue(PedestrianCrossingSign.OTHvalue.toString))))
      val trafficSign = PersistedTrafficSign(1, 1002l, 2, 2, 2, false, 0, 235, propTrafficSign, None, None, None, None, SideCode.BothDirections.value, None, NormalLinkInterface)

      when(pedestrianValidator.dao.fetchPedestrianCrossingByLinkIds(Seq(1001l,1002l, 1003l))).thenReturn(Seq())

      val result = pedestrianValidator.assetValidator(trafficSign)
        result should have size 1
        result.head.linkId should be(Some(roadLink2.linkId))

      dynamicSession.rollback()
    }
  }

  test("Pedestrian crossing traffic sign have a correct asset") {
    OracleDatabase.withDynTransaction {
      val propTrafficSign = Seq(TrafficSignProperty(0, "trafficSigns_type", "", false, Seq(TextPropertyValue(PedestrianCrossingSign.OTHvalue.toString))))
      val trafficSign = PersistedTrafficSign(1, 1002l, 2, 0, 2, false, 0, 235, propTrafficSign, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)

      val pointAssetProperty = PointAssetProperty(111111, "pedestrian_crossings_suggest_box", "checkbox", false, Seq(TextPropertyValue("", None, false)))

      when(pedestrianValidator.dao.fetchPedestrianCrossingByLinkIds(Seq(1001l, 1002l, 1003l)))
        .thenReturn(
          Seq(PedestrianCrossing(1, 1003l, 1.0, 1.0, 1.0, false, 0, 235, Seq(pointAssetProperty), linkSource = LinkGeomSource.NormalLinkInterface))
        )

      val result = pedestrianValidator.assetValidator(trafficSign)
        result should have size 0

      dynamicSession.rollback()
    }
  }

  test("Pedestrian crossing traffic sign without a match asset only after 50m") {
    OracleDatabase.withDynTransaction {
      val propTrafficSign = Seq(TrafficSignProperty(0, "trafficSigns_type", "", false, Seq(TextPropertyValue(PedestrianCrossingSign.OTHvalue.toString))))
      val trafficSign = PersistedTrafficSign(1, 1002l, 12, 0, 2, false, 0, 235, propTrafficSign, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)

      val pointAssetProperty = PointAssetProperty(111111, "pedestrian_crossings_suggest_box", "checkbox", false, Seq(TextPropertyValue("", None, false)))

      when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn(Seq(roadLink1, roadLink2, roadLink3, roadLink4))
      when(pedestrianValidator.dao.fetchPedestrianCrossingByLinkIds(Seq(1001l,1002l, 1003l, 1004l)))
        .thenReturn(
          Seq(PedestrianCrossing(1, 1004l, 13, 13, 13, false, 0, 235, Seq(pointAssetProperty), linkSource = LinkGeomSource.NormalLinkInterface))
        )

      val result = pedestrianValidator.assetValidator(trafficSign)
        result should have size 1
        result.head.linkId should be (Some(roadLink2.linkId))

      dynamicSession.rollback()
    }
  }

  test("Pedestrian crossing traffic filter nearest asset") {
    OracleDatabase.withDynTransaction {

      val pointAssetProperty = PointAssetProperty(111111, "pedestrian_crossings_suggest_box", "checkbox", false, Seq(TextPropertyValue("", None, false)))

      val roadLink = RoadLink(1001l, Seq(Point(0.0, 0.0), Point(50, 0.0)), 50, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val assets = Seq(
        PedestrianCrossing(1l, 1001l, 0, 5, 5, false, 0 ,235, Seq(pointAssetProperty), Some("testUser"), Some(DateTime.now), None, None, linkSource = LinkGeomSource.NormalLinkInterface),
        PedestrianCrossing(2l, 1001l, 0, 10, 10, false, 0 ,235, Seq(pointAssetProperty), Some("testUser"), Some(DateTime.now), None, None, linkSource = LinkGeomSource.NormalLinkInterface),
        PedestrianCrossing(3l, 1001l, 0, 30, 30, false, 0 ,235, Seq(pointAssetProperty), Some("testUser"), Some(DateTime.now), None, None, linkSource = LinkGeomSource.NormalLinkInterface),
        PedestrianCrossing(4l, 1001l, 0, 40, 40, false, 0 ,235, Seq(pointAssetProperty), Some("testUser"), Some(DateTime.now), None, None, linkSource = LinkGeomSource.NormalLinkInterface))

      val result = pedestrianValidator.filteredAsset(roadLink, assets, Point(0, 0.0), 0)
      result should have size 1
      result.head.id should be (1l)
      result.head.mValue should be (5)

      dynamicSession.rollback()
    }
  }

  test("Pedestrian crossing traffic filter further asset") {
    OracleDatabase.withDynTransaction {

      val pointAssetProperty = PointAssetProperty(111111, "pedestrian_crossings_suggest_box", "checkbox", false, Seq(TextPropertyValue("", None, false)))

      val roadLink = RoadLink(1001l, Seq(Point(0.0, 0.0), Point(50, 0.0)), 50, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val assets = Seq(
        PedestrianCrossing(1l, 1001l, 0, 5, 5, false, 0 ,235, Seq(pointAssetProperty), Some("testUser"), Some(DateTime.now), None, None, linkSource = LinkGeomSource.NormalLinkInterface),
        PedestrianCrossing(2l, 1001l, 0, 10, 10, false, 0 ,235, Seq(pointAssetProperty), Some("testUser"), Some(DateTime.now), None, None, linkSource = LinkGeomSource.NormalLinkInterface),
        PedestrianCrossing(3l, 1001l, 0, 30, 30, false, 0 ,235, Seq(pointAssetProperty), Some("testUser"), Some(DateTime.now), None, None, linkSource = LinkGeomSource.NormalLinkInterface),
        PedestrianCrossing(4l, 1001l, 0, 40, 40, false, 0 ,235, Seq(pointAssetProperty), Some("testUser"), Some(DateTime.now), None, None, linkSource = LinkGeomSource.NormalLinkInterface))

      val result = pedestrianValidator.filteredAsset(roadLink, assets, Point(50, 0.0), 0)
      result should have size 1
      result.head.id should be (4l)
      result.head.mValue should be (40)

      dynamicSession.rollback()
    }
  }

  test("Pedestrian crossing traffic (AgainstDigitizing) in roadLink filter nearest asset TowardDigitalization") {
    OracleDatabase.withDynTransaction {

      val pointAssetProperty = PointAssetProperty(111111, "pedestrian_crossings_suggest_box", "checkbox", false, Seq(TextPropertyValue("", None, false)))

      val roadLink = RoadLink(1001l, Seq(Point(0.0, 0.0), Point(50, 0.0)), 50, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val assets = Seq(
        PedestrianCrossing(1l, 1001l, 0, 5, 5, false, 0 ,235, Seq(pointAssetProperty), Some("testUser"), Some(DateTime.now), None, None, linkSource = LinkGeomSource.NormalLinkInterface),
        PedestrianCrossing(2l, 1001l, 0, 10, 10, false, 0 ,235, Seq(pointAssetProperty), Some("testUser"), Some(DateTime.now), None, None, linkSource = LinkGeomSource.NormalLinkInterface),
        PedestrianCrossing(3l, 1001l, 0, 30, 30, false, 0 ,235, Seq(pointAssetProperty), Some("testUser"), Some(DateTime.now), None, None, linkSource = LinkGeomSource.NormalLinkInterface),
        PedestrianCrossing(4l, 1001l, 0, 40, 40, false, 0 ,235, Seq(pointAssetProperty), Some("testUser"), Some(DateTime.now), None, None, linkSource = LinkGeomSource.NormalLinkInterface))

      val propTrafficSign = Seq(TrafficSignProperty(0, "trafficSigns_type", "", false, Seq(TextPropertyValue(PedestrianCrossingSign.OTHvalue.toString))))
      val trafficSign = PersistedTrafficSign(1, 1001l, 0, 25, 25, false, 0, 235, propTrafficSign, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)

      val result = pedestrianValidator.filteredAsset(roadLink, assets, Point(0.0, 0.0), 0, Some(trafficSign))
      result should have size 1
      result.head.id should be (2l)
      result.head.mValue should be (10)

      dynamicSession.rollback()
    }
  }

  test("Pedestrian crossing traffic (TowardsDigitizing) in roadLink filter nearest asset AgainstDigitizing") {
    OracleDatabase.withDynTransaction {
      val pointAssetProperty = PointAssetProperty(111111, "pedestrian_crossings_suggest_box", "checkbox", false, Seq(TextPropertyValue("", None, false)))

      val roadLink = RoadLink(1001l, Seq(Point(0.0, 0.0), Point(50, 0.0)), 50, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val assets = Seq(
        PedestrianCrossing(1l, 1001l, 0, 5, 5, false, 0 ,235, Seq(pointAssetProperty), Some("testUser"), Some(DateTime.now), None, None, linkSource = LinkGeomSource.NormalLinkInterface),
        PedestrianCrossing(2l, 1001l, 0, 10, 10, false, 0 ,235, Seq(pointAssetProperty), Some("testUser"), Some(DateTime.now), None, None, linkSource = LinkGeomSource.NormalLinkInterface),
        PedestrianCrossing(3l, 1001l, 0, 30, 30, false, 0 ,235, Seq(pointAssetProperty), Some("testUser"), Some(DateTime.now), None, None, linkSource = LinkGeomSource.NormalLinkInterface),
        PedestrianCrossing(4l, 1001l, 0, 40, 40, false, 0 ,235, Seq(pointAssetProperty), Some("testUser"), Some(DateTime.now), None, None, linkSource = LinkGeomSource.NormalLinkInterface))

      val propTrafficSign = Seq(TrafficSignProperty(0, "trafficSigns_type", "", false, Seq(TextPropertyValue(PedestrianCrossingSign.OTHvalue.toString))))
      val trafficSign = PersistedTrafficSign(1, 1001l, 0, 25, 25, false, 0, 235, propTrafficSign, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)

      val result = pedestrianValidator.filteredAsset(roadLink, assets, Point(0, 0.0), 0, Some(trafficSign))
      result should have size 1
      result.head.id should be (3l)
      result.head.mValue should be (30)

      dynamicSession.rollback()
    }
  }

  test("Pedestrian crossing traffic (AgainstDigitizing) in roadLink filter nearest asset AgainstDigitizing") {
    OracleDatabase.withDynTransaction {
      val pointAssetProperty = PointAssetProperty(111111, "pedestrian_crossings_suggest_box", "checkbox", false, Seq(TextPropertyValue("", None, false)))

      val roadLink = RoadLink(1001l, Seq(Point(0.0, 0.0), Point(50, 0.0)), 50, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val assets = Seq(
        PedestrianCrossing(1l, 1001l, 0, 5, 5, false, 0 ,235, Seq(pointAssetProperty), Some("testUser"), Some(DateTime.now), None, None, linkSource = LinkGeomSource.NormalLinkInterface),
        PedestrianCrossing(2l, 1001l, 0, 10, 10, false, 0 ,235, Seq(pointAssetProperty), Some("testUser"), Some(DateTime.now), None, None, linkSource = LinkGeomSource.NormalLinkInterface),
        PedestrianCrossing(3l, 1001l, 0, 30, 30, false, 0 ,235, Seq(pointAssetProperty), Some("testUser"), Some(DateTime.now), None, None, linkSource = LinkGeomSource.NormalLinkInterface),
        PedestrianCrossing(4l, 1001l, 0, 40, 40, false, 0 ,235, Seq(pointAssetProperty), Some("testUser"), Some(DateTime.now), None, None, linkSource = LinkGeomSource.NormalLinkInterface))

      val propTrafficSign = Seq(TrafficSignProperty(0, "trafficSigns_type", "", false, Seq(TextPropertyValue(PedestrianCrossingSign.OTHvalue.toString))))
      val trafficSign = PersistedTrafficSign(1, 1001l, 0, 25, 25, false, 0, 235, propTrafficSign, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)

      val result = pedestrianValidator.filteredAsset(roadLink, assets, Point(50.0, 0.0), 0, Some(trafficSign))
      result should have size 1
      result.head.id should be (2l)
      result.head.mValue should be (10)

      dynamicSession.rollback()
    }
  }

  test("Pedestrian crossing traffic (TowardsDigitizing) in roadLink filter nearest asset TowardDigitalization") {
    OracleDatabase.withDynTransaction {
      val pointAssetProperty = PointAssetProperty(111111, "pedestrian_crossings_suggest_box", "checkbox", false, Seq(TextPropertyValue("", None, false)))

      val roadLink = RoadLink(1001l, Seq(Point(0.0, 0.0), Point(50, 0.0)), 50, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val assets = Seq(
        PedestrianCrossing(1l, 1001l, 0, 5, 5, false, 0 ,235, Seq(pointAssetProperty), Some("testUser"), Some(DateTime.now), None, None, linkSource = LinkGeomSource.NormalLinkInterface),
        PedestrianCrossing(2l, 1001l, 0, 10, 10, false, 0 ,235, Seq(pointAssetProperty), Some("testUser"), Some(DateTime.now), None, None, linkSource = LinkGeomSource.NormalLinkInterface),
        PedestrianCrossing(3l, 1001l, 0, 30, 30, false, 0 ,235, Seq(pointAssetProperty), Some("testUser"), Some(DateTime.now), None, None, linkSource = LinkGeomSource.NormalLinkInterface),
        PedestrianCrossing(4l, 1001l, 0, 40, 40, false, 0 ,235, Seq(pointAssetProperty), Some("testUser"), Some(DateTime.now), None, None, linkSource = LinkGeomSource.NormalLinkInterface))

      val propTrafficSign = Seq(TrafficSignProperty(0, "trafficSigns_type", "", false, Seq(TextPropertyValue(PedestrianCrossingSign.OTHvalue.toString))))
      val trafficSign = PersistedTrafficSign(1, 1001l, 0, 25, 25, false, 0, 235, propTrafficSign, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)

      val result = pedestrianValidator.filteredAsset(roadLink, assets, Point(0, 0.0), 0, Some(trafficSign))
      result should have size 1
      result.head.id should be (3l)
      result.head.mValue should be (30)

      dynamicSession.rollback()
    }
  }




}
