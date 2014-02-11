package fi.liikennevirasto.digiroad2

import org.scalatra._
import org.json4s._
import org.scalatra.json._
import fi.liikennevirasto.digiroad2.Digiroad2Context._
import org.json4s.JsonDSL._
import fi.liikennevirasto.digiroad2.asset.{BoundingCircle, Asset, PropertyValue}
import org.joda.time.{LocalDate, DateTime}
import fi.liikennevirasto.digiroad2.authentication.{RequestHeaderAuthentication, UnauthenticatedException}
import org.slf4j.LoggerFactory

class Digiroad2Api extends ScalatraServlet with JacksonJsonSupport with CorsSupport with RequestHeaderAuthentication {
  val logger = LoggerFactory.getLogger(getClass)
  val MunicipalityNumber = "municipalityNumber"
  val Never = new DateTime().plusYears(1).toString("EEE, dd MMM yyyy HH:mm:ss zzzz")
  protected implicit val jsonFormats: Formats = DefaultFormats

  before() {
    contentType = formats("json")
    authenticateForApi(request)(userProvider)
  }

  get("/config") {
    userProvider.getThreadLocalUser() match {
      case Some(user) => readJsonFromBody(MapConfigJson.mapConfig(user.configuration))
      case _ => throw new UnauthenticatedException()
    }
  }

  get("/layers") {
    userProvider.getThreadLocalUser() match {
      case Some(user) => readJsonFromBody(LayersJson.layers(user.configuration))
      case _ => throw new UnauthenticatedException()
    }
  }

  get("/assetTypes") {
    assetProvider.getAssetTypes
  }

  get("/assets") {
    userProvider.getThreadLocalUser() match {
      case Some(user) => {
        val (startDate: Option[LocalDate], endDate: Option[LocalDate]) = (params.get("validityPeriod"), params.get("validityDate").map(LocalDate.parse)) match {
          case (Some(period), _) => period match {
            case "past" => (None, Some(LocalDate.now))
            case "future" => (Some(LocalDate.now), None)
          }
          case (_, Some(day)) => {
            (Some(day), Some(day))
          }
          case _ => (None, None)
        }
        val authorizedMunicipalities = user.configuration.authorizedMunicipalities
        assetProvider.getAssets(
            params("assetTypeId").toLong, multiParams(MunicipalityNumber).map(_.toLong),
            boundsFromParams, validFrom = startDate, validTo = endDate).map { asset =>
          asset.copy(readOnly = asset.municipalityNumber.map(isReadOnly(authorizedMunicipalities)).getOrElse(true))
        }
      }
      case _ => throw new UnauthenticatedException()
    }
  }

  private def isReadOnly( authorizedMunicipalities: Set[Long])(municipalityNumber: Long): Boolean = {
    !authorizedMunicipalities.contains(municipalityNumber)
  }

  get("/assets/:assetId") {
    assetProvider.getAssetById(params("assetId").toLong) match {
      case Some(a) => {
        val authorizedMunicipalities = userProvider.getThreadLocalUser().get.configuration.authorizedMunicipalities.toSet
        a.copy(readOnly = a.municipalityNumber.map(isReadOnly(authorizedMunicipalities)).getOrElse(true))
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
        propertyData = List(),
        bearing = bearing)
    val updated = assetProvider.updateAssetLocation(asset)
    logger.debug("Asset updated: " + updated)
    updated
  }

  // TODO: PUT -> POST
  put("/asset") {
    val user = userProvider.getThreadLocalUser().get
    assetProvider.createAsset(
      (parsedBody \ "assetTypeId").extract[Long],
      (parsedBody \ "lon").extract[Int].toDouble,
      (parsedBody \ "lat").extract[Int].toDouble,
      (parsedBody \ "roadLinkId").extract[Long],
      (parsedBody \ "bearing").extract[Int],
      user.username)
  }

  get("/roadlinks") {
    response.setHeader("Access-Control-Allow-Headers", "*");
    val rls = assetProvider.getRoadLinks(params.get(MunicipalityNumber).map(_.toInt), boundsFromParams)
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
    assetProvider.updateAssetProperty(params("assetId").toLong, params("propertyId"), propertyValues)
  }

  delete("/assets/:assetId/properties/:propertyId/values") {
    assetProvider.deleteAssetProperty(params("assetId").toLong, params("propertyId"))
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
    case ue: UnauthenticatedException => halt(Unauthorized("Not authenticated"))
    case e: Exception => logger.error("API Error", e)
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
