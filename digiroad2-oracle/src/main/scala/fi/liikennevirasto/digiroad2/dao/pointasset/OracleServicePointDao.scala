package fi.liikennevirasto.digiroad2.dao.pointasset

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import fi.liikennevirasto.digiroad2.dao.Queries._
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.Sequences
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.dao.{Queries, Sequences}
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedResult, StaticQuery}

case class IncomingServicePoint(lon: Double,
                                lat: Double,
                                services: Set[IncomingService])

case class IncomingService(serviceType: Int,
                           name: Option[String],
                           additionalInfo: Option[String],
                           typeExtension: Option[Int],
                           parkingPlaceCount: Option[Int])

case class Service(id: Long,
                   assetId: Long,
                   serviceType: Int,
                   name: Option[String],
                   additionalInfo: Option[String],
                   typeExtension: Option[Int],
                   parkingPlaceCount: Option[Int])

case class ServicePoint(id: Long,
                        lon: Double,
                        lat: Double,
                        services: Set[Service],
                        createdBy: Option[String] = None,
                        createdAt: Option[DateTime] = None,
                        modifiedBy: Option[String] = None,
                        modifiedAt: Option[DateTime] = None,
                        municipalityCode: Int)

object OracleServicePointDao {
  def create(servicePoint: IncomingServicePoint, municipalityCode: Int, username: String): Long = {
    val servicePointId = Sequences.nextPrimaryKeySeqValue
    sqlu"""
      insert all
        into asset(id, asset_type_id, created_by, created_date, municipality_code)
        values ($servicePointId, 250, $username, sysdate, $municipalityCode)
      select * from dual
    """.execute
    Queries.updateAssetGeometry(servicePointId, Point(servicePoint.lon, servicePoint.lat))
    servicePoint.services.foreach { service =>
      val serviceId = Sequences.nextPrimaryKeySeqValue
      sqlu"""
        insert into SERVICE_POINT_VALUE (ID, ASSET_ID, TYPE, ADDITIONAL_INFO, NAME, TYPE_EXTENSION, PARKING_PLACE_COUNT) values
        ($serviceId, $servicePointId, ${service.serviceType}, ${service.additionalInfo}, ${service.name}, ${service.typeExtension}, ${service.parkingPlaceCount})
      """.execute
    }
    servicePointId
  }

  def update(assetId: Long, updatedAsset: IncomingServicePoint, municipalityCode: Int, user: String) = {
    sqlu"""
           UPDATE asset
            SET municipality_code = $municipalityCode, modified_by = $user, modified_date = sysdate
            WHERE id = $assetId""".execute
    sqlu"""delete from SERVICE_POINT_VALUE where ASSET_ID = $assetId""".execute
    Queries.updateAssetGeometry(assetId, Point(updatedAsset.lon, updatedAsset.lat))
    updatedAsset.services.foreach { service =>
      val id = Sequences.nextPrimaryKeySeqValue
      sqlu"""
        insert into SERVICE_POINT_VALUE (ID, ASSET_ID, TYPE, ADDITIONAL_INFO, NAME, TYPE_EXTENSION, PARKING_PLACE_COUNT) values
        ($id, $assetId, ${service.serviceType}, ${service.additionalInfo}, ${service.name}, ${service.typeExtension}, ${service.parkingPlaceCount})
      """.execute
    }
  }

  def expire(id: Long, username: String) = {
    Queries.expireAsset(id, username)
  }

  def get: Set[ServicePoint] = {
    getWithFilter("")
  }

  def getByMunicipality(municipalityNumber: Int): Set[ServicePoint] = {
    getWithFilter(s"a.municipality_code = $municipalityNumber")
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
      select a.id, a.geometry, a.created_by, a.created_date, a.modified_by, a.modified_date, a.municipality_code
      from asset a
      where a.ASSET_TYPE_ID = 250
      and (a.valid_to > sysdate or a.valid_to is null)
      $withFilter
    """).iterator.toSet

    val services: Map[Long, Set[Service]] =
      if (servicePoints.isEmpty)
        Map.empty
      else
        StaticQuery.queryNA[Service](s"""
          select ID, ASSET_ID, TYPE, NAME, ADDITIONAL_INFO, TYPE_EXTENSION, PARKING_PLACE_COUNT
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
      val municipalityCode = r.nextInt()

      ServicePoint(id, point.x, point.y, Set.empty, createdBy, createdDateTime, modifiedBy, modifiedDateTime, municipalityCode)
    }
  }

  implicit val getService = new GetResult[Service] {
    def apply(r: PositionedResult) = {
      val id = r.nextLong()
      val assetId = r.nextLong()
      val serviceType = r.nextInt()
      val name = r.nextStringOption()
      val additionalInfo = r.nextStringOption()
      val typeExtension = r.nextIntOption()
      val parkingPlaceCount = r.nextIntOption()

      Service(id, assetId, serviceType, name, additionalInfo, typeExtension, parkingPlaceCount)
    }
  }

  def fetchByFilter(filter: String): Set[ServicePoint] = {
    getWithFilter(filter)
  }
}


