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

  protected def extractFeatureAttributes(feature: Map[String, Any]): Map[String, Any] = {
    feature("attributes").asInstanceOf[Map[String, Any]]
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

  protected def queryByLinkIds(linkIds: Set[Long]): Seq[LinkType] = {
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

  protected def queryByDates(since: DateTime, until: DateTime): Seq[LinkType] = {
    val definition = layerDefinition(Filter.withCreatedDateFilter(since, until))
    val url = serviceUrl(definition, queryParameters())

    fetchVVHFeatures(url) match {
      case Left(features) => features.map(extractFeature)
      case Right(error) => throw new ClientException(error.toString)
    }
  }
}
//Replace with new tiekamu client in future
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
    val timeStamp = Option(attributes("CREATED_DATE").asInstanceOf[BigInt]).map(_.longValue()).getOrElse(0L)
    val oldStartMeasure = extractMeasure(attributes("OLD_START"))
    val oldEndMeasure = extractMeasure(attributes("OLD_END"))
    val newStartMeasure = extractMeasure(attributes("NEW_START"))
    val newEndMeasure = extractMeasure(attributes("NEW_END"))

    ChangeInfo(oldId, newId, mmlId, changeType, oldStartMeasure, oldEndMeasure, newStartMeasure, newEndMeasure, timeStamp)
  }

  def fetchByDates(since: DateTime, until: DateTime): Seq[ChangeInfo] = {
    /*
    queryByDates(since,until)
    }*/
    Seq()  //disabled due to DROTH-3254
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
}