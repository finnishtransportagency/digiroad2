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
                               vvhTimeStamp: Long, geomModifiedDate: Option[DateTime], expired: Boolean = false, linkSource: LinkGeomSource)

case class ProhibitionsRow(id: Long, linkId: Long, sideCode: Int, prohibitionId: Long, prohibitionType: Int, validityPeriodType: Option[Int],
                           startHour: Option[Int], endHour: Option[Int], exceptionType: Option[Int], startMeasure: Double,
                           endMeasure: Double, createdBy: Option[String], createdDate: Option[DateTime], modifiedBy: Option[String], modifiedDate: Option[DateTime],
                           expired: Boolean, vvhTimeStamp: Long, geomModifiedDate: Option[DateTime], startMinute: Option[Int], endMinute: Option[Int],
                           additionalInfo: String, linkSource: Int, verifiedBy: Option[String], verifiedDate: Option[DateTime])

class OracleLinearAssetDao(val vvhClient: VVHClient, val roadLinkService: RoadLinkService ) {

  def MassQueryThreshold = 500
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
      val limitEndPoints = speedLimits.filter(sl => sl._3 == SideCode.BothDirections || sl._3 == sideCode).map { case(_, _, _, _, start, end, _, _, _) => (start, end) }
      limitEndPoints.foldLeft(Seq((0.0, roadLinkLength)))(GeometryUtils.subtractIntervalFromIntervals).filter { case (start, end) => math.abs(end - start) > 0.1}
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

  implicit val getProhibitionsRow = new GetResult[ProhibitionsRow] {
    def apply(r: PositionedResult) = {

      val id = r.nextLong()
      val linkId = r.nextLong()
      val sideCode = r.nextInt()
      val prohibitionId = r.nextLong()
      val prohibitionType = r.nextInt()
      val validityPeridoType = r.nextIntOption()
      val validityPeridoStartHour = r.nextIntOption()
      val validityPeridoEndHour = r.nextIntOption()
      val exceptionType = r.nextIntOption()
      val startMeasure = r.nextDouble()
      val endMeasure = r.nextDouble()
      val createdBy = r.nextStringOption()
      val createdDate = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val modifiedBy = r.nextStringOption()
      val modifiedDate = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val expired = r.nextBoolean
      val vvhTimeStamp = r.nextLong()
      val geomModifiedDate = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val validityPeridoStartMinute = r.nextIntOption()
      val validityPeridoEndMinute = r.nextIntOption()
      val prohibitionAdditionalInfo = r.nextString
      val linkSource = r.nextInt()
      val verifiedBy = r.nextStringOption()
      val verifiedDate = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))

      ProhibitionsRow(id, linkId, sideCode, prohibitionId, prohibitionType, validityPeridoType, validityPeridoStartHour, validityPeridoEndHour, exceptionType, startMeasure, endMeasure, createdBy, createdDate, modifiedBy, modifiedDate, expired, vvhTimeStamp, geomModifiedDate, validityPeridoStartMinute, validityPeridoEndMinute, prohibitionAdditionalInfo, linkSource, verifiedBy, verifiedDate)

    }
  }

  /**
    * Returns data for municipality validation. Used by OracleLinearAssetDao.splitSpeedLimit and OracleLinearAssetDao.updateSpeedLimitValue.
    */
  def getLinksWithLengthFromVVH(assetTypeId: Int, id: Long): Seq[(Long, Double, Seq[Point], Int, LinkGeomSource)] = {
    val links = sql"""
      select pos.link_id, pos.start_measure, pos.end_measure
      from ASSET a
        join ASSET_LINK al on a.id = al.asset_id
        join LRM_POSITION pos on al.position_id = pos.id
        where a.asset_type_id = $assetTypeId and a.id = $id
        """.as[(Long, Double, Double)].list

    val roadLinksByLinkId = roadLinkService.fetchVVHRoadlinksAndComplementary(links.map(_._1).toSet)

    links.map { case (linkId, startMeasure, endMeasure) =>
      val vvhRoadLink = roadLinksByLinkId.find(_.linkId == linkId).getOrElse(throw new NoSuchElementException)
      val truncatedGeometry = GeometryUtils.truncateGeometry3D(vvhRoadLink.geometry, startMeasure, endMeasure)
      (linkId, endMeasure - startMeasure, truncatedGeometry, vvhRoadLink.municipalityCode, vvhRoadLink.linkSource)
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
           where a.asset_type_id = 20 and floating = 0 AND
           (valid_to IS NULL OR valid_to > SYSDATE) """.as[(Long, Long, SideCode, Option[Int], Double, Double, Option[String], Option[DateTime], Option[String], Option[DateTime], Long, Option[DateTime])].list
    }
  }

  private def fetchHistorySpeedLimitsByLinkIds(linkIds: Seq[Long]) = {
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
           where a.asset_type_id = 20 and floating = 0 AND
           (valid_to IS NOT NULL AND valid_to < SYSDATE) """.as[(Long, Long, SideCode, Option[Int], Double, Double, Option[String], Option[DateTime], Option[String], Option[DateTime], Long, Option[DateTime])].list
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
               pos.adjusted_timestamp, pos.modified_date, pos.link_source, a.verified_by, a.verified_date
          from asset a
          join asset_link al on a.id = al.asset_id
          join lrm_position pos on al.position_id = pos.id
          join property p on p.public_id = $valuePropertyId
          join #$idTableName i on i.id = a.id
          left join number_property_value s on s.asset_id = a.id and s.property_id = p.id
          where a.floating = 0
      """.as[(Long, Long, Int, Option[Int], Double, Double, Option[String], Option[DateTime], Option[String], Option[DateTime], Boolean, Int, Long, Option[DateTime], Int, Option[String], Option[DateTime])].list
      assets.map { case (id, linkId, sideCode, value, startMeasure, endMeasure, createdBy, createdDate, modifiedBy, modifiedDate, expired, typeId, vvhTimeStamp, geomModifiedDate, linkSource, verifiedBy, verifiedDate) =>
        PersistedLinearAsset(id, linkId, sideCode, value.map(NumericValue), startMeasure, endMeasure, createdBy, createdDate, modifiedBy, modifiedDate, expired, typeId, vvhTimeStamp, geomModifiedDate, LinkGeomSource.apply(linkSource), verifiedBy, verifiedDate)
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
               pos.adjusted_timestamp, pos.modified_date, pos.link_source, a.verified_by, a.verified_date
          from asset a
          join asset_link al on a.id = al.asset_id
          join lrm_position pos on al.position_id = pos.id
          join property p on p.public_id = $valuePropertyId
          join #$idTableName i on i.id = a.id
          left join text_property_value s on s.asset_id = a.id and s.property_id = p.id
          where a.floating = 0
      """.as[(Long, Long, Int, Option[String], Double, Double, Option[String], Option[DateTime], Option[String], Option[DateTime], Boolean, Int, Long, Option[DateTime], Int, Option[String], Option[DateTime])].list
      assets.map { case (id, linkId, sideCode, value, startMeasure, endMeasure, createdBy, createdDate, modifiedBy, modifiedDate, expired, typeId, vvhTimeStamp, geomModifiedDate, linkSource, verifiedBy, verifiedDate) =>
        PersistedLinearAsset(id, linkId, sideCode, value.map(TextualValue), startMeasure, endMeasure, createdBy, createdDate, modifiedBy, modifiedDate, expired, typeId, vvhTimeStamp, geomModifiedDate, LinkGeomSource.apply(linkSource), verifiedBy, verifiedDate)
      }
    }
  }

  /**
    * Iterates a set of link ids with asset type id and property id and returns linear assets. Used by LinearAssetService.getByRoadLinks.
    */
  def fetchLinearAssetsByLinkIds(assetTypeId: Int, linkIds: Seq[Long], valuePropertyId: String, includeExpired: Boolean = false): Seq[PersistedLinearAsset] = {
    val filterExpired = if (includeExpired) "" else " and (a.valid_to >= sysdate or a.valid_to is null)"
    MassQuery.withIds(linkIds.toSet) { idTableName =>
      val assets = sql"""
        select a.id, pos.link_id, pos.side_code, s.value as total_weight_limit, pos.start_measure, pos.end_measure,
               a.created_by, a.created_date, a.modified_by, a.modified_date,
               case when a.valid_to <= sysdate then 1 else 0 end as expired, a.asset_type_id,
               pos.adjusted_timestamp, pos.modified_date, pos.link_source, a.verified_by, a.verified_date
          from asset a
          join asset_link al on a.id = al.asset_id
          join lrm_position pos on al.position_id = pos.id
          join property p on p.public_id = $valuePropertyId
          join #$idTableName i on i.id = pos.link_id
          left join number_property_value s on s.asset_id = a.id and s.property_id = p.id
          where a.asset_type_id = $assetTypeId
          and a.floating = 0
          #$filterExpired"""
        .as[(Long, Long, Int, Option[Int], Double, Double, Option[String], Option[DateTime], Option[String], Option[DateTime], Boolean, Int, Long, Option[DateTime], Int, Option[String], Option[DateTime])].list
      assets.map { case(id, linkId, sideCode, value, startMeasure, endMeasure, createdBy, createdDate, modifiedBy, modifiedDate, expired, typeId, vvhTimeStamp, geomModifiedDate, linkSource, verifiedBy, verifiedDate) =>
        PersistedLinearAsset(id, linkId, sideCode, value.map(NumericValue), startMeasure, endMeasure, createdBy, createdDate, modifiedBy, modifiedDate, expired, typeId, vvhTimeStamp, geomModifiedDate, LinkGeomSource.apply(linkSource), verifiedBy, verifiedDate)
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
               case when a.valid_to <= sysdate then 1 else 0 end as expired, a.asset_type_id,
               pos.adjusted_timestamp, pos.modified_date, pos.link_source, a.verified_by, a.verified_date
          from asset a
          join asset_link al on a.id = al.asset_id
          join lrm_position pos on al.position_id = pos.id
          join property p on p.public_id = $valuePropertyId
          join #$idTableName i on i.id = pos.link_id
          left join text_property_value s on s.asset_id = a.id and s.property_id = p.id
          where a.asset_type_id = $assetTypeId
          and (a.valid_to >= sysdate or a.valid_to is null)
          and a.floating = 0"""
        .as[(Long, Long, Int, Option[String], Double, Double, Option[String], Option[DateTime], Option[String], Option[DateTime], Boolean, Int, Long, Option[DateTime], Int, Option[String], Option[DateTime])].list
      assets.map { case(id, linkId, sideCode, value, startMeasure, endMeasure, createdBy, createdDate, modifiedBy, modifiedDate, expired, typeId, vvhTimeStamp, geomModifiedDate, linkSource, verifiedBy, verifiedDate) =>
        PersistedLinearAsset(id, linkId, sideCode, value.map(TextualValue), startMeasure, endMeasure, createdBy, createdDate, modifiedBy, modifiedDate, expired, typeId, vvhTimeStamp, geomModifiedDate, LinkGeomSource.apply(linkSource), verifiedBy, verifiedDate)
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
               pos.adjusted_timestamp, pos.modified_date, pvp.start_minute,
               pvp.end_minute, pv.additional_info, pos.link_source, a.verified_by, a.verified_date
          from asset a
          join asset_link al on a.id = al.asset_id
          join lrm_position pos on al.position_id = pos.id
          join prohibition_value pv on pv.asset_id = a.id
          join #$idTableName i on i.id = pos.link_id
          left join prohibition_validity_period pvp on pvp.prohibition_value_id = pv.id
          left join prohibition_exception pe on pe.prohibition_value_id = pv.id
          where a.asset_type_id = $prohibitionAssetTypeId
          and (a.valid_to >= sysdate or a.valid_to is null)
          #$floatingFilter""".as[ProhibitionsRow].list
    }

    val groupedByAssetId = assets.groupBy(_.id)
    val groupedByProhibitionId = groupedByAssetId.mapValues(_.groupBy(_.prohibitionId))

    groupedByProhibitionId.map { case (assetId, rowsByProhibitionId) =>
      val asset = groupedByAssetId(assetId).head
      val prohibitionValues = rowsByProhibitionId.keys.toSeq.sorted.map { prohibitionId =>
        val rows = rowsByProhibitionId(prohibitionId)
        val prohibitionType = rows.head.prohibitionType
        val prohibitionAdditionalInfo = rows.head.additionalInfo
        val exceptions = rows.flatMap(_.exceptionType).toSet
        val validityPeriods = rows.filter(_.validityPeriodType.isDefined).map { case row =>
          ValidityPeriod(row.startHour.get, row.endHour.get, ValidityPeriodDayOfWeek(row.validityPeriodType.get), row.startMinute.get, row.endMinute.get)
        }.toSet
        ProhibitionValue(prohibitionType, validityPeriods, exceptions, prohibitionAdditionalInfo)
      }
      PersistedLinearAsset(assetId, asset.linkId, asset.sideCode, Some(Prohibitions(prohibitionValues)), asset.startMeasure, asset.endMeasure, asset.createdBy,
        asset.createdDate, asset.modifiedBy, asset.modifiedDate, asset.expired, prohibitionAssetTypeId, asset.vvhTimeStamp, asset.geomModifiedDate, LinkGeomSource.apply(asset.linkSource),
        asset.verifiedBy, asset.verifiedDate)
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
               case when a.valid_to <= sysdate then 1 else 0 end as expired,
               pos.adjusted_timestamp, pos.modified_date, pvp.start_minute,
               pvp.end_minute, pv.additional_info, pos.link_source,
               a.verified_by, a.verified_date
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
        .as[ProhibitionsRow].list
    }

    val groupedByAssetId = assets.groupBy(_.id)
    val groupedByProhibitionId = groupedByAssetId.mapValues(_.groupBy(_.prohibitionId))

    groupedByProhibitionId.map { case (assetId, rowsByProhibitionId) =>
      val asset = groupedByAssetId(assetId).head
      val prohibitionValues = rowsByProhibitionId.keys.toSeq.sorted.map { prohibitionId =>
        val rows = rowsByProhibitionId(prohibitionId)
        val prohibitionType = rows.head.prohibitionType
        val exceptions = rows.flatMap(_.exceptionType).toSet
        val prohibitionAdditionalInfo = rows.head.additionalInfo
        val validityPeriods = rows.filter(_.validityPeriodType.isDefined).map { case row =>
          ValidityPeriod(row.startHour.get, row.endHour.get, ValidityPeriodDayOfWeek(row.validityPeriodType.get), row.startMinute.get, row.endMinute.get)
        }.toSet
        ProhibitionValue(prohibitionType, validityPeriods, exceptions, prohibitionAdditionalInfo)
      }
      PersistedLinearAsset(assetId, asset.linkId, asset.sideCode, Some(Prohibitions(prohibitionValues)), asset.startMeasure, asset.endMeasure, asset.createdBy,
        asset.createdDate, asset.modifiedBy, asset.modifiedDate, asset.expired, prohibitionAssetTypeId, asset.vvhTimeStamp, asset.geomModifiedDate, LinkGeomSource.apply(asset.linkSource),
        asset.verifiedBy, asset.verifiedDate)
    }.toSeq
  }

  private def fetchSpeedLimitsByLinkId(linkId: Long) = {
    sql"""
      select a.id, pos.link_id, pos.side_code, e.value, pos.start_measure, pos.end_measure, pos.adjusted_timestamp, pos.modified_date, pos.link_source
         from asset a
         join asset_link al on a.id = al.asset_id
         join lrm_position pos on al.position_id = pos.id
         join property p on a.asset_type_id = p.asset_type_id and p.public_id = 'rajoitus'
         join single_choice_value s on s.asset_id = a.id and s.property_id = p.id
         join enumerated_value e on s.enumerated_value_id = e.id
         where a.asset_type_id = 20 and floating = 0 and pos.link_id = $linkId
           and (a.valid_to > sysdate or a.valid_to is null) """.as[(Long, Long, SideCode, Option[Int], Double, Double, Long, Option[String], Int)].list
  }

  /**
    * Returns only car traffic roads as a topology and speed limits that match these road links.
    * Used by SpeedLimitService.get (by bounding box and a list of municipalities) and SpeedLimitService.get (by municipality)
    */
  def getSpeedLimitLinksByRoadLinks(roadLinks: Seq[RoadLink], showSpeedLimitsHistory: Boolean = false): (Seq[SpeedLimit],  Seq[RoadLink]) = {
    val topology = roadLinks.filter(_.isCarTrafficRoad)
    var speedLimitLinks : Seq[SpeedLimit] = Seq()
    if (showSpeedLimitsHistory){
      speedLimitLinks = fetchHistorySpeedLimitsByLinkIds(topology.map(_.linkId)).map(createGeometryForSegment(topology))
    }else{
      speedLimitLinks = fetchSpeedLimitsByLinkIds(topology.map(_.linkId)).map(createGeometryForSegment(topology))
    }
    (speedLimitLinks, topology)
  }

  def getSpeedLimitsChangedSince(sinceDate: DateTime, untilDate: DateTime) = {
    val speedLimits = sql"""
        select a.id, pos.link_id, pos.side_code, e.value, pos.start_measure, pos.end_measure, a.modified_by, a.modified_date, a.created_by, a.created_date, pos.adjusted_timestamp, pos.modified_date,
               case when a.valid_to <= sysdate then 1 else 0 end as expired, pos.link_source
         from asset a
         join asset_link al on a.id = al.asset_id
         join lrm_position pos on al.position_id = pos.id
         join property p on a.asset_type_id = p.asset_type_id and p.public_id = 'rajoitus'
         join single_choice_value s on s.asset_id = a.id and s.property_id = p.id
         join enumerated_value e on s.enumerated_value_id = e.id
         where
         a.asset_type_id = 20
         and (a.modified_by is null or a.modified_by != 'vvh_generated')
         and floating = 0
         and (
           (a.valid_to > $sinceDate and a.valid_to <= $untilDate)
           or
           (a.modified_date > $sinceDate and a.modified_date <= $untilDate)
           or
           (a.created_date > $sinceDate and a.created_date <= $untilDate)
         )
    """.as[(Long, Long, SideCode, Option[Int], Double, Double, Option[String], Option[DateTime], Option[String], Option[DateTime], Long, Option[DateTime], Boolean, Int)].list

    speedLimits.map { case (id, linkId, sideCode, value, startMeasure, endMeasure, modifiedBy, modifiedDate, createdBy, createdDate, vvhTimeStamp, geomModifiedDate, expired, linkSource) =>
      PersistedSpeedLimit(id, linkId, sideCode, value, startMeasure, endMeasure, modifiedBy, modifiedDate, createdBy, createdDate, vvhTimeStamp , geomModifiedDate, expired, LinkGeomSource.apply(linkSource))
    }
  }

  def getLinearAssetsChangedSince(assetTypeId: Int, sinceDate: DateTime, untilDate: DateTime) = {
    val assets = sql"""
        select a.id, pos.link_id, pos.side_code, s.value as total_weight_limit, pos.start_measure, pos.end_measure,
               a.created_by, a.created_date, a.modified_by, a.modified_date,
               case when a.valid_to <= sysdate then 1 else 0 end as expired, a.asset_type_id, pos.adjusted_timestamp,
               pos.modified_date, pos.link_source, a.verified_by, a.verified_date
          from asset a
          join asset_link al on a.id = al.asset_id
          join lrm_position pos on al.position_id = pos.id
          join property p on p.public_id = 'mittarajoitus'
          left join number_property_value s on s.asset_id = a.id and s.property_id = p.id
          where
          a.asset_type_id = $assetTypeId
          and (a.modified_by is null or a.modified_by != 'vvh_generated')
          and (
            (a.valid_to > $sinceDate and a.valid_to <= $untilDate)
            or
            (a.modified_date > $sinceDate and a.modified_date <= $untilDate)
            or
            (a.created_date > $sinceDate and a.created_date <= $untilDate)
          )
          and a.floating = 0"""
      .as[(Long, Long, Int, Option[Int], Double, Double, Option[String], Option[DateTime], Option[String], Option[DateTime], Boolean, Int, Long, Option[DateTime], Int, Option[String], Option[DateTime])].list

    assets.map { case(id, linkId, sideCode, value, startMeasure, endMeasure, createdBy, createdDate, modifiedBy, modifiedDate, expired, typeId, vvhTimeStamp, geomModifiedDate, linkSource, verifiedBy, verifiedDate) =>
      PersistedLinearAsset(id, linkId, sideCode, value.map(NumericValue), startMeasure, endMeasure, createdBy, createdDate, modifiedBy, modifiedDate, expired, typeId, vvhTimeStamp, geomModifiedDate, LinkGeomSource.apply(linkSource), verifiedBy, verifiedDate)
    }
  }

  private def createGeometryForSegment(topology: Seq[RoadLink])(segment: (Long, Long, SideCode, Option[Int], Double, Double, Option[String], Option[DateTime], Option[String], Option[DateTime], Long, Option[DateTime])) = {
    val (assetId, linkId, sideCode, speedLimit, startMeasure, endMeasure, modifiedBy, modifiedDate, createdBy, createdDate, vvhTimeStamp, geomModifiedDate) = segment
    val roadLink = topology.find(_.linkId == linkId).get
    val geometry = GeometryUtils.truncateGeometry3D(roadLink.geometry, startMeasure, endMeasure)
    SpeedLimit(assetId, linkId, sideCode, roadLink.trafficDirection, speedLimit.map(NumericValue), geometry, startMeasure, endMeasure, modifiedBy, modifiedDate, createdBy, createdDate, vvhTimeStamp, geomModifiedDate, linkSource = roadLink.linkSource)
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
        """.as[(Long, Long, SideCode, Option[Int], Double, Double, Option[String], Option[DateTime], Option[String], Option[DateTime], Long, Option[DateTime])].list

    val roadLinksWithComplementaryByLinkId = roadLinkService.fetchVVHRoadlinksAndComplementary(speedLimits.map(_._2).toSet)

    speedLimits.map { case (assetId, linkId, sideCode, value, startMeasure, endMeasure, modifiedBy, modifiedDate, createdBy, createdDate, vvhTimeStamp, geomModifiedDate) =>
      val vvhRoadLink = roadLinksWithComplementaryByLinkId.find(_.linkId == linkId).getOrElse(throw new NoSuchElementException)
      SpeedLimit(assetId, linkId, sideCode, vvhRoadLink.trafficDirection, value.map(NumericValue), GeometryUtils.truncateGeometry3D(vvhRoadLink.geometry, startMeasure, endMeasure), startMeasure, endMeasure, modifiedBy, modifiedDate, createdBy, createdDate, vvhTimeStamp, geomModifiedDate, linkSource = vvhRoadLink.linkSource)
    }
  }

  private def massQueryCurrentSpeedLimitsByLinkIds(ids: Set[Long]): List[SpeedLimit] = {
    val speedLimits = MassQuery.withIds(ids) { idTableName =>
      sql"""select a.id, pos.link_id, pos.side_code, e.value,
            pos.start_measure, pos.end_measure,
            a.modified_by, a.modified_date, a.created_by, a.created_date,
            pos.adjusted_timestamp, pos.modified_date, pos.link_source
        from ASSET a
        join ASSET_LINK al on a.id = al.asset_id
        join LRM_POSITION pos on al.position_id = pos.id
        join PROPERTY p on a.asset_type_id = p.asset_type_id and p.public_id = 'rajoitus'
        join SINGLE_CHOICE_VALUE s on s.asset_id = a.id and s.property_id = p.id
        join ENUMERATED_VALUE e on s.enumerated_value_id = e.id
        join #$idTableName i on (i.id = pos.link_id)
        where a.asset_type_id = 20 AND (a.valid_to IS NULL OR a.valid_to >= SYSDATE ) AND a.floating = 0""".as[
        (Long, Long, SideCode, Option[Int], Double, Double, Option[String], Option[DateTime], Option[String], Option[DateTime], Long, Option[DateTime], Int)
        ].list
    }
    speedLimits.map {
      case (assetId, linkId, sideCode, value, startMeasure, endMeasure, modifiedBy, modifiedDate, createdBy, createdDate, vvhTimeStamp, geomModifiedDate, linkSource) =>
        SpeedLimit(assetId, linkId, sideCode, TrafficDirection.UnknownDirection, value.map(NumericValue), Seq(Point(0.0, 0.0)), startMeasure, endMeasure, modifiedBy, modifiedDate, createdBy, createdDate, vvhTimeStamp, geomModifiedDate, linkSource = LinkGeomSource.apply(linkSource))
    }
  }

  /**
    * Returns speed limits that match a set of link ids. Used by SpeedLimitService.fillNewRoadLinksWithPreviousSpeedLimitData.
    */
  def getCurrentSpeedLimitsByLinkIds(ids: Option[Set[Long]]): List[SpeedLimit] = {
    if (ids.isEmpty) {
      List()
    } else {
      val idSet = ids.get
      if (idSet.size > MassQueryThreshold) {
        massQueryCurrentSpeedLimitsByLinkIds(idSet)
      } else {
        getCurrentSpeedLimitsByLinkIds(idSet)
      }
    }
  }

  private def getCurrentSpeedLimitsByLinkIds(ids: Set[Long]): List[SpeedLimit] = {
    def makeLinkIdSql(s: String) = {
      s.length match {
        case 0 => " and 1=0"
        case _ => s" and pos.link_id in (" + s + ")"
      }
    }

    val idString = ids.mkString(",")
    val sql = "select a.id, pos.link_id, pos.side_code, e.value, pos.start_measure, pos.end_measure, a.modified_by, a.modified_date, a.created_by, a.created_date, pos.adjusted_timestamp, pos.modified_date, pos.link_source " +
      "from ASSET a " +
      "join ASSET_LINK al on a.id = al.asset_id " +
      "join LRM_POSITION pos on al.position_id = pos.id " +
      "join PROPERTY p on a.asset_type_id = p.asset_type_id and p.public_id = 'rajoitus' " +
      "join SINGLE_CHOICE_VALUE s on s.asset_id = a.id and s.property_id = p.id " +
      "join ENUMERATED_VALUE e on s.enumerated_value_id = e.id " +
      "where a.asset_type_id = 20 AND (a.valid_to IS NULL OR a.valid_to >= SYSDATE ) AND a.floating = 0"

    val idSql = sql + makeLinkIdSql(idString)
    Q.queryNA[(Long, Long, SideCode, Option[Int], Double, Double, Option[String], Option[DateTime], Option[String], Option[DateTime], Long, Option[DateTime], Int)](idSql).list.map {
      case (assetId, linkId, sideCode, value, startMeasure, endMeasure, modifiedBy, modifiedDate, createdBy, createdDate, vvhTimeStamp, geomModifiedDate, linkSource) =>
        SpeedLimit(assetId, linkId, sideCode, TrafficDirection.UnknownDirection, value.map(NumericValue), Seq(Point(0.0, 0.0)), startMeasure, endMeasure, modifiedBy, modifiedDate, createdBy, createdDate, vvhTimeStamp, geomModifiedDate, linkSource = LinkGeomSource.apply(linkSource))
    }
  }

  /**
    * Returns speed limit by asset id. Used by SpeedLimitService.separate.
    */
  def getPersistedSpeedLimit(id: Long): Option[PersistedSpeedLimit] = {
    val speedLimit = sql"""
      select a.id, pos.link_id, pos.side_code, e.value, pos.start_measure, pos.end_measure, a.modified_by, a.modified_date, a.created_by, a.created_date, pos.adjusted_timestamp, pos.modified_date, pos.link_source
      from ASSET a
        join ASSET_LINK al on a.id = al.asset_id
        join LRM_POSITION pos on al.position_id = pos.id
        join PROPERTY p on a.asset_type_id = p.asset_type_id and p.public_id = 'rajoitus'
        join SINGLE_CHOICE_VALUE s on s.asset_id = a.id and s.property_id = p.id
        join ENUMERATED_VALUE e on s.enumerated_value_id = e.id
        where a.asset_type_id = 20 and a.id = $id
        """.as[(Long, Long, SideCode, Option[Int], Double, Double, Option[String], Option[DateTime], Option[String], Option[DateTime], Long, Option[DateTime], Int)].firstOption

    speedLimit.map { case (id, linkId, sideCode, value, startMeasure, endMeasure, modifiedBy, modifiedDate, createdBy, createdDate, vvhTimeStamp, geomModifiedDate, linkSource) =>
      PersistedSpeedLimit(id, linkId, sideCode, value, startMeasure, endMeasure, modifiedBy, modifiedDate, createdBy, createdDate, vvhTimeStamp, geomModifiedDate, linkSource = LinkGeomSource.apply(linkSource))
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
    * Returns the municipality code of a Asset by it's Id
 *
    * @param assetId The Id of the Asset
    * @return Type: Int - The Municipality Code
    */
  def getAssetMunicipalityCodeById(assetId: Int): Int = {
    val municipalityCode = sql"""Select municipality_code From asset Where id= $assetId""".as[Int].firstOption.get
    municipalityCode
  }

  /**
    * Returns m-values and side code by asset id. Used by OracleLinearAssetDao.splitSpeedLimit.
    */
  def getLinkGeometryData(id: Long): (Double, Double, SideCode, Long) = {
    sql"""
      select lrm.START_MEASURE, lrm.END_MEASURE, lrm.SIDE_CODE, lrm.ADJUSTED_TIMESTAMP
        from asset a
        join asset_link al on a.ID = al.ASSET_ID
        join lrm_position lrm on lrm.id = al.POSITION_ID
        where a.id = $id
    """.as[(Double, Double, SideCode, Long)].first
  }

  /**
    * Creates new speed limit with municipality validation. Returns id of new speed limit.
    * Used by SpeedLimitService.create.
    */
  def createSpeedLimit(creator: String, linkId: Long, linkMeasures: Measures, sideCode: SideCode, value: Int,
                       vvhTimeStamp: Long, municipalityValidation: (Int) => Unit): Option[Long] = {
    val roadlink = roadLinkService.fetchVVHRoadlinkAndComplementary(linkId)
    municipalityValidation(roadlink.get.municipalityCode)
    createSpeedLimitWithoutDuplicates(creator, linkId, linkMeasures, sideCode, value, None, None, None, None, roadlink.get.linkSource)
  }

  /**
    * Creates new speed limit. Returns id of new speed limit. SpeedLimitService.persistProjectedLimit and SpeedLimitService.separate.
    */
  def createSpeedLimit(creator: String, linkId: Long, linkMeasures: Measures, sideCode: SideCode, value: Int, vvhTimeStamp: Option[Long], createdDate: Option[DateTime] = None, modifiedBy: Option[String] = None, modifiedAt: Option[DateTime] = None, linkSource: LinkGeomSource) =
    createSpeedLimitWithoutDuplicates(creator, linkId, linkMeasures, sideCode, value, vvhTimeStamp, createdDate, modifiedBy, modifiedAt, roadLinkService.fetchVVHRoadlinksAndComplementary(Set(linkId)).map(_.linkSource).head)

  /**
    * Saves enumerated value to db. Used by OracleLinearAssetDao.createSpeedLimitWithoutDuplicates and AssetDataImporter.splitSpeedLimits.
    * Used as a parameter for OracleLinearAssetDao.forceCreateLinearAsset.
    */
  def insertEnumeratedValue(assetId: Long, valuePropertyId: String, value: Int) = {
    val propertyId = Q.query[String, Long](Queries.propertyIdByPublicId).apply(valuePropertyId).first
    sqlu"""
       insert into single_choice_value(asset_id, enumerated_value_id, property_id, modified_date)
       values ($assetId, (select id from enumerated_value where property_id = $propertyId and value = $value), $propertyId, SYSDATE)
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
  def forceCreateLinearAsset(creator: String, typeId: Int, linkId: Long, linkMeasures: Measures, sideCode: SideCode, value: Option[Int], valueInsertion: (Long, Int) => Unit, vvhTimeStamp: Option[Long], createdDate: Option[DateTime], modifiedBy: Option[String], modifiedAt: Option[DateTime], linkSource: LinkGeomSource): Long = {
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
         values ($assetId, $typeId, '$creator', $creationDate, '${modifiedBy.getOrElse("NULL")}', $modifiedDate)

         into lrm_position(id, start_measure, end_measure, link_id, side_code, adjusted_timestamp, modified_date, link_source)
         values ($lrmPositionId, ${linkMeasures.startMeasure}, ${linkMeasures.endMeasure}, $linkId, $sideCodeValue, ${vvhTimeStamp.getOrElse(0)}, SYSDATE, ${linkSource.value})

         into asset_link(asset_id, position_id)
         values ($assetId, $lrmPositionId)
       select * from dual
      """
    Q.updateNA(insertAll).execute

    value.foreach(valueInsertion(assetId, _))

    assetId
  }
  
  private def createSpeedLimitWithoutDuplicates(creator: String, linkId: Long, linkMeasures: Measures, sideCode: SideCode, value: Int, vvhTimeStamp: Option[Long], createdDate: Option[DateTime], modifiedBy: Option[String], modifiedAt: Option[DateTime], linkSource: LinkGeomSource): Option[Long] = {
    val existingLrmPositions = fetchSpeedLimitsByLinkId(linkId).filter(sl => sideCode == SideCode.BothDirections || sl._3 == sideCode)

    val remainders = existingLrmPositions.map { case(_, _, _, _, start, end, _, _, _) => (start, end) }.foldLeft(Seq((linkMeasures.startMeasure, linkMeasures.endMeasure)))(GeometryUtils.subtractIntervalFromIntervals).filter { case (start, end) => math.abs(end - start) > 0.01}
    if (remainders.length == 1) {
      Some(forceCreateLinearAsset(creator, 20, linkId, linkMeasures, sideCode, Some(value), (id, value) => insertEnumeratedValue(id, "rajoitus", value), vvhTimeStamp, createdDate, modifiedBy, modifiedAt, linkSource))
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
        modified_date = SYSDATE
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
        modified_date = SYSDATE
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
        modified_date = SYSDATE
      where id = (
        select lrm.id
          from asset a
          join asset_link al on a.ID = al.ASSET_ID
          join lrm_position lrm on lrm.id = al.POSITION_ID
          where a.id = $id)
    """.execute
  }

  /**
    * Updates asset area in db.
    **/
  def updateArea(assetId: Long, area: Int): Unit = {
    sqlu"""
      update asset
      set area = $area
      where id = $assetId
    """.execute
  }
  /**
    * Splits speed limit by given split measure. Updates old asset and creates new asset. Returns new asset id.
    * Used by SpeedLimitService.split.
    */
  def splitSpeedLimit(id: Long, splitMeasure: Double, value: Int, username: String, municipalityValidation: (Int) => Unit): Long = {
    def withMunicipalityValidation(vvhLinks: Seq[(Long, Double, Seq[Point], Int, LinkGeomSource)]) = {
      vvhLinks.foreach(vvhLink => municipalityValidation(vvhLink._4))
      vvhLinks
    }

    val (startMeasure, endMeasure, sideCode, vvhTimeStamp) = getLinkGeometryData(id)
    val link: (Long, Double, (Point, Point), LinkGeomSource) =
      withMunicipalityValidation(getLinksWithLengthFromVVH(20, id)).headOption.map { case (linkId, length, geometry, _, linkSource) =>
        (linkId, length, GeometryUtils.geometryEndpoints(geometry), linkSource)
      }.get

    Queries.updateAssetModified(id, username).execute
    val (existingLinkMeasures, createdLinkMeasures) = GeometryUtils.createSplit(splitMeasure, (startMeasure, endMeasure))

    updateMValues(id, existingLinkMeasures)
    val createdId = createSpeedLimitWithoutDuplicates(username, link._1, Measures(createdLinkMeasures._1, createdLinkMeasures._2), sideCode, value, Option(vvhTimeStamp), None, None, None, link._4).get
    createdId
  }

  /**
    * Updates speed limit value. Used by SpeedLimitService.updateValues, SpeedLimitService.split and SpeedLimitService.separate.
    */
  def updateSpeedLimitValue(id: Long, value: Int, username: String, municipalityValidation: Int => Unit): Option[Long] = {
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
  def createLinearAsset(typeId: Int, linkId: Long, expired: Boolean, sideCode: Int, measures: Measures, username: String, vvhTimeStamp: Long = 0L, linkSource: Option[Int],
                        fromUpdate: Boolean = false, createdByFromUpdate: Option[String] = Some(""),  createdDateTimeFromUpdate: Option[DateTime] = Some(DateTime.now()),
                        verifiedBy: Option[String] = None): Long = {
    val id = Sequences.nextPrimaryKeySeqValue
    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
    val validTo = if (expired) "sysdate" else "null"
    val verifiedDate = if (verifiedBy.getOrElse("") == "") "null" else "sysdate"

    if (fromUpdate) {
      sqlu"""
      insert all
        into asset(id, asset_type_id, created_by, created_date, valid_to, modified_by, modified_date, verified_by, verified_date)
        values ($id, $typeId, $createdByFromUpdate, $createdDateTimeFromUpdate, #$validTo, $username, sysdate, ${verifiedBy.getOrElse("")}, #$verifiedDate)

        into lrm_position(id, start_measure, end_measure, link_id, side_code, modified_date, adjusted_timestamp, link_source)
        values ($lrmPositionId, ${measures.startMeasure}, ${measures.endMeasure}, $linkId, $sideCode, sysdate, $vvhTimeStamp, $linkSource)

        into asset_link(asset_id, position_id)
        values ($id, $lrmPositionId)
      select * from dual
    """.execute
    } else {
      sqlu"""
      insert all
        into asset(id, asset_type_id, created_by, created_date, valid_to, verified_by, verified_date)
      values ($id, $typeId, $username, sysdate, #$validTo, ${verifiedBy.getOrElse("")}, #$verifiedDate)

      into lrm_position(id, start_measure, end_measure, link_id, side_code, modified_date, adjusted_timestamp, link_source)
      values ($lrmPositionId, ${measures.startMeasure}, ${measures.endMeasure}, $linkId, $sideCode, sysdate, $vvhTimeStamp, $linkSource)

      into asset_link(asset_id, position_id)
      values ($id, $lrmPositionId)
      select * from dual
        """.execute
    }
    id
  }

  /**
    * Updates number property value. Used by LinearAssetService.updateWithoutTransaction.
    */
  def clearValue(id: Long, valuePropertyId: String, username: String): Option[Long] = {
    val propertyId = Q.query[String, Long](Queries.propertyIdByPublicId).apply(valuePropertyId).first
    val assetsUpdated = Queries.updateAssetModified(id, username).first
    val propertiesUpdated =
      sqlu"update number_property_value set value = null where asset_id = $id and property_id = $propertyId".first
    if (assetsUpdated == 1 && propertiesUpdated == 1) {
      Some(id)
    } else {
      None
    }
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
  def updateProhibitionValue(id: Long, value: Prohibitions, username: String, optMeasure: Option[Measures] = None ): Option[Long] = {
    Queries.updateAssetModified(id, username).first

    val prohibitionValueIds = sql"""select id from PROHIBITION_VALUE where asset_id = $id""".as[Int].list.mkString(",")
    if (prohibitionValueIds.nonEmpty) {
      sqlu"""delete from PROHIBITION_EXCEPTION where prohibition_value_id in (#$prohibitionValueIds)""".execute
      sqlu"""delete from PROHIBITION_VALIDITY_PERIOD where prohibition_value_id in (#$prohibitionValueIds)""".execute
      sqlu"""delete from PROHIBITION_VALUE where asset_id = $id""".execute
    }

    insertProhibitionValue(id, value)
    optMeasure match {
      case None => None
      case Some(measure) => updateMValues(id, (measure.startMeasure, measure.endMeasure))
    }
    Some(id)
  }

  def getRequiredProperties(typeId: Int): Map[String, String] ={
    val requiredProperties =
      sql"""select public_id, property_type from property where asset_type_id = $typeId and required = 1""".as[(String, String)].iterator.toMap

    requiredProperties
  }

  /**
    * Saves prohibition value to db. Used by OracleLinearAssetDao.updateProhibitionValue and LinearAssetService.createWithoutTransaction.
    */
  def insertProhibitionValue(assetId: Long, value: Prohibitions): Unit = {
    value.prohibitions.foreach { (prohibition: ProhibitionValue) =>
      val prohibitionId = Sequences.nextPrimaryKeySeqValue
      val prohibitionType = prohibition.typeId
      val additionalInfo = prohibition.additionalInfo
      sqlu"""insert into PROHIBITION_VALUE (ID, ASSET_ID, TYPE, ADDITIONAL_INFO) values ($prohibitionId, $assetId, $prohibitionType, $additionalInfo)""".first

      prohibition.validityPeriods.foreach { validityPeriod =>
        val validityId = Sequences.nextPrimaryKeySeqValue
        val startHour = validityPeriod.startHour
        val endHour = validityPeriod.endHour
        val daysOfWeek = validityPeriod.days.value
        val startMinute = validityPeriod.startMinute
        val endMinute = validityPeriod.endMinute
        sqlu"""insert into PROHIBITION_VALIDITY_PERIOD (ID, PROHIBITION_VALUE_ID, TYPE, START_HOUR, END_HOUR, START_MINUTE, END_MINUTE)
               values ($validityId, $prohibitionId, $daysOfWeek, $startHour, $endHour, $startMinute, $endMinute)""".execute
      }
      prohibition.exceptions.foreach { exceptionType =>
        val exceptionId = Sequences.nextPrimaryKeySeqValue
        sqlu""" insert into PROHIBITION_EXCEPTION (ID, PROHIBITION_VALUE_ID, TYPE) values ($exceptionId, $prohibitionId, $exceptionType)""".execute
      }
    }
  }

  /**
    * When invoked will expire all assets of a given type.
    * It is required that the invoker takes care of the transaction.
 *
    * @param typeId Represets the id of the type given (for example 110 is the typeId used for pavement information)
    */
  def expireAllAssetsByTypeId (typeId: Int): Unit = {
    sqlu"update asset set valid_to = sysdate - 1/86400 where asset_type_id = $typeId".execute
  }

  def getIds (assetType: Int, linkId: Long): Seq[Long] = {
    val ids = sql""" select a.id from asset a
              join asset_link al on (a.id = al.asset_id)
              join lrm_position lp on (al.position_id = lp.id)
              where (a.asset_type_id = $assetType and  lp.link_id = $linkId)""".as[(Long)].list
    ids
  }

  def getAssetLrmPosition(typeId: Long, assetId: Long): Option[( Long, Double, Double)] = {
    val lrmInfo =
      sql"""
          select lrm.link_Id, lrm.start_measure, lrm.end_measure
          from asset a
          join asset_link al on al.asset_id = a.id
          join lrm_position lrm on lrm.id = al.position_id
          where a.asset_type_id = $typeId
          and (a.valid_to IS NULL OR a.valid_to >= SYSDATE )
          and a.floating = 0
          and a.id = $assetId
      """.as[(Long, Double, Double)].firstOption
    lrmInfo
  }

  def getMunicipalityById(id: Long): Seq[Long] = {
    sql"""select id from municipality where id = $id """.as[Long].list
  }

  def updateVerifiedInfo(ids: Set[Long], verifiedBy: String): Unit = {
      sqlu"update asset set verified_by = $verifiedBy, verified_date = sysdate where id in (#${ids.mkString(",")})".execute
  }
}

