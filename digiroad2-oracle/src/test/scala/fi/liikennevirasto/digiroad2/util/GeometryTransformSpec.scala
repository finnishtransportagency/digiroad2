  package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.SideCode
import org.scalatest.{FunSuite, Matchers}
import fi.liikennevirasto.digiroad2.dao.{RoadAddress => ViiteRoadAddress}
import fi.liikennevirasto.digiroad2.service.RoadAddressesService
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar

class GeometryTransformSpec extends FunSuite with Matchers {
  val mockRoadAddressesService = MockitoSugar.mock[RoadAddressesService]
  val transform = new GeometryTransform(mockRoadAddressesService)

  test("Resolve location on left when asset SideCode different than AgainstDigitizing value") {
    val linkId = 1641830
    val mValue = 60
    val sideCode = 1

    val dummyRoadAddress = Some(ViiteRoadAddress(1, 921, 2, Track.Combined, 0, 299, None, None, 1641830, 10, 298.694, SideCode.TowardsDigitizing, false, Seq(Point(4002, 3067), Point(385258.765,7300119.103)), false, None, None, None))

    when(mockRoadAddressesService.getByLrmPosition(any[Long], any[Double])).thenReturn(dummyRoadAddress)

    val (roadAddress, roadSide) =
      transform.resolveAddressAndLocation(Point(0,0), 0, mValue, linkId, sideCode)

    roadAddress.road should be(921)
    roadAddress.track should be(Track.Combined)
    roadAddress.addrM should be(247)
    roadSide should be(RoadSide.Left)
  }

  test("Resolve location on left when asset SideCode equals to TowardsDigitizing value") {
    val linkId = 1641830
    val mValue = 60
    val sideCode = 3

    val dummyRoadAddress = Some(ViiteRoadAddress(1, 921, 2, Track.Combined, 0, 299, None, None, 1641830, 10, 298.694, SideCode.TowardsDigitizing, false, Seq(Point(4002, 3067), Point(385258.765,7300119.103)), false, None, None, None))

    when(mockRoadAddressesService.getByLrmPosition(any[Long], any[Double])).thenReturn(dummyRoadAddress)

    val (roadAddress, roadSide) =
      transform.resolveAddressAndLocation(Point(0,0), 0, mValue, linkId, sideCode)

    roadAddress.road should be(921)
    roadAddress.track should be(Track.Combined)
    roadAddress.addrM should be(51)
    roadSide should be(RoadSide.Left)

  }

  test("Resolve location on right when asset SideCode different than AgainstDigitizing value") {
    val linkId = 1641830
    val mValue = 60
    val sideCode = 1

    val dummyRoadAddress = Some(ViiteRoadAddress(1, 921, 2, Track.RightSide, 0, 299, None, None, 1641830, 0, 298.694, SideCode.BothDirections, false, Seq(Point(4002, 3067), Point(385258.765,7300119.103)), false, None, None, None))

    when(mockRoadAddressesService.getByLrmPosition(any[Long], any[Double])).thenReturn(dummyRoadAddress)

    val (roadAddress, roadSide) = transform.resolveAddressAndLocation(Point(0,0), 0, mValue, linkId, sideCode)
    roadAddress.road should be(921)
    roadAddress.track should be(Track.RightSide)
    roadAddress.addrM should be(238)
    roadSide should be(RoadSide.Right)
  }

  test("Resolve location on right when asset SideCode equals to TowardsDigitizing value") {
    val linkId = 1641830
    val mValue = 60
    val sideCode = 2

    val dummyRoadAddress = Some(ViiteRoadAddress(1, 921, 2, Track.RightSide, 0, 299, None, None, 1641830, 0, 298.694, SideCode.TowardsDigitizing, false, Seq(Point(4002, 3067), Point(385258.765,7300119.103)), false, None, None, None))

    when(mockRoadAddressesService.getByLrmPosition(any[Long], any[Double])).thenReturn(dummyRoadAddress)

    val (roadAddress, roadSide) = transform.resolveAddressAndLocation(Point(0,0), 0, mValue, linkId, sideCode)
    roadAddress.road should be(921)
    roadAddress.track should be(Track.RightSide)
    roadAddress.addrM should be(60)
    roadSide should be(RoadSide.Right)
  }

  test("Resolve location on two-way road") {
    val linkId = 1641830
    val mValue = 11
    val sideCode = 0

    val dummyRoadAddress = Some(ViiteRoadAddress(1, 110, 2, Track.Combined, 0, 160, None, None, 1641830, 1, 150.690, SideCode.TowardsDigitizing, false, Seq(Point(4002, 3067), Point(385258.765,7300119.103)), false, None, None, None))

    when(mockRoadAddressesService.getByLrmPosition(any[Long], any[Double])).thenReturn(dummyRoadAddress)

    val (roadAddress, roadSide) = transform.resolveAddressAndLocation(Point(0,0), 0, mValue, linkId, sideCode, road = Option(110))
    roadAddress.road should be(110)
    roadAddress.track should be(Track.Combined)
    roadSide should be(RoadSide.Left)
  }
}
