package fi.liikennevirasto.digiroad2.dao.pointasset

import fi.liikennevirasto.digiroad2.asset.PropertyTypes._
import fi.liikennevirasto.digiroad2.asset.{PointAssetValue, _}
import fi.liikennevirasto.digiroad2.dao.Queries._
import fi.liikennevirasto.digiroad2.{GeometryUtils, PersistedPoint, Point, TrafficSignType}
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import com.github.tototoshi.slick.MySQLJodaSupport._
import fi.liikennevirasto.digiroad2.dao.{Queries, Sequences}
import fi.liikennevirasto.digiroad2.postgis.{MassQuery, PostGISDatabase}
import fi.liikennevirasto.digiroad2.service.pointasset.IncomingTrafficSign
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedResult, StaticQuery}
import scala.util.Try

case class PersistedTrafficSign(id: Long, linkId: String,
                                lon: Double, lat: Double,
                                mValue: Double, floating: Boolean,
                                timeStamp: Long,
                                municipalityCode: Int,
                                propertyData: Seq[Property],
                                createdBy: Option[String] = None,
                                createdAt: Option[DateTime] = None,
                                modifiedBy: Option[String] = None,
                                modifiedAt: Option[DateTime] = None,
                                validityDirection: Int,
                                bearing: Option[Int],
                                linkSource: LinkGeomSource,
                                expired: Boolean = false,
                                externalId: Option[String] = None) extends PersistedPoint {
  override def getValidityDirection: Option[Int] = Some(this.validityDirection)
  override def getBearing: Option[Int] = this.bearing
}

case class TrafficSignRow(id: Long, linkId: String,
                          lon: Double, lat: Double,
                          mValue: Double, floating: Boolean,
                          timeStamp: Long,
                          municipalityCode: Int,
                          property: PropertyRow,
                          validityDirection: Int,
                          bearing: Option[Int],
                          createdBy: Option[String] = None,
                          createdAt: Option[DateTime] = None,
                          modifiedBy: Option[String] = None,
                          modifiedAt: Option[DateTime] = None,
                          linkSource: LinkGeomSource,
                          additionalPanel: Option[AdditionalPanelRow] = None,
                          expired: Boolean = false,
                          externalId: Option[String] = None)

object PostGISTrafficSignDao {

  private def query() =
    """
        select a.id as asset_id, pos.link_id, a.geometry, pos.start_measure, a.floating, pos.adjusted_timestamp,a.municipality_code,
               p.id as property_id, p.public_id, p.property_type, p.required, ev.value,
               case
                when ev.name_fi is not null then ev.name_fi
                when tpv.value_fi is not null then tpv.value_fi
                when dpv.date_time is not null then to_char(dpv.date_time, 'DD.MM.YYYY')
                when npv.value is not null then cast(npv.value as text)
                else null
               end as display_value, a.created_by, a.created_date, a.modified_by, a.modified_date, pos.link_source, a.bearing,
                pos.side_code, ap.additional_sign_type, ap.additional_sign_value, ap.additional_sign_info, ap.form_position,
               ap.additional_sign_text, ap.additional_sign_size, ap.additional_sign_coating_type, ap.additional_sign_panel_color,
               case when a.valid_to <= current_timestamp then 1 else 0 end as expired, a.external_id
        from asset a
        join asset_link al on a.id = al.asset_id
        join lrm_position pos on al.position_id = pos.id
        join property p on a.asset_type_id = p.asset_type_id
        left join multiple_choice_value mcv on mcv.asset_id = a.id and mcv.property_id = p.id and p.property_type = 'checkbox'
        left join single_choice_value scv on scv.asset_id = a.id and scv.property_id = p.id and p.property_type = 'single_choice'
        left join text_property_value tpv on tpv.asset_id = a.id and tpv.property_id = p.id and p.property_type = 'text'
        left join date_property_value dpv on dpv.asset_id = a.id and dpv.property_id = p.id and p.property_type = 'date'
        left join number_property_value npv on npv.asset_id = a.id and npv.property_id = p.id and p.property_type = 'number'
        left join enumerated_value ev on scv.enumerated_value_id = ev.id or mcv.enumerated_value_id = ev.id
        left join additional_panel ap ON ap.asset_id = a.id AND p.PROPERTY_TYPE = 'additional_panel_type'
      """

  def fetchByFilter(queryFilter: String => String): Seq[PersistedTrafficSign] = {
    val queryWithFilter = queryFilter(query()) + " and (a.valid_to > current_timestamp or a.valid_to is null)"
    queryToPersistedTrafficSign(queryWithFilter)
  }

  def fetchByFilterWithExpired(queryFilter: String => String): Seq[PersistedTrafficSign] = {
    val queryWithFilter = queryFilter(query())
    queryToPersistedTrafficSign(queryWithFilter)
  }


  def fetchByFilterWithExpiredByIds(ids: Set[Long]): Seq[PersistedTrafficSign] = {
    MassQuery.withIds(ids) { idTableName =>
      val filter = s"join $idTableName i on i.id = a.id "
      queryToPersistedTrafficSign( query + " "+ filter )
    }

  }

  def fetchByFilterWithExpiredLimited(queryFilter: String => String, token: Option[String]): Seq[PersistedTrafficSign] = {
    val recordLimit = token match {
      case Some(tk) =>
        val (startNum, endNum) = Decode.getPageAndRecordNumber(tk)

        val counter = ", DENSE_RANK() over (ORDER BY a.id) line_number from "
        s"select asset_id, link_id, geometry, start_measure, floating, adjusted_timestamp, municipality_code, property_id, public_id, " +
        s"property_type, required, value, display_value, created_by, created_date, modified_by, modified_date, link_source, " +
        s"bearing, side_code, additional_sign_type, additional_sign_value, additional_sign_info, form_position, " +
        s"additional_sign_text, additional_sign_size, additional_sign_coating_type, additional_sign_panel_color, expired, external_id" +
        s" from ( ${queryFilter(query().replace("from", counter))} ) derivedAsset WHERE line_number between $startNum and $endNum"

      case _ => queryFilter(query())
    }

    queryToPersistedTrafficSign(recordLimit)
  }

  def fetchByRadius(position : Point, meters: Int): Seq[PersistedTrafficSign] = {
    val topLeft = Point(position.x - meters, position.y - meters)
    val bottomRight = Point(position.x + meters, position.y + meters)
    val boundingBoxFilter = PostGISDatabase.boundingBoxFilter(BoundingRectangle(topLeft, bottomRight), "a.geometry")
    val filter = s"Where a.asset_type_id = 300 and $boundingBoxFilter"
    fetchByFilter(query => query + filter).
      filter(r => GeometryUtils.geometryLength(Seq(position, Point(r.lon, r.lat))) <= meters)
  }

  def fetchByTypeValues(enumValues: Seq[Long], municipality: Int) : Seq[PersistedTrafficSign] = {
    val values = enumValues.mkString(",")
    val filter = s"where ev.id in ($values) and a.municipality_code = $municipality"
    fetchByFilter(query => query + filter)
  }

  def fetchEnumeratedValueIds( tsType: Seq[TrafficSignType]): Seq[Long] = {
    val values = tsType.map(_.OTHvalue)

    sql"""select distinct ev.id from PROPERTY p
                join ENUMERATED_VALUE ev on ev.property_id = p.id
                join SINGLE_CHOICE_VALUE sc on sc.ENUMERATED_VALUE_ID = ev.id
                where p.ASSET_TYPE_ID = 300 and p.PUBLIC_ID = 'trafficSigns_type' and ev.VALUE in (#${values.mkString(",")}) """.as[Long].list
  }

  def fetchByLinkId(linkIds : Seq[String]): Seq[PersistedTrafficSign] = {
    val rows = MassQuery.withStringIds(linkIds.toSet) { idTableName =>
      sql"""
        select a.id, lp.link_id, a.geometry, lp.start_measure, a.floating, lp.adjusted_timestamp,a.municipality_code,
               p.id, p.public_id, p.property_type, p.required, ev.value,
               case
                when ev.name_fi is not null then ev.name_fi
                when tpv.value_fi is not null then tpv.value_fi
                when dpv.date_time is not null then to_char(dpv.date_time, 'DD.MM.YYYY')
                else null
               end as display_value, a.created_by, a.created_date, a.modified_by, a.modified_date, lp.link_source, a.bearing,
               lp.side_code, ap.additional_sign_type, ap.additional_sign_value, ap.additional_sign_info, ap.form_position,
               ap.additional_sign_text, ap.additional_sign_size, ap.additional_sign_coating_type, ap.additional_sign_panel_color,
               case when a.valid_to <= current_timestamp then 1 else 0 end as expired, a.external_id
        from asset a
        join asset_link al on a.id = al.asset_id
        join lrm_position lp on al.position_id = lp.id
        join property p on a.asset_type_id = p.asset_type_id
        join #$idTableName i on i.id = lp.link_id
        left join single_choice_value scv on scv.asset_id = a.id and scv.property_id = p.id and p.property_type = 'single_choice'
        left join text_property_value tpv on tpv.asset_id = a.id and tpv.property_id = p.id and p.property_type = 'text'
        left join date_property_value dpv on dpv.asset_id = a.id and dpv.property_id = p.id and p.property_type = 'date'
        left join enumerated_value ev on scv.enumerated_value_id = ev.id
        left join additional_panel ap ON ap.asset_id = a.id AND p.PROPERTY_TYPE = 'additional_panel_type'
        where a.asset_type_id = 300
        and (a.valid_to > current_timestamp or a.valid_to is null)
      """.as[TrafficSignRow](getTrafficSignRow).list
    }
    groupTrafficSign(rows)
  }

  private def groupTrafficSign(rows: Seq[TrafficSignRow]): Seq[PersistedTrafficSign] = {
    rows.groupBy(_.id).map { case (id, signRows) =>
      val row = signRows.head
      val properties: Seq[Property] = assetRowToProperty(signRows)

      id -> PersistedTrafficSign(id = row.id, linkId = row.linkId, lon = row.lon, lat = row.lat, mValue = row.mValue,
        floating = row.floating, timeStamp = row.timeStamp, municipalityCode = row.municipalityCode, properties,
        createdBy = row.createdBy, createdAt = row.createdAt, modifiedBy = row.modifiedBy, modifiedAt = row.modifiedAt,
        linkSource = row.linkSource, validityDirection = row.validityDirection, bearing = row.bearing, expired = row.expired,
        externalId = row.externalId)
    }.values.toSeq
  }

  private def queryToPersistedTrafficSign(query: String): Seq[PersistedTrafficSign] = {
    val rows = StaticQuery.queryNA[TrafficSignRow](query)(getTrafficSignRow).iterator.toSeq
    groupTrafficSign(rows)
  }

  private def createOrUpdateTrafficSign(trafficSign: IncomingTrafficSign, id: Long): Unit ={
    trafficSign.propertyData.map(propertyWithTypeAndId(TrafficSigns.typeId)).foreach { propertyWithTypeAndId =>
      val propertyType = propertyWithTypeAndId._1
      val propertyPublicId = propertyWithTypeAndId._3.publicId
      val propertyId = propertyWithTypeAndId._2.get
      val propertyValues = propertyWithTypeAndId._3.values

      createOrUpdateProperties(id, propertyPublicId, propertyId, propertyType, propertyValues)
    }
  }

  implicit val getTrafficSignRow = new GetResult[TrafficSignRow] {
    def apply(r: PositionedResult) = {
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
      val property = PropertyRow(
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
      val optPanelType = r.nextIntOption()
      val panelValue = r.nextStringOption()
      val panelInfo = r.nextStringOption()
      val formPosition = r.nextInt()
      val panelText = r.nextStringOption()
      val panelSize = r.nextInt()
      val panelCoatingType = r.nextInt()
      val panelColor = r.nextInt()
      val additionalPanel = optPanelType match {
        case Some(panelType) => Some(AdditionalPanelRow(propertyPublicId, propertyType, panelType, panelInfo.getOrElse(""), panelValue.getOrElse(""), formPosition, panelText.getOrElse(""), panelSize, panelCoatingType, panelColor))
        case _ => None
      }
      val expired = r.nextBoolean()
      val externalId = r.nextStringOption()

      TrafficSignRow(id, linkId, point.x, point.y, mValue, floating, timeStamp, municipalityCode, property,
        validityDirection, bearing, createdBy, createdAt, modifiedBy, modifiedAt, LinkGeomSource(linkSource),
        additionalPanel, expired, externalId)
    }
  }

  def createFloating(trafficSign: IncomingTrafficSign, mValue: Double, username: String, municipality: Int, adjustmentTimestamp: Long, linkSource: LinkGeomSource, floating: Boolean = false): Long = {
    val id = Sequences.nextPrimaryKeySeqValue
    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
    sqlu"""
        insert into asset(id, asset_type_id, created_by, created_date, municipality_code, bearing, floating)
        values ($id, 300, $username, current_timestamp, $municipality, ${trafficSign.bearing}, $floating);

        insert into lrm_position(id, start_measure, link_id, adjusted_timestamp, link_source, side_code)
        values ($lrmPositionId, $mValue, ${trafficSign.linkId}, $adjustmentTimestamp, ${linkSource.value}, ${trafficSign.validityDirection});

        insert into asset_link(asset_id, position_id)
        values ($id, $lrmPositionId);
    """.execute
    updateAssetGeometry(id, Point(trafficSign.lon, trafficSign.lat))

    createOrUpdateTrafficSign(trafficSign, id)

    id
  }

  def create(trafficSign: IncomingTrafficSign, mValue: Double, username: String, municipality: Int, adjustmentTimestamp: Long, linkSource: LinkGeomSource): Long = {
    val id = Sequences.nextPrimaryKeySeqValue
    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
    sqlu"""
        insert into asset(id, asset_type_id, created_by, created_date, municipality_code, bearing)
        values ($id, 300, $username, current_timestamp, $municipality, ${trafficSign.bearing});

        insert into lrm_position(id, start_measure, link_id, adjusted_timestamp, link_source, side_code)
        values ($lrmPositionId, $mValue, ${trafficSign.linkId}, $adjustmentTimestamp, ${linkSource.value}, ${trafficSign.validityDirection});

        insert into asset_link(asset_id, position_id)
        values ($id, $lrmPositionId);
    """.execute
    updateAssetGeometry(id, Point(trafficSign.lon, trafficSign.lat))

    createOrUpdateTrafficSign(trafficSign, id)

    id
  }

  def create(trafficSign: IncomingTrafficSign, mValue: Double, username: String, municipality: Int, adjustmentTimestamp: Long,
             linkSource: LinkGeomSource, createdByFromUpdate: Option[String] = Some(""),
             createdDateTimeFromUpdate: Option[DateTime], externalIdFromUpdate: Option[String],
             fromPointAssetUpdater: Boolean = false, modifiedByFromUpdate: Option[String] = None, modifiedDateTimeFromUpdate: Option[DateTime] = None): Long = {
    val id = Sequences.nextPrimaryKeySeqValue
    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue

    val modifiedBy = if (fromPointAssetUpdater) modifiedByFromUpdate.getOrElse(null) else username
    val modifiedAt = if (fromPointAssetUpdater) modifiedDateTimeFromUpdate.getOrElse(null) else DateTime.now()
    sqlu"""
        insert into asset(id, external_id, asset_type_id, created_by, created_date, municipality_code, bearing, modified_by, modified_date)
        values ($id, $externalIdFromUpdate, 300, $createdByFromUpdate, $createdDateTimeFromUpdate, $municipality, ${trafficSign.bearing}, $modifiedBy, $modifiedAt);

        insert into lrm_position(id, start_measure, link_id, adjusted_timestamp, link_source, side_code, modified_date)
        values ($lrmPositionId, $mValue, ${trafficSign.linkId}, $adjustmentTimestamp, ${linkSource.value}, ${trafficSign.validityDirection}, current_timestamp);

        insert into asset_link(asset_id, position_id)
        values ($id, $lrmPositionId);
    """.execute
    updateAssetGeometry(id, Point(trafficSign.lon, trafficSign.lat))

    createOrUpdateTrafficSign(trafficSign, id)

    id
  }

  def update(id: Long, trafficSign: IncomingTrafficSign, mValue: Double, municipality: Int,
             username: String, adjustedTimeStampOption: Option[Long] = None, linkSource: LinkGeomSource, fromPointAssetUpdater: Boolean = false) = {
    sqlu""" update asset set municipality_code = $municipality, bearing=${trafficSign.bearing} where id = $id """.execute
    if (!fromPointAssetUpdater) updateAssetModified(id, username).execute
    updateAssetGeometry(id, Point(trafficSign.lon, trafficSign.lat))

    createOrUpdateTrafficSign(trafficSign, id)

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
    Queries.updateAssetGeometry(id,point)
  }

  def insertSingleChoiceProperty(assetId: Long, propertyId: Long, value: Long) = {
    sqlu"""
      insert into single_choice_value(asset_id, enumerated_value_id, property_id, modified_date)
      values ($assetId, (select id from enumerated_value where property_id = $propertyId and value = $value), $propertyId, current_timestamp)
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

  def assetRowToProperty(assetRows: Seq[TrafficSignRow]): Seq[Property] = {

    assetRows.groupBy(_.property.propertyId).map { case (key, rows) =>
      val row = rows.head
      Property(
        id = key,
        publicId = row.property.publicId,
        propertyType = row.property.propertyType,
        required = row.property.propertyRequired,
        values = rows.flatMap { assetRow =>
          assetRow.property.propertyType match {
            case Number =>
              val propertyValue = assetRow.property.propertyValue.replace(',', '.')
              val propertyDisplayValue = Try(assetRow.property.propertyDisplayValue.replace(',', '.')).toOption
              Seq(PropertyValue(propertyValue, propertyDisplayValue))
            case AdditionalPanelType =>
              assetRow.additionalPanel match {
                case Some(panel) => Seq(AdditionalPanel(panel.panelType, panel.panelInfo, panel.panelValue, panel.formPosition, panel.panelText, panel.panelSize, panel.panelCoatingType, panel.panelColor))
                case _ => Seq()
              }
            case _  =>

              val finalValue = PropertyValidator.propertyValueValidation(assetRow.property.publicId, assetRow.property.propertyValue )

              Seq(PropertyValue(finalValue, Option(assetRow.property.propertyDisplayValue)))
          }
        })
    }.toSeq
  }

  private def propertyWithTypeAndId(typeId: Int)(property: SimplePointAssetProperty): Tuple3[String, Option[Long], SimplePointAssetProperty] = {
    val propertyId = StaticQuery.query[(String, Int), Long](propertyIdByPublicIdAndTypeId).apply(property.publicId, typeId).firstOption.getOrElse(throw new IllegalArgumentException("Property: " + property.publicId + " not found"))
    (StaticQuery.query[Long, String](propertyTypeByPropertyId).apply(propertyId).first, Some(propertyId), property)
  }

  private def singleChoiceValueDoesNotExist(assetId: Long, propertyId: Long) = {
    StaticQuery.query[(Long, Long), Long](existsSingleChoiceProperty).apply((assetId, propertyId)).firstOption.isEmpty
  }

  private def textPropertyValueDoesNotExist(assetId: Long, propertyId: Long) = {
    StaticQuery.query[(Long, Long), Long](existsTextProperty).apply((assetId, propertyId)).firstOption.isEmpty
  }

  private def numberPropertyValueDoesNotExist(assetId: Long, propertyId: Long) = {
    StaticQuery.query[(Long, Long), Long](existsNumberProperty).apply((assetId, propertyId)).firstOption.isEmpty
  }

  private def multipleChoiceValueDoesNotExist(assetId: Long, propertyId: Long): Boolean = {
    StaticQuery.query[(Long, Long), Long](existsMultipleChoiceProperty).apply((assetId, propertyId)).firstOption.isEmpty
  }

  private def datePropertyValueDoesNotExist(assetId: Long, propertyId: Long) = {
    StaticQuery.query[(Long, Long), Long](existsDateProperty).apply((assetId, propertyId)).firstOption.isEmpty
  }

  def createOrUpdateProperties(assetId: Long, propertyPublicId: String, propertyId: Long, propertyType: String, propertyValues: Seq[PointAssetValue]) {
    propertyType match {
      case Text =>
        if (propertyValues.size > 1) throw new IllegalArgumentException("Text property must have exactly one value: " + propertyValues)
        if (propertyValues.isEmpty || propertyValues.head.asInstanceOf[PropertyValue].propertyValue.isEmpty) {
          deleteTextProperty(assetId, propertyId).execute
        } else if (textPropertyValueDoesNotExist(assetId, propertyId)) {
          insertTextProperty(assetId, propertyId, propertyValues.head.asInstanceOf[PropertyValue].propertyValue).execute
        } else {
          updateTextProperty(assetId, propertyId, propertyValues.head.asInstanceOf[PropertyValue].propertyValue).execute
        }
      case SingleChoice =>
        if (propertyValues.size != 1) throw new IllegalArgumentException("Single choice property must have exactly one value. publicId: " + propertyPublicId)
        if (singleChoiceValueDoesNotExist(assetId, propertyId)) {
          insertSingleChoiceProperty(assetId, propertyId, propertyValues.head.asInstanceOf[PropertyValue].propertyValue.toLong).execute
        } else {
          updateSingleChoiceProperty(assetId, propertyId, propertyValues.head.asInstanceOf[PropertyValue].propertyValue.toLong).execute
        }
      case AdditionalPanelType =>
        if (propertyValues.size > 5) throw new IllegalArgumentException("A maximum of 5 " + propertyPublicId + " allowed per traffic sign.")
        deleteAdditionalPanelProperty(assetId).execute
        propertyValues.foreach{value =>
          val additionalPanel = value.asInstanceOf[AdditionalPanel]
          additionalPanel.verifyCorrectInputOnAdditionalPanel
          insertAdditionalPanelProperty(assetId, additionalPanel).execute
        }
      case CheckBox =>
        if (propertyValues.size > 1) throw new IllegalArgumentException("Multiple choice only allows values between 0 and 1.")
        if (multipleChoiceValueDoesNotExist(assetId, propertyId)) {
          insertMultipleChoiceValue(assetId, propertyId, propertyValues.head.asInstanceOf[PropertyValue].propertyValue.toLong).execute
        } else {
          updateMultipleChoiceValue(assetId, propertyId, propertyValues.head.asInstanceOf[PropertyValue].propertyValue.toLong).execute
        }
      case Date =>
        if (propertyValues.size > 1) throw new IllegalArgumentException("Date property must have exactly one value: " + propertyValues)
        val isBlank =  propertyValues.isEmpty || propertyValues.head.asInstanceOf[PropertyValue].propertyValue.isEmpty
        if (!datePropertyValueDoesNotExist(assetId, propertyId) && isBlank) {
          deleteDateProperty(assetId, propertyId).execute
        } else if (datePropertyValueDoesNotExist(assetId, propertyId) && !isBlank) {
          insertDateProperty(assetId, propertyId, DateParser.DatePropertyFormat.parseDateTime(propertyValues.head.asInstanceOf[PropertyValue].propertyValue)).execute
        } else if (!datePropertyValueDoesNotExist(assetId, propertyId) && !isBlank){
          updateDateProperty(assetId, propertyId, DateParser.DatePropertyFormat.parseDateTime(propertyValues.head.asInstanceOf[PropertyValue].propertyValue)).execute
        }
      case Number =>
        if (propertyValues.size > 1) throw new IllegalArgumentException("Number property must have exactly one value: " + propertyValues)
        if (propertyValues.isEmpty || propertyValues.head.asInstanceOf[PropertyValue].propertyValue.isEmpty) {
          deleteNumberProperty(assetId, propertyId).execute
        } else if (numberPropertyValueDoesNotExist(assetId, propertyId)) {
          insertNumberProperty(assetId, propertyId, propertyValues.head.asInstanceOf[PropertyValue].propertyValue.toDouble).execute
        } else {
          updateNumberProperty(assetId, propertyId, propertyValues.head.asInstanceOf[PropertyValue].propertyValue.toDouble).execute
        }
      case t: String => throw new UnsupportedOperationException("Asset property type: " + t + " not supported")
    }
  }

  def expireAssetsByMunicipality(municipalities: Set[Int]) : Unit = {
    if (municipalities.nonEmpty) {
      sqlu"""
        update asset set valid_to = current_timestamp - INTERVAL'1 SECOND'
        where asset_type_id = ${TrafficSigns.typeId}
        and created_by != 'batch_process_trafficSigns'
        and municipality_code in (#${municipalities.mkString(",")})""".execute
    }
  }

  def expire(linkIds: Set[String], username: String): Unit = {
    MassQuery.withStringIds(linkIds) { idTableName =>
      sqlu"""
         update asset set valid_to = current_timestamp - INTERVAL'1 SECOND' where id in (
          select a.id
          from asset a
          join asset_link al on al.asset_id = a.id
          join lrm_position pos on pos.id = al.position_id
          join  #$idTableName i on i.id = pos.link_id
          where a.asset_type_id = ${TrafficSigns.typeId}
          AND (a.valid_to IS NULL OR a.valid_to > current_timestamp )
          AND a.created_by = $username
         )
      """.execute
    }
  }

  def expireWithoutTransaction(queryFilter: String => String, username: Option[String]) : Unit = {
    val modifiedBy = username match {case Some(user) => s", modified_date = current_timestamp , modified_by = '$user'" case _ => "" }
    val query = s"""
          update asset
          set valid_to = current_timestamp $modifiedBy
          where asset_type_id = ${TrafficSigns.typeId}
          """
    StaticQuery.updateNA(queryFilter(query) + " and (valid_to IS NULL OR valid_to > current_timestamp)").execute
  }

  def expireAssetByLinkId(linkIds: Seq[String], signsType: Set[Int] = Set(), username: Option[String]) : Unit = {
    val trafficSignType = if(signsType.isEmpty) "" else s"and ev.value in (${signsType.mkString(",")})"
    val filterByUsername = if(username.isEmpty) "" else s"and created_by = $username"
    MassQuery.withStringIds(linkIds.toSet) { idTableName =>
      sqlu"""
         update asset set valid_to = current_timestamp - INTERVAL'1 SECOND'
         where id in (
          select a.id
          from asset a
          join asset_link al on al.asset_id = a.id
          join lrm_position pos on pos.id = al.position_id
          join  #$idTableName i on i.id = pos.link_id
          join property p on a.asset_type_id = p.asset_type_id
          join single_choice_value scv on scv.asset_id = a.id and scv.property_id = p.id and p.property_type = 'single_choice' and public_id = 'trafficSigns_type'
          join enumerated_value ev on scv.enumerated_value_id = ev.id
          where a.asset_type_id = #${TrafficSigns.typeId}
          and (a.valid_to is null or a.valid_to > current_timestamp )
          and a.floating = '0'
          #$trafficSignType
          #$filterByUsername
         )
      """.execute
    }
  }

  def getLastExecutionDate(createdBy: String, signsType: Set[Int]): Option[DateTime] = {
    sql""" select MAX( case when a.modified_date is null then MAX(a.created_date) else MAX(a.modified_date) end ) as lastExecution
           from asset a
           join property p on a.asset_type_id = p.asset_type_id
           join single_choice_value scv on scv.asset_id = a.id and scv.property_id = p.id and p.property_type = 'single_choice' and public_id = 'trafficSigns_type'
           join enumerated_value ev on scv.enumerated_value_id = ev.id
           where a.created_by = $createdBy and ( a.modified_by = $createdBy or a.modified_by is null) and a.asset_type_id = #${TrafficSigns.typeId}
           and ev.value in (#${signsType.mkString(",")})
           group by a.modified_date, a.created_date""".as[DateTime].firstOption

  }
}