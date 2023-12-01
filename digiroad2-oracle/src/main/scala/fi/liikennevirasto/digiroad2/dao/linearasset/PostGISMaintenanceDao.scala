package fi.liikennevirasto.digiroad2.dao.linearasset

import fi.liikennevirasto.digiroad2.asset.{DynamicProperty, DynamicPropertyValue, InformationSource, LinkGeomSource, MaintenanceRoadAsset}
import fi.liikennevirasto.digiroad2.linearasset.{DynamicAssetValue, DynamicValue, PersistedLinearAsset}
import fi.liikennevirasto.digiroad2.postgis.MassQuery
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import com.github.tototoshi.slick.MySQLJodaSupport._
import fi.liikennevirasto.digiroad2.dao.Queries.DynamicPropertyRow
import fi.liikennevirasto.digiroad2.dao.{DynamicAssetRow, Queries, Sequences}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.Measures
import slick.jdbc.{GetResult, PositionedResult, StaticQuery => Q}
import slick.jdbc.StaticQuery.interpolation

class PostGISMaintenanceDao() {

  def fetchPotentialServiceRoads(includeFloating: Boolean = false, includeExpire: Boolean = false ): Seq[PersistedLinearAsset] = {
    val floatingFilter = if (includeFloating) "" else " and a.floating = '0'"
    val expiredFilter = if (includeExpire) "" else " and (a.valid_to > current_timestamp or a.valid_to is null)"
    var valueToBeFetch = "9"
    var propNameFi = "Potentiaalinen kayttooikeus"

    val filter = floatingFilter ++ expiredFilter

    val query =  s"""
                    select a.id, pos.link_id, pos.side_code, pos.start_measure, pos.end_measure, p.public_id, p.property_type, p.required,
                    case
                    when tp.value_fi is not null then tp.value_fi
                    when e.value is not null then cast(e.value as text)
                    else null
                    end as value,
                    a.created_by, a.created_date, a.modified_by, a.modified_date,
                    case when a.valid_to <= current_timestamp then 1 else 0 end as expired, a.asset_type_id,
                    pos.adjusted_timestamp, pos.modified_date, pos.link_source, a.verified_by, a.verified_date, a.information_source
                   from asset a
                     join asset_link al on a.id = al.asset_id
                     join lrm_position pos on al.position_id = pos.id
                     join property p on p.asset_type_id = a.asset_type_id
                     left join single_choice_value s on s.asset_id = a.id and s.property_id = p.id and p.property_type = 'single_choice'
                     left join enumerated_value e on e.id = s.enumerated_value_id
                     left join text_property_value tp on tp.asset_id = a.id and tp.property_id = p.id and (p.property_type = 'text' or p.property_type = 'long_text' or p.property_type = 'read_only_text')
                     left join multiple_choice_value mc on mc.asset_id = a.id and mc.property_id = p.id and (p.property_type = 'multiple_choice' or p.property_type = 'checkbox')
                     where a.asset_type_id = ${MaintenanceRoadAsset.typeId} $filter and exists (select * from single_choice_value s1
                         join enumerated_value en on s1.enumerated_value_id = en.id and en.VALUE =  $valueToBeFetch and en.name_fi = '$propNameFi'
                         where  s1.asset_id = a.id) """

    val assets = Q.queryNA[DynamicAssetRow](query)(getDynamicAssetRow).iterator.toSeq

    assets.groupBy(_.id).map { case (id, assetRows) =>
      val row = assetRows.head
      val value =  DynamicAssetValue(assetRowToProperty(assetRows))

      PersistedLinearAsset(id, row.linkId, row.sideCode, value = Some(DynamicValue(value)), row.startMeasure, row.endMeasure, row.createdBy,
        row.createdDate, row.modifiedBy, row.modifiedDate, row.expired, row.typeId, row.timeStamp,
        row.geomModifiedDate, LinkGeomSource.apply(row.linkSource), row.verifiedBy, row.verifiedDate, row.informationSource.map(info => InformationSource.apply(info)))

    }.toSeq
  }

  implicit val getDynamicAssetRow = new GetResult[DynamicAssetRow] {
    def apply(r: PositionedResult) : DynamicAssetRow = {
      val id = r.nextLong()
      val linkId = r.nextString()
      val sideCode = r.nextInt()
      val startMeasure = r.nextDouble()
      val endMeasure = r.nextDouble()

      val propertyPublicId = r.nextString
      val propertyType = r.nextString
      val propertyRequired = r.nextBoolean()
      val propertyValue = r.nextObjectOption()
      val value = DynamicPropertyRow(
        publicId = propertyPublicId,
        propertyType = propertyType,
        required = propertyRequired,
        propertyValue = propertyValue)
      val createdBy = r.nextStringOption()
      val createdDate = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val modifiedBy = r.nextStringOption()
      val modifiedDate = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val expired = r.nextBoolean
      val typeId = r.nextInt()
      val timeStamp = r.nextLong()
      val geomModifiedDate = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val linkSource = r.nextInt()
      val verifiedBy = r.nextStringOption()
      val verifiedDate = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val informationSource = r.nextIntOption()

      DynamicAssetRow(id, linkId, sideCode, value, startMeasure, endMeasure, createdBy, createdDate, modifiedBy, modifiedDate, expired, typeId, timeStamp, geomModifiedDate, linkSource, verifiedBy, verifiedDate, informationSource)
    }
  }

  def assetRowToProperty(assetRows: Iterable[DynamicAssetRow]): Seq[DynamicProperty] = {
    assetRows.groupBy(_.value.publicId).map { case (key, rows) =>
      val row = rows.head
      DynamicProperty(
        publicId = row.value.publicId,
        propertyType = row.value.propertyType,
        required = row.value.required,
        values = rows.flatMap(assetRow =>
          assetRow.value.propertyValue match {
            case Some(value) => Some(DynamicPropertyValue(value))
            case _ => None
          }
        ).toSeq
      )
    }.toSeq
  }

  def expireAllMaintenanceAssets(typeId: Int): Unit = {
    sqlu"update asset set valid_to = current_timestamp - INTERVAL'1 SECOND' where asset_type_id = $typeId".execute
  }

  def expireMaintenanceAssetsByLinkids(linkIds: Seq[String], typeId: Int): Unit = {
    linkIds.foreach { linkId =>
      sqlu"""
          update asset set valid_to = current_timestamp - INTERVAL'1 SECOND'
            where id in
            (SELECT a.id
               FROM asset a, asset_link al, lrm_position lp
              WHERE a.id = al.asset_id
                AND al.position_id = lp.id
                AND a.asset_type_id = $typeId
                AND a.valid_to IS NULL
                AND lp.link_id = $linkId)
        """.execute
    }
  }

  /**
    * Creates new Maintenance asset. Return id of new asset. Used by MaintenanceService.createWithoutTransaction
    */
  def createLinearAsset(typeId: Int, linkId: String, expired: Boolean, sideCode: Int, measures: Measures, username: String, timeStamp: Long = 0L, linkSource: Option[Int],
                        fromUpdate: Boolean = false,
                        createdByFromUpdate: Option[String] = Some(""),
                        createdDateTimeFromUpdate: Option[DateTime] = Some(DateTime.now()), modifiedByFromUpdate: Option[String] = None, modifiedDateTimeFromUpdate: Option[DateTime] = Some(DateTime.now()), area: Int): Long = {
    val id = Sequences.nextPrimaryKeySeqValue
    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
    val validTo = if (expired) "current_timestamp" else "null"
    val modifiedBy = modifiedByFromUpdate.getOrElse(username)
    if (fromUpdate) {
      sqlu"""
       insert into asset(id, asset_type_id, created_by, created_date, valid_to, modified_by, modified_date, area)
        values ($id, $typeId, $createdByFromUpdate, $createdDateTimeFromUpdate, #$validTo, $modifiedBy, $modifiedDateTimeFromUpdate, $area);

       insert into lrm_position(id, start_measure, end_measure, link_id, side_code, modified_date, adjusted_timestamp, link_source)
        values ($lrmPositionId, ${measures.startMeasure}, ${measures.endMeasure}, $linkId, $sideCode, current_timestamp, $timeStamp, $linkSource);

       insert into asset_link(asset_id, position_id)
        values ($id, $lrmPositionId)
    """.execute
    } else {
      sqlu"""
      insert into asset(id, asset_type_id, created_by, created_date, valid_to, area)
      values ($id, $typeId, $username, current_timestamp, #$validTo, $area);

      insert into lrm_position(id, start_measure, end_measure, link_id, side_code, modified_date, adjusted_timestamp, link_source)
      values ($lrmPositionId, ${measures.startMeasure}, ${measures.endMeasure}, $linkId, $sideCode, current_timestamp, $timeStamp, $linkSource);

      insert into asset_link(asset_id, position_id)
      values ($id, $lrmPositionId);
        """.execute
    }
    id
  }

  def getUncheckedMaintenanceRoad(areas: Option[Set[Int]]): List[(Long, String)] = {
    val optionalAreas = areas.map(_.mkString(","))
    val uncheckedQuery = """
          Select a.id, case when a.area is null then 'Unknown' else cast(a.area as text) end
          from asset a
          left join property p on a.asset_type_id = p.asset_type_id and public_id = 'huoltotie_tarkistettu'
          left join multiple_choice_value mc on mc.asset_id = a.id and mc.property_id = p.id
          join enumerated_value e on e.id = mc.enumerated_value_id and e.value = 0
          where a.asset_type_id = 290
          and(valid_to is NULL OR valid_to > current_timestamp)"""

    val sql = optionalAreas match {
      case Some(area) => uncheckedQuery + s" and a.area in ($area)"
      case _ => uncheckedQuery
    }
    Q.queryNA[(Long, String)](sql).list
  }

}
