package fi.liikennevirasto.digiroad2.roadaddress.oracle

import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.util.Track
import org.scalatest.{FunSuite, Matchers}

class RoadAddressDAOSpec extends FunSuite with Matchers {
  test("LRM calculation on Road Address") {
    val towards = RoadAddress(1L, 1L, 1L, Track.RightSide, 5, 100, 110, None, None, 1L, 123L, 1.5, 11.4, SideCode.TowardsDigitizing, false, Seq())
    val against = RoadAddress(1L, 1L, 1L, Track.RightSide, 5, 100, 110, None, None, 1L, 123L, 1.5, 11.4, SideCode.AgainstDigitizing, false, Seq())
    towards.addressMValueToLRM(100L) should be (1.5 +- .001)
    against.addressMValueToLRM(100L) should be (11.4 +- .001)
    towards.addressMValueToLRM(110L) should be (11.4 +- .001)
    against.addressMValueToLRM(110L) should be (1.5 +- .001)
    towards.addressMValueToLRM(103L) should be (4.470 +- .001)
    against.addressMValueToLRM(103L) should be (8.430 +- .001)
    towards.addressMValueToLRM(99L).isNaN should be (true)
    towards.addressMValueToLRM(111L).isNaN should be (true)
    against.addressMValueToLRM(99L).isNaN should be (true)
    against.addressMValueToLRM(111L).isNaN should be (true)
  }
}
