package fi.liikennevirasto.digiroad2.linearasset.oracle

import _root_.oracle.spatial.geometry.JGeometry
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase._
import scala.slick.jdbc.{StaticQuery => Q, PositionedResult, GetResult, PositionedParameters, SetParameter}
import Q.interpolation
import scala.slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import Q.interpolation
import fi.liikennevirasto.digiroad2.asset.oracle.Queries._
import _root_.oracle.sql.STRUCT

object OracleLinearAssetDao {
  implicit object GetByteArray extends GetResult[Array[Byte]] {
    def apply(rs: PositionedResult) = rs.nextBytes()
  }

  implicit object SetStruct extends SetParameter[STRUCT] {
    def apply(v: STRUCT, pp: PositionedParameters) {
      pp.setObject(v, java.sql.Types.STRUCT)
    }
  }

  def getAll(bounds: Option[BoundingRectangle]): Seq[(Long, Seq[(Double, Double)])] = {
    val b = bounds.get
    val boundingBox = new JGeometry(b.leftBottom.x, b.leftBottom.y, b.rightTop.x, b.rightTop.y, 3067)
    val geometry = storeGeometry(boundingBox, dynamicSession.conn)
    val linearAssets = sql"""
      select a.id, SDO_AGGR_CONCAT_LINES(to_2d(sdo_lrs.dynamic_segment(rl.geom, pos.start_measure, pos.end_measure)))
        from ASSET a
        join ASSET_LINK al on a.id = al.asset_id
        join LRM_POSITION pos on al.position_id = pos.id
        join ROAD_LINK rl on pos.road_link_id = rl.id
        where SDO_FILTER(geom, $geometry) = 'TRUE'
        group by a.id, rl.id
        order by rl.id
        """.as[(Long, Array[Byte])].list
    linearAssets.map { case (id, pos) =>
      val points = JGeometry.load(pos).getOrdinatesArray.grouped(2)
      (id, points.map { pointArray =>
        (pointArray(0), pointArray(1))
      }.toSeq)
    }
  }
}
