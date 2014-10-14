package fi.liikennevirasto.digiroad2.linearasset.oracle

import fi.liikennevirasto.digiroad2.{DummyEventBus, Point}

import scala.language.implicitConversions

import org.scalatest.FunSuite
import org.scalatest.Matchers
import org.scalatest.Tag

import fi.liikennevirasto.digiroad2.asset.BoundingRectangle

class OracleLinearAssetProviderSpec extends FunSuite with Matchers {
  val provider = new OracleLinearAssetProvider(new DummyEventBus)

  test("load speed limits with spatial bounds", Tag("db")) {
    val speedLimits = provider.getSpeedLimits(BoundingRectangle(Point(374700, 6677595), Point(374750, 6677560)))
    speedLimits.size shouldBe 4
  }

  test("get speed limit endpoints by id", Tag("db")) {
    val speedLimit = provider.getSpeedLimit(700160)
    speedLimit.get.endpoints shouldBe (Set(Point(372573.640063694,6678008.0175942),
                                           Point(372450.464234144,6678051.64592463)))
  }

  test("calculate end points of one link speed limit") {
    val links = List((Point(373028.812006694,6678475.44858997), Point(373044.204553789,6678442.81292882)))
    provider.calculateSpeedLimitEndPoints(links) shouldBe(Set(Point(373028.812006694,6678475.44858997),
                                                              Point(373044.204553789,6678442.81292882)))
  }

  test("calculate end points of two link speed limit") {
    val links = List((Point(374134.233471419,6677240.50731189), Point(374120.876216048,6677240.61213817)), (Point(374120.876216048,6677240.61213817), Point(374083.159979821,6677239.66865146)))
    provider.calculateSpeedLimitEndPoints(links) shouldBe (Set(Point(374134.233471419,6677240.50731189),
                                                               Point(374083.159979821,6677239.66865146)))
  }

  test("calculate end points of two link speed limit - order shouldn't matter") {
    val links = List((Point(374134.233471419, 6677240.50731189), Point(374120.876216048, 6677240.61213817)), (Point(374120.876216048, 6677240.61213817), Point(374083.159979821, 6677239.66865146)))
    provider.calculateSpeedLimitEndPoints(links.reverse) shouldBe (Set(Point(374134.233471419, 6677240.50731189),
                                                                       Point(374083.159979821, 6677239.66865146)))
  }

  test("calculate end points of three link speed limit") {
    val links = List((Point(372564.918268001,6678035.95699387), Point(372450.464234144,6678051.64592463)),
                     (Point(372572.589549587,6678017.88260562), Point(372564.91838001,6678035.95670311)),
                     (Point(372573.640063694,6678008.0175942), Point(372572.589549587,6678017.88260562)))

    provider.calculateSpeedLimitEndPoints(links) shouldBe (Set(Point(372573.640063694,6678008.0175942),
                                                               Point(372450.464234144,6678051.64592463)))
  }
}
