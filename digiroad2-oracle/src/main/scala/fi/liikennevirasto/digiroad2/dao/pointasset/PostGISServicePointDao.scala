package fi.liikennevirasto.digiroad2.dao.pointasset

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.dao.Queries._
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.asset.PropertyTypes._
import fi.liikennevirasto.digiroad2.dao.{Queries, Sequences}
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedResult, StaticQuery}

case class IncomingServicePoint(lon: Double, lat: Double, services: Set[IncomingService], propertyData: Set[SimplePointAssetProperty], mValue: Option[Double] = None)

case class IncomingService(serviceType: Int,
                           name: Option[String],
                           additionalInfo: Option[String],
                           typeExtension: Option[Int],
                           parkingPlaceCount: Option[Int],
                           isAuthorityData: Boolean = true,
                           weightLimit: Option[Int])

case class Service(id: Long,
                   assetId: Long,
                   serviceType: Int,
                   name: Option[String],
                   additionalInfo: Option[String],
                   typeExtension: Option[Int],
                   parkingPlaceCount: Option[Int],
                   isAuthorityData: Boolean = true,
                   weightLimit: Option[Int])

case class ServicePoint(id: Long,
                        lon: Double,
                        lat: Double,
                        services: Set[Service],
                        createdBy: Option[String] = None,
                        createdAt: Option[DateTime] = None,
                        modifiedBy: Option[String] = None,
                        modifiedAt: Option[DateTime] = None,
                        municipalityCode: Int,
                        propertyData: Seq[Property])

case class ServicePointRow(id: Long,
                          lon: Double,
                          lat: Double,
                          services: Set[Service],
                          createdBy: Option[String] = None,
                          createdAt: Option[DateTime] = None,
                          modifiedBy: Option[String] = None,
                          modifiedAt: Option[DateTime] = None,
                          municipalityCode: Int,
                          property: PropertyRow)

object PostGISServicePointDao {
  private def createOrUpdateServicePoint(servicePoint: IncomingServicePoint, id: Long): Unit ={
    servicePoint.propertyData.map(propertyWithTypeAndId(ServicePoints.typeId)).foreach { propertyWithTypeAndId =>
      val propertyType = propertyWithTypeAndId._1
      val propertyPublicId = propertyWithTypeAndId._3.publicId
      val propertyId = propertyWithTypeAndId._2.get
      val propertyValues = propertyWithTypeAndId._3.values

      createOrUpdateProperties(id, propertyPublicId, propertyId, propertyType, propertyValues)
    }
  }

  def create(servicePoint: IncomingServicePoint, municipalityCode: Int, username: String): Long = {
    val servicePointId = Sequences.nextPrimaryKeySeqValue
    sqlu"""
      insert
        into asset(id, asset_type_id, created_by, created_date, municipality_code)
        values ($servicePointId, 250, $username, current_timestamp, $municipalityCode);
    """.execute
    Queries.updateAssetGeometry(servicePointId, Point(servicePoint.lon, servicePoint.lat))
    servicePoint.services.foreach { service =>
      val serviceId = Sequences.nextPrimaryKeySeqValue
      sqlu"""
        insert into SERVICE_POINT_VALUE (ID, ASSET_ID, TYPE, ADDITIONAL_INFO, NAME, TYPE_EXTENSION, PARKING_PLACE_COUNT, is_authority_data, WEIGHT_LIMIT) values
        ($serviceId, $servicePointId, ${service.serviceType}, ${service.additionalInfo}, ${service.name}, ${service.typeExtension}, ${service.parkingPlaceCount}, ${service.isAuthorityData}, ${service.weightLimit})
      """.execute
    }

    createOrUpdateServicePoint(servicePoint, servicePointId)

    servicePointId
  }

  def update(assetId: Long, updatedAsset: IncomingServicePoint, municipalityCode: Int, user: String) = {
    sqlu"""
           UPDATE asset
            SET municipality_code = $municipalityCode, modified_by = $user, modified_date = current_timestamp
            WHERE id = $assetId""".execute
    sqlu"""delete from SERVICE_POINT_VALUE where ASSET_ID = $assetId""".execute
    Queries.updateAssetGeometry(assetId, Point(updatedAsset.lon, updatedAsset.lat))
    updatedAsset.services.foreach { service =>
      val id = Sequences.nextPrimaryKeySeqValue
      sqlu"""
        insert into SERVICE_POINT_VALUE (ID, ASSET_ID, TYPE, ADDITIONAL_INFO, NAME, TYPE_EXTENSION, PARKING_PLACE_COUNT, is_authority_data, WEIGHT_LIMIT) values
        ($id, $assetId, ${service.serviceType}, ${service.additionalInfo}, ${service.name}, ${service.typeExtension}, ${service.parkingPlaceCount}, ${service.isAuthorityData}, ${service.weightLimit})
      """.execute
    }

    createOrUpdateServicePoint(updatedAsset, assetId)

    assetId
  }

  def expire(id: Long, username: String) = {
    Queries.updateAssetModified(id, username).first
    sqlu"update asset set valid_to = current_timestamp where id = $id".first
  }

  def get: Set[ServicePoint] = {
    getWithFilter("")
  }

  def getById(id: Long): Set[ServicePoint] = {
    getWithFilter(s"a.id = $id")
  }

  def getByMunicipality(municipalityNumber: Int): Set[ServicePoint] = {
    getWithFilter(s"a.municipality_code = $municipalityNumber")
  }

  def get(bounds: BoundingRectangle): Set[ServicePoint] = {
    val bboxFilter = PostGISDatabase.boundingBoxFilter(bounds, "a.geometry")
    getWithFilter(bboxFilter)
  }

  private def getWithFilter(filter: String): Set[ServicePoint] = {
    val withFilter = if (!filter.isEmpty)
      s"and $filter"
    else
      ""

    val servicePointQuery = s"""
         select a.id, a.geometry, a.created_by, a.created_date, a.modified_by, a.modified_date, a.municipality_code, p.id, p.public_id, p.property_type, p.required, ev.value,
           case
             when ev.name_fi is not null then ev.name_fi
             else null
           end as display_value
         from asset a
         join property p on p.asset_type_id = a.asset_type_id
         left join multiple_choice_value mcv ON mcv.asset_id = a.id and mcv.property_id = p.id AND p.PROPERTY_TYPE = 'checkbox'
         left join enumerated_value ev on (ev.property_id = p.id AND mcv.enumerated_value_id = ev.id)
         where a.ASSET_TYPE_ID = 250
         and (a.valid_to > current_timestamp or a.valid_to is null)
      $withFilter
    """

    val servicePoints = queryToServicePoint(servicePointQuery)

    val services: Map[Long, Set[Service]] =
      if (servicePoints.isEmpty)
        Map.empty
      else
        StaticQuery.queryNA[Service](
          s"""
          select ID, ASSET_ID, TYPE, NAME, ADDITIONAL_INFO, TYPE_EXTENSION, PARKING_PLACE_COUNT,
          CASE WHEN IS_AUTHORITY_DATA IS NULL AND (TYPE = 10 OR TYPE = 17)THEN '0'
          WHEN IS_AUTHORITY_DATA IS NULL THEN '1' ELSE IS_AUTHORITY_DATA END AS IS_AUTHORITY_DATA,
          WEIGHT_LIMIT
          from SERVICE_POINT_VALUE
          where (ASSET_ID, ASSET_ID) in (${servicePoints.map(_.id).map({ x => s"($x, $x)" }).mkString(",")})
        """)(getService).iterator.toSet.groupBy(_.assetId)

    servicePoints.map { servicePoint =>
      servicePoint.copy(services = services(servicePoint.id))
    }.toSet
  }

  def assetRowToProperty(assetRows: Iterable[ServicePointRow]): Seq[Property] = {
    assetRows.groupBy(_.property.propertyId).map { case (key, rows) =>
      val row = rows.head
      Property(
        id = key,
        publicId = row.property.publicId,
        propertyType = row.property.propertyType,
        required = row.property.propertyRequired,
        values = rows.flatMap { assetRow =>

          val finalValue = PropertyValidator.propertyValueValidation(assetRow.property.publicId, assetRow.property.propertyValue )

          Seq(PropertyValue(finalValue, Option(assetRow.property.propertyDisplayValue)))

        }.toSeq)
    }.toSeq
  }

  private def queryToServicePoint(query: String): Seq[ServicePoint] = {
    val rows = StaticQuery.queryNA[ServicePointRow](query)(getServicePoint).iterator.toSeq

    rows.groupBy(_.id).map { case (id, signRows) =>
      val row = signRows.head
      val properties: Seq[Property] = assetRowToProperty(signRows)

      id -> ServicePoint(row.id, row.lon, row.lat, row.services, row.createdBy, row.createdAt, row.modifiedBy, row.modifiedAt, row.municipalityCode, properties)
    }.values.toSeq
  }

  implicit val getServicePoint = new GetResult[ServicePointRow] {
    def apply(r: PositionedResult) : ServicePointRow = {
      val id = r.nextLong()
      val point = r.nextObjectOption().map(objectToPoint).get
      val createdBy = r.nextStringOption()
      val createdDateTime = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val modifiedBy = r.nextStringOption()
      val modifiedDateTime = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val municipalityCode = r.nextInt()
      val propertyId = r.nextLong
      val propertyPublicId = r.nextString
      val propertyType = r.nextString
      val propertyRequired = r.nextBoolean
      val propertyValue = r.nextLongOption()
      val propertyDisplayValue = r.nextStringOption()
      val property = PropertyRow(
        propertyId = propertyId,
        publicId = propertyPublicId,
        propertyType = propertyType,
        propertyRequired = propertyRequired,
        propertyValue = propertyValue.getOrElse(propertyDisplayValue.getOrElse("")).toString,
        propertyDisplayValue = propertyDisplayValue.orNull)

      ServicePointRow(id, point.x, point.y, Set.empty, createdBy, createdDateTime, modifiedBy, modifiedDateTime, municipalityCode, property)
    }
  }

  implicit val getService: GetResult[Service] = new GetResult[Service] {
    def apply(r: PositionedResult) : Service = {
      val id = r.nextLong()
      val assetId = r.nextLong()
      val serviceType = r.nextInt()
      val name = r.nextStringOption()
      val additionalInfo = r.nextStringOption()
      val typeExtension = r.nextIntOption()
      val parkingPlaceCount = r.nextIntOption()
      val isAuthorityData = r.nextBoolean()
      val weightLimit = r.nextIntOption()

      Service(id, assetId, serviceType, name, additionalInfo, typeExtension, parkingPlaceCount, isAuthorityData, weightLimit)
    }
  }

  def fetchByFilter(filter: String): Set[ServicePoint] = {
    getWithFilter(filter)
  }

  def propertyWithTypeAndId(typeId: Int)(property: SimplePointAssetProperty): Tuple3[String, Option[Long], SimplePointAssetProperty] = {
    val propertyId = StaticQuery.query[(String, Int), Long](propertyIdByPublicIdAndTypeId).apply(property.publicId, typeId).firstOption.getOrElse(throw new IllegalArgumentException("Property: " + property.publicId + " not found"))
    (StaticQuery.query[Long, String](propertyTypeByPropertyId).apply(propertyId).first, Some(propertyId), property)
  }

  def singleChoiceValueDoesNotExist(assetId: Long, propertyId: Long) = {
    StaticQuery.query[(Long, Long), Long](existsSingleChoiceProperty).apply((assetId, propertyId)).firstOption.isEmpty
  }

  def textPropertyValueDoesNotExist(assetId: Long, propertyId: Long) = {
    StaticQuery.query[(Long, Long), Long](existsTextProperty).apply((assetId, propertyId)).firstOption.isEmpty
  }

  def multipleChoiceValueDoesNotExist(assetId: Long, propertyId: Long): Boolean = {
    StaticQuery.query[(Long, Long), Long](existsMultipleChoiceProperty).apply((assetId, propertyId)).firstOption.isEmpty
  }

  def createOrUpdateProperties(assetId: Long, propertyPublicId: String, propertyId: Long, propertyType: String, propertyValues: Seq[PointAssetValue]) {
    propertyType match {
      case CheckBox =>
        if (propertyValues.size > 1) throw new IllegalArgumentException("Multiple choice only allows values between 0 and 1.")
        if(multipleChoiceValueDoesNotExist(assetId, propertyId)) {
          insertMultipleChoiceValue(assetId, propertyId, propertyValues.head.asInstanceOf[PropertyValue].propertyValue.toLong).execute
        } else {
          updateMultipleChoiceValue(assetId, propertyId, propertyValues.head.asInstanceOf[PropertyValue].propertyValue.toLong).execute
        }
      case t: String => throw new UnsupportedOperationException("Asset property type: " + t + " not supported")
    }
  }
}
