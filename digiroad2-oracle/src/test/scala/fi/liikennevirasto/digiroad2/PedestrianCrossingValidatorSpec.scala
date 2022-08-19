package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.RoadLinkClient
import fi.liikennevirasto.digiroad2.dao.pointasset.{PedestrianCrossing, PersistedTrafficSign, PostGISPedestrianCrossingDao}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.process.PedestrianCrossingValidator
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.pointasset.TrafficSignService
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.util.LinkIdGenerator
import org.joda.time
import org.joda.time.DateTime

class PedestrianCrossingValidatorSpec extends FunSuite with Matchers{
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
  val mockTrafficSignService = MockitoSugar.mock[TrafficSignService]
  val mockPedestrianCrossingDao: PostGISPedestrianCrossingDao = MockitoSugar.mock[PostGISPedestrianCrossingDao]


  class TestPedestrianCrossingValidator extends PedestrianCrossingValidator {
    override lazy val dao: PostGISPedestrianCrossingDao = mockPedestrianCrossingDao
    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
    override lazy val roadLinkClient: RoadLinkClient = mockRoadLinkClient
  }

  val pedestrianValidator = new TestPedestrianCrossingValidator
  val typeId = PedestrianCrossings.typeId

  val linkId1: String = LinkIdGenerator.generateRandom()
  val linkId2: String = LinkIdGenerator.generateRandom()
  val linkId3: String = LinkIdGenerator.generateRandom()
  val linkId4: String = LinkIdGenerator.generateRandom()

  val roadLink1 = RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(10, 0.0)), 10, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
  val roadLink2 = RoadLink(linkId2, Seq(Point(10.0, 0.0), Point(20, 0.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
  val roadLink3 = RoadLink(linkId3, Seq(Point(20.0, 0.0), Point(50.0, 0.0)), 30.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
  val roadLink4 = RoadLink(linkId4, Seq(Point(50.0, 0.0), Point(70.0, 0.0)), 20.0, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

  when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean],any[Boolean])).thenReturn(Seq(roadLink1, roadLink2, roadLink3))

  test("Pedestrian crossing traffic sign without match asset") {
    PostGISDatabase.withDynTransaction {

      val propTrafficSign = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(PedestrianCrossingSign.OTHvalue.toString))))
      val trafficSign = PersistedTrafficSign(1, linkId2, 2, 2, 2, false, 0, 235, propTrafficSign, None, None, None, None, SideCode.BothDirections.value, None, NormalLinkInterface)

      when(pedestrianValidator.dao.fetchPedestrianCrossingByLinkIds(Seq(linkId1,linkId2, linkId3))).thenReturn(Seq())

      val result = pedestrianValidator.assetValidator(trafficSign)
        result should have size 1
        result.head.linkId should be(Some(roadLink2.linkId))

      dynamicSession.rollback()
    }
  }

  test("Pedestrian crossing traffic sign have a correct asset") {
    PostGISDatabase.withDynTransaction {
      val propTrafficSign = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(PedestrianCrossingSign.OTHvalue.toString))))
      val trafficSign = PersistedTrafficSign(1, linkId2, 2, 0, 2, false, 0, 235, propTrafficSign, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)

      val pointAssetProperty = Property(111111, "suggest_box", "checkbox", false, Seq(PropertyValue("", None, false)))

      when(pedestrianValidator.dao.fetchPedestrianCrossingByLinkIds(Seq(linkId1, linkId2, linkId3)))
        .thenReturn(
          Seq(PedestrianCrossing(1, linkId3, 1.0, 1.0, 1.0, false, 0, 235, Seq(pointAssetProperty), linkSource = LinkGeomSource.NormalLinkInterface))
        )

      val result = pedestrianValidator.assetValidator(trafficSign)
        result should have size 0

      dynamicSession.rollback()
    }
  }

  test("Pedestrian crossing traffic sign without a match asset only after 50m") {
    PostGISDatabase.withDynTransaction {
      val propTrafficSign = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(PedestrianCrossingSign.OTHvalue.toString))))
      val trafficSign = PersistedTrafficSign(1, linkId2, 12, 0, 2, false, 0, 235, propTrafficSign, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)

      val pointAssetProperty = Property(111111, "suggest_box", "checkbox", false, Seq(PropertyValue("", None, false)))

      when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean],any[Boolean])).thenReturn(Seq(roadLink1, roadLink2, roadLink3, roadLink4))
      when(pedestrianValidator.dao.fetchPedestrianCrossingByLinkIds(Seq(linkId1,linkId2, linkId3, linkId4)))
        .thenReturn(
          Seq(PedestrianCrossing(1, linkId4, 13, 13, 13, false, 0, 235, Seq(pointAssetProperty), linkSource = LinkGeomSource.NormalLinkInterface))
        )

      val result = pedestrianValidator.assetValidator(trafficSign)
        result should have size 1
        result.head.linkId should be (Some(roadLink2.linkId))

      dynamicSession.rollback()
    }
  }

  test("Pedestrian crossing traffic filter nearest asset") {
    PostGISDatabase.withDynTransaction {

      val pointAssetProperty = Property(111111, "suggest_box", "checkbox", false, Seq(PropertyValue("", None, false)))

      val roadLink = RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(50, 0.0)), 50, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val assets = Seq(
        PedestrianCrossing(1l, linkId1, 0, 5, 5, false, 0 ,235, Seq(pointAssetProperty), Some("testUser"), Some(DateTime.now), None, None, linkSource = LinkGeomSource.NormalLinkInterface),
        PedestrianCrossing(2l, linkId1, 0, 10, 10, false, 0 ,235, Seq(pointAssetProperty), Some("testUser"), Some(DateTime.now), None, None, linkSource = LinkGeomSource.NormalLinkInterface),
        PedestrianCrossing(3l, linkId1, 0, 30, 30, false, 0 ,235, Seq(pointAssetProperty), Some("testUser"), Some(DateTime.now), None, None, linkSource = LinkGeomSource.NormalLinkInterface),
        PedestrianCrossing(4l, linkId1, 0, 40, 40, false, 0 ,235, Seq(pointAssetProperty), Some("testUser"), Some(DateTime.now), None, None, linkSource = LinkGeomSource.NormalLinkInterface))

      val result = pedestrianValidator.filteredAsset(roadLink, assets, Point(0, 0.0), 0)
      result should have size 1
      result.head.id should be (1l)
      result.head.mValue should be (5)

      dynamicSession.rollback()
    }
  }

  test("Pedestrian crossing traffic filter further asset") {
    PostGISDatabase.withDynTransaction {

      val pointAssetProperty = Property(111111, "suggest_box", "checkbox", false, Seq(PropertyValue("", None, false)))

      val roadLink = RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(50, 0.0)), 50, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val assets = Seq(
        PedestrianCrossing(1l, linkId1, 0, 5, 5, false, 0 ,235, Seq(pointAssetProperty), Some("testUser"), Some(DateTime.now), None, None, linkSource = LinkGeomSource.NormalLinkInterface),
        PedestrianCrossing(2l, linkId1, 0, 10, 10, false, 0 ,235, Seq(pointAssetProperty), Some("testUser"), Some(DateTime.now), None, None, linkSource = LinkGeomSource.NormalLinkInterface),
        PedestrianCrossing(3l, linkId1, 0, 30, 30, false, 0 ,235, Seq(pointAssetProperty), Some("testUser"), Some(DateTime.now), None, None, linkSource = LinkGeomSource.NormalLinkInterface),
        PedestrianCrossing(4l, linkId1, 0, 40, 40, false, 0 ,235, Seq(pointAssetProperty), Some("testUser"), Some(DateTime.now), None, None, linkSource = LinkGeomSource.NormalLinkInterface))

      val result = pedestrianValidator.filteredAsset(roadLink, assets, Point(50, 0.0), 0)
      result should have size 1
      result.head.id should be (4l)
      result.head.mValue should be (40)

      dynamicSession.rollback()
    }
  }

  test("Pedestrian crossing traffic (AgainstDigitizing) in roadLink filter nearest asset TowardDigitalization") {
    PostGISDatabase.withDynTransaction {

      val pointAssetProperty = Property(111111, "suggest_box", "checkbox", false, Seq(PropertyValue("", None, false)))

      val roadLink = RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(50, 0.0)), 50, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val assets = Seq(
        PedestrianCrossing(1l, linkId1, 0, 5, 5, false, 0 ,235, Seq(pointAssetProperty), Some("testUser"), Some(DateTime.now), None, None, linkSource = LinkGeomSource.NormalLinkInterface),
        PedestrianCrossing(2l, linkId1, 0, 10, 10, false, 0 ,235, Seq(pointAssetProperty), Some("testUser"), Some(DateTime.now), None, None, linkSource = LinkGeomSource.NormalLinkInterface),
        PedestrianCrossing(3l, linkId1, 0, 30, 30, false, 0 ,235, Seq(pointAssetProperty), Some("testUser"), Some(DateTime.now), None, None, linkSource = LinkGeomSource.NormalLinkInterface),
        PedestrianCrossing(4l, linkId1, 0, 40, 40, false, 0 ,235, Seq(pointAssetProperty), Some("testUser"), Some(DateTime.now), None, None, linkSource = LinkGeomSource.NormalLinkInterface))

      val propTrafficSign = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(PedestrianCrossingSign.OTHvalue.toString))))
      val trafficSign = PersistedTrafficSign(1, linkId1, 0, 25, 25, false, 0, 235, propTrafficSign, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)

      val result = pedestrianValidator.filteredAsset(roadLink, assets, Point(0.0, 0.0), 0, Some(trafficSign))
      result should have size 1
      result.head.id should be (2l)
      result.head.mValue should be (10)

      dynamicSession.rollback()
    }
  }

  test("Pedestrian crossing traffic (TowardsDigitizing) in roadLink filter nearest asset AgainstDigitizing") {
    PostGISDatabase.withDynTransaction {
      val pointAssetProperty = Property(111111, "suggest_box", "checkbox", false, Seq(PropertyValue("", None, false)))

      val roadLink = RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(50, 0.0)), 50, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val assets = Seq(
        PedestrianCrossing(1l, linkId1, 0, 5, 5, false, 0 ,235, Seq(pointAssetProperty), Some("testUser"), Some(DateTime.now), None, None, linkSource = LinkGeomSource.NormalLinkInterface),
        PedestrianCrossing(2l, linkId1, 0, 10, 10, false, 0 ,235, Seq(pointAssetProperty), Some("testUser"), Some(DateTime.now), None, None, linkSource = LinkGeomSource.NormalLinkInterface),
        PedestrianCrossing(3l, linkId1, 0, 30, 30, false, 0 ,235, Seq(pointAssetProperty), Some("testUser"), Some(DateTime.now), None, None, linkSource = LinkGeomSource.NormalLinkInterface),
        PedestrianCrossing(4l, linkId1, 0, 40, 40, false, 0 ,235, Seq(pointAssetProperty), Some("testUser"), Some(DateTime.now), None, None, linkSource = LinkGeomSource.NormalLinkInterface))

      val propTrafficSign = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(PedestrianCrossingSign.OTHvalue.toString))))
      val trafficSign = PersistedTrafficSign(1, linkId1, 0, 25, 25, false, 0, 235, propTrafficSign, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)

      val result = pedestrianValidator.filteredAsset(roadLink, assets, Point(0, 0.0), 0, Some(trafficSign))
      result should have size 1
      result.head.id should be (3l)
      result.head.mValue should be (30)

      dynamicSession.rollback()
    }
  }

  test("Pedestrian crossing traffic (AgainstDigitizing) in roadLink filter nearest asset AgainstDigitizing") {
    PostGISDatabase.withDynTransaction {
      val pointAssetProperty = Property(111111, "suggest_box", "checkbox", false, Seq(PropertyValue("", None, false)))

      val roadLink = RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(50, 0.0)), 50, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val assets = Seq(
        PedestrianCrossing(1l, linkId1, 0, 5, 5, false, 0 ,235, Seq(pointAssetProperty), Some("testUser"), Some(DateTime.now), None, None, linkSource = LinkGeomSource.NormalLinkInterface),
        PedestrianCrossing(2l, linkId1, 0, 10, 10, false, 0 ,235, Seq(pointAssetProperty), Some("testUser"), Some(DateTime.now), None, None, linkSource = LinkGeomSource.NormalLinkInterface),
        PedestrianCrossing(3l, linkId1, 0, 30, 30, false, 0 ,235, Seq(pointAssetProperty), Some("testUser"), Some(DateTime.now), None, None, linkSource = LinkGeomSource.NormalLinkInterface),
        PedestrianCrossing(4l, linkId1, 0, 40, 40, false, 0 ,235, Seq(pointAssetProperty), Some("testUser"), Some(DateTime.now), None, None, linkSource = LinkGeomSource.NormalLinkInterface))

      val propTrafficSign = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(PedestrianCrossingSign.OTHvalue.toString))))
      val trafficSign = PersistedTrafficSign(1, linkId1, 0, 25, 25, false, 0, 235, propTrafficSign, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)

      val result = pedestrianValidator.filteredAsset(roadLink, assets, Point(50.0, 0.0), 0, Some(trafficSign))
      result should have size 1
      result.head.id should be (2l)
      result.head.mValue should be (10)

      dynamicSession.rollback()
    }
  }

  test("Pedestrian crossing traffic (TowardsDigitizing) in roadLink filter nearest asset TowardDigitalization") {
    PostGISDatabase.withDynTransaction {
      val pointAssetProperty = Property(111111, "suggest_box", "checkbox", false, Seq(PropertyValue("", None, false)))

      val roadLink = RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(50, 0.0)), 50, Municipality, 1, TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      val assets = Seq(
        PedestrianCrossing(1l, linkId1, 0, 5, 5, false, 0 ,235, Seq(pointAssetProperty), Some("testUser"), Some(DateTime.now), None, None, linkSource = LinkGeomSource.NormalLinkInterface),
        PedestrianCrossing(2l, linkId1, 0, 10, 10, false, 0 ,235, Seq(pointAssetProperty), Some("testUser"), Some(DateTime.now), None, None, linkSource = LinkGeomSource.NormalLinkInterface),
        PedestrianCrossing(3l, linkId1, 0, 30, 30, false, 0 ,235, Seq(pointAssetProperty), Some("testUser"), Some(DateTime.now), None, None, linkSource = LinkGeomSource.NormalLinkInterface),
        PedestrianCrossing(4l, linkId1, 0, 40, 40, false, 0 ,235, Seq(pointAssetProperty), Some("testUser"), Some(DateTime.now), None, None, linkSource = LinkGeomSource.NormalLinkInterface))

      val propTrafficSign = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(PedestrianCrossingSign.OTHvalue.toString))))
      val trafficSign = PersistedTrafficSign(1, linkId1, 0, 25, 25, false, 0, 235, propTrafficSign, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)

      val result = pedestrianValidator.filteredAsset(roadLink, assets, Point(0, 0.0), 0, Some(trafficSign))
      result should have size 1
      result.head.id should be (3l)
      result.head.mValue should be (30)

      dynamicSession.rollback()
    }
  }




}
