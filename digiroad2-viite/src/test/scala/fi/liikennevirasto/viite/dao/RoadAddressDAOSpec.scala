package fi.liikennevirasto.viite.dao

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
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
      val address = RoadAddressDAO.getAllRoadAddressesByRange(1L, 1L).head
      RoadAddressDAO.update(address)
    }
  }

  test("Updating a geometry is executed in SQL server") {
    runWithRollback {
      sqlu"""alter session set nls_language = 'american'""".execute
      val address = RoadAddressDAO.getAllRoadAddressesByRange(1L, 1L).head
      RoadAddressDAO.update(address, Some(Seq(Point(50200, 7630000.0, 0.0), Point(50210, 7630000.0, 10.0))))
    }
  }


  test("Set road address to floating and update the geometry as well") {
    runWithRollback {
      val address = RoadAddressDAO.getAllRoadAddressesByRange(1L, 1L).head
      RoadAddressDAO.changeRoadAddressFloating(true, address.id, Some(Seq(Point(50200, 7630000.0, 0.0), Point(50210, 7630000.0, 10.0))))
    }
  }
}
