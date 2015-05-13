package fi.liikennevirasto.digiroad2

import org.scalatest.mock.MockitoSugar
import org.scalatest.{BeforeAndAfter, FunSuite, Matchers}
import org.mockito.Mockito._

import fi.liikennevirasto.digiroad2.asset._
import com.jolbox.bonecp.{BoneCPConfig, BoneCPDataSource}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import scala.slick.driver.JdbcDriver.backend.Database
import scala.slick.driver.JdbcDriver.backend.Database.dynamicSession
import scala.slick.jdbc.StaticQuery.interpolation
import scala.slick.jdbc.{GetResult, PositionedResult, StaticQuery => Q}

class RoadLinkServiceSpec extends FunSuite with Matchers with BeforeAndAfter {
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
    roadLinks.find { case(id, _, _, _, _, _, _, _, _, _) => id == 7886262 }.map(_._7) should be (Some(TowardsDigitizing))
    roadLinks.find { case(_, mmlId, _, _, _, _, _, _, _, _) => mmlId == 391203482 }.map(_._7) should be (Some(AgainstDigitizing))
  }

  test("Override road link functional class with adjusted value") {
    val boundingBox = BoundingRectangle(Point(373816, 6676812), Point(374634, 6677671))
    val roadLinks = RoadLinkService.getRoadLinks(boundingBox)
    roadLinks.find { case (id, _, _, _, _, _, _, _, _, _) => id == 7886262}.map(_._6) should be(Some(5))
    roadLinks.find { case (_, mmlId, _, _, _, _, _, _, _, _) => mmlId == 391203482}.map(_._6) should be(Some(4))
  }

  test("Overriden road link adjustments return latest modification") {
    val roadLink = RoadLinkService.getRoadLink(7886262)
    val (_, _, _, _, _, _, _, modifiedAt, modifiedBy, _) = roadLink
    modifiedAt should be (Some("12.12.2014 00:00:00"))
    modifiedBy should be (Some("test"))
  }

  test("Adjust link type") {
    class TestService(vvhClient: VVHClient) extends VVHRoadLinkService(vvhClient) {
      override def withDynTransaction[T](f: => T): T = f
    }
    Database.forDataSource(OracleDatabase.ds).withDynTransaction {
      val mockVVHClient = MockitoSugar.mock[VVHClient]
      when(mockVVHClient.fetchVVHRoadlink(1l))
        .thenReturn(Some((91, Nil, Municipality, UnknownDirection)))
      val service = new TestService(mockVVHClient)
      val roadLink = service.updateProperties(1, 5, PedestrianZone, BothDirections, "testuser", { _ => })
      roadLink.map(_.linkType) should be(Some(PedestrianZone))
      dynamicSession.rollback()
    }
  }

  test("Adjust link traffic direction to value that is in VVH") {
    class TestService(vvhClient: VVHClient) extends VVHRoadLinkService(vvhClient) {
      override def withDynTransaction[T](f: => T): T = f
    }
    Database.forDataSource(OracleDatabase.ds).withDynTransaction {
      val mockVVHClient = MockitoSugar.mock[VVHClient]
      when(mockVVHClient.fetchVVHRoadlink(1l))
        .thenReturn(Some((91, Nil, Municipality, TowardsDigitizing)))
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
    val service = new VVHRoadLinkService(mockVVHClient)
    val roadLink = service.updateProperties(1, 5, PedestrianZone, BothDirections, "testuser", { _ => })
    roadLink.map(_.linkType) should be(None)
  }

  test("Validate access rights to municipality") {
    val mockVVHClient = MockitoSugar.mock[VVHClient]
    when(mockVVHClient.fetchVVHRoadlink(1l))
      .thenReturn(Some((91, Nil, Municipality, UnknownDirection)))
    val service = new VVHRoadLinkService(mockVVHClient)
    var validatedCode = 0
    service.updateProperties(1, 5, PedestrianZone, BothDirections, "testuser", { municipalityCode =>
      validatedCode = municipalityCode
    })
    validatedCode should be(91)
  }
}
