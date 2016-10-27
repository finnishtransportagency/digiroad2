package fi.liikennevirasto.viite.process

import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.viite.dao.{CalibrationPoint, Discontinuity, RoadAddress}
import org.joda.time.DateTime
import fi.liikennevirasto.viite.RoadType
import org.scalatest.{FunSuite, Matchers}

/**
  * Created by venholat on 30.9.2016.
  */
class LinkRoadAddressCalculatorSpec extends FunSuite with Matchers{

  test("testRecalculate one track road with single part") {
    val addresses = Seq(RoadAddress(1, 1, 1, Track.Combined, Discontinuity.Continuous, 0, 100, DateTime.now, null, 123L, 0.0, 98.3, (Some(CalibrationPoint(123L, 0.0, 0)), None)),
      RoadAddress(2, 1, 1, Track.Combined, Discontinuity.Continuous, 100, 130, DateTime.now, null, 124L, 0.0, 33.2),
      RoadAddress(3, 1, 1, Track.Combined, Discontinuity.Continuous, 130, 180, DateTime.now, null, 125L, 0.0, 48.1, (None, Some(CalibrationPoint(125L, 48.1, 180)))))
    val returnval = LinkRoadAddressCalculator.recalculate(addresses)
    returnval.last.endAddrMValue should be (180)
    returnval.last.endMValue should be (48.1)
    returnval.head.endAddrMValue should be (Math.round(180.0/(98.3+33.2+48.1)*98.3))
    returnval.filter(_.startAddrMValue==0) should have size (1)
  }

  test("testRecalculate one track road with single parts") {
    val addresses = Seq(
      RoadAddress(1, 1, 1, Track.LeftSide, Discontinuity.Continuous, 0, 110, DateTime.now, null, 123L, 0.0, 108.3, (Some(CalibrationPoint(123L, 0.0, 0)), None)),
      RoadAddress(2, 1, 1, Track.LeftSide, Discontinuity.Continuous, 110, 135, DateTime.now, null, 124L, 0.0, 25.2),
      RoadAddress(3, 1, 1, Track.LeftSide, Discontinuity.Continuous, 135, 180, DateTime.now, null, 125L, 0.0, 58.1, (None, Some(CalibrationPoint(125L, 48.1, 180)))),
      RoadAddress(4, 1, 1, Track.RightSide, Discontinuity.Continuous, 0, 100, DateTime.now, null, 223L, 0.0, 98.3, (Some(CalibrationPoint(223L, 0.0, 0)), None)),
      RoadAddress(5, 1, 1, Track.RightSide, Discontinuity.Continuous, 100, 130, DateTime.now, null, 224L, 0.0, 33.2),
      RoadAddress(6, 1, 1, Track.RightSide, Discontinuity.Continuous, 130, 180, DateTime.now, null, 225L, 0.0, 48.1, (None, Some(CalibrationPoint(225L, 48.1, 180))))
    )
    val (left, right) = LinkRoadAddressCalculator.recalculate(addresses).partition(_.track == Track.LeftSide)
    left.last.endAddrMValue should be (180)
    right.last.endAddrMValue should be (180)
    left.head.endAddrMValue should be (Math.round(180.0/(108.3+25.2+58.1)*108.3))
    right.head.endAddrMValue should be (Math.round(180.0/(98.3+33.2+48.1)*98.3))
    left.filter(_.startAddrMValue==0) should have size (1)
    right.filter(_.startAddrMValue==0) should have size (1)
  }

  test("testRecalculate one track road with multiple parts together") {
    val addresses = Seq(
      RoadAddress(1, 1, 1, Track.Combined, Discontinuity.Continuous, 0, 110, DateTime.now, null, 123L, 0.0, 108.3, (Some(CalibrationPoint(123L, 0.0, 0)), None)),
      RoadAddress(2, 1, 1, Track.Combined, Discontinuity.Continuous, 110, 135, DateTime.now, null, 124L, 0.0, 25.2),
      RoadAddress(3, 1, 1, Track.Combined, Discontinuity.Continuous, 135, 180, DateTime.now, null, 125L, 0.0, 58.1, (None, Some(CalibrationPoint(125L, 58.1, 180)))),
      RoadAddress(4, 1, 2, Track.Combined, Discontinuity.Continuous, 0, 100, DateTime.now, null, 223L, 0.0, 98.3, (Some(CalibrationPoint(223L, 0.0, 0)), None)),
      RoadAddress(5, 1, 2, Track.Combined, Discontinuity.Continuous, 100, 108, DateTime.now, null, 224L, 0.0, 8.0),
      RoadAddress(6, 1, 2, Track.Combined, Discontinuity.Continuous, 108, 116, DateTime.now, null, 224L, 0.0, 8.0),
      RoadAddress(7, 1, 2, Track.Combined, Discontinuity.Continuous, 116, 124, DateTime.now, null, 224L, 0.0, 8.0),
      RoadAddress(8, 1, 2, Track.Combined, Discontinuity.Continuous, 124, 130, DateTime.now, null, 224L, 0.0, 9.2),
      RoadAddress(9, 1, 2, Track.Combined, Discontinuity.Continuous, 130, 180, DateTime.now, null, 225L, 0.0, 48.1, (None, Some(CalibrationPoint(225L, 48.1, 180))))
    )
    val (one, two) = LinkRoadAddressCalculator.recalculate(addresses).partition(_.roadPartNumber == 1)
    one.last.endAddrMValue should be (180)
    two.last.endAddrMValue should be (180)
    one.head.endAddrMValue should be (Math.round(180.0/(108.3+25.2+58.1)*108.3))
    two.head.endAddrMValue should be (Math.round(180.0/(98.3+33.2+48.1)*98.3))
    one.filter(_.startAddrMValue==0) should have size (1)
    two.filter(_.startAddrMValue==0) should have size (1)
  }

  test("testRecalculate multiple track road with single part") {
    val addresses = Seq(
      RoadAddress(1, 1, 1, Track.Combined, Discontinuity.Continuous, 0, 110, DateTime.now, null, 123L, 0.0, 108.3, (Some(CalibrationPoint(123L, 0.0, 0)), None)),
      RoadAddress(2, 1, 1, Track.Combined, Discontinuity.Continuous, 110, 135, DateTime.now, null, 124L, 0.0, 25.2),
      RoadAddress(3, 1, 1, Track.Combined, Discontinuity.Continuous, 135, 180, DateTime.now, null, 125L, 0.0, 58.1, (None, Some(CalibrationPoint(125L, 58.1, 180)))),
      RoadAddress(4, 1, 1, Track.LeftSide, Discontinuity.Continuous, 180, 225, DateTime.now, null, 223L, 0.0, 44.3, (Some(CalibrationPoint(223L, 0.0, 180)), None)),
      RoadAddress(5, 1, 1, Track.LeftSide, Discontinuity.Continuous, 225, 265, DateTime.now, null, 224L, 0.0, 33.2, (None, Some(CalibrationPoint(225L, 33.2, 265)))),
      RoadAddress(6, 1, 1, Track.RightSide, Discontinuity.Continuous, 180, 230, DateTime.now, null, 225L, 0.0, 48.1, (Some(CalibrationPoint(225L, 0.0, 180)), None)),
      RoadAddress(7, 1, 1, Track.RightSide, Discontinuity.Continuous, 230, 265, DateTime.now, null, 323L, 0.0, 38.3, (None, Some(CalibrationPoint(323L, 38.3, 265)))),
      RoadAddress(8, 1, 1, Track.Combined, Discontinuity.Continuous, 265, 300, DateTime.now, null, 324L, 0.0, 33.2, (Some(CalibrationPoint(323L, 0.0, 265)), None)),
      RoadAddress(9, 1, 1, Track.Combined, Discontinuity.EndOfRoad, 300, 350, DateTime.now, null, 325L, 0.0, 48.1, (None, Some(CalibrationPoint(225L, 48.1, 350))))
    )
    val results = LinkRoadAddressCalculator.recalculate(addresses).toList.sortBy(_.endAddrMValue)
    results.last.endAddrMValue should be (350)
    results.head.endAddrMValue should be (102)
    results.filter(_.startAddrMValue==0) should have size (1)
  }

  test("calibration point in the middle of the link must not break calculation") {
    val addresses = Seq(
      RoadAddress(1, 1, 1, Track.Combined, Discontinuity.Continuous, 0, 110, DateTime.now, null, 123L, 0.0, 108.3, (Some(CalibrationPoint(123L, 0.0, 0)), None)),
      RoadAddress(2, 1, 1, Track.Combined, Discontinuity.Continuous, 110, 135, DateTime.now, null, 123L, 108.3, 135.2),
      RoadAddress(3, 1, 1, Track.Combined, Discontinuity.Continuous, 135, 180, DateTime.now, null, 123L, 135.2, 180.1, (None, Some(CalibrationPoint(123L, 180.1, 180)))),
      RoadAddress(4, 1, 1, Track.Combined, Discontinuity.Continuous, 180, 225, DateTime.now, null, 123L, 180.1, 224.3, (Some(CalibrationPoint(123L, 180.1, 180)), None)),
      RoadAddress(5, 1, 1, Track.Combined, Discontinuity.Continuous, 225, 265, DateTime.now, null, 123L, 224.3, 265.2, (None, Some(CalibrationPoint(225L, 265.2, 265)))))
    val results = LinkRoadAddressCalculator.recalculate(addresses).toList.sortBy(_.endAddrMValue)
    results.foreach(println)
    results.last.endAddrMValue should be (265)
    results.head.endAddrMValue should be (108)
    results.filter(_.startAddrMValue==0) should have size (1)
  }
}
