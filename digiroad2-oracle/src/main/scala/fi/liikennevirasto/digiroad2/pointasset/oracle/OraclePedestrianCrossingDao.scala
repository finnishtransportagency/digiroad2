package fi.liikennevirasto.digiroad2.pointasset.oracle

import fi.liikennevirasto.digiroad2.RoadLinkAssociatedPointAsset
import fi.liikennevirasto.digiroad2.asset.oracle.Queries._
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import slick.jdbc.{GetResult, PositionedResult, StaticQuery}

case class PersistedPedestrianCrossing(id: Long, mmlId: Long,
                                       lon: Double, lat: Double,
                                       mValue: Double, floating: Boolean,
                                       municipalityCode: Int) extends RoadLinkAssociatedPointAsset

object OraclePedestrianCrossingDao {
  def fetchByFilter(queryFilter: String => String): Seq[PersistedPedestrianCrossing] = {
    val query =
      """
        select a.id, pos.mml_id, a.geometry, pos.start_measure
        from asset a
        join asset_link al on a.id = al.asset_id
        join lrm_position pos on al.position_id = pos.id
      """
    val queryWithFilter = queryFilter(query)
    StaticQuery.queryNA[PersistedPedestrianCrossing](queryWithFilter).iterator.toSeq
  }

  implicit val getPointAsset = new GetResult[PersistedPedestrianCrossing] {
    def apply(r: PositionedResult) = {
      val id = r.nextLong()
      val mmlId = r.nextLong()
      val point = r.nextBytesOption().map(bytesToPoint).get
      val mValue = r.nextDouble()
      PersistedPedestrianCrossing(id, mmlId, point.x, point.y, mValue, false, 235)
    }
  }
}
