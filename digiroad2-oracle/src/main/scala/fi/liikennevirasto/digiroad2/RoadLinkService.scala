package fi.liikennevirasto.digiroad2

import java.text.{DecimalFormat, NumberFormat}
import java.util.Locale

import org.joda.time.LocalDate

import scala.slick.driver.JdbcDriver.backend.Database
import scala.slick.driver.JdbcDriver.backend.Database.dynamicSession
import scala.slick.jdbc.GetResult
import scala.slick.jdbc.PositionedResult
import scala.slick.jdbc.StaticQuery.interpolation
import scala.slick.jdbc.{StaticQuery => Q}

import com.jolbox.bonecp.BoneCPConfig
import com.jolbox.bonecp.BoneCPDataSource

import fi.liikennevirasto.digiroad2.asset.{RoadLink, BoundingRectangle, RoadLinkType}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.user.User
import _root_.oracle.spatial.geometry.JGeometry

object RoadLinkService {

  lazy val dataSource = {
    val cfg = new BoneCPConfig(OracleDatabase.loadProperties("/conversion.bonecp.properties"))
    new BoneCPDataSource(cfg)
  }

  implicit object GetPointSeq extends GetResult[Seq[Point]] {
    def apply(rs: PositionedResult) = toPoints(rs.nextBytes())
  }

  private def toPoints(bytes: Array[Byte]): Seq[Point] = {
    val geometry = JGeometry.load(bytes)
    if (geometry == null) Nil
    else {
      geometry.getOrdinatesArray.grouped(2).map { point ⇒
        Point(point(0), point(1))
      }.toList
    }
  }

  def getPointOnRoadLink(id: Long, measure: Double): Option[(Long, Option[Point])] = {
    Database.forDataSource(dataSource).withDynTransaction {
      val query = sql"""
        select dr1_id, to_2d(sdo_lrs.dynamic_segment(shape, $measure, $measure))
          from tielinkki_ctas
          where dr1_id = $id
        """
      query.as[(Long, Seq[Point])].firstOption.map {
        case (roadLinkId, points) ⇒
          (roadLinkId, points.headOption)
      }
    }
  }

  def getMunicipalityAndPointOnRoadLinkByTestId(testId: Long, measure: Double): Option[(Long, Int, Option[Point])] = {
    Database.forDataSource(dataSource).withDynTransaction {
       val query = sql"""
        select prod.dr1_id, prod.kunta_nro, to_2d(sdo_lrs.dynamic_segment(prod.shape, $measure, $measure))
          from tielinkki_ctas prod
          join tielinkki test
          on prod.mml_id = test.mml_id
          where test.objectid = $testId
        """
      query.as[(Long, Int, Seq[Point])].iterator().toList match {
        case List(productionLink) => Some((productionLink._1, productionLink._2, productionLink._3.headOption))
        case _ => None
      }
    }
  }

  def getRoadLinkGeometry(id: Long, startMeasure: Double, endMeasure: Double): Seq[Point] = {
    Database.forDataSource(dataSource).withDynTransaction {
      val query = sql"""
        select to_2d(sdo_lrs.dynamic_segment(shape, $startMeasure, $endMeasure))
          from tielinkki_ctas
          where dr1_id = $id
        """
      query.as[Seq[Point]].first
    }
  }

  implicit val getRoadLinkType = new GetResult[RoadLinkType] {
    def apply(r: PositionedResult) = {
      RoadLinkType(r.nextInt() / 10)
    }
  }

  def getRoadLinks(bounds: BoundingRectangle, filterRoads: Boolean = true, municipalities: Set[Int] = Set()): Seq[(Long, Seq[Point], Double, RoadLinkType, Int)] = {
    Database.forDataSource(dataSource).withDynTransaction {
      val leftBottomX = bounds.leftBottom.x
      val leftBottomY = bounds.leftBottom.y
      val rightTopX = bounds.rightTop.x
      val rightTopY = bounds.rightTop.y

      val roadFilter = if (filterRoads) "mod(functionalroadclass, 10) IN (1, 2, 3, 4, 5, 6) and" else ""
      val municipalityFilter = if (municipalities.nonEmpty) "kunta_nro in (" + municipalities.mkString(",") + ") and" else ""
      val query =
      s"""
            select dr1_id, to_2d(shape), sdo_lrs.geom_segment_length(shape) as length, functionalroadclass as roadLinkType, mod(functionalroadclass, 10)
              from tielinkki_ctas
              where $roadFilter $municipalityFilter
                    mdsys.sdo_filter(shape,
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
      Q.queryNA[(Long, Seq[Point], Double, RoadLinkType, Int)](query).iterator().toSeq
    }
  }
}
