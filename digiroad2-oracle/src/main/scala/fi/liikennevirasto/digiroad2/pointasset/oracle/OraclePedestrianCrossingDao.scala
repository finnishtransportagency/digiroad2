package fi.liikennevirasto.digiroad2.pointasset.oracle

import fi.liikennevirasto.digiroad2.{IncomingPedestrianCrossing, PersistedPointAsset, Point}
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.{Queries, Sequences}
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.Queries._
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource
import slick.jdbc.{GetResult, PositionedResult, StaticQuery}
import slick.jdbc.StaticQuery.interpolation

case class PedestrianCrossing(id: Long, linkId: Long,
                              lon: Double, lat: Double,
                              mValue: Double, floating: Boolean,
                              vvhTimeStamp: Long,
                              municipalityCode: Int,
                              createdBy: Option[String] = None,
                              createdAt: Option[DateTime] = None,
                              modifiedBy: Option[String] = None,
                              modifiedAt: Option[DateTime] = None,
                              linkSource: LinkGeomSource) extends PersistedPointAsset

case class PedestrianCrossingToBePersisted(linkId: Long, lon: Double, lat: Double, mValue: Double, municipalityCode: Int, createdBy: String)

object OraclePedestrianCrossingDao {
  def update(id: Long, persisted: IncomingPedestrianCrossing, mValue: Double, username: String, municipality: Int, adjustedTimeStampOption: Option[Long] = None, linkSource: LinkGeomSource) = {
    sqlu""" update asset set municipality_code = ${municipality} where id = $id """.execute
    updateAssetGeometry(id, Point(persisted.lon, persisted.lat))
    updateAssetModified(id, username).execute

    adjustedTimeStampOption match {
      case Some(adjustedTimeStamp) =>
        sqlu"""
          update lrm_position
           set
           start_measure = ${mValue},
           link_id = ${persisted.linkId},
           adjusted_timestamp = ${adjustedTimeStamp},
           link_source = ${linkSource.value}
           where id = (select position_id from asset_link where asset_id = $id)
        """.execute
      case _ =>
        sqlu"""
          update lrm_position
           set
           start_measure = ${mValue},
           link_id = ${persisted.linkId},
           link_source = ${linkSource.value}
           where id = (select position_id from asset_link where asset_id = $id)
        """.execute
    }
    id
  }

  def create(crossing: IncomingPedestrianCrossing, mValue: Double, username: String, municipality: Long, adjustedTimestamp: Long, linkSource: LinkGeomSource): Long = {
    val id = Sequences.nextPrimaryKeySeqValue
    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
    sqlu"""
      insert all
        into asset(id, asset_type_id, created_by, created_date, municipality_code)
        values ($id, 200, $username, sysdate, ${municipality})

        into lrm_position(id, start_measure, link_id, adjusted_timestamp, link_source)
        values ($lrmPositionId, ${mValue}, ${crossing.linkId}, $adjustedTimestamp, ${linkSource.value})

        into asset_link(asset_id, position_id)
        values ($id, $lrmPositionId)
      select * from dual
    """.execute
    updateAssetGeometry(id, Point(crossing.lon, crossing.lat))

    id
  }

  def fetchByFilter(queryFilter: String => String): Seq[PedestrianCrossing] = {
    val query =
      """
        select a.id, pos.link_id, a.geometry, pos.start_measure, a.floating, pos.adjusted_timestamp, a.municipality_code, a.created_by, a.created_date, a.modified_by, a.modified_date,
        pos.link_source
        from asset a
        join asset_link al on a.id = al.asset_id
        join lrm_position pos on al.position_id = pos.id
      """
    val queryWithFilter = queryFilter(query) + " and (a.valid_to > sysdate or a.valid_to is null)"
    StaticQuery.queryNA[PedestrianCrossing](queryWithFilter).iterator.toSeq
  }

  implicit val getPointAsset = new GetResult[PedestrianCrossing] {
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

      PedestrianCrossing(id, linkId, point.x, point.y, mValue, floating, vvhTimeStamp, municipalityCode, createdBy, createdDateTime, modifiedBy, modifiedDateTime, linkSource = LinkGeomSource(linkSource))
    }
  }
}
