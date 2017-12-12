package fi.liikennevirasto.viite.dao

import fi.liikennevirasto.digiroad2.asset.{LinkGeomSource, SideCode}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.viite.ReservedRoadPart
import fi.liikennevirasto.viite.RoadType.UnknownOwnerRoad
import fi.liikennevirasto.viite.process.{Delta, ReNumeration, Transferred, Unchanged}
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

  def addprojects(): Unit = {
    sqlu"""insert into project (id,state,name,ely,created_by, start_date) VALUES (1,0,'testproject',1,'automatedtest', sysdate)""".execute
    sqlu"""insert into project (id,state,name,ely,created_by, start_date) VALUES (2,0,'testproject2',1,'automatedtest', sysdate)""".execute
  }

  test("confirm data fetching"){
    runWithRollback{
      //inserts one case
      val addresses = List(ReservedRoadPart(5:Long, 203:Long, 203:Long, Some(6L), Some(Discontinuity.apply("jatkuva")), Some(8L), newLength = None, newDiscontinuity = None, newEly = None))
      val project = RoadAddressProject(100,ProjectState.Incomplete,"testiprojekti","Test",DateTime.now(),"Test",DateTime.now(),DateTime.now(),"info",addresses, None)
      ProjectDAO.createRoadAddressProject(project)
      sqlu""" insert into road_address_changes(project_id,change_type,new_road_number,new_road_part_number,new_track_code,new_start_addr_m,new_end_addr_m,new_discontinuity,new_road_type,new_ely) Values(100,1,6,1,1,0,10.5,1,1,8) """.execute
      val projectId = sql"""Select p.id From Project p Inner Join road_address_changes rac on p.id = rac.project_id""".as[Long].first
      val changesList = RoadAddressChangesDAO.fetchRoadAddressChanges(Set(projectId))
      changesList.isEmpty should be(false)
      changesList.head.projectId should be(projectId)
    }
  }

  test("confirm data insertion") {
    val newProjectLink = ProjectLink(1, 0, 0, Track.Unknown, Discontinuity.Continuous, 0, 0, None, None, None, 0, 0, 0.0, 0.0,
      SideCode.Unknown, (None, None), false, List(), 1, LinkStatus.New, UnknownOwnerRoad, LinkGeomSource.NormalLinkInterface, 0.0, 0, 5, false,
      None, 748800L)
    val delta = Delta(DateTime.now(), Seq(), Seq(newProjectLink), Unchanged(Seq()), Transferred(Seq()), ReNumeration(Seq()))
    runWithRollback {
      addprojects()
      RoadAddressChangesDAO.insertDeltaToRoadChangeTable(delta, 1)
      sql"""Select Project_Id From road_address_changes Where Project_Id In (1)""".as[Long].firstOption.get should be(1)
    }
  }
}
