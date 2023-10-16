package fi.liikennevirasto.digiroad2.dao.pointasset

import fi.liikennevirasto.digiroad2.dao.Queries._
import fi.liikennevirasto.digiroad2.{GeometryUtils, PersistedPointAsset, Point}
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.asset.PropertyTypes._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.dao.Sequences
import fi.liikennevirasto.digiroad2.service.pointasset.IncomingTrafficLight
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedResult, StaticQuery}
import com.github.tototoshi.slick.MySQLJodaSupport._
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase

import scala.util.Try

case class TrafficLightRow(id: Long, linkId: String,
                           lon: Double, lat: Double,
                           mValue: Double, floating: Boolean,
                           timeStamp: Long,
                           municipalityCode: Int,
                           property: PropertyRow,
                           createdBy: Option[String] = None,
                           createdAt: Option[DateTime] = None,
                           modifiedBy: Option[String] = None,
                           modifiedAt: Option[DateTime] = None,
                           linkSource: LinkGeomSource,
                           externalId: Option[String] = None)

case class TrafficLight(id: Long, linkId: String,
                        lon: Double, lat: Double,
                        mValue: Double, floating: Boolean,
                        timeStamp: Long,
                        municipalityCode: Int,
                        propertyData: Seq[Property],
                        createdBy: Option[String] = None,
                        createdAt: Option[DateTime] = None,
                        modifiedBy: Option[String] = None,
                        modifiedAt: Option[DateTime] = None,
                        linkSource: LinkGeomSource,
                        externalId: Option[String] = None) extends PersistedPointAsset {
  override def getValidityDirection: Option[Int] = Try(getProperty("sidecode").map(_.propertyValue).getOrElse("").toInt).toOption
  override def getBearing: Option[Int] = Try(getProperty("bearing").map(_.propertyValue).getOrElse("").toDouble.toInt).toOption
}

object PostGISTrafficLightDao {
  def fetchByFilter(queryFilter: String => String): Seq[TrafficLight] = {

    val query =
      """
        select a.id, pos.link_id, a.geometry, pos.start_measure, a.floating, pos.adjusted_timestamp, a.municipality_code, p.id, p.public_id, p.property_type, p.required, ev.value,
        case
          when ev.name_fi is not null then ev.name_fi
          when tpv.value_fi is not null then tpv.value_fi
          when npv.value is not null then cast(npv.value as text)
          else null
        end as display_value,
        case
        	when mcv.grouped_id > 0 then mcv.grouped_id
        	when scv.grouped_id > 0 then scv.grouped_id
        	when npv.grouped_id > 0 then npv.grouped_id
        	when tpv.grouped_id > 0 then tpv.grouped_id
        	else 0
        end as grouped_id,
          a.created_by, a.created_date, a.modified_by, a.modified_date, pos.link_source, a.external_id
        from asset a
        join asset_link al on a.id = al.asset_id
        join lrm_position pos on al.position_id = pos.id
        join property p on a.asset_type_id = p.asset_type_id
        left join multiple_choice_value mcv ON mcv.asset_id = a.id and mcv.property_id = p.id AND p.PROPERTY_TYPE = 'checkbox'
        left join single_choice_value scv on scv.asset_id = a.id and scv.property_id = p.id and p.property_type = 'single_choice'
        left join text_property_value tpv on tpv.asset_id = a.id and tpv.property_id = p.id and p.property_type = 'text'
        left join number_property_value npv on npv.asset_id = a.id and npv.property_id = p.id and p.property_type = 'number'
        left join enumerated_value ev on scv.enumerated_value_id = ev.id or mcv.enumerated_value_id = ev.id
      """
    val queryWithFilter = queryFilter(query) + " and (a.valid_to > current_timestamp or a.valid_to is null)"
    queryToTrafficLight(queryWithFilter)
  }

  def assetRowToProperty(assetRows: Iterable[TrafficLightRow]): Seq[Property] = {
    assetRows.groupBy(row =>  (row.property.propertyId, row.property.propertyGroupedId)).map { case ((key, groupedId), rows) =>
      val row = rows.head
      Property(
        id = key,
        publicId = row.property.publicId,
        propertyType = row.property.propertyType,
        required = row.property.propertyRequired,
        groupedId = groupedId,
        values = rows.flatMap { assetRow =>

          val finalValue = PropertyValidator.propertyValueValidation(assetRow.property.publicId, assetRow.property.propertyValue )

          Seq(PropertyValue(finalValue, Option(assetRow.property.propertyDisplayValue)))

        }.toSeq)
    }.toSeq
  }

  private def queryToTrafficLight(query: String): Seq[TrafficLight] = {
    val rows = StaticQuery.queryNA[TrafficLightRow](query)(getPointAsset).iterator.toSeq

    rows.groupBy(_.id).map { case (id, signRows) =>
      val row = signRows.head
      val properties: Seq[Property] = assetRowToProperty(signRows)

      id -> TrafficLight(id = row.id, linkId = row.linkId, lon = row.lon, lat = row.lat, mValue = row.mValue,
        floating = row.floating, timeStamp = row.timeStamp, municipalityCode = row.municipalityCode, properties,
        createdBy = row.createdBy, createdAt = row.createdAt, modifiedBy = row.modifiedBy, modifiedAt = row.modifiedAt, linkSource = row.linkSource, externalId = row.externalId)
    }.values.toSeq
  }

  private def createOrUpdateTrafficLight(trafficLight: IncomingTrafficLight, id: Long, fromCreate: Boolean = true): Unit = {
    val existingGroupedProperties: Set[Long] = existingGroupedIdForAsset(id)
    val updatedGroupedIds = trafficLight.propertyData
      .groupBy(_.groupedId)
      .map { case (key, properties) =>
        val latestGroupId = if (fromCreate) Sequences.nextGroupedIdSeqValue else key
        properties
          .map(propertyWithTypeAndId(TrafficLights.typeId))
          .foreach { propertyWithTypeAndId =>
            val propertyType = propertyWithTypeAndId._1
            val propertyPublicId = propertyWithTypeAndId._3.publicId
            val propertyId = propertyWithTypeAndId._2.get
            val propertyValues = propertyWithTypeAndId._3.values

            createOrUpdateProperties(id, propertyPublicId, propertyId, propertyType, propertyValues, Some(latestGroupId))
          }
        latestGroupId
      }.toSet
    removeExtraGroupedPropertiesFromUpdate(id, existingGroupedProperties.diff(updatedGroupedIds))
  }

  implicit val getPointAsset = new GetResult[TrafficLightRow] {
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
      val propertyValue = r.nextDoubleOption()
      val propertyDisplayValue = r.nextStringOption()
      val propertyGroupedId = r.nextLong
      val property = PropertyRow(
        propertyId = propertyId,
        publicId = propertyPublicId,
        propertyType = propertyType,
        propertyRequired = propertyRequired,
        propertyValue = propertyValue match {
          case Some(double) =>
            val value: AnyVal = if (double % 1 == 0) double.toLong else double
            value.toString
          case None =>
            propertyType match {
              case Number => propertyDisplayValue.getOrElse("").replace(",", ".")
              case _ => propertyDisplayValue.getOrElse("")
            }
        },
        propertyDisplayValue = propertyDisplayValue.orNull,
        propertyGroupedId = propertyGroupedId)
      val createdBy = r.nextStringOption()
      val createdDateTime = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val modifiedBy = r.nextStringOption()
      val modifiedDateTime = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val linkSource = r.nextInt()
      val externalId = r.nextStringOption()

      TrafficLightRow(id, linkId, point.x, point.y, mValue, floating, timeStamp, municipalityCode, property, createdBy,
        createdDateTime, modifiedBy, modifiedDateTime, linkSource = LinkGeomSource(linkSource), externalId = externalId)
    }
  }

  def create(trafficLight: IncomingTrafficLight, mValue: Double, username: String, municipality: Long, adjustedTimestamp: Long, linkSource: LinkGeomSource): Long = {
    val id = Sequences.nextPrimaryKeySeqValue
    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
    sqlu"""
        insert into asset(id, asset_type_id, created_by, created_date, municipality_code)
        values ($id, 280, $username, current_timestamp, ${municipality});

        insert into lrm_position(id, start_measure, link_id, adjusted_timestamp, link_source)
        values ($lrmPositionId, ${mValue}, ${trafficLight.linkId}, $adjustedTimestamp, ${linkSource.value});

        insert into asset_link(asset_id, position_id)
        values ($id, $lrmPositionId);
    """.execute
    updateAssetGeometry(id, Point(trafficLight.lon, trafficLight.lat))

    createOrUpdateTrafficLight(trafficLight, id)

    id
  }

  def create(trafficLight: IncomingTrafficLight, mValue: Double, username: String, municipality: Long, adjustedTimestamp: Long,
             linkSource: LinkGeomSource, createdByFromUpdate: Option[String] = Some(""),
             createdDateTimeFromUpdate: Option[DateTime], externalIdFromUpdate: Option[String],
             fromPointAssetUpdater: Boolean = false, modifiedByFromUpdate: Option[String] = None, modifiedDateTimeFromUpdate: Option[DateTime] = None): Long = {
    val id = Sequences.nextPrimaryKeySeqValue
    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue

    val modifiedBy = if (fromPointAssetUpdater) modifiedByFromUpdate.getOrElse(null) else username
    val modifiedAt = if (fromPointAssetUpdater) modifiedDateTimeFromUpdate.getOrElse(null) else DateTime.now()

    sqlu"""
        insert into asset(id, external_id, asset_type_id, created_by, created_date, municipality_code, modified_by, modified_date)
        values ($id, $externalIdFromUpdate, 280, $createdByFromUpdate, $createdDateTimeFromUpdate, $municipality, $modifiedBy, $modifiedAt);

        insert into lrm_position(id, start_measure, link_id, adjusted_timestamp, link_source, modified_date)
        values ($lrmPositionId, $mValue, ${trafficLight.linkId}, $adjustedTimestamp, ${linkSource.value}, current_timestamp);

        insert into asset_link(asset_id, position_id)
        values ($id, $lrmPositionId);
    """.execute
    updateAssetGeometry(id, Point(trafficLight.lon, trafficLight.lat))

    createOrUpdateTrafficLight(trafficLight, id)

    id
  }

  def update(id: Long, trafficLight: IncomingTrafficLight, mValue: Double, username: String, municipality: Int, adjustedTimeStampOption: Option[Long] = None, linkSource: LinkGeomSource, fromPointAssetUpdater: Boolean = false) = {
    sqlu""" update asset set municipality_code = ${municipality} where id = $id """.execute
    updateAssetGeometry(id, Point(trafficLight.lon, trafficLight.lat))
    if (!fromPointAssetUpdater) updateAssetModified(id, username).execute

    adjustedTimeStampOption match {
      case Some(adjustedTimeStamp) =>
        sqlu"""
          update lrm_position
           set
           start_measure = ${mValue},
           link_id = ${trafficLight.linkId},
           adjusted_timestamp = ${adjustedTimeStamp},
           link_source = ${linkSource.value}
           where id = (select position_id from asset_link where asset_id = $id)
        """.execute
      case _  =>
        sqlu"""
          update lrm_position
           set
           start_measure = ${mValue},
           link_id = ${trafficLight.linkId},
           link_source = ${linkSource.value}
           where id = (select position_id from asset_link where asset_id = $id)
        """.execute
    }

    createOrUpdateTrafficLight(trafficLight, id, false)

    id
  }

  def propertyWithTypeAndId(typeId: Int)(property: SimplePointAssetProperty): Tuple3[String, Option[Long], SimplePointAssetProperty] = {
    val propertyId = StaticQuery.query[(String, Int), Long](propertyIdByPublicIdAndTypeId).apply(property.publicId, typeId).firstOption.getOrElse(throw new IllegalArgumentException("Property: " + property.publicId + " not found"))
    (StaticQuery.query[Long, String](propertyTypeByPropertyId).apply(propertyId).first, Some(propertyId), property)
  }

  def singleChoiceValueDoesNotExist(assetId: Long, propertyId: Long, groupedId: Option[Long] = None) = {
    StaticQuery.query[(Long, Long, Option[Long]), Long](existsSingleChoicePropertyWithGroupedId).apply((assetId, propertyId, groupedId)).firstOption.isEmpty
  }

  def textPropertyValueDoesNotExist(assetId: Long, propertyId: Long, groupedId: Option[Long] = None) = {
    StaticQuery.query[(Long, Long, Option[Long]), Long](existsTextPropertyWithGroupedId).apply((assetId, propertyId, groupedId)).firstOption.isEmpty
  }

  def multipleChoiceValueDoesNotExist(assetId: Long, propertyId: Long, groupedId: Option[Long] = None): Boolean = {
    StaticQuery.query[(Long, Long, Option[Long]), Long](existsMultipleChoicePropertyWithGroupedId).apply((assetId, propertyId, groupedId)).firstOption.isEmpty
  }

  private def numberPropertyValueDoesNotExist(assetId: Long, propertyId: Long, groupedId: Option[Long] = None) = {
    StaticQuery.query[(Long, Long, Option[Long]), Long](existsNumberPropertyWithGroupedId).apply((assetId, propertyId, groupedId)).firstOption.isEmpty
  }

  private def existingGroupedIdForAsset(assetId: Long): Set[Long] = {
    StaticQuery.query[(Long, String), Long](existingGroupedIdForAssetQuery).apply((assetId, "trafficLight_type")).iterator.toSet
  }

  def createOrUpdateProperties(assetId: Long, propertyPublicId: String, propertyId: Long, propertyType: String, propertyValues: Seq[PointAssetValue], groupedId: Option[Long]) {
    val extractedPropertyValue = if (propertyValues.nonEmpty) propertyValues.head.asInstanceOf[PropertyValue].propertyValue else ""
    propertyType match {
      case CheckBox =>
        if (propertyValues.size > 1) throw new IllegalArgumentException("Multiple choice only allows values between 0 and 1.")
        if(multipleChoiceValueDoesNotExist(assetId, propertyId, groupedId)) {
          insertMultipleChoiceValue(assetId, propertyId, extractedPropertyValue.toLong, groupedId).execute
        } else {
          updateMultipleChoiceValue(assetId, propertyId, extractedPropertyValue.toLong, groupedId).execute
        }
      case Text =>
        if (propertyValues.size > 1) throw new IllegalArgumentException("Text property must have exactly one value: " + propertyValues)
        if (textPropertyValueDoesNotExist(assetId, propertyId, groupedId)) {
          insertTextProperty(assetId, propertyId, extractedPropertyValue, groupedId).execute
        } else {
          updateTextProperty(assetId, propertyId, extractedPropertyValue, groupedId).execute
        }
      case SingleChoice =>
        if (propertyValues.size != 1) {
          throw new IllegalArgumentException("Single choice property must have exactly one value. publicId: " + propertyPublicId)
        }
        else if (extractedPropertyValue.trim.nonEmpty) {
          /* This need to do cast to Double because of the property trafficLight_type */
          if (singleChoiceValueDoesNotExist(assetId, propertyId, groupedId)) {
            insertSingleChoiceProperty(assetId, propertyId, extractedPropertyValue.toDouble, groupedId).execute
          } else {
            updateSingleChoiceProperty(assetId, propertyId, extractedPropertyValue.toDouble, groupedId).execute
          }
        }
      case Number =>
        if (propertyValues.size > 1) throw new IllegalArgumentException("Number property must have exactly one value: " + propertyValues)
        if (numberPropertyValueDoesNotExist(assetId, propertyId, groupedId)) {
          insertNumberProperty(assetId, propertyId, Try(extractedPropertyValue.toDouble).toOption, groupedId).execute
        } else {
          updateNumberProperty(assetId, propertyId, Try(extractedPropertyValue.toDouble).toOption, groupedId).execute
        }

      case t: String => throw new UnsupportedOperationException("Asset property type: " + t + " not supported")
    }
  }

  def fetchByRadius(position : Point, meters: Int): Seq[TrafficLight] = {
    val topLeft = Point(position.x - meters, position.y - meters)
    val bottomRight = Point(position.x + meters, position.y + meters)
    val boundingBoxFilter = PostGISDatabase.boundingBoxFilter(BoundingRectangle(topLeft, bottomRight), "a.geometry")
    val filter = s"Where a.asset_type_id = 280 and $boundingBoxFilter"
    fetchByFilter(query => query + filter).
      filter(r => GeometryUtils.geometryLength(Seq(position, Point(r.lon, r.lat))) <= meters)
  }

  private def removeExtraGroupedPropertiesFromUpdate(assetId: Long, expiredGroupedProperties: Set[Long]): Unit = {
    // Set(0) added in order to remove old properties when updating from old traffic light to new
    val oldPropertiesToBeDeleted: Set[Long] = Set(0)
    (expiredGroupedProperties ++ oldPropertiesToBeDeleted).foreach { groupedKey =>
      deleteAdditionalGroupedAsset(assetId, groupedKey)
    }
  }
}