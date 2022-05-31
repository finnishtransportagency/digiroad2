package fi.liikennevirasto.digiroad2.client.vvh
import com.vividsolutions.jts.geom.Polygon
import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, BoundingRectangle, ConstructionType, LinkGeomSource, TrafficDirection, Unknown}
import fi.liikennevirasto.digiroad2.client.vvh.Filter.anyToDouble
import fi.liikennevirasto.digiroad2.util.{Digiroad2Properties, LogUtils}
import org.apache.http.HttpStatus
import org.apache.http.client.methods.{HttpGet, HttpRequestBase}
import org.apache.http.impl.client.HttpClientBuilder
import org.joda.time.DateTime
import org.json4s.{DefaultFormats, Formats, StreamInput}
import org.json4s.jackson.JsonMethods.parse

import java.net.URLEncoder

sealed case class FeatureCollection(`type`: String, features: List[Feature], crs: Option[Map[String, Any]] = None)
sealed case class Feature(`type`: String, geometry: Geometry, properties: Map[String, Any])
sealed case class Geometry(`type`: String, coordinates: List[List[Double]])

trait MtkOperation extends LinkOperationsAbstract{
  type LinkType
  type Content = FeatureCollection
  type IdType = String
  protected val linkGeomSource: LinkGeomSource = LinkGeomSource.NormalLinkInterface
  protected def restApiEndPoint: String =Digiroad2Properties.kmtkEndpoint
  protected def serviceName: String
  protected val disableGeometry: Boolean
  protected def mapFields(content:Content, url: String): Either[List[Map[String, Any]], LinkOperationError]  = ???
  protected def defaultOutFields(): String = ???
  protected def extractFeature(feature: Map[String, Any]): LinkType = ???

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


  protected def extractFeature(feature:Feature , path: List[List[Double]]): RoadlinkFetched = {
    val attributes =feature.properties

    /**
      * Extract double value from VVH data. Used for change info start and end measures.
      */
     def extractMeasure(value: Any): Option[Double] = {
      value match {
        case null => None
        case _ => Some(value.toString.toDouble)
      }
    }

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
    val linkGeometry: Seq[Point] = path.map(point => {
      Point(point(0), point(1), extractMeasure(point(2)).get)
    })
    val linkGeometryForApi = Map("points" -> path.map(point => Map("x" -> point(0), "y" -> point(1), "z" -> point(2), "m" -> point(3))))
    val linkGeometryWKTForApi = Map("geometryWKT" -> (s"LINESTRING ZM (${path.map(point => point(0) + " " + point(1) + " " + point(2) + " " + point(3)).mkString(", ")})"))
    val linkId = attributes("id").asInstanceOf[String]
    val municipalityCode = attributes("municipalitycode").asInstanceOf[Int]
    val mtkClass = attributes("MTKCLASS")
    val geometryLength = anyToDouble(attributes("GEOMETRYLENGTH")).getOrElse(0.0)

    val featureClassCode = if (mtkClass != null) // Complementary geometries have no MTK Class
      attributes("MTKCLASS").asInstanceOf[BigInt].intValue()
    else
      0
    val featureClass = featureClassCodeToFeatureClass.getOrElse(featureClassCode, FeatureClass.AllOthers)

    RoadlinkFetched(linkId, municipalityCode, linkGeometry, extractAdministrativeClass(attributes),
      extractTrafficDirection(attributes), featureClass, extractModifiedAt(attributes),
      extractAttributes(attributes) ++ linkGeometryForApi ++ linkGeometryWKTForApi, extractConstructionType(attributes), linkGeomSource, geometryLength)
  }
  

  protected def extractAdministrativeClass(attributes: Map[String, Any]): AdministrativeClass = {
    Option(attributes("adminclass").asInstanceOf[Int])
      .map(AdministrativeClass.apply)
      .getOrElse(Unknown)
  }

  protected def extractConstructionType(attributes: Map[String, Any]): ConstructionType = {
    Option(attributes("lifecyclestatus").asInstanceOf[Int])
      .map(ConstructionType.apply)
      .getOrElse(ConstructionType.InUse)
  }

  protected def extractLinkGeomSource(attributes: Map[String, Any]): LinkGeomSource = {
    Option(attributes("LINK_SOURCE").asInstanceOf[Int]) //?
      .map(LinkGeomSource.apply)
      .getOrElse(LinkGeomSource.Unknown)
  }

  protected def extractTrafficDirection(attributes: Map[String, Any]): TrafficDirection = {
    Option(attributes("directiontype").asInstanceOf[Int])
      .map(trafficDirectionToTrafficDirection.getOrElse(_, TrafficDirection.UnknownDirection))
      .getOrElse(TrafficDirection.UnknownDirection)
  }

  protected def extractAttributes(attributesMap: Map[String, Any]): Map[String, Any] = {
    Map(
      "MTKID"                 -> attributesMap("sourceid"),
      "MTKCLASS"              -> attributesMap("roadclass"),
      "HORIZONTALACCURACY"    -> attributesMap("xyaccuracy"),
      "VERTICALACCURACY"      -> attributesMap("zaccuracy"),
      "VERTICALLEVEL"         -> attributesMap("surfacerelation"),
      "CONSTRUCTIONTYPE"      -> attributesMap("lifecyclestatus"), 
      "ROADNAME_FI"           -> attributesMap("roadnamefin"),
      "ROADNAME_SM"           -> attributesMap(""),// delete
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
      "VALIDFROM"             -> attributesMap("sourcemodificationtime"),// ?
      "GEOMETRY_EDITED_DATE"  -> attributesMap(""), // ?
      "LINKID_NEW"            -> attributesMap(""), //?
      "SUBTYPE"               -> attributesMap(""), // ?
      "END_DATE"              -> attributesMap(""), //?
      "OBJECTID"              -> attributesMap(""), // delete
      "STARTNODE"             -> attributesMap(""), //?
      "ENDNODE"               -> attributesMap(""), // ?
      "CUST_OWNER"            -> attributesMap("")) //?
  }
  
  protected def encode(url: String): String = {
    URLEncoder.encode(url , "UTF-8")
  }

  override protected implicit val jsonFormats = DefaultFormats.preservingEmptyValues
  def addAuthorizationHeader(request: HttpRequestBase): Unit = {
    request.addHeader("X-API-Key", Digiroad2Properties.kmtkApiKey)
  }
  
  // https://github.com/json4s/json4s
  protected def fetchFeatures(url: String): Either[Any, LinkOperationError2] = {
    val request = new HttpGet(url)
    addAuthorizationHeader(request)
    val client = HttpClientBuilder.create().build()
    val response = client.execute(request)
    LogUtils.time(logger,"fetch roadlinks client"){
      try {
        val statusCode = response.getStatusLine.getStatusCode
        if (statusCode == HttpStatus.SC_OK) {
         val feature = parse(StreamInput(response.getEntity.getContent)).values.asInstanceOf[FeatureCollection]
          Left(parse(StreamInput(response.getEntity.getContent)).values.asInstanceOf[FeatureCollection])
        }else {
          Right(LinkOperationError2(response.getEntity.getContent.toString,response.getStatusLine.getStatusCode.toString))
        }
      } finally {
        response.close()
      } 
    }
  }
  
  override protected def queryByMunicipalitiesAndBounds(bounds: BoundingRectangle, roadNumbers: Seq[(Int, Int)],
                                                        municipalities: Set[Int] = Set(),
                                                        includeAllPublicRoads: Boolean = false): Seq[RoadlinkFetched] = ???

  override protected def queryByMunicipalitiesAndBounds(bounds: BoundingRectangle, municipalities: Set[Int],
                                                        filter: Option[String]): Seq[LinkType] = ???
//pagination
  override protected def queryByMunicipality(municipality: Int, filter: Option[String] = None): Seq[LinkType] = ???

  override protected def queryByPolygons(polygon: Polygon): Seq[LinkType] = ???

  override protected def queryLinksIdByPolygons(polygon: Polygon): Seq[Long] = ???

  override protected def queryByLinkIds[T](linkIds: Set[String],
                                           fieldSelection: Option[String],
                                           fetchGeometry: Boolean,
                                           resultTransition: (Map[String, Any], List[List[Double]]) => T,
                                           filter: Set[Long] => String): Seq[T] = ???

  override protected def queryByMunicipalitiesAndBounds(bounds: BoundingRectangle, municipalities: Set[Int]): Seq[LinkType] = {
    queryByMunicipalitiesAndBounds(bounds, municipalities, None)
  }
}
