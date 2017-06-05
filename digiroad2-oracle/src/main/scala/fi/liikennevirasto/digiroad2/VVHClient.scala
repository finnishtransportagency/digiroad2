package fi.liikennevirasto.digiroad2

import java.net.URLEncoder
import java.util.ArrayList

import com.vividsolutions.jts.geom.Polygon
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.RoadLinkLike
import org.apache.http.NameValuePair
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.{HttpGet, HttpPost}
import org.apache.http.impl.client.HttpClientBuilder
import org.joda.time.format.DateTimeFormat
import org.apache.http.message.BasicNameValuePair
import org.apache.http.util.EntityUtils
import org.apache.http.NameValuePair
import org.joda.time.{DateTime, DateTimeZone}
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

sealed trait FeatureClass
object FeatureClass {
  case object TractorRoad extends FeatureClass
  case object DrivePath extends FeatureClass
  case object CycleOrPedestrianPath extends FeatureClass
  case object AllOthers extends FeatureClass
}

case class VVHRoadlink(linkId: Long, municipalityCode: Int, geometry: Seq[Point],
                       administrativeClass: AdministrativeClass, trafficDirection: TrafficDirection,
                       featureClass: FeatureClass, modifiedAt: Option[DateTime] = None, attributes: Map[String, Any] = Map(),
                       constructionType: ConstructionType = ConstructionType.InUse, linkSource: LinkGeomSource = LinkGeomSource.NormalLinkInterface) extends RoadLinkLike {
  def roadNumber: Option[String] = attributes.get("ROADNUMBER").map(_.toString)
}

case class ChangeInfo(oldId: Option[Long], newId: Option[Long], mmlId: Long, changeType: Int,
                      oldStartMeasure: Option[Double], oldEndMeasure: Option[Double], newStartMeasure: Option[Double],
                      newEndMeasure: Option[Double], vvhTimeStamp: Long = 0L)

case class VVHHistoryRoadLink(linkId: Long, municipalityCode: Int, geometry: Seq[Point], administrativeClass: AdministrativeClass,
                              trafficDirection: TrafficDirection, featureClass: FeatureClass, createdDate:BigInt, endDate: BigInt, attributes: Map[String, Any] = Map())
/**
  * Numerical values for change types from VVH ChangeInfo Api
  */
sealed trait ChangeType {
  def value: Int
}
object ChangeType {
  val values = Set(Unknown, CombinedModifiedPart, CombinedRemovedPart, LenghtenedCommonPart, LengthenedNewPart, DividedModifiedPart, DividedNewPart, ShortenedCommonPart, ShortenedRemovedPart, Removed, New, ReplacedCommonPart, ReplacedNewPart, ReplacedRemovedPart)

  def apply(intValue: Int): ChangeType = {
    values.find(_.value == intValue).getOrElse(Unknown)
  }

  case object Unknown extends ChangeType { def value = 0 }
  case object CombinedModifiedPart extends ChangeType { def value = 1 }
  case object CombinedRemovedPart extends ChangeType { def value = 2 }
  case object LenghtenedCommonPart extends ChangeType { def value = 3 }
  case object LengthenedNewPart extends ChangeType { def value = 4 }
  case object DividedModifiedPart extends ChangeType { def value = 5 }
  case object DividedNewPart extends ChangeType { def value = 6 }
  case object ShortenedCommonPart extends ChangeType { def value = 7 }
  case object ShortenedRemovedPart extends ChangeType { def value = 8 }
  case object Removed extends ChangeType { def value = 11 }
  case object New extends ChangeType { def value = 12 }
  case object ReplacedCommonPart extends ChangeType { def value = 13 }
  case object ReplacedNewPart extends ChangeType { def value = 14 }
  case object ReplacedRemovedPart extends ChangeType { def value = 16 }

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
      case LenghtenedCommonPart => true
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

object VVHClient {
  def createVVHTimeStamp(offsetHours: Int): Long = {
    val oneHourInMs = 60 * 60 * 1000L
    val utcTime = DateTime.now().minusHours(offsetHours).getMillis
    val curr = utcTime + DateTimeZone.getDefault.getOffset(utcTime)
    curr - (curr % (24L*oneHourInMs))
  }
}

class VVHClient(vvhRestApiEndPoint: String) {
  class VVHClientException(response: String) extends RuntimeException(response)
  protected implicit val jsonFormats: Formats = DefaultFormats
  protected val linkGeomSource :LinkGeomSource =  LinkGeomSource.NormalLinkInterface

  lazy val logger = LoggerFactory.getLogger(getClass)
  lazy val complementaryData: VVHComplementaryClient = new VVHComplementaryClient(vvhRestApiEndPoint)
  lazy val historyData: VVHHistoryClient = new VVHHistoryClient(vvhRestApiEndPoint)

  private val roadLinkDataService = "Roadlink_data"

  /**
    *
    * @param polygon to be converted to string
    * @return string compatible with VVH polygon query
    */

  def stringifyPolygonGeometry(polygon: Polygon): String = {
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

  protected def withFilter[T](attributeName: String, ids: Set[T]): String = {
    val filter =
      if (ids.isEmpty) {
        ""
      } else {
        val query = ids.mkString(",")
        s""""where":"$attributeName IN ($query)","""
      }
    filter
  }

  private def withLimitFilter(attributeName: String, low: Int, high: Int, includeAllPublicRoads: Boolean = false): String = {
    val filter =
      if (low < 0 || high < 0 || low > high) {
        ""
      } else {
        if (includeAllPublicRoads) {
          s""""where":"( ADMINCLASS = 1 OR $attributeName >= $low and $attributeName <= $high )","""
        } else {
          s""""where":"( $attributeName >= $low and $attributeName <= $high )","""
        }
      }
    filter
  }

  private def withDateLimitFilter(attributeName: String, lowerDate: DateTime, higherDate: DateTime): String = {
    val formatter = DateTimeFormat.forPattern("yyyy-MM-dd")
    val since = formatter.print(lowerDate)
    val until = formatter.print(higherDate)

    s""""where":"( $attributeName >=date '$since' and $attributeName <=date '$until' )","""
  }

  protected def withMunicipalityFilter(municipalities: Set[Int]): String = {
    withFilter("MUNICIPALITYCODE", municipalities)
  }

  private def withRoadNumberFilter(roadNumbers: (Int, Int), includeAllPublicRoads: Boolean): String = {
    withLimitFilter("ROADNUMBER", roadNumbers._1, roadNumbers._2, includeAllPublicRoads)
  }

  protected def withRoadNumbersFilter(roadNumbers: Seq[(Int, Int)], includeAllPublicRoads: Boolean, filter: String = ""): String = {
    if (roadNumbers.isEmpty)
      return s""""where":"($filter)","""
    if (includeAllPublicRoads)
      return withRoadNumbersFilter(roadNumbers, false, "ADMINCLASS = 1")
    val limit = roadNumbers.head
    val filterAdd = s"""(ROADNUMBER >= ${limit._1} and ROADNUMBER <= ${limit._2})"""
    if (filter == "")
      withRoadNumbersFilter(roadNumbers.tail, includeAllPublicRoads, filterAdd)
    else
      withRoadNumbersFilter(roadNumbers.tail, includeAllPublicRoads, s"""$filter OR $filterAdd""")
  }

  protected def withLinkIdFilter(linkIds: Set[Long]): String = {
    withFilter("LINKID", linkIds)
  }

  private def withMmlIdFilter(mmlIds: Set[Long]): String = {
    withFilter("MTKID", mmlIds)
  }

  protected def withMtkClassFilter(ids: Set[Long]): String = {
    withFilter("MTKCLASS", ids)
  }

  protected  def withLastEditedDateFilter(lowerDate: DateTime, higherDate: DateTime): String = {
    withDateLimitFilter("LAST_EDITED_DATE", lowerDate, higherDate)
  }

  protected def combineFiltersWithAnd(filter1: String, filter2: String): String = {

    (filter1.isEmpty, filter2.isEmpty) match {
      case (true,true) => ""
      case (true,false) => filter2
      case (false,true) => filter1
      case (false,false) => "%s AND %s".format(filter1.dropRight(2), filter2.replace("\"where\":\"", ""))
    }
  }

  protected def combineFiltersWithAnd(filter1: String, filter2: Option[String]): String = {
    combineFiltersWithAnd(filter2.getOrElse(""), filter1)
  }

  protected def layerDefinitionWithoutEncoding(filter: String, customFieldSelection: Option[String] = None): String = {
    val definitionStart = "[{"
    val layerSelection = """"layerId":0,"""
    val fieldSelection = customFieldSelection match {
      case Some(fs) => s""""outFields":"""" + fs + """,CONSTRUCTIONTYPE""""
      case _ => s""""outFields":"MTKID,LINKID,MTKHEREFLIP,MUNICIPALITYCODE,VERTICALLEVEL,HORIZONTALACCURACY,VERTICALACCURACY,MTKCLASS,ADMINCLASS,DIRECTIONTYPE,CONSTRUCTIONTYPE,ROADNAME_FI,ROADNAME_SM,ROADNAME_SE,FROM_LEFT,TO_LEFT,FROM_RIGHT,TO_RIGHT,LAST_EDITED_DATE,ROADNUMBER,ROADPARTNUMBER,VALIDFROM,GEOMETRY_EDITED_DATE,CREATED_DATE,SURFACETYPE,END_DATE,OBJECTID""""
    }
    val definitionEnd = "}]"
    definitionStart + layerSelection + filter + fieldSelection + definitionEnd
  }

  protected def layerDefinition(filter: String, customFieldSelection: Option[String] = None): String = {
    URLEncoder.encode(layerDefinitionWithoutEncoding(filter, customFieldSelection) , "UTF-8")
  }

  protected def queryParameters(fetchGeometry: Boolean = true): String = {
    if (fetchGeometry) "returnGeometry=true&returnZ=true&returnM=true&geometryPrecision=3&f=pjson"
    else "f=pjson"
  }

  /**
    * Returns VVH road links in bounding box area. Municipalities are optional.
    * Used by VVHClient.fetchByRoadNumbersBoundsAndMunicipalitiesF.
    */
  def queryByRoadNumbersBoundsAndMunicipalities(bounds: BoundingRectangle, roadNumbers: Seq[(Int, Int)], municipalities: Set[Int] = Set(), includeAllPublicRoads: Boolean = false): Seq[VVHRoadlink] = {
    val roadNumberFilters = if (roadNumbers.nonEmpty || includeAllPublicRoads)
      withRoadNumbersFilter(roadNumbers, includeAllPublicRoads)
    else
      ""
    val definition = layerDefinition(combineFiltersWithAnd(withMunicipalityFilter(municipalities), roadNumberFilters))
    val url = vvhRestApiEndPoint + roadLinkDataService + "/FeatureServer/query?" +
      s"layerDefs=$definition&geometry=" + bounds.leftBottom.x + "," + bounds.leftBottom.y + "," + bounds.rightTop.x + "," + bounds.rightTop.y +
      "&geometryType=esriGeometryEnvelope&spatialRel=esriSpatialRelIntersects&" + queryParameters()

    fetchVVHFeatures(url) match {
      case Left(features) => features.map(extractVVHFeature)
      case Right(error) => throw new VVHClientException(error.toString)
    }
  }

  /**
    * Returns VVH road links in polygon area. Municipalities are optional.
    *  Polygon string example "{rings:[[[564000,6930000],[566000,6931000],[567000,6933000]]]}"
    */
  def queryRoadLinksByPolygons(polygon: Polygon): Seq[VVHRoadlink] = {
    val polygonString = stringifyPolygonGeometry(polygon)
    if (!polygonString.contains("{rings:["))
    {
      return  Seq.empty[VVHRoadlink]
    }
    val definition = layerDefinition(combineFiltersWithAnd("",""))
    val urlpoly=URLEncoder.encode(polygonString)
    val url = vvhRestApiEndPoint + roadLinkDataService + "/FeatureServer/query?" +
    s"layerDefs=$definition&geometry=" + urlpoly +
    "&geometryType=esriGeometryPolygon&spatialRel=esriSpatialRelIntersects&" + queryParameters()
    fetchVVHFeatures(url) match {
      case Left(features) => features.map(extractVVHFeature)
      case Right(error) => throw new VVHClientException(error.toString)
    }
  }

  def queryLinksIdWithRoadLinkDataServiceByPolygons(polygon: Polygon): Seq[Long] = {
    val url = vvhRestApiEndPoint + roadLinkDataService + "/FeatureServer/query"
    queryLinksIdByPolygons(polygon, url)
  }

  def queryLinksIdByPolygons(polygon: Polygon, url: String): Seq[Long] = {
    val polygonString = stringifyPolygonGeometry(polygon)
    if (!polygonString.contains("{rings:["))
    {
      return  Seq.empty[Long]
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

    fetchVVHFeatures(url, nvps) match {
      case Left(features) => features.map(extractLinkIdFromVVHFeature)
      case Right(error) => throw new VVHClientException(error.toString)
    }
  }

  def queryChangesByPolygon(polygon: Polygon): Seq[ChangeInfo] = {
    val polygonString = stringifyPolygonGeometry(polygon)
    if (!polygonString.contains("{rings:["))
      return  Seq.empty[ChangeInfo]
    val defs="[{\"layerId\":0,\"outFields\":\"OLD_ID,NEW_ID,MTKID,CHANGETYPE,OLD_START,OLD_END,NEW_START,NEW_END,CREATED_DATE,CONSTRUCTIONTYPE\"}]"
    val url = vvhRestApiEndPoint + "/Roadlink_ChangeInfo/FeatureServer/query?" +
    "layerDefs="+URLEncoder.encode(defs, "UTF-8")+"&geometry=" +  URLEncoder.encode(polygonString) + "&geometryType=esriGeometryPolygon&spatialRel=esriSpatialRelIntersects&returnGeometry=false&outFields=false&f=pjson"

    fetchVVHFeatures(url) match {
      case Left(features) => features.map(extractVVHChangeInfo)
      case Right(error) => throw new VVHClientException(error.toString)
    }
  }

  /**
    * Returns VVH road links in bounding box area. Municipalities are optional.
    * Used by VVHClient.fetchByMunicipalitiesAndBoundsF, RoadLinkService.getVVHRoadLinks(bounds, municipalities), RoadLinkService.getVVHRoadLinks(bounds),
    * PointAssetOperations.getByBoundingBox and ServicePointImporter.importServicePoints.
    */
  def queryByMunicipalitesAndBounds(bounds: BoundingRectangle, municipalities: Set[Int] = Set()): Seq[VVHRoadlink] = {
    val definition = layerDefinition(withMunicipalityFilter(municipalities))
    val url = vvhRestApiEndPoint + roadLinkDataService + "/FeatureServer/query?" +
      s"layerDefs=$definition&geometry=" + bounds.leftBottom.x + "," + bounds.leftBottom.y + "," + bounds.rightTop.x + "," + bounds.rightTop.y +
      "&geometryType=esriGeometryEnvelope&spatialRel=esriSpatialRelIntersects&" + queryParameters()

    fetchVVHFeatures(url) match {
      case Left(features) => features.map(extractVVHFeature)
      case Right(error) => throw new VVHClientException(error.toString)
    }
  }

  /**
    * Returns VVH road links. Uses Scala Future for concurrent operations.
    * Used by RoadLinkService.getRoadLinksAndChangesFromVVH(bounds, municipalities),
    * RoadLinkService.getViiteRoadLinksAndChangesFromVVH(bounds, roadNumbers, municipalities, everything, publicRoads).
    */
  def fetchByMunicipalitiesAndBoundsF(bounds: BoundingRectangle, municipalities: Set[Int]): Future[Seq[VVHRoadlink]] = {
    Future(queryByMunicipalitesAndBounds(bounds, municipalities))
  }

  def fetchRoadLinksByPolygonF(polygon : Polygon): Future[Seq[VVHRoadlink]] = {
    Future(queryRoadLinksByPolygons(polygon))
  }

  def fetchChangesByPolygonF(polygon : Polygon): Future[Seq[ChangeInfo]] = {
    Future(queryChangesByPolygon(polygon))
  }

  def fetchLinkIdsByPolygonF(polygon : Polygon): Future[Seq[Long]] = {
    Future(queryLinksIdWithRoadLinkDataServiceByPolygons(polygon))
  }

  def fetchLinkIdsByPolygonFComplementary(polygon : Polygon): Future[Seq[Long]] = {
    Future(complementaryData.queryLinksIdByPolygonsWithComplemntaryRoadLink(polygon))
  }

  /**
    * Returns VVH road links. Uses Scala Future for concurrent operations.
    * Used by RoadLinkService.getRoadLinksAndChangesFromVVH(bounds, municipalities).
    */
  def fetchByRoadNumbersBoundsAndMunicipalitiesF(bounds: BoundingRectangle, municipalities: Set[Int], roadNumbers: Seq[(Int, Int)],
                                                 includeAllPublicRoads: Boolean = false): Future[Seq[VVHRoadlink]] = {
    Future(queryByRoadNumbersBoundsAndMunicipalities(bounds, roadNumbers, municipalities, includeAllPublicRoads))
  }

  /**
    * Returns VVH road links. Uses Scala Future for concurrent operations.
    * Used by RoadLinkService.reloadRoadLinksAndChangesFromVVH(municipality),
    * RoadLinkService.getVVHRoadLinksF(municipality).
    */
  def fetchByMunicipalityF(municipality: Int): Future[Seq[VVHRoadlink]] = {
    Future(queryByMunicipality(municipality))
  }

  /**
    * Returns a sequence of VVH Road Links. Uses Scala Future for concurrent operations.
    * Used by RoadLinkService.getViiteCurrentAndComplementaryRoadLinksFromVVH(municipality, roadNumbers).
    */
  def fetchByMunicipalityAndRoadNumbersF(municipality: Int, roadNumbers: Seq[(Int, Int)]): Future[Seq[VVHRoadlink]] = {
    Future(queryByRoadNumbersAndMunicipality(municipality, roadNumbers))
  }
  /**
    * Returns VVH change data. Uses Scala Future for concurrent operations.
    * Used by RoadLinkService.getRoadLinksAndChangesFromVVH(bounds, municipalities),
    * RoadLinkService.getRoadLinksAndChangesFromVVH(bounds, bounds2),
    * RoadLinkService.getRoadLinksAndChangesFromVVH(bounds, bounds2).
    */
  def queryChangesByBoundsAndMunicipalitiesF(bounds: BoundingRectangle, municipalities: Set[Int]): Future[Seq[ChangeInfo]] = {
    val municipalityFilter = withMunicipalityFilter(municipalities)
    val definition = layerDefinition(municipalityFilter, Some("OLD_ID,NEW_ID,MTKID,CHANGETYPE,OLD_START,OLD_END,NEW_START,NEW_END,CREATED_DATE"))
    val url = vvhRestApiEndPoint + "/Roadlink_ChangeInfo/FeatureServer/query?" +
      s"layerDefs=$definition&geometry=" + bounds.leftBottom.x + "," + bounds.leftBottom.y + "," + bounds.rightTop.x + "," + bounds.rightTop.y +
      "&geometryType=esriGeometryEnvelope&spatialRel=esriSpatialRelIntersects&" + queryParameters(false)

    Future {
      fetchVVHFeatures(url) match {
        case Left(features) => features.map(extractVVHChangeInfo)
        case Right(error) => throw new VVHClientException(error.toString)
      }
    }
  }

  /**
    * Returns VVH change data. Uses Scala Future for concurrent operations.
    * Used by RoadLinkService.getRoadLinksAndChangesFromVVH(bounds, roadNumbers, municipalities, everything, publicRoads)
    */
  def queryChangesByRoadNumbersBoundsAndMunicipalitiesF(bounds: BoundingRectangle, municipalities: Set[Int]): Future[Seq[ChangeInfo]] = {
    val municipalityFilter = withMunicipalityFilter(municipalities)
    val definition = layerDefinition(municipalityFilter, Some("OLD_ID,NEW_ID,MTKID,CHANGETYPE,OLD_START,OLD_END,NEW_START,NEW_END,CREATED_DATE"))
    val url = vvhRestApiEndPoint + "/Roadlink_ChangeInfo/FeatureServer/query?" +
      s"layerDefs=$definition&geometry=" + bounds.leftBottom.x + "," + bounds.leftBottom.y + "," + bounds.rightTop.x + "," + bounds.rightTop.y +
      "&geometryType=esriGeometryEnvelope&spatialRel=esriSpatialRelIntersects&" + queryParameters(false)

    Future {
      fetchVVHFeatures(url) match {
        case Left(features) => features.map(extractVVHChangeInfo)
        case Right(error) => throw new VVHClientException(error.toString)
      }
    }
  }

  /**
    * Returns VVH change data. Uses Scala Future for concurrent operations.
    * Used by RoadLinkService.reloadRoadLinksAndChangesFromVVH(municipality).
    */
  def queryChangesByMunicipalityF(municipality: Int): Future[Seq[ChangeInfo]] = {
    val definition = layerDefinition(withMunicipalityFilter(Set(municipality)), Some("OLD_ID,NEW_ID,MTKID,CHANGETYPE,OLD_START,OLD_END,NEW_START,NEW_END,CREATED_DATE"))

    val url = vvhRestApiEndPoint + "/Roadlink_ChangeInfo/FeatureServer/query?" +
      s"layerDefs=$definition&" + queryParameters(true)

    Future {
      fetchVVHFeatures(url) match {
        case Left(features) => features.map(extractVVHChangeInfo)
        case Right(error) => throw new VVHClientException(error.toString)
      }
    }
  }


  private def extractVVHChangeInfo(feature: Map[String, Any]) = {
    val attributes = extractFeatureAttributes(feature)

    val oldId = Option(attributes("OLD_ID").asInstanceOf[BigInt]).map(_.longValue())
    val newId = Option(attributes("NEW_ID").asInstanceOf[BigInt]).map(_.longValue())
    val mmlId = attributes("MTKID").asInstanceOf[BigInt].longValue()
    val changeType = attributes("CHANGETYPE").asInstanceOf[BigInt].intValue()
    val vvhTimeStamp = Option(attributes("CREATED_DATE").asInstanceOf[BigInt]).map(_.longValue()).getOrElse(0L)
    val oldStartMeasure = extractMeasure(attributes("OLD_START"))
    val oldEndMeasure = extractMeasure(attributes("OLD_END"))
    val newStartMeasure = extractMeasure(attributes("NEW_START"))
    val newEndMeasure = extractMeasure(attributes("NEW_END"))

    ChangeInfo(oldId, newId, mmlId, changeType, oldStartMeasure, oldEndMeasure, newStartMeasure, newEndMeasure, vvhTimeStamp)
  }

  /**
    * Returns VVH road links by municipality.
    * Used by VVHClient.fetchByMunicipalityF(municipality),
    * RoadLinkService.fetchVVHRoadlinks(municipalityCode) and AssetDataImporter.adjustToNewDigitization(vvhHost).
    */
  def queryByMunicipality(municipality: Int): Seq[VVHRoadlink] = {
    val definition = layerDefinition(withMunicipalityFilter(Set(municipality)))
    val url = vvhRestApiEndPoint + roadLinkDataService + "/FeatureServer/query?" +
      s"layerDefs=$definition&${queryParameters()}"

    fetchVVHFeatures(url) match {
      case Left(features) => features.map(extractVVHFeature)
      case Right(error) => throw new VVHClientException(error.toString)
    }
  }

  /**
    * Returns VVH road links by municipality.
    * Used by VVHClient.fetchByMunicipalityAndRoadNumbersF(municipality, roadNumbers) and
    * RoadLinkService.getViiteRoadLinksFromVVH(municipality, roadNumbers).
    */
  def queryByRoadNumbersAndMunicipality(municipality: Int, roadNumbers: Seq[(Int, Int)]): Seq[VVHRoadlink] = {
    val roadNumberFilters = withRoadNumbersFilter(roadNumbers, true, "")
    val definition = layerDefinition(combineFiltersWithAnd(withMunicipalityFilter(Set(municipality)), roadNumberFilters))
    val url = vvhRestApiEndPoint + roadLinkDataService + "/FeatureServer/query?" +
      s"layerDefs=$definition&${queryParameters()}"

    fetchVVHFeatures(url) match {
      case Left(features) => features.map(extractVVHFeature)
      case Right(error) => throw new VVHClientException(error.toString)
    }
  }


  /**
    * Returns VVH road link by linkid
    * Used by Digiroad2Api.createMassTransitStop, Digiroad2Api.validateUserRights, Digiroad2Api &#47;manoeuvres DELETE endpoint, Digiroad2Api manoeuvres PUT endpoint,
    * CsvImporter.updateAssetByExternalIdLimitedByRoadType, RoadLinkService,getRoadLinkMiddlePointByLinkId, RoadLinkService.updateLinkProperties, RoadLinkService.getRoadLinkGeometry,
    * RoadLinkService.updateAutoGeneratedProperties, LinearAssetService.split, LinearAssetService.separate, MassTransitStopService.fetchRoadLink, PointAssetOperations.getById
    * and OracleLinearAssetDao.createSpeedLimit.
    */
  def fetchByLinkId(linkId: Long): Option[VVHRoadlink] = fetchByLinkIds(Set(linkId)).headOption

  /**
    * Returns VVH road links by link ids.
    * Used by VVHClient.fetchByLinkId, RoadLinkService.fetchVVHRoadlinks, SpeedLimitService.purgeUnknown, PointAssetOperations.getFloatingAssets,
    * OracleLinearAssetDao.getLinksWithLengthFromVVH, OracleLinearAssetDao.getSpeedLimitLinksById AssetDataImporter.importEuropeanRoads and AssetDataImporter.importProhibitions
    */
  def fetchByLinkIds(linkIds: Set[Long]): Seq[VVHRoadlink] = {
    fetchVVHRoadlinks(linkIds, None, true, roadLinkFromFeature, withLinkIdFilter)
  }

  def fetchByLinkIdsF(linkIds: Set[Long]) = {
    Future(fetchByLinkIds(linkIds))
  }

  /**
    * Returns VVH road link by mml id.
    * Used by RoadLinkService.getRoadLinkMiddlePointByMmlId
    */
  def fetchByMmlId(mmlId: Long): Option[VVHRoadlink] = fetchByMmlIds(Set(mmlId)).headOption

  /**
    * Returns VVH road links by mml ids.
    * Used by VVHClient.fetchByMmlId, LinkIdImporter.updateTable and AssetDataImporter.importRoadAddressData.
    */
  def fetchByMmlIds(mmlIds: Set[Long]): Seq[VVHRoadlink] = {
    fetchVVHRoadlinks(mmlIds, None, true, roadLinkFromFeature, withMmlIdFilter)
  }

  /**
    * Returns VVH road links.
    * Used by RoadLinkService.fetchVVHRoadlinks (called from CsvGenerator)
    */
  def fetchVVHRoadlinks[T](linkIds: Set[Long],
                           fieldSelection: Option[String],
                           fetchGeometry: Boolean,
                           resultTransition: (Map[String, Any], List[List[Double]]) => T): Seq[T] =
    fetchVVHRoadlinks(linkIds, fieldSelection, fetchGeometry, resultTransition, withLinkIdFilter)

  def fetchRoadLinkOrComplementaryFromVVH(linkId: Long): Option[VVHRoadlink] = {
    val vvhRoadLink = fetchByLinkId(linkId) match {
      case Some(vvhRoadLink) => Some(vvhRoadLink)
      case None => complementaryData.fetchComplementaryRoadlink(linkId)
    }
    vvhRoadLink
  }

  /**
    * Returns VVH road links.
    * Used by VVHClient.fetchByLinkIds, VVHClient.fetchByMmlIds and VVHClient.fetchVVHRoadlinks
    */
  def fetchVVHRoadlinks[T](linkIds: Set[Long],
                           fieldSelection: Option[String],
                           fetchGeometry: Boolean,
                           resultTransition: (Map[String, Any], List[List[Double]]) => T,
                           filter: Set[Long] => String): Seq[T] = {
    val batchSize = 1000
    val idGroups: List[Set[Long]] = linkIds.grouped(batchSize).toList
    idGroups.par.flatMap { ids =>
      val definition = layerDefinition(filter(ids), fieldSelection)
      val url = vvhRestApiEndPoint + roadLinkDataService + "/FeatureServer/query?" +
        s"layerDefs=$definition&${queryParameters(fetchGeometry)}"
      fetchVVHFeatures(url) match {
        case Left(features) => features.map { feature =>
          val attributes = extractFeatureAttributes(feature)
          val geometry = if (fetchGeometry) extractFeatureGeometry(feature) else Nil
          resultTransition(attributes, geometry)
        }
        case Right(error) => throw new VVHClientException(error.toString)
      }
    }.toList
  }

  /**
    * Returns VVH road links. Obtain all RoadLinks changes between two given dates.
    * Used by RoadLinkService.fetchChangedVVHRoadlinksBetweenDates (called from getRoadLinksBetweenTwoDatesFromVVH).
    */
  def fetchVVHRoadlinksChangesBetweenDates(lowerDate: DateTime, higherDate: DateTime): Seq[VVHRoadlink] = {
    val definition = layerDefinition(withLastEditedDateFilter(lowerDate, higherDate))
    val url = vvhRestApiEndPoint + roadLinkDataService + "/FeatureServer/query?" +
      s"layerDefs=$definition&${queryParameters()}"

    fetchVVHFeatures(url) match {
      case Left(features) => features.map(extractVVHFeature)
      case Right(error) => throw new VVHClientException(error.toString)
    }
  }

  case class VVHError(content: Map[String, Any], url: String)

  protected def fetchVVHFeatures(url: String): Either[List[Map[String, Any]], VVHError] = {
    val fetchVVHStartTime = System.currentTimeMillis()
    val request = new HttpGet(url)
    val client = HttpClientBuilder.create().build()
    val response = client.execute(request)
    try {
      val content: Map[String, Any] = parse(StreamInput(response.getEntity.getContent)).values.asInstanceOf[Map[String, Any]]
      val optionalLayers = content.get("layers").map(_.asInstanceOf[List[Map[String, Any]]])
      val optionalFeatureLayer = optionalLayers.flatMap { layers => layers.find { layer => layer.contains("features") } }
      val optionalFeatures = optionalFeatureLayer.flatMap { featureLayer => featureLayer.get("features").map(_.asInstanceOf[List[Map[String, Any]]]) }
      optionalFeatures.map(_.filter(roadLinkStatusFilter)).map(Left(_)).getOrElse(Right(VVHError(content, url)))
    } finally {
      response.close()
      val fetchVVHTimeSec = (System.currentTimeMillis()-fetchVVHStartTime)*0.001
      if(fetchVVHTimeSec > 5)
        logger.info("fetch vvh took %.3f sec with the following url %s".format(fetchVVHTimeSec, url))
    }
  }

  protected def fetchVVHFeatures(url: String, formparams: ArrayList[NameValuePair]): Either[List[Map[String, Any]], VVHError] = {
    val fetchVVHStartTime = System.currentTimeMillis()
    val request = new HttpPost(url)
    request.setEntity(new UrlEncodedFormEntity(formparams, "utf-8"))
    val client = HttpClientBuilder.create().build()
    val response = client.execute(request)
    try {
      val content: Map[String, Any] = parse(StreamInput(response.getEntity.getContent)).values.asInstanceOf[Map[String, Any]]
      val optionalLayers = content.get("layers").map(_.asInstanceOf[List[Map[String, Any]]])
      val optionalFeatureLayer = optionalLayers.flatMap { layers => layers.find { layer => layer.contains("features") } }
      val optionalFeatures = optionalFeatureLayer.flatMap { featureLayer => featureLayer.get("features").map(_.asInstanceOf[List[Map[String, Any]]]) }
      optionalFeatures.map(_.filter(roadLinkStatusFilter)).map(Left(_)).getOrElse(Right(VVHError(content, url)))
    } finally {
      response.close()
      val fetchVVHTimeSec = (System.currentTimeMillis()-fetchVVHStartTime)*0.001
      if(fetchVVHTimeSec > 5)
        logger.info("fetch vvh took %.3f sec with the following url %s".format(fetchVVHTimeSec, url))
    }
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

  protected def extractFeatureAttributes(feature: Map[String, Any]): Map[String, Any] = {
    feature("attributes").asInstanceOf[Map[String, Any]]
  }

  protected def extractFeatureGeometry(feature: Map[String, Any]): List[List[Double]] = {
    val geometry = feature("geometry").asInstanceOf[Map[String, Any]]
    val paths = geometry("paths").asInstanceOf[List[List[List[Double]]]]
    paths.reduceLeft((geom, nextPart) => geom ++ nextPart.tail)
  }

  protected def roadLinkFromFeature(attributes: Map[String, Any], path: List[List[Double]]): VVHRoadlink = {
    val linkGeometry: Seq[Point] = path.map(point => {
      Point(point(0), point(1), extractMeasure(point(2)).get)
    })
    val linkGeometryForApi = Map("points" -> path.map(point => Map("x" -> point(0), "y" -> point(1), "z" -> point(2), "m" -> point(3))))
    val linkGeometryWKTForApi = Map("geometryWKT" -> ("LINESTRING ZM (" + path.map(point => point(0) + " " + point(1) + " " + point(2) + " " + point(3)).mkString(", ") + ")"))
    val linkId = attributes("LINKID").asInstanceOf[BigInt].longValue()
    val municipalityCode = attributes("MUNICIPALITYCODE").asInstanceOf[BigInt].toInt
    val mtkClass = attributes("MTKCLASS")
    val featureClassCode = if (mtkClass != null) // Complementary geometries have no MTK Class
      attributes("MTKCLASS").asInstanceOf[BigInt].intValue()
    else
      0
    val featureClass = featureClassCodeToFeatureClass.getOrElse(featureClassCode, FeatureClass.AllOthers)

    VVHRoadlink(linkId, municipalityCode, linkGeometry, extractAdministrativeClass(attributes),
      extractTrafficDirection(attributes), featureClass, extractModifiedAt(attributes),
      extractAttributes(attributes) ++ linkGeometryForApi ++ linkGeometryWKTForApi, extractConstructionType(attributes), linkGeomSource)

  }

  protected def linkIdFromFeature(attributes: Map[String, Any]): Long = {
    attributes("LINKID").asInstanceOf[BigInt].longValue()
  }

  protected def extractLinkIdFromVVHFeature(feature: Map[String, Any]): Long = {
    linkIdFromFeature(extractFeatureAttributes(feature))
  }

  protected def extractVVHFeature(feature: Map[String, Any]): VVHRoadlink = {
    val attributes = extractFeatureAttributes(feature)
    val path = extractFeatureGeometry(feature)
    roadLinkFromFeature(attributes, path)
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
      "OBJECTID").contains(x)
    }.filter { case (_, value) =>
      value != null
    }
  }

  protected val featureClassCodeToFeatureClass: Map[Int, FeatureClass] = Map(
    12316 -> FeatureClass.TractorRoad,
    12141 -> FeatureClass.DrivePath,
    12314 -> FeatureClass.CycleOrPedestrianPath
  )

  private def extractModifiedAt(attributes: Map[String, Any]): Option[DateTime] = {
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
    var lastEditedDate : Option[Long] = Option(0)
    if(attributes.contains("LAST_EDITED_DATE")){
      lastEditedDate = Option(attributes("LAST_EDITED_DATE").asInstanceOf[BigInt]).map(_.toLong)
    }
    var geometryEditedDate : Option[Long] = Option(0)
      if(attributes.contains("GEOMETRY_EDITED_DATE")){
        geometryEditedDate =  Option(attributes("GEOMETRY_EDITED_DATE").asInstanceOf[BigInt]).map(_.toLong)
    }

    val latestDate = compareDateMillisOptions(lastEditedDate, geometryEditedDate)
    latestDate.orElse(validFromDate).map(modifiedTime => new DateTime(modifiedTime))
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

  private val vvhTrafficDirectionToTrafficDirection: Map[Int, TrafficDirection] = Map(
    0 -> TrafficDirection.BothDirections,
    1 -> TrafficDirection.TowardsDigitizing,
    2 -> TrafficDirection.AgainstDigitizing)

  protected def extractTrafficDirection(attributes: Map[String, Any]): TrafficDirection = {
    Option(attributes("DIRECTIONTYPE").asInstanceOf[BigInt])
      .map(_.toInt)
      .map(vvhTrafficDirectionToTrafficDirection.getOrElse(_, TrafficDirection.UnknownDirection))
      .getOrElse(TrafficDirection.UnknownDirection)
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
  def createVVHTimeStamp(offsetHours: Int): Long = {
    VVHClient.createVVHTimeStamp(offsetHours)
  }
}

class VVHComplementaryClient(vvhRestApiEndPoint: String) extends VVHClient(vvhRestApiEndPoint){

  private val roadLinkComplementaryService = "Roadlink_complimentary"
  override val linkGeomSource : LinkGeomSource = LinkGeomSource.ComplimentaryLinkInterface

  /**
    * Returns VVH road links in bounding box area. Municipalities are optional.
    * Used by VVHClient.fetchByBoundsAndMunicipalitiesF.
    */
  def queryByBoundsAndMunicipalities(bounds: BoundingRectangle, municipalities: Set[Int] = Set(), extraFilter: Option[String] = None): Seq[VVHRoadlink] = {
    val definition = layerDefinition(combineFiltersWithAnd(withMunicipalityFilter(municipalities), extraFilter),
      Option("MTKID,LINKID,MTKHEREFLIP,MUNICIPALITYCODE,VERTICALLEVEL,HORIZONTALACCURACY,VERTICALACCURACY,MTKCLASS," +
        "ADMINCLASS,DIRECTIONTYPE,ROADNAME_FI,ROADNAME_SM,ROADNAME_SE,FROM_LEFT,TO_LEFT,FROM_RIGHT,TO_RIGHT," +
        "LAST_EDITED_DATE,ROADNUMBER,ROADPARTNUMBER,VALIDFROM,GEOMETRY_EDITED_DATE,CREATED_DATE,SURFACETYPE,SUBTYPE"))
    val url = vvhRestApiEndPoint + roadLinkComplementaryService + "/FeatureServer/query?" +
      s"layerDefs=$definition&geometry=" + bounds.leftBottom.x + "," + bounds.leftBottom.y + "," + bounds.rightTop.x + "," + bounds.rightTop.y +
      "&geometryType=esriGeometryEnvelope&spatialRel=esriSpatialRelIntersects&" + queryParameters()

    resolveComplementaryVVHFeatures(url) match {
      case Left(features) => features.map(extractVVHFeature)
      case Right(error) => throw new VVHClientException(error.toString)
    }
  }

  def queryLinksIdByPolygonsWithComplemntaryRoadLink(polygon: Polygon): Seq[Long] = {
    val url = vvhRestApiEndPoint + roadLinkComplementaryService + "/FeatureServer/query"
    queryLinksIdByPolygons(polygon, url)
  }

  /**
    * Returns VVH road links in polygon area. Municipalities are optional.
    *
    */
  override def queryRoadLinksByPolygons(polygon: Polygon): Seq[VVHRoadlink] = {
    val polygonString = stringifyPolygonGeometry(polygon)
    if (!polygonString.contains("{rings:[")) //check that input is somewhat correct
    {
      return  Seq.empty[VVHRoadlink]
    }
    val definition = layerDefinition(combineFiltersWithAnd("",""))
    val urlpoly=URLEncoder.encode(polygonString)
    val url = vvhRestApiEndPoint + roadLinkComplementaryService + "/FeatureServer/query?" +
      s"layerDefs=$definition&geometry=" + urlpoly +
      "&geometryType=esriGeometryPolygon&spatialRel=esriSpatialRelIntersects&" + queryParameters()
    resolveComplementaryVVHFeatures(url) match {
      case Left(features) => features.map(extractVVHFeature)
      case Right(error) => throw new VVHClientException(error.toString)
    }
  }

  /**
    * Returns VVH complementary road links in a municipality
    * Used by VVHClient.fetchByMunicipalityAndRoadNumbers.
    */
  def queryByMunicipalityAndRoadNumbers(municipality: Int, roadNumbers: Seq[(Int, Int)]): Seq[VVHRoadlink] = {
    val roadNumberFilters = withRoadNumbersFilter(roadNumbers, true, "")
    val definition = layerDefinition(combineFiltersWithAnd(withMunicipalityFilter(Set(municipality)), roadNumberFilters), Option("MTKID,LINKID,MTKHEREFLIP,MUNICIPALITYCODE,VERTICALLEVEL,HORIZONTALACCURACY,VERTICALACCURACY,MTKCLASS,ADMINCLASS,DIRECTIONTYPE,ROADNAME_FI,ROADNAME_SM,ROADNAME_SE,FROM_LEFT,TO_LEFT,FROM_RIGHT,TO_RIGHT,LAST_EDITED_DATE,ROADNUMBER,ROADPARTNUMBER,VALIDFROM,GEOMETRY_EDITED_DATE,CREATED_DATE,SURFACETYPE,SUBTYPE"))
    val url = vvhRestApiEndPoint + roadLinkComplementaryService + "/FeatureServer/query?" +
      s"layerDefs=$definition&geometry=" +
      "&geometryType=esriGeometryEnvelope&spatialRel=esriSpatialRelIntersects&" + queryParameters()

    resolveComplementaryVVHFeatures(url) match {
      case Left(features) => features.map(extractVVHFeature)
      case Right(error) => throw new VVHClientException(error.toString)
    }
  }

  /**
    * Returns VVH complementary road links in a municipality
    * Used by VVHClient.fetchByMunicipalityAndRoadNumbers.
    */
  def queryComplimentaryByMunicipality(municipality: Int): Seq[VVHRoadlink] = {
    val definition = layerDefinition(withMunicipalityFilter(Set(municipality)), Option("MTKID,LINKID,MTKHEREFLIP,MUNICIPALITYCODE,VERTICALLEVEL,HORIZONTALACCURACY,VERTICALACCURACY,MTKCLASS,ADMINCLASS,DIRECTIONTYPE,ROADNAME_FI,ROADNAME_SM,ROADNAME_SE,FROM_LEFT,TO_LEFT,FROM_RIGHT,TO_RIGHT,LAST_EDITED_DATE,ROADNUMBER,ROADPARTNUMBER,VALIDFROM,GEOMETRY_EDITED_DATE,CREATED_DATE,SURFACETYPE,SUBTYPE"))
    val url = vvhRestApiEndPoint + roadLinkComplementaryService + "/FeatureServer/query?" +
      s"layerDefs=$definition&${queryParameters()}"

    resolveComplementaryVVHFeatures(url) match {
      case Left(features) => features.map(extractVVHFeature)
      case Right(error) => throw new VVHClientException(error.toString)
    }
  }

  /**
    * Returns VVH road links. Uses Scala Future for concurrent operations.
    * Used by RoadLinkService.getComplementaryRoadLinksFromVVH(bounds, municipalities).
    */
  def fetchByBoundsAndMunicipalitiesF(bounds: BoundingRectangle, municipalities: Set[Int]): Future[Seq[VVHRoadlink]] = {
    Future(queryByBoundsAndMunicipalities(bounds, municipalities))
  }

  /**
    * Returns VVH road links filtered by walkways (MTKCLASS=12314). Uses Scala Future for concurrent operations.
    * Used by RoadLinkService..
    *
    */
  def fetchWalkwaysByBoundsAndMunicipalitiesF(bounds: BoundingRectangle, municipalities: Set[Int]): Future[Seq[VVHRoadlink]] = {
    Future(queryByBoundsAndMunicipalities(bounds, municipalities, Some(withMtkClassFilter(Set(12314)))))
  }


  /**
    * Returns VVH road links. Uses Scala Future for concurrent operations.
    * Used by RoadLinkService.getComplementaryRoadLinksFromVVH(municipality).
    */
  def fetchByMunicipalityAndRoadNumbers(municipality: Int, roadNumbers: Seq[(Int, Int)]): Future[Seq[VVHRoadlink]] = {
    Future(queryByMunicipalityAndRoadNumbers(municipality, roadNumbers))
  }

  /**
    * Returns VVH complimentary road links. Uses Scala Future for concurrent operations.
    * Used by RoadLinkService.getComplementaryRoadLinksFromVVH(municipality).
    */
  def fetchComplimentaryByMunicipality(municipality: Int): Future[Seq[VVHRoadlink]] = {
    Future(queryComplimentaryByMunicipality(municipality))
  }


  private def resolveComplementaryVVHFeatures(url: String): Either[List[Map[String, Any]], VVHError] = {
    val request = new HttpGet(url)
    val client = HttpClientBuilder.create().build()
    val response = client.execute(request)
    try {
      val content: Map[String, Any] = parse(StreamInput(response.getEntity.getContent)).values.asInstanceOf[Map[String, Any]]
      val optionalLayers = content.get("layers").map(_.asInstanceOf[List[Map[String, Any]]])
      val optionalFeatureLayer = optionalLayers.flatMap { layers => layers.find { layer => layer.contains("features") } }
      val optionalFeatures = optionalFeatureLayer.flatMap { featureLayer => featureLayer.get("features").map(_.asInstanceOf[List[Map[String, Any]]]) }
      optionalFeatures.map(Left(_)).getOrElse(Right(VVHError(content, url)))
    } finally {
      response.close()
    }
  }

  /**
    * Returns VVH road link by linkid
    * Used by VVHClient.fetchComplementaryRoadlinks
    */
  def fetchComplementaryRoadlink(linkId: Long): Option[VVHRoadlink] = fetchComplementaryRoadlinks(Set(linkId)).headOption

  /**
    * Returns VVH road links.
    * Used by RoadLinkService.getComplementaryLinkMiddlePointByLinkId(linkId) and VVHClient.fetchComplementaryRoadlink.
    */
   def fetchComplementaryRoadlinks(linkIds: Set[Long]): Seq[VVHRoadlink] = {
    fetchComplementaryRoadlinks(linkIds, None, true, roadLinkFromFeature, withLinkIdFilter)
  }

  def fetchComplementaryRoadlinksF(linkIds: Set[Long]): Future[Seq[VVHRoadlink]] = {
    Future(fetchComplementaryRoadlinks(linkIds))
  }

  /**
    * Returns VVH road links.
    * Used by VVHClient.fetchComplementaryRoadlinks(linkId).
    */
  def fetchComplementaryRoadlinks[T](linkIds: Set[Long],
                                     fieldSelection: Option[String],
                                     fetchGeometry: Boolean,
                                     resultTransition: (Map[String, Any], List[List[Double]]) => T,
                                     filter: Set[Long] => String): Seq[T] = {
    val batchSize = 1000
    val idGroups: List[Set[Long]] = linkIds.grouped(batchSize).toList
    idGroups.par.flatMap { ids =>
      val definition = layerDefinition(filter(ids), fieldSelection)
      val url = vvhRestApiEndPoint + roadLinkComplementaryService + "/FeatureServer/query?" +
        s"layerDefs=$definition&${queryParameters(fetchGeometry)}"
      fetchVVHFeatures(url) match {
        case Left(features) => features.map { feature =>
          val attributes = extractFeatureAttributes(feature)
          val geometry = if (fetchGeometry) extractFeatureGeometry(feature) else Nil
          resultTransition(attributes, geometry)
        }
        case Right(error) => throw new VVHClientException(error.toString)
      }
    }.toList
  }

  def updateVVHFeatures(complementaryFeatures: Map[String, Any]): Either[List[Map[String, Any]], VVHError] = {
    val url = vvhRestApiEndPoint + roadLinkComplementaryService + "/FeatureServer/0/updateFeatures"
    val request = new HttpPost(url)
    request.setEntity(new UrlEncodedFormEntity(createFormParams(complementaryFeatures), "utf-8"))
    val client = HttpClientBuilder.create().build()
    val response = client.execute(request)
    try {
      val content: Map[String, Seq[Map[String, Any]]] = parse(StreamInput(response.getEntity.getContent)).values.asInstanceOf[Map[String, Seq[Map[String, Any]]]]
      content.get("updateResults").getOrElse(None) match {
        case None =>
          content.get("error").head.asInstanceOf[Map[String, Any]].getOrElse("details", None) match {
            case None => Right(VVHError(Map("error" -> "Error Without Details "), url))
            case value => Right(VVHError(Map("error details" -> value), url))
          }
        case _ =>
          content.get("updateResults").get.map(_.getOrElse("success", None)).head match {
            case None => Right(VVHError(Map("error" -> "Update status not available in JSON Response"), url))
            case true => Left(List(content))
            case false =>
              content.get("updateResults").get.map(_.getOrElse("error", None)).head.asInstanceOf[Map[String, Any]].getOrElse("description", None) match {
                case None => Right(VVHError(Map("error" -> "Error Without Information"), url))
                case value => Right(VVHError(Map("error" -> value), url))
              }
          }
      }
    } catch {
      case e: Exception => Right(VVHError(Map("error" -> e.getMessage), url))
    } finally {
      response.close()
    }
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
}

class VVHHistoryClient(vvhRestApiEndPoint: String) extends VVHClient(vvhRestApiEndPoint){

  private val roadLinkDataHistoryService = "Roadlink_data_history"
  override val linkGeomSource : LinkGeomSource = LinkGeomSource.HistoryLinkInterface

  private def historyLayerDefinition(filter: String, customFieldSelection: Option[String] = None): String = {
    val definitionStart = "[{"
    val layerSelection = """"layerId":0,"""
    val fieldSelection = customFieldSelection match {
      case Some(fs) => s""""outFields":"""" + fs + """,CONSTRUCTIONTYPE""""
      case _ => s""""outFields":"MTKID,LINKID,MTKHEREFLIP,MUNICIPALITYCODE,VERTICALLEVEL,HORIZONTALACCURACY,VERTICALACCURACY,MTKCLASS,ADMINCLASS,DIRECTIONTYPE,CONSTRUCTIONTYPE,ROADNAME_FI,ROADNAME_SM,ROADNAME_SE,FROM_LEFT,TO_LEFT,FROM_RIGHT,TO_RIGHT,LAST_EDITED_DATE,ROADNUMBER,ROADPARTNUMBER,VALIDFROM,GEOMETRY_EDITED_DATE,SURFACETYPE,END_DATE,LINKID_NEW,OBJECTID,CREATED_DATE""""
    }
    val definitionEnd = "}]"
    val definition = definitionStart + layerSelection + filter + fieldSelection + definitionEnd
    URLEncoder.encode(definition, "UTF-8")
  }

  private def extractVVHHistoricFeature(feature: Map[String, Any]): VVHHistoryRoadLink = {
    val attributes = extractFeatureAttributes(feature)
    val path = extractFeatureGeometry(feature)
    historicRoadLinkFromFeature(attributes,path)
  }

  private def historicRoadLinkFromFeature(attributes: Map[String, Any], path: List[List[Double]]): VVHHistoryRoadLink = {
    val linkGeometry: Seq[Point] = path.map(point => {
      Point(point(0), point(1), extractMeasure(point(2)).get)
    })
    val municipalityCode = attributes("MUNICIPALITYCODE").asInstanceOf[BigInt].toInt
    val linkGeometryForApi = Map("points" -> path.map(point => Map("x" -> point(0), "y" -> point(1), "z" -> point(2), "m" -> point(3))))
    val linkGeometryWKTForApi = Map("geometryWKT" -> ("LINESTRING ZM (" + path.map(point => point(0) + " " + point(1) + " " + point(2) + " " + point(3)).mkString(", ") + ")"))
    val linkId = attributes("LINKID").asInstanceOf[BigInt].longValue()
    val createdDate = attributes("CREATED_DATE").asInstanceOf[BigInt].longValue()
    val endTime = attributes("END_DATE").asInstanceOf[BigInt].longValue()
    val mtkClass = attributes("MTKCLASS")
    val featureClassCode = if (mtkClass != null) // Complementary geometries have no MTK Class
      attributes("MTKCLASS").asInstanceOf[BigInt].intValue()
    else
      0
    val featureClass = featureClassCodeToFeatureClass.getOrElse(featureClassCode, FeatureClass.AllOthers)

    VVHHistoryRoadLink(linkId, municipalityCode, linkGeometry, extractAdministrativeClass(attributes),
      extractTrafficDirection(attributes), featureClass, createdDate, endTime, extractAttributes(attributes) ++ linkGeometryForApi ++ linkGeometryWKTForApi)
  }


  /**
    * Returns VVH road links. Uses Scala Future for concurrent operations.
    * Used by RoadLinkService.getRoadLinksAndChangesFromVVH(bounds, municipalities).
    */
  def fetchVVHRoadlinkHistoryF(linkIds: Set[Long] = Set()): Future[Seq[VVHHistoryRoadLink]] = {
    Future(fetchVVHRoadlinkHistory(linkIds))
  }

  /**
  * Returns VVH road links. Uses Scala Future for concurrent operations.
  * Used by RoadLinkService.getRoadLinksAndChangesFromVVH(bounds, municipalities).
  */
  def fetchVVHRoadlinkHistoryMunicipalityF(bounds: BoundingRectangle, municipalities: Set[Int]): Future[Seq[VVHRoadlink]] = {
    Future(fetchVVHRoadlinkHistoryByBoundsAndMunicipalities(bounds, municipalities))
  }

  private def makeFilter[T](attributeName: String, ids: Set[T]): String = {
    val filter =
      if (ids.isEmpty) {
        ""
      } else {
        val query = ids.mkString(",")
        s"where=$attributeName IN ($query)"
      }
    filter
  }

  private def linkIdFilter(linkIds: Set[Long]): String = {
    withFilter("LINKID", linkIds)
  }

  private def layersMapToFeaturesMap(map: Map[String, Any]): Option[Any] = {
    val layerZero = map.get("layers")
    layerZero match {
      case Some(layerList) => layerList.asInstanceOf[List[Map[String, Any]]].headOption.flatMap(x => x.get("features"))
      case _ => None
    }
  }

  private def fetchVVHHistoryFeatures(url: String): Either[List[Map[String, Any]], VVHError] = {
    val request = new HttpGet(url)
    val client = HttpClientBuilder.create().build()
    val response = client.execute(request)
    try {
      val content: Map[String, Any] = parse(StreamInput(response.getEntity.getContent)).values.asInstanceOf[Map[String, Any]]
      val mapping = {
        if (content.contains("layers"))
          layersMapToFeaturesMap(content)
        else
          content.get("features")
      }
      mapping match{
        case Some(features) =>
          Left(features.asInstanceOf[List[Map[String, Any]]])
        case _ =>
          Right(VVHError(content, url))
      }
    } finally {
      response.close()
    }
  }

  /**
    * Returns VVH road link history data in bounding box area. Municipalities are optional.
    * Used by VVHClient.fetchVVHRoadlinksF, RoadLinkService.getVVHRoadLinks(bounds, municipalities), RoadLinkService.getVVHRoadLinks(bounds),
    * PointAssetService.getByBoundingBox and ServicePointImporter.importServicePoints.
    */
  def fetchVVHRoadlinkHistory(linkIds: Set[Long] = Set()): Seq[VVHHistoryRoadLink] = {
    if (linkIds.isEmpty)
      Nil
    else {
      val batchSize = 1000
      val idGroups: List[Set[Long]] = linkIds.grouped(batchSize).toList
      idGroups.par.flatMap { ids =>
        val definition = historyLayerDefinition(linkIdFilter(ids))
        val url = vvhRestApiEndPoint + roadLinkDataHistoryService + "/FeatureServer/query?layerDefs=" +
          definition + "&" +
          queryParameters()
        fetchVVHHistoryFeatures(url) match {
          case Left(features) => features.map(extractVVHHistoricFeature)
          case Right(error) => throw new VVHClientException(error.toString)
        }
      }.toList
    }
  }

  /**
  * Returns VVH road link history data in bounding box area. Municipalities are optional.
  * Used by VVHClient.fetchVVHRoadlinksF, RoadLinkService.getVVHRoadLinks(bounds, municipalities), RoadLinkService.getVVHRoadLinks(bounds),
  * PointAssetService.getByBoundingBox and ServicePointImporter.importServicePoints.
  */
  def fetchVVHRoadlinkHistoryByBoundsAndMunicipalities(bounds: BoundingRectangle, municipalities: Set[Int] = Set()): Seq[VVHRoadlink] = {
    val definitions = historyLayerDefinition(withMunicipalityFilter(municipalities))
    val url= vvhRestApiEndPoint + roadLinkDataHistoryService + "/FeatureServer/query?" +
    s"layerDefs=$definitions&geometry=" + bounds.leftBottom.x + "," + bounds.leftBottom.y + "," + bounds.rightTop.x + "," + bounds.rightTop.y +
    "&geometryType=esriGeometryEnvelope&spatialRel=esriSpatialRelIntersects&" + queryParameters()
    fetchVVHFeatures(url) match {
      case Left(features) => features.map(extractVVHFeature)
      case Right(error) => throw new VVHClientException(error.toString)
    }
  }
}





