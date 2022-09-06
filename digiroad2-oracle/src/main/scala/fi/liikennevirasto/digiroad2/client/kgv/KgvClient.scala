package fi.liikennevirasto.digiroad2.client.kgv

import com.vividsolutions.jts.geom.Polygon
import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.{BoundingRectangle, LinkGeomSource}
import fi.liikennevirasto.digiroad2.client.{ClientException, FeatureClass, Filter, HistoryRoadLink, IRoadLinkFetched, RoadLinkFetched}
import fi.liikennevirasto.digiroad2.util.Digiroad2Properties
import org.joda.time.DateTime
import org.json4s.{DefaultFormats, Formats}
import org.slf4j.LoggerFactory

import java.math.BigInteger
import scala.concurrent.Future
import scala.util.Try
import scala.concurrent.ExecutionContext.Implicits.global

case class ChangeKgv( id: Int, oldKmtkId: String, oldVersion: Int, newKmtkId: String,
                      newVersion: Int, `type`: String, ruleId: String, cTime: DateTime,
                      mFromOld: String, mToOld: Int, mFromNew: Int, mToNew: Int,
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

  override def extractFeature(feature: Feature, path: List[List[Double]], linkGeomSource: LinkGeomSource): LinkType  = {
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
    val roadClass = featureClassCodeToFeatureClass.getOrElse(roadClassCode, FeatureClass.AllOthers)

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

  override def extractFeature(feature: Feature, path: List[List[Double]], linkGeomSource: LinkGeomSource): LinkType  = {
    val attributes = feature.properties

    ChangeKgv(
      attributes("id").toString.toInt,
      attributes("oldkmtkid").toString,
      attributes("oldversion").toString.toInt,
      attributes("newkmtkid").toString,
      attributes("newversion").toString.toInt,
      attributes("type").toString,
      attributes("ruleid").toString,
      new DateTime(attributes("ctime").asInstanceOf[String]),
      attributes("mfromold").toString,
      attributes("mtoold").toString.toInt,
      attributes("mfromnew").toString.toInt,
      attributes("mtonew").toString.toInt,
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