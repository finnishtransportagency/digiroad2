package fi.liikennevirasto.digiroad2.pointasset.oracle

import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import fi.liikennevirasto.digiroad2.asset.oracle.Queries._
import fi.liikennevirasto.digiroad2.asset.oracle.{Queries, Sequences}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.{PersistedPointAsset, IncomingObstacle, Point}
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import slick.jdbc.StaticQuery
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedResult, StaticQuery}

case class Service(id: Long,
                   assetId: Long,
                   serviceType: Int,
                   name: Option[String],
                   additionalInfo: Option[String],
                   typeExtension: Int)

case class ServicePoint(id: Long,
                        lon: Double,
                        lat: Double,
                        services: Set[Service],
                        createdBy: Option[String] = None,
                        createdAt: Option[DateTime] = None,
                        modifiedBy: Option[String] = None,
                        modifiedAt: Option[DateTime] = None)

object OracleServicePointDao {
  def get: Set[ServicePoint] = {
    getWithFilter("")
  }

  def get(bounds: BoundingRectangle): Set[ServicePoint] = {
    val bboxFilter = OracleDatabase.boundingBoxFilter(bounds, "a.geometry")
    getWithFilter(bboxFilter)
  }

  private def getWithFilter(filter: String): Set[ServicePoint] = {
    val withFilter = if (!filter.isEmpty)
      s"and $filter"
    else
      ""

    val servicePoints = StaticQuery.queryNA[ServicePoint](
      s"""
      select a.id, a.geometry, a.created_by, a.created_date, a.modified_by, a.modified_date
      from asset a
      where a.ASSET_TYPE_ID = 250 $withFilter
    """).iterator.toSet

    val services: Map[Long, Set[Service]] =
      if (servicePoints.isEmpty)
        Map.empty
      else
        StaticQuery.queryNA[Service](s"""
          select ID, ASSET_ID, TYPE, NAME, ADDITIONAL_INFO, TYPE_EXTENSION
          from SERVICE_POINT_VALUE
          where (ASSET_ID, ASSET_ID) in (${servicePoints.map(_.id).map({x => s"($x, $x)"}).mkString(",")})
        """).iterator.toSet.groupBy(_.assetId)

    servicePoints.map { servicePoint =>
      servicePoint.copy(services = services(servicePoint.id))
    }

  }

  implicit val getServicePoint = new GetResult[ServicePoint] {
    def apply(r: PositionedResult) = {
      val id = r.nextLong()
      val point = r.nextBytesOption().map(bytesToPoint).get
      val createdBy = r.nextStringOption()
      val createdDateTime = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val modifiedBy = r.nextStringOption()
      val modifiedDateTime = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))

      ServicePoint(id, point.x, point.y, Set.empty, createdBy, createdDateTime, modifiedBy, modifiedDateTime)
    }
  }

  implicit val getService = new GetResult[Service] {
    def apply(r: PositionedResult) = {
      val id = r.nextLong()
      val assetId = r.nextLong()
      val serviceType = r.nextInt()
      val name = r.nextStringOption()
      val additionalInfo = r.nextStringOption()
      val typeExtension = r.nextInt()

      Service(id, assetId, serviceType, name, additionalInfo, typeExtension)
    }
  }
}


