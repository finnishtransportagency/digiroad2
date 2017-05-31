package fi.liikennevirasto.viite.dao

import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.viite.ReservedRoadPart
import org.joda.time.DateTime
import org.scalatest.{FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

class RoadAddressChangesDAOSpec extends FunSuite with Matchers {

  def runWithRollback(f: => Unit): Unit = {
    Database.forDataSource(OracleDatabase.ds).withDynTransaction {
      f
      dynamicSession.rollback()
    }
  }

  test("confirm data fetching"){
    runWithRollback{
      //inserts one case
      val addresses = List(ReservedRoadPart(5:Long, 203:Long, 203:Long, 5:Double, Discontinuity.apply("jatkuva"), 8:Long))
      val project = RoadAddressProject(100,ProjectState.Incomplete,"testiprojekti","Test",DateTime.now(),"Test",DateTime.now(),DateTime.now(),"info",addresses, None)
      ProjectDAO.createRoadAddressProject(project)
      sqlu""" insert into road_address_changes(project_id,change_type,new_road_number,new_road_part_number,new_track_code,new_start_addr_m,new_end_addr_m,new_discontinuity,new_road_type,new_ely) Values(100,1,6,1,1,0,10.5,1,1,8) """.execute
      val projectId = sql"""Select p.id From Project p Inner Join road_address_changes rac on p.id = rac.project_id""".as[Long].first
      val changesList = RoadAddressChangesDAO.fetchRoadAddressChanges(Set(projectId))
      changesList.isEmpty should be(false)
      changesList.head.projectId should be(projectId)
    }
  }

}
