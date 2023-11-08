package fi.liikennevirasto.digiroad2.util.assetUpdater

import com.github.tototoshi.csv.CSVWriter
import fi.liikennevirasto.digiroad2.asset.{AssetTypeInfo, RoadLinkProperties, UnknownAssetTypeId}
import fi.liikennevirasto.digiroad2.client.RoadLinkChangeType
import fi.liikennevirasto.digiroad2.service.AwsService
import fi.liikennevirasto.digiroad2.util.Digiroad2Properties
import fi.liikennevirasto.digiroad2.{FloatingReason, GeometryUtils, ILinearReference, Point}
import org.joda.time.DateTime
import org.json4s.jackson.Serialization
import org.json4s.{DefaultFormats, Formats}
import org.slf4j.{Logger, LoggerFactory}

import java.io.{PrintWriter, StringWriter}
import java.nio.file.{Files, Paths}

/**
  *  For point like asset mark [[endMValue]] None
  * @param linkId Road Link id
  * @param startMValue start point
  * @param endMValue end point, zero for point assets
  * @param sideCode for linear assets
  * @param validityDirection for point assets
  * @param length asset length, zero for point assets
  */
sealed case class LinearReferenceForReport(linkId: String, startMValue: Double, endMValue: Option[Double], sideCode: Option[Int] = None, validityDirection: Option[Int] = None, length: Double) extends ILinearReference

/**
  * 
  * @param assetId
  * @param values values as string. Convert into json format.
  * @param municipalityCode
  * @param geometry
  * @param linearReference Where asset is in. For floating use None.
  * @param isPointAsset
  */
sealed case class Asset(assetId: Long, values: String, municipalityCode: Option[Int], geometry: Option[Seq[Point]],
                        linearReference: Option[LinearReferenceForReport], isPointAsset: Boolean = false, floatingReason: Option[FloatingReason] = None) {

  def directLink: String = Digiroad2Properties.feedbackAssetsEndPoint
  val logger: Logger = LoggerFactory.getLogger(getClass)
  def geometryToString: String = {
    if (geometry.nonEmpty) {
      if (!isPointAsset) {
        GeometryUtils.toWktLineString(GeometryUtils.toDefaultPrecision(geometry.get)).string
      } else {
        val point = geometry.get.last
        GeometryUtils.toWktPoint(point.x, point.y).string
      }

    } else {
      logger.warn("Asset does not have geometry")
      ""
    }
  }

  def getUrl: String = {
    if (linearReference.nonEmpty) {
      s"""$directLink#linkProperty/${linearReference.get.linkId}"""
    }  else ""
  }

}

sealed trait ChangeType {
  def value: Int
}

object ChangeTypeReport {
  
  case object Creation extends ChangeType {
    def value: Int = 1
  }

  case object Deletion extends ChangeType {
    def value: Int = 2
  }

  case object Divided extends ChangeType {
    def value: Int = 3
  }

  case object Replaced extends ChangeType {
    def value: Int = 4
  }
  case object PropertyChange extends ChangeType {
    def value: Int = 5
  }
  
  /**
    * For point asset
    * */
  case object Move extends ChangeType {
    def value: Int = 6
  }

  /**
    * For point asset
    * */
  case object Floating extends ChangeType {
    def value: Int = 7
  }
}

sealed trait ReportedChange {
  def linkId: String
  def changeType: ChangeType
}

/**
  *
  * @param linkId link in which the changes have been applied
  * @param changeType type of change
  * @param oldValue old property value, optional for functional class and link type as new value can be generated
  * @param newValue new property value, no value if the link is removed
  * @param source source for new functionalClass or linkType, either "oldLink" or "mtkClass"
  */
case class AdministrativeClassChange(linkId: String, changeType: ChangeType, oldValue: Int, newValue: Option[Int]) extends ReportedChange
case class TrafficDirectionChange(linkId: String, changeType: ChangeType, oldValue: Int, newValue: Option[Int]) extends ReportedChange
case class RoadLinkAttributeChange(linkId: String, changeType: ChangeType, oldValues: Map[String, String], newValues: Map[String, String]) extends ReportedChange
case class FunctionalClassChange(linkId: String, changeType: ChangeType, oldValue: Option[Int], newValue: Option[Int], source: String = "") extends ReportedChange
case class LinkTypeChange(linkId: String, changeType: ChangeType, oldValue: Option[Int], newValue: Option[Int], source: String = "") extends ReportedChange


/**
  * 
  * @param linkId     link where changes is happening TODO remove if not needed
  * @param assetId    asset which is under samuutus, When there is more than one asset under samuutus (e.g merger or split) create new  [[ChangedAsset]] item for each asset.
  * @param changeType characteristic of change
  * @param before     situation before samuutus
  * @param after      after samuutus
  * */
case class ChangedAsset(linkId: String, assetId: Long, changeType: ChangeType, roadLinkChangeType: RoadLinkChangeType, before: Option[Asset], after: Seq[Asset]) extends ReportedChange

/**
  *
  * @param assetType
  * @param changes
  */
case class ChangeReport(assetType: Int, changes: Seq[ReportedChange])

object ChangeReporter {

  lazy val awsService = new AwsService
  lazy val s3Service: awsService.S3.type = awsService.S3
  lazy val s3Bucket: String = Digiroad2Properties.samuutusReportsBucketName
  val logger: Logger = LoggerFactory.getLogger(getClass)
  implicit lazy val serializationFormats: Formats = DefaultFormats
  def directLink: String = Digiroad2Properties.feedbackAssetsEndPoint
  val localReportDirectoryName = "samuutus-reports-local-test"



  private def getCSVRowForRoadLinkPropertyChanges(linkId: String, changeType: Int, changes: Seq[ReportedChange]) = {
    def getUrl(linkId: String): String = {
      if (linkId != null) s"""$directLink#linkProperty/${linkId}""" else null
    }

    val trafficDirectionChange = changes.find(_.isInstanceOf[TrafficDirectionChange])
    val (oldTrafficDirection, newTrafficDirection) = trafficDirectionChange match {
      case trChange: Some[TrafficDirectionChange] =>
        val oldValue = trChange.get.oldValue
        val newValue = trChange.get.newValue match {
          case Some(value) => value
          case _ => null
        }
        (oldValue, newValue)
      case _ => (null, null)
    }
    val adminClassChange = changes.find(_.isInstanceOf[AdministrativeClassChange])
    val (oldAdminClass, newAdminClass) = adminClassChange match {
      case acChange: Some[AdministrativeClassChange] =>
        val oldValue = acChange.get.oldValue
        val newValue = acChange.get.newValue match {
          case Some(value) => value
          case _ => null
        }
        (oldValue, newValue)
      case _ => (null, null)
    }
    val functionalClassChange = changes.find(_.isInstanceOf[FunctionalClassChange])
    val (oldFunctionalClass, newFunctionalClass, fcSource) = functionalClassChange match {
      case fcChange: Some[FunctionalClassChange] =>
        val oldValue = fcChange.get.oldValue match {
          case Some(value) => value
          case _ => null
        }
        val newValue = fcChange.get.newValue match {
          case Some(value) => value
          case _ => null
        }
        val source = fcChange.get.source
        (oldValue, newValue, source)
      case _ => (null, null, null)
    }
    val linkTypeChange = changes.find(_.isInstanceOf[LinkTypeChange])
    val (oldLinkType, newLinkType, ltSource) = linkTypeChange match {
      case ltChange: Some[LinkTypeChange] =>
        val oldValue = ltChange.get.oldValue match {
          case Some(value) => value
          case _ => null
        }
        val newValue = ltChange.get.newValue match {
          case Some(value) => value
          case _ => null
        }
        val source = ltChange.get.source
        (oldValue, newValue, source)
      case _ => (null, null, null)
    }
    val attributeChange = changes.find(_.isInstanceOf[RoadLinkAttributeChange])
    val (oldAttributes, newAttributes) = attributeChange match {
      case attributeChange: Some[RoadLinkAttributeChange] =>
        (Serialization.write(attributeChange.get.oldValues), Serialization.write(attributeChange.get.newValues))

      case _ => (null, null)
    }
    val url = getUrl(linkId)
    Seq(linkId, url, changeType, oldTrafficDirection, newTrafficDirection, oldAdminClass, newAdminClass, oldFunctionalClass,
      newFunctionalClass, fcSource, oldLinkType, newLinkType, ltSource, oldAttributes, newAttributes)
  }

  private def getCSVRowForPointAssetChanges(change: ReportedChange, assetTypeId: Int, withGeometry: Boolean = false) = {
    try {
      val changedAsset = change.asInstanceOf[ChangedAsset]
      val assetBefore = changedAsset.before.get
      val (beforeLinkId, beforeStartMValue, beforeEndMValue, beforeValidityDirection, beforeLength) = assetBefore.linearReference match {
        case Some(linearReference: LinearReferenceForReport) =>
          val linRefEndMValue = linearReference.endMValue match {
            case Some(value) => Some(value)
            case _ => None
          }
          (linearReference.linkId,linearReference.startMValue, linRefEndMValue.getOrElse(null),
            linearReference.validityDirection.getOrElse(null), linearReference.length)
        case _ =>
          (null, null, null, null, null)
      }
      val beforeGeometry = assetBefore.geometryToString
      changedAsset.after.map { assetAfter =>
        val (afterLinkId, afterStartMValue, afterEndMValue, afterValidityDirection, afterLength) = assetAfter.linearReference match {
          case Some(linearReference: LinearReferenceForReport) =>
            val linRefEndMValue = linearReference.endMValue match {
              case Some(value) => Some(value)
              case _ => None
            }
            (linearReference.linkId, linearReference.startMValue, linRefEndMValue.getOrElse(null),
              linearReference.validityDirection.getOrElse(null), linearReference.length)
          case _ =>
            (null, null, null, null, null)
        }
        val afterGeometry = assetAfter.geometryToString
        val floatingReason = assetAfter.floatingReason match {
          case Some(fr) => fr.value
          case _ => null
        }
        val csvRow = Seq(assetTypeId, changedAsset.changeType.value, floatingReason, changedAsset.roadLinkChangeType.value,
          assetBefore.assetId, beforeGeometry, assetBefore.values, assetBefore.municipalityCode.getOrElse(null),
          beforeValidityDirection, beforeLinkId, beforeStartMValue, beforeEndMValue, beforeLength, assetBefore.getUrl,
          assetAfter.assetId,  afterGeometry,  assetAfter.values, assetAfter.municipalityCode.getOrElse(null),
          afterValidityDirection, afterLinkId, afterStartMValue, afterEndMValue, afterLength, assetAfter.getUrl)
        if (withGeometry) {
          csvRow
        } else {
          csvRow.slice(0,5) ++ csvRow.slice(6, 15) ++ csvRow.slice(16, csvRow.size)
        }
      }
    } catch {
      case e: Throwable =>
        logger.error(s"csv conversion failed due to ${e.getMessage}")
        Seq(Seq())
    }
  }

  private def getCSVRowsForLinearAssetChange(change: ReportedChange, assetTypeId: Int, withGeometry: Boolean = false) = {
    try {
      val changedAsset = change.asInstanceOf[ChangedAsset]
      val assetBefore = changedAsset.before
      val metaFields = Seq(assetTypeId, changedAsset.changeType.value, changedAsset.roadLinkChangeType.value)
      val beforeFields = assetBefore match {
        case Some(before) =>
          val linearReference = before.linearReference.get
          Seq(before.assetId, before.geometryToString, before.values, before.municipalityCode.getOrElse(0), linearReference.sideCode.getOrElse(0), linearReference.linkId,
            linearReference.startMValue.toString, linearReference.endMValue.getOrElse(0).toString, linearReference.length.toString, before.getUrl)
        case None => Seq("", "", "", "", "", "", "","", "", "")
      }
      val beforeFieldsWithoutGeometry = beforeFields.patch(1, Nil, 1)
      if (changedAsset.after.isEmpty) {
        val emptyAfterFields =  Seq("", "", "", "", "", "", "","", "", "")
        if(withGeometry) Seq(metaFields ++ beforeFields ++ emptyAfterFields)
        else Seq(metaFields ++ beforeFieldsWithoutGeometry ++ emptyAfterFields)
      } else {
        changedAsset.after.map { after =>
          val linearReference = after.linearReference.get
          val afterFields = Seq(after.assetId, after.geometryToString, after.values, after.municipalityCode.get, linearReference.sideCode.get, linearReference.linkId,
            linearReference.startMValue.toString, linearReference.endMValue.get.toString, linearReference.length.toString, after.getUrl)
          val afterFieldsWithoutGeometry = afterFields.patch(1, Nil, 1)
          if (withGeometry) {
            metaFields ++ beforeFields ++ afterFields
          } else {
            metaFields ++ beforeFieldsWithoutGeometry ++ afterFieldsWithoutGeometry
          }
        }
      }
    } catch {
      case e: Throwable =>
        logger.error(s"csv conversion failed due to ${e.getMessage}")
        Seq(Seq())
    }
  }

  def generateCSV(changeReport: ChangeReport, withGeometry: Boolean = false): (String, Int) = {
    val stringWriter = new StringWriter()
    val csvWriter = new CSVWriter(stringWriter)
    csvWriter.writeRow(Seq("sep=,"))

    val (assetTypeId, changes) = (changeReport.assetType, changeReport.changes)
    val linkIds = changes.map(_.linkId).toSet
    val contentRows = AssetTypeInfo(assetTypeId) match {
      case UnknownAssetTypeId => throw new IllegalArgumentException("Can not generate report for unknown asset type")
      case RoadLinkProperties =>
        val labels = Seq("linkId", "url", "changeType", "oldTrafficDirection", "newTrafficDirection", "oldAdminClass", "newAdminClass", "oldFunctionalClass",
          "newFunctionalClass", "functionalClassSource", "oldLinkType", "newLinkType", "linkTypeSource", "oldLinkAttributes", "newLinkAttributes")
        csvWriter.writeRow(labels)
        val groupedChanges = changes.groupBy(_.linkId)
        linkIds.foreach { linkId =>
          groupedChanges.get(linkId) match {
            case Some(propertyChangesForLink) =>
              val changeType = propertyChangesForLink.head.changeType
              val csvRow = getCSVRowForRoadLinkPropertyChanges(linkId, changeType.value, propertyChangesForLink)
              csvWriter.writeRow(csvRow)
            case _ => //do nothing
          }
        }
        linkIds.size
      case assetTypeInfo: AssetTypeInfo if assetTypeInfo.geometryType == "point" =>
        val labels = Seq("asset_type_id", "change_type", "floating_reason", "roadlink_change", "before_asset_id",
          "before_geometry", "before_value", "before_municipality_code", "before_validity_direction", "before_link_id",
          "before_start_m_value", "before_end_m_value", "before_length", "before_roadlink_url", "after_asset_id",
          "after_geometry", "after_value", "after_municipality_code", "after_validity_direction", "after_link_id",
          "after_start_m_value", "after_end_m_value", "after_length", "after_roadlink_url")
        val labelsWithoutGeometry = labels.slice(0, 5) ++ labels.slice(6, 15) ++ labels.slice(16, labels.size)
        if (withGeometry) csvWriter.writeRow(labels) else csvWriter.writeRow(labelsWithoutGeometry)
        val contentRowCount = changes.map { change =>
          val csvRows = getCSVRowForPointAssetChanges(change, assetTypeId, withGeometry)
          csvRows.foreach { csvRow =>
            csvWriter.writeRow(csvRow)
          }
          csvRows.size
        }.sum
        contentRowCount
      case assetTypeInfo: AssetTypeInfo if assetTypeInfo.geometryType == "linear" =>
        val labels = Seq("asset_type_id", "change_type", "roadlink_change", "before_asset_id",
          "before_geometry", "before_value", "before_municipality_code", "before_side_code", "before_link_id",
          "before_start_m_value", "before_end_m_value", "before_length", "before_roadlink_url", "after_asset_id",
          "after_geometry", "after_value", "after_municipality_code", "after_side_code", "after_link_id",
          "after_start_m_value", "after_end_m_value", "after_length", "after_roadlink_url")
        val labelsWithoutGeometry = labels.filterNot(_.contains("geometry"))
        if (withGeometry) csvWriter.writeRow(labels) else csvWriter.writeRow(labelsWithoutGeometry)
        val contentRowCount = changes.map { change =>
          val csvRows = getCSVRowsForLinearAssetChange(change, assetTypeId, withGeometry)
          csvRows.foreach { csvRow =>
            csvWriter.writeRow(csvRow)
          }
          csvRows.size
        }.sum
        contentRowCount
    }
    (stringWriter.toString, contentRows)
  }

  def saveReportToS3(assetName: String, changesProcessedUntil: DateTime, body: String, contentRowCount: Int,
                     hasGeometry: Boolean = false): Unit = {
    val date = DateTime.now().toString("YYYY-MM-dd")
    val untilDate = changesProcessedUntil.toString("YYYY-MM-dd")
    val withGeometry = if (hasGeometry) "_withGeometry" else ""
    val path = s"$date/${assetName}_${untilDate}_${contentRowCount}content_rows$withGeometry.csv"
    s3Service.saveFileToS3(s3Bucket, path, body, "csv")
  }

  // Used for testing CSV report. Saves file locally to directory 'samuutus-reports-local-test' created in project root directory
  def saveReportToLocalFile(assetName: String, changesProcessedUntil: DateTime, body: String, contentRowCount: Int,
                            hasGeometry: Boolean = false): Unit = {
    val date = DateTime.now().toString("YYYY-MM-dd")
    val untilDate = changesProcessedUntil.toString("YYYY-MM-dd")
    val withGeometry = if (hasGeometry) "_withGeometry" else ""
    Files.createDirectories(Paths.get(localReportDirectoryName, date))
    val path = s"$localReportDirectoryName/$date/${assetName}_${untilDate}_${contentRowCount}content_rows$withGeometry.csv"
    new PrintWriter(path) {
      write(body)
      close()
    }
  }
}
