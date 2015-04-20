package fi.liikennevirasto.digiroad2

import org.scalatest.{Matchers, FunSuite}

class PointAssetServiceSpec extends FunSuite with Matchers {
  test("Get traffic light point data") {
    val data = PointAssetService.getByMunicipality(9, 235)
    val element = data(0)

    val id = element.get("id").get.asInstanceOf[Long]
    id should equal(24189944)
    val point = element.get("point").get.asInstanceOf[Point]
    point.x should equal(374673.0256)
    point.y should equal(6676887.3011)
  }

  test("Get Service point data") {
    val element = PointAssetService.getServicePoints()(0)

    val id = element.get("id").get.asInstanceOf[Long]
    id should equal(1283)

    val point = element.get("point").get.asInstanceOf[Point]
    point.x should equal(446693.6681)
    point.y should equal(6755143.4397)
  }
}
