package fi.liikennevirasto.digiroad2.service

import java.io.{File, FilenameFilter, IOException}
import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit
import java.util.{Date, Properties}

import com.github.tototoshi.slick.MySQLJodaSupport._
import com.vividsolutions.jts.geom.Polygon
import fi.liikennevirasto.digiroad2.GeometryUtils._
import fi.liikennevirasto.digiroad2.asset.Asset._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh._
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkProperties}
import fi.liikennevirasto.digiroad2.oracle.{MassQuery, OracleDatabase}
import fi.liikennevirasto.digiroad2.roadlinkservice.oracle.RoadLinkServiceDAO
import fi.liikennevirasto.digiroad2.user.User
import fi.liikennevirasto.digiroad2.util.{Track, VVHRoadLinkHistoryProcessor, VVHSerializer}
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, GeometryUtils, Point}
import org.joda.time.format.ISODateTimeFormat
import org.joda.time.{DateTime, DateTimeZone}
import org.slf4j.LoggerFactory
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedResult, StaticQuery => Q}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

case class IncompleteLink(linkId: Long, municipalityCode: Int, administrativeClass: AdministrativeClass)
case class RoadLinkChangeSet(adjustedRoadLinks: Seq[RoadLink], incompleteLinks: Seq[IncompleteLink])
case class ChangedVVHRoadlink(link: RoadLink, value: String, createdAt: Option[DateTime], changeType: String /*TODO create and use ChangeType case object*/)

sealed trait RoadLinkType {
  def value: Int
}

object RoadLinkType{
  val values = Set(NormalRoadLinkType, ComplementaryRoadLinkType, UnknownRoadLinkType, FloatingRoadLinkType)

  def apply(intValue: Int): RoadLinkType = {
    values.find(_.value == intValue).getOrElse(UnknownRoadLinkType)
  }

  case object UnknownRoadLinkType extends RoadLinkType { def value = 0 }
  case object NormalRoadLinkType extends RoadLinkType { def value = 1 }
  case object ComplementaryRoadLinkType extends RoadLinkType { def value = 3 }
  case object FloatingRoadLinkType extends RoadLinkType { def value = -1 }
  case object SuravageRoadLink extends RoadLinkType { def value = 4}
}

/**
  * This class performs operations related to road links. It uses VVHClient to get data from VVH Rest API.
  *
  * @param vvhClient
  * @param eventbus
  * @param vvhSerializer
  */
class RoadLinkService(val vvhClient: VVHClient, val eventbus: DigiroadEventBus, val vvhSerializer: VVHSerializer) {
  val logger = LoggerFactory.getLogger(getClass)

  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)

  implicit val getDateTime = new GetResult[DateTime] {
    def apply(r: PositionedResult) = {
      new DateTime(r.nextTimestamp())
    }
  }

  def getRoadLinkAndComplementaryFromVVH(linkId: Long, newTransaction: Boolean = true): Option[RoadLink] = getRoadLinksAndComplementariesFromVVH(Set(linkId), newTransaction: Boolean).headOption

  def getRoadLinksAndComplementariesFromVVH(linkId: Set[Long], newTransaction: Boolean = true): Seq[RoadLink] = {
    val vvhRoadLinks = fetchVVHRoadlinksAndComplementary(linkId)
    if (newTransaction)
      withDynTransaction {
        enrichRoadLinksFromVVH(vvhRoadLinks)
      }
    else
        enrichRoadLinksFromVVH(vvhRoadLinks)
  }

  /**
    * ATENTION Use this method always with transation not with session
    * Returns the road links from VVH by municipality.
    *
    * @param municipality A integer, representative of the municipality Id.
    */
  def getRoadLinksFromVVHByMunicipality(municipality: Int, newTransaction: Boolean = true): Seq[RoadLink] = {
    val vvhRoadLinks = vvhClient.roadLinkData.fetchByMunicipality(municipality)
    if (newTransaction)
      withDynTransaction {
        enrichRoadLinksFromVVH(vvhRoadLinks)
      }
    else
      enrichRoadLinksFromVVH(vvhRoadLinks)
  }

  /**
    * ATENTION Use this method always with transation not with session
    * Returns the road links and changes from VVH by municipality.
    *
    * @param municipality A integer, representative of the municipality Id.
    */
  def getRoadLinksAndChangesFromVVHByMunicipality(municipality: Int, newTransaction: Boolean = true): (Seq[RoadLink], Seq[ChangeInfo]) = {
    val fut = for{
      changeInfos <- vvhClient.roadLinkChangeInfo.fetchByMunicipalityF(municipality)
      vvhRoadLinks <- vvhClient.roadLinkData.fetchByMunicipalityF(municipality)
    } yield (changeInfos, vvhRoadLinks)

    val (changeInfos, vvhRoadLinks) = Await.result(fut, Duration.Inf)
    if (newTransaction)
      withDynTransaction {
        (enrichRoadLinksFromVVH(vvhRoadLinks), changeInfos)
      }
    else
      (enrichRoadLinksFromVVH(vvhRoadLinks), changeInfos)
  }

  /**
    * ATENTION Use this method always with transation not with session
    * This method returns road links by link ids.
    * @param linkIds
    * @return Road links
    */

  def getRoadLinksByLinkIdsFromVVH(linkIds: Set[Long], newTransaction: Boolean = true): Seq[RoadLink] = {
    val vvhRoadLinks = fetchVVHRoadlinks(linkIds)
    if (newTransaction)
      withDynTransaction {
        enrichRoadLinksFromVVH(vvhRoadLinks)
      }
    else
      enrichRoadLinksFromVVH(vvhRoadLinks)
  }

  def getRoadLinkByLinkIdFromVVH(linkId: Long, newTransaction: Boolean = true): Option[RoadLink] = getRoadLinksByLinkIdsFromVVH(Set(linkId), newTransaction: Boolean).headOption

  def getSuravageRoadLinksByLinkIdsFromVVH(linkIds: Set[Long], newTransaction: Boolean = true): Seq[RoadLink] = {
    val vvhSuravageLinks = fetchSuravageLinksByLinkIdsFromVVH(linkIds)
    if (newTransaction)
      withDynTransaction {
        enrichRoadLinksFromVVH(vvhSuravageLinks)
      }
    else
      enrichRoadLinksFromVVH(vvhSuravageLinks)
  }

  def getViiteRoadLinksByLinkIdsFromVVH(linkIds: Set[Long], newTransaction: Boolean = true, frozenTimeVVHAPIServiceEnabled:Boolean = false): Seq[RoadLink] = {
    val vvhRoadLinks = fetchVVHRoadlinks(linkIds, frozenTimeVVHAPIServiceEnabled)
    if (newTransaction)
      withDynTransaction {
        enrichRoadLinksFromVVH(vvhRoadLinks)
      }
    else
      enrichRoadLinksFromVVH(vvhRoadLinks)
  }

  /**
    * This method returns VVH road links that had changed between two dates.
    *
    * @param since
    * @param until
    * @return Road links
    */
  def getRoadLinksBetweenTwoDatesFromVVH(since: DateTime, until: DateTime, newTransaction: Boolean = true): Seq[VVHRoadlink] = {
    fetchChangedVVHRoadlinksBetweenDates(since, until)
  }

  /**
    * This method returns road links by municipality.
    *
    * @param municipality
    * @return Road links
    */
  def getRoadLinksFromVVH(municipality: Int): Seq[RoadLink] = {
    getCachedRoadLinksAndChanges(municipality)._1
  }

  def getRoadLinksWithComplementaryFromVVH(municipality: Int): Seq[RoadLink] = {
    getCachedRoadLinksWithComplementaryAndChanges(municipality)._1
  }

  def getRoadLinksFromVVHFuture(municipality: Int): Future[Seq[RoadLink]] = {
    Future(getRoadLinksFromVVH(municipality))
  }

  def getRoadNodesByMunicipality(municipality: Int): Seq[VVHRoadNodes] = {
    getCachedRoadNodes(municipality)
  }

  def getRoadNodesFromVVHFuture(municipality: Int): Future[Seq[VVHRoadNodes]] = {
    Future(getRoadNodesByMunicipality(municipality))
  }

  /**
    * This method returns road links by bounding box and municipalities.
    *
    * @param bounds
    * @param municipalities
    * @return Road links
    */
  def getRoadLinksFromVVH(bounds: BoundingRectangle, municipalities: Set[Int] = Set()) : Seq[RoadLink] =
    getRoadLinksAndChangesFromVVH(bounds, municipalities)._1

  /**
    * This method returns "real" road links and "complementary" road links by bounding box and municipalities.
    *
    * @param bounds
    * @param municipalities
    * @return Road links
    */
  def getRoadLinksWithComplementaryFromVVH(bounds: BoundingRectangle, municipalities: Set[Int] = Set(), newTransaction: Boolean = true) : Seq[RoadLink] =
    getRoadLinksWithComplementaryAndChangesFromVVH(bounds, municipalities, newTransaction)._1

  /**
    * This method is utilized to find adjacent links of a road link.
    *
    * @param bounds
    * @param bounds2
    * @return Road links
    */
  def getRoadLinksFromVVH(bounds: BoundingRectangle, bounds2: BoundingRectangle) : Seq[RoadLink] =
    getRoadLinksAndChangesFromVVH(bounds, bounds2)._1

  /**
    * This method returns VVH road links by link ids.
    *
    * @param linkIds
    * @return VVHRoadLinks
    */
  def fetchVVHRoadlinks(linkIds: Set[Long], frozenTimeVVHAPIServiceEnabled:Boolean = false): Seq[VVHRoadlink] = {
    if (linkIds.nonEmpty) {if(frozenTimeVVHAPIServiceEnabled){vvhClient.frozenTimeRoadLinkData.fetchByLinkIds(linkIds)} else vvhClient.roadLinkData.fetchByLinkIds(linkIds) }
    else Seq.empty[VVHRoadlink]
  }

  def fetchVVHComplementaryRoadlinks(linkIds: Set[Long]): Seq[VVHRoadlink] = {
    if (linkIds.nonEmpty) vvhClient.complementaryData.fetchByLinkIds(linkIds)
    else Seq.empty[VVHRoadlink]
  }

  def fetchVVHRoadlinksAndComplementary(linkIds: Set[Long]): Seq[VVHRoadlink] = {
    if (linkIds.nonEmpty) vvhClient.roadLinkData.fetchByLinkIds(linkIds) ++ vvhClient.complementaryData.fetchByLinkIds(linkIds)
    else Seq.empty[VVHRoadlink]
  }

  def fetchVVHRoadlinkAndComplementary(linkId: Long): Option[VVHRoadlink] = fetchVVHRoadlinksAndComplementary(Set(linkId)).headOption

  /**
    * This method returns VVH road links that had changed between two dates.
    *
    * @param since
    * @param until
    * @return VVHRoadLinks
    */
  def fetchChangedVVHRoadlinksBetweenDates(since: DateTime, until: DateTime): Seq[VVHRoadlink] = {
    if ((since != null) || (until != null)) vvhClient.roadLinkData.fetchByChangesDates(since, until)
    else Seq.empty[VVHRoadlink]
  }

  /**
    * This method returns VVH road links by bounding box and municipalities. Utilized to find the closest road link of a point.
    *
    * @param bounds
    * @param municipalities
    * @return VVHRoadLinks
    */
  def getVVHRoadLinks(bounds: BoundingRectangle, municipalities: Set[Int] = Set()): Seq[VVHRoadlink] = {
    vvhClient.roadLinkData.fetchByMunicipalitiesAndBounds(bounds, municipalities)
  }

  /**
    * This method is used by CsvGenerator.
    *
    * @param linkIds
    * @param fieldSelection
    * @param fetchGeometry
    * @param resultTransition
    * @tparam T
    * @return
    */
  def fetchVVHRoadlinks[T](linkIds: Set[Long],
                           fieldSelection: Option[String],
                           fetchGeometry: Boolean,
                           resultTransition: (Map[String, Any], List[List[Double]]) => T): Seq[T] = {
    if (linkIds.nonEmpty) vvhClient.roadLinkData.fetchVVHRoadlinks(linkIds, fieldSelection, fetchGeometry, resultTransition)
    else Seq.empty[T]
  }

  /**
    * This method returns road links and change data by bounding box and municipalities.
    *
    * @param bounds
    * @param municipalities
    * @return Road links and change data
    */
  def getRoadLinksAndChangesFromVVH(bounds: BoundingRectangle, municipalities: Set[Int] = Set()): (Seq[RoadLink], Seq[ChangeInfo]) = {
    val (changes, links) =
      Await.result(vvhClient.roadLinkChangeInfo.fetchByBoundsAndMunicipalitiesF(bounds, municipalities).zip(vvhClient.roadLinkData.fetchByMunicipalitiesAndBoundsF(bounds, municipalities)), atMost = Duration.Inf)
    withDynTransaction {
      (enrichRoadLinksFromVVH(links, changes), changes)
    }
  }

  def getRoadLinksAndChangesFromVVHWithFrozenTimeAPI(bounds: BoundingRectangle, municipalities: Set[Int] = Set(), frozenTimeVVHAPIServiceEnabled:Boolean = false): (Seq[RoadLink], Seq[ChangeInfo])= {
   if (frozenTimeVVHAPIServiceEnabled) {
     val (changes, links) =
       Await.result(Future(Seq.empty[ChangeInfo]).zip(vvhClient.frozenTimeRoadLinkData.fetchByMunicipalitiesAndBoundsF(bounds, municipalities)), atMost = Duration.Inf)
     withDynTransaction {
       (enrichRoadLinksFromVVH(links, changes), changes)
     }
   } else
       getRoadLinksAndChangesFromVVH(bounds,municipalities)
  }

  def getRoadLinksAndChangesFromVVHWithPolygon(polygon :Polygon): (Seq[RoadLink], Seq[ChangeInfo])= {
    val (changes, links) = Await.result(vvhClient.roadLinkChangeInfo.fetchByPolygonF(polygon).zip(vvhClient.roadLinkData.fetchByPolygonF(polygon)), atMost = Duration.Inf)
    withDynTransaction {
      (enrichRoadLinksFromVVH(links, changes), changes)
    }
  }

  def getRoadLinksWithComplementaryAndChangesFromVVHWithPolygon(polygon :Polygon): (Seq[RoadLink], Seq[ChangeInfo])= {
    val futures = for{
      roadLinkResult <- vvhClient.roadLinkData.fetchByPolygonF(polygon)
      changesResult <- vvhClient.roadLinkChangeInfo.fetchByPolygonF(polygon)
      complementaryResult <- vvhClient.complementaryData.fetchByPolygonF(polygon)
    } yield (roadLinkResult, changesResult, complementaryResult)

    val (complementaryLinks, changes, links) = Await.result(futures, Duration.Inf)

    withDynTransaction {
      (enrichRoadLinksFromVVH(links ++ complementaryLinks, changes), changes)
    }
  }

  /**
    * This method returns "real" and "complementary" link id by polygons.
    *
    * @param polygons
    * @return LinksId
    */

  def getLinkIdsFromVVHWithComplementaryByPolygons(polygons: Seq[Polygon]) = {
    polygons.flatMap(getLinkIdsFromVVHWithComplementaryByPolygon)
  }

  /**
    * This method returns "real" and "complementary" link id by polygon.
    *
    * @param polygon
    * @return seq(LinksId) , seq(LinksId)
    */
  def getLinkIdsFromVVHWithComplementaryByPolygon(polygon :Polygon): Seq[Long] = {

    val fut = for {
      f1Result <- vvhClient.roadLinkData.fetchLinkIdsByPolygonF(polygon)
      f2Result <- vvhClient.complementaryData.fetchLinkIdsByPolygonF(polygon)
    } yield (f1Result, f2Result)

    val (complementaryResult, result) = Await.result(fut, Duration.Inf)
    complementaryResult ++ result
  }

  /**
    * This method returns "real" road links, "complementary" road links and change data by bounding box and municipalities.
    *
    * @param bounds
    * @param municipalities
    * @return Road links and change data
    */
  def getRoadLinksWithComplementaryAndChangesFromVVH(bounds: BoundingRectangle, municipalities: Set[Int] = Set(), newTransaction:Boolean = true): (Seq[RoadLink], Seq[ChangeInfo])= {
    val fut = for{
      f1Result <- vvhClient.complementaryData.fetchWalkwaysByBoundsAndMunicipalitiesF(bounds, municipalities)
      f2Result <- vvhClient.roadLinkChangeInfo.fetchByBoundsAndMunicipalitiesF(bounds, municipalities)
      f3Result <- vvhClient.roadLinkData.fetchByMunicipalitiesAndBoundsF(bounds, municipalities)
    } yield (f1Result, f2Result, f3Result)

    val (complementaryLinks, changes, links) = Await.result(fut, Duration.Inf)

    if(newTransaction){
      withDynTransaction {
        (enrichRoadLinksFromVVH(links ++ complementaryLinks, changes), changes)
      }
    }
    else (enrichRoadLinksFromVVH(links ++ complementaryLinks, changes), changes)

  }

  def reloadRoadLinksWithComplementaryAndChangesFromVVH(municipalities: Int): (Seq[RoadLink], Seq[ChangeInfo], Seq[RoadLink])= {
    val fut = for{
      f1Result <- vvhClient.complementaryData.fetchWalkwaysByMunicipalitiesF(municipalities)
      f2Result <- vvhClient.roadLinkChangeInfo.fetchByMunicipalityF(municipalities)
      f3Result <- vvhClient.roadLinkData.fetchByMunicipalityF(municipalities)
    } yield (f1Result, f2Result, f3Result)

    val (complementaryLinks, changes, links) = Await.result(fut, Duration.Inf)

    withDynTransaction {
      (enrichRoadLinksFromVVH(links, changes), changes, enrichRoadLinksFromVVH(complementaryLinks, changes))
    }
  }

  /**
    * This method returns road links and change data by municipality.
    *
    * @param municipality
    * @return Road links and change data
    */
  def getRoadLinksAndChangesFromVVH(municipality: Int): (Seq[RoadLink], Seq[ChangeInfo])= {
    getCachedRoadLinksAndChanges(municipality)
  }

  def getFrozenViiteRoadLinksAndChangesFromVVH(municipality: Int, frozenTimeVVHAPIServiceEnabled: Boolean =false ): (Seq[RoadLink], Seq[ChangeInfo])= {
    getCachedRoadLinksAndChanges(municipality)
  }



  def getRoadLinksWithComplementaryAndChangesFromVVH(municipality: Int): (Seq[RoadLink], Seq[ChangeInfo])= {
    getCachedRoadLinksWithComplementaryAndChanges(municipality)
  }

  /**
    * This method is utilized to find adjacent links of a road link.
    *
    * @param bounds
    * @param bounds2
    * @return Road links and change data
    */
  def getRoadLinksAndChangesFromVVH(bounds: BoundingRectangle, bounds2: BoundingRectangle): (Seq[RoadLink], Seq[ChangeInfo])= {
    val links1F = vvhClient.roadLinkData.fetchByMunicipalitiesAndBoundsF(bounds, Set())
    val links2F = vvhClient.roadLinkData.fetchByMunicipalitiesAndBoundsF(bounds2, Set())
    val changeF = vvhClient.roadLinkChangeInfo.fetchByBoundsAndMunicipalitiesF(bounds, Set())
    val ((links, links2), changes) = Await.result(links1F.zip(links2F).zip(changeF), atMost = Duration.apply(60, TimeUnit.SECONDS))
    withDynTransaction {
      (enrichRoadLinksFromVVH(links ++ links2, changes), changes)
    }
  }

  /**
    * This method returns road links by municipality. Used by expireImportRoadLinksVVHtoOTH.
    *
    * @param municipality
    * @return VVHRoadLinks
    */
  def getVVHRoadLinksF(municipality: Int) : Seq[VVHRoadlink] = {
    Await.result(vvhClient.roadLinkData.fetchByMunicipalityF(municipality), atMost = Duration.Inf)
  }

  /**
    * Returns incomplete links by municipalities (Incomplete link = road link with no functional class and link type saved in OTH).
    * Used by Digiroad2Api /roadLinks/incomplete GET endpoint.
    */
  def getIncompleteLinks(includedMunicipalities: Option[Set[Int]]): Map[String, Map[String, Seq[Long]]] = {
    case class IncompleteLink(linkId: Long, municipality: String, administrativeClass: String)
    def toIncompleteLink(x: (Long, String, Int)) = IncompleteLink(x._1, x._2, AdministrativeClass(x._3).toString)

    withDynSession {
      val optionalMunicipalities = includedMunicipalities.map(_.mkString(","))
      val incompleteLinksQuery = """
        select l.link_id, m.name_fi, l.administrative_class
        from incomplete_link l
        join municipality m on l.municipality_code = m.id
                                 """

      val sql = optionalMunicipalities match {
        case Some(municipalities) => incompleteLinksQuery + s" where l.municipality_code in ($municipalities)"
        case _ => incompleteLinksQuery
      }

      Q.queryNA[(Long, String, Int)](sql).list
        .map(toIncompleteLink)
        .groupBy(_.municipality)
        .mapValues { _.groupBy(_.administrativeClass)
          .mapValues(_.map(_.linkId)) }
    }
  }

  /**
    * Returns road link middle point by link id. Used to select a road link by url to be shown on map (for example: index.html#linkProperty/12345).
    * Used by Digiroad2Api /roadlinks/:linkId GET endpoint.
    *
    */
  def getRoadLinkMiddlePointByLinkId(linkId: Long): Option[(Long, Point, LinkGeomSource)] = {
    val middlePoint: Option[Point] = vvhClient.roadLinkData.fetchByLinkId(linkId)
      .flatMap { vvhRoadLink =>
        GeometryUtils.calculatePointFromLinearReference(vvhRoadLink.geometry, GeometryUtils.geometryLength(vvhRoadLink.geometry) / 2.0)
      }
    middlePoint.map((linkId, _, LinkGeomSource.NormalLinkInterface)).orElse(getComplementaryLinkMiddlePointByLinkId(linkId).map((linkId, _, LinkGeomSource.ComplimentaryLinkInterface)))
  }

  def getComplementaryLinkMiddlePointByLinkId(linkId: Long): Option[Point] = {
    val middlePoint: Option[Point] = vvhClient.complementaryData.fetchByLinkIds(Set(linkId)).headOption
      .flatMap { vvhRoadLink =>
        GeometryUtils.calculatePointFromLinearReference(vvhRoadLink.geometry, GeometryUtils.geometryLength(vvhRoadLink.geometry) / 2.0)
      }
    middlePoint
  }

  /**
    * Returns road link middle point by mml id. Used to select a road link by url to be shown on map (for example: index.html#linkProperty/mml/12345).
    * Used by Digiroad2Api /roadlinks/mml/:mmlId GET endpoint.
    *
    */
  def getRoadLinkMiddlePointByMmlId(mmlId: Long): Option[(Long, Point)] = {
    vvhClient.roadLinkData.fetchByMmlId(mmlId).flatMap { vvhRoadLink =>
      val point = GeometryUtils.calculatePointFromLinearReference(vvhRoadLink.geometry, GeometryUtils.geometryLength(vvhRoadLink.geometry) / 2.0)
      point match {
        case Some(point) => Some(vvhRoadLink.linkId, point)
        case None => None
      }
    }
  }

  def checkMMLId(vvhRoadLink: VVHRoadlink) : Option[Long] = {
    vvhRoadLink.attributes.contains("MTKID") match {
      case true => Some(vvhRoadLink.attributes("MTKID").asInstanceOf[BigInt].longValue())
      case false => None
    }
  }

  /**
    * Saves road link property data from UI. Used by Digiroad2Api /linkproperties PUT endpoint.
    */
  def updateLinkProperties(linkId: Long, functionalClass: Int, linkType: LinkType,
                           direction: TrafficDirection, administrativeClass: AdministrativeClass, username: Option[String], municipalityValidation: Int => Unit): Option[RoadLink] = {
    val vvhRoadLink = vvhClient.roadLinkData.fetchByLinkId(linkId) match {
      case Some(vvhRoadLink) => Some(vvhRoadLink)
      case None => vvhClient.complementaryData.fetchByLinkId(linkId)
    }
    vvhRoadLink.map { vvhRoadLink =>
      municipalityValidation(vvhRoadLink.municipalityCode)
      withDynTransaction {
        setLinkProperty("traffic_direction", "traffic_direction", "", direction.value, linkId, username, Some(vvhRoadLink.trafficDirection.value), None, None, None)
        if (functionalClass != FunctionalClass.Unknown) setLinkProperty("functional_class", "functional_class", "", functionalClass, linkId, username, None, None, None, None)
        if (linkType != UnknownLinkType) setLinkProperty("link_type", "link_type", "", linkType.value, linkId, username, None, None, None, None)
        if (vvhRoadLink.administrativeClass != State) setLinkProperty("administrative_class", "administrative_class", "vvh_administrative_class", administrativeClass.value, linkId, username, Some(vvhRoadLink.administrativeClass.value), None, None, checkMMLId(vvhRoadLink))
        val enrichedLink = enrichRoadLinksFromVVH(Seq(vvhRoadLink)).head
        if (enrichedLink.functionalClass != FunctionalClass.Unknown && enrichedLink.linkType != UnknownLinkType) {
          removeIncompleteness(linkId)
        }
        enrichedLink
      }
    }
  }

  /**
    * Returns road link geometry by link id. Used by RoadLinkService.getAdjacent.
    */
  def getRoadLinkGeometry(id: Long): Option[Seq[Point]] = {
    vvhClient.roadLinkData.fetchByLinkId(id).map(_.geometry)
  }

  protected def setLinkProperty(table: String, column: String, vvhColumn: String, value: Int, linkId: Long, username: Option[String],
                                optionalVVHValue: Option[Int] = None, latestModifiedAt: Option[String],
                                latestModifiedBy: Option[String], mmlId: Option[Long]) = {
    val optionalExistingValue: Option[Int] = RoadLinkServiceDAO.getLinkProperty(table, column, linkId)
    (optionalExistingValue, optionalVVHValue) match {
      case (Some(existingValue), _) =>
        table match{
          case "administrative_class" => RoadLinkServiceDAO.updateExistingAdministrativeClass(table, column, vvhColumn, linkId, username, existingValue, value, mmlId, optionalVVHValue)
          case _ => RoadLinkServiceDAO.updateExistingLinkPropertyRow(table, column, linkId, username, existingValue, value)
        }
      case (None, None) =>
        insertLinkProperty(optionalExistingValue, optionalVVHValue, table, column, vvhColumn, linkId, username, value, latestModifiedAt, latestModifiedBy, mmlId)

      case (None, Some(vvhValue)) =>
        if (vvhValue != value) // only save if it overrides VVH provided value
          insertLinkProperty(optionalExistingValue, optionalVVHValue, table, column, vvhColumn, linkId, username, value, latestModifiedAt, latestModifiedBy, mmlId)
    }
  }

  private def insertLinkProperty(optionalExistingValue: Option[Int], optionalVVHValue: Option[Int], table: String,
                                 column: String, vvhColumn: String, linkId: Long, username: Option[String], value: Int, latestModifiedAt: Option[String],
                                 latestModifiedBy: Option[String], mmlId: Option[Long]) = {
    if (latestModifiedAt.isEmpty) {
      table match{
        case "administrative_class" => RoadLinkServiceDAO.insertNewAdministrativeClass(table, column, vvhColumn, linkId, username, value, mmlId, optionalVVHValue)
        case _ =>  RoadLinkServiceDAO.insertNewLinkProperty(table, column, linkId, username, value)
      }
    } else{
      try {
        var parsedDate = ""
        if (latestModifiedAt.get.matches("^\\d\\d\\.\\d\\d\\.\\d\\d\\d\\d.*")) {
          // Finnish date format
          parsedDate = DateTimePropertyFormat.parseDateTime(latestModifiedAt.get).toString()
        } else {
          parsedDate = DateTime.parse(latestModifiedAt.get).toString(ISODateTimeFormat.dateTime())
        }
        sqlu"""insert into #$table (id, link_id, #$column, modified_date, modified_by)
                 select primary_key_seq.nextval, $linkId, $value,
                 to_timestamp_tz($parsedDate, 'YYYY-MM-DD"T"HH24:MI:SS.ff3"+"TZH:TZM'), $latestModifiedBy
                 from dual
                 where not exists (select * from #$table where link_id = $linkId)""".execute
      } catch {
        case e: Exception =>
          println("ERR! -> table " + table + " (" + linkId + ", " + value + "): mod timestamp = " + latestModifiedAt.getOrElse("null"))
          throw e
      }
    }
  }


  private def fetchOverrides(idTableName: String): Map[Long, (Option[(Long, Int, DateTime, String)],
    Option[(Long, Int, DateTime, String)], Option[(Long, Int, DateTime, String)], Option[(Long, Int, DateTime, String)])] = {
    sql"""select i.id, t.link_id, t.traffic_direction, t.modified_date, t.modified_by,
          f.link_id, f.functional_class, f.modified_date, f.modified_by,
          l.link_id, l.link_type, l.modified_date, l.modified_by,
          a.link_id, a.administrative_class, a.created_date, a.created_by
            from #$idTableName i
            left join traffic_direction t on i.id = t.link_id
            left join functional_class f on i.id = f.link_id
            left join link_type l on i.id = l.link_id
            left join administrative_class a on i.id = a.link_id where a.valid_to is null
      """.as[(Long, Option[Long], Option[Int], Option[DateTime], Option[String],
      Option[Long], Option[Int], Option[DateTime], Option[String],
      Option[Long], Option[Int], Option[DateTime], Option[String],
      Option[Long], Option[Int], Option[DateTime], Option[String])].list.map(row =>
    {
      val td = (row._2, row._3, row._4, row._5) match {
        case (Some(linkId), Some(dir), Some(modDate), Some(modBy)) => Option((linkId, dir, modDate, modBy))
        case _ => None
      }
      val fc = (row._6, row._7, row._8, row._9) match {
        case (Some(linkId), Some(dir), Some(modDate), Some(modBy)) => Option((linkId, dir, modDate, modBy))
        case _ => None
      }
      val lt = (row._10, row._11, row._12, row._13) match {
        case (Some(linkId), Some(dir), Some(modDate), Some(modBy)) => Option((linkId, dir, modDate, modBy))
        case _ => None
      }
      val ac = (row._14, row._15, row._16, row._17) match{
        case (Some(linkId), Some(value), Some(createdDate), Some(createdBy)) => Option((linkId, value, createdDate, createdBy))
        case _ => None
      }
      row._1 ->(td, fc, lt, ac)
    }
    ).toMap
  }

  def getViiteRoadLinksHistoryFromVVH(roadAddressesLinkIds: Set[Long]): Seq[VVHHistoryRoadLink] = {
    if (roadAddressesLinkIds.nonEmpty) {
      val historyData = Await.result(vvhClient.historyData.fetchVVHRoadLinkByLinkIdsF(roadAddressesLinkIds), atMost = Duration.Inf)
      val groupedData = historyData.groupBy(_.linkId)
      groupedData.mapValues(_.maxBy(_.endDate)).values.toSeq
    } else
      Nil
  }

  def getViiteCurrentAndHistoryRoadLinksFromVVH(roadAddressesLinkIds: Set[Long], frozenTimeVVHAPIServiceEnabled : Boolean = false): (Seq[RoadLink], Seq[VVHHistoryRoadLink]) = {
    val fut = for{
      f1Result <- vvhClient.historyData.fetchVVHRoadLinkByLinkIdsF(roadAddressesLinkIds)
      f2Result <- if(frozenTimeVVHAPIServiceEnabled)vvhClient.frozenTimeRoadLinkData.fetchByLinkIdsF(roadAddressesLinkIds) else {vvhClient.roadLinkData.fetchByLinkIdsF(roadAddressesLinkIds)}
      f3Result <- vvhClient.complementaryData.fetchByLinkIdsF(roadAddressesLinkIds)
    } yield (f1Result, f2Result, f3Result)

    val (historyData, currentData, complementaryData) = Await.result(fut, Duration.Inf)
    val uniqueHistoryData = historyData.groupBy(_.linkId).mapValues(_.maxBy(_.endDate)).values.toSeq

    withDynTransaction {
      (enrichRoadLinksFromVVH(currentData ++ complementaryData, Seq()), uniqueHistoryData)
    }
  }

  def getViiteCurrentLinksFromVVH(roadAddressesLinkIds: Set[Long]): (Seq[RoadLink]) = {
    val fut = for{
      f1Result <- vvhClient.roadLinkData.fetchByLinkIdsF(roadAddressesLinkIds)
    } yield (f1Result)
    val currentData = Await.result(fut, Duration.Inf)

    withDynTransaction {
      enrichRoadLinksFromVVH(currentData, Seq())
    }
  }

  def getRoadLinksHistoryFromVVH(bounds: BoundingRectangle, municipalities: Set[Int] = Set()) : Seq[RoadLink] = {
    val (historyRoadLinks, roadlinks) = Await.result(vvhClient.historyData.fetchByMunicipalitiesAndBoundsF(bounds, municipalities).zip(vvhClient.roadLinkData.fetchByMunicipalitiesAndBoundsF(bounds, municipalities)), atMost = Duration.Inf)
    val linkprocessor = new VVHRoadLinkHistoryProcessor()
    // picks links that are newest in each link chains history with that are with in set tolerance . Keeps ones with no current link
    val filtteredHistoryLinks = linkprocessor.process(historyRoadLinks, roadlinks)

    withDynTransaction {
      enrichRoadLinksFromVVH(filtteredHistoryLinks)
    }
  }

  /**
    * Returns road links and change data from VVH by bounding box and road numbers and municipalities. Used by RoadLinkService.getRoadLinksFromVVH and SpeedLimitService.get.
    */
  def getViiteRoadLinksAndChangesFromVVH(bounds: BoundingRectangle, roadNumbers: Seq[(Int, Int)],
                                         municipalities: Set[Int] = Set(), everything: Boolean,
                                         publicRoads: Boolean): (Seq[RoadLink], Seq[ChangeInfo])= {
    val (changes, links) = Await.result(vvhClient.roadLinkChangeInfo.fetchByBoundsAndMunicipalitiesF(bounds, municipalities)
      .zip(everything match {
        case true => vvhClient.roadLinkData.fetchByMunicipalitiesAndBoundsF(bounds, municipalities)
        case false=> vvhClient.roadLinkData.fetchByRoadNumbersBoundsAndMunicipalitiesF(bounds, municipalities, roadNumbers, publicRoads)
      }), atMost = Duration.Inf)

    withDynTransaction {
      (enrichRoadLinksFromVVH(links, changes), changes)
    }
  }

  /**
    * Returns road links without change data for Viite from VVH by bounding box and road numbers and municipalities.
    */
  def getViiteRoadLinksFromVVH(bounds: BoundingRectangle, roadNumbers: Seq[(Int, Int)],
                               municipalities: Set[Int] = Set(),
                               publicRoads: Boolean, frozenTimeVVHAPIServiceEnabled:Boolean): Seq[RoadLink] = {
    val links = Await.result(

      if(frozenTimeVVHAPIServiceEnabled)
        vvhClient.frozenTimeRoadLinkData.fetchByRoadNumbersBoundsAndMunicipalitiesF(bounds, municipalities, roadNumbers, publicRoads)
      else
        vvhClient.roadLinkData.fetchByRoadNumbersBoundsAndMunicipalitiesF(bounds, municipalities, roadNumbers, publicRoads),
      atMost = Duration.Inf)

    withDynTransaction {
      (enrichRoadLinksFromVVH(links, Seq()), Seq())._1
    }
  }

  /**
    * Returns the road links from VVH by municipality.
    *
    * @param municipality A integer, representative of the municipality Id.
    */
  def getViiteRoadLinksFromVVHByMunicipality(municipality: Int, frozenTimeVVHAPIServiceEnabled:Boolean =false): Seq[RoadLink] = {
    val links = if(frozenTimeVVHAPIServiceEnabled){vvhClient.frozenTimeRoadLinkData.fetchByMunicipality(municipality)} else vvhClient.roadLinkData.fetchByMunicipality(municipality)
    withDynTransaction {
      (enrichRoadLinksFromVVH(links, Seq()), Seq())._1
    }
  }

  /**
    * Returns road links and change data from VVH by bounding box and road numbers and municipalities. Used by RoadLinkService.getRoadLinksFromVVH and SpeedLimitService.get.
    */
  def getViiteRoadLinksWithoutChangesFromVVH(linkIds: Set[Long], municipalities: Set[Int] = Set()): (Seq[RoadLink], Seq[ChangeInfo])= {
    (getViiteRoadLinksByLinkIdsFromVVH(linkIds), Seq())
  }

  def getViiteRoadLinksFromVVH(bounds: BoundingRectangle, roadNumbers: Seq[(Int, Int)], municipalities: Set[Int],
                               everything: Boolean, publicRoads: Boolean, frozenTimeVVHAPIServiceEnabled:Boolean) : Seq[RoadLink] =
    if (bounds.area >= 1E6 || frozenTimeVVHAPIServiceEnabled)
      getViiteRoadLinksFromVVH(bounds, roadNumbers, municipalities, publicRoads,frozenTimeVVHAPIServiceEnabled)
    else
      getViiteRoadLinksAndChangesFromVVH(bounds, roadNumbers, municipalities, everything, publicRoads)._1

  def getViiteRoadPartsFromVVH(linkIds: Set[Long], municipalities: Set[Int] = Set()) : Seq[RoadLink] =
    getViiteRoadLinksWithoutChangesFromVVH(linkIds, municipalities)._1

  def getChangeInfoFromVVH(bounds: BoundingRectangle, municipalities: Set[Int]): Seq[ChangeInfo] ={
    Await.result(getChangeInfoFromVVHF(bounds, municipalities), atMost = Duration.Inf)
  }

  def getChangeInfoFromVVHF(bounds: BoundingRectangle, municipalities: Set[Int]): Future[Seq[ChangeInfo]] ={
    vvhClient.roadLinkChangeInfo.fetchByBoundsAndMunicipalitiesF(bounds, municipalities)
  }

  def getChangeInfoFromVVHF(linkIds: Set[Long]): Future[Seq[ChangeInfo]] ={
    vvhClient.roadLinkChangeInfo.fetchByLinkIdsF(linkIds)
  }

  def reloadRoadNodesFromVVH(municipality: Int): (Seq[VVHRoadNodes])= {
    vvhClient.roadNodesData.fetchByMunicipality(municipality)
  }

  /**
    * Returns closest road link by user's authorization and point coordinates. Used by Digiroad2Api /servicePoints PUT and /servicePoints/:id PUT endpoints.
    */
  def getClosestRoadlinkFromVVH(user: User, point: Point): Option[VVHRoadlink] = {
    val diagonal = Vector3d(500, 500, 0)

    val roadLinks =
      if (user.isOperator())
        getVVHRoadLinks(BoundingRectangle(point - diagonal, point + diagonal))
      else
        getVVHRoadLinks(BoundingRectangle(point - diagonal, point + diagonal), user.configuration.authorizedMunicipalities)

    if (roadLinks.isEmpty)
      None
    else
      Some(roadLinks.minBy(roadlink => minimumDistance(point, roadlink.geometry)))
  }

  protected def removeIncompleteness(linkId: Long) = {
    sqlu"""delete from incomplete_link where link_id = $linkId""".execute
  }

  /**
    * Updates road link data in OTH db. Used by Digiroad2Context LinkPropertyUpdater Akka actor.
    */
  def updateRoadLinkChanges(roadLinkChangeSet: RoadLinkChangeSet): Unit = {
    updateAutoGeneratedProperties(roadLinkChangeSet.adjustedRoadLinks)
    updateIncompleteLinks(roadLinkChangeSet.incompleteLinks)
  }

  /**
    * Updates road link autogenerated properties (functional class, link type and traffic direction). Used by RoadLinkService.updateRoadLinkChanges.
    */
  def updateAutoGeneratedProperties(adjustedRoadLinks: Seq[RoadLink]) {
    def createUsernameForAutogenerated(modifiedBy: Option[String]): Option[String] =  {
      modifiedBy match {
        case Some("automatic_generation") => modifiedBy
        case _ => None
      }
    }
    def updateProperties(vvhRoadLinks: Seq[VVHRoadlink])(roadLink: RoadLink) = {
      val vvhRoadLink = vvhRoadLinks.find(_.linkId == roadLink.linkId)
      val vvhTrafficDirection = vvhRoadLink.map(v => v.trafficDirection.value)
      // Separate auto-generated links from change info links: username should be empty for change info links
      val username = createUsernameForAutogenerated(roadLink.modifiedBy)

      if (roadLink.trafficDirection != TrafficDirection.UnknownDirection)  setLinkProperty("traffic_direction", "traffic_direction", "", roadLink.trafficDirection.value, roadLink.linkId, None, vvhTrafficDirection, roadLink.modifiedAt, roadLink.modifiedBy, None)
      if (roadLink.functionalClass != FunctionalClass.Unknown) setLinkProperty("functional_class", "functional_class", "", roadLink.functionalClass, roadLink.linkId, username, None, roadLink.modifiedAt, roadLink.modifiedBy, None)
      if (roadLink.linkType != UnknownLinkType) setLinkProperty("link_type", "link_type", "", roadLink.linkType.value, roadLink.linkId, username, None, roadLink.modifiedAt, roadLink.modifiedBy, None)
    }
    val vvhRoadLinks = vvhClient.roadLinkData.fetchByLinkIds(adjustedRoadLinks.map(_.linkId).toSet)
    withDynTransaction {
      adjustedRoadLinks.foreach(updateProperties(vvhRoadLinks))
      adjustedRoadLinks.foreach(link =>
        if (link.functionalClass != FunctionalClass.Unknown && link.linkType != UnknownLinkType) removeIncompleteness(link.linkId)
      )
    }
  }

  /**
    * Updates incomplete road link list (incomplete = functional class or link type missing). Used by RoadLinkService.updateRoadLinkChanges.
    */
  protected def updateIncompleteLinks(incompleteLinks: Seq[IncompleteLink]) = {
    def setIncompleteness(incompleteLink: IncompleteLink) {
      withDynTransaction {
        sqlu"""insert into incomplete_link(id, link_id, municipality_code, administrative_class)
                 select primary_key_seq.nextval, ${incompleteLink.linkId}, ${incompleteLink.municipalityCode}, ${incompleteLink.administrativeClass.value} from dual
                 where not exists (select * from incomplete_link where link_id = ${incompleteLink.linkId})""".execute
      }
    }
    incompleteLinks.foreach(setIncompleteness)
  }

  /**
    * Returns value when all given values are the same. Used by RoadLinkService.fillIncompleteLinksWithPreviousLinkData.
    */
  def useValueWhenAllEqual[T](values: Seq[T]): Option[T] = {
    if (values.nonEmpty && values.forall(_ == values.head))
      Some(values.head)
    else
      None
  }

  private def getLatestModification[T](values: Map[Option[String], Option [String]]) = {
    if (values.nonEmpty)
      Some(values.reduce(calculateLatestDate))
    else
      None
  }

  private def calculateLatestDate(stringOption1: (Option[String], Option[String]), stringOption2: (Option[String], Option[String])): (Option[String], Option[String]) = {
    val date1 = convertStringToDate(stringOption1._1)
    val date2 = convertStringToDate(stringOption2._1)
    (date1, date2) match {
      case (Some(d1), Some(d2)) =>
        if (d1.after(d2))
          stringOption1
        else
          stringOption2
      case (Some(d1), None) => stringOption1
      case (None, Some(d2)) => stringOption2
      case (None, None) => (None, None)
    }
  }

  private def convertStringToDate(str: Option[String]): Option[Date] = {
    if (str.exists(_.trim.nonEmpty))
      Some(new SimpleDateFormat("dd.MM.yyyy hh:mm:ss").parse(str.get))
    else
      None
  }

  /**
    *  Fills incomplete road links with the previous link information.
    *  Used by ROadLinkService.enrichRoadLinksFromVVH.
    */
  def fillIncompleteLinksWithPreviousLinkData(incompleteLinks: Seq[RoadLink], changes: Seq[ChangeInfo]): (Seq[RoadLink], Seq[RoadLink]) = {
    val oldRoadLinkProperties = getOldRoadLinkPropertiesForChanges(changes)
    incompleteLinks.map { incompleteLink =>
      val oldIdsForIncompleteLink = changes.filter(_.newId == Option(incompleteLink.linkId)).flatMap(_.oldId)
      val oldPropertiesForIncompleteLink = oldRoadLinkProperties.filter(oldLink => oldIdsForIncompleteLink.contains(oldLink.linkId))
      val newFunctionalClass = incompleteLink.functionalClass match {
        case FunctionalClass.Unknown =>  useValueWhenAllEqual(oldPropertiesForIncompleteLink.map(_.functionalClass)).getOrElse(FunctionalClass.Unknown)
        case _ => incompleteLink.functionalClass
      }
      val newLinkType = incompleteLink.linkType match {
        case UnknownLinkType => useValueWhenAllEqual(oldPropertiesForIncompleteLink.map(_.linkType)).getOrElse(UnknownLinkType)
        case _ => incompleteLink.linkType
      }
      val modifications = (oldPropertiesForIncompleteLink.map(_.modifiedAt) zip oldPropertiesForIncompleteLink.map(_.modifiedBy)).toMap
      val modicationsWithVvhModification = modifications ++ Map(incompleteLink.modifiedAt -> incompleteLink.modifiedBy)
      val (newModifiedAt, newModifiedBy) = getLatestModification(modicationsWithVvhModification).getOrElse(incompleteLink.modifiedAt, incompleteLink.modifiedBy)
      val previousDirection = useValueWhenAllEqual(oldPropertiesForIncompleteLink.map(_.trafficDirection))

      incompleteLink.copy(
        functionalClass  = newFunctionalClass,
        linkType          = newLinkType,
        trafficDirection  =  previousDirection match
        { case Some(TrafficDirection.UnknownDirection) => incompleteLink.trafficDirection
          case None => incompleteLink.trafficDirection
          case _ => previousDirection.get
        },
        modifiedAt = newModifiedAt,
        modifiedBy = newModifiedBy)
    }.partition(isComplete)
  }

  /**
    * Checks if road link is complete (has both functional class and link type in OTH).
    * Used by RoadLinkService.fillIncompleteLinksWithPreviousLinkData and RoadLinkService.isIncomplete.
    */
  def isComplete(roadLink: RoadLink): Boolean = {
    roadLink.linkSource != LinkGeomSource.NormalLinkInterface ||
      roadLink.functionalClass != FunctionalClass.Unknown && roadLink.linkType.value != UnknownLinkType.value
  }

  def getRoadLinksAndComplementaryLinksFromVVHByMunicipality(municipality: Int): Seq[RoadLink] = {
    val fut = for {
      complementary <-getComplementaryRoadLinksFromVVHFuture(municipality)
      roadLinks <- getRoadLinksFromVVHFuture(municipality)
    } yield (complementary, roadLinks)
    val (complementaryResult, roadLinksResult) = Await.result(fut, Duration.Inf)
    complementaryResult           ++roadLinksResult
  }

  def getRoadNodesFromVVHByMunicipality(municipality: Int): Seq[VVHRoadNodes] = {
    Await.result(getRoadNodesFromVVHFuture(municipality), Duration.Inf)
  }

  /**
    * Checks if road link is not complete. Used by RoadLinkService.enrichRoadLinksFromVVH.
    */
  def isIncomplete(roadLink: RoadLink): Boolean = !isComplete(roadLink)

  /**
    * Checks if road link is partially complete (has functional class OR link type but not both). Used by RoadLinkService.enrichRoadLinksFromVVH.
    */
  def isPartiallyIncomplete(roadLink: RoadLink): Boolean = {
    val onlyFunctionalClassIsSet = roadLink.functionalClass != FunctionalClass.Unknown && roadLink.linkType.value == UnknownLinkType.value
    val onlyLinkTypeIsSet = roadLink.functionalClass == FunctionalClass.Unknown && roadLink.linkType.value != UnknownLinkType.value
    onlyFunctionalClassIsSet || onlyLinkTypeIsSet
  }

  /**
    * This method performs formatting operations to given vvh road links:
    * - auto-generation of functional class and link type by feature class
    * - information transfer from old link to new link from change data
    * It also passes updated links and incomplete links to be saved to db by actor.
    *
    * @param vvhRoadLinks
    * @param changes
    * @return Road links
    */
  protected def enrichRoadLinksFromVVH(vvhRoadLinks: Seq[VVHRoadlink], changes: Seq[ChangeInfo] = Nil): Seq[RoadLink] = {
    def autoGenerateProperties(roadLink: RoadLink): RoadLink = {
      val vvhRoadLink = vvhRoadLinks.find(_.linkId == roadLink.linkId)
      vvhRoadLink.get.featureClass match {
        case FeatureClass.TractorRoad => roadLink.copy(functionalClass = 7, linkType = TractorRoad, modifiedBy = Some("automatic_generation"), modifiedAt = Some(DateTimePropertyFormat.print(DateTime.now())))
        case FeatureClass.DrivePath => roadLink.copy(functionalClass = 6, linkType = SingleCarriageway, modifiedBy = Some("automatic_generation"), modifiedAt = Some(DateTimePropertyFormat.print(DateTime.now())))
        case FeatureClass.CycleOrPedestrianPath => roadLink.copy(functionalClass = 8, linkType = CycleOrPedestrianPath, modifiedBy = Some("automatic_generation"), modifiedAt = Some(DateTimePropertyFormat.print(DateTime.now())))
        case _ => roadLink //similar logic used in roadaddressbuilder
      }
    }
    def toIncompleteLink(roadLink: RoadLink): IncompleteLink = {
      val vvhRoadLink = vvhRoadLinks.find(_.linkId == roadLink.linkId)
      IncompleteLink(roadLink.linkId, vvhRoadLink.get.municipalityCode, roadLink.administrativeClass)
    }

    def canBeAutoGenerated(roadLink: RoadLink): Boolean = {
      vvhRoadLinks.find(_.linkId == roadLink.linkId).get.featureClass match {
        case FeatureClass.AllOthers => false
        case _ => true
      }
    }

    val roadLinkDataByLinkId: Seq[RoadLink] = getRoadLinkDataByLinkIds(vvhRoadLinks)
    val (incompleteLinks, completeLinks) = roadLinkDataByLinkId.partition(isIncomplete)
    val (linksToAutoGenerate, incompleteOtherLinks) = incompleteLinks.partition(canBeAutoGenerated)
    val autoGeneratedLinks = linksToAutoGenerate.map(autoGenerateProperties)
    val (changedLinks, stillIncompleteLinks) = fillIncompleteLinksWithPreviousLinkData(incompleteOtherLinks, changes)
    val changedPartiallyIncompleteLinks = stillIncompleteLinks.filter(isPartiallyIncomplete)
    val stillIncompleteLinksInUse = stillIncompleteLinks.filter(_.constructionType == ConstructionType.InUse)

    eventbus.publish("linkProperties:changed",
      RoadLinkChangeSet(autoGeneratedLinks ++ changedLinks ++ changedPartiallyIncompleteLinks, stillIncompleteLinksInUse.map(toIncompleteLink)))

    completeLinks ++ autoGeneratedLinks ++ changedLinks ++ stillIncompleteLinks
  }

  /**
    * Uses old road link ids from change data to fetch their OTH overridden properties from db.
    * Used by RoadLinkSErvice.fillIncompleteLinksWithPreviousLinkData.
    */
  def getOldRoadLinkPropertiesForChanges(changes: Seq[ChangeInfo]): Seq[RoadLinkProperties] = {
    val oldLinkIds = changes.flatMap(_.oldId)
    val propertyRows = fetchRoadLinkPropertyRows(oldLinkIds.toSet)

    oldLinkIds.map { linkId =>
      val latestModification = propertyRows.latestModifications(linkId)
      val (modifiedAt, modifiedBy) = (latestModification.map(_._1), latestModification.map(_._2))

      RoadLinkProperties(linkId,
        propertyRows.functionalClassValue(linkId),
        propertyRows.linkTypeValue(linkId),
        propertyRows.trafficDirectionValue(linkId).getOrElse(TrafficDirection.UnknownDirection),
        propertyRows.administrativeClassValue(linkId).getOrElse(Unknown),
        modifiedAt.map(DateTimePropertyFormat.print),
        modifiedBy)
    }
  }

  /**
    * Passes VVH road links to adjustedRoadLinks to get road links. Used by RoadLinkService.enrichRoadLinksFromVVH.
    */
  def getRoadLinkDataByLinkIds(vvhRoadLinks: Seq[VVHRoadlink]): Seq[RoadLink] = {
    adjustedRoadLinks(vvhRoadLinks)
  }

  private def adjustedRoadLinks(vvhRoadlinks: Seq[VVHRoadlink]): Seq[RoadLink] = {
    val propertyRows = fetchRoadLinkPropertyRows(vvhRoadlinks.map(_.linkId).toSet)

    vvhRoadlinks.map { link =>
      val latestModification = propertyRows.latestModifications(link.linkId, link.modifiedAt.map(at => (at, "vvh_modified")))
      val (modifiedAt, modifiedBy) = (latestModification.map(_._1), latestModification.map(_._2))

      RoadLink(link.linkId, link.geometry,
        GeometryUtils.geometryLength(link.geometry),
        propertyRows.administrativeClassValue(link.linkId).getOrElse(link.administrativeClass),
        propertyRows.functionalClassValue(link.linkId),
        propertyRows.trafficDirectionValue(link.linkId).getOrElse(link.trafficDirection),
        propertyRows.linkTypeValue(link.linkId),
        modifiedAt.map(DateTimePropertyFormat.print),
        modifiedBy, link.attributes, link.constructionType, link.linkSource)
    }
  }

  private def fetchRoadLinkPropertyRows(linkIds: Set[Long]): RoadLinkPropertyRows = {
    def cleanMap(parameterMap: Map[Long, (Option[(Long, Int, DateTime, String)])]): Map[RoadLinkId, RoadLinkPropertyRow] = {
      parameterMap.filter(i => i._2.nonEmpty).mapValues(i => i.get)
    }
    def splitMap(parameterMap: Map[Long, (Option[(Long, Int, DateTime, String)],
      Option[(Long, Int, DateTime, String)], Option[(Long, Int, DateTime, String)],
      Option[(Long, Int, DateTime, String)] )]) = {
      (cleanMap(parameterMap.map(i => i._1 -> i._2._1)),
        cleanMap(parameterMap.map(i => i._1 -> i._2._2)),
        cleanMap(parameterMap.map(i => i._1 -> i._2._3)),
        cleanMap(parameterMap.map(i => i._1 -> i._2._4)))
    }
    MassQuery.withIds(linkIds) {
      idTableName =>
        val (td, fc, lt, ac) = splitMap(fetchOverrides(idTableName))
        RoadLinkPropertyRows(td, fc, lt, ac)
    }
  }

  type RoadLinkId = Long
  type RoadLinkPropertyRow = (Long, Int, DateTime, String)

  case class RoadLinkPropertyRows(trafficDirectionRowsByLinkId: Map[RoadLinkId, RoadLinkPropertyRow],
                                  functionalClassRowsByLinkId: Map[RoadLinkId, RoadLinkPropertyRow],
                                  linkTypeRowsByLinkId: Map[RoadLinkId, RoadLinkPropertyRow],
                                  administrativeClassRowsByLinkId: Map[RoadLinkId, RoadLinkPropertyRow]) {

    def functionalClassValue(linkId: Long): Int = {
      val functionalClassRowOption = functionalClassRowsByLinkId.get(linkId)
      functionalClassRowOption.map(_._2).getOrElse(FunctionalClass.Unknown)
    }

    def linkTypeValue(linkId: Long): LinkType = {
      val linkTypeRowOption = linkTypeRowsByLinkId.get(linkId)
      linkTypeRowOption.map(linkTypeRow => LinkType(linkTypeRow._2)).getOrElse(UnknownLinkType)
    }

    def trafficDirectionValue(linkId: Long): Option[TrafficDirection] = {
      val trafficDirectionRowOption = trafficDirectionRowsByLinkId.get(linkId)
      trafficDirectionRowOption.map(trafficDirectionRow => TrafficDirection(trafficDirectionRow._2))
    }

    def administrativeClassValue(linkId: Long): Option[AdministrativeClass] = {
      val administrativeRowOption = administrativeClassRowsByLinkId.get(linkId)
      administrativeRowOption.map( ac => AdministrativeClass.apply(ac._2))
    }

    def latestModifications(linkId: Long, optionalModification: Option[(DateTime, String)] = None): Option[(DateTime, String)] = {
      val functionalClassRowOption = functionalClassRowsByLinkId.get(linkId)
      val linkTypeRowOption = linkTypeRowsByLinkId.get(linkId)
      val trafficDirectionRowOption = trafficDirectionRowsByLinkId.get(linkId)
      val administrativeRowOption = administrativeClassRowsByLinkId.get(linkId)

      val modifications = List(functionalClassRowOption, trafficDirectionRowOption, linkTypeRowOption, administrativeRowOption).map {
        case Some((_, _, at, by)) => Some((at, by))
        case _ => None
      } :+ optionalModification
      modifications.reduce(calculateLatestModifications)
    }

    private def calculateLatestModifications(a: Option[(DateTime, String)], b: Option[(DateTime, String)]) = {
      (a, b) match {
        case (Some((firstModifiedAt, firstModifiedBy)), Some((secondModifiedAt, secondModifiedBy))) =>
          if (firstModifiedAt.isAfter(secondModifiedAt))
            Some((firstModifiedAt, firstModifiedBy))
          else
            Some((secondModifiedAt, secondModifiedBy))
        case (Some((firstModifiedAt, firstModifiedBy)), None) => Some((firstModifiedAt, firstModifiedBy))
        case (None, Some((secondModifiedAt, secondModifiedBy))) => Some((secondModifiedAt, secondModifiedBy))
        case (None, None) => None
      }
    }
  }

  /**
    * Get the link end points depending on the road link directions
    *
    * @param roadlink The Roadlink
    * @return End points of the road link directions
    */
  def getRoadLinkEndDirectionPoints(roadlink: RoadLink) : Seq[Point] = {
    val endPoints = GeometryUtils.geometryEndpoints(roadlink.geometry)
    roadlink.trafficDirection match {
      case TrafficDirection.TowardsDigitizing =>
        Seq(endPoints._2)
      case TrafficDirection.AgainstDigitizing =>
        Seq(endPoints._1)
      case _ =>
        Seq(endPoints._1, endPoints._2)
    }
  }

  /**
    * Get the link start points depending on the road link directions
    *
    * @param roadlink The Roadlink
    * @return Start points of the road link directions
    */
  def getRoadLinkStartDirectionPoints(roadlink: RoadLink) : Seq[Point] = {
    val endPoints = GeometryUtils.geometryEndpoints(roadlink.geometry)
    roadlink.trafficDirection match {
      case TrafficDirection.TowardsDigitizing =>
        Seq(endPoints._1)
      case TrafficDirection.AgainstDigitizing =>
        Seq(endPoints._2)
      case _ =>
        Seq(endPoints._1, endPoints._2)
    }
  }

  /**
    * Returns adjacent road links by link id. Used by Digiroad2Api /roadlinks/adjacent/:id GET endpoint and CsvGenerator.generateDroppedManoeuvres.
    */
  def getAdjacent(linkId: Long): Seq[RoadLink] = {
    val sourceRoadLink = getRoadLinksByLinkIdsFromVVH(Set(linkId)).headOption
    val sourceLinkGeometryOption = sourceRoadLink.map(_.geometry)
    val sourceDirectionPoints = getRoadLinkEndDirectionPoints(sourceRoadLink.get)
    sourceLinkGeometryOption.map(sourceLinkGeometry => {
      val sourceLinkEndpoints = GeometryUtils.geometryEndpoints(sourceLinkGeometry)
      val delta: Vector3d = Vector3d(0.1, 0.1, 0)
      val bounds = BoundingRectangle(sourceLinkEndpoints._1 - delta, sourceLinkEndpoints._1 + delta)
      val bounds2 = BoundingRectangle(sourceLinkEndpoints._2 - delta, sourceLinkEndpoints._2 + delta)
      val roadLinks = getRoadLinksFromVVH(bounds, bounds2)
      roadLinks.filterNot(_.linkId == linkId)
        .filter(roadLink => roadLink.isCarTrafficRoad)
        .filter(roadLink => {
          val targetLinkGeometry = roadLink.geometry
          GeometryUtils.areAdjacent(sourceLinkGeometry, targetLinkGeometry)
        })
        .filter(roadlink => {
          //It's a valid destination link to turn if the end point of the source exists on the
          //start points of the destination links
          val pointDirections = getRoadLinkStartDirectionPoints(roadlink)
          (sourceDirectionPoints.exists(sourcePoint => pointDirections.contains(sourcePoint)))
        })
    }).getOrElse(Nil)
  }

  private val cacheDirectory = {
    val properties = new Properties()
    properties.load(getClass.getResourceAsStream("/digiroad2.properties"))
    properties.getProperty("digiroad2.cache.directory", "/tmp/digiroad.cache")
  }

  def geometryToBoundingBox(s: Seq[Point], delta: Vector3d) = {
    BoundingRectangle(Point(s.minBy(_.x).x, s.minBy(_.y).y) - delta, Point(s.maxBy(_.x).x, s.maxBy(_.y).y) + delta)
  }

  /**
    * Returns adjacent road links for list of ids.
    * Used by Digiroad2Api /roadlinks/adjacents/:ids GET endpoint
    */
  def getAdjacents(linkIds: Set[Long]): Map[Long, Seq[RoadLink]] = {
    val roadLinks = getRoadLinksByLinkIdsFromVVH(linkIds)
    val sourceLinkGeometryMap = roadLinks.map(rl => rl -> rl.geometry).toMap
    val delta: Vector3d = Vector3d(0.1, 0.1, 0)
    val sourceLinkBoundingBox = geometryToBoundingBox(sourceLinkGeometryMap.values.flatten.toSeq, delta)
    val sourceLinks = getRoadLinksFromVVH(sourceLinkBoundingBox, Set[Int]()).filter(roadLink => roadLink.isCarTrafficRoad)

    val mapped = sourceLinks.map(rl => rl.linkId -> getRoadLinkEndDirectionPoints(rl)).toMap
    val reverse = sourceLinks.map(rl => rl -> getRoadLinkStartDirectionPoints(rl)).flatMap {
      case (k, v) =>
        v.map(value => value -> k)
    }
    val reverseMap = reverse.groupBy(_._1).mapValues(s => s.map(_._2))

    mapped.map( tuple =>
      (tuple._1, tuple._2.flatMap(ep => {
        reverseMap.keys.filter(p => GeometryUtils.areAdjacent(p, ep )).flatMap( p =>
          reverseMap.getOrElse(p, Seq()).filterNot(rl => rl.linkId == tuple._1))
      }))
    )
  }

  private def getCacheDirectory: Option[File] = {
    val file = new File(cacheDirectory)
    try {
      if ((file.exists || file.mkdir()) && file.isDirectory) {
        return Option(file)
      } else {
        logger.error("Unable to create cache directory " + cacheDirectory)
      }
    } catch {
      case ex: SecurityException =>
        logger.error("Unable to create cache directory due to security", ex)
      case ex: IOException =>
        logger.error("Unable to create cache directory due to I/O error", ex)
    }
    None
  }

  private val geometryCacheFileNames = "geom_%d_%d.cached"
  private val changeCacheFileNames = "changes_%d_%d.cached"
  private val nodeCacheFileNames = "nodes_%d_%d.cached"
  private val geometryCacheStartsMatch = "geom_%d_"
  private val changeCacheStartsMatch = "changes_%d_"
  private val nodeCacheStartsMatch = "nodes_%d_"
  private val allCacheEndsMatch = ".cached"
  private val complementaryCacheFileNames = "complementary_%d_%d.cached"
  private val complementaryCacheStartsMatch = "complementary_%d_"

  private def deleteOldNodeCacheFiles(municipalityCode: Int, dir: Option[File], maxAge: Long) = {
    val oldNodeCacheFiles = dir.map(cacheDir => cacheDir.listFiles(new FilenameFilter {
      override def accept(dir: File, name: String): Boolean = {
        name.startsWith(nodeCacheStartsMatch.format(municipalityCode))
      }
    }).filter(f => f.lastModified() + maxAge < System.currentTimeMillis))
    oldNodeCacheFiles.getOrElse(Array()).foreach(f =>
      try {
        f.delete()
      } catch {
        case ex: Exception => logger.warn("Unable to delete old node cache file " + f.toPath, ex)
      }
    )
  }

  private def deleteOldCacheFiles(municipalityCode: Int, dir: Option[File], maxAge: Long) = {
    val oldCacheFiles = dir.map(cacheDir => cacheDir.listFiles(new FilenameFilter {
      override def accept(dir: File, name: String): Boolean = {
        name.startsWith(geometryCacheStartsMatch.format(municipalityCode))
      }
    }).filter(f => f.lastModified() + maxAge < System.currentTimeMillis))
    oldCacheFiles.getOrElse(Array()).foreach(f =>
      try {
        f.delete()
      } catch {
        case ex: Exception => logger.warn("Unable to delete old Geometry cache file " + f.toPath, ex)
      }
    )
    val oldChangesCacheFiles = dir.map(cacheDir => cacheDir.listFiles(new FilenameFilter {
      override def accept(dir: File, name: String): Boolean = {
        name.startsWith(changeCacheStartsMatch.format(municipalityCode))
      }
    }).filter(f => f.lastModified() + maxAge < System.currentTimeMillis))
    oldChangesCacheFiles.getOrElse(Array()).foreach(f =>
      try {
        f.delete()
      } catch {
        case ex: Exception => logger.warn("Unable to delete old change cache file " + f.toPath, ex)
      }
    )
    val oldCompCacheFiles = dir.map(cacheDir => cacheDir.listFiles(new FilenameFilter {
      override def accept(dir: File, name: String): Boolean = {
        name.startsWith(complementaryCacheStartsMatch.format(municipalityCode))
      }
    }).filter(f => f.lastModified() + maxAge < System.currentTimeMillis))
    oldCompCacheFiles.getOrElse(Array()).foreach(f =>
      try {
        f.delete()
      } catch {
        case ex: Exception => logger.warn("Unable to delete old Complementary cache file " + f.toPath, ex)
      }
    )
  }

  private def getCacheWithComplementaryFiles(municipalityCode: Int, dir: Option[File]): (Option[(File, File, File)]) = {
    val twentyHours = 20L * 60 * 60 * 1000
    deleteOldCacheFiles(municipalityCode, dir, twentyHours)

    val cachedGeometryFile = dir.map(cacheDir => cacheDir.listFiles(new FilenameFilter {
      override def accept(dir: File, name: String): Boolean = {
        name.startsWith(geometryCacheStartsMatch.format(municipalityCode))
      }
    }).filter(f => f.lastModified() + twentyHours > System.currentTimeMillis))

    val cachedChangesFile = dir.map(cacheDir => cacheDir.listFiles(new FilenameFilter {
      override def accept(dir: File, name: String): Boolean = {
        name.startsWith(changeCacheStartsMatch.format(municipalityCode))
      }
    }).filter(f => f.lastModified() + twentyHours > System.currentTimeMillis))

    val cachedComplementaryFile = dir.map(cacheDir => cacheDir.listFiles(new FilenameFilter {
      override def accept(dir: File, name: String): Boolean = {
        name.startsWith(complementaryCacheStartsMatch.format(municipalityCode))
      }
    }).filter(f => f.lastModified() + twentyHours > System.currentTimeMillis))

    if (cachedGeometryFile.nonEmpty && cachedGeometryFile.get.nonEmpty && cachedGeometryFile.get.head.canRead &&
      cachedChangesFile.nonEmpty && cachedChangesFile.get.nonEmpty && cachedChangesFile.get.head.canRead &&
      cachedComplementaryFile.nonEmpty && cachedComplementaryFile.get.nonEmpty && cachedComplementaryFile.get.head.canRead){
      Some(cachedGeometryFile.get.head, cachedChangesFile.get.head, cachedComplementaryFile.get.head)
    } else {
      None
    }
  }

  private def getNodeCacheFiles(municipalityCode: Int, dir: Option[File]): Option[File] = {
    val twentyHours = 20L * 60 * 60 * 1000

    deleteOldNodeCacheFiles(municipalityCode, dir, twentyHours)

    val cachedGeometryFile = dir.map(cacheDir => cacheDir.listFiles(new FilenameFilter {
      override def accept(dir: File, name: String): Boolean = {
        name.startsWith(nodeCacheStartsMatch.format(municipalityCode))
      }
    }).filter(f => f.lastModified() + twentyHours > System.currentTimeMillis))

    if (cachedGeometryFile.nonEmpty && cachedGeometryFile.get.nonEmpty && cachedGeometryFile.get.head.canRead) {
      Some(cachedGeometryFile.get.head)
    } else {
      None
    }
  }

  //getRoadLinksFromVVHFuture expects to get only "normal" roadlinks from getCachedRoadLinksAndChanges  method.
  private def getCachedRoadLinksAndChanges(municipalityCode: Int): (Seq[RoadLink], Seq[ChangeInfo]) = {
    val (roadLinks, changes, _) = getCachedRoadLinks(municipalityCode)
    (roadLinks, changes)
  }

  private def getCachedRoadLinksWithComplementaryAndChanges(municipalityCode: Int): (Seq[RoadLink], Seq[ChangeInfo]) = {
    val (roadLinks, changes, complementaries) = getCachedRoadLinks(municipalityCode)
    (roadLinks ++ complementaries, changes)
  }

  private def getCachedRoadLinks(municipalityCode: Int): (Seq[RoadLink], Seq[ChangeInfo], Seq[RoadLink]) = {
    val dir = getCacheDirectory
    val cachedFiles = getCacheWithComplementaryFiles(municipalityCode, dir)
    cachedFiles match {
      case Some((geometryFile, changesFile, complementaryFile)) =>
        logger.info("Returning cached result")
        (vvhSerializer.readCachedGeometry(geometryFile), vvhSerializer.readCachedChanges(changesFile), vvhSerializer.readCachedGeometry(complementaryFile))
      case _ =>
        val (roadLinks, changes, complementaries) = reloadRoadLinksWithComplementaryAndChangesFromVVH(municipalityCode)
        if (dir.nonEmpty) {
          try {
            val newGeomFile = new File(dir.get, geometryCacheFileNames.format(municipalityCode, System.currentTimeMillis))
            if (vvhSerializer.writeCache(newGeomFile, roadLinks)) {
              logger.info("New cached file created: " + newGeomFile + " containing " + roadLinks.size + " items")
            } else {
              logger.error("Writing cached geom file failed!")
            }
            val newChangeFile = new File(dir.get, changeCacheFileNames.format(municipalityCode, System.currentTimeMillis))
            if (vvhSerializer.writeCache(newChangeFile, changes)) {
              logger.info("New cached file created: " + newChangeFile + " containing " + changes.size + " items")
            } else {
              logger.error("Writing cached changes file failed!")
            }
            val newComplementaryFile = new File(dir.get, complementaryCacheFileNames.format(municipalityCode, System.currentTimeMillis))
            if (vvhSerializer.writeCache(newComplementaryFile, complementaries)) {
              logger.info("New cached file created: " + newComplementaryFile + " containing " + complementaries.size + " items")
            } else {
              logger.error("Writing cached complementay file failed!")
            }
          } catch {
            case ex: Exception => logger.warn("Failed cache IO when writing:", ex)
          }
        }
        (roadLinks, changes, complementaries)
    }
  }

  private def getCachedRoadNodes(municipalityCode: Int): Seq[VVHRoadNodes] = {
    val dir = getCacheDirectory
    val cachedFiles = getNodeCacheFiles(municipalityCode, dir)
    cachedFiles match {
      case Some(nodeFile) =>
        logger.info("Returning cached result")
        vvhSerializer.readCachedNodes(nodeFile)
      case _ =>
        val roadNodes = reloadRoadNodesFromVVH(municipalityCode)
        if (dir.nonEmpty) {
          try {
            val newGeomFile = new File(dir.get, nodeCacheFileNames.format(municipalityCode, System.currentTimeMillis))
            if (vvhSerializer.writeCache(newGeomFile, roadNodes)) {
              logger.info("New cached file created: " + newGeomFile + " containing " + roadNodes.size + " items")
            } else {
              logger.error("Writing cached geom file failed!")
            }
          } catch {
            case ex: Exception => logger.warn("Failed cache IO when writing:", ex)
          }
        }
        roadNodes
    }
  }

  def clearCache() = {
    val dir = getCacheDirectory
    var cleared = 0
    dir.foreach(d => d.listFiles(new FilenameFilter {
      override def accept(dir: File, name: String): Boolean = {
        name.endsWith(allCacheEndsMatch)
      }
    }).foreach { f =>
      logger.info("Clearing cache: " + f.getAbsolutePath)
      f.delete()
      cleared = cleared + 1
    }
    )
    cleared
  }

  def getComplementaryRoadLinksFromVVH(bounds: BoundingRectangle, municipalities: Set[Int] = Set()): Seq[RoadLink] = {
    val vvhRoadLinks = Await.result(vvhClient.complementaryData.fetchByMunicipalitiesAndBoundsF(bounds, municipalities), atMost = Duration.create(1, TimeUnit.HOURS))
    withDynTransaction {
      (enrichRoadLinksFromVVH(vvhRoadLinks, Seq.empty[ChangeInfo]), Seq.empty[ChangeInfo])
    }._1
  }

  def getSuravageLinksFromVVHF(bounds: BoundingRectangle, municipalities: Set[Int] = Set()): Future[Seq[VVHRoadlink]] = {
    vvhClient.suravageData.fetchSuravageByMunicipalitiesAndBoundsF(bounds, municipalities)
  }

  def getSuravageLinksFromVVH(bounds: BoundingRectangle, municipalities: Set[Int] = Set()): Seq[VVHRoadlink] = {
    Await.result(getSuravageLinksFromVVHF(bounds, municipalities), atMost = Duration.create(1, TimeUnit.HOURS))
  }

  def fetchSuravageLinksByLinkIdsFromVVH(linkIdsToGet: Set[Long]): Seq[VVHRoadlink] = {
    Await.result(vvhClient.suravageData.fetchSuravageByLinkIdsF(linkIdsToGet), atMost = Duration.create(1, TimeUnit.HOURS))
  }

  def getComplementaryRoadLinksFromVVH(municipality: Int): Seq[RoadLink] = {
    val vvhRoadLinks = Await.result(vvhClient.complementaryData.fetchByMunicipalityF(municipality), Duration.create(1, TimeUnit.HOURS))
    withDynTransaction {
      (enrichRoadLinksFromVVH(vvhRoadLinks, Seq.empty[ChangeInfo]), Seq.empty[ChangeInfo])
    }._1
  }

  def getComplementaryRoadLinksFromVVHFuture(municipality: Int): Future [Seq[RoadLink]] = {
    Future(withDynTransaction {
      (enrichRoadLinksFromVVH(Await.result(vvhClient.complementaryData.fetchByMunicipalityF(municipality), Duration.create(1, TimeUnit.HOURS)), Seq.empty[ChangeInfo]), Seq.empty[ChangeInfo])
    }._1)
  }

  def getViiteCurrentAndComplementaryRoadLinksFromVVH(municipality: Int, roadNumbers: Seq[(Int, Int)], frozenTimeVVHAPIServiceEnabled: Boolean = false): Seq[RoadLink] = {
    val complementaryF = vvhClient.complementaryData.fetchByMunicipalityAndRoadNumbersF(municipality, roadNumbers)
    val currentF = if (frozenTimeVVHAPIServiceEnabled) vvhClient.frozenTimeRoadLinkData.fetchByMunicipalityAndRoadNumbersF(municipality, roadNumbers) else vvhClient.roadLinkData.fetchByMunicipalityAndRoadNumbersF(municipality, roadNumbers)
    val (compLinks, vvhRoadLinks) = Await.result(complementaryF.zip(currentF), atMost = Duration.create(1, TimeUnit.HOURS))
    (enrichRoadLinksFromVVH(compLinks ++ vvhRoadLinks, Seq.empty[ChangeInfo]), Seq.empty[ChangeInfo])._1
  }

  def getCurrentAndComplementaryVVHRoadLinks(linkIds: Set[Long], frozenTimeVVHAPIServiceEnabled : Boolean = false): Seq[VVHRoadlink] = {
    val roadLinks= if(frozenTimeVVHAPIServiceEnabled) vvhClient.frozenTimeRoadLinkData.fetchByLinkIds(linkIds) else vvhClient.roadLinkData.fetchByLinkIds(linkIds)
    vvhClient.complementaryData.fetchByLinkIds(linkIds) ++ roadLinks
  }

  def getCurrentAndComplementaryRoadLinksFromVVH(linkIds: Set[Long], newTransaction: Boolean = true, frozenTimeVVHAPIServiceEnabled : Boolean = false ): Seq[RoadLink] = {
    val roadLinks= if(frozenTimeVVHAPIServiceEnabled) vvhClient.frozenTimeRoadLinkData.fetchByLinkIds(linkIds) else vvhClient.roadLinkData.fetchByLinkIds(linkIds)
    val roadLinksVVH = vvhClient.complementaryData.fetchByLinkIds(linkIds) ++ roadLinks
    if (newTransaction)
      withDynTransaction {
        enrichRoadLinksFromVVH(roadLinksVVH)
      }
    else
      enrichRoadLinksFromVVH(roadLinksVVH)
  }

  def withRoadAddress(roadLinks: Seq[RoadLink]): Seq[RoadLink] = {
    val addressData = getRoadAddressesByLinkIds(roadLinks.map(_.linkId).toSet).map(a => (a.linkId, a)).toMap
    roadLinks.map(rl =>
      if (addressData.contains(rl.linkId))
        rl.copy(attributes = rl.attributes ++ addressData(rl.linkId).asAttributes)
      else
        rl
    )
  }

  case class RoadLinkRoadAddress(linkId: Long, roadNumber: Long, roadPartNumber: Long, track: Track, sideCode: SideCode,
                                 startAddrMValue: Long, endAddrMValue: Long) {
    def asAttributes: Map[String, Any] = Map("VIITE_ROAD_NUMBER" -> roadNumber,
      "VIITE_ROAD_PART_NUMBER" -> roadPartNumber,
      "VIITE_TRACK" -> track.value,
      "VIITE_SIDECODE" -> sideCode.value,
      "VIITE_START_ADDR" -> startAddrMValue,
      "VIITE_END_ADDR" -> endAddrMValue)
  }

  /**
    * This method fetches road addresses by link ids from Viite ROAD_ADDRESS table using LRM_POSITION
    *
    * @param linkIds
    * @return
    */
  def getRoadAddressesByLinkIds(linkIds: Set[Long]): Seq[RoadLinkRoadAddress] = {
    withDynTransaction {
      getRoadAddressesInTX(linkIds)
    }
  }

  private def getRoadAddressesInTX(linkIds: Set[Long]): Seq[RoadLinkRoadAddress] = {
    MassQuery.withIds(linkIds) {
      idTableName =>
        val query = s"""
            SELECT pos.LINK_ID, ra.ROAD_NUMBER, ra.ROAD_PART_NUMBER, ra.TRACK_CODE, pos.SIDE_CODE,
              MIN(ra.START_ADDR_M), MAX(ra.END_ADDR_M)
            FROM ROAD_ADDRESS ra
            JOIN LRM_POSITION pos on ra.LRM_POSITION_ID = pos.ID
            JOIN $idTableName i on i.ID = pos.LINK_ID
            WHERE (VALID_FROM IS NULL OR VALID_FROM <= sysdate) AND
              (VALID_TO IS NULL OR VALID_TO >= sysdate) AND
              (START_DATE IS NULL OR START_DATE <= sysdate) AND
              (END_DATE IS NULL OR END_DATE >= sysdate)
            GROUP BY LINK_ID, ROAD_NUMBER, ROAD_PART_NUMBER, TRACK_CODE, SIDE_CODE"""
        Q.queryNA[(Long, Long, Long, Int, Int, Long, Long)](query).list.map {
          case (id, roadNumber, roadPartNumber, track, sideCode, startAddrMValue, endAddrMValue)
          => RoadLinkRoadAddress(id, roadNumber, roadPartNumber, Track.apply(track), SideCode.apply(sideCode),
            startAddrMValue, endAddrMValue)
        }
    }
  }

  /**
    * This method returns Road Link that have been changed in VVH between given dates values. It is used by TN-ITS ChangeApi.
    *
    * @param sinceDate
    * @param untilDate
    * @return Changed Road Links between given dates
    */
  def getChanged(sinceDate: DateTime, untilDate: DateTime): Seq[ChangedVVHRoadlink] = {
    val municipalitiesCodeToValidate = List(35, 43, 60, 62, 65, 76, 170, 295, 318, 417, 438, 478, 736, 766, 771, 941)
    val timezone = DateTimeZone.forOffsetHours(0)

    val roadLinks =
      withDynTransaction {
        enrichRoadLinksFromVVH(getRoadLinksBetweenTwoDatesFromVVH(sinceDate, untilDate))
      }

    roadLinks.map { roadLink =>
      ChangedVVHRoadlink(
        link = roadLink,
        value =
          if (municipalitiesCodeToValidate.contains(roadLink.municipalityCode)) {
            roadLink.attributes.getOrElse("ROADNAME_SE", "").toString
          } else {
            roadLink.attributes.getOrElse("ROADNAME_FI", "").toString
          },
        createdAt = roadLink.attributes.get("CREATED_DATE") match {
          case Some(date) => Some(new DateTime(date.asInstanceOf[BigInt].toLong, timezone))
          case _ => None
        },
        changeType = "Modify"
      )
    }
  }

}
