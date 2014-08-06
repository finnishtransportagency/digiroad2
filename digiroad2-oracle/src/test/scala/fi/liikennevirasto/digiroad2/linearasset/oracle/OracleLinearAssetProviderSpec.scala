package fi.liikennevirasto.digiroad2.linearasset.oracle

import org.scalatest._
import scala.util.Random
import fi.liikennevirasto.digiroad2.util.SqlScriptRunner._
import fi.liikennevirasto.digiroad2.asset.AssetStatus._
import org.joda.time.LocalDate
import fi.liikennevirasto.digiroad2.user.oracle.OracleUserProvider
import scala.language.implicitConversions
import fi.liikennevirasto.digiroad2.user.Role
import fi.liikennevirasto.digiroad2.util.DataFixture.TestAssetId
import java.sql.SQLIntegrityConstraintViolationException
import fi.liikennevirasto.digiroad2.{DummyEventBus, DigiroadEventBus}
import org.mockito.Mockito.verify
import fi.liikennevirasto.digiroad2.asset._
import scala.Some
import fi.liikennevirasto.digiroad2.user.User
import fi.liikennevirasto.digiroad2.user.Configuration
import scala.slick.jdbc.{StaticQuery => Q}

class OracleLinearAssetProviderSpec extends FunSuite with Matchers {
  val provider = new OracleLinearAssetProvider()

  test("load speed limits with spatial bounds", Tag("db")) {
    val speedLimits = provider.getSpeedLimits(BoundingRectangle(Point(374700, 6677595), Point(374750, 6677560)))
    speedLimits.size shouldBe 4
  }
}
