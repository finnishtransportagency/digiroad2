package fi.liikennevirasto.digiroad2

import org.scalatra._
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.json._
import fi.liikennevirasto.digiroad2.Digiroad2Context._
import fi.liikennevirasto.digiroad2.feature.BusStop

class Digiroad2Api extends ScalatraServlet with JacksonJsonSupport with CorsSupport {
  protected implicit val jsonFormats: Formats = DefaultFormats

  before() {
      contentType = formats("json")
  }

  get("/busstops") {
    response.setHeader("Access-Control-Allow-Headers", "*");
    featureProvider.getBusStops()
  }

  get("/config") {
    // todo read user specific properties from db
    try {
      getClass.getResourceAsStream("/full_config.json")
    } catch {
      case e: Exception => throw new RuntimeException("Can't load full_config.json for env: " + System.getProperty("env"), e)
    }
  }

  get("/ping") {
    "pong"
  }
}

