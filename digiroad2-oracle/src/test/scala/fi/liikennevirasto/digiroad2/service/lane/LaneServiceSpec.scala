package fi.liikennevirasto.digiroad2.service.lane

import fi.liikennevirasto.digiroad2.asset.SideCode.TowardsDigitizing
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.VKMClient
import fi.liikennevirasto.digiroad2.dao.MunicipalityDao
import fi.liikennevirasto.digiroad2.dao.lane.{LaneDao, LaneHistoryDao}
import fi.liikennevirasto.digiroad2.lane._
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.{RoadAddressService, RoadLinkService}
import fi.liikennevirasto.digiroad2.util.LaneUtils.persistedLanesTwoDigitLaneCode
import fi.liikennevirasto.digiroad2.util.{LinkIdGenerator, PolygonTools, TestTransactions}
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, Point}
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class LaneTestSupporter extends FunSuite with Matchers {
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockPolygonTools = MockitoSugar.mock[PolygonTools]
  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  val mockMunicipalityDao = MockitoSugar.mock[MunicipalityDao]
  val mockLaneDao = MockitoSugar.mock[LaneDao]
  val mockLaneHistoryDao = MockitoSugar.mock[LaneHistoryDao]
  val mockVKMClient = MockitoSugar.mock[VKMClient]
  val mockRoadAddressService = MockitoSugar.mock[RoadAddressService]
  val mockLaneService = MockitoSugar.mock[LaneService]

  val laneDao = new LaneDao()
  val laneHistoryDao = new LaneHistoryDao()
  val linkId: String = LinkIdGenerator.generateRandom()
  val roadLinkWithLinkSource = RoadLink(
    linkId, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
    1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "SURFACETYPE" -> BigInt(2)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)


  when(mockRoadLinkService.getRoadLinkByLinkId(any[String], any[Boolean])).thenReturn(Some(roadLinkWithLinkSource))


  val lanePropertiesValues1 = Seq( LaneProperty("lane_code", Seq(LanePropertyValue(1))),
                                LaneProperty("lane_type", Seq(LanePropertyValue("1")))
                              )


  val lanePropertiesValues2 = Seq( LaneProperty("lane_code", Seq(LanePropertyValue(2))),
                                LaneProperty("lane_type", Seq(LanePropertyValue("2"))),
                                LaneProperty("start_date", Seq(LanePropertyValue(DateTime.now().toString("dd.MM.yyyy")))),
                                LaneProperty("end_date", Seq(LanePropertyValue(DateTime.now().plusDays(10).toString("dd.MM.yyyy"))))
                              )

  val lanePropertiesValues4 = Seq(LaneProperty("lane_code", Seq(LanePropertyValue(4))),
                                   LaneProperty("lane_type", Seq(LanePropertyValue("3"))),
                                   LaneProperty("start_date", Seq(LanePropertyValue(DateTime.now().toString("dd.MM.yyyy"))))
                                  )

  val lanePropertiesPassedEndDate = Seq(LaneProperty("lane_code", Seq(LanePropertyValue(2))),
    LaneProperty("lane_type", Seq(LanePropertyValue("3"))),
    LaneProperty("start_date", Seq(LanePropertyValue(DateTime.now().minusYears(1).toString("dd.MM.yyyy")))),
    LaneProperty("end_date", Seq(LanePropertyValue(DateTime.now().minusMonths(1).toString("dd.MM.yyyy"))))
  )

  val (linkId1, linkId2) = (LinkIdGenerator.generateRandom(), LinkIdGenerator.generateRandom())
  val roadlink1: RoadLink = RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(100.0, 0.0)), 100, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map(
    "MUNICIPALITYCODE" -> BigInt(745),
    "ROADNUMBER" -> 100L,
    "ROADNAME_FI" -> "Testitie",
    "ROAD_PART_NUMBER" -> 7L,
    "ROAD_NUMBER" -> 100L,
    "END_ADDR" -> 2000L,
    "SIDECODE" -> TowardsDigitizing.value
  ))

  val roadlink2: RoadLink = RoadLink(linkId2, Seq(Point(0.0, 0.0), Point(100.0, 0.0)), 100, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map())




  object PassThroughLaneService extends LaneOperations {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: LaneDao = mockLaneDao
    override def historyDao: LaneHistoryDao = mockLaneHistoryDao
    override def eventBus: DigiroadEventBus = mockEventBus
    override def polygonTools: PolygonTools = mockPolygonTools
    override def municipalityDao: MunicipalityDao = mockMunicipalityDao
    override def vkmClient: VKMClient = mockVKMClient
    override def roadAddressService: RoadAddressService = mockRoadAddressService

  }

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback(PostGISDatabase.ds)(test)
}


class LaneServiceSpec extends LaneTestSupporter {

  object ServiceWithDao extends LaneService(mockRoadLinkService, mockEventBus, mockRoadAddressService) {

    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: LaneDao = laneDao
    override def historyDao: LaneHistoryDao = laneHistoryDao
    override def municipalityDao: MunicipalityDao = mockMunicipalityDao
    override def eventBus: DigiroadEventBus = mockEventBus
    override def polygonTools: PolygonTools = mockPolygonTools
    override def vkmClient: VKMClient = mockVKMClient
  }

  val usernameTest = "testuser"

  test("Create new lane") {
    runWithRollback {

      val newLane = ServiceWithDao.create(Seq(NewLane(0, 0, 500, 745, false, false, lanePropertiesValues1)), Set(linkId1), 1, usernameTest)
      newLane.length should be(1)

      val lane = laneDao.fetchLanesByIds( Set(newLane.head)).head
      lane.expired should be (false)
      lane.createdBy.get should be(usernameTest)
      lane.createdDateTime.get.getDayOfYear should be(DateTime.now().getDayOfYear)
      Math.abs(DateTime.now().getSecondOfDay - lane.createdDateTime.get.getSecondOfDay) should be < 120
      lane.modifiedBy should be(None)
      lane.modifiedDateTime should be(None)

      lane.attributes.foreach{ laneProp =>
        val attr = lanePropertiesValues1.find(_.publicId == laneProp.publicId)

        attr should not be (None)
        attr.head.values.head.value should be  (laneProp.values.head.value)
      }
    }
  }

  test("Create multiple lanes") {
    runWithRollback {
      val newLane1 = PersistedLane(0, linkId1, SideCode.TowardsDigitizing.value, 1, 0, 0, 100, None, None, None, None, None, None, expired = false, 0L, None, lanePropertiesValues1)
      val newLane2 = PersistedLane(0, linkId1, SideCode.AgainstDigitizing.value, 1, 0, 0, 100, None, None, None, None, None, None, expired = false, 0L, None, lanePropertiesValues1)
      val newLane3 = PersistedLane(0, linkId2, SideCode.AgainstDigitizing.value, 1, 0, 0, 100, None, None, None, None, None, None, expired = false, 0L, None, lanePropertiesValues1)
      val createdLanes = ServiceWithDao.createMultipleLanes(Seq(newLane1, newLane2, newLane3), usernameTest)
      createdLanes.length should be(3)

      val lanes = laneDao.fetchLanesByIds(createdLanes.map(_.id).toSet)
      lanes.length should be(3)

      val lane1 = lanes.head
      lane1.expired should be (false)
      lane1.attributes.foreach{ laneProp =>
        val attr = lanePropertiesValues1.find(_.publicId == laneProp.publicId)

        attr should not be None
        attr.head.values.head.value should be (laneProp.values.head.value)
      }
    }
  }

  test("Fetch existing main lanes by linkId"){
    runWithRollback {
      val newLane11 = ServiceWithDao.create(Seq(NewLane(0, 0, 500, 745, false, false, lanePropertiesValues1)), Set(linkId1), 1, usernameTest)
      newLane11.length should be(1)

      val newLane12 = ServiceWithDao.create(Seq(NewLane(0, 0, 500, 745, false, false, lanePropertiesValues2)), Set(linkId1), 1, usernameTest)
      newLane12.length should be(1)

      val newLane21 = ServiceWithDao.create(Seq(NewLane(0, 0, 500, 745, false, false, lanePropertiesValues1)), Set(linkId1), 2, usernameTest)
      newLane21.length should be(1)

      val mockRoadLink = RoadLink(linkId1, Seq(), 1000, State, 2, TrafficDirection.AgainstDigitizing,
        EnclosedTrafficArea, None, None)

      val existingLanes = ServiceWithDao.fetchExistingMainLanesByRoadLinks(Seq(mockRoadLink), Seq())
      existingLanes.length should be(2)
    }
  }

  test("Fetch existing Lanes by linksId"){
    runWithRollback {

      val newLane11 = ServiceWithDao.create(Seq(NewLane(0, 0, 500, 745, false, false, lanePropertiesValues1)), Set(linkId1), 1, usernameTest)
      newLane11.length should be(1)

      val newLane21 = ServiceWithDao.create(Seq(NewLane(0, 0, 500, 745, false, false, lanePropertiesValues1)), Set(linkId1), 2, usernameTest)
      newLane21.length should be(1)

      val newLane22 = ServiceWithDao.create(Seq(NewLane(0, 0, 500, 745, false, false, lanePropertiesValues2)), Set(linkId1), 2, usernameTest)
      newLane22.length should be(1)

      val existingLanes = ServiceWithDao.fetchExistingLanesByLinksIdAndSideCode(linkId1, 1)
      existingLanes.length should be(1)

      val existingLanesOtherSide = ServiceWithDao.fetchExistingLanesByLinksIdAndSideCode(linkId1, 2)
      existingLanesOtherSide.length should be(2)

    }
  }

  test("Update Lane Information") {
    runWithRollback {

      val updateValues1 = Seq( LaneProperty("lane_code", Seq(LanePropertyValue(1))),
                            LaneProperty("lane_type", Seq(LanePropertyValue("5")))
                          )


      val newLane1 = ServiceWithDao.create(Seq(NewLane(0, 0, 500, 745, false, false, lanePropertiesValues1)), Set(linkId1), 1, usernameTest)
      newLane1.length should be(1)

      val allExistingLanes = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(linkId1))
      val updatedLane = ServiceWithDao.update(Seq(NewLane(newLane1.head, 0, 500, 745, false, false, updateValues1)), Set(linkId1), 1, usernameTest, Seq(SideCodesForLinkIds(linkId1, 1)), allExistingLanes)
      updatedLane.length should be(1)

      //Verify the presence one line with old data before the update on histories tables
      val historyLanes = laneHistoryDao.fetchHistoryLanesByLinkIdsAndLaneCode(Seq(linkId1), Seq(1), true)
      historyLanes.size should be(1)

      val oldLaneData = historyLanes.filter(_.oldId == newLane1.head).head
      oldLaneData.oldId should be(newLane1.head)
      oldLaneData.expired should be(false)
      oldLaneData.attributes.foreach { laneProp =>
        lanePropertiesValues1.contains(laneProp) should be(true)
      }
    }
  }

  test("Remove unused attributes from database") {
    runWithRollback {
      val lanePropertiesWithDate = lanePropertiesValues2
      val lanePropertiesWithEmptyDate = lanePropertiesValues2 ++ Seq( LaneProperty("start_date", Seq()), LaneProperty("end_date", Seq()) )

      val newLaneId = ServiceWithDao.create(Seq(NewLane(0, 0, 500, 745, false, false, lanePropertiesWithDate)), Set(linkId1), 1, usernameTest)
      val createdLane = ServiceWithDao.getPersistedLanesByIds(newLaneId.toSet)
      createdLane.head.attributes.length should be(4)

      val updatedLaneId = ServiceWithDao.update(Seq(NewLane(newLaneId.head, 0, 500, 745, false, false, lanePropertiesWithEmptyDate)), Set(linkId1), 1, usernameTest, Seq(SideCodesForLinkIds(linkId1, 1)), createdLane)
      val updatedLane = ServiceWithDao.getPersistedLanesByIds(updatedLaneId.toSet)
      updatedLane.head.attributes.length should be(2)
    }
  }

  test("Expire a sub lane") {
    runWithRollback {
      //NewIncomeLanes to create
      val mainLane1ToAdd = Seq(NewLane(0, 0, 500, 745, false, false, lanePropertiesValues1))
      val subLane2ToAdd = Seq(NewLane(0, 0, 500, 745, false, false, lanePropertiesValues2))

      //Create initial lanes
      val newMainLaneid = ServiceWithDao.create(mainLane1ToAdd, Set(linkId1), 1, usernameTest).head
      val newSubLaneId = ServiceWithDao.create(subLane2ToAdd, Set(linkId1), 1, usernameTest).head

      val sideCodeForLink100 = SideCodesForLinkIds(linkId1, 1)
      val sideCodesForLinkIds = Seq(sideCodeForLink100)

      //Validate if initial lanes are correctly created
      val currentLanes = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(linkId1), Seq(1, 2), false)
      currentLanes.size should be(2)

      val lane11 = currentLanes.filter(_.id == newMainLaneid).head
      lane11.id should be(newMainLaneid)
      lane11.attributes.foreach{ laneProp =>
        lanePropertiesValues1.contains(laneProp) should be(true)
      }

      val lane12 = currentLanes.filter(_.id == newSubLaneId).head
      lane12.id should be(newSubLaneId)
      lane12.attributes.foreach{ laneProp =>
        lanePropertiesValues2.contains(laneProp) should be(true)
      }


      //Delete sublane 12 and verify the movement to history tables
      val subLane12ToExpire = Seq(NewLane(newSubLaneId, 0, 500, 745, true, false, lanePropertiesValues2))

      //Verify if the lane to delete was totally deleted from lane table
      val currentMainLane11 = Seq(mainLane1ToAdd.head.copy(id = newMainLaneid))
      ServiceWithDao.processNewLanes((currentMainLane11 ++ subLane12ToExpire).toSet, Set(linkId1), 1, usernameTest, sideCodesForLinkIds)
      val currentLanesAfterDelete = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(linkId1), Seq(1, 2), false)
      currentLanesAfterDelete.size should be(1)

      val lane11AfterDelete = currentLanesAfterDelete.filter(_.id == newMainLaneid).head
      lane11AfterDelete.id should be(newMainLaneid)
      lane11AfterDelete.attributes.foreach{ laneProp =>
        lanePropertiesValues1.contains(laneProp) should be(true)
      }

      //Verify the presence of the deleted lane on histories tables
      val historyLanes = laneHistoryDao.fetchHistoryLanesByLinkIdsAndLaneCode(Seq(linkId1), Seq(1, 2), true)
      historyLanes.size should be(1)

      val lane12AfterDelete = historyLanes.filter(_.oldId == newSubLaneId).head
      lane12AfterDelete.oldId should be(newSubLaneId)
      lane12AfterDelete.expired should be(true)
      lane12AfterDelete.attributes.foreach{ laneProp =>
        lanePropertiesValues2.contains(laneProp) should be(true)
      }
    }
  }

  test("Split current lane asset. Split lane in two but only keep one part") {
    runWithRollback {
      val mainLane1 = NewLane(0, 0, 500, 745, false, false, lanePropertiesValues1)
      val subLane2 = NewLane(0, 0, 500, 745, false, false, lanePropertiesValues2)
      val subLane2Splited = NewLane(0, 0, 250, 745, false, false, lanePropertiesValues2)

      //create lanes 11, 12
      val mainLane1Id = ServiceWithDao.create(Seq(mainLane1), Set(linkId1), 1, usernameTest).head
      val newSubLane2Id = ServiceWithDao.create(Seq(subLane2), Set(linkId1), 1, usernameTest).head

      val sideCodeForLink100 = SideCodesForLinkIds(linkId1, 1)
      val sideCodesForLinkIds = Seq(sideCodeForLink100)

      //Validate if initial lanes are correctly created
      val currentLanes = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(linkId1), Seq(1, 2), false)
      currentLanes.size should be(2)

      val lane1 = currentLanes.filter(_.id == mainLane1Id).head
      lane1.id should be(mainLane1Id)
      lane1.attributes.foreach { laneProp =>
        lanePropertiesValues1.contains(laneProp) should be(true)
      }

      val lane12 = currentLanes.filter(_.id == newSubLane2Id).head
      lane12.id should be(newSubLane2Id)
      lane12.startMeasure should be(0)
      lane12.endMeasure should be(500)
      lane12.attributes.foreach { laneProp =>
        lanePropertiesValues2.contains(laneProp) should be(true)
      }


      //Simulation of sending a main lane, and one sublane splited and stored only one part
      val currentMainLane11 = mainLane1.copy(id = mainLane1Id)
      ServiceWithDao.processNewLanes(Set(currentMainLane11, subLane2Splited), Set(linkId1), 1, usernameTest, sideCodesForLinkIds)

      val lanesAfterSplit = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(linkId1), Seq(1, 2), true)
      lanesAfterSplit.size should be(2)

      val lane11AfterSplit = lanesAfterSplit.filter(_.id == mainLane1Id).head
      lane11AfterSplit.id should be(mainLane1Id)
      lane11AfterSplit.attributes.foreach { laneProp =>
        lanePropertiesValues1.contains(laneProp) should be(true)
      }

      val lane12AfterSplit = lanesAfterSplit.filter(_.laneCode == 2).head
      lane12AfterSplit.id should not be newSubLane2Id
      lane12AfterSplit.startMeasure should be(0)
      lane12AfterSplit.endMeasure should be(250)
      lane12AfterSplit.attributes.foreach { laneProp =>
        lanePropertiesValues2.contains(laneProp) should be(true)
      }


      //Verify the presence of the old splitted lane on histories tables
      val historyLanes = laneHistoryDao.fetchHistoryLanesByLinkIdsAndLaneCode(Seq(linkId1), Seq(1, 2), true)
      historyLanes.size should be(1)

      val historyLane12 = historyLanes.filter(_.oldId == newSubLane2Id).head
      historyLane12.newId should not be 0
      historyLane12.newId should not be mainLane1Id
      historyLane12.oldId should be(newSubLane2Id)
      historyLane12.startMeasure should be(0)
      historyLane12.endMeasure should be(500)
      historyLane12.expired should be(true)
      historyLane12.attributes.foreach { laneProp =>
        lanePropertiesValues2.contains(laneProp) should be(true)

      }
    }
  }

  test("Create shorter lane in comparation with linkId usign cuting tool") {
    runWithRollback {
      val mainLane1 = NewLane(0, 0, 500, 745, false, false, lanePropertiesValues1)
      val subLane2Splited = NewLane(0, 0, 250, 745, false, false, lanePropertiesValues2)

      val mainLane1Id = ServiceWithDao.create(Seq(mainLane1), Set(linkId1), 1, usernameTest).head
      val subLane2SplitedId = ServiceWithDao.create(Seq(subLane2Splited), Set(linkId1), 1, usernameTest).head

      //Validate if initial lanes are correctly created
      val currentLanes = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(linkId1), Seq(1, 2), false)
      currentLanes.size should be(2)

      val lane1 = currentLanes.filter(_.id == mainLane1Id).head
      lane1.id should be(mainLane1Id)
      lane1.attributes.foreach { laneProp =>
        lanePropertiesValues1.contains(laneProp) should be(true)
      }

      val lane2 = currentLanes.filter(_.id == subLane2SplitedId).head
      lane2.id should be(subLane2SplitedId)
      lane2.startMeasure should be(0)
      lane2.endMeasure should be(250.0)
      lane2.attributes.foreach { laneProp =>
        lanePropertiesValues2.contains(laneProp) should be(true)
      }
    }
  }

  test("Create new lane splitted since the beigining, keeping two parts") {
    runWithRollback {
      val mainLane1 = NewLane(0, 0, 500, 745, false, false, lanePropertiesValues1)
      val subLane2SplitedA = NewLane(0, 0, 250, 745, false, false, lanePropertiesValues2)
      val subLane2SplitedB = NewLane(0, 250, 500, 745, false, false, lanePropertiesValues2)

      val mainLane1Id = ServiceWithDao.create(Seq(mainLane1), Set(linkId1), 1, usernameTest).head
      val subLane2SplitedAId = ServiceWithDao.create(Seq(subLane2SplitedA), Set(linkId1), 1, usernameTest).head
      val subLane2SplitedBId = ServiceWithDao.create(Seq(subLane2SplitedB), Set(linkId1), 1, usernameTest).head

      //Validate if initial lanes are correctly created
      val currentLanes = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(linkId1), Seq(1, 2), false)
      currentLanes.size should be(3)

      val lane1 = currentLanes.filter(_.id == mainLane1Id).head
      lane1.id should be(mainLane1Id)
      lane1.attributes.foreach { laneProp =>
        lanePropertiesValues1.contains(laneProp) should be(true)
      }

      val lane2A = currentLanes.filter(_.id == subLane2SplitedAId).head
      lane2A.id should be(subLane2SplitedAId)
      lane2A.startMeasure should be(0)
      lane2A.endMeasure should be(250.0)
      lane2A.attributes.foreach { laneProp =>
        lanePropertiesValues2.contains(laneProp) should be(true)
      }

      val lane2B = currentLanes.filter(_.id == subLane2SplitedBId).head
      lane2B.id should be(subLane2SplitedBId)
      lane2B.startMeasure should be(250.0)
      lane2B.endMeasure should be(500.0)
      lane2B.attributes.foreach { laneProp =>
        lanePropertiesValues2.contains(laneProp) should be(true)
      }
    }
  }

  test("Split current lane asset. Expire lane and create two new lanes") {
    runWithRollback {
      val lanePropertiesSubLaneSplit2 = Seq(
        LaneProperty("lane_code", Seq(LanePropertyValue(2))),
        LaneProperty("lane_type", Seq(LanePropertyValue("5"))),
        LaneProperty("start_date", Seq(LanePropertyValue(DateTime.now().toString("dd.MM.yyyy"))))
      )

      val mainLane = NewLane(0, 0, 500, 745, false, false, lanePropertiesValues1)
      val subLane2 = NewLane(0, 0, 500, 745, false, false, lanePropertiesValues2)
      val subLane2SplitA = NewLane(0, 0, 250, 745, false, false, lanePropertiesValues2)
      val subLane2SplitB = NewLane(0, 250, 500, 745, false, false, lanePropertiesSubLaneSplit2)

      val mainLane1Id = ServiceWithDao.create(Seq(mainLane), Set(linkId1), 1, usernameTest).head
      val newSubLane2Id = ServiceWithDao.create(Seq(subLane2), Set(linkId1), 1, usernameTest).head

      val sideCodeForLink100 = SideCodesForLinkIds(linkId1, 1)
      val sideCodesForLinkIds = Seq(sideCodeForLink100)

      //Validate if initial lanes are correctly created
      val currentLanes = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(linkId1), Seq(1, 2), false)
      currentLanes.size should be(2)

      val lane1 = currentLanes.filter(_.id == mainLane1Id).head
      lane1.id should be(mainLane1Id)
      lane1.attributes.foreach { laneProp =>
        lanePropertiesValues1.contains(laneProp) should be(true)
      }

      val lane2 = currentLanes.filter(_.id == newSubLane2Id).head
      lane2.id should be(newSubLane2Id)
      lane2.startMeasure should be(0)
      lane2.endMeasure should be(500.0)
      lane2.attributes.foreach { laneProp =>
        lanePropertiesValues2.contains(laneProp) should be(true)
      }


      //Simulation of sending a main lane, and two sublanes splited and stored both
      val currentMainLane = mainLane.copy(id = mainLane1Id)
      ServiceWithDao.processNewLanes(Set(currentMainLane, subLane2SplitA, subLane2SplitB), Set(linkId1), 1, usernameTest, sideCodesForLinkIds)

      val lanesAfterSplit = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(linkId1), Seq(1, 2), true)
      lanesAfterSplit.size should be(3)

      val lane11AfterSplit = lanesAfterSplit.filter(_.id == mainLane1Id).head
      lane11AfterSplit.id should be(mainLane1Id)
      lane11AfterSplit.attributes.foreach { laneProp =>
        lanePropertiesValues1.contains(laneProp) should be(true)
      }

      lanesAfterSplit.count(_.laneCode == 2) should be(2)

      val lane12A = lanesAfterSplit.filter(l => l.startMeasure == 0 && l.laneCode == 2).head
      lane12A.startMeasure should be(0)
      lane12A.endMeasure should be(250.0)
      lane12A.attributes.foreach { laneProp =>
        lanePropertiesValues2.contains(laneProp) should be(true)
      }

      val lane2B = lanesAfterSplit.filter(l => l.startMeasure == 250.0).head
      val lanePropertiesSubLaneSplit2WithStartDate = lanePropertiesSubLaneSplit2
      lane2B.startMeasure should be(250.0)
      lane2B.endMeasure should be(500.0)
      lane2B.attributes.foreach { laneProp =>
        lanePropertiesSubLaneSplit2WithStartDate.contains(laneProp) should be(true)
      }


      //Verify the presence of the old splitted lanes on histories tables
      val historyLanes = laneHistoryDao.fetchHistoryLanesByLinkIdsAndLaneCode(Seq(linkId1), Seq(1, 2), true)
      historyLanes.size should be(2)

      historyLanes.filter(_.oldId == newSubLane2Id).foreach { historyLane2 =>
        historyLane2.newId should not be 0
        historyLane2.newId should not be mainLane1Id
        historyLane2.startMeasure should be(0)
        historyLane2.endMeasure should be(500)
        historyLane2.expired should be(true)
        historyLane2.attributes.foreach { laneProp =>
          lanePropertiesValues2.contains(laneProp) should be(true)
        }
      }

    }
  }

  test("Replace two old split lanes with new full length additional lane") {
    runWithRollback {
      val lanePropertiesSubLaneSplit2 = Seq(
        LaneProperty("lane_code", Seq(LanePropertyValue(2))),
        LaneProperty("lane_type", Seq(LanePropertyValue("5"))),
        LaneProperty("start_date", Seq(LanePropertyValue(DateTime.now().toString("dd.MM.yyyy")))),
        LaneProperty("end_date", Seq(LanePropertyValue(DateTime.now().plusDays(11).toString("dd.MM.yyyy"))))
      )

      val mainLane = NewLane(0, 0, 500, 745, false, false, lanePropertiesValues1)
      val subLane2 = NewLane(0, 0, 500, 745, false, false, lanePropertiesValues2)
      val subLane2SplitA = NewLane(0, 0, 250, 745, false, false, lanePropertiesValues2)
      val subLane2SplitB = NewLane(0, 250, 500, 745, false, false, lanePropertiesSubLaneSplit2)

      val mainLane1Id = ServiceWithDao.create(Seq(mainLane), Set(linkId1), 1, usernameTest).head
      val newSubLane2SplitAId = ServiceWithDao.create(Seq(subLane2SplitA), Set(linkId1), 1, usernameTest).head
      val newSubLane2SplitBId = ServiceWithDao.create(Seq(subLane2SplitB), Set(linkId1), 1, usernameTest).head

      val sideCodeForLink100 = SideCodesForLinkIds(linkId1, 1)
      val sideCodesForLinkIds = Seq(sideCodeForLink100)

      //Validate if initial lanes are correctly created
      val currentLanes = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(linkId1), Seq(1, 2), false)
      currentLanes.size should be(3)

      val lane1 = currentLanes.filter(_.id == mainLane1Id).head
      lane1.id should be(mainLane1Id)
      lane1.attributes.foreach { laneProp =>
        lanePropertiesValues1.contains(laneProp) should be(true)
      }

      val lane2A = currentLanes.filter(_.id == newSubLane2SplitAId).head
      lane2A.id should be(newSubLane2SplitAId)
      lane2A.startMeasure should be(0)
      lane2A.endMeasure should be(250.0)
      lane2A.attributes.foreach { laneProp =>
        lanePropertiesValues2.contains(laneProp) should be(true)
      }


      val lane2B = currentLanes.filter(_.id == newSubLane2SplitBId).head
      val lanePropertiesSubLaneSplit2WithStartDate = lanePropertiesSubLaneSplit2
      lane2B.id should be(newSubLane2SplitBId)
      lane2B.startMeasure should be(250.0)
      lane2B.endMeasure should be(500.0)
      lane2B.attributes.foreach { laneProp =>
        lanePropertiesSubLaneSplit2WithStartDate.contains(laneProp) should be(true)
      }


      //Simulation of sending a main lane, and one sublane not splitted
      val currentMainLane = mainLane.copy(id = mainLane1Id)
      val expiredSubLane2A = subLane2SplitA.copy(id = newSubLane2SplitAId, isExpired = true)
      val expiredSubLane2B = subLane2SplitB.copy(id = newSubLane2SplitBId, isExpired = true)

      ServiceWithDao.processNewLanes(Set(currentMainLane, subLane2, expiredSubLane2A, expiredSubLane2B ), Set(linkId1), 1, usernameTest, sideCodesForLinkIds)

      val lanesAfterSplit = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(linkId1), Seq(1, 2), true)
      lanesAfterSplit.size should be(2)

      val lane1AfterSplit = lanesAfterSplit.filter(_.id == mainLane1Id).head
      lane1AfterSplit.id should be(mainLane1Id)
      lane1AfterSplit.attributes.foreach { laneProp =>
        lanePropertiesValues1.contains(laneProp) should be(true)
      }

      val lane2AfterSplit = lanesAfterSplit.filter(_.laneCode == 2).head
      lane2AfterSplit.startMeasure should be(0)
      lane2AfterSplit.endMeasure should be(500)
      lane2AfterSplit.expired should be(false)
      lane2AfterSplit.attributes.foreach { laneProp =>
        lanePropertiesValues2.contains(laneProp) should be(true)
      }


      //Verify the presence of the old splitted lanes on histories tables
      val historyLanes = laneHistoryDao.fetchHistoryLanesByLinkIdsAndLaneCode(Seq(linkId1), Seq(1, 2), true)
      historyLanes.size should be(2)

      val historylane2A = historyLanes.filter(_.oldId == newSubLane2SplitAId).head
      historylane2A.newId should be (0)
      historylane2A.newId should not be mainLane1Id
      historylane2A.startMeasure should be(0)
      historylane2A.endMeasure should be(250.0)
      historylane2A.expired should be(true)
      historylane2A.attributes.foreach { laneProp =>
        lanePropertiesValues2.contains(laneProp) should be(true)
      }

      val historylane2B = historyLanes.filter(_.oldId == newSubLane2SplitBId).head
      historylane2B.newId should be (0)
      historylane2B.newId should not be mainLane1Id
      historylane2B.startMeasure should be(250.0)
      historylane2B.endMeasure should be(500.0)
      historylane2B.expired should be(true)
      historylane2B.attributes.foreach { laneProp =>
        lanePropertiesSubLaneSplit2.contains(laneProp) should be(true)
      }

    }
  }

  test("Update two lanes with same lane code in one roadlink with two sub lanes with same lane code") {
    runWithRollback {
      val modifiedLaneProperties1 = Seq(
        LaneProperty("lane_code", Seq(LanePropertyValue(2))),
        LaneProperty("lane_type", Seq(LanePropertyValue("6"))),
        LaneProperty("start_date", Seq(LanePropertyValue(DateTime.now().toString("dd.MM.yyyy")))),
        LaneProperty("end_date", Seq(LanePropertyValue(DateTime.now().plusDays(10).toString("dd.MM.yyyy"))))
      )

      val modifiedLaneProperties2 = Seq(
        LaneProperty("lane_code", Seq(LanePropertyValue(2))),
        LaneProperty("lane_type", Seq(LanePropertyValue("8"))),
        LaneProperty("start_date", Seq(LanePropertyValue(DateTime.now().toString("dd.MM.yyyy")))),
        LaneProperty("end_date", Seq(LanePropertyValue(DateTime.now().plusDays(10).toString("dd.MM.yyyy"))))
      )

      val modifiedLaneProperties1WithStartDate = modifiedLaneProperties1
      val modifiedLaneProperties2WithStartDate = modifiedLaneProperties2

      val mainLane = NewLane(0, 0, 500, 745, false, false, lanePropertiesValues1)
      val subLane2SplitA = NewLane(0, 0, 250, 745, false, false, lanePropertiesValues2)
      val subLane2SplitB = NewLane(0, 250, 500, 745, false, false, modifiedLaneProperties1)

      val mainLane1Id = ServiceWithDao.create(Seq(mainLane), Set(linkId1), 1, usernameTest).head
      val newSubLane2SplitAId = ServiceWithDao.create(Seq(subLane2SplitA), Set(linkId1), 1, usernameTest).head
      val newSubLane2SplitBId = ServiceWithDao.create(Seq(subLane2SplitB), Set(linkId1), 1, usernameTest).head

      val sideCodeForLink100 = SideCodesForLinkIds(linkId1, 1)
      val sideCodesForLinkIds = Seq(sideCodeForLink100)

      //Validate if initial lanes are correctly created
      val currentLanes = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(linkId1), Seq(1, 2), false)
      currentLanes.size should be(3)

      val lane1 = currentLanes.filter(_.id == mainLane1Id).head
      lane1.id should be(mainLane1Id)
      lane1.attributes.foreach { laneProp =>
        lanePropertiesValues1.contains(laneProp) should be(true)
      }

      val lane2A = currentLanes.filter(_.id == newSubLane2SplitAId).head
      lane2A.id should be(newSubLane2SplitAId)
      lane2A.startMeasure should be(0)
      lane2A.endMeasure should be(250.0)
      lane2A.attributes.foreach { laneProp =>
        lanePropertiesValues2.contains(laneProp) should be(true)
      }

      val lane2B = currentLanes.filter(_.id == newSubLane2SplitBId).head
      lane2B.id should be(newSubLane2SplitBId)
      lane2B.startMeasure should be(250.0)
      lane2B.endMeasure should be(500.0)
      lane2B.attributes.foreach { laneProp =>
        modifiedLaneProperties1WithStartDate.contains(laneProp) should be(true)
      }


      //Simulation of sending a main lane, and two modificated sublanes on same link
      val updatedSubLane2SplitA = NewLane(newSubLane2SplitAId, 0, 250, 745, false, false, modifiedLaneProperties1)
      val updatedSubLane2SplitB = NewLane(newSubLane2SplitBId, 250, 500, 745, false, false, modifiedLaneProperties2)
      val currentMainLane = mainLane.copy(id = mainLane1Id)
      ServiceWithDao.processNewLanes(Set(currentMainLane, updatedSubLane2SplitA, updatedSubLane2SplitB), Set(linkId1), 1, usernameTest, sideCodesForLinkIds)

      val lanesAfterSplit = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(linkId1), Seq(1, 2), true)
      lanesAfterSplit.size should be(3)

      val lane1AfterUpdate = lanesAfterSplit.filter(_.id == mainLane1Id).head
      lane1AfterUpdate.id should be(mainLane1Id)
      lane1AfterUpdate.attributes.foreach { laneProp =>
        lanePropertiesValues1.contains(laneProp) should be(true)
      }

      val laneA2AfterUpdate = lanesAfterSplit.filter(_.id == newSubLane2SplitAId).head
      laneA2AfterUpdate.id should be(newSubLane2SplitAId)
      laneA2AfterUpdate.startMeasure should be(0)
      laneA2AfterUpdate.endMeasure should be(250.0)
      laneA2AfterUpdate.expired should be(false)
      laneA2AfterUpdate.attributes.foreach { laneProp =>
        modifiedLaneProperties1WithStartDate.contains(laneProp) should be(true)
      }

      val laneB2AfterUpdate = lanesAfterSplit.filter(_.id == newSubLane2SplitBId).head
      laneB2AfterUpdate.id should be(newSubLane2SplitBId)
      laneB2AfterUpdate.startMeasure should be(250.0)
      laneB2AfterUpdate.endMeasure should be(500.0)
      laneB2AfterUpdate.expired should be(false)
      laneB2AfterUpdate.attributes.foreach { laneProp =>
        modifiedLaneProperties2WithStartDate.contains(laneProp) should be(true)
      }

      //Verify the presence of the old splitted lanes attributes on histories tables
      val historyLanes = laneHistoryDao.fetchHistoryLanesByLinkIdsAndLaneCode(Seq(linkId1), Seq(1, 2), true)
      historyLanes.size should be(2)

      val historylane2A = historyLanes.filter(_.oldId == newSubLane2SplitAId).head
      historylane2A.newId should be(0)
      historylane2A.startMeasure should be(0)
      historylane2A.endMeasure should be(250.0)
      historylane2A.expired should be(false)
      historylane2A.attributes.foreach { laneProp =>
        lanePropertiesValues2.contains(laneProp) should be(true)
      }

      val historylane2B = historyLanes.filter(_.oldId == newSubLane2SplitBId).head
      historylane2B.newId should be(0)
      historylane2B.startMeasure should be(250.0)
      historylane2B.endMeasure should be(500.0)
      historylane2B.expired should be(false)
      historylane2B.attributes.foreach { laneProp =>
        modifiedLaneProperties1.contains(laneProp) should be(true)
      }
    }
  }

  // Deleting inner lanes not supported currently in Lane Modelling tool at the moment
  // TODO fix inner lane deletion unit tests if / when feature is added to Lane Modelling tool
  ignore("Delete sub lane in middle of lanes") {
    runWithRollback {
      val lanePropertiesValues4To2 = Seq(LaneProperty("lane_code", Seq(LanePropertyValue(2))),
        LaneProperty("lane_type", Seq(LanePropertyValue("3"))),
        LaneProperty("start_date", Seq(LanePropertyValue(DateTime.now().toString("dd.MM.yyyy"))))
      )

      val mainLane = NewLane(0, 0, 500, 745, false, false, lanePropertiesValues1)
      val subLane2 = NewLane(0, 0, 500, 745, false, false, lanePropertiesValues2)
      val subLane4 = NewLane(0, 0, 500, 745, false, false, lanePropertiesValues4)

      val mainLaneId = ServiceWithDao.create(Seq(mainLane), Set(linkId1), 1, usernameTest).head
      val newSubLane2Id = ServiceWithDao.create(Seq(subLane2), Set(linkId1), 1, usernameTest).head
      val newSubLane4Id = ServiceWithDao.create(Seq(subLane4), Set(linkId1), 1, usernameTest).head

      val sideCodeForLink100 = SideCodesForLinkIds(linkId1, 1)
      val sideCodesForLinkIds = Seq(sideCodeForLink100)

      // Delete the lane 12 and update 14 to new 12
      val updatedSubLane4 = NewLane(newSubLane4Id, 0, 500, 745, false, false, lanePropertiesValues4To2)
      val currentMainLane = mainLane.copy(id = mainLaneId)
      ServiceWithDao.processNewLanes(Set(currentMainLane, updatedSubLane4), Set(linkId1), 1, usernameTest, sideCodesForLinkIds)


      //Validate the delete of old lane 12 and the movement of lane 14 to 12
      val lanes = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(linkId1), Seq(1, 2, 4), true)
      lanes.length should be(2)
      lanes.find(_.id == newSubLane2Id) should be(None)
      lanes.find(_.laneCode == 4) should be(None)

      val newSubLane2 = lanes.find(_.id == newSubLane4Id).head
      newSubLane2.attributes.foreach { laneProp =>
        updatedSubLane4.properties.contains(laneProp) should be(true)
      }
      newSubLane2.startMeasure should be(0.0)
      newSubLane2.endMeasure should be(500.0)


      //Confirm the expired lane 12 and old info of lane 14 (Now 12) in history table
      val historyLanes = laneHistoryDao.fetchHistoryLanesByLinkIdsAndLaneCode(Seq(linkId1), Seq(1, 2, 4), true)
      historyLanes.length should be(2)

      val deletedSubLane2 = historyLanes.find(_.oldId == newSubLane2Id).head
      deletedSubLane2.newId should be(newSubLane4Id)
      deletedSubLane2.attributes.foreach { laneProp =>
        lanePropertiesValues2.contains(laneProp) should be(true)
      }
      deletedSubLane2.startMeasure should be(0.0)
      deletedSubLane2.endMeasure should be(500.0)
      deletedSubLane2.expired should be(true)

      val oldDataSubLane4 = historyLanes.find(_.oldId == newSubLane4Id).head
      oldDataSubLane4.newId should be(0)
      oldDataSubLane4.attributes.foreach { laneProp =>
        lanePropertiesValues4.contains(laneProp) should be(true)
      }
      oldDataSubLane4.expired should be(false)
      oldDataSubLane4.startMeasure should be(0.0)
      oldDataSubLane4.endMeasure should be(500.0)
    }
  }

  test("Expire a sub lane and then create another at same lane code with same properties") {
    runWithRollback {
      //NewIncomeLanes to create
      val mainLane1ToAdd = Seq(NewLane(0, 0, 500, 745, false, false, lanePropertiesValues1))
      val subLane2ToAdd = Seq(NewLane(0, 0, 500, 745, false, false, lanePropertiesValues2))

      //Create initial lanes
      val newMainLaneId = ServiceWithDao.create(mainLane1ToAdd, Set(linkId1), 1, usernameTest).head
      val newSubLaneId = ServiceWithDao.create(subLane2ToAdd, Set(linkId1), 1, usernameTest).head

      val sideCodeForLink100 = SideCodesForLinkIds(linkId1, 1)
      val sideCodesForLinkIds = Seq(sideCodeForLink100)

      //Validate if initial lanes are correctly created
      val currentLanes = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(linkId1), Seq(1, 2), false)
      currentLanes.size should be(2)

      val lane1 = currentLanes.filter(_.id == newMainLaneId).head
      lane1.attributes.foreach{ laneProp =>
        lanePropertiesValues1.contains(laneProp) should be(true)
      }

      val lane2 = currentLanes.filter(_.id == newSubLaneId).head
      lane2.attributes.foreach{ laneProp =>
        lanePropertiesValues2.contains(laneProp) should be(true)
      }


      //Create another sub lane 12
      val newSubLane2WithSameProperties = Seq(NewLane(0, 0, 500, 745, false, false, lanePropertiesValues2))

      //Verify if the lane to delete was totally deleted from lane table
      val currentMainLane1 = Seq(mainLane1ToAdd.head.copy(id = newMainLaneId))
      ServiceWithDao.processNewLanes((currentMainLane1 ++ newSubLane2WithSameProperties).toSet, Set(linkId1), 1, usernameTest, sideCodesForLinkIds)
      val currentLanesAfterProcess = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(linkId1), Seq(1, 2), false)
      currentLanesAfterProcess.size should be(2)

      val lane1After = currentLanesAfterProcess.filter(_.id == newMainLaneId).head
      lane1After.attributes.foreach{ laneProp =>
        lanePropertiesValues1.contains(laneProp) should be(true)
      }

      val lane2After = currentLanesAfterProcess.filter(_.id == newSubLaneId).head
      lane2After.attributes.foreach{ laneProp =>
        lanePropertiesValues2.contains(laneProp) should be(true)
      }

      //Nothing changed so nothing will be in history
      val historyLanes = laneHistoryDao.fetchHistoryLanesByLinkIdsAndLaneCode(Seq(linkId1), Seq(1, 2), true)
      historyLanes.size should be(0)
    }
  }

  test("Expire a sub lane and then create another at same lane code with different properties") {
    runWithRollback {
      val newLanePropertiesValues2 = Seq( LaneProperty("lane_code", Seq(LanePropertyValue(2))),
        LaneProperty("lane_type", Seq(LanePropertyValue("3"))),
        LaneProperty("start_date", Seq(LanePropertyValue(DateTime.now().toString("dd.MM.yyyy")))),
        LaneProperty("end_date", Seq(LanePropertyValue(DateTime.now().plusDays(10).toString("dd.MM.yyyy"))))
      )

      //NewIncomeLanes to create
      val mainLane1ToAdd = Seq(NewLane(0, 0, 500, 745, false, false, lanePropertiesValues1))
      val subLane2ToAdd = Seq(NewLane(0, 0, 500, 745, false, false, lanePropertiesValues2))

      //Create initial lanes
      val newMainLaneId = ServiceWithDao.create(mainLane1ToAdd, Set(linkId1), 1, usernameTest).head
      val newSubLaneId = ServiceWithDao.create(subLane2ToAdd, Set(linkId1), 1, usernameTest).head

      val sideCodeForLink100 = SideCodesForLinkIds(linkId1, 1)
      val sideCodesForLinkIds = Seq(sideCodeForLink100)


      //Validate if initial lanes are correctly created
      val currentLanes = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(linkId1), Seq(1, 2), false)
      currentLanes.size should be(2)

      val lane1 = currentLanes.filter(_.id == newMainLaneId).head
      lane1.attributes.foreach{ laneProp =>
        lanePropertiesValues1.contains(laneProp) should be(true)
      }

      val lane2 = currentLanes.filter(_.id == newSubLaneId).head
      lane2.attributes.foreach{ laneProp =>
        lanePropertiesValues2.contains(laneProp) should be(true)
      }


      //Create another sub lane 12
      val newSubLane2WithDiffProperties = Seq(NewLane(0, 0, 500, 745, false, false, newLanePropertiesValues2))

      //Verify if the lane to delete was totally deleted from lane table
      val currentMainLane1 = Seq(mainLane1ToAdd.head.copy(id = newMainLaneId))
      ServiceWithDao.processNewLanes((currentMainLane1 ++ newSubLane2WithDiffProperties).toSet, Set(linkId1), 1, usernameTest, sideCodesForLinkIds)
      val currentLanesAfterProcess = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(linkId1), Seq(1, 2), false)
      currentLanesAfterProcess.size should be(2)

      val lane1After = currentLanesAfterProcess.filter(_.id == newMainLaneId).head
      lane1After.attributes.foreach{ laneProp =>
        lanePropertiesValues1.contains(laneProp) should be(true)
      }

      val lane2After = currentLanesAfterProcess.filter(_.id == newSubLaneId).head
      lane2After.attributes.foreach{ laneProp =>
        newLanePropertiesValues2.contains(laneProp) should be(true)
      }

      //Verify history for old properties of lane 12
      val historyLanes = laneHistoryDao.fetchHistoryLanesByLinkIdsAndLaneCode(Seq(linkId1), Seq(1, 2), true)
      historyLanes.size should be(1)

      val oldSubLane2 = historyLanes.find(_.oldId == newSubLaneId).head
      oldSubLane2.newId should be(0)
      oldSubLane2.attributes.foreach { laneProp =>
        lanePropertiesValues2.contains(laneProp) should be(true)
      }
      oldSubLane2.expired should be(false)

    }
  }

  test("Expire a outer sub lane in various links") {
    runWithRollback {
      //NewIncomeLanes to create
      val mainLane1ToAdd = Seq(NewLane(0, 0, 500, 745, false, false, lanePropertiesValues1))
      val subLane2ToAdd = Seq(NewLane(0, 0, 500, 745, false, false, lanePropertiesValues2))

      //Create initial lanes
      val newMainLaneIdLink100 = ServiceWithDao.create(mainLane1ToAdd, Set(linkId1), 1, usernameTest).head
      val newSubLaneIdLink100 = ServiceWithDao.create(subLane2ToAdd, Set(linkId1), 1, usernameTest).head

      val newMainLaneIdLink101 = ServiceWithDao.create(mainLane1ToAdd, Set(linkId2), 1, usernameTest).head
      val newSubLaneIdLink101 = ServiceWithDao.create(subLane2ToAdd, Set(linkId2), 1, usernameTest).head

      val sideCodeForLink100 = SideCodesForLinkIds(linkId1, 1)
      val sideCodeForLink101 = SideCodesForLinkIds(linkId2, 1)
      val sideCodesForLinkIds = Seq(sideCodeForLink100, sideCodeForLink101)

      //Validate if initial lanes are correctly created
      val currentLanes = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(linkId1, linkId2), Seq(1, 2), false)
      currentLanes.size should be(4)

      val lane1Link100 = currentLanes.filter(_.id == newMainLaneIdLink100).head
      lane1Link100.attributes.foreach{ laneProp =>
        lanePropertiesValues1.contains(laneProp) should be(true)
      }

      val lane2Link100 = currentLanes.filter(_.id == newSubLaneIdLink100).head
      lane2Link100.attributes.foreach{ laneProp =>
        lanePropertiesValues2.contains(laneProp) should be(true)
      }

      val lane1Link101 = currentLanes.filter(_.id == newMainLaneIdLink101).head
      lane1Link101.attributes.foreach{ laneProp =>
        lanePropertiesValues1.contains(laneProp) should be(true)
      }

      val lane2Link101 = currentLanes.filter(_.id == newSubLaneIdLink101).head
      lane2Link101.attributes.foreach{ laneProp =>
        lanePropertiesValues2.contains(laneProp) should be(true)
      }

      //Delete sublane 12 and verify the movement to history tables
      val subLane2ToExpireA = Seq(NewLane(newSubLaneIdLink100, 0, 500, 745, true, false, lanePropertiesValues2))
      val subLane2ToExpireB= Seq(NewLane(newSubLaneIdLink101, 0, 500, 745, true, false, lanePropertiesValues2))

      //Verify if the lane to delete was totally deleted from lane table
      val currentMainLane1 = Seq(mainLane1ToAdd.head.copy(id = newMainLaneIdLink100))
      ServiceWithDao.processNewLanes((currentMainLane1 ++ subLane2ToExpireA ++ subLane2ToExpireB).toSet, Set(linkId1, linkId2), 1, usernameTest, sideCodesForLinkIds)
      val currentLanesAfterDelete = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(linkId1, linkId2), Seq(1, 2), false)
      currentLanesAfterDelete.size should be(2)

      val lane1Link100AfterDelete = currentLanesAfterDelete.filter(_.id == newMainLaneIdLink100).head
      lane1Link100AfterDelete.attributes.foreach{ laneProp =>
        lanePropertiesValues1.contains(laneProp) should be(true)
      }

      val lane1Link101AfterDelete = currentLanesAfterDelete.filter(_.id == newMainLaneIdLink101).head
      lane1Link101AfterDelete.attributes.foreach{ laneProp =>
        lanePropertiesValues1.contains(laneProp) should be(true)
      }

      //Verify the presence of the deleted lane on histories tables
      val historyLanes = laneHistoryDao.fetchHistoryLanesByLinkIdsAndLaneCode(Seq(linkId1, linkId2), Seq(1, 2), true)
      historyLanes.size should be(2)

      val lane2Link100AfterDelete = historyLanes.filter(_.oldId == newSubLaneIdLink100).head
      lane2Link100AfterDelete.linkId should be(linkId1)
      lane2Link100AfterDelete.newId should be(0)
      lane2Link100AfterDelete.expired should be(true)
      lane2Link100AfterDelete.attributes.foreach{ laneProp =>
        lanePropertiesValues2.contains(laneProp) should be(true)
      }

      val lane2Link101AfterDelete = historyLanes.filter(_.oldId == newSubLaneIdLink101).head
      lane2Link101AfterDelete.linkId should be(linkId2)
      lane2Link101AfterDelete.newId should be(0)
      lane2Link101AfterDelete.expired should be(true)
      lane2Link101AfterDelete.attributes.foreach{ laneProp =>
        lanePropertiesValues2.contains(laneProp) should be(true)
      }
    }
  }

  // Deleting inner lanes not supported currently in Lane Modelling tool at the moment
  // TODO fix inner lane deletion unit tests if / when feature is added to Lane Modelling tool
  ignore("Expire a inner sub lane in various links") {
    runWithRollback {
      val lanePropertiesValues4To2 = Seq(LaneProperty("lane_code", Seq(LanePropertyValue(2))),
        LaneProperty("lane_type", Seq(LanePropertyValue("3"))),
        LaneProperty("start_date", Seq(LanePropertyValue(DateTime.now().toString("dd.MM.yyyy"))))
      )

      val mainLane = NewLane(0, 0, 500, 745, false, false, lanePropertiesValues1)
      val subLane2 = NewLane(0, 0, 500, 745, false, false, lanePropertiesValues2)
      val subLane4 = NewLane(0, 0, 500, 745, false, false, lanePropertiesValues4)

      val mainLaneIdLink100 = ServiceWithDao.create(Seq(mainLane), Set(linkId1), 1, usernameTest).head
      val newSubLane2IdLink100 = ServiceWithDao.create(Seq(subLane2), Set(linkId1), 1, usernameTest).head
      val newSubLane4IdLink100 = ServiceWithDao.create(Seq(subLane4), Set(linkId1), 1, usernameTest).head

      val mainLaneIdLink101 = ServiceWithDao.create(Seq(mainLane), Set(linkId2), 1, usernameTest).head
      val newSubLane2IdLink101 = ServiceWithDao.create(Seq(subLane2), Set(linkId2), 1, usernameTest).head
      val newSubLane4IdLink101 = ServiceWithDao.create(Seq(subLane4), Set(linkId2), 1, usernameTest).head

      val sideCodeForLink100 = SideCodesForLinkIds(linkId1, 1)
      val sideCodeForLink101 = SideCodesForLinkIds(linkId1, 1)
      val sideCodesForLinkIds = Seq(sideCodeForLink100, sideCodeForLink101)


      // Delete the lane 12 and update 14 to new 12
      val updatedSubLane4 = NewLane(newSubLane4IdLink100, 0, 500, 745, false, false, lanePropertiesValues4To2)
      val currentMainLane = mainLane.copy(id = mainLaneIdLink100)
      ServiceWithDao.processNewLanes(Set(currentMainLane, updatedSubLane4), Set(linkId1, linkId2), 1, usernameTest, sideCodesForLinkIds)


      //Validate the delete of old lane 12 and the movement of lane 14 to 12
      val lanes = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(linkId1, linkId2), Seq(1, 2, 4), true)
      lanes.length should be(4)
      lanes.find(lane => lane.id == newSubLane2IdLink100 || lane.id == newSubLane2IdLink101) should be(None)
      lanes.find(_.laneCode == 4) should be(None)

      val newSubLane2Link100 = lanes.find(_.id == newSubLane4IdLink100).head
      newSubLane2Link100.attributes.foreach { laneProp =>
        updatedSubLane4.properties.contains(laneProp) should be(true)
      }
      newSubLane2Link100.startMeasure should be(0.0)
      newSubLane2Link100.endMeasure should be(500.0)

      val newSubLane2Link101 = lanes.find(_.id == newSubLane4IdLink101).head
      newSubLane2Link101.attributes.foreach { laneProp =>
        updatedSubLane4.properties.contains(laneProp) should be(true)
      }
      newSubLane2Link101.startMeasure should be(0.0)
      newSubLane2Link101.endMeasure should be(500.0)


      //Confirm the expired lane 12 and old info of lane 14 (Now 12) in history table
      val historyLanes = laneHistoryDao.fetchHistoryLanesByLinkIdsAndLaneCode(Seq(linkId1, linkId2), Seq(1, 2, 4), true)
      historyLanes.length should be(4)

      val deletedSubLane2Link100 = historyLanes.find(_.oldId == newSubLane2IdLink100).head
      deletedSubLane2Link100.linkId should be(linkId1)
      deletedSubLane2Link100.newId should be(newSubLane4IdLink100)
      deletedSubLane2Link100.attributes.foreach { laneProp =>
        lanePropertiesValues2.contains(laneProp) should be(true)
      }
      deletedSubLane2Link100.startMeasure should be(0.0)
      deletedSubLane2Link100.endMeasure should be(500.0)
      deletedSubLane2Link100.expired should be(true)

      val deletedSubLane4Link100 = historyLanes.find(_.oldId == newSubLane4IdLink100).head
      deletedSubLane4Link100.linkId should be(linkId1)
      deletedSubLane4Link100.newId should be(0)
      deletedSubLane4Link100.attributes.foreach { laneProp =>
        lanePropertiesValues4.contains(laneProp) should be(true)
      }
      deletedSubLane4Link100.expired should be(false)
      deletedSubLane4Link100.startMeasure should be(0.0)
      deletedSubLane4Link100.endMeasure should be(500.0)

      val deletedSubLane2Link101 = historyLanes.find(_.oldId == newSubLane2IdLink101).head
      deletedSubLane2Link101.linkId should be(linkId2)
      deletedSubLane2Link101.newId should be(newSubLane4IdLink101)
      deletedSubLane2Link101.attributes.foreach { laneProp =>
        lanePropertiesValues2.contains(laneProp) should be(true)
      }
      deletedSubLane2Link101.startMeasure should be(0.0)
      deletedSubLane2Link101.endMeasure should be(500.0)
      deletedSubLane2Link101.expired should be(true)

      val deletedSubLane4Link101 = historyLanes.find(_.oldId == newSubLane4IdLink101).head
      deletedSubLane4Link101.linkId should be(linkId2)
      deletedSubLane4Link101.newId should be(0)
      deletedSubLane4Link101.attributes.foreach { laneProp =>
        lanePropertiesValues4.contains(laneProp) should be(true)
      }
      deletedSubLane4Link101.expired should be(false)
      deletedSubLane4Link101.startMeasure should be(0.0)
      deletedSubLane4Link101.endMeasure should be(500.0)
    }
  }

  test("Testing expiring lane by linkId"){
    runWithRollback {
      val laneToBeCreated = NewLane(0, 0, 100, 745, false, false, lanePropertiesValues1)
      val createdLaneID = ServiceWithDao.create(Seq(laneToBeCreated), Set(linkId1), 2, usernameTest).head
      laneDao.expireLanesByLinkId(Set(linkId1), usernameTest)
      val laneId = laneDao.fetchLanesByIds(Set(createdLaneID))
      laneId.head.expired should be(true)
    }
  }

  test("Testing expiring additional lanes"){
    runWithRollback {
      val mainLaneToBeCreated = NewLane(0, 0, 100, 745, isExpired = false, isDeleted = false, lanePropertiesValues1)
      val additionalLaneToBeCreated = NewLane(0, 0, 100, 745, isExpired = false, isDeleted = false, lanePropertiesValues2)
      val createdMainLaneID = ServiceWithDao.create(Seq(mainLaneToBeCreated), Set(linkId1), 2, usernameTest).head
      val createdAdditionalLaneID = ServiceWithDao.create(Seq(additionalLaneToBeCreated), Set(linkId1), 2, usernameTest).head
      laneDao.expireAdditionalLanes(usernameTest)
      val mainLane = laneDao.fetchLanesByIds(Set(createdMainLaneID))
      val additionalLane = laneDao.fetchLanesByIds(Set(createdAdditionalLaneID))
      mainLane.head.expired should be(false)
      additionalLane.head.expired should be(true)
    }
  }

  test("Lane Change: Show 2 Add"){
    runWithRollback {
      val newLane1 = NewLane(0, 0, 100, 745, false, false, lanePropertiesValues1)
      val newLane21 = newLane1.copy(properties = lanePropertiesValues1)
      val dateAtThisMoment = DateTime.now()

      ServiceWithDao.create(Seq(newLane1), Set(linkId1), 2, usernameTest)
      ServiceWithDao.create(Seq(newLane21), Set(linkId1), 2, usernameTest)

      when(mockRoadLinkService.getRoadLinksByLinkIds(Set(linkId1), false)).thenReturn(Seq(roadlink1))
      when(mockRoadAddressService.roadLinkWithRoadAddress(Seq(roadlink1))).thenReturn(Seq(roadlink1))
      val lanesChanged = ServiceWithDao.getChanged(dateAtThisMoment.minusDays(1), dateAtThisMoment.plusDays(1))

      lanesChanged.map(_.changeType) should be(Seq(LaneChangeType.Add, LaneChangeType.Add))
    }
  }

  test("Lane Change: Show 1 Add and 1 attribute changed"){
    runWithRollback {
      val newLane1 = NewLane(0, 0, 100, 745, false, false, lanePropertiesValues1)
      val dateAtThisMoment = DateTime.now()

      val newLanePropertiesValues11 = Seq( LaneProperty("lane_code", Seq(LanePropertyValue(1))),
        LaneProperty("lane_type", Seq(LanePropertyValue("4")))
      )

      val lane1Id = ServiceWithDao.create(Seq(newLane1), Set(linkId1), 2, usernameTest).head
      val allExistingLanes = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(linkId1))
      ServiceWithDao.update(Seq(newLane1.copy(id = lane1Id, properties = newLanePropertiesValues11)), Set(linkId1), 2, usernameTest, Seq(SideCodesForLinkIds(linkId1, 2)), allExistingLanes)

      when(mockRoadLinkService.getRoadLinksByLinkIds(Set(linkId1), false)).thenReturn(Seq(roadlink1))
      when(mockRoadAddressService.roadLinkWithRoadAddress(Seq(roadlink1))).thenReturn(Seq(roadlink1))

      val lanesChanged = ServiceWithDao.getChanged(dateAtThisMoment.minusDays(1), dateAtThisMoment.plusDays(1))

      lanesChanged.map(_.changeType).sortBy(_.value) should be(Seq(LaneChangeType.Add, LaneChangeType.AttributesChanged))
      lanesChanged.map(_.lane.id) should be(Seq(lane1Id, lane1Id))
    }
  }


  test("Lane Change: Show 2 Add and 1 expire"){
    runWithRollback {
      val newLane1 = NewLane(0L, 0, 100, 745, false, false, lanePropertiesValues1)
      val newLane2 = newLane1.copy(id = 1L, properties = lanePropertiesValues2)
      val dateAtThisMoment = DateTime.now()

      ServiceWithDao.create(Seq(newLane1), Set(linkId1), 2, usernameTest)
      val lane2Id = ServiceWithDao.create(Seq(newLane2), Set(linkId1), 2, usernameTest).head
      val persistedLane2 = ServiceWithDao.fetchAllLanesByLinkIds(Seq(linkId1)).find(_.id == lane2Id).get
      ServiceWithDao.deleteMultipleLanes(Seq(persistedLane2), usernameTest)

      when(mockRoadLinkService.getRoadLinksByLinkIds(Set(linkId1), false)).thenReturn(Seq(roadlink1))
      when(mockRoadAddressService.roadLinkWithRoadAddress(Seq(roadlink1))).thenReturn(Seq(roadlink1))

      val lanesChanged = ServiceWithDao.getChanged(dateAtThisMoment.minusDays(1), dateAtThisMoment.plusDays(1))

      lanesChanged.map(_.changeType).sortBy(_.value) should be(Seq(LaneChangeType.Add, LaneChangeType.Add, LaneChangeType.Expired))
      lanesChanged.filter(_.changeType == LaneChangeType.Expired).map(_.lane.id) should be(Seq(lane2Id))
    }
  }

  test("Lane Change: Show 2 Add, 1 lane code change"){
    runWithRollback {
      val newLane2 = NewLane(0, 0, 100, 745, false, false, lanePropertiesValues2)
      val newLane4 = newLane2.copy(properties = lanePropertiesValues4)
      val dateAtThisMoment = DateTime.now()

      ServiceWithDao.create(Seq(newLane2), Set(linkId1), 2, usernameTest)
      val lane4Id = ServiceWithDao.create(Seq(newLane4), Set(linkId1), 2, usernameTest).head
      val allExistingLanes = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(linkId1))

      val newLanePropertiesValuesOld4 = Seq(LaneProperty("lane_code", Seq(LanePropertyValue(2))),
        LaneProperty("lane_type", Seq(LanePropertyValue("3")))
      )

      ServiceWithDao.update(Seq(newLane4.copy(id = lane4Id, properties = newLanePropertiesValuesOld4)), Set(linkId1), 2, usernameTest, Seq(SideCodesForLinkIds(linkId1, 2)), allExistingLanes)

      when(mockRoadLinkService.getRoadLinksByLinkIds(Set(linkId1), false)).thenReturn(Seq(roadlink1))
      when(mockRoadAddressService.roadLinkWithRoadAddress(Seq(roadlink1))).thenReturn(Seq(roadlink1))

      val lanesChanged = ServiceWithDao.getChanged(dateAtThisMoment.minusDays(1), dateAtThisMoment.plusDays(1))

      lanesChanged.map(_.changeType).sortBy(_.value) should be(Seq(LaneChangeType.Add, LaneChangeType.Add, LaneChangeType.LaneCodeTransfer))
      val laneCodeChanged = lanesChanged.filter(_.changeType == LaneChangeType.LaneCodeTransfer).head
      laneCodeChanged.lane.id should be(lane4Id)
      laneCodeChanged.lane.laneCode should be(12)
      laneCodeChanged.oldLane.get.laneCode should be(14)
    }
  }

  test("Lane Change: Show 2 Add and 1 shortened lane"){
    runWithRollback {
      val newLane1 = NewLane(0, 0, 100, 745, false, false, lanePropertiesValues1)
      val newLane2 = newLane1.copy(properties = lanePropertiesValues2)
      val dateAtThisMoment = DateTime.now()

      ServiceWithDao.create(Seq(newLane1), Set(linkId1), 2, usernameTest)
      val lane2Id = ServiceWithDao.create(Seq(newLane2), Set(linkId1), 2, usernameTest).head
      val allExistingLanes = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(linkId1))

      val subLane2Split = NewLane(0, 0, 50, 745, false, false, lanePropertiesValues2)
      ServiceWithDao.update(Seq(subLane2Split), Set(linkId1), 2, usernameTest, Seq(SideCodesForLinkIds(linkId1, 2)), allExistingLanes)

      when(mockRoadLinkService.getRoadLinksByLinkIds(Set(linkId1), false)).thenReturn(Seq(roadlink1))
      when(mockRoadAddressService.roadLinkWithRoadAddress(Seq(roadlink1))).thenReturn(Seq(roadlink1))

      val lanesChanged = ServiceWithDao.getChanged(dateAtThisMoment.minusDays(1), dateAtThisMoment.plusDays(1))

      lanesChanged.map(_.changeType).sortBy(_.value) should be(Seq(LaneChangeType.Add, LaneChangeType.Add, LaneChangeType.Shortened))
      val shortenedLane = lanesChanged.filter(_.changeType == LaneChangeType.Shortened).head
      shortenedLane.lane.endMeasure should be(50)
      shortenedLane.oldLane.get.endMeasure should be(100)
      shortenedLane.oldLane.get.id should be(lane2Id)
    }
  }

  test("Lane Change: Get only the first 2 changes with token"){
    //token = pageNumber:1,recordNumber:2
    runWithRollback {
      val newLane1 = NewLane(0, 0, 100, 745, false, false, lanePropertiesValues1)
      val newLane2 = newLane1.copy(properties = lanePropertiesValues2)
      val dateAtThisMoment = DateTime.now()

      ServiceWithDao.create(Seq(newLane1), Set(linkId1), 2, usernameTest)
      ServiceWithDao.create(Seq(newLane2), Set(linkId1), 2, usernameTest)

      val allExistingLanes = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(linkId1))
      val subLane2Split = NewLane(0, 0, 50, 745, false, false, lanePropertiesValues2)
      val subLane2Id = ServiceWithDao.update(Seq(subLane2Split), Set(linkId1), 2, usernameTest, Seq(SideCodesForLinkIds(linkId1, 2)), allExistingLanes).head

      when(mockRoadLinkService.getRoadLinksByLinkIds(Set(linkId1), false)).thenReturn(Seq(roadlink1))
      when(mockRoadAddressService.roadLinkWithRoadAddress(Seq(roadlink1))).thenReturn(Seq(roadlink1))

      val lanesChanged = ServiceWithDao.getChanged(dateAtThisMoment.minusDays(1), dateAtThisMoment.plusDays(1), token = Some("cGFnZU51bWJlcjoxLHJlY29yZE51bWJlcjoy"))

      lanesChanged.map(_.changeType).sortBy(_.value) should be(Seq(LaneChangeType.Add, LaneChangeType.Add))
      lanesChanged.size should equal(2)
    }
  }

  test("Lane Change:Show 1 Add and 2 Divided") {
    //2 divides because two new lanes with same old lane
    runWithRollback {
      val lanePropertiesValues2B = Seq( LaneProperty("lane_code", Seq(LanePropertyValue(2))),
        LaneProperty("lane_type", Seq(LanePropertyValue("3"))),
        LaneProperty("start_date", Seq(LanePropertyValue(DateTime.now().toString("dd.MM.yyyy")))),
        LaneProperty("end_date", Seq(LanePropertyValue(DateTime.now().plusDays(10).toString("dd.MM.yyyy"))))
      )

      val newLane2 = NewLane(0, 0, 500, 745, false, false, lanePropertiesValues2)
      val lane2SplitA = NewLane(0, 0, 250, 745, false, false, lanePropertiesValues2)
      val lane2SplitB = NewLane(0, 250, 500, 745, false, false, lanePropertiesValues2B)

      val dateAtThisMoment = DateTime.now()
      val lane2Id = ServiceWithDao.create(Seq(newLane2), Set(linkId1), 2, usernameTest).head

      ServiceWithDao.createMultiLanesOnLink(Seq(lane2SplitA,lane2SplitB), Set(linkId1), 2, usernameTest)

      when(mockRoadLinkService.getRoadLinksByLinkIds(Set(linkId1), false)).thenReturn(Seq(roadlink1))
      when(mockRoadAddressService.roadLinkWithRoadAddress(Seq(roadlink1))).thenReturn(Seq(roadlink1))

      val lanesChanged = ServiceWithDao.getChanged(dateAtThisMoment.minusDays(1), dateAtThisMoment.plusDays(1))

      lanesChanged.map(_.changeType).sortBy(_.value) should be(Seq(LaneChangeType.Add, LaneChangeType.Divided, LaneChangeType.Divided))
      val lanesDivides = lanesChanged.filter(_.changeType == LaneChangeType.Divided)
      lanesDivides.map(_.oldLane.get.id) should be(Seq(lane2Id, lane2Id))
    }
  }

  test("Show 2 add and 2 laneCodeTransfer"){
    runWithRollback {

      val lanePropertiesValues1 = Seq( LaneProperty("lane_code", Seq(LanePropertyValue(1))),
        LaneProperty("lane_type", Seq(LanePropertyValue("1"))),
        LaneProperty("start_date", Seq(LanePropertyValue(DateTime.now().toString("dd.MM.yyyy"))))
      )

      val lanePropertiesValues2 = Seq( LaneProperty("lane_code", Seq(LanePropertyValue(2))),
        LaneProperty("lane_type", Seq(LanePropertyValue("2"))),
        LaneProperty("start_date", Seq(LanePropertyValue(DateTime.now().toString("dd.MM.yyyy"))))
      )

      val newLane1 = NewLane(0, 0, 500, 745, false, false, lanePropertiesValues1)
      val newLane2 = NewLane(0, 0, 500, 745, false, false, lanePropertiesValues2)

      val lane1Id = ServiceWithDao.create(Seq(newLane1), Set(linkId1), 2, usernameTest).head
      val lane2Id = ServiceWithDao.create(Seq(newLane2), Set(linkId1), 2, usernameTest).head

      val allExistingLanes = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(linkId1))

      val lane1toLaneCode2 = newLane1.copy(id = lane1Id, newLaneCode = Some(2), properties = lanePropertiesValues2)
      val lane2toLaneCode1 = newLane2.copy(id = lane2Id, newLaneCode = Some(1), properties = lanePropertiesValues1)

      val sideCodesForLinkIds = SideCodesForLinkIds(linkId1, 2)

      val updatedIds = ServiceWithDao.update(Seq(lane1toLaneCode2, lane2toLaneCode1), Set(linkId1), 2,usernameTest, Seq(sideCodesForLinkIds), allExistingLanes)

      when(mockRoadLinkService.getRoadLinksByLinkIds(Set(linkId1), false)).thenReturn(Seq(roadlink1))
      when(mockRoadAddressService.roadLinkWithRoadAddress(Seq(roadlink1))).thenReturn(Seq(roadlink1))

      val lanesChanged = ServiceWithDao.getChanged(DateTime.now().minusDays(1), DateTime.now().plusDays(1))
      lanesChanged.map(_.changeType).sortBy(_.value) should be(Seq(LaneChangeType.Add, LaneChangeType.Add, LaneChangeType.LaneCodeTransfer, LaneChangeType.LaneCodeTransfer))
    }
  }

  test("Additional lane without start_date error") {
    runWithRollback {
      val lanePropertiesValues = Seq( LaneProperty("lane_code", Seq(LanePropertyValue(2))),
        LaneProperty("lane_type", Seq(LanePropertyValue("3")))
      )
      val thrown = intercept[IllegalArgumentException] {
        ServiceWithDao.validateStartDate(NewLane(0, 0, 500, 745, false, false, lanePropertiesValues), 2)
      }
      thrown.getMessage should be("Start Date attribute not found on additional lane!")
    }
  }

  test("Correctly create view only segmented lanes") {
    val roadLink1 = RoadLink("1L", Seq(Point(0.0, 0.0), Point(100.0, 0.0)), 100.0, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None)

    def constructPersistedLaneTowards(laneCode: Int, startMeasure: Double, endMeasure: Double, lanePropertiesValues: Seq[LaneProperty]) = {
      val sideCode = SideCode.TowardsDigitizing

      PersistedLane(laneCode.toLong, roadLink1.linkId, sideCode.value,
        laneCode, 745L, startMeasure, endMeasure, None, None, None, None, None, None, expired = false, 0L, None, lanePropertiesValues)
    }

    def constructPersistedLaneAgainst(laneCode: Int, startMeasure: Double, endMeasure: Double, lanePropertiesValues: Seq[LaneProperty]) = {
      val sideCode = SideCode.AgainstDigitizing

      PersistedLane(laneCode.toLong, roadLink1.linkId, sideCode.value,
        laneCode, 745L, startMeasure, endMeasure, None, None, None, None, None, None, expired = false, 0L, None, lanePropertiesValues)
    }

    val lane1 = constructPersistedLaneTowards(1,0, 100, lanePropertiesValues1)
    val lane2 = constructPersistedLaneTowards(2, 0, 90, lanePropertiesValues2)
    val lane4 = constructPersistedLaneTowards(4, 0, 40, lanePropertiesValues4)

    val lane21 = constructPersistedLaneAgainst(1, 0, 100, lanePropertiesValues1)
    val lane22 = constructPersistedLaneAgainst(2, 50, 80, lanePropertiesValues2)

    val allLanes = Seq(lane1, lane2, lane4, lane21, lane22)

    val segmentedViewOnlyLanes = ServiceWithDao.getSegmentedViewOnlyLanes(allLanes, Seq(roadLink1))
    segmentedViewOnlyLanes.size should be(6)

    def verifySegment(sideCodeValue: Int, startMeasure: Double, endMeasure: Double, lanes: Seq[Int]) = {
      segmentedViewOnlyLanes.find(svol => svol.startMeasure == startMeasure && svol.endMeasure == endMeasure && svol.sideCode == sideCodeValue).get.lanes.sorted should be(lanes)
    }

    verifySegment(SideCode.TowardsDigitizing.value, 0, 40, Seq(1, 2, 4))
    verifySegment(SideCode.TowardsDigitizing.value, 40, 90, Seq(1, 2))
    verifySegment(SideCode.TowardsDigitizing.value, 90, 100, Seq(1))

    verifySegment(SideCode.AgainstDigitizing.value, 0, 50, Seq(1))
    verifySegment(SideCode.AgainstDigitizing.value, 50, 80, Seq(1, 2))
    verifySegment(SideCode.AgainstDigitizing.value, 80, 100, Seq(1))
  }

  test("Lane Change: Show 1 Add with roadAddress"){
    runWithRollback {
      val newLane1 = NewLane(0, 0, 100, 745, false, false, lanePropertiesValues1)
      val newLane21 = newLane1.copy(properties = lanePropertiesValues1)
      val dateAtThisMoment = DateTime.now()

      ServiceWithDao.create(Seq(newLane1), Set(linkId1), 2, usernameTest)
      ServiceWithDao.create(Seq(newLane21), Set(linkId2), 2, usernameTest)

      when(mockRoadLinkService.getRoadLinksByLinkIds(Set(linkId1,linkId2), false)).thenReturn(
        Seq(roadlink1,roadlink2)
      )
      when(mockRoadAddressService.roadLinkWithRoadAddress(Seq(roadlink1, roadlink2))).thenReturn(Seq(roadlink1, roadlink2))

      val lanesChanged = ServiceWithDao.getChanged(dateAtThisMoment.minusDays(1), dateAtThisMoment.plusDays(1))

      lanesChanged.map(_.changeType) should be(Seq(LaneChangeType.Add))
    }
  }
  test("LaneCodes should be correct two digit codes") {
    val attributes = Seq(LaneProperty("lane_code", Seq(LanePropertyValue(1))))
    val laneTowardsDigitizing = PersistedLane(0, linkId1, SideCode.TowardsDigitizing.value, 1, 0, 0, 100, None, None, None, None, None, None, false, 0L, None, attributes)
    val laneAgainstDigitizing = PersistedLane(1, linkId1, SideCode.AgainstDigitizing.value, 1, 0, 0, 100, None, None, None, None, None, None, false, 0L, None, attributes)

    when(mockRoadLinkService.getRoadLinksByLinkIds(Set(linkId1), true)).thenReturn(
      Seq(RoadLink(linkId1, Seq(Point(20.0, 20.0), Point(40, 40.0)), 100, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map(
        "ROADNUMBER" -> 100
      ))))

    when(mockRoadLinkService.getRoadLinksByLinkIds(Set(linkId2), true)).thenReturn(
      Seq(RoadLink(linkId2, Seq(Point(50.0, 50.0), Point(100.0, 100.0)), 100, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None, Map(
        "ROADNUMBER" -> 101
      ))))

    val laneTowardsTwoDigit = persistedLanesTwoDigitLaneCode(Seq(laneTowardsDigitizing), Seq(roadlink1)).head
    val laneAgainstTwoDigit = persistedLanesTwoDigitLaneCode(Seq(laneAgainstDigitizing), Seq(roadlink1)).head

    laneTowardsTwoDigit.laneCode should equal(11)
    laneAgainstTwoDigit.laneCode should equal(21)
  }

  test("Create three new split lanes") {
    runWithRollback {
      val mainLane1 = NewLane(0, 0, 500, 745, false, false, lanePropertiesValues1)
      val mainLane1Id = ServiceWithDao.create(Seq(mainLane1), Set(linkId1), 1, usernameTest).head

      val sideCodeForLink100 = SideCodesForLinkIds(linkId1, 1)
      val sideCodesForLinkIds = Seq(sideCodeForLink100)

      val initialLanes = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(linkId1), Seq(1, 2), false)
      initialLanes.size should be(1)

      val createdMainLane = NewLane(mainLane1Id, 0, 500, 745, false, false, lanePropertiesValues1)

      val subLane2SplitA = NewLane(0, 0, 150, 745, false, false, lanePropertiesValues2)
      val subLane2SplitB = NewLane(0, 150, 350, 745, false, false, lanePropertiesValues2)
      val subLane2SplitC = NewLane(0, 350, 500, 745, false, false, lanePropertiesValues2)

      ServiceWithDao.processNewLanes(Set(createdMainLane, subLane2SplitA, subLane2SplitB, subLane2SplitC), Set(linkId1), 1, usernameTest, sideCodesForLinkIds)

      val currentLanes = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(linkId1), Seq(1, 2), false)
      currentLanes.size should be(4)

      val lane1 = currentLanes.filter(_.id == mainLane1Id).head
      lane1.id should be(mainLane1Id)
      lane1.attributes.foreach { laneProp =>
        lanePropertiesValues1.contains(laneProp) should be(true)
      }

      val splitLanes = currentLanes.filter(_.laneCode == 2).sortBy(_.startMeasure)
      splitLanes.size should be(3)

      splitLanes(0).startMeasure should be(0)
      splitLanes(0).endMeasure should be(150)
      splitLanes(1).startMeasure should be(150)
      splitLanes(1).endMeasure should be(350)
      splitLanes(2).startMeasure should be(350)
      splitLanes(2).endMeasure should be(500)

      splitLanes.foreach { lane =>
        lane.attributes.foreach {
          laneProp =>
            lanePropertiesValues2.contains(laneProp) should be(true)
        }
      }

      val expiredLanes = laneHistoryDao.fetchHistoryLanesByLinkIdsAndLaneCode(Seq(linkId1), Seq(2), true)
      expiredLanes.size should be(0)

      when(mockRoadLinkService.getRoadLinksByLinkIds(Set(linkId1), false)).thenReturn(Seq(roadlink1))
      when(mockRoadAddressService.roadLinkWithRoadAddress(Seq(roadlink1))).thenReturn(Seq(roadlink1))

      val dateAtThisMoment = DateTime.now()
      val laneChanges = ServiceWithDao.getChanged(dateAtThisMoment.minusDays(1), dateAtThisMoment.plusDays(1))

      laneChanges.size should be(4)
      laneChanges.count(_.changeType == LaneChangeType.Add) should be(4)
    }
  }

  test("Split existing lane in three pieces") {
    runWithRollback {
      val mainLane1 = NewLane(0, 0, 500, 745, false, false, lanePropertiesValues1)
      val subLane2 = NewLane(0, 0, 500, 745, false, false, lanePropertiesValues2)
      val mainLane1Id = ServiceWithDao.create(Seq(mainLane1), Set(linkId1), 1, usernameTest).head
      val sublane2Id = ServiceWithDao.create(Seq(subLane2), Set(linkId1), 1, usernameTest)

      val sideCodeForLink100 = SideCodesForLinkIds(linkId1, 1)
      val sideCodesForLinkIds = Seq(sideCodeForLink100)

      val initialLanes = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(linkId1), Seq(1, 2), false)
      initialLanes.size should be(2)

      val createdMainLane = NewLane(mainLane1Id, 0, 500, 745, false, false, lanePropertiesValues1)

      val subLane2SplitA = NewLane(0, 0, 150, 745, false, false, lanePropertiesValues2)
      val subLane2SplitB = NewLane(0, 150, 350, 745, false, false, lanePropertiesValues2)
      val subLane2SplitC = NewLane(0, 350, 500, 745, false, false, lanePropertiesValues2)

      ServiceWithDao.processNewLanes(Set(createdMainLane, subLane2SplitA, subLane2SplitB, subLane2SplitC), Set(linkId1), 1, usernameTest, sideCodesForLinkIds)

      val currentLanes = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(linkId1), Seq(1, 2), false)
      currentLanes.size should be(4)

      val lane1 = currentLanes.filter(_.id == mainLane1Id).head
      lane1.id should be(mainLane1Id)
      lane1.attributes.foreach { laneProp =>
        lanePropertiesValues1.contains(laneProp) should be(true)
      }

      val splitLanes = currentLanes.filter(_.laneCode == 2).sortBy(_.startMeasure)
      splitLanes.size should be(3)

      splitLanes(0).startMeasure should be(0)
      splitLanes(0).endMeasure should be(150)
      splitLanes(1).startMeasure should be(150)
      splitLanes(1).endMeasure should be(350)
      splitLanes(2).startMeasure should be(350)
      splitLanes(2).endMeasure should be(500)

      splitLanes.foreach { lane =>
        lane.attributes.foreach {
          laneProp =>
            lanePropertiesValues2.contains(laneProp) should be(true)
        }
      }

      val expiredLanes = laneHistoryDao.fetchHistoryLanesByLinkIdsAndLaneCode(Seq(linkId1), Seq(2), true)
      expiredLanes.size should be(3)

      when(mockRoadLinkService.getRoadLinksByLinkIds(Set(linkId1), false)).thenReturn(Seq(roadlink1))
      when(mockRoadAddressService.roadLinkWithRoadAddress(Seq(roadlink1))).thenReturn(Seq(roadlink1))

      val dateAtThisMoment = DateTime.now()
      val laneChanges = ServiceWithDao.getChanged(dateAtThisMoment.minusDays(1), dateAtThisMoment.plusDays(1))

      // The change type for the main lane and the additional lane saved before split is add and the type for split lanes is divided.
      laneChanges.size should be(5)
      laneChanges.count(_.changeType == LaneChangeType.Add) should be(2)
      laneChanges.count(_.changeType == LaneChangeType.Divided) should be(3)

      val divided = laneChanges.filter(_.changeType == LaneChangeType.Divided).sortBy(_.lane.startMeasure)
      divided(0).lane.startMeasure should be(0)
      divided(0).lane.endMeasure should be(150)
      divided(1).lane.startMeasure should be(150)
      divided(1).lane.endMeasure should be(350)
      divided(2).lane.startMeasure should be(350)
      divided(2).lane.endMeasure should be(500)

      divided.foreach(laneChange => laneChange.oldLane.get.id should be(sublane2Id.head))
    }
  }

  test("Split lane in both ends, leaving the middle untouched") {
    runWithRollback {
      val mainLane1 = NewLane(0, 0, 500, 745, false, false, lanePropertiesValues1)
      val subLane2SplitA = NewLane(0, 0, 150, 745, false, false, lanePropertiesValues2)
      val subLane2SplitB = NewLane(0, 150, 350, 745, false, false, lanePropertiesValues2)
      val subLane2SplitC = NewLane(0, 350, 500, 745, false, false, lanePropertiesValues2)
      val laneIds = ServiceWithDao.create(Seq(mainLane1, subLane2SplitA, subLane2SplitB, subLane2SplitC), Set(linkId1), 1, usernameTest)

      val sideCodeForLink100 = SideCodesForLinkIds(linkId1, 1)
      val sideCodesForLinkIds = Seq(sideCodeForLink100)

      val initialLanes = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(linkId1), Seq(1, 2), false)
      initialLanes.size should be(4)

      val existingMainLane1 = NewLane(laneIds(0), 0, 500, 745, false, false, lanePropertiesValues1)
      val newSubLane2SplitA1 = NewLane(0, 0, 50, 745, false, false, lanePropertiesValues2)
      val newSubLane2SplitA2 = NewLane(0, 50, 150, 745, false, false, lanePropertiesValues2)
      val existingSubLane2SplitB = NewLane(laneIds(2), 150, 350, 745, false, false, lanePropertiesValues2)
      val newSubLane2SplitC1 = NewLane(0, 350, 450, 745, false, false, lanePropertiesValues2)
      val newSubLane2SplitC2 = NewLane(0, 450, 500, 745, false, false, lanePropertiesValues2)

      ServiceWithDao.processNewLanes(Set(existingMainLane1, newSubLane2SplitA1, newSubLane2SplitA2, existingSubLane2SplitB, newSubLane2SplitC1, newSubLane2SplitC2), Set(linkId1), 1, usernameTest, sideCodesForLinkIds)

      val currentLanes = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(linkId1), Seq(1, 2), false)
      currentLanes.size should be(6)

      val expiredLanes = laneHistoryDao.fetchHistoryLanesByLinkIdsAndLaneCode(Seq(linkId1), Seq(2), true)
      expiredLanes.size should be(4)

      when(mockRoadLinkService.getRoadLinksByLinkIds(Set(linkId1), false)).thenReturn(Seq(roadlink1))
      when(mockRoadAddressService.roadLinkWithRoadAddress(Seq(roadlink1))).thenReturn(Seq(roadlink1))

      val dateAtThisMoment = DateTime.now()
      val laneChanges = ServiceWithDao.getChanged(dateAtThisMoment.minusDays(1), dateAtThisMoment.plusDays(1))

      /*The change type for the main lane and the additional split lanes saved before the second split is add. After two
      of the split lanes are split further into four pieces total, the change type for these four split lanes is divided*/
      laneChanges.size should be(8)
      laneChanges.count(_.changeType == LaneChangeType.Add) should be(4)
      laneChanges.count(_.changeType == LaneChangeType.Divided) should be(4)

      //Check that the measures and old lane ids are correct in change message.
      val divided = laneChanges.filter(_.changeType == LaneChangeType.Divided).sortBy(_.lane.startMeasure)
      divided(0).lane.startMeasure should be(0)
      divided(0).lane.endMeasure should be(50)
      divided(0).oldLane.get.id should be(laneIds(1)) //the id for split lane A
      divided(1).lane.startMeasure should be(50)
      divided(1).lane.endMeasure should be(150)
      divided(1).oldLane.get.id should be(laneIds(1))
      divided(2).lane.startMeasure should be(350)
      divided(2).lane.endMeasure should be(450)
      divided(2).oldLane.get.id should be(laneIds(3)) //the id for split lane C
      divided(3).lane.startMeasure should be(450)
      divided(3).lane.endMeasure should be(500)
      divided(3).oldLane.get.id should be(laneIds(3))
    }
  }

  test("Change properties of several splits") {
    runWithRollback {
      val mainLane1 = NewLane(0, 0, 500, 745, false, false, lanePropertiesValues1)
      val subLane2SplitA = NewLane(0, 0, 100, 745, false, false, lanePropertiesValues2)
      val subLane2SplitB = NewLane(0, 100, 200, 745, false, false, lanePropertiesValues2)
      val subLane2SplitC = NewLane(0, 200, 300, 745, false, false, lanePropertiesValues2)
      val subLane2SplitD = NewLane(0, 300, 400, 745, false, false, lanePropertiesValues2)
      val subLane2SplitE = NewLane(0, 400, 500, 745, false, false, lanePropertiesValues2)
      val laneIds = ServiceWithDao.create(Seq(mainLane1, subLane2SplitA, subLane2SplitB, subLane2SplitC, subLane2SplitD, subLane2SplitE), Set(linkId1), 1, usernameTest)

      val sideCodeForLink100 = SideCodesForLinkIds(linkId1, 1)
      val sideCodesForLinkIds = Seq(sideCodeForLink100)

      val initialLanes = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(linkId1), Seq(1, 2), false)
      initialLanes.size should be(6)

      val newPropertyValues1 = Seq(LaneProperty("lane_code", Seq(LanePropertyValue(2))),
        LaneProperty("lane_type", Seq(LanePropertyValue("5"))),
        LaneProperty("start_date", Seq(LanePropertyValue(DateTime.now().toString("dd.MM.yyyy")))),
        LaneProperty("end_date", Seq(LanePropertyValue(DateTime.now().plusDays(10).toString("dd.MM.yyyy"))))
      )

      val newPropertyValues2 = Seq(LaneProperty("lane_code", Seq(LanePropertyValue(2))),
        LaneProperty("lane_type", Seq(LanePropertyValue("13"))),
        LaneProperty("start_date", Seq(LanePropertyValue(DateTime.now().toString("dd.MM.yyyy")))),
        LaneProperty("end_date", Seq(LanePropertyValue(DateTime.now().plusDays(10).toString("dd.MM.yyyy"))))
      )

      val createdMainLane = NewLane(laneIds(0), 0, 500, 745, false, false, lanePropertiesValues1)
      val createdSubLane2SplitA = NewLane(laneIds(1), 0, 100, 745, false, false, lanePropertiesValues2)
      val createdSubLane2SplitB = NewLane(laneIds(2), 100, 200, 745, false, false, newPropertyValues1)
      val createdSubLane2SplitC = NewLane(laneIds(3), 200, 300, 745, false, false, newPropertyValues2)
      val createdSubLane2SplitD = NewLane(laneIds(4), 300, 400, 745, false, false, lanePropertiesValues2)
      val createdSubLane2SplitE = NewLane(laneIds(5), 400, 500, 745, false, false, newPropertyValues2)

      ServiceWithDao.processNewLanes(Set(createdMainLane, createdSubLane2SplitA, createdSubLane2SplitB, createdSubLane2SplitC, createdSubLane2SplitD, createdSubLane2SplitE), Set(linkId1), 1, usernameTest, sideCodesForLinkIds)

      val currentLanes = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(linkId1), Seq(1, 2), false)
      currentLanes.size should be(6)

      val splitLanes = currentLanes.filter(_.laneCode == 2).sortBy(_.startMeasure)

      splitLanes(0).attributes.foreach {
        laneProp =>
          lanePropertiesValues2.contains(laneProp) should be(true)
      }

      splitLanes(1).attributes.foreach {
        laneProp =>
          newPropertyValues1.contains(laneProp) should be(true)
      }

      splitLanes(2).attributes.foreach {
        laneProp =>
          newPropertyValues2.contains(laneProp) should be(true)
      }

      splitLanes(3).attributes.foreach {
        laneProp =>
          lanePropertiesValues2.contains(laneProp) should be(true)
      }

      splitLanes(4).attributes.foreach {
        laneProp =>
          newPropertyValues2.contains(laneProp) should be(true)
      }

      val expiredLanes = laneHistoryDao.fetchHistoryLanesByLinkIdsAndLaneCode(Seq(linkId1), Seq(2), true)
      expiredLanes.size should be(3)

      when(mockRoadLinkService.getRoadLinksByLinkIds(Set(linkId1), false)).thenReturn(Seq(roadlink1))
      when(mockRoadAddressService.roadLinkWithRoadAddress(Seq(roadlink1))).thenReturn(Seq(roadlink1))

      val dateAtThisMoment = DateTime.now()
      val lanesChanged = ServiceWithDao.getChanged(dateAtThisMoment.minusDays(1), dateAtThisMoment.plusDays(1))

      lanesChanged.size should be(9)

      //There should a change type add for all created lanes.
      lanesChanged.count(laneChange => laneChange.changeType == LaneChangeType.Add) should be(6)
      //There should be a change type attributes changed for the three modified lanes.
      lanesChanged.count(laneChange => laneChange.changeType == LaneChangeType.AttributesChanged) should be(3)

      //Check that the change information matches with the lanes changed.
      val attributesChanged = lanesChanged.filter(_.changeType == LaneChangeType.AttributesChanged).sortBy(_.lane.startMeasure)
      attributesChanged.map(_.lane.startMeasure) should be(Seq(100, 200, 400))
      attributesChanged.map(_.lane.endMeasure) should be(Seq(200, 300, 500))

      attributesChanged.foreach{change =>
        change.oldLane.get.attributes.foreach(laneProp => lanePropertiesValues2.contains(laneProp) should be(true))}

      attributesChanged(0).lane.attributes.foreach(laneProp => newPropertyValues1.contains(laneProp) should be(true))
      attributesChanged(1).lane.attributes.foreach(laneProp => newPropertyValues2.contains(laneProp) should be(true))
      attributesChanged(2).lane.attributes.foreach(laneProp => newPropertyValues2.contains(laneProp) should be(true))
    }
  }

  test("Expire several split lanes") {
    runWithRollback {
      val mainLane1 = NewLane(0, 0, 500, 745, false, false, lanePropertiesValues1)
      val subLane2SplitA = NewLane(0, 0, 100, 745, false, false, lanePropertiesValues2)
      val subLane2SplitB = NewLane(0, 100, 200, 745, false, false, lanePropertiesValues2)
      val subLane2SplitC = NewLane(0, 200, 300, 745, false, false, lanePropertiesValues2)
      val subLane2SplitD = NewLane(0, 300, 400, 745, false, false, lanePropertiesValues2)
      val subLane2SplitE = NewLane(0, 400, 500, 745, false, false, lanePropertiesValues2)
      val laneIds = ServiceWithDao.create(Seq(mainLane1, subLane2SplitA, subLane2SplitB, subLane2SplitC, subLane2SplitD, subLane2SplitE), Set(linkId1), 1, usernameTest)

      val sideCodeForLink100 = SideCodesForLinkIds(linkId1, 1)
      val sideCodesForLinkIds = Seq(sideCodeForLink100)

      val initialLanes = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(linkId1), Seq(1, 2), false)
      initialLanes.size should be(6)

      val createdMainLane = NewLane(laneIds(0), 0, 500, 745, false, false, lanePropertiesValues1)
      val createdSubLane2SplitA = NewLane(laneIds(1), 0, 100, 745, false, false, lanePropertiesValues2)
      val createdSubLane2SplitB = NewLane(laneIds(2), 100, 200, 745, true, false, lanePropertiesValues2)
      val createdSubLane2SplitC = NewLane(laneIds(3), 200, 300, 745, false, false, lanePropertiesValues2)
      val createdSubLane2SplitD = NewLane(laneIds(4), 300, 400, 745, true, false, lanePropertiesValues2)
      val createdSubLane2SplitE = NewLane(laneIds(5), 400, 500, 745, true, false, lanePropertiesValues2)

      ServiceWithDao.processNewLanes(Set(createdMainLane, createdSubLane2SplitA, createdSubLane2SplitB, createdSubLane2SplitC, createdSubLane2SplitD, createdSubLane2SplitE), Set(linkId1), 1, usernameTest, sideCodesForLinkIds)

      val currentLanes = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(linkId1), Seq(1, 2), false)
      currentLanes.size should be(3)

      val currentSplitLanes = currentLanes.filter(_.laneCode == 2).sortBy(_.startMeasure)
      currentSplitLanes.size should be(2)

      currentSplitLanes(0).startMeasure should be(0)
      currentSplitLanes(0).endMeasure should be(100)
      currentSplitLanes(1).startMeasure should be(200)
      currentSplitLanes(1).endMeasure should be(300)

      val expiredLanes = laneHistoryDao.fetchHistoryLanesByLinkIdsAndLaneCode(Seq(linkId1), Seq(2), true)
      expiredLanes.size should be(3)

      when(mockRoadLinkService.getRoadLinksByLinkIds(Set(linkId1), false)).thenReturn(Seq(roadlink1))
      when(mockRoadAddressService.roadLinkWithRoadAddress(Seq(roadlink1))).thenReturn(Seq(roadlink1))

      val dateAtThisMoment = DateTime.now()
      val lanesChanged = ServiceWithDao.getChanged(dateAtThisMoment.minusDays(1), dateAtThisMoment.plusDays(1))

      lanesChanged.size should be(9)

      //There should a change type add for all created lanes.
      lanesChanged.count(laneChange => laneChange.changeType == LaneChangeType.Add) should be(6)
      //There should be a change type expired for the three expired lanes.
      lanesChanged.count(laneChange => laneChange.changeType == LaneChangeType.Expired) should be(3)

      //Check that the measures of the expired lanes are correct in the change message.
      val expired = lanesChanged.filter(_.changeType == LaneChangeType.Expired).sortBy(_.lane.startMeasure)
      expired.map(_.lane.startMeasure) should be(Seq(100, 300, 400))
      expired.map(_.lane.endMeasure) should be(Seq(200, 400, 500))
    }
  }

  test("Test deleted and shortened splits") {
    runWithRollback {
      val mainLane1 = NewLane(0, 0, 500, 745, false, false, lanePropertiesValues1)
      val subLane2 = NewLane(0, 0, 500, 745, false, false, lanePropertiesValues2)
      val mainLane1Id = ServiceWithDao.create(Seq(mainLane1), Set(linkId1), 1, usernameTest).head
      val subLane2Id = ServiceWithDao.create(Seq(subLane2), Set(linkId1), 1, usernameTest)

      val sideCodeForLink100 = SideCodesForLinkIds(linkId1, 1)
      val sideCodesForLinkIds = Seq(sideCodeForLink100)

      val initialLanes = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(linkId1), Seq(1, 2), false)
      initialLanes.size should be(2)

      val createdMainLane = NewLane(mainLane1Id, 0, 500, 745, false, false, lanePropertiesValues1)

      val subLane2SplitA = NewLane(0, 0, 150, 745, false, false, lanePropertiesValues2)
      val subLane2SplitB = NewLane(0, 150, 350, 745, false, false, lanePropertiesValues2)

      ServiceWithDao.processNewLanes(Set(createdMainLane, subLane2SplitA, subLane2SplitB), Set(linkId1), 1, usernameTest, sideCodesForLinkIds)

      val subLane2SplitBs = NewLane(0, 150, 300, 745, false, false, lanePropertiesValues2)

      ServiceWithDao.processNewLanes(Set(createdMainLane, subLane2SplitA, subLane2SplitBs), Set(linkId1), 1, usernameTest, sideCodesForLinkIds)

      val currentLanes = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(linkId1), Seq(1, 2), false)
      currentLanes.size should be(3)

      val lane1 = currentLanes.filter(_.id == mainLane1Id).head
      lane1.id should be(mainLane1Id)
      lane1.attributes.foreach { laneProp =>
        lanePropertiesValues1.contains(laneProp) should be(true)
      }

      val splitLanes = currentLanes.filter(_.laneCode == 2).sortBy(_.startMeasure)
      splitLanes.size should be(2)

      splitLanes(0).startMeasure should be(0)
      splitLanes(0).endMeasure should be(150)
      splitLanes(1).startMeasure should be(150)
      splitLanes(1).endMeasure should be(300)

      splitLanes.foreach { lane =>
        lane.attributes.foreach {
          laneProp =>
            lanePropertiesValues2.contains(laneProp) should be(true)
        }
      }
      when(mockRoadLinkService.getRoadLinksByLinkIds(Set(linkId1), false)).thenReturn(Seq(roadlink1))
      when(mockRoadAddressService.roadLinkWithRoadAddress(Seq(roadlink1))).thenReturn(Seq(roadlink1))

      val dateAtThisMoment = DateTime.now()
      val laneChanges = ServiceWithDao.getChanged(dateAtThisMoment.minusDays(1), dateAtThisMoment.plusDays(1))
      /*The change type for the main lane and the original additional lane is add. When the additional lane is split, there
      will be three divided messages, two for remaining pieces and one for the removed part, and an explicit
      expire message for the removed part. Moreover, there will be two divided and one expire for the second split*/
      laneChanges.size should be(9)

      laneChanges.count(_.changeType == LaneChangeType.Add) should be(2)
      laneChanges.count(_.changeType == LaneChangeType.Divided) should be(5)
      laneChanges.count(_.changeType == LaneChangeType.Expired) should be(2)

      //Check that measures and old lane ids are correct.
      val divided = laneChanges.filter(_.changeType == LaneChangeType.Divided).sortBy(c => (c.lane.startMeasure, c.lane.endMeasure))
      divided(0).lane.startMeasure should be(0)
      divided(0).lane.endMeasure should be(150)
      divided(1).lane.startMeasure should be(150)
      divided(1).lane.endMeasure should be(300)
      divided(2).lane.startMeasure should be(150)
      divided(2).lane.endMeasure should be(350)
      divided(3).lane.startMeasure should be(300)
      divided(3).lane.endMeasure should be(350)
      divided(4).lane.startMeasure should be(350)
      divided(4).lane.endMeasure should be(500)

      val expired = laneChanges.filter(_.changeType == LaneChangeType.Expired).sortBy(_.lane.startMeasure)
      expired(0).lane.startMeasure should be(300)
      expired(0).lane.endMeasure should be(350)
      expired(1).lane.startMeasure should be(350)
      expired(1).lane.endMeasure should be(500)
    }
  }

  test("Split existing lane in three pieces and remove the middle part") {
    runWithRollback {
      val mainLane1 = NewLane(0, 0, 500, 745, false, false, lanePropertiesValues1)
      val subLane2 = NewLane(0, 0, 500, 745, false, false, lanePropertiesValues2)
      val mainLane1Id = ServiceWithDao.create(Seq(mainLane1), Set(linkId1), 1, usernameTest).head
      val sublane2Id = ServiceWithDao.create(Seq(subLane2), Set(linkId1), 1, usernameTest)

      val sideCodeForLink100 = SideCodesForLinkIds(linkId1, 1)
      val sideCodesForLinkIds = Seq(sideCodeForLink100)

      val initialLanes = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(linkId1), Seq(1, 2), false)
      initialLanes.size should be(2)

      val createdMainLane = NewLane(mainLane1Id, 0, 500, 745, false, false, lanePropertiesValues1)

      val subLane2SplitA = NewLane(0, 0, 150, 745, false, false, lanePropertiesValues2)
      val subLane2SplitB = NewLane(0, 350, 500, 745, false, false, lanePropertiesValues2)

      ServiceWithDao.processNewLanes(Set(createdMainLane, subLane2SplitA, subLane2SplitB), Set(linkId1), 1, usernameTest, sideCodesForLinkIds)

      val currentLanes = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(linkId1), Seq(1, 2), false)
      currentLanes.size should be(3)

      val lane1 = currentLanes.filter(_.id == mainLane1Id).head
      lane1.id should be(mainLane1Id)
      lane1.attributes.foreach { laneProp =>
        lanePropertiesValues1.contains(laneProp) should be(true)
      }

      val splitLanes = currentLanes.filter(_.laneCode == 2).sortBy(_.startMeasure)
      splitLanes.size should be(2)

      splitLanes(0).startMeasure should be(0)
      splitLanes(0).endMeasure should be(150)
      splitLanes(1).startMeasure should be(350)
      splitLanes(1).endMeasure should be(500)

      splitLanes.foreach { lane =>
        lane.attributes.foreach {
          laneProp =>
            lanePropertiesValues2.contains(laneProp) should be(true)
        }
      }

      // Three expired parts for the division of the big lane and one that is linked to the expired piece.
      val expiredLanes = laneHistoryDao.fetchHistoryLanesByLinkIdsAndLaneCode(Seq(linkId1), Seq(2), true)
      expiredLanes.size should be(4)

      when(mockRoadLinkService.getRoadLinksByLinkIds(Set(linkId1), false)).thenReturn(Seq(roadlink1))
      when(mockRoadAddressService.roadLinkWithRoadAddress(Seq(roadlink1))).thenReturn(Seq(roadlink1))

      val dateAtThisMoment = DateTime.now()
      val laneChanges = ServiceWithDao.getChanged(dateAtThisMoment.minusDays(1), dateAtThisMoment.plusDays(1))

      /*The change type for the main lane and the additional lane saved before split is add and the type for split lanes is divided.
      In addition, there is an explicit expire message for the removed part*/
      laneChanges.size should be(6)
      laneChanges.count(_.changeType == LaneChangeType.Add) should be(2)
      laneChanges.count(_.changeType == LaneChangeType.Divided) should be(3)
      laneChanges.count(_.changeType == LaneChangeType.Expired) should be(1)

      val divided = laneChanges.filter(_.changeType == LaneChangeType.Divided).sortBy(_.lane.startMeasure)
      divided(0).lane.startMeasure should be(0)
      divided(0).lane.endMeasure should be(150)
      divided(1).lane.startMeasure should be(150)
      divided(1).lane.endMeasure should be(350)
      divided(2).lane.startMeasure should be(350)
      divided(2).lane.endMeasure should be(500)

      divided.foreach(laneChange => laneChange.oldLane.get.id should be(sublane2Id.head))

      val expired = laneChanges.filter(_.changeType == LaneChangeType.Expired).head

      expired.lane.startMeasure should be(150)
      expired.lane.endMeasure should be(350)
    }
  }

  test("Create and expire lane with passed end date immediately") {
    runWithRollback {
      val mainLane1 = NewLane(0, 0, 500, 745, false, false, lanePropertiesValues1)
      val subLane2 = NewLane(0, 0, 500, 745, false, false, lanePropertiesPassedEndDate)


      //create lane main lane 1
      val mainLane1Id = ServiceWithDao.create(Seq(mainLane1), Set(linkId1), 1, usernameTest).head

      val sideCodesForLinkIds = Seq(SideCodesForLinkIds(linkId1, 1))

      //Validate if initial lanes are correctly created
      val currentLanes = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(linkId1), Seq(1), false)
      currentLanes.size should be(1)

      val lane1 = currentLanes.filter(_.id == mainLane1Id).head
      lane1.id should be(mainLane1Id)
      lane1.attributes.foreach { laneProp =>
        lanePropertiesValues1.contains(laneProp) should be(true)
      }

      //Send created main lane and new lane 2 which has passed end date
      val currentMainLane1 = mainLane1.copy(id = mainLane1Id)
      ServiceWithDao.processNewLanes(Set(currentMainLane1, subLane2), Set(linkId1), 1, usernameTest, sideCodesForLinkIds)

      val lanesAfterLane2CreationAndExpire = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(linkId1), Seq(1, 2), false)
      lanesAfterLane2CreationAndExpire.size should be(1)

      val lane1After = lanesAfterLane2CreationAndExpire.filter(_.id == mainLane1Id).head
      lane1After.id should be(mainLane1Id)
      lane1After.attributes.foreach { laneProp =>
        lanePropertiesValues1.contains(laneProp) should be(true)
      }

      //Verify that lane2 was created and moved to history
      val historyLanes = laneHistoryDao.fetchHistoryLanesByLinkIdsAndLaneCode(Seq(linkId1), Seq(1, 2), true)
      historyLanes.size should be(1)

      val historyLane2 = historyLanes.head
      historyLane2.newId should be (0)
      historyLane2.startMeasure should be(0)
      historyLane2.endMeasure should be(500)
      historyLane2.expired should be(true)
      historyLane2.attributes.foreach { laneProp =>
        lanePropertiesPassedEndDate.contains(laneProp) should be(true)

      }
    }
  }

  test("Existing additional lane should be expired after end date is set to past") {
    runWithRollback {
      val mainLane1 = NewLane(0, 0, 500, 745, false, false, lanePropertiesValues1)
      val subLane2 = NewLane(0, 0, 500, 745, false, false, lanePropertiesValues2)


      //create lane main lane 1 and sub lane 2
      val mainLane1Id = ServiceWithDao.create(Seq(mainLane1), Set(linkId1), 1, usernameTest).head
      val subLane2Id = ServiceWithDao.create(Seq(subLane2), Set(linkId1), 1, usernameTest).head

      val sideCodesForLinkIds = Seq(SideCodesForLinkIds(linkId1, 1))

      //Validate if initial lanes are correctly created
      val currentLanes = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(linkId1), Seq(1,2), false)
      currentLanes.size should be(2)

      val lane1 = currentLanes.filter(_.id == mainLane1Id).head
      lane1.id should be(mainLane1Id)
      lane1.attributes.foreach { laneProp =>
        lanePropertiesValues1.contains(laneProp) should be(true)
      }

      val lane2 = currentLanes.filter(_.id == subLane2Id).head
      lane2.id should be(subLane2Id)
      lane2.attributes.foreach { laneProp =>
        lanePropertiesValues2.contains(laneProp) should be(true)
      }

      //Send created main lane and updated lane 2 which has passed end date
      val currentMainLane1 = mainLane1.copy(id = mainLane1Id)
      val currentSubLane2 = subLane2.copy(id = subLane2Id, properties = lanePropertiesPassedEndDate)
      ServiceWithDao.processNewLanes(Set(currentMainLane1, currentSubLane2), Set(linkId1), 1, usernameTest, sideCodesForLinkIds)

      val lanesAfterLane2CreationAndExpire = laneDao.fetchLanesByLinkIdsAndLaneCode(Seq(linkId1), Seq(1, 2), false)
      lanesAfterLane2CreationAndExpire.size should be(1)

      val lane1After = lanesAfterLane2CreationAndExpire.filter(_.id == mainLane1Id).head
      lane1After.id should be(mainLane1Id)
      lane1After.attributes.foreach { laneProp =>
        lanePropertiesValues1.contains(laneProp) should be(true)
      }

      //Verify that lane2 was created and moved to history
      val historyLanes = laneHistoryDao.fetchHistoryLanesByLinkIdsAndLaneCode(Seq(linkId1), Seq(1, 2), true)
      historyLanes.size should be(2)

      //Two history lanes created, one for attribute update, one for expiring by end date
      val historyLane2Update = historyLanes.find(lane => lane.oldId == subLane2Id && !lane.expired).get
      val historyLane2Expire = historyLanes.find(lane => lane.oldId == subLane2Id && lane.expired).get

      historyLane2Update.newId should be (0)
      historyLane2Update.startMeasure should be(0)
      historyLane2Update.endMeasure should be(500)
      historyLane2Update.expired should be(false)
      historyLane2Update.attributes.foreach { laneProp =>
        lanePropertiesValues2.contains(laneProp) should be(true)
      }

      historyLane2Expire.newId should be (0)
      historyLane2Expire.startMeasure should be(0)
      historyLane2Expire.endMeasure should be(500)
      historyLane2Expire.expired should be(true)
      historyLane2Expire.attributes.foreach { laneProp =>
        lanePropertiesPassedEndDate.contains(laneProp) should be(true)
      }

    }
  }

}