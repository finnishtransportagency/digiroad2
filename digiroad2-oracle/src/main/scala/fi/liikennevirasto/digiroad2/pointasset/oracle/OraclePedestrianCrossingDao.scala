package fi.liikennevirasto.digiroad2.pointasset.oracle

import fi.liikennevirasto.digiroad2.{RoadLinkAssociatedPointAsset, Point, PedestrianCrossing}
import fi.liikennevirasto.digiroad2.asset.oracle.Queries._
import fi.liikennevirasto.digiroad2.oracle.MassQuery
import slick.jdbc.{PositionedResult, GetResult}
import slick.jdbc.StaticQuery.interpolation
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession

case class PersistedPedestrianCrossing(id: Long, mmlId: Long,
                                       lon: Double, lat: Double,
                                       mValue: Double, floating: Boolean,
                                       municipalityCode: Int) extends RoadLinkAssociatedPointAsset

object OraclePedestrianCrossingDao {
  def getByMmldIds(mmlIds: Seq[Long]): Seq[PedestrianCrossing] = {
    MassQuery.withIds(mmlIds.toSet) { idTableName =>
      sql"""
        select a.id, pos.mml_id, a.geometry, pos.start_measure
        from asset a
        join asset_link al on a.id = al.asset_id
        join lrm_position pos on al.position_id = pos.id
        join  #$idTableName i on i.id = pos.mml_id
        where a.asset_type_id = 200 and floating = 0
       """.as[PedestrianCrossing].list
    }
  }

  implicit val getPointAsset = new GetResult[PedestrianCrossing] {
    def apply(r: PositionedResult) = {
      val id = r.nextLong()
      val mmlId = r.nextLong()
      val point = r.nextBytesOption().map(bytesToPoint).get
      val mValue = r.nextDouble()
      PedestrianCrossing(id, mmlId, point.x, point.y, mValue, false)
    }
  }
}
