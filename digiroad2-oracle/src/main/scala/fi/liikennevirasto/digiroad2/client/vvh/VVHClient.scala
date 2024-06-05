package fi.liikennevirasto.digiroad2.client.vvh

import com.vividsolutions.jts.geom.Polygon
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.{ClientException, Filter, LinkOperationError}
import fi.liikennevirasto.digiroad2.util.{Digiroad2Properties, LogUtils}
import org.apache.commons.codec.binary.Base64
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.slf4j.LoggerFactory

import java.net.URLEncoder
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class ChangeInfo(oldId: Option[String], newId: Option[String], mmlId: Long, changeType: Int,
                      oldStartMeasure: Option[Double], oldEndMeasure: Option[Double], newStartMeasure: Option[Double],
                      newEndMeasure: Option[Double], timeStamp: Long = 0L) {
  def isOldId(id: String): Boolean = {
    oldId.nonEmpty && oldId.get == id
  }
  def affects(id: String, assettimeStamp: Long): Boolean = {
    isOldId(id) && assettimeStamp < timeStamp
  }
}

/**
  * Numerical values for change types from VVH ChangeInfo Api
  */
sealed trait ChangeType {
  def value: Int
}
object ChangeType {
  val values = Set(Unknown, CombinedModifiedPart, CombinedRemovedPart, LengthenedCommonPart,
    LengthenedNewPart, DividedModifiedPart, DividedNewPart, ShortenedCommonPart,
    ShortenedRemovedPart, Removed, New, ReplacedCommonPart, ReplacedNewPart, ReplacedRemovedPart)

  def apply(intValue: Int): ChangeType = {
    values.find(_.value == intValue).getOrElse(Unknown)
  }

  case object Unknown extends ChangeType { def value = 0 }
  case object CombinedModifiedPart extends ChangeType { def value = 1 }
  case object CombinedRemovedPart extends ChangeType { def value = 2 }
  case object LengthenedCommonPart extends ChangeType { def value = 3 }
  case object LengthenedNewPart extends ChangeType { def value = 4 }
  case object DividedModifiedPart extends ChangeType { def value = 5 }
  case object DividedNewPart extends ChangeType { def value = 6 }
  case object ShortenedCommonPart extends ChangeType { def value = 7 }
  case object ShortenedRemovedPart extends ChangeType { def value = 8 }
  case object Removed extends ChangeType { def value = 11 }
  case object New extends ChangeType { def value = 12 }
  case object ReplacedCommonPart extends ChangeType { def value = 13 }
  case object ReplacedNewPart extends ChangeType { def value = 14 }
  case object ReplacedRemovedPart extends ChangeType { def value = 15 }

  /**
    * Return true if this is a replacement where segment or part of it replaces another, older one
    * All changes should be of form (old_id, new_id, old_start, old_end, new_start, new_end) with non-null values
    *
    * @param changeInfo changeInfo object to check
    * @return true, if this is a replacement
    */
  def isReplacementChange(changeInfo: ChangeInfo) = { // Where asset geo location should be replaced with another
    ChangeType.apply(changeInfo.changeType) match {
      case CombinedModifiedPart => true
      case CombinedRemovedPart => true
      case LengthenedCommonPart => true
      case DividedModifiedPart => true
      case DividedNewPart => true
      case ShortenedCommonPart => true
      case ReplacedCommonPart => true
      case Unknown => false
      case LengthenedNewPart => false
      case ShortenedRemovedPart => false
      case Removed => false
      case New => false
      case ReplacedNewPart => false
      case ReplacedRemovedPart => false
    }
  }

  /**
    * Return true if this is an extension where segment or part of it has no previous entry
    * All changes should be of form (new_id, new_start, new_end) with non-null values and old_* fields must be null
    *
    * @param changeInfo changeInfo object to check
    * @return true, if this is an extension
    */
  def isExtensionChange(changeInfo: ChangeInfo) = { // Where asset geo location is a new extension (non-existing)
    ChangeType.apply(changeInfo.changeType) match {
      case LengthenedNewPart => true
      case ReplacedNewPart => true
      case _ => false
    }
  }

  def isDividedChange(changeInfo: ChangeInfo) = {
    ChangeType.apply(changeInfo.changeType) match {
      case DividedModifiedPart => true
      case DividedNewPart => true
      case _ => false
    }
  }

  /**
    * Return true if this is a removed segment or a piece of it. Only old id and m-values should be populated.
    *
    * @param changeInfo changeInfo object to check
    * @return true, if this is a removed segment
    */
  def isRemovalChange(changeInfo: ChangeInfo) = { // Where asset should be removed completely or partially
    ChangeType.apply(changeInfo.changeType) match {
      case Removed => true
      case ReplacedRemovedPart => true
      case ShortenedRemovedPart => true
      case _ => false
    }
  }

  /**
    * Return true if this is a new segment. Only new id and m-values should be populated.
    *
    * @param changeInfo changeInfo object to check
    * @return true, if this is a new segment
    */
  def isCreationChange(changeInfo: ChangeInfo) = { // Where asset geo location should be replaced with another
    ChangeType.apply(changeInfo.changeType) match {
      case New => true
      case _ => false
    }
  }

  def isUnknownChange(changeInfo: ChangeInfo) = {
    ChangeType.Unknown.value == changeInfo.changeType
  }
}

object Filter extends Filter {
  def withCreatedDateFilter(lowerDate: DateTime, higherDate: DateTime): String = {
    withCreatedDateLimitFilter("CREATED_DATE", lowerDate, higherDate)
  }

  def withCreatedDateLimitFilter(attributeName: String, lowerDate: DateTime, higherDate: DateTime): String = {
    val formatter = DateTimeFormat.forPattern("yyyy-MM-dd")
    val since = formatter.print(lowerDate)
    val until = formatter.print(higherDate)

    s""""where":"( $attributeName >=date '$since' and $attributeName <=date '$until' )","""
  }

  override def withFilter[T](attributeName: String, ids: Set[T]): String = {
    val filter =
      if (ids.isEmpty) {
        ""
      } else {
        val query = ids.mkString(",")
        s""""where":"$attributeName IN ($query)","""
      }
    filter
  }

   override def withMunicipalityFilter(municipalities: Set[Int]): String = {
    withFilter("MUNICIPALITYCODE", municipalities)
  }

  override def combineFiltersWithAnd(filter1: String, filter2: String): String = {

    (filter1.isEmpty, filter2.isEmpty) match {
      case (true,true) => ""
      case (true,false) => filter2
      case (false,true) => filter1
      case (false,false) => "%s AND %s".format(filter1.dropRight(2), filter2.replace("\"where\":\"", ""))
    }
  }

  override def combineFiltersWithAnd(filter1: String, filter2: Option[String]): String = {
    combineFiltersWithAnd(filter2.getOrElse(""), filter1)
  }

  /**
    *
    * @param polygon to be converted to string
    * @return string compatible with VVH polygon query
    */
  override def stringifyPolygonGeometry(polygon: Polygon): String = {
    var stringPolygonList: String = ""
    var polygonString: String = "{rings:[["
    polygon.getCoordinates
    if (polygon.getCoordinates.length > 0) {
      for (point <- polygon.getCoordinates.dropRight(1)) {
        // drop removes duplicates
        polygonString += "[" + point.x + "," + point.y + "],"
      }
      polygonString = polygonString.dropRight(1) + "]]}"
      stringPolygonList += polygonString
    }
    stringPolygonList
  }

   override def withMtkClassFilter(ids: Set[Long]): String = {
    withFilter("MTKCLASS", ids)
  }
}

