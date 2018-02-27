package fi.liikennevirasto.digiroad2.dao

import fi.liikennevirasto.digiroad2.asset.{MultiTypePropertyValue, LinkGeomSource, MultiTypeProperty}
import fi.liikennevirasto.digiroad2.dao.Queries.MultiValuePropertyRow
import fi.liikennevirasto.digiroad2.linearasset.{MultiValue, PersistedLinearAsset}
import fi.liikennevirasto.digiroad2.oracle.MassQuery
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedParameters, PositionedResult, SetParameter, StaticQuery => Q}

case class MultiValueAssetRow(id: Long, linkId: Long, sideCode: Int, value: MultiValuePropertyRow,
                              startMeasure: Double, endMeasure: Double, createdBy: Option[String], createdDate: Option[DateTime],
                              modifiedBy: Option[String], modifiedDate: Option[DateTime], expired: Boolean, typeId: Int,
                              vvhTimeStamp: Long, geomModifiedDate: Option[DateTime], linkSource: Int, verifiedBy: Option[String], verifiedDate: Option[DateTime])

class MultiValueLinearAssetDao {


  def fetchMultiValueLinearAssetsByLinkIds(assetTypeId: Int, linkIds: Seq[Long], includeExpired: Boolean = false): Seq[PersistedLinearAsset] = {
    val filterExpired = if (includeExpired) "" else " and (a.valid_to > sysdate or a.valid_to is null)"
    val assets = MassQuery.withIds(linkIds.toSet) { idTableName =>
      sql"""
        select a.id, pos.link_id, pos.side_code, pos.start_measure, pos.end_measure, p.public_id, p.property_type,
         case
               when tp.value_fi is not null then tp.value_fi
               when np.value is not null then to_char(np.value)
               when e.name_fi is not null then e.name_fi
               else null
         end as value,
               a.created_by, a.created_date, a.modified_by, a.modified_date,
               case when a.valid_to <= sysdate then 1 else 0 end as expired, a.asset_type_id,
               pos.adjusted_timestamp, pos.modified_date, pos.link_source, a.verified_by, a.verified_date
          from asset a
          join asset_link al on a.id = al.asset_id
          join lrm_position pos on al.position_id = pos.id
          join property p on p.asset_type_id = a.asset_type_id
          join #$idTableName i on i.id = pos.link_id
                      left join single_choice_value s on s.asset_id = a.id and s.property_id = p.id and p.property_type = 'single_choice'
                      left join text_property_value tp on tp.asset_id = a.id and tp.property_id = p.id and (p.property_type = 'text' or p.property_type = 'long_text' or p.property_type = 'read_only_text')
                      left join multiple_choice_value mc on mc.asset_id = a.id and mc.property_id = p.id and p.property_type = 'multiple_choice'
                      left join number_property_value np on np.asset_id = a.id and np.property_id = p.id and (p.property_type = 'number' or p.property_type = 'read_only_number')
                      left join enumerated_value e on mc.enumerated_value_id = e.id or s.enumerated_value_id = e.id
          where a.asset_type_id = $assetTypeId
          and a.floating = 0
          #$filterExpired""".as[MultiValueAssetRow].list
    }
    assets.groupBy(_.id).map { case (id, assetRows) =>
      val row = assetRows.head
      val value: Seq[MultiTypeProperty] = assetRowToProperty(assetRows)

      id -> PersistedLinearAsset(id = row.id, linkId = row.linkId, sideCode = row.sideCode, value = Some(MultiValue(value)), startMeasure = row.startMeasure, endMeasure = row.endMeasure, createdBy = row.createdBy,
        createdDateTime = row.createdDate, modifiedBy = row.modifiedBy, modifiedDateTime = row.modifiedDate, expired = row.expired, typeId = row.typeId,  vvhTimeStamp = row.vvhTimeStamp,
        geomModifiedDate = row.geomModifiedDate, linkSource = LinkGeomSource.apply(row.linkSource), verifiedBy = row.verifiedBy, verifiedDate = row.verifiedDate)
    }.values.toSeq
  }


  def fetchMultiValueLinearAssetsByIds(assetTypeId: Int, ids: Set[Long], includeExpired: Boolean = false): Seq[PersistedLinearAsset] = {
    val filterExpired = if (includeExpired) "" else " and (a.valid_to > sysdate or a.valid_to is null)"
    val assets = MassQuery.withIds(ids) { idTableName =>
      sql"""
        select a.id, pos.link_id, pos.side_code, pos.start_measure, pos.end_measure, p.public_id, p.property_type,
         case
               when tp.value_fi is not null then tp.value_fi
               when np.value is not null then to_char(np.value)
               when e.name_fi is not null then e.name_fi
               else null
         end as value,
               a.created_by, a.created_date, a.modified_by, a.modified_date,
               case when a.valid_to <= sysdate then 1 else 0 end as expired, a.asset_type_id,
               pos.adjusted_timestamp, pos.modified_date, pos.link_source, a.verified_by, a.verified_date
          from asset a
          join asset_link al on a.id = al.asset_id
          join lrm_position pos on al.position_id = pos.id
          join property p on p.asset_type_id = a.asset_type_id
          join #$idTableName i on i.id = a.id
                      left join single_choice_value s on s.asset_id = a.id and s.property_id = p.id and p.property_type = 'single_choice'
                      left join text_property_value tp on tp.asset_id = a.id and tp.property_id = p.id and (p.property_type = 'text' or p.property_type = 'long_text' or p.property_type = 'read_only_text')
                      left join multiple_choice_value mc on mc.asset_id = a.id and mc.property_id = p.id and p.property_type = 'multiple_choice'
                      left join number_property_value np on np.asset_id = a.id and np.property_id = p.id and (p.property_type = 'number' or p.property_type = 'read_only_number')
                      left join enumerated_value e on mc.enumerated_value_id = e.id or s.enumerated_value_id = e.id
          where a.asset_type_id = $assetTypeId
          and a.floating = 0
          #$filterExpired""".as[MultiValueAssetRow].list
    }
    assets.groupBy(_.id).map { case (id, assetRows) =>
      val row = assetRows.head
      val value: Seq[MultiTypeProperty] = assetRowToProperty(assetRows)

      id -> PersistedLinearAsset(id = row.id, linkId = row.linkId, sideCode = row.sideCode, value = Some(MultiValue(value)), startMeasure = row.startMeasure, endMeasure = row.endMeasure, createdBy = row.createdBy,
        createdDateTime = row.createdDate, modifiedBy = row.modifiedBy, modifiedDateTime = row.modifiedDate, expired = row.expired, typeId = row.typeId, vvhTimeStamp = row.vvhTimeStamp,
        geomModifiedDate = row.geomModifiedDate, linkSource = LinkGeomSource.apply(row.linkSource), verifiedBy = row.verifiedBy, verifiedDate = row.verifiedDate)
    }.values.toSeq
  }


  private def queryToPersistedLinearAssets(query: String): Seq[PersistedLinearAsset] = {
    val rows = Q.queryNA[MultiValueAssetRow](query).iterator.toSeq

    rows.groupBy(_.id).map { case (id, assetRows) =>
      val row = assetRows.head
      val value: Seq[MultiTypeProperty] = assetRowToProperty(assetRows)

      id -> PersistedLinearAsset(id = row.id, linkId = row.linkId, sideCode = row.sideCode, value = Some(MultiValue(value)), startMeasure = row.startMeasure, endMeasure = row.endMeasure, createdBy = row.createdBy,
        createdDateTime = row.createdDate, modifiedBy = row.modifiedBy, modifiedDateTime = row.modifiedDate, expired = row.expired, typeId = row.typeId,  vvhTimeStamp = row.vvhTimeStamp,
        geomModifiedDate = row.geomModifiedDate, linkSource = LinkGeomSource.apply(row.linkSource), verifiedBy = row.verifiedBy, verifiedDate = row.verifiedDate)
    }.values.toSeq
  }


  def assetRowToProperty(assetRows: Iterable[MultiValueAssetRow]): Seq[MultiTypeProperty] = {
    assetRows.groupBy(_.value.publicId).map { case (key, rows) =>
      val row = rows.head
      MultiTypeProperty(
        publicId = row.value.publicId,
        propertyType = row.value.propertyType,
        values = rows.map(assetRow =>
          MultiTypePropertyValue(
            assetRow.value.propertyValue
          )
        ).toSeq
      )
    }.toSeq
  }

  implicit val getMultiValueAssetRow = new GetResult[MultiValueAssetRow] {
    def apply(r: PositionedResult) = {

      val id = r.nextLong()
      val linkId = r.nextLong()
      val sideCode = r.nextInt()
      val startMeasure = r.nextDouble()
      val endMeasure = r.nextDouble()

      val propertyPublicId = r.nextString
      val propertyType = r.nextString
      val propertyValue = r.nextObjectOption()
      val value = new MultiValuePropertyRow(
        publicId = propertyPublicId,
        propertyType = propertyType,
        propertyValue = propertyValue.getOrElse(""))
      val createdBy = r.nextStringOption()
      val createdDate = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val modifiedBy = r.nextStringOption()
      val modifiedDate = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val expired = r.nextBoolean
      val typeId = r.nextInt()


      val vvhTimeStamp = r.nextLong()
      val geomModifiedDate = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val linkSource = r.nextInt()
      val verifiedBy = r.nextStringOption()
      val verifiedDate = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))

      MultiValueAssetRow(id, linkId, sideCode, value, startMeasure, endMeasure, createdBy, createdDate, modifiedBy, modifiedDate, expired, typeId, vvhTimeStamp, geomModifiedDate, linkSource, verifiedBy, verifiedDate)
    }
  }

}

