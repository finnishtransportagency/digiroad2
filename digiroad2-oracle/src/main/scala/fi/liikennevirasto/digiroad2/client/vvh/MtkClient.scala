package fi.liikennevirasto.digiroad2.client.vvh

import com.vividsolutions.jts.geom.Polygon
import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.Filter.anyToDouble
import fi.liikennevirasto.digiroad2.util.{Digiroad2Properties, LogUtils}
import org.apache.http.HttpStatus
import org.apache.http.client.config.{CookieSpecs, RequestConfig}
import org.apache.http.client.methods.{CloseableHttpResponse, HttpGet, HttpRequestBase}
import org.apache.http.impl.client.{HttpClientBuilder, HttpClients}
import org.joda.time.DateTime
import org.json4s.jackson.JsonMethods.parse
import org.json4s.{DefaultFormats, StreamInput}

import java.net.URLEncoder
import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.util.Try

sealed case class FeatureCollection(`type`: String, features: List[Feature], crs: Option[Map[String, Any]] = None,
                                    numberReturned:Int=0,
                                    nextPageLink:String="")
sealed case class Feature(`type`: String, geometry: Geometry, properties: Map[String, Any])
sealed case class Geometry(`type`: String, coordinates: List[List[Double]])

sealed trait Collection {
  def value :String
}
  
object MtkCollection {
  case object Frozen extends Collection { def value = "keskilinjavarasto:frozenlinks" }
  case object Changes extends Collection { def value = "keskilinjavarasto:change" }
  case object UnFrozen extends Collection { def value = "keskilinjavarasto:road_links" }
  case object LinkVersios extends Collection { def value = "keskilinjavarasto:road_links_versions" }
  case object LinkCorreponceTable extends Collection { def value = "keskilinjavarasto:frozenlinks_vastintaulu" }
}

object FilterOgc extends Filter {

  val singleFilter= (field:String,value:String) => s"${field}=${value}"
  
  // TODO can we do filtering in ogc api and doest it work
  override def withFilter[T](attributeName: String, ids: Set[T]): String = ???

  override def withLimitFilter(attributeName: String, low: Int, high: Int, includeAllPublicRoads: Boolean = false): String = ???

  override def withMunicipalityFilter(municipalities: Set[Int]): String = {
    val singleFilter= (municipalities:Int) => s"municipalitycode=${municipalities}"
    if (municipalities.size ==1) {
      singleFilter(municipalities.head)
    }else {
      municipalities.map(m=>singleFilter(m)).mkString(" OR ")
    }
  }
  override def withRoadNameFilter[T](attributeName: String, names: Set[T]): String = {
    val filter =
      if (names.isEmpty) {
        ""
      } else {
        names.map(n =>singleFilter(attributeName,n.toString)).mkString(" OR ")
      }
    filter
  }

  override def combineFiltersWithAnd(filter1: String, filter2: String): String = {
    filter1 +" AND " + filter2
  }

  override def combineFiltersWithAnd(filter1: String, filter2: Option[String]): String =   if (filter2.isDefined) filter1 +" AND " + filter2.get else filter1
  
  // Query filters methods
  override def withLinkIdFilter[T](linkIds: Set[T]): String = {
    val filter =
      if (linkIds.isEmpty) {
        ""
      } else {
        linkIds.map(n =>singleFilter("ID",n.toString)).mkString(" OR ")
      }
    filter
  }

  override def withFinNameFilter(roadNameSource: String)(roadNames: Set[String]): String = {
    val filter =
      if (roadNames.isEmpty) {
        ""
      } else {
        roadNames.map(n =>singleFilter(roadNameSource,n)).mkString(" OR ")
      }
    filter
  }

  override def withMtkClassFilter(ids: Set[Long]): String = {
    val filter =
      if (ids.isEmpty) {
        ""
      } else {
        ids.map(n =>singleFilter("roadclass",n.toString)).mkString(" OR ")
      }
    filter
  }

  override def withLastEditedDateFilter(lowerDate: DateTime, higherDate: DateTime): String = ???

  override def withDateLimitFilter(attributeName: String, lowerDate: DateTime, higherDate: DateTime): String = ???
  
}

trait MtkOperation extends LinkOperationsAbstract{
  type LinkType
  type Content = FeatureCollection
  type IdType = String
  protected val linkGeomSource: LinkGeomSource = LinkGeomSource.NormalLinkInterface
  protected def serviceName: String
  protected val disableGeometry: Boolean
  protected def mapFields(content:Content, url: String): Either[List[Map[String, Any]], LinkOperationError]  = ???
  protected def defaultOutFields(): String = ???
  protected def extractFeature(feature: Map[String, Any]): LinkType = ???
  private val cqlLang ="cql-text"
  private val bboxCrsType="EPSG%3A3067"
  private val crs="EPSG%3A3067"
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
    * Extract double value from VVH data. Used for change info start and end measures.
    */
private  def extractMeasure(value: Any): Option[Double] = {
    value match {
      case null => None
      case _ => Some(value.toString.toDouble)
    }
  }
  protected def extractFeature(feature:Feature , path: List[List[Double]]): RoadlinkFetchedMtk = {
    val attributes =feature.properties
    
    def extractModifiedAt(attributes: Map[String, Any]): Option[DateTime] = {
      def compareDateMillisOptions(a: Option[Long], b: Option[Long]): Option[Long] = {
        (a, b) match {
          case (Some(firstModifiedAt), Some(secondModifiedAt)) =>
            Some(Math.max(firstModifiedAt, secondModifiedAt))
          case (Some(firstModifiedAt), None) => Some(firstModifiedAt)
          case (None, Some(secondModifiedAt)) => Some(secondModifiedAt)
          case (None, None) => None
        }
      }

      val validFromDate = Option(new DateTime(attributes("sourcemodificationtime").asInstanceOf[String]).getMillis)
      var lastEditedDate : Option[Long] = Option(0)
      if(attributes.contains("versionstarttime")){
        lastEditedDate = Option(new DateTime(attributes("versionstarttime").asInstanceOf[String]).getMillis)
      }
      var geometryEditedDate : Option[Long] = Option(0)
      if(attributes.contains("GEOMETRY_EDITED_DATE")){
        geometryEditedDate =  Option(new DateTime(attributes("GEOMETRY_EDITED_DATE").asInstanceOf[String]).getMillis)
      }

      val latestDate = compareDateMillisOptions(lastEditedDate, geometryEditedDate)
      latestDate.orElse(validFromDate).map(modifiedTime => new DateTime(modifiedTime))
    }
    val linkGeometry: Seq[Point] = path.map(point => {
      try {
        Point(point.head.toString.toDouble, point(1).toString.toDouble, extractMeasure(point(2).toString.toDouble).get)
      }catch {
        case e:ClassCastException => {
          println(s"error ${point.toString()}"); 
          Point(0,0,0)}
      }
    })
    val linkGeometryForApi = Map("points" -> path.map(point => Map("x" -> point(0).toString.toDouble, "y" -> point(1).toString.toDouble, "z" -> point(2).toString.toDouble, "m" -> point(3).toString.toDouble)))
    val linkGeometryWKTForApi = Map("geometryWKT" -> (s"LINESTRING ZM (${path.map(point => point(0).toString.toDouble + " " + point(1).toString.toDouble + " " + point(2).toString.toDouble + " " + point(3).toString.toDouble.toString.toDouble).mkString(", ")})"))
    
    val linkId = attributes("id").asInstanceOf[String]
    val municipalityCode = attributes("municipalitycode").asInstanceOf[String].toInt
    val mtkClass = attributes("roadclass")
    val geometryLength :Double = anyToDouble(attributes("horizontallength")).getOrElse(0.0)// ?

    val featureClassCode = if (mtkClass != null) // Complementary geometries have no MTK Class
      attributes("roadclass").asInstanceOf[String].toInt
    else
      0
    val featureClass = featureClassCodeToFeatureClass.getOrElse(featureClassCode, FeatureClass.AllOthers)

    RoadlinkFetchedMtk(linkId, municipalityCode,
      linkGeometry,
      extractAdministrativeClass(attributes),
      extractTrafficDirection(attributes), featureClass, extractModifiedAt(attributes),
      extractAttributes(attributes)
        ++ linkGeometryForApi ++ linkGeometryWKTForApi
      ,extractConstructionType(attributes), linkGeomSource, geometryLength)
  }
  

  protected def extractAdministrativeClass(attributes: Map[String, Any]): AdministrativeClass = {
    if (attributes("adminclass").asInstanceOf[String] != null)
      Option(attributes("adminclass").asInstanceOf[String].toInt)
        .map(AdministrativeClass.apply)
        .getOrElse(Unknown)
    else Unknown
  }

  protected def extractConstructionType(attributes: Map[String, Any]): ConstructionType = {
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

  // "ROADNAME_SM"           -> attributesMap(""),// delete find used in code remove if possible
  //    "GEOMETRY_EDITED_DATE"  -> attributesMap(""), // ?
  //"SUBTYPE"               -> attributesMap(""), // ? delete
  //"OBJECTID"              -> attributesMap(""), // delete
  //"STARTNODE"             -> attributesMap(""), //? delete
  //"ENDNODE"               -> attributesMap(""), // delete
  
  protected def extractAttributes(attributesMap: Map[String, Any]): Map[String, Any] = {
    Map( // in end rename these everywhere in code
      "MTKID"                 -> attributesMap("sourceid"),
      "MTKCLASS"              -> attributesMap("roadclass"),
      "HORIZONTALACCURACY"    -> attributesMap("xyaccuracy"),
      "VERTICALACCURACY"      -> attributesMap("zaccuracy"),
      "VERTICALLEVEL"         -> attributesMap("surfacerelation"),
      "CONSTRUCTIONTYPE"      -> attributesMap("lifecyclestatus"), 
      "ROADNAME_FI"           -> attributesMap("roadnamefin"),
      "ROADNAME_SE"           -> attributesMap("roadnameswe"),
      "ROADNUMBER"            -> attributesMap("roadnumber"),
      "ROADPARTNUMBER"        -> attributesMap("roadpartnumber"),
      "FROM_LEFT"             -> attributesMap("addressfromleft"),
      "TO_LEFT"               -> attributesMap("addresstoleft"),
      "FROM_RIGHT"            -> attributesMap("addressfromright"),
      "TO_RIGHT"              -> attributesMap("addresstoright"),
      "MUNICIPALITYCODE"      -> attributesMap("municipalitycode"),
      "MTKHEREFLIP"           -> attributesMap("geometryflip"),
      "CREATED_DATE"          -> attributesMap("starttime"),
      "LAST_EDITED_DATE"      -> attributesMap("versionstarttime"),
      "SURFACETYPE"           -> attributesMap("surfacetype"),
      "VALIDFROM"             -> attributesMap("sourcemodificationtime"))
    
    //  "LINKID_NEW"            -> attributesMap(""), //?
       //"END_DATE"              -> attributesMap(""), //?
      //"CUST_OWNER"            -> attributesMap("")) //?
  }
  


  def convertToFeature(content: Map[String, Any]): Feature = {
    val geometry = Geometry(`type` = content("geometry").asInstanceOf[Map[String, Any]]("type").toString,
      coordinates = content("geometry").asInstanceOf[Map[String, Any]]("coordinates").asInstanceOf[List[List[Double]]]
    )
    Feature(
      `type` = content("type").toString,
      geometry = geometry,
      properties = content("properties").asInstanceOf[Map[String, Any]]
    )
  }

  protected def encode(url: String): String = {
    URLEncoder.encode(url, "UTF-8")
  }

  override protected implicit val jsonFormats = DefaultFormats.preservingEmptyValues
  def addAuthorizationHeader(request: HttpRequestBase): Unit = {
    request.addHeader("X-API-Key", Digiroad2Properties.kmtkApiKey)
  }
  
  // https://github.com/json4s/json4s
  protected def fetchFeatures(url: String): Either[Option[FeatureCollection], LinkOperationError2] = {
    val request = new HttpGet(url)
    request.addHeader("accept","application/geo+json")
    addAuthorizationHeader(request)
    val client =  HttpClients.custom()
      .setDefaultRequestConfig(RequestConfig.custom()
        .setCookieSpec(CookieSpecs.STANDARD).build())
      .build();
    var response: CloseableHttpResponse = null
    println("new thread: "+Thread.currentThread().getName +" URL :\n "+url)
    LogUtils.time(logger, "fetch roadlinks client") {
      try {
        response = client.execute(request)
        val statusCode = response.getStatusLine.getStatusCode
        if (statusCode == HttpStatus.SC_OK) {
          val feature = parse(StreamInput(response.getEntity.getContent)).values.asInstanceOf[Map[String, Any]]
          val resort = feature("type").toString match {
            case "Feature" => Some(FeatureCollection(
              `type` = "FeatureCollection",
              features = List(LogUtils.time(logger, "convertToFeature",true)(convertToFeature(feature))),
              crs = Try(Some(feature("crs").asInstanceOf[Map[String, Any]])).getOrElse(None)
            ))
            case "FeatureCollection" =>
              Some(FeatureCollection(
                `type` = "FeatureCollection",
                features = LogUtils.time(logger, "convertToFeature",true)(feature("features").asInstanceOf[List[Map[String, Any]]].map(convertToFeature)),
                crs = Try(Some(feature("crs").asInstanceOf[Map[String, Any]])).getOrElse(None),
                numberReturned = feature("numberReturned").asInstanceOf[BigInt].toInt
              ))
            case _ => None
          }
          Left(resort)
        } else {
          Right(LinkOperationError2(response.getStatusLine.getReasonPhrase, response.getStatusLine.getStatusCode.toString))
        }
      }
      catch {
        case e: Exception => println(e.toString); Right(LinkOperationError2(e.toString, ""))
      }
      finally {
        if (response != null) {
          response.close()
        }
      }
    }
  }
  
  def paginationRequest(base:String,limit:Int,startIndex:Int = 0,firstRequest:Boolean = true ): (String,Int) = {
    if (firstRequest) (s"${base}&limit=${limit}&startIndex=${startIndex}",limit)
    else (s"${base}&limit=${limit}&startIndex=${startIndex}",startIndex+limit)
  }

  def paginate(finalResponse: Seq[FeatureCollection] = Seq(), baseUrl: String = "", firstRequest: Boolean = true, limit: Int = 4599, position: Int = 0): Seq[FeatureCollection] = { // recursive loop or while loop, first try recurse
    var newPositionOuteer: Int = 0
    val resort = if (firstRequest) {
      val (url, newPosition) = paginationRequest(baseUrl, limit)
      newPositionOuteer = newPosition
      fetchFeatures(url)
      match {
        case Left(features) => features
        case Right(error) => throw new ClientException(error.toString)
      }
    } else {
      val (url, newPosition) = paginationRequest(baseUrl, limit, firstRequest = false, startIndex = position)
      newPositionOuteer = newPosition
      fetchFeatures(url) match {
        case Left(features) => features
        case Right(error) => throw new ClientException(error.toString)
      }
    }

    if (resort.isDefined) {
      if (resort.get.numberReturned != 0) {
        paginate(finalResponse ++ Seq(resort.get), baseUrl, firstRequest = false, position = newPositionOuteer)
      } else {
        finalResponse
      }
    } else {
      logger.warn("possible end ?")
      finalResponse
    }
  }
 


  def queryWithPaginationThreaded(baseUrl: String = ""):Seq[LinkType]={
    val pageAllReadyFetched:mutable.HashSet[String] = new mutable.HashSet()

    @tailrec
    def paginateAtomic(finalResponse: Set[FeatureCollection] = Set(), baseUrl: String = "", limit:Int, position: Int): Set[FeatureCollection] = { // recursive loop or while loop, first try recurse
      val (url, newPosition) = paginationRequest(baseUrl, limit, firstRequest = false, startIndex = position)
      if(!pageAllReadyFetched.contains(url)){
        pageAllReadyFetched.add(url)
        val resort =  fetchFeatures(url) match {
          case Left(features) => features
          case Right(error) => throw new ClientException(error.toString)
        }
        if (resort.isDefined) {
          if (resort.get.numberReturned != 0) {
            paginateAtomic(finalResponse ++ Set(resort.get), baseUrl,limit,newPosition)
          } else {
            finalResponse
          }
        } else {
          logger.warn("possible end ?")
          finalResponse
        }
      }else {
        paginateAtomic(finalResponse, baseUrl,limit,newPosition)
      }
    }
    
    val limit = 4599

    val fut1=  Future(paginateAtomic(baseUrl = baseUrl,limit=limit,position = 0))
    val fut2=  Future(paginateAtomic(baseUrl = baseUrl,limit=limit,position = limit*2))
    val fut3=  Future(paginateAtomic(baseUrl = baseUrl,limit=limit,position = limit*3))
    val fut4=  Future(paginateAtomic(baseUrl = baseUrl,limit=limit,position = limit*4))

    val (items1)=Await.result(fut1,atMost = Duration.Inf)
    val (items2)=Await.result(fut2,atMost = Duration.Inf)
    val (items3)=Await.result(fut3,atMost = Duration.Inf)
    val (items4)=Await.result(fut4,atMost = Duration.Inf)
    val begin = System.currentTimeMillis()
    // create regular for loop with mutable list
   val t= (items1 ++ items2 ++items3 ++ items4).toSeq.flatMap(t=>t.features.map(t=>extractFeature(t,t.geometry.coordinates).asInstanceOf[LinkType]))
    val duration = System.currentTimeMillis() - begin
    println(s"roadlink merging completed in ${duration} ms and in second ${duration / 1000}")
    //  paginate(baseUrl = baseUrl).flatMap(t=>t.features.map(t=>extractFeature(t,t.geometry.coordinates).asInstanceOf[LinkType]))
  t
  }
  
  override protected def queryByMunicipalitiesAndBounds(bounds: BoundingRectangle, municipalities: Set[Int],
                                                        filter: Option[String]): Seq[LinkType] = {
    val bbox = s"${bounds.leftBottom.x},${bounds.leftBottom.y},${bounds.rightTop.x},${bounds.rightTop.y}"
    val filterString  = if (municipalities.nonEmpty || filter.isDefined){
      "filter="+encode(FilterOgc.combineFiltersWithAnd(FilterOgc.withMunicipalityFilter(municipalities),filter))
    }else {
      ""
    }
    fetchFeatures(s"${restApiEndPoint}/${MtkCollection.Frozen.value}/items?bbox=${bbox}&filter-lang=${cqlLang}&bbox-crs=${bboxCrsType}&crs=${crs}&${filterString}") 
    match {
      case Left(features) =>features.get.features.map(t=>extractFeature(t,t.geometry.coordinates).asInstanceOf[LinkType])
      case Right(error) => throw new ClientException(error.toString)
    }
  }
//pagination
  // https://api.testivaylapilvi.fi/paikkatiedot/ogc/features/collections/keskilinjavarasto:frozenlinks/items?filter=municipalitycode%3D91%20OR%20municipalitycode%3D297&limit=2&startIndex=2
  override protected def queryByMunicipality(municipality: Int, filter: Option[String] = None): Seq[LinkType] = {
    val filterString  = s"filter=${FilterOgc.combineFiltersWithAnd(FilterOgc.withMunicipalityFilter(Set(municipality)), filter)}"
    queryWithPaginationThreaded(s"${restApiEndPoint}/${MtkCollection.Frozen.value}/items?${filterString}&filter-lang=${cqlLang}&crs=${crs}")
  }

  override protected def queryByPolygons(polygon: Polygon): Seq[LinkType] = {
    val filterString  = s"filter=${(s"INTERSECTS(geometry,${encode(polygon.toString)}")})"
    val queryString = s"?${filterString}&filter-lang=${cqlLang}&crs=${crs}"
    fetchFeatures(s"${restApiEndPoint}/${MtkCollection.Frozen.value}/items/${queryString}") match {
      case Left(features) =>features.get.features.map(t=>extractFeature(t,t.geometry.coordinates).asInstanceOf[LinkType])
      case Right(error) => throw new ClientException(error.toString)
    }
  }

  override protected def queryLinksIdByPolygons(polygon: Polygon): Seq[IdType] = {
    val filterString  = s"filter=${(s"INTERSECTS(geometry,${encode(polygon.toString)}")})"
    val queryString = s"?${filterString}&filter-lang=${cqlLang}&crs=${crs}"
    fetchFeatures(s"${restApiEndPoint}/${MtkCollection.Frozen.value}/items/${queryString}") match {
      case Left(features) =>features.get.features.map(_.properties.get("id").asInstanceOf[IdType])
      case Right(error) => throw new ClientException(error.toString)
    }
  }

  protected def queryByLinkId[T](linkId: String,
                                           fieldSelection: Option[String] =None,
                                           fetchGeometry: Boolean =false
                                ): Seq[T] = {
    fetchFeatures(s"${restApiEndPoint}/${MtkCollection.Frozen.value}/items/${linkId}") match {
      case Left(features) =>features.get.features.map(t=>extractFeature(t,t.geometry.coordinates).asInstanceOf[T])
      case Right(error) => throw new ClientException(error.toString)
    }
  }

  override protected def queryByLinkIds[T](linkIds: Set[String],
                                           fieldSelection: Option[String],
                                           fetchGeometry: Boolean,
                                           resultTransition: (Map[String, Any], List[List[Double]]) => T,
                                           filter: Set[Long] => String): Seq[T] = {
    if (linkIds.size == 1) {
      queryByLinkId[T](linkIds.head)
    }else {
      // how are we going to fetch large set of id, map loop is too inefficient
      // fork join pool request in 10 item set ?
      linkIds.flatMap(t=>queryByLinkId[T](t)).toSeq
    }
  }

  protected def queryByFilter[LinkType](filter:Option[String]): Seq[LinkType] = {
    val filterString  = if (filter.nonEmpty){
      "filter="+encode(filter.get)
    }else {
      ""
    }
    fetchFeatures(s"${restApiEndPoint}/${MtkCollection.Frozen.value}/items?filter-lang=${cqlLang}&crs=${crs}&${filterString}")
    match {
      case Left(features) =>features.get.features.map(t=>extractFeature(t,t.geometry.coordinates).asInstanceOf[LinkType])
      case Right(error) => throw new ClientException(error.toString)
    }
  }

  override protected def queryByMunicipalitiesAndBounds(bounds: BoundingRectangle, municipalities: Set[Int]): Seq[LinkType] = {
    queryByMunicipalitiesAndBounds(bounds, municipalities, None)
  }
  
}
