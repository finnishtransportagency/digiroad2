package fi.liikennevirasto.digiroad2.dao.pointasset

import fi.liikennevirasto.digiroad2.dao.Queries._
import fi.liikennevirasto.digiroad2.{PersistedPoint, Point}
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.asset.PropertyTypes._
import fi.liikennevirasto.digiroad2.asset.{Decode, LinkGeomSource, _}
import fi.liikennevirasto.digiroad2.dao.Sequences
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.pointasset.IncomingWidthOfRoadAxisMarking
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedResult, StaticQuery}
import com.github.tototoshi.slick.MySQLJodaSupport._
import fi.liikennevirasto.digiroad2.dao.pointasset.OracleObstacleDao.{assetRowToProperty, getPointAsset}


case class WidthOfRoadAxisMarkingRow(id: Long, linkId: Long,
                                     lon: Double, lat: Double,
                                     mValue: Double, floating: Boolean,
                                     vvhTimeStamp: Long,
                                     municipalityCode: Int,
                                     property: PropertyRow,
                                     createdBy: Option[String] = None,
                                     createdAt: Option[DateTime] = None,
                                     modifiedBy: Option[String] = None,
                                     modifiedAt: Option[DateTime] = None,
                                     expired: Boolean = false,
                                     linkSource: LinkGeomSource)

case class WidthOfRoadAxisMarking(id: Long, linkId: Long,
                                  lon: Double, lat: Double,
                                  mValue: Double, floating: Boolean,
                                  vvhTimeStamp: Long,
                                  municipalityCode: Int,
                                  propertyData: Seq[Property],
                                  createdBy: Option[String] = None,
                                  createdAt: Option[DateTime] = None,
                                  modifiedBy: Option[String] = None,
                                  modifiedAt: Option[DateTime] = None,
                                  expired: Boolean = false,
                                  linkSource: LinkGeomSource) extends PersistedPoint

object OracleWidthOfRoadAxisMarkingDao {

  private def query() = {
    """
      select a.id as asset_id, pos.link_id, a.geometry, pos.start_measure, a.floating, pos.adjusted_timestamp, a.municipality_code, p.id as property_id, p.public_id, p.property_type, p.required, ev.value,
      case
        when ev.name_fi is not null then ev.name_fi
        when tpv.value_fi is not null then tpv.value_fi
        when dpv.date_time is not null then to_char(dpv.date_time, 'DD.MM.YYYY')
        when npv.value is not null then to_char(npv.value)
        else null
      end as display_value, a.created_by, a.created_date, a.modified_by, a.modified_date, case when a.valid_to <= sysdate then 1 else 0 end as expired, pos.link_source
      from asset a
      join asset_link al on a.id = al.asset_id
      join lrm_position pos on al.position_id = pos.id
      join property p on a.asset_type_id = p.asset_type_id
      left join single_choice_value scv on scv.asset_id = a.id and scv.property_id = p.id and p.property_type = 'single_choice'
      left join text_property_value tpv on tpv.asset_id = a.id and tpv.property_id = p.id and p.property_type = 'text'
      left join date_property_value dpv on dpv.asset_id = a.id and dpv.property_id = p.id and p.property_type = 'date'
      left join number_property_value npv on npv.asset_id = a.id and npv.property_id = p.id and p.property_type = 'number'
      left join enumerated_value ev on scv.enumerated_value_id = ev.id
    """
  }

  def fetchByFilterWithExpired(queryFilter: String => String): Seq[WidthOfRoadAxisMarking] = {
    val queryWithFilter = queryFilter(query())
    queryToWidthOfRoadAxisMarking(queryWithFilter)
  }

  // This works as long as there is only one (and exactly one) property (currently type) for WidthOfRoadAxisMarking and up to one value
  def fetchByFilter(queryFilter: String => String, withDynSession: Boolean = false): Seq[WidthOfRoadAxisMarking] = {
    val queryWithFilter = queryFilter(query()) + " and (a.valid_to > sysdate or a.valid_to is null)"
    if (withDynSession) {
      OracleDatabase.withDynSession {
        queryToWidthOfRoadAxisMarking(queryWithFilter)
      }
    } else {
      queryToWidthOfRoadAxisMarking(queryWithFilter)
    }
  }

  def assetRowToProperty(assetRows: Iterable[WidthOfRoadAxisMarkingRow]): Seq[Property] = {
    assetRows.groupBy(_.property.propertyId).map { case (key, rows) =>
      val row = rows.head
      Property(
        id = key,
        publicId = row.property.publicId,
        propertyType = row.property.propertyType,
        required = row.property.propertyRequired,
        values = rows.flatMap { assetRow =>

          val finalValue = PropertyValidator.propertyValueValidation(assetRow.property.publicId, assetRow.property.propertyValue)

          Seq(PropertyValue(finalValue, Option(assetRow.property.propertyDisplayValue)))

        }.toSeq)
    }.toSeq
  }


  private def queryToWidthOfRoadAxisMarking(query: String): Seq[WidthOfRoadAxisMarking] = {
    val rows = StaticQuery.queryNA[WidthOfRoadAxisMarkingRow](query)(getPointAsset).iterator.toSeq

    rows.groupBy(_.id).map { case (id, signRows) =>
      val row = signRows.head
      val properties: Seq[Property] = assetRowToProperty(signRows)

      id -> WidthOfRoadAxisMarking(id = row.id, linkId = row.linkId, lon = row.lon, lat = row.lat, mValue = row.mValue,
        floating = row.floating, vvhTimeStamp = row.vvhTimeStamp, municipalityCode = row.municipalityCode, properties,
        createdBy = row.createdBy, createdAt = row.createdAt, modifiedBy = row.modifiedBy, modifiedAt = row.modifiedAt,
        expired = row.expired, linkSource = row.linkSource)
    }.values.toSeq
  }

  private def createOrUpdateWidthOfRoadAxisMarking(widthOfRoadAxisMarking: IncomingWidthOfRoadAxisMarking, id: Long): Unit = {
    widthOfRoadAxisMarking.propertyData.map(propertyWithTypeAndId(WidthOfRoadAxisMarkings.typeId)).foreach { propertyWithTypeAndId =>
      val propertyType = propertyWithTypeAndId._1
      val propertyPublicId = propertyWithTypeAndId._3.publicId
      val propertyId = propertyWithTypeAndId._2.get
      val propertyValues = propertyWithTypeAndId._3.values

      createOrUpdateProperties(id, propertyPublicId, propertyId, propertyType, propertyValues)
    }
  }

  def fetchByFilterWithExpiredLimited(queryFilter: String => String, token: Option[String]): Seq[WidthOfRoadAxisMarking] = {
    val recordLimit = token match {
      case Some(tk) =>
        val (startNum, endNum) = Decode.getPageAndRecordNumber(tk)

        val counter = ", DENSE_RANK() over (ORDER BY a.id) line_number from "
        s" select asset_id, link_id, geometry, start_measure, floating, adjusted_timestamp, municipality_code, property_id, public_id, property_type, required, value, display_value, created_by, created_date," +
          s" modified_by, modified_date, expired, link_source from ( ${queryFilter(query().replace("from", counter))} ) WHERE line_number between $startNum and $endNum"

      case _ => queryFilter(query())
    }
    queryToWidthOfRoadAxisMarking(recordLimit)
  }

  implicit val getPointAsset: GetResult[WidthOfRoadAxisMarkingRow] = new GetResult[WidthOfRoadAxisMarkingRow] {
    def apply(r: PositionedResult): WidthOfRoadAxisMarkingRow = {
      val id = r.nextLong()
      val linkId = r.nextLong()
      val point = r.nextBytesOption().map(bytesToPoint).get
      val mValue = r.nextDouble()
      val floating = r.nextBoolean()
      val vvhTimeStamp = r.nextLong()
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
      val createdBy = r.nextStringOption()
      val createdDateTime = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val modifiedBy = r.nextStringOption()
      val modifiedDateTime = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val expired = r.nextBoolean()
      val linkSource = r.nextInt()

      WidthOfRoadAxisMarkingRow(id, linkId, point.x, point.y, mValue, floating, vvhTimeStamp, municipalityCode, property, createdBy, createdDateTime, modifiedBy, modifiedDateTime, expired, LinkGeomSource(linkSource))
    }
  }

  def create(widthOfRoadAxisMarking: IncomingWidthOfRoadAxisMarking, mValue: Double, username: String, municipality: Int, adjustmentTimestamp: Long, linkSource: LinkGeomSource): Long = {
    val id = Sequences.nextPrimaryKeySeqValue
    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
    sqlu"""
      insert all
        into asset(id, asset_type_id, created_by, created_date, municipality_code)
        values ($id, ${WidthOfRoadAxisMarkings.typeId}, $username, sysdate, $municipality)

        into lrm_position(id, start_measure, link_id, adjusted_timestamp, link_source)
        values ($lrmPositionId, $mValue, ${widthOfRoadAxisMarking.linkId}, $adjustmentTimestamp, ${linkSource.value})

        into asset_link(asset_id, position_id)
        values ($id, $lrmPositionId)

      select * from dual
    """.execute
    updateAssetGeometry(id, Point(widthOfRoadAxisMarking.lon, widthOfRoadAxisMarking.lat))

    createOrUpdateWidthOfRoadAxisMarking(widthOfRoadAxisMarking, id)

    id
  }

  def create(widthOfRoadAxisMarking: IncomingWidthOfRoadAxisMarking, mValue: Double, username: String, municipality: Int, adjustmentTimestamp: Long, linkSource: LinkGeomSource, createdByFromUpdate: Option[String] = Some(""), createdDateTimeFromUpdate: Option[DateTime]): Long = {
    val id = Sequences.nextPrimaryKeySeqValue
    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
    sqlu"""
      insert all
        into asset(id, asset_type_id, created_by, created_date, municipality_code, modified_by, modified_date)
        values ($id, ${WidthOfRoadAxisMarkings.typeId}, $createdByFromUpdate, $createdDateTimeFromUpdate, $municipality, $username, sysdate)

        into lrm_position(id, start_measure, link_id, adjusted_timestamp, link_source, modified_date)
        values ($lrmPositionId, $mValue, ${widthOfRoadAxisMarking.linkId}, $adjustmentTimestamp, ${linkSource.value}, sysdate)

        into asset_link(asset_id, position_id)
        values ($id, $lrmPositionId)

      select * from dual
    """.execute
    updateAssetGeometry(id, Point(widthOfRoadAxisMarking.lon, widthOfRoadAxisMarking.lat))

    createOrUpdateWidthOfRoadAxisMarking(widthOfRoadAxisMarking, id)

    id
  }

  def update(id: Long, widthOfRoadAxisMarking: IncomingWidthOfRoadAxisMarking, mValue: Double, username: String, municipality: Int, adjustedTimeStampOption: Option[Long] = None, linkSource: LinkGeomSource): Long = {
    sqlu""" update asset set municipality_code = $municipality where id = $id """.execute
    updateAssetModified(id, username).execute
    updateAssetGeometry(id, Point(widthOfRoadAxisMarking.lon, widthOfRoadAxisMarking.lat))

    createOrUpdateWidthOfRoadAxisMarking(widthOfRoadAxisMarking, id)

    adjustedTimeStampOption match {
      case Some(adjustedTimeStamp) =>
        sqlu"""
          update lrm_position
           set
           start_measure = $mValue,
           link_id = ${widthOfRoadAxisMarking.linkId},
           adjusted_timestamp = $adjustedTimeStamp,
           link_source = ${linkSource.value}
          where id = (select position_id from asset_link where asset_id = $id)
        """.execute
      case _ =>
        sqlu"""
          update lrm_position
           set
           start_measure = $mValue,
           link_id = ${widthOfRoadAxisMarking.linkId},
           link_source = ${linkSource.value}
          where id = (select position_id from asset_link where asset_id = $id)
        """.execute
    }

    id
  }

  def selectFloatings(floating: Int, lastIdUpdate: Long, batchSize: Int) : Seq[WidthOfRoadAxisMarking] ={
    val query =
      """
        select * from (
          select a.id, pos.link_id, a.geometry, pos.start_measure, a.floating, pos.adjusted_timestamp, a.municipality_code, p.id AS property_id, p.public_id, p.property_type, p.required, ev.value,
            case
              when ev.name_fi is not null then ev.name_fi
              when tpv.value_fi is not null then tpv.value_fi
              when dpv.date_time is not null then to_char(dpv.date_time, 'DD.MM.YYYY')
              when npv.value is not null then to_char(npv.value)
              else null
        end as display_value, a.created_by, a.created_date, a.modified_by,
        a.modified_date, case when a.valid_to <= sysdate then 1 else 0 end as expired, pos.link_source
       from asset a
       join asset_link al on a.id = al.asset_id
       join lrm_position pos on al.position_id = pos.id
       join property p on a.asset_type_id = p.asset_type_id
       left join single_choice_value scv on scv.asset_id = a.id and scv.property_id = p.id and p.property_type = 'single_choice'
       left join text_property_value tpv on tpv.asset_id = a.id and tpv.property_id = p.id and p.property_type = 'text'
       left join date_property_value dpv on dpv.asset_id = a.id and dpv.property_id = p.id and p.property_type = 'date'
       left join number_property_value npv on npv.asset_id = a.id and npv.property_id = p.id and p.property_type = 'number'
       left join enumerated_value ev on scv.enumerated_value_id = ev.id or mcv.enumerated_value_id = ev.ID
    """

    val queryWithFilter = query + s"where a.asset_type_id = ${WidthOfRoadAxisMarkings.typeId} and a.floating = $floating and " +
      s"(a.valid_to > sysdate or a.valid_to is null) and a.id > $lastIdUpdate order by a.id asc) where ROWNUM <= $batchSize"
    queryToWidthOfRoadAxisMarking(queryWithFilter)
  }

  def updateFloatingAsset(widthOfRoadAxisMarkingUpdated: WidthOfRoadAxisMarking): Unit = {
    val id = widthOfRoadAxisMarkingUpdated.id

    sqlu"""update asset set municipality_code = ${widthOfRoadAxisMarkingUpdated.municipalityCode}, floating =  ${widthOfRoadAxisMarkingUpdated.floating} where id = $id""".execute

    updateAssetModified(id, widthOfRoadAxisMarkingUpdated.modifiedBy.get).execute
    updateAssetGeometry(id, Point(widthOfRoadAxisMarkingUpdated.lon, widthOfRoadAxisMarkingUpdated.lat))

    val widthOfRoadAxisMarking: IncomingWidthOfRoadAxisMarking = IncomingWidthOfRoadAxisMarking(widthOfRoadAxisMarkingUpdated.lon, widthOfRoadAxisMarkingUpdated.lat, widthOfRoadAxisMarkingUpdated.linkId,
      widthOfRoadAxisMarkingUpdated.propertyData.map(prop => SimplePointAssetProperty(prop.publicId, prop.values)).toSet)
    createOrUpdateWidthOfRoadAxisMarking(widthOfRoadAxisMarking, id)

    sqlu"""
      update lrm_position
       set
       start_measure = ${widthOfRoadAxisMarkingUpdated.mValue},
       link_id = ${widthOfRoadAxisMarkingUpdated.linkId}
       where id = (select position_id from asset_link where asset_id = $id)
    """.execute
  }

  def propertyWithTypeAndId(typeId: Int)(property: SimplePointAssetProperty): Tuple3[String, Option[Long], SimplePointAssetProperty] = {
    val propertyId = StaticQuery.query[(String, Int), Long](propertyIdByPublicIdAndTypeId).apply(property.publicId, typeId).firstOption.getOrElse(throw new IllegalArgumentException("Property: " + property.publicId + " not found"))
    (StaticQuery.query[Long, String](propertyTypeByPropertyId).apply(propertyId).first, Some(propertyId), property)
  }

  def createOrUpdateProperties(assetId: Long, propertyPublicId: String, propertyId: Long, propertyType: String, propertyValues: Seq[PointAssetValue]) {
    propertyType match {
      case Text =>
        if (propertyValues.size > 1) throw new IllegalArgumentException("Text property must have exactly one value: " + propertyValues)
        if (propertyValues.isEmpty || propertyValues.head.asInstanceOf[PropertyValue].propertyValue.isEmpty) {
          deleteTextProperty(assetId, propertyId).execute
        } else if (PropertyValidator.textPropertyValueDoesNotExist(assetId, propertyId)) {
          insertTextProperty(assetId, propertyId, propertyValues.head.asInstanceOf[PropertyValue].propertyValue).execute
        } else {
          updateTextProperty(assetId, propertyId, propertyValues.head.asInstanceOf[PropertyValue].propertyValue).execute
        }
      case SingleChoice =>
        if (propertyValues.size != 1) throw new IllegalArgumentException("Single choice property must have exactly one value. publicId: " + propertyPublicId)
        if (PropertyValidator.singleChoiceValueDoesNotExist(assetId, propertyId)) {
          insertSingleChoiceProperty(assetId, propertyId, propertyValues.head.asInstanceOf[PropertyValue].propertyValue.toLong).execute
        } else {
          updateSingleChoiceProperty(assetId, propertyId, propertyValues.head.asInstanceOf[PropertyValue].propertyValue.toLong).execute
        }
      case Date =>
        if (propertyValues.size > 1) throw new IllegalArgumentException("Date property must have exactly one value: " + propertyValues)
        val isBlank = propertyValues.isEmpty || propertyValues.head.asInstanceOf[PropertyValue].propertyValue.isEmpty
        if (!PropertyValidator.datePropertyValueDoesNotExist(assetId, propertyId) && isBlank) {
          deleteDateProperty(assetId, propertyId).execute
        } else if (PropertyValidator.datePropertyValueDoesNotExist(assetId, propertyId) && !isBlank) {
          insertDateProperty(assetId, propertyId, DateParser.DatePropertyFormat.parseDateTime(propertyValues.head.asInstanceOf[PropertyValue].propertyValue)).execute
        } else if (!PropertyValidator.datePropertyValueDoesNotExist(assetId, propertyId) && !isBlank) {
          updateDateProperty(assetId, propertyId, DateParser.DatePropertyFormat.parseDateTime(propertyValues.head.asInstanceOf[PropertyValue].propertyValue)).execute
        }
      case Number =>
        if (propertyValues.size > 1) throw new IllegalArgumentException("Number property must have exactly one value: " + propertyValues)
        if (propertyValues.isEmpty || propertyValues.head.asInstanceOf[PropertyValue].propertyValue.isEmpty) {
          deleteNumberProperty(assetId, propertyId).execute
        } else if (PropertyValidator.numberPropertyValueDoesNotExist(assetId, propertyId)) {
          insertNumberProperty(assetId, propertyId, propertyValues.head.asInstanceOf[PropertyValue].propertyValue.toDouble).execute
        } else {
          updateNumberProperty(assetId, propertyId, propertyValues.head.asInstanceOf[PropertyValue].propertyValue.toDouble).execute
        }
      case t: String => throw new UnsupportedOperationException("Asset property type: " + t + " not supported")
    }
  }
}
