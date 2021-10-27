package fi.liikennevirasto.digiroad2.dao.linearasset

import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.postgis.{MassQuery, PostGISDatabase}
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import com.github.tototoshi.slick.MySQLJodaSupport._
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.dao.Queries.{insertMultipleChoiceValue, multipleChoicePropertyValuesByAssetIdAndPropertyId}
import fi.liikennevirasto.digiroad2.dao.{Queries, Sequences}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.Measures
import org.slf4j.{Logger, LoggerFactory}
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedResult, StaticQuery => Q}

import scala.language.implicitConversions

case class ProhibitionsRow(id: Long, linkId: Long, sideCode: Int, prohibitionId: Long, prohibitionType: Int, validityPeriodType: Option[Int],
                           startHour: Option[Int], endHour: Option[Int], exceptionType: Option[Int], startMeasure: Double,
                           endMeasure: Double, createdBy: Option[String], createdDate: Option[DateTime], modifiedBy: Option[String], modifiedDate: Option[DateTime],
                           expired: Boolean, vvhTimeStamp: Long, geomModifiedDate: Option[DateTime], startMinute: Option[Int], endMinute: Option[Int],
                           additionalInfo: String, linkSource: Int, verifiedBy: Option[String], verifiedDate: Option[DateTime], informationSource: Option[Int], isSuggested: Boolean = false)

case class AssetLastModification(id: Long, linkId: Long, modifiedBy: Option[String], modifiedDate: Option[DateTime])
case class AssetLink(id: Long, linkId: Long)


class PostGISLinearAssetDao(val vvhClient: VVHClient, val roadLinkService: RoadLinkService ) {
  implicit def bool2int(b:Boolean) = if (b) 1 else 0
  val logger: Logger = LoggerFactory.getLogger(getClass)

  implicit val getProhibitionsRow: GetResult[ProhibitionsRow] = new GetResult[ProhibitionsRow] {
    def apply(r: PositionedResult) : ProhibitionsRow = {

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
      val prohibitionAdditionalInfo = r.nextStringOption().getOrElse("")
      val linkSource = r.nextInt()
      val verifiedBy = r.nextStringOption()
      val verifiedDate = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val informationSource = r.nextIntOption()
      val isSuggested = r.nextBoolean()

      ProhibitionsRow(id, linkId, sideCode, prohibitionId, prohibitionType, validityPeridoType, validityPeridoStartHour, validityPeridoEndHour,
                      exceptionType, startMeasure, endMeasure, createdBy, createdDate, modifiedBy, modifiedDate, expired, vvhTimeStamp,
                      geomModifiedDate, validityPeridoStartMinute, validityPeridoEndMinute, prohibitionAdditionalInfo, linkSource,
                      verifiedBy, verifiedDate, informationSource, isSuggested)

    }
  }

  implicit val getLinearAsset: GetResult[PersistedLinearAsset] = new GetResult[PersistedLinearAsset] {
    def apply(r: PositionedResult) : PersistedLinearAsset = {
      val id = r.nextLong()
      val linkId = r.nextLong()
      val sideCode = r.nextInt()
      val value = r.nextIntOption().map(NumericValue)
      val startMeasure = r.nextDouble()
      val endMeasure = r.nextDouble()
      val createdBy = r.nextStringOption()
      val createdDate = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val modifiedBy = r.nextStringOption()
      val modifiedDate = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val expired = r.nextBoolean()
      val typeId = r.nextInt()
      val vvhTimeStamp = r.nextLong()
      val geomModifiedDate = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val linkSource = LinkGeomSource.apply(r.nextInt())
      val verifiedBy = r.nextStringOption()
      val verifiedDate = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val informationSource = r.nextIntOption()


      PersistedLinearAsset(id, linkId, sideCode, value, startMeasure, endMeasure, createdBy, createdDate, modifiedBy, modifiedDate, expired, typeId, vvhTimeStamp, geomModifiedDate,
        linkSource, verifiedBy, verifiedDate, informationSource.map(info => InformationSource.apply(info)))
    }
  }

  implicit val getPiecewise = new GetResult[PieceWiseLinearAsset] {
    def apply(r: PositionedResult) = {
      val id = r.nextLong()
      val linkId = r.nextLong()
      val sideCode = r.nextInt()
      val value = r.nextIntOption().map(NumericValue)
      val startMeasure = r.nextDouble()
      val endMeasure = r.nextDouble()
      val createdBy = r.nextStringOption()
      val createdDate = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val modifiedBy = r.nextStringOption()
      val modifiedDate = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val expired = r.nextBoolean()
      val typeId = r.nextInt()
      val vvhTimeStamp = r.nextLong()
      val geomModifiedDate = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val linkSource = LinkGeomSource.apply(r.nextInt())
      val verifiedBy = r.nextStringOption()
      val verifiedDate = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val informationSource = r.nextIntOption()
      val startPoint_x = r.nextDouble()
      val startPoint_y = r.nextDouble()
      val endPoint_x = r.nextDouble()
      val endPoint_y = r.nextDouble()
      val administrativeClass = AdministrativeClass(r.nextInt())

      val geometry = Seq(Point(startPoint_x, startPoint_y), Point(endPoint_x, endPoint_y))
      PieceWiseLinearAsset(id, linkId, SideCode(sideCode), value, geometry, expired, startMeasure, endMeasure,
                           geometry.toSet, modifiedBy, modifiedDate, createdBy, createdDate, typeId, SideCode.toTrafficDirection(SideCode(sideCode)), vvhTimeStamp,
                           geomModifiedDate, linkSource, administrativeClass, verifiedBy = verifiedBy, verifiedDate = verifiedDate, informationSource = informationSource.map(info => InformationSource.apply(info)))


    }
  }

  implicit val getLightLinearAssets: GetResult[LightLinearAsset] = new GetResult[LightLinearAsset] {
    def apply(r: PositionedResult) : LightLinearAsset = {
      val expired = r.nextBoolean()
      val value = r.nextInt()
      val typeId = r.nextInt()
      val startPoint_x = r.nextDouble()
      val startPoint_y = r.nextDouble()
      val endPoint_x = r.nextDouble()
      val endPoint_y = r.nextDouble()
      val geometry = Seq(Point(startPoint_x, startPoint_y), Point(endPoint_x, endPoint_y))
      val sideCode = r.nextInt()
      LightLinearAsset(geometry, value, expired, typeId, sideCode)
    }
  }

  /**
    * Iterates a set of asset ids with a property id and returns linear assets. Used by LinearAssetService.getPersistedAssetsByIds,
    * LinearAssetService.split and LinearAssetService.separate.
    */
  def fetchLinearAssetsByIds(ids: Set[Long], valuePropertyId: String): Seq[PersistedLinearAsset] = {
    MassQuery.withIds(ids) { idTableName =>
      sql"""
        select a.id, pos.link_id, pos.side_code, s.value, pos.start_measure, pos.end_measure,
               a.created_by, a.created_date, a.modified_by, a.modified_date,
               case when a.valid_to <= current_timestamp then 1 else 0 end as expired, a.asset_type_id,
               pos.adjusted_timestamp, pos.modified_date, pos.link_source, a.verified_by, a.verified_date, a.information_source
          from asset a
          join asset_link al on a.id = al.asset_id
          join lrm_position pos on al.position_id = pos.id
          join property p on p.public_id = $valuePropertyId
          join #$idTableName i on i.id = a.id
          left join number_property_value s on s.asset_id = a.id and s.property_id = p.id
          where a.floating = '0'
      """.as[PersistedLinearAsset].list
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
               case when a.valid_to <= current_timestamp then 1 else 0 end as expired, a.asset_type_id,
               pos.adjusted_timestamp, pos.modified_date, pos.link_source, a.verified_by, a.verified_date, a.information_source
          from asset a
          join asset_link al on a.id = al.asset_id
          join lrm_position pos on al.position_id = pos.id
          join property p on p.public_id = $valuePropertyId
          join #$idTableName i on i.id = a.id
          left join text_property_value s on s.asset_id = a.id and s.property_id = p.id
          where a.floating = '0'
      """.as[(Long, Long, Int, Option[String], Double, Double, Option[String], Option[DateTime], Option[String], Option[DateTime], Boolean, Int, Long, Option[DateTime], Int, Option[String], Option[DateTime], Option[Int])].list
      assets.map { case (id, linkId, sideCode, value, startMeasure, endMeasure, createdBy, createdDate, modifiedBy, modifiedDate, expired, typeId, vvhTimeStamp, geomModifiedDate, linkSource, verifiedBy, verifiedDate, informationSource) =>
        PersistedLinearAsset(id, linkId, sideCode, value.map(TextualValue), startMeasure, endMeasure, createdBy, createdDate, modifiedBy, modifiedDate, expired, typeId, vvhTimeStamp, geomModifiedDate, LinkGeomSource.apply(linkSource), verifiedBy, verifiedDate, informationSource.map{info => InformationSource(info)})
      }
    }
  }

  /**
    * Iterates a set of link ids with asset type id and property id and returns linear assets. Used by LinearAssetService.getByRoadLinks.
    */
  def fetchLinearAssetsByLinkIds(assetTypeId: Int, linkIds: Seq[Long], valuePropertyId: String, includeExpired: Boolean = false): Seq[PersistedLinearAsset] = {
    val filterExpired = if (includeExpired) "" else " and (a.valid_to > current_timestamp or a.valid_to is null)"
    MassQuery.withIds(linkIds.toSet) { idTableName =>
      sql"""
        select a.id, pos.link_id, pos.side_code, s.value as total_weight_limit, pos.start_measure, pos.end_measure,
               a.created_by, a.created_date, a.modified_by, a.modified_date,
               case when a.valid_to <= current_timestamp then 1 else 0 end as expired, a.asset_type_id,
               pos.adjusted_timestamp, pos.modified_date, pos.link_source, a.verified_by, a.verified_date, a.information_source
          from asset a
          join asset_link al on a.id = al.asset_id
          join lrm_position pos on al.position_id = pos.id
          join property p on p.public_id = $valuePropertyId
          join #$idTableName i on i.id = pos.link_id
          left join number_property_value s on s.asset_id = a.id and s.property_id = p.id
          where a.asset_type_id = $assetTypeId
          and a.floating = '0'
          #$filterExpired"""
        .as[PersistedLinearAsset].list
    }
  }

  def fetchAssetsByLinkIds(assetTypeId: Set[Int], linkIds: Seq[Long], includeExpired: Boolean = false): Seq[AssetLink] = {
    val filterExpired = if (includeExpired) "" else " and (a.valid_to > current_timestamp or a.valid_to is null)"
    MassQuery.withIds(linkIds.toSet) { idTableName =>
      sql"""
        select a.id, pos.link_id
          from asset a
          join asset_link al on a.id = al.asset_id
          join lrm_position pos on al.position_id = pos.id
          join #$idTableName i on i.id = pos.link_id
          where a.asset_type_id in (#${assetTypeId.mkString(",")}) and a.floating = '0' #$filterExpired""".as[AssetLink](getAssetLink).list
    }
  }

  implicit val getAssetLink = new GetResult[AssetLink] {
    def apply(r: PositionedResult) = {
      val id = r.nextLong()
      val linkId = r.nextLong()

      AssetLink(id, linkId)
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
        where asset_type_id = $assetTypeId and a.floating = '0' and  a.valid_to is not null and a.valid_to < current_timestamp"""
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
               case when a.valid_to <= current_timestamp then 1 else 0 end as expired, a.asset_type_id,
               pos.adjusted_timestamp, pos.modified_date, pos.link_source, a.verified_by, a.verified_date, a.information_source
          from asset a
          join asset_link al on a.id = al.asset_id
          join lrm_position pos on al.position_id = pos.id
          join property p on p.public_id = $valuePropertyId
          join #$idTableName i on i.id = pos.link_id
          left join text_property_value s on s.asset_id = a.id and s.property_id = p.id
          where a.asset_type_id = $assetTypeId
          and (a.valid_to > current_timestamp or a.valid_to is null)
          and a.floating = '0'"""
        .as[(Long, Long, Int, Option[String], Double, Double, Option[String], Option[DateTime], Option[String], Option[DateTime], Boolean, Int, Long, Option[DateTime], Int, Option[String], Option[DateTime], Option[Int])].list
      assets.map { case(id, linkId, sideCode, value, startMeasure, endMeasure, createdBy, createdDate, modifiedBy, modifiedDate, expired, typeId, vvhTimeStamp, geomModifiedDate, linkSource, verifiedBy, verifiedDate, informationSource) =>
        PersistedLinearAsset(id, linkId, sideCode, value.map(TextualValue), startMeasure, endMeasure, createdBy, createdDate, modifiedBy, modifiedDate, expired, typeId, vvhTimeStamp, geomModifiedDate, LinkGeomSource.apply(linkSource), verifiedBy, verifiedDate, informationSource.map{info => InformationSource(info)})
      }
    }
  }

  def groupProhibitionsResult(assets: List[ProhibitionsRow], assetTypeId: Int): Seq[PersistedLinearAsset] = {
    val groupedByAssetId = assets.groupBy(_.id)
    val groupedByProhibitionId = groupedByAssetId.mapValues(_.groupBy(_.prohibitionId))

    groupedByProhibitionId.map { case (assetId, rowsByProhibitionId) =>
      val asset = groupedByAssetId(assetId).head
      val prohibitionValues = rowsByProhibitionId.keys.toSeq.sorted.map { prohibitionId =>
        val rows = rowsByProhibitionId(prohibitionId)
        val prohibitionType = rows.head.prohibitionType
        val prohibitionAdditionalInfo = rows.head.additionalInfo
        val exceptions = rows.flatMap(_.exceptionType).toSet
        val validityPeriods = rows.filter(_.validityPeriodType.isDefined).map { row =>
          ValidityPeriod(row.startHour.get, row.endHour.get, ValidityPeriodDayOfWeek(row.validityPeriodType.get), row.startMinute.get, row.endMinute.get)
        }.toSet
        ProhibitionValue(prohibitionType, validityPeriods, exceptions, prohibitionAdditionalInfo)
      }
      PersistedLinearAsset(assetId, asset.linkId, asset.sideCode, Some(Prohibitions(prohibitionValues, asset.isSuggested)), asset.startMeasure, asset.endMeasure, asset.createdBy,
        asset.createdDate, asset.modifiedBy, asset.modifiedDate, asset.expired, assetTypeId, asset.vvhTimeStamp, asset.geomModifiedDate, LinkGeomSource.apply(asset.linkSource),
        asset.verifiedBy, asset.verifiedDate, asset.informationSource.map(info => InformationSource.apply(info)))
    }.toSeq
  }

  /**
    * Iterates a set of link ids with prohibition asset type id and floating flag and returns linear assets. Used by LinearAssetService.getByRoadLinks
    * and CsvGenerator.generateDroppedProhibitions.
    */
  def fetchProhibitionsByLinkIds(prohibitionAssetTypeId: Int, linkIds: Seq[Long], includeFloating: Boolean = false): Seq[PersistedLinearAsset] = {
    val floatingFilter = if (includeFloating) "" else "and a.floating = '0'"

    val assets = MassQuery.withIds(linkIds.toSet) { idTableName =>
      sql"""
        select a.id, pos.link_id, pos.side_code,
               pv.id, pv.type,
               pvp.type, pvp.start_hour, pvp.end_hour,
               pe.type,
               pos.start_measure, pos.end_measure,
               a.created_by, a.created_date, a.modified_by, a.modified_date,
               case when a.valid_to <= current_timestamp then 1 else 0 end as expired,
               pos.adjusted_timestamp, pos.modified_date, pvp.start_minute,
               pvp.end_minute, pv.additional_info, pos.link_source, a.verified_by, a.verified_date, a.information_source, e.value as suggested
          from asset a
          join asset_link al on a.id = al.asset_id
          join lrm_position pos on al.position_id = pos.id
          join prohibition_value pv on pv.asset_id = a.id
          join #$idTableName i on i.id = pos.link_id
          join property p on a.asset_type_id = p.asset_type_id
          left join prohibition_validity_period pvp on pvp.prohibition_value_id = pv.id
          left join prohibition_exception pe on pe.prohibition_value_id = pv.id
          left join multiple_choice_value mc on mc.asset_id = a.id and mc.property_id = p.id and p.property_type = 'checkbox'
          left join enumerated_value e on mc.enumerated_value_id = e.id
          where a.asset_type_id = $prohibitionAssetTypeId
          and (a.valid_to > current_timestamp or a.valid_to is null)
          #$floatingFilter""".as[ProhibitionsRow].list
    }

    groupProhibitionsResult(assets, prohibitionAssetTypeId)
  }

  /**
    * Iterates a set of asset ids with prohibition asset type id and floating flag and returns linear assets. User by LinearAssetSErvice.getPersistedAssetsByIds.
    */
  def fetchProhibitionsByIds(prohibitionAssetTypeId: Int, ids: Set[Long], includeFloating: Boolean = false): Seq[PersistedLinearAsset] = {
    val floatingFilter = if (includeFloating) "" else "and a.floating = '0'"

    val assets = MassQuery.withIds(ids.toSet) { idTableName =>
      sql"""
        select a.id, pos.link_id, pos.side_code,
               pv.id, pv.type,
               pvp.type, pvp.start_hour, pvp.end_hour,
               pe.type,
               pos.start_measure, pos.end_measure,
               a.created_by, a.created_date, a.modified_by, a.modified_date,
               case when a.valid_to <= current_timestamp then 1 else 0 end as expired,
               pos.adjusted_timestamp, pos.modified_date, pvp.start_minute,
               pvp.end_minute, pv.additional_info, pos.link_source,
               a.verified_by, a.verified_date, a.information_source, e.value as suggested
          from asset a
          join asset_link al on a.id = al.asset_id
          join lrm_position pos on al.position_id = pos.id
          join prohibition_value pv on pv.asset_id = a.id
          join #$idTableName i on i.id = a.id
          join property p on a.asset_type_id = p.asset_type_id
          left join prohibition_validity_period pvp on pvp.prohibition_value_id = pv.id
          left join prohibition_exception pe on pe.prohibition_value_id = pv.id
          left join multiple_choice_value mc on mc.asset_id = a.id and mc.property_id = p.id and p.property_type = 'checkbox'
          left join enumerated_value e on mc.enumerated_value_id = e.id
          where a.asset_type_id = $prohibitionAssetTypeId
          and (a.valid_to > current_timestamp or a.valid_to is null)
          #$floatingFilter"""
        .as[ProhibitionsRow].list
    }

    groupProhibitionsResult(assets, prohibitionAssetTypeId)
  }

  def getLinearAssetsChangedSince(assetTypeId: Int, sinceDate: DateTime, untilDate: DateTime, withAdjust: Boolean, token: Option[String] = None) : List[PersistedLinearAsset] = {
    val withAutoAdjustFilter = if (withAdjust) "" else "and (a.modified_by is null OR a.modified_by != 'vvh_generated')"
    val recordLimit = token match {
      case Some(tk) =>
        val (startNum, endNum) = Decode.getPageAndRecordNumber(tk)

        s"WHERE line_number between $startNum and $endNum"

      case _ => ""
    }

    val assets = sql"""
        select asset_id, link_id, side_code, value, start_measure, end_measure, created_by, created_date, modified_by, modified_date,
               expired, asset_type_id, adjusted_timestamp, pos_modified_date, link_source, verified_by, verified_date, information_source
        from (
          select a.id as asset_id, pos.link_id, pos.side_code, s.value, pos.start_measure, pos.end_measure,
                 a.created_by, a.created_date, a.modified_by, a.modified_date,
                 case when a.valid_to <= current_timestamp then 1 else 0 end as expired, a.asset_type_id, pos.adjusted_timestamp,
                 pos.modified_date as pos_modified_date, pos.link_source, a.verified_by, a.verified_date, a.information_source,
                 DENSE_RANK() over (ORDER BY a.id) line_number
            from asset a
            join asset_link al on a.id = al.asset_id
            join lrm_position pos on al.position_id = pos.id
            join property p on p.public_id = 'mittarajoitus'
            left join number_property_value s on s.asset_id = a.id and s.property_id = p.id
            where a.asset_type_id = $assetTypeId
            and (
              (a.valid_to > $sinceDate and a.valid_to <= $untilDate)
              or
              (a.modified_date > $sinceDate and a.modified_date <= $untilDate)
             or
             (a.created_date > $sinceDate and a.created_date <= $untilDate)
           )
           and a.floating = '0'
           #$withAutoAdjustFilter
           ) derivedAsset #$recordLimit"""
      .as[(Long, Long, Int, Option[Int], Double, Double, Option[String], Option[DateTime], Option[String], Option[DateTime], Boolean, Int, Long, Option[DateTime], Int, Option[String], Option[DateTime], Option[Int])].list

    assets.map { case(id, linkId, sideCode, value, startMeasure, endMeasure, createdBy, createdDate, modifiedBy, modifiedDate, expired, typeId, vvhTimeStamp, geomModifiedDate, linkSource, verifiedBy, verifiedDate, informationSource) =>
      PersistedLinearAsset(id, linkId, sideCode, value.map(NumericValue), startMeasure, endMeasure, createdBy, createdDate, modifiedBy, modifiedDate, expired, typeId, vvhTimeStamp, geomModifiedDate, LinkGeomSource.apply(linkSource), verifiedBy, verifiedDate, informationSource.map(info => InformationSource.apply(info)))
    }
  }

  def fetchLinearAssets(assetTypeId: Int, valuePropertyId: String, bounds: BoundingRectangle, linkSource: Option[LinkGeomSource] = None): Seq[LightLinearAsset] = {
    val linkGeomCondition = linkSource match {
      case Some(LinkGeomSource.NormalLinkInterface) => s" and pos.link_source = ${LinkGeomSource.NormalLinkInterface.value}"
      case _ => ""
    }
    val boundingBoxFilter = PostGISDatabase.boundingBoxFilter(bounds, "a.geometry")
    sql"""
         SELECT case when a.valid_to <= current_timestamp then 1 else 0 end as expired, CASE WHEN a.valid_to IS NULL THEN 1 ELSE NULL END AS value, a.asset_type_id, t.X, t.Y, t2.X, t2.Y, pos.side_code
          from asset a
          join asset_link al on a.id = al.asset_id
          join lrm_position pos on al.position_id = pos.id
          cross join TABLE(SDO_UTIL.GETVERTICES(a.geometry)) t
          cross join TABLE(SDO_UTIL.GETVERTICES(a.geometry)) t2
          where a.valid_to is null and a.floating = '0' and a.asset_type_id = #$assetTypeId #$linkGeomCondition and #$boundingBoxFilter"""
      .as[LightLinearAsset].list
  }

  def getProhibitionsChangedSince(assetTypeId: Int, sinceDate: DateTime, untilDate: DateTime, excludedTypes: Seq[ProhibitionClass], withAdjust: Boolean, token: Option[String] = None): Seq[PersistedLinearAsset] = {
    val withAutoAdjustFilter = if (withAdjust) "" else "and (a.modified_by is null OR a.modified_by != 'vvh_generated')"
    val excludedTypesValues = excludedTypes.map(_.value)

    val recordLimit = token match {
      case Some(tk) =>
        val (startNum, endNum) = Decode.getPageAndRecordNumber(tk)

        s"WHERE line_number between $startNum and $endNum"

      case _ => ""
    }

    val assets = sql"""
        select asset_id, link_id, side_code, pv_id, pv_type, pvp_type, start_hour, end_hour,pe_type,
               start_measure, end_measure, created_by, created_date, modified_by, modified_date,
               expired, adjusted_timestamp, pos_modified_date, start_minute, end_minute, additional_info,
               link_source, verified_by, verified_date, information_source, suggested
        from (
          select a.id as asset_id, pos.link_id, pos.side_code, pv.id as pv_id, pv.type as pv_type, pvp.type as pvp_type, pvp.start_hour, pvp.end_hour,
                 pe.type as pe_type, pos.start_measure, pos.end_measure, a.created_by, a.created_date, a.modified_by, a.modified_date,
                 case when a.valid_to <= current_timestamp then 1 else 0 end as expired,
                 pos.adjusted_timestamp, pos.modified_date as pos_modified_date, pvp.start_minute,
                 pvp.end_minute, pv.additional_info, pos.link_source,
                 a.verified_by, a.verified_date, a.information_source, e.value as suggested,
                 DENSE_RANK() over (ORDER BY a.id) line_number
          from asset a
          join asset_link al on a.id = al.asset_id
          join lrm_position pos on al.position_id = pos.id
          join prohibition_value pv on pv.asset_id = a.id
          join property p on a.asset_type_id = p.asset_type_id
          left join prohibition_validity_period pvp on pvp.prohibition_value_id = pv.id
          left join prohibition_exception pe on pe.prohibition_value_id = pv.id
          left join multiple_choice_value mc on mc.asset_id = a.id and mc.property_id = p.id and p.property_type = 'checkbox'
          left join enumerated_value e on mc.enumerated_value_id = e.id
          where a.asset_type_id = $assetTypeId and pv.TYPE not in (#${excludedTypesValues.mkString(",")} )
          and (
            (a.valid_to > $sinceDate and a.valid_to <= $untilDate)
          or
            (a.modified_date > $sinceDate and a.modified_date <= $untilDate)
          or
            (a.created_date > $sinceDate and a.created_date <= $untilDate)
          )
          #$withAutoAdjustFilter
       ) derivedAsset #$recordLimit""".as[ProhibitionsRow].list

    groupProhibitionsResult(assets, assetTypeId)
  }

  /**
    * Saves number property value to db. Used by LinearAssetService.createWithoutTransaction.
    */
  def insertValue(assetId: Long, valuePropertyId: String, value: Int, typeId: Option[Int] = None): Unit = {
    val numberPropertyValueId = Sequences.nextPrimaryKeySeqValue
    val propertyId =
      typeId match {
        case Some(assetTypeID) =>
          Q.query[(String, Int), Long](Queries.propertyIdByPublicIdAndTypeId).apply(valuePropertyId, assetTypeID).first
        case _ =>
          Q.query[String, Long](Queries.propertyIdByPublicId).apply(valuePropertyId).first
      }

    sqlu"""
       insert into number_property_value(id, asset_id, property_id, value)
       values ($numberPropertyValueId, $assetId, $propertyId, $value)
     """.execute
  }

  /**
    * Saves textual property value to db. Used by LinearAssetService.createWithoutTransaction.
    */
  def insertValue(assetId: Long, valuePropertyId: String, value: String): Unit = {
    val propertyId = Q.query[String, Long](Queries.propertyIdByPublicId).apply(valuePropertyId).first
    Queries.insertTextProperty(assetId, propertyId, value).execute
  }

  /**
    * Saves linear asset to db. Returns id of new linear asset. Used by AssetDataImporter.splitLinearAssets.
    */
  def forceCreateLinearAsset(creator: String, typeId: Int, linkId: Long, linkMeasures: Measures, sideCode: SideCode, value: Option[Int],
                             valueInsertion: (Long, Int) => Unit, vvhTimeStamp: Option[Long], createdDate: Option[DateTime],
                             modifiedBy: Option[String], modifiedAt: Option[DateTime], linkSource: LinkGeomSource): Long = {
    val assetId = Sequences.nextPrimaryKeySeqValue
    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
    val sideCodeValue = sideCode.value

    val creationDate = createdDate match {
      case Some(datetime) => s"""TO_TIMESTAMP('$datetime', 'YYYY-MM-DD"T"HH24:MI:SS.FF3')"""
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
         values ($lrmPositionId, ${linkMeasures.startMeasure}, ${linkMeasures.endMeasure}, $linkId, $sideCodeValue, ${vvhTimeStamp.getOrElse(0)}, current_timestamp, ${linkSource.value});

       insert into asset_link(asset_id, position_id)
         values ($assetId, $lrmPositionId);
      """
    Q.updateNA(insertAll).execute

    value.foreach(valueInsertion(assetId, _))

    assetId
  }

  /**
    * Updates m-values in db. Used by PostGISLinearAssetDao.splitSpeedLimit, LinearAssetService.persistMValueAdjustments and LinearAssetService.split.
    */
  def updateMValues(id: Long, linkMeasures: (Double, Double)): Unit = {
    val (startMeasure, endMeasure) = linkMeasures
    sqlu"""
      update LRM_POSITION
      set
        start_measure = $startMeasure,
        end_measure = $endMeasure,
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
        modified_date = current_timestamp,
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
          modified_date = current_timestamp
      where id = $id
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
    * Updates asset information Source in db.
    **/
  def updateInformationSource(typeId:Long, assetId: Long, informationSource: InformationSource): Unit = {
    sqlu"""
      update asset
      set information_source = ${informationSource.value}
      where id = $assetId and asset_type_id = $typeId
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
  def updateExpiration(id: Long): Option[Long] = {

    val propertiesUpdated =
      sqlu"update asset set valid_to = current_timestamp where id = $id".first

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
                        verifiedBy: Option[String] = None, verifiedDateFromUpdate: Option[DateTime] = None, informationSource: Option[Int] = None, geometry: Seq[Point] = Seq()): Long = {
    val id = Sequences.nextPrimaryKeySeqValue
    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
    val validTo = if (expired) "current_timestamp" else "null"
    val verifiedDate = if (verifiedBy.getOrElse("") == "") "null" else "current_timestamp"

    val geom: String = if (geometry.nonEmpty) {
      val geom = GeometryUtils.truncateGeometry2D(geometry, measures.startMeasure, measures.endMeasure)

      //TODO This IF clause it's to be removed when VIITE finally stops using frozen VVH because besides we have different road links, we can have different or empty geometries between them too. In that case we don't want to store that.
      if (geom.nonEmpty) {
        val assetLength = measures.endMeasure - measures.startMeasure
        val line = Queries.linearGeometry(Point(geom.head.x,geom.head.y),Point(geom.last.x,geom.last.y), assetLength)
        s"""ST_GeomFromText('$line', 3067)"""
      } else {
        "null"
      }

    } else {
      "null"
    }

    if (fromUpdate) {
      verifiedDateFromUpdate match {
        case Some(value) => sqlu"""

      insert  into asset(id, asset_type_id, created_by, created_date, valid_to, modified_by, modified_date, verified_by, verified_date, information_source, geometry)
        values ($id, $typeId, $createdByFromUpdate, $createdDateTimeFromUpdate, #$validTo, $username, current_timestamp, $verifiedBy, $verifiedDateFromUpdate, $informationSource, #$geom);

      insert into lrm_position(id, start_measure, end_measure, link_id, side_code, modified_date, adjusted_timestamp, link_source)
        values ($lrmPositionId, ${measures.startMeasure}, ${measures.endMeasure}, $linkId, $sideCode, current_timestamp, $vvhTimeStamp, $linkSource);

     insert   into asset_link(asset_id, position_id)
        values ($id, $lrmPositionId);
    """.execute
        case None => sqlu"""
       insert into asset(id, asset_type_id, created_by, created_date, valid_to, modified_by, modified_date, verified_by, verified_date, information_source, geometry)
        values ($id, $typeId, $createdByFromUpdate, $createdDateTimeFromUpdate, #$validTo, $username, current_timestamp, $verifiedBy, #$verifiedDate, $informationSource, #$geom);

       insert into lrm_position(id, start_measure, end_measure, link_id, side_code, modified_date, adjusted_timestamp, link_source)
        values ($lrmPositionId, ${measures.startMeasure}, ${measures.endMeasure}, $linkId, $sideCode, current_timestamp, $vvhTimeStamp, $linkSource);

      insert  into asset_link(asset_id, position_id)
        values ($id, $lrmPositionId);
    """.execute
      }
    } else {
      sqlu"""
     insert into asset(id, asset_type_id, created_by, created_date, valid_to, verified_by, verified_date, information_source, geometry)
      values ($id, $typeId, $username, current_timestamp, #$validTo, ${verifiedBy.getOrElse("")}, #$verifiedDate, $informationSource, #$geom);

      insert into lrm_position(id, start_measure, end_measure, link_id, side_code, modified_date, adjusted_timestamp, link_source)
      values ($lrmPositionId, ${measures.startMeasure}, ${measures.endMeasure}, $linkId, $sideCode, current_timestamp, $vvhTimeStamp, $linkSource);

      insert into asset_link(asset_id, position_id)
      values ($id, $lrmPositionId);
        """.execute
    }

    id
  }

  def insertConnectedAsset(linearId: Long, pointId : Long) : Unit =
    sqlu"""insert into connected_asset(linear_asset_id, point_asset_id) values ($linearId, $pointId)""".execute


  def expireConnectedByLinearAsset(id: Long) : Unit =
    sqlu"""update connected_asset set valid_to = current_timestamp where valid_to is not null and linear_asset_id = $id""".execute


  def expireConnectedByPointAsset(id: Long) : Unit = {
    sqlu"""update connected_asset set valid_to = current_timestamp where valid_to is not null and point_asset_id = $id""".execute
  }

  def getLastExecutionDateOfConnectedAsset(typeId: Int): Option[DateTime] = {
    sql"""select * from (
            select max(greatest( coalesce(con.created_date, con.modified_date , con.valid_to))) as lastExecutionDate
              from connected_asset con
              join asset a on a.id = con.linear_asset_id and a.asset_type_id = $typeId) derivedConnectedAsset
          where lastExecutionDate is not null
          """.as[DateTime].firstOption
  }

  def insertTrafficSignsToProcess(assetId: Long, linearAssetTypeId: Int, sign: String) : Unit = {
    sqlu""" insert into traffic_sign_manager (traffic_sign_id, linear_asset_type_id, sign)
           values ($assetId, $linearAssetTypeId, $sign)
           """.execute
  }

  def getTrafficSignsToProcess(typeId: Int) : Seq[Long] = {
    sql""" select traffic_sign_id
           from traffic_sign_manager
           where linear_asset_type_id = $typeId
           """.as[Long].list
  }

  def getTrafficSignsToProcessById(ids: Seq[Long]) : Seq[(Long, String)] = {
    MassQuery.withIds(ids.toSet) { idTableName =>
      sql""" select tsm.traffic_sign_id, tsm.sign
           from traffic_sign_manager tsm
              JOIN #$idTableName i on tsm.traffic_sign_id = i.id
           """.as[(Long, String)].list
    }
  }

  def deleteTrafficSignsToProcess(ids: Seq[Long], typeId: Int) : Unit = {
    MassQuery.withIds(ids.toSet) { idTableName =>
      sqlu"""DELETE FROM traffic_sign_manager
           WHERE linear_asset_type_id = $typeId
           AND traffic_sign_id IN ( SELECT id FROM #$idTableName )
         """.execute
    }
  }

  def getConnectedAssetFromTrafficSign(id: Long): Seq[Long] = {
    val linearAssetsIds = sql"""select linear_asset_id from connected_asset where point_asset_id = $id""".as[(Long)].list
    linearAssetsIds
  }

  def getConnectedAssetFromLinearAsset(ids: Seq[Long]): Seq[(Long, Long)] = {
    sql"""select linear_asset_id, point_asset_id from connected_asset where linear_asset_id in (#${ids.mkString(",")})""".as[(Long, Long)].list
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
  def updateProhibitionValue(id: Long, typeId: Int, value: Prohibitions, username: String, optMeasure: Option[Measures] = None ): Option[Long] = {
    Queries.updateAssetModified(id, username).first

    val propertyId = Q.query[(String, Int), Long](Queries.propertyIdByPublicIdAndTypeId).apply("suggest_box", typeId).first
    val currentIdsAndValue = Q.query[(Long, Long), (Long, Long)](multipleChoicePropertyValuesByAssetIdAndPropertyId).apply(id, propertyId).list

    if (currentIdsAndValue.isEmpty) {
      insertMultipleChoiceValue(id, propertyId, bool2int(value.isSuggested)).execute
    } else if(bool2int(value.isSuggested) != currentIdsAndValue.head._2 ) {
      Queries.deleteMultipleChoiceValue(currentIdsAndValue.head._1).execute
      insertMultipleChoiceValue(id, propertyId, bool2int(value.isSuggested)).execute
    }

    val prohibitionValueIds = sql"""select id from PROHIBITION_VALUE where asset_id = $id""".as[Int].list.mkString(",")
    if (prohibitionValueIds.nonEmpty) {
      sqlu"""delete from PROHIBITION_EXCEPTION where prohibition_value_id in (#$prohibitionValueIds)""".execute
      sqlu"""delete from PROHIBITION_VALIDITY_PERIOD where prohibition_value_id in (#$prohibitionValueIds)""".execute
      sqlu"""delete from PROHIBITION_VALUE where asset_id = $id""".execute
    }

    insertProhibitionValue(id, typeId, value)
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
    * Saves prohibition value to db. Used by PostGISLinearAssetDao.updateProhibitionValue and LinearAssetService.createWithoutTransaction.
    */
  def insertProhibitionValue(assetId: Long, typeId: Int, value: Prohibitions): Unit = {
    val propertyId = Q.query[(String, Int), Long](Queries.propertyIdByPublicIdAndTypeId).apply("suggest_box", typeId).first
    Queries.insertMultipleChoiceValue(assetId, propertyId, if(value.isSuggested) 1 else 0).execute

    value.prohibitions.foreach { prohibition: ProhibitionValue =>
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
    sqlu"update asset set valid_to = current_timestamp - INTERVAL'1 SECOND' where asset_type_id = $typeId".execute
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
          and (a.valid_to IS NULL OR a.valid_to > current_timestamp )
          and a.floating = '0'
          and a.id = $assetId
      """.as[(Long, Double, Double)].firstOption
    lrmInfo
  }

  def updateVerifiedInfo(ids: Set[Long], verifiedBy: String): Unit = {
    sqlu"update asset set verified_by = $verifiedBy, verified_date = current_timestamp where id in (#${ids.mkString(",")})".execute
  }

  def getUnVerifiedLinearAsset(assetTypeId: Int): List[(Long, Long)] = {
    val TwoYears: Int = 24
    sql"""
          Select a.id, pos.link_id
          from ASSET a
          join ASSET_LINK al on a.id = al.asset_id
          join LRM_POSITION pos on al.position_id = pos.id
          where a.asset_type_id = $assetTypeId
          and (valid_to is NULL OR valid_to >= current_timestamp)
          and (a.created_by in ('dr1_conversion', 'dr1conversion') OR (extract(month from age(current_timestamp, a.created_date)) > $TwoYears))
          and (a.modified_date is NULL OR (a.modified_date is NOT NULL and a.modified_by = 'vvh_generated'))
          and (a.verified_date is NULL OR (extract(month from age(current_timestamp, a.verified_date)) > $TwoYears))
          and a.floating = '0'
      """.as[(Long, Long)].list
  }

  def deleteByTrafficSign(queryFilter: String => String, username: Option[String]) : Unit = {
    val modifiedBy = username match { case Some(user) => s", modified_by = '$user' " case _ => ""}
    val query = s"""
          update asset aux
          set valid_to = current_timestamp, modified_date = current_timestamp  $modifiedBy
          Where (aux.valid_to IS NULL OR aux.valid_to > current_timestamp )
          and exists ( select 1
                       from connected_asset con
                       join asset a on a.id = con.connected_asset_id
                       where aux.id = con.asset_id
                       and a.asset_type_id = ${TrafficSigns.typeId}
          """
    Q.updateNA(queryFilter(query) + ")").execute
  }

  def getAutomaticGeneratedAssets(municipalities: Seq[Int], assetTypeId: Int, lastCreationDate: Option[DateTime]): List[(Long, Int)] = {
    val municipalityFilter = if(municipalities.isEmpty) "" else s" and a1.municipality_code in (${municipalities.mkString(",")}) "
    val sinceString = lastCreationDate.get.toString("yyyy-MM-dd")
    sql"""select a.id, a1.municipality_code
         from asset a
         join connected_asset ca on a.id = ca.linear_asset_id
         join asset a1 on ca.point_asset_id = a1.id and a1.asset_type_id = ${TrafficSigns.typeId}
         where  (a.valid_to is null or a.valid_to > current_timestamp)
         and a.created_by = 'automatic_trafficSign_created'
         and a.asset_type_id = $assetTypeId
         and ca.created_date > TO_DATE($sinceString, 'YYYY-MM-DD hh24:mi:ss')-INTERVAL'1 MONTH'
         #$municipalityFilter""".as[(Long, Int)].list
  }
  def getLinksWithExpiredAssets(linkIds: Seq[Long], assetType: Int): Seq[Long] = {
    MassQuery.withIds(linkIds.toSet) { idTableName =>
      sql"""
      select LINK_ID
      from asset a
      join asset_link al on a.id = al.asset_id
      join lrm_position pos on al.position_id = pos.id
      join #$idTableName i on i.id = pos.link_id
      where a.asset_type_id = $assetType
      and a.valid_to is not null
    """.as[Long].list
    }
  }
}