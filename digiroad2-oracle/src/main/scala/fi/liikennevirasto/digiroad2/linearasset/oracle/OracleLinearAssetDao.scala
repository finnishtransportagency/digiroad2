package fi.liikennevirasto.digiroad2.linearasset.oracle

import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.{Queries, Sequences}
import fi.liikennevirasto.digiroad2.oracle.MassQuery
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import _root_.oracle.sql.STRUCT
import com.github.tototoshi.slick.MySQLJodaSupport._
import org.slf4j.LoggerFactory
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedParameters, PositionedResult, SetParameter, StaticQuery => Q}

case class PersistedSpeedLimit(id: Long, linkId: Long, sideCode: SideCode, value: Option[Int], startMeasure: Double, endMeasure: Double,
                               modifiedBy: Option[String], modifiedDate: Option[DateTime], createdBy: Option[String], createdDate: Option[DateTime],
                              vvhTimeStamp: Long, vvhModifiedDate: Option[String])

class OracleLinearAssetDao(val vvhClient: VVHClient) {

  /**
    * Returns unknown speed limits by municipality. Used by SpeedLimitService.getUnknown.
    */
  def getUnknownSpeedLimits(municipalities: Option[Set[Int]]): Map[String, Map[String, Any]] = {
    case class UnknownLimit(linkId: Long, municipality: String, administrativeClass: String)
    def toUnknownLimit(x: (Long, String, Int)) = UnknownLimit(x._1, x._2, AdministrativeClass(x._3).toString)
    val optionalMunicipalities = municipalities.map(_.mkString(","))
    val unknownSpeedLimitQuery = """
      select s.link_id, m.name_fi, s.administrative_class
      from unknown_speed_limit s
      join municipality m on s.municipality_code = m.id
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
        .mapValues(_.map(_.linkId))
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

  /**
    * Saves unknown speed limits to unknown speed limits list. Used by SpeedLimitService.persistUnknown.
    */
  def persistUnknownSpeedLimits(limits: Seq[UnknownSpeedLimit]): Unit = {
    val statement = dynamicSession.prepareStatement("""
        insert into unknown_speed_limit (link_id, municipality_code, administrative_class)
        select ?, ?, ?
        from dual
        where not exists (select * from unknown_speed_limit where link_id = ?)
      """)
    try {
      limits.foreach { limit =>
        statement.setLong(1, limit.linkId)
        statement.setInt(2, limit.municipalityCode)
        statement.setInt(3, limit.administrativeClass.value)
        statement.setLong(4, limit.linkId)
        statement.addBatch()
      }
      statement.executeBatch()
    } finally {
      statement.close()
    }
  }

  /**
    * Removes speed limits from unknown speed limits list. Used by SpeedLimitService.purgeUnknown.
    */
  def purgeFromUnknownSpeedLimits(linkId: Long, roadLinkLength: Double): Unit = {
    val speedLimits = fetchSpeedLimitsByLinkId(linkId)

    def calculateRemainders(sideCode: SideCode): Seq[(Double, Double)] = {
      val limitEndPoints = speedLimits.filter(sl => sl._3 == SideCode.BothDirections || sl._3 == sideCode).map { case(_, _, _, _, start, end, _, _) => (start, end) }
      limitEndPoints.foldLeft(Seq((0.0, roadLinkLength)))(GeometryUtils.subtractIntervalFromIntervals).filter { case (start, end) => math.abs(end - start) > 0.01}
    }

    val towardsRemainders = calculateRemainders(SideCode.TowardsDigitizing)
    val againstRemainders = calculateRemainders(SideCode.AgainstDigitizing)
    if (towardsRemainders.isEmpty && againstRemainders.isEmpty) {
      sqlu"""delete from unknown_speed_limit where link_id = $linkId""".execute
    }
  }

  val logger = LoggerFactory.getLogger(getClass)

  /**
    * No usages in OTH.
    */
  implicit object GetByteArray extends GetResult[Array[Byte]] {
    def apply(rs: PositionedResult) = rs.nextBytes()
  }

  /**
    * No usages in OTH.
    */
  implicit object GetSideCode extends GetResult[SideCode] {
    def apply(rs: PositionedResult) = SideCode(rs.nextInt())
  }

  /**
    * No usages in OTH.
    */
  implicit object SetStruct extends SetParameter[STRUCT] {
    def apply(v: STRUCT, pp: PositionedParameters) {
      pp.setObject(v, java.sql.Types.STRUCT)
    }
  }

  /**
    * No usages in OTH.
    */
  implicit val SetParameterFromLong: SetParameter[Seq[Long]] = new SetParameter[Seq[Long]] {
    def apply(seq: Seq[Long], p: PositionedParameters): Unit = {
      seq.foreach(p.setLong)
    }
  }

  /**
    * Returns data for municipality validation. Used by OracleLinearAssetDao.splitSpeedLimit and OracleLinearAssetDao.updateSpeedLimitValue.
    */
  def getLinksWithLengthFromVVH(assetTypeId: Int, id: Long): Seq[(Long, Double, Seq[Point], Int)] = {
    val links = sql"""
      select pos.link_id, pos.start_measure, pos.end_measure
        from ASSET a
        join ASSET_LINK al on a.id = al.asset_id
        join LRM_POSITION pos on al.position_id = pos.id
        where a.asset_type_id = $assetTypeId and a.id = $id
        """.as[(Long, Double, Double)].list

    val roadLinksByLinkId = vvhClient.fetchVVHRoadlinks(links.map(_._1).toSet)

    links.map { case (linkId, startMeasure, endMeasure) =>
      val vvhRoadLink = roadLinksByLinkId.find(_.linkId == linkId).getOrElse(throw new NoSuchElementException)
      val truncatedGeometry = GeometryUtils.truncateGeometry(vvhRoadLink.geometry, startMeasure, endMeasure)
      (linkId, endMeasure - startMeasure, truncatedGeometry, vvhRoadLink.municipalityCode)
    }
  }

  private def fetchSpeedLimitsByLinkIds(linkIds: Seq[Long]) = {
    MassQuery.withIds(linkIds.toSet) { idTableName =>
      sql"""
        select a.id, pos.link_id, pos.side_code, e.value, pos.start_measure, pos.end_measure, a.modified_by,
        a.modified_date, a.created_by, a.created_date, pos.adjusted_timestamp, pos.modified_date
           from asset a
           join asset_link al on a.id = al.asset_id
           join lrm_position pos on al.position_id = pos.id
           join property p on a.asset_type_id = p.asset_type_id and p.public_id = 'rajoitus'
           join single_choice_value s on s.asset_id = a.id and s.property_id = p.id
           join enumerated_value e on s.enumerated_value_id = e.id
           join  #$idTableName i on i.id = pos.link_id
           where a.asset_type_id = 20 and floating = 0""".as[(Long, Long, SideCode, Option[Int], Double, Double, Option[String], Option[DateTime], Option[String], Option[DateTime], Long, Option[String])].list
    }
  }

  /**
    * Iterates a set of asset ids with a property id and returns linear assets. Used by LinearAssetService.getPersistedAssetsByIds,
    * LinearAssetService.split and LinearAssetService.separate.
    */
  def fetchLinearAssetsByIds(ids: Set[Long], valuePropertyId: String): Seq[PersistedLinearAsset] = {
    MassQuery.withIds(ids) { idTableName =>
      val assets = sql"""
        select a.id, pos.link_id, pos.side_code, s.value, pos.start_measure, pos.end_measure,
               a.created_by, a.created_date, a.modified_by, a.modified_date,
               case when a.valid_to <= sysdate then 1 else 0 end as expired, a.asset_type_id,
               pos.adjusted_timestamp, pos.modified_date
          from asset a
          join asset_link al on a.id = al.asset_id
          join lrm_position pos on al.position_id = pos.id
          join property p on p.public_id = $valuePropertyId
          join #$idTableName i on i.id = a.id
          left join number_property_value s on s.asset_id = a.id and s.property_id = p.id
          where a.floating = 0
      """.as[(Long, Long, Int, Option[Int], Double, Double, Option[String], Option[DateTime], Option[String], Option[DateTime], Boolean, Int, Long, Option[String])].list
      assets.map { case (id, linkId, sideCode, value, startMeasure, endMeasure, createdBy, createdDate, modifiedBy, modifiedDate, expired, typeId, vvhTimeStamp, vvhModifiedDate) =>
        PersistedLinearAsset(id, linkId, sideCode, value.map(NumericValue), startMeasure, endMeasure, createdBy, createdDate, modifiedBy, modifiedDate, expired, typeId, vvhTimeStamp, vvhModifiedDate)
      }
    }
  }

  /**
    * Iterates a set of asset ids with a property id and returns linear assets with textual value. Used by LinearAssetService.getPersistedAssetsByIds.
    */
  def fetchAssetsWithTextualValuesByIds(ids: Set[Long], valuePropertyId: String): Seq[PersistedLinearAsset] = {
    MassQuery.withIds(ids) { idTableName =>
      val assets = sql"""
        select a.id, pos.link_id, pos.side_code, s.value_fi, pos.start_measure, pos.end_measure,
               a.created_by, a.created_date, a.modified_by, a.modified_date,
               case when a.valid_to <= sysdate then 1 else 0 end as expired, a.asset_type_id,
               pos.adjusted_timestamp, pos.modified_date
          from asset a
          join asset_link al on a.id = al.asset_id
          join lrm_position pos on al.position_id = pos.id
          join property p on p.public_id = $valuePropertyId
          join #$idTableName i on i.id = a.id
          left join text_property_value s on s.asset_id = a.id and s.property_id = p.id
          where a.floating = 0
      """.as[(Long, Long, Int, Option[String], Double, Double, Option[String], Option[DateTime], Option[String], Option[DateTime], Boolean, Int, Long, Option[String])].list
      assets.map { case (id, linkId, sideCode, value, startMeasure, endMeasure, createdBy, createdDate, modifiedBy, modifiedDate, expired, typeId, vvhTimeStamp, vvhModifiedDate) =>
        PersistedLinearAsset(id, linkId, sideCode, value.map(TextualValue), startMeasure, endMeasure, createdBy, createdDate, modifiedBy, modifiedDate, expired, typeId, vvhTimeStamp, vvhModifiedDate)
      }
    }
  }

  /**
    * Iterates a set of link ids with asset type id and property id and returns linear assets. Used by LinearAssetService.getByRoadLinks.
    */
  def fetchLinearAssetsByLinkIds(assetTypeId: Int, linkIds: Seq[Long], valuePropertyId: String): Seq[PersistedLinearAsset] = {
    MassQuery.withIds(linkIds.toSet) { idTableName =>
      val assets = sql"""
        select a.id, pos.link_id, pos.side_code, s.value as total_weight_limit, pos.start_measure, pos.end_measure,
               a.created_by, a.created_date, a.modified_by, a.modified_date,
               case when a.valid_to <= sysdate then 1 else 0 end as expired, a.asset_type_id,
               pos.adjusted_timestamp, pos.modified_date
          from asset a
          join asset_link al on a.id = al.asset_id
          join lrm_position pos on al.position_id = pos.id
          join property p on p.public_id = $valuePropertyId
          join #$idTableName i on i.id = pos.link_id
          left join number_property_value s on s.asset_id = a.id and s.property_id = p.id
          where a.asset_type_id = $assetTypeId
          and (a.valid_to >= sysdate or a.valid_to is null)
          and a.floating = 0"""
        .as[(Long, Long, Int, Option[Int], Double, Double, Option[String], Option[DateTime], Option[String], Option[DateTime], Boolean, Int, Long, Option[String])].list
      assets.map { case(id, linkId, sideCode, value, startMeasure, endMeasure, createdBy, createdDate, modifiedBy, modifiedDate, expired, typeId, vvhTimeStamp, vvhModifiedDate) =>
        PersistedLinearAsset(id, linkId, sideCode, value.map(NumericValue), startMeasure, endMeasure, createdBy, createdDate, modifiedBy, modifiedDate, expired, typeId, vvhTimeStamp, vvhModifiedDate)
      }
    }
  }

  /**
    * Iterates a set of link ids with asset type id and property id and returns linear assets. Used by LinearAssetService.getByRoadLinks.
    */
  def fetchAssetsWithTextualValuesByLinkIds(assetTypeId: Int, linkIds: Seq[Long], valuePropertyId: String): Seq[PersistedLinearAsset] = {
    MassQuery.withIds(linkIds.toSet) { idTableName =>
      val assets = sql"""
        select a.id, pos.link_id, pos.side_code, s.value_fi, pos.start_measure, pos.end_measure,
               a.created_by, a.created_date, a.modified_by, a.modified_date,
               case when a.valid_to <= sysdate then 1 else 0 end as expired, a.asset_type_id
               pos.adjusted_timestamp, pos.modified_date
          from asset a
          join asset_link al on a.id = al.asset_id
          join lrm_position pos on al.position_id = pos.id
          join property p on p.public_id = $valuePropertyId
          join #$idTableName i on i.id = pos.link_id
          left join text_property_value s on s.asset_id = a.id and s.property_id = p.id
          where a.asset_type_id = $assetTypeId
          and (a.valid_to >= sysdate or a.valid_to is null)
          and a.floating = 0"""
        .as[(Long, Long, Int, Option[String], Double, Double, Option[String], Option[DateTime], Option[String], Option[DateTime], Boolean, Int, Long, Option[String])].list
      assets.map { case(id, linkId, sideCode, value, startMeasure, endMeasure, createdBy, createdDate, modifiedBy, modifiedDate, expired, typeId, vvhTimeStamp, vvhModifiedDate) =>
        PersistedLinearAsset(id, linkId, sideCode, value.map(TextualValue), startMeasure, endMeasure, createdBy, createdDate, modifiedBy, modifiedDate, expired, typeId, vvhTimeStamp, vvhModifiedDate)
      }
    }
  }

  /**
    * Iterates a set of link ids with prohibition asset type id and floating flag and returns linear assets. Used by LinearAssetService.getByRoadLinks
    * and CsvGenerator.generateDroppedProhibitions.
    */
  def fetchProhibitionsByLinkIds(prohibitionAssetTypeId: Int, linkIds: Seq[Long], includeFloating: Boolean = false): Seq[PersistedLinearAsset] = {
    val floatingFilter = if (includeFloating) "" else "and a.floating = 0"

    val assets = MassQuery.withIds(linkIds.toSet) { idTableName =>
      sql"""
        select a.id, pos.link_id, pos.side_code,
               pv.id, pv.type,
               pvp.type, pvp.start_hour, pvp.end_hour,
               pe.type,
               pos.start_measure, pos.end_measure,
               a.created_by, a.created_date, a.modified_by, a.modified_date,
               case when a.valid_to <= sysdate then 1 else 0 end as expired,
               pos.adjusted_timestamp, pos.modified_date
          from asset a
          join asset_link al on a.id = al.asset_id
          join lrm_position pos on al.position_id = pos.id
          join prohibition_value pv on pv.asset_id = a.id
          join #$idTableName i on i.id = pos.link_id
          left join prohibition_validity_period pvp on pvp.prohibition_value_id = pv.id
          left join prohibition_exception pe on pe.prohibition_value_id = pv.id
          where a.asset_type_id = $prohibitionAssetTypeId
          and (a.valid_to >= sysdate or a.valid_to is null)
          #$floatingFilter"""
        .as[(Long, Long, Int, Long, Int, Option[Int], Option[Int], Option[Int], Option[Int], Double, Double, Option[String], Option[DateTime], Option[String], Option[DateTime], Boolean, Long, Option[String])].list
    }

    val groupedByAssetId = assets.groupBy(_._1)
    val groupedByProhibitionId = groupedByAssetId.mapValues(_.groupBy(_._4))

    groupedByProhibitionId.map { case (assetId, rowsByProhibitionId) =>
      val (_, linkId, sideCode, _, _, _, _, _, _, startMeasure, endMeasure, createdBy, createdDate, modifiedBy, modifiedDate, expired, vvhTimeStamp, vvhModifiedDate) = groupedByAssetId(assetId).head
      val prohibitionValues = rowsByProhibitionId.keys.toSeq.sorted.map { prohibitionId =>
        val rows = rowsByProhibitionId(prohibitionId)
        val prohibitionType = rows.head._5
        val exceptions = rows.flatMap(_._9).toSet
        val validityPeriods = rows.filter(_._6.isDefined).map { case row =>
          ValidityPeriod(row._7.get, row._8.get, ValidityPeriodDayOfWeek(row._6.get))
        }.toSet
        ProhibitionValue(prohibitionType, validityPeriods, exceptions)
      }
      PersistedLinearAsset(assetId, linkId, sideCode, Some(Prohibitions(prohibitionValues)), startMeasure, endMeasure, createdBy, createdDate, modifiedBy, modifiedDate, expired, prohibitionAssetTypeId, vvhTimeStamp, vvhModifiedDate)
    }.toSeq
  }

  /**
    * Iterates a set of asset ids with prohibition asset type id and floating flag and returns linear assets. User by LinearAssetSErvice.getPersistedAssetsByIds.
    */
  def fetchProhibitionsByIds(prohibitionAssetTypeId: Int, ids: Set[Long], includeFloating: Boolean = false): Seq[PersistedLinearAsset] = {
    val floatingFilter = if (includeFloating) "" else "and a.floating = 0"

    val assets = MassQuery.withIds(ids.toSet) { idTableName =>
      sql"""
        select a.id, pos.link_id, pos.side_code,
               pv.id, pv.type,
               pvp.type, pvp.start_hour, pvp.end_hour,
               pe.type,
               pos.start_measure, pos.end_measure,
               a.created_by, a.created_date, a.modified_by, a.modified_date,
               case when a.valid_to <= sysdate then 1 else 0 end as expired
          from asset a
          join asset_link al on a.id = al.asset_id
          join lrm_position pos on al.position_id = pos.id
          join prohibition_value pv on pv.asset_id = a.id
          join #$idTableName i on i.id = a.id
          left join prohibition_validity_period pvp on pvp.prohibition_value_id = pv.id
          left join prohibition_exception pe on pe.prohibition_value_id = pv.id
          where a.asset_type_id = $prohibitionAssetTypeId
          and (a.valid_to >= sysdate or a.valid_to is null)
          #$floatingFilter"""
        .as[(Long, Long, Int, Long, Int, Option[Int], Option[Int], Option[Int], Option[Int], Double, Double, Option[String], Option[DateTime], Option[String], Option[DateTime], Boolean)].list
    }

    val groupedByAssetId = assets.groupBy(_._1)
    val groupedByProhibitionId = groupedByAssetId.mapValues(_.groupBy(_._4))

    groupedByProhibitionId.map { case (assetId, rowsByProhibitionId) =>
      val (_, linkId, sideCode, _, _, _, _, _, _, startMeasure, endMeasure, createdBy, createdDate, modifiedBy, modifiedDate, expired) = groupedByAssetId(assetId).head
      val prohibitionValues = rowsByProhibitionId.keys.toSeq.sorted.map { prohibitionId =>
        val rows = rowsByProhibitionId(prohibitionId)
        val prohibitionType = rows.head._5
        val exceptions = rows.flatMap(_._9).toSet
        val validityPeriods = rows.filter(_._6.isDefined).map { case row =>
          ValidityPeriod(row._7.get, row._8.get, ValidityPeriodDayOfWeek(row._6.get))
        }.toSet
        ProhibitionValue(prohibitionType, validityPeriods, exceptions)
      }
      PersistedLinearAsset(assetId, linkId, sideCode, Some(Prohibitions(prohibitionValues)), startMeasure, endMeasure, createdBy, createdDate, modifiedBy, modifiedDate, expired, prohibitionAssetTypeId, 0, None)
    }.toSeq
  }


  private def fetchSpeedLimitsByLinkId(linkId: Long) = {
    sql"""
      select a.id, pos.link_id, pos.side_code, e.value, pos.start_measure, pos.end_measure, pos.adjusted_timestamp, pos.modified_date
         from asset a
         join asset_link al on a.id = al.asset_id
         join lrm_position pos on al.position_id = pos.id
         join property p on a.asset_type_id = p.asset_type_id and p.public_id = 'rajoitus'
         join single_choice_value s on s.asset_id = a.id and s.property_id = p.id
         join enumerated_value e on s.enumerated_value_id = e.id
         where a.asset_type_id = 20 and floating = 0 and pos.link_id = $linkId""".as[(Long, Long, SideCode, Option[Int], Double, Double, Long, Option[String])].list
  }

  /**
    * Returns only car traffic roads as a topology and speed limits that match these road links.
    * Used by SpeedLimitService.get (by bounding box and a list of municipalities) and SpeedLimitService.get (by municipality)
    */
  def getSpeedLimitLinksByRoadLinks(roadLinks: Seq[RoadLink]): (Seq[SpeedLimit],  Seq[RoadLink]) = {
    val topology = roadLinks.filter(_.isCarTrafficRoad)
    val speedLimitLinks = fetchSpeedLimitsByLinkIds(topology.map(_.linkId)).map(createGeometryForSegment(topology))
    (speedLimitLinks, topology)
  }

  private def createGeometryForSegment(topology: Seq[RoadLink])(segment: (Long, Long, SideCode, Option[Int], Double, Double, Option[String], Option[DateTime], Option[String], Option[DateTime], Long, Option[String])) = {
    val (assetId, linkId, sideCode, speedLimit, startMeasure, endMeasure, modifiedBy, modifiedDate, createdBy, createdDate, vvhTimeStamp, vvhModifiedDate) = segment
    val roadLink = topology.find(_.linkId == linkId).get
    val geometry = GeometryUtils.truncateGeometry(roadLink.geometry, startMeasure, endMeasure)
    SpeedLimit(assetId, linkId, sideCode, roadLink.trafficDirection, speedLimit.map(NumericValue), geometry, startMeasure, endMeasure, modifiedBy, modifiedDate, createdBy, createdDate, vvhTimeStamp, vvhModifiedDate)
  }

  /**
    * Returns speed limits by asset id. Used by SpeedLimitService.loadSpeedLimit.
    */
  def getSpeedLimitLinksById(id: Long): Seq[SpeedLimit] = {
    val speedLimits = sql"""
      select a.id, pos.link_id, pos.side_code, e.value, pos.start_measure, pos.end_measure, a.modified_by, a.modified_date, a.created_by, a.created_date, pos.adjusted_timestamp, pos.modified_date
        from ASSET a
        join ASSET_LINK al on a.id = al.asset_id
        join LRM_POSITION pos on al.position_id = pos.id
        join PROPERTY p on a.asset_type_id = p.asset_type_id and p.public_id = 'rajoitus'
        join SINGLE_CHOICE_VALUE s on s.asset_id = a.id and s.property_id = p.id
        join ENUMERATED_VALUE e on s.enumerated_value_id = e.id
        where a.asset_type_id = 20 and a.id = $id
        """.as[(Long, Long, SideCode, Option[Int], Double, Double, Option[String], Option[DateTime], Option[String], Option[DateTime], Long, Option[String])].list

    val roadLinksByLinkId = vvhClient.fetchVVHRoadlinks(speedLimits.map(_._2).toSet)

    speedLimits.map { case (assetId, linkId, sideCode, value, startMeasure, endMeasure, modifiedBy, modifiedDate, createdBy, createdDate, vvhTimeStamp, vvhModifiedDate) =>
      val vvhRoadLink = roadLinksByLinkId.find(_.linkId == linkId).getOrElse(throw new NoSuchElementException)
      SpeedLimit(assetId, linkId, sideCode, vvhRoadLink.trafficDirection, value.map(NumericValue), GeometryUtils.truncateGeometry(vvhRoadLink.geometry, startMeasure, endMeasure), startMeasure, endMeasure, modifiedBy, modifiedDate, createdBy, createdDate, vvhTimeStamp, vvhModifiedDate)
    }
  }

  /**
    * Returns speed limits that match a set of link ids. Used by SpeedLimitService.fillNewRoadLinksWithPreviousSpeedLimitData.
    */
  def getCurrentSpeedLimitsByLinkIds(ids: Option[Set[Long]]): List[SpeedLimit] = {
    def makeLinkIdSql(s: String) = {
      s.length match {
        case 0 => " and 1=0"
        case _ => s" and pos.link_id in (" + s + ")"
      }
    }

    val idString = ids.map(_.mkString(","))
    val sql = "select a.id, pos.link_id, pos.side_code, e.value, pos.start_measure, pos.end_measure, a.modified_by, a.modified_date, a.created_by, a.created_date, pos.adjusted_timestamp, pos.modified_date "+
    "from ASSET a "+
    "join ASSET_LINK al on a.id = al.asset_id " +
    "join LRM_POSITION pos on al.position_id = pos.id " +
    "join PROPERTY p on a.asset_type_id = p.asset_type_id and p.public_id = 'rajoitus' " +
    "join SINGLE_CHOICE_VALUE s on s.asset_id = a.id and s.property_id = p.id " +
    "join ENUMERATED_VALUE e on s.enumerated_value_id = e.id " +
    "where a.asset_type_id = 20 AND (a.valid_to IS NULL OR a.valid_to >= SYSDATE ) "

    idString match {
      case Some(s) =>
        val idSql = sql + makeLinkIdSql(s)
        Q.queryNA[(Long, Long, SideCode, Option[Int], Double, Double, Option[String], Option[DateTime], Option[String], Option[DateTime], Long, Option[String])] (idSql).list.map {
          case (assetId, linkId, sideCode, value, startMeasure, endMeasure, modifiedBy, modifiedDate, createdBy, createdDate, vvhTimeStamp, vvhModifiedDate) =>
            SpeedLimit(assetId, linkId, sideCode, TrafficDirection.UnknownDirection, value.map(NumericValue), Seq(Point(0.0, 0.0)), startMeasure, endMeasure, modifiedBy, modifiedDate, createdBy, createdDate, vvhTimeStamp, vvhModifiedDate)
        }
      case _ => List()
    }
  }

  /**
    * Returns speed limit by asset id. Used by SpeedLimitService.separate.
    */
  def getPersistedSpeedLimit(id: Long): Option[PersistedSpeedLimit] = {
    val speedLimit = sql"""
      select a.id, pos.link_id, pos.side_code, e.value, pos.start_measure, pos.end_measure, a.modified_by, a.modified_date, a.created_by, a.created_date, pos.adjusted_timestamp, pos.modified_date        from ASSET a
        join ASSET_LINK al on a.id = al.asset_id
        join LRM_POSITION pos on al.position_id = pos.id
        join PROPERTY p on a.asset_type_id = p.asset_type_id and p.public_id = 'rajoitus'
        join SINGLE_CHOICE_VALUE s on s.asset_id = a.id and s.property_id = p.id
        join ENUMERATED_VALUE e on s.enumerated_value_id = e.id
        where a.asset_type_id = 20 and a.id = $id
        """.as[(Long, Long, SideCode, Option[Int], Double, Double, Option[String], Option[DateTime], Option[String], Option[DateTime], Long, Option[String])].firstOption

    speedLimit.map { case (id, linkId, sideCode, value, startMeasure, endMeasure, modifiedBy, modifiedDate, createdBy, createdDate, vvhTimeStamp, vvhModifiedDate) =>
      PersistedSpeedLimit(id, linkId, sideCode, value, startMeasure, endMeasure, modifiedBy, modifiedDate, createdBy, createdDate, vvhTimeStamp, vvhModifiedDate)
    }
  }

  /**
    * Returns details of speed limit by asset id. Used only in unit tests (OracleLinearAssetDaoSpec).
    */
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

  /**
    * Returns m-values and side code by asset id. Used by OracleLinearAssetDao.splitSpeedLimit.
    */
  def getLinkGeometryData(id: Long): (Double, Double, SideCode) = {
    sql"""
      select lrm.START_MEASURE, lrm.END_MEASURE, lrm.SIDE_CODE
        from asset a
        join asset_link al on a.ID = al.ASSET_ID
        join lrm_position lrm on lrm.id = al.POSITION_ID
        where a.id = $id
    """.as[(Double, Double, SideCode)].first
  }

  /**
    * Creates new speed limit with municipality validation. Returns id of new speed limit.
    * Used by SpeedLimitService.create.
    */
  def createSpeedLimit(creator: String, linkId: Long, linkMeasures: (Double, Double), sideCode: SideCode, value: Int,  municipalityValidation: (Int) => Unit): Option[Long] = {
    municipalityValidation(vvhClient.fetchVVHRoadlink(linkId).get.municipalityCode)
    createSpeedLimitWithoutDuplicates(creator, linkId, linkMeasures, sideCode, value, None, None, None, None)
  }

  /**
    * Creates new speed limit. Returns id of new speed limit. SpeedLimitService.persistProjectedLimit and SpeedLimitService.separate.
    */
  def createSpeedLimit(creator: String, linkId: Long, linkMeasures: (Double, Double), sideCode: SideCode, value: Int, vvhTimeStamp: Option[Long], createdDate: Option[DateTime] = None, modifiedBy: Option[String] = None, modifiedAt: Option[DateTime] = None) =
    createSpeedLimitWithoutDuplicates(creator, linkId, linkMeasures, sideCode, value, vvhTimeStamp, createdDate, modifiedBy, modifiedAt)

  /**
    * Saves enumerated value to db. Used by OracleLinearAssetDao.createSpeedLimitWithoutDuplicates and AssetDataImporter.splitSpeedLimits.
    * Used as a parameter for OracleLinearAssetDao.forceCreateLinearAsset.
    */
  def insertEnumeratedValue(assetId: Long, valuePropertyId: String, value: Int) = {
    val propertyId = Q.query[String, Long](Queries.propertyIdByPublicId).apply(valuePropertyId).first
    sqlu"""
       insert into single_choice_value(asset_id, enumerated_value_id, property_id, modified_date)
       values ($assetId, (select id from enumerated_value where property_id = $propertyId and value = $value), $propertyId, current_timestamp)
     """.execute
  }

  /**
    * Saves number property value to db. Used by LinearAssetService.createWithoutTransaction and AssetDataImporter.splitLinearAssets.
    */
  def insertValue(assetId: Long, valuePropertyId: String, value: Int) = {
    val numberPropertyValueId = Sequences.nextPrimaryKeySeqValue
    val propertyId = Q.query[String, Long](Queries.propertyIdByPublicId).apply(valuePropertyId).first
    sqlu"""
       insert into number_property_value(id, asset_id, property_id, value)
       values ($numberPropertyValueId, $assetId, $propertyId, $value)
     """.execute
  }

  /**
    * Saves textual property value to db. Used by LinearAssetService.createWithoutTransaction.
    */
  def insertValue(assetId: Long, valuePropertyId: String, value: String) = {
    val propertyId = Q.query[String, Long](Queries.propertyIdByPublicId).apply(valuePropertyId).first
    Queries.insertTextProperty(assetId, propertyId, value).execute
  }

  /**
    * Saves linear asset to db. Returns id of new linear asset. Used by OracleLinearAssetDao.createSpeedLimitWithoutDuplicates,
    * AssetDataImporter.splitSpeedLimits and AssetDataImporter.splitLinearAssets.
    */
  def forceCreateLinearAsset(creator: String, typeId: Int, linkId: Long, linkMeasures: (Double, Double), sideCode: SideCode, value: Option[Int], valueInsertion: (Long, Int) => Unit, vvhTimeStamp: Option[Long], createdDate: Option[DateTime], modifiedBy: Option[String], modifiedAt: Option[DateTime]): Long = {
    val (startMeasure, endMeasure) = linkMeasures
    val assetId = Sequences.nextPrimaryKeySeqValue
    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
    val sideCodeValue = sideCode.value

    val creationDate = createdDate match {
      case Some(datetime) => s"""TO_TIMESTAMP_TZ('$datetime', 'YYYY-MM-DD"T"HH24:MI:SS.FF3TZH:TZM')"""
      case None => "sysdate"
    }

    val modifiedDate = modifiedAt match {
      case Some(datetime) => s"""TO_TIMESTAMP_TZ('$datetime', 'YYYY-MM-DD"T"HH24:MI:SS.FF3TZH:TZM')"""
      case None => "NULL"
    }

    val insertAll =
      s"""
       insert all
         into asset(id, asset_type_id, created_by, created_date, modified_by, modified_date)
         values ($assetId, $typeId, '$creator', $creationDate, ${modifiedBy.getOrElse("NULL")}, $modifiedDate)

         into lrm_position(id, start_measure, end_measure, link_id, side_code, adjusted_timestamp, modified_date)
         values ($lrmPositionId, $startMeasure, $endMeasure, $linkId, $sideCodeValue, ${vvhTimeStamp.getOrElse(0)}, CURRENT_TIMESTAMP)

         into asset_link(asset_id, position_id)
         values ($assetId, $lrmPositionId)
       select * from dual
      """
    Q.updateNA(insertAll).execute

    value.foreach(valueInsertion(assetId, _))

    assetId
  }
  
  private def createSpeedLimitWithoutDuplicates(creator: String, linkId: Long, linkMeasures: (Double, Double), sideCode: SideCode, value: Int, vvhTimeStamp: Option[Long], createdDate: Option[DateTime], modifiedBy: Option[String], modifiedAt: Option[DateTime]): Option[Long] = {
    val (startMeasure, endMeasure) = linkMeasures
    val existingLrmPositions = fetchSpeedLimitsByLinkId(linkId).filter(sl => sideCode == SideCode.BothDirections || sl._3 == sideCode).map { case(_, _, _, _, start, end, _, _) => (start, end) }
    val remainders = existingLrmPositions.foldLeft(Seq((startMeasure, endMeasure)))(GeometryUtils.subtractIntervalFromIntervals).filter { case (start, end) => math.abs(end - start) > 0.01}
    if (remainders.length == 1) {
      Some(forceCreateLinearAsset(creator, 20, linkId, linkMeasures, sideCode, Some(value), (id, value) => insertEnumeratedValue(id, "rajoitus", value), vvhTimeStamp, createdDate, modifiedBy, modifiedAt))
    } else {
      None
    }
  }

  /**
    * Updates m-values in db. Used by OracleLinearAssetDao.splitSpeedLimit, LinearAssetService.persistMValueAdjustments and LinearAssetService.split.
    */
  def updateMValues(id: Long, linkMeasures: (Double, Double)): Unit = {
    val (startMeasure, endMeasure) = linkMeasures
    sqlu"""
      update LRM_POSITION
      set
        start_measure = $startMeasure,
        end_measure = $endMeasure,
        modified_date = CURRENT_TIMESTAMP
      where id = (
        select lrm.id
          from asset a
          join asset_link al on a.ID = al.ASSET_ID
          join lrm_position lrm on lrm.id = al.POSITION_ID
          where a.id = $id)
    """.execute
  }

  /**
    * Updates m-values and vvh time stamp in db. Used by OracleLinearAssetDao.splitSpeedLimit, LinearAssetService.persistMValueAdjustments and LinearAssetService.split.
    */
  def updateMValues(id: Long, linkMeasures: (Double, Double), vvhTimeStamp: Long): Unit = {
    val (startMeasure, endMeasure) = linkMeasures
    sqlu"""
      update LRM_POSITION
      set
        start_measure = $startMeasure,
        end_measure = $endMeasure,
        adjusted_timestamp = $vvhTimeStamp,
        modified_date = CURRENT_TIMESTAMP
      where id = (
        select lrm.id
          from asset a
          join asset_link al on a.ID = al.ASSET_ID
          join lrm_position lrm on lrm.id = al.POSITION_ID
          where a.id = $id)
    """.execute
  }

  /**
    * Updates side codes in db. Used by SpeedLimitService.separate, LinearAssetService.persistSideCodeAdjustments and LinearAssetService.separate.
    */
  def updateSideCode(id: Long, sideCode: SideCode): Unit = {
    val sideCodeValue = sideCode.value
    sqlu"""
      update LRM_POSITION
      set
        side_code = $sideCodeValue,
        modified_date = CURRENT_TIMESTAMP
      where id = (
        select lrm.id
          from asset a
          join asset_link al on a.ID = al.ASSET_ID
          join lrm_position lrm on lrm.id = al.POSITION_ID
          where a.id = $id)
    """.execute
  }

  /**
    * Splits speed limit by given split measure. Updates old asset and creates new asset. Returns new asset id.
    * Used by SpeedLimitService.split.
    */
  def splitSpeedLimit(id: Long, splitMeasure: Double, value: Int, username: String, municipalityValidation: (Int) => Unit): Long = {
    def withMunicipalityValidation(vvhLinks: Seq[(Long, Double, Seq[Point], Int)]) = {
      vvhLinks.foreach(vvhLink => municipalityValidation(vvhLink._4))
      vvhLinks
    }

    val (startMeasure, endMeasure, sideCode) = getLinkGeometryData(id)
    val link: (Long, Double, (Point, Point)) =
      withMunicipalityValidation(getLinksWithLengthFromVVH(20, id)).headOption.map { case (linkId, length, geometry, _) =>
        (linkId, length, GeometryUtils.geometryEndpoints(geometry))
      }.get

    Queries.updateAssetModified(id, username).execute
    val (existingLinkMeasures, createdLinkMeasures) = GeometryUtils.createSplit(splitMeasure, (startMeasure, endMeasure))

    updateMValues(id, existingLinkMeasures)
    val createdId = createSpeedLimitWithoutDuplicates(username, link._1, createdLinkMeasures, sideCode, value, None, None, None, None).get
    createdId
  }

  /**
    * Updates speed limit value. Used by SpeedLimitService.updateValues, SpeedLimitService.split and SpeedLimitService.separate.
    */
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

  /**
    * Sets floating flag of linear assets true in db. Used in LinearAssetService.drop and AssetDataImporter.splitSpeedLimits.
    */
  def floatLinearAssets(ids: Set[Long]): Unit = {
    if (ids.nonEmpty) {
      MassQuery.withIds(ids) { idTableName =>
        sqlu"""update asset set floating = 1 where id in (select id from #$idTableName)""".execute
      }
    }
  }

  /**
    * Updates validity of asset in db. Used by LinearAssetService.expire, LinearAssetService.split and LinearAssetService.separate.
    */
  def updateExpiration(id: Long, expired: Boolean, username: String) = {
    val assetsUpdated = Queries.updateAssetModified(id, username).first
    val propertiesUpdated = if (expired) {
      sqlu"update asset set valid_to = sysdate where id = $id".first
    } else {
      sqlu"update asset set valid_to = null where id = $id".first
    }
    if (assetsUpdated == 1 && propertiesUpdated == 1) {
      Some(id)
    } else {
      None
    }
  }

  /**
    * Creates new linear asset. Return id of new asset. Used by LinearAssetService.createWithoutTransaction
    */
  def createLinearAsset(typeId: Int, linkId: Long, expired: Boolean, sideCode: Int, startMeasure: Double, endMeasure: Double, username: String): Long  = {
    val id = Sequences.nextPrimaryKeySeqValue
    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
    val validTo = if(expired) "sysdate" else "null"
    sqlu"""
      insert all
        into asset(id, asset_type_id, created_by, created_date, valid_to)
        values ($id, $typeId, $username, sysdate, #$validTo)

        into lrm_position(id, start_measure, end_measure, link_id, side_code, modified_date)
        values ($lrmPositionId, $startMeasure, $endMeasure, $linkId, $sideCode, CURRENT_TIMESTAMP)

        into asset_link(asset_id, position_id)
        values ($id, $lrmPositionId)
      select * from dual
    """.execute

    id
  }

  /**
    * Updates number property value. Used by LinearAssetService.updateWithoutTransaction.
    */
  def updateValue(id: Long, value: Int, valuePropertyId: String, username: String): Option[Long] = {
    val propertyId = Q.query[String, Long](Queries.propertyIdByPublicId).apply(valuePropertyId).first
    val assetsUpdated = Queries.updateAssetModified(id, username).first
    val propertiesUpdated =
      sqlu"update number_property_value set value = $value where asset_id = $id and property_id = $propertyId".first
    if (assetsUpdated == 1 && propertiesUpdated == 1) {
      Some(id)
    } else {
      None
    }
  }

  /**
    * Updates textual property value. Used by LinearAssetService.updateWithoutTransaction.
    */
  def updateValue(id: Long, value: String, valuePropertyId: String, username: String): Option[Long] = {
    val propertyId = Q.query[String, Long](Queries.propertyIdByPublicId).apply(valuePropertyId).first
    val assetsUpdated = Queries.updateAssetModified(id, username).first
    val propertiesUpdated = Queries.updateTextProperty(id, propertyId, value).first
    if (assetsUpdated == 1 && propertiesUpdated == 1) {
      Some(id)
    } else {
      None
    }
  }

  /**
    *  Updates prohibition value. Used by LinearAssetService.updateWithoutTransaction.
    */
  def updateProhibitionValue(id: Long, value: Prohibitions, username: String): Option[Long] = {
    Queries.updateAssetModified(id, username).first

    val prohibitionValueIds = sql"""select id from PROHIBITION_VALUE where asset_id = $id""".as[Int].list.mkString(",")
    if (prohibitionValueIds.nonEmpty) {
      sqlu"""delete from PROHIBITION_EXCEPTION where prohibition_value_id in (#$prohibitionValueIds)""".execute
      sqlu"""delete from PROHIBITION_VALIDITY_PERIOD where prohibition_value_id in (#$prohibitionValueIds)""".execute
      sqlu"""delete from PROHIBITION_VALUE where asset_id = $id""".execute
    }

    insertProhibitionValue(id, value)
    Some(id)
  }

  /**
    * Saves prohibition value to db. Used by OracleLinearAssetDao.updateProhibitionValue and LinearAssetService.createWithoutTransaction.
    */
  def insertProhibitionValue(assetId: Long, value: Prohibitions): Unit = {
    value.prohibitions.foreach { (prohibition: ProhibitionValue) =>
      val prohibitionId = Sequences.nextPrimaryKeySeqValue
      val prohibitionType = prohibition.typeId
      sqlu"""insert into PROHIBITION_VALUE (ID, ASSET_ID, TYPE) values ($prohibitionId, $assetId, $prohibitionType)""".first

      prohibition.validityPeriods.foreach { validityPeriod =>
        val validityId = Sequences.nextPrimaryKeySeqValue
        val startHour = validityPeriod.startHour
        val endHour = validityPeriod.endHour
        val daysOfWeek = validityPeriod.days.value
        sqlu"""insert into PROHIBITION_VALIDITY_PERIOD (ID, PROHIBITION_VALUE_ID, TYPE, START_HOUR, END_HOUR)
               values ($validityId, $prohibitionId, $daysOfWeek, $startHour, $endHour)""".execute
      }
      prohibition.exceptions.foreach { exceptionType =>
        val exceptionId = Sequences.nextPrimaryKeySeqValue
        sqlu""" insert into PROHIBITION_EXCEPTION (ID, PROHIBITION_VALUE_ID, TYPE) values ($exceptionId, $prohibitionId, $exceptionType)""".execute
      }
    }
  }
}

