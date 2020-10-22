package fi.liikennevirasto.digiroad2.dao.pointasset

import fi.liikennevirasto.digiroad2.{PersistedPoint, Point}
import fi.liikennevirasto.digiroad2.asset.{LinkGeomSource, OtherRoadwayMarkings, Property, PropertyValue}
import fi.liikennevirasto.digiroad2.dao.Queries._
import fi.liikennevirasto.digiroad2.dao.Sequences
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.pointasset.IncomingOtherRoadwayMarking
import org.joda.time.DateTime
import slick.jdbc.StaticQuery.interpolation



case class OtherRoadwayMarkingRow(id: Long, linkId: Long,
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

case class OtherRoadwayMarking(id: Long, linkId: Long,
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

object OracleOtherRoadwayMarkingDao {

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

 /** def fetchByFilterWithExpired(queryFilter: String => String): Seq[OtherRoadwayMarking] = {
    val queryWithFilter = queryFilter(query())
    queryToOtherRoadwayMarking(queryWithFilter)
  }**/

  // This works as long as there is only one (and exactly one) property (currently type) for WidthOfRoadAxisMarking and up to one value
 /** def fetchByFilter(queryFilter: String => String, withDynSession: Boolean = false): Seq[OtherRoadwayMarking] = {
    val queryWithFilter = queryFilter(query()) + " and (a.valid_to > sysdate or a.valid_to is null)"
    if (withDynSession) {
      OracleDatabase.withDynSession {
        queryToOtherRoadwayMarking(queryWithFilter)
      }
    } else {
      queryToOtherRoadwayMarking(queryWithFilter)
    }
  }**/

  def assetRowToProperty(assetRows: Iterable[OtherRoadwayMarkingRow]): Seq[Property] = {
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

 /* private def queryToOtherRoadwayMarking(query: String): Seq[OtherRoadwayMarking] = {
    /*val rows = StaticQuery.queryNA[OtherRoadwayMarkingRow](query)(getPointAsset).iterator.toSeq

    rows.groupBy(_.id).map { case (id, signRows) =>
      val row = signRows.head
      val properties: Seq[Property] = assetRowToProperty(signRows)

      id -> OtherRoadwayMarking(id = row.id, linkId = row.linkId, lon = row.lon, lat = row.lat, mValue = row.mValue,
        floating = row.floating, vvhTimeStamp = row.vvhTimeStamp, municipalityCode = row.municipalityCode, properties,
        createdBy = row.createdBy, createdAt = row.createdAt, modifiedBy = row.modifiedBy, modifiedAt = row.modifiedAt,
        expired = row.expired, linkSource = row.linkSource)
    }.values.toSeq*/
  }*/

  private def createOrUpdateOtherRoadwayMarking(widthOfRoadAxisMarking: IncomingOtherRoadwayMarking, id: Long): Unit = {
    otherRoadwayMarking.propertyData.map(propertyWithTypeAndId(OtherRoadwayMarkings.typeId)).foreach { propertyWithTypeAndId =>
      val propertyType = propertyWithTypeAndId._1
      val propertyPublicId = propertyWithTypeAndId._3.publicId
      val propertyId = propertyWithTypeAndId._2.get
      val propertyValues = propertyWithTypeAndId._3.values

      createOrUpdateProperties(id, propertyPublicId, propertyId, propertyType, propertyValues)
    }
  }

  def create(widthOfRoadAxisMarking: IncomingOtherRoadwayMarking, mValue: Double, username: String, municipality: Int, adjustmentTimestamp: Long, linkSource: LinkGeomSource): Long = {
    val id = Sequences.nextPrimaryKeySeqValue
    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
    sqlu"""
      insert all
        into asset(id, asset_type_id, created_by, created_date, municipality_code)
        values ($id, ${OtherRoadwayMarkings.typeId}, $username, sysdate, $municipality)

        into lrm_position(id, start_measure, link_id, adjusted_timestamp, link_source)
        values ($lrmPositionId, $mValue, ${widthOfRoadAxisMarking.linkId}, $adjustmentTimestamp, ${linkSource.value})

        into asset_link(asset_id, position_id)
        values ($id, $lrmPositionId)

      select * from dual
    """.execute
    updateAssetGeometry(id, Point(widthOfRoadAxisMarking.lon, widthOfRoadAxisMarking.lat))

    createOrUpdateOtherRoadwayMarking(widthOfRoadAxisMarking, id)

    id
  }

  def create(widthOfRoadAxisMarking: IncomingOtherRoadwayMarking, mValue: Double, username: String, municipality: Int, adjustmentTimestamp: Long, linkSource: LinkGeomSource, createdByFromUpdate: Option[String] = Some(""), createdDateTimeFromUpdate: Option[DateTime]): Long = {
    val id = Sequences.nextPrimaryKeySeqValue
    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
    sqlu"""
      insert all
        into asset(id, asset_type_id, created_by, created_date, municipality_code, modified_by, modified_date)
        values ($id, ${OtherRoadwayMarkings.typeId}, $createdByFromUpdate, $createdDateTimeFromUpdate, $municipality, $username, sysdate)

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


  def updateFloatingAsset(otherRoadwayMarkingUpdated: OtherRoadwayMarking): Unit = {
    val id = otherRoadwayMarkingUpdated.id
  }



}