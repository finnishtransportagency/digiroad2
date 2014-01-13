package fi.liikennevirasto.digiroad2

import org.scalatra._
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.json._
import fi.liikennevirasto.digiroad2.Digiroad2Context._
import org.json4s.JsonDSL._
import fi.liikennevirasto.digiroad2.asset.{Asset, PropertyValue}
import org.joda.time.DateTime
import fi.liikennevirasto.digiroad2.authentication.{UnauthenticatedException, AuthenticationSupport}
import org.slf4j.LoggerFactory

class Digiroad2Api extends ScalatraServlet with JacksonJsonSupport with CorsSupport with AuthenticationSupport {
  val logger = LoggerFactory.getLogger(getClass)
  val MunicipalityNumber = "municipalityNumber"
  val Never = new DateTime().plusYears(1).toString("EEE, dd MMM yyyy HH:mm:ss zzzz")

  before() {
    contentType = formats("json")
    userOption match {
      case Some(user) => Digiroad2Context.userProvider.setThreadLocalUser(user)
      case None => throw new UnauthenticatedException()
    }
  }

  get("/config") {
    userProvider.getThreadLocalUser() match {
      case Some(user) => readJsonFromBody(MapConfigJson.mapConfig(user.configuration))
      case _ => throw new UnauthenticatedException()
    }
  }

  get("/assetTypes") {
    featureProvider.getAssetTypes
  }

  get("/assets") {
    featureProvider.getAssets(params("assetTypeId").toLong, params.get(MunicipalityNumber).flatMap(x => Some(x.toLong)))
  }

  get("/assets/:assetId") {
    featureProvider.getAssetById(params("assetId").toLong) match {
      case Some(a) => a
      case None => NotFound("Asset " + params("assetId") + " not found")
    }
  }

  get("/enumeratedPropertyValues/:assetTypeId") {
    featureProvider.getEnumeratedPropertyValues(params("assetTypeId").toLong)
  }

  // TODO: handle missing roadLinkId
  put("/assets/:id") {
    val (assetTypeId, lon, lat, roadLinkId, bearing) = ((parsedBody \ "assetTypeId").extractOpt[Long], (parsedBody \ "lon").extractOpt[Double], (parsedBody \ "lat").extractOpt[Double],
      (parsedBody \ "roadLinkId").extractOpt[Long], (parsedBody \ "bearing").extractOpt[Int])
    val asset = Asset(params("id").toLong, assetTypeId = assetTypeId.get, lon = lon.get, lat = lat.get, roadLinkId = roadLinkId.get, propertyData = List(), bearing = bearing)
    val updated = featureProvider.updateAssetLocation(asset)
    logger.debug("Asset updated: " + updated)
    updated
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

  put("/assets/:assetId/properties/:propertyId/values") {
    val propertyValues = parsedBody.extract[List[PropertyValue]]
    featureProvider.updateAssetProperty(params("assetId").toLong, params("propertyId"), propertyValues)
  }

  delete("/assets/:assetId/properties/:propertyId/values") {
    featureProvider.deleteAssetProperty(params("assetId").toLong, params("propertyId"))
  }

  get("/images/:imageId") {
    val id = params("imageId").split("_").head // last modified date is appended with an underscore to image id in order to cache image when it has not been altered
    val bytes = featureProvider.getImage(id.toLong)
    response.setHeader("Expires", Never)
    response.setContentType("application/octet-stream")
    bytes
  }

  error {
    case ue: UnauthenticatedException => Unauthorized("Not authenticated")
    case e: Exception => logger.error("API Error", e)
  }
}
