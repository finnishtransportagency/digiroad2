package fi.liikennevirasto.digiroad2.linearasset.oracle

import _root_.oracle.spatial.geometry.JGeometry
import fi.liikennevirasto.digiroad2.LinkChain.GeometryDirection
import fi.liikennevirasto.digiroad2.linearasset.RoadLinkUncoveredBySpeedLimit
import fi.liikennevirasto.digiroad2.oracle.collections.OracleArray
import fi.liikennevirasto.digiroad2.{LinkChain, RoadLinkService, Point}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.asset.oracle.{OracleSpatialAssetDao, Queries}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase._
import fi.liikennevirasto.digiroad2.util.GeometryUtils
import org.joda.time.DateTime
import scala.slick.jdbc.{StaticQuery => Q, PositionedResult, GetResult, PositionedParameters, SetParameter}
import Q.interpolation
import scala.slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import Q.interpolation
import fi.liikennevirasto.digiroad2.asset.oracle.Queries._
import _root_.oracle.sql.STRUCT
import com.github.tototoshi.slick.MySQLJodaSupport._
import collection.JavaConversions._

object OracleLinearAssetDao {
  implicit object GetByteArray extends GetResult[Array[Byte]] {
    def apply(rs: PositionedResult) = rs.nextBytes()
  }

  implicit object SetStruct extends SetParameter[STRUCT] {
    def apply(v: STRUCT, pp: PositionedParameters) {
      pp.setObject(v, java.sql.Types.STRUCT)
    }
  }

  implicit val SetParameterFromLong: SetParameter[Seq[Long]] = new SetParameter[Seq[Long]] {
    def apply(seq: Seq[Long], p: PositionedParameters): Unit = {
      seq.foreach(p.setLong)
    }
  }

  def transformLink(link: (Long, Long, Int, Int, Array[Byte])) = {
    val (id, roadLinkId, sideCode, limit, pos) = link
    val points = JGeometry.load(pos).getOrdinatesArray.grouped(2)
    (id, roadLinkId, sideCode, limit, points.map { pointArray =>
      Point(pointArray(0), pointArray(1))}.toSeq)
  }

  def getSpeedLimitLinksWithLength(id: Long): Seq[(Long, Double, Seq[Point])] = {
    val speedLimitLinks = sql"""
      select pos.road_link_id, pos.start_measure, pos.end_measure
        from ASSET a
        join ASSET_LINK al on a.id = al.asset_id
        join LRM_POSITION pos on al.position_id = pos.id
        where a.asset_type_id = 20 and a.id = $id
        """.as[(Long, Double, Double)].list
    speedLimitLinks.map { case (roadLinkId, startMeasure, endMeasure) =>
      val points = RoadLinkService.getRoadLinkGeometry(roadLinkId, startMeasure, endMeasure)
      (roadLinkId, endMeasure - startMeasure, points)
    }
  }



  private def findPartiallyCoveredRoadLinks(roadLinkIds: Set[Long], roadLinks: Map[Long, (Seq[Point], Double, Int)], speedLimitLinks: Seq[(Long, Long, Int, Int, Double, Double)]): Seq[(Long, Int, Seq[(Double, Double)])] = {
    val speedLimitLinksByRoadLinkId: Map[Long, Seq[(Long, Long, Int, Int, Double, Double)]] = speedLimitLinks.groupBy(_._2)
    val partiallyCoveredLinks = roadLinkIds.map { roadLinkId =>
      val length = roadLinks(roadLinkId)._2
      val roadLinkType = roadLinks(roadLinkId)._3
      val lrmPositions: Seq[(Double, Double)] = speedLimitLinksByRoadLinkId(roadLinkId).map { case (_, _, _, _, startMeasure, endMeasure) => (startMeasure, endMeasure) }
      val remainders = lrmPositions.foldLeft(Seq((0.0, length)))(GeometryUtils.subtractIntervalFromIntervals).filter { case (start, end) => math.abs(end - start) > 0.01}
      (roadLinkId, roadLinkType, remainders)
    }
    partiallyCoveredLinks.filterNot(_._3.isEmpty).toSeq
  }

  def getSpeedLimitLinksByBoundingBox(bounds: BoundingRectangle): (Seq[(Long, Long, Int, Int, Seq[Point])], Map[Long, (Seq[Point], Double, Int)]) = {
    val linksWithGeometries = RoadLinkService.getRoadLinks(bounds)

    val assetLinks: Seq[(Long, Long, Int, Int, Double, Double)] = OracleArray.fetchAssetLinksByRoadLinkIds(linksWithGeometries.map(_._1), bonecpToInternalConnection(dynamicSession.conn))

    val linkGeometries: Map[Long, (Seq[Point], Double, Int)] =
      linksWithGeometries.foldLeft(Map.empty[Long, (Seq[Point], Double, Int)]) { (acc, linkWithGeometry) =>
        acc + (linkWithGeometry._1 -> (linkWithGeometry._2, linkWithGeometry._3, linkWithGeometry._4))
      }

    val speedLimits: Seq[(Long, Long, Int, Int, Seq[Point])] = assetLinks.map { link =>
      val (assetId, roadLinkId, sideCode, speedLimit, startMeasure, endMeasure) = link
      val geometry = GeometryUtils.truncateGeometry(linkGeometries(roadLinkId)._1, startMeasure, endMeasure)
      (assetId, roadLinkId, sideCode, speedLimit, geometry)
    }
    // FIXME: Remove filtering once speed limits fetched from DR1 production
    val filteredSpeedLimits = speedLimits.filterNot { speedLimit =>
      speedLimit._5.isEmpty
    }
    (filteredSpeedLimits, linkGeometries)
  }

  def getSpeedLimitLinksById(id: Long): Seq[(Long, Long, Int, Int, Seq[Point])] = {
    val speedLimits = sql"""
      select a.id, pos.road_link_id, pos.side_code, e.name_fi as speed_limit, pos.start_measure, pos.end_measure
        from ASSET a
        join ASSET_LINK al on a.id = al.asset_id
        join LRM_POSITION pos on al.position_id = pos.id
        join PROPERTY p on a.asset_type_id = p.asset_type_id and p.public_id = 'rajoitus'
        join SINGLE_CHOICE_VALUE s on s.asset_id = a.id and s.property_id = p.id
        join ENUMERATED_VALUE e on s.enumerated_value_id = e.id
        where a.asset_type_id = 20 and a.id = $id
        """.as[(Long, Long, Int, Int, Double, Double)].list
    speedLimits.map { case (assetId, roadLinkId, sideCode, value, startMeasure, endMeasure) =>
      val points = RoadLinkService.getRoadLinkGeometry(roadLinkId, startMeasure, endMeasure)
      (assetId, roadLinkId, sideCode, value, points)
    }
  }

  def getSpeedLimitDetails(id: Long): (Option[String], Option[DateTime], Option[String], Option[DateTime], Int, Seq[(Long, Long, Int, Int, Seq[Point])]) = {
    val (modifiedBy, modifiedDate, createdBy, createdDate, name) = sql"""
      select a.modified_by, a.modified_date, a.created_by, a.created_date, e.name_fi
      from ASSET a
      join PROPERTY p on a.asset_type_id = p.asset_type_id and p.public_id = 'rajoitus'
      join SINGLE_CHOICE_VALUE s on s.asset_id = a.id and s.property_id = p.id
      join ENUMERATED_VALUE e on s.enumerated_value_id = e.id
      where a.id = $id
    """.as[(Option[String], Option[DateTime], Option[String], Option[DateTime], Int)].first
    val speedLimitLinks = getSpeedLimitLinksById(id)
    (modifiedBy, modifiedDate, createdBy, createdDate, name, speedLimitLinks)
  }

  def getSpeedLimitLinkGeometryData(id: Long, roadLinkId: Long): (Double, Double, Int) = {
    sql"""
      select lrm.START_MEASURE, lrm.END_MEASURE, lrm.SIDE_CODE
        from asset a
        join asset_link al on a.ID = al.ASSET_ID
        join lrm_position lrm on lrm.id = al.POSITION_ID
        where a.id = $id and lrm.road_link_id = $roadLinkId
    """.as[(Double, Double, Int)].list.head
  }

  def createSpeedLimit(creator: String, roadLinkId: Long, linkMeasures: (Double, Double), sideCode: Int, value: Int): Long = {
    val assetId = OracleSpatialAssetDao.nextPrimaryKeySeqValue
    createSpeedLimit(creator, assetId, roadLinkId, linkMeasures, sideCode, value)
  }

  def createSpeedLimit(creator: String, speedLimitId: Long, roadLinkId: Long, linkMeasures: (Double, Double), sideCode: Int, value: Int): Long = {
    val lrmPositionId = OracleSpatialAssetDao.nextLrmPositionPrimaryKeySeqValue
    val (startMeasure, endMeasure) = linkMeasures
    val propertyId = Q.query[String, Long](Queries.propertyIdByPublicId).firstOption("rajoitus").get

    val insertAll =
      s"""
      INSERT ALL
        into asset(id, asset_type_id, created_by, created_date)
        values ($speedLimitId, 20, '$creator', sysdate)

        into lrm_position(id, start_measure, end_measure, road_link_id, side_code)
        values ($lrmPositionId, $startMeasure, $endMeasure, $roadLinkId, $sideCode)

        into asset_link(asset_id, position_id)
        values ($speedLimitId, $lrmPositionId)

        into single_choice_value(asset_id, enumerated_value_id, property_id, modified_date)
        values ($speedLimitId, (select id from enumerated_value where property_id = $propertyId and value = $value), $propertyId, current_timestamp)
      SELECT * FROM DUAL
      """
    Q.updateNA(insertAll).execute()

    speedLimitId
  }

  def moveLinksToSpeedLimit(sourceSpeedLimitId: Long, targetSpeedLimitId: Long, roadLinkIds: Seq[Long]) = {
    val roadLinks = roadLinkIds.map(_ => "?").mkString(",")
    val sql = s"""
      update ASSET_LINK
      set
        asset_id = $targetSpeedLimitId
      where asset_id = $sourceSpeedLimitId and position_id in (
        select al.position_id from asset_link al join lrm_position lrm on al.position_id = lrm.id where lrm.road_link_id in ($roadLinks))
    """
    Q.update[Seq[Long]](sql).list(roadLinkIds)
  }

  def updateLinkStartAndEndMeasures(speedLimitId: Long,
                                    roadLinkId: Long,
                                    linkMeasures: (Double, Double)): Unit = {
    val (startMeasure, endMeasure) = linkMeasures

    sqlu"""
      update LRM_POSITION
      set
        start_measure = $startMeasure,
        end_measure = $endMeasure
      where id = (
        select lrm.id
          from asset a
          join asset_link al on a.ID = al.ASSET_ID
          join lrm_position lrm on lrm.id = al.POSITION_ID
          where a.id = $speedLimitId and lrm.road_link_id = $roadLinkId)
    """.execute()
  }

  def splitSpeedLimit(id: Long, roadLinkId: Long, splitMeasure: Double, value: Int, username: String): Long = {
    Queries.updateAssetModified(id, username).execute()
    val (startMeasure, endMeasure, sideCode) = getSpeedLimitLinkGeometryData(id, roadLinkId)
    val links: Seq[(Long, Double, (Point, Point))] = getSpeedLimitLinksWithLength(id).map { link =>
      val (roadLinkId, length, geometry) = link
      (roadLinkId, length, GeometryUtils.geometryEndpoints(geometry))
    }
    val (existingLinkMeasures: (Double, Double), createdLinkMeasures: (Double, Double), linksToMove: Seq[(Long, Double, (Point, Point))]) = createSpeedLimitSplit(splitMeasure, (roadLinkId, startMeasure, endMeasure), links)

    updateLinkStartAndEndMeasures(id, roadLinkId, existingLinkMeasures)
    val createdId = createSpeedLimit(username, roadLinkId, createdLinkMeasures, sideCode, value)
    if (linksToMove.nonEmpty) moveLinksToSpeedLimit(id, createdId, linksToMove.map(_._1))
    createdId
  }

  def createSpeedLimitSplit(splitMeasure: Double, linkToBeSplit: (Long, Double, Double), links: Seq[(Long, Double, (Point, Point))]): ((Double, Double), (Double, Double), Seq[(Long, Double, (Point, Point))]) = {
    val (splitLinkId, startMeasureOfSplitLink, endMeasureOfSplitLink) = linkToBeSplit
    def linkEndPoints(link: (Long, Double, (Point, Point))) = {
      val (_, _, linkEndPoints) = link
      linkEndPoints
    }
    def linkLength(link: (Long, Double, (Point, Point))) = {
      val (_, length, _) = link
      length
    }

    val (linksBeforeSplit, splitLink, linksAfterSplit) = LinkChain(links, linkEndPoints).splitBy {case (linkId, _, _) => linkId == splitLinkId}

    val (firstSplitLength, secondSplitLength) = splitLink.geometryDirection match {
      case GeometryDirection.TowardsLinkChain =>
        (splitMeasure - startMeasureOfSplitLink + linksBeforeSplit.length(linkLength),
          endMeasureOfSplitLink - splitMeasure + linksAfterSplit.length(linkLength))
      case GeometryDirection.AgainstLinkChain =>
        (endMeasureOfSplitLink - splitMeasure + linksBeforeSplit.length(linkLength),
          splitMeasure - startMeasureOfSplitLink + linksAfterSplit.length(linkLength))
    }

    val (existingLinkMeasures, createdLinkMeasures, linksToMove) = (firstSplitLength > secondSplitLength, splitLink.geometryDirection) match {
      case (true, GeometryDirection.TowardsLinkChain) => ((startMeasureOfSplitLink, splitMeasure), (splitMeasure, endMeasureOfSplitLink), linksAfterSplit)
      case (true, GeometryDirection.AgainstLinkChain) => ((splitMeasure, endMeasureOfSplitLink), (startMeasureOfSplitLink, splitMeasure), linksAfterSplit)
      case (false, GeometryDirection.TowardsLinkChain) => ((splitMeasure, endMeasureOfSplitLink), (startMeasureOfSplitLink, splitMeasure), linksBeforeSplit)
      case (false, GeometryDirection.AgainstLinkChain) => ((startMeasureOfSplitLink, splitMeasure), (splitMeasure, endMeasureOfSplitLink), linksBeforeSplit)
    }
    (existingLinkMeasures, createdLinkMeasures, linksToMove.rawLinks())
  }

  def updateSpeedLimitValue(id: Long, value: Int, username: String): Option[Long] = {
    val propertyId = Q.query[String, Long](Queries.propertyIdByPublicId).firstOption("rajoitus").get
    val assetsUpdated = Queries.updateAssetModified(id, username).first
    val propertiesUpdated = Queries.updateSingleChoiceProperty(id, propertyId, value.toLong).first
    if (assetsUpdated == 1 && propertiesUpdated == 1) {
      Some(id)
    } else {
      dynamicSession.rollback()
      None
    }
  }

  private def findUncoveredLinkIds(roadLinks: Set[Long], speedLimitLinks: Seq[(Long, Long, Int, Int, Double, Double)]): Set[Long] = {
    roadLinks -- speedLimitLinks.map(_._2).toSet
  }

  private def findCoveredRoadLinks(roadLinks: Set[Long], speedLimitLinks: Seq[(Long, Long, Int, Int, Double, Double)]): Set[Long] = {
    roadLinks intersect speedLimitLinks.map(_._2).toSet
  }

  private val limitValueLookup: Map[Int, Int] = Map(1 -> 80, 2 -> 50, 3 -> 80)
  private def generateSpeedLimit(roadLinkId: Long, linkMeasures: (Double, Double), sideCode: Int, roadLinkType: Int): (Long, Long, Int, Int, Double, Double) = {
    val assetId = OracleSpatialAssetDao.nextPrimaryKeySeqValue
    val value = limitValueLookup(roadLinkType)
    (assetId, roadLinkId, sideCode, value, linkMeasures._1, linkMeasures._2)
  }

  private def createSpeedLimits(speedLimits: Seq[(Long, Long, Int, Int, Double, Double)]): Unit = {
    if (speedLimits.nonEmpty) {
      val propertyId = Q.query[String, Long](Queries.propertyIdByPublicId).firstOption("rajoitus").get

      println("creating " + speedLimits.size + " speed limits")

      val enumeratedValues = sql"select value, id from enumerated_value where property_id = $propertyId".as[(Int, Long)].list.toMap

      speedLimits.foreach { case (speedLimitId, roadLinkId, sideCode, value, startMeasure, endMeasure) =>
        val lrmPositionId = OracleSpatialAssetDao.nextLrmPositionPrimaryKeySeqValue
        val enumeratedValueId = enumeratedValues(value)
        val sb = new StringBuilder()
        sb.append("insert all")
        sb.append(
          s"""
            into asset(id, asset_type_id, created_by, created_date)
            values ($speedLimitId, 20, 'automatic_speed_limit_generation', sysdate)

            into lrm_position(id, start_measure, end_measure, road_link_id, side_code)
            values ($lrmPositionId, $startMeasure, $endMeasure, $roadLinkId, $sideCode)

            into asset_link(asset_id, position_id)
            values ($speedLimitId, $lrmPositionId)

            into single_choice_value(asset_id, enumerated_value_id, property_id, modified_date)
            values ($speedLimitId, $enumeratedValueId, $propertyId, current_timestamp)
          """)
        sb.append("\nSELECT * FROM DUAL\n")
        val sql = sb.toString()
        Q.updateNA(sql).execute()
      }
    }
  }

  private def timed[A](s: String, f: => A): A = {
    val start = System.currentTimeMillis()
    val retval = f
    println(s + " finished in: " + (System.currentTimeMillis - start))
    retval
  }

  def fillPartiallyFilledRoadLinks(linkGeometries: Map[Long, (Seq[Point], Double, Int)]): Unit = {
    val start = System.currentTimeMillis()
    val assetLinks: Seq[(Long, Long, Int, Int, Double, Double)] = timed("fetchAssetLinks", { OracleArray.fetchAssetLinksByRoadLinkIds(linkGeometries.keys.toSeq, bonecpToInternalConnection(dynamicSession.conn)) })

    val uncoveredLinkIds = timed("findUncoveredLinks", { findUncoveredLinkIds(linkGeometries.keySet, assetLinks) })
    val roadLinksUncoveredBySpeedLimits: Seq[(Long, Long, Int, Int, Double, Double)] = timed("roadLinksUncoveredBySpeedLimits", { uncoveredLinkIds.toSeq.map { roadLinkId =>
      val length = linkGeometries(roadLinkId)._2
      val roadLinkType = linkGeometries(roadLinkId)._3
      generateSpeedLimit(roadLinkId, (0.0, length), 1, roadLinkType)
    }
    })
    timed("createSpeedLimitsForUncoveredLinks", { createSpeedLimits(roadLinksUncoveredBySpeedLimits) })

    val coveredLinkIds = timed("findCoveredLinks", { findCoveredRoadLinks(linkGeometries.keySet, assetLinks) })
    val partiallyCoveredLinks = timed("findPartiallyCoveredLinks", { findPartiallyCoveredRoadLinks(coveredLinkIds, linkGeometries, assetLinks) })
    val generatedPartialLinkSpeedLimits = timed("generatedPartialLink", { partiallyCoveredLinks.flatMap { partiallyCoveredLink =>
      val (roadLinkId, roadLinkType, unfilledSegments) = partiallyCoveredLink
      unfilledSegments.map { segment =>
        generateSpeedLimit(roadLinkId, segment, 1, roadLinkType)
      }
    }
    })
    timed("createSpeedLimitsForPartialLinks", { createSpeedLimits(generatedPartialLinkSpeedLimits) })
  }
}
