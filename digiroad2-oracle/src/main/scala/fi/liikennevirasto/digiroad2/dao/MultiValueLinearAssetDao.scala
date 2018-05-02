package fi.liikennevirasto.digiroad2.dao

import fi.liikennevirasto.digiroad2.asset.PropertyTypes._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.dao.Queries._
import fi.liikennevirasto.digiroad2.linearasset.{MultiAssetValue, MultiValue, PersistedLinearAsset}
import fi.liikennevirasto.digiroad2.oracle.MassQuery
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import org.slf4j.LoggerFactory
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedResult, StaticQuery => Q}


case class MultiValueAssetRow(id: Long, linkId: Long, sideCode: Int, value: MultiValuePropertyRow,
                              startMeasure: Double, endMeasure: Double, createdBy: Option[String], createdDate: Option[DateTime],
                              modifiedBy: Option[String], modifiedDate: Option[DateTime], expired: Boolean, typeId: Int,
                              vvhTimeStamp: Long, geomModifiedDate: Option[DateTime], linkSource: Int, verifiedBy: Option[String], verifiedDate: Option[DateTime])

class MultiValueLinearAssetDao {
  val logger = LoggerFactory.getLogger(getClass)

  def fetchMultiValueLinearAssetsByLinkIds(assetTypeId: Int, linkIds: Seq[Long], includeExpired: Boolean = false): Seq[PersistedLinearAsset] = {
    val filterExpired = if (includeExpired) "" else " and (a.valid_to > sysdate or a.valid_to is null)"
    val assets = MassQuery.withIds(linkIds.toSet) { idTableName =>
      sql"""
        select a.id, pos.link_id, pos.side_code, pos.start_measure, pos.end_measure, p.public_id, p.property_type, p.required,
         case
               when tp.value_fi is not null then tp.value_fi
               when np.value is not null then to_char(np.value)
               when e.value is not null then to_char(e.value)
               when dtp.date_time is not null then to_char(dtp.date_time, 'DD.MM.YYYY')
               else null
         end as value,
               a.created_by, a.created_date, a.modified_by, a.modified_date,
               case when a.valid_to <= sysdate then 1 else 0 end as expired, a.asset_type_id,
               pos.adjusted_timestamp, pos.modified_date, pos.link_source, a.verified_by, a.verified_date
          from asset a
          join asset_link al on a.id = al.asset_id
          join lrm_position pos on al.position_id = pos.id
          join property p on p.asset_type_id = a.asset_type_id
          join #$idTableName i on i.id = pos.link_id
          left join single_choice_value s on s.asset_id = a.id and s.property_id = p.id and p.property_type = 'single_choice'
          left join text_property_value tp on tp.asset_id = a.id and tp.property_id = p.id and (p.property_type = 'text' or p.property_type = 'long_text' or p.property_type = 'read_only_text')
          left join multiple_choice_value mc on mc.asset_id = a.id and mc.property_id = p.id and (p.property_type = 'multiple_choice' or p.property_type = 'checkbox')
          left join number_property_value np on np.asset_id = a.id and np.property_id = p.id and (p.property_type = 'number' or p.property_type = 'read_only_number' or p.property_type = 'integer')
          left join date_property_value dtp on dtp.asset_id = a.id and dtp.property_id = p.id and (p.property_type = 'date')
          left join enumerated_value e on mc.enumerated_value_id = e.id or s.enumerated_value_id = e.id
          where a.asset_type_id = $assetTypeId
          and a.floating = 0
          #$filterExpired""".as[MultiValueAssetRow].list
    }
    assets.groupBy(_.id).map { case (id, assetRows) =>
      val row = assetRows.head
      val value: MultiAssetValue = MultiAssetValue(assetRowToProperty(assetRows))

      id -> PersistedLinearAsset(id = row.id, linkId = row.linkId, sideCode = row.sideCode, value = Some(MultiValue(value)), startMeasure = row.startMeasure, endMeasure = row.endMeasure, createdBy = row.createdBy,
        createdDateTime = row.createdDate, modifiedBy = row.modifiedBy, modifiedDateTime = row.modifiedDate, expired = row.expired, typeId = row.typeId,  vvhTimeStamp = row.vvhTimeStamp,
        geomModifiedDate = row.geomModifiedDate, linkSource = LinkGeomSource.apply(row.linkSource), verifiedBy = row.verifiedBy, verifiedDate = row.verifiedDate)
    }.values.toSeq
  }

  def fetchMultiValueLinearAssetsByIds(ids: Set[Long]): Seq[PersistedLinearAsset] = {
    val assets = MassQuery.withIds(ids) { idTableName =>
      sql"""
        select a.id, pos.link_id, pos.side_code, pos.start_measure, pos.end_measure, p.public_id, p.property_type, p.required,
         case
               when tp.value_fi is not null then tp.value_fi
               when np.value is not null then to_char(np.value)
               when e.value is not null then to_char(e.value)
               when dtp.date_time is not null then to_char(dtp.date_time, 'DD.MM.YYYY')
               else null
         end as value,
               a.created_by, a.created_date, a.modified_by, a.modified_date,
               case when a.valid_to <= sysdate then 1 else 0 end as expired, a.asset_type_id,
               pos.adjusted_timestamp, pos.modified_date, pos.link_source, a.verified_by, a.verified_date
          from asset a
          join asset_link al on a.id = al.asset_id
          join lrm_position pos on al.position_id = pos.id
          join property p on p.asset_type_id = a.asset_type_id
          join #$idTableName i on i.id = a.id
                      left join single_choice_value s on s.asset_id = a.id and s.property_id = p.id and p.property_type = 'single_choice'
                      left join text_property_value tp on tp.asset_id = a.id and tp.property_id = p.id and (p.property_type = 'text' or p.property_type = 'long_text' or p.property_type = 'read_only_text')
                      left join multiple_choice_value mc on mc.asset_id = a.id and mc.property_id = p.id and (p.property_type = 'multiple_choice' or p.property_type = 'checkbox')
                      left join number_property_value np on np.asset_id = a.id and np.property_id = p.id and (p.property_type = 'number' or p.property_type = 'read_only_number' or p.property_type = 'integer')
                      left join date_property_value dtp on dtp.asset_id = a.id and dtp.property_id = p.id and (p.property_type = 'date')
                      left join enumerated_value e on mc.enumerated_value_id = e.id or s.enumerated_value_id = e.id
          where a.floating = 0 """.as[MultiValueAssetRow].list
    }
    assets.groupBy(_.id).map { case (id, assetRows) =>
      val row = assetRows.head
      val value: MultiAssetValue = MultiAssetValue(assetRowToProperty(assetRows))

      id -> PersistedLinearAsset(id = row.id, linkId = row.linkId, sideCode = row.sideCode, value = Some(MultiValue(value)), startMeasure = row.startMeasure, endMeasure = row.endMeasure, createdBy = row.createdBy,
        createdDateTime = row.createdDate, modifiedBy = row.modifiedBy, modifiedDateTime = row.modifiedDate, expired = row.expired, typeId = row.typeId, vvhTimeStamp = row.vvhTimeStamp,
        geomModifiedDate = row.geomModifiedDate, linkSource = LinkGeomSource.apply(row.linkSource), verifiedBy = row.verifiedBy, verifiedDate = row.verifiedDate)
    }.values.toSeq
  }

  //TODO never used, check if is suppose to use it
//  private def queryToPersistedLinearAssets(query: String): Seq[PersistedLinearAsset] = {
//    val rows = Q.queryNA[MultiValueAssetRow](query).iterator.toSeq
//
//    rows.groupBy(_.id).map { case (id, assetRows) =>
//      val row = assetRows.head
//      val value: MultiAssetValue = MultiAssetValue(assetRowToProperty(assetRows))
//
//      id -> PersistedLinearAsset(id = row.id, linkId = row.linkId, sideCode = row.sideCode, value = Some(MultiValue(value)), startMeasure = row.startMeasure, endMeasure = row.endMeasure, createdBy = row.createdBy,
//        createdDateTime = row.createdDate, modifiedBy = row.modifiedBy, modifiedDateTime = row.modifiedDate, expired = row.expired, typeId = row.typeId,  vvhTimeStamp = row.vvhTimeStamp,
//        geomModifiedDate = row.geomModifiedDate, linkSource = LinkGeomSource.apply(row.linkSource), verifiedBy = row.verifiedBy, verifiedDate = row.verifiedDate)
//    }.values.toSeq
//  }

  def assetRowToProperty(assetRows: Iterable[MultiValueAssetRow]): Seq[MultiTypeProperty] = {
    assetRows.groupBy(_.value.publicId).map { case (key, rows) =>
      val row = rows.head
      MultiTypeProperty(
        publicId = row.value.publicId,
        propertyType = row.value.propertyType,
        required = row.value.required,
        values = rows.flatMap(assetRow =>
          assetRow.value.propertyValue match {
            case Some(value) => Some(MultiTypePropertyValue(value))
            case _ => None
          }
        ).toSeq
      )
    }.toSeq
  }

  implicit val getMultiValueAssetRow = new GetResult[MultiValueAssetRow] {
    def apply(r: PositionedResult) = {

      val id = r.nextLong()
      val linkId = r.nextLong()
      val sideCode = r.nextInt()
      val startMeasure = r.nextDouble()
      val endMeasure = r.nextDouble()

      val propertyPublicId = r.nextString
      val propertyType = r.nextString
      val propertyRequired = r.nextBoolean()
      val propertyValue = r.nextObjectOption()
      val value = MultiValuePropertyRow(
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


      val vvhTimeStamp = r.nextLong()
      val geomModifiedDate = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val linkSource = r.nextInt()
      val verifiedBy = r.nextStringOption()
      val verifiedDate = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))

      MultiValueAssetRow(id, linkId, sideCode, value, startMeasure, endMeasure, createdBy, createdDate, modifiedBy, modifiedDate, expired, typeId, vvhTimeStamp, geomModifiedDate, linkSource, verifiedBy, verifiedDate)
    }
  }


  def propertyDefaultValues(assetTypeId: Long): List[MultiTypeProperty] = {
    implicit val getDefaultValue = new GetResult[MultiTypeProperty] {
      def apply(r: PositionedResult) = {
        MultiTypeProperty(publicId = r.nextString, propertyType = r.nextString(), required = r.nextBoolean(), values = List(MultiTypePropertyValue(r.nextString)))
      }
    }
    sql"""
      select p.public_id, p.property_type, p.default_value from asset_type a
      join property p on p.asset_type_id = a.id
      where a.id = $assetTypeId and p.default_value is not null""".as[MultiTypeProperty].list
  }

  private def validPropertyUpdates(propertyWithType: Tuple3[String, Option[Long], MultiTypeProperty]): Boolean = {
    propertyWithType match {
      case (SingleChoice, _, property) => property.values.nonEmpty
      case _ => true
    }
  }

  private def propertyWithTypeAndId(property: MultiTypeProperty): Tuple3[String, Option[Long], MultiTypeProperty] = {
    if (AssetPropertyConfiguration.commonAssetProperties.get(property.publicId).isDefined) {
      (AssetPropertyConfiguration.commonAssetProperties(property.publicId).propertyType, None, property)
    }
    else {
      val propertyId = Q.query[String, Long](propertyIdByPublicId).apply(property.publicId).firstOption.getOrElse(throw new IllegalArgumentException("Property: " + property.publicId + " not found"))
      (Q.query[Long, String](propertyTypeByPropertyId).apply(propertyId).first, Some(propertyId), property)
    }
  }

  def updateAssetProperties(assetId: Long, properties: Seq[MultiTypeProperty]) {
    properties.map(propertyWithTypeAndId).filter(validPropertyUpdates).foreach { propertyWithTypeAndId =>
      if (AssetPropertyConfiguration.commonAssetProperties.get(propertyWithTypeAndId._3.publicId).isDefined) {
        updateCommonAssetProperty(assetId, propertyWithTypeAndId._3.publicId, propertyWithTypeAndId._1, propertyWithTypeAndId._3.values)
      } else {
        updateAssetSpecificProperty(assetId, propertyWithTypeAndId._3.publicId, propertyWithTypeAndId._2.get, propertyWithTypeAndId._1, propertyWithTypeAndId._3.values)
      }
    }
  }

  private def updateAssetSpecificProperty(assetId: Long, propertyPublicId: String, propertyId: Long, propertyType: String, propertyValues: Seq[MultiTypePropertyValue]) {
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
        val formatter = ISODateTimeFormat.dateOptionalTimeParser()

        if (propertyValues.size > 1) throw new IllegalArgumentException("Date property must have exactly one value: " + propertyValues)
        if (propertyValues.isEmpty) {
          deleteDateProperty(assetId, propertyId).execute
        } else if (datePropertyValueDoesNotExist(assetId, propertyId)) {
          insertDateProperty(assetId, propertyId, formatter.parseDateTime(propertyValues.head.value.toString)).execute
        } else {
          updateDateProperty(assetId, propertyId, formatter.parseDateTime(propertyValues.head.value.toString)).execute
        }

      case ReadOnly | ReadOnlyNumber | ReadOnlyText =>
        logger.debug("Ignoring read only property in update: " + propertyPublicId)

      case t: String => throw new UnsupportedOperationException("Asset property type: " + t + " not supported")
    }
  }

  private def numberPropertyValueDoesNotExist(assetId: Long, propertyId: Long) = {
    Q.query[(Long, Long), Long](existsNumberProperty).apply((assetId, propertyId)).firstOption.isEmpty
  }

  private def datePropertyValueDoesNotExist(assetId: Long, propertyId: Long) = {
    Q.query[(Long, Long), Long](existsDateProperty).apply((assetId, propertyId)).firstOption.isEmpty
  }

  private def textPropertyValueDoesNotExist(assetId: Long, propertyId: Long) = {
    Q.query[(Long, Long), Long](existsTextProperty).apply((assetId, propertyId)).firstOption.isEmpty
  }

  private def singleChoiceValueDoesNotExist(assetId: Long, propertyId: Long) = {
    Q.query[(Long, Long), Long](existsSingleChoiceProperty).apply((assetId, propertyId)).firstOption.isEmpty
  }

  private def updateCommonAssetProperty(assetId: Long, propertyPublicId: String, propertyType: String, propertyValues: Seq[MultiTypePropertyValue]) {
    val property = AssetPropertyConfiguration.commonAssetProperties(propertyPublicId)
    propertyType match {
      case SingleChoice =>
        val newVal = propertyValues.head.value.toString
        AssetPropertyConfiguration.commonAssetPropertyEnumeratedValues.find { p =>
          (p.publicId == propertyPublicId) && p.values.map(_.propertyValue).contains(newVal)
        } match {
          case Some(propValues) =>
            updateCommonProperty(assetId, property.column, newVal, property.lrmPositionProperty).execute

          case None => throw new IllegalArgumentException("Invalid property/value: " + propertyPublicId + "/" + newVal)
        }

      case Text | LongText => updateCommonProperty(assetId, property.column, propertyValues.head.value.toString).execute
      case Date =>
        val formatter = ISODateTimeFormat.dateOptionalTimeParser()
        val optionalDateTime = propertyValues.headOption match {
          case None => None
          case Some(x) if x.value.toString.trim.isEmpty => None
          case Some(x) => Some(formatter.parseDateTime(x.value.toString))
        }
        updateCommonDateProperty(assetId, property.column, optionalDateTime, property.lrmPositionProperty).execute

      case ReadOnlyText | ReadOnlyNumber =>
        logger.debug("Ignoring read only property in update: " + propertyPublicId)

      case t: String => throw new UnsupportedOperationException("Asset: " + propertyPublicId + " property type: " + t + " not supported")
    }
  }

  private[this] def createOrUpdateMultipleChoiceProperty(propertyValues: Seq[MultiTypePropertyValue], assetId: Long, propertyId: Long) {
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
      sql"""select public_id, property_type from property where asset_type_id = $typeId and required = 1""".as[(String, String)].iterator.toMap

    requiredProperties
  }

}

