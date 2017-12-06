package fi.liikennevirasto.viite

import java.util.Properties

import fi.liikennevirasto.digiroad2.FeatureClass.AllOthers
import fi.liikennevirasto.viite.util.{SplitOptions, StaticTestData}
import fi.liikennevirasto.digiroad2.asset.ConstructionType.InUse
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.asset.TrafficDirection.BothDirections
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.{PolyLine, RoadLink}
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.Sequences
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.digiroad2.util.Track.Combined
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, Point, RoadLinkService, _}
import fi.liikennevirasto.viite.RoadType.PublicRoad
import fi.liikennevirasto.viite.dao.AddressChangeType._
import fi.liikennevirasto.viite.dao.Discontinuity.{Continuous, Discontinuous}
import fi.liikennevirasto.viite.dao.ProjectState.Sent2TR
import fi.liikennevirasto.viite.dao._
import fi.liikennevirasto.viite.model.{Anomaly, ProjectAddressLink, RoadAddressLinkLike}
import fi.liikennevirasto.viite.process.ProjectDeltaCalculator
import org.joda.time.DateTime
import org.mockito.Matchers._
import org.mockito.Mockito.{when, _}
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.mock.MockitoSugar
import org.scalatest.{BeforeAndAfter, FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

class ProjectServiceSpec extends FunSuite with Matchers with BeforeAndAfter {
  val properties: Properties = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/digiroad2.properties"))
    props
  }
  val mockProjectService = MockitoSugar.mock[ProjectService]
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockRoadAddressService = MockitoSugar.mock[RoadAddressService]
  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  val roadAddressService = new RoadAddressService(mockRoadLinkService, mockEventBus) {
    override def withDynSession[T](f: => T): T = f

    override def withDynTransaction[T](f: => T): T = f
  }
  val projectService = new ProjectService(roadAddressService, mockRoadLinkService, mockEventBus) {
    override def withDynSession[T](f: => T): T = f

    override def withDynTransaction[T](f: => T): T = f
  }

  val projectServiceWithRoadAddressMock = new ProjectService(mockRoadAddressService, mockRoadLinkService, mockEventBus) {
    override def withDynSession[T](f: => T): T = f

    override def withDynTransaction[T](f: => T): T = f
  }



  after {
    reset(mockRoadLinkService)
  }

  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)

  def runWithRollback[T](f: => T): T = {
    Database.forDataSource(OracleDatabase.ds).withDynTransaction {
      val t = f
      dynamicSession.rollback()
      t
    }
  }

  private def createProjectLinks(linkIds: Seq[Long], projectId: Long, roadNumber: Long, roadPartNumber: Long, track: Int,
                         discontinuity: Int, roadType: Int, roadLinkSource: Int,
                         roadEly: Long, user: String): Map[String, Any] = {
    projectService.createProjectLinks(linkIds, projectId, roadNumber, roadPartNumber, Track.apply(track), Discontinuity.apply(discontinuity),
      RoadType.apply(roadType), LinkGeomSource.apply(roadLinkSource), roadEly, user)
  }
  private def toProjectLink(project: RoadAddressProject, status: LinkStatus)(roadAddress: RoadAddress): ProjectLink = {
    ProjectLink(id = NewRoadAddress, roadAddress.roadNumber, roadAddress.roadPartNumber, roadAddress.track,
      roadAddress.discontinuity, roadAddress.startAddrMValue, roadAddress.endAddrMValue, roadAddress.startDate,
      roadAddress.endDate, modifiedBy = Option(project.createdBy), 0L, roadAddress.linkId, roadAddress.startMValue, roadAddress.endMValue,
      roadAddress.sideCode, roadAddress.calibrationPoints, floating = false, roadAddress.geometry, project.id, status, RoadType.PublicRoad,
      roadAddress.linkGeomSource, GeometryUtils.geometryLength(roadAddress.geometry), if (status == LinkStatus.New) 0 else roadAddress.id, roadAddress.ely, false,
      None, roadAddress.adjustedTimestamp)
  }

  private def toProjectAddressLink(ral: RoadAddressLinkLike): ProjectAddressLink = {
    ProjectAddressLink(ral.id, ral.linkId, ral.geometry, ral.length, ral.administrativeClass, ral.linkType, ral.roadLinkType,
      ral.constructionType, ral.roadLinkSource, ral.roadType, ral.roadName, ral.municipalityCode, ral.modifiedAt, ral.modifiedBy,
      ral.attributes, ral.roadNumber, ral.roadPartNumber, ral.trackCode, ral.elyCode, ral.discontinuity,
      ral.startAddressM, ral.endAddressM, ral.startMValue, ral.endMValue, ral.sideCode, ral.startCalibrationPoint, ral.endCalibrationPoint,
      ral.anomaly, ral.lrmPositionId, LinkStatus.Unknown, ral.id)
  }

  private def extractTrafficDirection(sideCode: SideCode, track: Track): TrafficDirection = {
    (sideCode, track) match {
      case (_, Track.Combined) => TrafficDirection.BothDirections
      case (TowardsDigitizing, Track.RightSide) => TrafficDirection.TowardsDigitizing
      case (TowardsDigitizing, Track.LeftSide) => TrafficDirection.AgainstDigitizing
      case (AgainstDigitizing, Track.RightSide) => TrafficDirection.AgainstDigitizing
      case (AgainstDigitizing, Track.LeftSide) => TrafficDirection.TowardsDigitizing
      case (_, _) => TrafficDirection.UnknownDirection
    }
  }

  private def toRoadLink(ral: ProjectLink): RoadLink = {
    RoadLink(ral.linkId, ral.geometry, ral.geometryLength, State, 1,
      extractTrafficDirection(ral.sideCode, ral.track), Motorway, None, None, Map(
        "MUNICIPALITYCODE" -> BigInt(749), "VERTICALLEVEL" -> BigInt(1), "SURFACETYPE" -> BigInt(1),
        "ROADNUMBER" -> BigInt(ral.roadNumber), "ROADPARTNUMBER" -> BigInt(ral.roadPartNumber)),
      ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
  }

  private def toRoadLink(ral: RoadAddressLinkLike): RoadLink = {
    RoadLink(ral.linkId, ral.geometry, ral.length, ral.administrativeClass, 1,
      extractTrafficDirection(ral.sideCode, Track.apply(ral.trackCode.toInt)), ral.linkType, ral.modifiedAt, ral.modifiedBy, Map(
        "MUNICIPALITYCODE" -> BigInt(749), "VERTICALLEVEL" -> BigInt(1), "SURFACETYPE" -> BigInt(1),
        "ROADNUMBER" -> BigInt(ral.roadNumber), "ROADPARTNUMBER" -> BigInt(ral.roadPartNumber)),
      ral.constructionType, ral.roadLinkSource)
  }

  private def toMockAnswer(projectLinks: Seq[ProjectLink], roadLink: RoadLink, seq: Seq[RoadLink] = Seq()) = {
    new Answer[Seq[RoadLink]]() {
      override def answer(invocation: InvocationOnMock): Seq[RoadLink] = {
        val ids = if (invocation.getArguments.apply(0) == null)
          Set[Long]()
        else invocation.getArguments.apply(0).asInstanceOf[Set[Long]]
        projectLinks.groupBy(_.linkId).filterKeys(l => ids.contains(l)).mapValues { pl =>
          val startP = Point(pl.map(_.startAddrMValue).min, 0.0)
          val endP = Point(pl.map(_.endAddrMValue).max, 0.0)
          val maxLen = pl.map(_.endMValue).max
          val midP = Point((startP.x + endP.x) * .5,
            if (endP.x - startP.x < maxLen) {
              Math.sqrt(maxLen * maxLen - (startP.x - endP.x) * (startP.x - endP.x)) / 2
            }
            else 0.0)
          val forcedGeom = pl.filter(l => l.id == -1000L && l.geometry.nonEmpty).sortBy(_.startAddrMValue)
          val (startFG, endFG) = (forcedGeom.headOption.map(_.startingPoint), forcedGeom.lastOption.map(_.endPoint))
          if (pl.head.id == -1000L) {
            roadLink.copy(linkId = pl.head.linkId, geometry = Seq(startFG.get, endFG.get))
          } else
            roadLink.copy(linkId = pl.head.linkId, geometry = Seq(startP, midP, endP))
        }.values.toSeq ++ seq
      }
    }
  }

  private def toMockAnswer(roadLinks: Seq[RoadLink]) = {
    new Answer[Seq[RoadLink]]() {
      override def answer(invocation: InvocationOnMock): Seq[RoadLink] = {
        val ids = invocation.getArguments.apply(0).asInstanceOf[Set[Long]]
        roadLinks.filter(rl => ids.contains(rl.linkId))
      }
    }
  }

  private def mockForProject[T <: PolyLine](id: Long, l: Seq[T] = Seq()) = {
    val roadLink = RoadLink(1, Seq(Point(535602.222, 6982200.25, 89.9999), Point(535605.272, 6982204.22, 85.90899999999965))
      , 540.3960283713503, State, 99, TrafficDirection.AgainstDigitizing, UnknownLinkType, Some("25.06.2015 03:00:00"), Some("vvh_modified"), Map("MUNICIPALITYCODE" -> BigInt.apply(749)),
      InUse, NormalLinkInterface)
    val (projectLinks, palinks) = l.partition(_.isInstanceOf[ProjectLink])
    val dbLinks = ProjectDAO.getProjectLinks(id)
    when(mockRoadLinkService.getViiteRoadLinksHistoryFromVVH(any[Set[Long]])).thenReturn(Seq())
    when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean], any[Boolean])).thenAnswer(
      toMockAnswer(dbLinks ++ projectLinks.asInstanceOf[Seq[ProjectLink]].filterNot(l => dbLinks.map(_.linkId).contains(l.linkId)),
        roadLink, palinks.asInstanceOf[Seq[ProjectAddressLink]].map(toRoadLink)
      ))
  }

  test("create road link project without road parts") {
    runWithRollback {
      val roadAddressProject = RoadAddressProject(0, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List.empty[ReservedRoadPart], None)
      val project = projectService.createRoadLinkProject(roadAddressProject)
      project.reservedParts should have size (0)
    }
  }

  test("Adding and removing TR_ID") {
    runWithRollback {
      val projectId = Sequences.nextViitePrimaryKeySeqValue
      val rap = RoadAddressProject(projectId, ProjectState.apply(3), "TestProject", "TestUser", DateTime.parse("2700-01-01"), "TestUser", DateTime.parse("2700-01-01"), DateTime.now(), "Some additional info", List.empty[ReservedRoadPart], None)
      runWithRollback {
        ProjectDAO.createRoadAddressProject(rap)
        val emptyTrId = ProjectDAO.getRotatingTRProjectId(projectId)
        emptyTrId.isEmpty should be(true)
        val projectNone = ProjectDAO.getRoadAddressProjectById(projectId)
        projectService.removeRotatingTRId(projectId)
        projectNone.head.statusInfo.getOrElse("").size should be(0)
        ProjectDAO.addRotatingTRProjectId(projectId)
        val trId = ProjectDAO.getRotatingTRProjectId(projectId)
        trId.nonEmpty should be(true)
        projectService.removeRotatingTRId(projectId)
        emptyTrId.isEmpty should be(true)
        ProjectDAO.addRotatingTRProjectId(projectId)
        projectService.removeRotatingTRId(projectId)
        val project = ProjectDAO.getRoadAddressProjectById(projectId).head
        project.status should be(ProjectState.Incomplete)
        project.statusInfo.getOrElse("1").size should be > 2
      }
    }
  }

  test("change roadpart direction and check reversed attribute, service level") {
    runWithRollback {
      val rap = RoadAddressProject(0L, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("1901-01-01"),
        "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info",
        Seq(), None)
      val project = projectService.createRoadLinkProject(rap)
      val id = project.id
      mockForProject(id, RoadAddressDAO.fetchByRoadPart(5, 207).map(toProjectLink(project)))
      projectService.saveProject(project.copy(reservedParts = Seq(
        ReservedRoadPart(0L, 5, 207, Some(0L), Some(Continuous), Some(8L), None, None, None, None, true))))
      val projectLinks = ProjectDAO.getProjectLinks(id)
      ProjectDAO.updateProjectLinks(projectLinks.map(x => x.id).toSet, LinkStatus.Transfer, "test")
      mockForProject(id)
      projectService.changeDirection(id,5,207, projectLinks.map(l => LinkToRevert(l.id, l.linkId, l.status.value, l.geometry)),"test") should be (None)
      val updatedProjectLinks = ProjectDAO.getProjectLinks(id)
      updatedProjectLinks.foreach(x=>x.reversed should be (true))
      projectService.changeDirection(id,5,207, projectLinks.map(l => LinkToRevert(l.id, l.linkId, l.status.value, l.geometry)),"test")
      val secondUpdatedProjectLinks = ProjectDAO.getProjectLinks(id)
      secondUpdatedProjectLinks.foreach(x=>x.reversed should be (false))
    }
  }

  private def toProjectLink(project: RoadAddressProject)(roadAddress: RoadAddress): ProjectLink = {
    ProjectLink(id = NewRoadAddress, roadAddress.roadNumber, roadAddress.roadPartNumber, roadAddress.track,
      roadAddress.discontinuity, roadAddress.startAddrMValue, roadAddress.endAddrMValue, roadAddress.startDate,
      roadAddress.endDate, modifiedBy = Option(project.createdBy), 0L, roadAddress.linkId, roadAddress.startMValue, roadAddress.endMValue,
      roadAddress.sideCode, roadAddress.calibrationPoints, floating = false, roadAddress.geometry, project.id, LinkStatus.NotHandled, RoadType.PublicRoad,
      roadAddress.linkGeomSource, GeometryUtils.geometryLength(roadAddress.geometry), 0, roadAddress.ely, false,
      None, roadAddress.adjustedTimestamp)
  }

  test("Using TR_id as project_id when querying should be empty") {
    runWithRollback {
      val projectId = Sequences.nextViitePrimaryKeySeqValue
      val rap = RoadAddressProject(projectId, ProjectState.apply(2), "TestProject", "TestUser", DateTime.parse("2700-01-01"), "TestUser", DateTime.parse("2700-01-01"), DateTime.now(), "Some additional info", List.empty[ReservedRoadPart], None)
      runWithRollback {
        ProjectDAO.createRoadAddressProject(rap)
        ProjectDAO.addRotatingTRProjectId(projectId)
        projectService.updateProjectsWaitingResponseFromTR()
        val project = ProjectDAO.getRoadAddressProjectById(projectId).head
        project.statusInfo.getOrElse("").size should be(0)
        projectService.updateProjectsWaitingResponseFromTR()
      }
    }
  }

  test("Using TR_id as project_id when querrying info: should fail") {
    runWithRollback {
      val projectId = Sequences.nextViitePrimaryKeySeqValue
      val rap = RoadAddressProject(projectId, ProjectState.apply(2), "TestProject", "TestUser", DateTime.parse("2700-01-01"), "TestUser", DateTime.parse("2700-01-01"), DateTime.now(), "Some additional info", List.empty[ReservedRoadPart], None)
      runWithRollback {
        ProjectDAO.createRoadAddressProject(rap)
        projectService.updateProjectsWaitingResponseFromTR()
        val project = ProjectDAO.getRoadAddressProjectById(projectId).head
        project.statusInfo.getOrElse("") contains ("Failed to find TR-ID") should be(true)
      }
    }
  }

  test("create road link project without valid roadParts") {
    val roadlink = RoadLink(5175306, Seq(Point(535605.272, 6982204.22, 85.90899999999965))
      , 540.3960283713503, State, 99, TrafficDirection.AgainstDigitizing, UnknownLinkType, Some("25.06.2015 03:00:00"), Some("vvh_modified"), Map("MUNICIPALITYCODE" -> BigInt.apply(749)),
      InUse, NormalLinkInterface)
    when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(Set(5175306L))).thenReturn(Seq(roadlink))
    runWithRollback {
      val roadAddressProject = RoadAddressProject(0, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List.empty[ReservedRoadPart], None)
      val project = projectService.createRoadLinkProject(roadAddressProject)
      project.reservedParts should have size (0)
    }
  }

  test("create and get projects by id") {
    var count = 0
    runWithRollback {
      val countCurrentProjects = projectService.getRoadAddressAllProjects()
      val roadAddressProject = RoadAddressProject(0, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", Seq(), None)
      val project = projectService.createRoadLinkProject(roadAddressProject)
      mockForProject(project.id, RoadAddressDAO.fetchByRoadPart(5, 203).map(toProjectLink(roadAddressProject)))
      projectService.saveProject(project.copy(reservedParts = Seq(
        ReservedRoadPart(0L, 5, 203, Some(0L), Some(Continuous), Some(8L), None, None, None, None, true))))
      val countAfterInsertProjects = projectService.getRoadAddressAllProjects()
      count = countCurrentProjects.size + 1
      countAfterInsertProjects.size should be(count)
    }
    runWithRollback {
      projectService.getRoadAddressAllProjects().size should be(count - 1)
    }
  }

  test("save project") {
    var count = 0
    runWithRollback {
      val countCurrentProjects = projectService.getRoadAddressAllProjects()
      val id = 0
      val roadAddressProject = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List(), None)
      val project = projectService.createRoadLinkProject(roadAddressProject)
      mockForProject(project.id, RoadAddressDAO.fetchByRoadPart(1130, 4).map(toProjectLink(roadAddressProject)))
      projectService.saveProject(project.copy(reservedParts = List(
        ReservedRoadPart(Sequences.nextViitePrimaryKeySeqValue: Long, 1130: Long, 4: Long, Some(5L), Some(Discontinuity.apply("jatkuva")), Some(8L), newLength = None, newDiscontinuity = None, newEly = None))))
      val countAfterInsertProjects = projectService.getRoadAddressAllProjects()
      count = countCurrentProjects.size + 1
      countAfterInsertProjects.size should be(count)
    }
    runWithRollback {
      projectService.getRoadAddressAllProjects()
    } should have size (count - 1)
  }

  test("create and delete project") {
    var count = 0
    runWithRollback {
      val countCurrentProjects = projectService.getRoadAddressAllProjects()
      val roadAddressProject = RoadAddressProject(0, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", Seq(), None)
      val project = projectService.createRoadLinkProject(roadAddressProject)
      mockForProject(project.id, RoadAddressDAO.fetchByRoadPart(5, 203).map(toProjectLink(roadAddressProject)))
      projectService.saveProject(project.copy(reservedParts = Seq(ReservedRoadPart(0L, 5, 203, Some(100L), Some(Continuous), Some(8L), None, None, None, None))))
      val countAfterInsertProjects = projectService.getRoadAddressAllProjects()
      count = countCurrentProjects.size + 1
      countAfterInsertProjects.size should be(count)
      projectService.deleteProject(project.id)
      val projectsAfterOperations = projectService.getRoadAddressAllProjects()
      projectsAfterOperations.size should be(count)
      projectsAfterOperations.exists(_.id == project.id) should be (true)
      projectsAfterOperations.find(_.id == project.id).get.status should be (ProjectState.Deleted)
    }
  }

  test("Unchanged with termination test, repreats termination update, checks calibration points are cleared and moved to correct positions") {
    var count = 0
    val roadLink = RoadLink(5170939L, Seq(Point(535605.272, 6982204.22, 85.90899999999965))
      , 540.3960283713503, State, 99, TrafficDirection.AgainstDigitizing, UnknownLinkType, Some("25.06.2015 03:00:00"), Some("vvh_modified"), Map("MUNICIPALITYCODE" -> BigInt.apply(749)),
      InUse, NormalLinkInterface)
    runWithRollback {
      val countCurrentProjects = projectService.getRoadAddressAllProjects()
      val id = 0
      val addresses = Seq(ReservedRoadPart(5: Long, 5: Long, 205: Long, Some(5L), Some(Discontinuity.apply("jatkuva")), Some(8L), newLength = None, newDiscontinuity = None, newEly = None),
        ReservedRoadPart(5: Long, 5: Long, 206: Long, Some(5L), Some(Discontinuity.apply("jatkuva")), Some(8L), newLength = None, newDiscontinuity = None, newEly = None))
      val roadAddressProject = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", Seq(), None)
      val savedProject = projectService.createRoadLinkProject(roadAddressProject)
      mockForProject(savedProject.id, (RoadAddressDAO.fetchByRoadPart(5, 205) ++ RoadAddressDAO.fetchByRoadPart(5,206)).map(toProjectLink(savedProject)))
      projectService.saveProject(savedProject.copy(reservedParts = addresses))
      val countAfterInsertProjects = projectService.getRoadAddressAllProjects()
      count = countCurrentProjects.size + 1
      countAfterInsertProjects.size should be(count)
      projectService.projectLinkPublishable(savedProject.id) should be(false)
      projectService.getRoadAddressSingleProject(savedProject.id).nonEmpty should be(true)
      projectService.getRoadAddressSingleProject(savedProject.id).get.reservedParts.nonEmpty should be(true)
      val projectLinks = ProjectDAO.getProjectLinks(savedProject.id)
      val partitioned = projectLinks.partition(_.roadPartNumber == 205)
      val linkIds205 = partitioned._1.map(_.linkId).toSet
      val linkIds206 = partitioned._2.map(_.linkId).toSet
      reset(mockRoadLinkService)
      when(mockRoadLinkService.getViiteRoadLinksHistoryFromVVH(any[Set[Long]])).thenReturn(Seq())
      when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean], any[Boolean])).thenAnswer(
        toMockAnswer(projectLinks, roadLink)
      )
      projectService.updateProjectLinks(savedProject.id, linkIds205, LinkStatus.UnChanged, "-", 0, 0, 0, Option.empty[Int]) should be(None)
      projectService.projectLinkPublishable(savedProject.id) should be(false)
      projectService.updateProjectLinks(savedProject.id, linkIds206, LinkStatus.UnChanged, "-", 0, 0, 0, Option.empty[Int]) should be(None)
      projectService.projectLinkPublishable(savedProject.id) should be(true)
      projectService.updateProjectLinks(savedProject.id, Set(5168573), LinkStatus.Terminated, "-", 0, 0, 0, Option.empty[Int]) should be(None)
      projectService.projectLinkPublishable(savedProject.id) should be(true)
      val changeProjectOpt = projectService.getChangeProject(savedProject.id)
      val change = changeProjectOpt.get
      val updatedProjectLinks = ProjectDAO.getProjectLinks(savedProject.id)
      updatedProjectLinks.exists { x => x.status == LinkStatus.UnChanged } should be(true)
      updatedProjectLinks.exists { x => x.status == LinkStatus.Terminated } should be(true)
      updatedProjectLinks.filter(pl => pl.linkId == 5168579).head.calibrationPoints should be((None, Some(CalibrationPoint(5168579, 15.173, 4681))))
      projectService.updateProjectLinks(savedProject.id, Set(5168579), LinkStatus.Terminated, "-", 0, 0, 0, Option.empty[Int])
      val updatedProjectLinks2 = ProjectDAO.getProjectLinks(savedProject.id)
      updatedProjectLinks2.filter(pl => pl.linkId == 5168579).head.calibrationPoints should be((None, None))
      updatedProjectLinks2.filter(pl => pl.linkId == 5168583).head.calibrationPoints should be((None, Some(CalibrationPoint(5168583, 63.8, 4666))))
      updatedProjectLinks2.filter(pl => pl.roadPartNumber == 205).exists { x => x.status == LinkStatus.Terminated } should be(false)
    }
    runWithRollback {
      projectService.getRoadAddressAllProjects()
    } should have size (count - 1)
  }

  test("Transfer and then terminate") {
    var count = 0
    val roadLink = RoadLink(5170939L, Seq(Point(535605.272, 6982204.22, 85.90899999999965))
      , 540.3960283713503, State, 99, TrafficDirection.AgainstDigitizing, UnknownLinkType, Some("25.06.2015 03:00:00"), Some("vvh_modified"), Map("MUNICIPALITYCODE" -> BigInt.apply(749)),
      InUse, NormalLinkInterface)
    runWithRollback {
      val countCurrentProjects = projectService.getRoadAddressAllProjects()
      val id = 0
      val addresses = List(ReservedRoadPart(5: Long, 5: Long, 207: Long, Some(5L), Some(Discontinuity.apply("jatkuva")), Some(8L), newLength = None, newDiscontinuity = None, newEly = None))
      val roadAddressProject = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", Seq(), None)
      val savedProject = projectService.createRoadLinkProject(roadAddressProject)
      mockForProject(savedProject.id, RoadAddressDAO.fetchByRoadPart(5,207).map(toProjectLink(savedProject)))
      projectService.saveProject(savedProject.copy(reservedParts = addresses))
      val countAfterInsertProjects = projectService.getRoadAddressAllProjects()
      count = countCurrentProjects.size + 1
      countAfterInsertProjects.size should be(count)
      projectService.projectLinkPublishable(savedProject.id) should be(false)
      val projectLinks = ProjectDAO.getProjectLinks(savedProject.id)
      val partitioned = projectLinks.partition(_.roadPartNumber == 207)
      val highestDistanceEnd = projectLinks.map(p => p.endAddrMValue).max
      val linkIds207 = partitioned._1.map(_.linkId).toSet
      reset(mockRoadLinkService)
      when(mockRoadLinkService.getViiteRoadLinksHistoryFromVVH(any[Set[Long]])).thenReturn(Seq())
      when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean], any[Boolean])).thenAnswer(
        toMockAnswer(projectLinks, roadLink)
      )
      projectService.updateProjectLinks(savedProject.id, linkIds207, LinkStatus.Transfer, "-", 0, 0, 0, Option.empty[Int]) should be(None)
      projectService.updateProjectLinks(savedProject.id, Set(5168510), LinkStatus.Terminated, "-", 0, 0, 0, Option.empty[Int]) should be(None)
      projectService.projectLinkPublishable(savedProject.id) should be(true)
      val changeProjectOpt = projectService.getChangeProject(savedProject.id)
      val change = changeProjectOpt.get
      val updatedProjectLinks = ProjectDAO.getProjectLinks(savedProject.id)
      updatedProjectLinks.exists { x => x.status == LinkStatus.Transfer } should be(true)
      updatedProjectLinks.exists { x => x.status == LinkStatus.Terminated } should be(true)
      updatedProjectLinks.filter(pl => pl.linkId == 5168540).head.calibrationPoints should be((Some(CalibrationPoint(5168540, 0.0, 0)), None))
      updatedProjectLinks.filter(pl => pl.linkId == 6463199).head.calibrationPoints should be((None, Some(CalibrationPoint(6463199, 442.89, highestDistanceEnd - projectLinks.filter(pl => pl.linkId == 5168510).head.endAddrMValue)))) //we terminated link with distance 172
      projectService.updateProjectLinks(savedProject.id, Set(5168540), LinkStatus.Terminated, "-", 0, 0, 0, Option.empty[Int]) should be(None)
      val updatedProjectLinks2 = ProjectDAO.getProjectLinks(savedProject.id)
      updatedProjectLinks2.filter(pl => pl.linkId == 6463199).head.calibrationPoints should be(None, Some(CalibrationPoint(6463199, 442.89, highestDistanceEnd - projectLinks.filter(pl => pl.linkId == 5168510).head.endAddrMValue - updatedProjectLinks.filter(pl => pl.linkId == 5168540).head.endAddrMValue)))
    }
    runWithRollback {
      projectService.getRoadAddressAllProjects()
    } should have size (count - 1)
  }

  test("Terminate then transfer") {
    var count = 0
    val roadLink = RoadLink(5170939L, Seq(Point(535605.272, 6982204.22, 85.90899999999965))
      , 540.3960283713503, State, 99, TrafficDirection.AgainstDigitizing, UnknownLinkType, Some("25.06.2015 03:00:00"), Some("vvh_modified"), Map("MUNICIPALITYCODE" -> BigInt.apply(749)),
      InUse, NormalLinkInterface)
    runWithRollback {
      val countCurrentProjects = projectService.getRoadAddressAllProjects()
      val id = 0
      val addresses = List(ReservedRoadPart(5: Long, 5: Long, 207: Long, Some(5L), Some(Discontinuity.apply("jatkuva")), Some(8L), newLength = None, newDiscontinuity = None, newEly = None))
      val roadAddressProject = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", Seq(), None)
      val savedProject = projectService.createRoadLinkProject(roadAddressProject)
      mockForProject(savedProject.id, RoadAddressDAO.fetchByRoadPart(5,207).map(toProjectLink(savedProject)))
      projectService.saveProject(savedProject.copy(reservedParts = addresses))
      val countAfterInsertProjects = projectService.getRoadAddressAllProjects()
      count = countCurrentProjects.size + 1
      countAfterInsertProjects.size should be(count)
      projectService.projectLinkPublishable(savedProject.id) should be(false)
      val projectLinks = ProjectDAO.getProjectLinks(savedProject.id)
      val partitioned = projectLinks.partition(_.roadPartNumber == 207)
      val highestDistanceStart = projectLinks.map(p => p.startAddrMValue).max
      val highestDistanceEnd = projectLinks.map(p => p.endAddrMValue).max
      val linkIds207 = partitioned._1.map(_.linkId).toSet
      reset(mockRoadLinkService)
      when(mockRoadLinkService.getViiteRoadLinksHistoryFromVVH(any[Set[Long]])).thenReturn(Seq())
      when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean], any[Boolean])).thenAnswer(
        toMockAnswer(projectLinks, roadLink)
      )
      projectService.updateProjectLinks(savedProject.id, Set(5168510), LinkStatus.Terminated, "-", 0, 0, 0, Option.empty[Int])
      projectService.updateProjectLinks(savedProject.id, linkIds207 - 5168510, LinkStatus.Transfer, "-", 0, 0, 0, Option.empty[Int])
      projectService.projectLinkPublishable(savedProject.id) should be(true)
      val changeProjectOpt = projectService.getChangeProject(savedProject.id)
      val change = changeProjectOpt.get
      val updatedProjectLinks = ProjectDAO.getProjectLinks(savedProject.id)
      updatedProjectLinks.exists { x => x.status == LinkStatus.Transfer } should be(true)
      updatedProjectLinks.exists { x => x.status == LinkStatus.Terminated } should be(true)
      updatedProjectLinks.filter(pl => pl.linkId == 5168540).head.calibrationPoints should be((Some(CalibrationPoint(5168540, 0.0, 0)), None))
      updatedProjectLinks.filter(pl => pl.linkId == 6463199).head.calibrationPoints should be((None, Some(CalibrationPoint(6463199, 442.89, highestDistanceEnd - 172)))) //we terminated link with distance 172
      projectService.updateProjectLinks(savedProject.id, Set(5168540), LinkStatus.Terminated, "-", 0, 0, 0, Option.empty[Int])
      val updatedProjectLinks2 = ProjectDAO.getProjectLinks(savedProject.id)
      updatedProjectLinks2.filter(pl => pl.linkId == 6463199).head.calibrationPoints should be(None, Some(CalibrationPoint(6463199, 442.89, highestDistanceEnd - projectLinks.filter(pl => pl.linkId == 5168510).head.endAddrMValue - updatedProjectLinks.filter(pl => pl.linkId == 5168540).head.endAddrMValue)))
    }
    runWithRollback {
      projectService.getRoadAddressAllProjects()
    } should have size (count - 1)
  }

  test("Terminate, new links and then transfer") {
    val roadLink = RoadLink(51L, Seq(Point(535605.272, 6982204.22, 85.90899999999965))
      , 540.3960283713503, State, 1, TrafficDirection.AgainstDigitizing, Motorway,
      Some("25.06.2015 03:00:00"), Some("vvh_modified"), Map("MUNICIPALITYCODE" -> BigInt.apply(749)),
      InUse, NormalLinkInterface)
    runWithRollback {
      val id = 0
      val addresses = List(ReservedRoadPart(5: Long, 5: Long, 205: Long, Some(5L), Some(Discontinuity.apply("jatkuva")), Some(8L), newLength = None, newDiscontinuity = None, newEly = None))
      val roadAddressProject = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("2021-01-01"), DateTime.now(), "Some additional info", Seq(), None)
      val savedProject = projectService.createRoadLinkProject(roadAddressProject)
      mockForProject(savedProject.id, RoadAddressDAO.fetchByRoadPart(5,205).map(toProjectLink(savedProject)))
      projectService.saveProject(savedProject.copy(reservedParts = addresses))
      val projectLinks = ProjectDAO.getProjectLinks(savedProject.id)
      projectLinks.size should be(66)

      val linkIds = projectLinks.map(pl => pl.track.value -> pl.linkId).groupBy(_._1).mapValues(_.map(_._2).toSet)
      val newLinkTemplates = Seq(ProjectLink(-1000L, 0L, 0L, Track.apply(99), Discontinuity.Continuous, 0L, 0L, None, None,
        None, 0L, 1234L, 0.0, 43.1, SideCode.Unknown, (None, None), false,
        Seq(Point(468.5, 0.5), Point(512.0, 0.0)), 0L, LinkStatus.Unknown, RoadType.PublicRoad, LinkGeomSource.NormalLinkInterface, 43.1, 0L, 0, false,
        None, 86400L),
        ProjectLink(-1000L, 0L, 0L, Track.apply(99), Discontinuity.Continuous, 0L, 0L, None, None,
          None, 0L, 1235L, 0.0, 71.1, SideCode.Unknown, (None, None), false,
          Seq(Point(510.0, 0.0), Point(581.0, 0.0)), 0L, LinkStatus.Unknown, RoadType.PublicRoad, LinkGeomSource.NormalLinkInterface, 71.1, 0L, 0, false,
          None, 86400L))
      projectService.updateProjectLinks(savedProject.id, Set(5172715, 5172714, 5172031, 5172030), LinkStatus.Terminated, "-", 5, 205, 0, None)
      linkIds.keySet.foreach( k =>
        projectService.updateProjectLinks(savedProject.id, linkIds(k) -- Set(5172715, 5172714, 5172031, 5172030), LinkStatus.Transfer, "-", 5, 205, k, None)
      )
      ProjectDAO.getProjectLinks(savedProject.id).size should be (66)
      when(mockRoadLinkService.fetchSuravageLinksByLinkIdsFromVVH(any[Set[Long]])).thenReturn(Seq())
      when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean], any[Boolean])).thenReturn(newLinkTemplates.take(1).map(toRoadLink))
      createProjectLinks(newLinkTemplates.take(1).map(_.linkId), savedProject.id, 5L, 205L, 1, 5, 2, 1, 8, "U").get("success") should be (Some(true))
      when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean], any[Boolean])).thenReturn(newLinkTemplates.tail.take(1).map(toRoadLink))
      createProjectLinks(newLinkTemplates.tail.take(1).map(_.linkId), savedProject.id, 5L, 205L, 2, 5, 2, 1, 8, "U").get("success") should be (Some(true))
      ProjectDAO.getProjectLinks(savedProject.id).size should be (68)
      val changeInfo = projectService.getChangeProject(savedProject.id)
      projectService.projectLinkPublishable(savedProject.id) should be(true)
      changeInfo.get.changeInfoSeq.foreach { ci =>
        ci.changeType match {
          case Termination =>
            ci.source.startAddressM should be(Some(0))
            ci.source.endAddressM should be(Some(546))
            ci.target.startAddressM should be(None)
            ci.target.endAddressM should be(None)
          case Transfer =>
            ci.source.startAddressM should be(Some(546))
            ci.source.endAddressM should be(Some(6730))
            ci.target.startAddressM should be(Some(57))
            (ci.source.startAddressM.get - ci.target.startAddressM.get) should be(ci.source.endAddressM.get - ci.target.endAddressM.get)
          case AddressChangeType.New =>
            ci.source.startAddressM should be(None)
            ci.target.startAddressM should be(Some(0))
            ci.source.endAddressM should be(None)
            ci.target.endAddressM should be(Some(57))
          case _ =>
            throw new RuntimeException(s"Nobody expects ${ci.changeType} inquisition!")
        }
      }
    }
  }

  test("Validate road part dates with project date - startDate") {
    runWithRollback {
      val projDate = DateTime.parse("1990-01-01")
      val addresses = List(ReservedRoadPart(5: Long, 5: Long, 205: Long, Some(5L), Some(Discontinuity.apply("jatkuva")), Some(8L), newLength = None, newDiscontinuity = None, newEly = None))
      val errorMsg = projectService.validateProjectDate(addresses, projDate)
      errorMsg should not be (None)
    }
  }

  test("Validate road part dates with project date - startDate valid") {
    runWithRollback {
      val projDate = DateTime.parse("2015-01-01")
      val addresses = List(ReservedRoadPart(5: Long, 5: Long, 205: Long, Some(5L), Some(Discontinuity.apply("jatkuva")), Some(8L), newLength = None, newDiscontinuity = None, newEly = None))
      val errorMsg = projectService.validateProjectDate(addresses, projDate)
      errorMsg should be(None)
    }
  }

  test("Validate road part dates with project date - startDate and endDate") {
    runWithRollback {
      val projDate = DateTime.parse("1990-01-01")
      val addresses = List(ReservedRoadPart(5: Long, 5: Long, 205: Long, Some(5L), Some(Discontinuity.apply("jatkuva")), Some(8L), newLength = None, newDiscontinuity = None, newEly = None))
      val errorMsg = projectService.validateProjectDate(addresses, projDate)
      errorMsg should not be (None)
    }
  }

  test("Validate road part dates with project date - startDate and endDate valid") {
    runWithRollback {
      val projDate = DateTime.parse("2018-01-01")
      val addresses = List(ReservedRoadPart(5: Long, 5: Long, 205: Long, Some(5L), Some(Discontinuity.apply("jatkuva")), Some(8L), newLength = None, newDiscontinuity = None, newEly = None))
      val errorMsg = projectService.validateProjectDate(addresses, projDate)
      errorMsg should be(None)
    }
  }

  test("process roadChange data and import the roadLink") {
    //First Create Mock Project, RoadLinks and

    runWithRollback {
      var projectId = 0L
      val roadNumber = 1943845
      val roadPartNumber = 1
      val linkId = 12345L
      //Creation of Test road
      val id = RoadAddressDAO.getNextRoadAddressId
      val ra = Seq(RoadAddress(id, roadNumber, roadPartNumber, RoadType.PublicRoad, Track.Combined, Discontinuous, 0L, 10L,
        Some(DateTime.parse("1901-01-01")), None, Option("tester"), 0, linkId, 0.0, 9.8, SideCode.TowardsDigitizing, 0, (None, None), false,
        Seq(Point(0.0, 0.0), Point(0.0, 9.8)), LinkGeomSource.NormalLinkInterface, 8))
      RoadAddressDAO.create(ra)
      val roadsBeforeChanges = RoadAddressDAO.fetchByLinkId(Set(linkId)).head
      when(mockRoadLinkService.getViiteRoadLinksHistoryFromVVH(any[Set[Long]])).thenReturn(Seq())
      when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean], any[Boolean])).thenReturn(Seq(RoadLink(linkId, ra.head.geometry, 9.8, State, 1, TrafficDirection.BothDirections,
        Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(167)))))
      when(mockRoadLinkService.fetchVVHRoadlinks(any[Set[Long]], any[Boolean])).thenReturn(Seq(VVHRoadlink(linkId, 167,
        ra.head.geometry, State, TrafficDirection.BothDirections, FeatureClass.AllOthers, None, Map("MUNICIPALITYCODE" -> BigInt(167)),
        ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)))
      when(mockRoadLinkService.fetchSuravageLinksByLinkIdsFromVVH(Set(linkId))).thenReturn(Seq())
      //Creation of test project with test links
      val project = RoadAddressProject(projectId, ProjectState.Incomplete, "testiprojekti", "Test", DateTime.now(), "Test",
        DateTime.parse("1990-01-01"), DateTime.now(), "info",
        List(ReservedRoadPart(Sequences.nextViitePrimaryKeySeqValue: Long, roadNumber: Long, roadPartNumber: Long, Some(5L), Some(Discontinuity.apply("jatkuva")), Some(8L), newLength = None, newDiscontinuity = None, newEly = None)), None)
      val savedProject = projectService.createRoadLinkProject(project)
      val projectLinkId = savedProject.reservedParts.head.startingLinkId
      projectLinkId.isEmpty should be(false)
      projectId = savedProject.id
      val unchangedValue = LinkStatus.UnChanged.value
      val projectLink = ProjectDAO.fetchFirstLink(projectId, roadNumber, roadPartNumber)
      projectLink.isEmpty should be(false)
      //Changing the status of the test link
      sqlu"""Update Project_Link Set Status = $unchangedValue
            Where ID = ${projectLink.get.id} And PROJECT_ID = $projectId""".execute

      //Creation of test road_address_changes
      sqlu"""insert into road_address_changes
             (project_id,change_type,new_road_number,new_road_part_number,new_track_code,new_start_addr_m,new_end_addr_m,new_discontinuity,new_road_type,new_ely,
              old_road_number,old_road_part_number,old_track_code,old_start_addr_m,old_end_addr_m)
             Values ($projectId,1,$roadNumber,$roadPartNumber,0,0,10,2,1,8,$roadNumber,$roadPartNumber,0,0,10)""".execute

      projectService.updateRoadAddressWithProjectLinks(ProjectState.Saved2TR, projectId)

      val roadsAfterChanges = RoadAddressDAO.fetchByLinkId(Set(linkId), false, true)
      roadsAfterChanges.size should be(1)
      val roadsAfterPublishing = roadsAfterChanges.filter(x => x.startDate.nonEmpty && x.endDate.isEmpty).head
      val endedAddress = roadsAfterChanges.filter(x => x.endDate.nonEmpty)

      roadsBeforeChanges.linkId should be(roadsAfterPublishing.linkId)
      roadsBeforeChanges.roadNumber should be(roadsAfterPublishing.roadNumber)
      roadsBeforeChanges.roadPartNumber should be(roadsAfterPublishing.roadPartNumber)
      endedAddress.isEmpty should be (true)
      roadsAfterPublishing.startDate.get.toString("yyyy-MM-dd") should be("1901-01-01")
    }
  }

  test("Calculate delta for project") {
    var count = 0
    val roadlink = RoadLink(5170939L, Seq(Point(535605.272, 6982204.22, 85.90899999999965))
      , 540.3960283713503, State, 99, TrafficDirection.AgainstDigitizing, UnknownLinkType, Some("25.06.2015 03:00:00"), Some("vvh_modified"), Map("MUNICIPALITYCODE" -> BigInt.apply(749)),
      InUse, NormalLinkInterface)
    runWithRollback {
      val countCurrentProjects = projectService.getRoadAddressAllProjects()
      val addresses = List(ReservedRoadPart(Sequences.nextViitePrimaryKeySeqValue, 5L, 205L, Some(5L), Some(Discontinuity.apply("jatkuva")), Some(8L), newLength = None, newDiscontinuity = None, newEly = None))
      val roadAddressProject = RoadAddressProject(0, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List(), None)
      val saved = projectService.createRoadLinkProject(roadAddressProject)
      mockForProject(saved.id, RoadAddressDAO.fetchByRoadPart(5 ,205).map(toProjectLink(saved)))
      val changed = saved.copy(reservedParts = addresses)
      projectService.saveProject(changed)
      val countAfterInsertProjects = projectService.getRoadAddressAllProjects()
      val projectLinks = ProjectDAO.fetchByProjectRoadPart(5, 205, saved.id)
      projectLinks.nonEmpty should be(true)
      count = countCurrentProjects.size + 1
      countAfterInsertProjects.size should be(count)
      sqlu"""UPDATE Project_link set status = ${LinkStatus.Terminated.value} Where PROJECT_ID = ${saved.id}""".execute
      val terminations = ProjectDeltaCalculator.delta(saved.id).terminations
      terminations should have size (projectLinks.size)
      sqlu"""UPDATE Project_link set status = ${LinkStatus.New.value} Where PROJECT_ID = ${saved.id}""".execute
      val newCreations = ProjectDeltaCalculator.delta(saved.id).newRoads
      newCreations should have size (projectLinks.size)
      val sections = ProjectDeltaCalculator.partition(terminations)
      sections should have size (2)
      sections.exists(_.track == Track.LeftSide) should be(true)
      sections.exists(_.track == Track.RightSide) should be(true)
      sections.groupBy(_.endMAddr).keySet.size should be(1)
    }
    runWithRollback {
      projectService.getRoadAddressAllProjects()
    } should have size (count - 1)
  }

  test("Calculate delta for project with discontinuity") {
    var count = 0
    val roadlink = RoadLink(5170939L, Seq(Point(535605.272, 6982204.22, 85.90899999999965))
      , 540.3960283713503, State, 99, TrafficDirection.AgainstDigitizing, UnknownLinkType, Some("25.06.2015 03:00:00"), Some("vvh_modified"), Map("MUNICIPALITYCODE" -> BigInt.apply(749)),
      InUse, NormalLinkInterface)
    runWithRollback {
      val countCurrentProjects = projectService.getRoadAddressAllProjects()
      val addresses = List(ReservedRoadPart(Sequences.nextViitePrimaryKeySeqValue, 5L, 205L, Some(5L), Some(Discontinuity.apply("jatkuva")), Some(8L), newLength = None, newDiscontinuity = None, newEly = None))
      val roadAddressProject = RoadAddressProject(0, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List(), None)
      val saved = projectService.createRoadLinkProject(roadAddressProject)
      mockForProject(saved.id, RoadAddressDAO.fetchByRoadPart(5,205).map(toProjectLink(saved)))
      val changed = saved.copy(reservedParts = addresses)
      projectService.saveProject(changed)
      val countAfterInsertProjects = projectService.getRoadAddressAllProjects()
      val projectLinks = ProjectDAO.fetchByProjectRoadPart(5, 205, saved.id)
      count = countCurrentProjects.size + 1
      countAfterInsertProjects.size should be(count)
      sqlu"""UPDATE Project_link set status = ${LinkStatus.Terminated.value} where project_id = ${saved.id}""".execute
      val terminations = ProjectDeltaCalculator.delta(saved.id).terminations
      terminations should have size (projectLinks.size)
      val modTerminations = terminations.map(t =>
        if (t.endAddrMValue == 4529)
          t.copy(discontinuity = Discontinuity.MinorDiscontinuity)
        else
          t
      )
      val sections = ProjectDeltaCalculator.partition(modTerminations)
      sections should have size (4)
      sections.exists(_.track == Track.LeftSide) should be(true)
      sections.exists(_.track == Track.RightSide) should be(true)
      sections.groupBy(_.track).keySet should have size (2)
    }
    runWithRollback {
      projectService.getRoadAddressAllProjects()
    } should have size (count - 1)
  }

  test("process roadChange data and expire the roadLink") {
    //First Create Mock Project, RoadLinks and

    runWithRollback {
      var projectId = 0L
      val roadNumber = 1943845
      val roadPartNumber = 1
      val linkId = 12345L
      //Creation of Test road
      val id = RoadAddressDAO.getNextRoadAddressId
      val ra = Seq(RoadAddress(id, roadNumber, roadPartNumber, RoadType.Unknown, Track.Combined, Discontinuous, 0L, 10L,
        Some(DateTime.parse("1901-01-01")), None, Option("tester"), 0, linkId, 0.0, 9.8, SideCode.TowardsDigitizing, 0, (None, None), false,
        Seq(Point(0.0, 0.0), Point(0.0, 9.8)), LinkGeomSource.NormalLinkInterface, 5))
      RoadAddressDAO.create(ra)
      val roadsBeforeChanges = RoadAddressDAO.fetchByLinkId(Set(linkId)).head

      when(mockRoadLinkService.getViiteRoadLinksHistoryFromVVH(any[Set[Long]])).thenReturn(Seq())
      when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean], any[Boolean])).thenReturn(Seq(RoadLink(linkId, ra.head.geometry, 9.8, State, 1, TrafficDirection.BothDirections,
        Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(167)))))
      when(mockRoadLinkService.fetchVVHRoadlinks(any[Set[Long]], any[Boolean])).thenReturn(Seq(VVHRoadlink(linkId, 167,
        ra.head.geometry, State, TrafficDirection.BothDirections, FeatureClass.AllOthers, None, Map("MUNICIPALITYCODE" -> BigInt(167)),
        ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)))
      when(mockRoadLinkService.fetchSuravageLinksByLinkIdsFromVVH(Set(linkId))).thenReturn(Seq())
      //Creation of test project with test links
      val project = RoadAddressProject(projectId, ProjectState.Incomplete, "testiprojekti", "Test", DateTime.now(), "Test",
        DateTime.parse("2020-01-01"), DateTime.now(), "info",
        List(ReservedRoadPart(Sequences.nextViitePrimaryKeySeqValue: Long, roadNumber: Long, roadPartNumber: Long, Some(5L), Some(Discontinuity.apply("jatkuva")), Some(8L), newLength = None, newDiscontinuity = None, newEly = None)), None)
      when(mockRoadLinkService.getViiteRoadLinksHistoryFromVVH(any[Set[Long]])).thenReturn(Seq())
      val proj = projectService.createRoadLinkProject(project)
      projectId = proj.id
      val projectLinkId = proj.reservedParts.head.startingLinkId.get
      val link = ProjectDAO.getProjectLinksByLinkId(projectLinkId).head
      val terminatedValue = LinkStatus.Terminated.value
      //Changing the status of the test link
      sqlu"""Update Project_Link Set Status = $terminatedValue
            Where ID = ${link.id}""".execute

      //Creation of test road_address_changes
      sqlu"""insert into road_address_changes
             (project_id,change_type,new_road_number,new_road_part_number,new_track_code,new_start_addr_m,new_end_addr_m,new_discontinuity,new_road_type,new_ely,
              old_road_number,old_road_part_number,old_track_code,old_start_addr_m,old_end_addr_m)
             Values ($projectId,5,$roadNumber,$roadPartNumber,1,0,10,1,1,8,$roadNumber,$roadPartNumber,1,0,10)""".execute

      projectService.updateRoadAddressWithProjectLinks(ProjectState.Saved2TR, projectId)

      val roadsAfterChanges = RoadAddressDAO.fetchByLinkId(Set(linkId))
      roadsAfterChanges.size should be(1)
      val endedAddress = roadsAfterChanges.filter(x => x.endDate.nonEmpty)
      endedAddress.head.endDate.nonEmpty should be(true)
      endedAddress.size should be(1)
      endedAddress.head.endDate.get.toString("yyyy-MM-dd") should be("2020-01-01")
      sql"""SELECT id FROM PROJECT_LINK WHERE project_id=$projectId""".as[Long].firstOption should be(None)
      sql"""SELECT id FROM PROJECT_RESERVED_ROAD_PART WHERE project_id=$projectId""".as[Long].firstOption should be(None)
    }
  }

  test("verify existence of roadAddressNumbersAndSEParts") {
    val roadNumber = 1943845
    val roadStartPart = 1
    val roadEndPart = 2
    runWithRollback {
      val id1 = RoadAddressDAO.getNextRoadAddressId
      val id2 = RoadAddressDAO.getNextRoadAddressId
      val ra = Seq(RoadAddress(id1, roadNumber, roadStartPart, RoadType.Unknown, Track.Combined, Discontinuous, 0L, 10L,
        Some(DateTime.parse("1901-01-01")), None, Option("tester"), 0, 12345L, 0.0, 9.8, SideCode.TowardsDigitizing, 0, (None, None), false,
        Seq(Point(0.0, 0.0), Point(0.0, 9.8)), LinkGeomSource.NormalLinkInterface, 5))
      val rb = Seq(RoadAddress(id2, roadNumber, roadEndPart, RoadType.Unknown, Track.Combined, Discontinuous, 0L, 10L,
        Some(DateTime.parse("1901-01-01")), None, Option("tester"), 0, 12345L, 0.0, 9.8, SideCode.TowardsDigitizing, 0, (None, None), false,
        Seq(Point(0.0, 0.0), Point(0.0, 9.8)), LinkGeomSource.NormalLinkInterface, 5))
      when(mockRoadLinkService.getViiteRoadLinksHistoryFromVVH(any[Set[Long]])).thenReturn(Seq())
      val shouldNotExist = projectService.checkRoadPartsExist(roadNumber, roadStartPart, roadEndPart)
      shouldNotExist.get should be("Tienumeroa ei ole olemassa, tarkista tiedot")
      RoadAddressDAO.create(ra)
      val roadNumberShouldNotExist = projectService.checkRoadPartsExist(roadNumber, roadStartPart + 1, roadEndPart)
      roadNumberShouldNotExist.get should be("Tiellä ei ole olemassa valittua alkuosaa, tarkista tiedot")
      val endingPartShouldNotExist = projectService.checkRoadPartsExist(roadNumber, roadStartPart, roadEndPart)
      endingPartShouldNotExist.get should be("Tiellä ei ole olemassa valittua loppuosaa, tarkista tiedot")
      RoadAddressDAO.create(rb)
      val allIsOk = projectService.checkRoadPartsExist(roadNumber, roadStartPart, roadEndPart)
      allIsOk should be(None)
    }
  }

  test("check reservability of a road") {
    val roadNumber = 1943845
    val roadStartPart = 1
    val roadEndPart = 2
    val roadlink = RoadLink(12345L, Seq(Point(535605.272, 6982204.22, 85.90899999999965))
      , 540.3960283713503, State, 99, TrafficDirection.AgainstDigitizing, UnknownLinkType, Some("25.06.2015 03:00:00"), Some("vvh_modified"), Map("MUNICIPALITYCODE" -> BigInt.apply(749)),
      InUse, NormalLinkInterface)
    when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean], any[Boolean])).thenReturn(Seq(roadlink))
    runWithRollback {
      val id1 = RoadAddressDAO.getNextRoadAddressId
      val ra = Seq(RoadAddress(id1, roadNumber, roadStartPart, RoadType.Unknown, Track.Combined, Discontinuous, 0L, 10L,
        Some(DateTime.parse("1901-01-01")), None, Option("tester"), 0, 12345L, 0.0, 9.8, SideCode.TowardsDigitizing, 0, (None, None), false,
        Seq(Point(0.0, 0.0), Point(0.0, 9.8)), LinkGeomSource.NormalLinkInterface, 8))
      val reservation = projectService.checkRoadPartsReservable(roadNumber, roadStartPart, roadEndPart)
      reservation.right.get.size should be(0)
      RoadAddressDAO.create(ra)
      val id2 = RoadAddressDAO.getNextRoadAddressId
      val rb = Seq(RoadAddress(id2, roadNumber, roadEndPart, RoadType.Unknown, Track.Combined, Discontinuous, 0L, 10L,
        Some(DateTime.parse("1901-01-01")), None, Option("tester"), 0, 12345L, 0.0, 9.8, SideCode.TowardsDigitizing, 0, (None, None), false,
        Seq(Point(0.0, 0.0), Point(0.0, 9.8)), LinkGeomSource.NormalLinkInterface, 8))
      RoadAddressDAO.create(rb)
      val reservationAfterB = projectService.checkRoadPartsReservable(roadNumber, roadStartPart, roadEndPart)
      reservationAfterB.right.get.size should be(2)
      reservationAfterB.right.get.map(_.roadNumber).distinct.size should be(1)
      reservationAfterB.right.get.map(_.roadNumber).distinct.head should be(roadNumber)
    }
  }

  test("get the road address project") {
    var count = 0
    runWithRollback {
      val roadlink = RoadLink(12345L, Seq(Point(535605.272, 6982204.22, 85.90899999999965))
        , 540.3960283713503, State, 99, TrafficDirection.AgainstDigitizing, UnknownLinkType, Some("25.06.2015 03:00:00"), Some("vvh_modified"), Map("MUNICIPALITYCODE" -> BigInt.apply(749)),
        InUse, NormalLinkInterface)
      val countCurrentProjects = projectService.getRoadAddressAllProjects()
      val addresses: List[ReservedRoadPart] = List(ReservedRoadPart(Sequences.nextViitePrimaryKeySeqValue: Long, 5: Long, 203: Long, Some(5L), Some(Discontinuity.apply("jatkuva")), Some(8L), newLength = None, newDiscontinuity = None, newEly = None))
      val roadAddressProject = RoadAddressProject(0, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", Seq(), None)
      val saved = projectService.createRoadLinkProject(roadAddressProject)
      mockForProject(saved.id, RoadAddressDAO.fetchByRoadPart(5, 203).map(toProjectLink(saved)))
      projectService.saveProject(saved.copy(reservedParts = addresses))
      when(mockRoadLinkService.getViiteRoadLinksHistoryFromVVH(any[Set[Long]])).thenReturn(Seq())
      val countAfterInsertProjects = projectService.getRoadAddressAllProjects()
      count = countCurrentProjects.size + 1
      countAfterInsertProjects.size should be(count)
      val project = projectService.getRoadAddressSingleProject(saved.id)
      project.size should be(1)
      project.head.name should be("TestProject")
    }
    runWithRollback {
      projectService.getRoadAddressAllProjects().size should be(count - 1)
    }
  }

  test("Check for new roadaddress reservation") {
    var count = 0
    runWithRollback {
      reset(mockRoadLinkService)
      val roadlink = RoadLink(12345L, Seq(Point(535605.272, 6982204.22, 85.90899999999965))
        , 540.3960283713503, State, 99, TrafficDirection.AgainstDigitizing, UnknownLinkType, Some("25.06.2015 03:00:00"), Some("vvh_modified"), Map("MUNICIPALITYCODE" -> BigInt.apply(749)),
        InUse, NormalLinkInterface)
      val countCurrentProjects = projectService.getRoadAddressAllProjects()
      val addresses: List[ReservedRoadPart] = List(ReservedRoadPart(Sequences.nextViitePrimaryKeySeqValue: Long, 5: Long, 203: Long, Some(5L), Some(Discontinuity.apply("jatkuva")), Some(8L), newLength = None, newDiscontinuity = None, newEly = None))
      val roadAddressProject = RoadAddressProject(0, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", Seq(), None)
      val saved = projectService.createRoadLinkProject(roadAddressProject)
      mockForProject(saved.id, RoadAddressDAO.fetchByRoadPart(5, 203).map(toProjectLink(saved)))
      projectService.saveProject(saved.copy(reservedParts = addresses))
      val countAfterInsertProjects = projectService.getRoadAddressAllProjects()
      count = countCurrentProjects.size + 1
      countAfterInsertProjects.size should be(count)
      val project = projectService.getRoadAddressSingleProject(saved.id)
      project.size should be(1)
      project.head.name should be("TestProject")
    }
    runWithRollback {
      projectService.getRoadAddressAllProjects().size should be(count - 1)
    }
  }

  test("Project ELY -1 update when reserving roadpart and revert to -1 when all reserved roadparts are removed") {
    val projectIdNew = 0L
    val roadNumber = 1943845
    val roadPartNumber = 1
    val linkId = 12345L

    runWithRollback {

      //Creation of Test road
      val id = RoadAddressDAO.getNextRoadAddressId
      val ra = Seq(RoadAddress(id, roadNumber, roadPartNumber, RoadType.Unknown, Track.Combined, Discontinuous, 0L, 10L,
        Some(DateTime.parse("1901-01-01")), None, Option("tester"), 0, linkId, 0.0, 9.8, SideCode.TowardsDigitizing, 0, (None, None), false,
        Seq(Point(0.0, 0.0), Point(0.0, 9.8)), LinkGeomSource.NormalLinkInterface, 8))
      val rl = RoadLink(linkId, ra.head.geometry, 9.8, State, 1, TrafficDirection.BothDirections,
        Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(167)))
      when(mockRoadLinkService.getViiteRoadLinksHistoryFromVVH(any[Set[Long]])).thenReturn(Seq())
      when(mockRoadLinkService.getRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean])).thenReturn(Seq(rl))
      when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean], any[Boolean])).thenReturn(Seq(rl))
      RoadAddressDAO.create(ra)
      val addresses: List[ReservedRoadPart] = List(ReservedRoadPart(Sequences.nextViitePrimaryKeySeqValue: Long, 5: Long, 203: Long, Some(5L), Some(Discontinuity.apply("jatkuva")), Some(8L), newLength = None, newDiscontinuity = None, newEly = None))

      //Creation of test project with test links
      val project = RoadAddressProject(projectIdNew, ProjectState.Incomplete, "testiprojekti", "Test", DateTime.now(), "Test",
        DateTime.now(), DateTime.now(), "info",
        List.empty, None)
      val proj = projectService.createRoadLinkProject(project)
      val returnedProject = projectService.getRoadAddressSingleProject(proj.id).get
      returnedProject.name should be("testiprojekti")
      returnedProject.ely.getOrElse(-1) should be(-1)
      mockForProject(proj.id, RoadAddressDAO.fetchByRoadPart(5, 203).map(toProjectLink(proj)))
      val projupdated = projectService.saveProject(proj.copy(reservedParts = addresses))
      val updatedReturnProject = projectService.getRoadAddressSingleProject(proj.id).head
      updatedReturnProject.ely.getOrElse(-1) should be(8)
      projectService.saveProject(proj.copy(ely = None)) //returns project to null
      val updatedReturnProject2 = projectService.getRoadAddressSingleProject(proj.id).head
      updatedReturnProject2.ely.getOrElse(-1) should be(-1)
      projectService.saveProject(proj.copy(reservedParts = addresses))
      val updatedReturnProject3 = projectService.getRoadAddressSingleProject(proj.id).head
      updatedReturnProject3.ely.getOrElse(-1) should be(8)
    }
  }

  test("get the project with it's reserved road parts") {
    var projectId = 0L
    val roadNumber = 1943845
    val roadPartNumber = 1
    val linkId = 12345L

    runWithRollback {

      //Creation of Test road
      val id = RoadAddressDAO.getNextRoadAddressId
      val ra = Seq(RoadAddress(id, roadNumber, roadPartNumber, RoadType.Unknown, Track.Combined, Discontinuous, 0L, 10L,
        Some(DateTime.parse("1901-01-01")), None, Option("tester"), 0, linkId, 0.0, 9.8, SideCode.TowardsDigitizing, 0, (None, None), false,
        Seq(Point(0.0, 0.0), Point(0.0, 9.8)), LinkGeomSource.NormalLinkInterface, 8))
      val rl = RoadLink(linkId, ra.head.geometry, 9.8, State, 1, TrafficDirection.BothDirections,
        Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(167)))
      when(mockRoadLinkService.getViiteRoadLinksHistoryFromVVH(any[Set[Long]])).thenReturn(Seq())
      when(mockRoadLinkService.getRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean])).thenReturn(Seq(rl))
      when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean], any[Boolean])).thenReturn(Seq(rl))
      RoadAddressDAO.create(ra)

      //Creation of test project with test links
      val project = RoadAddressProject(projectId, ProjectState.Incomplete, "testiprojekti", "Test", DateTime.now(), "Test",
        DateTime.now(), DateTime.now(), "info",
        List(ReservedRoadPart(Sequences.nextViitePrimaryKeySeqValue: Long, roadNumber: Long, roadPartNumber: Long, Some(5L), Some(Discontinuity.apply("jatkuva")), Some(8L), newLength = None, newDiscontinuity = None, newEly = None)), None)
      val proj = projectService.createRoadLinkProject(project)
      val returnedProject = projectService.getRoadAddressSingleProject(proj.id).get
      returnedProject.name should be("testiprojekti")
      returnedProject.reservedParts.size should be(1)
      returnedProject.reservedParts.head.roadNumber should be(roadNumber)
    }

  }

  test("error message when reserving already used road number&part (in other project ids). Empty error message if same road number&part but == proj id ") {
    runWithRollback {
      val idr = RoadAddressDAO.getNextRoadAddressId
      val id = Sequences.nextViitePrimaryKeySeqValue
      val rap = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("2700-01-01"), "TestUser", DateTime.parse("1972-03-03"), DateTime.parse("2700-01-01"), "Some additional info", List.empty[ReservedRoadPart], None)
      val projectLink = toProjectLink(rap, LinkStatus.New)(RoadAddress(idr, 5, 203, RoadType.Unknown, Track.Combined, Discontinuous,
        0L, 10L, Some(DateTime.parse("1901-01-01")), Some(DateTime.parse("1902-01-01")), Option("tester"), 0, 12345L, 0.0, 9.8,
        SideCode.TowardsDigitizing, 0, (None, None), false, Seq(Point(0.0, 0.0), Point(0.0, 9.8)), LinkGeomSource.NormalLinkInterface, 8))
      ProjectDAO.createRoadAddressProject(rap)

      val rap2 = RoadAddressProject(id + 1, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("2700-01-01"), "TestUser", DateTime.parse("1972-03-04"), DateTime.parse("2700-01-01"), "Some additional info", List.empty[ReservedRoadPart], None)
      val projectLink2 = toProjectLink(rap2, LinkStatus.New)(RoadAddress(idr, 5, 999, RoadType.Unknown, Track.Combined, Discontinuous,
        0L, 10L, Some(DateTime.parse("1901-01-01")), Some(DateTime.parse("1902-01-01")), Option("tester"), 0, 12345L, 0.0, 9.8,
        SideCode.TowardsDigitizing, 0, (None, None), false, Seq(Point(0.0, 0.0), Point(0.0, 9.8)), LinkGeomSource.NormalLinkInterface, 8))
      ProjectDAO.createRoadAddressProject(rap2)

      val projectLink3 = toProjectLink(rap, LinkStatus.New)(RoadAddress(idr, 5, 999, RoadType.Unknown, Track.Combined, Discontinuous,
        0L, 10L, Some(DateTime.parse("1901-01-01")), Some(DateTime.parse("1902-01-01")), Option("tester"), 0, 12345L, 0.0, 9.8,
        SideCode.TowardsDigitizing, 0, (None, None), false, Seq(Point(0.0, 0.0), Point(0.0, 9.8)), LinkGeomSource.NormalLinkInterface, 8))

      val p = ProjectAddressLink(idr, projectLink.linkId, projectLink.geometry,
        1, AdministrativeClass.apply(1), LinkType.apply(1), RoadLinkType.apply(1), ConstructionType.apply(1), projectLink.linkGeomSource, RoadType.PublicUnderConstructionRoad, "", 111, Some(""), Some("vvh_modified"),
        Map(), projectLink.roadNumber, projectLink.roadPartNumber, 2, -1, projectLink.discontinuity.value,
        projectLink.startAddrMValue, projectLink.endAddrMValue, projectLink.startMValue, projectLink.endMValue,
        projectLink.sideCode,
        projectLink.calibrationPoints._1,
        projectLink.calibrationPoints._2, Anomaly.None, projectLink.lrmPositionId, projectLink.status, 0)

      mockForProject(id, Seq(p))

      val message1project1 = projectService.addNewLinksToProject(Seq(projectLink), id, "U", p.linkId).getOrElse("")
      val links = ProjectDAO.getProjectLinks(id)
      links.size should be(0)
      message1project1 should be("TIE 5 OSA 203 on jo olemassa projektin alkupäivänä 03.03.1972, tarkista tiedot") //check that it is reserved in roadaddress table

      val message1project2 = projectService.addNewLinksToProject(Seq(projectLink2), id + 1, "U", p.linkId)
      val links2 = ProjectDAO.getProjectLinks(id + 1)
      links2.size should be(1)
      message1project2 should be(None)

      val message2project1 = projectService.addNewLinksToProject(Seq(projectLink3), id, "U", p.linkId).getOrElse("")
      val links3 = ProjectDAO.getProjectLinks(id)
      links3.size should be(0)
      message2project1 should be("TIE 5 OSA 999 on jo varattuna projektissa TestProject, tarkista tiedot")
    }
  }

  test("parsePrefillData no-link from vvh") {
    projectService.parsePreFillData(Seq.empty[VVHRoadlink]) should be(Left("Link could not be found in VVH"))
  }

  test("parsePrefillData contains correct info") {
    val attributes1 = Map("ROADNUMBER" -> BigInt(100), "ROADPARTNUMBER" -> BigInt(100))
    val newRoadLink1 = VVHRoadlink(1, 2, List(Point(0.0, 0.0), Point(20.0, 0.0)), AdministrativeClass.apply(1), TrafficDirection.BothDirections, FeatureClass.DrivePath, None, attributes1)
    projectService.parsePreFillData(Seq(newRoadLink1)) should be(Right(PreFillInfo(100, 100)))
  }

  test("parsePrefillData incomplete data") {
    val attributes1 = Map("ROADNUMBER" -> BigInt(2))
    val newRoadLink1 = VVHRoadlink(1, 2, List(Point(0.0, 0.0), Point(20.0, 0.0)), AdministrativeClass.apply(1), TrafficDirection.BothDirections, FeatureClass.DrivePath, None, attributes1)
    projectService.parsePreFillData(Seq(newRoadLink1)) should be(Left("Link does not contain valid prefill info"))
  }

  test("changing project ELY") {
    runWithRollback {
      val roadAddressProject = RoadAddressProject(0, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List.empty[ReservedRoadPart], None, None)
      when(mockRoadLinkService.getViiteRoadLinksHistoryFromVVH(any[Set[Long]])).thenReturn(Seq())
      val project = projectService.createRoadLinkProject(roadAddressProject)
      project.ely should be(None)
      val result = projectService.setProjectEly(project.id, 2)
      result should be(None)
      val result2 = projectService.setProjectEly(project.id, 2)
      result2 should be(None)
      val result3 = projectService.setProjectEly(project.id, 3)
      result3.isEmpty should be(false)
    }
  }

  test("split road address save behaves correctly on transfer + new") {
    val road = 5L
    val roadPart = 205L
    val origStartM = 1024L
    val origEndM = 1547L
    val origStartD = Some(DateTime.now().minusYears(10))
    val linkId = 1049L
    val endM = 520.387
    val suravageLinkId = 5774839L
    val user = Some("user")
    val project = RoadAddressProject(-1L, Sent2TR, "split", user.get, DateTime.now(), user.get,
      DateTime.now().plusMonths(2), DateTime.now(), "", Seq(), None, None)
    val roadAddress = RoadAddress(1L, 5L, 205L, PublicRoad, Track.Combined, Continuous, origStartM, origEndM, origStartD,
      None, None, 1L, linkId, 0.0, endM, SideCode.TowardsDigitizing, 86400L, (None, None), false, Seq(Point(1024.0, 0.0), Point(1025.0, 1544.386)),
      LinkGeomSource.NormalLinkInterface, 8L, false)
    val transferAndNew = Seq(ProjectLink(2L, 5, 205, Track.Combined, Continuous, 1028, 1128, Some(DateTime.now()), None, user,
      2L, suravageLinkId, 0.0, 99.384, SideCode.TowardsDigitizing, (None, None), false, Seq(Point(1024.0, 0.0), Point(1024.0, 99.384)),
      -1L, LinkStatus.Transfer, PublicRoad, LinkGeomSource.SuravageLinkInterface, 99.384, 1L, 8L, false, Some(linkId), 748800L),
      ProjectLink(3L, 5, 205, Track.Combined, Continuous, 1128, 1205, Some(DateTime.now()), None, user,
        3L, suravageLinkId, 99.384, 176.495, SideCode.TowardsDigitizing, (None, None), false, Seq(Point(1024.0, 99.384), Point(1101.111, 99.384)),
        -1L, LinkStatus.New, PublicRoad, LinkGeomSource.SuravageLinkInterface, 77.111, 1L, 8L, false, Some(linkId), 748800L),
      ProjectLink(4L, 5, 205, Track.Combined, Continuous, origStartM+100L, origEndM, Some(DateTime.now()), None, user,
        4L, linkId, 99.384, endM, SideCode.TowardsDigitizing, (None, None), false, Seq(Point(1024.0, 99.384), Point(1025.0, 1544.386)),
        -1L, LinkStatus.Terminated, PublicRoad, LinkGeomSource.NormalLinkInterface, endM - 99.384, 1L, 8L, false, Some(suravageLinkId), 748800L))
    val result = projectService.createSplitRoadAddress(roadAddress, transferAndNew, project)
    result should have size(4)
    result.count(_.terminated) should be (1)
    result.count(_.startDate == roadAddress.startDate) should be (2)
    result.count(_.startDate.get == project.startDate) should be (2)
    result.count(_.endDate.isEmpty) should be (2)
  }

  test("split road address save behaves correctly on unchanged + new") {
    val road = 5L
    val roadPart = 205L
    val origStartM = 1024L
    val origEndM = 1547L
    val origStartD = Some(DateTime.now().minusYears(10))
    val linkId = 1049L
    val endM = 520.387
    val suravageLinkId = 5774839L
    val user = Some("user")
    val project = RoadAddressProject(-1L, Sent2TR, "split", user.get, DateTime.now(), user.get,
      DateTime.now().plusMonths(2), DateTime.now(), "", Seq(), None, None)
    val roadAddress = RoadAddress(1L, 5L, 205L, PublicRoad, Track.Combined, Continuous, origStartM, origEndM, origStartD,
      None, None, 1L, linkId, 0.0, endM, SideCode.TowardsDigitizing, 86400L, (None, None), false, Seq(Point(1024.0, 0.0), Point(1025.0, 1544.386)),
      LinkGeomSource.NormalLinkInterface, 8L, false)
    val unchangedAndNew = Seq(ProjectLink(2L, 5, 205, Track.Combined, Continuous, origStartM, origStartM+100L, Some(DateTime.now()), None, user,
      2L, suravageLinkId, 0.0, 99.384, SideCode.TowardsDigitizing, (None, None), false, Seq(Point(1024.0, 0.0), Point(1024.0, 99.384)),
      -1L, LinkStatus.UnChanged, PublicRoad, LinkGeomSource.SuravageLinkInterface, 99.384, 1L, 8L, false, Some(linkId), 85088L),
      ProjectLink(3L, 5, 205, Track.Combined, Continuous, origStartM+100L, origStartM+177L, Some(DateTime.now()), None, user,
        3L, suravageLinkId, 99.384, 176.495, SideCode.TowardsDigitizing, (None, None), false, Seq(Point(1024.0, 99.384), Point(1101.111, 99.384)),
        -1L, LinkStatus.New, PublicRoad, LinkGeomSource.SuravageLinkInterface, 77.111, 1L, 8L, false, Some(linkId), 85088L),
      ProjectLink(4L, 5, 205, Track.Combined, Continuous, origStartM+100L, origEndM, Some(DateTime.now()), None, user,
        4L, linkId, 99.384, endM, SideCode.TowardsDigitizing, (None, None), false, Seq(Point(1024.0, 99.384), Point(1025.0, 1544.386)),
        -1L, LinkStatus.Terminated, PublicRoad, LinkGeomSource.NormalLinkInterface, endM - 99.384, 1L, 8L, false, Some(suravageLinkId), 85088L))
    val result = projectService.createSplitRoadAddress(roadAddress, unchangedAndNew, project)
    result should have size(3)
    result.count(_.terminated) should be (1)
    result.count(_.startDate == roadAddress.startDate) should be (2)
    result.count(_.startDate.get == project.startDate) should be (1)
    result.count(_.endDate.isEmpty) should be (2)
  }

  test("verify correction of a null ELY code project") {
    runWithRollback {
      val rap = RoadAddressProject(0L, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("1901-01-01"),
        "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info",
        Seq(), None)
      val project = projectService.createRoadLinkProject(rap)
      val id = project.id
      val roadParts = RoadAddressDAO.fetchByRoadPart(5, 207).map(toProjectLink(project))
      mockForProject(id, roadParts)
      ProjectDAO.reserveRoadPart(id, roadParts.head.roadNumber, roadParts.head.roadPartNumber, "TestUser")
      ProjectDAO.create(Seq(roadParts.head))
      ProjectDAO.getProjectEly(project.id).isEmpty should be (true)
      projectService.correctNullProjectEly()
      ProjectDAO.getProjectEly(project.id).isEmpty should be (false)
    }
  }
}
