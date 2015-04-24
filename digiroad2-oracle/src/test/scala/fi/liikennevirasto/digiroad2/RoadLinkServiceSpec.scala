package fi.liikennevirasto.digiroad2

import org.scalatest.{BeforeAndAfter, FunSuite, Matchers}

import fi.liikennevirasto.digiroad2.asset.{AgainstDigitizing, TowardsDigitizing, BoundingRectangle}
import com.jolbox.bonecp.{BoneCPConfig, BoneCPDataSource}
import fi.liikennevirasto.digiroad2.asset.{BoundingRectangle, AdministrativeClass, TrafficDirection}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import scala.slick.driver.JdbcDriver.backend.Database
import scala.slick.driver.JdbcDriver.backend.Database.dynamicSession
import scala.slick.jdbc.StaticQuery.interpolation
import scala.slick.jdbc.{GetResult, PositionedResult, StaticQuery => Q}

class RoadLinkServiceSpec extends FunSuite with Matchers with BeforeAndAfter {

  after {
    Database.forDataSource(OracleDatabase.ds).withDynTransaction {
      sqlu"""delete from link_type""".execute()
    }
  }

  test("Get production road link with test id that maps to one production road link") {
    RoadLinkService.getByTestIdAndMeasure(48l, 50.0).map(_._1) should be (Some(57))
    RoadLinkService.getByTestIdAndMeasure(48l, 50.0).map(_._2) should be (Some(18))
  }

  test("Get production road link with test id that doesn't map to production") {
    RoadLinkService.getByTestIdAndMeasure(1414l, 50.0) should be (None)
  }

  test("Get production road link with test id that maps to several links in production") {
    RoadLinkService.getByTestIdAndMeasure(147298l, 50.0) should be (None)
  }

  test("Override road link traffic direction with adjusted value") {
    val boundingBox = BoundingRectangle(Point(373816, 6676812), Point(374634, 6677671))
    val roadLinks = RoadLinkService.getRoadLinks(boundingBox)
    roadLinks.find { case(id, _, _, _, _, _, _, _, _, _) => id == 7886262 }.map(_._7) should be (Some(TowardsDigitizing))
    roadLinks.find { case(_, mmlId, _, _, _, _, _, _, _, _) => mmlId == 391203482 }.map(_._7) should be (Some(AgainstDigitizing))
  }

  test("Override road link functional class with adjusted value") {
    val boundingBox = BoundingRectangle(Point(373816, 6676812), Point(374634, 6677671))
    val roadLinks = RoadLinkService.getRoadLinks(boundingBox)
    roadLinks.find { case (id, _, _, _, _, _, _, _, _, _) => id == 7886262}.map(_._6) should be(Some(5))
    roadLinks.find { case (_, mmlId, _, _, _, _, _, _, _, _) => mmlId == 391203482}.map(_._6) should be(Some(4))
  }

  test("Overriden road link adjustments return latest modification") {
    val roadLink = RoadLinkService.getRoadLink(7886262)
    val (_, _, _, _, _, _, _, modifiedAt, modifiedBy, _) = roadLink
    modifiedAt should be (Some("12.12.2014 00:00:00"))
    modifiedBy should be (Some("test"))
  }

  test("Override road link type with adjusted value") {
    addLinkTypeAdjustment(99, 896628487)

    val roadLink = RoadLinkService.getRoadLink(5925952)
    val (_, _, _, _, _, _, _, _, _, linkType) = roadLink
    linkType should be (99)
  }

  test("Adjust link type") {
    RoadLinkService.adjustLinkType(5925952, 111, "testuser")

    val roadLink = RoadLinkService.getRoadLink(5925952)
    val (_, _, _, _, _, _, _, _, _, linkType) = roadLink
    linkType should be (111)
  }

  test("Link type adjustment should return latest modification") {
    addLinkTypeAdjustment(99, 896628487)

    val roadLink = RoadLinkService.getRoadLink(5925952)
    val (_, _, _, _, _, _, _, modifiedAt, modifiedBy, _) = roadLink
    modifiedBy should be (Some("testuser"))
    modifiedAt should be (Some("12.12.2014 00:00:00"))
  }

  def addLinkTypeAdjustment(linkTypeAdjustment: Int, mmlId: Int): Unit = {
    Database.forDataSource(OracleDatabase.ds).withDynTransaction {
      sqlu"""insert into link_type (mml_id, link_type, modified_by, modified_date) values ($mmlId, $linkTypeAdjustment, 'testuser', to_timestamp('2014-12-12', 'YYYY-MM-DD'))""".execute()
    }
  }
}
