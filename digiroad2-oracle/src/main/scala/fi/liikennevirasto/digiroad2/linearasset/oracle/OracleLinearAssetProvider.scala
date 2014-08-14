package fi.liikennevirasto.digiroad2.linearasset.oracle

import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase._
import fi.liikennevirasto.digiroad2.linearasset.Point
import fi.liikennevirasto.digiroad2.linearasset.SpeedLimit
import scala.slick.driver.JdbcDriver.backend.Database
import fi.liikennevirasto.digiroad2.asset.BoundingRectangle

class OracleLinearAssetProvider extends LinearAssetProvider {
  private def toSpeedLimit(entity: (Long, Long, Int, Int, Seq[(Double, Double)])): SpeedLimit = {
    val (id, roadLinkId, sideCode, limit, points) = entity
    SpeedLimit(id, roadLinkId, sideCode, limit, points.map { case (x, y) => Point(x, y) })
  }

  override def getSpeedLimits(bounds: BoundingRectangle): Seq[SpeedLimit] = {
    Database.forDataSource(ds).withDynTransaction {
      OracleLinearAssetDao.getSpeedLimits(bounds).map(toSpeedLimit)
    }
  }
}
