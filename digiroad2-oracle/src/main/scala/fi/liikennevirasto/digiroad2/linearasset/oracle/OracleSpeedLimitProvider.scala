package fi.liikennevirasto.digiroad2.linearasset.oracle

import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.{BoundingRectangle, SideCode}
import fi.liikennevirasto.digiroad2.linearasset.SpeedLimitFiller.{MValueAdjustment, SideCodeAdjustment, UnknownLimit}
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import org.slf4j.LoggerFactory
import slick.jdbc.{StaticQuery => Q}

// FIXME:
// - move common asset functionality to asset service
class OracleSpeedLimitProvider(eventbus: DigiroadEventBus, roadLinkServiceImplementation: RoadLinkService = RoadLinkService) extends SpeedLimitProvider {
  val dao: OracleLinearAssetDao = new OracleLinearAssetDao {
    override val roadLinkService: RoadLinkService = roadLinkServiceImplementation
  }
  val logger = LoggerFactory.getLogger(getClass)

  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)

  override def getUnknown(municipalities: Option[Set[Int]]): Map[String, Map[String, Any]] = {
    withDynSession {
      dao.getUnknownSpeedLimits(municipalities)
    }
  }

  override def purgeUnknown(mmlIds: Set[Long]): Unit = {
    val roadLinks = roadLinkServiceImplementation.fetchVVHRoadlinks(mmlIds)
    withDynTransaction {
      roadLinks.foreach { rl =>
        dao.purgeFromUnknownSpeedLimits(rl.mmlId, GeometryUtils.geometryLength(rl.geometry))
      }
    }
  }

  override def get(bounds: BoundingRectangle, municipalities: Set[Int]): Seq[Seq[SpeedLimit]] = {
    val roadLinks = roadLinkServiceImplementation.getRoadLinksFromVVH(bounds, municipalities)
    withDynTransaction {
      val (speedLimitLinks, linkGeometries) = dao.getSpeedLimitLinksByRoadLinks(roadLinks)
      val speedLimits = speedLimitLinks.groupBy(_.id)

      val (filledTopology, speedLimitChangeSet) = SpeedLimitFiller.fillTopology(linkGeometries, speedLimits)
      eventbus.publish("speedLimits:update", speedLimitChangeSet)
      val roadIdentifiers = linkGeometries
        .groupBy(_.mmlId)
        .mapValues(_.head)
        .mapValues(_.roadIdentifier)
        .filter(_._2.isDefined)
        .mapValues(_.get)
      SpeedLimitPartitioner.partition(filledTopology, roadIdentifiers)
    }
  }

  override def get(ids: Seq[Long]): Seq[SpeedLimit] = {
    withDynTransaction {
      ids.flatMap(loadSpeedLimit)
    }
  }

  override def find(speedLimitId: Long): Option[SpeedLimit] = {
    withDynTransaction {
     loadSpeedLimit(speedLimitId)
    }
  }

  private def loadSpeedLimit(speedLimitId: Long): Option[SpeedLimit] = {
    dao.getSpeedLimitLinksById(speedLimitId).headOption
  }

  override def persistMValueAdjustments(adjustments: Seq[MValueAdjustment]): Unit = {
    withDynTransaction {
      adjustments.foreach { adjustment =>
        dao.updateMValues(adjustment.assetId, (adjustment.startMeasure, adjustment.endMeasure))
      }
    }
  }

  override def persistSideCodeAdjustments(adjustments: Seq[SideCodeAdjustment]): Unit = {
    withDynTransaction {
      adjustments.foreach { adjustment =>
        dao.updateSideCode(adjustment.assetId, adjustment.sideCode)
      }
    }
  }

  override def persistUnknown(limits: Seq[UnknownLimit]): Unit = {
    withDynTransaction {
      dao.persistUnknownSpeedLimits(limits)
    }
  }

  override def updateValues(ids: Seq[Long], value: Int, username: String, municipalityValidation: Int => Unit): Seq[Long] = {
    withDynTransaction {
      ids.map(dao.updateSpeedLimitValue(_, value, username, municipalityValidation)).flatten
    }
  }

  override def split(id: Long, splitMeasure: Double, existingValue: Int, createdValue: Int, username: String, municipalityValidation: (Int) => Unit): Seq[SpeedLimit] = {
    withDynTransaction {
      val newId = dao.splitSpeedLimit(id, splitMeasure, createdValue, username, municipalityValidation)
      dao.updateSpeedLimitValue(id, existingValue, username, municipalityValidation)
      Seq(loadSpeedLimit(id).get, loadSpeedLimit(newId).get)
    }
  }

  override def separate(id: Long, valueTowardsDigitization: Int, valueAgainstDigitization: Int, username: String, municipalityValidation: Int => Unit): Seq[SpeedLimit] = {
    withDynTransaction {
      val newId = dao.separateSpeedLimit(id, valueTowardsDigitization, valueAgainstDigitization, username, municipalityValidation)
      Seq(loadSpeedLimit(id).get, loadSpeedLimit(newId).get)
    }
  }

  override def get(municipality: Int): Seq[SpeedLimit] = {
    val roadLinks = roadLinkServiceImplementation.getRoadLinksFromVVH(municipality)
    withDynTransaction {
      val (speedLimitLinks, roadLinksByMmlId) = dao.getSpeedLimitLinksByRoadLinks(roadLinks)
      val (filledTopology, speedLimitChangeSet) = SpeedLimitFiller.fillTopology(roadLinksByMmlId, speedLimitLinks.groupBy(_.id))
      eventbus.publish("speedLimits:update", speedLimitChangeSet)
      filledTopology
    }
  }

  override def drop(ids: Set[Long]): Unit = {
    withDynTransaction {
      dao.floatLinearAssets(ids)
    }
  }

  override def create(newLimits: Seq[NewLimit], value: Int, username: String, municipalityValidation: (Int) => Unit): Seq[Long] = {
    withDynTransaction {
      val createdIds = newLimits.flatMap { limit =>
        dao.createSpeedLimit(username, limit.mmlId, (limit.startMeasure, limit.endMeasure), SideCode.BothDirections, value, municipalityValidation)
      }
      eventbus.publish("speedLimits:purgeUnknown", newLimits.map(_.mmlId).toSet)
      createdIds
    }
  }
}
