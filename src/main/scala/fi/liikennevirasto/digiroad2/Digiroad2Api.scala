package fi.liikennevirasto.digiroad2

import org.scalatra._
import org.json4s._
import org.scalatra.json._
import fi.liikennevirasto.digiroad2.Digiroad2Context._
import fi.liikennevirasto.digiroad2.asset._
import org.joda.time.{LocalDate, DateTime}
import fi.liikennevirasto.digiroad2.authentication.{UserNotFoundException, RequestHeaderAuthentication, UnauthenticatedException}
import org.slf4j.LoggerFactory
import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import fi.liikennevirasto.digiroad2.user.{User}
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
      if(request.isWrite
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
    val (east, north, zoom) = {
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
    val bbox = params.get("bbox").map(constructBoundingRectangle).getOrElse(halt(BadRequest("Bounding box was missing")))
    validateBoundingBox(bbox)
    assetProvider.getAssets(user, bbox, validFrom, validTo)
  }

  get("/massTransitStops") {
    val user = userProvider.getCurrentUser
    val bbox = params.get("bbox").map(constructBoundingRectangle).getOrElse(halt(BadRequest("Bounding box was missing")))
    validateBoundingBox(bbox)
    MassTransitStopService.getByBoundingBox(user, bbox)
  }

  get("/floatingAssets") {
    val user = userProvider.getCurrentUser
    assetProvider.getFloatingAssetsByUser(user)
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
        if (user.isOperator() || user.isAuthorizedToRead(a.municipalityNumber)) {
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

  // TODO: Remove obsolete entry point
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

  // TODO: Deduce useVVHGeometry value from running environment
  private val useVVHGeometry: Boolean = properties.getProperty("digiroad2.useVVHGeometry", "false").toBoolean
  private def massTransitStopPositionParameters(parsedBody: JValue): (Option[Double], Option[Double], Option[Long], Option[Int]) = {
    val lon = (parsedBody \ "lon").extractOpt[Double]
    val lat = (parsedBody \ "lat").extractOpt[Double]
    val roadLinkId = useVVHGeometry match {
      case true => (parsedBody \ "mmlId").extractOpt[Long]
      case false => (parsedBody \ "roadLinkId").extractOpt[Long]
    }
    val bearing = (parsedBody \ "bearing").extractOpt[Int]
    (lon, lat, roadLinkId, bearing)
  }
  private def updateMassTransitStop(id: Long, position: Option[Position], properties: Seq[SimpleProperty]): AssetWithProperties = {
    useVVHGeometry match {
      case true =>
        val asset = assetProvider.updateAsset(id, None, properties)
        val massTransitStop = position.map { position => MassTransitStopService.updatePosition(id, position) }
        massTransitStop.getOrElse(asset)
      case false =>
        assetProvider.updateAsset(id, position, properties)
    }
  }
  put("/massTransitStops/:id") {
    val (optionalLon, optionalLat, optionalRoadLinkId, bearing) = massTransitStopPositionParameters(parsedBody)
    val properties = (parsedBody \ "properties").extractOpt[Seq[SimpleProperty]].getOrElse(Seq())
    val position = (optionalLon, optionalLat, optionalRoadLinkId) match {
      case (Some(lon), Some(lat), Some(roadLinkId)) => Some(Position(lon, lat, roadLinkId, bearing))
      case _ => None
    }
    try {
      updateMassTransitStop(params("id").toLong, position, properties)
    } catch {
      case e: NoSuchElementException => BadRequest("Target roadlink not found")
    }
  }

  post("/assets") {
    val user = userProvider.getCurrentUser()
    assetProvider.createAsset(
      10,
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
        val (id, mmlId, points, length, administrativeClass, functionalClass,
             trafficDirection, modifiedAt, modifiedBy, linkType) = roadLink
        Map("roadLinkId" -> id,
            "mmlId" -> mmlId,
            "points" -> points,
            "length" -> length,
            "administrativeClass" -> administrativeClass.toString,
            "functionalClass" -> functionalClass,
            "trafficDirection" -> trafficDirection.toString,
            "modifiedAt" -> modifiedAt,
            "modifiedBy" -> modifiedBy,
            "linkType" -> linkType)
      }
    } getOrElse {
      BadRequest("Missing mandatory 'bbox' parameter")
    }
  }

  get("/roadlinks2") {
    response.setHeader("Access-Control-Allow-Headers", "*")

    val user = userProvider.getCurrentUser()
    val municipalities: Set[Int] = if (user.isOperator()) Set() else user.configuration.authorizedMunicipalities

    params.get("bbox").map { bbox =>
      val boundingRectangle = constructBoundingRectangle(bbox)
      validateBoundingBox(boundingRectangle)
      RoadLinkService.getRoadLinksFromVVH(
        bounds = boundingRectangle,
        municipalities = municipalities).map { roadLink =>
          Map(
            "mmlId" -> roadLink.mmlId,
            "points" -> roadLink.geometry,
            "administrativeClass" -> roadLink.administrativeClass.toString,
            "linkType" -> roadLink.linkType.value)
        }
    } getOrElse {
      BadRequest("Missing mandatory 'bbox' parameter")
    }
  }

  get("/roadlinks/:id") {
    val withMMLId = params.get("mmlId").getOrElse("false")
    if (withMMLId == "true") {
      val mmlId = params("id").toLong
      RoadLinkService.getRoadLinkMiddlePointByMMLId(mmlId).map {
        case(id, middlePoint) => Map("id" -> id, "middlePoint" -> middlePoint)
      }.getOrElse(NotFound("Road link with MML ID " + mmlId + " not found"))
    } else {
      BadRequest("Roadlinks can be fetched with MML ID only")
    }
  }

  get("/roadlinks/adjacent/:id"){
    val id = params("id").toLong
    RoadLinkService.getAdjacent(id)
  }

  put("/linkproperties/:id") {
    val id = params("id").toLong
    val municipalityCode = RoadLinkService.getMunicipalityCode(id)
    val user = userProvider.getCurrentUser()
    hasWriteAccess(user, municipalityCode.get)
    val trafficDirection = TrafficDirection((parsedBody \ "trafficDirection").extract[String])
    val functionalClass = (parsedBody \ "functionalClass").extract[Int]
    val linkType = (parsedBody \ "linkType").extract[Int]
    RoadLinkService.adjustTrafficDirection(id, trafficDirection, user.username)
    RoadLinkService.adjustFunctionalClass(id, functionalClass, user.username)
    RoadLinkService.adjustLinkType(id, linkType, user.username)
    val (_, mmlId, points, length, administrativeClass,
         updatedFunctionalClass, updatedTrafficDirection,
         modifiedAt, modifiedBy, updatedLinkType) = RoadLinkService.getRoadLink(id)
    Map("roadLinkId" -> id,
      "mmlId" -> mmlId,
      "points" -> points,
      "length" -> length,
      "administrativeClass" -> administrativeClass.toString,
      "functionalClass" -> updatedFunctionalClass,
      "trafficDirection" -> updatedTrafficDirection.toString,
      "modifiedAt" -> modifiedAt,
      "modifiedBy" -> modifiedBy,
      "linkType" -> updatedLinkType)
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

  private def validateBoundingBox(bbox: BoundingRectangle): Unit = {
    val leftBottom = bbox.leftBottom
    val rightTop = bbox.rightTop
    val width = Math.abs(rightTop.x - leftBottom.x).toLong
    val height = Math.abs(rightTop.y - leftBottom.y).toLong
    if ((width * height) > MAX_BOUNDING_BOX) {
      halt(BadRequest("Bounding box was too big: " + bbox))
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

  get("/numericallimits") {
    val user = userProvider.getCurrentUser()
    val municipalities: Set[Int] = if (user.isOperator()) Set() else user.configuration.authorizedMunicipalities
    val typeId = params.getOrElse("typeId", halt(BadRequest("Missing mandatory 'typeId' parameter"))).toInt
    params.get("bbox").map { bbox =>
      val boundingRectangle = constructBoundingRectangle(bbox)
      validateBoundingBox(boundingRectangle)
      NumericalLimitService.getByBoundingBox(typeId, boundingRectangle, municipalities)
    } getOrElse {
      BadRequest("Missing mandatory 'bbox' parameter")
    }
  }

  get("/numericallimits/:segmentId") {
    val segmentId = params("segmentId")
    NumericalLimitService.getById(segmentId.toLong)
      .getOrElse(NotFound("Numerical limit " + segmentId + " not found"))
  }

  private def validateNumericalLimitValue(value: BigInt): Unit = {
    if (value > Integer.MAX_VALUE) {
      halt(BadRequest("Numerical limit value too big"))
    } else if (value < 0) {
      halt(BadRequest("Numerical limit value cannot be negative"))
    }
  }


  put("/numericallimits/:id") {
    val user = userProvider.getCurrentUser()
    val id = params("id").toLong
    if (!user.hasEarlyAccess() || !AssetService.getMunicipalityCodes(id).forall(user.isAuthorizedToWrite(_))) {
      halt(Unauthorized("User not authorized"))
    }
    val expiredOption: Option[Boolean] = (parsedBody \ "expired").extractOpt[Boolean]
    val valueOption: Option[BigInt] = (parsedBody \ "value").extractOpt[BigInt]
    (expiredOption, valueOption) match {
      case (None, None) => BadRequest("Numerical limit value or expiration not provided")
      case (expired, value) =>
        value.foreach(validateNumericalLimitValue)
        NumericalLimitService.updateNumericalLimit(id, value.map(_.intValue()), expired.getOrElse(false), user.username) match {
          case Some(segmentId) => NumericalLimitService.getById(segmentId)
          case None => NotFound("Numerical limit " + id + " not found")
        }
    }
  }

  post("/numericallimits") {
    val user = userProvider.getCurrentUser()
    val roadLinkId = (parsedBody \ "roadLinkId").extract[Long]
    val municipalityCode = RoadLinkService.getMunicipalityCode(roadLinkId)
    hasWriteAccess(user, municipalityCode.get)
    val typeId = params.getOrElse("typeId", halt(BadRequest("Missing mandatory 'typeId' parameter"))).toInt
    val value = (parsedBody \ "value").extract[BigInt]
    validateNumericalLimitValue(value)
    val username = user.username
    NumericalLimitService.createNumericalLimit(typeId = typeId,
                                         roadLinkId = roadLinkId,
                                         value = value.intValue,
                                         username = username)
  }

  post("/numericallimits/:id") {
    val user = userProvider.getCurrentUser()
    val roadLinkId = (parsedBody \ "roadLinkId").extract[Long]
    val municipalityCode = RoadLinkService.getMunicipalityCode(roadLinkId)
    hasWriteAccess(user, municipalityCode.get)
    val value = (parsedBody \ "value").extract[BigInt]
    validateNumericalLimitValue(value)
    val expired = (parsedBody \ "expired").extract[Boolean]
    val id = params("id").toLong
    val username = user.username
    val measure = (parsedBody \ "splitMeasure").extract[Double]
    NumericalLimitService.split(id, roadLinkId, measure, value.intValue, expired, username)
  }

  get("/speedlimits/:segmentId") {
    val segmentId = params("segmentId")
    linearAssetProvider.getSpeedLimit(segmentId.toLong).getOrElse(NotFound("Speed limit " + segmentId + " not found"))
  }

  put("/speedlimits/:speedLimitId") {
    val user = userProvider.getCurrentUser()
    val speedLimitId = params("speedLimitId").toLong
    if (!user.hasEarlyAccess() || !AssetService.getMunicipalityCodes(speedLimitId).forall(user.isAuthorizedToWrite(_))) {
      halt(Unauthorized("User not authorized"))
    }
    (parsedBody \ "limit").extractOpt[Int] match {
      case Some(limit) =>
        linearAssetProvider.updateSpeedLimitValue(speedLimitId, limit, user.username) match {
          case Some(id) => linearAssetProvider.getSpeedLimit(id)
          case _ => NotFound("Speed limit " + speedLimitId + " not found")
        }
      case _ => BadRequest("Speed limit value not provided")
    }
  }

  put("/speedlimits") {
    val user = userProvider.getCurrentUser()
    val optionalValue = (parsedBody \ "value").extractOpt[Int]
    val optionalIds = (parsedBody \ "ids").extractOpt[Seq[Long]]
    val authorizedForMunicipalities: Boolean = optionalIds
      .map(_.forall(AssetService.getMunicipalityCodes(_).forall(user.isAuthorizedToWrite)))
      .getOrElse(halt(BadRequest("Speed limit id list not provided")))

    if (!user.hasEarlyAccess() || !authorizedForMunicipalities) {
      halt(Unauthorized("User not authorized"))
    }
    optionalValue match {
      case Some(value) => linearAssetProvider.updateSpeedLimitValues(optionalIds.get, value, user.username)
      case _ => BadRequest("Speed limit value not provided")
    }
  }

  post("/speedlimits/:speedLimitId") {
    val user = userProvider.getCurrentUser()
    val roadLinkId = (parsedBody \ "roadLinkId").extract[Long]
    val municipalityCode = RoadLinkService.getMunicipalityCode(roadLinkId)
    hasWriteAccess(user, municipalityCode.get)
    linearAssetProvider.splitSpeedLimit(params("speedLimitId").toLong,
                                        roadLinkId,
                                        (parsedBody \ "splitMeasure").extract[Double],
                                        (parsedBody \ "limit").extract[Int],
                                        userProvider.getCurrentUser().username)
  }

  def hasWriteAccess(user: User, municipality: Int) {
    if (!user.hasEarlyAccess() || !user.isAuthorizedToWrite(municipality)) {
      halt(Unauthorized("User not authorized"))
    }
  }

  get("/manoeuvres") {
    val user = userProvider.getCurrentUser()
    val municipalities: Set[Int] = if (user.isOperator()) Set() else user.configuration.authorizedMunicipalities
    params.get("bbox").map { bbox =>
      val boundingRectangle = constructBoundingRectangle(bbox)
      validateBoundingBox(boundingRectangle)
      ManoeuvreService.getByBoundingBox(boundingRectangle, municipalities)
    } getOrElse {
      BadRequest("Missing mandatory 'bbox' parameter")
    }
  }

  post("/manoeuvres") {
    val user = userProvider.getCurrentUser()

    val manoeuvres = (parsedBody \ "manoeuvres").extractOrElse[Seq[NewManoeuvre]](halt(BadRequest("Malformed 'manoeuvres' parameter")))

    val manoeuvreIds = manoeuvres.map { manoeuvre =>
      val municipality = RoadLinkService.getMunicipalityCode(manoeuvre.sourceRoadLinkId)
      hasWriteAccess(user, municipality.get)
      ManoeuvreService.createManoeuvre(user.username, manoeuvre)
    }
    Created(manoeuvreIds)
  }

  delete("/manoeuvres") {
    val user = userProvider.getCurrentUser()

    val manoeuvreIds = (parsedBody \ "manoeuvreIds").extractOrElse[Seq[Long]](halt(BadRequest("Malformed 'manoeuvreIds' parameter")))

    manoeuvreIds.foreach { manoeuvreId =>
      val sourceRoadLinkId = ManoeuvreService.getSourceRoadLinkIdById(manoeuvreId)
      hasWriteAccess(user, RoadLinkService.getMunicipalityCode(sourceRoadLinkId).get)
      ManoeuvreService.deleteManoeuvre(user.username, manoeuvreId)
    }
  }

  put("/manoeuvres") {
    val user = userProvider.getCurrentUser()

    val manoeuvreUpdates: Map[Long, ManoeuvreUpdates] = parsedBody
      .extractOrElse[Map[String, ManoeuvreUpdates]](halt(BadRequest("Malformed body on put manoeuvres request")))
      .map{case(id, updates) => (id.toLong, updates)}
    manoeuvreUpdates.foreach{ case(id, updates) =>
      val sourceRoadLinkId = ManoeuvreService.getSourceRoadLinkIdById(id)
      hasWriteAccess(user, RoadLinkService.getMunicipalityCode(sourceRoadLinkId).get)
      ManoeuvreService.updateManoeuvre(user.username, id, updates)
    }
  }
}
