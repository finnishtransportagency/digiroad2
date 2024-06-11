package fi.liikennevirasto.digiroad2.service

import com.github.tototoshi.slick.MySQLJodaSupport._
import com.vividsolutions.jts.geom.Polygon
import fi.liikennevirasto.digiroad2.GeometryUtils._
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.DateParser._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client._
import fi.liikennevirasto.digiroad2.dao.RoadLinkOverrideDAO.{AdministrativeClassDao, FunctionalClassDao, IncompleteLinkDao, LinkAttributesDao, LinkTypeDao, TrafficDirectionDao}
import fi.liikennevirasto.digiroad2.dao.{ComplementaryLinkDAO, RoadLinkDAO, RoadLinkOverrideDAO}
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike, RoadLinkProperties, TinyRoadLink}
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase.withDbConnection
import fi.liikennevirasto.digiroad2.postgis.{MassQuery, PostGISDatabase}
import fi.liikennevirasto.digiroad2.service.linearasset.AssetUpdateActor
import fi.liikennevirasto.digiroad2.user.User
import fi.liikennevirasto.digiroad2.util._
import org.joda.time.format.{DateTimeFormat, ISODateTimeFormat}
import org.joda.time.{DateTime, DateTimeZone}
import org.slf4j.LoggerFactory
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedResult}

import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

case class IncompleteLink(linkId: String, municipalityCode: Int, administrativeClass: AdministrativeClass)
case class RoadLinkSet(link: RoadLink, itNext: Option[RoadLink], itPrevious: Option[RoadLink])
case class ChangedRoadlink(link: RoadLink, value: String, createdAt: Option[DateTime], changeType: String /*TODO create and use ChangeType case object*/)
case class LinkProperties(linkId: String, functionalClass: Int, linkType: LinkType, trafficDirection: TrafficDirection,
                          administrativeClass: AdministrativeClass, privateRoadAssociation: Option[String] = None, additionalInfo: Option[AdditionalInformation] = None,
                          accessRightID: Option[String] = None)
case class PrivateRoadAssociation(name: String, roadName: String, municipality: String, linkId: String)
case class RoadLinkAttributeInfo(id: Long, linkId: Option[String], name: Option[String], value: Option[String], createdDate: Option[DateTime], createdBy: Option[String], modifiedDate: Option[DateTime], modifiedBy: Option[String])
case class LinkPropertiesEntries(propertyName: String, linkProperty: LinkProperties, username: Option[String],
                                 roadLinkFetched: RoadLinkFetched, latestModifiedAt: Option[String],
                                 latestModifiedBy: Option[String],mmlId: Option[Long])
case class LinkPropertyChange(propertyName: String, optionalExistingValue: Option[Int], linkProperty: LinkProperties,
                              roadLinkFetched: RoadLinkFetched, username: Option[String])


case class RoadLinkWithExpiredDate(roadLink: RoadLinkFetched, expiredDate: DateTime)

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

sealed trait AdditionalInformation {
  def value: String
  def label: String
}

object AdditionalInformation{
  val values = Set(DeliveredWithRestrictions, DeliveredWithoutRestrictions, NotDelivered)

  def apply(stringValue: String): AdditionalInformation = {
    values.find(_.value == stringValue).getOrElse(NotDelivered)
  }

  case object NotDelivered extends AdditionalInformation { def value = "99"; def label = "Ei toimitettu"; }
  case object DeliveredWithRestrictions extends AdditionalInformation { def value = "1"; def label = "Tieto toimitettu, rajoituksia"; }
  case object DeliveredWithoutRestrictions extends AdditionalInformation { def value = "2"; def label = "Tieto toimitettu, ei rajoituksia"; }
}

case class PrivateRoadInfoStructure(privateRoadName: Option[String], associationId: Option[String], additionalInfo: Option[String], lastModifiedDate: Option[String])

/**
  * This class performs operations related to road links.
  *
  * @param roadLinkClient
  * @param eventbus
  */
class RoadLinkService(val roadLinkClient: RoadLinkClient, val eventbus: DigiroadEventBus) {
  lazy val municipalityService = new MunicipalityService

  protected def roadLinkDAO: RoadLinkDAO = new RoadLinkDAO
  protected def complementaryLinkDAO: ComplementaryLinkDAO = new ComplementaryLinkDAO
  
  val logger = LoggerFactory.getLogger(getClass)
  
  //Attributes names used on table "road_link_attributes"
  val privateRoadAssociationPublicId = "PRIVATE_ROAD_ASSOCIATION"
  val additionalInfoPublicId = "ADDITIONAL_INFO"
  val accessRightIDPublicId = "ACCESS_RIGHT_ID"
  val privateLastModifiedDatePublicId = "PRIVATE_ROAD_LAST_MOD_DATE"
  val privateLastModifiedUserPublicId = "PRIVATE_ROAD_LAST_MOD_USER"

  //No road name found
  val roadWithoutName = "tuntematon tienimi"

  def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)
  def withDynSession[T](f: => T): T = PostGISDatabase.withDynSession(f)

  implicit def dateTimeOrdering: Ordering[DateTime] = Ordering.fromLessThan(_ isAfter  _)
  implicit val getDateTime = new GetResult[DateTime] {
    def apply(r: PositionedResult) = {
      new DateTime(r.nextTimestamp())
    }
  }
  implicit val getRoadAttributeInfo = new GetResult[RoadLinkAttributeInfo] {
    def apply(r: PositionedResult) = {
      val id = r.nextLong()
      val linkId = r.nextStringOption()
      val name = r.nextStringOption()
      val value = r.nextStringOption()
      val createdDate = r.nextTimestampOption().map(new DateTime(_))
      val createdBy = r.nextStringOption()
      val modifiedDate = r.nextTimestampOption().map(new DateTime(_))
      val modifiedBy = r.nextStringOption()

      RoadLinkAttributeInfo(id, linkId, name, value, createdDate, createdBy, modifiedDate, modifiedBy)
    }
  }


  def getRoadLinkAndComplementaryByLinkId(linkId: String, newTransaction: Boolean = true): Option[RoadLink] = getRoadLinksAndComplementariesByLinkIds(Set(linkId), newTransaction: Boolean).headOption

  def getRoadLinksAndComplementariesByLinkIds(linkIds: Set[String], newTransaction: Boolean = true): Seq[RoadLink] = {
    val fetchedRoadLinks = fetchRoadlinksAndComplementaries(linkIds)
    if (newTransaction)
      withDynTransaction {
        enrichFetchedRoadLinks(fetchedRoadLinks)
      }
    else
      enrichFetchedRoadLinks(fetchedRoadLinks)
  }

  /**
    * ATTENTION Use this method always with transaction, never with session,
    * Returns the road links from database by municipality.
    *
    * @param municipality A integer, representative of the municipality Id.
    */
  def getRoadLinksByMunicipality(municipality: Int, newTransaction: Boolean = true): Seq[RoadLink] = {
    val fetchedRoadLinks = withDbConnection {roadLinkDAO.fetchByMunicipality(municipality)}
    if (newTransaction)
      withDynTransaction {
        enrichFetchedRoadLinks(fetchedRoadLinks)
      }
    else
      enrichFetchedRoadLinks(fetchedRoadLinks)
  }

  /**
    * Returns the road link ids from database by municipality.
   *
    * @param municipality A integer, representative of the municipality Id.
    */
  def getRoadLinksIdsByMunicipality(municipality: Int): Seq[String] = {
    val fetchedRoadLinks = withDbConnection {roadLinkDAO.fetchByMunicipality(municipality)}
    fetchedRoadLinks.map(_.linkId)
  }

  def getRoadLinksWithComplementaryByMunicipality(municipality: Int, newTransaction: Boolean = true): (Seq[RoadLink]) = {
    val (fetchedRoadLinks,fetchedComplementaryLinks) = withDbConnection{
      (roadLinkDAO.fetchByMunicipality(municipality),complementaryLinkDAO.fetchByMunicipality(municipality))
    }

    if (newTransaction)
      withDynTransaction {
        (enrichFetchedRoadLinks(fetchedRoadLinks ++ fetchedComplementaryLinks))
      }
    else
      (enrichFetchedRoadLinks(fetchedRoadLinks ++ fetchedComplementaryLinks))
  }


  /**
    * ATTENTION: Use this method always with transaction, never with session.
    * This method returns road links by link ids.
    *
    * @param linkIds
    * @return Road links
    */
  def getRoadLinksByLinkIds(linkIds: Set[String], newTransaction: Boolean = true): Seq[RoadLink] = {
    val fetchedRoadLinks = fetchRoadlinksByIds(linkIds)
    if (newTransaction)
      withDynTransaction {
        enrichFetchedRoadLinks(fetchedRoadLinks)
      }
    else
      enrichFetchedRoadLinks(fetchedRoadLinks)
  }

  def getRoadLinkByLinkId(linkId: String, newTransaction: Boolean = true): Option[RoadLink] = getRoadLinksByLinkIds(Set(linkId), newTransaction: Boolean).headOption

  /**
   * This method returns existing and expired road links by link ids. Used by samuutus.
   * ATTENTION: Use this method always with transaction, never with session.
   */
  def getExistingAndExpiredRoadLinksByLinkIds(linkIds: Set[String], newTransaction: Boolean = true): Seq[RoadLink] = {
    def getLinks: Seq[RoadLinkFetched] = {
      val nonExpiredLinks = fetchRoadlinksByIds(linkIds)
      val missingLinkIds = linkIds.diff(nonExpiredLinks.map(_.linkId).toSet)

      if (missingLinkIds.nonEmpty)
        nonExpiredLinks ++ roadLinkDAO.fetchExpiredByLinkIds(linkIds)
      else
        nonExpiredLinks
    }

    if (newTransaction) withDynTransaction ( enrichFetchedRoadLinks(getLinks) )
    else enrichFetchedRoadLinks(getLinks)
  }

  def getExistingOrExpiredRoadLinkByLinkId(linkId: String, newTransaction: Boolean = true): Option[RoadLink] =
    getExistingAndExpiredRoadLinksByLinkIds(Set(linkId), newTransaction).headOption

  def getExpiredRoadLinkByLinkId(linkId: String, newTransaction: Boolean = true): Option[RoadLink] = {
    val fetchedRoadLinks = roadLinkDAO.fetchExpiredRoadLink(linkId)
    if (newTransaction)
      withDynTransaction {
        enrichFetchedRoadLinks(fetchedRoadLinks).headOption
      }
    else
      enrichFetchedRoadLinks(fetchedRoadLinks).headOption
  }

  def getExpiredRoadLinkByLinkIdNonEncrished(linkId: String, newTransaction: Boolean = true): Option[RoadLinkFetched] = {
   roadLinkDAO.fetchExpiredRoadLink(linkId).headOption
  }

  def getAllExpiredRoadLinksWithExpiredDates(): Seq[RoadLinkWithExpiredDate]= {
    val fetchedExpiredLinks = roadLinkDAO.fetchExpiredRoadLinks()
    val expiredDates = roadLinkDAO.getRoadLinkExpiredDateWithLinkIds(fetchedExpiredLinks.map(_.linkId).toSet)
    fetchedExpiredLinks.map(roadLink => {
      val expiredDate = expiredDates.find(_.linkId == roadLink.linkId).get
      RoadLinkWithExpiredDate(roadLink, expiredDate.expiredDate)
    })
  }

  /**
    * This method returns road links that have been changed within a specified time period.
    *
    * @param since
    * @param until
    * @return Road links
    */
  def getRoadLinksByChangesWithinTimePeriod(since: DateTime, until: DateTime, newTransaction: Boolean = true): Seq[RoadLinkFetched] = {
    if ((since != null) || (until != null))withDbConnection { roadLinkDAO.fetchByChangesDates(since, until)}
    else Seq.empty[RoadLinkFetched]
  }


  /**
    * This method returns road links by municipality.
    *
    * @param municipality
    * @return Road links
    */
  def getRoadLinksByMunicipalityUsingCache(municipality: Int): Seq[RoadLink] = {
    LogUtils.time(logger,"Get roadlink with cache") {
      val (roadLinks,_) = getCachedRoadLinks(municipality)
      (roadLinks)
    }
  }

  def getRoadLinksWithComplementaryByMunicipalityUsingCache(municipality: Int): Seq[RoadLink] = {
    LogUtils.time(logger,"Get roadlink with cache") {
      val (roadLinks, complementary) = getCachedRoadLinks(municipality)
      (roadLinks ++ complementary)
    }
  }

  def getTinyRoadLinksByMunicipality(municipality: Int): Seq[TinyRoadLink] = {
    val (roadLinks, complementaryRoadLink) = getCachedRoadLinks(municipality)
    (roadLinks ++ complementaryRoadLink).map { roadLink =>
      TinyRoadLink(roadLink.linkId)
    }
  }

  /**
    * This method returns road links by bounding box and municipalities.
    *
    * @param bounds
    * @param municipalities
    * @return Road links
    */
  def getRoadLinksByBoundsAndMunicipalities(bounds: BoundingRectangle, municipalities: Set[Int] = Set(), asyncMode: Boolean = true) : Seq[RoadLink] =
    getRoadLinks(bounds, municipalities,asyncMode)

  /**
    * This method returns "real" road links and "complementary" road links by bounding box and municipalities.
    *
    * @param bounds
    * @param municipalities
    * @return Road links
    */
  def getRoadLinksWithComplementaryByBoundsAndMunicipalities(bounds: BoundingRectangle, municipalities: Set[Int] = Set(), newTransaction: Boolean = true, asyncMode: Boolean = true) : Seq[RoadLink] =
    getRoadLinksWithComplementary(bounds, municipalities, newTransaction,asyncMode)

  def getRoadLinksByBounds(bounds: BoundingRectangle, bounds2: BoundingRectangle, newTransaction: Boolean = true) : Seq[RoadLink] = {
    val (links, links2) = withDbConnection {
      (roadLinkDAO.fetchByMunicipalitiesAndBounds(bounds, Set()), roadLinkDAO.fetchByMunicipalitiesAndBounds(bounds2, Set()))
    }

    if (newTransaction)
      withDynTransaction {
        enrichFetchedRoadLinks(links ++ links2)
      }
    else
      enrichFetchedRoadLinks(links ++ links2)
  }
   

  /**
    * This method returns road links from database by link ids.
    *
    * @param linkIds
    * @return RoadLinkFetched
    */
  def fetchRoadlinksByIds(linkIds: Set[String]): Seq[RoadLinkFetched] = {
    if (linkIds.nonEmpty) {withDbConnection {roadLinkDAO.fetchByLinkIds(linkIds) }}
    else Seq.empty[RoadLinkFetched]
  }

  def getAllPrivateRoadAssociationNames(): Seq[String] = {
    withDynSession {
      LinkAttributesDao.getAllExistingDistinctValues(privateRoadAssociationPublicId)
    }
  }

  def getPrivateRoadsByAssociationName(roadAssociationName: String, newTransaction: Boolean = true): Seq[PrivateRoadAssociation] = {
    val roadNamesPerLinkId = getValuesByRoadAssociationName(roadAssociationName, privateRoadAssociationPublicId, newTransaction)
    val roadLinks = getRoadLinksAndComplementaryByLinkIds(roadNamesPerLinkId.map(_._2).toSet, newTransaction)

    val municipalityCodes = roadLinks.map(_.municipalityCode).toSet
    val municipalitiesInfo = municipalityService.getMunicipalitiesNameAndIdByCode(municipalityCodes, newTransaction)
    val groupedByNameRoadLinks = roadLinks.groupBy(_.municipalityCode).values.map(_.groupBy(_.roadNameIdentifier))

    groupedByNameRoadLinks.flatMap { municipalityRoadLinks =>
      municipalityRoadLinks.map { roadLinkAssociation =>
        val roadName = if(roadLinkAssociation._1.getOrElse(" ").trim.isEmpty) roadWithoutName else roadLinkAssociation._1.get
        val maxLengthRoadLink = roadLinkAssociation._2.maxBy(_.length)
        val municipalityName = municipalitiesInfo.find(_.id == maxLengthRoadLink.municipalityCode).head.name
        PrivateRoadAssociation(roadNamesPerLinkId.head._1, roadName, municipalityName, maxLengthRoadLink.linkId)
      }
    }.toSeq
  }

  def getPrivateRoadsInfoByMunicipality(municipalityCode: Int): Seq[PrivateRoadInfoStructure] = {
    val cachedRoadLinks = getTinyRoadLinksByMunicipality(municipalityCode).map(_.linkId).toSet
    val results = getPrivateRoadsInfoByLinkIds(cachedRoadLinks)
    groupPrivateRoadInformation(results)
  }

  def getPrivateRoadsInfoByLinkIds(linkIds: Set[String]): List[(String, Option[(String, String)])] = {
    withDynTransaction {
      MassQuery.withStringIds(linkIds) { idTableName =>
        fetchOverridedRoadLinkAttributes(idTableName)
      }
    }
  }

  def getValuesByRoadAssociationName(roadAssociationName: String, roadAssociationPublicId: String, newTransaction: Boolean = true): List[(String, String)] = {
    if(newTransaction)
      withDynSession {
        LinkAttributesDao.getValuesByRoadAssociationName(roadAssociationName, privateRoadAssociationPublicId)
      }
    else
      LinkAttributesDao.getValuesByRoadAssociationName(roadAssociationName, privateRoadAssociationPublicId)
  }

  def fetchRoadlinksAndComplementaries(linkIds: Set[String]): Seq[RoadLinkFetched] = {
    if (linkIds.nonEmpty) withDbConnection {roadLinkDAO.fetchByLinkIds(linkIds) ++ complementaryLinkDAO.fetchByLinkIds(linkIds)}
    else Seq.empty[RoadLinkFetched]
  }

  def fetchRoadlinkAndComplementary(linkId: String): Option[RoadLinkFetched] = fetchRoadlinksAndComplementaries(Set(linkId)).headOption


  /**
    * This method returns road links from database by bounding box and municipalities. Used for finding the closest road link of a geometry point.
    *
    * @param bounds
    * @param municipalities
    * @return RoadLinkFetched
    */
  def fetchRoadLinksByBoundsAndMunicipalities(bounds: BoundingRectangle, municipalities: Set[Int] = Set(), includeComplementaries: Boolean = false): Seq[RoadLinkFetched] = {
    if (includeComplementaries) withDbConnection{roadLinkDAO.fetchByMunicipalitiesAndBounds(bounds, municipalities) ++ complementaryLinkDAO.fetchByMunicipalitiesAndBounds(bounds, municipalities)}
    else withDbConnection{roadLinkDAO.fetchByMunicipalitiesAndBounds(bounds, municipalities)}
  }

  /**
    * This method returns road links and change data by bounding box and municipalities.
    *
    * @param bounds
    * @param municipalities
    * @return Road links and change data
    */
  def getRoadLinks(bounds: BoundingRectangle, municipalities: Set[Int] = Set(), asyncMode: Boolean = true): (Seq[RoadLink]) = {
    
    val links = withDbConnection {roadLinkDAO.fetchByMunicipalitiesAndBounds(bounds, municipalities)}
    withDynTransaction {
      LogUtils.time(logger, "TEST LOG enrichFetchedRoadLinksFrom from boundingBox request, link count: " + links.size)(
        enrichFetchedRoadLinks(links)
      )
    }
  }

/*  def getRoadLinksAndChangesWithPolygon(polygon :Polygon): (Seq[RoadLink], Seq[ChangeInfo])= {
    val changes = Await.result(roadLinkClient.roadLinkChangeInfo.fetchByPolygonF(polygon), atMost = Duration.Inf)
    val links = withDbConnection {roadLinkDAO.fetchByPolygon(polygon)}
    withDynTransaction {
      (enrichFetchedRoadLinks(links), changes)
    }
  }*/

  /**
    * This method returns "real" and "complementary" link id by polygons.
    *
    * @param polygons
    * @return LinksId
    */

  def getLinkIdsWithComplementaryByPolygons(polygons: Seq[Polygon]) = {
    Await.result(Future.sequence(polygons.map(getLinkIdsWithComplementaryByPolygonF)), Duration.Inf).flatten
  }

  /**
    * This method returns "real" and "complementary" link id by polygon.
    *
    * @param polygon
    * @return seq(LinksId) , seq(LinksId)
    */
  def getLinkIdsWithComplementaryByPolygon(polygon :Polygon): Seq[String] = {
    val complementaryResult = withDbConnection {roadLinkDAO.fetchLinkIdsByPolygon(polygon)}
    val result = withDbConnection { complementaryLinkDAO.fetchLinkIdsByPolygon(polygon)}
    complementaryResult ++ result
  }

  def getLinkIdsWithComplementaryByPolygonF(polygon :Polygon): Future[Seq[String]] = {
    Future(getLinkIdsWithComplementaryByPolygon(polygon))
  }


  def fetchByMunicipality(municipality: Int): Seq[RoadLinkFetched] = {
    withDbConnection { roadLinkDAO.fetchByMunicipality(municipality)}
  }

  def fetchByBounds(bounds: BoundingRectangle): Seq[RoadLinkFetched] = {
    withDbConnection {roadLinkDAO.fetchByBounds(bounds)}
  }

  def fetchNormalOrComplimentaryRoadLinkByLinkId(linkId: String): Option[RoadLinkFetched] = {
    withDbConnection {
      roadLinkDAO.fetchByLinkId(linkId) match {
        case Some(fetchedRoadLink) => Some(fetchedRoadLink)
        case None => complementaryLinkDAO.fetchByLinkId(linkId)
      }
    }
  }

  def fetchByLinkId(linkId: String): Option[RoadLinkFetched] = {
    withDbConnection { roadLinkDAO.fetchByLinkId(linkId)}
  }
  def fetchComplimentaryByLinkId(linkId: String): Option[RoadLinkFetched] = {
    withDbConnection { complementaryLinkDAO.fetchByLinkId(linkId)}
  }

  /**
    * This method returns "real" road links, "complementary" road links and change data by bounding box and municipalities.
    *
    * @param bounds
    * @param municipalities
    * @return Road links and change data
    */
  def getRoadLinksWithComplementary(bounds: BoundingRectangle, municipalities: Set[Int] = Set(), newTransaction:Boolean = true, asyncMode: Boolean = true): (Seq[RoadLink])= {
    val  (complementaryLinks, links) = withDbConnection {
      (complementaryLinkDAO.fetchWalkwaysByBoundsAndMunicipalities(bounds, municipalities),
        roadLinkDAO.fetchByMunicipalitiesAndBounds(bounds, municipalities))
    }
    if(newTransaction){
      withDynTransaction {
        (enrichFetchedRoadLinks(links ++ complementaryLinks))
      }
    }
    else (enrichFetchedRoadLinks(links ++ complementaryLinks))

  }

  def getRoadLinksAndComplementaryByLinkIds(linkIds: Set[String], newTransaction:Boolean = true): Seq[RoadLink] = {
    val  (complementaryLinks, links) = withDbConnection {
      (complementaryLinkDAO.fetchByLinkIds(linkIds),roadLinkDAO.fetchByLinkIds(linkIds))
    }
 
    if(newTransaction){
      withDynTransaction {
        enrichFetchedRoadLinks(links ++ complementaryLinks)
      }
    }
    else enrichFetchedRoadLinks(links ++ complementaryLinks)
  }

  def getRoadLinksAndComplementaryByRoadName(roadNamePublicIds: String, roadNameSource: Set[String], newTransaction: Boolean = true): Seq[RoadLink] = {
  val  (complementaryLinks, links) = withDbConnection {
    (complementaryLinkDAO.fetchByRoadNames(roadNamePublicIds, roadNameSource),
      roadLinkDAO.fetchByRoadNames(roadNamePublicIds, roadNameSource))
  }
    if(newTransaction){
      withDynTransaction {
        enrichFetchedRoadLinks(links ++ complementaryLinks)
      }
    }
    else enrichFetchedRoadLinks(links ++ complementaryLinks)
  }

  private def reloadRoadLinksWithComplementary(municipalities: Int, newTransaction: Boolean = true): (Seq[RoadLink], Seq[RoadLink]) = {
    val  (complementaryLinks, links) = withDbConnection {
      (complementaryLinkDAO.fetchWalkwaysByMunicipalities(municipalities),roadLinkDAO.fetchByMunicipality(municipalities))
    }
    if (newTransaction) {
      withDynTransaction {
        (enrichFetchedRoadLinks(links), enrichFetchedRoadLinks(complementaryLinks))
      }
    } else {
      (enrichFetchedRoadLinks(links), enrichFetchedRoadLinks(complementaryLinks))
    }
  }

  def getRoadLinksWithComplementary(municipality: Int): Seq[RoadLink]= {
    LogUtils.time(logger,"Get roadlinks with cache"){
    val (roadLinks, complementaries) = getCachedRoadLinks(municipality)
    (roadLinks ++ complementaries)
    }
  }

  /**
    * Returns incomplete links by municipalities (Incomplete link = road link with no functional class and link type saved in OTH).
    * Used by Digiroad2Api /roadLinks/incomplete GET endpoint.
    */
  def getIncompleteLinks(includedMunicipalities: Option[Set[Int]], newSession: Boolean = true): Map[String, Map[String, Seq[String]]] = {
    if (newSession) {
      withDynSession(IncompleteLinkDao.getIncompleteLinks(includedMunicipalities))
    } else {
      IncompleteLinkDao.getIncompleteLinks(includedMunicipalities)
    }
  }

  /**
    * Returns road link middle point by link id. Used to select a road link by url to be shown on map (for example: index.html#linkProperty/12345).
    * Used by Digiroad2Api /roadlinks/:linkId GET endpoint.
    *
    */
  def getRoadLinkMiddlePointByLinkId(linkId: String): Option[(String, Point, LinkGeomSource)] = {
    val middlePoint: Option[Point] = withDbConnection { roadLinkDAO.fetchByLinkId(linkId)}
      .flatMap { fetchedRoadLink =>
        GeometryUtils.calculatePointFromLinearReference(fetchedRoadLink.geometry, GeometryUtils.geometryLength(fetchedRoadLink.geometry) / 2.0)
      }
    middlePoint.map((linkId, _, LinkGeomSource.NormalLinkInterface)).orElse(getComplementaryLinkMiddlePointByLinkId(linkId).map((linkId, _, LinkGeomSource.ComplimentaryLinkInterface)))
  }

  def getComplementaryLinkMiddlePointByLinkId(linkId: String): Option[Point] = {
    val middlePoint: Option[Point] = withDbConnection { complementaryLinkDAO.fetchByLinkIds(Set(linkId)).headOption}
      .flatMap { fetchedRoadLink =>
        GeometryUtils.calculatePointFromLinearReference(fetchedRoadLink.geometry, GeometryUtils.geometryLength(fetchedRoadLink.geometry) / 2.0)
      }
    middlePoint
  }

  /**
    * Returns road link middle point by mml id. Used to select a road link by url to be shown on map (for example: index.html#linkProperty/mml/12345).
    * Used by Digiroad2Api /roadlinks/mml/:mmlId GET endpoint.
    *
    */
  def getRoadLinkMiddlePointByMmlId(mmlId: Long): Option[(String, Point)] = {
    withDbConnection {
      roadLinkDAO.fetchByMmlId(mmlId)
    }.flatMap { fetchedRoadLink =>
      val point = GeometryUtils.calculatePointFromLinearReference(fetchedRoadLink.geometry, GeometryUtils.geometryLength(fetchedRoadLink.geometry) / 2.0)
      point match {
        case Some(point) => Some(fetchedRoadLink.linkId, point)
        case None => None
      }
    }
  }
  def checkMMLId(roadLinkFetched: RoadLinkFetched) : Option[Long] = {
    roadLinkFetched.attributes.contains("MTKID") match {
      case true => Some(roadLinkFetched.attributes("MTKID").asInstanceOf[Long])
      case false => None
    }
  }

  /**
    * Saves road link property data from UI.
    */
  def updateLinkProperties(linkProperty: LinkProperties, username: Option[String], municipalityValidation: (Int, AdministrativeClass) => Unit, isOperator: Boolean = false): Option[RoadLink] = {
    val fetchedRoadLink = withDbConnection { roadLinkDAO.fetchByLinkId(linkProperty.linkId) match {
      case Some(fetchedRoadLink) => Some(fetchedRoadLink)
      case None => complementaryLinkDAO.fetchByLinkId(linkProperty.linkId)
    }}
    fetchedRoadLink.map { fetchedRoadLink =>
      municipalityValidation(fetchedRoadLink.municipalityCode, fetchedRoadLink.administrativeClass)
      withDynTransaction {
        setLinkProperty(RoadLinkOverrideDAO.TrafficDirection, linkProperty, username, fetchedRoadLink, None, None)
        if (linkProperty.functionalClass != UnknownFunctionalClass.value) setLinkProperty(RoadLinkOverrideDAO.FunctionalClass, linkProperty, username, fetchedRoadLink, None, None)
        if (linkProperty.linkType != UnknownLinkType) setLinkProperty(RoadLinkOverrideDAO.LinkType, linkProperty, username, fetchedRoadLink, None, None)
        if ((linkProperty.administrativeClass != State && fetchedRoadLink.administrativeClass != State) || isOperator) setLinkProperty(RoadLinkOverrideDAO.AdministrativeClass, linkProperty, username, fetchedRoadLink, None, None)
        if (linkProperty.administrativeClass == Private) setLinkAttributes(RoadLinkOverrideDAO.LinkAttributes, linkProperty, username, fetchedRoadLink, None, None)
        else LinkAttributesDao.expireValues(linkProperty.linkId, username)
        val enrichedLink = enrichFetchedRoadLinks(Seq(fetchedRoadLink)).head
        if (enrichedLink.functionalClass != UnknownFunctionalClass.value && enrichedLink.linkType != UnknownLinkType) {
          removeIncompleteness(linkProperty.linkId)
        }
        enrichedLink
      }
    }
  }
  
  def updateSideCodes(roadLinks:Seq[RoadLinkLike] ): Unit = {
    AssetTypeInfo.linearAssets.foreach(a=> {
      eventbus.publish("linearAssetUpdater",AssetUpdateActor(roadLinks.map(_.linkId).toSet,a.typeId,roadLinkUpdate = true))
    })
  }


  protected def setLinkProperty(propertyName: String, linkProperty: LinkProperties, username: Option[String],
                                roadLinkFetched: RoadLinkFetched, latestModifiedAt: Option[String],
                                latestModifiedBy: Option[String]) = {
    val optionalExistingValue: Option[Int] = RoadLinkOverrideDAO.get(propertyName, linkProperty.linkId)
    (optionalExistingValue, RoadLinkOverrideDAO.getMasterDataValue(propertyName, roadLinkFetched)) match {
      case (Some(existingValue), _) =>
        eventbus.publish("roadLinkProperty:changed", LinkPropertyChange(propertyName, optionalExistingValue, linkProperty, roadLinkFetched, username))
        updateSideCodes(Seq(roadLinkFetched))
        RoadLinkOverrideDAO.update(propertyName, linkProperty, roadLinkFetched, username, existingValue, checkMMLId(roadLinkFetched))
      case (None, None) =>
        eventbus.publish("roadLinkProperty:changed", LinkPropertyChange(propertyName, optionalExistingValue, linkProperty, roadLinkFetched, username))
        updateSideCodes(Seq(roadLinkFetched))
        insertLinkProperty(propertyName, linkProperty, roadLinkFetched, username, latestModifiedAt, latestModifiedBy)
      case (None, Some(masterDataValue)) =>
        if (masterDataValue != RoadLinkOverrideDAO.getValue(propertyName, linkProperty)) { // only save if it overrides master data value
          eventbus.publish("roadLinkProperty:changed", LinkPropertyChange(propertyName, optionalExistingValue, linkProperty, roadLinkFetched, username))
          updateSideCodes(Seq(roadLinkFetched))
          insertLinkProperty(propertyName, linkProperty, roadLinkFetched, username, latestModifiedAt, latestModifiedBy)
        }
    }
  }

  private def insertLinkProperty(propertyName: String, linkProperty: LinkProperties, roadLinkFetched: RoadLinkFetched,
                                 username: Option[String], latestModifiedAt: Option[String],
                                 latestModifiedBy: Option[String]) = {
    if (latestModifiedAt.isEmpty) {
      RoadLinkOverrideDAO.insert(propertyName, linkProperty, roadLinkFetched, username, checkMMLId(roadLinkFetched))
    } else{
      try {
        var parsedDate = ""
        if (latestModifiedAt.get.matches("^\\d\\d\\.\\d\\d\\.\\d\\d\\d\\d.*")) {
          // Finnish date format
          parsedDate = DateTimePropertyFormat.parseDateTime(latestModifiedAt.get).toString()
        } else {
          parsedDate = DateTime.parse(latestModifiedAt.get).toString(ISODateTimeFormat.dateTime())
        }
        RoadLinkOverrideDAO.insert(propertyName, linkProperty, latestModifiedBy, parsedDate)
      } catch {
        case e: Exception =>
          println("ERR! -> table " + propertyName + " (" + linkProperty.linkId + "): mod timestamp = " + latestModifiedAt.getOrElse("null"))
          throw e
      }
    }
  }

  private def fetchOverrides(idTableName: String): Map[String, (Option[RoadLinkPropertyRow],
    Option[RoadLinkPropertyRow], Option[RoadLinkPropertyRow], Option[RoadLinkPropertyRow])] = {
    sql"""select i.id, t.link_id, t.traffic_direction, t.modified_date, t.modified_by,
          f.link_id, f.functional_class, f.modified_date, f.modified_by,
          l.link_id, l.link_type, l.modified_date, l.modified_by,
          a.link_id, a.administrative_class, a.created_date, a.created_by
            from #$idTableName i
            left join traffic_direction t on i.id = t.link_id
            left join functional_class f on i.id = f.link_id
            left join link_type l on i.id = l.link_id
            left join administrative_class a on i.id = a.link_id and (a.valid_to IS NULL OR a.valid_to > current_timestamp)
      """.as[(String, Option[String], Option[Int], Option[DateTime], Option[String],
      Option[String], Option[Int], Option[DateTime], Option[String],
      Option[String], Option[Int], Option[DateTime], Option[String],
      Option[String], Option[Int], Option[DateTime], Option[String])].list.map(row =>
    {
      val td = (row._2, row._3, row._4, row._5) match {
        case (Some(linkId), Some(dir), Some(modDate), Some(modBy)) => Option((linkId, dir, modDate, Some(modBy)))
        case _ => None
      }
      val fc = (row._6, row._7, row._8, row._9) match {
        case (Some(linkId), Some(dir), Some(modDate), Some(modBy)) => Option((linkId, dir, modDate, Some(modBy)))
        case _ => None
      }
      val lt = (row._10, row._11, row._12, row._13) match {
        case (Some(linkId), Some(dir), Some(modDate), Some(modBy)) => Option((linkId, dir, modDate, Some(modBy)))
        case _ => None
      }
      val ac = (row._14, row._15, row._16, row._17) match{
        case (Some(linkId), Some(value), Some(createdDate), createdByOption) => Option((linkId, value, createdDate, createdByOption))
        case _ => None
      }
      row._1 ->(td, fc, lt, ac)
    }
    ).toMap
  }

  protected def setLinkAttributes(propertyName: String, linkProperty: LinkProperties, username: Option[String],
                                  roadLinkFetched: RoadLinkFetched, latestModifiedAt: Option[String],
                                  latestModifiedBy: Option[String]) = {

    val linkAttributesInfo = LinkAttributesDao.getExistingValues(linkProperty.linkId)

    val oldPrivateRoadAssociation = linkAttributesInfo.get(privateRoadAssociationPublicId)
    val oldAdditionalInfo = linkAttributesInfo.get(additionalInfoPublicId)
    val oldAccessRightID = linkAttributesInfo.get(accessRightIDPublicId)

    if (oldPrivateRoadAssociation.isEmpty && linkProperty.privateRoadAssociation.nonEmpty) {
      LinkAttributesDao.insertAttributeValue(linkProperty, username.getOrElse(""), privateRoadAssociationPublicId, linkProperty.privateRoadAssociation.get, None)
    } else if (oldPrivateRoadAssociation != linkProperty.privateRoadAssociation) {
      linkProperty.privateRoadAssociation match {
        case Some(privateRoad) if privateRoad.nonEmpty => LinkAttributesDao.updateAttributeValue(linkProperty, username.getOrElse(""), privateRoadAssociationPublicId, privateRoad)
        case _ => LinkAttributesDao.expireAttributeValue(linkProperty, username.getOrElse(""), privateRoadAssociationPublicId)
      }
    }

    if (oldAdditionalInfo.isEmpty && linkProperty.additionalInfo.nonEmpty) {
      LinkAttributesDao.insertAttributeValue(linkProperty, username.getOrElse(""), additionalInfoPublicId, linkProperty.additionalInfo.get.value, None)
    } else if(linkProperty.additionalInfo.nonEmpty && AdditionalInformation.apply(oldAdditionalInfo.get) != linkProperty.additionalInfo.get) {
      LinkAttributesDao.updateAttributeValue(linkProperty, username.getOrElse(""), additionalInfoPublicId, linkProperty.additionalInfo.get.value)
    }

    if (oldAccessRightID.isEmpty && linkProperty.accessRightID.nonEmpty) {
      LinkAttributesDao.insertAttributeValue(linkProperty, username.getOrElse(""), accessRightIDPublicId, linkProperty.accessRightID.get, None)
    } else if (oldAccessRightID != linkProperty.accessRightID) {
      linkProperty.accessRightID match {
        case Some(accessRight) if accessRight.nonEmpty => LinkAttributesDao.updateAttributeValue(linkProperty, username.getOrElse(""), accessRightIDPublicId, accessRight)
        case _ => LinkAttributesDao.expireAttributeValue(linkProperty, username.getOrElse(""), accessRightIDPublicId)
      }
    }
  }

  private def fetchOverridedRoadLinkAttributes(idTableName: String): List[(String, Option[(String, String)])] = {
    val fetchResult =
      sql"""select rla.id, rla.link_id, rla.name, rla.value, rla.created_date, rla.created_by, rla.modified_date, rla.modified_by
            from #$idTableName i
            join road_link_attributes rla on i.id = rla.link_id and rla.valid_to IS NULL"""
            .as[RoadLinkAttributeInfo].list

    fetchResult.map(row => {
      val rla = (row.name, row.value) match {
        case (Some(name), Some(value)) => Option((name, value))
        case _ => None
      }

      row.linkId.get -> rla
    }
    ) ++ getPrivateRoadLastModification(fetchResult)
  }

  private def getPrivateRoadLastModification(fetchResult: Seq[RoadLinkAttributeInfo]): Seq[(String, Option[(String, String)])] = {
    val groupedResults = fetchResult.filter(row => row.linkId.nonEmpty && Seq(privateRoadAssociationPublicId, additionalInfoPublicId).contains(row.name.get)).flatMap {roadInfo =>
        (roadInfo.createdDate, roadInfo.modifiedDate) match {
          case (Some(createdDt), None) => Some(roadInfo.linkId.get, createdDt, roadInfo.createdBy.get)
          case (_, Some(modifiedDt)) => Some(roadInfo.linkId.get, modifiedDt, roadInfo.modifiedBy.get)
          case _ => None
        }
    }.groupBy(_._1).mapValues(_.sortBy(_._2)(dateTimeOrdering).head)

    groupedResults.map(value => Seq((value._2._1, Some(privateLastModifiedDatePublicId , DateTimeFormat.forPattern("dd.MM.yyyy HH:mm:ss").print(value._2._2))),
      (value._2._1, Some(privateLastModifiedUserPublicId, value._2._3)))
    ).flatten.toSeq
  }

  def groupPrivateRoadInformation(results: List[(String, Option[(String, String)])]): Seq[PrivateRoadInfoStructure] = {
    val inputFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss")
    val outputFormat = new SimpleDateFormat("dd.MM.yyyy")

    results.groupBy(_._1).map { attr =>
      val prop = attr._2.flatMap(_._2)
      PrivateRoadInfoStructure(
        prop.find(_._1 == privateRoadAssociationPublicId).map(_._2),
        prop.find(_._1 == accessRightIDPublicId).map(_._2),
        prop.find(_._1 == additionalInfoPublicId).map(_._2),
        prop.find(_._1 == privateLastModifiedDatePublicId).map(date => outputFormat.format(inputFormat.parse(date._2)))
      )
    }.toSeq.distinct
  }

  def getRoadLinksHistory(bounds: BoundingRectangle, municipalities: Set[Int] = Set()) : Seq[RoadLink] = {
    val historyRoadLinks = Await.result(roadLinkClient.historyData.fetchByMunicipalitiesAndBoundsF(bounds, municipalities), atMost = Duration.Inf)

    val roadlinks = withDbConnection {roadLinkDAO.fetchByMunicipalitiesAndBounds(bounds, municipalities)}

    val linkprocessor = new RoadLinkHistoryProcessor()
    // picks links that are newest in each link chains history with that are with in set tolerance . Keeps ones with no current link
    val filteredHistoryLinks = linkprocessor.process(historyRoadLinks, roadlinks)

    withDynTransaction {
      enrichFetchedRoadLinks(filteredHistoryLinks)
    }
  }

  def getHistoryDataLink(linkId: String, newTransaction: Boolean = true): Option[RoadLink] = getHistoryDataLinks(Set(linkId), newTransaction).headOption

  def getHistoryDataLinks(linkIds: Set[String], newTransaction: Boolean = true): Seq[RoadLink] = {
    if (linkIds.isEmpty) Seq.empty[HistoryRoadLink]

    val historyRoadLinks = roadLinkClient.historyData.fetchByLinkIds(linkIds)
    if (newTransaction)
      withDynTransaction {
        enrichFetchedRoadLinks(historyRoadLinks)
      }
    else
      enrichFetchedRoadLinks(historyRoadLinks)
  }


  /**
    * Returns closest road link by user's authorization and point coordinates. Used by Digiroad2Api /servicePoints PUT and /servicePoints/:id PUT endpoints and add_obstacles_shapefile batch.
    */
  def getClosestRoadlink(user: User, point: Point, vectorRadius: Int = 500): Option[RoadLinkFetched] = {
    val diagonal = Vector3d(vectorRadius, vectorRadius, 0)

    val roadLinks =
      if (user.isOperator()) fetchRoadLinksByBoundsAndMunicipalities(BoundingRectangle(point - diagonal, point + diagonal))
      else fetchRoadLinksByBoundsAndMunicipalities(BoundingRectangle(point - diagonal, point + diagonal), user.configuration.authorizedMunicipalities)

    if (roadLinks.isEmpty) None
    else Some(roadLinks.minBy(roadLink => minimumDistance(point, roadLink.geometry)))
  }

  def getClosestRoadlinkForCarTraffic(user: User, point: Point, forCarTraffic: Boolean = true, includeComplementaries: Boolean = false): Seq[RoadLink] = {
    val diagonal = Vector3d(10, 10, 0)

    val roadLinks =
      if (user.isOperator()) fetchRoadLinksByBoundsAndMunicipalities(BoundingRectangle(point - diagonal, point + diagonal), includeComplementaries = includeComplementaries)
      else fetchRoadLinksByBoundsAndMunicipalities(BoundingRectangle(point - diagonal, point + diagonal), user.configuration.authorizedMunicipalities, includeComplementaries)

    val closestRoadLinks =
      if (roadLinks.isEmpty) Seq.empty[RoadLink]
      else  enrichFetchedRoadLinks(roadLinks.filter(rl => GeometryUtils.minimumDistance(point, rl.geometry) <= 10.0))

    if (forCarTraffic) closestRoadLinks.filter(roadLink => roadLink.linkType != CycleOrPedestrianPath && roadLink.linkType != PedestrianZone)
    else closestRoadLinks
  }

  protected def removeIncompleteness(linkId: String): Unit = {
    sqlu"""delete from incomplete_link where link_id = $linkId""".execute
  }

  /**
    * Updates incomplete road link list (incomplete = functional class or link type missing).
    */
  def updateIncompleteLinks(incompleteLinks: Seq[IncompleteLink]): Unit = {
    IncompleteLinkDao.insertIncompleteLinks(incompleteLinks)
  }


  def getRoadLinksAndComplementaryLinksByMunicipality(municipality: Int, newTransaction: Boolean = true): Seq[RoadLink] = {
    val (roadLinks, complementaries) =  LogUtils.time(logger,"Get roadlinks with cache")(
      getCachedRoadLinks(municipality, newTransaction)
    )
    roadLinks ++ complementaries
  }


  def enrichFetchedRoadLinks(allFetchedRoadLinks: Seq[IRoadLinkFetched], includeHardShoulderRoads: Boolean = false): Seq[RoadLink] = {
    val featureClassesToExclude =
      if (includeHardShoulderRoads) Seq(FeatureClass.WinterRoads)
      else FeatureClass.featureClassesToIgnore
    val filteredRoadLinks = allFetchedRoadLinks.filterNot(link => featureClassesToExclude.contains(link.featureClass))
    LogUtils.time(logger,"TEST LOG enrich roadLinkDataByLinkId, link count: " + filteredRoadLinks.size){getRoadLinkDataByLinkIds(filteredRoadLinks)}
  }


  /**
    * Passes fetched road links to adjustedRoadLinks to get road links. Used by RoadLinkService.enrichFetchedRoadLinks and UpdateIncompleteLinkList.
    */
  def getRoadLinkDataByLinkIds(roadLinks: Seq[IRoadLinkFetched]): Seq[RoadLink] = {
    adjustedRoadLinks(roadLinks)
  }

  private def adjustedRoadLinks(roadLinks: Seq[IRoadLinkFetched]): Seq[RoadLink] = {
    val propertyRows = fetchRoadLinkPropertyRows(roadLinks.map(_.linkId).toSet)

    roadLinks.map { link =>
      val latestModification = propertyRows.latestModifications(link.linkId, link.modifiedAt.map(at => (at,
        AutoGeneratedUsername.automaticAdjustment)))
      val (modifiedAt, modifiedBy) = (latestModification.map(_._1), latestModification.map(_._2))

      RoadLink(link.linkId, link.geometry,
        link.length,
        propertyRows.administrativeClassValue(link.linkId).getOrElse(link.administrativeClass),
        propertyRows.functionalClassValue(link.linkId),
        propertyRows.trafficDirectionValue(link.linkId).getOrElse(link.trafficDirection),
        propertyRows.linkTypeValue(link.linkId),
        modifiedAt.map(DateTimePropertyFormat.print),
        modifiedBy, link.attributes ++ propertyRows.roadLinkAttributesValues(link.linkId), link.constructionType, link.linkSource)
    }
  }

  private def fetchRoadLinkPropertyRows(linkIds: Set[String]): RoadLinkPropertyRows = {
    def cleanMap(parameterMap: Map[String, Option[RoadLinkPropertyRow]]): Map[RoadLinkId, RoadLinkPropertyRow] = {
      parameterMap.filter(i => i._2.nonEmpty).mapValues(i => i.get)
    }
    def splitMap(parameterMap: Map[String, (Option[RoadLinkPropertyRow],
      Option[RoadLinkPropertyRow], Option[RoadLinkPropertyRow],
      Option[RoadLinkPropertyRow])] ) = {
      (cleanMap(parameterMap.map(i => i._1 -> i._2._1)),
        cleanMap(parameterMap.map(i => i._1 -> i._2._2)),
        cleanMap(parameterMap.map(i => i._1 -> i._2._3)),
        cleanMap(parameterMap.map(i => i._1 -> i._2._4)))
    }

    def splitRoadLinkAttributesMap(parameterMap: List[(String, Option[(String, String)])]) = {
      parameterMap.filter(_._2.nonEmpty).groupBy(_._1).map { case (k, v) => (k, v.map(_._2.get)) }
    }

    MassQuery.withStringIds(linkIds) {
      idTableName =>
        val (td, fc, lt, ac) = splitMap(fetchOverrides(idTableName))
        val overridedRoadLinkAttributes = splitRoadLinkAttributesMap(fetchOverridedRoadLinkAttributes(idTableName))
        RoadLinkPropertyRows(td, fc, lt, ac, overridedRoadLinkAttributes)
    }
  }

  type RoadLinkId = String
  type RoadLinkPropertyRow = (String, Int, DateTime, Option[String])
  type RoadLinkAtributes = Seq[(String, String)]

  case class RoadLinkPropertyRows(trafficDirectionRowsByLinkId: Map[RoadLinkId, RoadLinkPropertyRow],
                                  functionalClassRowsByLinkId: Map[RoadLinkId, RoadLinkPropertyRow],
                                  linkTypeRowsByLinkId: Map[RoadLinkId, RoadLinkPropertyRow],
                                  administrativeClassRowsByLinkId: Map[RoadLinkId, RoadLinkPropertyRow],
                                  roadLinkAttributesByLinkId: Map[RoadLinkId, RoadLinkAtributes]) {

    def roadLinkAttributesValues(linkId: String): Map[String, String] = {
      roadLinkAttributesByLinkId.get(linkId) match {
        case Some(attributes) => attributes.toMap
        case _ => Map.empty[String, String]
      }
    }

    def functionalClassValue(linkId: String): Int = {
      val functionalClassRowOption = functionalClassRowsByLinkId.get(linkId)
      functionalClassRowOption.map(_._2).getOrElse(UnknownFunctionalClass.value)
    }

    def linkTypeValue(linkId: String): LinkType = {
      val linkTypeRowOption = linkTypeRowsByLinkId.get(linkId)
      linkTypeRowOption.map(linkTypeRow => LinkType(linkTypeRow._2)).getOrElse(UnknownLinkType)
    }

    def trafficDirectionValue(linkId: String): Option[TrafficDirection] = {
      val trafficDirectionRowOption = trafficDirectionRowsByLinkId.get(linkId)
      trafficDirectionRowOption.map(trafficDirectionRow => TrafficDirection(trafficDirectionRow._2))
    }

    def administrativeClassValue(linkId: String): Option[AdministrativeClass] = {
      val administrativeRowOption = administrativeClassRowsByLinkId.get(linkId)
      administrativeRowOption.map( ac => AdministrativeClass.apply(ac._2))
    }

    def latestModifications(linkId: String, optionalModification: Option[(DateTime, String)] = None): Option[(DateTime, String)] = {
      val functionalClassRowOption = functionalClassRowsByLinkId.get(linkId)
      val linkTypeRowOption = linkTypeRowsByLinkId.get(linkId)
      val trafficDirectionRowOption = trafficDirectionRowsByLinkId.get(linkId)
      val administrativeRowOption = administrativeClassRowsByLinkId.get(linkId)

      val modifications = List(functionalClassRowOption, trafficDirectionRowOption, linkTypeRowOption, administrativeRowOption).map {
        case Some((_, _, at, Some(by))) => Some((at, by))
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
    * Get the link points depending on the road link
    *
    * @param roadLink The Roadlink
    * @return Points of the road link
    */
  def getRoadLinkPoints(roadLink: RoadLink) : Seq[Point] = {
    val endPoints = GeometryUtils.geometryEndpoints(roadLink.geometry)
    Seq(endPoints._1, endPoints._2)
  }


  def getRoadLinkEndDirectionPoints(roadLink: RoadLink, direction: Option[Int] = None) : Seq[Point] = {
    val endPoints = GeometryUtils.geometryEndpoints(roadLink.geometry)
    direction match {
      case Some(dir) =>SideCode(dir) match {
        case SideCode.TowardsDigitizing =>
          Seq(endPoints._2)
        case SideCode.AgainstDigitizing =>
          Seq(endPoints._1)
        case _ =>
          Seq(endPoints._1, endPoints._2)
      }
      case _ => Seq(endPoints._1, endPoints._2)
    }
  }


  def getRoadLinkStartDirectionPoints(roadLink: RoadLink, direction: Option[Int] = None) : Seq[Point] = {
    val endPoints = GeometryUtils.geometryEndpoints(roadLink.geometry)
    direction match {
      case Some(dir) =>SideCode(dir) match {
        case SideCode.TowardsDigitizing =>
          Seq(endPoints._1)
        case SideCode.AgainstDigitizing =>
          Seq(endPoints._2)
        case _ =>
          Seq(endPoints._1, endPoints._2)
      }
      case _ => Seq(endPoints._1, endPoints._2)
    }
  }

  def filterRoadLinkByBearing(assetBearing: Option[Int], assetValidityDirection: Option[Int], assetCoordinates: Point, roadLinks: Seq[RoadLink]): Seq[RoadLink] = {
    val toleranceInDegrees = 25

    def getAngle(b1: Int, b2: Int): Int = {
      180 - Math.abs(Math.abs(b1 - b2) - 180)
    }

    assetBearing match {
      case Some(aBearing) =>
        val filteredEnrichedRoadLinks =
          roadLinks.filter { roadLink =>
            val mValue = GeometryUtils.calculateLinearReferenceFromPoint(assetCoordinates, roadLink.geometry)
            val roadLinkBearing = GeometryUtils.calculateBearing(roadLink.geometry, Some(mValue))

            if (roadLink.trafficDirection == TrafficDirection.BothDirections) {
              val reverseRoadLinkBearing =
                if (roadLinkBearing - 180 < 0) {
                  roadLinkBearing + 180
                } else {
                  roadLinkBearing - 180
                }

              getAngle(aBearing, roadLinkBearing) <= toleranceInDegrees || Math.abs(aBearing - reverseRoadLinkBearing) <= toleranceInDegrees
            } else {
              getAngle(aBearing, roadLinkBearing) <= toleranceInDegrees && (TrafficDirection.toSideCode(roadLink.trafficDirection).value == assetValidityDirection.get)
            }
          }
        filteredEnrichedRoadLinks
      case _ =>
        roadLinks
    }
  }


  /**
   * used by getAdjacent method to handle minor errors in road link geometry precision
   */
  private def pointFoundWithinEpsilon(sourcePoint: Point, destPoints: Seq[Point]) = {
    destPoints.exists(d => {
      Math.abs(d.x - sourcePoint.x) < getDefaultEpsilon() && Math.abs(d.y - sourcePoint.y) < getDefaultEpsilon()
    })
  }

  /**
    * Returns adjacent road links by link id. Used by Digiroad2Api /roadlinks/adjacent/:id GET endpoint and CsvGenerator.generateDroppedManoeuvres.
    */
  def getAdjacent(linkId: String, newTransaction: Boolean): Seq[RoadLink] = {
    val sourceRoadLink = getRoadLinksByLinkIds(Set(linkId), newTransaction).headOption
    val sourceLinkGeometryOption = sourceRoadLink.map(_.geometry)
    val sourcePoints = getRoadLinkPoints(sourceRoadLink.get)
    sourceLinkGeometryOption.map(sourceLinkGeometry => {
      val sourceLinkEndpoints = GeometryUtils.geometryEndpoints(sourceLinkGeometry)
      val delta: Vector3d = Vector3d(0.1, 0.1, 0)
      val bounds = BoundingRectangle(sourceLinkEndpoints._1 - delta, sourceLinkEndpoints._1 + delta)
      val bounds2 = BoundingRectangle(sourceLinkEndpoints._2 - delta, sourceLinkEndpoints._2 + delta)
      val roadLinks = getRoadLinksByBounds(bounds, bounds2, newTransaction)
      roadLinks.filterNot(_.linkId == linkId)
        .filter(roadLink => roadLink.isCarTrafficRoad)
        .filter(roadLink => {
          val targetLinkGeometry = roadLink.geometry
          GeometryUtils.areAdjacent(sourceLinkGeometry, targetLinkGeometry)
        })
        .filter(roadLink => {
          //It's a valid destination link to turn if the end point of the source exists on the
          //start points of the destination links
          val pointDirections = getRoadLinkPoints(roadLink)
          sourcePoints.exists(sourcePoint => pointFoundWithinEpsilon(sourcePoint, pointDirections))
        })
    }).getOrElse(Nil)
  }

  def getAdjacent(linkId: String, sourcePoints: Seq[Point], newTransaction: Boolean = true): Seq[RoadLink] = {
    val sourceRoadLink = getRoadLinksByLinkIds(Set(linkId), newTransaction).headOption
    val sourceLinkGeometryOption = sourceRoadLink.map(_.geometry)
    sourceLinkGeometryOption.map(sourceLinkGeometry => {
      val sourceLinkEndpoints = GeometryUtils.geometryEndpoints(sourceLinkGeometry)
      val delta: Vector3d = Vector3d(0.1, 0.1, 0)
      val bounds = BoundingRectangle(sourceLinkEndpoints._1 - delta, sourceLinkEndpoints._1 + delta)
      val bounds2 = BoundingRectangle(sourceLinkEndpoints._2 - delta, sourceLinkEndpoints._2 + delta)
      val roadLinks = getRoadLinksByBounds(bounds, bounds2, newTransaction)
      roadLinks.filterNot(_.linkId == linkId)
        .filter(roadLink => roadLink.isCarTrafficRoad)
        .filter(roadLink => {
          val targetLinkGeometry = roadLink.geometry
          GeometryUtils.areAdjacent(sourceLinkGeometry, targetLinkGeometry)
        })
        .filter(roadLink => {
          val pointDirections = getRoadLinkStartDirectionPoints(roadLink)
          sourcePoints.exists(sourcePoint => pointFoundWithinEpsilon(sourcePoint, pointDirections))
        })
    }).getOrElse(Nil)
  }

  def pickRightMost(lastLink: RoadLink, candidates: Seq[RoadLink]): RoadLink = {
    val cPoint =  getConnectionPoint(lastLink, candidates)
    val forward = getGeometryLastSegmentVector(cPoint, lastLink)
    val vectors = candidates.map(pl => (pl, GeometryUtils.firstSegmentDirection(if (GeometryUtils.areAdjacent(pl.geometry.head, cPoint)) pl.geometry else pl.geometry.reverse)))
    val (_, hVector) = forward
    val (candidate, _) = vectors.maxBy {
      case (rl, vector) =>
        val rAngle = hVector.angleXY(vector)
        println(s"rl = ${rl.linkId} hVector = $hVector vector = $vector angle = ${Math.toDegrees(rAngle)} ")
        rAngle
    }
    candidate
  }

  def pickLeftMost(lastLink: RoadLink, candidates: Seq[RoadLink]): RoadLink = {
    val cPoint =  getConnectionPoint(lastLink, candidates)
    val forward = getGeometryLastSegmentVector(cPoint, lastLink)
    val vectors = candidates.map(pl => (pl, GeometryUtils.firstSegmentDirection(if (GeometryUtils.areAdjacent(pl.geometry.head, cPoint)) pl.geometry else pl.geometry.reverse)))
    val (_, hVector) = forward
    val (candidate, _) = vectors.minBy {  case (rl, vector) =>
      val rAngle = hVector.angleXY(vector)
      println(s"rl = ${rl.linkId} hVector = $hVector vector = $vector angle = ${Math.toDegrees(rAngle)} ")
      rAngle
    }
    candidate
  }

  def pickForwardMost(lastLink: RoadLink, candidates: Seq[RoadLink]): RoadLink = {
    val cPoint = getConnectionPoint(lastLink, candidates)
    val candidateVectors = getGeometryFirstSegmentVectors(cPoint, candidates)
    val (_, lastLinkVector) = getGeometryLastSegmentVector(cPoint, lastLink)
    val (candidate, _) = candidateVectors.minBy{ case (_, vector) => Math.abs(lastLinkVector.angleXYWithNegativeValues(vector)) }
    candidate
  }

  def getConnectionPoint(lastLink: RoadLink, projectLinks: Seq[RoadLink]) : Point =
    GeometryUtils.connectionPoint(projectLinks.map(_.geometry) :+ lastLink.geometry).getOrElse(throw new Exception("Candidates should have at least one connection point"))

  def getGeometryFirstSegmentVectors(connectionPoint: Point, candidates: Seq[RoadLink]) : Seq[(RoadLink, Vector3d)] =
    candidates.map(pl => getGeometryFirstSegmentVector(connectionPoint, pl))

  def getGeometryFirstSegmentVector(connectionPoint: Point, roadLink: RoadLink) : (RoadLink, Vector3d) =
    (roadLink, GeometryUtils.firstSegmentDirection(if (GeometryUtils.areAdjacent(roadLink.geometry.head, connectionPoint)) roadLink.geometry else roadLink.geometry.reverse))

  def getGeometryLastSegmentVector(connectionPoint: Point, roadLink: RoadLink) : (RoadLink, Vector3d) =
    (roadLink, GeometryUtils.lastSegmentDirection(if (GeometryUtils.areAdjacent(roadLink.geometry.last, connectionPoint)) roadLink.geometry else roadLink.geometry.reverse))
  
  def geometryToBoundingBox(s: Seq[Point], delta: Vector3d) = {
    BoundingRectangle(Point(s.minBy(_.x).x, s.minBy(_.y).y) - delta, Point(s.maxBy(_.x).x, s.maxBy(_.y).y) + delta)
  }

  /**
    * Returns adjacent road links for list of ids.
    * Used by Digiroad2Api /roadlinks/adjacents/:ids GET endpoint
    */
  def getAdjacents(linkIds: Set[String]): Map[String, Seq[RoadLink]] = {
    val roadLinks = getRoadLinksByLinkIds(linkIds)
    val sourceLinkGeometryMap = roadLinks.map(rl => rl -> rl.geometry).toMap
    val delta: Vector3d = Vector3d(0.1, 0.1, 0)
    val sourceLinkBoundingBox = geometryToBoundingBox(sourceLinkGeometryMap.values.flatten.toSeq, delta)
    val sourceLinks = getRoadLinksByBoundsAndMunicipalities(sourceLinkBoundingBox, Set[Int]()).filter(roadLink => roadLink.isCarTrafficRoad)

    val mapped = sourceLinks.map(rl => rl.linkId -> getRoadLinkPoints(rl)).toMap
    val reverse = sourceLinks.map(rl => rl -> getRoadLinkPoints(rl)).flatMap {
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

  /**
    *  Call reloadRoadLinksWithComplementary
    */
  private def getCachedRoadLinks(municipalityCode: Int, newTransaction: Boolean = true): (Seq[RoadLink], Seq[RoadLink]) = {
    Caching.cache[(Seq[RoadLink], Seq[RoadLink])](
      reloadRoadLinksWithComplementary(municipalityCode, newTransaction)
    )("links:" + municipalityCode)
  }

  /**
    * This method returns roadLinks that have been changed within a specified time period. It is used by TN-ITS ChangeApi.
    *
    * @param sinceDate
    * @param untilDate
    * @return Changed Road Links between given dates
    */
  def getChanged(sinceDate: DateTime, untilDate: DateTime): Seq[ChangedRoadlink] = {
    val municipalitiesForSwedishRoadNames = List(35, 43, 60, 62, 65, 76, 170, 295, 318, 417, 438, 478, 736, 766, 771, 941)
    val timezone = DateTimeZone.forOffsetHours(0)

    val roadLinksBetweenDates = getRoadLinksByChangesWithinTimePeriod(sinceDate, untilDate)
    val roadLinks = withDynTransaction { enrichFetchedRoadLinks(roadLinksBetweenDates) }

    roadLinks.map { roadLink =>
      ChangedRoadlink(
        link = roadLink,
        value =
          if (municipalitiesForSwedishRoadNames.contains(roadLink.municipalityCode)) {
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

  /**
    * Delete or expire overwritten road link properties, link types and functional classes on given expired links
    * @param expiredLinkIds Expired road link IDs
    */
  def deleteRoadLinksAndPropertiesByLinkIds(expiredLinkIds: Set[String]): Unit = {
    LogUtils.time(logger, "TEST LOG Delete and expire road link properties and delete expired road links") {
      expiredLinkIds.foreach { linkId =>
        TrafficDirectionDao.deleteValues(linkId)
        LinkTypeDao.deleteValues(linkId)
        FunctionalClassDao.deleteValues(linkId)
        AdministrativeClassDao.expireValues(linkId, Some(AutoGeneratedUsername.automaticAdjustment), Some(LinearAssetUtils.createTimeStamp()))
        LinkAttributesDao.expireValues(linkId, Some(AutoGeneratedUsername.automaticAdjustment), Some(LinearAssetUtils.createTimeStamp()))
      }
      roadLinkDAO.deleteRoadLinksByIds(expiredLinkIds)
    }
  }

}
