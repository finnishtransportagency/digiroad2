package fi.liikennevirasto.digiroad2.dao

import java.sql.Connection

import slick.driver.JdbcDriver.backend.Database
import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset._
import slick.jdbc.{StaticQuery => Q, PositionedResult, GetResult, SetParameter}
import Database.dynamicSession
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

  case class PropertyRow(propertyId: Long, publicId: String, propertyType: String, propertyRequired: Boolean, propertyValue: String, propertyDisplayValue: String)

  def bytesToPoint(bytes: Array[Byte]): Point = {
    val geometry = JGeometry.load(bytes)
    val point = geometry.getPoint()
    Point(point(0), point(1))
  }

  implicit val getPoint = new GetResult[Point] {
    def apply(r: PositionedResult) = {
      bytesToPoint(r.nextBytes)
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

  def nextLrmPositionPrimaryKeyId = sql"select lrm_position_primary_key_seq.nextval from dual"

  def nextViitePrimaryKeyId = sql"select viite_general_seq.nextval from dual"

  def nextCommonHistoryValue = sql"select common_history_seq.nextval from dual"

  def fetchViitePrimaryKeyId(len: Int) = {
    sql"""select viite_general_seq.nextval from dual connect by level <= $len""".as[Long].list
  }

  def fetchLrmPositionIds(len: Int) = {
    sql"""SELECT lrm_position_primary_key_seq.nextval FROM dual connect by level <= $len""".as[Long].list
  }

  def updateAssetModified(assetId: Long, updater: String) =
    sqlu"""
      update asset set modified_by = $updater, modified_date = SYSDATE where id = $assetId
    """

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

  def insertAsset(assetId: Long, externalId: Long,
                  assetTypeId: Long, bearing: Int,
                  creator: String, municipalityCode: Int) =
    sqlu"""
      insert into asset(id, external_id, asset_type_id, bearing, valid_from, created_by, municipality_code)
      values ($assetId, $externalId, $assetTypeId, $bearing, ${new LocalDate()}, $creator, $municipalityCode)
    """

  def expireAsset(id: Long, username: String): Unit = {
    sqlu"""update ASSET set VALID_TO = sysdate, MODIFIED_BY = $username, modified_date = sysdate where id = $id""".execute
  }

  def propertyIdByPublicId = "select id from property where public_id = ?"
  def getPropertyIdByPublicId(id: String) = sql"select id from property where public_id = $id".as[Long].first

  def propertyTypeByPropertyId = "SELECT property_type FROM property WHERE id = ?"

  def multipleChoicePropertyValuesByAssetIdAndPropertyId = "SELECT mcv.id, ev.value FROM multiple_choice_value mcv, enumerated_value ev " +
    "WHERE mcv.enumerated_value_id = ev.id AND mcv.asset_id = ? AND mcv.property_id = ?"

  def deleteMultipleChoiceValue(valueId: Long) = sqlu"delete from multiple_choice_value WHERE id = $valueId"

  def insertMultipleChoiceValue(assetId: Long, propertyId: Long, propertyValue: Long) =
    sqlu"""
      insert into multiple_choice_value(id, property_id, asset_id, enumerated_value_id, modified_date)
      values (primary_key_seq.nextval, $propertyId, $assetId,
        (select id from enumerated_value WHERE value = $propertyValue and property_id = $propertyId), SYSDATE)
    """

  def insertTextProperty(assetId: Long, propertyId: Long, valueFi: String) = {
    sqlu"""
      insert into text_property_value(id, property_id, asset_id, value_fi, created_date)
      values (primary_key_seq.nextval, $propertyId, $assetId, $valueFi, SYSDATE)
    """
  }

  def updateTextProperty(assetId: Long, propertyId: Long, valueFi: String) =
    sqlu"update text_property_value set value_fi = $valueFi where asset_id = $assetId and property_id = $propertyId"

  def deleteTextProperty(assetId: Long, propertyId: Long) =
    sqlu"delete from text_property_value where asset_id = $assetId and property_id = $propertyId"

  def existsTextProperty =
    "select id from text_property_value where asset_id = ? and property_id = ?"

  def insertNumberProperty(assetId: Long, propertyId: Long, value: Int) = {
    sqlu"""
      insert into number_property_value(id, property_id, asset_id, value)
      values (primary_key_seq.nextval, $propertyId, $assetId, $value)
    """
  }

  def updateNumberProperty(assetId: Long, propertyId: Long, value: Int) =
    sqlu"update number_property_value set value = $value where asset_id = $assetId and property_id = $propertyId"

  def deleteNumberProperty(assetId: Long, propertyId: Long) =
    sqlu"delete from number_property_value where asset_id = $assetId and property_id = $propertyId"

  def existsNumberProperty =
    "select id from number_property_value where asset_id = ? and property_id = ?"

  def insertSingleChoiceProperty(assetId: Long, propertyId: Long, value: Long) = {
    sqlu"""
      insert into single_choice_value(asset_id, enumerated_value_id, property_id, modified_date)
      values ($assetId, (select id from enumerated_value where property_id = $propertyId and value = $value), $propertyId, SYSDATE)
    """
  }

  def updateSingleChoiceProperty(assetId: Long, propertyId: Long, value: Long) =
    sqlu"""
      update single_choice_value set enumerated_value_id =
        (select id from enumerated_value where property_id = $propertyId and value = $value)
        where asset_id = $assetId and property_id = $propertyId
    """

  def existsSingleChoiceProperty =
    "select asset_id from single_choice_value where asset_id = ? and property_id = ?"

  def updateCommonProperty(assetId: Long, propertyColumn: String, value: String, isLrmAssetProperty: Boolean = false) =
    if (isLrmAssetProperty)
      sqlu"update lrm_position set #$propertyColumn = $value where id = (select position_id from asset_link where asset_id = $assetId)"
    else
      sqlu"update asset set #$propertyColumn = $value where id = $assetId"

  def updateCommonDateProperty(assetId: Long, propertyColumn: String, value: Option[DateTime], isLrmAssetProperty: Boolean = false) =
    if (isLrmAssetProperty)
      sqlu"update lrm_position set #$propertyColumn = $value where id = (select position_id from asset_link where asset_id = $assetId)"
    else
      sqlu"update asset set #$propertyColumn = $value where id = $assetId"

  def enumeratedPropertyValues = """
    select p.id, p.public_id, p.property_type, ls.value_fi as property_name, p.required, e.value, e.name_fi from asset_type a
    join property p on p.asset_type_id = a.id
    join enumerated_value e on e.property_id = p.id
    join localized_string ls on ls.id = p.name_localized_string_id
    where (p.property_type = 'single_choice' or p.property_type = 'multiple_choice') and a.id = ?"""

  def getEnumeratedPropertyValues(assetTypeId: Long): Seq[EnumeratedPropertyValue] = {
    Q.query[Long, EnumeratedPropertyValueRow](enumeratedPropertyValues).apply(assetTypeId).list.groupBy(_.propertyId).map { case (k, v) =>
      val row = v(0)
      EnumeratedPropertyValue(row.propertyId, row.propertyPublicId, row.propertyName, row.propertyType, row.required, v.map(r => PropertyValue(r.value.toString, Some(r.displayValue))).toSeq)
    }.toSeq
  }

  def getNumberPropertyValue(assetId: Long, publicId: String): Option[Int] ={
    sql"""
            select v.value from number_property_value v
            join property p on v.property_id = p.id
            where v.asset_id = $assetId and p.public_id = $publicId
      """.as[Int].firstOption
  }

  def availableProperties(assetTypeId: Long): Seq[Property] = {
    implicit val getPropertyDescription = new GetResult[Property] {
      def apply(r: PositionedResult) = {
        Property(r.nextLong, r.nextString, r.nextString, r.nextBoolean, Seq())
      }
    }
    sql"""
      select p.id, p.public_id, p.property_type, p.required from property p where p.asset_type_id = $assetTypeId
    """.as[Property].list
  }

  def assetPropertyNames(language: String): Map[String, String] = {
    val valueColumn = language match  {
      case "fi" => "ls.value_fi"
      case "sv" => "ls.value_sv"
      case _ => throw new IllegalArgumentException("Language not supported: " + language)
    }
    val propertyNames = sql"""
      select p.public_id, #$valueColumn from property p, localized_string ls where ls.id = p.name_localized_string_id
    """.as[(String, String)].list.toMap
    propertyNames.filter(_._1 != null)
  }

  def storeGeometry(geometry: JGeometry, conn: Connection): STRUCT = {
    JGeometry.store(geometry, bonecpToInternalConnection(conn))
  }

  def collectedQuery[R](qc: QueryCollector)(implicit rconv: GetResult[R], pconv: SetParameter[IndexedSeq[Any]]): List[R] = {
    Q.query[IndexedSeq[Any], R](qc.sql).apply(qc.params).list
  }

  def getMunicipalities: Seq[Int] = {
    sql"""
      select id from municipality
    """.as[Int].list
  }

  def getMunicipalitiesWithoutAhvenanmaa: Seq[Int] = {
    //The road_maintainer_id of Ahvenanmaa is 0
    sql"""
      select id from municipality where ROAD_MAINTAINER_ID != 0
      """.as[Int].list
  }

  def getDistinctRoadNumbers(filterRoadAddresses : Boolean) : Seq[Int] = {
    if(filterRoadAddresses){
      sql"""
      select distinct road_number from road_address where (ROAD_NUMBER <= 20000 or (road_number >= 40000 and road_number <= 70000)) and floating = '0' AND (end_date < sysdate OR end_date IS NULL) order by road_number
      """.as[Int].list
    }
    else{
      sql"""
       select distinct road_number from road_address where floating = '0' AND (end_date < sysdate OR end_date IS NULL) order by road_number
      """.as[Int].list
    }
  }

  def getLinkIdsByRoadNumber(roadNumber: Int) : Set[Long] = {
    sql"""
       select distinct pos.LINK_ID from road_address ra join LRM_POSITION pos on ra.lrm_position_id = pos.id where ra.road_number = $roadNumber
      """.as[Long].list.toSet
  }

  def getMunicipalitiesByEly(elyNro: Int): Seq[Int] = {
    sql"""
      select m.id from municipality m where m.ELY_NRO = $elyNro
    """.as[Int].list
  }

  implicit object GetByteArray extends GetResult[Array[Byte]] {
    def apply(rs: PositionedResult) = rs.nextBytes()
  }

}
