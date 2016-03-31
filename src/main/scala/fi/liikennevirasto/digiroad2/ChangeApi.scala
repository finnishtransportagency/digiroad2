package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.Digiroad2Context._
import fi.liikennevirasto.digiroad2.asset.Asset._
import fi.liikennevirasto.digiroad2.linearasset.{PieceWiseLinearAsset, SpeedLimit}
import org.joda.time.DateTime
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.ScalatraServlet
import org.scalatra.json.JacksonJsonSupport


class ChangeApi extends ScalatraServlet with JacksonJsonSupport with AuthenticationSupport {
  protected implicit val jsonFormats: Formats = DefaultFormats

  before() {
    basicAuth
    contentType = formats("json")
  }

  get("/:assetType") {
    contentType = formats("json")
    val since = DateTime.parse(params("since"))
    params("assetType") match {
      case "speed_limits"                => speedLimitsToGeoJson(since, speedLimitService.getChanged(since))
      case "total_weight_limits"         => linearAssetsToGeoJson(since, linearAssetService.getChanged(30, since))
      case "trailer_truck_weight_limits" => linearAssetsToGeoJson(since, linearAssetService.getChanged(40, since))
      case "axle_weight_limits"          => linearAssetsToGeoJson(since, linearAssetService.getChanged(50, since))
      case "bogie_weight_limits"         => linearAssetsToGeoJson(since, linearAssetService.getChanged(60, since))
      case "height_limits"               => linearAssetsToGeoJson(since, linearAssetService.getChanged(70, since))
      case "length_limits"               => linearAssetsToGeoJson(since, linearAssetService.getChanged(80, since))
      case "width_limits"                => linearAssetsToGeoJson(since, linearAssetService.getChanged(90, since))
    }
  }

  private def speedLimitsToGeoJson(since: DateTime, speedLimits: Seq[ChangedSpeedLimit]) =
    Map(
      "type" -> "FeatureCollection",
      "features" ->
        speedLimits.map { case ChangedSpeedLimit(speedLimit, link) =>
          Map(
            "type" -> "Feature",
            "geometry" -> Map(
              "type" -> "LineString",
              "coordinates" -> speedLimit.geometry.map(p => Seq(p.x, p.y, p.z))
            ),
            "properties" ->
              Map("id" -> speedLimit.id,
                "value" -> speedLimit.value.map(_.toJson),
                "link" -> Map(
                  "id" -> link.linkId,
                  "type" -> "Feature",
                  "geometry" -> Map(
                    "type" -> "LineString",
                    "coordinates" -> link.geometry.map(p => Seq(p.x, p.y, p.z))
                  ),
                  "properties" -> Map(
                    "functionalClass" -> link.functionalClass,
                    "type" -> link.linkType.value
                  )
                ),
                "sideCode" -> speedLimit.sideCode.value,
                "startMeasure" -> speedLimit.startMeasure,
                "endMeasure" -> speedLimit.endMeasure,
                "createdBy" -> speedLimit.createdBy,
                "modifiedAt" -> speedLimit.modifiedDateTime.map(DateTimePropertyFormat.print(_)),
                "createdAt" -> speedLimit.createdDateTime.map(DateTimePropertyFormat.print(_)),
                "modifiedBy" -> speedLimit.modifiedBy,
                "changeType" -> extractSpeedLimitChangeType(since, speedLimit)
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
            "geometry" -> Map(
              "type" -> "LineString",
              "coordinates" -> linearAsset.geometry.map(p => Seq(p.x, p.y, p.z))
            ),
            "properties" ->
              Map("id" -> linearAsset.id,
                "value" -> linearAsset.value.map(_.toJson),
                "link" -> Map(
                  "id" -> link.linkId,
                  "type" -> "Feature",
                  "geometry" -> Map(
                    "type" -> "LineString",
                    "coordinates" -> link.geometry.map(p => Seq(p.x, p.y, p.z))
                  ),
                  "properties" -> Map(
                    "functionalClass" -> link.functionalClass,
                    "type" -> link.linkType.value
                  )
                ),
                "sideCode" -> linearAsset.sideCode.value,
                "startMeasure" -> linearAsset.startMeasure,
                "endMeasure" -> linearAsset.endMeasure,
                "createdBy" -> linearAsset.createdBy,
                "modifiedAt" -> linearAsset.modifiedDateTime.map(DateTimePropertyFormat.print(_)),
                "createdAt" -> linearAsset.createdDateTime.map(DateTimePropertyFormat.print(_)),
                "modifiedBy" -> linearAsset.modifiedBy,
                "changeType" -> extractLinearAssetChangeType(since, linearAsset)
              )
          )
        }
    )

  private def extractSpeedLimitChangeType(since: DateTime, speedLimit: SpeedLimit): String =
    if (speedLimit.createdDateTime.exists(_.isAfter(since)))
      "Add"
    else
      "Update"

  private def extractLinearAssetChangeType(since: DateTime, asset: PieceWiseLinearAsset) = {
    if (asset.expired) {
      "Remove"
    } else if (asset.createdDateTime.exists(_.isAfter(since))) {
      "Add"
    } else {
      "Update"
    }
  }
}
