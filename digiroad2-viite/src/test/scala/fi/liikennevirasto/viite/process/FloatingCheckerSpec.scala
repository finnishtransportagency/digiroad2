package fi.liikennevirasto.viite.process

import fi.liikennevirasto.digiroad2.{GeometryUtils, Point, RoadLinkService}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.viite.RoadType
import fi.liikennevirasto.viite.dao.{Discontinuity, RoadAddress, TerminationCode}
import org.joda.time.DateTime
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSuite, Matchers}


class FloatingCheckerSpec  extends FunSuite with Matchers{
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val floatingChecker = new FloatingChecker(roadLinkService = mockRoadLinkService)

  test("Geometry subtracted by more than 1 meter should trigger floating check") {
    val geometry = Seq(Point(0.0, 0.0), Point(60.0, 0.0), Point(60.0, 9.844))
    val roadAddressSeq = Seq(
      RoadAddress(1L, 12L, 1, RoadType.Unknown, Track.RightSide, Discontinuity.Continuous, 5L, 60L, None, None, None, 1L, 123, 0.0, 54.948,
        SideCode.TowardsDigitizing, 0L, (None, None), false, GeometryUtils.truncateGeometry2D(geometry, 0.0, 54.948), LinkGeomSource.NormalLinkInterface, 8),
      RoadAddress(2L, 12L, 1, RoadType.Unknown, Track.RightSide, Discontinuity.Continuous, 60L, 75L, None, None, None, 1L, 123, 54.948, 69.844,
        SideCode.TowardsDigitizing, 0L, (None, None), false, GeometryUtils.truncateGeometry2D(geometry, 54.948, 69.844), LinkGeomSource.NormalLinkInterface, 8)
    )
    val link = RoadLink(12L, geometry, 69.844, State, 1, TrafficDirection.TowardsDigitizing, Motorway, None, None,
      Map(), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    floatingChecker.isGeometryChange(link, roadAddressSeq) should be (false)

    val truncatedGeomLink = link.copy(
      geometry = GeometryUtils.truncateGeometry2D(geometry, 1.01, 69.844), length = 68.834)

    floatingChecker.isGeometryChange(truncatedGeomLink, roadAddressSeq) should be (true)

    val additionalGeomLink = link.copy(
      geometry = geometry ++ Seq(Point(60.0, 10.854)), length = 70.854)

    floatingChecker.isGeometryChange(additionalGeomLink, roadAddressSeq) should be (true)

    val subtractedGeom = link.copy(
      geometry = Seq(Point(0.0, 0.0), Point(59.5, -1.1), Point(59.5, 9.844)), length = 70.454)

    floatingChecker.isGeometryChange(subtractedGeom, roadAddressSeq) should be (true)
  }

  test("Geometry subtracted by less than 1 meter should not trigger floating check") {
    val geometry = Seq(Point(0.0, 0.0), Point(60.0, 0.0), Point(60.0, 9.844))
    val roadAddressSeq = Seq(
      RoadAddress(1L, 12L, 1, RoadType.Unknown, Track.RightSide, Discontinuity.Continuous, 5L, 60L, None, None, None, 1L, 123, 0.0, 54.948,
        SideCode.TowardsDigitizing, 0L, (None, None), false, GeometryUtils.truncateGeometry2D(geometry, 0.0, 54.948), LinkGeomSource.NormalLinkInterface, 8),
      RoadAddress(2L, 12L, 1, RoadType.Unknown, Track.RightSide, Discontinuity.Continuous, 60L, 75L, None, None, None, 1L, 123, 54.948, 69.844,
        SideCode.TowardsDigitizing, 0L, (None, None), false, GeometryUtils.truncateGeometry2D(geometry, 54.948, 69.844), LinkGeomSource.NormalLinkInterface, 8)
    )
    val link = RoadLink(12L, geometry, 69.844, State, 1, TrafficDirection.TowardsDigitizing, Motorway, None, None,
      Map(), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
    floatingChecker.isGeometryChange(link, roadAddressSeq) should be (false)

    floatingChecker.isGeometryChange(link.copy(
      geometry = GeometryUtils.truncateGeometry2D(geometry, 0.99, 69.844), length = 68.944), roadAddressSeq) should be (false)
    floatingChecker.isGeometryChange(link.copy(
      geometry = geometry ++ Seq(Point(60.0, 10.834)), length = 70.834), roadAddressSeq) should be (false)
  }

  test("Automatic Merged road addresses that also overlap with the road link should not trigger a floating check") {
    val roadLinkGeometry = List(Point(206744.672,7035045.556,2.25800000000163), Point(206738.659,7035055.284,2.0500000000029104),
      Point(206731.52,7035065.245,1.745999999999185), Point(206721.382,7035065.09,1.7299999999959255), Point(206720.428,7035057.438,1.7550000000046566),
      Point(206725.112,7035050.874,1.6440000000002328), Point(206735.678,7035047.871,1.7489999999961583), Point(206744.672,7035045.556,2.25800000000163))

    val roadAddressSeq = Seq(RoadAddress(411362, 12819, 2, RoadType.PublicRoad, Track.Combined, Discontinuity.Continuous,10 ,92 ,
      Some(DateTime.parse("2015-08-25T00:00:00.000+03:00")), None, Some("Automatic_merged"), 70411395, 5515411 ,0.0 ,69.87700000000001 ,
      SideCode.TowardsDigitizing, 1476392565000L, (None,None), false, roadLinkGeometry ,LinkGeomSource.NormalLinkInterface,4,TerminationCode.NoTermination))

    val link = RoadLink(6474047L, roadLinkGeometry, 69.87700000000001, State, 1, TrafficDirection.TowardsDigitizing, Motorway, None, None,
      Map(), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)

    floatingChecker.isGeometryChange(link, roadAddressSeq) should be (false)
  }
}
