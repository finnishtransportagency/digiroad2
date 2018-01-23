package fi.liikennevirasto.digiroad2.dao.pointasset

import fi.liikennevirasto.digiroad2.dao.Queries._
import fi.liikennevirasto.digiroad2.service.pointasset.{IncomingWeightLimit, WeightLimit}
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.{LinkGeomSource, TrBogieWeightLimit}
import fi.liikennevirasto.digiroad2.dao.{Queries, Sequences}
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedResult, StaticQuery}

object OracleBogieWeightLimitDao {
  val typeId = TrBogieWeightLimit.typeId

  def fetchByFilter(queryFilter: String => String): Seq[WeightLimit] = {
    val query =
      """
        select a.id, pos.link_id, a.geometry, pos.start_measure, a.floating, pos.adjusted_timestamp, a.municipality_code, a.created_by, a.created_date, a.modified_by, a.modified_date,
        pos.link_source
        from asset a
        join asset_link al on a.id = al.asset_id
        join lrm_position pos on al.position_id = pos.id
        left join number_property_value npv on npv.asset_id = a.id
      """
    val queryWithFilter = queryFilter(query) + " and (a.valid_to > sysdate or a.valid_to is null)"
    StaticQuery.queryNA[WeightLimit](queryWithFilter).iterator.toSeq
  }

  def create(asset: IncomingWeightLimit, mValue: Double, municipality: Int, username: String, adjustedTimestamp: Long, linkSource: LinkGeomSource): Long = {
    val id = Sequences.nextPrimaryKeySeqValue
    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
    sqlu"""
      insert all
        into asset(id, asset_type_id, created_by, created_date, municipality_code)
        values ($id, $typeId, $username, sysdate, $municipality)

        into lrm_position(id, start_measure, link_id, adjusted_timestamp, link_source)
        values ($lrmPositionId, $mValue, ${asset.linkId}, $adjustedTimestamp, ${linkSource.value})

        into asset_link(asset_id, position_id)
        values ($id, $lrmPositionId)

      select * from dual
    """.execute
    updateAssetGeometry(id, Point(asset.lon, asset.lat))
    insertNumberProperty(id, getLimitPropertyId, asset.limit).execute
    id
  }

  private def getLimitPropertyId: Long = {
    StaticQuery.query[String, Long](Queries.propertyIdByPublicId).apply("suurin_sallittu_telimassa").first
  }

  implicit val getPointAsset = new GetResult[WeightLimit] {
    def apply(r: PositionedResult) = {
      val id = r.nextLong()
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

      WeightLimit(id, linkId, point.x, point.y, mValue, floating, vvhTimeStamp, municipalityCode, createdBy, createdDateTime, modifiedBy, modifiedDateTime, linkSource = LinkGeomSource(linkSource), limit)
    }
  }
}