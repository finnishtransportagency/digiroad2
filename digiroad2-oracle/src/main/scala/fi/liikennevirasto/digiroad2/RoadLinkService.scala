package fi.liikennevirasto.digiroad2

import scala.slick.driver.JdbcDriver.backend.Database
import scala.slick.driver.JdbcDriver.backend.Database.dynamicSession
import scala.slick.jdbc.GetResult
import scala.slick.jdbc.PositionedResult
import scala.slick.jdbc.StaticQuery.interpolation

import com.jolbox.bonecp.BoneCPConfig
import com.jolbox.bonecp.BoneCPDataSource

import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import fi.liikennevirasto.digiroad2.asset.RoadLinkType
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.user.User
import _root_.oracle.spatial.geometry.JGeometry

object RoadLinkService {
  lazy val dataSource = {
    val cfg = new BoneCPConfig(OracleDatabase.loadProperties("/conversion.bonecp.properties"))
    new BoneCPDataSource(cfg)
  }

  implicit object GetByteArray extends GetResult[Array[Byte]] {
    def apply(rs: PositionedResult) = rs.nextBytes()
  }

  private def toPoints(bytes: Array[Byte]): Seq[Point] = {
    val geometry = JGeometry.load(bytes)
    geometry.getOrdinatesArray.grouped(2).map { point =>
      Point(point(0), point(1))
    }.toList
  }

  // TODO: Change signature to return `Seq[Point]`
  def getRoadLinkGeometry(id: Long, startMeasure: Double, endMeasure: Double): Seq[(Double, Double)] = {
    Database.forDataSource(dataSource).withDynTransaction {
      val query = sql"""
        select to_2d(sdo_lrs.dynamic_segment(shape, $startMeasure, $endMeasure))
        from tielinkki
        where objectid = $id
        """
      toPoints(query.as[Array[Byte]].first).map { point =>
        (point.x, point.y)
      }
    }
  }

  def getRoadLinks(bounds: BoundingRectangle): Seq[Map[String, Any]] = {
    Database.forDataSource(dataSource).withDynTransaction {
      val leftBottomX = bounds.leftBottom.x
      val leftBottomY = bounds.leftBottom.y
      val rightTopX = bounds.rightTop.x
      val rightTopY = bounds.rightTop.y

      val query =
        sql"""
            select objectid, to_2d(shape)
              from tielinkki
              where mod(functionalroadclass, 10) IN (1, 2, 3, 4, 5, 6) and
                    mdsys.sdo_filter(tielinkki.shape,
                                      sdo_cs.viewport_transform(
                                        mdsys.sdo_geometry(
                                          2003,
                                          0,
                                          NULL,
                                          mdsys.sdo_elem_info_array(1,1003,3),
                                          mdsys.sdo_ordinate_array($leftBottomX,
                                                                   $leftBottomY,
                                                                   $rightTopX,
                                                                   $rightTopY)
                                        ),
                                        3067
                                      ),
                                      'querytype=WINDOW'
                                    ) = 'TRUE'
        """

      query.as[(Long, Array[Byte])].list().map { roadLink =>
        val (id, geometry) = roadLink
        Map("roadLinkId" -> id,
            "points" -> toPoints(geometry))
      }
    }
  }
}
