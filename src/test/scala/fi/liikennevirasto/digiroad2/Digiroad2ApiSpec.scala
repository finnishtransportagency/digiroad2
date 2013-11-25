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
      busStops.head should equal (BusStop("10",6676322.16437243,373496.295384342,Some(60.2040416199226),Some(24.7176288460131),"2",Map("shelter_type" -> "1")))
    }
  }

  test("ping") {
    get("/ping") {
      status should equal (200)
      body should equal ("pong")
    }
  }

  test("get map configuration") {
    get("/config") {
      status should equal(200)
      val responseJson = parse(body)
      (responseJson \ "mapfull" \ "state" \ "zoom").values should equal(5)
      (responseJson \ "mapfull" \ "state" \ "east").values should equal("123456")
      (responseJson \ "mapfull" \ "state" \ "north").values should equal("6677676")
    }
  }
}