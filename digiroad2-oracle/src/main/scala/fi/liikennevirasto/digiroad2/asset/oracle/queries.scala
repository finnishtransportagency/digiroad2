package fi.liikennevirasto.digiroad2.asset.oracle

import scala.slick.driver.JdbcDriver.backend.Database
import scala.slick.jdbc.{StaticQuery => Q, PositionedResult, GetResult, SetParameter}
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.asset._
import _root_.oracle.spatial.geometry.JGeometry
import java.sql.{Timestamp, Connection}
import _root_.oracle.sql.STRUCT
import com.jolbox.bonecp.ConnectionHandle
import scala.math.BigDecimal.RoundingMode
import java.text.{NumberFormat, DecimalFormat}
import Q._
import org.joda.time.{LocalDate, DateTime}
import com.github.tototoshi.slick.MySQLJodaSupport._
import java.util.Locale

object Queries {
  def bonecpToInternalConnection(cpConn: Connection) = cpConn.asInstanceOf[ConnectionHandle].getInternalConnection

  case class QueryCollector(sql: String, params: IndexedSeq[Any] = IndexedSeq()) {
    def add(element: Option[(String, List[Any])]): QueryCollector = element match {
      case Some((s, ps))  => this.copy(sql = this.sql + " " + s, this.params ++ ps)
      case _ => this
    }
  }

  case class Modification(modificationTime: Option[DateTime], modifier: Option[String])
  case class Image(imageId: Option[Long], lastModified: Option[DateTime])

  case class AssetRow(id: Long, externalId: Option[Long], assetTypeId: Long, lon: Double, lat: Double, roadLinkId: Long, bearing: Option[Int],
                      validityDirection: Int, validFrom: Option[Timestamp], validTo: Option[Timestamp], propertyId: Long,
                      propertyName: String, propertyType: String, propertyRequired: Boolean, propertyValue: Long, propertyDisplayValue: String,
                      image: Image, roadLinkEndDate: Option[LocalDate],
                      municipalityNumber: Int, created: Modification, modified: Modification)

  case class ListedAssetRow(id: Long, assetTypeId: Long, lon: Double, lat: Double, roadLinkId: Long, bearing: Option[Int],
                      validityDirection: Int, validFrom: Option[Timestamp], validTo: Option[Timestamp],
                      image: Image, roadLinkEndDate: Option[LocalDate],
                      municipalityNumber: Int)

  implicit val getAssetWithPosition = new GetResult[(AssetRow, LRMPosition)] {
    def apply(r: PositionedResult) = {
      val id = r.nextLong()
      val externalId = r.nextLongOption()
      val assetTypeId = r.nextLong()
      val bearing = r.nextIntOption()
      val validityDirection = r.nextInt()
      val validFrom = r.nextTimestampOption()
      val validTo = r.nextTimestampOption()
      val pos = r.nextBytes()
      val propertyId = r.nextLong()
      val propertyName = r.nextString()
      val propertyType = r.nextString()
      val propertyRequired = r.nextBoolean()
      val propertyValue = r.nextLong()
      val propertyDisplayValue = r.nextString()
      val lrmId = r.nextLong()
      val startMeasure = r.nextInt()
      val endMeasure = r.nextInt()
      val roadLinkId = r.nextLong()
      val image = new Image(r.nextLongOption(), r.nextTimestampOption().map(new DateTime(_)))
      val roadLinkEndDate = r.nextDateOption().map(new LocalDate(_))
      val municipalityNumber = r.nextInt()
      val created = new Modification(r.nextTimestampOption().map(new DateTime(_)), r.nextStringOption())
      val modified = new Modification(r.nextTimestampOption().map(new DateTime(_)), r.nextStringOption())
      val posGeom = JGeometry.load(pos)
      (AssetRow(id, externalId, assetTypeId, posGeom.getJavaPoint.getX, posGeom.getJavaPoint.getY, roadLinkId, bearing, validityDirection,
        validFrom, validTo, propertyId, propertyName, propertyType, propertyRequired, propertyValue, propertyDisplayValue, image,
        roadLinkEndDate, municipalityNumber, created, modified),
        LRMPosition(lrmId, startMeasure, endMeasure, posGeom))
    }
  }

  implicit val getListedAssetWithPosition = new GetResult[(ListedAssetRow, LRMPosition)] {
    def apply(r: PositionedResult) = {
      val (id, assetTypeId, bearing, validityDirection, validFrom, validTo, pos, lrmId, startMeasure, endMeasure,
      roadLinkId, image, roadLinkEndDate, municipalityNumber) =
        (r.nextLong, r.nextLong, r.nextIntOption, r.nextInt, r.nextTimestampOption, r.nextTimestampOption, r.nextBytes, r.nextLong, r.nextInt, r.nextInt,
          r.nextLong, new Image(r.nextLongOption, r.nextTimestampOption.map(new DateTime(_))), r.nextDateOption().map(new LocalDate(_)), r.nextInt)
      val posGeom = JGeometry.load(pos)
      (ListedAssetRow(id, assetTypeId, posGeom.getJavaPoint.getX, posGeom.getJavaPoint.getY, roadLinkId, bearing, validityDirection,
        validFrom, validTo, image, roadLinkEndDate, municipalityNumber),
        LRMPosition(lrmId, startMeasure, endMeasure, posGeom))
    }
  }

  implicit val getRoadLink = new GetResult[RoadLink] {
    def apply(r: PositionedResult) = {
      val (id, geomBytes, endDate, municipalityNumber) = (r.nextLong, r.nextBytes, r.nextDateOption, r.nextInt)
      val geom = JGeometry.load(geomBytes)
      val decimalPattern = "#.###"
      val newFormat = NumberFormat.getNumberInstance(Locale.US).asInstanceOf[DecimalFormat]
      newFormat.applyPattern(decimalPattern)
      val points: Array[Double] = geom.getOrdinatesArray
      val coords = for (i <- 0 to points.size / geom.getDimensions - 1) yield {
        (newFormat.format(points(geom.getDimensions * i)).toDouble,
         newFormat.format(points(geom.getDimensions * i + 1)).toDouble)
      }
      RoadLink(id = id,
               lonLat = coords,
               endDate = endDate.map(new LocalDate(_)),
               municipalityNumber = municipalityNumber)
    }
  }

  implicit val getAssetType = new GetResult[AssetType] {
    def apply(r: PositionedResult) = {
      AssetType(r.nextLong, r.nextString, r.nextString)
    }
  }

  case class EnumeratedPropertyValueRow(propertyId: Long, propertyType: String, propertyName: String, required: Boolean, value: Long, displayValue: String)

  implicit val getEnumeratedValue = new GetResult[EnumeratedPropertyValueRow] {
    def apply(r: PositionedResult) = {
      EnumeratedPropertyValueRow(r.nextLong, r.nextString, r.nextString, r.nextBoolean, r.nextLong, r.nextString)
    }
  }

  def nextPrimaryKeyId = sql"select primary_key_seq.nextval from dual"

  def allAssets =
    """
    select a.id as asset_id, a.external_id as asset_external_id, t.id as asset_type_id, a.bearing as bearing, a.validity_direction as validity_direction,
    a.valid_from as valid_from, a.valid_to as valid_to,
    SDO_LRS.LOCATE_PT(rl.geom, LEAST(lrm.start_measure, SDO_LRS.GEOM_SEGMENT_END_MEASURE(rl.geom))) AS position,
    p.id as property_id, p.name_fi as property_name, p.property_type, p.required,
    case
      when e.value is not null then e.value
      else null
    end as value,
    case
      when e.name_fi is not null then e.name_fi
      when tp.value_fi is not null then tp.value_fi
      else null
    end as display_value,
    lrm.id, lrm.start_measure, lrm.end_measure, lrm.road_link_id, i.id as image_id, i.modified_date as image_modified_date,
    rl.end_date, rl.municipality_number, a.created_date, a.created_by, a.modified_date, a.modified_by
    from asset_type t
      join asset a on a.asset_type_id = t.id
        join lrm_position lrm on a.lrm_position_id = lrm.id
          join road_link rl on lrm.road_link_id = rl.id
        join property p on t.id = p.asset_type_id
          left join single_choice_value s on s.asset_id = a.id and s.property_id = p.id and p.property_type = 'single_choice'
          left join text_property_value tp on tp.asset_id = a.id and tp.property_id = p.id and (p.property_type = 'text' or p.property_type = 'long_text')
          left join multiple_choice_value mc on mc.asset_id = a.id and mc.property_id = p.id and p.property_type = 'multiple_choice'
          left join enumerated_value e on mc.enumerated_value_id = e.id or s.enumerated_value_id = e.id
          left join image i on e.image_id = i.id"""

  def allAssetsWithoutProperties =
    """
    select a.id as asset_id, t.id as asset_type_id, a.bearing as bearing, a.validity_direction as validity_direction,
    a.valid_from as valid_from, a.valid_to as valid_to,
    SDO_LRS.LOCATE_PT(rl.geom, LEAST(lrm.start_measure, SDO_LRS.GEOM_SEGMENT_END_MEASURE(rl.geom))) AS position,
    lrm.id, lrm.start_measure, lrm.end_measure, lrm.road_link_id, i.id as image_id, i.modified_date as image_modified_date,
    rl.end_date, rl.municipality_number
    from asset_type t
      join asset a on a.asset_type_id = t.id
        join lrm_position lrm on a.lrm_position_id = lrm.id
          join road_link rl on lrm.road_link_id = rl.id
        join property p on t.id = p.asset_type_id
          left join single_choice_value s on s.asset_id = a.id and s.property_id = p.id and p.property_type = 'single_choice'
          left join multiple_choice_value mc on mc.asset_id = a.id and mc.property_id = p.id and p.property_type = 'multiple_choice'
          left join enumerated_value e on mc.enumerated_value_id = e.id or s.enumerated_value_id = e.id
          join image i on e.image_id = i.id"""

  def assetLrmPositionId =
    "select lrm_position_id from asset where id = ?"

  def assetsByTypeWithPosition = allAssetsWithoutProperties + " where t.id = ?"

  def assetWithPositionById = allAssets + " WHERE a.id = ?"

  def assetWhereId(id: Long) = sql"WHERE a.id = $id"

  def assetsWithPositionByMunicipalityNumber = assetsByTypeWithPosition + " AND rl.municipality_number = ?"

  def assetTypes = sql"select id, name, geometry_type from asset_type"

  def andByValidityTimeConstraint = "AND (a.valid_from <= ? OR a.valid_from IS NULL) AND (a.valid_to >= ? OR a.valid_to IS NULL)"

  def andExpiredBefore = "AND a.valid_to < ? AND a.valid_from IS NOT NULL"

  def andValidAfter = "AND a.valid_from > ?"

  def updateAssetBearing(assetId: Long, bearing: Int) = sqlu"update asset set bearing = $bearing where id = $assetId"

  def insertAsset(assetId: Long, assetTypeId: Long, roadLinkId: Long, bearing: Int, creator: String) =
    sqlu"""
      insert into asset(id, asset_type_id, lrm_position_id, bearing, valid_from, created_by)
      values ($assetId, $assetTypeId, $roadLinkId, $bearing, ${new LocalDate()}, $creator)
    """

  def propertyTypeByPropertyId = "SELECT property_type FROM property WHERE id = ?"

  def multipleChoicePropertyValuesByAssetIdAndPropertyId = "SELECT mcv.id, ev.value FROM multiple_choice_value mcv, enumerated_value ev " +
    "WHERE mcv.enumerated_value_id = ev.id AND mcv.asset_id = ? AND mcv.property_id = ?"

  def deleteMultipleChoiceValue(valueId: Long) = sqlu"delete from multiple_choice_value WHERE id = $valueId"

  def insertMultipleChoiceValue(assetId: Long, propertyId: Long, propertyValue: Long) =
    sqlu"""
      insert into multiple_choice_value(id, property_id, asset_id, enumerated_value_id, modified_date)
      values (primary_key_seq.nextval, $propertyId, $assetId,
        (select id from enumerated_value WHERE value = $propertyValue and property_id = $propertyId), current_timestamp)
    """

  def deleteMultipleChoiceProperty(assetId: Long, propertyId: Long) =
    sqlu"delete from multiple_choice_value where asset_id = $assetId and property_id = $propertyId"

  def insertTextProperty(assetId: Long, propertyId: Long, valueFi: String) = {
    sqlu"""
      insert into text_property_value(id, property_id, asset_id, value_fi, created_date)
      values (primary_key_seq.nextval, $propertyId, $assetId, $valueFi, CURRENT_TIMESTAMP)
    """
  }

  def updateTextProperty(assetId: Long, propertyId: Long, valueFi: String) =
    sqlu"update text_property_value set value_fi = $valueFi where asset_id = $assetId and property_id = $propertyId"

  def deleteTextProperty(assetId: Long, propertyId: Long) =
    sqlu"delete from text_property_value where asset_id = $assetId and property_id = $propertyId"

  def insertSingleChoiceProperty(assetId: Long, propertyId: Long, value: Long) = {
    sqlu"""
      insert into single_choice_value(asset_id, enumerated_value_id, property_id, modified_date)
      values ($assetId, (select id from enumerated_value where property_id = $propertyId and value = $value), $propertyId, current_timestamp)
    """
  }

  def updateSingleChoiceProperty(assetId: Long, propertyId: Long, value: Long) =
    sqlu"""
      update single_choice_value set enumerated_value_id =
        (select id from enumerated_value where property_id = $propertyId and value = $value)
        where asset_id = $assetId and property_id = $propertyId
    """

  def deleteSingleChoiceProperty(assetId: Long, propertyId: Long) =
    sqlu"delete from single_choice_value where asset_id = $assetId and property_id = $propertyId"

  def updateCommonProperty(assetId: Long, propertyColumn: String, value: String) =
    sqlu"update asset set #$propertyColumn = $value where id = $assetId"

  def updateCommonDateProperty(assetId: Long, propertyColumn: String, value: Option[DateTime]) =
    sqlu"update asset set #$propertyColumn = $value where id = $assetId"

  def roadLinks = "SELECT id, geom, end_date, municipality_number FROM road_link WHERE functional_class IN (1, 2, 3, 4, 5, 6)"

  def roadLinksAndMunicipality(municipalityNumbers: Seq[Int]) =
    if (municipalityNumbers.isEmpty) "" else "AND municipality_number IN (" + municipalityNumbers.map(_ => "?").mkString(",") + ")"

  def roadLinksAndWithinBoundingBox = "AND SDO_FILTER(geom, ?) = 'TRUE'"

  def enumeratedPropertyValues = """
    select p.id, p.property_type, p.name_fi as property_name, p.required, e.value, e.name_fi from asset_type a
    join property p on p.asset_type_id = a.id
    join enumerated_value e on e.property_id = p.id
    where p.property_type = 'single_choice' or p.property_type = 'multiple_choice' and a.id = ?"""

  def getPointLRMeasure(latLonGeometry: JGeometry, roadLinkId: Long, conn: Connection): BigDecimal = {
    val getLRMeasure = conn.prepareStatement("SELECT SDO_LRS.GET_MEASURE(SDO_LRS.PROJECT_PT(rl.geom, ?)), SDO_LRS.GEOM_SEGMENT_LENGTH(rl.geom) " +
        "FROM road_link rl WHERE rl.id = ?")
    val encodedGeometry: STRUCT = storeGeometry(latLonGeometry, conn)
    getLRMeasure.setObject(1, encodedGeometry)
    getLRMeasure.setLong(2, roadLinkId)
    val rs = getLRMeasure.executeQuery()
    if (rs.next()) {
      val measure = rs.getBigDecimal(1)
      val length = rs.getBigDecimal(2).setScale(0, RoundingMode.DOWN) // TODO: update rounding precision when LRM_POSITION table is updated, use GEOM_END_MEASURE
      measure.min(length)
    } else {
      throw new RuntimeException("ROAD_LINK " + roadLinkId + " NOT FOUND")
    }
  }

  def updateLRMeasure(lrmPosition: LRMPosition, roadLinkId: Long, lrMeasure: BigDecimal, conn: Connection) {
    updateLRMeasure(lrmPosition.id, roadLinkId, lrMeasure, conn)
  }

  def updateLRMeasure(lrmPositionId: Long, roadLinkId: Long, lrMeasure: BigDecimal, conn: Connection) {
    val updateMeasure = conn.prepareStatement("UPDATE lrm_position SET start_measure = ?, end_measure = ?, road_link_id = ? WHERE id = ?")
    updateMeasure.setBigDecimal(1, lrMeasure.bigDecimal)
    updateMeasure.setBigDecimal(2, lrMeasure.bigDecimal)
    updateMeasure.setLong(3, roadLinkId)
    updateMeasure.setLong(4, lrmPositionId)
    updateMeasure.executeUpdate()
  }

  def insertLRMPosition(lrmPositionId: Long, roadLinkId: Long, lrMeasure: BigDecimal, conn: Connection): Long = {
    val insertPosition = conn.prepareStatement("INSERT INTO lrm_position (id, start_measure, end_measure, road_link_id) values (?, ?, ?, ?)")
    insertPosition.setLong(1, lrmPositionId)
    insertPosition.setBigDecimal(2, lrMeasure.bigDecimal)
    insertPosition.setBigDecimal(3, lrMeasure.bigDecimal)
    insertPosition.setLong(4, roadLinkId)
    insertPosition.executeUpdate()
  }

  def storeGeometry(geometry: JGeometry, conn: Connection): STRUCT = {
    JGeometry.store(geometry, bonecpToInternalConnection(conn))
  }

  def collectedQuery[R](qc: QueryCollector)(implicit rconv: GetResult[R], pconv: SetParameter[IndexedSeq[Any]]): List[R] = {
    Q.query[IndexedSeq[Any], R](qc.sql).list(qc.params)
  }

  implicit object GetByteArray extends GetResult[Array[Byte]] {
    def apply(rs: PositionedResult) = rs.nextBytes()
  }

  def imageById = "select image_data from image where id = ?"
}
