package fi.liikennevirasto.digiroad2.dao.linearasset

import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.FeatureClass.AllOthers
import fi.liikennevirasto.digiroad2.linearasset.ValidityPeriodDayOfWeek.Weekday
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.util.TestTransactions
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers, Tag}
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.asset.SideCode.BothDirections
import fi.liikennevirasto.digiroad2.client.vvh.{VVHClient, VVHRoadLinkClient, VVHRoadlink}
import fi.liikennevirasto.digiroad2.dao.Sequences
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.Measures
import slick.jdbc.StaticQuery.interpolation

class PostGISSpeedLimitDaoSpec extends FunSuite with Matchers {
  val roadLink = VVHRoadlink(388562360, 0, List(Point(0.0, 0.0), Point(0.0, 200.0)), Municipality, TrafficDirection.BothDirections, AllOthers)
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]

  private def daoWithRoadLinks(roadLinks: Seq[VVHRoadlink]): PostGISSpeedLimitDao = {
    val mockVVHClient = MockitoSugar.mock[VVHClient]
    val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]

    when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
    when(mockVVHRoadLinkClient.fetchByLinkIds(roadLinks.map(_.linkId).toSet))
      .thenReturn(roadLinks)

    when(mockRoadLinkService.fetchVVHRoadlinksAndComplementary(roadLinks.map(_.linkId).toSet))
      .thenReturn(roadLinks)

    roadLinks.foreach { roadLink =>
      when(mockVVHRoadLinkClient.fetchByLinkId(roadLink.linkId)).thenReturn(Some(roadLink))
    }

    new PostGISSpeedLimitDao(mockVVHClient, mockRoadLinkService)
  }

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)



  def passingMunicipalityValidation(code: Int, administrativeClass: AdministrativeClass): Unit = {}

  def failingMunicipalityValidation(code: Int, administrativeClass: AdministrativeClass): Unit = {
    throw new IllegalArgumentException
  }

  private def simulateQuery[T](f: => T): T = {
    val result = f
    sqlu"""delete from temp_id""".execute
    result
  }

  test("filter out disallowed link types") {
    runWithRollback {
      val roadLinks = Seq(
        RoadLink(1611552, List(Point(0.0, 0.0), Point(40.0, 0.0)), 40.0, Municipality, 1, TrafficDirection.UnknownDirection, MultipleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235))),
        RoadLink(1611558, List(Point(0.0, 0.0), Point(370.0, 0.0)), 370.0, Municipality, 1, TrafficDirection.UnknownDirection, PedestrianZone, None, None, Map("MUNICIPALITYCODE" -> BigInt(235))),
        RoadLink(1611558, List(Point(0.0, 0.0), Point(370.0, 0.0)), 370.0, Municipality, 1, TrafficDirection.UnknownDirection, CycleOrPedestrianPath, None, None, Map("MUNICIPALITYCODE" -> BigInt(235))),
        RoadLink(1611558, List(Point(0.0, 0.0), Point(370.0, 0.0)), 370.0, Municipality, 1, TrafficDirection.UnknownDirection, CableFerry, None, None, Map("MUNICIPALITYCODE" -> BigInt(235))),
        RoadLink(1611558, List(Point(0.0, 0.0), Point(370.0, 0.0)), 370.0, Municipality, 1, TrafficDirection.UnknownDirection, UnknownLinkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      )
      val dao = new PostGISSpeedLimitDao(MockitoSugar.mock[VVHClient], MockitoSugar.mock[RoadLinkService])

      val speedLimits = dao.getSpeedLimitLinksByRoadLinks(roadLinks.filter(_.isCarTrafficRoad))

      speedLimits._1.map(_.id) should equal(Seq(300103))
    }
  }

  test("filter out disallowed functional classes") {
    runWithRollback {
      val roadLinks = Seq(
        RoadLink(1611552, List(Point(0.0, 0.0), Point(40.0, 0.0)), 40.0, Municipality, 1, TrafficDirection.UnknownDirection, MultipleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235))),
        RoadLink(1611558, List(Point(0.0, 0.0), Point(370.0, 0.0)), 370.0, Municipality, 7, TrafficDirection.UnknownDirection, MultipleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235))),
        RoadLink(1611558, List(Point(0.0, 0.0), Point(370.0, 0.0)), 370.0, Municipality, 8, TrafficDirection.UnknownDirection, MultipleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
      )
      val dao = new PostGISSpeedLimitDao(MockitoSugar.mock[VVHClient], MockitoSugar.mock[RoadLinkService])

      val speedLimits = dao.getSpeedLimitLinksByRoadLinks(roadLinks.filter(_.isCarTrafficRoad))

      speedLimits._1.map(_.id) should equal(Seq(300103))
    }
  }

  test("speed limit creation fails if speed limit is already defined on link segment") {
    runWithRollback {
      val roadLink = VVHRoadlink(123, 0, List(Point(0.0, 0.0), Point(0.0, 200.0)), Municipality, TrafficDirection.UnknownDirection, AllOthers)
      when(mockRoadLinkService.fetchVVHRoadlinkAndComplementary(123)).thenReturn(Some(roadLink))
      val dao = daoWithRoadLinks(List(roadLink))
      val id = simulateQuery {
        dao.createSpeedLimit("test", 123, Measures(0.0, 100.0), SideCode.BothDirections, SpeedLimitValue(40), 0, (_, _) => ())
      }
      id shouldBe defined
      val id2 = simulateQuery {
        dao.createSpeedLimit("test", 123, Measures(0.0, 100.0), SideCode.BothDirections, SpeedLimitValue(40), 0, (_, _) => ())
      }
      id2 shouldBe None
    }
  }

  test("speed limit creation succeeds when speed limit is already defined on segment iff speed limits have opposing sidecodes") {
    runWithRollback {
      val roadLink = VVHRoadlink(123, 0, List(Point(0.0, 0.0), Point(0.0, 200.0)), Municipality, TrafficDirection.UnknownDirection, AllOthers)
      when(mockRoadLinkService.fetchVVHRoadlinkAndComplementary(123)).thenReturn(Some(roadLink))
      val dao = daoWithRoadLinks(List(roadLink))
      val id = simulateQuery {
        dao.createSpeedLimit("test", 123, Measures(0.0, 100.0), SideCode.TowardsDigitizing, SpeedLimitValue(40), 0, (_, _) => ())
      }
      id shouldBe defined
      val id2 = simulateQuery {
        dao.createSpeedLimit("test", 123, Measures(0.0, 100.0), SideCode.AgainstDigitizing, SpeedLimitValue(40), 0, (_, _) => ())
      }
      id2 shouldBe defined
      val id3 = simulateQuery {
        dao.createSpeedLimit("test", 123, Measures(0.0, 100.0), SideCode.BothDirections, SpeedLimitValue(40), 0, (_, _) => ())
      }
      id3 shouldBe None
    }
  }

  test("speed limit purge removes fully covered link from unknown speed limit list") {
    runWithRollback {
      val linkId = 1611389
      sqlu"""delete from unknown_speed_limit""".execute
      sqlu"""insert into unknown_speed_limit (link_id, municipality_code, administrative_class) values ($linkId, 235, 1)""".execute
      val dao = daoWithRoadLinks(Nil)
      dao.purgeFromUnknownSpeedLimits(linkId, 59.934)
      sql"""select link_id from unknown_speed_limit where link_id = $linkId""".as[Long].firstOption should be(None)
    }
  }

  test("speed limit purge does not remove partially covered link from unknown speed limit list") {
    runWithRollback {
      val linkId = 1611397
      sqlu"""delete from unknown_speed_limit""".execute
      sqlu"""insert into unknown_speed_limit (link_id, municipality_code, administrative_class) values ($linkId, 235, 1)""".execute
      val roadLink = VVHRoadlink(linkId, 0, Nil, Municipality, TrafficDirection.UnknownDirection, AllOthers)
      val dao = daoWithRoadLinks(List(roadLink))

      when(mockRoadLinkService.fetchVVHRoadlinkAndComplementary(linkId)).thenReturn(Some(roadLink))
      dao.createSpeedLimit("test", linkId, Measures(11.0, 16.0), SideCode.BothDirections, SpeedLimitValue(40), 0, (_, _) => ())
      dao.purgeFromUnknownSpeedLimits(linkId, 84.121)
      sql"""select link_id from unknown_speed_limit where link_id = $linkId""".as[Long].firstOption should be(Some(linkId))

      dao.createSpeedLimit("test", linkId, Measures(20.0, 54.0), SideCode.BothDirections, SpeedLimitValue(40), 0, (_, _) => ())
      dao.purgeFromUnknownSpeedLimits(linkId, 84.121)
      sql"""select link_id from unknown_speed_limit where link_id = $linkId""".as[Long].firstOption should be(None)
    }
  }

  test("unknown speed limits can be filtered by municipality") {
    runWithRollback {
      val linkId = 1
      val linkId2 = 2
      sqlu"""delete from unknown_speed_limit""".execute
      sqlu"""insert into unknown_speed_limit (link_id, municipality_code, administrative_class) values ($linkId, 235, 1)""".execute
      sqlu"""insert into unknown_speed_limit (link_id, municipality_code, administrative_class) values ($linkId2, 49, 1)""".execute

      val roadLink = VVHRoadlink(linkId, 0, Nil, Municipality, TrafficDirection.UnknownDirection, AllOthers)
      val roadLink2 = VVHRoadlink(linkId2, 0, Nil, Municipality, TrafficDirection.UnknownDirection, AllOthers)
      val dao = daoWithRoadLinks(List(roadLink, roadLink2))

      val allSpeedLimits = dao.getUnknownSpeedLimits(Set(), None)
      allSpeedLimits("Kauniainen")("State").asInstanceOf[Seq[Long]].length should be(1)
      allSpeedLimits("Espoo")("State").asInstanceOf[Seq[Long]].length should be(1)

      val kauniainenSpeedLimits = dao.getUnknownSpeedLimits(Set(235), None)
      kauniainenSpeedLimits("Kauniainen")("State").asInstanceOf[Seq[Long]].length should be(1)
      kauniainenSpeedLimits.keySet.contains("Espoo") should be(false)
    }
  }

  test("speed limit mass query") {
    val ids = Seq.range(1L, 500L).toSet
    val dao = new PostGISSpeedLimitDao(null, null)
    runWithRollback {
      dao.getCurrentSpeedLimitsByLinkIds(Option(ids))
    }
  }

  test("speed limit no mass query") {
    val ids = Seq.range(1L, 2L).toSet
    val dao = new PostGISSpeedLimitDao(null, null)
    runWithRollback {
      dao.getCurrentSpeedLimitsByLinkIds(Option(ids))
    }
  }

  test("speed limit empty set must not crash") {
    val ids = Set():Set[Long]
    val dao = new PostGISSpeedLimitDao(null, null)
    runWithRollback {
      dao.getCurrentSpeedLimitsByLinkIds(Option(ids))
    }
  }

  test("speed limit no set must not crash") {
    val dao = new PostGISSpeedLimitDao(null, null)
    runWithRollback {
      dao.getCurrentSpeedLimitsByLinkIds(None)
    }
  }

}
