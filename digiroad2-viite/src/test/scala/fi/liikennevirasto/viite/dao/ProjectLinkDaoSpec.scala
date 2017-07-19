package fi.liikennevirasto.viite.dao
import java.sql.SQLException

import fi.liikennevirasto.digiroad2.GeometryUtils
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.Sequences
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.viite._
import org.joda.time.DateTime
import org.scalatest.{FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

/**
  * Class to test DB trigger that does not allow reserving already reserved links to project
  */
class ProjectLinkDaoSpec  extends FunSuite with Matchers {

  def runWithRollback(f: => Unit): Unit = {
    Database.forDataSource(OracleDatabase.ds).withDynTransaction {
      f
      dynamicSession.rollback()
    }
  }

  private def toProjectLink(project: RoadAddressProject)(roadAddress: RoadAddress): ProjectLink = {
    ProjectLink(id = NewRoadAddress, roadAddress.roadNumber, roadAddress.roadPartNumber, roadAddress.track,
      roadAddress.discontinuity, roadAddress.startAddrMValue, roadAddress.endAddrMValue, roadAddress.startDate,
      roadAddress.endDate, modifiedBy = Option(project.createdBy), 0L, roadAddress.linkId, roadAddress.startMValue, roadAddress.endMValue,
      roadAddress.sideCode, roadAddress.calibrationPoints, floating = false, roadAddress.geom, project.id, LinkStatus.NotHandled, RoadType.PublicRoad, roadAddress.linkGeomSource, GeometryUtils.geometryLength(roadAddress.geom))
  }

  def addprojects(): Unit = {
    sqlu"""insert into project (id,state,name,ely,created_by, start_date) VALUES (1,0,'testproject',1,'automatedtest', sysdate)""".execute
    sqlu"""insert into project (id,state,name,ely,created_by, start_date) VALUES (2,0,'testproject2',1,'automatedtest', sysdate)""".execute
  }

  test("Add two links that are not reserved") {
    OracleDatabase.withDynTransaction {
      addprojects()
      /*Insert links to project*/
      sqlu"""insert into project_link (id,project_id,track_code,discontinuity_type,road_number,road_part_number,start_addr_M,end_addr_M,lrm_position_id,created_by) VALUES (1,1,1,0,1,1,1,1,20000286,'automatedtest')""".execute
      sqlu"""insert into project_link (id,project_id,track_code,discontinuity_type,road_number,road_part_number,start_addr_M,end_addr_M,lrm_position_id,created_by) VALUES (2,2,1,0,1,1,1,1,20000287,'automatedtest')""".execute
      sql"""SELECT COUNT(*) FROM project_link WHERE created_by = 'automatedtest'""".as[Long].first should be(2L)
      dynamicSession.rollback()
    }
  }

  //TRIGGER has been removed for the time being. Should activate the test again if the triggers are activated again.
  ignore("Add two links that are reserved") {
    OracleDatabase.withDynTransaction {
      addprojects()
      var completed = true
      /*Insert links to project*/
      sqlu"""insert into project_link (id,project_id,track_code,discontinuity_type,road_number,road_part_number,start_addr_M,end_addr_M,lrm_position_id,created_by) VALUES (1,1,1,0,1,1,1,1,20000286,'automatedtest')""".execute
      try {
        sqlu"""insert into project_link (id,project_id,track_code,discontinuity_type,road_number,road_part_number,start_addr_M,end_addr_M,lrm_position_id,created_by) VALUES (2,2,1,0,1,1,1,1,20000286,'automatedtest')""".execute
      } catch {
        case _: SQLException =>
          completed = false
      }
      sql"""SELECT COUNT(*) FROM project_link WHERE created_by = 'automatedtest'""".as[Long].first should be(1L)
      completed should be(false)
      dynamicSession.rollback()
    }
  }

  test("create empty road address project") {
    runWithRollback {
      val id = Sequences.nextViitePrimaryKeySeqValue
      val rap = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("1901-01-01"), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List.empty[ReservedRoadPart], None)
      ProjectDAO.createRoadAddressProject(rap)
      ProjectDAO.getRoadAddressProjectById(id).nonEmpty should be(true)
    }
  }

  test("create road address project with project links") {
    val address = ReservedRoadPart(5: Long, 203: Long, 203: Long, 5.5: Double, Discontinuity.apply("jatkuva"), 8: Long, None: Option[DateTime], None: Option[DateTime])
    runWithRollback {
      val id = Sequences.nextViitePrimaryKeySeqValue
      val rap = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("1901-01-01"), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List(address), None)
      ProjectDAO.createRoadAddressProject(rap)
      val addresses = RoadAddressDAO.fetchByRoadPart(5, 203).map(toProjectLink(rap))
      ProjectDAO.create(addresses)
      ProjectDAO.getRoadAddressProjectById(id).nonEmpty should be(true)
      val projectlinks = ProjectDAO.getProjectLinks(id)
      projectlinks.length should be > 0
      projectlinks.forall(_.status == LinkStatus.NotHandled) should be(true)
    }
  }

  test("get projects waiting TR response") {
    val address = ReservedRoadPart(5: Long, 203: Long, 203: Long, 5.5: Double, Discontinuity.apply("jatkuva"), 8: Long, None: Option[DateTime], None: Option[DateTime])
    runWithRollback {
      val waitingCountp = ProjectDAO.getProjectsWithWaitingTRStatus().length
      val id = Sequences.nextViitePrimaryKeySeqValue
      val rap = RoadAddressProject(id, ProjectState.apply(2), "TestProject", "TestUser", DateTime.parse("1901-01-01"), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List(address), None)
      ProjectDAO.createRoadAddressProject(rap)
      val waitingCountNow = ProjectDAO.getProjectsWithWaitingTRStatus().length
      waitingCountNow - waitingCountp should be(1)
    }
  }

  test("check the removal of project links") {
    val address = ReservedRoadPart(5: Long, 203: Long, 203: Long, 5.5: Double, Discontinuity.apply("jatkuva"), 8: Long, None: Option[DateTime], None: Option[DateTime])
    runWithRollback {
      val id = Sequences.nextViitePrimaryKeySeqValue
      val rap = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("1901-01-01"), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List(address), None)
      ProjectDAO.createRoadAddressProject(rap)
      val addresses = RoadAddressDAO.fetchByRoadPart(5, 203).map(toProjectLink(rap))
      ProjectDAO.create(addresses)
      ProjectDAO.getRoadAddressProjectById(id).nonEmpty should be(true)
      val projectLinks = ProjectDAO.getProjectLinks(id)
      ProjectDAO.removeProjectLinksById(projectLinks.map(_.id).toSet)
      ProjectDAO.getProjectLinks(id).nonEmpty should be(false)
    }
  }

  test("verify if one can increment the project check counter") {
    val address = ReservedRoadPart(5: Long, 203: Long, 203: Long, 5.5: Double, Discontinuity.apply("jatkuva"), 8: Long, None: Option[DateTime], None: Option[DateTime])
    runWithRollback {
      val id = Sequences.nextViitePrimaryKeySeqValue
      val rap = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("1901-01-01"), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List(address), None)
      ProjectDAO.createRoadAddressProject(rap)
      val addresses = RoadAddressDAO.fetchByRoadPart(5, 203).map(toProjectLink(rap))
      ProjectDAO.create(addresses)
      ProjectDAO.getRoadAddressProjectById(id).nonEmpty should be(true)
      val counterBeforeUpdate = ProjectDAO.getCheckCounter(id).get
      counterBeforeUpdate should be(0)
      ProjectDAO.setCheckCounter(id, counterBeforeUpdate + 1)
      val counterAfterUpdate = ProjectDAO.getCheckCounter(id).get
      counterAfterUpdate should be(1)
      ProjectDAO.incrementCheckCounter(id, 2)
      val counterAfterIncrement = ProjectDAO.getCheckCounter(id).get
      counterAfterIncrement should be(3)
    }

  }

  test("Update project link status") {
    runWithRollback {
      val id = Sequences.nextViitePrimaryKeySeqValue
      val rap = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("1901-01-01"), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List.empty, None)
      ProjectDAO.createRoadAddressProject(rap)
      val addresses = RoadAddressDAO.fetchByRoadPart(5, 203).map(toProjectLink(rap))
      ProjectDAO.create(addresses)
      val projectLinks = ProjectDAO.getProjectLinks(id)
      ProjectDAO.updateProjectLinkStatus(projectLinks.map(x => x.id).toSet, LinkStatus.Terminated, "test")
      val updatedProjectLinks = ProjectDAO.getProjectLinks(id)
      updatedProjectLinks.head.status should be(LinkStatus.Terminated)
    }
  }


  test("Update project status") {
    runWithRollback {
      val id = Sequences.nextViitePrimaryKeySeqValue
      val rap = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("1901-01-01"), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List.empty, None)
      ProjectDAO.createRoadAddressProject(rap)
      ProjectDAO.updateProjectStatus(id, ProjectState.Saved2TR, "")
      ProjectDAO.getProjectStatus(id) should be(Some(ProjectState.Saved2TR))
    }
  }

  test("Update project info") {
    val address = ReservedRoadPart(5: Long, 203: Long, 203: Long, 5.5: Double, Discontinuity.apply("jatkuva"), 8: Long, None: Option[DateTime], None: Option[DateTime])
    runWithRollback {
      val id = Sequences.nextViitePrimaryKeySeqValue
      val rap = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("1901-01-01"), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List(address), None)
      val updatedrap = RoadAddressProject(id, ProjectState.apply(1), "newname", "TestUser", DateTime.parse("1901-01-02"), "TestUser", DateTime.parse("1901-01-02"), DateTime.now(), "updated info", List(address), None)
      ProjectDAO.createRoadAddressProject(rap)
      ProjectDAO.updateRoadAddressProject(updatedrap)
      ProjectDAO.getRoadAddressProjectById(id) match {
        case Some(project) =>
          project.name should be("newname")
          project.additionalInfo should be("updated info")
          project.startDate should be(DateTime.parse("1901-01-02"))
          project.dateModified.getMillis should be > DateTime.parse("1901-01-03").getMillis + 100000000
        case None => None should be(RoadAddressProject)
      }
    }
  }

  test("get roadaddressprojects") {
    runWithRollback {
      val projectListSize = ProjectDAO.getRoadAddressProjects().length
      val id = Sequences.nextViitePrimaryKeySeqValue
      val rap = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("1901-01-01"), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List.empty[ReservedRoadPart], None)
      ProjectDAO.createRoadAddressProject(rap)
      val projectList = ProjectDAO.getRoadAddressProjects()
      projectList.length - projectListSize should be(1)
    }
  }

  test("roadpart reserved by project test") {
    //Creation of Test road
    runWithRollback {
      val id = Sequences.nextViitePrimaryKeySeqValue
      val rap = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("1901-01-01"), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List.empty, None)
      ProjectDAO.createRoadAddressProject(rap)
      val addresses = RoadAddressDAO.fetchByRoadPart(5, 203).map(toProjectLink(rap))
      ProjectDAO.create(addresses)
      val project = ProjectDAO.roadPartReservedByProject(5, 203)
      project should be(Some("TestProject"))
    }
  }
  test("Change roadaddress direction") {
    runWithRollback {
      val id = Sequences.nextViitePrimaryKeySeqValue
      val rap = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("1901-01-01"), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List.empty, None)
      ProjectDAO.createRoadAddressProject(rap)
      val addresses = RoadAddressDAO.fetchByRoadPart(5, 203).map(toProjectLink(rap))
      ProjectDAO.create(addresses)
      val (lrmid,linkid)= sql"select LRM_POSITION_ID,ID from PROJECT_LINK where PROJECT_LINK.PROJECT_ID = $id".as[(Long,Long)].first
      val projectid= sql"select LRM_POSITION_ID from PROJECT_LINK where PROJECT_LINK.PROJECT_ID = $id".as[Long].first
      val psidecode=sql"select side_code from LRM_Position WHERE id=$lrmid".as[Int].first
      psidecode should be (2)
      ProjectDAO.flipProjectLinksSideCodes(Seq(linkid))
      val nsidecode=sql"select side_code from LRM_Position WHERE id=$lrmid".as[Int].first
      nsidecode should be (3)
      ProjectDAO.flipProjectLinksSideCodes(Seq(linkid))
      val bsidecode=sql"select side_code from LRM_Position WHERE id=$lrmid".as[Int].first
      bsidecode should be (2)
    }
  }
}