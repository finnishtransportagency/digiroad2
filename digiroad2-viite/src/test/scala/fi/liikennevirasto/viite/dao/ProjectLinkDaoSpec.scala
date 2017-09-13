package fi.liikennevirasto.viite.dao
import java.sql.SQLException

import fi.liikennevirasto.digiroad2.GeometryUtils
import fi.liikennevirasto.digiroad2.asset.{LinkGeomSource, SideCode}
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.Sequences
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.viite.RoadType.UnknownOwnerRoad
import fi.liikennevirasto.viite._
import fi.liikennevirasto.viite.dao.LinkStatus.NotHandled
import org.joda.time.DateTime
import org.scalatest.{FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

/**
  * Class to test DB trigger that does not allow reserving already reserved links to project
  */
class ProjectLinkDaoSpec  extends FunSuite with Matchers {

  private final val lock: String = "LOCK OBJECT"

  def runWithRollback(f: => Unit): Unit = {
    // Prevent deadlocks in DB because we create and delete links in tests and don't handle the project ids properly
    // TODO: create projects with unique ids so we don't get into transaction deadlocks in tests
    lock.synchronized {
      Database.forDataSource(OracleDatabase.ds).withDynTransaction {
        f
        dynamicSession.rollback()
      }
    }
  }

  private def toProjectLink(project: RoadAddressProject)(roadAddress: RoadAddress): ProjectLink = {
    ProjectLink(id = NewRoadAddress, roadAddress.roadNumber, roadAddress.roadPartNumber, roadAddress.track,
      roadAddress.discontinuity, roadAddress.startAddrMValue, roadAddress.endAddrMValue, roadAddress.startDate,
      roadAddress.endDate, modifiedBy = Option(project.createdBy), 0L, roadAddress.linkId, roadAddress.startMValue, roadAddress.endMValue,
      roadAddress.sideCode, roadAddress.calibrationPoints, floating = false, roadAddress.geometry, project.id, LinkStatus.NotHandled, RoadType.PublicRoad, roadAddress.linkGeomSource, GeometryUtils.geometryLength(roadAddress.geometry))
  }

  def addprojects(): Unit = {
    sqlu"""insert into project (id,state,name,ely,created_by, start_date) VALUES (1,0,'testproject',1,'automatedtest', sysdate)""".execute
    sqlu"""insert into project (id,state,name,ely,created_by, start_date) VALUES (2,0,'testproject2',1,'automatedtest', sysdate)""".execute
  }

  //TRIGGER has been removed for the time being. Should activate the test again if the triggers are activated again.
  test("Add two links that are reserved") {
    OracleDatabase.withDynTransaction {
      addprojects()
      ProjectDAO.reserveRoadPart(1, 1, 1, "TestUser")
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
    val address = ReservedRoadPart(5: Long, 203: Long, 203: Long, 5.5: Double, 6, Discontinuity.apply("jatkuva"), 8: Long, None: Option[DateTime], None: Option[DateTime])
    runWithRollback {
      val id = Sequences.nextViitePrimaryKeySeqValue
      val rap = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("1901-01-01"), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List(address), None)
      ProjectDAO.createRoadAddressProject(rap)
      val addresses = RoadAddressDAO.fetchByRoadPart(5, 203).map(toProjectLink(rap))
      ProjectDAO.reserveRoadPart(id, 5, 203, "TestUser")
      ProjectDAO.create(addresses)
      ProjectDAO.getRoadAddressProjectById(id).nonEmpty should be(true)
      val projectlinks = ProjectDAO.getProjectLinks(id)
      projectlinks.length should be > 0
      projectlinks.forall(_.status == LinkStatus.NotHandled) should be(true)
      ProjectDAO.fetchFirstLink(1,1,1).isEmpty should be (projectlinks.minBy(_.startAddrMValue))
    }
  }

  test("get projects waiting TR response") {
    val address = ReservedRoadPart(5: Long, 203: Long, 203: Long, 5.5: Double, 6, Discontinuity.apply("jatkuva"), 8: Long, None: Option[DateTime], None: Option[DateTime])
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
    val address = ReservedRoadPart(5: Long, 5: Long, 203: Long, 5.5: Double, 6L, Discontinuity.apply("jatkuva"), 8: Long, None: Option[DateTime], None: Option[DateTime])
    runWithRollback {
      val id = Sequences.nextViitePrimaryKeySeqValue
      val rap = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("1901-01-01"), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List(address), None)
      ProjectDAO.createRoadAddressProject(rap)
      ProjectDAO.reserveRoadPart(id, address.roadNumber, address.roadPartNumber, rap.createdBy)
      val addresses = RoadAddressDAO.fetchByRoadPart(5, 203).map(toProjectLink(rap))
      ProjectDAO.create(addresses)
      ProjectDAO.getRoadAddressProjectById(id).nonEmpty should be(true)
      val projectLinks = ProjectDAO.getProjectLinks(id)
      ProjectDAO.removeProjectLinksById(projectLinks.map(_.id).toSet)
      ProjectDAO.getProjectLinks(id).nonEmpty should be(false)
    }
  }

  test("verify if one can increment the project check counter") {
    val address = ReservedRoadPart(5: Long, 5: Long, 203: Long, 5.5: Double, 6, Discontinuity.apply("jatkuva"), 8: Long, None: Option[DateTime], None: Option[DateTime])
    runWithRollback {
      val id = Sequences.nextViitePrimaryKeySeqValue
      val rap = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("1901-01-01"), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List(address), None)
      ProjectDAO.createRoadAddressProject(rap)
      ProjectDAO.reserveRoadPart(id, address.roadNumber, address.roadPartNumber, rap.createdBy)
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
      ProjectDAO.reserveRoadPart(id, 5, 203, rap.createdBy)
      val addresses = RoadAddressDAO.fetchByRoadPart(5, 203).map(toProjectLink(rap))
      ProjectDAO.create(addresses)
      val projectLinks = ProjectDAO.getProjectLinks(id)
      ProjectDAO.updateProjectLinkStatus(projectLinks.map(x => x.id).toSet, LinkStatus.Terminated, "test")
      val updatedProjectLinks = ProjectDAO.getProjectLinks(id)
      updatedProjectLinks.head.status should be(LinkStatus.Terminated)
    }
  }


  /*
  (id: Long, roadNumber: Long, roadPartNumber: Long, track: Track,
                       discontinuity: Discontinuity, startAddrMValue: Long, endAddrMValue: Long, startDate: Option[DateTime] = None,
                       endDate: Option[DateTime] = None, modifiedBy: Option[String] = None, lrmPositionId : Long, linkId: Long, startMValue: Double, endMValue: Double, sideCode: SideCode,
                       calibrationPoints: (Option[CalibrationPoint], Option[CalibrationPoint]) = (None, None), floating: Boolean = false,
                       geometry: Seq[Point], projectId: Long, status: LinkStatus, roadType: RoadType, linkGeomSource: LinkGeomSource = LinkGeomSource.NormalLinkInterface, geometryLength: Double)

   */

  test("update project link") {
    runWithRollback {
      val terminatedLink = ProjectLink(648,77997,1,Track.Unknown,Discontinuity.Discontinuous,2523,3214,None,None,Some("updateTestuser"),70000665,6638300,1.0,377.05,SideCode.BothDirections,(None, Some(CalibrationPoint(125L, 58.1, 180))), floating=false, List(),7081807,LinkStatus.Terminated,UnknownOwnerRoad, LinkGeomSource.NormalLinkInterface, 10.0)
      val projectLinks = ProjectDAO.getProjectLinks(7081807)
      ProjectDAO.getProjectLinksById(Seq(647))
      ProjectDAO.updateProjectLinkStatus(projectLinks.map(x => x.id).toSet, LinkStatus.UnChanged, "test")
      ProjectDAO.updateProjectLinksToDB(Seq(terminatedLink),"tester")
      val updatedProjectLinks = ProjectDAO.getProjectLinks(7081807).filter( _.id==648)
     val updatedLink=updatedProjectLinks.head
      updatedLink.status should be(LinkStatus.Terminated)
      updatedLink.discontinuity should be (Discontinuity.Discontinuous)
      updatedLink.startAddrMValue should be (2523)
      updatedLink.endAddrMValue should be (3214)
      updatedLink.track should be (Track.Unknown)
      updatedLink.roadType should be (UnknownOwnerRoad)
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
    val address = ReservedRoadPart(5: Long, 203: Long, 203: Long, 5.5: Double, 6, Discontinuity.apply("jatkuva"), 8: Long, None: Option[DateTime], None: Option[DateTime])
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
      ProjectDAO.reserveRoadPart(id, 5, 203, rap.createdBy)
      val addresses = RoadAddressDAO.fetchByRoadPart(5, 203).map(toProjectLink(rap))
      ProjectDAO.create(addresses)
      val project = ProjectDAO.roadPartReservedByProject(5, 203)
      project should be(Some("TestProject"))
    }
  }
  test("Change road address direction") {
    runWithRollback {
      val id = Sequences.nextViitePrimaryKeySeqValue
      val rap = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("1901-01-01"), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List.empty, None)
      ProjectDAO.createRoadAddressProject(rap)
      ProjectDAO.reserveRoadPart(id, 5, 203, rap.createdBy)
      val addresses = RoadAddressDAO.fetchByRoadPart(5, 203).map(toProjectLink(rap))
      ProjectDAO.create(addresses)
      val (lrmid,linkid)= sql"select LRM_POSITION_ID,ID from PROJECT_LINK where PROJECT_LINK.PROJECT_ID = $id".as[(Long,Long)].first
      val projectid= sql"select LRM_POSITION_ID from PROJECT_LINK where PROJECT_LINK.PROJECT_ID = $id".as[Long].first
      val psidecode=sql"select side_code from LRM_Position WHERE id=$lrmid".as[Int].first
      psidecode should be (2)
      ProjectDAO.flipProjectLinksSideCodes(id, 5, 203)
      val nsidecode=sql"select side_code from LRM_Position WHERE id=$lrmid".as[Int].first
      nsidecode should be (3)
      ProjectDAO.flipProjectLinksSideCodes(id, 5, 203)
      val bsidecode=sql"select side_code from LRM_Position WHERE id=$lrmid".as[Int].first
      bsidecode should be (2)
    }
  }

  test("change project Ely DAO") {
    runWithRollback {
      val id = Sequences.nextViitePrimaryKeySeqValue
      val rap = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("1901-01-01"), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List.empty[ReservedRoadPart], None)
      ProjectDAO.createRoadAddressProject(rap)
      ProjectDAO.getRoadAddressProjectById(id).nonEmpty should be(true)
      ProjectDAO.updateProjectEly(id, 100)
      ProjectDAO.getProjectEly(id).get should be (100)
    }
  }
}