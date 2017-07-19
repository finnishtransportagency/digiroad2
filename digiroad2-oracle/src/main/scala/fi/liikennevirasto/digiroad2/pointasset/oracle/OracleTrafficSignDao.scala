package fi.liikennevirasto.digiroad2.pointasset.oracle

import fi.liikennevirasto.digiroad2.masstransitstop.oracle.Queries._
import fi.liikennevirasto.digiroad2.{IncomingTrafficSign, Point, PersistedPointAsset}
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.{Queries, Sequences}
import org.joda.time.DateTime
import slick.jdbc.{PositionedResult, GetResult, StaticQuery}


//TODO verify if this imports are really necessary
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

case class TrafficSign(id: Long, linkId: Long,
                       lon: Double, lat: Double,
                       mValue: Double, floating: Boolean,
                       vvhTimeStamp: Long,
                       municipalityCode: Int,
                       signType: Int,
                       value: Option[String],
                       additionalInfo: Option[String],
                       createdBy: Option[String] = None,
                       createdAt: Option[DateTime] = None,
                       modifiedBy: Option[String] = None,
                       modifiedAt: Option[DateTime] = None,
                       linkSource: LinkGeomSource) extends PersistedPointAsset

object OracleTrafficSignDao {

  def fetchByFilter(queryFilter: String => String): Seq[TrafficSign] = {
    val query =
      """
        select a.id, lp.link_id, a.geometry, lp.start_measure, a.floating, lp.adjusted_timestamp,a.municipality_code,
               case
                when ev.name_fi is not null then ev.name_fi
                when tpv.value_fi is not null then tpv.value_fi
                else null
               end as value, a.created_by, a.created_date, a.modified_by, a.modified_date, lp.link_source
        from asset a
        join asset_link al on a.id = al.asset_id
        join lrm_position lp on al.position_id = lp.id
        join property p on a.asset_type_id = p.asset_type_id
        left join single_choice_value scv on scv.asset_id = a.id and scv.property_id = p.id and p.property_type = 'single_choice'
        left join text_property_value tpv on tpv.asset_id = a.id and tpv.property_id = p.id and p.property_type = 'text'
        left join enumerated_value ev on scv.enumerated_value_id = ev.id
      """
    val queryWithFilter = queryFilter(query) + " and (a.valid_to > sysdate or a.valid_to is null)"
    StaticQuery.queryNA[TrafficSign](queryWithFilter).iterator.toSeq
  }

  implicit val getPointAsset = new GetResult[TrafficSign] {
    def apply(r: PositionedResult) = {
      val id = r.nextLong()
      val linkId = r.nextLong()
      val point = r.nextBytesOption().map(bytesToPoint).get
      val mValue = r.nextDouble()
      val floating = r.nextBoolean()
      val vvhTimeStamp = r.nextLong()
      val municipalityCode = r.nextInt()
      val signType = r.nextInt()
      val value = r.nextStringOption()
      val additionalInfo = r.nextStringOption()
      val createdBy = r.nextStringOption()
      val createdAt = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val modifiedBy = r.nextStringOption()
      val modifiedAt = r.nextTimestampOption().map(timestamp => new DateTime(timestamp))
      val linkSource = r.nextInt()

      TrafficSign(id, linkId, point.x, point.y, mValue, floating, vvhTimeStamp, municipalityCode, signType, value, additionalInfo, createdBy, createdAt, modifiedBy, modifiedAt, LinkGeomSource(linkSource))
    }
  }

  def create(trafficSign: IncomingTrafficSign, mValue: Double, username: String, municipality: Int, adjustmentTimestamp: Long, linkSource: LinkGeomSource): Long = {
    val id = Sequences.nextPrimaryKeySeqValue
    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
    sqlu"""
      insert all
        into asset(id, asset_type_id, created_by, created_date, municipality_code)
        values ($id, 300, $username, sysdate, $municipality)

        into lrm_position(id, start_measure, link_id, adjusted_timestamp, link_source)
        values ($lrmPositionId, $mValue, ${trafficSign.linkId}, $adjustmentTimestamp, ${linkSource.value})

        into asset_link(asset_id, position_id)
        values ($id, $lrmPositionId)

      select * from dual
    """.execute
    updateAssetGeometry(id, Point(trafficSign.lon, trafficSign.lat))
    insertSingleChoiceProperty(id, getSignTypePropertyId, trafficSign.signType).execute
    val mapOfTextPropertiesToInsert =
      Map(getValuePropertyId -> trafficSign.value,
        getAdditionalInfoPropertyId -> trafficSign.additionalInfo)
    insertMultipleTextProperty(id, mapOfTextPropertiesToInsert)
    id
  }

  def update(id: Long, trafficSign: IncomingTrafficSign, mValue: Double, municipality: Int, username: String, adjustedTimeStampOption: Option[Long] = None, linkSource: LinkGeomSource) = {
    sqlu""" update asset set municipality_code = $municipality where id = $id """.execute
    updateAssetModified(id, username).execute
    updateAssetGeometry(id, Point(trafficSign.lon, trafficSign.lat))
    updateSingleChoiceProperty(id, getSignTypePropertyId, trafficSign.signType).execute

    val textPropertiesToDelete = Seq(getValuePropertyId, getAdditionalInfoPropertyId)
    val textPropertiesToInsert =
      Map(getValuePropertyId -> trafficSign.value,
        getAdditionalInfoPropertyId -> trafficSign.additionalInfo)

    deleteMultipleTextProperty(id, textPropertiesToDelete)
    insertMultipleTextProperty(id, textPropertiesToInsert)

    adjustedTimeStampOption match {
      case Some(adjustedTimeStamp) =>
        sqlu"""
          update lrm_position
           set
           start_measure = $mValue,
           link_id = ${trafficSign.linkId},
           adjusted_timestamp = ${adjustedTimeStamp},
           link_source = ${linkSource.value}
           where id = (select position_id from asset_link where asset_id = $id)
        """.execute
      case _ =>
        sqlu"""
          update lrm_position
           set
           start_measure = $mValue,
           link_id = ${trafficSign.linkId},
           link_source = ${linkSource.value}
           where id = (select position_id from asset_link where asset_id = $id)
        """.execute
    }
    id
  }

  def updateAssetGeometry(id: Long, point: Point): Unit = {
    val x = point.x
    val y = point.y
    sqlu"""
      UPDATE asset
        SET geometry = MDSYS.SDO_GEOMETRY(4401,
                                          3067,
                                          NULL,
                                          MDSYS.SDO_ELEM_INFO_ARRAY(1,1,1),
                                          MDSYS.SDO_ORDINATE_ARRAY($x, $y, 0, 0)
                                         )
        WHERE id = $id
    """.execute
  }

  def insertSingleChoiceProperty(assetId: Long, propertyId: Long, value: Long) = {
    sqlu"""
      insert into single_choice_value(asset_id, enumerated_value_id, property_id, modified_date)
      values ($assetId, (select id from enumerated_value where property_id = $propertyId and value = $value), $propertyId, current_timestamp)
    """
  }

  def insertMultipleTextProperty(assetId: Long, valuesToInsert: Map[Long, Option[String]]) {
    valuesToInsert.map { values =>
      values match {
        case (propertyId, Some(valueFi)) =>
          sqlu"""
          insert into text_property_value(id, property_id, asset_id, value_fi, created_date)
          values (primary_key_seq.nextval, $propertyId, $assetId, $valueFi, CURRENT_TIMESTAMP)
        """.execute
      }
    }
  }

  def deleteMultipleTextProperty(assetId: Long, properties: Seq[Long]) = {
    properties.map(property =>
      sqlu""""delete from text_property_value where asset_id = $assetId and property_id = $property""".execute
    )
  }

  private def getSignTypePropertyId: Long = {
    StaticQuery.query[String, Long](Queries.propertyIdByPublicId).apply("liikennemerkki_tyyppi").first
  }

  private def getValuePropertyId: Long = {
    StaticQuery.query[String, Long](Queries.propertyIdByPublicId).apply("liikennemerkki_arvo").first
  }

  private def getAdditionalInfoPropertyId: Long = {
    StaticQuery.query[String, Long](Queries.propertyIdByPublicId).apply("liikennemerkki_lisatieto").first
  }
}