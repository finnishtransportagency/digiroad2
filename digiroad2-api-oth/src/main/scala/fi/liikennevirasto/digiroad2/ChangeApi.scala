package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.Digiroad2Context._
import fi.liikennevirasto.digiroad2.asset.DateParser._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.lane.{LaneChangeType, PersistedLane}
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.service.ChangedRoadlink
import fi.liikennevirasto.digiroad2.service.lane.LaneChange
import fi.liikennevirasto.digiroad2.service.linearasset.{ChangedLinearAsset, ChangedSpeedLimit}
import fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop.PersistedMassTransitStop
import fi.liikennevirasto.digiroad2.util.GeometryTransform
import fi.liikennevirasto.digiroad2.vallu.ValluStoreStopChangeMessage._
import fi.liikennevirasto.digiroad2.vallu.ValluTransformer.{describeEquipments, describeReachability, transformToISODate}
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.swagger.{Swagger, SwaggerSupport}
import org.scalatra.{BadRequest, ScalatraServlet}

class ChangeApi(val swagger: Swagger) extends ScalatraServlet with JacksonJsonSupport with SwaggerSupport {
  protected val applicationDescription = "Change API "
  protected implicit val jsonFormats: Formats = DefaultFormats
  lazy val geometryTransform: GeometryTransform = new GeometryTransform(Digiroad2Context.roadAddressService)
  val apiId = "change-api"

  case class ChangedSegment(startMeasure: Double, startAddrM: Int, endMeasure: Double, endAddrM: Int, segmentChangeType: LaneChangeType)

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
      val since = DateTime.parse(params.get("since").getOrElse(halt(BadRequest("Missing mandatory 'since' parameter"))))
      val until = DateTime.parse(params.get("until").getOrElse(halt(BadRequest("Missing mandatory 'until' parameter"))))
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
        case "speed_limits" => speedLimitsToGeoJson(since, speedLimitService.getChanged(since, until, withAdjust, token))
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
      }
    }
  }

  get("/mass_transit_stops", operation(getMassTransitStopsPrintedAtValluXML)) {
    contentType = formats("json")
    ApiUtils.avoidRestrictions(apiId + "_mass_transit_stops", request, params) { params =>
      val since = DateTime.parse(params.get("since").getOrElse(halt(BadRequest("Missing mandatory 'since' parameter"))))
      val until = DateTime.parse(params.get("until").getOrElse(halt(BadRequest("Missing mandatory 'until' parameter"))))
      val token = params.get("token").map(_.toString)

      massTransitStopsToGeoJson(since, massTransitStopService.getPublishedOnXml(since, until, token))
    }
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

  def laneChangesToGeoJson(laneChanges: Seq[LaneChange], withGeometry: Boolean = false): Map[String, Any] = {
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

    def mapLaneAddressInfo(lane: PersistedLane, roadLink: RoadLink): Map[String, Any] = {
      try {
        val roadStartAddr = roadLink.attributes("START_ADDR").toString.toInt
        val roadEndAddr = roadLink.attributes("END_ADDR").toString.toInt
        val roadAddressSideCode = SideCode.apply(roadLink.attributes("SIDECODE").toString.toInt)

        val (laneStartAddr, laneEndAddr) = geometryTransform.getAddressMValuesForCutAssets(roadLink.length, roadAddressSideCode, roadStartAddr, roadEndAddr,
          lane.startMeasure, lane.endMeasure)
        Map(
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
          Map()
        case nsee: NoSuchElementException =>
          logger.error(nsee.getMessage)
          Map()
        case e: Exception =>
          logger.error(e.getMessage)
          Map()
      }

    }

    /**
      *
      * @param laneChange laneChange for shortened or lengthened lane
      * @return Optional M-values and road address values for segments. 1st element of tuple is for digitizing
      *         direction start segment, 2nd is for end segment. Returns None for segment in question if
      *         that end of lane was not changed.
      */
    def getChangedSegmentMeasures(laneChange: LaneChange): (Option[ChangedSegment], Option[ChangedSegment]) = {
      val roadLink = laneChange.roadLink.get
      val lane = laneChange.lane
      val oldLane = laneChange.oldLane.get

      val roadLinkStartAddr = roadLink.attributes("START_ADDR").toString.toInt
      val roadLinkEndAddr = roadLink.attributes("END_ADDR").toString.toInt
      val roadAddressSideCode = SideCode.apply(roadLink.attributes("SIDECODE").toString.toInt)

      val (laneStartAddrM, laneEndAddrM) = geometryTransform.getAddressMValuesForCutAssets(roadLink.length, roadAddressSideCode,
        roadLinkStartAddr, roadLinkEndAddr, lane.startMeasure, lane.endMeasure)

      val (oldLaneStartAddrM, oldLaneEndAddrM) = geometryTransform.getAddressMValuesForCutAssets(roadLink.length, roadAddressSideCode,
        roadLinkStartAddr, roadLinkEndAddr, oldLane.startMeasure, oldLane.endMeasure)


      val cutFromStart = lane.startMeasure > oldLane.startMeasure
      val cutFromEnd = lane.endMeasure < oldLane.endMeasure
      val lenghtenedFromStart = lane.startMeasure < oldLane.startMeasure
      val lenghtenedFromEnd = lane.endMeasure > oldLane.endMeasure

      (cutFromStart, cutFromEnd, lenghtenedFromStart, lenghtenedFromEnd) match {
        // Lane cut from digitizing direction start
        case (true, false, false, false) =>
          roadAddressSideCode match {
            case SideCode.TowardsDigitizing =>
              val startSegmentChange = Some(ChangedSegment(oldLane.startMeasure, oldLaneStartAddrM, lane.startMeasure, laneStartAddrM, LaneChangeType.Expired))
              val endSegmentChange = None
              (startSegmentChange, endSegmentChange)
            case SideCode.AgainstDigitizing =>
              val startSegmentChange = Some(ChangedSegment(oldLane.startMeasure, laneEndAddrM, lane.startMeasure, oldLaneEndAddrM, LaneChangeType.Expired))
              val endSegmentChange = None
              (startSegmentChange, endSegmentChange)
          }

        // Lane cut from digitizing direction end
        case (false, true, false, false) =>
          roadAddressSideCode match {
            case SideCode.TowardsDigitizing =>
              val startSegmentChange = None
              val endSegmentChange = Some(ChangedSegment(lane.endMeasure, laneEndAddrM, oldLane.endMeasure, oldLaneEndAddrM, LaneChangeType.Expired))
              (startSegmentChange, endSegmentChange)
            case SideCode.AgainstDigitizing =>
              val startSegmentChange = None
              val endSegmentChange = Some(ChangedSegment(lane.endMeasure, laneStartAddrM, oldLane.endMeasure, oldLaneStartAddrM, LaneChangeType.Expired))
              (startSegmentChange, endSegmentChange)
          }

        // Lane cut from both ends
        case (true, true, false, false) =>
          roadAddressSideCode match {
            case SideCode.TowardsDigitizing =>
              val startSegmentChange = Some(ChangedSegment(oldLane.startMeasure, oldLaneStartAddrM, lane.startMeasure, laneStartAddrM, LaneChangeType.Expired))
              val endSegmentChange = Some(ChangedSegment(lane.endMeasure, laneEndAddrM, oldLane.endMeasure, oldLaneEndAddrM, LaneChangeType.Expired))
              (startSegmentChange, endSegmentChange)
            case SideCode.AgainstDigitizing =>
              val startSegmentChange = Some(ChangedSegment(oldLane.startMeasure, oldLaneEndAddrM, lane.startMeasure, laneEndAddrM, LaneChangeType.Expired))
              val endSegmentChange = Some(ChangedSegment(lane.endMeasure, laneStartAddrM, oldLane.endMeasure, oldLaneStartAddrM, LaneChangeType.Expired))
              (startSegmentChange, endSegmentChange)

          }

        // Lane lengthened from digitizing direction start
        case (false, false, true, false) =>
          roadAddressSideCode match {
            case SideCode.TowardsDigitizing =>
              val startSegmentChange = Some(ChangedSegment(lane.startMeasure, laneStartAddrM, oldLane.startMeasure, oldLaneStartAddrM, LaneChangeType.Add))
              val endSegmentChange = None
              (startSegmentChange, endSegmentChange)
            case SideCode.AgainstDigitizing =>
              val startSegmentChange = Some(ChangedSegment(lane.startMeasure, oldLaneEndAddrM, oldLane.startMeasure, laneEndAddrM, LaneChangeType.Add))
              val endSegmentChange = None
              (startSegmentChange, endSegmentChange)
          }

        // Lane lengthened from digitizing direction end
        case (false, false, false, true) =>
          roadAddressSideCode match {
            case SideCode.TowardsDigitizing =>
              val startSegmentChange = None
              val endSegmentChange = Some(ChangedSegment(oldLane.endMeasure, oldLaneEndAddrM, lane.endMeasure, laneEndAddrM, LaneChangeType.Add))
              (startSegmentChange, endSegmentChange)
            case SideCode.AgainstDigitizing =>
              val startSegmentChange = None
              val endSegmentChange = Some(ChangedSegment(oldLane.endMeasure, laneStartAddrM, lane.endMeasure, oldLaneStartAddrM, LaneChangeType.Add))
              (startSegmentChange, endSegmentChange)
          }

        // Lane lengthened from both ends
        case (false, false, true, true) =>
          roadAddressSideCode match {
            case SideCode.TowardsDigitizing =>
              val startSegmentChange = Some(ChangedSegment(lane.startMeasure, laneStartAddrM, oldLane.startMeasure, oldLaneStartAddrM, LaneChangeType.Add))
              val endSegmentChange = Some(ChangedSegment(oldLane.endMeasure, oldLaneEndAddrM, lane.endMeasure, laneEndAddrM, LaneChangeType.Add))
              (startSegmentChange, endSegmentChange)
            case SideCode.AgainstDigitizing =>
              val startSegmentChange = Some(ChangedSegment(lane.startMeasure, oldLaneEndAddrM, oldLane.startMeasure, laneEndAddrM, LaneChangeType.Add))
              val endSegmentChange = Some(ChangedSegment(oldLane.endMeasure, laneStartAddrM, lane.endMeasure, oldLaneStartAddrM, LaneChangeType.Add))
              (startSegmentChange, endSegmentChange)
          }

        // Lane cut from digitizing direction start and lengthened from end
        case (true, false, false, true) =>
          roadAddressSideCode match {
            case SideCode.TowardsDigitizing =>
              val startSegmentChange = Some(ChangedSegment(oldLane.startMeasure, oldLaneStartAddrM, lane.startMeasure, laneStartAddrM, LaneChangeType.Expired))
              val endSegmentChange = Some(ChangedSegment(oldLane.endMeasure, oldLaneEndAddrM, lane.endMeasure, laneEndAddrM, LaneChangeType.Add))
              (startSegmentChange, endSegmentChange)
            case SideCode.AgainstDigitizing =>
              val startSegmentChange = Some(ChangedSegment(oldLane.startMeasure, oldLaneEndAddrM, lane.startMeasure, laneEndAddrM, LaneChangeType.Expired))
              val endSegmentChange = Some(ChangedSegment(oldLane.endMeasure, laneStartAddrM, lane.endMeasure, oldLaneStartAddrM, LaneChangeType.Add))
              (startSegmentChange, endSegmentChange)
          }

        // Lane cut from digitizing direction end and lengthened from start
        case (false, true, true, false) =>
          roadAddressSideCode match {
            case SideCode.TowardsDigitizing =>
              val startSegmentChange = Some(ChangedSegment(lane.startMeasure, laneStartAddrM, oldLane.startMeasure, oldLaneStartAddrM, LaneChangeType.Add))
              val endSegmentChange = Some(ChangedSegment(lane.endMeasure, laneEndAddrM, oldLane.endMeasure, oldLaneEndAddrM, LaneChangeType.Expired))
              (startSegmentChange, endSegmentChange)
            case SideCode.AgainstDigitizing =>
              val startSegmentChange = Some(ChangedSegment(lane.startMeasure, oldLaneEndAddrM, oldLane.startMeasure, laneEndAddrM, LaneChangeType.Add))
              val endSegmentChange = Some(ChangedSegment(lane.endMeasure, laneStartAddrM, oldLane.endMeasure, oldLaneStartAddrM, LaneChangeType.Expired))
              (startSegmentChange, endSegmentChange)
          }
      }
    }

    def generateCommonFieldsWithDiffSource(laneToUseOnCreates: PersistedLane, modifiedAt: Option[String] = None,
                                            modifiedBy: Option[String] = None, expiredAt: Option[String] = None,
                                           expiredBy: Option[String] = None): Map[String, String] = {

      Map("createdAt" -> laneToUseOnCreates.createdDateTime.map(DateTimePropertyFormat.print(_)).getOrElse(""),
        "createdBy" -> laneToUseOnCreates.createdBy.getOrElse(""),
        "modifiedAt" -> modifiedAt.getOrElse(""),
        "modifiedBy" -> modifiedBy.getOrElse(""),
        "expiredAt" -> expiredAt.getOrElse(""),
        "expiredBy" -> expiredBy.getOrElse("")
      )
    }

    def generateCommonModifiedFields(lane: PersistedLane, oldLane: PersistedLane): Map[String, Any] = {
      val laneType = laneService.getPropertyValue(lane.attributes, "lane_type")
      val oldLaneType = laneService.getPropertyValue(oldLane.attributes, "lane_type")

      Map("oldId" -> oldLane.id,
        "oldLaneNumber" -> oldLane.laneCode,
        "oldLaneType" -> oldLaneType,
        "newId" -> lane.id,
        "newLaneNumber" -> lane.laneCode,
        "newLaneType" -> laneType)
    }

    // Generate response fields for segment of lane which was added or expired in change
    def generateShortenedOrLengthenedSegmentFields(changedSegment: ChangedSegment, laneChange: LaneChange,
                                                  sameSegmentLaneAddressInfo: Map[String, Any]): Seq[Map[String, Any]] = {
      val newLane = laneChange.lane
      val oldLane = laneChange.oldLane.get

      val laneAttributesForSegment = if (changedSegment.segmentChangeType == LaneChangeType.Add) newLane.attributes
      else oldLane.attributes

      val startDate = decodePropertyValue(laneService.getPropertyValue(laneAttributesForSegment, "start_date"))
      val endDate = decodePropertyValue(laneService.getPropertyValue(laneAttributesForSegment, "end_date"))
      val laneType = decodePropertyValue(laneService.getPropertyValue(laneAttributesForSegment, "lane_type"))


      val segmentModificationMap = changedSegment.segmentChangeType match {
        case LaneChangeType.Expired =>
          Map("oldId" -> oldLane.id,
            "oldLaneNumber" -> oldLane.laneCode,
            "oldLaneType" -> laneType) ++ generateCommonFieldsWithDiffSource(oldLane,
            expiredAt = oldLane.expiredDateTime.map(DateTimePropertyFormat.print(_)), expiredBy = oldLane.expiredBy)
        case LaneChangeType.Add =>
          Map("newId" -> newLane.id,
            "newLaneNumber" -> newLane.laneCode,
            "newLaneType" -> laneType) ++ generateCommonFieldsWithDiffSource(newLane)

      }

     Seq(Map("type" -> "Feature",
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
        "roadEndAddr" -> changedSegment.endAddrM) ++ segmentModificationMap)
    }

    Map("type" -> "FeatureCollection",
      "features" -> laneChanges.map{ laneChange =>
        val lane = laneChange.lane
        val laneAttributes = lane.attributes

        val startDate = decodePropertyValue(laneService.getPropertyValue(laneAttributes, "start_date"))
        val endDate = decodePropertyValue(laneService.getPropertyValue(laneAttributes, "end_date"))

        val laneType = laneService.getPropertyValue(laneAttributes, "lane_type")

        val commonJson = Map("type" -> "Feature",
          "changeType" -> {laneChange.changeType match {
            case LaneChangeType.Add => "Add"
            case LaneChangeType.Expired => "Expire"
            case _ => "Modify"
          }},
          "geometry" -> getGeometryMap(laneChange.roadLink.get),
          "startDate" -> startDate,
          "endDate" -> endDate,
          "linkId" -> lane.linkId,
          "startMeasure" -> lane.startMeasure,
          "endMeasure" -> lane.endMeasure) ++ mapLaneAddressInfo(lane, laneChange.roadLink.get)

        val json = laneChange.changeType match {
          case LaneChangeType.Add =>
            Seq(Map("newId" -> lane.id,
              "newLaneNumber" -> lane.laneCode,
              "newLaneType" -> laneType)
              ++ generateCommonFieldsWithDiffSource(lane))

          case LaneChangeType.Expired =>
            Seq(Map("oldId" -> lane.id,
              "oldLaneNumber" -> lane.laneCode,
              "oldLaneType" -> laneType)
              ++ generateCommonFieldsWithDiffSource(lane, expiredAt = lane.expiredDateTime.map(DateTimePropertyFormat.print(_)), expiredBy = lane.expiredBy))

          case LaneChangeType.LaneCodeTransfer | LaneChangeType.AttributesChanged =>
            val oldLane = laneChange.oldLane.get
            Seq(generateCommonModifiedFields(lane, oldLane) ++
              generateCommonFieldsWithDiffSource(lane, modifiedAt = lane.modifiedDateTime.map(DateTimePropertyFormat.print(_)), modifiedBy = lane.modifiedBy))

          case LaneChangeType.Divided =>
            val oldLane = laneChange.oldLane.get
            Seq(generateCommonModifiedFields(lane, oldLane) ++
              generateCommonFieldsWithDiffSource(oldLane, modifiedAt = lane.createdDateTime.map(DateTimePropertyFormat.print(_)), modifiedBy = lane.createdBy))

          case LaneChangeType.Shortened | LaneChangeType.Lengthened =>
            val oldLane = laneChange.oldLane.get
            val oldLaneType = laneService.getPropertyValue(oldLane.attributes, "lane_type")

            val (startSegmentChange, endSegmentChange) = getChangedSegmentMeasures(laneChange)
            val sameSegmentLaneAddressInfo = mapLaneAddressInfo(lane, laneChange.roadLink.get)

            val startSegmentMap = startSegmentChange match {
              case Some(segment: ChangedSegment) => generateShortenedOrLengthenedSegmentFields(segment, laneChange, sameSegmentLaneAddressInfo)
              case None => Seq()
            }

            val endSegmentMap = endSegmentChange match {
              case Some(segment: ChangedSegment) => generateShortenedOrLengthenedSegmentFields(segment, laneChange, sameSegmentLaneAddressInfo)
              case None => Seq()
            }

            val sameSegmentMap = Seq(Map("type" -> "Feature",
              "changeType" -> "Modify",
              "geometry" -> getGeometryMap(laneChange.roadLink.get),
              "startDate" -> startDate,
              "endDate" -> endDate,
              "linkId" -> lane.linkId,
              "startMeasure" -> lane.startMeasure,
              "endMeasure" -> lane.endMeasure,
              "oldId" -> oldLane.id,
              "oldLaneNumber" -> oldLane.laneCode,
              "oldLaneType" -> oldLaneType,
              "newId" -> lane.id,
              "newLaneNumber" -> lane.laneCode,
              "newLaneType" -> laneType) ++ sameSegmentLaneAddressInfo ++
              generateCommonFieldsWithDiffSource(oldLane, modifiedAt = lane.createdDateTime.map(DateTimePropertyFormat.print(_)), modifiedBy = lane.createdBy))

              sameSegmentMap ++ startSegmentMap ++ endSegmentMap

          case _ => Seq()
        }

        if(laneChange.changeType == LaneChangeType.Shortened | laneChange.changeType == LaneChangeType.Lengthened){
          json
        }else{
          json.map(_ ++ commonJson)
        }
      }
    )
  }
}
