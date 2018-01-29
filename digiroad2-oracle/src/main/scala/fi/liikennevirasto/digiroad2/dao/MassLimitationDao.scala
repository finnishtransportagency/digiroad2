package fi.liikennevirasto.digiroad2.dao

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.oracle.MassQuery
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import com.github.tototoshi.slick.MySQLJodaSupport._
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{StaticQuery => Q}

class MassLimitationDao {

  /**
    * Iterates a set of link ids with asset type id and property id and returns linear assets. Used by LinearAssetService.getByRoadLinks.
    */
  def fetchLinearAssetsByLinkIds(assetTypeId: Seq[Int], linkIds: Seq[Long], valuePropertyId: String): Seq[PersistedLinearAsset] = {
    MassQuery.withIds(linkIds.toSet) { idTableName =>
      val assets = sql"""
        select a.id, pos.link_id, pos.side_code, s.value as total_weight_limit, pos.start_measure, pos.end_measure,
               a.created_by, a.created_date, a.modified_by, a.modified_date,
               case when a.valid_to <= sysdate then 1 else 0 end as expired, a.asset_type_id,
               pos.adjusted_timestamp, pos.modified_date, pos.link_source, a.verified_by, a.verified_date
          from asset a
          join asset_link al on a.id = al.asset_id
          join lrm_position pos on al.position_id = pos.id
          join property p on p.public_id = $valuePropertyId
          join #$idTableName i on i.id = pos.link_id
          left join number_property_value s on s.asset_id = a.id and s.property_id = p.id
          where a.asset_type_id in (#${assetTypeId.mkString(",")})
          and (a.valid_to > sysdate or a.valid_to is null)
          and a.floating = 0"""
        .as[(Long, Long, Int, Option[Int], Double, Double, Option[String], Option[DateTime], Option[String], Option[DateTime], Boolean, Int, Long, Option[DateTime], Int, Option[String], Option[DateTime])].list
      assets.map { case(id, linkId, sideCode, value, startMeasure, endMeasure, createdBy, createdDate, modifiedBy, modifiedDate, expired, typeId, vvhTimeStamp, geomModifiedDate, linkSource, verifiedBy, verifiedDate) =>
        PersistedLinearAsset(id, linkId, sideCode, value.map(NumericValue), startMeasure, endMeasure, createdBy, createdDate, modifiedBy, modifiedDate, expired, typeId, vvhTimeStamp, geomModifiedDate, LinkGeomSource.apply(linkSource), verifiedBy, verifiedDate)
      }
    }
  }
}
