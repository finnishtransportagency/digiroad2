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

  def getSpeedLimits(bounds: BoundingRectangle): Seq[(Long, Long, Int, Int, Seq[(Double, Double)])] = {
    val boundingBox = new JGeometry(bounds.leftBottom.x, bounds.leftBottom.y, bounds.rightTop.x, bounds.rightTop.y, 3067)
    val geometry = storeGeometry(boundingBox, dynamicSession.conn)
    val speedLimits = sql"""
      select a.id, rl.id, pos.side_code, e.name_fi as speed_limit, SDO_AGGR_CONCAT_LINES(to_2d(sdo_lrs.dynamic_segment(rl.geom, pos.start_measure, pos.end_measure)))
        from ASSET a
        join ASSET_LINK al on a.id = al.asset_id
        join LRM_POSITION pos on al.position_id = pos.id
        join ROAD_LINK rl on pos.road_link_id = rl.id
        join PROPERTY p on a.asset_type_id = p.asset_type_id and p.public_id = 'rajoitus'
        join SINGLE_CHOICE_VALUE s on s.asset_id = a.id and s.property_id = p.id
        join ENUMERATED_VALUE e on s.enumerated_value_id = e.id
        where a.asset_type_id = 20 and SDO_FILTER(rl.geom, $geometry) = 'TRUE'
        group by a.id, rl.id, pos.side_code, e.name_fi
        """.as[(Long, Long, Int, Int, Array[Byte])].list
    speedLimits.map { case (id, roadLinkId, sideCode, limit, pos) =>
      val points = JGeometry.load(pos).getOrdinatesArray.grouped(2)
      (id, roadLinkId, sideCode, limit, points.map { pointArray =>
        (pointArray(0), pointArray(1))
      }.toSeq)
    }
  }

  def getSpeedLimits(id: Long): Seq[(Long, Seq[(Double, Double)])] = {
    val speedLimits = sql"""
      select a.id, SDO_AGGR_CONCAT_LINES(to_2d(sdo_lrs.dynamic_segment(rl.geom, pos.start_measure, pos.end_measure)))
        from ASSET a
        join ASSET_LINK al on a.id = al.asset_id
        join LRM_POSITION pos on al.position_id = pos.id
        join ROAD_LINK rl on pos.road_link_id = rl.id
        where a.asset_type_id = 20 and a.id = $id
        group by a.id, rl.id
        """.as[(Long, Array[Byte])].list
    speedLimits.map { case (id, pos) =>
      val points = JGeometry.load(pos).getOrdinatesArray.grouped(2)
      (id, points.map { pointArray =>
        (pointArray(0), pointArray(1))
      }.toSeq)
    }
  }

  def getSpeedLimitDetails(id: Long): (Option[String], Int) = {
    sql"""
      select a.modified_by, e.name_fi
      from ASSET a
      join PROPERTY p on a.asset_type_id = p.asset_type_id and p.public_id = 'rajoitus'
      join SINGLE_CHOICE_VALUE s on s.asset_id = a.id and s.property_id = p.id
      join ENUMERATED_VALUE e on s.enumerated_value_id = e.id
      where a.id = $id
    """.as[(Option[String], Int)].first
  }
}
