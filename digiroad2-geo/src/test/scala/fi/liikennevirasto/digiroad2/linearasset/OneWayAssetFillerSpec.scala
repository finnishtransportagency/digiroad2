package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, BothDirections, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.{ChangeSet, SideCodeAdjustment}
import org.scalatest.{FunSuite, Matchers}


class OneWayAssetFillerSpec extends FunSuite with Matchers {

  object oneWayAssetFiller extends OneWayAssetFiller

  test("transform one-sided asset to two-sided when its defined on one-way road link") {
    val topology = Seq(
      RoadLink(1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
        1, TrafficDirection.TowardsDigitizing, Motorway, None, None),
      RoadLink(2, Seq(Point(10.0, 0.0), Point(20.0, 0.0)), 10.0, Municipality,
        1, TrafficDirection.TowardsDigitizing, Motorway, None, None)
    )
    val linearAssets = Map(
      1l -> Seq(PersistedLinearAsset(1l, 1l, 2, Some(NumericValue(1)), 0.0, 10.0, None, None, None, None, false, 110, 0, None, linkSource = NormalLinkInterface, None, None, None)),
      2l -> Seq(
        PersistedLinearAsset(2l, 2l, 2, Some(NumericValue(1)), 0.0, 5.0, None, None, None, None, false, 110, 0, None, linkSource = NormalLinkInterface, None, None, None),
        PersistedLinearAsset(3l, 2l, 2, Some(NumericValue(1)), 7.0, 10.0, None, None, None, None, false, 110, 0, None, linkSource = NormalLinkInterface, None, None, None),
        PersistedLinearAsset(4l, 2l, SideCode.BothDirections.value, Some(NumericValue(1)), 5.0, 7.0, None, None, None, None, false, 110, 0, None, linkSource = NormalLinkInterface, None, None, None)
      )
    )

    val (filledTopology, changeSet) = oneWayAssetFiller.fillTopology(topology, linearAssets, 110)

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
      SideCodeAdjustment(1l, SideCode.BothDirections, PavedRoad.typeId),
      SideCodeAdjustment(2l, SideCode.BothDirections, PavedRoad.typeId),
      SideCodeAdjustment(3l, SideCode.BothDirections, PavedRoad.typeId)))
  }

  test("generate one-sided asset when two-way road link is half-covered 1") {
    val topology = Seq(
      RoadLink(1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
        1, TrafficDirection.BothDirections, Motorway, None, None))
    val linearAssets = Map(
      1l -> Seq(PersistedLinearAsset(1l, 1l, 2, Some(NumericValue(1)), 0.0, 10.0, None, None, None, None, false, 110, 0, None, linkSource = NormalLinkInterface, None, None, None)))

    val (filledTopology, changeSet) = oneWayAssetFiller.fillTopology(topology, linearAssets, 110)

    filledTopology should have size 2
    filledTopology.filter(_.id == 1).map(_.sideCode) should be(Seq(TowardsDigitizing))
    filledTopology.filter(_.id == 1).map(_.value) should be(Seq(Some(NumericValue(1))))
    filledTopology.filter(_.id == 1).map(_.geometry) should be(Seq(Seq(Point(0.0, 0.0), Point(10.0, 0.0))))

    filledTopology.filter(_.id == 0).map(_.sideCode) should be(Seq(AgainstDigitizing))
    filledTopology.filter(_.id == 0).map(_.value) should be(Seq(None))
    filledTopology.filter(_.id == 0).map(_.geometry) should be(Seq(Seq(Point(0.0, 0.0), Point(10.0, 0.0))))

    changeSet should be(ChangeSet(Set.empty[Long], Nil, Nil, Nil, Set.empty[Long], Nil))
  }
}