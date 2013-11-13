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
      busStops.size should be (41)
      busStops.head should equal (BusStop("1", 6677241.45081766, 374085.572947231, "2", Map("shelter_type" -> "2")))
    }
  }

  test("ping") {
    get("/ping") {
      status should equal (200)
      body should equal ("pong")
    }
  }
}