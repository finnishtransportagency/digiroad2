package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.VVHRoadLinkWithProperties
import fi.liikennevirasto.digiroad2.util.TestTransactions
import org.joda.time.DateTime
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, FunSuite}
import org.mockito.Mockito._
import slick.driver.JdbcDriver.backend.Database
import slick.driver.JdbcDriver.backend.Database.dynamicSession

class LinearAssetServiceSpec extends FunSuite with Matchers {
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  when(mockRoadLinkService.fetchVVHRoadlink(388562360l)).thenReturn(Some(VVHRoadlink(388562360l, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockRoadLinkService.fetchVVHRoadlinks(235)).thenReturn(Seq(VVHRoadlink(388562360l, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))

  object PassThroughService extends LinearAssetOperations {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
  }

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback(PassThroughService.dataSource)(test)

  test("Expire numerical limit") {
    runWithRollback {
      PassThroughService.update(11111, None, true, "lol")
      val limit = PassThroughService.getById(11111)
      limit.get.value should be (Some(4000))
      limit.get.expired should be (true)
    }
  }

  test("Update numerical limit") {
    runWithRollback {
      PassThroughService.update(11111, Some(2000), false, "lol")
      val limit = PassThroughService.getById(11111)
      limit.get.value should be (Some(2000))
      limit.get.expired should be (false)
    }
  }

  test("get limits by municipality") {
    runWithRollback {
      val (limits, _): (Seq[PersistedLinearAsset], Map[Long, Seq[Point]]) = PassThroughService.getByMunicipality(30, 235)
      limits.length should be (2)
      Set(limits(0).id, limits(1).id) should be (Set(11111, 11112))
    }
  }

  test("create non-existent linear assets on empty road links") {
    val topology = Seq(
      VVHRoadLinkWithProperties(1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality, 1,
        TrafficDirection.BothDirections, Motorway, None, None))
    val linearAssets = Map.empty[Long, Seq[PersistedLinearAsset]]
    val filledTopology = LinearAssetFiller.fillTopology(topology, linearAssets, 30)
    filledTopology should have size 1
    filledTopology.map(_.sideCode) should be(Seq(1))
    filledTopology.map(_.value) should be(Seq(None))
    filledTopology.map(_.id) should be(Seq(0))
    filledTopology.map(_.mmlId) should be(Seq(1))
    filledTopology.map(_.points) should be(Seq(Seq(Point(0.0, 0.0), Point(10.0, 0.0))))
  }
}
