package fi.liikennevirasto.digiroad2.dao


import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.PropertyTypes._
import fi.liikennevirasto.digiroad2.asset.{MassTransitStopValidityPeriod, _}
import fi.liikennevirasto.digiroad2.dao.Queries._
import fi.liikennevirasto.digiroad2.model.PointAssetLRMPosition
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop.{LightGeometryMassTransitStop, MassTransitStopOperations, MassTransitStopRow, PersistedMassTransitStop}
import org.joda.time.format.{DateTimeFormat, ISODateTimeFormat}
import org.joda.time.{DateTime, Interval, LocalDate}
import org.slf4j.LoggerFactory
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedParameters, PositionedResult, SetParameter, StaticQuery => Q}

import scala.language.reflectiveCalls


class MassTransitStopDao {
  val logger = LoggerFactory.getLogger(getClass)
  def typeId: Int = 10
  val idField = "national_id"

  implicit val SetStringSeq: SetParameter[IndexedSeq[Any]] = new SetParameter[IndexedSeq[Any]] {
    def apply(seq: IndexedSeq[Any], p: PositionedParameters): Unit = {
      for (i <- 1 to seq.length) {
        p.ps.setObject(i, seq(i - 1))
      }
    }
  }

  def queryFetchPointAssets() : String = {
    """ select a.id, a.national_id, a.asset_type_id, a.bearing, pos.side_code,
        a.valid_from, a.valid_to, geometry, a.municipality_code, a.floating,
        pos.adjusted_timestamp, p.id as p_id, p.public_id, p.property_type, p.required, p.max_value_length, e.value,
        case
          when e.name_fi is not null then e.name_fi
          when tp.value_fi is not null then tp.value_fi
          when np.value is not null then cast(np.value as text)
          else null
        end as display_value,
        pos.id as lrm_id, pos.start_measure, pos.link_id,
        a.created_date, a.created_by, a.modified_date, a.modified_by,
        ST_Transform(a.geometry, 4326) AS position_wgs84, pos.link_source,
        tbs.terminal_asset_id as terminal_asset_id
        from asset a
          join asset_link al on a.id = al.asset_id
          join lrm_position pos on al.position_id = pos.id
          join property p on a.asset_type_id = p.asset_type_id
          left join terminal_bus_stop_link tbs on tbs.bus_stop_asset_id = a.id
          left join single_choice_value s on s.asset_id = a.id and s.property_id = p.id and p.property_type = 'single_choice'
          left join text_property_value tp on tp.asset_id = a.id and tp.property_id = p.id and (p.property_type = 'text' or p.property_type = 'long_text' or p.property_type = 'read_only_text')
          left join multiple_choice_value mc on mc.asset_id = a.id and mc.property_id = p.id and (p.property_type = 'multiple_choice' or p.property_type = 'checkbox')
          left join number_property_value np on np.asset_id = a.id and np.property_id = p.id and p.property_type = 'read_only_number'
          left join enumerated_value e on mc.enumerated_value_id = e.id or s.enumerated_value_id = e.id """
  }


  def fetchPointAssetsWithExpiredLimited(queryFilter: String => String, token: Option[String]): Seq[PersistedMassTransitStop] = {
    val query = queryFetchPointAssets()

    val recordLimit = token match {
      case Some(tk) =>
        val (startNum, endNum) = Decode.getPageAndRecordNumber(tk)
        val counter = ", DENSE_RANK() over (ORDER BY a.id) line_number from asset a "

        s"select id, national_id, asset_type_id, bearing, side_code, valid_from, valid_to, geometry, municipality_code, floating, "+
        s" adjusted_timestamp, p_id, public_id, property_type, required, max_value_length, value, display_value, lrm_id, start_measure, "+
        s" link_id, created_date, created_by, modified_date, modified_by, position_wgs84, link_source, terminal_asset_id "+
        s" from ( ${queryFilter(query.replace("from asset a", counter))} ) derivedAsset WHERE line_number between $startNum and $endNum "

      case _ => queryFilter(queryFetchPointAssets())
    }

    queryToPersistedMassTransitStops(recordLimit)
  }


  def fetchPointAssets(queryFilter: String => String): Seq[PersistedMassTransitStop] = {
    val query = queryFetchPointAssets()

    queryToPersistedMassTransitStops(queryFilter(query))
  }

  def fetchLightGeometry(queryFilter: String => String): Seq[LightGeometryMassTransitStop] = {
    val query =
      """
        select a.geometry, a.valid_from, a.valid_to
        from asset a
          join asset_link al on a.id = al.asset_id
      """

    val resultList = Q.queryNA[(Point, Option[LocalDate], Option[LocalDate])](queryFilter(query)).list

    resultList.map { case (point, validFrom, validTo) =>
      val validityPeriod = Some(constructValidityPeriod(validFrom, validTo))
      LightGeometryMassTransitStop(point.x, point.y, validityPeriod)
    }
  }

  def fetchByRadius(position : Point, meters: Int, terminalIdOption: Option[Long] = None): Seq[PersistedMassTransitStop] = {
    val topLeft = Point(position.x - meters, position.y - meters)
    val bottomRight = Point(position.x + meters, position.y + meters)
    val boundingBoxFilter = PostGISDatabase.boundingBoxFilter(BoundingRectangle(topLeft, bottomRight), "a.geometry")
    val filter = s"where a.asset_type_id = $typeId and (($boundingBoxFilter ) and (a.valid_to is null or a.valid_to > current_timestamp))"
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
    val rows = Q.queryNA[MassTransitStopRow](query)(getMassTransitStopRow).iterator.toSeq

    rows.groupBy(_.id).map { case (id, stopRows) =>
      val row = stopRows.head
      val commonProperties: Seq[Property] = AssetPropertyConfiguration.assetRowToCommonProperties(row)
      val properties: Seq[Property] = commonProperties ++ assetRowToProperty(stopRows)
      val point = row.point.get
      val validityPeriod = Some(constructValidityPeriod(row.validFrom, row.validTo))
      val stopTypes = extractStopTypes(stopRows)
      val mValue = row.lrmPosition.startMeasure
      val timeStamp = row.lrmPosition.timeStamp
      val linkSource = row.lrmPosition.linkSource

      id -> PersistedMassTransitStop(id = row.id, nationalId = row.nationalId, linkId = row.linkId, stopTypes = stopTypes,
        municipalityCode = row.municipalityCode, lon = point.x, lat = point.y, mValue = mValue,
        validityDirection = Some(row.validityDirection), bearing = row.bearing,
        validityPeriod = validityPeriod, floating = row.persistedFloating, timeStamp = timeStamp, created = row.created, modified = row.modified,
        propertyData = properties, linkSource = LinkGeomSource(linkSource), terminalId = row.terminalId)
    }.values.toSeq
  }

  private implicit val getLocalDate = new GetResult[Option[LocalDate]] {
    def apply(r: PositionedResult) = {
      r.nextDateOption().map(new LocalDate(_))
    }
  }

  private implicit val getMassTransitStopRow = new GetResult[MassTransitStopRow] {
    def apply(r: PositionedResult) : MassTransitStopRow = {
      val id = r.nextLong
      val nationalId = r.nextLong
      val assetTypeId = r.nextLong
      val bearing = r.nextIntOption
      val validityDirection = r.nextInt
      val validFrom = r.nextDateOption.map(new LocalDate(_))
      val validTo = r.nextDateOption.map(new LocalDate(_))
      val point = r.nextObjectOption().map(objectToPoint)
      val municipalityCode = r.nextInt()
      val persistedFloating = r.nextBoolean()
      val timeStamp = r.nextLong()
      val propertyId = r.nextLong
      val propertyPublicId = r.nextString
      val propertyType = r.nextString
      val propertyRequired = r.nextBoolean
      val propertyMaxCharacters = r.nextIntOption()
      val propertyValue = r.nextLongOption()
      val propertyDisplayValue = r.nextStringOption()
      val property = new PropertyRow(
        propertyId = propertyId,
        publicId = propertyPublicId,
        propertyType = propertyType,
        propertyRequired = propertyRequired,
        propertyValue = propertyValue.getOrElse(propertyDisplayValue.getOrElse("")).toString,
        propertyDisplayValue = propertyDisplayValue.orNull,
        propertyMaxCharacters = propertyMaxCharacters)
      val lrmId = r.nextLong
      val startMeasure = r.nextDouble()
      val linkId = r.nextString()
      val created = new Modification(r.nextTimestampOption().map(new DateTime(_)), r.nextStringOption)
      val modified = new Modification(r.nextTimestampOption().map(new DateTime(_)), r.nextStringOption)
      val wgsPoint = r.nextObjectOption().map(objectToPoint)
      val linkSource = r.nextInt
      val terminalId = r.nextLongOption
      MassTransitStopRow(id, nationalId, assetTypeId, point, linkId, bearing, validityDirection,
        validFrom, validTo, property, created, modified, wgsPoint,
        lrmPosition = PointAssetLRMPosition(lrmId, startMeasure, point, timeStamp, linkSource),
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
        ).filter(_.propertyDisplayValue.isDefined).toSeq,
        numCharacterMax = row.property.propertyMaxCharacters)
    }.toSeq
  }

  private def propertyDisplayValueFromAssetRow(assetRow: MassTransitStopRow): Option[String] = {
    if (assetRow.property.publicId == "liikennointisuuntima") Some(getBearingDescription(assetRow.validityDirection, assetRow.bearing))
    else Option(assetRow.property.propertyDisplayValue)
  }

  private[dao] def getBearingDescription(validityDirection: Int, bearing: Option[Int]): String = {
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

  private def validPropertyUpdates(propertyWithType: Tuple3[String, Option[Long], SimplePointAssetProperty]): Boolean = {
    propertyWithType match {
      case (SingleChoice, _, property) => property.values.nonEmpty
      case _ => true
    }
  }

  private def propertyWithTypeAndId(property: SimplePointAssetProperty): Tuple3[String, Option[Long], SimplePointAssetProperty] = {
    if (AssetPropertyConfiguration.commonAssetProperties.get(property.publicId).isDefined) {
      (AssetPropertyConfiguration.commonAssetProperties(property.publicId).propertyType, None, property)
    }
    else {
      val propertyId = Q.query[(String, Int), Long](propertyIdByPublicIdAndTypeId).apply(property.publicId, MassTransitStopAsset.typeId).firstOption.getOrElse(throw new IllegalArgumentException("Property: " + property.publicId + " not found"))
      (Q.query[Long, String](propertyTypeByPropertyId).apply(propertyId).first, Some(propertyId), property)
    }
  }

  def updateAssetProperties(assetId: Long, properties: Seq[SimplePointAssetProperty]) {
    properties.map(propertyWithTypeAndId).filter(validPropertyUpdates).foreach { propertyWithTypeAndId =>
      if (AssetPropertyConfiguration.commonAssetProperties.get(propertyWithTypeAndId._3.publicId).isDefined) {
        updateCommonAssetProperty(assetId, propertyWithTypeAndId._3.publicId, propertyWithTypeAndId._1, propertyWithTypeAndId._3.values.map(_.asInstanceOf[PropertyValue]))
      } else {
        updateAssetSpecificProperty(assetId, propertyWithTypeAndId._3.publicId, propertyWithTypeAndId._2.get, propertyWithTypeAndId._1, propertyWithTypeAndId._3.values.map(_.asInstanceOf[PropertyValue]))
      }
    }
  }

  def multipleChoiceValueDoesNotExist(assetId: Long, propertyId: Long): Boolean = {
    Q.query[(Long, Long), Long](existsMultipleChoiceProperty).apply((assetId, propertyId)).firstOption.isEmpty
  }

  private def updateAssetSpecificProperty(assetId: Long, propertyPublicId: String, propertyId: Long, propertyType: String, propertyValues: Seq[PropertyValue]) {
    propertyType match {
      case Text | LongText => {
        if (propertyValues.size > 1) {
          throw new IllegalArgumentException(s"Text property must have exactly one value: $propertyValues")
        }
        if (propertyValues.nonEmpty) {
          val propertyValue = propertyValues.head.propertyValue
          if (propertyValue.equals("-") || propertyValues.head.propertyValue.equals("")) {
            deleteTextProperty(assetId, propertyId).execute
          } else if (propertyPublicId.equals("inventointipaiva")) {
            val formattedDate = finnishToIso8601(propertyValue)
            if (textPropertyValueDoesNotExist(assetId, propertyId)) {
              insertTextProperty(assetId, propertyId, formattedDate).execute
            } else {
              updateTextProperty(assetId, propertyId, formattedDate).execute
            }
          } else {
            if (textPropertyValueDoesNotExist(assetId, propertyId)) {
              insertTextProperty(assetId, propertyId, propertyValue).execute
            } else {
              updateTextProperty(assetId, propertyId, propertyValue).execute
            }
          }
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
      case MultipleChoice | CheckBox => {
        createOrUpdateMultipleChoiceProperty(propertyValues, assetId, propertyId)
      }
      case ReadOnly | ReadOnlyNumber | ReadOnlyText => {
        logger.debug("Ignoring read only property in update: " + propertyPublicId)
      }
      case t: String => throw new UnsupportedOperationException("Asset property type: " + t + " not supported")
    }
  }

  private def finnishToIso8601(propertyValue: String): String = {
    val iso8601Formatter = DateTimeFormat.forPattern("yyyy-MM-dd")
    val finnishFormatter = DateTimeFormat.forPattern("dd.MM.yyyy")

    try iso8601Formatter.parseDateTime(propertyValue).toString("yyyy-MM-dd")
    catch {
      case _: Exception => finnishFormatter.parseDateTime(propertyValue).toString("yyyy-MM-dd")
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
    deleteNumberProperty(assetId, propertyId).execute
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
          (p.publicId == propertyPublicId) && p.values.map(_.asInstanceOf[PropertyValue].propertyValue).contains(newVal)
        } match {
          case Some(propValues) =>
            updateCommonProperty(assetId, property.column, newVal, property.lrmPositionProperty).execute
          case None => throw new IllegalArgumentException("Invalid property/value: " + propertyPublicId + "/" + newVal)
        }
      }
      case Text | LongText => updateCommonProperty(assetId, property.column, propertyValues.head.propertyValue).execute
      case Date => {
        val formatter = ISODateTimeFormat.dateOptionalTimeParser()
        val optionalDateTime = propertyValues.headOption match {
          case None => None
          case Some(x) if x.propertyValue.trim.isEmpty => None
          case Some(x) => try {
            val isoDateString = finnishToIso8601(x.propertyValue)
            val dateTime = DateTime.parse(isoDateString, formatter)
            Some(dateTime)
          } catch {
            case _: Exception => None
          }
        }
        updateCommonDateProperty(assetId, property.column, optionalDateTime, property.lrmPositionProperty).execute
      }
      case ReadOnlyText | ReadOnlyNumber => {
        logger.debug("Ignoring read only property in update: " + propertyPublicId)
      }
      case t: String => throw new UnsupportedOperationException("Asset: " + propertyPublicId + " property type: " + t + " not supported")
    }
  }

  protected def createOrUpdateMultipleChoiceProperty(propertyValues: Seq[PropertyValue], assetId: Long, propertyId: Long) {
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

  def propertyDefaultValues(assetTypeId: Long): List[SimplePointAssetProperty] = {
    implicit val getDefaultValue = new GetResult[SimplePointAssetProperty] {
      def apply(r: PositionedResult) = {
        SimplePointAssetProperty(publicId = r.nextString, values = List(PropertyValue(r.nextString)))
      }
    }
    sql"""
      select p.public_id, p.default_value from asset_type a
      join property p on p.asset_type_id = a.id
      where a.id = $assetTypeId and p.default_value is not null""".as[SimplePointAssetProperty].list
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
             set valid_to = current_timestamp -INTERVAL'1 DAYS', modified_date = current_timestamp, modified_by = $username
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
                 when np.value is not null then cast(np.value as text)
                 else null
               end as display_value
       From PROPERTY p left join ENUMERATED_VALUE e on e.PROPERTY_ID = p.ID left join TEXT_PROPERTY_VALUE tp on
         tp.PROPERTY_ID = p.ID left join NUMBER_PROPERTY_VALUE np on np.PROPERTY_ID = p.ID
       Where p.PUBLIC_ID = $propertyPublicId And e.value = cast($value as numeric)
      """.as[String].list
  }

  def deleteAllMassTransitStopData(assetId: Long): Unit ={
    sqlu"""Delete From Single_Choice_Value Where asset_id in (Select id as asset_id From asset Where id = $assetId)""".execute
    sqlu"""Delete From Multiple_Choice_Value Where asset_id in (Select id as asset_id From asset Where id = $assetId)""".execute
    sqlu"""Delete From Text_Property_Value Where asset_id in (Select id as asset_id From asset Where id = $assetId)""".execute
    sqlu"""Delete From Asset_Link Where asset_id in (Select id as asset_id From asset Where id = $assetId)""".execute
    sqlu"""Delete From Number_Property_Value Where asset_id in (Select id as asset_id From asset Where id = $assetId)""".execute
    sqlu"""Delete From Terminal_Bus_Stop_Link where terminal_asset_id = $assetId or bus_stop_asset_id = $assetId""".execute
    sqlu"""Delete From Vallu_Xml_Ids where asset_id in (Select id as asset_id From asset Where id = $assetId)""".execute
    sqlu"""Delete From municipality_asset_id_mapping where asset_id in (Select id as asset_id From asset Where id = $assetId)""".execute
    sqlu"""Delete From Asset Where id = $assetId""".execute
  }

  def updateLrmPosition(id: Long, mValue: Double, linkId: String, linkSource: LinkGeomSource, adjustedTimeStampOption: Option[Long] = None) {
    adjustedTimeStampOption match {
      case Some(adjustedTimeStamp) =>
        sqlu"""
           update lrm_position
            set start_measure = $mValue,
            link_id = $linkId,
            link_source = ${linkSource.value},
            modified_date = current_timestamp,
            adjusted_timestamp = ${adjustedTimeStamp}
           where id = (
            select pos.id
            from asset a
            join asset_link al on al.asset_id = a.id
            join lrm_position pos on pos.id = al.position_id
            where a.id = $id)
      """.execute
      case _ =>
        sqlu"""
           update lrm_position
           set start_measure = $mValue, link_id = $linkId, modified_date = current_timestamp, link_source = ${linkSource.value}
           where id = (
            select pos.id
            from asset a
            join asset_link al on al.asset_id = a.id
            join lrm_position pos on pos.id = al.position_id
            where a.id = $id)
      """.execute
    }
  }

  def insertLrmPosition(id: Long, mValue: Double, linkId: String, linkSource: LinkGeomSource) {
    sqlu"""
           insert into lrm_position (id, start_measure, link_id, link_source)
           values ($id, $mValue, $linkId, ${linkSource.value})
      """.execute
  }

  def insertLrmPosition(id: Long, mValue: Double, linkId: String, linkSource: LinkGeomSource, sideCode: SideCode) {
    sqlu"""
           insert into lrm_position (id, start_measure, link_id, link_source, side_code)
           values ($id, $mValue, $linkId, ${linkSource.value}, ${sideCode.value})
      """.execute
  }

  def insertAsset(id: Long, nationalId: Long, lon: Double, lat: Double, bearing: Int, creator: String, municipalityCode: Int, floating: Boolean): Unit = {
    val typeId = 10
    val pointGeometry =Queries.pointGeometry(lon,lat)
    sqlu"""
           insert into asset (id, national_id, asset_type_id, bearing, created_by, municipality_code, geometry, floating)
           values ($id, $nationalId, $typeId, $bearing, $creator, $municipalityCode,
          ST_GeomFromText($pointGeometry,3067),
           $floating)
      """.execute
  }

  def insertAsset(id: Long, nationalId: Long, lon: Double, lat: Double, creator: String, municipalityCode: Int, floating: Boolean): Unit = {
    val typeId = 10
    val pointGeometry =Queries.pointGeometry(lon,lat)
    sqlu"""
           insert into asset (id, national_id, asset_type_id, created_by, municipality_code, geometry, floating)
           values ($id, $nationalId, $typeId, $creator, $municipalityCode,
           ST_GeomFromText($pointGeometry,3067),
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

  def getAllChildren(terminalAssetId: Long): Seq[Long] = {
    sql"""
          SELECT BUS_STOP_ASSET_ID from TERMINAL_BUS_STOP_LINK
           where TERMINAL_ASSET_ID = $terminalAssetId
      """.as[Long].list
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
      select a.national_id
      from text_property_value tp
      join asset a on a.id = tp.asset_id
      where tp.property_id = (select p.id from property p where p.public_id = 'yllapitajan_koodi')
      and tp.value_fi = $liviId""".as[Long].list
  }

  def getNationalIdsByPassengerId(passengerId: String): Seq[Long] = {
    sql"""
      select a.national_id
      from text_property_value tp
      join asset a on a.id = tp.asset_id
      where tp.property_id = (select p.id from property p where p.public_id = 'matkustajatunnus')
      and UPPER(tp.value_fi) = UPPER($passengerId)""".as[Long].list
  }

  def withId(id: Long)(query: String): String = {
    query + s" where a.id = $id"
  }
  def withIds(ids: Seq[Long])(query: String): String = {
    query + s" where a.id in (${ids.mkString(",")})"
  }

  def withTerminalId(terminalId: Long)(query: String): String = {
    query + s" where terminal_asset_id = $terminalId and (a.valid_to is null or a.valid_to > current_timestamp)"
  }

  def withNationalId(nationalId: Long)(query: String): String = {
    query + s" where a.national_id = $nationalId"
  }

  def withNationalIds(nationalIds: Seq[Long])(query: String): String = {
    query + s" where a.national_id in (${nationalIds.mkString(",")})"
  }

  def countTerminalChildBusStops(assetId: Long): Int = {
    sql"""
        select count(*)
        from asset a
          left join terminal_bus_stop_link tbs on tbs.bus_stop_asset_id = a.id
        where a.asset_type_id = 10 and (a.valid_to is null or a.valid_to > current_timestamp) and tbs.terminal_asset_id = $assetId
      """.as[Int].first
  }
  def getPropertiesWithMaxSize(assetTypeId: Long): Map[String, Int] = {
    sql"""select public_id, max_value_length from property where asset_type_id = $assetTypeId and max_value_length is not null""".as[(String, Int)].iterator.toMap
  }

  def fetchTerminalFloatingAssets(addQueryFilter: String => String, isOperator: Option[Boolean]): Seq[(Long, String)] ={
    val query = s"""select a.$idField, pos.link_id
          from asset a
          join asset_link al on a.id = al.asset_id
          join lrm_position pos on al.position_id = pos.id
          join property p on a.asset_type_id = p.asset_type_id and p.public_id = 'pysakin_tyyppi'
          left join number_property_value np on np.asset_id = a.id and np.property_id = p.id and p.property_type = 'read_only_number'
          left join multiple_choice_value mc on mc.asset_id = a.id and mc.property_id = p.id and p.property_type = 'multiple_choice'
          left join enumerated_value e on mc.enumerated_value_id = e.id
          where a.asset_type_id = $typeId and a.floating = '1' and (a.valid_to is null or a.valid_to > current_timestamp) and e.value = 6"""

    val queryFilter = isOperator match {
      case Some(false) =>
        (q: String) => {
          addQueryFilter(q + s""" and np.value <> ${FloatingReason.RoadOwnerChanged.value}""")
        }
      case _ =>
        addQueryFilter
    }

    Q.queryNA[(Long, String)](queryFilter(query)).list
  }

  def insertValluXmlIds(assetId: Long): Unit = {
    sqlu"""
           insert into vallu_xml_ids(id, asset_id)
           values (nextval('primary_key_seq'), $assetId)
      """.execute
  }

}
