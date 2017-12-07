package fi.liikennevirasto.digiroad2.dao

import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.PropertyTypes._
import fi.liikennevirasto.digiroad2.asset.{MassTransitStopValidityPeriod, _}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop.MassTransitStopOperations
import org.joda.time.format.ISODateTimeFormat
import org.joda.time.{DateTime, Interval, LocalDate}
import org.slf4j.LoggerFactory
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedParameters, PositionedResult, SetParameter, StaticQuery => Q}

import scala.language.reflectiveCalls

class MassTransitStopDao {
  val logger = LoggerFactory.getLogger(getClass)
  def typeId: Int = 10
  val idField = "external_id"

  implicit val SetStringSeq: SetParameter[IndexedSeq[Any]] = new SetParameter[IndexedSeq[Any]] {
    def apply(seq: IndexedSeq[Any], p: PositionedParameters): Unit = {
      for (i <- 1 to seq.length) {
        p.ps.setObject(i, seq(i - 1))
      }
    }
  }

  def fetchPointAssets(queryFilter: String => String): Seq[PersistedMassTransitStop] = {
    val query = """
        select a.id, a.external_id, a.asset_type_id, a.bearing, lrm.side_code,
        a.valid_from, a.valid_to, geometry, a.municipality_code, a.floating,
        lrm.adjusted_timestamp, p.id, p.public_id, p.property_type, p.required, e.value,
        case
          when e.name_fi is not null then e.name_fi
          when tp.value_fi is not null then tp.value_fi
          when np.value is not null then to_char(np.value)
          else null
        end as display_value,
        lrm.id, lrm.start_measure, lrm.end_measure, lrm.link_id,
        a.created_date, a.created_by, a.modified_date, a.modified_by,
        SDO_CS.TRANSFORM(a.geometry, 4326) AS position_wgs84, lrm.link_source,
        tbs.terminal_asset_id as terminal_asset_id
        from asset a
          join asset_link al on a.id = al.asset_id
          join lrm_position lrm on al.position_id = lrm.id
          join property p on a.asset_type_id = p.asset_type_id
          left join terminal_bus_stop_link tbs on tbs.bus_stop_asset_id = a.id
          left join single_choice_value s on s.asset_id = a.id and s.property_id = p.id and p.property_type = 'single_choice'
          left join text_property_value tp on tp.asset_id = a.id and tp.property_id = p.id and (p.property_type = 'text' or p.property_type = 'long_text' or p.property_type = 'read_only_text')
          left join multiple_choice_value mc on mc.asset_id = a.id and mc.property_id = p.id and p.property_type = 'multiple_choice'
          left join number_property_value np on np.asset_id = a.id and np.property_id = p.id and p.property_type = 'read_only_number'
          left join enumerated_value e on mc.enumerated_value_id = e.id or s.enumerated_value_id = e.id
      """
    queryToPersistedMassTransitStops(queryFilter(query))
  }

  def fetchByRadius(position : Point, meters: Int, terminalIdOption: Option[Long] = None): Seq[PersistedMassTransitStop] = {
    val topLeft = Point(position.x - meters, position.y - meters)
    val bottomRight = Point(position.x + meters, position.y + meters)
    val boundingBoxFilter = OracleDatabase.boundingBoxFilter(BoundingRectangle(topLeft, bottomRight), "a.geometry")
    val filter = s"where a.asset_type_id = $typeId and (($boundingBoxFilter ) and (a.valid_to is null or a.valid_to > sysdate))"
    val nearestStops = fetchPointAssets(withFilter(filter)).
      filter(r => GeometryUtils.geometryLength(Seq(position, Point(r.lon, r.lat))) <= meters)

    terminalIdOption match {
      case Some(terminalId) =>
        val terminalStops = fetchPointAssets(withTerminalId(terminalId))
        nearestStops.filterNot(ns => terminalStops.exists(t => t.id == ns.id)) ++ terminalStops
      case _ =>
        nearestStops
    }
  }

  private def queryToPersistedMassTransitStops(query: String): Seq[PersistedMassTransitStop] = {
    val rows = Q.queryNA[MassTransitStopRow](query).iterator.toSeq

    rows.groupBy(_.id).map { case (id, stopRows) =>
      val row = stopRows.head
      val commonProperties: Seq[Property] = AssetPropertyConfiguration.assetRowToCommonProperties(row)
      val properties: Seq[Property] = commonProperties ++ assetRowToProperty(stopRows)
      val point = row.point.get
      val validityPeriod = Some(constructValidityPeriod(row.validFrom, row.validTo))
      val stopTypes = extractStopTypes(stopRows)
      val mValue = row.lrmPosition.startMeasure
      val vvhTimeStamp = row.lrmPosition.vvhTimeStamp
      val linkSource = row.lrmPosition.linkSource

      id -> PersistedMassTransitStop(id = row.id, nationalId = row.externalId, linkId = row.linkId, stopTypes = stopTypes,
        municipalityCode = row.municipalityCode, lon = point.x, lat = point.y, mValue = mValue,
        validityDirection = Some(row.validityDirection), bearing = row.bearing,
        validityPeriod = validityPeriod, floating = row.persistedFloating, vvhTimeStamp = vvhTimeStamp, created = row.created, modified = row.modified,
        propertyData = properties, linkSource = LinkGeomSource(linkSource), terminalId = row.terminalId)
    }.values.toSeq
  }

  private implicit val getLocalDate = new GetResult[Option[LocalDate]] {
    def apply(r: PositionedResult) = {
      r.nextDateOption().map(new LocalDate(_))
    }
  }

  private implicit val getMassTransitStopRow = new GetResult[MassTransitStopRow] {
    def apply(r: PositionedResult) = {
      val id = r.nextLong
      val externalId = r.nextLong
      val assetTypeId = r.nextLong
      val bearing = r.nextIntOption
      val validityDirection = r.nextInt
      val validFrom = r.nextDateOption.map(new LocalDate(_))
      val validTo = r.nextDateOption.map(new LocalDate(_))
      val point = r.nextBytesOption.map(bytesToPoint)
      val municipalityCode = r.nextInt()
      val persistedFloating = r.nextBoolean()
      val vvhTimeStamp = r.nextLong()
      val propertyId = r.nextLong
      val propertyPublicId = r.nextString
      val propertyType = r.nextString
      val propertyRequired = r.nextBoolean
      val propertyValue = r.nextLongOption()
      val propertyDisplayValue = r.nextStringOption()
      val property = new PropertyRow(
        propertyId = propertyId,
        publicId = propertyPublicId,
        propertyType = propertyType,
        propertyRequired = propertyRequired,
        propertyValue = propertyValue.getOrElse(propertyDisplayValue.getOrElse("")).toString,
        propertyDisplayValue = propertyDisplayValue.orNull)
      val lrmId = r.nextLong
      val startMeasure = r.nextDouble()
      val endMeasure = r.nextDouble()
      val linkId = r.nextLong
      val created = new Modification(r.nextTimestampOption().map(new DateTime(_)), r.nextStringOption)
      val modified = new Modification(r.nextTimestampOption().map(new DateTime(_)), r.nextStringOption)
      val wgsPoint = r.nextBytesOption.map(bytesToPoint)
      val linkSource = r.nextInt
      val terminalId = r.nextLongOption
      MassTransitStopRow(id, externalId, assetTypeId, point, linkId, bearing, validityDirection,
        validFrom, validTo, property, created, modified, wgsPoint,
        lrmPosition = LRMPosition(lrmId, startMeasure, endMeasure, point, vvhTimeStamp, linkSource),
        municipalityCode = municipalityCode, persistedFloating = persistedFloating, terminalId = terminalId)
    }
  }

  private def extractStopTypes(rows: Seq[MassTransitStopRow]): Seq[Int] = {
    rows
      .filter { row => row.property.publicId.equals(MassTransitStopOperations.MassTransitStopTypePublicId) }
      .filterNot { row => row.property.propertyValue.isEmpty }
      .map { row => row.property.propertyValue.toInt }
  }

  private def constructValidityPeriod(validFrom: Option[LocalDate], validTo: Option[LocalDate]): String = {
    (validFrom, validTo) match {
      case (Some(from), None) => if (from.isAfter(LocalDate.now())) { MassTransitStopValidityPeriod.
        Future }
      else { MassTransitStopValidityPeriod.
        Current }
      case (None, Some(to)) => if (LocalDate.now().isAfter(to
      )) { MassTransitStopValidityPeriod
        .Past }
      else { MassTransitStopValidityPeriod.
        Current }
      case (Some(from), Some(to)) =>
        val interval = new Interval(from.toDateMidnight, to.toDateMidnight)
        if (interval.
          containsNow()) { MassTransitStopValidityPeriod
          .Current }
        else if (interval.
          isBeforeNow) {
          MassTransitStopValidityPeriod.Past }
        else {
          MassTransitStopValidityPeriod.Future }
      case _ => MassTransitStopValidityPeriod.Current
    }
  }

  def getNationalBusStopId = {
    nextNationalBusStopId.as[Long].first
  }

  def assetRowToProperty(assetRows: Iterable[MassTransitStopRow]): Seq[Property] = {
    assetRows.groupBy(_.property.propertyId).map { case (key, rows) =>
      val row = rows.head
      Property(
        id = key,
        publicId = row.property.publicId,
        propertyType = row.property.propertyType,
        required = row.property.propertyRequired,
        values = rows.map(assetRow =>
          PropertyValue(
            assetRow.property.propertyValue,
            propertyDisplayValueFromAssetRow(assetRow))
        ).filter(_.propertyDisplayValue.isDefined).toSeq)
    }.toSeq
  }

  private def propertyDisplayValueFromAssetRow(assetRow: MassTransitStopRow): Option[String] = {
    if (assetRow.property.publicId == "liikennointisuuntima") Some(getBearingDescription(assetRow.validityDirection, assetRow.bearing))
    else Option(assetRow.property.propertyDisplayValue)
  }

  private[oracle] def getBearingDescription(validityDirection: Int, bearing: Option[Int]): String = {
    GeometryUtils.calculateActualBearing(validityDirection, bearing).getOrElse(0) match {
      case x if 46 to 135 contains x => "Itä"
      case x if 136 to 225 contains x => "Etelä"
      case x if 226 to 315 contains x => "Länsi"
      case _ => "Pohjoinen"
    }
  }

  def updateAssetLastModified(assetId: Long, modifier: String) {
    updateAssetModified(assetId, modifier).execute
  }

  private def validPropertyUpdates(propertyWithType: Tuple3[String, Option[Long], SimpleProperty]): Boolean = {
    propertyWithType match {
      case (SingleChoice, _, property) => property.values.nonEmpty
      case _ => true
    }
  }

  private def propertyWithTypeAndId(property: SimpleProperty): Tuple3[String, Option[Long], SimpleProperty] = {
    if (AssetPropertyConfiguration.commonAssetProperties.get(property.publicId).isDefined) {
      (AssetPropertyConfiguration.commonAssetProperties(property.publicId).propertyType, None, property)
    }
    else {
      val propertyId = Q.query[String, Long](propertyIdByPublicId).apply(property.publicId).firstOption.getOrElse(throw new IllegalArgumentException("Property: " + property.publicId + " not found"))
      (Q.query[Long, String](propertyTypeByPropertyId).apply(propertyId).first, Some(propertyId), property)
    }
  }

  def updateAssetProperties(assetId: Long, properties: Seq[SimpleProperty]) {
    properties.map(propertyWithTypeAndId).filter(validPropertyUpdates).foreach { propertyWithTypeAndId =>
      if (AssetPropertyConfiguration.commonAssetProperties.get(propertyWithTypeAndId._3.publicId).isDefined) {
        updateCommonAssetProperty(assetId, propertyWithTypeAndId._3.publicId, propertyWithTypeAndId._1, propertyWithTypeAndId._3.values)
      } else {
        updateAssetSpecificProperty(assetId, propertyWithTypeAndId._3.publicId, propertyWithTypeAndId._2.get, propertyWithTypeAndId._1, propertyWithTypeAndId._3.values)
      }
    }
  }

  private def updateAssetSpecificProperty(assetId: Long, propertyPublicId: String, propertyId: Long, propertyType: String, propertyValues: Seq[PropertyValue]) {
    propertyType match {
      case Text | LongText => {
        if (propertyValues.size > 1) throw new IllegalArgumentException("Text property must have exactly one value: " + propertyValues)
        if (propertyValues.isEmpty) {
          deleteTextProperty(assetId, propertyId).execute
        } else if (textPropertyValueDoesNotExist(assetId, propertyId)) {
          insertTextProperty(assetId, propertyId, propertyValues.head.propertyValue).execute
        } else {
          updateTextProperty(assetId, propertyId, propertyValues.head.propertyValue).execute
        }
      }
      case SingleChoice => {
        if (propertyValues.size != 1) throw new IllegalArgumentException("Single choice property must have exactly one value. publicId: " + propertyPublicId)
        if (singleChoiceValueDoesNotExist(assetId, propertyId)) {
          insertSingleChoiceProperty(assetId, propertyId, propertyValues.head.propertyValue.toLong).execute
        } else {
          updateSingleChoiceProperty(assetId, propertyId, propertyValues.head.propertyValue.toLong).execute
        }
      }
      case MultipleChoice => {
        createOrUpdateMultipleChoiceProperty(propertyValues, assetId, propertyId)
      }
      case ReadOnly | ReadOnlyNumber | ReadOnlyText => {
        logger.debug("Ignoring read only property in update: " + propertyPublicId)
      }
      case t: String => throw new UnsupportedOperationException("Asset property type: " + t + " not supported")
    }
  }

  def updateTextPropertyValue(assetId: Long, propertyPublicId: String, value: String): Unit = {
    val propertyId = Q.query[String, Long](propertyIdByPublicId).apply(propertyPublicId).firstOption.getOrElse(throw new IllegalArgumentException("Property: " + propertyPublicId + " not found"))
    if (textPropertyValueDoesNotExist(assetId, propertyId)) {
      insertTextProperty(assetId, propertyId, value).execute
    } else {
      updateTextProperty(assetId, propertyId, value).execute
    }
  }

  def updateNumberPropertyValue(assetId: Long, propertyPublicId: String, value: Int): Unit = {
    val propertyId = Q.query[String, Long](propertyIdByPublicId).apply(propertyPublicId).firstOption.getOrElse(throw new IllegalArgumentException("Property: " + propertyPublicId + " not found"))
    if (numberPropertyValueDoesNotExist(assetId, propertyId)) {
      insertNumberProperty(assetId, propertyId, value).execute
    } else {
      updateNumberProperty(assetId, propertyId, value).execute
    }
  }

  def deleteNumberPropertyValue(assetId: Long, propertyPublicId: String): Unit = {
    val propertyId = Q.query[String, Long](propertyIdByPublicId).apply(propertyPublicId).firstOption.getOrElse(throw new IllegalArgumentException("Property: " + propertyPublicId + " not found"))
    deleteNumberProperty(assetId, propertyId)
  }

  private def numberPropertyValueDoesNotExist(assetId: Long, propertyId: Long) = {
    Q.query[(Long, Long), Long](existsNumberProperty).apply((assetId, propertyId)).firstOption.isEmpty
  }

  private def textPropertyValueDoesNotExist(assetId: Long, propertyId: Long) = {
    Q.query[(Long, Long), Long](existsTextProperty).apply((assetId, propertyId)).firstOption.isEmpty
  }

  private def singleChoiceValueDoesNotExist(assetId: Long, propertyId: Long) = {
    Q.query[(Long, Long), Long](existsSingleChoiceProperty).apply((assetId, propertyId)).firstOption.isEmpty
  }

  private def updateCommonAssetProperty(assetId: Long, propertyPublicId: String, propertyType: String, propertyValues: Seq[PropertyValue]) {
    val property = AssetPropertyConfiguration.commonAssetProperties(propertyPublicId)
    propertyType match {
      case SingleChoice => {
        val newVal = propertyValues.head.propertyValue.toString
        AssetPropertyConfiguration.commonAssetPropertyEnumeratedValues.find { p =>
          (p.publicId == propertyPublicId) && (p.values.map(_.propertyValue).contains(newVal))
        } match {
          case Some(propValues) => {
            updateCommonProperty(assetId, property.column, newVal, property.lrmPositionProperty).execute
          }
          case None => throw new IllegalArgumentException("Invalid property/value: " + propertyPublicId + "/" + newVal)
        }
      }
      case Text | LongText => updateCommonProperty(assetId, property.column, propertyValues.head.propertyValue).execute
      case Date => {
        val formatter = ISODateTimeFormat.dateOptionalTimeParser()
        val optionalDateTime = propertyValues.headOption match {
          case None => None
          case Some(x) if x.propertyValue.trim.isEmpty => None
          case Some(x) => Some(formatter.parseDateTime(x.propertyValue))
        }
        updateCommonDateProperty(assetId, property.column, optionalDateTime, property.lrmPositionProperty).execute
      }
      case ReadOnlyText | ReadOnlyNumber => {
        logger.debug("Ignoring read only property in update: " + propertyPublicId)
      }
      case t: String => throw new UnsupportedOperationException("Asset: " + propertyPublicId + " property type: " + t + " not supported")
    }
  }

  private[this] def createOrUpdateMultipleChoiceProperty(propertyValues: Seq[PropertyValue], assetId: Long, propertyId: Long) {
    val newValues = propertyValues.map(_.propertyValue.toLong)
    val currentIdsAndValues = Q.query[(Long, Long), (Long, Long)](multipleChoicePropertyValuesByAssetIdAndPropertyId).apply(assetId, propertyId).list
    val currentValues = currentIdsAndValues.map(_._2)
    // remove values as necessary
    currentIdsAndValues.foreach {
      case (multipleChoiceId, enumValue) =>
        if (!newValues.contains(enumValue)) {
          deleteMultipleChoiceValue(multipleChoiceId).execute
        }
    }
    // add values as necessary
    newValues.filter {
      !currentValues.contains(_)
    }.foreach {
      v =>
        insertMultipleChoiceValue(assetId, propertyId, v).execute
    }
  }

  def getMunicipalityNameByCode(code: Int): String = {
    sql"""
      select name_fi from municipality where id = $code
    """.as[String].first
  }

  def propertyDefaultValues(assetTypeId: Long): List[SimpleProperty] = {
    implicit val getDefaultValue = new GetResult[SimpleProperty] {
      def apply(r: PositionedResult) = {
        SimpleProperty(publicId = r.nextString, values = List(PropertyValue(r.nextString)))
      }
    }
    sql"""
      select p.public_id, p.default_value from asset_type a
      join property p on p.asset_type_id = a.id
      where a.id = $assetTypeId and p.default_value is not null""".as[SimpleProperty].list
  }

  def getAssetAdministrationClass(assetId: Long): Option[AdministrativeClass] = {
    val propertyValueOption = getNumberPropertyValue(assetId, "linkin_hallinnollinen_luokka")

    propertyValueOption match {
      case None => None
      case Some(propertyValue) =>
        Some(AdministrativeClass.apply(propertyValue))
    }
  }

  def getAssetFloatingReason(assetId: Long): Option[FloatingReason] = {
    val propertyValueOption = getNumberPropertyValue(assetId, "kellumisen_syy")

    propertyValueOption match {
      case None => None
      case Some(propertyValue) =>
        Some(FloatingReason.apply(propertyValue))
    }
  }

  def expireMassTransitStop(username: String, id: Long) = {
    sqlu"""
             update asset
             set valid_to = sysdate -1, modified_date = sysdate, modified_by = $username
             where id = $id
          """.execute
  }

  //TODO: Fixme. Is distinct needed?
  def getPropertyDescription(propertyPublicId : String, value: String) = {
    sql"""
       Select distinct
         case
                 when e.name_fi is not null then e.name_fi
                 when tp.value_fi is not null then tp.value_fi
                 when np.value is not null then to_char(np.value)
                 else null
               end as display_value
       From PROPERTY p left join ENUMERATED_VALUE e on e.PROPERTY_ID = p.ID left join TEXT_PROPERTY_VALUE tp on
         tp.PROPERTY_ID = p.ID left join NUMBER_PROPERTY_VALUE np on np.PROPERTY_ID = p.ID
       Where p.PUBLIC_ID = $propertyPublicId And e.value = $value
      """.as[String].list
  }

  def deleteAllMassTransitStopData(assetId: Long): Unit ={
    sqlu"""Delete From Single_Choice_Value Where asset_id in (Select id as asset_id From asset Where id = $assetId)""".execute
    sqlu"""Delete From Multiple_Choice_Value Where asset_id in (Select id as asset_id From asset Where id = $assetId)""".execute
    sqlu"""Delete From Text_Property_Value Where asset_id in (Select id as asset_id From asset Where id = $assetId)""".execute
    sqlu"""Delete From Asset_Link Where asset_id in (Select id as asset_id From asset Where id = $assetId)""".execute
    sqlu"""Delete From Number_Property_Value Where asset_id in (Select id as asset_id From asset Where id = $assetId)""".execute
    sqlu"""Delete From Terminal_Bus_Stop_Link where terminal_asset_id = $assetId or bus_stop_asset_id = $assetId""".execute
    sqlu"""Delete From Asset Where id = $assetId""".execute
  }

  def updateLrmPosition(id: Long, mValue: Double, linkId: Long, linkSource: LinkGeomSource, adjustedTimeStampOption: Option[Long] = None) {
    adjustedTimeStampOption match {
      case Some(adjustedTimeStamp) =>
        sqlu"""
           update lrm_position
            set start_measure = $mValue,
            end_measure = $mValue,
            link_id = $linkId,
            link_source = ${linkSource.value},
            adjusted_timestamp = ${adjustedTimeStamp}
           where id = (
            select lrm.id
            from asset a
            join asset_link al on al.asset_id = a.id
            join lrm_position lrm on lrm.id = al.position_id
            where a.id = $id)
      """.execute
      case _ =>
        sqlu"""
           update lrm_position
           set start_measure = $mValue, end_measure = $mValue, link_id = $linkId, link_source = ${linkSource.value}
           where id = (
            select lrm.id
            from asset a
            join asset_link al on al.asset_id = a.id
            join lrm_position lrm on lrm.id = al.position_id
            where a.id = $id)
      """.execute
    }
  }

  def insertLrmPosition(id: Long, mValue: Double, linkId: Long, linkSource: LinkGeomSource) {
    sqlu"""
           insert into lrm_position (id, start_measure, end_measure, link_id, link_source)
           values ($id, $mValue, $mValue, $linkId, ${linkSource.value})
      """.execute
  }

  def insertLrmPosition(id: Long, mValue: Double, linkId: Long, linkSource: LinkGeomSource, sideCode: SideCode) {
    sqlu"""
           insert into lrm_position (id, start_measure, end_measure, link_id, link_source, side_code)
           values ($id, $mValue, $mValue, $linkId, ${linkSource.value}, ${sideCode.value})
      """.execute
  }

  def insertAsset(id: Long, nationalId: Long, lon: Double, lat: Double, bearing: Int, creator: String, municipalityCode: Int, floating: Boolean): Unit = {
    val typeId = 10
    sqlu"""
           insert into asset (id, external_id, asset_type_id, bearing, created_by, municipality_code, geometry, floating)
           values ($id, $nationalId, $typeId, $bearing, $creator, $municipalityCode,
           MDSYS.SDO_GEOMETRY(4401, 3067, NULL, MDSYS.SDO_ELEM_INFO_ARRAY(1,1,1), MDSYS.SDO_ORDINATE_ARRAY($lon, $lat, 0, 0)),
           $floating)
      """.execute
  }

  def insertAsset(id: Long, nationalId: Long, lon: Double, lat: Double, creator: String, municipalityCode: Int, floating: Boolean): Unit = {
    val typeId = 10
    sqlu"""
           insert into asset (id, external_id, asset_type_id, created_by, municipality_code, geometry, floating)
           values ($id, $nationalId, $typeId, $creator, $municipalityCode,
           MDSYS.SDO_GEOMETRY(4401, 3067, NULL, MDSYS.SDO_ELEM_INFO_ARRAY(1,1,1), MDSYS.SDO_ORDINATE_ARRAY($lon, $lat, 0, 0)),
           $floating)
      """.execute
  }

  def insertChildren(terminalAssetId: Long, massTransitStopAssetId: Seq[Long]): Unit = {
    massTransitStopAssetId.foreach{ id =>
        sqlu"""
           insert into TERMINAL_BUS_STOP_LINK (TERMINAL_ASSET_ID, BUS_STOP_ASSET_ID) values ($terminalAssetId, $id)
        """.execute
    }
  }

  def deleteChildren(terminalAssetId: Long): Unit = {
    sqlu"""delete from TERMINAL_BUS_STOP_LINK
           where TERMINAL_ASSET_ID = $terminalAssetId
           """.execute
  }

  def insertTerminal(assetId: Long): Unit = {
    sqlu"""INSERT INTO TERMINAL_BUS_STOP_LINK (TERMINAL_ASSET_ID) VALUES ($assetId)""".execute
  }

  def deleteTerminalMassTransitStopData(assetId: Long): Unit ={
    sqlu"""Delete From Text_Property_Value Where asset_id in (Select id as asset_id From asset Where id = $assetId)""".execute
    sqlu"""Delete From Multiple_Choice_Value Where asset_id in (Select id as asset_id From asset Where id = $assetId)""".execute
    sqlu"""Delete From Number_Property_Value Where asset_id in (Select id as asset_id From asset Where id = $assetId)""".execute
    sqlu"""Delete From Asset_Link Where asset_id in (Select id as asset_id From asset Where id = $assetId)""".execute
    sqlu"""Delete from TERMINAL_BUS_STOP_LINK where TERMINAL_ASSET_ID in (Select id as asset_id From asset Where id = $assetId)""".execute
    sqlu"""Delete From Asset Where id = $assetId""".execute
  }

  def withFilter(filter: String)(query: String): String = {
    query + " " + filter
  }

  def insertAssetLink(assetId: Long, lrmPositionId: Long): Unit = {

    sqlu"""
           insert into asset_link(asset_id, position_id)
           values ($assetId, $lrmPositionId)
      """.execute
  }

  def updateBearing(id: Long, position: Position) {
    position.bearing.foreach { bearing =>
      sqlu"""
           update asset
           set bearing = $bearing
           where id = $id
        """.execute
    }
  }

  def updateMunicipality(id: Long, municipalityCode: Int) {
    sqlu"""
           update asset
           set municipality_code = $municipalityCode
           where id = $id
      """.execute
  }

  def getNationalIdByLiviId(liviId: String): Seq[Long] = {
    sql"""
      select a.external_id
      from text_property_value tp
      join asset a on a.id = tp.asset_id
      where tp.property_id = (select p.id from property p where p.public_id = 'yllapitajan_koodi')
      and tp.value_fi = $liviId""".as[Long].list
  }

  def withId(id: Long)(query: String): String = {
    query + s" where a.id = $id"
  }

  def withTerminalId(terminalId: Long)(query: String): String = {
    query + s" where terminal_asset_id = $terminalId and (a.valid_to is null or a.valid_to > sysdate)"
  }

  def withNationalId(nationalId: Long)(query: String): String = {
    query + s" where a.external_id = $nationalId"
  }

  def countTerminalChildBusStops(assetId: Long): Int = {
    sql"""
        select count(*)
        from asset a
          left join terminal_bus_stop_link tbs on tbs.bus_stop_asset_id = a.id
        where a.asset_type_id = 10 and (a.valid_to is null or a.valid_to > sysdate) and tbs.terminal_asset_id = $assetId
      """.as[Int].first
  }
  def getPropertiesWithMaxSize(assetTypeId: Long): Map[String, Int] = {
    sql"""select public_id, max_value_length from property where asset_type_id = $assetTypeId and max_value_length is not null""".as[(String, Int)].iterator.toMap
  }

  def fetchTerminalFloatingAssets(addQueryFilter: String => String, isOperator: Option[Boolean]): Seq[(Long, Long)] ={
    val query = s"""select a.$idField, lrm.link_id
          from asset a
          join asset_link al on a.id = al.asset_id
          join lrm_position lrm on al.position_id = lrm.id
          join property p on a.asset_type_id = p.asset_type_id and p.public_id = 'pysakin_tyyppi'
          left join number_property_value np on np.asset_id = a.id and np.property_id = p.id and p.property_type = 'read_only_number'
          left join multiple_choice_value mc on mc.asset_id = a.id and mc.property_id = p.id and p.property_type = 'multiple_choice'
          left join enumerated_value e on mc.enumerated_value_id = e.id
          where a.asset_type_id = $typeId and a.floating = '1' and (a.valid_to is null or a.valid_to > sysdate) and e.value = 6"""

    val queryFilter = isOperator match {
      case Some(false) =>
        (q: String) => {
          addQueryFilter(q + s""" and np.value <> ${FloatingReason.RoadOwnerChanged.value}""")
        }
      case _ =>
        addQueryFilter
    }

    Q.queryNA[(Long, Long)](queryFilter(query)).list
  }


}
