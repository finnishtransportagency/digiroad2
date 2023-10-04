package fi.liikennevirasto.digiroad2.dao.pointasset

import fi.liikennevirasto.digiroad2.asset.{LinkGeomSource, TrHeightLimit}
import fi.liikennevirasto.digiroad2.dao.Queries.{objectToPoint, insertNumberProperty, updateAssetGeometry}
import fi.liikennevirasto.digiroad2.service.pointasset.{HeightLimit, IncomingHeightLimit}
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.dao.{Queries, Sequences}
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedResult, StaticQuery}

object PostGISHeightLimitDao {
  val typeId = TrHeightLimit.typeId

  def fetchByFilter(queryFilter: String => String): Seq[HeightLimit] = {
    val query =
      s"""
        select a.id, pos.link_id, a.geometry, pos.start_measure, a.floating, pos.adjusted_timestamp, a.municipality_code,
        a.created_by, a.created_date, a.modified_by, a.modified_date, pos.link_source, npv.value
        from asset a
        join asset_link al on a.id = al.asset_id
        join lrm_position pos on al.position_id = pos.id
        left join number_property_value npv on npv.asset_id = a.id
      """
    val queryWithFilter = queryFilter(query) + " and (a.valid_to > current_timestamp or a.valid_to is null) "
    StaticQuery.queryNA[HeightLimit](queryWithFilter)(getPointAsset).iterator.toSeq
  }

  def create(asset: IncomingHeightLimit, mValue: Double, municipality: Int, username: String, adjustedTimestamp: Long, linkSource: LinkGeomSource): Long = {
    val id = Sequences.nextPrimaryKeySeqValue
    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
    sqlu"""
      insert  into asset(id, asset_type_id, created_by, created_date, municipality_code)
        values ($id, $typeId, $username, current_timestamp, $municipality);

      insert  into lrm_position(id, start_measure, link_id, adjusted_timestamp, link_source)
        values ($lrmPositionId, $mValue, ${asset.linkId}, $adjustedTimestamp, ${linkSource.value});

      insert  into asset_link(asset_id, position_id)
        values ($id, $lrmPositionId);
    """.execute
    updateAssetGeometry(id, Point(asset.lon, asset.lat))
    insertNumberProperty(id, getLimitPropertyId, asset.limit).execute
    id
  }

  private def getLimitPropertyId: Long = {
    StaticQuery.query[String, Long](Queries.propertyIdByPublicId).apply("suurin_sallittu_korkeus").first
  }

  implicit val getPointAsset = new GetResult[HeightLimit] {
    def apply(r: PositionedResult): HeightLimit = {
      val id = r.nextLong()
      val linkId = r.nextString()
      val point = r.nextObjectOption().map(objectToPoint).get
      val mValue = r.nextDouble()
      val floating = r.nextBoolean()
      val timeStamp = r.nextLong()
      val municipalityCode = r.nextInt()
      val createdBy = r.nextStringOption()
      val createdDateTime = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val modifiedBy = r.nextStringOption()
      val modifiedDateTime = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val linkSource = r.nextInt()
      val limit = r.nextDouble()

      HeightLimit(id, linkId, point.x, point.y, mValue, floating, timeStamp, municipalityCode, createdBy, createdDateTime, modifiedBy, modifiedDateTime, linkSource = LinkGeomSource(linkSource), limit)
    }
  }

}
