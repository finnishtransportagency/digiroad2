package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.{ValidityPeriod, BoundingRectangle}
import org.scalatest.{BeforeAndAfter, FunSuite, Matchers}

import scala.slick.jdbc.{StaticQuery => Q}

class MassTransitStopServiceSpec extends FunSuite with Matchers {
  test("Calculate mass transit stop validity periods") {
    val massTransitStops = MassTransitStopService.getByBoundingBox(BoundingRectangle(Point(374000,6677000), Point(374800,6677600)))
    massTransitStops.find(_.id == 300000).map(_.validityPeriod) should be(Some(ValidityPeriod.Current))
    massTransitStops.find(_.id == 300001).map(_.validityPeriod) should be(Some(ValidityPeriod.Past))
    massTransitStops.find(_.id == 300003).map(_.validityPeriod) should be(Some(ValidityPeriod.Future))
  }

  test("Get stops by bounding box") {
    val stops = MassTransitStopService.getByBoundingBox(BoundingRectangle(Point(374443, 6677245), Point(374444, 6677246)))
    stops.size shouldBe 1
  }
}
