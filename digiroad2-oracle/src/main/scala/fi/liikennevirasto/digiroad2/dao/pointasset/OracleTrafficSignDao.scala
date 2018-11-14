package fi.liikennevirasto.digiroad2.dao.pointasset

import fi.liikennevirasto.digiroad2.asset.PropertyTypes._
import fi.liikennevirasto.digiroad2.asset.{PointAssetValue, _}
import fi.liikennevirasto.digiroad2.dao.Queries._
import fi.liikennevirasto.digiroad2.{GeometryUtils, PersistedPointAsset, Point}
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.dao.{Queries, Sequences}
import fi.liikennevirasto.digiroad2.service.pointasset.{IncomingTrafficSign, TrafficSignType}
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedResult, StaticQuery}
import com.github.tototoshi.slick.MySQLJodaSupport._
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase

case class PersistedTrafficSign(id: Long, linkId: Long,
                                lon: Double, lat: Double,
                                mValue: Double, floating: Boolean,
                                vvhTimeStamp: Long,
                                municipalityCode: Int,
                                propertyData: Seq[TrafficSignProperty],
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
                          linkSource: LinkGeomSource,
                          additionalPanel: AdditionalPanelRow)

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
               lp.side_code, ap.additional_sign_type, ap.additional_sign_value, ap.additional_sign_info, ap.form_position
        from asset a
        join asset_link al on a.id = al.asset_id
        join lrm_position lp on al.position_id = lp.id
        join property p on a.asset_type_id = p.asset_type_id
        left join single_choice_value scv on scv.asset_id = a.id and scv.property_id = p.id and p.property_type = 'single_choice'
        left join text_property_value tpv on tpv.asset_id = a.id and tpv.property_id = p.id and p.property_type = 'text'
        left join enumerated_value ev on scv.enumerated_value_id = ev.id
        left join additional_panel ap ON ap.asset_id = a.id AND p.PROPERTY_TYPE = 'additional_panel_type'
      """
    val queryWithFilter = queryFilter(query) + " and (a.valid_to > sysdate or a.valid_to is null)"
    queryToPersistedTrafficSign(queryWithFilter)
  }

  def fetchByRadius(position : Point, meters: Int): Seq[PersistedTrafficSign] = {
    val topLeft = Point(position.x - meters, position.y - meters)
    val bottomRight = Point(position.x + meters, position.y + meters)
    val boundingBoxFilter = OracleDatabase.boundingBoxFilter(BoundingRectangle(topLeft, bottomRight), "a.geometry")
    val filter = s"Where a.asset_type_id = 300 and $boundingBoxFilter"
    fetchByFilter(query => query + filter).
      filter(r => GeometryUtils.geometryLength(Seq(position, Point(r.lon, r.lat))) <= meters)
  }

  def fetchByTurningRestrictions(enumValues: Seq[Long], municipality: Int) : Seq[PersistedTrafficSign] = {
    val values = enumValues.mkString(",")
    val filter = s"where ev.id in ($values) and a.municipality_code = $municipality"
    fetchByFilter(query => query + filter)
  }

  def fetchEnumeratedValueIds( tsType: Seq[TrafficSignType]): Seq[Long] = {
    val values = tsType.map(_.value)

    sql"""select distinct ev.id from PROPERTY p
                join ENUMERATED_VALUE ev on ev.property_id = p.id
                join SINGLE_CHOICE_VALUE sc on sc.ENUMERATED_VALUE_ID = ev.id
                where p.ASSET_TYPE_ID = 300 and p.PUBLIC_ID = 'trafficSigns_type' and ev.VALUE in (#${values.mkString(",")}) """.as[Long].list
  }

  private def queryToPersistedTrafficSign(query: String): Seq[PersistedTrafficSign] = {
    val rows = StaticQuery.queryNA[TrafficSignRow](query).iterator.toSeq
    val rows2 = rows.toArray.groupBy(_.id) //remove

    rows.groupBy(_.id).map { case (id, signRows) =>
      val row = signRows.head
      val properties: Seq[TrafficSignProperty] = assetRowToProperty(signRows)

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
      val panelType = r.nextInt()
      val panelValue = r.nextString()
      val panelInfo = r.nextString()
      val formPosition = r.nextInt()
      val additionalPanel = new AdditionalPanelRow(publicId = propertyPublicId, propertyType = propertyType, panelType = panelType, panelInfo = panelInfo, panelValue = panelValue, formPosition = formPosition)

      TrafficSignRow(id, linkId, point.x, point.y, mValue, floating, vvhTimeStamp, municipalityCode, property, validityDirection, bearing, createdBy, createdAt, modifiedBy, modifiedAt, LinkGeomSource(linkSource), additionalPanel)
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

  def assetRowToProperty(assetRows: Iterable[TrafficSignRow]): Seq[TrafficSignProperty] = {
    assetRows.groupBy(_.property.propertyId).map { case (key, rows) =>
      val row = rows.head
      TrafficSignProperty(
        id = key,
        publicId = row.property.publicId,
        propertyType = row.property.propertyType,
        required = row.property.propertyRequired,
        values = rows.map(assetRow =>
          assetRow.property.propertyType match {
            case SingleChoice | Text | LongText =>
                TextPropertyValue(assetRow.property.propertyValue, Option(assetRow.property.propertyDisplayValue))
            case AdditionalPanelType =>
                AdditionalPanelValue(assetRow.additionalPanel.panelType, Option(assetRow.additionalPanel.panelInfo), Option(assetRow.additionalPanel.panelValue), assetRow.additionalPanel.formPosition)
          }
        ).toSeq)
    }.toSeq
  }

  private def propertyWithTypeAndId(property: SimpleTrafficSignProperty): Tuple3[String, Option[Long], SimpleTrafficSignProperty] = {
    val propertyId = StaticQuery.query[String, Long](propertyIdByPublicId).apply(property.publicId).firstOption.getOrElse(throw new IllegalArgumentException("Property: " + property.publicId + " not found"))
    (StaticQuery.query[Long, String](propertyTypeByPropertyId).apply(propertyId).first, Some(propertyId), property)
  }

  private def singleChoiceValueDoesNotExist(assetId: Long, propertyId: Long) = {
    StaticQuery.query[(Long, Long), Long](existsSingleChoiceProperty).apply((assetId, propertyId)).firstOption.isEmpty
  }

  private def textPropertyValueDoesNotExist(assetId: Long, propertyId: Long) = {
    StaticQuery.query[(Long, Long), Long](existsTextProperty).apply((assetId, propertyId)).firstOption.isEmpty
  }

  private def createOrUpdateProperties(assetId: Long, propertyPublicId: String, propertyId: Long, propertyType: String, propertyValues: Seq[PointAssetValue]) {
    propertyType match {
      case Text | LongText => {
        if (propertyValues.size > 1) throw new IllegalArgumentException("Text property must have exactly one value: " + propertyValues)
        if (propertyValues.isEmpty) {
          deleteTextProperty(assetId, propertyId).execute
        } else if (textPropertyValueDoesNotExist(assetId, propertyId)) {
          insertTextProperty(assetId, propertyId, propertyValues.head.asInstanceOf[TextPropertyValue].propertyValue).execute
        } else {
          updateTextProperty(assetId, propertyId, propertyValues.head.asInstanceOf[TextPropertyValue].propertyValue).execute
        }
      }
      case SingleChoice => {
        if (propertyValues.size != 1) throw new IllegalArgumentException("Single choice property must have exactly one value. publicId: " + propertyPublicId)
        if (singleChoiceValueDoesNotExist(assetId, propertyId)) {
          insertSingleChoiceProperty(assetId, propertyId, propertyValues.head.asInstanceOf[TextPropertyValue].propertyValue.toLong).execute
        } else {
          updateSingleChoiceProperty(assetId, propertyId, propertyValues.head.asInstanceOf[TextPropertyValue].propertyValue.toLong).execute
        }
      }
      case AdditionalPanelType =>
        deleteAdditionalPanelProperty(assetId).execute
        propertyValues.foreach{value =>
          insertAdditionalPanelProperty(assetId, value.asInstanceOf[AdditionalPanelValue]).execute
        }
      case t: String => throw new UnsupportedOperationException("Asset property type: " + t + " not supported")
    }
  }
}