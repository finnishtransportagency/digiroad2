package fi.liikennevirasto.digiroad2.util.assetUpdater.pointasset

import fi.liikennevirasto.digiroad2.LocationSpecifier.OnRoadOrStreetNetwork
import fi.liikennevirasto.digiroad2.asset.PropertyValue
import fi.liikennevirasto.digiroad2.asset.SideCode.DoesNotAffectRoadLink
import fi.liikennevirasto.digiroad2.client.RoadLinkInfo
import fi.liikennevirasto.digiroad2.{AssetUpdate, FloatingReason, GeometryUtils, PersistedPointAsset, Point, PointAssetOperations}

import scala.util.Try

class TrafficSignUpdater(service: PointAssetOperations) extends DirectionalPointAssetUpdater(service: PointAssetOperations) {

  override protected def snapAssetToNewLink(asset: PersistedPointAsset, newLink: RoadLinkInfo, oldLink: RoadLinkInfo,
                                 digitizationChange: Boolean): AssetUpdate = {
    if (isVersionChange(asset.linkId, newLink.linkId) && newLink.linkLength == oldLink.linkLength) {
      val fixedValidityDirection = if (isOutsideRoadNetwork(asset)) Some(DoesNotAffectRoadLink.value) else asset.getValidityDirection
      toAssetUpdate(asset.id, Point(asset.lon, asset.lat), newLink.linkId, asset.mValue,
        fixedValidityDirection, asset.getBearing)
    } else {
      val oldLocation = Point(asset.lon, asset.lat)
      val newMValue = GeometryUtils.calculateLinearReferenceFromPoint(oldLocation, newLink.geometry)
      val newPoint = GeometryUtils.calculatePointFromLinearReference(newLink.geometry, newMValue)
      newPoint match {
        case Some(point) if point.distance2DTo(oldLocation) <= MaxDistanceDiffAllowed =>
          val calculatedBearing = if (isOutsideRoadNetwork(asset)) asset.getBearing else calculateBearing(point, newLink.geometry)
          val fixedValidityDirection = if (isOutsideRoadNetwork(asset)) Some(DoesNotAffectRoadLink.value) else adjustValidityDirection(asset.getValidityDirection, digitizationChange)
          toAssetUpdate(asset.id, point, newLink.linkId, newMValue, fixedValidityDirection, calculatedBearing)
        case _ =>
          setAssetAsFloating(asset, Some(FloatingReason.DistanceToRoad))
      }
    }
  }

  private def isOutsideRoadNetwork(asset: PersistedPointAsset): Boolean = {
    Try(service.getProperty(asset.propertyData, "location_specifier")).getOrElse(None) match {
      case Some(value: PropertyValue) =>
        Try(value.propertyValue.toInt).getOrElse(None) == OnRoadOrStreetNetwork.value
      case _ =>
        false
    }
  }
}
