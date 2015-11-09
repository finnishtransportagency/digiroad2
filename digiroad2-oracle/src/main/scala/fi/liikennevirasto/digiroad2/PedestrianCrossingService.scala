package fi.liikennevirasto.digiroad2

import com.jolbox.bonecp.{BoneCPDataSource, BoneCPConfig}
import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.pointasset.oracle.OraclePointAssetDao
import fi.liikennevirasto.digiroad2.user.User
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

trait PointAssetOperations {
  def roadLinkService: RoadLinkService
  def dao: OraclePointAssetDao
  lazy val dataSource = {
    val cfg = new BoneCPConfig(OracleDatabase.loadProperties("/bonecp.properties"))
    new BoneCPDataSource(cfg)
  }
  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  def typeId: Int

  def getByBoundingBox(bounds: BoundingRectangle, municipalities: Set[Int] = Set()): Seq[PointAsset] = {
    val roadLinks = roadLinkService.getRoadLinksFromVVH(bounds, municipalities)
    withDynTransaction {
      getByMmlIds(roadLinks.map(_.mmlId))
    }
  }

  private def getByMmlIds(mmlIds: Seq[Long]): Seq[PointAsset] = {
    dao.getByMmldIds(mmlIds)
  }

  protected def getByBoundingBox2[T <: FloatingStop, M <: RoadLinkAssociatedPointAsset](user: User,
                                                                                      bounds: BoundingRectangle,
                                                                                      pointAssetFetcher: (String => String) => Seq[M],
                                                                                      persistedAssetToAsset: (M, Boolean) => T): Seq[T] = {
    case class MassTransitStopBeforeUpdate(stop: T, persistedFloating: Boolean)

    val roadLinks = roadLinkService.fetchVVHRoadlinks(bounds)
    withDynSession {
      val boundingBoxFilter = OracleDatabase.boundingBoxFilter(bounds, "a.geometry")
      val filter = s"where a.asset_type_id = $typeId and $boundingBoxFilter"
      val persistedMassTransitStops: Seq[M] = pointAssetFetcher(withFilter(filter))

      val stopsBeforeUpdate: Seq[MassTransitStopBeforeUpdate] = persistedMassTransitStops.filter { persistedStop =>
        user.isAuthorizedToRead(persistedStop.municipalityCode)
      }.map { persistedStop =>
        val floating = isFloating(persistedStop, roadLinks.find(_.mmlId == persistedStop.mmlId).map(link => (link.municipalityCode, link.geometry)))
        MassTransitStopBeforeUpdate(persistedAssetToAsset(persistedStop, floating), persistedStop.floating)
      }

      stopsBeforeUpdate.foreach { stop =>
        if (stop.stop.floating != stop.persistedFloating) {
          updateFloating(stop.stop.id, stop.stop.floating)
        }
      }

      stopsBeforeUpdate.map(_.stop)
    }
  }

  def isFloating(persistedStop: RoadLinkAssociatedPointAsset, roadLink: Option[(Int, Seq[Point])]): Boolean = {
    val point = Point(persistedStop.lon, persistedStop.lat)
    roadLink match {
      case None => true
      case Some((municipalityCode, geometry)) => municipalityCode != persistedStop.municipalityCode ||
        !coordinatesWithinThreshold(Some(point), GeometryUtils.calculatePointFromLinearReference(geometry, persistedStop.mValue))
    }
  }

  protected def withFilter(filter: String)(query: String): String = {
    query + " " + filter
  }

  protected def updateFloating(id: Long, floating: Boolean) = sqlu"""update asset set floating = $floating where id = $id""".execute

  private val FLOAT_THRESHOLD_IN_METERS = 3

  protected def coordinatesWithinThreshold(pt1: Option[Point], pt2: Option[Point]): Boolean = {
    (pt1, pt2) match {
      case (Some(point1), Some(point2)) => point1.distanceTo(point2) <= FLOAT_THRESHOLD_IN_METERS
      case _ => false
    }
  }
}

class PedestrianCrossingService(roadLinkServiceImpl: RoadLinkService) extends PointAssetOperations {
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def dao: OraclePointAssetDao = OraclePointAssetDao
  override def typeId: Int = 200
}

case class PointAsset(id:Long, mmlId: Long, lon: Double, lat: Double, mValue: Double)


object PointAssetOperations {
  def isFloating(pointAsset: PointAsset, roadLink: Option[VVHRoadlink]): Boolean = {
    val calculatedPoint = GeometryUtils.calculatePointFromLinearReference(_: Seq[Point], pointAsset.mValue)
    val persistedPoint = Point(pointAsset.lon, pointAsset.lat)

    roadLink match {
      case Some(roadLink) => !coordinatesWithinThreshold(persistedPoint, calculatedPoint(roadLink.geometry))
      case None           => true
    }
  }

  private val FLOAT_THRESHOLD_IN_METERS = 3

  private def coordinatesWithinThreshold(pt1: Point, pt2: Option[Point]): Boolean = {
    (pt1, pt2) match {
      case (point1, Some(point2)) => point1.distanceTo(point2) <= FLOAT_THRESHOLD_IN_METERS
      case _ => false
    }
  }
}