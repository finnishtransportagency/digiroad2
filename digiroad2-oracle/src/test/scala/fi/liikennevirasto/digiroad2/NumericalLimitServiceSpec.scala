package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.{TrafficDirection, Municipality, AdministrativeClass}
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, FunSuite}
import org.mockito.Mockito._
import slick.driver.JdbcDriver.backend.Database
import slick.driver.JdbcDriver.backend.Database.dynamicSession

class NumericalLimitServiceSpec extends FunSuite with Matchers {
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  when(mockRoadLinkService.fetchVVHRoadlink(388562360l)).thenReturn(Some(VVHRoadlink(388562360l, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))

  object PassThroughService extends NumericalLimitOperations {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
  }

  def runWithCleanup(test: => Unit): Unit = {
    Database.forDataSource(PassThroughService.dataSource).withDynTransaction {
      test
      dynamicSession.rollback()
    }
  }

  test("Expire numerical limit") {
    runWithCleanup {
      PassThroughService.updateNumericalLimit(11111, None, true, "lol")
      val limit = PassThroughService.getById(11111)
      limit.get.value should be (Some(4000))
      limit.get.expired should be (true)
    }
  }

  test("Update numerical limit") {
    runWithCleanup {
      PassThroughService.updateNumericalLimit(11111, Some(2000), false, "lol")
      val limit = PassThroughService.getById(11111)
      limit.get.value should be (Some(2000))
      limit.get.expired should be (false)
    }
  }

  test("calculate end points of one link speed limit") {
    val links = List((Point(373028.812006694, 6678475.44858997), Point(373044.204553789, 6678442.81292882)))
    PassThroughService.calculateEndPoints(links) shouldBe Set(Point(373028.812006694, 6678475.44858997),
      Point(373044.204553789, 6678442.81292882))
  }

  test("calculate end points of two link speed limit") {
    val links = List((Point(374134.233471419, 6677240.50731189), Point(374120.876216048, 6677240.61213817)), (Point(374120.876216048, 6677240.61213817), Point(374083.159979821, 6677239.66865146)))
    PassThroughService.calculateEndPoints(links) shouldBe Set(Point(374134.233471419, 6677240.50731189),
      Point(374083.159979821, 6677239.66865146))
  }

  test("calculate end points of two link speed limit - order shouldn't matter") {
    val links = List((Point(374134.233471419, 6677240.50731189), Point(374120.876216048, 6677240.61213817)), (Point(374120.876216048, 6677240.61213817), Point(374083.159979821, 6677239.66865146)))
    PassThroughService.calculateEndPoints(links.reverse) shouldBe Set(Point(374134.233471419, 6677240.50731189),
      Point(374083.159979821, 6677239.66865146))
  }

  test("calculate end points of three link speed limit") {
    val links = List((Point(372564.918268001, 6678035.95699387), Point(372450.464234144, 6678051.64592463)),
      (Point(372572.589549587, 6678017.88260562), Point(372564.91838001, 6678035.95670311)),
      (Point(372573.640063694, 6678008.0175942), Point(372572.589549587, 6678017.88260562)))

    PassThroughService.calculateEndPoints(links) shouldBe Set(Point(372573.640063694, 6678008.0175942),
      Point(372450.464234144, 6678051.64592463))
  }

  test("calculate end points of speed limit where end points are not first points of their respective links") {
    val links = List((Point(1.0, 0.0), Point(0.0, 0.0)),
      (Point(1.0, 0.0), Point(2.0, 0.0)))

    PassThroughService.calculateEndPoints(links) shouldBe Set(Point(0.0, 0.0), Point(2.0, 0.0))
  }
}
