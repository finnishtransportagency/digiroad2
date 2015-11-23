package fi.liikennevirasto.digiroad2

import org.scalatest.{Matchers, FunSuite}

class PointAssetServiceSpec extends FunSuite with Matchers {
  test("Get traffic light point data") {
    val data = ReadOnlyPointAssetService.getByMunicipality(9, 235)
    val trafficLight = data.find { light =>
      light.get("id").get.asInstanceOf[Long] == 24189944
    }
    val point = trafficLight.map { light => light.get("point").get.asInstanceOf[Point] }
    point should be(Some(Point(374673.0256, 6676887.3011)))
    data.length should be(23)
  }

  test("Get Service point data") {
    val element = ReadOnlyPointAssetService.getServicePoints()(0)

    val id = element.get("id").get.asInstanceOf[Long]
    id should equal(1283)

    val point = element.get("point").get.asInstanceOf[Point]
    point.x should equal(446693.6681)
    point.y should equal(6755143.4397)
  }
}
