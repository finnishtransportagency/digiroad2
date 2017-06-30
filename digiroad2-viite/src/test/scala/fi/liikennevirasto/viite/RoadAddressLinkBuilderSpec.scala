package fi.liikennevirasto.viite

import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import fi.liikennevirasto.digiroad2.asset.{LinkGeomSource, SideCode}
import fi.liikennevirasto.digiroad2.asset.TrafficDirection.TowardsDigitizing
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.viite.dao.Discontinuity.Discontinuous
import fi.liikennevirasto.viite.dao.{CalibrationPoint, Discontinuity, RoadAddress}
import fi.liikennevirasto.viite.process.InvalidAddressDataException
import org.joda.time.DateTime
import org.scalatest.{Assertions, FunSuite, Matchers}

class RoadAddressLinkBuilderSpec extends FunSuite with Matchers{

  test("Fuse road address should accept single road address") {
    val roadAddress = Seq(RoadAddress(1, 1, 1, Track.Combined, Discontinuous, 0L, 10L, None, None, Option("tester"), 0, 12345L, 0.0, 9.8, SideCode.TowardsDigitizing, 0, (None, None), false,
      Seq(Point(0.0,0.0), Point(0.0,9.8)), LinkGeomSource.NormalLinkInterface))
    RoadAddressLinkBuilder.fuseRoadAddress(roadAddress) should be (roadAddress)
  }

  test("Fuse road address should merge consecutive road addresses even if floating") {
    OracleDatabase.withDynSession {
      val roadAddress = Seq(
        RoadAddress(1, 1, 1, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), None, Option("tester"),0, 12345L, 0.0, 9.8,
          SideCode.TowardsDigitizing, 0, (None, None), true, Seq(Point(0.0, 0.0), Point(0.0, 9.8)), LinkGeomSource.NormalLinkInterface),
        RoadAddress(2, 1, 1, Track.Combined, Discontinuous, 10L, 20L, Some(DateTime.parse("1901-01-01")), None, Option("tester"),0, 12345L, 0.0, 10.4,
          SideCode.TowardsDigitizing, 0, (None, None), true, Seq(Point(0.0, 9.8), Point(0.0, 20.2)), LinkGeomSource.NormalLinkInterface)
      )
      RoadAddressLinkBuilder.fuseRoadAddress(roadAddress) should have size (1)
    }
  }

  test("Fuse road address should not merge consecutive road addresses with differing start dates") {
    OracleDatabase.withDynSession {
      val roadAddress = Seq(
        RoadAddress(1, 1, 1, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), None, Option("tester"),0, 12345L, 0.0, 9.8, SideCode.TowardsDigitizing, 0, (None, None), false,
          Seq(Point(0.0, 0.0), Point(0.0, 9.8)), LinkGeomSource.NormalLinkInterface),
        RoadAddress(2, 1, 1, Track.Combined, Discontinuous, 10L, 20L, Some(DateTime.parse("1902-02-02")), None, Option("tester"),0, 12345L, 9.8, 20.2, SideCode.TowardsDigitizing, 0, (None, None), false,
          Seq(Point(0.0, 9.8), Point(0.0, 20.2)), LinkGeomSource.NormalLinkInterface),
        RoadAddress(3, 1, 1, Track.Combined, Discontinuous, 20L, 30L, Some(DateTime.parse("1901-01-01")), None, Option("tester"),0, 12345L, 20.2, 30.2, SideCode.TowardsDigitizing, 0, (None, None), false,
          Seq(Point(0.0, 20.2), Point(0.0, 30.2)), LinkGeomSource.NormalLinkInterface)
      )
      val fused = RoadAddressLinkBuilder.fuseRoadAddress(roadAddress)
      fused should have size (3)
      val ids = roadAddress.map(_.id).toSet
      RoadAddressLinkBuilder.fuseRoadAddress(roadAddress).map(_.id).toSet should be (ids)
    }
  }

  test("Fuse road address should not merge consecutive road addresses with differing link ids") {
    OracleDatabase.withDynSession {
      val roadAddress = Seq(
        RoadAddress(1, 1, 1, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), None, Option("tester"),0, 12345L, 0.0, 9.8, SideCode.TowardsDigitizing, 0, (None, None), false,
          Seq(Point(0.0, 0.0), Point(0.0, 9.8)), LinkGeomSource.NormalLinkInterface),
        RoadAddress(1, 1, 1, Track.Combined, Discontinuous, 10L, 20L, Some(DateTime.parse("1901-01-01")), None, Option("tester"),0, 12346L, 0.0, 10.4, SideCode.TowardsDigitizing, 0, (None, None), false,
          Seq(Point(0.0, 9.8), Point(0.0, 20.2)), LinkGeomSource.NormalLinkInterface)
      )
      RoadAddressLinkBuilder.fuseRoadAddress(roadAddress) should have size (2)
      val ids = roadAddress.map(_.id).toSet
      RoadAddressLinkBuilder.fuseRoadAddress(roadAddress).map(_.id).toSet should be (ids)
    }
  }

  test("Fuse road address should not merge consecutive road addresses if side code differs") {
    OracleDatabase.withDynSession {
      val roadAddress = Seq(
        RoadAddress(1, 1, 1, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), None, Option("tester"),0, 12345L, 0.0, 9.8, SideCode.TowardsDigitizing, 0, (None, None), false,
          Seq(Point(0.0, 0.0), Point(0.0, 9.8)), LinkGeomSource.NormalLinkInterface),
        RoadAddress(2, 1, 1, Track.Combined, Discontinuous, 10L, 20L, Some(DateTime.parse("1901-01-01")), None, Option("tester"),0, 12345L, 0.0, 10.4, SideCode.AgainstDigitizing, 0, (None, None), false,
          Seq(Point(0.0, 9.8), Point(0.0, 20.2)), LinkGeomSource.NormalLinkInterface)
      )
      intercept[InvalidAddressDataException] {
        RoadAddressLinkBuilder.fuseRoadAddress(roadAddress)
      }
    }
  }

  test("Fuse road address should merge multiple road addresses with random ordering") {
    OracleDatabase.withDynSession {
      val roadAddress = Seq(
        RoadAddress(1, 1, 1, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), None, Option("tester"),0, 12345L, 0.0, 9.8, SideCode.TowardsDigitizing, 0, (None, None), false,
          Seq(Point(0.0, 0.0), Point(0.0, 9.8)), LinkGeomSource.NormalLinkInterface),
        RoadAddress(4, 1, 1, Track.Combined, Discontinuous, 30L, 40L, Some(DateTime.parse("1901-01-01")), None, Option("tester"),0, 12345L, 30.0, 39.8, SideCode.TowardsDigitizing, 0, (None, None), false,
          Seq(Point(0.0, 30.0), Point(0.0, 39.8)), LinkGeomSource.NormalLinkInterface),
        RoadAddress(3, 1, 1, Track.Combined, Discontinuous, 20L, 30L, Some(DateTime.parse("1901-01-01")), None, Option("tester"),0, 12345L, 10.4, 30.0, SideCode.TowardsDigitizing, 0, (None, None), false,
          Seq(Point(0.0, 20.2), Point(0.0, 30.0)), LinkGeomSource.NormalLinkInterface),
        RoadAddress(2, 1, 1, Track.Combined, Discontinuous, 10L, 20L, Some(DateTime.parse("1901-01-01")), None, Option("tester"),0, 12345L, 0.0, 10.4, SideCode.TowardsDigitizing, 0, (None, None), false,
          Seq(Point(0.0, 9.8), Point(0.0, 20.2)), LinkGeomSource.NormalLinkInterface)
      )
      RoadAddressLinkBuilder.fuseRoadAddress(roadAddress) should have size (1)
    }
  }

  test("Fuse road address should not merge consecutive road addresses with calibration point in between") {
    // TODO: Or do we throw an exception then?
    OracleDatabase.withDynSession {
      val roadAddress = Seq(
        RoadAddress(1, 1, 1, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), None, Option("tester"),0, 12345L, 0.0, 9.8,
          SideCode.TowardsDigitizing, 0, (None, Some(CalibrationPoint(12345L, 9.8, 10L))), false, Seq(Point(0.0, 0.0), Point(0.0, 9.8)), LinkGeomSource.NormalLinkInterface),

        RoadAddress(2, 1, 1, Track.Combined, Discontinuous, 10L, 20L, Some(DateTime.parse("1901-01-01")), None, Option("tester"),0, 12345L, 0.0, 10.4,
          SideCode.TowardsDigitizing, 0, (Some(CalibrationPoint(12345L, 9.8, 10L)), None), false, Seq(Point(0.0, 9.8), Point(0.0, 20.2)), LinkGeomSource.NormalLinkInterface),

        RoadAddress(3, 1, 1, Track.Combined, Discontinuous, 20L, 30L, Some(DateTime.parse("1901-01-01")), None, Option("tester"),0, 12345L, 10.4, 30.0,
          SideCode.TowardsDigitizing, 0, (None, None), false, Seq(Point(0.0, 20.2), Point(0.0, 30.0)), LinkGeomSource.NormalLinkInterface),

        RoadAddress(4, 1, 1, Track.Combined, Discontinuous, 30L, 40L, Some(DateTime.parse("1901-01-01")), None, Option("tester"),0, 12345L, 30.0, 39.8,
          SideCode.TowardsDigitizing, 0, (None, None), false, Seq(Point(0.0, 30.0), Point(0.0, 39.8)), LinkGeomSource.NormalLinkInterface)
      )
      //Changed fuseRoadAddress size from 3 to 2 the reasoning behind it is that although we cannot fuse  1 and 2, there is nothing stopping us from fusing 2,3 and 4
      RoadAddressLinkBuilder.fuseRoadAddress(roadAddress) should have size (2)
    }
  }

  test("Fuse road address should combine geometries and address values with starting calibration point - real life scenario") {
    val geom = Seq(Point(379483.273,6672835.486), Point(379556.289,6673054.073))
    val roadAddress = Seq(
      RoadAddress(3767413, 101, 1, Track.RightSide, Discontinuous, 679L, 701L, Some(DateTime.parse("1991-01-01")), None, Option("tester"),0, 138834, 0.0, 21.0,
        SideCode.TowardsDigitizing, 0, (Some(CalibrationPoint(138834, 0.0, 679L)), None), false, GeometryUtils.truncateGeometry3D(geom, 0.0, 21.0), LinkGeomSource.NormalLinkInterface),

      RoadAddress(3767414, 101, 1, Track.RightSide, Discontinuous, 701L, 923L, Some(DateTime.parse("1991-01-01")), None, Option("tester"),0, 138834, 21.0, 230.776,
        SideCode.TowardsDigitizing, 0, (None, None), false, GeometryUtils.truncateGeometry3D(geom, 21.0, 230.776), LinkGeomSource.NormalLinkInterface)
    )
    val fusedList = RoadAddressLinkBuilder.fuseRoadAddress(roadAddress)
    fusedList should have size (1)
    val fused = fusedList.head
    fused.startMValue should be (0.0)
    fused.endMValue should be (230.776)
    fused.geom.last should be (Point(379556.289,6673054.073))
    fused.geom should have size(2)
    fused.startAddrMValue should be (679L)
    fused.endAddrMValue should be (923L)
    fused.track should be (Track.RightSide)
    fused.calibrationPoints._1.isEmpty should be (false)
    fused.calibrationPoints._2.isEmpty should be (true)

  }

  test("Fuse road address should use single calibration point for both") {
    val geom = Seq(Point(379483.273,6672835.486), Point(379556.289,6673054.073))
    val roadAddress = Seq(
      RoadAddress(3767413, 101, 1, Track.RightSide, Discontinuous, 679L, 701L, Some(DateTime.parse("1991-01-01")), None, Option("tester"),0, 138834, 0.0, 21.0,
        SideCode.TowardsDigitizing, 0, (Some(CalibrationPoint(138834, 0.0, 679L)), Some(CalibrationPoint(138834, 21.0, 920L))), false, GeometryUtils.truncateGeometry3D(geom, 0.0, 21.0), LinkGeomSource.NormalLinkInterface),

      RoadAddress(3767414, 101, 1, Track.RightSide, Discontinuous, 701L, 923L, Some(DateTime.parse("1991-01-01")), None,  Option("tester"),0, 138834, 21.0, 230.776,
        SideCode.TowardsDigitizing, 0, (None, None), false, GeometryUtils.truncateGeometry3D(geom, 21.0, 230.776), LinkGeomSource.NormalLinkInterface)
    )
    val fusedList = RoadAddressLinkBuilder.fuseRoadAddress(roadAddress)
    fusedList should have size (1)
    val fused = fusedList.head
    fused.startMValue should be (0.0)
    fused.endMValue should be (230.776)
    fused.geom.last should be (Point(379556.289,6673054.073))
    fused.geom should have size(2)
    fused.startAddrMValue should be (679L)
    fused.endAddrMValue should be (920L)
    fused.track should be (Track.RightSide)
    fused.calibrationPoints._1.isEmpty should be (false)
    fused.calibrationPoints._2.isEmpty should be (false)
    fused.calibrationPoints._2.get.addressMValue should be (920L)
    fused.calibrationPoints._2.get.segmentMValue should be (230.776)
  }

  test("Fuse road address should fuse against digitization road addresses properly") {
    OracleDatabase.withDynSession {
      val roadAddress = Seq(
        RoadAddress(1, 1, 1, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), None, Option("tester"),0, 12345L, 0.0, 9.8, SideCode.AgainstDigitizing, 0, (None, None), false,
          Seq(Point(0.0, 9.8), Point(0.0, 0.0)), LinkGeomSource.NormalLinkInterface),
        RoadAddress(4, 1, 1, Track.Combined, Discontinuous, 30L, 40L, Some(DateTime.parse("1901-01-01")), None, Option("tester"),0, 12345L, 30.0, 39.8, SideCode.AgainstDigitizing, 0, (None, None), false,
          Seq(Point(0.0, 39.8), Point(0.0, 30.0)), LinkGeomSource.NormalLinkInterface),
        RoadAddress(3, 1, 1, Track.Combined, Discontinuous, 20L, 30L, Some(DateTime.parse("1901-01-01")), None, Option("tester"),0, 12345L, 10.4, 30.0, SideCode.AgainstDigitizing, 0, (None, None), false,
          Seq(Point(0.0, 30.0), Point(0.0, 20.2)), LinkGeomSource.NormalLinkInterface),
        RoadAddress(2, 1, 1, Track.Combined, Discontinuous, 10L, 20L, Some(DateTime.parse("1901-01-01")), None, Option("tester"),0, 12345L, 0.0, 10.4, SideCode.AgainstDigitizing, 0, (None, None), false,
          Seq(Point(0.0, 20.2), Point(0.0, 9.8)), LinkGeomSource.NormalLinkInterface)
      )
      RoadAddressLinkBuilder.fuseRoadAddress(roadAddress) should have size (1)
    }
  }

}
