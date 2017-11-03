package fi.liikennevirasto.digiroad2.linearasset.oracle

import fi.liikennevirasto.digiroad2.asset.{PropertyTypes, LinkGeomSource}
import fi.liikennevirasto.digiroad2.linearasset.{Properties, MaintenanceRoad, PersistedLinearAsset}
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.{Sequences, Queries}
import fi.liikennevirasto.digiroad2.oracle.MassQuery
import fi.liikennevirasto.digiroad2.{Measures, RoadLinkService, VVHClient}
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import slick.jdbc.{StaticQuery => Q}
import com.github.tototoshi.slick.MySQLJodaSupport._
import slick.jdbc.StaticQuery.interpolation

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
    * Updates MaintenanceRoad property. Used by MaintenanceService.updateProjected.
    */
  def updateMaintenanceRoadValue(assetId: Long, maintenanceRoad: MaintenanceRoad, username: String): Option[Long] = {
    maintenanceRoad.properties.foreach { prop =>
      val propertyId = Q.query[String, Long](Queries.propertyIdByPublicId).apply(prop.publicId).firstOption.getOrElse(throw new IllegalArgumentException("Property: " + prop.publicId + " not found"))
      prop.propertyType match {
        case PropertyTypes.Text => {
          if (textPropertyValueDoesNotExist(assetId, propertyId) && prop.value.nonEmpty) {
            Queries.insertTextProperty(assetId, propertyId, prop.value).first
          } else if (prop.value.isEmpty) {
            Queries.deleteTextProperty(assetId, propertyId).execute
          } else {
            Queries.updateTextProperty(assetId, propertyId, prop.value).firstOption.getOrElse(throw new IllegalArgumentException("Property Text Update: " + prop.publicId + " not found"))
          }
        }
        case PropertyTypes.SingleChoice | PropertyTypes.CheckBox => {
          if (singleChoiceValueDoesNotExist(assetId, propertyId)) {
            Queries.insertSingleChoiceProperty(assetId, propertyId, prop.value.toInt).first
          } else {
            Queries.updateSingleChoiceProperty(assetId, propertyId, prop.value.toInt).firstOption.getOrElse(throw new IllegalArgumentException("Property Single Choice Update: " + prop.publicId + " not found"))
          }
        }
      }
    }
    Some(assetId)
  }

  private def textPropertyValueDoesNotExist(assetId: Long, propertyId: Long) = {
    Q.query[(Long, Long), Long](Queries.existsTextProperty).apply((assetId, propertyId)).firstOption.isEmpty
  }

  private def singleChoiceValueDoesNotExist(assetId: Long, propertyId: Long) = {
    Q.query[(Long, Long), Long](Queries.existsSingleChoiceProperty).apply((assetId, propertyId)).firstOption.isEmpty
  }

  /**
    * Creates new Maintenance asset. Return id of new asset. Used by MaintenanceService.createWithoutTransaction
    */
  def createLinearAsset(typeId: Int, linkId: Long, expired: Boolean, sideCode: Int, measures: Measures, username: String, vvhTimeStamp: Long = 0L, linkSource: Option[Int],
                        fromUpdate: Boolean = false,
                        createdByFromUpdate: Option[String] = Some(""),
                        createdDateTimeFromUpdate: Option[DateTime] = Some(DateTime.now()), area: Int): Long = {
    val id = Sequences.nextPrimaryKeySeqValue
    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
    val validTo = if (expired) "sysdate" else "null"
    if (fromUpdate) {
      sqlu"""
      insert all
        into asset(id, asset_type_id, created_by, created_date, valid_to, modified_by, modified_date, area)
        values ($id, $typeId, $createdByFromUpdate, $createdDateTimeFromUpdate, #$validTo, $username, CURRENT_TIMESTAMP, $area)

        into lrm_position(id, start_measure, end_measure, link_id, side_code, modified_date, adjusted_timestamp, link_source)
        values ($lrmPositionId, ${measures.startMeasure}, ${measures.endMeasure}, $linkId, $sideCode, CURRENT_TIMESTAMP, $vvhTimeStamp, $linkSource)

        into asset_link(asset_id, position_id)
        values ($id, $lrmPositionId)
      select * from dual
    """.execute
    } else {
      sqlu"""
      insert all
        into asset(id, asset_type_id, created_by, created_date, valid_to, area)
      values ($id, $typeId, $username, sysdate, #$validTo, $area)

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

  def insertMaintenanceRoadValue(assetId: Long, maintenanceRoad: MaintenanceRoad): Unit = {
    maintenanceRoad.properties.filter(finalProps => finalProps.value != "").foreach(prop => {
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

  def getUncheckedMaintenanceRoad(areas: Option[Set[Int]]): List[(Long, String)] = {
    val optionalAreas = areas.map(_.mkString(","))
    val uncheckedQuery = """
          Select a.id, case when a.area is null then 'Unknown' else TO_CHAR(a.area) end
          from asset a
          left join property p on a.asset_type_id = p.asset_type_id and public_id = 'huoltotie_tarkistettu'
          left join single_choice_value s on s.asset_id = a.id and s.property_id = p.id
          join enumerated_value e on e.id = s.enumerated_value_id and e.value = 0
          where a.asset_type_id = 290
          and(valid_to is NULL OR valid_to >= CURRENT_TIMESTAMP)"""

    val sql = optionalAreas match {
      case Some(area) => uncheckedQuery + s" and a.area in ($area)"
      case _ => uncheckedQuery
    }
    Q.queryNA[(Long, String)](sql).list
  }

}
