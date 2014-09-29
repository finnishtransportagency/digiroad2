package fi.liikennevirasto.digiroad2.linearasset.oracle

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import fi.liikennevirasto.digiroad2.asset.oracle.{AssetPropertyConfiguration, Queries}
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase._
import fi.liikennevirasto.digiroad2.user.UserProvider
import fi.liikennevirasto.digiroad2.util.{GeometryUtils, SpeedLimitLinkPositions}

import scala.slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import scala.slick.jdbc.{StaticQuery => Q}

class OracleLinearAssetProvider extends LinearAssetProvider {
  private def toSpeedLimit(linkAndPositionNumber: ((Long, Long, Int, Int, Seq[(Double, Double)]), Int)): SpeedLimitLink = {
    val ((id, roadLinkId, sideCode, limit, points), positionNumber) = linkAndPositionNumber
    SpeedLimitLink(id, roadLinkId, sideCode, limit, points.map { case (x, y) => Point(x, y) }, positionNumber)
  }

  private def getLinkEndpoints(link: (Long, Long, Int, Int, Seq[(Double, Double)])): (Point, Point) = {
    val (_, _, _, _, points) = link
    GeometryUtils.geometryEndpoints(points)
  }

  private def getLinksWithPositions(links: Seq[(Long, Long, Int, Int, Seq[(Double, Double)])]): Seq[SpeedLimitLink] = {
    val linkEndpoints: Seq[(Point, Point)] = links.map(getLinkEndpoints)
    val positionNumbers = SpeedLimitLinkPositions.generate(linkEndpoints)
    links.zip(positionNumbers).map(toSpeedLimit)
  }

  override def getSpeedLimits(bounds: BoundingRectangle): Seq[SpeedLimitLink] = {
    Database.forDataSource(ds).withDynTransaction {
      OracleLinearAssetDao.getSpeedLimitLinksByBoundingBox(bounds).groupBy(_._1).mapValues(getLinksWithPositions).values.flatten.toSeq
    }
  }

  def calculateSpeedLimitEndPoints(links: List[(Point, Point)]): Set[Point] = {
    val positionIndices = SpeedLimitLinkPositions.generate(links)
    val linksWithPositionIndices: Seq[((Point, Point), Int)] = links.zip(positionIndices)
    val orderedLinks = linksWithPositionIndices.sortBy(_._2)
    Set(orderedLinks.head._1._1, orderedLinks.last._1._2)
 }

  override def getSpeedLimit(speedLimitId: Long): Option[SpeedLimit] = {
    Database.forDataSource(ds).withDynTransaction {
      loadSpeedLimit(speedLimitId)
    }
  }

  private def loadSpeedLimit(speedLimitId: Long): Option[SpeedLimit] = {
    val links = OracleLinearAssetDao.getSpeedLimitLinks(speedLimitId)
    if (links.isEmpty) None
    else {
      val points: List[(Point, Point)] = links.map { link =>
        val first = link._2.head
        val last = link._2.last
        (Point(first._1, first._2), Point(last._1, last._2))
      }.toList
      val endpoints = calculateSpeedLimitEndPoints(points)
      val (modifiedBy, modifiedDateTime, createdBy, createdDateTime, limit, speedLimitLinks) = OracleLinearAssetDao.getSpeedLimitDetails(speedLimitId)
      Some(SpeedLimit(speedLimitId, limit, endpoints,
        modifiedBy, modifiedDateTime.map(AssetPropertyConfiguration.DateTimePropertyFormat.print),
        createdBy, createdDateTime.map(AssetPropertyConfiguration.DateTimePropertyFormat.print),
        getLinksWithPositions(speedLimitLinks)))
    }
  }

  override def updateSpeedLimitValue(id: Long, value: Int, username: String): Option[Long] = {
    Database.forDataSource(ds).withDynTransaction {
      val propertyId = Q.query[String, Long](Queries.propertyIdByPublicId).firstOption("rajoitus").get
      val assetsUpdated = Queries.updateAssetModified(id, username).first
      val propertiesUpdated = Queries.updateSingleChoiceProperty(id, propertyId, value.toLong).first
      if (assetsUpdated == 1 && propertiesUpdated == 1) {
        Some(id)
      } else {
        dynamicSession.rollback()
        None
      }
    }
  }

  override def splitSpeedLimit(id: Long, roadLinkId: Long, splitMeasure: Double, username: String): Seq[SpeedLimit] = {
    Database.forDataSource(ds).withDynTransaction {
      val newId = OracleLinearAssetDao.splitSpeedLimit(id, roadLinkId, splitMeasure, username)
      Seq(loadSpeedLimit(id).get, loadSpeedLimit(newId).get)
    }
  }
}
