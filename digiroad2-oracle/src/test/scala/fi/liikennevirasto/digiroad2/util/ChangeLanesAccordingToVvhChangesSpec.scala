package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.ConstructionType.InUse
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, Municipality, Unknown, SingleCarriageway, TractorRoad}
import fi.liikennevirasto.digiroad2.asset.TrafficDirection.BothDirections
import fi.liikennevirasto.digiroad2.client.vvh.ChangeInfo
import fi.liikennevirasto.digiroad2.lane.{LaneProperty, LanePropertyValue, PersistedLane}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.util.ChangeLanesAccordingToVvhChanges.{handleChanges, roadLinkService}
import org.scalatest.{FunSuite, Matchers}

class ChangeLanesAccordingToVvhChangesSpec extends FunSuite with Matchers{

  def getChangesForTest1: Seq[ChangeInfo] = {
    val change1 = ChangeInfo(Some(1785786),Some(12626035),1340062600,1,Some(0.0),Some(24.46481088),Some(3.75475214),Some(25.72055144),1648076424000L)
    val change2a = ChangeInfo(Some(1785785),Some(12626035),1340062600,2,Some(0.0),Some(23.56318194),Some(25.72055144),Some(49.28373338),1648076424000L)
    val change2b = ChangeInfo(Some(1785787),Some(12626035),1340062600,2,Some(0.0),Some(26.67487965),Some(3.75475214),Some(25.72055144), 1648076424000L)
    Seq(change1, change2a, change2b)
  }

  def getRoadLinksForTest1: Seq[RoadLink] = {
    val linkGeometry1 = Seq(Point(408965.077,7526198.366,198.62600000000384),Point(408962.127,7526203.399,198.8929999999964),Point(408958.656,7526212.77,199.07600000000093),
      Point(408958.067,7526222.646,199.32200000000012),Point(408960.03,7526234.58,199.68499999999767),Point(408959.826,7526246.047,200.24499999999534))
    val roadLink1 = RoadLink(12626035, linkGeometry1, 49.283733382337545, Municipality, 5, BothDirections, SingleCarriageway, Some("24.03.2022 01:16:41"), Some("automatic_generation"),
      Map(), InUse, NormalLinkInterface, List())
    Seq(roadLink1)
  }

  def getLanesForTest1: Seq[PersistedLane] = {
    //MainLanes on link 1785785
    val lane1 = PersistedLane(28355014, 1785785, 2, 1, 261, 0.0, 23.563, Some("auto_generated_lane"), None, None, None, None, None, false,
      1636588800000L, None, Seq(LaneProperty("lane_type", Seq(LanePropertyValue(1))), LaneProperty("lane_code", Seq(LanePropertyValue(1)))))
    val lane2 = PersistedLane(28355017, 1785785, 3, 1, 261, 0.0, 23.563, Some("auto_generated_lane"), None, None, None, None, None, false,
      1636588800000L, None, Seq(LaneProperty("lane_type", Seq(LanePropertyValue(1))), LaneProperty("lane_code", Seq(LanePropertyValue(1)))))

    //MainLanes on link 1785786
    val lane3 = PersistedLane(28355020, 1785786, 2, 1, 261, 0.0, 24.465, Some("auto_generated_lane"), None, None, None, None, None, false,
      1636588800000L, None, Seq(LaneProperty("lane_type", Seq(LanePropertyValue(1))), LaneProperty("lane_code", Seq(LanePropertyValue(1)))))
    val lane4 = PersistedLane(28355023, 1785786, 2, 1, 261, 0.0, 24.465, Some("auto_generated_lane"), None, None, None, None, None, false,
      1636588800000L, None, Seq(LaneProperty("lane_type", Seq(LanePropertyValue(1))), LaneProperty("lane_code", Seq(LanePropertyValue(1)))))

    //MainLanes on link 1785787
    val lane5 = PersistedLane(28355026, 1785787, 2, 1, 261, 0.0, 26.675, Some("auto_generated_lane"), None, None, None, None, None, false,
      1636588800000L, None, Seq(LaneProperty("lane_type", Seq(LanePropertyValue(1))), LaneProperty("lane_code", Seq(LanePropertyValue(1)))))
    val lane6 = PersistedLane(28355029, 1785787, 3, 1, 261, 0.0, 26.675, Some("auto_generated_lane"), None, None, None, None, None, false,
      1636588800000L, None, Seq(LaneProperty("lane_type", Seq(LanePropertyValue(1))), LaneProperty("lane_code", Seq(LanePropertyValue(1)))))

    Seq(lane1, lane2, lane3, lane4, lane5, lane6)
  }

  def getChangesForTest2: Seq[ChangeInfo] = {
    val change3 = ChangeInfo(Some(6170239), Some(6170239), 63590222, 3, Some(0.0), Some(203.35672628), Some(98.47743301), Some(301.83415929), 1648162836000L)
    val change4 = ChangeInfo(None, Some(6170239), 63590222, 4, None, None, Some(0.0), Some(98.47743301), 1648162836000L)

    Seq(change3, change4)
  }

  def getRoadLinksForTest2: Seq[RoadLink] = {
    val linkGeometry = Seq(Point(523728.115,6711284.616,5.875),
      Point(523770.568,6711310.839,6.028000000005704),
      Point(523811.603,6711336.839,6.076000000000931),
      Point(523861.672,6711368.942,6.101999999998952),
      Point(523900.402,6711392.305,6.2519999999931315),
      Point(523911.72,6711399.241,6.495999999999185),
      Point(523929.303,6711411.527,6.926000000006752),
      Point(523938.893,6711420.194,7.282999999995809),
      Point(523946.177,6711428.966,7.5350000000034925),
      Point(523953.841,6711437.694,7.614000000001397),
      Point(523961.295,6711452.731,8.430999999996857),
      Point(523965.311,6711463.184,7.752999999996973))
    val roadLink = RoadLink(6170239, linkGeometry, 301.834159290816, Unknown, 7, BothDirections, TractorRoad, Some("25.03.2022 01:00:36"), Some("vvh_modified"), Map(), InUse, NormalLinkInterface,
      Seq())

    Seq(roadLink)
  }

  def getLanesForTest2: Seq[PersistedLane] = {
    val mainLane = PersistedLane(101, 6170239, 1, 1, 935, 0.0, 203.357, Some("auto_generated_lane"), None, None, None, None, None, false,
      1636588800000L, None, Seq(LaneProperty("lane_type", Seq(LanePropertyValue(1))), LaneProperty("lane_code", Seq(LanePropertyValue(1)))))

    val lane2FullLength = PersistedLane(102, 6170239, 1, 2, 935, 0.0, 203.357, Some("auto_generated_lane"), None, None, None, None, None, false,
      1636588800000L, None, Seq(LaneProperty("lane_type", Seq(LanePropertyValue(1))), LaneProperty("lane_code", Seq(LanePropertyValue(2)))))

    val lane3CutFromEnd = PersistedLane(103, 6170239, 1, 3, 935, 0.0, 150.50, Some("auto_generated_lane"), None, None, None, None, None, false,
      1636588800000L, None, Seq(LaneProperty("lane_type", Seq(LanePropertyValue(1))), LaneProperty("lane_code", Seq(LanePropertyValue(3)))))

    val lane4CutFromStart = PersistedLane(104, 6170239, 1, 4, 935, 120.25, 203.357, Some("auto_generated_lane"), None, None, None, None, None, false,
      1636588800000L, None, Seq(LaneProperty("lane_type", Seq(LanePropertyValue(1))), LaneProperty("lane_code", Seq(LanePropertyValue(4)))))
    Seq(mainLane, lane2FullLength, lane3CutFromEnd, lane4CutFromStart)
  }

  def getChangesForTest3: Seq[ChangeInfo] = {
    val change5 = ChangeInfo(Some(5452255), Some(12628125), 517006310, 5, Some(0.0), Some(138.29966444), Some(0.0), Some(138.29966471), 1648162836000L)
    val change6 = ChangeInfo(Some(5452255), Some(12628139), 517006310, 6, Some(381.82675568), Some(651.79968351), Some(0.0), Some(269.97292782), 1648162836000L)

    Seq(change5, change6)
  }

  def getRoadLinksForTest3: Seq[RoadLink] = {
    val link1Geometry = Seq(Point(435178.441, 6827706.385, 84.46499999999651),
      Point(435157.084, 6827716.962, 84.82300000000396),
      Point(435136.866, 6827727.225, 84.91800000000512),
      Point(435100.259, 6827744.579, 85.6079999999929),
      Point(435069.111, 6827759.41, 84.56699999999546),
      Point(435053.691, 6827766.034, 84.18799999999464))
    val link2Geometry = Seq(Point(434808.369, 6827803.319, 80.86900000000605),
      Point(434838.612, 6827797.323, 81.24499999999534),
      Point(434831.54, 6827797.807, 80.63999999999942),
      Point(434824.62, 6827798.623, 80.77000000000407),
      Point(434786.487, 6827809.795, 80.93600000000151),
      Point(434763.48, 6827817.239, 81.24599999999919),
      Point(434745.46, 6827823.064, 81.30899999999383),
      Point(434726.961, 6827830.823, 81.77099999999336),
      Point(434713.124, 6827835.353, 82.37900000000081),
      Point(434704.44, 6827840.519, 83.06900000000314),
      Point(434696.887, 6827848.421, 84.21499999999651),
      Point(434686.442, 6827859.707, 86.24400000000605),
      Point(434678.251, 6827870.99, 87.78200000000652),
      Point(434668.133, 6827885.174, 89.2100000000064),
      Point(434659.779, 6827894.525, 90.23600000000442),
      Point(434652.064, 6827901.782, 90.72699999999895),
      Point(434646.926, 6827910.001, 90.68700000000536),
      Point(434635.366, 6827928.373, 92.4719999999943),
      Point(434628.905, 6827933.16, 92.56900000000314),
      Point(434622.44, 6827938.025, 92.89299999999639))

    val link1 = RoadLink(12628125,link1Geometry, 138.29966470656584, Unknown, 7, BothDirections, TractorRoad,
      Some("25.03.2022 01:00:36"), Some("vvh_modified"), Map(), InUse, NormalLinkInterface, Seq())

    val link2 = RoadLink(12628139, link2Geometry, 269.9729278270251, Unknown, 7, BothDirections, TractorRoad,
      Some("25.03.2022 00:53:10"), Some("automatic_generation"), Map(), InUse, NormalLinkInterface, Seq())

    Seq(link1, link2)
  }

  def getLanesForTest3: Seq[PersistedLane] = {
    val mainLane = PersistedLane(101, 5452255, 1, 1, 781, 0.0, 651.8, Some("auto_generated_lane"), None, None, None, None, None,
      false, 1636588800000L, None, Seq(LaneProperty("lane_type", Seq(LanePropertyValue(1))), LaneProperty("lane_code", Seq(LanePropertyValue(1)))))
    val lane2Cut = PersistedLane(102, 5452255, 1, 2, 781, 50.75, 150.61, Some("auto_generated_lane"), None, None, None, None, None,
      false, 1636588800000L, None, Seq(LaneProperty("lane_type", Seq(LanePropertyValue(1))), LaneProperty("lane_code", Seq(LanePropertyValue(1)))))

    Seq(mainLane, lane2Cut)
  }


  test("Three links combine into one"){
    val roadLinks = getRoadLinksForTest1
    val changes = getChangesForTest1
    val existingLanes = getLanesForTest1
    val historyLinks = roadLinkService.getHistoryDataLinksFromVVH(existingLanes.map(_.linkId).toSet)
    val latestHistoryLinks = historyLinks.groupBy(_.linkId).map(_._2.maxBy(_.vvhTimeStamp)).toSeq
    val (changeSet, modifiedLanes) = handleChanges(roadLinks, latestHistoryLinks, changes, existingLanes)
    changeSet.expiredLaneIds should contain(28355014)
    changeSet.expiredLaneIds should contain(28355017)
    changeSet.expiredLaneIds should contain(28355020)
    changeSet.expiredLaneIds should contain(28355023)
    changeSet.expiredLaneIds should contain(28355026)
    changeSet.expiredLaneIds should contain(28355029)
    changeSet.expiredLaneIds.size should equal(6)
    changeSet.generatedPersistedLanes.size should equal(2)
    changeSet.generatedPersistedLanes.head.startMeasure should equal(0)
    changeSet.generatedPersistedLanes.head.endMeasure should equal(49.283733382337545)
  }




  test("Link lengthened"){
    val roadLinks = getRoadLinksForTest2
    val changes = getChangesForTest2
    val existingLanes = getLanesForTest2
    val historyLinks = roadLinkService.getHistoryDataLinksFromVVH(existingLanes.map(_.linkId).toSet)
    val latestHistoryLinks = historyLinks.groupBy(_.linkId).map(_._2.maxBy(_.vvhTimeStamp)).toSeq
    val (changeSet, modifiedLanes) = handleChanges(roadLinks, latestHistoryLinks, changes, existingLanes)


    modifiedLanes.size should equal(0)
    changeSet.expiredLaneIds.size should equal(0)
    changeSet.adjustedMValues.size should equal(0)
    changeSet.generatedPersistedLanes.size should equal(0)
    changeSet.adjustedSideCodes.size should equal(0)
    changeSet.adjustedVVHChanges.size should equal(4)

    val mainLaneVvhAdjustment = changeSet.adjustedVVHChanges.find(_.linkId == 101).get
    val lane2FullLengthVvhAdjustment = changeSet.adjustedVVHChanges.find(_.linkId == 102).get
    val lane3CutVvhAdjustment = changeSet.adjustedVVHChanges.find(_.linkId == 103).get
    val lane4CutVvhAdjustment = changeSet.adjustedVVHChanges.find(_.linkId == 104).get

    mainLaneVvhAdjustment.startMeasure should equal(0.0)
    mainLaneVvhAdjustment.endMeasure should equal(301.83415929)

    lane2FullLengthVvhAdjustment.startMeasure should equal(0.0)
    lane2FullLengthVvhAdjustment.endMeasure should equal(301.83415929)

    lane3CutVvhAdjustment.startMeasure should equal(0.0)
    lane3CutVvhAdjustment.endMeasure should equal(150.50)

    lane4CutVvhAdjustment.startMeasure should equal(120.25)
    lane4CutVvhAdjustment.endMeasure should equal(203.357)





  }


  test("Link divided into two"){
    val roadLinks = getRoadLinksForTest3
    val changes = getChangesForTest3
    val existingLanes = getLanesForTest3
    val historyLinks = roadLinkService.getHistoryDataLinksFromVVH(existingLanes.map(_.linkId).toSet)
    val latestHistoryLinks = historyLinks.groupBy(_.linkId).map(_._2.maxBy(_.vvhTimeStamp)).toSeq
    val (changeSet, modifiedLanes) = handleChanges(roadLinks, latestHistoryLinks, changes, existingLanes)
    println("jee")
  }

  test("Link shortened"){

  }

  test("Link replaced with longer"){

  }

  test("Link replaced with shorter"){

  }
}
