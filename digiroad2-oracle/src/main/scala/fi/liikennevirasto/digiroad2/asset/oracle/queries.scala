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
import fi.liikennevirasto.digiroad2.asset.Modification

object Queries {
  def bonecpToInternalConnection(cpConn: Connection) = cpConn.asInstanceOf[ConnectionHandle].getInternalConnection

  case class QueryCollector(sql: String, params: IndexedSeq[Any] = IndexedSeq()) {
    def add(element: Option[(String, List[Any])]): QueryCollector = element match {
      case Some((s, ps))  => this.copy(sql = this.sql + " " + s, this.params ++ ps)
      case _ => this
    }
  }

  case class Image(imageId: Option[Long], lastModified: Option[DateTime])
  case class PropertyRow(propertyId: Long, publicId: String, propertyType: String, propertyUiIndex: Int, propertyRequired: Boolean, propertyValue: String, propertyDisplayValue: String)

  case class AssetRow(id: Long, externalId: Long, assetTypeId: Long, lon: Double, lat: Double, roadLinkId: Long, bearing: Option[Int],
                      validityDirection: Int, validFrom: Option[LocalDate], validTo: Option[LocalDate], property: PropertyRow,
                      image: Image, roadLinkEndDate: Option[LocalDate],
                      municipalityNumber: Int, created: Modification, modified: Modification, wgslon: Double, wgslat: Double)

  case class ListedAssetRow(id: Long, externalId: Long, assetTypeId: Long, lon: Double, lat: Double, roadLinkId: Long, bearing: Option[Int],
                      validityDirection: Int, validFrom: Option[LocalDate], validTo: Option[LocalDate],
                      image: Image, roadLinkEndDate: Option[LocalDate],
                      municipalityNumber: Int)

  implicit val getAssetWithPosition = new GetResult[(AssetRow, LRMPosition)] {
    def apply(r: PositionedResult) = {
      val id = r.nextLong
      val externalId = r.nextLong
      val assetTypeId = r.nextLong
      val bearing = r.nextIntOption
      val validityDirection = r.nextInt
      val validFrom = r.nextDateOption.map(new LocalDate(_))
      val validTo = r.nextDateOption.map(new LocalDate(_))
      val pos = r.nextBytes
      val propertyId = r.nextLong
      val propertyPublicId = r.nextString
      val propertyType = r.nextString
      val propertyUiIndex = r.nextInt
      val propertyRequired = r.nextBoolean
      val propertyValue = r.nextLongOption()
      val propertyDisplayValue = r.nextStringOption()
      val property = new PropertyRow(propertyId, propertyPublicId, propertyType, propertyUiIndex, propertyRequired, propertyValue.getOrElse(propertyDisplayValue.getOrElse("")).toString, propertyDisplayValue.getOrElse(null))
      val lrmId = r.nextLong
      val startMeasure = r.nextInt
      val endMeasure = r.nextInt
      val roadLinkId = r.nextLong
      val image = new Image(r.nextLongOption, r.nextTimestampOption.map(new DateTime(_)))
      val roadLinkEndDate = r.nextDateOption.map(new LocalDate(_))
      val municipalityNumber = r.nextInt
      val created = new Modification(r.nextTimestampOption().map(new DateTime(_)), r.nextStringOption)
      val modified = new Modification(r.nextTimestampOption().map(new DateTime(_)), r.nextStringOption)
      val posGeom = JGeometry.load(pos)
      val posWsg84 = JGeometry.load(r.nextBytes)
      (AssetRow(id, externalId, assetTypeId, posGeom.getJavaPoint.getX, posGeom.getJavaPoint.getY, roadLinkId, bearing, validityDirection,
        validFrom, validTo, property, image,
        roadLinkEndDate, municipalityNumber, created, modified, wgslon = posWsg84.getJavaPoint.getX, wgslat = posWsg84.getJavaPoint.getY),
        LRMPosition(lrmId, startMeasure, endMeasure, posGeom))
    }
  }

  implicit val getListedAssetWithPosition = new GetResult[(ListedAssetRow, LRMPosition)] {
    def apply(r: PositionedResult) = {
      val (id, externalId, assetTypeId, bearing, validityDirection, validFrom, validTo, pos, lrmId, startMeasure, endMeasure,
      roadLinkId, image, roadLinkEndDate, municipalityNumber) =
        (r.nextLong, r.nextLong, r.nextLong, r.nextIntOption, r.nextInt, r.nextDateOption.map(new LocalDate(_)), r.nextDateOption.map(new LocalDate(_)), r.nextBytes, r.nextLong, r.nextInt, r.nextInt,
          r.nextLong, new Image(r.nextLongOption, r.nextTimestampOption.map(new DateTime(_))), r.nextDateOption.map(new LocalDate(_)), r.nextInt)
      val posGeom = JGeometry.load(pos)
      (ListedAssetRow(id, externalId, assetTypeId, posGeom.getJavaPoint.getX, posGeom.getJavaPoint.getY, roadLinkId, bearing, validityDirection,
        validFrom, validTo, image, roadLinkEndDate, municipalityNumber),
        LRMPosition(lrmId, startMeasure, endMeasure, posGeom))
    }
  }

  val RoadLinkTypeMapping = Map((1 -> Road), (2 -> Street), (3 -> PrivateRoad))

  implicit val getRoadLink = new GetResult[RoadLink] {
    def apply(r: PositionedResult) = {
      val (id, geomBytes, endDate, municipalityNumber, linkType) = (r.nextLong, r.nextBytes, r.nextDateOption, r.nextInt, r.nextInt)
      val geom = JGeometry.load(geomBytes)
      val decimalPattern = "#.###"
      val newFormat = NumberFormat.getNumberInstance(Locale.US).asInstanceOf[DecimalFormat]
      newFormat.applyPattern(decimalPattern)
      val points: Array[Double] = geom.getOrdinatesArray
      val coords = for (i <- 0 to points.size / geom.getDimensions - 1) yield {
        (newFormat.format(points(geom.getDimensions * i)).toDouble,
         newFormat.format(points(geom.getDimensions * i + 1)).toDouble)
      }
      val roadLinkType = RoadLinkTypeMapping.getOrElse((linkType / 10), UnknownRoad)
      RoadLink(id = id,
               lonLat = coords,
               endDate = endDate.map(new LocalDate(_)),
               municipalityNumber = municipalityNumber,
               roadLinkType = roadLinkType)
    }
  }

  implicit val getAssetType = new GetResult[AssetType] {
    def apply(r: PositionedResult) = {
      AssetType(r.nextLong, r.nextString, r.nextString)
    }
  }

  case class EnumeratedPropertyValueRow(propertyId: Long, propertyPublicId: String, propertyType: String, propertyName: String, required: Boolean, value: Long, displayValue: String)

  implicit val getEnumeratedValue = new GetResult[EnumeratedPropertyValueRow] {
    def apply(r: PositionedResult) = {
      EnumeratedPropertyValueRow(r.nextLong, r.nextString, r.nextString, r.nextString, r.nextBoolean, r.nextLong, r.nextString)
    }
  }

  def nextPrimaryKeyId = sql"select primary_key_seq.nextval from dual"

  def nextNationalBusStopId = sql"select national_bus_stop_id_seq.nextval from dual"

  def updateAssetModified(assetId: Long, updater: String) =
    sqlu"""
      update asset set modified_by = $updater, modified_date = CURRENT_TIMESTAMP where id = $assetId
    """

  def allAssets =
    """
    select a.id as asset_id, a.external_id as asset_external_id, t.id as asset_type_id, a.bearing as bearing, lrm.side_code as validity_direction,
    a.valid_from as valid_from, a.valid_to as valid_to,
    SDO_LRS.LOCATE_PT(rl.geom, LEAST(lrm.start_measure, SDO_LRS.GEOM_SEGMENT_END_MEASURE(rl.geom))) AS position,
    p.id as property_id, p.public_id as property_public_id, p.property_type, p.ui_position_index, p.required, e.value as value,
    case
      when e.name_fi is not null then e.name_fi
      when tp.value_fi is not null then tp.value_fi
      else null
    end as display_value,
    lrm.id, lrm.start_measure, lrm.end_measure, lrm.road_link_id, i.id as image_id, i.modified_date as image_modified_date,
    rl.end_date, rl.municipality_number, a.created_date, a.created_by, a.modified_date, a.modified_by,
    SDO_CS.TRANSFORM(SDO_LRS.LOCATE_PT(rl.geom, LEAST(lrm.start_measure, SDO_LRS.GEOM_SEGMENT_END_MEASURE(rl.geom))),4326) AS position_wgs84
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
    select a.id as asset_id, a.external_id as asset_external_id, t.id as asset_type_id, a.bearing as bearing, lrm.side_code as validity_direction,
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

  def assetByExternalId = allAssets + " WHERE a.external_id = ?"

  def assetsByIds(ids: Seq[Long]) = " WHERE a.id IN (" + ids.map(_ => "?").mkString(",") + ")"

  def andByValidityTimeConstraint = "AND (a.valid_from <= ? OR a.valid_from IS NULL) AND (a.valid_to >= ? OR a.valid_to IS NULL)"

  def andExpiredBefore = "AND a.valid_to < ? AND a.valid_from IS NOT NULL"

  def andValidAfter = "AND a.valid_from > ?"

  def updateAssetBearing(assetId: Long, bearing: Int) = sqlu"update asset set bearing = $bearing where id = $assetId"

  def insertAsset(assetId: Long, externalId: Long, assetTypeId: Long, roadLinkId: Long, bearing: Int, creator: String) =
    sqlu"""
      insert into asset(id, external_id, asset_type_id, lrm_position_id, bearing, valid_from, created_by)
      values ($assetId, $externalId, $assetTypeId, $roadLinkId, $bearing, ${new LocalDate()}, $creator)
    """

  def deleteAsset(assetId: Long) = sqlu"""delete from asset where id = $assetId"""

  def propertyIdByPublicId = "select id from property where public_id = ?"

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

  def deleteAssetMultipleChoiceProperties(assetId: Long) =
    sqlu"delete from multiple_choice_value where asset_id = $assetId"

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

  def deleteAssetTextProperties(assetId: Long) =
    sqlu"delete from text_property_value where asset_id = $assetId"

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

  def deleteAssetSingleChoiceProperties(assetId: Long) =
    sqlu"delete from single_choice_value where asset_id = $assetId"

  def updateCommonProperty(assetId: Long, propertyColumn: String, value: String, isLrmAssetProperty: Boolean = false) =
    if (isLrmAssetProperty)
      sqlu"update lrm_position set #$propertyColumn = $value where id = (select lrm_position_id from asset where id = $assetId)"
    else
      sqlu"update asset set #$propertyColumn = $value where id = $assetId"

  def updateCommonDateProperty(assetId: Long, propertyColumn: String, value: Option[DateTime], isLrmAssetProperty: Boolean = false) =
    if (isLrmAssetProperty)
      sqlu"update lrm_position set #$propertyColumn = $value where id = (select lrm_position_id from asset where id = $assetId)"
    else
      sqlu"update asset set #$propertyColumn = $value where id = $assetId"

  def roadLinks = "SELECT id, geom, end_date, municipality_number, functional_class FROM road_link WHERE mod(functional_class, 10) IN (1, 2, 3, 4, 5, 6)"

  def roadLinksAndMunicipality(municipalityNumbers: Seq[Int]) =
    if (municipalityNumbers.isEmpty) "" else "AND municipality_number IN (" + municipalityNumbers.map(_ => "?").mkString(",") + ")"

  def roadLinksAndWithinBoundingBox = "AND SDO_FILTER(geom, ?) = 'TRUE'"

  def enumeratedPropertyValues = """
    select p.id, p.public_id, p.property_type, ls.value_fi as property_name, p.required, e.value, e.name_fi from asset_type a
    join property p on p.asset_type_id = a.id
    join enumerated_value e on e.property_id = p.id
    join localized_string ls on ls.id = p.name_localized_string_id
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

  def deleteLRMPosition(lrmPositionId: Long) = sqlu"""delete from lrm_position where id = $lrmPositionId"""

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
