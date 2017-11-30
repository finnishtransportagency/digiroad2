package fi.liikennevirasto.digiroad2.linearasset

import org.joda.time.DateTime
import fi.liikennevirasto.digiroad2.GeometryUtils.Projection
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, BothDirections, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.{ChangeSet, MValueAdjustment, SideCodeAdjustment}
import org.scalatest._

class NumericalLimitFillerSpec extends FunSuite with Matchers {
  test("create non-existent linear assets on empty road links") {
    val topology = Seq(
      RoadLink(1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
        1, TrafficDirection.BothDirections, Motorway, None, None))
    val linearAssets = Map.empty[Long, Seq[PersistedLinearAsset]]
    val (filledTopology, _) = NumericalLimitFiller.fillTopology(topology, linearAssets, 30)
    filledTopology should have size 1
    filledTopology.map(_.sideCode) should be(Seq(BothDirections))
    filledTopology.map(_.value) should be(Seq(None))
    filledTopology.map(_.id) should be(Seq(0))
    filledTopology.map(_.linkId) should be(Seq(1))
    filledTopology.map(_.geometry) should be(Seq(Seq(Point(0.0, 0.0), Point(10.0, 0.0))))
  }

  test("expire assets that fall completely outside topology") {
    val topology = Seq(
      RoadLink(1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
        1, TrafficDirection.BothDirections, Motorway, None, None))
    val linearAssets = Map(
      1l -> Seq(PersistedLinearAsset(1l, 1l, 1, Some(NumericValue(1)), 10.0, 15.0, None, None, None, None, false, 110, 0, None, linkSource = NormalLinkInterface, None, None)))

    val (filledTopology, changeSet) = NumericalLimitFiller.fillTopology(topology, linearAssets, 110)

    filledTopology should have size 1
    filledTopology.map(_.sideCode) should be(Seq(BothDirections))
    filledTopology.map(_.value) should be(Seq(None))
    filledTopology.map(_.id) should be(Seq(0))
    filledTopology.map(_.linkId) should be(Seq(1))
    filledTopology.map(_.geometry) should be(Seq(Seq(Point(0.0, 0.0), Point(10.0, 0.0))))

    changeSet.expiredAssetIds should be(Set(1l))
    changeSet.droppedAssetIds should be(Set())
  }

  test("cap assets that go over roadlink geometry") {
    val topology = Seq(
      RoadLink(1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
        1, TrafficDirection.BothDirections, Motorway, None, None))
    val linearAssets = Map(
      1l -> Seq(PersistedLinearAsset(1l, 1l, 1, Some(NumericValue(1)), 0.0, 15.0, None, None, None, None, false, 110, 0, None, linkSource = NormalLinkInterface, None, None)))

    val (filledTopology, changeSet) = NumericalLimitFiller.fillTopology(topology, linearAssets, 110)

    filledTopology should have size 1
    filledTopology.map(_.sideCode) should be(Seq(BothDirections))
    filledTopology.map(_.value) should be(Seq(Some(NumericValue(1))))
    filledTopology.map(_.id) should be(Seq(1))
    filledTopology.map(_.linkId) should be(Seq(1))
    filledTopology.map(_.geometry) should be(Seq(Seq(Point(0.0, 0.0), Point(10.0, 0.0))))

    changeSet.droppedAssetIds should be(Set())
    changeSet.expiredAssetIds should be(Set())
    changeSet.adjustedMValues should be(Seq(MValueAdjustment(1l, 1l, 0.0, 10.0)))
  }

  test("transform one-sided asset to two-sided when its defined on one-way road link") {
    val topology = Seq(
      RoadLink(1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
        1, TrafficDirection.TowardsDigitizing, Motorway, None, None),
      RoadLink(2, Seq(Point(10.0, 0.0), Point(20.0, 0.0)), 10.0, Municipality,
        1, TrafficDirection.TowardsDigitizing, Motorway, None, None)
    )
    val linearAssets = Map(
      1l -> Seq(PersistedLinearAsset(1l, 1l, 2, Some(NumericValue(1)), 0.0, 10.0, None, None, None, None, false, 110, 0, None, linkSource = NormalLinkInterface, None, None)),
      2l -> Seq(
        PersistedLinearAsset(2l, 2l, 2, Some(NumericValue(1)), 0.0, 5.0, None, None, None, None, false, 110, 0, None, linkSource = NormalLinkInterface, None, None),
        PersistedLinearAsset(3l, 2l, 3, Some(NumericValue(1)), 7.0, 10.0, None, None, None, None, false, 110, 0, None, linkSource = NormalLinkInterface, None, None),
        PersistedLinearAsset(4l, 2l, SideCode.BothDirections.value, Some(NumericValue(1)), 5.0, 7.0, None, None, None, None, false, 110, 0, None, linkSource = NormalLinkInterface, None, None)
      )
    )

    val (filledTopology, changeSet) = NumericalLimitFiller.fillTopology(topology, linearAssets, 110)

    filledTopology should have size 4

    filledTopology.filter(_.id == 1l).map(_.sideCode) should be(Seq(BothDirections))
    filledTopology.filter(_.id == 1l).map(_.linkId) should be(Seq(1l))

    filledTopology.filter(_.id == 2l).map(_.sideCode) should be(Seq(BothDirections))
    filledTopology.filter(_.id == 2l).map(_.linkId) should be(Seq(2l))

    filledTopology.filter(_.id == 3l).map(_.sideCode) should be(Seq(BothDirections))
    filledTopology.filter(_.id == 3l).map(_.linkId) should be(Seq(2l))

    filledTopology.filter(_.id == 4l).map(_.sideCode) should be(Seq(BothDirections))
    filledTopology.filter(_.id == 4l).map(_.linkId) should be(Seq(2l))

    changeSet.adjustedSideCodes should be(Seq(
      SideCodeAdjustment(1l, SideCode.BothDirections),
      SideCodeAdjustment(2l, SideCode.BothDirections),
      SideCodeAdjustment(3l, SideCode.BothDirections)))
  }

  test("generate one-sided asset when two-way road link is half-covered") {
    val topology = Seq(
      RoadLink(1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
        1, TrafficDirection.BothDirections, Motorway, None, None))
    val linearAssets = Map(
      1l -> Seq(PersistedLinearAsset(1l, 1l, 2, Some(NumericValue(1)), 0.0, 10.0, None, None, None, None, false, 110, 0, None, linkSource = NormalLinkInterface, None, None)))

    val (filledTopology, changeSet) = NumericalLimitFiller.fillTopology(topology, linearAssets, 110)

    filledTopology should have size 2
    filledTopology.filter(_.id == 1).map(_.sideCode) should be(Seq(TowardsDigitizing))
    filledTopology.filter(_.id == 1).map(_.value) should be(Seq(Some(NumericValue(1))))
    filledTopology.filter(_.id == 1).map(_.geometry) should be(Seq(Seq(Point(0.0, 0.0), Point(10.0, 0.0))))

    filledTopology.filter(_.id == 0).map(_.sideCode) should be(Seq(AgainstDigitizing))
    filledTopology.filter(_.id == 0).map(_.value) should be(Seq(None))
    filledTopology.filter(_.id == 0).map(_.geometry) should be(Seq(Seq(Point(0.0, 0.0), Point(10.0, 0.0))))

    changeSet should be(ChangeSet(Set.empty[Long], Nil, Nil, Set.empty[Long]))
  }

  test("project road lights to new geometry") {
    val oldRoadLink = roadLink(1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)))
    val newLink1 = roadLink(1, Seq(Point(0.0, 0.0), Point(3.0, 0.0)))
    val newLink2 = roadLink(2, Seq(Point(0.0, 0.0), Point(4.0, 0.0)))
    val newLink3 = roadLink(3, Seq(Point(0.0, 0.0), Point(3.0, 0.0)))
    val linkmap = Map(1L -> newLink1, 2L -> newLink2, 3L -> newLink3)
    val assets = Seq(
      PersistedLinearAsset(1, 1, SideCode.BothDirections.value, None, 0.0, 10.0, Some("guy"),
      None, None, None, expired = false, 100, 0, None, linkSource = NormalLinkInterface, None, None)
        )
    val changes = Seq(ChangeInfo(Some(1l), Some(1l), 2l, 5, Some(0.0), Some(3.0), Some(0.0), Some(3.0), Some(1440000)),
      ChangeInfo(Some(1l), Some(2l), 22, 6, Some(3.0), Some(7.0), Some(0.0), Some(4.0), Some(1440000)),
      ChangeInfo(Some(1l), Some(3l), 23, 6, Some(7.0), Some(10.0), Some(3.0), Some(0.0), Some(1440000))
    )
    val output = changes map { change =>
      NumericalLimitFiller.projectLinearAsset(assets.head, linkmap.get(change.newId.get).get,
        Projection(change.oldStartMeasure.get, change.oldEndMeasure.get, change.newStartMeasure.get, change.newEndMeasure.get, change.vvhTimeStamp.get)) }
    output.length should be(3)
    output.head.sideCode should be (SideCode.BothDirections.value)
    output.head.startMeasure should be(0.0)
    output.head.endMeasure should be(3.0)
    output.last.endMeasure should be(3.0)
  }

  test("project paved road to new geometry, one side paved, should switch the last turned link segment") {
    val oldRoadLink = roadLink(1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)))
    val newLink1 = roadLink(1, Seq(Point(0.0, 0.0), Point(3.0, 0.0)))
    val newLink2 = roadLink(2, Seq(Point(0.0, 0.0), Point(4.0, 0.0)))
    val newLink3 = roadLink(3, Seq(Point(0.0, 0.0), Point(3.0, 0.0)))
    val linkmap = Map(1L -> newLink1, 2L -> newLink2, 3L -> newLink3)
    val assets = Seq(
      PersistedLinearAsset(1, 1, SideCode.TowardsDigitizing.value, None, 0.0, 10.0, Some("guy"),
        None, None, None, expired = false, 110, 0, None, linkSource = NormalLinkInterface, None, None),
        PersistedLinearAsset(2, 1, SideCode.AgainstDigitizing.value, None, 0.0, 10.0, Some("guy"),
        None, None, None, expired = false, 110, 0, None, linkSource = NormalLinkInterface, None, None)
    )

    val changes = Seq(ChangeInfo(Some(1l), Some(1l), 2l, 5, Some(0.0), Some(3.0), Some(0.0), Some(3.0), Some(1440000)),
      ChangeInfo(Some(1l), Some(2l), 22, 6, Some(3.0), Some(7.0), Some(0.0), Some(4.0), Some(1440000)),
      ChangeInfo(Some(1l), Some(3l), 23, 6, Some(7.0), Some(10.0), Some(3.0), Some(0.0), Some(1440000))
    )

    val output = changes map { change =>
      NumericalLimitFiller.projectLinearAsset(assets.head, linkmap.get(change.newId.get).get,
        Projection(change.oldStartMeasure.get, change.oldEndMeasure.get, change.newStartMeasure.get, change.newEndMeasure.get, change.vvhTimeStamp.get)) }
    output.head.sideCode should be (SideCode.TowardsDigitizing.value)
    output.last.sideCode should be (SideCode.AgainstDigitizing.value)
    output.head.startMeasure should be(0.0)
    output.head.endMeasure should be(3.0)
    output.last.startMeasure should be(0.0)
    output.last.endMeasure should be(3.0)

    val output2 = changes map { change =>
      NumericalLimitFiller.projectLinearAsset(assets.last, linkmap.get(change.newId.get).get,
        Projection(change.oldStartMeasure.get, change.oldEndMeasure.get, change.newStartMeasure.get, change.newEndMeasure.get, change.vvhTimeStamp.get)) }
    output2.length should be(3)
    output2.head.sideCode should be (SideCode.AgainstDigitizing.value)
    output2.last.sideCode should be (SideCode.TowardsDigitizing.value)
    output2.head.startMeasure should be(0.0)
    output2.head.endMeasure should be(3.0)
    output2.last.startMeasure should be(0.0)
    output2.last.endMeasure should be(3.0)
  }

  test("project thawing asset to new geometry, cuts short") {
    val oldRoadLink = roadLink(1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)))
    val newLink1 = roadLink(1, Seq(Point(0.0, 0.0), Point(3.0, 0.0)))
    val newLink2 = roadLink(2, Seq(Point(0.0, 0.0), Point(4.0, 0.0)))
    val newLink3 = roadLink(3, Seq(Point(0.0, 0.0), Point(3.0, 0.0)))
    val linkmap = Map(1L -> newLink1, 2L -> newLink2, 3L -> newLink3)
    val assets = Seq(
      PersistedLinearAsset(1, 1, SideCode.TowardsDigitizing.value, None, 0.0, 9.0, Some("guy"),
        None, None, None, expired = false, 130, 0, None, linkSource = NormalLinkInterface, None, None)
    )

    val changes = Seq(ChangeInfo(Some(1l), Some(1l), 2l, 5, Some(0.0), Some(3.0), Some(0.0), Some(3.0), Some(1440000)),
      ChangeInfo(Some(1l), Some(2l), 22, 6, Some(3.0), Some(7.0), Some(0.0), Some(4.0), Some(1440000)),
      ChangeInfo(Some(1l), Some(3l), 23, 6, Some(7.0), Some(10.0), Some(3.0), Some(0.0), Some(1440000))
    )

    val output = changes flatMap { change =>
      assets.map(
        NumericalLimitFiller.projectLinearAsset(_, linkmap.get(change.newId.get).get,
          Projection(change.oldStartMeasure.get, change.oldEndMeasure.get, change.newStartMeasure.get, change.newEndMeasure.get, change.vvhTimeStamp.get))) } filter(sl => sl.startMeasure != sl.endMeasure)

    output.head.sideCode should be (SideCode.TowardsDigitizing.value)
    output.last.sideCode should be (SideCode.AgainstDigitizing.value)
    output.head.startMeasure should be(0.0)
    output.head.endMeasure should be(3.0)
    output.last.startMeasure should be(1.0)
    output.last.endMeasure should be(3.0)
    output.length should be (3)
  }

  test("drop segments less than 2 meters"){
    val roadLink = RoadLink(1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, AdministrativeClass.apply(1), FunctionalClass.Unknown,
      TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    val assets = Seq(
      PersistedLinearAsset(1, 1, SideCode.BothDirections.value, Some(NumericValue(2)), 0.0, 1.9, Some("guy"),
        Some(DateTime.now()), None, None, expired = false, 140, 0, None, linkSource = NormalLinkInterface, None, None),
      PersistedLinearAsset(2, 1, SideCode.BothDirections.value, Some(NumericValue(2)), 8.0, 10.0, Some("guy"),
        Some(DateTime.now()), None, None, expired = false, 140, 0, None, linkSource = NormalLinkInterface, None, None)
    )

    val (filledTopology, changeSet) = NumericalLimitFiller.fillTopology(Seq(roadLink), Map(1L -> assets), 140)
    filledTopology should have size 2
    filledTopology.map(_.id).sorted should be (Seq(0,2))
    changeSet.droppedAssetIds should have size 1
    changeSet.droppedAssetIds.head should be (1)
  }

  test("Don't drop segments less than 2 meters on a road link with length less that 2 meters"){
    val roadLink = RoadLink(1, Seq(Point(0.0, 0.0), Point(1.9, 0.0)), 1.9, AdministrativeClass.apply(1), FunctionalClass.Unknown,
      TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    val assets = Seq(
      PersistedLinearAsset(1, 1, SideCode.BothDirections.value, Some(NumericValue(2)), 0.0, 1.9, Some("guy"),
        Some(DateTime.now()), None, None, expired = false, 140, 0, None, linkSource = NormalLinkInterface, None, None)
    )

    val (filledTopology, changeSet) = NumericalLimitFiller.fillTopology(Seq(roadLink), Map(1L -> assets), 140)
    filledTopology should have size 1
    filledTopology.map(_.id) should be (Seq(1))
    changeSet.droppedAssetIds should have size 0
  }

  test("project mass transit lanes to new geometry") {
    val oldRoadLink = roadLink(1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)))
    val newLink1 = roadLink(1, Seq(Point(0.0, 0.0), Point(3.0, 0.0)))
    val newLink2 = roadLink(2, Seq(Point(0.0, 0.0), Point(4.0, 0.0)))
    val newLink3 = roadLink(3, Seq(Point(0.0, 0.0), Point(3.0, 0.0)))
    val linkmap = Map(1L -> newLink1, 2L -> newLink2, 3L -> newLink3)
    val assets = Seq(
      PersistedLinearAsset(1, 1, SideCode.TowardsDigitizing.value, None, 1.0, 10.0, Some("guy"),
        None, None, None, expired = false, 160, 0, None, linkSource = NormalLinkInterface, None, None),
      PersistedLinearAsset(2, 1, SideCode.AgainstDigitizing.value, None, 0.0, 9.0, Some("guy"),
        None, None, None, expired = false, 160, 0, None, linkSource = NormalLinkInterface, None, None)
    )

    val changes = Seq(ChangeInfo(Some(1l), Some(1l), 2l, 5, Some(0.0), Some(3.0), Some(0.0), Some(3.0), Some(1440000)),
      ChangeInfo(Some(1l), Some(2l), 22, 6, Some(3.0), Some(7.0), Some(0.0), Some(4.0), Some(1440000)),
      ChangeInfo(Some(1l), Some(3l), 23, 6, Some(7.0), Some(10.0), Some(3.0), Some(0.0), Some(1440000))
    )

    val output = changes flatMap { change =>
      assets.map(
        NumericalLimitFiller.projectLinearAsset(_, linkmap.get(change.newId.get).get,
          Projection(change.oldStartMeasure.get, change.oldEndMeasure.get, change.newStartMeasure.get, change.newEndMeasure.get, change.vvhTimeStamp.get))) } filter(sl => sl.startMeasure != sl.endMeasure)

    output.filter(o => o.linkId == 1 && o.sideCode == SideCode.TowardsDigitizing.value).forall(_.startMeasure == 1.0) should be (true)
    output.filter(o => o.linkId == 1 && o.sideCode == SideCode.AgainstDigitizing.value).forall(_.startMeasure == 0.0) should be (true)
    output.filter(o => o.linkId == 1 && o.sideCode == SideCode.TowardsDigitizing.value).forall(_.endMeasure == 3.0) should be (true)
    output.filter(o => o.linkId == 1 && o.sideCode == SideCode.AgainstDigitizing.value).forall(_.endMeasure == 3.0) should be (true)
    output.filter(o => o.linkId == 3 && o.sideCode == SideCode.AgainstDigitizing.value).forall(_.startMeasure == 0.0) should be (true)
    output.filter(o => o.linkId == 3 && o.sideCode == SideCode.TowardsDigitizing.value).forall(_.startMeasure == 1.0) should be (true)
    output.filter(o => o.linkId == 3 && o.sideCode == SideCode.AgainstDigitizing.value).forall(_.endMeasure == 3.0) should be (true)
    output.filter(o => o.linkId == 3 && o.sideCode == SideCode.TowardsDigitizing.value).forall(_.endMeasure == 3.0) should be (true)
    output.length should be (6)
  }

  test("combine two segments with same value in same RoadLink") {
    val roadLink = RoadLink(1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, AdministrativeClass.apply(1), FunctionalClass.Unknown,
      TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    val assets = Seq(
      PersistedLinearAsset(1, 1, SideCode.BothDirections.value, Some(NumericValue(2)), 0.0, 4.5, Some("guy"),
        Some(DateTime.now()), None, None, expired = false, 140, 0, None, linkSource = NormalLinkInterface, None, None),
      PersistedLinearAsset(2, 1, SideCode.BothDirections.value, Some(NumericValue(2)), 4.5, 10.0, Some("guy"),
        Some(DateTime.now()), None, None, expired = false, 140, 0, None, linkSource = NormalLinkInterface,None, None)
    )

    val (filledTopology, changeSet) = NumericalLimitFiller.fillTopology(Seq(roadLink), Map(1L -> assets), 140)
    filledTopology should have size 1
    filledTopology.map(_.sideCode) should be(Seq(SideCode.BothDirections))
    filledTopology.map(_.value) should be(Seq(Some(NumericValue(2))))
    filledTopology.map(_.typeId) should be(List(140))
    filledTopology.map(_.startMeasure) should be(List(0.0))
    filledTopology.map(_.endMeasure) should be(List(10.0))
    changeSet.adjustedMValues should have size 1
    changeSet.adjustedSideCodes should be(List())
  }

  test("fuse two segments with same value in same RoadLink with different side code") {
    val roadLinks = Seq(
      RoadLink(1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, AdministrativeClass.apply(1), FunctionalClass.Unknown,
        TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    )
    val assets = Seq(
      PersistedLinearAsset(1, 1, SideCode.TowardsDigitizing.value, Some(NumericValue(2)), 0.0, 10.0, Some("guy"),
        Some(DateTime.now()), None, None, expired = false, 140, 0, None, linkSource = NormalLinkInterface, None, None),
      PersistedLinearAsset(2, 1, SideCode.AgainstDigitizing.value, Some(NumericValue(2)), 0.0, 10.0, Some("guy"),
        Some(DateTime.now()), None, None, expired = false, 140, 0, None, linkSource = NormalLinkInterface, None, None)
    )

    val (filledTopology, changeSet) = NumericalLimitFiller.fillTopology(roadLinks, Map(1L -> assets), 140)
    filledTopology should have size 1
    filledTopology.map(_.sideCode) should be(Seq(SideCode.BothDirections))
    filledTopology.map(_.value) should be(Seq(Some(NumericValue(2))))
    filledTopology.map(_.createdBy) should be(Seq(Some("guy")))
    filledTopology.map(_.typeId) should be(List(140))
    filledTopology.map(_.startMeasure) should be(List(0.0))
    filledTopology.map(_.endMeasure) should be(List(10.0))
    changeSet.adjustedMValues should be(List())
    changeSet.adjustedSideCodes.map(_.assetId) should be(List(1))
    changeSet.adjustedSideCodes.map(_.sideCode) should be(List(SideCode.BothDirections))
    changeSet.droppedAssetIds should be(Set())
    changeSet.expiredAssetIds should be(Set(2))
  }

  test("adjustSegments with None value") {
    val roadLink = RoadLink(1, Seq(Point(0.0, 0.0), Point(15.0, 0.0)), 15.0, AdministrativeClass.apply(1), FunctionalClass.Unknown,
    TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    val assets = Seq(
      PersistedLinearAsset(1, 1, SideCode.BothDirections.value, None, 0.0, 4.5, Some("guy"),
        Some(DateTime.now()), None, None, expired = false, 160, 0, None, linkSource = NormalLinkInterface, None, None),
      PersistedLinearAsset(2, 1, SideCode.BothDirections.value, None, 4.5, 9.0, Some("guy"),
        Some(DateTime.now().minusDays(2)), None, None, expired = false, 160, 0, None, linkSource = NormalLinkInterface, None, None),
      PersistedLinearAsset(3, 1, SideCode.BothDirections.value, None, 9.0, 15.0, Some("guy"),
        Some(DateTime.now().minusDays(1)), None, None, expired = false, 160, 0, None, linkSource = NormalLinkInterface, None, None)
    )
    val (filledTopology, changeSet) = NumericalLimitFiller.fillTopology(Seq(roadLink), Map(1L -> assets), 160)
    filledTopology.length should be (1)
    filledTopology.head.id should be (1)
    filledTopology.head.endMeasure should be (15.0)
    filledTopology.head.startMeasure should be (0.0)
    changeSet.adjustedMValues.length should be (1)
    changeSet.adjustedMValues.head.endMeasure should be (15.0)
    changeSet.adjustedMValues.head.startMeasure should be (0.0)
    changeSet.expiredAssetIds should be (Set(2,3))
  }

  test("adjustSegments with value") {
    val roadLink = RoadLink(1, Seq(Point(0.0, 0.0), Point(15.0, 0.0)), 15.0, AdministrativeClass.apply(1), FunctionalClass.Unknown,
      TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    val assets = Seq(
      PersistedLinearAsset(1, 1, SideCode.BothDirections.value, Some(NumericValue(10)), 0.0, 4.5, Some("guy"),
        Some(DateTime.now()), None, None, expired = false, 160, 0, None, linkSource = NormalLinkInterface, None, None),
      PersistedLinearAsset(2, 1, SideCode.BothDirections.value, Some(NumericValue(10)), 4.5, 9.0, Some("guy"),
        Some(DateTime.now().minusDays(2)), None, None, expired = false, 160, 0, None, linkSource = NormalLinkInterface, None, None),
      PersistedLinearAsset(3, 1, SideCode.BothDirections.value, Some(NumericValue(10)), 9.0, 15.0, Some("guy"),
        Some(DateTime.now().minusDays(1)), None, None, expired = false, 160, 0, None, linkSource = NormalLinkInterface, None, None)
    )
    val (filledTopology, changeSet) = NumericalLimitFiller.fillTopology(Seq(roadLink), Map(1L -> assets), 160)
    filledTopology.length should be (1)
    filledTopology.head.id should be (1)
    filledTopology.head.endMeasure should be (15.0)
    filledTopology.head.startMeasure should be (0.0)
    changeSet.adjustedMValues.length should be (1)
    changeSet.adjustedMValues.head.endMeasure should be (15.0)
    changeSet.adjustedMValues.head.startMeasure should be (0.0)
    changeSet.expiredAssetIds should be (Set(2,3))
  }

  test("adjust Segments with diferent direction") {
    val roadLink = RoadLink(1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, AdministrativeClass.apply(1), FunctionalClass.Unknown,
      TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    val assets = Seq(
      PersistedLinearAsset(1, 1, SideCode.AgainstDigitizing.value, Some(NumericValue(10)), 0.0, 5.0, Some("guy"),
        Some(DateTime.now()), None, None, expired = false, 180, 0, None, linkSource = NormalLinkInterface, None, None),
      PersistedLinearAsset(2, 1, SideCode.AgainstDigitizing.value, Some(NumericValue(10)), 5.0, 10.0, Some("guy"),
        Some(DateTime.now().minusDays(2)), None, None, expired = false, 180, 0, None, linkSource = NormalLinkInterface, None, None),
      PersistedLinearAsset(3, 1, SideCode.TowardsDigitizing.value, Some(NumericValue(20)), 0.0, 4.0, Some("guy"),
        Some(DateTime.now().minusDays(3)), None, None, expired = false, 180, 0, None, linkSource = NormalLinkInterface, None, None),
      PersistedLinearAsset(4, 1, SideCode.TowardsDigitizing.value, Some(NumericValue(20)), 4.0, 10.0, Some("guy"),
        Some(DateTime.now().minusDays(4)), None, None, expired = false, 180, 0, None, linkSource = NormalLinkInterface, None, None)
    )
    val (filledTopology, changeSet) = NumericalLimitFiller.fillTopology(Seq(roadLink), Map(1L -> assets), 180)
    filledTopology.length should be (2)
    filledTopology.head.startMeasure should be (0.0)
    filledTopology.head.endMeasure should be (10.00)
    filledTopology.last.startMeasure should be (0.0)
    filledTopology.last.endMeasure should be (10.00)
    changeSet.adjustedMValues.length should be (2)
    changeSet.adjustedMValues.head.endMeasure should be (10.0)
    changeSet.adjustedMValues.head.startMeasure should be (0.0)
    changeSet.adjustedMValues.last.endMeasure should be (10.0)
    changeSet.adjustedMValues.last.startMeasure should be (0.0)
  }

  test("combine segments into one") {
    val roadLink = RoadLink(1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, AdministrativeClass.apply(1), FunctionalClass.Unknown,
      TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    val assets = Seq(
      PersistedLinearAsset(1, 1, SideCode.AgainstDigitizing.value, Some(NumericValue(10)), 0.0, 10.0, Some("guy"),
        Some(DateTime.now()), None, None, expired = false, 180, 0, None, linkSource = NormalLinkInterface, None, None),
      PersistedLinearAsset(3, 1, SideCode.TowardsDigitizing.value, Some(NumericValue(10)), 0.0, 10.0, Some("guy"),
        Some(DateTime.now().minusDays(1)), None, None, expired = false, 180, 0, None, linkSource = NormalLinkInterface, None, None)
    )
    val (filledTopology, changeSet) = NumericalLimitFiller.fillTopology(Seq(roadLink), Map(1L -> assets), 180)
    filledTopology.length should be (1)
    filledTopology.head.startMeasure should be (0.0)
    filledTopology.head.endMeasure should be (10.00)
  }

  test("combine segments into one with none value") {
    val roadLink = RoadLink(1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, AdministrativeClass.apply(1), FunctionalClass.Unknown,
      TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    val assets = Seq(
      PersistedLinearAsset(1, 1, SideCode.AgainstDigitizing.value, None, 0.0, 10.0, Some("guy"),
        Some(DateTime.now()), None, None, expired = false, 180, 0, None, linkSource = NormalLinkInterface, None, None),
      PersistedLinearAsset(3, 1, SideCode.TowardsDigitizing.value, None, 0.0, 10.0, Some("guy"),
        Some(DateTime.now().minusDays(1)), None, None, expired = false, 180, 0, None, linkSource = NormalLinkInterface, None, None)
    )
    val (filledTopology, changeSet) = NumericalLimitFiller.fillTopology(Seq(roadLink), Map(1L -> assets), 180)
    filledTopology.length should be (1)
    filledTopology.head.startMeasure should be (0.0)
    filledTopology.head.endMeasure should be (10.00)
  }

  private def roadLink(linkId: Long, geometry: Seq[Point], administrativeClass: AdministrativeClass = Unknown): RoadLink = {
    val municipalityCode = "MUNICIPALITYCODE" -> BigInt(235)
    RoadLink(
      linkId, geometry, GeometryUtils.geometryLength(geometry), administrativeClass, 1,
      TrafficDirection.BothDirections, Motorway, None, None, Map(municipalityCode))
  }

  private def makeAssetsList = {
    Seq(
      (18083292,1722175,1,None,0,85.545,0,"04.05.2016 14:32:37,294105000"),
      (18083302,1722175,1,None,0,85.472,0,"04.05.2016 14:32:37,321328000"),
      (18083410,1722175,1,None,0,85.545,0,"04.05.2016 14:32:37,598152000"),
      (18083420,1722175,1,None,0,85.472,0,"04.05.2016 14:32:37,624683000"),
      (18116162,1722175,1,None,0,85.545,0,"04.05.2016 14:34:00,372695000"),
      (18116172,1722175,1,None,0,85.472,0,"04.05.2016 14:34:00,395510000"),
      (18116266,1722175,1,None,0,85.545,0,"04.05.2016 14:34:00,597215000"),
      (18116357,1722175,1,None,0,85.618,0,"04.05.2016 14:34:00,879024000"),
      (18116367,1722175,1,None,0,85.545,0,"04.05.2016 14:34:00,900251000"),
      (18088733,1722175,1,None,0,85.472,0,"04.05.2016 14:32:51,480673000"),
      (18088743,1722175,1,None,0,85.399,0,"04.05.2016 14:32:51,504387000"),
      (18088833,1722175,1,None,0,85.545,0,"04.05.2016 14:32:51,740964000"),
      (18088843,1722175,1,None,0,85.472,0,"04.05.2016 14:32:51,762947000"),
      (18088929,1722175,1,None,0,85.545,0,"04.05.2016 14:32:52,025372000"),
      (18088939,1722175,1,None,0,85.472,0,"04.05.2016 14:32:52,047985000"),
      (18100314,1722175,1,None,0,85.545,0,"04.05.2016 14:33:19,084481000"),
      (18100324,1722175,1,None,0,85.472,0,"04.05.2016 14:33:19,107717000"),
      (18100422,1722175,1,None,0,85.618,0,"04.05.2016 14:33:19,350112000"),
      (18100432,1722175,1,None,0,85.545,0,"04.05.2016 14:33:19,374252000"),
      (18104278,1722175,1,None,0,85.472,0,"04.05.2016 14:33:29,181521000"),
      (18104288,1722175,1,None,0,85.399,0,"04.05.2016 14:33:29,202715000"),
      (18108314,1722175,1,None,0,85.545,0,"04.05.2016 14:33:38,909732000"),
      (18108324,1722175,1,None,0,85.472,0,"04.05.2016 14:33:38,932204000"),
      (18108414,1722175,1,None,0,85.618,0,"04.05.2016 14:33:39,127050000"),
      (18108424,1722175,1,None,0,85.545,0,"04.05.2016 14:33:39,148784000"),
      (18108510,1722175,1,None,0,85.618,0,"04.05.2016 14:33:39,332110000"),
      (18110169,1722175,1,None,0,85.691,0,"04.05.2016 14:33:43,757209000"),
      (18110179,1722175,1,None,0,85.618,0,"04.05.2016 14:33:43,778582000"),
      (18110392,1722175,1,None,0,85.545,0,"04.05.2016 14:33:44,435067000"),
      (18110403,1722175,1,None,0,85.472,0,"04.05.2016 14:33:44,474105000"),
      (18095576,1722175,1,None,0,85.545,0,"04.05.2016 14:33:08,156219000"),
      (18095586,1722175,1,None,0,85.472,0,"04.05.2016 14:33:08,179633000"),
      (18095676,1722175,1,None,0,85.545,0,"04.05.2016 14:33:08,375868000"),
      (18095686,1722175,1,None,0,85.472,0,"04.05.2016 14:33:08,406218000"),
      (18113289,1722175,1,None,0,85.618,0,"04.05.2016 14:33:52,298744000"),
      (18113299,1722175,1,None,0,85.545,0,"04.05.2016 14:33:52,322262000"),
      (18113386,1722175,1,None,0,85.618,0,"04.05.2016 14:33:52,628802000"),
      (18113396,1722175,1,None,0,85.545,0,"04.05.2016 14:33:52,657721000"),
      (18113496,1722175,1,None,0,85.618,0,"04.05.2016 14:33:52,868395000"),
      (18113506,1722175,1,None,0,85.545,0,"04.05.2016 14:33:52,889889000"),
      (18116455,1722175,1,None,0,85.691,0,"04.05.2016 14:34:01,113740000"),
      (18116465,1722175,1,None,0,85.618,0,"04.05.2016 14:34:01,137544000"),
      (18116549,1722175,1,None,0,85.691,0,"04.05.2016 14:34:01,330175000"),
      (18116559,1722175,1,None,0,85.618,0,"04.05.2016 14:34:01,356920000"),
      (18089026,1722175,1,None,0,85.618,0,"04.05.2016 14:32:52,251967000"),
      (18089036,1722175,1,None,0,85.545,0,"04.05.2016 14:32:52,276910000"),
      (18097591,1722175,1,None,0,85.399,0,"04.05.2016 14:33:12,695338000"),
      (18104378,1722175,1,None,0,85.545,0,"04.05.2016 14:33:29,398479000"),
      (18104388,1722175,1,None,0,85.472,0,"04.05.2016 14:33:29,420256000"),
      (18104474,1722175,1,None,0,85.545,0,"04.05.2016 14:33:29,599962000"),
      (18104484,1722175,1,None,0,85.472,0,"04.05.2016 14:33:29,621285000"),
      (18104571,1722175,1,None,0,85.618,0,"04.05.2016 14:33:29,907514000"),
      (18104581,1722175,1,None,0,85.545,0,"04.05.2016 14:33:29,929864000"),
      (18108521,1722175,1,None,0,85.545,0,"04.05.2016 14:33:39,362341000"),
      (18108605,1722175,1,None,0,85.691,0,"04.05.2016 14:33:39,574920000"),
      (18108615,1722175,1,None,0,85.618,0,"04.05.2016 14:33:39,596652000"),
      (18110489,1722175,1,None,0,85.618,0,"04.05.2016 14:33:44,862098000"),
      (18110499,1722175,1,None,0,85.545,0,"04.05.2016 14:33:44,991041000"),
      (18110586,1722175,1,None,0,85.545,0,"04.05.2016 14:33:45,316189000"),
      (18110596,1722175,1,None,0,85.472,0,"04.05.2016 14:33:45,339087000"),
      (18110686,1722175,1,None,0,85.618,0,"04.05.2016 14:33:45,539095000"),
      (18110697,1722175,1,None,0,85.545,0,"04.05.2016 14:33:45,648053000"),
      (18095773,1722175,1,None,0,85.618,0,"04.05.2016 14:33:08,608873000"),
      (18095783,1722175,1,None,0,85.545,0,"04.05.2016 14:33:08,630669000"),
      (18095869,1722175,1,None,0,85.545,0,"04.05.2016 14:33:08,818292000"),
      (18095879,1722175,1,None,0,85.472,0,"04.05.2016 14:33:08,842189000"),
      (18095969,1722175,1,None,0,85.618,0,"04.05.2016 14:33:09,038384000"),
      (18095980,1722175,1,None,0,85.545,0,"04.05.2016 14:33:09,062307000"),
      (18112994,1722175,1,None,0,85.618,0,"04.05.2016 14:33:51,517163000"),
      (18113004,1722175,1,None,0,85.545,0,"04.05.2016 14:33:51,540468000"),
      (18113088,1722175,1,None,0,85.691,0,"04.05.2016 14:33:51,746267000"),
      (18113098,1722175,1,None,0,85.618,0,"04.05.2016 14:33:51,769794000"),
      (18113189,1722175,1,None,0,85.545,0,"04.05.2016 14:33:52,068287000"),
      (18113199,1722175,1,None,0,85.472,0,"04.05.2016 14:33:52,089633000"),
      (18113590,1722175,1,None,0,85.691,0,"04.05.2016 14:33:53,093850000"),
      (18113600,1722175,1,None,0,85.618,0,"04.05.2016 14:33:53,115195000"),
      (18097716,1722175,1,None,0,85.472,0,"04.05.2016 14:33:12,972516000"),
      (18097726,1722175,1,None,0,85.399,0,"04.05.2016 14:33:12,995363000"),
      (18097828,1722175,1,None,0,85.545,0,"04.05.2016 14:33:13,251951000"),
      (18097838,1722175,1,None,0,85.472,0,"04.05.2016 14:33:13,273916000"),
      (18097947,1722175,1,None,0,85.545,0,"04.05.2016 14:33:13,526303000"),
      (18097957,1722175,1,None,0,85.472,0,"04.05.2016 14:33:13,550084000"),
      (18101077,1722175,1,None,0,85.399,0,"04.05.2016 14:33:20,949363000"),
      (18104673,1722175,1,None,0,85.327,0,"04.05.2016 14:33:30,152687000"),
      (18104764,1722175,1,None,0,85.472,0,"04.05.2016 14:33:30,488304000"),
      (18104774,1722175,1,None,0,85.399,0,"04.05.2016 14:33:30,510131000"),
      (18104868,1722175,1,None,0,85.472,0,"04.05.2016 14:33:30,729555000"),
      (18093187,1722175,1,None,0,85.399,0,"04.05.2016 14:33:02,676768000"),
      (18093278,1722175,1,None,0,85.472,0,"04.05.2016 14:33:02,883259000"),
      (18093288,1722175,1,None,0,85.399,0,"04.05.2016 14:33:02,906669000"),
      (18096066,1722175,1,None,0,85.618,0,"04.05.2016 14:33:09,267470000"),
      (18096076,1722175,1,None,0,85.545,0,"04.05.2016 14:33:09,291838000"),
      (18083519,1722175,1,None,0,85.618,0,"04.05.2016 14:32:37,916210000"),
      (18083529,1722175,1,None,0,85.545,0,"04.05.2016 14:32:37,939781000"),
      (18083539,1722175,1,None,0,85.327,0,"04.05.2016 14:32:37,964926000"),
      (18083641,1722175,1,None,0,85.472,0,"04.05.2016 14:32:38,223678000"),
      (18083651,1722175,1,None,0,85.399,0,"04.05.2016 14:32:38,246850000"),
      (18104959,1722175,1,None,0,85.545,0,"04.05.2016 14:33:31,021034000"),
      (18104969,1722175,1,None,0,85.472,0,"04.05.2016 14:33:31,046063000"),
      (18105059,1722175,1,None,0,85.618,0,"04.05.2016 14:33:31,261130000"),
      (18105069,1722175,1,None,0,85.545,0,"04.05.2016 14:33:31,286566000"),
      (18105155,1722175,1,None,0,85.618,0,"04.05.2016 14:33:31,551582000"),
      (18105165,1722175,1,None,0,85.545,0,"04.05.2016 14:33:31,572779000"),
      (18083764,1722175,1,None,0,85.472,0,"04.05.2016 14:32:38,543091000"),
      (18083774,1722175,1,None,0,85.399,0,"04.05.2016 14:32:38,567032000"),
      (18083872,1722175,1,None,0,85.545,0,"04.05.2016 14:32:38,801752000"),
      (18083882,1722175,1,None,0,85.472,0,"04.05.2016 14:32:38,825025000"),
      (18083968,1722175,1,None,0,85.472,0,"04.05.2016 14:32:39,106161000"),
      (18083978,1722175,1,None,0,85.399,0,"04.05.2016 14:32:39,129966000"),
      (18070511,1722175,1,None,0,85.327,0,"04.05.2016 12:13:34,780316000"),
      (18089671,1722175,1,None,0,85.472,0,"04.05.2016 14:32:54,075251000"),
      (18089681,1722175,1,None,0,85.399,0,"04.05.2016 14:32:54,097594000"),
      (18089783,1722175,1,None,0,85.545,0,"04.05.2016 14:32:54,329385000"),
      (18089793,1722175,1,None,0,85.472,0,"04.05.2016 14:32:54,352883000"),
      (18101167,1722175,1,None,0,85.545,0,"04.05.2016 14:33:21,146233000"),
      (18101177,1722175,1,None,0,85.472,0,"04.05.2016 14:33:21,169146000"),
      (18105250,1722175,1,None,0,85.691,0,"04.05.2016 14:33:31,774098000"),
      (18105260,1722175,1,None,0,85.618,0,"04.05.2016 14:33:31,797724000"),
      (18105270,1722175,1,None,0,85.399,0,"04.05.2016 14:33:31,825349000"),
      (18084069,1722175,1,None,0,85.545,0,"04.05.2016 14:32:39,367565000"),
      (18084079,1722175,1,None,0,85.472,0,"04.05.2016 14:32:39,391009000"),
      (18084165,1722175,1,None,0,85.545,0,"04.05.2016 14:32:39,621852000"),
      (18084175,1722175,1,None,0,85.472,0,"04.05.2016 14:32:39,644690000"),
      (18084262,1722175,1,None,0,85.618,0,"04.05.2016 14:32:39,894894000"),
      (18084272,1722175,1,None,0,85.545,0,"04.05.2016 14:32:39,923043000"),
      (18070689,1722175,1,None,0,85.399,0,"04.05.2016 12:14:12,476116000"),
      (18070700,1722175,1,None,0,85.327,0,"04.05.2016 12:14:12,502899000"),
      (18079383,1722175,1,None,0,85.399,0,"04.05.2016 14:32:15,651628000"),
      (18079393,1722175,1,None,0,85.327,0,"04.05.2016 14:32:15,701297000"),
      (18079495,1722175,1,None,0,85.472,0,"04.05.2016 14:32:16,090261000"),
      (18079505,1722175,1,None,0,85.399,0,"04.05.2016 14:32:16,119956000"),
      (18079613,1722175,1,None,0,85.472,0,"04.05.2016 14:32:16,454580000"),
      (18089902,1722175,1,None,0,85.545,0,"04.05.2016 14:32:54,614898000"),
      (18089912,1722175,1,None,0,85.472,0,"04.05.2016 14:32:54,639063000"),
      (18090010,1722175,1,None,0,85.618,0,"04.05.2016 14:32:54,886514000"),
      (18090020,1722175,1,None,0,85.545,0,"04.05.2016 14:32:54,912846000"),
      (18070867,1722175,1,None,0,85.399,0,"04.05.2016 12:14:56,599938000"),
      (18101601,1722175,1,None,0,85.618,0,"04.05.2016 14:33:22,225243000"),
      (18084394,1722175,1,None,0,85.472,0,"04.05.2016 14:32:40,266093000"),
      (18084404,1722175,1,None,0,85.399,0,"04.05.2016 14:32:40,298127000"),
      (18084507,1722175,1,None,0,85.545,0,"04.05.2016 14:32:40,649121000"),
      (18084517,1722175,1,None,0,85.472,0,"04.05.2016 14:32:40,674922000"),
      (18079623,1722175,1,None,0,85.399,0,"04.05.2016 14:32:16,485462000"),
      (18079721,1722175,1,None,0,85.545,0,"04.05.2016 14:32:16,766620000"),
      (18079731,1722175,1,None,0,85.472,0,"04.05.2016 14:32:16,794372000"),
      (18081451,1722175,1,None,0,85.399,0,"04.05.2016 14:32:31,716524000"),
      (18070877,1722175,1,None,0,85.327,0,"04.05.2016 12:14:56,655809000"),
      (18070975,1722175,1,None,0,85.472,0,"04.05.2016 12:14:56,944500000"),
      (18070985,1722175,1,None,0,85.399,0,"04.05.2016 12:14:56,970281000"),
      (18101611,1722175,1,None,0,85.545,0,"04.05.2016 14:33:22,269387000"),
      (18101695,1722175,1,None,0,85.691,0,"04.05.2016 14:33:22,458014000"),
      (18101705,1722175,1,None,0,85.618,0,"04.05.2016 14:33:22,481337000"),
      (18105977,1722175,1,None,0,85.472,0,"04.05.2016 14:33:33,759059000"),
      (18105987,1722175,1,None,0,85.399,0,"04.05.2016 14:33:33,781145000"),
      (18084625,1722175,1,None,0,85.545,0,"04.05.2016 14:32:40,974522000"),
      (18084635,1722175,1,None,0,85.472,0,"04.05.2016 14:32:40,999042000"),
      (18084734,1722175,1,None,0,85.618,0,"04.05.2016 14:32:41,225169000"),
      (18084744,1722175,1,None,0,85.545,0,"04.05.2016 14:32:41,248414000"),
      (18081541,1722175,1,None,0,85.472,0,"04.05.2016 14:32:32,012561000"),
      (18081551,1722175,1,None,0,85.399,0,"04.05.2016 14:32:32,038437000"),
      (18081641,1722175,1,None,0,85.545,0,"04.05.2016 14:32:32,358911000"),
      (18081651,1722175,1,None,0,85.472,0,"04.05.2016 14:32:32,386040000"),
      (18081737,1722175,1,None,0,85.545,0,"04.05.2016 14:32:32,613244000"),
      (18081747,1722175,1,None,0,85.472,0,"04.05.2016 14:32:32,639975000"),
      (18098055,1722175,1,None,0,85.618,0,"04.05.2016 14:33:13,795694000"),
      (18098065,1722175,1,None,0,85.545,0,"04.05.2016 14:33:13,821401000"),
      (18098157,1722175,1,None,0,85.327,0,"04.05.2016 14:33:14,028900000"),
      (18098260,1722175,1,None,0,85.472,0,"04.05.2016 14:33:14,283962000"),
      (18098270,1722175,1,None,0,85.399,0,"04.05.2016 14:33:14,304989000"),
      (18106073,1722175,1,None,0,85.545,0,"04.05.2016 14:33:33,974380000"),
      (18106084,1722175,1,None,0,85.472,0,"04.05.2016 14:33:33,996270000"),
      (18106170,1722175,1,None,0,85.472,0,"04.05.2016 14:33:34,199966000"),
      (18106180,1722175,1,None,0,85.399,0,"04.05.2016 14:33:34,226580000"),
      (18106270,1722175,1,None,0,85.545,0,"04.05.2016 14:33:34,445508000"),
      (18106280,1722175,1,None,0,85.472,0,"04.05.2016 14:33:34,467275000"),
      (18093378,1722175,1,None,0,85.545,0,"04.05.2016 14:33:03,117196000"),
      (18093388,1722175,1,None,0,85.472,0,"04.05.2016 14:33:03,140517000"),
      (18093475,1722175,1,None,0,85.545,0,"04.05.2016 14:33:03,357902000"),
      (18093485,1722175,1,None,0,85.472,0,"04.05.2016 14:33:03,378845000"),
      (18114020,1722175,1,None,0,85.472,0,"04.05.2016 14:33:54,245891000"),
      (18114110,1722175,1,None,0,85.545,0,"04.05.2016 14:33:54,452251000"),
      (18114120,1722175,1,None,0,85.472,0,"04.05.2016 14:33:54,475868000"),
      (18081833,1722175,1,None,0,85.618,0,"04.05.2016 14:32:32,927915000"),
      (18081843,1722175,1,None,0,85.545,0,"04.05.2016 14:32:32,957308000"),
      (18081853,1722175,1,None,0,85.327,0,"04.05.2016 14:32:32,999162000"),
      (18081943,1722175,1,None,0,85.472,0,"04.05.2016 14:32:33,292163000"),
      (18081953,1722175,1,None,0,85.399,0,"04.05.2016 14:32:33,319084000"),
      (18082043,1722175,1,None,0,85.472,0,"04.05.2016 14:32:33,630804000"),
      (18082053,1722175,1,None,0,85.399,0,"04.05.2016 14:32:33,655887000"),
      (18102161,1722175,1,None,0,85.618,0,"04.05.2016 14:33:23,563165000"),
      (18102171,1722175,1,None,0,85.545,0,"04.05.2016 14:33:23,584388000"),
      (18102255,1722175,1,None,0,85.691,0,"04.05.2016 14:33:23,778992000"),
      (18102265,1722175,1,None,0,85.618,0,"04.05.2016 14:33:23,799149000"),
      (18102356,1722175,1,None,0,85.545,0,"04.05.2016 14:33:24,007715000"),
      (18102366,1722175,1,None,0,85.472,0,"04.05.2016 14:33:24,030391000"),
      (18106367,1722175,1,None,0,85.545,0,"04.05.2016 14:33:34,674564000"),
      (18106377,1722175,1,None,0,85.472,0,"04.05.2016 14:33:34,696060000"),
      (18106463,1722175,1,None,0,85.618,0,"04.05.2016 14:33:34,879901000"),
      (18106473,1722175,1,None,0,85.545,0,"04.05.2016 14:33:34,900071000"),
      (18106577,1722175,1,None,0,85.545,0,"04.05.2016 14:33:35,116401000"),
      (18093571,1722175,1,None,0,85.618,0,"04.05.2016 14:33:03,579189000"),
      (18093581,1722175,1,None,0,85.545,0,"04.05.2016 14:33:03,605754000"),
      (18093673,1722175,1,None,0,85.327,0,"04.05.2016 14:33:03,813211000"),
      (18093764,1722175,1,None,0,85.472,0,"04.05.2016 14:33:04,020303000"),
      (18093774,1722175,1,None,0,85.399,0,"04.05.2016 14:33:04,043755000"),
      (18079857,1722175,1,None,0,85.399,0,"04.05.2016 14:32:17,250079000"),
      (18079867,1722175,1,None,0,85.327,0,"04.05.2016 14:32:17,281743000"),
      (18079969,1722175,1,None,0,85.472,0,"04.05.2016 14:32:17,578733000"),
      (18114211,1722175,1,None,0,85.618,0,"04.05.2016 14:33:54,710199000"),
      (18114221,1722175,1,None,0,85.545,0,"04.05.2016 14:33:54,732262000"),
      (18114307,1722175,1,None,0,85.618,0,"04.05.2016 14:33:54,925503000"),
      (18114317,1722175,1,None,0,85.545,0,"04.05.2016 14:33:54,947430000"),
      (18114401,1722175,1,None,0,85.691,0,"04.05.2016 14:33:55,276289000"),
      (18114411,1722175,1,None,0,85.618,0,"04.05.2016 14:33:55,323716000"),
      (18082139,1722175,1,None,0,85.545,0,"04.05.2016 14:32:33,901783000"),
      (18082149,1722175,1,None,0,85.472,0,"04.05.2016 14:32:33,925942000"),
      (18082235,1722175,1,None,0,85.472,0,"04.05.2016 14:32:34,162466000"),
      (18082245,1722175,1,None,0,85.399,0,"04.05.2016 14:32:34,186150000"),
      (18082335,1722175,1,None,0,85.545,0,"04.05.2016 14:32:34,411441000"),
      (18082345,1722175,1,None,0,85.472,0,"04.05.2016 14:32:34,437912000"),
      (18102456,1722175,1,None,0,85.618,0,"04.05.2016 14:33:24,254441000"),
      (18102466,1722175,1,None,0,85.545,0,"04.05.2016 14:33:24,276613000"),
      (18102552,1722175,1,None,0,85.618,0,"04.05.2016 14:33:24,463176000"),
      (18102563,1722175,1,None,0,85.545,0,"04.05.2016 14:33:24,485745000"),
      (18106588,1722175,1,None,0,85.472,0,"04.05.2016 14:33:35,137792000"),
      (18106678,1722175,1,None,0,85.545,0,"04.05.2016 14:33:35,326558000"),
      (18106688,1722175,1,None,0,85.472,0,"04.05.2016 14:33:35,347897000"),
      (18106774,1722175,1,None,0,85.618,0,"04.05.2016 14:33:35,532703000"),
      (18106784,1722175,1,None,0,85.545,0,"04.05.2016 14:33:35,553014000"),
      (18093868,1722175,1,None,0,85.472,0,"04.05.2016 14:33:04,266984000"),
      (18093958,1722175,1,None,0,85.545,0,"04.05.2016 14:33:04,463525000"),
      (18093968,1722175,1,None,0,85.472,0,"04.05.2016 14:33:04,484395000"),
      (18094059,1722175,1,None,0,85.618,0,"04.05.2016 14:33:04,682422000"),
      (18094069,1722175,1,None,0,85.545,0,"04.05.2016 14:33:04,703094000"),
      (18096614,1722175,1,None,0,85.472,0,"04.05.2016 14:33:10,540650000"),
      (18096624,1722175,1,None,0,85.399,0,"04.05.2016 14:33:10,564244000"),
      (18096714,1722175,1,None,0,85.545,0,"04.05.2016 14:33:10,764299000"),
      (18096724,1722175,1,None,0,85.472,0,"04.05.2016 14:33:10,787419000"),
      (18096811,1722175,1,None,0,85.545,0,"04.05.2016 14:33:10,979280000"),
      (18096821,1722175,1,None,0,85.472,0,"04.05.2016 14:33:11,001074000"),
      (18079979,1722175,1,None,0,85.399,0,"04.05.2016 14:32:17,603588000"),
      (18080087,1722175,1,None,0,85.472,0,"04.05.2016 14:32:17,911524000"),
      (18080097,1722175,1,None,0,85.399,0,"04.05.2016 14:32:17,937023000"),
      (18080195,1722175,1,None,0,85.545,0,"04.05.2016 14:32:18,184750000"),
      (18080205,1722175,1,None,0,85.472,0,"04.05.2016 14:32:18,210897000"),
      (18114687,1722175,1,None,0,85.545,0,"04.05.2016 14:33:56,537399000"),
      (18114698,1722175,1,None,0,85.472,0,"04.05.2016 14:33:56,643952000"),
      (18082431,1722175,1,None,0,85.545,0,"04.05.2016 14:32:34,685872000"),
      (18082441,1722175,1,None,0,85.472,0,"04.05.2016 14:32:34,713688000"),
      (18082527,1722175,1,None,0,85.618,0,"04.05.2016 14:32:34,935268000"),
      (18082537,1722175,1,None,0,85.545,0,"04.05.2016 14:32:34,960216000"),
      (18087182,1722175,1,None,0,85.399,0,"04.05.2016 14:32:47,504238000"),
      (18087305,1722175,1,None,0,85.472,0,"04.05.2016 14:32:47,812275000"),
      (18098939,1722175,1,None,0,85.472,0,"04.05.2016 14:33:15,942737000"),
      (18098949,1722175,1,None,0,85.399,0,"04.05.2016 14:33:15,964876000"),
      (18099047,1722175,1,None,0,85.545,0,"04.05.2016 14:33:16,182580000"),
      (18099057,1722175,1,None,0,85.472,0,"04.05.2016 14:33:16,203972000"),
      (18099143,1722175,1,None,0,85.472,0,"04.05.2016 14:33:16,412361000"),
      (18102783,1722175,1,None,0,85.545,0,"04.05.2016 14:33:25,005860000"),
      (18102793,1722175,1,None,0,85.472,0,"04.05.2016 14:33:25,034081000"),
      (18102884,1722175,1,None,0,85.618,0,"04.05.2016 14:33:25,419122000"),
      (18102894,1722175,1,None,0,85.545,0,"04.05.2016 14:33:25,466368000"),
      (18102981,1722175,1,None,0,85.618,0,"04.05.2016 14:33:25,825351000"),
      (18106871,1722175,1,None,0,85.545,0,"04.05.2016 14:33:35,745508000"),
      (18106881,1722175,1,None,0,85.472,0,"04.05.2016 14:33:35,766844000"),
      (18106971,1722175,1,None,0,85.618,0,"04.05.2016 14:33:35,965199000"),
      (18106981,1722175,1,None,0,85.545,0,"04.05.2016 14:33:35,989117000"),
      (18107067,1722175,1,None,0,85.618,0,"04.05.2016 14:33:36,173433000"),
      (18107077,1722175,1,None,0,85.545,0,"04.05.2016 14:33:36,194265000"),
      (18108801,1722175,1,None,0,85.472,0,"04.05.2016 14:33:40,026181000"),
      (18108892,1722175,1,None,0,85.545,0,"04.05.2016 14:33:40,241452000"),
      (18108902,1722175,1,None,0,85.472,0,"04.05.2016 14:33:40,262901000"),
      (18108992,1722175,1,None,0,85.618,0,"04.05.2016 14:33:40,464929000"),
      (18109002,1722175,1,None,0,85.545,0,"04.05.2016 14:33:40,488022000"),
      (18094155,1722175,1,None,0,85.618,0,"04.05.2016 14:33:04,919671000"),
      (18094165,1722175,1,None,0,85.545,0,"04.05.2016 14:33:04,941936000"),
      (18094250,1722175,1,None,0,85.691,0,"04.05.2016 14:33:05,135597000"),
      (18094260,1722175,1,None,0,85.618,0,"04.05.2016 14:33:05,160425000"),
      (18094270,1722175,1,None,0,85.399,0,"04.05.2016 14:33:05,182583000"),
      (18096907,1722175,1,None,0,85.618,0,"04.05.2016 14:33:11,185907000"),
      (18096917,1722175,1,None,0,85.545,0,"04.05.2016 14:33:11,207026000"),
      (18096987,1722175,1,None,0,85.691,0,"04.05.2016 14:33:11,366747000"),
      (18096997,1722175,1,None,0,85.618,0,"04.05.2016 14:33:11,387635000"),
      (18097088,1722175,1,None,0,85.545,0,"04.05.2016 14:33:11,579355000"),
      (18097098,1722175,1,None,0,85.472,0,"04.05.2016 14:33:11,600741000"),
      (18114788,1722175,1,None,0,85.618,0,"04.05.2016 14:33:56,882678000"),
      (18114798,1722175,1,None,0,85.545,0,"04.05.2016 14:33:56,906679000"),
      (18114885,1722175,1,None,0,85.618,0,"04.05.2016 14:33:57,113773000"),
      (18114895,1722175,1,None,0,85.545,0,"04.05.2016 14:33:57,137318000"),
      (18114979,1722175,1,None,0,85.691,0,"04.05.2016 14:33:57,334058000"),
      (18114989,1722175,1,None,0,85.618,0,"04.05.2016 14:33:57,355262000"),
      (18082627,1722175,1,None,0,85.472,0,"04.05.2016 14:32:35,196236000"),
      (18082637,1722175,1,None,0,85.399,0,"04.05.2016 14:32:35,228841000"),
      (18082727,1722175,1,None,0,85.545,0,"04.05.2016 14:32:35,749186000"),
      (18082737,1722175,1,None,0,85.472,0,"04.05.2016 14:32:35,774433000"),
      (18082823,1722175,1,None,0,85.545,0,"04.05.2016 14:32:36,028675000"),
      (18082833,1722175,1,None,0,85.472,0,"04.05.2016 14:32:36,071091000"),
      (18087315,1722175,1,None,0,85.399,0,"04.05.2016 14:32:47,886844000"),
      (18087417,1722175,1,None,0,85.545,0,"04.05.2016 14:32:48,123558000"),
      (18087427,1722175,1,None,0,85.472,0,"04.05.2016 14:32:48,146885000"),
      (18087536,1722175,1,None,0,85.545,0,"04.05.2016 14:32:48,404613000"),
      (18087546,1722175,1,None,0,85.472,0,"04.05.2016 14:32:48,430272000"),
      (18099153,1722175,1,None,0,85.399,0,"04.05.2016 14:33:16,435911000"),
      (18099244,1722175,1,None,0,85.545,0,"04.05.2016 14:33:16,647620000"),
      (18099254,1722175,1,None,0,85.472,0,"04.05.2016 14:33:16,667992000"),
      (18099340,1722175,1,None,0,85.545,0,"04.05.2016 14:33:16,866848000"),
      (18099350,1722175,1,None,0,85.472,0,"04.05.2016 14:33:16,888438000"),
      (18102991,1722175,1,None,0,85.545,0,"04.05.2016 14:33:25,866957000"),
      (18103075,1722175,1,None,0,85.691,0,"04.05.2016 14:33:26,188614000"),
      (18103085,1722175,1,None,0,85.618,0,"04.05.2016 14:33:26,208973000"),
      (18107756,1722175,1,None,0,85.472,0,"04.05.2016 14:33:37,673559000"),
      (18107842,1722175,1,None,0,85.545,0,"04.05.2016 14:33:37,868205000"),
      (18107852,1722175,1,None,0,85.472,0,"04.05.2016 14:33:37,889336000"),
      (18107939,1722175,1,None,0,85.618,0,"04.05.2016 14:33:38,077075000"),
      (18107949,1722175,1,None,0,85.545,0,"04.05.2016 14:33:38,099742000"),
      (18109787,1722175,1,None,0,85.472,0,"04.05.2016 14:33:42,427992000"),
      (18109878,1722175,1,None,0,85.545,0,"04.05.2016 14:33:42,786610000"),
      (18109888,1722175,1,None,0,85.472,0,"04.05.2016 14:33:42,825849000"))
  }

  test("huge assets") {
    val rl = RoadLink(1722175, Seq(Point(0.0, 0.0), Point(0.0, 85.398)), 85.398, AdministrativeClass.apply(1), 1, TrafficDirection.BothDirections, LinkType.apply(1), modifiedAt = None, modifiedBy = None, attributes=Map())
    val assets = makeAssetsList
    val linearAssets = assets.map( a =>
      PersistedLinearAsset(a._1, a._2, a._3, a._4, a._5, a._6, Option("k123"), None, Option("k345"), Option(DateTime.parse(a._8.dropRight(6), Asset.DateTimePropertyFormatMs)), expired=false, 100, a._7, None, linkSource = NormalLinkInterface, None, None)
    )
    val (outputAssets, changeSet) = NumericalLimitFiller.fillTopology(Seq(rl), linearAssets.groupBy(_.linkId), 110)
    changeSet.adjustedMValues.size == 1 should be (true)
    outputAssets.size should be (1)
    changeSet.expiredAssetIds should have size 317
    outputAssets.head.id should be (18116559L)
  }
  case class ChangeInfo(oldId: Option[Long],
                        newId: Option[Long],
                        mmlId: Long,
                        changeType: Int,
                        oldStartMeasure: Option[Double],
                        oldEndMeasure: Option[Double],
                        newStartMeasure: Option[Double],
                        newEndMeasure: Option[Double],
                        vvhTimeStamp: Option[Long])
}
