package fi.liikennevirasto.digiroad2.linearasset.oracle

import scala.language.implicitConversions

import org.scalatest.FunSuite
import org.scalatest.Matchers
import org.scalatest.Tag

import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import fi.liikennevirasto.digiroad2.linearasset.Point
import fi.liikennevirasto.digiroad2.asset.{Point => AssetPoint}

class OracleLinearAssetProviderSpec extends FunSuite with Matchers {
  val provider = new OracleLinearAssetProvider()

  test("load speed limits with spatial bounds", Tag("db")) {
    val speedLimits = provider.getSpeedLimits(BoundingRectangle(AssetPoint(374700, 6677595), AssetPoint(374750, 6677560)))
    speedLimits.size shouldBe 4
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
}
