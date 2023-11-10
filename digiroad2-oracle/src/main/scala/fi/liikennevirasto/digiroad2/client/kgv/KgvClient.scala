package fi.liikennevirasto.digiroad2.client.kgv

import com.vividsolutions.jts.geom.Polygon
import fi.liikennevirasto.digiroad2.GeometryUtils.isPointInsideGeometry
import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.{BoundingRectangle, LinkGeomSource}
import fi.liikennevirasto.digiroad2.client.{ClientException, Filter, HistoryRoadLink, LinkOperationError, RoadLinkFetched}
import fi.liikennevirasto.digiroad2.util.Digiroad2Properties
import org.joda.time.DateTime
import org.json4s.{DefaultFormats, Formats}
import org.slf4j.LoggerFactory

import java.math.BigInteger
import scala.concurrent.Future
import scala.util.Try
import scala.concurrent.ExecutionContext.Implicits.global
import org.apache.http.HttpStatus
import org.apache.http.client.config.{CookieSpecs, RequestConfig}
import org.apache.http.client.methods.{CloseableHttpResponse, HttpGet, HttpRequestBase}
import org.apache.http.impl.client.HttpClients
import fi.liikennevirasto.digiroad2.util.LogUtils
import org.json4s.jackson.JsonMethods.parse
import org.json4s.StreamInput
import fi.liikennevirasto.digiroad2.{Geometry => UtilGeometry}

case class ChangeKgv( id: Int, oldKmtkId: String, oldVersion: Int, newKmtkId: Option[String],
                      newVersion: Option[Int], `type`: String, ruleId: Option[String], cTime: DateTime,
                      mFromOld: Double, mToOld: Double, mFromNew: Option[Double], mToNew: Option[Double],
                      taskId: String)

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
  protected def queryByIds[LinkType](idSet: Set[String],filter:(Set[String])=>String): Seq[LinkType] = ???
  protected def queryByMunicipalitiesAndBounds(bounds: BoundingRectangle, municipalities: Set[Int]): Seq[LinkType] = {
    queryByMunicipalitiesAndBounds(bounds, municipalities, None)
  }
}

class KgvRoadLinkClient(collection: Option[KgvCollection] = None,linkGeomSourceValue:Option[LinkGeomSource] = None) extends KgvRoadLinkClientBase(collection,linkGeomSourceValue) {
  override type LinkType = RoadLinkFetched
}

/**
 * Client for Municipality Border interface
 * @param collection
 * @param linkGeomSourceValue
 * @param extractor
 * TODO: remove inherited RoadLink dependencies as the client has no use for them
 */
class KgvMunicipalityBorderClient(collection: Option[KgvCollection], linkGeomSourceValue: Option[LinkGeomSource] = None, extractor: ExtractorBase = new Extractor) extends KgvOperation(extractor) {
  override def restApiEndPoint: String = Digiroad2Properties.kgvEndpoint
  protected val serviceName:String = collection.getOrElse(throw new ClientException("Collection is not defined") ).value
  protected val linkGeomSource: LinkGeomSource = LinkGeomSource.Unknown

  val filter: Filter = FilterOgc

  def fetchAllMunicipalities(): Seq[Municipality] = {
    val format = "f=application%2Fgeo%2Bjson"
    queryMunicipalityBorders(restApiEndPoint, serviceName, format)
    match {
      case Right(features) => features.get.features.map {feature =>
        val polygonFeature = feature.asInstanceOf[PolygonFeature]
        Municipality(polygonFeature.properties("kuntanumer").toString.toInt, polygonFeature.polygonGeometry) }
      case Left(error) => throw new ClientException(error.toString)
    }
  }

  def fetchAllMunicipalitiesF(): Future[Seq[Municipality]] = {
    Future(fetchAllMunicipalities())
  }

  /** *
   * Fetches all municipalities in a PolygonFeature collection
   *
   * @param restApiEndPoint
   * @param serviceName
   * @param format
   * @return either Error or collection of PolygonFeatures
   */
  def queryMunicipalityBorders(restApiEndPoint: String, serviceName: String, format: String): Either[LinkOperationError, Option[FeatureCollection]] = {
    fetchFeatures(s"$restApiEndPoint/$serviceName/items?$format&crs=$crs")
  }

  protected override def fetchFeatures(url: String): Either[LinkOperationError, Option[FeatureCollection]] = {
    val request = new HttpGet(url)
    addHeaders(request)

    val client = HttpClients.custom()
      .setDefaultRequestConfig(RequestConfig.custom()
        .setCookieSpec(CookieSpecs.STANDARD)
        .build())
      .build()

    var response: CloseableHttpResponse = null
    LogUtils.time(logger, s"fetch municipality borders", url = Some(url)) {
      try {
        response = client.execute(request)
        val statusCode = response.getStatusLine.getStatusCode
        if (statusCode == HttpStatus.SC_OK) {
          val feature = parse(StreamInput(response.getEntity.getContent)).values.asInstanceOf[Map[String, Any]]
          val resort = feature("type").toString match {
            case "FeatureCollection" =>
              Some(FeatureCollection(
                `type` = "FeatureCollection",
                features = feature("features").asInstanceOf[List[Map[String, Any]]].map(convertToFeature),
                crs = Try(Some(feature("crs").asInstanceOf[Map[String, Any]])).getOrElse(None)
              ))
            case _ => None
          }
          Right(resort)
        } else {
          Left(LinkOperationError(response.getStatusLine.getReasonPhrase, response.getStatusLine.getStatusCode.toString, url))
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

  protected override def convertToFeature(content: Map[String, Any]): BaseFeature = {
    val geometry = content("geometry").asInstanceOf[Map[String, Any]]
    val geometryCoordinates = geometry("coordinates").asInstanceOf[List[List[List[Double]]]].map(coords => UtilGeometry(`type` = geometry("type").toString,
      coordinates = coords))
    PolygonFeature(
      `type` = content("type").toString,
      polygonGeometry = geometryCoordinates,
      properties = content("properties").asInstanceOf[Map[String, Any]]
    )
  }

  /**
   * Finds the Municipality that matches given location point
   *
   * @param point
   * @param municipalities
   * @return correct Municipality
   *
   */
  def findMunicipalityForPoint(point: Point, municipalities: Seq[Municipality]): Option[Municipality] = {
    municipalities.find(municipality => municipality.geometry.exists(geometry => isPointInsideGeometry(point, geometry)))
  }

  override protected def queryByMunicipalitiesAndBounds(bounds: BoundingRectangle, municipalities: Set[Int],
                                                        filter: Option[String]): Seq[LinkType] = {
    throw new NotImplementedError("Inherited method not implemented in KgvMunicipalityBorderClient")
  }

  override protected def queryByMunicipality(municipality: Int, filter: Option[String] = None): Seq[LinkType] = {
    throw new NotImplementedError("Inherited method not implemented in KgvMunicipalityBorderClient")
  }

  override protected def queryByPolygons(polygon: Polygon): Seq[LinkType] = {
    throw new NotImplementedError("Inherited method not implemented in KgvMunicipalityBorderClient")
  }

  override protected def queryLinksIdByPolygons(polygon: Polygon): Seq[String] = {
    throw new NotImplementedError("Inherited method not implemented in KgvMunicipalityBorderClient")
  }

  override protected def queryByIds[LinkType](idSet: Set[String], filter: Set[String] => String): Seq[LinkType] = {
    throw new NotImplementedError("Inherited method not implemented in KgvMunicipalityBorderClient")
  }

  override protected def queryByLinkIds[LinkType](linkIds: Set[String], filter: Option[String] = None): Seq[LinkType] = {
    throw new NotImplementedError("Inherited method not implemented in KgvMunicipalityBorderClient")
  }

  override protected def queryByLinkIdsUsingFilter[LinkType](linkIds: Set[String], filter: Option[String]): Seq[LinkType] = {
    throw new NotImplementedError("Inherited method not implemented in KgvMunicipalityBorderClient")
  }

  override protected def queryByFilter[LinkType](filter: Option[String], pagination: Boolean = false): Seq[LinkType] = {
    throw new NotImplementedError("Inherited method not implemented in KgvMunicipalityBorderClient")
  }

  override protected def queryByDatetimeAndFilter[LinkType](lowerDate: DateTime, higherDate: DateTime, filter: Option[String] = None): Seq[LinkType] = {
    throw new NotImplementedError("Inherited method not implemented in KgvMunicipalityBorderClient")
  }

  override protected def queryByLastEditedDate[LinkType](lowerDate: DateTime, higherDate: DateTime): Seq[LinkType] = {
    throw new NotImplementedError("Inherited method not implemented in KgvMunicipalityBorderClient")
  }

  override protected def queryByMunicipalitiesAndBounds(bounds: BoundingRectangle, municipalities: Set[Int]): Seq[LinkType] = {
    throw new NotImplementedError("Inherited method not implemented in KgvMunicipalityBorderClient")
  }
}

class KgvRoadLinkClientBase(collection: Option[KgvCollection] = None, linkGeomSourceValue:Option[LinkGeomSource] = None, extractor:ExtractorBase = new Extractor) extends KgvOperation(extractor) {

  override type LinkType
  override def restApiEndPoint: String = Digiroad2Properties.kgvEndpoint
  protected val serviceName:String = collection.getOrElse(throw new ClientException("Collection is not defined") ).value
  protected val linkGeomSource: LinkGeomSource = linkGeomSourceValue.getOrElse(throw new ClientException("LinkGeomSource is not defined") )
  val filter:Filter = FilterOgc

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

class ExtractHistory extends ExtractorBase {

  override type LinkType = HistoryRoadLink

  override def extractFeature(feature: LineFeature, path: List[List[Double]], linkGeomSource: LinkGeomSource): LinkType  = {
    val attributes = feature.properties
    val lastEditedDate = Option(BigInteger.valueOf(new DateTime(attributes("versionstarttime").asInstanceOf[String]).getMillis))
    val startTime = Option(BigInteger.valueOf(new DateTime(attributes("starttime").asInstanceOf[String]).getMillis))
    val linkGeometry: Seq[Point] = path.map(point => {
      Point(anyToDouble(point(0)).get, anyToDouble(point(1)).get, anyToDouble(point(2)).get)
    })
    val linkGeometryForApi = Map("points" -> path.map(point => Map("x" -> anyToDouble(point(0)).get, "y" -> anyToDouble(point(1)).get, "z" -> anyToDouble(point(2)).get, "m" -> anyToDouble(point(3)).get)))
    val linkGeometryWKTForApi = Map("geometryWKT" -> (s"LINESTRING ZM (${path.map(point => anyToDouble(point(0)).get + " " + anyToDouble(point(1)).get + " " + anyToDouble(point(2)).get + " " + anyToDouble(point(3)).get).mkString(", ")})"))

    val linkId = attributes("id").asInstanceOf[String]
    val municipalityCode = attributes("municipalitycode").asInstanceOf[String].toInt
    val roadClassCode = attributes("roadclass").asInstanceOf[String].toInt
    val roadClass = featureClassCodeToFeatureClass(roadClassCode)

    val kmtkId = linkId.split(":")(0)
    val version = linkId.split(":")(1).toInt

    HistoryRoadLink(linkId, municipalityCode, linkGeometry, extractAdministrativeClass(attributes),
      extractTrafficDirection(attributes), roadClass, createdDate = startTime.get,
      attributes = extractAttributes(attributes,lastEditedDate.get,startTime.get)
        ++ linkGeometryForApi ++ linkGeometryWKTForApi, kmtkid = kmtkId, version = version)
  }
}
class ExtractKgvChange extends ExtractorBase {
  override type LinkType = ChangeKgv

  override def extractFeature(feature: LineFeature, path: List[List[Double]], linkGeomSource: LinkGeomSource): LinkType  = {
    val attributes = feature.properties.filter{case (key, value) => value != null}

    ChangeKgv(
      attributes("id").toString.toInt,
      attributes("oldkmtkid").toString,
      attributes("oldversion").toString.toInt,
      attributes.get("newkmtkid").asInstanceOf[Option[String]],
      attributes.get("newversion") match {
        case Some(value) => Option(value.toString.toInt)
        case None => None
      },
      attributes("type").toString,
      attributes.get("ruleid").asInstanceOf[Option[String]],
      new DateTime(attributes("ctime").asInstanceOf[String]),
      attributes("mfromold").toString.toDouble,
      attributes("mtoold").toString.toDouble,
      attributes.get("mfromnew") match {
        case Some(value) => Option(value.toString.toDouble)
        case None => None
      },
      attributes.get("mtonew") match {
        case Some(value) => Option(value.toString.toDouble)
        case None => None
      },
      attributes("taskid").toString)
  }
}
class RoadLinkHistoryClient(serviceName:KgvCollection = KgvCollection.LinkVersios, linkGeomSource:LinkGeomSource=LinkGeomSource.HistoryLinkInterface)
  extends KgvRoadLinkClientBase(Some(serviceName),Some(linkGeomSource),extractor = new ExtractHistory) {

  override type LinkType = HistoryRoadLink

  private def enrichWithChangeInfo(response: Seq[HistoryRoadLink]): Seq[HistoryRoadLink] = {
    val changesLinkInfo = new RoadLinkChangeKgvClient().fetchByOldKmtkId(response.map(_.kmtkid).toSet)
    response.map(r => {
      val newId = Try {
        val link = changesLinkInfo.find(f => r.kmtkid == f.oldKmtkId && r.version == f.oldVersion).get
        s"${link.oldKmtkId}:${link.oldVersion}"
      }.getOrElse(None)
      r.copy(attributes = r.attributes ++ Map("LINKID_NEW" -> newId))
    })
  }

  override protected def queryByMunicipalitiesAndBounds(bounds: BoundingRectangle, municipalities: Set[Int]): Seq[LinkType] = {
    val response = queryByMunicipalitiesAndBounds(bounds, municipalities, None)
    enrichWithChangeInfo(response)
  }

}

class RoadLinkChangeKgvClient(serviceName:KgvCollection = KgvCollection.Changes, linkGeomSource:LinkGeomSource=LinkGeomSource.Unknown)
  extends KgvRoadLinkClientBase(Some(serviceName),Some(linkGeomSource),extractor = new ExtractKgvChange) {

  override type LinkType = ChangeKgv

  def fetchByOldKmtkId(ids: Set[String]): Seq[LinkType] = {
    queryByIds(ids,filter.withOldkmtkidFilter).asInstanceOf[Seq[LinkType]]
  }
}