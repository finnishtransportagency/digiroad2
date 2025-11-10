package fi.liikennevirasto.digiroad2.dao

import java.nio.charset.StandardCharsets
import java.util.Base64
import fi.liikennevirasto.digiroad2.asset.PropertyTypes._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.dao.Queries._
import fi.liikennevirasto.digiroad2.linearasset.{DynamicAssetValue, DynamicValue, NumericValue, PersistedLinearAsset}
import fi.liikennevirasto.digiroad2.postgis.MassQuery
import org.joda.time.DateTime
import org.joda.time.format.{DateTimeFormat, ISODateTimeFormat}
import org.slf4j.{Logger, LoggerFactory}
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedResult, StaticQuery => Q}
import com.github.tototoshi.slick.MySQLJodaSupport._
import fi.liikennevirasto.digiroad2.asset.DateParser.DatePropertyFormat
import fi.liikennevirasto.digiroad2.util.LogUtils


case class DynamicAssetRow(id: Long, linkId: String, sideCode: Int, value: DynamicPropertyRow,
                           startMeasure: Double, endMeasure: Double, createdBy: Option[String], createdDate: Option[DateTime],
                           modifiedBy: Option[String], modifiedDate: Option[DateTime], expired: Boolean, typeId: Int,
                           timeStamp: Long, geomModifiedDate: Option[DateTime], linkSource: Int, verifiedBy: Option[String], verifiedDate: Option[DateTime], informationSource: Option[Int], externalIds: Seq[String] = Seq())

class DynamicLinearAssetDao {
  val logger: Logger = LoggerFactory.getLogger(getClass)

  def fetchDynamicLinearAssetsByLinkIds(assetTypeId: Int, linkIds: Seq[String], includeExpired: Boolean = false, includeFloating: Boolean = false): Seq[PersistedLinearAsset] = {
    val filterFloating = if (includeFloating) "" else " and a.floating = '0'"
    val filterExpired = if (includeExpired) "" else " and (a.valid_to > current_timestamp or a.valid_to is null)"
    val filter = filterFloating + filterExpired
    val assets = LogUtils.time(logger, s"Fetch dynamic linear assets with MassQuery on ${linkIds.size} links, assetType: $assetTypeId") {
        sql"""
        select a.id, pos.link_id, pos.side_code, pos.start_measure, pos.end_measure, p.public_id, p.property_type, p.required,
         case
               when tp.value_fi is not null then tp.value_fi
               when np.value is not null then cast(np.value as text)
               when e.value is not null then cast(e.value as text)
               when dtp.date_time is not null then to_char(dtp.date_time, 'DD.MM.YYYY')
               else null
         end as value,
               a.created_by, a.created_date, a.modified_by, a.modified_date,
               case when a.valid_to <= current_timestamp then 1 else 0 end as expired, a.asset_type_id,
               pos.adjusted_timestamp, pos.modified_date, pos.link_source, a.verified_by, a.verified_date, a.information_source, a.external_ids
          from asset a
          join asset_link al on a.id = al.asset_id
          join lrm_position pos on al.position_id = pos.id
          join property p on p.asset_type_id = a.asset_type_id
           #${MassQuery.withStringIdsValuesJoin("pos.link_id", linkIds.toSet)}
          left join single_choice_value s on s.asset_id = a.id and s.property_id = p.id and p.property_type = 'single_choice'
          left join text_property_value tp on tp.asset_id = a.id and tp.property_id = p.id and (p.property_type = 'text' or p.property_type = 'long_text' or p.property_type = 'read_only_text')
          left join multiple_choice_value mc on mc.asset_id = a.id and mc.property_id = p.id and (p.property_type = 'multiple_choice' or p.property_type = 'checkbox')
          left join number_property_value np on np.asset_id = a.id and np.property_id = p.id and (p.property_type = 'number' or p.property_type = 'read_only_number' or p.property_type = 'integer')
          left join date_property_value dtp on dtp.asset_id = a.id and dtp.property_id = p.id and p.property_type = 'date'
          left join enumerated_value e on mc.enumerated_value_id = e.id or s.enumerated_value_id = e.id
          where a.asset_type_id = $assetTypeId
          #$filter""".as[DynamicAssetRow](getDynamicAssetRow).list
    }
    LogUtils.time(logger, s"Forming ${assets.size} asset rows to PersitedLinearAssets") {
      assets.groupBy(_.id).map { case (id, assetRows) =>
        val row = assetRows.head
        val value: DynamicAssetValue = DynamicAssetValue(assetRowToProperty(assetRows))

        id -> PersistedLinearAsset(id = row.id, linkId = row.linkId, sideCode = row.sideCode, value = Some(DynamicValue(value)), startMeasure = row.startMeasure, endMeasure = row.endMeasure, createdBy = row.createdBy,
          createdDateTime = row.createdDate, modifiedBy = row.modifiedBy, modifiedDateTime = row.modifiedDate, expired = row.expired, typeId = row.typeId, timeStamp = row.timeStamp,
          geomModifiedDate = row.geomModifiedDate, linkSource = LinkGeomSource.apply(row.linkSource), verifiedBy = row.verifiedBy, verifiedDate = row.verifiedDate, informationSource = row.informationSource.map(info => InformationSource.apply(info)), externalIds = row.externalIds)
      }.values.toSeq
    }
  }

  def fetchDynamicLinearAssetsByIds(ids: Set[Long]): Seq[PersistedLinearAsset] = {
    val assets = MassQuery.withIds(ids) { idTableName =>
      sql"""
        select a.id, pos.link_id, pos.side_code, pos.start_measure, pos.end_measure, p.public_id, p.property_type, p.required,
         case
               when tp.value_fi is not null then tp.value_fi
               when np.value is not null then cast(np.value as text)
               when e.value is not null then cast(e.value as text)
               when dtp.date_time is not null then to_char(dtp.date_time, 'DD.MM.YYYY')
               else null
         end as value,
               a.created_by, a.created_date, a.modified_by, a.modified_date,
               case when a.valid_to <= current_timestamp then 1 else 0 end as expired, a.asset_type_id,
               pos.adjusted_timestamp, pos.modified_date, pos.link_source, a.verified_by, a.verified_date, a.information_source, a.external_ids
          from asset a
          join asset_link al on a.id = al.asset_id
          join lrm_position pos on al.position_id = pos.id
          join property p on p.asset_type_id = a.asset_type_id
          join #$idTableName i on i.id = a.id
                      left join single_choice_value s on s.asset_id = a.id and s.property_id = p.id and p.property_type = 'single_choice'
                      left join text_property_value tp on tp.asset_id = a.id and tp.property_id = p.id and (p.property_type = 'text' or p.property_type = 'long_text' or p.property_type = 'read_only_text')
                      left join multiple_choice_value mc on mc.asset_id = a.id and mc.property_id = p.id and (p.property_type = 'multiple_choice' or p.property_type = 'checkbox')
                      left join number_property_value np on np.asset_id = a.id and np.property_id = p.id and (p.property_type = 'number' or p.property_type = 'read_only_number' or p.property_type = 'integer')
                      left join date_property_value dtp on dtp.asset_id = a.id and dtp.property_id = p.id and p.property_type = 'date'
                      left join enumerated_value e on mc.enumerated_value_id = e.id or s.enumerated_value_id = e.id
          where a.floating = '0' """.as[DynamicAssetRow](getDynamicAssetRow).list
    }
    assets.groupBy(_.id).map { case (id, assetRows) =>
      val row = assetRows.head
      val value: DynamicAssetValue = DynamicAssetValue(assetRowToProperty(assetRows))

      id -> PersistedLinearAsset(id = row.id, linkId = row.linkId, sideCode = row.sideCode, value = Some(DynamicValue(value)), startMeasure = row.startMeasure, endMeasure = row.endMeasure, createdBy = row.createdBy,
        createdDateTime = row.createdDate, modifiedBy = row.modifiedBy, modifiedDateTime = row.modifiedDate, expired = row.expired, typeId = row.typeId, timeStamp = row.timeStamp,
        geomModifiedDate = row.geomModifiedDate, linkSource = LinkGeomSource.apply(row.linkSource), verifiedBy = row.verifiedBy, verifiedDate = row.verifiedDate, row.informationSource.map(info => InformationSource.apply(info)), externalIds = row.externalIds)
    }.values.toSeq
  }

  def assetRowToProperty(assetRows: Iterable[DynamicAssetRow]): Seq[DynamicProperty] = {
    assetRows.toSeq.sortBy(_.value.publicId).map { row =>
      DynamicProperty(
        publicId = row.value.publicId,
        propertyType = row.value.propertyType,
        required = row.value.required,
        values = row.value.propertyValue match {
          case Some(value) => Seq(DynamicPropertyValue(value))
          case _ => Seq.empty
        }
      )
    }
  }

  implicit val getDynamicAssetRow: GetResult[DynamicAssetRow] = new GetResult[DynamicAssetRow] {
    def apply(r: PositionedResult) : DynamicAssetRow = {
      val id = r.nextLong()
      val linkId = r.nextString()
      val sideCode = r.nextIntOption()
      val startMeasure = r.nextDouble()
      val endMeasure = r.nextDouble()
      val propertyPublicId = r.nextString
      val propertyType = r.nextString
      val propertyRequired = r.nextBoolean()
      val propertyValue = r.nextObjectOption()
      val value = DynamicPropertyRow(
        publicId = propertyPublicId,
        propertyType = propertyType,
        required = propertyRequired,
        propertyValue = propertyValue)
      val createdBy = r.nextStringOption()
      val createdDate = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val modifiedBy = r.nextStringOption()
      val modifiedDate = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val expired = r.nextBoolean
      val typeId = r.nextInt()
      val timeStamp = r.nextLong()
      val geomModifiedDate = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val linkSource = r.nextInt()
      val verifiedBy = r.nextStringOption()
      val verifiedDate = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val informationSource = r.nextIntOption()
      val externalIds: Seq[String] = Seq(r.nextStringOption()).flatMap {
        case Some(value) => value.split(",")
        case None => Seq.empty
      }

      DynamicAssetRow(id, linkId, sideCode.getOrElse(99), value, startMeasure, endMeasure, createdBy, createdDate, modifiedBy, modifiedDate, expired, typeId, timeStamp, geomModifiedDate, linkSource, verifiedBy, verifiedDate, informationSource, externalIds)
    }
  }

  def propertyDefaultValues(assetTypeId: Long): List[DynamicProperty] = {
    implicit val getDefaultValue: GetResult[DynamicProperty] = new GetResult[DynamicProperty] {
      def apply(r: PositionedResult) : DynamicProperty = {
        DynamicProperty(publicId = r.nextString, propertyType = r.nextString(), required = r.nextBoolean(), values = List(DynamicPropertyValue(r.nextString)))
      }
    }
    sql"""
      select p.public_id, p.property_type, p.default_value from asset_type a
      join property p on p.asset_type_id = a.id
      where a.id = $assetTypeId and p.default_value is not null""".as[DynamicProperty].list
  }

  private def validPropertyUpdates(propertyWithType: (String, Option[Long], DynamicProperty)): Boolean = {
    propertyWithType match {
      case (SingleChoice, _, property) => property.values.nonEmpty
      case _ => true
    }
  }

  private def propertyWithTypeAndId(typeId: Int, property: DynamicProperty): (String, Option[Long], DynamicProperty) = {
    if (AssetPropertyConfiguration.commonAssetProperties.get(property.publicId).isDefined) {
      (AssetPropertyConfiguration.commonAssetProperties(property.publicId).propertyType, None, property)
    }
    else {
      val propertyId = Q.query[(String, Int), Long](propertyIdByPublicIdAndTypeId).apply(property.publicId, typeId).firstOption.getOrElse(throw new IllegalArgumentException("Property: " + property.publicId + " not found"))
      (Q.query[Long, String](propertyTypeByPropertyId).apply(propertyId).first, Some(propertyId), property)
    }
  }

  def updateAssetProperties(assetId: Long, properties: Seq[DynamicProperty], typeId: Int) {
    properties.map(prop =>propertyWithTypeAndId(typeId, prop)).filter(validPropertyUpdates).foreach { propertyWithTypeAndId =>
      if (AssetPropertyConfiguration.commonAssetProperties.get(propertyWithTypeAndId._3.publicId).isDefined) {
        updateCommonAssetProperty(assetId, propertyWithTypeAndId._3.publicId, propertyWithTypeAndId._1, propertyWithTypeAndId._3.values)
      } else {
        updateAssetSpecificProperty(assetId, propertyWithTypeAndId._3.publicId, propertyWithTypeAndId._2.get, propertyWithTypeAndId._1, propertyWithTypeAndId._3.values)
      }
    }
  }

  private def updateAssetSpecificProperty(assetId: Long, propertyPublicId: String, propertyId: Long, propertyType: String, propertyValues: Seq[DynamicPropertyValue]) {
    propertyType match {
      case Text | LongText =>
        if (propertyValues.size > 1) throw new IllegalArgumentException("Text property must have exactly one value: " + propertyValues)
        if (propertyValues.isEmpty) {
          deleteTextProperty(assetId, propertyId).execute
        } else if (textPropertyValueDoesNotExist(assetId, propertyId)) {
          insertTextProperty(assetId, propertyId, propertyValues.head.value.toString).execute
        } else {
          updateTextProperty(assetId, propertyId, propertyValues.head.value.toString).execute
        }

      case SingleChoice =>
        if (propertyValues.size != 1) throw new IllegalArgumentException("Single choice property must have exactly one value. publicId: " + propertyPublicId)
        if (singleChoiceValueDoesNotExist(assetId, propertyId)) {
          insertSingleChoiceProperty(assetId, propertyId, Integer.valueOf(propertyValues.head.value.toString).toLong).execute
        } else {
          updateSingleChoiceProperty(assetId, propertyId, Integer.valueOf(propertyValues.head.value.toString).toLong).execute
        }

      case MultipleChoice | CheckBox =>
        createOrUpdateMultipleChoiceProperty(propertyValues, assetId, propertyId)

      case Number | IntegerProp =>
        if (propertyValues.size > 1) throw new IllegalArgumentException("Number property must have exactly one value: " + propertyValues)
        if (propertyValues.isEmpty) {
          deleteNumberProperty(assetId, propertyId).execute
        } else if (numberPropertyValueDoesNotExist(assetId, propertyId)) {
          insertNumberProperty(assetId, propertyId, propertyValues.head.value.toString.toDouble).execute
        } else {
          updateNumberProperty(assetId, propertyId, propertyValues.head.value.toString.toDouble).execute
        }

      case Date =>
        if (propertyValues.size > 1) throw new IllegalArgumentException("Date property must have exactly one value: " + propertyValues)
        if (propertyValues.isEmpty) {
          deleteDateProperty(assetId, propertyId).execute
        } else if (datePropertyValueDoesNotExist(assetId, propertyId)) {
          insertDateProperty(assetId, propertyId, DatePropertyFormat.parseDateTime(propertyValues.head.value.toString)).execute
        } else {
          updateDateProperty(assetId, propertyId, DatePropertyFormat.parseDateTime(propertyValues.head.value.toString)).execute
        }

      case TimePeriod =>
        if(validityPeriodPropertyValueExist(assetId: Long, propertyId: Long)) {
          deleteValidityPeriodProperty(assetId, propertyId).execute
        }

        if (propertyValues.nonEmpty) {
          propertyValues.distinct.foreach { propertyValue =>
                val validityPeriodValue = propertyValue.value.asInstanceOf[Map[String, Any]]
                insertValidityPeriodProperty(assetId, propertyId, ValidityPeriodValue.fromMap(validityPeriodValue)).execute
            }
          }

      case ReadOnly | ReadOnlyNumber | ReadOnlyText =>
        logger.debug("Ignoring read only property in update: " + propertyPublicId)

      case DatePeriodType =>
        if(datePeriodPropertyValueExists(assetId: Long, propertyId: Long)){
          deleteDatePeriodProperty(assetId, propertyId).execute
        }

        if (propertyValues.nonEmpty) {
          propertyValues.distinct.foreach { propertyValue =>
            val dates = propertyValue.value.asInstanceOf[Map[String, String]]
            val period = DatePeriodValue.fromMap(dates)
            insertDatePeriodProperty(assetId, propertyId, DatePropertyFormat.parseDateTime(period.startDate), DatePropertyFormat.parseDateTime(period.endDate)).execute
          }
        }
      case t: String => throw new UnsupportedOperationException("Asset property type: " + t + " not supported")
    }
  }

  private def numberPropertyValueDoesNotExist(assetId: Long, propertyId: Long) = {
    Q.query[(Long, Long), Long](existsNumberProperty).apply((assetId, propertyId)).firstOption.isEmpty
  }

  private def datePropertyValueDoesNotExist(assetId: Long, propertyId: Long) = {
    Q.query[(Long, Long), Long](existsDateProperty).apply((assetId, propertyId)).firstOption.isEmpty
  }

  private def validityPeriodPropertyValueExist(assetId: Long, propertyId: Long) = {
    Q.query[(Long, Long), Long](existsValidityPeriodProperty).apply((assetId, propertyId)).firstOption.nonEmpty
  }

  private def datePeriodPropertyValueExists(assetId: Long, propertyId: Long) = {
    Q.query[(Long, Long), Long](existsDatePeriodProperty).apply((assetId, propertyId)).firstOption.nonEmpty
  }

  private def textPropertyValueDoesNotExist(assetId: Long, propertyId: Long) = {
    Q.query[(Long, Long), Long](existsTextProperty).apply((assetId, propertyId)).firstOption.isEmpty
  }

  private def singleChoiceValueDoesNotExist(assetId: Long, propertyId: Long) = {
    Q.query[(Long, Long), Long](existsSingleChoiceProperty).apply((assetId, propertyId)).firstOption.isEmpty
  }

  private def updateCommonAssetProperty(assetId: Long, propertyPublicId: String, propertyType: String, propertyValues: Seq[DynamicPropertyValue]) {
    val property = AssetPropertyConfiguration.commonAssetProperties(propertyPublicId)
    propertyType match {
      case SingleChoice =>
        val newVal = propertyValues.head.value.toString
        AssetPropertyConfiguration.commonAssetPropertyEnumeratedValues.find { p =>
          (p.publicId == propertyPublicId) && p.values.map(_.asInstanceOf[PropertyValue].propertyValue).contains(newVal)
        } match {
          case Some(propValues) =>
            updateCommonProperty(assetId, property.column, newVal, property.lrmPositionProperty).execute

          case None => throw new IllegalArgumentException("Invalid property/value: " + propertyPublicId + "/" + newVal)
        }

      case Text | LongText => updateCommonProperty(assetId, property.column, propertyValues.head.value.toString).execute

      case Date =>
        val formatter = ISODateTimeFormat.dateOptionalTimeParser()
        updateCommonDateProperty(assetId, property.column, Some(formatter.parseDateTime(propertyValues.head.value.toString)))

      case ReadOnlyText | ReadOnlyNumber =>
        logger.debug("Ignoring read only property in update: " + propertyPublicId)

      case t: String => throw new UnsupportedOperationException("Asset: " + propertyPublicId + " property type: " + t + " not supported")
    }
  }

  private[this] def createOrUpdateMultipleChoiceProperty(propertyValues: Seq[DynamicPropertyValue], assetId: Long, propertyId: Long) {
    val newValues = propertyValues.map(p => Integer.valueOf(p.value.toString).toLong)
    val currentIdsAndValues = Q.query[(Long, Long), (Long, Long)](multipleChoicePropertyValuesByAssetIdAndPropertyId).apply(assetId, propertyId).list
    val currentValues = currentIdsAndValues.map(_._2)
    // remove values as necessary
    currentIdsAndValues.foreach {
      case (multipleChoiceId, enumValue) =>
        if (!newValues.contains(enumValue)) {
          deleteMultipleChoiceValue(multipleChoiceId).execute
        }
    }
    // add values as necessary
    newValues.filter {
      !currentValues.contains(_)
    }.foreach {
      v =>
        insertMultipleChoiceValue(assetId, propertyId, v).execute
    }
  }

  def getAssetRequiredProperties(typeId: Int): Map[String, String] ={
    val requiredProperties =
      sql"""select public_id, property_type from property where asset_type_id = $typeId and required = '1'""".as[(String, String)].iterator.toMap

    requiredProperties
  }


  def getValidityPeriodPropertyValue(ids: Set[Long], typeId: Int) : Map[Long, Seq[DynamicProperty]] = {
    val assets = MassQuery.withIds (ids) {
      idTableName =>
        sql"""
          select vpp.asset_id, p.public_id, p.property_type, p.required, vpp.period_week_day, vpp.start_hour, vpp.end_hour, vpp.start_minute, vpp.end_minute, vpp.type
          from validity_period_property_value vpp
          join property p on p.asset_type_id = $typeId and p.property_type = 'time_period'
          join #$idTableName i on i.id = vpp.asset_id
          where vpp.property_id = p.id
        """.as[ValidityPeriodRow](getValidityPeriodRow).list
    }
    assets.groupBy(_.assetId).mapValues{ assetGroup =>
      assetGroup.groupBy(_.publicId).map { case (_, values) =>
        val row = values.head
        DynamicProperty(row.publicId, row.propertyType, row.required, values.map(_.value))
      }.toSeq
    }
  }

  def getDatePeriodPropertyValue(ids: Set[Long], typeId: Int) : Map[Long, Seq[DynamicProperty]] = {
    val assets = MassQuery.withIds (ids) {
      idTableName =>
        sql"""
          select dp.asset_id, p.public_id, p.property_type, p.required,
          case when dp.start_date is not null then to_char(dp.start_date, 'DD.MM.YYYY') else null end as START_DATE,
          case when dp.end_date is not null then to_char(dp.end_date, 'DD.MM.YYYY') else null end as END_DATE
          from date_period_value dp
          join property p on p.asset_type_id = $typeId and p.property_type = 'date_period'
          join #$idTableName i on i.id = dp.asset_id
          where dp.property_id = p.id
        """.as[DatePeriodRow](getDatePeriodRow).list
    }
    assets.groupBy(_.assetId).mapValues{ assetGroup =>
      assetGroup.groupBy(_.publicId).map { case (_, values) =>
        val row = values.head
        DynamicProperty(row.publicId, row.propertyType, row.required, values.map(_.value))
      }.toSeq
    }
  }

  case class ValidityPeriodRow(assetId: Long, publicId: String, propertyType: String, required: Boolean, value: DynamicPropertyValue )
  case class DatePeriodRow(assetId: Long, publicId: String, propertyType: String, required: Boolean, value: DynamicPropertyValue)

  implicit val getValidityPeriodRow: GetResult[ValidityPeriodRow] = new GetResult[ValidityPeriodRow] {
    def apply(r: PositionedResult): ValidityPeriodRow = {
      val assetId = r.nextLong
      val publicId = r.nextString
      val propertyType = r.nextString
      val required = r.nextBoolean
      val value =
        Map("days" -> r.nextInt,
          "startHour" -> r.nextInt,
          "endHour" -> r.nextInt,
          "startMinute" -> r.nextInt,
          "endMinute" -> r.nextInt,
          "periodType" -> r.nextIntOption
      )

      ValidityPeriodRow(assetId, publicId, propertyType, required, DynamicPropertyValue(value))
    }
  }

  implicit val getDatePeriodRow: GetResult[DatePeriodRow] = new GetResult[DatePeriodRow] {
    def apply(r: PositionedResult) : DatePeriodRow = {
      val assetId = r.nextLong
      val publicId = r.nextString
      val propertyType = r.nextString
      val required = r.nextBoolean
      val optStartDate = r.nextStringOption()
      val optEndDate = r.nextStringOption()
      val value = (optStartDate, optEndDate) match {
        case (Some(startDate), Some(endDate)) => DatePeriodValue.toMap(DatePeriodValue(startDate, endDate))
        case _ => None
      }
      DatePeriodRow(assetId, publicId, propertyType, required, DynamicPropertyValue(value))
    }
  }

  def updateAssetLastModified(assetId: Long, modifier: String): Option[Long] = {
    val assetsUpdated = Queries.updateAssetModified(assetId, modifier).first
    if (assetsUpdated == 1) {
      Some(assetId)
    } else {
      None
    }
  }

  def getDynamicLinearAssetsChangedSince(assetTypeId: Int, sinceDate: DateTime, untilDate: DateTime, withAdjust: Boolean, token: Option[String] = None) : List[PersistedLinearAsset] = {
    val withAutoAdjustFilter = if (withAdjust) "" else "and (a.modified_by is null OR a.modified_by != 'generated_in_update')"
    val recordLimit = token match {
      case Some(tk) =>
        val (startNum, endNum) = Decode.getPageAndRecordNumber(tk)

        s"WHERE line_number between $startNum and $endNum"

      case _ => ""
    }

    val assets = sql"""
        select asset_id, link_id, side_code, value, start_measure, end_measure, public_id, property_type, required,
               created_by, created_date, modified_by, modified_date, expired, asset_type_id, adjusted_timestamp,
               pos_modified_date, link_source, verified_by, verified_date, information_source
        from (
          select a.id as asset_id, pos.link_id, pos.side_code,
          case
            when tp.value_fi is not null then tp.value_fi
            when np.value is not null then cast(np.value as text)
            when e.value is not null then cast(e.value as text)
            when dtp.date_time is not null then to_char(dtp.date_time, 'DD.MM.YYYY')
            else null
          end as value,
          pos.start_measure, pos.end_measure, p.public_id, p.property_type, p.required,
          a.created_by, a.created_date, a.modified_by, a.modified_date,
          case
            when a.valid_to <= current_timestamp then 1 else 0 end as expired, a.asset_type_id, pos.adjusted_timestamp,
            pos.modified_date as pos_modified_date, pos.link_source, a.verified_by, a.verified_date, a.information_source,
            DENSE_RANK() over (ORDER BY a.id) line_number
          from asset a
          join asset_link al on a.id = al.asset_id
          join lrm_position pos on al.position_id = pos.id
          join property p on p.asset_type_id = a.asset_type_id
          left join single_choice_value s on s.asset_id = a.id and s.property_id = p.id and p.property_type = 'single_choice'
          left join text_property_value tp on tp.asset_id = a.id and tp.property_id = p.id and (p.property_type = 'text' or p.property_type = 'long_text' or p.property_type = 'read_only_text')
          left join multiple_choice_value mc on mc.asset_id = a.id and mc.property_id = p.id and (p.property_type = 'multiple_choice' or p.property_type = 'checkbox')
          left join number_property_value np on np.asset_id = a.id and np.property_id = p.id and (p.property_type = 'number' or p.property_type = 'read_only_number' or p.property_type = 'integer')
          left join date_property_value dtp on dtp.asset_id = a.id and dtp.property_id = p.id and p.property_type = 'date'
          left join enumerated_value e on mc.enumerated_value_id = e.id or s.enumerated_value_id = e.id
          where a.asset_type_id = $assetTypeId
          and (
            (a.valid_to > $sinceDate and a.valid_to <= $untilDate)
            or
            (a.modified_date > $sinceDate and a.modified_date <= $untilDate)
            or
            (a.created_date > $sinceDate and a.created_date <= $untilDate)
          )
          and a.floating = '0'
          #$withAutoAdjustFilter
        ) derivedAsset #$recordLimit"""
      .as[(Long, String, Int, Option[String], Double, Double, String, String, Boolean, Option[String], Option[DateTime], Option[String], Option[DateTime], Boolean, Int, Long, Option[DateTime], Int, Option[String], Option[DateTime], Option[Int])].list

      val groupedAssets = assets.groupBy(_._1)

      groupedAssets.flatMap { case (_, assetRows) =>
        val value = assetRows.map { case (_, _, _, optValue, _, _, publicId, propertyType, required, _, _, _, _, _, _, _, _, _, _, _, _) =>
          DynamicProperty(publicId, propertyType, required, Seq(DynamicPropertyValue(optValue)))
        }
        assetRows.map{
          case (id, linkId, sideCode, _, startMeasure, endMeasure, _, _, _, createdBy, createdDate, modifiedBy, modifiedDate, expired, typeId, timeStamp, geomModifiedDate, linkSource, verifiedBy, verifiedDate, informationSource) =>
            PersistedLinearAsset(id, linkId, sideCode, Some(DynamicValue(DynamicAssetValue(value))), startMeasure, endMeasure, createdBy, createdDate, modifiedBy, modifiedDate, expired, typeId, timeStamp, geomModifiedDate, LinkGeomSource.apply(linkSource), verifiedBy, verifiedDate, informationSource.map(info => InformationSource.apply(info)))
      }.toSet
    }.toList
  }
}

