package fi.liikennevirasto.digiroad2.client.vvh

import com.vividsolutions.jts.geom.Polygon
import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.Filter.withMtkClassFilter
import fi.liikennevirasto.digiroad2.linearasset.RoadLinkLike
import fi.liikennevirasto.digiroad2.util.{Digiroad2Properties, LogUtils}
import org.apache.commons.codec.binary.Base64
import org.apache.http.NameValuePair
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.{HttpGet, HttpPost}
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.message.BasicNameValuePair
import org.joda.time.format.DateTimeFormat
import org.joda.time.{DateTime, DateTimeZone}
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization
import org.slf4j.LoggerFactory

import java.net.URLEncoder
import java.util.ArrayList
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

sealed trait FeatureClass
object FeatureClass {
  case object TractorRoad extends FeatureClass
  case object DrivePath extends FeatureClass
  case object CycleOrPedestrianPath extends FeatureClass
  case object WinterRoads extends FeatureClass
  case object SpecialTransportWithoutGate extends FeatureClass
  case object SpecialTransportWithGate extends FeatureClass
  case object CarRoad_IIIa extends FeatureClass
  case object CarRoad_IIIb extends FeatureClass
  case object AllOthers extends FeatureClass
}

sealed trait NodeType {
  def value: Int
}

object NodeType {
  val values = Set[NodeType](ClosedTrafficArea, Intersection, RailwayCrossing, PseudoNode, EndOfTheRoad,
    RoundaboutNodeType, TrafficedSquare, RoadServiceArea, LightTrafficJunction, UnknownNodeType)

  def apply(value: Int): NodeType = {
    values.find(_.value == value).getOrElse(UnknownNodeType)
  }

  case object ClosedTrafficArea extends NodeType { def value = 1 }
  case object Intersection extends NodeType { def value = 2 }
  case object RailwayCrossing extends NodeType { def value = 3 }
  case object PseudoNode extends NodeType { def value = 4 }
  case object EndOfTheRoad extends NodeType { def value = 5 }
  case object RoundaboutNodeType extends NodeType { def value = 6 }
  case object TrafficedSquare extends NodeType { def value = 7 }
  case object RoadServiceArea extends NodeType { def value = 8 }
  case object LightTrafficJunction extends NodeType { def value = 9 }
  case object UnknownNodeType extends NodeType { def value = 99 }
}
case class RoadLinkFetched(linkId: String, municipalityCode: Int, geometry: Seq[Point],
                           administrativeClass: AdministrativeClass, trafficDirection: TrafficDirection,
                           featureClass: FeatureClass, modifiedAt: Option[DateTime] = None, attributes: Map[String, Any] = Map(),
                           constructionType: ConstructionType = ConstructionType.InUse, linkSource: LinkGeomSource = LinkGeomSource.NormalLinkInterface, length: Double = 0.0) extends RoadLinkLike {
  def roadNumber: Option[String] = attributes.get("ROADNUMBER").map(_.toString)
  def verticalLevel: Option[String] = attributes.get("VERTICALLEVEL").map(_.toString)
  val timeStamp = attributes.getOrElse("LAST_EDITED_DATE", attributes.getOrElse("CREATED_DATE", BigInt(0))).asInstanceOf[BigInt].longValue()
}

case class ChangeInfo(oldId: Option[String], newId: Option[String], mmlId: Long, changeType: Int,
                      oldStartMeasure: Option[Double], oldEndMeasure: Option[Double], newStartMeasure: Option[Double],
                      newEndMeasure: Option[Double], vvhTimeStamp: Long = 0L) {
  def isOldId(id: String): Boolean = {
    oldId.nonEmpty && oldId.get == id
  }
  def affects(id: String, assetVvhTimeStamp: Long): Boolean = {
    isOldId(id) && assetVvhTimeStamp < vvhTimeStamp
  }
}

case class HistoryRoadLink(linkId: String, municipalityCode: Int, geometry: Seq[Point], administrativeClass: AdministrativeClass,
                           trafficDirection: TrafficDirection, featureClass: FeatureClass, createdDate:BigInt, endDate: BigInt, attributes: Map[String, Any] = Map(),
                           constructionType: ConstructionType = ConstructionType.InUse, linkSource: LinkGeomSource = LinkGeomSource.NormalLinkInterface, length: Double = 0.0) extends RoadLinkLike {
  def roadNumber: Option[String] = attributes.get("ROADNUMBER").map(_.toString)
  val timeStamp: Long = attributes.getOrElse("LAST_EDITED_DATE", createdDate).asInstanceOf[BigInt].longValue()
}

case class RoadNodesFetched(objectId: Long, geometry: Point, nodeId: Long, formOfNode: NodeType, municipalityCode: Int, subtype: Int)

/**
  * Numerical values for change types from VVH ChangeInfo Api
  */
sealed trait ChangeType {
  def value: Int
}
object ChangeType {
  val values = Set(Unknown, CombinedModifiedPart, CombinedRemovedPart, LengthenedCommonPart, LengthenedNewPart, DividedModifiedPart, DividedNewPart, ShortenedCommonPart, ShortenedRemovedPart, Removed, New, ReplacedCommonPart, ReplacedNewPart, ReplacedRemovedPart)

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

trait Filter {
  def withFilter[T](attributeName: String, ids: Set[T]): String = ???

  def withMunicipalityFilter(municipalities: Set[Int]): String = ???

  def withRoadNameFilter[T](attributeName: String, names: Set[T]): String = ???

  def combineFiltersWithAnd(filter1: String, filter2: String): String = ???

  def combineFiltersWithAnd(filter1: String, filter2: Option[String]): String = ???

  /**
    *
    * @param polygon to be converted to string
    * @return string compatible with VVH polygon query
    */
  def stringifyPolygonGeometry(polygon: Polygon): String = ???

  // Query filters methods
  def withLinkIdFilter[T](linkIds: Set[T]): String = ???

  def withFinNameFilter(roadNameSource: String)(roadNames: Set[String]): String = ???

  def withMmlIdFilter(mmlIds: Set[Long]): String = ???

  def withMtkClassFilter(ids: Set[Long]): String = ???

  def withLastEditedDateFilter(lowerDate: DateTime, higherDate: DateTime): String = ???

  def withDateLimitFilter(attributeName: String, lowerDate: DateTime, higherDate: DateTime): String = ???

}

object Filter extends Filter {

  def anyToDouble(number: Any): Option[Double] = number match {
    case bi: BigInt => Some(bi.toDouble)
    case i: Int => Some(i.toDouble)
    case l: Long => Some(l.toDouble)
    case d: Double => Some(d)
    case _ => None
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

   override def withRoadNameFilter[T](attributeName: String, names: Set[T]): String = {
    val filter =
      if (names.isEmpty) {
        ""
      } else {
        val query = names.mkString("','")
        s""""where":"$attributeName IN ('$query')","""
      }
    filter
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

  // Query filters methods
   override def withLinkIdFilter[T](linkIds: Set[T]): String = {
    withFilter("LINKID", linkIds)
  }

   override def withFinNameFilter(roadNameSource: String)(roadNames: Set[String]): String = {
    withRoadNameFilter(roadNameSource, roadNames)
  }

   override def withMmlIdFilter(mmlIds: Set[Long]): String = {
    withFilter("MTKID", mmlIds)
  }

   override def withMtkClassFilter(ids: Set[Long]): String = {
    withFilter("MTKCLASS", ids)
  }

  override def withLastEditedDateFilter(lowerDate: DateTime, higherDate: DateTime): String = {
    withDateLimitFilter("LAST_EDITED_DATE", lowerDate, higherDate)
  }

  override def withDateLimitFilter(attributeName: String, lowerDate: DateTime, higherDate: DateTime): String = {
    val formatter = DateTimeFormat.forPattern("yyyy-MM-dd")
    val since = formatter.print(lowerDate)
    val until = formatter.print(higherDate)

    s""""where":"( $attributeName >=date '$since' and $attributeName <=date '$until' )","""
  }
}

object RoadLinkClient {
  /**
    * Create a pseudo VVH time stamp when an asset is created or updated and is on the current road geometry.
    * This prevents change info from being applied to the recently created asset. Resolution is one day.
    * @param offsetHours Offset to the timestamp. Defaults to 5 which reflects to VVH offset for batch runs.
    * @return VVH timestamp for current date
    */
  def createVVHTimeStamp(offsetHours: Int = 5): Long = {
    val oneHourInMs = 60 * 60 * 1000L
    val utcTime = DateTime.now().minusHours(offsetHours).getMillis
    val curr = utcTime + DateTimeZone.getDefault.getOffset(utcTime)
    curr - (curr % (24L*oneHourInMs))
  }
}

class RoadLinkClient(vvhRestApiEndPoint: String) {
  lazy val roadLinkData: OldVVHRoadLinkClient = new OldVVHRoadLinkClient(vvhRestApiEndPoint)
  lazy val frozenTimeRoadLinkData: VVHFrozenTimeRoadLinkClientServicePoint = new VVHFrozenTimeRoadLinkClientServicePoint(vvhRestApiEndPoint)
  lazy val roadLinkChangeInfo: VVHChangeInfoClient = new VVHChangeInfoClient(vvhRestApiEndPoint)
  lazy val complementaryData: VVHComplementaryClient = new VVHComplementaryClient(vvhRestApiEndPoint)
  lazy val historyData: VVHHistoryClient = new VVHHistoryClient(vvhRestApiEndPoint)
  lazy val roadNodesData: VVHRoadNodesClient = new VVHRoadNodesClient(vvhRestApiEndPoint)

  def fetchRoadLinkByLinkId(linkId: String): Option[RoadLinkFetched] = {
    roadLinkData.fetchByLinkId(linkId) match {
      case Some(vvhRoadLink) => Some(vvhRoadLink)
      case None => complementaryData.fetchByLinkId(linkId)
    }
  }
  
  def createVVHTimeStamp(offsetHours: Int = 5): Long = {
    RoadLinkClient.createVVHTimeStamp(offsetHours)
  }
}

case class LinkOperationError(content: String, statusCode:String) extends Exception(s"Content: ${content}, Status code: ${statusCode}")
class ClientException(response: String) extends RuntimeException(response)

trait LinkOperationsAbstract {
  type LinkType
  type Content
  protected val linkGeomSource: LinkGeomSource
  protected def restApiEndPoint: String
  protected def serviceName: String
  
  protected implicit val jsonFormats: Formats = DefaultFormats

  lazy val logger = LoggerFactory.getLogger(getClass)

  protected def queryByMunicipalitiesAndBounds(bounds: BoundingRectangle, municipalities: Set[Int],
                                               filter: Option[String]): Seq[LinkType] = ???

  protected def queryByMunicipality(municipality: Int, filter: Option[String] = None): Seq[LinkType] = ???

  protected def queryByPolygons(polygon: Polygon): Seq[LinkType] = ???

  protected def queryLinksIdByPolygons(polygon: Polygon): Seq[String] = ???

  protected def queryByLinkIds[LinkType](linkIds: Set[String], filter: Option[String] = None): Seq[LinkType] = ???

  protected def queryByMunicipalitiesAndBounds(bounds: BoundingRectangle, municipalities: Set[Int]): Seq[LinkType] = {
    queryByMunicipalitiesAndBounds(bounds, municipalities, None)
  }
}

class VVHAuthPropertyReader {
  private def getUsername: String = {
    val loadedKeyString = Digiroad2Properties.vvhRestUsername
    if (loadedKeyString == null)
      throw new IllegalArgumentException("Missing OAG username")
    loadedKeyString
  }

  private def getPassword: String = {
    val loadedKeyString = Digiroad2Properties.vvhRestPassword
    if (loadedKeyString == null)
      throw new IllegalArgumentException("Missing OAG Password")
    loadedKeyString
  }

  def getAuthInBase64: String = {
    Base64.encodeBase64String((getUsername + ":" + getPassword).getBytes)
  }
}


class KgvRoadLinkClient(collection: Option[KgvCollection] = None,linkGeomSourceValue:Option[LinkGeomSource] = None) extends KgvOperation {

  override type LinkType = RoadLinkFetched
  override def restApiEndPoint: String = Digiroad2Properties.kgvEndpoint
  override protected val serviceName = collection.getOrElse(throw new ClientException("Collection is not defined") ).value
  override protected val linkGeomSource: LinkGeomSource = linkGeomSourceValue.getOrElse(throw new ClientException("LinkGeomSource is not defined") )
  val filter:Filter = FilterOgc

  def createVVHTimeStamp(offsetHours: Int = 5): Long =  RoadLinkClient.createVVHTimeStamp(offsetHours)

  def fetchByMunicipality(municipality: Int): Seq[LinkType] = {
    queryByMunicipality(municipality)
  }

  def fetchByMunicipalityF(municipality: Int): Future[Seq[LinkType]] = {
    Future(queryByMunicipality(municipality))
  }
  
  def fetchByMunicipalitiesAndBounds(bounds: BoundingRectangle, municipalities: Set[Int]): Seq[LinkType] = {
    queryByMunicipalitiesAndBounds(bounds, municipalities)
  }

  def fetchByBounds(bounds: BoundingRectangle): Seq[LinkType] = {
    queryByMunicipalitiesAndBounds(bounds, Set[Int]())
  }
  
  def fetchByMunicipalitiesAndBoundsF(bounds: BoundingRectangle, municipalities: Set[Int]): Future[Seq[LinkType]] = {
    Future(queryByMunicipalitiesAndBounds(bounds, municipalities))
  }
  
  def fetchByLinkId(linkId: String): Option[LinkType] = fetchByLinkIds(Set(linkId)).headOption
  
  def fetchByLinkIds(linkIds: Set[String]): Seq[LinkType] = {
    queryByLinkIds[LinkType](linkIds)
  }

  def fetchByLinkIdsF(linkIds: Set[String]) = Future(fetchByLinkIds(linkIds))
  
  def fetchVVHRoadlinks[LinkType](linkIds: Set[String]): Seq[LinkType] =
    queryByLinkIds[LinkType](linkIds)
  
  def fetchByChangesDates(lowerDate: DateTime, higherDate: DateTime): Seq[LinkType] = {
    queryByLastEditedDate(lowerDate,higherDate)
  }

  def fetchByDatetime(lowerDate: DateTime, higherDate: DateTime): Seq[LinkType] = {
    queryByDatetimeAndFilter(lowerDate,higherDate)
  }

  def fetchByPolygonF(polygon : Polygon): Future[Seq[LinkType]] = {
    Future(queryByPolygons(polygon))
  }

  def fetchLinkIdsByPolygonF(polygon : Polygon): Future[Seq[String]] = {
    Future(queryLinksIdByPolygons(polygon))
  }
  def fetchWalkwaysByBoundsAndMunicipalitiesF(bounds: BoundingRectangle, municipalities: Set[Int]): Future[Seq[LinkType]] = {
    Future(queryByMunicipalitiesAndBounds(bounds, municipalities, Some(filter.withMtkClassFilter(Set(12314)))))
  }

  def fetchWalkwaysByMunicipalitiesF(municipality: Int): Future[Seq[LinkType]] =
    Future(queryByMunicipality(municipality, Some(filter.withMtkClassFilter(Set(12314)))))

  def fetchByMmlIds(toSet: Set[Long]): Seq[LinkType] = queryByFilter(Some(filter.withMmlIdFilter(toSet)))

  def fetchByMmlId(mmlId: Long) : Option[LinkType]= fetchByMmlIds(Set(mmlId)).headOption
}

trait VVHClientOperations {

  protected val linkGeomSource: LinkGeomSource
  protected def restApiEndPoint: String
  protected def serviceName: String
  protected val disableGeometry: Boolean

  protected implicit val jsonFormats: Formats = DefaultFormats
  
  type LinkType
  type Content
  
  protected def mapFields(content: Map[String, Any], url: String): Either[List[Map[String, Any]], LinkOperationError]
  protected def defaultOutFields(): String
  protected def extractFeature(feature: Map[String, Any]): LinkType

  lazy val logger = LoggerFactory.getLogger(getClass)

  // TODO: Temporary parsing of link ids from string to long. Remove after not needed anymore
  def parseLinkIdsToLong(linkIds: Set[String]): Set[Long] = {
    linkIds.flatMap(linkId =>
      try {
        Some(linkId.toLong)
      } catch {
        case _: Throwable =>
          logger.error(s"Unable to convert link $linkId to long type")
          None
      }
    )
  }

  protected def queryParameters(fetchGeometry: Boolean = true): String = {
    if (fetchGeometry && !disableGeometry) "returnGeometry=true&returnZ=true&returnM=true&geometryPrecision=3&f=pjson"
    else "returnGeometry=false&f=pjson"
  }

  protected def serviceUrl = restApiEndPoint + serviceName + "/FeatureServer/query"

  protected def serviceUrl(bounds: BoundingRectangle, definition: String, parameters: String): String = {
    serviceUrl +
      s"?layerDefs=$definition&geometry=" + bounds.leftBottom.x + "," + bounds.leftBottom.y + "," + bounds.rightTop.x + "," + bounds.rightTop.y +
      s"&geometryType=esriGeometryEnvelope&spatialRel=esriSpatialRelIntersects&$parameters"

  }

  protected def serviceUrl(polygon: Polygon, definition: String, parameters: String): String = {
    val polygonString = Filter.stringifyPolygonGeometry(polygon)
    serviceUrl +
      s"?layerDefs=$definition&geometry=" + URLEncoder.encode(polygonString) +
      s"&geometryType=esriGeometryPolygon&spatialRel=esriSpatialRelIntersects&$parameters"
  }

  protected def serviceUrl(definition: String, parameters: String): String = {
    serviceUrl +
      s"?layerDefs=$definition&" + parameters
  }

  protected def layerDefinitionWithoutEncoding(filter: String, customFieldSelection: Option[String] = None): String = {
    val definitionStart = "[{"
    val layerSelection = """"layerId":0,"""
    val fieldSelection = customFieldSelection match {
      case Some(fs) => s""""outFields":"""" + fs + """,CONSTRUCTIONTYPE""""
      case _ => s""""outFields":"""" + defaultOutFields + """""""
    }
    val definitionEnd = "}]"
    definitionStart + layerSelection + filter + fieldSelection + definitionEnd
  }

  protected def layerDefinition(filter: String, customFieldSelection: Option[String] = None): String = {
    URLEncoder.encode(layerDefinitionWithoutEncoding(filter, customFieldSelection), "UTF-8")
  }

  protected def fetchVVHFeatures(url: String): Either[List[Map[String, Any]], LinkOperationError] = {
    val fetchVVHStartTime = System.currentTimeMillis()
    val request = new HttpGet(url)
    val client = HttpClientBuilder.create().build()
    val vVHAuthPropertyReader = new VVHAuthPropertyReader
    request.addHeader("Authorization", "Basic " + vVHAuthPropertyReader.getAuthInBase64)

    val response = client.execute(request)
    try {
      response.getStatusLine.getStatusCode match {
        case 200 => mapFields(parse(StreamInput(response.getEntity.getContent)).values.asInstanceOf[Map[String, Any]], url)
        case _ => throw new ClientException(response.toString)
      }
    } finally {
      response.close()
      val fetchVVHTimeSec = (System.currentTimeMillis() - fetchVVHStartTime) * 0.001
      if (fetchVVHTimeSec > LogUtils.timeLoggingThresholdInMs * 0.001)
        logger.info("fetch vvh took %.3f sec with the following url %s".format(fetchVVHTimeSec, url))
    }
  }

  protected def fetchVVHFeatures(url: String, formparams: ArrayList[NameValuePair]): Either[List[Map[String, Any]], LinkOperationError] = {
    val fetchVVHStartTime = System.currentTimeMillis()
    val request = new HttpPost(url)
    request.setEntity(new UrlEncodedFormEntity(formparams, "utf-8"))
    val client = HttpClientBuilder.create().build()

    val vVHAuthPropertyReader = new VVHAuthPropertyReader
    request.addHeader("Authorization", "Basic " + vVHAuthPropertyReader.getAuthInBase64)

    val response = client.execute(request)
    try {
      response.getStatusLine.getStatusCode match {
        case 200 => mapFields(parse(StreamInput(response.getEntity.getContent)).values.asInstanceOf[Map[String, Any]], url)
        case _ => throw new ClientException(response.toString)
      }
    } finally {
      response.close()
      val fetchVVHTimeSec = (System.currentTimeMillis() - fetchVVHStartTime) * 0.001
      if (fetchVVHTimeSec > LogUtils.timeLoggingThresholdInMs * 0.001)
        logger.info("fetch vvh took %.3f sec with the following url %s".format(fetchVVHTimeSec, url))
    }
  }

  protected def extractFeatureAttributes(feature: Map[String, Any]): Map[String, Any] = {
    feature("attributes").asInstanceOf[Map[String, Any]]
  }

  protected def extractFeatureGeometry(feature: Map[String, Any]): List[List[Double]] = {
    if (feature.contains("geometry")) {
      val geometry = feature("geometry").asInstanceOf[Map[String, Any]]
      val paths = geometry("paths").asInstanceOf[List[List[List[Double]]]]
      paths.reduceLeft((geom, nextPart) => geom ++ nextPart.tail)
    }
    else List.empty
  }


  protected def extractModifiedAt(attributes: Map[String, Any]): Option[DateTime] = {
    def compareDateMillisOptions(a: Option[Long], b: Option[Long]): Option[Long] = {
      (a, b) match {
        case (Some(firstModifiedAt), Some(secondModifiedAt)) =>
          Some(Math.max(firstModifiedAt, secondModifiedAt))
        case (Some(firstModifiedAt), None) => Some(firstModifiedAt)
        case (None, Some(secondModifiedAt)) => Some(secondModifiedAt)
        case (None, None) => None
      }
    }

    val validFromDate = Option(attributes("VALIDFROM").asInstanceOf[BigInt]).map(_.toLong)
    var lastEditedDate: Option[Long] = Option(0)
    if (attributes.contains("LAST_EDITED_DATE")) {
      lastEditedDate = Option(attributes("LAST_EDITED_DATE").asInstanceOf[BigInt]).map(_.toLong)
    }
    var geometryEditedDate: Option[Long] = Option(0)
    if (attributes.contains("GEOMETRY_EDITED_DATE")) {
      geometryEditedDate = Option(attributes("GEOMETRY_EDITED_DATE").asInstanceOf[BigInt]).map(_.toLong)
    }

    val latestDate = compareDateMillisOptions(lastEditedDate, geometryEditedDate)
    latestDate.orElse(validFromDate).map(modifiedTime => new DateTime(modifiedTime))
  }

  /**
    * Extract double value from VVH data. Used for change info start and end measures.
    */
  protected def extractMeasure(value: Any): Option[Double] = {
    value match {
      case null => None
      case _ => Some(value.toString.toDouble)
    }
  }

  /**
    * Creates a pseudo VVH Timestamp for new assets and speed limits. Turns clock back to 0:00 on the same day
    * if less than offsetHours have passed since or 0:00 on previous day if not.
    *
    * @param offsetHours Number of hours since midnight to return current day as a VVH timestamp (UNIX time in ms)
    */
  def createVVHTimeStamp(offsetHours: Int = 5): Long = {
    RoadLinkClient.createVVHTimeStamp(offsetHours)
  }

  /**
    * Returns VVH road links by municipality.
    */
  protected def queryByMunicipality(municipality: Int, filter: Option[String] = None): Seq[LinkType] = {
    val definition = layerDefinition(Filter.combineFiltersWithAnd(Filter.withMunicipalityFilter(Set(municipality)), filter))
    val url = serviceUrl(definition, queryParameters())

    fetchVVHFeatures(url) match {
      case Left(features) => features.map(extractFeature)
      case Right(error) => throw new ClientException(error.toString)
    }
  }


  /**
    * Returns VVH road links in bounding box area. Municipalities are optional.
    */
  protected def queryByMunicipalitiesAndBounds(bounds: BoundingRectangle, municipalities: Set[Int], filter: Option[String]): Seq[LinkType] = {
    val definition = layerDefinition(Filter.combineFiltersWithAnd(Filter.withMunicipalityFilter(municipalities), filter))
    val url = serviceUrl(bounds, definition, queryParameters())

    fetchVVHFeatures(url) match {
      case Left(features) => features.map(extractFeature)
      case Right(error) => throw new ClientException(error.toString)
    }
  }

  protected def queryByMunicipalitiesAndBounds(bounds: BoundingRectangle, municipalities: Set[Int]): Seq[LinkType] = {
    queryByMunicipalitiesAndBounds(bounds, municipalities, None)
  }

  /**
    * Returns VVH road links in polygon area.
    */
  protected def queryByPolygons(polygon: Polygon): Seq[LinkType] = {
    if (polygon.getCoordinates.size == 0)
      return Seq[LinkType]()

    val definition = layerDefinition(Filter.combineFiltersWithAnd("", ""))
    val url = serviceUrl(polygon, definition, queryParameters())

    fetchVVHFeatures(url) match {
      case Left(features) => features.map(extractFeature)
      case Right(error) => throw new ClientException(error.toString)
    }
  }
}
class VVHFrozenTimeRoadLinkClientServicePoint(vvhRestApiEndPoint: String) extends OldVVHRoadLinkClient(vvhRestApiEndPoint){
  protected override val serviceName = "Roadlink_temp"
  protected override val disableGeometry = false
}

class OldVVHRoadLinkClient(vvhRestApiEndPoint: String) extends VVHClientOperations{

  type LinkType = RoadLinkFetched
  type Content =Map[String, Any]

  protected override val restApiEndPoint = vvhRestApiEndPoint
  protected override val serviceName = "Roadlink_data"
  protected override val linkGeomSource:LinkGeomSource = LinkGeomSource.NormalLinkInterface
  protected override val disableGeometry = false

  protected override def defaultOutFields(): String = {
    "MTKID,LINKID,MTKHEREFLIP,MUNICIPALITYCODE,VERTICALLEVEL,HORIZONTALACCURACY,VERTICALACCURACY,MTKCLASS,ADMINCLASS,DIRECTIONTYPE,CONSTRUCTIONTYPE,ROADNAME_FI,ROADNAME_SM,ROADNAME_SE,FROM_LEFT,TO_LEFT,FROM_RIGHT,TO_RIGHT,LAST_EDITED_DATE,ROADNUMBER,ROADPARTNUMBER,VALIDFROM,GEOMETRY_EDITED_DATE,CREATED_DATE,SURFACETYPE,END_DATE,STARTNODE,ENDNODE,GEOMETRYLENGTH"
  }

  protected override def mapFields(content: Map[String, Any], url: String): Either[List[Map[String, Any]], LinkOperationError] = {
    val optionalLayers = content.get("layers").map(_.asInstanceOf[List[Map[String, Any]]])
    val optionalFeatureLayer = optionalLayers.flatMap { layers => layers.find { layer => layer.contains("features") } }
    val optionalFeatures = optionalFeatureLayer.flatMap { featureLayer => featureLayer.get("features").map(_.asInstanceOf[List[Map[String, Any]]]) }
    optionalFeatures.map(Left(_)).getOrElse(Right(LinkOperationError(url+" : "+content.toString(), "")))
  }

  /**
    * Constructions Types Allows to return
    * In Use - 0
    * Under Construction - 1
    * Planned - 3
    */
  protected def roadLinkStatusFilter(feature: Map[String, Any]): Boolean = {
    val attributes = feature("attributes").asInstanceOf[Map[String, Any]]
    val linkStatus = extractAttributes(attributes).getOrElse("CONSTRUCTIONTYPE", BigInt(0)).asInstanceOf[BigInt]
    linkStatus == ConstructionType.InUse.value || linkStatus == ConstructionType.Planned.value || linkStatus == ConstructionType.UnderConstruction.value
  }

  protected def queryLinksIdByPolygons(polygon: Polygon): Seq[String] = {
    val polygonString = Filter.stringifyPolygonGeometry(polygon)
    if (!polygonString.contains("{rings:["))
    {
      return  Seq.empty[String]
    }
    val nvps = new ArrayList[NameValuePair]()
    nvps.add(new BasicNameValuePair("layerDefs", layerDefinitionWithoutEncoding("", Some("LINKID"))))
    nvps.add(new BasicNameValuePair("geometry", polygonString))
    nvps.add(new BasicNameValuePair("geometryType", "esriGeometryPolygon"))
    nvps.add(new BasicNameValuePair("spatialRel", "esriSpatialRelIntersects"))
    nvps.add(new BasicNameValuePair("returnGeometry", "false"))
    nvps.add(new BasicNameValuePair("returnZ", "true"))
    nvps.add(new BasicNameValuePair("returnM", "true"))
    nvps.add(new BasicNameValuePair("geometryPrecision", "3"))
    nvps.add(new BasicNameValuePair("f", "pjson"))

    fetchVVHFeatures(serviceUrl, nvps) match {
      case Left(features) => features.map(extractLinkIdFromVVHFeature)
      case Right(error) => throw new ClientException(error.toString)
    }
  }

  /**
    * Returns VVH road links.
    */
  protected def queryByLinkIds[T](linkIds: Set[Long],
                                           fieldSelection: Option[String],
                                           fetchGeometry: Boolean,
                                           resultTransition: (Map[String, Any], List[List[Double]]) => T,
                                           filter: Set[Long] => String): Seq[T] = {
    val batchSize = 1000
    val idGroups: List[Set[Long]] = linkIds.grouped(batchSize).toList
    idGroups.par.flatMap { ids =>
      val definition = layerDefinition(filter(ids), fieldSelection)
      val url = serviceUrl(definition, queryParameters(fetchGeometry))

      fetchVVHFeatures(url) match {
        case Left(features) => features.map { feature =>
          val attributes = extractFeatureAttributes(feature)
          val geometry = if (fetchGeometry) extractFeatureGeometry(feature) else Nil
          resultTransition(attributes, geometry)
        }
        case Right(error) => throw new ClientException(error.toString)
      }
    }.toList
  }

  /**
    * Returns VVH road links related with finnish names. TODO only one used
    */
  protected def queryByNames[T](names: Set[String],
                                fieldSelection: Option[String],
                                fetchGeometry: Boolean,
                                resultTransition: (Map[String, Any], List[List[Double]]) => T,
                                filter: Set[String] => String): Seq[T] = {
    val batchSize = 1000
    val nameGroups: List[Set[String]] = names.grouped(batchSize).toList
    nameGroups.par.flatMap { names =>
      val definition = layerDefinition(filter(names), fieldSelection)
      val url = serviceUrl(definition, queryParameters(fetchGeometry))

      fetchVVHFeatures(url) match {
        case Left(features) => features.map { feature =>
          val attributes = extractFeatureAttributes(feature)
          val geometry = if (fetchGeometry) extractFeatureGeometry(feature) else Nil
          resultTransition(attributes, geometry)
        }
        case Right(error) => throw new ClientException(error.toString)
      }
    }.toList
  }

  //Extract attributes methods
  protected override def extractFeature(feature: Map[String, Any]): RoadLinkFetched = {
    val attributes = extractFeatureAttributes(feature)
    val path = extractFeatureGeometry(feature)
    extractRoadLinkFeature(attributes, path)
  }

  protected def extractRoadLinkFeature(attributes: Map[String, Any], path: List[List[Double]]): RoadLinkFetched = {
    val linkGeometry: Seq[Point] = path.map(point => {
      Point(point(0), point(1), extractMeasure(point(2)).get)
    })
    val linkGeometryForApi = Map("points" -> path.map(point => Map("x" -> point(0), "y" -> point(1), "z" -> point(2), "m" -> point(3))))
    val linkGeometryWKTForApi = Map("geometryWKT" -> ("LINESTRING ZM (" + path.map(point => point(0) + " " + point(1) + " " + point(2) + " " + point(3)).mkString(", ") + ")"))
    val linkId = attributes("LINKID").asInstanceOf[BigInt].toString   // TODO: Temporary parsing to string. Remove after not needed anymore
    val municipalityCode = attributes("MUNICIPALITYCODE").asInstanceOf[BigInt].toInt
    val mtkClass = attributes("MTKCLASS")
    val geometryLength = Filter.anyToDouble(attributes("GEOMETRYLENGTH")).getOrElse(0.0)

    val featureClassCode = if (mtkClass != null) // Complementary geometries have no MTK Class
      attributes("MTKCLASS").asInstanceOf[BigInt].intValue()
    else
      0
    val featureClass = featureClassCodeToFeatureClass.getOrElse(featureClassCode, FeatureClass.AllOthers)

    RoadLinkFetched(linkId, municipalityCode, linkGeometry, extractAdministrativeClass(attributes),
      extractTrafficDirection(attributes), featureClass, extractModifiedAt(attributes),
      extractAttributes(attributes) ++ linkGeometryForApi ++ linkGeometryWKTForApi, extractConstructionType(attributes), linkGeomSource, geometryLength)

  }

  protected def extractLinkIdFromVVHFeature(feature: Map[String, Any]): String = {
    extractFeatureAttributes(feature)("LINKID").asInstanceOf[BigInt].toString // TODO: Temporary parsing to string. Remove after not needed anymore
  }

  protected def extractAdministrativeClass(attributes: Map[String, Any]): AdministrativeClass = {
    Option(attributes("ADMINCLASS").asInstanceOf[BigInt])
      .map(_.toInt)
      .map(AdministrativeClass.apply)
      .getOrElse(Unknown)
  }

  protected def extractConstructionType(attributes: Map[String, Any]): ConstructionType = {
    Option(attributes("CONSTRUCTIONTYPE").asInstanceOf[BigInt])
      .map(_.toInt)
      .map(ConstructionType.apply)
      .getOrElse(ConstructionType.InUse)
  }

  protected def extractLinkGeomSource(attributes: Map[String, Any]): LinkGeomSource = {
    Option(attributes("LINK_SOURCE").asInstanceOf[BigInt])
      .map(_.toInt)
      .map(LinkGeomSource.apply)
      .getOrElse(LinkGeomSource.Unknown)
  }
  protected val vvhTrafficDirectionToTrafficDirection: Map[Int, TrafficDirection] = Map(
    0 -> TrafficDirection.BothDirections,
    1 -> TrafficDirection.TowardsDigitizing,
    2 -> TrafficDirection.AgainstDigitizing)

  protected def extractTrafficDirection(attributes: Map[String, Any]): TrafficDirection = {
    Option(attributes("DIRECTIONTYPE").asInstanceOf[BigInt])
      .map(_.toInt)
      .map(vvhTrafficDirectionToTrafficDirection.getOrElse(_, TrafficDirection.UnknownDirection))
      .getOrElse(TrafficDirection.UnknownDirection)
  }

  protected def extractAttributes(attributesMap: Map[String, Any]): Map[String, Any] = {
    attributesMap.filterKeys{ x => Set(
      "MTKID",
      "MTKCLASS",
      "HORIZONTALACCURACY",
      "VERTICALACCURACY",
      "VERTICALLEVEL",
      "CONSTRUCTIONTYPE",//TODO Remove this attribute from here when VVHHistoryRoadLink have a different way to get the ConstructionType like VVHRoadlink
      "ROADNAME_FI",
      "ROADNAME_SM",
      "ROADNAME_SE",
      "ROADNUMBER",
      "ROADPARTNUMBER",
      "FROM_LEFT",
      "TO_LEFT",
      "FROM_RIGHT",
      "TO_RIGHT",
      "MUNICIPALITYCODE",
      "MTKHEREFLIP",
      "VALIDFROM",
      "GEOMETRY_EDITED_DATE",
      "END_DATE",
      "LINKID_NEW",
      "CREATED_DATE",
      "LAST_EDITED_DATE",
      "SURFACETYPE",
      "SUBTYPE",
      "END_DATE",
      "OBJECTID",
      "STARTNODE",
      "ENDNODE",
      "TRACK_CODE", //used only with suravage in viite
      "CUST_OWNER").contains(x)
    }.filter { case (_, value) =>
      value != null
    }
  }

  protected val featureClassCodeToFeatureClass: Map[Int, FeatureClass] = Map(
    12316 -> FeatureClass.TractorRoad,
    12141 -> FeatureClass.DrivePath,
    12314 -> FeatureClass.CycleOrPedestrianPath,
    12312 -> FeatureClass.WinterRoads,
    12153 -> FeatureClass.SpecialTransportWithoutGate,
    12154 -> FeatureClass.SpecialTransportWithGate,
    12131 -> FeatureClass.CarRoad_IIIa,
    12132 -> FeatureClass.CarRoad_IIIb
  )

  protected val trafficDirectionToTrafficDirection: Map[Int, TrafficDirection] = Map(
    0 -> TrafficDirection.BothDirections,
    1 -> TrafficDirection.TowardsDigitizing,
    2 -> TrafficDirection.AgainstDigitizing)

  /**
    * Returns VVH road links. Obtain all RoadLinks changes between two given dates.
    */
  def fetchByChangesDates(lowerDate: DateTime, higherDate: DateTime): Seq[RoadLinkFetched] = {
    val definition = layerDefinition(Filter.withLastEditedDateFilter(lowerDate, higherDate))
    val url = serviceUrl(definition, queryParameters())

    fetchVVHFeatures(url) match {
      case Left(features) => features.map(extractFeature)
      case Right(error) => throw new ClientException(error.toString)
    }
  }

  /**
    * Returns VVH road link by linkid
    * Used by Digiroad2Api.createMassTransitStop, Digiroad2Api.validateUserRights, Digiroad2Api &#47;manoeuvres DELETE endpoint, Digiroad2Api manoeuvres PUT endpoint,
    * CsvImporter.updateAssetByExternalIdLimitedByRoadType, RoadLinkService,getRoadLinkMiddlePointByLinkId, RoadLinkService.updateLinkProperties, RoadLinkService.getRoadLinkGeometry,
    * RoadLinkService.updateAutoGeneratedProperties, LinearAssetService.split, LinearAssetService.separate, MassTransitStopService.fetchRoadLink, PointAssetOperations.getById
    * and PostGISLinearAssetDao.createSpeedLimit.
    */
  def fetchByLinkId(linkId: String): Option[RoadLinkFetched] = fetchByLinkIds(Set(linkId)).headOption  
  
  /**
    * Returns VVH road links by link ids.
    * Used by VVHClient.fetchByLinkId, RoadLinkService.fetchVVHRoadlinks, SpeedLimitService.purgeUnknown, PointAssetOperations.getFloatingAssets,
    * PostGISLinearAssetDao.getLinksWithLengthFromVVH, PostGISLinearAssetDao.getSpeedLimitLinksById AssetDataImporter.importEuropeanRoads and AssetDataImporter.importProhibitions
    */
  def fetchByLinkIds(linkIds: Set[String]): Seq[RoadLinkFetched] = {
    // TODO: Temporary parsing from string to long. Remove after not needed anymore
    queryByLinkIds(parseLinkIdsToLong(linkIds), None, true, extractRoadLinkFeature, Filter.withLinkIdFilter)
  }
  
  def fetchByLinkIdsF(linkIds: Set[String]) = {
    Future(fetchByLinkIds(linkIds))
  }

  def fetchByRoadNamesF(roadNamePublicIds: String, roadNameSource: Set[String]) = {
    Future(fetchByRoadNames(roadNamePublicIds, roadNameSource))
  }

  /**
    * Returns VVH road link by mml id.
    * Used by RoadLinkService.getRoadLinkMiddlePointByMmlId
    */
  def fetchByMmlId(mmlId: Long): Option[RoadLinkFetched] = fetchByMmlIds(Set(mmlId)).headOption

  /**
    * Returns VVH road links by mml ids.
    * Used by VVHClient.fetchByMmlId, LinkIdImporter.updateTable and AssetDataImporter.importRoadAddressData.
    */
  def fetchByMmlIds(mmlIds: Set[Long]): Seq[RoadLinkFetched] = {
    queryByLinkIds(mmlIds, None, true, extractRoadLinkFeature,Filter.withMmlIdFilter)
  }

  def fetchByMunicipality(municipality: Int): Seq[RoadLinkFetched] = {
    queryByMunicipality(municipality)
  }

  def fetchByMunicipalityF(municipality: Int): Future[Seq[RoadLinkFetched]] = {
    Future(queryByMunicipality(municipality))
  }
  /**
    * Returns VVH road links. Uses Scala Future for concurrent operations.
    * Used by RoadLinkService.getRoadLinksAndChangesFromVVH(bounds, municipalities)
    */
  def fetchByMunicipalitiesAndBounds(bounds: BoundingRectangle, municipalities: Set[Int]): Seq[RoadLinkFetched] = {
    queryByMunicipalitiesAndBounds(bounds, municipalities)
  }

  def fetchByBounds(bounds: BoundingRectangle): Seq[RoadLinkFetched] = {
    queryByMunicipalitiesAndBounds(bounds, Set[Int]())
  }

  /**
    * Returns VVH road links. Uses Scala Future for concurrent operations.
    * Used by RoadLinkService.getRoadLinksAndChangesFromVVH(bounds, municipalities)
    */
  def fetchByMunicipalitiesAndBoundsF(bounds: BoundingRectangle, municipalities: Set[Int]): Future[Seq[RoadLinkFetched]] = {
    Future(queryByMunicipalitiesAndBounds(bounds, municipalities))
  }
  
  def fetchByPolygon(polygon : Polygon): Seq[RoadLinkFetched] = {
    queryByPolygons(polygon)
  }

  def fetchByPolygonF(polygon : Polygon): Future[Seq[RoadLinkFetched]] = {
    Future(queryByPolygons(polygon))
  }

  def fetchLinkIdsByPolygonF(polygon : Polygon): Future[Seq[String]] = {
    Future(queryLinksIdByPolygons(polygon))
  }
  
  /**
    * Returns VVH road links by finnish names.
    * Used by VVHClient.fetchByLinkId,
    */
  def fetchByRoadNames(roadNamePublicId: String, roadNames: Set[String]): Seq[RoadLinkFetched] = {
    queryByNames(roadNames, None, true, extractRoadLinkFeature, Filter.withFinNameFilter(roadNamePublicId))
  }

  /**
    * Returns VVH road links.
    * Used by RoadLinkService.fetchVVHRoadlinks (called from CsvGenerator)
    */
  def fetchVVHRoadlinks[T](linkIds: Set[String],
                           fieldSelection: Option[String],
                           fetchGeometry: Boolean,
                           resultTransition: (Map[String, Any], List[List[Double]]) => T): Seq[T] =
  // only one used in very old batch
  // TODO: Temporary parsing from string to long. Remove after not needed anymore
    queryByLinkIds(parseLinkIdsToLong(linkIds), fieldSelection, fetchGeometry, resultTransition, Filter.withLinkIdFilter)
}

class VVHChangeInfoClient(vvhRestApiEndPoint: String) extends VVHClientOperations {
  override type LinkType = ChangeInfo
  override type Content = Map[String, Any]
  protected override val restApiEndPoint = vvhRestApiEndPoint
  protected override val serviceName = "Roadlink_ChangeInfo"
  protected override val linkGeomSource = LinkGeomSource.Unknown
  protected override val disableGeometry = true

  protected override def defaultOutFields(): String = {
    "OLD_ID,NEW_ID,MTKID,CHANGETYPE,OLD_START,OLD_END,NEW_START,NEW_END,CREATED_DATE,CONSTRUCTIONTYPE,STARTNODE,ENDNODE"
  }

  protected override def mapFields(content: Map[String, Any], url: String): Either[List[Map[String, Any]], LinkOperationError] = {
    val optionalLayers = content.get("layers").map(_.asInstanceOf[List[Map[String, Any]]])
    val optionalFeatureLayer = optionalLayers.flatMap { layers => layers.find { layer => layer.contains("features") } }
    val optionalFeatures = optionalFeatureLayer.flatMap { featureLayer => featureLayer.get("features").map(_.asInstanceOf[List[Map[String, Any]]]) }
    optionalFeatures.map(Left(_)).getOrElse(Right(LinkOperationError(url+" : "+content.toString(),"")))
  }

  protected override def extractFeature(feature: Map[String, Any]): LinkType = {
    val attributes = extractFeatureAttributes(feature)

    val oldId = Option(attributes("OLD_ID").asInstanceOf[BigInt]).map(_.toString) // TODO: Temporary parsing to string. Remove after not needed anymore
    val newId = Option(attributes("NEW_ID").asInstanceOf[BigInt]).map(_.toString)// TODO: Temporary parsing to string. Remove after not needed anymore
    val mmlId = attributes("MTKID").asInstanceOf[BigInt].longValue()
    val changeType = attributes("CHANGETYPE").asInstanceOf[BigInt].intValue()
    val vvhTimeStamp = Option(attributes("CREATED_DATE").asInstanceOf[BigInt]).map(_.longValue()).getOrElse(0L)
    val oldStartMeasure = extractMeasure(attributes("OLD_START"))
    val oldEndMeasure = extractMeasure(attributes("OLD_END"))
    val newStartMeasure = extractMeasure(attributes("NEW_START"))
    val newEndMeasure = extractMeasure(attributes("NEW_END"))

    ChangeInfo(oldId, newId, mmlId, changeType, oldStartMeasure, oldEndMeasure, newStartMeasure, newEndMeasure, vvhTimeStamp)
  }

  def fetchByDates(since: DateTime, until: DateTime): Seq[ChangeInfo] = {
    /*val definition = layerDefinition(withCreatedDateFilter(since, until))
    val url = serviceUrl(definition, queryParameters())

    fetchVVHFeatures(url) match {
      case Left(features) => features.map(extractFeature)
      case Right(error) => throw new ClientException(error.toString)
    }*/
    Seq()  //disabled due to DROTH-3254
  }

  protected  def withCreatedDateFilter(lowerDate: DateTime, higherDate: DateTime): String = {
    withCreatedDateLimitFilter("CREATED_DATE", lowerDate, higherDate)
  }

  protected def withCreatedDateLimitFilter(attributeName: String, lowerDate: DateTime, higherDate: DateTime): String = {
    val formatter = DateTimeFormat.forPattern("yyyy-MM-dd")
    val since = formatter.print(lowerDate)
    val until = formatter.print(higherDate)

    s""""where":"( $attributeName >=date '$since' and $attributeName <=date '$until' )","""
  }

  def fetchByBoundsAndMunicipalities(bounds: BoundingRectangle, municipalities: Set[Int]): Seq[ChangeInfo] = {
    Seq()
    //disabled due to DROTH-3311, enable this when changes can be fetched again: queryByMunicipalitiesAndBounds(bounds, municipalities)
  }

  def fetchByMunicipality(municipality: Int): Seq[ChangeInfo] = {
    Seq()
    //disabled due to DROTH-3311, enable this when changes can be fetched again: queryByMunicipality(municipality)
  }

  def fetchByBoundsAndMunicipalitiesF(bounds: BoundingRectangle, municipalities: Set[Int]): Future[Seq[ChangeInfo]] = {
    Future(Seq())
    //disabled due to DROTH-3311, enable this when changes can be fetched again: Future(queryByMunicipalitiesAndBounds(bounds, municipalities))
  }

  def fetchByMunicipalityF(municipality: Int): Future[Seq[ChangeInfo]] = {
    Future(Seq())
    //disabled due to DROTH-3311, enable this when changes can be fetched again: Future(queryByMunicipality(municipality))
  }

  def fetchByPolygon(polygon: Polygon): Seq[ChangeInfo] = {
    Seq()
    //disabled due to DROTH-3311, enable this when changes can be fetched again: queryByPolygons(polygon)
  }

  def fetchByPolygonF(polygon: Polygon): Future[Seq[ChangeInfo]] = {
    Future(Seq())
    //disabled due to DROTH-3311, enable this when changes can be fetched again: Future(queryByPolygons(polygon))
  }

  def fetchByLinkIdsF(linkIds: Set[String]): Future[Seq[ChangeInfo]] = {
    Future(Seq())
    //disabled due to DROTH-3311, enable this when changes can be fetched again: Future(fetchByLinkIds(linkIds))
  }

  /**
    * Fetch change information where given link id is in the old_id list (source)
    *
    * @param linkIds Link ids to check as sources
    * @return ChangeInfo for given links
    */
  def fetchByLinkIds(linkIds: Set[String]): Seq[ChangeInfo] = {
    Seq()
    //disabled due to DROTH-3311, enable this when changes can be fetched again: queryByLinkIds(linkIds)
  }

  protected def queryByLinkIds(linkIds: Set[Long]): Seq[ChangeInfo] = {
    val batchSize = 1000
    val idGroups: List[Set[Long]] = linkIds.grouped(batchSize).toList
    idGroups.par.flatMap { ids =>
      val definition = layerDefinition(Filter.withFilter("OLD_ID", ids))
      val url = serviceUrl(definition, queryParameters(false))

      fetchVVHFeatures(url) match {
        case Left(features) => features.map(extractFeature)
        case Right(error) => throw new ClientException(error.toString)
      }
    }.toList
  }
}

class VVHRoadNodesClient(vvhRestApiEndPoint: String) extends VVHClientOperations {

  override type LinkType = RoadNodesFetched
  override type Content = Map[String, Any]
  protected override val restApiEndPoint = vvhRestApiEndPoint
  protected override val serviceName = "Roadnode_data"
  protected override val linkGeomSource = LinkGeomSource.Unknown
  protected override val disableGeometry = false

  protected override def defaultOutFields(): String = {
    "OBJECTID,NODEID,FORMOFNODE,MUNICIPALITYCODE,SUBTYPE"
  }

  protected override def mapFields(content: Map[String, Any], url: String): Either[List[Map[String, Any]], LinkOperationError] = {
    val optionalLayers = content.get("layers").map(_.asInstanceOf[List[Map[String, Any]]])
    val optionalFeatureLayer = optionalLayers.flatMap { layers => layers.find { layer => layer.contains("features") } }
    val optionalFeatures = optionalFeatureLayer.flatMap { featureLayer => featureLayer.get("features").map(_.asInstanceOf[List[Map[String, Any]]]) }
    optionalFeatures.map(Left(_)).getOrElse(Right(LinkOperationError(url+" : "+content.toString(),"")))
  }

  protected override def extractFeature(feature: Map[String, Any]) : RoadNodesFetched = {
    val attributes = extractFeatureAttributes(feature)
    val geometry = feature("geometry").asInstanceOf[Map[String, Double]]
    val nodeGeometry: Point = Point(geometry.get("x").get, geometry.get("y").get)
    val municipalityCode = attributes("MUNICIPALITYCODE").asInstanceOf[BigInt].toInt
    val objectId = attributes("OBJECTID").asInstanceOf[BigInt].longValue()
    val nodeId = attributes("NODEID").asInstanceOf[BigInt].longValue()
    val subtype = attributes("SUBTYPE").asInstanceOf[BigInt].toInt

    RoadNodesFetched(objectId, nodeGeometry, nodeId, extractNodeType(attributes), municipalityCode, subtype)
  }

  protected def extractNodeType(attributes: Map[String, Any]): NodeType = {
    Option(attributes("FORMOFNODE").asInstanceOf[BigInt])
      .map(_.toInt)
      .map(NodeType.apply)
      .getOrElse(NodeType.UnknownNodeType)
  }

  def fetchByMunicipality(municipality: Int): Seq[RoadNodesFetched] = {
    queryByMunicipality(municipality)
  }

  def fetchByMunicipalityF(municipality: Int): Future[Seq[RoadNodesFetched]] = {
    Future(queryByMunicipality(municipality))
  }
}

class VVHComplementaryClient(vvhRestApiEndPoint: String) extends OldVVHRoadLinkClient(vvhRestApiEndPoint) {

  protected override val restApiEndPoint = vvhRestApiEndPoint
  protected override val serviceName = "Roadlink_complimentary"
  protected override val linkGeomSource: LinkGeomSource = LinkGeomSource.ComplimentaryLinkInterface
  protected override val disableGeometry = false
  
  override def defaultOutFields(): String = {
    "MTKID,LINKID,OBJECTID,MTKHEREFLIP,MUNICIPALITYCODE,VERTICALLEVEL,HORIZONTALACCURACY,VERTICALACCURACY,MTKCLASS,ADMINCLASS,DIRECTIONTYPE,ROADNAME_FI,ROADNAME_SM,ROADNAME_SE,FROM_LEFT,TO_LEFT,FROM_RIGHT,TO_RIGHT,LAST_EDITED_DATE,ROADNUMBER,ROADPARTNUMBER,VALIDFROM,GEOMETRY_EDITED_DATE,CREATED_DATE,SURFACETYPE,SUBTYPE,CONSTRUCTIONTYPE,CUST_OWNER,GEOMETRYLENGTH"
  }

  private def createFormParams(complementaryFeatures: Map[String, Any]): ArrayList[NameValuePair] = {
    val featuresValue = Serialization.write(Seq(Map("attributes" -> complementaryFeatures)))
    // Print JSON sent to VVH for testing purposes
    logger.info("complementaryFeatures to JSON: %s".format(featuresValue))

    val nvps = new ArrayList[NameValuePair]()
    nvps.add(new BasicNameValuePair("features", featuresValue))
    nvps.add(new BasicNameValuePair("gdbVersion", ""))
    nvps.add(new BasicNameValuePair("rollbackOnFailure", "true"))
    nvps.add(new BasicNameValuePair("f", "pjson"))

    nvps
  }

  def fetchWalkwaysByBoundsAndMunicipalitiesF(bounds: BoundingRectangle, municipalities: Set[Int]): Future[Seq[RoadLinkFetched]] = {
    Future(queryByMunicipalitiesAndBounds(bounds, municipalities, Some(Filter.withMtkClassFilter(Set(12314)))))
  }

  def fetchWalkwaysByBoundsAndMunicipalities(bounds: BoundingRectangle, municipalities: Set[Int]): Seq[RoadLinkFetched] = {
    queryByMunicipalitiesAndBounds(bounds, municipalities, Some(withMtkClassFilter(Set(12314))))
  }

  def fetchWalkwaysByMunicipalitiesF(municipality: Int): Future[Seq[RoadLinkFetched]] =
    Future(queryByMunicipality(municipality, Some(Filter.withMtkClassFilter(Set(12314)))))
  case class LinkOperationErrorComplementary(content: Map[String, Any], url: String)
  def updateVVHFeatures(complementaryFeatures: Map[String, Any]): Either[List[Map[String, Any]], LinkOperationErrorComplementary] = {
    val url = vvhRestApiEndPoint + serviceName + "/FeatureServer/0/updateFeatures"
    val request = new HttpPost(url)
    request.setEntity(new UrlEncodedFormEntity(createFormParams(complementaryFeatures), "utf-8"))
    val client = HttpClientBuilder.create().build()
    val vVHAuthPropertyReader = new VVHAuthPropertyReader
    request.addHeader("Authorization", "Basic " + vVHAuthPropertyReader.getAuthInBase64)

    val response = client.execute(request)
    try {
      val content: Map[String, Seq[Map[String, Any]]] = parse(StreamInput(response.getEntity.getContent)).values.asInstanceOf[Map[String, Seq[Map[String, Any]]]]
      content.get("updateResults").getOrElse(None) match {
        case None =>
          content.get("error").head.asInstanceOf[Map[String, Any]].getOrElse("details", None) match {
            case None => Right(LinkOperationErrorComplementary(Map("error" -> "Error Without Details "), url))
            case value => Right(LinkOperationErrorComplementary(Map("error details" -> value), url))
          }
        case _ =>
          content.get("updateResults").get.map(_.getOrElse("success", None)).head match {
            case None => Right(LinkOperationErrorComplementary(Map("error" -> "Update status not available in JSON Response"), url))
            case true => Left(List(content))
            case false =>
              content.get("updateResults").get.map(_.getOrElse("error", None)).head.asInstanceOf[Map[String, Any]].getOrElse("description", None) match {
                case None => Right(LinkOperationErrorComplementary(Map("error" -> "Error Without Information"), url))
                case value => Right(LinkOperationErrorComplementary(Map("error" -> value), url))
              }
          }
      }
    } catch {
      case e: Exception => Right(LinkOperationErrorComplementary(Map("error" -> e.getMessage), url))
    } finally {
      response.close()
    }
  }
}

class VVHHistoryClient(vvhRestApiEndPoint: String) extends OldVVHRoadLinkClient(vvhRestApiEndPoint) {

  protected override val restApiEndPoint = vvhRestApiEndPoint
  protected override val serviceName = "Roadlink_data_history"
  protected override val linkGeomSource = LinkGeomSource.HistoryLinkInterface
  protected override val disableGeometry = false

  protected override def defaultOutFields() : String = {
    "MTKID,LINKID,MTKHEREFLIP,MUNICIPALITYCODE,VERTICALLEVEL,HORIZONTALACCURACY,VERTICALACCURACY,MTKCLASS,ADMINCLASS,DIRECTIONTYPE,CONSTRUCTIONTYPE,ROADNAME_FI,ROADNAME_SM,ROADNAME_SE,FROM_LEFT,TO_LEFT,FROM_RIGHT,TO_RIGHT,LAST_EDITED_DATE,ROADNUMBER,ROADPARTNUMBER,VALIDFROM,GEOMETRY_EDITED_DATE,SURFACETYPE,END_DATE,LINKID_NEW,OBJECTID,CREATED_DATE,CONSTRUCTIONTYPE,GEOMETRYLENGTH"
  }

  protected override def mapFields(content: Map[String, Any], url: String): Either[List[Map[String, Any]], LinkOperationError] = {
    val optionalFeatures = if (content.contains("layers")){
      val optionalLayers = content.get("layers").map(_.asInstanceOf[List[Map[String, Any]]])
      val optionalFeatureLayer = optionalLayers.flatMap { layers => layers.find { layer => layer.contains("features") } }
      optionalFeatureLayer.flatMap { featureLayer => featureLayer.get("features").map(_.asInstanceOf[List[Map[String, Any]]]) }
    }
    else{
      content.get("features").map(_.asInstanceOf[List[Map[String, Any]]])
    }
    optionalFeatures.map(Left(_)).getOrElse(Right(LinkOperationError(url+" : "+content.toString(),"")))
  }

  protected def extractVVHHistoricFeature(feature: Map[String, Any]) : HistoryRoadLink = {
    val attributes = extractFeatureAttributes(feature)
    val path = extractFeatureGeometry(feature)
    val linkGeometry: Seq[Point] = path.map(point => { Point(point(0), point(1), extractMeasure(point(2)).get) })
    val municipalityCode = attributes("MUNICIPALITYCODE").asInstanceOf[BigInt].toInt
    val linkGeometryForApi = Map("points" -> path.map(point => Map("x" -> point(0), "y" -> point(1), "z" -> point(2), "m" -> point(3))))
    val linkGeometryWKTForApi = Map("geometryWKT" -> ("LINESTRING ZM (" + path.map(point => point(0) + " " + point(1) + " " + point(2) + " " + point(3)).mkString(", ") + ")"))
    val linkId = attributes("LINKID").asInstanceOf[BigInt].toString // TODO: Temporary parsing to string. Remove after not needed anymore
    val createdDate = attributes("CREATED_DATE").asInstanceOf[BigInt].longValue()
    val endTime = attributes("END_DATE").asInstanceOf[BigInt].longValue()
    val mtkClass = attributes("MTKCLASS")
    val featureClassCode = if (mtkClass != null) // Complementary geometries have no MTK Class
      attributes("MTKCLASS").asInstanceOf[BigInt].intValue()
    else
      0
    val featureClass = featureClassCodeToFeatureClass.getOrElse(featureClassCode, FeatureClass.AllOthers)

    HistoryRoadLink(linkId, municipalityCode, linkGeometry, extractAdministrativeClass(attributes),
      extractTrafficDirection(attributes), featureClass, createdDate, endTime, extractAttributes(attributes) ++ linkGeometryForApi ++ linkGeometryWKTForApi)
  }
  /**
    * Returns VVH road link history data in bounding box area. Municipalities are optional.
    * Used by VVHClient.fetchVVHRoadlinksF, RoadLinkService.getVVHRoadLinks(bounds, municipalities), RoadLinkService.getVVHRoadLinks(bounds),
    * PointAssetService.getByBoundingBox and ServicePointImporter.importServicePoints.
    */
  def fetchVVHRoadLinkByLinkIds(linkIds: Set[String] = Set()): Seq[HistoryRoadLink] = {
    if (linkIds.isEmpty)
      Nil
    else {
      val batchSize = 1000
      // TODO: Temporary parsing from string to long. Remove after not needed anymore
      val idGroups: List[Set[Long]] = parseLinkIdsToLong(linkIds).grouped(batchSize).toList
      idGroups.par.flatMap { ids =>
        val definition = layerDefinition(Filter.withLinkIdFilter(ids))
        val url = serviceUrl(definition, queryParameters())

        fetchVVHFeatures(url) match {
          case Left(features) => features.map(extractVVHHistoricFeature)
          case Right(error) => throw new ClientException(error.toString)
        }
      }.toList
    }
  }

  def fetchVVHRoadLinkByLinkIdsF(linkIds: Set[String] = Set()): Future[Seq[HistoryRoadLink]] = {
    Future(fetchVVHRoadLinkByLinkIds(linkIds))
  }
}