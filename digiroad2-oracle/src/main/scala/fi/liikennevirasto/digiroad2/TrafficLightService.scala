package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.PointAssetFiller.AssetAdjustment
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, BoundingRectangle, LinkGeomSource}
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.pointasset.oracle.{OracleTrafficLightDao, TrafficLight, TrafficLightToBePersisted}
import fi.liikennevirasto.digiroad2.user.User
import org.slf4j.LoggerFactory

case class IncomingTrafficLight(lon: Double, lat: Double, linkId: Long) extends IncomingPointAsset

class TrafficLightService(val roadLinkService: RoadLinkService) extends PointAssetOperations {
  type IncomingAsset = IncomingTrafficLight
  type PersistedAsset = TrafficLight

  override def typeId: Int = 280

  override def setAssetPosition(asset: IncomingTrafficLight, geometry: Seq[Point], mValue: Double): IncomingTrafficLight = {
    GeometryUtils.calculatePointFromLinearReference(geometry, mValue) match {
      case Some(point) =>
        asset.copy(lon = point.x, lat = point.y)
      case _ =>
        asset
    }
  }

  override def update(id: Long, updatedAsset: IncomingTrafficLight, geometry: Seq[Point], municipality: Int, username: String, linkSource: LinkGeomSource): Long = {
    val oldAsset = getById(id)
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(updatedAsset.lon, updatedAsset.lat, 0), geometry)
    withDynTransaction {
      oldAsset match {
        case Some(old) if  old.lat != updatedAsset.lat || old.lon != updatedAsset.lon =>
          updateExpiration(id, expired = true, username)
          OracleTrafficLightDao.create(setAssetPosition(updatedAsset, geometry, mValue), mValue, username, municipality, VVHClient.createVVHTimeStamp(), linkSource)
        case _ =>
          OracleTrafficLightDao.update(id, setAssetPosition(updatedAsset, geometry, mValue), mValue, username, municipality, Some(VVHClient.createVVHTimeStamp()), linkSource)
          id
      }
    }
  }

  override def setFloating(persistedAsset: TrafficLight, floating: Boolean) = {
    persistedAsset.copy(floating = floating)
  }

  override def fetchPointAssets(queryFilter: (String) => String, roadLinks: Seq[RoadLinkLike]): Seq[TrafficLight] = {
    OracleTrafficLightDao.fetchByFilter(queryFilter)
  }

  override def create(asset: IncomingTrafficLight, username: String, roadLink: RoadLink): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(asset.lon, asset.lat, 0), roadLink.geometry)
    withDynTransaction {
      OracleTrafficLightDao.create(setAssetPosition(asset, roadLink.geometry, mValue), mValue, username, roadLink.municipalityCode, VVHClient.createVVHTimeStamp(), roadLink.linkSource)
    }
  }

  override def getByBoundingBox(user: User, bounds: BoundingRectangle) : Seq[PersistedAsset] = {
    val (roadLinks, changeInfo) = roadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(bounds)
    super.getByBoundingBox(user, bounds, roadLinks, changeInfo, floatingAdjustment(adjustmentOperation, createOperation))
  }

  private def createOperation(asset: PersistedAsset, adjustment: AssetAdjustment): PersistedAsset = {
    createPersistedAsset(asset, adjustment)
  }

  private def adjustmentOperation(persistedAsset: PersistedAsset, adjustment: AssetAdjustment): Long = {
    val updated = IncomingTrafficLight(adjustment.lon, adjustment.lat, adjustment.linkId)
    OracleTrafficLightDao.update(adjustment.assetId, updated, adjustment.mValue, "vvh_generated", persistedAsset.municipalityCode, Some(adjustment.vvhTimeStamp), persistedAsset.linkSource)
  }

  override def getByMunicipality(municipalityCode: Int): Seq[PersistedAsset] = {
    val (roadLinks, changeInfo) = roadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(municipalityCode)
    val mapRoadLinks = roadLinks.map(l => l.linkId -> l).toMap
    getByMunicipality(municipalityCode, mapRoadLinks, roadLinks, changeInfo, floatingAdjustment(adjustmentOperation, createOperation))
  }

  private def createPersistedAsset[T](persistedStop: PersistedAsset, asset: AssetAdjustment) = {

    new PersistedAsset(asset.assetId, asset.linkId, asset.lon, asset.lat,
      asset.mValue, asset.floating, persistedStop.vvhTimeStamp, persistedStop.municipalityCode, persistedStop.createdBy,
      persistedStop.createdAt, persistedStop.modifiedBy, persistedStop.modifiedAt, persistedStop.linkSource)
  }
}
