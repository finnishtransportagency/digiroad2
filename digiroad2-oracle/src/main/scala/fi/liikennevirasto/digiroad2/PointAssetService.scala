package fi.liikennevirasto.digiroad2

import com.jolbox.bonecp.{BoneCPDataSource, BoneCPConfig}
import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.pointasset.oracle.OraclePointAssetDao

trait PointAssetOperations {
  def roadLinkService: RoadLinkService
  def dao: OraclePointAssetDao
  lazy val dataSource = {
    val cfg = new BoneCPConfig(OracleDatabase.loadProperties("/bonecp.properties"))
    new BoneCPDataSource(cfg)
  }
  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)

  def getByBoundingBox(bounds: BoundingRectangle, municipalities: Set[Int] = Set()): Seq[PointAsset] = {
    val roadLinks = roadLinkService.getRoadLinksFromVVH(bounds, municipalities)
    withDynTransaction {
      getByMmlIds(roadLinks.map(_.mmlId))
    }
  }

  private def getByMmlIds(mmlIds: Seq[Long]): Seq[PointAsset] = {
    dao.getByMmldIds(mmlIds)
  }
}

class PointAssetService(roadLinkServiceImpl: RoadLinkService) extends PointAssetOperations {
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def dao: OraclePointAssetDao = OraclePointAssetDao
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