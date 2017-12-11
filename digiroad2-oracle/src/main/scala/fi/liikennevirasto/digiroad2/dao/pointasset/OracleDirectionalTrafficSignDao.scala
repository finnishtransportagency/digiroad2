package fi.liikennevirasto.digiroad2.dao.pointasset

import fi.liikennevirasto.digiroad2.dao.Queries._
import fi.liikennevirasto.digiroad2.{PersistedPointAsset, Point}
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource
import fi.liikennevirasto.digiroad2.dao.{Queries, Sequences}
import fi.liikennevirasto.digiroad2.service.pointasset.IncomingDirectionalTrafficSign
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedResult, StaticQuery}

case class DirectionalTrafficSign(id: Long, linkId: Long,
                                  lon: Double, lat: Double,
                                  mValue: Double, floating: Boolean,
                                  vvhTimeStamp: Long,
                                  municipalityCode: Int,
                                  validityDirection: Int,
                                  text: Option[String],
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
        select a.id, lrm.link_id, a.geometry, lrm.start_measure, a.floating, lrm.adjusted_timestamp, a.municipality_code, lrm.side_code,
        tpv.value_fi, a.created_by, a.created_date, a.modified_by, a.modified_date, a.bearing, lrm.link_source
        from asset a
        join asset_link al on a.id = al.asset_id
        join lrm_position lrm on al.position_id = lrm.id
        left join text_property_value tpv on (tpv.property_id = $getTextPropertyId AND tpv.asset_id = a.id)

      """
    val queryWithFilter = queryFilter(query) + " and (a.valid_to > sysdate or a.valid_to is null) "
    StaticQuery.queryNA[DirectionalTrafficSign](queryWithFilter).iterator.toSeq
  }

  implicit val getPointAsset = new GetResult[DirectionalTrafficSign] {
    def apply(r: PositionedResult) = {
      val id = r.nextLong()
      val linkId = r.nextLong()
      val point = r.nextBytesOption().map(bytesToPoint).get
      val mValue = r.nextDouble()
      val floating = r.nextBoolean()
      val vvhTimeStamp = r.nextLong()
      val municipalityCode = r.nextInt()
      val validityDirection = r.nextInt()
      val text = r.nextStringOption()
      val createdBy = r.nextStringOption()
      val createdDateTime = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val modifiedBy = r.nextStringOption()
      val modifiedDateTime = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val bearing = r.nextIntOption()
      val linkSource = r.nextInt()

      DirectionalTrafficSign(id, linkId, point.x, point.y, mValue, floating, vvhTimeStamp, municipalityCode, validityDirection, text, bearing, createdBy, createdDateTime, modifiedBy, modifiedDateTime, linkSource = LinkGeomSource(linkSource))
    }
  }

  def create(sign: IncomingDirectionalTrafficSign, mValue: Double,  municipality: Int, username: String): Long = {
    val id = Sequences.nextPrimaryKeySeqValue

    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
    sqlu"""
      insert all
        into asset(id, asset_type_id, created_by, created_date, municipality_code, bearing)
        values ($id, 240, $username, sysdate, $municipality, ${sign.bearing})
        into lrm_position(id, start_measure, end_measure, link_id, side_code)
        values ($lrmPositionId, $mValue, $mValue, ${sign.linkId}, ${sign.validityDirection})
        into asset_link(asset_id, position_id)
        values ($id, $lrmPositionId)
      select * from dual
    """.execute
    updateAssetGeometry(id, Point(sign.lon, sign.lat))
    sign.text.foreach(insertTextProperty(id, getTextPropertyId, _).execute)
    id
  }

  def update(id: Long, sign: IncomingDirectionalTrafficSign, mValue: Double, municipality: Int, username: String) = {
    sqlu""" update asset set municipality_code = $municipality, bearing=${sign.bearing} where id = $id """.execute
    updateAssetModified(id, username).execute
    updateAssetGeometry(id, Point(sign.lon, sign.lat))
    deleteTextProperty(id, getTextPropertyId).execute
    sign.text.foreach(insertTextProperty(id, getTextPropertyId, _).execute)

    sqlu"""
      update lrm_position
       set
       start_measure = $mValue,
       link_id = ${sign.linkId},
       side_code = ${sign.validityDirection}
       where id = (select position_id from asset_link where asset_id = $id)
    """.execute
    id
  }

  private def getTextPropertyId: Long = {
    StaticQuery.query[String, Long](Queries.propertyIdByPublicId).apply("opastustaulun_teksti").first
  }
}



