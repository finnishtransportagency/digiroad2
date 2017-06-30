package fi.liikennevirasto.viite

import fi.liikennevirasto.digiroad2.asset.ConstructionType.InUse
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import fi.liikennevirasto.digiroad2.asset.{SideCode, State, UnknownLinkType}
import fi.liikennevirasto.digiroad2.asset.TrafficDirection.BothDirections
import fi.liikennevirasto.digiroad2.asset.SideCode.AgainstDigitizing
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.digiroad2.util.Track.Combined
import fi.liikennevirasto.viite.RoadType.UnknownOwnerRoad
import fi.liikennevirasto.viite.dao.Discontinuity.{Continuous, Discontinuous}
import fi.liikennevirasto.viite.dao.LinkStatus.NotHandled
import fi.liikennevirasto.viite.dao._
import fi.liikennevirasto.viite.process.InvalidAddressDataException
import org.joda.time.DateTime
import org.scalatest.{Assertions, FunSuite, Matchers}

class RoadAddressLinkBuilderSpec extends FunSuite with Matchers{

  test("Fuse road address should accept single road address") {
    val roadAddress = Seq(RoadAddress(1, 1, 1, Track.Combined, Discontinuous, 0L, 10L, None, None, Option("tester"), 0, 12345L, 0.0, 9.8, SideCode.TowardsDigitizing, 0, (None, None), false,
      Seq(Point(0.0,0.0), Point(0.0,9.8))))
    RoadAddressLinkBuilder.fuseRoadAddress(roadAddress) should be (roadAddress)
  }

  test("Fuse road address should merge consecutive road addresses even if floating") {
    OracleDatabase.withDynSession {
      val roadAddress = Seq(
        RoadAddress(1, 1, 1, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), None, Option("tester"),0, 12345L, 0.0, 9.8,
          SideCode.TowardsDigitizing, 0, (None, None), true, Seq(Point(0.0, 0.0), Point(0.0, 9.8))),
        RoadAddress(2, 1, 1, Track.Combined, Discontinuous, 10L, 20L, Some(DateTime.parse("1901-01-01")), None, Option("tester"),0, 12345L, 0.0, 10.4,
          SideCode.TowardsDigitizing, 0, (None, None), true, Seq(Point(0.0, 9.8), Point(0.0, 20.2)))
      )
      RoadAddressLinkBuilder.fuseRoadAddress(roadAddress) should have size (1)
    }
  }

  test("Fuse road address should not merge consecutive road addresses with differing start dates") {
    OracleDatabase.withDynSession {
      val roadAddress = Seq(
        RoadAddress(1, 1, 1, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), None, Option("tester"),0, 12345L, 0.0, 9.8, SideCode.TowardsDigitizing, 0, (None, None), false,
          Seq(Point(0.0, 0.0), Point(0.0, 9.8))),
        RoadAddress(2, 1, 1, Track.Combined, Discontinuous, 10L, 20L, Some(DateTime.parse("1902-02-02")), None, Option("tester"),0, 12345L, 9.8, 20.2, SideCode.TowardsDigitizing, 0, (None, None), false,
          Seq(Point(0.0, 9.8), Point(0.0, 20.2))),
        RoadAddress(3, 1, 1, Track.Combined, Discontinuous, 20L, 30L, Some(DateTime.parse("1901-01-01")), None, Option("tester"),0, 12345L, 20.2, 30.2, SideCode.TowardsDigitizing, 0, (None, None), false,
          Seq(Point(0.0, 20.2), Point(0.0, 30.2)))
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
          Seq(Point(0.0, 0.0), Point(0.0, 9.8))),
        RoadAddress(1, 1, 1, Track.Combined, Discontinuous, 10L, 20L, Some(DateTime.parse("1901-01-01")), None, Option("tester"),0, 12346L, 0.0, 10.4, SideCode.TowardsDigitizing, 0, (None, None), false,
          Seq(Point(0.0, 9.8), Point(0.0, 20.2)))
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
          Seq(Point(0.0, 0.0), Point(0.0, 9.8))),
        RoadAddress(2, 1, 1, Track.Combined, Discontinuous, 10L, 20L, Some(DateTime.parse("1901-01-01")), None, Option("tester"),0, 12345L, 0.0, 10.4, SideCode.AgainstDigitizing, 0, (None, None), false,
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
        RoadAddress(1, 1, 1, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), None, Option("tester"),0, 12345L, 0.0, 9.8, SideCode.TowardsDigitizing, 0, (None, None), false,
          Seq(Point(0.0, 0.0), Point(0.0, 9.8))),
        RoadAddress(4, 1, 1, Track.Combined, Discontinuous, 30L, 40L, Some(DateTime.parse("1901-01-01")), None, Option("tester"),0, 12345L, 30.0, 39.8, SideCode.TowardsDigitizing, 0, (None, None), false,
          Seq(Point(0.0, 30.0), Point(0.0, 39.8))),
        RoadAddress(3, 1, 1, Track.Combined, Discontinuous, 20L, 30L, Some(DateTime.parse("1901-01-01")), None, Option("tester"),0, 12345L, 10.4, 30.0, SideCode.TowardsDigitizing, 0, (None, None), false,
          Seq(Point(0.0, 20.2), Point(0.0, 30.0))),
        RoadAddress(2, 1, 1, Track.Combined, Discontinuous, 10L, 20L, Some(DateTime.parse("1901-01-01")), None, Option("tester"),0, 12345L, 0.0, 10.4, SideCode.TowardsDigitizing, 0, (None, None), false,
          Seq(Point(0.0, 9.8), Point(0.0, 20.2)))
      )
      RoadAddressLinkBuilder.fuseRoadAddress(roadAddress) should have size (1)
    }
  }

  test("Fuse road address should not merge consecutive road addresses with calibration point in between") {
    // TODO: Or do we throw an exception then?
    OracleDatabase.withDynSession {
      val roadAddress = Seq(
        RoadAddress(1, 1, 1, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), None, Option("tester"),0, 12345L, 0.0, 9.8,
          SideCode.TowardsDigitizing, 0, (None, Some(CalibrationPoint(12345L, 9.8, 10L))), false, Seq(Point(0.0, 0.0), Point(0.0, 9.8))),

        RoadAddress(2, 1, 1, Track.Combined, Discontinuous, 10L, 20L, Some(DateTime.parse("1901-01-01")), None, Option("tester"),0, 12345L, 0.0, 10.4,
          SideCode.TowardsDigitizing, 0, (Some(CalibrationPoint(12345L, 9.8, 10L)), None), false, Seq(Point(0.0, 9.8), Point(0.0, 20.2))),

        RoadAddress(3, 1, 1, Track.Combined, Discontinuous, 20L, 30L, Some(DateTime.parse("1901-01-01")), None, Option("tester"),0, 12345L, 10.4, 30.0,
          SideCode.TowardsDigitizing, 0, (None, None), false, Seq(Point(0.0, 20.2), Point(0.0, 30.0))),

        RoadAddress(4, 1, 1, Track.Combined, Discontinuous, 30L, 40L, Some(DateTime.parse("1901-01-01")), None, Option("tester"),0, 12345L, 30.0, 39.8,
          SideCode.TowardsDigitizing, 0, (None, None), false, Seq(Point(0.0, 30.0), Point(0.0, 39.8)))
      )
      //Changed fuseRoadAddress size from 3 to 2 the reasoning behind it is that although we cannot fuse  1 and 2, there is nothing stopping us from fusing 2,3 and 4
      RoadAddressLinkBuilder.fuseRoadAddress(roadAddress) should have size (2)
    }
  }

  test("Fuse road address should combine geometries and address values with starting calibration point - real life scenario") {
    val geom = Seq(Point(379483.273,6672835.486), Point(379556.289,6673054.073))
    val roadAddress = Seq(
      RoadAddress(3767413, 101, 1, Track.RightSide, Discontinuous, 679L, 701L, Some(DateTime.parse("1991-01-01")), None, Option("tester"),0, 138834, 0.0, 21.0,
        SideCode.TowardsDigitizing, 0, (Some(CalibrationPoint(138834, 0.0, 679L)), None), false, GeometryUtils.truncateGeometry3D(geom, 0.0, 21.0)),

      RoadAddress(3767414, 101, 1, Track.RightSide, Discontinuous, 701L, 923L, Some(DateTime.parse("1991-01-01")), None, Option("tester"),0, 138834, 21.0, 230.776,
        SideCode.TowardsDigitizing, 0, (None, None), false, GeometryUtils.truncateGeometry3D(geom, 21.0, 230.776))
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
        SideCode.TowardsDigitizing, 0, (Some(CalibrationPoint(138834, 0.0, 679L)), Some(CalibrationPoint(138834, 21.0, 920L))), false, GeometryUtils.truncateGeometry3D(geom, 0.0, 21.0)),

      RoadAddress(3767414, 101, 1, Track.RightSide, Discontinuous, 701L, 923L, Some(DateTime.parse("1991-01-01")), None,  Option("tester"),0, 138834, 21.0, 230.776,
        SideCode.TowardsDigitizing, 0, (None, None), false, GeometryUtils.truncateGeometry3D(geom, 21.0, 230.776))
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
          Seq(Point(0.0, 9.8), Point(0.0, 0.0))),
        RoadAddress(4, 1, 1, Track.Combined, Discontinuous, 30L, 40L, Some(DateTime.parse("1901-01-01")), None, Option("tester"),0, 12345L, 30.0, 39.8, SideCode.AgainstDigitizing, 0, (None, None), false,
          Seq(Point(0.0, 39.8), Point(0.0, 30.0))),
        RoadAddress(3, 1, 1, Track.Combined, Discontinuous, 20L, 30L, Some(DateTime.parse("1901-01-01")), None, Option("tester"),0, 12345L, 10.4, 30.0, SideCode.AgainstDigitizing, 0, (None, None), false,
          Seq(Point(0.0, 30.0), Point(0.0, 20.2))),
        RoadAddress(2, 1, 1, Track.Combined, Discontinuous, 10L, 20L, Some(DateTime.parse("1901-01-01")), None, Option("tester"),0, 12345L, 0.0, 10.4, SideCode.AgainstDigitizing, 0, (None, None), false,
          Seq(Point(0.0, 20.2), Point(0.0, 9.8)))
      )
      RoadAddressLinkBuilder.fuseRoadAddress(roadAddress) should have size (1)
    }
  }

  test("Correct build for ProjectAddressLink partitioner") {
    val unknownProjectLink = ProjectLink(0,0,0,Track.Unknown,Discontinuity.Continuous,0,0,None,None,None,0,0,0.0,0.0,SideCode.Unknown,(None,None),false, List(),0,NotHandled,UnknownOwnerRoad)
    val projectLinks =
      Map(
        1717380l -> ProjectLink(1270,0,0,Track.apply(99), Continuous,1021,1028,None,None,None,70001448,1717380,0.0,6.0,AgainstDigitizing,(None,None),false,List(),1227,NotHandled,UnknownOwnerRoad),
        1717374l -> ProjectLink(1259,1130,0,Combined, Continuous,959,1021,None,None,None,70001437,1717374,0.0,61.0,AgainstDigitizing,(None,None),false,List(),1227,NotHandled,UnknownOwnerRoad)
      )

    val roadLinks = Seq(
      RoadLink(1717380,List(Point(358594.785,6678940.735,57.788000000000466), Point(358599.713,6678945.133,57.78100000000268)),6.605118318435748,State,99,BothDirections,UnknownLinkType,Some("14.10.2016 21:15:13"),Some("vvh_modified"),Map("TO_RIGHT" -> 104, "LAST_EDITED_DATE" -> BigInt("1476468913000"), "FROM_LEFT" -> 103, "MTKHEREFLIP" -> 1, "MTKID" -> 362888804, "ROADNAME_FI" -> "Evitskogintie", "STARTNODE" -> 1729826, "VERTICALACCURACY" -> 201, "ENDNODE" -> 1729824, "VALIDFROM" -> BigInt("1379548800000"), "CONSTRUCTIONTYPE" -> 0, "SURFACETYPE" -> 2, "MTKCLASS" -> 12122, "ROADPARTNUMBER" -> 4, "points" -> List(Map("x" -> 358594.785, "y" -> 6678940.735, "z" -> 57.788000000000466, "m" -> 0), Map("x" -> 358599.713, "y" -> 6678945.133, "z" -> 57.78100000000268, "m" -> 6.605100000000675)), "TO_LEFT" -> 103, "geometryWKT" -> "LINESTRING ZM (358594.785 6678940.735 57.788000000000466 0, 358599.713 6678945.133 57.78100000000268 6.605100000000675)", "VERTICALLEVEL" -> 0, "ROADNAME_SE" -> "Evitskogsvägen", "MUNICIPALITYCODE" -> BigInt(257), "FROM_RIGHT" -> 104, "CREATED_DATE" -> BigInt("1446132842000"), "GEOMETRY_EDITED_DATE" -> BigInt("1476468913000"), "HORIZONTALACCURACY" -> 3000, "ROADNUMBER" -> 1130),InUse,NormalLinkInterface),
      RoadLink(1717374,List(Point(358599.713,6678945.133,57.78100000000268), Point(358601.644,6678946.448,57.771999999997206), Point(358621.812,6678964.766,57.41000000000349), Point(358630.04,6678971.657,57.10099999999511), Point(358638.064,6678977.863,56.78599999999278), Point(358647.408,6678984.55,56.31399999999849)),61.948020518025565,State,99,BothDirections,UnknownLinkType,Some("14.10.2016 21:15:13"),Some("vvh_modified"),Map("TO_RIGHT" -> 98, "LAST_EDITED_DATE" -> BigInt("1476468913000"), "FROM_LEFT" -> 101, "MTKHEREFLIP" -> 1, "MTKID" -> 362888798, "STARTNODE" -> 1729824, "VERTICALACCURACY" -> 201, "ENDNODE" -> 1729819, "VALIDFROM" -> BigInt("1379548800000"), "CONSTRUCTIONTYPE" -> 0, "SURFACETYPE" -> 2, "MTKCLASS" -> 12122, "points" -> List(Map("x" -> 358599.713, "y" -> 6678945.133, "z" -> 57.78100000000268, "m" -> 0), Map("x" -> 358601.644, "y" -> 6678946.448, "z" -> 57.771999999997206, "m" -> 2.336200000005192), Map("x" -> 358621.812, "y" -> 6678964.766, "z" -> 57.41000000000349, "m" -> 29.581399999995483), Map("x" -> 358630.04, "y" -> 6678971.657, "z" -> 57.10099999999511, "m" -> 40.31380000000354), Map("x" -> 358638.064, "y" -> 6678977.863, "z" -> 56.78599999999278, "m" -> 50.45780000000377), Map("x" -> 358647.408, "y" -> 6678984.55, "z" -> 56.31399999999849, "m" -> 61.94800000000396)), "TO_LEFT" -> 97, "geometryWKT" -> "LINESTRING ZM (358599.713 6678945.133 57.78100000000268 0, 358601.644 6678946.448 57.771999999997206 2.336200000005192, 358621.812 6678964.766 57.41000000000349 29.581399999995483, 358630.04 6678971.657 57.10099999999511 40.31380000000354, 358638.064 6678977.863 56.78599999999278 50.45780000000377, 358647.408 6678984.55 56.31399999999849 61.94800000000396)", "VERTICALLEVEL" -> 0, "ROADNAME_SE" -> "Evitskogsvägen", "MUNICIPALITYCODE" -> BigInt(0), "FROM_RIGHT" -> 102, "CREATED_DATE" -> BigInt("1446132842000"), "GEOMETRY_EDITED_DATE" -> BigInt("1476468913000"), "HORIZONTALACCURACY" -> 3000, "ROADNUMBER" -> 1130),InUse,NormalLinkInterface)
    )
    val projectRoadLinks = roadLinks.map {
      rl =>
        val pl = projectLinks.getOrElse(rl.linkId, unknownProjectLink)
        rl.linkId -> RoadAddressLinkBuilder.build(rl, pl)
    }
    projectRoadLinks should have size (2)
    val pal1 = projectRoadLinks.head._2
    val pal2 = projectRoadLinks.tail.head._2

    pal1.roadNumber should be (1130)
    pal1.roadPartNumber should be (4)
    pal1.trackCode should be (99)
    pal1.roadName should be ("Evitskogintie")
    pal1.municipalityCode should be (BigInt(257))

    pal2.roadNumber should be (1130)
    pal2.roadPartNumber should be (0)
    pal2.trackCode should be (0)
    pal2.roadName should be ("Evitskogsvägen")
    pal2.municipalityCode should be (BigInt(0))
  }

}
