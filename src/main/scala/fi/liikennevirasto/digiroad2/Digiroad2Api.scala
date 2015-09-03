package fi.liikennevirasto.digiroad2

import com.newrelic.api.agent.NewRelic
import fi.liikennevirasto.digiroad2.Digiroad2Context._
import fi.liikennevirasto.digiroad2.asset.{BoundingRectangle, _}
import fi.liikennevirasto.digiroad2.asset.oracle.AssetPropertyConfiguration
import fi.liikennevirasto.digiroad2.authentication.{RequestHeaderAuthentication, UnauthenticatedException, UserNotFoundException}
import fi.liikennevirasto.digiroad2.linearasset.{LinearAssetProvider, NewLimit, RoadLinkPartitioner}
import fi.liikennevirasto.digiroad2.user.User
import org.apache.commons.lang3.StringUtils.isBlank
import org.joda.time.DateTime
import org.json4s._
import org.scalatra._
import org.scalatra.json._
import org.slf4j.LoggerFactory

class Digiroad2Api(val roadLinkService: RoadLinkService,
                   val linearAssetProvider: LinearAssetProvider,
                   val massTransitStopService: MassTransitStopService) extends ScalatraServlet
with JacksonJsonSupport
with CorsSupport
with RequestHeaderAuthentication
with GZipSupport {
  val logger = LoggerFactory.getLogger(getClass)
  val MunicipalityNumber = "municipalityNumber"
  val Never = new DateTime().plusYears(1).toString("EEE, dd MMM yyyy HH:mm:ss zzzz")
  // Somewhat arbitrarily chosen limit for bounding box (Math.abs(y1 - y2) * Math.abs(x1 - x2))
  val MAX_BOUNDING_BOX = 100000000
  case object DateTimeSerializer extends CustomSerializer[DateTime](format => ({ null }, { case d: DateTime => JString(d.toString(AssetPropertyConfiguration.DateTimePropertyFormat))}))
  case object SideCodeSerializer extends CustomSerializer[SideCode](format => ({ null }, { case s: SideCode => JInt(s.value)}))
  case object TrafficDirectionSerializer extends CustomSerializer[TrafficDirection](format => ({ null }, { case t: TrafficDirection => JString(t.toString)}))
  protected implicit val jsonFormats: Formats = DefaultFormats + DateTimeSerializer + SideCodeSerializer + TrafficDirectionSerializer

  before() {
    contentType = formats("json") + "; charset=utf-8"
    try {
      authenticateForApi(request)(userProvider)
      if(request.isWrite && !userProvider.getCurrentUser().hasWriteAccess()){
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

  get("/massTransitStops") {
    val user = userProvider.getCurrentUser()
    val bbox = params.get("bbox").map(constructBoundingRectangle).getOrElse(halt(BadRequest("Bounding box was missing")))
    validateBoundingBox(bbox)
    useVVHGeometry match {
      case true => massTransitStopService.getByBoundingBox(user, bbox)
      case false => throw new NotImplementedError()
    }
  }

  get("/user/roles") {
    userProvider.getCurrentUser().configuration.roles
  }

  get("/massTransitStops/:nationalId") {
    def validateMunicipalityAuthorization(nationalId: Long)(municipalityCode: Int): Unit = {
      if (!userProvider.getCurrentUser().isAuthorizedToRead(municipalityCode))
        halt(Unauthorized("User not authorized for mass transit stop " + nationalId))
    }
    val nationalId = params("nationalId").toLong
    val massTransitStop = useVVHGeometry match {
      case true => massTransitStopService.getMassTransitStopByNationalId(nationalId, validateMunicipalityAuthorization(nationalId)).map { stop =>
         Map("id" -> stop.id,
          "nationalId" -> stop.nationalId,
          "stopTypes" -> stop.stopTypes,
          "lat" -> stop.lat,
          "lon" -> stop.lon,
          "validityDirection" -> stop.validityDirection,
          "bearing" -> stop.bearing,
          "validityPeriod" -> stop.validityPeriod,
          "floating" -> stop.floating,
          "propertyData" -> stop.propertyData)
      }
      case false => throw new NotImplementedError()
    }
    massTransitStop.getOrElse(NotFound("Mass transit stop " + nationalId + " not found"))
  }

  get("/massTransitStops/floating") {
    val user = userProvider.getCurrentUser()
    val includedMunicipalities = user.isOperator() match {
      case true => None
      case false => Some(user.configuration.authorizedMunicipalities)
    }
    massTransitStopService.getFloatingStops(includedMunicipalities)
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

  put("/massTransitStops/:id") {
    def validateMunicipalityAuthorization(id: Long)(municipalityCode: Int): Unit = {
      if (!userProvider.getCurrentUser().isAuthorizedToWrite(municipalityCode))
        halt(Unauthorized("User cannot update mass transit stop " + id + ". No write access to municipality " + municipalityCode))
    }
    val (optionalLon, optionalLat, optionalRoadLinkId, bearing) = massTransitStopPositionParameters(parsedBody)
    val properties = (parsedBody \ "properties").extractOpt[Seq[SimpleProperty]].getOrElse(Seq())
    val position = (optionalLon, optionalLat, optionalRoadLinkId) match {
      case (Some(lon), Some(lat), Some(roadLinkId)) => Some(Position(lon, lat, roadLinkId, bearing))
      case _ => None
    }
    try {
      val id = params("id").toLong
      useVVHGeometry match {
        case true =>
          massTransitStopService.updateExistingById(id, position, properties.toSet, userProvider.getCurrentUser().username, validateMunicipalityAuthorization(id))
        case false =>
          assetProvider.updateAsset(id, position, properties)
      }
    } catch {
      case e: NoSuchElementException => BadRequest("Target roadlink not found")
    }
  }

  private def createMassTransitStop(lon: Double, lat: Double, roadLinkId: Long, bearing: Int, properties: Seq[SimpleProperty]): Map[String, Any] = {
     useVVHGeometry match {
      case true =>
        val massTransitStop = massTransitStopService.createNew(lon, lat, roadLinkId, bearing, userProvider.getCurrentUser().username, properties)
        Map("id" -> massTransitStop.id,
          "nationalId" -> massTransitStop.nationalId,
          "stopTypes" -> massTransitStop.stopTypes,
          "lat" -> massTransitStop.lat,
          "lon" -> massTransitStop.lon,
          "validityDirection" -> massTransitStop.validityDirection,
          "bearing" -> massTransitStop.bearing,
          "validityPeriod" -> massTransitStop.validityPeriod,
          "floating" -> massTransitStop.floating,
          "propertyData" -> massTransitStop.propertyData)
      case false => throw new NotImplementedError()
     }
  }
  private def validateUserRights(roadLinkId: Long) = {
    if(useVVHGeometry) {
      val authorized: Boolean = roadLinkService.fetchVVHRoadlink(roadLinkId).map(_.municipalityCode).exists(userProvider.getCurrentUser().isAuthorizedToWrite)
      if (!authorized) halt(Unauthorized("User not authorized"))
    }
  }
  private def validateCreationProperties(properties: Seq[SimpleProperty]) = {
    if(useVVHGeometry) {
      val mandatoryProperties: Map[String, String] = massTransitStopService.mandatoryProperties()
      val nonEmptyMandatoryProperties: Seq[SimpleProperty] = properties.filter { property =>
        mandatoryProperties.contains(property.publicId) && property.values.nonEmpty
      }
      val missingProperties: Set[String] = mandatoryProperties.keySet -- nonEmptyMandatoryProperties.map(_.publicId).toSet
      if (missingProperties.nonEmpty) halt(BadRequest("Missing mandatory properties: " + missingProperties.mkString(", ")))
      val propertiesWithInvalidValues = nonEmptyMandatoryProperties.filter { property =>
        val propertyType = mandatoryProperties(property.publicId)
        propertyType match {
          case PropertyTypes.MultipleChoice =>
            property.values.forall { value => isBlank(value.propertyValue) || value.propertyValue.toInt == 99 }
          case _ =>
            property.values.forall { value => isBlank(value.propertyValue) }
        }
      }
      if (propertiesWithInvalidValues.nonEmpty)
        halt(BadRequest("Invalid property values on: " + propertiesWithInvalidValues.map(_.publicId).mkString(", ")))
    }
  }
  post("/massTransitStops") {
    val positionParameters = massTransitStopPositionParameters(parsedBody)
    val lon = positionParameters._1.get
    val lat = positionParameters._2.get
    val roadLinkId = positionParameters._3.get
    val bearing = positionParameters._4.get
    val properties = (parsedBody \ "properties").extract[Seq[SimpleProperty]]
    validateUserRights(roadLinkId)
    validateCreationProperties(properties)
    createMassTransitStop(lon, lat, roadLinkId, bearing, properties)
  }


  private def getRoadLinks(municipalities: Set[Int])(bbox: String): Seq[Map[String, Any]] = {
    val boundingRectangle = constructBoundingRectangle(bbox)
    validateBoundingBox(boundingRectangle)
    RoadLinkService.getRoadLinks(
      bounds = boundingRectangle,
      municipalities = municipalities).map { roadLink =>
      Map("roadLinkId" -> roadLink.id,
        "mmlId" -> roadLink.mmlId,
        "points" -> roadLink.geometry,
        "length" -> roadLink.length,
        "administrativeClass" -> roadLink.administrativeClass.toString,
        "functionalClass" -> roadLink.functionalClass,
        "trafficDirection" -> roadLink.trafficDirection.toString,
        "modifiedAt" -> roadLink.modifiedAt,
        "modifiedBy" -> roadLink.modifiedBy,
        "linkType" -> roadLink.linkType)
    }
  }

  private def getRoadLinksFromVVH(municipalities: Set[Int])(bbox: String): Seq[Seq[Map[String, Any]]]  = {
    val boundingRectangle = constructBoundingRectangle(bbox)
    validateBoundingBox(boundingRectangle)
    val roadLinks = roadLinkService.getRoadLinksFromVVH(boundingRectangle, municipalities)
    val partitionedRoadLinks = RoadLinkPartitioner.partition(roadLinks)
    partitionedRoadLinks.map { group => group.map { roadLink =>
      Map(
        "mmlId" -> roadLink.mmlId,
        "points" -> roadLink.geometry,
        "administrativeClass" -> roadLink.administrativeClass.toString,
        "linkType" -> roadLink.linkType.value,
        "functionalClass" -> roadLink.functionalClass,
        "trafficDirection" -> roadLink.trafficDirection.toString,
        "modifiedAt" -> roadLink.modifiedAt,
        "modifiedBy" -> roadLink.modifiedBy,
        "municipalityCode" -> roadLink.attributes.get("MUNICIPALITYCODE"),
        "roadNameFi" -> roadLink.attributes.get("ROADNAME_FI"),
        "roadNameSe" -> roadLink.attributes.get("ROADNAME_SE"),
        "roadNameSm" -> roadLink.attributes.get("ROADNAME_SM"),
        "minAddressNumberRight" -> roadLink.attributes.get("MINANRIGHT"),
        "maxAddressNumberRight" -> roadLink.attributes.get("MAXANRIGHT"),
        "minAddressNumberLeft" -> roadLink.attributes.get("MINANLEFT"),
        "maxAddressNumberLeft" -> roadLink.attributes.get("MAXANLEFT"))
    } }
  }

  get("/roadlinks") {
    response.setHeader("Access-Control-Allow-Headers", "*")

    val user = userProvider.getCurrentUser()
    val municipalities: Set[Int] = if (user.isOperator()) Set() else user.configuration.authorizedMunicipalities

    params.get("bbox")
      .map (getRoadLinks(municipalities))
      .getOrElse (BadRequest("Missing mandatory 'bbox' parameter"))
  }

  get("/roadlinks2") {
    response.setHeader("Access-Control-Allow-Headers", "*")

    val user = userProvider.getCurrentUser()
    val municipalities: Set[Int] = if (user.isOperator()) Set() else user.configuration.authorizedMunicipalities

    val getRoadLinksFn = useVVHGeometry match {
      case true => getRoadLinksFromVVH(municipalities) _
      case false => getRoadLinks(municipalities) _
    }

    params.get("bbox")
      .map (getRoadLinksFn)
      .getOrElse (BadRequest("Missing mandatory 'bbox' parameter"))
  }

  get("/roadlinks/:mmlId") {
    val mmlId = params("mmlId").toLong
    roadLinkService.getRoadLinkMiddlePointByMMLId(mmlId).map {
      case (id, middlePoint) => Map("id" -> id, "middlePoint" -> middlePoint)
    }.getOrElse(NotFound("Road link with MML ID " + mmlId + " not found"))
  }

  get("/roadlinks/adjacent/:id"){
    val id = params("id").toLong
    RoadLinkService.getAdjacent(id)
  }

  get("/roadLinks/incomplete") {
    val user = userProvider.getCurrentUser()
    val includedMunicipalities = user.isOperator() match {
      case true => None
      case false => Some(user.configuration.authorizedMunicipalities)
    }
    roadLinkService.getIncompleteLinks(includedMunicipalities)
  }

  put("/linkproperties") {
    val ids = (parsedBody \ "mmlIds").extract[Seq[Long]]
    updateRoadLinkProperties(ids)
  }

  def updateRoadLinkProperties(ids: Seq[Long]): Seq[Map[String, Any]] = {
    val trafficDirection = TrafficDirection((parsedBody \ "trafficDirection").extract[String])
    val functionalClass = (parsedBody \ "functionalClass").extract[Int]
    val linkType = LinkType((parsedBody \ "linkType").extract[Int])

    val user = userProvider.getCurrentUser()
    def municipalityValidation(municipalityCode: Int) = validateUserMunicipalityAccess(user)(municipalityCode)
    ids.map { id =>
      roadLinkService.updateProperties(id, functionalClass, linkType, trafficDirection, user.username, municipalityValidation).map { roadLink =>
        Map("mmlId" -> roadLink.mmlId,
          "points" -> roadLink.geometry,
          "administrativeClass" -> roadLink.administrativeClass.toString,
          "functionalClass" -> roadLink.functionalClass,
          "trafficDirection" -> roadLink.trafficDirection.toString,
          "modifiedAt" -> roadLink.modifiedAt,
          "modifiedBy" -> roadLink.modifiedBy,
          "linkType" -> roadLink.linkType.value)
      }.getOrElse(halt(NotFound("Road link with MML ID " + id + " not found")))
    }
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
    case e: Exception =>
      logger.error("API Error", e)
      NewRelic.noticeError(e)
      halt(InternalServerError("API error"))
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
      linearAssetProvider.getSpeedLimits(boundingRectangle, municipalities).map { linkPartition =>
        linkPartition.map { link =>
          Map(
            "id" -> (if (link.id == 0) None else Some(link.id)),
            "mmlId" -> link.mmlId,
            "sideCode" -> link.sideCode,
            "trafficDirection" -> link.trafficDirection,
            "value" -> link.value,
            "points" -> link.geometry,
            "startMeasure" -> link.startMeasure,
            "endMeasure" -> link.endMeasure,
            "modifiedBy" -> link.modifiedBy,
            "modifiedAt" -> link.modifiedDateTime,
            "createdBy" -> link.createdBy,
            "createdAt" -> link.createdDateTime
          )
        }
      }
    } getOrElse {
      BadRequest("Missing mandatory 'bbox' parameter")
    }
  }

  get("/speedlimits/unknown") {
    val user = userProvider.getCurrentUser()
    val includedMunicipalities = user.isOperator() match {
      case true => None
      case false => Some(user.configuration.authorizedMunicipalities)
    }
    linearAssetProvider.getUnknownSpeedLimits(includedMunicipalities)
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
    if (!user.hasEarlyAccess() || !assetService.getMunicipalityCodes(id).forall(user.isAuthorizedToWrite)) {
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
    validateUserMunicipalityAccess(user)(municipalityCode.get)
    val typeId = params.getOrElse("typeId", halt(BadRequest("Missing mandatory 'typeId' parameter"))).toInt
    val value = (parsedBody \ "value").extract[BigInt]
    validateNumericalLimitValue(value)
    val username = user.username
    NumericalLimitService.createNumericalLimit(typeId = typeId,
                                         roadLinkId = roadLinkId,
                                         value = value.intValue(),
                                         username = username)
  }

  post("/numericallimits/:id") {
    val user = userProvider.getCurrentUser()
    val roadLinkId = (parsedBody \ "roadLinkId").extract[Long]
    val municipalityCode = RoadLinkService.getMunicipalityCode(roadLinkId)
    validateUserMunicipalityAccess(user)(municipalityCode.get)
    val value = (parsedBody \ "value").extract[BigInt]
    validateNumericalLimitValue(value)
    val expired = (parsedBody \ "expired").extract[Boolean]
    val id = params("id").toLong
    val username = user.username
    val measure = (parsedBody \ "splitMeasure").extract[Double]
    NumericalLimitService.split(id, roadLinkId, measure, value.intValue(), expired, username)
  }

  put("/speedlimits") {
    val user = userProvider.getCurrentUser()
    val optionalValue = (parsedBody \ "value").extractOpt[Int]
    val ids = (parsedBody \ "ids").extract[Seq[Long]]
    val newLimits = (parsedBody \ "newLimits").extract[Seq[NewLimit]]
    optionalValue match {
      case Some(value) =>
        val updatedIds = linearAssetProvider.updateSpeedLimitValues(ids, value, user.username, validateUserMunicipalityAccess(user))
        val createdIds = linearAssetProvider.createSpeedLimits(newLimits, value, user.username, validateUserMunicipalityAccess(user))
        linearAssetProvider.getSpeedLimits(updatedIds ++ createdIds)
      case _ => BadRequest("Speed limit value not provided")
    }
  }

  post("/speedlimits/:speedLimitId/split") {
    val user = userProvider.getCurrentUser()

    linearAssetProvider.splitSpeedLimit(params("speedLimitId").toLong,
      (parsedBody \ "splitMeasure").extract[Double],
      (parsedBody \ "existingValue").extract[Int],
      (parsedBody \ "createdValue").extract[Int],
      user.username,
      validateUserMunicipalityAccess(user))
  }

  post("/speedlimits/:speedLimitId/separate") {
    val user = userProvider.getCurrentUser()

    linearAssetProvider.separateSpeedLimit(params("speedLimitId").toLong,
      (parsedBody \ "valueTowardsDigitization").extract[Int],
      (parsedBody \ "valueAgainstDigitization").extract[Int],
      user.username,
      validateUserMunicipalityAccess(user))
  }

  post("/speedlimits") {
    val user = userProvider.getCurrentUser()

    val newLimit = NewLimit((parsedBody \ "mmlId").extract[Long],
                            (parsedBody \ "startMeasure").extract[Double],
                            (parsedBody \ "endMeasure").extract[Double])

    linearAssetProvider.createSpeedLimits(Seq(newLimit),
                                         (parsedBody \ "value").extract[Int],
                                         user.username,
                                         validateUserMunicipalityAccess(user)).headOption match {
      case Some(id) => linearAssetProvider.getSpeedLimit(id)
      case _ => BadRequest("Speed limit creation failed")
    }
  }

  private def validateUserMunicipalityAccess(user: User)(municipality: Int): Unit = {
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
      validateUserMunicipalityAccess(user)(municipality.get)
      ManoeuvreService.createManoeuvre(user.username, manoeuvre)
    }
    Created(manoeuvreIds)
  }

  delete("/manoeuvres") {
    val user = userProvider.getCurrentUser()

    val manoeuvreIds = (parsedBody \ "manoeuvreIds").extractOrElse[Seq[Long]](halt(BadRequest("Malformed 'manoeuvreIds' parameter")))

    manoeuvreIds.foreach { manoeuvreId =>
      val sourceRoadLinkId = ManoeuvreService.getSourceRoadLinkIdById(manoeuvreId)
      validateUserMunicipalityAccess(user)(RoadLinkService.getMunicipalityCode(sourceRoadLinkId).get)
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
      validateUserMunicipalityAccess(user)(RoadLinkService.getMunicipalityCode(sourceRoadLinkId).get)
      ManoeuvreService.updateManoeuvre(user.username, id, updates)
    }
  }
}
