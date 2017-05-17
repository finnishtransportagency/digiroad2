package fi.liikennevirasto.viite

import java.util.Properties

import fi.liikennevirasto.digiroad2.asset.ConstructionType.InUse
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset.{BoundingRectangle, State, TrafficDirection, UnknownLinkType}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.Sequences
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.viite.dao._
import org.joda.time.DateTime
import org.mockito.Mockito.when
import org.scalatest.{FunSuite, Matchers}
import org.scalatest.mock.MockitoSugar
import slick.driver.JdbcDriver.backend.Database
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery
import slick.jdbc.StaticQuery.interpolation

class ProjectServiceSpec  extends FunSuite with Matchers {
  val properties: Properties = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/digiroad2.properties"))
    props
  }
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

  test("Fetch project links") {
    val roadLinkService = new RoadLinkService(new VVHClient(properties.getProperty("digiroad2.VVHRestApiEndPoint")), mockEventBus, new DummySerializer) {
      override def withDynSession[T](f: => T): T = f
      override def withDynTransaction[T](f: => T): T = f
    }
    val roadAddressService = new RoadAddressService(roadLinkService,mockEventBus) {
      override def withDynSession[T](f: => T): T = f
      override def withDynTransaction[T](f: => T): T = f
    }
    val projectService = new ProjectService(roadAddressService, roadLinkService, mockEventBus) {
      override def withDynSession[T](f: => T): T = f
      override def withDynTransaction[T](f: => T): T = f
    }
    runWithRollback {
      val addresses:List[ReservedRoadPart]= List(ReservedRoadPart(0:Long, 5:Long, 205:Long, 5:Double, Discontinuity.apply("jatkuva"), 8:Long))
      val roadAddressProject = RoadAddressProject(0, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", addresses)
      val savedProject = projectService.createRoadLinkProject(roadAddressProject)._1
      val startingLinkId = ProjectDAO.getProjectLinks(savedProject.id).filter(_.track == Track.LeftSide).minBy(_.startAddrMValue).linkId
      val boundingRectangle = roadLinkService.fetchVVHRoadlinks(Set(startingLinkId)).map { vrl =>
        val x = vrl.geometry.map(l => l.x)
        val y = vrl.geometry.map(l => l.y)

        BoundingRectangle(Point(x.min, y.min) + Vector3d(-5.0,-5.0,0.0), Point(x.max, y.max) + Vector3d(5.0,5.0,0.0))}.head

      val links = projectService.getProjectRoadLinks(savedProject.id, boundingRectangle, Seq(), Set(), true, true)
      links.nonEmpty should be (true)
      links.exists(_.status == LinkStatus.Unknown) should be (true)
      links.exists(_.status == LinkStatus.NotHandled) should be (true)
      val (unk, nh) = links.partition(_.status == LinkStatus.Unknown)
      nh.forall(l => l.roadNumber == 5 && l.roadPartNumber == 205) should be (true)
      unk.forall(l => l.roadNumber != 5 || l.roadPartNumber != 205) should be (true)
      nh.map(_.linkId).toSet.intersect(unk.map(_.linkId).toSet) should have size (0)
      unk.exists(_.attributes.getOrElse("ROADPARTNUMBER", "0").toString == "203") should be (true)
    }
  }
}
