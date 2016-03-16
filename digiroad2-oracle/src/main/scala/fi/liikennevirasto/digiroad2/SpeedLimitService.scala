package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.{BoundingRectangle, SideCode, TrafficDirection}
import fi.liikennevirasto.digiroad2.linearasset.SpeedLimitFiller.Projection
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.linearasset.oracle.{OracleLinearAssetDao, PersistedSpeedLimit}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import org.slf4j.LoggerFactory
import slick.jdbc.{StaticQuery => Q}

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
      val speedLimits = speedLimitLinks.groupBy(_.linkId)
      val roadLinksByLinkId = topology.groupBy(_.linkId).mapValues(_.head)

      println("change: " + change)
      // wip: get data for projection
      val newSpeedLimits = fillNewRoadLinksWithPreviousSpeedLimitData(roadLinks, change)
      // TODO: Now save the earlier speed limits to have valid_to date to now and save the vvh time stamp in them as well

      println("new speed limits: " + newSpeedLimits)

      val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology, speedLimits)
      eventbus.publish("linearAssets:update", changeSet)

      eventbus.publish("speedLimits:purgeUnknownLimits", changeSet.adjustedMValues.map(_.linkId).toSet)

      val unknownLimits = createUnknownLimits(filledTopology, roadLinksByLinkId)
      eventbus.publish("speedLimits:persistUnknownLimits", unknownLimits)

      LinearAssetPartitioner.partition(filledTopology, roadLinksByLinkId)
    }
  }

  /**
    * Uses VVH ChangeInfo API to map OTH speed limit information from old road links to new road links after geometry changes.
    */
  def fillNewRoadLinksWithPreviousSpeedLimitData(roadLinks: Seq[RoadLink], changes: Seq[ChangeInfo]) ={
    val oldRoadLinkIds = changes.flatMap(_.oldId)
    println(dao.getSpeedLimitsByIds(Some(oldRoadLinkIds.toSet)))
    val oldSpeedLimits = dao.getSpeedLimitsByIds(Some(oldRoadLinkIds.toSet)).toSeq.groupBy(_.linkId)
    val projections = changeListToProjection(roadLinks, changes)

    println("old links: " + oldRoadLinkIds)
    println("old speed limits: " + oldSpeedLimits)
    projections.map { p =>
      p match {
        case ((Some(from), Some(to)), _) =>
          println(" projection from " + from + " to " + to + ": " + p._2)
          val toLink = roadLinks.find(link => link.linkId == to)
          println(" projection to " + toLink)
          val projection = p._2
          oldSpeedLimits.get(from).map(
            speedLimits =>
              speedLimits.filter(limit => limit.vvhTimeStamp < projection.vvhTimeStamp).map(
                limit =>
                  SpeedLimitFiller.projectSpeedLimit(limit, toLink.get, projection)))
        case ((_, _), _) =>
          println("ignored: " + p)
      }
    }
  }

  def changeListToProjection(roadLinks: Seq[RoadLink], changes: Seq[ChangeInfo]): Map[(Option[Long], Option[Long]), Projection] = {
    changes.map(change => (change.oldId, change.newId) -> mapChangeToProjection(change)).toMap
  }

  def mapChangeToProjection(change: ChangeInfo) = {
    // TODO: Do different type of change info handling here
    change.changeType match {
      case 5 => Projection(change.oldStartMeasure.getOrElse(0), change.oldEndMeasure.getOrElse(0),
        change.newStartMeasure.getOrElse(0), change.newEndMeasure.getOrElse(0), change.vvhTimeStamp.getOrElse(0))
      case 6 => Projection(change.oldStartMeasure.getOrElse(0), change.oldEndMeasure.getOrElse(0),
        change.newStartMeasure.getOrElse(0), change.newEndMeasure.getOrElse(0), change.vvhTimeStamp.getOrElse(0))
      case _ => Projection(0,0,0,0,0)
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
      val newId = dao.createSpeedLimit(username, speedLimit.linkId, (speedLimit.startMeasure, speedLimit.endMeasure), SideCode.AgainstDigitizing, valueAgainstDigitization).get

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
