package fi.liikennevirasto.viite.dao

import fi.liikennevirasto.digiroad2.{DigiroadEventBus, Point, RoadLinkService}
import fi.liikennevirasto.digiroad2.asset.{BoundingRectangle, SideCode}
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.Queries
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.viite.{RoadAddressMerge, RoadAddressService}
import fi.liikennevirasto.viite.dao.Discontinuity.Discontinuous
import org.joda.time.DateTime
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery
import slick.jdbc.StaticQuery.interpolation

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
      val numbers = RoadAddressDAO.getValidRoadNumbers
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
      RoadAddressDAO.fetchByBoundingBox(BoundingRectangle(Point(50202, 7620000), Point(50205, 7640000)), false).
        _1.exists(_.id == address.id) should be (true)
      RoadAddressDAO.fetchByBoundingBox(BoundingRectangle(Point(50212, 7620000), Point(50215, 7640000)), false).
        _1.exists(_.id == address.id) should be (false)
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
      val ra = Seq(RoadAddress(id, 1943845, 1, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), None, 12345L, 0.0, 9.8, SideCode.TowardsDigitizing, (None, None), false,
        Seq(Point(0.0, 0.0), Point(0.0, 9.8))))
      val currentSize = RoadAddressDAO.fetchByRoadPart(ra.head.roadNumber, ra.head.roadPartNumber).size
      val returning = RoadAddressDAO.create(ra)
      returning.nonEmpty should be (true)
      returning.head should be (id)
      val newSize = currentSize + 1
      RoadAddressDAO.fetchByRoadPart(ra.head.roadNumber, ra.head.roadPartNumber) should have size(newSize)
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
      val id = RoadAddressDAO.getNextRoadAddressId
          val toBeMergedRoadAddresses = Seq(RoadAddress(id, 1943845, 1, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), None, 6556558L, 0.0, 9.8, SideCode.TowardsDigitizing, (None, None), false,
            Seq(Point(0.0, 0.0), Point(0.0, 9.8))))
      localRoadAddressService.mergeRoadAddress(RoadAddressMerge(Set(1L), toBeMergedRoadAddresses))
    }
  }

}
