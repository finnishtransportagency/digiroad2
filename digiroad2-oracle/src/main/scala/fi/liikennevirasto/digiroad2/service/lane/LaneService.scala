package fi.liikennevirasto.digiroad2.service.lane

import java.security.InvalidParameterException

import com.jolbox.bonecp.{BoneCPConfig, BoneCPDataSource}
import fi.liikennevirasto.digiroad2.GeometryUtils.Projection
import fi.liikennevirasto.digiroad2.asset.{TrafficDirection, _}
import fi.liikennevirasto.digiroad2.client.vvh.{ChangeInfo, ChangeType, VVHClient}
import fi.liikennevirasto.digiroad2.dao.{MunicipalityDao, RoadAddressTEMP}
import fi.liikennevirasto.digiroad2.dao.lane.LaneDao
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


class LaneService(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends LaneOperations {
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def vvhClient: VVHClient = roadLinkServiceImpl.vvhClient
  override def dao: LaneDao = new LaneDao(roadLinkServiceImpl.vvhClient, roadLinkServiceImpl)
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

    roadLinks.foldLeft(Seq.empty[PersistedLane], changedSet) {
      case ((_,changedSet), roadLink) =>
        adjustLanesSideCodes(roadLink, allLanes, changedSet, allRoadAddresses)
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

    val (lanesAfterSideCodeAdjust, changeSetInterm) = checkSideCodes(roadLinksToAdjust, changedSet, allLanes )

    val groupedAssets = lanesAfterSideCodeAdjust.groupBy(_.linkId)
    val (filledTopology, changeSet) = laneFiller.fillTopology(roadLinks, groupedAssets, Some(changeSetInterm) )

    val generatedMappedById = changeSet.generatedPersistedLanes.groupBy(_.id)
    val modifiedLanes = projectedLanes.filterNot {lane => generatedMappedById(lane.id).nonEmpty } ++ changeSet.generatedPersistedLanes

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
        0, roadLink.length, None, None, None, None, expired = false, roadLink.vvhTimeStamp, None,
        lanePropertiesValues)
    }

    // auxiliary function to find lanes which can have the correct SideCode or is BothDirection
    def getMainLaneForBothDirection(filteredLanes: Seq[PersistedLane], laneCode: Int, sideCode: Int): Option[PersistedLane] = {
      filteredLanes.find { lane =>
        val isLaneCodeTowards = lane.laneCode == laneCode
        val isSideCodeOK = lane.sideCode == sideCode || lane.sideCode == SideCode.BothDirections.value

        isLaneCodeTowards && isSideCodeOK
      }
    }

    def getLaneCodesForSideCodeAdjust(lanes: Seq[PersistedLane], laneCodeStart: Int, laneCodeEnd: Int, sideCode: SideCode ): Seq[SideCodeAdjustment] = {
      lanes.filter { lane =>
                    val isLaneCodeAllowed = lane.laneCode >= laneCodeStart && lane.laneCode <= laneCodeEnd
                    val isSideCodeNOK = lane.sideCode != sideCode.value

                    isLaneCodeAllowed && isSideCodeNOK
             }
             .map(lane => SideCodeAdjustment(lane.id, sideCode) )
    }

    /* Based on main lane number, return the range of possible lanes number.
      ex: input: 11  || output (11,19)
     */
    def getLaneNumbersRange(laneNumber: Int): (Int, Int) = {
      val laneNum = LaneNumber(laneNumber)

      if (laneNumber == laneNum.againstDirection)
        (laneNum.againstDirection, FourthRightAdditional.againstDirection)
      else if (laneNumber == laneNum.towardsDirection)
        (laneNum.towardsDirection, FourthRightAdditional.towardsDirection)
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


    val (mainLaneDirectionOK, mainLaneDirectionRemove) = getLaneDirectionOkAndToRemove( filteredRoadAddresses,roadLink.trafficDirection )

    val lanesToProcess = lanes.filter(_.linkId == roadLink.linkId)
    val baseLane = lanesToProcess.minBy(_.laneCode)
    val baseProps = baseLane.attributes.filterNot(_.publicId == "lane_code")

    roadLink.trafficDirection match {

      case TrafficDirection.BothDirections =>

        val mainLanes = ( getMainLaneForBothDirection(lanesToProcess, MainLane.towardsDirection, mainLane11SideCode.value).getOrElse(false),
                          getMainLaneForBothDirection(lanesToProcess, MainLane.againstDirection, mainLane21SideCode.value).getOrElse(false) )

        val (toAdd, toAdjustSideCode) = mainLanes match {
         // case not exist main lane 11
          case (false, _: PersistedLane) =>
            val missingLane = Seq(createPersistedLane(MainLane.towardsDirection, mainLane11SideCode.value, baseLane.municipalityCode, baseProps))
            val needAdjustSideCode = getLaneCodesForSideCodeAdjust(lanesToProcess, MainLane.againstDirection, FourthRightAdditional.againstDirection, mainLane21SideCode)

            (missingLane, needAdjustSideCode)


          // case not exist main lane 21
          case (_: PersistedLane, false) =>
            val missingLane = Seq(createPersistedLane(MainLane.againstDirection, mainLane21SideCode.value, baseLane.municipalityCode, baseProps))
            val needAdjustSideCode = getLaneCodesForSideCodeAdjust(lanesToProcess, MainLane.towardsDirection, FourthRightAdditional.towardsDirection, mainLane11SideCode)

            (missingLane, needAdjustSideCode)


          // case not exist both main lanes
          case (false, false) =>
            val missingLanes = Seq(createPersistedLane(MainLane.towardsDirection, mainLane11SideCode.value, baseLane.municipalityCode, baseProps),
                                    createPersistedLane(MainLane.againstDirection, mainLane21SideCode.value, baseLane.municipalityCode, baseProps))
            (missingLanes, Seq())


          // case both main lanes exists
          case _ =>
            val needAdjustSideCodeLane11 = getLaneCodesForSideCodeAdjust(lanesToProcess, MainLane.towardsDirection, FourthRightAdditional.towardsDirection, mainLane11SideCode)
            val needAdjustSideCodeLane21 = getLaneCodesForSideCodeAdjust(lanesToProcess, MainLane.againstDirection, FourthRightAdditional.againstDirection, mainLane21SideCode)

            (Seq(), needAdjustSideCodeLane11 ++ needAdjustSideCodeLane21)
        }

        // To remove the lanes with wrong SideCode
        val toRemove = lanesToProcess.filterNot(_.id == 0L)
                                    .filter { lane =>
                                      val isLaneCodeTowards = lane.laneCode >= MainLane.towardsDirection && lane.laneCode <= FourthRightAdditional.towardsDirection
                                      val isSideCodeTowardsOk = lane.sideCode != mainLane11SideCode.value && lane.sideCode != SideCode.BothDirections.value

                                      val isLaneCodeAgainst = lane.laneCode >= MainLane.againstDirection && lane.laneCode <= FourthRightAdditional.againstDirection
                                      val isSideCodeAgainstOk = lane.sideCode != mainLane21SideCode.value && lane.sideCode != SideCode.BothDirections.value

                                      (isLaneCodeTowards && isSideCodeTowardsOk ) || (isLaneCodeAgainst && isSideCodeAgainstOk)
                                     }
                                    .map(_.id)

        val lanesToAdd = (lanes ++ toAdd).filterNot(lane => toRemove.contains(lane.id))

        val newChangeSet = changeSet.copy(generatedPersistedLanes = changeSet.generatedPersistedLanes ++ lanesToAdd,
                                    expiredLaneIds = changeSet.expiredLaneIds ++ toRemove,
                                    adjustedSideCodes = changeSet.adjustedSideCodes ++ toAdjustSideCode )

        (lanesToAdd, newChangeSet)

      case TrafficDirection.TowardsDigitizing =>



        val toAdd = if (!lanesToProcess.exists(lane => lane.laneCode == mainLaneDirectionOK))
                      Seq(createPersistedLane(mainLaneDirectionOK, SideCode.BothDirections.value, baseLane.municipalityCode, baseProps))
                    else
                      Seq()

        val (laneCodeStart, laneCodeEnd) = getLaneNumbersRange(mainLaneDirectionOK)
        val needAdjustSideCode = getLaneCodesForSideCodeAdjust(lanesToProcess, laneCodeStart, laneCodeEnd, SideCode.BothDirections)

        val toRemove = lanesToProcess.filterNot(_.id == 0L)
                                    .filter(lane => (lane.laneCode >= mainLaneDirectionRemove._1 && lane.laneCode <= mainLaneDirectionRemove._2) ||
                                              lane.sideCode != SideCode.BothDirections.value)
                                    .map(_.id)


        val lanesToAdd = (lanes ++ toAdd).filterNot(lane => toRemove.contains(lane.id))
        val newChangeSet = changeSet.copy(generatedPersistedLanes = changeSet.generatedPersistedLanes ++ lanesToAdd,
                                    expiredLaneIds = changeSet.expiredLaneIds ++ toRemove,
                                    adjustedSideCodes = changeSet.adjustedSideCodes ++ needAdjustSideCode )

        (lanesToAdd, newChangeSet)


      case TrafficDirection.AgainstDigitizing =>

        val toAdd = if (!lanesToProcess.exists(lane => lane.laneCode == mainLaneDirectionOK))
                      Seq(createPersistedLane(mainLaneDirectionOK, SideCode.BothDirections.value, baseLane.municipalityCode, baseProps))
                    else
                      Seq()

        val (laneCodeStart, laneCodeEnd) = getLaneNumbersRange(mainLaneDirectionOK)
        val needAdjustSideCode = getLaneCodesForSideCodeAdjust(lanesToProcess, laneCodeStart, laneCodeEnd, SideCode.BothDirections)

        val toRemove = lanesToProcess.filterNot(_.id == 0L)
                                    .filter(lane => (lane.laneCode >= mainLaneDirectionRemove._1 && lane.laneCode <= mainLaneDirectionRemove._2) ||
                                              lane.sideCode != SideCode.BothDirections.value)
                                    .map(_.id)


        val lanesToAdd = (lanes ++ toAdd).filterNot(lane => toRemove.contains(lane.id))
        val newChangeSet = changeSet.copy(generatedPersistedLanes = changeSet.generatedPersistedLanes ++ lanesToAdd,
                                    expiredLaneIds = changeSet.expiredLaneIds ++ toRemove,
                                    adjustedSideCodes = changeSet.adjustedSideCodes ++ needAdjustSideCode )

        (lanesToAdd, newChangeSet)

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

  def persistModifiedLinearAssets(newLanes: Seq[PersistedLane]): Unit ={
    if (newLanes.nonEmpty) {
      logger.info("Saving modified lanes")

      val username = "modifiedLanes"
      val (toInsert, toUpdate) = newLanes.partition(_.id == 0L)

      withDynTransaction {
        if(toUpdate.nonEmpty) {
          updatePersistedLanes(toUpdate, username)
          logger.info("Updated ids/linkids " + toUpdate.map(a => (a.id, a.linkId)))
        }
        if (toInsert.nonEmpty){
          toInsert.foreach{ lane =>
            createWithoutTransaction( lane , username)
          }
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

  def getPropertyValue(newLane: NewIncomeLane, publicId: String) = {
    val laneProperty = newLane.properties.find(_.publicId == publicId)
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
    val laneCodeValue = getPropertyValue(newIncomeLane, "lane_code")

    if (laneCodeValue != None && laneCodeValue.toString.trim.nonEmpty)
      laneCodeValue.toString.trim
    else
      throw new IllegalArgumentException("Lane code attribute not found!")
  }

  /**
    * @param newIncomeLane The lanes that will be updated
    * @param linkIds  If only 1 means the user are change a specific lane and it can be cut or properties update
    *                 If multiple means the chain is being updated and only properties are change ( Lane size / cut cannot be done)
    * @param sideCode To know the direction
    * @param username Username of the user is doing the changes
    * @return
    */
  def update(newIncomeLane: Seq[NewIncomeLane], linkIds: Set[Long], sideCode: Int, username: String, newTransaction: Boolean = true): Seq[Long] = {
    def updateProcess(): Seq[Long] = {
      if (linkIds.size == 1) {
        val linkId = linkIds.head

        newIncomeLane.map { lane =>
          val laneToUpdate = PersistedLane(lane.id, linkId, sideCode, getLaneCode(lane).toInt, lane.municipalityCode,
            lane.startMeasure, lane.endMeasure, Some(username), None, None, None, false, 0, None, lane.properties)

          dao.updateEntryLane(laneToUpdate, username)
        }

      } else {
        val allExistingLanes = dao.fetchLanesByLinkIdsAndLaneCode(linkIds.toSeq)

        newIncomeLane.flatMap{ lane =>
          //we need original lane code so we can propagate the update to the other lanes on other links
          val originalLaneCode = allExistingLanes.find(_.id == lane.id)
                                                  .getOrElse(throw new InvalidParameterException(s"Error: Could not find lane with lane Id ${lane.id}!"))
                                                  .laneCode

          linkIds.map{ linkId =>
            val currentLane = allExistingLanes.find(laneAux => laneAux.laneCode == originalLaneCode && laneAux.linkId == linkId)
                                                .getOrElse(throw new InvalidParameterException(s"LinkId: $linkId dont have laneCode: $originalLaneCode for update!"))

            val laneToUpdate = PersistedLane(currentLane.id, linkId, sideCode, getLaneCode(lane).toInt, currentLane.municipalityCode,
              currentLane.startMeasure, currentLane.endMeasure, Some(username), None, None, None, false, 0, None, lane.properties)

            dao.updateEntryLane(laneToUpdate, username)
          }
        }
      }
    }

    if (newIncomeLane.isEmpty || linkIds.isEmpty)
      return Seq()

    if(newTransaction)
      withDynTransaction {
        updateProcess()
      }
    else
      updateProcess()
  }

  def updatePersistedLanes ( lanes: Seq[PersistedLane], username: String ): Seq[Long] = {
    withDynTransaction {
      lanes.map { lane =>
        dao.updateEntryLane(lane, username)
      }
    }
  }

  def createWithoutTransaction( newLane: PersistedLane, username: String ): Long = {

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
  def create(newIncomeLane: Seq[NewIncomeLane], linkIds: Set[Long] ,sideCode: Int, username: String, vvhTimeStamp: Long = vvhClient.roadLinkData.createVVHTimeStamp(), newTransaction: Boolean = true): Seq[Long] = {
    def createProcess(): Seq[Long] = {
      // if it is only 1 link it can be just a new lane with same size as the link or it can be a cut
      // for that reason we need to use the measure that come inside the newIncomeLane
      if (linkIds.size == 1) {

        val linkId = linkIds.head

        newIncomeLane.map { newLane =>
          val laneCode = newLane.properties.find(_.publicId == "lane_code")
                                          .getOrElse(throw new IllegalArgumentException("Lane Code attribute not found!"))

          val laneToInsert = PersistedLane(0, linkId, sideCode, laneCode.values.head.value.toString.toInt, newLane.municipalityCode,
                                      newLane.startMeasure, newLane.endMeasure, Some(username), Some(DateTime.now()), None, None,
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
                                  roadLink.startMValue,roadLink.endMValue, Some(username), Some(DateTime.now()), None, None,
                                  expired = false, vvhTimeStamp, None, newLane.properties)

            createWithoutTransaction(laneToInsert, username)
          }
        }

        result.head
      }
    }

    if (newIncomeLane.isEmpty || linkIds.isEmpty)
      return Seq()

    if(newTransaction)
      withDynTransaction {
        createProcess()
      }
    else
      createProcess()
  }

  def updateChangeSet(changeSet: ChangeSet) : Unit = {
    withDynTransaction {

      if (changeSet.adjustedMValues.nonEmpty)
        logger.info("Saving adjustments for lane/link ids=" + changeSet.adjustedMValues.map(a => "" + a.laneId + "/" + a.linkId).mkString(", "))

      changeSet.adjustedMValues.foreach { adjustment =>
        dao.updateMValues(adjustment.laneId, (adjustment.startMeasure, adjustment.endMeasure), VvhGenerated)
      }

      if (changeSet.adjustedVVHChanges.nonEmpty)
        logger.info("Saving adjustments for lane/link ids=" + changeSet.adjustedVVHChanges.map(a => "" + a.laneId + "/" + a.linkId).mkString(", "))

      changeSet.adjustedVVHChanges.foreach { adjustment =>
        dao.updateMValues(adjustment.laneId, (adjustment.startMeasure, adjustment.endMeasure), VvhGenerated, adjustment.vvhTimestamp)
      }

      val ids = changeSet.expiredLaneIds.toSeq
      if (ids.nonEmpty)
        logger.info("Expiring ids " + ids.mkString(", "))
      ids.foreach(id => dao.updateExpiration(id, VvhGenerated))

      if (changeSet.adjustedSideCodes.nonEmpty)
        logger.info("Saving SideCode adjustments for lane/link ids=" + changeSet.adjustedSideCodes.map(a => "" + a.laneId).mkString(", "))

      changeSet.adjustedSideCodes.foreach { adjustment =>
        adjustedSideCode(adjustment)
      }

      if (changeSet.valueAdjustments.nonEmpty)
        logger.info("Saving value adjustments for assets: " + changeSet.valueAdjustments.map(a => "" + a.lane.id).mkString(", "))
      changeSet.valueAdjustments.foreach { adjustment =>
        updatePersistedLaneAttributes( adjustment.lane.id, adjustment.lane.attributes, adjustment.lane.modifiedBy.get)
      }
    }
  }

  def adjustedSideCode(adjustment: SideCodeAdjustment): Unit = {
    val oldAsset = getPersistedLanesByIds( Set(adjustment.laneId), newTransaction = false).head
    val roadLink = roadLinkService.getRoadLinkAndComplementaryFromVVH(oldAsset.linkId, newTransaction = false)
                                  .getOrElse(throw new IllegalStateException("Road link no longer available"))

    val newLane = PersistedLane (0, oldAsset.linkId, adjustment.sideCode.value, oldAsset.laneCode, roadLink.municipalityCode,
      oldAsset.startMeasure, oldAsset.endMeasure, Some(VvhGenerated), Some( DateTime.now() ),
      None,None, expired = false, vvhClient.roadLinkData.createVVHTimeStamp(), oldAsset.geomModifiedDate, oldAsset.attributes)

    dao.updateExpiration(oldAsset.id, VvhGenerated)
   createWithoutTransaction( newLane, VvhGenerated )
  }

  def updatePersistedLaneAttributes( id: Long, attributes: Seq[LaneProperty], username: String): Unit = {
    attributes.foreach{ prop =>
      dao.updateLaneAttributes(id, prop, username)
    }
  }

  /**
    * Delete lanes with specified laneCodes on linkIds given
    * @param linkIds  LinkIds where are the lanes to be deleted
    * @param laneCodes  Lane codes to delete
    * @param newTransaction with or without transaction
    */
  def deleteMultipleLanes(linkIds: Set[Long], laneCodes: Set[Int], newTransaction: Boolean = true): Unit = {
    def deleteProcess(): Unit = {
      if (laneCodes.nonEmpty) {
        val allExistingLanes = dao.fetchLanesByLinkIdsAndLaneCode(linkIds.toSeq, laneCodes.toSeq)
        allExistingLanes.map(_.id).foreach(id => dao.deleteEntryLane(id))
      }
    }
    if(newTransaction)
      withDynTransaction {
        deleteProcess()
      }
    else
      deleteProcess()
  }

  /**
    * Expire lanes with specified laneCodes on linkIds given
    * @param linkIds  LinkIds where are the lanes to be expired
    * @param laneCodes  Lane codes to expire
    * @param username username of the one who expired the lanes
    * @param newTransaction with or without transaction
    */
  def multipleLanesToHistory(linkIds: Set[Long], laneCodes: Set[Int], username: String, newTransaction: Boolean = true): Unit = {
    def expireProcess(): Unit = {
      if (laneCodes.nonEmpty) {
        val allExistingLanes = dao.fetchLanesByLinkIdsAndLaneCode(linkIds.toSeq, laneCodes.toSeq)
        allExistingLanes.map(_.id).foreach(id => dao.updateLaneExpiration(id, username))
      }
    }
    if(newTransaction)
      withDynTransaction {
        expireProcess()
      }
    else
      expireProcess()
  }
}