package fi.liikennevirasto.digiroad2.client.kgv

import org.locationtech.jts.geom.Polygon
import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.kgv.KgvCollection.Changes
import fi.liikennevirasto.digiroad2.client.{ClientException, FeatureClass, Filter, LinkOperationError, RoadLinkFetched}
import fi.liikennevirasto.digiroad2.util.{Digiroad2Properties, KgvUtil, LogUtils, Parallel}
import org.apache.http.HttpStatus
import org.apache.http.client.config.{CookieSpecs, RequestConfig}
import org.apache.http.client.methods.{CloseableHttpResponse, HttpGet, HttpRequestBase}
import org.apache.http.impl.client.HttpClients
import org.joda.time.DateTime
import org.json4s.jackson.JsonMethods.parse
import org.json4s.{DefaultFormats, StreamInput}
import org.slf4j.LoggerFactory

import java.math.BigInteger
import java.net.URLEncoder
import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}
import fi.liikennevirasto.digiroad2.{Geometry => UtilGeometry}

sealed case class Link(title:String,`type`: String,rel:String,href:String)
sealed case class FeatureCollection(`type`: String, features: List[BaseFeature], crs: Option[Map[String, Any]] = None, numberReturned: Int = 0, nextPageLink: String = "", previousPageLink: String = "")
sealed class BaseFeature(`type`: String, properties: Map[String, Any])
sealed case class LineFeature(`type`: String, geometry: Geometry, properties: Map[String, Any]) extends BaseFeature(`type`, properties)
sealed case class PolygonFeature(`type`: String, polygonGeometry: List[UtilGeometry], properties: Map[String, Any]) extends BaseFeature(`type`, properties)
sealed case class Geometry(`type`: String, coordinates: List[List[Double]])
sealed case class MunicipalityBorders(municipalityCode: Int, geometry: List[UtilGeometry])

trait KgvCollection {
  def value :String
}
object DummyCollection {
  case object Dummy extends KgvCollection { def value = "" }
} 
object KgvCollection {
  case object Frozen extends KgvCollection { def value = "keskilinjavarasto:frozenlinks" }
  case object Changes extends KgvCollection { def value = "keskilinjavarasto:change" }
  case object UnFrozen extends KgvCollection { def value = "keskilinjavarasto:road_links" }
  case object LinkVersios extends KgvCollection { def value = "keskilinjavarasto:road_links_versions" }
  case object LinkCorreponceTable extends KgvCollection { def value = "keskilinjavarasto:frozenlinks_vastintaulu" }
  case object MunicipalityBorders extends KgvCollection { def value = "paikkatiedot:kuntarajat_10k"}
}

object FilterOgc extends Filter {

  val singleFilter= (field:String,value:String) => s"${field}='${value}'"
  val singleAddQuotation= (value:String) => s"'${value}'"
  
  override def withFilter[T](attributeName: String, ids: Set[T]): String = {
      if (ids.nonEmpty) 
        s"${attributeName.toLowerCase} IN (${ids.map(t=>singleAddQuotation(t.toString)).mkString(",")})"
      else ""
  }
  
  override def withMunicipalityFilter(municipalities: Set[Int]): String = {
    if (municipalities.size == 1) singleFilter("municipalitycode",municipalities.head.toString)
    else withFilter("municipalitycode",municipalities)
  }
  override def withRoadNameFilter[T](attributeName: String, names: Set[T]): String = {
      if (names.nonEmpty) withFilter(attributeName,names) 
      else ""
  }

  override def combineFiltersWithAnd(filter1: String, filter2: String): String = {
    (filter1.isEmpty, filter2.isEmpty) match {
      case (true,true) => ""
      case (true,false) => filter2
      case (false,true) => filter1
      case (false,false) => s"$filter1 AND $filter2"
    }
  }

  override def combineFiltersWithAnd(filter1: String, filter2: Option[String]): String =   {
    combineFiltersWithAnd(filter2.getOrElse(""), filter1)
  }
  
  // Query filters methods
  override def withLinkIdFilter[T](linkIds: Set[T]): String = {
      if (linkIds.nonEmpty) withFilter("id",linkIds)
      else ""
  }

  override def withOldkmtkidFilter(linkIds: Set[String]): String = {
    if (linkIds.nonEmpty) withFilter("oldkmtkid",linkIds)
    else ""
  }
  
  override def withFinNameFilter(roadNameSource: String)(roadNames: Set[String]): String = {
      if (roadNames.nonEmpty) withFilter(roadNameSource,roadNames)
      else ""
  }

  override def withMtkClassFilter(ids: Set[Long]): String = {
      if (ids.nonEmpty) withFilter("roadclass",ids)
      else ""
  }
  def withContructionFilter(values: Set[Int]): String = {
      if (values.nonEmpty) withFilter("lifecyclestatus",values)
      else ""
  }

  override def withLastEditedDateFilter(lowerDate: DateTime, higherDate: DateTime): String = {
    withDateLimitFilter("versionstarttime",lowerDate, higherDate)
  }
  
  override def withMmlIdFilter(mmlIds: Set[Long]): String = {
    if (mmlIds.nonEmpty) withFilter("sourceid",mmlIds)
    else ""
  }
  
  override def withDateLimitFilter(attributeName: String, lowerDate: DateTime, higherDate: DateTime): String = {
    s"$attributeName >= $lowerDate and $attributeName <= $higherDate"
  }
  
}


class ExtractorBase {
  lazy val logger = LoggerFactory.getLogger(getClass)
  type LinkType

  def extractFeature(feature: LineFeature, path: List[List[Double]], linkGeomSource: LinkGeomSource): LinkType = ???
  
  protected def featureClassCodeToFeatureClass(code: Int) = {
    KgvUtil.extractFeatureClass(code)
  }

  protected val trafficDirectionToTrafficDirection: Map[Int, TrafficDirection] = Map(
    0 -> TrafficDirection.BothDirections,
    1 -> TrafficDirection.TowardsDigitizing,
    2 -> TrafficDirection.AgainstDigitizing)

  protected  def extractAdministrativeClass(attributes: Map[String, Any]): AdministrativeClass = {
    if (attributes("adminclass").asInstanceOf[String] != null)
      Option(attributes("adminclass").asInstanceOf[String].toInt)
        .map(AdministrativeClass.apply)
        .getOrElse(Unknown)
    else Unknown
  }

  def extractConstructionType(attributes: Map[String, Any]): ConstructionType = {
    if (attributes("lifecyclestatus").asInstanceOf[String] != null)
      Option(attributes("lifecyclestatus").asInstanceOf[String].toInt)
        .map(ConstructionType.apply)
        .getOrElse(ConstructionType.InUse)
    else ConstructionType.InUse
  }

  protected def extractTrafficDirection(attributes: Map[String, Any]): TrafficDirection = {
    if (attributes("directiontype").asInstanceOf[String] != null)
      Option(attributes("directiontype").asInstanceOf[String].toInt)
        .map(trafficDirectionToTrafficDirection.getOrElse(_, TrafficDirection.UnknownDirection))
        .getOrElse(TrafficDirection.UnknownDirection)
    else TrafficDirection.UnknownDirection
  }


  /**
    * Extract double value from data. Used for change info start and end measures.
    */
  protected def anyToDouble(value: Any): Option[Double] = {
    value match {
      case null => None
      case _ => {
        val doubleValue = Try(value.toString.toDouble).getOrElse(throw new NumberFormatException(s"Failed to convert value: ${value.toString}") )
        Some(doubleValue)
      }
    }
  }

  def toBigInt(value: Long): BigInt = {
    Try(BigInt(value)).getOrElse(throw new NumberFormatException(s"Failed to convert value: ${value.toString}"))
  }

  protected def extractModifiedAt(createdDate:Option[Long],lastEdited:Option[Long]): Option[DateTime] = {
    KgvUtil.extractModifiedAt(createdDate,lastEdited)
  }
  
  protected def extractAttributes(attributesMap: Map[String, Any], lastEditedDate:BigInt, starttime:BigInt): Map[String, Any] = {
    case class NumberConversionFailed(msg:String)extends Exception(msg)
    def numberConversion(field:String): BigInt = {
      if (attributesMap(field) == null){
        null
      }else {
        try {
          toBigInt(attributesMap(field).toString.toLong)
        } catch {
          case _: Exception =>
            throw NumberConversionFailed(s"Failed to retrieve value ${field}: ${attributesMap(field)}")
        }
      }
    }
    
    Map(
      "ROADNUMBER"            -> numberConversion("roadnumber"),
      "ROADPARTNUMBER"        -> numberConversion("roadpartnumber"),
      "MUNICIPALITYCODE"      -> numberConversion("municipalitycode"),
      "SURFACETYPE"           -> numberConversion("surfacetype"),

      "MTKID"                 -> numberConversion("sourceid"),
      "MTKCLASS"              -> numberConversion("roadclass"),
      "HORIZONTALACCURACY"    -> anyToDouble(attributesMap("xyaccuracy")),
      "VERTICALACCURACY"      -> anyToDouble(attributesMap("zaccuracy")),
      "VERTICALLEVEL"         -> numberConversion("surfacerelation"),
      "CONSTRUCTIONTYPE"      -> numberConversion("lifecyclestatus"),
      "ROADNAME_FI"           -> attributesMap("roadnamefin"),
      "ROADNAME_SE"           -> attributesMap("roadnameswe"),
      "ROADNAMESME"           -> attributesMap("roadnamesme"),
      "ROADNAMESMN"           -> attributesMap("roadnamesmn"),
      "ROADNAMESMS"           -> attributesMap("roadnamesms"),

      "FROM_RIGHT"            -> numberConversion("addressfromright"),
      "TO_RIGHT"              -> numberConversion("addresstoright"),
      "FROM_LEFT"             -> numberConversion("addressfromleft"),
      "TO_LEFT"               -> numberConversion("addresstoleft"),

      "MTKHEREFLIP"           -> attributesMap("geometryflip"),
      "CREATED_DATE"          -> starttime,
      "LAST_EDITED_DATE"      -> lastEditedDate
    )
  }

}
class Extractor extends ExtractorBase {
  
  override type LinkType = RoadLinkFetched
  
  override def extractFeature(feature: LineFeature, path: List[List[Double]], linkGeomSource: LinkGeomSource): LinkType = {
    val attributes = feature.properties
    
    val lastEditedDate = Option(new DateTime(attributes("versionstarttime").asInstanceOf[String]).getMillis)
    val startTime = Option(new DateTime(attributes("starttime").asInstanceOf[String]).getMillis)

    val linkGeometry: Seq[Point] = path.map(point => {
      Point(anyToDouble(point(0)).get, anyToDouble(point(1)).get, anyToDouble(point(2)).get)
    })
    val linkGeometryForApi = Map("points" -> path.map(point => Map("x" -> anyToDouble(point(0)).get, "y" -> anyToDouble(point(1)).get, "z" -> anyToDouble(point(2)).get, "m" -> anyToDouble(point(3)).get)))
    val linkGeometryWKTForApi = Map("geometryWKT" -> (s"LINESTRING ZM (${path.map(point => anyToDouble(point(0)).get + " " + anyToDouble(point(1)).get + " " + anyToDouble(point(2)).get + " " + anyToDouble(point(3)).get).mkString(", ")})"))

    val linkId = attributes("id").asInstanceOf[String]
    val municipalityCode = attributes("municipalitycode").asInstanceOf[String].toInt

    val geometryLength: Double = anyToDouble(attributes("horizontallength")).getOrElse(0.0)

    val roadClassCode = attributes("roadclass").asInstanceOf[String].toInt

    val roadClass = featureClassCodeToFeatureClass(roadClassCode)

    RoadLinkFetched(linkId, municipalityCode,
      linkGeometry,
      extractAdministrativeClass(attributes),
      extractTrafficDirection(attributes), roadClass, extractModifiedAt(startTime,lastEditedDate),
      extractAttributes(attributes,BigInteger.valueOf(lastEditedDate.getOrElse(0)),BigInteger.valueOf(startTime.getOrElse(0)))
        ++ linkGeometryForApi ++ linkGeometryWKTForApi
      , extractConstructionType(attributes), linkGeomSource, geometryLength)
  }
}

abstract class KgvOperation(extractor:ExtractorBase) extends LinkOperationsAbstract{
  type LinkType
  type Content = FeatureCollection
  
  protected val linkGeomSource: LinkGeomSource
  protected def serviceName: String
  private val cqlLang = "cql-text"
  private val bboxCrsType = "EPSG%3A3067"
  protected val crs = "EPSG%3A3067"
  private val WARNING_LEVEL: Int = 10
  
  // This is way to bypass AWS API gateway 10MB limitation, tune it if item size increase or degrease 
  private val BATCH_SIZE: Int = 4999
  // Limit size of url query, Too big query string result error 414
  private val BATCH_SIZE_FOR_SELECT_IN_QUERY: Int = 150
  
  override protected implicit val jsonFormats = DefaultFormats.preservingEmptyValues

  protected def convertToFeature(content: Map[String, Any]): BaseFeature = {
    val geometry = if (serviceName == Changes.value) Geometry("", List())
    else {
      Geometry(`type` = content("geometry").asInstanceOf[Map[String, Any]]("type").toString,
        coordinates = content("geometry").asInstanceOf[Map[String, Any]]("coordinates").asInstanceOf[List[List[Double]]])
    }
    LineFeature(
      `type` = content("type").toString,
      geometry = geometry,
      properties = content("properties").asInstanceOf[Map[String, Any]]
    )
  }

  protected def encode(url: String): String = {
    URLEncoder.encode(url, "UTF-8")
  }

  protected def addHeaders(request: HttpRequestBase): Unit = {
    request.addHeader("X-API-Key", Digiroad2Properties.kgvApiKey)
    request.addHeader("accept","application/geo+json")
  }

  /**
    * Constructions Types Allows to return
    * In Use - 0
    * Under Construction - 1
    * Planned - 3
    */
  private def roadLinkStatusFilter(feature: Map[String, Any]): Boolean = {
    val attributes = feature("properties").asInstanceOf[Map[String, Any]]
    val linkStatus = extractor.extractConstructionType(attributes)
    linkStatus == ConstructionType.InUse || linkStatus == ConstructionType.Planned || linkStatus == ConstructionType.UnderConstruction
  }

  protected def fetchFeatures(url: String): Either[LinkOperationError, Option[FeatureCollection]] = {
    val request = new HttpGet(url)
    addHeaders(request)
    
    val client =  HttpClients.custom()
                  .setDefaultRequestConfig( RequestConfig.custom()
                                  .setCookieSpec(CookieSpecs.STANDARD)
                                  .build())
                  .build()
    
    var response: CloseableHttpResponse = null
    LogUtils.time(logger, s"fetch features",url = Some(url)) {
      try {
        response = client.execute(request)
        val statusCode = response.getStatusLine.getStatusCode
        if (statusCode == HttpStatus.SC_OK) {
          val feature = parse(StreamInput(response.getEntity.getContent)).values.asInstanceOf[Map[String, Any]]
          val resort = feature("type").toString match {
            case "Feature" => 
              if (roadLinkStatusFilter(feature)){
                Some(FeatureCollection(
                  `type` = "FeatureCollection",
                  features = List(convertToFeature(feature)),
                  crs = Try(Some(feature("crs").asInstanceOf[Map[String, Any]])).getOrElse(None)))
              } else None
            case "FeatureCollection" =>
              val links = feature("links").asInstanceOf[List[Map[String, Any]]].map(link =>
                Link(link("title").asInstanceOf[String], link("type").asInstanceOf[String],
                  link("rel").asInstanceOf[String], link("href").asInstanceOf[String]))

              val nextLink = Try(links.find(_.title == "Next page").get.href).getOrElse("")
              val previousLink = Try(links.find(_.title == "Previous page").get.href).getOrElse("")
              val features = if (serviceName == Changes.value) {
                feature("features").asInstanceOf[List[Map[String, Any]]].map(convertToFeature)
              }
              else {
                feature("features").asInstanceOf[List[Map[String, Any]]]
                  .filter(roadLinkStatusFilter)
                  .map(convertToFeature)
              }
              Some(FeatureCollection(
                `type` = "FeatureCollection",
                features = features,
                crs = Try(Some(feature("crs").asInstanceOf[Map[String, Any]])).getOrElse(None),
                numberReturned = feature("numberReturned").asInstanceOf[BigInt].toInt,
                nextLink, previousLink
              ))
            case _ => None
          }
          Right(resort)
        } else {
          Left(LinkOperationError(response.getStatusLine.getReasonPhrase, response.getStatusLine.getStatusCode.toString,url))
        }
      }
      catch {
        case e: Exception => Left(LinkOperationError(e.toString, ""))
      }
      finally {
        if (response != null) {
          response.close()
        }
      }
    }
  }

  private def paginationRequest(base:String,limit:Int,startIndex:Int = 0,firstRequest:Boolean = true ): (String,Int) = {
    if (firstRequest) (s"${base}&limit=${limit}&startIndex=${startIndex}",limit)
    else (s"${base}&limit=${limit}&startIndex=${startIndex}",startIndex+limit)
  }
  
  private def queryWithPaginationThreaded(baseUrl: String = ""): Seq[LinkType] = {
    val pageAllReadyFetched: mutable.HashSet[String] = new mutable.HashSet()

    @tailrec
    def paginateAtomic(finalResponse: Set[FeatureCollection] = Set(), baseUrl: String = "", limit: Int, position: Int,counter:Int =0): Set[FeatureCollection] = {
      val (url, newPosition) = paginationRequest(baseUrl, limit, firstRequest = false, startIndex = position)
      if (!pageAllReadyFetched.contains(url)) {
        val resort = fetchFeatures(url) match {
          case Right(features) => features
          case Left(error) => throw new ClientException(error.toString)
        }
        pageAllReadyFetched.add(url)
        resort match {
          case Some(feature) if feature.numberReturned == 0 => finalResponse
          case Some(feature) if feature.numberReturned != 0 =>
              if ( counter == WARNING_LEVEL) logger.warn(s"Getting the result is taking very long time, URL was : $url")
            if(feature.nextPageLink.nonEmpty) {
              paginateAtomic(finalResponse ++ Set(feature), baseUrl, limit, newPosition, counter+1)
            }else {
              finalResponse ++ Set(feature)
            }
          case None => finalResponse
        }
      } else {
        paginateAtomic(finalResponse, baseUrl, limit, newPosition,counter+1)
      }
    }

    val limit = BATCH_SIZE

    val resultF = for (
      f1 <- Future {paginateAtomic(baseUrl = baseUrl, limit = limit, position = 0)};
      f2 <- Future {paginateAtomic(baseUrl = baseUrl, limit = limit, position = limit * 2)};
      f3 <- Future {paginateAtomic(baseUrl = baseUrl, limit = limit, position = limit * 3)}
    ) yield f1 ++ f2 ++ f3

    val operations = resultF.map { t => t.flatMap(_.features.par.map(feature =>
      extractor.extractFeature(feature.asInstanceOf[LineFeature], feature.asInstanceOf[LineFeature].geometry.coordinates, linkGeomSource)
        .asInstanceOf[LinkType]).toList).toSeq}
    try {
      Await.result(operations, Duration.Inf)
    } catch {
      case e: Throwable =>
        logger.error(s"Failed to retrieve links by using pagination with message ${e.getMessage} and stacktrace: ",e);
        throw e
    }
  }

  override protected def queryByMunicipalitiesAndBounds(bounds: BoundingRectangle, municipalities: Set[Int],
                                                        filter: Option[String]): Seq[LinkType] = {
    val bbox = s"${bounds.leftBottom.x},${bounds.leftBottom.y},${bounds.rightTop.x},${bounds.rightTop.y}"
    val filterString  = if (municipalities.nonEmpty || filter.isDefined){
      s"filter=${encode(FilterOgc.combineFiltersWithAnd(FilterOgc.withMunicipalityFilter(municipalities), filter))}"
    }else {
      ""
    }
    fetchFeatures(s"$restApiEndPoint/${serviceName}/items?bbox=$bbox&filter-lang=$cqlLang&bbox-crs=$bboxCrsType&crs=$crs&$filterString") 
    match {
      case Right(features) =>features.get.features.map(feature=>
        extractor.extractFeature(feature.asInstanceOf[LineFeature],feature.asInstanceOf[LineFeature].geometry.coordinates,linkGeomSource).asInstanceOf[LinkType])
      case Left(error) => throw new ClientException(error.toString)
    }
  }

 override protected def queryByMunicipality(municipality: Int, filter: Option[String] = None): Seq[LinkType] = {
    val filterString  = s"filter=${encode(FilterOgc.combineFiltersWithAnd(FilterOgc.withMunicipalityFilter(Set(municipality)), filter))}"
    queryWithPaginationThreaded(s"${restApiEndPoint}/${serviceName}/items?${filterString}&filter-lang=${cqlLang}&crs=${crs}")
  }

  override protected def queryByPolygons(polygon: Polygon): Seq[LinkType] = {
    if(polygon.getCoordinates.size == 0)
      return Seq[LinkType]()
    val filterString  = s"filter=${(s"INTERSECTS(geometry,${encode(polygon.toString)}")})"
    val queryString = s"?${filterString}&filter-lang=${cqlLang}&crs=${crs}"
    fetchFeatures(s"${restApiEndPoint}/${serviceName}/items/${queryString}") match {
      case Right(features) =>features.get.features.map(t=>extractor.extractFeature(t.asInstanceOf[LineFeature],t.asInstanceOf[LineFeature].geometry.coordinates,linkGeomSource).asInstanceOf[LinkType])
      case Left(error) => throw new ClientException(error.toString)
    }
  }

  override protected def queryLinksIdByPolygons(polygon: Polygon): Seq[String] = {
    if(polygon.getCoordinates.size == 0)
      return Seq[String]()
    val filterString  = s"filter=${(s"INTERSECTS(geometry,${encode(polygon.toString)}")})"
    val queryString = s"?${filterString}&filter-lang=${cqlLang}&crs=${crs}"
    fetchFeatures(s"${restApiEndPoint}/${serviceName}/items/${queryString}") match {
      case Right(features) =>features.get.features.map(_.asInstanceOf[LineFeature].properties.get("id").asInstanceOf[String])
      case Left(error) => throw new ClientException(error.toString)
    }
  }
  
  override protected def queryByIds[LinkType](idSet: Set[String],filter: Set[String] => String): Seq[LinkType] = {
    new Parallel().operation(idSet.grouped(BATCH_SIZE_FOR_SELECT_IN_QUERY).toList.par,2){
      _.flatMap(ids=>queryByFilter(Some(filter(ids)))).toList
    }
  }
  
  override protected def queryByLinkIds[LinkType](linkIds: Set[String], filter: Option[String] = None): Seq[LinkType] = {
    new Parallel().operation( linkIds.grouped(BATCH_SIZE_FOR_SELECT_IN_QUERY).toList.par,2){
      _.flatMap(ids=>queryByLinkIdsUsingFilter(ids,filter)).toList
    }
  }

  protected def queryByLinkIdsUsingFilter[LinkType](linkIds: Set[String],filter: Option[String]): Seq[LinkType] = {
    queryByFilter(Some(FilterOgc.combineFiltersWithAnd(FilterOgc.withLinkIdFilter(linkIds), filter)))
  }

  protected def queryByFilter[LinkType](filter:Option[String],pagination:Boolean = false): Seq[LinkType] = {
    val filterString  = if (filter.nonEmpty) s"&filter=${encode(filter.get)}" else ""
    val url = s"${restApiEndPoint}/${serviceName}/items?filter-lang=${cqlLang}&crs=${crs}${filterString}"
    if(!pagination){
      fetchFeatures(url) match {
        case Right(features) =>features.get.features.map(feature=>
          extractor.extractFeature(feature.asInstanceOf[LineFeature],feature.asInstanceOf[LineFeature].geometry.coordinates,linkGeomSource).asInstanceOf[LinkType])
        case Left(error) => throw new ClientException(error.toString)
      }
    }else {
      queryWithPaginationThreaded(url).asInstanceOf[Seq[LinkType]]
    }
  }

  protected def queryByDatetimeAndFilter[LinkType](lowerDate: DateTime, higherDate: DateTime,filter:Option[String]=None): Seq[LinkType] = {
    val filterString  = if (filter.nonEmpty) s"&filter=${encode(filter.get)}" else ""
    queryWithPaginationThreaded(s"${restApiEndPoint}/${serviceName}" +
      s"/items?datetime=${encode(lowerDate.toString)}/${encode(higherDate.toString)}&filter-lang=${cqlLang}&crs=${crs}${filterString}").asInstanceOf[Seq[LinkType]]
  }

  protected def queryByLastEditedDate[LinkType](lowerDate: DateTime, higherDate: DateTime): Seq[LinkType] = {
    val filterString  = s"&filter=${encode(FilterOgc.withLastEditedDateFilter(lowerDate,higherDate))}"
    queryWithPaginationThreaded(s"${restApiEndPoint}/${serviceName}" +
      s"/items?filter-lang=${cqlLang}&crs=${crs}${filterString}").asInstanceOf[Seq[LinkType]]
  }

  override protected def queryByMunicipalitiesAndBounds(bounds: BoundingRectangle, municipalities: Set[Int]): Seq[LinkType] = {
    queryByMunicipalitiesAndBounds(bounds, municipalities, None)
  }

}
