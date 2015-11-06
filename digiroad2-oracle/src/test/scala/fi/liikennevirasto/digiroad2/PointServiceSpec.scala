package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import org.scalatest.{Matchers, FunSuite}

class PointServiceSpec extends FunSuite with Matchers {
  object Service extends PointAssetService

  test("Can fetch by bounding box") {
    Service.getByBoundingBox(BoundingRectangle(Point(0.0, 0.0), Point(1.0, 1.0)))
  }
}
