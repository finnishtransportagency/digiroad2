package fi.liikennevirasto.digiroad2.util

import java.sql.PreparedStatement

import fi.liikennevirasto.digiroad2.oracle.OracleDatabase._
import scala.slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.asset.oracle.Queries.nextPrimaryKeyId
import scala.slick.jdbc.{StaticQuery => Q, _}
import Q.interpolation

object SpeedLimitGenerator {
  private def subtractInterval(remainingRoadLinks: List[(Double, Double)], span: (Double, Double)): List[(Double, Double)] = {
    val (spanStart, spanEnd) = (math.min(span._1, span._2), math.max(span._1, span._2))
    def liesInBetween(measure: Double, interval: (Double, Double)): Boolean = {
      measure >= interval._1 && measure <= interval._2
    }
    remainingRoadLinks.flatMap {
      case (start, end) if !liesInBetween(spanStart, (start, end)) && liesInBetween(spanEnd, (start, end)) => List((spanEnd, end))
      case (start, end) if !liesInBetween(spanEnd, (start, end)) && liesInBetween(spanStart, (start, end)) => List((start, spanStart))
      case (start, end) if !liesInBetween(spanStart, (start, end)) && !liesInBetween(spanEnd, (start, end)) => List()
      case (start, end) if liesInBetween(spanStart, (start, end)) && liesInBetween(spanEnd, (start, end)) => List((start, spanStart), (spanEnd, end))
      case x => List(x)
    }
  }

  private def findPartiallyCoveredRoadLinks(municipality: Option[Int]): Iterator[(Long, List[(Double, Double)])] = {
    val query = """
      select id, SDO_LRS.GEOM_SEGMENT_LENGTH(geom) from road_link
      where id in (
        select lp.road_link_id from lrm_position lp
        join asset_link al on al.position_id = lp.id
      )
      and mod(functional_class, 10) IN (1, 2, 3, 4, 5, 6)
    """
    val roadLinks = Q.queryNA[(Long, Double)](
      municipality match {
        case Some(m) => query + " and road_link.MUNICIPALITY_NUMBER = " + m.toString
        case _ => query
      }
    ).iterator()

    val partiallyCoveredLinks = roadLinks.map { case (id, length) =>
      val lrmPositions = sql"""select start_measure, end_measure from lrm_position join asset_link on asset_link.POSITION_ID = lrm_position.id join asset on asset_link.ASSET_ID = asset.id where lrm_position.road_link_id = $id""".as[(Double, Double)].list
      val remainders = lrmPositions.foldLeft(List((0.0, length)))(subtractInterval).filter { case (start, end) => math.abs(end - start) > 0.01 }
      (id, remainders)
    }

    partiallyCoveredLinks.filterNot(_._2.isEmpty)
  }

  private def withSpeedLimitInsertions(callback: (PreparedStatement, PreparedStatement, PreparedStatement, PreparedStatement) => Unit): Unit = {
    val assetPS = dynamicSession.prepareStatement("insert into asset (id, asset_type_id, CREATED_DATE, CREATED_BY) values (?, 20, SYSDATE, 'automatic_speed_limit_generation')")
    val lrmPositionPS = dynamicSession.prepareStatement("insert into lrm_position (ID, ROAD_LINK_ID, START_MEASURE, END_MEASURE, SIDE_CODE) values (?, ?, ?, ?, ?)")
    val assetLinkPS = dynamicSession.prepareStatement("insert into asset_link (asset_id, position_id) values (?, ?)")
    val speedLimitPS = dynamicSession.prepareStatement("insert into single_choice_value(asset_id, enumerated_value_id, property_id, modified_date, modified_by) values (?, (select id from enumerated_value where value = ? and property_id = (select id from property where public_id = 'rajoitus')), (select id from property where public_id = 'rajoitus'), sysdate, 'automatic_speed_limit_generation')")

    callback(assetPS, lrmPositionPS, assetLinkPS, speedLimitPS)

    println("Writing new speed limits to database")
    assetPS.executeBatch()
    lrmPositionPS.executeBatch()
    assetLinkPS.executeBatch()
    speedLimitPS.executeBatch()
    assetPS.close()
    lrmPositionPS.close()
    assetLinkPS.close()
    speedLimitPS.close()
  }

  def fillPartiallyFilledRoads(municipality: Option[Int]) = {
    municipality match {
      case Some(m) => println("Filling partially filled speed limits on municipality: " + m.toString)
      case _ => println("Filling partially filled speed limits")
    }
    Database.forDataSource(ds).withDynSession {
      var roadLinksProcessed = 0l

      withSpeedLimitInsertions { case (assetPS, lrmPositionPS, assetLinkPS, speedLimitPS) =>
        println("Finding partially covered road links")
        val partiallyCoveredRoadLinks = findPartiallyCoveredRoadLinks(municipality)
        println("Partially covered road links found")
        partiallyCoveredRoadLinks.foreach { case (roadLinkId, emptySegments) =>
          emptySegments.foreach { case (start, end) =>
            generateNewSpeedLimit(roadLinkId, start, end, 50, assetPS, lrmPositionPS, assetLinkPS, speedLimitPS)
          }
          roadLinksProcessed = roadLinksProcessed + 1
          println("New speed limits generated to " + roadLinksProcessed + " partially covered road links")
        }
      }

      println("Partially covered road links filled with speed limits")
    }
  }

  private def generateNewSpeedLimit(roadLinkId: Long,
                                    startMeasure: Double,
                                    endMeasure: Double,
                                    speedLimitValue: Int,
                                    assetStatement: PreparedStatement,
                                    lrmPositionStatement: PreparedStatement,
                                    assetLinkStatement: PreparedStatement,
                                    speedLimitStatement: PreparedStatement): Unit = {
    val assetId = nextPrimaryKeyId.as[Long].first
    val lrmPositionId = nextPrimaryKeyId.as[Long].first
    assetStatement.setLong(1, assetId)
    assetStatement.addBatch()

    lrmPositionStatement.setLong(1, lrmPositionId)
    lrmPositionStatement.setLong(2, roadLinkId)
    lrmPositionStatement.setDouble(3, startMeasure)
    lrmPositionStatement.setDouble(4, endMeasure)
    lrmPositionStatement.setInt(5, 1)
    lrmPositionStatement.addBatch()

    assetLinkStatement.setLong(1, assetId)
    assetLinkStatement.setLong(2, lrmPositionId)
    assetLinkStatement.addBatch()

    speedLimitStatement.setLong(1, assetId)
    speedLimitStatement.setInt(2, speedLimitValue)
    speedLimitStatement.addBatch()
  }

  private def generateSpeedLimitsForEmptyLinks(speedLimitValue: Int, functionalClasses: List[Int], municipalities: Seq[Int]): Unit = {
    println("Running speed limit generation...")
    var handledCount = 0l
    Database.forDataSource(ds).withDynSession {
      val baseQuery = """
        select id, SDO_LRS.GEOM_SEGMENT_LENGTH(rl.geom) as length FROM road_link rl
        where id not in (
          select lp.road_link_id from lrm_position lp
          join asset_link al on al.position_id = lp.id
        )
        and floor(functional_class / 10) in (""" + functionalClasses.mkString(", ") + ")"
      val roadLinks = Q.queryNA[(Long, Double)](
        if (municipalities.isEmpty) baseQuery
        else baseQuery + " and rl.MUNICIPALITY_NUMBER in (" + municipalities.mkString(", ") + ")").iterator()
      println("got road link iterator")

      val assetPS = dynamicSession.prepareStatement("insert into asset (id, asset_type_id, CREATED_DATE, CREATED_BY) values (?, 20, SYSDATE, 'automatic_speed_limit_generation')")
      val lrmPositionPS = dynamicSession.prepareStatement("insert into lrm_position (ID, ROAD_LINK_ID, START_MEASURE, END_MEASURE, SIDE_CODE) values (?, ?, ?, ?, ?)")
      val assetLinkPS = dynamicSession.prepareStatement("insert into asset_link (asset_id, position_id) values (?, ?)")
      val speedLimitPS = dynamicSession.prepareStatement("insert into single_choice_value(asset_id, enumerated_value_id, property_id, modified_date, modified_by) values (?, (select id from enumerated_value where value = ? and property_id = (select id from property where public_id = 'rajoitus')), (select id from property where public_id = 'rajoitus'), sysdate, 'automatic_speed_limit_generation')")

      roadLinks.foreach { case (roadLinkId, length) =>
        generateNewSpeedLimit(roadLinkId, 0.0, length, speedLimitValue, assetPS, lrmPositionPS, assetLinkPS, speedLimitPS)
        handledCount = handledCount + 1
        if (handledCount % 1000 == 0) {
          println("generated " + handledCount + " speed limits")
        }
      }

      assetPS.executeBatch()
      lrmPositionPS.executeBatch()
      assetLinkPS.executeBatch()
      speedLimitPS.executeBatch()
      assetPS.close()
      lrmPositionPS.close()
      assetLinkPS.close()
      speedLimitPS.close()
    }
    println("...done (generated " + handledCount + " speed limits).")
  }

  def generateForHighways(municipalities: Int*): Unit = {
    generateSpeedLimitsForEmptyLinks(80, List(1), municipalities)
  }

  def generateForCityAreas(municipalities: Int*): Unit = {
    generateSpeedLimitsForEmptyLinks(50, List(2, 3), municipalities)
  }
}
