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
import fi.liikennevirasto.digiroad2.user.UserProvider
import fi.liikennevirasto.digiroad2.asset.ValidityPeriod
import org.joda.time.Interval
import org.joda.time.DateTime

class OracleSpatialAssetProvider(userProvider: UserProvider) extends AssetProvider {
  val logger = LoggerFactory.getLogger(getClass)

  implicit val SetStringSeq: SetParameter[IndexedSeq[Any]] = new SetParameter[IndexedSeq[Any]] {
    def apply(seq: IndexedSeq[Any], p: PositionedParameters): Unit = {
      for (i <- 1 to seq.length) {
        p.ps.setObject(i, seq(i - 1))
      }
    }
  }

  private def userCanModifyMunicipality(municipalityNumber: Long): Boolean =
    userProvider.getCurrentUser.configuration.authorizedMunicipalities.contains(municipalityNumber)

  private def userCanModifyAsset(assetId: Long): Boolean =
    getAssetById(assetId).flatMap(a => a.municipalityNumber.map(userCanModifyMunicipality)).getOrElse(false)

  private def userCanModifyRoadLink(roadLinkId: Long): Boolean =
    getRoadLinkById(roadLinkId).map(rl => userCanModifyMunicipality(rl.municipalityNumber)).getOrElse(false)

  def getAssetTypes: Seq[AssetType] = {
    Database.forDataSource(ds).withDynSession {
      assetTypes.as[AssetType].list
    }
  }

  private[this] def nextPrimaryKeySeqValue = {
    Database.forDataSource(ds).withDynSession {
      nextPrimaryKeyId.as[Long].first
    }
  }

  private[oracle] def getImageId(image: Image) = {
    image.imageId match {
      case None => null
      case _ => image.imageId.get + "_" + image.lastModified.get.getMillis
    }
  }

  private[this] def assetRowToProperty(assetRows: Iterable[AssetRow]): Seq[Property] = {
    assetRows.groupBy(_.propertyId).map { case (k, v) =>
      val row = v.toSeq(0)
      Property(row.propertyId.toString, row.propertyName, row.propertyType, row.propertyRequired, v.map(r => PropertyValue(r.propertyValue, r.propertyDisplayValue, getImageId(r.image))).filter(_.propertyDisplayValue != null).toSeq)
    }.toSeq
  }

  def getAssetById(assetId: Long): Option[AssetWithProperties] = {
    Database.forDataSource(ds).withDynSession {
      Q.query[Long, (AssetRow, LRMPosition)](assetWithPositionById).list(assetId).map(_._1).groupBy(_.id).map { case (k, v) =>
        val row = v(0)
        AssetWithProperties(id = row.id, assetTypeId = row.assetTypeId,
              lon = row.lon, lat = row.lat, roadLinkId = row.roadLinkId,
              propertyData = AssetPropertyConfiguration.assetRowToCommonProperties(row) ++ assetRowToProperty(v),
              bearing = row.bearing, municipalityNumber = Option(row.municipalityNumber),
              validityPeriod = validityPeriod(row.validFrom, row.validTo),
              imageIds = v.map(row => getImageId(row.image)).toSeq.filter(_ != null),
              validityDirection = Some(row.validityDirection))
      }.headOption
    }
  }

  def getAssets(assetTypeId: Long, municipalityNumbers: Seq[Long], bounds: Option[BoundingCircle], validFrom: Option[LocalDate], validTo: Option[LocalDate]): Seq[Asset] = {
    def andMunicipality =
      if (municipalityNumbers.isEmpty) {
        None
      } else {
        Some(("AND rl.municipality_number IN (" + municipalityNumbers.map(_ => "?").mkString(",") + ")", municipalityNumbers.toList))
      }
    def andWithinDistance = bounds map { b =>
        val latLonGeometry = JGeometry.createPoint(Array(b.centreLat, b.centreLon), 2, 3067)
        ("AND SDO_WITHIN_DISTANCE(rl.geom, ?, ?) = 'TRUE'", List(storeGeometry(latLonGeometry, dynamicSession.conn), "DISTANCE=" + b.radiusM + " UNIT=METER"))
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
    Database.forDataSource(ds).withDynSession {
      val q = QueryCollector(assetsByTypeWithPosition, IndexedSeq(assetTypeId.toString)).add(andMunicipality).add(andWithinDistance).add(andValidityInRange)
      collectedQuery[(ListedAssetRow, LRMPosition)](q).map(_._1).groupBy(_.id).map { case (k, v) =>
        val row = v(0)
        Asset(id = row.id, assetTypeId = row.assetTypeId, lon = row.lon,
              lat = row.lat, roadLinkId = row.roadLinkId,
              imageIds = v.map(row => getImageId(row.image)).toSeq,
              bearing = row.bearing,
              validityDirection = Some(row.validityDirection),
              status = assetStatus(validFrom, validTo, row.roadLinkEndDate),
              municipalityNumber = Option(row.municipalityNumber), validityPeriod = validityPeriod(row.validFrom, row.validTo))
      }.toSeq
    }
  }

  def validityPeriod(validFrom: Option[Timestamp], validTo: Option[Timestamp]): Option[String] = {
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

  def createAsset(assetTypeId: Long, lon: Double, lat: Double, roadLinkId: Long, bearing: Int): AssetWithProperties = {
    Database.forDataSource(ds).withDynSession {
      val creator = userProvider.getCurrentUser.username
      if (!userCanModifyRoadLink(roadLinkId)) {
        throw new IllegalArgumentException("User does not have write access to municipality")
      }
      val assetId = nextPrimaryKeySeqValue
      val lrmPositionId = nextPrimaryKeySeqValue
      val latLonGeometry = JGeometry.createPoint(Array(lon, lat), 2, 3067)
      val lrMeasure = getPointLRMeasure(latLonGeometry, roadLinkId, dynamicSession.conn)
      insertLRMPosition(lrmPositionId, roadLinkId, lrMeasure, dynamicSession.conn)
      insertAsset(assetId, assetTypeId, lrmPositionId, bearing, creator).execute
      getAssetById(assetId).get
    }
  }

  def getEnumeratedPropertyValues(assetTypeId: Long): Seq[EnumeratedPropertyValue] = {
    AssetPropertyConfiguration.commonAssetPropertyEnumeratedValues ++ Database.forDataSource(ds).withDynSession {
      Q.query[Long, EnumeratedPropertyValueRow](enumeratedPropertyValues).list(assetTypeId).groupBy(_.propertyId).map { case (k, v) =>
        val row = v(0)
        EnumeratedPropertyValue(row.propertyId.toString, row.propertyName, row.propertyType, row.required, v.map(r => PropertyValue(r.value, r.displayValue)).toSeq)
      }.toSeq
    }
  }

  def updateAssetLocation(asset: Asset): AssetWithProperties = {
    Database.forDataSource(ds).withDynSession {
      if (!userCanModifyAsset(asset.id)) {
        throw new IllegalArgumentException("User does not have write access to municipality")
      }
      val latLonGeometry = JGeometry.createPoint(Array(asset.lon, asset.lat), 2, 3067)
      val lrMeasure = getPointLRMeasure(latLonGeometry, asset.roadLinkId, dynamicSession.conn)
      val lrmPositionId = Q.query[Long, Long](assetLrmPositionId).first(asset.id)
      updateLRMeasure(lrmPositionId, asset.roadLinkId, lrMeasure, dynamicSession.conn)
      asset.bearing match {
        case Some(b) => updateAssetBearing(asset.id, b).execute()
        case None => // do nothing
      }
      getAssetById(asset.id).get
    }
  }

  def updateRoadLinks(roadlinks: Seq[MtkRoadLink]): Unit = {
    // TODO: Verify write access?
    val parallerSeq = roadlinks.par
    parallerSeq.tasksupport = new ForkJoinTaskSupport(new ForkJoinPool(20))
    parallerSeq.foreach(RoadlinkProvider.updateRoadLink(ds, _))
  }

  def getRoadLinks(municipalityNumbers: Seq[Long], bounds: Option[BoundingCircle]): Seq[RoadLink] = {
    def andMunicipality =
      if (municipalityNumbers.isEmpty) None else Some(roadLinksAndMunicipality(municipalityNumbers), municipalityNumbers.toList)
    def andWithinDistance = bounds map { b =>
      val latLonGeometry = JGeometry.createPoint(Array(b.centreLat, b.centreLon), 2, 3067)
      (roadLinksAndWithinDistance, List(storeGeometry(latLonGeometry, dynamicSession.conn), "DISTANCE=" + b.radiusM + " UNIT=METER"))
    }
    Database.forDataSource(ds).withDynSession {
      val q = QueryCollector(roadLinks).add(andMunicipality).add(andWithinDistance)
      collectedQuery[RoadLink](q)
    }
  }

  def getRoadLinkById(roadLinkId: Long): Option[RoadLink] = {
    Database.forDataSource(ds).withDynSession {
      Q.query[Long, RoadLink](roadLinks + " AND id = ?").firstOption(roadLinkId)
    }
  }


  def updateAssetProperty(assetId: Long, propertyId: String, propertyValues: Seq[PropertyValue]) {
    def updateAssetSpecificProperty(assetId: Long, propertyId: Long, propertyValues: Seq[PropertyValue]) {
      val asset = getAssetById(assetId)
      if (!userCanModifyAsset(assetId)) {
        throw new IllegalArgumentException("User does not have write access to municipality")
      }
      if (asset.isEmpty) throw new IllegalArgumentException("Asset " + assetId + " not found")
      val createNew = (asset.head.propertyData.exists(_.propertyId == propertyId.toString) && asset.head.propertyData.find(_.propertyId == propertyId.toString).get.values.isEmpty)
      Database.forDataSource(ds).withDynSession {
        Q.query[Long, String](propertyTypeByPropertyId).first(propertyId) match {
          case Text => {
            if (propertyValues.size != 1) throw new IllegalArgumentException("Text property must have exactly one value: " + propertyValues)
            if (createNew) {
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
    }

    def updateCommonAssetProperty(assetId: Long, propertyId: String, propertyValues: Seq[PropertyValue]) {
      val column = AssetPropertyConfiguration.commonAssetPropertyColumns(propertyId)
      Database.forDataSource(ds).withDynSession {
        if (!userCanModifyAsset(assetId)) {
          throw new IllegalArgumentException("User does not have write access to municipality")
        }
        AssetPropertyConfiguration.commonAssetPropertyTypes(propertyId) match {
          case SingleChoice => {
            val newVal = propertyValues.head.propertyValue.toString
            AssetPropertyConfiguration.commonAssetPropertyEnumeratedValues.find { p =>
              (p.propertyId == propertyId) && (p.values.map(_.propertyValue.toString).contains(newVal))
            } match {
              case Some(propValues) => {
                updateCommonProperty(assetId, column, newVal).execute()
              }
              case None => throw new IllegalArgumentException("Invalid property/value: " + propertyId + "/" + newVal)
            }
          }
          case Text => updateCommonProperty(assetId, column, propertyValues.head.propertyDisplayValue).execute()
          case Date => {
            val formatter = ISODateTimeFormat.dateOptionalTimeParser()
            val optionalDateTime = propertyValues.headOption.map(_.propertyDisplayValue).map(formatter.parseDateTime)
            updateCommonDateProperty(assetId, column, optionalDateTime).execute()
          }
          case t: String => throw new UnsupportedOperationException("Asset property type: " + t + " not supported")
        }
      }
    }

    if (AssetPropertyConfiguration.commonAssetPropertyColumns.keySet.contains(propertyId)) {
      updateCommonAssetProperty(assetId, propertyId, propertyValues)
    } else {
      updateAssetSpecificProperty(assetId, propertyId.toLong, propertyValues)
    }
  }

  def deleteAssetProperty(assetId: Long, propertyId: String) {
    if (AssetPropertyConfiguration.commonAssetPropertyColumns.keySet.contains(propertyId)) {
      throw new IllegalArgumentException("Cannot delete common asset property value: " + propertyId)
    }
    val longPropertyId: Long = propertyId.toLong
    Database.forDataSource(ds).withDynSession {
      if (!userCanModifyAsset(assetId)) {
        throw new IllegalArgumentException("User does not have write access to municipality")
      }
      Q.query[Long, String](propertyTypeByPropertyId).first(longPropertyId) match {
        case Text => deleteTextProperty(assetId, longPropertyId).execute()
        case SingleChoice => deleteSingleChoiceProperty(assetId, longPropertyId).execute()
        case MultipleChoice => deleteMultipleChoiceProperty(assetId, longPropertyId).execute()
        case t: String => throw new UnsupportedOperationException("Delete asset not supported for property type: " + t)
      }
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
    Database.forDataSource(ds).withDynSession {
      Q.query[Long, Array[Byte]](imageById).first(imageId)
    }
  }

  def availableProperties(assetTypeId: Long): Seq[Property] = {
    implicit val getPropertyDescription = new GetResult[Property] {
      def apply(r: PositionedResult) = {
        Property(r.nextString(), r.nextString, r.nextString, r.nextBoolean(), Seq())
      }
    }
    AssetPropertyConfiguration.commonAssetPropertyDescriptors.values.toSeq ++ Database.forDataSource(ds).withDynSession {
      sql"""
        select id, name_fi, property_type, required from property where asset_type_id = $assetTypeId
      """.as[Property].list.sortBy(_.propertyId)
    }
  }

}
