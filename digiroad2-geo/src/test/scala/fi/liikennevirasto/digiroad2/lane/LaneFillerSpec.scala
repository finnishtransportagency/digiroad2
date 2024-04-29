package fi.liikennevirasto.digiroad2.lane

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.{Motorway, Municipality, SideCode, TrafficDirection}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import org.scalatest.{FunSuite, Matchers}
import java.util.UUID
import scala.util.Random


class LaneFillerSpec extends FunSuite with Matchers {

  object laneFiller extends LaneFiller

  private def generateRandomLinkId(): String = s"${UUID.randomUUID()}:${Random.nextInt(100)}"

  val linkId1: String = generateRandomLinkId()
  val linkId2: String = generateRandomLinkId()
  val linkId3: String = generateRandomLinkId()
  val linkId4: String = generateRandomLinkId()

  val roadLinkTowards1 = RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
                                1, TrafficDirection.TowardsDigitizing, Motorway, None, None)

  val roadLinkTowards2 = RoadLink(linkId2, Seq(Point(0.0, 0.0), Point(15.0, 0.0)), 15.0, Municipality,
                                1, TrafficDirection.BothDirections, Motorway, None, None)

  val roadLinkTowards3 = RoadLink(linkId3, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
                                1, TrafficDirection.BothDirections, Motorway, None, None)

  val roadLinkTowards4 = RoadLink(linkId4, Seq(Point(0.0, 0.0), Point(1.9, 0.0)), 1.9, Municipality,
                                1, TrafficDirection.AgainstDigitizing, Motorway, None, None)

  test("Expire lane that is outside topology and create a new lane similar and with % reduction") {

    val topology = Seq( roadLinkTowards1 )

    val lane = PersistedLane(1L, roadLinkTowards1.linkId, SideCode.BothDirections.value,
      1, 745L, 10.0, 15.0, None, None, None, None, None, None, expired = false, 0L, None,
      Seq(LaneProperty("lane_code", Seq(LanePropertyValue(1))))
    )

    val linearAssets = Map(
      roadLinkTowards1.linkId -> Seq( lane )
    )

    val (filledTopology, changeSet) = laneFiller.fillTopology(topology, linearAssets)
    val pieceWiseTopology = laneFiller.toLPieceWiseLaneOnMultipleLinks(filledTopology, topology)
    pieceWiseTopology should have size 1
    pieceWiseTopology.map(_.sideCode) should be (Seq(SideCode.BothDirections.value))
    pieceWiseTopology.map(_.id) should be (Seq(0L))
    pieceWiseTopology.map(_.linkId) should be (Seq(linkId1))
    pieceWiseTopology.map(_.geometry) should be (Seq(Seq(Point(5.0, 0.0), Point(7.5, 0.0))))

    changeSet.expiredLaneIds should be (Set(1L))
  }

  test("Multiple lanes outside topology. One lane should dropped due the shortness after conversion.") {
    val topology = Seq( roadLinkTowards1 )

    val lane1 = PersistedLane(1L, roadLinkTowards1.linkId, SideCode.BothDirections.value,
      1, 745L, 10.0, 15.0, None, None, None, None, None, None, expired = false, 0L, None,
      Seq(LaneProperty("lane_code", Seq(LanePropertyValue(1))))
    )

    val lane2 = PersistedLane(2L, roadLinkTowards1.linkId, SideCode.BothDirections.value,
      2, 745L, 10.0, 15.0, None, None, None, None, None, None, expired = false, 0L, None,
      Seq(LaneProperty("lane_code", Seq(LanePropertyValue(2))))
    )

    val lane3 = PersistedLane(3L, roadLinkTowards1.linkId, SideCode.BothDirections.value,
      3, 745L, 13.0, 15.0, None, None, None, None, None, None, expired = false, 0L, None,
      Seq(LaneProperty("lane_code", Seq(LanePropertyValue(3))))
    )

    val linearAssets = Map(
      roadLinkTowards1.linkId -> Seq( lane1, lane2, lane3 )
    )

    val (filledTopology, changeSet) = laneFiller.fillTopology(topology, linearAssets)
    val pieceWiseTopology = laneFiller.toLPieceWiseLaneOnMultipleLinks(filledTopology, topology)
    pieceWiseTopology should have size 2
    pieceWiseTopology.map(_.sideCode) should be (Seq(SideCode.BothDirections.value, SideCode.BothDirections.value))
    pieceWiseTopology.map(_.id) should be (Seq(0L, 0L))
    pieceWiseTopology.map(_.linkId) should be (Seq(roadLinkTowards1.linkId, roadLinkTowards1.linkId))
    pieceWiseTopology.map(_.geometry) should be (Seq(Seq(Point(5.0, 0.0), Point(7.5, 0.0)), Seq(Point(5.0, 0.0), Point(7.5, 0.0))))

    changeSet.expiredLaneIds should be (Set(1L, 2L, 3L))
  }

  test("Adjust lane that goes outside of roadLink length") {
    val topology = Seq( roadLinkTowards1 )

    val lane = PersistedLane(1L, roadLinkTowards1.linkId, SideCode.BothDirections.value,
      1, 745L, 0.0, 15.0, None, None, None, None, None, None, expired = false, 0L, None,
      Seq(LaneProperty("lane_code", Seq(LanePropertyValue(1))))
    )

    val linearAssets = Map(
      roadLinkTowards1.linkId -> Seq( lane )
    )

    val (filledTopology, changeSet) = laneFiller.fillTopology(topology, linearAssets)
    val pieceWiseTopology = laneFiller.toLPieceWiseLaneOnMultipleLinks(filledTopology, topology)
    pieceWiseTopology should have size 1
    pieceWiseTopology.map(_.sideCode) should be (Seq(SideCode.BothDirections.value))
    pieceWiseTopology.map(_.id) should be (Seq(1L))
    pieceWiseTopology.map(_.linkId) should be (Seq(linkId1))
    pieceWiseTopology.map(_.geometry) should be (Seq(Seq(Point(0, 0.0), Point(10.0, 0.0))))

    changeSet.expiredLaneIds should be (Set())
  }

  test("Adjust lanes that goes outside of roadLink length. Multiple roadLinks") {
    val topology = Seq( roadLinkTowards3, roadLinkTowards1 )

    val lane = PersistedLane(1L, roadLinkTowards3.linkId, SideCode.TowardsDigitizing.value,
      1, 745L, 0.0, 15.0, None, None, None, None, None, None, expired = false, 0L, None,
      Seq(LaneProperty("lane_code", Seq(LanePropertyValue(1))))
    )

    val lane2 = PersistedLane(2L, roadLinkTowards3.linkId, SideCode.AgainstDigitizing.value,
      1, 745L, 5.0, 15.0, None, None, None, None, None, None, expired = false, 0L, None,
      Seq(LaneProperty("lane_code", Seq(LanePropertyValue(1))))
    )

    val lane3 = PersistedLane(20L, roadLinkTowards1.linkId, SideCode.BothDirections.value,
      1, 745L, 5.0, 15.0, None, None, None, None, None, None, expired = false, 0L, None,
      Seq(LaneProperty("lane_code", Seq(LanePropertyValue(1))))
    )


    val linearAssets = Map(
      roadLinkTowards3.linkId -> Seq( lane, lane2 ),
      roadLinkTowards1.linkId -> Seq( lane3 )
    )

    val (filledTopology, changeSet) = laneFiller.fillTopology(topology, linearAssets)
    val pieceWiseTopology = laneFiller.toLPieceWiseLaneOnMultipleLinks(filledTopology, topology)

    pieceWiseTopology should have size 3
    pieceWiseTopology.map(_.sideCode) should contain theSameElementsAs  Seq(SideCode.TowardsDigitizing.value, SideCode.AgainstDigitizing.value, SideCode.BothDirections.value)
    pieceWiseTopology.map(_.id) should contain theSameElementsAs Seq(1L, 2L, 20L)
    pieceWiseTopology.map(_.linkId) should contain theSameElementsAs Seq(linkId3, linkId3, linkId1)
    pieceWiseTopology.map(_.geometry) should contain theSameElementsAs Seq(Seq(Point(0.0, 0.0), Point(10.0, 0.0)), Seq(Point(5.0, 0.0), Point(10.0, 0.0)), Seq(Point(5.0, 0.0), Point(10.0, 0.0)))

    changeSet.expiredLaneIds should be (Set())
  }

  test("Merge Lanes") {
    val topology = Seq( roadLinkTowards1 )

    val lane = PersistedLane(20L, roadLinkTowards1.linkId, SideCode.BothDirections.value,
      1, 745L, 0.0, 10.0, None, None, None, None, None, None, expired = false, 0L, None,
      Seq(LaneProperty("lane_code", Seq(LanePropertyValue(1))))
    )

    val lane2 = PersistedLane(21L, roadLinkTowards1.linkId, SideCode.BothDirections.value,
      2, 745L, 7.0, 10.0, None, None, None, None, None, None, expired = false, 0L, None,
      Seq(LaneProperty("lane_code", Seq(LanePropertyValue(2))))
    )

    val lane2b = PersistedLane(22L, roadLinkTowards1.linkId, SideCode.BothDirections.value,
      2, 745L, 4.0, 7.0, None, None, None, None, None, None, expired = false, 0L, None,
      Seq(LaneProperty("lane_code", Seq(LanePropertyValue(2))))
    )

    val linearAssets = Map(
      roadLinkTowards1.linkId -> Seq( lane, lane2, lane2b )
    )

    val (filledTopology, changeSet) = laneFiller.fillTopology(topology, linearAssets)
    val pieceWiseTopology = laneFiller.toLPieceWiseLaneOnMultipleLinks(filledTopology, topology)

    pieceWiseTopology should have size 2
    pieceWiseTopology.map(_.sideCode) should be (Seq(SideCode.BothDirections.value,  SideCode.BothDirections.value))
    pieceWiseTopology.map(_.id) should be (Seq(20L, 21L))
    pieceWiseTopology.map(_.linkId) should be (Seq( linkId1, linkId1))
    pieceWiseTopology.map(_.geometry) should be (Seq(Seq(Point(0.0, 0.0), Point(10.0, 0.0)), Seq(Point(4.0, 0.0), Point(10.0, 0.0)) ))

    changeSet.expiredLaneIds should be (Set(22L))
  }


  test("Drop lanes with less than 2 meters") {
    val topology = Seq( roadLinkTowards1 )

    val lane = PersistedLane(20L, roadLinkTowards1.linkId, SideCode.BothDirections.value,
      1, 745L, 1.0, 2.0, None, None, None, None, None, None, expired = false, 0L, None,
      Seq(LaneProperty("lane_code", Seq(LanePropertyValue(1))))
    )

    val lane2 = PersistedLane(21L, roadLinkTowards1.linkId, SideCode.BothDirections.value,
      2, 745L, 8.0, 10.0, None, None, None, None, None, None, expired = false, 0L, None,
      Seq(LaneProperty("lane_code", Seq(LanePropertyValue(2))))
    )


    val linearAssets = Map(
      roadLinkTowards1.linkId -> Seq( lane, lane2 )
    )

    val (filledTopology, changeSet) = laneFiller.fillTopology(topology, linearAssets)
    val pieceWiseTopology = laneFiller.toLPieceWiseLaneOnMultipleLinks(filledTopology, topology)

    pieceWiseTopology should have size 1
    pieceWiseTopology.map(_.sideCode) should be (Seq(SideCode.BothDirections.value ))
    pieceWiseTopology.map(_.id) should be (Seq(21L))
    pieceWiseTopology.map(_.linkId) should be (Seq( linkId1))
    pieceWiseTopology.map(_.geometry) should be (Seq(Seq(Point(8.0, 0.0), Point(10.0, 0.0)) ))

    changeSet.expiredLaneIds should be (Set(20L))
  }

  test("Don't drop lanes with less than 2 meters on a roadLink with length less than 2 meters") {
    val topology = Seq( roadLinkTowards4 )

    val lane = PersistedLane(20L, roadLinkTowards4.linkId, SideCode.BothDirections.value,
      1, 745L, 0.0, 1.9, None, None, None, None, None, None, expired = false, 0L, None,
      Seq(LaneProperty("lane_code", Seq(LanePropertyValue(1))))
    )

    val lane2 = PersistedLane(21L, roadLinkTowards4.linkId, SideCode.BothDirections.value,
      2, 745L, 0.0, 1.0, None, None, None, None, None, None, expired = false, 0L, None,
      Seq(LaneProperty("lane_code", Seq(LanePropertyValue(2))))
    )


    val linearAssets = Map(
      roadLinkTowards4.linkId -> Seq( lane, lane2 )
    )

    val (filledTopology, changeSet) = laneFiller.fillTopology(topology, linearAssets)

    filledTopology should have size 2
    filledTopology.map(_.sideCode) should be (Seq(SideCode.BothDirections.value, SideCode.BothDirections.value))
    filledTopology.map(_.id).sorted should be (Seq(20L, 21L))
    filledTopology.map(_.linkId) should be (Seq(linkId4, linkId4))

    changeSet.expiredLaneIds should be (Set())
  }

  test("Expire duplicate lane") {
    val topology = Seq(roadLinkTowards1)

    val lane1 = PersistedLane(1L, roadLinkTowards1.linkId, SideCode.BothDirections.value,
      1, 745L, 0.0, 10.0, None, None, None, None, None, None, expired = false, 0L, None,
      Seq(LaneProperty("lane_code", Seq(LanePropertyValue(1))))
    )

    val lane2 = PersistedLane(2L, roadLinkTowards1.linkId, SideCode.BothDirections.value,
      2, 745L, 0.0, 10.0, None, None, None, None, None, None, expired = false, 0L, None,
      Seq(LaneProperty("lane_code", Seq(LanePropertyValue(2))))
    )

    val lane2Duplicated = PersistedLane(3L, roadLinkTowards1.linkId, SideCode.BothDirections.value,
      2, 745L, 0.0, 10.0, None, None, None, None, None, None, expired = false, 0L, None,
      Seq(LaneProperty("lane_code", Seq(LanePropertyValue(2))))
    )

    val lane3 = PersistedLane(4L, roadLinkTowards1.linkId, SideCode.BothDirections.value,
      3, 745L, 5.0, 10.0, None, None, None, None, None, None, expired = false, 0L, None,
      Seq(LaneProperty("lane_code", Seq(LanePropertyValue(3))))
    )

    val linearAssets = Map(
      roadLinkTowards1.linkId -> Seq(lane1, lane2, lane2Duplicated, lane3)
    )

    val (filledTopology, changeSet) = laneFiller.fillTopology(topology, linearAssets)
    val pieceWiseTopology = laneFiller.toLPieceWiseLaneOnMultipleLinks(filledTopology, topology)

    pieceWiseTopology should have size 3
    pieceWiseTopology.map(_.sideCode) should be (Seq(SideCode.BothDirections.value, SideCode.BothDirections.value, SideCode.BothDirections.value))
    pieceWiseTopology.map(_.id).sorted should be (Seq(1L, 2L, 4L))
    pieceWiseTopology.map(_.linkId) should be (Seq(roadLinkTowards1.linkId, roadLinkTowards1.linkId, roadLinkTowards1.linkId))
    pieceWiseTopology.map(_.geometry) should be (Seq(Seq(Point(0.0, 0.0), Point(10.0, 0.0)), Seq(Point(0.0, 0.0), Point(10.0, 0.0)), Seq(Point(5.0, 0.0), Point(10.0, 0.0))))

    changeSet.expiredLaneIds should be (Set(3L))
  }

  test("Expire duplicate lane2") {
    val topology = Seq(roadLinkTowards1)

    val lane1 = PersistedLane(1L, roadLinkTowards1.linkId, SideCode.BothDirections.value,
      1, 745L, 0.0, 10.0, None, None, None, None, None, None, expired = false, 0L, None,
      Seq(LaneProperty("lane_code", Seq(LanePropertyValue(1))))
    )

    val lane2 = PersistedLane(2L, roadLinkTowards1.linkId, SideCode.BothDirections.value,
      2, 745L, 0.0, 10.0, None, None, None, None, None, None, expired = false, 0L, None,
      Seq(LaneProperty("lane_code", Seq(LanePropertyValue(2))))
    )

    val lane2Duplicated = PersistedLane(3L, roadLinkTowards1.linkId, SideCode.BothDirections.value,
      2, 745L, 0.0, 10.0, None, None, None, None, None, None, expired = false, 0L, None,
      Seq(LaneProperty("lane_code", Seq(LanePropertyValue(2))))
    )

    val lane3 = PersistedLane(4L, roadLinkTowards1.linkId, SideCode.BothDirections.value,
      3, 745L, 5.0, 10.0, None, None, None, None, None, None, expired = false, 0L, None,
      Seq(LaneProperty("lane_code", Seq(LanePropertyValue(3))))
    )

    val linearAssets = Map(
      roadLinkTowards1.linkId -> Seq(lane1, lane2, lane2Duplicated, lane3)
    )

    val (filledTopology, changeSet) = laneFiller.fillTopology(topology, linearAssets,geometryChanged = false)
    val pieceWiseTopology = laneFiller.toLPieceWiseLaneOnMultipleLinks(filledTopology, topology)

    pieceWiseTopology should have size 3
    pieceWiseTopology.map(_.sideCode) should be(Seq(SideCode.BothDirections.value, SideCode.BothDirections.value, SideCode.BothDirections.value))
    pieceWiseTopology.map(_.id).sorted should be(Seq(1L, 2L, 4L))
    pieceWiseTopology.map(_.linkId) should be(Seq(roadLinkTowards1.linkId, roadLinkTowards1.linkId, roadLinkTowards1.linkId))
    pieceWiseTopology.map(_.geometry) should be(Seq(Seq(Point(0.0, 0.0), Point(10.0, 0.0)), Seq(Point(0.0, 0.0), Point(10.0, 0.0)), Seq(Point(5.0, 0.0), Point(10.0, 0.0))))

    changeSet.expiredLaneIds should be(Set(3L))
  }
}
