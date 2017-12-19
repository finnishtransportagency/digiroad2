package fi.liikennevirasto.viite.dao

import java.sql.SQLException

import com.github.tototoshi.slick.MySQLJodaSupport._
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.ComplimentaryLinkInterface
import fi.liikennevirasto.digiroad2.asset.{BoundingRectangle, LinkGeomSource, SideCode}
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.Sequences
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, Point, RoadLinkService}
import fi.liikennevirasto.viite.dao.Discontinuity.Discontinuous
import fi.liikennevirasto.viite.{ReservedRoadPart, RoadAddressMerge, RoadAddressService, RoadType}
import org.joda.time.DateTime
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{StaticQuery => Q}

import scala.util.control.NonFatal


/**
  * Created by venholat on 12.9.2016.
  */
class RoadAddressDAOSpec extends FunSuite with Matchers {

  def runWithRollback(f: => Unit): Unit = {
    Database.forDataSource(OracleDatabase.ds).withDynTransaction {
      f
      dynamicSession.rollback()
    }
  }

  test("insert roadaddress duplicate info check") {
      runWithRollback {
        val error = intercept[SQLException] {
        sqlu"""
      Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE,LINK_SOURCE) values (lrm_position_primary_key_seq.nextval,null,2,0,21.021, null,1111102483,1476392565000,to_timestamp('17.09.15 19:39:30','RR.MM.DD HH24:MI:SS,FF'),1)""".execute
        sqlu""" Insert into ROAD_ADDRESS (ID,ROAD_NUMBER,ROAD_PART_NUMBER,TRACK_CODE,DISCONTINUITY,START_ADDR_M,END_ADDR_M,LRM_POSITION_ID,START_DATE,END_DATE,CREATED_BY,VALID_FROM,CALIBRATION_POINTS,FLOATING,GEOMETRY,VALID_TO, ROAD_TYPE, ELY) values (viite_general_seq.nextval,1010,1,0,5,627,648,lrm_position_primary_key_seq.currval,to_date('63.01.01','RR.MM.DD'),null,'tr',to_date('98.10.16','RR.MM.DD'),0,0,MDSYS.SDO_GEOMETRY(4002,3067,NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1),MDSYS.SDO_ORDINATE_ARRAY(288781.428,6825565.909,0,0,288763.118,6825576.235,0,21)),null, 1, 4)""".execute
        sqlu"""
      Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE,LINK_SOURCE) values (lrm_position_primary_key_seq.nextval,null,2,0,21.021,null,1111102483,1476392565000,to_timestamp('17.09.15 19:39:30','RR.MM.DD HH24:MI:SS,FF'),1)""".execute
        sqlu""" Insert into ROAD_ADDRESS (ID,ROAD_NUMBER,ROAD_PART_NUMBER,TRACK_CODE,DISCONTINUITY,START_ADDR_M,END_ADDR_M,LRM_POSITION_ID,START_DATE,END_DATE,CREATED_BY,VALID_FROM,CALIBRATION_POINTS,FLOATING,GEOMETRY,VALID_TO, ROAD_TYPE, ELY) values (viite_general_seq.nextval,1010,1,0,5,627,648,lrm_position_primary_key_seq.currval,to_date('63.01.01','RR.MM.DD'),null,'tr',to_date('98.10.16','RR.MM.DD'),0,0,MDSYS.SDO_GEOMETRY(4002,3067,NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1),MDSYS.SDO_ORDINATE_ARRAY(288781.428,6825565.909,0,0,288763.118,6825576.235,0,21)),null, 1, 4)""".execute
        }
        error.getErrorCode should be (1)
      }
  }


  test("insert roadaddress m-values overlap") {
    runWithRollback {
      val error = intercept[SQLException] {
        sqlu"""
      Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE,LINK_SOURCE) values (lrm_position_primary_key_seq.nextval,null,2,0,21.021,null,1111102483,1476392565000,to_timestamp('17.09.15 19:39:30','RR.MM.DD HH24:MI:SS,FF'),1)""".execute
        sqlu""" Insert into ROAD_ADDRESS (ID,ROAD_NUMBER,ROAD_PART_NUMBER,TRACK_CODE,DISCONTINUITY,START_ADDR_M,END_ADDR_M,LRM_POSITION_ID,START_DATE,END_DATE,CREATED_BY,VALID_FROM,CALIBRATION_POINTS,FLOATING,GEOMETRY,VALID_TO, ROAD_TYPE, ELY) values (viite_general_seq.nextval,1010,1,0,5,627,648,lrm_position_primary_key_seq.currval,to_date('63.01.01','RR.MM.DD'),null,'tr',to_date('98.10.16','RR.MM.DD'),0,0,MDSYS.SDO_GEOMETRY(4002,3067,NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1),MDSYS.SDO_ORDINATE_ARRAY(288781.428,6825565.909,0,0,288763.118,6825576.235,0,21)),null, 1, 4)""".execute
        sqlu"""
      Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE,LINK_SOURCE) values (lrm_position_primary_key_seq.nextval,null,2,0,21.021,null,1111102483,1476392565000,to_timestamp('17.09.15 19:39:30','RR.MM.DD HH24:MI:SS,FF'),1)""".execute
        sqlu""" Insert into ROAD_ADDRESS (ID,ROAD_NUMBER,ROAD_PART_NUMBER,TRACK_CODE,DISCONTINUITY,START_ADDR_M,END_ADDR_M,LRM_POSITION_ID,START_DATE,END_DATE,CREATED_BY,VALID_FROM,CALIBRATION_POINTS,FLOATING,GEOMETRY,VALID_TO, ROAD_TYPE, ELY) values (viite_general_seq.nextval,1010,1,0,5,627,648,lrm_position_primary_key_seq.currval,to_date('63.01.01','RR.MM.DD'),null,'tr',to_date('98.10.16','RR.MM.DD'),0,0,MDSYS.SDO_GEOMETRY(4012,3057,NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1),MDSYS.SDO_ORDINATE_ARRAY(288781.428,6825565.909,0,0,288763.118,6825576.235,0,21)),null, 1, 4)""".execute
      }
      error.getErrorCode should be(29875)
    }
  }


  test("testFetchByRoadPart") {
    runWithRollback {
      RoadAddressDAO.fetchByRoadPart(5L, 201L).isEmpty should be(false)
    }
  }

  test("testFetchByLinkId") {
    runWithRollback {
      val sets = RoadAddressDAO.fetchByLinkId(Set(5170942, 5170947))
      sets.size should be (2)
      sets.forall(_.floating == false) should be (true)
    }
  }

  test("Get valid road numbers") {
    runWithRollback {
      val numbers = RoadAddressDAO.getCurrentValidRoadNumbers()
      numbers.isEmpty should be(false)
      numbers should contain(5L)
    }
  }

  test("Get valid road part numbers") {
    runWithRollback {
      val numbers = RoadAddressDAO.getValidRoadParts(5L)
      numbers.isEmpty should be(false)
      numbers should contain(201L)
    }
  }

  test("Update without geometry") {
    runWithRollback {
      val address = RoadAddressDAO.fetchByLinkId(Set(5170942)).head
      RoadAddressDAO.update(address)
    }
  }

  test("Updating a geometry is executed in SQL server") {
    runWithRollback {
      val address = RoadAddressDAO.fetchByLinkId(Set(5170942)).head
      RoadAddressDAO.update(address, Some(Seq(Point(50200, 7630000.0, 0.0), Point(50210, 7630000.0, 10.0))))
      RoadAddressDAO.fetchRoadAddressesByBoundingBox(BoundingRectangle(Point(50202, 7620000), Point(50205, 7640000)), false).exists(_.id == address.id) should be (true)
      RoadAddressDAO.fetchRoadAddressesByBoundingBox(BoundingRectangle(Point(50212, 7620000), Point(50215, 7640000)), false).exists(_.id == address.id) should be (false)
    }
  }

  test("Fetch missing road address by boundingBox"){
    runWithRollback {
      val boundingBox = BoundingRectangle(Point(6699381, 396898), Point(6699382, 396898))
      sqlu"""
           insert into missing_road_address (link_id, start_addr_m, end_addr_m,anomaly_code, start_m, end_m, geometry)
           values (1943845, 0, 1, 1, 0, 34.944, MDSYS.SDO_GEOMETRY(4002, 3067, NULL, MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1), MDSYS.SDO_ORDINATE_ARRAY(6699381,396898,0,0.0,6699382,396898,0,2)))
           """.execute

      val missingRoadAddresses = RoadAddressDAO.fetchMissingRoadAddressesByBoundingBox(boundingBox)
      val addedValue = missingRoadAddresses.find(p => p.linkId == 1943845).get
      addedValue should not be None
      addedValue.geom.nonEmpty should be (true)
      addedValue.startAddrMValue.get should be (0)
      addedValue.endAddrMValue.get should be (1)
    }
  }

  test("Set road address to floating and update the geometry as well") {
    runWithRollback {
      val address = RoadAddressDAO.fetchByLinkId(Set(5170942)).head
      RoadAddressDAO.changeRoadAddressFloating(true, address.id, Some(Seq(Point(50200, 7630000.0, 0.0), Point(50210, 7630000.0, 10.0))))
    }
  }

  test("Create Road Address") {
    runWithRollback {
      val id = RoadAddressDAO.getNextRoadAddressId
      val ra = Seq(RoadAddress(id, 1943845, 1, RoadType.Unknown, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), None, Option("tester"), 0, 12345L, 0.0, 9.8, SideCode.TowardsDigitizing, 0, (None, None), false,
        Seq(Point(0.0, 0.0), Point(0.0, 9.8)), LinkGeomSource.NormalLinkInterface, 8))
      val currentSize = RoadAddressDAO.fetchByRoadPart(ra.head.roadNumber, ra.head.roadPartNumber).size
      val returning = RoadAddressDAO.create(ra)
      returning.nonEmpty should be (true)
      returning.head should be (id)
      val newSize = currentSize + 1
      RoadAddressDAO.fetchByRoadPart(ra.head.roadNumber, ra.head.roadPartNumber) should have size(newSize)
    }
  }

  test("Adding geometry to missing roadaddress") {
    runWithRollback {
      val id = 1943845
      sqlu"""
           insert into missing_road_address (link_id, start_addr_m, end_addr_m,anomaly_code, start_m)
           values ($id, 0, 1, 1, 1)
           """.execute
      sqlu"""UPDATE MISSING_ROAD_ADDRESS
        SET geometry= MDSYS.SDO_GEOMETRY(4002, 3067, NULL, MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1), MDSYS.SDO_ORDINATE_ARRAY(
             6699381,396898,0,0.0,6699382,396898,0,2))
        WHERE link_id = ${id}""".execute
      val query= s"""select Count(geometry)
                 from missing_road_address ra
                 WHERE ra.link_id=$id AND geometry IS NOT NULL
      """
      Q.queryNA[Int](query).firstOption should be (Some(1))
    }
  }

  test("Create Road Address with username") {
    runWithRollback {
      val username = "testUser"
      val id = RoadAddressDAO.getNextRoadAddressId
      val ra = Seq(RoadAddress(id, 1943845, 1, RoadType.Unknown, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), None, Option("tester"), 0, 12345L, 0.0, 9.8, SideCode.TowardsDigitizing, 0, (None, None), false,
        Seq(Point(0.0, 0.0), Point(0.0, 9.8)), LinkGeomSource.NormalLinkInterface, 8))
      val currentSize = RoadAddressDAO.fetchByRoadPart(ra.head.roadNumber, ra.head.roadPartNumber).size
      val returning = RoadAddressDAO.create(ra, Some(username))
      returning.nonEmpty should be (true)
      returning.head should be (id)
      val newSize = currentSize + 1
      val roadAddress = RoadAddressDAO.fetchByRoadPart(ra.head.roadNumber, ra.head.roadPartNumber)
      roadAddress should have size(newSize)
      roadAddress.head.modifiedBy.get should be (username)
    }
  }

  test("Create Road Address With Calibration Point") {
    runWithRollback {
      val id = RoadAddressDAO.getNextRoadAddressId
      val ra = Seq(RoadAddress(id, 1943845, 1, RoadType.Unknown, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), None, Option("tester"),0, 12345L, 0.0, 9.8, SideCode.TowardsDigitizing, 0,
        (Some(CalibrationPoint(12345L, 0.0, 0L)), None), false,
        Seq(Point(0.0, 0.0), Point(0.0, 9.8)), LinkGeomSource.NormalLinkInterface, 8))
      val returning = RoadAddressDAO.create(ra)
      returning.nonEmpty should be (true)
      returning.head should be (id)
      val fetch = sql"""select calibration_points from road_address where id = $id""".as[Int].list
      fetch.head should be (2)
    }
    runWithRollback {
      val id = RoadAddressDAO.getNextRoadAddressId
      val ra = Seq(RoadAddress(id, 1943845, 1, RoadType.Unknown, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), None, Option("tester"),0, 12345L, 0.0, 9.8, SideCode.TowardsDigitizing, 0,
        (Some(CalibrationPoint(12345L, 0.0, 0L)), Some(CalibrationPoint(12345L, 9.8, 10L))), false,
        Seq(Point(0.0, 0.0), Point(0.0, 9.8)), LinkGeomSource.NormalLinkInterface, 8))
      val returning = RoadAddressDAO.create(ra)
      returning.nonEmpty should be (true)
      returning.head should be (id)
      val fetch = sql"""select calibration_points from road_address where id = $id""".as[Int].list
      fetch.head should be (3)
    }
  }

  test("Create Road Address with complementary source") {
    runWithRollback {
      val id = RoadAddressDAO.getNextRoadAddressId
      val ra = Seq(RoadAddress(id, 1943845, 1, RoadType.Unknown, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")),
        None, Option("tester"), 0, 12345L, 0.0, 9.8, SideCode.TowardsDigitizing, 0, (None, None), false,
        Seq(Point(0.0, 0.0), Point(0.0, 9.8)), LinkGeomSource.ComplimentaryLinkInterface, 8))
      val returning = RoadAddressDAO.create(ra)
      returning.nonEmpty should be (true)
      returning.head should be (id)
      sql"""SELECT link_source FROM ROAD_ADDRESS ra JOIN LRM_POSITION pos ON (ra.lrm_position_id = pos.id) WHERE ra.id = $id"""
        .as[Int].first should be (ComplimentaryLinkInterface.value)
    }
  }


  test("Delete Road Addresses") {
    runWithRollback {
      val addresses = RoadAddressDAO.fetchByRoadPart(5, 206)
      addresses.nonEmpty should be (true)
      RoadAddressDAO.remove(addresses) should be (addresses.size)
      sql"""SELECT COUNT(*) FROM ROAD_ADDRESS WHERE ROAD_NUMBER = 5 AND ROAD_PART_NUMBER = 206 AND VALID_TO IS NULL""".as[Long].first should be (0L)
    }
  }

  test("test update for merged Road Addresses") {
    val localMockRoadLinkService = MockitoSugar.mock[RoadLinkService]
    val localMockEventBus = MockitoSugar.mock[DigiroadEventBus]
    val localRoadAddressService = new RoadAddressService(localMockRoadLinkService,localMockEventBus)
    runWithRollback {
      val id1 = RoadAddressDAO.getNextRoadAddressId
      val ra = Seq(RoadAddress(id1, 1943845, 1, RoadType.Unknown, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), None, Option("tester"), 0, 12345L, 0.0, 9.8, SideCode.TowardsDigitizing, 0, (None, None), false,
        Seq(Point(0.0, 0.0), Point(0.0, 9.8)), LinkGeomSource.NormalLinkInterface, 8))
      RoadAddressDAO.create(ra, Some("user"))
      val id = RoadAddressDAO.getNextRoadAddressId
      val toBeMergedRoadAddresses = Seq(RoadAddress(id, 1943845, 1, RoadType.Unknown, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), None, Option("tester"),0, 6556558L, 0.0, 9.8, SideCode.TowardsDigitizing, 0, (None, None), false,
        Seq(Point(0.0, 0.0), Point(0.0, 9.8)), LinkGeomSource.NormalLinkInterface, 8))
      localRoadAddressService.mergeRoadAddressInTX(RoadAddressMerge(Set(id1), toBeMergedRoadAddresses))
    }
  }

  ignore("test if road addresses are expired") {
    def now(): DateTime = {
      OracleDatabase.withDynSession {
        return sql"""select sysdate FROM dual""".as[DateTime].list.head
      }
    }

    val beforeCallMethodDatetime = now()
    runWithRollback {
      val linkIds: Set[Long] = Set(4147081)
      RoadAddressDAO.expireRoadAddresses(linkIds)
      val dbResult = sql"""select valid_to FROM road_address where lrm_position_id in (select id from lrm_position where link_id in(4147081))""".as[DateTime].list
      dbResult.size should be (1)
      dbResult.foreach{ date =>
        date.getMillis should be >= beforeCallMethodDatetime.getMillis
      }
    }
  }

  test("find road address by start or end address value") {
    OracleDatabase.withDynSession {
      val s = RoadAddressDAO.fetchByAddressStart(75, 1, Track.apply(2), 875)
      val e = RoadAddressDAO.fetchByAddressEnd(75, 1, Track.apply(2), 995)
      s.isEmpty should be(false)
      e.isEmpty should be(false)
      s should be(e)
    }
  }

  /*
  1.  RA has START_DATE < PROJ_DATE, END_DATE = null
  2.a START_DATE > PROJ_DATE, END_DATE = null
  2.b START_DATE == PROJ_DATE, END_DATE = null
  3.a START_DATE < PROJ_DATE, END_DATE < PROJ_DATE
  3.b START_DATE < PROJ_DATE, END_DATE == PROJ_DATE
  4.a START_DATE < PROJ_DATE, END_DATE > PROJ_DATE
  4.b START_DATE == PROJ_DATE, END_DATE > PROJ_DATE
  5.a START_DATE > PROJ_DATE, END_DATE > PROJ_DATE
  5.b START_DATE == PROJ_DATE, END_DATE > PROJ_DATE
  1 and 3 are acceptable scenarios
  6. Combination 1+3a(+3a+3a+3a+...)
  7. Expired rows are not checked
   */
  test("New roadnumber and roadpart available because start date before project date and no end date (1)") {
    runWithRollback {
      val id1 = Sequences.nextViitePrimaryKeySeqValue
      val rap1 = RoadAddressProject(id1, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("2700-01-01"), "TestUser", DateTime.parse("2700-01-01"), DateTime.now(), "Some additional info", List.empty[ReservedRoadPart], None)
      ProjectDAO.createRoadAddressProject(rap1)
      // Check that the DB contains only null values in end dates
      RoadAddressDAO.fetchByRoadPart(5, 205).map(_.endDate).forall(ed => ed.isEmpty) should be (true)
      val reserved1 = RoadAddressDAO.isNotAvailableForProject(5, 205, id1)
      reserved1 should be(false)
    }
  }

  test("New roadnumber and roadpart not available because start date after project date (2a)") {
    runWithRollback {
      val id = Sequences.nextViitePrimaryKeySeqValue
      val rap = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("1901-01-01"), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List.empty[ReservedRoadPart], None)
      ProjectDAO.createRoadAddressProject(rap)
      val reserved = RoadAddressDAO.isNotAvailableForProject(5,205,id)
      reserved should be (true)
    }
  }

  test("Roadnumber and roadpart not available because start date equals project date (2b)") {
    runWithRollback {
      val id3 = Sequences.nextViitePrimaryKeySeqValue
      val rap3 = RoadAddressProject(id3, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("1962-11-01"),
        "TestUser", DateTime.parse("1962-11-01"), DateTime.now(), "Some additional info", List.empty[ReservedRoadPart], None)
      ProjectDAO.createRoadAddressProject(rap3)
      // Check that the DB contains the start date
      RoadAddressDAO.fetchByRoadPart(5, 207).flatMap(_.startDate.map(_.toDate)).min should be (DateTime.parse("1962-11-01").toDate)
      val reserved3 = RoadAddressDAO.isNotAvailableForProject(5,207,id3)
      reserved3 should be (true)
    }
  }

  test("New roadnumber and roadpart available because start date and end date before project date (3a)") {
    runWithRollback {
      createRoadAddress8888(Option.apply(DateTime.parse("1975-11-18")), Option.apply(DateTime.parse("2000-01-01")))
      val id4 = Sequences.nextViitePrimaryKeySeqValue
      val rap4 = RoadAddressProject(id4, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("2700-01-01"), "TestUser", DateTime.parse("2700-01-01"), DateTime.now(), "Some additional info", List.empty[ReservedRoadPart], None)
      ProjectDAO.createRoadAddressProject(rap4)
      val reserved4 = RoadAddressDAO.isNotAvailableForProject(8888,1,id4)
      reserved4 should be (false)
    }
  }

  test("New roadnumber and roadpart available because end date equals project date (3b)") {
    runWithRollback {
      createRoadAddress8888(Option.apply(DateTime.parse("1975-11-18")), Option.apply(DateTime.parse("2700-01-01")))
      val id5 = Sequences.nextViitePrimaryKeySeqValue
      val rap5 = RoadAddressProject(id5, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("2700-01-01"), "TestUser", DateTime.parse("2700-01-01"), DateTime.now(), "Some additional info", List.empty[ReservedRoadPart], None)
      ProjectDAO.createRoadAddressProject(rap5)
      val reserved5 = RoadAddressDAO.isNotAvailableForProject(8888,1,id5)
      reserved5 should be (false)

    }
  }

  test("New roadnumber and roadpart not available because project date between start and end date (4a)") {
    runWithRollback {
      createRoadAddress8888(Option.apply(DateTime.parse("1975-11-18")), Option.apply(DateTime.parse("2800-01-01")))
      val id6 = Sequences.nextViitePrimaryKeySeqValue
      val rap6 = RoadAddressProject(id6, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("2700-01-01"), "TestUser", DateTime.parse("2700-01-01"), DateTime.now(), "Some additional info", List.empty[ReservedRoadPart], None)
      ProjectDAO.createRoadAddressProject(rap6)
      val reserved6 = RoadAddressDAO.isNotAvailableForProject(8888,1,id6)
      reserved6 should be (true)
    }
  }
  test("New roadnumber and roadpart not available because project date between start and end date (4b)") {
    runWithRollback {
      createRoadAddress8888(Option.apply(DateTime.parse("2700-01-01")), Option.apply(DateTime.parse("2800-01-01")))
      val id6 = Sequences.nextViitePrimaryKeySeqValue
      val rap6 = RoadAddressProject(id6, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("2700-01-01"), "TestUser", DateTime.parse("2700-01-01"), DateTime.now(), "Some additional info", List.empty[ReservedRoadPart], None)
      ProjectDAO.createRoadAddressProject(rap6)
      val reserved6 = RoadAddressDAO.isNotAvailableForProject(8888,1,id6)
      reserved6 should be (true)

    }
  }

  test("New roadnumber and roadpart number not reservable if it's going to exist in the future (5a)") {
    runWithRollback {
      val id7 = Sequences.nextViitePrimaryKeySeqValue
      val rap7 = RoadAddressProject(id7, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("1975-01-01"),
        "TestUser", DateTime.parse("1975-01-01"), DateTime.now(), "Some additional info", List.empty[ReservedRoadPart], None)
      ProjectDAO.createRoadAddressProject(rap7)
      createRoadAddress8888(Option.apply(DateTime.parse("1975-11-18")), Option.apply(DateTime.parse("1990-01-01")))
      val reserved7 = RoadAddressDAO.isNotAvailableForProject(8888,1,id7)
      reserved7 should be (true)
    }
  }

  test("New roadnumber and roadpart number not reservable if it's going to exist in the future (5b)") {
    runWithRollback {
      createRoadAddress8888(Option.apply(DateTime.parse("1975-01-01")))
      val id9 = Sequences.nextViitePrimaryKeySeqValue
      val rap9 = RoadAddressProject(id9, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("1975-01-01"),
        "TestUser", DateTime.parse("1975-01-01"), DateTime.now(), "Some additional info", List.empty[ReservedRoadPart], None)
      ProjectDAO.createRoadAddressProject(rap9)
      val reserved9 = RoadAddressDAO.isNotAvailableForProject(8888, 1, id9)
      reserved9 should be(true)
    }
  }

  test("New roadnumber and roadpart available because last end date is open ended (6)") {
    runWithRollback {
      createRoadAddress8888(Option.apply(DateTime.parse("1975-11-18")), Option.apply(DateTime.parse("2000-01-01")))
      createRoadAddress8888(Option.apply(DateTime.parse("2000-01-01")), Option.apply(DateTime.parse("2001-01-01")))
      createRoadAddress8888(Option.apply(DateTime.parse("2001-01-01")))
      val id = Sequences.nextViitePrimaryKeySeqValue
      val rap = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("2017-01-01"),
        "TestUser", DateTime.parse("2017-01-01"), DateTime.now(), "Some additional info", List.empty[ReservedRoadPart], None)
      ProjectDAO.createRoadAddressProject(rap)
      val reserved = RoadAddressDAO.isNotAvailableForProject(8888,1,id)
      reserved should be (false)
    }
  }

  test("invalidated rows don't affect reservation (7)") {
    runWithRollback {
      createRoadAddress8888(Option.apply(DateTime.parse("1975-11-18")), Option.apply(DateTime.parse("2000-01-01")))
      val id = Sequences.nextViitePrimaryKeySeqValue
      val rap = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("1997-01-01"),
        "TestUser", DateTime.parse("1997-01-01"), DateTime.now(), "Some additional info", List.empty[ReservedRoadPart], None)
      ProjectDAO.createRoadAddressProject(rap)
      RoadAddressDAO.isNotAvailableForProject(8888,1,id) should be (true)
      sqlu"""update ROAD_ADDRESS set valid_to = sysdate WHERE road_number = 8888""".execute
      createRoadAddress8888(Option.apply(DateTime.parse("1975-11-18")), None)
      RoadAddressDAO.isNotAvailableForProject(8888,1,id) should be (false)
    }
  }


  test("New roadnumber and roadpart number  reserved") {
    runWithRollback {
      val id = Sequences.nextViitePrimaryKeySeqValue
      val rap = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("2700-01-01"), "TestUser", DateTime.parse("2700-01-01"), DateTime.now(), "Some additional info", List.empty[ReservedRoadPart], None)
      ProjectDAO.createRoadAddressProject(rap)
      val reserved=   RoadAddressDAO.isNotAvailableForProject(1234567899,1,id)
      reserved should be (false)
    }
  }

  test("Terminated road reservation") {
    runWithRollback {
      val idr = RoadAddressDAO.getNextRoadAddressId
      val ra = Seq(RoadAddress(idr, 1943845, 1, RoadType.Unknown, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), Some(DateTime.parse("1902-01-01")), Option("tester"), 0, 12345L, 0.0, 9.8, SideCode.TowardsDigitizing, 0, (None, None), false,
        Seq(Point(0.0, 0.0), Point(0.0, 9.8)), LinkGeomSource.NormalLinkInterface, 8))
      RoadAddressDAO.create(ra)
      val id = Sequences.nextViitePrimaryKeySeqValue
      val rap = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("2700-01-01"), "TestUser", DateTime.parse("2700-01-01"), DateTime.now(), "Some additional info", List.empty[ReservedRoadPart], None)
      ProjectDAO.createRoadAddressProject(rap)
      val reserved=   RoadAddressDAO.isNotAvailableForProject(1943845,1,id)
      reserved should be (false)
    }
  }

  test("Returning of a terminated road") {
    runWithRollback {
      createTerminatedRoadAddress7777(Option.apply(DateTime.parse("1975-11-18")))
      val roadAddresses = RoadAddressDAO.fetchByLinkId(Set(7777777))
      roadAddresses.size should be (1)
      roadAddresses.head.terminated.value should be (1)
    }
  }

  private def createRoadAddress8888(startDate: Option[DateTime], endDate: Option[DateTime] = None): Unit = {
    RoadAddressDAO.create(
      Seq(
        RoadAddress(Sequences.nextViitePrimaryKeySeqValue, 8888, 1, RoadType.PublicRoad, Track.Combined,
          Discontinuity.Continuous, 0, 35, startDate, endDate,
          Option("TestUser"), Sequences.nextLrmPositionPrimaryKeySeqValue, 8888888, 0, 35, SideCode.TowardsDigitizing,
          0, (None, None), false, Seq(Point(24.24477,987.456)), LinkGeomSource.Unknown, 8)))
  }

  private def createTerminatedRoadAddress7777(startDate: Option[DateTime]): Unit = {
    val roadAddressId = Sequences.nextViitePrimaryKeySeqValue
    RoadAddressDAO.create(
      Seq(
        RoadAddress(roadAddressId, 7777, 1, RoadType.PublicRoad, Track.Combined,
          Discontinuity.Continuous, 0, 35, startDate, Option.apply(DateTime.parse("2000-01-01")),
          Option("TestUser"), Sequences.nextLrmPositionPrimaryKeySeqValue, 7777777, 0, 35, SideCode.TowardsDigitizing,
          0, (None, None), false, Seq(Point(24.24477,987.456)), LinkGeomSource.Unknown, 8)))
    sqlu"""UPDATE ROAD_ADDRESS SET Terminated = 1 Where ID = ${roadAddressId}""".execute
  }

}
