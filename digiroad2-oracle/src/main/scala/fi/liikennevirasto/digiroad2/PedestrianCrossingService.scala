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

case class NewPedestrianCrossing(lon: Double, lat: Double, mmlId: Long) extends IncomingPointAsset

class PedestrianCrossingService(val vvhClient: VVHClient) extends PointAssetOperations {
  type IncomingAsset = NewPedestrianCrossing
  type Asset = PersistedPedestrianCrossing
  type PersistedAsset = PersistedPedestrianCrossing

  override def update(id:Long, updatedAsset: IncomingAsset, geometry: Seq[Point], municipality: Int, username: String): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(updatedAsset.lon, updatedAsset.lat, 0), geometry)
    withDynTransaction {
      OraclePedestrianCrossingDao.update(id, PedestrianCrossingToBePersisted(updatedAsset.mmlId, updatedAsset.lon, updatedAsset.lat, mValue, municipality, username))
    }
    id
  }

  override def typeId: Int = 200
  override def fetchPointAssets(queryFilter: String => String): Seq[PersistedPedestrianCrossing] = OraclePedestrianCrossingDao.fetchByFilter(queryFilter)
  override def setFloating(persistedAsset: PersistedPedestrianCrossing, floating: Boolean) = {
    PersistedPedestrianCrossing(
      id = persistedAsset.id,
      mmlId = persistedAsset.mmlId,
      municipalityCode = persistedAsset.municipalityCode,
      lon = persistedAsset.lon,
      lat = persistedAsset.lat,
      mValue = persistedAsset.mValue,
      floating = floating,
      createdBy = persistedAsset.createdBy,
      createdDateTime = persistedAsset.createdDateTime,
      modifiedBy = persistedAsset.modifiedBy,
      modifiedDateTime = persistedAsset.modifiedDateTime)
  }

  override def create(asset: NewPedestrianCrossing, username: String, geometry: Seq[Point], municipality: Int): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(asset.lon, asset.lat, 0), geometry)
    withDynTransaction {
      OraclePedestrianCrossingDao.create(PedestrianCrossingToBePersisted(asset.mmlId, asset.lon, asset.lat, mValue, municipality, username), username)
    }
  }
}

