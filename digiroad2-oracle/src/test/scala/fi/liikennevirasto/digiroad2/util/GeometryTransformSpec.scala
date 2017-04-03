package fi.liikennevirasto.digiroad2.util

import org.apache.http.conn.{ConnectTimeoutException, HttpHostConnectException}
import org.scalatest.{FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.driver.JdbcDriver.backend.Database

class GeometryTransformSpec extends FunSuite with Matchers {

  val transform = new GeometryTransform()

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  test("Resolve location on left") {
    val linkId = 1641830
    val mValue = 60
    val sideCode = 1

    runWithRollback {
      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE)
    values (5400000,null,2,10,298.694,null,1641830,0,to_timestamp('17.02.17 12:21:39,227710000','RR.MM.DD HH24:MI:SSXFF'))""".execute

      sqlu"""Insert into ROAD_ADDRESS (ID,ROAD_NUMBER,ROAD_PART_NUMBER,TRACK_CODE,DISCONTINUITY,START_ADDR_M,END_ADDR_M,LRM_POSITION_ID,START_DATE,END_DATE,CREATED_BY,VALID_FROM,CALIBRATION_POINTS,FLOATING,GEOMETRY,VALID_TO)
    values (7000000,921,2,0,5,0,299,5400000,to_date('01.09.12','RR.MM.DD'),null,'tr',to_date('01.09.12','RR.MM.DD'),'2','0',MDSYS.SDO_GEOMETRY(4002,3067,NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1),MDSYS.SDO_ORDINATE_ARRAY(385258.765,7300119.103,0,0,384984.756,7300237.964,0,299)),null)""".execute

      val (roadAddress, roadSide) =
        transform.resolveAddressAndLocation(mValue, linkId, sideCode)

      roadAddress.road should be(921)
      roadAddress.track should be(Track.Combined)
      roadAddress.mValue should be(50)
      roadSide should be(RoadSide.Left)
    }
  }

  test("Resolve location on right") {
    val linkId = 1641830
    val mValue = 60
    val sideCode = 1

    runWithRollback {
      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE)
    values (5400000,null,1,0,298.694,null,1641830,0,to_timestamp('17.02.17 12:21:39,227710000','RR.MM.DD HH24:MI:SSXFF'))""".execute

      sqlu"""Insert into ROAD_ADDRESS (ID,ROAD_NUMBER,ROAD_PART_NUMBER,TRACK_CODE,DISCONTINUITY,START_ADDR_M,END_ADDR_M,LRM_POSITION_ID,START_DATE,END_DATE,CREATED_BY,VALID_FROM,CALIBRATION_POINTS,FLOATING,GEOMETRY,VALID_TO)
    values (7000000,921,2,1,5,0,299,5400000,to_date('01.09.12','RR.MM.DD'),null,'tr',to_date('01.09.12','RR.MM.DD'),'2','0',MDSYS.SDO_GEOMETRY(4002,3067,NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1),MDSYS.SDO_ORDINATE_ARRAY(385258.765,7300119.103,0,0,384984.756,7300237.964,0,299)),null)""".execute

      val (roadAddress, roadSide) = transform.resolveAddressAndLocation(mValue, linkId, sideCode)
      roadAddress.road should be(921)
      roadAddress.track should be(Track.RightSide)
      roadAddress.mValue should be(60)
      roadSide should be(RoadSide.Right)
    }
  }

  test("Resolve location on two-way road") {
    val linkId = 1641830
    val mValue = 11
    val sideCode = 0

    runWithRollback {
      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE)
      values ('5400000',null,'2','1','298,694',null,'1641830','0',to_timestamp('17.02.17 12:21:39,227710000','RR.MM.DD HH24:MI:SSXFF'))""".execute

      sqlu"""Insert into ROAD_ADDRESS (ID,ROAD_NUMBER,ROAD_PART_NUMBER,TRACK_CODE,DISCONTINUITY,START_ADDR_M,END_ADDR_M,LRM_POSITION_ID,START_DATE,END_DATE,CREATED_BY,VALID_FROM,CALIBRATION_POINTS,FLOATING,GEOMETRY,VALID_TO)
      values ('7000000','110','2','0','5','0','299','5400000',to_date('01.09.12','RR.MM.DD'),null,'tr',to_date('01.09.12','RR.MM.DD'),'2','0',MDSYS.SDO_GEOMETRY(4002,3067,NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1),MDSYS.SDO_ORDINATE_ARRAY(385258.765,7300119.103,0,0,384984.756,7300237.964,0,299)),null)""".execute

      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE)
      values ('5400005',null,'2','1','150,690',null,'1641830','0',to_timestamp('17.02.17 12:21:39,227710000','RR.MM.DD HH24:MI:SSXFF'))""".execute

      sqlu"""Insert into ROAD_ADDRESS (ID,ROAD_NUMBER,ROAD_PART_NUMBER,TRACK_CODE,DISCONTINUITY,START_ADDR_M,END_ADDR_M,LRM_POSITION_ID,START_DATE,END_DATE,CREATED_BY,VALID_FROM,CALIBRATION_POINTS,FLOATING,GEOMETRY,VALID_TO)
      values ('8000000','110','2','0','5','0','160','5400005',to_date('01.09.12','RR.MM.DD'),null,'tr',to_date('01.09.12','RR.MM.DD'),'2','0',MDSYS.SDO_GEOMETRY(4002,3067,NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1),MDSYS.SDO_ORDINATE_ARRAY(385258.765,7300119.103,0,0,384984.756,7300237.964,0,299)),null)""".execute

      val (roadAddress, roadSide) = transform.resolveAddressAndLocation(mValue, linkId, sideCode, road = Option(110))
      roadAddress.road should be(110)
      roadAddress.track should be(Track.Combined)
      roadSide should be(RoadSide.Left)
    }
  }

  test("Resolve road address -> coordinate") {
    runWithRollback {
        sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE)
               values ('500000',null,'2','0','15',null,'3714864','0',to_timestamp('17.02.17 12:19:45,763718000','RR.MM.DD HH24:MI:SSXFF'))""".execute
        sqlu"""Insert into ROAD_ADDRESS (ID,ROAD_NUMBER,ROAD_PART_NUMBER,TRACK_CODE,DISCONTINUITY,START_ADDR_M,END_ADDR_M,LRM_POSITION_ID,START_DATE,END_DATE,CREATED_BY,VALID_FROM,CALIBRATION_POINTS,FLOATING,GEOMETRY,VALID_TO)
               values ('7000001','20','1','0','5','0','20','500000',to_date('56.01.01','RR.MM.DD'),null,'tr',to_date('98.10.16','RR.MM.DD'),'0','0',MDSYS.SDO_GEOMETRY(4002,3067,NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1),MDSYS.SDO_ORDINATE_ARRAY(0,0,0,0,0,20,0,0)),null)""".execute

        sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE)
               values ('500001',null,'2','0','6',null,'3710726','0',to_timestamp('17.02.17 12:19:45,763718000','RR.MM.DD HH24:MI:SSXFF'))""".execute
        sqlu"""Insert into ROAD_ADDRESS (ID,ROAD_NUMBER,ROAD_PART_NUMBER,TRACK_CODE,DISCONTINUITY,START_ADDR_M,END_ADDR_M,LRM_POSITION_ID,START_DATE,END_DATE,CREATED_BY,VALID_FROM,CALIBRATION_POINTS,FLOATING,GEOMETRY,VALID_TO)
               values ('7000002','20','2','0','5','20','30','500001',to_date('60.01.01','RR.MM.DD'),null,'tr',to_date('98.10.16','RR.MM.DD'),'0','0',MDSYS.SDO_GEOMETRY(4002,3067,NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1),MDSYS.SDO_ORDINATE_ARRAY(0,20,0,0,0,30,0,0)),null)""".execute

        sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE)
               values ('500002',null,'2','0','8',null,'3714455','0',to_timestamp('17.02.17 12:19:45,763718000','RR.MM.DD HH24:MI:SSXFF'))""".execute
        sqlu"""Insert into ROAD_ADDRESS (ID,ROAD_NUMBER,ROAD_PART_NUMBER,TRACK_CODE,DISCONTINUITY,START_ADDR_M,END_ADDR_M,LRM_POSITION_ID,START_DATE,END_DATE,CREATED_BY,VALID_FROM,CALIBRATION_POINTS,FLOATING,GEOMETRY,VALID_TO)
                values ('7000003','20','3','0','5','30','40','500002',to_date('60.01.01','RR.MM.DD'),null,'tr',to_date('98.10.16','RR.MM.DD'),'0','0',MDSYS.SDO_GEOMETRY(4002,3067,NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1),MDSYS.SDO_ORDINATE_ARRAY(0,30,0,0,0,40,0,0)),null)""".execute

        val address = RoadAddress(None, 20, 2, Track.Combined, 22, None)
        val coord = transform.addressToCoords(address.road, address.roadPart, address.track, address.mValue)

        coord.size should be(1)
        coord.head.x should be(0.0)
        coord.head.y should be(22.0)
    }
  }
}
