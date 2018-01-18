package fi.liikennevirasto.digiroad2.service

import fi.liikennevirasto.digiroad2.asset.Asset._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.FeatureClass.AllOthers
import fi.liikennevirasto.digiroad2.client.vvh._
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.util.VVHSerializer
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, DummyEventBus, DummySerializer, Point}
import org.joda.time.DateTime
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{BeforeAndAfter, FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{StaticQuery => Q}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}

class RoadLinkServiceSpec extends FunSuite with Matchers with BeforeAndAfter {

  class TestService(vvhClient: VVHClient, eventBus: DigiroadEventBus = new DummyEventBus, vvhSerializer: VVHSerializer = new DummySerializer) extends RoadLinkService(vvhClient, eventBus, vvhSerializer) {
    override def withDynTransaction[T](f: => T): T = f
    override def withDynSession[T](f: => T): T = f
  }

  class RoadLinkTestService(vvhClient: VVHClient, eventBus: DigiroadEventBus = new DummyEventBus, vvhSerializer: VVHSerializer = new DummySerializer) extends RoadLinkOTHService(vvhClient, eventBus, vvhSerializer) {
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
      val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHRoadLinkClient.fetchByLinkIds(Set(1611447l)))
        .thenReturn(Seq(VVHRoadlink(1611447, 91, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
      val service = new TestService(mockVVHClient)
      val roadLinks = service.getRoadLinksByLinkIdsFromVVH(Set(1611447l))
      roadLinks.find {
        _.linkId == 1611447
      }.map(_.trafficDirection) should be(Some(TrafficDirection.AgainstDigitizing))
      dynamicSession.rollback()
    }
  }

  test("Include road link functional class with adjusted value") {
    OracleDatabase.withDynTransaction {
      val mockVVHClient = MockitoSugar.mock[VVHClient]
      val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHRoadLinkClient.fetchByLinkIds(Set(1611447l)))
        .thenReturn(Seq(VVHRoadlink(1611447, 91, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
      val service = new TestService(mockVVHClient)
      val roadLinks = service.getRoadLinksByLinkIdsFromVVH(Set(1611447l))
      roadLinks.find {_.linkId == 1611447}.map(_.functionalClass) should be(Some(4))
      dynamicSession.rollback()
    }
  }

  test("Modified traffic Direction in a Complementary RoadLink") {
    OracleDatabase.withDynTransaction {
      val oldRoadLink = VVHRoadlink(30, 235, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, attributes = Map("MTKCLASS" -> BigInt(12314)))
      val mockVVHClient = MockitoSugar.mock[VVHClient]
      val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]

      val mockVVHComplementaryClient = MockitoSugar.mock[VVHComplementaryClient]
      val service = new TestService(mockVVHClient)

      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHRoadLinkClient.fetchByLinkId(30)).thenReturn(None)
      when(mockVVHClient.complementaryData).thenReturn(mockVVHComplementaryClient)
      when(mockVVHComplementaryClient.fetchByLinkId(30)).thenReturn(Some(oldRoadLink))

      val roadLink = service.updateLinkProperties(30, 8, CycleOrPedestrianPath, TrafficDirection.BothDirections, Municipality, Option("testuser"), { _ => })
      roadLink.map(_.trafficDirection) should be(Some(TrafficDirection.BothDirections))
      roadLink.map(_.attributes("MTKCLASS")) should be (Some(12314))
      dynamicSession.rollback()
    }
  }

  test("Adjust link type") {
    OracleDatabase.withDynTransaction {
      val mockVVHClient = MockitoSugar.mock[VVHClient]
      val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]

      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHRoadLinkClient.fetchByLinkId(1l))
        .thenReturn(Some(VVHRoadlink(1l, 91, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
      val service = new TestService(mockVVHClient)
      val roadLink = service.updateLinkProperties(1, 5, PedestrianZone, TrafficDirection.BothDirections, Municipality, Option("testuser"), { _ => })
      roadLink.map(_.linkType) should be(Some(PedestrianZone))
      dynamicSession.rollback()
    }
  }

  test("Override administrative class") {
    OracleDatabase.withDynTransaction {
      val mockVVHClient = MockitoSugar.mock[VVHClient]
      val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]

      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHRoadLinkClient.fetchByLinkId(1l))
        .thenReturn(Some(VVHRoadlink(1l, 91, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
      val service = new TestService(mockVVHClient)
      val roadLink = service.updateLinkProperties(1, 5, PedestrianZone, TrafficDirection.UnknownDirection, Private, Option("testuser"), { _ => })
      roadLink.map(_.administrativeClass) should be(Some(Private))
      dynamicSession.rollback()
    }
  }

  test("Provide last edited date from VVH on road link modification date if there are no overrides") {
    OracleDatabase.withDynTransaction {
      val mockVVHClient = MockitoSugar.mock[VVHClient]
      val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
      val mockVVHChangeInfoClient = MockitoSugar.mock[VVHChangeInfoClient]

      val lastEditedDate = DateTime.now()
      val roadLinks = Seq(VVHRoadlink(1l, 0, Nil, Municipality, TrafficDirection.TowardsDigitizing, AllOthers, Some(lastEditedDate)))
      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHClient.roadLinkChangeInfo).thenReturn(mockVVHChangeInfoClient)
      when(mockVVHRoadLinkClient.fetchByMunicipalitiesAndBoundsF(any[BoundingRectangle], any[Set[Int]])).thenReturn(Promise.successful(roadLinks).future)
      when(mockVVHChangeInfoClient.fetchByBoundsAndMunicipalitiesF(any[BoundingRectangle], any[Set[Int]])).thenReturn(Promise.successful(Nil).future)

      val service = new TestService(mockVVHClient)
      val results = service.getRoadLinksFromVVH(BoundingRectangle(Point(0.0, 0.0), Point(1.0, 1.0)))
      results.head.modifiedAt should be(Some(DateTimePropertyFormat.print(lastEditedDate)))
      dynamicSession.rollback()
    }
  }

  test("Adjust link traffic direction to value that is in VVH") {
    OracleDatabase.withDynTransaction {
      val mockVVHClient = MockitoSugar.mock[VVHClient]
      val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]

      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHRoadLinkClient.fetchByLinkId(1l))
        .thenReturn(Some(VVHRoadlink(1l, 91, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
      val service = new TestService(mockVVHClient)
      val roadLink = simulateQuery {
        service.updateLinkProperties(1, 5, PedestrianZone, TrafficDirection.BothDirections, Municipality, Option("testuser"), { _ => })
      }
      roadLink.map(_.trafficDirection) should be(Some(TrafficDirection.BothDirections))
      val roadLink2 = simulateQuery {
        service.updateLinkProperties(1, 5, PedestrianZone, TrafficDirection.TowardsDigitizing, Municipality, Option("testuser"), { _ => })
      }
      roadLink2.map(_.trafficDirection) should be(Some(TrafficDirection.TowardsDigitizing))
      dynamicSession.rollback()
    }
  }

  test("Adjust non-existent road link") {
    val mockVVHClient = MockitoSugar.mock[VVHClient]
    val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
    val mockVVHComplementaryClient = MockitoSugar.mock[VVHComplementaryClient]

    when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
    when(mockVVHRoadLinkClient.fetchByLinkId(1l)).thenReturn(None)
    when(mockVVHClient.complementaryData).thenReturn(mockVVHComplementaryClient)
    when(mockVVHComplementaryClient.fetchByLinkId(1l)).thenReturn(None)

    val service = new RoadLinkOTHService(mockVVHClient, new DummyEventBus, new DummySerializer)
    val roadLink = service.updateLinkProperties(1, 5, PedestrianZone, TrafficDirection.BothDirections, Municipality, Option("testuser"), { _ => })
    roadLink.map(_.linkType) should be(None)
  }

  test("Validate access rights to municipality") {
    OracleDatabase.withDynTransaction {
      val mockVVHClient = MockitoSugar.mock[VVHClient]
      val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]

      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHRoadLinkClient.fetchByLinkId(1l))
        .thenReturn(Some(VVHRoadlink(1l, 91, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
      val service = new TestService(mockVVHClient)
      var validatedCode = 0
      service.updateLinkProperties(1, 5, PedestrianZone, TrafficDirection.BothDirections, Municipality, Option("testuser"), { municipalityCode =>
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
      val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
      val mockVVHChangeInfoClient = MockitoSugar.mock[VVHChangeInfoClient]

      val service = new TestService(mockVVHClient)
      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHClient.roadLinkChangeInfo).thenReturn(mockVVHChangeInfoClient)
      when(mockVVHChangeInfoClient.fetchByBoundsAndMunicipalitiesF(boundingBox, Set())).thenReturn(Promise.successful(Nil).future)
      when(mockVVHRoadLinkClient.fetchByMunicipalitiesAndBoundsF(boundingBox, Set()))
        .thenReturn(Promise.successful(List(
          VVHRoadlink(123l, 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.DrivePath),
          VVHRoadlink(456l, 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.TractorRoad),
          VVHRoadlink(789l, 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers),
          VVHRoadlink(111l, 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.CycleOrPedestrianPath))).future)

      sqlu"""delete from incomplete_link where municipality_code = 91""".execute
      sqlu"""insert into incomplete_link(id, link_id, municipality_code) values(3123123123, 456, 91)""".execute
      val roadLinks = service.getRoadLinksFromVVH(boundingBox)

      roadLinks.find(_.linkId == 123).get.functionalClass should be(6)
      roadLinks.find(_.linkId == 123).get.linkType should be(SingleCarriageway)

      roadLinks.find(_.linkId == 456).get.functionalClass should be(7)
      roadLinks.find(_.linkId == 456).get.linkType should be(TractorRoad)

      roadLinks.find(_.linkId == 789).get.functionalClass should be(FunctionalClass.Unknown)
      roadLinks.find(_.linkId == 789).get.linkType should be(UnknownLinkType)

      roadLinks.find(_.linkId == 111).get.functionalClass should be(8)
      roadLinks.find(_.linkId == 111).get.linkType should be(CycleOrPedestrianPath)

      dynamicSession.rollback()
    }
  }

  test("Changes should cause event") {
    OracleDatabase.withDynTransaction {
      val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
      val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
      val mockVVHClient = MockitoSugar.mock[VVHClient]
      val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
      val mockVVHChangeInfoClient = MockitoSugar.mock[VVHChangeInfoClient]

      val vvhRoadLinks = List(
        VVHRoadlink(123l, 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.DrivePath, linkSource = LinkGeomSource.NormalLinkInterface),
        VVHRoadlink(789l, 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, linkSource = LinkGeomSource.NormalLinkInterface))
      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHClient.roadLinkChangeInfo).thenReturn(mockVVHChangeInfoClient)
      when(mockVVHRoadLinkClient.fetchByMunicipalitiesAndBoundsF(boundingBox, Set())).thenReturn(Promise.successful(vvhRoadLinks).future)
      when(mockVVHChangeInfoClient.fetchByBoundsAndMunicipalitiesF(boundingBox, Set())).thenReturn(Promise.successful(Nil).future)

      val service = new TestService(mockVVHClient, mockEventBus)
      val result = service.getRoadLinksFromVVH(boundingBox)
      val exactModifiedAtValue = result.head.modifiedAt
      val roadLink: List[RoadLink] = List(RoadLink(123, List(), 0.0, Municipality, 6, TrafficDirection.TowardsDigitizing, SingleCarriageway, exactModifiedAtValue, Some("automatic_generation"), constructionType = ConstructionType.InUse, linkSource = LinkGeomSource.NormalLinkInterface))
      val changeSet: RoadLinkChangeSet = RoadLinkChangeSet(roadLink, List(IncompleteLink(789,91,Municipality)))

      verify(mockEventBus).publish(
        org.mockito.Matchers.eq("linkProperties:changed"),
        org.mockito.Matchers.eq(changeSet))

      dynamicSession.rollback()
    }
  }

  test("Remove road link from incomplete link list once functional class and link type are specified") {
    OracleDatabase.withDynTransaction {
      val mockVVHClient = MockitoSugar.mock[VVHClient]
      val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]

      val roadLink = VVHRoadlink(1l, 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers)
      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHRoadLinkClient.fetchByLinkId(1l)).thenReturn(Some(roadLink))
      val service = new TestService(mockVVHClient)

      sqlu"""insert into incomplete_link (id, link_id, municipality_code, administrative_class) values (43241231233, 1, 91, 1)""".execute

      simulateQuery { service.updateLinkProperties(1, FunctionalClass.Unknown, Freeway, TrafficDirection.BothDirections, Municipality, Option("test"), _ => ()) }
      simulateQuery { service.updateLinkProperties(1, 4, UnknownLinkType, TrafficDirection.BothDirections, Municipality, Option("test"), _ => ()) }

      val incompleteLinks = service.getIncompleteLinks(Some(Set(91)))
      incompleteLinks should be(empty)

      dynamicSession.rollback()
    }
  }

  test("Should map link properties of old link to new link when one old link maps to one new link") {
    val oldLinkId = 1l
    val newLinkId = 2l
    val changeInfo = ChangeInfo(Some(oldLinkId), Some(newLinkId), 123l, 5, Some(0), Some(1), Some(0), Some(1), 144000000)
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val oldRoadLink = VVHRoadlink(oldLinkId, 235, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, attributes = Map("MUNICIPALITYCODE" -> BigInt(235)))
    val newRoadLink = VVHRoadlink(newLinkId, 235, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, attributes = Map("MUNICIPALITYCODE" -> BigInt(235)))

    val mockVVHClient = MockitoSugar.mock[VVHClient]
    val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
    val mockVVHChangeInfoClient = MockitoSugar.mock[VVHChangeInfoClient]

    val service = new TestService(mockVVHClient)

    OracleDatabase.withDynTransaction {
      sqlu"""insert into functional_class (id, link_id, functional_class, modified_by) values (1, $oldLinkId, 3, 'test' )""".execute
      sqlu"""insert into link_type (id, link_id, link_type, modified_by) values (2, $oldLinkId, ${Freeway.value}, 'test' )""".execute
      sqlu"""insert into traffic_direction (id, link_id, traffic_direction, modified_by) values (3, $oldLinkId, ${TrafficDirection.BothDirections.value}, 'test' )""".execute

      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHClient.roadLinkChangeInfo).thenReturn(mockVVHChangeInfoClient)
      when(mockVVHRoadLinkClient.fetchByMunicipalitiesAndBoundsF(boundingBox, Set())).thenReturn(Promise.successful(Seq(oldRoadLink)).future)
      when(mockVVHChangeInfoClient.fetchByBoundsAndMunicipalitiesF(boundingBox, Set())).thenReturn(Promise.successful(Nil).future)
      val before = service.getRoadLinksFromVVH(boundingBox)
      before.head.functionalClass should be(3)
      before.head.linkType should be(Freeway)
      before.head.trafficDirection should be(TrafficDirection.BothDirections)

      when(mockVVHRoadLinkClient.fetchByMunicipalitiesAndBoundsF(boundingBox, Set())).thenReturn(Promise.successful(Seq(newRoadLink)).future)
      when(mockVVHChangeInfoClient.fetchByBoundsAndMunicipalitiesF(boundingBox, Set())).thenReturn(Promise.successful(Seq(changeInfo)).future)
      val after = service.getRoadLinksFromVVH(boundingBox)
      after.head.functionalClass should be(3)
      after.head.linkType should be(Freeway)
      after.head.trafficDirection should be(TrafficDirection.BothDirections)

      dynamicSession.rollback()
    }
  }

  test("Shoul map link properties of old link to new link when multiple old links map to new link and all have same values") {
    val oldLinkId1 = 1l
    val oldLinkId2 = 2l
    val newLinkId = 3l
    val changeInfo = Seq(ChangeInfo(Some(oldLinkId1), Some(newLinkId), 123l, 5, Some(0), Some(1), Some(0), Some(1), 144000000), ChangeInfo(Some(oldLinkId2), Some(newLinkId), 345l, 5, Some(0), Some(1), Some(0), Some(1), 144000000))
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val oldRoadLinks = Seq(VVHRoadlink(oldLinkId1, 235, Nil, Municipality, TrafficDirection.AgainstDigitizing, FeatureClass.AllOthers, attributes = Map("MUNICIPALITYCODE" -> BigInt(235))),
      VVHRoadlink(oldLinkId2, 235, Nil, Municipality, TrafficDirection.AgainstDigitizing, FeatureClass.AllOthers, attributes = Map("MUNICIPALITYCODE" -> BigInt(235))))
    val newRoadLink = VVHRoadlink(newLinkId, 235, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, attributes = Map("MUNICIPALITYCODE" -> BigInt(235)))

    val mockVVHClient = MockitoSugar.mock[VVHClient]
    val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
    val mockVVHChangeInfoClient = MockitoSugar.mock[VVHChangeInfoClient]
    val service = new TestService(mockVVHClient)

    OracleDatabase.withDynTransaction {
      sqlu"""insert into functional_class (id, link_id, functional_class, modified_by) values (1, $oldLinkId1, 3, 'test' )""".execute
      sqlu"""insert into functional_class (id, link_id, functional_class, modified_by) values (2, $oldLinkId2, 3, 'test' )""".execute
      sqlu"""insert into traffic_direction (id, link_id, traffic_direction, modified_by) values (3, $oldLinkId1, ${TrafficDirection.TowardsDigitizing.value}, 'test' )""".execute
      sqlu"""insert into traffic_direction (id, link_id, traffic_direction, modified_by) values (4, $oldLinkId2, ${TrafficDirection.TowardsDigitizing.value}, 'test' )""".execute
      sqlu"""insert into link_type (id, link_id, link_type, modified_by) values (5, $oldLinkId1, ${Freeway.value}, 'test' )""".execute
      sqlu"""insert into link_type (id, link_id, link_type, modified_by) values (6, $oldLinkId2, ${Freeway.value}, 'test' )""".execute

      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHClient.roadLinkChangeInfo).thenReturn(mockVVHChangeInfoClient)
      when(mockVVHRoadLinkClient.fetchByMunicipalitiesAndBoundsF(boundingBox, Set())).thenReturn(Promise.successful(oldRoadLinks).future)
      when(mockVVHChangeInfoClient.fetchByBoundsAndMunicipalitiesF(boundingBox, Set())).thenReturn(Promise.successful(Nil).future)
      val before = service.getRoadLinksFromVVH(boundingBox)
      before.foreach(_.functionalClass should be(3))
      before.foreach(_.linkType should be(Freeway))
      before.foreach(_.trafficDirection should be(TrafficDirection.TowardsDigitizing))

      when(mockVVHRoadLinkClient.fetchByMunicipalitiesAndBoundsF(boundingBox, Set())).thenReturn(Promise.successful(Seq(newRoadLink)).future)
      when(mockVVHChangeInfoClient.fetchByBoundsAndMunicipalitiesF(boundingBox, Set())).thenReturn(Promise.successful(changeInfo).future)
      val after = service.getRoadLinksFromVVH(boundingBox)
      after.head.functionalClass should be(3)
      after.head.linkType should be(Freeway)
      after.head.trafficDirection should be(TrafficDirection.TowardsDigitizing)


      dynamicSession.rollback()
    }
  }

  test("""Functional class and link type should be unknown
         and traffic direction same as for the new VVH link
         when multiple old links map to new link but have different properties values""") {
    val oldLinkId1 = 1l
    val oldLinkId2 = 2l
    val newLinkId = 3l
    val changeInfo = Seq(ChangeInfo(Some(oldLinkId1), Some(newLinkId), 123l, 5, Some(0), Some(1), Some(0), Some(1), 144000000), ChangeInfo(Some(oldLinkId2), Some(newLinkId), 345l, 5, Some(0), Some(1), Some(0), Some(1), 144000000))
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val oldRoadLinks = Seq(
      VVHRoadlink(oldLinkId1, 235, Nil, Municipality, TrafficDirection.AgainstDigitizing, FeatureClass.AllOthers, attributes = Map("MUNICIPALITYCODE" -> BigInt(235))),
      VVHRoadlink(oldLinkId2, 235, Nil, Municipality, TrafficDirection.AgainstDigitizing, FeatureClass.AllOthers, attributes = Map("MUNICIPALITYCODE" -> BigInt(235))))
    val newRoadLink = VVHRoadlink(newLinkId, 235, Nil, Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, attributes = Map("MUNICIPALITYCODE" -> BigInt(235)))

    val mockVVHClient = MockitoSugar.mock[VVHClient]
    val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
    val mockVVHChangeInfoClient = MockitoSugar.mock[VVHChangeInfoClient]
    val service = new TestService(mockVVHClient)

    OracleDatabase.withDynTransaction {
      sqlu"""insert into functional_class (id, link_id, functional_class, modified_by) values (1, $oldLinkId1, 3, 'test' )""".execute
      sqlu"""insert into functional_class (id, link_id, functional_class, modified_by) values (2, $oldLinkId2, 2, 'test' )""".execute
      sqlu"""insert into traffic_direction (id, link_id, traffic_direction, modified_by) values (3, $oldLinkId1, ${TrafficDirection.BothDirections.value}, 'test' )""".execute
      sqlu"""insert into traffic_direction (id, link_id, traffic_direction, modified_by) values (4, $oldLinkId2, ${TrafficDirection.TowardsDigitizing.value}, 'test' )""".execute
      sqlu"""insert into link_type (id, link_id, link_type, modified_by) values (5, $oldLinkId1, ${Freeway.value}, 'test' )""".execute
      sqlu"""insert into link_type (id, link_id, link_type, modified_by) values (6, $oldLinkId2, ${Motorway.value}, 'test' )""".execute

      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHClient.roadLinkChangeInfo).thenReturn(mockVVHChangeInfoClient)
      when(mockVVHRoadLinkClient.fetchByMunicipalitiesAndBoundsF(boundingBox, Set())).thenReturn(Promise.successful(oldRoadLinks).future)
      when(mockVVHChangeInfoClient.fetchByBoundsAndMunicipalitiesF(boundingBox, Set())).thenReturn(Promise.successful(Nil).future)
      val before = service.getRoadLinksFromVVH(boundingBox)
      before.length should be(2)

      when(mockVVHRoadLinkClient.fetchByMunicipalitiesAndBoundsF(boundingBox, Set())).thenReturn(Promise.successful(Seq(newRoadLink)).future)
      when(mockVVHChangeInfoClient.fetchByBoundsAndMunicipalitiesF(boundingBox, Set())).thenReturn(Promise.successful(changeInfo).future)
      val after = service.getRoadLinksFromVVH(boundingBox)
      after.head.functionalClass should be(FunctionalClass.Unknown)
      after.head.linkType should be(UnknownLinkType)
      after.head.trafficDirection should be(TrafficDirection.BothDirections)

      dynamicSession.rollback()
    }
  }

  test("""Traffic direction should be received from VVH if it wasn't overridden in OTH""") {
    val oldLinkId1 = 1l
    val oldLinkId2 = 2l
    val newLinkId = 3l
    val changeInfo = Seq(ChangeInfo(Some(oldLinkId1), Some(newLinkId), 123l, 5, Some(0), Some(1), Some(0), Some(1), 144000000), ChangeInfo(Some(oldLinkId2), Some(newLinkId), 345l, 5, Some(0), Some(1), Some(0), Some(1), 144000000))
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val oldRoadLinks = Seq(
      VVHRoadlink(oldLinkId1, 235, Nil, Municipality, TrafficDirection.AgainstDigitizing, FeatureClass.AllOthers, attributes = Map("MUNICIPALITYCODE" -> BigInt(235))),
      VVHRoadlink(oldLinkId2, 235, Nil, Municipality, TrafficDirection.AgainstDigitizing, FeatureClass.AllOthers, attributes = Map("MUNICIPALITYCODE" -> BigInt(235))))
    val newRoadLink = VVHRoadlink(newLinkId, 235, Nil, Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, attributes = Map("MUNICIPALITYCODE" -> BigInt(235)))

    val mockVVHClient = MockitoSugar.mock[VVHClient]
    val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
    val mockVVHChangeInfoClient = MockitoSugar.mock[VVHChangeInfoClient]
    val service = new TestService(mockVVHClient)

    OracleDatabase.withDynTransaction {
      sqlu"""insert into functional_class (id, link_id, functional_class, modified_by) values (1, $oldLinkId1, 3, 'test' )""".execute
      sqlu"""insert into functional_class (id, link_id, functional_class, modified_by) values (2, $oldLinkId2, 3, 'test' )""".execute
      sqlu"""insert into link_type (id, link_id, link_type, modified_by) values (5, $oldLinkId1, ${Freeway.value}, 'test' )""".execute
      sqlu"""insert into link_type (id, link_id, link_type, modified_by) values (6, $oldLinkId2, ${Freeway.value}, 'test' )""".execute


      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHClient.roadLinkChangeInfo).thenReturn(mockVVHChangeInfoClient)
      when(mockVVHRoadLinkClient.fetchByMunicipalitiesAndBoundsF(boundingBox, Set())).thenReturn(Promise.successful(oldRoadLinks).future)
      when(mockVVHChangeInfoClient.fetchByBoundsAndMunicipalitiesF(boundingBox, Set())).thenReturn(Promise.successful(Nil).future)
      val before = service.getRoadLinksFromVVH(boundingBox)
      before.length should be(2)

      when(mockVVHRoadLinkClient.fetchByMunicipalitiesAndBoundsF(boundingBox, Set())).thenReturn(Promise.successful(Seq(newRoadLink)).future)
      when(mockVVHChangeInfoClient.fetchByBoundsAndMunicipalitiesF(boundingBox, Set())).thenReturn(Promise.successful(changeInfo).future)
      val after = service.getRoadLinksFromVVH(boundingBox)
      after.head.functionalClass should be(3)
      after.head.linkType should be(Freeway)
      after.head.trafficDirection should be(TrafficDirection.BothDirections)

      dynamicSession.rollback()
    }
  }

  test("Should map link properties of old link to two new links when old link maps multiple new links in change info table") {
    val oldLinkId = 1l
    val newLinkId1 = 2l
    val newLinkId2 = 3l
    val changeInfo = Seq(ChangeInfo(Some(oldLinkId), Some(newLinkId1), 123l, 5, Some(0), Some(1), Some(0), Some(1), 144000000), ChangeInfo(Some(oldLinkId), Some(newLinkId2), 345l, 5, Some(0), Some(1), Some(0), Some(1), 144000000))
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val oldRoadLink = VVHRoadlink(oldLinkId, 235, Nil, Municipality, TrafficDirection.AgainstDigitizing, FeatureClass.AllOthers, attributes = Map("MUNICIPALITYCODE" -> BigInt(235)))
    val newRoadLinks = Seq(
      VVHRoadlink(newLinkId1, 235, Nil, Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, attributes = Map("MUNICIPALITYCODE" -> BigInt(235))),
      VVHRoadlink(newLinkId2, 235, Nil, Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, attributes = Map("MUNICIPALITYCODE" -> BigInt(235))))

    val mockVVHClient = MockitoSugar.mock[VVHClient]
    val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
    val mockVVHChangeInfoClient = MockitoSugar.mock[VVHChangeInfoClient]
    val service = new TestService(mockVVHClient)

    OracleDatabase.withDynTransaction {
      sqlu"""insert into functional_class (id, link_id, functional_class, modified_by) values (1, $oldLinkId, 3, 'test' )""".execute
      sqlu"""insert into traffic_direction (id, link_id, traffic_direction, modified_by) values (3, $oldLinkId, ${TrafficDirection.TowardsDigitizing.value}, 'test' )""".execute
      sqlu"""insert into link_type (id, link_id, link_type, modified_by) values (5, $oldLinkId, ${SlipRoad.value}, 'test' )""".execute

      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHClient.roadLinkChangeInfo).thenReturn(mockVVHChangeInfoClient)
      when(mockVVHRoadLinkClient.fetchByMunicipalitiesAndBoundsF(boundingBox, Set())).thenReturn(Promise.successful(Seq(oldRoadLink)).future)
      when(mockVVHChangeInfoClient.fetchByBoundsAndMunicipalitiesF(boundingBox, Set())).thenReturn(Promise.successful(Nil).future)
      val before = service.getRoadLinksFromVVH(boundingBox)
      before.foreach(_.functionalClass should be(3))
      before.foreach(_.linkType should be(SlipRoad))
      before.foreach(_.trafficDirection should be(TrafficDirection.TowardsDigitizing))

      when(mockVVHRoadLinkClient.fetchByMunicipalitiesAndBoundsF(boundingBox, Set())).thenReturn(Promise.successful(newRoadLinks).future)
      when(mockVVHChangeInfoClient.fetchByBoundsAndMunicipalitiesF(boundingBox, Set())).thenReturn(Promise.successful(changeInfo).future)
      val after = service.getRoadLinksFromVVH(boundingBox)
      after.length should be(2)
      after.foreach { link =>
        link.functionalClass should be(3)
        link.linkType should be(SlipRoad)
        link.trafficDirection should be(TrafficDirection.TowardsDigitizing)
      }

      dynamicSession.rollback()
    }
  }

  test("Should map link properties of old link to new link when one old link maps to one new link, old link has functional class but no link type") {
    val oldLinkId = 1l
    val newLinkId = 2l
    val changeInfo = ChangeInfo(Some(oldLinkId), Some(newLinkId), 123l, 5, Some(0), Some(1), Some(0), Some(1), 144000000)
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val oldRoadLink = VVHRoadlink(oldLinkId, 235, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, attributes = Map("MUNICIPALITYCODE" -> BigInt(235)))
    val newRoadLink = VVHRoadlink(newLinkId, 235, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, attributes = Map("MUNICIPALITYCODE" -> BigInt(235)))

    val mockVVHClient = MockitoSugar.mock[VVHClient]
    val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
    val mockVVHChangeInfoClient = MockitoSugar.mock[VVHChangeInfoClient]
    val service = new TestService(mockVVHClient)

    OracleDatabase.withDynTransaction {
      sqlu"""insert into functional_class (id, link_id, functional_class, modified_by) values (1, $oldLinkId, 3, 'test' )""".execute
      sqlu"""insert into link_type (id, link_id, link_type, modified_by) values (2, $oldLinkId, ${Freeway.value}, 'test' )""".execute
      sqlu"""insert into traffic_direction (id, link_id, traffic_direction, modified_by) values (3, $oldLinkId, ${TrafficDirection.BothDirections.value}, 'test' )""".execute

      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHClient.roadLinkChangeInfo).thenReturn(mockVVHChangeInfoClient)
      when(mockVVHRoadLinkClient.fetchByMunicipalitiesAndBoundsF(boundingBox, Set())).thenReturn(Promise.successful(Seq(oldRoadLink)).future)
      when(mockVVHChangeInfoClient.fetchByBoundsAndMunicipalitiesF(boundingBox, Set())).thenReturn(Promise.successful(Nil).future)
      val before = service.getRoadLinksFromVVH(boundingBox)
      before.head.functionalClass should be(3)
      before.head.linkType should be(Freeway)
      before.head.trafficDirection should be(TrafficDirection.BothDirections)

      sqlu"""delete from link_type where id=2""".execute

      when(mockVVHRoadLinkClient.fetchByMunicipalitiesAndBoundsF(boundingBox, Set())).thenReturn(Promise.successful(Seq(newRoadLink)).future)
      when(mockVVHChangeInfoClient.fetchByBoundsAndMunicipalitiesF(boundingBox, Set())).thenReturn(Promise.successful(Seq(changeInfo)).future)
      val after = service.getRoadLinksFromVVH(boundingBox)
      after.head.functionalClass should be(3)
      after.head.linkType should be(UnknownLinkType)
      after.head.trafficDirection should be(TrafficDirection.BothDirections)

      dynamicSession.rollback()
    }
  }

  test(
    """Should map just old link type (not functional class) to new link
       when one old link maps to one new link
       and new link has functional class but no link type
       and old link has both functional class and link type""".stripMargin) {
    val oldLinkId = 1l
    val newLinkId = 2l
    val changeInfo = ChangeInfo(Some(oldLinkId), Some(newLinkId), 123l, 5, Some(0), Some(1), Some(0), Some(1), 144000000)
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val oldRoadLink = VVHRoadlink(oldLinkId, 235, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, attributes = Map("MUNICIPALITYCODE" -> BigInt(235)))
    val newRoadLink = VVHRoadlink(newLinkId, 235, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, attributes = Map("MUNICIPALITYCODE" -> BigInt(235)))

    val mockVVHClient = MockitoSugar.mock[VVHClient]
    val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
    val mockVVHChangeInfoClient = MockitoSugar.mock[VVHChangeInfoClient]
    val service = new TestService(mockVVHClient)

    OracleDatabase.withDynTransaction {
      sqlu"""insert into functional_class (id, link_id, functional_class, modified_by) values (1, $oldLinkId, 3, 'test' )""".execute
      sqlu"""insert into link_type (id, link_id, link_type, modified_by) values (3, $oldLinkId, ${Freeway.value}, 'test' )""".execute
      sqlu"""insert into traffic_direction (id, link_id, traffic_direction, modified_by) values (4, $oldLinkId, ${TrafficDirection.BothDirections.value}, 'test' )""".execute

      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHClient.roadLinkChangeInfo).thenReturn(mockVVHChangeInfoClient)
      when(mockVVHRoadLinkClient.fetchByMunicipalitiesAndBoundsF(boundingBox, Set())).thenReturn(Promise.successful(Seq(oldRoadLink)).future)
      when(mockVVHChangeInfoClient.fetchByBoundsAndMunicipalitiesF(boundingBox, Set())).thenReturn(Promise.successful(Nil).future)
      val before = service.getRoadLinksFromVVH(boundingBox)
      before.head.functionalClass should be(3)
      before.head.linkType should be(Freeway)
      before.head.trafficDirection should be(TrafficDirection.BothDirections)

      sqlu"""insert into functional_class (id, link_id, functional_class, modified_by) values (2, $newLinkId, 6, 'test' )""".execute

      when(mockVVHRoadLinkClient.fetchByMunicipalitiesAndBoundsF(boundingBox, Set())).thenReturn(Promise.successful(Seq(newRoadLink)).future)
      when(mockVVHChangeInfoClient.fetchByBoundsAndMunicipalitiesF(boundingBox, Set())).thenReturn(Promise.successful(Seq(changeInfo)).future)
      val after = service.getRoadLinksFromVVH(boundingBox)
      after.head.functionalClass should be(6)
      after.head.linkType should be(Freeway)
      after.head.trafficDirection should be(TrafficDirection.BothDirections)

      dynamicSession.rollback()
    }
  }

  test("Should take the latest time stamp (from VVH road link or from link properties in db) to show in UI") {

    val linkId = 1l
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val vvhModifiedDate = 1455274504000l
    val roadLink = VVHRoadlink(linkId, 235, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, Option(new DateTime(vvhModifiedDate.toLong)), attributes = Map("MUNICIPALITYCODE" -> BigInt(235)))

    val mockVVHClient = MockitoSugar.mock[VVHClient]
    val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
    val mockVVHChangeInfoClient = MockitoSugar.mock[VVHChangeInfoClient]
    val service = new TestService(mockVVHClient)

    OracleDatabase.withDynTransaction {
      sqlu"""insert into functional_class (id, link_id, functional_class, modified_by, modified_date) values (1, $linkId, 3, 'test', TO_TIMESTAMP('2014-02-10 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'))""".execute
      sqlu"""insert into traffic_direction (id, link_id, traffic_direction, modified_by, modified_date) values (2, $linkId, ${TrafficDirection.TowardsDigitizing.value}, 'test', TO_TIMESTAMP('2014-02-10 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'))""".execute
      sqlu"""insert into link_type (id, link_id, link_type, modified_by, modified_date) values (5, $linkId, ${Freeway.value}, 'test', TO_TIMESTAMP('2015-03-10 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'))""".execute

      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHClient.roadLinkChangeInfo).thenReturn(mockVVHChangeInfoClient)
      when(mockVVHRoadLinkClient.fetchByMunicipalitiesAndBoundsF(boundingBox, Set())).thenReturn(Promise.successful(Seq(roadLink)).future)
      when(mockVVHChangeInfoClient.fetchByBoundsAndMunicipalitiesF(boundingBox, Set())).thenReturn(Promise.successful(Nil).future)
      val roadLinkAfterDateComparison = service.getRoadLinksFromVVH(boundingBox).head

      roadLinkAfterDateComparison.modifiedAt.get should be ("12.02.2016 12:55:04")
      roadLinkAfterDateComparison.modifiedBy.get should be ("vvh_modified")

      dynamicSession.rollback()
    }
  }

  test("Check the correct return of a ViiteRoadLink By Municipality") {
    val municipalityId = 235
    val linkId = 2l
    val roadLink = VVHRoadlink(linkId, municipalityId, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, attributes = Map("MUNICIPALITYCODE" -> BigInt(235)))

    val mockVVHClient = MockitoSugar.mock[VVHClient]
    val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
    val service = new TestService(mockVVHClient)

    OracleDatabase.withDynTransaction {
      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHRoadLinkClient.fetchByMunicipality(municipalityId)).thenReturn(Seq(roadLink))

      val viiteRoadLinks = service.getViiteRoadLinksFromVVHByMunicipality(municipalityId)

      viiteRoadLinks.length > 0 should be (true)
      viiteRoadLinks.head.isInstanceOf[RoadLink] should be (true)
      viiteRoadLinks.head.linkId should be(linkId)
      viiteRoadLinks.head.municipalityCode should be (municipalityId)
    }
  }

  test("Verify if there are roadlinks from the complimentary geometry") {
    val municipalityId = 235
    val linkId = 2l
    val roadLink: VVHRoadlink = VVHRoadlink(linkId, municipalityId, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, attributes = Map("MUNICIPALITYCODE" -> BigInt(235)))
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))

    val mockVVHClient = MockitoSugar.mock[VVHClient]
    val mockVVHComplementaryClient = MockitoSugar.mock[VVHComplementaryClient]
    val service = new TestService(mockVVHClient)

    OracleDatabase.withDynTransaction {
      when(mockVVHClient.complementaryData).thenReturn(mockVVHComplementaryClient)
      when(mockVVHComplementaryClient.fetchByMunicipalitiesAndBoundsF(any[BoundingRectangle], any[Set[Int]])).thenReturn(Future(Seq(roadLink)))

      val roadLinksList = service.getComplementaryRoadLinksFromVVH(boundingBox, Set.empty)

      roadLinksList.length > 0 should be(true)
      roadLinksList.head.isInstanceOf[RoadLink] should be(true)
      roadLinksList.head.linkId should be(linkId)
      roadLinksList.head.municipalityCode should be(municipalityId)
    }
  }

  test("Full municipality request includes both complementary and ordinary geometries") {
    val municipalityId = 235
    val linkId = Seq(1l, 2l)
    val roadLinks = linkId.map(id =>
      new VVHRoadlink(id, municipalityId, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, attributes = Map("MUNICIPALITYCODE" -> BigInt(235)))
    )
    val linkIdComp = Seq(3l, 4l)
    val roadLinksComp = linkIdComp.map(id =>
      new VVHRoadlink(id, municipalityId, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, attributes = Map("MUNICIPALITYCODE" -> BigInt(235), "SUBTYPE" -> BigInt(3)))
    )

    val mockVVHClient = MockitoSugar.mock[VVHClient]
    val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
    val mockVVHComplementaryClient = MockitoSugar.mock[VVHComplementaryClient]
    val service = new TestService(mockVVHClient)

    OracleDatabase.withDynTransaction {
      when(mockVVHClient.complementaryData).thenReturn(mockVVHComplementaryClient)
      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHComplementaryClient.fetchByMunicipalityAndRoadNumbersF(any[Int], any[Seq[(Int,Int)]])).thenReturn(Future(roadLinksComp))
      when(mockVVHRoadLinkClient.fetchByMunicipalityAndRoadNumbersF(any[Int], any[Seq[(Int,Int)]])).thenReturn(Future(roadLinks))

      val roadLinksList = service.getViiteCurrentAndComplementaryRoadLinksFromVVH(235, Seq())

      roadLinksList should have length (4)
      roadLinksList.filter(_.attributes.contains("SUBTYPE")) should have length(2)
    }
  }

  test("Verify the returning of the correct VVHHistoryRoadLink"){
    val municipalityId = 235
    val linkId = 1234
    val firstRoadLink = new VVHHistoryRoadLink(linkId, municipalityId, Seq.empty, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, 0, 100, attributes = Map("MUNICIPALITYCODE" -> BigInt(235), "SUBTYPE" -> BigInt(3)))
    val secondRoadLink = new VVHHistoryRoadLink(linkId, municipalityId, Seq.empty, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, 0, 1000, attributes = Map("MUNICIPALITYCODE" -> BigInt(235), "SUBTYPE" -> BigInt(3)))

    val mockVVHClient = MockitoSugar.mock[VVHClient]
    val mockVVHHistoryClient = MockitoSugar.mock[VVHHistoryClient]
    val service = new TestService(mockVVHClient)

    OracleDatabase.withDynTransaction {
      when(mockVVHClient.historyData).thenReturn(mockVVHHistoryClient)
      when(mockVVHHistoryClient.fetchVVHRoadLinkByLinkIdsF(any[Set[Long]])).thenReturn(Future(Seq(firstRoadLink, secondRoadLink)))
      val serviceResult = service.getViiteRoadLinksHistoryFromVVH(Set[Long](linkId))
      serviceResult.length should be (1)
      serviceResult.head.linkId should be (linkId)
      serviceResult.head.endDate should be (1000)
    }
  }

  test("Only road links with construction type 'in use' should be saved to incomplete_link table (not 'under construction' or 'planned')") {
    OracleDatabase.withDynTransaction {
      val mockVVHClient = MockitoSugar.mock[VVHClient]
      val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
      val mockVVHChangeInfoClient = MockitoSugar.mock[VVHChangeInfoClient]
      val mockVVHComplementaryDataClient = MockitoSugar.mock[VVHComplementaryClient]
      val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
      val vvhRoadLink1 = VVHRoadlink(1, 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, constructionType = ConstructionType.InUse, linkSource = LinkGeomSource.NormalLinkInterface)
      val vvhRoadLink2 = VVHRoadlink(2, 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, constructionType = ConstructionType.UnderConstruction, linkSource = LinkGeomSource.NormalLinkInterface)
      val vvhRoadLink3 = VVHRoadlink(3, 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, constructionType = ConstructionType.Planned, linkSource = LinkGeomSource.NormalLinkInterface)
      val vvhRoadLink4 = VVHRoadlink(4, 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, constructionType = ConstructionType.InUse, linkSource = LinkGeomSource.NormalLinkInterface)

      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHClient.roadLinkChangeInfo).thenReturn(mockVVHChangeInfoClient)
      when(mockVVHClient.complementaryData).thenReturn(mockVVHComplementaryDataClient)
      when(mockVVHComplementaryDataClient.fetchWalkwaysByMunicipalitiesF(91)).thenReturn(Promise.successful(Seq()).future)
      when(mockVVHRoadLinkClient.fetchByMunicipalityF(91)).thenReturn(Promise.successful(Seq(vvhRoadLink1, vvhRoadLink2, vvhRoadLink3, vvhRoadLink4)).future)
      when(mockVVHChangeInfoClient.fetchByMunicipalityF(91)).thenReturn(Promise.successful(Nil).future)
      val service = new TestService(mockVVHClient, mockEventBus)
      val roadLinks = service.getRoadLinksFromVVH(91)

      // Return all road links (all are incomplete here)
      val roadLink1 = RoadLink(1,List(),0.0,Municipality,99,TrafficDirection.TowardsDigitizing,UnknownLinkType,None,None,Map(),ConstructionType.InUse, linkSource = LinkGeomSource.NormalLinkInterface)
      val roadLink2 = RoadLink(2,List(),0.0,Municipality,99,TrafficDirection.TowardsDigitizing,UnknownLinkType,None,None,Map(),ConstructionType.UnderConstruction, linkSource = LinkGeomSource.NormalLinkInterface)
      val roadLink3 = RoadLink(3,List(),0.0,Municipality,99,TrafficDirection.TowardsDigitizing,UnknownLinkType,None,None,Map(),ConstructionType.Planned, linkSource = LinkGeomSource.NormalLinkInterface)
      val roadLink4 = RoadLink(4,List(),0.0,Municipality,99,TrafficDirection.TowardsDigitizing,UnknownLinkType,None,None,Map(),ConstructionType.InUse, linkSource = LinkGeomSource.NormalLinkInterface)
      roadLinks.equals(Seq(roadLink1, roadLink2, roadLink3, roadLink4))

      // Pass only incomplete road links with construction type 'in use' to be saved with actor
      val changeSet = RoadLinkChangeSet(Seq(), List(IncompleteLink(1,91,Municipality), IncompleteLink(4,91,Municipality)))
      verify(mockEventBus).publish(
        org.mockito.Matchers.eq("linkProperties:changed"),
        org.mockito.Matchers.eq(changeSet))

      dynamicSession.rollback()
    }
  }

  test("Should not save links to incomplete_link when the road source is not normal") {
    OracleDatabase.withDynTransaction {
      val mockVVHClient = MockitoSugar.mock[VVHClient]
      val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
      val mockVVHChangeInfoClient = MockitoSugar.mock[VVHChangeInfoClient]
      val mockVVHComplementaryDataClient = MockitoSugar.mock[VVHComplementaryClient]
      val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
      val vvhRoadLink1 = VVHRoadlink(1, 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, constructionType = ConstructionType.InUse, linkSource = LinkGeomSource.FrozenLinkInterface)
      val vvhRoadLink2 = VVHRoadlink(2, 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, constructionType = ConstructionType.InUse, linkSource = LinkGeomSource.HistoryLinkInterface)
      val vvhRoadLink3 = VVHRoadlink(3, 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, constructionType = ConstructionType.InUse, linkSource = LinkGeomSource.SuravageLinkInterface)
      val vvhRoadLink4 = VVHRoadlink(4, 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, constructionType = ConstructionType.InUse, linkSource = LinkGeomSource.Unknown)
      val vvhRoadLink5 = VVHRoadlink(5, 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, constructionType = ConstructionType.InUse, linkSource = LinkGeomSource.NormalLinkInterface)
      val vvhRoadLink6 = VVHRoadlink(6, 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, constructionType = ConstructionType.InUse, linkSource = LinkGeomSource.ComplimentaryLinkInterface)

      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHClient.roadLinkChangeInfo).thenReturn(mockVVHChangeInfoClient)
      when(mockVVHClient.complementaryData).thenReturn(mockVVHComplementaryDataClient)
      when(mockVVHComplementaryDataClient.fetchWalkwaysByMunicipalitiesF(91)).thenReturn(Promise.successful(Seq()).future)
      when(mockVVHRoadLinkClient.fetchByMunicipalityF(91)).thenReturn(Promise.successful(Seq(vvhRoadLink1, vvhRoadLink2, vvhRoadLink3, vvhRoadLink4, vvhRoadLink5, vvhRoadLink6)).future)
      when(mockVVHChangeInfoClient.fetchByMunicipalityF(91)).thenReturn(Promise.successful(Nil).future)
      val service = new TestService(mockVVHClient, mockEventBus)
      when(mockVVHRoadLinkClient.fetchByLinkId(5)).thenReturn(Some(vvhRoadLink5))

      val roadLinks = service.getRoadLinksFromVVH(91)

      // Return all road links (all are incomplete here)
      val roadLink1 = RoadLink(1,List(),0.0,Municipality,99,TrafficDirection.TowardsDigitizing,UnknownLinkType,None,None,Map(),ConstructionType.InUse, linkSource = LinkGeomSource.FrozenLinkInterface)
      val roadLink2 = RoadLink(2,List(),0.0,Municipality,99,TrafficDirection.TowardsDigitizing,UnknownLinkType,None,None,Map(),ConstructionType.InUse, linkSource = LinkGeomSource.HistoryLinkInterface)
      val roadLink3 = RoadLink(3,List(),0.0,Municipality,99,TrafficDirection.TowardsDigitizing,UnknownLinkType,None,None,Map(),ConstructionType.InUse, linkSource = LinkGeomSource.SuravageLinkInterface)
      val roadLink4 = RoadLink(4,List(),0.0,Municipality,99,TrafficDirection.TowardsDigitizing,UnknownLinkType,None,None,Map(),ConstructionType.InUse, linkSource = LinkGeomSource.Unknown)
      val roadLink5 = RoadLink(5,List(),0.0,Municipality,99,TrafficDirection.TowardsDigitizing,UnknownLinkType,None,None,Map(),ConstructionType.InUse, linkSource = LinkGeomSource.NormalLinkInterface)
      val roadLink6 = RoadLink(6,List(),0.0,Municipality,99,TrafficDirection.TowardsDigitizing,UnknownLinkType,None,None,Map(),ConstructionType.InUse, linkSource = LinkGeomSource.ComplimentaryLinkInterface)

      roadLinks.equals(Seq(roadLink1, roadLink2, roadLink3, roadLink4, roadLink5, roadLink6))

      // Pass only incomplete road links with link source normal
      val changeSet = RoadLinkChangeSet(List(), List(IncompleteLink(5,91,Municipality)))
      verify(mockEventBus).publish(
        org.mockito.Matchers.eq("linkProperties:changed"),
        org.mockito.Matchers.eq(changeSet))

      dynamicSession.rollback()
    }
  }

  test("Should return roadlinks and complementary roadlinks") {

    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val mockVVHClient = MockitoSugar.mock[VVHClient]
    val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
    val mockVVHChangeInfoClient = MockitoSugar.mock[VVHChangeInfoClient]
    val mockVVHComplementaryClient = MockitoSugar.mock[VVHComplementaryClient]
    val service = new TestService(mockVVHClient)

    val complRoadLink1 = VVHRoadlink(1, 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, constructionType = ConstructionType.InUse)
    val complRoadLink2 = VVHRoadlink(2, 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, constructionType = ConstructionType.UnderConstruction)
    val complRoadLink3 = VVHRoadlink(3, 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, constructionType = ConstructionType.Planned)
    val complRoadLink4 = VVHRoadlink(4, 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, constructionType = ConstructionType.InUse)

    val vvhRoadLink1 = VVHRoadlink(5, 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, constructionType = ConstructionType.InUse)
    val vvhRoadLink2 = VVHRoadlink(6, 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, constructionType = ConstructionType.UnderConstruction)
    val vvhRoadLink3 = VVHRoadlink(7, 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, constructionType = ConstructionType.Planned)
    val vvhRoadLink4 = VVHRoadlink(8, 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, constructionType = ConstructionType.InUse)

    OracleDatabase.withDynTransaction {
      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHClient.roadLinkChangeInfo).thenReturn(mockVVHChangeInfoClient)
      when(mockVVHClient.complementaryData).thenReturn(mockVVHComplementaryClient)
      when(mockVVHComplementaryClient.fetchWalkwaysByBoundsAndMunicipalitiesF(any[BoundingRectangle], any[Set[Int]])).thenReturn(Future(Seq(complRoadLink1, complRoadLink2, complRoadLink3, complRoadLink4)))
      when(mockVVHChangeInfoClient.fetchByBoundsAndMunicipalitiesF(any[BoundingRectangle], any[Set[Int]])).thenReturn(Future(Seq()))
      when(mockVVHRoadLinkClient.fetchByMunicipalitiesAndBoundsF(any[BoundingRectangle], any[Set[Int]])).thenReturn(Future(Seq(vvhRoadLink1, vvhRoadLink2, vvhRoadLink3, vvhRoadLink4)))

      val roadlinks = service.getRoadLinksWithComplementaryFromVVH(boundingBox, Set(91))

      roadlinks.length should be(8)
      roadlinks.map(r => r.linkId).sorted should be (Seq(1,2,3,4,5,6,7,8))

    }
  }

  test("Return road nodes") {
    val mockVVHClient = MockitoSugar.mock[VVHClient]
    val mockVVHRoadNodesClient = MockitoSugar.mock[VVHRoadNodesClient]
    val vvhRoadNode = VVHRoadNodes(1, Point(1, 2, 3), 2, NodeType(1), 235, 1)
    val vvhRoadNode1 = VVHRoadNodes(2, Point(4, 5, 6), 2, NodeType(1), 235, 1)
    val service = new TestService(mockVVHClient)

    OracleDatabase.withDynTransaction {
      when(mockVVHClient.roadNodesData).thenReturn(mockVVHRoadNodesClient)
      when(mockVVHRoadNodesClient.fetchByMunicipality(any[Int])).thenReturn(Seq(vvhRoadNode, vvhRoadNode1))

      val roadNodes = service.getRoadNodesFromVVHByMunicipality(235)
      roadNodes.size should be (2)
    }

  }

  test("Get information about changes in road names when using all other municipalities") {
    val modifiedAt = Some(DateTime.parse("2015-05-07T12:00Z"))
    val attributes: Map[String, Any] =
      Map("ROADNAME_SE" -> "roadname_se",
        "ROADNAME_FI" -> "roadname_fi",
        "CREATED_DATE" -> BigInt.apply(1446132842000L),
        "MUNICIPALITYCODE" -> BigInt(91))

    val mockVVHClient = MockitoSugar.mock[VVHClient]
    val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
    when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
    when(mockVVHRoadLinkClient.fetchByChangesDates(DateTime.parse("2017-05-07T12:00Z"), DateTime.parse("2017-05-09T12:00Z")))
      .thenReturn(Seq(VVHRoadlink(1611447, 91, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers, modifiedAt, attributes)))
    val service = new TestService(mockVVHClient)

    OracleDatabase.withDynTransaction {
      val changedVVHRoadlinks = service.getChanged(DateTime.parse("2017-05-07T12:00Z"), DateTime.parse("2017-05-09T12:00Z"))
      changedVVHRoadlinks.length should be(1)
      changedVVHRoadlinks.head.link.linkId should be(1611447)
      changedVVHRoadlinks.head.link.municipalityCode should be(91)
      changedVVHRoadlinks.head.value should be(attributes.get("ROADNAME_FI").get.toString)
      changedVVHRoadlinks.head.createdAt should be(Some(DateTime.parse("2015-10-29T15:34:02.000Z")))
      changedVVHRoadlinks.head.changeType should be("Modify")
    }
  }

  test("Get information about changes in road names when using the municipalities of Ahvenanmaa") {
    val modifiedAt = Some(DateTime.parse("2015-05-07T12:00Z"))
    val attributes: Map[String, Any] =
      Map("ROADNAME_SE" -> "roadname_se",
        "ROADNAME_FI" -> "roadname_fi",
        "CREATED_DATE" -> BigInt.apply(1446132842000L),
        "MUNICIPALITYCODE" -> BigInt(60))

    val mockVVHClient = MockitoSugar.mock[VVHClient]
    val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
    when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
    when(mockVVHRoadLinkClient.fetchByChangesDates(DateTime.parse("2017-05-07T12:00Z"), DateTime.parse("2017-05-09T12:00Z")))
      .thenReturn(Seq(VVHRoadlink(1611447, 60, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers, modifiedAt, attributes)))
    val service = new TestService(mockVVHClient)

    OracleDatabase.withDynTransaction {
      val changedVVHRoadlinks = service.getChanged(DateTime.parse("2017-05-07T12:00Z"), DateTime.parse("2017-05-09T12:00Z"))
      changedVVHRoadlinks.length should be(1)
      changedVVHRoadlinks.head.link.linkId should be(1611447)
      changedVVHRoadlinks.head.link.municipalityCode should be(60)
      changedVVHRoadlinks.head.value should be(attributes.get("ROADNAME_SE").get.toString)
      changedVVHRoadlinks.head.createdAt should be(Some(DateTime.parse("2015-10-29T15:34:02.000Z")))
      changedVVHRoadlinks.head.changeType should be("Modify")
    }
  }

  test("verify the output of change info") {
    val oldLinkId = 1l
    val newLinkId = 2l
    val changeInfo = ChangeInfo(Some(oldLinkId), Some(newLinkId), 123l, 5, Some(0), Some(1), Some(0), Some(1), 144000000)
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val mockVVHClient = MockitoSugar.mock[VVHClient]
    val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
    val mockVVHChangeInfoClient = MockitoSugar.mock[VVHChangeInfoClient]
    val service = new TestService(mockVVHClient)
    OracleDatabase.withDynTransaction {
      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHClient.roadLinkChangeInfo).thenReturn(mockVVHChangeInfoClient)
      when(mockVVHChangeInfoClient.fetchByBoundsAndMunicipalitiesF(boundingBox, Set())).thenReturn(Promise.successful(Seq(changeInfo)).future)
      val returnedChangeInfo = service.getChangeInfoFromVVH(boundingBox, Set())

      returnedChangeInfo.size should be (1)
      returnedChangeInfo.head.oldId.get should be(oldLinkId)
      returnedChangeInfo.head.newId.get should be(newLinkId)
      returnedChangeInfo.head.mmlId should be(123l)
    }
  }

  test("Should not return roadLinks because it has FeatureClass Winter Roads") {
    OracleDatabase.withDynTransaction {
      val mockVVHClient = MockitoSugar.mock[VVHClient]
      val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHRoadLinkClient.fetchByLinkIds(Set(1611447l)))
        .thenReturn(Seq(VVHRoadlink(1611447, 91, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.WinterRoads)))
      val service = new RoadLinkTestService(mockVVHClient)
      val roadLinks = service.getRoadLinksByLinkIdsFromVVH(Set(1611447l))
      roadLinks.length should be (0)
      dynamicSession.rollback()
    }
  }


  test("Should only return roadLinks that doesn't have FeatureClass Winter Roads") {
    OracleDatabase.withDynTransaction {
      val mockVVHClient = MockitoSugar.mock[VVHClient]
      val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
      when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
      when(mockVVHRoadLinkClient.fetchByMunicipality(91))
        .thenReturn(Seq(VVHRoadlink(1611447, 91, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.WinterRoads),
          VVHRoadlink(1611448, 91, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers),
          VVHRoadlink(1611449, 91, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
      val service = new RoadLinkTestService(mockVVHClient)
      val roadLinks = service.getRoadLinksFromVVHByMunicipality(91)
      roadLinks.length should be (2)
      roadLinks.sortBy(_.linkId)
      roadLinks.head.linkId should be(1611448)
      roadLinks.last.linkId should be(1611449)
      dynamicSession.rollback()
    }
  }
}
