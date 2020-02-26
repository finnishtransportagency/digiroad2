package fi.liikennevirasto.digiroad2.dao.pointasset

import fi.liikennevirasto.digiroad2.dao.Queries._
import fi.liikennevirasto.digiroad2.{PersistedPointAsset, Point}
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.dao.{Queries, Sequences}
import fi.liikennevirasto.digiroad2.service.pointasset.IncomingDirectionalTrafficSign
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedResult, StaticQuery}
import com.github.tototoshi.slick.MySQLJodaSupport._
import fi.liikennevirasto.digiroad2.asset.PropertyTypes._

case class DirectionalTrafficSignRow(id: Long, linkId: Long,
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
                                    geometry: Seq[Point] = Nil,
                                    linkSource: LinkGeomSource)

case class DirectionalTrafficSign(id: Long, linkId: Long,
                                  lon: Double, lat: Double,
                                  mValue: Double, floating: Boolean,
                                  vvhTimeStamp: Long,
                                  municipalityCode: Int,
                                  propertyData: Seq[Property],
                                  validityDirection: Int,
                                  bearing: Option[Int],
                                  createdBy: Option[String] = None,
                                  createdAt: Option[DateTime] = None,
                                  modifiedBy: Option[String] = None,
                                  modifiedAt: Option[DateTime] = None,
                                  geometry: Seq[Point] = Nil,
                                  linkSource: LinkGeomSource) extends PersistedPointAsset

object OracleDirectionalTrafficSignDao {
  def fetchByFilter(queryFilter: String => String): Seq[DirectionalTrafficSign] = {
    val query =
      s"""
         select a.id, lrm.link_id, a.geometry, lrm.start_measure, a.floating, lrm.adjusted_timestamp, a.municipality_code, p.id, p.public_id, p.property_type, p.required, ev.value,
          case
            when ev.name_fi is not null then ev.name_fi
            when tpv.value_fi is not null then tpv.value_fi
            else null
          end as display_value, lrm.side_code, a.created_by, a.created_date, a.modified_by, a.modified_date, a.bearing, lrm.link_source
         from asset a
         join asset_link al on a.id = al.asset_id
         join lrm_position lrm on al.position_id = lrm.id
         join property p on a.asset_type_id = p.asset_type_id
         left join text_property_value tpv on (tpv.property_id = p.id AND tpv.asset_id = a.id)
         left join multiple_choice_value mcv ON mcv.asset_id = a.id and mcv.property_id = p.id AND p.PROPERTY_TYPE = 'checkbox'
         left join enumerated_value ev on  mcv.ENUMERATED_VALUE_ID = ev.ID
      """
    val queryWithFilter = queryFilter(query) + " and (a.valid_to > sysdate or a.valid_to is null) "
    queryToDirectionalTrafficSign(queryWithFilter)
  }

  def assetRowToProperty(assetRows: Iterable[DirectionalTrafficSignRow]): Seq[Property] = {
    assetRows.groupBy(_.property.propertyId).map { case (key, rows) =>
      val row = rows.head
      Property(
        id = key,
        publicId = row.property.publicId,
        propertyType = row.property.propertyType,
        required = row.property.propertyRequired,
        values = rows.flatMap { assetRow =>
          Seq(PropertyValue(assetRow.property.propertyValue, Option(assetRow.property.propertyDisplayValue)))
        }.toSeq)
    }.toSeq
  }

  private def queryToDirectionalTrafficSign(query: String): Seq[DirectionalTrafficSign] = {
    val rows = StaticQuery.queryNA[DirectionalTrafficSignRow](query)(getPointAsset).iterator.toSeq

    rows.groupBy(_.id).map { case (id, signRows) =>
      val row = signRows.head
      val properties: Seq[Property] = assetRowToProperty(signRows)

      id -> DirectionalTrafficSign(id = row.id, linkId = row.linkId, lon = row.lon, lat = row.lat, mValue = row.mValue,
        floating = row.floating, vvhTimeStamp = row.vvhTimeStamp, municipalityCode = row.municipalityCode, properties,
        validityDirection = row.validityDirection, bearing = row.bearing, createdBy = row.createdBy, createdAt = row.createdAt, modifiedBy = row.modifiedBy, modifiedAt = row.modifiedAt,
        geometry = row.geometry, linkSource = row.linkSource)
    }.values.toSeq
  }

  private def createOrUpdateDirectionalTrafficSign(sign: IncomingDirectionalTrafficSign, id: Long): Unit ={
    sign.propertyData.map(propertyWithTypeAndId(DirectionalTrafficSigns.typeId)).foreach { propertyWithTypeAndId =>
      val propertyType = propertyWithTypeAndId._1
      val propertyPublicId = propertyWithTypeAndId._3.publicId
      val propertyId = propertyWithTypeAndId._2.get
      val propertyValues = propertyWithTypeAndId._3.values

      createOrUpdateProperties(id, propertyPublicId, propertyId, propertyType, propertyValues)
    }
  }

  implicit val getPointAsset = new GetResult[DirectionalTrafficSignRow] {
    def apply(r: PositionedResult) = {
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
      val validityDirection = r.nextInt()
      val createdBy = r.nextStringOption()
      val createdDateTime = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val modifiedBy = r.nextStringOption()
      val modifiedDateTime = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val bearing = r.nextIntOption()
      val linkSource = r.nextInt()

      DirectionalTrafficSignRow(id, linkId, point.x, point.y, mValue, floating, vvhTimeStamp, municipalityCode, property, validityDirection, bearing, createdBy, createdDateTime, modifiedBy, modifiedDateTime, linkSource = LinkGeomSource(linkSource))
    }
  }

  def create(sign: IncomingDirectionalTrafficSign, mValue: Double,  municipality: Int, username: String, floating: Boolean): Long = {
    val id = Sequences.nextPrimaryKeySeqValue

    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
    sqlu"""
      insert all
        into asset(id, asset_type_id, created_by, created_date, municipality_code, bearing, floating)
        values ($id, 240, $username, sysdate, $municipality, ${sign.bearing}, $floating)
        into lrm_position(id, start_measure, end_measure, link_id, side_code)
        values ($lrmPositionId, $mValue, $mValue, ${sign.linkId}, ${sign.validityDirection})
        into asset_link(asset_id, position_id)
        values ($id, $lrmPositionId)
      select * from dual
    """.execute
    updateAssetGeometry(id, Point(sign.lon, sign.lat))

    createOrUpdateDirectionalTrafficSign(sign, id)

    id
  }

  def create(sign: IncomingDirectionalTrafficSign, mValue: Double,  municipality: Int, username: String, createdByFromUpdate: Option[String] = Some(""), createdDateTimeFromUpdate: Option[DateTime]): Long = {
    val id = Sequences.nextPrimaryKeySeqValue

    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
    sqlu"""
      insert all
        into asset(id, asset_type_id, created_by, created_date, municipality_code, bearing, modified_by, modified_date)
        values ($id, 240, $createdByFromUpdate, $createdDateTimeFromUpdate, $municipality, ${sign.bearing}, $username, sysdate)
        into lrm_position(id, start_measure, end_measure, link_id, side_code, modified_date)
        values ($lrmPositionId, $mValue, $mValue, ${sign.linkId}, ${sign.validityDirection}, sysdate)
        into asset_link(asset_id, position_id)
        values ($id, $lrmPositionId)
      select * from dual
    """.execute
    updateAssetGeometry(id, Point(sign.lon, sign.lat))

    createOrUpdateDirectionalTrafficSign(sign, id)

    id
  }

  def update(id: Long, sign: IncomingDirectionalTrafficSign, mValue: Double, municipality: Int, username: String) = {
    sqlu""" update asset set municipality_code = $municipality, bearing=${sign.bearing} where id = $id """.execute
    updateAssetModified(id, username).execute
    updateAssetGeometry(id, Point(sign.lon, sign.lat))
    deleteTextProperty(id, getTextPropertyId).execute

    sqlu"""
      update lrm_position
       set
       start_measure = $mValue,
       link_id = ${sign.linkId},
       side_code = ${sign.validityDirection}
       where id = (select position_id from asset_link where asset_id = $id)
    """.execute

    createOrUpdateDirectionalTrafficSign(sign, id)

    id
  }

  def getUnverifiedAssets(assetTypeId: Int): List[(Long, Int)] = {
    sql"""select a.id, a.municipality_code from asset a
            where a.verified_by is null
            and a.valid_to is null
            and a.modified_date is null
            and a.asset_type_id = $assetTypeId
            and a.verified_date is null
           	or exists (select m.verified_date from municipality_verification m where m.asset_type_id = $assetTypeId
           	and m.municipality_id = a.municipality_code and valid_to is null and a.verified_date > m.verified_date)""".as[(Long, Int)].list
  }

  def updateVerifiedInfo(assetId: Long, user: String): Long = {
    sqlu"""update asset set verified_by = $user, verified_date = sysdate where id = $assetId""".execute
    assetId
  }

  private def getTextPropertyId: Long = {
    StaticQuery.query[String, Long](Queries.propertyIdByPublicId).apply("opastustaulun_teksti").first
  }

  def propertyWithTypeAndId(typeId: Int)(property: SimplePointAssetProperty): Tuple3[String, Option[Long], SimplePointAssetProperty] = {
    val propertyId = StaticQuery.query[(String, Int), Long](propertyIdByPublicIdAndTypeId).apply(property.publicId, typeId).firstOption.getOrElse(throw new IllegalArgumentException("Property: " + property.publicId + " not found"))
    (StaticQuery.query[Long, String](propertyTypeByPropertyId).apply(propertyId).first, Some(propertyId), property)
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
