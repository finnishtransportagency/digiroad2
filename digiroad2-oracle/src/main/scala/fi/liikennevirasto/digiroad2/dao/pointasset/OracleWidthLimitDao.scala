package fi.liikennevirasto.digiroad2.dao.pointasset

import fi.liikennevirasto.digiroad2.asset.{LinkGeomSource, TrWidthLimit}
import fi.liikennevirasto.digiroad2.dao.Queries.{bytesToPoint, insertNumberProperty, insertSingleChoiceProperty, updateAssetGeometry}
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.dao.{Queries, Sequences}
import fi.liikennevirasto.digiroad2.{IncomingPointAsset, PersistedPointAsset, Point}
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedResult, StaticQuery}

sealed trait WidthLimitReason {
  def value: Int
}
object WidthLimitReason {
  val values = Set(Bridge, FullPortal, HalfPortal, Railing, Lamp, Fence, Abutment, TrafficLightPost, Other, Unknown)

  def apply(intValue: Int): WidthLimitReason = {
    values.find(_.value == intValue).getOrElse(Unknown)
  }

  case object Bridge extends WidthLimitReason { def value = 1 }
  case object FullPortal extends WidthLimitReason { def value = 2 }
  case object HalfPortal extends WidthLimitReason { def value = 3 }
  case object Railing extends WidthLimitReason { def value = 4 }
  case object Lamp extends WidthLimitReason { def value = 5 }
  case object Fence extends WidthLimitReason { def value = 6 }
  case object Abutment extends WidthLimitReason { def value = 7 }
  case object TrafficLightPost extends WidthLimitReason { def value = 8 }
  case object Other extends WidthLimitReason { def value = 9 }
  case object Unknown extends WidthLimitReason { def value = 99 }
}

case class IncomingWidthLimit(lon: Double, lat: Double, linkId: Long, limit: Double, reason: WidthLimitReason, validityDirection: Int, bearing: Option[Int]) extends IncomingPointAsset

case class WidthLimit(id: Long, linkId: Long,
                       lon: Double, lat: Double,
                       mValue: Double, floating: Boolean,
                       vvhTimeStamp: Long,
                       municipalityCode: Int,
                       createdBy: Option[String] = None,
                       createdAt: Option[DateTime] = None,
                       modifiedBy: Option[String] = None,
                       modifiedAt: Option[DateTime] = None,
                       linkSource: LinkGeomSource,
                       limit: Double,
                       reason: WidthLimitReason) extends PersistedPointAsset

object OracleWidthLimitDao {
  val typeId = TrWidthLimit.typeId

  def fetchByFilter(queryFilter: String => String): Seq[WidthLimit] = {
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
    StaticQuery.queryNA[WidthLimit](queryWithFilter).iterator.toSeq
  }

  def create(asset: IncomingWidthLimit, mValue: Double, municipality: Int, username: String, adjustedTimestamp: Long, linkSource: LinkGeomSource): Long = {
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
    insertSingleChoiceProperty(id, getReasonPropertyId, asset.reason.value).execute
    insertNumberProperty(id, getLimitPropertyId, asset.limit).execute
    id
  }

  private def getReasonPropertyId: Long = {
    StaticQuery.query[String, Long](Queries.propertyIdByPublicId).apply("suurin_sallittu_leveys_syy").first
  }

  private def getLimitPropertyId: Long = {
    StaticQuery.query[String, Long](Queries.propertyIdByPublicId).apply("suurin_sallittu_leveys").first
  }

  implicit val getPointAsset = new GetResult[WidthLimit] {
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
      val reason =  WidthLimitReason.apply(r.nextInt())

      WidthLimit(id, linkId, point.x, point.y, mValue, floating, vvhTimeStamp, municipalityCode, createdBy, createdDateTime, modifiedBy, modifiedDateTime, linkSource = LinkGeomSource(linkSource), limit, reason)
    }
  }
}
