package fi.liikennevirasto.digiroad2.linearasset.oracle

import fi.liikennevirasto.digiroad2.{RoadLinkService, Point}
import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase._
import fi.liikennevirasto.digiroad2.asset.oracle.Queries._
import fi.liikennevirasto.digiroad2.util.{GeometryUtils}
import oracle.jdbc.OracleConnection

import org.scalatest.FunSuite
import org.scalatest.Matchers
import org.scalatest.Tag

import oracle.spatial.geometry.JGeometry
import scala.slick.jdbc.{StaticQuery => Q}
import Q.interpolation
import scala.slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession

class OracleLinearAssetDaoSpec extends FunSuite with Matchers {

  private def truncateLinkGeometry(linkId: Long, startMeasure: Double, endMeasure: Double): Seq[Point] = {
    RoadLinkService.getRoadLinkGeometry(linkId, startMeasure, endMeasure)
  }

  def assertSpeedLimitEndPointsOnLink(speedLimitId: Long, roadLinkId: Long, startMeasure: Double, endMeasure: Double) = {
    val expectedEndPoints = GeometryUtils.geometryEndpoints(truncateLinkGeometry(roadLinkId, startMeasure, endMeasure).toList)
    val limitEndPoints = GeometryUtils.geometryEndpoints(OracleLinearAssetDao.getSpeedLimitLinksWithLength(speedLimitId).find { link => link._1 == roadLinkId }.get._3)
    expectedEndPoints._1.distanceTo(limitEndPoints._1) should be(0.0 +- 0.01)
    expectedEndPoints._2.distanceTo(limitEndPoints._2) should be(0.0 +- 0.01)
  }

  test("splitting one link speed limit " +
    "where split measure is after link middle point " +
    "modifies end measure of existing speed limit " +
    "and creates new speed limit for second split", Tag("db")) {
    Database.forDataSource(ds).withDynTransaction {
      val createdId = OracleLinearAssetDao.splitSpeedLimit(200097, 6551, 100, 120, "test")
      val (existingModifiedBy, _, _, _, _, _) = OracleLinearAssetDao.getSpeedLimitDetails(200097)
      val (_, _, newCreatedBy, _, _, _) = OracleLinearAssetDao.getSpeedLimitDetails(createdId)

      assertSpeedLimitEndPointsOnLink(200097, 6551, 0, 100)
      assertSpeedLimitEndPointsOnLink(createdId, 6551, 100, 136.788)

      existingModifiedBy shouldBe Some("test")
      newCreatedBy shouldBe Some("test")
      dynamicSession.rollback()
    }
  }

  test("splitting one link speed limit " +
    "where split measure is before link middle point " +
    "modifies start measure of existing speed limit " +
    "and creates new speed limit for first split", Tag("db")) {
    Database.forDataSource(ds).withDynTransaction {
      val createdId = OracleLinearAssetDao.splitSpeedLimit(200097, 6551, 50, 120, "test")
      val (modifiedBy, _, _, _, _, _) = OracleLinearAssetDao.getSpeedLimitDetails(200097)
      val (_, _, newCreatedBy, _, _, _) = OracleLinearAssetDao.getSpeedLimitDetails(createdId)

      assertSpeedLimitEndPointsOnLink(200097, 6551, 50, 136.788)
      assertSpeedLimitEndPointsOnLink(createdId, 6551, 0, 50)

      modifiedBy shouldBe Some("test")
      newCreatedBy shouldBe Some("test")
      dynamicSession.rollback()
    }
  }

  test("splitting three link speed limit where first split is longer than second should allocate first split to existing limit") {
    val link1 = (0l, 154.0, (Point(372530, 6676811), Point(372378, 6676808)))
    val link2 = (1l, 87.0, (Point(372614, 6676793), Point(372530, 6676811)))
    val link3 = (2l, 224.0, (Point(372378, 6676808), Point(372164, 6676763)))
    val (existingLinkMeasures, createdLinkMeasures, linksToMove) = OracleLinearAssetDao.createSpeedLimitSplit(150.0, (0, 0.0, 154.0), Seq(link1, link2, link3))

    existingLinkMeasures shouldBe(0.0, 150.0)
    createdLinkMeasures shouldBe(150.0, 154.0)
    linksToMove.map(_._1) shouldBe Seq(2)
  }

  test("splitting three link speed limit " +
    "where first split is shorter than second " +
    "existing speed limit should cover only second split", Tag("db")) {
    Database.forDataSource(ds).withDynTransaction {
      OracleLinearAssetDao.splitSpeedLimit(200217, 6871, 10, 120, "test")
      val existingLinks = OracleLinearAssetDao.getSpeedLimitLinksWithLength(200217)

      existingLinks.length shouldBe 2
      existingLinks.map(_._1) should contain only (6871, 7294)
      dynamicSession.rollback()
    }
  }

  test("splitting speed limit " +
    "so that shorter split contains multiple linear references " +
    "moves all linear references to newly created speed limit", Tag("db")) {
    Database.forDataSource(ds).withDynTransaction {
      val createdId = OracleLinearAssetDao.splitSpeedLimit(200363, 7230, 148, 120, "test")
      val createdLinks = OracleLinearAssetDao.getSpeedLimitLinksWithLength(createdId)

      createdLinks.length shouldBe 3
      createdLinks.map(_._1) should contain only (6710, 6740, 7230)
      dynamicSession.rollback()
    }
  }

  test("splitting speed limit where all links run against link indexing") {
    val link1 = (0l, 20.0, (Point(20.0, 0.0), Point(0.0, 0.0)))
    val link2 = (1l, 16.0, (Point(36.0, 0.0), Point(20.0, 0.0)))
    val link3 = (2l, 10.0, (Point(46.0, 0.0), Point(36.0, 0.0)))
    val (existingLinkMeasures, createdLinkMeasures, linksToMove) = OracleLinearAssetDao.createSpeedLimitSplit(15.0, (1, 0.0, 16.0), Seq(link1, link2, link3))

    existingLinkMeasures shouldBe (0.0, 15.0)
    createdLinkMeasures shouldBe (15.0, 16.0)
    linksToMove.map(_._1) shouldBe Seq(0)
  }

  test("splitting speed limit where all links run towards link indexing") {
    val link1 = (0l, 20.0, (Point(0.0, 0.0), Point(20.0, 0.0)))
    val link2 = (1l, 16.0, (Point(20.0, 0.0), Point(36.0, 0.0)))
    val link3 = (2l, 10.0, (Point(36.0, 0.0), Point(46.0, 0.0)))
    val (existingLinkMeasures, createdLinkMeasures, linksToMove) = OracleLinearAssetDao.createSpeedLimitSplit(15.0, (1, 0.0, 16.0), Seq(link1, link2, link3))

    existingLinkMeasures shouldBe(0.0, 15.0)
    createdLinkMeasures shouldBe(15.0, 16.0)
    linksToMove.map(_._1) shouldBe Seq(2)
  }

  test("splitting speed limit where speed limit terminates to links pointing outwards") {
    val link1 = (0l, 20.0, (Point(20.0, 0.0), Point(0.0, 0.0)))
    val link2 = (1l, 16.0, (Point(20.0, 0.0), Point(36.0, 0.0)))
    val link3 = (2l, 10.0, (Point(36.0, 0.0), Point(46.0, 0.0)))
    val (existingLinkMeasures, createdLinkMeasures, linksToMove) = OracleLinearAssetDao.createSpeedLimitSplit(15.0, (1, 0.0, 16.0), Seq(link1, link2, link3))

    existingLinkMeasures shouldBe(0.0, 15.0)
    createdLinkMeasures shouldBe(15.0, 16.0)
    linksToMove.map(_._1) shouldBe Seq(2)
  }

  test("splitting speed limit where speed limit terminates to links pointing outwards and split link runs against indexing") {
    val link1 = (0l, 20.0, (Point(20.0, 0.0), Point(0.0, 0.0)))
    val link2 = (1l, 16.0, (Point(36.0, 0.0), Point(20.0, 0.0)))
    val link3 = (2l, 10.0, (Point(36.0, 0.0), Point(46.0, 0.0)))
    val (existingLinkMeasures, createdLinkMeasures, linksToMove) = OracleLinearAssetDao.createSpeedLimitSplit(15.0, (1, 0.0, 16.0), Seq(link1, link2, link3))

    existingLinkMeasures shouldBe(0.0, 15.0)
    createdLinkMeasures shouldBe(15.0, 16.0)
    linksToMove.map(_._1) shouldBe Seq(0)
  }

  test("splitting speed limit where links are unordered and links run against link indexing") {
    val link1 = (0l, 20.0, (Point(20.0, 0.0), Point(0.0, 0.0)))
    val link2 = (1l, 16.0, (Point(36.0, 0.0), Point(20.0, 0.0)))
    val link3 = (2l, 10.0, (Point(46.0, 0.0), Point(36.0, 0.0)))
    val (existingLinkMeasures, createdLinkMeasures, linksToMove) = OracleLinearAssetDao.createSpeedLimitSplit(15.0, (1, 0.0, 16.0), Seq(link1, link3, link2))

    existingLinkMeasures shouldBe(0.0, 15.0)
    createdLinkMeasures shouldBe(15.0, 16.0)
    linksToMove.map(_._1) shouldBe Seq(0)
  }

  test("splitting speed limit where links are unordered and links run towards link indexing") {
    val link1 = (0l, 20.0, (Point(0.0, 0.0), Point(20.0, 0.0)))
    val link2 = (1l, 16.0, (Point(20.0, 0.0), Point(36.0, 0.0)))
    val link3 = (2l, 10.0, (Point(36.0, 0.0), Point(46.0, 0.0)))
    val (existingLinkMeasures, createdLinkMeasures, linksToMove) = OracleLinearAssetDao.createSpeedLimitSplit(15.0, (1, 0.0, 16.0), Seq(link1, link3, link2))

    existingLinkMeasures shouldBe(0.0, 15.0)
    createdLinkMeasures shouldBe(15.0, 16.0)
    linksToMove.map(_._1) shouldBe Seq(2)
  }

  test("splitting speed limit where links are unordered and speed limit terminates to links pointing outwards") {
    val link1 = (0l, 20.0, (Point(20.0, 0.0), Point(0.0, 0.0)))
    val link2 = (1l, 16.0, (Point(20.0, 0.0), Point(36.0, 0.0)))
    val link3 = (2l, 10.0, (Point(36.0, 0.0), Point(46.0, 0.0)))
    val (existingLinkMeasures, createdLinkMeasures, linksToMove) = OracleLinearAssetDao.createSpeedLimitSplit(15.0, (1, 0.0, 16.0), Seq(link1, link3, link2))

    existingLinkMeasures shouldBe(0.0, 15.0)
    createdLinkMeasures shouldBe(15.0, 16.0)
    linksToMove.map(_._1) shouldBe Seq(2)
  }

  test("splitting speed limit where links are unordered and speed limit terminates to links pointing outwards and split link runs against indexing") {
    val link1 = (0l, 20.0, (Point(20.0, 0.0), Point(0.0, 0.0)))
    val link2 = (1l, 16.0, (Point(36.0, 0.0), Point(20.0, 0.0)))
    val link3 = (2l, 10.0, (Point(36.0, 0.0), Point(46.0, 0.0)))
    val (existingLinkMeasures, createdLinkMeasures, linksToMove) = OracleLinearAssetDao.createSpeedLimitSplit(15.0, (1, 0.0, 16.0), Seq(link1, link3, link2))

    existingLinkMeasures shouldBe(0.0, 15.0)
    createdLinkMeasures shouldBe(15.0, 16.0)
    linksToMove.map(_._1) shouldBe Seq(0)
  }
}
