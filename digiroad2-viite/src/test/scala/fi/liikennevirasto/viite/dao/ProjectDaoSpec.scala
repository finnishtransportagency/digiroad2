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
class ProjectDaoSpec  extends FunSuite with Matchers {

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
      roadAddress.sideCode, roadAddress.calibrationPoints, floating = false, roadAddress.geometry, project.id, LinkStatus.NotHandled, RoadType.PublicRoad, roadAddress.linkGeomSource, GeometryUtils.geometryLength(roadAddress.geometry), 0)
  }

  def addprojects(): (Long, Long) = {
    val id1 = Sequences.nextViitePrimaryKeySeqValue
    val id2 = Sequences.nextViitePrimaryKeySeqValue
    sqlu"""insert into project (id,state,name,ely,created_by, start_date) VALUES ($id1,0,'testproject',1,'automatedtest', sysdate)""".execute
    sqlu"""insert into project (id,state,name,ely,created_by, start_date) VALUES ($id2,0,'testproject2',1,'automatedtest', sysdate)""".execute
    (id1, id2)
  }

  test("Add two links that are reserved") {
    OracleDatabase.withDynTransaction {
      val (projectId1, projectId2) = addprojects()
      ProjectDAO.reserveRoadPart(projectId1, 1, 1, "TestUser")
      var completed = true
      /*Insert links to project*/
      sqlu"""insert into project_link (id,project_id,track_code,discontinuity_type,road_number,road_part_number,start_addr_M,end_addr_M,lrm_position_id,created_by) VALUES (${Sequences.nextViitePrimaryKeySeqValue},${projectId1},1,0,1,1,1,1,20000286,'automatedtest')""".execute
      try {
        sqlu"""insert into project_link (id,project_id,track_code,discontinuity_type,road_number,road_part_number,start_addr_M,end_addr_M,lrm_position_id,created_by) VALUES (${Sequences.nextViitePrimaryKeySeqValue},${projectId2},1,0,1,1,1,1,20000286,'automatedtest')""".execute
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
      ProjectDAO.fetchFirstLink(id,5,203) should be (Some(projectlinks.minBy(_.startAddrMValue)))
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

  //TODO: this test is always deadlocking, need to check better
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
      ProjectDAO.updateProjectLinks(projectLinks.map(x => x.id).toSet, LinkStatus.Terminated, "test")
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
      val projectLinks = ProjectDAO.getProjectLinks(7081807)
      ProjectDAO.updateProjectLinks(projectLinks.map(x => x.id).toSet, LinkStatus.UnChanged, "test")
      val savedProjectLinks = ProjectDAO.getProjectLinks(7081807)
      ProjectDAO.updateProjectLinksToDB(Seq(savedProjectLinks.sortBy(_.startAddrMValue).last.copy(status = LinkStatus.Terminated)),"tester")
      val terminatedLink = projectLinks.sortBy(_.startAddrMValue).last
      val updatedProjectLinks = ProjectDAO.getProjectLinks(7081807).filter( link => link.id == terminatedLink.id)
      val updatedLink=updatedProjectLinks.head
      updatedLink.status should be(LinkStatus.Terminated)
      updatedLink.discontinuity should be (Discontinuity.Continuous)
      updatedLink.startAddrMValue should be (savedProjectLinks.sortBy(_.startAddrMValue).last.startAddrMValue)
      updatedLink.endAddrMValue should be (savedProjectLinks.sortBy(_.startAddrMValue).last.endAddrMValue)
      updatedLink.track should be (savedProjectLinks.sortBy(_.startAddrMValue).last.track)
      updatedLink.roadType should be (savedProjectLinks.sortBy(_.startAddrMValue).last.roadType)
    }
  }

  test("Empty list will not throw an exception") {
    ProjectDAO.getProjectLinksByIds(Seq())
    ProjectDAO.removeProjectLinksById(Set())
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

  //TODO: test is always deadlocking, need to see what is happening
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

  test("roadpart reserved and released by project test") {
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
      val reserved = ProjectDAO.fetchReservedRoadPart(5, 203)
      reserved.nonEmpty should be (true)
      ProjectDAO.removeReservedRoadPart(id, reserved.get)
      val projectAfter = ProjectDAO.roadPartReservedByProject(5, 203)
      projectAfter should be(None)
      ProjectDAO.fetchReservedRoadPart(5, 203).isEmpty should be (true)
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