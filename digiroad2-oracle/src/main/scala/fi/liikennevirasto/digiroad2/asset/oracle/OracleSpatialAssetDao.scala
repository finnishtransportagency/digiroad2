package fi.liikennevirasto.digiroad2.asset.oracle

import _root_.oracle.spatial.geometry.JGeometry
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.asset.AssetStatus._
import scala.slick.driver.JdbcDriver.backend.Database
import scala.slick.jdbc.{StaticQuery => Q, PositionedResult, GetResult, PositionedParameters, SetParameter}
import Database.dynamicSession
import Queries._
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

// TODO: trait + class?
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

  def getNationalBusStopId = {
    nextNationalBusStopId.as[Long].first
  }

  def getAssetsByMunicipality(municipality: Int) = {
    def withMunicipality = {
      Some(" WHERE rl.municipality_number = ?", List(municipality))
    }
    val q = QueryCollector(allAssets).add(withMunicipality)
    collectedQuery[(AssetRow, LRMPosition)](q).map(_._1).groupBy(_.id).map { case (k, v) =>
      val row = v(0)
      assetRowToAssetWithProperties(row.id, v)
    }
  }

  private[oracle] def getImageId(image: Image) = {
    image.imageId match {
      case None => null
      case _ => image.imageId.get + "_" + image.lastModified.get.getMillis
    }
  }

  private[oracle] def assetRowToProperty(assetRows: Iterable[AssetRow]): Seq[Property] = {
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

  private def propertyDisplayValueFromAssetRow(assetRow: AssetRow): Option[String] = {
    if (assetRow.property.publicId == "liikennointisuuntima") Some(getBearingDescription(assetRow.validityDirection, assetRow.bearing))
    else if (assetRow.property.propertyDisplayValue != null) Some(assetRow.property.propertyDisplayValue)
    else None
  }

  private[this] def assetRowToAssetWithProperties(param: (Long, List[AssetRow])): AssetWithProperties = {
    val row = param._2(0)
    AssetWithProperties(id = row.id, externalId = row.externalId, assetTypeId = row.assetTypeId,
        lon = row.lon, lat = row.lat, roadLinkId = row.roadLinkId,
        propertyData = (AssetPropertyConfiguration.assetRowToCommonProperties(row) ++ assetRowToProperty(param._2)).sortBy(_.propertyUiIndex),
        bearing = row.bearing, municipalityNumber = Option(row.municipalityNumber),
        validityPeriod = validityPeriod(row.validFrom, row.validTo),
        imageIds = param._2.map(row => getImageId(row.image)).toSeq.filter(_ != null),
        validityDirection = Some(row.validityDirection), wgslon = row.wgslon, wgslat = row.wgslat,
        created = row.created, modified = row.modified)
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
    Q.query[Long, (AssetRow, LRMPosition)](assetByExternalId).list(externalId).map(_._1).groupBy(_.id).map(assetRowToAssetWithProperties).headOption
  }

  def getAssetsByIds(ids: List[Long]): Seq[AssetWithProperties] = {
    val q = QueryCollector(allAssets).add(Some(assetsByIds(ids), ids))
    collectedQuery[(AssetRow, LRMPosition)](q).map(_._1).groupBy(_.id).map(assetRowToAssetWithProperties).toList
  }

  def getAssetById(assetId: Long): Option[AssetWithProperties] = {
    Q.query[Long, (AssetRow, LRMPosition)](assetWithPositionById).list(assetId).map(_._1).groupBy(_.id).map(assetRowToAssetWithProperties).headOption
  }

  def getAssetPositionByExternalId(externalId: Long): Option[(Double, Double)] = {
    Q.query[Long, (AssetRow, LRMPosition)](assetByExternalId).list(externalId).map(_._1).groupBy(_.id).map { case (k, v) =>
      val row = v(0)
      (row.lon, row.lat)
    }.headOption
  }

  def getAssets(assetTypeId: Long, user: User, bounds: Option[BoundingRectangle], validFrom: Option[LocalDate], validTo: Option[LocalDate]): Seq[Asset] = {
    def andMunicipality =
      if (user.configuration.roles.contains(Role.Operator)) {
        None
      } else {
        Some(("AND rl.municipality_number IN (" + user.configuration.authorizedMunicipalities.toList.map(_ => "?").mkString(",") + ")", user.configuration.authorizedMunicipalities.toList))
      }
    def andWithinBoundingBox = bounds map { b =>
      val boundingBox = new JGeometry(b.leftBottom.x, b.leftBottom.y, b.rightTop.x, b.rightTop.y, 3067)
      (roadLinksAndWithinBoundingBox, List(storeGeometry(boundingBox, dynamicSession.conn)))
    }
    def andValidityInRange = (validFrom, validTo) match {
      case (Some(from), Some(to)) => Some(andByValidityTimeConstraint, List(jodaToSqlDate(from), jodaToSqlDate(to)))
      case (None, Some(to)) => Some(andExpiredBefore, List(jodaToSqlDate(to)))
      case (Some(from), None) => Some(andValidAfter, List(jodaToSqlDate(from)))
      case (None, None) => None
    }
    def assetStatus(validFrom: Option[LocalDate], validTo: Option[LocalDate], roadLinkEndDate: Option[LocalDate]): Option[String] = {
      def beforeTimePeriod(date: LocalDate, periodStartDate: LocalDate, periodEndDate: LocalDate): Boolean = {
        (date.compareTo(periodStartDate) < 0) && (date.compareTo(periodEndDate) < 0)
      }
      (validFrom, validTo, roadLinkEndDate) match {
        case (Some(from), Some(to), Some(end)) => if (beforeTimePeriod(end, from, to)) Some(Floating) else None
        case _ => None
      }
    }
    val q = QueryCollector(assetsByTypeWithPosition, IndexedSeq(assetTypeId.toString)).add(andMunicipality).add(andWithinBoundingBox).add(andValidityInRange)
    collectedQuery[(ListedAssetRow, LRMPosition)](q).map(_._1).groupBy(_.id).map { case (k, v) =>
      val row = v(0)
      Asset(id = row.id, externalId = row.externalId, assetTypeId = row.assetTypeId, lon = row.lon,
        lat = row.lat, roadLinkId = row.roadLinkId,
        imageIds = v.map(row => getImageId(row.image)).toSeq,
        bearing = row.bearing,
        validityDirection = Some(row.validityDirection),
        status = assetStatus(validFrom, validTo, row.roadLinkEndDate),
        municipalityNumber = Option(row.municipalityNumber), validityPeriod = validityPeriod(row.validFrom, row.validTo))
    }.toSeq
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
    val assetId = nextPrimaryKeySeqValue
    val lrmPositionId = nextPrimaryKeySeqValue
    val externalId = getNationalBusStopId
    val latLonGeometry = JGeometry.createPoint(Array(lon, lat), 2, 3067)
    val lrMeasure = getPointLRMeasure(latLonGeometry, roadLinkId, dynamicSession.conn)
    insertLRMPosition(lrmPositionId, roadLinkId, lrMeasure, dynamicSession.conn)
    insertAsset(assetId, externalId, assetTypeId, lrmPositionId, bearing, creator).execute
    val defaultValues = propertyDefaultValues(assetTypeId).filterNot( defaultValue => properties.exists(_.publicId == defaultValue.publicId))
    updateAssetProperties(assetId, properties ++ defaultValues)
    getAssetById(assetId).get
  }

  def removeAsset(assetId: Long): Unit = {
    Q.query[Long, Long](assetLrmPositionId).firstOption(assetId).map { lrmPositionId =>
      deleteAssetProperties(assetId)
      deleteAsset(assetId).execute()
      deleteLRMPosition(lrmPositionId).execute()
    }
  }

  def updateAssetLastModified(assetId: Long, modifier: String) {
    updateAssetModified(assetId, modifier).execute()
  }

  def validPropertyUpdates(propertyWithType: Tuple3[String, Option[Long], SimpleProperty]): Boolean = {
    propertyWithType match {
      case (SingleChoice, _, property) => property.values.size > 0
      case _ => true
    }
  }

  def propertyWithTypeAndId(property: SimpleProperty): Tuple3[String, Option[Long], SimpleProperty] = {
    if (AssetPropertyConfiguration.commonAssetProperties.get(property.publicId).isDefined) {
      (AssetPropertyConfiguration.commonAssetProperties(property.publicId).propertyType, None, property)
    }
    else {
      val propertyId = Q.query[String, Long](propertyIdByPublicId).firstOption(property.publicId).getOrElse(throw new IllegalArgumentException("Property: " + property.publicId + " not found"))
      (Q.query[Long, String](propertyTypeByPropertyId).first(propertyId), Some(propertyId), property)
    }
  }

  def updateAssetProperties(assetId: Long, properties: Seq[SimpleProperty]) {
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

  def updateAssetLocation(id: Long, lon: Double, lat: Double, roadLinkId: Long, bearing: Option[Int]) {
    val latLonGeometry = JGeometry.createPoint(Array(lon, lat), 2, 3067)
    val lrMeasure = getPointLRMeasure(latLonGeometry, roadLinkId, dynamicSession.conn)
    val lrmPositionId = Q.query[Long, Long](assetLrmPositionId).first(id)
    updateLRMeasure(lrmPositionId, roadLinkId, lrMeasure, dynamicSession.conn)
    bearing match {
      case Some(b) => updateAssetBearing(id, b).execute()
      case None => // do nothing
    }
  }

  def getRoadLinks(user: User, bounds: Option[BoundingRectangle]): Seq[RoadLink] = {
    def andMunicipality =
      if (user.configuration.roles.contains(Role.Operator)) None else Some((roadLinksAndMunicipality(user.configuration.authorizedMunicipalities.toList), user.configuration.authorizedMunicipalities.toList))
    def andWithinBoundingBox = bounds map { b =>
      val boundingBox = new JGeometry(b.leftBottom.x, b.leftBottom.y, b.rightTop.x, b.rightTop.y, 3067);
      (roadLinksAndWithinBoundingBox, List(storeGeometry(boundingBox, dynamicSession.conn)))
    }
    val q = QueryCollector(roadLinks).add(andMunicipality).add(andWithinBoundingBox)
    collectedQuery[RoadLink](q)
  }

  def getRoadLinkById(roadLinkId: Long): Option[RoadLink] = {
    Q.query[Long, RoadLink](roadLinks + " AND id = ?").firstOption(roadLinkId)
  }

  def updateAssetSpecificProperty(assetId: Long, propertyPublicId: String, propertyId: Long, propertyType: String, propertyValues: Seq[PropertyValue]) {
    val asset = getAssetById(assetId)
    if (asset.isEmpty) throw new IllegalArgumentException("Asset " + assetId + " not found")
    val createNew = (asset.head.propertyData.exists(_.publicId == propertyPublicId) && asset.head.propertyData.find(_.publicId == propertyPublicId).get.values.isEmpty)

    propertyType match {
      case Text | LongText => {
        if (propertyValues.size > 1) throw new IllegalArgumentException("Text property must have exactly one value: " + propertyValues)
        if (propertyValues.size == 0) {
          deleteTextProperty(assetId, propertyId).execute()
        } else if (createNew) {
          insertTextProperty(assetId, propertyId, propertyValues.head.propertyValue).execute()
        } else {
          updateTextProperty(assetId, propertyId, propertyValues.head.propertyValue).execute()
        }
      }
      case SingleChoice => {
        if (propertyValues.size != 1) throw new IllegalArgumentException("Single choice property must have exactly one value. publicId: " + propertyPublicId)
        if (createNew) {
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

  def updateCommonAssetProperty(assetId: Long, propertyPublicId: String, propertyType: String, propertyValues: Seq[PropertyValue]) {
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
}
