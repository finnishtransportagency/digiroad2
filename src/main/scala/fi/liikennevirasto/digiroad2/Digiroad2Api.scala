package fi.liikennevirasto.digiroad2

import org.scalatra._
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.json._
import fi.liikennevirasto.digiroad2.feature.BusStop

class Digiroad2Api extends ScalatraServlet with JacksonJsonSupport {
  protected implicit val jsonFormats: Formats = DefaultFormats

  before() {
      contentType = formats("json")
    }

  get("/busstops") {
    // TODO: integrate to database when available, this is only to test communication between ui and server
    List(
      BusStop("1", 385637, 6675353),
      BusStop("2", 385537, 6676353),
      BusStop("3", 385737, 6677353),
      BusStop("4", 385137, 6678353)
    )
  }

  get("/ping") {
    "pong"
  }
}

