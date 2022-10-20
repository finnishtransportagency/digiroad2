package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, BothDirections, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.{ChangeSet, MValueAdjustment, SideCodeAdjustment, VVHChangesAdjustment, ValueAdjustment}
import org.scalatest.{FunSuite, Matchers}

import java.util.UUID
import scala.util.Random


class OneWayAssetFillerSpec extends FunSuite with Matchers {

  object oneWayAssetFiller extends OneWayAssetFiller

  private def generateRandomLinkId(): String = s"${UUID.randomUUID()}:${Random.nextInt(100)}"

  test("transform one-sided asset to two-sided when its defined on one-way road link") {
    val linkId1 = generateRandomLinkId()
    val linkId2 = generateRandomLinkId()
    val topology = Seq(
      RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
        1, TrafficDirection.TowardsDigitizing, Motorway, None, None),
      RoadLink(linkId2, Seq(Point(10.0, 0.0), Point(20.0, 0.0)), 10.0, Municipality,
        1, TrafficDirection.TowardsDigitizing, Motorway, None, None)
    )
    val linearAssets = Map(
      linkId1 -> Seq(PersistedLinearAsset(1l, linkId1, 2, Some(NumericValue(1)), 0.0, 10.0, None, None, None, None, false, 110, 0, None, linkSource = NormalLinkInterface, None, None, None)),
      linkId2 -> Seq(
        PersistedLinearAsset(2l, linkId2, 2, Some(NumericValue(1)), 0.0, 5.0, None, None, None, None, false, 110, 0, None, linkSource = NormalLinkInterface, None, None, None),
        PersistedLinearAsset(3l, linkId2, 2, Some(NumericValue(1)), 7.0, 10.0, None, None, None, None, false, 110, 0, None, linkSource = NormalLinkInterface, None, None, None),
        PersistedLinearAsset(4l, linkId2, SideCode.BothDirections.value, Some(NumericValue(1)), 5.0, 7.0, None, None, None, None, false, 110, 0, None, linkSource = NormalLinkInterface, None, None, None)
      )
    )

    val (filledTopology, changeSet) = oneWayAssetFiller.fillTopology(topology, linearAssets, 110)

    filledTopology should have size 4

    filledTopology.filter(_.id == 1l).map(_.sideCode) should be(Seq(BothDirections))
    filledTopology.filter(_.id == 1l).map(_.linkId) should be(Seq(linkId1))

    filledTopology.filter(_.id == 2l).map(_.sideCode) should be(Seq(BothDirections))
    filledTopology.filter(_.id == 2l).map(_.linkId) should be(Seq(linkId2))

    filledTopology.filter(_.id == 3l).map(_.sideCode) should be(Seq(BothDirections))
    filledTopology.filter(_.id == 3l).map(_.linkId) should be(Seq(linkId2))

    filledTopology.filter(_.id == 4l).map(_.sideCode) should be(Seq(BothDirections))
    filledTopology.filter(_.id == 4l).map(_.linkId) should be(Seq(linkId2))

    changeSet.adjustedSideCodes should be(Seq(
      SideCodeAdjustment(1l, SideCode.BothDirections, PavedRoad.typeId),
      SideCodeAdjustment(2l, SideCode.BothDirections, PavedRoad.typeId),
      SideCodeAdjustment(3l, SideCode.BothDirections, PavedRoad.typeId)))
  }

  test("generate one-sided asset when two-way road link only has asset on the other side") {
    val linkId = generateRandomLinkId()
    val topology = Seq(
      RoadLink(linkId, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
        1, TrafficDirection.BothDirections, Motorway, None, None))
    val linearAssets = Map(
      linkId -> Seq(PersistedLinearAsset(1l, linkId, TowardsDigitizing.value, Some(NumericValue(1)), 0.0, 10.0, None, None, None, None, false, 110, 0, None, linkSource = NormalLinkInterface, None, None, None)))

    val changeSet = ChangeSet( droppedAssetIds = Set.empty[Long],
      expiredAssetIds = Set.empty[Long],
      adjustedMValues = Seq.empty[MValueAdjustment],
      adjustedVVHChanges = Seq.empty[VVHChangesAdjustment],
      adjustedSideCodes = Seq.empty[SideCodeAdjustment],
      valueAdjustments = Seq.empty[ValueAdjustment])

    val adjustedAssets = oneWayAssetFiller.adjustAssets(topology, changeSet, linearAssets, 110)._1
    val filledTopology = oneWayAssetFiller.toLinearAsset(adjustedAssets, topology.head)

    filledTopology should have size 2
    filledTopology.filter(_.id == 1).map(_.sideCode) should be(Seq(TowardsDigitizing))
    filledTopology.filter(_.id == 1).map(_.value) should be(Seq(Some(NumericValue(1))))
    filledTopology.filter(_.id == 1).map(_.geometry) should be(Seq(Seq(Point(0.0, 0.0), Point(10.0, 0.0))))

    filledTopology.filter(_.id == 0).map(_.sideCode) should be(Seq(AgainstDigitizing))
    filledTopology.filter(_.id == 0).map(_.value) should be(Seq(None))
    filledTopology.filter(_.id == 0).map(_.geometry) should be(Seq(Seq(Point(0.0, 0.0), Point(10.0, 0.0))))
  }
}