package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.ConstructionType.InUse
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, Municipality, SingleCarriageway, State, TractorRoad, Unknown}
import fi.liikennevirasto.digiroad2.asset.TrafficDirection.BothDirections
import fi.liikennevirasto.digiroad2.client.vvh.ChangeInfo
import fi.liikennevirasto.digiroad2.lane.{LaneProperty, LanePropertyValue, PersistedLane}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.util.ChangeLanesAccordingToVvhChanges.{handleChanges, roadLinkService}
import org.scalatest.{FunSuite, Matchers}

class ChangeLanesAccordingToVvhChangesSpec extends FunSuite with Matchers{

  def areMeasuresCloseEnough(measure1: Double, measure2: Double): Boolean ={
    val acceptableInaccuracy = 1
    val difference = math.abs(measure2 - measure1)
    if(difference < acceptableInaccuracy) true
    else false
  }

  def generateTestLane(id: Int, linkId: Int, sideCode: Int, laneCode: Int, startM: Double, endM: Double, vvhTimestamp: Long): PersistedLane = {
    val attributes = Seq(LaneProperty("lane_type", Seq(LanePropertyValue(1))), LaneProperty("lane_code", Seq(LanePropertyValue(laneCode))))
    PersistedLane(id, linkId, sideCode, laneCode, 999, startM, endM, None, None, None, None, None, None, false, vvhTimestamp, None, attributes)
  }

  val changesForTest1: Seq[ChangeInfo] = {
    val change1 = ChangeInfo(Some(1785786),Some(12626035),1340062600,1,Some(0.0),Some(24.46481088),Some(3.75475214),Some(25.72055144),1648076424000L)
    val change2a = ChangeInfo(Some(1785785),Some(12626035),1340062600,2,Some(0.0),Some(23.56318194),Some(25.72055144),Some(49.28373338),1648076424000L)
    val change2b = ChangeInfo(Some(1785787),Some(12626035),1340062600,2,Some(0.0),Some(26.67487965),Some(3.75475214),Some(25.72055144), 1648076424000L)
    Seq(change1, change2a, change2b)
  }

  val roadLinksForTest1: Seq[RoadLink] = {
    val linkGeometry1 = Seq(Point(408965.077,7526198.366,198.62600000000384),Point(408962.127,7526203.399,198.8929999999964),Point(408958.656,7526212.77,199.07600000000093),
      Point(408958.067,7526222.646,199.32200000000012),Point(408960.03,7526234.58,199.68499999999767),Point(408959.826,7526246.047,200.24499999999534))
    val roadLink1 = RoadLink(12626035, linkGeometry1, 49.283733382337545, Municipality, 5, BothDirections, SingleCarriageway, Some("24.03.2022 01:16:41"), Some("automatic_generation"),
      Map(), InUse, NormalLinkInterface, List())
    Seq(roadLink1)
  }

  val lanesForTest1: Seq[PersistedLane] = {
    //MainLanes on link 1785785
    val lane1 = generateTestLane(201, 1785785, 2, 1 , 0.0, 23.563, 1636588800000L)
    val lane2 = generateTestLane(301, 1785785, 3, 1 , 0.0, 23.563, 1636588800000L)

    //MainLanes on link 1785786
    val lane3 = generateTestLane(211, 1785786, 2, 1 , 0.0, 24.465, 1636588800000L)
    val lane4 = generateTestLane(311, 1785786, 3, 1 , 0.0, 24.465, 1636588800000L)

    //MainLanes on link 1785787
    val lane5 = generateTestLane(221, 1785787, 2, 1 , 0.0, 26.675, 1636588800000L)
    val lane6 = generateTestLane(321, 1785786, 3, 1 , 0.0, 26.675, 1636588800000L)

    Seq(lane1, lane2, lane3, lane4, lane5, lane6)
  }

  val changesForTest2: Seq[ChangeInfo] = {
    val change3 = ChangeInfo(Some(6170239), Some(6170239), 63590222, 3, Some(0.0), Some(203.35672628), Some(98.47743301), Some(301.83415929), 1648162836000L)
    val change4 = ChangeInfo(None, Some(6170239), 63590222, 4, None, None, Some(0.0), Some(98.47743301), 1648162836000L)

    Seq(change3, change4)
  }

  val roadLinksForTest2: Seq[RoadLink] = {
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

  val lanesForTest2: Seq[PersistedLane] = {
    val mainLane = generateTestLane(101, 6170239, 1, 1, 0.0, 203.357, 1636588800000L)
    val lane2FullLength = generateTestLane(102, 6170239, 1, 2, 0.0, 203.357, 1636588800000L)
    val lane3CutFromEnd = generateTestLane(103, 6170239, 1, 3, 0.0, 150.50, 1636588800000L)
    val lane4CutFromStart = generateTestLane(104, 6170239, 1, 4, 120.25, 203.357, 1636588800000L)
    Seq(mainLane, lane2FullLength, lane3CutFromEnd, lane4CutFromStart)
  }

  val changesForTest3: Seq[ChangeInfo] = {
    val change5 = ChangeInfo(Some(5452255), Some(12628125), 517006310, 5, Some(0.0), Some(138.29966444), Some(0.0), Some(138.29966471), 1648162836000L)
    val change6 = ChangeInfo(Some(5452255), Some(12628139), 517006310, 6, Some(381.82675568), Some(651.79968351), Some(0.0), Some(269.97292782), 1648162836000L)

    Seq(change5, change6)
  }

  val roadLinksForTest3: Seq[RoadLink] = {
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

  val lanesForTest3: Seq[PersistedLane] = {
    val mainLane = generateTestLane(101, 5452255, 1, 1, 0.0, 651.8, 1636588800000L)
    //Additional lane cut from start and end
    val lane2Cut = generateTestLane(102, 5452255, 1, 2, 50.75, 150.61, 1636588800000L)
    //Additional lane cut from start and end
    val lane3Cut = generateTestLane(103, 5452255, 1, 3, 50.75, 530.75, 1636588800000L)

    Seq(mainLane, lane2Cut, lane3Cut)
  }

  val changesForTest4: Seq[ChangeInfo] = {
    val change7 = ChangeInfo(Some(56731), Some(56731), 362451385, 7, Some(7.21549736), Some(315.93394668), Some(0.0),
      Some(313.48187171), 1648832434000L)
    val change8a = ChangeInfo(Some(56731), None, 362451385, 8, Some(0.0), Some(7.21549736), None, None, 1648832434000L)
    val change8b = ChangeInfo(Some(56731), None, 362451385, 8, Some(315.93394668), Some(317.9947522), None, None, 1648832434000L)

    Seq(change7, change8a, change8b)
  }

  val roadLinksForTest4: Seq[RoadLink] = {
    val linkGeom = Seq(Point(418434.814, 6784823.753, 156.54300000000512),
      Point(418432.435, 6784829.281, 156.41999999999825),
      Point(418432.075, 6784836.297, 156.19700000000012),
      Point(418434.151, 6784843.73, 155.87200000000303),
      Point(418435.819, 6784852.713, 155.97999999999593),
      Point(418435.145, 6784859.158, 155.9149999999936),
      Point(418430.25, 6784867.295, 155.93300000000454),
      Point(418422.718, 6784879.089, 155.63300000000163),
      Point(418411.327, 6784887.241, 155.56200000000536),
      Point(418398.64, 6784896.864, 155.77700000000186),
      Point(418387.176, 6784902.845, 156.28399999999965),
      Point(418376.0, 6784907.664, 156.26600000000326),
      Point(418364.536, 6784913.499, 155.5920000000042),
      Point(418352.503, 6784923.582, 154.95799999999872),
      Point(418346.693, 6784934.418, 154.32799999999406),
      Point(418340.751, 6784947.355, 153.56699999999546),
      Point(418327.483, 6784965.219, 152.97699999999895),
      Point(418321.044, 6784969.98, 153.0969999999943),
      Point(418315.036, 6784978.051, 153.50400000000081),
      Point(418304.479, 6785002.03, 152.76900000000023),
      Point(418300.585, 6785013.065, 152.50199999999313),
      Point(418298.649, 6785028.485, 152.1640000000043),
      Point(418300.558, 6785037.719, 151.8570000000036),
      Point(418304.122, 6785048.115, 151.85499999999593),
      Point(418304.138, 6785054.985, 151.86699999999837),
      Point(418301.536, 6785062.14, 152.14500000000407),
      Point(418297.99, 6785076.53, 152.80299999999988))

    val roadLink = RoadLink(56731, linkGeom, 313.48187170953395, Unknown, 7, BothDirections, TractorRoad, Some("01.04.2022 20:00:34"),
      None, Map(), InUse, NormalLinkInterface, List())

    Seq(roadLink)
  }

  val lanesForTest4: Seq[PersistedLane] = {
    val mainLane = generateTestLane(101, 56731, 1, 1, 0.0, 317.995, 1636588800000L)
    //Additional lane cut from start and end
    val additionalLane2 = generateTestLane(102, 56731, 1, 2, 65.25, 290.55, 1636588800000L)
    //Additional lane cut from start
    val additionalLane3 = generateTestLane(103, 56731, 1, 3, 45.25, 317.995, 1636588800000L)

    Seq(mainLane, additionalLane2, additionalLane3)
  }

  val changesForTest5a: Seq[ChangeInfo] = {
    val change13 = ChangeInfo(Some(2776126), Some(2776126), 2110412275, 13, Some(0.0), Some(217.79702887), Some(0.0), Some(217.79702887), 1648832434000L)
    val change14 = ChangeInfo(None, Some(2776126), 2110412275, 14, None, None, Some(217.79702887), Some(324.37921035), 1648832434000L)

    Seq(change13, change14)
  }

  val roadLinksForTest5a: Seq[RoadLink] = {
    val linkGeometry = Seq(Point(556784.23, 6940336.168, 122.7719999999972),
      Point(556779.608, 6940337.892, 122.60599999999977),
      Point(556776.547, 6940339.101, 122.53500000000349),
      Point(556773.194, 6940340.785, 122.5280000000057),
      Point(556769.847, 6940342.087, 122.48799999999756),
      Point(556760.072, 6940347.712, 122.44899999999325),
      Point(556729.727, 6940371.342, 123.40799999999581),
      Point(556665.729, 6940425.814, 127.56600000000617),
      Point(556613.25, 6940469.761, 130.85400000000664),
      Point(556579.397, 6940508.521, 131.92399999999907),
      Point(556547.015, 6940545.762, 132.42600000000675),
      Point(556543.231, 6940550.117, 132.48099999999977))

    Seq(RoadLink(2776126, linkGeometry, 324.3792103503157, State, 4, BothDirections, SingleCarriageway, None,
      None, Map(), InUse, NormalLinkInterface, List()))
  }

  val lanesForTest5a: Seq[PersistedLane] = {
    val mainLaneTowards = generateTestLane(201, 2776126, 2, 1, 0.0, 217.797, 1636588800000L)
    val mainLaneAgainst = generateTestLane(301, 2776126, 3, 1, 0.0, 217.797, 1636588800000L)
    //Additional lane 2 cut from start and end
    val lane2 = generateTestLane(202, 2776126, 2, 2, 50.75, 180.50, 1636588800000L)
    //Full length additional
    val lane3 = generateTestLane(303, 2776126, 3, 3, 0.0, 217.797, 1636588800000L)

    Seq(mainLaneTowards, mainLaneAgainst, lane2, lane3)
  }

  val changesForTest5b: Seq[ChangeInfo] = {
    val change13 = ChangeInfo(Some(12397071), Some(12397071), 2110376244, 13, Some(0.0), Some(34.81129942), Some(0.0), Some(34.81121228), 1648832434000L)
    val change15 = ChangeInfo(Some(12397071), None, 2110376244, 15, Some(34.81121228), Some(36.00944863), None, None, 1648832434000L)

    Seq(change13, change15)
  }

  val roadLinksForTest5b: Seq[RoadLink] ={
    val linkGeometry = Seq(Point(225273.264, 6994886.16, 30.919999999998254),
      Point(225268.561, 6994898.527, 30.269000000000233),
      Point(225265.664, 6994903.343, 30.172000000005937),
      Point(225254.502, 6994912.196, 29.66700000000128),
      Point(225252.87, 6994912.718, 29.672999999995227))
    Seq(RoadLink(12397071, linkGeometry, 34.81129942581629, Municipality, 5, BothDirections, SingleCarriageway, None, None, Map(),
      InUse, NormalLinkInterface, List()))
  }

  val lanesForTest5b: Seq[PersistedLane] ={
    // Full lenght mainLanes
    val mainLaneTowards = generateTestLane(201, 12397071, 2, 1, 0.0, 36.009, 1636588800000L)
    val mainLaneAgainst = generateTestLane(301, 12397071, 3, 1, 0.0, 36.009, 1636588800000L)

    // Cut from start
    val lane2Towards = generateTestLane(202, 12397071, 2, 2, 15.5, 36.009, 1636588800000L)
    // Cut from end inside shortened link segment
    val lane3Against = generateTestLane(303, 12397071, 3, 3, 0.0, 35.45, 1636588800000L)
    // Cut from end before shortened link segment
    val lane4Towards = generateTestLane(204, 12397071, 2, 4, 0.0, 27.5, 1636588800000L)
    // Cut lane completely on removed link segment
    val lane5Against = generateTestLane(305, 12397071, 3, 5, 34.9, 36.0, 1636588800000L)
    Seq(mainLaneTowards, mainLaneAgainst, lane2Towards, lane3Against, lane4Towards, lane5Against)
  }

  test("Case 1: Three links combine into one"){
    val roadLinks = roadLinksForTest1
    val changes = changesForTest1
    val existingLanes = lanesForTest1
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




  test("Case 2: Link lengthened"){
    val roadLinks = roadLinksForTest2
    val changes = changesForTest2
    val existingLanes = lanesForTest2
    val historyLinks = roadLinkService.getHistoryDataLinksFromVVH(existingLanes.map(_.linkId).toSet)
    val latestHistoryLinks = historyLinks.groupBy(_.linkId).map(_._2.maxBy(_.vvhTimeStamp)).toSeq
    val (changeSet, modifiedLanes) = handleChanges(roadLinks, latestHistoryLinks, changes, existingLanes)

    modifiedLanes.size should equal(4)
    changeSet.expiredLaneIds.size should equal(0)
    changeSet.adjustedMValues.size should equal(0)
    changeSet.generatedPersistedLanes.size should equal(0)
    changeSet.adjustedSideCodes.size should equal(0)
    changeSet.adjustedVVHChanges.size should equal(4)

    val mainLaneVvhAdjustment = changeSet.adjustedVVHChanges.find(_.laneId == 101).get
    val lane2FullLengthVvhAdjustment = changeSet.adjustedVVHChanges.find(_.laneId == 102).get
    val lane3CutVvhAdjustment = changeSet.adjustedVVHChanges.find(_.laneId == 103).get
    val lane4CutVvhAdjustment = changeSet.adjustedVVHChanges.find(_.laneId == 104).get

    val commonPartChange = changes.find(c => c.oldId.contains(6170239) && c.newId.contains(6170239)).get

    mainLaneVvhAdjustment.startMeasure should equal(0.0)
    areMeasuresCloseEnough(mainLaneVvhAdjustment.endMeasure, commonPartChange.newEndMeasure.get)

    lane2FullLengthVvhAdjustment.startMeasure should equal(0.0)
    areMeasuresCloseEnough(lane2FullLengthVvhAdjustment.endMeasure, commonPartChange.newEndMeasure.get)

    lane3CutVvhAdjustment.startMeasure should equal(0.0)
    lane3CutVvhAdjustment.endMeasure should equal(150.50)

    lane4CutVvhAdjustment.startMeasure should equal(120.25)
    lane4CutVvhAdjustment.endMeasure should equal(203.357)

  }

//   Roadlink is split in two, 138m -> 381m segment is removed, new link lengths: 138m and 269m
  test("Case 3: Link divided into two"){
    val roadLinks = roadLinksForTest3
    val changes = changesForTest3
    val existingLanes = lanesForTest3
    val historyLinks = roadLinkService.getHistoryDataLinksFromVVH(existingLanes.map(_.linkId).toSet)
    val latestHistoryLinks = historyLinks.groupBy(_.linkId).map(_._2.maxBy(_.vvhTimeStamp)).toSeq
    val (changeSet, modifiedLanes) = handleChanges(roadLinks, latestHistoryLinks, changes, existingLanes)

    modifiedLanes.size should equal(5)
    changeSet.expiredLaneIds.size should equal(3)
    changeSet.adjustedMValues.size should equal(0)
    changeSet.generatedPersistedLanes.size should equal(0)
    changeSet.adjustedSideCodes.size should equal(0)
    changeSet.adjustedVVHChanges.size should equal(0)

    val originalLane3 = existingLanes.find(_.id == 103).get

    val adjustedMainLaneLink25 = modifiedLanes.find(lane => lane.linkId == 12628125 && lane.laneCode == 1).get
    val adjustedLane2Link25 = modifiedLanes.find(lane => lane.linkId == 12628125 && lane.laneCode == 2).get
    val adjustedLane3Link25 = modifiedLanes.find(lane => lane.linkId == 12628125 && lane.laneCode == 3).get

    val adjustedMainLaneLink39 = modifiedLanes.find(lane => lane.linkId == 12628139 && lane.laneCode == 1).get
    val adjustedLane2Link39 = modifiedLanes.find(lane => lane.linkId == 12628139 && lane.laneCode == 2)
    val adjustedLane3Link39 = modifiedLanes.find(lane => lane.linkId == 12628139 && lane.laneCode == 3).get

    val changeInfoLink25 = changes.find(_.newId.contains(12628125)).get
    val changeInfoLink39 = changes.find(_.newId.contains(12628139)).get

    //MainLanes should cover the whole link
    adjustedMainLaneLink25.startMeasure should equal(0.0)
    areMeasuresCloseEnough(changeInfoLink25.newEndMeasure.get, adjustedMainLaneLink25.endMeasure) should equal(true)

    adjustedMainLaneLink39.startMeasure should equal(0.0)
    areMeasuresCloseEnough(changeInfoLink39.newEndMeasure.get, adjustedMainLaneLink39.endMeasure) should equal(true)


    //Additional lane's startM should be same because that segment of the lane wasn't cut
    //Additional lane's endM should be the same as link's new endM, because additional lane's original length
    //exceeded the cutting point
    adjustedLane2Link25.startMeasure.round should equal(51)
    areMeasuresCloseEnough(changeInfoLink25.newEndMeasure.get, adjustedLane2Link25.endMeasure) should equal(true)

    //Additional lane should not exist on on link 12628139 because original additional lane didn't cover that segment
    adjustedLane2Link39.isEmpty should equal(true)

    //StartM should have stayed the same
    //EndM should be equal to links length because lane exceeded the cutting point
    adjustedLane3Link25.startMeasure.round should equal(51)
    areMeasuresCloseEnough(changeInfoLink25.newEndMeasure.get, adjustedLane3Link25.endMeasure) should equal(true)

    //EndM should have decreased equal to the difference between original endM and segments old startM
    adjustedLane3Link39.startMeasure should equal(0.0)
    areMeasuresCloseEnough(adjustedLane3Link39.endMeasure, originalLane3.endMeasure - changeInfoLink39.oldStartMeasure.get)

  }

  test("Case 4: Link shortened"){
    val roadLinks = roadLinksForTest4
    val changes = changesForTest4
    val existingLanes = lanesForTest4
    val historyLinks = roadLinkService.getHistoryDataLinksFromVVH(existingLanes.map(_.linkId).toSet)
    val latestHistoryLinks = historyLinks.groupBy(_.linkId).map(_._2.maxBy(_.vvhTimeStamp)).toSeq
    val (changeSet, modifiedLanes) = handleChanges(roadLinks, latestHistoryLinks, changes, existingLanes)

    modifiedLanes.size should equal(3)
    changeSet.expiredLaneIds.size should equal(0)
    changeSet.adjustedMValues.size should equal(0)
    changeSet.generatedPersistedLanes.size should equal(0)
    changeSet.adjustedSideCodes.size should equal(0)
    changeSet.adjustedVVHChanges.size should equal(3)

    val mainLaneVvhAdjustment = changeSet.adjustedVVHChanges.find(_.laneId == 101).get
    val lane2VvhAdjustment = changeSet.adjustedVVHChanges.find(_.laneId == 102).get
    val lane3VvhAdjustment = changeSet.adjustedVVHChanges.find(_.laneId == 103).get

    val existingLane2 = existingLanes.find(_.id == 102).get
    val roadLink = roadLinks.head

    val commonPartChange = changes.find(c => c.oldId.contains(56731) && c.newId.contains(56731)).get

    //Mainlane should cover the whole link
    mainLaneVvhAdjustment.startMeasure should equal(0.0)
    areMeasuresCloseEnough(commonPartChange.newEndMeasure.get, mainLaneVvhAdjustment.endMeasure) should equal(true)

    //Start measure should have decreased equal to the amount that was cut from start of roadLink
    areMeasuresCloseEnough(lane2VvhAdjustment.startMeasure, existingLane2.startMeasure - commonPartChange.oldStartMeasure.get) should equal(true)
    areMeasuresCloseEnough(lane2VvhAdjustment.endMeasure, 287) should equal(true)

    areMeasuresCloseEnough(lane3VvhAdjustment.startMeasure, 45.25 - commonPartChange.oldStartMeasure.get) should equal(true)
    areMeasuresCloseEnough(lane3VvhAdjustment.endMeasure, roadLink.length) should equal(true)

  }

  test("Case 5a: Link replaced with longer"){
    val roadLinks = roadLinksForTest5a
    val changes = changesForTest5a
    val existingLanes = lanesForTest5a
    val historyLinks = roadLinkService.getHistoryDataLinksFromVVH(existingLanes.map(_.linkId).toSet)
    val latestHistoryLinks = historyLinks.groupBy(_.linkId).map(_._2.maxBy(_.vvhTimeStamp)).toSeq
    val (changeSet, modifiedLanes) = handleChanges(roadLinks, latestHistoryLinks, changes, existingLanes)

    modifiedLanes.size should equal(existingLanes.size)
    changeSet.adjustedVVHChanges.size should equal(existingLanes.size)
    changeSet.expiredLaneIds.size should equal(0)
    changeSet.adjustedMValues.size should equal(0)
    changeSet.generatedPersistedLanes.size should equal(0)
    changeSet.adjustedSideCodes.size should equal(0)

    val roadLink = roadLinks.head
    val modifiedMainLaneTowards = modifiedLanes.find(_.id == 201).get
    val modifiedMainLaneAgainst = modifiedLanes.find(_.id == 301).get
    val modifiedLane3Against = modifiedLanes.find(_.id == 303).get
    val modifiedLane2Towards = modifiedLanes.find(_.id == 202).get

    modifiedMainLaneTowards.startMeasure should equal(0.0)
    modifiedMainLaneTowards.endMeasure should equal(roadLink.length)

    modifiedMainLaneAgainst.startMeasure should equal(0.0)
    modifiedMainLaneAgainst.endMeasure should equal(roadLink.length)

    //Measures should have stayed the same on cut additional lane
    modifiedLane2Towards.startMeasure should equal(50.75)
    modifiedLane2Towards.endMeasure should equal(180.5)

    modifiedLane3Against.startMeasure should equal(0.0)
    modifiedLane3Against.endMeasure should equal(roadLink.length)




  }

  test("Link replaced with shorter"){
    val roadLinks = roadLinksForTest5b
    val changes = changesForTest5b
    val existingLanes = lanesForTest5b
    val historyLinks = roadLinkService.getHistoryDataLinksFromVVH(existingLanes.map(_.linkId).toSet)
    val latestHistoryLinks = historyLinks.groupBy(_.linkId).map(_._2.maxBy(_.vvhTimeStamp)).toSeq
    val (changeSet, modifiedLanes) = handleChanges(roadLinks, latestHistoryLinks, changes, existingLanes)

    modifiedLanes.size should equal(5)
    changeSet.adjustedVVHChanges.size should equal(5)

    val modifiedMainLaneTowards = modifiedLanes.find(_.id == 201).get
    val modifiedMainLaneAgainst = modifiedLanes.find(_.id == 301).get
    val modifiedLane2 = modifiedLanes.find(_.id == 202).get
    val modifiedLane3 = modifiedLanes.find(_.id == 303).get
    val modifiedLane4 = modifiedLanes.find(_.id == 204).get
    val roadLink = roadLinks.head

    modifiedMainLaneTowards.startMeasure should equal(0.0)
    modifiedMainLaneTowards.endMeasure should equal(roadLink.length)

    modifiedMainLaneAgainst.startMeasure should equal(0.0)
    modifiedMainLaneAgainst.endMeasure should equal(roadLink.length)

    areMeasuresCloseEnough(modifiedLane2.startMeasure, 15.5)
    modifiedLane2.endMeasure should equal(roadLink.length)

    modifiedLane3.startMeasure should equal(0.0)
    modifiedLane3.endMeasure should equal(roadLink.length)

    modifiedLane4.startMeasure should equal(0.0)
    areMeasuresCloseEnough(modifiedLane4.endMeasure, 27.5)



  }
}
