package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.{AgainstDigitizing, TowardsDigitizing, BoundingRectangle}
import org.scalatest.{FunSuite, Matchers}

class RoadLinkServiceSpec extends FunSuite with Matchers {
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
    roadLinks.find { case(id, _, _, _, _, _, _, _, _) => id == 7886262 }.map(_._7) should be (Some(TowardsDigitizing))
    roadLinks.find { case(_, mmlId, _, _, _, _, _, _, _) => mmlId == 391203482 }.map(_._7) should be (Some(AgainstDigitizing))
  }

  test("Override road link functional class with adjusted value") {
    val boundingBox = BoundingRectangle(Point(373816, 6676812), Point(374634, 6677671))
    val roadLinks = RoadLinkService.getRoadLinks(boundingBox)
    roadLinks.find { case (id, _, _, _, _, _, _, _, _) => id == 7886262}.map(_._6) should be(Some(5))
    roadLinks.find { case (_, mmlId, _, _, _, _, _, _, _) => mmlId == 391203482}.map(_._6) should be(Some(4))
  }

  test("Overriden road link adjustments return latest modification") {
    val roadLink = RoadLinkService.getRoadLink(7886262)
    val (_, _, _, _, _, _, _, modifiedAt, modifiedBy) = roadLink
    modifiedAt should be (Some("12.12.2014 00:00:00"))
    modifiedBy should be (Some("test"))
  }
}