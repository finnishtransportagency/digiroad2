package fi.liikennevirasto.digiroad2.service

import java.io.{File, FilenameFilter, IOException}
import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit
import java.util.{Date, Properties}

import com.github.tototoshi.slick.MySQLJodaSupport._
import com.vividsolutions.jts.geom.Polygon
import fi.liikennevirasto.digiroad2.GeometryUtils._
import fi.liikennevirasto.digiroad2.asset.DateParser._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh._
import fi.liikennevirasto.digiroad2.dao.RoadLinkDAO
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkProperties, TinyRoadLink}
import fi.liikennevirasto.digiroad2.oracle.{MassQuery, OracleDatabase}
import fi.liikennevirasto.digiroad2.asset.CycleOrPedestrianPath
import fi.liikennevirasto.digiroad2.user.User
import fi.liikennevirasto.digiroad2.util._
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.client.vvh.ChangeType.New
import fi.liikennevirasto.digiroad2.dao.RoadLinkDAO.LinkAttributesDao
import org.joda.time.format.{DateTimeFormat, ISODateTimeFormat}
import org.joda.time.{DateTime, DateTimeZone}
import org.slf4j.LoggerFactory
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedResult, StaticQuery => Q}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

case class IncompleteLink(linkId: Long, municipalityCode: Int, administrativeClass: AdministrativeClass)
case class RoadLinkChangeSet(adjustedRoadLinks: Seq[RoadLink], incompleteLinks: Seq[IncompleteLink], changes: Seq[ChangeInfo] = Nil, roadLinks: Seq[RoadLink] = Nil)
case class ChangedVVHRoadlink(link: RoadLink, value: String, createdAt: Option[DateTime], changeType: String /*TODO create and use ChangeType case object*/)
case class LinkProperties(linkId: Long, functionalClass: Int, linkType: LinkType, trafficDirection: TrafficDirection,
                          administrativeClass: AdministrativeClass, privateRoadAssociation: Option[String] = None, additionalInfo: Option[AdditionalInformation] = None,
                          accessRightID: Option[String] = None)
case class PrivateRoadAssociation(name: String, roadName: String, municipality: String, linkId: Long)
case class RoadLinkAttributeInfo(id: Long, linkId: Option[Long], name: Option[String], value: Option[String], createdDate: Option[DateTime], createdBy: Option[String], modifiedDate: Option[DateTime], modifiedBy: Option[String])

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
  * This class performs operations related to road links. It uses VVHClient to get data from VVH Rest API.
  *
  * @param vvhClient
  * @param eventbus
  * @param vvhSerializer
  */
class RoadLinkService(val vvhClient: VVHClient, val eventbus: DigiroadEventBus, val vvhSerializer: VVHSerializer) {
  lazy val municipalityService = new MunicipalityService

  val logger = LoggerFactory.getLogger(getClass)

  //Attributes names used on table "road_link_attributes"
  val privateRoadAssociationPublicId = "PRIVATE_ROAD_ASSOCIATION"
  val additionalInfoPublicId = "ADDITIONAL_INFO"
  val accessRightIDPublicId = "ACCESS_RIGHT_ID"
  val privateLastModifiedDatePublicId = "PRIVATE_ROAD_LAST_MOD_DATE"
  val privateLastModifiedUserPublicId = "PRIVATE_ROAD_LAST_MOD_USER"

  //No road name found
  val roadWithoutName = "tuntematon tienimi"

  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)

  implicit def dateTimeOrdering: Ordering[DateTime] = Ordering.fromLessThan(_ isAfter  _)
  implicit val getDateTime = new GetResult[DateTime] {
    def apply(r: PositionedResult) = {
      new DateTime(r.nextTimestamp())
    }
  }
  implicit val getRoadAttributeInfo = new GetResult[RoadLinkAttributeInfo] {
    def apply(r: PositionedResult) = {
      val id = r.nextLong()
      val linkId = r.nextLongOption()
      val name = r.nextStringOption()
      val value = r.nextStringOption()
      val createdDate = r.nextTimestampOption().map(new DateTime(_))
      val createdBy = r.nextStringOption()
      val modifiedDate = r.nextTimestampOption().map(new DateTime(_))
      val modifiedBy = r.nextStringOption()

      RoadLinkAttributeInfo(id, linkId, name, value, createdDate, createdBy, modifiedDate, modifiedBy)
    }
  }

  /**
    * ATENTION Use this method always with transation not with session
    * This method returns a road link by link id.
    *
    * @param linkId
    * @param newTransaction
    * @return Road link
    */
  def getRoadLinkFromVVH(linkId: Long, newTransaction: Boolean = true): Option[RoadLink] = getRoadsLinksFromVVH(Set(linkId), newTransaction: Boolean).headOption

  def getRoadsLinksFromVVH(linkId: Set[Long], newTransaction: Boolean = true): Seq[RoadLink] = {
    val vvhRoadLinks = fetchVVHRoadlinks(linkId)
    if (newTransaction)
      withDynTransaction {
        enrichRoadLinksFromVVH(vvhRoadLinks)
      }
    else
      enrichRoadLinksFromVVH(vvhRoadLinks)
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

  def getRoadLinksWithComplementaryAndChangesFromVVHByMunicipality(municipality: Int, newTransaction: Boolean = true): (Seq[RoadLink], Seq[ChangeInfo]) = {
    val fut = for{
      changeInfos <- vvhClient.roadLinkChangeInfo.fetchByMunicipalityF(municipality)
      complementaryLinks <- vvhClient.complementaryData.fetchByMunicipalityF(municipality)
      vvhRoadLinks <- vvhClient.roadLinkData.fetchByMunicipalityF(municipality)
    } yield (changeInfos, complementaryLinks, vvhRoadLinks)

    val (changeInfos, complementaryLinks, vvhRoadLinks)= Await.result(fut, Duration.Inf)
    if (newTransaction)
      withDynTransaction {
        (enrichRoadLinksFromVVH(vvhRoadLinks ++ complementaryLinks, changeInfos), changeInfos)
      }
    else
      (enrichRoadLinksFromVVH(vvhRoadLinks ++ complementaryLinks, changeInfos), changeInfos)
  }


  /**
    * ATENTION Use this method always with transation not with session
    * This method returns road links by link ids.
    *
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

  def getRoadNodesByMunicipality(municipality: Int): Seq[VVHRoadNodes] = {
    getCachedRoadNodes(municipality)
  }

  def getRoadNodesFromVVHFuture(municipality: Int): Future[Seq[VVHRoadNodes]] = {
    Future(getRoadNodesByMunicipality(municipality))
  }

  def getTinyRoadLinkFromVVH(municipality: Int): Seq[TinyRoadLink] = getCachedTinyRoadLinks(municipality)

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

  def getRoadLinksFromVVHByBounds(bounds: BoundingRectangle, bounds2: BoundingRectangle, newTransaction: Boolean = true) : Seq[RoadLink] =
    getRoadLinksAndChangesByBoundsFromVVH(bounds, bounds2, newTransaction)._1

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

  /**
    * This method returns VVH road links by Finnish or Swedish name.
    *
    * @param roadNamePublicIds
    * @param roadNameSource
    * @return VVHRoadLinks
    */
  def fetchVVHRoadlinks(roadNamePublicIds: String, roadNameSource: Set[String]): Seq[VVHRoadlink] = {
      vvhClient.roadLinkData.fetchByRoadNames(roadNamePublicIds, roadNameSource)
  }

  def getAllLinkType(linkIds: Seq[Long]): Map[Long, Seq[(Long, LinkType)]] = {
    RoadLinkDAO.LinkTypeDao.getAllLinkType(linkIds).groupBy(_._1)
  }

  def getAllPrivateRoadAssociationNames(): Seq[String] = {
    withDynSession {
      LinkAttributesDao.getAllExistingDistinctValues(privateRoadAssociationPublicId)
    }
  }

  def getPrivateRoadsByAssociationName(roadAssociationName: String, newTransaction: Boolean = true): Seq[PrivateRoadAssociation] = {
    val roadNamesPerLinkId = getValuesByRoadAssociationName(roadAssociationName, privateRoadAssociationPublicId, newTransaction)
    val roadLinks = getRoadLinksAndComplementaryByLinkIdsFromVVH(roadNamesPerLinkId.map(_._2).toSet, newTransaction)

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
    val cachedRoadLinks = getTinyRoadLinkFromVVH(municipalityCode).map(_.linkId).toSet
    val results = getPrivateRoadsInfoByLinkIds(cachedRoadLinks)
    groupPrivateRoadInformation(results)
  }

  def getPrivateRoadsInfoByLinkIds(linkIds: Set[Long]): List[(Long, Option[(String, String)])] = {
    withDynTransaction {
      MassQuery.withIds(linkIds) { idTableName =>
        fetchOverridedRoadLinkAttributes(idTableName)
      }
    }
  }

  def getValuesByRoadAssociationName(roadAssociationName: String, roadAssociationPublicId: String, newTransaction: Boolean = true): List[(String, Long)] = {
    if(newTransaction)
      withDynSession {
        LinkAttributesDao.getValuesByRoadAssociationName(roadAssociationName, privateRoadAssociationPublicId)
      }
    else
      LinkAttributesDao.getValuesByRoadAssociationName(roadAssociationName, privateRoadAssociationPublicId)
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
    Await.result(Future.sequence(polygons.map(getLinkIdsFromVVHWithComplementaryByPolygonF)), Duration.Inf).flatten
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

  def getLinkIdsFromVVHWithComplementaryByPolygonF(polygon :Polygon): Future[Seq[Long]] = {
    Future(getLinkIdsFromVVHWithComplementaryByPolygon(polygon))
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

  def getRoadLinksAndComplementaryByLinkIdsFromVVH(linkIds: Set[Long], newTransaction:Boolean = true): Seq[RoadLink] = {
    val fut = for{
      f1Result <- vvhClient.complementaryData.fetchByLinkIdsF(linkIds)
      f2Result <- vvhClient.roadLinkData.fetchByLinkIdsF(linkIds)
    } yield (f1Result, f2Result)

    val (complementaryLinks, links) = Await.result(fut, Duration.Inf)

    if(newTransaction){
      withDynTransaction {
        enrichRoadLinksFromVVH(links ++ complementaryLinks)
      }
    }
    else enrichRoadLinksFromVVH(links ++ complementaryLinks)
  }

  def getRoadLinksAndComplementaryByRoadNameFromVVH(roadNamePublicIds: String, roadNameSource: Set[String], newTransaction: Boolean = true): Seq[RoadLink] = {
    val fut = for{
      f1Result <- vvhClient.complementaryData.fetchByRoadNamesF(roadNamePublicIds, roadNameSource)
      f2Result <- vvhClient.roadLinkData.fetchByRoadNamesF(roadNamePublicIds, roadNameSource)
    } yield (f1Result, f2Result)

    val (complementaryLinks, links) = Await.result(fut, Duration.Inf)

    if(newTransaction){
      withDynTransaction {
        enrichRoadLinksFromVVH(links ++ complementaryLinks)
      }
    }
    else enrichRoadLinksFromVVH(links ++ complementaryLinks)
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
    getRoadLinksAndChangesByBoundsFromVVH(bounds, bounds2)
  }

  def getRoadLinksAndChangesByBoundsFromVVH(bounds: BoundingRectangle, bounds2: BoundingRectangle, newTransaction: Boolean = true): (Seq[RoadLink], Seq[ChangeInfo])= {
    val links1F = vvhClient.roadLinkData.fetchByMunicipalitiesAndBoundsF(bounds, Set())
    val links2F = vvhClient.roadLinkData.fetchByMunicipalitiesAndBoundsF(bounds2, Set())
    val changeF = vvhClient.roadLinkChangeInfo.fetchByBoundsAndMunicipalitiesF(bounds, Set())
    val ((links, links2), changes) = Await.result(links1F.zip(links2F).zip(changeF), atMost = Duration.apply(60, TimeUnit.SECONDS))
    if(newTransaction)
      withDynTransaction {
        (enrichRoadLinksFromVVH(links ++ links2, changes), changes)
      }
    else
      (enrichRoadLinksFromVVH(links ++ links2, changes), changes)
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
    * Saves road link property data from UI.
    */
  def updateLinkProperties(linkProperty: LinkProperties, username: Option[String], municipalityValidation: (Int, AdministrativeClass) => Unit): Option[RoadLink] = {
    val vvhRoadLink = vvhClient.roadLinkData.fetchByLinkId(linkProperty.linkId) match {
      case Some(vvhRoadLink) => Some(vvhRoadLink)
      case None => vvhClient.complementaryData.fetchByLinkId(linkProperty.linkId)
    }
    vvhRoadLink.map { vvhRoadLink =>
      municipalityValidation(vvhRoadLink.municipalityCode, vvhRoadLink.administrativeClass)
      withDynTransaction {
        setLinkProperty(RoadLinkDAO.TrafficDirection, linkProperty, username, vvhRoadLink, None, None)
        if (linkProperty.functionalClass != FunctionalClass.Unknown) setLinkProperty(RoadLinkDAO.FunctionalClass, linkProperty, username, vvhRoadLink, None, None)
        if (linkProperty.linkType != UnknownLinkType) setLinkProperty(RoadLinkDAO.LinkType, linkProperty, username, vvhRoadLink, None, None)
        if (linkProperty.administrativeClass != State && vvhRoadLink.administrativeClass != State) setLinkProperty(RoadLinkDAO.AdministrativeClass, linkProperty, username, vvhRoadLink, None, None)
        if (linkProperty.administrativeClass == Private) setLinkAttributes(RoadLinkDAO.LinkAttributes, linkProperty, username, vvhRoadLink, None, None)
        else LinkAttributesDao.expireValues(linkProperty.linkId, username)
        val enrichedLink = enrichRoadLinksFromVVH(Seq(vvhRoadLink)).head
        if (enrichedLink.functionalClass != FunctionalClass.Unknown && enrichedLink.linkType != UnknownLinkType) {
          removeIncompleteness(linkProperty.linkId)
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

  protected def setLinkProperty(propertyName: String, linkProperty: LinkProperties, username: Option[String],
                                vvhRoadLink: VVHRoadlink, latestModifiedAt: Option[String],
                                latestModifiedBy: Option[String]) = {
    val optionalExistingValue: Option[Int] = RoadLinkDAO.get(propertyName, linkProperty.linkId)
    (optionalExistingValue, RoadLinkDAO.getVVHValue(propertyName, vvhRoadLink)) match {
      case (Some(existingValue), _) =>
        RoadLinkDAO.update(propertyName, linkProperty, vvhRoadLink, username, existingValue, checkMMLId(vvhRoadLink))
      case (None, None) =>
        insertLinkProperty(propertyName, linkProperty, vvhRoadLink, username, latestModifiedAt, latestModifiedBy)

      case (None, Some(vvhValue)) =>
        if (vvhValue != RoadLinkDAO.getValue(propertyName, linkProperty)) // only save if it overrides VVH provided value
          insertLinkProperty(propertyName, linkProperty, vvhRoadLink, username, latestModifiedAt, latestModifiedBy)
    }
  }

  private def insertLinkProperty(propertyName: String, linkProperty: LinkProperties, vvhRoadLink: VVHRoadlink,
                                 username: Option[String], latestModifiedAt: Option[String],
                                 latestModifiedBy: Option[String]) = {
    if (latestModifiedAt.isEmpty) {
      RoadLinkDAO.insert(propertyName, linkProperty, vvhRoadLink, username, checkMMLId(vvhRoadLink))
    } else{
      try {
        var parsedDate = ""
        if (latestModifiedAt.get.matches("^\\d\\d\\.\\d\\d\\.\\d\\d\\d\\d.*")) {
          // Finnish date format
          parsedDate = DateTimePropertyFormat.parseDateTime(latestModifiedAt.get).toString()
        } else {
          parsedDate = DateTime.parse(latestModifiedAt.get).toString(ISODateTimeFormat.dateTime())
        }
        RoadLinkDAO.insert(propertyName, linkProperty, latestModifiedBy, parsedDate)
      } catch {
        case e: Exception =>
          println("ERR! -> table " + propertyName + " (" + linkProperty.linkId + "): mod timestamp = " + latestModifiedAt.getOrElse("null"))
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
            left join administrative_class a on i.id = a.link_id and (a.valid_to IS NULL OR a.valid_to > sysdate)
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

  protected def setLinkAttributes(propertyName: String, linkProperty: LinkProperties, username: Option[String],
                                  vvhRoadLink: VVHRoadlink, latestModifiedAt: Option[String],
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

  private def fetchOverridedRoadLinkAttributes(idTableName: String): List[(Long, Option[(String, String)])] = {
    val fetchResult =
      sql"""select i.id, rla.link_id, rla.name, rla.value, rla.created_date, rla.created_by, rla.modified_date, rla.modified_by
            from #$idTableName i
            join road_link_attributes rla on i.id = rla.link_id and rla.valid_to IS NULL"""
            .as[RoadLinkAttributeInfo].list

    fetchResult.map(row => {
      val rla = (row.name, row.value) match {
        case (Some(name), Some(value)) => Option((name, value))
        case _ => None
      }

      row.id -> rla
    }
    ) ++ getPrivateRoadLastModification(fetchResult)
  }

  private def getPrivateRoadLastModification(fetchResult: Seq[RoadLinkAttributeInfo]): Seq[(Long, Option[(String, String)])] = {
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

  def groupPrivateRoadInformation(results: List[(Long, Option[(String, String)])]): Seq[PrivateRoadInfoStructure] = {
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

  def getRoadLinksHistoryFromVVH(bounds: BoundingRectangle, municipalities: Set[Int] = Set()) : Seq[RoadLink] = {
    val (historyRoadLinks, roadlinks) = Await.result(vvhClient.historyData.fetchByMunicipalitiesAndBoundsF(bounds, municipalities).zip(vvhClient.roadLinkData.fetchByMunicipalitiesAndBoundsF(bounds, municipalities)), atMost = Duration.Inf)
    val linkprocessor = new VVHRoadLinkHistoryProcessor()
    // picks links that are newest in each link chains history with that are with in set tolerance . Keeps ones with no current link
    val filteredHistoryLinks = linkprocessor.process(historyRoadLinks, roadlinks)

    withDynTransaction {
      enrichRoadLinksFromVVH(filteredHistoryLinks)
    }
  }

  def reloadRoadNodesFromVVH(municipality: Int): Seq[VVHRoadNodes] = {
    vvhClient.roadNodesData.fetchByMunicipality(municipality)
  }

  def getHistoryDataLinkFromVVH(linkId: Long, newTransaction: Boolean = true): Option[RoadLink] = getHistoryDataLinksFromVVH(Set(linkId), newTransaction).headOption

  def getHistoryDataLinksFromVVH(linkId: Set[Long], newTransaction: Boolean = true): Seq[RoadLink] = {
    val vvhRoadLinks = fetchHistoryDataLinks(linkId)
    if (newTransaction)
      withDynTransaction {
        enrichRoadLinksFromVVH(vvhRoadLinks)
      }
    else
      enrichRoadLinksFromVVH(vvhRoadLinks)
  }

  def fetchHistoryDataLinks(linkIds: Set[Long]): Seq[VVHRoadlink] = {
    if (linkIds.nonEmpty)
      vvhClient.historyData.fetchByLinkIds(linkIds)
    else
      Seq.empty[VVHRoadlink]
  }


  /**
    * Returns closest road link by user's authorization and point coordinates. Used by Digiroad2Api /servicePoints PUT and /servicePoints/:id PUT endpoints and add_obstacles_shapefile batch.
    */
  def getClosestRoadlinkFromVVH(user: User, point: Point, vectorRadius: Int = 500): Option[VVHRoadlink] = {
    val diagonal = Vector3d(vectorRadius, vectorRadius, 0)

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

  def getClosestRoadlinkForCarTrafficFromVVH(user: User, point: Point): Seq[VVHRoadlink] = {
    val diagonal = Vector3d(10, 10, 0)

    val roadLinks = user.isOperator() match {
        case true =>  getVVHRoadLinks(BoundingRectangle(point - diagonal, point + diagonal))
        case false => getVVHRoadLinks(BoundingRectangle(point - diagonal, point + diagonal), user.configuration.authorizedMunicipalities)
      }

    roadLinks.isEmpty match {
      case true => Seq.empty[VVHRoadlink]
      case false => roadLinks.filter(rl => GeometryUtils.minimumDistance(point, rl.geometry) <= 10.0).filter(_.featureClass != FeatureClass.CycleOrPedestrianPath)
    }
  }

  protected def removeIncompleteness(linkId: Long): Unit = {
    sqlu"""delete from incomplete_link where link_id = $linkId""".execute
  }

  /**
    * Updates road link data in OTH db. Used by Digiroad2Context LinkPropertyUpdater Akka actor.
    */
  def updateRoadLinkChanges(roadLinkChangeSet: RoadLinkChangeSet): Unit = {
    withDynTransaction {
      updateAutoGeneratedProperties(roadLinkChangeSet.adjustedRoadLinks)
      updateIncompleteLinks(roadLinkChangeSet.incompleteLinks)
      fillRoadLinkAttributes(roadLinkChangeSet.roadLinks, roadLinkChangeSet.changes)
    }
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
    def updateProperties(vvhRoadLinks: Seq[VVHRoadlink])(roadLink: RoadLink): Unit = {
      val vvhRoadLink = vvhRoadLinks.find(_.linkId == roadLink.linkId)
      // Separate auto-generated links from change info links: username should be empty for change info links
      val username = createUsernameForAutogenerated(roadLink.modifiedBy)
      val linkProperty = LinkProperties(roadLink.linkId, roadLink.functionalClass, roadLink.linkType, roadLink.trafficDirection, roadLink.administrativeClass)
      vvhRoadLink.foreach {
        vvh =>
          if (roadLink.trafficDirection != TrafficDirection.UnknownDirection) setLinkProperty(RoadLinkDAO.TrafficDirection, linkProperty, username, vvh, None, None)
          if (roadLink.functionalClass != FunctionalClass.Unknown) setLinkProperty(RoadLinkDAO.FunctionalClass, linkProperty, username, vvh, roadLink.modifiedAt, roadLink.modifiedBy)
          if (roadLink.linkType != UnknownLinkType) setLinkProperty(RoadLinkDAO.LinkType, linkProperty, username, vvh, roadLink.modifiedAt, roadLink.modifiedBy)
      }
    }
    val vvhRoadLinks = vvhClient.roadLinkData.fetchByLinkIds(adjustedRoadLinks.map(_.linkId).toSet)

    adjustedRoadLinks.foreach(updateProperties(vvhRoadLinks))
    adjustedRoadLinks.foreach(link =>
      if (link.functionalClass != FunctionalClass.Unknown && link.linkType != UnknownLinkType) removeIncompleteness(link.linkId)
    )
  }

  /**
    * Updates incomplete road link list (incomplete = functional class or link type missing). Used by RoadLinkService.updateRoadLinkChanges.
    */
  protected def updateIncompleteLinks(incompleteLinks: Seq[IncompleteLink]) = {
    def setIncompleteness(incompleteLink: IncompleteLink) {
      sqlu"""insert into incomplete_link(id, link_id, municipality_code, administrative_class)
                 select primary_key_seq.nextval, ${incompleteLink.linkId}, ${incompleteLink.municipalityCode}, ${incompleteLink.administrativeClass.value} from dual
                 where not exists (select * from incomplete_link where link_id = ${incompleteLink.linkId})""".execute
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
    val (roadLinks, _, complementaries) = getCachedRoadLinks(municipality)
    roadLinks ++ complementaries
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
    *  Updates roadLinks attributes based on the changes received
    * @param roadLinks  UpToDate roadLinks
    * @param changes  Change information to treat
    * @param changeUsername  Username applied to this changes
    */
  def fillRoadLinkAttributes(roadLinks: Seq[RoadLink], changes: Seq[ChangeInfo], changeUsername: String = "vvh_change"): Unit = {
    def resolveNewChange(change: ChangeInfo): Unit = {
      val roadLinkAttributesRelatedWithThisChange = LinkAttributesDao.getExistingValues(change.newId.get)
      val roadLinkFind = roadLinks.find(_.linkId == change.newId.get)

      if(roadLinkAttributesRelatedWithThisChange.isEmpty && roadLinkFind.isDefined){
        val roadLink = roadLinkFind.get
        val (startPoint, endPoint) = GeometryUtils.geometryEndpoints(roadLink.geometry)

        val roadLinksAdjFirst = roadLinks.find(link => GeometryUtils.areAdjacent(link.geometry, startPoint) && link != roadLink)
        val roadLinksAdjLast = roadLinks.find(link => GeometryUtils.areAdjacent(link.geometry, endPoint) && link != roadLink)

        if(roadLinksAdjFirst.nonEmpty && roadLinksAdjLast.nonEmpty){
          val attributesFirstRoadLink = LinkAttributesDao.getExistingValues(roadLinksAdjFirst.get.linkId)
          val attributesLastRoadLink = LinkAttributesDao.getExistingValues(roadLinksAdjLast.get.linkId)

          val commonAttributes = returnEqualAttributes(Seq(attributesFirstRoadLink, attributesLastRoadLink))

          commonAttributes.foreach { case (attribute, value) =>
            LinkAttributesDao.insertAttributeValueByChanges(change.newId.get, changeUsername, attribute, value, change.vvhTimeStamp)
          }
        }
      }
    }

    def returnEqualAttributes(oldAttributes: Seq[Map[String, String]]): Map[String, String] = {
      oldAttributes.flatMap { mapToFilter =>
        mapToFilter.filter { case (attr, value) =>
          oldAttributes.forall { oldAttribute =>
            val attribute = oldAttribute.get(attr)
            attribute.nonEmpty && attribute.get == value
          }
        }
      }.toMap
    }

    def resolveChanges(changesToBeProcessed: Seq[ChangeInfo]): Unit = {
      changesToBeProcessed.foreach { change =>
        if (change.oldId.isEmpty) {
          resolveNewChange(change)
        } else {
          val newIdFromVariousOld = changesToBeProcessed.filter(_.newId == change.newId && change.newId.isDefined)
          if (newIdFromVariousOld.size > 1) {
            val oldIdsAttributes = newIdFromVariousOld.map { thisChange =>
              val attributes = LinkAttributesDao.getExistingValues(thisChange.oldId.get, Some(change.vvhTimeStamp))
              if (attributes.nonEmpty)
                LinkAttributesDao.expireValues(thisChange.oldId.get, Some(changeUsername), Some(thisChange.vvhTimeStamp))
              attributes
            }

            returnEqualAttributes(oldIdsAttributes).foreach { case (attribute, value) =>
              LinkAttributesDao.insertAttributeValueByChanges(change.newId.get, changeUsername, attribute, value, change.vvhTimeStamp)
            }
          } else {
            val roadLinkAttributesRelatedWithThisChange = LinkAttributesDao.getExistingValues(change.oldId.get, Some(change.vvhTimeStamp))

            if (roadLinkAttributesRelatedWithThisChange.nonEmpty) {
              val newIdsOfThisOldId = changesToBeProcessed.filter(thisChange => thisChange.oldId == change.oldId && thisChange.newId.isDefined)

              LinkAttributesDao.expireValues(change.oldId.get, Some(changeUsername), Some(change.vvhTimeStamp))
              newIdsOfThisOldId.foreach { changeNewId =>
                roadLinkAttributesRelatedWithThisChange.foreach { case (attribute, value) =>
                  LinkAttributesDao.insertAttributeValueByChanges(changeNewId.newId.get, changeUsername, attribute, value, changeNewId.vvhTimeStamp)
                }
              }
            }
          }
        }
      }
    }

    val changesToBeProcessed = changes.filter(change => change.oldId != change.newId).sortWith(_.vvhTimeStamp < _.vvhTimeStamp)
    resolveChanges(changesToBeProcessed)
  }

  /**
    * This method performs formatting operations to given vvh road links:
    * - auto-generation of functional class and link type by feature class
    * - information transfer from old link to new link from change data
    * It also passes updated links and incomplete links to be saved to db by actor.
    *
    * @param allVvhRoadLinks
    * @param changes
    * @return Road links
    */
  def enrichRoadLinksFromVVH(allVvhRoadLinks: Seq[VVHRoadlink], changes: Seq[ChangeInfo] = Nil): Seq[RoadLink] = {
    val vvhRoadLinks = allVvhRoadLinks.filterNot(_.featureClass == FeatureClass.WinterRoads)
    def autoGenerateProperties(roadLink: RoadLink): RoadLink = {
      val vvhRoadLink = vvhRoadLinks.find(_.linkId == roadLink.linkId)
      vvhRoadLink.get.featureClass match {
        case FeatureClass.TractorRoad => roadLink.copy(functionalClass = 7, linkType = TractorRoad, modifiedBy = Some("automatic_generation"), modifiedAt = Some(DateTimePropertyFormat.print(DateTime.now())))
        case FeatureClass.DrivePath | FeatureClass.CarRoad_IIIb => roadLink.copy(functionalClass = 6, linkType = SingleCarriageway, modifiedBy = Some("automatic_generation"), modifiedAt = Some(DateTimePropertyFormat.print(DateTime.now())))
        case FeatureClass.CycleOrPedestrianPath => roadLink.copy(functionalClass = 8, linkType = CycleOrPedestrianPath, modifiedBy = Some("automatic_generation"), modifiedAt = Some(DateTimePropertyFormat.print(DateTime.now())))
        case FeatureClass.SpecialTransportWithoutGate => roadLink.copy(functionalClass = FunctionalClass.Unknown, linkType = SpecialTransportWithoutGate, modifiedBy = Some("auto_generation"), modifiedAt = Some(DateTimePropertyFormat.print(DateTime.now())))
        case FeatureClass.SpecialTransportWithGate => roadLink.copy(functionalClass = FunctionalClass.Unknown, linkType = SpecialTransportWithGate, modifiedBy = Some("auto_generation"), modifiedAt = Some(DateTimePropertyFormat.print(DateTime.now())))
        case FeatureClass.CarRoad_IIIa => vvhRoadLink.get.administrativeClass match {
          case State => roadLink.copy(functionalClass = 4, linkType = SingleCarriageway, modifiedBy = Some("automatic_generation"), modifiedAt = Some(DateTimePropertyFormat.print(DateTime.now())))
          case Municipality | Private => roadLink.copy(functionalClass = 5, linkType = SingleCarriageway, modifiedBy = Some("automatic_generation"), modifiedAt = Some(DateTimePropertyFormat.print(DateTime.now())))
          case _ => roadLink
        }
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
      RoadLinkChangeSet(autoGeneratedLinks ++ changedLinks ++ changedPartiallyIncompleteLinks, stillIncompleteLinksInUse.map(toIncompleteLink), changes, roadLinkDataByLinkId))

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
        modifiedBy, link.attributes ++ propertyRows.roadLinkAttributesValues(link.linkId), link.constructionType, link.linkSource)
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

    def splitRoadLinkAttributesMap(parameterMap: List[(Long, Option[(String, String)])]) = {
      parameterMap.filter(_._2.nonEmpty).groupBy(_._1).map { case (k, v) => (k, v.map(_._2.get)) }
    }

    MassQuery.withIds(linkIds) {
      idTableName =>
        val (td, fc, lt, ac) = splitMap(fetchOverrides(idTableName))
        val overridedRoadLinkAttributes = splitRoadLinkAttributesMap(fetchOverridedRoadLinkAttributes(idTableName))
        RoadLinkPropertyRows(td, fc, lt, ac, overridedRoadLinkAttributes)
    }
  }

  type RoadLinkId = Long
  type RoadLinkPropertyRow = (Long, Int, DateTime, String)
  type RoadLinkAtributes = Seq[(String, String)]

  case class RoadLinkPropertyRows(trafficDirectionRowsByLinkId: Map[RoadLinkId, RoadLinkPropertyRow],
                                  functionalClassRowsByLinkId: Map[RoadLinkId, RoadLinkPropertyRow],
                                  linkTypeRowsByLinkId: Map[RoadLinkId, RoadLinkPropertyRow],
                                  administrativeClassRowsByLinkId: Map[RoadLinkId, RoadLinkPropertyRow],
                                  roadLinkAttributesByLinkId: Map[RoadLinkId, RoadLinkAtributes]) {

    def roadLinkAttributesValues(linkId: Long): Map[String, String] = {
      roadLinkAttributesByLinkId.get(linkId) match {
        case Some(attributes) => attributes.toMap
        case _ => Map.empty[String, String]
      }
    }

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
    * Returns adjacent road links by link id. Used by Digiroad2Api /roadlinks/adjacent/:id GET endpoint and CsvGenerator.generateDroppedManoeuvres.
    */
  def getAdjacent(linkId: Long, newTransaction: Boolean): Seq[RoadLink] = {
    val sourceRoadLink = getRoadLinksByLinkIdsFromVVH(Set(linkId), newTransaction).headOption
    val sourceLinkGeometryOption = sourceRoadLink.map(_.geometry)
    val sourcePoints = getRoadLinkPoints(sourceRoadLink.get)
    sourceLinkGeometryOption.map(sourceLinkGeometry => {
      val sourceLinkEndpoints = GeometryUtils.geometryEndpoints(sourceLinkGeometry)
      val delta: Vector3d = Vector3d(0.1, 0.1, 0)
      val bounds = BoundingRectangle(sourceLinkEndpoints._1 - delta, sourceLinkEndpoints._1 + delta)
      val bounds2 = BoundingRectangle(sourceLinkEndpoints._2 - delta, sourceLinkEndpoints._2 + delta)
      val roadLinks = getRoadLinksFromVVHByBounds(bounds, bounds2, newTransaction)
      roadLinks.filterNot(_.linkId == linkId)
        .filter(roadLink => roadLink.isCarTrafficRoad)
        .filter(roadLink => {
          val targetLinkGeometry = roadLink.geometry
          GeometryUtils.areAdjacent(sourceLinkGeometry, targetLinkGeometry)
        })
        .filter(roadlink => {
          //It's a valid destination link to turn if the end point of the source exists on the
          //start points of the destination links
          val pointDirections = getRoadLinkPoints(roadlink)
          sourcePoints.exists(sourcePoint => pointDirections.contains(sourcePoint))
        })
    }).getOrElse(Nil)
  }

  def getAdjacent(linkId: Long, sourcePoints: Seq[Point], newTransaction: Boolean = true): Seq[RoadLink] = {
    val sourceRoadLink = getRoadLinksByLinkIdsFromVVH(Set(linkId), newTransaction).headOption
    val sourceLinkGeometryOption = sourceRoadLink.map(_.geometry)
    sourceLinkGeometryOption.map(sourceLinkGeometry => {
      val sourceLinkEndpoints = GeometryUtils.geometryEndpoints(sourceLinkGeometry)
      val delta: Vector3d = Vector3d(0.1, 0.1, 0)
      val bounds = BoundingRectangle(sourceLinkEndpoints._1 - delta, sourceLinkEndpoints._1 + delta)
      val bounds2 = BoundingRectangle(sourceLinkEndpoints._2 - delta, sourceLinkEndpoints._2 + delta)
      val roadLinks = getRoadLinksFromVVHByBounds(bounds, bounds2, newTransaction)
      roadLinks.filterNot(_.linkId == linkId)
        .filter(roadLink => roadLink.isCarTrafficRoad)
        .filter(roadLink => {
          val targetLinkGeometry = roadLink.geometry
          GeometryUtils.areAdjacent(sourceLinkGeometry, targetLinkGeometry)
        })
        .filter(roadlink => {
          val pointDirections = getRoadLinkStartDirectionPoints(roadlink)
          sourcePoints.exists(sourcePoint => pointDirections.contains(sourcePoint))
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

  protected def readCachedGeometry(geometryFile: File): Seq[RoadLink] = {
    def getFeatureClass(roadLink: RoadLink): Int ={
      val mtkClass = roadLink.attributes("MTKCLASS")
      if (mtkClass != null) // Complementary geometries have no MTK Class
        mtkClass.asInstanceOf[BigInt].intValue()
      else
        0
    }

    vvhSerializer.readCachedGeometry(geometryFile).filterNot(r => getFeatureClass(r) == 12312)
  }

  protected def readCachedTinyRoadLinks(geometryFile: File): Seq[TinyRoadLink] = {
    vvhSerializer.readCachedTinyRoadLinks(geometryFile)
  }

  private def getCachedTinyRoadLinks(municipalityCode: Int): Seq[TinyRoadLink] = {
    val dir = getCacheDirectory
    val cachedFiles = getCacheWithComplementaryFiles(municipalityCode, dir)
    cachedFiles match {
      case Some((geometryFile, _, complementaryFile)) =>
        logger.info("Returning cached result")
        readCachedTinyRoadLinks(geometryFile) ++ readCachedTinyRoadLinks(complementaryFile)
      case _ =>
        val (roadLinks, _ , complementaryRoadLink) = getCachedRoadLinks(municipalityCode)
        (roadLinks ++ complementaryRoadLink).map { roadlink =>
          TinyRoadLink(roadlink.linkId)
        }
    }
  }

  private def getCachedRoadLinks(municipalityCode: Int): (Seq[RoadLink], Seq[ChangeInfo], Seq[RoadLink]) = {
    val dir = getCacheDirectory
    val cachedFiles = getCacheWithComplementaryFiles(municipalityCode, dir)
    cachedFiles match {
      case Some((geometryFile, changesFile, complementaryFile)) =>
        logger.info("Returning cached result")
        (readCachedGeometry(geometryFile), vvhSerializer.readCachedChanges(changesFile), readCachedGeometry(complementaryFile))
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
