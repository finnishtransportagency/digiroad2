package fi.liikennevirasto.viite.process

import fi.liikennevirasto.digiroad2.{ChangeInfo, Point}
import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.viite.dao.{Discontinuity, RoadAddress}
import org.joda.time.DateTime
import org.scalatest.{FunSuite, Matchers}


class RoadAddressChangeInfoMapperSpec extends FunSuite with Matchers {
  test("resolve") {
    val roadAddress = RoadAddress(1, 1, 1, Track.RightSide, Discontinuity.Continuous, 0, 1000, Some(DateTime.now), None,
      None, 0L, 123L, 0.0, 1000.234, SideCode.AgainstDigitizing, 86400L, (None, None), false, Seq(Point(0.0, 0.0), Point(1000.234, 0.0)))
    val roadAddress2 = RoadAddress(1, 1, 1, Track.RightSide, Discontinuity.Continuous, 1000, 1400, Some(DateTime.now), None,
      None, 0L, 124L, 0.0, 399.648, SideCode.AgainstDigitizing, 86400L, (None, None), false, Seq(Point(1000.234, 0.0), Point(1000.234, 399.648)))
    val map = Seq(roadAddress, roadAddress2).groupBy(_.linkId)
    val changes = Seq(
      ChangeInfo(Some(123), Some(124), 123L, 2, Some(0.0), Some(1000.234), Some(399.648), Some(1399.882), 96400L),
      ChangeInfo(Some(124), Some(124), 123L, 1, Some(0.0), Some(399.648), Some(0.0), Some(399.648), 96400L))
    val results = RoadAddressChangeInfoMapper.resolveChangesToMap(map, Seq(), changes)
    results.get(123).isEmpty should be (true)
    results.get(124).isEmpty should be (false)
    results(124).size should be (2)
  }
}
