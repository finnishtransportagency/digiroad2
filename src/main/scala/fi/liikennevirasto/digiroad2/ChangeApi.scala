package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.Digiroad2Context._
import fi.liikennevirasto.digiroad2.asset.Asset._
import fi.liikennevirasto.digiroad2.linearasset.PieceWiseLinearAsset
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
      case "speed_limits"                => changedSpeedLimitsToApi(since, speedLimitService.getChanged(since))
      case "total_weight_limits"         => changedLinearAssetsToApi(since, linearAssetService.getChanged(30, since))
      case "trailer_truck_weight_limits" => changedLinearAssetsToApi(since, linearAssetService.getChanged(40, since))
      case "axle_weight_limits"          => changedLinearAssetsToApi(since, linearAssetService.getChanged(50, since))
      case "bogie_weight_limits"         => changedLinearAssetsToApi(since, linearAssetService.getChanged(60, since))
      case "height_limits"               => changedLinearAssetsToApi(since, linearAssetService.getChanged(70, since))
      case "length_limits"               => changedLinearAssetsToApi(since, linearAssetService.getChanged(80, since))
      case "width_limits"                => changedLinearAssetsToApi(since, linearAssetService.getChanged(90, since))
    }
  }

  private def changedSpeedLimitsToApi(since: DateTime, speedLimits: Seq[ChangedSpeedLimit]) = {
    val (addedSpeedLimits, updatedSpeedLimits) =
      speedLimits.partition { case ChangedSpeedLimit(speedLimit, link) =>
        speedLimit.createdDateTime match {
          case None => false
          case Some(createdDate) => createdDate.isAfter(since)
        }
      }

    Map("added" -> addedSpeedLimits.map(speedLimitToApi),
      "updated"  -> updatedSpeedLimits.map(speedLimitToApi))
  }

  def extractLinearAssetChangeType(since: DateTime, asset: PieceWiseLinearAsset) = {
    if (asset.expired) {
      "Removed"
    } else if (asset.createdDateTime.map(_.isAfter(since)).getOrElse(false)) {
      "Added"
    } else {
      "Updated"
    }
  }

  private def changedLinearAssetsToApi(since: DateTime, assets: Seq[ChangedLinearAsset]) = {
    assets.map {  case ChangedLinearAsset(asset, link) =>
      Map("id" -> asset.id,
        "geometry" -> asset.geometry,
        "linkGeometry" -> link.geometry,
        "linkFunctionalClass" -> link.functionalClass,
        "linkType" -> link.linkType.value,
        "value" -> asset.value.map(_.toJson),
        "side_code" -> asset.sideCode.value,
        "linkId" -> asset.linkId,
        "startMeasure" -> asset.startMeasure,
        "endMeasure" -> asset.endMeasure,
        "changeType" -> extractLinearAssetChangeType(since, asset))
    }
  }

  private def speedLimitToApi(changedSpeedLimit: ChangedSpeedLimit) = {
    val ChangedSpeedLimit(speedLimit, link) = changedSpeedLimit

    Map("id" -> speedLimit.id,
      "value" -> speedLimit.value,
      "linkId" -> speedLimit.linkId,
      "linkGeometry" -> link.geometry,
      "linkFunctionalClass" -> link.functionalClass,
      "linkType" -> link.linkType.value,
      "sideCode" -> speedLimit.sideCode.value,
      "startMeasure" -> speedLimit.startMeasure,
      "endMeasure" -> speedLimit.endMeasure,
      "geometry" -> speedLimit.geometry,
      "createdBy" -> speedLimit.createdBy,
      "modifiedAt" -> speedLimit.modifiedDateTime.map(DateTimePropertyFormat.print(_)),
      "createdAt" -> speedLimit.createdDateTime.map(DateTimePropertyFormat.print(_)),
      "modifiedBy" -> speedLimit.modifiedBy)
  }
}
