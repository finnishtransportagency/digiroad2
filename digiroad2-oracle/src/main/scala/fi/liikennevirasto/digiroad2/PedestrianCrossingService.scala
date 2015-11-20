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

trait FloatingAsset {
  val id: Long
  val floating: Boolean
}

trait PersistedPointAsset {
  val id: Long
  val lon: Double
  val lat: Double
  val municipalityCode: Int
}

trait RoadLinkAssociatedPointAsset extends PersistedPointAsset {
  val mmlId: Long
  val mValue: Double
  val floating: Boolean
}

case class NewPointAsset(lon: Double, lat: Double, mmlId: Long)

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
                              modifiedAt: Option[DateTime] = None) extends FloatingAsset

class PedestrianCrossingService(roadLinkServiceImpl: RoadLinkService) extends PointAssetOperations[PedestrianCrossing, PersistedPedestrianCrossing, PedestrianCrossing] {
  def update(id:Long, updatedAsset: NewPointAsset, geometry: Seq[Point], municipality: Int, username: String): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(updatedAsset.lon, updatedAsset.lat, 0), geometry)
    withDynTransaction {
      OraclePedestrianCrossingDao.update(id, PedestrianCrossingToBePersisted(updatedAsset.mmlId, updatedAsset.lon, updatedAsset.lat, mValue, municipality, username))
    }
    id
  }

  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
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
  override def persistedAssetToAssetWithTimeStamps(persistedPedestrianCrossing: PersistedPedestrianCrossing, floating: Boolean) =
    persistedAssetToAsset(persistedPedestrianCrossing, floating)

  def expire(id: Long, username: String): Long = {
    withDynSession {
        OraclePedestrianCrossingDao.expire(id, username)
      id
    }
  }

  def getFloatingAssets(includedMunicipalities: Option[Set[Int]]): Map[String, Map[String, Seq[Long]]] = {
    getFloatingAssets("id", includedMunicipalities)
  }

  def create(asset: NewPointAsset, username: String, geometry: Seq[Point], municipality: Int): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(asset.lon, asset.lat, 0), geometry)
    withDynTransaction {
      OraclePedestrianCrossingDao.create(PedestrianCrossingToBePersisted(asset.mmlId, asset.lon, asset.lat, mValue, municipality, username), username)
    }
  }
}

