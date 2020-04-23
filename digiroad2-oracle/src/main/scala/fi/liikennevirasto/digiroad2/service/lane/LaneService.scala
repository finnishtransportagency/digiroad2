package fi.liikennevirasto.digiroad2.service.lane

import java.security.InvalidParameterException

import com.jolbox.bonecp.{BoneCPConfig, BoneCPDataSource}
import fi.liikennevirasto.digiroad2.GeometryUtils.Projection
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.{ChangeInfo, ChangeType, VVHClient}
import fi.liikennevirasto.digiroad2.dao.MunicipalityDao
import fi.liikennevirasto.digiroad2.dao.lane.LaneDao
import fi.liikennevirasto.digiroad2.lane.LaneFiller._
import fi.liikennevirasto.digiroad2.lane._
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.{LaneUtils, PolygonTools}
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
  def laneFilter: LaneFiller = new LaneFiller

  lazy val dataSource = {
    val cfg = new BoneCPConfig(OracleDatabase.loadProperties("/bonecp.properties"))
    new BoneCPDataSource(cfg)
  }

  val logger = LoggerFactory.getLogger(getClass)
  lazy val VvhGenerated = "vvh_generated"

  def getByZoomLevel( boundingRectangle: BoundingRectangle, linkGeomSource: Option[LinkGeomSource] = None) : Seq[Seq[LightLane]] = {
    withDynTransaction {
      val assets = dao.fetchLanes(  boundingRectangle, linkGeomSource)
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
    val linearAssets = getMainLanesByRoadLinks(roadLinks, change)

    LanePartitioner.partition(linearAssets, roadLinks.groupBy(_.linkId).mapValues(_.head))
  }

  protected def getMainLanesByRoadLinks(roadLinks: Seq[RoadLink], changes: Seq[ChangeInfo]): Seq[PieceWiseLane] = {
    val mappedChanges = LaneUtils.getMappedChanges(changes)
    val removedLinkIds = LaneUtils.deletedRoadLinkIds(mappedChanges, roadLinks.map(_.linkId).toSet)
    val existingAssets = fetchExistingLanesByRoadLinks(roadLinks, removedLinkIds)

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

    val groupedAssets = (assetsOnChangedLinks.filterNot(a => projectedLanes.exists(_.linkId == a.linkId)) ++ projectedLanes ++ lanesWithoutChangedLinks).groupBy(_.linkId)
    val (filledTopology, changeSet) = laneFilter.fillTopology(roadLinks, groupedAssets, Some(changedSet))

    val generatedMappedById = changeSet.generatedPersistedLanes.groupBy(_.id)
    val modifiedLanes = projectedLanes.filterNot {lane => generatedMappedById(lane.id).nonEmpty } ++ changeSet.generatedPersistedLanes

    publish(eventBus, changeSet, modifiedLanes)
    filledTopology.filter(lane => lane.laneAttributes.find(_.publicId == "lane_code").head.values.head.value.toString.charAt(1) == '1')

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
        val (linearAsset, changes) = laneFilter.projectLinearAsset(asset, roadLink, projection, cs)
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
    def givenAndEqualLongs(v1: Option[Long], v2: Option[Long]) = {
      (v1, v2) match {
        case (Some(l1), Some(l2)) => l1 == l2
        case _ => false
      }
    }
    // Test if these change infos extend each other. Then take the small little piece just after tolerance value to test if it is true there
    val (mStart, mEnd) = (givenAndEqualDoubles(replacementChangeInfo.newStartMeasure, extensionChangeInfo.newEndMeasure),
      givenAndEqualDoubles(replacementChangeInfo.newEndMeasure, extensionChangeInfo.newStartMeasure)) match {
      case (true, false) =>
        (replacementChangeInfo.oldStartMeasure.get + laneFilter.AllowedTolerance,
          replacementChangeInfo.oldStartMeasure.get + laneFilter.AllowedTolerance + laneFilter.MaxAllowedError)
      case (false, true) =>
        (Math.max(0.0, replacementChangeInfo.oldEndMeasure.get - laneFilter.AllowedTolerance - laneFilter.MaxAllowedError),
          Math.max(0.0, replacementChangeInfo.oldEndMeasure.get - laneFilter.AllowedTolerance))
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
      dao.fetchMainLanesByLinkIds( linkIds ++ removedLinkIds)
    }.filterNot(_.expired)
  }

  def fetchExistingLanesByRoadLinks(roadLinks: Seq[RoadLink], removedLinkIds: Seq[Long] = Seq()): Seq[PersistedLane] = {
    val linkIds = roadLinks.map(_.linkId)

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
      lane.geomModifiedDate, roadLink.administrativeClass, lane.attributes, attributes = Map("municipality" -> lane.municipalityCode))
    }
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
  def update ( newIncomeLane: Seq[NewIncomeLane], linkIds: Set[Long] ,sideCode: Int, username: String ): Seq[Long] = {
    if (newIncomeLane.isEmpty || linkIds.isEmpty)
      return Seq()

    withDynTransaction {
     if (linkIds.size == 1) {
        val linkId = linkIds.head

        newIncomeLane.map { lane =>

          val laneCode = getLaneCode(lane)

          val lameToUpdate = PersistedLane(lane.id, linkId, sideCode, laneCode.toInt, lane.municipalityCode,
            lane.startMeasure, lane.endMeasure, Some(username), None, None, None, false, 0, None, lane.properties)

          dao.updateEntryLane(lameToUpdate, username)
        }

      } else {

        // get all lane codes from lanes to update
        val laneCodes = newIncomeLane.map( lane => getLaneCode(lane).toInt )

        // fetch from db the existing lanes
        val allExistingLanes = dao.fetchLanesByLinkIdsAndLaneCode(linkIds.toSeq, laneCodes )
                                  .groupBy(_.linkId)

        // Iterate through the links
        val result = linkIds.map { linkId =>
          newIncomeLane.map { lane =>

            val laneCode = getLaneCode(lane).toInt

            val currentLanes = allExistingLanes.getOrElse(linkId, throw new InvalidParameterException(s"LinkId: $linkId dont have laneCode: $laneCode for update!"))
            val auxLane = currentLanes.filter(_.laneCode == laneCode)

            // Since cut lanes will not be in multiple linkIds Update, we can only have the 1st element
            val currentLane = if ( auxLane.nonEmpty)
                                auxLane.head
                              else
                                throw new InvalidParameterException(s"LinkId: $linkId dont have laneCode: $laneCode for update!")


            val laneToUpdate = PersistedLane(currentLane.id, linkId, sideCode, laneCode, currentLane.municipalityCode,
              currentLane.startMeasure, currentLane.endMeasure, Some(username), None, None, None, false, 0, None, lane.properties)

            dao.updateEntryLane(laneToUpdate, username)
          }
        }

        result.head
      }
    }
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
  def create(newIncomeLane: Seq[NewIncomeLane], linkIds: Set[Long] ,sideCode: Int, username: String, vvhTimeStamp: Long = vvhClient.roadLinkData.createVVHTimeStamp()): Seq[Long] = {

    if (newIncomeLane.isEmpty || linkIds.isEmpty)
      return Seq()

    withDynTransaction {

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
        dao.updateMValuesChangeInfo(adjustment.laneId, (adjustment.startMeasure, adjustment.endMeasure), adjustment.vvhTimestamp, VvhGenerated)
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
    val roadLink = roadLinkService.getRoadLinkAndComplementaryFromVVH(oldAsset.linkId, newTransaction = false).getOrElse(throw new IllegalStateException("Road link no longer available"))

    val newLane = PersistedLane (0, oldAsset.linkId, adjustment.sideCode.value, oldAsset.laneCode, roadLink.municipalityCode,
      oldAsset.startMeasure, oldAsset.endMeasure, Some(VvhGenerated), Some( DateTime.now() ),
      None,None, expired = false, vvhClient.roadLinkData.createVVHTimeStamp(), oldAsset.geomModifiedDate, oldAsset.attributes)

    dao.updateExpiration(oldAsset.id, VvhGenerated)
   createWithoutTransaction( newLane, VvhGenerated )
  }

  def updatePersistedLaneAttributes( id: Long, attributes: Seq[LaneProperty], username: String) = {
    attributes.map { prop =>
      dao.updateLaneAttributes(id, prop, username)
    }
  }

  def deleteMultipleLanes(ids: Set[Long]) = {
    withDynTransaction {
      ids.map(id => dao.deleteEntryLane(id))
    }
  }

  def deleteEntryLane(id: Long ): Unit = {
    withDynTransaction {
      dao.deleteEntryLane(id)
    }
  }

  def multipleLanesToHistory (ids: Set[Long], username: String):  Set[Long] ={
    withDynTransaction {
      ids.map(id => dao.updateLaneExpiration(id, username) )
    }
    ids
  }

  def laneToHistory (id: Long, username: String): Long ={
    withDynTransaction {
      dao.updateLaneExpiration(id, username)
    }
    id
  }

}