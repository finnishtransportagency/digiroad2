package fi.liikennevirasto.digiroad2.dao

import java.sql.Connection
import slick.driver.JdbcDriver.backend.Database
import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.util.LorryParkingInDATEX2
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import com.jolbox.bonecp.ConnectionHandle
import fi.liikennevirasto.digiroad2.user.{Configuration, User}
import org.joda.time.{DateTime, LocalDate}
import org.json4s.NoTypeHints
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization.read
import slick.jdbc.StaticQuery._
import slick.jdbc.{GetResult, PositionedResult, SetParameter, StaticQuery => Q}
import Q._
import com.github.tototoshi.slick.MySQLJodaSupport._
import org.postgis.PGgeometry
import org.postgresql.util.PGobject
import java.util.Locale


object Queries {
  def bonecpToInternalConnection(cpConn: Connection) = cpConn.asInstanceOf[ConnectionHandle].getInternalConnection

  case class QueryCollector(sql: String, params: IndexedSeq[Any] = IndexedSeq()) {
    def add(element: Option[(String, List[Any])]): QueryCollector = element match {
      case Some((s, ps))  => this.copy(sql = this.sql + " " + s, this.params ++ ps)
      case _ => this
    }
  }
  case class PropertyRow(propertyId: Long, publicId: String, propertyType: String, propertyRequired: Boolean, propertyValue: String, propertyDisplayValue: String, propertyMaxCharacters: Option[Int] = None, propertyGroupedId: Long = 0)
  case class AdditionalPanelRow(publicId: String, propertyType: String, panelType: Int, panelInfo: String, panelValue: String, formPosition: Int, panelText: String, panelSize: Int, panelCoatingType: Int, panelColor: Int)
  case class DynamicPropertyRow(publicId: String, propertyType: String, required: Boolean = false, propertyValue: Option[Any])

  def objectToPoint(geometry: Object): Point = {
    val pgObject = geometry.asInstanceOf[PGobject]
    val geom = PGgeometry.geomFromString(pgObject.getValue)
    val point =geom.getFirstPoint
    Point(point.x, point.y)
  }

  implicit val getPoint = new GetResult[Point] {
    def apply(r: PositionedResult) = {
      objectToPoint(r.nextObject())
    }
  }

  implicit val getAssetType = new GetResult[AssetType] {
    def apply(r: PositionedResult) = {
      AssetType(r.nextLong, r.nextString, r.nextString)
    }
  }

  implicit val formats = Serialization.formats(NoTypeHints)

  implicit val getUser = new GetResult[User] {
    def apply(r: PositionedResult) = {
      User(r.nextLong(), r.nextString(), read[Configuration](r.nextString()), r.nextStringOption())
    }
  }

  case class EnumeratedPropertyValueRow(propertyId: Long, propertyPublicId: String, propertyType: String, propertyName: String, required: Boolean, value: Double, displayValue: String)

  implicit val getEnumeratedValue = new GetResult[EnumeratedPropertyValueRow] {
    def apply(r: PositionedResult) = {
      EnumeratedPropertyValueRow(r.nextLong, r.nextString, r.nextString, r.nextString, r.nextBoolean, r.nextDouble, r.nextString)
    }
  }

  def nextPrimaryKeyId = sql"select nextval('primary_key_seq')"

  def nextNationalBusStopId = sql"select nextval('national_bus_stop_id_seq')"

  def nextLrmPositionPrimaryKeyId = sql"select nextval('lrm_position_primary_key_seq')"

  def nextGroupedId = sql"select nextval('grouped_id_seq')"

  def nextPrimaryKeyIds(len: Int) = {
    sql"""select nextval('primary_key_seq') from generate_series(1, $len)"""
  }

  def nextLaneHistoryEventOrderNumber = sql"select nextval('lane_history_event_order_seq')"

  def nextLaneHistoryEventOrderNumbers(len: Int) = {
    sql"""select nextval('lane_history_event_order_seq') from generate_series(1, $len)"""
  }

  def fetchLrmPositionIds(len: Int) = {
    sql"""SELECT nextval('lrm_position_primary_key_seq') from generate_series(1,$len)""".as[Long].list
  }

  def updateAssetModified(assetId: Long, updater: String) =
    sqlu"""
      update asset set modified_by = $updater, modified_date = current_timestamp where id = $assetId
    """
  def updateAssestModified(assetIds: Seq[Long], updater: String) = {
    sqlu"""update asset set modified_by = $updater, modified_date = current_timestamp where id in(#${assetIds.mkString(",")})""".execute
  }
  

  def updateAssetGeometry(id: Long, point: Point): Unit = {
    val x = point.x
    val y = point.y
    val pointGeometry =s"POINT($x $y 0 0)"
    sqlu"""
      UPDATE asset
        SET geometry = ST_GeomFromText($pointGeometry,3067)
        WHERE id = $id
    """.execute
  }

  def linearGeometry(startPoint: Point, endPoint: Point,assetLength:Double): String ={
    val startPointX =startPoint.x.toString
    val startPointY =startPoint.y.toString
    val endPointX =endPoint.x.toString
    val endPointY =endPoint.x.toString
    s"LINESTRING($startPointX $startPointY 0.0 0.0,$endPointX $endPointY 0.0 $assetLength)"
  }

  def pointGeometry(lon: Double, lat: Double): String ={s"POINT($lon $lat 0 0)"}

  def insertAsset(assetId: Long, nationalId: Long,
                  assetTypeId: Long, bearing: Int,
                  creator: String, municipalityCode: Int) =
    sqlu"""
      insert into asset(id, national_id, asset_type_id, bearing, valid_from, created_by, municipality_code)
      values ($assetId, $nationalId, $assetTypeId, $bearing, ${new LocalDate()}, $creator, $municipalityCode)
    """

  def expireAsset(id: Long, username: String): Unit = {
    sqlu"""update ASSET set VALID_TO = current_timestamp, MODIFIED_BY = $username, modified_date = current_timestamp where id = $id""".execute
  }

  def propertyIdByPublicIdAndTypeId = "select id from property where public_id = ? and asset_type_id = ?"
  def propertyIdByPublicId = "select id from property where public_id = ?"
  def getPropertyIdByPublicId(id: String) = sql"select id from property where public_id = $id".as[Long].first
  def getPropertyMaxSize = "select max_value_length from property where public_id = ?"

  def propertyTypeByPropertyId = "SELECT property_type FROM property WHERE id = ?"

  def multipleChoicePropertyValuesByAssetIdAndPropertyId = "SELECT mcv.id, ev.value FROM multiple_choice_value mcv, enumerated_value ev " +
    "WHERE mcv.enumerated_value_id = ev.id AND mcv.asset_id = ? AND mcv.property_id = ?"

  def deleteMultipleChoiceValue(valueId: Long) = sqlu"delete from multiple_choice_value WHERE id = $valueId"

  def insertMultipleChoiceValue(assetId: Long, propertyId: Long, propertyValue: Long, groupedId: Option[Long] = Some(0)) =
    sqlu"""
      insert into multiple_choice_value(id, property_id, asset_id, enumerated_value_id, modified_date, grouped_id)
      values (nextval('primary_key_seq'), $propertyId, $assetId,
        (select id from enumerated_value WHERE value = $propertyValue and property_id = $propertyId), current_timestamp, $groupedId)
    """

  def updateMultipleChoiceValue(assetId: Long, propertyId: Long, propertyValue: Long, groupedId: Option[Long] = Some(0)) =
    sqlu"""
     update multiple_choice_value set enumerated_value_id =
       (select id from enumerated_value where value = $propertyValue and property_id = $propertyId)
       where asset_id = $assetId and property_id = $propertyId and grouped_id = $groupedId
   """

  def insertTextProperty(assetId: Long, propertyId: Long, valueFi: String, groupedId: Option[Long] = Some(0)) = {
    sqlu"""
      insert into text_property_value(id, property_id, asset_id, value_fi, created_date, grouped_id)
      values (nextval('primary_key_seq'), $propertyId, $assetId, $valueFi, current_timestamp, $groupedId)
    """
  }

  def updateTextProperty(assetId: Long, propertyId: Long, valueFi: String, groupedId: Option[Long] = Some(0)) =
    sqlu"update text_property_value set value_fi = $valueFi where asset_id = $assetId and property_id = $propertyId and grouped_id = $groupedId"

  def deleteTextProperty(assetId: Long, propertyId: Long) =
    sqlu"delete from text_property_value where asset_id = $assetId and property_id = $propertyId"

  def existsTextProperty =
    "select id from text_property_value where asset_id = ? and property_id = ?"

  def existsTextPropertyWithGroupedId =
    "select id from text_property_value where asset_id = ? and property_id = ? and grouped_id = ?"

  def existsMultipleChoiceProperty =
    "select asset_id from multiple_choice_value where asset_id = ? and property_id = ?"

  def existsMultipleChoicePropertyWithGroupedId =
    "select asset_id from multiple_choice_value where asset_id = ? and property_id = ? and grouped_id = ?"

  def insertNumberProperty(assetId: Long, propertyId: Long, value: Int) = {
    sqlu"""
      insert into number_property_value(id, property_id, asset_id, value)
      values (nextval('primary_key_seq'), $propertyId, $assetId, $value)
    """
  }

  def insertNumberProperty(assetId: Long, propertyId: Long, value: Double) = {
    sqlu"""
      insert into number_property_value(id, property_id, asset_id, value)
      values (nextval('primary_key_seq'), $propertyId, $assetId, $value)
    """
  }

  def insertNumberProperty(assetId: Long, propertyId: Long, value: Option[Double], groupedId: Option[Long] = Some(0)) = {
    sqlu"""
      insert into number_property_value(id, property_id, asset_id, value, grouped_id)
      values (nextval('primary_key_seq'), $propertyId, $assetId, $value, $groupedId)
    """
  }

  def updateNumberProperty(assetId: Long, propertyId: Long, value: Option[Double], groupedId: Option[Long] = Some(0)) =
    sqlu"update number_property_value set value = $value where asset_id = $assetId and property_id = $propertyId and grouped_id = $groupedId"

  def updateNumberProperty(assetId: Long, propertyId: Long, value: Double) =
    sqlu"update number_property_value set value = $value where asset_id = $assetId and property_id = $propertyId"

  def updateNumberProperty(assetId: Long, propertyId: Long, value: Int) =
    sqlu"update number_property_value set value = $value where asset_id = $assetId and property_id = $propertyId"

  def deleteNumberProperty(assetId: Long, propertyId: Long) =
    sqlu"delete from number_property_value where asset_id = $assetId and property_id = $propertyId"

  def existsNumberProperty =
    "select id from number_property_value where asset_id = ? and property_id = ?"

  def existsNumberPropertyWithGroupedId =
    "select id from number_property_value where asset_id = ? and property_id = ? and grouped_id = ?"

  def existingGroupedIdForAssetQuery =
    "select grouped_id from single_choice_value where asset_id = ? and property_id = (select id from property where public_id = ?)"

  def insertDateProperty(assetId: Long, propertyId: Long, dateTime: DateTime) = {
    sqlu"""
      insert into date_property_value(id, property_id, asset_id, date_time)
      values (nextval('primary_key_seq'), $propertyId, $assetId, $dateTime)
    """
  }

  def existsValidityPeriodProperty =
    "select id from validity_period_property_value where asset_id = ? and property_id = ?"

  def deleteValidityPeriodProperty(assetId: Long, propertyId: Long) =
    sqlu"delete from validity_period_property_value where asset_id = $assetId and property_id = $propertyId"

  def insertValidityPeriodProperty(assetId: Long, propertyId: Long, validityPeriodValue: ValidityPeriodValue) = {
    sqlu"""
      insert into validity_period_property_value(id, property_id, asset_id, type, period_week_day, start_hour, end_hour, start_minute, end_minute)
      values (nextval('primary_key_seq'), $propertyId, $assetId, ${validityPeriodValue.periodType}, ${validityPeriodValue.days}, ${validityPeriodValue.startHour},
      ${validityPeriodValue.endHour}, ${validityPeriodValue.startMinute}, ${validityPeriodValue.endMinute})
    """
  }

  def updateDateProperty(assetId: Long, propertyId: Long, dateTime: DateTime) =
    sqlu"update date_property_value set date_time = $dateTime where asset_id = $assetId and property_id = $propertyId"

  def deleteDateProperty(assetId: Long, propertyId: Long) =
    sqlu"delete from date_property_value where asset_id = $assetId and property_id = $propertyId"

  def existsDateProperty =
    "select id from date_property_value where asset_id = ? and property_id = ?"

  def insertSingleChoiceProperty(assetId: Long, propertyId: Long, value: Long) = {
    sqlu"""
      insert into single_choice_value(asset_id, enumerated_value_id, property_id, modified_date)
      values ($assetId, (select id from enumerated_value where property_id = $propertyId and value = $value), $propertyId, current_timestamp)
    """
  }

  def insertSingleChoiceProperty(assetId: Long, propertyId: Long, value: Double, groupedId: Option[Long]) = {
    sqlu"""
      insert into single_choice_value(asset_id, enumerated_value_id, property_id, modified_date, grouped_id)
      values ($assetId, (select id from enumerated_value where property_id = $propertyId and value = $value), $propertyId, current_timestamp, $groupedId)
    """
  }

  def updateSingleChoiceProperty(assetId: Long, propertyId: Long, value: Long) =
    sqlu"""
      update single_choice_value set enumerated_value_id =
        (select id from enumerated_value where property_id = $propertyId and value = $value)
        where asset_id = $assetId and property_id = $propertyId
    """

  def updateSingleChoiceProperty(assetId: Long, propertyId: Long, value: Double, groupedId: Option[Long]) =
    sqlu"""
      update single_choice_value set enumerated_value_id =
        (select id from enumerated_value where property_id = $propertyId and value = $value)
        where asset_id = $assetId and property_id = $propertyId and grouped_id = $groupedId
    """

  def insertAdditionalPanelProperty(assetId: Long, value: AdditionalPanel) = {
    val id = Sequences.nextPrimaryKeySeqValue
    sqlu"""
    INSERT INTO additional_panel (id, asset_id ,property_id, additional_sign_type, additional_sign_value, additional_sign_info, form_position, additional_sign_text, additional_sign_size, additional_sign_coating_type, additional_sign_panel_color)
    VALUES ($id, $assetId, (select id from property where public_id='additional_panel'), ${value.panelType}, ${value.panelValue}, ${value.panelInfo}, ${value.formPosition}, ${value.text}, ${value.size}, ${value.coating_type}, ${value.additional_panel_color})
    """
  }

  def deleteAdditionalPanelProperty(assetId: Long) = {
    sqlu"""
    DELETE FROM additional_panel where asset_id = $assetId
    """
  }

  def updateAdditionalPanelProperties (assetId: Long): Unit = {
    sqlu"""UPDATE ADDITIONAL_PANEL SET ADDITIONAL_SIGN_SIZE = 99, ADDITIONAL_SIGN_COATING_TYPE = 99, ADDITIONAL_SIGN_PANEL_COLOR = 99
           WHERE ASSET_ID = $assetId AND ADDITIONAL_SIGN_SIZE IS NULL OR ADDITIONAL_SIGN_COATING_TYPE IS NULL OR ADDITIONAL_SIGN_PANEL_COLOR IS NULL""".execute

  }

  def existsSingleChoiceProperty =
    "select asset_id from single_choice_value where asset_id = ? and property_id = ?"

  def existsSingleChoicePropertyWithGroupedId =
    "select asset_id from single_choice_value where asset_id = ? and property_id = ? and grouped_id = ?"

  def updateCommonProperty(assetId: Long, propertyColumn: String, value: String, isLrmAssetProperty: Boolean = false) =
    if (isLrmAssetProperty)
      sqlu"update lrm_position set #$propertyColumn = cast($value as numeric) where id = (select position_id from asset_link where asset_id = $assetId)"
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
      val row = v.head
      EnumeratedPropertyValue(row.propertyId, row.propertyPublicId, row.propertyName, row.propertyType, row.required, v.map(r => {
        val value: AnyVal = if (r.value % 1 == 0) r.value.toInt else r.value
        PropertyValue(value.toString , Some(r.displayValue))
      }))
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
        Property(r.nextLong, r.nextString, r.nextString, r.nextBoolean, Seq(), r.nextIntOption())
      }
    }
    sql"""
      select p.id, p.public_id, p.property_type, p.required, p.max_value_length from property p where p.asset_type_id = $assetTypeId
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

  def getMunicipalitiesByEly(elyNro: Int): Seq[Int] = {
    sql"""
      select m.id from municipality m where m.ELY_NRO = $elyNro
    """.as[Int].list
  }

  def existsDatePeriodProperty =
    "select id from date_period_value where asset_id = ? and property_id = ?"

  def deleteDatePeriodProperty(assetId: Long, propertyId: Long) =
    sqlu"delete from date_period_value where asset_id = $assetId and property_id = $propertyId"

  def insertDatePeriodProperty(assetId: Long, propertyId: Long, startDate: DateTime, endDate: DateTime) = {
    sqlu"""
      insert into date_period_value(id, property_id, asset_id, start_date, end_date)
      values (nextval('primary_key_seq'), $propertyId, $assetId, ${startDate}, ${endDate})
    """
  }

  //Table lorry_parking_to_datex2 created using an shape file importer, does't exist on Flyway
  def getLorryParkingToTransform(): Set[LorryParkingInDATEX2] = {
    //When getting the geometry column it need to be in the SRID 4258
    //workaround if geometry is not in SRID 4258. SDO_CS.TRANSFORM(GEOMETRY, 4258)
    Q.queryNA[LorryParkingInDATEX2](
      s"""
      select PALVPISTID, PALVELUID, TYYPPI, TYYPPI_TAR, NIMI, LISATIEDOT, MUOKKAUSPV, KUNTAKOODI, GEOMETRY from lorry_parking_to_datex2
    """).iterator.toSet
  }

  implicit val getLorryParkings = new GetResult[LorryParkingInDATEX2] {
    def apply(r: PositionedResult) = {
      val servicePointId = r.nextLongOption()
      val serviceId = r.nextLongOption()
      val parkingType = r.nextInt()
      val parkingTypeMeaning = r.nextInt()
      val name = r.nextStringOption()
      val additionalInfo = r.nextStringOption()
      val modifiedDate = r.nextStringOption()
      val municipalityCode = r.nextInt()
      val point = r.nextObjectOption().map(objectToPoint).get

      LorryParkingInDATEX2(servicePointId, serviceId, parkingType, parkingTypeMeaning, name, additionalInfo, point.x, point.y, modifiedDate, municipalityCode)
    }
  }

  implicit object GetByteArray extends GetResult[Array[Byte]] {
    def apply(rs: PositionedResult) = rs.nextBytes()
  }

  def mergeMunicipalities(municipalityToDelete: Int, municipalityToMerge: Int): Unit = {
    sqlu"""UPDATE ASSET SET MUNICIPALITY_CODE = $municipalityToMerge, MODIFIED_DATE = current_timestamp, MODIFIED_BY = 'batch_process_municipality_merge' WHERE MUNICIPALITY_CODE = $municipalityToDelete""".execute
    sqlu"""UPDATE UNKNOWN_SPEED_LIMIT SET MUNICIPALITY_CODE = $municipalityToMerge WHERE MUNICIPALITY_CODE = $municipalityToDelete""".execute
    sqlu"""UPDATE INACCURATE_ASSET SET MUNICIPALITY_CODE = $municipalityToMerge WHERE MUNICIPALITY_CODE = $municipalityToDelete""".execute
    sqlu"""UPDATE INCOMPLETE_LINK SET MUNICIPALITY_CODE = $municipalityToMerge WHERE MUNICIPALITY_CODE = $municipalityToDelete""".execute
    sqlu"""DELETE FROM MUNICIPALITY_VERIFICATION WHERE MUNICIPALITY_ID = $municipalityToDelete""".execute
    sqlu"""DELETE FROM DASHBOARD_INFO WHERE MUNICIPALITY_ID = $municipalityToDelete""".execute
    sqlu"""DELETE FROM MUNICIPALITY WHERE ID = $municipalityToDelete""".execute
  }

  def deleteAdditionalGroupedAsset(assetId: Long, groupedId: Long): Unit = {
    sqlu"""delete from number_property_value where asset_id = $assetId and grouped_id = $groupedId""".execute
    sqlu"""delete from text_property_value where asset_id = $assetId and grouped_id = $groupedId""".execute
    sqlu"""delete from single_choice_value where asset_id = $assetId and grouped_id = $groupedId""".execute
    sqlu"""delete from multiple_choice_value where asset_id = $assetId and grouped_id = $groupedId""".execute
  }
  
  def getLatestSuccessfulSamuutus(typeid: Int):DateTime = {
    sql"""select last_succesfull_samuutus from samuutus_success where asset_type_id = $typeid """.as[DateTime].list.head
  }
  def updateLatestSuccessfulSamuutus(typeid: Int, latestSuccess: DateTime): Unit = {
    sqlu"""UPDATE samuutus_success SET last_succesfull_samuutus = to_timestamp($latestSuccess, 'YYYY-MM-DD"T"HH24:MI:SS.FF') WHERE asset_type_id = $typeid""".execute
  }

  // Used for performance reasons, remember to add back constraints after operation using method addLaneFKConstraints()
  def dropLaneFKConstraints(): Unit = {
    sqlu"ALTER TABLE LANE_ATTRIBUTE DROP CONSTRAINT fk_lane_attribute_lane".execute
    sqlu"ALTER TABLE LANE_LINK DROP CONSTRAINT fk_lane_link_lane".execute
    sqlu"ALTER TABLE LANE_LINK DROP CONSTRAINT fk_lane_link_lane_position".execute
  }

  def addLaneFKConstraints(): Unit = {
    sqlu"ALTER TABLE lane_attribute ADD CONSTRAINT fk_lane_attribute_lane FOREIGN KEY (lane_id) REFERENCES lane(id) ON DELETE NO ACTION NOT DEFERRABLE INITIALLY IMMEDIATE".execute
    sqlu"ALTER TABLE LANE_LINK ADD CONSTRAINT fk_lane_link_lane_position FOREIGN KEY (lane_position_id) REFERENCES lane_position(id) ON DELETE NO ACTION NOT DEFERRABLE INITIALLY IMMEDIATE".execute
    sqlu"ALTER TABLE LANE_LINK ADD CONSTRAINT fk_lane_link_lane FOREIGN KEY (lane_id) REFERENCES lane(id) ON DELETE NO ACTION NOT DEFERRABLE INITIALLY IMMEDIATE".execute
  }
 
}
