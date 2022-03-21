package fi.liikennevirasto.digiroad2

import java.security.InvalidParameterException
import java.time.LocalDate
import com.newrelic.api.agent.NewRelic
import fi.liikennevirasto.digiroad2.Digiroad2Context.municipalityProvider
import fi.liikennevirasto.digiroad2.asset.DateParser._
import fi.liikennevirasto.digiroad2.asset.{PointAssetValue, HeightLimit => HeightLimitInfo, WidthLimit => WidthLimitInfo, _}
import fi.liikennevirasto.digiroad2.authentication.{JWTAuthentication, UnauthenticatedException, UserNotFoundException}
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.dao.pointasset.{IncomingServicePoint, ServicePoint}
import fi.liikennevirasto.digiroad2.dao.{MapViewZoom, MunicipalityDao}
import fi.liikennevirasto.digiroad2.lane._
import fi.liikennevirasto.digiroad2.linearasset.{SpeedLimitValue, _}
import fi.liikennevirasto.digiroad2.service._
import fi.liikennevirasto.digiroad2.service.feedback.{Feedback, FeedbackApplicationService, FeedbackDataService}
import fi.liikennevirasto.digiroad2.service.lane.LaneService
import fi.liikennevirasto.digiroad2.service.linearasset.{ProhibitionService, _}
import fi.liikennevirasto.digiroad2.service.pointasset._
import fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop.{MassTransitStopException, MassTransitStopService, NewMassTransitStop, ServicePointStopService}
import fi.liikennevirasto.digiroad2.user.{User, UserProvider}
import fi.liikennevirasto.digiroad2.util._
import org.apache.commons.lang3.StringUtils.isBlank
import org.apache.http.HttpStatus
import org.joda.time.DateTime
import org.json4s.JsonAST.JValue
import org.json4s._
import org.scalatra._
import org.scalatra.json._
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.util.Try

case class ExistingLinearAsset(id: Long, linkId: Long)

case class NewNumericValueAsset(linkId: Long, startMeasure: Double, endMeasure: Double, value: Int, sideCode: Int)

case class NewTextualValueAsset(linkId: Long, startMeasure: Double, endMeasure: Double, value: String, sideCode: Int)

case class NewProhibition(linkId: Long, startMeasure: Double, endMeasure: Double, value: Prohibitions, sideCode: Int)

case class NewDynamicLinearAsset(linkId: Long, startMeasure: Double, endMeasure: Double, value: DynamicAssetValue, sideCode: Int)

class Digiroad2Api(val roadLinkService: RoadLinkService,
                   val roadAddressService: RoadAddressService,
                   val speedLimitService: SpeedLimitService,
                   val obstacleService: ObstacleService = Digiroad2Context.obstacleService,
                   val railwayCrossingService: RailwayCrossingService = Digiroad2Context.railwayCrossingService,
                   val directionalTrafficSignService: DirectionalTrafficSignService = Digiroad2Context.directionalTrafficSignService,
                   val servicePointService: ServicePointService = Digiroad2Context.servicePointService,
                   val vvhClient: VVHClient,
                   val massTransitStopService: MassTransitStopService,
                   val linearAssetService: LinearAssetService,
                   val linearMassLimitationService: LinearMassLimitationService = Digiroad2Context.linearMassLimitationService,
                   val maintenanceRoadService: MaintenanceService,
                   val pavedRoadService: PavedRoadService,
                   val roadWidthService: RoadWidthService,
                   val massTransitLaneService: MassTransitLaneService,
                   val numberOfLanesService: NumberOfLanesService,
                   val prohibitionService: ProhibitionService = Digiroad2Context.prohibitionService,
                   val hazmatTransportProhibitionService: HazmatTransportProhibitionService = Digiroad2Context.hazmatTransportProhibitionService,
                   val textValueLinearAssetService: TextValueLinearAssetService = Digiroad2Context.textValueLinearAssetService,
                   val numericValueLinearAssetService: NumericValueLinearAssetService = Digiroad2Context.numericValueLinearAssetService,
                   val manoeuvreService: ManoeuvreService = Digiroad2Context.manoeuvreService,
                   val pedestrianCrossingService: PedestrianCrossingService = Digiroad2Context.pedestrianCrossingService,
                   val userProvider: UserProvider = Digiroad2Context.userProvider,
                   val assetPropertyService: AssetPropertyService = Digiroad2Context.assetPropertyService,
                   val trafficLightService: TrafficLightService = Digiroad2Context.trafficLightService,
                   val trafficSignService: TrafficSignService = Digiroad2Context.trafficSignService,
                   val heightLimitService: HeightLimitService = Digiroad2Context.heightLimitService,
                   val widthLimitService: WidthLimitService = Digiroad2Context.widthLimitService,
                   val pointMassLimitationService: PointMassLimitationService = Digiroad2Context.pointMassLimitationService,
                   val assetService: AssetService = Digiroad2Context.assetService,
                   val verificationService: VerificationService = Digiroad2Context.verificationService,
                   val municipalityService: MunicipalityService = Digiroad2Context.municipalityService,
                   val applicationFeedback: FeedbackApplicationService = Digiroad2Context.applicationFeedback,
                   val dynamicLinearAssetService: DynamicLinearAssetService = Digiroad2Context.dynamicLinearAssetService,
                   val linearTotalWeightLimitService: LinearTotalWeightLimitService = Digiroad2Context.linearTotalWeightLimitService,
                   val linearAxleWeightLimitService: LinearAxleWeightLimitService = Digiroad2Context.linearAxleWeightLimitService,
                   val linearHeightLimitService: LinearHeightLimitService = Digiroad2Context.linearHeightLimitService,
                   val linearLengthLimitService: LinearLengthLimitService = Digiroad2Context.linearLengthLimitService,
                   val linearTrailerTruckWeightLimitService: LinearTrailerTruckWeightLimitService = Digiroad2Context.linearTrailerTruckWeightLimitService,
                   val linearWidthLimitService: LinearWidthLimitService = Digiroad2Context.linearWidthLimitService,
                   val linearBogieWeightLimitService: LinearBogieWeightLimitService = Digiroad2Context.linearBogieWeightLimitService,
                   val userNotificationService: UserNotificationService = Digiroad2Context.userNotificationService,
                   val dataFeedback: FeedbackDataService = Digiroad2Context.dataFeedback,
                   val damagedByThawService: DamagedByThawService = Digiroad2Context.damagedByThawService,
                   val roadWorkService: RoadWorkService = Digiroad2Context.roadWorkService,
                   val parkingProhibitionService: ParkingProhibitionService = Digiroad2Context.parkingProhibitionService,
                   val cyclingAndWalkingService: CyclingAndWalkingService = Digiroad2Context.cyclingAndWalkingService,
                   val laneService: LaneService = Digiroad2Context.laneService,
                   val servicePointStopService: ServicePointStopService = Digiroad2Context.servicePointStopService)

  extends ScalatraServlet
    with JacksonJsonSupport
    with CorsSupport
    with JWTAuthentication {

  val logger = LoggerFactory.getLogger(getClass)
  // Somewhat arbitrarily chosen limit for bounding box (Math.abs(y1 - y2) * Math.abs(x1 - x2))
  val MAX_BOUNDING_BOX = 100000000

  case object DateTimeSerializer extends CustomSerializer[DateTime](format => ( {
    case _ => throw new NotImplementedError("DateTime deserialization")
  }, {
    case d: DateTime => JString(d.toString(DateTimePropertyFormat))
  }))

  case object SideCodeSerializer extends CustomSerializer[SideCode](format => ( {
    null
  }, {
    case s: SideCode => JInt(s.value)
  }))

  case object WidthLimitReasonSerializer extends CustomSerializer[WidthLimitReason](format => ({
    case JInt(lg) => WidthLimitReason.apply(lg.toInt)
  }, {
    case lg: WidthLimitReason => JInt(lg.value)
  }))

  case object LinkGeomSourceSerializer extends CustomSerializer[LinkGeomSource](format => ({
    case JInt(lg) => LinkGeomSource.apply(lg.toInt)
  }, {
    case lg: LinkGeomSource => JInt(lg.value)
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

  case object AdministrativeClassSerializer extends CustomSerializer[AdministrativeClass](format => ( {
    case JString(administrativeClass) => AdministrativeClass(administrativeClass)
  }, {
    case ac: AdministrativeClass => JString(ac.toString)
  }))

  case object PointAssetSerializer extends CustomSerializer[SimplePointAssetProperty](format =>
    ({
      case jsonObj: JObject =>
        val publicId = (jsonObj \ "publicId").extract[String]
        val propertyValue: Seq[PointAssetValue] = (jsonObj \ "values").extractOpt[Seq[PropertyValue]].getOrElse((jsonObj \ "values").extractOpt[Seq[AdditionalPanel]].getOrElse(Seq()))
        val groupedId: Long =  (jsonObj \ "groupedId").extractOrElse(0)

        SimplePointAssetProperty(publicId, propertyValue, groupedId)
    },
      {
        case tv : SimplePointAssetProperty => Extraction.decompose(tv)
      }))

  case object AdditionalInfoClassSerializer extends CustomSerializer[AdditionalInformation](format => ( {
    case JString(additionalInfo) => AdditionalInformation(additionalInfo)
  }, {
    case ai: AdditionalInformation => JString(ai.toString)
  }))


  protected implicit val jsonFormats: Formats = DefaultFormats + DateTimeSerializer + LinkGeomSourceSerializer + SideCodeSerializer +
                        TrafficDirectionSerializer + LinkTypeSerializer + DayofWeekSerializer + AdministrativeClassSerializer +
                        WidthLimitReasonSerializer + AdditionalInfoClassSerializer + PointAssetSerializer

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

  case class StartupParameters(lon: Double, lat: Double, zoom: Int, startupAsseId: Int)
// TODO TR asset are hidden for now
  val StateRoadRestrictedAssetsForOperator = Set(TrHeightLimit.typeId, TrWidthLimit.typeId, TrTrailerTruckWeightLimit.typeId,
    TrAxleWeightLimit.typeId, TrBogieWeightLimit.typeId, TrWeightLimit.typeId)

  val StateRoadRestrictedAssets = Set(DamagedByThaw.typeId, MassTransitLane.typeId, EuropeanRoads.typeId, LitRoad.typeId,
    PavedRoad.typeId, TrafficSigns.typeId, CareClass.typeId, TrafficVolume.typeId)

  val minVisibleZoom = 8
  val maxZoom = 9

  get("/userNotification") {
    val user = userProvider.getCurrentUser()

    val updatedUser = user.copy(configuration = user.configuration.copy(lastNotificationDate = Some(LocalDate.now.toString)))
    userProvider.updateUserConfiguration(updatedUser)

    userNotificationService.getAllUserNotifications.map { notification =>

      Map("id" -> notification.id,
        "createdDate" -> notification.createdDate.toString(DatePropertyFormat),
        "heading" -> notification.heading,
        "content" -> notification.content,
        "unRead" -> (user.configuration.lastNotificationDate match {
          case Some(dateValue) if dateValue.compareTo(notification.createdDate.toString("yyyy-MM-dd")) >= 0 => false
          case _ => true
        })
      )
    }
  }

  def municipalityDao = new MunicipalityDao

  private def getMapViewStartParameters(mapView: Option[MapViewZoom], assetType: Int): Option[(Double, Double, Int, Int)] = mapView.map(m => (m.geometry.x, m.geometry.y, m.zoom, assetType))

  get("/startupParameters") {
    val defaultValues: (Double, Double, Int, Int) = (390000, 6900000, 2, MassTransitStopAsset.typeId)
    val user = userProvider.getCurrentUser()

    val assetTypeId = user.configuration.assetType match {
      case Some(assetType) => assetType
      case _  => MassTransitStopAsset.typeId
    }

    val userPreferences: Option[(Long, Long, Int, Int)] = (user.configuration.east, user.configuration.north, user.configuration.zoom) match {
      case (Some(east), Some(north), Some(zoom)) => Some(east, north, zoom, assetTypeId)
      case _  => None
    }
    val location = userPreferences match {
      case Some(preference) => Some(preference._1.toDouble, preference._2.toDouble, preference._3, preference._4)
      case _ =>
        if(user.isMunicipalityMaintainer() && user.configuration.authorizedMunicipalities.nonEmpty )
          getStartUpParameters(user.configuration.authorizedMunicipalities, municipalityDao.getCenterViewMunicipality, assetTypeId)
        else {
          if (user.isServiceRoadMaintainer() && user.configuration.authorizedAreas.nonEmpty)
            getStartUpParameters(user.configuration.authorizedAreas, municipalityDao.getCenterViewArea, assetTypeId)
          else if (user.isELYMaintainer() && user.configuration.authorizedMunicipalities.nonEmpty) //case ely maintainer
            getStartUpParameters(getUserElysByMunicipalities(user.configuration.authorizedMunicipalities), municipalityDao.getCenterViewEly, assetTypeId)
          else
            None
        }
    }
    val loc = location.getOrElse(defaultValues)
    StartupParameters(loc._1, loc._2, loc._3, loc._4)
  }

  private def getUserElysByMunicipalities(authorizedMunicipalities: Set[Int]): Set[Int] = {
    val userMunicipalities = authorizedMunicipalities.toSeq

    municipalityDao.getElysByMunicipalities(authorizedMunicipalities).sorted.find { ely =>
        val municipalities = municipalityProvider.getMunicipalities(Set(ely))
        municipalities.forall(userMunicipalities.contains)
    }.toSet
  }

  def splitToInts(numbers: String) : Option[Seq[Int]] = {
    val split = numbers.split(",").filterNot(_.trim.isEmpty)
    split match {
      case Array() => None
      case _ => Some(split.map(_.trim.toInt).toSeq)
    }
  }

  private def getStartUpParameters(authorizedTo: Set[Int], getter: Int => Option[MapViewZoom], assetType: Int): Option[(Double, Double, Int, Int)]  = {
    authorizedTo.headOption.map { id => getMapViewStartParameters(getter(id), assetType) } match {
      case Some(param) => param
      case None => None
    }
  }

  get("/masstransitstopgapiurl"){
    val lat =params.get("latitude").getOrElse(halt(BadRequest("Bad coordinates")))
    val lon =params.get("longitude").getOrElse(halt(BadRequest("Bad coordinates")))
    val heading =params.get("heading").getOrElse(halt(BadRequest("Bad coordinates")))
    val oldapikeyurl=s"//maps.googleapis.com/maps/api/streetview?key=AIzaSyBh5EvtzXZ1vVLLyJ4kxKhVRhNAq-_eobY&size=360x180&location=$lat,$lon&fov=110&heading=$heading&pitch=-10&sensor=false'"
    try {
      val urlsigner = new GMapUrlSigner()
      Map("gmapiurl" -> urlsigner.signRequest(lat,lon,heading))
    } catch
      {
        case e: Exception => Map("gmapiurl" -> oldapikeyurl)
      }
  }

  get("/massServiceStops") {
    val user = userProvider.getCurrentUser()
    val bbox = params.get("bbox").map(constructBoundingRectangle).getOrElse(halt(BadRequest("Bounding box was missing")))
    validateBoundingBox(bbox)

    servicePointStopService.getByBoundingBox(user, bbox).map { stop =>
      Map("id" -> stop.id,
        "name" -> extractPropertyValue("nimi_suomeksi", stop.propertyData, values => values.headOption.getOrElse("")),
        "nationalId" -> stop.nationalId,
        "stopTypes" -> stop.stopTypes,
        "municipalityNumber" -> stop.municipalityCode,
        "lat" -> stop.lat,
        "lon" -> stop.lon,
        "propertyData" -> stop.propertyData,
        "validityPeriod" -> "current",
        "floating" -> false
      )
    }
  }

  get("/massTransitStops") {
    val user = userProvider.getCurrentUser()
    val bbox = params.get("bbox").map(constructBoundingRectangle).getOrElse(halt(BadRequest("Bounding box was missing")))
    validateBoundingBox(bbox)
    massTransitStopService.getByBoundingBox(user, bbox).map { stop =>
      Map("id" -> stop.id,
        "linkId" -> stop.linkId,
        "name" -> extractPropertyValue("nimi_suomeksi", stop.propertyData, values => values.headOption.getOrElse("")),
        "nationalId" -> stop.nationalId,
        "stopTypes" -> stop.stopTypes,
        "municipalityNumber" -> stop.municipalityCode,
        "lat" -> stop.lat,
        "lon" -> stop.lon,
        "validityDirection" -> stop.validityDirection,
        "bearing" -> stop.bearing,
        "validityPeriod" -> stop.validityPeriod,
        "floating" -> stop.floating,
        "linkSource" -> stop.linkSource.value,
        "propertyData" -> stop.propertyData)
    }
  }

  delete("/massTransitStops/removal") {
    val user = userProvider.getCurrentUser()
    val assetId = (parsedBody \ "assetId").extractOpt[Int].get
    massTransitStopService.getPersistedAssetsByIds(Set(assetId)).headOption.map{ a =>
      a.linkId match {
        case 0 => validateUserMunicipalityAccessByMunicipality(user)(a.municipalityCode)
        case _ => validateUserMunicipalityAccessByLinkId(user, a.linkId, a.municipalityCode)
      }
      massTransitStopService.deleteMassTransitStopData(assetId)
    }
  }

  delete("/massServiceStops/removal") {
    val user = userProvider.getCurrentUser()
    val assetId = (parsedBody \ "assetId").extractOpt[Int].get
    servicePointStopService.getById(assetId).map{servicePointStop =>
      validateUserMunicipalityAccessByMunicipality(user)(servicePointStop.municipalityCode)
      servicePointStopService.expire(servicePointStop, user.username)
    }
  }

  get("/user/roles") {
    val user = userProvider.getCurrentUser()
    Map(
      "username" -> user.username,
      "roles" -> user.configuration.roles,
      "municipalities" -> user.configuration.authorizedMunicipalities,
      "areas" -> user.configuration.authorizedAreas)
  }

  get("/massTransitStops/:nationalId") {
    val nationalId = params("nationalId").toLong
    val massTransitStopReturned = massTransitStopService.getMassTransitStopByNationalId(nationalId)
    val massTransitStop = massTransitStopReturned._1.map { stop =>

      Map("id" -> stop.id,
        "nationalId" -> stop.nationalId,
        "stopTypes" -> stop.stopTypes,
        "lat" -> stop.lat,
        "lon" -> stop.lon,
        "validityDirection" -> stop.validityDirection,
        "bearing" -> stop.bearing,
        "validityPeriod" -> stop.validityPeriod,
        "floating" -> stop.floating,
        "propertyData" -> stop.propertyData,
        "municipalityCode" -> massTransitStopReturned._3)
    }
      massTransitStop.getOrElse(NotFound("Mass transit stop " + nationalId + " not found"))
  }

  get("/massTransitStop/:id") {
    val id = params("id").toLong
    val massTransitStopReturned = massTransitStopService.getMassTransitStopById(id)

    val massTransitStop = massTransitStopReturned._1.map { stop =>
      Map("id" -> stop.id,
        "nationalId" -> stop.nationalId,
        "stopTypes" -> stop.stopTypes,
        "lat" -> stop.lat,
        "lon" -> stop.lon,
        "validityDirection" -> stop.validityDirection,
        "bearing" -> stop.bearing,
        "validityPeriod" -> stop.validityPeriod,
        "floating" -> stop.floating,
        "propertyData" -> stop.propertyData,
        "municipalityCode" -> massTransitStopReturned._3)
    }
      massTransitStop.getOrElse(NotFound("Mass transit stop " + id + " not found"))
  }

  get("/massServiceStops/:nationalId") {
    val id = params("nationalId").toLong
    servicePointStopService.getByNationalId(id) match {
      case Some(stop) =>
        Map("id" -> stop.id,
          "nationalId" -> stop.nationalId,
          "stopTypes" -> stop.stopTypes,
          "lat" -> stop.lat,
          "lon" -> stop.lon,
          "propertyData" -> stop.propertyData,
          "municipalityCode" -> stop.municipalityCode)

      case _ =>
        Map("success" -> false)
    }
  }

  /**
  * Returns empty result as Json message, not as page not found
  */
  get("/massTransitStopsSafe/:nationalId") {
      val nationalId = params("nationalId").toLong
      val massTransitStopReturned =massTransitStopService.getMassTransitStopByNationalId(nationalId)
      massTransitStopReturned._1 match {
        case Some(stop) =>
          Map ("id" -> stop.id,
            "nationalId" -> stop.nationalId,
            "stopTypes" -> stop.stopTypes,
            "lat" -> stop.lat,
            "lon" -> stop.lon,
            "validityDirection" -> stop.validityDirection,
            "bearing" -> stop.bearing,
            "validityPeriod" -> stop.validityPeriod,
            "floating" -> stop.floating,
            "propertyData" -> stop.propertyData,
            "municipalityCode" -> massTransitStopReturned._3,
            "success" -> true)
        case None =>
          Map("success" -> false)
      }
    }

  get("/massTransitStops/passenger/:passengerId") {
    def validateMunicipalityAuthorization(passengerId: String)(municipalityCode: Int): Unit = {
      if (!userProvider.getCurrentUser().isAuthorizedToRead(municipalityCode))
        halt(Unauthorized("User not authorized for mass transit stop " + passengerId))
    }
    val passengerId = params("passengerId")
    val massTransitStopsReturned = massTransitStopService.getMassTransitStopByPassengerId(passengerId, validateMunicipalityAuthorization(passengerId))
    if (massTransitStopsReturned.nonEmpty)
      massTransitStopsReturned.map { stop =>
        Map("nationalId" -> stop.nationalId,
          "lat" -> stop.lat,
          "lon" -> stop.lon,
          "municipalityName" -> stop.municipalityName.getOrElse(""),
          "success" -> true)
      }
    else
      Map("success" -> false)
  }

  get("/massTransitStops/livi/:liviId") {
    def validateMunicipalityAuthorization(id: String)(municipalityCode: Int): Unit = {
      if (!userProvider.getCurrentUser().isAuthorizedToRead(municipalityCode))
        halt(Unauthorized("User not authorized for mass transit stop " + id))
    }
    val liviId = params("liviId")
    val massTransitStopReturned = massTransitStopService.getMassTransitStopByLiviId(liviId, validateMunicipalityAuthorization(liviId))

    val massTransitStop = massTransitStopReturned.map { stop =>

      val validityPeriod = {
        if (stop.stopTypes.contains(7) ) {
          "current"
        } else {
          stop.validityPeriod
        }
      }

      Map("id" -> stop.id,
        "nationalId" -> stop.nationalId,
        "stopTypes" -> stop.stopTypes,
        "lat" -> stop.lat,
        "lon" -> stop.lon,
        "validityDirection" -> stop.validityDirection,
        "bearing" -> stop.bearing,
        "validityPeriod" -> validityPeriod,
        "floating" -> stop.floating,
        "propertyData" -> stop.propertyData,
        "success" -> true)
    }
    massTransitStop.getOrElse(Map("success" -> false))
  }

  get("/massTransitStops/floating") {
    val user = userProvider.getCurrentUser()
    val includedMunicipalities = user.isOperator() match {
      case true => None
      case false => Some(user.configuration.authorizedMunicipalities)
    }
    massTransitStopService.getFloatingAssetsWithReason(includedMunicipalities, Some(user.isOperator()))
  }

  get("/massTransitStops/metadata") {
    massTransitStopService.getMetadata(params.get("position").map(constructPosition))
  }

  get("/enumeratedPropertyValues/:assetTypeId") {
    assetPropertyService.getEnumeratedPropertyValues(params("assetTypeId").toLong)
  }

  get("/getAssetTypeMetadata/:assetTypeId") {
    assetPropertyService.getAssetTypeMetadata(params("assetTypeId").toLong)
  }

  private def massTransitStopPositionParameters(parsedBody: JValue): (Option[Double], Option[Double], Option[Long], Option[Int]) = {
    val lon = (parsedBody \ "lon").extractOpt[Double]
    val lat = (parsedBody \ "lat").extractOpt[Double]
    val roadLinkId = (parsedBody \ "linkId").extractOpt[Long]
    val bearing = (parsedBody \ "bearing").extractOpt[Int]
    (lon, lat, roadLinkId, bearing)
  }

  put("/massTransitStops/:id") {
    def validateMunicipalityAuthorization(id: Long)(municipalityCode: Int, administrativeClass: AdministrativeClass): Unit = {
      if (!userProvider.getCurrentUser().isAuthorizedToWrite(municipalityCode, administrativeClass))
        halt(Unauthorized("User cannot update mass transit stop " + id + ". No write access to municipality " + municipalityCode))
    }
    val (optionalLon, optionalLat, optionalLinkId, bearing) = massTransitStopPositionParameters(parsedBody)
    val properties = (parsedBody \ "properties").extractOpt[Seq[SimplePointAssetProperty]].getOrElse(Seq())
    validateElyMaintainerUser(properties)
    val id = params("id").toLong

    validatePropertiesMaxSize(properties)
    val position = (optionalLon, optionalLat, optionalLinkId) match {
      case (Some(lon), Some(lat), Some(linkId)) => Some(Position(lon, lat, linkId, bearing))
      case _ => None
    }
    try {
      massTransitStopService.updateExistingById(id, position, properties.toSet, userProvider.getCurrentUser().username, validateMunicipalityAuthorization(id))
    } catch {
      case e: NoSuchElementException => BadRequest("Target roadlink not found")
      case e: RoadAddressException =>
        logger.warn("RoadAddress error: " + e.getMessage)
        PreconditionFailed("Unable to find target road link")
    }
  }

  put("/massServiceStops/:id") {
    val user = userProvider.getCurrentUser()
    val lon = (parsedBody \ "lon").extract[Double]
    val lat = (parsedBody \ "lat").extract[Double]
    val properties = (parsedBody \ "properties").extractOpt[Seq[SimplePointAssetProperty]].getOrElse(Seq())
    val id = params("id").toLong

    roadLinkService.getClosestRoadlinkFromVVH(user, Point(lon, lat)) match {
      case None =>
        halt(Conflict(s"Can not find nearby road link for given municipalities " + user.configuration.authorizedMunicipalities))
      case Some(link) =>
        validateUserAccess(user, Some(ServicePoints.typeId))(link.municipalityCode, link.administrativeClass)
        try {
          servicePointStopService.update(id, Point(lon, lat), properties, user.username, link.municipalityCode)
        } catch {
          case e: ServicePointException => halt(BadRequest( e.servicePointException.mkString(",")))
        }
    }
  }

  private def validateElyMaintainerUser(properties: Seq[SimplePointAssetProperty]) = {
    val user = userProvider.getCurrentUser()
    val propertyToValidation = properties.find {
      property => property.publicId.equals("tietojen_yllapitaja") && property.values.exists(p => p.asInstanceOf[PropertyValue].propertyValue.equals("2"))
    }
    if (propertyToValidation.isDefined && (!user.isELYMaintainer() && !user.isOperator)) {
      halt(MethodNotAllowed("User not authorized, User needs to be elyMaintainer for do that action."))
    }
  }

  private def validateCreationProperties(properties: Seq[SimplePointAssetProperty]) = {
    val mandatoryProperties: Map[String, String] = massTransitStopService.mandatoryProperties(properties)
    val nonEmptyMandatoryProperties: Seq[SimplePointAssetProperty] = properties.filter { property =>
      mandatoryProperties.contains(property.publicId) && property.values.nonEmpty
    }
    val missingProperties: Set[String] = mandatoryProperties.keySet -- nonEmptyMandatoryProperties.map(_.publicId).toSet
    if (missingProperties.nonEmpty) halt(BadRequest("Missing mandatory properties: " + missingProperties.mkString(", ")))
    val propertiesWithInvalidValues = nonEmptyMandatoryProperties.filter { property =>
      val propertyType = mandatoryProperties(property.publicId)
      propertyType match {
        case PropertyTypes.MultipleChoice =>
          property.values.forall { value => isBlank(value.asInstanceOf[PropertyValue].propertyValue) || value.asInstanceOf[PropertyValue].propertyValue.toInt == 99 }
        case _ =>
          property.values.forall { value => isBlank(value.asInstanceOf[PropertyValue].propertyValue) }
      }
    }
    if (propertiesWithInvalidValues.nonEmpty)
      halt(BadRequest("Invalid property values on: " + propertiesWithInvalidValues.map(_.publicId).mkString(", ")))
  }

  private def validatePropertiesMaxSize(properties: Seq[SimplePointAssetProperty]) = {
    val propertiesWithMaxSize: Map[String, Int] = massTransitStopService.getPropertiesWithMaxSize()
    val invalidPropertiesDueMaxSize: Seq[SimplePointAssetProperty] = properties.filter { property =>
      propertiesWithMaxSize.contains(property.publicId) && property.values.nonEmpty && property.values.forall { value => value.asInstanceOf[PropertyValue].propertyValue.length > propertiesWithMaxSize(property.publicId) }
    }
    if (invalidPropertiesDueMaxSize.nonEmpty) halt(BadRequest("Properties with Invalid Size: " + invalidPropertiesDueMaxSize.mkString(", ")))
  }

  post("/massTransitStops") {
    val positionParameters = massTransitStopPositionParameters(parsedBody)
    val lon = positionParameters._1.get
    val lat = positionParameters._2.get
    val linkId = positionParameters._3.get
    val bearing = positionParameters._4.get
    val properties = (parsedBody \ "properties").extract[Seq[SimplePointAssetProperty]]
    val roadLink = roadLinkService.getRoadLinkAndComplementaryFromVVH(linkId).getOrElse(throw new NoSuchElementException)
    validateUserAccess(userProvider.getCurrentUser())(roadLink.municipalityCode, roadLink.administrativeClass)
    validateElyMaintainerUser(properties)
    validateCreationProperties(properties)
    validatePropertiesMaxSize(properties)
    try {
      val id = massTransitStopService.create(NewMassTransitStop(lon, lat, linkId, bearing, properties), userProvider.getCurrentUser().username, roadLink)
      massTransitStopService.getNormalAndComplementaryById(id, roadLink)
    } catch {
      case e: RoadAddressException =>
        logger.warn(e.getMessage)
        PreconditionFailed("Unable to find target road link")
    }
  }

  post("/massServiceStops") {
    val user = userProvider.getCurrentUser()
    val positionParameters = massTransitStopPositionParameters(parsedBody)
    val lon = positionParameters._1.get
    val lat = positionParameters._2.get
    val properties = (parsedBody \ "properties").extract[Seq[SimplePointAssetProperty]]
    val roadLink = roadLinkService.getClosestRoadlinkFromVVH(user, Point(lon, lat)).getOrElse(throw new NoSuchElementException)

    validateUserMunicipalityAccessByMunicipality(user)(roadLink.municipalityCode)
    validatePropertiesMaxSize(properties)
    try {
      val id = servicePointStopService.create(lon, lat, properties, user.username, roadLink.municipalityCode)
      servicePointStopService.getById(id)
    } catch {
      case e: RoadAddressException =>
        logger.warn(e.getMessage)
        PreconditionFailed("Unable to find nearest road link")
    }
  }

  private def getRoadLinksFromVVH(municipalities: Set[Int], withRoadAddress: Boolean = true,withLaneInfo:Boolean=false)(bbox: String): Seq[Seq[Map[String, Any]]] = {
    LogUtils.time(logger,"TEST LOG Total time getRoadLinksFromVVH with bbox"){
      val boundingRectangle = LogUtils.time(logger, "TEST LOG Constructing boundingBox")(constructBoundingRectangle(bbox))
      validateBoundingBox(boundingRectangle)
      val roadLinkSeq = LogUtils.time(logger, "TEST LOG Get and enrich RoadLinks from VVH")(roadLinkService.getRoadLinksFromVVH(boundingRectangle, municipalities))
      val roadLinks = if(withRoadAddress) {
        val viiteInformation = LogUtils.time(logger, "TEST LOG Get Viite road address for links")(roadAddressService.roadLinkWithRoadAddress(roadLinkSeq))
        val vkmInformation = LogUtils.time(logger, "TEST LOG Get Temp road address for links")(roadAddressService.roadLinkWithRoadAddressTemp(viiteInformation.filterNot(_.attributes.contains("VIITE_ROAD_NUMBER"))))
        viiteInformation.filter(_.attributes.contains("VIITE_ROAD_NUMBER")) ++ vkmInformation
      } else roadLinkSeq
      LogUtils.time(logger, "TEST LOG Partition roadLinks")(partitionRoadLinks(roadLinks,withLaneInfo = withLaneInfo))
    }
  }

  private def getRoadlinksWithComplementaryFromVVH(municipalities: Set[Int], withRoadAddress: Boolean = true,withLaneInfo:Boolean=false)(bbox: String): Seq[Seq[Map[String, Any]]] = {
    val boundingRectangle = constructBoundingRectangle(bbox)
    validateBoundingBox(boundingRectangle)
    val roadLinkSeq = roadLinkService.getRoadLinksWithComplementaryFromVVH(boundingRectangle, municipalities)
    val roadLinks = if(withRoadAddress) {
      val viiteInformation = roadAddressService.roadLinkWithRoadAddress(roadLinkSeq)
      val vkmInformation = roadAddressService.roadLinkWithRoadAddressTemp(viiteInformation.filterNot(_.attributes.contains("VIITE_ROAD_NUMBER")))
      viiteInformation.filter(_.attributes.contains("VIITE_ROAD_NUMBER")) ++ vkmInformation
    } else roadLinkSeq
    partitionRoadLinks(roadLinks,withLaneInfo=withLaneInfo)
  }

  private def getRoadLinksHistoryFromVVH(municipalities: Set[Int],withLaneInfo:Boolean=false)(bbox: String): Seq[Seq[Map[String, Any]]] = {
    val boundingRectangle = constructBoundingRectangle(bbox)
    validateBoundingBox(boundingRectangle)
    val roadLinks = roadLinkService.getRoadLinksHistoryFromVVH(boundingRectangle, municipalities)
    partitionRoadLinks(roadLinks,withLaneInfo = withLaneInfo)
  }

  private def partitionRoadLinks(roadLinks: Seq[RoadLink],withLaneInfo: Boolean = false): Seq[Seq[Map[String, Any]]] = {
    val linkWithLane = if(withLaneInfo) lanesWithRoadlink(roadLinks) else roadLinks
    val partitionedRoadLinks = RoadLinkPartitioner.partition(linkWithLane)
    LogUtils.time(logger, "TEST LOG roadLinkToApiWithLaneInfo")(
      partitionedRoadLinks.map(r=>{roadLinkToApiWithLaneInfo(r,withLaneInfo=withLaneInfo)})
    )
  }
  
  protected def lanesWithRoadlink(linkIds: Seq[RoadLink]): Seq[RoadLink]= {
    val lanes = laneService.fetchExistingLanesByLinkIds(linkIds.map(_.linkId))
    val lanesByLink = lanes.groupBy(_.linkId)
    linkIds.map(r => r.copy(lanes=lanesByLink.getOrElse(r.linkId,Seq())))
  }

  /**
    * Enrich roadlink with lane information before turning withLaneInfo flag true
    */
  protected def roadLinkToApiWithLaneInfo(roadLinks:Seq[RoadLink],withLaneInfo: Boolean = false): Seq[Map[String,Any]] = {
      roadLinks.map(rl=>{roadLinkToApi(rl,withLaneInfo)})
  }
  /**
    * Enrich roadlink with lane information before turning withLaneInfo flag true
    */
  def roadLinkToApi(roadLink: RoadLink, withLaneInfo: Boolean = false): Map[String, Any] = {
    val laneInfo = if(withLaneInfo) roadLink.lanes else Seq()

    Map(
      "lanes" -> laneInfo.map(_.laneCode).sorted,
      "linkId" -> roadLink.linkId,
      "mmlId" -> roadLink.attributes.get("MTKID"),
      "points" -> roadLink.geometry,
      "administrativeClass" -> roadLink.administrativeClass.toString,
      "linkType" -> roadLink.linkType.value,
      "functionalClass" -> roadLink.functionalClass,
      "trafficDirection" -> roadLink.trafficDirection.toString,
      "modifiedAt" -> roadLink.modifiedAt,
      "modifiedBy" -> roadLink.modifiedBy,
      "municipalityCode" -> roadLink.attributes.get("MUNICIPALITYCODE"),
      "verticalLevel" -> roadLink.attributes.get("VERTICALLEVEL"),
      "roadNameFi" -> roadLink.attributes.get("ROADNAME_FI"),
      "roadNameSe" -> roadLink.attributes.get("ROADNAME_SE"),
      "roadNameSm" -> roadLink.attributes.get("ROADNAME_SM"),
      "minAddressNumberRight" -> roadLink.attributes.get("FROM_RIGHT"),
      "maxAddressNumberRight" -> roadLink.attributes.get("TO_RIGHT"),
      "minAddressNumberLeft" -> roadLink.attributes.get("FROM_LEFT"),
      "maxAddressNumberLeft" -> roadLink.attributes.get("TO_LEFT"),
      "roadPartNumber" -> roadLink.attributes.getOrElse("VIITE_ROAD_PART_NUMBER", roadLink.attributes.get("TEMP_ROAD_PART_NUMBER")),
      "roadNumber" -> roadLink.attributes.getOrElse("VIITE_ROAD_NUMBER", roadLink.attributes.get("TEMP_ROAD_NUMBER")),
      "constructionType" -> roadLink.constructionType.value,
      "linkSource" -> roadLink.linkSource.value,
      "track" -> roadLink.attributes.getOrElse("VIITE_TRACK", roadLink.attributes.get("TEMP_TRACK")),
      "startAddrMValue" -> roadLink.attributes.getOrElse("VIITE_START_ADDR", roadLink.attributes.get("TEMP_START_ADDR")),
      "endAddrMValue" ->  roadLink.attributes.getOrElse("VIITE_END_ADDR", roadLink.attributes.get("TEMP_END_ADDR")),
      "accessRightID" -> roadLink.attributes.get("ACCESS_RIGHT_ID"),
      "privateRoadAssociation" -> roadLink.attributes.get("PRIVATE_ROAD_ASSOCIATION"),
      "additionalInfo" -> roadLink.attributes.get("ADDITIONAL_INFO"),
      "privateRoadLastModifiedDate" -> roadLink.attributes.get("PRIVATE_ROAD_LAST_MOD_DATE"),
      "privateRoadLastModifiedUser" -> roadLink.attributes.get("PRIVATE_ROAD_LAST_MOD_USER")
    )
  }

  private def extractIntValue(roadLink: RoadLink, value: String) = {
    roadLink.attributes.get(value) match {
      case Some(x) => x.asInstanceOf[Int]
      case _ => None
    }
  }

  private def extractLongValue(roadLink: RoadLink, value: String) = {
    roadLink.attributes.get(value) match {
      case Some(x) => x.asInstanceOf[Long]
      case _ => None
    }
  }

  private def extractIntValue(attributes: Map[String, Any], value: String) = {
    attributes.get(value) match {
      case Some(x) => x.asInstanceOf[Int]
      case _ => None
    }
  }

  private def extractLongValue(attributes: Map[String, Any], value: String) = {
    attributes.get(value) match {
      case Some(x) => x.asInstanceOf[Long]
      case _ => None
    }
  }

  get("/roadlinks") {
    response.setHeader("Access-Control-Allow-Headers", "*")
    val laneInfo = Try(params("laneInfo").toBoolean).getOrElse(false)
    params.get("bbox")
      .map(getRoadLinksFromVVH(Set(),withLaneInfo = laneInfo))
      .getOrElse(BadRequest("Missing mandatory 'bbox' parameter"))
  }

  get("/roadlinks/:linkId") {
    val linkId = params("linkId").toLong
    roadLinkService.getRoadLinkMiddlePointByLinkId(linkId).map {
      case (id, middlePoint,source) => Map("success"->true, "id" -> id, "middlePoint" -> middlePoint, "source" -> source.value)
    }.getOrElse(Map("success:" ->false, "Reason"->"Link-id not found or invalid input"))
  }

  get("/roadlinks/history") {
    response.setHeader("Access-Control-Allow-Headers", "*")

    val user = userProvider.getCurrentUser()
    val municipalities: Set[Int] = if (user.isOperator()) Set() else user.configuration.authorizedMunicipalities
    val laneInfo = Try(params("laneInfo").toBoolean).getOrElse(false)
    params.get("bbox")
      .map(getRoadLinksHistoryFromVVH(municipalities,withLaneInfo = laneInfo))
      .getOrElse(BadRequest("Missing mandatory 'bbox' parameter"))
  }

  get("/roadlinks/mml/:mmlId") {
    val mmlId = params("mmlId").toLong
    roadLinkService.getRoadLinkMiddlePointByMmlId(mmlId).map {
      case (id, middlePoint) => Map("id" -> id, "middlePoint" -> middlePoint)
    }.getOrElse(NotFound("Road link with MML ID " + mmlId + " not found"))
  }

  get("/roadlinks/adjacent/:id") {
    val user = userProvider.getCurrentUser()
    val id = params("id").toLong
    val link =  roadLinkService.getAdjacent(id, true).filter(link => user.isAuthorizedToWrite(link.municipalityCode))
    roadLinkToApiWithLaneInfo(link)
  }

  get("/roadlinks/adjacents/:ids") {
    val user = userProvider.getCurrentUser()
    val ids = params("ids").split(',').map(_.toLong)
    roadLinkService.getAdjacents(ids.toSet).mapValues(_.filter(link => user.isAuthorizedToWrite(link.municipalityCode))).mapValues(_.map(rl => roadLinkToApi(rl)))
  }

  get("/roadlinks/complementaries"){
    response.setHeader("Access-Control-Allow-Headers", "*")
    val laneInfo = Try(params("laneInfo").toBoolean).getOrElse(false)
    params.get("bbox")
      .map(getRoadlinksWithComplementaryFromVVH(Set(),withLaneInfo = laneInfo))
      .getOrElse(BadRequest("Missing mandatory 'bbox' parameter"))
  }

  get("/roadLinks/incomplete") {
    val user = userProvider.getCurrentUser()
    val includedMunicipalities = user.isOperator() match {
      case true => None
      case false => Some(user.configuration.authorizedMunicipalities)
    }
    roadLinkService.getIncompleteLinks(includedMunicipalities)
  }

  get("/linearAsset/unchecked") {
    val user = userProvider.getCurrentUser()
    val includedAreas = user.isOperator() match {
      case true => None
      case false => Some(user.configuration.authorizedAreas)
    }
    val typeId = params.getOrElse("typeId", halt(BadRequest("Missing mandatory 'typeId' parameter"))).toInt
    if(typeId == MaintenanceRoadAsset.typeId)
      maintenanceRoadService.getUncheckedLinearAssets(includedAreas)
    else
      BadRequest("Linear Asset type not allowed")
  }

  put("/linkproperties") {
    val properties = parsedBody.extract[Seq[LinkProperties]]
    val user = userProvider.getCurrentUser()
    def municipalityValidation(municipalityCode: Int, administrativeClass: AdministrativeClass) = validateUserAccess(user)(municipalityCode, administrativeClass)
    properties.map { prop =>
      roadLinkService.updateLinkProperties(prop, Option(user.username), municipalityValidation).map { roadLink =>
        Map("linkId" -> roadLink.linkId,
          "points" -> roadLink.geometry,
          "administrativeClass" -> roadLink.administrativeClass.toString,
          "functionalClass" -> roadLink.functionalClass,
          "trafficDirection" -> roadLink.trafficDirection.toString,
          "modifiedAt" -> roadLink.modifiedAt,
          "modifiedBy" -> roadLink.modifiedBy,
          "linkType" -> roadLink.linkType.value)
      }.getOrElse(halt(NotFound("Road link with MML ID " + prop.linkId + " not found")))
    }
  }

  get("/assetTypeProperties/:assetTypeId") {
    try {
      val assetTypeId = params("assetTypeId").toLong
      assetPropertyService.availableProperties(assetTypeId)
    } catch {
      case e: Exception => BadRequest("Invalid asset type id: " + params("assetTypeId"))
    }
  }

  get("/assetPropertyNames/:language") {
    val lang = params("language")
    assetPropertyService.assetPropertyNames(lang)
  }
  
  object RoadAddressNotFound {
    def apply(body: Any = Unit, headers: Map[String, String] = Map.empty, reason: String = "") =
      ActionResult(HttpStatus.SC_PRECONDITION_FAILED, body, headers)
  }

  error {
    case ise: IllegalStateException => halt(InternalServerError("Illegal state: " + ise.getMessage))
    case ue: UnauthenticatedException => halt(Unauthorized("Not authenticated"))
    case unf: UserNotFoundException => halt(Forbidden(unf.username))
    case rae: RoadAddressException => halt(RoadAddressNotFound("Sovellus ei pysty tunnistamaan annetulle pysäkin sijainnille tieosoitetta. Pysäkin tallennus OTH:ssa epäonnistui"))
    case masse: MassTransitStopException => halt(NotAcceptable("Invalid Mass Transit Stop direction"))
    case valuee: AssetValueException => halt(NotAcceptable("Invalid asset value: " + valuee.getMessage))
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
    val typeId = params.getOrElse("typeId", halt(BadRequest("Missing mandatory 'typeId' parameter"))).toInt
    getLinearAssets(typeId)
  }

  private def getLinearAssets(typeId: Int) = {
    params.get("bbox").map { bbox =>
      val zoom = params.getOrElse("zoom", halt(BadRequest("Missing zoom"))).toInt
      val boundingRectangle = constructBoundingRectangle(bbox)
      val usedService = getLinearAssetService(typeId)
      zoom >= minVisibleZoom && zoom <= maxZoom match {
        case true => mapLightLinearAssets(usedService.getByZoomLevel(typeId, boundingRectangle, Some(LinkGeomSource.NormalLinkInterface)))
        case false =>
          validateBoundingBox(boundingRectangle)
          val assets = usedService.getByBoundingBox(typeId, boundingRectangle)
          if(params("withRoadAddress").toBoolean) {
            val updatedInfo = roadAddressService.linearAssetWithRoadAddress(assets)
            val frozenInfo = roadAddressService.experimentalLinearAssetWithRoadAddress(updatedInfo.map(_.filter(_.attributes.get("VIITE_ROAD_NUMBER").isEmpty)))
            mapLinearAssets(updatedInfo ++ frozenInfo)
          } else
            mapLinearAssets(assets)
      }
    } getOrElse {
      BadRequest("Missing mandatory 'bbox' parameter")
    }
  }

  get("/linearassets/complementary"){
    val typeId = params.getOrElse("typeId", halt(BadRequest("Missing mandatory 'typeId' parameter"))).toInt
    getLinearAssetsWithComplementary(typeId)
  }

  private def getLinearAssetsWithComplementary(typeId: Int) = {
    params.get("bbox").map { bbox =>
      val zoom = params.getOrElse("zoom", halt(BadRequest("Missing zoom"))).toInt
      val boundingRectangle = constructBoundingRectangle(bbox)
      val usedService = getLinearAssetService(typeId)
      zoom >= minVisibleZoom && zoom <= maxZoom match {
        case true => mapLightLinearAssets(usedService.getByZoomLevel(typeId, boundingRectangle))
        case false =>
          validateBoundingBox(boundingRectangle)
          val assets = usedService.getComplementaryByBoundingBox(typeId, boundingRectangle)
          if(params("withRoadAddress").toBoolean) {
            val updatedInfo = roadAddressService.linearAssetWithRoadAddress(assets)
            val frozenInfo = roadAddressService.experimentalLinearAssetWithRoadAddress(updatedInfo.map(_.filter(_.attributes.get("VIITE_ROAD_NUMBER").isEmpty)))
            mapLinearAssets(updatedInfo ++ frozenInfo)
          } else
            mapLinearAssets(assets)
          }
    } getOrElse {
      BadRequest("Missing mandatory 'bbox' parameter")
    }
  }

  get("/serviceRoad") {
    val user = userProvider.getCurrentUser()
    val municipalities: Set[Int] = if (user.isOperator()) Set() else user.configuration.authorizedMunicipalities
    val zoom = params.getOrElse("zoom", halt(BadRequest("Missing zoom"))).toInt
    val minVisibleZoom = 2
    val maxZoom = 8

    zoom >= minVisibleZoom && zoom < maxZoom match {
      case true =>
        mapLinearAssets(maintenanceRoadService.getByZoomLevel)
      case false =>
        getLinearAssets(MaintenanceRoadAsset.typeId)
    }
  }

  get("/serviceRoad/complementary") {
    val user = userProvider.getCurrentUser()
    val municipalities: Set[Int] = if (user.isOperator()) Set() else user.configuration.authorizedMunicipalities
    val zoom = params.getOrElse("zoom", halt(BadRequest("Missing zoom"))).toInt
    val minVisibleZoom = 2
    val maxZoom = 8

    zoom >= minVisibleZoom && zoom < maxZoom match {
      case true =>
        mapLinearAssets(maintenanceRoadService.getWithComplementaryByZoomLevel)
      case false =>
        getLinearAssetsWithComplementary(MaintenanceRoadAsset.typeId)
    }
  }

  get("/getMunicipalityInfo") {
    params.get("bbox").map { bbox =>
      val boundingRectangle = constructBoundingRectangle(bbox)
      validateBoundingBox(boundingRectangle)
      verificationService.getMunicipalityInfo(boundingRectangle)
    } getOrElse {
      BadRequest("Missing mandatory 'bbox' parameter")
    }
  }

  get("/verificationInfo") {
    val typeId = params.getOrElse("typeId", halt(BadRequest("Missing mandatory 'typeId' parameter"))).toInt
    val municipalityCode = params.getOrElse("municipality", halt(BadRequest("Missing mandatory 'municipality' parameter"))).toInt
    val user = userProvider.getCurrentUser()
    if (!user.isAuthorizedToRead(municipalityCode))
      halt(NotFound("User not authorized"))

    verificationService.getAssetVerificationInfo(typeId, municipalityCode)
  }

  private def sendFeedback(service: Feedback)(implicit m: Manifest[service.FeedbackBody]): Long= {
    val body = (parsedBody \ "body").extract[service.FeedbackBody]
    val user = userProvider.getCurrentUser()
    service.insertFeedback(user.username, body)
  }

  post("/feedbackApplication")(sendFeedback(applicationFeedback))
  post("/feedbackData")(sendFeedback(dataFeedback))

  get("/linearassets/massLimitation") {
    val user = userProvider.getCurrentUser()
    val typeId = params.getOrElse("typeId", halt(BadRequest("Missing mandatory 'typeId' parameter"))).toInt
    params.get("bbox").map { bbox =>
      val boundingRectangle = constructBoundingRectangle(bbox)
      validateBoundingBox(boundingRectangle)
      val massLinearAssets = linearMassLimitationService.getByBoundingBox(boundingRectangle, Set())
      if(params("withRoadAddress").toBoolean)
        mapMassLinearAssets(roadAddressService.massLimitationWithRoadAddress(massLinearAssets))
      else
        mapMassLinearAssets(massLinearAssets)
    } getOrElse {
      BadRequest("Missing mandatory 'bbox' parameter")
    }
  }

  get("/linearassets/massLimitation/complementary") {
    val user = userProvider.getCurrentUser()
    val municipalities: Set[Int] = if (user.isOperator()) Set() else user.configuration.authorizedMunicipalities
    val typeId = params.getOrElse("typeId", halt(BadRequest("Missing mandatory 'typeId' parameter"))).toInt
    params.get("bbox").map { bbox =>
      val boundingRectangle = constructBoundingRectangle(bbox)
      validateBoundingBox(boundingRectangle)
      val massLinearAssets = linearMassLimitationService.getByBoundingBox(boundingRectangle, municipalities)
      if(params("withRoadAddress").toBoolean)
        mapMassLinearAssets(roadAddressService.massLimitationWithRoadAddress(massLinearAssets))
      else
        mapMassLinearAssets(massLinearAssets)
    } getOrElse {
      BadRequest("Missing mandatory 'bbox' parameter")
    }
  }

  def mappingValues(x : Option[Value]) = {
    x match {
      case Some(Prohibitions(prohibitions, isSuggested)) =>
        Map(
          "isSuggested" -> isSuggested,
          "prohibitions" -> prohibitions
        )
      case _ => x.map(_.toJson)
    }
  }

  def mapLinearAssets(assets: Seq[Seq[PieceWiseLinearAsset]]): Seq[Seq[Map[String, Any]]] = {
    assets.map { links =>
      links.map { link =>
        Map(
          "id" -> (if (link.id == 0) None else Some(link.id)),
          "linkId" -> link.linkId,
          "sideCode" -> link.sideCode,
          "trafficDirection" -> link.trafficDirection,
          "value" -> mappingValues(link.value),
          "points" -> link.geometry,
          "expired" -> link.expired,
          "startMeasure" -> link.startMeasure,
          "endMeasure" -> link.endMeasure,
          "modifiedBy" -> link.modifiedBy,
          "modifiedAt" -> link.modifiedDateTime,
          "createdBy" -> link.createdBy,
          "createdAt" -> link.createdDateTime,
          "verifiedBy" -> link.verifiedBy,
          "verifiedAt" -> link.verifiedDate,
          "area" -> extractIntValue(link.attributes, "area"),
          "municipalityCode" -> extractIntValue(link.attributes, "municipality"),
          "informationSource" -> link.informationSource,
          "roadPartNumber" -> link.attributes.getOrElse("VIITE_ROAD_PART_NUMBER", link.attributes.get("TEMP_ROAD_PART_NUMBER")),
          "roadNumber" -> link.attributes.getOrElse("VIITE_ROAD_NUMBER", link.attributes.get("TEMP_ROAD_NUMBER")),
          "track" -> link.attributes.getOrElse("VIITE_TRACK",  link.attributes.get("TEMP_TRACK")),
          "startAddrMValue" -> link.attributes.getOrElse("VIITE_START_ADDR", link.attributes.get("TEMP_START_ADDR")),
          "endAddrMValue" ->  link.attributes.getOrElse("VIITE_END_ADDR", link.attributes.get("TEMP_END_ADDR")),
          "administrativeClass" -> link.administrativeClass.value,
          "constructionType" -> extractIntValue(link.attributes, "constructionType"),
          "linkType" -> (if (extractIntValue(link.attributes, "linkType") != None) LinkType(extractIntValue(link.attributes, "linkType").asInstanceOf[Int]) else UnknownLinkType.value),
          "functionalClass" -> extractIntValue(link.attributes, "functionalClass")
        )
      }
    }
  }

  def mapLanes(lanesRoot: Seq[Seq[PieceWiseLane]]): Seq[Seq[Map[String, Any]]] = {
    lanesRoot.map { lanes =>
      lanes.map { lane =>
        val laneInfo = lanes.filter(potentialLane => potentialLane.linkId == lane.linkId && potentialLane.sideCode == lane.sideCode)
        val laneCodes = laneInfo.flatMap(laneOnLink => laneService.getPropertyValue(laneOnLink, "lane_code"))
        val laneCodesSorted = laneCodes.map(_.value.toString()).sorted
        Map(
          "lanes" -> laneCodesSorted,
          "id" -> (if (lane.id == 0) None else Some(lane.id)),
          "linkId" -> lane.linkId,
          "sideCode" -> lane.sideCode,
          "properties" -> lane.laneAttributes,
          "points" -> lane.geometry,
          "trafficDirection" -> lane.attributes.get("trafficDirection"),
          "expired" -> lane.expired,
          "startMeasure" -> lane.startMeasure,
          "endMeasure" -> lane.endMeasure,
          "modifiedBy" -> lane.modifiedBy,
          "modifiedAt" -> lane.modifiedDateTime,
          "createdBy" -> lane.createdBy,
          "createdAt" -> lane.createdDateTime,
          "municipalityCode" -> extractLongValue(lane.attributes, "municipality"),
          "roadPartNumber" -> lane.attributes.getOrElse("VIITE_ROAD_PART_NUMBER", lane.attributes.get("TEMP_ROAD_PART_NUMBER")),
          "roadNumber" -> lane.attributes.getOrElse("VIITE_ROAD_NUMBER", lane.attributes.get("TEMP_ROAD_NUMBER")),
          "track" -> lane.attributes.getOrElse("VIITE_TRACK",  lane.attributes.get("TEMP_TRACK")),
          "startAddrMValue" -> lane.attributes.getOrElse("VIITE_START_ADDR", lane.attributes.get("TEMP_START_ADDR")),
          "endAddrMValue" ->  lane.attributes.getOrElse("VIITE_END_ADDR", lane.attributes.get("TEMP_END_ADDR")),
          "administrativeClass" -> lane.administrativeClass.value
        )
      }
    }
  }

  def mapLightLinearAssets(assets: Seq[Seq[LightLinearAsset]]): Seq[Seq[Map[String, Any]]] = {
    assets.map {asset =>
      asset.map { a =>
        Map(
          "value" -> a.value,
          "points" -> a.geometry,
          "expired" -> a.expired,
          "sideCode" -> a.sideCode
        )
      }
    }
  }

  def mapLightLane(lanes: Seq[Seq[LightLane]]): Seq[Seq[Map[String, Any]]] = {
    lanes.map {lane =>
      lane.map { a =>
        Map(
          "value" -> a.value,
          "expired" -> a.expired,
          "sideCode" -> a.sideCode
        )
      }
    }
  }

  def mapMassLinearAssets(readOnlyAssets: Seq[Seq[MassLimitationAsset]]): Seq[Seq[Map[String, Any]]] = {
    readOnlyAssets.map { links =>
      links.map { link =>
        Map(
          "points" -> link.geometry,
          "sideCode" -> link.sideCode,
          "values" -> link.value.map(_.toJson),
          "roadPartNumber" -> extractLongValue(link.attributes, "VIITE_ROAD_PART_NUMBER"),
          "roadNumber" -> extractLongValue(link.attributes, "VIITE_ROAD_NUMBER"),
          "track" -> extractIntValue(link.attributes, "VIITE_TRACK"),
          "startAddrMValue" -> extractLongValue(link.attributes, "VIITE_START_ADDR"),
          "endAddrMValue" ->  extractLongValue(link.attributes, "VIITE_END_ADDR"),
          "administrativeClass" -> link.administrativeClass.value
        )
      }
    }
  }

  private def extractLinearAssetValue(value: JValue): Option[Value] = {
    val numericValue = value.extractOpt[Int]
    val prohibitionParameter: Option[Prohibitions] = value.extractOpt[Prohibitions]
    val textualParameter = value.extractOpt[String]
    val dynamicValueParameter: Option[DynamicAssetValue] = value.extractOpt[DynamicAssetValue]

    val prohibition = prohibitionParameter match {
      case Some(Prohibitions(Nil, false)) => None
      case None => None
      case Some(x) => Some(Prohibitions(x.prohibitions, x.isSuggested))
    }

    val dynamicValueProps = dynamicValueParameter match {
      case Some(DynamicAssetValue(Nil)) => None
      case None => None
      case Some(x) => Some(DynamicValue(x))
    }

    numericValue
      .map(NumericValue)
      .orElse(textualParameter.map(TextualValue))
      .orElse(prohibition)
      .orElse(dynamicValueProps)
  }

  private def extractNewLinearAssets(typeId: Int, value: JValue) = {
    typeId match {
      case LinearAssetTypes.ExitNumberAssetTypeId | LinearAssetTypes.EuropeanRoadAssetTypeId =>
        value.extractOpt[Seq[NewTextualValueAsset]].getOrElse(Nil).map(x => NewLinearAsset(x.linkId, x.startMeasure, x.endMeasure, TextualValue(x.value), x.sideCode, 0, None))
      case LinearAssetTypes.ProhibitionAssetTypeId | LinearAssetTypes.HazmatTransportProhibitionAssetTypeId =>
        value.extractOpt[Seq[NewProhibition]].getOrElse(Nil).map(x => NewLinearAsset(x.linkId, x.startMeasure, x.endMeasure, x.value, x.sideCode, 0, None))
      case EuropeanRoads.typeId | ExitNumbers.typeId | TrafficVolume.typeId | NumberOfLanes.typeId | WinterSpeedLimit.typeId =>
        value.extractOpt[Seq[NewNumericValueAsset]].getOrElse(Nil).map(x => NewLinearAsset(x.linkId, x.startMeasure, x.endMeasure, NumericValue(x.value), x.sideCode, 0, None))
      case _ =>
        value.extractOpt[Seq[NewDynamicLinearAsset]].getOrElse(Nil).map(x => NewLinearAsset(x.linkId, x.startMeasure, x.endMeasure, DynamicValue(x.value), x.sideCode, 0, None))
    }
  }

  def createFakeNewLinearAssetsForValidations(existingAssets: Seq[PersistedLinearAsset], inputValues: Option[Value]): Seq[NewLinearAsset] = {
    inputValues match {
      case Some(values) => existingAssets.map(existingAsset => NewLinearAsset(existingAsset.linkId, existingAsset.startMeasure,
        existingAsset.endMeasure, values, existingAsset.sideCode, existingAsset.vvhTimeStamp, existingAsset.geomModifiedDate))
      case _ => Seq()
    }
  }

  post("/linearassets") {
    val user = userProvider.getCurrentUser()
    val typeId = (parsedBody \ "typeId").extractOrElse[Int](halt(BadRequest("Missing mandatory 'typeId' parameter")))
    val usedService = getLinearAssetService(typeId)
    val valueOption = extractLinearAssetValue(parsedBody \ "value")
    val existingAssetIds = (parsedBody \ "ids").extract[Set[Long]]
    val newLinearAssets = extractNewLinearAssets(typeId, parsedBody \ "newLimits")
    val existingAssets = usedService.getPersistedAssetsByIds(typeId, existingAssetIds)

    val assets = newLinearAssets ++ createFakeNewLinearAssetsForValidations(existingAssets, valueOption)

    validateUserRights(existingAssets, newLinearAssets, user, typeId)
    assets.foreach(usedService.validateCondition)

    val updatedNumericalIds = if (valueOption.nonEmpty) {
      try {
        valueOption.map(usedService.update(existingAssetIds.toSeq, _, user.username)).getOrElse(Nil)
      } catch {
        case e: MissingMandatoryPropertyException => halt(BadRequest("Missing Mandatory Properties: " + e.missing.mkString(",")))
        case e: IllegalArgumentException => halt(BadRequest("Property not found"))
      }
    } else {
      usedService.clearValue(existingAssetIds.toSeq, user.username)
    }

    try {
      val createdIds = usedService.create(newLinearAssets, typeId, user.username)
      updatedNumericalIds ++ createdIds
    } catch {
      case e: MissingMandatoryPropertyException => halt(BadRequest("Missing Mandatory Properties: " + e.missing.mkString(",")))
    }
  }

  put("/linearassets/verified") {
    val user = userProvider.getCurrentUser()
    val ids = (parsedBody \ "ids").extract[Set[Long]]
    val typeId = (parsedBody \ "typeId").extractOrElse[Int](halt(BadRequest("Missing mandatory 'typeId' parameter")))
    val usedService = getLinearAssetService(typeId)
    val linkIds = usedService.getPersistedAssetsByIds(typeId, ids).map(_.linkId)
    roadLinkService.fetchVVHRoadlinks(linkIds.toSet)
      .foreach(a => validateUserAccess(user, Some(typeId))(a.municipalityCode, a.administrativeClass))

    usedService.updateVerifiedInfo(ids, user.username, typeId)
  }

  get("/linearassets/unverified"){
    val user = userProvider.getCurrentUser()
    val includedMunicipalities = if (user.isOperator()) Set() else user.configuration.authorizedMunicipalities

    val typeId = params.getOrElse("typeId", halt(BadRequest("Missing mandatory 'typeId' parameter"))).toInt
    val usedService = getLinearAssetService(typeId)
    usedService.getUnverifiedLinearAssets(typeId, includedMunicipalities.toSet)
  }

  get("/linearassets/midpoint"){
    val typeId = params("typeId").toInt
    val service = getLinearAssetService(typeId)
    val (id, pointInfo, source) = service.getLinearMiddlePointAndSourceById(typeId, params("id").toLong)
    pointInfo.map {
      case middlePoint => Map("success" -> true, "id" -> id, "middlePoint" -> middlePoint, "source" -> source)}
      .getOrElse(Map("success" -> false, "Reason" -> "Id not found or invalid input"))
  }

  delete("/linearassets") {
    val user = userProvider.getCurrentUser()
    val ids = (parsedBody \ "ids").extract[Set[Long]]
    val typeId = (parsedBody \ "typeId").extractOrElse[Int](halt(BadRequest("Missing mandatory 'typeId' parameter")))
    val usedService =  getLinearAssetService(typeId)
    val existingAssets = usedService.getPersistedAssetsByIds(typeId, ids)

    validateUserRights(existingAssets, Seq(), user, typeId)
    usedService.expire(ids.toSeq, user.username)
  }

  post("/linearassets/:id") {
    val user = userProvider.getCurrentUser()
    val typeId = (parsedBody \ "typeId").extractOrElse[Int](halt(BadRequest("Missing mandatory 'typeId' parameter")))
    val usedService =  getLinearAssetService(typeId)
    usedService.split(params("id").toLong,
      (parsedBody \ "splitMeasure").extract[Double],
      extractLinearAssetValue(parsedBody \ "existingValue"),
      extractLinearAssetValue(parsedBody \ "createdValue"),
      user.username,
      validateUserAccess(user, Some(typeId)))
  }

  post("/linearassets/:id/separate") {
    val user = userProvider.getCurrentUser()
    val typeId = (parsedBody \ "typeId").extractOrElse[Int](halt(BadRequest("Missing mandatory 'typeId' parameter")))
    val usedService =  getLinearAssetService(typeId)
    usedService.separate(params("id").toLong,
      extractLinearAssetValue(parsedBody \ "valueTowardsDigitization"),
      extractLinearAssetValue(parsedBody \ "valueAgainstDigitization"),
      user.username,
      validateUserAccess(user, Some(typeId)))
  }

  get("/speedlimit/sid/") {
    val segmentID = params.get("segmentid").getOrElse(halt(BadRequest("Bad coordinates")))
    val speedLimit = speedLimitService.getSpeedLimitById(segmentID.toLong)
    speedLimit match {
      case Some(speedLimit) => {
        roadLinkService.getRoadLinkMiddlePointByLinkId(speedLimit.linkId) match {
          case Some(location) => {
            Map ("success" -> true,
              "linkId" ->  speedLimit.linkId,
              "latitude" -> location._2.y,
              "longitude"-> location._2.x
            )
          }
          case None => {
            Map("success" -> false)
          }
        }
      }
      case None => {
        Map("success" -> false)
      }
    }
  }

  get("/speedlimits") {
    val municipalities: Set[Int] = Set()

    params.get("bbox").map { bbox =>
      val boundingRectangle = constructBoundingRectangle(bbox)
      validateBoundingBox(boundingRectangle)
      val speedLimits = if(params("withRoadAddress").toBoolean) roadAddressService.speedLimitWithRoadAddress(speedLimitService.get(boundingRectangle, municipalities)) else speedLimitService.get(boundingRectangle, municipalities)
      speedLimits.map { linkPartition =>
        linkPartition.map { link =>
          Map(
            "id" -> (if (link.id == 0) None else Some(link.id)),
            "linkId" -> link.linkId,
            "sideCode" -> link.sideCode,
            "trafficDirection" -> link.trafficDirection,
            "value" -> link.value.map(x => Map("isSuggested" -> x.isSuggested, "value" -> x.value)),
            "points" -> link.geometry,
            "startMeasure" -> link.startMeasure,
            "endMeasure" -> link.endMeasure,
            "modifiedBy" -> link.modifiedBy,
            "modifiedAt" -> link.modifiedDateTime,
            "createdBy" -> link.createdBy,
            "createdAt" -> link.createdDateTime,
            "linkSource" -> link.linkSource.value,
            "roadPartNumber" -> extractLongValue(link.attributes, "VIITE_ROAD_PART_NUMBER"),
            "roadNumber" -> extractLongValue(link.attributes, "VIITE_ROAD_NUMBER"),
            "track" -> extractIntValue(link.attributes, "VIITE_TRACK"),
            "startAddrMValue" -> extractLongValue(link.attributes, "VIITE_START_ADDR"),
            "endAddrMValue" ->  extractLongValue(link.attributes, "VIITE_END_ADDR"),
            "administrativeClass" -> link.attributes.get("ROAD_ADMIN_CLASS"),
            "municipalityCode" -> extractIntValue(link.attributes, "municipalityCode")
          )
        }
      }
    } getOrElse {
      BadRequest("Missing mandatory 'bbox' parameter")
    }
  }

  get("/speedlimits/history") {
    val user = userProvider.getCurrentUser()
    val municipalities: Set[Int] = if (user.isOperator()) Set() else user.configuration.authorizedMunicipalities

    params.get("bbox").map { bbox =>
      val boundingRectangle = constructBoundingRectangle(bbox)
      validateBoundingBox(boundingRectangle)
      speedLimitService.getHistory(boundingRectangle, municipalities).map { linkPartition =>
        linkPartition.map { link =>
          Map(
            "id" -> (if (link.id == 0) None else Some(link.id)),
            "linkId" -> link.linkId,
            "sideCode" -> link.sideCode,
            "trafficDirection" -> link.trafficDirection,
            "value" -> link.value.map(x => Map("isSuggested" -> x.isSuggested, "value" -> x.value)),
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
    val adminClass = if(params.get("adminClass").map(_.toString).contains("State")) Some(State) else Some(Municipality)

    getUnknowns(adminClass, None)
  }

  get("/speedlimits/unknown/municipality") {
    val municipality = params.get("id").getOrElse(halt(BadRequest("Missing municipality id"))).toInt
    getUnknowns(Some(Municipality), Some(municipality)).map {
      unknowns =>
        Map( "id" -> municipality,
             "name" -> unknowns._1,
              unknowns._1 -> unknowns._2
        )
    }
  }

  get("/speedLimits/municipalities"){
    val user = userProvider.getCurrentUser()
    speedLimitService.getMunicipalitiesWithUnknown(Some(Municipality)).sortBy(_._2).map { municipality =>
      Map("id" -> municipality._1,
        "name" -> municipality._2)
    }
  }

  def getUnknowns(administrativeClass: Option[AdministrativeClass], municipality: Option[Int]): Map[String, Map[String, Any]] ={
    val user = userProvider.getCurrentUser()

    val municipalities = user.isOperator() match {
        case true => Set.empty[Int]
        case false => user.configuration.authorizedMunicipalities
    }

    val includedMunicipalities = municipality match {
      case Some(municipalityId) => Set(municipalityId)
      case _ => municipalities
    }
    speedLimitService.getUnknown(includedMunicipalities, administrativeClass)
  }

  get("/speedLimits/inaccurates") {
    val user = userProvider.getCurrentUser()
    val municipalityCode = user.configuration.authorizedMunicipalities
    municipalityCode.foreach(validateUserMunicipalityAccessByMunicipality(user))

    if(user.isOperator()) {
      val adminClass = params.get("adminClass").getOrElse(halt(BadRequest("Missing administrativeClass"))).toString match {
        case "State" => State
        case _ => Municipality
      }
      speedLimitService.getInaccurateRecords(adminClass = Set(adminClass))
    } else
      speedLimitService.getInaccurateRecords(municipalityCode, Set(Municipality))
  }

  get("/manoeuvre/inaccurates") {
    val user = userProvider.getCurrentUser()
    val municipalityCode = user.configuration.authorizedMunicipalities
    municipalityCode.foreach(validateUserMunicipalityAccessByMunicipality(user))

    user.isOperator() match {
      case true =>
        manoeuvreService.getInaccurateRecords()
      case false =>
        manoeuvreService.getInaccurateRecords(municipalityCode, Set(Municipality))
    }
  }

  get("/inaccurates") {
    val user = userProvider.getCurrentUser()
    val municipalityCode = user.configuration.authorizedMunicipalities
    municipalityCode.foreach(validateUserMunicipalityAccessByMunicipality(user))
    val typeId = params.getOrElse("typeId", halt(BadRequest("Missing mandatory 'typeId' parameter"))).toInt
    user.isOperator() match {
      case true =>
        getLinearAssetService(typeId).getInaccurateRecords(typeId)
      case false =>
        getLinearAssetService(typeId).getInaccurateRecords(typeId, municipalityCode, Set(Municipality))
    }
  }

  put("/speedlimits") {
    val user = userProvider.getCurrentUser()
    val optionalValue = (parsedBody \ "value").extractOpt[SpeedLimitValue]
    val ids = (parsedBody \ "ids").extract[Seq[Long]]
    val newLimits = (parsedBody \ "newLimits").extract[Seq[NewLimit]]
    optionalValue match {
      case Some(values) =>
        val updatedIds = speedLimitService.updateValues(ids, values, user.username, validateUserAccess(user, Some(SpeedLimitAsset.typeId))).toSet
        val createdIds = speedLimitService.create(newLimits, values, user.username, validateUserAccess(user, Some(SpeedLimitAsset.typeId))).toSet
        speedLimitService.getSpeedLimitAssetsByIds(updatedIds ++ createdIds)
      case _ => BadRequest("Speed limit value not provided")
    }
  }

  post("/speedlimits/:speedLimitId/split") {
    val user = userProvider.getCurrentUser()
    speedLimitService.split(params("speedLimitId").toLong,
      (parsedBody \ "splitMeasure").extract[Double],
      (parsedBody \ "existingValue").extract[Int],
      (parsedBody \ "createdValue").extract[Int],
      user.username,
      validateUserAccess(user, Some(SpeedLimitAsset.typeId)) _)
  }

  post("/speedlimits/:speedLimitId/separate") {
    val user = userProvider.getCurrentUser()
    speedLimitService.separate(params("speedLimitId").toLong,
      (parsedBody \ "valueTowardsDigitization").extract[SpeedLimitValue],
      (parsedBody \ "valueAgainstDigitization").extract[SpeedLimitValue],
      user.username,
      validateUserAccess(user, Some(SpeedLimitAsset.typeId)))
  }

  post("/speedlimits") {
    val user = userProvider.getCurrentUser()
    val newLimit = NewLimit((parsedBody \ "linkId").extract[Long],
      (parsedBody \ "startMeasure").extract[Double],
      (parsedBody \ "endMeasure").extract[Double])

    val c = (parsedBody \ "value").extract[SpeedLimitValue]

    speedLimitService.create(Seq(newLimit),
      (parsedBody \ "value").extract[SpeedLimitValue],
      user.username,
      validateUserAccess(user, Some(SpeedLimitAsset.typeId))).headOption match {
      case Some(id) => speedLimitService.getSpeedLimitById(id)
      case _ => BadRequest("Speed limit creation failed")
    }
  }

  private def validateUserAccess(user: User, typeId: Option[Int] = None)(municipality: Int, administrativeClass: AdministrativeClass) : Unit = {
    typeId match {
      case Some(assetType) => validateAdministrativeClass(assetType, user, municipality)(administrativeClass)
      case _ =>
    }
    if (!user.isAuthorizedToWrite(municipality, administrativeClass)) {
      halt(Unauthorized("User not authorized"))
    }
  }

  private def validateUserRightsForOldTrafficCodes(user: User, isOldCodeBeingUsed: Boolean, municipalityCode: Int) : Unit = {
    if (isOldCodeBeingUsed) {
      validateUserMunicipalityAccessByMunicipality(user)(municipalityCode)
    }
  }

  private def validateUserMunicipalityAccessByMunicipality(user: User)(municipality: Int) : Unit = {
    if (!user.isAuthorizedToWrite(municipality)) {
      halt(Unauthorized("User not authorized"))
    }
  }

  private def validateMunicipalityAccessByLinkId(user: User, linkId: Long): Unit = {
    val road = roadLinkService.getRoadLinkAndComplementaryFromVVH(linkId).getOrElse(halt(NotFound("Link id for asset not found")))
    validateUserMunicipalityAccessByMunicipality(user)(road.municipalityCode)
  }

  private def validateUserMunicipalityAccessByLinkId(user: User, linkId: Long, municipality: Int): Unit = {
    roadLinkService.getRoadLinkAndComplementaryFromVVH(linkId) match {
      case Some(road) =>
        if (!user.isAuthorizedToWrite(road.municipalityCode, road.administrativeClass))
          halt(Unauthorized("User not authorized"))
      case _ =>
        //when link id for asset not found
        validateUserMunicipalityAccessByMunicipality(user)(municipality)
    }
  }

  private def validateUserRights(existingAssets: Seq[PersistedLinearAsset], newLinearAssets: Seq[NewLinearAsset], user: User, typeId: Int) : Unit = {

    val roadLinks = roadLinkService.fetchVVHRoadlinksAndComplementary((existingAssets.map(_.linkId) ++ newLinearAssets.map(_.linkId)).toSet)
    if (typeId == MaintenanceRoadAsset.typeId) {

      val groupedRoadLinks = roadLinks.groupBy(_.linkId)
      val assetInfo =
        existingAssets.map { asset =>
          val roadLink = groupedRoadLinks(asset.linkId).head
          (roadLink, Measures(asset.startMeasure, asset.endMeasure), roadLink.administrativeClass)
        } ++
          newLinearAssets.map { asset =>
            val roadLink = groupedRoadLinks(asset.linkId).head
            (roadLink, Measures(asset.startMeasure, asset.endMeasure), roadLink.administrativeClass)
          }

        assetInfo.foreach { case (roadLink, measures, administrativeClass) =>
          if (!user.isAuthorizedToWriteInArea(maintenanceRoadService.getAssetArea(Some(roadLink), measures), administrativeClass))
            halt(Unauthorized("User not authorized"))
        }
    } else {
      roadLinks.foreach(a => validateUserAccess(user, Some(typeId))(a.municipalityCode, a.administrativeClass))
    }
  }

  private def validateUserRightsForLanes(linkIds: Set[Long], user: User) : Unit = {

    val roadLinks = roadLinkService.fetchVVHRoadlinksAndComplementary(linkIds)
    roadLinks.foreach(a => validateUserAccess(user)(a.municipalityCode, a.administrativeClass))
  }

  private def validateUserRightsForRoadAddress(laneRoadAddressInfo: LaneRoadAddressInfo, user: User) : Unit = {

    val roadParts = laneRoadAddressInfo.initialRoadPartNumber to laneRoadAddressInfo.endRoadPartNumber

    // Get the LinkIds from road address information from Viite
    val linkIds = roadAddressService.getAllByRoadNumberAndParts(laneRoadAddressInfo.roadNumber, roadParts, Seq(Track.apply(laneRoadAddressInfo.track)))
                                    .map(_.linkId)

    //Validate the user rights on those LinkIds
    validateUserRightsForLanes(linkIds.toSet, user)
  }

  private def validateAdministrativeClass(typeId: Int, user: User, municipality: Int)(administrativeClass: AdministrativeClass): Unit  = {
    val isNotElyExceptionAndIsStateRoad = !user.isAnElyException(municipality) && administrativeClass == State
    if(user.isOperator()){
      if (isNotElyExceptionAndIsStateRoad  && StateRoadRestrictedAssetsForOperator.contains(typeId))
        halt(BadRequest("Modification restriction for this asset on state roads"))
    }else{
      if ( isNotElyExceptionAndIsStateRoad && StateRoadRestrictedAssets.contains(typeId))
        halt(BadRequest("Modification restriction for this asset on state roads"))
    }
  }

  private def missingStartDates(lanes: Set[NewLane]): Boolean = {
    lanes.exists { lane =>
      if (LaneNumberOneDigit.isMainLane(laneService.getLaneCode(lane).toInt)) false
      else {
        val property = lane.properties.find(_.publicId == "start_date")
        if (property.isEmpty) true
        else {
          property match {
            case Some(prop) if prop.values.isEmpty || prop.values.head.value.toString.trim.isEmpty => true
            case _ => false
          }
        }
      }
    }
  }


  get("/manoeuvres") {
    params.get("bbox").map { bbox =>
      val boundingRectangle = constructBoundingRectangle(bbox)
      validateBoundingBox(boundingRectangle)
      manoeuvreService.getByBoundingBox(boundingRectangle, Set())
    } getOrElse {
      BadRequest("Missing mandatory 'bbox' parameter")
    }
  }

  post("/manoeuvres") {
    val user = userProvider.getCurrentUser()
    val manoeuvres = (parsedBody \ "manoeuvres").extractOrElse[Seq[NewManoeuvre]](halt(BadRequest("Malformed 'manoeuvres' parameter")))

    val manoeuvreIds = manoeuvres.map { manoeuvre =>

      val linkIds = manoeuvres.flatMap(_.linkIds)
      val roadLinks = roadLinkService.getRoadLinksByLinkIdsFromVVH(linkIds.toSet)

      roadLinks.foreach{rl => validateUserMunicipalityAccessByMunicipality(user)(rl.municipalityCode)}

      try {
        manoeuvreService.createManoeuvre(user.username, manoeuvre, roadLinks)
      } catch {
        case err: InvalidParameterException => halt(BadRequest(err.getMessage))
      }
    }
    Created(manoeuvreIds)
  }

  delete("/manoeuvres") {
    val user = userProvider.getCurrentUser()
    val manoeuvreIds = (parsedBody \ "manoeuvreIds").extractOrElse[Seq[Long]](halt(BadRequest("Malformed 'manoeuvreIds' parameter")))

    manoeuvreIds.map { manoeuvreId =>
      val sourceRoadLinkId = manoeuvreService.getSourceRoadLinkIdById(manoeuvreId)
      validateMunicipalityAccessByLinkId(user, sourceRoadLinkId)
      manoeuvreService.deleteManoeuvre(user.username, manoeuvreId)
    }
  }

  put("/manoeuvres") {
    val user = userProvider.getCurrentUser()
    val manoeuvreUpdates: Map[Long, ManoeuvreUpdates] = parsedBody
      .extractOrElse[Map[String, ManoeuvreUpdates]](halt(BadRequest("Malformed body on put manoeuvres request")))
      .map { case (id, updates) => (id.toLong, updates) }

    manoeuvreUpdates.map { case (id, updates) =>
      val sourceRoadLinkId = manoeuvreService.getSourceRoadLinkIdById(id)
      validateMunicipalityAccessByLinkId(user, sourceRoadLinkId)
      manoeuvreService.updateManoeuvre(user.username, id, updates, None)
    }
  }

  get("/pointassets/light") {
    val assetType = params.getOrElse("type", halt(BadRequest("Missing mandatory 'type' parameter")))
    getLightGeometry(assetType)
  }

  private def getLightGeometry(assetType: String) = {
    params.get("bbox").map { bbox =>
      val boundingRectangle = constructBoundingRectangle(bbox)
      validateBoundingBox(boundingRectangle)
      val usedService = getPointAssetService(assetType)
      usedService.getLightGeometryByBoundingBox(boundingRectangle)
    } getOrElse {
      BadRequest("Missing mandatory 'bbox' parameter")
    }
  }

  get("/pedestrianCrossings")(getPointAssets(pedestrianCrossingService))
  get("/pedestrianCrossings/:id")(getPointAssetById(pedestrianCrossingService))
  get("/pedestrianCrossings/floating")(getFloatingPointAssets(pedestrianCrossingService))
  get("/pedestrianCrossings/inaccurates")(getInaccuratesPointAssets(pedestrianCrossingService))
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
  put("/railwayCrossings/:id") {
    checkPropertySize(railwayCrossingService)
    updatePointAsset(railwayCrossingService)
  }
  delete("/railwayCrossings/:id")(deletePointAsset(railwayCrossingService))

  post("/railwayCrossings"){
    checkPropertySize(railwayCrossingService)
    createNewPointAsset(railwayCrossingService)
  }

  private def checkPropertySize(service: RailwayCrossingService): Unit = {
    val asset = (parsedBody \ "asset").extractOrElse[IncomingRailwayCrossing](halt(BadRequest("Malformed asset")))
    val railwayId = asset.propertyData.filter(_.publicId == "tasoristeystunnus").head.values.head.asInstanceOf[PropertyValue].propertyValue
    val maxSize = railwayCrossingService.getCodeMaxSize

    if(railwayId.length > maxSize)
      halt(BadRequest("Railway id property is too big"))
  }

  get("/directionalTrafficSigns")(getPointAssets(directionalTrafficSignService))
  get("/directionalTrafficSigns/:id")(getPointAssetById(directionalTrafficSignService))
  get("/directionalTrafficSigns/floating")(getFloatingPointAssets(directionalTrafficSignService))
  post("/directionalTrafficSigns")(createNewPointAsset(directionalTrafficSignService))
  put("/directionalTrafficSigns/:id")(updatePointAsset(directionalTrafficSignService))
  delete("/directionalTrafficSigns/:id")(deletePointAsset(directionalTrafficSignService))

  get("/trafficLights")(getPointAssets(trafficLightService))
  get("/trafficLights/:id")(getPointAssetById(trafficLightService))
  get("/trafficLights/floating")(getFloatingPointAssets(trafficLightService))
  post("/trafficLights")(createNewPointAsset(trafficLightService))
  put("/trafficLights/:id")(updatePointAsset(trafficLightService))
  delete("/trafficLights/:id")(deletePointAsset(trafficLightService))

  get("/trafficSigns")(getPointAssets(trafficSignService))
  get("/trafficSigns/:id")(getPointAssetById(trafficSignService))
  get("/trafficSigns/floating")(getFloatingPointAssets(trafficSignService))
  post("/trafficSigns")(createNewPointAsset(trafficSignService))
  put("/trafficSigns/:id")(updatePointAsset(trafficSignService))
  delete("/trafficSigns/:id")(deletePointAsset(trafficSignService))

  get("/trHeightLimits")(getPointAssets(heightLimitService)) // TODO TR asset hidden for now
  get("/trHeightLimits/:id")(getPointAssetById(heightLimitService))

  get("/trWidthLimits")(getPointAssets(widthLimitService))
  get("/trWidthLimits/:id")(getPointAssetById(widthLimitService))

  get("/servicePoints")(getServicePoints)
  get("/servicePoints/:id")(getServicePointsById)

  get("/groupedPointAssets")(getGroupedPointAssets)
  get("/groupedPointAssets/:id")(getGroupedPointAssetsById)

  private def getServicePoints: Set[ServicePoint] = {
    val bbox = params.get("bbox").map(constructBoundingRectangle).getOrElse(halt(BadRequest("Bounding box was missing")))
    servicePointService.get(bbox)
  }

  private def getServicePointsById: ServicePoint ={
    servicePointService.getById(params("id").toLong)
  }

  private def getGroupedPointAssets: Seq[MassLimitationPointAsset] = {
    val user = userProvider.getCurrentUser()
    val bbox = params.get("bbox").map(constructBoundingRectangle).getOrElse(halt(BadRequest("Bounding box was missing")))
    val typeIds = params.get("typeIds").getOrElse(halt(BadRequest("type Id parameters missing")))
    validateBoundingBox(bbox)
    pointMassLimitationService.getByBoundingBox(user,bbox)
  }

  private def getGroupedPointAssetsById: Seq[MassLimitationPointAsset] = {
    pointMassLimitationService.getByIds(params("id").split(",").map(_.toLong))
  }

  private def getPointAssets(service: PointAssetOperations): Seq[service.PersistedAsset] = {
    val user = userProvider.getCurrentUser()
    val bbox = params.get("bbox").map(constructBoundingRectangle).getOrElse(halt(BadRequest("Bounding box was missing")))
    validateBoundingBox(bbox)
    service.getByBoundingBox(user, bbox)
  }

  private def getPointAssetById(service: PointAssetOperations) = {
    val asset = service.getById(params("id").toLong)
    asset match {
      case None => halt(NotFound("Asset with given id not found"))
      case Some(foundAsset) => foundAsset
    }
  }

  get("/municipalities") {
      municipalityService.getMunicipalities.map { municipality =>
        Map("id" -> municipality._1,
          "name" -> municipality._2)
      }
  }

  get("/unverifiedMunicipality") {
    val municipalityCode = params("municipalityCode")
    municipalityService.getMunicipalitiesNameAndIdByCode(Set(municipalityCode.toInt))
                      .sortBy(_.name)
                      .map { municipality =>
                        Map("id" -> municipality.id,
                            "name" -> municipality.name
                          )
                      }
  }

  get("/getAssetTypes") {
    AssetTypeInfo.values.filterNot(_.typeId == UnknownAssetTypeId.typeId).toList
                        .sortBy(_.typeId)
                        .map { asset =>
                          Map("id" -> asset.typeId,
                            "name" -> { if (asset.nameFI.trim.nonEmpty) asset.nameFI else asset.label }
                          )
                        }
  }

  get("/municipalities/byUser") {
    val municipalityCode = try {
      params("municipalityCode").asInstanceOf[Option[Int]]
    } catch {
      case _: Exception => None
    }

    val user = userProvider.getCurrentUser()
    val municipalities: Set[Int] = if(municipalityCode.isDefined) Set(municipalityCode.get) else { if (user.isOperator()) Set() else user.configuration.authorizedMunicipalities }
    municipalityService.getMunicipalitiesNameAndIdByCode(municipalities).sortBy(_.name).map { municipality =>
      Map("id" -> municipality.id,
        "name" -> municipality.name)
    }
  }

  get("/municipalities/idByName") {
    val municipalityName = params("name")
    municipalityService.getMunicipalityByName(municipalityName).getOrElse(List())
  }

  get("/autoGeneratedAssets/:assetTypeId") {
    val assetTypeId = params("assetTypeId").toInt

    val user = userProvider.getCurrentUser()
    val municipalities: Set[Int] = if(user.isOperator()) Set() else user.configuration.authorizedMunicipalities

    val createdAssets = linearAssetService.getAutomaticGeneratedAssets(municipalities, assetTypeId)

    createdAssets.map { asset =>
      Map(
        "municipality" -> asset._1,
        "generatedAssets" -> asset._2
      )
    }
  }

  get("/suggested/:municipalityCode/:typeId") {
    val municipalityCode = params("municipalityCode").toInt
    val assetTypeId = params("typeId").toInt
    val result = verificationService.getSuggestedAssets(municipalityCode, assetTypeId)
    Map(
      "municipalityName" -> result.municipalityName,
      "municipalityCode" -> result.municipalityCode,
      "assetName" -> result.assetTypeName,
      "assetTypeId" -> result.assetTypeId,
      "assetIds" -> result.suggestedIds)
  }

  get("/municipalities/:municipalityCode/assetTypes/:refresh") {
    val municipalityCode = params("municipalityCode").toInt
    val refresh = params("refresh").toBoolean

    val verifiedAssetTypes = if(refresh) {
      verificationService.getRefreshedAssetTypesByMunicipality(municipalityCode, roadLinkService.getRoadLinksWithComplementaryFromVVH)
    } else
      verificationService.getAssetTypesByMunicipality(municipalityCode)

    Map(
      "municipalityName" -> verifiedAssetTypes.head.municipalityName,
      "refreshDate" -> verifiedAssetTypes.head.refreshDate.getOrElse(""),
      "properties" -> verifiedAssetTypes.map{ assetType =>
        Map("typeId" -> assetType.assetTypeCode,
          "assetName" -> assetType.assetTypeName,
          "verified_date" -> assetType.verifiedDate.map(DatePropertyFormat.print).getOrElse(""),
          "verified_by"   -> assetType.verifiedBy.getOrElse(""),
          "verified"   -> assetType.verified,
          "counter" -> assetType.counter,
          "modified_by" -> assetType.modifiedBy.getOrElse(""),
          "modified_date" -> assetType.modifiedDate.map(DatePropertyFormat.print).getOrElse(""),
          "type" -> assetType.geometryType,
          "countSuggested" -> assetType.suggestedAssetsCount)}
    )
  }

  post("/municipalities/:municipalityCode/assetVerification") {
    val user = userProvider.getCurrentUser()
    val municipalityCode = params("municipalityCode").toInt
    validateUserMunicipalityAccessByMunicipality(user)(municipalityCode)
    val assetTypeId = (parsedBody \ "typeId").extract[Set[Int]]
    verificationService.verifyAssetType(municipalityCode, assetTypeId, user.username)
  }

  delete("/municipalities/:municipalityCode/removeVerification") {
    val user = userProvider.getCurrentUser()
    val municipalityCode = params("municipalityCode").toInt
    validateUserMunicipalityAccessByMunicipality(user)(municipalityCode)
    val assetTypeIds = (parsedBody \ "typeId").extract[Set[Int]]
    verificationService.removeAssetTypeVerification(municipalityCode, assetTypeIds, user.username)
  }

  private def getFloatingPointAssets(service: PointAssetOperations) = {
    val user = userProvider.getCurrentUser()
    val includedMunicipalities = user.isOperator() match {
      case true => None
      case false => Some(user.configuration.authorizedMunicipalities)
    }
    service.getFloatingAssets(includedMunicipalities)
  }

  private def getInaccuratesPointAssets(service: PointAssetOperations): Map[String, Map[String, Any]] = {
    val user = userProvider.getCurrentUser()
    val municipalityCode = user.configuration.authorizedMunicipalities
    municipalityCode.foreach(validateUserMunicipalityAccessByMunicipality(user))
    user.isOperator() match {
      case true =>
        service.getInaccurateRecords(service.typeId)
      case false =>
        service.getInaccurateRecords(service.typeId, municipalityCode, Set(Municipality))
    }
  }

  private def deletePointAsset(service: PointAssetOperations): Long = {
    val user = userProvider.getCurrentUser()
    val id = params("id").toLong
    service.getPersistedAssetsByIds(Set(id)).headOption.foreach{ a =>
      a.linkId match {
        case 0 => validateUserMunicipalityAccessByMunicipality(user)(a.municipalityCode)
        case _ => validateUserMunicipalityAccessByLinkId(user, a.linkId, a.municipalityCode)
      }
    }
    service.expire(id, user.username)
  }

  private def updatePointAsset(service: PointAssetOperations)(implicit m: Manifest[service.IncomingAsset]) = {
    val user = userProvider.getCurrentUser()
    val id = params("id").toLong
    val updatedAsset = (parsedBody \ "asset").extract[service.IncomingAsset]
    roadLinkService.getRoadLinkAndComplementaryFromVVH(updatedAsset.linkId) match {
      case None => halt(NotFound(s"Roadlink with mml id ${updatedAsset.linkId} does not exist"))
      case Some(link) =>
        validateUserAccess(user, Some(service.typeId))(link.municipalityCode, link.administrativeClass)
        service match {
          case trafficSignService: TrafficSignService => {
            if (!trafficSignService.verifyDatesOnTemporarySigns(updatedAsset.asInstanceOf[IncomingTrafficSign])) halt(BadRequest("Incorrect parameters on date values"))
          }
          case _ =>
        }
        service.update(id, updatedAsset, link, user.username)
    }
  }

  private def createNewPointAsset(service: PointAssetOperations)(implicit m: Manifest[service.IncomingAsset]) = {
    val user = userProvider.getCurrentUser()
    val asset = (parsedBody \ "asset").extract[service.IncomingAsset]

    roadLinkService.getRoadLinkAndComplementaryFromVVH(asset.linkId).map{
      link =>
       validateUserAccess(user, Some(service.typeId))(link.municipalityCode, link.administrativeClass)
        service match {
          case trafficSignService: TrafficSignService => {
            if (!trafficSignService.verifyDatesOnTemporarySigns(asset.asInstanceOf[IncomingTrafficSign])) halt(BadRequest("Incorrect parameters on date values"))
          }
          case _ =>
        }
       service.create(asset, user.username, link)
    }
  }

  private def getLinearAssetService(typeId: Int): LinearAssetOperations = {
    typeId match {
      case MaintenanceRoadAsset.typeId => maintenanceRoadService
      case PavedRoad.typeId => pavedRoadService
      case RoadWidth.typeId => roadWidthService
      case Prohibition.typeId => prohibitionService
      case HazmatTransportProhibition.typeId => hazmatTransportProhibitionService
      case EuropeanRoads.typeId | ExitNumbers.typeId => textValueLinearAssetService
      case CareClass.typeId | CarryingCapacity.typeId | LitRoad.typeId => dynamicLinearAssetService
      case HeightLimitInfo.typeId => linearHeightLimitService
      case LengthLimit.typeId => linearLengthLimitService
      case WidthLimitInfo.typeId => linearWidthLimitService
      case TotalWeightLimit.typeId => linearTotalWeightLimitService
      case TrailerTruckWeightLimit.typeId => linearTrailerTruckWeightLimitService
      case AxleWeightLimit.typeId => linearAxleWeightLimitService
      case BogieWeightLimit.typeId => linearBogieWeightLimitService
      case MassTransitLane.typeId => massTransitLaneService
      case NumberOfLanes.typeId => numberOfLanesService
      case DamagedByThaw.typeId => damagedByThawService
      case RoadWorksAsset.typeId => roadWorkService
      case ParkingProhibition.typeId => parkingProhibitionService
      case CyclingAndWalking.typeId => cyclingAndWalkingService
      case _ => linearAssetService
    }
  }

  private def getPointAssetService(assetType: String): PointAssetOperations = {
    assetType match {
      case MassTransitStopAsset.layerName => massTransitStopService
      case DirectionalTrafficSigns.layerName => directionalTrafficSignService
      case _ => throw new UnsupportedOperationException("Asset type not supported")
    }
  }

  private def constructPosition(position: String): Point = {
    val PositionList = position.split(",").map(_.toDouble)
    Point(PositionList(0), PositionList(1))
  }

  post("/servicePoints") {
    val user = userProvider.getCurrentUser()
    val asset = (parsedBody \ "asset").extract[IncomingServicePoint]
    roadLinkService.getClosestRoadlinkFromVVH(user, Point(asset.lon, asset.lat)) match {
      case None =>
        halt(Conflict(s"Can not find nearby road link for given municipalities " + user.configuration.authorizedMunicipalities))
      case Some(link) =>
        validateUserAccess(user, Some(ServicePoints.typeId))(link.municipalityCode, link.administrativeClass)
        try {
          servicePointService.create(asset, link.municipalityCode, user.username)
        } catch {
        case e: ServicePointException => halt(BadRequest( e.servicePointException.mkString(",")))
      }
    }
  }

  put("/servicePoints/:id") {
    val id = params("id").toLong
    val updatedAsset = (parsedBody \ "asset").extract[IncomingServicePoint]
    val user = userProvider.getCurrentUser()
    roadLinkService.getClosestRoadlinkFromVVH(user, Point(updatedAsset.lon, updatedAsset.lat)) match {
      case None =>
        halt(Conflict(s"Can not find nearby road link for given municipalities " + user.configuration.authorizedMunicipalities))
      case Some(link) =>
        validateUserAccess(user, Some(ServicePoints.typeId))(link.municipalityCode, link.administrativeClass)
        try {
          servicePointService.update(id, updatedAsset, link.municipalityCode, user.username)
        } catch {
          case e: ServicePointException => halt(BadRequest( e.servicePointException.mkString(",")))
        }
    }
  }

  delete("/servicePoints/:id") {
    val user = userProvider.getCurrentUser()
    val id = params("id").toLong
    servicePointService.getPersistedAssetsByIds(Set(id)).headOption.map { a =>
      (a.lon, a.lat) match {
        case (lon, lat) =>
          roadLinkService.getClosestRoadlinkFromVVH(user, Point(lon, lat)) match {
            case None =>
              halt(Conflict(s"Can not find nearby road link for given municipalities " + user.configuration.authorizedMunicipalities))
            case Some(link) =>
              validateUserAccess(user, Some(ServicePoints.typeId))(link.municipalityCode, link.administrativeClass)
          }
        case _ => halt(Conflict(s"Can not find nearby road link for given municipalities " + user.configuration.authorizedMunicipalities))
      }
      servicePointService.expire(id, user.username)
    }
  }

  private def extractPropertyValue(key: String, properties: Seq[Property], transformation: Seq[String] => Any) = {
    val values: Seq[String] = properties.filter { property => property.publicId == key }.flatMap { property =>
      property.values.map { value =>
        value.asInstanceOf[PropertyValue].propertyValue
      }
    }
    transformation(values)
  }

  get("/userConfiguration/elys") {
    val user = userProvider.getCurrentUser()
    val elysId = municipalityDao.getElysByMunicipalities(user.configuration.authorizedMunicipalities)
    val elysIdAndName = municipalityDao.getElysIdAndNamesByCode(elysId.toSet)

    elysIdAndName.sortBy(_._2).map( ely =>
      Map(
        "id" -> ely._1,
        "name" -> ely._2
      )
    )
  }

  put("/userConfiguration/defaultLocation") {
    def getCenterView(id: Int, getCenterViewFunctionType: Int => Option[MapViewZoom], user: User): User = {
      getCenterViewFunctionType(id) match {
        case Some(centerView) =>
          val east = Option(centerView.geometry.x.toLong)
          val north = Option(centerView.geometry.y.toLong)
          val zoom = Option(centerView.zoom)

          user.copy(configuration = user.configuration.copy(east = east, north = north, zoom = zoom))
        case _ => user
      }
    }

    val user = userProvider.getCurrentUser()
    val east = (parsedBody \ "lon").extractOpt[Long]
    val north = (parsedBody \ "lat").extractOpt[Long]
    val zoom = (parsedBody \ "zoom").extractOpt[Int]
    val assetType = (parsedBody \ "assetType").extractOpt[Int]
    val municipalityId = (parsedBody \ "municipalityId").extractOpt[Int]
    val elyId = (parsedBody \ "elyId").extractOpt[Int]

    val updatedUserWithAssetType = assetType match {
      case Some(_) => user.copy(configuration = user.configuration.copy(assetType = assetType))
      case _ => user
    }

    val updatedUser = (municipalityId, elyId, east) match {
      case (Some(idMunicipality), _, _) =>
        getCenterView(idMunicipality, municipalityDao.getCenterViewMunicipality, updatedUserWithAssetType)
      case (_, Some(idEly), _) =>
        getCenterView(idEly, municipalityDao.getCenterViewEly, updatedUserWithAssetType)
      case (_, _, Some(_)) =>
        updatedUserWithAssetType.copy(configuration = updatedUserWithAssetType.configuration.copy(east = east, north = north, zoom = zoom))
      case _ =>
        updatedUserWithAssetType
    }

    userProvider.updateUserConfiguration(updatedUser)
  }

  get("/dashBoardInfo") {
    val user = userProvider.getCurrentUser()
    val userConfiguration = user.configuration

    userConfiguration.lastLoginDate match {
      case Some(lastLoginDate) if lastLoginDate.compareTo(LocalDate.now().toString) == 0  || !user.isMunicipalityMaintainer =>
        None
      case _ if userConfiguration.authorizedMunicipalities.nonEmpty || userConfiguration.municipalityNumber.nonEmpty =>
        val municipalitiesNumbers =  userConfiguration.authorizedMunicipalities ++ userConfiguration.municipalityNumber
        val verifiedAssetTypes = verificationService.getCriticalAssetTypesByMunicipality(municipalitiesNumbers.head)
        val totalSuggestedAssets = verificationService.getNumberSuggestedAssetNumber(municipalitiesNumbers)
        val modifiedAssetTypes = verificationService.getAssetsLatestModifications(municipalitiesNumbers)

        val updateUserLastLoginDate = user.copy(configuration = userConfiguration.copy(lastLoginDate = Some(LocalDate.now().toString)))
        userProvider.updateUserConfiguration(updateUserLastLoginDate)

        val verifiedMap =
          verifiedAssetTypes.map(assetType =>
            Map(
              "typeId" -> assetType.assetTypeCode,
              "assetName" -> assetType.assetTypeName,
              "verified_date" -> assetType.verifiedDate.map(DatePropertyFormat.print).getOrElse(""),
              "verified_by" -> assetType.verifiedBy.getOrElse("")
            ))

        val modifiedMap =
          modifiedAssetTypes.map(assetType =>
            Map(
              "typeId" -> assetType.assetTypeCode,
              "modified_date" -> assetType.modifiedDate.map(DatePropertyFormat.print).getOrElse(""),
              "modified_by" -> assetType.modifiedBy.getOrElse("")
            ))

        (verifiedMap, modifiedMap, totalSuggestedAssets)
      case _ => None
    }
  }

  get("/roadAssociationName") {
    roadLinkService.getAllPrivateRoadAssociationNames()
  }

  get("/fetchAssociationNames/:associationName") {
    val associationName = params("associationName").toString
    val privateRoadAssociations = roadLinkService.getPrivateRoadsByAssociationName(associationName)
    privateRoadAssociations.map { value =>
      Map("name" -> value.name,
        "linkId" -> value.linkId,
        "roadName" -> value.roadName,
        "municipality" -> value.municipality
      )
    }
  }

  get("/privateRoads/:municipalityCode") {
    val municipalityCode = params("municipalityCode").toInt
    val municipalityName = roadLinkService.municipalityService.getMunicipalityNameByCode(municipalityCode)
    val groupedResults = roadLinkService.getPrivateRoadsInfoByMunicipality(municipalityCode)

    Map(
      "municipalityName" -> municipalityName,
      "municipalityCode" -> municipalityCode,
      "results" ->
        groupedResults.filter(row => row.privateRoadName.isDefined || row.associationId.isDefined).map{result =>
          Map(
            "privateRoadName" -> result.privateRoadName.getOrElse(""),
            "associationId" -> result.associationId.getOrElse(""),
            "additionalInfo" -> result.additionalInfo.getOrElse(99),
            "lastModifiedDate" -> result.lastModifiedDate.getOrElse("")
          )
        }
    )
  }

  get("/userStartLocation") {
    val user = userProvider.getCurrentUser()
    params.get("position") match {
      case Some(position) =>
        val coordinates = constructPosition(position)

        if (user.isELYMaintainer()) {
          municipalityService.getElyByCoordinates(coordinates).map {
            case (elyId, elyName) =>
              Map("elyID" -> elyId,
                "elyName" -> elyName)
          }
        } else {
          val municipalityInfo = municipalityService.getMunicipalityByCoordinates(coordinates)
          if (municipalityInfo.isEmpty) {
            Map()
          } else {
            municipalityInfo.map {
              municipalityInfo =>
                Map("municipalityId" -> municipalityInfo.id,
                  "municipalityName" -> municipalityInfo.name,
                  "municipalityElyNumber" -> municipalityInfo.ely)
            }
          }
        }
      case _ => Map()
    }
  }

  delete("/unknownSpeedLimit/delete") {
    val unknownSpeedLimitIds = parsedBody.extractOpt[Set[Long]]

    unknownSpeedLimitIds match {
      case Some(value) => speedLimitService.hideUnknownSpeedLimits(value)
      case None => false
    }
  }

  get("/lanes") {
    params.get("bbox").map { bbox =>
      val zoom = params.getOrElse("zoom", halt(BadRequest("Missing zoom"))).toInt
      val boundingRectangle = constructBoundingRectangle(bbox)
      val usedService = laneService

      if (zoom >= minVisibleZoom && zoom <= maxZoom) {
        mapLightLane(usedService.getByZoomLevel( Some(LinkGeomSource.NormalLinkInterface)))
      } else {
        validateBoundingBox(boundingRectangle)
        val (assets, roadLinksWithoutLanes) = usedService.getByBoundingBox(boundingRectangle, withWalkingCycling = params.getAsOrElse[Boolean]("withWalkingCycling", false))
        mapLanes(assets) ++ roadLinkToApiWithLaneInfo(roadLinksWithoutLanes,withLaneInfo = false)
      }

    } getOrElse {
      BadRequest("Missing mandatory 'bbox' parameter")
    }
  }

  get("/lanes/viewOnlyLanes") {
    params.get("bbox").map { bbox =>
      val boundingRectangle = constructBoundingRectangle(bbox)
      validateBoundingBox(boundingRectangle)
      val viewOnlyLanes = laneService.getViewOnlyByBoundingBox(boundingRectangle, withWalkingCycling = params.getAsOrElse[Boolean]("withWalkingCycling", false))
      viewOnlyLanes.map { lane =>
        Map (
          "linkId" -> lane.linkId,
          "sideCode" -> lane.sideCode,
          "trafficDirection" -> lane.trafficDirection,
          "startMeasure" -> lane.startMeasure,
          "endMeasure" -> lane.endMeasure,
          "points" -> lane.geometry,
          "lanes" -> lane.lanes,
          "isViewOnly" -> true
        )
      }
    } getOrElse {
      BadRequest("Missing mandatory 'bbox' parameter")
    }
  }

  post("/lanes") {
    val user = userProvider.getCurrentUser()

    val linkIds = (parsedBody \ "linkIds")extractOrElse[Set[Long]](halt(BadRequest("Malformed 'linkIds' parameter")))
    val sideCode = (parsedBody \ "sideCode")extractOrElse[Int](halt(BadRequest("Malformed 'sideCode' parameter")))
    val incomingLanes = (parsedBody \ "lanes").extractOrElse[Seq[NewLane]](halt(BadRequest("Malformed 'lanes' parameter")))
    val sideCodesForLinks = (parsedBody \ "sideCodesForLinks").extractOrElse[Seq[SideCodesForLinkIds]](halt(BadRequest("Malformed 'sideCodesForLinks' parameter")))
    if (missingStartDates(incomingLanes.toSet)) halt(BadRequest("Missing required 'start_date' on one or more lanes"))

    validateUserRightsForLanes(linkIds, user)
    laneService.processNewLanes(incomingLanes.toSet, linkIds, sideCode, user.username, sideCodesForLinks)
  }

  post("/lanesByRoadAddress") {
    val user = userProvider.getCurrentUser()

    val sideCode = (parsedBody \ "sideCode")extractOrElse[Int](halt(BadRequest("Malformed 'sideCode' parameter")))
    val laneRoadAddressInfo = (parsedBody \ "laneRoadAddressInfo").extractOrElse[LaneRoadAddressInfo](halt(BadRequest("Malformed 'laneRoadAddressInfo' parameter")))
    val incomingLanes = (parsedBody \ "lanes").extractOrElse[Set[NewLane]](halt(BadRequest("Malformed 'lanes' parameter")))
    if (missingStartDates(incomingLanes)) halt(BadRequest("Missing required 'start_date' on one or more lanes"))

    validateUserRightsForRoadAddress(laneRoadAddressInfo, user)
    try {
      laneService.processLanesByRoadAddress(incomingLanes, laneRoadAddressInfo, user.username)
    } catch {
      case e: InvalidParameterException => halt(BadRequest(e.getMessage))
    }
  }

  get("/lane/:linkId/:sideCode") {
    val linkId = params("linkId").toLong
    val sideCode = params("sideCode").toInt

    laneService.fetchExistingLanesByLinksIdAndSideCode(linkId, sideCode).map { lane =>
      Map(
        "id" -> lane.id,
        "linkId" -> lane.linkId,
        "sideCode" -> lane.sideCode,
        "startMeasure" -> lane.startMeasure,
        "endMeasure" -> lane.endMeasure,
        "createdBy" -> lane.createdBy,
        "createdAt" -> lane.createdDateTime,
        "modifiedBy" -> lane.modifiedBy,
        "modifiedAt" -> lane.modifiedDateTime,
        "points" -> lane.geometry,
        "trafficDirection" -> lane.attributes.get("trafficDirection"),
        "municipalityCode" -> extractLongValue(lane.attributes, "municipality"),
        "properties" -> lane.laneAttributes
      )
    }
  }
}
