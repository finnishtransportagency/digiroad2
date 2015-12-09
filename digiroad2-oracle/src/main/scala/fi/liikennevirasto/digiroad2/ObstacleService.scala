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

class ObstacleService(val vvhClient: VVHClient)extends PointAssetOperations[Obstacle, PersistedObstacle] {

  def getFloatingAssets(includedMunicipalities: Option[Set[Int]]): Map[String, Map[String, Seq[Long]]] = {
    getFloatingAssets("id", includedMunicipalities)
  }

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
}


