package fi.liikennevirasto.viite.dao

import java.sql.SQLException

import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
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
import fi.liikennevirasto.viite.util.toProjectLink

/**
  * Class to test DB trigger that does not allow reserving already reserved links to project
  */
class ProjectDaoSpec extends FunSuite with Matchers {

  def runWithRollback(f: => Unit): Unit = {
    // Prevent deadlocks in DB because we create and delete links in tests and don't handle the project ids properly
    // TODO: create projects with unique ids so we don't get into transaction deadlocks in tests
    Database.forDataSource(OracleDatabase.ds).withDynTransaction {
      f
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

  test("create road address project with project links and verify if the geometry is set") {
    val address = ReservedRoadPart(5: Long, 203: Long, 203: Long, Some(6L), Some(Discontinuity.apply("jatkuva")), Some(8L), newLength = None, newDiscontinuity = None, newEly = None)
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
      projectlinks.forall(_.geometry.size == 2) should be (true)
      projectlinks.sortBy(_.endAddrMValue).map(_.geometry).zip(addresses.sortBy(_.endAddrMValue).map(_.geometry)).forall {case (x, y) => x == y}
      ProjectDAO.fetchFirstLink(id, 5, 203) should be(Some(projectlinks.minBy(_.startAddrMValue)))
    }
  }

  test("get projects waiting TR response") {
    val address = ReservedRoadPart(5: Long, 203: Long, 203: Long, Some(6L), Some(Discontinuity.apply("jatkuva")), Some(8L), newLength = None, newDiscontinuity = None, newEly = None)
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
    val address = ReservedRoadPart(5: Long, 5: Long, 203: Long, Some(6L), Some(Discontinuity.apply("jatkuva")), Some(8L), newLength = None, newDiscontinuity = None, newEly = None)
    runWithRollback {
      val id = Sequences.nextViitePrimaryKeySeqValue
      val rap = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("1901-01-01"), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List(address), None)
      ProjectDAO.createRoadAddressProject(rap)
      ProjectDAO.reserveRoadPart(id, address.roadNumber, address.roadPartNumber, rap.createdBy)
      val addresses = RoadAddressDAO.fetchByRoadPart(5, 203).map(toProjectLink(rap))
      ProjectDAO.create(addresses)
      ProjectDAO.getRoadAddressProjectById(id).nonEmpty should be(true)
      val projectLinks = ProjectDAO.getProjectLinks(id)
      ProjectDAO.removeProjectLinksById(projectLinks.map(_.id).toSet) should be(projectLinks.size)
      ProjectDAO.getProjectLinks(id).nonEmpty should be(false)
    }
  }

  test("verify if one can increment the project check counter") {
    val address = ReservedRoadPart(5: Long, 5: Long, 203: Long, Some(6L), Some(Discontinuity.apply("jatkuva")), Some(8L), newLength = None, newDiscontinuity = None, newEly = None)
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

  // Tests now also for VIITE-855 - accidentally removing all links if set is empty
  test("Update project link status and remove zero links") {
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
      ProjectDAO.removeProjectLinksByLinkId(id, Set())
      ProjectDAO.getProjectLinks(id).size should be(updatedProjectLinks.size)
    }
  }

  test("Update project link to reversed") {
    runWithRollback {
      val id = Sequences.nextViitePrimaryKeySeqValue
      val rap = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("1901-01-01"), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List.empty, None)
      ProjectDAO.createRoadAddressProject(rap)
      ProjectDAO.reserveRoadPart(id, 5, 203, rap.createdBy)
      val addresses = RoadAddressDAO.fetchByRoadPart(5, 203).map(toProjectLink(rap))
      ProjectDAO.create(addresses)
      val projectLinks = ProjectDAO.getProjectLinks(id)
      projectLinks.count(x => !x.reversed) should be(projectLinks.size)
      val reversedprojectLinks = projectLinks.map(x => x.copy(reversed = true))
      ProjectDAO.updateProjectLinksToDB(reversedprojectLinks, "testuset")
      val updatedProjectLinks = ProjectDAO.getProjectLinks(id)
      updatedProjectLinks.count(x => x.reversed) should be(updatedProjectLinks.size)
    }
  }


  test("Create reversed project link") {
    runWithRollback {
      val id = Sequences.nextViitePrimaryKeySeqValue
      val rap = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("1901-01-01"), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List.empty, None)
      ProjectDAO.createRoadAddressProject(rap)
      ProjectDAO.reserveRoadPart(id, 5, 203, rap.createdBy)
      val addresses = RoadAddressDAO.fetchByRoadPart(5, 203).map(toProjectLink(rap))
      ProjectDAO.create(addresses.map(x => x.copy(reversed = true)))
      val projectLinks = ProjectDAO.getProjectLinks(id)
      projectLinks.count(x => x.reversed) should be(projectLinks.size)
    }
  }


  test("Create project with no reversed links") {
    runWithRollback {
      val id = Sequences.nextViitePrimaryKeySeqValue
      val rap = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("1901-01-01"), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List.empty, None)
      ProjectDAO.createRoadAddressProject(rap)
      ProjectDAO.reserveRoadPart(id, 5, 203, rap.createdBy)
      val addresses = RoadAddressDAO.fetchByRoadPart(5, 203).map(toProjectLink(rap))
      ProjectDAO.create(addresses.map(x => x.copy(reversed = false)))
      val projectLinks = ProjectDAO.getProjectLinks(id)
      projectLinks.count(x => x.reversed) should be(0)
    }
  }


  test("update project link") {
    runWithRollback {
      val projectLinks = ProjectDAO.getProjectLinks(7081807)
      ProjectDAO.updateProjectLinks(projectLinks.map(x => x.id).toSet, LinkStatus.UnChanged, "test")
      val savedProjectLinks = ProjectDAO.getProjectLinks(7081807)
      ProjectDAO.updateProjectLinksToDB(Seq(savedProjectLinks.sortBy(_.startAddrMValue).last.copy(status = LinkStatus.Terminated)), "tester")
      val terminatedLink = projectLinks.sortBy(_.startAddrMValue).last
      val updatedProjectLinks = ProjectDAO.getProjectLinks(7081807).filter(link => link.id == terminatedLink.id)
      val updatedLink = updatedProjectLinks.head
      updatedLink.status should be(LinkStatus.Terminated)
      updatedLink.discontinuity should be(Discontinuity.Continuous)
      updatedLink.startAddrMValue should be(savedProjectLinks.sortBy(_.startAddrMValue).last.startAddrMValue)
      updatedLink.endAddrMValue should be(savedProjectLinks.sortBy(_.startAddrMValue).last.endAddrMValue)
      updatedLink.track should be(savedProjectLinks.sortBy(_.startAddrMValue).last.track)
      updatedLink.roadType should be(savedProjectLinks.sortBy(_.startAddrMValue).last.roadType)
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
      ProjectDAO.updateProjectStatus(id, ProjectState.Saved2TR)
      ProjectDAO.getProjectStatus(id) should be(Some(ProjectState.Saved2TR))
    }
  }

  test("Update project info") {
    val address = ReservedRoadPart(5: Long, 203: Long, 203: Long, Some(6L), Some(Discontinuity.apply("jatkuva")), Some(8L), newLength = None, newDiscontinuity = None, newEly = None)
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

  test("roadpart reserved, fetched with and without filtering, and released by project test") {
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
      reserved.nonEmpty should be(true)
      ProjectDAO.fetchReservedRoadParts(id) should have size (1)
      ProjectDAO.fetchReservedRoadParts(id, Seq(NotHandled.value)) should have size (0)
      ProjectDAO.removeReservedRoadPart(id, reserved.get)
      val projectAfter = ProjectDAO.roadPartReservedByProject(5, 203)
      projectAfter should be(None)
      ProjectDAO.fetchReservedRoadPart(5, 203).isEmpty should be(true)
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
      val (lrmid, linkid) = sql"select LRM_POSITION_ID,ID from PROJECT_LINK where PROJECT_LINK.PROJECT_ID = $id".as[(Long, Long)].first
      val projectid = sql"select LRM_POSITION_ID from PROJECT_LINK where PROJECT_LINK.PROJECT_ID = $id".as[Long].first
      val psidecode = sql"select side_code from LRM_Position WHERE id=$lrmid".as[Int].first
      psidecode should be(2)
      ProjectDAO.reverseRoadPartDirection(id, 5, 203)
      val nsidecode = sql"select side_code from LRM_Position WHERE id=$lrmid".as[Int].first
      nsidecode should be(3)
      ProjectDAO.reverseRoadPartDirection(id, 5, 203)
      val bsidecode = sql"select side_code from LRM_Position WHERE id=$lrmid".as[Int].first
      bsidecode should be(2)
    }
  }

  test("change project Ely DAO") {
    runWithRollback {
      val id = Sequences.nextViitePrimaryKeySeqValue
      val rap = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("1901-01-01"), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List.empty[ReservedRoadPart], None)
      ProjectDAO.createRoadAddressProject(rap)
      ProjectDAO.getRoadAddressProjectById(id).nonEmpty should be(true)
      ProjectDAO.updateProjectEly(id, 100)
      ProjectDAO.getProjectEly(id).get should be(100)
    }
  }

  test("update project_link's road_type and discontinuity") {
    runWithRollback {
      val projectLinks = ProjectDAO.getProjectLinks(7081807)
      val biggestProjectLink = projectLinks.maxBy(_.endAddrMValue)
      ProjectDAO.updateProjectLinkRoadTypeDiscontinuity(projectLinks.map(x => x.id).filterNot(_ == biggestProjectLink.id).toSet, LinkStatus.UnChanged, "test", 2, None)
      ProjectDAO.updateProjectLinkRoadTypeDiscontinuity(Set(biggestProjectLink.id), LinkStatus.UnChanged, "test", 2, Some(2))
      val savedProjectLinks = ProjectDAO.getProjectLinks(7081807)
      savedProjectLinks.filter(_.roadType.value == 2).size should be(savedProjectLinks.size)
      savedProjectLinks.filter(_.discontinuity.value == 2).size should be(1)
      savedProjectLinks.filter(_.discontinuity.value == 2).head.id should be(biggestProjectLink.id)
    }
  }

  test("fetch by road parts") {
    //Creation of Test road
    runWithRollback {
      val id = Sequences.nextViitePrimaryKeySeqValue
      val rap = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("1901-01-01"), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List.empty, None)
      ProjectDAO.createRoadAddressProject(rap)
      ProjectDAO.reserveRoadPart(id, 5, 203, rap.createdBy)
      ProjectDAO.reserveRoadPart(id, 5, 205, rap.createdBy)
      val addresses = (RoadAddressDAO.fetchByRoadPart(5, 203) ++ RoadAddressDAO.fetchByRoadPart(5, 205)).map(toProjectLink(rap))
      ProjectDAO.create(addresses)
      ProjectDAO.roadPartReservedByProject(5, 203) should be(Some("TestProject"))
      ProjectDAO.roadPartReservedByProject(5, 205) should be(Some("TestProject"))
      val reserved203 = ProjectDAO.fetchByProjectRoadParts(Set((5L, 203L)), id)
      reserved203.nonEmpty should be (true)
      val reserved205 = ProjectDAO.fetchByProjectRoadParts(Set((5L, 205L)), id)
      reserved205.nonEmpty should be (true)
      reserved203 shouldNot be (reserved205)
      reserved203.toSet.intersect(reserved205.toSet) should have size (0)
      val reserved = ProjectDAO.fetchByProjectRoadParts(Set((5L,203L), (5L, 205L)), id)
      reserved.map(_.id).toSet should be (reserved203.map(_.id).toSet ++ reserved205.map(_.id).toSet)
      reserved should have size (addresses.size)
    }
  }

  test("update ProjectLinkgeom") {
    //Creation of Test road
    val address = ReservedRoadPart(5: Long, 203: Long, 203: Long, Some(6L), Some(Discontinuity.apply("jatkuva")), Some(8L), newLength = None, newDiscontinuity = None, newEly = None)
    runWithRollback {
      val id = Sequences.nextViitePrimaryKeySeqValue
      val rap = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("1901-01-01"), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List(address), None)
      ProjectDAO.createRoadAddressProject(rap)
      val addresses = RoadAddressDAO.fetchByRoadPart(5, 203).map(toProjectLink(rap))
      ProjectDAO.reserveRoadPart(id, 5, 203, "TestUser")
      ProjectDAO.create(addresses)
      ProjectDAO.getRoadAddressProjectById(id).nonEmpty should be(true)
      val projectlinksWithDummyGeom = ProjectDAO.getProjectLinks(id).map(x=>x.copy(geometry = Seq(Point(1,1,1),Point(2,2,2),Point(1,2))))
      ProjectDAO.updateProjectLinksGeometry(projectlinksWithDummyGeom,"test")
      val updatedProjectlinks=ProjectDAO.getProjectLinks(id)
      updatedProjectlinks.head.geometry.size should be (3)
      updatedProjectlinks.head.geometry should be (Stream(Point(1.0,1.0,1.0), Point(2.0,2.0,2.0), Point(1.0,2.0,0.0)))
    }
  }

  test("Write and read geometry from string notation") {
    val seq = Seq(Point(123,456,789), Point(4848.125000001,4857.1229999999,48775.123), Point(498487,48373), Point(472374,238474,0.1), Point(0.0, 0.0), Point(-10.0, -20.09, -30.0))
    val converted = fi.liikennevirasto.viite.toGeomString(seq)
    converted.contains(".000") should be (false)
    converted.contains("4848.125,") should be (true)
    converted.contains("4857.123,") should be (true)
    val back = fi.liikennevirasto.viite.toGeometry(converted)
    back.zip(seq).foreach{ case (p1,p2) =>
      p1.x should be (p2.x +- 0.0005)
      p1.y should be (p2.y +- 0.0005)
      p1.z should be (p2.z +- 0.0005)
    }

  }
}