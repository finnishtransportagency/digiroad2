package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.Asset._
import fi.liikennevirasto.digiroad2.FeatureClass.AllOthers
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import org.joda.time.DateTime
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{BeforeAndAfter, FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{StaticQuery => Q}

class RoadLinkServiceSpec extends FunSuite with Matchers with BeforeAndAfter {

  class TestService(vvhClient: VVHClient, eventBus: DigiroadEventBus = new DummyEventBus) extends VVHRoadLinkService(vvhClient, eventBus) {
    override def withDynTransaction[T](f: => T): T = f
    override def withDynSession[T](f: => T): T = f
  }

  private def simulateQuery[T](f: => T): T = {
    val result = f
    sqlu"""delete from temp_id""".execute
    result
  }

  test("Override road link traffic direction with adjusted value") {
    OracleDatabase.withDynTransaction {
      val mockVVHClient = MockitoSugar.mock[VVHClient]
      when(mockVVHClient.fetchVVHRoadlinks(Set(391203482l)))
        .thenReturn(Seq(VVHRoadlink(391203482l, 91, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
      val service = new TestService(mockVVHClient)
      val roadLinks = service.getRoadLinksFromVVH(Set(391203482l))
      roadLinks.find {
        _.mmlId == 391203482
      }.map(_.trafficDirection) should be(Some(TrafficDirection.AgainstDigitizing))
      dynamicSession.rollback()
    }
  }

  test("Include road link functional class with adjusted value") {
    OracleDatabase.withDynTransaction {
      val mockVVHClient = MockitoSugar.mock[VVHClient]
      when(mockVVHClient.fetchVVHRoadlinks(Set(391203482l)))
        .thenReturn(Seq(VVHRoadlink(391203482l, 91, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
      val service = new TestService(mockVVHClient)
      val roadLinks = service.getRoadLinksFromVVH(Set(391203482l))
      println(roadLinks)
      roadLinks.find {_.mmlId == 391203482}.map(_.functionalClass) should be(Some(4))
      dynamicSession.rollback()
    }
  }

  test("Adjust link type") {
    OracleDatabase.withDynTransaction {
      val mockVVHClient = MockitoSugar.mock[VVHClient]
      when(mockVVHClient.fetchVVHRoadlink(1l))
        .thenReturn(Some(VVHRoadlink(1l, 91, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
      val service = new TestService(mockVVHClient)
      val roadLink = service.updateProperties(1, 5, PedestrianZone, TrafficDirection.BothDirections, "testuser", { _ => })
      roadLink.map(_.linkType) should be(Some(PedestrianZone))
      dynamicSession.rollback()
    }
  }

  test("Provide last edited date from VVH on road link modification date if there are no overrides") {
    OracleDatabase.withDynTransaction {
      val mockVVHClient = MockitoSugar.mock[VVHClient]
      val lastEditedDate = DateTime.now()
      val roadLinks = Seq(VVHRoadlink(1l, 0, Nil, Municipality, TrafficDirection.TowardsDigitizing, AllOthers, Some(lastEditedDate)))
      when(mockVVHClient.fetchVVHRoadlinks(any[BoundingRectangle], any[Set[Int]])).thenReturn(roadLinks)
      val service = new TestService(mockVVHClient)
      val results = service.getRoadLinksFromVVH(BoundingRectangle(Point(0.0, 0.0), Point(1.0, 1.0)))
      results.head.modifiedAt should be(Some(DateTimePropertyFormat.print(lastEditedDate)))
      dynamicSession.rollback()
    }
  }

  test("Adjust link traffic direction to value that is in VVH") {
    OracleDatabase.withDynTransaction {
      val mockVVHClient = MockitoSugar.mock[VVHClient]
      when(mockVVHClient.fetchVVHRoadlink(1l))
        .thenReturn(Some(VVHRoadlink(1l, 91, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
      val service = new TestService(mockVVHClient)
      val roadLink = simulateQuery {
        service.updateProperties(1, 5, PedestrianZone, TrafficDirection.BothDirections, "testuser", { _ => })
      }
      roadLink.map(_.trafficDirection) should be(Some(TrafficDirection.BothDirections))
      val roadLink2 = simulateQuery {
        service.updateProperties(1, 5, PedestrianZone, TrafficDirection.TowardsDigitizing, "testuser", { _ => })
      }
      roadLink2.map(_.trafficDirection) should be(Some(TrafficDirection.TowardsDigitizing))
      dynamicSession.rollback()
    }
  }

  test("Adjust non-existent road link") {
    val mockVVHClient = MockitoSugar.mock[VVHClient]
    when(mockVVHClient.fetchVVHRoadlink(1l)).thenReturn(None)
    val service = new VVHRoadLinkService(mockVVHClient, new DummyEventBus)
    val roadLink = service.updateProperties(1, 5, PedestrianZone, TrafficDirection.BothDirections, "testuser", { _ => })
    roadLink.map(_.linkType) should be(None)
  }

  test("Validate access rights to municipality") {
    OracleDatabase.withDynTransaction {
      val mockVVHClient = MockitoSugar.mock[VVHClient]
      when(mockVVHClient.fetchVVHRoadlink(1l))
        .thenReturn(Some(VVHRoadlink(1l, 91, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
      val service = new TestService(mockVVHClient)
      var validatedCode = 0
      service.updateProperties(1, 5, PedestrianZone, TrafficDirection.BothDirections, "testuser", { municipalityCode =>
        validatedCode = municipalityCode
      })
      validatedCode should be(91)
      dynamicSession.rollback()
    }
  }


  test("Autogenerate properties for tractor road and drive path") {
    OracleDatabase.withDynTransaction {
      val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
      val mockVVHClient = MockitoSugar.mock[VVHClient]
      when(mockVVHClient.fetchVVHRoadlinks(boundingBox, Set()))
        .thenReturn(List(
        VVHRoadlink(123l, 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.DrivePath),
        VVHRoadlink(456l, 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.TractorRoad),
        VVHRoadlink(789l, 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers),
        VVHRoadlink(111l, 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.CycleOrPedestrianPath)))
      val service = new TestService(mockVVHClient)

      sqlu"""delete from incomplete_link where municipality_code = 91""".execute
      sqlu"""insert into incomplete_link(mml_id, municipality_code) values(456, 91)""".execute
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
    OracleDatabase.withDynTransaction {
      val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
      val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
      val mockVVHClient = MockitoSugar.mock[VVHClient]
      when(mockVVHClient.fetchVVHRoadlinks(boundingBox, Set()))
        .thenReturn(List(
        VVHRoadlink(123l, 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.DrivePath),
        VVHRoadlink(789l, 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers)))

      val service = new TestService(mockVVHClient, mockEventBus)
      val roadLink: List[RoadLink] = List(RoadLink(123, List(), 0.0, Municipality, 6, TrafficDirection.TowardsDigitizing, SingleCarriageway, None, None))

      val changeSet: RoadLinkChangeSet = RoadLinkChangeSet(roadLink, List(IncompleteLink(789,91,Municipality)))

      service.getRoadLinksFromVVH(boundingBox)

      verify(mockEventBus).publish(
        org.mockito.Matchers.eq("linkProperties:changed"),
        org.mockito.Matchers.eq(changeSet))

      dynamicSession.rollback()
    }
  }

  test("Remove road link from incomplete link list once functional class and link type are specified") {
    OracleDatabase.withDynTransaction {
      val mockVVHClient = MockitoSugar.mock[VVHClient]
      val roadLink = VVHRoadlink(1l, 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers)
      when(mockVVHClient.fetchVVHRoadlink(1l)).thenReturn(Some(roadLink))
      val service = new TestService(mockVVHClient)

      sqlu"""insert into incomplete_link (mml_id, municipality_code, administrative_class) values (1, 91, 1)""".execute

      simulateQuery { service.updateProperties(1, FunctionalClass.Unknown, Freeway, TrafficDirection.BothDirections, "test", _ => ()) }
      simulateQuery { service.updateProperties(1, 4, UnknownLinkType, TrafficDirection.BothDirections, "test", _ => ()) }

      val incompleteLinks = service.getIncompleteLinks(Some(Set(91)))
      incompleteLinks should be(empty)

      dynamicSession.rollback()
    }
  }
}
