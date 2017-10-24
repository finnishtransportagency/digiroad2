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

class ProjectServiceSpec  extends FunSuite with Matchers with BeforeAndAfter {
  val properties: Properties = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/digiroad2.properties"))
    props
  }
  val mockProjectService= MockitoSugar.mock[ProjectService]
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

    val projectServiceWithRoadAddressMock= new ProjectService(mockRoadAddressService, mockRoadLinkService, mockEventBus) {
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
      roadAddress.endDate, modifiedBy=Option(project.createdBy), 0L, roadAddress.linkId, roadAddress.startMValue, roadAddress.endMValue,
      roadAddress.sideCode, roadAddress.calibrationPoints, floating = false, roadAddress.geometry, project.id, status, RoadType.PublicRoad, roadAddress.linkGeomSource, GeometryUtils.geometryLength(roadAddress.geometry), roadAddress.id)
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
        projectLinks.groupBy(_.linkId).filterKeys(l => ids.contains(l)).mapValues{pl =>
          val startP = Point(pl.map(_.startAddrMValue).min, 0.0)
          val endP = Point(pl.map(_.endAddrMValue).max, 0.0)
          val maxLen = pl.map(_.endMValue).max
          val midP = Point((startP.x + endP.x)*.5,
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
      val addresses: List[ReservedRoadPart] = List(ReservedRoadPart(5: Long, 5: Long, 203: Long, 5: Double, 5: Long, Discontinuity.apply("jatkuva"), 8: Long, None: Option[DateTime], None: Option[DateTime]))
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

      when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean],any[Boolean])).thenReturn(Seq(roadLink))
      val addresses = List(ReservedRoadPart(5: Long, 1130: Long, 4: Long, 5: Double, 5: Long, Discontinuity.apply("jatkuva"), 8: Long, None: Option[DateTime], None: Option[DateTime]))
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

  test("Calculate delta for project") {
    var count = 0
    val roadlink = RoadLink(5170939L, Seq(Point(535605.272, 6982204.22, 85.90899999999965))
      , 540.3960283713503, State, 99, TrafficDirection.AgainstDigitizing, UnknownLinkType, Some("25.06.2015 03:00:00"), Some("vvh_modified"), Map("MUNICIPALITYCODE" -> BigInt.apply(749)),
      InUse, NormalLinkInterface)
    when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean],any[Boolean])).thenReturn(Seq(roadlink))
    runWithRollback {
      val countCurrentProjects = projectService.getRoadAddressAllProjects()
      val addresses = List(ReservedRoadPart(0L, 5L, 205L, 5.0, 5: Long, Discontinuity.apply("jatkuva"), 8: Long, None: Option[DateTime], None: Option[DateTime]))
      val roadAddressProject = RoadAddressProject(0, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List(), None)
      val saved = projectService.createRoadLinkProject(roadAddressProject)
      val changed = saved.copy(reservedParts = addresses)
      projectService.saveProject(changed)
      val countAfterInsertProjects = projectService.getRoadAddressAllProjects()
      val projectLinks = ProjectDAO.fetchByProjectRoadPart(5, 205, saved.id)
      projectLinks.nonEmpty should be (true)
      count = countCurrentProjects.size + 1
      countAfterInsertProjects.size should be(count)
      sqlu"""UPDATE Project_link set status = ${LinkStatus.Terminated.value}""".execute
      val terminations = ProjectDeltaCalculator.delta(saved.id).terminations
      terminations should have size (projectLinks.size)
      sqlu"""UPDATE Project_link set status = ${LinkStatus.New.value}""".execute
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
    when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean],any[Boolean])).thenReturn(Seq(roadlink))
    runWithRollback {
      val countCurrentProjects = projectService.getRoadAddressAllProjects()
      val addresses = List(ReservedRoadPart(0L, 5L, 205L, 5.0, 5: Long, Discontinuity.apply("jatkuva"), 8: Long, None: Option[DateTime], None: Option[DateTime]))
      val roadAddressProject = RoadAddressProject(0, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List(), None)
      val saved = projectService.createRoadLinkProject(roadAddressProject)
      val changed = saved.copy(reservedParts = addresses)
      projectService.saveProject(changed)
      val countAfterInsertProjects = projectService.getRoadAddressAllProjects()
      val projectLinks = ProjectDAO.fetchByProjectRoadPart(5, 205, saved.id)
      count = countCurrentProjects.size + 1
      countAfterInsertProjects.size should be(count)
      sqlu"""UPDATE Project_link set status = ${LinkStatus.Terminated.value}""".execute
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

  test("update project link status and check project status") {
    var count = 0
    val roadLink = RoadLink(5170939L, Seq(Point(535605.272, 6982204.22, 85.90899999999965))
      , 540.3960283713503, State, 99, TrafficDirection.AgainstDigitizing, UnknownLinkType, Some("25.06.2015 03:00:00"), Some("vvh_modified"), Map("MUNICIPALITYCODE" -> BigInt.apply(749)),
      InUse, NormalLinkInterface)
    when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean], any[Boolean])).thenReturn(Seq(roadLink))
    runWithRollback {
      val countCurrentProjects = projectService.getRoadAddressAllProjects()
      val id = 0
      val addresses = List(ReservedRoadPart(5: Long, 5: Long, 205: Long, 5: Double, 5: Long, Discontinuity.apply("jatkuva"), 8: Long, None: Option[DateTime], None: Option[DateTime]))
      val roadAddressProject = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", addresses, None)
      val saved = projectService.createRoadLinkProject(roadAddressProject)
      val countAfterInsertProjects = projectService.getRoadAddressAllProjects()
      count = countCurrentProjects.size + 1
      countAfterInsertProjects.size should be(count)
      val projectLinks = ProjectDAO.getProjectLinks(saved.id)
      val partitioned = projectLinks.partition(_.roadPartNumber == 205)
      val linkIds205 = partitioned._1.map(_.linkId).toSet
      when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(linkIds205, false, false)).thenReturn(
        partitioned._1.map(pl => roadLink.copy(linkId = pl.linkId, geometry = Seq(Point(pl.startAddrMValue, 0.0), Point(pl.endAddrMValue, 0.0)))))


      projectService.projectLinkPublishable(saved.id) should be(false)
      val linkIds = ProjectDAO.getProjectLinks(saved.id).map(_.linkId).toSet
      projectService.updateProjectLinks(saved.id, linkIds, LinkStatus.Terminated, "-", 0, 0, Option.empty[Int])
      projectService.projectLinkPublishable(saved.id) should be(true)
    }
    runWithRollback {
      projectService.getRoadAddressAllProjects()
    } should have size (count - 1)
  }

  test("update project link numbering and check project status") {
    var count = 0
    val roadLinks = Seq(
      RoadLink(5170939L, Seq(Point(535605.272, 6982204.22, 85.90899999999965))
        , 540.3960283713503, State, 99, TrafficDirection.AgainstDigitizing, UnknownLinkType, Some("25.06.2015 03:00:00"), Some("vvh_modified"), Map("MUNICIPALITYCODE" -> BigInt.apply(749)),
        InUse, NormalLinkInterface))

    when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean], any[Boolean])).thenReturn(roadLinks)
    runWithRollback {
      val countCurrentProjects = projectService.getRoadAddressAllProjects()
      val id = 0
      val addresses = List(
        ReservedRoadPart(0: Long, 5: Long, 207: Long, 5: Double, 5: Long, Discontinuity.apply("jatkuva"), 8: Long, None: Option[DateTime], None: Option[DateTime]))
      val roadAddressProject = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(),
        "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", addresses, None)
      val saved = projectService.createRoadLinkProject(roadAddressProject)
      val countAfterInsertProjects = projectService.getRoadAddressAllProjects()
      count = countCurrentProjects.size + 1
      countAfterInsertProjects.size should be(count)
      val projectLinks = ProjectDAO.getProjectLinks(saved.id)
      projectLinks.isEmpty should be (false)
      val partitioned = projectLinks.partition(_.roadPartNumber == 207)
      val linkIds207 = partitioned._1.map(_.linkId).toSet
      when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(linkIds207, false, false)).thenReturn(
        partitioned._1.map(pl => roadLinks.head.copy(linkId = pl.linkId, geometry = Seq(Point(pl.startAddrMValue, 0.0), Point(pl.endAddrMValue, 0.0)))))

      projectService.projectLinkPublishable(saved.id) should be(false)
      val linkIds = ProjectDAO.getProjectLinks(saved.id).map(_.linkId).toSet
      projectService.updateProjectLinks(saved.id, linkIds, LinkStatus.Numbering, "-", 99999, 1, Option.empty[Int])
      val afterNumberingLinks = ProjectDAO.getProjectLinks(saved.id)
      afterNumberingLinks.foreach(l => (l.roadNumber == 99999 && l.roadPartNumber == 1 ) should be (true))
    }

  }

  test("Splitting link test") {
    reset(mockRoadLinkService)
    reset(mockRoadAddressService)
    val projectId=Sequences.nextViitePrimaryKeySeqValue
    val lrmPositionId=Sequences.nextLrmPositionPrimaryKeySeqValue
    val roadLink = RoadLink(1, Seq(Point(0,0),Point(0,45.3),Point(0,87))
      , 540.3960283713503, State, 99, TrafficDirection.AgainstDigitizing, UnknownLinkType, Some("25.06.2015 03:00:00"), Some("vvh_modified"), Map("MUNICIPALITYCODE" -> BigInt.apply(749)),
      InUse, NormalLinkInterface)
    val suravageAddressLink= RoadAddressLink(2,2,Seq(Point(0,0),Point(0,45.3),Point(0,123)),123,
      AdministrativeClass.apply(1),LinkType.apply(1),RoadLinkType.UnknownRoadLinkType,ConstructionType.Planned,LinkGeomSource.SuravageLinkInterface,RoadType.PublicRoad,"testRoad",
      8,None,None,null,1,1,Track.Combined.value,8,Discontinuity.Continuous.value,0,123,"","",0,123,SideCode.AgainstDigitizing,None,None,Anomaly.None,1)
    val options=SplitOptions(Point(0,45.3),LinkStatus.UnChanged,LinkStatus.New,1,1,Track.Combined,Discontinuity.Continuous,1,LinkGeomSource.NormalLinkInterface,RoadType.PublicRoad,projectId)
    when(mockRoadAddressService.getSuravageRoadLinkAddressesByLinkIds(any[Set[Long]])).thenReturn(Seq(suravageAddressLink))
    when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(any[BoundingRectangle],any[Set[Int]], any[Boolean])).thenReturn(Seq(roadLink))
    val rap = RoadAddressProject(projectId, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("2700-01-01"), "TestUser", DateTime.parse("2700-01-01"), DateTime.now(), "Some additional info", List.empty[ReservedRoadPart], None)
    runWithRollback {
      ProjectDAO.createRoadAddressProject(rap)
      sqlu""" insert into LRM_Position(id,start_Measure,end_Measure,Link_id) Values($lrmPositionId,0,87,1) """.execute
      sqlu""" INSERT INTO PROJECT_RESERVED_ROAD_PART (ID, ROAD_NUMBER, ROAD_PART_NUMBER, PROJECT_ID, CREATED_BY, ROAD_LENGTH, ADDRESS_LENGTH, DISCONTINUITY, ELY) VALUES (0,1,1,$projectId,'""',87,900,0,0)""".execute
      sqlu""" INSERT INTO PROJECT_LINK (ID, PROJECT_ID, TRACK_CODE, DISCONTINUITY_TYPE, ROAD_NUMBER, ROAD_PART_NUMBER, START_ADDR_M, END_ADDR_M, LRM_POSITION_ID, CREATED_BY, CREATED_DATE, STATUS) VALUES (1,$projectId,0,0,1,1,0,87,$lrmPositionId,'testuser',TO_DATE('2017-10-06 14:54:41', 'YYYY-MM-DD HH24:MI:SS'),0)""".execute
      val failmessage = projectServiceWithRoadAddressMock.splitSuravageLinkInTX(suravageAddressLink.linkId, "testUser", options)
      failmessage should be (None)
      val projectLinks=ProjectDAO.getProjectLinks(projectId)
      val newSuravageLink=projectLinks.filter(x=>x.linkGeomSource==LinkGeomSource.SuravageLinkInterface)
      val unchangedLink=projectLinks.filter(x=>x.status == LinkStatus.UnChanged).head
      val newLink=projectLinks.filter(x=>x.status == LinkStatus.New).head
      val templateLink=projectLinks.filter(x=>x.linkGeomSource!=LinkGeomSource.SuravageLinkInterface).head
      checkSplit(projectLinks, 45.3, 0, 45, 87, 123.0)
      reset(mockRoadLinkService)
      reset(mockRoadAddressService)
    }
  }

  private def checkSplit(projectLinks: Seq[ProjectLink], splitMValue: Double, startAddrMValue: Long,
                         splitAddrMValue: Long, endAddrMValue: Long, survageEndMValue: Double) = {
    val newSuravageLink=projectLinks.filter(x=>x.linkGeomSource==LinkGeomSource.SuravageLinkInterface)
    val unchangedLink=projectLinks.filter(x=>x.status == LinkStatus.UnChanged).head
    val newLink=projectLinks.filter(x=>x.status == LinkStatus.New).head
    val templateLink=projectLinks.filter(x=>x.linkGeomSource!=LinkGeomSource.SuravageLinkInterface).head
    projectLinks.count(x => x.connectedLinkId.isDefined) should be  (3)
    newLink.connectedLinkId should be  (Some(templateLink.linkId))
    unchangedLink.connectedLinkId should be (Some(templateLink.linkId))
    templateLink.connectedLinkId should be (Some(newLink.linkId))
    newLink.startMValue should be  (splitMValue)
    newLink.startAddrMValue should be  (splitAddrMValue)
    newLink.endAddrMValue should be  (endAddrMValue)
    newLink.endMValue should be (survageEndMValue)
    unchangedLink.startMValue should be (0)
    unchangedLink.startAddrMValue should be (startAddrMValue)
    unchangedLink.endAddrMValue should be (splitAddrMValue)
    unchangedLink.endMValue should be (splitMValue)
    templateLink.status should be (LinkStatus.Terminated)
    templateLink.startAddrMValue should be (newLink.startAddrMValue)
    templateLink.endAddrMValue should be (newLink.endAddrMValue)
    templateLink.roadAddressId should be (newLink.roadAddressId)
  }

  test("Split and revert links") {
    reset(mockRoadLinkService)
    reset(mockRoadAddressService)
    val projectId = Sequences.nextViitePrimaryKeySeqValue
    val roadLink = RoadLink(Sequences.nextViitePrimaryKeySeqValue, Seq(Point(0, 0), Point(0, 45.3), Point(0, 87))
      , 540.3960283713503, State, 99, TrafficDirection.AgainstDigitizing, UnknownLinkType, Some("25.06.2015 03:00:00"), Some("vvh_modified"), Map("MUNICIPALITYCODE" -> BigInt.apply(749)),
      InUse, NormalLinkInterface)
    val suravageAddressLink= RoadAddressLink(2,2,Seq(Point(0,0),Point(0,45.3),Point(0,123)),123,
      AdministrativeClass.apply(1), LinkType.apply(1), RoadLinkType.UnknownRoadLinkType, ConstructionType.Planned, LinkGeomSource.SuravageLinkInterface, RoadType.PublicRoad, "testRoad",
      8, None, None, null, 1, 1, Track.Combined.value, 8, Discontinuity.Continuous.value, 0, 123, "", "", 0, 123, SideCode.AgainstDigitizing, None, None, Anomaly.None, 1)
    val options = SplitOptions(Point(0, 25.3), LinkStatus.UnChanged, LinkStatus.New, 1, 1, Track.Combined, Discontinuity.Continuous, 1, LinkGeomSource.NormalLinkInterface, RoadType.PublicRoad, projectId)
    when(mockRoadAddressService.getSuravageRoadLinkAddressesByLinkIds(any[Set[Long]])).thenReturn(Seq(suravageAddressLink))
    when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(any[BoundingRectangle], any[Set[Int]], any[Boolean])).thenReturn(Seq(roadLink))
    val rap = RoadAddressProject(projectId, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("2700-01-01"), "TestUser", DateTime.parse("2700-01-01"), DateTime.now(), "Some additional info", List.empty[ReservedRoadPart], None)
    runWithRollback {
      val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
      val lrmPositionId2 = Sequences.nextLrmPositionPrimaryKeySeqValue
      val raId = Sequences.nextViitePrimaryKeySeqValue
      ProjectDAO.createRoadAddressProject(rap)
      sqlu""" insert into LRM_Position(id,start_Measure,end_Measure,Link_id) Values($lrmPositionId,0,87,1) """.execute
      sqlu""" INSERT INTO PROJECT_RESERVED_ROAD_PART (ID, ROAD_NUMBER, ROAD_PART_NUMBER, PROJECT_ID, CREATED_BY, ROAD_LENGTH, ADDRESS_LENGTH, DISCONTINUITY, ELY) VALUES (0,1,1,$projectId,'""',87,900,0,0)""".execute
      sqlu"""insert into lrm_position (ID, link_id, SIDE_CODE, start_measure, end_measure, adjusted_timestamp, link_source) values ($lrmPositionId2, 1, 1, 0.0, 87.0, 0, 1)""".execute
      sqlu"""insert into ROAD_ADDRESS (id, lrm_position_id, road_number, road_part_number,
         track_code, discontinuity, START_ADDR_M, END_ADDR_M, start_date, end_date, created_by,
         VALID_FROM, geometry, floating, calibration_points) VALUES ($raId, $lrmPositionId2, 1, 1, 0, 5, 0, 87, date'2011-01-01', null, 'foo', date'2011-01-01', MDSYS.SDO_GEOMETRY(4002,3067,NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1),MDSYS.SDO_ORDINATE_ARRAY(0,0,0,0,0,87.0,0,87)), 0, 0)""".execute
      sqlu""" INSERT INTO PROJECT_LINK (ID, PROJECT_ID, TRACK_CODE, DISCONTINUITY_TYPE, ROAD_NUMBER, ROAD_PART_NUMBER, START_ADDR_M, END_ADDR_M, LRM_POSITION_ID, CREATED_BY, CREATED_DATE, STATUS, ROAD_ADDRESS_ID) VALUES (1,$projectId,0,0,1,1,0,87,$lrmPositionId,'testuser',TO_DATE('2017-10-06 14:54:41', 'YYYY-MM-DD HH24:MI:SS'),0, $raId)""".execute
      RoadAddressDAO.fetchByIdMassQuery(Set(raId), true, true).size should be (1)
      projectServiceWithRoadAddressMock.splitSuravageLinkInTX(suravageAddressLink.linkId, "testUser", options) should be (None)
      val projectLinks=ProjectDAO.getProjectLinks(projectId)
      checkSplit(projectLinks, 25.3, 0, 25, 87, 123.0)
      when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean], any[Boolean]))
        .thenReturn(Seq(toRoadLink(suravageAddressLink), roadLink))
      when(mockRoadAddressService.getSuravageRoadLinkAddressesByLinkIds(any[Set[Long]])).thenReturn(Seq(suravageAddressLink))
      projectServiceWithRoadAddressMock.revertSplit(projectId, 1) should be (None)
      reset(mockRoadLinkService)
      reset(mockRoadAddressService)
    }
  }

  test("Updating split link test") {
    reset(mockRoadLinkService)
    reset(mockRoadAddressService)
    val roadLink = RoadLink(Sequences.nextViitePrimaryKeySeqValue, Seq(Point(0,0),Point(0,45.3),Point(0,87))
      , 540.3960283713503, State, 99, TrafficDirection.AgainstDigitizing, UnknownLinkType, Some("25.06.2015 03:00:00"), Some("vvh_modified"), Map("MUNICIPALITYCODE" -> BigInt.apply(749)),
      InUse, NormalLinkInterface)
    val suravageAddressLink= RoadAddressLink(Sequences.nextViitePrimaryKeySeqValue,2,Seq(Point(0,0),Point(0,45.3),Point(0,123)),123,
      AdministrativeClass.apply(1),LinkType.apply(1),RoadLinkType.UnknownRoadLinkType,ConstructionType.Planned,LinkGeomSource.SuravageLinkInterface,RoadType.PublicRoad,"testRoad",
      8,None,None,null,1,1,Track.Combined.value,8,Discontinuity.Continuous.value,0,123,"","",0,123,SideCode.AgainstDigitizing,None,None,Anomaly.None,1)
    runWithRollback {
      val projectId=Sequences.nextViitePrimaryKeySeqValue
      val rap = RoadAddressProject(projectId, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("2700-01-01"), "TestUser", DateTime.parse("2700-01-01"), DateTime.now(), "Some additional info", List.empty[ReservedRoadPart], None)
      val options=SplitOptions(Point(0,45.3),LinkStatus.UnChanged,LinkStatus.New,1,1,Track.Combined,Discontinuity.Continuous,1,LinkGeomSource.NormalLinkInterface,RoadType.PublicRoad,projectId)
      val options2=SplitOptions(Point(0,65.3),LinkStatus.UnChanged,LinkStatus.New,1,1,Track.Combined,Discontinuity.Continuous,1,LinkGeomSource.NormalLinkInterface,RoadType.PublicRoad,projectId)
      when(mockRoadAddressService.getSuravageRoadLinkAddressesByLinkIds(any[Set[Long]])).thenReturn(Seq(suravageAddressLink))
      when(mockRoadLinkService.getRoadLinksWithComplementaryFromVVH(any[BoundingRectangle],any[Set[Int]],any[Boolean])).thenReturn(Seq(roadLink))
      val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
      val lrmPositionId2 = Sequences.nextLrmPositionPrimaryKeySeqValue
      val raId = Sequences.nextViitePrimaryKeySeqValue
      ProjectDAO.createRoadAddressProject(rap)
      sqlu""" insert into LRM_Position(id,start_Measure,end_Measure,Link_id) Values($lrmPositionId,0,87,1) """.execute
      sqlu""" INSERT INTO PROJECT_RESERVED_ROAD_PART (ID, ROAD_NUMBER, ROAD_PART_NUMBER, PROJECT_ID, CREATED_BY, ROAD_LENGTH, ADDRESS_LENGTH, DISCONTINUITY, ELY) VALUES (0,1,1,$projectId,'""',87,900,0,0)""".execute
      sqlu"""insert into lrm_position (ID, link_id, SIDE_CODE, start_measure, end_measure, adjusted_timestamp, link_source) values ($lrmPositionId2, 1, 1, 0.0, 87.0, 0, 1)""".execute
      sqlu"""insert into ROAD_ADDRESS (id, lrm_position_id, road_number, road_part_number,
         track_code, discontinuity, START_ADDR_M, END_ADDR_M, start_date, end_date, created_by,
         VALID_FROM, geometry, floating, calibration_points) VALUES ($raId, $lrmPositionId2, 1, 1, 0, 5, 0, 87, date'2011-01-01', null, 'foo', date'2011-01-01', MDSYS.SDO_GEOMETRY(4002,3067,NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1),MDSYS.SDO_ORDINATE_ARRAY(0,0,0,0,0,87.0,0,87)), 0, 0)""".execute
      sqlu""" INSERT INTO PROJECT_LINK (ID, PROJECT_ID, TRACK_CODE, DISCONTINUITY_TYPE, ROAD_NUMBER, ROAD_PART_NUMBER,
            START_ADDR_M, END_ADDR_M, LRM_POSITION_ID, CREATED_BY, CREATED_DATE, STATUS, ROAD_ADDRESS_ID) VALUES
            ($raId,$projectId,0,0,1,1,0,87,$lrmPositionId,'testuser',TO_DATE('2017-10-06 14:54:41', 'YYYY-MM-DD HH24:MI:SS'),0, $raId)""".execute
      RoadAddressDAO.fetchByIdMassQuery(Set(raId), true, true).size should be (1)
      ProjectDAO.getProjectLinks(projectId) should have size (1)
      projectServiceWithRoadAddressMock.splitSuravageLinkInTX(suravageAddressLink.linkId, "testUser", options) should be (None)
      val projectLinks=ProjectDAO.getProjectLinks(projectId)
      val newSuravageLink=projectLinks.filter(x=>x.linkGeomSource==LinkGeomSource.SuravageLinkInterface)
      val unchangedLink=projectLinks.filter(x=>x.status == LinkStatus.UnChanged).head
      val newLink=projectLinks.filter(x=>x.status == LinkStatus.New).head
      val templateLink=projectLinks.filter(x=>x.linkGeomSource!=LinkGeomSource.SuravageLinkInterface).head
      projectLinks.count(x => x.connectedLinkId.isDefined) should be  (3)
      newLink.connectedLinkId should be  (Some(templateLink.linkId))
      unchangedLink.connectedLinkId should be (Some(templateLink.linkId))
      templateLink.connectedLinkId should be (Some(newLink.linkId))
      newLink.startMValue should be  (45.3)
      newLink.startAddrMValue should be  (45)
      newLink.endAddrMValue should be  (87) //123-45,3 =~87
      newLink.endMValue should be (123)
      unchangedLink.startMValue should be (0)
      unchangedLink.startAddrMValue should be (0)
      unchangedLink.endAddrMValue should be (45)
      unchangedLink.endMValue should be (45.3)
      templateLink.status should be (LinkStatus.Terminated)
      templateLink.startAddrMValue should be (newLink.startAddrMValue)
      templateLink.endAddrMValue should be (newLink.endAddrMValue)
      templateLink.roadAddressId should be (newLink.roadAddressId)
      when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean], any[Boolean]))
        .thenReturn(Seq(toRoadLink(suravageAddressLink), roadLink))
      when(mockRoadAddressService.getSuravageRoadLinkAddressesByLinkIds(any[Set[Long]])).thenReturn(Seq(suravageAddressLink))
      projectServiceWithRoadAddressMock.splitSuravageLinkInTX(suravageAddressLink.linkId, "testUser", options2) should be (None)
      val projectLinks2=ProjectDAO.getProjectLinks(projectId)
      projectLinks2.count(x => x.connectedLinkId.isDefined) should be  (3)
      val newSuravageLink2=projectLinks2.filter(x=>x.linkGeomSource==LinkGeomSource.SuravageLinkInterface)
      val unchangedLink2=projectLinks2.filter(x=>x.status == LinkStatus.UnChanged).head
      val newLink2=projectLinks2.filter(x=>x.status == LinkStatus.New).head
      val templateLink2=projectLinks2.filter(x=>x.linkGeomSource!=LinkGeomSource.SuravageLinkInterface).head
      newLink2.connectedLinkId should be  (Some(templateLink.linkId))
      unchangedLink2.connectedLinkId should be (Some(templateLink.linkId))
      templateLink2.connectedLinkId should be (Some(newLink.linkId))
      newLink2.startMValue should be  (65.3)
      newLink2.startAddrMValue should be  (65)
      newLink2.endAddrMValue should be  (87) //123-45,3 =~87
      newLink2.endMValue should be (123)
      unchangedLink2.startMValue should be (0)
      unchangedLink2.startAddrMValue should be (0)
      unchangedLink2.endAddrMValue should be (65)
      unchangedLink2.endMValue should be (65.3)
      templateLink2.status should be (LinkStatus.Terminated)
      templateLink2.startAddrMValue should be (newLink2.startAddrMValue)
      templateLink2.endAddrMValue should be (newLink2.endAddrMValue)
      templateLink2.roadAddressId should be (newLink2.roadAddressId)
      reset(mockRoadLinkService)
      reset(mockRoadAddressService)
    }
  }

  test("fetch project data and send it to TR") {
    assume(testConnection)
    runWithRollback {
      val project = RoadAddressProject(1, ProjectState.Incomplete, "testiprojekti", "Test", DateTime.now(), "Test",
        DateTime.now(), DateTime.now(), "info", List(
          ReservedRoadPart(5: Long, 203: Long, 203: Long, 5: Double, 5: Long, Discontinuity.apply("jatkuva"), 8: Long,
            None: Option[DateTime], None: Option[DateTime])), None)
      ProjectDAO.createRoadAddressProject(project)
      sqlu""" insert into road_address_changes(project_id,change_type,new_road_number,new_road_part_number,new_track_code,new_start_addr_m,new_end_addr_m,new_discontinuity,new_road_type,new_ely) Values(1,1,6,1,1,0,10.5,1,1,8) """.execute
      //Assuming that there is data to show
      val responses = projectService.getRoadAddressChangesAndSendToTR(Set(1))
      responses.projectId should be(1)
    }
  }

  test("update ProjectStatus when TR saved") {
    val sent2TRState = ProjectState.apply(2) //notfinnished
    val savedState = ProjectState.apply(5)
    val projectId = 0
    val addresses = List(ReservedRoadPart(5: Long, 203: Long, 203: Long, 5: Double, 5: Long, Discontinuity.apply("jatkuva"), 8: Long, None: Option[DateTime], None: Option[DateTime]))
    val roadAddressProject = RoadAddressProject(projectId, ProjectState.apply(2), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List(), None)
    runWithRollback {
      val saved = projectService.createRoadLinkProject(roadAddressProject)
      val stateaftercheck = projectService.updateProjectStatusIfNeeded(sent2TRState, savedState, "", saved.id)
      stateaftercheck.description should be(ProjectState.Saved2TR.description)
    }

  }

  test("Update to TRerror state") {
    val sent2TRState = ProjectState.apply(2) //notfinnished
    val savedState = ProjectState.apply(3)
    val projectId = 0
    val addresses = List(ReservedRoadPart(5: Long, 203: Long, 203: Long, 5: Double, 5: Long, Discontinuity.apply("jatkuva"), 8: Long, None: Option[DateTime], None: Option[DateTime]))
    val roadAddressProject = RoadAddressProject(projectId, ProjectState.apply(2), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List(), None)
    runWithRollback {
      val saved = projectService.createRoadLinkProject(roadAddressProject)
      val stateaftercheck = projectService.updateProjectStatusIfNeeded(sent2TRState, savedState, "failed", saved.id)
      stateaftercheck.description should be(ProjectState.ErroredInTR.description)
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
      val ra = Seq(RoadAddress(id, roadNumber, roadPartNumber, RoadType.Unknown, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), None, Option("tester"), 0, linkId, 0.0, 9.8, SideCode.TowardsDigitizing, 0, (None, None), false,
        Seq(Point(0.0, 0.0), Point(0.0, 9.8)), LinkGeomSource.NormalLinkInterface))
      RoadAddressDAO.create(ra)
      val roadsBeforeChanges = RoadAddressDAO.fetchByLinkId(Set(linkId)).head

      when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(Set(linkId))).thenReturn(Seq(RoadLink(linkId, ra.head.geometry, 9.8, State, 1, TrafficDirection.BothDirections,
        Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(167)))))
      //Creation of test project with test links
      val project = RoadAddressProject(projectId, ProjectState.Incomplete, "testiprojekti", "Test", DateTime.now(), "Test",
        DateTime.parse("1990-01-01"), DateTime.now(), "info",
        List(ReservedRoadPart(0: Long, roadNumber: Long, roadPartNumber: Long, 5: Double, 5: Long, Discontinuity.apply("jatkuva"),
          8: Long, None: Option[DateTime], None: Option[DateTime])), None)
      val savedProject = projectService.createRoadLinkProject(project)
      val projectLinkId = savedProject.reservedParts.head.startingLinkId
      projectLinkId.isEmpty should be(false)
      projectId = savedProject.id
      val terminatedValue = LinkStatus.UnChanged.value
      val projectLink = ProjectDAO.fetchFirstLink(projectId, roadNumber, roadPartNumber)
      projectLink.isEmpty should be(false)
      //Changing the status of the test link
      sqlu"""Update Project_Link Set Status = $terminatedValue
            Where ID = ${projectLink.get.id} And PROJECT_ID = $projectId""".execute

      //Creation of test road_address_changes
      sqlu"""insert into road_address_changes
             (project_id,change_type,new_road_number,new_road_part_number,new_track_code,new_start_addr_m,new_end_addr_m,new_discontinuity,new_road_type,new_ely,
              old_road_number,old_road_part_number,old_track_code,old_start_addr_m,old_end_addr_m)
             Values ($projectId,5,$roadNumber,$roadPartNumber,1,0,10,1,1,8,$roadNumber,$roadPartNumber,1,0,10)""".execute

      projectService.updateRoadAddressWithProjectLinks(ProjectState.Saved2TR, projectId)

      val roadsAfterChanges=RoadAddressDAO.fetchByLinkId(Set(linkId))
      roadsAfterChanges.size should be (3)
      val roadsAfterPublishing = roadsAfterChanges.filter(x=>x.startDate.nonEmpty && x.endDate.isEmpty).head
      val endedAddress = roadsAfterChanges.filter(x=>x.endDate.nonEmpty)

      roadsBeforeChanges.linkId should be(roadsAfterPublishing.linkId)
      roadsBeforeChanges.roadNumber should be(roadsAfterPublishing.roadNumber)
      roadsBeforeChanges.roadPartNumber should be(roadsAfterPublishing.roadPartNumber)
      endedAddress.head.endDate.nonEmpty should be(true)
      endedAddress.size should be (1)
      endedAddress.head.endDate.get.toString("yyyy-MM-dd") should be("1990-01-01")
      roadsAfterPublishing.startDate.get.toString("yyyy-MM-dd") should be("1990-01-01")
    }
  }

  test("process roadChange data and expire the roadLink") {
    //First Create Mock Project, RoadLinks and

    runWithRollback {
      var projectId = Sequences.nextViitePrimaryKeySeqValue
      val roadNumber = 1943845
      val roadPartNumber = 1
      val linkId = 12345L
      //Creation of Test road
      val id = RoadAddressDAO.getNextRoadAddressId
      val ra = Seq(RoadAddress(id, roadNumber, roadPartNumber, RoadType.Unknown, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), None, Option("tester"), 0, linkId, 0.0, 9.8, SideCode.TowardsDigitizing, 0, (None, None), false,
        Seq(Point(0.0, 0.0), Point(0.0, 9.8)), LinkGeomSource.NormalLinkInterface))
      RoadAddressDAO.create(ra)
      val roadsBeforeChanges = RoadAddressDAO.fetchByLinkId(Set(linkId)).head

      when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(Set(linkId))).thenReturn(Seq(RoadLink(linkId, ra.head.geometry, 9.8, State, 1, TrafficDirection.BothDirections,
        Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(167)))))
      //Creation of test project with test links
      val project = RoadAddressProject(projectId, ProjectState.Incomplete, "testiprojekti", "Test", DateTime.now(), "Test",
        DateTime.parse("2020-01-01"), DateTime.now(), "info",
        List(ReservedRoadPart(0: Long, roadNumber: Long, roadPartNumber: Long, 5: Double, 5: Long, Discontinuity.apply("jatkuva"),
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

      val roadsAfterChanges=RoadAddressDAO.fetchByLinkId(Set(linkId))
      roadsAfterChanges.size should be (1)
      val endedAddress = roadsAfterChanges.filter(x=>x.endDate.nonEmpty)
      endedAddress.head.endDate.nonEmpty should be(true)
      endedAddress.size should be (1)
      endedAddress.head.endDate.get.toString("yyyy-MM-dd") should be("2020-01-01")
      sql"""SELECT id FROM PROJECT_LINK WHERE project_id=$projectId""".as[Long].firstOption should be (None)
      sql"""SELECT id FROM PROJECT_RESERVED_ROAD_PART WHERE project_id=$projectId""".as[Long].firstOption should be (None)
    }
  }



  test("verify existence of roadAddressNumbersAndSEParts") {
    val roadNumber = 1943845
    val roadStartPart = 1
    val roadEndPart = 2
    runWithRollback {
      val id1 = RoadAddressDAO.getNextRoadAddressId
      val id2 = RoadAddressDAO.getNextRoadAddressId
      val ra = Seq(RoadAddress(id1, roadNumber, roadStartPart, RoadType.Unknown, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), None, Option("tester"), 0, 12345L, 0.0, 9.8, SideCode.TowardsDigitizing, 0, (None, None), false,
        Seq(Point(0.0, 0.0), Point(0.0, 9.8)), LinkGeomSource.NormalLinkInterface))
      val rb = Seq(RoadAddress(id2, roadNumber, roadEndPart, RoadType.Unknown, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), None, Option("tester"), 0, 12345L, 0.0, 9.8, SideCode.TowardsDigitizing, 0, (None, None), false,
        Seq(Point(0.0, 0.0), Point(0.0, 9.8)), LinkGeomSource.NormalLinkInterface))
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
    when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean],any[Boolean])).thenReturn(Seq(roadlink))
    runWithRollback {
      val id1 = RoadAddressDAO.getNextRoadAddressId
      val ra = Seq(RoadAddress(id1, roadNumber, roadStartPart, RoadType.Unknown, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), None, Option("tester"), 0, 12345L, 0.0, 9.8, SideCode.TowardsDigitizing, 0, (None, None), false,
        Seq(Point(0.0, 0.0), Point(0.0, 9.8)), LinkGeomSource.NormalLinkInterface))
      val reservation = projectService.checkRoadPartsReservable(roadNumber, roadStartPart, roadEndPart)
      reservation.right.get.size should be(0)
      RoadAddressDAO.create(ra)
      val id2 = RoadAddressDAO.getNextRoadAddressId
      val rb = Seq(RoadAddress(id2, roadNumber, roadEndPart, RoadType.Unknown, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), None, Option("tester"), 0, 12345L, 0.0, 9.8, SideCode.TowardsDigitizing, 0, (None, None), false,
        Seq(Point(0.0, 0.0), Point(0.0, 9.8)), LinkGeomSource.NormalLinkInterface))
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
      when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean],any[Boolean])).thenReturn(Seq(roadlink))
      val countCurrentProjects = projectService.getRoadAddressAllProjects()
      val addresses: List[ReservedRoadPart] = List(ReservedRoadPart(5: Long, 5: Long, 203: Long, 5: Double, 5L, Discontinuity.apply("jatkuva"), 8: Long, None: Option[DateTime], None: Option[DateTime]))
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
      when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean],any[Boolean])).thenReturn(Seq(roadlink))
      val countCurrentProjects = projectService.getRoadAddressAllProjects()
      val addresses: List[ReservedRoadPart] = List(ReservedRoadPart(5: Long, 5: Long, 203: Long, 5: Double, 5L, Discontinuity.apply("jatkuva"), 8: Long, None: Option[DateTime], None: Option[DateTime]))
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
      val ra = Seq(RoadAddress(id, roadNumber, roadPartNumber, RoadType.Unknown, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), None, Option("tester"), 0, linkId, 0.0, 9.8, SideCode.TowardsDigitizing, 0, (None, None), false,
        Seq(Point(0.0, 0.0), Point(0.0, 9.8)), LinkGeomSource.NormalLinkInterface))
      val rl = RoadLink(linkId, ra.head.geometry, 9.8, State, 1, TrafficDirection.BothDirections,
        Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(167)))
      when(mockRoadLinkService.getRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean])).thenReturn(Seq(rl))
      when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean], any[Boolean])).thenReturn(Seq(rl))
      RoadAddressDAO.create(ra)

      //Creation of test project with test links
      val project = RoadAddressProject(projectId, ProjectState.Incomplete, "testiprojekti", "Test", DateTime.now(), "Test",
        DateTime.now(), DateTime.now(), "info",
        List(ReservedRoadPart(0: Long, roadNumber: Long, roadPartNumber: Long, 5: Double, 5L, Discontinuity.apply("jatkuva"),
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
      val projectLink = toProjectLink(rap, LinkStatus.New)(RoadAddress(idr, 5, 203, RoadType.Unknown, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), Some(DateTime.parse("1902-01-01")), Option("tester"), 0, 12345L, 0.0, 9.8, SideCode.TowardsDigitizing, 0, (None, None), false,
        Seq(Point(0.0, 0.0), Point(0.0, 9.8)), LinkGeomSource.NormalLinkInterface))
      ProjectDAO.createRoadAddressProject(rap)

      val rap2 = RoadAddressProject(id + 1, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("2700-01-01"), "TestUser", DateTime.parse("1972-03-04"), DateTime.parse("2700-01-01"), "Some additional info", List.empty[ReservedRoadPart], None)
      val projectLink2 = toProjectLink(rap2, LinkStatus.New)(RoadAddress(idr, 5, 999, RoadType.Unknown, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), Some(DateTime.parse("1902-01-01")), Option("tester"), 0, 12345L, 0.0, 9.8, SideCode.TowardsDigitizing, 0, (None, None), false,
        Seq(Point(0.0, 0.0), Point(0.0, 9.8)), LinkGeomSource.NormalLinkInterface))
      ProjectDAO.createRoadAddressProject(rap2)

      val projectLink3 = toProjectLink(rap, LinkStatus.New)(RoadAddress(idr, 5, 999, RoadType.Unknown, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), Some(DateTime.parse("1902-01-01")), Option("tester"), 0, 12345L, 0.0, 9.8, SideCode.TowardsDigitizing, 0, (None, None), false,
        Seq(Point(0.0, 0.0), Point(0.0, 9.8)), LinkGeomSource.NormalLinkInterface))

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
    projectService.parsePreFillData(Seq.empty[VVHRoadlink]) should be (Left("Link could not be found in VVH"))
  }

  test("parsePrefillData contains correct info") {
    val attributes1 = Map("ROADNUMBER" -> BigInt(100), "ROADPARTNUMBER" -> BigInt(100))
    val newRoadLink1 = VVHRoadlink(1, 2, List(Point(0.0, 0.0), Point(20.0, 0.0)), AdministrativeClass.apply(1),TrafficDirection.BothDirections, FeatureClass.DrivePath, None, attributes1)
    projectService.parsePreFillData(Seq(newRoadLink1)) should be (Right(PreFillInfo(100,100)))
  }

  test("parsePrefillData incomplete data") {
    val attributes1 = Map("ROADNUMBER" -> BigInt(2))
    val newRoadLink1 = VVHRoadlink(1, 2, List(Point(0.0, 0.0), Point(20.0, 0.0)), AdministrativeClass.apply(1),TrafficDirection.BothDirections, FeatureClass.DrivePath, None, attributes1)
    projectService.parsePreFillData(Seq(newRoadLink1)) should be (Left("Link does not contain valid prefill info"))
  }

  test("changing project ELY") {
    runWithRollback {
      val roadAddressProject = RoadAddressProject(0, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List.empty[ReservedRoadPart], None, None)
      val project = projectService.createRoadLinkProject(roadAddressProject)
      project.ely should be(None)
      val result = projectService.setProjectEly(project.id, 2)
      result should be (None)
      val result2 = projectService.setProjectEly(project.id, 2)
      result2 should be (None)
      val result3 = projectService.setProjectEly(project.id, 3)
      result3.isEmpty should be (false)
    }
  }

}
