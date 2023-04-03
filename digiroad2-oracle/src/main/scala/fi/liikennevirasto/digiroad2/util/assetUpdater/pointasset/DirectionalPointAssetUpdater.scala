package fi.liikennevirasto.digiroad2.util.assetUpdater.pointasset

import fi.liikennevirasto.digiroad2.{FloatingReason, PersistedPointAsset, Point, PointAssetOperations}
import fi.liikennevirasto.digiroad2.asset.{SideCode, TrafficDirection}
import fi.liikennevirasto.digiroad2.asset.TrafficDirection.toSideCode
import fi.liikennevirasto.digiroad2.client.{ReplaceInfo, RoadLinkInfo}

class DirectionalPointAssetUpdater(service: PointAssetOperations) extends PointAssetUpdater(service: PointAssetOperations) {
  override def shouldFloat(asset: PersistedPointAsset, replaceInfo: ReplaceInfo,
                           newLink: Option[RoadLinkInfo]): (Boolean, Option[FloatingReason]) = {
    newLink match {
      case Some(link) if !directionMatches(asset.getValidityDirection, link.trafficDirection, replaceInfo.digitizationChange) =>
        (true, Some(FloatingReason.TrafficDirectionNotMatch))
      case _ => super.shouldFloat(asset, replaceInfo, newLink)
    }
  }

  private def directionMatches(assetDirection: Option[Int], linkDirection: TrafficDirection,
                               digitizationChanged: Boolean): Boolean = {
    val direction = SideCode.apply(assetDirection.getOrElse(SideCode.Unknown.value))
    val validToOneDirection = List(SideCode.AgainstDigitizing, SideCode.TowardsDigitizing).contains(direction)
    val oneWayLink = linkDirection.isOneWay
    val directionsMatch = if (digitizationChanged) direction != toSideCode(linkDirection) else direction == toSideCode(linkDirection)
    !validToOneDirection || !oneWayLink || directionsMatch
  }

  override def adjustValidityDirection(assetDirection: Option[Int], digitizationChange: Boolean): Option[Int] = {
    if (assetDirection.isEmpty) return None
    (SideCode.apply(assetDirection.get), digitizationChange) match {
      case (SideCode.TowardsDigitizing, true) => Some(SideCode.AgainstDigitizing.value)
      case (SideCode.AgainstDigitizing, true) => Some(SideCode.TowardsDigitizing.value)
      case _ => assetDirection
    }
  }

  override def calculateBearing(point: Point, geometry: Seq[Point]): Option[Int] = {
    Some(PointAssetOperations.calculateBearing(point, geometry))
  }
}