package fi.liikennevirasto.digiroad2.lane

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.{Motorway, Municipality, SideCode, TrafficDirection}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import org.scalatest.{FunSuite, Matchers}


class LaneFillerSpec extends FunSuite with Matchers {

  object laneFiller extends LaneFiller

  val roadLinkTowards1 = RoadLink(1L, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
                                1, TrafficDirection.TowardsDigitizing, Motorway, None, None)

  val roadLinkTowards2 = RoadLink(2L, Seq(Point(0.0, 0.0), Point(15.0, 0.0)), 15.0, Municipality,
                                1, TrafficDirection.BothDirections, Motorway, None, None)

  val roadLinkTowards3 = RoadLink(3L, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
                                1, TrafficDirection.BothDirections, Motorway, None, None)

  val roadLinkTowards4 = RoadLink(4L, Seq(Point(0.0, 0.0), Point(1.9, 0.0)), 1.9, Municipality,
                                1, TrafficDirection.AgainstDigitizing, Motorway, None, None)

  test("Expire lane that is outside topology and create a new lane similar and with % reduction") {

    val topology = Seq( roadLinkTowards1 )

    val lane = PersistedLane(1L, roadLinkTowards1.linkId, TrafficDirection.toSideCode(roadLinkTowards1.trafficDirection).value,
      11, 745L, 10.0, 15.0, None, None, None, None, expired = false, 0L, None,
      LanePropertiesValues(Seq(LaneProperty("lane_code", Seq(LanePropertyValue(11)))))
    )

    val linearAssets = Map(
      roadLinkTowards1.linkId -> Seq( lane )
    )

    val (filledTopology, changeSet) = laneFiller.fillTopology(topology, linearAssets)

    filledTopology should have size 1
    filledTopology.map(_.sideCode) should be (Seq(SideCode.TowardsDigitizing.value))
    filledTopology.map(_.id) should be (Seq(0L))
    filledTopology.map(_.linkId) should be (Seq(1L))
    filledTopology.map(_.geometry) should be (Seq(Seq(Point(5.0, 0.0), Point(7.5, 0.0))))

    changeSet.expiredLaneIds should be (Set(1L))
  }

  test("Multiple lanes outside topology. One lane should dropped due the shortness after conversion.") {

    val sideCodeTowardsDigitizingValue = SideCode.TowardsDigitizing.value
    val topology = Seq( roadLinkTowards1 )

    val lane11 = PersistedLane(1L, roadLinkTowards1.linkId, sideCodeTowardsDigitizingValue,
      11, 745L, 10.0, 15.0, None, None, None, None, expired = false, 0L, None,
      LanePropertiesValues(Seq(LaneProperty("lane_code", Seq(LanePropertyValue(11)))))
    )

    val lane12 = PersistedLane(2L, roadLinkTowards1.linkId, sideCodeTowardsDigitizingValue,
      12, 745L, 10.0, 15.0, None, None, None, None, expired = false, 0L, None,
      LanePropertiesValues(Seq(LaneProperty("lane_code", Seq(LanePropertyValue(12)))))
    )

    val lane13 = PersistedLane(3L, roadLinkTowards1.linkId, sideCodeTowardsDigitizingValue,
      13, 745L, 13.0, 15.0, None, None, None, None, expired = false, 0L, None,
      LanePropertiesValues(Seq(LaneProperty("lane_code", Seq(LanePropertyValue(13)))))
    )

    val linearAssets = Map(
      roadLinkTowards1.linkId -> Seq( lane11, lane12, lane13 )
    )

    val (filledTopology, changeSet) = laneFiller.fillTopology(topology, linearAssets)

    filledTopology should have size 2
    filledTopology.map(_.sideCode) should be (Seq(sideCodeTowardsDigitizingValue, sideCodeTowardsDigitizingValue))
    filledTopology.map(_.id) should be (Seq(0L, 0L))
    filledTopology.map(_.linkId) should be (Seq(roadLinkTowards1.linkId, roadLinkTowards1.linkId))
    filledTopology.map(_.geometry) should be (Seq(Seq(Point(5.0, 0.0), Point(7.5, 0.0)), Seq(Point(5.0, 0.0), Point(7.5, 0.0))))

    changeSet.expiredLaneIds should be (Set(1L, 2L, 3L))
  }

  test("RoadLink BothDirection and with only one Lane (TowardsDigitizing). The 2nd should be created.") {

    val topology = Seq( roadLinkTowards2 )

    val lane11 = PersistedLane(2L, roadLinkTowards2.linkId, SideCode.TowardsDigitizing.value,
      11, 745L, 0, 15.0, None, None, None, None, expired = false, 0L, None,
      LanePropertiesValues(Seq(LaneProperty("lane_code", Seq(LanePropertyValue(11)))))
    )

    val linearAssets = Map(
      roadLinkTowards2.linkId -> Seq( lane11 )
    )

    val (filledTopology, changeSet) = laneFiller.fillTopology(topology, linearAssets)

    filledTopology should have size 2
    filledTopology.map(_.sideCode) should be (Seq(SideCode.TowardsDigitizing.value, SideCode.AgainstDigitizing.value))
    filledTopology.map(_.id) should be (Seq(2L, 0L))
    filledTopology.map(_.linkId) should be (Seq(roadLinkTowards2.linkId, roadLinkTowards2.linkId))
    filledTopology.map(_.geometry) should be (Seq(Seq(Point(0.0, 0.0), Point(15.0, 0.0)), Seq(Point(0.0, 0.0), Point(15.0, 0.0))))

    changeSet.expiredLaneIds should be (Set())
  }

  test("RoadLink BothDirection and with only one Lane (AgainstDigitizing). The 2nd should be created.") {

    val topology = Seq( roadLinkTowards2 )

    val lane11 = PersistedLane(2L, roadLinkTowards2.linkId, SideCode.AgainstDigitizing.value,
      21, 745L, 0, 15.0, None, None, None, None, expired = false, 0L, None,
      LanePropertiesValues(Seq(LaneProperty("lane_code", Seq(LanePropertyValue(21)))))
    )

    val linearAssets = Map(
      roadLinkTowards2.linkId -> Seq( lane11 )
    )

    val (filledTopology, changeSet) = laneFiller.fillTopology(topology, linearAssets)

    filledTopology should have size 2
    filledTopology.map(_.sideCode) should be (Seq(SideCode.AgainstDigitizing.value, SideCode.TowardsDigitizing.value))
    filledTopology.map(_.id) should be (Seq(2L, 0L))
    filledTopology.map(_.linkId) should be (Seq(roadLinkTowards2.linkId, roadLinkTowards2.linkId))
    filledTopology.map(_.geometry) should be (Seq(Seq(Point(0.0, 0.0), Point(15.0, 0.0)), Seq(Point(0.0, 0.0), Point(15.0, 0.0))))

    changeSet.expiredLaneIds should be (Set())
  }

  test("Adjust lane that goes outside of roadLink length") {
    val topology = Seq( roadLinkTowards1 )

    val lane = PersistedLane(1L, roadLinkTowards1.linkId, TrafficDirection.toSideCode(roadLinkTowards1.trafficDirection).value,
      11, 745L, 0.0, 15.0, None, None, None, None, expired = false, 0L, None,
      LanePropertiesValues(Seq(LaneProperty("lane_code", Seq(LanePropertyValue(11)))))
    )

    val linearAssets = Map(
      roadLinkTowards1.linkId -> Seq( lane )
    )

    val (filledTopology, changeSet) = laneFiller.fillTopology(topology, linearAssets)

    filledTopology should have size 1
    filledTopology.map(_.sideCode) should be (Seq(SideCode.TowardsDigitizing.value))
    filledTopology.map(_.id) should be (Seq(1L))
    filledTopology.map(_.linkId) should be (Seq(1L))
    filledTopology.map(_.geometry) should be (Seq(Seq(Point(0, 0.0), Point(10.0, 0.0))))

    changeSet.expiredLaneIds should be (Set())
  }

  test("Adjust lanes that goes outside of roadLink length. Multiple roadLinks") {
    val topology = Seq( roadLinkTowards3, roadLinkTowards1 )

    val lane = PersistedLane(1L, roadLinkTowards3.linkId, SideCode.TowardsDigitizing.value,
      11, 745L, 0.0, 15.0, None, None, None, None, expired = false, 0L, None,
      LanePropertiesValues(Seq(LaneProperty("lane_code", Seq(LanePropertyValue(11)))))
    )

    val lane2 = PersistedLane(2L, roadLinkTowards3.linkId, SideCode.AgainstDigitizing.value,
      21, 745L, 5.0, 15.0, None, None, None, None, expired = false, 0L, None,
      LanePropertiesValues(Seq(LaneProperty("lane_code", Seq(LanePropertyValue(21)))))
    )

    val lane3 = PersistedLane(20L, roadLinkTowards1.linkId, SideCode.AgainstDigitizing.value,
      21, 745L, 5.0, 10.0, None, None, None, None, expired = false, 0L, None,
      LanePropertiesValues(Seq(LaneProperty("lane_code", Seq(LanePropertyValue(21)))))
    )


    val linearAssets = Map(
      roadLinkTowards3.linkId -> Seq( lane, lane2 ),
      roadLinkTowards1.linkId -> Seq( lane3 )
    )

    val (filledTopology, changeSet) = laneFiller.fillTopology(topology, linearAssets)

    filledTopology should have size 3
    filledTopology.map(_.sideCode) should be (Seq(SideCode.TowardsDigitizing.value, SideCode.AgainstDigitizing.value, SideCode.TowardsDigitizing.value))
    filledTopology.map(_.id) should be (Seq(1L, 2L, 0L))
    filledTopology.map(_.linkId) should be (Seq(3L, 3L, 1L))
    filledTopology.map(_.geometry) should be (Seq(Seq(Point(0.0, 0.0), Point(10.0, 0.0)), Seq(Point(5.0, 0.0), Point(10.0, 0.0)), Seq(Point(0.0, 0.0), Point(10.0, 0.0))))

    changeSet.expiredLaneIds should be (Set(20L))
  }

  test("Merge Lanes") {
    val topology = Seq( roadLinkTowards1 )

    val lane = PersistedLane(20L, roadLinkTowards1.linkId, SideCode.TowardsDigitizing.value,
      11, 745L, 0.0, 10.0, None, None, None, None, expired = false, 0L, None,
      LanePropertiesValues(Seq(LaneProperty("lane_code", Seq(LanePropertyValue(11)))))
    )

    val lane12 = PersistedLane(21L, roadLinkTowards1.linkId, SideCode.TowardsDigitizing.value,
      12, 745L, 7.0, 10.0, None, None, None, None, expired = false, 0L, None,
      LanePropertiesValues(Seq(LaneProperty("lane_code", Seq(LanePropertyValue(12)))))
    )

    val lane12b = PersistedLane(22L, roadLinkTowards1.linkId, SideCode.TowardsDigitizing.value,
      12, 745L, 4.0, 7.0, None, None, None, None, expired = false, 0L, None,
      LanePropertiesValues(Seq(LaneProperty("lane_code", Seq(LanePropertyValue(12)))))
    )

    val linearAssets = Map(
      roadLinkTowards1.linkId -> Seq( lane, lane12, lane12b )
    )

    val (filledTopology, changeSet) = laneFiller.fillTopology(topology, linearAssets)

    filledTopology should have size 2
    filledTopology.map(_.sideCode) should be (Seq(SideCode.TowardsDigitizing.value,  SideCode.TowardsDigitizing.value))
    filledTopology.map(_.id) should be (Seq(20L, 21L))
    filledTopology.map(_.linkId) should be (Seq( 1L, 1L))
    filledTopology.map(_.geometry) should be (Seq(Seq(Point(0.0, 0.0), Point(10.0, 0.0)), Seq(Point(4.0, 0.0), Point(10.0, 0.0)) ))

    changeSet.expiredLaneIds should be (Set(22L))
  }

  test("Merge Lanes and create missing one") {
    val topology = Seq( roadLinkTowards2 )

    val lane = PersistedLane(20L, roadLinkTowards2.linkId, SideCode.TowardsDigitizing.value,
      11, 745L, 0.0, 10.0, None, None, None, None, expired = false, 0L, None,
      LanePropertiesValues(Seq(LaneProperty("lane_code", Seq(LanePropertyValue(11)))))
    )

    val lane12 = PersistedLane(21L, roadLinkTowards2.linkId, SideCode.TowardsDigitizing.value,
      12, 745L, 7.0, 10.0, None, None, None, None, expired = false, 0L, None,
      LanePropertiesValues(Seq(LaneProperty("lane_code", Seq(LanePropertyValue(12)))))
    )

    val lane12b = PersistedLane(22L, roadLinkTowards2.linkId, SideCode.TowardsDigitizing.value,
      12, 745L, 4.0, 7.0, None, None, None, None, expired = false, 0L, None,
      LanePropertiesValues(Seq(LaneProperty("lane_code", Seq(LanePropertyValue(12)))))
    )

    val linearAssets = Map(
      roadLinkTowards2.linkId -> Seq( lane, lane12, lane12b )
    )

    val (filledTopology, changeSet) = laneFiller.fillTopology(topology, linearAssets)

    filledTopology should have size 3
    filledTopology.map(_.sideCode) should be (Seq(SideCode.TowardsDigitizing.value,  SideCode.TowardsDigitizing.value, SideCode.AgainstDigitizing.value))
    filledTopology.map(_.id) should be (Seq(20L, 21L, 0L))
    filledTopology.map(_.linkId) should be (Seq( 2L, 2L, 2L))
    filledTopology.map(_.geometry) should be (Seq(Seq(Point(0.0, 0.0), Point(10.0, 0.0)), Seq(Point(4.0, 0.0), Point(10.0, 0.0)), Seq(Point(0.0, 0.0), Point(15.0, 0.0)) ))

    changeSet.expiredLaneIds should be (Set(22L))
  }

  test("Drop lanes with less than 2 meters") {
    val topology = Seq( roadLinkTowards1 )

    val lane = PersistedLane(20L, roadLinkTowards1.linkId, SideCode.TowardsDigitizing.value,
      11, 745L, 1.0, 2.0, None, None, None, None, expired = false, 0L, None,
      LanePropertiesValues(Seq(LaneProperty("lane_code", Seq(LanePropertyValue(11)))))
    )

    val lane12 = PersistedLane(21L, roadLinkTowards1.linkId, SideCode.TowardsDigitizing.value,
      12, 745L, 8.0, 10.0, None, None, None, None, expired = false, 0L, None,
      LanePropertiesValues(Seq(LaneProperty("lane_code", Seq(LanePropertyValue(12)))))
    )


    val linearAssets = Map(
      roadLinkTowards1.linkId -> Seq( lane, lane12 )
    )

    val (filledTopology, changeSet) = laneFiller.fillTopology(topology, linearAssets)

    filledTopology should have size 2
    filledTopology.map(_.sideCode) should be (Seq(SideCode.TowardsDigitizing.value, SideCode.TowardsDigitizing.value ))
    filledTopology.map(_.id) should be (Seq(21L, 0L))
    filledTopology.map(_.linkId) should be (Seq( 1L, 1L))
    filledTopology.map(_.geometry) should be (Seq(Seq(Point(8.0, 0.0), Point(10.0, 0.0)), Seq(Point(0.0, 0.0), Point(10.0, 0.0)) ))

    changeSet.expiredLaneIds should be (Set(20L))
  }

  test("Don't drop lanes with less than 2 meters on a roadLink with length less than 2 meters") {
    val topology = Seq( roadLinkTowards4 )

    val lane = PersistedLane(20L, roadLinkTowards4.linkId, SideCode.AgainstDigitizing.value,
      21, 745L, 0.0, 1.9, None, None, None, None, expired = false, 0L, None,
      LanePropertiesValues(Seq(LaneProperty("lane_code", Seq(LanePropertyValue(21)))))
    )

    val lane12 = PersistedLane(21L, roadLinkTowards4.linkId, SideCode.AgainstDigitizing.value,
      22, 745L, 0.0, 1.0, None, None, None, None, expired = false, 0L, None,
      LanePropertiesValues(Seq(LaneProperty("lane_code", Seq(LanePropertyValue(22)))))
    )


    val linearAssets = Map(
      roadLinkTowards4.linkId -> Seq( lane, lane12 )
    )

    val (filledTopology, changeSet) = laneFiller.fillTopology(topology, linearAssets)

    filledTopology should have size 2
    filledTopology.map(_.sideCode) should be (Seq(SideCode.AgainstDigitizing.value, SideCode.AgainstDigitizing.value ))
    filledTopology.map(_.id) should be (Seq(20L, 21L))
    filledTopology.map(_.linkId) should be (Seq( 4L, 4L))

    changeSet.expiredLaneIds should be (Set())
  }

}
