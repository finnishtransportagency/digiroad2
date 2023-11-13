package fi.liikennevirasto.digiroad2.dao.linearasset

import java.nio.charset.StandardCharsets
import java.util.{Base64, NoSuchElementException}
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.postgis.MassQuery
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import com.github.tototoshi.slick.MySQLJodaSupport._
import fi.liikennevirasto.digiroad2.dao.{DynamicLinearAssetDao, Queries, Sequences}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.Measures
import fi.liikennevirasto.digiroad2.util.LinearAssetUtils
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedResult, StaticQuery => Q}
case class UnknownLimit(linkId: String, municipality: String, administrativeClass: String)
class PostGISSpeedLimitDao(val roadLinkService: RoadLinkService) extends DynamicLinearAssetDao {
  def MassQueryThreshold = 500
 

  implicit object GetByteArray extends GetResult[Array[Byte]] {
    def apply(rs: PositionedResult) = rs.nextBytes()
  }

  implicit object GetSideCode extends GetResult[SideCode] {
    def apply(rs: PositionedResult) = SideCode(rs.nextInt())
  }

  implicit val getSpeedLimit = new GetResult[SpeedLimitRow] {
    def apply(r: PositionedResult) : SpeedLimitRow = {
      val id = r.nextLong()
      val linkId = r.nextString()
      val sideCode = r.nextInt()
      val value = r.nextIntOption()
      val startMeasure = r.nextDouble()
      val endMeasure = r.nextDouble()
      val modifiedBy = r.nextStringOption()
      val modifiedDateTime = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val expired = r.nextBoolean
      val createdBy = r.nextStringOption()
      val createdDateTime = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val timeStamp = r.nextLong()
      val geomModifiedDate = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val linkSource = r.nextInt()
      val publicId = r.nextString()

      SpeedLimitRow(id, linkId, SideCode(sideCode), value, startMeasure, endMeasure, modifiedBy, modifiedDateTime, createdBy, createdDateTime, timeStamp, geomModifiedDate, expired, linkSource = LinkGeomSource(linkSource), publicId)
    }
  }

  implicit val getUnknown = new GetResult[UnknownLimit] {
    def apply(r: PositionedResult) = {
      val linkId = r.nextString()
      val municipality = r.nextString()
      val administrativeClass = AdministrativeClass(r.nextInt()).toString
      UnknownLimit (linkId, municipality, administrativeClass)
    }
  }

  private def fetchByLinkIds(linkIds: Seq[String], queryFilter: String) : Seq[PersistedLinearAsset] = {
    val speedLimitRows = MassQuery.withStringIds(linkIds.toSet) { idTableName =>
      sql"""
        select a.id, pos.link_id, pos.side_code, e.value, pos.start_measure, pos.end_measure, a.modified_by,
        a.modified_date, case when a.valid_to <= current_timestamp then 1 else 0 end as expired, a.created_by, a.created_date,
        pos.adjusted_timestamp, pos.modified_date, pos.link_source, p.public_id
           from asset a
           join asset_link al on a.id = al.asset_id
           join lrm_position pos on al.position_id = pos.id
            join  #$idTableName i on i.id = pos.link_id
           join property p on a.asset_type_id = p.asset_type_id
           left join single_choice_value s on s.asset_id = a.id and s.property_id = p.id
           left join multiple_choice_value mc on mc.asset_id = a.id and mc.property_id = p.id and p.property_type = 'checkbox'
           left join enumerated_value e on s.enumerated_value_id = e.id or mc.enumerated_value_id = e.id
		   where a.asset_type_id = 20 and floating = '0' #$queryFilter""".as[SpeedLimitRow].list
    }
    groupSpeedLimitsResult(speedLimitRows)
  }

  def groupSpeedLimitsResult(speedLimitRows: Seq[SpeedLimitRow]) : Seq[PersistedLinearAsset] = {
    val groupedSpeedLimit = speedLimitRows.groupBy(_.id)
    groupedSpeedLimit.keys.map { assetId =>
      val rows = groupedSpeedLimit(assetId)
      val asset = rows.head
      val suggestBoxValue =
        rows.find(_.publicId == "suggest_box") match {
          case Some(suggested) => suggested.value
          case _ => 0
        }

      val speedLimitValue = (suggestBoxValue, rows.find(_.publicId == "rajoitus").head.value.map(_.asInstanceOf[Int])) match {
        case (Some(isSuggested), Some(value)) => Some(SpeedLimitValue(value, isSuggested == 1 ))
        case (None, Some(value)) => Some(SpeedLimitValue(value))
        case _ => None
      }

      PersistedLinearAsset(asset.id, asset.linkId, asset.sideCode.value, speedLimitValue,
        asset.startMeasure, asset.endMeasure, asset.createdBy,
        asset.createdDate, asset.modifiedBy, asset.modifiedDate, asset.expired,
        SpeedLimitAsset.typeId,asset.timeStamp,  asset.geomModifiedDate, asset.linkSource,
        None, None, None)
    }.toSeq
  }

  def fetchSpeedLimitsByLinkIds(linkIds: Seq[String]): Seq[PieceWiseLinearAsset] = {

    val queryFilter = "AND (valid_to IS NULL OR valid_to > current_timestamp)"
    fetchByLinkIds(linkIds, queryFilter).map {persisted =>
        PieceWiseLinearAsset(persisted.id, persisted.linkId, SideCode(persisted.sideCode), persisted.value, Seq(Point(0.0, 0.0)), persisted.expired,
          persisted.startMeasure, persisted.endMeasure, Set(Point(0.0, 0.0)),persisted.modifiedBy, persisted.modifiedDateTime, persisted.createdBy, persisted.createdDateTime,
          persisted.typeId, SideCode.toTrafficDirection(SideCode(persisted.sideCode)), persisted.timeStamp, persisted.geomModifiedDate,
          persisted.linkSource, Unknown, Map(), persisted.verifiedBy, persisted.verifiedDate, persisted.informationSource)
    }
  }


  private def fetchSpeedLimitsByLinkId(linkId: String): Seq[PieceWiseLinearAsset] = fetchSpeedLimitsByLinkIds(Seq(linkId))

  private def fetchHistorySpeedLimitsByLinkIds(linkIds: Seq[String]): Seq[PieceWiseLinearAsset] = {
    val queryFilter = "AND (valid_to IS NOT NULL AND valid_to < current_timestamp)"

    fetchByLinkIds(linkIds, queryFilter).map {
      case (persisted) =>
        PieceWiseLinearAsset(persisted.id, persisted.linkId, SideCode(persisted.sideCode), persisted.value, Seq(Point(0.0, 0.0)), persisted.expired,
          persisted.startMeasure, persisted.endMeasure, Set(Point(0.0, 0.0)),persisted.modifiedBy, persisted.modifiedDateTime, persisted.createdBy, persisted.createdDateTime,
          persisted.typeId, SideCode.toTrafficDirection(SideCode(persisted.sideCode)), persisted.timeStamp, persisted.geomModifiedDate,
          persisted.linkSource, Unknown, Map(), persisted.verifiedBy, persisted.verifiedDate, persisted.informationSource)

    }
  }

  /**
    * Returns speed limits by asset id. Used by SpeedLimitService.loadSpeedLimit.
    */
  def getSpeedLimitLinksById(id: Long): Seq[PieceWiseLinearAsset] = getSpeedLimitLinksByIds(Set(id))

  def getSpeedLimitLinksByIds(ids: Set[Long]): Seq[PieceWiseLinearAsset] = {
    val speedLimitRows = MassQuery.withIds(ids) { idTableName =>
      sql"""select a.id, pos.link_id, pos.side_code, e.value, pos.start_measure, pos.end_measure, a.modified_by, a.modified_date, case when a.valid_to <= current_timestamp then 1 else 0 end as expired,
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
    val roadLinksWithComplementaryByLinkId = roadLinkService.fetchRoadlinksAndComplementaries(speedLimits.map(_.linkId).toSet)

    speedLimits.map {persisted =>
      val roadLinkFetched = roadLinksWithComplementaryByLinkId.find(_.linkId == persisted.linkId).getOrElse(throw new NoSuchElementException)
      PieceWiseLinearAsset(persisted.id, persisted.linkId, SideCode(persisted.sideCode), persisted.value, GeometryUtils.truncateGeometry3D(roadLinkFetched.geometry, persisted.startMeasure, persisted.endMeasure), persisted.expired,
        persisted.startMeasure, persisted.endMeasure, Set(Point(0.0, 0.0)),persisted.modifiedBy, persisted.modifiedDateTime, persisted.createdBy, persisted.createdDateTime,
        persisted.typeId, SideCode.toTrafficDirection(SideCode(persisted.sideCode)), persisted.timeStamp, persisted.geomModifiedDate,
        roadLinkFetched.linkSource, Unknown, Map(), persisted.verifiedBy, persisted.verifiedDate, persisted.informationSource)

    }
  }

  def getPersistedSpeedLimitByIds(ids: Set[Long]): Seq[PersistedLinearAsset] = {
    val speedLimitRows = MassQuery.withIds(ids) { idTableName =>
      sql"""select a.id, pos.link_id, pos.side_code, e.value, pos.start_measure, pos.end_measure, a.modified_by, a.modified_date, case when a.valid_to <= current_timestamp then 1 else 0 end as expired,
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

  override def fetchDynamicLinearAssetsByLinkIds(assetTypeId: Int, linkIds: Seq[String], includeExpired: Boolean = false, includeFloating: Boolean = false): Seq[PersistedLinearAsset] = {
    val queryFilter = "AND (valid_to IS NULL OR valid_to > current_timestamp)"
    fetchByLinkIds(linkIds,queryFilter)
  }

  /**
    * Returns speed limits that match a set of link ids.
    */
  def getCurrentSpeedLimitsByLinkIds(linkIds: Option[Set[String]]): Seq[PieceWiseLinearAsset] = {

    linkIds.map { linkId =>
      linkId.isEmpty match {
        case true => Seq.empty[PieceWiseLinearAsset]
        case false => fetchSpeedLimitsByLinkIds(linkId.toSeq)
      }
    }.getOrElse(Seq.empty[PieceWiseLinearAsset])
  }

  /**
    * Returns speed limit by asset id. Used by SpeedLimitService.separate.
    */
  def getPersistedSpeedLimit(id: Long): Option[PersistedLinearAsset] = {
    val speedLimitRows = sql"""
      select a.id, pos.link_id, pos.side_code, e.value, pos.start_measure, pos.end_measure,a.modified_by,
             a.modified_date, case when a.valid_to <= current_timestamp then 1 else 0 end as expired, a.created_by, a.created_date, pos.adjusted_timestamp, pos.modified_date, pos.link_source, p.public_id
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
    def toUnknownLimit(x: (String, String, Int)) = UnknownLimit(x._1, x._2, AdministrativeClass(x._3).toString)
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

  def getUnknownSpeedLimits(links: Set[String]): Seq[UnknownLimit] = {
    if (links.isEmpty) {
      throw new Exception("Parameters is empty")
    }
   
    val sql =
      s"""
      select s.link_id, m.name_fi, s.administrative_class
      from unknown_speed_limit s
      join municipality m on s.municipality_code = m.id
      where s.unnecessary = 0 and s.link_id in (${links.map(t=>s"'$t'").mkString(",")}) """
    
    Q.queryNA[UnknownLimit](sql).list
  }

  def getMunicipalitiesWithUnknown(administrativeClass: Option[AdministrativeClass]): Seq[(Long, String)] = {

    val municipalitiesQuery =
      s"""
      select m.id, m.name_fi from municipality m
      where m.id in (select MUNICIPALITY_CODE from UNKNOWN_SPEED_LIMIT uk where uk.administrative_class not in ( ${State.value} , ${Private.value} ))
      """

    Q.queryNA[(Long, String)](municipalitiesQuery).list
  }


  def getMunicipalitiesWithUnknown(municipality: Int): Seq[(String, Int)] = {

    val municipalitiesQuery =
      s"""
      select LINK_ID, ADMINISTRATIVE_CLASS from UNKNOWN_SPEED_LIMIT uk where uk.MUNICIPALITY_CODE = $municipality
      """

    Q.queryNA[(String, Int)](municipalitiesQuery).list
  }


    /**
    * Returns data for municipality validation. Used by PostGISSpeedLimitDao.splitSpeedLimit.
    */
  def getLinksWithLength(id: Long): Seq[(String, Double, Seq[Point], Int, LinkGeomSource, AdministrativeClass)] = {
    val assetTypeId = SpeedLimitAsset.typeId
    val links = sql"""
      select pos.link_id, pos.start_measure, pos.end_measure
      from ASSET a
        join ASSET_LINK al on a.id = al.asset_id
        join LRM_POSITION pos on al.position_id = pos.id
        where a.asset_type_id = $assetTypeId and a.id = $id
        """.as[(String, Double, Double)].list

    val roadLinksByLinkId = roadLinkService.fetchRoadlinksAndComplementaries(links.map(_._1).toSet)

    links.map { case (linkId, startMeasure, endMeasure) =>
      val roadLinkFetched = roadLinksByLinkId.find(_.linkId == linkId).getOrElse(throw new NoSuchElementException)
      val truncatedGeometry = GeometryUtils.truncateGeometry3D(roadLinkFetched.geometry, startMeasure, endMeasure)
      (linkId, endMeasure - startMeasure, truncatedGeometry, roadLinkFetched.municipalityCode, roadLinkFetched.linkSource, roadLinkFetched.administrativeClass)
    }
  }

  /**
    * Returns only car traffic roads as a topology and speed limits that match these road links.
    * Used by SpeedLimitService.get (by bounding box and a list of municipalities) and SpeedLimitService.get (by municipality)
    */
  def getSpeedLimitLinksByRoadLinks(roadLinks: Seq[RoadLink], showSpeedLimitsHistory: Boolean = false): Seq[PieceWiseLinearAsset] = {
    if (showSpeedLimitsHistory) {
      fetchHistorySpeedLimitsByLinkIds(roadLinks.map(_.linkId)).map(createGeometryForSegment(roadLinks))
    } else {
      fetchSpeedLimitsByLinkIds(roadLinks.map(_.linkId)).map(createGeometryForSegment(roadLinks))
    }
  }

  def getSpeedLimitsChangedSince(sinceDate: DateTime, untilDate: DateTime, withAdjust: Boolean, token: Option[String]): Seq[PersistedLinearAsset] = {
    val withAutoAdjustFilter = if (withAdjust) "" else "and (a.modified_by is null OR a.modified_by != 'generated_in_update')"
    val recordLimit = token match {
      case Some(tk) =>
        val (startNum, endNum) = Decode.getPageAndRecordNumber(tk)

        s"WHERE line_number between $startNum and $endNum"

      case _ => ""
    }

    val speedLimitRows =  sql"""
        select asset_id, link_id, side_code, value, start_measure, end_measure, modified_by, modified_date, expired, created_by, created_date,
               adjusted_timestamp, pos_modified_date, link_source, public_id
          from (
            select a.id as asset_id, pos.link_id, pos.side_code, e.value, pos.start_measure, pos.end_measure, a.modified_by, a.modified_date,
            case when a.valid_to <= current_timestamp then 1 else 0 end as expired, a.created_by, a.created_date, pos.adjusted_timestamp,
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
            and floating = '0'
            and (
              (a.valid_to > $sinceDate and a.valid_to <= $untilDate)
              or
              (a.modified_date > $sinceDate and a.modified_date <= $untilDate)
              or
              (a.created_date > $sinceDate and a.created_date <= $untilDate)
            )
            #$withAutoAdjustFilter
        ) derivedAsset #$recordLimit
    """.as[SpeedLimitRow].list

    groupSpeedLimitsResult(speedLimitRows)
  }

  /**
    * Returns m-values and side code by asset id. Used by PostGISSpeedLimitDao.splitSpeedLimit.
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
        select ?, ?, ? where not exists (select * from unknown_speed_limit where link_id = ?)
      """)
    try {
      limits.foreach { limit =>
        statement.setString(1, limit.linkId)
        statement.setInt(2, limit.municipalityCode)
        statement.setInt(3, limit.administrativeClass.value)
        statement.setString(4, limit.linkId)
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
  def createSpeedLimit(creator: String, linkId: String, linkMeasures: Measures, sideCode: SideCode, value: SpeedLimitValue,
                       timeStamp: Long, municipalityValidation: (Int, AdministrativeClass) => Unit): Option[Long] = {
    val roadLink = roadLinkService.fetchRoadlinkAndComplementary(linkId)
    municipalityValidation(roadLink.get.municipalityCode, roadLink.get.administrativeClass)
    createSpeedLimitWithoutDuplicates(creator, linkId, linkMeasures, sideCode, value, None, None, None, None, roadLink.get.linkSource)
  }

  /**
    * Creates new speed limit. Returns id of new speed limit. SpeedLimitService.persistProjectedLimit and SpeedLimitService.separate.
    */
  def createSpeedLimit(creator: String, linkId: String, linkMeasures: Measures, sideCode: SideCode, value: SpeedLimitValue, timeStamp: Option[Long], createdDate: Option[DateTime] = None, modifiedBy: Option[String] = None, modifiedAt: Option[DateTime] = None, linkSource: LinkGeomSource): Option[Long]  =
    createSpeedLimitWithoutDuplicates(creator, linkId, linkMeasures, sideCode, value, timeStamp, createdDate, modifiedBy, modifiedAt, linkSource)

  private def createSpeedLimitWithoutDuplicates(creator: String, linkId: String, linkMeasures: Measures, sideCode: SideCode, value: SpeedLimitValue, timeStamp: Option[Long], createdDate: Option[DateTime], modifiedBy: Option[String], modifiedAt: Option[DateTime], linkSource: LinkGeomSource): Option[Long] = {
    val existingLrmPositions = fetchSpeedLimitsByLinkId(linkId).filter(sl => sideCode == SideCode.BothDirections || sl.sideCode == sideCode)

    val remainders = existingLrmPositions.map {speedLimit =>
      (speedLimit.startMeasure, speedLimit.endMeasure) }.foldLeft(Seq((linkMeasures.startMeasure, linkMeasures.endMeasure)))(GeometryUtils.subtractIntervalFromIntervals).filter { case (start, end) => math.abs(end - start) > 0.01 }
    if (remainders.length == 1) {
      Some(forceCreateSpeedLimit(creator, SpeedLimitAsset.typeId, linkId, linkMeasures, sideCode, Some(value), (id, value) => insertProperties(id, value), Some(timeStamp.getOrElse(LinearAssetUtils.createTimeStamp())), createdDate, modifiedBy, modifiedAt, linkSource))
    } else {
      None
    }
  }
  /**
    * Saves enumerated value to db. Used by PostGISSpeedLimitDao.createSpeedLimitWithoutDuplicates and AssetDataImporter.splitSpeedLimits.
    */
  def insertSingleChoiceValue(assetId: Long, valuePropertyId: String, value: Int): Unit  = {
    val propertyId = Q.query[(String, Int), Long](Queries.propertyIdByPublicIdAndTypeId).apply(valuePropertyId, SpeedLimitAsset.typeId).first
    Queries.insertSingleChoiceProperty(assetId, propertyId, value).execute
  }

  /**
    * Saves enumerated value to db. Used by PostGISSpeedLimitDao.createSpeedLimitWithoutDuplicates and AssetDataImporter.splitSpeedLimits.
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
  def forceCreateSpeedLimit(creator: String, typeId: Int, linkId: String, linkMeasures: Measures, sideCode: SideCode, value: Option[SpeedLimitValue], valueInsertion: (Long, SpeedLimitValue) => Unit, timeStamp: Option[Long], createdDate: Option[DateTime], modifiedBy: Option[String], modifiedAt: Option[DateTime], linkSource: LinkGeomSource): Long = {
    val assetId = Sequences.nextPrimaryKeySeqValue
    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
    val sideCodeValue = sideCode.value

    val creationDate = createdDate match {
      case Some(datetime) => s"""TO_TIMESTAMP('${datetime}', 'YYYY-MM-DD"T"HH24:MI:SS.FF3')"""
      case None => "current_timestamp"
    }

    val modifiedDate = modifiedAt match {
      case Some(datetime) => s"""TO_TIMESTAMP('$datetime', 'YYYY-MM-DD"T"HH24:MI:SS.FF3')"""
      case None => "NULL"
    }

    val latestModifiedBy = modifiedBy match {
      case Some(modifier) => s"""'$modifier'"""
      case None => null
    }

    val insertAll =
      s"""
         insert into asset(id, asset_type_id, created_by, created_date, modified_by, modified_date)
         values ($assetId, $typeId, '$creator', $creationDate, $latestModifiedBy, $modifiedDate);

         insert into lrm_position(id, start_measure, end_measure, link_id, side_code, adjusted_timestamp, modified_date, link_source)
         values ($lrmPositionId, ${linkMeasures.startMeasure}, ${linkMeasures.endMeasure}, '$linkId', $sideCodeValue, ${timeStamp.getOrElse(0)}, current_timestamp, ${linkSource.value});

         insert into asset_link(asset_id, position_id)
         values ($assetId, $lrmPositionId);
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
        sqlu"""update asset set floating = '1' where id in (select id from #$idTableName)""".execute
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
    * Updates m-values and time stamp in db. Used by PostGISSpeedLimitDao.splitSpeedLimit.
    */
  def updateMValues(id: Long, linkMeasures: (Double, Double), timeStamp: Option[Long] = None): Unit = {
    val (startMeasure, endMeasure) = linkMeasures

    val adjusted_timestamp =  timeStamp match {
      case Some(timeStamp) => timeStamp
      case _ => LinearAssetUtils.createTimeStamp()
    }

    sqlu"""
      update LRM_POSITION
      set
        start_measure = $startMeasure,
        end_measure = $endMeasure,
        adjusted_timestamp = $adjusted_timestamp,
        modified_date = current_timestamp
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
      sqlu"update asset set valid_to = current_timestamp where id = $id".first
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
      sqlu"update asset set valid_to = current_timestamp where id = $id".first

    if (propertiesUpdated == 1) {
      Some(id)
    } else {
      None
    }
  }

  def updateExpirations(ids: Set[Long],username:String) = {
    Queries.updateAssestModified(ids.toSeq, username)
     sqlu"update asset set valid_to = current_timestamp where id in (${ids.mkString(",")})".execute
  }

  /**
    * Removes speed limits from unknown speed limits list. Used by SpeedLimitService.purgeUnknown.
    */
  def purgeFromUnknownSpeedLimits(linkId: String, roadLinkLength: Double): Unit = {
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
  def deleteUnknownSpeedLimits(linkIds: Seq[String]): Unit = {
    MassQuery.withStringIds(linkIds.toSet) { idTableName =>
      sqlu"""delete from unknown_speed_limit where link_id in (select id from #$idTableName)""".execute
    }
  }

  /**
    * Update administrative_class and municipality_code of links in the unknown speed limits list. Used by removeUnnecessaryUnknownSpeedLimits batch.
    */
  def updateUnknownSpeedLimitAdminClassAndMunicipality(linkId: String, administrativeClass: AdministrativeClass, municipalityCode: Int): Unit = {
    sqlu"""update unknown_speed_limit
          set administrative_class = ${administrativeClass.value}, municipality_code = $municipalityCode
          where link_id = $linkId""".execute
  }

  def hideUnknownSpeedLimits(linkIds: Set[String]): Set[String] = {
    sqlu"""update unknown_speed_limit set unnecessary = 1 where link_id in (#${linkIds.map(id => s"'$id'").mkString(",")})""".execute
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

  private def createGeometryForSegment(topology: Seq[RoadLink])(segment: PieceWiseLinearAsset) = {
    val roadLink = topology.find(_.linkId == segment.linkId).get
    val geometry = GeometryUtils.truncateGeometry3D(roadLink.geometry, segment.startMeasure, segment.endMeasure)
    segment.copy(geometry = geometry, endpoints = geometry.toSet)
  }

  /**
    * Sets floating flag of linear assets true in db. Used in LinearAssetService.drop.
    */
  def floatLinearAssets(ids: Set[Long]): Unit = {
    if (ids.nonEmpty) {
      MassQuery.withIds(ids) { idTableName =>
        sqlu"""update asset set floating = '1' where id in (select id from #$idTableName)""".execute
      }
    }
  }
}