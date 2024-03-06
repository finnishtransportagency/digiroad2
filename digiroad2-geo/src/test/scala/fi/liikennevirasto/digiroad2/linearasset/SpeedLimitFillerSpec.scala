package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset.SideCode.BothDirections
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller._
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import org.joda.time.DateTime
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


  val initChangeSet = LinearAssetFiller.emptyChangeSet


  private def generateRandomLinkId(): String = s"${UUID.randomUUID()}:${Random.nextInt(100)}"

  val linkId1: String = generateRandomLinkId()
  val linkId2: String = generateRandomLinkId()
  val linkId3: String = generateRandomLinkId()

  private def oneWayRoadLink(linkId: String, geometry: Seq[Point], trafficDirection: TrafficDirection) = {
    roadLink(linkId, geometry).copy(trafficDirection = trafficDirection)
  }

  test("drop speedlimit segments less than 2 meters") {
    val roadLink = RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
      TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    val speedLimit1 = PieceWiseLinearAsset(1, linkId1, SideCode.BothDirections, Some(SpeedLimitValue(80)), Seq(Point(0.0, 0.0), Point(1.9, 0.0)),
      false, 0.0, 1.9, Set(Point(0.0, 0.0), Point(1.9, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimit2 = PieceWiseLinearAsset(2, linkId1, SideCode.BothDirections, Some(SpeedLimitValue(80)), Seq(Point(1.0, 0.0), Point(3.0, 0.0)),
      false, 2.0, 4.0, Set(Point(1.0, 0.0), Point(3.0, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val assets = Seq(speedLimit1, speedLimit2)

    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(Seq(roadLink).map(SpeedLimitFiller.toRoadLinkForFillTopology), Map(linkId1 -> assets), SpeedLimitAsset.typeId)
    filledTopology.filter(_.id != 0) should have size 1
    filledTopology.map(_.id) should not contain (1)
    changeSet.expiredAssetIds should have size 1
    changeSet.expiredAssetIds.head should be(1)
  }

  test("Don't drop speedlimit segments less than 2 meters on a road link with length less that 2 meters") {
    val roadLink = RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(1.9, 0.0)), 1.9, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
      TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    val speedLimit = PieceWiseLinearAsset(1, linkId1, SideCode.BothDirections, Some(SpeedLimitValue(80)), Seq(Point(0.0, 0.0), Point(1.9, 0.0)),
      false, 0.0, 1.9, Set(Point(0.0, 0.0), Point(1.9, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val assets = Seq(speedLimit)

    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(Seq(roadLink).map(SpeedLimitFiller.toRoadLinkForFillTopology), Map(linkId1 -> assets), SpeedLimitAsset.typeId)
    filledTopology should have size 1
    filledTopology.map(_.id) should be(Seq(1))
    changeSet.droppedAssetIds should have size 0
  }

  test("drop segment outside of link geometry") {
    val topology = Seq(
      roadLink(linkId2, Seq(Point(1.0, 0.0), Point(2.0, 0.0))))
    val speedLimit = PieceWiseLinearAsset(1, linkId2, SideCode.BothDirections, Some(SpeedLimitValue(80)), Seq(Point(1.0, 0.0), Point(2.0, 0.0)),
      false, 2.15, 2.35, Set(Point(1.0, 0.0), Point(2.0, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimits = Map(linkId2 -> Seq(speedLimit))
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology.map(SpeedLimitFiller.toRoadLinkForFillTopology), speedLimits, SpeedLimitAsset.typeId)
    changeSet.expiredAssetIds should be(Set(1))
  }

  test("adjust speed limit to cover whole link when its the only speed limit to refer to the link") {
    val topology = Seq(
      roadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0))))
    val speedLimit = PieceWiseLinearAsset(1, linkId1, SideCode.BothDirections, Some(SpeedLimitValue(80)), Seq(Point(0.0, 0.0), Point(1.9, 0.0)),
      false, 0.0, 1.9, Set(Point(0.0, 0.0), Point(1.9, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimits = Map(linkId1 -> Seq(speedLimit))
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology.map(SpeedLimitFiller.toRoadLinkForFillTopology), speedLimits, SpeedLimitAsset.typeId)
    filledTopology.length should be(1)
    filledTopology.head.geometry should be(Seq(Point(0.0, 0.0), Point(10.0, 0.0)))
    filledTopology.head.startMeasure should be(0.0)
    filledTopology.head.endMeasure should be(10.0)
    changeSet.droppedAssetIds should be(Set.empty)
    changeSet.adjustedSideCodes should be(Nil)
    changeSet.expiredAssetIds should be(Set.empty)
    changeSet.valueAdjustments should be(Nil)
    val adjustedMValues = changeSet.adjustedMValues
    adjustedMValues.sortBy(_.assetId) should be(Seq(MValueAdjustment(1, linkId1, 0, 10.0)))
  }

  test("adjust two-sided speed limits from both ends leaving the middle limit intact") {
    val topology = Seq(
      roadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0))))
    val speedLimit1 = PieceWiseLinearAsset(1, linkId1, SideCode.BothDirections, Some(SpeedLimitValue(80)), Seq(Point(0.2, 0.0), Point(2.0, 0.0)),
      false, 0.2, 2.0, Set(Point(0.2, 0.0), Point(2.0, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimit2 = PieceWiseLinearAsset(2, linkId1, SideCode.BothDirections, Some(SpeedLimitValue(60)), Seq(Point(2.0, 0.0), Point(4.9, 0.0)),
      false, 2.0, 4.9, Set(Point(2.0, 0.0), Point(4.9, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimit3 = PieceWiseLinearAsset(3, linkId1, SideCode.BothDirections, Some(SpeedLimitValue(100)), Seq(Point(4.9, 0.0), Point(9.8, 0.0)),
      false, 4.9, 9.8, Set(Point(4.9, 0.0), Point(9.8, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimits = Map(linkId1 -> Seq(speedLimit1, speedLimit2, speedLimit3))
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology.map(SpeedLimitFiller.toRoadLinkForFillTopology), speedLimits, SpeedLimitAsset.typeId)
    val sortedFilledTopology = filledTopology.sortBy(_.startMeasure)
    sortedFilledTopology.length should be(3)
    sortedFilledTopology.head.geometry should be(Seq(Point(0.0, 0.0), Point(2.0, 0.0)))
    sortedFilledTopology.head.startMeasure should be(0.0)
    sortedFilledTopology.head.endMeasure should be(2.0)
    sortedFilledTopology(1).geometry should be(Seq(Point(2.0, 0.0), Point(4.9, 0.0)))
    sortedFilledTopology(1).startMeasure should be(2.0)
    sortedFilledTopology(1).endMeasure should be(4.9)
    sortedFilledTopology.last.geometry should be(Seq(Point(4.9, 0.0), Point(10.0, 0.0)))
    sortedFilledTopology.last.startMeasure should be(4.9)
    sortedFilledTopology.last.endMeasure should be(10.0)
    changeSet.droppedAssetIds should be(Set.empty)
    changeSet.adjustedSideCodes should be(Nil)
    changeSet.expiredAssetIds should be(Set.empty)
    changeSet.valueAdjustments should be(Nil)
    val adjustedMValues = changeSet.adjustedMValues
    adjustedMValues.sortBy(_.assetId) should be(Seq(MValueAdjustment(1, linkId1, 0, 2.0), MValueAdjustment(3, linkId1, 4.9, 10.0)))
  }

  test("adjust two-sided speed limits from both ends leaving the middle with two one-sided limits intact") {
    val topology = Seq(
      roadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0))))
    val speedLimit1 = PieceWiseLinearAsset(1, linkId1, SideCode.BothDirections, Some(SpeedLimitValue(80)), Seq(Point(0.2, 0.0), Point(2.0, 0.0)),
      false, 0.2, 2.0, Set(Point(0.2, 0.0), Point(2.0, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimit2 = PieceWiseLinearAsset(2, linkId1, SideCode.TowardsDigitizing, Some(SpeedLimitValue(60)), Seq(Point(2.0, 0.0), Point(4.9, 0.0)),
      false, 2.0, 4.9, Set(Point(2.0, 0.0), Point(4.9, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimit3 = PieceWiseLinearAsset(3, linkId1, SideCode.AgainstDigitizing, Some(SpeedLimitValue(30)), Seq(Point(2.0, 0.0), Point(4.9, 0.0)),
      false, 2.0, 4.9, Set(Point(2.0, 0.0), Point(4.9, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimit4 = PieceWiseLinearAsset(4, linkId1, SideCode.BothDirections, Some(SpeedLimitValue(100)), Seq(Point(4.9, 0.0), Point(9.8, 0.0)),
      false, 4.9, 9.8, Set(Point(4.9, 0.0), Point(9.8, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimits = Map(linkId1 -> Seq(speedLimit1, speedLimit2, speedLimit3, speedLimit4))
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology.map(SpeedLimitFiller.toRoadLinkForFillTopology), speedLimits, SpeedLimitAsset.typeId)
    val sortedFilledTopology = filledTopology.sortBy(_.startMeasure)
    sortedFilledTopology.length should be(4)
    sortedFilledTopology.head.geometry should be(Seq(Point(0.0, 0.0), Point(2.0, 0.0)))
    sortedFilledTopology.head.startMeasure should be(0.0)
    sortedFilledTopology.head.endMeasure should be(2.0)
    sortedFilledTopology(1).geometry should be(Seq(Point(2.0, 0.0), Point(4.9, 0.0)))
    sortedFilledTopology(1).startMeasure should be(2.0)
    sortedFilledTopology(1).endMeasure should be(4.9)
    sortedFilledTopology(2).geometry should be(Seq(Point(2.0, 0.0), Point(4.9, 0.0)))
    sortedFilledTopology(2).startMeasure should be(2.0)
    sortedFilledTopology(2).endMeasure should be(4.9)
    sortedFilledTopology.last.geometry should be(Seq(Point(4.9, 0.0), Point(10.0, 0.0)))
    sortedFilledTopology.last.startMeasure should be(4.9)
    sortedFilledTopology.last.endMeasure should be(10.0)
    changeSet.droppedAssetIds should be(Set.empty)
    changeSet.adjustedSideCodes should be(Nil)
    changeSet.expiredAssetIds should be(Set.empty)
    changeSet.valueAdjustments should be(Nil)
    val adjustedMValues = changeSet.adjustedMValues
    adjustedMValues.sortBy(_.assetId) should be(Seq(MValueAdjustment(1, linkId1, 0, 2.0), MValueAdjustment(4, linkId1, 4.9, 10.0)))
  }

  test("adjust two-sided speed limits from both ends leaving the middle with one one-sided limit intact") {
    val topology = Seq(
      roadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0))))
    val speedLimit1 = PieceWiseLinearAsset(1, linkId1, SideCode.BothDirections, Some(SpeedLimitValue(80)), Seq(Point(0.2, 0.0), Point(2.0, 0.0)),
      false, 0.2, 2.0, Set(Point(0.2, 0.0), Point(2.0, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimit2 = PieceWiseLinearAsset(2, linkId1, SideCode.TowardsDigitizing, Some(SpeedLimitValue(60)), Seq(Point(2.0, 0.0), Point(4.9, 0.0)),
      false, 2.0, 4.9, Set(Point(2.0, 0.0), Point(4.9, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimit3 = PieceWiseLinearAsset(3, linkId1, SideCode.BothDirections, Some(SpeedLimitValue(100)), Seq(Point(4.9, 0.0), Point(9.8, 0.0)),
      false, 4.9, 9.8, Set(Point(4.9, 0.0), Point(9.8, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimits = Map(linkId1 -> Seq(speedLimit1, speedLimit2, speedLimit3))
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology.map(SpeedLimitFiller.toRoadLinkForFillTopology), speedLimits, SpeedLimitAsset.typeId)
    val sortedFilledTopology = filledTopology.sortBy(_.startMeasure)
    sortedFilledTopology.length should be(3)
    sortedFilledTopology.head.geometry should be(Seq(Point(0.0, 0.0), Point(2.0, 0.0)))
    sortedFilledTopology.head.startMeasure should be(0.0)
    sortedFilledTopology.head.endMeasure should be(2.0)
    sortedFilledTopology.head.sideCode should be(BothDirections)
    sortedFilledTopology(1).geometry should be(Seq(Point(2.0, 0.0), Point(4.9, 0.0)))
    sortedFilledTopology(1).startMeasure should be(2.0)
    sortedFilledTopology(1).endMeasure should be(4.9)
    sortedFilledTopology(1).sideCode should be(BothDirections)
    sortedFilledTopology.last.geometry should be(Seq(Point(4.9, 0.0), Point(10.0, 0.0)))
    sortedFilledTopology.last.startMeasure should be(4.9)
    sortedFilledTopology.last.endMeasure should be(10.0)
    sortedFilledTopology.last.sideCode should be(BothDirections)
    changeSet.droppedAssetIds should be(Set.empty)
    changeSet.adjustedSideCodes should be(Seq(SideCodeAdjustment(2,linkId1,BothDirections,20)))
    changeSet.expiredAssetIds should be(Set.empty)
    changeSet.valueAdjustments should be(Nil)
    val adjustedMValues = changeSet.adjustedMValues
    adjustedMValues.sortBy(_.assetId) should be(Seq(MValueAdjustment(1, linkId1, 0, 2.0), MValueAdjustment(3, linkId1, 4.9, 10.0)))
  }

  test("adjust one-sided limits when there is one on one side and two on the other") {
    val topology = Seq(
      roadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0))))
    val speedLimit1 = PieceWiseLinearAsset(1, linkId1, SideCode.TowardsDigitizing, Some(SpeedLimitValue(80)), Seq(Point(0.2, 0.0), Point(2.9, 0.0)),
      false, 0.2, 2.9, Set(Point(0.2, 0.0), Point(2.9, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimit2 = PieceWiseLinearAsset(2, linkId1, SideCode.TowardsDigitizing, Some(SpeedLimitValue(60)), Seq(Point(2.9, 0.0), Point(8.9, 0.0)),
      false, 2.9, 8.9, Set(Point(2.9, 0.0), Point(8.9, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimit3 = PieceWiseLinearAsset(3, linkId1, SideCode.AgainstDigitizing, Some(SpeedLimitValue(100)), Seq(Point(0.9, 0.0), Point(9.8, 0.0)),
      false, 0.9, 9.8, Set(Point(0.9, 0.0), Point(9.8, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimits = Map(linkId1 -> Seq(speedLimit1, speedLimit2, speedLimit3))
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology.map(SpeedLimitFiller.toRoadLinkForFillTopology), speedLimits, SpeedLimitAsset.typeId)
    val sortedFilledTopology = filledTopology.sortBy(_.id)
    sortedFilledTopology.length should be(3)
    sortedFilledTopology.head.geometry should be(Seq(Point(0.0, 0.0), Point(2.9, 0.0)))
    sortedFilledTopology.head.startMeasure should be(0.0)
    sortedFilledTopology.head.endMeasure should be(2.9)
    sortedFilledTopology(1).geometry should be(Seq(Point(2.9, 0.0), Point(10.0, 0.0)))
    sortedFilledTopology(1).startMeasure should be(2.9)
    sortedFilledTopology(1).endMeasure should be(10.0)
    sortedFilledTopology.last.geometry should be(Seq(Point(0.0, 0.0), Point(10.0, 0.0)))
    sortedFilledTopology.last.startMeasure should be(0.0)
    sortedFilledTopology.last.endMeasure should be(10.0)
    changeSet.droppedAssetIds should be(Set.empty)
    changeSet.adjustedSideCodes should be(Nil)
    changeSet.expiredAssetIds should be(Set.empty)
    changeSet.valueAdjustments should be(Nil)
    val adjustedMValues = changeSet.adjustedMValues
    adjustedMValues.sortBy(_.assetId) should be(Seq(MValueAdjustment(1, linkId1, 0, 2.9), MValueAdjustment(2, linkId1, 2.9, 10.0),
      MValueAdjustment(3, linkId1, 0.0, 10.0)))
  }

  test("two opposite side directions with smallest start measure are adjusted") {
    val topology = Seq(
      roadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0))))
    val speedLimit1 = PieceWiseLinearAsset(1, linkId1, SideCode.TowardsDigitizing, Some(SpeedLimitValue(80)), Seq(Point(0.2, 0.0), Point(2.9, 0.0)),
      false, 0.2, 2.9, Set(Point(0.2, 0.0), Point(2.9, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimit2 = PieceWiseLinearAsset(2, linkId1, SideCode.AgainstDigitizing, Some(SpeedLimitValue(60)), Seq(Point(0.7, 0.0), Point(2.9, 0.0)),
      false, 0.7, 2.9, Set(Point(0.7, 0.0), Point(2.9, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimit3 = PieceWiseLinearAsset(3, linkId1, SideCode.BothDirections, Some(SpeedLimitValue(100)), Seq(Point(2.9, 0.0), Point(10.0, 0.0)),
      false, 2.9, 10.0, Set(Point(2.9, 0.0), Point(10.0, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimits = Map(linkId1 -> Seq(speedLimit1, speedLimit2, speedLimit3))
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology.map(SpeedLimitFiller.toRoadLinkForFillTopology), speedLimits, SpeedLimitAsset.typeId)
    val sortedFilledTopology = filledTopology.sortBy(_.id)
    sortedFilledTopology.length should be(3)
    sortedFilledTopology.head.geometry should be(Seq(Point(0.0, 0.0), Point(2.9, 0.0)))
    sortedFilledTopology.head.startMeasure should be(0.0)
    sortedFilledTopology.head.endMeasure should be(2.9)
    sortedFilledTopology(1).geometry should be(Seq(Point(0.0, 0.0), Point(2.9, 0.0)))
    sortedFilledTopology(1).startMeasure should be(0.0)
    sortedFilledTopology(1).endMeasure should be(2.9)
    sortedFilledTopology.last.geometry should be(Seq(Point(2.9, 0.0), Point(10.0, 0.0)))
    sortedFilledTopology.last.startMeasure should be(2.9)
    sortedFilledTopology.last.endMeasure should be(10.0)
    changeSet.droppedAssetIds should be(Set.empty)
    changeSet.adjustedSideCodes should be(Nil)
    changeSet.expiredAssetIds should be(Set.empty)
    changeSet.valueAdjustments should be(Nil)
    val adjustedMValues = changeSet.adjustedMValues
    adjustedMValues.sortBy(_.assetId) should be(Seq(MValueAdjustment(1, linkId1, 0.0, 2.9), MValueAdjustment(2, linkId1, 0.0, 2.9)))
  }

  test("only the one-sided limit with the smallest start measure is adjusted when the two smallest are on the same side") {
    val topology = Seq(
      roadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0))))
    val speedLimit1 = PieceWiseLinearAsset(1, linkId1, SideCode.TowardsDigitizing, Some(SpeedLimitValue(80)), Seq(Point(0.2, 0.0), Point(2.9, 0.0)),
      false, 0.2, 2.9, Set(Point(0.2, 0.0), Point(2.9, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimit2 = PieceWiseLinearAsset(2, linkId1, SideCode.TowardsDigitizing, Some(SpeedLimitValue(60)), Seq(Point(2.9, 0.0), Point(5.9, 0.0)),
      false, 2.9, 5.9, Set(Point(2.9, 0.0), Point(5.9, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimit3 = PieceWiseLinearAsset(3, linkId1, SideCode.BothDirections, Some(SpeedLimitValue(100)), Seq(Point(5.9, 0.0), Point(10.0, 0.0)),
      false, 5.9, 10.0, Set(Point(5.9, 0.0), Point(10.0, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimits = Map(linkId1 -> Seq(speedLimit1, speedLimit2, speedLimit3))
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology.map(SpeedLimitFiller.toRoadLinkForFillTopology), speedLimits, SpeedLimitAsset.typeId)
    val (generated, adjusted) = filledTopology.partition(_.id == 0)
    val sortedFilledTopology = adjusted.sortBy(_.id)
    sortedFilledTopology.length should be(3)
    sortedFilledTopology.head.geometry should be(Seq(Point(0.0, 0.0), Point(2.9, 0.0)))
    sortedFilledTopology.head.startMeasure should be(0.0)
    sortedFilledTopology.head.endMeasure should be(2.9)
    sortedFilledTopology.head.sideCode should be(BothDirections)
    sortedFilledTopology(1).geometry should be(Seq(Point(2.9, 0.0), Point(5.9, 0.0)))
    sortedFilledTopology(1).startMeasure should be(2.9)
    sortedFilledTopology(1).endMeasure should be(5.9)
    sortedFilledTopology(1).sideCode should be(BothDirections)
    sortedFilledTopology.last.geometry should be(Seq(Point(5.9, 0.0), Point(10.0, 0.0)))
    sortedFilledTopology.last.startMeasure should be(5.9)
    sortedFilledTopology.last.endMeasure should be(10.0)
    sortedFilledTopology.last.sideCode should be(BothDirections)
    changeSet.droppedAssetIds should be(Set.empty)
    changeSet.adjustedSideCodes.sortBy(_.assetId) should be(Seq(SideCodeAdjustment(1,linkId1,BothDirections,20), SideCodeAdjustment(2,linkId1,BothDirections,20)))
    changeSet.expiredAssetIds should be(Set.empty)
    changeSet.valueAdjustments should be(Nil)
    val adjustedMValues = changeSet.adjustedMValues
    adjustedMValues.sortBy(_.assetId) should be(Seq(MValueAdjustment(1, linkId1, 0.0, 2.9)))
  }

  test("two opposite side directions with largest end measure and the two-sided limit in the beginning are adjusted") {
    val topology = Seq(
      roadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0))))
    val speedLimit1 = PieceWiseLinearAsset(1, linkId1, SideCode.BothDirections, Some(SpeedLimitValue(80)), Seq(Point(0.2, 0.0), Point(2.9, 0.0)),
      false, 0.2, 2.9, Set(Point(0.2, 0.0), Point(2.9, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimit2 = PieceWiseLinearAsset(2, linkId1, SideCode.AgainstDigitizing, Some(SpeedLimitValue(60)), Seq(Point(2.9, 0.0), Point(9.9, 0.0)),
      false, 2.9, 9.9, Set(Point(2.9, 0.0), Point(9.9, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimit3 = PieceWiseLinearAsset(3, linkId1, SideCode.TowardsDigitizing, Some(SpeedLimitValue(100)), Seq(Point(2.9, 0.0), Point(8.0, 0.0)),
      false, 2.9, 8.0, Set(Point(2.9, 0.0), Point(8.0, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimits = Map(linkId1 -> Seq(speedLimit1, speedLimit2, speedLimit3))
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology.map(SpeedLimitFiller.toRoadLinkForFillTopology), speedLimits, SpeedLimitAsset.typeId)
    val sortedFilledTopology = filledTopology.sortBy(_.id)
    sortedFilledTopology.length should be(3)
    sortedFilledTopology.head.geometry should be(Seq(Point(0.0, 0.0), Point(2.9, 0.0)))
    sortedFilledTopology.head.startMeasure should be(0.0)
    sortedFilledTopology.head.endMeasure should be(2.9)
    sortedFilledTopology(1).geometry should be(Seq(Point(2.9, 0.0), Point(10.0, 0.0)))
    sortedFilledTopology(1).startMeasure should be(2.9)
    sortedFilledTopology(1).endMeasure should be(10.0)
    sortedFilledTopology.last.geometry should be(Seq(Point(2.9, 0.0), Point(10.0, 0.0)))
    sortedFilledTopology.last.startMeasure should be(2.9)
    sortedFilledTopology.last.endMeasure should be(10.0)
    changeSet.droppedAssetIds should be(Set.empty)
    changeSet.adjustedSideCodes should be(Nil)
    changeSet.expiredAssetIds should be(Set.empty)
    changeSet.valueAdjustments should be(Nil)
    val adjustedMValues = changeSet.adjustedMValues
    adjustedMValues.sortBy(_.assetId) should be(Seq(MValueAdjustment(1, linkId1, 0, 2.9), MValueAdjustment(2, linkId1, 2.9, 10.0),
      MValueAdjustment(3, linkId1, 2.9, 10.0)))
  }

  test("only the one-sided limit with the largest end measure is adjusted when the two largest are on the same side") {
    val topology = Seq(
      roadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0))))
    val speedLimit1 = PieceWiseLinearAsset(1, linkId1, SideCode.BothDirections, Some(SpeedLimitValue(80)), Seq(Point(0.2, 0.0), Point(2.9, 0.0)),
      false, 0.2, 2.9, Set(Point(0.2, 0.0), Point(2.9, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimit2 = PieceWiseLinearAsset(2, linkId1, SideCode.TowardsDigitizing, Some(SpeedLimitValue(60)), Seq(Point(2.9, 0.0), Point(5.9, 0.0)),
      false, 2.9, 5.9, Set(Point(2.9, 0.0), Point(5.9, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimit3 = PieceWiseLinearAsset(3, linkId1, SideCode.TowardsDigitizing, Some(SpeedLimitValue(100)), Seq(Point(5.9, 0.0), Point(9.5, 0.0)),
      false, 5.9, 9.5, Set(Point(5.9, 0.0), Point(9.5, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimits = Map(linkId1 -> Seq(speedLimit1, speedLimit2, speedLimit3))
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology.map(SpeedLimitFiller.toRoadLinkForFillTopology), speedLimits, SpeedLimitAsset.typeId)
    val (generated, adjusted) = filledTopology.partition(_.id == 0)
    val sortedFilledTopology = adjusted.sortBy(_.id)
    sortedFilledTopology.length should be(3)
    sortedFilledTopology.head.geometry should be(Seq(Point(0.0, 0.0), Point(2.9, 0.0)))
    sortedFilledTopology.head.startMeasure should be(0.0)
    sortedFilledTopology.head.endMeasure should be(2.9)
    sortedFilledTopology.head.sideCode should be(BothDirections)
    sortedFilledTopology(1).geometry should be(Seq(Point(2.9, 0.0), Point(5.9, 0.0)))
    sortedFilledTopology(1).startMeasure should be(2.9)
    sortedFilledTopology(1).endMeasure should be(5.9)
    sortedFilledTopology(1).sideCode should be(BothDirections)
    sortedFilledTopology.last.geometry should be(Seq(Point(5.9, 0.0), Point(10.0, 0.0)))
    sortedFilledTopology.last.startMeasure should be(5.9)
    sortedFilledTopology.last.endMeasure should be(10.0)
    sortedFilledTopology.last.sideCode should be(BothDirections)
    changeSet.droppedAssetIds should be(Set.empty)
    changeSet.adjustedSideCodes.sortBy(_.assetId) should be(Seq(SideCodeAdjustment(2,linkId1,BothDirections,20,0), SideCodeAdjustment(3,linkId1,BothDirections,20,0)))
    changeSet.expiredAssetIds should be(Set.empty)
    changeSet.valueAdjustments should be(Nil)
    val adjustedMValues = changeSet.adjustedMValues
    adjustedMValues.sortBy(_.assetId) should be(Seq(MValueAdjustment(1, linkId1, 0, 2.9), MValueAdjustment(3, linkId1, 5.9, 10.0)))
  }

  test("adjust one way speed limits to cover whole link when there are no multiple speed limits on one side of the link") {
    val topology = Seq(
      roadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0))))
    val speedLimit1 = PieceWiseLinearAsset(1, linkId1, SideCode.TowardsDigitizing, Some(SpeedLimitValue(40)), Seq(Point(0.0, 0.0), Point(1.9, 0.0)),
      false, 0.0, 1.9, Set(Point(0.0, 0.0), Point(1.9, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimit2 = PieceWiseLinearAsset(2, linkId1, SideCode.AgainstDigitizing, Some(SpeedLimitValue(50)), Seq(Point(0.0, 0.0), Point(1.9, 0.0)),
      false, 0.0, 1.9, Set(Point(0.0, 0.0), Point(1.9, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimits = Map(
      linkId1 -> Seq(speedLimit1, speedLimit2))
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology.map(SpeedLimitFiller.toRoadLinkForFillTopology), speedLimits, SpeedLimitAsset.typeId)
    filledTopology should have size 2
    filledTopology.map(_.geometry) should be(Seq(
      Seq(Point(0.0, 0.0), Point(10.0, 0.0)),
      Seq(Point(0.0, 0.0), Point(10.0, 0.0))))
    filledTopology.map(_.startMeasure) should be(Seq(0.0, 0.0))
    filledTopology.map(_.endMeasure) should be(Seq(10.0, 10.0))
    changeSet.adjustedMValues should have size 2
    changeSet.adjustedMValues.sortBy(_.assetId) should be(Seq(MValueAdjustment(1, linkId1, 0, 10.0), MValueAdjustment(2, linkId1, 0, 10.0)))
  }

  case class Measure(startMeasure: Double, endMeasure: Double)

  def createAsset(id: Long, linkId1: String, measure: Measure, sideCode: SideCode, value: Option[Value], trafficDirection: TrafficDirection = TrafficDirection.BothDirections) = {
    PieceWiseLinearAsset(id = id, linkId = linkId1, sideCode = sideCode, value = value, geometry = Nil, expired = false, startMeasure = measure.startMeasure, endMeasure = measure.endMeasure,
      endpoints = Set(Point(measure.endMeasure, 0.0)), modifiedBy = None, modifiedDateTime = None, createdBy = Some("guy"),
      createdDateTime = Some(DateTime.now()), typeId = 140, trafficDirection = trafficDirection, timeStamp = 0L,
      geomModifiedDate = None, linkSource = NormalLinkInterface, administrativeClass = State, attributes = Map(), verifiedBy = None, verifiedDate = None, informationSource = None)
  }
  test("Adjust start and end m-value when difference is 0.001") {
    val roadLinks = Seq(
      RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(36.783, 0.0)), 36.783, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
        TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    )

    val assets = Seq(
      createAsset(1, linkId1, Measure(0.001, 36.782), SideCode.BothDirections, None, TrafficDirection.BothDirections)
    )

    val (methodTest, combineTestChangeSet) = SpeedLimitFiller.adjustAssets(roadLinks.map(SpeedLimitFiller.toRoadLinkForFillTopology).head, assets, initChangeSet)

    val sorted = methodTest.sortBy(_.endMeasure)

    sorted.size should be(1)
    //107.093
    sorted(0).startMeasure should be(0)
    sorted(0).endMeasure should be(36.783)

  }

  test("cap speed limit to road link geometry") {
    val topology = Seq(
      roadLink(linkId1, Seq(Point(0.0, 0.0), Point(100.0, 0.0))))
    val speedLimit1 = PieceWiseLinearAsset(1, linkId1, SideCode.BothDirections, Some(SpeedLimitValue(40)), Seq(Point(0.0, 0.0), Point(90.0, 0.0)),
      false, 0.0, 90.0, Set(Point(0.0, 0.0), Point(90.0, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimit2 = PieceWiseLinearAsset(2, linkId1, SideCode.BothDirections, Some(SpeedLimitValue(50)), Seq(Point(90.0, 0.0), Point(110.0, 0.0)),
      false, 90.0, 110.0, Set(Point(90.0, 0.0), Point(110.0, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimits = Map(linkId1 -> Seq(speedLimit1, speedLimit2))
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology.map(SpeedLimitFiller.toRoadLinkForFillTopology), speedLimits, SpeedLimitAsset.typeId)
    filledTopology.length should be(2)

    filledTopology.find(_.id == 2).map(_.geometry) should be(Some(Seq(Point(90.0, 0.0), Point(100.0, 0.0))))
    filledTopology.find(_.id == 2).map(_.endMeasure) should be(Some(100.0))

    changeSet.adjustedMValues should be(Seq(MValueAdjustment(2, linkId1, 90.0, 100.0)))
  }

  test("drop short speed limit") {
    val topology = Seq(
      roadLink(linkId1, Seq(Point(0.0, 0.0), Point(100.00, 0.0))))
    val speedLimit1 = PieceWiseLinearAsset(1, linkId1, SideCode.BothDirections, Some(SpeedLimitValue(40)), Seq(Point(0.04, 0.0), Point(100.0, 0.0)),
      false, 0.04, 100.0, Set(Point(0.04, 0.0), Point(100.0, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimit2 = PieceWiseLinearAsset(2, linkId1, SideCode.BothDirections, Some(SpeedLimitValue(50)), Seq(Point(0.0, 0.0), Point(0.04, 0.0)),
      false, 0.0, 0.04, Set(Point(0.0, 0.0), Point(0.04, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimits = Map(
      linkId1 -> Seq(speedLimit1, speedLimit2))
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology.map(SpeedLimitFiller.toRoadLinkForFillTopology), speedLimits, SpeedLimitAsset.typeId)
    filledTopology should have size 1
    changeSet.expiredAssetIds should be(Set(speedLimit2.id))
  }

  test("do not drop short speed limit if it fills the road length") {
    val topology = Seq(
      roadLink(linkId1, Seq(Point(0.0, 0.0), Point(0.04, 0.0))))
    val speedLimit = PieceWiseLinearAsset(2, linkId1, SideCode.BothDirections, Some(SpeedLimitValue(50)), Seq(Point(0.0, 0.0), Point(0.04, 0.0)),
      false, 0.0, 0.04, Set(Point(0.0, 0.0), Point(0.04, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimits = Map(
      linkId1 -> Seq(speedLimit))
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology.map(SpeedLimitFiller.toRoadLinkForFillTopology), speedLimits, SpeedLimitAsset.typeId)
    filledTopology should have size 1
    changeSet.droppedAssetIds should be(Set())
  }

  test("should not drop adjusted short speed limit") {
    val topology = Seq(
      roadLink(linkId1, Seq(Point(0.0, 0.0), Point(1.0, 0.0))))
    val speedLimit = PieceWiseLinearAsset(2, linkId1, SideCode.BothDirections, Some(SpeedLimitValue(50)), Seq(Point(0.0, 0.0), Point(0.04, 0.0)),
      false, 0.0, 0.04, Set(Point(0.0, 0.0), Point(0.04, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimits = Map(
      linkId1 -> Seq(speedLimit))
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology.map(SpeedLimitFiller.toRoadLinkForFillTopology), speedLimits, SpeedLimitAsset.typeId)
    filledTopology should have size 1
    filledTopology.map(_.id) should be(Seq(speedLimit.id))
    changeSet.droppedAssetIds shouldBe empty
  }

  test("adjust side code of a speed limit") {
    val topology = Seq(
      roadLink(linkId1, Seq(Point(0.0, 0.0), Point(1.9, 0.0))))
    val speedLimit = PieceWiseLinearAsset(1, linkId1, SideCode.TowardsDigitizing, Some(SpeedLimitValue(40)), Seq(Point(0.0, 0.0), Point(1.9, 0.0)),
      false, 0.0, 1.9, Set(Point(0.0, 0.0), Point(1.9, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimits = Map(
      linkId1 -> Seq(speedLimit))
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology.map(SpeedLimitFiller.toRoadLinkForFillTopology), speedLimits, SpeedLimitAsset.typeId)
    filledTopology should have size 1
    filledTopology.map(_.sideCode) should be(Seq(SideCode.BothDirections))
    changeSet.adjustedSideCodes should have size 1
    changeSet.adjustedSideCodes.head should be(SideCodeAdjustment(1, linkId1, SideCode.BothDirections, SpeedLimitAsset.typeId))
  }

  test("adjust one-way speed limits on one-way road link to maintain one-way speed limits") {
    val topology = Seq(
      oneWayRoadLink(linkId1, Seq(Point(0.0, 0.0), Point(2.0, 0.0)), TrafficDirection.TowardsDigitizing))
    val speedLimit1 = PieceWiseLinearAsset(1, linkId1, SideCode.TowardsDigitizing, Some(SpeedLimitValue(40)), Seq(Point(0.0, 0.0), Point(1.0, 0.0)),
      false, 0.0, 1.0, Set(Point(0.0, 0.0), Point(1.0, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.TowardsDigitizing,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimit2 = PieceWiseLinearAsset(2, linkId1, SideCode.TowardsDigitizing, Some(SpeedLimitValue(50)), Seq(Point(1.0, 0.0), Point(2.0, 0.0)),
      false, 1.0, 2.0, Set(Point(1.0, 0.0), Point(2.0, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.TowardsDigitizing,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimits = Map(
      linkId1 -> Seq(speedLimit1, speedLimit2))
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology.map(SpeedLimitFiller.toRoadLinkForFillTopology), speedLimits, SpeedLimitAsset.typeId)
    filledTopology should have size 2
    filledTopology.map(_.sideCode) should be(Seq(SideCode.TowardsDigitizing, SideCode.TowardsDigitizing))
  }

  ignore("merge speed limits with same value on shared road link2") { // check janne are we going maintain this logic
    val topology = Seq(
      roadLink(linkId1, Seq(Point(0.0, 0.0), Point(9.0, 0.0))))
    
    
    val point1 =  Set(Point(0.0, 0.0), Point(3.0, 0.0))
    val point2 =  Set(Point(3.0, 0.0), Point(6.0, 0.0))
    val point3 =  Set(Point(6.0, 0.0), Point(9.0, 0.0))
    
    val speedLimit1 = PieceWiseLinearAsset(1, linkId1, SideCode.TowardsDigitizing, Some(SpeedLimitValue(40)), 
      geometry = Seq(Point(0.0, 0.0), Point(1.0, 0.0)),
      false, 
      startMeasure = 0.0,endMeasure =  3.0, endpoints = Set(Point(0.0, 0.0), Point(3.0, 0.0)), 
      Some("earlier modifier"), Some(DateTime.now().minus(1000)), None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimit2 = PieceWiseLinearAsset(2, linkId1, SideCode.AgainstDigitizing, Some(SpeedLimitValue(40)),
      geometry =  Seq(Point(3.0, 0.0), Point(6.0, 0.0)),
      false,
      startMeasure = 3.0,endMeasure =  6.0,endpoints =  Set(Point(3.0, 0.0), Point(6.0, 0.0)),
      Some("earlier modifier"), Some(DateTime.now().minus(1000)), None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimit3 = PieceWiseLinearAsset(3, linkId1, SideCode.BothDirections, Some(SpeedLimitValue(40)),  
      geometry = Seq(Point(6.0, 0.0), Point(9.0, 0.0)),
      false,
      startMeasure = 6.0,endMeasure =  9.0, endpoints =  Set(Point(6.0, 0.0), Point(9.0, 0.0)), 
      Some("latest modifier"), Some(DateTime.now().minus(100)), None, None, SpeedLimitAsset.typeId, TrafficDirection.TowardsDigitizing,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimits = Map(
      linkId1 -> Seq(
        speedLimit1, speedLimit2, speedLimit3))
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology.map(SpeedLimitFiller.toRoadLinkForFillTopology), speedLimits, SpeedLimitAsset.typeId)
    filledTopology should have size 1
    filledTopology.map(_.sideCode) should be(Seq(SideCode.BothDirections))
    filledTopology.map(_.value) should be(Seq(Some(SpeedLimitValue(40))))
    filledTopology.map(_.modifiedBy) should be(Seq(Some("latest modifier"))) // latest modification should show
    changeSet.adjustedMValues should be(Seq(MValueAdjustment(3, linkId1, 0.0, 3.0)))
    changeSet.adjustedSideCodes should be(List())
    changeSet.expiredAssetIds should be(Set(1, 2))
  }

  test("generate unknown and retain asset") {
    val topology = Seq(
      roadLink(linkId1, Seq(Point(0.0, 0.0), Point(9.0, 0.0))))

    val speedLimit1 = PieceWiseLinearAsset(1, linkId1, SideCode.TowardsDigitizing, Some(SpeedLimitValue(41)),
      geometry = Seq(Point(0.0, 0.0), Point(1.0, 0.0)),
      false,
      startMeasure = 0.0, endMeasure = 3.0, endpoints = Set(Point(0.0, 0.0), Point(3.0, 0.0)),
      Some("earlier modifier"), Some(DateTime.now().minus(1000)), None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimit2 = PieceWiseLinearAsset(2, linkId1, SideCode.AgainstDigitizing, Some(SpeedLimitValue(42)),
      geometry = Seq(Point(3.0, 0.0), Point(6.0, 0.0)),
      false,
      startMeasure = 3.0, endMeasure = 6.0, endpoints = Set(Point(3.0, 0.0), Point(6.0, 0.0)),
      Some("earlier modifier"), Some(DateTime.now().minus(1000)), None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimit3 = PieceWiseLinearAsset(3, linkId1, SideCode.BothDirections, Some(SpeedLimitValue(43)),
      geometry = Seq(Point(6.0, 0.0), Point(9.0, 0.0)),
      false,
      startMeasure = 6.0, endMeasure = 9.0, endpoints = Set(Point(6.0, 0.0), Point(9.0, 0.0)),
      Some("latest modifier"), Some(DateTime.now().minus(100)), None, None, SpeedLimitAsset.typeId, TrafficDirection.TowardsDigitizing,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimits = Map(
      linkId1 -> Seq(
        speedLimit1, speedLimit2, speedLimit3))
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology.map(SpeedLimitFiller.toRoadLinkForFillTopology), speedLimits, SpeedLimitAsset.typeId)
    filledTopology should have size 4
    val (generated, humanCreated) =filledTopology.partition(_.id == 0)

    val sortedGenerated = generated.sortBy(_.startMeasure)
    sortedGenerated.size should be(1)
    val sortedHumanCreated = humanCreated.sortBy(_.startMeasure)

    sortedHumanCreated.size should be(3)
    
    sortedHumanCreated(0).sideCode should be(SideCode.TowardsDigitizing)
    sortedHumanCreated(0).value should be(Some(SpeedLimitValue(41)))
    sortedHumanCreated(0).modifiedBy should be(Some("earlier modifier"))

    sortedHumanCreated(1).sideCode should be(SideCode.AgainstDigitizing)
    sortedHumanCreated(1).value should be(Some(SpeedLimitValue(42)))
    sortedHumanCreated(1).modifiedBy should be(Some("earlier modifier"))

    sortedHumanCreated(2).sideCode should be(SideCode.BothDirections)
    sortedHumanCreated(2).value should be(Some(SpeedLimitValue(43)))
    sortedHumanCreated(2).modifiedBy should be(Some("latest modifier"))

    changeSet.adjustedSideCodes.size should be (0)
    
  }
  
  test("merge speed limits with same value on shared road link, test only method") {
    val topology = Seq(
      roadLink(linkId1, Seq(Point(0.0, 0.0), Point(3.0, 0.0))))
    val speedLimit1 = PieceWiseLinearAsset(1, linkId1, SideCode.BothDirections, Some(SpeedLimitValue(40)), Seq(Point(0.0, 0.0), Point(1.0, 0.0)),
      false, 0.0, 1.0, Set(Point(0.0, 0.0), Point(1.0, 0.0)), Some("earlier modifier"), Some(DateTime.now().minus(1000)), None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimit2 = PieceWiseLinearAsset(2, linkId1, SideCode.BothDirections, Some(SpeedLimitValue(40)), Seq(Point(1.0, 0.0), Point(2.0, 0.0)),
      false, 1.0, 2.0, Set(Point(1.0, 0.0), Point(2.0, 0.0)), Some("earlier modifier"), Some(DateTime.now().minus(1000)), None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimit3 = PieceWiseLinearAsset(3, linkId1, SideCode.BothDirections, Some(SpeedLimitValue(40)), Seq(Point(2.0, 0.0), Point(3.0, 0.0)),
      false, 2.0, 3.0, Set(Point(2.0, 0.0), Point(3.0, 0.0)), Some("latest modifier"), Some(DateTime.now().minus(100)), None, None, SpeedLimitAsset.typeId, TrafficDirection.TowardsDigitizing,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimits = Map(
      linkId1 -> Seq(
        speedLimit1, speedLimit2, speedLimit3))
    val (filledTopology, changeSet) = SpeedLimitFiller.fuse(topology.map(SpeedLimitFiller.toRoadLinkForFillTopology).head, speedLimits.head._2, initChangeSet)
    filledTopology should have size 1
    filledTopology.map(_.sideCode) should be(Seq(SideCode.BothDirections))
    filledTopology.map(_.value) should be(Seq(Some(SpeedLimitValue(40))))
    filledTopology.map(_.modifiedBy) should be(Seq(Some("latest modifier"))) // latest modification should show
    changeSet.adjustedMValues should be(Seq(MValueAdjustment(3, linkId1, 0.0, 3.0)))
    changeSet.adjustedSideCodes should be(List())
    changeSet.expiredAssetIds should be(Set(1, 2))
  }

  test("create unknown speed limit on empty segments") {
    val topology = Seq(
      roadLink(linkId1, Seq(Point(0.0, 0.0), Point(100.0, 0.0)), State))
    val speedLimits = Map.empty[String, Seq[PieceWiseLinearAsset]]
    val (filledTopology, changeSet) = SpeedLimitFiller.generateUnknowns(topology.map(SpeedLimitFiller.toRoadLinkForFillTopology), speedLimits, SpeedLimitAsset.typeId)
    filledTopology should have size 1
    filledTopology.map(_.sideCode) should be(Seq(SideCode.BothDirections))
    filledTopology.map(_.value) should be(Seq(None))
    filledTopology.map(_.id) should be(Seq(0))
    filledTopology.head.startMeasure should be(0.0)
    filledTopology.head.endMeasure should be(100.0)
  }

  /*  test("test failing combination") {
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
        PieceWiseLinearAsset(1, linkId1, SideCode.BothDirections, Some(SpeedLimitValue(40)), Seq(Point(0.0, 0.0), Point(54.825, 0.0)),
          false, 0.0, 54.825, Set(Point(0.0, 0.0), Point(54.825, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
          0, None, NormalLinkInterface, Unknown, Map(), None, None, None),
        PieceWiseLinearAsset(2, linkId1, SideCode.BothDirections, Some(SpeedLimitValue(50)), Seq(Point(54.825, 0.0), Point(171.027, 0.0)),
          false, 54.825, 171.027, Set(Point(54.825, 0.0), Point(171.027, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
          0, None, NormalLinkInterface, Unknown, Map(), None, None, None))
  
      val changes = Seq(ChangeInfo(Some(oldRoadLink.linkId), Some(newLink2.linkId), 2l, 5, Some(113.53850795), Some(171.02731386), Some(0.0), Some(57.4888233), Some(1461844024000L)),
        ChangeInfo(Some(oldRoadLink.linkId), Some(newLink1.linkId), 2l, 6, Some(6.56875026), Some(113.53850795), Some(0.0), Some(106.96978931), Some(1461844024000L)),
        ChangeInfo(Some(oldRoadLink.linkId), Some(newLink3.linkId), 2l, 6, Some(0.0), Some(6.56875026), Some(0.0), Some(6.56875026), Some(1461844024000L))
      )
  
      val output = changes flatMap { change =>
        speedLimit.map(
          SpeedLimitFiller.projectSpeedLimit(_, linkmap.get(change.newId.get).get,
            Projection(change.oldStartMeasure.get, change.oldEndMeasure.get, change.newStartMeasure.get, change.newEndMeasure.get, change.timeStamp.get),
            ChangeSet(Set.empty, Nil, Nil, Nil, Set.empty, Nil))._1) } filter(sl => sl.startMeasure != sl.endMeasure)
    }*/

  test("Overlapping assets, asset is both direction and full length of link, split so it does not overlap") {
    val topology = Seq(
      roadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0))))

    val speedLimit1 = PieceWiseLinearAsset(1, linkId1, SideCode.BothDirections, Some(SpeedLimitValue(80)), Seq(Point(0.0, 0.0), Point(10, 0.0)),
      false, 0, 10, Set(Point(0.0, 0.0), Point(10, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimit2 = PieceWiseLinearAsset(2, linkId1, SideCode.TowardsDigitizing, Some(SpeedLimitValue(50)), Seq(Point(3, 0.0), Point(5, 0.0)),
      false, 3, 5, Set(Point(3, 0.0), Point(5, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimit3 = PieceWiseLinearAsset(3, linkId1, SideCode.AgainstDigitizing, Some(SpeedLimitValue(60)), Seq(Point(3, 0.0), Point(5, 0.0)),
      false, 3, 5, Set(Point(3, 0.0), Point(5, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)

    val speedLimits = Map(linkId1 -> Seq(speedLimit1, speedLimit2, speedLimit3))
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(topology.map(SpeedLimitFiller.toRoadLinkForFillTopology), speedLimits, SpeedLimitAsset.typeId)
    val sortedFilledTopology = filledTopology.sortBy(_.endMeasure)

    sortedFilledTopology.length should be(4)
    sortedFilledTopology.head.geometry should be(Seq(Point(0.0, 0.0), Point(3, 0.0)))
    sortedFilledTopology.head.startMeasure should be(0.0)
    sortedFilledTopology.head.endMeasure should be(3)
    sortedFilledTopology.head.sideCode should be(SideCode.BothDirections)
    sortedFilledTopology.head.value should be(Some(SpeedLimitValue(80)))

    sortedFilledTopology.exists(_.sideCode == SideCode.TowardsDigitizing) should be(true)

    sortedFilledTopology.find(_.sideCode == SideCode.TowardsDigitizing).get.geometry should be(Seq(Point(3.0, 0.0), Point(5, 0.0)))
    sortedFilledTopology.find(_.sideCode == SideCode.TowardsDigitizing).get.startMeasure should be(3.0)
    sortedFilledTopology.find(_.sideCode == SideCode.TowardsDigitizing).get.endMeasure should be(5)
    sortedFilledTopology.find(_.sideCode == SideCode.TowardsDigitizing).get.value should be(Some(SpeedLimitValue(50)))

    sortedFilledTopology.exists(_.sideCode == SideCode.AgainstDigitizing) should be(true)

    sortedFilledTopology.find(_.sideCode == SideCode.AgainstDigitizing).get.geometry should be(Seq(Point(3.0, 0.0), Point(5, 0.0)))
    sortedFilledTopology.find(_.sideCode == SideCode.AgainstDigitizing).get.startMeasure should be(3.0)
    sortedFilledTopology.find(_.sideCode == SideCode.AgainstDigitizing).get.endMeasure should be(5)
    sortedFilledTopology.find(_.sideCode == SideCode.AgainstDigitizing).get.value should be(Some(SpeedLimitValue(60)))


    sortedFilledTopology.last.geometry should be(Seq(Point(5, 0.0), Point(10.0, 0.0)))
    sortedFilledTopology.last.startMeasure should be(5)
    sortedFilledTopology.last.endMeasure should be(10.0)
    sortedFilledTopology.last.sideCode should be(SideCode.BothDirections)
    sortedFilledTopology.last.value should be(Some(SpeedLimitValue(80)))
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
      PieceWiseLinearAsset(1, linkId1, SideCode.BothDirections, Some(SpeedLimitValue(60)), Seq(Point(26.67, 0.0), Point(43.33, 0.0)),
        false, 26.67, 43.33, Set(Point(26.67, 0.0), Point(43.33, 0.0)), None, None, Some("blue bd"), bdblue, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
        0, None, NormalLinkInterface, Unknown, Map(), None, None, None),
      PieceWiseLinearAsset(2, linkId1, SideCode.BothDirections, Some(SpeedLimitValue(50)), Seq(Point(10.0, 0.0), Point(26.67, 0.0)),
        false, 10.0, 26.67, Set(Point(10.0, 0.0), Point(26.67, 0.0)), None, None, Some("red bd"), bdred, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
        0, None, NormalLinkInterface, Unknown, Map(), None, None, None),
      PieceWiseLinearAsset(3, linkId1, SideCode.BothDirections, Some(SpeedLimitValue(40)), Seq(Point(50.0, 0.0), Point(60, 0.0)),
        false, 50.0, 60.0, Set(Point(50.0, 0.0), Point(60, 0.0)), None, None, Some("green bd"), bdgreen, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
        0, None, NormalLinkInterface, Unknown, Map(), None, None, None),
      PieceWiseLinearAsset(4, linkId1, SideCode.TowardsDigitizing, Some(SpeedLimitValue(60)), Seq(Point(16.67, 0.0), Point(33.33, 0.0)),
        false, 16.67, 33.33, Set(Point(16.67, 0.0), Point(33.33, 0.0)), None, None, Some("blue td"), sdblue, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
        0, None, NormalLinkInterface, Unknown, Map(), None, None, None),
      PieceWiseLinearAsset(5, linkId1, SideCode.AgainstDigitizing, Some(SpeedLimitValue(60)), Seq(Point(23.33, 0.0), Point(36.67, 0.0)),
        false, 23.33, 36.67, Set(Point(23.33, 0.0), Point(36.67, 0.0)), None, None, Some("blue ad"), sdblue, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
        0, None, NormalLinkInterface, Unknown, Map(), None, None, None),
      PieceWiseLinearAsset(6, linkId1, SideCode.TowardsDigitizing, Some(SpeedLimitValue(50)), Seq(Point(0.0, 0.0), Point(16.67, 0.0)),
        false, 0.0, 16.67, Set(Point(0.0, 0.0), Point(16.67, 0.0)), None, None, Some("red td"), sdred1, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
        0, None, NormalLinkInterface, Unknown, Map(), None, None, None),
      PieceWiseLinearAsset(7, linkId1, SideCode.AgainstDigitizing, Some(SpeedLimitValue(50)), Seq(Point(0.0, 0.0), Point(10.0, 0.0)),
        false, 0.0, 10.0, Set(Point(0.0, 0.0), Point(10.0, 0.0)), None, None, Some("red ad"), sdred2, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
        0, None, NormalLinkInterface, Unknown, Map(), None, None, None),
      PieceWiseLinearAsset(8, linkId1, SideCode.AgainstDigitizing, Some(SpeedLimitValue(50)), Seq(Point(10.0, 0.0), Point(23.33, 0.0)),
        false, 10.0, 23.33, Set(Point(10.0, 0.0), Point(23.33, 0.0)), None, None, Some("red ad"), sdred3, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
        0, None, NormalLinkInterface, Unknown, Map(), None, None, None),
      PieceWiseLinearAsset(9, linkId1, SideCode.TowardsDigitizing, Some(SpeedLimitValue(40)), Seq(Point(33.33, 0.0), Point(50.0, 0.0)),
        false, 33.33, 50.0, Set(Point(33.33, 0.0), Point(50.0, 0.0)), None, None, Some("green bd"), sdgreen, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
        0, None, NormalLinkInterface, Unknown, Map(), None, None, None),
      PieceWiseLinearAsset(10, linkId1, SideCode.AgainstDigitizing, Some(SpeedLimitValue(40)), Seq(Point(36.67, 0.0), Point(50.0, 0.0)),
        false, 36.67, 50.0, Set(Point(36.67, 0.0), Point(50.0, 0.0)), None, None, Some("green bd"), sdgreen, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
        0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    )

    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(Seq(rLink).map(SpeedLimitFiller.toRoadLinkForFillTopology), Map(linkId1 -> speedLimit), SpeedLimitAsset.typeId)

    changeSet.expiredAssetIds should have size 4
    filledTopology.count(_.id != 0) should be(6)
    filledTopology.forall(_.value.nonEmpty) should be(true)

    // Test that filler is stable
    var counter = 0
    var unstable = true
    var topology = filledTopology
    while (counter < 100 && unstable) {
      counter = counter + 1
      val (refill, newChangeSet) = SpeedLimitFiller.fillTopology(Seq(rLink).map(SpeedLimitFiller.toRoadLinkForFillTopology), Map(linkId1 -> topology.map(sl => sl.copy(id = sl.id + 1))), SpeedLimitAsset.typeId)
      unstable = refill.size != topology.size || !refill.forall(sl => topology.find(_.id == sl.id - 1).get.copy(id = sl.id).equals(sl))
      topology = refill
    }
    counter should be < (100)
    unstable should be(false)
    topology should have size (5)
  }

  test("Should split older asset if necessary") {
    val rLink = roadLink(linkId1, Seq(Point(0.0, 0.0), Point(50.0, 0.0)))
    val linkmap = Map(linkId1 -> rLink)
    val edit1 = Option(DateTime.now().minusDays(7))
    val edit2 = Option(DateTime.now().minusDays(6))
    val speedLimit = Seq(
      PieceWiseLinearAsset(1, linkId1, SideCode.BothDirections, Some(SpeedLimitValue(60)), Seq(Point(0.0, 0.0), Point(50.0, 0.0)),
        false, 0.0, 50.0, Set(Point(0.0, 0.0), Point(50.0, 0.0)), None, None, Some("blue bd"), edit1, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
        0, None, NormalLinkInterface, Unknown, Map(), None, None, None),
      PieceWiseLinearAsset(2, linkId1, SideCode.BothDirections, Some(SpeedLimitValue(50)), Seq(Point(10.00, 0.0), Point(26.67, 0.0)),
        false, 10.0, 26.67, Set(Point(10.00, 0.0), Point(26.67, 0.0)), None, None, Some("red bd"), edit2, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
        0, None, NormalLinkInterface, Unknown, Map(), None, None, None))

    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(Seq(rLink).map(SpeedLimitFiller.toRoadLinkForFillTopology), Map(linkId1 -> speedLimit), SpeedLimitAsset.typeId)

    changeSet.droppedAssetIds should be(Set())
    filledTopology.length should be(3)
    filledTopology.map(_.id).toSet should be(Set(0, 1, 2))
    val newLink = filledTopology.find(_.startMeasure == 0.0).get
    newLink.endMeasure should be(10.0)
    newLink.value.get should be(SpeedLimitValue(60))
    val oldLink1 = filledTopology.find(_.startMeasure == 26.67).get
    oldLink1.endMeasure should be(50.0)
    oldLink1.value should be(Some(SpeedLimitValue(60)))
    val oldLink2 = filledTopology.find(_.startMeasure == 10.0).get
    oldLink2.startMeasure should be(10.0)
    oldLink2.endMeasure should be(26.67)
    oldLink2.value should be(Some(SpeedLimitValue(50)))

    val (refill, newChangeSet) = SpeedLimitFiller.fillTopology(Seq(rLink).map(SpeedLimitFiller.toRoadLinkForFillTopology), Map(linkId1 -> filledTopology), SpeedLimitAsset.typeId)
    refill should have size 3
    newChangeSet.adjustedMValues should have size 0
  }

  test("Should fill any holes it creates") {
    val rLink = roadLink(linkId1, Seq(Point(0.0, 0.0), Point(50.0, 0.0)))
    val linkmap = Map(linkId1 -> rLink)
    val edit1 = Option(DateTime.now().minusDays(7))
    val edit2 = Option(DateTime.now().minusDays(6))
    val speedLimit = Seq(
      PieceWiseLinearAsset(1, linkId1, SideCode.BothDirections, Some(SpeedLimitValue(60)), Seq(Point(0.0, 0.0), Point(50.0, 0.0)),
        false, 0.0, 50.0, Set(Point(0.0, 0.0), Point(50.0, 0.0)), None, None, Some("blue bd"), edit1, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
        0, None, NormalLinkInterface, Unknown, Map(), None, None, None),
      PieceWiseLinearAsset(2, linkId1, SideCode.BothDirections, Some(SpeedLimitValue(50)), Seq(Point(10.00, 0.0), Point(26.67, 0.0)),
        false, 10.0, 26.67, Set(Point(10.00, 0.0), Point(26.67, 0.0)), None, None, Some("red bd"), edit2, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
        0, None, NormalLinkInterface, Unknown, Map(), None, None, None),
      PieceWiseLinearAsset(3, linkId1, SideCode.BothDirections, Some(SpeedLimitValue(50)), Seq(Point(26.8, 0.0), Point(50, 0.0)),
        false, 26.74, 50, Set(Point(26.8, 0.0), Point(50, 0.0)), None, None, Some("red bd"), edit2, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
        0, None, NormalLinkInterface, Unknown, Map(), None, None, None))

    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(Seq(rLink).map(SpeedLimitFiller.toRoadLinkForFillTopology), Map(linkId1 -> speedLimit), SpeedLimitAsset.typeId)
    changeSet.expiredAssetIds should be(Set(3))
    filledTopology.length should be(2)
    val oldLink0 = filledTopology.find(_.startMeasure == 0.0).get
    oldLink0.endMeasure should be(10.0)
    oldLink0.value.get should be(SpeedLimitValue(60))
    val oldLink1 = filledTopology.find(_.startMeasure == 10.0).get
    oldLink1.endMeasure should be(50.0)
    oldLink1.value.get should be(SpeedLimitValue(50))
  }

  def parse(string: String): DateTime = {
    val dateTimePropertyFormat = DateTimeFormat.forPattern("dd.MM.yyyy HH:mm")
    DateTime.parse(string, dateTimePropertyFormat)
  }

  test("should return sensible geometry on combinable entries") {
    val linkId = generateRandomLinkId()
    val rLink = roadLink(linkId, Seq(Point(0.0, 0.0), Point(66.463, 0.0)))
    val speedLimit = Seq(
      PieceWiseLinearAsset(1, linkId, SideCode.apply(2), Option(SpeedLimitValue(100)), Seq(),
        false, 42.545, 66.463, Set(), None, None, Option(AutoGeneratedUsername.dr1Conversion), Option(parse("28.10.2014 14:56")), SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
        0, None, NormalLinkInterface, Unknown, Map(), None, None, None),
      PieceWiseLinearAsset(2, linkId, SideCode.apply(3), Option(SpeedLimitValue(100)), Seq(),
        false, 42.545, 66.463, Set(), None, None, Option(AutoGeneratedUsername.dr1Conversion), Option(parse("28.10.2014 14:56")), SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
        0, None, NormalLinkInterface, Unknown, Map(), None, None, None),
      PieceWiseLinearAsset(3, linkId, SideCode.apply(2), Option(SpeedLimitValue(80)), Seq(),
        false, 0, 42.545, Set(), None, None, Option(AutoGeneratedUsername.dr1Conversion), Option(parse("28.10.2014 14:56")), SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
        0, None, NormalLinkInterface, Unknown, Map(), None, None, None),
      PieceWiseLinearAsset(4, linkId, SideCode.apply(3), Option(SpeedLimitValue(80)), Seq(),
        false, 0, 42.545, Set(), None, None, Option(AutoGeneratedUsername.dr1Conversion), Option(parse("28.10.2014 14:56")), SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
        0, None, NormalLinkInterface, Unknown, Map(), None, None, None))

    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(Seq(rLink).map(SpeedLimitFiller.toRoadLinkForFillTopology), speedLimit.groupBy(_.linkId), SpeedLimitAsset.typeId)
    changeSet.expiredAssetIds should have size (2)
    changeSet.adjustedSideCodes should have size (2)
  }

  test("should not break opposite directions") {
    val linkId = generateRandomLinkId()
    val rLink = roadLink(linkId, Seq(Point(0.0, 0.0), Point(323.203, 0.0)))
    val speedLimit = Seq(
      PieceWiseLinearAsset(1, linkId, SideCode.apply(3), Option(SpeedLimitValue(60)), Seq(),
        false, 0, 199.502, Set(), None, None, Option(AutoGeneratedUsername.dr1Conversion), Option(parse("28.10.2014 14:56")), SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
        0, None, NormalLinkInterface, Unknown, Map(), None, None, None),
      PieceWiseLinearAsset(2, linkId, SideCode.apply(2), Option(SpeedLimitValue(60)), Seq(),
        false, 0, 323.203, Set(), None, None, Option(AutoGeneratedUsername.dr1Conversion), Option(parse("28.10.2014 14:56")), SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
        0, None, NormalLinkInterface, Unknown, Map(), None, None, None),
      PieceWiseLinearAsset(3, linkId, SideCode.apply(3), Option(SpeedLimitValue(80)), Seq(),
        false, 199.502, 323.203, Set(), None, None, Option(AutoGeneratedUsername.dr1Conversion), Option(parse("28.10.2014 14:56")), SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
        0, None, NormalLinkInterface, Unknown, Map(), None, None, None))

    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(Seq(rLink).map(SpeedLimitFiller.toRoadLinkForFillTopology), speedLimit.groupBy(_.linkId), SpeedLimitAsset.typeId)
    changeSet.droppedAssetIds should have size (0)
  }

  test("should not combine entries that disagree") {
    val linkId = generateRandomLinkId()
    val rLink = roadLink(linkId, Seq(Point(0.0, 0.0), Point(66.463, 0.0)))
    val geom1 = GeometryUtils.truncateGeometry3D(rLink.geometry, 42.545, 66.463)
    val geom2 = GeometryUtils.truncateGeometry3D(rLink.geometry, 0, 42.545)
    val speedLimit = Seq(
      PieceWiseLinearAsset(1, linkId, SideCode.apply(2), Option(SpeedLimitValue(100)), geom1,
        false, 42.545, 66.463, geom1.toSet, None, None, Option(AutoGeneratedUsername.dr1Conversion), Option(parse("28.10.2014 14:56")), SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
        0, None, NormalLinkInterface, Unknown, Map(), None, None, None),
      PieceWiseLinearAsset(2, linkId, SideCode.apply(3), Option(SpeedLimitValue(80)), geom1,
        false, 42.545, 66.463, geom1.toSet, None, None, Option(AutoGeneratedUsername.dr1Conversion), Option(parse("28.10.2014 14:56")), SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
        0, None, NormalLinkInterface, Unknown, Map(), None, None, None),
      PieceWiseLinearAsset(3, linkId, SideCode.apply(3), Option(SpeedLimitValue(100)), geom2,
        false, 0, 42.545, geom2.toSet, None, None, Option(AutoGeneratedUsername.dr1Conversion), Option(parse("28.10.2014 14:56")), SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
        0, None, NormalLinkInterface, Unknown, Map(), None, None, None),
      PieceWiseLinearAsset(4, linkId, SideCode.apply(2), Option(SpeedLimitValue(80)), geom2,
        false, 0, 42.545, geom2.toSet, None, None, Option(AutoGeneratedUsername.dr1Conversion), Option(parse("28.10.2014 14:56")), SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
        0, None, NormalLinkInterface, Unknown, Map(), None, None, None))

    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(Seq(rLink).map(SpeedLimitFiller.toRoadLinkForFillTopology), speedLimit.groupBy(_.linkId), SpeedLimitAsset.typeId)
    changeSet.droppedAssetIds should have size (0)
    changeSet.adjustedSideCodes should have size (0)
    changeSet.adjustedMValues should have size (0)
  }


  test("should not combine different values on opposite directions") {
    val rLink = roadLink(linkId1, Seq(Point(0.0, 0.0), Point(50.0, 0.0)))
    val edit1 = Option(DateTime.now().minusDays(7))
    val edit2 = Option(DateTime.now().minusDays(6))
    val speedLimit = Seq(
      PieceWiseLinearAsset(1, linkId1, SideCode.BothDirections, Some(SpeedLimitValue(60)), Seq(Point(0.0, 0.0), Point(30.0, 0.0)),
        false, 0.0, 30.0, Set(Point(0.0, 0.0), Point(30.0, 0.0)), None, None, Some("blue bd"), edit1, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
        0, None, NormalLinkInterface, Unknown, Map(), None, None, None),
      PieceWiseLinearAsset(2, linkId1, SideCode.TowardsDigitizing, Some(SpeedLimitValue(50)), Seq(Point(30.00, 0.0), Point(50.0, 0.0)),
        false, 30.0, 50.0, Set(Point(30.00, 0.0), Point(50.0, 0.0)), None, None, Some("red td"), edit2, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
        0, None, NormalLinkInterface, Unknown, Map(), None, None, None),
      PieceWiseLinearAsset(3, linkId1, SideCode.AgainstDigitizing, Some(SpeedLimitValue(60)), Seq(Point(30.0, 0.0), Point(50.0, 0.0)),
        false, 30.0, 50.0, Set(Point(30.0, 0.0), Point(50.0, 0.0)), None, None, Some("blue ad"), edit2, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
        0, None, NormalLinkInterface, Unknown, Map(), None, None, None))

    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(Seq(rLink).map(SpeedLimitFiller.toRoadLinkForFillTopology), Map(linkId1 -> speedLimit), SpeedLimitAsset.typeId)

    filledTopology.length should be(3)
    val oldLink1 = filledTopology.find(_.value.contains(SpeedLimitValue(50))).get
    oldLink1.startMeasure should be(30.0)
    oldLink1.endMeasure should be(50.00)
    val oldLink2 = filledTopology.find(_.createdBy.contains("blue ad")).get
    oldLink2.startMeasure should be(30.0)
    oldLink2.endMeasure should be(50.00)
    oldLink2.value.get should be(SpeedLimitValue(60))
  }

  test("do not change side code to BothDirections if there is a speed limit on the other side") {
    val roadLink = RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(20, 0.0)), 20, AdministrativeClass.apply(2), UnknownFunctionalClass.value,
      TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())

    val speedLimits = Seq(
      PieceWiseLinearAsset(1, linkId1, SideCode.TowardsDigitizing, Some(SpeedLimitValue(60)), Seq(Point(0.0, 0.0), Point(20.0, 0.0)),
        false, 0.0, 20.0, Set(Point(0.0, 0.0), Point(20.0, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
        0, None, NormalLinkInterface, Unknown, Map(), None, None, None),
      PieceWiseLinearAsset(2, linkId1, SideCode.AgainstDigitizing, Some(SpeedLimitValue(50)), Seq(Point(0.0, 0.0), Point(20.0, 0.0)),
        false, 0.0, 20.0, Set(Point(0.0, 0.0), Point(20.0, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
        0, None, NormalLinkInterface, Unknown, Map(), None, None, None))

    val (adjustedAssets, changeSet) = SpeedLimitFiller.adjustSegmentSideCodes(SpeedLimitFiller.toRoadLinkForFillTopology(roadLink), speedLimits, initChangeSet)

    val sortedSpeedLimits = adjustedAssets.sortBy(_.id)

    sortedSpeedLimits.size should be(2)
    sortedSpeedLimits.head.sideCode should be(SideCode.TowardsDigitizing)
    sortedSpeedLimits.last.sideCode should be(SideCode.AgainstDigitizing)

    changeSet.adjustedSideCodes.size should be(0)
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
