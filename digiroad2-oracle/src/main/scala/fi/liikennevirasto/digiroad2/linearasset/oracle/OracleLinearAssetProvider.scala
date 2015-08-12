package fi.liikennevirasto.digiroad2.linearasset.oracle

import fi.liikennevirasto.digiroad2.SpeedLimitFiller.{SideCodeAdjustment, MValueAdjustment}
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.{SideCode, BoundingRectangle}
import fi.liikennevirasto.digiroad2.asset.oracle.AssetPropertyConfiguration
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase._
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

import slick.driver.JdbcDriver.backend.Database
import slick.jdbc.{StaticQuery => Q}

// FIXME:
// - rename to speed limit service
// - move common asset functionality to asset service
class OracleLinearAssetProvider(eventbus: DigiroadEventBus, roadLinkServiceImplementation: RoadLinkService = RoadLinkService) extends LinearAssetProvider {
  val dao: OracleLinearAssetDao = new OracleLinearAssetDao {
    override val roadLinkService: RoadLinkService = roadLinkServiceImplementation
  }
  val logger = LoggerFactory.getLogger(getClass)
  def withDynTransaction[T](f: => T): T = Database.forDataSource(ds).withDynTransaction(f)

  override def getSpeedLimits(bounds: BoundingRectangle, municipalities: Set[Int]): Seq[Seq[SpeedLimit]] = {
    withDynTransaction {
      val (speedLimitLinks, linkGeometries) = dao.getSpeedLimitLinksByBoundingBox(bounds, municipalities)
      val speedLimits = speedLimitLinks.groupBy(_.id)

      val (filledTopology, speedLimitChangeSet) = SpeedLimitFiller.fillTopology(linkGeometries, speedLimits)
      eventbus.publish("speedLimits:update", speedLimitChangeSet)
      val roadIdentifiers = linkGeometries.mapValues(_.roadIdentifier).filter(_._2.isDefined).mapValues(_.get)
      SpeedLimitPartitioner.partition(filledTopology, roadIdentifiers)
    }
  }

  override def getSpeedLimits(ids: Seq[Long]): Seq[SpeedLimit] = {
    withDynTransaction {
      ids.flatMap(loadSpeedLimit)
    }
  }

  override def getSpeedLimit(speedLimitId: Long): Option[SpeedLimit] = {
    withDynTransaction {
     loadSpeedLimit(speedLimitId)
    }
  }

  private def loadSpeedLimit(speedLimitId: Long): Option[SpeedLimit] = {
    dao.getSpeedLimitLinksById(speedLimitId).headOption
  }

  override def persistMValueAdjustments(adjustments: Seq[MValueAdjustment]): Unit = {
    Database.forDataSource(ds).withDynTransaction {
      adjustments.foreach { adjustment =>
        dao.updateMValues(adjustment.assetId, (adjustment.startMeasure, adjustment.endMeasure))
      }
    }
  }

  override def persistSideCodeAdjustments(adjustments: Seq[SideCodeAdjustment]): Unit = {
    Database.forDataSource(ds).withDynTransaction {
      adjustments.foreach { adjustment =>
        dao.updateSideCode(adjustment.assetId, adjustment.sideCode)
      }
    }
  }

  override def updateSpeedLimitValues(ids: Seq[Long], value: Int, username: String, municipalityValidation: Int => Unit): Seq[Long] = {
    Database.forDataSource(ds).withDynTransaction {
      ids.map(dao.updateSpeedLimitValue(_, value, username, municipalityValidation)).flatten
    }
  }

  override def splitSpeedLimit(id: Long, splitMeasure: Double, existingValue: Int, createdValue: Int, username: String, municipalityValidation: (Int) => Unit): Seq[SpeedLimit] = {
    withDynTransaction {
      val newId = dao.splitSpeedLimit(id, splitMeasure, createdValue, username, municipalityValidation)
      dao.updateSpeedLimitValue(id, existingValue, username, municipalityValidation)
      Seq(loadSpeedLimit(id).get, loadSpeedLimit(newId).get)
    }
  }

  override def separateSpeedLimit(id: Long, valueTowardsDigitization: Int, valueAgainstDigitization: Int, username: String, municipalityValidation: Int => Unit): Seq[SpeedLimit] = {
    Database.forDataSource(ds).withDynTransaction {
      val newId = dao.separateSpeedLimit(id, valueTowardsDigitization, valueAgainstDigitization, username, municipalityValidation)
      Seq(loadSpeedLimit(id).get, loadSpeedLimit(newId).get)
    }
  }

  override def getSpeedLimits(municipality: Int): Seq[SpeedLimit] = {
    Database.forDataSource(ds).withDynTransaction {
      val (speedLimitLinks, roadLinksByMmlId) = dao.getByMunicipality(municipality)
      val (filledTopology, speedLimitChangeSet) = SpeedLimitFiller.fillTopology(roadLinksByMmlId, speedLimitLinks.groupBy(_.id))
      eventbus.publish("speedLimits:update", speedLimitChangeSet)
      filledTopology
    }
  }

  override def markSpeedLimitsFloating(ids: Set[Long]): Unit = {
    Database.forDataSource(ds).withDynTransaction {
      dao.markSpeedLimitsFloating(ids)
    }
  }

  override def createSpeedLimits(newLimits: Seq[NewLimit], value: Int, username: String, municipalityValidation: (Int) => Unit): Seq[Long] = {
    withDynTransaction {
      newLimits.flatMap { limit =>
        dao.createSpeedLimit(username, limit.mmlId, (limit.startMeasure, limit.endMeasure), SideCode.BothDirections, value, municipalityValidation)
      }
    }
  }
}
