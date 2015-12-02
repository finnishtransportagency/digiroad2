package fi.liikennevirasto.digiroad2.asset.oracle

import java.sql.SQLException

import _root_.oracle.spatial.geometry.JGeometry

import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.{Point, RoadLinkService}
import fi.liikennevirasto.digiroad2.asset.PropertyTypes._
import fi.liikennevirasto.digiroad2.asset.{ValidityPeriod, _}
import fi.liikennevirasto.digiroad2.asset.oracle.Queries._
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase._
import fi.liikennevirasto.digiroad2.user.User
import org.joda.time.{DateTime, Interval, LocalDate}
import org.joda.time.format.ISODateTimeFormat
import org.slf4j.LoggerFactory
import scala.language.reflectiveCalls
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedParameters, PositionedResult, SetParameter, StaticQuery => Q}

class OracleSpatialAssetDao(roadLinkService: RoadLinkService) {
  val logger = LoggerFactory.getLogger(getClass)

  implicit val SetStringSeq: SetParameter[IndexedSeq[Any]] = new SetParameter[IndexedSeq[Any]] {
    def apply(seq: IndexedSeq[Any], p: PositionedParameters): Unit = {
      for (i <- 1 to seq.length) {
        p.ps.setObject(i, seq(i - 1))
      }
    }
  }

  def getNationalBusStopId = {
    nextNationalBusStopId.as[Long].first
  }

  def assetRowToProperty(assetRows: Iterable[IAssetRow]): Seq[Property] = {
    assetRows.groupBy(_.property.propertyId).map { case (key, assetRows) =>
      val row = assetRows.head
      Property(key, row.property.publicId, row.property.propertyType, row.property.propertyUiIndex, row.property.propertyRequired,
        assetRows.map(assetRow =>
          PropertyValue(
            assetRow.property.propertyValue,
            propertyDisplayValueFromAssetRow(assetRow))
        ).filter(_.propertyDisplayValue.isDefined).toSeq)
    }.toSeq
  }

  private def propertyDisplayValueFromAssetRow(assetRow: IAssetRow): Option[String] = {
    if (assetRow.property.publicId == "liikennointisuuntima") Some(getBearingDescription(assetRow.validityDirection, assetRow.bearing))
    else if (assetRow.property.propertyDisplayValue != null) Some(assetRow.property.propertyDisplayValue)
    else None
  }

  private def getOptionalProductionRoadLink(row: {val productionRoadLinkId: Option[Long]; val roadLinkId: Long; val lrmPosition: LRMPosition}): Option[(Long, Int, Option[Point], AdministrativeClass)] = {
    val productionRoadLinkId: Option[Long] = row.productionRoadLinkId
    productionRoadLinkId.map { roadLinkId =>
      roadLinkService.getByIdAndMeasure(roadLinkId, row.lrmPosition.startMeasure)
    }.getOrElse(roadLinkService.getByTestIdAndMeasure(row.roadLinkId, row.lrmPosition.startMeasure))
  }

  private def extractStopTypes(rows: Seq[PropertyRow]): Seq[Int] = {
    rows
      .filter { row => row.publicId.equals("pysakin_tyyppi") }
      .filterNot { row => row.propertyValue.isEmpty }
      .map { row => row.propertyValue.toInt }
  }

  private[this] def singleAssetRowToAssetWithProperties(param: (Long, List[SingleAssetRow])): (AssetWithProperties, Boolean) = {
    val row = param._2.head
    val point = row.point.get
    val wgsPoint = row.wgsPoint.get
    val roadLinkOption = getOptionalProductionRoadLink(row)
    val floating = isFloating(row, roadLinkOption)
    (AssetWithProperties(
        id = row.id, nationalId = row.externalId, assetTypeId = row.assetTypeId,
        lon = point.x, lat = point.y,
        propertyData = (AssetPropertyConfiguration.assetRowToCommonProperties(row) ++ assetRowToProperty(param._2)).sortBy(_.propertyUiIndex),
        bearing = row.bearing, municipalityNumber = row.municipalityCode,
        validityPeriod = validityPeriod(row.validFrom, row.validTo),
        validityDirection = Some(row.validityDirection), wgslon = wgsPoint.x, wgslat = wgsPoint.y,
        created = row.created, modified = row.modified, roadLinkType = roadLinkOption.map(_._4).getOrElse(Unknown),
        stopTypes = extractStopTypes(param._2.map(_.property)),
        floating = floating), row.persistedFloating)
  }

  private[this] def calculateActualBearing(validityDirection: Int, bearing: Option[Int]): Option[Int] = {
    if (validityDirection != 3) {
      bearing
    } else {
      bearing.map(_  - 180).map(x => if(x < 0) x + 360 else x)
    }
  }

  private[oracle] def getBearingDescription(validityDirection: Int, bearing: Option[Int]): String = {
    calculateActualBearing(validityDirection, bearing).getOrElse(0) match {
      case x if 46 to 135 contains x => "Itä"
      case x if 136 to 225 contains x => "Etelä"
      case x if 226 to 315 contains x => "Länsi"
      case _ => "Pohjoinen"
    }
  }

  private def updateAssetFloatingStatus(assetWithFloating: ({val id: Long; val floating: Boolean;}, Boolean)) = {
    val (asset, persistedFloating) = assetWithFloating
    if (persistedFloating != asset.floating) {
      sqlu"""update asset set floating = ${asset.floating} where id = ${asset.id}""".execute
    }
  }

  def getAssetById(assetId: Long): Option[AssetWithProperties] = {
    val assetWithProperties = Q.query[Long, SingleAssetRow](assetWithPositionById).apply(assetId).list.groupBy(_.id).map(singleAssetRowToAssetWithProperties).headOption
    assetWithProperties.map(updateAssetFloatingStatus)
    assetWithProperties.map(_._1)
  }

  private val FLOAT_THRESHOLD_IN_METERS = 3

  private def coordinatesWithinThreshold(pt1: Point, pt2: Point): Boolean = {
    pt1.distanceTo(pt2) <= FLOAT_THRESHOLD_IN_METERS
  }

  def isFloating(asset: {val roadLinkId: Long; val lrmPosition: LRMPosition; val point: Option[Point]; val municipalityCode: Int}, optionalRoadLink: Option[(Long, Int, Option[Point], AdministrativeClass)]): Boolean = {
    optionalRoadLink.flatMap { case (_, roadLinkMunicipalityCode, pointOnRoadLinkOption, _) =>
      val optionalCoordinateMismatch: Option[Boolean] = pointOnRoadLinkOption.map { pointOnRoadLink =>
        !coordinatesWithinThreshold(asset.point.get, pointOnRoadLink)
      }
      optionalCoordinateMismatch.map { coordinateMismatch =>
        coordinateMismatch || (asset.municipalityCode != roadLinkMunicipalityCode)
      }
    }.getOrElse(true)
  }

  private def validityPeriod(validFrom: Option[LocalDate], validTo: Option[LocalDate]): Option[String] = {
    val beginningOfTime = new LocalDate(0, 1, 1)
    val endOfTime = new LocalDate(9999, 1, 1)
    val from = validFrom.getOrElse(beginningOfTime)
    val to = validTo.getOrElse(endOfTime)
    val interval = new Interval(from.toDateMidnight(), to.toDateMidnight)
    val now = DateTime.now
    val status = if (interval.isBefore(now)) {
      ValidityPeriod.Past
    } else if (interval.contains(now)) {
      ValidityPeriod.Current
    } else {
      ValidityPeriod.Future
    }
    Some(status)
  }

  def createAsset(assetTypeId: Long, lon: Double, lat: Double, roadLinkId: Long, bearing: Int, creator: String, properties: Seq[SimpleProperty]): AssetWithProperties = {
    val assetId = Sequences.nextPrimaryKeySeqValue
    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
    val externalId = getNationalBusStopId
    val lrMeasure = roadLinkService.getPointLRMeasure(roadLinkId, Point(lon, lat))
    val testId = roadLinkService.getTestId(roadLinkId).getOrElse(roadLinkId)
    val municipalityCode = roadLinkService.getMunicipalityCode(roadLinkId).get
    insertLRMPosition(lrmPositionId, testId, roadLinkId, lrMeasure, dynamicSession.conn)
    insertAsset(assetId, externalId, assetTypeId, bearing, creator, municipalityCode).execute
    insertAssetPosition(assetId, lrmPositionId).execute
    updateAssetGeometry(assetId, Point(lon, lat))
    val defaultValues = propertyDefaultValues(assetTypeId).filterNot( defaultValue => properties.exists(_.publicId == defaultValue.publicId))
    updateAssetProperties(assetId, properties ++ defaultValues)
    getAssetById(assetId).get
  }

  def removeAsset(assetId: Long): Unit = {
    val optionalLrmPositionId = Q.query[Long, Long](assetLrmPositionId).apply(assetId).firstOption
    optionalLrmPositionId match {
      case Some(lrmPositionId) =>
        deleteAssetProperties(assetId)
        deleteAssetLink(assetId).execute
        deleteAsset(assetId).execute
        try {
          deleteLRMPosition(lrmPositionId).execute
        } catch {
          case e: SQLException => throw new LRMPositionDeletionFailed("LRM position " + lrmPositionId + " deletion failed with exception " + e.toString)
        }
      case None => ()
    }
  }

  private def updateAssetMunicipality(id: Long, roadLinkId: Long): Unit = {
    val municipalityCode = roadLinkService.getMunicipalityCode(roadLinkId).get
    sqlu"update asset set municipality_code = $municipalityCode where id = $id".execute
  }

  def updateAsset(assetId: Long, position: Option[Position], modifier: String, properties: Seq[SimpleProperty]): AssetWithProperties = {
    updateAssetLastModified(assetId, modifier)
    if (!properties.isEmpty) {
      updateAssetProperties(assetId, properties)
    }
    position match {
      case None => logger.debug("not updating position")
      case Some(pos) => {
        updateAssetLocation(id = assetId, lon = pos.lon, lat = pos.lat, roadLinkId = pos.roadLinkId, bearing = pos.bearing)
        updateAssetGeometry(assetId, Point(pos.lon, pos.lat))
        updateAssetMunicipality(assetId, pos.roadLinkId)
      }
    }
    getAssetById(assetId).get
  }

  def updateAssetLastModified(assetId: Long, modifier: String) {
    updateAssetModified(assetId, modifier).execute
  }

  private def validPropertyUpdates(propertyWithType: Tuple3[String, Option[Long], SimpleProperty]): Boolean = {
    propertyWithType match {
      case (SingleChoice, _, property) => property.values.size > 0
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

  def getEnumeratedPropertyValues(assetTypeId: Long): Seq[EnumeratedPropertyValue] = {
    Q.query[Long, EnumeratedPropertyValueRow](enumeratedPropertyValues).apply(assetTypeId).list.groupBy(_.propertyId).map { case (k, v) =>
      val row = v(0)
      EnumeratedPropertyValue(row.propertyId, row.propertyPublicId, row.propertyName, row.propertyType, row.required, v.map(r => PropertyValue(r.value.toString, Some(r.displayValue))).toSeq)
    }.toSeq
  }

  private def updateAssetLocation(id: Long, lon: Double, lat: Double, roadLinkId: Long, bearing: Option[Int]) {
    val point = Point(lon, lat)
    val lrMeasure = roadLinkService.getPointLRMeasure(roadLinkId, point)
    val lrmPositionId = Q.query[Long, Long](assetLrmPositionId).apply(id).first
    val testId = roadLinkService.getTestId(roadLinkId).getOrElse(roadLinkId)
    updateLRMeasure(lrmPositionId, testId, roadLinkId, lrMeasure, dynamicSession.conn)
    bearing match {
      case Some(b) => updateAssetBearing(id, b).execute
      case None => // do nothing
    }
  }

  private def updateAssetSpecificProperty(assetId: Long, propertyPublicId: String, propertyId: Long, propertyType: String, propertyValues: Seq[PropertyValue]) {
    propertyType match {
      case Text | LongText => {
        if (propertyValues.size > 1) throw new IllegalArgumentException("Text property must have exactly one value: " + propertyValues)
        if (propertyValues.size == 0) {
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
      case ReadOnly => {
        logger.debug("Ignoring read only property in update: " + propertyPublicId)
      }
      case t: String => throw new UnsupportedOperationException("Asset property type: " + t + " not supported")
    }
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
      case ReadOnlyText => {
        logger.debug("Ignoring read only text property in update: " + propertyPublicId)
      }
      case t: String => throw new UnsupportedOperationException("Asset: " + propertyPublicId + " property type: " + t + " not supported")
    }
  }

  def deleteAssetProperty(assetId: Long, propertyPublicId: String) {
    val propertyId = Q.query[String, Long](propertyIdByPublicId).apply(propertyPublicId).firstOption.getOrElse(throw new IllegalArgumentException("Property: " + propertyPublicId + " not found, cannot delete"))
    Q.query[Long, String](propertyTypeByPropertyId).apply(propertyId).first match {
      case Text | LongText => deleteTextProperty(assetId, propertyId).execute
      case SingleChoice => deleteSingleChoiceProperty(assetId, propertyId).execute
      case MultipleChoice => deleteMultipleChoiceProperty(assetId, propertyId).execute
      case t: String => throw new UnsupportedOperationException("Delete asset not supported for property type: " + t)
    }
  }

  def deleteAssetProperties(assetId: Long) {
    deleteAssetTextProperties(assetId).execute
    deleteAssetSingleChoiceProperties(assetId).execute
    deleteAssetMultipleChoiceProperties(assetId).execute
  }

  private[this] def createOrUpdateMultipleChoiceProperty(propertyValues: Seq[PropertyValue], assetId: Long, propertyId: Long) {
    val newValues = propertyValues.map(_.propertyValue)
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
        insertMultipleChoiceValue(assetId, propertyId, v.toLong).execute
    }
  }

  def getMunicipalities: Seq[Int] = {
    sql"""
      select id from municipality
    """.as[Int].list
  }

  def getMunicipalityNameByCode(code: Int): String = {
    sql"""
      select name_fi from municipality where id = $code
    """.as[String].first
  }

  def availableProperties(assetTypeId: Long): Seq[Property] = {
    implicit val getPropertyDescription = new GetResult[Property] {
      def apply(r: PositionedResult) = {
        Property(r.nextLong, r.nextString, r.nextString, r.nextInt, r.nextBoolean, Seq())
      }
    }
    sql"""
      select p.id, p.public_id, p.property_type, p.ui_position_index, p.required from property p where p.asset_type_id = $assetTypeId
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

  def getAssetGeometryById(id: Long): Point = {
    sql"""
      select geometry from asset where id = $id
    """.as[Point].first
  }
}
