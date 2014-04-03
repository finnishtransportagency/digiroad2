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
import fi.liikennevirasto.digiroad2.mtk.MtkRoadLink
import java.sql.Timestamp
import scala.collection.parallel.ForkJoinTaskSupport
import scala.concurrent.forkjoin.ForkJoinPool
import org.joda.time.format.ISODateTimeFormat
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase._
import org.slf4j.LoggerFactory
import scala.Array
import org.joda.time.LocalDate
import fi.liikennevirasto.digiroad2.user.{Role, User, UserProvider}
import fi.liikennevirasto.digiroad2.asset.ValidityPeriod
import org.joda.time.Interval
import org.joda.time.DateTime

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

  def getAssetTypes: Seq[AssetType] = {
    assetTypes.as[AssetType].list
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
      AssetWithProperties(id = row.id, externalId = row.externalId, assetTypeId = row.assetTypeId,
        lon = row.lon, lat = row.lat, roadLinkId = row.roadLinkId,
        propertyData = AssetPropertyConfiguration.assetRowToCommonProperties(row) ++ assetRowToProperty(v).sortBy(_.propertyId.toLong),
        bearing = row.bearing, municipalityNumber = Option(row.municipalityNumber),
        validityPeriod = validityPeriod(row.validFrom, row.validTo),
        imageIds = v.map(row => getImageId(row.image)).toSeq.filter(_ != null),
        validityDirection = Some(row.validityDirection))
    }.toSeq
  }

  private[oracle] def getImageId(image: Image) = {
    image.imageId match {
      case None => null
      case _ => image.imageId.get + "_" + image.lastModified.get.getMillis
    }
  }

  private[this] def assetRowToProperty(assetRows: Iterable[AssetRow]): Seq[Property] = {
    assetRows.groupBy(_.property.propertyId).map { case (k, v) =>
      val row = v.toSeq(0)
      Property(row.property.propertyId, row.property.publicId, row.property.propertyName, row.property.propertyType, row.property.propertyUiIndex, row.property.propertyRequired, v.map(r => PropertyValue(r.property.propertyValue, r.property.propertyDisplayValue, getImageId(r.image))).filter(_.propertyDisplayValue != null).toSeq)
    }.toSeq
  }

  private[this] def assetRowToAssetWithProperties(param: (Long, List[AssetRow])): AssetWithProperties = {
    val row = param._2(0)
    AssetWithProperties(id = row.id, externalId = row.externalId, assetTypeId = row.assetTypeId,
        lon = row.lon, lat = row.lat, roadLinkId = row.roadLinkId,
        propertyData = (AssetPropertyConfiguration.assetRowToCommonProperties(row) ++ assetRowToProperty(param._2)).sortBy(_.propertyUiIndex),
        bearing = row.bearing, municipalityNumber = Option(row.municipalityNumber),
        validityPeriod = validityPeriod(row.validFrom, row.validTo),
        imageIds = param._2.map(row => getImageId(row.image)).toSeq.filter(_ != null),
        validityDirection = Some(row.validityDirection))
  }

  def getAssetByExternalId(externalId: Long): Option[AssetWithProperties] = {
    Q.query[Long, (AssetRow, LRMPosition)](assetByExternalId).list(externalId).map(_._1).groupBy(_.id).map(assetRowToAssetWithProperties).headOption
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

  private def validityPeriod(validFrom: Option[Timestamp], validTo: Option[Timestamp]): Option[String] = {
    val beginningOfTime = new DateTime(0, 1, 1, 0, 0)
    val endOfTime = new DateTime(9999, 1, 1, 0, 0)
    val from = validFrom.map(new DateTime(_)).getOrElse(beginningOfTime)
    val to = validTo.map(new DateTime(_)).getOrElse(endOfTime)
    val interval = new Interval(from, to)
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
    properties.foreach { property =>
      if (!property.values.isEmpty) {
        if (AssetPropertyConfiguration.commonAssetProperties.get(property.publicId).isDefined) {
          OracleSpatialAssetDao.updateCommonAssetProperty(assetId, property.publicId, property.values)
        } else {
          OracleSpatialAssetDao.updateAssetSpecificProperty(assetId, property.publicId, property.values)
        }
      }
    }
    getAssetById(assetId).get
  }

  def getEnumeratedPropertyValues(assetTypeId: Long): Seq[EnumeratedPropertyValue] = {
    Q.query[Long, EnumeratedPropertyValueRow](enumeratedPropertyValues).list(assetTypeId).groupBy(_.propertyId).map { case (k, v) =>
      val row = v(0)
      EnumeratedPropertyValue(row.propertyId, row.propertyPublicId, row.propertyName, row.propertyType, row.required, v.map(r => PropertyValue(r.value, r.displayValue)).toSeq)
    }.toSeq
  }

  def updateAssetLocation(id: Long, lon: Double, lat: Double, roadLinkId: Long, bearing: Option[Int]): AssetWithProperties = {
    val latLonGeometry = JGeometry.createPoint(Array(lon, lat), 2, 3067)
    val lrMeasure = getPointLRMeasure(latLonGeometry, roadLinkId, dynamicSession.conn)
    val lrmPositionId = Q.query[Long, Long](assetLrmPositionId).first(id)
    updateLRMeasure(lrmPositionId, roadLinkId, lrMeasure, dynamicSession.conn)
    bearing match {
      case Some(b) => updateAssetBearing(id, b).execute()
      case None => // do nothing
    }
    getAssetById(id).get
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

  def updateAssetSpecificProperty(assetId: Long, propertyPublicId: String, propertyValues: Seq[PropertyValue]) {
    val asset = getAssetById(assetId)
    if (asset.isEmpty) throw new IllegalArgumentException("Asset " + assetId + " not found")
    val createNew = (asset.head.propertyData.exists(_.publicId == propertyPublicId) && asset.head.propertyData.find(_.publicId == propertyPublicId).get.values.isEmpty)
    val propertyId = Q.query[String, Long](propertyIdByPublicId).firstOption(propertyPublicId).getOrElse(throw new IllegalArgumentException("Property: " + propertyPublicId + " not found"))

    Q.query[Long, String](propertyTypeByPropertyId).first(propertyId) match {
      case Text | LongText => {
        if (propertyValues.size > 1) throw new IllegalArgumentException("Text property must have exactly one value: " + propertyValues)
        if (propertyValues.size == 0) {
          deleteTextProperty(assetId, propertyId).execute()
        } else if (createNew) {
          insertTextProperty(assetId, propertyId, propertyValues.head.propertyDisplayValue).execute()
        } else {
          updateTextProperty(assetId, propertyId, propertyValues.head.propertyDisplayValue).execute()
        }
      }
      case SingleChoice => {
        if (propertyValues.size != 1) throw new IllegalArgumentException("Single choice property must have exactly one value")
        if (createNew) {
          insertSingleChoiceProperty(assetId, propertyId, propertyValues.head.propertyValue).execute()
        } else {
          updateSingleChoiceProperty(assetId, propertyId, propertyValues.head.propertyValue).execute()
        }
      }
      case MultipleChoice => {
        createOrUpdateMultipleChoiceProperty(propertyValues, assetId, propertyId)
      }
      case t: String => throw new UnsupportedOperationException("Asset property type: " + t + " not supported")
    }
  }

  def updateCommonAssetProperty(assetId: Long, propertyPublicId: String, propertyValues: Seq[PropertyValue]) {
    val property = AssetPropertyConfiguration.commonAssetProperties(propertyPublicId)
    AssetPropertyConfiguration.commonAssetProperties(propertyPublicId).propertyType match {
      case SingleChoice => {
        val newVal = propertyValues.head.propertyValue.toString
        AssetPropertyConfiguration.commonAssetPropertyEnumeratedValues.find { p =>
          (p.publicId == propertyPublicId) && (p.values.map(_.propertyValue.toString).contains(newVal))
        } match {
          case Some(propValues) => {
            updateCommonProperty(assetId, property.column, newVal, property.lrmPositionProperty).execute()
          }
          case None => throw new IllegalArgumentException("Invalid property/value: " + propertyPublicId + "/" + newVal)
        }
      }
      case Text | LongText => updateCommonProperty(assetId, property.column, propertyValues.head.propertyDisplayValue).execute()
      case Date => {
        val formatter = ISODateTimeFormat.dateOptionalTimeParser()
        val optionalDateTime = propertyValues.headOption.map(_.propertyDisplayValue).map(formatter.parseDateTime)
        updateCommonDateProperty(assetId, property.column, optionalDateTime, property.lrmPositionProperty).execute()
      }
      case t: String => throw new UnsupportedOperationException("Asset property type: " + t + " not supported")
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
        insertMultipleChoiceValue(assetId, propertyId, v).execute()
    }
  }

  def getImage(imageId: Long): Array[Byte] = {
    Q.query[Long, Array[Byte]](imageById).first(imageId)
  }

  def availableProperties(assetTypeId: Long): Seq[Property] = {
    implicit val getPropertyDescription = new GetResult[Property] {
      def apply(r: PositionedResult) = {
        Property(r.nextLong, r.nextString, r.nextString, r.nextString, r.nextInt, r.nextBoolean, Seq())
      }
    }
    sql"""
      select p.id, p.public_id, ls.value_fi, p.property_type, p.ui_position_index, p.required from property p, localized_string ls where ls.id = p.name_localized_string_id and p.asset_type_id = $assetTypeId
    """.as[Property].list
  }
}
