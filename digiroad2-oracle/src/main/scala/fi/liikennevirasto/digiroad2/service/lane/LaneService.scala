package fi.liikennevirasto.digiroad2.service.lane

import fi.liikennevirasto.digiroad2.GeometryUtils.Projection
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.{ChangeInfo, ChangeType, VVHClient}
import fi.liikennevirasto.digiroad2.dao.lane.{LaneDao, LaneHistoryDao}
import fi.liikennevirasto.digiroad2.dao.{MunicipalityDao, RoadAddressTEMP}
import fi.liikennevirasto.digiroad2.lane.LaneFiller._
import fi.liikennevirasto.digiroad2.lane._
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.{LaneUtils, PolygonTools}
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, GeometryUtils}
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

import java.security.InvalidParameterException
import scala.collection.DebugUtils


case class LaneChange(lane: PersistedLane, oldLane: Option[PersistedLane], changeType: LaneChangeType, roadLink: Option[RoadLink])

class LaneService(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends LaneOperations {
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def vvhClient: VVHClient = roadLinkServiceImpl.vvhClient
  override def dao: LaneDao = new LaneDao(roadLinkServiceImpl.vvhClient, roadLinkServiceImpl)
  override def historyDao: LaneHistoryDao = new LaneHistoryDao(roadLinkServiceImpl.vvhClient, roadLinkServiceImpl)
  override def municipalityDao: MunicipalityDao = new MunicipalityDao
  override def eventBus: DigiroadEventBus = eventBusImpl
  override def polygonTools: PolygonTools = new PolygonTools()

}

trait LaneOperations {

  def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)
  def withDynSession[T](f: => T): T = PostGISDatabase.withDynSession(f)
  def roadLinkService: RoadLinkService
  def vvhClient: VVHClient
  def dao: LaneDao
  def historyDao: LaneHistoryDao
  def municipalityDao: MunicipalityDao
  def eventBus: DigiroadEventBus
  def polygonTools: PolygonTools
  def laneFiller: LaneFiller = new LaneFiller


  val logger = LoggerFactory.getLogger(getClass)
  lazy val VvhGenerated = "vvh_generated"

  case class ActionsPerLanes(lanesToDelete: Set[NewLane] = Set(),
                             lanesToUpdate: Set[NewLane] = Set(),
                             lanesToInsert: Set[NewLane] = Set(),
                             multiLanesOnLink: Set[NewLane] = Set())

  def getByZoomLevel( linkGeomSource: Option[LinkGeomSource] = None) : Seq[Seq[LightLane]] = {
    withDynTransaction {
      val assets = dao.fetchLanes( linkGeomSource)
      Seq(assets)
    }
  }

  /**
    * Returns linear assets for Digiroad2Api /lanes GET endpoint.
    *
    * @param bounds
    * @param municipalities
    * @return
    */
  def getByBoundingBox(bounds: BoundingRectangle, municipalities: Set[Int] = Set(), withWalkingCycling: Boolean = false): (Seq[Seq[PieceWiseLane]], Seq[RoadLink]) = {
    val roadLinks = roadLinkService.getRoadLinksFromVVH(bounds, municipalities)
    val filteredRoadLinks = if (withWalkingCycling) roadLinks else roadLinks.filter(_.functionalClass != WalkingAndCyclingPath.value)
    val linearAssets = getLanesByRoadLinks(filteredRoadLinks)

    val roadLinksWithoutLanes = filteredRoadLinks.filter { link => !linearAssets.exists(_.linkId == link.linkId) }

    val partitionedLanes = LanePartitioner.partition(linearAssets, roadLinks.groupBy(_.linkId).mapValues(_.head))

    val lanes = partitionedLanes.map(_.filter { lane =>
      getPropertyValue(lane, "lane_code") match {
        case Some(laneValue) =>
          LaneNumberOneDigit.isMainLane(laneValue.value.asInstanceOf[Int])
        case _ => false
      }
    })

    (lanes, roadLinksWithoutLanes)
  }

  /**
   * Returns lanes for Digiroad2Api /lanes/viewOnlyLanes GET endpoint.
   * This is only to be used for visualization purposes after the getByBoundingBox ran first
   */
  def getViewOnlyByBoundingBox (bounds :BoundingRectangle, municipalities: Set[Int] = Set(), withWalkingCycling: Boolean = false): Seq[ViewOnlyLane] = {
    val roadLinks = roadLinkService.getRoadLinksFromVVH(bounds, municipalities)
    val filteredRoadLinks = if (withWalkingCycling) roadLinks else roadLinks.filter(_.functionalClass != WalkingAndCyclingPath.value)

    val linkIds = filteredRoadLinks.map(_.linkId)
    val allLanes = fetchExistingLanesByLinkIds(linkIds)

    getSegmentedViewOnlyLanes(allLanes, filteredRoadLinks)
  }

  /**
    * Use lanes measures to create segments with lanes with same link id and side code
    * @param allLanes lanes to be segmented
    * @param roadLinks  roadlinks to relate to a segment piece and use it to construct the segment points
    * @return Segmented view only lanes
    */
  def getSegmentedViewOnlyLanes(allLanes: Seq[PersistedLane], roadLinks: Seq[RoadLink]): Seq[ViewOnlyLane] = {
    //Separate lanes into groups by linkId and side code
    val lanesGroupedByLinkIdAndSideCode = allLanes.groupBy(lane => (lane.linkId, lane.sideCode))

    lanesGroupedByLinkIdAndSideCode.flatMap {
      case ((linkId, sideCode), lanes) =>
        val roadLink = roadLinks.find(_.linkId == linkId).get

        //Find measures for the segments
        val segments = lanes.flatMap(lane => Seq(lane.startMeasure, lane.endMeasure)).distinct

        //Separate each segment in the lanes as a ViewOnlyLane
        segments.sorted.sliding(2).map { measures =>
          val startMeasure = measures.head
          val endMeasure = measures.last
          val consideredLanes = lanes.filter(lane => lane.startMeasure <= startMeasure && lane.endMeasure >= endMeasure).map(_.laneCode)
          val geometry = GeometryUtils.truncateGeometry3D(roadLink.geometry, startMeasure, endMeasure)

          ViewOnlyLane(linkId, startMeasure, endMeasure, sideCode, geometry, consideredLanes)
        }
    }.toSeq
  }

  protected def getLanesByRoadLinks(roadLinks: Seq[RoadLink]): Seq[PieceWiseLane] = {
    val lanes = fetchExistingLanesByLinkIds(roadLinks.map(_.linkId).distinct)
    val lanesMapped = lanes.groupBy(_.linkId)
    val filledTopology = laneFiller.fillTopology(roadLinks, lanesMapped)._1

    filledTopology
  }

   def fillNewRoadLinksWithPreviousAssetsData(roadLinks: Seq[RoadLink], lanesToUpdate: Seq[PersistedLane],
                                                       currentLanes: Seq[PersistedLane], changes: Seq[ChangeInfo], changeSet: ChangeSet) : (Seq[PersistedLane], ChangeSet) ={

    val (replacementChanges, otherChanges) = changes.partition( ChangeType.isReplacementChange)
    val reverseLookupMap = replacementChanges.filterNot(c=>c.oldId.isEmpty || c.newId.isEmpty).map(c => c.newId.get -> c).groupBy(_._1).mapValues(_.map(_._2))

    val extensionChanges = otherChanges.filter(ChangeType.isExtensionChange).flatMap(
      ext => reverseLookupMap.getOrElse(ext.newId.getOrElse(0L), Seq()).flatMap(
        rep => addSourceRoadLinkToChangeInfo(ext, rep)))

    val fullChanges = extensionChanges ++ replacementChanges

    val lanes = mapReplacementProjections(lanesToUpdate, currentLanes, roadLinks, fullChanges).foldLeft((Seq.empty[PersistedLane], changeSet)) {
      case ((persistedAsset, cs), (asset, (Some(roadLink), Some(projection)))) =>
        val (linearAsset, changes) = laneFiller.projectLinearAsset(asset, roadLink, projection, cs)
        (persistedAsset ++ Seq(linearAsset), changes)
      case _ => (Seq.empty[PersistedLane], changeSet)
    }

    lanes
  }





  private def mapReplacementProjections(oldLinearAssets: Seq[PersistedLane], currentLinearAssets: Seq[PersistedLane], roadLinks: Seq[RoadLink], changes: Seq[ChangeInfo]) : Seq[(PersistedLane, (Option[RoadLink], Option[Projection]))] = {

    val targetLinks = changes.flatMap(_.newId).toSet
    val newRoadLinks = roadLinks.filter(rl => targetLinks.contains(rl.linkId)).groupBy(_.linkId)
    val changeMap = changes.filterNot(c => c.newId.isEmpty || c.oldId.isEmpty).map(c => (c.oldId.get, c.newId.get)).groupBy(_._1)
    val targetRoadLinks = changeMap.mapValues(a => a.flatMap(b => newRoadLinks.getOrElse(b._2, Seq())).distinct)
    val groupedLinearAssets = currentLinearAssets.groupBy(_.linkId)
    val groupedOldLinearAssets = oldLinearAssets.groupBy(_.linkId)
    oldLinearAssets.flatMap{asset =>
      targetRoadLinks.getOrElse(asset.linkId, Seq()).map(newRoadLink =>
        (asset,
          getRoadLinkAndProjection(roadLinks, changes, asset.linkId, newRoadLink.linkId, groupedOldLinearAssets, groupedLinearAssets))
      )}
  }

  private def getRoadLinkAndProjection(roadLinks: Seq[RoadLink], changes: Seq[ChangeInfo], oldId: Long, newId: Long,
                                       linearAssetsToUpdate: Map[Long, Seq[PersistedLane]],
                                       currentLinearAssets: Map[Long, Seq[PersistedLane]]): (Option[RoadLink], Option[Projection]) = {
    val roadLink = roadLinks.find(rl => newId == rl.linkId)
    val changeInfo = changes.find(c => c.oldId.getOrElse(0) == oldId && c.newId.getOrElse(0) == newId)
    val projection = changeInfo match {
      case Some(changedPart) =>
        // ChangeInfo object related assets; either mentioned in oldId or in newId
        val linearAssets = (linearAssetsToUpdate.getOrElse(changedPart.oldId.getOrElse(0L), Seq()) ++
          currentLinearAssets.getOrElse(changedPart.newId.getOrElse(0L), Seq())).distinct
        mapChangeToProjection(changedPart, linearAssets)
      case _ => None
    }
    (roadLink,projection)
  }

  private def mapChangeToProjection(change: ChangeInfo, linearAssets: Seq[PersistedLane]): Option[Projection] = {
    val typed = ChangeType.apply(change.changeType)
    typed match {
      // cases 5, 6, 1, 2
      case ChangeType.DividedModifiedPart  | ChangeType.DividedNewPart | ChangeType.CombinedModifiedPart |
           ChangeType.CombinedRemovedPart => projectAssetsConditionally(change, linearAssets, testNoAssetExistsOnTarget, useOldId=false)
      // cases 3, 7, 13, 14
      case ChangeType.LengthenedCommonPart | ChangeType.ShortenedCommonPart | ChangeType.ReplacedCommonPart |
           ChangeType.ReplacedNewPart =>
        projectAssetsConditionally(change, linearAssets, testAssetOutdated, useOldId=false)

      case ChangeType.LengthenedNewPart | ChangeType.ReplacedNewPart =>
        projectAssetsConditionally(change, linearAssets, testAssetsContainSegment, useOldId=true)
      case _ =>
        None
    }
  }

  private def testNoAssetExistsOnTarget(lanes: Seq[PersistedLane], linkId: Long, mStart: Double, mEnd: Double,
                                        vvhTimeStamp: Long): Boolean = {
    !lanes.exists(l => l.linkId == linkId && GeometryUtils.overlaps((l.startMeasure,l.endMeasure),(mStart,mEnd)))
  }

  private def testAssetOutdated(lanes: Seq[PersistedLane], linkId: Long, mStart: Double, mEnd: Double,
                                vvhTimeStamp: Long): Boolean = {
    val targetLanes = lanes.filter(a => a.linkId == linkId)
    targetLanes.nonEmpty && !targetLanes.exists(a => a.vvhTimeStamp >= vvhTimeStamp)
  }

  private def projectAssetsConditionally(change: ChangeInfo, lanes: Seq[PersistedLane],
                                         condition: (Seq[PersistedLane], Long, Double, Double, Long) => Boolean,
                                         useOldId: Boolean): Option[Projection] = {
    val id = if (useOldId) {
                change.oldId
              } else {
                change.newId
              }

    (id, change.oldStartMeasure, change.oldEndMeasure, change.newStartMeasure, change.newEndMeasure, change.vvhTimeStamp) match {
      case (Some(targetId), Some(oldStart:Double), Some(oldEnd:Double),
            Some(newStart:Double), Some(newEnd:Double), vvhTimeStamp) =>

              if (condition(lanes, targetId, oldStart, oldEnd, vvhTimeStamp)) {
                Some(Projection(oldStart, oldEnd, newStart, newEnd, vvhTimeStamp))
              } else {
                None
              }

      case _ => None
    }
  }

  private def testAssetsContainSegment(lanes: Seq[PersistedLane], linkId: Long, mStart: Double, mEnd: Double,
                                       vvhTimeStamp: Long): Boolean = {
    val targetAssets = lanes.filter(a => a.linkId == linkId)
    targetAssets.nonEmpty && !targetAssets.exists(a => a.vvhTimeStamp >= vvhTimeStamp) && targetAssets.exists(
      a => GeometryUtils.covered((a.startMeasure, a.endMeasure),(mStart,mEnd)))
  }

  private def addSourceRoadLinkToChangeInfo(extensionChangeInfo: ChangeInfo, replacementChangeInfo: ChangeInfo) = {
    def givenAndEqualDoubles(v1: Option[Double], v2: Option[Double]) = {
      (v1, v2) match {
        case (Some(d1), Some(d2)) => d1 == d2
        case _ => false
      }
    }

    // Test if these change infos extend each other. Then take the small little piece just after tolerance value to test if it is true there
    val (mStart, mEnd) = (givenAndEqualDoubles(replacementChangeInfo.newStartMeasure, extensionChangeInfo.newEndMeasure),
      givenAndEqualDoubles(replacementChangeInfo.newEndMeasure, extensionChangeInfo.newStartMeasure)) match {
      case (true, false) =>
        (replacementChangeInfo.oldStartMeasure.get + laneFiller.AllowedTolerance,
          replacementChangeInfo.oldStartMeasure.get + laneFiller.AllowedTolerance + laneFiller.MaxAllowedError)
      case (false, true) =>
        (Math.max(0.0, replacementChangeInfo.oldEndMeasure.get - laneFiller.AllowedTolerance - laneFiller.MaxAllowedError),
          Math.max(0.0, replacementChangeInfo.oldEndMeasure.get - laneFiller.AllowedTolerance))
      case (_, _) => (0.0, 0.0)
    }

    if (mStart != mEnd && extensionChangeInfo.vvhTimeStamp == replacementChangeInfo.vvhTimeStamp)
      Option(extensionChangeInfo.copy(oldId = replacementChangeInfo.oldId, oldStartMeasure = Option(mStart), oldEndMeasure = Option(mEnd)))
    else
      None
  }

  def fetchExistingMainLanesByRoadLinks( roadLinks: Seq[RoadLink], removedLinkIds: Seq[Long]): Seq[PersistedLane] = {
    val linkIds = roadLinks.map(_.linkId)

    withDynTransaction {
      dao.fetchLanesByLinkIds( linkIds ++ removedLinkIds,mainLanes = true)
    }.filterNot(_.expired)
  }

  def fetchExistingLanesByLinkIds(linkIds: Seq[Long], removedLinkIds: Seq[Long] = Seq()): Seq[PersistedLane] = {
    withDynTransaction {
      dao.fetchLanesByLinkIds(linkIds ++ removedLinkIds)
    }.filterNot(_.expired)
  }

  def fetchAllLanesByLinkIds(linkIds: Seq[Long]): Seq[PersistedLane] = {
    withDynTransaction {
      dao.fetchAllLanesByLinkIds(linkIds)
    }
  }

  def fetchExistingLanesByLinksIdAndSideCode(linkId: Long, sideCode: Int): Seq[PieceWiseLane] = {
    val roadLink = roadLinkService.getRoadLinkByLinkIdFromVVH(linkId).head

    val existingAssets =
      withDynTransaction {
        dao.fetchLanesByLinkIdAndSideCode(linkId, sideCode)
      }

    existingAssets.map { lane =>
    val geometry = GeometryUtils.truncateGeometry3D(roadLink.geometry, lane.startMeasure, lane.endMeasure)
    val endPoints = GeometryUtils.geometryEndpoints(geometry)

    PieceWiseLane(lane.id, lane.linkId, lane.sideCode, lane.expired, geometry,
      lane.startMeasure, lane.endMeasure,
      Set(endPoints._1, endPoints._2), lane.modifiedBy, lane.modifiedDateTime,
      lane.createdBy, lane.createdDateTime,  roadLink.vvhTimeStamp,
      lane.geomModifiedDate, roadLink.administrativeClass, lane.attributes, attributes = Map("municipality" -> lane.municipalityCode, "trafficDirection" -> roadLink.trafficDirection))
    }
  }

  /**
    * Filter lanes based on their lane code direction
    * @param lanes  lanes to be filtered
    * @param direction  direction to filter (first char of the lane code)
    * @return filtered lanes by lane code direction
    */
  def filterLanesByDirection(lanes: Seq[PersistedLane], direction: Char): Seq[PersistedLane] = {
    lanes.filter(_.laneCode.toString.charAt(0) == direction)
  }

  /**
    * Get lanes that were touched between two dates
    * @param sinceDate  start date
    * @param untilDate  end date
    * @param withAutoAdjust with automatic modified lanes
    * @param token  interval of records
    * @return lanes modified between the dates
    */
  def getChanged(sinceDate: DateTime, untilDate: DateTime, withAutoAdjust: Boolean = false, token: Option[String] = None): Seq[LaneChange] = {
    //Sort changes by id and their created/modified/expired times
    //With this we get a unique ordering with the same values position so the token can be effective
    def customSort(laneChanges: Seq[LaneChange]): Seq[LaneChange] = {
      laneChanges.sortBy{ laneChange =>
        val lane = laneChange.lane
        val defaultTime = DateTime.now()

        (lane.id, lane.createdDateTime.getOrElse(defaultTime).getMillis,
          lane.modifiedDateTime.getOrElse(defaultTime).getMillis, lane.expiredDateTime.getOrElse(defaultTime).getMillis)
      }
    }

    val (upToDateLanes, historyLanes) = withDynTransaction {
      (dao.getLanesChangedSince(sinceDate, untilDate, withAutoAdjust),
        historyDao.getHistoryLanesChangedSince(sinceDate, untilDate, withAutoAdjust))
    }

    val linkIds = (upToDateLanes.map(_.linkId) ++ historyLanes.map(_.linkId)).toSet
    val roadLinks = roadLinkService.getRoadLinksByLinkIdsFromVVH(linkIds)

    val viiteInformation = LaneUtils.roadAddressService.roadLinkWithRoadAddress(roadLinks)
    val vkmInformation = LaneUtils.roadAddressService.roadLinkWithRoadAddressTemp(viiteInformation.filterNot(_.attributes.contains("VIITE_ROAD_NUMBER")))
    val roadLinksWithRoadAddressInfo = viiteInformation.filter(_.attributes.contains("VIITE_ROAD_NUMBER")) ++ vkmInformation


    val upToDateLaneChanges = upToDateLanes.flatMap{ upToDate =>
      val roadLink = roadLinksWithRoadAddressInfo.find(_.linkId == upToDate.linkId)
      val relevantHistory = historyLanes.find(history => history.newId == upToDate.id)

      relevantHistory match {
        case Some(history) =>
          val uptoDateLastModification = historyLanes.filter(historyLane =>
            history.newId == historyLane.oldId && historyLane.newId == 0 &&
              (historyLane.historyCreatedDate.isAfter(history.historyCreatedDate) || historyLane.historyCreatedDate.isEqual(history.historyCreatedDate)))

          if (uptoDateLastModification.nonEmpty && upToDate.laneCode != uptoDateLastModification.minBy(_.historyCreatedDate.getMillis).laneCode)
            Some(LaneChange(upToDate, Some(historyLaneToPersistedLane(uptoDateLastModification.minBy(_.historyCreatedDate.getMillis))), LaneChangeType.LaneCodeTransfer, roadLink))

          else if (historyLanes.count(historyLane => historyLane.newId != 0 && historyLane.oldId == history.oldId) == 2)
            Some(LaneChange(upToDate, Some(historyLaneToPersistedLane(history)), LaneChangeType.Divided, roadLink))

          else if (upToDate.endMeasure - upToDate.startMeasure > history.endMeasure - history.startMeasure)
            Some(LaneChange(upToDate, Some(historyLaneToPersistedLane(history)), LaneChangeType.Lengthened, roadLink))

          else if(upToDate.endMeasure - upToDate.startMeasure < history.endMeasure - history.startMeasure)
            Some(LaneChange(upToDate, Some(historyLaneToPersistedLane(history)), LaneChangeType.Shortened, roadLink))

          else
            None

        case None if upToDate.modifiedDateTime.isEmpty =>
          Some(LaneChange(upToDate, None, LaneChangeType.Add, roadLink))

        case _ =>
          val historyRelatedLanes = historyLanes.filter(_.oldId == upToDate.id)

          if(historyRelatedLanes.nonEmpty){
            val historyLane = historyLaneToPersistedLane(historyRelatedLanes.maxBy(_.historyCreatedDate.getMillis))

            if (isSomePropertyDifferent(historyLane, upToDate.attributes))
              Some(LaneChange(upToDate, Some(historyLane), LaneChangeType.AttributesChanged, roadLink))
            else
              None
          }else{
            None
          }
      }
    }

    val expiredLanes = historyLanes.filter(lane => lane.expired && lane.newId == 0).map{ history =>
      val roadLink = roadLinksWithRoadAddressInfo.find(_.linkId == history.linkId)
      LaneChange(historyLaneToPersistedLane(history), None, LaneChangeType.Expired, roadLink)
    }

    val historyLaneChanges = historyLanes.groupBy(_.oldId).flatMap{ case (_, lanes) =>
      val roadLink = roadLinksWithRoadAddressInfo.find(_.linkId == lanes.head.linkId)

      val lanesSorted = lanes.sortBy(- _.historyCreatedDate.getMillis)
      lanesSorted.foldLeft(Seq.empty[Option[LaneChange]], lanesSorted){ case (foldLeftParameters, lane) =>

        val (treatedLaneChanges, lanesNotTreated) = foldLeftParameters
        val laneAsPersistedLane = historyLaneToPersistedLane(lane)
        val relevantLanes = lanesNotTreated.filterNot(_.id == lane.id)

        val laneChangeReturned = {
          if (relevantLanes.isEmpty) {
            val newIdRelation = historyLanes.find(_.newId == laneAsPersistedLane.id)

            newIdRelation match {
              case Some(relation) if historyLanes.count(historyLane => historyLane.newId != 0 && historyLane.oldId == relation.oldId) == 2 =>
                Some(LaneChange(laneAsPersistedLane, Some(historyLaneToPersistedLane(relation)), LaneChangeType.Divided, roadLink))

              case Some(relation) if laneAsPersistedLane.endMeasure - laneAsPersistedLane.startMeasure > relation.endMeasure - relation.startMeasure =>
                Some(LaneChange(laneAsPersistedLane, Some(historyLaneToPersistedLane(relation)), LaneChangeType.Lengthened, roadLink))

              case Some(relation) if laneAsPersistedLane.endMeasure - laneAsPersistedLane.startMeasure < relation.endMeasure - relation.startMeasure =>
                Some(LaneChange(laneAsPersistedLane, Some(historyLaneToPersistedLane(relation)), LaneChangeType.Shortened, roadLink))

              case _ =>
                val wasCreateInTimePeriod = lane.modifiedDateTime.isEmpty &&
                  (lane.createdDateTime.get.isAfter(sinceDate) || lane.createdDateTime.get.isEqual(sinceDate))

                if (wasCreateInTimePeriod)
                  Some(LaneChange(laneAsPersistedLane, None, LaneChangeType.Add, roadLink))
                else
                  None
            }
          } else {
            val relevantLane = historyLaneToPersistedLane(relevantLanes.maxBy(_.historyCreatedDate.getMillis))

            if (isSomePropertyDifferent(relevantLane, laneAsPersistedLane.attributes))
              Some(LaneChange(laneAsPersistedLane, Some(relevantLane), LaneChangeType.AttributesChanged, roadLink))
            else
              None
          }
        }

        (treatedLaneChanges :+ laneChangeReturned, relevantLanes)

      }._1.flatten
    }

    val relevantLanesChanged = (upToDateLaneChanges ++ expiredLanes ++ historyLaneChanges).filter(_.roadLink.isDefined)
    val sortedLanesChanged = customSort(relevantLanesChanged)

    token match {
      case Some(tk) =>
        val (start, end) = Decode.getPageAndRecordNumber(tk)

        sortedLanesChanged.slice(start - 1, end)
      case _ => sortedLanesChanged
    }
  }

  def historyLaneToPersistedLane(historyLane: PersistedHistoryLane): PersistedLane = {
    PersistedLane(historyLane.oldId, historyLane.linkId, historyLane.sideCode, historyLane.laneCode,
                  historyLane.municipalityCode, historyLane.startMeasure, historyLane.endMeasure, historyLane.createdBy,
                  historyLane.createdDateTime, historyLane.modifiedBy, historyLane.modifiedDateTime,
                  Some(historyLane.historyCreatedBy), Some(historyLane.historyCreatedDate),
                  historyLane.expired, historyLane.vvhTimeStamp, historyLane.geomModifiedDate, historyLane.attributes)
  }

  def fixSideCode( roadAddress: RoadAddressTEMP , laneCode: String ): SideCode = {
    // Need to pay attention of the SideCode and laneCode. This will have influence in representation of the lane
    roadAddress.sideCode match {
      case Some(SideCode.AgainstDigitizing) =>
        if (laneCode.startsWith("1")) SideCode.AgainstDigitizing else SideCode.TowardsDigitizing
      case Some(SideCode.TowardsDigitizing) =>
        if (laneCode.startsWith("1")) SideCode.TowardsDigitizing else SideCode.AgainstDigitizing
      case _ => SideCode.BothDirections
    }
  }

  /**
    * Returns lanes ids. Used by Digiroad2Api /lane POST and /lane DELETE endpoints.
    */
  def getPersistedLanesByIds( ids: Set[Long], newTransaction: Boolean = true): Seq[PersistedLane] = {
    if(newTransaction)
      withDynTransaction {
        dao.fetchLanesByIds( ids )
      }
    else
      dao.fetchLanesByIds( ids )
  }

  def getPropertyValue(newLaneProperties: Seq[LaneProperty], publicId: String) = {
    val laneProperty = newLaneProperties.find(_.publicId == publicId)

    laneProperty match {
      case Some(prop) if prop.values.nonEmpty =>
        prop.values.head.value
      case _ => None
    }
  }

  def getPropertyValue(pwLane: PieceWiseLane, publicIdToSearch: String): Option[LanePropertyValue] = {
    pwLane.laneAttributes.find(_.publicId == publicIdToSearch).get.values.headOption
  }

  def getPropertyValue(pLane: PersistedLane, publicIdToSearch: String): Option[LanePropertyValue] = {
    pLane.attributes.find(_.publicId == publicIdToSearch).get.values.headOption
  }

  def getLaneCode (newLane: NewLane): String = {
    val laneCodeValue = getPropertyValue(newLane.properties, "lane_code")

    if (laneCodeValue != None && laneCodeValue.toString.trim.nonEmpty)
      laneCodeValue.toString.trim
    else
      throw new IllegalArgumentException("Lane code attribute not found!")
  }

  def validateStartDateOneDigit(newLane: NewLane, laneCode: Int): Unit = {
    if (!LaneNumberOneDigit.isMainLane(laneCode)) {
      val startDateValue = getPropertyValue(newLane.properties, "start_date")
      if (startDateValue == None || startDateValue.toString.trim.isEmpty)
        throw new IllegalArgumentException("Start Date attribute not found on additional lane!")
    }
  }

  def validateStartDate(newLane: NewLane, laneCode: Int): Unit = {
    if (!LaneNumber.isMainLane(laneCode)) {
      val startDateValue = getPropertyValue(newLane.properties, "start_date")
      if (startDateValue == None || startDateValue.toString.trim.isEmpty)
        throw new IllegalArgumentException("Start Date attribute not found on additional lane!")
    }
  }

  def isSomePropertyDifferent(oldLane: PersistedLane, newLaneProperties: Seq[LaneProperty]): Boolean = {
    oldLane.attributes.length != newLaneProperties.length ||
      oldLane.attributes.exists { property =>
        val oldPropertyValue = if (property.values.nonEmpty) {property.values.head.value} else None
        val newPropertyValue = getPropertyValue(newLaneProperties, property.publicId)

        oldPropertyValue != newPropertyValue
      }
  }

  /**
    * @param updateNewLane The lanes that will be updated
    * @param linkIds  If only 1 means the user are change a specific lane and it can be cut or properties update
    *                 If multiple means the chain is being updated and only properties are change ( Lane size / cut cannot be done)
    * @param sideCode To know the direction
    * @param username Username of the user is doing the changes
    * @return
    */
  def update(updateNewLane: Seq[NewLane], linkIds: Set[Long], sideCode: Int, username: String): Seq[Long] = {
    if (updateNewLane.isEmpty || linkIds.isEmpty)
      return Seq()

    val allExistingLanes = dao.fetchLanesByLinkIdsAndLaneCode(linkIds.toSeq)

    updateNewLane.flatMap { laneToUpdate =>
      val originalLane = allExistingLanes.find(_.id == laneToUpdate.id)
      val laneToUpdateCode = getLaneCode(laneToUpdate).toInt

      linkIds.map{ linkId =>
        val laneRelatedByUpdatedLaneCode = allExistingLanes.find(laneAux => laneAux.laneCode == laneToUpdateCode && laneAux.linkId == linkId)
          .getOrElse(throw new InvalidParameterException(s"LinkId: $linkId dont have laneCode: $laneToUpdateCode for update!"))

        originalLane match {
          case Some(lane) =>
            //oldLane is the lane related with the laneToUpdate by Id, when various links we need to find this lane Id and lane code
            val oldLane = allExistingLanes.find(laneAux => laneAux.laneCode == lane.laneCode && laneAux.linkId == linkId)
              .getOrElse(throw new InvalidParameterException(s"LinkId: $linkId dont have laneCode: ${lane.laneCode} for update!"))

            if (linkIds.size == 1 &&
              (oldLane.startMeasure != laneToUpdate.startMeasure || oldLane.endMeasure != laneToUpdate.endMeasure)) {
              val newLaneID = create(Seq(laneToUpdate), Set(linkId), sideCode, username)
              moveToHistory(oldLane.id, Some(newLaneID.head), true, true, username)
              newLaneID.head
            } else if (oldLane.laneCode != laneToUpdateCode || isSomePropertyDifferent(oldLane, laneToUpdate.properties)) {
              //Something changed on properties or lane code
              val persistedLaneToUpdate = PersistedLane(oldLane.id, linkId, sideCode, laneToUpdateCode, oldLane.municipalityCode,
                oldLane.startMeasure, oldLane.endMeasure, Some(username), None, None, None, None, None, false, 0, None, laneToUpdate.properties)

              if (oldLane.laneCode != laneToUpdateCode)
                moveToHistory(laneRelatedByUpdatedLaneCode.id, Some(oldLane.id), true, true, username)

              moveToHistory(oldLane.id, None, false, false, username)
              dao.updateEntryLane(persistedLaneToUpdate, username)

            } else {
              oldLane.id
            }
          case _ if laneToUpdate.id == 0 =>
            //User deleted and created another lane in same lane code
            //so it will be a update, if have some modification, and not a expire and then create

            if (linkIds.size == 1 &&
              (laneRelatedByUpdatedLaneCode.startMeasure != laneToUpdate.startMeasure || laneRelatedByUpdatedLaneCode.endMeasure != laneToUpdate.endMeasure)) {
              val newLaneID = create(Seq(laneToUpdate), Set(linkId), sideCode, username)
              moveToHistory(laneRelatedByUpdatedLaneCode.id, Some(newLaneID.head), true, true, username)
              newLaneID.head
            } else if(isSomePropertyDifferent(laneRelatedByUpdatedLaneCode, laneToUpdate.properties)){
              val persistedLaneToUpdate = PersistedLane(laneRelatedByUpdatedLaneCode.id, linkId, sideCode, laneToUpdateCode, laneToUpdate.municipalityCode,
                laneToUpdate.startMeasure, laneToUpdate.endMeasure, Some(username), None, None, None, None, None, false, 0, None, laneToUpdate.properties)

              moveToHistory(laneRelatedByUpdatedLaneCode.id, None, false, false, username)
              dao.updateEntryLane(persistedLaneToUpdate, username)
            } else {
              laneRelatedByUpdatedLaneCode.id
            }
          case _ => throw new InvalidParameterException(s"Error: Could not find lane with lane Id ${laneToUpdate.id}!")
        }
      }
    }
  }

  def createWithoutTransaction(newLane: PersistedLane, username: String): Long = {
    val laneId = dao.createLane( newLane, username )

    newLane.attributes match {
      case props: Seq[LaneProperty] =>
        props.filterNot( _.publicId == "lane_code" )
             .map( attr => dao.insertLaneAttributes(laneId, attr, username) )

      case _ => None
    }

    laneId
  }


  def validateMinDistance(measure1: Double, measure2: Double): Boolean = {
    val minDistanceAllow = 0.01
    val (maxMeasure, minMeasure) = (math.max(measure1, measure2), math.min(measure1, measure2))
    (maxMeasure - minMeasure) > minDistanceAllow
  }

  /**
    * Saves new linear assets from UI. Used by Digiroad2Api /linearassets POST endpoint.
    */
  def create(newLanes: Seq[NewLane], linkIds: Set[Long], sideCode: Int, username: String, vvhTimeStamp: Long = vvhClient.roadLinkData.createVVHTimeStamp()): Seq[Long] = {

    if (newLanes.isEmpty || linkIds.isEmpty)
      return Seq()

      // if it is only 1 link it can be just a new lane with same size as the link or it can be a cut
      // for that reason we need to use the measure that come inside the newLane
      if (linkIds.size == 1) {

        val linkId = linkIds.head

        newLanes.map { newLane =>
          val laneCode = getLaneCode(newLane)
          validateStartDateOneDigit(newLane, laneCode.toInt)

          val laneToInsert = PersistedLane(0, linkId, sideCode, laneCode.toInt, newLane.municipalityCode,
                                      newLane.startMeasure, newLane.endMeasure, Some(username), Some(DateTime.now()), None, None, None, None,
                                      expired = false, vvhTimeStamp, None, newLane.properties)

          createWithoutTransaction(laneToInsert, username)
        }

      } else {
        // If we have more than 1 linkId than we have a chain selected
        // Lanes will be created with the size of the link
        val viiteRoadLinks = LaneUtils.viiteClient.fetchAllByLinkIds(linkIds.toSeq)
                                                  .groupBy(_.linkId)

        val result = linkIds.map { linkId =>

          newLanes.map { newLane =>

            val laneCode = getLaneCode(newLane)
            validateStartDateOneDigit(newLane, laneCode.toInt)

            val roadLink = if (viiteRoadLinks(linkId).nonEmpty)
                            viiteRoadLinks(linkId).head
                           else
                            throw new InvalidParameterException(s"No RoadLink found: $linkId")

            val laneToInsert = PersistedLane(0, linkId, sideCode, laneCode.toInt, newLane.municipalityCode,
                                  roadLink.startMValue,roadLink.endMValue, Some(username), Some(DateTime.now()), None, None, None, None,
                                  expired = false, vvhTimeStamp, None, newLane.properties)

            createWithoutTransaction(laneToInsert, username)
          }
        }

        result.head
      }
  }



  def updatePersistedLaneAttributes( id: Long, attributes: Seq[LaneProperty], username: String): Unit = {
    attributes.foreach{ prop =>
      if (prop.values.isEmpty) dao.deleteLaneAttribute(id, prop)
      else dao.updateLaneAttributes(id, prop, username)
    }
  }

  /**
    * Delete lanes with specified laneCodes on linkIds given
    * @param laneCodes  lanes codes to delete
    * @param linkIds  linkIds where are the lanes to be expired
    * @param username username of the one who expired the lanes
    */
  def deleteMultipleLanes(laneCodes: Set[Int], linkIds: Set[Long], username: String): Seq[Long] = {
    if (laneCodes.nonEmpty) {
      val allExistingLanes = dao.fetchLanesByLinkIdsAndLaneCode(linkIds.toSeq, laneCodes.toSeq)
      allExistingLanes.map(_.id).map(moveToHistory(_, None, true, true, username))
    } else
      Seq()
  }

  def moveToHistory(oldId: Long, newId: Option[Long], expireHistoryLane: Boolean = false, deleteFromLanes: Boolean = false,
                    username: String): Long = {
    val historyLaneId = historyDao.insertHistoryLane(oldId, newId, username)

    if (expireHistoryLane)
      historyDao.expireHistoryLane(historyLaneId, username)

    if (deleteFromLanes)
      dao.deleteEntryLane(oldId)

    historyLaneId
  }

  def processNewLanes(newLanes: Set[NewLane], linkIds: Set[Long],
                      sideCode: Int, username: String): Seq[Long] = {
    withDynTransaction {
      val actionsLanes = separateNewLanesInActions(newLanes, linkIds, sideCode)
      val laneCodesToBeDeleted = actionsLanes.lanesToDelete.map(getLaneCode(_).toInt)

      create(actionsLanes.lanesToInsert.toSeq, linkIds, sideCode, username) ++
      update(actionsLanes.lanesToUpdate.toSeq, linkIds, sideCode, username) ++
      deleteMultipleLanes(laneCodesToBeDeleted, linkIds, username) ++
      createMultiLanesOnLink(actionsLanes.multiLanesOnLink.toSeq, linkIds, sideCode, username)
    }
  }

  def separateNewLanesInActions(newLanes: Set[NewLane], linkIds: Set[Long], sideCode: Int): ActionsPerLanes = {
    //Get Current Lanes for sended linkIds
    val allExistingLanes: Seq[PersistedLane] = dao.fetchLanesByLinkIdAndSideCode(linkIds.head, sideCode)

    //Get Lanes to be deleted
    val resultWithDeleteActions = newLanes.foldLeft(ActionsPerLanes()) {
      (result, existingLane) =>
        if (existingLane.isExpired)
          result.copy(lanesToDelete = Set(existingLane) ++ result.lanesToDelete)
        else
          result
    }

    //Get multiple lanes in one link
    val resultWithMultiLanesInLink = newLanes.filter(_.isExpired != true).foldLeft(resultWithDeleteActions) {
      (result, newLane) =>
        val newLaneCode: Int = getPropertyValue(newLane.properties, "lane_code").toString.toInt

        val numberOfOldLanesByCode = allExistingLanes.count(_.laneCode == newLaneCode)
        val numberOfFutureLanesByCode = newLanes.filter(_.isExpired != true).count { newLane => getLaneCode(newLane).toInt == newLaneCode }

        if ((numberOfFutureLanesByCode == 2 && numberOfOldLanesByCode >= 1) || (numberOfFutureLanesByCode == 1 && numberOfOldLanesByCode == 2))
          result.copy(multiLanesOnLink = Set(newLane) ++ result.multiLanesOnLink)
        else
          result
    }

    //Get Lanes to be created and updated
    val resultWithAllActions =
      newLanes.filter(_.isExpired != true).diff(resultWithMultiLanesInLink.multiLanesOnLink)
        .foldLeft(resultWithMultiLanesInLink) {
          (result, lane) =>
            val laneCode = getPropertyValue(lane.properties, "lane_code")

            // If new Lane already exist at data base will be marked to be updated
            if (allExistingLanes.exists(_.laneCode == laneCode)) {
              result.copy(lanesToUpdate = Set(lane) ++ result.lanesToUpdate)
            } else {
              // If new Lane doesnt exist at data base will be marked to be created
              result.copy(lanesToInsert = Set(lane) ++ result.lanesToInsert)
            }
        }

    resultWithAllActions
  }

  def createMultiLanesOnLink(updateNewLanes: Seq[NewLane], linkIds: Set[Long], sideCode: Int, username: String): Seq[Long] = {
    // Get all lane codes from lanes to update
    val laneCodesToModify = updateNewLanes.map { newLane => getLaneCode(newLane).toInt }

    //Fetch from db the existing lanes
    val oldLanes = dao.fetchLanesByLinkIdsAndLaneCode(linkIds.toSeq, laneCodesToModify)

    val newLanesByLaneCode = updateNewLanes.groupBy(il => getLaneCode(il).toInt)

    //By lane check if exist something to modify
    newLanesByLaneCode.flatMap { case (laneCode, lanesToUpdate) =>
      val oldLanesByCode = oldLanes.filter(_.laneCode == laneCode)


      if (lanesToUpdate.size == 2 && oldLanesByCode.size == 1) {
        //When its to create two lanes in one link
        val newLanesIDs = lanesToUpdate.map { lane =>
          create(Seq(lane), linkIds, sideCode, username).head
        }

        newLanesIDs.foreach { newLane =>
          moveToHistory(oldLanesByCode.head.id, Some(newLane), true, false, username)
        }
        dao.deleteEntryLane(oldLanesByCode.head.id)

        newLanesIDs

      } else if (lanesToUpdate.size == 1 && oldLanesByCode.size == 2) {
        //When its to transform two lanes in one on same roadlink
        val newLanesIDs = lanesToUpdate.map { lane =>
          create(Seq(lane), linkIds, sideCode, username).head
        }

        oldLanesByCode.foreach { oldLane =>
          moveToHistory(oldLane.id, Some(newLanesIDs.head), true, true, username)
        }

        newLanesIDs

      } else {
        //When its to update both lanes in one link
        oldLanesByCode.map { oldLane =>
          val newDataToUpdate = lanesToUpdate.filter(_.id == oldLane.id).head

          if (isSomePropertyDifferent(oldLane, newDataToUpdate.properties)) {
            val persistedLaneToUpdate = PersistedLane(newDataToUpdate.id, linkIds.head, sideCode, oldLane.laneCode, newDataToUpdate.municipalityCode,
              newDataToUpdate.startMeasure, newDataToUpdate.endMeasure, Some(username), None, None, None, None, None, false, 0, None, newDataToUpdate.properties)

            moveToHistory(oldLane.id, None, false, false, username)
            dao.updateEntryLane(persistedLaneToUpdate, username)

          } else {
            oldLane.id
          }
        }
      }
    }.toSeq
  }


  def expireAllLanesInStateRoad(username: String): Any = {
    val existingLinkIds = dao.getAllLinkIdsInLanePosition()
    val stateRoadLinks = roadLinkService.getRoadsLinksFromVVH(existingLinkIds.toSet, false)
                                      .filter(_.administrativeClass == State)

    dao.expireLanesByLinkId( stateRoadLinks.map(_.linkId).toSet, username)
  }

  def expireAllAdditionalLanes(username: String): Any = {
    dao.expireAdditionalLanes(username)
  }

  // Used by initial main lane population process
  def expireAllMunicipalityLanes(municipality: Int, username: String): Unit = {
    val lanes = dao.fetchLanesByMunicipality(municipality)
    val lanesWithHistoryId = lanes.map(_.id)
                                  .map(lane => (lane, historyDao.insertHistoryLane(lane, None, username)))

    historyDao.expireHistoryLanes(lanesWithHistoryId.map(_._2), username)
    dao.deleteEntryLanes(lanesWithHistoryId.map(_._1))
  }

}