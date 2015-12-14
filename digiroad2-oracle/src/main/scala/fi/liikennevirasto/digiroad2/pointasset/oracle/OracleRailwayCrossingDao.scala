package fi.liikennevirasto.digiroad2.pointasset.oracle

import fi.liikennevirasto.digiroad2.{Point, PersistedPointAsset}
import fi.liikennevirasto.digiroad2.asset.oracle.{Sequences, Queries}
import fi.liikennevirasto.digiroad2.asset.oracle.Queries._
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import slick.jdbc.{GetResult, PositionedResult, StaticQuery}
import slick.jdbc.StaticQuery.interpolation

case class PersistedRailwayCrossing(id: Long, mmlId: Long,
                             lon: Double, lat: Double,
                             mValue: Double, floating: Boolean,
                             municipalityCode: Int,
                             railwayCrossingType: Int,
                             name: String,
                             createdBy: Option[String] = None,
                             createdDateTime: Option[DateTime] = None,
                             modifiedBy: Option[String] = None,
                             modifiedDateTime: Option[DateTime] = None) extends PersistedPointAsset

case class RailwayCrossingToBePersisted(mmlId: Long, lon: Double, lat: Double, mValue: Double, municipalityCode: Int, createdBy: String, railwayCrossingType: Int, name: String)

object OracleRailwayCrossingDao {

  // This works as long as there are only two properties of different types for railway crossings
  def fetchByFilter(queryFilter: String => String): Seq[PersistedRailwayCrossing] = {
    val railwayCrossingType = getRailwayCrossingType
    val namePropertyId = getNamePropertyId
    val query =
      s"""
        select a.id, pos.mml_id, a.geometry, pos.start_measure, a.floating, a.municipality_code, ev.value,
        tpv.value_fi, a.created_by, a.created_date, a.modified_by, a.modified_date
        from asset a
        join asset_link al on a.id = al.asset_id
        join lrm_position pos on al.position_id = pos.id
        left join single_choice_value scv on scv.asset_id = a.id
        left join enumerated_value ev on (scv.enumerated_value_id = ev.id)
        left join text_property_value tpv on (tpv.asset_id = a.id)
      """
    val queryWithFilter = queryFilter(query) + " and (a.valid_to > sysdate or a.valid_to is null) AND " +
      s"""ev.property_id = $railwayCrossingType AND tpv.property_id = $namePropertyId """
    StaticQuery.queryNA[PersistedRailwayCrossing](queryWithFilter).iterator.toSeq
  }

  implicit val getPointAsset = new GetResult[PersistedRailwayCrossing] {
    def apply(r: PositionedResult) = {
      val id = r.nextLong()
      val mmlId = r.nextLong()
      val point = r.nextBytesOption().map(bytesToPoint).get
      val mValue = r.nextDouble()
      val floating = r.nextBoolean()
      val municipalityCode = r.nextInt()
      val railwayCrossingType = r.nextInt()
      val name = r.nextString()
      val createdBy = r.nextStringOption()
      val createdDateTime = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val modifiedBy = r.nextStringOption()
      val modifiedDateTime = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))

      PersistedRailwayCrossing(id, mmlId, point.x, point.y, mValue, floating, municipalityCode, railwayCrossingType, name, createdBy, createdDateTime, modifiedBy, modifiedDateTime)
    }
  }

  def create(RailwayCrossing: RailwayCrossingToBePersisted, username: String): Long = {
    val id = Sequences.nextPrimaryKeySeqValue
    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
    sqlu"""
      insert all
        into asset(id, asset_type_id, created_by, created_date, municipality_code)
        values ($id, 220, $username, sysdate, ${RailwayCrossing.municipalityCode})

        into lrm_position(id, start_measure, mml_id)
        values ($lrmPositionId, ${RailwayCrossing.mValue}, ${RailwayCrossing.mmlId})

        into asset_link(asset_id, position_id)
        values ($id, $lrmPositionId)

      select * from dual
    """.execute
    updateAssetGeometry(id, Point(RailwayCrossing.lon, RailwayCrossing.lat))
    insertSingleChoiceProperty(id, getNamePropertyId, RailwayCrossing.railwayCrossingType).execute
    id
  }

  def update(id: Long, RailwayCrossing: RailwayCrossingToBePersisted) = {
    sqlu""" update asset set municipality_code = ${RailwayCrossing.municipalityCode} where id = $id """.execute
    updateAssetModified(id, RailwayCrossing.createdBy).execute
    updateAssetGeometry(id, Point(RailwayCrossing.lon, RailwayCrossing.lat))
    updateSingleChoiceProperty(id, getRailwayCrossingType, RailwayCrossing.railwayCrossingType).execute

    sqlu"""
      update lrm_position
       set
       start_measure = ${RailwayCrossing.mValue},
       mml_id = ${RailwayCrossing.mmlId}
       where id = (select position_id from asset_link where asset_id = $id)
    """.execute
    id
  }

  private def getRailwayCrossingType: Long = {
    StaticQuery.query[String, Long](Queries.propertyIdByPublicId).apply("turvavarustus").first
  }

  private def getNamePropertyId: Long = {
    StaticQuery.query[String, Long](Queries.propertyIdByPublicId).apply("rautatien_tasoristeyksen_nimi").first
  }
}



