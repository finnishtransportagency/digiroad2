package fi.liikennevirasto.digiroad2.dao.pointasset

import fi.liikennevirasto.digiroad2.PersistedPoint
import fi.liikennevirasto.digiroad2.asset.{LinkGeomSource, Property}
import fi.liikennevirasto.digiroad2.dao.Queries._
import org.joda.time.DateTime


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

  def fetchByFilterWithExpired(queryFilter: String => String): Seq[OtherRoadwayMarking] = {
    val queryWithFilter = queryFilter(query())
    queryToOtherRoadwayMarking(queryWithFilter)
  }

  def updateFloatingAsset(otherRoadwayMarkingUpdated: OtherRoadwayMarking): Unit = {
    val id = otherRoadwayMarkingUpdated.id


  }



}