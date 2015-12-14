package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.pointasset.oracle._
import org.joda.time.DateTime

class ObstacleService(val vvhClient: VVHClient) extends PointAssetOperations {
  type IncomingAsset = Obstacle
  type PersistedAsset = Obstacle

  override def typeId: Int = 220

  override def fetchPointAssets(queryFilter: String => String): Seq[Obstacle] = OracleObstacleDao.fetchByFilter(queryFilter)

  override def setFloating(persistedAsset: Obstacle, floating: Boolean) = {
    persistedAsset.copy(floating = floating)
  }

  override def create(asset: Obstacle, username: String, geometry: Seq[Point], municipality: Int): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(asset.lon, asset.lat, 0), geometry)
    withDynTransaction {
      OracleObstacleDao.create(asset.copy(mValue = mValue, municipalityCode = municipality), username)
    }
  }

  override def update(id:Long, updatedAsset: Obstacle, geometry: Seq[Point], municipality: Int, username: String): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(updatedAsset.lon, updatedAsset.lat, 0), geometry)
    withDynTransaction {
      OracleObstacleDao.update(id, updatedAsset.copy(mValue = mValue, municipalityCode = municipality, modifiedBy = Some(username)))
    }
    id
  }

}


