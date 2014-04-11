package fi.liikennevirasto.digiroad2.linearasset.oracle

import _root_.oracle.spatial.geometry.JGeometry
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase._
import scala.slick.jdbc.{StaticQuery => Q, PositionedResult, GetResult, PositionedParameters, SetParameter}
import Q.interpolation
import scala.slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import Q.interpolation

object OracleLinearAssetDao {
  def get(id: Long): (Long, Seq[(Double, Double)]) = {
    (15, Seq(
      (292545.465942562, 6824449.3763405),
      (292537.36220989,6824447.14768917),
      (292530.875310699,6824446.07626144),
      (292517.699818906,6824443.85193657),
      (292503.884685422,6824441.10446331),
      (292493.333123626,6824437.88088131)))
  }

  implicit object GetByteArray extends GetResult[Array[Byte]] {
    def apply(rs: PositionedResult) = rs.nextBytes()
  }

  def getAll(): Seq[(Long, Seq[(Double, Double)])] = {
    val linearAssets = sql"""
      select a.id, SDO_AGGR_CONCAT_LINES(to_2d(sdo_lrs.dynamic_segment(rl.geom, pos.start_measure, pos.end_measure)))
        from ASSET a
        join ASSET_LINK al on a.id = al.asset_id
        join LRM_POSITION pos on al.position_id = pos.id
        join ROAD_LINK rl on pos.road_link_id = rl.id
        group by a.id""".as[(Long, Array[Byte])].list
    linearAssets.map { case (id, pos) =>
      val points = JGeometry.load(pos).getOrdinatesArray.grouped(2)
      (id, points.map { pointArray =>
        (pointArray(0), pointArray(1))
      }.toSeq)
    }
  }
}
