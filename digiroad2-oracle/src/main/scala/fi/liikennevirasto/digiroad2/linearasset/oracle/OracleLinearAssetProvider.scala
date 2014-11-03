package fi.liikennevirasto.digiroad2.linearasset.oracle

import fi.liikennevirasto.digiroad2.{DigiroadEventBus, Point}
import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import fi.liikennevirasto.digiroad2.asset.oracle.{AssetPropertyConfiguration, Queries}
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase._
import fi.liikennevirasto.digiroad2.user.UserProvider
import fi.liikennevirasto.digiroad2.util.{LinkChain, GeometryUtils, SpeedLimitLinkPositions}
import scala.slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import scala.slick.jdbc.{StaticQuery => Q}
import org.slf4j.LoggerFactory

class OracleLinearAssetProvider(eventbus: DigiroadEventBus) extends LinearAssetProvider {
  val logger = LoggerFactory.getLogger(getClass)

  private def toSpeedLimit(linkAndPositionNumber: ((Long, Long, Int, Int, Seq[Point]), Int)): SpeedLimitLink = {
    val ((id, roadLinkId, sideCode, limit, points), positionNumber) = linkAndPositionNumber
    SpeedLimitLink(id, roadLinkId, sideCode, limit, points, positionNumber)
  }

  private def getLinkEndpoints(link: (Long, Long, Int, Int, Seq[Point])): (Point, Point) = {
    val (_, _, _, _, points) = link
    GeometryUtils.geometryEndpoints(points)
  }

  private def getLinksWithPositions(links: Seq[(Long, Long, Int, Int, Seq[Point])]): Seq[SpeedLimitLink] = {
    val linkPositions = LinkChain(links, getLinkEndpoints).linkPositions()
    links.zip(linkPositions).map(toSpeedLimit)
  }

  override def getSpeedLimits(bounds: BoundingRectangle): Seq[SpeedLimitLink] = {
    Database.forDataSource(ds).withDynTransaction {
      val (speedLimits, linkGeometries) = OracleLinearAssetDao.getSpeedLimitLinksByBoundingBox(bounds)
      eventbus.publish("speedLimits:linkGeometriesRetrieved", linkGeometries)
      speedLimits.groupBy(_._1).mapValues(getLinksWithPositions).values.flatten.toSeq
    }
  }

  def calculateSpeedLimitEndPoints(links: List[(Point, Point)]): Set[Point] = {
    val endPoints = SpeedLimitLinkPositions.calculateLinkChainEndPoints(links)
    Set(endPoints._1, endPoints._2)
 }

  override def getSpeedLimit(speedLimitId: Long): Option[SpeedLimit] = {
    Database.forDataSource(ds).withDynTransaction {
      loadSpeedLimit(speedLimitId)
    }
  }

  private def loadSpeedLimit(speedLimitId: Long): Option[SpeedLimit] = {
    val links = OracleLinearAssetDao.getSpeedLimitLinksById(speedLimitId)
    if (links.isEmpty) None
    else {
      val linkEndpoints: List[(Point, Point)] = links.map(getLinkEndpoints).toList
      val limitEndpoints = calculateSpeedLimitEndPoints(linkEndpoints)
      val (modifiedBy, modifiedDateTime, createdBy, createdDateTime, limit, speedLimitLinks) = OracleLinearAssetDao.getSpeedLimitDetails(speedLimitId)
      Some(SpeedLimit(speedLimitId, limit, limitEndpoints,
        modifiedBy, modifiedDateTime.map(AssetPropertyConfiguration.DateTimePropertyFormat.print),
        createdBy, createdDateTime.map(AssetPropertyConfiguration.DateTimePropertyFormat.print),
        getLinksWithPositions(speedLimitLinks)))
    }
  }

  override def updateSpeedLimitValue(id: Long, value: Int, username: String): Option[Long] = {
    Database.forDataSource(ds).withDynTransaction {
      OracleLinearAssetDao.updateSpeedLimitValue(id, value, username)
    }
  }

  override def splitSpeedLimit(id: Long, roadLinkId: Long, splitMeasure: Double, limit: Int, username: String): Seq[SpeedLimit] = {
    Database.forDataSource(ds).withDynTransaction {
      val newId = OracleLinearAssetDao.splitSpeedLimit(id, roadLinkId, splitMeasure, limit, username)
      Seq(loadSpeedLimit(id).get, loadSpeedLimit(newId).get)
    }
  }

  override def fillPartiallyFilledRoadLinks(linkGeometries: Map[Long, (Seq[Point], Double, Int)]): Unit = {
    Database.forDataSource(ds).withDynTransaction {
      logger.info("Filling partially filled road links, link count: " + linkGeometries.size)
      OracleLinearAssetDao.fillPartiallyFilledRoadLinks(linkGeometries)
      logger.info("...done with filling.")
    }
  }
}
