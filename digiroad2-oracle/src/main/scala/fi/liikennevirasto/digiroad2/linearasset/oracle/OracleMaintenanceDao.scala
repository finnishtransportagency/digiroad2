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

class OracleMaintenanceDao(val vvhClient: VVHClient, val roadLinkService: RoadLinkService) {

  /**
    * Iterates a set of link ids with MaintenanceRoad asset type id and floating flag and returns linear assets. Used by MaintenanceService.getByRoadLinks
    */
  def fetchMaintenancesByLinkIds(maintenanceRoadAssetTypeId: Int, linkIds: Seq[Long], includeFloating: Boolean = false, includeExpire: Boolean = true ): Seq[PersistedLinearAsset] = {
    val floatingFilter = if (includeFloating) "" else "and a.floating = 0"
    val expiredFilter = if (includeExpire) "" else "and (a.valid_to > sysdate or a.valid_to is null)"
    val filter = floatingFilter ++ expiredFilter

    val assets = MassQuery.withIds(linkIds.toSet) { idTableName =>
      sql"""
         select a.id, t.id, t.property_id, t.value_fi, p.property_type, p.public_id, p.required, pos.link_id,
                pos.side_code, pos.start_measure,
                pos.end_measure, pos.adjusted_timestamp, pos.modified_date, a.created_by, a.created_date,
                a.modified_by, a.modified_date,
                case when a.valid_to <= sysdate then 1 else 0 end as expired, pos.link_source
           from asset a
                join asset_link al on a.id = al.asset_id
                join lrm_position pos on al.position_id = pos.id
                join text_property_value t on t.asset_id = a.id
                join #$idTableName i on i.id = pos.link_id
                join property p on p.id = t.property_id
          where a.asset_type_id = #$maintenanceRoadAssetTypeId
          #$filter
         union
         select a.id, e.id, e.property_id, cast (e.value as varchar2 (30)), p.property_type, p.public_id, p.required,
                pos.link_id, pos.side_code,
                pos.start_measure, pos.end_measure, pos.adjusted_timestamp, pos.modified_date, a.created_by,
                a.created_date, a.modified_by, a.modified_date,
                case when a.valid_to <= sysdate then 1 else 0 end as expired, pos.link_source
           from asset a
               join asset_link al on a.id = al.asset_id
                join lrm_position pos on al.position_id = pos.id
                join single_choice_value s on s.asset_id = a.id
                join enumerated_value e on e.id = s.enumerated_value_id
                join #$idTableName i on i.id = pos.link_id
                join property p on p.id = e.property_id
          where a.asset_type_id = #$maintenanceRoadAssetTypeId
          #$filter"""
        .as[(Long, Long, Long, String, String, String, Boolean, Long, Int, Double, Double, Long, Option[DateTime], Option[String], Option[DateTime], Option[String], Option[DateTime], Boolean, Int)].list
    }

    val groupedByAssetId = assets.groupBy(_._1)
    val groupedByMaintenanceRoadId = groupedByAssetId.mapValues(_.groupBy(_._2))

    groupedByMaintenanceRoadId.map { case (assetId, rowsByMaintenanceRoadId) =>
      val (_, _, _, _, _, _, _, linkId, sideCode, startMeasure, endMeasure, vvhTimeStamp, geomModifiedDate, createdBy, createdDate, modifiedBy, modifiedDate, expired, linkSource) = groupedByAssetId(assetId).head
      val maintenanceRoadValues = rowsByMaintenanceRoadId.keys.toSeq.sorted.map { maintenanceRoadId =>
        val rows = rowsByMaintenanceRoadId(maintenanceRoadId)
        val propertyValue = rows.head._4
        val propertyType = rows.head._5
        val propertyPublicId = rows.head._6
        Properties(propertyPublicId, propertyType, propertyValue)
      }
      PersistedLinearAsset(assetId, linkId, sideCode, Some(MaintenanceRoad(maintenanceRoadValues)), startMeasure, endMeasure, createdBy, createdDate, modifiedBy, modifiedDate, expired, maintenanceRoadAssetTypeId, vvhTimeStamp, geomModifiedDate, LinkGeomSource.apply(linkSource))
    }.toSeq
  }

  /**
    * Iterates a set of asset ids with MaintenanceRoad asset type id and floating flag and returns linear assets. User by LinearAssetService.getPersistedAssetsByIds
    */
  def fetchMaintenancesByIds(maintenanceRoadAssetTypeId: Int, ids: Set[Long], includeFloating: Boolean = false): Seq[PersistedLinearAsset] = {
    val floatingFilter = if (includeFloating) "" else "and a.floating = 0"

    val assets = MassQuery.withIds(ids.toSet) { idTableName =>
      sql"""
         select a.id, t.id, t.property_id, t.value_fi, p.property_type, p.public_id, p.required, pos.link_id,
                pos.side_code, pos.start_measure,
                pos.end_measure, pos.adjusted_timestamp, pos.modified_date, a.created_by, a.created_date,
                a.modified_by, a.modified_date,
                case when a.valid_to <= sysdate then 1 else 0 end as expired, pos.link_source
           from asset a
                join asset_link al on a.id = al.asset_id
                join lrm_position pos on al.position_id = pos.id
                join text_property_value t on t.asset_id = a.id
                join #$idTableName i on i.id = a.id
                join property p on p.id = t.property_id
          where a.asset_type_id = #$maintenanceRoadAssetTypeId
            #$floatingFilter
         union
         select a.id, e.id, e.property_id, cast (e.value as varchar2 (30)), p.property_type, p.public_id, p.required,
                pos.link_id, pos.side_code,
                pos.start_measure, pos.end_measure, pos.adjusted_timestamp, pos.modified_date, a.created_by,
                a.created_date, a.modified_by, a.modified_date,
                case when a.valid_to <= sysdate then 1 else 0 end as expired, pos.link_source
           from asset a
               join asset_link al on a.id = al.asset_id
                join lrm_position pos on al.position_id = pos.id
                join single_choice_value s on s.asset_id = a.id
                join enumerated_value e on e.id = s.enumerated_value_id
                join #$idTableName i on i.id = a.id
                join property p on p.id = e.property_id
          where a.asset_type_id = #$maintenanceRoadAssetTypeId
            #$floatingFilter"""
        .as[(Long, Long, Long, String, String, String, Boolean, Long, Int, Double, Double, Long, Option[DateTime], Option[String], Option[DateTime], Option[String], Option[DateTime], Boolean, Int)].list
    }

    val groupedByAssetId = assets.groupBy(_._1)
    val groupedByMaintenanceRoadId = groupedByAssetId.mapValues(_.groupBy(_._2))

    groupedByMaintenanceRoadId.map { case (assetId, rowsByMaintenanceRoadId) =>
      val (_, _, _, _, _, _, _, linkId, sideCode, startMeasure, endMeasure, vvhTimeStamp, geomModifiedDate, createdBy, createdDate, modifiedBy, modifiedDate, expired, linkSource) = groupedByAssetId(assetId).head
      val maintenanceRoadValues = rowsByMaintenanceRoadId.keys.toSeq.sorted.map { maintenanceRoadId =>
        val rows = rowsByMaintenanceRoadId(maintenanceRoadId)
        val propertyValue = rows.head._4
        val propertyType = rows.head._5
        val propertyPublicId = rows.head._6
        Properties(propertyPublicId, propertyType, propertyValue)
      }
      PersistedLinearAsset(assetId, linkId, sideCode, Some(MaintenanceRoad(maintenanceRoadValues)), startMeasure, endMeasure, createdBy, createdDate, modifiedBy, modifiedDate, expired, maintenanceRoadAssetTypeId, vvhTimeStamp, geomModifiedDate, LinkGeomSource.apply(linkSource))
    }.toSeq
  }

  def expireAllMaintenanceAssets(typeId: Int): Unit = {
    sqlu"update asset set valid_to = sysdate - 1/86400 where asset_type_id = $typeId".execute
  }

  /**
    * Creates new Maintenance asset. Return id of new asset. Used by MaintenanceService.createWithoutTransaction
    */
  def createLinearAsset(typeId: Int, linkId: Long, expired: Boolean, sideCode: Int, measures: Measures, username: String, vvhTimeStamp: Long = 0L, linkSource: Option[Int],
                        fromUpdate: Boolean = false,
                        createdByFromUpdate: Option[String] = Some(""),
                        createdDateTimeFromUpdate: Option[DateTime] = Some(DateTime.now())): Long = {
    val id = Sequences.nextPrimaryKeySeqValue
    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
    val validTo = if (expired) "sysdate" else "null"
    if (fromUpdate) {
      sqlu"""
      insert all
        into asset(id, asset_type_id, created_by, created_date, valid_to, modified_by, modified_date)
        values ($id, $typeId, $createdByFromUpdate, $createdDateTimeFromUpdate, #$validTo, $username, CURRENT_TIMESTAMP)

        into lrm_position(id, start_measure, end_measure, link_id, side_code, modified_date, adjusted_timestamp, link_source)
        values ($lrmPositionId, ${measures.startMeasure}, ${measures.endMeasure}, $linkId, $sideCode, CURRENT_TIMESTAMP, $vvhTimeStamp, $linkSource)

        into asset_link(asset_id, position_id)
        values ($id, $lrmPositionId)
      select * from dual
    """.execute
    } else {
      sqlu"""
      insert all
        into asset(id, asset_type_id, created_by, created_date, valid_to)
      values ($id, $typeId, $username, sysdate, #$validTo)

      into lrm_position(id, start_measure, end_measure, link_id, side_code, modified_date, adjusted_timestamp, link_source)
      values ($lrmPositionId, ${measures.startMeasure}, ${measures.endMeasure}, $linkId, $sideCode, CURRENT_TIMESTAMP, $vvhTimeStamp, $linkSource)

      into asset_link(asset_id, position_id)
      values ($id, $lrmPositionId)
      select * from dual
        """.execute
    }
    id
  }

  def getMaintenanceRequiredProperties(typeId: Int): Map[String, String] ={
    val requiredProperties =
      sql"""select public_id, property_type from property where asset_type_id = $typeId and required = 1""".as[(String, String)].iterator.toMap

    requiredProperties
  }

  def insertMaintenanceRoadValue(assetId: Long, value: MaintenanceRoad): Unit = {
    value.maintenanceRoad.filter(finalProps => finalProps.value != "").foreach(prop => {
      prop.propertyType match {
        case PropertyTypes.Text =>
          insertValue(assetId, prop.publicId, prop.value)
        case PropertyTypes.SingleChoice | PropertyTypes.CheckBox =>
          insertEnumeratedValue(assetId, prop.publicId, prop.value.toInt)
      }
    })
  }

  /**
    * Saves textual property value to db. Used by OracleMaintenanceDao.createWithoutTransaction.
    */
  def insertValue(assetId: Long, valuePropertyId: String, value: String) = {
    val propertyId = Q.query[String, Long](Queries.propertyIdByPublicId).apply(valuePropertyId).first
    Queries.insertTextProperty(assetId, propertyId, value).execute
  }

  /**
    * Saves enumerated value to db. Used by OracleMaintenanceDao.createSpeedLimitWithoutDuplicates
    */
  def insertEnumeratedValue(assetId: Long, valuePropertyId: String, value: Int) = {
    val propertyId = Q.query[String, Long](Queries.propertyIdByPublicId).apply(valuePropertyId).first
    sqlu"""
       insert into single_choice_value(asset_id, enumerated_value_id, property_id, modified_date)
       values ($assetId, (select id from enumerated_value where property_id = $propertyId and value = $value), $propertyId, current_timestamp)
     """.execute
  }

}
