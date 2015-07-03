package fi.liikennevirasto.digiroad2

import org.scalatest.mock.MockitoSugar
import org.scalatest.{BeforeAndAfter, FunSuite, Matchers}
import org.mockito.Matchers._
import org.mockito.Mockito._

import fi.liikennevirasto.digiroad2.asset._
import com.jolbox.bonecp.{BoneCPConfig, BoneCPDataSource}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import scala.slick.driver.JdbcDriver.backend.Database
import scala.slick.driver.JdbcDriver.backend.Database.dynamicSession
import scala.slick.jdbc.StaticQuery.interpolation
import scala.slick.jdbc.{GetResult, PositionedResult, StaticQuery => Q}

class RoadLinkServiceSpec extends FunSuite with Matchers with BeforeAndAfter {

  class TestService(vvhClient: VVHClient, eventBus: DigiroadEventBus = new DummyEventBus) extends VVHRoadLinkService(vvhClient, eventBus) {
    override def withDynTransaction[T](f: => T): T = f
    override def withDynSession[T](f: => T): T = f
  }

  test("Get production road link with test id that maps to one production road link") {
    RoadLinkService.getByTestIdAndMeasure(48l, 50.0).map(_._1) should be (Some(57))
    RoadLinkService.getByTestIdAndMeasure(48l, 50.0).map(_._2) should be (Some(18))
  }

  test("Get production road link with test id that doesn't map to production") {
    RoadLinkService.getByTestIdAndMeasure(1414l, 50.0) should be (None)
  }

  test("Get production road link with test id that maps to several links in production") {
    RoadLinkService.getByTestIdAndMeasure(147298l, 50.0) should be (None)
  }

  test("Override road link traffic direction with adjusted value") {
    val boundingBox = BoundingRectangle(Point(373816, 6676812), Point(374634, 6677671))
    val roadLinks = RoadLinkService.getRoadLinks(boundingBox)
    roadLinks.find { _.id == 7886262 }.map(_.trafficDirection) should be (Some(TowardsDigitizing))
    roadLinks.find { _.mmlId == 391203482 }.map(_.trafficDirection) should be (Some(AgainstDigitizing))
  }

  test("Override road link functional class with adjusted value") {
    val boundingBox = BoundingRectangle(Point(373816, 6676812), Point(374634, 6677671))
    val roadLinks = RoadLinkService.getRoadLinks(boundingBox)
    roadLinks.find { _.id == 7886262}.map(_.functionalClass) should be(Some(5))
    roadLinks.find {_.mmlId == 391203482}.map(_.functionalClass) should be(Some(4))
  }

  test("Overriden road link adjustments return latest modification") {
    val roadLink = RoadLinkService.getRoadLink(7886262)
    roadLink.modifiedAt should be (Some("12.12.2014 00:00:00"))
    roadLink.modifiedBy should be (Some("test"))
  }

  test("Adjust link type") {
    Database.forDataSource(OracleDatabase.ds).withDynTransaction {
      val mockVVHClient = MockitoSugar.mock[VVHClient]
      when(mockVVHClient.fetchVVHRoadlink(1l))
        .thenReturn(Some(VVHRoadlink(1l, 91, Nil, Municipality, UnknownDirection, FeatureClass.AllOthers)))
      val service = new TestService(mockVVHClient)
      val roadLink = service.updateProperties(1, 5, PedestrianZone, BothDirections, "testuser", { _ => })
      roadLink.map(_.linkType) should be(Some(PedestrianZone))
      dynamicSession.rollback()
    }
  }

  test("Adjust link traffic direction to value that is in VVH") {
    Database.forDataSource(OracleDatabase.ds).withDynTransaction {
      val mockVVHClient = MockitoSugar.mock[VVHClient]
      when(mockVVHClient.fetchVVHRoadlink(1l))
        .thenReturn(Some(VVHRoadlink(1l, 91, Nil, Municipality, UnknownDirection, FeatureClass.AllOthers)))
      val service = new TestService(mockVVHClient)
      val roadLink = service.updateProperties(1, 5, PedestrianZone, BothDirections, "testuser", { _ => })
      roadLink.map(_.trafficDirection) should be(Some(BothDirections))
      val roadLink2 = service.updateProperties(1, 5, PedestrianZone, TowardsDigitizing, "testuser", { _ => })
      roadLink2.map(_.trafficDirection) should be(Some(TowardsDigitizing))
      dynamicSession.rollback()
    }
  }

  test("Adjust non-existent road link") {
    val mockVVHClient = MockitoSugar.mock[VVHClient]
    when(mockVVHClient.fetchVVHRoadlink(1l)).thenReturn(None)
    val service = new VVHRoadLinkService(mockVVHClient, new DummyEventBus)
    val roadLink = service.updateProperties(1, 5, PedestrianZone, BothDirections, "testuser", { _ => })
    roadLink.map(_.linkType) should be(None)
  }

  test("Validate access rights to municipality") {
    Database.forDataSource(OracleDatabase.ds).withDynTransaction {
      val mockVVHClient = MockitoSugar.mock[VVHClient]
      when(mockVVHClient.fetchVVHRoadlink(1l))
        .thenReturn(Some(VVHRoadlink(1l, 91, Nil, Municipality, UnknownDirection, FeatureClass.AllOthers)))
      val service = new TestService(mockVVHClient)
      var validatedCode = 0
      service.updateProperties(1, 5, PedestrianZone, BothDirections, "testuser", { municipalityCode =>
        validatedCode = municipalityCode
      })
      validatedCode should be(91)
      dynamicSession.rollback()
    }
  }


  test("Autogenerate properties for tractor road and drive path") {
    Database.forDataSource(OracleDatabase.ds).withDynTransaction {
      val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
      val mockVVHClient = MockitoSugar.mock[VVHClient]
      when(mockVVHClient.fetchVVHRoadlinks(boundingBox, Set()))
        .thenReturn(List(
        VVHRoadlink(123l, 91, Nil, Municipality, TowardsDigitizing, FeatureClass.DrivePath),
        VVHRoadlink(456l, 91, Nil, Municipality, TowardsDigitizing, FeatureClass.TractorRoad),
        VVHRoadlink(789l, 91, Nil, Municipality, TowardsDigitizing, FeatureClass.AllOthers),
        VVHRoadlink(111l, 91, Nil, Municipality, TowardsDigitizing, FeatureClass.CycleOrPedestrianPath)))
      val service = new TestService(mockVVHClient)

      sqlu"""delete from incomplete_link where municipality_code = 91""".execute()
      sqlu"""insert into incomplete_link(mml_id, municipality_code) values(456, 91)""".execute()
      val roadLinks = service.getRoadLinksFromVVH(boundingBox)

      roadLinks.find(_.mmlId == 123).get.functionalClass should be(6)
      roadLinks.find(_.mmlId == 123).get.linkType should be(SingleCarriageway)

      roadLinks.find(_.mmlId == 456).get.functionalClass should be(7)
      roadLinks.find(_.mmlId == 456).get.linkType should be(TractorRoad)

      roadLinks.find(_.mmlId == 789).get.functionalClass should be(FunctionalClass.Unknown)
      roadLinks.find(_.mmlId == 789).get.linkType should be(UnknownLinkType)

      roadLinks.find(_.mmlId == 111).get.functionalClass should be(8)
      roadLinks.find(_.mmlId == 111).get.linkType should be(CycleOrPedestrianPath)

      dynamicSession.rollback()
    }
  }

  test("Changes should cause event") {
    Database.forDataSource(OracleDatabase.ds).withDynTransaction {
      val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
      val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
      val mockVVHClient = MockitoSugar.mock[VVHClient]
      when(mockVVHClient.fetchVVHRoadlinks(boundingBox, Set()))
        .thenReturn(List(
        VVHRoadlink(123l, 91, Nil, Municipality, TowardsDigitizing, FeatureClass.DrivePath),
        VVHRoadlink(789l, 91, Nil, Municipality, TowardsDigitizing, FeatureClass.AllOthers)))

      val service = new TestService(mockVVHClient, mockEventBus)
      val roadLink: List[VVHRoadLinkWithProperties] = List(VVHRoadLinkWithProperties(123, List(), 0.0, Municipality, 6, TowardsDigitizing, SingleCarriageway, None, None))

      val changeSet: RoadLinkChangeSet = RoadLinkChangeSet(roadLink, List(IncompleteLink(789,91,Municipality)))

      service.getRoadLinksFromVVH(boundingBox)

      verify(mockEventBus).publish(
        org.mockito.Matchers.eq("linkProperties:changed"),
        org.mockito.Matchers.eq(changeSet))

      dynamicSession.rollback()
    }
  }

  test("Remove road link from incomplete link list once functional class and link type are specified") {
    Database.forDataSource(OracleDatabase.ds).withDynTransaction {
      val mockVVHClient = MockitoSugar.mock[VVHClient]
      val roadLink = VVHRoadlink(1l, 91, Nil, Municipality, TowardsDigitizing, FeatureClass.AllOthers)
      when(mockVVHClient.fetchVVHRoadlink(1l)).thenReturn(Some(roadLink))
      val service = new TestService(mockVVHClient)

      sqlu"""insert into incomplete_link (mml_id, municipality_code, administrative_class) values (1, 91, 1)""".execute()

      service.updateProperties(1, FunctionalClass.Unknown, Freeway, BothDirections, "test", _ => ())
      service.updateProperties(1, 4, UnknownLinkType, BothDirections, "test", _ => ())

      val incompleteLinks = service.getIncompleteLinks(Some(Set(91)))
      incompleteLinks should be(empty)

      dynamicSession.rollback()
    }
  }
}
