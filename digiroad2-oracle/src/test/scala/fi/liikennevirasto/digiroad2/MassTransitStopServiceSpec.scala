package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.{ValidityPeriod, BoundingRectangle}
import org.scalatest.{BeforeAndAfter, FunSuite, Matchers}

import scala.slick.jdbc.{StaticQuery => Q}

class MassTransitStopServiceSpec extends FunSuite with Matchers {
  test("Calculate mass transit stop validity periods") {
    val massTransitStops = MassTransitStopService.getByBoundingBox
    massTransitStops.find(_.id == 300000).map(_.validityPeriod) should be(Some(ValidityPeriod.Current))
    massTransitStops.find(_.id == 300001).map(_.validityPeriod) should be(Some(ValidityPeriod.Past))
    massTransitStops.find(_.id == 300003).map(_.validityPeriod) should be(Some(ValidityPeriod.Future))
  }
}
