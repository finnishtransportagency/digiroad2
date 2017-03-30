package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.PointAssetFiller.AssetAdjustment
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, BoundingRectangle}
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.pointasset.oracle.{PedestrianCrossing, _}
import fi.liikennevirasto.digiroad2.user.User
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

case class IncomingObstacle(lon: Double, lat: Double, linkId: Long, obstacleType: Int) extends IncomingPointAsset

class ObstacleService(val roadLinkService: RoadLinkService) extends PointAssetOperations {
  type IncomingAsset = IncomingObstacle
  type PersistedAsset = Obstacle

  override def typeId: Int = 220

  override def fetchPointAssets(queryFilter: String => String, roadLinks: Seq[RoadLinkLike]): Seq[Obstacle] = OracleObstacleDao.fetchByFilter(queryFilter)

  override def setFloating(persistedAsset: Obstacle, floating: Boolean) = {
    persistedAsset.copy(floating = floating)
  }

  override def create(asset: IncomingObstacle, username: String, geometry: Seq[Point], municipality: Int, administrativeClass: Option[AdministrativeClass] = None): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(asset.lon, asset.lat, 0), geometry)
    withDynTransaction {
      OracleObstacleDao.create(asset, mValue, username, municipality)
    }
  }

  override def update(id: Long, updatedAsset: IncomingObstacle, geometry: Seq[Point], municipality: Int, username: String): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(updatedAsset.lon, updatedAsset.lat, 0), geometry)
    withDynTransaction {
      OracleObstacleDao.update(id, updatedAsset, mValue, username, municipality)
    }
    id
  }

  override def getByBoundingBox(user: User, bounds: BoundingRectangle) : Seq[PersistedAsset] = {
    val (roadLinks, changeInfo) = roadLinkService.getRoadLinksAndChangesFromVVH(bounds)
    super.getByBoundingBox(user, bounds, roadLinks, changeInfo, floatingAdjustment)
  }

  private def floatingAdjustment(roadLinks: Seq[RoadLink], changeInfo: Seq[ChangeInfo], assetBeforeUpdate: AssetBeforeUpdate) = {
    if (assetBeforeUpdate.persistedFloating || assetBeforeUpdate.asset.floating) {
      PointAssetFiller.correctedPersistedAsset(assetBeforeUpdate.asset, roadLinks, changeInfo) match {
        case Some(asset) =>
          val updated = IncomingObstacle(asset.lon, asset.lat, asset.linkId, assetBeforeUpdate.asset.obstacleType)
          OracleObstacleDao.update(asset.assetId, updated, asset.mValue, "vvh_generated", assetBeforeUpdate.asset.municipalityCode)

          AssetBeforeUpdate(createPersistedAsset(assetBeforeUpdate.asset, asset), asset.floating, Some(FloatingReason.Unknown))

        case None =>
          if (assetBeforeUpdate.persistedFloating && !assetBeforeUpdate.asset.floating) {
            val logger = LoggerFactory.getLogger(getClass)
            val floatingReasonMessage = floatingReason(assetBeforeUpdate.asset, roadLinks.find(_.linkId == assetBeforeUpdate.asset.linkId))
            logger.info("Floating asset %d, reason: %s".format(assetBeforeUpdate.asset.id, floatingReasonMessage))
          }
          AssetBeforeUpdate(setFloating(assetBeforeUpdate.asset, assetBeforeUpdate.persistedFloating), assetBeforeUpdate.asset.floating, assetBeforeUpdate.floatingReason)
      }
    }
    else
      AssetBeforeUpdate(setFloating(assetBeforeUpdate.asset, assetBeforeUpdate.persistedFloating), assetBeforeUpdate.asset.floating, assetBeforeUpdate.floatingReason)
  }

  override def getByMunicipality(municipalityCode: Int): Seq[PersistedAsset] = {
    val (roadLinks, changeInfo) = roadLinkService.getRoadLinksAndChangesFromVVH(municipalityCode)
    val mapRoadLinks = roadLinks.map(l => l.linkId -> l).toMap
    getByMunicipality(municipalityCode, mapRoadLinks, roadLinks, changeInfo, floatingCorrection)
  }

  private def floatingCorrection[T](changeInfo: Seq[ChangeInfo], roadLinks: Seq[RoadLink],
                                    persistedStop: PersistedAsset, floating: Boolean, assetFloatingReason: Option[FloatingReason],
                                    conversion: (PersistedAsset, Boolean) => T) = {
    if (floating) {
      PointAssetFiller.correctedPersistedAsset(persistedStop, roadLinks, changeInfo) match {
        case Some(asset) =>
          val updated = IncomingObstacle(asset.lon, asset.lat, asset.linkId, persistedStop.obstacleType)
          OracleObstacleDao.update(asset.assetId, updated, asset.mValue, "vvh_generated", persistedStop.municipalityCode)

          val persistedAsset = createPersistedAsset(persistedStop, asset)
          (conversion(persistedAsset, persistedAsset.floating), assetFloatingReason)

        case None => (conversion(persistedStop, floating), assetFloatingReason)
      }
    }
    else
      (conversion(persistedStop, floating), assetFloatingReason)

  }

  private def createPersistedAsset[T](persistedStop: PersistedAsset, asset: AssetAdjustment) = {

    new PersistedAsset(asset.assetId, asset.linkId, asset.lon, asset.lat,
      asset.mValue, asset.floating, persistedStop.vvhTimeStamp, persistedStop.municipalityCode, persistedStop.obstacleType, persistedStop.createdBy,
      persistedStop.createdAt, persistedStop.modifiedBy, persistedStop.modifiedAt)

  }

  def getFloatingObstacles(floating: Int, lastIdUpdate: Long, batchSize: Int): Seq[Obstacle] = {
    OracleObstacleDao.selectFloatings(floating, lastIdUpdate, batchSize)
  }

  def updateFloatingAsset(obstacleUpdated: Obstacle) = {
    OracleObstacleDao.updateFloatingAsset(obstacleUpdated)
  }
}


