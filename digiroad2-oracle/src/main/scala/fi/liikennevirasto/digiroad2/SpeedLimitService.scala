package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.{BoundingRectangle, SideCode, TrafficDirection}
import fi.liikennevirasto.digiroad2.linearasset.SpeedLimitFiller.Projection
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.linearasset.oracle.{OracleLinearAssetDao, PersistedSpeedLimit}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

case class ChangedSpeedLimit(speedLimit: SpeedLimit, link: RoadLink)

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

  /**
    * Returns speed limits for Digiroad2Api /speedlimits GET endpoint.
    */
  def get(bounds: BoundingRectangle, municipalities: Set[Int]): Seq[Seq[SpeedLimit]] = {

    val (roadLinks, change) = roadLinkServiceImplementation.getRoadLinksAndChangesFromVVH(bounds, municipalities)
    withDynTransaction {
      val (speedLimitLinks, topology) = dao.getSpeedLimitLinksByRoadLinks(roadLinks)

      // find timestamps and ids from change and speedlimitLinks
      val changeRoadLinkTimeStampsAndIds = change.map(a => a.newId.getOrElse(None) -> a.vvhTimeStamp.getOrElse(None))
      val speedLimitLinksVvhTimeStampsAndIds = speedLimitLinks.map(s => s.linkId -> s.vvhTimeStamp)
      val merged = changeRoadLinkTimeStampsAndIds.intersect(speedLimitLinksVvhTimeStampsAndIds)

      // filter those roadlinks that have already been projected = vvhTimeStamp is same
      val filteredSpeedLimitLinks = speedLimitLinks.filter(sl => merged.map(m => m._1).contains(sl.linkId))

      val projectableRoadLinks = roadLinks.filter(_.isCarTrafficRoad).filterNot(rl => filteredSpeedLimitLinks.map(sl => sl.linkId).contains(rl.linkId))

      val (oldSpeedLimits, newSpeedLimits) = fillNewRoadLinksWithPreviousSpeedLimitData(projectableRoadLinks, change)
      val speedLimits = speedLimitLinks.groupBy(_.linkId) ++ newSpeedLimits.groupBy(_.linkId)
      val roadLinksByLinkId = topology.groupBy(_.linkId).mapValues(_.head)

      // TODO: Now save the earlier speed limits to have valid_to date to now and save the vvh time stamp in them as well
      // in Actor: oldSpeedLimit.validTo -> current_timestamp. Change ChangeSet class to include Set of expired asset ids?
      //         newSpeedLimit.id -> sequence value
      //         newSpeedLimit.LRM_timestamp -> current_timestamp

      val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology, speedLimits)
      eventbus.publish("linearAssets:update", changeSet)
      eventbus.publish("speedLimits:saveProjectedSpeedLimits", newSpeedLimits)

      eventbus.publish("speedLimits:purgeUnknownLimits", changeSet.adjustedMValues.map(_.linkId).toSet)

      val unknownLimits = createUnknownLimits(filledTopology, roadLinksByLinkId)
      eventbus.publish("speedLimits:persistUnknownLimits", unknownLimits)

      LinearAssetPartitioner.partition(filledTopology, roadLinksByLinkId)
    }
  }

  def getChanged(sinceDate: DateTime): Seq[ChangedSpeedLimit] = {
    val persistedSpeedLimits = withDynTransaction {
      dao.getSpeedLimitsChangedSince(sinceDate)
    }
    val roadLinks = roadLinkServiceImplementation.getRoadLinksFromVVH(persistedSpeedLimits.map(_.linkId).toSet)

    persistedSpeedLimits.flatMap { speedLimit =>
      roadLinks.find(_.linkId == speedLimit.linkId).map { roadLink =>
        ChangedSpeedLimit(
          speedLimit = SpeedLimit(
            id = speedLimit.id,
            linkId = speedLimit.linkId,
            sideCode = speedLimit.sideCode,
            trafficDirection = roadLink.trafficDirection,
            value = speedLimit.value.map(NumericValue),
            geometry = GeometryUtils.truncateGeometry(roadLink.geometry, speedLimit.startMeasure, speedLimit.endMeasure),
            startMeasure = speedLimit.startMeasure,
            endMeasure = speedLimit.endMeasure,
            modifiedBy = speedLimit.modifiedBy, modifiedDateTime = speedLimit.modifiedDate,
            createdBy = speedLimit.createdBy, createdDateTime = speedLimit.createdDate
          ),
          link = roadLink
        )
      }
    }
  }

  /**
    * Uses VVH ChangeInfo API to map OTH speed limit information from old road links to new road links after geometry changes.
    */
  def fillNewRoadLinksWithPreviousSpeedLimitData(roadLinks: Seq[RoadLink], changes: Seq[ChangeInfo]) : (Seq[SpeedLimit], Seq[SpeedLimit]) ={
    val oldRoadLinkIds = changes.flatMap(_.oldId)
    val oldSpeedLimits = dao.getCurrentSpeedLimitsByLinkIds(Some(oldRoadLinkIds.toSet)).toSeq
    val newSpeedLimits = mapReplacementProjections(oldSpeedLimits, roadLinks, changes).flatMap(
      limit =>
        limit match {
          case (speedLimit, (Some(roadLink), Some(projection))) =>
            Some(SpeedLimitFiller.projectSpeedLimit(speedLimit, roadLink, projection))
          case (_, (_, _)) =>
            None
        }).filter(sl => Math.abs(sl.startMeasure - sl.endMeasure) > 0) // Remove zero-length or invalid length parts
    (oldSpeedLimits, newSpeedLimits)
  }

  def mapReplacementProjections(oldSpeedLimits: Seq[SpeedLimit], newRoadLinks: Seq[RoadLink], changes: Seq[ChangeInfo]) : Seq[(SpeedLimit, (Option[RoadLink], Option[Projection]))] = {
    val targetLinks = changes.flatMap(_.newId).toSet
    oldSpeedLimits.flatMap(limit =>
      newRoadLinks.filter(rl => targetLinks.contains(rl.linkId)).map(newRoadLink =>
        (limit, getRoadLinkAndProjection(newRoadLinks, changes, limit.linkId, newRoadLink.linkId))
      ))
  }

  def getRoadLinkAndProjection(roadLinks: Seq[RoadLink], changes: Seq[ChangeInfo], oldId: Long, newId: Long ) = {
    val roadLink = roadLinks.find(rl => newId == rl.linkId)
    val changeInfo = changes.find(c => c.oldId.getOrElse(0) == oldId && c.newId.getOrElse(0) == newId)
    val projection = changeInfo match {
      case Some(info) => mapChangeToProjection(info)
      case _ => None
    }
    (roadLink,projection)
  }

  def mapChangeToProjection(change: ChangeInfo) = {
    // TODO: Do different type of change info handling here
    val typed = ChangeType.apply(change.changeType)
    typed match {
      case ChangeType.DividedModifiedPart => Some(Projection(change.oldStartMeasure.getOrElse(Double.NaN), change.oldEndMeasure.getOrElse(Double.NaN),
        change.newStartMeasure.getOrElse(Double.NaN), change.newEndMeasure.getOrElse(Double.NaN), change.vvhTimeStamp.getOrElse(0)))
      case ChangeType.DividedNewPart => Some(Projection(change.oldStartMeasure.getOrElse(Double.NaN), change.oldEndMeasure.getOrElse(Double.NaN),
        change.newStartMeasure.getOrElse(Double.NaN), change.newEndMeasure.getOrElse(Double.NaN), change.vvhTimeStamp.getOrElse(0)))
      case ChangeType.CombinedModifiedPart => Some(Projection(change.oldStartMeasure.getOrElse(Double.NaN), change.oldEndMeasure.getOrElse(Double.NaN),
        change.newStartMeasure.getOrElse(Double.NaN), change.newEndMeasure.getOrElse(Double.NaN), change.vvhTimeStamp.getOrElse(0)))
      case ChangeType.CombinedRemovedPart => Some(Projection(change.oldStartMeasure.getOrElse(Double.NaN), change.oldEndMeasure.getOrElse(Double.NaN),
        change.newStartMeasure.getOrElse(Double.NaN), change.newEndMeasure.getOrElse(Double.NaN), change.vvhTimeStamp.getOrElse(0)))
      case ChangeType.LenghtenedCommonPart => Some(Projection(change.oldStartMeasure.getOrElse(Double.NaN), change.oldEndMeasure.getOrElse(Double.NaN),
        change.newStartMeasure.getOrElse(Double.NaN), change.newEndMeasure.getOrElse(Double.NaN), change.vvhTimeStamp.getOrElse(0)))
      case ChangeType.LengthenedNewPart => Some(Projection(change.oldStartMeasure.getOrElse(Double.NaN), change.oldEndMeasure.getOrElse(Double.NaN),
        change.newStartMeasure.getOrElse(Double.NaN), change.newEndMeasure.getOrElse(Double.NaN), change.vvhTimeStamp.getOrElse(0)))
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
      limits.foreach { limit =>
        dao.createSpeedLimit("vvh_generated", limit.linkId, (limit.startMeasure, limit.endMeasure),limit.sideCode, limit.value.get.value, Some(limit.vvhTimeStamp))
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
      val (speedLimitLinks, topology) = dao.getSpeedLimitLinksByRoadLinks(roadLinks)
      val roadLinksByLinkId = topology.groupBy(_.linkId).mapValues(_.head)

      val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology, speedLimitLinks.groupBy(_.linkId))
      eventbus.publish("linearAssets:update", changeSet)

      val unknownLimits = createUnknownLimits(filledTopology, roadLinksByLinkId)
      eventbus.publish("speedLimits:persistUnknownLimits", unknownLimits)

      filledTopology
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
