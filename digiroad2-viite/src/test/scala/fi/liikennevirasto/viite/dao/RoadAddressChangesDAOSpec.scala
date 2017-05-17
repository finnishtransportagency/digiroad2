package fi.liikennevirasto.viite.dao

import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import org.scalatest.{FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

/**
  * Created by pedrosag on 16-05-2017.
  */
class RoadAddressChangesDAOSpec extends FunSuite with Matchers {

  def runWithRollback(f: => Unit): Unit = {
    Database.forDataSource(OracleDatabase.ds).withDynTransaction {
      f
      dynamicSession.rollback()
    }
  }

  test("confirm data fetching"){
    runWithRollback{
      //Assuming that there is data to show
      val projectId = sql"""Select p.id From Project p Inner Join road_address_changes rac on p.id = rac.project_id""".as[Long].first
      val changesList = RoadAddressChangesDAO.fetchRoadAddressChanges(Set(projectId))
      changesList.isEmpty should be(false)
      changesList.head.projectId should be(projectId)
    }
  }


}
