package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.SideCodeAdjustment
import org.scalatest.{FunSuite, Matchers}

import java.util.UUID
import scala.util.Random


class OneWayAssetFillerSpec extends FunSuite with Matchers {

  object oneWayAssetFiller extends OneWayAssetFiller

  private def generateRandomLinkId(): String = s"${UUID.randomUUID()}:${Random.nextInt(100)}"

  test("do not transform one-sided asset to two-sided when its defined on one-way road link") {
    val linkId1 = generateRandomLinkId()
    val linkId2 = generateRandomLinkId()
    val topology = Seq(
      RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
        1, TrafficDirection.TowardsDigitizing, Motorway, None, None),
      RoadLink(linkId2, Seq(Point(10.0, 0.0), Point(20.0, 0.0)), 10.0, Municipality,
        1, TrafficDirection.TowardsDigitizing, Motorway, None, None)
    )
    val linearAssets = Map(
      linkId1 -> oneWayAssetFiller.toLinearAsset(Seq(PersistedLinearAsset(1l, linkId1, SideCode.TowardsDigitizing.value, Some(NumericValue(1)), 0.0, 10.0,
        None, None, None, None, false, 110, 0, None, linkSource = NormalLinkInterface, None, None, None)), topology.map(oneWayAssetFiller.toRoadLinkForFillTopology).head),
      linkId2 -> oneWayAssetFiller.toLinearAsset(Seq(
        PersistedLinearAsset(2l, linkId2, SideCode.TowardsDigitizing.value, Some(NumericValue(1)), 0.0, 5.0, None, None, None, None, false, 110, 0, None, linkSource = NormalLinkInterface, None, None, None),
        PersistedLinearAsset(3l, linkId2, SideCode.TowardsDigitizing.value, Some(NumericValue(1)), 7.0, 10.0, None, None, None, None, false, 110, 0, None, linkSource = NormalLinkInterface, None, None, None),
        PersistedLinearAsset(4l, linkId2, SideCode.BothDirections.value, Some(NumericValue(1)), 5.0, 7.0, None, None, None, None, false, 110, 0, None, linkSource = NormalLinkInterface, None, None, None)
      ), topology.map(oneWayAssetFiller.toRoadLinkForFillTopology).last)
    )

    val (filledTopology, changeSet) = oneWayAssetFiller.fillTopology(topology.map(oneWayAssetFiller.toRoadLinkForFillTopology), linearAssets, 110)

    filledTopology should have size 4

    filledTopology.filter(_.id == 1l).map(_.sideCode) should be(Seq(TowardsDigitizing))
    filledTopology.filter(_.id == 1l).map(_.linkId) should be(Seq(linkId1))

    filledTopology.filter(_.id == 2l).map(_.sideCode) should be(Seq(TowardsDigitizing))
    filledTopology.filter(_.id == 2l).map(_.linkId) should be(Seq(linkId2))

    filledTopology.filter(_.id == 3l).map(_.sideCode) should be(Seq(TowardsDigitizing))
    filledTopology.filter(_.id == 3l).map(_.linkId) should be(Seq(linkId2))

    filledTopology.filter(_.id == 4l).map(_.sideCode) should be(Seq(TowardsDigitizing))
    filledTopology.filter(_.id == 4l).map(_.linkId) should be(Seq(linkId2))

    changeSet.adjustedSideCodes should be(Seq(SideCodeAdjustment(4, TowardsDigitizing, 110)))
  }

  test("generate one-sided asset when two-way road link only has asset on the other side") {
    val linkId = generateRandomLinkId()
    val topology = Seq(
      RoadLink(linkId, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
        1, TrafficDirection.BothDirections, Motorway, None, None))
    val linearAssets = Map(
      linkId -> oneWayAssetFiller.toLinearAsset(Seq(PersistedLinearAsset(1l, linkId, TowardsDigitizing.value, Some(NumericValue(1)),
        0.0, 10.0, None, None, None, None, false, 110, 0, None, linkSource = NormalLinkInterface, None, None, None)), topology.map(oneWayAssetFiller.toRoadLinkForFillTopology).head))

    val changeSet = LinearAssetFiller.emptyChangeSet

    val filledTopology = oneWayAssetFiller.generateUnknowns(topology.map(oneWayAssetFiller.toRoadLinkForFillTopology), linearAssets, 110)._1

    filledTopology should have size 2
    filledTopology.filter(_.id == 1).map(_.sideCode) should be(Seq(TowardsDigitizing))
    filledTopology.filter(_.id == 1).map(_.value) should be(Seq(Some(NumericValue(1))))
    filledTopology.filter(_.id == 1).map(_.geometry) should be(Seq(Seq(Point(0.0, 0.0), Point(10.0, 0.0))))

    filledTopology.filter(_.id == 0).map(_.sideCode) should be(Seq(AgainstDigitizing))
    filledTopology.filter(_.id == 0).map(_.value) should be(Seq(None))
    filledTopology.filter(_.id == 0).map(_.geometry) should be(Seq(Seq(Point(0.0, 0.0), Point(10.0, 0.0))))
  }
}