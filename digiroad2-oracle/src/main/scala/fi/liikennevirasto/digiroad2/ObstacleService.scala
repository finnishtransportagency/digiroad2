package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.PointAssetFiller.AssetAdjustment
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, BoundingRectangle}
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.pointasset.oracle._
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

  override def getByBoundingBox(user: User, bounds: BoundingRectangle): Seq[PersistedAsset] = {
    case class AssetBeforeUpdate(asset: PersistedAsset, persistedFloating: Boolean, floatingReason: Option[FloatingReason])
    val (roadLinks, changeInfo) = roadLinkService.getRoadLinksAndChangesFromVVH(bounds)

    withDynTransaction {
      val boundingBoxFilter = OracleDatabase.boundingBoxFilter(bounds, "a.geometry")
      val filter = s"where a.asset_type_id = $typeId and $boundingBoxFilter"
      val persistedAssets: Seq[PersistedAsset] = fetchPointAssets(withFilter(filter), roadLinks)

      val assetsBeforeUpdate: Seq[AssetBeforeUpdate] = persistedAssets.filter { persistedAsset =>
        user.isAuthorizedToRead(persistedAsset.municipalityCode)
      }.map { (persistedAsset: PersistedAsset) =>
        val (floating, assetFloatingReason) = super.isFloating(persistedAsset, roadLinks.find(_.linkId == persistedAsset.linkId))

        if (floating || persistedAsset.floating) {
          PointAssetFiller.correctedPersistedAsset(persistedAsset, roadLinks, changeInfo) match {
            case Some(obstacle) =>
              val updated = IncomingObstacle(obstacle.lon, obstacle.lat, obstacle.linkId, persistedAsset.obstacleType)
              OracleObstacleDao.update(obstacle.assetId, updated, obstacle.mValue, "vvh_generated", persistedAsset.municipalityCode)

              AssetBeforeUpdate(new PersistedAsset(obstacle.assetId, obstacle.linkId, obstacle.lon, obstacle.lat,
                obstacle.mValue, obstacle.floating, persistedAsset.municipalityCode, persistedAsset.obstacleType, persistedAsset.createdBy,
                persistedAsset.createdAt, persistedAsset.modifiedBy, persistedAsset.modifiedAt), obstacle.floating, Some(FloatingReason.Unknown))

            case None => {
              if (floating && !persistedAsset.floating) {
                val logger = LoggerFactory.getLogger(getClass)
                val floatingReasonMessage = floatingReason(persistedAsset, roadLinks.find(_.linkId == persistedAsset.linkId))
                logger.info("Floating asset %d, reason: %s".format(persistedAsset.id, floatingReasonMessage))
              }
              AssetBeforeUpdate(setFloating(persistedAsset, floating), persistedAsset.floating, assetFloatingReason)
            }
          }
        }
        else
          AssetBeforeUpdate(setFloating(persistedAsset, floating), persistedAsset.floating, assetFloatingReason)
      }

      assetsBeforeUpdate.foreach { asset =>
        if (asset.asset.floating != asset.persistedFloating) {
          updateFloating(asset.asset.id, asset.asset.floating, asset.floatingReason)
        }
      }

      assetsBeforeUpdate.map(_.asset)
    }
  }

  override def getByMunicipality(municipalityCode: Int): Seq[PersistedAsset] = {
    val (roadLinks, changeInfo) = roadLinkService.getRoadLinksAndChangesFromVVH(municipalityCode)

    def linkIdToRoadLink(linkId: Long): Option[RoadLinkLike] =
      roadLinks.map(l => l.linkId -> l).toMap.get(linkId)

    withDynTransaction {
      fetchPointAssets(withMunicipality(municipalityCode))
        .map(withFloatingUpdate(convertPersistedAsset(setFloating, linkIdToRoadLink, changeInfo, roadLinks)))
        .toList
    }
  }

  def convertPersistedAsset[T](conversion: (PersistedAsset, Boolean) => T,
                               linkIdToRoadLink: (Long) => Option[RoadLinkLike],
                               changeInfo: Seq[ChangeInfo], roadLinks: Seq[RoadLink])
                              (persistedStop: PersistedAsset): (T, Option[FloatingReason]) = {

    val (floating, assetFloatingReason) = isFloating(persistedStop, linkIdToRoadLink(persistedStop.linkId))
    if (floating) {
      PointAssetFiller.correctedPersistedAsset(persistedStop, roadLinks, changeInfo) match {
        case Some(obstacle) =>
          val updated = IncomingObstacle(obstacle.lon, obstacle.lat, obstacle.linkId, persistedStop.obstacleType)
          OracleObstacleDao.update(obstacle.assetId, updated, obstacle.mValue, "vvh_generated", persistedStop.municipalityCode)

          val persistedAsset = new PersistedAsset(obstacle.assetId, obstacle.linkId, obstacle.lon, obstacle.lat,
            obstacle.mValue, obstacle.floating, persistedStop.municipalityCode, persistedStop.obstacleType, persistedStop.createdBy,
            persistedStop.createdAt, persistedStop.modifiedBy, persistedStop.modifiedAt)

          (conversion(persistedAsset, persistedAsset.floating), assetFloatingReason)

        case None => (conversion(persistedStop, floating), assetFloatingReason)
      }
    }
    else
      (conversion(persistedStop, floating), assetFloatingReason)
  }

  def getFloatingObstacles(floating: Int, lastIdUpdate: Long, batchSize: Int): Seq[Obstacle] = {
    OracleObstacleDao.selectFloatings(floating, lastIdUpdate, batchSize)
  }

  def updateFloatingAsset(obstacleUpdated: Obstacle) = {
    OracleObstacleDao.updateFloatingAsset(obstacleUpdated)
  }
}


