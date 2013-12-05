package fi.liikennevirasto.digiroad2

import org.scalatra.test.scalatest._
import org.scalatest.{Tag, FunSuite}
import org.json4s._
import org.json4s.jackson.JsonMethods._
import fi.liikennevirasto.digiroad2.feature.BusStop
import org.json4s.JsonDSL._

class Digiroad2ApiSpec extends ScalatraSuite with FunSuite {
  protected implicit val jsonFormats: Formats = DefaultFormats

  addServlet(classOf[Digiroad2Api], "/*")

  test("get bus stops", Tag("db")) {
    get("/busstops?municipalityNumber=235") {
      status should equal (200)
      val busStops = parse(body).extract[List[BusStop]]
      busStops.size should be (41)
      busStops.head should equal (BusStop(1,374314.731173214,6676916.54122174,"2",Map("shelter_type" -> "2"),3315))
    }
  }

  test("missing bus stops", Tag("db")) {
    get("/busstops?municipalityNumber=234") {
      status should equal (200)
      parse(body).extract[List[BusStop]].size should be (0)
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
      (responseJson \ "mapfull" \ "state" \ "zoom").values should equal(8)
      (responseJson \ "mapfull" \ "state" \ "east").values should equal("373560")
      (responseJson \ "mapfull" \ "state" \ "north").values should equal("6677676")
    }
  }

  test("get road links", Tag("db")) {
    get("/roadlinks?municipalityNumber=235") {
      status should equal(200)
      val roadLinksJson = parse(body)
      (roadLinksJson \ "features" \ "geometry").children.size should (be > 500)
      val cs = (roadLinksJson \ "features" \ "geometry" \ "coordinates" \\ classOf[JDouble])
      cs.take(2) should equal (List(373426.924263711, 6677127.53394753))
    }
  }
}