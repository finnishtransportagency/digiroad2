package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.Digiroad2Context._
import fi.liikennevirasto.digiroad2.asset.Asset._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.service.ChangedVVHRoadlink
import fi.liikennevirasto.digiroad2.service.linearasset.{ChangedLinearAsset, ChangedSpeedLimit}
import org.joda.time.DateTime
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.{BadRequest, ScalatraServlet}
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.swagger.{Swagger, SwaggerSupport}


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

  get("/:assetType", operation(getChangesOfAssetsByType)) {
    contentType = formats("json")
    val since = DateTime.parse(params.get("since").getOrElse(halt(BadRequest("Missing mandatory 'since' parameter"))))
    val until = DateTime.parse(params.get("until").getOrElse(halt(BadRequest("Missing mandatory 'until' parameter"))))

    val withAdjust = params.get("withAdjust") match{
      case Some(value)=> true
      case _ => false
    }

    params("assetType") match {
      case "speed_limits"                => speedLimitsToGeoJson(since, speedLimitService.getChanged(since, until, withAdjust))
      case "total_weight_limits"         => linearAssetsToGeoJson(since, linearAssetService.getChanged(TotalWeightLimit.typeId , since, until, withAdjust))
      case "trailer_truck_weight_limits" => linearAssetsToGeoJson(since, linearAssetService.getChanged(TrailerTruckWeightLimit.typeId, since, until, withAdjust))
      case "axle_weight_limits"          => linearAssetsToGeoJson(since, linearAssetService.getChanged(AxleWeightLimit.typeId, since, until, withAdjust))
      case "bogie_weight_limits"         => linearAssetsToGeoJson(since, linearAssetService.getChanged(BogieWeightLimit.typeId, since, until, withAdjust))
      case "height_limits"               => linearAssetsToGeoJson(since, linearAssetService.getChanged(HeightLimit.typeId, since, until, withAdjust))
      case "length_limits"               => linearAssetsToGeoJson(since, linearAssetService.getChanged(LengthLimit.typeId, since, until, withAdjust))
      case "width_limits"                => linearAssetsToGeoJson(since, linearAssetService.getChanged(WidthLimit.typeId, since, until, withAdjust))
      case "road_names"                  => vvhRoadLinkToGeoJson(roadLinkService.getChanged(since, until))
    }
  }

  private def speedLimitsToGeoJson(since: DateTime, speedLimits: Seq[ChangedSpeedLimit]) =
    Map(
      "type" -> "FeatureCollection",
      "features" ->
        speedLimits.map { case ChangedSpeedLimit(speedLimit, link) =>
          Map(
            "type" -> "Feature",
            "id" -> speedLimit.id,
            "geometry" -> Map(
              "type" -> "LineString",
              "coordinates" -> speedLimit.geometry.map(p => Seq(p.x, p.y, p.z))
            ),
            "properties" ->
              Map(
                "value" -> speedLimit.value.map(_.toJson),
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

  private def linearAssetsToGeoJson(since: DateTime, changedLinearAssets: Seq[ChangedLinearAsset]) =
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
              Map(
                "value" -> linearAsset.value.map(_.toJson),
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
