package fi.liikennevirasto.digiroad2.linearasset.oracle

import fi.liikennevirasto.digiroad2.SpeedLimitFiller.MValueAdjustment
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import fi.liikennevirasto.digiroad2.asset.oracle.AssetPropertyConfiguration
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase._
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

import scala.slick.driver.JdbcDriver.backend.Database
import scala.slick.jdbc.{StaticQuery => Q}

// FIXME:
// - rename to speed limit service
// - move common asset functionality to asset service
class OracleLinearAssetProvider(eventbus: DigiroadEventBus, roadLinkServiceImplementation: RoadLinkService = RoadLinkService) extends LinearAssetProvider {
  import fi.liikennevirasto.digiroad2.GeometryDirection._

  val dao: OracleLinearAssetDao = new OracleLinearAssetDao {
    override val roadLinkService: RoadLinkService = roadLinkServiceImplementation
  }
  val logger = LoggerFactory.getLogger(getClass)
  def withDynTransaction[T](f: => T): T = Database.forDataSource(ds).withDynTransaction(f)

  private def getLinkEndpoints(link: (Long, Long, Int, Option[Int], Seq[Point], Double, Double)): (Point, Point) = {
    val (_, _, _, _, points, _, _) = link
    GeometryUtils.geometryEndpoints(points)
  }

  private def getLinksWithPositions(links: Seq[(Long, Long, Int, Option[Int], Seq[Point], Double, Double)]): Seq[SpeedLimitLink] = {
    val linkChain = LinkChain(links, getLinkEndpoints)
    linkChain.map { chainedLink =>
      val (id, mmlId, sideCode, limit, points, startMeasure, endMeasure) = chainedLink.rawLink
      SpeedLimitLink(id, mmlId, sideCode, limit, points, startMeasure, endMeasure,
        chainedLink.linkPosition, chainedLink.geometryDirection == TowardsLinkChain)
    }
  }

  private def constructLinkChains(speedLimitLinks: Seq[SpeedLimitDTO]): Seq[SpeedLimitLink] = {
    def getLinkEndpoints(link: SpeedLimitDTO): (Point, Point) = {
      GeometryUtils.geometryEndpoints(link.geometry)
    }
    val linkChain = LinkChain(speedLimitLinks, getLinkEndpoints)
    linkChain.map { chainedLink =>
      val link = chainedLink.rawLink
      SpeedLimitLink(link.assetId, link.mmlId, link.sideCode, link.value,
        link.geometry, link.startMeasure, link.endMeasure,
        chainedLink.linkPosition, chainedLink.geometryDirection == TowardsLinkChain)
    }
  }

  override def getSpeedLimits(bounds: BoundingRectangle, municipalities: Set[Int]): Seq[SpeedLimitLink] = {
    withDynTransaction {
      val (speedLimitLinks, linkGeometries) = dao.getSpeedLimitLinksByBoundingBox(bounds, municipalities)
      val speedLimits = speedLimitLinks.groupBy(_.assetId)

      val (filledTopology, speedLimitChangeSet) = SpeedLimitFiller.fillTopology(linkGeometries, speedLimits)
      eventbus.publish("speedLimits:update", speedLimitChangeSet)
      constructLinkChains(filledTopology)
    }
  }

  override def getSpeedLimits2(bounds: BoundingRectangle, municipalities: Set[Int]): Seq[Seq[SpeedLimitLink]] = {
    withDynTransaction {
      val (speedLimitLinks, linkGeometries) = dao.getSpeedLimitLinksByBoundingBox(bounds, municipalities)
      val speedLimits = speedLimitLinks.groupBy(_.assetId)

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
    val links = dao.getSpeedLimitLinksById(speedLimitId)
    if (links.isEmpty) None
    else {
      val linkEndpoints: List[(Point, Point)] = links.map(getLinkEndpoints).toList
      val limitEndpoints = LinearAsset.calculateEndPoints(linkEndpoints)
      val (modifiedBy, modifiedDateTime, createdBy, createdDateTime, limit) = dao.getSpeedLimitDetails(speedLimitId)
      Some(SpeedLimit(speedLimitId, limit, limitEndpoints,
        modifiedBy, modifiedDateTime.map(AssetPropertyConfiguration.DateTimePropertyFormat.print),
        createdBy, createdDateTime.map(AssetPropertyConfiguration.DateTimePropertyFormat.print),
        getLinksWithPositions(links)))
    }
  }

  override def persistMValueAdjustments(adjustments: Seq[MValueAdjustment]): Unit = {
    Database.forDataSource(ds).withDynTransaction {
      adjustments.foreach { adjustment =>
        dao.updateMValues(adjustment.assetId, adjustment.mmlId, (adjustment.startMeasure, adjustment.endMeasure))
      }
    }
  }

  override def updateSpeedLimitValue(id: Long, value: Int, username: String, municipalityValidation: Int => Unit): Option[Long] = {
    Database.forDataSource(ds).withDynTransaction {
      dao.updateSpeedLimitValue(id, value, username, municipalityValidation)
    }
  }

  override def updateSpeedLimitValues(ids: Seq[Long], value: Int, username: String, municipalityValidation: Int => Unit): Seq[Long] = {
    Database.forDataSource(ds).withDynTransaction {
      ids.map(dao.updateSpeedLimitValue(_, value, username, municipalityValidation)).flatten
    }
  }

  override def splitSpeedLimit(id: Long, mmlId: Long, splitMeasure: Double, limit: Int, username: String, municipalityValidation: Int => Unit): Seq[SpeedLimit] = {
    Database.forDataSource(ds).withDynTransaction {
      val newId = dao.splitSpeedLimit(id, mmlId, splitMeasure, limit, username, municipalityValidation)
      Seq(loadSpeedLimit(id).get, loadSpeedLimit(newId).get)
    }
  }

  override def getSpeedLimits(municipality: Int): Seq[SpeedLimitLink] = {
    Database.forDataSource(ds).withDynTransaction {
      val (speedLimitLinks, roadLinksByMmlId) = dao.getByMunicipality(municipality)
      val (filledTopology, speedLimitChangeSet) = SpeedLimitFiller.fillTopology(roadLinksByMmlId, speedLimitLinks.groupBy(_.assetId))
      eventbus.publish("speedLimits:update", speedLimitChangeSet)
      constructLinkChains(filledTopology)
    }
  }

  override def markSpeedLimitsFloating(ids: Set[Long]): Unit = {
    Database.forDataSource(ds).withDynTransaction {
      dao.markSpeedLimitsFloating(ids)
    }
  }

  override def getSpeedLimitTimeStamps(ids: Set[Long]): Seq[SpeedLimitTimeStamps] = {
    withDynTransaction{
      dao.getSpeedLimitTimeStamps(ids)
    }
  }

  override def createSpeedLimits(newLimits: Seq[NewLimit], value: Int, username: String, municipalityValidation: (Int) => Unit): Seq[Long] = {
    withDynTransaction {
      newLimits.flatMap { limit =>
        dao.createSpeedLimit(username, limit.mmlId, (limit.startMeasure, limit.endMeasure), 1, value, municipalityValidation)
      }
    }
  }

}
