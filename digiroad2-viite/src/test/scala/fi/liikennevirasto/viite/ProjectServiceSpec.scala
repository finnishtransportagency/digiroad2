package fi.liikennevirasto.viite

import java.net.ConnectException
import java.util.Properties

import fi.liikennevirasto.viite.util.{SplitOptions, StaticTestData}
import fi.liikennevirasto.digiroad2.asset.ConstructionType.InUse
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.{PolyLine, RoadLink}
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.Sequences
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, Point, RoadLinkService, _}
import fi.liikennevirasto.viite.dao.AddressChangeType._
import fi.liikennevirasto.viite.dao.Discontinuity.Discontinuous
import fi.liikennevirasto.viite.dao.{Discontinuity, ProjectDAO, ProjectState, RoadAddressProject, _}
import fi.liikennevirasto.viite.model.{Anomaly, ProjectAddressLink, RoadAddressLink, RoadAddressLinkLike}
import fi.liikennevirasto.viite.dao.{AddressChangeType, Discontinuity, ProjectState, RoadAddressProject, _}
import fi.liikennevirasto.viite.model.{Anomaly, ProjectAddressLink, RoadAddressLinkLike}
import fi.liikennevirasto.viite.process.{ProjectDeltaCalculator, ProjectSectionCalculator}
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpGet
import org.apache.http.conn.{ConnectTimeoutException, HttpHostConnectException}
import org.apache.http.impl.client.HttpClientBuilder
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

import scala.util.parsing.json.JSON

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

  private def toProjectLink(project: RoadAddressProject, status: LinkStatus)(roadAddress: RoadAddress): ProjectLink = {
    ProjectLink(id = NewRoadAddress, roadAddress.roadNumber, roadAddress.roadPartNumber, roadAddress.track,
      roadAddress.discontinuity, roadAddress.startAddrMValue, roadAddress.endAddrMValue, roadAddress.startDate,
      roadAddress.endDate, modifiedBy = Option(project.createdBy), 0L, roadAddress.linkId, roadAddress.startMValue, roadAddress.endMValue,
      roadAddress.sideCode, roadAddress.calibrationPoints, floating = false, roadAddress.geometry, project.id, status, RoadType.PublicRoad,
      roadAddress.linkGeomSource, GeometryUtils.geometryLength(roadAddress.geometry), roadAddress.id, roadAddress.ely, false)
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
      val roadlink = RoadLink(5175306, Seq(Point(535605.272, 6982204.22, 85.90899999999965))
        , 540.3960283713503, State, 99, TrafficDirection.AgainstDigitizing, UnknownLinkType, Some("25.06.2015 03:00:00"), Some("vvh_modified"), Map("MUNICIPALITYCODE" -> BigInt.apply(749)),
        InUse, NormalLinkInterface)
      when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean], any[Boolean])).thenReturn(Seq(roadlink))
      val countCurrentProjects = projectService.getRoadAddressAllProjects()
      val id = 0
      val addresses: List[ReservedRoadPart] = List(ReservedRoadPart(Sequences.nextViitePrimaryKeySeqValue: Long, 5: Long, 203: Long, 5: Double, 5: Long, Discontinuity.apply("jatkuva"), 8: Long, None: Option[DateTime], None: Option[DateTime]))
      val roadAddressProject = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", addresses, None)
      projectService.createRoadLinkProject(roadAddressProject)
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
      val roadLink = RoadLink(2226637L, Seq(Point(534148.494, 6978117.311, 94.54499999999825))
        , 378.264, State, 99, TrafficDirection.AgainstDigitizing, UnknownLinkType, Some("25.06.2015 03:00:00"), Some("vvh_modified"), Map("MUNICIPALITYCODE" -> BigInt.apply(749)),
        InUse, NormalLinkInterface)

      when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean], any[Boolean])).thenReturn(Seq(roadLink))
      val addresses = List(ReservedRoadPart(Sequences.nextViitePrimaryKeySeqValue: Long, 1130: Long, 4: Long, 5: Double, 5: Long, Discontinuity.apply("jatkuva"), 8: Long, None: Option[DateTime], None: Option[DateTime]))
      val roadAddressProject = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List(), None)
      val saved = projectService.createRoadLinkProject(roadAddressProject)
      val changed = saved.copy(reservedParts = addresses)
      projectService.saveProject(changed)
      val countAfterInsertProjects = projectService.getRoadAddressAllProjects()
      count = countCurrentProjects.size + 1
      countAfterInsertProjects.size should be(count)
    }
    runWithRollback {
      projectService.getRoadAddressAllProjects()
    } should have size (count - 1)
  }

  test("Unchanged with termination test, repreats termination update, checks calibration points are cleared and moved to correct positions") {
    var count = 0
    val roadLink = RoadLink(5170939L, Seq(Point(535605.272, 6982204.22, 85.90899999999965))
      , 540.3960283713503, State, 99, TrafficDirection.AgainstDigitizing, UnknownLinkType, Some("25.06.2015 03:00:00"), Some("vvh_modified"), Map("MUNICIPALITYCODE" -> BigInt.apply(749)),
      InUse, NormalLinkInterface)
    when(mockRoadLinkService.getViiteRoadLinksHistoryFromVVH(any[Set[Long]])).thenReturn(Seq())
    when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean], any[Boolean])).thenReturn(Seq(roadLink))
    runWithRollback {
      val countCurrentProjects = projectService.getRoadAddressAllProjects()
      val id = 0
      val addresses = List(ReservedRoadPart(5: Long, 5: Long, 205: Long, 5: Double, 5: Long, Discontinuity.apply("jatkuva"), 8: Long, None: Option[DateTime], None: Option[DateTime]),
        ReservedRoadPart(5: Long, 5: Long, 206: Long, 5: Double, 5: Long, Discontinuity.apply("jatkuva"), 8: Long, None: Option[DateTime], None: Option[DateTime]))
      val roadAddressProject = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", addresses, None)
      val savedProject = projectService.createRoadLinkProject(roadAddressProject)
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
      projectService.updateProjectLinks(savedProject.id, linkIds205, LinkStatus.UnChanged, "-", 0, 0, Option.empty[Int]) should be(None)
      projectService.projectLinkPublishable(savedProject.id) should be(false)
      projectService.updateProjectLinks(savedProject.id, linkIds206, LinkStatus.UnChanged, "-", 0, 0, Option.empty[Int]) should be(None)
      projectService.projectLinkPublishable(savedProject.id) should be(true)
      projectService.updateProjectLinks(savedProject.id, Set(5168573), LinkStatus.Terminated, "-", 0, 0, Option.empty[Int]) should be(None)
      projectService.projectLinkPublishable(savedProject.id) should be(true)
      val changeProjectOpt = projectService.getChangeProject(savedProject.id)
      val change = changeProjectOpt.get
      val updatedProjectLinks = ProjectDAO.getProjectLinks(savedProject.id)
      updatedProjectLinks.exists { x => x.status == LinkStatus.UnChanged } should be(true)
      updatedProjectLinks.exists { x => x.status == LinkStatus.Terminated } should be(true)
      updatedProjectLinks.filter(pl => pl.linkId == 5168579).head.calibrationPoints should be((None, Some(CalibrationPoint(5168579, 15.173, 4681))))
      projectService.updateProjectLinks(savedProject.id, Set(5168579), LinkStatus.Terminated, "-", 0, 0, Option.empty[Int])
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
    when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean], any[Boolean])).thenReturn(Seq(roadLink))
    runWithRollback {
      val countCurrentProjects = projectService.getRoadAddressAllProjects()
      val id = 0
      val addresses = List(ReservedRoadPart(5: Long, 5: Long, 207: Long, 5: Double, 5: Long, Discontinuity.apply("jatkuva"), 8: Long, None: Option[DateTime], None: Option[DateTime]))
      val roadAddressProject = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", addresses, None)
      val savedProject = projectService.createRoadLinkProject(roadAddressProject)
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
      projectService.updateProjectLinks(savedProject.id, linkIds207, LinkStatus.Transfer, "-", 0, 0, Option.empty[Int]) should be(None)
      projectService.updateProjectLinks(savedProject.id, Set(5168510), LinkStatus.Terminated, "-", 0, 0, Option.empty[Int]) should be(None)
      projectService.projectLinkPublishable(savedProject.id) should be(true)
      val changeProjectOpt = projectService.getChangeProject(savedProject.id)
      val change = changeProjectOpt.get
      val updatedProjectLinks = ProjectDAO.getProjectLinks(savedProject.id)
      updatedProjectLinks.exists { x => x.status == LinkStatus.Transfer } should be(true)
      updatedProjectLinks.exists { x => x.status == LinkStatus.Terminated } should be(true)
      updatedProjectLinks.filter(pl => pl.linkId == 5168540).head.calibrationPoints should be((Some(CalibrationPoint(5168540, 0.0, 0)), None))
      updatedProjectLinks.filter(pl => pl.linkId == 6463199).head.calibrationPoints should be((None, Some(CalibrationPoint(6463199, 442.89, highestDistanceEnd - projectLinks.filter(pl => pl.linkId == 5168510).head.endAddrMValue)))) //we terminated link with distance 172
      projectService.updateProjectLinks(savedProject.id, Set(5168540), LinkStatus.Terminated, "-", 0, 0, Option.empty[Int]) should be(None)
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
    when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean], any[Boolean])).thenReturn(Seq(roadLink))
    runWithRollback {
      val countCurrentProjects = projectService.getRoadAddressAllProjects()
      val id = 0
      val addresses = List(ReservedRoadPart(5: Long, 5: Long, 207: Long, 5: Double, 5: Long, Discontinuity.apply("jatkuva"), 8: Long, None: Option[DateTime], None: Option[DateTime]))
      val roadAddressProject = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", addresses, None)
      val savedProject = projectService.createRoadLinkProject(roadAddressProject)
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
      projectService.updateProjectLinks(savedProject.id, Set(5168510), LinkStatus.Terminated, "-", 0, 0, Option.empty[Int])
      projectService.updateProjectLinks(savedProject.id, linkIds207 - 5168510, LinkStatus.Transfer, "-", 0, 0, Option.empty[Int])
      projectService.projectLinkPublishable(savedProject.id) should be(true)
      val changeProjectOpt = projectService.getChangeProject(savedProject.id)
      val change = changeProjectOpt.get
      val updatedProjectLinks = ProjectDAO.getProjectLinks(savedProject.id)
      updatedProjectLinks.exists { x => x.status == LinkStatus.Transfer } should be(true)
      updatedProjectLinks.exists { x => x.status == LinkStatus.Terminated } should be(true)
      updatedProjectLinks.filter(pl => pl.linkId == 5168540).head.calibrationPoints should be((Some(CalibrationPoint(5168540, 0.0, 0)), None))
      updatedProjectLinks.filter(pl => pl.linkId == 6463199).head.calibrationPoints should be((None, Some(CalibrationPoint(6463199, 442.89, highestDistanceEnd - 172)))) //we terminated link with distance 172
      projectService.updateProjectLinks(savedProject.id, Set(5168540), LinkStatus.Terminated, "-", 0, 0, Option.empty[Int])
      val updatedProjectLinks2 = ProjectDAO.getProjectLinks(savedProject.id)
      updatedProjectLinks2.filter(pl => pl.linkId == 6463199).head.calibrationPoints should be(None, Some(CalibrationPoint(6463199, 442.89, highestDistanceEnd - projectLinks.filter(pl => pl.linkId == 5168510).head.endAddrMValue - updatedProjectLinks.filter(pl => pl.linkId == 5168540).head.endAddrMValue)))
    }
    runWithRollback {
      projectService.getRoadAddressAllProjects()
    } should have size (count - 1)
  }

  test("Terminate, new links and then transfer") {
    var count = 0
    val roadLink = RoadLink(51L, Seq(Point(535605.272, 6982204.22, 85.90899999999965))
      , 540.3960283713503, State, 1, TrafficDirection.AgainstDigitizing, Motorway,
      Some("25.06.2015 03:00:00"), Some("vvh_modified"), Map("MUNICIPALITYCODE" -> BigInt.apply(749)),
      InUse, NormalLinkInterface)
    when(mockRoadLinkService.getViiteRoadLinksHistoryFromVVH(any[Set[Long]])).thenReturn(Seq())
    when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean], any[Boolean])).thenReturn(Seq(roadLink))
    runWithRollback {
      val id = 0
      val addresses = List(ReservedRoadPart(5: Long, 5: Long, 205: Long, 5: Double, 5: Long, Discontinuity.apply("jatkuva"), 8: Long, None: Option[DateTime], None: Option[DateTime]))
      val roadAddressProject = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("2021-01-01"), DateTime.now(), "Some additional info", addresses, None)
      val savedProject = projectService.createRoadLinkProject(roadAddressProject)
      val projectLinks = ProjectDAO.getProjectLinks(savedProject.id)
      projectLinks.size should be(66)

      val linkIds = projectLinks.map(_.linkId).toSet
      val newLinkTemplates = Seq(ProjectLink(-1000L, 0L, 0L, Track.apply(99), Discontinuity.Continuous, 0L, 0L, None, None,
        None, 0L, 1234L, 0.0, 43.1, SideCode.Unknown, (None, None), false,
        Seq(Point(468.5, 0.5), Point(512.0, 0.0)), 0L, LinkStatus.Unknown, RoadType.PublicRoad, LinkGeomSource.NormalLinkInterface, 43.1, 0L, 0, false),
        ProjectLink(-1000L, 0L, 0L, Track.apply(99), Discontinuity.Continuous, 0L, 0L, None, None,
          None, 0L, 1235L, 0.0, 71.1, SideCode.Unknown, (None, None), false,
          Seq(Point(510.0, 0.0), Point(581.0, 0.0)), 0L, LinkStatus.Unknown, RoadType.PublicRoad, LinkGeomSource.NormalLinkInterface, 71.1, 0L, 0, false))
      reset(mockRoadLinkService)
      when(mockRoadLinkService.getViiteRoadLinksHistoryFromVVH(any[Set[Long]])).thenReturn(Seq())
      when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean], any[Boolean])).thenAnswer(
        toMockAnswer(projectLinks ++ newLinkTemplates, roadLink)
      )
      ProjectDAO.getProjectLinks(savedProject.id).foreach { l =>
        l.roadType should be(RoadType.UnknownOwnerRoad)
      }
      projectService.updateProjectLinks(savedProject.id, Set(5172715, 5172714, 5172031, 5172030), LinkStatus.Terminated, "-", 5, 205, None)
      projectService.updateProjectLinks(savedProject.id, linkIds -- Set(5172715, 5172714, 5172031, 5172030), LinkStatus.Transfer, "-", 5, 205, None)
      ProjectDAO.getProjectLinks(savedProject.id).size should be(66)
      projectService.createProjectLinks(newLinkTemplates.take(1).map(_.linkId).toSet, savedProject.id, 5L, 205L, 1, 5, 2, 1, 8, "U").get("success") should be(Some(true))
      projectService.createProjectLinks(newLinkTemplates.tail.take(1).map(_.linkId).toSet, savedProject.id, 5L, 205L, 2, 5, 2, 1, 8, "U").get("success") should be(Some(true))
      ProjectDAO.getProjectLinks(savedProject.id).size should be(68)
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
    val projDate = DateTime.parse("2015-01-01")
    val addresses = List(ReservedRoadPart(5: Long, 5: Long, 205: Long, 5: Double, 5: Long, Discontinuity.apply("jatkuva"), 8: Long, Option(DateTime.parse("2017-01-01")): Option[DateTime], None: Option[DateTime]))
    val errorMsg = projectService.validateProjectDate(addresses, projDate)
    errorMsg should not be (None)
  }

  test("Validate road part dates with project date - startDate valid") {
    val projDate = DateTime.parse("2015-01-01")
    val addresses = List(ReservedRoadPart(5: Long, 5: Long, 205: Long, 5: Double, 5: Long, Discontinuity.apply("jatkuva"), 8: Long, Option(DateTime.parse("2010-01-01")): Option[DateTime], None: Option[DateTime]))
    val errorMsg = projectService.validateProjectDate(addresses, projDate)
    errorMsg should be(None)
  }

  test("Validate road part dates with project date - startDate and endDate") {
    val projDate = DateTime.parse("2015-01-01")
    val addresses = List(ReservedRoadPart(5: Long, 5: Long, 205: Long, 5: Double, 5: Long, Discontinuity.apply("jatkuva"), 8: Long, Option(DateTime.parse("2010-01-01")): Option[DateTime], Option(DateTime.parse("2017-01-01")): Option[DateTime]))
    val errorMsg = projectService.validateProjectDate(addresses, projDate)
    errorMsg should not be (None)
  }

  test("Validate road part dates with project date - startDate and endDate valid") {
    val projDate = DateTime.parse("2018-01-01")
    val addresses = List(ReservedRoadPart(5: Long, 5: Long, 205: Long, 5: Double, 5: Long, Discontinuity.apply("jatkuva"), 8: Long, Option(DateTime.parse("2010-01-01")): Option[DateTime], Option(DateTime.parse("2017-01-01")): Option[DateTime]))
    val errorMsg = projectService.validateProjectDate(addresses, projDate)
    errorMsg should be(None)
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
      when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(Set(linkId))).thenReturn(Seq(RoadLink(linkId, ra.head.geometry, 9.8, State, 1, TrafficDirection.BothDirections,
        Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(167)))))
      //Creation of test project with test links
      val project = RoadAddressProject(projectId, ProjectState.Incomplete, "testiprojekti", "Test", DateTime.now(), "Test",
        DateTime.parse("1990-01-01"), DateTime.now(), "info",
        List(ReservedRoadPart(Sequences.nextViitePrimaryKeySeqValue: Long, roadNumber: Long, roadPartNumber: Long, 5: Double, 5: Long, Discontinuity.apply("jatkuva"),
          8: Long, None: Option[DateTime], None: Option[DateTime])), None)
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

      val roadsAfterChanges = RoadAddressDAO.fetchByLinkId(Set(linkId))
      roadsAfterChanges.size should be(2)
      val roadsAfterPublishing = roadsAfterChanges.filter(x => x.startDate.nonEmpty && x.endDate.isEmpty).head
      val endedAddress = roadsAfterChanges.filter(x => x.endDate.nonEmpty)

      roadsBeforeChanges.linkId should be(roadsAfterPublishing.linkId)
      roadsBeforeChanges.roadNumber should be(roadsAfterPublishing.roadNumber)
      roadsBeforeChanges.roadPartNumber should be(roadsAfterPublishing.roadPartNumber)
      endedAddress.head.endDate.nonEmpty should be(true)
      endedAddress.size should be(1)
      endedAddress.head.endDate.get.toString("yyyy-MM-dd") should be("1990-01-01")
      roadsAfterPublishing.startDate.get.toString("yyyy-MM-dd") should be("1990-01-01")
    }
  }

  test("Calculate delta for project") {
    var count = 0
    val roadlink = RoadLink(5170939L, Seq(Point(535605.272, 6982204.22, 85.90899999999965))
      , 540.3960283713503, State, 99, TrafficDirection.AgainstDigitizing, UnknownLinkType, Some("25.06.2015 03:00:00"), Some("vvh_modified"), Map("MUNICIPALITYCODE" -> BigInt.apply(749)),
      InUse, NormalLinkInterface)
    when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean], any[Boolean])).thenReturn(Seq(roadlink))
    runWithRollback {
      val countCurrentProjects = projectService.getRoadAddressAllProjects()
      val addresses = List(ReservedRoadPart(Sequences.nextViitePrimaryKeySeqValue, 5L, 205L, 5.0, 5: Long, Discontinuity.apply("jatkuva"), 8: Long, None: Option[DateTime], None: Option[DateTime]))
      val roadAddressProject = RoadAddressProject(0, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List(), None)
      val saved = projectService.createRoadLinkProject(roadAddressProject)
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
    when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean], any[Boolean])).thenReturn(Seq(roadlink))
    runWithRollback {
      val countCurrentProjects = projectService.getRoadAddressAllProjects()
      val addresses = List(ReservedRoadPart(Sequences.nextViitePrimaryKeySeqValue, 5L, 205L, 5.0, 5: Long, Discontinuity.apply("jatkuva"), 8: Long, None: Option[DateTime], None: Option[DateTime]))
      val roadAddressProject = RoadAddressProject(0, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List(), None)
      val saved = projectService.createRoadLinkProject(roadAddressProject)
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

  ignore("process roadChange data and expire the roadLink") {
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

      when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(Set(linkId))).thenReturn(Seq(RoadLink(linkId, ra.head.geometry, 9.8, State, 1, TrafficDirection.BothDirections,
        Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(167)))))
      //Creation of test project with test links
      val project = RoadAddressProject(projectId, ProjectState.Incomplete, "testiprojekti", "Test", DateTime.now(), "Test",
        DateTime.parse("2020-01-01"), DateTime.now(), "info",
        List(ReservedRoadPart(Sequences.nextViitePrimaryKeySeqValue: Long, roadNumber: Long, roadPartNumber: Long, 5: Double, 5: Long, Discontinuity.apply("jatkuva"),
          8: Long, None: Option[DateTime], None: Option[DateTime])), None)
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
      when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean], any[Boolean])).thenReturn(Seq(roadlink))
      val countCurrentProjects = projectService.getRoadAddressAllProjects()
      val addresses: List[ReservedRoadPart] = List(ReservedRoadPart(Sequences.nextViitePrimaryKeySeqValue: Long, 5: Long, 203: Long, 5: Double, 5L, Discontinuity.apply("jatkuva"), 8: Long, None: Option[DateTime], None: Option[DateTime]))
      val roadAddressProject = RoadAddressProject(0, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", addresses, None)
      val id = projectService.createRoadLinkProject(roadAddressProject).id
      val countAfterInsertProjects = projectService.getRoadAddressAllProjects()
      count = countCurrentProjects.size + 1
      countAfterInsertProjects.size should be(count)
      val project = projectService.getRoadAddressSingleProject(id)
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
      val roadlink = RoadLink(12345L, Seq(Point(535605.272, 6982204.22, 85.90899999999965))
        , 540.3960283713503, State, 99, TrafficDirection.AgainstDigitizing, UnknownLinkType, Some("25.06.2015 03:00:00"), Some("vvh_modified"), Map("MUNICIPALITYCODE" -> BigInt.apply(749)),
        InUse, NormalLinkInterface)
      when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean], any[Boolean])).thenReturn(Seq(roadlink))
      val countCurrentProjects = projectService.getRoadAddressAllProjects()
      val addresses: List[ReservedRoadPart] = List(ReservedRoadPart(Sequences.nextViitePrimaryKeySeqValue: Long, 5: Long, 203: Long, 5: Double, 5L, Discontinuity.apply("jatkuva"), 8: Long, None: Option[DateTime], None: Option[DateTime]))
      val roadAddressProject = RoadAddressProject(0, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", addresses, None)
      val id = projectService.createRoadLinkProject(roadAddressProject).id
      val countAfterInsertProjects = projectService.getRoadAddressAllProjects()
      count = countCurrentProjects.size + 1
      countAfterInsertProjects.size should be(count)
      val project = projectService.getRoadAddressSingleProject(id)
      project.size should be(1)
      project.head.name should be("TestProject")
    }
    runWithRollback {
      projectService.getRoadAddressAllProjects().size should be(count - 1)
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
      when(mockRoadLinkService.getRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean])).thenReturn(Seq(rl))
      when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean], any[Boolean])).thenReturn(Seq(rl))
      RoadAddressDAO.create(ra)

      //Creation of test project with test links
      val project = RoadAddressProject(projectId, ProjectState.Incomplete, "testiprojekti", "Test", DateTime.now(), "Test",
        DateTime.now(), DateTime.now(), "info",
        List(ReservedRoadPart(Sequences.nextViitePrimaryKeySeqValue: Long, roadNumber: Long, roadPartNumber: Long, 5: Double, 5L, Discontinuity.apply("jatkuva"),
          8: Long, None: Option[DateTime], None: Option[DateTime])), None)
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
        null, projectLink.roadNumber, projectLink.roadPartNumber, 2, -1, projectLink.discontinuity.value,
        projectLink.startAddrMValue, projectLink.endAddrMValue, projectLink.startMValue, projectLink.endMValue,
        projectLink.sideCode,
        projectLink.calibrationPoints._1,
        projectLink.calibrationPoints._2, Anomaly.None, projectLink.lrmPositionId, projectLink.status, 0)

      mockForProject(id, Seq(p))

      val message1project1 = projectService.addNewLinksToProject(Seq(p), id, projectLink.roadNumber,
        projectLink.roadPartNumber, projectLink.track.value.toLong, projectLink.discontinuity.value.toLong, projectLink.roadType.value, "U").getOrElse("")
      val links = ProjectDAO.getProjectLinks(id)
      links.size should be(0)
      message1project1 should be("TIE 5 OSA 203 on jo olemassa projektin alkupäivänä 03.03.1972, tarkista tiedot") //check that it is reserved in roadaddress table

      val message1project2 = projectService.addNewLinksToProject(Seq(p), id + 1, projectLink2.roadNumber,
        projectLink2.roadPartNumber, projectLink2.track.value.toLong, projectLink2.discontinuity.value.toLong, projectLink2.roadType.value, "U")
      val links2 = ProjectDAO.getProjectLinks(id + 1)
      links2.size should be(1)
      message1project2 should be(None)

      val message2project1 = projectService.addNewLinksToProject(Seq(p), id, projectLink3.roadNumber,
        projectLink3.roadPartNumber, projectLink3.track.value.toLong, projectLink3.discontinuity.value.toLong, projectLink3.roadType.value, "U").getOrElse("")
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

}
