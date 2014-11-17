package fi.liikennevirasto.digiroad2.asset.oracle

import java.sql.SQLException
import _root_.oracle.spatial.geometry.JGeometry
import fi.liikennevirasto.digiroad2.asset._
import scala.slick.driver.JdbcDriver.backend.Database
import scala.slick.jdbc.{StaticQuery => Q, PositionedResult, GetResult, PositionedParameters, SetParameter}
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.asset.oracle.Queries._
import Q.interpolation
import PropertyTypes._
import org.joda.time.format.ISODateTimeFormat
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase._
import org.slf4j.LoggerFactory
import scala.Array
import org.joda.time.LocalDate
import fi.liikennevirasto.digiroad2.user.{Role, User, UserProvider}
import fi.liikennevirasto.digiroad2.asset.ValidityPeriod
import org.joda.time.Interval
import org.joda.time.DateTime
import com.github.tototoshi.slick.MySQLJodaSupport._
import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.RoadLinkService
import scala.language.reflectiveCalls

object OracleSpatialAssetDao {
  val logger = LoggerFactory.getLogger(getClass)

  implicit val SetStringSeq: SetParameter[IndexedSeq[Any]] = new SetParameter[IndexedSeq[Any]] {
    def apply(seq: IndexedSeq[Any], p: PositionedParameters): Unit = {
      for (i <- 1 to seq.length) {
        p.ps.setObject(i, seq(i - 1))
      }
    }
  }

  def nextPrimaryKeySeqValue = {
    nextPrimaryKeyId.as[Long].first
  }

  def nextLrmPositionPrimaryKeySeqValue = {
    nextLrmPositionPrimaryKeyId.as[Long].first
  }

  def getNationalBusStopId = {
    nextNationalBusStopId.as[Long].first
  }

  def getAssetsByMunicipality(municipality: Int) = {
    val q = QueryCollector(allAssets)
    val assets = collectedQuery[AssetRow](q).groupBy(_.id)

    val assetsWithRoadLinks: Map[Long, (Option[(Long, Int, Option[Point], RoadLinkType)], Seq[AssetRow])] = assets.mapValues { assetRows =>
      val row = assetRows.head
      val roadLinkOption = getOptionalProductionRoadLink(row)
      (roadLinkOption, assetRows)
    }

    val assetsForMunicipality = assetsWithRoadLinks.filter { case (_, (roadLinkOption, _)) =>
      roadLinkOption.exists(roadLink => roadLink._2 == municipality)
    }

    assetsForMunicipality.map { case (assetId, (roadLinkOption, assetRows)) =>
      assetRowToAssetWithProperties(assetId, assetRows.toList, roadLinkOption)
    }
  }

  private[oracle] def getImageId(image: Image) = {
    image.imageId match {
      case None => null
      case _ => image.imageId.get + "_" + image.lastModified.get.getMillis
    }
  }

  private[oracle] def assetRowToProperty(assetRows: Iterable[IAssetRow]): Seq[Property] = {
    assetRows.groupBy(_.property.propertyId).map { case (key, assetRows) =>
      val row = assetRows.head
      Property(key, row.property.publicId, row.property.propertyType, row.property.propertyUiIndex, row.property.propertyRequired,
        assetRows.map(assetRow =>
          PropertyValue(
            assetRow.property.propertyValue,
            propertyDisplayValueFromAssetRow(assetRow),
            getImageId(assetRow.image))
        ).filter(_.propertyDisplayValue.isDefined).toSeq)
    }.toSeq
  }

  private def propertyDisplayValueFromAssetRow(assetRow: IAssetRow): Option[String] = {
    if (assetRow.property.publicId == "liikennointisuuntima") Some(getBearingDescription(assetRow.validityDirection, assetRow.bearing))
    else if (assetRow.property.propertyDisplayValue != null) Some(assetRow.property.propertyDisplayValue)
    else None
  }

  private[this] def assetRowToAssetWithProperties(assetId: Long, assetRows: Seq[AssetRow], optionalRoadLink: Option[(Long, Int, Option[Point], RoadLinkType)]): AssetWithProperties = {
    val row = assetRows.head
    val point = row.point.get
    val wgsPoint = row.wgsPoint.get
    val municipalityNumber = optionalRoadLink.map(_._2)
    val roadLinkType = optionalRoadLink.map(_._4).getOrElse(UnknownRoad)
    AssetWithProperties(id = row.id, externalId = row.externalId, assetTypeId = row.assetTypeId,
        lon = point.x, lat = point.y,
        propertyData = (AssetPropertyConfiguration.assetRowToCommonProperties(row) ++ assetRowToProperty(assetRows)).sortBy(_.propertyUiIndex),
        bearing = row.bearing, municipalityNumber = municipalityNumber,
        validityPeriod = validityPeriod(row.validFrom, row.validTo),
        imageIds = assetRows.map(row => getImageId(row.image)).toSeq.filter(_ != null),
        validityDirection = Some(row.validityDirection), wgslon = wgsPoint.x, wgslat = wgsPoint.y,
        created = row.created, modified = row.modified, roadLinkType = roadLinkType, floating = isFloating(row))
  }

  private def getOptionalProductionRoadLink(row: {val productionRoadLinkId: Option[Long]; val roadLinkId: Long; val lrmPosition: LRMPosition}): Option[(Long, Int, Option[Point], RoadLinkType)] = {
    val productionRoadLinkId: Option[Long] = row.productionRoadLinkId
    productionRoadLinkId.map { roadLinkId =>
      RoadLinkService.getByIdAndMeasure(roadLinkId, row.lrmPosition.startMeasure)
    }.getOrElse(RoadLinkService.getByTestIdAndMeasure(row.roadLinkId, row.lrmPosition.startMeasure))
  }

  private[this] def singleAssetRowToAssetWithProperties(param: (Long, List[SingleAssetRow])): AssetWithProperties = {
    val row = param._2(0)
    val point = row.point.get
    val wgsPoint = row.wgsPoint.get
    val roadLinkOption = getOptionalProductionRoadLink(row)
    val floating = roadLinkOption.flatMap(_._3.map(isFloating(point, _))).getOrElse(true)
    AssetWithProperties(
        id = row.id, externalId = row.externalId, assetTypeId = row.assetTypeId,
        lon = point.x, lat = point.y,
        propertyData = (AssetPropertyConfiguration.assetRowToCommonProperties(row) ++ assetRowToProperty(param._2)).sortBy(_.propertyUiIndex),
        bearing = row.bearing, municipalityNumber = roadLinkOption.map(_._2),
        validityPeriod = validityPeriod(row.validFrom, row.validTo),
        imageIds = param._2.map(row => getImageId(row.image)).toSeq.filter(_ != null),
        validityDirection = Some(row.validityDirection), wgslon = wgsPoint.x, wgslat = wgsPoint.y,
        created = row.created, modified = row.modified, roadLinkType = roadLinkOption.map(_._4).getOrElse(UnknownRoad),
        floating = floating)
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

  def getAssetByExternalId(externalId: Long): Option[AssetWithProperties] = {
    Q.query[Long, SingleAssetRow](assetByExternalId).list(externalId).groupBy(_.id).map(singleAssetRowToAssetWithProperties).headOption
  }

  def getAssetById(assetId: Long): Option[AssetWithProperties] = {
    Q.query[Long, SingleAssetRow](assetWithPositionById).list(assetId).groupBy(_.id).map(singleAssetRowToAssetWithProperties).headOption
  }

  def getAssetPositionByExternalId(externalId: Long): Option[Point] = {
    Q.query[Long, SingleAssetRow](assetByExternalId).firstOption(externalId).flatMap { case row =>
      row.point
    }
  }

  private def getAssetMunicipalityCode(optionalRoadLink: Option[(Long, Int, Option[Point], RoadLinkType)], assetRow: ListedAssetRow): Option[Int] = {
    optionalRoadLink.map(_._2).orElse(assetRow.municipalityCode)
  }

  def getAssets(user: User, bounds: Option[BoundingRectangle], validFrom: Option[LocalDate], validTo: Option[LocalDate]): Seq[Asset] = {
    def andAssetWithinBoundingBox = bounds map { b =>
      val boundingBox = new JGeometry(b.leftBottom.x, b.leftBottom.y, b.rightTop.x, b.rightTop.y, 3067)
      ("AND SDO_FILTER(geometry, ?) = 'TRUE'", List(storeGeometry(boundingBox, dynamicSession.conn)))
    }
    def andValidityInRange = (validFrom, validTo) match {
      case (Some(from), Some(to)) => Some(andByValidityTimeConstraint, List(jodaToSqlDate(from), jodaToSqlDate(to)))
      case (None, Some(to)) => Some(andExpiredBefore, List(jodaToSqlDate(to)))
      case (Some(from), None) => Some(andValidAfter, List(jodaToSqlDate(from)))
      case (None, None) => None
    }
    val query = QueryCollector(allAssetsWithoutProperties).add(andValidityInRange).add(andAssetWithinBoundingBox)
    val allAssets = collectedQuery[ListedAssetRow](query).iterator
    val assetsWithProperties: Map[Long, Seq[ListedAssetRow]] = allAssets.toSeq.groupBy(_.id)
    val assetsWithRoadLinks: Map[Long, (Option[(Long, Int, Option[Point], RoadLinkType)], Seq[ListedAssetRow])] = assetsWithProperties.mapValues { assetRows =>
      val row = assetRows.head
      val roadLinkOption = getOptionalProductionRoadLink(row)
      (roadLinkOption, assetRows)
    }
    val authorizedAssets = user.configuration.roles.contains(Role.Operator) match {
      case true => assetsWithRoadLinks
      case false => assetsWithRoadLinks.filter { case (_, (roadLinkOption, assetRows)) =>
        val assetRow = assetRows.head
        val municipalityCode: Option[Int] = getAssetMunicipalityCode(roadLinkOption, assetRow)
        municipalityCode.exists(code => user.configuration.authorizedMunicipalities.contains(code))
      }
    }
    authorizedAssets.map { case (assetId, (roadLinkOption, assetRows)) =>
      val row = assetRows.head
      val point = row.point.get
      Asset(id = row.id,
        externalId = row.externalId,
        assetTypeId = row.assetTypeId,
        lon = point.x,
        lat = point.y,
        roadLinkId = roadLinkOption.map(_._1).getOrElse(-1), // FIXME: Temporary solution for possibly missing roadLinkId
        imageIds = assetRows.map(row => getImageId(row.image)).toSeq,
        bearing = row.bearing,
        validityDirection = Some(row.validityDirection),
        municipalityNumber = roadLinkOption.map(_._2),
        validityPeriod = validityPeriod(row.validFrom, row.validTo),
        floating = roadLinkOption.flatMap(_._3.map(isFloating(point, _))).getOrElse(true))
    }.toSeq
  }

  private val FLOAT_THRESHOLD_IN_METERS = 5

  def coordinatesWithinThreshold(pt1: Point, pt2: Point): Boolean = {
    pt1.distanceTo(pt2) <= FLOAT_THRESHOLD_IN_METERS
  }

  def isFloating(asset: {val roadLinkId: Long; val lrmPosition: LRMPosition; val point: Option[Point]}): Boolean = {
    val roadLinkOption = RoadLinkService.getPointOnRoadLink(asset.roadLinkId, asset.lrmPosition.startMeasure)
    roadLinkOption.flatMap { case (_, pointOnRoadLinkOption) =>
      pointOnRoadLinkOption.map { pointOnRoadLink =>
        !coordinatesWithinThreshold(asset.point.get, pointOnRoadLink)
      }
    }.getOrElse(true)
  }

  def isFloating(storedPoint: Point, lrmPoint: Point): Boolean = {
    !coordinatesWithinThreshold(storedPoint, lrmPoint)
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

  def updateAssetGeometry(id: Long, point: Point): Unit = {
    val x = point.x
    val y = point.y
    sqlu"""
      update asset
        set geometry = MDSYS.SDO_GEOMETRY(4401,
                                          3067,
                                          NULL,
                                          MDSYS.SDO_ELEM_INFO_ARRAY(1,1,1),
                                          MDSYS.SDO_ORDINATE_ARRAY($x, $y, 0, 0)
                                         )
        where id = $id
    """.execute
  }

  def createAsset(assetTypeId: Long, lon: Double, lat: Double, roadLinkId: Long, bearing: Int, creator: String, properties: Seq[SimpleProperty]): AssetWithProperties = {
    val assetId = nextPrimaryKeySeqValue
    val lrmPositionId = nextLrmPositionPrimaryKeySeqValue
    val externalId = getNationalBusStopId
    val lrMeasure = RoadLinkService.getPointLRMeasure(roadLinkId, Point(lon, lat))
    val testId = RoadLinkService.getTestId(roadLinkId).get
    insertLRMPosition(lrmPositionId, testId, roadLinkId, lrMeasure, dynamicSession.conn)
    insertAsset(assetId, externalId, assetTypeId, bearing, creator).execute
    insertAssetPosition(assetId, lrmPositionId).execute
    updateAssetGeometry(assetId, Point(lon, lat))
    val defaultValues = propertyDefaultValues(assetTypeId).filterNot( defaultValue => properties.exists(_.publicId == defaultValue.publicId))
    updateAssetProperties(assetId, properties ++ defaultValues)
    getAssetById(assetId).get
  }

  def removeAsset(assetId: Long): Unit = {
    val optionalLrmPositionId = Q.query[Long, Long](assetLrmPositionId).firstOption(assetId)
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
      }
    }
    getAssetById(assetId).get
  }

  private def updateAssetLastModified(assetId: Long, modifier: String) {
    updateAssetModified(assetId, modifier).execute()
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
      val propertyId = Q.query[String, Long](propertyIdByPublicId).firstOption(property.publicId).getOrElse(throw new IllegalArgumentException("Property: " + property.publicId + " not found"))
      (Q.query[Long, String](propertyTypeByPropertyId).first(propertyId), Some(propertyId), property)
    }
  }

  private def updateAssetProperties(assetId: Long, properties: Seq[SimpleProperty]) {
    properties.map(propertyWithTypeAndId).filter(validPropertyUpdates).foreach { propertyWithTypeAndId =>
      if (AssetPropertyConfiguration.commonAssetProperties.get(propertyWithTypeAndId._3.publicId).isDefined) {
        OracleSpatialAssetDao.updateCommonAssetProperty(assetId, propertyWithTypeAndId._3.publicId, propertyWithTypeAndId._1, propertyWithTypeAndId._3.values)
      } else {
        OracleSpatialAssetDao.updateAssetSpecificProperty(assetId, propertyWithTypeAndId._3.publicId, propertyWithTypeAndId._2.get, propertyWithTypeAndId._1, propertyWithTypeAndId._3.values)
      }
    }
  }

  def getEnumeratedPropertyValues(assetTypeId: Long): Seq[EnumeratedPropertyValue] = {
    Q.query[Long, EnumeratedPropertyValueRow](enumeratedPropertyValues).list(assetTypeId).groupBy(_.propertyId).map { case (k, v) =>
      val row = v(0)
      EnumeratedPropertyValue(row.propertyId, row.propertyPublicId, row.propertyName, row.propertyType, row.required, v.map(r => PropertyValue(r.value.toString, Some(r.displayValue))).toSeq)
    }.toSeq
  }

  private def updateAssetLocation(id: Long, lon: Double, lat: Double, roadLinkId: Long, bearing: Option[Int]) {
    val point = Point(lon, lat)
    val lrMeasure = RoadLinkService.getPointLRMeasure(roadLinkId, point)
    val lrmPositionId = Q.query[Long, Long](assetLrmPositionId).first(id)
    val testId = RoadLinkService.getTestId(roadLinkId).get
    updateLRMeasure(lrmPositionId, testId, roadLinkId, lrMeasure, dynamicSession.conn)
    bearing match {
      case Some(b) => updateAssetBearing(id, b).execute()
      case None => // do nothing
    }
  }

  private def updateAssetSpecificProperty(assetId: Long, propertyPublicId: String, propertyId: Long, propertyType: String, propertyValues: Seq[PropertyValue]) {
    propertyType match {
      case Text | LongText => {
        if (propertyValues.size > 1) throw new IllegalArgumentException("Text property must have exactly one value: " + propertyValues)
        if (propertyValues.size == 0) {
          deleteTextProperty(assetId, propertyId).execute()
        } else if (textPropertyValueDoesNotExist(assetId, propertyId)) {
          insertTextProperty(assetId, propertyId, propertyValues.head.propertyValue).execute()
        } else {
          updateTextProperty(assetId, propertyId, propertyValues.head.propertyValue).execute()
        }
      }
      case SingleChoice => {
        if (propertyValues.size != 1) throw new IllegalArgumentException("Single choice property must have exactly one value. publicId: " + propertyPublicId)
        if (singleChoiceValueDoesNotExist(assetId, propertyId)) {
          insertSingleChoiceProperty(assetId, propertyId, propertyValues.head.propertyValue.toLong).execute()
        } else {
          updateSingleChoiceProperty(assetId, propertyId, propertyValues.head.propertyValue.toLong).execute()
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
    Q.query[(Long, Long), Long](existsTextProperty).firstOption((assetId, propertyId)).isEmpty
  }

  private def singleChoiceValueDoesNotExist(assetId: Long, propertyId: Long) = {
    Q.query[(Long, Long), Long](existsSingleChoiceProperty).firstOption((assetId, propertyId)).isEmpty
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
            updateCommonProperty(assetId, property.column, newVal, property.lrmPositionProperty).execute()
          }
          case None => throw new IllegalArgumentException("Invalid property/value: " + propertyPublicId + "/" + newVal)
        }
      }
      case Text | LongText => updateCommonProperty(assetId, property.column, propertyValues.head.propertyValue).execute()
      case Date => {
        val formatter = ISODateTimeFormat.dateOptionalTimeParser()
        val optionalDateTime = propertyValues.headOption match {
          case None => None
          case Some(x) if x.propertyValue.trim.isEmpty => None
          case Some(x) => Some(formatter.parseDateTime(x.propertyValue))
        }
        updateCommonDateProperty(assetId, property.column, optionalDateTime, property.lrmPositionProperty).execute()
      }
      case ReadOnlyText => {
        logger.debug("Ignoring read only text property in update: " + propertyPublicId)
      }
      case t: String => throw new UnsupportedOperationException("Asset: " + propertyPublicId + " property type: " + t + " not supported")
    }
  }

  def deleteAssetProperty(assetId: Long, propertyPublicId: String) {
    val propertyId = Q.query[String, Long](propertyIdByPublicId).firstOption(propertyPublicId).getOrElse(throw new IllegalArgumentException("Property: " + propertyPublicId + " not found, cannot delete"))
    Q.query[Long, String](propertyTypeByPropertyId).first(propertyId) match {
      case Text | LongText => deleteTextProperty(assetId, propertyId).execute()
      case SingleChoice => deleteSingleChoiceProperty(assetId, propertyId).execute()
      case MultipleChoice => deleteMultipleChoiceProperty(assetId, propertyId).execute()
      case t: String => throw new UnsupportedOperationException("Delete asset not supported for property type: " + t)
    }
  }

  def deleteAssetProperties(assetId: Long) {
    deleteAssetTextProperties(assetId).execute()
    deleteAssetSingleChoiceProperties(assetId).execute()
    deleteAssetMultipleChoiceProperties(assetId).execute()
  }

  private[this] def createOrUpdateMultipleChoiceProperty(propertyValues: Seq[PropertyValue], assetId: Long, propertyId: Long) {
    val newValues = propertyValues.map(_.propertyValue)
    val currentIdsAndValues = Q.query[(Long, Long), (Long, Long)](multipleChoicePropertyValuesByAssetIdAndPropertyId).list(assetId, propertyId)
    val currentValues = currentIdsAndValues.map(_._2)
    // remove values as necessary
    currentIdsAndValues.foreach {
      case (multipleChoiceId, enumValue) =>
        if (!newValues.contains(enumValue)) {
          deleteMultipleChoiceValue(multipleChoiceId).execute()
        }
    }
    // add values as necessary
    newValues.filter {
      !currentValues.contains(_)
    }.foreach {
      v =>
        insertMultipleChoiceValue(assetId, propertyId, v.toLong).execute()
    }
  }

  def getImage(imageId: Long): Array[Byte] = {
    Q.query[Long, Array[Byte]](imageById).first(imageId)
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

  def requiredProperties(assetTypeId: Long): Set[Property] = {
    availableProperties(assetTypeId).filter(_.required).toSet
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
