package fi.liikennevirasto.digiroad2.pointasset.oracle

import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import fi.liikennevirasto.digiroad2.asset.oracle.Queries._
import fi.liikennevirasto.digiroad2.asset.oracle.{Queries, Sequences}
import fi.liikennevirasto.digiroad2.{PersistedPointAsset, IncomingObstacle, Point}
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedResult, StaticQuery}

case class Service(id: Long, serviceType: Int, name: String, additionalInfo: Option[String], typeExtension: Int)

case class ServicePoint(id: Long,
                        lon: Double,
                        lat: Double,
                        services: Set[Service],
                        createdBy: Option[String] = None,
                        createdAt: Option[DateTime] = None,
                        modifiedBy: Option[String] = None,
                        modifiedAt: Option[DateTime] = None)


object OracleServicePointDao {
  def get(boundingBox: BoundingRectangle): Set[ServicePoint] = {
    Set.empty
  }
}


