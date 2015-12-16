package fi.liikennevirasto.digiroad2.pointasset.oracle

import fi.liikennevirasto.digiroad2.{IncomingRailwayCrossing, Point, PersistedPointAsset}
import fi.liikennevirasto.digiroad2.asset.oracle.{Sequences, Queries}
import fi.liikennevirasto.digiroad2.asset.oracle.Queries._
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import slick.jdbc.{GetResult, PositionedResult, StaticQuery}
import slick.jdbc.StaticQuery.interpolation

case class DirectionalTrafficSign(id: Long, mmlId: Long,
                                  lon: Double, lat: Double,
                                  mValue: Double, floating: Boolean,
                                  municipalityCode: Int,
                                  validityDirection: Int,
                                  text: Option[String],
                                  createdBy: Option[String] = None,
                                  createdDateTime: Option[DateTime] = None,
                                  modifiedBy: Option[String] = None,
                                  modifiedDateTime: Option[DateTime] = None) extends PersistedPointAsset

object OracleDirectionalTrafficSignDao {
  def fetchByFilter(queryFilter: String => String): Seq[DirectionalTrafficSign] = {
    val query =
      s"""
        select a.id, lrm.mml_id, a.geometry, lrm.start_measure, a.floating, a.municipality_code, lrm.side_code,
        tpv.value_fi, a.created_by, a.created_date, a.modified_by, a.modified_date
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
      val mmlId = r.nextLong()
      val point = r.nextBytesOption().map(bytesToPoint).get
      val mValue = r.nextDouble()
      val floating = r.nextBoolean()
      val municipalityCode = r.nextInt()
      val validityDirection = r.nextInt()
      val text = r.nextStringOption()
      val createdBy = r.nextStringOption()
      val createdDateTime = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val modifiedBy = r.nextStringOption()
      val modifiedDateTime = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))

      DirectionalTrafficSign(id, mmlId, point.x, point.y, mValue, floating, municipalityCode, validityDirection, text, createdBy, createdDateTime, modifiedBy, modifiedDateTime)
    }
  }

  private def getTextPropertyId: Long = {
    StaticQuery.query[String, Long](Queries.propertyIdByPublicId).apply("opastustaulun_teksti").first
  }
}



