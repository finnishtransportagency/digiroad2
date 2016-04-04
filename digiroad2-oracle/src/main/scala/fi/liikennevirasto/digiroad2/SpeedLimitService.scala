package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.{UnknownLinkType, BoundingRectangle, SideCode, TrafficDirection}
import fi.liikennevirasto.digiroad2.linearasset.SpeedLimitFiller.Projection
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.linearasset.oracle.{OracleLinearAssetDao, PersistedSpeedLimit}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.util.LinearAssetUtils
import org.slf4j.LoggerFactory

class SpeedLimitService(eventbus: DigiroadEventBus, vvhClient: VVHClient, roadLinkServiceImplementation: RoadLinkService) {
  val dao: OracleLinearAssetDao = new OracleLinearAssetDao(vvhClient)
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
    val roadLinks = vvhClient.fetchVVHRoadlinks(linkIds)
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

  // TODO: Move to LinearAssetUtils
  def isNewProjection(roadLink: RoadLink, changeInfo: Seq[ChangeInfo], speedLimits: Seq[SpeedLimit]) = {
    changeInfo.exists(_.newId == roadLink.linkId) &&
      speedLimits.exists(sl => (sl.linkId == roadLink.linkId) &&
        (sl.vvhTimeStamp < changeInfo.filter(_.newId == roadLink.linkId).maxBy(_.vvhTimeStamp).vvhTimeStamp.getOrElse(0: Long)))
  }

  // Filter to only those Ids that are no longer present on map
  private def deletedRoadLinkIds(change: Seq[ChangeInfo], current: Seq[RoadLink]): Seq[Long] = {
    change.filter(_.oldId.nonEmpty).flatMap(_.oldId).filterNot(id => current.exists(rl => rl.linkId == id))
  }

  def getFilledTopologyAndRoadLinks(roadLinks: Seq[RoadLink], change: Seq[ChangeInfo]) = {
    val (speedLimitLinks, topology) = dao.getSpeedLimitLinksByRoadLinks(roadLinks)
    val oldRoadLinkIds = deletedRoadLinkIds(change, roadLinks)
    val oldSpeedLimits = dao.getCurrentSpeedLimitsByLinkIds(Some(oldRoadLinkIds.toSet)).toSeq

    // filter those road links that have already been projected earlier from being reprojected
    val speedLimitsOnChangedLinks = speedLimitLinks.filter(sl => LinearAssetUtils.newChangeInfoDetected(sl, change))

    val projectableTargetRoadLinks = roadLinks.filter(
      rl => rl.linkType.value == UnknownLinkType.value || rl.isCarTrafficRoad)

    val newSpeedLimits = fillNewRoadLinksWithPreviousSpeedLimitData(projectableTargetRoadLinks, oldSpeedLimits ++ speedLimitsOnChangedLinks, speedLimitsOnChangedLinks, change)
    // TODO: Remove from newSpeedLimits if we already have one saved.
    val speedLimits = (speedLimitLinks.filterNot(sl => newSpeedLimits.map(_.linkId).contains(sl.linkId)) ++ newSpeedLimits).groupBy(_.linkId)
    val roadLinksByLinkId = topology.groupBy(_.linkId).mapValues(_.head)

    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology, speedLimits)

    eventbus.publish("linearAssets:update", changeSet.copy(expiredAssetIds =
      oldSpeedLimits.map(_.id).toSet)) // Expire only non-rewritten
    eventbus.publish("speedLimits:saveProjectedSpeedLimits", newSpeedLimits)

    eventbus.publish("speedLimits:purgeUnknownLimits", changeSet.adjustedMValues.map(_.linkId).toSet)

    val unknownLimits = createUnknownLimits(filledTopology, roadLinksByLinkId)
    eventbus.publish("speedLimits:persistUnknownLimits", unknownLimits)

    (filledTopology, roadLinksByLinkId)
  }
  /**
    * Returns speed limits for Digiroad2Api /speedlimits GET endpoint.
    */
  def get(bounds: BoundingRectangle, municipalities: Set[Int]): Seq[Seq[SpeedLimit]] = {
    val (roadLinks, change) = roadLinkServiceImplementation.getRoadLinksAndChangesFromVVH(bounds, municipalities)
    withDynTransaction {
      val (filledTopology,roadLinksByLinkId) = getFilledTopologyAndRoadLinks(roadLinks, change)
      LinearAssetPartitioner.partition(filledTopology, roadLinksByLinkId)
    }
  }

  /**
    * Uses VVH ChangeInfo API to map OTH speed limit information from old road links to new road links after geometry changes.
    */
  def fillNewRoadLinksWithPreviousSpeedLimitData(roadLinks: Seq[RoadLink], oldSpeedLimits: Seq[SpeedLimit], currentSpeedLimits: Seq[SpeedLimit], changes: Seq[ChangeInfo]) : Seq[SpeedLimit] ={
    val newSpeedLimits = mapReplacementProjections(oldSpeedLimits, currentSpeedLimits, roadLinks, changes).flatMap(
      limit =>
        limit match {
          case (speedLimit, (Some(roadLink), Some(projection))) =>
            Some(SpeedLimitFiller.projectSpeedLimit(speedLimit, roadLink, projection))
          case (_, (_, _)) =>
            None
        }).filter(sl => Math.abs(sl.startMeasure - sl.endMeasure) > 0) // Remove zero-length or invalid length parts
    newSpeedLimits
  }

  def mapReplacementProjections(oldSpeedLimits: Seq[SpeedLimit], currentSpeedLimits: Seq[SpeedLimit], newRoadLinks: Seq[RoadLink],
                                changes: Seq[ChangeInfo]) : Seq[(SpeedLimit, (Option[RoadLink], Option[Projection]))] = {
    val targetLinks = changes.flatMap(_.newId).toSet
    oldSpeedLimits.flatMap{limit =>
      newRoadLinks.filter(rl => targetLinks.contains(rl.linkId)).map(newRoadLink =>
        (limit, getRoadLinkAndProjection(newRoadLinks, changes, limit.linkId, newRoadLink.linkId, oldSpeedLimits, currentSpeedLimits))
      )}
  }

  def getRoadLinkAndProjection(roadLinks: Seq[RoadLink], changes: Seq[ChangeInfo], oldId: Long, newId: Long,
                               oldSpeedLimits: Seq[SpeedLimit], currentSpeedLimits: Seq[SpeedLimit]) = {
    val roadLink = roadLinks.find(rl => newId == rl.linkId)
    val changeInfo = changes.find(c => c.oldId.getOrElse(0) == oldId && c.newId.getOrElse(0) == newId)
    val projection = changeInfo match {
      case Some(info) => mapChangeToProjection(info, oldSpeedLimits.filter(_.linkId == info.oldId.getOrElse(0L)),
        currentSpeedLimits.filter(_.linkId == info.newId.getOrElse(0L)))
      case _ => None
    }
    (roadLink,projection)
  }

  def mapChangeToProjection(change: ChangeInfo, oldSpeedLimits: Seq[SpeedLimit], currentSpeedLimits: Seq[SpeedLimit]) = {
    // TODO: Do different type of change info handling here
    val typed = ChangeType.apply(change.changeType)
    typed match {
        // cases 5, 6, 1, 2
      case ChangeType.DividedModifiedPart  | ChangeType.DividedNewPart | ChangeType.CombinedModifiedPart |
           ChangeType.CombinedRemovedPart => projectIfNoSpeedLimitExistsOnDestination(change, oldSpeedLimits++currentSpeedLimits)
        // cases 3, 7, 13, 14
      case ChangeType.LenghtenedCommonPart | ChangeType.ShortenedCommonPart | ChangeType.ReplacedCommonPart |
           ChangeType.ReplacedNewPart =>
        projectSpeedLimitIfDestinationIsOlder(change, oldSpeedLimits++currentSpeedLimits)
      case _ => None
    }
  }

  // For replaced links (oldId != newId)
  def projectIfNoSpeedLimitExistsOnDestination(change: ChangeInfo, limits: Seq[SpeedLimit]) = {
    (change.newId, change.oldStartMeasure, change.oldEndMeasure, change.newStartMeasure, change.newEndMeasure, change.vvhTimeStamp) match {
      case (Some(newId), Some(oldStart:Double), Some(oldEnd:Double),
      Some(newStart:Double), Some(newEnd:Double), vvhTimeStamp) =>
        limits.exists(l => l.linkId == newId && GeometryUtils.overlaps((l.startMeasure,l.endMeasure),(newStart,newEnd))) match {
          case false => Some(Projection(oldStart, oldEnd, newStart, newEnd, vvhTimeStamp.getOrElse(0)))
          case true => None
        }
      case _ => None
    }
  }

  // For updated links (oldId == newId), vvhTimeStamp must then exist in change so we can compare
  def projectSpeedLimitIfDestinationIsOlder(change: ChangeInfo, limits: Seq[SpeedLimit]) = {
    (change.newId, change.oldStartMeasure, change.oldEndMeasure, change.newStartMeasure, change.newEndMeasure, change.vvhTimeStamp) match {
      case (Some(newId), Some(oldStart:Double), Some(oldEnd:Double),
      Some(newStart:Double), Some(newEnd:Double), Some(vvhTimeStamp)) =>
        val myLimits = limits.filter(l => l.linkId == newId)
        myLimits.isEmpty || myLimits.exists(l => l.vvhTimeStamp >= vvhTimeStamp) match {
            // nonempty && only old data is found for this link -> project it
          case false => Some(Projection(oldStart, oldEnd, newStart, newEnd, vvhTimeStamp))
          case true => None
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
        dao.createSpeedLimit(limit.createdBy.getOrElse("vvh_generated"), limit.linkId, (limit.startMeasure, limit.endMeasure),
          limit.sideCode, limit.value.get.value, Some(limit.vvhTimeStamp), limit.createdDateTime, limit.modifiedBy,
          limit.modifiedDateTime)
      }
      changedlimits.foreach { limit =>
        dao.updateMValues(limit.id, (limit.startMeasure, limit.endMeasure), limit.vvhTimeStamp)
      }
    }
  }

  /**
    * Saves speed limit value changes received from UI. Used by Digiroad2Api /speedlimits PUT endpoint.
    */
  def updateValues(ids: Seq[Long], value: Int, username: String, municipalityValidation: Int => Unit): Seq[Long] = {
    withDynTransaction {
      ids.map(dao.updateSpeedLimitValue(_, value, username, municipalityValidation)).flatten
    }
  }

  /**
    * Saves speed limit values when speed limit is split to two parts in UI (scissors icon). Used by Digiroad2Api /speedlimits/:speedLimitId/split POST endpoint.
    */
  def split(id: Long, splitMeasure: Double, existingValue: Int, createdValue: Int, username: String, municipalityValidation: (Int) => Unit): Seq[SpeedLimit] = {
    withDynTransaction {
      val newId = dao.splitSpeedLimit(id, splitMeasure, createdValue, username, municipalityValidation)
      dao.updateSpeedLimitValue(id, existingValue, username, municipalityValidation)
      Seq(loadSpeedLimit(id).get, loadSpeedLimit(newId).get)
    }
  }

  private def toSpeedLimit(persistedSpeedLimit: PersistedSpeedLimit): SpeedLimit = {
    val roadLink = roadLinkServiceImplementation.getRoadLinkFromVVH(persistedSpeedLimit.linkId).get

    SpeedLimit(
      persistedSpeedLimit.id, persistedSpeedLimit.linkId, persistedSpeedLimit.sideCode,
      roadLink.trafficDirection, persistedSpeedLimit.value.map(NumericValue),
      GeometryUtils.truncateGeometry(roadLink.geometry, persistedSpeedLimit.startMeasure, persistedSpeedLimit.endMeasure),
      persistedSpeedLimit.startMeasure, persistedSpeedLimit.endMeasure,
      persistedSpeedLimit.modifiedBy, persistedSpeedLimit.modifiedDate,
      persistedSpeedLimit.createdBy, persistedSpeedLimit.createdDate, persistedSpeedLimit.vvhTimeStamp, persistedSpeedLimit.vvhModifiedDate)
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
      dao.updateSpeedLimitValue(id, valueTowardsDigitization, username, municipalityValidation)
      dao.updateSideCode(id, SideCode.TowardsDigitizing)
      val newId = dao.createSpeedLimit(username, speedLimit.linkId, (speedLimit.startMeasure, speedLimit.endMeasure), SideCode.AgainstDigitizing, valueAgainstDigitization, None).get

      Seq(loadSpeedLimit(id).get, loadSpeedLimit(newId).get)
    }
  }

  /**
    * Returns speed limits by municipality. Used by IntegrationApi speed_limits endpoint.
    */
  def get(municipality: Int): Seq[SpeedLimit] = {

    val (roadLinks, changes) = roadLinkServiceImplementation.getRoadLinksAndChangesFromVVH(municipality)
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
        dao.createSpeedLimit(username, limit.linkId, (limit.startMeasure, limit.endMeasure), SideCode.BothDirections, value, municipalityValidation)
      }
      eventbus.publish("speedLimits:purgeUnknownLimits", newLimits.map(_.linkId).toSet)
      createdIds
    }
  }
}
