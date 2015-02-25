package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import org.scalatest.{FunSuite, Matchers}

class ManoeuvreServiceSpec extends FunSuite with Matchers {
  test("Get all manoeuvres partially or completely in bounding box") {
    val bounds = BoundingRectangle(Point(373880.25, 6677085), Point(374133, 6677382))
    val manoeuvres = ManoeuvreService.getByBoundingBox(bounds, Set(235))
    manoeuvres.length should equal(5)
    val m = manoeuvres.find(_.id == 39561).get
    m.sourceMmlId should equal(388562342)
    m.destMmlId should equal(388569406)
  }
}
