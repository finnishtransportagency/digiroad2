package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.pointasset.oracle._
import org.joda.time.DateTime

case class NewObstacle(lon: Double, lat: Double, mmlId: Long, obstacleType: Int) extends IncomingPointAsset

case class Obstacle(id: Long, mmlId: Long,
                              lon: Double, lat: Double,
                              mValue: Double, floating: Boolean,
                              municipalityCode: Int,
                              obstacleType: Int,
                              createdBy: Option[String] = None,
                              createdAt: Option[DateTime] = None,
                              modifiedBy: Option[String] = None,
                              modifiedAt: Option[DateTime] = None) extends PointAsset

class ObstacleService(val vvhClient: VVHClient) extends PointAssetOperations {
  type IncomingAsset = NewObstacle
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

  override def create(asset: NewObstacle, username: String, geometry: Seq[Point], municipality: Int): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(asset.lon, asset.lat, 0), geometry)
    withDynTransaction {
      OracleObstacleDao.create(ObstacleToBePersisted(asset.mmlId, asset.lon, asset.lat, mValue, municipality, username, asset.obstacleType), username)
    }
  }

  override def update(id:Long, updatedAsset: NewObstacle, geometry: Seq[Point], municipality: Int, username: String): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(updatedAsset.lon, updatedAsset.lat, 0), geometry)
    withDynTransaction {
      OracleObstacleDao.update(id, ObstacleToBePersisted(updatedAsset.mmlId, updatedAsset.lon, updatedAsset.lat, mValue, municipality, username, updatedAsset.obstacleType))
    }
    id
  }

}


