package fi.liikennevirasto.digiroad2.pointasset.oracle

import fi.liikennevirasto.digiroad2.asset.PropertyTypes._
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.Queries._
import fi.liikennevirasto.digiroad2.{IncomingTrafficSign, Point, PersistedPointAsset}
import fi.liikennevirasto.digiroad2.asset.{SimpleProperty, PropertyValue, Property, LinkGeomSource}
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.{Queries, Sequences}
import org.joda.time.DateTime
import slick.jdbc.{StaticQuery, PositionedResult, GetResult}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import com.github.tototoshi.slick.MySQLJodaSupport._

case class PersistedTrafficSign(id: Long, linkId: Long,
                                lon: Double, lat: Double,
                                mValue: Double, floating: Boolean,
                                vvhTimeStamp: Long,
                                municipalityCode: Int,
                                propertyData: Seq[Property],
                                createdBy: Option[String] = None,
                                createdAt: Option[DateTime] = None,
                                modifiedBy: Option[String] = None,
                                modifiedAt: Option[DateTime] = None,
                                validityDirection: Int,
                                bearing: Option[Int],
                                linkSource: LinkGeomSource) extends PersistedPointAsset


case class TrafficSignRow(id: Long, linkId: Long,
                          lon: Double, lat: Double,
                          mValue: Double, floating: Boolean,
                          vvhTimeStamp: Long,
                          municipalityCode: Int,
                          property: PropertyRow,
                          validityDirection: Int,
                          bearing: Option[Int],
                          createdBy: Option[String] = None,
                          createdAt: Option[DateTime] = None,
                          modifiedBy: Option[String] = None,
                          modifiedAt: Option[DateTime] = None,
                          linkSource: LinkGeomSource)

object OracleTrafficSignDao {

  def fetchByFilter(queryFilter: String => String): Seq[PersistedTrafficSign] = {
    val query =
      """
        select a.id, lp.link_id, a.geometry, lp.start_measure, a.floating, lp.adjusted_timestamp,a.municipality_code,
               p.id, p.public_id, p.property_type, p.required, ev.value,
               case
                when ev.name_fi is not null then ev.name_fi
                when tpv.value_fi is not null then tpv.value_fi
                else null
               end as display_value, a.created_by, a.created_date, a.modified_by, a.modified_date, lp.link_source, a.bearing,
               lp.side_code
        from asset a
        join asset_link al on a.id = al.asset_id
        join lrm_position lp on al.position_id = lp.id
        join property p on a.asset_type_id = p.asset_type_id
        left join single_choice_value scv on scv.asset_id = a.id and scv.property_id = p.id and p.property_type = 'single_choice'
        left join text_property_value tpv on tpv.asset_id = a.id and tpv.property_id = p.id and p.property_type = 'text'
        left join enumerated_value ev on scv.enumerated_value_id = ev.id
      """
    val queryWithFilter = queryFilter(query) + " and (a.valid_to > sysdate or a.valid_to is null)"
    queryToPersistedTrafficSign(queryWithFilter)
  }

  private def queryToPersistedTrafficSign(query: String): Seq[PersistedTrafficSign] = {
    val rows = StaticQuery.queryNA[TrafficSignRow](query).iterator.toSeq

    rows.groupBy(_.id).map { case (id, signRows) =>
      val row = signRows.head
      val properties: Seq[Property] = assetRowToProperty(signRows)

      id -> PersistedTrafficSign(id = row.id, linkId = row.linkId, lon = row.lon, lat = row.lat, mValue = row.mValue,
        floating = row.floating, vvhTimeStamp = row.vvhTimeStamp, municipalityCode = row.municipalityCode, properties,
        createdBy = row.createdBy, createdAt = row.createdAt, modifiedBy = row.modifiedBy, modifiedAt = row.modifiedAt,
        linkSource = row.linkSource, validityDirection = row.validityDirection, bearing = row.bearing)
    }.values.toSeq
  }

  implicit val getTrafficSignRow = new GetResult[TrafficSignRow] {
    def apply(r: PositionedResult) = {
      val id = r.nextLong()
      val linkId = r.nextLong()
      val point = r.nextBytesOption.map(bytesToPoint).get
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
      val property = new PropertyRow(
        propertyId = propertyId,
        publicId = propertyPublicId,
        propertyType = propertyType,
        propertyRequired = propertyRequired,
        propertyValue = propertyValue.getOrElse(propertyDisplayValue.getOrElse("")).toString,
        propertyDisplayValue = propertyDisplayValue.orNull)
      val createdBy = r.nextStringOption()
      val createdAt = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val modifiedBy = r.nextStringOption()
      val modifiedAt = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val linkSource = r.nextInt()
      val bearing = r.nextIntOption()
      val validityDirection = r.nextInt()

      TrafficSignRow(id, linkId, point.x, point.y, mValue, floating, vvhTimeStamp, municipalityCode, property, validityDirection, bearing, createdBy, createdAt, modifiedBy, modifiedAt, LinkGeomSource(linkSource))
    }
  }

  def createFloating(trafficSign: IncomingTrafficSign, mValue: Double, username: String, municipality: Int, adjustmentTimestamp: Long, linkSource: LinkGeomSource, floating: Boolean = false): Long = {
    val id = Sequences.nextPrimaryKeySeqValue
    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
    sqlu"""
      insert all
        into asset(id, asset_type_id, created_by, created_date, municipality_code, bearing, floating)
        values ($id, 300, $username, sysdate, $municipality, ${trafficSign.bearing}, $floating)

        into lrm_position(id, start_measure, link_id, adjusted_timestamp, link_source, side_code)
        values ($lrmPositionId, $mValue, ${trafficSign.linkId}, $adjustmentTimestamp, ${linkSource.value}, ${trafficSign.validityDirection})

        into asset_link(asset_id, position_id)
        values ($id, $lrmPositionId)

      select * from dual
    """.execute
    updateAssetGeometry(id, Point(trafficSign.lon, trafficSign.lat))

    trafficSign.propertyData.map(propertyWithTypeAndId).foreach { propertyWithTypeAndId =>
      val propertyType = propertyWithTypeAndId._1
      val propertyPublicId = propertyWithTypeAndId._3.publicId
      val propertyId = propertyWithTypeAndId._2.get
      val propertyValues = propertyWithTypeAndId._3.values

      createOrUpdateProperties(id, propertyPublicId, propertyId, propertyType, propertyValues)
    }
    id
  }

  def create(trafficSign: IncomingTrafficSign, mValue: Double, username: String, municipality: Int, adjustmentTimestamp: Long, linkSource: LinkGeomSource): Long = {
    val id = Sequences.nextPrimaryKeySeqValue
    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
    sqlu"""
      insert all
        into asset(id, asset_type_id, created_by, created_date, municipality_code, bearing)
        values ($id, 300, $username, sysdate, $municipality, ${trafficSign.bearing})

        into lrm_position(id, start_measure, link_id, adjusted_timestamp, link_source, side_code)
        values ($lrmPositionId, $mValue, ${trafficSign.linkId}, $adjustmentTimestamp, ${linkSource.value}, ${trafficSign.validityDirection})

        into asset_link(asset_id, position_id)
        values ($id, $lrmPositionId)

      select * from dual
    """.execute
    updateAssetGeometry(id, Point(trafficSign.lon, trafficSign.lat))

    trafficSign.propertyData.map(propertyWithTypeAndId).foreach { propertyWithTypeAndId =>
      val propertyType = propertyWithTypeAndId._1
      val propertyPublicId = propertyWithTypeAndId._3.publicId
      val propertyId = propertyWithTypeAndId._2.get
      val propertyValues = propertyWithTypeAndId._3.values

      createOrUpdateProperties(id, propertyPublicId, propertyId, propertyType, propertyValues)
    }
    id
  }

  def create(trafficSign: IncomingTrafficSign, mValue: Double, username: String, municipality: Int, adjustmentTimestamp: Long, linkSource: LinkGeomSource, createdByFromUpdate: Option[String] = Some(""), createdDateTimeFromUpdate: Option[DateTime]): Long = {
    val id = Sequences.nextPrimaryKeySeqValue
    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
    sqlu"""
      insert all
        into asset(id, asset_type_id, created_by, created_date, municipality_code, bearing, modified_by, modified_date)
        values ($id, 300, $createdByFromUpdate, $createdDateTimeFromUpdate, $municipality, ${trafficSign.bearing}, $username, sysdate)

        into lrm_position(id, start_measure, link_id, adjusted_timestamp, link_source, side_code, modified_date)
        values ($lrmPositionId, $mValue, ${trafficSign.linkId}, $adjustmentTimestamp, ${linkSource.value}, ${trafficSign.validityDirection}, sysdate)

        into asset_link(asset_id, position_id)
        values ($id, $lrmPositionId)

      select * from dual
    """.execute
    updateAssetGeometry(id, Point(trafficSign.lon, trafficSign.lat))

    trafficSign.propertyData.map(propertyWithTypeAndId).foreach { propertyWithTypeAndId =>
      val propertyType = propertyWithTypeAndId._1
      val propertyPublicId = propertyWithTypeAndId._3.publicId
      val propertyId = propertyWithTypeAndId._2.get
      val propertyValues = propertyWithTypeAndId._3.values

      createOrUpdateProperties(id, propertyPublicId, propertyId, propertyType, propertyValues)
    }
    id
  }

  def update(id: Long, trafficSign: IncomingTrafficSign, mValue: Double, municipality: Int,
             username: String, adjustedTimeStampOption: Option[Long] = None, linkSource: LinkGeomSource) = {
    sqlu""" update asset set municipality_code = $municipality, bearing=${trafficSign.bearing} where id = $id """.execute
    updateAssetModified(id, username).execute
    updateAssetGeometry(id, Point(trafficSign.lon, trafficSign.lat))

    trafficSign.propertyData.map(propertyWithTypeAndId).foreach { propertyWithTypeAndId =>
      val propertyType = propertyWithTypeAndId._1
      val propertyPublicId = propertyWithTypeAndId._3.publicId
      val propertyId = propertyWithTypeAndId._2.get
      val propertyValues = propertyWithTypeAndId._3.values

      createOrUpdateProperties(id, propertyPublicId, propertyId, propertyType, propertyValues)
    }

    adjustedTimeStampOption match {
      case Some(adjustedTimeStamp) =>
        sqlu"""
          update lrm_position
           set
           start_measure = $mValue,
           link_id = ${trafficSign.linkId},
           side_code = ${trafficSign.validityDirection},
           adjusted_timestamp = ${adjustedTimeStamp},
           link_source = ${linkSource.value}
           where id = (select position_id from asset_link where asset_id = $id)
        """.execute
      case _ =>
        sqlu"""
          update lrm_position
           set
           start_measure = $mValue,
           link_id = ${trafficSign.linkId},
           side_code = ${trafficSign.validityDirection},
           link_source = ${linkSource.value}
           where id = (select position_id from asset_link where asset_id = $id)
        """.execute
    }
    id
  }

  def updateAssetGeometry(id: Long, point: Point): Unit = {
    val x = point.x
    val y = point.y
    sqlu"""
      UPDATE asset
        SET geometry = MDSYS.SDO_GEOMETRY(4401,
                                          3067,
                                          NULL,
                                          MDSYS.SDO_ELEM_INFO_ARRAY(1,1,1),
                                          MDSYS.SDO_ORDINATE_ARRAY($x, $y, 0, 0)
                                         )
        WHERE id = $id
    """.execute
  }

  def insertSingleChoiceProperty(assetId: Long, propertyId: Long, value: Long) = {
    sqlu"""
      insert into single_choice_value(asset_id, enumerated_value_id, property_id, modified_date)
      values ($assetId, (select id from enumerated_value where property_id = $propertyId and value = $value), $propertyId, SYSDATE)
    """
  }

  private def getSignTypePropertyId: Long = {
    StaticQuery.query[String, Long](Queries.propertyIdByPublicId).apply("trafficSigns_type").first
  }

  private def getValuePropertyId: Long = {
    StaticQuery.query[String, Long](Queries.propertyIdByPublicId).apply("trafficSigns_value").first
  }

  private def getAdditionalInfoPropertyId: Long = {
    StaticQuery.query[String, Long](Queries.propertyIdByPublicId).apply("trafficSigns_info").first
  }

  def assetRowToProperty(assetRows: Iterable[TrafficSignRow]): Seq[Property] = {
    assetRows.groupBy(_.property.propertyId).map { case (key, rows) =>
      val row = rows.head
      Property(
        id = key,
        publicId = row.property.publicId,
        propertyType = row.property.propertyType,
        required = row.property.propertyRequired,
        values = rows.map(assetRow =>
          PropertyValue(
            assetRow.property.propertyValue,
            Option(assetRow.property.propertyDisplayValue))
        ).filter(_.propertyDisplayValue.isDefined).toSeq)
    }.toSeq
  }

  private def propertyWithTypeAndId(property: SimpleProperty): Tuple3[String, Option[Long], SimpleProperty] = {
    val propertyId = StaticQuery.query[String, Long](propertyIdByPublicId).apply(property.publicId).firstOption.getOrElse(throw new IllegalArgumentException("Property: " + property.publicId + " not found"))
    (StaticQuery.query[Long, String](propertyTypeByPropertyId).apply(propertyId).first, Some(propertyId), property)
  }

  private def singleChoiceValueDoesNotExist(assetId: Long, propertyId: Long) = {
    StaticQuery.query[(Long, Long), Long](existsSingleChoiceProperty).apply((assetId, propertyId)).firstOption.isEmpty
  }

  private def textPropertyValueDoesNotExist(assetId: Long, propertyId: Long) = {
    StaticQuery.query[(Long, Long), Long](existsTextProperty).apply((assetId, propertyId)).firstOption.isEmpty
  }

  private def createOrUpdateProperties(assetId: Long, propertyPublicId: String, propertyId: Long, propertyType: String, propertyValues: Seq[PropertyValue]) {
    propertyType match {
      case Text | LongText => {
        if (propertyValues.size > 1) throw new IllegalArgumentException("Text property must have exactly one value: " + propertyValues)
        if (propertyValues.isEmpty) {
          deleteTextProperty(assetId, propertyId).execute
        } else if (textPropertyValueDoesNotExist(assetId, propertyId)) {
          insertTextProperty(assetId, propertyId, propertyValues.head.propertyValue).execute
        } else {
          updateTextProperty(assetId, propertyId, propertyValues.head.propertyValue).execute
        }
      }
      case SingleChoice => {
        if (propertyValues.size != 1) throw new IllegalArgumentException("Single choice property must have exactly one value. publicId: " + propertyPublicId)
        if (singleChoiceValueDoesNotExist(assetId, propertyId)) {
          insertSingleChoiceProperty(assetId, propertyId, propertyValues.head.propertyValue.toLong).execute
        } else {
          updateSingleChoiceProperty(assetId, propertyId, propertyValues.head.propertyValue.toLong).execute
        }
      }
      case t: String => throw new UnsupportedOperationException("Asset property type: " + t + " not supported")
    }
  }
}