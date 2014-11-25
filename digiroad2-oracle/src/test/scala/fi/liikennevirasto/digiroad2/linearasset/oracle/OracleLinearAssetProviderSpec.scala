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
    val speedLimits = provider.getSpeedLimits(BoundingRectangle(Point(374700, 6677595), Point(374750, 6677560)), municipalities = Set())
    speedLimits.size shouldBe 4
  }

  test("get speed limit endpoints by id", Tag("db")) {
    val speedLimit = provider.getSpeedLimit(200114)
    speedLimit.get.endpoints shouldBe Set(Point(372573.6401, 6678008.0168),
                                          Point(372450.464262317, 6678051.64513878))
  }
}
