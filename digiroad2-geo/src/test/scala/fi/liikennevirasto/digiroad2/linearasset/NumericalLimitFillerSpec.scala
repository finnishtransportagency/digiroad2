package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, TowardsDigitizing, BothDirections}
import fi.liikennevirasto.digiroad2.asset.{SideCode, Motorway, TrafficDirection, Municipality}
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.{ChangeSet, SideCodeAdjustment}
import org.scalatest._

class NumericalLimitFillerSpec extends FunSuite with Matchers {
  test("create non-existent linear assets on empty road links") {
    val topology = Seq(
      VVHRoadLinkWithProperties(1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
        1, TrafficDirection.BothDirections, Motorway, None, None))
    val linearAssets = Map.empty[Long, Seq[PersistedLinearAsset]]
    val (filledTopology, _) = NumericalLimitFiller.fillTopology(topology, linearAssets, 30)
    filledTopology should have size 1
    filledTopology.map(_.sideCode) should be(Seq(BothDirections))
    filledTopology.map(_.value) should be(Seq(None))
    filledTopology.map(_.id) should be(Seq(0))
    filledTopology.map(_.mmlId) should be(Seq(1))
    filledTopology.map(_.geometry) should be(Seq(Seq(Point(0.0, 0.0), Point(10.0, 0.0))))
  }

  test("drop assets that fall completely outside topology") {
    val topology = Seq(
      VVHRoadLinkWithProperties(1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
        1, TrafficDirection.BothDirections, Motorway, None, None))
    val linearAssets = Map(
      1l -> Seq(PersistedLinearAsset(1l, 1l, 1, Some(NumericValue(1)), 10.0, 15.0, None, None, None, None, false, 110)))

    val (filledTopology, changeSet) = NumericalLimitFiller.fillTopology(topology, linearAssets, 110)

    filledTopology should have size 1
    filledTopology.map(_.sideCode) should be(Seq(BothDirections))
    filledTopology.map(_.value) should be(Seq(None))
    filledTopology.map(_.id) should be(Seq(0))
    filledTopology.map(_.mmlId) should be(Seq(1))
    filledTopology.map(_.geometry) should be(Seq(Seq(Point(0.0, 0.0), Point(10.0, 0.0))))

    changeSet.droppedAssetIds should be(Set(1l))
  }

  test("transform one-sided asset to two-sided when its defined on one-way road link") {
    val topology = Seq(
      VVHRoadLinkWithProperties(1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
        1, TrafficDirection.TowardsDigitizing, Motorway, None, None),
      VVHRoadLinkWithProperties(2, Seq(Point(10.0, 0.0), Point(20.0, 0.0)), 10.0, Municipality,
        1, TrafficDirection.TowardsDigitizing, Motorway, None, None)
    )
    val linearAssets = Map(
      1l -> Seq(PersistedLinearAsset(1l, 1l, 2, Some(NumericValue(1)), 0.0, 10.0, None, None, None, None, false, 110)),
      2l -> Seq(
        PersistedLinearAsset(2l, 2l, 2, Some(NumericValue(1)), 0.0, 5.0, None, None, None, None, false, 110),
        PersistedLinearAsset(3l, 2l, 3, Some(NumericValue(1)), 7.0, 10.0, None, None, None, None, false, 110),
        PersistedLinearAsset(4l, 2l, SideCode.BothDirections.value, Some(NumericValue(1)), 5.0, 7.0, None, None, None, None, false, 110)
      )
    )

    val (filledTopology, changeSet) = NumericalLimitFiller.fillTopology(topology, linearAssets, 110)

    filledTopology should have size 4

    filledTopology.filter(_.id == 1l).map(_.sideCode) should be(Seq(BothDirections))
    filledTopology.filter(_.id == 1l).map(_.mmlId) should be(Seq(1l))

    filledTopology.filter(_.id == 2l).map(_.sideCode) should be(Seq(BothDirections))
    filledTopology.filter(_.id == 2l).map(_.mmlId) should be(Seq(2l))

    filledTopology.filter(_.id == 3l).map(_.sideCode) should be(Seq(BothDirections))
    filledTopology.filter(_.id == 3l).map(_.mmlId) should be(Seq(2l))

    filledTopology.filter(_.id == 4l).map(_.sideCode) should be(Seq(BothDirections))
    filledTopology.filter(_.id == 4l).map(_.mmlId) should be(Seq(2l))

    changeSet.adjustedSideCodes should be(Seq(
      SideCodeAdjustment(1l, SideCode.BothDirections),
      SideCodeAdjustment(2l, SideCode.BothDirections),
      SideCodeAdjustment(3l, SideCode.BothDirections)))
  }

  test("generate one-sided asset when two-way road link is half-covered") {
    val topology = Seq(
      VVHRoadLinkWithProperties(1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
        1, TrafficDirection.BothDirections, Motorway, None, None))
    val linearAssets = Map(
      1l -> Seq(PersistedLinearAsset(1l, 1l, 2, Some(NumericValue(1)), 0.0, 10.0, None, None, None, None, false, 110)))

    val (filledTopology, changeSet) = NumericalLimitFiller.fillTopology(topology, linearAssets, 110)

    filledTopology should have size 2
    filledTopology.filter(_.id == 1).map(_.sideCode) should be(Seq(TowardsDigitizing))
    filledTopology.filter(_.id == 1).map(_.value) should be(Seq(Some(NumericValue(1))))
    filledTopology.filter(_.id == 1).map(_.geometry) should be(Seq(Seq(Point(0.0, 0.0), Point(10.0, 0.0))))

    filledTopology.filter(_.id == 0).map(_.sideCode) should be(Seq(AgainstDigitizing))
    filledTopology.filter(_.id == 0).map(_.value) should be(Seq(None))
    filledTopology.filter(_.id == 0).map(_.geometry) should be(Seq(Seq(Point(0.0, 0.0), Point(10.0, 0.0))))

    changeSet should be(ChangeSet(Set.empty[Long], Nil, Nil))
  }
}
