package fi.liikennevirasto.digiroad2

import com.jolbox.bonecp.{BoneCPConfig, BoneCPDataSource}
import fi.liikennevirasto.digiroad2.asset.{TimeStamps, RoadLinkStop, BoundingRectangle}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.pointasset.oracle.{OraclePedestrianCrossingDao, PersistedPedestrianCrossing}
import fi.liikennevirasto.digiroad2.user.User
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database.dynamicSession
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

trait PointAssetOperations[A <: FloatingAsset, B <: RoadLinkAssociatedPointAsset, C <: FloatingAsset] {
  def roadLinkService: RoadLinkService
  lazy val dataSource = {
    val cfg = new BoneCPConfig(OracleDatabase.loadProperties("/bonecp.properties"))
    new BoneCPDataSource(cfg)
  }
  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  def typeId: Int
  def fetchPointAssets(queryFilter: String => String): Seq[B]
  def persistedAssetToAsset(persistedAsset: B, floating: Boolean): A
  def persistedAssetToAssetWithTimeStamps(persistedStop: B, floating: Boolean): C

  def getByBoundingBox(user: User, bounds: BoundingRectangle): Seq[A] = {
    case class AssetBeforeUpdate(asset: A, persistedFloating: Boolean)

    val roadLinks = roadLinkService.fetchVVHRoadlinks(bounds)
    withDynSession {
      val boundingBoxFilter = OracleDatabase.boundingBoxFilter(bounds, "a.geometry")
      val filter = s"where a.asset_type_id = $typeId and $boundingBoxFilter"
      val persistedAssets: Seq[B] = fetchPointAssets(withFilter(filter))

      val assetsBeforeUpdate: Seq[AssetBeforeUpdate] = persistedAssets.filter { persistedAsset =>
        user.isAuthorizedToRead(persistedAsset.municipalityCode)
      }.map { persistedAsset =>
        val floating = PointAssetOperations.isFloating(persistedAsset, roadLinks.find(_.mmlId == persistedAsset.mmlId).map(link => (link.municipalityCode, link.geometry)))
        AssetBeforeUpdate(persistedAssetToAsset(persistedAsset, floating), persistedAsset.floating)
      }

      assetsBeforeUpdate.foreach { asset =>
        if (asset.asset.floating != asset.persistedFloating) {
          updateFloating(asset.asset.id, asset.asset.floating)
        }
      }

      assetsBeforeUpdate.map(_.asset)
    }
  }

  def getByMunicipality(municipalityCode: Int): Seq[C] = {
    val roadLinks = roadLinkService.fetchVVHRoadlinks(municipalityCode)
    def findRoadlink(mmlId: Long): Option[(Int, Seq[Point])] =
      roadLinks.find(_.mmlId == mmlId).map(x => (x.municipalityCode, x.geometry))

    withDynSession {
      fetchPointAssets(withMunicipality(municipalityCode))
        .map(withFloatingUpdate(convertPersistedAsset(persistedAssetToAssetWithTimeStamps, findRoadlink)))
        .toList
    }
  }

  def getPersistedAssetsByIds(ids: Set[Long]): Seq[B] = {
    withDynSession {
      val idsStr = ids.toSeq.mkString(",")
      val filter = s"where a.asset_type_id = $typeId and a.id in ($idsStr)"
      fetchPointAssets(withFilter(filter))
    }
  }

  def expire(ids: Seq[Long], username: String): Seq[Long] = {
    withDynSession {
      ids.foreach {
        OraclePedestrianCrossingDao.expire(_, username)
      }
      ids
    }
  }

  protected def convertPersistedAsset[T](conversion: (B, Boolean) => T,
                                      roadLinkByMmlId: Long => Option[(Int, Seq[Point])])
                                     (persistedStop: B): T = {
    val floating = PointAssetOperations.isFloating(persistedStop, roadLinkByMmlId(persistedStop.mmlId))
    conversion(persistedStop, floating)
  }

  protected def withFilter(filter: String)(query: String): String = {
    query + " " + filter
  }

  protected def withMunicipality(municipalityCode: Int)(query: String): String = {
    withFilter(s"where a.asset_type_id = $typeId and a.municipality_code = $municipalityCode")(query)
  }

  protected def withFloatingUpdate[T <: FloatingAsset](toPointAsset: B => T)
                                                    (persistedAsset: B): T = {
    val pointAsset = toPointAsset(persistedAsset)
    if (persistedAsset.floating != pointAsset.floating) updateFloating(pointAsset.id, pointAsset.floating)
    pointAsset
  }

  protected def updateFloating(id: Long, floating: Boolean) = sqlu"""update asset set floating = $floating where id = $id""".execute
}

case class PedestrianCrossing(id: Long,
                              mmlId: Long,
                              lon: Double,
                              lat: Double,
                              mValue: Double,
                              floating: Boolean,
                              createdBy: Option[String] = None,
                              createdAt: Option[DateTime] = None,
                              modifiedBy: Option[String] = None,
                              modifiedAt: Option[DateTime] = None) extends FloatingAsset

class PedestrianCrossingService(roadLinkServiceImpl: RoadLinkService) extends PointAssetOperations[PedestrianCrossing, PersistedPedestrianCrossing, PedestrianCrossing] {
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def typeId: Int = 200
  override def fetchPointAssets(queryFilter: String => String): Seq[PersistedPedestrianCrossing] = OraclePedestrianCrossingDao.fetchByFilter(queryFilter)
  override def persistedAssetToAsset(persistedAsset: PersistedPedestrianCrossing, floating: Boolean) = {
    PedestrianCrossing(
      id = persistedAsset.id,
      mmlId = persistedAsset.mmlId,
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
}

object PointAssetOperations {
  def isFloating(persistedAsset: RoadLinkAssociatedPointAsset, roadLink: Option[(Int, Seq[Point])]): Boolean = {
    val point = Point(persistedAsset.lon, persistedAsset.lat)
    roadLink match {
      case None => true
      case Some((municipalityCode, geometry)) => municipalityCode != persistedAsset.municipalityCode ||
        !coordinatesWithinThreshold(Some(point), GeometryUtils.calculatePointFromLinearReference(geometry, persistedAsset.mValue))
    }
  }

  private val FLOAT_THRESHOLD_IN_METERS = 3

  def coordinatesWithinThreshold(pt1: Option[Point], pt2: Option[Point]): Boolean = {
    (pt1, pt2) match {
      case (Some(point1), Some(point2)) => point1.distanceTo(point2) <= FLOAT_THRESHOLD_IN_METERS
      case _ => false
    }
  }
}