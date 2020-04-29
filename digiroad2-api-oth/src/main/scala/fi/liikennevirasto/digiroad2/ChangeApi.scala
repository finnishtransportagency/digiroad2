package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.Digiroad2Context._
import fi.liikennevirasto.digiroad2.asset.DateParser._
import fi.liikennevirasto.digiroad2.asset.{SideCode, _}
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset.{DynamicValue, PieceWiseLinearAsset, Prohibitions, SpeedLimitValue, Value}
import fi.liikennevirasto.digiroad2.service.ChangedVVHRoadlink
import fi.liikennevirasto.digiroad2.service.linearasset.{ChangedLinearAsset, ChangedSpeedLimit}
import fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop.PersistedMassTransitStop
import fi.liikennevirasto.digiroad2.vallu.ValluStoreStopChangeMessage._
import fi.liikennevirasto.digiroad2.vallu.ValluTransformer.{describeEquipments, describeReachability, transformToISODate}
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.swagger.{Swagger, SwaggerSupport}
import org.scalatra.{BadRequest, ScalatraServlet}

class ChangeApi(val swagger: Swagger) extends ScalatraServlet with JacksonJsonSupport with AuthenticationSupport with SwaggerSupport {
  protected val applicationDescription = "Change API "
  protected implicit val jsonFormats: Formats = DefaultFormats

  before() {
    basicAuth
    contentType = formats("json")
  }

  //Description of Api entry point to get assets changes by asset type and between two dates
  val getChangesOfAssetsByType =
    (apiOperation[Long]("getChangesOfAssetsByType")
      .parameters(
        queryParam[String]("since").description("Initial date of the interval between two dates to obtain modifications for a particular asset."),
        queryParam[String]("until").description("The end date of the interval between two dates to obtain modifications for an asset."),
        queryParam[String]("withAdjust").description("With the field withAdjust, we allow or not the presence of records modified by vvh_generated and not modified yet on the response. The value is False by default").optional,
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
    val since = DateTime.parse(params.get("since").getOrElse(halt(BadRequest("Missing mandatory 'since' parameter"))))
    val until = DateTime.parse(params.get("until").getOrElse(halt(BadRequest("Missing mandatory 'until' parameter"))))
    val token = params.get("token").map(_.toString)

    val withAdjust = params.get("withAdjust") match{
      case Some(value)=> true
      case _ => false
    }

    params("assetType") match {
      case "speed_limits"                => speedLimitsToGeoJson(since, speedLimitService.getChanged(since, until, withAdjust, token))
      case "total_weight_limits"         => sevenRestrictionToGeoJson(since, dynamicLinearAssetService.getChanged(TotalWeightLimit.typeId , since, until, withAdjust, token))
      case "trailer_truck_weight_limits" => sevenRestrictionToGeoJson(since, dynamicLinearAssetService.getChanged(TrailerTruckWeightLimit.typeId, since, until, withAdjust, token))
      case "axle_weight_limits"          => sevenRestrictionToGeoJson(since, dynamicLinearAssetService.getChanged(AxleWeightLimit.typeId, since, until, withAdjust, token))
      case "bogie_weight_limits"         => bogieWeightLimitsToGeoJson(since, dynamicLinearAssetService.getChanged(BogieWeightLimit.typeId, since, until, withAdjust, token))
      case "height_limits"               => sevenRestrictionToGeoJson(since, dynamicLinearAssetService.getChanged(HeightLimit.typeId, since, until, withAdjust, token))
      case "length_limits"               => sevenRestrictionToGeoJson(since, dynamicLinearAssetService.getChanged(LengthLimit.typeId, since, until, withAdjust, token))
      case "width_limits"                => sevenRestrictionToGeoJson(since, dynamicLinearAssetService.getChanged(WidthLimit.typeId, since, until, withAdjust, token))
      case "road_names"                  => vvhRoadLinkToGeoJson(roadLinkService.getChanged(since, until))
      case "vehicle_prohibitions"        => prohibitionsToGeoJson(since, prohibitionService.getChanged(Prohibition.typeId, since, until, withAdjust, token))
      case "pedestrian_crossing"         => pointAssetsToGeoJson(since, pedestrianCrossingService.getChanged(since, until, token), pointAssetGenericProperties)
      case "obstacles"                   => pointAssetsToGeoJson(since, obstacleService.getChanged(since, until, token), pointAssetGenericProperties)
      case "warning_signs_group"         => pointAssetsToGeoJson(since, trafficSignService.getChangedByType(trafficSignService.getTrafficSignTypeByGroup(TrafficSignTypeGroup.GeneralWarningSigns), since, until, token), pointAssetWarningSignsGroupProperties)
      case "stop_sign"                   => pointAssetsToGeoJson(since, trafficSignService.getChangedByType(Set(Stop.OTHvalue), since, until, token), pointAssetStopSignProperties)
    }
  }

  get("/mass_transit_stops", operation(getMassTransitStopsPrintedAtValluXML)) {
    contentType = formats("json")
    val since = DateTime.parse(params.get("since").getOrElse(halt(BadRequest("Missing mandatory 'since' parameter"))))
    val until = DateTime.parse(params.get("until").getOrElse(halt(BadRequest("Missing mandatory 'until' parameter"))))
    val token = params.get("token").map(_.toString)

    massTransitStopsToGeoJson(since, massTransitStopService.getPublishedOnXml(since, until, token))
  }

  private def speedLimitsToGeoJson(since: DateTime, speedLimits: Seq[ChangedSpeedLimit]) =
    Map(
      "type" -> "FeatureCollection",
      "features" ->
        speedLimits.filterNot(x => x.speedLimit.value.nonEmpty && x.speedLimit.value.get.isSuggested).map { case ChangedSpeedLimit(speedLimit, link) =>
          Map(
            "type" -> "Feature",
            "id" -> speedLimit.id,
            "geometry" -> Map(
              "type" -> "LineString",
              "coordinates" -> speedLimit.geometry.map(p => Seq(p.x, p.y, p.z))
            ),
            "properties" ->
              Map(
                "value" -> speedLimit.value.map(_.value),
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

  private def vvhRoadLinkToGeoJson(changedRoadlinks: Seq[ChangedVVHRoadlink]) =
    Map(
      "type" -> "FeatureCollection",
      "features" ->
        changedRoadlinks.map { case ChangedVVHRoadlink(link, value, createdAt, changeType) =>
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
}
