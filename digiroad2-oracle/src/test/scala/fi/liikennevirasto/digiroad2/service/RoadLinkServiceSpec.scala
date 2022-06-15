package fi.liikennevirasto.digiroad2.service

import fi.liikennevirasto.digiroad2.asset.DateParser._
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset.TrafficDirection.{AgainstDigitizing, BothDirections, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.FeatureClass.AllOthers
import fi.liikennevirasto.digiroad2.client.vvh._
import fi.liikennevirasto.digiroad2.dao.RoadLinkDAO
import fi.liikennevirasto.digiroad2.dao.RoadLinkDAO.LinkAttributesDao
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.util.UpdateIncompleteLinkList.generateProperties
import fi.liikennevirasto.digiroad2.util.{TestTransactions, VVHSerializer}
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, DummyEventBus, DummySerializer, Point}
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfter, FunSuite, Matchers}
import org.slf4j.LoggerFactory
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

import scala.collection.immutable.Stream.Empty
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}


class RoadLinkServiceSpec extends FunSuite with Matchers with BeforeAndAfter {

  class TestService(roadLinkClient: RoadLinkClient, eventBus: DigiroadEventBus = new DummyEventBus, vvhSerializer: VVHSerializer = new DummySerializer) extends RoadLinkService(roadLinkClient, eventBus, vvhSerializer) {
    override def withDynTransaction[T](f: => T): T = f
    override def withDynSession[T](f: => T): T = f
  }

  class RoadLinkTestService(roadLinkClient: RoadLinkClient, eventBus: DigiroadEventBus = new DummyEventBus, vvhSerializer: VVHSerializer = new DummySerializer) extends RoadLinkService(roadLinkClient, eventBus, vvhSerializer) {
    override def withDynTransaction[T](f: => T): T = f
    override def withDynSession[T](f: => T): T = f
  }

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)
  
  val logger = LoggerFactory.getLogger(getClass)

  private def simulateQuery[T](f: => T): T = {
    val result = f
    sqlu"""delete from temp_string_id""".execute
    result
  }

  test("Override road link traffic direction with adjusted value") {
    PostGISDatabase.withDynTransaction {
      val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
      val mockKgvRoadLinkClient = MockitoSugar.mock[KgvRoadLinkClient]
      when(mockRoadLinkClient.roadLinkData).thenReturn(mockKgvRoadLinkClient)
      when(mockKgvRoadLinkClient.fetchByLinkIds(Set("1611447")))
        .thenReturn(Seq(RoadLinkFetched("1611447", 91, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
      val service = new TestService(mockRoadLinkClient)
      val roadLinks = service.getRoadLinksByLinkIdsFromVVH(Set("1611447"))
      roadLinks.find {
        _.linkId == "1611447"
      }.map(_.trafficDirection) should be(Some(TrafficDirection.AgainstDigitizing))
      dynamicSession.rollback()
    }
  }

  test("Include road link functional class with adjusted value") {
    PostGISDatabase.withDynTransaction {
      val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
      val mockKgvRoadLinkClient = MockitoSugar.mock[KgvRoadLinkClient]
      when(mockRoadLinkClient.roadLinkData).thenReturn(mockKgvRoadLinkClient)
      when(mockKgvRoadLinkClient.fetchByLinkIds(Set("1611447")))
        .thenReturn(Seq(RoadLinkFetched("1611447", 91, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
      val service = new TestService(mockRoadLinkClient)
      val roadLinks = service.getRoadLinksByLinkIdsFromVVH(Set("1611447"))
      roadLinks.find {_.linkId == "1611447"}.map(_.functionalClass) should be(Some(4))
      dynamicSession.rollback()
    }
  }

  test("Modified traffic Direction in a Complementary RoadLink") {
    PostGISDatabase.withDynTransaction {
      val oldRoadLink = RoadLinkFetched("30", 235, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, attributes = Map("MTKCLASS" -> BigInt(12314)))
      val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
      val mockKgvRoadLinkClient = MockitoSugar.mock[KgvRoadLinkClient]

      val mockVVHComplementaryClient = MockitoSugar.mock[VVHComplementaryClient]
      val service = new TestService(mockRoadLinkClient)

      when(mockRoadLinkClient.roadLinkData).thenReturn(mockKgvRoadLinkClient)
      when(mockKgvRoadLinkClient.fetchByLinkId("30")).thenReturn(None)
      when(mockRoadLinkClient.complementaryData).thenReturn(mockVVHComplementaryClient)
      when(mockVVHComplementaryClient.fetchByLinkId("30")).thenReturn(Some(oldRoadLink))

      val linkProperty = LinkProperties("30", 8, CycleOrPedestrianPath, TrafficDirection.BothDirections, Municipality)
      val roadLink = service.updateLinkProperties(linkProperty, Option("testuser"), { (_, _) => })
      roadLink.map(_.trafficDirection) should be(Some(TrafficDirection.BothDirections))
      roadLink.map(_.attributes("ROADCLASS")) should be (Some(12314))
      dynamicSession.rollback()
    }
  }

  //this fail at seventh consecutive test suite run
  test("Adjust link type") {
    PostGISDatabase.withDynTransaction {
      val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
      val mockKgvRoadLinkClient = MockitoSugar.mock[KgvRoadLinkClient]

      when(mockRoadLinkClient.roadLinkData).thenReturn(mockKgvRoadLinkClient)
      when(mockKgvRoadLinkClient.fetchByLinkId("1"))
        .thenReturn(Some(RoadLinkFetched("1", 91, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
      val service = new TestService(mockRoadLinkClient)
      val linkProperty = LinkProperties("1", 5, PedestrianZone, TrafficDirection.BothDirections, Municipality)
      val roadLink = service.updateLinkProperties(linkProperty, Option("testuser"), { (_, _) => })
      roadLink.map(_.linkType) should be(Some(PedestrianZone))
      dynamicSession.rollback()
    }
  }

  test("Override administrative class") {
    PostGISDatabase.withDynTransaction {
      val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
      val mockKgvRoadLinkClient = MockitoSugar.mock[KgvRoadLinkClient]

      when(mockRoadLinkClient.roadLinkData).thenReturn(mockKgvRoadLinkClient)
      when(mockKgvRoadLinkClient.fetchByLinkId("1"))
        .thenReturn(Some(RoadLinkFetched("1", 91, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
      val service = new TestService(mockRoadLinkClient)
      val linkProperty = LinkProperties("1", 5, PedestrianZone, TrafficDirection.UnknownDirection, Private)
      val roadLink = service.updateLinkProperties(linkProperty, Option("testuser"), { (_, _) => })
      roadLink.map(_.administrativeClass) should be(Some(Private))
      dynamicSession.rollback()
    }
  }

  test("Provide last edited date from VVH on road link modification date if there are no overrides") {
    PostGISDatabase.withDynTransaction {
      val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
      val mockKgvRoadLinkClient = MockitoSugar.mock[KgvRoadLinkClient]
      val mockVVHChangeInfoClient = MockitoSugar.mock[VVHChangeInfoClient]

      val lastEditedDate = DateTime.now()
      val roadLinks = Seq(RoadLinkFetched("1", 0, Nil, Municipality, TrafficDirection.TowardsDigitizing, AllOthers, Some(lastEditedDate)))
      when(mockRoadLinkClient.roadLinkData).thenReturn(mockKgvRoadLinkClient)
      when(mockRoadLinkClient.roadLinkChangeInfo).thenReturn(mockVVHChangeInfoClient)
      when(mockKgvRoadLinkClient.fetchByMunicipalitiesAndBoundsF(any[BoundingRectangle], any[Set[Int]])).thenReturn(Promise.successful(roadLinks).future)
      when(mockVVHChangeInfoClient.fetchByBoundsAndMunicipalitiesF(any[BoundingRectangle], any[Set[Int]])).thenReturn(Promise.successful(Nil).future)

      val service = new TestService(mockRoadLinkClient)
      val results = service.getRoadLinksFromVVH(BoundingRectangle(Point(0.0, 0.0), Point(1.0, 1.0)))
      results.head.modifiedAt should be(Some(DateTimePropertyFormat.print(lastEditedDate)))
      dynamicSession.rollback()
    }
  }

  test("Adjust link traffic direction to value that is in VVH") {
    PostGISDatabase.withDynTransaction {
      val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
      val mockKgvRoadLinkClient = MockitoSugar.mock[KgvRoadLinkClient]
      val linkId = "1"

      when(mockRoadLinkClient.roadLinkData).thenReturn(mockKgvRoadLinkClient)
      when(mockKgvRoadLinkClient.fetchByLinkId(linkId))
        .thenReturn(Some(RoadLinkFetched(linkId, 91, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
      val service = new TestService(mockRoadLinkClient)
      val roadLink = simulateQuery {
        val linkProperty = LinkProperties(linkId, 5, PedestrianZone, TrafficDirection.BothDirections, Municipality)
        service.updateLinkProperties(linkProperty, Option("testuser"), { (_, _) => })
      }
      roadLink.map(_.trafficDirection) should be(Some(TrafficDirection.BothDirections))
      val roadLink2 = simulateQuery {
        val linkProperty = LinkProperties(linkId, 5, PedestrianZone, TrafficDirection.TowardsDigitizing, Municipality)
        service.updateLinkProperties(linkProperty, Option("testuser"), { (_, _) => })
      }
      roadLink2.map(_.trafficDirection) should be(Some(TrafficDirection.TowardsDigitizing))
      dynamicSession.rollback()
    }
  }

  test("Adjust non-existent road link") {
    val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
    val mockKgvRoadLinkClient = MockitoSugar.mock[KgvRoadLinkClient]
    val mockVVHComplementaryClient = MockitoSugar.mock[VVHComplementaryClient]
    val linkId = "1"

    when(mockRoadLinkClient.roadLinkData).thenReturn(mockKgvRoadLinkClient)
    when(mockKgvRoadLinkClient.fetchByLinkId(linkId)).thenReturn(None)
    when(mockRoadLinkClient.complementaryData).thenReturn(mockVVHComplementaryClient)
    when(mockVVHComplementaryClient.fetchByLinkId(linkId)).thenReturn(None)

    val service = new RoadLinkService(mockRoadLinkClient, new DummyEventBus, new DummySerializer)
    val linkProperty = LinkProperties(linkId, 5, PedestrianZone, TrafficDirection.BothDirections, Municipality)
    val roadLink = service.updateLinkProperties(linkProperty, Option("testuser"), { (_, _) => })
    roadLink.map(_.linkType) should be(None)
  }

  test("Validate access rights to municipality") {
    PostGISDatabase.withDynTransaction {
      val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
      val mockKgvRoadLinkClient = MockitoSugar.mock[KgvRoadLinkClient]
      val linkId = "1"

      when(mockRoadLinkClient.roadLinkData).thenReturn(mockKgvRoadLinkClient)
      when(mockKgvRoadLinkClient.fetchByLinkId(linkId))
        .thenReturn(Some(RoadLinkFetched(linkId, 91, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
      val service = new TestService(mockRoadLinkClient)
      var validatedCode = 0
      val linkProperty = LinkProperties(linkId, 5, PedestrianZone, TrafficDirection.BothDirections, Municipality)
      service.updateLinkProperties(linkProperty, Option("testuser"), { (municipalityCode, _) =>
        validatedCode = municipalityCode
      })
      validatedCode should be(91)
      dynamicSession.rollback()
    }
  }


  test("Autogenerate properties for tractor road, drive path, cycle or cpedestrian path, special transport with and without gate") {
    PostGISDatabase.withDynTransaction {
      val (linkId1, linkId2, linkId3, linkId4, linkId5, linkId6) = ("123", "456", "789", "111", "222", "333")
      val vvhRoadLinks = List(
        RoadLinkFetched(linkId1, 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.DrivePath),
        RoadLinkFetched(linkId2, 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.TractorRoad),
        RoadLinkFetched(linkId3, 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers),
        RoadLinkFetched(linkId4, 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.CycleOrPedestrianPath),
        RoadLinkFetched(linkId5, 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.SpecialTransportWithoutGate),
        RoadLinkFetched(linkId6, 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.SpecialTransportWithGate))

      sqlu"""delete from incomplete_link where municipality_code = 91""".execute
      sqlu"""insert into incomplete_link(id, link_id, municipality_code) values(3123123123, $linkId2, 91)""".execute
      val roadLinks = generateProperties(vvhRoadLinks)

      roadLinks.find(_.linkId == linkId1).get.functionalClass should be(6)
      roadLinks.find(_.linkId == linkId1).get.linkType should be(SingleCarriageway)

      roadLinks.find(_.linkId == linkId2).get.functionalClass should be(7)
      roadLinks.find(_.linkId == linkId2).get.linkType should be(TractorRoad)

      roadLinks.find(_.linkId == linkId3).get.functionalClass should be(UnknownFunctionalClass.value)
      roadLinks.find(_.linkId == linkId3).get.linkType should be(UnknownLinkType)

      roadLinks.find(_.linkId == linkId4).get.functionalClass should be(8)
      roadLinks.find(_.linkId == linkId4).get.linkType should be(CycleOrPedestrianPath)

      roadLinks.find(_.linkId == linkId5).get.functionalClass should be(99)
      roadLinks.find(_.linkId == linkId5).get.linkType should be(SpecialTransportWithoutGate)

      roadLinks.find(_.linkId == linkId6).get.functionalClass should be(99)
      roadLinks.find(_.linkId == linkId6).get.linkType should be(SpecialTransportWithGate)

      dynamicSession.rollback()
    }
  }

  //this fail at fifth consecutive test suite run
  test("Remove road link from incomplete link list once functional class and link type are specified") {
    PostGISDatabase.withDynTransaction {
      val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
      val mockKgvRoadLinkClient = MockitoSugar.mock[KgvRoadLinkClient]
      val linkId = "1"

      val roadLink = RoadLinkFetched(linkId, 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers)
      when(mockRoadLinkClient.roadLinkData).thenReturn(mockKgvRoadLinkClient)
      when(mockKgvRoadLinkClient.fetchByLinkId(linkId)).thenReturn(Some(roadLink))
      val service = new TestService(mockRoadLinkClient)

      sqlu"""insert into incomplete_link (id, link_id, municipality_code, administrative_class) values (43241231233, 1, 91, 1)""".execute

      simulateQuery {
        val linkProperty = LinkProperties(linkId, UnknownFunctionalClass.value, Freeway, TrafficDirection.BothDirections, Municipality)
        service.updateLinkProperties(linkProperty, Option("test"), { (_, _) => })
      }
      simulateQuery {
        val linkProperty = LinkProperties(linkId, 4, UnknownLinkType, TrafficDirection.BothDirections, Municipality)
        service.updateLinkProperties(linkProperty, Option("test"), { (_, _) => })
      }
      val incompleteLinks = service.getIncompleteLinks(Some(Set(91)))
      incompleteLinks should be(empty)

      dynamicSession.rollback()
    }
  }

  test("Should map link properties of old link to new link when one old link maps to one new link") {
    val oldLinkId = "1"
    val newLinkId = "2"
    val changeInfo = ChangeInfo(Some(oldLinkId), Some(newLinkId), 123l, 5, Some(0), Some(1), Some(0), Some(1), 144000000)
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val oldRoadLink = RoadLinkFetched(oldLinkId, 235, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, attributes = Map("MUNICIPALITYCODE" -> BigInt(235)))
    val newRoadLink = RoadLinkFetched(newLinkId, 235, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, attributes = Map("MUNICIPALITYCODE" -> BigInt(235)))

    val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
    val mockKgvRoadLinkClient = MockitoSugar.mock[KgvRoadLinkClient]
    val mockVVHChangeInfoClient = MockitoSugar.mock[VVHChangeInfoClient]

    val service = new TestService(mockRoadLinkClient)

    PostGISDatabase.withDynTransaction {
      sqlu"""insert into functional_class (id, link_id, functional_class, modified_by) values (1, $oldLinkId, 3, 'test' )""".execute
      sqlu"""insert into link_type (id, link_id, link_type, modified_by) values (2, $oldLinkId, ${Freeway.value}, 'test' )""".execute
      sqlu"""insert into traffic_direction (id, link_id, traffic_direction, modified_by) values (3, $oldLinkId, ${TrafficDirection.BothDirections.value}, 'test' )""".execute

      when(mockRoadLinkClient.roadLinkData).thenReturn(mockKgvRoadLinkClient)
      when(mockRoadLinkClient.roadLinkChangeInfo).thenReturn(mockVVHChangeInfoClient)
      when(mockKgvRoadLinkClient.fetchByMunicipalitiesAndBoundsF(boundingBox, Set())).thenReturn(Promise.successful(Seq(oldRoadLink)).future)
      when(mockVVHChangeInfoClient.fetchByBoundsAndMunicipalitiesF(boundingBox, Set())).thenReturn(Promise.successful(Nil).future)
      val before = service.getRoadLinksFromVVH(boundingBox)
      before.head.functionalClass should be(3)
      before.head.linkType should be(Freeway)
      before.head.trafficDirection should be(TrafficDirection.BothDirections)

      val after = service.enrichRoadLinksFromVVH(Seq(oldRoadLink, newRoadLink))
      after.head.functionalClass should be(3)
      after.head.linkType should be(Freeway)
      after.head.trafficDirection should be(TrafficDirection.BothDirections)

      dynamicSession.rollback()
    }
  }

  test("Shoul map link properties of old link to new link when multiple old links map to new link and all have same values") {
    val oldLinkId1 = "1"
    val oldLinkId2 = "2"
    val newLinkId = "3"
    val changeInfo = Seq(ChangeInfo(Some(oldLinkId1), Some(newLinkId), 123l, 5, Some(0), Some(1), Some(0), Some(1), 144000000), ChangeInfo(Some(oldLinkId2), Some(newLinkId), 345l, 5, Some(0), Some(1), Some(0), Some(1), 144000000))
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val oldRoadLinks = Seq(RoadLinkFetched(oldLinkId1, 235, Nil, Municipality, TrafficDirection.AgainstDigitizing, FeatureClass.AllOthers, attributes = Map("MUNICIPALITYCODE" -> BigInt(235))),
      RoadLinkFetched(oldLinkId2, 235, Nil, Municipality, TrafficDirection.AgainstDigitizing, FeatureClass.AllOthers, attributes = Map("MUNICIPALITYCODE" -> BigInt(235))))
    val newRoadLink = RoadLinkFetched(newLinkId, 235, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, attributes = Map("MUNICIPALITYCODE" -> BigInt(235)))

    val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
    val mockKgvRoadLinkClient = MockitoSugar.mock[KgvRoadLinkClient]
    val mockVVHChangeInfoClient = MockitoSugar.mock[VVHChangeInfoClient]
    val service = new TestService(mockRoadLinkClient)

    PostGISDatabase.withDynTransaction {
      sqlu"""insert into functional_class (id, link_id, functional_class, modified_by) values (1, $oldLinkId1, 3, 'test' )""".execute
      sqlu"""insert into functional_class (id, link_id, functional_class, modified_by) values (2, $oldLinkId2, 3, 'test' )""".execute
      sqlu"""insert into traffic_direction (id, link_id, traffic_direction, modified_by) values (3, $oldLinkId1, ${TrafficDirection.TowardsDigitizing.value}, 'test' )""".execute
      sqlu"""insert into traffic_direction (id, link_id, traffic_direction, modified_by) values (4, $oldLinkId2, ${TrafficDirection.TowardsDigitizing.value}, 'test' )""".execute
      sqlu"""insert into link_type (id, link_id, link_type, modified_by) values (5, $oldLinkId1, ${Freeway.value}, 'test' )""".execute
      sqlu"""insert into link_type (id, link_id, link_type, modified_by) values (6, $oldLinkId2, ${Freeway.value}, 'test' )""".execute

      when(mockRoadLinkClient.roadLinkData).thenReturn(mockKgvRoadLinkClient)
      when(mockRoadLinkClient.roadLinkChangeInfo).thenReturn(mockVVHChangeInfoClient)
      when(mockKgvRoadLinkClient.fetchByMunicipalitiesAndBoundsF(boundingBox, Set())).thenReturn(Promise.successful(oldRoadLinks).future)
      when(mockVVHChangeInfoClient.fetchByBoundsAndMunicipalitiesF(boundingBox, Set())).thenReturn(Promise.successful(Nil).future)
      val before = service.getRoadLinksFromVVH(boundingBox)
      before.foreach(_.functionalClass should be(3))
      before.foreach(_.linkType should be(Freeway))
      before.foreach(_.trafficDirection should be(TrafficDirection.TowardsDigitizing))

      when(mockKgvRoadLinkClient.fetchByMunicipalitiesAndBoundsF(boundingBox, Set())).thenReturn(Promise.successful(Seq(newRoadLink)).future)
      when(mockVVHChangeInfoClient.fetchByBoundsAndMunicipalitiesF(boundingBox, Set())).thenReturn(Promise.successful(changeInfo).future)
      val after = generateProperties(Seq(newRoadLink) ++ oldRoadLinks)
      after.head.functionalClass should be(3)
      after.head.linkType should be(Freeway)
      after.head.trafficDirection should be(TrafficDirection.TowardsDigitizing)


      dynamicSession.rollback()
    }
  }

  test("""Functional class and link type should be unknown
         and traffic direction same as for the new VVH link
         when multiple old links map to new link but have different properties values""") {
    val oldLinkId1 = "1"
    val oldLinkId2 = "2"
    val newLinkId = "3"
    val changeInfo = Seq(ChangeInfo(Some(oldLinkId1), Some(newLinkId), 123l, 5, Some(0), Some(1), Some(0), Some(1), 144000000), ChangeInfo(Some(oldLinkId2), Some(newLinkId), 345l, 5, Some(0), Some(1), Some(0), Some(1), 144000000))
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val oldRoadLinks = Seq(
      RoadLinkFetched(oldLinkId1, 235, Nil, Municipality, TrafficDirection.AgainstDigitizing, FeatureClass.AllOthers, attributes = Map("MUNICIPALITYCODE" -> BigInt(235))),
      RoadLinkFetched(oldLinkId2, 235, Nil, Municipality, TrafficDirection.AgainstDigitizing, FeatureClass.AllOthers, attributes = Map("MUNICIPALITYCODE" -> BigInt(235))))
    val newRoadLink = RoadLinkFetched(newLinkId, 235, Nil, Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, attributes = Map("MUNICIPALITYCODE" -> BigInt(235)))

    val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
    val mockKgvRoadLinkClient = MockitoSugar.mock[KgvRoadLinkClient]
    val mockVVHChangeInfoClient = MockitoSugar.mock[VVHChangeInfoClient]
    val service = new TestService(mockRoadLinkClient)

    PostGISDatabase.withDynTransaction {
      sqlu"""insert into functional_class (id, link_id, functional_class, modified_by) values (1, $oldLinkId1, 3, 'test' )""".execute
      sqlu"""insert into functional_class (id, link_id, functional_class, modified_by) values (2, $oldLinkId2, 2, 'test' )""".execute
      sqlu"""insert into traffic_direction (id, link_id, traffic_direction, modified_by) values (3, $oldLinkId1, ${TrafficDirection.BothDirections.value}, 'test' )""".execute
      sqlu"""insert into traffic_direction (id, link_id, traffic_direction, modified_by) values (4, $oldLinkId2, ${TrafficDirection.TowardsDigitizing.value}, 'test' )""".execute
      sqlu"""insert into link_type (id, link_id, link_type, modified_by) values (5, $oldLinkId1, ${Freeway.value}, 'test' )""".execute
      sqlu"""insert into link_type (id, link_id, link_type, modified_by) values (6, $oldLinkId2, ${Motorway.value}, 'test' )""".execute

      when(mockRoadLinkClient.roadLinkData).thenReturn(mockKgvRoadLinkClient)
      when(mockRoadLinkClient.roadLinkChangeInfo).thenReturn(mockVVHChangeInfoClient)
      when(mockKgvRoadLinkClient.fetchByMunicipalitiesAndBoundsF(boundingBox, Set())).thenReturn(Promise.successful(oldRoadLinks).future)
      when(mockVVHChangeInfoClient.fetchByBoundsAndMunicipalitiesF(boundingBox, Set())).thenReturn(Promise.successful(Nil).future)
      val before = service.getRoadLinksFromVVH(boundingBox)
      before.length should be(2)

      when(mockKgvRoadLinkClient.fetchByMunicipalitiesAndBoundsF(boundingBox, Set())).thenReturn(Promise.successful(Seq(newRoadLink)).future)
      when(mockVVHChangeInfoClient.fetchByBoundsAndMunicipalitiesF(boundingBox, Set())).thenReturn(Promise.successful(changeInfo).future)
      val after = generateProperties(Seq(newRoadLink))
      after.head.functionalClass should be(UnknownFunctionalClass.value)
      after.head.linkType should be(UnknownLinkType)
      after.head.trafficDirection should be(TrafficDirection.BothDirections)

      dynamicSession.rollback()
    }
  }

  test("""Traffic direction should be received from VVH if it wasn't overridden in OTH""") {
    val oldLinkId1 = "1"
    val oldLinkId2 = "2"
    val newLinkId = "3"
    val changeInfo = Seq(ChangeInfo(Some(oldLinkId1), Some(newLinkId), 123l, 5, Some(0), Some(1), Some(0), Some(1), 144000000), ChangeInfo(Some(oldLinkId2), Some(newLinkId), 345l, 5, Some(0), Some(1), Some(0), Some(1), 144000000))
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val oldRoadLinks = Seq(
      RoadLinkFetched(oldLinkId1, 235, Nil, Municipality, TrafficDirection.AgainstDigitizing, FeatureClass.AllOthers, attributes = Map("MUNICIPALITYCODE" -> BigInt(235))),
      RoadLinkFetched(oldLinkId2, 235, Nil, Municipality, TrafficDirection.AgainstDigitizing, FeatureClass.AllOthers, attributes = Map("MUNICIPALITYCODE" -> BigInt(235))))
    val newRoadLink = RoadLinkFetched(newLinkId, 235, Nil, Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, attributes = Map("MUNICIPALITYCODE" -> BigInt(235)))

    val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
    val mockKgvRoadLinkClient = MockitoSugar.mock[KgvRoadLinkClient]
    val mockVVHChangeInfoClient = MockitoSugar.mock[VVHChangeInfoClient]
    val service = new TestService(mockRoadLinkClient)

    PostGISDatabase.withDynTransaction {
      sqlu"""insert into functional_class (id, link_id, functional_class, modified_by) values (1, $oldLinkId1, 3, 'test' )""".execute
      sqlu"""insert into functional_class (id, link_id, functional_class, modified_by) values (2, $oldLinkId2, 3, 'test' )""".execute
      sqlu"""insert into link_type (id, link_id, link_type, modified_by) values (5, $oldLinkId1, ${Freeway.value}, 'test' )""".execute
      sqlu"""insert into link_type (id, link_id, link_type, modified_by) values (6, $oldLinkId2, ${Freeway.value}, 'test' )""".execute


      when(mockRoadLinkClient.roadLinkData).thenReturn(mockKgvRoadLinkClient)
      when(mockRoadLinkClient.roadLinkChangeInfo).thenReturn(mockVVHChangeInfoClient)
      when(mockKgvRoadLinkClient.fetchByMunicipalitiesAndBoundsF(boundingBox, Set())).thenReturn(Promise.successful(oldRoadLinks).future)
      when(mockVVHChangeInfoClient.fetchByBoundsAndMunicipalitiesF(boundingBox, Set())).thenReturn(Promise.successful(Nil).future)
      val before = service.getRoadLinksFromVVH(boundingBox)
      before.length should be(2)

      when(mockKgvRoadLinkClient.fetchByMunicipalitiesAndBoundsF(boundingBox, Set())).thenReturn(Promise.successful(Seq(newRoadLink)).future)
      when(mockVVHChangeInfoClient.fetchByBoundsAndMunicipalitiesF(boundingBox, Set())).thenReturn(Promise.successful(changeInfo).future)
      val after = generateProperties(Seq(newRoadLink), changeInfo)
      after.head.functionalClass should be(3)
      after.head.linkType should be(Freeway)
      after.head.trafficDirection should be(TrafficDirection.BothDirections)

      dynamicSession.rollback()
    }
  }

  test("Should map link properties of old link to two new links when old link maps multiple new links in change info table") {
    val oldLinkId = "1"
    val newLinkId1 = "2"
    val newLinkId2 = "3"
    val changeInfo = Seq(ChangeInfo(Some(oldLinkId), Some(newLinkId1), 123l, 5, Some(0), Some(1), Some(0), Some(1), 144000000), ChangeInfo(Some(oldLinkId), Some(newLinkId2), 345l, 5, Some(0), Some(1), Some(0), Some(1), 144000000))
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val oldRoadLink = RoadLinkFetched(oldLinkId, 235, Nil, Municipality, TrafficDirection.AgainstDigitizing, FeatureClass.AllOthers, attributes = Map("MUNICIPALITYCODE" -> BigInt(235)))
    val newRoadLinks = Seq(
      RoadLinkFetched(newLinkId1, 235, Nil, Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, attributes = Map("MUNICIPALITYCODE" -> BigInt(235))),
      RoadLinkFetched(newLinkId2, 235, Nil, Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, attributes = Map("MUNICIPALITYCODE" -> BigInt(235))))

    val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
    val mockKgvRoadLinkClient = MockitoSugar.mock[KgvRoadLinkClient]
    val mockVVHChangeInfoClient = MockitoSugar.mock[VVHChangeInfoClient]
    val service = new TestService(mockRoadLinkClient)

    PostGISDatabase.withDynTransaction {
      sqlu"""insert into functional_class (id, link_id, functional_class, modified_by) values (1, $oldLinkId, 3, 'test' )""".execute
      sqlu"""insert into traffic_direction (id, link_id, traffic_direction, modified_by) values (3, $oldLinkId, ${TrafficDirection.TowardsDigitizing.value}, 'test' )""".execute
      sqlu"""insert into link_type (id, link_id, link_type, modified_by) values (5, $oldLinkId, ${SlipRoad.value}, 'test' )""".execute

      when(mockRoadLinkClient.roadLinkData).thenReturn(mockKgvRoadLinkClient)
      when(mockRoadLinkClient.roadLinkChangeInfo).thenReturn(mockVVHChangeInfoClient)
      when(mockKgvRoadLinkClient.fetchByMunicipalitiesAndBoundsF(boundingBox, Set())).thenReturn(Promise.successful(Seq(oldRoadLink)).future)
      when(mockVVHChangeInfoClient.fetchByBoundsAndMunicipalitiesF(boundingBox, Set())).thenReturn(Promise.successful(Nil).future)
      val before = service.getRoadLinksFromVVH(boundingBox)
      before.foreach(_.functionalClass should be(3))
      before.foreach(_.linkType should be(SlipRoad))
      before.foreach(_.trafficDirection should be(TrafficDirection.TowardsDigitizing))

      when(mockKgvRoadLinkClient.fetchByMunicipalitiesAndBoundsF(boundingBox, Set())).thenReturn(Promise.successful(newRoadLinks).future)
      when(mockVVHChangeInfoClient.fetchByBoundsAndMunicipalitiesF(boundingBox, Set())).thenReturn(Promise.successful(changeInfo).future)
      val after = generateProperties(newRoadLinks, changeInfo)
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
    val oldLinkId = "1"
    val newLinkId = "2"
    val changeInfo = ChangeInfo(Some(oldLinkId), Some(newLinkId), 123l, 5, Some(0), Some(1), Some(0), Some(1), 144000000)
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val oldRoadLink = RoadLinkFetched(oldLinkId, 235, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, attributes = Map("MUNICIPALITYCODE" -> BigInt(235)))
    val newRoadLink = RoadLinkFetched(newLinkId, 235, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, attributes = Map("MUNICIPALITYCODE" -> BigInt(235)))

    val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
    val mockKgvRoadLinkClient = MockitoSugar.mock[KgvRoadLinkClient]
    val mockVVHChangeInfoClient = MockitoSugar.mock[VVHChangeInfoClient]
    val service = new TestService(mockRoadLinkClient)

    PostGISDatabase.withDynTransaction {
      sqlu"""insert into functional_class (id, link_id, functional_class, modified_by) values (1, $oldLinkId, 3, 'test' )""".execute
      sqlu"""insert into link_type (id, link_id, link_type, modified_by) values (2, $oldLinkId, ${Freeway.value}, 'test' )""".execute
      sqlu"""insert into traffic_direction (id, link_id, traffic_direction, modified_by) values (3, $oldLinkId, ${TrafficDirection.BothDirections.value}, 'test' )""".execute

      when(mockRoadLinkClient.roadLinkData).thenReturn(mockKgvRoadLinkClient)
      when(mockRoadLinkClient.roadLinkChangeInfo).thenReturn(mockVVHChangeInfoClient)
      when(mockKgvRoadLinkClient.fetchByMunicipalitiesAndBoundsF(boundingBox, Set())).thenReturn(Promise.successful(Seq(oldRoadLink)).future)
      when(mockVVHChangeInfoClient.fetchByBoundsAndMunicipalitiesF(boundingBox, Set())).thenReturn(Promise.successful(Nil).future)
      val before = service.getRoadLinksFromVVH(boundingBox)
      before.head.functionalClass should be(3)
      before.head.linkType should be(Freeway)
      before.head.trafficDirection should be(TrafficDirection.BothDirections)

      sqlu"""delete from link_type where id=2""".execute

      when(mockKgvRoadLinkClient.fetchByMunicipalitiesAndBoundsF(boundingBox, Set())).thenReturn(Promise.successful(Seq(newRoadLink)).future)
      when(mockVVHChangeInfoClient.fetchByBoundsAndMunicipalitiesF(boundingBox, Set())).thenReturn(Promise.successful(Seq(changeInfo)).future)
      val after = generateProperties(Seq(newRoadLink), Seq(changeInfo))
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
    val oldLinkId = "1"
    val newLinkId = "2"
    val changeInfo = ChangeInfo(Some(oldLinkId), Some(newLinkId), 123l, 5, Some(0), Some(1), Some(0), Some(1), 144000000)
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val oldRoadLink = RoadLinkFetched(oldLinkId, 235, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, attributes = Map("MUNICIPALITYCODE" -> BigInt(235)))
    val newRoadLink = RoadLinkFetched(newLinkId, 235, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, attributes = Map("MUNICIPALITYCODE" -> BigInt(235)))

    val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
    val mockKgvRoadLinkClient = MockitoSugar.mock[KgvRoadLinkClient]
    val mockVVHChangeInfoClient = MockitoSugar.mock[VVHChangeInfoClient]
    val service = new TestService(mockRoadLinkClient)

    PostGISDatabase.withDynTransaction {
      sqlu"""insert into functional_class (id, link_id, functional_class, modified_by) values (1, $oldLinkId, 3, 'test' )""".execute
      sqlu"""insert into link_type (id, link_id, link_type, modified_by) values (3, $oldLinkId, ${Freeway.value}, 'test' )""".execute
      sqlu"""insert into traffic_direction (id, link_id, traffic_direction, modified_by) values (4, $oldLinkId, ${TrafficDirection.BothDirections.value}, 'test' )""".execute

      when(mockRoadLinkClient.roadLinkData).thenReturn(mockKgvRoadLinkClient)
      when(mockRoadLinkClient.roadLinkChangeInfo).thenReturn(mockVVHChangeInfoClient)
      when(mockKgvRoadLinkClient.fetchByMunicipalitiesAndBoundsF(boundingBox, Set())).thenReturn(Promise.successful(Seq(oldRoadLink)).future)
      when(mockVVHChangeInfoClient.fetchByBoundsAndMunicipalitiesF(boundingBox, Set())).thenReturn(Promise.successful(Nil).future)
      val before = service.getRoadLinksFromVVH(boundingBox)
      before.head.functionalClass should be(3)
      before.head.linkType should be(Freeway)
      before.head.trafficDirection should be(TrafficDirection.BothDirections)

      sqlu"""insert into functional_class (id, link_id, functional_class, modified_by) values (2, $newLinkId, 6, 'test' )""".execute

      when(mockKgvRoadLinkClient.fetchByMunicipalitiesAndBoundsF(boundingBox, Set())).thenReturn(Promise.successful(Seq(newRoadLink)).future)
      when(mockVVHChangeInfoClient.fetchByBoundsAndMunicipalitiesF(boundingBox, Set())).thenReturn(Promise.successful(Seq(changeInfo)).future)
      val after = generateProperties(Seq(newRoadLink), Seq(changeInfo))
      after.head.functionalClass should be(6)
      after.head.linkType should be(Freeway)
      after.head.trafficDirection should be(TrafficDirection.BothDirections)

      dynamicSession.rollback()
    }
  }

  test("Should take the latest time stamp (from VVH road link or from link properties in db) to show in UI") {

    val linkId = "1"
    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))

    val roadLink = RoadLinkFetched(linkId, 235, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, Option(new DateTime("2016-02-12T12:55:04")), attributes = Map("MUNICIPALITYCODE" -> BigInt(235)))

    val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
    val mockKgvRoadLinkClient = MockitoSugar.mock[KgvRoadLinkClient]
    val mockVVHChangeInfoClient = MockitoSugar.mock[VVHChangeInfoClient]
    val service = new TestService(mockRoadLinkClient)

    PostGISDatabase.withDynTransaction {
      sqlu"""insert into functional_class (id, link_id, functional_class, modified_by, modified_date) values (1, $linkId, 3, 'test', TO_TIMESTAMP('2014-02-10 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'))""".execute
      sqlu"""insert into traffic_direction (id, link_id, traffic_direction, modified_by, modified_date) values (2, $linkId, ${TrafficDirection.TowardsDigitizing.value}, 'test', TO_TIMESTAMP('2014-02-10 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'))""".execute
      sqlu"""insert into link_type (id, link_id, link_type, modified_by, modified_date) values (5, $linkId, ${Freeway.value}, 'test', TO_TIMESTAMP('2015-03-10 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'))""".execute

      when(mockRoadLinkClient.roadLinkData).thenReturn(mockKgvRoadLinkClient)
      when(mockRoadLinkClient.roadLinkChangeInfo).thenReturn(mockVVHChangeInfoClient)
      when(mockKgvRoadLinkClient.fetchByMunicipalitiesAndBoundsF(boundingBox, Set())).thenReturn(Promise.successful(Seq(roadLink)).future)
      when(mockVVHChangeInfoClient.fetchByBoundsAndMunicipalitiesF(boundingBox, Set())).thenReturn(Promise.successful(Nil).future)
      val roadLinkAfterDateComparison = service.getRoadLinksFromVVH(boundingBox).head

      roadLinkAfterDateComparison.modifiedAt.get should be ("12.02.2016 12:55:04")
      roadLinkAfterDateComparison.modifiedBy.get should be ("vvh_modified")

      dynamicSession.rollback()
    }
  }

  //Ignored because "linkProperties:changed" event not in use currently
  ignore("Only road links with construction type 'in use' should be saved to incomplete_link table (not 'under construction' or 'planned')") {
    PostGISDatabase.withDynTransaction {
      val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
      val mockKgvRoadLinkClient = MockitoSugar.mock[KgvRoadLinkClient]
      val mockVVHChangeInfoClient = MockitoSugar.mock[VVHChangeInfoClient]
      val mockVVHComplementaryDataClient = MockitoSugar.mock[VVHComplementaryClient]
      val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
      val vvhRoadLink1 = RoadLinkFetched("1", 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, constructionType = ConstructionType.InUse, linkSource = LinkGeomSource.NormalLinkInterface)
      val vvhRoadLink2 = RoadLinkFetched("2", 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, constructionType = ConstructionType.UnderConstruction, linkSource = LinkGeomSource.NormalLinkInterface)
      val vvhRoadLink3 = RoadLinkFetched("3", 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, constructionType = ConstructionType.Planned, linkSource = LinkGeomSource.NormalLinkInterface)
      val vvhRoadLink4 = RoadLinkFetched("4", 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, constructionType = ConstructionType.InUse, linkSource = LinkGeomSource.NormalLinkInterface)

      when(mockRoadLinkClient.roadLinkData).thenReturn(mockKgvRoadLinkClient)
      when(mockRoadLinkClient.roadLinkChangeInfo).thenReturn(mockVVHChangeInfoClient)
      when(mockRoadLinkClient.complementaryData).thenReturn(mockVVHComplementaryDataClient)
      when(mockVVHComplementaryDataClient.fetchWalkwaysByMunicipalitiesF(91)).thenReturn(Promise.successful(Seq()).future)
      when(mockKgvRoadLinkClient.fetchByMunicipalityF(91)).thenReturn(Promise.successful(Seq(vvhRoadLink1, vvhRoadLink2, vvhRoadLink3, vvhRoadLink4)).future)
      when(mockVVHChangeInfoClient.fetchByMunicipalityF(91)).thenReturn(Promise.successful(Nil).future)
      val service = new TestService(mockRoadLinkClient, mockEventBus)
      val roadLinks = service.getRoadLinksFromVVH(91)

      // Return all road links (all are incomplete here)
      val roadLink1 = RoadLink("1",List(),0.0,Municipality,99,TrafficDirection.TowardsDigitizing,UnknownLinkType,None,None,Map(),ConstructionType.InUse, linkSource = LinkGeomSource.NormalLinkInterface)
      val roadLink2 = RoadLink("2",List(),0.0,Municipality,99,TrafficDirection.TowardsDigitizing,UnknownLinkType,None,None,Map(),ConstructionType.UnderConstruction, linkSource = LinkGeomSource.NormalLinkInterface)
      val roadLink3 = RoadLink("3",List(),0.0,Municipality,99,TrafficDirection.TowardsDigitizing,UnknownLinkType,None,None,Map(),ConstructionType.Planned, linkSource = LinkGeomSource.NormalLinkInterface)
      val roadLink4 = RoadLink("4",List(),0.0,Municipality,99,TrafficDirection.TowardsDigitizing,UnknownLinkType,None,None,Map(),ConstructionType.InUse, linkSource = LinkGeomSource.NormalLinkInterface)
      roadLinks.equals(Seq(roadLink1, roadLink2, roadLink3, roadLink4))

      // Pass only incomplete road links with construction type 'in use' to be saved with actor
      val changeSet = RoadLinkChangeSet(Seq(), List(IncompleteLink("1",91,Municipality), IncompleteLink("4",91,Municipality)), List(), roadLinks.sortBy(_.linkId))
      verify(mockEventBus).publish(
        org.mockito.ArgumentMatchers.eq("linkProperties:changed"),
        org.mockito.ArgumentMatchers.eq(changeSet))

      dynamicSession.rollback()
    }
  }

  //Ignored because "linkProperties:changed" event not in use currently
  ignore("Should not save links to incomplete_link when the road source is not normal") {
    PostGISDatabase.withDynTransaction {
      val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
      val mockKgvRoadLinkClient = MockitoSugar.mock[KgvRoadLinkClient]
      val mockVVHChangeInfoClient = MockitoSugar.mock[VVHChangeInfoClient]
      val mockVVHComplementaryDataClient = MockitoSugar.mock[VVHComplementaryClient]
      val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
      val vvhRoadLink1 = RoadLinkFetched("1", 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, constructionType = ConstructionType.InUse, linkSource = LinkGeomSource.FrozenLinkInterface)
      val vvhRoadLink2 = RoadLinkFetched("2", 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, constructionType = ConstructionType.InUse, linkSource = LinkGeomSource.HistoryLinkInterface)
      val vvhRoadLink3 = RoadLinkFetched("3", 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, constructionType = ConstructionType.InUse, linkSource = LinkGeomSource.SuravageLinkInterface)
      val vvhRoadLink4 = RoadLinkFetched("4", 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, constructionType = ConstructionType.InUse, linkSource = LinkGeomSource.Unknown)
      val vvhRoadLink5 = RoadLinkFetched("5", 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, constructionType = ConstructionType.InUse, linkSource = LinkGeomSource.NormalLinkInterface)
      val vvhRoadLink6 = RoadLinkFetched("6", 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, constructionType = ConstructionType.InUse, linkSource = LinkGeomSource.ComplimentaryLinkInterface)

      when(mockRoadLinkClient.roadLinkData).thenReturn(mockKgvRoadLinkClient)
      when(mockRoadLinkClient.roadLinkChangeInfo).thenReturn(mockVVHChangeInfoClient)
      when(mockRoadLinkClient.complementaryData).thenReturn(mockVVHComplementaryDataClient)
      when(mockVVHComplementaryDataClient.fetchWalkwaysByMunicipalitiesF(91)).thenReturn(Promise.successful(Seq()).future)
      when(mockKgvRoadLinkClient.fetchByMunicipalityF(91)).thenReturn(Promise.successful(Seq(vvhRoadLink1, vvhRoadLink2, vvhRoadLink3, vvhRoadLink4, vvhRoadLink5, vvhRoadLink6)).future)
      when(mockVVHChangeInfoClient.fetchByMunicipalityF(91)).thenReturn(Promise.successful(Nil).future)
      val service = new TestService(mockRoadLinkClient, mockEventBus)
      when(mockKgvRoadLinkClient.fetchByLinkId("5")).thenReturn(Some(vvhRoadLink5))

      val roadLinks = service.getRoadLinksFromVVH(91)

      // Return all road links (all are incomplete here)
      val roadLink1 = RoadLink("1",List(),0.0,Municipality,99,TrafficDirection.TowardsDigitizing,UnknownLinkType,None,None,Map(),ConstructionType.InUse, linkSource = LinkGeomSource.FrozenLinkInterface)
      val roadLink2 = RoadLink("2",List(),0.0,Municipality,99,TrafficDirection.TowardsDigitizing,UnknownLinkType,None,None,Map(),ConstructionType.InUse, linkSource = LinkGeomSource.HistoryLinkInterface)
      val roadLink3 = RoadLink("3",List(),0.0,Municipality,99,TrafficDirection.TowardsDigitizing,UnknownLinkType,None,None,Map(),ConstructionType.InUse, linkSource = LinkGeomSource.SuravageLinkInterface)
      val roadLink4 = RoadLink("4",List(),0.0,Municipality,99,TrafficDirection.TowardsDigitizing,UnknownLinkType,None,None,Map(),ConstructionType.InUse, linkSource = LinkGeomSource.Unknown)
      val roadLink5 = RoadLink("5",List(),0.0,Municipality,99,TrafficDirection.TowardsDigitizing,UnknownLinkType,None,None,Map(),ConstructionType.InUse, linkSource = LinkGeomSource.NormalLinkInterface)
      val roadLink6 = RoadLink("6",List(),0.0,Municipality,99,TrafficDirection.TowardsDigitizing,UnknownLinkType,None,None,Map(),ConstructionType.InUse, linkSource = LinkGeomSource.ComplimentaryLinkInterface)

      roadLinks.equals(Seq(roadLink1, roadLink2, roadLink3, roadLink4, roadLink5, roadLink6))

      // Pass only incomplete road links with link source normal
      val changeSet = RoadLinkChangeSet(List(),List(IncompleteLink("5",91,Municipality)),List(),roadLinks.sortBy(_.linkId))
      verify(mockEventBus).publish(
        org.mockito.ArgumentMatchers.eq("linkProperties:changed"),
        org.mockito.ArgumentMatchers.eq(changeSet))

      dynamicSession.rollback()
    }
  }

  test("Should return roadlinks and complementary roadlinks") {

    val boundingBox = BoundingRectangle(Point(123, 345), Point(567, 678))
    val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
    val mockKgvRoadLinkClient = MockitoSugar.mock[KgvRoadLinkClient]
    val mockVVHChangeInfoClient = MockitoSugar.mock[VVHChangeInfoClient]
    val mockVVHComplementaryClient = MockitoSugar.mock[VVHComplementaryClient]
    val service = new TestService(mockRoadLinkClient)

    val complRoadLink1 = RoadLinkFetched("1", 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, constructionType = ConstructionType.InUse)
    val complRoadLink2 = RoadLinkFetched("2", 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, constructionType = ConstructionType.UnderConstruction)
    val complRoadLink3 = RoadLinkFetched("3", 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, constructionType = ConstructionType.Planned)
    val complRoadLink4 = RoadLinkFetched("4", 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, constructionType = ConstructionType.InUse)

    val vvhRoadLink1 = RoadLinkFetched("5", 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, constructionType = ConstructionType.InUse)
    val vvhRoadLink2 = RoadLinkFetched("6", 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, constructionType = ConstructionType.UnderConstruction)
    val vvhRoadLink3 = RoadLinkFetched("7", 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, constructionType = ConstructionType.Planned)
    val vvhRoadLink4 = RoadLinkFetched("8", 91, Nil, Municipality, TrafficDirection.TowardsDigitizing, FeatureClass.AllOthers, constructionType = ConstructionType.InUse)

    PostGISDatabase.withDynTransaction {
      when(mockRoadLinkClient.roadLinkData).thenReturn(mockKgvRoadLinkClient)
      when(mockRoadLinkClient.roadLinkChangeInfo).thenReturn(mockVVHChangeInfoClient)
      when(mockRoadLinkClient.complementaryData).thenReturn(mockVVHComplementaryClient)
      when(mockVVHComplementaryClient.fetchWalkwaysByBoundsAndMunicipalitiesF(any[BoundingRectangle], any[Set[Int]])).thenReturn(Future(Seq(complRoadLink1, complRoadLink2, complRoadLink3, complRoadLink4)))
      when(mockVVHChangeInfoClient.fetchByBoundsAndMunicipalitiesF(any[BoundingRectangle], any[Set[Int]])).thenReturn(Future(Seq()))
      when(mockKgvRoadLinkClient.fetchByMunicipalitiesAndBoundsF(any[BoundingRectangle], any[Set[Int]])).thenReturn(Future(Seq(vvhRoadLink1, vvhRoadLink2, vvhRoadLink3, vvhRoadLink4)))

      val roadlinks = service.getRoadLinksWithComplementaryFromVVH(boundingBox, Set(91))

      roadlinks.length should be(8)
      roadlinks.map(r => r.linkId).sorted should be (Seq("1","2","3","4","5","6","7","8"))

    }
  }

  test("Return road nodes") {
    val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
    val mockVVHRoadNodesClient = MockitoSugar.mock[VVHRoadNodesClient]
    val vvhRoadNode = RoadNodesFetched(1, Point(1, 2, 3), 2, NodeType(1), 235, 1)
    val vvhRoadNode1 = RoadNodesFetched(2, Point(4, 5, 6), 2, NodeType(1), 235, 1)
    val service = new TestService(mockRoadLinkClient)

    PostGISDatabase.withDynTransaction {
      when(mockRoadLinkClient.roadNodesData).thenReturn(mockVVHRoadNodesClient)
      when(mockVVHRoadNodesClient.fetchByMunicipality(any[Int])).thenReturn(Seq(vvhRoadNode, vvhRoadNode1))

      val roadNodes = service.getRoadNodesFromVVHByMunicipality(235)
      roadNodes.size should be (2)
    }

  }

  test("Get information about changes in road names when using all other municipalities") {
    val modifiedAt = Some(DateTime.parse("2015-05-07T12:00Z"))
    val attributes: Map[String, Any] =
      Map("ROADNAMESWE" -> "ROADNAMESWE",
        "ROADNAMEFIN" -> "ROADNAMEFIN",
        "STARTTIME" -> BigInt.apply(1446132842000L),
        "MUNICIPALITYCODE" -> BigInt(91))

    val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
    val mockKgvRoadLinkClient = MockitoSugar.mock[KgvRoadLinkClient]
    when(mockRoadLinkClient.roadLinkData).thenReturn(mockKgvRoadLinkClient)
    when(mockKgvRoadLinkClient.fetchByChangesDates(DateTime.parse("2017-05-07T12:00Z"), DateTime.parse("2017-05-09T12:00Z")))
      .thenReturn(Seq(RoadLinkFetched("1611447", 91, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers, modifiedAt, attributes)))
    val service = new TestService(mockRoadLinkClient)

    PostGISDatabase.withDynTransaction {
      val changedVVHRoadlinks = service.getChanged(DateTime.parse("2017-05-07T12:00Z"), DateTime.parse("2017-05-09T12:00Z"))
      changedVVHRoadlinks.length should be(1)
      changedVVHRoadlinks.head.link.linkId should be("1611447")
      changedVVHRoadlinks.head.link.municipalityCode should be(91)
      changedVVHRoadlinks.head.value should be(attributes.get("ROADNAMEFIN").get.toString)
      changedVVHRoadlinks.head.createdAt should be(Some(DateTime.parse("2015-10-29T15:34:02.000Z")))
      changedVVHRoadlinks.head.changeType should be("Modify")
    }
  }

  test("Get information about changes in road names when using the municipalities of Ahvenanmaa") {
    val modifiedAt = Some(DateTime.parse("2015-05-07T12:00Z"))
    val attributes: Map[String, Any] =
      Map("ROADNAMESWE" -> "ROADNAMESWE",
        "ROADNAMEFIN" -> "ROADNAMEFIN",
        "STARTTIME" -> BigInt.apply(1446132842000L),
        "MUNICIPALITYCODE" -> BigInt(60))

    val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
    val mockKgvRoadLinkClient = MockitoSugar.mock[KgvRoadLinkClient]
    when(mockRoadLinkClient.roadLinkData).thenReturn(mockKgvRoadLinkClient)
    when(mockKgvRoadLinkClient.fetchByChangesDates(DateTime.parse("2017-05-07T12:00Z"), DateTime.parse("2017-05-09T12:00Z")))
      .thenReturn(Seq(RoadLinkFetched("1611447", 60, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers, modifiedAt, attributes)))
    val service = new TestService(mockRoadLinkClient)

    PostGISDatabase.withDynTransaction {
      val changedVVHRoadlinks = service.getChanged(DateTime.parse("2017-05-07T12:00Z"), DateTime.parse("2017-05-09T12:00Z"))
      changedVVHRoadlinks.length should be(1)
      changedVVHRoadlinks.head.link.linkId should be("1611447")
      changedVVHRoadlinks.head.link.municipalityCode should be(60)
      changedVVHRoadlinks.head.value should be(attributes.get("ROADNAMESWE").get.toString)
      changedVVHRoadlinks.head.createdAt should be(Some(DateTime.parse("2015-10-29T15:34:02.000Z")))
      changedVVHRoadlinks.head.changeType should be("Modify")
    }
  }

  test("Should not return roadLinks because it has FeatureClass Winter Roads") {
    PostGISDatabase.withDynTransaction {
      val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
      val vvhRoadLinks = Seq(RoadLinkFetched("1611447", 91, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.WinterRoads))
      val service = new RoadLinkTestService(mockRoadLinkClient)
      val roadLinks = generateProperties(vvhRoadLinks)
      roadLinks.length should be (0)
      dynamicSession.rollback()
    }
  }


  test("Should only return roadLinks that doesn't have FeatureClass Winter Roads") {
    PostGISDatabase.withDynTransaction {
      val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
      val vvhRoadLinks = Seq(
        RoadLinkFetched("1611447", 91, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.WinterRoads),
        RoadLinkFetched("1611448", 91, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers),
        RoadLinkFetched("1611449", 91, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers))
      val service = new RoadLinkTestService(mockRoadLinkClient)
      val roadLinks = generateProperties(vvhRoadLinks)
      roadLinks.length should be (2)
      roadLinks.sortBy(_.linkId)
      roadLinks.head.linkId should be("1611448")
      roadLinks.last.linkId should be("1611449")
      dynamicSession.rollback()
    }
  }

  def insertFunctionalClass() = {

    sqlu""" INSERT INTO FUNCTIONAL_CLASS (ID, LINK_ID, FUNCTIONAL_CLASS, MODIFIED_BY, MODIFIED_DATE) VALUES (1, '445521', 3, 'test', TO_TIMESTAMP('2014-02-10 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'))""".execute
    sqlu""" INSERT INTO FUNCTIONAL_CLASS (ID, LINK_ID, FUNCTIONAL_CLASS, MODIFIED_BY, MODIFIED_DATE) VALUES (2, '445518', 3, 'test', TO_TIMESTAMP('2014-02-10 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'))""".execute
    sqlu""" INSERT INTO FUNCTIONAL_CLASS (ID, LINK_ID, FUNCTIONAL_CLASS, MODIFIED_BY, MODIFIED_DATE) VALUES (3, '445522', 3, 'test', TO_TIMESTAMP('2014-02-10 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'))""".execute
    sqlu""" INSERT INTO FUNCTIONAL_CLASS (ID, LINK_ID, FUNCTIONAL_CLASS, MODIFIED_BY, MODIFIED_DATE) VALUES (4, '445520', 3, 'test', TO_TIMESTAMP('2014-02-10 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'))""".execute
    sqlu""" INSERT INTO FUNCTIONAL_CLASS (ID, LINK_ID, FUNCTIONAL_CLASS, MODIFIED_BY, MODIFIED_DATE) VALUES (5, '445407', 3, 'test', TO_TIMESTAMP('2014-02-10 10:03:51.047483', 'YYYY-MM-DD HH24:MI:SS.FF6'))""".execute
  }

  def insertLinkType() = {
    sqlu""" INSERT INTO LINK_TYPE (ID, LINK_ID, LINK_TYPE, MODIFIED_BY) VALUES (1, '445521', 3, 'test')""".execute
    sqlu""" INSERT INTO LINK_TYPE (ID, LINK_ID, LINK_TYPE, MODIFIED_BY) VALUES (2, '445518', 3, 'test')""".execute
    sqlu""" INSERT INTO LINK_TYPE (ID, LINK_ID, LINK_TYPE, MODIFIED_BY) VALUES (3, '445522', 3, 'test')""".execute
    sqlu""" INSERT INTO LINK_TYPE (ID, LINK_ID, LINK_TYPE, MODIFIED_BY) VALUES (4, '445520', 3, 'test')""".execute
    sqlu""" INSERT INTO LINK_TYPE (ID, LINK_ID, LINK_TYPE, MODIFIED_BY) VALUES (5, '445407', 3, 'test')""".execute
  }


  test("Should return adjacents according to given point"){
    PostGISDatabase.withDynTransaction {

      insertFunctionalClass()
      insertLinkType()
      val sourceRoadLinkVVH = RoadLinkFetched("445521", 91, Seq(Point(386028.217, 6671112.363, 20.596000000005006), Point(386133.222, 6671115.993, 21.547000000005937)), Municipality, TowardsDigitizing, FeatureClass.AllOthers)

      val vvhRoadLinks = Seq(RoadLinkFetched("445518", 91, Seq(Point(386030.813, 6671026.151, 15.243000000002212), Point(386028.217, 6671112.363, 20.596000000005006)), Municipality, BothDirections, FeatureClass.AllOthers),
        RoadLinkFetched("445521", 91, Seq(Point(386028.217, 6671112.363, 20.596000000005006), Point(386133.222, 6671115.993, 21.547000000005937)), Municipality, TowardsDigitizing, FeatureClass.AllOthers),
        RoadLinkFetched("445522", 91, Seq(Point(385935.666, 6671107.833, 19.85899999999674), Point(386028.217, 6671112.363, 20.596000000005006)), Municipality, BothDirections, FeatureClass.AllOthers),
        RoadLinkFetched("445520", 91, Seq(Point(386136.267, 6671029.985, 15.785000000003492), Point(386133.222, 6671115.993, 21.547000000005937)), Municipality, BothDirections, FeatureClass.AllOthers),
        RoadLinkFetched("445521", 91, Seq(Point(386028.217, 6671112.363, 20.596000000005006), Point(386133.222, 6671115.993, 21.547000000005937)), Municipality, TowardsDigitizing, FeatureClass.AllOthers),
        RoadLinkFetched("445407", 91, Seq(Point(386133.222, 6671115.993, 21.547000000005937), Point(386126.902, 6671320.939, 19.69199999999546)), Municipality, TowardsDigitizing, FeatureClass.AllOthers))


      val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
      val mockKgvRoadLinkClient = MockitoSugar.mock[KgvRoadLinkClient]
      val mockVVHChangeInfoClient = MockitoSugar.mock[VVHChangeInfoClient]

      when(mockRoadLinkClient.roadLinkData).thenReturn(mockKgvRoadLinkClient)
      when(mockRoadLinkClient.roadLinkChangeInfo).thenReturn(mockVVHChangeInfoClient)
      when(mockKgvRoadLinkClient.fetchByMunicipalitiesAndBoundsF(BoundingRectangle(Point(386028.117,6671112.263,20.596000000005006),Point(386028.317,6671112.4629999995,20.596000000005006)), Set())).thenReturn(Future(vvhRoadLinks))
      when(mockKgvRoadLinkClient.fetchByMunicipalitiesAndBoundsF(BoundingRectangle(Point(386133.12200000003,6671115.893,21.547000000005937),Point(386133.322,6671116.092999999,21.547000000005937)), Set())).thenReturn(Future(Seq()))
      when(mockVVHChangeInfoClient.fetchByBoundsAndMunicipalitiesF(any[BoundingRectangle], any[Set[Int]])).thenReturn(Future(Seq()))
      when(mockKgvRoadLinkClient.fetchByLinkIds(any[Set[String]])).thenReturn(Seq(sourceRoadLinkVVH))

      val service = new RoadLinkTestService(mockRoadLinkClient)
      val adjacents = service.getAdjacent("445521", Seq(Point(386133.222, 6671115.993, 21.547000000005937)))

      adjacents.size should be(2)
      val linkIds = adjacents.map(_.linkId)

      linkIds.max should be("445520")
      linkIds.min should be("445407")

      dynamicSession.rollback()
    }
  }

  test("PickMost: Should pick the most left roadLink"){
    PostGISDatabase.withDynTransaction {
      val sourceRoadLink = RoadLink("445521", Seq(Point(386028.217, 6671112.363, 20.596000000005006), Point(386133.222, 6671115.993, 21.547000000005937)), 105.06772542032195, Municipality, 6, TowardsDigitizing, Motorway, None, None, linkSource = NormalLinkInterface)

      val roadLinks =
        Seq(RoadLink("445520", Seq(Point(386136.267, 6671029.985, 15.785000000003492), Point(386133.222, 6671115.993, 21.547000000005937)), 86.06188522746326, Municipality, 6, BothDirections, SingleCarriageway, None, None, linkSource = NormalLinkInterface)
          , RoadLink("445407", Seq(Point(386133.222, 6671115.993, 21.547000000005937), Point(386126.902, 6671320.939, 19.69199999999546)), 205.04342300154235, Municipality, 6, TowardsDigitizing, SingleCarriageway, None, None, linkSource = NormalLinkInterface))

      val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]

      val service = new RoadLinkTestService(mockRoadLinkClient)
      val mostLeft = service.pickLeftMost(sourceRoadLink, roadLinks)

      mostLeft.linkId should be("445407")
    }
  }

  test("PickMost: Should pick the most right roadLink"){
    PostGISDatabase.withDynTransaction {
      val sourceRoadLink = RoadLink("445521", Seq(Point(386028.217, 6671112.363, 20.596000000005006), Point(386133.222, 6671115.993, 21.547000000005937)), 105.06772542032195, Municipality, 6, AgainstDigitizing, Motorway, None, None, linkSource = NormalLinkInterface)

      val roadLinks =
        Seq(RoadLink("445518", Seq(Point(386030.813, 6671026.151, 15.243000000002212), Point(386028.217, 6671112.363, 20.596000000005006)), 86.25107628343082, Municipality, 6, BothDirections, Motorway, None, None, linkSource = NormalLinkInterface)
          , RoadLink("445522", Seq(Point(385935.666, 6671107.833, 19.85899999999674), Point(386028.217, 6671112.363, 20.596000000005006)), 92.6617963402298, Municipality, 6, BothDirections, Motorway, None, None, linkSource = NormalLinkInterface))
      val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]

      val service = new RoadLinkTestService(mockRoadLinkClient)
      val rightMost = service.pickRightMost(sourceRoadLink, roadLinks)

      rightMost.linkId should be("445522")
    }
  }

  test("PickMost: Should pick the most right adjacent"){
    PostGISDatabase.withDynTransaction {
      val (linkId1, linkId2, linkId3) = ("5169340", "5169276", "5169274")
      val sourceGeometry = Seq(Point(533701.563,6994545.568, 100.42699999999604), Point(533700.872,6994552.548, 100.4030000000057),
                              Point(533700.608, 6994559.672,100.38499999999476), Point(533696.367,6994589.226,99.94599999999627))

      val roadLink1Geometry = Seq( Point(533696.367,6994589.226,99.94599999999627), Point(533675.111,6994589.313,100.67699999999604),
                                Point(533669.956,6994589.771,101.08000000000175), Point(533656.28,6994601.636,102.28399999999965),
                                Point(533649.832,6994618.702,102.26499999999942), Point(533647.351,6994643.607,101.22900000000664))
      val roadLink2Geometry = Seq(Point(533696.367,6994589.226,99.94599999999627), Point(533694.885,6994596.395,99.82799999999406),
                                  Point(533687.513,6994659.491,97.33999999999651), Point(533682.186,6994702.867,94.096000000005),
                                  Point(533678.296,6994729.959,91.96300000000338), Point(533675.016,6994741.734,91.28699999999662))

      val sourceRoadLink = RoadLink(linkId1, sourceGeometry, 53.2185423077318, Municipality, 6, BothDirections, Motorway, None, None, linkSource = NormalLinkInterface)

      val roadLinks =
        Seq(RoadLink(linkId2, roadLink1Geometry, 87.80880628900667, Municipality, 6, BothDirections, Motorway, None, None, linkSource = NormalLinkInterface)
          , RoadLink(linkId3, roadLink2Geometry, 154.1408100462925, Municipality, 6, BothDirections, Motorway, None, None, linkSource = NormalLinkInterface))
      val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]

      val service = new RoadLinkTestService(mockRoadLinkClient)
      val rightMost = service.pickRightMost(sourceRoadLink, roadLinks)

      rightMost.linkId should be(linkId3)
    }
  }

  test("Should pick the most left roadLink"){
    PostGISDatabase.withDynTransaction {
      val (linkId1, linkId2, linkId3) = ("445521", "445518", "445522")
      val sourceRoadLink = RoadLink(linkId1, Seq(Point(386028.217, 6671112.363, 20.596000000005006), Point(386133.222, 6671115.993, 21.547000000005937)), 105.06772542032195, Municipality, 6, BothDirections, Motorway, None, None, linkSource = NormalLinkInterface)

      val roadLinks =
        Seq(RoadLink(linkId2, Seq(Point(386030.813, 6671026.151, 15.243000000002212), Point(386028.217, 6671112.363, 20.596000000005006)), 86.25107628343082, Municipality, 6, BothDirections, Motorway, None, None, linkSource = NormalLinkInterface)
          , RoadLink(linkId3, Seq(Point(385935.666, 6671107.833, 19.85899999999674), Point(386028.217, 6671112.363, 20.596000000005006)), 92.6617963402298, Municipality, 6, BothDirections, Motorway, None, None, linkSource = NormalLinkInterface))
      val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]

      val service = new RoadLinkTestService(mockRoadLinkClient)
      val mostLeft = service.pickLeftMost(sourceRoadLink, roadLinks)

      mostLeft.linkId should be(linkId2)
    }
  }

  test("Added privateRoadAssociation, additionalInfo and accessRightId fields if private road") {
    PostGISDatabase.withDynTransaction {
      val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
      val mockKgvRoadLinkClient = MockitoSugar.mock[KgvRoadLinkClient]
      val linkId = "1"

      when(mockRoadLinkClient.roadLinkData).thenReturn(mockKgvRoadLinkClient)
      when(mockKgvRoadLinkClient.fetchByLinkId(linkId))
        .thenReturn(Some(RoadLinkFetched(linkId, 91, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
      val service = new TestService(mockRoadLinkClient)
      val linkProperty = LinkProperties(linkId, 5, PedestrianZone, TrafficDirection.UnknownDirection, Private, Some("Private Road Name Text Dummy"), Some(AdditionalInformation.DeliveredWithRestrictions), Some("999999"))
      val roadLink = service.updateLinkProperties(linkProperty, Option("testuser"), { (_, _) => })
      roadLink.map(_.administrativeClass) should be(Some(Private))
      roadLink.map(_.attributes(service.privateRoadAssociationPublicId) should be("Private Road Name Text Dummy"))
      roadLink.map(_.attributes(service.additionalInfoPublicId) should be(AdditionalInformation.DeliveredWithRestrictions.value))
      roadLink.map(_.attributes(service.accessRightIDPublicId) should be("999999"))
      dynamicSession.rollback()
    }
  }

  test("Added privateRoadAssociation, additionalInfo and accessRightId fields if road different from private") {
    PostGISDatabase.withDynTransaction {
      val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
      val mockKgvRoadLinkClient = MockitoSugar.mock[KgvRoadLinkClient]
      val linkId = "1"

      when(mockRoadLinkClient.roadLinkData).thenReturn(mockKgvRoadLinkClient)
      when(mockKgvRoadLinkClient.fetchByLinkId(linkId))
        .thenReturn(Some(RoadLinkFetched(linkId, 91, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
      val service = new TestService(mockRoadLinkClient)
      val linkProperty = LinkProperties(linkId, 5, PedestrianZone, TrafficDirection.UnknownDirection, Municipality, Some("Private Road Name Text Dummy"), Some(AdditionalInformation.DeliveredWithRestrictions), Some("999999"))
      val roadLink = service.updateLinkProperties(linkProperty, Option("testuser"), { (_, _) => })
      roadLink.map(_.administrativeClass) should be(Some(Municipality))
      roadLink.map(_.attributes.contains(service.privateRoadAssociationPublicId) should be(false))
      roadLink.map(_.attributes.contains(service.additionalInfoPublicId) should be(false))
      roadLink.map(_.attributes.contains(service.accessRightIDPublicId) should be(false))
      dynamicSession.rollback()
    }
  }

  test("Update privateRoadAssociation, additionalInfo and accessRightId fields if private road") {
    PostGISDatabase.withDynTransaction {
      val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
      val mockKgvRoadLinkClient = MockitoSugar.mock[KgvRoadLinkClient]
      val linkId = "1"

      when(mockRoadLinkClient.roadLinkData).thenReturn(mockKgvRoadLinkClient)
      when(mockKgvRoadLinkClient.fetchByLinkId(linkId))
        .thenReturn(Some(RoadLinkFetched(linkId, 91, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
      val service = new TestService(mockRoadLinkClient)
      val linkProperty = LinkProperties(linkId, 5, PedestrianZone, TrafficDirection.UnknownDirection, Private, Some("Private Road Name Text Dummy"), Some(AdditionalInformation.DeliveredWithRestrictions), Some("999999"))
      val roadLink = service.updateLinkProperties(linkProperty, Option("testuser"), { (_, _) => })
      roadLink.map(_.administrativeClass) should be(Some(Private))
      roadLink.map(_.attributes(service.privateRoadAssociationPublicId) should be("Private Road Name Text Dummy"))
      roadLink.map(_.attributes(service.additionalInfoPublicId) should be(AdditionalInformation.DeliveredWithRestrictions.value))
      roadLink.map(_.attributes(service.accessRightIDPublicId) should be("999999"))

      val linkPropertyUpdated = LinkProperties(linkId, 5, PedestrianZone, TrafficDirection.UnknownDirection, Private, Some("Private Road Name Text Dummy99"), Some(AdditionalInformation.DeliveredWithoutRestrictions), Some("11111"))
      val roadLinkUpdated = service.updateLinkProperties(linkPropertyUpdated, Option("testuser"), { (_, _) => })
      roadLinkUpdated.map(_.administrativeClass) should be(Some(Private))
      roadLinkUpdated.map(_.attributes(service.privateRoadAssociationPublicId) should be("Private Road Name Text Dummy99"))
      roadLinkUpdated.map(_.attributes(service.additionalInfoPublicId) should be(AdditionalInformation.DeliveredWithoutRestrictions.value))
      roadLinkUpdated.map(_.attributes(service.accessRightIDPublicId) should be("11111"))
      dynamicSession.rollback()
    }
  }

  test("Expire privateRoadAssociation, additionalInfo and accessRightId fields if switch private road to another type") {
    PostGISDatabase.withDynTransaction {
      val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
      val mockKgvRoadLinkClient = MockitoSugar.mock[KgvRoadLinkClient]
      val linkId = "1"

      when(mockRoadLinkClient.roadLinkData).thenReturn(mockKgvRoadLinkClient)
      when(mockKgvRoadLinkClient.fetchByLinkId(linkId))
        .thenReturn(Some(RoadLinkFetched(linkId, 91, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
      val service = new TestService(mockRoadLinkClient)
      val linkProperty = LinkProperties(linkId, 5, PedestrianZone, TrafficDirection.UnknownDirection, Private, Some("Private Road Name Text Dummy"), Some(AdditionalInformation.DeliveredWithRestrictions), Some("999999"))
      val roadLink = service.updateLinkProperties(linkProperty, Option("testuser"), { (_, _) => })
      roadLink.map(_.administrativeClass) should be(Some(Private))
      roadLink.map(_.attributes(service.privateRoadAssociationPublicId) should be("Private Road Name Text Dummy"))
      roadLink.map(_.attributes(service.additionalInfoPublicId) should be(AdditionalInformation.DeliveredWithRestrictions.value))
      roadLink.map(_.attributes(service.accessRightIDPublicId) should be("999999"))

      val linkPropertyUpdated = LinkProperties(linkId, 5, PedestrianZone, TrafficDirection.UnknownDirection, Municipality, Some("Private Road Name Text Dummy"), Some(AdditionalInformation.DeliveredWithRestrictions), Some("999999"))
      val roadLinkUpdated = service.updateLinkProperties(linkPropertyUpdated, Option("testuser"), { (_, _) => })

      val roadLinkAttributes =
        sql"""
              Select name, value From road_link_attributes where link_id = $linkId and (valid_to is null or valid_to > current_timestamp)
        """.as[(String, String)].list

      roadLinkAttributes should be (Empty)
      dynamicSession.rollback()
    }
  }

  test("filter road links considering bearing in traffic sign and bearing of the road links, same bearing, validity direction and 10 meter radius of the sign") {
    val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
    val service = new TestService(mockRoadLinkClient)

    val newLinkId1 = "5000"
    val geometryPoints1 = List(Point(60.0, 35.0), Point(60.0, 15.0), Point(50.0, 10.0), Point(30.0, 15.0), Point(10.0, 25.0))
    val trafficDirection1 = TrafficDirection.AgainstDigitizing
    val newLinkId2 = "5001"
    val geometryPoints2 = List(Point(40.0, 40.0), Point(90.0, 40.0))
    val trafficDirection2 = TrafficDirection.BothDirections
    val newLinkId3 = "5002"
    val geometryPoints3 = List(Point(80.0, 10.0), Point(80.0, 30.0))
    val trafficDirection3 = TrafficDirection.TowardsDigitizing

    val trafficSignBearing = Some(190)
    val trafficSignCoordinates = Point(70.0, 32.0)
    val municipalityCode = 564
    val administrativeClass = Municipality
    val attributes = Map("OBJECTID" -> BigInt(99))

    val newVVHRoadLink1 = RoadLinkFetched(newLinkId1, municipalityCode, geometryPoints1, administrativeClass, trafficDirection1, FeatureClass.DrivePath, None, attributes)
    val newVVHRoadLink2 = RoadLinkFetched(newLinkId2, municipalityCode, geometryPoints2, administrativeClass, trafficDirection2, FeatureClass.DrivePath, None, attributes)
    val newVVHRoadLink3 = RoadLinkFetched(newLinkId3, municipalityCode, geometryPoints3, administrativeClass, trafficDirection3, FeatureClass.DrivePath, None, attributes)
    val vVHRoadLinkSeq = Seq(newVVHRoadLink1, newVVHRoadLink2, newVVHRoadLink3)

    val newRoadLink1 = RoadLink(newLinkId1, geometryPoints1, 0.0, administrativeClass, 1, trafficDirection1, Motorway, None, None)
    val newRoadLink2 = RoadLink(newLinkId2, geometryPoints2, 0.0, administrativeClass, 1, trafficDirection2, Motorway, None, None)
    val newRoadLink3 = RoadLink(newLinkId3, geometryPoints3, 0.0, administrativeClass, 1, trafficDirection3, Motorway, None, None)
    val roadLinkSeq = Seq(newRoadLink1, newRoadLink2, newRoadLink3)

    val roadLinksFilteredByBearing = service.filterRoadLinkByBearing(trafficSignBearing, Some(TrafficDirection.toSideCode(trafficDirection1).value), trafficSignCoordinates, roadLinkSeq)

    roadLinksFilteredByBearing.size should be (1)
    roadLinksFilteredByBearing.head.linkId should be (newLinkId1)
  }

  test("filter road links considering bearing in traffic sign and bearing of the road links, different bearing in all") {
    val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
    val service = new TestService(mockRoadLinkClient)
    val newLinkId1 = "5000"
    val geometryPoints1 = List(Point(10.0, 25.0), Point(30.0, 15.0), Point(50.0, 10.0), Point(60.0, 15.0), Point(60.0, 35.0))
    val trafficDirection1 = TrafficDirection.TowardsDigitizing
    val newLinkId2 = "5001"
    val geometryPoints2 = List(Point(40.0, 40.0), Point(90.0, 40.0))
    val trafficDirection2 = TrafficDirection.TowardsDigitizing
    val newLinkId3 = "5002"
    val geometryPoints3 = List(Point(80.0, 10.0), Point(80.0, 30.0))
    val trafficDirection3 = TrafficDirection.TowardsDigitizing

    val trafficSignBearing = Some(20)
    val trafficSignCoordinates = Point(70.0, 32.0)
    val municipalityCode = 564
    val administrativeClass = Municipality
    val attributes = Map("OBJECTID" -> BigInt(99)) //delete ?

    val newVVHRoadLink1 = RoadLinkFetched(newLinkId1, municipalityCode, geometryPoints1, administrativeClass, trafficDirection1, FeatureClass.DrivePath, None, attributes)
    val newVVHRoadLink2 = RoadLinkFetched(newLinkId2, municipalityCode, geometryPoints2, administrativeClass, trafficDirection2, FeatureClass.DrivePath, None, attributes)
    val newVVHRoadLink3 = RoadLinkFetched(newLinkId3, municipalityCode, geometryPoints3, administrativeClass, trafficDirection3, FeatureClass.DrivePath, None, attributes)
    val vVHRoadLinkSeq = Seq(newVVHRoadLink1, newVVHRoadLink2, newVVHRoadLink3)

    val newRoadLink1 = RoadLink(newLinkId1, geometryPoints1, 0.0, administrativeClass, 1, trafficDirection1, Motorway, None, None)
    val newRoadLink2 = RoadLink(newLinkId2, geometryPoints2, 0.0, administrativeClass, 1, trafficDirection2, Motorway, None, None)
    val newRoadLink3 = RoadLink(newLinkId3, geometryPoints3, 0.0, administrativeClass, 1, trafficDirection3, Motorway, None, None)
    val roadLinkSeq = Seq(newRoadLink1, newRoadLink2, newRoadLink3)

    val roadLinksFilteredByBearing = service.filterRoadLinkByBearing(trafficSignBearing, Some(TrafficDirection.toSideCode(trafficDirection1).value), trafficSignCoordinates, roadLinkSeq)

    roadLinksFilteredByBearing should be (Seq(newRoadLink1, newRoadLink3))
  }

  test("filter road links considering bearing in traffic sign and bearing of the road links, road link with both traffic direction") {
    val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
    val service = new TestService(mockRoadLinkClient)
    val newLinkId1 = "5000"
    val geometryPoints1 = List(Point(60.0, 35.0), Point(60.0, 15.0), Point(50.0, 10.0), Point(30.0, 15.0), Point(10.0, 25.0))
    val trafficDirection1 = TrafficDirection.BothDirections
    val newLinkId2 = "5001"
    val geometryPoints2 = List(Point(40.0, 40.0), Point(90.0, 40.0))
    val trafficDirection2 = TrafficDirection.TowardsDigitizing
    val newLinkId3 = "5002"
    val geometryPoints3 = List(Point(80.0, 10.0), Point(80.0, 30.0))
    val trafficDirection3 = TrafficDirection.TowardsDigitizing

    val trafficSignBearing = Some(20)
    val trafficSignCoordinates = Point(70.0, 32.0)
    val municipalityCode = 564
    val administrativeClass = Municipality
    val attributes = Map("OBJECTID" -> BigInt(99)) //delete ?


    val newVVHRoadLink1 = RoadLinkFetched(newLinkId1, municipalityCode, geometryPoints1, administrativeClass, trafficDirection1, FeatureClass.DrivePath, None, attributes)
    val newVVHRoadLink2 = RoadLinkFetched(newLinkId2, municipalityCode, geometryPoints2, administrativeClass, trafficDirection2, FeatureClass.DrivePath, None, attributes)
    val newVVHRoadLink3 = RoadLinkFetched(newLinkId3, municipalityCode, geometryPoints3, administrativeClass, trafficDirection3, FeatureClass.DrivePath, None, attributes)
    val vVHRoadLinkSeq = Seq(newVVHRoadLink1, newVVHRoadLink2, newVVHRoadLink3)

    val newRoadLink1 = RoadLink(newLinkId1, geometryPoints1, 0.0, administrativeClass, 1, trafficDirection1, Motorway, None, None)
    val newRoadLink2 = RoadLink(newLinkId2, geometryPoints2, 0.0, administrativeClass, 1, trafficDirection2, Motorway, None, None)
    val newRoadLink3 = RoadLink(newLinkId3, geometryPoints3, 0.0, administrativeClass, 1, trafficDirection3, Motorway, None, None)
    val roadLinkSeq = Seq(newRoadLink1, newRoadLink2, newRoadLink3)

    val roadLinksFilteredByBearing = service.filterRoadLinkByBearing(trafficSignBearing, Some(TrafficDirection.toSideCode(trafficDirection1).value), trafficSignCoordinates, roadLinkSeq)

    roadLinksFilteredByBearing.size should be (1)
    roadLinksFilteredByBearing.head.linkId should be (newLinkId1)
  }

  test("filter road links when bearing info not sended") {
    val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
    val service = new TestService(mockRoadLinkClient)
    val newLinkId = "5000"
    val geometryPoints = List(Point(60.0, 35.0), Point(60.0, 15.0), Point(50.0, 10.0), Point(30.0, 15.0), Point(10.0, 25.0))
    val trafficDirection = TrafficDirection.BothDirections

    val trafficSignBearing = None
    val trafficSignCoordinates = Point(70.0, 32.0)
    val administrativeClass = Municipality

    val newRoadLink = RoadLink(newLinkId, geometryPoints, 0.0, administrativeClass, 1, trafficDirection, Motorway, None, None)
    val roadLinkSeq = Seq(newRoadLink)

    val roadLinksFiltered = service.filterRoadLinkByBearing(trafficSignBearing, Some(TrafficDirection.toSideCode(trafficDirection).value), trafficSignCoordinates, roadLinkSeq)

    roadLinksFiltered.size should be (1)
    roadLinksFiltered.head.linkId should be (newLinkId)
  }

  test("Test to fetch road link by private road association name") {
    PostGISDatabase.withDynTransaction {
      val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
      val mockKgvRoadLinkClient = MockitoSugar.mock[KgvRoadLinkClient]
      val mockVVHComplementaryClient = MockitoSugar.mock[VVHComplementaryClient]

      val dummyRoadAssociationName = "Dummy Road Association"
      val refactoredDummyRoadAssName = dummyRoadAssociationName.trim().toUpperCase()

      val (linkId1, linkId2, linkId3) = ("55555555", "66666666", "77777777")

      sqlu"""Insert into ROAD_LINK_ATTRIBUTES (ID, NAME, LINK_ID, VALUE, CREATED_BY) values (55555555, 'PRIVATE_ROAD_ASSOCIATION', $linkId1, $dummyRoadAssociationName, 'test_user')""".execute
      sqlu"""Insert into ROAD_LINK_ATTRIBUTES (ID, NAME, LINK_ID, VALUE, CREATED_BY) values (66666666, 'PRIVATE_ROAD_ASSOCIATION', $linkId2, $dummyRoadAssociationName, 'test_user')""".execute
      sqlu"""Insert into ROAD_LINK_ATTRIBUTES (ID, NAME, LINK_ID, VALUE, CREATED_BY) values (77777777, 'PRIVATE_ROAD_ASSOCIATION', $linkId3, $dummyRoadAssociationName, 'test_user')""".execute

      val attributesRoad1 = Map("ROADNAMEFIN" -> "Road Number 1", "MUNICIPALITYCODE" -> BigInt(16))
      val attributesRoad2 = Map("ROADNAMEFIN" -> "Road Number 2", "MUNICIPALITYCODE" -> BigInt(16))
      val attributesRoad3 = Map("ROADNAMEFIN" -> "Road Number 3", "MUNICIPALITYCODE" -> BigInt(16))

      val vvhRoadLinks = Seq(
        RoadLinkFetched(linkId1, 16, Seq(Point(386136, 6671029, 15), Point(386133, 6671115, 21)), Municipality, BothDirections, FeatureClass.AllOthers, attributes = attributesRoad1, length = 100),
        RoadLinkFetched(linkId2, 16, Seq(Point(386136, 6671029, 15), Point(386133, 6671115, 21)), Municipality, BothDirections, FeatureClass.AllOthers, attributes = attributesRoad2, length = 200),
        RoadLinkFetched(linkId3, 16, Seq(Point(386136, 6671029, 15), Point(386133, 6671115, 21)), Municipality, BothDirections, FeatureClass.AllOthers, attributes = attributesRoad3, length = 150)
      )

      val linkIds = vvhRoadLinks.map(_.linkId)

      val service = new RoadLinkService(mockRoadLinkClient, new DummyEventBus, new DummySerializer)

      when(mockRoadLinkClient.complementaryData).thenReturn(mockVVHComplementaryClient)
      when(mockVVHComplementaryClient.fetchByLinkIdsF(linkIds.toSet)).thenReturn(Future(Seq()))

      when(mockRoadLinkClient.roadLinkData).thenReturn(mockKgvRoadLinkClient)
      when(mockKgvRoadLinkClient.fetchByLinkIdsF(linkIds.toSet)).thenReturn(Future(vvhRoadLinks))

      val result = service.getPrivateRoadsByAssociationName(refactoredDummyRoadAssName, false)

      result.length should be (3)

      result.map(_.roadName).contains(attributesRoad1("ROADNAMEFIN")) should be(true)
      result.map(_.roadName).contains(attributesRoad2("ROADNAMEFIN")) should be(true)
      result.map(_.roadName).contains(attributesRoad3("ROADNAMEFIN")) should be(true)

      dynamicSession.rollback()
    }
  }

  test("Test to fetch road link by private road association name having road links with different road association name, different municipalities and no road name") {
    PostGISDatabase.withDynTransaction {
      val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
      val mockKgvRoadLinkClient = MockitoSugar.mock[KgvRoadLinkClient]
      val mockVVHComplementaryClient = MockitoSugar.mock[VVHComplementaryClient]

      val dummyRoadAssociationNameNumberOne = "Dummy Road Association number one"
      val refactoredDummyRoadAssNameNumberOne = dummyRoadAssociationNameNumberOne.trim().toUpperCase()

      val dummyRoadAssociationNameNumberTwo = "Dummy Road Association number two"
      val noRoadName = "tuntematon tienimi"

      val (linkId1, linkId2, linkId3) = ("55555555", "66666666", "77777777")

      sqlu"""Insert into ROAD_LINK_ATTRIBUTES (ID, NAME, LINK_ID, VALUE, CREATED_BY) values (55555555, 'PRIVATE_ROAD_ASSOCIATION', $linkId1, $dummyRoadAssociationNameNumberOne, 'test_user')""".execute
      sqlu"""Insert into ROAD_LINK_ATTRIBUTES (ID, NAME, LINK_ID, VALUE, CREATED_BY) values (66666666, 'PRIVATE_ROAD_ASSOCIATION', $linkId2, $dummyRoadAssociationNameNumberOne, 'test_user')""".execute
      sqlu"""Insert into ROAD_LINK_ATTRIBUTES (ID, NAME, LINK_ID, VALUE, CREATED_BY) values (77777777, 'PRIVATE_ROAD_ASSOCIATION', $linkId3, $dummyRoadAssociationNameNumberTwo, 'test_user')""".execute

      val attributesRoad1 = Map("ROADNAMEFIN" -> "Road Number 1", "MUNICIPALITYCODE" -> BigInt(16))
      val attributesRoad2 = Map("ROADNAMEFIN" -> "", "MUNICIPALITYCODE" -> BigInt(766))
      val attributesRoad3 = Map("ROADNAMEFIN" -> "Road Number 3", "MUNICIPALITYCODE" -> BigInt(16))

      val vvhRoadLinks = Seq(
        RoadLinkFetched(linkId1, 16, Seq(Point(386136, 6671029, 15), Point(386133, 6671115, 21)), Municipality, BothDirections, FeatureClass.AllOthers, attributes = attributesRoad1, length = 100),
        RoadLinkFetched(linkId2, 766, Seq(Point(386133, 6671115, 21), Point(386136, 6671029, 15)), Municipality, BothDirections, FeatureClass.AllOthers, attributes = attributesRoad2, length = 200)
      )

      val linkIds = vvhRoadLinks.map(_.linkId)

      val service = new RoadLinkService(mockRoadLinkClient, new DummyEventBus, new DummySerializer)

      when(mockRoadLinkClient.complementaryData).thenReturn(mockVVHComplementaryClient)
      when(mockVVHComplementaryClient.fetchByLinkIdsF(linkIds.toSet)).thenReturn(Future(Seq()))

      when(mockRoadLinkClient.roadLinkData).thenReturn(mockKgvRoadLinkClient)
      when(mockKgvRoadLinkClient.fetchByLinkIdsF(linkIds.toSet)).thenReturn(Future(vvhRoadLinks))

      val result = service.getPrivateRoadsByAssociationName(refactoredDummyRoadAssNameNumberOne, false)

      result.length should be (2)

      result.map(_.roadName).contains(attributesRoad1("ROADNAMEFIN")) should be(true)
      result.map(_.roadName).contains(attributesRoad3("ROADNAMEFIN")) should be(false)
      result.map(_.roadName).contains(noRoadName) should be(true)

      dynamicSession.rollback()
    }
  }

  test("Update roadLink attributes based on geometry changes with old links passing to new links") {
    PostGISDatabase.withDynTransaction {
      val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
      val dummyRoadAssociationNameNumber = "Test Road Association"

      val (linkId1, linkId2, linkId3, linkId4) = ("22222222", "33333333", "44444444", "55555555")
      val (newLinkId1, newLinkId2, newLinkId3) = ("5", "3", "4")

      val changeInfoTest = Seq(
        ChangeInfo(Some(linkId1), Some(newLinkId1), 1, 1, None, None, None, None, 1),
        ChangeInfo(Some(linkId1), Some(newLinkId2), 1, 1, None, None, None, None, 1),

        ChangeInfo(Some(linkId2), Some(newLinkId3), 1, 1, None, None, None, None, 1),
        ChangeInfo(Some(linkId3), Some(newLinkId3), 1, 1, None, None, None, None, 1),
        ChangeInfo(Some(linkId4), Some(newLinkId3), 1, 1, None, None, None, None, 1)
      )

      val testUser = "test_user"
      sqlu"""Insert into ROAD_LINK_ATTRIBUTES (ID, NAME, LINK_ID, VALUE, CREATED_BY) values (2, 'PRIVATE_ROAD_ASSOCIATION', $linkId1, $dummyRoadAssociationNameNumber, $testUser)""".execute
      sqlu"""Insert into ROAD_LINK_ATTRIBUTES (ID, NAME, LINK_ID, VALUE, CREATED_BY) values (33333331, 'PRIVATE_ROAD_ASSOCIATION', $linkId2, $dummyRoadAssociationNameNumber, $testUser)""".execute
      sqlu"""Insert into ROAD_LINK_ATTRIBUTES (ID, NAME, LINK_ID, VALUE, CREATED_BY) values (33333332, 'ADDITIONAL_INFO', $linkId2, '2', $testUser)""".execute
      sqlu"""Insert into ROAD_LINK_ATTRIBUTES (ID, NAME, LINK_ID, VALUE, CREATED_BY) values (44444441, 'PRIVATE_ROAD_ASSOCIATION', $linkId3, $dummyRoadAssociationNameNumber, $testUser)""".execute
      sqlu"""Insert into ROAD_LINK_ATTRIBUTES (ID, NAME, LINK_ID, VALUE, CREATED_BY) values (44444442, 'ADDITIONAL_INFO', $linkId3, '2', $testUser)""".execute
      sqlu"""Insert into ROAD_LINK_ATTRIBUTES (ID, NAME, LINK_ID, VALUE, CREATED_BY) values (55555551, 'PRIVATE_ROAD_ASSOCIATION', $linkId4, $dummyRoadAssociationNameNumber, $testUser)""".execute
      sqlu"""Insert into ROAD_LINK_ATTRIBUTES (ID, NAME, LINK_ID, VALUE, CREATED_BY) values (55555552, 'ADDITIONAL_INFO', $linkId4, '2', $testUser)""".execute


      val service = new RoadLinkService(mockRoadLinkClient, new DummyEventBus, new DummySerializer)
      service.fillRoadLinkAttributes(Seq(), changeInfoTest)

      val attributesRoadLink22222222 = LinkAttributesDao.getExistingValues(linkId1)
      attributesRoadLink22222222.isEmpty should be(false)

      val attributesRoadLink44444444 = LinkAttributesDao.getExistingValues(linkId3)
      attributesRoadLink44444444.isEmpty should be(false)

      val attributesRoadLink4 = LinkAttributesDao.getExistingValues(newLinkId3)
      attributesRoadLink4.size should be (2)
      attributesRoadLink4.get("PRIVATE_ROAD_ASSOCIATION") should be(Some(dummyRoadAssociationNameNumber))
      attributesRoadLink4.get("ADDITIONAL_INFO") should be(Some("2"))

      val attributesRoadLink3 = LinkAttributesDao.getExistingValues(newLinkId2)
      attributesRoadLink3.size should be (1)
      attributesRoadLink3.get("PRIVATE_ROAD_ASSOCIATION") should be(Some(dummyRoadAssociationNameNumber))

      val attributesRoadLink5 = LinkAttributesDao.getExistingValues(newLinkId1)
      attributesRoadLink5.size should be (1)
      attributesRoadLink5.get("PRIVATE_ROAD_ASSOCIATION") should be(Some(dummyRoadAssociationNameNumber))

      dynamicSession.rollback()
    }
  }

  test("Update roadLink attributes based on geometry changes with new roadlink"){
    PostGISDatabase.withDynTransaction {
      val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
      val dummyRoadAssociationNameNumber = "Test Road Association"

      val (linkId1, linkId2, linkId3, linkId4) = ("1", "2", "3", "4")

      val changeInfoTest = Seq(
        ChangeInfo(None, Some(linkId3), 1, 4, None, None, None, None, 1),
        ChangeInfo(None, Some(linkId4), 1, 12, None, None, None, None, 1)
      )

      val roadLinks = Seq(
        RoadLink(linkId1, Seq(Point(111111, 1111111, 10), Point(386136, 6671029, 15)), 100, Municipality, 1, BothDirections, Motorway, None, None),
        RoadLink(linkId2, Seq(Point(386133, 6671115, 21), Point(222222, 2222222, 25)), 100, Municipality, 1, BothDirections, Motorway, None, None),
        RoadLink(linkId3, Seq(Point(386136, 6671029, 15), Point(386133, 6671115, 21)), 100, Municipality, 1, BothDirections, Motorway, None, None),
        RoadLink(linkId4, Seq(Point(386136, 6671029, 15), Point(386133, 6671115, 21)), 100, Municipality, 1, BothDirections, Motorway, None, None)
      )

      val testUser = "test_user"
      sqlu"""Insert into ROAD_LINK_ATTRIBUTES (ID, NAME, LINK_ID, VALUE, CREATED_BY) values (1, 'PRIVATE_ROAD_ASSOCIATION', $linkId1, $dummyRoadAssociationNameNumber, $testUser)""".execute
      sqlu"""Insert into ROAD_LINK_ATTRIBUTES (ID, NAME, LINK_ID, VALUE, CREATED_BY) values (2, 'ADDITIONAL_INFO', $linkId1, '2', $testUser)""".execute
      sqlu"""Insert into ROAD_LINK_ATTRIBUTES (ID, NAME, LINK_ID, VALUE, CREATED_BY) values (3, 'PRIVATE_ROAD_ASSOCIATION', $linkId2, $dummyRoadAssociationNameNumber, $testUser)""".execute


      val service = new RoadLinkService(mockRoadLinkClient, new DummyEventBus, new DummySerializer)
      service.fillRoadLinkAttributes(roadLinks, changeInfoTest)

      val attributesRoadLink3 = LinkAttributesDao.getExistingValues(linkId3)
      attributesRoadLink3.isEmpty should be(true)

      val attributesRoadLink4 = LinkAttributesDao.getExistingValues(linkId4)
      attributesRoadLink4.size should be (1)
      attributesRoadLink4.get("PRIVATE_ROAD_ASSOCIATION") should be(Some(dummyRoadAssociationNameNumber))

      dynamicSession.rollback()
    }
  }

  test("Update roadLink attributes based on geometry changes with old roadlink"){
    PostGISDatabase.withDynTransaction {
      val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
      val dummyRoadAssociationNameNumber = "Test Road Association"

      val (linkId1, linkId2) = ("1", "2")

      val changeInfoTest = Seq(
        ChangeInfo(Some(linkId1), None, 1, 1, None, None, None, None, 1),
        ChangeInfo(Some(linkId2), None, 1, 11, None, None, None, None, 1)
      )

      val testUser = "test_user"
      sqlu"""Insert into ROAD_LINK_ATTRIBUTES (ID, NAME, LINK_ID, VALUE, CREATED_BY) values (1, 'PRIVATE_ROAD_ASSOCIATION', $linkId1, $dummyRoadAssociationNameNumber, $testUser)""".execute
      sqlu"""Insert into ROAD_LINK_ATTRIBUTES (ID, NAME, LINK_ID, VALUE, CREATED_BY) values (2, 'ADDITIONAL_INFO', $linkId2, '2', $testUser)""".execute


      val service = new RoadLinkService(mockRoadLinkClient, new DummyEventBus, new DummySerializer)
      service.fillRoadLinkAttributes(Seq(), changeInfoTest)

      val attributesRoadLink1 = LinkAttributesDao.getExistingValues(linkId1)
      attributesRoadLink1.size should be (1)

      val attributesRoadLink2 = LinkAttributesDao.getExistingValues(linkId2)
      attributesRoadLink2.isEmpty should be(true)

      dynamicSession.rollback()
    }
  }

  test("Override link properties only if different than vvh") {
    val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
    val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
    
    val service = new TestService(mockRoadLinkClient, mockEventBus)

    val (linkId1, linkId2) = ("1", "2")

    val roadLinkAdjucted: List[AdjustedRoadLinksAndRoadLinkFetched] = List(
      AdjustedRoadLinksAndRoadLinkFetched(
        RoadLink(linkId1, List(), 0.0, Municipality,
          functionalClass=UnknownFunctionalClass.value,
          trafficDirection=TrafficDirection.TowardsDigitizing,
          linkType=UnknownLinkType, Some("10.01.2022 14:54:15"), Some("automatic_generation"),
          constructionType = ConstructionType.InUse, linkSource = LinkGeomSource.NormalLinkInterface),
        RoadLinkFetched(linkId = linkId1,
          0, geometry = Seq(),
          Municipality,
          trafficDirection = TrafficDirection.TowardsDigitizing,
          featureClass = FeatureClass.WinterRoads, None, Map(),
          ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)),
      AdjustedRoadLinksAndRoadLinkFetched(
        RoadLink(linkId2, List(), 0.0, Municipality,
          functionalClass=UnknownFunctionalClass.value,
          trafficDirection=TrafficDirection.TowardsDigitizing,
          linkType=UnknownLinkType, 
          Some("10.01.2022 14:54:15"), Some("automatic_generation"),
          constructionType = ConstructionType.InUse, linkSource = LinkGeomSource.NormalLinkInterface),
        RoadLinkFetched(linkId2, 0, List(), Municipality,
          trafficDirection=TowardsDigitizing,featureClass= FeatureClass.TractorRoad,
          None, Map(), ConstructionType.InUse, NormalLinkInterface))
    )
    
    val changeSet: RoadLinkChangeSet = RoadLinkChangeSet(roadLinkAdjucted, Seq(), Seq(), Seq())

    runWithRollback {
      service.updateAutoGeneratedProperties(changeSet.adjustedRoadLinks)
      val linkTypes = RoadLinkDAO.getValues(RoadLinkDAO.LinkType, Seq(linkId1,linkId2))
      val functionalClass = RoadLinkDAO.getValues(RoadLinkDAO.FunctionalClass, Seq(linkId1,linkId2))
      val trafficDirections = RoadLinkDAO.getValues(RoadLinkDAO.TrafficDirection, Seq(linkId1,linkId2))

      trafficDirections.size should be (0)
      functionalClass.size should be (0)
      linkTypes.size should be (0)
    }
  }
  
  test("Mass save adjustedRoadLink") {
    val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
    val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]

    def roadLink(id: String) = {
      RoadLinkFetched(linkId = id,
        municipalityCode = 0, geometry = Seq(),
        administrativeClass = Municipality, trafficDirection = TrafficDirection.UnknownDirection,
        featureClass = FeatureClass.WinterRoads, modifiedAt = None, attributes = Map(),
        constructionType = ConstructionType.InUse, linkSource = LinkGeomSource.NormalLinkInterface)
    }

    val (linkId1, linkId2, linkId3, linkId4) = ("1", "2", "3", "4")

    val service = new TestService(mockRoadLinkClient, mockEventBus)

    val roadLinkAdjucted: List[AdjustedRoadLinksAndRoadLinkFetched] = List(
      AdjustedRoadLinksAndRoadLinkFetched(RoadLink(linkId1, List(), 0.0, Municipality,
        AnotherPrivateRoad.value, TrafficDirection.TowardsDigitizing, SingleCarriageway, Some("10.01.2022 14:54:15"), Some("automatic_generation"),
        constructionType = ConstructionType.InUse, linkSource = LinkGeomSource.NormalLinkInterface), roadLink(linkId1)),
      AdjustedRoadLinksAndRoadLinkFetched(RoadLink(linkId1, List(), 0.0, Municipality,
        UnknownFunctionalClass.value, TrafficDirection.TowardsDigitizing, UnknownLinkType, Some("10.01.2022 14:54:15"), Some("automatic_generation"),
        constructionType = ConstructionType.InUse, linkSource = LinkGeomSource.NormalLinkInterface), roadLink(linkId1))
      , AdjustedRoadLinksAndRoadLinkFetched(RoadLink(linkId1, List(), 0.0, Municipality,
        AnotherPrivateRoad.value, TrafficDirection.BothDirections, SingleCarriageway, Some("10.01.2022 14:54:15"), Some("automatic_generation"),
        constructionType = ConstructionType.InUse, linkSource = LinkGeomSource.NormalLinkInterface), roadLink(linkId1))
      , AdjustedRoadLinksAndRoadLinkFetched(RoadLink(linkId2, List(), 0.0, Municipality,
        AnotherPrivateRoad.value, TrafficDirection.BothDirections, UnknownLinkType, Some("10.01.2022 14:54:15"), Some("automatic_generation"),
        constructionType = ConstructionType.InUse, linkSource = LinkGeomSource.NormalLinkInterface), roadLink(linkId2))
      , AdjustedRoadLinksAndRoadLinkFetched(RoadLink(linkId2, List(), 0.0, Municipality,
        PrimitiveRoad.value, TrafficDirection.TowardsDigitizing, SingleCarriageway, Some("10.01.2022 14:54:15"), Some("automatic_generation"),
        constructionType = ConstructionType.InUse, linkSource = LinkGeomSource.NormalLinkInterface), roadLink(linkId2))
      , AdjustedRoadLinksAndRoadLinkFetched(RoadLink(linkId2, List(), 0.0, Municipality,
        AnotherPrivateRoad.value, TrafficDirection.BothDirections, UnknownLinkType, Some("10.01.2022 14:54:15"), Some("automatic_generation"),
        constructionType = ConstructionType.InUse, linkSource = LinkGeomSource.NormalLinkInterface), roadLink(linkId2))
      , AdjustedRoadLinksAndRoadLinkFetched(RoadLink(linkId3, List(), 0.0, Municipality,
        PrimitiveRoad.value, TrafficDirection.TowardsDigitizing, SingleCarriageway, Some("10.01.2022 14:54:15"), Some("automatic_generation"),
        constructionType = ConstructionType.InUse, linkSource = LinkGeomSource.NormalLinkInterface), roadLink(linkId3))
      , AdjustedRoadLinksAndRoadLinkFetched(RoadLink(linkId3, List(), 0.0, Municipality,
        AnotherPrivateRoad.value, TrafficDirection.TowardsDigitizing, SingleCarriageway, Some("10.01.2022 14:54:15"), Some("automatic_generation"),
        constructionType = ConstructionType.InUse, linkSource = LinkGeomSource.NormalLinkInterface), roadLink(linkId3))
      , AdjustedRoadLinksAndRoadLinkFetched(RoadLink(linkId3, List(), 0.0, Municipality,
        PrimitiveRoad.value, TrafficDirection.BothDirections, SingleCarriageway, Some("10.01.2022 14:54:15"), Some("automatic_generation"),
        constructionType = ConstructionType.InUse, linkSource = LinkGeomSource.NormalLinkInterface), roadLink(linkId3))
      , AdjustedRoadLinksAndRoadLinkFetched(RoadLink(linkId4, List(), 0.0, Municipality,
        AnotherPrivateRoad.value, TrafficDirection.TowardsDigitizing, UnknownLinkType, Some("10.01.2022 14:54:15"), Some("automatic_generation"),
        constructionType = ConstructionType.InUse, linkSource = LinkGeomSource.NormalLinkInterface), roadLink(linkId4)))
    val changeSet: RoadLinkChangeSet = RoadLinkChangeSet(roadLinkAdjucted, Seq(), Seq(), Seq())

    runWithRollback {
      service.updateAutoGeneratedProperties(changeSet.adjustedRoadLinks)
      val linkTypes = RoadLinkDAO.getValues(RoadLinkDAO.LinkType, Seq(linkId1, linkId2, linkId3, linkId4))
      val functionalClass = RoadLinkDAO.getValues(RoadLinkDAO.FunctionalClass, Seq(linkId1, linkId2, linkId3, linkId4))
      val trafficDirections = RoadLinkDAO.getValues(RoadLinkDAO.TrafficDirection, Seq(linkId1, linkId2, linkId3, linkId4))

      trafficDirections.size should be (4)
      functionalClass.size should be (4)
      linkTypes.size should be (3)
    }
  }
}