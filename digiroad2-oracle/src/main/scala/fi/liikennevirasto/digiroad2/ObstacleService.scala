package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.PointAssetFiller.AssetAdjustment
import fi.liikennevirasto.digiroad2.asset.{BoundingRectangle, LinkGeomSource}
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.pointasset.oracle._
import fi.liikennevirasto.digiroad2.user.User

case class IncomingObstacle(lon: Double, lat: Double, linkId: Long, obstacleType: Int) extends IncomingPointAsset

class ObstacleService(val roadLinkService: RoadLinkService) extends PointAssetOperations {
  type IncomingAsset = IncomingObstacle
  type PersistedAsset = Obstacle

  override def typeId: Int = 220

  override def fetchPointAssets(queryFilter: String => String, roadLinks: Seq[RoadLinkLike]): Seq[Obstacle] = OracleObstacleDao.fetchByFilter(queryFilter)

  override def setFloating(persistedAsset: Obstacle, floating: Boolean) = {
    persistedAsset.copy(floating = floating)
  }

  override def setAssetPosition(asset: IncomingObstacle, geometry: Seq[Point], mValue: Double): IncomingObstacle = {
    GeometryUtils.calculatePointFromLinearReference(geometry, mValue) match {
      case Some(point) =>
        asset.copy(lon = point.x, lat = point.y)
      case _ =>
        asset
    }
  }

  override def create(asset: IncomingObstacle, username: String, roadLink: RoadLink): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(asset.lon, asset.lat, 0), roadLink.geometry)
    withDynTransaction {
      OracleObstacleDao.create(setAssetPosition(asset, roadLink.geometry, mValue), mValue, username, roadLink.municipalityCode, VVHClient.createVVHTimeStamp(), roadLink.linkSource)
    }
  }

  override def update(id: Long, updatedAsset: IncomingObstacle, geometry: Seq[Point], municipality: Int, username: String, linkSource: LinkGeomSource): Long = {
    withDynTransaction {
      updateWithoutTransaction(id, updatedAsset, geometry, municipality, username, linkSource)
    }
  }

  def updateWithoutTransaction(id: Long, updatedAsset: IncomingObstacle, geometry: Seq[Point], municipality: Int, username: String, linkSource: LinkGeomSource): Long = {
    val oldAsset = getPersistedAssetsByIdsWithoutTransaction(Set(id)).headOption
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(updatedAsset.lon, updatedAsset.lat, 0), geometry)
    oldAsset match {
      case Some(old) if old.lat != updatedAsset.lat || old.lon != updatedAsset.lon =>
        expireWihoutTransaction(id, username)
        OracleObstacleDao.create(setAssetPosition(updatedAsset, geometry, mValue), mValue, username, municipality, VVHClient.createVVHTimeStamp(), linkSource)
      case _ =>
        OracleObstacleDao.update(id, setAssetPosition(updatedAsset, geometry, mValue), mValue, username, municipality, Some(VVHClient.createVVHTimeStamp()), linkSource)
        id

    }
  }

  override def getByBoundingBox(user: User, bounds: BoundingRectangle) : Seq[PersistedAsset] = {
    val (roadLinks, changeInfo) = roadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(bounds)
    super.getByBoundingBox(user, bounds, roadLinks, changeInfo, floatingAdjustment(adjustmentOperation, createOperation))
  }

  private def createOperation(asset: PersistedAsset, adjustment: AssetAdjustment): PersistedAsset = {
    createPersistedAsset(asset, adjustment)
  }

  private def adjustmentOperation(persistedAsset: PersistedAsset, adjustment: AssetAdjustment, roadLink: RoadLink): Long = {
    val updated = IncomingObstacle(adjustment.lon, adjustment.lat, adjustment.linkId, persistedAsset.obstacleType)
    updateWithoutTransaction(adjustment.assetId, updated, roadLink.geometry, persistedAsset.municipalityCode, "vvh_generated", persistedAsset.linkSource)
  }

  override def getByMunicipality(municipalityCode: Int): Seq[PersistedAsset] = {
    val (roadLinks, changeInfo) = roadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(municipalityCode)
    val mapRoadLinks = roadLinks.map(l => l.linkId -> l).toMap
    getByMunicipality(municipalityCode, mapRoadLinks, roadLinks, changeInfo, floatingAdjustment(adjustmentOperation, createOperation))
  }

  private def createPersistedAsset[T](persistedStop: PersistedAsset, asset: AssetAdjustment) = {

    new PersistedAsset(asset.assetId, asset.linkId, asset.lon, asset.lat,
      asset.mValue, asset.floating, persistedStop.vvhTimeStamp, persistedStop.municipalityCode, persistedStop.obstacleType, persistedStop.createdBy,
      persistedStop.createdAt, persistedStop.modifiedBy, persistedStop.modifiedAt, persistedStop.linkSource)

  }

  def getFloatingObstacles(floating: Int, lastIdUpdate: Long, batchSize: Int): Seq[Obstacle] = {
    OracleObstacleDao.selectFloatings(floating, lastIdUpdate, batchSize)
  }

  def updateFloatingAsset(obstacleUpdated: Obstacle) = {
    OracleObstacleDao.updateFloatingAsset(obstacleUpdated)
  }
}


