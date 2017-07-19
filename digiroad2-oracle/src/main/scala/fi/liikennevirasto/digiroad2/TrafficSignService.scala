package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.PointAssetFiller.AssetAdjustment
import fi.liikennevirasto.digiroad2.asset.{BoundingRectangle, LinkGeomSource, AdministrativeClass}
import fi.liikennevirasto.digiroad2.linearasset.RoadLinkLike
import fi.liikennevirasto.digiroad2.pointasset.oracle.{OracleObstacleDao, OracleTrafficSignDao, TrafficSign}
import fi.liikennevirasto.digiroad2.user.User

case class IncomingTrafficSign(lon: Double, lat: Double, linkId: Long, signType: Int, value: Option[String], additionalInfo: Option[String]) extends IncomingPointAsset

class TrafficSignService(val roadLinkService: RoadLinkService) extends PointAssetOperations {
  type IncomingAsset = IncomingTrafficSign
  type PersistedAsset = TrafficSign

  override def typeId: Int = 300

  override def fetchPointAssets(queryFilter: String => String, roadLinks: Seq[RoadLinkLike]): Seq[TrafficSign] = OracleTrafficSignDao.fetchByFilter(queryFilter)

  override def setFloating(persistedAsset: TrafficSign, floating: Boolean) = {
    persistedAsset.copy(floating = floating)
  }

  override def create(asset: IncomingTrafficSign, username: String, geometry: Seq[Point], municipality: Int, administrativeClass: Option[AdministrativeClass] = None, linkSource: LinkGeomSource): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(asset.lon, asset.lat, 0), geometry)
    withDynTransaction {
      OracleTrafficSignDao.create(asset, mValue, username, municipality, VVHClient.createVVHTimeStamp(), linkSource)
    }
  }

  override def update(id: Long, updatedAsset: IncomingTrafficSign, geometry: Seq[Point], municipality: Int, username: String, linkSource: LinkGeomSource): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(updatedAsset.lon, updatedAsset.lat, 0), geometry)
    withDynTransaction {
      OracleTrafficSignDao.update(id, updatedAsset, mValue, municipality, username, Some(VVHClient.createVVHTimeStamp()), linkSource)
    }
    id
  }

  override def getByBoundingBox(user: User, bounds: BoundingRectangle): Seq[PersistedAsset] = {
    val (roadLinks, changeInfo) = roadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(bounds)
    super.getByBoundingBox(user, bounds, roadLinks, changeInfo, floatingAdjustment(adjustmentOperation, createOperation))
  }

  private def createOperation(asset: PersistedAsset, adjustment: AssetAdjustment): PersistedAsset = {
    createPersistedAsset(asset, adjustment)
  }

  private def adjustmentOperation(persistedAsset: PersistedAsset, adjustment: AssetAdjustment): Long = {
    val updated = IncomingTrafficSign(adjustment.lon, adjustment.lat, adjustment.linkId, persistedAsset.signType, persistedAsset.value, persistedAsset.additionalInfo)
    OracleTrafficSignDao.update(adjustment.assetId, updated, adjustment.mValue, persistedAsset.municipalityCode, "vvh_generated", Some(adjustment.vvhTimeStamp), persistedAsset.linkSource)
  }

  override def getByMunicipality(municipalityCode: Int): Seq[PersistedAsset] = {
    val (roadLinks, changeInfo) = roadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(municipalityCode)
    val mapRoadLinks = roadLinks.map(l => l.linkId -> l).toMap
    getByMunicipality(municipalityCode, mapRoadLinks, roadLinks, changeInfo, floatingAdjustment(adjustmentOperation, createOperation))
  }

  private def createPersistedAsset[T](persistedStop: PersistedAsset, asset: AssetAdjustment) = {
    new PersistedAsset(asset.assetId, asset.linkId, asset.lon, asset.lat,
      asset.mValue, asset.floating, persistedStop.vvhTimeStamp, persistedStop.municipalityCode, persistedStop.signType, persistedStop.value, persistedStop.additionalInfo, persistedStop.createdBy,
      persistedStop.createdAt, persistedStop.modifiedBy, persistedStop.modifiedAt, persistedStop.linkSource)
  }

}
