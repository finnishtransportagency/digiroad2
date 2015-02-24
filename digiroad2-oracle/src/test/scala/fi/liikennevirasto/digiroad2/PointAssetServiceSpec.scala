package fi.liikennevirasto.digiroad2

import org.scalatest.{Matchers, FunSuite}

class PointAssetServiceSpec extends FunSuite with Matchers {
  test("Get traffic light point data") {
    val data = PointAssetService.getByMunicipality(9, 235)
    val element = data(0)

    val id = element.get("id").get.asInstanceOf[Long]
    id should equal(9815555)
    val point = element.get("point").get.asInstanceOf[Point]
    point.x should equal(374699.3691)
    point.y should equal(6677284.2874)
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
