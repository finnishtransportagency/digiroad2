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
