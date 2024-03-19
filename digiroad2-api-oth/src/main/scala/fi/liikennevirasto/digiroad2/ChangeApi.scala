package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.Digiroad2Context._
import fi.liikennevirasto.digiroad2.asset.DateParser._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.lane.{ChangedSegment, LaneChange, LaneChangeType, PersistedLane}
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.service.ChangedRoadlink
import fi.liikennevirasto.digiroad2.service.linearasset.ChangedLinearAsset
import fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop.PersistedMassTransitStop
import fi.liikennevirasto.digiroad2.vallu.ValluStoreStopChangeMessage._
import fi.liikennevirasto.digiroad2.vallu.ValluTransformer.{describeEquipments, describeReachability, transformToISODate}
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.swagger.{Swagger, SwaggerSupport}
import org.scalatra.{BadRequest, ScalatraServlet}

import scala.collection.mutable
import scala.util.Try

class ChangeApi(val swagger: Swagger) extends ScalatraServlet with JacksonJsonSupport with SwaggerSupport {
  protected val applicationDescription = "Change API "
  protected implicit val jsonFormats: Formats = DefaultFormats
  val apiId = "change-api"

  before() {
    contentType = formats("json")
  }

  after() {
    response.setHeader("Access-Control-Allow-Origin", request.getHeader("Origin"));
    response.setHeader("Access-Control-Allow-Methods",  "OPTIONS,POST,GET");
    response.setHeader("Access-Control-Allow-Headers", request.getHeader("Access-Control-Request-Headers"));
  }

  //Description of Api entry point to get assets changes by asset type and between two dates
  val getChangesOfAssetsByType =
    (apiOperation[Long]("getChangesOfAssetsByType")
      .parameters(
        queryParam[String]("since").description("Initial date of the interval between two dates to obtain modifications for a particular asset."),
        queryParam[String]("until").description("The end date of the interval between two dates to obtain modifications for an asset."),
        queryParam[String]("withAdjust").description("With the field withAdjust, we allow or not the presence of records modified by generated_in_update and not modified yet on the response. The value is False by default").optional,
        queryParam[String]("withGeometry").description("With the field withGeometry, we allow or not to print the geometry values for lane_information. The value is False by default").optional,
        pathParam[String]("assetType").description("Asset type name to get the changes")
      )
      tags "Change API"
      summary "List all changes per assets type between two specific dates."
      authorizations "Contact your service provider for more information"
      description "Example URL: api/changes/bogie_weight_limits?since=2018-04-12T04:00Z&until=2018-04-16T15:00Z"
      )

  //Api entry point to get Mass Transit Stops printed on Vally XML file between two dates
  val getMassTransitStopsPrintedAtValluXML =
    (apiOperation[Long]("getMassTransitStopsPrintedAtValluXML")
      .parameters(
        queryParam[String]("since").description("Initial date of the interval between two dates to obtain modifications for a particular asset."),
        queryParam[String]("until").description("The end date of the interval between two dates to obtain modifications for an asset.")
      )
      tags "Change API"
      summary "List all Mass Transit Stops printed on Vally XML file between two specific dates."
      authorizations "Contact your service provider for more information"
      description "Example URL: api/changes/mass_transit_stops?since=2019-03-15T09:19:27.424Z&until=2019-03-22T23:55:27.429Z"
      )
 
  get("/:assetType", operation(getChangesOfAssetsByType)) {
    contentType = formats("json")
    ApiUtils.avoidRestrictions(apiId, request, params) { params =>
     val since = Try(DateTime.parse(params.get("since").getOrElse(throw DigiroadApiError(HttpStatusCodeError.BAD_REQUEST, "Missing mandatory 'since' parameter"))))
                 .getOrElse(throw DigiroadApiError(HttpStatusCodeError.BAD_REQUEST, "Cannot convert to date"))
     val until = Try(DateTime.parse(params.get("until").getOrElse(throw DigiroadApiError(HttpStatusCodeError.BAD_REQUEST, "Missing mandatory 'until' parameter"))))
                 .getOrElse(throw DigiroadApiError(HttpStatusCodeError.BAD_REQUEST, "Cannot convert to date"))
     val token = params.get("token").map(_.toString)

      val withAdjust = params.get("withAdjust") match {
        case Some(value) => true
        case _ => false
      }

      val withGeometry = params.get("withGeometry") match {
        case Some(value) => true
        case _ => false
      }

      params("assetType") match {
        case "speed_limits" => speedLimitsToGeoJson(since, speedLimitService.getChanged(SpeedLimitAsset.typeId, since, until, withAdjust, token))
        case "total_weight_limits" => sevenRestrictionToGeoJson(since, dynamicLinearAssetService.getChanged(TotalWeightLimit.typeId, since, until, withAdjust, token))
        case "trailer_truck_weight_limits" => sevenRestrictionToGeoJson(since, dynamicLinearAssetService.getChanged(TrailerTruckWeightLimit.typeId, since, until, withAdjust, token))
        case "axle_weight_limits" => sevenRestrictionToGeoJson(since, dynamicLinearAssetService.getChanged(AxleWeightLimit.typeId, since, until, withAdjust, token))
        case "bogie_weight_limits" => bogieWeightLimitsToGeoJson(since, dynamicLinearAssetService.getChanged(BogieWeightLimit.typeId, since, until, withAdjust, token))
        case "height_limits" => sevenRestrictionToGeoJson(since, dynamicLinearAssetService.getChanged(HeightLimit.typeId, since, until, withAdjust, token))
        case "length_limits" => sevenRestrictionToGeoJson(since, dynamicLinearAssetService.getChanged(LengthLimit.typeId, since, until, withAdjust, token))
        case "width_limits" => sevenRestrictionToGeoJson(since, dynamicLinearAssetService.getChanged(WidthLimit.typeId, since, until, withAdjust, token))
        case "road_names" => roadLinksToGeoJson(roadLinkService.getChanged(since, until))
        case "vehicle_prohibitions" => prohibitionsToGeoJson(since, prohibitionService.getChanged(Prohibition.typeId, since, until, withAdjust, token))
        case "pedestrian_crossing" => pointAssetsToGeoJson(since, pedestrianCrossingService.getChanged(since, until, token), pointAssetGenericProperties)
        case "obstacles" => pointAssetsToGeoJson(since, obstacleService.getChanged(since, until, token), pointAssetGenericProperties)
        case "warning_signs_group" => pointAssetsToGeoJson(since, trafficSignService.getChangedByType(trafficSignService.getTrafficSignTypeByGroup(TrafficSignTypeGroup.GeneralWarningSigns), since, until, token), pointAssetWarningSignsGroupProperties)
        case "stop_sign" => pointAssetsToGeoJson(since, trafficSignService.getChangedByType(Set(Stop.OTHvalue), since, until, token), pointAssetStopSignProperties)
        case "lane_information" => laneChangesToGeoJson(laneService.getChanged(since, until, withAdjust, token), withGeometry)
        case _ => throw DigiroadApiError(HttpStatusCodeError.BAD_REQUEST,"Invalid asset type")
      }
    }
  }

  get("/mass_transit_stops", operation(getMassTransitStopsPrintedAtValluXML)) {
    contentType = formats("json")
    ApiUtils.avoidRestrictions(apiId + "_mass_transit_stops", request, params) { params =>
     val since = Try(DateTime.parse(params.get("since").getOrElse(throw DigiroadApiError(HttpStatusCodeError.BAD_REQUEST, "Missing mandatory 'since' parameter"))))
       .getOrElse(throw DigiroadApiError(HttpStatusCodeError.BAD_REQUEST, "Cannot convert to date"))
     val until = Try(DateTime.parse(params.get("until").getOrElse(throw DigiroadApiError(HttpStatusCodeError.BAD_REQUEST, "Missing mandatory 'until' parameter"))))
       .getOrElse(throw DigiroadApiError(HttpStatusCodeError.BAD_REQUEST, "Cannot convert to date"))
      val token = params.get("token").map(_.toString)

      massTransitStopsToGeoJson(since, massTransitStopService.getPublishedOnXml(since, until, token))
    }
  }

  private def speedLimitsToGeoJson(since: DateTime, changedAssets: Seq[ChangedLinearAsset]) = {

    val speedLimitsWithValue = changedAssets.map { changedAsset =>
      val speedLimitValue = speedLimitService.getSpeedLimitValue(changedAsset.linearAsset.value)
      val speedLimitWithValue = changedAsset.linearAsset.copy(value = speedLimitValue)
      ChangedLinearAsset(speedLimitWithValue, changedAsset.link)
      }

    Map(
      "type" -> "FeatureCollection",
      "features" ->
        speedLimitsWithValue.filterNot(x => x.linearAsset.value.nonEmpty && speedLimitService.getSpeedLimitValue(x.linearAsset.value).get.isSuggested).map { case ChangedLinearAsset(speedLimit, link) =>
          Map(
            "type" -> "Feature",
            "id" -> speedLimit.id,
            "geometry" -> Map(
              "type" -> "LineString",
              "coordinates" -> speedLimit.geometry.map(p => Seq(p.x, p.y, p.z))
            ),
            "properties" ->
              Map(
                "value" -> speedLimitService.getSpeedLimitValue(speedLimit.value).get.value,
                "link" -> Map(
                  "type" -> "Feature",
                  "id" -> link.linkId,
                  "geometry" -> Map(
                    "type" -> "LineString",
                    "coordinates" -> link.geometry.map(p => Seq(p.x, p.y, p.z))
                  ),
                  "properties" -> Map(
                    "functionalClass" -> link.functionalClass,
                    "type" -> link.linkType.value,
                    "length" -> link.length
                  )
                ),
                "sideCode" -> (link.trafficDirection match {
                  case TrafficDirection.AgainstDigitizing =>
                    SideCode.AgainstDigitizing.value
                  case TrafficDirection.TowardsDigitizing =>
                    SideCode.TowardsDigitizing.value
                  case _ =>
                    speedLimit.sideCode.value
                }),
                "startMeasure" -> speedLimit.startMeasure,
                "endMeasure" -> speedLimit.endMeasure,
                "createdBy" -> speedLimit.createdBy,
                "modifiedAt" -> speedLimit.modifiedDateTime.map(DateTimePropertyFormat.print(_)),
                "createdAt" -> speedLimit.createdDateTime.map(DateTimePropertyFormat.print(_)),
                "modifiedBy" -> speedLimit.modifiedBy,
                "changeType" -> extractChangeType(since, speedLimit.expired, speedLimit.createdDateTime)
              )
          )
        }
    )
  }

  private def bogieWeightLimitsToGeoJson(since: DateTime, changedLinearAssets: Seq[ChangedLinearAsset]): Map[String, Any] = {
    def mapValues(value: Option[Value]): Seq[(String, Any)] = {
      value match {
        case Some(DynamicValue(value)) =>
          value.properties.flatMap { bogieWeightAxel =>
            bogieWeightAxel.publicId match {
              case "bogie_weight_2_axel" =>
                bogieWeightAxel.values.map { v =>
                  "twoAxleValue" -> v.value
                }
              case "bogie_weight_3_axel" =>
                bogieWeightAxel.values.map { v =>
                  "threeAxleValue" -> v.value
                }
              case _ => None
            }
          }
        case _ => Seq()
      }
    }
    dynamicLinearAssetsToGeoJson(since, changedLinearAssets.filterNot(isSuggested), mapValues)
  }

  private def isSuggested(asset: ChangedLinearAsset): Boolean = {
    asset.linearAsset.value match {
      case Some(Prohibitions(_, isSuggested)) => isSuggested
      case Some(SpeedLimitValue(_, isSuggested)) => isSuggested
      case Some(DynamicValue(x)) =>
        x.properties.find(_.publicId == "suggest_box").flatMap(_.values.headOption) match {
          case Some(value) => value.asInstanceOf[DynamicPropertyValue].value.toString.equals("1")
          case _ => false
        }
      case _ => false
    }
  }

  private def isSuggested(asset: ChangedPointAsset): Boolean = {
    asset.pointAsset.propertyData.find(_.publicId == "suggest_box").flatMap(_.values.headOption) match {
      case Some(value) => value.asInstanceOf[PropertyValue].propertyValue.equals("1")
      case _ => false
    }
  }

  private def sevenRestrictionToGeoJson(since: DateTime, changedLinearAssets: Seq[ChangedLinearAsset]): Map[String, Any] = {
      def mapValues(value: Option[Value]): Seq[(String, Any)] =
        value match {
        case Some(DynamicValue(value)) =>
          value.properties.flatMap { asset =>
             asset.publicId match {
              case "height" | "length" | "weight" | "width" =>
                Map("value" ->  asset.values.flatMap(_.value.asInstanceOf[Option[String]]).head.toInt)
              case _ => None
            }
          }
        case _ => Seq()
      }
      dynamicLinearAssetsToGeoJson(since, changedLinearAssets.filterNot(isSuggested), mapValues)
  }

  private def dynamicLinearAssetsToGeoJson(since: DateTime, changedLinearAssets: Seq[ChangedLinearAsset], mapValues: Option[Value] => Seq[(String, Any)]): Map[String, Any] = {
    Map(
      "type" -> "FeatureCollection",
      "features" ->
        changedLinearAssets.map { case ChangedLinearAsset(linearAsset, link) =>
          Map(
            "type" -> "Feature",
            "id" -> linearAsset.id,
            "geometry" -> Map(
              "type" -> "LineString",
              "coordinates" -> linearAsset.geometry.map(p => Seq(p.x, p.y, p.z))
            ),
            "properties" ->
              (Map(
                "link" -> Map(
                  "type" -> "Feature",
                  "id" -> link.linkId,
                  "geometry" -> Map(
                    "type" -> "LineString",
                    "coordinates" -> link.geometry.map(p => Seq(p.x, p.y, p.z))
                  ),
                  "properties" -> Map(
                    "functionalClass" -> link.functionalClass,
                    "type" -> link.linkType.value,
                    "length" -> link.length
                  )
                ),
                "sideCode" -> (link.trafficDirection match {
                  case TrafficDirection.AgainstDigitizing =>
                    SideCode.AgainstDigitizing.value
                  case TrafficDirection.TowardsDigitizing =>
                    SideCode.TowardsDigitizing.value
                  case _ =>
                    linearAsset.sideCode.value
                }),
                "startMeasure" -> linearAsset.startMeasure,
                "endMeasure" -> linearAsset.endMeasure,
                "createdBy" -> linearAsset.createdBy,
                "modifiedAt" -> linearAsset.modifiedDateTime.map(DateTimePropertyFormat.print(_)),
                "createdAt" -> linearAsset.createdDateTime.map(DateTimePropertyFormat.print(_)),
                "modifiedBy" -> linearAsset.modifiedBy,
                "changeType" -> extractChangeType(since, linearAsset.expired, linearAsset.createdDateTime)
              ) ++ mapValues(linearAsset.value))
          )
        }
    )
  }

  private def prohibitionsToGeoJson(since: DateTime, changedLinearAssets: Seq[ChangedLinearAsset]) =
    Map(
      "type" -> "FeatureCollection",
      "features" ->
        changedLinearAssets.filterNot(x => x.linearAsset.value.nonEmpty && x.linearAsset.value.get.asInstanceOf[Prohibitions].isSuggested).map { case ChangedLinearAsset(linearAsset, link) =>
          Map(
            "type" -> "Feature",
            "id" -> linearAsset.id,
            "geometry" -> Map(
              "type" -> "LineString",
              "coordinates" -> linearAsset.geometry.map(p => Seq(p.x, p.y, p.z))
            ),
            "properties" ->
              Map(
                "value" -> mapValue(linearAsset.value),
                "link" -> Map(
                  "type" -> "Feature",
                  "id" -> link.linkId,
                  "geometry" -> Map(
                    "type" -> "LineString",
                    "coordinates" -> link.geometry.map(p => Seq(p.x, p.y, p.z))
                  ),
                  "properties" -> Map(
                    "functionalClass" -> link.functionalClass,
                    "type" -> link.linkType.value,
                    "length" -> link.length
                  )
                ),
                "sideCode" -> (link.trafficDirection match {
                  case TrafficDirection.AgainstDigitizing =>
                    SideCode.AgainstDigitizing.value
                  case TrafficDirection.TowardsDigitizing =>
                    SideCode.TowardsDigitizing.value
                  case _ =>
                    linearAsset.sideCode.value
                }),
                "startMeasure" -> linearAsset.startMeasure,
                "endMeasure" -> linearAsset.endMeasure,
                "createdBy" -> linearAsset.createdBy,
                "modifiedAt" -> linearAsset.modifiedDateTime.map(DateTimePropertyFormat.print(_)),
                "createdAt" -> linearAsset.createdDateTime.map(DateTimePropertyFormat.print(_)),
                "modifiedBy" -> linearAsset.modifiedBy,
                "changeType" -> extractChangeType(since, linearAsset.expired, linearAsset.createdDateTime)
              )
          )
        }
    )

  private def mapValue(optValue: Option[Value]): Option[Any] = {
    optValue match {
      case Some(Prohibitions(prohibitions, false)) =>
        Some(prohibitions.map { prohibitionValue =>
        Map("typeId" -> prohibitionValue.typeId,
          "exceptions" -> prohibitionValue.exceptions,
          "validityPeriod" ->
            prohibitionValue.validityPeriods.map { validityPeriod =>
              Map(
                "startHour" -> validityPeriod.startHour,
                "endHour" -> validityPeriod.endHour,
                "startMinute" -> validityPeriod.startMinute,
                "endMinute" -> validityPeriod.endMinute,
                "days" -> validityPeriod.days.value)
            }
        )
      })
      case _ => optValue.map(_.toJson)
    }
  }

  private def pointAssetsToGeoJson(since: DateTime, changedPointAssets: Seq[ChangedPointAsset], dynamicPointAssetProperties: (PersistedPointAsset, DateTime) => Map[String, Any]) =
    Map(
      "type" -> "FeatureCollection",
      "features" ->
        changedPointAssets.filterNot(isSuggested).map {  case ChangedPointAsset(pointAsset, link) =>
         val point = GeometryUtils.calculatePointFromLinearReference(link.geometry, pointAsset.mValue).getOrElse(Point(pointAsset.lon, pointAsset.lat))
          Map(
            "type" -> "Feature",
            "id" -> pointAsset.id,
            "geometry" -> Map(
              "type" -> "Point",
              "coordinates" -> Seq(point.x, point.y, point.z)
            ),
            "properties" ->
              (Map(
                "link" -> Map(
                  "type" -> "Feature",
                  "id" -> link.linkId,
                  "geometry" -> Map(
                    "type" -> "LineString",
                    "coordinates" -> link.geometry.map(p => Seq(p.x, p.y, p.z))
                  ),
                  "properties" -> Map(
                    "functionalClass" -> link.functionalClass,
                    "type" -> link.linkType.value,
                    "length" -> link.length
                  )
                ))
                ++ dynamicPointAssetProperties(pointAsset, since))
          )
        }
    )


  def pointAssetGenericProperties(pointAsset: PersistedPointAsset, since: DateTime) : Map[String, Any] = {
    val point = pointAsset.asInstanceOf[PersistedPoint]
   Map(
    "mValue" -> point.mValue,
    "createdBy" -> point.createdBy,
    "modifiedAt" -> point.modifiedAt.map(DateTimePropertyFormat.print(_)),
    "createdAt" -> point.createdAt.map(DateTimePropertyFormat.print(_)),
    "modifiedBy" -> point.modifiedBy,
    "changeType" -> extractChangeType(since, point.expired, point.createdAt),
    "sideCode" -> SideCode.BothDirections.value
   )
  }

  def pointAssetWarningSignsGroupProperties(pointAsset: PersistedPointAsset, since: DateTime): Map[String, Any] = {
    val point = pointAsset.asInstanceOf[PersistedTrafficSign]
    pointAssetGenericProperties(pointAsset, since) ++
    Map(
      "typeValue" -> trafficSignService.getProperty(point, trafficSignService.typePublicId).get.propertyValue.toInt,
      "sideCode" -> point.validityDirection
    )
  }

  def pointAssetStopSignProperties(pointAsset: PersistedPointAsset, since: DateTime): Map[String, Any] = {
    val point = pointAsset.asInstanceOf[PersistedTrafficSign]
    pointAssetGenericProperties(pointAsset, since) ++ Map("sideCode" -> point.validityDirection)
  }

  private def massTransitStopsToGeoJson(since: DateTime, massTransitStopsOnVallu: Seq[ChangedPointAsset]): Map[String, Any] = {
    def getValidityDatesProperties(stop: PersistedMassTransitStop, publicId: String): String = {
      if (!propertyIsDefined(stop, publicId) || propertyIsEmpty(stop, publicId)) {
        "true"
      } else
        transformToISODate(extractPropertyValueOption(stop, publicId))
    }

    Map(
      "type" -> "FeatureCollection",
      "features" ->
        massTransitStopsOnVallu.map { case ChangedPointAsset(pointAsset, link) =>
          val municipalityInfo =
            massTransitStopsOnVallu.flatMap { stop =>
              municipalityService.getMunicipalitiesNameAndIdByCode(Set(stop.pointAsset.municipalityCode))
            }
          val massTransitStop = pointAsset.asInstanceOf[PersistedMassTransitStop]
          val busStopTypes = getPropertyValuesByPublicId("pysakin_tyyppi", massTransitStop.propertyData).map(x => x.propertyValue.toLong).toSet
          val modificationInfo = massTransitStop.modified.modificationTime match {
            case Some(_) => massTransitStop.modified
            case _ => massTransitStop.created
          }
          val validTo = getValidityDatesProperties(massTransitStop, "viimeinen_voimassaolopaiva")
          val validFrom = getValidityDatesProperties(massTransitStop, "ensimmainen_voimassaolopaiva")

          Map(
            "id" -> massTransitStop.nationalId,
            "geometry" ->
              Map(
                "type" -> "MassTransitStop",
                "coordinates" -> Seq(massTransitStop.lon, massTransitStop.lat)
              ),
            "properties" ->
              Map(
                "adminStopId" -> extractPropertyValueOption(massTransitStop, "yllapitajan_tunnus").getOrElse(""),
                "stopCode" -> extractPropertyValueOption(massTransitStop, "matkustajatunnus").getOrElse(""),
                "name_fi" -> extractPropertyValueOption(massTransitStop, "nimi_suomeksi"),
                "name_sv" -> extractPropertyValueOption(massTransitStop, "nimi_ruotsiksi"),
                "bearing" -> massTransitStop.bearing.getOrElse(""),
                "bearingDescription" -> extractOptionalPropertyDisplayValue(massTransitStop, "liikennointisuuntima"),
                "direction" -> extractPropertyValueOption(massTransitStop, "liikennointisuunta"),
                "stopAttribute" -> busStopTypes,
                "equipment" -> describeEquipments(massTransitStop),
                "reachability" -> describeReachability(massTransitStop),
                "specialNeeds" -> extractPropertyValueOption(massTransitStop, "esteettomyys_liikuntarajoitteiselle").getOrElse(""),
                "modifiedBy" -> modificationInfo.modifier.get,
                "modifiedTimestamp" -> ISODateTimeFormat.dateHourMinuteSecond.print(modificationInfo.modificationTime.get),
                "validFrom" -> validFrom,
                "validTo" -> validTo,
                "administratorCode" -> (if (propertyIsDefined(massTransitStop, "tietojen_yllapitaja")) extractOptionalPropertyDisplayValue(massTransitStop, "tietojen_yllapitaja").get else "Ei tiedossa"),
                "municipalityCode" -> massTransitStop.municipalityCode,
                "municipalityName" -> (if (municipalityInfo.nonEmpty) municipalityInfo.find(_.id == massTransitStop.municipalityCode).head.name else ""),
                "comments" -> extractPropertyValueOption(massTransitStop, "lisatiedot").getOrElse(""),
                "platformCode" -> extractPropertyValueOption(massTransitStop, "laiturinumero").getOrElse(""),
                "connectedToTerminal" -> extractPropertyValueOption(massTransitStop, "liitetty_terminaaliin_ulkoinen_tunnus").getOrElse(""),
                "contactEmails" -> "pysakit@digiroad.fi",
                "zoneId" -> extractPropertyValueOption(massTransitStop, "vyohyketieto").getOrElse(""),
                //Parameter by default to extend from points, we will not use them when print VALLU XML
                "sideCode" -> SideCode.BothDirections.value,
                "changeType" -> "",
                "mValue" -> 0.0,
                "link" -> Map(
                  "type" -> "Feature",
                  "id" -> link.linkId,
                  "geometry" -> Map(
                    "type" -> "LineString",
                    "coordinates" -> link.geometry.map(p => Seq(p.x, p.y, p.z))
                  ),
                  "properties" -> Map(
                    "functionalClass" -> link.functionalClass,
                    "type" -> link.linkType.value,
                    "length" -> link.length
                  )
                )
              )
          )
        }
    )
  }

  private def extractChangeType(since: DateTime, expired: Boolean, createdDateTime: Option[DateTime]) = {
    if (expired) {
      "Remove"
    } else if (createdDateTime.exists(_.isAfter(since))) {
      "Add"
    } else {
      "Modify"
    }
  }

  private def roadLinksToGeoJson(changedRoadlinks: Seq[ChangedRoadlink]) =
    Map(
      "type" -> "FeatureCollection",
      "features" ->
        changedRoadlinks.map { case ChangedRoadlink(link, value, createdAt, changeType) =>
          Map(
            "type" -> "Feature",
            "id" -> link.linkId,
            "geometry" -> Map(
              "type" -> "LineString",
              "coordinates" -> link.geometry.map(p => Seq(p.x, p.y, p.z))
            ),
            "properties" ->
              Map(
                "value" -> value,
                "link" -> Map(
                  "type" -> "Feature",
                  "id" -> link.linkId,
                  "geometry" -> Map(
                    "type" -> "LineString",
                    "coordinates" -> link.geometry.map(p => Seq(p.x, p.y, p.z))
                  ),
                  "properties" -> Map(
                    "functionalClass" -> link.functionalClass,
                    "type" -> link.linkType.value,
                    "length" -> link.length
                  )
                ),
                "sideCode" -> (link.trafficDirection match {
                  case TrafficDirection.AgainstDigitizing =>
                    SideCode.AgainstDigitizing.value
                  case TrafficDirection.TowardsDigitizing =>
                    SideCode.TowardsDigitizing.value
                  case _ =>
                    SideCode.BothDirections.value
                }),
                "startMeasure" -> 0,
                "endMeasure" -> link.geometry.length,
                "modifiedAt" -> link.modifiedAt,
                "createdAt" -> createdAt.map(DateTimePropertyFormat.print(_)),
                "changeType" -> changeType
              )
          )
        }
    )

  def laneChangesToGeoJson(laneChanges: Seq[LaneChange], withGeometry: Boolean = false): mutable.LinkedHashMap[String, Any] = {
    def decodePropertyValue(value: Any): String = {
      value match {
        case v: String => v
        case _ => ""
      }
    }

    def getGeometryMap(roadLink: RoadLink) = {
      if (withGeometry) {
        Map("type" -> "LineString",
          "coordinates" -> roadLink.geometry.map(p => Seq(p.x, p.y, p.z))
        )
      } else {
        None
      }
    }

    def mapLaneAddressInfo(lane: PersistedLane, roadLink: RoadLink): mutable.LinkedHashMap[String, Any] = {
      try {
        val roadStartAddr = roadLink.attributes("START_ADDR").toString.toInt
        val roadEndAddr = roadLink.attributes("END_ADDR").toString.toInt
        val roadAddressSideCode = SideCode.apply(roadLink.attributes("SIDECODE").toString.toInt)

        val (laneStartAddr, laneEndAddr) = RoadAddress.getAddressMValuesForCutAssets(roadLink.length, roadAddressSideCode, roadStartAddr, roadEndAddr,
          lane.startMeasure, lane.endMeasure)
        mutable.LinkedHashMap(
          "roadNumber" -> roadLink.attributes("ROAD_NUMBER"),
          "roadPart" -> roadLink.attributes("ROAD_PART_NUMBER"),
          "roadTrack" -> roadLink.attributes("TRACK"),
          "roadStartAddr" -> laneStartAddr,
          "roadEndAddr" -> laneEndAddr
        )
      }
      catch {
        case nfe: NumberFormatException =>
          logger.error(nfe.getMessage)
          mutable.LinkedHashMap()
        case nsee: NoSuchElementException =>
          logger.error(nsee.getMessage)
          mutable.LinkedHashMap()
        case e: Exception =>
          logger.error(e.getMessage)
          mutable.LinkedHashMap()
      }

    }

    def generateCommonFields(changedAt: Option[DateTime], changedBy: Option[String], historyEventOrderNumber: Option[Int]): mutable.LinkedHashMap[String, Any] = {
      val changedAtString = if(changedAt.nonEmpty) DateTimePropertyFormat.print(changedAt.get) else ""
      val changedByString = if(changedBy.nonEmpty) changedBy.get else ""

      mutable.LinkedHashMap("changedAt" -> changedAtString,
        "changedBy" -> changedByString,
        "historyEventOrderNumber" -> historyEventOrderNumber.getOrElse("").toString)
    }

    // Generate common fields for "Modified" changes
    def generateCommonModifiedFields(lane: PersistedLane, oldLane: PersistedLane): mutable.LinkedHashMap[String, Any] = {
      val laneType = laneService.getPropertyValue(lane.attributes, "lane_type")
      val oldLaneType = laneService.getPropertyValue(oldLane.attributes, "lane_type")

      mutable.LinkedHashMap("oldId" -> oldLane.id,
        "oldLaneNumber" -> oldLane.laneCode,
        "oldLaneType" -> oldLaneType,
        "newId" -> lane.id,
        "newLaneNumber" -> lane.laneCode,
        "newLaneType" -> laneType)
    }

    // Generate response fields for segment of lane which was added or expired in change
    def generateShortenedOrLengthenedSegmentFields(changedSegment: ChangedSegment, laneChange: LaneChange,
                                                  sameSegmentLaneAddressInfo: mutable.LinkedHashMap[String, Any]): mutable.LinkedHashMap[String, Any] = {
      val newLane = laneChange.lane
      val oldLane = laneChange.oldLane.get

      val laneAttributesForSegment = if (changedSegment.segmentChangeType == LaneChangeType.Add) newLane.attributes
      else oldLane.attributes

      val startDate = decodePropertyValue(laneService.getPropertyValue(laneAttributesForSegment, "start_date"))
      val endDate = decodePropertyValue(laneService.getPropertyValue(laneAttributesForSegment, "end_date"))
      val laneType = decodePropertyValue(laneService.getPropertyValue(laneAttributesForSegment, "lane_type"))


      val segmentModificationMap = changedSegment.segmentChangeType match {
        case LaneChangeType.Expired =>
          mutable.LinkedHashMap("oldId" -> oldLane.id,
            "oldLaneNumber" -> oldLane.laneCode,
            "oldLaneType" -> laneType) ++
            generateCommonFields(oldLane.expiredDateTime, oldLane.expiredBy, laneChange.historyEventOrderNumber)
        case LaneChangeType.Add =>
          mutable.LinkedHashMap("newId" -> newLane.id,
            "newLaneNumber" -> newLane.laneCode,
            "newLaneType" -> laneType) ++
            generateCommonFields(newLane.createdDateTime, newLane.createdBy, laneChange.historyEventOrderNumber)
      }

     mutable.LinkedHashMap("type" -> "Feature",
        "changeType" -> {
          changedSegment.segmentChangeType match {
            case LaneChangeType.Add => "Add"
            case LaneChangeType.Expired => "Expire"
          }
        },
        "geometry" -> getGeometryMap(laneChange.roadLink.get),
        "startDate" -> startDate,
        "endDate" -> endDate,
        "linkId" -> laneChange.roadLink.get.linkId,
        "startMeasure" -> changedSegment.startMeasure,
        "endMeasure" -> changedSegment.endMeasure,
        "roadNumber" -> sameSegmentLaneAddressInfo("roadNumber"),
        "roadPart" -> sameSegmentLaneAddressInfo("roadPart"),
        "roadTrack" -> sameSegmentLaneAddressInfo("roadTrack"),
        "roadStartAddr" -> changedSegment.startAddrM,
        "roadEndAddr" -> changedSegment.endAddrM) ++ segmentModificationMap
    }

    mutable.LinkedHashMap("type" -> "FeatureCollection",
      "features" -> laneChanges.flatMap{ laneChange =>
        val newLane = laneChange.lane
        val laneAttributes = newLane.attributes

        val startDate = decodePropertyValue(laneService.getPropertyValue(laneAttributes, "start_date"))
        val endDate = decodePropertyValue(laneService.getPropertyValue(laneAttributes, "end_date"))

        val laneType = laneService.getPropertyValue(laneAttributes, "lane_type")

        val commonJson = mutable.LinkedHashMap("type" -> "Feature",
          "changeType" -> {laneChange.changeType match {
            case LaneChangeType.Add => "Add"
            case LaneChangeType.Expired => "Expire"
            case _ => "Modify"
          }},
          "geometry" -> getGeometryMap(laneChange.roadLink.get),
          "startDate" -> startDate,
          "endDate" -> endDate,
          "linkId" -> newLane.linkId,
          "startMeasure" -> newLane.startMeasure,
          "endMeasure" -> newLane.endMeasure) ++ mapLaneAddressInfo(newLane, laneChange.roadLink.get)

        val json = laneChange.changeType match {
          case LaneChangeType.Add =>
            Seq(commonJson ++ mutable.LinkedHashMap("newId" -> newLane.id,
              "newLaneNumber" -> newLane.laneCode,
              "newLaneType" -> laneType) ++
              generateCommonFields(newLane.createdDateTime, newLane.createdBy, laneChange.historyEventOrderNumber)
            )

          case LaneChangeType.Expired =>
            Seq(commonJson ++ mutable.LinkedHashMap("oldId" -> newLane.id,
              "oldLaneNumber" -> newLane.laneCode,
              "oldLaneType" -> laneType) ++
              generateCommonFields(newLane.expiredDateTime, newLane.expiredBy, laneChange.historyEventOrderNumber))

          case LaneChangeType.LaneCodeTransfer | LaneChangeType.AttributesChanged =>
            val oldLane = laneChange.oldLane.get
            Seq(commonJson ++ generateCommonModifiedFields(newLane, oldLane) ++
              generateCommonFields(newLane.modifiedDateTime, newLane.modifiedBy, laneChange.historyEventOrderNumber))

          case LaneChangeType.Divided =>
            val oldLane = laneChange.oldLane.get
            Seq(commonJson ++ generateCommonModifiedFields(newLane, oldLane) ++
              generateCommonFields(oldLane.expiredDateTime, oldLane.expiredBy, laneChange.historyEventOrderNumber))

          case LaneChangeType.Shortened | LaneChangeType.Lengthened =>
            val oldLane = laneChange.oldLane.get
            val oldLaneType = laneService.getPropertyValue(oldLane.attributes, "lane_type")

            val (startSegmentChange, endSegmentChange) = laneService.getChangedSegmentMeasures(laneChange)
            val sameSegmentLaneAddressInfo = laneChange.changeType match {
              case LaneChangeType.Shortened => mapLaneAddressInfo(newLane, laneChange.roadLink.get)
              case LaneChangeType.Lengthened => mapLaneAddressInfo(oldLane, laneChange.roadLink.get)
            }

            val startSegmentMap = startSegmentChange match {
              case Some(segment: ChangedSegment) => generateShortenedOrLengthenedSegmentFields(segment, laneChange, sameSegmentLaneAddressInfo)
              case None => Map.empty[String, Any]
            }

            val endSegmentMap = endSegmentChange match {
              case Some(segment: ChangedSegment) => generateShortenedOrLengthenedSegmentFields(segment, laneChange, sameSegmentLaneAddressInfo)
              case None => mutable.LinkedHashMap.empty[String, Any]
            }

            val sameSegmentMap = mutable.LinkedHashMap("type" -> "Feature",
              "changeType" -> "Modify",
              "geometry" -> getGeometryMap(laneChange.roadLink.get),
              "startDate" -> startDate,
              "endDate" -> endDate,
              "linkId" -> newLane.linkId,
              "startMeasure" -> {
                laneChange.changeType match {
                  case LaneChangeType.Shortened => newLane.startMeasure
                  case LaneChangeType.Lengthened => oldLane.startMeasure
                }
              },
              "endMeasure" -> {
                laneChange.changeType match {
                  case LaneChangeType.Shortened => newLane.endMeasure
                  case LaneChangeType.Lengthened => oldLane.endMeasure
                }
              },
              "oldId" -> oldLane.id,
              "oldLaneNumber" -> oldLane.laneCode,
              "oldLaneType" -> oldLaneType,
              "newId" -> newLane.id,
              "newLaneNumber" -> newLane.laneCode,
              "newLaneType" -> laneType) ++
              sameSegmentLaneAddressInfo ++
              generateCommonFields(newLane.createdDateTime, newLane.createdBy, laneChange.historyEventOrderNumber)

              Seq(sameSegmentMap, startSegmentMap, endSegmentMap)

          case _ => Seq()
        }

        json.filterNot(_.isEmpty)
      }
    )
  }
}
