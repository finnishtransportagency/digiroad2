package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.user.User
import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import scala.slick.driver.JdbcDriver.backend.Database
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase._
import fi.liikennevirasto.digiroad2.asset.oracle.OracleSpatialAssetDao

object RoadLinkService {
  def getRoadLinks(user: User, bounds: BoundingRectangle): Seq[Map[String, Any]] = {
    // TODO: Fetch road links from future "live" API
    Database.forDataSource(ds).withDynTransaction {
      OracleSpatialAssetDao.getRoadLinks(user, Some(bounds)).map { rl =>
        Map("roadLinkId" -> rl.id, "type" -> rl.roadLinkType.toString, "points" -> rl.lonLat.map { ll =>
          Map("x" -> ll._1, "y" -> ll._2)
        })
      }
    }
  }
}
