package fi.liikennevirasto.digiroad2.dao.pointasset

import fi.liikennevirasto.digiroad2.dao.Queries._
import fi.liikennevirasto.digiroad2.{PersistedPoint, Point}
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.asset.PropertyTypes._
import fi.liikennevirasto.digiroad2.asset.{Decode, LinkGeomSource, PedestrianCrossings, _}
import fi.liikennevirasto.digiroad2.dao.Sequences
import fi.liikennevirasto.digiroad2.service.pointasset.IncomingPedestrianCrossing
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, _}
import com.github.tototoshi.slick.MySQLJodaSupport._
import scala.language.reflectiveCalls

case class PedestrianCrossingRow(id: Long, linkId: String,
                                 lon: Double, lat: Double,
                                 mValue: Double, floating: Boolean,
                                 timeStamp: Long,
                                 municipalityCode: Int,
                                 property: PropertyRow,
                                 createdBy: Option[String] = None,
                                 createdAt: Option[DateTime] = None,
                                 modifiedBy: Option[String] = None,
                                 modifiedAt: Option[DateTime] = None,
                                 expired: Boolean = false,
                                 linkSource: LinkGeomSource,
                                 externalId: Option[String] = None)

case class PedestrianCrossing(id: Long, linkId: String,
                              lon: Double, lat: Double,
                              mValue: Double, floating: Boolean,
                              timeStamp: Long,
                              municipalityCode: Int,
                              propertyData: Seq[Property],
                              createdBy: Option[String] = None,
                              createdAt: Option[DateTime] = None,
                              modifiedBy: Option[String] = None,
                              modifiedAt: Option[DateTime] = None,
                              expired: Boolean = false,
                              linkSource: LinkGeomSource,
                              externalId: Option[String] = None) extends PersistedPoint


class PostGISPedestrianCrossingDao() {

  private def createOrUpdatePedestrianCrossing(crossing: IncomingPedestrianCrossing, id: Long): Unit ={
    crossing.propertyData.map(propertyWithTypeAndId(PedestrianCrossings.typeId)).foreach { propertyWithTypeAndId =>
      val propertyType = propertyWithTypeAndId._1
      val propertyPublicId = propertyWithTypeAndId._3.publicId
      val propertyId = propertyWithTypeAndId._2.get
      val propertyValues = propertyWithTypeAndId._3.values

      createOrUpdateProperties(id, propertyPublicId, propertyId, propertyType, propertyValues)
    }
  }

  def update(id: Long, persisted: IncomingPedestrianCrossing, mValue: Double, username: String, municipality: Int, adjustedTimeStampOption: Option[Long] = None, linkSource: LinkGeomSource, fromPointAssetUpdater: Boolean = false) = {
    sqlu""" update asset set municipality_code = ${municipality} where id = $id """.execute
    updateAssetGeometry(id, Point(persisted.lon, persisted.lat))
    if (!fromPointAssetUpdater) updateAssetModified(id, username).execute

    adjustedTimeStampOption match {
      case Some(adjustedTimeStamp) =>
        sqlu"""
          update lrm_position
           set
           start_measure = ${mValue},
           link_id = ${persisted.linkId},
           adjusted_timestamp = ${adjustedTimeStamp},
           modified_date = current_timestamp,
           link_source = ${linkSource.value}
           where id = (select position_id from asset_link where asset_id = $id)
        """.execute
      case _ =>
        sqlu"""
          update lrm_position
           set
           start_measure = ${mValue},
           link_id = ${persisted.linkId},
           modified_date = current_timestamp,
           link_source = ${linkSource.value}
           where id = (select position_id from asset_link where asset_id = $id)
        """.execute
    }

    createOrUpdatePedestrianCrossing(persisted, id)

    id
  }

  def create(crossing: IncomingPedestrianCrossing, mValue: Double, username: String, municipality: Long, adjustedTimestamp: Long, linkSource: LinkGeomSource): Long = {
    val id = Sequences.nextPrimaryKeySeqValue
    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
    sqlu"""
        insert into asset(id, asset_type_id, created_by, created_date, municipality_code)
        values ($id, 200, $username, current_timestamp, ${municipality});

        insert into lrm_position(id, start_measure, link_id, adjusted_timestamp, link_source)
        values ($lrmPositionId, ${mValue}, ${crossing.linkId}, $adjustedTimestamp, ${linkSource.value});

        insert into asset_link(asset_id, position_id)
        values ($id, $lrmPositionId);
    """.execute
    updateAssetGeometry(id, Point(crossing.lon, crossing.lat))

    createOrUpdatePedestrianCrossing(crossing, id)

    id
  }

  def create(crossing: IncomingPedestrianCrossing, mValue: Double, username: String, municipality: Long, adjustedTimestamp: Long, linkSource: LinkGeomSource,
             createdByFromUpdate: Option[String] = Some(""), createdDateTimeFromUpdate: Option[DateTime],
             externalIdFromUpdate: Option[String], fromPointAssetUpdater: Boolean = false, modifiedByFromUpdate: Option[String] = None, modifiedDateTimeFromUpdate: Option[DateTime] = None): Long = {
    val id = Sequences.nextPrimaryKeySeqValue
    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue

    val modifiedBy = if (fromPointAssetUpdater) modifiedByFromUpdate.getOrElse(null) else username
    val modifiedAt = if (fromPointAssetUpdater) modifiedDateTimeFromUpdate.getOrElse(null) else DateTime.now()

      sqlu"""
        insert into asset(id, external_id, asset_type_id, created_by, created_date, municipality_code, modified_by, modified_date)
        values ($id, $externalIdFromUpdate, 200, $createdByFromUpdate, $createdDateTimeFromUpdate, $municipality, $modifiedBy, $modifiedAt);

        insert into lrm_position(id, start_measure, link_id, adjusted_timestamp, link_source, modified_date)
        values ($lrmPositionId, $mValue, ${crossing.linkId}, $adjustedTimestamp, ${linkSource.value}, current_timestamp);

        insert into asset_link(asset_id, position_id)
        values ($id, $lrmPositionId);
    """.execute

    updateAssetGeometry(id, Point(crossing.lon, crossing.lat))

    createOrUpdatePedestrianCrossing(crossing, id)

    id
  }

  def fetchByFilter(queryFilter: String => String): Seq[PedestrianCrossing] = {
    val queryWithFilter = queryFilter(query()) + " and (a.valid_to > current_timestamp or a.valid_to is null)"
    queryToPedestrian(queryWithFilter)
  }

  def fetchByFilterWithExpired(queryFilter: String => String): Seq[PedestrianCrossing] = {
    val queryWithFilter = queryFilter(query())
    queryToPedestrian(queryWithFilter)
  }

  def fetchByFilterWithExpiredLimited(queryFilter: String => String, token: Option[String]): Seq[PedestrianCrossing] = {
    val recordLimit = token match {
      case Some(tk) =>
        val (startNum, endNum) = Decode.getPageAndRecordNumber(tk)

        val counter = ", DENSE_RANK() over (ORDER BY a.id) line_number from "
        s" select asset_id, link_id, geometry, start_measure, floating, adjusted_timestamp, municipality_code," +
          s" property_id, public_id, property_type, required, value, display_value, created_by, created_date," +
          s" modified_by, modified_date, expired, link_source, external_id from ( ${queryFilter(query().replace("from", counter))} ) derivedAsset WHERE line_number between $startNum and $endNum"

      case _ => queryFilter(query())
    }
    queryToPedestrian(recordLimit)
  }

  private def query() = {
    """
      select a.id as asset_id, pos.link_id, a.geometry, pos.start_measure, a.floating, pos.adjusted_timestamp, a.municipality_code, p.id as property_id, p.public_id, p.property_type, p.required, ev.value,
      case
        when ev.name_fi is not null then ev.name_fi
          else null
         end as display_value, a.created_by, a.created_date, a.modified_by, a.modified_date,
      case when a.valid_to <= current_timestamp then 1 else 0 end as expired, pos.link_source, a.external_id
      from asset a
      join asset_link al on a.id = al.asset_id
      join lrm_position pos on al.position_id = pos.id
      join property p on a.asset_type_id = p.asset_type_id
      left join multiple_choice_value mcv ON mcv.asset_id = a.id and mcv.property_id = p.id AND p.PROPERTY_TYPE = 'checkbox'
      left join enumerated_value ev on  mcv.ENUMERATED_VALUE_ID = ev.ID
    """
  }

  def fetchPedestrianCrossingByLinkIds(linkIds: Seq[String], includeExpired: Boolean = false): Seq[PedestrianCrossing] = {
    val filterExpired = if (includeExpired) "" else " and (a.valid_to > current_timestamp or a.valid_to is null)"
    val query =
      """
        select a.id, pos.link_id, a.geometry, pos.start_measure, a.floating, pos.adjusted_timestamp, a.municipality_code, p.id, p.public_id, p.property_type, p.required, ev.value,
          case
            when ev.name_fi is not null then ev.name_fi
            else null
          end as display_value, a.created_by, a.created_date, a.modified_by, a.modified_date,
          case when a.valid_to <= current_timestamp then 1 else 0 end as expired, pos.link_source, a.external_id
          from asset a
          join asset_link al on a.id = al.asset_id
          join lrm_position pos on al.position_id = pos.id
          join property p on p.asset_type_id = a.asset_type_id
          left join single_choice_value scv on scv.asset_id = a.id
          left join multiple_choice_value mcv ON mcv.asset_id = a.id and mcv.property_id = p.id AND p.PROPERTY_TYPE = 'checkbox'
          left join enumerated_value ev on (ev.property_id = p.id AND scv.enumerated_value_id = ev.id)
      """
    val queryWithFilter =
      query + s"where a.asset_type_id = ${PedestrianCrossings.typeId} and pos.link_id in (${linkIds.map(id => s"'$id'").mkString(",")})" + filterExpired
    queryToPedestrian(queryWithFilter)
  }

  implicit val getPointAssetRow = new GetResult[PedestrianCrossingRow] {
    def apply(r: PositionedResult) : PedestrianCrossingRow = {
      val id = r.nextLong()
      val linkId = r.nextString()
      val point = r.nextObjectOption().map(objectToPoint).get
      val mValue = r.nextDouble()
      val floating = r.nextBoolean()
      val timeStamp = r.nextLong()
      val municipalityCode = r.nextInt()
      val propertyId = r.nextLong
      val propertyPublicId = r.nextString
      val propertyType = r.nextString
      val propertyRequired = r.nextBoolean
      val propertyValue = r.nextLongOption()
      val propertyDisplayValue = r.nextStringOption()
      val property = new PropertyRow(
        propertyId = propertyId,
        publicId = propertyPublicId,
        propertyType = propertyType,
        propertyRequired = propertyRequired,
        propertyValue = propertyValue.getOrElse(propertyDisplayValue.getOrElse("")).toString,
        propertyDisplayValue = propertyDisplayValue.orNull)
      val createdBy = r.nextStringOption()
      val createdDateTime = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val modifiedBy = r.nextStringOption()
      val modifiedDateTime = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val expired = r.nextBoolean()
      val linkSource = r.nextInt()
      val externalId = r.nextStringOption()

      PedestrianCrossingRow(id, linkId, point.x, point.y, mValue, floating, timeStamp, municipalityCode, property,
        createdBy, createdDateTime, modifiedBy, modifiedDateTime, expired, LinkGeomSource(linkSource), externalId)
    }
  }

  def assetRowToProperty(assetRows: Iterable[PedestrianCrossingRow]): Seq[Property] = {
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

  private def queryToPedestrian(query: String): Seq[PedestrianCrossing] = {
    val rows = StaticQuery.queryNA[PedestrianCrossingRow](query)(getPointAssetRow).iterator.toSeq

    rows.groupBy(_.id).map { case (id, signRows) =>
      val row = signRows.head
      val properties: Seq[Property] = assetRowToProperty(signRows)

      id -> PedestrianCrossing(id = row.id, linkId = row.linkId, lon = row.lon, lat = row.lat, mValue = row.mValue,
        floating = row.floating, timeStamp = row.timeStamp, municipalityCode = row.municipalityCode, properties,
        createdBy = row.createdBy, createdAt = row.createdAt, modifiedBy = row.modifiedBy, modifiedAt = row.modifiedAt,
        expired = row.expired, linkSource = row.linkSource, externalId = row.externalId)
    }.values.toSeq
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
