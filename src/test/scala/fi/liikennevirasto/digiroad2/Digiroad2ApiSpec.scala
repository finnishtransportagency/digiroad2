package fi.liikennevirasto.digiroad2

import org.scalatra.test.scalatest._
import org.scalatest.{Tag, FunSuite}
import org.json4s._
import org.json4s.jackson.JsonMethods._
import fi.liikennevirasto.digiroad2.feature.BusStop

class Digiroad2ApiSpec extends ScalatraSuite with FunSuite {
  protected implicit val jsonFormats: Formats = DefaultFormats

  addServlet(classOf[Digiroad2Api], "/*")

  test("get bus stops", Tag("db")) {
    get("/busstops") {
      status should equal (200)
      val busStops = parse(body).extract[List[BusStop]]
      busStops.size should be (99)
      busStops.head should equal (BusStop("94265", 327130.0 ,6991394.0, Map("address" -> "Lapuantie", "roadNumber" -> "16", "bearing" -> "267")))
    }
  }

  test("ping") {
    get("/ping") {
      status should equal (200)
      body should equal ("pong")
    }
  }
}