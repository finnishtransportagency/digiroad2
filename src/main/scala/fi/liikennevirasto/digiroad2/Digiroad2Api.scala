package fi.liikennevirasto.digiroad2

import org.scalatra._
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.json._
import fi.liikennevirasto.digiroad2.Digiroad2Context._
import fi.liikennevirasto.digiroad2.feature.BusStop
import org.json4s.JsonAST.{JInt, JString, JField}

class Digiroad2Api extends ScalatraServlet with JacksonJsonSupport with CorsSupport {
  protected implicit val jsonFormats: Formats = DefaultFormats

  before() {
      contentType = formats("json")
  }

  get("/config") {
    // todo read user specific properties from db
    try {
      val config = readJsonFromStream(getClass.getResourceAsStream("/full_config.json"))
      config.replace("mapfull" :: "state" :: Nil, (config \ "mapfull" \ "state").transformField {
        case ("zoom", JInt(x)) => ("zoom", JInt(8))
        case ("east", JString(x)) => ("east", JString("373868"))
        case ("north", JString(x)) => ("north", JString("6677676"))
      })
    } catch {
      case e: Exception => throw new RuntimeException("Can't load full_config.json", e)
    }
  }

  case class State(zoom: Integer, east: Integer, north: Integer)

  get("/busstops") {
    response.setHeader("Access-Control-Allow-Headers", "*");
    featureProvider.getBusStops()
  }

  get("/ping") {
    "pong"
  }
}
