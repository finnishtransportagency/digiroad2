package fi.liikennevirasto.digiroad2

import org.scalatra._
import org.json4s._
import org.scalatra.json._
import fi.liikennevirasto.digiroad2.Digiroad2Context._
import org.json4s.JsonDSL._
import fi.liikennevirasto.digiroad2.asset._
import org.joda.time.{LocalDate, DateTime}
import fi.liikennevirasto.digiroad2.authentication.{UserNotFoundException, RequestHeaderAuthentication, UnauthenticatedException}
import org.slf4j.LoggerFactory
import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import scala.Some
import fi.liikennevirasto.digiroad2.asset.PropertyValue
import fi.liikennevirasto.digiroad2.user.{Role, User}

class Digiroad2Api extends ScalatraServlet with JacksonJsonSupport with CorsSupport with RequestHeaderAuthentication with GZipSupport {
  val logger = LoggerFactory.getLogger(getClass)
  val MunicipalityNumber = "municipalityNumber"
  val Never = new DateTime().plusYears(1).toString("EEE, dd MMM yyyy HH:mm:ss zzzz")
  protected implicit val jsonFormats: Formats = DefaultFormats

  before() {
    contentType = formats("json")
    try {
      authenticateForApi(request)(userProvider)
      // oskari makes POSTs to /layers, allow those to pass
      if(request.isWrite
            && request.getPathInfo.equalsIgnoreCase("/layers") == false
            && userProvider.getCurrentUser().hasWriteAccess() == false){
        halt(Unauthorized("No write permissions"))
      }
    } catch {
      case ise: IllegalStateException => halt(Unauthorized("Authentication error: " + ise.getMessage))
    }
    response.setHeader(Digiroad2ServerOriginatedResponseHeader, "true")
  }

  get("/config") {
    val (east, north, zoom) = params.get("externalAssetId").flatMap { assetId =>
        assetProvider.getAssetPositionByExternalId(assetId.toLong).map { case (east, north) =>
        (Some(east), Some(north), Some(12))
      }
    }.getOrElse {
      val config = userProvider.getCurrentUser.configuration
      (config.east.map(_.toDouble), config.north.map(_.toDouble), config.zoom.map(_.toInt))
    }
    readJsonFromBody(MapConfigJson.mapConfig(userProvider.getCurrentUser.configuration, east, north, zoom))
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
    getAssetsBy(user, validFrom, validTo, params("assetTypeId").toLong)
  }


  def getAssetsBy(user: User, validFrom: Option[LocalDate], validTo: Option[LocalDate], assetTypeId: Long): Seq[Asset] = {
    assetProvider.getAssets(
      assetTypeId, user, boundsFromParams, validFrom, validTo)
  }

  private def isReadOnly(user: User)(municipalityNumber: Int): Boolean = {
    !(user.configuration.roles.contains(Role.Operator) || user.configuration.authorizedMunicipalities.contains(municipalityNumber))
  }

  get("/assets/municipality/:municipality") {
    assetProvider.getAssetsByMunicipality(params("municipality").toInt)

  }

  get("/user/roles") {
    userProvider.getCurrentUser().configuration.roles
  }

  get("/assets/:assetId") {
    val getAssetById = if (params.get("externalId").isDefined) {
      assetProvider.getAssetByExternalId _
    } else {
      assetProvider.getAssetById _
    }
    getAssetById(params("assetId").toLong) match {
      case Some(a) => {
        if (a.municipalityNumber.map(isReadOnly(userProvider.getCurrentUser())).getOrElse(true)) {
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
    val (lon, lat, roadLinkId, bearing) =
      ((parsedBody \ "lon").extractOpt[Double], (parsedBody \ "lat").extractOpt[Double],
        (parsedBody \ "roadLinkId").extractOpt[Long], (parsedBody \ "bearing").extractOpt[Int])
    val props = (parsedBody \ "properties").extractOpt[Seq[SimpleProperty]]
    val position = lon match {
      case Some(_) => Some(Position(lon.get, lat.get, roadLinkId.get, bearing))
      case None => None
    }
    assetProvider.updateAsset(params("id").toLong, position, props)
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
    val bboxOption = params.get("bbox").map { b =>
      val BBOXList = b.split(",").map(_.toDouble)
      (BBOXList(0), BBOXList(1), BBOXList(2), BBOXList(3))
    }

    // TODO: check bounds are within range to avoid oversized queries
    val user = userProvider.getCurrentUser()
    response.setHeader("Access-Control-Allow-Headers", "*")

    val rls = assetProvider.getRoadLinks(user, boundsFromParams)
    ("type" -> "FeatureCollection") ~
      ("features" ->  rls.map { rl =>
        ("type" -> "Feature") ~ ("properties" -> ("roadLinkId" -> rl.id)) ~ ("geometry" ->
          ("type" -> "LineString") ~ ("coordinates" -> rl.lonLat.map { ll =>
            List(ll._1, ll._2)
          }) ~ ("crs" -> ("type" -> "OGC") ~ ("properties" -> ("urn" -> "urn:ogc:def:crs:OGC:1.3:ETRS89")))
        )
      })
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

  get("/assetPropertyNames/:language") {
    val lang = params("language")
    assetProvider.assetPropertyNames(lang)
  }

  error {
    case ise: IllegalStateException => halt(InternalServerError("Illegal state: " + ise.getMessage))
    case ue: UnauthenticatedException => halt(Unauthorized("Not authenticated"))
    case unf: UserNotFoundException => halt(Forbidden(unf.username))
    case e: Exception => {
      logger.error("API Error", e)
      halt(InternalServerError("API error"))
    }
  }

  private[this] def boundsFromParams: Option[BoundingRectangle] = {
    params.get("bbox").map { b =>
      val BBOXList = b.split(",").map(_.toDouble)
      BoundingRectangle(Point(BBOXList(0), BBOXList(1)), Point(BBOXList(2), BBOXList(3)))
    }
  }
}
