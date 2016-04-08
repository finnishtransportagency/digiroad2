package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.GeometryUtils.Projection
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, TowardsDigitizing, BothDirections}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.{MValueAdjustment, ChangeSet, SideCodeAdjustment}
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

  test("drop assets that fall completely outside topology") {
    val topology = Seq(
      RoadLink(1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
        1, TrafficDirection.BothDirections, Motorway, None, None))
    val linearAssets = Map(
      1l -> Seq(PersistedLinearAsset(1l, 1l, 1, Some(NumericValue(1)), 10.0, 15.0, None, None, None, None, false, 110, 0, None)))

    val (filledTopology, changeSet) = NumericalLimitFiller.fillTopology(topology, linearAssets, 110)

    filledTopology should have size 1
    filledTopology.map(_.sideCode) should be(Seq(BothDirections))
    filledTopology.map(_.value) should be(Seq(None))
    filledTopology.map(_.id) should be(Seq(0))
    filledTopology.map(_.linkId) should be(Seq(1))
    filledTopology.map(_.geometry) should be(Seq(Seq(Point(0.0, 0.0), Point(10.0, 0.0))))

    changeSet.droppedAssetIds should be(Set(1l))
  }

  test("cap assets that go over roadlink geometry") {
    val topology = Seq(
      RoadLink(1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
        1, TrafficDirection.BothDirections, Motorway, None, None))
    val linearAssets = Map(
      1l -> Seq(PersistedLinearAsset(1l, 1l, 1, Some(NumericValue(1)), 0.0, 15.0, None, None, None, None, false, 110, 0, None)))

    val (filledTopology, changeSet) = NumericalLimitFiller.fillTopology(topology, linearAssets, 110)

    filledTopology should have size 1
    filledTopology.map(_.sideCode) should be(Seq(BothDirections))
    filledTopology.map(_.value) should be(Seq(Some(NumericValue(1))))
    filledTopology.map(_.id) should be(Seq(1))
    filledTopology.map(_.linkId) should be(Seq(1))
    filledTopology.map(_.geometry) should be(Seq(Seq(Point(0.0, 0.0), Point(10.0, 0.0))))

    changeSet.droppedAssetIds should be(Set())
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
      1l -> Seq(PersistedLinearAsset(1l, 1l, 2, Some(NumericValue(1)), 0.0, 10.0, None, None, None, None, false, 110, 0, None)),
      2l -> Seq(
        PersistedLinearAsset(2l, 2l, 2, Some(NumericValue(1)), 0.0, 5.0, None, None, None, None, false, 110, 0, None),
        PersistedLinearAsset(3l, 2l, 3, Some(NumericValue(1)), 7.0, 10.0, None, None, None, None, false, 110, 0, None),
        PersistedLinearAsset(4l, 2l, SideCode.BothDirections.value, Some(NumericValue(1)), 5.0, 7.0, None, None, None, None, false, 110, 0, None)
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
      1l -> Seq(PersistedLinearAsset(1l, 1l, 2, Some(NumericValue(1)), 0.0, 10.0, None, None, None, None, false, 110, 0, None)))

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
      None, None, None, expired = false, 100, 0, None)
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
        None, None, None, expired = false, 110, 0, None),
        PersistedLinearAsset(2, 1, SideCode.AgainstDigitizing.value, None, 0.0, 10.0, Some("guy"),
        None, None, None, expired = false, 110, 0, None)
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
        None, None, None, expired = false, 130, 0, None)
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

  test("project mass transit lanes to new geometry") {
    val oldRoadLink = roadLink(1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)))
    val newLink1 = roadLink(1, Seq(Point(0.0, 0.0), Point(3.0, 0.0)))
    val newLink2 = roadLink(2, Seq(Point(0.0, 0.0), Point(4.0, 0.0)))
    val newLink3 = roadLink(3, Seq(Point(0.0, 0.0), Point(3.0, 0.0)))
    val linkmap = Map(1L -> newLink1, 2L -> newLink2, 3L -> newLink3)
    val assets = Seq(
      PersistedLinearAsset(1, 1, SideCode.TowardsDigitizing.value, None, 1.0, 10.0, Some("guy"),
        None, None, None, expired = false, 160, 0, None),
      PersistedLinearAsset(2, 1, SideCode.AgainstDigitizing.value, None, 0.0, 9.0, Some("guy"),
        None, None, None, expired = false, 160, 0, None)
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

  private def roadLink(linkId: Long, geometry: Seq[Point], administrativeClass: AdministrativeClass = Unknown): RoadLink = {
    val municipalityCode = "MUNICIPALITYCODE" -> BigInt(235)
    RoadLink(
      linkId, geometry, GeometryUtils.geometryLength(geometry), administrativeClass, 1,
      TrafficDirection.BothDirections, Motorway, None, None, Map(municipalityCode))
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
