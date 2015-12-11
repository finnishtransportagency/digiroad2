package fi.liikennevirasto.digiroad2

import com.jolbox.bonecp.{BoneCPConfig, BoneCPDataSource}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.pointasset.oracle.{PedestrianCrossingToBePersisted, OraclePedestrianCrossingDao, PersistedPedestrianCrossing}
import fi.liikennevirasto.digiroad2.user.User
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery
import slick.jdbc.StaticQuery.interpolation

case class NewPedestrianCrossing(lon: Double, lat: Double, mmlId: Long) extends IncomingAsset

case class PedestrianCrossing(id: Long,
                              mmlId: Long,
                              municipalityCode: Int,
                              lon: Double,
                              lat: Double,
                              mValue: Double,
                              floating: Boolean,
                              createdBy: Option[String] = None,
                              createdAt: Option[DateTime] = None,
                              modifiedBy: Option[String] = None,
                              modifiedAt: Option[DateTime] = None) extends PointAsset

class PedestrianCrossingService(val vvhClient: VVHClient) extends PointAssetOperations {
  type NewAsset = NewPedestrianCrossing
  type Asset = PedestrianCrossing
  type PersistedAsset = PersistedPedestrianCrossing

  def update(id:Long, updatedAsset: NewAsset, geometry: Seq[Point], municipality: Int, username: String): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(updatedAsset.lon, updatedAsset.lat, 0), geometry)
    withDynTransaction {
      OraclePedestrianCrossingDao.update(id, PedestrianCrossingToBePersisted(updatedAsset.mmlId, updatedAsset.lon, updatedAsset.lat, mValue, municipality, username))
    }
    id
  }

  override def typeId: Int = 200
  override def fetchPointAssets(queryFilter: String => String): Seq[PersistedPedestrianCrossing] = OraclePedestrianCrossingDao.fetchByFilter(queryFilter)
  override def persistedAssetToAsset(persistedAsset: PersistedPedestrianCrossing, floating: Boolean) = {
    PedestrianCrossing(
      id = persistedAsset.id,
      mmlId = persistedAsset.mmlId,
      municipalityCode = persistedAsset.municipalityCode,
      lon = persistedAsset.lon,
      lat = persistedAsset.lat,
      mValue = persistedAsset.mValue,
      floating = floating,
      createdBy = persistedAsset.createdBy,
      createdAt = persistedAsset.createdDateTime,
      modifiedBy = persistedAsset.modifiedBy,
      modifiedAt = persistedAsset.modifiedDateTime)
  }

  def create(asset: NewPedestrianCrossing, username: String, geometry: Seq[Point], municipality: Int): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(asset.lon, asset.lat, 0), geometry)
    withDynTransaction {
      OraclePedestrianCrossingDao.create(PedestrianCrossingToBePersisted(asset.mmlId, asset.lon, asset.lat, mValue, municipality, username), username)
    }
  }
}

