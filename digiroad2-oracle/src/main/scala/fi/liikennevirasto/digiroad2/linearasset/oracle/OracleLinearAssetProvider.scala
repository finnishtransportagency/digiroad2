package fi.liikennevirasto.digiroad2.linearasset.oracle

import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase._
import fi.liikennevirasto.digiroad2.linearasset.Point
import fi.liikennevirasto.digiroad2.linearasset.LinearAsset
import scala.slick.driver.JdbcDriver.backend.Database
import fi.liikennevirasto.digiroad2.asset.BoundingRectangle

class OracleLinearAssetProvider extends LinearAssetProvider {
  private def toLinearAsset(entity: (Long, Seq[(Double, Double)])): LinearAsset = {
    val (id, points) = entity
    LinearAsset(id, points.map { case (x, y) => Point(x, y) })
  }

  override def getAll(bounds: Option[BoundingRectangle]): Seq[LinearAsset] = {
    Database.forDataSource(ds).withDynTransaction {
      OracleLinearAssetDao.getAll(bounds).map(toLinearAsset)
    }
  }
}
