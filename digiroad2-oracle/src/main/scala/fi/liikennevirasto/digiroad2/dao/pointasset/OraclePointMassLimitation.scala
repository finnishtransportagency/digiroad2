package fi.liikennevirasto.digiroad2.dao.pointasset

import fi.liikennevirasto.digiroad2.dao.Queries._
import fi.liikennevirasto.digiroad2.PersistedPointAsset
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource
import fi.liikennevirasto.digiroad2.dao.Queries.bytesToPoint
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.service.pointasset.{WeightGroupLimitation}
import org.joda.time.DateTime
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedResult, StaticQuery}
import com.github.tototoshi.slick.MySQLJodaSupport._


class OraclePointMassLimitationDao {


  def fetchByBoundingBox(assetTypes: Seq[Int], queryFilter: String => String): Seq[WeightGroupLimitation] = {
    val query =
      """
        select a.id, a.asset_type_id, pos.link_id, a.geometry, pos.start_measure, a.floating, pos.adjusted_timestamp, a.municipality_code, a.created_by, a.created_date, a.modified_by, a.modified_date,
        pos.link_source, npv.value
        from asset a
        join asset_link al on a.id = al.asset_id
        join lrm_position pos on al.position_id = pos.id
        left join number_property_value npv on npv.asset_id = a.id
      """
    val queryWithFilter = queryFilter(query) +
      s" and (a.valid_to > sysdate or a.valid_to is null) and a.asset_type_id in (${assetTypes.mkString(",")})"
    StaticQuery.queryNA[WeightGroupLimitation](queryWithFilter).iterator.toSeq
  }

  implicit val getPointAsset = new GetResult[WeightGroupLimitation] {
    def apply(r: PositionedResult) = {
      val id = r.nextLong()
      val typeId = r.nextInt()
      val linkId = r.nextLong()
      val point = r.nextBytesOption().map(bytesToPoint).get
      val mValue = r.nextDouble()
      val floating = r.nextBoolean()
      val vvhTimeStamp = r.nextLong()
      val municipalityCode = r.nextInt()
      val createdBy = r.nextStringOption()
      val createdDateTime = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val modifiedBy = r.nextStringOption()
      val modifiedDateTime = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val linkSource = r.nextInt()
      val limit = r.nextDouble()

      WeightGroupLimitation(id, typeId, linkId, point.x, point.y, mValue, floating, vvhTimeStamp, municipalityCode, createdBy, createdDateTime, modifiedBy, modifiedDateTime, linkSource = LinkGeomSource(linkSource), limit)
    }
  }

}
