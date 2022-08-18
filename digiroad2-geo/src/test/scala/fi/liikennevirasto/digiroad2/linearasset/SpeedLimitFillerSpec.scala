package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.GeometryUtils.Projection
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset.TrafficDirection.TowardsDigitizing
import org.joda.time.DateTime
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.{ChangeSet, MValueAdjustment, SideCodeAdjustment}
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import fi.liikennevirasto.digiroad2.asset._
import org.joda.time.format.DateTimeFormat
import org.scalatest._
import java.util.UUID
import scala.util.Random

class SpeedLimitFillerSpec extends FunSuite with Matchers {
  private def roadLink(linkId: String, geometry: Seq[Point], administrativeClass: AdministrativeClass = Unknown): RoadLink = {
    val municipalityCode = "MUNICIPALITYCODE" -> BigInt(235)
    RoadLink(
      linkId, geometry, GeometryUtils.geometryLength(geometry), administrativeClass, 1,
      TrafficDirection.BothDirections, Motorway, None, None, Map(municipalityCode))
  }

  private def generateRandomLinkId(): String = s"${UUID.randomUUID()}:${Random.nextInt(100)}"

  val linkId1: String = generateRandomLinkId()
  val linkId2: String = generateRandomLinkId()
  val linkId3: String = generateRandomLinkId()

  private def oneWayRoadLink(linkId: String, geometry: Seq[Point], trafficDirection: TrafficDirection) = {
    roadLink(linkId, geometry).copy(trafficDirection = trafficDirection)
  }

  test("drop speedlimit segments less than 2 meters"){
    val roadLink = RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
      TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    val assets = Seq(
      SpeedLimit(1, linkId1, SideCode.BothDirections, TrafficDirection.BothDirections, Some(SpeedLimitValue(80)), Seq(Point(0.0, 0.0),
        Point(1.9, 0.0)), 0.0, 1.9, None, None, None, None, 0, None, linkSource = NormalLinkInterface),
    SpeedLimit(2, linkId1, SideCode.BothDirections, TrafficDirection.BothDirections, Some(SpeedLimitValue(80)), Seq(Point(1.0, 0.0),
      Point(3.0, 0.0)), 2.0, 4.0, None, None, None, None, 0, None, linkSource = NormalLinkInterface)
    )

    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(Seq(roadLink), Map(linkId1 -> assets))
    filledTopology should have size 2
    filledTopology.map(_.id) should not contain (1)
    changeSet.expiredAssetIds should have size 1
    changeSet.expiredAssetIds.head should be (1)
  }

  test("Don't drop speedlimit segments less than 2 meters on a road link with length less that 2 meters"){
    val roadLink = RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(1.9, 0.0)), 1.9, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
      TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    val assets = Seq(
      SpeedLimit(1, linkId1, SideCode.BothDirections, TrafficDirection.BothDirections, Some(SpeedLimitValue(80)), Seq(Point(0.0, 0.0),
        Point(1.9, 0.0)), 0.0, 1.9, None, None, None, None, 0, None, linkSource = NormalLinkInterface)
    )

    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(Seq(roadLink), Map(linkId1 -> assets))
    filledTopology should have size 1
    filledTopology.map(_.id) should be (Seq(1))
    changeSet.droppedAssetIds should have size 0
  }

  test("drop segment outside of link geometry") {
    val topology = Seq(
      roadLink(linkId2, Seq(Point(1.0, 0.0), Point(2.0, 0.0))))
    val speedLimits = Map(linkId2 -> Seq(
      SpeedLimit(1, linkId2, SideCode.BothDirections, TrafficDirection.BothDirections, Some(SpeedLimitValue(80)), Seq(Point(1.0, 0.0), Point(2.0, 0.0)), 2.15, 2.35, None, None, None, None, 0, None, linkSource = NormalLinkInterface)))
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology, speedLimits)
    changeSet.expiredAssetIds should be(Set(1))
  }

  test("adjust speed limit to cover whole link when its the only speed limit to refer to the link") {
    val topology = Seq(
      roadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0))))
    val speedLimits = Map(linkId1 -> Seq(
      SpeedLimit(1, linkId1, SideCode.BothDirections, TrafficDirection.BothDirections, Some(SpeedLimitValue(40)), Seq(Point(2.0, 0.0), Point(9.0, 0.0)), 2.0, 9.0, None, None, None, None, 0, None, linkSource = NormalLinkInterface)))
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology, speedLimits)
    filledTopology.length should be(1)
    filledTopology.head.geometry should be(Seq(Point(0.0, 0.0), Point(10.0, 0.0)))
    filledTopology.head.startMeasure should be(0.0)
    filledTopology.head.endMeasure should be(10.0)
    changeSet should be(ChangeSet(Set.empty, Seq(MValueAdjustment(1, linkId1, 0, 10.0)), Nil, Nil, Set.empty, Nil))
  }

  test("adjust one way speed limits to cover whole link when there are no multiple speed limits on one side of the link") {
    val topology = Seq(
      roadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0))))
    val speedLimits = Map(
      linkId1 -> Seq(
        SpeedLimit(
          1, linkId1, SideCode.TowardsDigitizing, TrafficDirection.BothDirections, Some(SpeedLimitValue(40)),
          Seq(Point(2.0, 0.0), Point(9.0, 0.0)), 2.0, 9.0, None, None, None, None, 0, None, linkSource = NormalLinkInterface),
        SpeedLimit(
          2, linkId1, SideCode.AgainstDigitizing, TrafficDirection.BothDirections, Some(SpeedLimitValue(50)),
          Seq(Point(2.0, 0.0), Point(9.0, 0.0)), 2.0, 9.0, None, None, None, None, 0, None, linkSource = NormalLinkInterface)))
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology, speedLimits)
    filledTopology should have size 2
    filledTopology.map(_.geometry) should be(Seq(
      Seq(Point(0.0, 0.0), Point(10.0, 0.0)),
      Seq(Point(0.0, 0.0), Point(10.0, 0.0))))
    filledTopology.map(_.startMeasure) should be(Seq(0.0, 0.0))
    filledTopology.map(_.endMeasure) should be(Seq(10.0, 10.0))
    changeSet.adjustedMValues should have size 2
    changeSet.adjustedMValues should be(Seq(MValueAdjustment(1, linkId1, 0, 10.0), MValueAdjustment(2, linkId1, 0, 10.0)))
  }

  test("cap speed limit to road link geometry") {
    val topology = Seq(
      roadLink(linkId1, Seq(Point(0.0, 0.0), Point(100.0, 0.0))))
    val speedLimits = Map(linkId1 -> Seq(
      SpeedLimit(1, linkId1, SideCode.BothDirections, TrafficDirection.BothDirections, Some(SpeedLimitValue(40)), Seq(Point(0.0, 0.0), Point(90.0, 0.0)), 0.0, 90.0, None, None, None, None, 0, None, linkSource = NormalLinkInterface),
      SpeedLimit(2, linkId1, SideCode.BothDirections, TrafficDirection.BothDirections, Some(SpeedLimitValue(50)), Seq(Point(90.0, 0.0), Point(110.0, 0.0)), 90.0, 110.0, None, None, None, None, 0, None, linkSource = NormalLinkInterface)))
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology, speedLimits)
    filledTopology.length should be(2)

    filledTopology.find(_.id == 2).map(_.geometry) should be(Some(Seq(Point(90.0, 0.0), Point(100.0, 0.0))))
    filledTopology.find(_.id == 2).map(_.endMeasure) should be(Some(100.0))

    changeSet.adjustedMValues should be(Seq(MValueAdjustment(2, linkId1, 90.0, 100.0)))
  }

  test("drop short speed limit") {
    val topology = Seq(
      roadLink(linkId1, Seq(Point(0.0, 0.0), Point(100.00, 0.0))))
    val speedLimits = Map(
      linkId1 -> Seq(SpeedLimit(1, linkId1, SideCode.TowardsDigitizing, TrafficDirection.BothDirections, Some(SpeedLimitValue(40)), Seq(Point(0.0, 0.0), Point(0.04, 0.0)), 0.0, 0.04, None, None, None, None, 0, None, linkSource = NormalLinkInterface),
        SpeedLimit(2, linkId1, SideCode.TowardsDigitizing, TrafficDirection.BothDirections, Some(SpeedLimitValue(50)), Seq(Point(0.04, 0.0), Point(100.0, 0.0)), 0.04, 100.0, None, None, None, None, 0, None, linkSource = NormalLinkInterface)))
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology, speedLimits)
    filledTopology should have size 1
    changeSet.expiredAssetIds should be(Set(1))
  }

  test("do not drop short speed limit if it fills the road length") {
  val topology = Seq(
    roadLink(linkId1, Seq(Point(0.0, 0.0), Point(0.4, 0.0))))
  val speedLimits = Map(
    linkId1 -> Seq(SpeedLimit(1, linkId1, SideCode.TowardsDigitizing, TrafficDirection.BothDirections, Some(SpeedLimitValue(40)), Seq(Point(0.0, 0.0), Point(0.4, 0.0)), 0.0, 0.4, None, None, None, None, 0, None, linkSource = NormalLinkInterface)))
  val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology, speedLimits)
  filledTopology should have size 1
  changeSet.droppedAssetIds should be(Set())
}

  test("should not drop adjusted short speed limit") {
    val topology = Seq(
      roadLink(linkId1, Seq(Point(0.0, 0.0), Point(1.0, 0.0))))
    val speedLimits = Map(
      linkId1 -> Seq(SpeedLimit(1, linkId1, SideCode.TowardsDigitizing, TrafficDirection.BothDirections, Some(SpeedLimitValue(40)), Seq(Point(0.0, 0.0), Point(0.4, 0.0)), 0.0, 0.4, None, None, None, None, 0, None, linkSource = NormalLinkInterface)))
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology, speedLimits)
    filledTopology should have size 1
    filledTopology.map(_.id) should be(Seq(1))
    changeSet.droppedAssetIds shouldBe empty
  }

  test("adjust side code of a speed limit") {
    val topology = Seq(
      roadLink(linkId1, Seq(Point(0.0, 0.0), Point(1.0, 0.0))))
    val speedLimits = Map(
      linkId1 -> Seq(SpeedLimit(1, linkId1, SideCode.TowardsDigitizing, TrafficDirection.BothDirections, Some(SpeedLimitValue(40)), Seq(Point(0.0, 0.0), Point(1.0, 0.0)), 0.0, 1.0, None, None, None, None, 0, None, linkSource = NormalLinkInterface)))
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology, speedLimits)
    filledTopology should have size 1
    filledTopology.map(_.sideCode) should be(Seq(SideCode.BothDirections))
    changeSet.adjustedSideCodes should have size 1
    changeSet.adjustedSideCodes.head should be(SideCodeAdjustment(1, SideCode.BothDirections, SpeedLimitAsset.typeId))
  }

  test("adjust one-way speed limits on one-way road link into two-way speed limits") {
    val topology = Seq(
      oneWayRoadLink(linkId1, Seq(Point(0.0, 0.0), Point(2.0, 0.0)), TrafficDirection.TowardsDigitizing))
    val speedLimits = Map(
      linkId1 -> Seq(SpeedLimit(1, linkId1, SideCode.TowardsDigitizing, TrafficDirection.TowardsDigitizing, Some(SpeedLimitValue(40)), Seq(Point(0.0, 0.0), Point(1.0, 0.0)), 0.0, 1.0, None, None, None, None, 0, None, linkSource = NormalLinkInterface),
        SpeedLimit(2, linkId1, SideCode.TowardsDigitizing, TrafficDirection.TowardsDigitizing, Some(SpeedLimitValue(50)), Seq(Point(1.0, 0.0), Point(2.0, 0.0)), 1.0, 2.0, None, None, None, None, 0, None, linkSource = NormalLinkInterface)))
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology, speedLimits)
    filledTopology should have size 2
    filledTopology.map(_.sideCode) should be(Seq(SideCode.BothDirections, SideCode.BothDirections))
    changeSet.adjustedSideCodes should have size 2
    changeSet.adjustedSideCodes.toSet should be(Set(SideCodeAdjustment(1, SideCode.BothDirections, SpeedLimitAsset.typeId), SideCodeAdjustment(2, SideCode.BothDirections, SpeedLimitAsset.typeId)))
  }

  test("merge speed limits with same value on shared road link") {
    val topology = Seq(
      roadLink(linkId1, Seq(Point(0.0, 0.0), Point(1.0, 0.0))))
    val speedLimits = Map(
      linkId1 -> Seq(
        SpeedLimit(
          1, linkId1, SideCode.TowardsDigitizing, TrafficDirection.BothDirections, Some(SpeedLimitValue(40)),
          Seq(Point(0.0, 0.0), Point(0.2, 0.0)), 0.0, 0.2, None, None, Some("one"), Some(DateTime.now().minus(1000)), 0, None, linkSource = NormalLinkInterface),
        SpeedLimit(
          2, linkId1, SideCode.AgainstDigitizing, TrafficDirection.BothDirections, Some(SpeedLimitValue(40)),
          Seq(Point(0.2, 0.0), Point(0.55, 0.0)), 0.2, 0.55, Some("one else"), Some(DateTime.now().minus(500)),
          Some("one"), Some(DateTime.now().minus(1000)), 0, None, linkSource = NormalLinkInterface),
        SpeedLimit(
          3, linkId1, SideCode.BothDirections, TrafficDirection.BothDirections, Some(SpeedLimitValue(40)),
          Seq(Point(0.55, 0.0), Point(1.0, 0.0)), 0.55, 1.0, Some("random guy"), Some(DateTime.now().minus(450)),
          Some("one"), Some(DateTime.now().minus(1100)), 0, None, linkSource = NormalLinkInterface)))
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology, speedLimits)
    filledTopology should have size 1
    filledTopology.map(_.sideCode) should be(Seq(SideCode.BothDirections))
    filledTopology.map(_.value) should be(Seq(Some(SpeedLimitValue(40))))
    filledTopology.map(_.modifiedBy) should be (Seq(Some("random guy"))) // latest modification should show
    changeSet.adjustedMValues should be(Seq(MValueAdjustment(3, linkId1, 0.0, 1.0)))
    changeSet.adjustedSideCodes should be(List())
    changeSet.expiredAssetIds should be(Set(1, 2))
  }

  test("create unknown speed limit on empty segments") {
    val topology = Seq(
      roadLink(linkId1, Seq(Point(0.0, 0.0), Point(100.0, 0.0)), State))
    val speedLimits = Map.empty[String, Seq[SpeedLimit]]
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology, speedLimits)
    filledTopology should have size 1
    filledTopology.map(_.sideCode) should be(Seq(SideCode.BothDirections))
    filledTopology.map(_.value) should be(Seq(None))
    filledTopology.map(_.id) should be(Seq(0))
  }

  test("project speed limits to new geometry, case 1 - single speed, both directions") {
    val oldRoadLink = roadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)))
    val newLink1 = roadLink(linkId1, Seq(Point(0.0, 0.0), Point(3.0, 0.0)))
    val newLink2 = roadLink(linkId2, Seq(Point(0.0, 0.0), Point(4.0, 0.0)))
    val newLink3 = roadLink(linkId3, Seq(Point(0.0, 0.0), Point(3.0, 0.0)))
    val linkmap = Map(linkId1 -> newLink1, linkId2 -> newLink2, linkId3 -> newLink3)
    val speedLimit = Seq(
      SpeedLimit(
        1, linkId1, SideCode.BothDirections, TrafficDirection.BothDirections, Some(SpeedLimitValue(40)),
        Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 0.0, 10.0, None, None, None, None, 0, None, linkSource = NormalLinkInterface))
    val changes = Seq(ChangeInfo(Some(linkId1), Some(linkId1), 2l, 5, Some(0.0), Some(3.0), Some(0.0), Some(3.0), Some(1440000)),
      ChangeInfo(Some(linkId1), Some(linkId2), 22, 6, Some(3.0), Some(7.0), Some(0.0), Some(4.0), Some(1440000)),
      ChangeInfo(Some(linkId1), Some(linkId3), 23, 6, Some(7.0), Some(10.0), Some(3.0), Some(0.0), Some(1440000))
    )
    val output = changes map { change =>
      SpeedLimitFiller.projectSpeedLimit(speedLimit.head, linkmap.get(change.newId.get).get,
        Projection(change.oldStartMeasure.get, change.oldEndMeasure.get, change.newStartMeasure.get, change.newEndMeasure.get, change.timeStamp.get),
        ChangeSet(Set.empty, Nil, Nil, Nil, Set.empty, Nil)) }
    output.length should be(3)
    output.head._1.trafficDirection should be (TrafficDirection.BothDirections)
    output.head._1.startMeasure should be(0.0)
    output.head._1.endMeasure should be(3.0)
  }

  test("project speed limits to new geometry, case 2 - different speeds to different directions") {
    val oldRoadLink = roadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)))
    val newLink1 = roadLink(linkId1, Seq(Point(0.0, 0.0), Point(3.0, 0.0)))
    val newLink2 = roadLink(linkId2, Seq(Point(0.0, 0.0), Point(4.0, 0.0)))
    val newLink3 = roadLink(linkId3, Seq(Point(0.0, 0.0), Point(3.0, 0.0)))
    val linkmap = Map(linkId1 -> newLink1, linkId2 -> newLink2, linkId3 -> newLink3)
    val speedLimit = Seq(
      SpeedLimit(
        1, linkId1, SideCode.TowardsDigitizing, TrafficDirection.BothDirections, Some(SpeedLimitValue(40)),
        Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 0.0, 10.0, None, None, None, None, 0, None, linkSource = NormalLinkInterface),
      SpeedLimit(
        2, linkId1, SideCode.AgainstDigitizing, TrafficDirection.BothDirections, Some(SpeedLimitValue(50)),
        Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 0.0, 10.0, None, None, None, None, 0, None, linkSource = NormalLinkInterface))

    val changes = Seq(ChangeInfo(Some(linkId1), Some(linkId1), 2l, 5, Some(0.0), Some(3.0), Some(0.0), Some(3.0), Some(1440000)),
      ChangeInfo(Some(linkId1), Some(linkId2), 22, 6, Some(3.0), Some(7.0), Some(0.0), Some(4.0), Some(1440000)),
      ChangeInfo(Some(linkId1), Some(linkId3), 23, 6, Some(7.0), Some(10.0), Some(3.0), Some(0.0), Some(1440000))
    )

    val output = changes map { change =>
      SpeedLimitFiller.projectSpeedLimit(speedLimit.head, linkmap.get(change.newId.get).get,
        Projection(change.oldStartMeasure.get, change.oldEndMeasure.get, change.newStartMeasure.get, change.newEndMeasure.get, change.timeStamp.get),
        ChangeSet(Set.empty, Nil, Nil, Nil, Set.empty, Nil)) }
    output.head._1.sideCode should be (SideCode.TowardsDigitizing)
    output.last._1.sideCode should be (SideCode.AgainstDigitizing)
    output.head._1.startMeasure should be(0.0)
    output.head._1.endMeasure should be(3.0)
    output.last._1.startMeasure should be(0.0)
    output.last._1.endMeasure should be(3.0)
    output.foreach( out => out._1.value should be (Some(SpeedLimitValue(40))))

    val output2 = changes map { change =>
      SpeedLimitFiller.projectSpeedLimit(speedLimit.last, linkmap.get(change.newId.get).get,
        Projection(change.oldStartMeasure.get, change.oldEndMeasure.get, change.newStartMeasure.get, change.newEndMeasure.get, change.timeStamp.get),
        ChangeSet(Set.empty, Nil, Nil, Nil, Set.empty, Nil)) }
    output2.length should be(3)
    output2.head._1.sideCode should be (SideCode.AgainstDigitizing)
    output2.last._1.sideCode should be (SideCode.TowardsDigitizing)
    output2.head._1.startMeasure should be(0.0)
    output2.head._1.endMeasure should be(3.0)
    output2.last._1.startMeasure should be(0.0)
    output2.last._1.endMeasure should be(3.0)
    output2.foreach(out => out._1.value should be (Some(SpeedLimitValue(50))))
  }

  test("project speed limits to new geometry, case 3 - speed changes in the middle of the roadlink") {
    val oldRoadLink = roadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)))
    val newLink1 = roadLink(linkId1, Seq(Point(0.0, 0.0), Point(3.0, 0.0)))
    val newLink2 = roadLink(linkId2, Seq(Point(0.0, 0.0), Point(4.0, 0.0)))
    val newLink3 = roadLink(linkId3, Seq(Point(0.0, 0.0), Point(3.0, 0.0)))
    val linkmap = Map(linkId1 -> newLink1, linkId2 -> newLink2, linkId3 -> newLink3)
    val speedLimit = Seq(
      SpeedLimit(
        1, linkId1, SideCode.BothDirections, TrafficDirection.BothDirections, Some(SpeedLimitValue(40)),
        Seq(Point(0.0, 0.0), Point(4.5, 0.0)), 0.0, 4.5, None, None, None, None, 0, None, linkSource = NormalLinkInterface),
      SpeedLimit(
        2, linkId1, SideCode.BothDirections, TrafficDirection.BothDirections, Some(SpeedLimitValue(50)),
        Seq(Point(4.5, 0.0), Point(10.0, 0.0)), 4.5, 10.0, None, None, None, None, 0, None, linkSource = NormalLinkInterface))

    val changes = Seq(ChangeInfo(Some(linkId1), Some(linkId1), 2l, 5, Some(0.0), Some(3.0), Some(0.0), Some(3.0), Some(1440000)),
      ChangeInfo(Some(linkId1), Some(linkId2), 22, 6, Some(3.0), Some(7.0), Some(0.0), Some(4.0), Some(1440000)),
      ChangeInfo(Some(linkId1), Some(linkId3), 23, 6, Some(7.0), Some(10.0), Some(3.0), Some(0.0), Some(1440000))
    )

    val output = changes flatMap { change =>
      speedLimit.map(
        SpeedLimitFiller.projectSpeedLimit(_, linkmap.get(change.newId.get).get,
          Projection(change.oldStartMeasure.get, change.oldEndMeasure.get, change.newStartMeasure.get, change.newEndMeasure.get, change.timeStamp.get),
          ChangeSet(Set.empty, Nil, Nil, Nil, Set.empty, Nil))._1) }  filter(sl => sl.startMeasure != sl.endMeasure)

    output.foreach(_.sideCode should be (SideCode.BothDirections))
    output.head.startMeasure should be(0.0)
    output.head.endMeasure should be(3.0)
    output.head.value should be(Some(SpeedLimitValue(40)))
    output.last.startMeasure should be(0.0)
    output.last.endMeasure should be(3.0)
    output.last.value should be(Some(SpeedLimitValue(50)))
    output.length should be (4)
  }

  test("project speed limits to new geometry, case 3b - speed changes in the middle of the roadlink, digitization switches there") {
    val oldRoadLink = roadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)))
    val newLink1 = roadLink(linkId1, Seq(Point(0.0, 0.0), Point(3.0, 0.0)))
    val newLink2 = roadLink(linkId2, Seq(Point(0.0, 0.0), Point(4.0, 0.0)))
    val newLink3 = roadLink(linkId3, Seq(Point(0.0, 0.0), Point(3.0, 0.0)))
    val linkmap = Map(linkId1 -> newLink1, linkId2 -> newLink2, linkId3 -> newLink3)
    val speedLimit = Seq(
      SpeedLimit(
        1, linkId1, SideCode.BothDirections, TrafficDirection.BothDirections, Some(SpeedLimitValue(40)),
        Seq(Point(0.0, 0.0), Point(7.5, 0.0)), 0.0, 7.5, None, None, None, None, 0, None, linkSource = NormalLinkInterface),
      SpeedLimit(
        2, linkId1, SideCode.BothDirections, TrafficDirection.BothDirections, Some(SpeedLimitValue(50)),
        Seq(Point(7.5, 0.0), Point(10.0, 0.0)), 7.5, 10.0, None, None, None, None, 0, None, linkSource = NormalLinkInterface))

    val changes = Seq(ChangeInfo(Some(linkId1), Some(linkId1), 2l, 5, Some(0.0), Some(3.0), Some(0.0), Some(3.0), Some(1440000)),
      ChangeInfo(Some(linkId1), Some(linkId2), 22, 6, Some(3.0), Some(7.0), Some(0.0), Some(4.0), Some(1440000)),
      ChangeInfo(Some(linkId1), Some(linkId3), 23, 6, Some(7.0), Some(10.0), Some(3.0), Some(0.0), Some(1440000))
    )

    val output = changes flatMap { change =>
      speedLimit.map(
        SpeedLimitFiller.projectSpeedLimit(_, linkmap.get(change.newId.get).get,
          Projection(change.oldStartMeasure.get, change.oldEndMeasure.get, change.newStartMeasure.get, change.newEndMeasure.get, change.timeStamp.get),
          ChangeSet(Set.empty, Nil, Nil, Nil, Set.empty, Nil))._1) }  filter(sl => sl.startMeasure != sl.endMeasure)

    output.foreach(_.sideCode should be (SideCode.BothDirections))
    output.length should be (4)
    val (link3sl1, link3sl2) = (output.tail.tail.head, output.last)
    link3sl1.startMeasure should be (2.5)
    link3sl2.startMeasure should be (0)
    link3sl1.endMeasure should be (3)
    link3sl2.endMeasure should be (2.5)
  }

  test("project speed limits to new geometry, case 4 - speed changes in the middle of the roadlink, different for different directions") {
    val oldRoadLink = roadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)))
    val newLink1 = roadLink(linkId1, Seq(Point(0.0, 0.0), Point(3.0, 0.0)))
    val newLink2 = roadLink(linkId2, Seq(Point(0.0, 0.0), Point(4.0, 0.0)))
    val newLink3 = roadLink(linkId3, Seq(Point(0.0, 0.0), Point(3.0, 0.0)))
    val linkmap = Map(linkId1 -> newLink1, linkId2 -> newLink2, linkId3 -> newLink3)
    val speedLimit = Seq(
      SpeedLimit(
        1, linkId1, SideCode.TowardsDigitizing, TrafficDirection.BothDirections, Some(SpeedLimitValue(40)),
        Seq(Point(0.0, 0.0), Point(4.5, 0.0)), 0.0, 4.5, None, None, None, None, 0, None, linkSource = NormalLinkInterface),
      SpeedLimit(
        2, linkId1, SideCode.TowardsDigitizing, TrafficDirection.BothDirections, Some(SpeedLimitValue(50)),
        Seq(Point(4.5, 0.0), Point(10.0, 0.0)), 4.5, 10.0, None, None, None, None, 0, None, linkSource = NormalLinkInterface),
      SpeedLimit(
        3, linkId1, SideCode.AgainstDigitizing, TrafficDirection.BothDirections, Some(SpeedLimitValue(30)),
        Seq(Point(0.0, 0.0), Point(4.5, 0.0)), 0.0, 4.5, None, None, None, None, 0, None, linkSource = NormalLinkInterface),
      SpeedLimit(
        4, linkId1, SideCode.AgainstDigitizing, TrafficDirection.BothDirections, Some(SpeedLimitValue(60)),
        Seq(Point(4.5, 0.0), Point(10.0, 0.0)), 4.5, 10.0, None, None, None, None, 0, None, linkSource = NormalLinkInterface))

    val changes = Seq(ChangeInfo(Some(linkId1), Some(linkId1), 2l, 5, Some(0.0), Some(3.0), Some(0.0), Some(3.0), Some(1440000)),
      ChangeInfo(Some(linkId1), Some(linkId2), 22, 6, Some(3.0), Some(7.0), Some(0.0), Some(4.0), Some(1440000)),
      ChangeInfo(Some(linkId1), Some(linkId3), 23, 6, Some(7.0), Some(10.0), Some(3.0), Some(0.0), Some(1440000))
    )

    val output = changes flatMap { change =>
      speedLimit.map(
        SpeedLimitFiller.projectSpeedLimit(_, linkmap.get(change.newId.get).get,
          Projection(change.oldStartMeasure.get, change.oldEndMeasure.get, change.newStartMeasure.get, change.newEndMeasure.get, change.timeStamp.get),
          ChangeSet(Set.empty, Nil, Nil, Nil, Set.empty, Nil))._1) }  filter(sl => sl.startMeasure != sl.endMeasure)

    output.filter(_.linkId == linkId1).count(_.value.contains(SpeedLimitValue(30))) should be (1)
    output.filter(_.linkId == linkId1).count(_.value.contains(SpeedLimitValue(40))) should be (1)
    output.filter(_.linkId == linkId2).count(_.value.contains(SpeedLimitValue(30))) should be (1)
    output.filter(_.linkId == linkId2).count(_.value.contains(SpeedLimitValue(40))) should be (1)
    output.filter(_.linkId == linkId2).count(_.value.contains(SpeedLimitValue(50))) should be (1)
    output.filter(_.linkId == linkId2).count(_.value.contains(SpeedLimitValue(60))) should be (1)
    output.filter(_.linkId == linkId3).count(_.value.contains(SpeedLimitValue(50))) should be (1)
    output.filter(_.linkId == linkId3).count(_.value.contains(SpeedLimitValue(60))) should be (1)
    output.length should be (8)
  }

  test("test failing combination") {
    val oldLinkId = generateRandomLinkId()
    val newLinkId1 = generateRandomLinkId()
    val newLinkId2 = generateRandomLinkId()
    val newLinkId3 = generateRandomLinkId()

    val oldRoadLink = roadLink(oldLinkId, Seq(Point(0.0, 0.0), Point(171.02731386, 0.0)))
    val newLink1 = roadLink(newLinkId1, Seq(Point(0.0, 0.0), Point(106.96978931, 0.0)))
    val newLink2 = roadLink(newLinkId2, Seq(Point(0.0, 0.0), Point(57.4888233, 0.0)))
    val newLink3 = roadLink(newLinkId3, Seq(Point(0.0, 0.0), Point(6.56875041, 0.0)))
    val linkmap = Map(newLinkId1 -> newLink1, newLinkId2 -> newLink2, newLinkId3 -> newLink3)
    val speedLimit = Seq(
      SpeedLimit(
        1, oldRoadLink.linkId, SideCode.BothDirections, TrafficDirection.BothDirections, Some(SpeedLimitValue(40)),
        Seq(Point(0.0, 0.0), Point(54.825, 0.0)), 0.0, 54.825, None, None, None, None, 0, None, linkSource = NormalLinkInterface),
      SpeedLimit(
        2, linkId1, SideCode.BothDirections, TrafficDirection.BothDirections, Some(SpeedLimitValue(50)),
        Seq(Point(54.825, 0.0), Point(171.027, 0.0)), 54.825, 171.027, None, None, None, None, 0, None, linkSource = NormalLinkInterface))

    val changes = Seq(ChangeInfo(Some(oldRoadLink.linkId), Some(newLink2.linkId), 2l, 5, Some(113.53850795), Some(171.02731386), Some(0.0), Some(57.4888233), Some(1461844024000L)),
      ChangeInfo(Some(oldRoadLink.linkId), Some(newLink1.linkId), 2l, 6, Some(6.56875026), Some(113.53850795), Some(0.0), Some(106.96978931), Some(1461844024000L)),
      ChangeInfo(Some(oldRoadLink.linkId), Some(newLink3.linkId), 2l, 6, Some(0.0), Some(6.56875026), Some(0.0), Some(6.56875026), Some(1461844024000L))
    )

    val output = changes flatMap { change =>
      speedLimit.map(
        SpeedLimitFiller.projectSpeedLimit(_, linkmap.get(change.newId.get).get,
          Projection(change.oldStartMeasure.get, change.oldEndMeasure.get, change.newStartMeasure.get, change.newEndMeasure.get, change.timeStamp.get),
          ChangeSet(Set.empty, Nil, Nil, Nil, Set.empty, Nil))._1) } filter(sl => sl.startMeasure != sl.endMeasure)
  }

  test("Should repair speed limit data on overlaps and invalid data") {
    val rLink = roadLink(linkId1, Seq(Point(0.0, 0.0), Point(50.0, 0.0)))
    val bdblue = Option(DateTime.now().minusDays(6))
    val sdblue = Option(DateTime.now().minusDays(5))
    val bdred = Option(DateTime.now().minusDays(4))
    val sdred1 = Option(DateTime.now().minusDays(3).minusHours(1))
    val sdred2 = Option(DateTime.now().minusDays(3))
    val sdred3 = Option(DateTime.now().minusDays(3).plusHours(1))
    val bdgreen = Option(DateTime.now().minusDays(2))
    val sdgreen = Option(DateTime.now().minusDays(1))
    val speedLimit = Seq(
      SpeedLimit(
        1, linkId1, SideCode.BothDirections, TrafficDirection.BothDirections, Some(SpeedLimitValue(60)),
        Seq(Point(26.67, 0.0), Point(43.33, 0.0)), 26.67, 43.33, None, None, Some("blue bd"), bdblue, 0, None, linkSource = NormalLinkInterface),
      SpeedLimit(
        2, linkId1, SideCode.BothDirections, TrafficDirection.BothDirections, Some(SpeedLimitValue(50)),
        Seq(Point(10.00, 0.0), Point(26.67, 0.0)), 10.0, 26.67, None, None, Some("red bd"), bdred, 0, None, linkSource = NormalLinkInterface),
      SpeedLimit(
        3, linkId1, SideCode.BothDirections, TrafficDirection.BothDirections, Some(SpeedLimitValue(40)),
        Seq(Point(50.0, 0.0), Point(60.0, 0.0)), 50.0, 60.0, None, None, Some("green bd"), bdgreen, 0, None, linkSource = NormalLinkInterface),
      SpeedLimit(
        4, linkId1, SideCode.TowardsDigitizing, TrafficDirection.BothDirections, Some(SpeedLimitValue(60)),
        Seq(Point(16.67, 0.0), Point(33.33, 0.0)), 16.67, 33.33, None, None, Some("blue td"), sdblue, 0, None, linkSource = NormalLinkInterface),
      SpeedLimit(
        5, linkId1, SideCode.AgainstDigitizing, TrafficDirection.BothDirections, Some(SpeedLimitValue(60)),
        Seq(Point(23.33, 0.0), Point(36.67, 0.0)), 23.33, 36.67, None, None, Some("blue ad"), sdblue, 0, None, linkSource = NormalLinkInterface),
      SpeedLimit(
        6, linkId1, SideCode.TowardsDigitizing, TrafficDirection.BothDirections, Some(SpeedLimitValue(50)),
        Seq(Point(0.0, 0.0), Point(16.67, 0.0)), 0.0, 16.67, None, None, Some("red td"), sdred1, 0, None, linkSource = NormalLinkInterface),
      SpeedLimit(
        7, linkId1, SideCode.AgainstDigitizing, TrafficDirection.BothDirections, Some(SpeedLimitValue(50)),
        Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 0.0, 10.0, None, None, Some("red ad"), sdred2, 0, None, linkSource = NormalLinkInterface),
      SpeedLimit(
        8, linkId1, SideCode.AgainstDigitizing, TrafficDirection.BothDirections, Some(SpeedLimitValue(50)),
        Seq(Point(10.0, 0.0), Point(23.33, 0.0)), 10.0, 23.33, None, None, Some("red ad"), sdred3, 0, None, linkSource = NormalLinkInterface),
      SpeedLimit(
        9, linkId1, SideCode.TowardsDigitizing, TrafficDirection.BothDirections, Some(SpeedLimitValue(40)),
        Seq(Point(33.33, 0.0), Point(50.0, 0.0)), 33.33, 50.0, None, None, Some("green bd"), sdgreen, 0, None, linkSource = NormalLinkInterface),
      SpeedLimit(
        10, linkId1, SideCode.AgainstDigitizing, TrafficDirection.BothDirections, Some(SpeedLimitValue(40)),
        Seq(Point(36.67, 0.0), Point(50.0, 0.0)), 36.67, 50.0, None, None, Some("green bd"), sdgreen, 0, None, linkSource = NormalLinkInterface)
    )

    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(Seq(rLink), Map(linkId1 -> speedLimit))

    changeSet.expiredAssetIds should have size 4
    filledTopology.count(_.id != 0) should be (6)
    filledTopology.forall(_.value.nonEmpty) should be (true)

    // Test that filler is stable
    var counter = 0
    var unstable = true
    var topology = filledTopology
    while (counter < 100 && unstable) {
      counter = counter + 1
      val (refill, newChangeSet) = SpeedLimitFiller.fillTopology(Seq(rLink), Map(linkId1 -> topology.map(sl => sl.copy(id = sl.id+1))))
      unstable = refill.size != topology.size || !refill.forall(sl => topology.find(_.id == sl.id-1).get.copy(id = sl.id).equals(sl))
      topology = refill
    }
    counter should be < (100)
    unstable should be (false)
    topology should have size (5)
  }

  test("Should split older asset if necessary") {
    val rLink = roadLink(linkId1, Seq(Point(0.0, 0.0), Point(50.0, 0.0)))
    val linkmap = Map(linkId1 -> rLink)
    val edit1 = Option(DateTime.now().minusDays(7))
    val edit2 = Option(DateTime.now().minusDays(6))
    val speedLimit = Seq(
      SpeedLimit(
        1, linkId1, SideCode.BothDirections, TrafficDirection.BothDirections, Some(SpeedLimitValue(60)),
        Seq(Point(0.0, 0.0), Point(50.0, 0.0)), 0.0, 50.0, None, None, Some("blue bd"), edit1, 0, None, linkSource = NormalLinkInterface),
      SpeedLimit(
        2, linkId1, SideCode.BothDirections, TrafficDirection.BothDirections, Some(SpeedLimitValue(50)),
        Seq(Point(10.00, 0.0), Point(26.67, 0.0)), 10.0, 26.67, None, None, Some("red bd"), edit2, 0, None, linkSource = NormalLinkInterface))

    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(Seq(rLink), Map(linkId1 -> speedLimit))

    changeSet.droppedAssetIds should be (Set())
    filledTopology.length should be (3)
    filledTopology.map(_.id).toSet should be (Set(0,1,2))
    val newLink = filledTopology.find(_.startMeasure==0.0).get
    newLink.endMeasure should be (10.0)
    newLink.value.get should be (SpeedLimitValue(60))
    val oldLink1 = filledTopology.find(_.startMeasure==26.67).get
    oldLink1.endMeasure should be (50.0)
    oldLink1.value should be (Some(SpeedLimitValue(60)))
    val oldLink2 = filledTopology.find(_.startMeasure==10.0).get
    oldLink2.startMeasure should be (10.0)
    oldLink2.endMeasure should be (26.67)
    oldLink2.value should be (Some(SpeedLimitValue(50)))

    val (refill, newChangeSet) = SpeedLimitFiller.fillTopology(Seq(rLink), Map(linkId1 -> filledTopology))
    refill should have size 3
    newChangeSet.adjustedMValues should have size 0
  }

  test("Should fill any holes it creates") {
    val rLink = roadLink(linkId1, Seq(Point(0.0, 0.0), Point(50.0, 0.0)))
    val linkmap = Map(linkId1 -> rLink)
    val edit1 = Option(DateTime.now().minusDays(7))
    val edit2 = Option(DateTime.now().minusDays(6))
    val speedLimit = Seq(
      SpeedLimit(
        1, linkId1, SideCode.BothDirections, TrafficDirection.BothDirections, Some(SpeedLimitValue(60)),
        Seq(Point(0.0, 0.0), Point(50.0, 0.0)), 0.0, 50.0, None, None, Some("blue bd"), edit1, 0, None, linkSource = NormalLinkInterface),
      SpeedLimit(
        2, linkId1, SideCode.BothDirections, TrafficDirection.BothDirections, Some(SpeedLimitValue(50)),
        Seq(Point(10.00, 0.0), Point(26.67, 0.0)), 10.0, 26.7, None, None, Some("red bd"), edit2, 1, None, linkSource = NormalLinkInterface),
      SpeedLimit(
        3, linkId1, SideCode.BothDirections, TrafficDirection.BothDirections, Some(SpeedLimitValue(50)),
        Seq(Point(26.8, 0.0), Point(50, 0.0)), 26.74, 50, None, None, Some("red bd"), edit2, 0, None, linkSource = NormalLinkInterface)
    )

    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(Seq(rLink), Map(linkId1 -> speedLimit))
    changeSet.expiredAssetIds should be (Set(3))
    filledTopology.length should be (2)
    val oldLink0 = filledTopology.find(_.startMeasure==0.0).get
    oldLink0.endMeasure should be (10.0)
    oldLink0.value.get should be (SpeedLimitValue(60))
    val oldLink1 = filledTopology.find(_.startMeasure==10.0).get
    oldLink1.endMeasure should be (50.0)
    oldLink1.value.get should be (SpeedLimitValue(50))
  }

  def parse(string: String): DateTime = {
    val dateTimePropertyFormat = DateTimeFormat.forPattern("dd.MM.yyyy HH:mm")
    DateTime.parse(string, dateTimePropertyFormat)
  }

  def printSL(speedLimit: SpeedLimit): Unit = {
    val ids = s"%d (${speedLimit.linkId})".format(speedLimit.id)
    val dir = speedLimit.sideCode match {
      case SideCode.BothDirections => "⇅"
      case SideCode.TowardsDigitizing => "↑"
      case SideCode.AgainstDigitizing => "↓"
      case _ => "?"
    }
    val details = "%d %.1f %.1f".format(speedLimit.value.getOrElse(SpeedLimitValue(0)).value, speedLimit.startMeasure, speedLimit.endMeasure)
    if (speedLimit.expired) {
      println("N/A")
    } else {
      println("%s %s %s".format(ids, dir, details))
    }
  }

  test("should return sensible geometry on combinable entries") {
    val linkId = generateRandomLinkId()
    val rLink = roadLink(linkId, Seq(Point(0.0, 0.0), Point(66.463, 0.0)))
    val speedLimit = Seq(
      SpeedLimit(1183653,linkId,SideCode.apply(2),TrafficDirection.apply(1),Option(SpeedLimitValue(100)),Seq(),42.545,66.463,None,None,Option("dr1_conversion"),Option(parse("28.10.2014 14:56")),0,Option(parse("28.10.2014 14:56")), linkSource = NormalLinkInterface),
      SpeedLimit(1204429,linkId,SideCode.apply(3),TrafficDirection.apply(1),Option(SpeedLimitValue(100)),Seq(),42.545,66.463,None,None,Option("dr1_conversion"),Option(parse("28.10.2014 14:59")),0,Option(parse("28.10.2014 14:59")), linkSource = NormalLinkInterface),
      SpeedLimit(1232110,linkId,SideCode.apply(2),TrafficDirection.apply(1),Option(SpeedLimitValue(80)),Seq(),0,42.545,None,None,Option("dr1_conversion"),Option(parse("28.10.2014 15:02")),0,Option(parse("28.10.2014 15:02")), linkSource = NormalLinkInterface),
      SpeedLimit(1868563,linkId,SideCode.apply(3),TrafficDirection.apply(1),Option(SpeedLimitValue(80)),Seq(),0,42.545,Option("vvh_generated"),Option(parse("14.6.2016 16:10")),Option("split_speedlimit_1183653"),Option(parse("2.7.2015 10:58")),0,Option(parse("2.7.2015 10:58")), linkSource = NormalLinkInterface)
    )

    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(Seq(rLink), speedLimit.groupBy(_.linkId))
    changeSet.expiredAssetIds should have size (2)
    changeSet.adjustedSideCodes should have size (2)
  }

  test("should not break opposite directions") {
    val linkId = generateRandomLinkId()
    val rLink = roadLink(linkId, Seq(Point(0.0, 0.0), Point(323.203, 0.0)))
    val speedLimit = Seq(
      SpeedLimit(1271877,linkId,SideCode.apply(3),TrafficDirection.apply(1),Option(SpeedLimitValue(60)),Seq(),0,199.502,None, None, Option("dr1_conversion"),Option(parse("28.10.2014 15:11")),0,Option(parse("28.10.2014 15:11")), linkSource = NormalLinkInterface),
      SpeedLimit(2102779,linkId,SideCode.apply(2),TrafficDirection.apply(1),Option(SpeedLimitValue(60)),Seq(),0,323.203,None,None,Option("split_speedlimit_1266969"),Option(parse("02.07.2015 12:12")),0,Option(parse("02.07.2015 12:12")), linkSource = NormalLinkInterface),
      SpeedLimit(1988786,linkId,SideCode.apply(3),TrafficDirection.apply(1),Option(SpeedLimitValue(80)),Seq(),199.502,323.203,None,None,Option("split_speedlimit_1228070"),Option(parse("02.07.2015 11:44")),0,Option(parse("02.07.2015 11:44")), linkSource = NormalLinkInterface)
    )

    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(Seq(rLink), speedLimit.groupBy(_.linkId))
    changeSet.droppedAssetIds should have size (0)
  }

  test("should not combine entries that disagree") {
    val linkId = generateRandomLinkId()
    val rLink = roadLink(linkId, Seq(Point(0.0, 0.0), Point(66.463, 0.0)))
    val geom1 = GeometryUtils.truncateGeometry3D(rLink.geometry, 42.545,66.463)
    val geom2 = GeometryUtils.truncateGeometry3D(rLink.geometry, 0, 42.545)
    val speedLimit = Seq(
      SpeedLimit(1183653,linkId,SideCode.apply(2),TrafficDirection.apply(1),Option(SpeedLimitValue(100)),geom1,42.545,66.463,None,None,Option("dr1_conversion"),Option(parse("28.10.2014 14:56")),0,Option(parse("28.10.2014 14:56")), linkSource = NormalLinkInterface),
      SpeedLimit(1204429,linkId,SideCode.apply(3),TrafficDirection.apply(1),Option(SpeedLimitValue(80)),geom1,42.545,66.463,None,None,Option("dr1_conversion"),Option(parse("28.10.2014 14:59")),0,Option(parse("28.10.2014 14:59")), linkSource = NormalLinkInterface),
      SpeedLimit(1232110,linkId,SideCode.apply(3),TrafficDirection.apply(1),Option(SpeedLimitValue(100)),geom2,0,42.545,None,None,Option("dr1_conversion"),Option(parse("28.10.2014 15:02")),0,Option(parse("28.10.2014 15:02")), linkSource = NormalLinkInterface),
      SpeedLimit(1868563,linkId,SideCode.apply(2),TrafficDirection.apply(1),Option(SpeedLimitValue(80)),geom2,0,42.545,Option("vvh_generated"),Option(parse("14.6.2016 16:10")),Option("split_speedlimit_1183653"),Option(parse("2.7.2015 10:58")),0,Option(parse("2.7.2015 10:58")), linkSource = NormalLinkInterface)
    )

    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(Seq(rLink), speedLimit.groupBy(_.linkId))
    changeSet.droppedAssetIds should have size (0)
    changeSet.adjustedSideCodes should have size (0)
    changeSet.adjustedMValues should have size (0)
  }


  test("should not combine different values on opposite directions") {
    val rLink = roadLink(linkId1, Seq(Point(0.0, 0.0), Point(50.0, 0.0)))
    val edit1 = Option(DateTime.now().minusDays(7))
    val edit2 = Option(DateTime.now().minusDays(6))
    val speedLimit = Seq(
      SpeedLimit(
        1, linkId1, SideCode.BothDirections, TrafficDirection.BothDirections, Some(SpeedLimitValue(60)),
        Seq(Point(0.0, 0.0), Point(30.0, 0.0)), 0.0, 30.0, None, None, Some("blue bd"), edit1, 0, None, linkSource = NormalLinkInterface),
      SpeedLimit(
        0, linkId1, SideCode.TowardsDigitizing, TrafficDirection.BothDirections, Some(SpeedLimitValue(50)),
        Seq(Point(30.00, 0.0), Point(50.0, 0.0)), 30.0, 50.0, None, None, Some("red td"), edit2, 0, None, linkSource = NormalLinkInterface),
      SpeedLimit(
        0, linkId1, SideCode.AgainstDigitizing, TrafficDirection.BothDirections, Some(SpeedLimitValue(60)),
        Seq(Point(30.0, 0.0), Point(50.0, 0.0)), 30.0, 50.0, None, None, Some("blue ad"), edit2, 0, None, linkSource = NormalLinkInterface)
    )

    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(Seq(rLink), Map(linkId1 -> speedLimit))

    filledTopology.length should be (3)
    val oldLink1 = filledTopology.find(_.value.contains(SpeedLimitValue(50))).get
    oldLink1.startMeasure should be (30.0)
    oldLink1.endMeasure should be (50.00)
    val oldLink2 = filledTopology.find(_.createdBy.contains("blue ad")).get
    oldLink2.startMeasure should be (30.0)
    oldLink2.endMeasure should be (50.00)
    oldLink2.value.get should be (SpeedLimitValue(60))
  }

  case class ChangeInfo(oldId: Option[String],
                        newId: Option[String],
                        mmlId: Long,
                        changeType: Int,
                        oldStartMeasure: Option[Double],
                        oldEndMeasure: Option[Double],
                        newStartMeasure: Option[Double],
                        newEndMeasure: Option[Double],
                        timeStamp: Option[Long])
}
