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
import com.newrelic.api.agent.NewRelic

class Digiroad2Api extends ScalatraServlet with JacksonJsonSupport with CorsSupport with RequestHeaderAuthentication with GZipSupport {
  val logger = LoggerFactory.getLogger(getClass)
  val MunicipalityNumber = "municipalityNumber"
  val Never = new DateTime().plusYears(1).toString("EEE, dd MMM yyyy HH:mm:ss zzzz")
  // Somewhat arbitrarily chosen limit for bounding box (Math.abs(y1 - y2) * Math.abs(x1 - x2))
  val MAX_BOUNDING_BOX = 100000000
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

  case class StartupParameters(lon: Double, lat: Double, zoom: Int)
  get("/startupParameters") {
    val (east, north, zoom) = params.get("externalAssetId").flatMap { assetId =>
      assetProvider.getAssetPositionByExternalId(assetId.toLong).map { point =>
        (Some(point.x), Some(point.y), Some(12))
      }
    }.getOrElse {
      val config = userProvider.getCurrentUser().configuration
      (config.east.map(_.toDouble), config.north.map(_.toDouble), config.zoom.map(_.toInt))
    }
    StartupParameters(east.getOrElse(390000), north.getOrElse(6900000), zoom.getOrElse(2))
  }

  get("/assets") {
    val user = userProvider.getCurrentUser
    val (validFrom: Option[LocalDate], validTo: Option[LocalDate]) = params.get("validityPeriod") match {
      case Some("past") => (None, Some(LocalDate.now))
      case Some("future") => (Some(LocalDate.now), None)
      case Some("current") => (Some(LocalDate.now), Some(LocalDate.now))
      case _ => (None, None)
    }
    getAssetsBy(user, validFrom, validTo)
  }


  def getAssetsBy(user: User, validFrom: Option[LocalDate], validTo: Option[LocalDate]): Seq[Asset] = {
    assetProvider.getAssets(user, boundsFromParams, validFrom, validTo)
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
        val user = userProvider.getCurrentUser()
        if (user.isOperator() || user.isAuthorizedFor(a.municipalityNumber)) {
          a
        } else {
          Unauthorized("Asset " + params("assetId") + " not authorized")
        }
      }
      case None => NotFound("Asset " + params("assetId") + " not found")
    }
  }

  get("/enumeratedPropertyValues/:assetTypeId") {
    assetProvider.getEnumeratedPropertyValues(params("assetTypeId").toLong)
  }

  put("/assets/:id") {
    val (optionalLon, optionalLat, optionalRoadLinkId, bearing) =
      ((parsedBody \ "lon").extractOpt[Double], (parsedBody \ "lat").extractOpt[Double],
        (parsedBody \ "roadLinkId").extractOpt[Long], (parsedBody \ "bearing").extractOpt[Int])
    val props = (parsedBody \ "properties").extractOpt[Seq[SimpleProperty]].getOrElse(Seq())
    val position = (optionalLon, optionalLat, optionalRoadLinkId) match {
      case (Some(lon), Some(lat), Some(roadLinkId)) => Some(Position(lon, lat, roadLinkId, bearing))
      case _ => None
    }
    assetProvider.updateAsset(params("id").toLong, position, props)
  }

  post("/assets") {
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
    response.setHeader("Access-Control-Allow-Headers", "*")

    val user = userProvider.getCurrentUser()
    val municipalities: Set[Int] = if (user.isOperator()) Set() else user.configuration.authorizedMunicipalities

    params.get("bbox").map { bbox =>
      val boundingRectangle = constructBoundingRectangle(bbox)
      validateBoundingBox(boundingRectangle)
      RoadLinkService.getRoadLinks(
          bounds = boundingRectangle,
          municipalities = municipalities).map { roadLink =>
        val (id, points, length, roadLinkType, _) = roadLink
        Map("roadLinkId" -> id,
            "points" -> points,
            "length" -> length,
            "type" -> roadLinkType.toString)
      }
    } getOrElse {
      BadRequest("Missing mandatory 'bbox' parameter")
    }

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
      NewRelic.noticeError(e)
      halt(InternalServerError("API error"))
    }
  }

  private[this] def boundsFromParams: Option[BoundingRectangle] = {
    val bboxOption = params.get("bbox").map(constructBoundingRectangle)
    bboxOption.foreach(validateBoundingBox)
    if (bboxOption.isEmpty) {
      throw new IllegalArgumentException("Bounding box was missing")
    }
    bboxOption
  }

  private def validateBoundingBox(bbox: BoundingRectangle): Unit = {
    val leftBottom = bbox.leftBottom
    val rightTop = bbox.rightTop
    val width = Math.abs(rightTop.x - leftBottom.x).toLong
    val height = Math.abs(rightTop.y - leftBottom.y).toLong
    if ((width * height) > MAX_BOUNDING_BOX) {
      throw new IllegalArgumentException("Bounding box was too big: " + bbox)
    }
  }

  private[this] def constructBoundingRectangle(bbox: String) = {
    val BBOXList = bbox.split(",").map(_.toDouble)
    BoundingRectangle(Point(BBOXList(0), BBOXList(1)), Point(BBOXList(2), BBOXList(3)))
  }

  get("/speedlimits") {
    val user = userProvider.getCurrentUser()
    val municipalities: Set[Int] = if (user.isOperator()) Set() else user.configuration.authorizedMunicipalities

    params.get("bbox").map { bbox =>
      val boundingRectangle = constructBoundingRectangle(bbox)
      validateBoundingBox(boundingRectangle)
      linearAssetProvider.getSpeedLimits(boundingRectangle, municipalities)
    } getOrElse {
      BadRequest("Missing mandatory 'bbox' parameter")
    }
  }

  get("/speedlimits/:segmentId") {
    val segmentId = params("segmentId")
    linearAssetProvider.getSpeedLimit(segmentId.toLong).getOrElse(NotFound("Speed limit " + segmentId + " not found"))
  }

  put("/speedlimits/:speedLimitId") {
    val user = userProvider.getCurrentUser()
    if (!user.configuration.roles.contains(Role.Operator)) { halt(Unauthorized("User not authorized")) }
    val speedLimitId = params("speedLimitId").toLong
    (parsedBody \ "limit").extractOpt[Int] match {
      case Some(limit) =>
        linearAssetProvider.updateSpeedLimitValue(speedLimitId, limit, user.username) match {
          case Some(id) => linearAssetProvider.getSpeedLimit(id)
          case _ => NotFound("Speed limit " + speedLimitId + " not found")
        }
      case _ => throw new IllegalArgumentException("Speed limit value not provided")
    }
  }

  post("/speedlimits/:speedLimitId") {
    val user = userProvider.getCurrentUser()
    if (!user.configuration.roles.contains(Role.Operator)) { halt(Unauthorized("User not authorized")) }
    linearAssetProvider.splitSpeedLimit(params("speedLimitId").toLong,
                                        (parsedBody \ "roadLinkId").extract[Long],
                                        (parsedBody \ "splitMeasure").extract[Double],
                                        (parsedBody \ "limit").extract[Int],
                                        userProvider.getCurrentUser().username)
  }
}
