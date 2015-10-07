package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.SideCode.BothDirections
import fi.liikennevirasto.digiroad2.asset.{Motorway, TrafficDirection, Municipality}
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
      1l -> Seq(PersistedLinearAsset(1l, 1l, 1, Some(1), 10.0, 15.0, None, None, None, None, false, 110)))

    val (filledTopology, changeSet) = NumericalLimitFiller.fillTopology(topology, linearAssets, 110)

    filledTopology should have size 1
    filledTopology.map(_.sideCode) should be(Seq(BothDirections))
    filledTopology.map(_.value) should be(Seq(None))
    filledTopology.map(_.id) should be(Seq(0))
    filledTopology.map(_.mmlId) should be(Seq(1))
    filledTopology.map(_.geometry) should be(Seq(Seq(Point(0.0, 0.0), Point(10.0, 0.0))))

    changeSet.droppedAssetIds should be(Set(1l))
  }
}
