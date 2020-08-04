package fi.liikennevirasto.digiroad2.service.lane

import fi.liikennevirasto.digiroad2.asset.DateParser.DatePropertyFormat
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.{VVHClient, VVHRoadLinkClient}
import fi.liikennevirasto.digiroad2.dao.{MunicipalityDao, RoadAddressTEMP}
import fi.liikennevirasto.digiroad2.dao.lane.LaneDao
import fi.liikennevirasto.digiroad2.lane.LaneFiller.{ChangeSet, SideCodeAdjustment}
import fi.liikennevirasto.digiroad2.lane.{LaneNumber, LaneProperty, LanePropertyValue, NewIncomeLane, PersistedLane}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.{PolygonTools, TestTransactions, Track}
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, Point}
import org.joda.time.DateTime
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


  val lanePropertiesValues11 = Seq( LaneProperty("lane_code", Seq(LanePropertyValue(11))),
                                LaneProperty("lane_type", Seq(LanePropertyValue("2")))
                              )


  val lanePropertiesValues12 = Seq( LaneProperty("lane_code", Seq(LanePropertyValue(12))),
                                LaneProperty("lane_type", Seq(LanePropertyValue("2")))
                              )

  val lanePropertiesValues14 = Seq(LaneProperty("lane_code", Seq(LanePropertyValue(14))),
                                   LaneProperty("lane_type", Seq(LanePropertyValue("3")))
                                  )


  val lanePropertiesValues21 = Seq( LaneProperty("lane_code", Seq(LanePropertyValue(21))),
                                LaneProperty("lane_type", Seq(LanePropertyValue("2")))
                              )


  val lanePropertiesValues22 = Seq( LaneProperty("lane_code", Seq(LanePropertyValue(22))),
                                LaneProperty("lane_type", Seq(LanePropertyValue("2")))
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

      lane.attributes.foreach{ laneProp =>
        val attr = lanePropertiesValues11.find(_.publicId == laneProp.publicId)

        attr should not be (None)
        attr.head.values.head.value should be  (laneProp.values.head.value)
      }

    }

  }

  test("Should not be able to delete main lanes") {
    runWithRollback {
      val newLane = NewIncomeLane(0, 0, 500, 745, false, false, lanePropertiesValues11)
      val newLaneId = ServiceWithDao.create(Seq(newLane), Set(100L), 1, usernameTest)
      newLaneId.length should be(1)

      val thrown = intercept[IllegalArgumentException] {
        ServiceWithDao.deleteMultipleLanes(Set(100L), Set(ServiceWithDao.getLaneCode(newLane).toInt))
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

      val existingLanes = ServiceWithDao.fetchExistingLanesByLinksIdAndSideCode(100L, 1)
      existingLanes.length should be(1)

      val existingLanesOtherSide = ServiceWithDao.fetchExistingLanesByLinksIdAndSideCode(100L, 2)
      existingLanesOtherSide.length should be(2)

    }
  }


  test("Update Lane Information") {
    runWithRollback {

      val updateValues11 = Seq( LaneProperty("lane_code", Seq(LanePropertyValue(11))),
                            LaneProperty("lane_type", Seq(LanePropertyValue("5")))
                          )


      val newLane11 = ServiceWithDao.create(Seq(NewIncomeLane(0, 0, 500, 745, false, false, lanePropertiesValues11)), Set(100L), 1, usernameTest)
      newLane11.length should be(1)

      val updatedLane = ServiceWithDao.update(Seq(NewIncomeLane(newLane11.head, 0, 500, 745, false, false, updateValues11)), Set(100L), 1, usernameTest)
      updatedLane.length should be(1)

      }
  }

  test("Remove unused attributes from database") {
    runWithRollback {
      val lanePropertiesWithDate = lanePropertiesValues12 ++ Seq( LaneProperty("start_date", Seq(LanePropertyValue("20.07.2020"))) )
      val lanePropertiesWithEmptyDate = lanePropertiesValues12 ++ Seq( LaneProperty("start_date", Seq()) )

      val newLaneId = ServiceWithDao.create(Seq(NewIncomeLane(0, 0, 500, 745, false, false, lanePropertiesWithDate)), Set(100L), 1, usernameTest)
      val createdLane = ServiceWithDao.getPersistedLanesByIds(newLaneId.toSet)
      createdLane.head.attributes.length should be(3)

      val updatedLaneId = ServiceWithDao.update(Seq(NewIncomeLane(newLaneId.head, 0, 500, 745, false, false, lanePropertiesWithEmptyDate)), Set(100L), 1, usernameTest)
      val updatedLane = ServiceWithDao.getPersistedLanesByIds(updatedLaneId.toSet)
      updatedLane.head.attributes.length should be(2)
    }
  }

  test("Should not be able to Update main Lane") {
    runWithRollback {

      val updateValues11 = Seq( LaneProperty("lane_code", Seq(LanePropertyValue(12))),
                            LaneProperty("lane_type", Seq(LanePropertyValue("5")))
                          )


      val newLane11 = ServiceWithDao.create(Seq(NewIncomeLane(0, 0, 500, 745, false, false, lanePropertiesValues11)), Set(100L), 1, usernameTest)
      newLane11.length should be(1)

      val thrown = intercept[IllegalArgumentException] {
        ServiceWithDao.update(Seq(NewIncomeLane(newLane11.head, 0, 500, 745, false, false, updateValues11)), Set(100L), 1, usernameTest)
      }

      thrown.getMessage should be("Cannot change the code of main lane!")

    }
  }

  test("Expire a sub lane") {
    runWithRollback {
      val newSubLane = NewIncomeLane(0, 0, 500, 745, false, false, lanePropertiesValues12)
      val newSubLaneId = ServiceWithDao.create(Seq(newSubLane), Set(100L), 1, usernameTest).head
      ServiceWithDao.multipleLanesToHistory(Set(100L), Set(ServiceWithDao.getLaneCode(newSubLane).toInt), usernameTest)

      val lanes = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(100L), Seq(12), true)

      val expiredLane = lanes.find(_.id == newSubLaneId).head
      lanes.find(_.id == newSubLaneId).head.expired should be (true)
      expiredLane.attributes.foreach{ laneProp =>
        lanePropertiesValues12.contains(laneProp) should be(true)
      }
      expiredLane.startMeasure should be (0.0)
      expiredLane.endMeasure should be (500.0)
    }
  }

  test("Delete a sub lane") {
    runWithRollback {
      val newSubLane = NewIncomeLane(0, 0, 500, 745, false, false, lanePropertiesValues12)
      val newSubLaneId = ServiceWithDao.create(Seq(newSubLane), Set(100L), 1, usernameTest).head
      ServiceWithDao.deleteMultipleLanes(Set(100L), Set(ServiceWithDao.getLaneCode(newSubLane).toInt))

      val lanes = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(100L), Seq(12), true)
      lanes.find(_.id == newSubLaneId) should be(None)
    }
  }

  test("Split lane asset. Expire lane and create two new lanes") {
    runWithRollback {
      val lanePropertiesSubLaneSplit2 = Seq(
        LaneProperty("lane_code", Seq(LanePropertyValue(12))),
        LaneProperty("lane_type", Seq(LanePropertyValue("5")))
      )

      val mainLane = NewIncomeLane(0, 0, 500, 745, false, false, lanePropertiesValues11)
      val mainLaneId = ServiceWithDao.create(Seq(mainLane), Set(100L), 1, usernameTest).head
      val newSubLane = NewIncomeLane(0, 0, 500, 745, false, false, lanePropertiesValues12)
      val newSubLaneId = ServiceWithDao.create(Seq(newSubLane), Set(100L), 1, usernameTest).head

      val subLaneSplit1 = NewIncomeLane(0, 0, 250, 745, false, false, lanePropertiesValues12)
      val subLaneSplit2 = NewIncomeLane(0, 250, 500, 745, false, false, lanePropertiesSubLaneSplit2)
      ServiceWithDao.update(Seq(mainLane.copy(id = mainLaneId)), Set(100L), 1, usernameTest)
      ServiceWithDao.multipleLanesToHistory(Set(100L), Set(ServiceWithDao.getLaneCode(newSubLane).toInt), usernameTest)

      val subLaneSplit1Id = ServiceWithDao.create(Seq(subLaneSplit1), Set(100L), 1, usernameTest).head
      val subLaneSplit2Id = ServiceWithDao.create(Seq(subLaneSplit2), Set(100L), 1, usernameTest).head

      val lanes = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(100L), Seq(11, 12), true)

      lanes.find(_.id == newSubLaneId).head.expired should be (true)

      val split1 = lanes.find(_.id == subLaneSplit1Id).head
      split1.attributes.foreach{ laneProp =>
       lanePropertiesValues12.contains(laneProp) should be(true)
      }
      split1.startMeasure should be (0.0)
      split1.endMeasure should be (250.0)

      val split2 = lanes.find(_.id == subLaneSplit2Id).head
      split2.attributes.foreach{ laneProp =>
        lanePropertiesSubLaneSplit2.contains(laneProp) should be(true)
      }
      split2.startMeasure should be (250.0)
      split2.endMeasure should be (500.0)
    }
  }

  test("Delete sub lane in middle of lanes") {
    runWithRollback {
      val lanePropertiesValues14To12 = Seq(LaneProperty("lane_code", Seq(LanePropertyValue(12))),
        LaneProperty("lane_type", Seq(LanePropertyValue("3")))
      )

      val mainLane = NewIncomeLane(0, 0, 500, 745, false, false, lanePropertiesValues11)
      val newSubLane12 = NewIncomeLane(0, 0, 500, 745, false, false, lanePropertiesValues12)
      val subLane14 = NewIncomeLane(0, 0, 500, 745, false, false, lanePropertiesValues14)

      val mainLaneId = ServiceWithDao.create(Seq(mainLane), Set(100L), 1, usernameTest).head
      val newSubLane12Id = ServiceWithDao.create(Seq(newSubLane12), Set(100L), 1, usernameTest).head
      val newSubLane14Id = ServiceWithDao.create(Seq(subLane14), Set(100L), 1, usernameTest).head

      ServiceWithDao.deleteMultipleLanes(Set(100L), Set(ServiceWithDao.getLaneCode(newSubLane12).toInt))
      ServiceWithDao.update(Seq(mainLane.copy(id = mainLaneId), subLane14.copy(id = newSubLane14Id, properties = lanePropertiesValues14To12)), Set(100L), 1, usernameTest)

      val lanes = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(100L), Seq(11, 12), true)
      lanes.length should be (2)
      lanes.find(_.id == newSubLane12Id) should be (None)

      val updatedSubLane12 = lanes.find(_.id == newSubLane14Id).head
      updatedSubLane12.attributes.foreach{ laneProp =>
        lanePropertiesValues14To12.contains(laneProp) should be(true)
      }
      updatedSubLane12.startMeasure should be (0.0)
      updatedSubLane12.endMeasure should be (500.0)
    }
  }


  test("RoadLink BothDirection and with only one Lane (TowardsDigitizing). The 2nd should be created.") {
    runWithRollback {

      val roadLinkTowards2 = RoadLink(2L, Seq(Point(0.0, 0.0), Point(15.0, 0.0)), 15.0, Municipality,
                              1, TrafficDirection.BothDirections, Motorway, None, None)

      val lane11 = PersistedLane(2L, roadLinkTowards2.linkId, SideCode.TowardsDigitizing.value,
                  11, 745L, 0, 15.0, None, None, None, None, expired = false, 0L, None,
                  Seq(LaneProperty("lane_code", Seq(LanePropertyValue(11)))) )


      val roadAddress = RoadAddressTEMP(roadLinkTowards2.linkId, 6, 202, Track.Combined, 1, 150, 0, 150,
                        Seq(Point(0.0, 0.0), Point(15.0, 0.0)), Some(SideCode.TowardsDigitizing), None)

      val (persistedLanes, changeSet) = ServiceWithDao.adjustLanesSideCodes(roadLinkTowards2, Seq(lane11), ChangeSet(), Seq(roadAddress) )

      persistedLanes should have size 2
      persistedLanes.map(_.sideCode) should be(Seq(SideCode.TowardsDigitizing.value, SideCode.AgainstDigitizing.value))
      persistedLanes.map(_.id) should be(Seq(2L, 0L))
      persistedLanes.map(_.linkId) should be(Seq(roadLinkTowards2.linkId, roadLinkTowards2.linkId))

      changeSet.expiredLaneIds should be(Set())

    }
  }

  test("RoadLink BothDirection and with only one Lane (AgainstDigitizing). The 2nd should be created.") {

    val roadLinkTowards2 = RoadLink(2L, Seq(Point(0.0, 0.0), Point(15.0, 0.0)), 15.0, Municipality,
                            1, TrafficDirection.BothDirections, Motorway, None, None)

    val lane11 = PersistedLane(2L, roadLinkTowards2.linkId, SideCode.AgainstDigitizing.value,
                  21, 745L, 0, 15.0, None, None, None, None, expired = false, 0L, None,
                  Seq(LaneProperty("lane_code", Seq(LanePropertyValue(21)))) )

    val roadAddress = RoadAddressTEMP(roadLinkTowards2.linkId, 6, 202, Track.Combined, 1, 150, 0, 150,
                        Seq(Point(0.0, 0.0), Point(15.0, 0.0)), Some(SideCode.TowardsDigitizing), None)

    val (persistedLanes, changeSet) = ServiceWithDao.adjustLanesSideCodes(roadLinkTowards2, Seq(lane11), ChangeSet(), Seq(roadAddress) )

    persistedLanes should have size 2
    persistedLanes.map(_.sideCode) should be (Seq(SideCode.AgainstDigitizing.value, SideCode.TowardsDigitizing.value))
    persistedLanes.map(_.id) should be (Seq(2L, 0L))
    persistedLanes.map(_.linkId) should be (Seq(roadLinkTowards2.linkId, roadLinkTowards2.linkId))

    changeSet.expiredLaneIds should be(Set())

  }

  test("RoadLink TowardsDigitizing and with two main Lanes(TowardsDigitizing and AgainstDigitizing). " +
    "The main lane TowardsDigitizing should be standing.") {
    val roadLinkTowards1 = RoadLink(1L, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
                            1, TrafficDirection.TowardsDigitizing, Motorway, None, None)

    val lane11 = PersistedLane(1L, roadLinkTowards1.linkId, SideCode.TowardsDigitizing.value,
                  11, 745L, 0, 10.0, None, None, None, None, expired = false, 0L, None,
                  Seq(LaneProperty("lane_code", Seq(LanePropertyValue(11)))) )

    val lane21 = PersistedLane(2L, roadLinkTowards1.linkId, SideCode.AgainstDigitizing.value,
                  21, 745L, 0, 10.0, None, None, None, None, expired = false, 0L, None,
                  Seq(LaneProperty("lane_code", Seq(LanePropertyValue(21)))) )

    val roadAddress = RoadAddressTEMP(roadLinkTowards1.linkId, 6, 202, Track.Combined, 1, 150, 0, 150,
                        Seq(Point(0.0, 0.0), Point(15.0, 0.0)), Some(SideCode.TowardsDigitizing), None)

    val (persistedLanes, changeSet) = ServiceWithDao.adjustLanesSideCodes(roadLinkTowards1,Seq(lane11, lane21), ChangeSet(), Seq(roadAddress) )


    persistedLanes should have size 1
    persistedLanes.map(_.id) should be (List(1L))
    persistedLanes.map(_.sideCode) should be (Seq(SideCode.BothDirections.value))
    persistedLanes.map(_.laneCode) should be (List(11))
    persistedLanes.map(_.linkId) should be (Seq(roadLinkTowards1.linkId))

    changeSet.expiredLaneIds should be (Set(2L))
  }

  test("Fix wrong sideCodes on a BothDirections roadlink") {
    val roadLinkTowards1 = RoadLink(1L, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None)

    val lane11 = PersistedLane(1L, roadLinkTowards1.linkId, SideCode.AgainstDigitizing.value,
      11, 745L, 0, 10.0, None, None, None, None, expired = false, 0L, None,
      Seq(LaneProperty("lane_code", Seq(LanePropertyValue(11)))) )

    val lane21 = PersistedLane(2L, roadLinkTowards1.linkId, SideCode.TowardsDigitizing.value,
      21, 745L, 0, 10.0, None, None, None, None, expired = false, 0L, None,
      Seq(LaneProperty("lane_code", Seq(LanePropertyValue(21)))) )

    val roadAddress = RoadAddressTEMP(roadLinkTowards1.linkId, 6, 202, Track.Combined, 1, 150, 0, 150,
      Seq(Point(0.0, 0.0), Point(15.0, 0.0)), Some(SideCode.TowardsDigitizing), None)

    val (persistedLanes, changeSet) = ServiceWithDao.adjustLanesSideCodes(roadLinkTowards1,Seq(lane11, lane21), ChangeSet(), Seq(roadAddress) )

    persistedLanes should have size 2
    persistedLanes.map(_.id).sorted should be (List(1L, 2L))
    persistedLanes.map(_.sideCode).sorted should be (Seq(SideCode.TowardsDigitizing.value, SideCode.AgainstDigitizing.value))
    persistedLanes.map(_.laneCode).sorted should be (List(11, 21))
    persistedLanes.find(_.laneCode == 11).get.sideCode should be (SideCode.TowardsDigitizing.value)
    persistedLanes.map(_.linkId) should be (Seq(roadLinkTowards1.linkId, roadLinkTowards1.linkId))

    changeSet.expiredLaneIds should be (Set())
    changeSet.adjustedSideCodes should be (Seq(SideCodeAdjustment(1L, SideCode.TowardsDigitizing), SideCodeAdjustment(2L, SideCode.AgainstDigitizing)))
  }

  test("Populate start date automatically") {
    val lanes = Set(NewIncomeLane(0, 0, 500, 745, false, false, lanePropertiesValues11),
      NewIncomeLane(0, 0, 500, 745, false, false, lanePropertiesValues12))

    val currentTime = DateTime.now().toString(DatePropertyFormat)

    val populatedLanes = ServiceWithDao.populateStartDate(lanes)

    populatedLanes.size should be(2)
    populatedLanes.map(_.properties.length) should be(Set(2, 3))

    val lanePopulated = populatedLanes.find(lane => !LaneNumber.isMainLane(ServiceWithDao.getLaneCode(lane).toInt)).get
    lanePopulated.properties.find(_.publicId == "start_date").get.values.head.value.toString should be(currentTime)
  }
}
