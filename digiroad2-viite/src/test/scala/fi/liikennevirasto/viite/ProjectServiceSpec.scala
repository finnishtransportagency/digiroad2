package fi.liikennevirasto.viite

import fi.liikennevirasto.digiroad2.asset.ConstructionType.InUse
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset.{State, TrafficDirection, UnknownLinkType}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.Sequences
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, Point, RoadLinkService}
import fi.liikennevirasto.viite.dao.{Discontinuity, RoadAddressDAO, RoadAddressProject, ProjectState}
import org.joda.time.DateTime
import org.mockito.Mockito.when
import org.scalatest.{FunSuite, Matchers}
import org.scalatest.mock.MockitoSugar
import slick.driver.JdbcDriver.backend.Database
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery
import slick.jdbc.StaticQuery.interpolation

class ProjectServiceSpec  extends FunSuite with Matchers {
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  val roadAddressService = new RoadAddressService(mockRoadLinkService,mockEventBus) {
    override def withDynSession[T](f: => T): T = f
    override def withDynTransaction[T](f: => T): T = f
  }
  val projectService = new ProjectService(roadAddressService, mockRoadLinkService, mockEventBus) {
    override def withDynSession[T](f: => T): T = f
    override def withDynTransaction[T](f: => T): T = f
  }

  def runWithRollback[T](f: => T): T = {
    Database.forDataSource(OracleDatabase.ds).withDynTransaction {
      val t = f
      dynamicSession.rollback()
      t
    }
  }

  test ("create road link project without road parts") {
    runWithRollback{
      val roadAddressProject = RoadAddressProject(0, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List.empty[ReservedRoadPart])
      val (project, projLinkOpt, formLines, str) = projectService.createRoadLinkProject(roadAddressProject)
      projLinkOpt should be (None)
      formLines should have size (0)

    }
  }

  test ("create road link project without valid roadParts") {
    val roadlink = RoadLink(5175306,Seq(Point(535605.272,6982204.22,85.90899999999965))
      ,540.3960283713503,State,99,TrafficDirection.AgainstDigitizing,UnknownLinkType,Some("25.06.2015 03:00:00"), Some("vvh_modified"),Map("MUNICIPALITYCODE" -> BigInt.apply(749)),
      InUse,NormalLinkInterface)
    when(mockRoadLinkService.getRoadLinksByLinkIdsFromVVH(Set(5175306L))).thenReturn(Seq(roadlink))
    runWithRollback{
      val roadAddressProject = RoadAddressProject(0, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List.empty[ReservedRoadPart])
      val (project, projLinkOpt, formLines, str) = projectService.createRoadLinkProject(roadAddressProject)
      projLinkOpt should be (None)
      formLines should have size (0)
    }
  }

  test("create and get projects by id") {
    var count = 0
    runWithRollback {
      val countCurrentProjects = projectService.getRoadAddressAllProjects()
      val id = 0
      val addresses:List[ReservedRoadPart]= List(ReservedRoadPart(5:Long, 203:Long, 203:Long, 5:Double, Discontinuity.apply("jatkuva"), 8:Long))
      val roadAddressProject = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", addresses)
      projectService.createRoadLinkProject(roadAddressProject)
      val countAfterInsertProjects = projectService.getRoadAddressAllProjects()
      count = countCurrentProjects.size + 1
      countAfterInsertProjects.size should be (count)
    }
    runWithRollback {
      projectService.getRoadAddressAllProjects().size should be (count-1)
    }
  }

  test("save project") {
    var count = 0
    runWithRollback {
      val countCurrentProjects = projectService.getRoadAddressAllProjects()
      val id = 0
      val addresses = List(ReservedRoadPart(5:Long, 203:Long, 203:Long, 5:Double, Discontinuity.apply("jatkuva"), 8:Long))
      val roadAddressProject = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List())
      val saved = projectService.createRoadLinkProject(roadAddressProject)._1
      val changed = saved.copy(reservedParts = addresses)
      projectService.saveRoadLinkProject(changed)
      val countAfterInsertProjects = projectService.getRoadAddressAllProjects()
      count = countCurrentProjects.size + 1
      countAfterInsertProjects.size should be (count)
    }
    runWithRollback { projectService.getRoadAddressAllProjects() } should have size (count - 1)
  }

  test("fetch project data and send it to TR") {
    runWithRollback{
      //Assuming that there is data to show
      val projectId = 0
      val responses = projectService.getRoadAddressChangesAndSendToTR(Set(projectId))
      responses.isEmpty should be(false)
      responses.head.projectId should be(projectId)
    }
  }

}
