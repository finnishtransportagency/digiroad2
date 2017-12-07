  package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.SideCode
import org.scalatest.{FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.dao.{Queries, Sequences}
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.Sequences
import slick.jdbc.StaticQuery.interpolation


class GeometryTransformSpec extends FunSuite with Matchers {

  val transform = new GeometryTransform()

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  test("Resolve location on left when asset SideCode different than AgainstDigitizing value") {
    val linkId = 1641830
    val mValue = 60
    val sideCode = 1

    runWithRollback {
      val id = Sequences.nextViitePrimaryKeySeqValue
      val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE)
    values ($lrmPositionId,null,2,10,298.694,null,1641830,0,to_timestamp('17.02.17 12:21:39','RR.MM.DD HH24:MI:SS'))""".execute

      sqlu"""Insert into ROAD_ADDRESS (ID,ROAD_NUMBER,ROAD_PART_NUMBER,TRACK_CODE,DISCONTINUITY,START_ADDR_M,END_ADDR_M,LRM_POSITION_ID,START_DATE,END_DATE,CREATED_BY,VALID_FROM,CALIBRATION_POINTS,FLOATING,GEOMETRY,VALID_TO)
    values ($id,921,2,0,5,0,299,$lrmPositionId,to_date('01.09.12','RR.MM.DD'),null,'tr',to_date('01.09.12','RR.MM.DD'),2,0,MDSYS.SDO_GEOMETRY(4002,3067,NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1),MDSYS.SDO_ORDINATE_ARRAY(385258.765,7300119.103,0,0,384984.756,7300237.964,0,299)),null)""".execute

      val (roadAddress, roadSide) =
        transform.resolveAddressAndLocation(Point(0,0), 0, mValue, linkId, sideCode)

      roadAddress.road should be(921)
      roadAddress.track should be(Track.Combined)
      roadAddress.mValue should be(247)
      roadSide should be(RoadSide.Left)
    }
  }

  test("Resolve location on left when asset SideCode equals to TowardsDigitizing value") {
    val linkId = 1641830
    val mValue = 60
    val sideCode = 3

    runWithRollback {
      val id = Sequences.nextViitePrimaryKeySeqValue
      val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE)
    values ($lrmPositionId,null,2,10,298.694,null,1641830,0,to_timestamp('17.02.17 12:21:39','RR.MM.DD HH24:MI:SS'))""".execute

      sqlu"""Insert into ROAD_ADDRESS (ID,ROAD_NUMBER,ROAD_PART_NUMBER,TRACK_CODE,DISCONTINUITY,START_ADDR_M,END_ADDR_M,LRM_POSITION_ID,START_DATE,END_DATE,CREATED_BY,VALID_FROM,CALIBRATION_POINTS,FLOATING,GEOMETRY,VALID_TO)
    values ($id,921,2,0,5,0,299,$lrmPositionId,to_date('01.09.12','RR.MM.DD'),null,'tr',to_date('01.09.12','RR.MM.DD'),2,0,MDSYS.SDO_GEOMETRY(4002,3067,NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1),MDSYS.SDO_ORDINATE_ARRAY(385258.765,7300119.103,0,0,384984.756,7300237.964,0,299)),null)""".execute

      val (roadAddress, roadSide) =
        transform.resolveAddressAndLocation(Point(0,0), 0, mValue, linkId, sideCode)

      roadAddress.road should be(921)
      roadAddress.track should be(Track.Combined)
      roadAddress.mValue should be(51)
      roadSide should be(RoadSide.Left)
    }
  }

  test("Resolve location on right when asset SideCode different than AgainstDigitizing value") {
    val linkId = 1641830
    val mValue = 60
    val sideCode = 1

    runWithRollback {
      val id = Sequences.nextViitePrimaryKeySeqValue
      val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE)
    values ($lrmPositionId,null,1,0,298.694,null,1641830,0,to_timestamp('17.02.17 12:21:39','RR.MM.DD HH24:MI:SS'))""".execute

      sqlu"""Insert into ROAD_ADDRESS (ID,ROAD_NUMBER,ROAD_PART_NUMBER,TRACK_CODE,DISCONTINUITY,START_ADDR_M,END_ADDR_M,LRM_POSITION_ID,START_DATE,END_DATE,CREATED_BY,VALID_FROM,CALIBRATION_POINTS,FLOATING,GEOMETRY,VALID_TO)
    values ($id,921,2,1,5,0,299,$lrmPositionId,to_date('01.09.12','RR.MM.DD'),null,'tr',to_date('01.09.12','RR.MM.DD'),2,0,MDSYS.SDO_GEOMETRY(4002,3067,NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1),MDSYS.SDO_ORDINATE_ARRAY(385258.765,7300119.103,0,0,384984.756,7300237.964,0,299)),null)""".execute

      val (roadAddress, roadSide) = transform.resolveAddressAndLocation(Point(0,0), 0, mValue, linkId, sideCode)
      roadAddress.road should be(921)
      roadAddress.track should be(Track.RightSide)
      roadAddress.mValue should be(238)
      roadSide should be(RoadSide.Right)
    }
  }

  test("Resolve location on right when asset SideCode equals to TowardsDigitizing value") {
    val linkId = 1641830
    val mValue = 60
    val sideCode = 2

    runWithRollback {
      val id = Sequences.nextViitePrimaryKeySeqValue
      val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE)
    values ($lrmPositionId,null,2,0,298.694,null,1641830,0,to_timestamp('17.02.17 12:21:39','RR.MM.DD HH24:MI:SS'))""".execute

      sqlu"""Insert into ROAD_ADDRESS (ID,ROAD_NUMBER,ROAD_PART_NUMBER,TRACK_CODE,DISCONTINUITY,START_ADDR_M,END_ADDR_M,LRM_POSITION_ID,START_DATE,END_DATE,CREATED_BY,VALID_FROM,CALIBRATION_POINTS,FLOATING,GEOMETRY,VALID_TO)
    values ($id,921,1,1,5,0,299,$lrmPositionId,to_date('01.09.12','RR.MM.DD'),null,'tr',to_date('01.09.12','RR.MM.DD'),2,0,MDSYS.SDO_GEOMETRY(4002,3067,NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1),MDSYS.SDO_ORDINATE_ARRAY(385258.765,7300119.103,0,0,384984.756,7300237.964,0,299)),null)""".execute

      val (roadAddress, roadSide) = transform.resolveAddressAndLocation(Point(0,0), 0, mValue, linkId, sideCode)
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
      val id = Sequences.nextViitePrimaryKeySeqValue
      val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
      val id2 = Sequences.nextViitePrimaryKeySeqValue
      val lrmPositionId2 = Sequences.nextLrmPositionPrimaryKeySeqValue

      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE)
      values ($lrmPositionId,null,2,1,298.694,null,1641830,0,to_timestamp('17.02.17 12:21:39','RR.MM.DD HH24:MI:SS'))""".execute

      sqlu"""Insert into ROAD_ADDRESS (ID,ROAD_NUMBER,ROAD_PART_NUMBER,TRACK_CODE,DISCONTINUITY,START_ADDR_M,END_ADDR_M,LRM_POSITION_ID,START_DATE,END_DATE,CREATED_BY,VALID_FROM,CALIBRATION_POINTS,FLOATING,GEOMETRY,VALID_TO)
      values ($id,110,2,0,5,0,299,$lrmPositionId,to_date('01.09.12','RR.MM.DD'),null,'tr',to_date('01.09.12','RR.MM.DD'),2,0,MDSYS.SDO_GEOMETRY(4002,3067,NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1),MDSYS.SDO_ORDINATE_ARRAY(385258.765,7300119.103,0,0,384984.756,7300237.964,0,299)),null)""".execute

      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE)
      values ($lrmPositionId2,null,2,1,150.690,null,1641830,0,to_timestamp('17.02.17 12:21:39','RR.MM.DD HH24:MI:SS'))""".execute

      sqlu"""Insert into ROAD_ADDRESS (ID,ROAD_NUMBER,ROAD_PART_NUMBER,TRACK_CODE,DISCONTINUITY,START_ADDR_M,END_ADDR_M,LRM_POSITION_ID,START_DATE,END_DATE,CREATED_BY,VALID_FROM,CALIBRATION_POINTS,FLOATING,GEOMETRY,VALID_TO)
      values ($id2,110,2,0,5,0,160,$lrmPositionId2,to_date('01.09.12','RR.MM.DD'),null,'tr',to_date('01.09.12','RR.MM.DD'),2,0,MDSYS.SDO_GEOMETRY(4002,3067,NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1),MDSYS.SDO_ORDINATE_ARRAY(385258.765,7300119.103,0,0,384984.756,7300237.964,0,299)),null)""".execute

      val (roadAddress, roadSide) = transform.resolveAddressAndLocation(Point(0,0), 0, mValue, linkId, sideCode, road = Option(110))
      roadAddress.road should be(110)
      roadAddress.track should be(Track.Combined)
      roadSide should be(RoadSide.Left)
    }
  }

  test("Resolve road address -> coordinate") {
    runWithRollback {
      val id = Sequences.nextViitePrimaryKeySeqValue
      val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
      val id2 = Sequences.nextViitePrimaryKeySeqValue
      val lrmPositionId2 = Sequences.nextLrmPositionPrimaryKeySeqValue
      val id3 = Sequences.nextViitePrimaryKeySeqValue
      val lrmPositionId3 = Sequences.nextLrmPositionPrimaryKeySeqValue
      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE)
               values ($lrmPositionId,null,2,0,15,null,3714864,0,to_timestamp('17.02.17 12:19:45','RR.MM.DD HH24:MI:SS'))""".execute
      sqlu"""Insert into ROAD_ADDRESS (ID,ROAD_NUMBER,ROAD_PART_NUMBER,TRACK_CODE,DISCONTINUITY,START_ADDR_M,END_ADDR_M,LRM_POSITION_ID,START_DATE,END_DATE,CREATED_BY,VALID_FROM,CALIBRATION_POINTS,FLOATING,GEOMETRY,VALID_TO)
               values ($id,20,1,0,5,0,20,$lrmPositionId,to_date('56.01.01','RR.MM.DD'),null,'tr',to_date('98.10.16','RR.MM.DD'),0,0,MDSYS.SDO_GEOMETRY(4002,3067,NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1),MDSYS.SDO_ORDINATE_ARRAY(0,0,0,0,0,20,0,0)),null)""".execute

      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE)
               values ($lrmPositionId2,null,2,0,6,null,3710726,0,to_timestamp('17.02.17 12:19:45','RR.MM.DD HH24:MI:SS'))""".execute
      sqlu"""Insert into ROAD_ADDRESS (ID,ROAD_NUMBER,ROAD_PART_NUMBER,TRACK_CODE,DISCONTINUITY,START_ADDR_M,END_ADDR_M,LRM_POSITION_ID,START_DATE,END_DATE,CREATED_BY,VALID_FROM,CALIBRATION_POINTS,FLOATING,GEOMETRY,VALID_TO)
               values ($id2,20,2,0,5,20,30,$lrmPositionId2,to_date('60.01.01','RR.MM.DD'),null,'tr',to_date('98.10.16','RR.MM.DD'),0,0,MDSYS.SDO_GEOMETRY(4002,3067,NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1),MDSYS.SDO_ORDINATE_ARRAY(0,20,0,0,0,30,0,0)),null)""".execute

      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE)
               values ($lrmPositionId3,null,2,0,8,null,3714455,0,to_timestamp('17.02.17 12:19:45','RR.MM.DD HH24:MI:SS'))""".execute
      sqlu"""Insert into ROAD_ADDRESS (ID,ROAD_NUMBER,ROAD_PART_NUMBER,TRACK_CODE,DISCONTINUITY,START_ADDR_M,END_ADDR_M,LRM_POSITION_ID,START_DATE,END_DATE,CREATED_BY,VALID_FROM,CALIBRATION_POINTS,FLOATING,GEOMETRY,VALID_TO)
                values ($id3,20,3,0,5,30,40,$lrmPositionId3,to_date('60.01.01','RR.MM.DD'),null,'tr',to_date('98.10.16','RR.MM.DD'),0,0,MDSYS.SDO_GEOMETRY(4002,3067,NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1),MDSYS.SDO_ORDINATE_ARRAY(0,30,0,0,0,40,0,0)),null)""".execute

      val address = RoadAddress(None, 20, 2, Track.Combined, 22, None)
      val coord = transform.addressToCoords(address.road, address.roadPart, address.track, address.mValue)

      coord.size should be(1)
      coord.head.x should be(0.0)
      coord.head.y should be(22.0)
    }
  }

  test("Resolve address and location from Viite"){
    runWithRollback{
      val lrmPositionsIds = Queries.fetchLrmPositionIds(4)
      val roadAddressId1 = Queries.nextViitePrimaryKeyId.as[Long].first
      val roadAddressId2 = Queries.nextViitePrimaryKeyId.as[Long].first
      val roadAddressId3 = Queries.nextViitePrimaryKeyId.as[Long].first
      val roadAddressId4 = Queries.nextViitePrimaryKeyId.as[Long].first

      sqlu"""insert into lrm_position(id, side_code, link_id, mml_id, start_measure, end_measure) VALUES (${lrmPositionsIds(0)}, 2, 6000, null, 0.000, 100.000)""".execute
      sqlu"""insert into lrm_position(id, side_code, link_id, mml_id, start_measure, end_measure) VALUES (${lrmPositionsIds(1)}, 2, 6000, null, 100.000, 200.000)""".execute
      sqlu"""insert into lrm_position(id, side_code, link_id, mml_id, start_measure, end_measure) VALUES (${lrmPositionsIds(2)}, 2, 6000, null, 200.000, 350.000)""".execute
      sqlu"""insert into lrm_position(id, side_code, link_id, mml_id, start_measure, end_measure) VALUES (${lrmPositionsIds(3)}, 2, 6000, null, 350.000, 500.000)""".execute

      sqlu"""insert into road_address(id, road_number, road_part_number, track_code, discontinuity, start_addr_m, end_addr_m, lrm_position_id,
                                     start_date, end_date, created_by, valid_from, calibration_points, floating, geometry, valid_to)
             values ($roadAddressId1, 100, 1, 0, 5, 0, 150, ${lrmPositionsIds(0)}, to_date('15.01.01','RR.MM.DD'), NULL, 'test', to_date('15.01.01','RR.MM.DD'), 0, '0'
                    , MDSYS.SDO_GEOMETRY(4002,3067,NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1),MDSYS.SDO_ORDINATE_ARRAY(534718.983,6980666.786,0,0,534857.168,6980825.352,0,212))
                    , NULL)""".execute

      sqlu"""insert into road_address(id, road_number, road_part_number, track_code, discontinuity, start_addr_m, end_addr_m, lrm_position_id,
                                      start_date, end_date, created_by, valid_from, calibration_points, floating, geometry, valid_to)
              values ($roadAddressId2, 101, 1, 0, 5, 150, 250, ${lrmPositionsIds(1)}, to_date('15.01.01','RR.MM.DD'), NULL, 'test', to_date('15.01.01','RR.MM.DD'), 0, '0'
                      , MDSYS.SDO_GEOMETRY(4002,3067,NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1),MDSYS.SDO_ORDINATE_ARRAY(534718.983,6980666.786,0,0,534857.168,6980825.352,0,212))
                      , NULL)""".execute

      sqlu"""insert into road_address(id, road_number, road_part_number, track_code, discontinuity, start_addr_m, end_addr_m, lrm_position_id,
                                      start_date, end_date, created_by, valid_from, calibration_points, floating, geometry, valid_to)
             values ($roadAddressId3, 102, 1, 0, 5, 250, 400, ${lrmPositionsIds(2)}, to_date('15.01.01','RR.MM.DD'), NULL, 'test', to_date('15.01.01','RR.MM.DD'), 0, '0'
                     , MDSYS.SDO_GEOMETRY(4002,3067,NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1),MDSYS.SDO_ORDINATE_ARRAY(534718.983,6980666.786,0,0,534857.168,6980825.352,0,212))
                     , NULL)""".execute

      sqlu"""insert into road_address(id, road_number, road_part_number, track_code, discontinuity, start_addr_m, end_addr_m, lrm_position_id,
                                      start_date, end_date, created_by, valid_from, calibration_points, floating, geometry, valid_to)
             values ($roadAddressId4, 103, 1, 0, 5, 400, 550, ${lrmPositionsIds(3)}, to_date('15.01.01','RR.MM.DD'), NULL, 'test', to_date('15.01.01','RR.MM.DD'), 0, '0'
                     , MDSYS.SDO_GEOMETRY(4002,3067,NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1),MDSYS.SDO_ORDINATE_ARRAY(534718.983,6980666.786,0,0,534857.168,6980825.352,0,212))
                     , NULL)""".execute

      val roadAddress = transform.resolveAddressAndLocation(6000, 0, 500, SideCode.BothDirections)
      roadAddress.length should be(4)
      roadAddress.map( _.id ).sorted should be (Seq (roadAddressId1, roadAddressId2, roadAddressId3, roadAddressId4).sorted)

    }
  }

  test("Resolve address and location from Viite with road address with exceeding lrm_position bounds "){
    runWithRollback{
      val lrmPositionsIds = Queries.fetchLrmPositionIds(1)
      val roadAddressId = Queries.nextViitePrimaryKeyId.as[Long].first

      sqlu"""insert into lrm_position(id, link_id, mml_id, start_measure, end_measure) VALUES (${lrmPositionsIds(0)}, 6000, null, 100.000, 250.000)""".execute

      sqlu"""insert into road_address(id, road_number, road_part_number, track_code, discontinuity, start_addr_m, end_addr_m, lrm_position_id,
                                     start_date, end_date, created_by, valid_from, calibration_points, floating, geometry, valid_to)
             values ($roadAddressId, 100, 1, 0, 5, 0, 150, ${lrmPositionsIds(0)}, to_date('15.01.01','RR.MM.DD'), NULL, 'test', to_date('15.01.01','RR.MM.DD'), 0, '0'
                    , MDSYS.SDO_GEOMETRY(4002,3067,NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1),MDSYS.SDO_ORDINATE_ARRAY(534718.983,6980666.786,0,0,534857.168,6980825.352,0,212))
                    , NULL)""".execute

      val roadAddress = transform.resolveAddressAndLocation(6000, 0, 500, SideCode.Unknown)
      roadAddress.length should be(1)
      roadAddress.map( _.id ).sorted should be (Seq (roadAddressId))

    }
  }

  test("Resolve address and location from Viite with road address with lrm_position measure bigger"){
    runWithRollback{
      val lrmPositionsIds = Queries.fetchLrmPositionIds(1)
      val roadAddressId = Queries.nextViitePrimaryKeyId.as[Long].first

      sqlu"""insert into lrm_position(id, link_id, mml_id, start_measure, end_measure) VALUES (${lrmPositionsIds(0)}, 6000, null, 0.000, 250.000)""".execute

      sqlu"""insert into road_address(id, road_number, road_part_number, track_code, discontinuity, start_addr_m, end_addr_m, lrm_position_id,
                                     start_date, end_date, created_by, valid_from, calibration_points, floating, geometry, valid_to)
             values ($roadAddressId, 100, 1, 0, 5, 150, 500, ${lrmPositionsIds(0)}, to_date('15.01.01','RR.MM.DD'), NULL, 'test', to_date('15.01.01','RR.MM.DD'), 0, '0'
                    , MDSYS.SDO_GEOMETRY(4002,3067,NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1),MDSYS.SDO_ORDINATE_ARRAY(534718.983,6980666.786,0,0,534857.168,6980825.352,0,212))
                    , NULL)""".execute

      val roadAddress = transform.resolveAddressAndLocation(6000, 0, 500, SideCode.Unknown)
      roadAddress.length should be(1)
      roadAddress.map( _.id ).sorted should be (Seq (roadAddressId))

    }
  }

  test("Resolve address and location from Viite with no matching side code "){
    runWithRollback{
      val lrmPositionsIds = Queries.fetchLrmPositionIds(1)
      val roadAddressId = Queries.nextViitePrimaryKeyId.as[Long].first
      //Side Code TowardsDigitizing
      sqlu"""insert into lrm_position(id, side_code, link_id, mml_id, start_measure, end_measure) VALUES (${lrmPositionsIds(0)}, 2, 6000, null, 100.000, 250.000)""".execute

      sqlu"""insert into road_address(id, road_number, road_part_number, track_code, discontinuity, start_addr_m, end_addr_m, lrm_position_id,
                                     start_date, end_date, created_by, valid_from, calibration_points, floating, geometry, valid_to)
             values ($roadAddressId, 100, 1, 0, 5, 0, 150, ${lrmPositionsIds(0)}, to_date('15.01.01','RR.MM.DD'), NULL, 'test', to_date('15.01.01','RR.MM.DD'), 0, '0'
                    , MDSYS.SDO_GEOMETRY(4002,3067,NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1),MDSYS.SDO_ORDINATE_ARRAY(534718.983,6980666.786,0,0,534857.168,6980825.352,0,212))
                    , NULL)""".execute

      val roadAddress = transform.resolveAddressAndLocation(6000, 0, 500, SideCode.AgainstDigitizing)
      roadAddress.length should be(0)

    }
  }

  test("Resolve address and location from Viite with matching side code "){
    runWithRollback{
      val lrmPositionsIds = Queries.fetchLrmPositionIds(1)
      val roadAddressId = Queries.nextViitePrimaryKeyId.as[Long].first
      //Side Code TowardsDigitizing
      sqlu"""insert into lrm_position(id, side_code, link_id, mml_id, start_measure, end_measure) VALUES (${lrmPositionsIds(0)}, 2, 6000, null, 100.000, 250.000)""".execute

      sqlu"""insert into road_address(id, road_number, road_part_number, track_code, discontinuity, start_addr_m, end_addr_m, lrm_position_id,
                                     start_date, end_date, created_by, valid_from, calibration_points, floating, geometry, valid_to)
             values ($roadAddressId, 100, 1, 0, 5, 0, 150, ${lrmPositionsIds(0)}, to_date('15.01.01','RR.MM.DD'), NULL, 'test', to_date('15.01.01','RR.MM.DD'), 0, '0'
                    , MDSYS.SDO_GEOMETRY(4002,3067,NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1),MDSYS.SDO_ORDINATE_ARRAY(534718.983,6980666.786,0,0,534857.168,6980825.352,0,212))
                    , NULL)""".execute

      val roadAddress = transform.resolveAddressAndLocation(6000, 0, 500, SideCode.TowardsDigitizing)
      roadAddress.length should be(1)
      roadAddress.map( _.id ).sorted should be (Seq (roadAddressId))

    }
  }
}
