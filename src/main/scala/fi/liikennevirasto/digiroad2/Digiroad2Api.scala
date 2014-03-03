package fi.liikennevirasto.digiroad2

import org.scalatra._
import org.json4s._
import org.scalatra.json._
import fi.liikennevirasto.digiroad2.Digiroad2Context._
import org.json4s.JsonDSL._
import fi.liikennevirasto.digiroad2.asset._
import org.joda.time.{LocalDate, DateTime}
import fi.liikennevirasto.digiroad2.authentication.{RequestHeaderAuthentication, UnauthenticatedException}
import org.slf4j.LoggerFactory
import fi.liikennevirasto.digiroad2.asset.BoundingCircle
import scala.Some
import fi.liikennevirasto.digiroad2.asset.PropertyValue

class Digiroad2Api extends ScalatraServlet with JacksonJsonSupport with CorsSupport with RequestHeaderAuthentication with GZipSupport {
  val logger = LoggerFactory.getLogger(getClass)
  val MunicipalityNumber = "municipalityNumber"
  val Never = new DateTime().plusYears(1).toString("EEE, dd MMM yyyy HH:mm:ss zzzz")
  protected implicit val jsonFormats: Formats = DefaultFormats

  before() {
    contentType = formats("json")
    try {
      authenticateForApi(request)(userProvider)
    } catch {
      case ise: IllegalStateException => halt(Unauthorized("Authentication error: " + ise.getMessage))
    }
    response.setHeader(Digiroad2ServerOriginatedResponseHeader, "true")
  }

  get("/config") {
    readJsonFromBody(MapConfigJson.mapConfig(userProvider.getCurrentUser.configuration))
  }

  post("/layers") {
    if (params("action_route") == "GetSupportedLocales") {
      "[\"fi_FI\"]"
    }
  }

  get("/assetTypes") {
    assetProvider.getAssetTypes
  }

  get("/assets") {
    val user = userProvider.getCurrentUser
    val (validFrom: Option[LocalDate], validTo: Option[LocalDate]) = params.get("validityPeriod") match {
      case Some("past") => (None, Some(LocalDate.now))
      case Some("future") => (Some(LocalDate.now), None)
      case Some("current") => (Some(LocalDate.now), Some(LocalDate.now))
      case _ => (None, None)
    }
    val authorizedMunicipalities = user.configuration.authorizedMunicipalities
    assetProvider.getAssets(
        params("assetTypeId").toLong, authorizedMunicipalities.toList,
        boundsFromParams, validFrom = validFrom, validTo = validTo)
  }

  private def isReadOnly(authorizedMunicipalities: Set[Int])(municipalityNumber: Int): Boolean = {
    !authorizedMunicipalities.contains(municipalityNumber)
  }

  get("/assets/:assetId") {
    assetProvider.getAssetById(params("assetId").toLong) match {
      case Some(a) => {
        val authorizedMunicipalities = userProvider.getCurrentUser.configuration.authorizedMunicipalities.toSet
        if (a.municipalityNumber.map(isReadOnly(authorizedMunicipalities)).getOrElse(true)) {
          Unauthorized("Asset " + params("assetId") + " not authorized")
        } else {
          a
        }
      }
      case None => NotFound("Asset " + params("assetId") + " not found")
    }
  }

  get("/enumeratedPropertyValues/:assetTypeId") {
    assetProvider.getEnumeratedPropertyValues(params("assetTypeId").toLong)
  }

  // TODO: handle missing roadLinkId
  put("/assets/:id") {
    val (assetTypeId, lon, lat, roadLinkId, bearing) = ((parsedBody \ "assetTypeId").extractOpt[Long], (parsedBody \ "lon").extractOpt[Double], (parsedBody \ "lat").extractOpt[Double],
      (parsedBody \ "roadLinkId").extractOpt[Long], (parsedBody \ "bearing").extractOpt[Int])
    val asset = Asset(
        id = params("id").toLong,
        assetTypeId = assetTypeId.get,
        lon = lon.get,
        lat = lat.get,
        roadLinkId = roadLinkId.get,
        imageIds = List(),
        bearing = bearing)
    val updated = assetProvider.updateAssetLocation(asset)
    logger.debug("Asset updated: " + updated)
    updated
  }

  post("/asset") {
    val user = userProvider.getCurrentUser()
    assetProvider.createAsset(
      (parsedBody \ "assetTypeId").extract[Long],
      (parsedBody \ "lon").extract[Int].toDouble,
      (parsedBody \ "lat").extract[Int].toDouble,
      (parsedBody \ "roadLinkId").extract[Long],
      (parsedBody \ "bearing").extract[Int],
      user.username,
      (parsedBody \ "properties").extract[Seq[SimpleProperty]])
  }

  get("/roadlinks") {
    // TODO: check bounds are within range to avoid oversized queries
    val user = userProvider.getCurrentUser()
    response.setHeader("Access-Control-Allow-Headers", "*");
    val rls = assetProvider.getRoadLinks(user.configuration.authorizedMunicipalities.toList, boundsFromParams)
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
    val assetId = params("assetId").toLong
    assetProvider.updateAssetProperty(assetId, params("propertyId"), propertyValues)
    assetProvider.getAssetById(assetId)
  }

  delete("/assets/:assetId/properties/:propertyId/values") {
    val assetId = params("assetId").toLong
    assetProvider.deleteAssetProperty(assetId, params("propertyId"))
    assetProvider.getAssetById(assetId)
  }

  get("/images/:imageId") {
    val id = params("imageId").split("_").head // last modified date is appended with an underscore to image id in order to cache image when it has not been altered
    val bytes = assetProvider.getImage(id.toLong)
    response.setHeader("Expires", Never)
    response.setContentType("application/octet-stream")
    bytes
  }

  get("/assetTypeProperties/:assetTypeId") {
    try {
      val assetTypeId = params("assetTypeId").toLong
      assetProvider.availableProperties(assetTypeId)
    } catch {
      case e: Exception => BadRequest("Invalid asset type id: " + params("assetTypeId"))
    }
  }

  error {
    case ise: IllegalStateException => halt(InternalServerError("Illegal state: " + ise.getMessage))
    case ue: UnauthenticatedException => halt(Unauthorized("Not authenticated"))
    case e: Exception => {
      logger.error("API Error", e)
      halt(InternalServerError("API error"))
    }
  }

  private[this] def boundsFromParams: Option[BoundingCircle] = {
    params.get("bbox").map { b =>
      val BBOXList = b.split(",").map(_.toDouble);
      val (left, bottom, right, top) = (BBOXList(0), BBOXList(1), BBOXList(2), BBOXList(3));
      val r =  math.hypot(bottom - top, left - right)/2
      val lon = (bottom + top)/2
      val lat = (left + right)/2
      BoundingCircle(lat, lon, r)
    }
  }
}
