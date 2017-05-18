package fi.liikennevirasto.viite.dao
import java.sql.SQLException

import fi.liikennevirasto.digiroad2.masstransitstop.oracle.Sequences
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, RoadLinkService}
import slick.jdbc.StaticQuery.interpolation
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.viite._
import org.joda.time.DateTime
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database
import slick.driver.JdbcDriver.backend.Database.dynamicSession

/**
  * Class to test DB trigger that does not allow reserving already reserved links to project
  */
class ProjectLinkDaoSpec  extends FunSuite with Matchers{

  def runWithRollback(f: => Unit): Unit = {
    Database.forDataSource(OracleDatabase.ds).withDynTransaction {
      f
      dynamicSession.rollback()
    }
  }

  def addprojects(): Unit ={
    sqlu"""insert into project (id,state,name,ely,created_by, start_date) VALUES (1,0,'testproject',1,'automatedtest', sysdate)""".execute
    sqlu"""insert into project (id,state,name,ely,created_by, start_date) VALUES (2,0,'testproject2',1,'automatedtest', sysdate)""".execute
  }

  test("Add two links that are not reserved")
  {
    OracleDatabase.withDynTransaction {
      addprojects()
      /*Insert links to project*/
      sqlu"""insert into project_link (id,project_id,track_code,discontinuity_type,road_number,road_part_number,start_addr_M,end_addr_M,lrm_position_id,created_by) VALUES (1,1,1,0,1,1,1,1,20000286,'automatedtest')""".execute
      sqlu"""insert into project_link (id,project_id,track_code,discontinuity_type,road_number,road_part_number,start_addr_M,end_addr_M,lrm_position_id,created_by) VALUES (2,2,1,0,1,1,1,1,20000287,'automatedtest')""".execute
      sql"""SELECT COUNT(*) FROM project_link WHERE created_by = 'automatedtest'""".as[Long].first should be (2L)
      dynamicSession.rollback()
    }
  }

  //TRIGGER has been removed for the time being. Should activate the test again if the triggers are activated again.
  ignore("Add two links that are reserved")
  {
    OracleDatabase.withDynTransaction {
      addprojects()
      var completed=true
      /*Insert links to project*/
      sqlu"""insert into project_link (id,project_id,track_code,discontinuity_type,road_number,road_part_number,start_addr_M,end_addr_M,lrm_position_id,created_by) VALUES (1,1,1,0,1,1,1,1,20000286,'automatedtest')""".execute
      try{
        sqlu"""insert into project_link (id,project_id,track_code,discontinuity_type,road_number,road_part_number,start_addr_M,end_addr_M,lrm_position_id,created_by) VALUES (2,2,1,0,1,1,1,1,20000286,'automatedtest')""".execute
      } catch
        {
          case _:SQLException =>
            completed=false
        }
      sql"""SELECT COUNT(*) FROM project_link WHERE created_by = 'automatedtest'""".as[Long].first should be (1L)
      completed should be (false)
      dynamicSession.rollback()
    }
  }

  test("create empty road address project") {
    runWithRollback {
      val id = Sequences.nextViitePrimaryKeySeqValue
      val rap = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("1901-01-01"), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List.empty[ReservedRoadPart])
      ProjectDAO.createRoadAddressProject(rap)
      ProjectDAO.getRoadAddressProjectById(id).nonEmpty should be(true)
    }
  }

  test("create road address project with project links") {
    def toProjectLink(project: RoadAddressProject)(roadAddress: RoadAddress): ProjectLink = {
      ProjectLink(id=NewRoadAddress, roadAddress.roadNumber, roadAddress.roadPartNumber, roadAddress.track,
        roadAddress.discontinuity, roadAddress.startAddrMValue, roadAddress.endAddrMValue, roadAddress.startDate,
        roadAddress.endDate, modifiedBy=Option(project.createdBy), 0L, roadAddress.linkId, roadAddress.startMValue, roadAddress.endMValue,
        roadAddress.sideCode, roadAddress.calibrationPoints, floating=false, roadAddress.geom, project.id, LinkStatus.NotHandled)
    }
    val address=ReservedRoadPart(5:Long, 203:Long, 203:Long, 5.5:Double, Discontinuity.apply("jatkuva"), 8:Long)
    runWithRollback {
      val id = Sequences.nextViitePrimaryKeySeqValue
      val rap = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("1901-01-01"), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List(address))
      ProjectDAO.createRoadAddressProject(rap)
      val addresses = RoadAddressDAO.fetchByRoadPart(5, 203).map(toProjectLink(rap))
      ProjectDAO.create(addresses)
      ProjectDAO.getRoadAddressProjectById(id).nonEmpty should be(true)
      val projectlinks=ProjectDAO.getProjectLinks(id)
      projectlinks.length should be > 0
      projectlinks.forall(_.status == LinkStatus.NotHandled) should be (true)
    }
  }

  test("get roadpart info") {
    runWithRollback {
      val reserveResult= RoadAddressDAO.getRoadPartInfo(5,203)
      val expectedLink = reserveResult==Some((242,5172706,5907.0,5))
      expectedLink should be (true)
    }
  }
}
