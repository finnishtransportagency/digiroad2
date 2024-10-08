  package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.service.{RoadAddressForLink, RoadAddressService}
import fi.liikennevirasto.digiroad2.{Point, Track}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class GeometryTransformSpec extends FunSuite with Matchers {
  val mockRoadAddressService = MockitoSugar.mock[RoadAddressService]
  val transform = new GeometryTransform(mockRoadAddressService)

  test("Resolve location on left when asset SideCode different than AgainstDigitizing value") {
    val linkId = LinkIdGenerator.generateRandom()
    val mValue = 60
    val sideCode = 1

    val dummyRoadAddress = Some(RoadAddressForLink(1, 921, 2, Track.Combined, 0, 299, None, None, linkId, 10, 298.694, SideCode.TowardsDigitizing, Seq(Point(4002, 3067), Point(385258.765,7300119.103)), false, None, None, None))

    when(mockRoadAddressService.getByLrmPosition(any[String], any[Double])).thenReturn(dummyRoadAddress)

    val (roadAddress, roadSide) =
      transform.resolveAddressAndLocation(Point(0,0), 0, mValue, linkId, sideCode)

    roadAddress.road should be(921)
    roadAddress.track should be(Track.Combined)
    roadAddress.addrM should be(247)
    roadSide should be(RoadSide.Left)
  }

  test("Resolve location on left when asset SideCode equals to TowardsDigitizing value") {
    val linkId = LinkIdGenerator.generateRandom()
    val mValue = 60
    val sideCode = 3

    val dummyRoadAddress = Some(RoadAddressForLink(1, 921, 2, Track.Combined, 0, 299, None, None, linkId, 10, 298.694, SideCode.TowardsDigitizing, Seq(Point(4002, 3067), Point(385258.765,7300119.103)), false, None, None, None))

    when(mockRoadAddressService.getByLrmPosition(any[String], any[Double])).thenReturn(dummyRoadAddress)

    val (roadAddress, roadSide) =
      transform.resolveAddressAndLocation(Point(0,0), 0, mValue, linkId, sideCode)

    roadAddress.road should be(921)
    roadAddress.track should be(Track.Combined)
    roadAddress.addrM should be(51)
    roadSide should be(RoadSide.Left)

  }

  test("Resolve location on right when asset SideCode different than AgainstDigitizing value") {
    val linkId = LinkIdGenerator.generateRandom()
    val mValue = 60
    val sideCode = 1

    val dummyRoadAddress = Some(RoadAddressForLink(1, 921, 2, Track.RightSide, 0, 299, None, None, linkId, 0, 298.694, SideCode.BothDirections, Seq(Point(4002, 3067), Point(385258.765,7300119.103)), false, None, None, None))

    when(mockRoadAddressService.getByLrmPosition(any[String], any[Double])).thenReturn(dummyRoadAddress)

    val (roadAddress, roadSide) = transform.resolveAddressAndLocation(Point(0,0), 0, mValue, linkId, sideCode)
    roadAddress.road should be(921)
    roadAddress.track should be(Track.RightSide)
    roadAddress.addrM should be(238)
    roadSide should be(RoadSide.Right)
  }

  test("Resolve location on right when asset SideCode equals to TowardsDigitizing value") {
    val linkId = LinkIdGenerator.generateRandom()
    val mValue = 60
    val sideCode = 2

    val dummyRoadAddress = Some(RoadAddressForLink(1, 921, 2, Track.RightSide, 0, 299, None, None, linkId, 0, 298.694, SideCode.TowardsDigitizing, Seq(Point(4002, 3067), Point(385258.765,7300119.103)), false, None, None, None))

    when(mockRoadAddressService.getByLrmPosition(any[String], any[Double])).thenReturn(dummyRoadAddress)

    val (roadAddress, roadSide) = transform.resolveAddressAndLocation(Point(0,0), 0, mValue, linkId, sideCode)
    roadAddress.road should be(921)
    roadAddress.track should be(Track.RightSide)
    roadAddress.addrM should be(60)
    roadSide should be(RoadSide.Right)
  }

  test("Resolve location on two-way road") {
    val linkId = LinkIdGenerator.generateRandom()
    val mValue = 11
    val sideCode = 0

    val dummyRoadAddress = Some(RoadAddressForLink(1, 110, 2, Track.Combined, 0, 160, None, None, linkId, 1, 150.690, SideCode.TowardsDigitizing, Seq(Point(4002, 3067), Point(385258.765,7300119.103)), false, None, None, None))

    when(mockRoadAddressService.getByLrmPosition(any[String], any[Double])).thenReturn(dummyRoadAddress)

    val (roadAddress, roadSide) = transform.resolveAddressAndLocation(Point(0,0), 0, mValue, linkId, sideCode, road = Option(110))
    roadAddress.road should be(110)
    roadAddress.track should be(Track.Combined)
    roadSide should be(RoadSide.Left)
  }
}
