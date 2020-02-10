package fi.liikennevirasto.digiroad2.service.lane

import fi.liikennevirasto.digiroad2.{DigiroadEventBus, Point}
import fi.liikennevirasto.digiroad2.asset.{ ConstructionType, EnclosedTrafficArea, LinkGeomSource, Motorway, Municipality, State, TrafficDirection}
import fi.liikennevirasto.digiroad2.client.vvh.{VVHClient, VVHRoadLinkClient}
import fi.liikennevirasto.digiroad2.dao.MunicipalityDao
import fi.liikennevirasto.digiroad2.dao.lane.LaneDao
import fi.liikennevirasto.digiroad2.lane.{LanePropertiesValues, LaneProperty, LanePropertyValue, NewIncomeLane}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.{PolygonTools, TestTransactions}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}


class LaneTestSupporter extends FunSuite with Matchers {
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockVVHClient = MockitoSugar.mock[VVHClient]
  val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
  val mockPolygonTools = MockitoSugar.mock[PolygonTools]
  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  val mockMunicipalityDao = MockitoSugar.mock[MunicipalityDao]
  val mockLaneDao = MockitoSugar.mock[LaneDao]

  val laneDao = new LaneDao(mockVVHClient, mockRoadLinkService)
  val roadLinkWithLinkSource = RoadLink(
    1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
    1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "SURFACETYPE" -> BigInt(2)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)


  when(mockRoadLinkService.getRoadLinkByLinkIdFromVVH(any[Long], any[Boolean])).thenReturn(Some(roadLinkWithLinkSource))

  when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)


  val lanePropertiesValues11 = LanePropertiesValues(
    Seq(
      LaneProperty("lane_code", Seq(LanePropertyValue(11))),
      LaneProperty("lane_continuity", Seq(LanePropertyValue("1"))),
      LaneProperty("lane_type", Seq(LanePropertyValue("2"))),
      LaneProperty("lane_information", Seq(LanePropertyValue("13")))
    )
  )

  val lanePropertiesValues12 = LanePropertiesValues(
    Seq(
      LaneProperty("lane_code", Seq(LanePropertyValue(12))),
      LaneProperty("lane_continuity", Seq(LanePropertyValue("1"))),
      LaneProperty("lane_type", Seq(LanePropertyValue("2"))),
      LaneProperty("lane_information", Seq(LanePropertyValue("13")))
    )
  )


  val lanePropertiesValues21 = LanePropertiesValues(
    Seq(
      LaneProperty("lane_code", Seq(LanePropertyValue(21))),
      LaneProperty("lane_continuity", Seq(LanePropertyValue("1"))),
      LaneProperty("lane_type", Seq(LanePropertyValue("2"))),
      LaneProperty("lane_information", Seq(LanePropertyValue("10")))
    )
  )

  val lanePropertiesValues22 = LanePropertiesValues(
    Seq(
      LaneProperty("lane_code", Seq(LanePropertyValue(22))),
      LaneProperty("lane_continuity", Seq(LanePropertyValue("1"))),
      LaneProperty("lane_type", Seq(LanePropertyValue("2"))),
      LaneProperty("lane_information", Seq(LanePropertyValue("10")))
    )
  )


  object PassThroughLaneService extends LaneOperations {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: LaneDao = mockLaneDao
    override def eventBus: DigiroadEventBus = mockEventBus
    override def vvhClient: VVHClient = mockVVHClient
    override def polygonTools: PolygonTools = mockPolygonTools
    override def municipalityDao: MunicipalityDao = mockMunicipalityDao

  }

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback(PassThroughLaneService.dataSource)(test)
}


class LaneServiceSpec extends LaneTestSupporter {

  object ServiceWithDao extends LaneService(mockRoadLinkService, mockEventBus) {
    
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def vvhClient: VVHClient = mockVVHClient
    override def dao: LaneDao = laneDao
    override def municipalityDao: MunicipalityDao = mockMunicipalityDao
    override def eventBus: DigiroadEventBus = mockEventBus
    override def polygonTools: PolygonTools = mockPolygonTools

  }

  val usernameTest = "testuser"

  test("Create new lane") {
    runWithRollback {

      val newLane = ServiceWithDao.create(Seq(NewIncomeLane(0, 0, 500, 745, false, false, lanePropertiesValues11)), Set(100L), 1, usernameTest)
      newLane.length should be(1)

      val lane = laneDao.fetchLanesByIds( Set(newLane.head)).head
      lane.expired should be (false)

      lane.attributes.properties.foreach{ laneProp =>
        val attr = lanePropertiesValues11.properties.find(_.publicId == laneProp.publicId)

        attr should not be (None)
        attr.head.values.head.value should be  (laneProp.values.head.value)
      }

    }

  }

  test("Should not be able to delete main lanes") {
    runWithRollback {
      val newLane = ServiceWithDao.create(Seq(NewIncomeLane(0, 0, 500, 745, false, false, lanePropertiesValues11)), Set(100L), 1, usernameTest)
      newLane.length should be(1)

      val thrown = intercept[IllegalArgumentException] {
        ServiceWithDao.deleteEntryLane(newLane.head)
      }

      thrown.getMessage should be("Cannot Delete a main lane!")

    }
  }

  test("Fetch existing main lanes by linkId"){
    runWithRollback {
      val newLane11 = ServiceWithDao.create(Seq(NewIncomeLane(0, 0, 500, 745, false, false, lanePropertiesValues11)), Set(100L), 1, usernameTest)
      newLane11.length should be(1)

      val newLane12 = ServiceWithDao.create(Seq(NewIncomeLane(0, 0, 500, 745, false, false, lanePropertiesValues12)), Set(100L), 1, usernameTest)
      newLane12.length should be(1)

      val newLane21 = ServiceWithDao.create(Seq(NewIncomeLane(0, 0, 500, 745, false, false, lanePropertiesValues21)), Set(100L), 2, usernameTest)
      newLane21.length should be(1)

      val mockRoadLink = RoadLink(100L, Seq(), 1000, State, 2, TrafficDirection.AgainstDigitizing,
        EnclosedTrafficArea, None, None)

      val existingLanes = ServiceWithDao.fetchExistingMainLanesByRoadLinks(Seq(mockRoadLink), Seq())
      existingLanes.length should be(2)

    }
  }

  test("Fetch existing Lanes by linksId"){
    runWithRollback {

      val newLane11 = ServiceWithDao.create(Seq(NewIncomeLane(0, 0, 500, 745, false, false, lanePropertiesValues11)), Set(100L), 1, usernameTest)
      newLane11.length should be(1)

      val newLane21 = ServiceWithDao.create(Seq(NewIncomeLane(0, 0, 500, 745, false, false, lanePropertiesValues21)), Set(100L), 2, usernameTest)
      newLane21.length should be(1)

      val newLane22 = ServiceWithDao.create(Seq(NewIncomeLane(0, 0, 500, 745, false, false, lanePropertiesValues22)), Set(100L), 2, usernameTest)
      newLane22.length should be(1)

      val existingLanes = ServiceWithDao.fetchExistingLanesByLinksId(100L, 1)
      existingLanes.length should be(1)

      val existingLanesOtherSide = ServiceWithDao.fetchExistingLanesByLinksId(100L, 2)
      existingLanesOtherSide.length should be(2)

    }
  }


  test("Update Lane Information") {
    runWithRollback {

      val updateValues11 = LanePropertiesValues(
        Seq(
          LaneProperty("lane_code", Seq(LanePropertyValue(11))),
          LaneProperty("lane_continuity", Seq(LanePropertyValue("2"))),
          LaneProperty("lane_type", Seq(LanePropertyValue("5"))),
          LaneProperty("lane_information", Seq(LanePropertyValue("12")))
        )
      )

      val newLane11 = ServiceWithDao.create(Seq(NewIncomeLane(0, 0, 500, 745, false, false, lanePropertiesValues11)), Set(100L), 1, usernameTest)
      newLane11.length should be(1)

      val updatedLane = ServiceWithDao.update(Seq(NewIncomeLane(newLane11.head, 0, 500, 745, false, false, updateValues11)), Set(100L), 1, usernameTest)
      updatedLane.length should be(1)

      }
  }

  test("Should not be able to Update main Lane") {
    runWithRollback {

      val updateValues11 = LanePropertiesValues(
        Seq(
          LaneProperty("lane_code", Seq(LanePropertyValue(12))),
          LaneProperty("lane_continuity", Seq(LanePropertyValue("2"))),
          LaneProperty("lane_type", Seq(LanePropertyValue("5"))),
          LaneProperty("lane_information", Seq(LanePropertyValue("12")))
        )
      )

      val newLane11 = ServiceWithDao.create(Seq(NewIncomeLane(0, 0, 500, 745, false, false, lanePropertiesValues11)), Set(100L), 1, usernameTest)
      newLane11.length should be(1)

      val thrown = intercept[IllegalArgumentException] {
        ServiceWithDao.update(Seq(NewIncomeLane(newLane11.head, 0, 500, 745, false, false, updateValues11)), Set(100L), 1, usernameTest)
      }

      thrown.getMessage should be("Cannot change the code of main lane!")

    }
  }

}
