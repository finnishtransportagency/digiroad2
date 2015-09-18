package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.{ChangeSet, MValueAdjustment}
import fi.liikennevirasto.digiroad2.linearasset.{PersistedLinearAsset, NumericalLimitFiller, VVHRoadLinkWithProperties}
import fi.liikennevirasto.digiroad2.linearasset.oracle.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.util.TestTransactions
import org.mockito.Mockito._
import org.mockito.Matchers._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class LinearAssetServiceSpec extends FunSuite with Matchers {
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  when(mockRoadLinkService.fetchVVHRoadlink(388562360l)).thenReturn(Some(VVHRoadlink(388562360l, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockRoadLinkService.fetchVVHRoadlinks(any[Set[Long]])).thenReturn(Seq(VVHRoadlink(388562360l, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockRoadLinkService.getRoadLinksFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn(Seq(
      VVHRoadLinkWithProperties(
        1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
        1, TrafficDirection.BothDirections, Motorway, None, None)))

  val mockLinearAssetDao = MockitoSugar.mock[OracleLinearAssetDao]
  when(mockLinearAssetDao.fetchLinearAssetsByMmlIds(30, Seq(1), "mittarajoitus"))
    .thenReturn(Seq(PersistedLinearAsset(1, 1, 1, Some(40000), 0.4, 9.6, None, None, None, None, false)))

  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]

  object PassThroughService extends LinearAssetOperations {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: OracleLinearAssetDao = mockLinearAssetDao
    override def eventBus: DigiroadEventBus = mockEventBus
  }

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback(PassThroughService.dataSource)(test)

  test("Expire numerical limit") {
    runWithRollback {
      PassThroughService.update(Seq(11111l), None, true, "lol", (_) => Unit)
      val limit = PassThroughService.getById(11111)
      limit.get.value should be (Some(4000))
      limit.get.expired should be (true)
    }
  }

  test("Update numerical limit") {
    runWithRollback {
      PassThroughService.update(Seq(11111l), Some(2000), false, "lol", (_) => Unit)
      val limit = PassThroughService.getById(11111)
      limit.get.value should be (Some(2000))
      limit.get.expired should be (false)
    }
  }

  test("create non-existent linear assets on empty road links") {
    val topology = Seq(
      VVHRoadLinkWithProperties(1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
        1, TrafficDirection.BothDirections, Motorway, None, None))
    val linearAssets = Map.empty[Long, Seq[PersistedLinearAsset]]
    val (filledTopology, _) = NumericalLimitFiller.fillTopology(topology, linearAssets, 30)
    filledTopology should have size 1
    filledTopology.map(_.sideCode) should be(Seq(1))
    filledTopology.map(_.value) should be(Seq(None))
    filledTopology.map(_.id) should be(Seq(0))
    filledTopology.map(_.mmlId) should be(Seq(1))
    filledTopology.map(_.points) should be(Seq(Seq(Point(0.0, 0.0), Point(10.0, 0.0))))
  }

  test("adjust linear asset to cover whole link when the difference in asset length and link length is less than maximum allowed error") {
    val linearAssets = PassThroughService.getByBoundingBox(30, BoundingRectangle(Point(0.0, 0.0), Point(1.0, 1.0)))
    linearAssets should have size 1
    linearAssets.map(_.points) should be(Seq(Seq(Point(0.0, 0.0), Point(10.0, 0.0))))
    linearAssets.map(_.mmlId) should be(Seq(1))
    linearAssets.map(_.value) should be(Seq(Some(40000)))
    verify(mockEventBus, times(1))
      .publish("linearAssets:update", ChangeSet(Set.empty[Long], Seq(MValueAdjustment(1, 1, 0.0, 10.0)), Nil))
  }
}
