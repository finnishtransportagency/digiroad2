package fi.liikennevirasto.digiroad2

import java.text.{DecimalFormat, NumberFormat}
import java.util.Locale

import fi.liikennevirasto.digiroad2.asset.oracle.AssetPropertyConfiguration.DateTimePropertyFormat
import fi.liikennevirasto.digiroad2.asset.oracle.Queries
import fi.liikennevirasto.digiroad2.oracle.collections.OracleArray
import org.joda.time.format.DateTimeFormat
import org.joda.time.{DateTime, LocalDate}

import scala.slick.driver.JdbcDriver.backend.Database
import scala.slick.driver.JdbcDriver.backend.Database.dynamicSession
import scala.slick.jdbc.GetResult
import scala.slick.jdbc.PositionedResult
import scala.slick.jdbc.StaticQuery.interpolation
import scala.slick.jdbc.{StaticQuery => Q}

import com.jolbox.bonecp.BoneCPConfig
import com.jolbox.bonecp.BoneCPDataSource

import fi.liikennevirasto.digiroad2.asset.{TrafficDirection, RoadLink, BoundingRectangle, RoadLinkType}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.user.User
import _root_.oracle.spatial.geometry.JGeometry
import collection.JavaConversions._

object RoadLinkService {
  type BasicRoadLink = (Long, Long, Seq[Point], Double, RoadLinkType, Int, TrafficDirection)
  type RoadLink = (Long, Long, Seq[Point], Double, RoadLinkType, Int, TrafficDirection, Option[String])

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

  def getByTestId(testId: Long): Option[(Long, Int, RoadLinkType)] = {
    Database.forDataSource(dataSource).withDynTransaction {
      val query = sql"""
         select prod.dr1_id, prod.kunta_nro, prod.functionalroadclass
           from tielinkki_ctas prod
           join tielinkki test
           on prod.mml_id = test.mml_id
           where test.objectid = $testId
        """
      query.as[(Long, Int, RoadLinkType)].list() match {
        case List(productionLink) => Some((productionLink._1, productionLink._2, productionLink._3))
        case _ => None
      }
    }
  }

  def getByIdAndMeasure(id: Long, measure: Double): Option[(Long, Int, Option[Point], RoadLinkType)] = {
    Database.forDataSource(dataSource).withDynTransaction {
      val query = sql"""
         select dr1_id, kunta_nro, to_2d(sdo_lrs.dynamic_segment(shape, $measure, $measure)), functionalroadclass
           from tielinkki_ctas
           where dr1_id = $id
        """
      query.as[(Long, Int, Seq[Point], RoadLinkType)].firstOption().map {
        case (roadLinkId, municipalityNumber, geometry, roadLinkType) => (roadLinkId, municipalityNumber, geometry.headOption, roadLinkType)
      }
    }
  }

  def getByTestIdAndMeasure(testId: Long, measure: Double): Option[(Long, Int, Option[Point], RoadLinkType)] = {
    Database.forDataSource(dataSource).withDynTransaction {
       val query = sql"""
         select prod.dr1_id, prod.kunta_nro, to_2d(sdo_lrs.dynamic_segment(prod.shape, $measure, $measure)), prod.functionalroadclass
           from tielinkki_ctas prod
           join tielinkki test
           on prod.mml_id = test.mml_id
           where test.objectid = $testId
        """
      query.as[(Long, Int, Seq[Point], RoadLinkType)].list() match {
        case List(productionLink) => Some((productionLink._1, productionLink._2, productionLink._3.headOption, productionLink._4))
        case _ => None
      }
    }
  }

  def getMunicipalityCode(roadLinkId: Long): Option[Int] = {
    Database.forDataSource(dataSource).withDynTransaction {
       val query = sql"""
         select prod.kunta_nro
           from tielinkki_ctas prod
           where prod.dr1_id = $roadLinkId
        """
      query.as[Int].firstOption
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

  def getRoadLinkGeometry(id: Long): Option[Seq[Point]] = {
    Database.forDataSource(dataSource).withDynTransaction {
      val query = sql"""
        select to_2d(shape)
          from tielinkki_ctas
          where dr1_id = $id
        """
      query.as[Seq[Point]].firstOption
    }
  }

  def getRoadLinkGeometryByTestId(testId: Long): Option[Seq[Point]] = {
    Database.forDataSource(dataSource).withDynTransaction {
      val query = sql"""
        select to_2d(prod.shape)
           from tielinkki_ctas prod
           join tielinkki test
           on prod.mml_id = test.mml_id
           where test.objectid = $testId
        """
      query.as[Seq[Point]].firstOption
    }
  }

  def getTestId(id: Long): Option[Long] = {
    Database.forDataSource(dataSource).withDynTransaction {
      val query = sql"""
        select test.objectid
           from tielinkki_ctas prod
           join tielinkki test
           on prod.mml_id = test.mml_id
           where prod.dr1_id = $id
        """
      query.as[Long].firstOption
    }
  }

  def getPointLRMeasure(roadLinkId: Long, point: Point): BigDecimal = {
    Database.forDataSource(dataSource).withDynTransaction {
      val x = point.x
      val y = point.y
      val query =
        s"""
          SELECT
            SDO_LRS.GET_MEASURE(
              SDO_LRS.PROJECT_PT(
                tl.shape,
                MDSYS.SDO_GEOMETRY(2001,
                                   3067,
                                   NULL,
                                   MDSYS.SDO_ELEM_INFO_ARRAY(1,1,1),
                                   MDSYS.SDO_ORDINATE_ARRAY($x, $y)
                                  )
                ))
            FROM tielinkki_ctas tl
            WHERE tl.dr1_id = $roadLinkId
        """
      Q.queryNA[BigDecimal](query).first
    }
  }

  implicit val getRoadLinkType = new GetResult[RoadLinkType] {
    def apply(r: PositionedResult) = {
      RoadLinkType(r.nextInt() / 10)
    }
  }

  implicit val getTrafficDirection = new GetResult[TrafficDirection] {
    def apply(r: PositionedResult) = {
      TrafficDirection(r.nextIntOption())
    }
  }

  def getRoadLinkLength(id: Long): Option[Double] = {
    Database.forDataSource(dataSource).withDynTransaction {
      val query = sql"""
        select sdo_lrs.geom_segment_length(shape) as length
          from tielinkki_ctas
          where dr1_id = $id
        """
      query.as[Double].firstOption
    }
  }

  private def getRoadLinkProperties(id: Long): BasicRoadLink = {
    sql"""select dr1_id, mml_id, to_2d(shape), sdo_lrs.geom_segment_length(shape) as length, functionalroadclass as roadLinkType, mod(functionalroadclass, 10), liikennevirran_suunta
            from tielinkki_ctas where dr1_id = $id"""
      .as[BasicRoadLink].first()
  }

  private def addAdjustment(adjustmentTable: String, adjustmentColumn: String, adjustment: Int, unadjustedValue: Int, mmlId: Long) = {
    Database.forDataSource(OracleDatabase.ds).withDynTransaction {
      val optionalAdjustment: Option[Int] = sql"""select #$adjustmentColumn from #$adjustmentTable where mml_id = $mmlId""".as[Int].firstOption
      optionalAdjustment match {
        case Some(existingAdjustment) =>
          if (existingAdjustment != adjustment) {
            sqlu"""update #$adjustmentTable set #$adjustmentColumn = $adjustment where mml_id = $mmlId""".execute()
          }
        case None =>
          if (unadjustedValue != adjustment) {
            sqlu"""insert into #$adjustmentTable (mml_id, #$adjustmentColumn) values ($mmlId, $adjustment)""".execute()
          }
      }
    }
  }

  def adjustTrafficDirection(id: Long, trafficDirection: TrafficDirection): Unit = {
    val unadjustedRoadLink: BasicRoadLink = Database.forDataSource(dataSource).withDynTransaction { getRoadLinkProperties(id) }
    val (mmlId, unadjustedTrafficDirection) = (unadjustedRoadLink._2, unadjustedRoadLink._7)
    addAdjustment("adjusted_traffic_direction", "traffic_direction", trafficDirection.value, unadjustedTrafficDirection.value, mmlId)
  }

  def adjustFunctionalClass(id: Long, functionalClass: Int): Unit = {
    val unadjustedRoadLink: BasicRoadLink = Database.forDataSource(dataSource).withDynTransaction { getRoadLinkProperties(id) }
    val (mmlId, unadjustedFunctionalClass) = (unadjustedRoadLink._2, unadjustedRoadLink._6)
    addAdjustment("adjusted_functional_class", "functional_class", functionalClass, unadjustedFunctionalClass, mmlId)
  }

  private def basicToAdjusted(basic: BasicRoadLink, modifiedAt: Option[DateTime]): RoadLink = {
    (basic._1, basic._2, basic._3, basic._4,
     basic._5, basic._6, basic._7, modifiedAt.map(DateTimePropertyFormat.print))
  }

  private def adjustedRoadLinks(basicRoadLinks: Seq[BasicRoadLink]): Seq[RoadLink] = {
    Database.forDataSource(OracleDatabase.ds).withDynTransaction {
      val adjustedTrafficDirections: Map[Long, Seq[(Long, Int, DateTime)]] = OracleArray.fetchAdjustedTrafficDirectionsByMMLId(basicRoadLinks.map(_._2), Queries.bonecpToInternalConnection(dynamicSession.conn)).groupBy(_._1)
      val adjustedFunctionalClasses: Map[Long, Seq[(Long, Int, DateTime)]] = OracleArray.fetchAdjustedFunctionalClassesByMMLId(basicRoadLinks.map(_._2), Queries.bonecpToInternalConnection(dynamicSession.conn)).groupBy(_._1)

      basicRoadLinks.map { basicRoadLink =>
        val mmlId = basicRoadLink._2
        val functionalClass = adjustedFunctionalClasses.get(mmlId).flatMap(_.headOption)
        val trafficDirection = adjustedTrafficDirections.get(mmlId).flatMap(_.headOption)
        val functionalClassValue = functionalClass.map(_._2).getOrElse(basicRoadLink._6)
        val trafficDirectionValue = trafficDirection.map( trafficDirection =>
          TrafficDirection(trafficDirection._2)
        ).getOrElse(basicRoadLink._7)

        val modifiedAt = (functionalClass.map(_._3), trafficDirection.map(_._3)) match {
          case (Some(fcModifiedAt), Some(tdModifiedAt)) => if (fcModifiedAt.isAfter(tdModifiedAt)) Some(fcModifiedAt) else Some(tdModifiedAt)
          case (Some(fcModifiedAt), None) => Some(fcModifiedAt)
          case (None, Some(tdModifiedAt)) => Some(tdModifiedAt)
          case (None, None) => None
        }

        basicToAdjusted(basicRoadLink.copy(_6 = functionalClassValue, _7 = trafficDirectionValue), modifiedAt)
      }
    }
  }

  def getRoadLink(id: Long): RoadLink = {
    val roadLink = Database.forDataSource(dataSource).withDynTransaction { getRoadLinkProperties(id) }
    adjustedRoadLinks(Seq(roadLink)).head
  }

  def getRoadLinks(bounds: BoundingRectangle, filterRoads: Boolean = true, municipalities: Set[Int] = Set()): Seq[RoadLink] = {
    val roadLinks = Database.forDataSource(dataSource).withDynTransaction {
      val leftBottomX = bounds.leftBottom.x
      val leftBottomY = bounds.leftBottom.y
      val rightTopX = bounds.rightTop.x
      val rightTopY = bounds.rightTop.y

      val roadFilter = if (filterRoads) "mod(functionalroadclass, 10) IN (1, 2, 3, 4, 5, 6) and" else ""
      val municipalityFilter = if (municipalities.nonEmpty) "kunta_nro in (" + municipalities.mkString(",") + ") and" else ""
      val query =
      s"""
            select dr1_id, mml_id, to_2d(shape), sdo_lrs.geom_segment_length(shape) as length, functionalroadclass as roadLinkType, mod(functionalroadclass, 10), liikennevirran_suunta
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
      Q.queryNA[BasicRoadLink](query).iterator().toSeq
    }
    adjustedRoadLinks(roadLinks)
  }
}
