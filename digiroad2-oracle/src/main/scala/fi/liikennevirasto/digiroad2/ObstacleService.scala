package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.pointasset.oracle._
import org.joda.time.DateTime

case class Obstacle(id: Long, mmlId: Long,
                              lon: Double, lat: Double,
                              mValue: Double, floating: Boolean,
                              municipalityCode: Int,
                              obstacleType: Int,
                              createdBy: Option[String] = None,
                              createdAt: Option[DateTime] = None,
                              modifiedBy: Option[String] = None,
                              modifiedAt: Option[DateTime] = None) extends FloatingAsset

class ObstacleService(val vvhClient: VVHClient) extends PointAssetOperations {
  type NewAsset = NewObstacle
  type Asset = Obstacle
  type PersistedAsset = PersistedObstacle

  override def typeId: Int = 220
  override def fetchPointAssets(queryFilter: String => String): Seq[PersistedObstacle] = OracleObstacleDao.fetchByFilter(queryFilter)
  override def persistedAssetToAsset(persistedAsset: PersistedObstacle, floating: Boolean) = {
    Obstacle(
      id = persistedAsset.id,
      mmlId = persistedAsset.mmlId,
      municipalityCode = persistedAsset.municipalityCode,
      lon = persistedAsset.lon,
      lat = persistedAsset.lat,
      mValue = persistedAsset.mValue,
      floating = floating,
      obstacleType = persistedAsset.obstacleType,
      createdBy = persistedAsset.createdBy,
      createdAt = persistedAsset.createdDateTime,
      modifiedBy = persistedAsset.modifiedBy,
      modifiedAt = persistedAsset.modifiedDateTime)
  }

  def create(asset: NewObstacle, username: String, geometry: Seq[Point], municipality: Int): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(asset.lon, asset.lat, 0), geometry)
    withDynTransaction {
      OracleObstacleDao.create(ObstacleToBePersisted(asset.mmlId, asset.lon, asset.lat, mValue, municipality, username, asset.obstacleType), username)
    }
  }

}


