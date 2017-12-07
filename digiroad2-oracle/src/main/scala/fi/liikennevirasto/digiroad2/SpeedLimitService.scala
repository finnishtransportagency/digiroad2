package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.GeometryUtils.Projection
import fi.liikennevirasto.digiroad2.client.vvh.{ChangeInfo, ChangeType, VVHClient}
import fi.liikennevirasto.digiroad2.dao.linearasset.{OracleLinearAssetDao, PersistedSpeedLimit}
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.linearasset.oracle.PersistedSpeedLimit
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.util.{LinearAssetUtils, VVHRoadLinkHistoryProcessor}
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

case class ChangedSpeedLimit(speedLimit: SpeedLimit, link: RoadLink)

class SpeedLimitService(eventbus: DigiroadEventBus, vvhClient: VVHClient, roadLinkServiceImplementation: RoadLinkService) {
  val dao: OracleLinearAssetDao = new OracleLinearAssetDao(vvhClient, roadLinkServiceImplementation)
  val logger = LoggerFactory.getLogger(getClass)

  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)

  /**
    * Returns unknown speed limits for Digiroad2Api /speedlimits/unknown GET endpoint.
    */
  def getUnknown(municipalities: Option[Set[Int]]): Map[String, Map[String, Any]] = {
    withDynSession {
      dao.getUnknownSpeedLimits(municipalities)
    }
  }

  /**
    * Removes speed limit from unknown speed limits list if speed limit exists. Used by SpeedLimitUpdater actor.
    */
  def purgeUnknown(linkIds: Set[Long]): Unit = {
    val roadLinks = vvhClient.roadLinkData.fetchByLinkIds(linkIds)
    withDynTransaction {
      roadLinks.foreach { rl =>
        dao.purgeFromUnknownSpeedLimits(rl.linkId, GeometryUtils.geometryLength(rl.geometry))
      }
    }
  }

  private def createUnknownLimits(speedLimits: Seq[SpeedLimit], roadLinksByLinkId: Map[Long, RoadLink]): Seq[UnknownSpeedLimit] = {
    val generatedLimits = speedLimits.filter(_.id == 0)
    generatedLimits.map { limit =>
      val roadLink = roadLinksByLinkId(limit.linkId)
      UnknownSpeedLimit(roadLink.linkId, roadLink.municipalityCode, roadLink.administrativeClass)
    }
  }

  private def getFilledTopologyAndRoadLinks(roadLinks: Seq[RoadLink], change: Seq[ChangeInfo], showSpeedLimitsHistory: Boolean = false) = {
    val (speedLimitLinks, topology) = dao.getSpeedLimitLinksByRoadLinks(roadLinks, showSpeedLimitsHistory)
    val oldRoadLinkIds = LinearAssetUtils.deletedRoadLinkIds(change, roadLinks)
    val oldSpeedLimits = dao.getCurrentSpeedLimitsByLinkIds(Some(oldRoadLinkIds.toSet))

    // filter those road links that have already been projected earlier from being reprojected
    val speedLimitsOnChangedLinks = speedLimitLinks.filter(sl => LinearAssetUtils.newChangeInfoDetected(sl, change))

    val projectableTargetRoadLinks = roadLinks.filter(
      rl => rl.linkType.value == UnknownLinkType.value || rl.isCarTrafficRoad)

    val newSpeedLimits = fillNewRoadLinksWithPreviousSpeedLimitData(projectableTargetRoadLinks,
      oldSpeedLimits ++ speedLimitsOnChangedLinks, speedLimitsOnChangedLinks, change)
    val speedLimits = (speedLimitLinks ++ newSpeedLimits).groupBy(_.linkId)
    val roadLinksByLinkId = topology.groupBy(_.linkId).mapValues(_.head)

    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology, speedLimits)

    eventbus.publish("linearAssets:update", changeSet.copy(expiredAssetIds =
      oldSpeedLimits.map(_.id).toSet ++ changeSet.droppedAssetIds, droppedAssetIds = Set())) // Expire all assets that are dropped or expired. No more floating speed limits.
    eventbus.publish("speedLimits:saveProjectedSpeedLimits", newSpeedLimits ++ filledTopology.filter(sl => sl.id <= 0 && sl.value.nonEmpty))

    eventbus.publish("speedLimits:purgeUnknownLimits", changeSet.adjustedMValues.map(_.linkId).toSet)

    val unknownLimits = createUnknownLimits(filledTopology, roadLinksByLinkId)
    eventbus.publish("speedLimits:persistUnknownLimits", unknownLimits)

    (filledTopology, roadLinksByLinkId)
  }
  /**
    * Returns speed limits for Digiroad2Api /speedlimits GET endpoint.
    */
  def get(bounds: BoundingRectangle, municipalities: Set[Int]): Seq[Seq[SpeedLimit]] = {
    val (roadLinks, change) = roadLinkServiceImplementation.getRoadLinksWithComplementaryAndChangesFromVVH(bounds, municipalities)
    withDynTransaction {
      val (filledTopology,roadLinksByLinkId) = getFilledTopologyAndRoadLinks(roadLinks, change)
      LinearAssetPartitioner.partition(enrichSpeedLimitAttributes(filledTopology, roadLinksByLinkId), roadLinksByLinkId)
    }
  }

  def withRoadAddress(pieceWiseLinearAssets: Seq[Seq[SpeedLimit]]): Seq[Seq[SpeedLimit]] ={
    val addressData = roadLinkServiceImplementation.getRoadAddressesByLinkIds(pieceWiseLinearAssets.flatMap(pwa => pwa.map(_.linkId)).toSet).map(a => (a.linkId, a)).toMap
    pieceWiseLinearAssets.map(
      _.map(pwa =>
        if (addressData.contains(pwa.linkId))
          pwa.copy(attributes = pwa.attributes ++ addressData(pwa.linkId).asAttributes)
        else
          pwa
      ))
  }

  /**
    * Returns speed limits history for Digiroad2Api /history speedlimits GET endpoint.
    */
  def getHistory(bounds: BoundingRectangle, municipalities: Set[Int]): Seq[Seq[SpeedLimit]] = {
    val roadLinks = roadLinkServiceImplementation.getRoadLinksHistoryFromVVH(bounds, municipalities)
    withDynTransaction {
      val (filledTopology, roadLinksByLinkId) = getFilledTopologyAndRoadLinks(roadLinks, Seq(), true)
      LinearAssetPartitioner.partition(filledTopology, roadLinksByLinkId)
    }
  }

  /**
    * This method returns speed limits that have been changed in OTH between given date values. It is used by TN-ITS ChangeApi.
    *
    * @param sinceDate
    * @param untilDate
    * @return Changed speed limits
    */
  def getChanged(sinceDate: DateTime, untilDate: DateTime): Seq[ChangedSpeedLimit] = {
    val persistedSpeedLimits = withDynTransaction {
      dao.getSpeedLimitsChangedSince(sinceDate, untilDate)
    }
    val roadLinks = roadLinkServiceImplementation.getRoadLinksAndComplementariesFromVVH(persistedSpeedLimits.map(_.linkId).toSet)
    val roadLinksWithoutWalkways = roadLinks.filterNot(_.linkType == CycleOrPedestrianPath).filterNot(_.linkType == TractorRoad)

    persistedSpeedLimits.flatMap { speedLimit =>
      roadLinksWithoutWalkways.find(_.linkId == speedLimit.linkId).map { roadLink =>
        ChangedSpeedLimit(
          speedLimit = SpeedLimit(
            id = speedLimit.id,
            linkId = speedLimit.linkId,
            sideCode = speedLimit.sideCode,
            trafficDirection = roadLink.trafficDirection,
            value = speedLimit.value.map(NumericValue),
            geometry = GeometryUtils.truncateGeometry3D(roadLink.geometry, speedLimit.startMeasure, speedLimit.endMeasure),
            startMeasure = speedLimit.startMeasure,
            endMeasure = speedLimit.endMeasure,
            modifiedBy = speedLimit.modifiedBy, modifiedDateTime = speedLimit.modifiedDate,
            createdBy = speedLimit.createdBy, createdDateTime = speedLimit.createdDate,
            vvhTimeStamp = speedLimit.vvhTimeStamp, geomModifiedDate = speedLimit.geomModifiedDate,
            expired = speedLimit.expired,
            linkSource = roadLink.linkSource
          ),
          link = roadLink
        )
      }
    }
  }

  /**
    * Uses VVH ChangeInfo API to map OTH speed limit information from old road links to new road links after geometry changes.
    */
  private def fillNewRoadLinksWithPreviousSpeedLimitData(roadLinks: Seq[RoadLink], speedLimitsToUpdate: Seq[SpeedLimit],
                                                         currentSpeedLimits: Seq[SpeedLimit], changes: Seq[ChangeInfo]) : Seq[SpeedLimit] ={
    val mapped = mapReplacementProjections(speedLimitsToUpdate, currentSpeedLimits, roadLinks, changes)
    val newSpeedLimits = mapped.flatMap {
      case (speedLimit, (Some(roadLink), Some(projection))) =>
        Some(SpeedLimitFiller.projectSpeedLimit(speedLimit, roadLink, projection))
      case (_, (_, _)) =>
        None
    }.filter(sl => Math.abs(sl.startMeasure - sl.endMeasure) > 0) // Remove zero-length or invalid length parts
    newSpeedLimits
  }

  private def mapReplacementProjections(oldSpeedLimits: Seq[SpeedLimit], currentSpeedLimits: Seq[SpeedLimit], roadLinks: Seq[RoadLink],
                                changes: Seq[ChangeInfo]) : Seq[(SpeedLimit, (Option[RoadLink], Option[Projection]))] = {
    val targetLinks = changes.flatMap(_.newId).toSet
    val newRoadLinks = roadLinks.filter(rl => targetLinks.contains(rl.linkId)).groupBy(_.linkId)
    val changeMap = changes.filterNot(c => c.newId.isEmpty || c.oldId.isEmpty).map(c => (c.oldId.get, c.newId.get)).groupBy(_._1)
    val targetRoadLinks = changeMap.mapValues(a => a.flatMap(b => newRoadLinks.getOrElse(b._2, Seq())))
    oldSpeedLimits.flatMap{limit =>
      targetRoadLinks.getOrElse(limit.linkId, Seq()).map(newRoadLink =>
        (limit,
          getRoadLinkAndProjection(roadLinks, changes, limit.linkId, newRoadLink.linkId, oldSpeedLimits, currentSpeedLimits))
      )}
  }

  private def getRoadLinkAndProjection(roadLinks: Seq[RoadLink], changes: Seq[ChangeInfo], oldId: Long, newId: Long,
                               speedLimitsToUpdate: Seq[SpeedLimit], currentSpeedLimits: Seq[SpeedLimit]) = {
    val roadLink = roadLinks.find(rl => newId == rl.linkId)
    val changeInfo = changes.find(c => c.oldId.getOrElse(0) == oldId && c.newId.getOrElse(0) == newId)
    val projection = changeInfo match {
      case Some(info) =>
        // ChangeInfo object related speed limits; either mentioned in oldId or in newId
        val speedLimits = speedLimitsToUpdate.filter(_.linkId == info.oldId.getOrElse(0L)) ++
          currentSpeedLimits.filter(_.linkId == info.newId.getOrElse(0L))
        mapChangeToProjection(info, speedLimits)
      case _ => None
    }
    (roadLink,projection)
  }

  private def mapChangeToProjection(change: ChangeInfo, speedLimits: Seq[SpeedLimit]) = {
    val typed = ChangeType.apply(change.changeType)
    typed match {
        // cases 5, 6, 1, 2
      case ChangeType.DividedModifiedPart  | ChangeType.DividedNewPart | ChangeType.CombinedModifiedPart |
           ChangeType.CombinedRemovedPart => projectSpeedLimitConditionally(change, speedLimits, testNoSpeedLimitExists)
        // cases 3, 7, 13, 14
      case ChangeType.LengthenedCommonPart | ChangeType.ShortenedCommonPart | ChangeType.ReplacedCommonPart |
           ChangeType.ReplacedNewPart =>
        projectSpeedLimitConditionally(change, speedLimits, testSpeedLimitOutdated)
      case _ => None
    }
  }

  private def testNoSpeedLimitExists(speedLimits: Seq[SpeedLimit], linkId: Long, mStart: Double, mEnd: Double,
                                     vvhTimeStamp: Long) = {
    !speedLimits.exists(l => l.linkId == linkId && GeometryUtils.overlaps((l.startMeasure,l.endMeasure),(mStart,mEnd)))
  }

  private def testSpeedLimitOutdated(speedLimits: Seq[SpeedLimit], linkId: Long, mStart: Double, mEnd: Double,
                                     vvhTimeStamp: Long) = {
    val targetLimits = speedLimits.filter(l => l.linkId == linkId)
    targetLimits.nonEmpty && !targetLimits.exists(l => l.vvhTimeStamp >= vvhTimeStamp)
  }

  private def projectSpeedLimitConditionally(change: ChangeInfo, limits: Seq[SpeedLimit],
                                     condition: (Seq[SpeedLimit], Long, Double, Double, Long) => Boolean) = {
    (change.newId, change.oldStartMeasure, change.oldEndMeasure, change.newStartMeasure, change.newEndMeasure, change.vvhTimeStamp) match {
      case (Some(newId), Some(oldStart:Double), Some(oldEnd:Double),
      Some(newStart:Double), Some(newEnd:Double), vvhTimeStamp) =>
        condition(limits, newId, newStart, newEnd, vvhTimeStamp) match {
          case true => Some(Projection(oldStart, oldEnd, newStart, newEnd, vvhTimeStamp))
          case false => None
        }
      case _ => None
    }
  }

  /**
    * Returns speed limits for Digiroad2Api /speedlimits PUT endpoint (after updating or creating speed limits).
    */
  def get(ids: Seq[Long]): Seq[SpeedLimit] = {
    withDynTransaction {
      ids.flatMap(loadSpeedLimit)
    }
  }

  /**
    * Returns speed limit for Digiroad2Api /speedlimits POST endpoint (after creating new speed limit).
    */
  def find(speedLimitId: Long): Option[SpeedLimit] = {
    withDynTransaction {
      loadSpeedLimit(speedLimitId)
    }
  }

  private def loadSpeedLimit(speedLimitId: Long): Option[SpeedLimit] = {
    dao.getSpeedLimitLinksById(speedLimitId).headOption
  }

  /**
    * Adds speed limits to unknown speed limits list. Used by SpeedLimitUpdater actor.
    * Links to unknown speed limits are shown on UI Worklist page.
    */
  def persistUnknown(limits: Seq[UnknownSpeedLimit]): Unit = {
    withDynTransaction {
      dao.persistUnknownSpeedLimits(limits)
    }
  }

  def persistProjectedLimit(limits: Seq[SpeedLimit]): Unit = {
    withDynTransaction {
      val (newlimits, changedlimits) = limits.partition(_.id <= 0)
      newlimits.foreach { limit =>
        dao.createSpeedLimit(limit.createdBy.getOrElse(LinearAssetTypes.VvhGenerated), limit.linkId, Measures(limit.startMeasure, limit.endMeasure),
          limit.sideCode, limit.value.get.value, Some(limit.vvhTimeStamp), limit.createdDateTime, limit.modifiedBy,
          limit.modifiedDateTime, limit.linkSource)
      }
      changedlimits.foreach { limit =>
        dao.updateMValues(limit.id, (limit.startMeasure, limit.endMeasure), limit.vvhTimeStamp)
      }
    }
    // Add them to checks to remove unknown limits
    eventbus.publish("speedLimits:purgeUnknownLimits", limits.map(_.linkId).toSet)
  }

  /**
    * Saves speed limit value changes received from UI. Used by Digiroad2Api /speedlimits PUT endpoint.
    */
  def updateValues(ids: Seq[Long], value: Int, username: String, municipalityValidation: Int => Unit): Seq[Long] = {
    withDynTransaction {
      ids.flatMap(updateSpeedLimitValue(_, value, username, municipalityValidation))
    }
  }

  /**
    * Create new speed limit when value received from UI changes and expire the old one. Used by SpeeedLimitsService.updateValues.
    */
  def updateSpeedLimitValue(id: Long, value: Int, username: String, municipalityValidation: Int => Unit): Option[Long] = {
    def validateMunicipalities(vvhLinks: Seq[(Long, Double, Seq[Point], Int, LinkGeomSource)]): Unit = {
      vvhLinks.foreach(vvhLink => municipalityValidation(vvhLink._4))
    }

    validateMunicipalities(dao.getLinksWithLengthFromVVH(20, id))

    //Get all data from the speedLimit to update
    val speedLimit = dao.getPersistedSpeedLimit(id).get

    //Expire old speed limit
    dao.updateExpiration(id, true, username)

    //Create New Asset copy by the old one with new value
    dao.createSpeedLimit(speedLimit.createdBy.getOrElse(username), speedLimit.linkId, Measures(speedLimit.startMeasure, speedLimit.endMeasure),
      speedLimit.sideCode, value, Some(speedLimit.vvhTimeStamp), speedLimit.createdDate,
      Some(username), Some(DateTime.now()), speedLimit.linkSource)
  }

  /**
    * Saves speed limit values when speed limit is split to two parts in UI (scissors icon). Used by Digiroad2Api /speedlimits/:speedLimitId/split POST endpoint.
    */
  def split(id: Long, splitMeasure: Double, existingValue: Int, createdValue: Int, username: String, municipalityValidation: (Int) => Unit): Seq[SpeedLimit] = {
    withDynTransaction {
      val newId = dao.splitSpeedLimit(id, splitMeasure, createdValue, username, municipalityValidation)
      val idUpdated = updateSpeedLimitValue(id, existingValue, username, municipalityValidation).get
      Seq(loadSpeedLimit(idUpdated).get, loadSpeedLimit(newId).get)
    }
  }

  private def toSpeedLimit(persistedSpeedLimit: PersistedSpeedLimit): SpeedLimit = {
    val roadLink = roadLinkServiceImplementation.getRoadLinkAndComplementaryFromVVH(persistedSpeedLimit.linkId).get

    SpeedLimit(
      persistedSpeedLimit.id, persistedSpeedLimit.linkId, persistedSpeedLimit.sideCode,
      roadLink.trafficDirection, persistedSpeedLimit.value.map(NumericValue),
      GeometryUtils.truncateGeometry3D(roadLink.geometry, persistedSpeedLimit.startMeasure, persistedSpeedLimit.endMeasure),
      persistedSpeedLimit.startMeasure, persistedSpeedLimit.endMeasure,
      persistedSpeedLimit.modifiedBy, persistedSpeedLimit.modifiedDate,
      persistedSpeedLimit.createdBy, persistedSpeedLimit.createdDate, persistedSpeedLimit.vvhTimeStamp, persistedSpeedLimit.geomModifiedDate,
      linkSource = persistedSpeedLimit.linkSource)
  }

  private def isSeparableValidation(speedLimit: SpeedLimit): SpeedLimit = {
    val separable = speedLimit.sideCode == SideCode.BothDirections && speedLimit.trafficDirection == TrafficDirection.BothDirections
    if (!separable) throw new IllegalArgumentException
    speedLimit
  }

  /**
    * Saves speed limit values when speed limit is separated to two sides in UI. Used by Digiroad2Api /speedlimits/:speedLimitId/separate POST endpoint.
    */
  def separate(id: Long, valueTowardsDigitization: Int, valueAgainstDigitization: Int, username: String, municipalityValidation: Int => Unit): Seq[SpeedLimit] = {
    val speedLimit = withDynTransaction { dao.getPersistedSpeedLimit(id) }
      .map(toSpeedLimit)
      .map(isSeparableValidation)
      .get

    withDynTransaction {
      val idUpdated = updateSpeedLimitValue(id, valueTowardsDigitization, username, municipalityValidation).get
      dao.updateSideCode(idUpdated, SideCode.TowardsDigitizing)
      val newId = dao.createSpeedLimit(username, speedLimit.linkId, Measures(speedLimit.startMeasure, speedLimit.endMeasure), SideCode.AgainstDigitizing, valueAgainstDigitization, None, linkSource = speedLimit.linkSource).get

      Seq(loadSpeedLimit(idUpdated).get, loadSpeedLimit(newId).get)
    }
  }

  /**
    * Returns speed limits by municipality. Used by IntegrationApi speed_limits endpoint.
    */
  def get(municipality: Int): Seq[SpeedLimit] = {
    val (roadLinks, changes) = roadLinkServiceImplementation.getRoadLinksWithComplementaryAndChangesFromVVH(municipality)
    withDynTransaction {
      getFilledTopologyAndRoadLinks(roadLinks, changes)._1
    }
  }

  /**
    * Saves new speed limit from UI. Used by Digiroad2Api /speedlimits PUT and /speedlimits POST endpoints.
    */
  def create(newLimits: Seq[NewLimit], value: Int, username: String, municipalityValidation: (Int) => Unit): Seq[Long] = {
    withDynTransaction {
      val createdIds = newLimits.flatMap { limit =>
        dao.createSpeedLimit(username, limit.linkId, Measures(limit.startMeasure, limit.endMeasure), SideCode.BothDirections, value, vvhClient.createVVHTimeStamp(), municipalityValidation)
      }
      eventbus.publish("speedLimits:purgeUnknownLimits", newLimits.map(_.linkId).toSet)
      createdIds
    }
  }

  private def addRoadAdministrationClassAttribute(speedLimit: SpeedLimit, roadLink: RoadLink): SpeedLimit = {
    speedLimit.copy(attributes = speedLimit.attributes ++ Map("ROAD_ADMIN_CLASS" -> roadLink.administrativeClass))
  }

  private def enrichSpeedLimitAttributes(speedLimits: Seq[SpeedLimit], roadLinksForSpeedLimits: Map[Long, RoadLink]): Seq[SpeedLimit] = {
    val speedLimitAttributeOperations: Seq[(SpeedLimit, RoadLink) => SpeedLimit] = Seq(
      addRoadAdministrationClassAttribute
      //In the future if we need to add more attributes just add a method here
    )

    speedLimits.map(speedLimit =>
      speedLimitAttributeOperations.foldLeft(speedLimit) { case (asset, operation) =>
        roadLinksForSpeedLimits.get(asset.linkId).map{
          roadLink =>
            operation(asset, roadLink)
        }.getOrElse(asset)
      }
    )
  }
}
