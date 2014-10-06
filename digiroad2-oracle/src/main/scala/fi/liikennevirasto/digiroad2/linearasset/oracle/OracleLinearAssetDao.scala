package fi.liikennevirasto.digiroad2.linearasset.oracle

import _root_.oracle.spatial.geometry.JGeometry
import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.asset.oracle.{OracleSpatialAssetDao, Queries}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase._
import fi.liikennevirasto.digiroad2.util.{SpeedLimitLinkPositions, GeometryUtils}
import org.joda.time.DateTime
import scala.slick.jdbc.{StaticQuery => Q, PositionedResult, GetResult, PositionedParameters, SetParameter}
import Q.interpolation
import scala.slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import Q.interpolation
import fi.liikennevirasto.digiroad2.asset.oracle.Queries._
import _root_.oracle.sql.STRUCT
import com.github.tototoshi.slick.MySQLJodaSupport._
import fi.liikennevirasto.digiroad2.RoadLinkService

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
      (pointArray(0), pointArray(1))
    }.toSeq)
  }

  def getSpeedLimitLinksWithLength(id: Long): Seq[(Long, Double, Seq[(Double, Double)])] = {
    val speedLimitLinks = sql"""
      select rl.id, pos.start_measure, pos.end_measure
        from ASSET a
        join ASSET_LINK al on a.id = al.asset_id
        join LRM_POSITION pos on al.position_id = pos.id
        join ROAD_LINK rl on pos.road_link_id = rl.id
        where a.asset_type_id = 20 and a.id = $id
        """.as[(Long, Double, Double)].list
    speedLimitLinks.map { case (roadLinkId, startMeasure, endMeasure) =>
      val points = RoadLinkService.getRoadLinkGeometry(roadLinkId, startMeasure, endMeasure)
      (roadLinkId, endMeasure - startMeasure, points)
    }
  }

  def getSpeedLimitLinksByBoundingBox(bounds: BoundingRectangle): Seq[(Long, Long, Int, Int, Seq[(Double, Double)])] = {
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
    speedLimits.map(transformLink)
  }

  def getSpeedLimitLinksById(id: Long): Seq[(Long, Long, Int, Int, Seq[(Double, Double)])] = {
    val speedLimits = sql"""
      select a.id, rl.id, pos.side_code, e.name_fi as speed_limit, pos.start_measure, pos.end_measure
        from ASSET a
        join ASSET_LINK al on a.id = al.asset_id
        join LRM_POSITION pos on al.position_id = pos.id
        join ROAD_LINK rl on pos.road_link_id = rl.id
        join PROPERTY p on a.asset_type_id = p.asset_type_id and p.public_id = 'rajoitus'
        join SINGLE_CHOICE_VALUE s on s.asset_id = a.id and s.property_id = p.id
        join ENUMERATED_VALUE e on s.enumerated_value_id = e.id
        where a.asset_type_id = 20 and a.id = $id
        """.as[(Long, Long, Int, Int, Double, Double)].list
    speedLimits.map { case (id, roadLinkId, sideCode, value, startMeasure, endMeasure) =>
      val points = RoadLinkService.getRoadLinkGeometry(roadLinkId, startMeasure, endMeasure)
      (id, roadLinkId, sideCode, value, points)
    }
  }

  def getSpeedLimitLinks(id: Long): Seq[(Long, Seq[(Double, Double)])] = {
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

  def getSpeedLimitDetails(id: Long): (Option[String], Option[DateTime], Option[String], Option[DateTime], Int, Seq[(Long, Long, Int, Int, Seq[(Double, Double)])]) = {
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

  def createSpeedLimit(creator: String, roadLinkId: Long, linkMeasures: (Double, Double), sideCode: Int): Long = {
    val (startMeasure, endMeasure) = linkMeasures
    val assetId = OracleSpatialAssetDao.nextPrimaryKeySeqValue
    val lrmPositionId = OracleSpatialAssetDao.nextLrmPositionPrimaryKeySeqValue
    sqlu"""
      insert into asset(id, asset_type_id, created_by, created_date)
      values ($assetId, 20, $creator, sysdate)
    """.execute()
    sqlu"""
      insert into lrm_position(id, start_measure, end_measure, road_link_id, side_code)
      values ($lrmPositionId, $startMeasure, $endMeasure, $roadLinkId, $sideCode)
    """.execute()
    sqlu"""
      insert into asset_link(asset_id, position_id)
      values ($assetId, $lrmPositionId)
    """.execute()
    val propertyId = Q.query[String, Long](Queries.propertyIdByPublicId).firstOption("rajoitus").get
    Queries.insertSingleChoiceProperty(assetId, propertyId, 50).execute()
    assetId
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

  def splitSpeedLimit(id: Long, roadLinkId: Long, splitMeasure: Double, username: String): Long = {
    Queries.updateAssetModified(id, username).execute()
    val (startMeasure, endMeasure, sideCode) = getSpeedLimitLinkGeometryData(id, roadLinkId)
    val links: Seq[(Long, Double, (Point, Point))] = getSpeedLimitLinksWithLength(id).map { link =>
      val (roadLinkId, length, geometry) = link
      (roadLinkId, length, GeometryUtils.geometryEndpoints(geometry))
    }
    val endPoints: Seq[(Point, Point)] = links.map { case (_, _, linkEndPoints) => linkEndPoints }
    val linksWithPositions: Seq[(Long, Double, (Point, Point), Int)] = links
      .zip(SpeedLimitLinkPositions.generate(endPoints))
      .map { case ((linkId, length, linkEndPoints), position) => (linkId, length, linkEndPoints, position) }
      .sortBy { case (_, _, _, position) => position }
    val linksBeforeSplitLink: Seq[(Long, Double, (Point, Point), Int)] = linksWithPositions.takeWhile { case (linkId, _, _, _) => linkId != roadLinkId }
    val linksAfterSplitLink: Seq[(Long, Double, (Point, Point), Int)] = linksWithPositions.dropWhile { case (linkId, _, _, _) => linkId != roadLinkId }.tail

    val firstSplitLength = splitMeasure - startMeasure + linksBeforeSplitLink.foldLeft(0.0) { case (acc, link) => acc + link._2}
    val secondSplitLength = endMeasure - splitMeasure + linksAfterSplitLink.foldLeft(0.0) { case (acc, link) => acc + link._2}

    val (existingLinkMeasures, createdLinkMeasures, linksToMove) = firstSplitLength > secondSplitLength match {
      case true => ((startMeasure, splitMeasure), (splitMeasure, endMeasure), linksAfterSplitLink)
      case false => ((splitMeasure, endMeasure), (startMeasure, splitMeasure), linksBeforeSplitLink)
    }

    updateLinkStartAndEndMeasures(id, roadLinkId, existingLinkMeasures)
    val createdId = createSpeedLimit(username, roadLinkId, createdLinkMeasures, sideCode)
    if (linksToMove.nonEmpty) moveLinksToSpeedLimit(id, createdId, linksToMove.map(_._1))
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
}
