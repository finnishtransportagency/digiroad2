package fi.liikennevirasto.digiroad2.dao.linearasset

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
import org.slf4j.LoggerFactory
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedParameters, PositionedResult, SetParameter, StaticQuery => Q}


case class ProhibitionsRow(id: Long, linkId: Long, sideCode: Int, prohibitionId: Long, prohibitionType: Int, validityPeriodType: Option[Int],
                           startHour: Option[Int], endHour: Option[Int], exceptionType: Option[Int], startMeasure: Double,
                           endMeasure: Double, createdBy: Option[String], createdDate: Option[DateTime], modifiedBy: Option[String], modifiedDate: Option[DateTime],
                           expired: Boolean, vvhTimeStamp: Long, geomModifiedDate: Option[DateTime], startMinute: Option[Int], endMinute: Option[Int],
                           additionalInfo: String, linkSource: Int, verifiedBy: Option[String], verifiedDate: Option[DateTime])

case class AssetLastModification(id: Long, linkId: Long, modifiedBy: Option[String], modifiedDate: Option[DateTime])

class OracleLinearAssetDao(val vvhClient: VVHClient, val roadLinkService: RoadLinkService ) {
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
    val filterExpired = if (includeExpired) "" else " and (a.valid_to > sysdate or a.valid_to is null)"
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

  def fetchExpireAssetLastModificationsByLinkIds(assetTypeId: Int, linkIds: Seq[Long]) : Seq[AssetLastModification] = {
    MassQuery.withIds(linkIds.toSet) { idTableName =>
      val assets = sql"""
        select a.id, pos.link_id, a.modified_date, a.modified_by
          from asset a
          join asset_link al on a.id = al.asset_id
          join lrm_position pos on al.position_id = pos.id
          join #$idTableName i on i.id = pos.link_id
          join (select pos.link_id, max(a.modified_date) as modified_date
            from asset a
            join asset_link al on a.id = al.asset_id
            join lrm_position pos on al.position_id = pos.id
            join #$idTableName i on i.id = pos.link_id
            group by pos.link_id) asset_lk on asset_lk.link_id = pos.link_id and asset_lk.modified_date = a.modified_date
        where asset_type_id = $assetTypeId and a.floating = 0 and  a.valid_to is not null and a.valid_to < sysdate"""
        .as[(Long, Long, Option[DateTime], Option[String])].list
      assets.map { case(id, linkId, modifiedDate, modifiedBy) =>
        AssetLastModification(id, linkId, modifiedBy, modifiedDate)
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
          and (a.valid_to > sysdate or a.valid_to is null)
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
          and (a.valid_to > sysdate or a.valid_to is null)
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
          and (a.valid_to > sysdate or a.valid_to is null)
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

  /**
    * Saves number property value to db. Used by LinearAssetService.createWithoutTransaction.
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
    * Saves linear asset to db. Returns id of new linear asset. Used by AssetDataImporter.splitLinearAssets.
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

    value.foreach(valueInsertion(assetId, _))

    assetId
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
    * Updates side codes in db. Used by LinearAssetService.persistSideCodeAdjustments and LinearAssetService.separate.
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
    * Creates new linear asset. Return id of new asset. Used by LinearAssetService.createWithoutTransaction
    */
  def createLinearAsset(typeId: Int, linkId: Long, expired: Boolean, sideCode: Int, measures: Measures, username: String, vvhTimeStamp: Long = 0L, linkSource: Option[Int],
                        fromUpdate: Boolean = false, createdByFromUpdate: Option[String] = Some(""),  createdDateTimeFromUpdate: Option[DateTime] = Some(DateTime.now()),
                        verifiedBy: Option[String] = None, verifiedDateFromUpdate: Option[DateTime] = None): Long = {
    val id = Sequences.nextPrimaryKeySeqValue
    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
    val validTo = if (expired) "sysdate" else "null"
    val verifiedDate = if (verifiedBy.getOrElse("") == "") "null" else "sysdate"

    if (fromUpdate) {
      verifiedDateFromUpdate match {
        case Some(value) => sqlu"""
      insert all
        into asset(id, asset_type_id, created_by, created_date, valid_to, modified_by, modified_date, verified_by, verified_date)
        values ($id, $typeId, $createdByFromUpdate, $createdDateTimeFromUpdate, #$validTo, $username, sysdate, $verifiedBy, $verifiedDateFromUpdate)

        into lrm_position(id, start_measure, end_measure, link_id, side_code, modified_date, adjusted_timestamp, link_source)
        values ($lrmPositionId, ${measures.startMeasure}, ${measures.endMeasure}, $linkId, $sideCode, sysdate, $vvhTimeStamp, $linkSource)

        into asset_link(asset_id, position_id)
        values ($id, $lrmPositionId)
      select * from dual
    """.execute
        case None => sqlu"""
      insert all
        into asset(id, asset_type_id, created_by, created_date, valid_to, modified_by, modified_date, verified_by, verified_date)
        values ($id, $typeId, $createdByFromUpdate, $createdDateTimeFromUpdate, #$validTo, $username, sysdate, $verifiedBy, #$verifiedDate)

        into lrm_position(id, start_measure, end_measure, link_id, side_code, modified_date, adjusted_timestamp, link_source)
        values ($lrmPositionId, ${measures.startMeasure}, ${measures.endMeasure}, $linkId, $sideCode, sysdate, $vvhTimeStamp, $linkSource)

        into asset_link(asset_id, position_id)
        values ($id, $lrmPositionId)
      select * from dual
    """.execute
      }
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
          and (a.valid_to IS NULL OR a.valid_to > SYSDATE )
          and a.floating = 0
          and a.id = $assetId
      """.as[(Long, Double, Double)].firstOption
    lrmInfo
  }

  def updateVerifiedInfo(ids: Set[Long], verifiedBy: String): Unit = {
      sqlu"update asset set verified_by = $verifiedBy, verified_date = sysdate where id in (#${ids.mkString(",")})".execute
  }

  def getUnVerifiedLinearAsset(assetTypeId: Int): List[(Long, Long)] = {
    sql"""
          Select a.id, pos.link_id
          from ASSET a
          join ASSET_LINK al on a.id = al.asset_id
          join LRM_POSITION pos on al.position_id = pos.id
          where a.asset_type_id = $assetTypeId
          and(valid_to is NULL OR valid_to >= SYSDATE)
          and a.created_by in ('dr1_conversion', 'dr1conversion') AND a.modified_date is NULL AND a.verified_date is NULL
          and a.floating = 0
      """.as[(Long, Long)].list
  }

}

