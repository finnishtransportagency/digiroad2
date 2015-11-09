package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.{Motorway, TrafficDirection, Municipality, BoundingRectangle}
import fi.liikennevirasto.digiroad2.linearasset.VVHRoadLinkWithProperties
import fi.liikennevirasto.digiroad2.pointasset.oracle.OraclePointAssetDao
import fi.liikennevirasto.digiroad2.util.TestTransactions
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, FunSuite}

class PointServiceSpec extends FunSuite with Matchers {
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  when(mockRoadLinkService.getRoadLinksFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn(Seq(
    VVHRoadLinkWithProperties(
      388553074, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None)))

  object Service extends PointAssetOperations {
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: OraclePointAssetDao = OraclePointAssetDao
    override def withDynTransaction[T](f: => T): T = f
    override def typeId: Int = 200
  }
  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback(Service.dataSource)(test)

  test("Can fetch by bounding box") {
    runWithRollback {
      val result = Service.getByBoundingBox(BoundingRectangle(Point(0.0, 0.0), Point(1.0, 1.0))).head

      result.id should equal(600029)
      result.mmlId should equal(388553074)
      result.lon should equal(374467)
      result.lat should equal(6677347)
      result.mValue should equal(103)
    }

  }
}
