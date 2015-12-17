package fi.liikennevirasto.digiroad2

import com.newrelic.api.agent.NewRelic
import fi.liikennevirasto.digiroad2.asset.Asset._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.authentication.{RequestHeaderAuthentication, UnauthenticatedException, UserNotFoundException}
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.user.{UserProvider, User}
import org.apache.commons.lang3.StringUtils.isBlank
import org.joda.time.DateTime
import org.json4s._
import org.scalatra._
import org.scalatra.json._
import org.slf4j.LoggerFactory

case class ExistingLinearAsset(id: Long, mmlId: Long)

case class NewNumericValueAsset(mmlId: Long, startMeasure: Double, endMeasure: Double, value: Int, sideCode: Int)

case class NewProhibition(mmlId: Long, startMeasure: Double, endMeasure: Double, value: Seq[ProhibitionValue], sideCode: Int)

class Digiroad2Api(val roadLinkService: RoadLinkService,
                   val speedLimitProvider: SpeedLimitProvider,
                   val obstacleService: ObstacleService = Digiroad2Context.obstacleService,
                   val railwayCrossingService: RailwayCrossingService = Digiroad2Context.railwayCrossingService,
                   val directionalTrafficSignService: DirectionalTrafficSignService = Digiroad2Context.directionalTrafficSignService,
                   val vvhClient: VVHClient,
                   val massTransitStopService: MassTransitStopService,
                   val linearAssetService: LinearAssetService,
                   val manoeuvreService: ManoeuvreService = Digiroad2Context.manoeuvreService,
                   val pedestrianCrossingService: PedestrianCrossingService = Digiroad2Context.pedestrianCrossingService,
                   val userProvider: UserProvider = Digiroad2Context.userProvider,
                   val assetProvider: AssetProvider = Digiroad2Context.assetProvider) extends ScalatraServlet



with JacksonJsonSupport
with CorsSupport
with RequestHeaderAuthentication
with GZipSupport {
  val logger = LoggerFactory.getLogger(getClass)
  val MunicipalityNumber = "municipalityNumber"
  val Never = new DateTime().plusYears(1).toString("EEE, dd MMM yyyy HH:mm:ss zzzz")
  // Somewhat arbitrarily chosen limit for bounding box (Math.abs(y1 - y2) * Math.abs(x1 - x2))
  val MAX_BOUNDING_BOX = 100000000

  case object DateTimeSerializer extends CustomSerializer[DateTime](format => ( {
    null
  }, {
    case d: DateTime => JString(d.toString(DateTimePropertyFormat))
  }))

  case object SideCodeSerializer extends CustomSerializer[SideCode](format => ( {
    null
  }, {
    case s: SideCode => JInt(s.value)
  }))

  case object TrafficDirectionSerializer extends CustomSerializer[TrafficDirection](format => ( {
    case JString(direction) => TrafficDirection(direction)
  }, {
    case t: TrafficDirection => JString(t.toString)
  }))

  case object DayofWeekSerializer extends CustomSerializer[ValidityPeriodDayOfWeek](format => ( {
    case JString(dayOfWeek) => ValidityPeriodDayOfWeek(dayOfWeek)
  }, {
    case d: ValidityPeriodDayOfWeek => JString(d.toString)
  }))

  case object LinkTypeSerializer extends CustomSerializer[LinkType](format => ( {
    case JInt(linkType) => LinkType(linkType.toInt)
  }, {
    case lt: LinkType => JInt(BigInt(lt.value))
  }))

  protected implicit val jsonFormats: Formats = DefaultFormats + DateTimeSerializer + SideCodeSerializer + TrafficDirectionSerializer + LinkTypeSerializer + DayofWeekSerializer

  before() {
    contentType = formats("json") + "; charset=utf-8"
    try {
      authenticateForApi(request)(userProvider)
      if (request.isWrite && !userProvider.getCurrentUser().hasWriteAccess()) {
        halt(Unauthorized("No write permissions"))
      }
    } catch {
      case ise: IllegalStateException => halt(Unauthorized("Authentication error: " + ise.getMessage))
    }
    response.setHeader(Digiroad2Context.Digiroad2ServerOriginatedResponseHeader, "true")
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
    massTransitStopService.getByBoundingBox(user, bbox).map { stop =>
        Map("id" -> stop.id,
          "nationalId" -> stop.nationalId,
          "stopTypes" -> stop.stopTypes,
          "municipalityNumber" -> stop.municipalityCode,
          "lat" -> stop.lat,
          "lon" -> stop.lon,
          "validityDirection" -> stop.validityDirection,
          "bearing" -> stop.bearing,
          "validityPeriod" -> stop.validityPeriod,
          "floating" -> stop.floating)
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
    val massTransitStop = massTransitStopService.getMassTransitStopByNationalId(nationalId, validateMunicipalityAuthorization(nationalId)).map { stop =>
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
    massTransitStop.getOrElse(NotFound("Mass transit stop " + nationalId + " not found"))
  }

  get("/massTransitStops/floating") {
    val user = userProvider.getCurrentUser()
    val includedMunicipalities = user.isOperator() match {
      case true => None
      case false => Some(user.configuration.authorizedMunicipalities)
    }
    massTransitStopService.getFloatingAssets(includedMunicipalities)
  }

  get("/enumeratedPropertyValues/:assetTypeId") {
    assetProvider.getEnumeratedPropertyValues(params("assetTypeId").toLong)
  }

  private def massTransitStopPositionParameters(parsedBody: JValue): (Option[Double], Option[Double], Option[Long], Option[Int]) = {
    val lon = (parsedBody \ "lon").extractOpt[Double]
    val lat = (parsedBody \ "lat").extractOpt[Double]
    val roadLinkId = (parsedBody \ "mmlId").extractOpt[Long]
    val bearing = (parsedBody \ "bearing").extractOpt[Int]
    (lon, lat, roadLinkId, bearing)
  }

  put("/massTransitStops/:id") {
    def validateMunicipalityAuthorization(id: Long)(municipalityCode: Int): Unit = {
      if (!userProvider.getCurrentUser().isAuthorizedToWrite(municipalityCode))
        halt(Unauthorized("User cannot update mass transit stop " + id + ". No write access to municipality " + municipalityCode))
    }
    val (optionalLon, optionalLat, optionalMmlId, bearing) = massTransitStopPositionParameters(parsedBody)
    val properties = (parsedBody \ "properties").extractOpt[Seq[SimpleProperty]].getOrElse(Seq())
    val position = (optionalLon, optionalLat, optionalMmlId) match {
      case (Some(lon), Some(lat), Some(mmlId)) => Some(Position(lon, lat, mmlId, bearing))
      case _ => None
    }
    try {
      val id = params("id").toLong
      massTransitStopService.updateExistingById(id, position, properties.toSet, userProvider.getCurrentUser().username, validateMunicipalityAuthorization(id))
    } catch {
      case e: NoSuchElementException => BadRequest("Target roadlink not found")
    }
  }

  private def createMassTransitStop(lon: Double, lat: Double, mmlId: Long, bearing: Int, properties: Seq[SimpleProperty]): Long = {
    val roadLink = vvhClient.fetchVVHRoadlink(mmlId).getOrElse(throw new NoSuchElementException)
    massTransitStopService.create(NewMassTransitStop(lon, lat, mmlId, bearing, properties), userProvider.getCurrentUser().username, roadLink.geometry, roadLink.municipalityCode)
  }

  private def validateUserRights(mmlId: Long) = {
    val authorized: Boolean = vvhClient.fetchVVHRoadlink(mmlId).map(_.municipalityCode).exists(userProvider.getCurrentUser().isAuthorizedToWrite)
    if (!authorized) halt(Unauthorized("User not authorized"))
  }

  private def validateCreationProperties(properties: Seq[SimpleProperty]) = {
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

  post("/massTransitStops") {
    val positionParameters = massTransitStopPositionParameters(parsedBody)
    val lon = positionParameters._1.get
    val lat = positionParameters._2.get
    val mmlId = positionParameters._3.get
    val bearing = positionParameters._4.get
    val properties = (parsedBody \ "properties").extract[Seq[SimpleProperty]]
    validateUserRights(mmlId)
    validateCreationProperties(properties)
    val id = createMassTransitStop(lon, lat, mmlId, bearing, properties)
    massTransitStopService.getById(id)
  }

  private def getRoadLinksFromVVH(municipalities: Set[Int])(bbox: String): Seq[Seq[Map[String, Any]]] = {
    val boundingRectangle = constructBoundingRectangle(bbox)
    validateBoundingBox(boundingRectangle)
    val roadLinks = roadLinkService.getRoadLinksFromVVH(boundingRectangle, municipalities)
    val partitionedRoadLinks = RoadLinkPartitioner.partition(roadLinks)
    partitionedRoadLinks.map {
      _.map(roadLinkToApi)
    }
  }

  def roadLinkToApi(roadLink: RoadLink): Map[String, Any] = {
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
      "maxAddressNumberLeft" -> roadLink.attributes.get("MAXANLEFT"),
      "roadNumber" -> roadLink.attributes.get("ROADNUMBER"))
  }

  get("/roadlinks") {
    response.setHeader("Access-Control-Allow-Headers", "*")

    val user = userProvider.getCurrentUser()
    val municipalities: Set[Int] = if (user.isOperator()) Set() else user.configuration.authorizedMunicipalities

    params.get("bbox")
      .map(getRoadLinksFromVVH(municipalities))
      .getOrElse(BadRequest("Missing mandatory 'bbox' parameter"))
  }

  get("/roadlinks/:mmlId") {
    val mmlId = params("mmlId").toLong
    roadLinkService.getRoadLinkMiddlePointByMMLId(mmlId).map {
      case (id, middlePoint) => Map("id" -> id, "middlePoint" -> middlePoint)
    }.getOrElse(NotFound("Road link with MML ID " + mmlId + " not found"))
  }

  get("/roadlinks/adjacent/:id") {
    val id = params("id").toLong
    roadLinkService.getAdjacent(id).map(roadLinkToApi)
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
    val properties = parsedBody.extract[Seq[LinkProperties]]
    val user = userProvider.getCurrentUser()
    def municipalityValidation(municipalityCode: Int) = validateUserMunicipalityAccess(user)(municipalityCode)
    properties.map { prop =>
      roadLinkService.updateProperties(prop.mmlId, prop.functionalClass, prop.linkType, prop.trafficDirection, user.username, municipalityValidation).map { roadLink =>
        Map("mmlId" -> roadLink.mmlId,
          "points" -> roadLink.geometry,
          "administrativeClass" -> roadLink.administrativeClass.toString,
          "functionalClass" -> roadLink.functionalClass,
          "trafficDirection" -> roadLink.trafficDirection.toString,
          "modifiedAt" -> roadLink.modifiedAt,
          "modifiedBy" -> roadLink.modifiedBy,
          "linkType" -> roadLink.linkType.value)
      }.getOrElse(halt(NotFound("Road link with MML ID " + prop.mmlId + " not found")))
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

  get("/linearassets") {
    val user = userProvider.getCurrentUser()
    val municipalities: Set[Int] = if (user.isOperator()) Set() else user.configuration.authorizedMunicipalities
    val typeId = params.getOrElse("typeId", halt(BadRequest("Missing mandatory 'typeId' parameter"))).toInt
    params.get("bbox").map { bbox =>
      val boundingRectangle = constructBoundingRectangle(bbox)
      validateBoundingBox(boundingRectangle)
      linearAssetService.getByBoundingBox(typeId, boundingRectangle, municipalities).map { links =>
        links.map { link =>
          Map(
            "id" -> (if (link.id == 0) None else Some(link.id)),
            "mmlId" -> link.mmlId,
            "sideCode" -> link.sideCode,
            "trafficDirection" -> link.trafficDirection,
            "value" -> link.value.map(_.toJson),
            "points" -> link.geometry,
            "expired" -> link.expired,
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

  private def extractLinearAssetValue(value: JValue): Option[Value] = {
    val numericValue = value.extractOpt[Int]
    val prohibitionParameter: Option[Seq[ProhibitionValue]] = value.extractOpt[Seq[ProhibitionValue]]

    val prohibition = prohibitionParameter match {
      case Some(Nil) => None
      case None => None
      case Some(x) => Some(Prohibitions(x))
    }

    numericValue
      .map(NumericValue)
      .orElse(prohibition)
  }

  private def extractNewLinearAssets(value: JValue) = {
    val numerical = value.extractOpt[Seq[NewNumericValueAsset]].getOrElse(Nil).map(x => NewLinearAsset(x.mmlId, x.startMeasure, x.endMeasure, NumericValue(x.value), x.sideCode))
    val prohibitions = value.extractOpt[Seq[NewProhibition]].getOrElse(Nil).map(x => NewLinearAsset(x.mmlId, x.startMeasure, x.endMeasure, Prohibitions(x.value), x.sideCode))
    numerical ++ prohibitions
  }

  post("/linearassets") {
    val user = userProvider.getCurrentUser()
    val typeId = (parsedBody \ "typeId").extractOrElse[Int](halt(BadRequest("Missing mandatory 'typeId' parameter")))
    val valueOption = extractLinearAssetValue(parsedBody \ "value")
    val existingAssets = (parsedBody \ "ids").extract[Set[Long]]
    val newLinearAssets = extractNewLinearAssets(parsedBody \ "newLimits")
    val existingMmlIds = linearAssetService.getPersistedAssetsByIds(existingAssets).map(_.mmlId)
    val mmlIds = newLinearAssets.map(_.mmlId) ++ existingMmlIds
    roadLinkService.fetchVVHRoadlinks(mmlIds.toSet)
      .map(_.municipalityCode)
      .foreach(validateUserMunicipalityAccess(user))

    val updatedNumericalIds = valueOption.map(linearAssetService.update(existingAssets.toSeq, _, user.username)).getOrElse(Nil)
    val createdIds = linearAssetService.create(newLinearAssets, typeId, user.username)

    updatedNumericalIds ++ createdIds
  }

  delete("/linearassets") {
    val user = userProvider.getCurrentUser()
    val ids = (parsedBody \ "ids").extract[Set[Long]]
    val mmlIds = linearAssetService.getPersistedAssetsByIds(ids).map(_.mmlId)
    roadLinkService.fetchVVHRoadlinks(mmlIds.toSet)
      .map(_.municipalityCode)
      .foreach(validateUserMunicipalityAccess(user))

    linearAssetService.expire(ids.toSeq, user.username)
  }

  post("/linearassets/:id") {
    val user = userProvider.getCurrentUser()

    linearAssetService.split(params("id").toLong,
      (parsedBody \ "splitMeasure").extract[Double],
      extractLinearAssetValue(parsedBody \ "existingValue"),
      extractLinearAssetValue(parsedBody \ "createdValue"),
      user.username,
      validateUserMunicipalityAccess(user))
  }

  post("/linearassets/:id/separate") {
    val user = userProvider.getCurrentUser()

    linearAssetService.separate(params("id").toLong,
      extractLinearAssetValue(parsedBody \ "valueTowardsDigitization"),
      extractLinearAssetValue(parsedBody \ "valueAgainstDigitization"),
      user.username,
      validateUserMunicipalityAccess(user))
  }

  get("/speedlimits") {
    val user = userProvider.getCurrentUser()
    val municipalities: Set[Int] = if (user.isOperator()) Set() else user.configuration.authorizedMunicipalities

    params.get("bbox").map { bbox =>
      val boundingRectangle = constructBoundingRectangle(bbox)
      validateBoundingBox(boundingRectangle)
      speedLimitProvider.get(boundingRectangle, municipalities).map { linkPartition =>
        linkPartition.map { link =>
          Map(
            "id" -> (if (link.id == 0) None else Some(link.id)),
            "mmlId" -> link.mmlId,
            "sideCode" -> link.sideCode,
            "trafficDirection" -> link.trafficDirection,
            "value" -> link.value.map(_.value),
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
    speedLimitProvider.getUnknown(includedMunicipalities)
  }

  put("/speedlimits") {
    val user = userProvider.getCurrentUser()
    val optionalValue = (parsedBody \ "value").extractOpt[Int]
    val ids = (parsedBody \ "ids").extract[Seq[Long]]
    val newLimits = (parsedBody \ "newLimits").extract[Seq[NewLimit]]
    optionalValue match {
      case Some(value) =>
        val updatedIds = speedLimitProvider.updateValues(ids, value, user.username, validateUserMunicipalityAccess(user))
        val createdIds = speedLimitProvider.create(newLimits, value, user.username, validateUserMunicipalityAccess(user))
        speedLimitProvider.get(updatedIds ++ createdIds)
      case _ => BadRequest("Speed limit value not provided")
    }
  }

  post("/speedlimits/:speedLimitId/split") {
    val user = userProvider.getCurrentUser()

    speedLimitProvider.split(params("speedLimitId").toLong,
      (parsedBody \ "splitMeasure").extract[Double],
      (parsedBody \ "existingValue").extract[Int],
      (parsedBody \ "createdValue").extract[Int],
      user.username,
      validateUserMunicipalityAccess(user))
  }

  post("/speedlimits/:speedLimitId/separate") {
    val user = userProvider.getCurrentUser()

    speedLimitProvider.separate(params("speedLimitId").toLong,
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

    speedLimitProvider.create(Seq(newLimit),
      (parsedBody \ "value").extract[Int],
      user.username,
      validateUserMunicipalityAccess(user)).headOption match {
      case Some(id) => speedLimitProvider.find(id)
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
      manoeuvreService.getByBoundingBox(boundingRectangle, municipalities)
    } getOrElse {
      BadRequest("Missing mandatory 'bbox' parameter")
    }
  }

  post("/manoeuvres") {
    val user = userProvider.getCurrentUser()

    val manoeuvres = (parsedBody \ "manoeuvres").extractOrElse[Seq[NewManoeuvre]](halt(BadRequest("Malformed 'manoeuvres' parameter")))

    val manoeuvreIds = manoeuvres.map { manoeuvre =>

      val mmlIds = manoeuvres.map(_.sourceMmlId)
      roadLinkService.fetchVVHRoadlinks(mmlIds.toSet)
        .map(_.municipalityCode)
        .foreach(validateUserMunicipalityAccess(user))

      manoeuvreService.createManoeuvre(user.username, manoeuvre)
    }
    Created(manoeuvreIds)
  }


  delete("/manoeuvres") {
    val user = userProvider.getCurrentUser()

    val manoeuvreIds = (parsedBody \ "manoeuvreIds").extractOrElse[Seq[Long]](halt(BadRequest("Malformed 'manoeuvreIds' parameter")))

    manoeuvreIds.foreach { manoeuvreId =>
      val sourceRoadLinkMmlId = manoeuvreService.getSourceRoadLinkMmlIdById(manoeuvreId)
      validateUserMunicipalityAccess(user)(vvhClient.fetchVVHRoadlink(sourceRoadLinkMmlId).get.municipalityCode)
      manoeuvreService.deleteManoeuvre(user.username, manoeuvreId)
    }
  }

  put("/manoeuvres") {
    val user = userProvider.getCurrentUser()

    val manoeuvreUpdates: Map[Long, ManoeuvreUpdates] = parsedBody
      .extractOrElse[Map[String, ManoeuvreUpdates]](halt(BadRequest("Malformed body on put manoeuvres request")))
      .map { case (id, updates) => (id.toLong, updates) }

    manoeuvreUpdates.foreach { case (id, updates) =>
      val sourceRoadLinkMmlId = manoeuvreService.getSourceRoadLinkMmlIdById(id)
      validateUserMunicipalityAccess(user)(vvhClient.fetchVVHRoadlink(sourceRoadLinkMmlId).get.municipalityCode)
      manoeuvreService.updateManoeuvre(user.username, id, updates)
    }
  }

  get("/pedestrianCrossings")(getPointAssets(pedestrianCrossingService))
  get("/pedestrianCrossings/:id")(getPointAssetById(pedestrianCrossingService))
  get("/pedestrianCrossings/floating")(getFloatingPointAssets(pedestrianCrossingService))
  delete("/pedestrianCrossings/:id")(deletePointAsset(pedestrianCrossingService))
  put("/pedestrianCrossings/:id")(updatePointAsset(pedestrianCrossingService))
  post("/pedestrianCrossings")(createNewPointAsset(pedestrianCrossingService))

  get("/obstacles")(getPointAssets(obstacleService))
  get("/obstacles/:id")(getPointAssetById(obstacleService))
  get("/obstacles/floating")(getFloatingPointAssets(obstacleService))
  delete("/obstacles/:id")(deletePointAsset(obstacleService))
  put("/obstacles/:id")(updatePointAsset(obstacleService))
  post("/obstacles")(createNewPointAsset(obstacleService))

  get("/railwayCrossings")(getPointAssets(railwayCrossingService))
  get("/railwayCrossings/:id")(getPointAssetById(railwayCrossingService))
  get("/railwayCrossings/floating")(getFloatingPointAssets(railwayCrossingService))
  put("/railwayCrossings/:id")(updatePointAsset(railwayCrossingService))
  delete("/railwayCrossings/:id")(deletePointAsset(railwayCrossingService))
  post("/railwayCrossings")(createNewPointAsset(railwayCrossingService))

  get("/directionalTrafficSigns")(getPointAssets(directionalTrafficSignService))
  post("/directionalTrafficSigns")(createNewPointAsset(directionalTrafficSignService))

  private def getPointAssets(service: PointAssetOperations) = {
    val user = userProvider.getCurrentUser()

    val bbox = params.get("bbox").map(constructBoundingRectangle).getOrElse(halt(BadRequest("Bounding box was missing")))
    validateBoundingBox(bbox)
    service.getByBoundingBox(user, bbox)
  }

  private def getPointAssetById(service: PointAssetOperations) = {
    val user = userProvider.getCurrentUser()
    val asset = service.getById(params("id").toLong)
    asset match {
      case None => halt(NotFound("Asset with given id not found"))
      case Some(foundAsset) =>
        validateUserMunicipalityAccess(user)(foundAsset.municipalityCode)
        foundAsset
    }
  }

  private def getFloatingPointAssets(service: PointAssetOperations) = {
    val user = userProvider.getCurrentUser()
    val includedMunicipalities = user.isOperator() match {
      case true => None
      case false => Some(user.configuration.authorizedMunicipalities)
    }
    service.getFloatingAssets(includedMunicipalities)
  }

  private def deletePointAsset(service: PointAssetOperations): Long = {
    val user = userProvider.getCurrentUser()
    val id = params("id").toLong
    service.getPersistedAssetsByIds(Set(id)).headOption.map(_.municipalityCode).foreach(validateUserMunicipalityAccess(user))
    service.expire(id, user.username)
  }

  private def updatePointAsset(service: PointAssetOperations)(implicit m: Manifest[service.IncomingAsset]) {
    val user = userProvider.getCurrentUser()
    val id = params("id").toLong
    val updatedAsset = (parsedBody \ "asset").extract[service.IncomingAsset]
    roadLinkService.getRoadLinkFromVVH(updatedAsset.mmlId) match {
      case None => halt(NotFound(s"Roadlink with mml id ${updatedAsset.mmlId} does not exist"))
      case Some(link) => service.update(id, updatedAsset, link.geometry, link.municipalityCode, user.username)
    }
  }

  private def createNewPointAsset(service: PointAssetOperations)(implicit m: Manifest[service.IncomingAsset]) = {
    val user = userProvider.getCurrentUser()
    val asset = (parsedBody \ "asset").extract[service.IncomingAsset]
    for (link <- roadLinkService.getRoadLinkFromVVH(asset.mmlId)) {
      validateUserMunicipalityAccess(user)(link.municipalityCode)
      service.create(asset, user.username, link.geometry, link.municipalityCode)
    }
  }
}
