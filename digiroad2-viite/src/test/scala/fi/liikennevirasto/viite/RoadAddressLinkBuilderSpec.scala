package fi.liikennevirasto.viite

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.viite.dao.Discontinuity.Discontinuous
import fi.liikennevirasto.viite.dao.{CalibrationPoint, RoadAddress}
import fi.liikennevirasto.viite.process.InvalidAddressDataException
import org.joda.time.DateTime
import org.scalatest.{Assertions, FunSuite, Matchers}

class RoadAddressLinkBuilderSpec extends FunSuite with Matchers{

  test("Fuse road address should accept single road address") {
    val roadAddress = Seq(RoadAddress(1, 1, 1, Track.Combined, Discontinuous, 0L, 10L, None, None, 12345L, 0.0, 9.8, SideCode.TowardsDigitizing, (None, None), false,
      Seq(Point(0.0,0.0), Point(0.0,9.8))))
    RoadAddressLinkBuilder.fuseRoadAddress(roadAddress) should be (roadAddress)
  }

  test("Fuse road address should merge consecutive road addresses even if floating") {
    OracleDatabase.withDynSession {
      val roadAddress = Seq(
        RoadAddress(1, 1, 1, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), None, 12345L, 0.0, 9.8, SideCode.TowardsDigitizing, (None, None), true,
          Seq(Point(0.0, 0.0), Point(0.0, 9.8))),
        RoadAddress(2, 1, 1, Track.Combined, Discontinuous, 10L, 20L, Some(DateTime.parse("1901-01-01")), None, 12345L, 0.0, 10.4, SideCode.TowardsDigitizing, (None, None), true,
          Seq(Point(0.0, 9.8), Point(0.0, 20.2)))
      )
      RoadAddressLinkBuilder.fuseRoadAddress(roadAddress) should have size (1)
    }
  }

  test("Fuse road address should not merge consecutive road addresses with differing start dates") {
    OracleDatabase.withDynSession {
      val roadAddress = Seq(
        RoadAddress(1, 1, 1, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), None, 12345L, 0.0, 9.8, SideCode.TowardsDigitizing, (None, None), false,
          Seq(Point(0.0, 0.0), Point(0.0, 9.8))),
        RoadAddress(2, 1, 1, Track.Combined, Discontinuous, 10L, 20L, Some(DateTime.parse("1902-02-02")), None, 12345L, 9.8, 20.2, SideCode.TowardsDigitizing, (None, None), false,
          Seq(Point(0.0, 9.8), Point(0.0, 20.2)))
      )
      RoadAddressLinkBuilder.fuseRoadAddress(roadAddress) should have size (2)
    }
  }

  test("Fuse road address should not merge consecutive road addresses with differing link ids") {
    OracleDatabase.withDynSession {
      val roadAddress = Seq(
        RoadAddress(1, 1, 1, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), None, 12345L, 0.0, 9.8, SideCode.TowardsDigitizing, (None, None), false,
          Seq(Point(0.0, 0.0), Point(0.0, 9.8))),
        RoadAddress(2, 1, 1, Track.Combined, Discontinuous, 10L, 20L, Some(DateTime.parse("1901-01-01")), None, 12346L, 0.0, 10.4, SideCode.TowardsDigitizing, (None, None), false,
          Seq(Point(0.0, 9.8), Point(0.0, 20.2)))
      )
      RoadAddressLinkBuilder.fuseRoadAddress(roadAddress) should have size (1)
    }
  }

  test("Fuse road address should not merge consecutive road addresses if sidecode differs") {
    OracleDatabase.withDynSession {
      val roadAddress = Seq(
        RoadAddress(1, 1, 1, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), None, 12345L, 0.0, 9.8, SideCode.TowardsDigitizing, (None, None), false,
          Seq(Point(0.0, 0.0), Point(0.0, 9.8))),
        RoadAddress(2, 1, 1, Track.Combined, Discontinuous, 10L, 20L, Some(DateTime.parse("1901-01-01")), None, 12345L, 0.0, 10.4, SideCode.TowardsDigitizing, (None, None), false,
          Seq(Point(0.0, 9.8), Point(0.0, 20.2)))
      )
      intercept[InvalidAddressDataException] {
        RoadAddressLinkBuilder.fuseRoadAddress(roadAddress)
      }
    }
  }

  test("Fuse road address should merge multiple road addresses with random ordering") {
    OracleDatabase.withDynSession {
      val roadAddress = Seq(
        RoadAddress(1, 1, 1, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), None, 12345L, 0.0, 9.8, SideCode.TowardsDigitizing, (None, None), false,
          Seq(Point(0.0, 0.0), Point(0.0, 9.8))),
        RoadAddress(4, 1, 1, Track.Combined, Discontinuous, 30L, 40L, Some(DateTime.parse("1901-01-01")), None, 12345L, 30.0, 39.8, SideCode.TowardsDigitizing, (None, None), false,
          Seq(Point(0.0, 30.0), Point(0.0, 39.8))),
        RoadAddress(3, 1, 1, Track.Combined, Discontinuous, 20L, 30L, Some(DateTime.parse("1901-01-01")), None, 12345L, 10.4, 30.0, SideCode.TowardsDigitizing, (None, None), false,
          Seq(Point(0.0, 20.2), Point(0.0, 30.0))),
        RoadAddress(2, 1, 1, Track.Combined, Discontinuous, 10L, 20L, Some(DateTime.parse("1901-01-01")), None, 12345L, 0.0, 10.4, SideCode.TowardsDigitizing, (None, None), false,
          Seq(Point(0.0, 9.8), Point(0.0, 20.2)))
      )
      RoadAddressLinkBuilder.fuseRoadAddress(roadAddress) should have size (1)
    }
  }

  test("Fuse road address should not merge consecutive road addresses with calibration point in between") {
    // TODO: Or do we throw an exception then?
    OracleDatabase.withDynSession {
      val roadAddress = Seq(
        RoadAddress(1, 1, 1, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), None, 12345L, 0.0, 9.8, SideCode.TowardsDigitizing, (None, Some(CalibrationPoint(12345L, 9.8, 10L))), false,
          Seq(Point(0.0, 0.0), Point(0.0, 9.8))),
        RoadAddress(4, 1, 1, Track.Combined, Discontinuous, 30L, 40L, Some(DateTime.parse("1901-01-01")), None, 12345L, 30.0, 39.8, SideCode.TowardsDigitizing, (None, None), false,
          Seq(Point(0.0, 30.0), Point(0.0, 39.8))),
        RoadAddress(3, 1, 1, Track.Combined, Discontinuous, 20L, 30L, Some(DateTime.parse("1901-01-01")), None, 12345L, 10.4, 30.0, SideCode.TowardsDigitizing, (None, None), false,
          Seq(Point(0.0, 20.2), Point(0.0, 30.0))),
        RoadAddress(2, 1, 1, Track.Combined, Discontinuous, 10L, 20L, Some(DateTime.parse("1901-01-01")), None, 12345L, 0.0, 10.4, SideCode.TowardsDigitizing, (Some(CalibrationPoint(12345L, 9.8, 10L)), None), false,
          Seq(Point(0.0, 9.8), Point(0.0, 20.2)))
      )
      RoadAddressLinkBuilder.fuseRoadAddress(roadAddress) should have size (3)
    }
  }

}
