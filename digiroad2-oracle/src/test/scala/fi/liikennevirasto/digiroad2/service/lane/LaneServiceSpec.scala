package fi.liikennevirasto.digiroad2.service.lane

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.{VVHClient, VVHRoadLinkClient}
import fi.liikennevirasto.digiroad2.dao.{MunicipalityDao, RoadAddressTEMP}
import fi.liikennevirasto.digiroad2.dao.lane.{LaneDao, LaneHistoryDao}
import fi.liikennevirasto.digiroad2.lane.LaneFiller.ChangeSet
import fi.liikennevirasto.digiroad2.lane.{LaneChangeType, LaneProperty, LanePropertyValue, NewIncomeLane, PersistedLane}
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
  val mockLaneHistoryDao = MockitoSugar.mock[LaneHistoryDao]

  val laneDao = new LaneDao(mockVVHClient, mockRoadLinkService)
  val laneHistoryDao = new LaneHistoryDao(mockVVHClient, mockRoadLinkService)
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
    override def historyDao: LaneHistoryDao = mockLaneHistoryDao
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
    override def historyDao: LaneHistoryDao = laneHistoryDao
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

      //Verify the presence one line with old data before the udate on histories tables
      val historyLanes = laneHistoryDao.fetchHistoryLanesByLinkIdsAndLaneCode(Seq(100L), Seq(11), true)
      historyLanes.size should be(1)

      val oldLaneData = historyLanes.filter(_.oldId == newLane11.head).head
      oldLaneData.oldId should be(newLane11.head)
      oldLaneData.expired should be(false)
      oldLaneData.attributes.foreach { laneProp =>
        lanePropertiesValues11.contains(laneProp) should be(true)
      }
    }
  }

  test("Expire a sub lane") {
    runWithRollback {
      //NewIncomeLanes to create
      val mainLane11ToAdd = Seq(NewIncomeLane(0, 0, 500, 745, false, false, lanePropertiesValues11))
      val subLane12ToAdd = Seq(NewIncomeLane(0, 0, 500, 745, false, false, lanePropertiesValues12))

      //Create initial lanes
      val newMainLaneid = ServiceWithDao.create(mainLane11ToAdd, Set(100L), 1, usernameTest).head
      val newSubLaneId = ServiceWithDao.create(subLane12ToAdd, Set(100L), 1, usernameTest).head


      //Validate if initial lanes are correctly created
      val currentLanes = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(100L), Seq(11, 12), false)
      currentLanes.size should be(2)

      val lane11 = currentLanes.filter(_.id == newMainLaneid).head
      lane11.id should be(newMainLaneid)
      lane11.attributes.foreach{ laneProp =>
        lanePropertiesValues11.contains(laneProp) should be(true)
      }

      val lane12 = currentLanes.filter(_.id == newSubLaneId).head
      lane12.id should be(newSubLaneId)
      lane12.attributes.foreach{ laneProp =>
        lanePropertiesValues12.contains(laneProp) should be(true)
      }


      //Delete sublane 12 and verify the movement to history tables
      val subLane12ToExpire = Seq(NewIncomeLane(newSubLaneId, 0, 500, 745, true, false, lanePropertiesValues12))

      //Verify if the lane to delete was totally deleted from lane table
      val currentMainLane11 = Seq(mainLane11ToAdd.head.copy(id = newMainLaneid))
      ServiceWithDao.processNewIncomeLanes((currentMainLane11 ++ subLane12ToExpire).toSet, Set(100L), 1, usernameTest)
      val currentLanesAfterDelete = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(100L), Seq(11, 12), false)
      currentLanesAfterDelete.size should be(1)

      val lane11AfterDelete = currentLanesAfterDelete.filter(_.id == newMainLaneid).head
      lane11AfterDelete.id should be(newMainLaneid)
      lane11AfterDelete.attributes.foreach{ laneProp =>
        lanePropertiesValues11.contains(laneProp) should be(true)
      }

      //Verify the presence of the deleted lane on histories tables
      val historyLanes = laneHistoryDao.fetchHistoryLanesByLinkIdsAndLaneCode(Seq(100L), Seq(11, 12), true)
      historyLanes.size should be(1)

      val lane12AfterDelete = historyLanes.filter(_.oldId == newSubLaneId).head
      lane12AfterDelete.oldId should be(newSubLaneId)
      lane12AfterDelete.expired should be(true)
      lane12AfterDelete.attributes.foreach{ laneProp =>
        lanePropertiesValues12.contains(laneProp) should be(true)
      }
    }
  }

  test("Split current lane asset. Split lane in two but only keep one part") {
    runWithRollback {
      val mainLane11 = NewIncomeLane(0, 0, 500, 745, false, false, lanePropertiesValues11)
      val subLane12 = NewIncomeLane(0, 0, 500, 745, false, false, lanePropertiesValues12)
      val subLane12Splited = NewIncomeLane(0, 0, 250, 745, false, false, lanePropertiesValues12)

      //create lanes 11, 12
      val mainLane11Id = ServiceWithDao.create(Seq(mainLane11), Set(100L), 1, usernameTest).head
      val newSubLane12Id = ServiceWithDao.create(Seq(subLane12), Set(100L), 1, usernameTest).head

      //Validate if initial lanes are correctly created
      val currentLanes = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(100L), Seq(11, 12), false)
      currentLanes.size should be(2)

      val lane11 = currentLanes.filter(_.id == mainLane11Id).head
      lane11.id should be(mainLane11Id)
      lane11.attributes.foreach { laneProp =>
        lanePropertiesValues11.contains(laneProp) should be(true)
      }

      val lane12 = currentLanes.filter(_.id == newSubLane12Id).head
      lane12.id should be(newSubLane12Id)
      lane12.startMeasure should be(0)
      lane12.endMeasure should be(500)
      lane12.attributes.foreach { laneProp =>
        lanePropertiesValues12.contains(laneProp) should be(true)
      }


      //Simulation of sending a main lane, and one sublane splited and stored only one part
      val currentMainLane11 = mainLane11.copy(id = mainLane11Id)
      ServiceWithDao.processNewIncomeLanes(Set(currentMainLane11, subLane12Splited), Set(100L), 1, usernameTest)

      val lanesAfterSplit = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(100L), Seq(11, 12), true)
      lanesAfterSplit.size should be(2)

      val lane11AfterSplit = lanesAfterSplit.filter(_.id == mainLane11Id).head
      lane11AfterSplit.id should be(mainLane11Id)
      lane11AfterSplit.attributes.foreach { laneProp =>
        lanePropertiesValues11.contains(laneProp) should be(true)
      }

      val lane12AfterSplit = lanesAfterSplit.filter(_.laneCode == 12).head
      lane12AfterSplit.id should not be newSubLane12Id
      lane12AfterSplit.startMeasure should be(0)
      lane12AfterSplit.endMeasure should be(250)
      lane12AfterSplit.attributes.foreach { laneProp =>
        lanePropertiesValues12.contains(laneProp) should be(true)
      }


      //Verify the presence of the old splitted lane on histories tables
      val historyLanes = laneHistoryDao.fetchHistoryLanesByLinkIdsAndLaneCode(Seq(100L), Seq(11, 12), true)
      historyLanes.size should be(1)

      val historyLane12 = historyLanes.filter(_.oldId == newSubLane12Id).head
      historyLane12.newId should not be 0
      historyLane12.newId should not be mainLane11Id
      historyLane12.oldId should be(newSubLane12Id)
      historyLane12.startMeasure should be(0)
      historyLane12.endMeasure should be(500)
      historyLane12.expired should be(true)
      historyLane12.attributes.foreach { laneProp =>
        lanePropertiesValues12.contains(laneProp) should be(true)

      }
    }
  }

  test("Create shorter lane in comparation with linkId usign cuting tool") {
    runWithRollback {
      val mainLane11 = NewIncomeLane(0, 0, 500, 745, false, false, lanePropertiesValues11)
      val subLane12Splited = NewIncomeLane(0, 0, 250, 745, false, false, lanePropertiesValues12)

      val mainLane11Id = ServiceWithDao.create(Seq(mainLane11), Set(100L), 1, usernameTest).head
      val subLane12SplitedId = ServiceWithDao.create(Seq(subLane12Splited), Set(100L), 1, usernameTest).head

      //Validate if initial lanes are correctly created
      val currentLanes = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(100L), Seq(11, 12), false)
      currentLanes.size should be(2)

      val lane11 = currentLanes.filter(_.id == mainLane11Id).head
      lane11.id should be(mainLane11Id)
      lane11.attributes.foreach { laneProp =>
        lanePropertiesValues11.contains(laneProp) should be(true)
      }

      val lane12 = currentLanes.filter(_.id == subLane12SplitedId).head
      lane12.id should be(subLane12SplitedId)
      lane12.startMeasure should be(0)
      lane12.endMeasure should be(250.0)
      lane12.attributes.foreach { laneProp =>
        lanePropertiesValues12.contains(laneProp) should be(true)
      }
    }
  }

  test("Create new lane splitted since the beigining, keeping two parts") {
    runWithRollback {
      val mainLane11 = NewIncomeLane(0, 0, 500, 745, false, false, lanePropertiesValues11)
      val subLane12SplitedA = NewIncomeLane(0, 0, 250, 745, false, false, lanePropertiesValues12)
      val subLane12SplitedB = NewIncomeLane(0, 250, 500, 745, false, false, lanePropertiesValues12)

      val mainLane11Id = ServiceWithDao.create(Seq(mainLane11), Set(100L), 1, usernameTest).head
      val subLane12SplitedAId = ServiceWithDao.create(Seq(subLane12SplitedA), Set(100L), 1, usernameTest).head
      val subLane12SplitedBId = ServiceWithDao.create(Seq(subLane12SplitedB), Set(100L), 1, usernameTest).head

      //Validate if initial lanes are correctly created
      val currentLanes = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(100L), Seq(11, 12), false)
      currentLanes.size should be(3)

      val lane11 = currentLanes.filter(_.id == mainLane11Id).head
      lane11.id should be(mainLane11Id)
      lane11.attributes.foreach { laneProp =>
        lanePropertiesValues11.contains(laneProp) should be(true)
      }

      val lane12A = currentLanes.filter(_.id == subLane12SplitedAId).head
      lane12A.id should be(subLane12SplitedAId)
      lane12A.startMeasure should be(0)
      lane12A.endMeasure should be(250.0)
      lane12A.attributes.foreach { laneProp =>
        lanePropertiesValues12.contains(laneProp) should be(true)
      }

      val lane12B = currentLanes.filter(_.id == subLane12SplitedBId).head
      lane12B.id should be(subLane12SplitedBId)
      lane12B.startMeasure should be(250.0)
      lane12B.endMeasure should be(500.0)
      lane12B.attributes.foreach { laneProp =>
        lanePropertiesValues12.contains(laneProp) should be(true)
      }
    }
  }

  test("Split current lane asset. Expire lane and create two new lanes") {
    runWithRollback {
      val lanePropertiesSubLaneSplit2 = Seq(
        LaneProperty("lane_code", Seq(LanePropertyValue(12))),
        LaneProperty("lane_type", Seq(LanePropertyValue("5")))
      )

      val mainLane = NewIncomeLane(0, 0, 500, 745, false, false, lanePropertiesValues11)
      val subLane12 = NewIncomeLane(0, 0, 500, 745, false, false, lanePropertiesValues12)
      val subLane12SplitA = NewIncomeLane(0, 0, 250, 745, false, false, lanePropertiesValues12)
      val subLane12SplitB = NewIncomeLane(0, 250, 500, 745, false, false, lanePropertiesSubLaneSplit2)

      val mainLane11Id = ServiceWithDao.create(Seq(mainLane), Set(100L), 1, usernameTest).head
      val newSubLane12Id = ServiceWithDao.create(Seq(subLane12), Set(100L), 1, usernameTest).head

      //Validate if initial lanes are correctly created
      val currentLanes = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(100L), Seq(11, 12), false)
      currentLanes.size should be(2)

      val lane11 = currentLanes.filter(_.id == mainLane11Id).head
      lane11.id should be(mainLane11Id)
      lane11.attributes.foreach { laneProp =>
        lanePropertiesValues11.contains(laneProp) should be(true)
      }

      val lane12 = currentLanes.filter(_.id == newSubLane12Id).head
      lane12.id should be(newSubLane12Id)
      lane12.startMeasure should be(0)
      lane12.endMeasure should be(500.0)
      lane12.attributes.foreach { laneProp =>
        lanePropertiesValues12.contains(laneProp) should be(true)
      }


      //Simulation of sending a main lane, and two sublanes splited and stored both
      val currentMainLane = mainLane.copy(id = mainLane11Id)
      ServiceWithDao.processNewIncomeLanes(Set(currentMainLane, subLane12SplitA, subLane12SplitB), Set(100L), 1, usernameTest)

      val lanesAfterSplit = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(100L), Seq(11, 12), true)
      lanesAfterSplit.size should be(3)

      val lane11AfterSplit = lanesAfterSplit.filter(_.id == mainLane11Id).head
      lane11AfterSplit.id should be(mainLane11Id)
      lane11AfterSplit.attributes.foreach { laneProp =>
        lanePropertiesValues11.contains(laneProp) should be(true)
      }

      lanesAfterSplit.count(_.laneCode == 12) should be(2)

      val lane12A = lanesAfterSplit.filter(l => l.startMeasure == 0 && l.laneCode == 12).head
      lane12A.startMeasure should be(0)
      lane12A.endMeasure should be(250.0)
      lane12A.attributes.foreach { laneProp =>
        lanePropertiesValues12.contains(laneProp) should be(true)
      }

      val lane12B = lanesAfterSplit.filter(l => l.startMeasure == 250.0).head
      lane12B.startMeasure should be(250.0)
      lane12B.endMeasure should be(500.0)
      lane12B.attributes.foreach { laneProp =>
        lanePropertiesSubLaneSplit2.contains(laneProp) should be(true)
      }


      //Verify the presence of the old splitted lanes on histories tables
      val historyLanes = laneHistoryDao.fetchHistoryLanesByLinkIdsAndLaneCode(Seq(100L), Seq(11, 12), true)
      historyLanes.size should be(2)

      historyLanes.filter(_.oldId == newSubLane12Id).foreach { historyLane12 =>
        historyLane12.newId should not be 0
        historyLane12.newId should not be mainLane11Id
        historyLane12.startMeasure should be(0)
        historyLane12.endMeasure should be(500)
        historyLane12.expired should be(true)
        historyLane12.attributes.foreach { laneProp =>
          lanePropertiesValues12.contains(laneProp) should be(true)
        }
      }

    }
  }

  test("Update two lanes in one roadlink to only one lane") {
    runWithRollback {
      val lanePropertiesSubLaneSplit2 = Seq(
        LaneProperty("lane_code", Seq(LanePropertyValue(12))),
        LaneProperty("lane_type", Seq(LanePropertyValue("5")))
      )

      val mainLane = NewIncomeLane(0, 0, 500, 745, false, false, lanePropertiesValues11)
      val subLane12 = NewIncomeLane(0, 0, 500, 745, false, false, lanePropertiesValues12)
      val subLane12SplitA = NewIncomeLane(0, 0, 250, 745, false, false, lanePropertiesValues12)
      val subLane12SplitB = NewIncomeLane(0, 250, 500, 745, false, false, lanePropertiesSubLaneSplit2)

      val mainLane11Id = ServiceWithDao.create(Seq(mainLane), Set(100L), 1, usernameTest).head
      val newSubLane12SplitAId = ServiceWithDao.create(Seq(subLane12SplitA), Set(100L), 1, usernameTest).head
      val newSubLane12SplitBId = ServiceWithDao.create(Seq(subLane12SplitB), Set(100L), 1, usernameTest).head

      //Validate if initial lanes are correctly created
      val currentLanes = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(100L), Seq(11, 12), false)
      currentLanes.size should be(3)

      val lane11 = currentLanes.filter(_.id == mainLane11Id).head
      lane11.id should be(mainLane11Id)
      lane11.attributes.foreach { laneProp =>
        lanePropertiesValues11.contains(laneProp) should be(true)
      }

      val lane12A = currentLanes.filter(_.id == newSubLane12SplitAId).head
      lane12A.id should be(newSubLane12SplitAId)
      lane12A.startMeasure should be(0)
      lane12A.endMeasure should be(250.0)
      lane12A.attributes.foreach { laneProp =>
        lanePropertiesValues12.contains(laneProp) should be(true)
      }


      val lane12B = currentLanes.filter(_.id == newSubLane12SplitBId).head
      lane12B.id should be(newSubLane12SplitBId)
      lane12B.startMeasure should be(250.0)
      lane12B.endMeasure should be(500.0)
      lane12B.attributes.foreach { laneProp =>
        lanePropertiesSubLaneSplit2.contains(laneProp) should be(true)
      }


      //Simulation of sending a main lane, and one sublane not splitted
      val currentMainLane = mainLane.copy(id = mainLane11Id)
      ServiceWithDao.processNewIncomeLanes(Set(currentMainLane, subLane12), Set(100L), 1, usernameTest)

      val lanesAfterSplit = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(100L), Seq(11, 12), true)
      lanesAfterSplit.size should be(2)

      val lane11AfterSplit = lanesAfterSplit.filter(_.id == mainLane11Id).head
      lane11AfterSplit.id should be(mainLane11Id)
      lane11AfterSplit.attributes.foreach { laneProp =>
        lanePropertiesValues11.contains(laneProp) should be(true)
      }

      val lane12AfterSplit = lanesAfterSplit.filter(_.laneCode == 12).head
      lane12AfterSplit.startMeasure should be(0)
      lane12AfterSplit.endMeasure should be(500)
      lane12AfterSplit.expired should be(false)
      lane12AfterSplit.attributes.foreach { laneProp =>
        lanePropertiesValues12.contains(laneProp) should be(true)
      }


      //Verify the presence of the old splitted lanes on histories tables
      val historyLanes = laneHistoryDao.fetchHistoryLanesByLinkIdsAndLaneCode(Seq(100L), Seq(11, 12), true)
      historyLanes.size should be(2)

      val historylane12A = historyLanes.filter(_.oldId == newSubLane12SplitAId).head
      historylane12A.newId should not be 0
      historylane12A.newId should not be mainLane11Id
      historylane12A.startMeasure should be(0)
      historylane12A.endMeasure should be(250.0)
      historylane12A.expired should be(true)
      historylane12A.attributes.foreach { laneProp =>
        lanePropertiesValues12.contains(laneProp) should be(true)
      }

      val historylane12B = historyLanes.filter(_.oldId == newSubLane12SplitBId).head
      historylane12B.newId should not be 0
      historylane12B.newId should not be mainLane11Id
      historylane12B.startMeasure should be(250.0)
      historylane12B.endMeasure should be(500.0)
      historylane12B.expired should be(true)
      historylane12B.attributes.foreach { laneProp =>
        lanePropertiesSubLaneSplit2.contains(laneProp) should be(true)
      }

    }
  }

  test("Update two lanes with same lane code in one roadlink with two sub lanes with same lane code") {
    runWithRollback {
      val modifiedLaneProperties1 = Seq(
        LaneProperty("lane_code", Seq(LanePropertyValue(12))),
        LaneProperty("lane_type", Seq(LanePropertyValue("6")))
      )

      val modifiedLaneProperties2 = Seq(
        LaneProperty("lane_code", Seq(LanePropertyValue(12))),
        LaneProperty("lane_type", Seq(LanePropertyValue("8")))
      )

      val mainLane = NewIncomeLane(0, 0, 500, 745, false, false, lanePropertiesValues11)
      val subLane12SplitA = NewIncomeLane(0, 0, 250, 745, false, false, lanePropertiesValues12)
      val subLane12SplitB = NewIncomeLane(0, 250, 500, 745, false, false, modifiedLaneProperties1)

      val mainLane11Id = ServiceWithDao.create(Seq(mainLane), Set(100L), 1, usernameTest).head
      val newSubLane12SplitAId = ServiceWithDao.create(Seq(subLane12SplitA), Set(100L), 1, usernameTest).head
      val newSubLane12SplitBId = ServiceWithDao.create(Seq(subLane12SplitB), Set(100L), 1, usernameTest).head

      //Validate if initial lanes are correctly created
      val currentLanes = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(100L), Seq(11, 12), false)
      currentLanes.size should be(3)

      val lane11 = currentLanes.filter(_.id == mainLane11Id).head
      lane11.id should be(mainLane11Id)
      lane11.attributes.foreach { laneProp =>
        lanePropertiesValues11.contains(laneProp) should be(true)
      }

      val lane12A = currentLanes.filter(_.id == newSubLane12SplitAId).head
      lane12A.id should be(newSubLane12SplitAId)
      lane12A.startMeasure should be(0)
      lane12A.endMeasure should be(250.0)
      lane12A.attributes.foreach { laneProp =>
        lanePropertiesValues12.contains(laneProp) should be(true)
      }

      val lane12B = currentLanes.filter(_.id == newSubLane12SplitBId).head
      lane12B.id should be(newSubLane12SplitBId)
      lane12B.startMeasure should be(250.0)
      lane12B.endMeasure should be(500.0)
      lane12B.attributes.foreach { laneProp =>
        modifiedLaneProperties1.contains(laneProp) should be(true)
      }


      //Simulation of sending a main lane, and two modificated sublanes on same link
      val updatedSubLane12SplitA = NewIncomeLane(newSubLane12SplitAId, 0, 250, 745, false, false, modifiedLaneProperties1)
      val updatedSubLane12SplitB = NewIncomeLane(newSubLane12SplitBId, 250, 500, 745, false, false, modifiedLaneProperties2)
      val currentMainLane = mainLane.copy(id = mainLane11Id)
      ServiceWithDao.processNewIncomeLanes(Set(currentMainLane, updatedSubLane12SplitA, updatedSubLane12SplitB), Set(100L), 1, usernameTest)

      val lanesAfterSplit = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(100L), Seq(11, 12), true)
      lanesAfterSplit.size should be(3)

      val lane11AfterUpdate = lanesAfterSplit.filter(_.id == mainLane11Id).head
      lane11AfterUpdate.id should be(mainLane11Id)
      lane11AfterUpdate.attributes.foreach { laneProp =>
        lanePropertiesValues11.contains(laneProp) should be(true)
      }

      val laneA12AfterUpdate = lanesAfterSplit.filter(_.id == newSubLane12SplitAId).head
      laneA12AfterUpdate.id should be(newSubLane12SplitAId)
      laneA12AfterUpdate.startMeasure should be(0)
      laneA12AfterUpdate.endMeasure should be(250.0)
      laneA12AfterUpdate.expired should be(false)
      laneA12AfterUpdate.attributes.foreach { laneProp =>
        modifiedLaneProperties1.contains(laneProp) should be(true)
      }

      val laneB12AfterUpdate = lanesAfterSplit.filter(_.id == newSubLane12SplitBId).head
      laneB12AfterUpdate.id should be(newSubLane12SplitBId)
      laneB12AfterUpdate.startMeasure should be(250.0)
      laneB12AfterUpdate.endMeasure should be(500.0)
      laneB12AfterUpdate.expired should be(false)
      laneB12AfterUpdate.attributes.foreach { laneProp =>
        modifiedLaneProperties2.contains(laneProp) should be(true)
      }

      //Verify the presence of the old splitted lanes attributes on histories tables
      val historyLanes = laneHistoryDao.fetchHistoryLanesByLinkIdsAndLaneCode(Seq(100L), Seq(11, 12), true)
      historyLanes.size should be(2)

      val historylane12A = historyLanes.filter(_.oldId == newSubLane12SplitAId).head
      historylane12A.newId should be(0)
      historylane12A.startMeasure should be(0)
      historylane12A.endMeasure should be(250.0)
      historylane12A.expired should be(false)
      historylane12A.attributes.foreach { laneProp =>
        lanePropertiesValues12.contains(laneProp) should be(true)
      }

      val historylane12B = historyLanes.filter(_.oldId == newSubLane12SplitBId).head
      historylane12B.newId should be(0)
      historylane12B.startMeasure should be(250.0)
      historylane12B.endMeasure should be(500.0)
      historylane12B.expired should be(false)
      historylane12B.attributes.foreach { laneProp =>
        modifiedLaneProperties1.contains(laneProp) should be(true)
      }
    }
  }

  test("Delete sub lane in middle of lanes") {
    runWithRollback {
      val lanePropertiesValues14To12 = Seq(LaneProperty("lane_code", Seq(LanePropertyValue(12))),
        LaneProperty("lane_type", Seq(LanePropertyValue("3")))
      )

      val mainLane = NewIncomeLane(0, 0, 500, 745, false, false, lanePropertiesValues11)
      val subLane12 = NewIncomeLane(0, 0, 500, 745, false, false, lanePropertiesValues12)
      val subLane14 = NewIncomeLane(0, 0, 500, 745, false, false, lanePropertiesValues14)

      val mainLaneId = ServiceWithDao.create(Seq(mainLane), Set(100L), 1, usernameTest).head
      val newSubLane12Id = ServiceWithDao.create(Seq(subLane12), Set(100L), 1, usernameTest).head
      val newSubLane14Id = ServiceWithDao.create(Seq(subLane14), Set(100L), 1, usernameTest).head


      // Delete the lane 12 and update 14 to new 12
      val updatedSubLane14 = NewIncomeLane(newSubLane14Id, 0, 500, 745, false, false, lanePropertiesValues14To12)
      val currentMainLane = mainLane.copy(id = mainLaneId)
      ServiceWithDao.processNewIncomeLanes(Set(currentMainLane, updatedSubLane14), Set(100L), 1, usernameTest)


      //Validate the delete of old lane 12 and the movement of lane 14 to 12
      val lanes = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(100L), Seq(11, 12, 14), true)
      lanes.length should be(2)
      lanes.find(_.id == newSubLane12Id) should be(None)
      lanes.find(_.laneCode == 14) should be(None)

      val newSubLane12 = lanes.find(_.id == newSubLane14Id).head
      newSubLane12.attributes.foreach { laneProp =>
        updatedSubLane14.properties.contains(laneProp) should be(true)
      }
      newSubLane12.startMeasure should be(0.0)
      newSubLane12.endMeasure should be(500.0)


      //Confirm the expired lane 12 and old info of lane 14 (Now 12) in history table
      val historyLanes = laneHistoryDao.fetchHistoryLanesByLinkIdsAndLaneCode(Seq(100L), Seq(11, 12, 14), true)
      historyLanes.length should be(2)

      val deletedSubLane12 = historyLanes.find(_.oldId == newSubLane12Id).head
      deletedSubLane12.newId should be(newSubLane14Id)
      deletedSubLane12.attributes.foreach { laneProp =>
        lanePropertiesValues12.contains(laneProp) should be(true)
      }
      deletedSubLane12.startMeasure should be(0.0)
      deletedSubLane12.endMeasure should be(500.0)
      deletedSubLane12.expired should be(true)

      val oldDataSubLane14 = historyLanes.find(_.oldId == newSubLane14Id).head
      oldDataSubLane14.newId should be(0)
      oldDataSubLane14.attributes.foreach { laneProp =>
        lanePropertiesValues14.contains(laneProp) should be(true)
      }
      oldDataSubLane14.expired should be(false)
      oldDataSubLane14.startMeasure should be(0.0)
      oldDataSubLane14.endMeasure should be(500.0)
    }
  }

  test("RoadLink BothDirection and with only one Lane (TowardsDigitizing). The 2nd should be created.") {
    runWithRollback {

      val roadLinkTowards2 = RoadLink(2L, Seq(Point(0.0, 0.0), Point(15.0, 0.0)), 15.0, Municipality,
                              1, TrafficDirection.BothDirections, Motorway, None, None)

      val lane11 = PersistedLane(2L, roadLinkTowards2.linkId, SideCode.TowardsDigitizing.value,
                  11, 745L, 0, 15.0, None, None, None, None, None, None, expired = false, 0L, None,
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
                  21, 745L, 0, 15.0, None, None, None, None, None, None, expired = false, 0L, None,
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
                  21, 745L, 0, 10.0, None, None, None, None, None, None, expired = false, 0L, None,
                  Seq(LaneProperty("lane_code", Seq(LanePropertyValue(11)))) )

    val lane21 = PersistedLane(2L, roadLinkTowards1.linkId, SideCode.AgainstDigitizing.value,
                  21, 745L, 0, 10.0, None, None, None, None, None, None, expired = false, 0L, None,
                  Seq(LaneProperty("lane_code", Seq(LanePropertyValue(21)))) )

    val roadAddress = RoadAddressTEMP(roadLinkTowards1.linkId, 6, 202, Track.Combined, 1, 150, 0, 150,
                        Seq(Point(0.0, 0.0), Point(15.0, 0.0)), Some(SideCode.TowardsDigitizing), None)

    val (persistedLanes, changeSet) = ServiceWithDao.adjustLanesSideCodes(roadLinkTowards1,Seq(lane11, lane21), ChangeSet(), Seq(roadAddress) )


    persistedLanes should have size 1
    persistedLanes.map(_.sideCode) should be (Seq(SideCode.BothDirections.value))
    persistedLanes.map(_.laneCode) should be (List(11))
    persistedLanes.map(_.linkId) should be (Seq(roadLinkTowards1.linkId))

    changeSet.expiredLaneIds should be (Set(1L,2L))
  }

  test("Expire a sub lane and then create another at same lane code with same properties") {
    runWithRollback {
      //NewIncomeLanes to create
      val mainLane11ToAdd = Seq(NewIncomeLane(0, 0, 500, 745, false, false, lanePropertiesValues11))
      val subLane12ToAdd = Seq(NewIncomeLane(0, 0, 500, 745, false, false, lanePropertiesValues12))

      //Create initial lanes
      val newMainLaneId = ServiceWithDao.create(mainLane11ToAdd, Set(100L), 1, usernameTest).head
      val newSubLaneId = ServiceWithDao.create(subLane12ToAdd, Set(100L), 1, usernameTest).head


      //Validate if initial lanes are correctly created
      val currentLanes = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(100L), Seq(11, 12), false)
      currentLanes.size should be(2)

      val lane11 = currentLanes.filter(_.id == newMainLaneId).head
      lane11.attributes.foreach{ laneProp =>
        lanePropertiesValues11.contains(laneProp) should be(true)
      }

      val lane12 = currentLanes.filter(_.id == newSubLaneId).head
      lane12.attributes.foreach{ laneProp =>
        lanePropertiesValues12.contains(laneProp) should be(true)
      }


      //Create another sub lane 12
      val newSubLane12WithSameProperties = Seq(NewIncomeLane(0, 0, 500, 745, false, false, lanePropertiesValues12))

      //Verify if the lane to delete was totally deleted from lane table
      val currentMainLane11 = Seq(mainLane11ToAdd.head.copy(id = newMainLaneId))
      ServiceWithDao.processNewIncomeLanes((currentMainLane11 ++ newSubLane12WithSameProperties).toSet, Set(100L), 1, usernameTest)
      val currentLanesAfterProcess = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(100L), Seq(11, 12), false)
      currentLanesAfterProcess.size should be(2)

      val lane11After = currentLanesAfterProcess.filter(_.id == newMainLaneId).head
      lane11After.attributes.foreach{ laneProp =>
        lanePropertiesValues11.contains(laneProp) should be(true)
      }

      val lane12After = currentLanesAfterProcess.filter(_.id == newSubLaneId).head
      lane12After.attributes.foreach{ laneProp =>
        lanePropertiesValues12.contains(laneProp) should be(true)
      }

      //Nothing changed so nothing will be in history
      val historyLanes = laneHistoryDao.fetchHistoryLanesByLinkIdsAndLaneCode(Seq(100L), Seq(11, 12), true)
      historyLanes.size should be(0)
    }
  }

  test("Expire a sub lane and then create another at same lane code with different properties") {
    runWithRollback {
      val newLanePropertiesValues12 = Seq( LaneProperty("lane_code", Seq(LanePropertyValue(12))),
        LaneProperty("lane_type", Seq(LanePropertyValue("3")))
      )

      //NewIncomeLanes to create
      val mainLane11ToAdd = Seq(NewIncomeLane(0, 0, 500, 745, false, false, lanePropertiesValues11))
      val subLane12ToAdd = Seq(NewIncomeLane(0, 0, 500, 745, false, false, lanePropertiesValues12))

      //Create initial lanes
      val newMainLaneId = ServiceWithDao.create(mainLane11ToAdd, Set(100L), 1, usernameTest).head
      val newSubLaneId = ServiceWithDao.create(subLane12ToAdd, Set(100L), 1, usernameTest).head


      //Validate if initial lanes are correctly created
      val currentLanes = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(100L), Seq(11, 12), false)
      currentLanes.size should be(2)

      val lane11 = currentLanes.filter(_.id == newMainLaneId).head
      lane11.attributes.foreach{ laneProp =>
        lanePropertiesValues11.contains(laneProp) should be(true)
      }

      val lane12 = currentLanes.filter(_.id == newSubLaneId).head
      lane12.attributes.foreach{ laneProp =>
        lanePropertiesValues12.contains(laneProp) should be(true)
      }


      //Create another sub lane 12
      val newSubLane12WithDiffProperties = Seq(NewIncomeLane(0, 0, 500, 745, false, false, newLanePropertiesValues12))

      //Verify if the lane to delete was totally deleted from lane table
      val currentMainLane11 = Seq(mainLane11ToAdd.head.copy(id = newMainLaneId))
      ServiceWithDao.processNewIncomeLanes((currentMainLane11 ++ newSubLane12WithDiffProperties).toSet, Set(100L), 1, usernameTest)
      val currentLanesAfterProcess = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(100L), Seq(11, 12), false)
      currentLanesAfterProcess.size should be(2)

      val lane11After = currentLanesAfterProcess.filter(_.id == newMainLaneId).head
      lane11After.attributes.foreach{ laneProp =>
        lanePropertiesValues11.contains(laneProp) should be(true)
      }

      val lane12After = currentLanesAfterProcess.filter(_.id == newSubLaneId).head
      lane12After.attributes.foreach{ laneProp =>
        newLanePropertiesValues12.contains(laneProp) should be(true)
      }

      //Verify history for old properties of lane 12
      val historyLanes = laneHistoryDao.fetchHistoryLanesByLinkIdsAndLaneCode(Seq(100L), Seq(11, 12), true)
      historyLanes.size should be(1)

      val oldSubLane12 = historyLanes.find(_.oldId == newSubLaneId).head
      oldSubLane12.newId should be(0)
      oldSubLane12.attributes.foreach { laneProp =>
        lanePropertiesValues12.contains(laneProp) should be(true)
      }
      oldSubLane12.expired should be(false)

    }
  }

  test("Expire a outer sub lane in various links") {
    runWithRollback {
      //NewIncomeLanes to create
      val mainLane11ToAdd = Seq(NewIncomeLane(0, 0, 500, 745, false, false, lanePropertiesValues11))
      val subLane12ToAdd = Seq(NewIncomeLane(0, 0, 500, 745, false, false, lanePropertiesValues12))

      //Create initial lanes
      val newMainLaneIdLink100 = ServiceWithDao.create(mainLane11ToAdd, Set(100L), 1, usernameTest).head
      val newSubLaneIdLink100 = ServiceWithDao.create(subLane12ToAdd, Set(100L), 1, usernameTest).head

      val newMainLaneIdLink101 = ServiceWithDao.create(mainLane11ToAdd, Set(101L), 1, usernameTest).head
      val newSubLaneIdLink101 = ServiceWithDao.create(subLane12ToAdd, Set(101L), 1, usernameTest).head

      //Validate if initial lanes are correctly created
      val currentLanes = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(100L, 101L), Seq(11, 12), false)
      currentLanes.size should be(4)

      val lane11Link100 = currentLanes.filter(_.id == newMainLaneIdLink100).head
      lane11Link100.attributes.foreach{ laneProp =>
        lanePropertiesValues11.contains(laneProp) should be(true)
      }

      val lane12Link100 = currentLanes.filter(_.id == newSubLaneIdLink100).head
      lane12Link100.attributes.foreach{ laneProp =>
        lanePropertiesValues12.contains(laneProp) should be(true)
      }

      val lane11Link101 = currentLanes.filter(_.id == newMainLaneIdLink101).head
      lane11Link101.attributes.foreach{ laneProp =>
        lanePropertiesValues11.contains(laneProp) should be(true)
      }

      val lane12Link101 = currentLanes.filter(_.id == newSubLaneIdLink101).head
      lane12Link101.attributes.foreach{ laneProp =>
        lanePropertiesValues12.contains(laneProp) should be(true)
      }

      //Delete sublane 12 and verify the movement to history tables
      val subLane12ToExpire = Seq(NewIncomeLane(newSubLaneIdLink100, 0, 500, 745, true, false, lanePropertiesValues12))

      //Verify if the lane to delete was totally deleted from lane table
      val currentMainLane11 = Seq(mainLane11ToAdd.head.copy(id = newMainLaneIdLink100))
      ServiceWithDao.processNewIncomeLanes((currentMainLane11 ++ subLane12ToExpire).toSet, Set(100L, 101L), 1, usernameTest)
      val currentLanesAfterDelete = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(100L, 101L), Seq(11, 12), false)
      currentLanesAfterDelete.size should be(2)

      val lane11Link100AfterDelete = currentLanesAfterDelete.filter(_.id == newMainLaneIdLink100).head
      lane11Link100AfterDelete.attributes.foreach{ laneProp =>
        lanePropertiesValues11.contains(laneProp) should be(true)
      }

      val lane11Link101AfterDelete = currentLanesAfterDelete.filter(_.id == newMainLaneIdLink101).head
      lane11Link101AfterDelete.attributes.foreach{ laneProp =>
        lanePropertiesValues11.contains(laneProp) should be(true)
      }

      //Verify the presence of the deleted lane on histories tables
      val historyLanes = laneHistoryDao.fetchHistoryLanesByLinkIdsAndLaneCode(Seq(100L, 101L), Seq(11, 12), true)
      historyLanes.size should be(2)

      val lane12Link100AfterDelete = historyLanes.filter(_.oldId == newSubLaneIdLink100).head
      lane12Link100AfterDelete.linkId should be(100L)
      lane12Link100AfterDelete.newId should be(0)
      lane12Link100AfterDelete.expired should be(true)
      lane12Link100AfterDelete.attributes.foreach{ laneProp =>
        lanePropertiesValues12.contains(laneProp) should be(true)
      }

      val lane12Link101AfterDelete = historyLanes.filter(_.oldId == newSubLaneIdLink101).head
      lane12Link101AfterDelete.linkId should be(101L)
      lane12Link101AfterDelete.newId should be(0)
      lane12Link101AfterDelete.expired should be(true)
      lane12Link101AfterDelete.attributes.foreach{ laneProp =>
        lanePropertiesValues12.contains(laneProp) should be(true)
      }
    }
  }

  test("Expire a inner sub lane in various links") {
    runWithRollback {
      val lanePropertiesValues14To12 = Seq(LaneProperty("lane_code", Seq(LanePropertyValue(12))),
        LaneProperty("lane_type", Seq(LanePropertyValue("3")))
      )

      val mainLane = NewIncomeLane(0, 0, 500, 745, false, false, lanePropertiesValues11)
      val subLane12 = NewIncomeLane(0, 0, 500, 745, false, false, lanePropertiesValues12)
      val subLane14 = NewIncomeLane(0, 0, 500, 745, false, false, lanePropertiesValues14)

      val mainLaneIdLink100 = ServiceWithDao.create(Seq(mainLane), Set(100L), 1, usernameTest).head
      val newSubLane12IdLink100 = ServiceWithDao.create(Seq(subLane12), Set(100L), 1, usernameTest).head
      val newSubLane14IdLink100 = ServiceWithDao.create(Seq(subLane14), Set(100L), 1, usernameTest).head

      val mainLaneIdLink101 = ServiceWithDao.create(Seq(mainLane), Set(101L), 1, usernameTest).head
      val newSubLane12IdLink101 = ServiceWithDao.create(Seq(subLane12), Set(101L), 1, usernameTest).head
      val newSubLane14IdLink101 = ServiceWithDao.create(Seq(subLane14), Set(101L), 1, usernameTest).head


      // Delete the lane 12 and update 14 to new 12
      val updatedSubLane14 = NewIncomeLane(newSubLane14IdLink100, 0, 500, 745, false, false, lanePropertiesValues14To12)
      val currentMainLane = mainLane.copy(id = mainLaneIdLink100)
      ServiceWithDao.processNewIncomeLanes(Set(currentMainLane, updatedSubLane14), Set(100L, 101L), 1, usernameTest)


      //Validate the delete of old lane 12 and the movement of lane 14 to 12
      val lanes = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(100L, 101L), Seq(11, 12, 14), true)
      lanes.length should be(4)
      lanes.find(lane => lane.id == newSubLane12IdLink100 || lane.id == newSubLane12IdLink101) should be(None)
      lanes.find(_.laneCode == 14) should be(None)

      val newSubLane12Link100 = lanes.find(_.id == newSubLane14IdLink100).head
      newSubLane12Link100.attributes.foreach { laneProp =>
        updatedSubLane14.properties.contains(laneProp) should be(true)
      }
      newSubLane12Link100.startMeasure should be(0.0)
      newSubLane12Link100.endMeasure should be(500.0)

      val newSubLane12Link101 = lanes.find(_.id == newSubLane14IdLink101).head
      newSubLane12Link101.attributes.foreach { laneProp =>
        updatedSubLane14.properties.contains(laneProp) should be(true)
      }
      newSubLane12Link101.startMeasure should be(0.0)
      newSubLane12Link101.endMeasure should be(500.0)


      //Confirm the expired lane 12 and old info of lane 14 (Now 12) in history table
      val historyLanes = laneHistoryDao.fetchHistoryLanesByLinkIdsAndLaneCode(Seq(100L, 101L), Seq(11, 12, 14), true)
      historyLanes.length should be(4)

      val deletedSubLane12Link100 = historyLanes.find(_.oldId == newSubLane12IdLink100).head
      deletedSubLane12Link100.linkId should be(100L)
      deletedSubLane12Link100.newId should be(newSubLane14IdLink100)
      deletedSubLane12Link100.attributes.foreach { laneProp =>
        lanePropertiesValues12.contains(laneProp) should be(true)
      }
      deletedSubLane12Link100.startMeasure should be(0.0)
      deletedSubLane12Link100.endMeasure should be(500.0)
      deletedSubLane12Link100.expired should be(true)

      val deletedSubLane14Link100 = historyLanes.find(_.oldId == newSubLane14IdLink100).head
      deletedSubLane14Link100.linkId should be(100L)
      deletedSubLane14Link100.newId should be(0)
      deletedSubLane14Link100.attributes.foreach { laneProp =>
        lanePropertiesValues14.contains(laneProp) should be(true)
      }
      deletedSubLane14Link100.expired should be(false)
      deletedSubLane14Link100.startMeasure should be(0.0)
      deletedSubLane14Link100.endMeasure should be(500.0)

      val deletedSubLane12Link101 = historyLanes.find(_.oldId == newSubLane12IdLink101).head
      deletedSubLane12Link101.linkId should be(101L)
      deletedSubLane12Link101.newId should be(newSubLane14IdLink101)
      deletedSubLane12Link101.attributes.foreach { laneProp =>
        lanePropertiesValues12.contains(laneProp) should be(true)
      }
      deletedSubLane12Link101.startMeasure should be(0.0)
      deletedSubLane12Link101.endMeasure should be(500.0)
      deletedSubLane12Link101.expired should be(true)

      val deletedSubLane14Link101 = historyLanes.find(_.oldId == newSubLane14IdLink101).head
      deletedSubLane14Link101.linkId should be(101L)
      deletedSubLane14Link101.newId should be(0)
      deletedSubLane14Link101.attributes.foreach { laneProp =>
        lanePropertiesValues14.contains(laneProp) should be(true)
      }
      deletedSubLane14Link101.expired should be(false)
      deletedSubLane14Link101.startMeasure should be(0.0)
      deletedSubLane14Link101.endMeasure should be(500.0)
    }
  }

  test("Adjust lanes: Create missing main lane 21 and adjust side codes of lanes 11 and 12"){
    runWithRollback {
      val newLane11 = NewIncomeLane(0, 0, 500, 745, false, false, lanePropertiesValues11)
      val newLane12 = newLane11.copy(properties = lanePropertiesValues12)

      val lane11Id = ServiceWithDao.create(Seq(newLane11), Set(100L), 1, usernameTest).head
      val lane12Id = ServiceWithDao.create(Seq(newLane12), Set(100L), 1, usernameTest).head

      val mockRoadLink = RoadLink(100L, Seq(), 500, State, 2, TrafficDirection.BothDirections,
        EnclosedTrafficArea, None, None)
      val mockRoadAddressTEMP = RoadAddressTEMP(mockRoadLink.linkId, 0, 0, Track.Combined,
        1000, 1500, 0, 500, Seq(), Some(SideCode.TowardsDigitizing), Some(0))

      val lanes = ServiceWithDao.fetchExistingLanesByLinkIds(Seq(mockRoadLink.linkId))

      val (updatedLanes, changeSet) = ServiceWithDao.adjustLanesSideCodes(mockRoadLink, lanes, ChangeSet(), Seq(mockRoadAddressTEMP))

      updatedLanes.length should be(3)
      val lanesSideCodes = updatedLanes.map(lane => SideCode(lane.sideCode))
      lanesSideCodes.sortBy(_.value) should be(Seq(SideCode.TowardsDigitizing, SideCode.TowardsDigitizing, SideCode.AgainstDigitizing))
      updatedLanes.map(_.laneCode).sorted should be(Seq(11, 12, 21))
      updatedLanes.count(_.id == 0) should be(1)
      val newLane = updatedLanes.find(_.id == 0).head
      newLane.laneCode should be(21)
      SideCode(newLane.sideCode) should be(SideCode.AgainstDigitizing)

      changeSet.adjustedSideCodes.length should be(2)
      changeSet.adjustedSideCodes.map(_.laneId).sorted should be(Seq(lane11Id, lane12Id).sorted)
      changeSet.adjustedSideCodes.map(_.sideCode) should be(Seq(SideCode.TowardsDigitizing, SideCode.TowardsDigitizing))
      changeSet.expiredLaneIds.size should be(0)
    }
  }

  test("Adjust lanes: Create missing main lane 21 and delete lanes 11 and 12"){
    runWithRollback {
      val newLane11 = NewIncomeLane(0, 0, 500, 745, false, false, lanePropertiesValues11)
      val newLane12 = newLane11.copy(properties = lanePropertiesValues12)

      val lane11Id = ServiceWithDao.create(Seq(newLane11), Set(100L), 1, usernameTest).head
      val lane12Id = ServiceWithDao.create(Seq(newLane12), Set(100L), 1, usernameTest).head

      val mockRoadLink = RoadLink(100L, Seq(), 500, State, 2, TrafficDirection.AgainstDigitizing,
        EnclosedTrafficArea, None, None)
      val mockRoadAddressTEMP = RoadAddressTEMP(mockRoadLink.linkId, 0, 0, Track.Combined,
        1000, 1500, 0, 500, Seq(), Some(SideCode.TowardsDigitizing), Some(0))

      val lanes = ServiceWithDao.fetchExistingLanesByLinkIds(Seq(mockRoadLink.linkId))

      val (updatedLanes, changeSet) = ServiceWithDao.adjustLanesSideCodes(mockRoadLink, lanes, ChangeSet(), Seq(mockRoadAddressTEMP))

      updatedLanes.length should be(1)
      val updatedLane = updatedLanes.head
      SideCode(updatedLane.sideCode) should be(SideCode.BothDirections)
      updatedLane.id should be(0)
      updatedLane.laneCode should be(21)

      changeSet.adjustedSideCodes.length should be(0)
      changeSet.expiredLaneIds.size should be(2)
      changeSet.expiredLaneIds.toSeq.sorted should be(Seq(lane11Id, lane12Id).sorted)
    }
  }

  test("Adjust lanes: Adjust sideCode of lane 11"){
    runWithRollback {
      val lane11Id = ServiceWithDao.create(Seq(NewIncomeLane(0, 0, 500, 745, false, false, lanePropertiesValues11)), Set(100L), 2, usernameTest).head

      val mockRoadLink = RoadLink(100L, Seq(), 500, State, 2, TrafficDirection.TowardsDigitizing,
        EnclosedTrafficArea, None, None)
      val mockRoadAddressTEMP = RoadAddressTEMP(mockRoadLink.linkId, 0, 0, Track.Combined,
        1000, 1500, 0, 500, Seq(), Some(SideCode.TowardsDigitizing), Some(0))

      val lanes = ServiceWithDao.fetchExistingLanesByLinkIds(Seq(mockRoadLink.linkId))

      val (updatedLanes, changeSet) = ServiceWithDao.adjustLanesSideCodes(mockRoadLink, lanes, ChangeSet(), Seq(mockRoadAddressTEMP))

      updatedLanes.length should be(1)
      val updatedLane = updatedLanes.head
      SideCode(updatedLane.sideCode) should be(SideCode.BothDirections)
      updatedLane.id should be(lane11Id)
      updatedLane.laneCode should be(11)

      changeSet.adjustedSideCodes.length should be(1)
      changeSet.expiredLaneIds.size should be(0)
    }
  }

  test("Adjust lanes: Delete lane 21"){
    runWithRollback {
      val newLane11 = NewIncomeLane(0, 0, 500, 745, false, false, lanePropertiesValues11)
      val newLane21 = newLane11.copy(properties = lanePropertiesValues21)

      val lane11Id = ServiceWithDao.create(Seq(newLane11), Set(100L), 1, usernameTest).head
      val lane21Id = ServiceWithDao.create(Seq(newLane21), Set(100L), 1, usernameTest).head

      val mockRoadLink = RoadLink(100L, Seq(), 500, State, 2, TrafficDirection.TowardsDigitizing,
        EnclosedTrafficArea, None, None)
      val mockRoadAddressTEMP = RoadAddressTEMP(mockRoadLink.linkId, 0, 0, Track.Combined,
        1000, 1500, 0, 500, Seq(), Some(SideCode.TowardsDigitizing), Some(0))

      val lanes = ServiceWithDao.fetchExistingLanesByLinkIds(Seq(mockRoadLink.linkId))

      val (updatedLanes, changeSet) = ServiceWithDao.adjustLanesSideCodes(mockRoadLink, lanes, ChangeSet(), Seq(mockRoadAddressTEMP))

      updatedLanes.length should be(1)
      val updatedLane = updatedLanes.head
      SideCode(updatedLane.sideCode) should be(SideCode.BothDirections)
      updatedLane.id should be(lane11Id)
      updatedLane.laneCode should be(11)

      changeSet.adjustedSideCodes.length should be(0)
      changeSet.expiredLaneIds.size should be(1)
      changeSet.expiredLaneIds should be(Set(lane21Id))
    }
  }

  test("Lane Change: Show 2 Add"){
    runWithRollback {
      val newLane11 = NewIncomeLane(0, 0, 100, 745, false, false, lanePropertiesValues11)
      val newLane21 = newLane11.copy(properties = lanePropertiesValues21)
      val dateAtThisMoment = DateTime.now()

      ServiceWithDao.create(Seq(newLane11), Set(100L), 2, usernameTest)
      ServiceWithDao.create(Seq(newLane21), Set(100L), 2, usernameTest)

      when(mockRoadLinkService.getRoadLinksByLinkIdsFromVVH(Set(100L), false)).thenReturn(
        Seq(RoadLink(100L, Seq(Point(0.0, 0.0), Point(100.0, 0.0)), 100, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(745))))
      )

      val lanesChanged = ServiceWithDao.getChanged(dateAtThisMoment.minusDays(1), dateAtThisMoment.plusDays(1))

      lanesChanged.map(_.changeType) should be(Seq(LaneChangeType.Add, LaneChangeType.Add))
    }
  }

  test("Lane Change: Show 1 Add and 1 attribute changed"){
    runWithRollback {
      val newLane11 = NewIncomeLane(0, 0, 100, 745, false, false, lanePropertiesValues11)
      val dateAtThisMoment = DateTime.now()

      val newLanePropertiesValues11 = Seq( LaneProperty("lane_code", Seq(LanePropertyValue(11))),
        LaneProperty("lane_type", Seq(LanePropertyValue("4")))
      )

      val lane11Id = ServiceWithDao.create(Seq(newLane11), Set(100L), 2, usernameTest).head
      ServiceWithDao.update(Seq(newLane11.copy(id = lane11Id, properties = newLanePropertiesValues11)), Set(100L), 1, usernameTest)

      when(mockRoadLinkService.getRoadLinksByLinkIdsFromVVH(Set(100L), false)).thenReturn(
        Seq(RoadLink(100L, Seq(Point(0.0, 0.0), Point(100.0, 0.0)), 100, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(745))))
      )

      val lanesChanged = ServiceWithDao.getChanged(dateAtThisMoment.minusDays(1), dateAtThisMoment.plusDays(1))

      lanesChanged.map(_.changeType).sortBy(_.value) should be(Seq(LaneChangeType.Add, LaneChangeType.AttributesChanged))
      lanesChanged.map(_.lane.id) should be(Seq(lane11Id, lane11Id))
    }
  }

  test("Lane Change: Show 2 Add and 1 expire"){
    runWithRollback {
      val newLane11 = NewIncomeLane(0, 0, 100, 745, false, false, lanePropertiesValues11)
      val newLane12 = newLane11.copy(properties = lanePropertiesValues12)
      val dateAtThisMoment = DateTime.now()

      ServiceWithDao.create(Seq(newLane11), Set(100L), 2, usernameTest)
      val lane12Id = ServiceWithDao.create(Seq(newLane12), Set(100L), 2, usernameTest).head

      ServiceWithDao.deleteMultipleLanes(Set(12), Set(100L), usernameTest)

      when(mockRoadLinkService.getRoadLinksByLinkIdsFromVVH(Set(100L), false)).thenReturn(
        Seq(RoadLink(100L, Seq(Point(0.0, 0.0), Point(100.0, 0.0)), 100, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(745))))
      )

      val lanesChanged = ServiceWithDao.getChanged(dateAtThisMoment.minusDays(1), dateAtThisMoment.plusDays(1))

      lanesChanged.map(_.changeType).sortBy(_.value) should be(Seq(LaneChangeType.Add, LaneChangeType.Add, LaneChangeType.Expired))
      lanesChanged.filter(_.changeType == LaneChangeType.Expired).map(_.lane.id) should be(Seq(lane12Id))
    }
  }

  test("Lane Change: Show 2 Add, 1 lane code change"){
    runWithRollback {
      val newLane12 = NewIncomeLane(0, 0, 100, 745, false, false, lanePropertiesValues12)
      val newLane14 = newLane12.copy(properties = lanePropertiesValues14)
      val dateAtThisMoment = DateTime.now()

      ServiceWithDao.create(Seq(newLane12), Set(100L), 2, usernameTest)
      val lane14Id = ServiceWithDao.create(Seq(newLane14), Set(100L), 2, usernameTest).head

      val newLanePropertiesValuesOld14 = Seq(LaneProperty("lane_code", Seq(LanePropertyValue(12))),
        LaneProperty("lane_type", Seq(LanePropertyValue("3")))
      )

      ServiceWithDao.update(Seq(newLane14.copy(id = lane14Id, properties = newLanePropertiesValuesOld14)), Set(100L), 1, usernameTest)

      when(mockRoadLinkService.getRoadLinksByLinkIdsFromVVH(Set(100L), false)).thenReturn(
        Seq(RoadLink(100L, Seq(Point(0.0, 0.0), Point(100.0, 0.0)), 100, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(745))))
      )

      val lanesChanged = ServiceWithDao.getChanged(dateAtThisMoment.minusDays(1), dateAtThisMoment.plusDays(1))

      lanesChanged.map(_.changeType).sortBy(_.value) should be(Seq(LaneChangeType.Add, LaneChangeType.Add, LaneChangeType.LaneCodeTransfer))
      val laneCodeChanged = lanesChanged.filter(_.changeType == LaneChangeType.LaneCodeTransfer).head
      laneCodeChanged.lane.id should be(lane14Id)
      laneCodeChanged.lane.laneCode should be(12)
      laneCodeChanged.oldLane.get.laneCode should be(14)
    }
  }

  test("Lane Change: Show 2 Add and 1 shortened lane"){
    runWithRollback {
      val newLane11 = NewIncomeLane(0, 0, 100, 745, false, false, lanePropertiesValues11)
      val newLane12 = newLane11.copy(properties = lanePropertiesValues12)
      val dateAtThisMoment = DateTime.now()

      ServiceWithDao.create(Seq(newLane11), Set(100L), 2, usernameTest)
      val lane12Id = ServiceWithDao.create(Seq(newLane12), Set(100L), 2, usernameTest).head

      val subLane12Split = NewIncomeLane(0, 0, 50, 745, false, false, lanePropertiesValues12)
      ServiceWithDao.update(Seq(subLane12Split), Set(100L), 2, usernameTest)

      when(mockRoadLinkService.getRoadLinksByLinkIdsFromVVH(Set(100L), false)).thenReturn(
        Seq(RoadLink(100L, Seq(Point(0.0, 0.0), Point(100.0, 0.0)), 100, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(745))))
      )

      val lanesChanged = ServiceWithDao.getChanged(dateAtThisMoment.minusDays(1), dateAtThisMoment.plusDays(1))

      lanesChanged.map(_.changeType).sortBy(_.value) should be(Seq(LaneChangeType.Add, LaneChangeType.Add, LaneChangeType.Shortened))
      val shortenedLane = lanesChanged.filter(_.changeType == LaneChangeType.Shortened).head
      shortenedLane.lane.endMeasure should be(50)
      shortenedLane.oldLane.get.endMeasure should be(100)
      shortenedLane.oldLane.get.id should be(lane12Id)
    }
  }

  test("Lane Change: Get only the 2 Add with token"){
    //token = pageNumber:1,recordNumber:2
    runWithRollback {
      val newLane11 = NewIncomeLane(0, 0, 100, 745, false, false, lanePropertiesValues11)
      val newLane12 = newLane11.copy(properties = lanePropertiesValues12)
      val dateAtThisMoment = DateTime.now()

      ServiceWithDao.create(Seq(newLane11), Set(100L), 2, usernameTest)
      ServiceWithDao.create(Seq(newLane12), Set(100L), 2, usernameTest)

      val subLane12Split = NewIncomeLane(0, 0, 50, 745, false, false, lanePropertiesValues12)
      val subLane12Id = ServiceWithDao.update(Seq(subLane12Split), Set(100L), 2, usernameTest).head

      when(mockRoadLinkService.getRoadLinksByLinkIdsFromVVH(Set(100L), false)).thenReturn(
        Seq(RoadLink(100L, Seq(Point(0.0, 0.0), Point(100.0, 0.0)), 100, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(745))))
      )

      val lanesChanged = ServiceWithDao.getChanged(dateAtThisMoment.minusDays(1), dateAtThisMoment.plusDays(1), token = Some("cGFnZU51bWJlcjoxLHJlY29yZE51bWJlcjoy"))

      lanesChanged.map(_.changeType).sortBy(_.value) should be(Seq(LaneChangeType.Add, LaneChangeType.Add))
      lanesChanged.map(_.lane.id).contains(subLane12Id) should be(false)
    }
  }

  test("Lane Change:Show 1 Add and 2 Divided") {
    //2 divides because two new lanes with same old lane
    runWithRollback {
      val newLane12 = NewIncomeLane(0, 0, 500, 745, false, false, lanePropertiesValues12)
      val lane12SplitA = NewIncomeLane(0, 0, 250, 745, false, false, lanePropertiesValues12)
      val lane12SplitB = NewIncomeLane(0, 250, 500, 745, false, false, lanePropertiesValues12)

      val dateAtThisMoment = DateTime.now()
      val lane12Id = ServiceWithDao.create(Seq(newLane12), Set(100L), 2, usernameTest).head

      ServiceWithDao.createMultiLanesOnLink(Seq(lane12SplitA,lane12SplitB), Set(100L), 2, usernameTest)

      when(mockRoadLinkService.getRoadLinksByLinkIdsFromVVH(Set(100L), false)).thenReturn(
        Seq(RoadLink(100L, Seq(Point(0.0, 0.0), Point(100.0, 0.0)), 100, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(745))))
      )

      val lanesChanged = ServiceWithDao.getChanged(dateAtThisMoment.minusDays(1), dateAtThisMoment.plusDays(1))

      lanesChanged.map(_.changeType).sortBy(_.value) should be(Seq(LaneChangeType.Add, LaneChangeType.Divided, LaneChangeType.Divided))
      val lanesDivides = lanesChanged.filter(_.changeType == LaneChangeType.Divided)
      lanesDivides.map(_.oldLane.get.id) should be(Seq(lane12Id, lane12Id))
    }
  }
}
