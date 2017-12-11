package fi.liikennevirasto.digiroad2.service.pointasset

import fi.liikennevirasto.digiroad2.PointAssetFiller.AssetAdjustment
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.{BoundingRectangle, LinkGeomSource}
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.dao.pointasset.{OracleTrafficLightDao, TrafficLight, TrafficLightToBePersisted}
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.user.User

case class IncomingTrafficLight(lon: Double, lat: Double, linkId: Long) extends IncomingPointAsset

class TrafficLightService(val roadLinkService: RoadLinkService) extends PointAssetOperations {
  type IncomingAsset = IncomingTrafficLight
  type PersistedAsset = TrafficLight

  override def typeId: Int = 280

  override def update(id: Long, updatedAsset: IncomingAsset, geometry: Seq[Point], municipality: Int, username: String, linkSource: LinkGeomSource): Long = {
    val assetPoint = Point(updatedAsset.lon, updatedAsset.lat, 0)
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(assetPoint, geometry)
    val point = GeometryUtils.calculatePointFromLinearReference(geometry, mValue).getOrElse(assetPoint)
    withDynTransaction {
      OracleTrafficLightDao.update(id, TrafficLightToBePersisted(updatedAsset.linkId, point.x, point.y, mValue, municipality, username), Some(VVHClient.createVVHTimeStamp()), linkSource)
    }
    id
  }

  override def setFloating(persistedAsset: TrafficLight, floating: Boolean) = {
    persistedAsset.copy(floating = floating)
  }

  override def fetchPointAssets(queryFilter: (String) => String, roadLinks: Seq[RoadLinkLike]): Seq[TrafficLight] = {
    OracleTrafficLightDao.fetchByFilter(queryFilter)
  }

  override def create(asset: IncomingAsset, username: String, roadLink: RoadLink): Long = {
    val assetPoint = Point(asset.lon, asset.lat, 0)
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(assetPoint, roadLink.geometry)
    val point = GeometryUtils.calculatePointFromLinearReference(roadLink.geometry, mValue).getOrElse(assetPoint)
    withDynTransaction {
      OracleTrafficLightDao.create(TrafficLightToBePersisted(asset.linkId, asset.lon, point.y, mValue, roadLink.municipalityCode, username), username, VVHClient.createVVHTimeStamp(), roadLink.linkSource)
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
    OracleTrafficLightDao.update(adjustment.assetId, TrafficLightToBePersisted(adjustment.linkId,
      adjustment.lon, adjustment.lat, adjustment.mValue, persistedAsset.municipalityCode, "vvh_generated"), Some(adjustment.vvhTimeStamp), persistedAsset.linkSource)
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
