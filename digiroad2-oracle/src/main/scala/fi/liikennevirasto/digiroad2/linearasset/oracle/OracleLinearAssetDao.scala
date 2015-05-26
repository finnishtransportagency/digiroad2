package fi.liikennevirasto.digiroad2.linearasset.oracle

import _root_.oracle.spatial.geometry.JGeometry
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.asset.oracle.{Queries, Sequences}
import fi.liikennevirasto.digiroad2.linearasset.{GeneratedSpeedLimitLink, RoadLinkForSpeedLimit}
import fi.liikennevirasto.digiroad2.oracle.collections.OracleArray
import org.joda.time.DateTime

import scala.slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import _root_.oracle.sql.STRUCT
import com.github.tototoshi.slick.MySQLJodaSupport._
import fi.liikennevirasto.digiroad2.asset.oracle.Queries._
import org.slf4j.LoggerFactory
import scala.collection.JavaConversions._
import scala.slick.jdbc.StaticQuery.interpolation
import scala.slick.jdbc.{GetResult, PositionedParameters, PositionedResult, SetParameter, StaticQuery => Q}

object OracleLinearAssetDao {
  val logger = LoggerFactory.getLogger(getClass)

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
    val (id, roadLinkId, sideCode, value, pos) = link
    val points = JGeometry.load(pos).getOrdinatesArray.grouped(2)
    (id, roadLinkId, sideCode, value, points.map { pointArray =>
      Point(pointArray(0), pointArray(1))}.toSeq)
  }

  def getLinksWithLength(assetTypeId: Int, id: Long): Seq[(Long, Double, Seq[Point])] = {
    val links = sql"""
      select pos.road_link_id, pos.start_measure, pos.end_measure
        from ASSET a
        join ASSET_LINK al on a.id = al.asset_id
        join LRM_POSITION pos on al.position_id = pos.id
        where a.asset_type_id = $assetTypeId and a.id = $id
        """.as[(Long, Double, Double)].list
    links.map { case (roadLinkId, startMeasure, endMeasure) =>
      val points = RoadLinkService.getRoadLinkGeometry(roadLinkId, startMeasure, endMeasure)
      (roadLinkId, endMeasure - startMeasure, points)
    }
  }



  private def findPartiallyCoveredRoadLinks(roadLinkIds: Set[Long], roadLinks: Map[Long, RoadLinkForSpeedLimit], speedLimitLinks: Seq[(Long, Long, Int, Option[Int], Double, Double)]): Seq[(Long, AdministrativeClass, Seq[(Double, Double)])] = {
    val speedLimitLinksByRoadLinkId: Map[Long, Seq[(Long, Long, Int, Option[Int], Double, Double)]] = speedLimitLinks.groupBy(_._2)
    val partiallyCoveredLinks = roadLinkIds.map { roadLinkId =>
      val length = roadLinks(roadLinkId).length
      val administrativeClass = roadLinks(roadLinkId).administrativeClass
      val lrmPositions: Seq[(Double, Double)] = speedLimitLinksByRoadLinkId(roadLinkId).map { case (_, _, _, _, startMeasure, endMeasure) => (startMeasure, endMeasure) }
      val remainders = lrmPositions.foldLeft(Seq((0.0, length)))(GeometryUtils.subtractIntervalFromIntervals).filter { case (start, end) => math.abs(end - start) > 0.01}
      (roadLinkId, administrativeClass, remainders)
    }
    partiallyCoveredLinks.filterNot(_._3.isEmpty).toSeq
  }

  def getSpeedLimitLinksByBoundingBox(bounds: BoundingRectangle, municipalities: Set[Int]): (Seq[(Long, Long, Int, Option[Int], Seq[Point])], Map[Long, RoadLinkForSpeedLimit]) = {
    val linksWithGeometries = RoadLinkService.getRoadLinks(bounds = bounds, municipalities = municipalities)

    val assetLinks: Seq[(Long, Long, Int, Option[Int], Double, Double)] = OracleArray
      .fetchSpeedLimitsByRoadLinkIds(linksWithGeometries.map(_.id), bonecpToInternalConnection(dynamicSession.conn))
      .map { case (id, roadLinkId, _, sideCode, limitValue, startMeasure, endMeasure) => (id, roadLinkId, sideCode, limitValue, startMeasure, endMeasure) }

    val linkGeometries: Map[Long, (Seq[Point], Double, AdministrativeClass, Int, Long)] =
      linksWithGeometries.foldLeft(Map.empty[Long, (Seq[Point], Double, AdministrativeClass, Int, Long)]) { (acc, linkWithGeometry) =>
        acc + (linkWithGeometry.id -> (linkWithGeometry.geometry, linkWithGeometry.length, linkWithGeometry.administrativeClass, linkWithGeometry.functionalClass, linkWithGeometry.mmlId))
      }

    val speedLimits: Seq[(Long, Long, Int, Option[Int], Seq[Point])] = assetLinks.map { link =>
      val (assetId, roadLinkId, sideCode, speedLimit, startMeasure, endMeasure) = link
      val geometry = GeometryUtils.truncateGeometry(linkGeometries(roadLinkId)._1, startMeasure, endMeasure)
      (assetId, roadLinkId, sideCode, speedLimit, geometry)
    }
    // FIXME: Remove filtering once speed limits fetched from DR1 production
    val filteredSpeedLimits = speedLimits.filterNot { speedLimit =>
      speedLimit._5.isEmpty
    }

    val linksOnRoads = linkGeometries.filter { link =>
      val (_, _, _, functionalClass, _) = link._2
      Set(1, 2, 3, 4, 5, 6).contains(functionalClass % 10)
    }.map { link =>
      val (geometry, length, roadLinkType, _, mmlId) = link._2
      link._1 -> RoadLinkForSpeedLimit(geometry, length, roadLinkType, mmlId)
    }

    (filteredSpeedLimits, linksOnRoads)
  }

  def getByMunicipality(municipality: Int): Seq[Map[String, Any]] = {
    val linksWithGeometries = RoadLinkService.getByMunicipality(municipality)

    val assetLinks: Seq[(Long, Long, Long, Int, Option[Int], Double, Double)] = OracleArray.fetchSpeedLimitsByRoadLinkIds(linksWithGeometries.map(_._1), bonecpToInternalConnection(dynamicSession.conn))

    val linkGeometries: Map[Long, Seq[Point]] =
      linksWithGeometries.foldLeft(Map.empty[Long, Seq[Point]]) { (acc, linkWithGeometry) =>
        acc + (linkWithGeometry._1 -> linkWithGeometry._2)
      }

    val speedLimits: Seq[Map[String, Any]] = assetLinks.map { link =>
      val (assetId, roadLinkId, mmlId, sideCode, speedLimit, startMeasure, endMeasure) = link
      val geometry = GeometryUtils.truncateGeometry(linkGeometries(roadLinkId), startMeasure, endMeasure)
      Map ("id" -> (assetId + "-" + mmlId),
        "sideCode" -> sideCode,
        "points" -> geometry,
        "value" -> speedLimit,
        "startMeasure" -> startMeasure,
        "endMeasure" -> endMeasure,
        "mmlId" -> mmlId)
    }

    speedLimits
  }

  def getSpeedLimitLinksById(id: Long): Seq[(Long, Long, Int, Option[Int], Seq[Point])] = {
    val speedLimits = sql"""
      select a.id, pos.road_link_id, pos.side_code, e.value, pos.start_measure, pos.end_measure
        from ASSET a
        join ASSET_LINK al on a.id = al.asset_id
        join LRM_POSITION pos on al.position_id = pos.id
        join PROPERTY p on a.asset_type_id = p.asset_type_id and p.public_id = 'rajoitus'
        join SINGLE_CHOICE_VALUE s on s.asset_id = a.id and s.property_id = p.id
        join ENUMERATED_VALUE e on s.enumerated_value_id = e.id
        where a.asset_type_id = 20 and a.id = $id
        """.as[(Long, Long, Int, Option[Int], Double, Double)].list
    speedLimits.map { case (assetId, roadLinkId, sideCode, value, startMeasure, endMeasure) =>
      val points = RoadLinkService.getRoadLinkGeometry(roadLinkId, startMeasure, endMeasure)
      (assetId, roadLinkId, sideCode, value, points)
    }
  }

  def getSpeedLimitDetails(id: Long): (Option[String], Option[DateTime], Option[String], Option[DateTime], Option[Int], Seq[(Long, Long, Int, Option[Int], Seq[Point])]) = {
    val (modifiedBy, modifiedDate, createdBy, createdDate, value) = sql"""
      select a.modified_by, a.modified_date, a.created_by, a.created_date, e.value
      from ASSET a
      join PROPERTY p on a.asset_type_id = p.asset_type_id and p.public_id = 'rajoitus'
      join SINGLE_CHOICE_VALUE s on s.asset_id = a.id and s.property_id = p.id
      join ENUMERATED_VALUE e on s.enumerated_value_id = e.id
      where a.id = $id
    """.as[(Option[String], Option[DateTime], Option[String], Option[DateTime], Option[Int])].first
    val speedLimitLinks = getSpeedLimitLinksById(id)
    (modifiedBy, modifiedDate, createdBy, createdDate, value, speedLimitLinks)
  }

  def getLinkGeometryData(id: Long, roadLinkId: Long): (Double, Double, Int) = {
    sql"""
      select lrm.START_MEASURE, lrm.END_MEASURE, lrm.SIDE_CODE
        from asset a
        join asset_link al on a.ID = al.ASSET_ID
        join lrm_position lrm on lrm.id = al.POSITION_ID
        where a.id = $id and lrm.road_link_id = $roadLinkId
    """.as[(Double, Double, Int)].list.head
  }

  def createSpeedLimit(creator: String, roadLinkId: Long, linkMeasures: (Double, Double), sideCode: Int, value: Int): Long = {
    val assetId = Sequences.nextPrimaryKeySeqValue
    createSpeedLimit(creator, assetId, roadLinkId, linkMeasures, sideCode, value)
  }

  def createSpeedLimit(creator: String, speedLimitId: Long, roadLinkId: Long, linkMeasures: (Double, Double), sideCode: Int, value: Int): Long = {
    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
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

  def moveLinks(sourceId: Long, targetId: Long, roadLinkIds: Seq[Long]): List[Int] = {
    val roadLinks = roadLinkIds.map(_ => "?").mkString(",")
    val sql = s"""
      update ASSET_LINK
      set
        asset_id = $targetId
      where asset_id = $sourceId and position_id in (
        select al.position_id from asset_link al join lrm_position lrm on al.position_id = lrm.id where lrm.road_link_id in ($roadLinks))
    """
    Q.update[Seq[Long]](sql).list(roadLinkIds)
  }

  def updateLinkStartAndEndMeasures(id: Long,
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
          where a.id = $id and lrm.road_link_id = $roadLinkId)
    """.execute()
  }

  def splitSpeedLimit(id: Long, roadLinkId: Long, splitMeasure: Double, value: Int, username: String): Long = {
    Queries.updateAssetModified(id, username).execute()
    val (startMeasure, endMeasure, sideCode) = getLinkGeometryData(id, roadLinkId)
    val links: Seq[(Long, Double, (Point, Point))] = getLinksWithLength(20, id).map { link =>
      val (roadLinkId, length, geometry) = link
      (roadLinkId, length, GeometryUtils.geometryEndpoints(geometry))
    }
    val (existingLinkMeasures, createdLinkMeasures, linksToMove) = GeometryUtils.createSplit(splitMeasure, (roadLinkId, startMeasure, endMeasure), links)

    updateLinkStartAndEndMeasures(id, roadLinkId, existingLinkMeasures)
    val createdId = createSpeedLimit(username, roadLinkId, createdLinkMeasures, sideCode, value)
    if (linksToMove.nonEmpty) moveLinks(id, createdId, linksToMove.map(_._1))
    createdId
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

  private def findUncoveredLinkIds(roadLinks: Set[Long], speedLimitLinks: Seq[(Long, Long, Int, Option[Int], Double, Double)]): Set[Long] = {
    roadLinks -- speedLimitLinks.map(_._2).toSet
  }

  private def findCoveredRoadLinks(roadLinks: Set[Long], speedLimitLinks: Seq[(Long, Long, Int, Option[Int], Double, Double)]): Set[Long] = {
    roadLinks intersect speedLimitLinks.map(_._2).toSet
  }

  private def generateSpeedLimit(roadLinkId: Long, linkMeasures: (Double, Double), sideCode: Int, roadLinkType: AdministrativeClass, mmlId: Long): GeneratedSpeedLimitLink = {
    val assetId = Sequences.nextPrimaryKeySeqValue
    GeneratedSpeedLimitLink(id = assetId, mmlId = mmlId, roadLinkId = roadLinkId, sideCode = sideCode, startMeasure = linkMeasures._1, endMeasure = linkMeasures._2)
  }

  private def createSpeedLimits(speedLimits: Seq[GeneratedSpeedLimitLink]): Unit = {
    if (speedLimits.nonEmpty) {
      val propertyId = Q.query[String, Long](Queries.propertyIdByPublicId).firstOption("rajoitus").get

      logger.info("creating " + speedLimits.size + " speed limits")

      val enumeratedValueId = sql"select id from enumerated_value where property_id = $propertyId and value is null".as[Long].first()

      speedLimits.foreach { speedLimit =>
        val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
        val sb = new StringBuilder()
        sb.append("insert all")
        sb.append(
          s"""
            into asset(id, asset_type_id, created_by, created_date)
            values (${speedLimit.id}, 20, 'automatic_speed_limit_generation', sysdate)

            into lrm_position(id, start_measure, end_measure, road_link_id, side_code, mml_id)
            values ($lrmPositionId, ${speedLimit.startMeasure}, ${speedLimit.endMeasure}, ${speedLimit.roadLinkId},
                    ${speedLimit.sideCode}, ${speedLimit.mmlId})

            into asset_link(asset_id, position_id)
            values (${speedLimit.id}, $lrmPositionId)

            into single_choice_value(asset_id, enumerated_value_id, property_id, modified_date)
            values (${speedLimit.id}, $enumeratedValueId, $propertyId, current_timestamp)
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
    logger.info(s + " finished in: " + (System.currentTimeMillis - start) + "ms")
    retval
  }

  def fillPartiallyFilledRoadLinks(linkGeometries: Map[Long, RoadLinkForSpeedLimit]): Unit = {
    val assetLinks: Seq[(Long, Long, Int, Option[Int], Double, Double)] = timed("fetchAssetLinks", { OracleArray.
      fetchSpeedLimitsByRoadLinkIds(linkGeometries.keys.toSeq, bonecpToInternalConnection(dynamicSession.conn))
      .map { case (id, roadLinkId, _, sideCode, limitValue, startMeasure, endMeasure) => (id, roadLinkId, sideCode, limitValue, startMeasure, endMeasure) }
    })

    val uncoveredLinkIds = timed("findUncoveredLinks", { findUncoveredLinkIds(linkGeometries.keySet, assetLinks) })
    val generatedSingleLinkSpeedLimits: Seq[GeneratedSpeedLimitLink] = timed("generatedSingleLinkSpeedLimits", { uncoveredLinkIds.toSeq.map { roadLinkId =>
      val link = linkGeometries(roadLinkId)
      generateSpeedLimit(roadLinkId, (0.0, link.length), 1, link.administrativeClass, link.mmlId)
    }
    })
    timed("createSpeedLimitsForUncoveredLinks", { createSpeedLimits(generatedSingleLinkSpeedLimits) })

    val coveredLinkIds = timed("findCoveredLinks", { findCoveredRoadLinks(linkGeometries.keySet, assetLinks) })
    val partiallyCoveredLinks = timed("findPartiallyCoveredLinks", { findPartiallyCoveredRoadLinks(coveredLinkIds, linkGeometries, assetLinks) })
    val generatedPartialLinkSpeedLimits = timed("generatedPartialLink", { partiallyCoveredLinks.flatMap { partiallyCoveredLink =>
      val (roadLinkId, roadLinkType, unfilledSegments) = partiallyCoveredLink
      unfilledSegments.map { segment =>
        generateSpeedLimit(roadLinkId, segment, 1, roadLinkType, linkGeometries(roadLinkId).mmlId)
      }
    }
    })
    timed("createSpeedLimitsForPartialLinks", { createSpeedLimits(generatedPartialLinkSpeedLimits) })
  }
}
