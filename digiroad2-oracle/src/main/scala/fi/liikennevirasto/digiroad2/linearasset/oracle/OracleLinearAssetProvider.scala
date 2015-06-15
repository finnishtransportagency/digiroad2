package fi.liikennevirasto.digiroad2.linearasset.oracle

import fi.liikennevirasto.digiroad2.GeometryDirection.GeometryDirection
import fi.liikennevirasto.digiroad2.SpeedLimitFiller.MValueAdjustment
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import fi.liikennevirasto.digiroad2.asset.oracle.{AssetPropertyConfiguration, Queries}
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase._
import fi.liikennevirasto.digiroad2.user.UserProvider
import scala.slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import scala.slick.jdbc.{StaticQuery => Q}
import org.slf4j.LoggerFactory
import fi.liikennevirasto.digiroad2.asset.AdministrativeClass

// FIXME:
// - rename to speed limit service
// - move common asset functionality to asset service
class OracleLinearAssetProvider(eventbus: DigiroadEventBus, roadLinkServiceImplementation: RoadLinkService = RoadLinkService) extends LinearAssetProvider {
  import GeometryDirection._

  val dao: OracleLinearAssetDao = new OracleLinearAssetDao {
    override val roadLinkService: RoadLinkService = roadLinkServiceImplementation
  }
  val logger = LoggerFactory.getLogger(getClass)
  def withDynTransaction[T](f: => T): T = Database.forDataSource(ds).withDynTransaction(f)

  private def toSpeedLimit(linkAndPositionNumber: (Long, Long, Int, Option[Int], Seq[Point], Int, GeometryDirection, Double, Double)): SpeedLimitLink = {
    val (id, mmlId, sideCode, limit, points, positionNumber, geometryDirection, startMeasure, endMeasure) = linkAndPositionNumber

    val towardsLinkChain = geometryDirection match {
      case TowardsLinkChain => true
      case AgainstLinkChain => false
    }

    SpeedLimitLink(id, mmlId, sideCode, limit, points, startMeasure, endMeasure, positionNumber, towardsLinkChain)
  }

  private def getLinkEndpoints(link: (Long, Long, Int, Option[Int], Seq[Point], Double, Double)): (Point, Point) = {
    val (_, _, _, _, points, _, _) = link
    GeometryUtils.geometryEndpoints(points)
  }

  private def getLinksWithPositions(links: Seq[(Long, Long, Int, Option[Int], Seq[Point], Double, Double)]): Seq[SpeedLimitLink] = {
    val linkChain = LinkChain(links, getLinkEndpoints)
    linkChain.map { chainedLink =>
      val (id, mmlId, sideCode, limit, points, startMeasure, endMeasure) = chainedLink.rawLink
      toSpeedLimit((id, mmlId, sideCode, limit, points, chainedLink.linkPosition, chainedLink.geometryDirection, startMeasure, endMeasure))
    }
  }

  override def getSpeedLimits(bounds: BoundingRectangle, municipalities: Set[Int]): Seq[SpeedLimitLink] = {
    withDynTransaction {
      val (speedLimitLinks, linkGeometries) = dao.getSpeedLimitLinksByBoundingBox(bounds, municipalities)
      val speedLimits = speedLimitLinks.groupBy(_.assetId)

      val (filledTopology, speedLimitChangeSet) = SpeedLimitFiller.fillTopology(linkGeometries, speedLimits)
      eventbus.publish("speedLimits:update", speedLimitChangeSet)
      filledTopology
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
        dao.updateEndMeasure(adjustment.assetId, adjustment.mmlId, adjustment.endMeasure)
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

  override def getSpeedLimits(municipality: Int): Seq[Map[String, Any]] = {
    Database.forDataSource(ds).withDynTransaction {
      val (speedLimitLinks, roadLinksByMmlId) = dao.getByMunicipality(municipality)

      val (filledTopology, speedLimitChangeSet) = SpeedLimitFiller.fillTopology(roadLinksByMmlId, speedLimitLinks.groupBy(_.assetId))
      eventbus.publish("speedLimits:update", speedLimitChangeSet)

      filledTopology.map { link =>
        Map ("id" -> (link.id + "-" + link.mmlId),
          "sideCode" -> link.sideCode,
          "points" -> link.points,
          "value" -> link.value,
          "startMeasure" -> link.startMeasure,
          "endMeasure" -> link.endMeasure,
          "mmlId" -> link.mmlId)
      }
    }
  }

  override def markSpeedLimitsFloating(ids: Set[Long]): Unit = {
    Database.forDataSource(ds).withDynTransaction {
      dao.markSpeedLimitsFloating(ids)
    }
  }
}
