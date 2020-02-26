package fi.liikennevirasto.digiroad2.dao.linearasset

import java.util.NoSuchElementException

import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.oracle.MassQuery
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import _root_.oracle.sql.STRUCT
import com.github.tototoshi.slick.MySQLJodaSupport._
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.dao.{Queries, Sequences}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.Measures
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedResult, StaticQuery => Q}

class OracleSpeedLimitDao(val vvhClient: VVHClient, val roadLinkService: RoadLinkService) {
  def MassQueryThreshold = 500
  case class UnknownLimit(linkId: Long, municipality: String, administrativeClass: String)

  implicit object GetByteArray extends GetResult[Array[Byte]] {
    def apply(rs: PositionedResult) = rs.nextBytes()
  }

  implicit object GetSideCode extends GetResult[SideCode] {
    def apply(rs: PositionedResult) = SideCode(rs.nextInt())
  }

  implicit val getSpeedLimit = new GetResult[SpeedLimitRow] {
    def apply(r: PositionedResult) : SpeedLimitRow = {
      val id = r.nextLong()
      val linkId = r.nextLong()
      val sideCode = r.nextInt()
      val value = r.nextIntOption()
      val startMeasure = r.nextDouble()
      val endMeasure = r.nextDouble()
      val modifiedBy = r.nextStringOption()
      val modifiedDateTime = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val expired = r.nextBoolean
      val createdBy = r.nextStringOption()
      val createdDateTime = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val vvhTimeStamp = r.nextLong()
      val geomModifiedDate = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val linkSource = r.nextInt()
      val publicId = r.nextString()

      SpeedLimitRow(id, linkId, SideCode(sideCode), value, startMeasure, endMeasure, modifiedBy, modifiedDateTime, createdBy, createdDateTime, vvhTimeStamp, geomModifiedDate, expired, linkSource = LinkGeomSource(linkSource), publicId)
    }
  }

  implicit val getUnknown = new GetResult[UnknownLimit] {
    def apply(r: PositionedResult) = {
      val linkId = r.nextLong()
      val municipality = r.nextString()
      val administrativeClass = AdministrativeClass(r.nextInt()).toString
      UnknownLimit (linkId, municipality, administrativeClass)
    }
  }

  private def fetchByLinkIds(linkIds: Seq[Long], queryFilter: String) : Seq[PersistedSpeedLimit] = {
    val speedLimitRows = MassQuery.withIds(linkIds.toSet) { idTableName =>
      sql"""
        select a.id, pos.link_id, pos.side_code, e.value, pos.start_measure, pos.end_measure, a.modified_by,
        a.modified_date, case when a.valid_to <= sysdate then 1 else 0 end as expired, a.created_by, a.created_date,
        pos.adjusted_timestamp, pos.modified_date, pos.link_source, p.public_id
           from asset a
           join asset_link al on a.id = al.asset_id
           join lrm_position pos on al.position_id = pos.id
            join  #$idTableName i on i.id = pos.link_id
           join property p on a.asset_type_id = p.asset_type_id
           left join single_choice_value s on s.asset_id = a.id and s.property_id = p.id
           left join multiple_choice_value mc on mc.asset_id = a.id and mc.property_id = p.id and p.property_type = 'checkbox'
           left join enumerated_value e on s.enumerated_value_id = e.id or mc.enumerated_value_id = e.id
		   where a.asset_type_id = 20 and floating = 0 #$queryFilter""".as[SpeedLimitRow].list
    }
    groupSpeedLimitsResult(speedLimitRows)
  }

  def groupSpeedLimitsResult(speedLimitRows: Seq[SpeedLimitRow]) : Seq[PersistedSpeedLimit] = {
    val groupedSpeedLimit = speedLimitRows.groupBy(_.id)
    groupedSpeedLimit.keys.map { assetId =>
      val rows = groupedSpeedLimit(assetId)
      val asset = rows.head

      val speedLimitValue = (rows.find(_.publicId == "suggest_box").head.value.map(_.asInstanceOf[Long]), rows.find(_.publicId == "rajoitus").head.value.map(_.asInstanceOf[Int])) match {
        case (Some(isSuggested), Some(value)) => Some(SpeedLimitValue(value, isSuggested == 1 ))
        case (None, Some(value)) => Some(SpeedLimitValue(value))
        case _ => None
      }

      PersistedSpeedLimit(asset.id, asset.linkId, asset.sideCode, speedLimitValue,
        asset.startMeasure, asset.endMeasure, asset.modifiedBy, asset.modifiedDate, asset.createdBy,
        asset.createdDate, asset.vvhTimeStamp, asset.geomModifiedDate, asset.expired, asset.linkSource)
    }.toSeq
  }

  def fetchSpeedLimitsByLinkIds(linkIds: Seq[Long]): Seq[SpeedLimit] = {

    val queryFilter = "AND (valid_to IS NULL OR valid_to > SYSDATE)"
    fetchByLinkIds(linkIds, queryFilter).map {persisted =>
        SpeedLimit(persisted.id, persisted.linkId, persisted.sideCode, TrafficDirection.UnknownDirection, persisted.value, Seq(Point(0.0, 0.0)),persisted. startMeasure, persisted.endMeasure, persisted.modifiedBy, persisted.modifiedDate, persisted.createdBy, persisted.createdDate, persisted.vvhTimeStamp, persisted.geomModifiedDate, linkSource = persisted.linkSource)
    }
  }

  private def fetchSpeedLimitsByLinkId(linkId: Long): Seq[SpeedLimit] = fetchSpeedLimitsByLinkIds(Seq(linkId))

  private def fetchHistorySpeedLimitsByLinkIds(linkIds: Seq[Long]): Seq[SpeedLimit] = {
    val queryFilter = "AND (valid_to IS NOT NULL AND valid_to < SYSDATE)"

    fetchByLinkIds(linkIds, queryFilter).map {
      case (persisted) =>
        SpeedLimit(persisted.id, persisted.linkId, persisted.sideCode, TrafficDirection.UnknownDirection, persisted.value, Seq(Point(0.0, 0.0)),persisted. startMeasure, persisted.endMeasure, persisted.modifiedBy, persisted.modifiedDate, persisted.createdBy, persisted.createdDate, persisted.vvhTimeStamp, persisted.geomModifiedDate, linkSource = persisted.linkSource)
    }
  }

  /**
    * Returns speed limits by asset id. Used by SpeedLimitService.loadSpeedLimit.
    */
  def getSpeedLimitLinksById(id: Long): Seq[SpeedLimit] = getSpeedLimitLinksByIds(Set(id))

  def getSpeedLimitLinksByIds(ids: Set[Long]): Seq[SpeedLimit] = {
    val speedLimitRows = MassQuery.withIds(ids) { idTableName =>
      sql"""select a.id, pos.link_id, pos.side_code, e.value, pos.start_measure, pos.end_measure, a.modified_by, a.modified_date, case when a.valid_to <= sysdate then 1 else 0 end as expired,
            a.created_by, a.created_date, pos.adjusted_timestamp, pos.modified_date, pos.link_source, p.public_id
        from ASSET a
        join ASSET_LINK al on a.id = al.asset_id
        join LRM_POSITION pos on al.position_id = pos.id
        join #$idTableName i on i.id = a.id
        join PROPERTY p on a.asset_type_id = p.asset_type_id
        left join SINGLE_CHOICE_VALUE s on s.asset_id = a.id and s.property_id = p.id
        left join MULTIPLE_CHOICE_VALUE mc on mc.asset_id = a.id and mc.property_id = p.id and p.property_type = 'checkbox'
        left join ENUMERATED_VALUE e on s.enumerated_value_id = e.id or mc.enumerated_value_id = e.id
        where a.asset_type_id = 20
        """.as[SpeedLimitRow].list
    }

    val speedLimits = groupSpeedLimitsResult(speedLimitRows)
    val roadLinksWithComplementaryByLinkId = roadLinkService.fetchVVHRoadlinksAndComplementary(speedLimits.map(_.linkId).toSet)

    speedLimits.map {speedLimit =>
      val vvhRoadLink = roadLinksWithComplementaryByLinkId.find(_.linkId == speedLimit.linkId).getOrElse(throw new NoSuchElementException)
      SpeedLimit(speedLimit.id, speedLimit.linkId, speedLimit.sideCode, vvhRoadLink.trafficDirection, speedLimit.value, GeometryUtils.truncateGeometry3D(vvhRoadLink.geometry, speedLimit.startMeasure, speedLimit.endMeasure), speedLimit.startMeasure, speedLimit.endMeasure, speedLimit.modifiedBy, speedLimit.modifiedDate, speedLimit.createdBy, speedLimit.createdDate, speedLimit.vvhTimeStamp, speedLimit.geomModifiedDate, linkSource = vvhRoadLink.linkSource)
    }
  }

  def getPersistedSpeedLimitByIds(ids: Set[Long]): Seq[PersistedSpeedLimit] = {
    val speedLimitRows = MassQuery.withIds(ids) { idTableName =>
      sql"""select a.id, pos.link_id, pos.side_code, e.value, pos.start_measure, pos.end_measure, a.modified_by, a.modified_date, case when a.valid_to <= sysdate then 1 else 0 end as expired,
            a.created_by, a.created_date, pos.adjusted_timestamp, pos.modified_date, pos.link_source, p.public_id
        from ASSET a
        join ASSET_LINK al on a.id = al.asset_id
        join LRM_POSITION pos on al.position_id = pos.id
        join PROPERTY p on a.asset_type_id = p.asset_type_id
        join #$idTableName i on i.id = a.id
        left join SINGLE_CHOICE_VALUE s on s.asset_id = a.id and s.property_id = p.id
        left join MULTIPLE_CHOICE_VALUE mc on mc.asset_id = a.id and mc.property_id = p.id and p.property_type = 'checkbox'
        left join ENUMERATED_VALUE e on s.enumerated_value_id = e.id or mc.enumerated_value_id = e.id
        where a.asset_type_id = 20
        """.as[SpeedLimitRow].list
    }
    groupSpeedLimitsResult(speedLimitRows)
  }

  /**
    * Returns speed limits that match a set of link ids.
    */
  def getCurrentSpeedLimitsByLinkIds(linkIds: Option[Set[Long]]): Seq[SpeedLimit] = {

    linkIds.map { linkId =>
      linkId.isEmpty match {
        case true => Seq.empty[SpeedLimit]
        case false => fetchSpeedLimitsByLinkIds(linkId.toSeq)
      }
    }.getOrElse(Seq.empty[SpeedLimit])
  }

  /**
    * Returns speed limit by asset id. Used by SpeedLimitService.separate.
    */
  def getPersistedSpeedLimit(id: Long): Option[PersistedSpeedLimit] = {
    val speedLimitRows = sql"""
      select a.id, pos.link_id, pos.side_code, e.value, pos.start_measure, pos.end_measure,a.modified_by,
             a.modified_date, case when a.valid_to <= sysdate then 1 else 0 end as expired, a.created_by, a.created_date, pos.adjusted_timestamp, pos.modified_date, pos.link_source, p.public_id
      from ASSET a
        join ASSET_LINK al on a.id = al.asset_id
        join LRM_POSITION pos on al.position_id = pos.id
        join PROPERTY p on a.asset_type_id = p.asset_type_id
        left join SINGLE_CHOICE_VALUE s on s.asset_id = a.id and s.property_id = p.id
        left join ENUMERATED_VALUE e on s.enumerated_value_id = e.id or mc.enumerated_value_id = e.id
        left join multiple_choice_value mc on mc.asset_id = a.id and mc.property_id = p.id and p.property_type = 'checkbox'
        where a.asset_type_id = 20 and a.id = $id
        """.as[SpeedLimitRow].firstOption

    groupSpeedLimitsResult(speedLimitRows.toSeq).headOption
  }

  /**
    * Returns unknown speed limits by municipality. Used by SpeedLimitService.getUnknown.
    */
  def getUnknownSpeedLimits(municipalities: Set[Int], administrativeClass: Option[AdministrativeClass]): Map[String, Map[String, Any]] = {
    def toUnknownLimit(x: (Long, String, Int)) = UnknownLimit(x._1, x._2, AdministrativeClass(x._3).toString)
    val unknownSpeedLimitQuery =
      """
      select s.link_id, m.name_fi, s.administrative_class
      from unknown_speed_limit s
      join municipality m on s.municipality_code = m.id
      where s.unnecessary = 0
      """

    val filterAdministrativeClass = administrativeClass match {
      case Some(ac) if ac == Municipality => s" and s.administrative_class not in ( ${State.value}, ${Private.value})"
      case Some(ac) if ac == State => s" and s.administrative_class = ${ac.value}"
      case _ => ""
    }

    val sql = if (municipalities.isEmpty) {
      unknownSpeedLimitQuery + filterAdministrativeClass
    } else {
      unknownSpeedLimitQuery + filterAdministrativeClass + s" and s.municipality_code in (${municipalities.mkString(",")}) "
    }

    val limitsByMunicipality = Q.queryNA[UnknownLimit](sql).list
      .groupBy(_.municipality)
      .mapValues {
        _.groupBy(_.administrativeClass)
          .mapValues(_.map(_.linkId))
      }

    addCountsFor(limitsByMunicipality)
  }

  def getMunicipalitiesWithUnknown(administrativeClass: Option[AdministrativeClass]): Seq[(Long, String)] = {

    val municipalitiesQuery =
      s"""
      select m.id, m.name_fi from municipality m
      where m.id in (select MUNICIPALITY_CODE from UNKNOWN_SPEED_LIMIT uk where uk.administrative_class not in ( ${State.value} , ${Private.value} ))
      """

    Q.queryNA[(Long, String)](municipalitiesQuery).list
  }


  def getMunicipalitiesWithUnknown(municipality: Int): Seq[Long] = {

    val municipalitiesQuery =
      s"""
      select LINK_ID from UNKNOWN_SPEED_LIMIT uk where uk.MUNICIPALITY_CODE = $municipality
      """

    Q.queryNA[Long](municipalitiesQuery).list
  }


    /**
    * Returns data for municipality validation. Used by OracleSpeedLimitDao.splitSpeedLimit.
    */
  def getLinksWithLengthFromVVH(id: Long): Seq[(Long, Double, Seq[Point], Int, LinkGeomSource, AdministrativeClass)] = {
    val assetTypeId = SpeedLimitAsset.typeId
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
      (linkId, endMeasure - startMeasure, truncatedGeometry, vvhRoadLink.municipalityCode, vvhRoadLink.linkSource, vvhRoadLink.administrativeClass)
    }
  }

  /**
    * Returns only car traffic roads as a topology and speed limits that match these road links.
    * Used by SpeedLimitService.get (by bounding box and a list of municipalities) and SpeedLimitService.get (by municipality)
    */
  def getSpeedLimitLinksByRoadLinks(roadLinks: Seq[RoadLink], showSpeedLimitsHistory: Boolean = false): (Seq[SpeedLimit], Seq[RoadLink]) = {
    var speedLimitLinks: Seq[SpeedLimit] = Seq()
    if (showSpeedLimitsHistory) {
      speedLimitLinks = fetchHistorySpeedLimitsByLinkIds(roadLinks.map(_.linkId)).map(createGeometryForSegment(roadLinks))
    } else {
      speedLimitLinks = fetchSpeedLimitsByLinkIds(roadLinks.map(_.linkId)).map(createGeometryForSegment(roadLinks))
    }
    (speedLimitLinks, roadLinks)
  }

  def getSpeedLimitsChangedSince(sinceDate: DateTime, untilDate: DateTime, withAdjust: Boolean, recordLimit: String): Seq[PersistedSpeedLimit] = {
    val withAutoAdjustFilter = if (withAdjust) "" else "and (a.modified_by is null OR a.modified_by != 'vvh_generated')"

    val speedLimitRows =  sql"""
        select asset_id, link_id, side_code, value, start_measure, end_measure, modified_by, modified_date, expired, created_by, created_date,
               adjusted_timestamp, pos_modified_date, link_source, public_id
          from (
            select a.id as asset_id, pos.link_id, pos.side_code, e.value, pos.start_measure, pos.end_measure, a.modified_by, a.modified_date,
            case when a.valid_to <= sysdate then 1 else 0 end as expired, a.created_by, a.created_date, pos.adjusted_timestamp,
            pos.modified_date as pos_modified_date, pos.link_source, p.public_id,
            DENSE_RANK() over (ORDER BY a.id) line_number
            from asset a
            join asset_link al on a.id = al.asset_id
            join lrm_position pos on al.position_id = pos.id
            join property p on a.asset_type_id = p.asset_type_id
            left join single_choice_value s on s.asset_id = a.id and s.property_id = p.id
            left join multiple_choice_value mc on mc.asset_id = a.id and mc.property_id = p.id and p.property_type = 'checkbox'
            left join enumerated_value e on s.enumerated_value_id = e.id or mc.enumerated_value_id = e.id
            where a.asset_type_id = 20
            and floating = 0
            and (
              (a.valid_to > $sinceDate and a.valid_to <= $untilDate)
              or
              (a.modified_date > $sinceDate and a.modified_date <= $untilDate)
              or
              (a.created_date > $sinceDate and a.created_date <= $untilDate)
            )
            #$withAutoAdjustFilter
        ) #$recordLimit
    """.as[SpeedLimitRow].list

    groupSpeedLimitsResult(speedLimitRows)
  }

  /**
    * Returns m-values and side code by asset id. Used by OracleSpeedLimitDao.splitSpeedLimit.
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
    * Saves unknown speed limits to unknown speed limits list. Used by SpeedLimitService.persistUnknown.
    */
  def persistUnknownSpeedLimits(limits: Seq[UnknownSpeedLimit]): Unit = {
    val statement = dynamicSession.prepareStatement(
      """
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
    * Creates new speed limit with municipality validation. Returns id of new speed limit.
    * Used by SpeedLimitService.create.
    */
  def createSpeedLimit(creator: String, linkId: Long, linkMeasures: Measures, sideCode: SideCode, value: SpeedLimitValue,
                       vvhTimeStamp: Long, municipalityValidation: (Int, AdministrativeClass) => Unit): Option[Long] = {
    val roadlink = roadLinkService.fetchVVHRoadlinkAndComplementary(linkId)
    municipalityValidation(roadlink.get.municipalityCode, roadlink.get.administrativeClass)
    createSpeedLimitWithoutDuplicates(creator, linkId, linkMeasures, sideCode, value, None, None, None, None, roadlink.get.linkSource)
  }

  /**
    * Creates new speed limit. Returns id of new speed limit. SpeedLimitService.persistProjectedLimit and SpeedLimitService.separate.
    */
  def createSpeedLimit(creator: String, linkId: Long, linkMeasures: Measures, sideCode: SideCode, value: SpeedLimitValue, vvhTimeStamp: Option[Long], createdDate: Option[DateTime] = None, modifiedBy: Option[String] = None, modifiedAt: Option[DateTime] = None, linkSource: LinkGeomSource): Option[Long]  =
    createSpeedLimitWithoutDuplicates(creator, linkId, linkMeasures, sideCode, value, vvhTimeStamp, createdDate, modifiedBy, modifiedAt, linkSource)

  private def createSpeedLimitWithoutDuplicates(creator: String, linkId: Long, linkMeasures: Measures, sideCode: SideCode, value: SpeedLimitValue, vvhTimeStamp: Option[Long], createdDate: Option[DateTime], modifiedBy: Option[String], modifiedAt: Option[DateTime], linkSource: LinkGeomSource): Option[Long] = {
    val existingLrmPositions = fetchSpeedLimitsByLinkId(linkId).filter(sl => sideCode == SideCode.BothDirections || sl.sideCode == sideCode)

    val remainders = existingLrmPositions.map {speedLimit =>
      (speedLimit.startMeasure, speedLimit.endMeasure) }.foldLeft(Seq((linkMeasures.startMeasure, linkMeasures.endMeasure)))(GeometryUtils.subtractIntervalFromIntervals).filter { case (start, end) => math.abs(end - start) > 0.01 }
    if (remainders.length == 1) {
      Some(forceCreateSpeedLimit(creator, SpeedLimitAsset.typeId, linkId, linkMeasures, sideCode, Some(value), (id, value) => insertProperties(id, value), Some(vvhTimeStamp.getOrElse(vvhClient.roadLinkData.createVVHTimeStamp())), createdDate, modifiedBy, modifiedAt, linkSource))
    } else {
      None
    }
  }
  /**
    * Saves enumerated value to db. Used by OracleSpeedLimitDao.createSpeedLimitWithoutDuplicates and AssetDataImporter.splitSpeedLimits.
    */
  def insertSingleChoiceValue(assetId: Long, valuePropertyId: String, value: Int): Unit  = {
    val propertyId = Q.query[(String, Int), Long](Queries.propertyIdByPublicIdAndTypeId).apply(valuePropertyId, SpeedLimitAsset.typeId).first
    Queries.insertSingleChoiceProperty(assetId, propertyId, value).execute
  }

  /**
    * Saves enumerated value to db. Used by OracleSpeedLimitDao.createSpeedLimitWithoutDuplicates and AssetDataImporter.splitSpeedLimits.
    */
  def insertMultipleChoiceValue(assetId: Long, valuePropertyId: String, value: Int): Unit  = {
    val propertyId = Q.query[(String, Int), Long](Queries.propertyIdByPublicIdAndTypeId).apply(valuePropertyId, SpeedLimitAsset.typeId).first
    Queries.insertMultipleChoiceValue(assetId, propertyId, value).execute
  }


  def insertProperties(assetId: Long, value: SpeedLimitValue): Unit = {
    insertSingleChoiceValue(assetId, "rajoitus", value.value)

    insertMultipleChoiceValue(assetId, "suggest_box", if(value.isSuggested) 1 else 0)
  }

  /**
    * This method doesn't trigger "speedLimits:purgeUnknownLimits" actor, to remove the created speed limits from the unknown list
    */
  def forceCreateSpeedLimit(creator: String, typeId: Int, linkId: Long, linkMeasures: Measures, sideCode: SideCode, value: Option[SpeedLimitValue], valueInsertion: (Long, SpeedLimitValue) => Unit, vvhTimeStamp: Option[Long], createdDate: Option[DateTime], modifiedBy: Option[String], modifiedAt: Option[DateTime], linkSource: LinkGeomSource): Long = {
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

    val latestModifiedBy = modifiedBy match {
      case Some(modifier) => s"""'$modifier'"""
      case None => null
    }

    val insertAll =
      s"""
       insert all
         into asset(id, asset_type_id, created_by, created_date, modified_by, modified_date)
         values ($assetId, $typeId, '$creator', $creationDate, $latestModifiedBy, $modifiedDate)

         into lrm_position(id, start_measure, end_measure, link_id, side_code, adjusted_timestamp, modified_date, link_source)
         values ($lrmPositionId, ${linkMeasures.startMeasure}, ${linkMeasures.endMeasure}, $linkId, $sideCodeValue, ${vvhTimeStamp.getOrElse(0)}, SYSDATE, ${linkSource.value})

         into asset_link(asset_id, position_id)
         values ($assetId, $lrmPositionId)
       select * from dual
      """
    Q.updateNA(insertAll).execute

    value.foreach(x => valueInsertion(assetId, x))

    assetId
  }


  /**
    * Sets floating flag of linear assets true in db. Used in AssetDataImporter.splitSpeedLimits.
    */
  def setFloating(ids: Set[Long]): Unit = {
    if (ids.nonEmpty) {
      MassQuery.withIds(ids) { idTableName =>
        sqlu"""update asset set floating = 1 where id in (select id from #$idTableName)""".execute
      }
    }
  }

  /**
    * Updates speed limit value. Used by SpeedLimitService.updateValues, SpeedLimitService.split and SpeedLimitService.separate.
    */
  def updateSpeedLimitValue(id: Long, values: SpeedLimitValue, username: String): Option[Long] = {
    val propertyId = Q.query[String, Long](Queries.propertyIdByPublicId).apply("rajoitus").first
    val assetsUpdated = Queries.updateAssetModified(id, username).first
    val singlePropertiesUpdated = Queries.updateSingleChoiceProperty(id, propertyId, values.value.toLong).first

    val propertyIdBox = Q.query[(String, Int), Long](Queries.propertyIdByPublicIdAndTypeId).apply("suggest_box", SpeedLimitAsset.typeId).first
    val propertiesUpdated = Queries.updateMultipleChoiceValue(id, propertyIdBox, if(values.isSuggested) 1 else 0).first + singlePropertiesUpdated

    if (assetsUpdated == 1 ) {
      Some(id)
    } else {
      dynamicSession.rollback()
      None
    }
  }

  /**
    * Updates m-values and vvh time stamp in db. Used by OracleSpeedLimitDao.splitSpeedLimit.
    */
  def updateMValues(id: Long, linkMeasures: (Double, Double), vvhTimeStamp: Option[Long] = None): Unit = {
    val (startMeasure, endMeasure) = linkMeasures

    val adjusted_timestamp =  vvhTimeStamp match {
      case Some(timeStamp) => timeStamp
      case _ => vvhClient.roadLinkData.createVVHTimeStamp()
    }

    sqlu"""
      update LRM_POSITION
      set
        start_measure = $startMeasure,
        end_measure = $endMeasure,
        adjusted_timestamp = $adjusted_timestamp,
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
    * Updates validity of asset in db.
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
    * Updates validity of asset in db.
    */
  def updateExpiration(id: Long) = {
    val propertiesUpdated =
      sqlu"update asset set valid_to = sysdate where id = $id".first

    if (propertiesUpdated == 1) {
      Some(id)
    } else {
      None
    }
  }

  /**
    * Removes speed limits from unknown speed limits list. Used by SpeedLimitService.purgeUnknown.
    */
  def purgeFromUnknownSpeedLimits(linkId: Long, roadLinkLength: Double): Unit = {
    val speedLimits = fetchSpeedLimitsByLinkId(linkId)

    def calculateRemainders(sideCode: SideCode): Seq[(Double, Double)] = {
      val limitEndPoints = speedLimits.filter(sl => sl.sideCode == SideCode.BothDirections || sl.sideCode == sideCode).map {
        case(speedLimit) => (speedLimit.startMeasure, speedLimit.endMeasure) }
      limitEndPoints.foldLeft(Seq((0.0, roadLinkLength)))(GeometryUtils.subtractIntervalFromIntervals).filter { case (start, end) => math.abs(end - start) > 0.1}
    }

    val towardsRemainders = calculateRemainders(SideCode.TowardsDigitizing)
    val againstRemainders = calculateRemainders(SideCode.AgainstDigitizing)
    if (towardsRemainders.isEmpty && againstRemainders.isEmpty) {
      sqlu"""delete from unknown_speed_limit where link_id = $linkId""".execute
    }
  }

  /**
    * Removes speed limits from unknown speed limits list. Used by SpeedLimitService.purgeUnknown.
    */
  def deleteUnknownSpeedLimits(linkIds: Seq[Long]): Unit = {
    sqlu"""delete from unknown_speed_limit where link_id in (#${linkIds.mkString(",")})""".execute
  }

  def hideUnknownSpeedLimits(linkIds: Set[Long]): Set[Long] = {
    sqlu"""update unknown_speed_limit set unnecessary = 1 where link_id in (#${linkIds.mkString(",")})""".execute
    linkIds
  }

  private def addCountsFor(unknownLimitsByMunicipality: Map[String, Map[String, Any]]): Map[String, Map[String, Any]] = {
    val unknownSpeedLimitCounts = sql"""
      select name_fi, s.administrative_class, count(*), m.id
      from unknown_speed_limit s
      join municipality m on s.municipality_code = m.id
      where s.unnecessary = 0
      group by name_fi, administrative_class, m.id
    """.as[(String, Int, Int, Int)].list

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

  private def createGeometryForSegment(topology: Seq[RoadLink])(segment: SpeedLimit) = {
    val roadLink = topology.find(_.linkId == segment.linkId).get
    val geometry = GeometryUtils.truncateGeometry3D(roadLink.geometry, segment.startMeasure, segment.endMeasure)
    SpeedLimit(segment.id, segment.linkId, segment.sideCode, roadLink.trafficDirection, segment.value, geometry, segment.startMeasure,
      segment.endMeasure, segment.modifiedBy, segment.modifiedDateTime, segment.createdBy, segment.createdDateTime, segment.vvhTimeStamp,
      segment.geomModifiedDate, linkSource = roadLink.linkSource)
  }

  /**
    * Sets floating flag of linear assets true in db. Used in LinearAssetService.drop.
    */
  def floatLinearAssets(ids: Set[Long]): Unit = {
    if (ids.nonEmpty) {
      MassQuery.withIds(ids) { idTableName =>
        sqlu"""update asset set floating = 1 where id in (select id from #$idTableName)""".execute
      }
    }
  }

  /**
    * Updates from Change Info in db.
    */
  def updateMValuesChangeInfo(id: Long, linkMeasures: (Double, Double), vvhTimestamp: Long, username: String): Unit = {
    println("asset_id -> " + id)
    val (startMeasure, endMeasure) = linkMeasures
    sqlu"""
      update LRM_POSITION
      set
        start_measure = $startMeasure,
        end_measure = $endMeasure,
        modified_date = SYSDATE,
        adjusted_timestamp = $vvhTimestamp
      where id = (
        select lrm.id
          from asset a
          join asset_link al on a.ID = al.ASSET_ID
          join lrm_position lrm on lrm.id = al.POSITION_ID
          where a.id = $id)
    """.execute

    sqlu"""
      update ASSET
      set modified_by = $username,
          modified_date = SYSDATE
      where id = $id
    """.execute
  }

}