package fi.liikennevirasto.viite.process

import fi.liikennevirasto.digiroad2.{GeometryUtils, Point, RoadLinkService}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.viite.RoadType
import fi.liikennevirasto.viite.dao.{Discontinuity, RoadAddress}
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

    floatingChecker.isGeometryChange(link.copy(
      geometry = GeometryUtils.truncateGeometry2D(geometry, 1.01, 69.844), length = 68.834), roadAddressSeq) should be (true)
    floatingChecker.isGeometryChange(link.copy(
      geometry = geometry ++ Seq(Point(60.0, 10.854)), length = 70.854), roadAddressSeq) should be (true)
    floatingChecker.isGeometryChange(link.copy(
      geometry = Seq(Point(0.0, 0.0), Point(59.5, -1.1), Point(59.5, 9.844)), length = 70.454), roadAddressSeq) should be (true)
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
}
