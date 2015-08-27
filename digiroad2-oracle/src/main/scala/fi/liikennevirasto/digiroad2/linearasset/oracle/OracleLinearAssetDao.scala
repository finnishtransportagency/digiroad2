package fi.liikennevirasto.digiroad2.linearasset.oracle

import _root_.oracle.spatial.geometry.JGeometry
import fi.liikennevirasto.digiroad2.linearasset._
import SpeedLimitFiller.UnknownLimit
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.asset.oracle.{Queries, Sequences}
import fi.liikennevirasto.digiroad2.oracle.MassQuery
import org.joda.time.DateTime

import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import _root_.oracle.sql.STRUCT
import com.github.tototoshi.slick.MySQLJodaSupport._
import org.slf4j.LoggerFactory
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedParameters, PositionedResult, SetParameter, StaticQuery => Q}

import scala.util.Try


trait OracleLinearAssetDao {
  def getUnknownSpeedLimits(municipalities: Option[Set[Int]]): Map[String, Map[String, Any]] = {
    case class UnknownLimit(mmlId: Long, municipality: String, administrativeClass: String)
    def toUnknownLimit(x: (Long, String, Int)) = UnknownLimit(x._1, x._2, AdministrativeClass(x._3).toString)
    val optionalMunicipalities = municipalities.map(_.mkString(","))
    val unknownSpeedLimitQuery = """
      select mml_id, name_fi, administrative_class
      from (select s.mml_id, m.name_fi, s.administrative_class, rank() over (partition by s.municipality_code, s.administrative_class order by s.mml_id) rank, s.municipality_code
           from unknown_speed_limit s
           join municipality m on s.municipality_code = m.id)
      where rank <= 10
      """

    val sql = optionalMunicipalities match {
      case Some(m) => unknownSpeedLimitQuery + s" and municipality_code in ($m)"
      case _ => unknownSpeedLimitQuery
    }

    val limitsByMunicipality = Q.queryNA[(Long, String, Int)](sql).list
      .map(toUnknownLimit)
      .groupBy(_.municipality)
      .mapValues {
      _.groupBy(_.administrativeClass)
        .mapValues(_.map(_.mmlId))
    }

    addCountsFor(limitsByMunicipality)
  }

  private def addCountsFor(unknownLimitsByMunicipality: Map[String, Map[String, Any]]): Map[String, Map[String, Any]] = {
    val unknownSpeedLimitCounts =  sql"""
      select name_fi, s.administrative_class, count(*)
      from unknown_speed_limit s
      join municipality m on s.municipality_code = m.id
      group by name_fi, administrative_class
    """.as[(String, Int, Int)].list

    unknownLimitsByMunicipality.map { case (municipality, values) =>
      val municipalityCount = unknownSpeedLimitCounts.find(x => x._1 == municipality && x._2 == Municipality.value).map(_._3).getOrElse(0)
      val stateCount = unknownSpeedLimitCounts.find(x => x._1 == municipality && x._2 == State.value).map(_._3).getOrElse(0)
      val privateCount = unknownSpeedLimitCounts.find(x => x._1 == municipality && x._2 == Private.value).map(_._3).getOrElse(0)

      val valuesWithCounts = values +
        ("municipalityCount" -> municipalityCount) +
        ("stateCount" -> stateCount) +
        ("privateCount" -> privateCount) +
        ("totalCount" -> (municipalityCount + stateCount + privateCount))

      (municipality -> valuesWithCounts)
    }
  }

  def persistUnknownSpeedLimits(limits: Seq[UnknownLimit]): Unit = {
    val statement = dynamicSession.prepareStatement("""
        insert into unknown_speed_limit (mml_id, municipality_code, administrative_class)
        select ?, ?, ?
        from dual
        where not exists (select * from unknown_speed_limit where mml_id = ?)
      """)
    limits.foreach { limit =>
      statement.setLong(1, limit.mmlId)
      statement.setInt(2, limit.municipalityCode)
      statement.setInt(3, limit.administrativeClass.value)
      statement.setLong(4, limit.mmlId)
      statement.addBatch()
    }
    statement.executeBatch()
    statement.close()
  }

  def purgeFromUnknownSpeedLimits(mmlId: Long, roadLinkLength: Double): Unit = {
    val speedLimits = fetchSpeedLimitsByMmlId(mmlId)

    def calculateRemainders(sideCode: SideCode): Seq[(Double, Double)] = {
      val limitEndPoints = speedLimits.filter(sl => sl._3 == SideCode.BothDirections || sl._3 == sideCode).map { case(_, _, _, _, start, end) => (start, end) }
      limitEndPoints.foldLeft(Seq((0.0, roadLinkLength)))(GeometryUtils.subtractIntervalFromIntervals).filter { case (start, end) => math.abs(end - start) > 0.01}
    }

    val towardsRemainders = calculateRemainders(SideCode.TowardsDigitizing)
    val againstRemainders = calculateRemainders(SideCode.AgainstDigitizing)
    if (towardsRemainders.isEmpty && againstRemainders.isEmpty) {
      sqlu"""delete from unknown_speed_limit where mml_id = $mmlId""".execute
    }
  }

  case class GeneratedSpeedLimitLink(id: Long, mmlId: Long, roadLinkId: Long, sideCode: Int, startMeasure: Double, endMeasure: Double)

  val roadLinkService: RoadLinkService
  val logger = LoggerFactory.getLogger(getClass)

  implicit object GetByteArray extends GetResult[Array[Byte]] {
    def apply(rs: PositionedResult) = rs.nextBytes()
  }

  implicit object GetSideCode extends GetResult[SideCode] {
    def apply(rs: PositionedResult) = SideCode(rs.nextInt())
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

  def getLinksWithLengthFromVVH(assetTypeId: Int, id: Long): Seq[(Long, Double, Seq[Point], Int)] = {
    val links = sql"""
      select pos.mml_id, pos.start_measure, pos.end_measure
        from ASSET a
        join ASSET_LINK al on a.id = al.asset_id
        join LRM_POSITION pos on al.position_id = pos.id
        where a.asset_type_id = $assetTypeId and a.id = $id
        """.as[(Long, Double, Double)].list

    val roadLinksByMmlId = roadLinkService.fetchVVHRoadlinks(links.map(_._1).toSet)

    links.map { case (mmlId, startMeasure, endMeasure) =>
      val vvhRoadLink = roadLinksByMmlId.find(_.mmlId == mmlId).getOrElse(throw new NoSuchElementException)
      val truncatedGeometry = GeometryUtils.truncateGeometry(vvhRoadLink.geometry, startMeasure, endMeasure)
      (mmlId, endMeasure - startMeasure, truncatedGeometry, vvhRoadLink.municipalityCode)
    }
  }

  private def fetchSpeedLimitsByMmlIds(mmlIds: Seq[Long]) = {
    MassQuery.withIds(mmlIds.toSet) { idTableName =>
      sql"""
        select a.id, pos.mml_id, pos.side_code, e.value, pos.start_measure, pos.end_measure, a.modified_by,  a.modified_date, a.created_by, a.created_date
           from asset a
           join asset_link al on a.id = al.asset_id
           join lrm_position pos on al.position_id = pos.id
           join property p on a.asset_type_id = p.asset_type_id and p.public_id = 'rajoitus'
           join single_choice_value s on s.asset_id = a.id and s.property_id = p.id
           join enumerated_value e on s.enumerated_value_id = e.id
           join  #$idTableName i on i.id = pos.mml_id
           where a.asset_type_id = 20 and floating = 0""".as[(Long, Long, SideCode, Option[Int], Double, Double, Option[String], Option[DateTime], Option[String], Option[DateTime])].list
    }
  }

  private def fetchSpeedLimitsByMmlId(mmlId: Long) = {
    sql"""
      select a.id, pos.mml_id, pos.side_code, e.value, pos.start_measure, pos.end_measure
         from asset a
         join asset_link al on a.id = al.asset_id
         join lrm_position pos on al.position_id = pos.id
         join property p on a.asset_type_id = p.asset_type_id and p.public_id = 'rajoitus'
         join single_choice_value s on s.asset_id = a.id and s.property_id = p.id
         join enumerated_value e on s.enumerated_value_id = e.id
         where a.asset_type_id = 20 and floating = 0 and pos.mml_id = $mmlId""".as[(Long, Long, SideCode, Option[Int], Double, Double)].list
  }

  def getSpeedLimitLinksByRoadLinks(roadLinks: Seq[VVHRoadLinkWithProperties]): (Seq[SpeedLimit],  Map[Long, RoadLinkForSpeedLimit]) = {
    val topology = toTopology(roadLinks)
    val speedLimitLinks = fetchSpeedLimitsByMmlIds(topology.keys.toSeq).map(createGeometryForSegment(topology))
    (speedLimitLinks, topology)
  }

  private def toTopology(roadLinks: Seq[VVHRoadLinkWithProperties]): Map[Long, RoadLinkForSpeedLimit] = {
    def municipalityCodeFromAttributes(attributes: Map[String, Any]): Int = {
      attributes("MUNICIPALITYCODE").asInstanceOf[BigInt].intValue()
    }
    def isCarTrafficRoad(link: VVHRoadLinkWithProperties) = Set(1, 2, 3, 4, 5, 6).contains(link.functionalClass % 10)
    def toRoadLinkForSpeedLimit(link: VVHRoadLinkWithProperties) = RoadLinkForSpeedLimit(
      link.geometry,
      link.length,
      link.administrativeClass,
      link.mmlId,
      LinearAsset.roadIdentifierFromRoadLink(link),
      link.trafficDirection,
      municipalityCodeFromAttributes(link.attributes))

    roadLinks
      .filter(isCarTrafficRoad)
      .map(toRoadLinkForSpeedLimit)
      .groupBy(_.mmlId).mapValues(_.head)
  }

  private def createGeometryForSegment(topology: Map[Long, RoadLinkForSpeedLimit])(segment: (Long, Long, SideCode, Option[Int], Double, Double, Option[String], Option[DateTime], Option[String], Option[DateTime])) = {
    val (assetId, mmlId, sideCode, speedLimit, startMeasure, endMeasure, modifiedBy, modifiedDate, createdBy, createdDate) = segment
    val roadLink = topology.get(mmlId).get
    val geometry = GeometryUtils.truncateGeometry(roadLink.geometry, startMeasure, endMeasure)
    SpeedLimit(assetId, mmlId, sideCode, roadLink.trafficDirection, speedLimit, geometry, startMeasure, endMeasure, modifiedBy, modifiedDate, createdBy, createdDate)
  }

  def getSpeedLimitLinksById(id: Long): Seq[SpeedLimit] = {
    val speedLimits = sql"""
      select a.id, pos.mml_id, pos.side_code, e.value, pos.start_measure, pos.end_measure, a.modified_by, a.modified_date, a.created_by, a.created_date
        from ASSET a
        join ASSET_LINK al on a.id = al.asset_id
        join LRM_POSITION pos on al.position_id = pos.id
        join PROPERTY p on a.asset_type_id = p.asset_type_id and p.public_id = 'rajoitus'
        join SINGLE_CHOICE_VALUE s on s.asset_id = a.id and s.property_id = p.id
        join ENUMERATED_VALUE e on s.enumerated_value_id = e.id
        where a.asset_type_id = 20 and a.id = $id
        """.as[(Long, Long, SideCode, Option[Int], Double, Double, Option[String], Option[DateTime], Option[String], Option[DateTime])].list

    val roadLinksByMmlId = roadLinkService.fetchVVHRoadlinks(speedLimits.map(_._2).toSet)

    speedLimits.map { case (assetId, mmlId, sideCode, value, startMeasure, endMeasure, modifiedBy, modifiedDate, createdBy, createdDate) =>
      val vvhRoadLink = roadLinksByMmlId.find(_.mmlId == mmlId).getOrElse(throw new NoSuchElementException)
      SpeedLimit(assetId, mmlId, sideCode, vvhRoadLink.trafficDirection, value, GeometryUtils.truncateGeometry(vvhRoadLink.geometry, startMeasure, endMeasure), startMeasure, endMeasure, modifiedBy, modifiedDate, createdBy, createdDate)
    }
  }

  def getSpeedLimitDetails(id: Long): (Option[String], Option[DateTime], Option[String], Option[DateTime], Option[Int]) = {
    val (modifiedBy, modifiedDate, createdBy, createdDate, value) = sql"""
      select a.modified_by, a.modified_date, a.created_by, a.created_date, e.value
      from ASSET a
      join PROPERTY p on a.asset_type_id = p.asset_type_id and p.public_id = 'rajoitus'
      join SINGLE_CHOICE_VALUE s on s.asset_id = a.id and s.property_id = p.id
      join ENUMERATED_VALUE e on s.enumerated_value_id = e.id
      where a.id = $id
    """.as[(Option[String], Option[DateTime], Option[String], Option[DateTime], Option[Int])].first
    (modifiedBy, modifiedDate, createdBy, createdDate, value)
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
  
  def getLinkGeometryData(id: Long): (Double, Double, SideCode) = {
    sql"""
      select lrm.START_MEASURE, lrm.END_MEASURE, lrm.SIDE_CODE
        from asset a
        join asset_link al on a.ID = al.ASSET_ID
        join lrm_position lrm on lrm.id = al.POSITION_ID
        where a.id = $id
    """.as[(Double, Double, SideCode)].first
  }
  
  def createSpeedLimit(creator: String, mmlId: Long, linkMeasures: (Double, Double), sideCode: SideCode, value: Int,  municipalityValidation: (Int) => Unit): Option[Long] = {
    municipalityValidation(roadLinkService.fetchVVHRoadlink(mmlId).get.municipalityCode)
    createSpeedLimitWithoutDuplicates(creator, mmlId, linkMeasures, sideCode, value)
  }

  def forceCreateSpeedLimit(creator: String, mmlId: Long, linkMeasures: (Double, Double), sideCode: SideCode, value: Int): Long = {
    val (startMeasure, endMeasure) = linkMeasures
    val speedLimitId = Sequences.nextPrimaryKeySeqValue
    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
    val propertyId = Q.query[String, Long](Queries.propertyIdByPublicId).apply("rajoitus").first
    val sideCodeValue = sideCode.value

    val insertAll =
      s"""
      INSERT ALL
        into asset(id, asset_type_id, created_by, created_date)
        values ($speedLimitId, 20, '$creator', sysdate)

        into lrm_position(id, start_measure, end_measure, mml_id, side_code)
        values ($lrmPositionId, $startMeasure, $endMeasure, $mmlId, $sideCodeValue)

        into asset_link(asset_id, position_id)
        values ($speedLimitId, $lrmPositionId)

        into single_choice_value(asset_id, enumerated_value_id, property_id, modified_date)
        values ($speedLimitId, (select id from enumerated_value where property_id = $propertyId and value = $value), $propertyId, current_timestamp)
      SELECT * FROM DUAL
      """
    Q.updateNA(insertAll).execute

    speedLimitId
  }
  
  private def createSpeedLimitWithoutDuplicates(creator: String, mmlId: Long, linkMeasures: (Double, Double), sideCode: SideCode, value: Int): Option[Long] = {
    val (startMeasure, endMeasure) = linkMeasures
    val existingLrmPositions = fetchSpeedLimitsByMmlId(mmlId).filter(sl => sideCode == SideCode.BothDirections || sl._3 == sideCode).map { case(_, _, _, _, start, end) => (start, end) }
    val remainders = existingLrmPositions.foldLeft(Seq((startMeasure, endMeasure)))(GeometryUtils.subtractIntervalFromIntervals).filter { case (start, end) => math.abs(end - start) > 0.01}
    if (remainders.length == 1) {
      Some(forceCreateSpeedLimit(creator, mmlId, linkMeasures, sideCode, value))
    } else {
      None
    }
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
    Q.update[Seq[Long]](sql).apply(roadLinkIds).list
  }

  def moveLinksByMmlId(sourceId: Long, targetId: Long, mmlIds: Seq[Long]): Unit = {
    val roadLinks = mmlIds.mkString(",")
    sqlu"""
      update ASSET_LINK
      set
        asset_id = $targetId
      where asset_id = $sourceId and position_id in (
        select al.position_id from asset_link al join lrm_position lrm on al.position_id = lrm.id where lrm.mml_id in (#$roadLinks))
    """.execute
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
    """.execute
  }
  
  def updateMValues(id: Long, linkMeasures: (Double, Double)): Unit = {
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
          where a.id = $id)
    """.execute
  }

  def updateSideCode(id: Long, sideCode: SideCode): Unit = {
    val sideCodeValue = sideCode.value
    sqlu"""
      update LRM_POSITION
      set
        side_code = $sideCodeValue
      where id = (
        select lrm.id
          from asset a
          join asset_link al on a.ID = al.ASSET_ID
          join lrm_position lrm on lrm.id = al.POSITION_ID
          where a.id = $id)
    """.execute
  }


  def splitSpeedLimit(id: Long, splitMeasure: Double, value: Int, username: String, municipalityValidation: (Int) => Unit): Long = {
    def withMunicipalityValidation(vvhLinks: Seq[(Long, Double, Seq[Point], Int)]) = {
      vvhLinks.foreach(vvhLink => municipalityValidation(vvhLink._4))
      vvhLinks
    }

    val (startMeasure, endMeasure, sideCode) = getLinkGeometryData(id)
    val link: (Long, Double, (Point, Point)) =
      withMunicipalityValidation(getLinksWithLengthFromVVH(20, id)).headOption.map { case (mmlId, length, geometry, _) =>
        (mmlId, length, GeometryUtils.geometryEndpoints(geometry))
      }.get

    Queries.updateAssetModified(id, username).execute
    val (existingLinkMeasures, createdLinkMeasures) = GeometryUtils.createSplit(splitMeasure, (startMeasure, endMeasure))

    updateMValues(id, existingLinkMeasures)
    val createdId = createSpeedLimitWithoutDuplicates(username, link._1, createdLinkMeasures, sideCode, value).get
    createdId
  }

  def separateSpeedLimit(id: Long, valueTowardsDigitization: Int, valueAgainstDigitization: Int, username: String, municipalityValidation: Int => Unit): Long = {
    val speedLimit = getSpeedLimitLinksById(id).head
    val separable = speedLimit.sideCode == SideCode.BothDirections && speedLimit.trafficDirection == TrafficDirection.BothDirections
    if (!separable) throw new IllegalArgumentException
    updateSpeedLimitValue(id, valueTowardsDigitization, username, municipalityValidation)
    updateSideCode(id, SideCode.TowardsDigitizing)
    createSpeedLimitWithoutDuplicates(username, speedLimit.mmlId, (speedLimit.startMeasure, speedLimit.endMeasure), SideCode.AgainstDigitizing, valueAgainstDigitization).get
  }

  def updateSpeedLimitValue(id: Long, value: Int, username: String, municipalityValidation: Int => Unit): Option[Long] = {
    def validateMunicipalities(vvhLinks: Seq[(Long, Double, Seq[Point], Int)]): Unit = {
      vvhLinks.foreach(vvhLink => municipalityValidation(vvhLink._4))
    }

    validateMunicipalities(getLinksWithLengthFromVVH(20, id))
    val propertyId = Q.query[String, Long](Queries.propertyIdByPublicId).apply("rajoitus").first
    val assetsUpdated = Queries.updateAssetModified(id, username).first
    val propertiesUpdated = Queries.updateSingleChoiceProperty(id, propertyId, value.toLong).first
    if (assetsUpdated == 1 && propertiesUpdated == 1) {
      Some(id)
    } else {
      dynamicSession.rollback()
      None
    }
  }

  def markSpeedLimitsFloating(ids: Set[Long]): Unit = {
    if (ids.nonEmpty) {
      MassQuery.withIds(ids) { idTableName =>
        sqlu"""update asset set floating = 1 where id in (select id from #$idTableName)""".execute
      }
    }
  }
}

object OracleLinearAssetDao extends OracleLinearAssetDao {
  override val roadLinkService: RoadLinkService = RoadLinkService
}
