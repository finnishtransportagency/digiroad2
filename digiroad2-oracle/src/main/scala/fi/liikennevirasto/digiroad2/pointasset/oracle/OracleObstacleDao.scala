package fi.liikennevirasto.digiroad2.pointasset.oracle

import fi.liikennevirasto.digiroad2.asset.oracle.Queries._
import org.joda.time.DateTime
import slick.jdbc.{PositionedResult, GetResult, StaticQuery}
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession


/**
  * Created by venholat on 9.12.2015.
  */

case class PersistedObstacle(id: Long, mmlId: Long,
                             lon: Double, lat: Double,
                             mValue: Double, floating: Boolean,
                             municipalityCode: Int,
                             obstacleType: Int,
                             createdBy: Option[String] = None,
                             createdDateTime: Option[DateTime] = None,
                             modifiedBy: Option[String] = None,
                             modifiedDateTime: Option[DateTime] = None)
case class ObstacleToBePersisted(mmlId: Long, lon: Double, lat: Double, mValue: Double, municipalityCode: Int, createdBy: String)

object OracleObstacleDao {
  // This works as long as there is only one property (currently type) for obstacles and up to one value
  def fetchByFilter(queryFilter: String => String): Seq[PersistedObstacle] = {
    val query =
      """
        select a.id, pos.mml_id, a.geometry, pos.start_measure, a.floating, a.municipality_code, ev.value, a.created_by, a.created_date, a.modified_by, a.modified_date
        from asset a
        join asset_link al on a.id = al.asset_id
        join lrm_position pos on al.position_id = pos.id
        join property p on p.asset_type_id = a.id
        left join enumerated_value ev on ev.property_id = p.id
        left join single_choice_value scv on (scv.asset_id = a.id AND scv.enumerated_value_id = ev.id)
      """
    val queryWithFilter = queryFilter(query) + " and (a.valid_to > sysdate or a.valid_to is null)"
    StaticQuery.queryNA[PersistedObstacle](queryWithFilter).iterator.toSeq
  }
  implicit val getPointAsset = new GetResult[PersistedObstacle] {
    def apply(r: PositionedResult) = {
      val id = r.nextLong()
      val mmlId = r.nextLong()
      val point = r.nextBytesOption().map(bytesToPoint).get
      val mValue = r.nextDouble()
      val floating = r.nextBoolean()
      val municipalityCode = r.nextInt()
      val obstacleType = r.nextInt()
      val createdBy = r.nextStringOption()
      val createdDateTime = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val modifiedBy = r.nextStringOption()
      val modifiedDateTime = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))

      PersistedObstacle(id, mmlId, point.x, point.y, mValue, floating, obstacleType, municipalityCode, createdBy, createdDateTime, modifiedBy, modifiedDateTime)
    }
  }
}
