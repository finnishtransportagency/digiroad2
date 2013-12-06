package fi.liikennevirasto.digiroad2

import org.scalatra._
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.json._
import fi.liikennevirasto.digiroad2.Digiroad2Context._
import org.json4s.JsonAST._
import org.json4s.JsonDSL._
import fi.liikennevirasto.digiroad2.feature.BusStop
import org.json4s.JsonAST.JString
import org.json4s.JsonAST.JInt

class Digiroad2Api extends ScalatraServlet with JacksonJsonSupport with CorsSupport {
  val MunicipalityNumber = "municipalityNumber"

  protected implicit val jsonFormats: Formats = DefaultFormats

  before() {
    contentType = formats("json")
  }

  get("/config") {
    // todo read user specific properties from db
    val config = readJsonFromStream(getClass.getResourceAsStream("/map_base_config.json"))
    val userConfig = userProvider.getUserConfiguration()
    config.replace("mapfull" :: "state" :: Nil, (config \ "mapfull" \ "state").transformField {
      case ("zoom", JInt(x)) => ("zoom", JInt(BigInt(userConfig.get("zoom").getOrElse(x.toString))))
      case ("east", JString(x)) => ("east", JString(userConfig.get("east").getOrElse(x)))
      case ("north", JString(x)) => ("north", JString(userConfig.get("north").getOrElse(x)))
    })
  }

  get("/busstops") {
    response.setHeader("Access-Control-Allow-Headers", "*");
    featureProvider.getBusStops(params.get(MunicipalityNumber).flatMap(x => Some(x.toInt)))
  }

  get("/assets/") {
    featureProvider.getAssets(10);
  }

  put("/busstops/:id") {
    // TODO: update optional/required fields in bus stop
    val (lon, lat, roadLinkId) = ((parsedBody \ "lon").extractOpt[Double], (parsedBody \ "lat").extractOpt[Double], (parsedBody \ "roadLinkId").extractOpt[Long])
    val bs = BusStop(params("id").toLong, lon = lon.get, lat = lat.get, roadLinkId = roadLinkId.get, busStopType = "")
    val ubs = featureProvider.updateBusStop(bs)
    println("UPDATED: " + ubs)
    ubs
  }

  get("/roadlinks") {
    response.setHeader("Access-Control-Allow-Headers", "*");
    val rls = featureProvider.getRoadLinks(params.get(MunicipalityNumber).flatMap(x => Some(x.toInt)))
    ("type" -> "FeatureCollection") ~
      ("features" ->  rls.map { rl =>
        ("type" -> "Feature") ~ ("properties" -> ("roadLinkId" -> rl.id)) ~ ("geometry" ->
          ("type" -> "LineString") ~ ("coordinates" -> rl.lonLat.map { ll =>
            List(ll._1, ll._2)
          }) ~ ("crs" -> ("type" -> "OGC") ~ ("properties" -> ("urn" -> "urn:ogc:def:crs:OGC:1.3:ETRS89")))
        )
      })
  }

  get("/ping") {
    "pong"
  }

  error {
    // TODO: error logging / handling
    case e => e.printStackTrace()
  }
}
