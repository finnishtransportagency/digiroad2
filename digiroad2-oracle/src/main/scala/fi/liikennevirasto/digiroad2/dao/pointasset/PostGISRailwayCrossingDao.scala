package fi.liikennevirasto.digiroad2.dao.pointasset

import fi.liikennevirasto.digiroad2.dao.Queries._
import fi.liikennevirasto.digiroad2.{PersistedPointAsset, Point}
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.asset.PropertyTypes._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.dao.{Queries, Sequences}
import fi.liikennevirasto.digiroad2.service.pointasset.IncomingRailwayCrossing
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedResult, StaticQuery}
import com.github.tototoshi.slick.MySQLJodaSupport._

case class RailwayCrossingRow(id: Long, linkId: String,
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

case class RailwayCrossing(id: Long, linkId: String,
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
                           externalId: Option[String] = None) extends PersistedPointAsset

object PostGISRailwayCrossingDao {
  // This works as long as there are only two properties of different types for railway crossings
  def fetchByFilter(queryFilter: String => String): Seq[RailwayCrossing] = {
    val query =
      s"""
         select a.id, pos.link_id, a.geometry, pos.start_measure, a.floating, pos.adjusted_timestamp, a.municipality_code, p.id, p.public_id, p.property_type, p.required, ev.value,
          case
            when ev.name_fi is not null then ev.name_fi
            when tpv.value_fi is not null then tpv.value_fi
            else null
          end as display_value, a.created_by, a.created_date, a.modified_by, a.modified_date, pos.link_source, a.external_id
         from asset a
         join asset_link al on a.id = al.asset_id
         join lrm_position pos on al.position_id = pos.id
         join property p on p.asset_type_id = a.asset_type_id
         left join single_choice_value scv on scv.asset_id = a.id
         left join multiple_choice_value mcv ON mcv.asset_id = a.id and mcv.property_id = p.id AND p.PROPERTY_TYPE = 'checkbox'
         left join enumerated_value ev on (ev.property_id = p.id AND (scv.enumerated_value_id = ev.id or mcv.enumerated_value_id = ev.id))
         left join text_property_value tpv on (tpv.property_id = p.id AND tpv.asset_id = a.id)
      """
    val queryWithFilter = queryFilter(query) + " and (a.valid_to > current_timestamp or a.valid_to is null) "
    queryToRailwayCrossing(queryWithFilter)
  }

  def assetRowToProperty(assetRows: Iterable[RailwayCrossingRow]): Seq[Property] = {
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

  private def queryToRailwayCrossing(query: String): Seq[RailwayCrossing] = {
    val rows = StaticQuery.queryNA[RailwayCrossingRow](query)(getPointAsset).iterator.toSeq

    rows.groupBy(_.id).map { case (id, signRows) =>
      val row = signRows.head
      val properties: Seq[Property] = assetRowToProperty(signRows)

      id -> RailwayCrossing(id = row.id, linkId = row.linkId, lon = row.lon, lat = row.lat, mValue = row.mValue,
        floating = row.floating, timeStamp = row.timeStamp, municipalityCode = row.municipalityCode, properties, createdBy = row.createdBy, createdAt = row.createdAt,
        modifiedBy = row.modifiedBy, modifiedAt = row.modifiedAt, linkSource = row.linkSource, externalId = row.externalId)
    }.values.toSeq
  }

  private def createOrUpdateRailwayCrossing(crossing: IncomingRailwayCrossing, id: Long): Unit ={
    crossing.propertyData.map(propertyWithTypeAndId(RailwayCrossings.typeId)).foreach { propertyWithTypeAndId =>
      val propertyType = propertyWithTypeAndId._1
      val propertyPublicId = propertyWithTypeAndId._3.publicId
      val propertyId = propertyWithTypeAndId._2.get
      val propertyValues = propertyWithTypeAndId._3.values

      createOrUpdateProperties(id, propertyPublicId, propertyId, propertyType, propertyValues)
    }
  }

  implicit val getPointAsset = new GetResult[RailwayCrossingRow] {
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
      val createdDateTime = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val modifiedBy = r.nextStringOption()
      val modifiedDateTime = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val linkSource = r.nextInt()
      val externalId = r.nextStringOption()

      RailwayCrossingRow(id, linkId, point.x, point.y, mValue, floating, timeStamp, municipalityCode, property, createdBy, createdDateTime, modifiedBy, modifiedDateTime, linkSource = LinkGeomSource(linkSource), externalId = externalId)
    }
  }

  def create(asset: IncomingRailwayCrossing, mValue: Double, municipality: Int, username: String, adjustedTimestamp: Long, linkSource: LinkGeomSource): Long = {
    val id = Sequences.nextPrimaryKeySeqValue
    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
    sqlu"""
        insert into asset(id, asset_type_id, created_by, created_date, municipality_code)
        values ($id, 230, $username, current_timestamp, $municipality);

        insert into lrm_position(id, start_measure, link_id, adjusted_timestamp, link_source)
        values ($lrmPositionId, $mValue, ${asset.linkId}, $adjustedTimestamp, ${linkSource.value});

        insert into asset_link(asset_id, position_id)
        values ($id, $lrmPositionId);
    """.execute
    updateAssetGeometry(id, Point(asset.lon, asset.lat))

    createOrUpdateRailwayCrossing(asset, id)

    id
  }

  def create(asset: IncomingRailwayCrossing, mValue: Double, municipality: Int, username: String, adjustedTimestamp: Long,
             linkSource: LinkGeomSource, createdByFromUpdate: Option[String] = Some(""),
             createdDateTimeFromUpdate: Option[DateTime], externalIdFromUpdate: Option[String],
             fromPointAssetUpdater: Boolean = false, modifiedByFromUpdate: Option[String] = None, modifiedDateTimeFromUpdate: Option[DateTime] = None): Long = {
    val id = Sequences.nextPrimaryKeySeqValue
    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue

    val modifiedBy = if (fromPointAssetUpdater) modifiedByFromUpdate.getOrElse(null) else username
    val modifiedAt = if (fromPointAssetUpdater) modifiedDateTimeFromUpdate.getOrElse(null) else DateTime.now()
    sqlu"""
        insert into asset(id, external_id, asset_type_id, created_by, created_date, municipality_code, modified_by, modified_date)
        values ($id, $externalIdFromUpdate, 230, $createdByFromUpdate, $createdDateTimeFromUpdate, $municipality, $modifiedBy, $modifiedAt);

        insert into lrm_position(id, start_measure, link_id, adjusted_timestamp, link_source, modified_date)
        values ($lrmPositionId, $mValue, ${asset.linkId}, $adjustedTimestamp, ${linkSource.value}, current_timestamp);

        insert into asset_link(asset_id, position_id)
        values ($id, $lrmPositionId);
    """.execute
    updateAssetGeometry(id, Point(asset.lon, asset.lat))

    createOrUpdateRailwayCrossing(asset, id)

    id
  }

  def update(id: Long, railwayCrossing: IncomingRailwayCrossing, mValue: Double, municipality: Int, username: String, adjustedTimeStampOption: Option[Long] = None, linkSource: LinkGeomSource, fromPointAssetUpdater: Boolean = false) = {
    sqlu""" update asset set municipality_code = $municipality where id = $id """.execute
    if (!fromPointAssetUpdater) updateAssetModified(id, username).execute
    updateAssetGeometry(id, Point(railwayCrossing.lon, railwayCrossing.lat))
    deleteTextProperty(id, getNamePropertyId).execute
    deleteTextProperty(id, getCodePropertyId).execute

    adjustedTimeStampOption match {
      case Some(adjustedTimeStamp) =>
        sqlu"""
          update lrm_position
           set
           start_measure = $mValue,
           link_id = ${railwayCrossing.linkId},
           adjusted_timestamp = ${adjustedTimeStamp},
           modified_date = current_timestamp,
           link_source = ${linkSource.value}
           where id = (select position_id from asset_link where asset_id = $id)
        """.execute
      case _ =>
        sqlu"""
          update lrm_position
           set
           start_measure = $mValue,
           link_id = ${railwayCrossing.linkId},
           modified_date = current_timestamp,
           link_source = ${linkSource.value}
           where id = (select position_id from asset_link where asset_id = $id)
        """.execute
    }

    createOrUpdateRailwayCrossing(railwayCrossing, id)

    id
  }

  private def getSafetyEquipmentPropertyId: Long = {
    StaticQuery.query[String, Long](Queries.propertyIdByPublicId).apply("turvavarustus").first
  }

  private def getNamePropertyId: Long = {
    StaticQuery.query[String, Long](Queries.propertyIdByPublicId).apply("rautatien_tasoristeyksen_nimi").first
  }

  private def getCodePropertyId: Long = {
    StaticQuery.query[String, Long](Queries.propertyIdByPublicId).apply("tasoristeystunnus").first
  }

  def getCodeMaxSize: Long  = {
    StaticQuery.query[String, Long](Queries.getPropertyMaxSize).apply("tasoristeystunnus").first
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
      case Text =>
        if (propertyValues.size > 1) throw new IllegalArgumentException("Text property must have exactly one value: " + propertyValues)
        if (propertyValues.isEmpty) {
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
