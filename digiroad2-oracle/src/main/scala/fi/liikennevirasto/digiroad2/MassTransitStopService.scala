package fi.liikennevirasto.digiroad2

import _root_.oracle.spatial.geometry.JGeometry
import fi.liikennevirasto.digiroad2.asset.{ValidityPeriod, BoundingRectangle}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.user.User
import org.joda.time.{Interval, DateTime, LocalDate}
import scala.slick.driver.JdbcDriver.backend.Database
import scala.slick.driver.JdbcDriver.backend.Database.dynamicSession
import scala.slick.jdbc.{SQLInterpolationResult, PositionedResult, GetResult}
import scala.slick.jdbc.StaticQuery.interpolation
import fi.liikennevirasto.digiroad2.asset.oracle.Queries._

case class MassTransitStop(id: Long, nationalId: Long, lon: Double, lat: Double, bearing: Option[Int],
                           validityDirection: Int, municipalityNumber: Int,
                           validityPeriod: String, floating: Boolean, stopType: Seq[Int])

trait MassTransitStopService {
  def withDynSession[T](f: => T): T

  def getByBoundingBox(user: User, bounds: BoundingRectangle, roadLinkService: RoadLinkService): Seq[MassTransitStop] = {
    case class MassTransitStopBeforeUpdate(stop: MassTransitStop, persistedFloating: Boolean)
    type MassTransitStopAndType = (Long, Long, Option[Int], Int, Int, Double, Long, Point, Option[LocalDate], Option[LocalDate], Boolean, Int)
    type MassTransitStopWithTypes = (Long, Long, Option[Int], Int, Int, Double, Long, Point, Option[LocalDate], Option[LocalDate], Boolean, Seq[Int])

    val roadLinks = roadLinkService.fetchVVHRoadlinks(bounds)
    withDynSession {
      val boundingBoxFilter = OracleDatabase.boundingBoxFilter(bounds, "a.geometry")

      val massTransitStopsAndStopTypes = sql"""
          select a.id, a.external_id, a.bearing, lrm.side_code,
          a.municipality_code, lrm.start_measure, lrm.mml_id,
          a.geometry, a.valid_from, a.valid_to, a.floating, e.value
          from asset a
          join asset_link al on a.id = al.asset_id
          join lrm_position lrm on al.position_id = lrm.id
          left join multiple_choice_value v on a.id = v.asset_id
          left join enumerated_value e on v.enumerated_value_id = e.id
          where a.asset_type_id = 10
          and #$boundingBoxFilter
          and v.property_id = (select id from property where public_id = 'pysakin_tyyppi')
       """.as[MassTransitStopAndType].list()

      val massTransitStops: Seq[MassTransitStopWithTypes] = massTransitStopsAndStopTypes.groupBy(_._1).map { case(id, rows) =>
        val stopTypes = rows.map(_._12)
        val (_, nationalId, bearing, sideCode, municipalityCode, startMeasure, mmlId, geometry, validFrom, validTo, floating, _) = rows.head
        id -> (id, nationalId, bearing, sideCode, municipalityCode, startMeasure, mmlId, geometry, validFrom, validTo, floating, stopTypes)
      }.values.toSeq

      val stopsBeforeUpdate = massTransitStops.filter { massTransitStop =>
        val (_, _, _, _, municipalityCode, _, _, _, _, _, _, _) = massTransitStop
        user.isAuthorizedToRead(municipalityCode)
      }.map { massTransitStop =>
        val (id, nationalId, bearing, sideCode, municipalityCode, measure, mmlId, point, validFrom, validTo, persistedFloating, stopTypes) = massTransitStop
        val roadLinkForStop: Option[(Long, Int, Seq[Point])] = roadLinks.find(_._1 == mmlId)
        val floating = roadLinkForStop match {
          case None => true
          case Some(roadLink) => roadLink._2 != municipalityCode || !coordinatesWithinThreshold(Some(point), calculateLinearReferencePoint(roadLink._3, measure))
        }
        MassTransitStopBeforeUpdate(MassTransitStop(id, nationalId, point.x, point.y, bearing, sideCode, municipalityCode, validityPeriod(validFrom, validTo), floating, stopTypes), persistedFloating)
      }

      stopsBeforeUpdate.foreach { stop =>
        if (stop.stop.floating != stop.persistedFloating) {
          sqlu"""update asset set floating = ${stop.stop.floating} where id = ${stop.stop.id}""".execute()
        }
      }

      stopsBeforeUpdate.map(_.stop)
    }
  }

  def calculateLinearReferencePoint(geometry: Seq[Point], measure: Double): Option[Point] = {
    case class AlgorithmState(previousPoint: Point, remainingMeasure: Double, result: Option[Point])
    if (geometry.size < 2 || measure < 0) { None }
    else {
      val state = geometry.tail.foldLeft(AlgorithmState(geometry.head, measure, None)) { (acc, point) =>
        if (acc.result.isDefined) {
          acc
        } else {
          val distance = point.distanceTo(acc.previousPoint)
          if (acc.remainingMeasure <= distance) {
            val directionVector = (point - acc.previousPoint).normalize()
            val result = Some(acc.previousPoint + directionVector.scale(acc.remainingMeasure))
            AlgorithmState(point, acc.remainingMeasure - distance, result)
          } else {
            AlgorithmState(point, acc.remainingMeasure - distance, None)
          }
        }
      }
      state.result
    }
  }

  private implicit val getLocalDate = new GetResult[Option[LocalDate]] {
    def apply(r: PositionedResult) = {
      r.nextDateOption().map(new LocalDate(_))
    }
  }

  private def validityPeriod(validFrom: Option[LocalDate], validTo: Option[LocalDate]): String = {
    (validFrom, validTo) match {
      case (Some(from), None) => if (from.isBefore(LocalDate.now())) { ValidityPeriod.Current } else { ValidityPeriod.Future }
      case (None, Some(to)) => if (to.isBefore(LocalDate.now())) { ValidityPeriod.Past } else { ValidityPeriod.Current }
      case (Some(from), Some(to)) =>
        val interval = new Interval(from.toDateMidnight, to.toDateMidnight)
        if (interval.containsNow()) { ValidityPeriod.Current }
        else if (interval.isBeforeNow) { ValidityPeriod.Past }
        else { ValidityPeriod.Future }
      case _ => ValidityPeriod.Current
    }
  }

  private val FLOAT_THRESHOLD_IN_METERS = 3

  private def coordinatesWithinThreshold(pt1: Option[Point], pt2: Option[Point]): Boolean = {
    (pt1, pt2) match {
      case (Some(point1), Some(point2)) => point1.distanceTo(point2) <= FLOAT_THRESHOLD_IN_METERS
      case _ => false
    }
  }

}

object MassTransitStopService extends MassTransitStopService {
  def withDynSession[T](f: => T): T = Database.forDataSource(OracleDatabase.ds).withDynSession(f)
}
