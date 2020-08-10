package fi.liikennevirasto.digiroad2.service.lane

import java.security.InvalidParameterException

import com.jolbox.bonecp.{BoneCPConfig, BoneCPDataSource}
import fi.liikennevirasto.digiroad2.GeometryUtils.Projection
import fi.liikennevirasto.digiroad2.asset.{TrafficDirection, _}
import fi.liikennevirasto.digiroad2.client.vvh.{ChangeInfo, ChangeType, VVHClient}
import fi.liikennevirasto.digiroad2.dao.{MunicipalityDao, RoadAddressTEMP}
import fi.liikennevirasto.digiroad2.dao.lane.{LaneDao, LaneHistoryDao}
import fi.liikennevirasto.digiroad2.lane.LaneFiller._
import fi.liikennevirasto.digiroad2.lane.LaneNumber.{FourthRightAdditional, MainLane}
import fi.liikennevirasto.digiroad2.lane._
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.LaneUtils.roadLinkTempDAO
import fi.liikennevirasto.digiroad2.util.{LaneUtils, PolygonTools, Track}
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, GeometryUtils}
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

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

  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  def roadLinkService: RoadLinkService
  def vvhClient: VVHClient
  def dao: LaneDao
  def historyDao: LaneHistoryDao
  def municipalityDao: MunicipalityDao
  def eventBus: DigiroadEventBus
  def polygonTools: PolygonTools
  def laneFiller: LaneFiller = new LaneFiller

  lazy val dataSource = {
    val cfg = new BoneCPConfig(OracleDatabase.loadProperties("/bonecp.properties"))
    new BoneCPDataSource(cfg)
  }

  val logger = LoggerFactory.getLogger(getClass)
  lazy val VvhGenerated = "vvh_generated"

  case class ActionsPerLanes(lanesToDelete: Set[NewIncomeLane] = Set(),
                             lanesToUpdate: Set[NewIncomeLane] = Set(),
                             lanesToInsert: Set[NewIncomeLane] = Set(),
                             multiLanesOnLink: Set[NewIncomeLane] = Set())

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
  def getByBoundingBox(bounds: BoundingRectangle, municipalities: Set[Int] = Set()): Seq[Seq[PieceWiseLane]] = {
    val (roadLinks, change) = roadLinkService.getRoadLinksAndChangesFromVVH(bounds, municipalities)
    val linearAssets = getLanesByRoadLinks(roadLinks, change)

    val partitionedLanes = LanePartitioner.partition(linearAssets, roadLinks.groupBy(_.linkId).mapValues(_.head))

    partitionedLanes.map(_.filter { lane =>
      getPropertyValue(lane, "lane_code") match {
        case Some(laneValue) =>
          LaneNumber.isMainLane(laneValue.value.asInstanceOf[Int])
        case _ => false
      }
    })
  }

  // Validate if lane.SideCode is ok with roadAddress.SideCode.
  // adjustLanesSideCodes function will validate that
  def checkSideCodes (roadLinks: Seq[RoadLink], changedSet: ChangeSet, allLanes: Seq[PersistedLane] ): (Seq[PersistedLane], ChangeSet) = {

    val linkIds = roadLinks.map(_.linkId)
    val roadAddresses = LaneUtils.viiteClient.fetchAllByLinkIds(linkIds)
                                              .map( elem => RoadAddressTEMP (elem.linkId, elem.roadNumber, elem.roadPartNumber, elem.track,
                                                elem.startAddrMValue, elem.endAddrMValue, elem.startMValue, elem.endMValue,elem.geom, Some(elem.sideCode), Some(0) )
                                              )
    val roadAddressesGrouped = roadAddresses.groupBy(_.linkId)

    val vkmRoadAddress = withDynSession {
                                roadLinkTempDAO.getByLinkIds(linkIds.toSet)
                          }
    val allRoadAddresses = vkmRoadAddress.filterNot(addr => roadAddressesGrouped(addr.linkId).nonEmpty) ++ roadAddresses

    roadLinks.foldLeft(allLanes, changedSet) {
      case ((lanes,changedSet), roadLink) =>
        adjustLanesSideCodes(roadLink, lanes, changedSet, allRoadAddresses)
    }
  }

  protected def getLanesByRoadLinks(roadLinks: Seq[RoadLink], changes: Seq[ChangeInfo]): Seq[PieceWiseLane] = {
    val mappedChanges = LaneUtils.getMappedChanges(changes)
    val removedLinkIds = LaneUtils.deletedRoadLinkIds(mappedChanges, roadLinks.map(_.linkId).toSet)
    val existingAssets = fetchExistingLanesByLinkIds(roadLinks.map(_.linkId).distinct, removedLinkIds)

    val timing = System.currentTimeMillis
    val (assetsOnChangedLinks, lanesWithoutChangedLinks) = existingAssets.partition(a => LaneUtils.newChangeInfoDetected(a, mappedChanges))

    val initChangeSet = ChangeSet(
      expiredLaneIds = existingAssets.filter(asset => removedLinkIds.contains(asset.linkId)) // Get only the assets marked to remove
                                      .map(_.id)                                             // Get only the Ids
                                      .toSet
                                      .filterNot( _ == 0L)                                   // Remove the new assets (ID == 0 )
                      )

    val (projectedLanes, changedSet) = fillNewRoadLinksWithPreviousAssetsData(roadLinks, assetsOnChangedLinks, assetsOnChangedLinks, changes, initChangeSet)
    val newLanes = projectedLanes ++ lanesWithoutChangedLinks

    if (newLanes.nonEmpty) {
      logger.info("Finnish transfer %d assets at %d ms after start".format(newLanes.length, System.currentTimeMillis - timing))
    }

    val allLanes = assetsOnChangedLinks.filterNot(a => projectedLanes.exists(_.linkId == a.linkId)) ++ projectedLanes ++ lanesWithoutChangedLinks
    val roadLinksToAdjust = roadLinks.filter( road =>  allLanes.exists(_.linkId == road.linkId))

    val (lanesAfterSideCodeAdjust, changeSetInterm) = checkSideCodes(roadLinksToAdjust, changedSet, allLanes)

    val groupedAssets = lanesAfterSideCodeAdjust.groupBy(_.linkId)
    val (filledTopology, changeSet) = laneFiller.fillTopology(roadLinks, groupedAssets, Some(changeSetInterm) )

    val generatedMappedById = changeSet.generatedPersistedLanes.groupBy(_.id)
    val modifiedLanes = projectedLanes.filterNot(lane => generatedMappedById(lane.id).nonEmpty) ++ changeSet.generatedPersistedLanes

    publish(eventBus, changeSet, modifiedLanes)
    filledTopology
  }

  def publish(eventBus: DigiroadEventBus, changeSet: ChangeSet, modifiedLanes: Seq[PersistedLane]) {
    eventBus.publish("lanes:updater", changeSet)
    eventBus.publish("lanes:saveModifiedLanes", modifiedLanes.filter(_.id == 0L))
  }


  protected def fillNewRoadLinksWithPreviousAssetsData(roadLinks: Seq[RoadLink], lanesToUpdate: Seq[PersistedLane],
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


  /*
   * Based on track we need to know what laneCode is correct or not
   * This will return the correct MainLane Code and the range of lanes Code (start and end) to remove
   */
  private def getLaneDirectionOkAndToRemove (filteredRoadAddresses: Option[RoadAddressTEMP], trafficDirection: TrafficDirection): (Int, (Int, Int)) = {

    val towardsOkAndAgainstWrong = (MainLane.towardsDirection, (MainLane.againstDirection, FourthRightAdditional.againstDirection))
    val againstOkAndTowardsWrong = (MainLane.againstDirection, (MainLane.towardsDirection,FourthRightAdditional.towardsDirection) )

    def decodeBasedOnTraffic(sideCode: Option[SideCode]): (Int, (Int, Int)) = {
      sideCode match {
        case Some(SideCode.TowardsDigitizing) => if (trafficDirection == TrafficDirection.TowardsDigitizing)
                                                  towardsOkAndAgainstWrong
                                                else
                                                  againstOkAndTowardsWrong

        case Some(SideCode.AgainstDigitizing) => if (trafficDirection == TrafficDirection.AgainstDigitizing)
                                                  towardsOkAndAgainstWrong
                                                else
                                                  againstOkAndTowardsWrong

        case _ => towardsOkAndAgainstWrong
      }
    }

    filteredRoadAddresses match {
      case Some(roadAddress) => roadAddress.track match {
                    /* If Track is Right Side it means the AET and LET are increasing */
                    case Track.RightSide => towardsOkAndAgainstWrong

                    /* If Track is Right Side it means the AET and LET are decreasing */
                    case Track.LeftSide => againstOkAndTowardsWrong

                    /* If Track is combined we need to check the traffic direction (obtain by the roadlink) */
                    case Track.Combined => decodeBasedOnTraffic(roadAddress.sideCode)

                    /* If something went wrong...*/
                    case _ =>
                      (MainLane.towardsDirection, (MainLane.motorwayMaintenance, MainLane.motorwayMaintenance) )
      }
      case _ => towardsOkAndAgainstWrong
    }

  }


  def adjustLanesSideCodes(roadLink: RoadLink, lanes: Seq[PersistedLane], changeSet: ChangeSet, roadAddresses: Seq[RoadAddressTEMP]): (Seq[PersistedLane], ChangeSet) = {

    // auxiliary function to create PersistedLane object
    def createPersistedLane(laneCode: Int, sideCode: Int, municipalityCode: Long, baseProperties: Seq[LaneProperty]): PersistedLane = {
      val lanePropertiesValues = baseProperties ++ Seq(LaneProperty("lane_code", Seq(LanePropertyValue(laneCode))))

      PersistedLane(0L, roadLink.linkId, sideCode, laneCode, municipalityCode,
        0, roadLink.length, None, None, None, None, None, None, expired = false, roadLink.vvhTimeStamp, None,
        lanePropertiesValues)
    }

    //from the lanes get those that need a sideCode adjust and apply that adjust
    def editLanesNeedSideCodeAdjust(lanes: Seq[PersistedLane], laneCodeStart: Int, laneCodeEnd: Int, sideCode: SideCode): Seq[PersistedLane] = {
      lanes.filter { lane =>
        val isLaneCodeAllowed = lane.laneCode >= laneCodeStart && lane.laneCode <= laneCodeEnd
        val isSideCodeNOK = lane.sideCode != sideCode.value

        isLaneCodeAllowed && isSideCodeNOK
      }.map(_.copy(sideCode = sideCode.value))
    }

    /* Based on main lane number, return the range of possible lanes number.
      ex: input: 11  || output (11,19)
     */
    def getLaneNumbersRange(laneNumber: Int): (Int, Int) = {
      val laneNum = LaneNumber(laneNumber)

      if (laneNumber == laneNum.againstDirection)
        (laneNum.againstDirection, FourthRightAdditional.againstDirection)
      else
        (laneNum.towardsDirection, FourthRightAdditional.towardsDirection)
    }


    /*******
      ** Main Block
      *******/
    if (lanes.isEmpty)
      return (lanes, changeSet)

    val filteredRoadAddresses = roadAddresses.find(_.linkId == roadLink.linkId)

    val (mainLane11SideCode, mainLane21SideCode) = filteredRoadAddresses match {
      case Some(roadAddress) =>
        (fixSideCode(roadAddress, MainLane.towardsDirection.toString),
          fixSideCode(roadAddress, MainLane.againstDirection.toString))

      case _ =>
        (SideCode.TowardsDigitizing, SideCode.AgainstDigitizing)
    }


    val (mainLaneDirectionOK, mainLaneDirectionRemove) = getLaneDirectionOkAndToRemove(filteredRoadAddresses,roadLink.trafficDirection)

    val lanesToProcess = lanes.filter(_.linkId == roadLink.linkId)
    val baseLane = lanesToProcess.minBy(_.laneCode)
    val baseProps = baseLane.attributes.filterNot(_.publicId == "lane_code")

    roadLink.trafficDirection match {
      case TrafficDirection.BothDirections =>
        val mainLanes = (lanesToProcess.exists(lane => lane.laneCode == MainLane.towardsDirection),
          lanesToProcess.exists(lane => lane.laneCode == MainLane.againstDirection))

        val (toAdd, adjustedSideCode) = mainLanes match {
          // case not exist main lane 11
          case (false, true) =>
            val missingLane = Seq(createPersistedLane(MainLane.towardsDirection, mainLane11SideCode.value, baseLane.municipalityCode, baseProps))
            val adjustedSideCode = editLanesNeedSideCodeAdjust(lanesToProcess, MainLane.againstDirection, FourthRightAdditional.againstDirection, mainLane21SideCode)

            (missingLane, adjustedSideCode)

          // case not exist main lane 21
          case (true, false) =>
            val missingLane = Seq(createPersistedLane(MainLane.againstDirection, mainLane21SideCode.value, baseLane.municipalityCode, baseProps))
            val adjustedSideCode = editLanesNeedSideCodeAdjust(lanesToProcess, MainLane.towardsDirection, FourthRightAdditional.towardsDirection, mainLane11SideCode)

            (missingLane, adjustedSideCode)

          // case not exist both main lanes
          case (false, false) =>
            val missingLanes = Seq(createPersistedLane(MainLane.towardsDirection, mainLane11SideCode.value, baseLane.municipalityCode, baseProps),
              createPersistedLane(MainLane.againstDirection, mainLane21SideCode.value, baseLane.municipalityCode, baseProps))
            (missingLanes, Seq())

          // case both main lanes exists
          case _ =>
            val adjustedSideCode11 = editLanesNeedSideCodeAdjust(lanesToProcess, MainLane.towardsDirection, FourthRightAdditional.towardsDirection, mainLane11SideCode)
            val adjustedSideCode21 = editLanesNeedSideCodeAdjust(lanesToProcess, MainLane.againstDirection, FourthRightAdditional.againstDirection, mainLane21SideCode)

            (Seq(), adjustedSideCode11 ++ adjustedSideCode21)
        }

        val updatedLanes = (lanes ++ toAdd).filterNot(lane => adjustedSideCode.exists(_.id == lane.id)) ++ adjustedSideCode

        val lanesWithSideCodeAdjustment = adjustedSideCode.map(lane => SideCodeAdjustment(lane.id, SideCode(lane.sideCode)))
        val newChangeSet = changeSet.copy(generatedPersistedLanes = updatedLanes,
          adjustedSideCodes = changeSet.adjustedSideCodes ++ lanesWithSideCodeAdjustment)

        (updatedLanes, newChangeSet)

      case TrafficDirection.TowardsDigitizing | TrafficDirection.AgainstDigitizing  =>
        val (laneCodeStart, laneCodeEnd) = getLaneNumbersRange(mainLaneDirectionOK)

        val (toAdd, adjustedSideCode) = if (lanesToProcess.exists(lane => lane.laneCode == mainLaneDirectionOK))
          (Seq(), editLanesNeedSideCodeAdjust(lanesToProcess, laneCodeStart, laneCodeEnd, SideCode.BothDirections))
        else
          (Seq(createPersistedLane(mainLaneDirectionOK, SideCode.BothDirections.value, baseLane.municipalityCode, baseProps)), Seq())

        val lanesWithSideCodeAdjustment = adjustedSideCode.map(lane => SideCodeAdjustment(lane.id, SideCode.BothDirections))

        val toRemove = lanesToProcess.filter { lane =>
          //all lanes with lane code opposite are to be deleted
          val isValidLaneId = lane.id != 0L
          val isLaneCodeToRemove = lane.laneCode >= mainLaneDirectionRemove._1 && lane.laneCode <= mainLaneDirectionRemove._2

          isValidLaneId && isLaneCodeToRemove
        }
          .map(_.id)


        val updatedLanes = (lanes ++ toAdd).filterNot(lane => toRemove.contains(lane.id) || adjustedSideCode.exists(_.id == lane.id)) ++ adjustedSideCode
        val newChangeSet = changeSet.copy(generatedPersistedLanes = updatedLanes,
          expiredLaneIds = changeSet.expiredLaneIds ++ toRemove,
          adjustedSideCodes = changeSet.adjustedSideCodes ++ lanesWithSideCodeAdjustment)

        (updatedLanes, newChangeSet)

      case _ => (lanes, changeSet)
    }
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

  def persistModifiedLinearAssets(newLanes: Seq[PersistedLane]): Unit = {
    if (newLanes.nonEmpty) {
      logger.info("Saving modified lanes")

      val username = "modifiedLanes"
      val (toInsert, toUpdate) = newLanes.partition(_.id == 0L)

      withDynTransaction {
        if(toUpdate.nonEmpty) {
          toUpdate.foreach{ lane =>
            moveToHistory(lane.id, None, false, false, username)
            dao.updateEntryLane(lane, username)
          }
          logger.info("Updated ids/linkids " + toUpdate.map(a => (a.id, a.linkId)))
        }
        if (toInsert.nonEmpty){
          toInsert.foreach(createWithoutTransaction(_ , username))
          logger.info("Added lanes for linkids " + toInsert.map(_.linkId))
        }
      }
    }
  }


  def fixSideCode( roadAddress: RoadAddressTEMP , laneCode: String ): SideCode = {
    roadAddress.track.value match {
      case 1 | 2 => SideCode.BothDirections // This means the road may have both ways with something between them (like highways or similar)
                                            // The representation of the lane will be in the middle 'of the road'

      case _ => roadAddress.sideCode match { // In this case the road have both ways 'connected' so we need to take attention of the SideCode and laneCode
                                             // This will have influence in representation of the lane
                      case Some(SideCode.AgainstDigitizing) => if (laneCode.startsWith("1")) SideCode.AgainstDigitizing
                                                                else SideCode.TowardsDigitizing

                      case Some(SideCode.TowardsDigitizing) => if (laneCode.startsWith("1")) SideCode.TowardsDigitizing
                                                                else SideCode.AgainstDigitizing

                      case _ => SideCode.BothDirections
                    }
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
      .getOrElse(throw new IllegalArgumentException(s"Attribute '$publicId' not found!"))

    if (laneProperty.values.nonEmpty)
      laneProperty.values.head.value
    else
      None
  }

  def getPropertyValue(pwLane: PieceWiseLane, publicIdToSearch: String): Option[LanePropertyValue] = {
    pwLane.laneAttributes.find(_.publicId == publicIdToSearch).get.values.headOption
  }

  def getLaneCode (newIncomeLane: NewIncomeLane): String = {
    val laneCodeValue = getPropertyValue(newIncomeLane.properties, "lane_code")

    if (laneCodeValue != None && laneCodeValue.toString.trim.nonEmpty)
      laneCodeValue.toString.trim
    else
      throw new IllegalArgumentException("Lane code attribute not found!")
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
    * @param updateIncomeLane The lanes that will be updated
    * @param linkIds  If only 1 means the user are change a specific lane and it can be cut or properties update
    *                 If multiple means the chain is being updated and only properties are change ( Lane size / cut cannot be done)
    * @param sideCode To know the direction
    * @param username Username of the user is doing the changes
    * @return
    */
  def update(updateIncomeLane: Seq[NewIncomeLane], linkIds: Set[Long], sideCode: Int, username: String): Seq[Long] = {
    if (updateIncomeLane.isEmpty || linkIds.isEmpty)
      return Seq()

    val allExistingLanes = dao.fetchLanesByLinkIdsAndLaneCode(linkIds.toSeq)

    updateIncomeLane.flatMap { laneToUpdate =>
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
  def create(newIncomeLane: Seq[NewIncomeLane], linkIds: Set[Long] ,sideCode: Int, username: String, vvhTimeStamp: Long = vvhClient.roadLinkData.createVVHTimeStamp()): Seq[Long] = {

    if (newIncomeLane.isEmpty || linkIds.isEmpty)
      return Seq()

      // if it is only 1 link it can be just a new lane with same size as the link or it can be a cut
      // for that reason we need to use the measure that come inside the newIncomeLane
      if (linkIds.size == 1) {

        val linkId = linkIds.head

        newIncomeLane.map { newLane =>
          val laneCode = newLane.properties.find(_.publicId == "lane_code")
                                          .getOrElse(throw new IllegalArgumentException("Lane Code attribute not found!"))

          val laneToInsert = PersistedLane(0, linkId, sideCode, laneCode.values.head.value.toString.toInt, newLane.municipalityCode,
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

          newIncomeLane.map { newLane =>

            val laneCode = newLane.properties.find(_.publicId == "lane_code")
                                             .getOrElse(throw new IllegalArgumentException("Lane Code attribute not found!"))

            val roadLink = if (viiteRoadLinks(linkId).nonEmpty)
                            viiteRoadLinks(linkId).head
                           else
                            throw new InvalidParameterException(s"No RoadLink found: $linkId")

            val laneToInsert = PersistedLane(0, linkId, sideCode, laneCode.values.head.value.toString.toInt, newLane.municipalityCode,
                                  roadLink.startMValue,roadLink.endMValue, Some(username), Some(DateTime.now()), None, None, None, None,
                                  expired = false, vvhTimeStamp, None, newLane.properties)

            createWithoutTransaction(laneToInsert, username)
          }
        }

        result.head
      }
  }

  def updateChangeSet(changeSet: ChangeSet) : Unit = {
    def treatChangeSetData(changeSetToTreat: Seq[baseAdjustment]): Unit = {
      val toAdjustLanes = getPersistedLanesByIds(changeSetToTreat.map(_.laneId).toSet, false)

      changeSetToTreat.foreach { adjustment =>
        val oldLane = toAdjustLanes.find(_.id == adjustment.laneId).get
        val newIncomeLane = persistedToIncomeWithNewMeasures(oldLane, adjustment.startMeasure, adjustment.endMeasure)
        val newLaneID = create(Seq(newIncomeLane), Set(oldLane.linkId), oldLane.sideCode, VvhGenerated)
        moveToHistory(oldLane.id, Some(newLaneID.head), true, true, VvhGenerated)
      }
    }

    def persistedToIncomeWithNewMeasures(persistedLane: PersistedLane, newStartMeasure: Double, newEndMeasure: Double): NewIncomeLane = {
      NewIncomeLane(0, newStartMeasure, newEndMeasure, persistedLane.municipalityCode, false, false, persistedLane.attributes)
    }

    withDynTransaction {
      if (changeSet.adjustedMValues.nonEmpty)
        logger.info("Saving adjustments for lane/link ids=" + changeSet.adjustedMValues.map(a => "" + a.laneId + "/" + a.linkId).mkString(", "))

      treatChangeSetData(changeSet.adjustedMValues)

      if (changeSet.adjustedVVHChanges.nonEmpty)
        logger.info("Saving adjustments for lane/link ids=" + changeSet.adjustedVVHChanges.map(a => "" + a.laneId + "/" + a.linkId).mkString(", "))

      treatChangeSetData(changeSet.adjustedVVHChanges)

      if (changeSet.adjustedSideCodes.nonEmpty)
        logger.info("Saving SideCode adjustments for lane/link ids=" + changeSet.adjustedSideCodes.map(a => "" + a.laneId).mkString(", "))

      changeSet.adjustedSideCodes.foreach { adjustment =>
        moveToHistory(adjustment.laneId, None, false, false, VvhGenerated)
        dao.updateSideCode(adjustment.laneId, adjustment.sideCode.value, VvhGenerated)
      }

      val ids = changeSet.expiredLaneIds.toSeq
      if (ids.nonEmpty)
        logger.info("Expiring ids " + ids.mkString(", "))
      ids.foreach(moveToHistory(_, None, true, true, VvhGenerated))
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

  def processNewIncomeLanes(newIncomeLanes: Set[NewIncomeLane], linkIds: Set[Long],
                            sideCode: Int, username: String): Seq[Long] = {
    withDynTransaction {
      val actionsLanes = separateNewIncomeLanesInActions(newIncomeLanes, linkIds, sideCode)
      val laneCodesToBeDeleted = actionsLanes.lanesToDelete.map(getLaneCode(_).toInt)

      create(actionsLanes.lanesToInsert.toSeq, linkIds, sideCode, username) ++
      update(actionsLanes.lanesToUpdate.toSeq, linkIds, sideCode, username) ++
      deleteMultipleLanes(laneCodesToBeDeleted, linkIds, username) ++
      createMultiLanesOnLink(actionsLanes.multiLanesOnLink.toSeq, linkIds, sideCode, username)
    }
  }

  def separateNewIncomeLanesInActions(newIncomeLanes: Set[NewIncomeLane], linkIds: Set[Long], sideCode: Int): ActionsPerLanes = {
    //Get Current Lanes for sended linkIds
    val allExistingLanes: Seq[PersistedLane] = dao.fetchLanesByLinkIdAndSideCode(linkIds.head, sideCode)

    //Get Lanes to be deleted
    val resultWithDeleteActions = newIncomeLanes.foldLeft(ActionsPerLanes()) {
      (result, existingLane) =>
        if (existingLane.isExpired)
          result.copy(lanesToDelete = Set(existingLane) ++ result.lanesToDelete)
        else
          result
    }

    //Get multiple lanes in one link
    val resultWithMultiLanesInLink = newIncomeLanes.filter(_.isExpired != true).foldLeft(resultWithDeleteActions) {
      (result, incomeLane) =>
        val incomeLaneCode: Int = getPropertyValue(incomeLane.properties, "lane_code").toString.toInt

        val numberOfOldLanesByCode = allExistingLanes.count(_.laneCode == incomeLaneCode)
        val numberOfFutureLanesByCode = newIncomeLanes.filter(_.isExpired != true).count { incomeLane => getLaneCode(incomeLane).toInt == incomeLaneCode }

        if ((numberOfFutureLanesByCode == 2 && numberOfOldLanesByCode >= 1) || (numberOfFutureLanesByCode == 1 && numberOfOldLanesByCode == 2))
          result.copy(multiLanesOnLink = Set(incomeLane) ++ result.multiLanesOnLink)
        else
          result
    }

    //Get Lanes to be created and updated
    val resultWithAllActions =
      newIncomeLanes.filter(_.isExpired != true).diff(resultWithMultiLanesInLink.multiLanesOnLink)
        .foldLeft(resultWithMultiLanesInLink) {
          (result, lane) =>
            val laneCode = getPropertyValue(lane.properties, "lane_code")

            // If new Income Lane already exist at data base will be marked to be updated
            if (allExistingLanes.exists(_.laneCode == laneCode)) {
              result.copy(lanesToUpdate = Set(lane) ++ result.lanesToUpdate)
            } else {
              // If new Income Lane doesnt exist at data base will be marked to be created
              result.copy(lanesToInsert = Set(lane) ++ result.lanesToInsert)
            }
        }

    resultWithAllActions
  }

  def createMultiLanesOnLink(updateIncomeLane: Seq[NewIncomeLane], linkIds: Set[Long], sideCode: Int, username: String): Seq[Long] = {
    // Get all lane codes from lanes to update
    val laneCodesToModify = updateIncomeLane.map { incomeLane => getLaneCode(incomeLane).toInt }

    //Fetch from db the existing lanes
    val oldLanes = dao.fetchLanesByLinkIdsAndLaneCode(linkIds.toSeq, laneCodesToModify)

    val incomeLanesByLaneCode = updateIncomeLane.groupBy(il => getLaneCode(il).toInt)

    //By lane check if exist something to modify
    incomeLanesByLaneCode.flatMap { case (laneCode, lanesToUpdate) =>
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

}