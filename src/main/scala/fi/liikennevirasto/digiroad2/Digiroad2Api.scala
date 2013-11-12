package fi.liikennevirasto.digiroad2

import org.scalatra._
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.json._
import fi.liikennevirasto.digiroad2.Digiroad2Context._
import fi.liikennevirasto.digiroad2.feature.BusStop

class Digiroad2Api extends ScalatraServlet with JacksonJsonSupport {
  protected implicit val jsonFormats: Formats = DefaultFormats

  before() {
      contentType = formats("json")
    }

  get("/busstops") {
    featureProvider.getBusStops()
  }

  get("/ping") {
    "pong"
  }
}

