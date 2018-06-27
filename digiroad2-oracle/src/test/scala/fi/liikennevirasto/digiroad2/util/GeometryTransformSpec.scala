  package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.SideCode
import org.scalatest.{FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.dao.{RoadAddress => ViiteRoadAddress}
import fi.liikennevirasto.digiroad2.service.RoadAddressesService
import org.mockito.Matchers.any
import org.mockito.Mockito.when
import org.scalatest.mock.MockitoSugar
import slick.jdbc.StaticQuery.interpolation


class GeometryTransformSpec extends FunSuite with Matchers {
  val mockRoadAddressesService = MockitoSugar.mock[RoadAddressesService]
  val transform = new GeometryTransform(mockRoadAddressesService)

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  test("Resolve location on left when asset SideCode different than AgainstDigitizing value") {
    val linkId = 1641830
    val mValue = 60
    val sideCode = 1

    runWithRollback {

      val ra = Some(ViiteRoadAddress(1, 921, 2, Track.Combined, 0, 299, None, None, 1641830, 10, 298.694, SideCode.TowardsDigitizing, false, Seq(Point(4002, 3067), Point(385258.765,7300119.103)), false, None, None, None))

      when(mockRoadAddressesService.getByLrmPosition(any[Long], any[Double])).thenReturn(ra)
//      val id = Sequences.nextViitePrimaryKeySeqValue
//      val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
//      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE)
//    values ($lrmPositionId,null,2,10,298.694,null,1641830,0,to_timestamp('17.02.17 12:21:39','RR.MM.DD HH24:MI:SS'))""".execute
//
//      sqlu"""Insert into ROAD_ADDRESS (ID,ROAD_NUMBER,ROAD_PART_NUMBER,TRACK_CODE,DISCONTINUITY,START_ADDR_M,END_ADDR_M,LRM_POSITION_ID,START_DATE,END_DATE,CREATED_BY,VALID_FROM,CALIBRATION_POINTS,FLOATING,GEOMETRY,VALID_TO)
//    values ($id,921,2,0,5,0,299,$lrmPositionId,to_date('01.09.12','RR.MM.DD'),null,'tr',to_date('01.09.12','RR.MM.DD'),2,0,MDSYS.SDO_GEOMETRY(4002,3067,NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1),MDSYS.SDO_ORDINATE_ARRAY(385258.765,7300119.103,0,0,384984.756,7300237.964,0,299)),null)""".execute

      val (roadAddress, roadSide) =
        transform.resolveAddressAndLocation(Point(0,0), 0, mValue, linkId, sideCode)

      roadAddress.road should be(921)
      roadAddress.track should be(Track.Combined)
      roadAddress.addrM should be(247)
      roadSide should be(RoadSide.Left)
    }
  }

  test("Resolve location on left when asset SideCode equals to TowardsDigitizing value") {
    val linkId = 1641830
    val mValue = 60
    val sideCode = 3

    runWithRollback {
//      val id = Sequences.nextViitePrimaryKeySeqValue
//      val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
//      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE)
//    values ($lrmPositionId,null,2,10,298.694,null,1641830,0,to_timestamp('17.02.17 12:21:39','RR.MM.DD HH24:MI:SS'))""".execute
//
//      sqlu"""Insert into ROAD_ADDRESS (ID,ROAD_NUMBER,ROAD_PART_NUMBER,TRACK_CODE,DISCONTINUITY,START_ADDR_M,END_ADDR_M,LRM_POSITION_ID,START_DATE,END_DATE,CREATED_BY,VALID_FROM,CALIBRATION_POINTS,FLOATING,GEOMETRY,VALID_TO)
//    values ($id,921,2,0,5,0,299,$lrmPositionId,to_date('01.09.12','RR.MM.DD'),null,'tr',to_date('01.09.12','RR.MM.DD'),2,0,MDSYS.SDO_GEOMETRY(4002,3067,NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1),MDSYS.SDO_ORDINATE_ARRAY(385258.765,7300119.103,0,0,384984.756,7300237.964,0,299)),null)""".execute

      val (roadAddress, roadSide) =
        transform.resolveAddressAndLocation(Point(0,0), 0, mValue, linkId, sideCode)

      roadAddress.road should be(921)
      roadAddress.track should be(Track.Combined)
      roadAddress.addrM should be(51)
      roadSide should be(RoadSide.Left)
    }
  }

  test("Resolve location on right when asset SideCode different than AgainstDigitizing value") {
    val linkId = 1641830
    val mValue = 60
    val sideCode = 1

    runWithRollback {
//      val id = Sequences.nextViitePrimaryKeySeqValue
//      val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
//      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE)
//    values ($lrmPositionId,null,1,0,298.694,null,1641830,0,to_timestamp('17.02.17 12:21:39','RR.MM.DD HH24:MI:SS'))""".execute
//
//      sqlu"""Insert into ROAD_ADDRESS (ID,ROAD_NUMBER,ROAD_PART_NUMBER,TRACK_CODE,DISCONTINUITY,START_ADDR_M,END_ADDR_M,LRM_POSITION_ID,START_DATE,END_DATE,CREATED_BY,VALID_FROM,CALIBRATION_POINTS,FLOATING,GEOMETRY,VALID_TO)
//    values ($id,921,2,1,5,0,299,$lrmPositionId,to_date('01.09.12','RR.MM.DD'),null,'tr',to_date('01.09.12','RR.MM.DD'),2,0,MDSYS.SDO_GEOMETRY(4002,3067,NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1),MDSYS.SDO_ORDINATE_ARRAY(385258.765,7300119.103,0,0,384984.756,7300237.964,0,299)),null)""".execute

      val (roadAddress, roadSide) = transform.resolveAddressAndLocation(Point(0,0), 0, mValue, linkId, sideCode)
      roadAddress.road should be(921)
      roadAddress.track should be(Track.RightSide)
      roadAddress.addrM should be(238)
      roadSide should be(RoadSide.Right)
    }
  }

  test("Resolve location on right when asset SideCode equals to TowardsDigitizing value") {
    val linkId = 1641830
    val mValue = 60
    val sideCode = 2

    runWithRollback {
//      val id = Sequences.nextViitePrimaryKeySeqValue
//      val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
//      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE)
//    values ($lrmPositionId,null,2,0,298.694,null,1641830,0,to_timestamp('17.02.17 12:21:39','RR.MM.DD HH24:MI:SS'))""".execute
//
//      sqlu"""Insert into ROAD_ADDRESS (ID,ROAD_NUMBER,ROAD_PART_NUMBER,TRACK_CODE,DISCONTINUITY,START_ADDR_M,END_ADDR_M,LRM_POSITION_ID,START_DATE,END_DATE,CREATED_BY,VALID_FROM,CALIBRATION_POINTS,FLOATING,GEOMETRY,VALID_TO)
//    values ($id,921,1,1,5,0,299,$lrmPositionId,to_date('01.09.12','RR.MM.DD'),null,'tr',to_date('01.09.12','RR.MM.DD'),2,0,MDSYS.SDO_GEOMETRY(4002,3067,NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1),MDSYS.SDO_ORDINATE_ARRAY(385258.765,7300119.103,0,0,384984.756,7300237.964,0,299)),null)""".execute

      val (roadAddress, roadSide) = transform.resolveAddressAndLocation(Point(0,0), 0, mValue, linkId, sideCode)
      roadAddress.road should be(921)
      roadAddress.track should be(Track.RightSide)
      roadAddress.addrM should be(60)
      roadSide should be(RoadSide.Right)
    }
  }

  test("Resolve location on two-way road") {
    val linkId = 1641830
    val mValue = 11
    val sideCode = 0

    runWithRollback {
//      val id = Sequences.nextViitePrimaryKeySeqValue
//      val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
//      val id2 = Sequences.nextViitePrimaryKeySeqValue
//      val lrmPositionId2 = Sequences.nextLrmPositionPrimaryKeySeqValue
//
//      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE)
//      values ($lrmPositionId,null,2,1,298.694,null,1641830,0,to_timestamp('17.02.17 12:21:39','RR.MM.DD HH24:MI:SS'))""".execute
//
//      sqlu"""Insert into ROAD_ADDRESS (ID,ROAD_NUMBER,ROAD_PART_NUMBER,TRACK_CODE,DISCONTINUITY,START_ADDR_M,END_ADDR_M,LRM_POSITION_ID,START_DATE,END_DATE,CREATED_BY,VALID_FROM,CALIBRATION_POINTS,FLOATING,GEOMETRY,VALID_TO)
//      values ($id,110,2,0,5,0,299,$lrmPositionId,to_date('01.09.12','RR.MM.DD'),null,'tr',to_date('01.09.12','RR.MM.DD'),2,0,MDSYS.SDO_GEOMETRY(4002,3067,NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1),MDSYS.SDO_ORDINATE_ARRAY(385258.765,7300119.103,0,0,384984.756,7300237.964,0,299)),null)""".execute
//
//      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE)
//      values ($lrmPositionId2,null,2,1,150.690,null,1641830,0,to_timestamp('17.02.17 12:21:39','RR.MM.DD HH24:MI:SS'))""".execute
//
//      sqlu"""Insert into ROAD_ADDRESS (ID,ROAD_NUMBER,ROAD_PART_NUMBER,TRACK_CODE,DISCONTINUITY,START_ADDR_M,END_ADDR_M,LRM_POSITION_ID,START_DATE,END_DATE,CREATED_BY,VALID_FROM,CALIBRATION_POINTS,FLOATING,GEOMETRY,VALID_TO)
//      values ($id2,110,2,0,5,0,160,$lrmPositionId2,to_date('01.09.12','RR.MM.DD'),null,'tr',to_date('01.09.12','RR.MM.DD'),2,0,MDSYS.SDO_GEOMETRY(4002,3067,NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1),MDSYS.SDO_ORDINATE_ARRAY(385258.765,7300119.103,0,0,384984.756,7300237.964,0,299)),null)""".execute

      val (roadAddress, roadSide) = transform.resolveAddressAndLocation(Point(0,0), 0, mValue, linkId, sideCode, road = Option(110))
      roadAddress.road should be(110)
      roadAddress.track should be(Track.Combined)
      roadSide should be(RoadSide.Left)
    }
  }
}
