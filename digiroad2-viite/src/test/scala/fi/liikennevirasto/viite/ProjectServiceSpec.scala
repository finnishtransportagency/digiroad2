package fi.liikennevirasto.viite

import java.net.ConnectException
import java.util.Properties

import fi.liikennevirasto.digiroad2.asset.ConstructionType.InUse
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.Sequences
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, Point, RoadLinkService, _}
import fi.liikennevirasto.viite.dao.Discontinuity.Discontinuous
import fi.liikennevirasto.viite.dao.{Discontinuity, ProjectState, RoadAddressProject, _}
import fi.liikennevirasto.viite.model.{Anomaly, ProjectAddressLink, RoadAddressLinkLike}
import fi.liikennevirasto.viite.process.ProjectDeltaCalculator
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
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  val roadAddressService = new RoadAddressService(mockRoadLinkService, mockEventBus) {
    override def withDynSession[T](f: => T): T = f

    override def withDynTransaction[T](f: => T): T = f
  }
  val projectService = new ProjectService(roadAddressService, mockRoadLinkService, mockEventBus) {
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

  val dr2properties: Properties = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/digiroad2.properties"))
    props
  }

  private def testConnection: Boolean = {
    val url = dr2properties.getProperty("digiroad2.tierekisteriViiteRestApiEndPoint")
    val request = new HttpGet(url)
    request.setConfig(RequestConfig.custom().setConnectTimeout(2500).build())
    val client = HttpClientBuilder.create().build()
    try {
      val response = client.execute(request)
      try {
        response.getStatusLine.getStatusCode >= 200
      } finally {
        response.close()
      }
    } catch {
      case e: HttpHostConnectException =>
        false
      case e: ConnectTimeoutException =>
        false
      case e: ConnectException =>
        false
    }
  }

  private def toProjectLink(project: RoadAddressProject)(roadAddress: RoadAddress): ProjectLink = {
    ProjectLink(id = NewRoadAddress, roadAddress.roadNumber, roadAddress.roadPartNumber, roadAddress.track,
      roadAddress.discontinuity, roadAddress.startAddrMValue, roadAddress.endAddrMValue, roadAddress.startDate,
      roadAddress.endDate, modifiedBy=Option(project.createdBy), 0L, roadAddress.linkId, roadAddress.startMValue, roadAddress.endMValue,
      roadAddress.sideCode, roadAddress.calibrationPoints, floating = false, roadAddress.geometry, project.id, LinkStatus.NotHandled, RoadType.PublicRoad, roadAddress.linkGeomSource, GeometryUtils.geometryLength(roadAddress.geometry))
  }

  private def toProjectAddressLink(ral: RoadAddressLinkLike): ProjectAddressLink = {
    ProjectAddressLink(ral.id, ral.linkId, ral.geometry, ral.length, ral.administrativeClass, ral.linkType, ral.roadLinkType,
      ral.constructionType, ral.roadLinkSource, ral.roadType, ral.roadName, ral.municipalityCode, ral.modifiedAt, ral.modifiedBy,
      ral.attributes, ral.roadNumber, ral.roadPartNumber, ral.trackCode, ral.elyCode, ral.discontinuity,
      ral.startAddressM, ral.endAddressM, ral.startMValue, ral.endMValue, ral.sideCode, ral.startCalibrationPoint, ral.endCalibrationPoint,
      ral.anomaly, ral.lrmPositionId, LinkStatus.Unknown)
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
  private def toRoadLink(ral: ProjectAddressLink): RoadLink = {
    RoadLink(ral.linkId, ral.geometry, ral.length, ral.administrativeClass, 1,
      extractTrafficDirection(ral.sideCode, Track.apply(ral.trackCode.toInt)), ral.linkType, ral.modifiedAt, ral.modifiedBy, Map(
        "MUNICIPALITYCODE" -> BigInt(749), "VERTICALLEVEL" -> BigInt(1), "SURFACETYPE" -> BigInt(1),
        "ROADNUMBER" -> BigInt(ral.roadNumber), "ROADPARTNUMBER" -> BigInt(ral.roadPartNumber)),
      ral.constructionType, ral.roadLinkSource)
  }

  private def toMockAnswer(projectLinks: Seq[ProjectLink], roadLink: RoadLink) = {
    new Answer[Seq[RoadLink]]() {
      override def answer(invocation: InvocationOnMock): Seq[RoadLink] = {
        val ids = invocation.getArguments.apply(0).asInstanceOf[Set[Long]]
        projectLinks.groupBy(_.linkId).filterKeys(l => ids.contains(l)).mapValues{pl =>
          val startP = Point(pl.map(_.startAddrMValue).min, 0.0)
          val endP = Point(pl.map(_.endAddrMValue).max, 0.0)
          val maxLen = pl.map(_.endMValue).max
          val midP = Point((startP.x + endP.x)*.5,
            if (endP.x - startP.x < maxLen) {
              Math.sqrt(maxLen * maxLen - (startP.x - endP.x) * (startP.x - endP.x)) / 2
            }
            else 0.0)
          roadLink.copy(linkId = pl.head.linkId, geometry =
            Seq(startP, midP, endP))
        }.values.toSeq
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

  test("create road link project without road parts") {
    runWithRollback {
      val roadAddressProject = RoadAddressProject(0, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List.empty[ReservedRoadPart], None)
      val (project, projLinkOpt, formLines, str) = projectService.createRoadLinkProject(roadAddressProject)
      projLinkOpt should be(None)
      formLines should have size (0)

    }
  }

  test("create road link project without valid roadParts") {
    val roadlink = RoadLink(5175306, Seq(Point(535605.272, 6982204.22, 85.90899999999965))
      , 540.3960283713503, State, 99, TrafficDirection.AgainstDigitizing, UnknownLinkType, Some("25.06.2015 03:00:00"), Some("vvh_modified"), Map("MUNICIPALITYCODE" -> BigInt.apply(749)),
      InUse, NormalLinkInterface)
    when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(Set(5175306L))).thenReturn(Seq(roadlink))
    runWithRollback {
      val roadAddressProject = RoadAddressProject(0, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List.empty[ReservedRoadPart], None)
      val (project, projLinkOpt, formLines, str) = projectService.createRoadLinkProject(roadAddressProject)
      projLinkOpt should be(None)
      formLines should have size (0)
    }
  }

  test("create and get projects by id") {
    var count = 0
    runWithRollback {
      val countCurrentProjects = projectService.getRoadAddressAllProjects()
      val id = 0
      val addresses: List[ReservedRoadPart] = List(ReservedRoadPart(5: Long, 203: Long, 203: Long, 5: Double, Discontinuity.apply("jatkuva"), 8: Long, None: Option[DateTime], None: Option[DateTime]))
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
      val addresses = List(ReservedRoadPart(5: Long, 203: Long, 203: Long, 5: Double, Discontinuity.apply("jatkuva"), 8: Long, None: Option[DateTime], None: Option[DateTime]))
      val roadAddressProject = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List(), None)
      val saved = projectService.createRoadLinkProject(roadAddressProject)._1
      val changed = saved.copy(reservedParts = addresses)
      projectService.saveRoadLinkProject(changed)
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
    when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean],any[Boolean])).thenReturn(Seq(roadLink))
    runWithRollback {
      val countCurrentProjects = projectService.getRoadAddressAllProjects()
      val id = 0
      val addresses = List(ReservedRoadPart(5: Long, 5: Long, 205: Long, 5: Double, Discontinuity.apply("jatkuva"), 8: Long, None: Option[DateTime], None: Option[DateTime]),
        ReservedRoadPart(5: Long, 5: Long, 206: Long, 5: Double, Discontinuity.apply("jatkuva"), 8: Long, None: Option[DateTime], None: Option[DateTime]))
      val roadAddressProject = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", addresses, None)
      val (savedProject, _, formLines, errMsg) = projectService.createRoadLinkProject(roadAddressProject)
      val countAfterInsertProjects = projectService.getRoadAddressAllProjects()
      count = countCurrentProjects.size + 1
      countAfterInsertProjects.size should be(count)
      projectService.projectLinkPublishable(savedProject.id) should be(false)
      val projectLinks = ProjectDAO.getProjectLinks(savedProject.id)
      val partitioned = projectLinks.partition(_.roadPartNumber == 205)
      val linkIds205 = partitioned._1.map(_.linkId).toSet
      val linkIds206 = partitioned._2.map(_.linkId).toSet
      reset(mockRoadLinkService)
      when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean], any[Boolean])).thenAnswer(
        toMockAnswer(projectLinks, roadLink)
      )

      projectService.updateProjectLinkStatus(savedProject.id, linkIds205, LinkStatus.UnChanged, "-")
      projectService.projectLinkPublishable(savedProject.id) should be(false)

      projectService.updateProjectLinkStatus(savedProject.id, linkIds206, LinkStatus.UnChanged, "-")
      projectService.projectLinkPublishable(savedProject.id) should be(true)

      projectService.updateProjectLinkStatus(savedProject.id, Set(5168573), LinkStatus.Terminated, "-")
      projectService.projectLinkPublishable(savedProject.id) should be(true)

      val changeProjectOpt = projectService.getChangeProject(savedProject.id)
      val change = changeProjectOpt.get
      val updatedProjectLinks=ProjectDAO.getProjectLinks(savedProject.id)
      updatedProjectLinks.exists { x=> x.status==LinkStatus.UnChanged } should be(true)
      updatedProjectLinks.exists { x=> x.status==LinkStatus.Terminated } should be(true)
      updatedProjectLinks.filter( pl => pl.linkId==5168579).head.calibrationPoints should be ((None,Some(CalibrationPoint(5168579,15.173,4681))))
      projectService.updateProjectLinkStatus(savedProject.id, Set(5168579), LinkStatus.Terminated, "-")
      val updatedProjectLinks2=ProjectDAO.getProjectLinks(savedProject.id)
      updatedProjectLinks2.filter( pl => pl.linkId==5168579).head.calibrationPoints should be ((None,None))
      updatedProjectLinks2.filter( pl => pl.linkId==5168583).head.calibrationPoints should be ((None,Some(CalibrationPoint(5168583,63.8,4666))))
      updatedProjectLinks2.filter(pl => pl.roadPartNumber==205 ).exists { x=> x.status==LinkStatus.Terminated } should be( false)
    }
    runWithRollback {
      projectService.getRoadAddressAllProjects()
    } should have size (count - 1)
  }
  ignore("Fetch project links") { // Needs more of mocking because of Futures + transactions disagreeing
    val roadLinkService = new RoadLinkService(new VVHClient(properties.getProperty("digiroad2.VVHRestApiEndPoint")), mockEventBus, new DummySerializer) {
      override def withDynSession[T](f: => T): T = f

      override def withDynTransaction[T](f: => T): T = f
    }
    val roadAddressService = new RoadAddressService(roadLinkService, mockEventBus) {
      override def withDynSession[T](f: => T): T = f

      override def withDynTransaction[T](f: => T): T = f
    }
    val projectService = new ProjectService(roadAddressService, roadLinkService, mockEventBus) {
      override def withDynSession[T](f: => T): T = f

      override def withDynTransaction[T](f: => T): T = f
    }
    runWithRollback {
      val addresses: List[ReservedRoadPart] = List(ReservedRoadPart(0: Long, 5: Long, 205: Long, 5: Double, Discontinuity.apply("jatkuva"), 8: Long, None: Option[DateTime], None: Option[DateTime]))
      val roadAddressProject = RoadAddressProject(0, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", addresses, None)
      val savedProject = projectService.createRoadLinkProject(roadAddressProject)._1
      val startingLinkId = ProjectDAO.getProjectLinks(savedProject.id).filter(_.track == Track.LeftSide).minBy(_.startAddrMValue).linkId
      val boundingRectangle = roadLinkService.fetchVVHRoadlinks(Set(startingLinkId)).map { vrl =>
        val x = vrl.geometry.map(l => l.x)
        val y = vrl.geometry.map(l => l.y)

        BoundingRectangle(Point(x.min, y.min) + Vector3d(-5.0, -5.0, 0.0), Point(x.max, y.max) + Vector3d(5.0, 5.0, 0.0))
      }.head

      val links = projectService.getProjectRoadLinks(savedProject.id, boundingRectangle, Seq(), Set(), true, true)
      links.nonEmpty should be(true)
      links.exists(_.status == LinkStatus.Unknown) should be(true)
      links.exists(_.status == LinkStatus.NotHandled) should be(true)
      val (unk, nh) = links.partition(_.status == LinkStatus.Unknown)
      nh.forall(l => l.roadNumber == 5 && l.roadPartNumber == 205) should be(true)
      unk.forall(l => l.roadNumber != 5 || l.roadPartNumber != 205) should be(true)
      nh.map(_.linkId).toSet.intersect(unk.map(_.linkId).toSet) should have size (0)
      unk.exists(_.attributes.getOrElse("ROADPARTNUMBER", "0").toString == "203") should be(true)
    }
  }

  test("Validate road part dates with project date - startDate") {
    val projDate = DateTime.parse("2015-01-01")
    val addresses = List(ReservedRoadPart(5: Long, 5: Long, 205: Long, 5: Double, Discontinuity.apply("jatkuva"), 8: Long, Option(DateTime.parse("2017-01-01")): Option[DateTime], None: Option[DateTime]))
    val errorMsg = projectService.validateProjectDate(addresses, projDate)
    errorMsg should not be (None)
  }

  test("Validate road part dates with project date - startDate valid") {
    val projDate = DateTime.parse("2015-01-01")
    val addresses = List(ReservedRoadPart(5: Long, 5: Long, 205: Long, 5: Double, Discontinuity.apply("jatkuva"), 8: Long, Option(DateTime.parse("2010-01-01")): Option[DateTime], None: Option[DateTime]))
    val errorMsg = projectService.validateProjectDate(addresses, projDate)
    errorMsg should be(None)
  }

  test("Validate road part dates with project date - startDate and endDate") {
    val projDate = DateTime.parse("2015-01-01")
    val addresses = List(ReservedRoadPart(5: Long, 5: Long, 205: Long, 5: Double, Discontinuity.apply("jatkuva"), 8: Long, Option(DateTime.parse("2010-01-01")): Option[DateTime], Option(DateTime.parse("2017-01-01")): Option[DateTime]))
    val errorMsg = projectService.validateProjectDate(addresses, projDate)
    errorMsg should not be (None)
  }

  test("Validate road part dates with project date - startDate and endDate valid") {
    val projDate = DateTime.parse("2018-01-01")
    val addresses = List(ReservedRoadPart(5: Long, 5: Long, 205: Long, 5: Double, Discontinuity.apply("jatkuva"), 8: Long, Option(DateTime.parse("2010-01-01")): Option[DateTime], Option(DateTime.parse("2017-01-01")): Option[DateTime]))
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
      val addresses = List(ReservedRoadPart(0L, 5L, 205L, 5.0, Discontinuity.apply("jatkuva"), 8: Long, None: Option[DateTime], None: Option[DateTime]))
      val roadAddressProject = RoadAddressProject(0, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List(), None)
      val saved = projectService.createRoadLinkProject(roadAddressProject)._1
      val changed = saved.copy(reservedParts = addresses)
      projectService.saveRoadLinkProject(changed)
      val countAfterInsertProjects = projectService.getRoadAddressAllProjects()
      val projectLinks = ProjectDAO.fetchByProjectNewRoadPart(5, 205, saved.id)
      count = countCurrentProjects.size + 1
      countAfterInsertProjects.size should be(count)
      sqlu"""UPDATE Project_link set status = 1""".execute
      val terminations = ProjectDeltaCalculator.delta(saved.id).terminations
      terminations should have size (projectLinks.size)
      sqlu"""UPDATE Project_link set status = 2""".execute
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
      val addresses = List(ReservedRoadPart(0L, 5L, 205L, 5.0, Discontinuity.apply("jatkuva"), 8: Long, None: Option[DateTime], None: Option[DateTime]))
      val roadAddressProject = RoadAddressProject(0, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List(), None)
      val saved = projectService.createRoadLinkProject(roadAddressProject)._1
      val changed = saved.copy(reservedParts = addresses)
      projectService.saveRoadLinkProject(changed)
      val countAfterInsertProjects = projectService.getRoadAddressAllProjects()
      val projectLinks = ProjectDAO.fetchByProjectNewRoadPart(5, 205, saved.id)
      count = countCurrentProjects.size + 1
      countAfterInsertProjects.size should be(count)
      sqlu"""UPDATE Project_link set status = 1""".execute
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
      sections.groupBy(_.endMAddr).keySet should have size (2)
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
      val addresses = List(ReservedRoadPart(5: Long, 5: Long, 205: Long, 5: Double, Discontinuity.apply("jatkuva"), 8: Long, None: Option[DateTime], None: Option[DateTime]))
      val roadAddressProject = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", addresses, None)
      val saved = projectService.createRoadLinkProject(roadAddressProject)._1
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
      projectService.updateProjectLinkStatus(saved.id, linkIds, LinkStatus.Terminated, "-")
      projectService.projectLinkPublishable(saved.id) should be(true)
    }
    runWithRollback {
      projectService.getRoadAddressAllProjects()
    } should have size (count - 1)
  }

  test("fetch project data and send it to TR") {
    assume(testConnection)
    runWithRollback {
      val project = RoadAddressProject(1, ProjectState.Incomplete, "testiprojekti", "Test", DateTime.now(), "Test", DateTime.now(), DateTime.now(), "info", List(ReservedRoadPart(5: Long, 203: Long, 203: Long, 5: Double, Discontinuity.apply("jatkuva"), 8: Long, None: Option[DateTime], None: Option[DateTime])), None)
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
    val addresses = List(ReservedRoadPart(5: Long, 203: Long, 203: Long, 5: Double, Discontinuity.apply("jatkuva"), 8: Long, None: Option[DateTime], None: Option[DateTime]))
    val roadAddressProject = RoadAddressProject(projectId, ProjectState.apply(2), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List(), None)
    runWithRollback {
      val saved = projectService.createRoadLinkProject(roadAddressProject)._1
      val stateaftercheck = projectService.updateProjectStatusIfNeeded(sent2TRState, savedState, "", saved.id)
      stateaftercheck.description should be(ProjectState.Saved2TR.description)
    }

  }

  test("Update to TRerror state") {
    val sent2TRState = ProjectState.apply(2) //notfinnished
    val savedState = ProjectState.apply(3)
    val projectId = 0
    val addresses = List(ReservedRoadPart(5: Long, 203: Long, 203: Long, 5: Double, Discontinuity.apply("jatkuva"), 8: Long, None: Option[DateTime], None: Option[DateTime]))
    val roadAddressProject = RoadAddressProject(projectId, ProjectState.apply(2), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List(), None)
    runWithRollback {
      val saved = projectService.createRoadLinkProject(roadAddressProject)._1
      val stateaftercheck = projectService.updateProjectStatusIfNeeded(sent2TRState, savedState, "failed", saved.id)
      stateaftercheck.description should be(ProjectState.ErroredInTR.description)
    }

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
      val ra = Seq(RoadAddress(id, roadNumber, roadPartNumber, RoadType.Unknown, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), None, Option("tester"), 0, linkId, 0.0, 9.8, SideCode.TowardsDigitizing, 0, (None, None), false,
        Seq(Point(0.0, 0.0), Point(0.0, 9.8)), LinkGeomSource.NormalLinkInterface))
      RoadAddressDAO.create(ra)
      val roadsBeforeChanges = RoadAddressDAO.fetchByLinkId(Set(linkId)).head

      when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(Set(linkId))).thenReturn(Seq(RoadLink(linkId, ra.head.geometry, 9.8, State, 1, TrafficDirection.BothDirections,
        Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(167)))))
      //Creation of test project with test links
      val project = RoadAddressProject(projectId, ProjectState.Incomplete, "testiprojekti", "Test", DateTime.now(), "Test",
        DateTime.parse("2020-01-01"), DateTime.now(), "info",
        List(ReservedRoadPart(0: Long, roadNumber: Long, roadPartNumber: Long, 5: Double, Discontinuity.apply("jatkuva"),
          8: Long, None: Option[DateTime], None: Option[DateTime])), None)
      val (proj, projectLink, _, errmsg) = projectService.createRoadLinkProject(project)
      projectLink.isEmpty should be(false)
      errmsg should be("ok")
      projectId = proj.id
      val projectLinkId = projectLink.get.id
      val projectLinkProjectId = projectLink.get.projectId
      val terminatedValue = LinkStatus.Terminated.value
      //Changing the status of the test link to terminated
      sqlu"""Update Project_Link Set Status = $terminatedValue
            Where ID = $projectLinkId And PROJECT_ID = $projectLinkProjectId""".execute

      //Creation of test road_address_changes
      sqlu"""insert into road_address_changes
             (project_id,change_type,new_road_number,new_road_part_number,new_track_code,new_start_addr_m,new_end_addr_m,new_discontinuity,new_road_type,new_ely,
              old_road_number,old_road_part_number,old_track_code,old_start_addr_m,old_end_addr_m)
             Values ($projectId,5,$roadNumber,$roadPartNumber,1,0,10,1,1,8,$roadNumber,$roadPartNumber,1,0,10)""".execute

      projectService.updateRoadAddressWithProject(ProjectState.Saved2TR, projectId)

      val roadsAfterChanges=RoadAddressDAO.fetchByLinkId(Set(linkId))
      roadsAfterChanges.size should be (2)
      val roadsAfterPublishing = roadsAfterChanges.filter(x=>x.startDate.nonEmpty).head
      val endedAddress = roadsAfterChanges.filter(x=>x.endDate.nonEmpty)

      roadsBeforeChanges.linkId should be(roadsAfterPublishing.linkId)
      roadsBeforeChanges.roadNumber should be(roadsAfterPublishing.roadNumber)
      roadsBeforeChanges.roadPartNumber should be(roadsAfterPublishing.roadPartNumber)
      endedAddress.head.endDate.nonEmpty should be(true)
      endedAddress.size should be (1)
      endedAddress.head.endDate.get.toString("yyyy-MM-dd") should be("2020-01-01")
      roadsAfterPublishing.startDate.get.toString("yyyy-MM-dd") should be("2020-01-01")
    }
  }
/*
  test("process roadChange data and expire the roadLinks and save TR published info to roadaddress table") {
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
        DateTime.parse("2020-01-01"), DateTime.now(), "info",
        List(ReservedRoadPart(0: Long, roadNumber: Long, roadPartNumber: Long, 5: Double, Discontinuity.apply("jatkuva"),
          8: Long, None: Option[DateTime], None: Option[DateTime])), None)
      val (proj, projectLink, _, errmsg) = projectService.createRoadLinkProject(project)
      projectLink.isEmpty should be(false)
      errmsg should be("ok")
      projectId = proj.id
      val projectLinkId = projectLink.get.id
      val projectLinkProjectId = projectLink.get.projectId
      projectService.addNewLinksToProject(roadLinks, project.id, 987, 23452, projectLink.newTrackCode, projectLink.newDiscontinuity, projectLink.roadType) match {

      val terminatedValue = LinkStatus.Terminated.value
      //Changing the status of the test link to terminated
      sqlu"""Update Project_Link Set Status = $terminatedValue
            Where ID = $projectLinkId And PROJECT_ID = $projectLinkProjectId""".execute

      //Creation of test road_address_changes
      sqlu"""insert into road_address_changes
             (project_id,change_type,new_road_number,new_road_part_number,new_track_code,new_start_addr_m,new_end_addr_m,new_discontinuity,new_road_type,new_ely,
              old_road_number,old_road_part_number,old_track_code,old_start_addr_m,old_end_addr_m)
             Values ($projectId,5,$roadNumber,$roadPartNumber,1,0,10,1,1,8,$roadNumber,$roadPartNumber,1,0,10)""".execute

      projectService.updateRoadAddressWithProject(ProjectState.Saved2TR, projectId)

      val roadsAfterChanges=RoadAddressDAO.fetchByLinkId(Set(linkId))
      roadsAfterChanges.size should be (2)
      val roadsAfterPublishing = roadsAfterChanges.filter(x=>x.startDate.nonEmpty).head
      val endedAddress = roadsAfterChanges.filter(x=>x.endDate.nonEmpty)

      roadsBeforeChanges.linkId should be(roadsAfterPublishing.linkId)
      roadsBeforeChanges.roadNumber should be(roadsAfterPublishing.roadNumber)
      roadsBeforeChanges.roadPartNumber should be(roadsAfterPublishing.roadPartNumber)
      endedAddress.head.endDate.nonEmpty should be(true)
      endedAddress.size should be (1)
      endedAddress.head.endDate.get.toString("yyyy-MM-dd") should be("2020-01-01")
      roadsAfterPublishing.startDate.get.toString("yyyy-MM-dd") should be("2020-01-01")
    }
  }
*/

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
      val countCurrentProjects = projectService.getRoadAddressAllProjects()
      val addresses: List[ReservedRoadPart] = List(ReservedRoadPart(5: Long, 203: Long, 203: Long, 5: Double, Discontinuity.apply("jatkuva"), 8: Long, None: Option[DateTime], None: Option[DateTime]))
      val roadAddressProject = RoadAddressProject(0, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", addresses, None)
      val id = projectService.createRoadLinkProject(roadAddressProject)._1.id
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
      val countCurrentProjects = projectService.getRoadAddressAllProjects()
      val addresses: List[ReservedRoadPart] = List(ReservedRoadPart(5: Long, 203: Long, 203: Long, 5: Double, Discontinuity.apply("jatkuva"), 8: Long, None: Option[DateTime], None: Option[DateTime]))
      val roadAddressProject = RoadAddressProject(0, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", addresses, None)
      val id = projectService.createRoadLinkProject(roadAddressProject)._1.id
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
        List(ReservedRoadPart(0: Long, roadNumber: Long, roadPartNumber: Long, 5: Double, Discontinuity.apply("jatkuva"),
          8: Long, None: Option[DateTime], None: Option[DateTime])), None)
      val (proj, projectLink, _, errmsg) = projectService.createRoadLinkProject(project)

      val projectWithReserves = projectService.getProjectsWithReservedRoadParts(proj.id)
      val returnedProject = projectWithReserves._1
      val returnedRoadParts = projectWithReserves._2

      returnedProject.name should be("testiprojekti")
      returnedRoadParts.size should be(1)
      returnedRoadParts.head.roadNumber should be(roadNumber)
    }

  }

  test("get change table test with update change table on every road link change") {
    var count = 0
    val roadLink = RoadLink(5170939L, Seq(Point(535605.272, 6982204.22, 85.90899999999965))
      , 540.3960283713503, State, 99, TrafficDirection.AgainstDigitizing, UnknownLinkType, Some("25.06.2015 03:00:00"), Some("vvh_modified"), Map("MUNICIPALITYCODE" -> BigInt.apply(749)),
      InUse, NormalLinkInterface)
    when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean], any[Boolean])).thenReturn(Seq(roadLink))
    runWithRollback {
      val countCurrentProjects = projectService.getRoadAddressAllProjects()
      val id = 0
      val addresses = List(ReservedRoadPart(5: Long, 5: Long, 205: Long, 5: Double, Discontinuity.apply("jatkuva"), 8: Long, None: Option[DateTime], None: Option[DateTime]),
          ReservedRoadPart(5: Long, 5: Long, 206: Long, 5: Double, Discontinuity.apply("jatkuva"), 8: Long, None: Option[DateTime], None: Option[DateTime]))
      val roadAddressProject = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", addresses, None)
      val (saved, _, formLines, errMsg) = projectService.createRoadLinkProject(roadAddressProject)
      errMsg should be("ok")
      formLines should have size (2)
      val countAfterInsertProjects = projectService.getRoadAddressAllProjects()
      count = countCurrentProjects.size + 1
      countAfterInsertProjects.size should be(count)
      projectService.projectLinkPublishable(saved.id) should be(false)
      val projectLinks = ProjectDAO.getProjectLinks(saved.id)
      val partitioned = projectLinks.partition(_.roadPartNumber == 205)
      val linkIds205 = partitioned._1.map(_.linkId).toSet
      val linkIds206 = partitioned._2.map(_.linkId).toSet
      reset(mockRoadLinkService)
      when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean], any[Boolean])).thenAnswer(
        toMockAnswer(projectLinks, roadLink)
      )

      projectService.updateProjectLinkStatus(saved.id, linkIds205, LinkStatus.Terminated, "-")
      projectService.projectLinkPublishable(saved.id) should be(false)


      projectService.updateProjectLinkStatus(saved.id, linkIds206, LinkStatus.Terminated, "-")
      projectService.projectLinkPublishable(saved.id) should be(true)

      val changeProjectOpt = projectService.getChangeProject(saved.id)
      changeProjectOpt.map(_.changeInfoSeq).getOrElse(Seq()) should have size (5)

      val change = changeProjectOpt.get

      change.changeDate should be(roadAddressProject.startDate.toString("YYYY-MM-DD"))
      change.ely should be(8)
      change.user should be("TestUser")
      change.name should be("TestProject")
      change.changeInfoSeq.foreach(rac => {
        val s = rac.source
        val t = rac.target
        val (sTie, sAosa, sAjr, sAet, sLet) = (s.roadNumber, s.startRoadPartNumber, s.trackCode, s.startAddressM, s.endAddressM)
        val (tTie, tAosa, tAjr, tAet, tLet) = (t.roadNumber, t.startRoadPartNumber, t.trackCode, t.startAddressM, t.endAddressM)
        sTie should be(tTie)
        sAosa should be(tAosa)
        sAjr should be(tAjr)
        sAet should be(tAet)
        sLet should be(tLet)
      })

      change.changeInfoSeq.foreach(_.changeType should be(AddressChangeType.Termination))
      change.changeInfoSeq.foreach(_.discontinuity should be(Discontinuity.Continuous))
      // TODO: When road types are properly generated
//      change.changeInfoSeq.foreach(_.roadType should be(RoadType.UnknownOwnerRoad))
    }
    runWithRollback {
      projectService.getRoadAddressAllProjects()
    } should have size (count - 1)
  }
  test("add nonexisting roadlink to project") {
    runWithRollback {
      val idr = RoadAddressDAO.getNextRoadAddressId
      val id = Sequences.nextViitePrimaryKeySeqValue
      val rap = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("2700-01-01"), "TestUser", DateTime.parse("2700-01-01"), DateTime.now(), "Some additional info", List.empty[ReservedRoadPart], None)
      val projectLink = toProjectLink(rap)(RoadAddress(idr, 1943845, 1, RoadType.Unknown, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), Some(DateTime.parse("1902-01-01")), Option("tester"), 0, 12345L, 0.0, 9.8, SideCode.TowardsDigitizing, 0, (None, None), false,
        Seq(Point(0.0, 0.0), Point(0.0, 9.8)), LinkGeomSource.NormalLinkInterface))
      ProjectDAO.createRoadAddressProject(rap)

      val p = ProjectAddressLink(idr, projectLink.linkId, projectLink.geometry,
        1, AdministrativeClass.apply(1), LinkType.apply(1), RoadLinkType.apply(1), ConstructionType.apply(1), projectLink.linkGeomSource, RoadType.PublicUnderConstructionRoad, "", 111, Some(""), Some("vvh_modified"),
        null, projectLink.roadNumber, projectLink.roadPartNumber, 2, -1, projectLink.discontinuity.value,
        projectLink.startAddrMValue, projectLink.endAddrMValue, projectLink.startMValue, projectLink.endMValue,
        projectLink.sideCode,
        projectLink.calibrationPoints._1,
        projectLink.calibrationPoints._2, Anomaly.None, projectLink.lrmPositionId, projectLink.status)

      projectService.addNewLinksToProject(Seq(p), id, projectLink.roadNumber, projectLink.roadPartNumber, projectLink.track.value.toLong, projectLink.discontinuity.value.toLong, projectLink.roadType.value)
      val links= ProjectDAO.getProjectLinks(id)
      links.size should be (1)
    }
  }

  test("add two consecutive roadlinks to project road number & road part") {
    val roadlink = RoadLink(5175306, Seq(Point(535602.222, 6982200.25, 89.9999), Point(535605.272, 6982204.22, 85.90899999999965))
      , 540.3960283713503, State, 99, TrafficDirection.AgainstDigitizing, UnknownLinkType, Some("25.06.2015 03:00:00"), Some("vvh_modified"), Map("MUNICIPALITYCODE" -> BigInt.apply(749)),
      InUse, NormalLinkInterface)
    when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(Set(5175306L))).thenReturn(Seq(roadlink))
    runWithRollback {

      val idr1 = RoadAddressDAO.getNextRoadAddressId
      val idr2 = RoadAddressDAO.getNextRoadAddressId
      val idr3 = RoadAddressDAO.getNextRoadAddressId
      val id = Sequences.nextViitePrimaryKeySeqValue
      val rap = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("2700-01-01"), "TestUser", DateTime.parse("2700-01-01"), DateTime.now(), "Some additional info", List.empty[ReservedRoadPart], None)
      ProjectDAO.createRoadAddressProject(rap)

      val projectLink1 = toProjectLink(rap)(RoadAddress(idr1, 1943845, 1, RoadType.Unknown, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), Some(DateTime.parse("1902-01-01")), Option("tester"), 0, 5175306L, 0.0, 9.8, SideCode.TowardsDigitizing, 0, (None, None), false,
        Seq(Point(535602.222, 6982200.25, 89.9999),Point(535605.272, 6982204.22, 85.90899999999965)), LinkGeomSource.NormalLinkInterface))

      val projectLink2 = toProjectLink(rap)(RoadAddress(idr2, 1943845, 1, RoadType.Unknown, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), Some(DateTime.parse("1902-01-01")), Option("tester"), 0, 1610976L, 0.0, 5.8, SideCode.TowardsDigitizing, 0, (None, None), false,
        Seq(Point(535605.272, 6982204.22, 85.90899999999965), Point(535608.555, 6982204.33, 86.90)), LinkGeomSource.NormalLinkInterface))


      val p1 = ProjectAddressLink(idr1, projectLink1.linkId, projectLink1.geometry,
        1, AdministrativeClass.apply(1), LinkType.apply(1), RoadLinkType.apply(1), ConstructionType.apply(1), projectLink1.linkGeomSource, RoadType.PublicUnderConstructionRoad, "", 111, Some(""), Some("vvh_modified"),
        null, projectLink1.roadNumber, projectLink1.roadPartNumber, 2, -1, projectLink1.discontinuity.value,
        projectLink1.startAddrMValue, projectLink1.endAddrMValue, projectLink1.startMValue, projectLink1.endMValue,
        projectLink1.sideCode,
        projectLink1.calibrationPoints._1,
        projectLink1.calibrationPoints._2, Anomaly.None, projectLink1.lrmPositionId, projectLink1.status)

      val p2 = ProjectAddressLink(idr2, projectLink2.linkId, projectLink2.geometry,
        1, AdministrativeClass.apply(1), LinkType.apply(1), RoadLinkType.apply(1), ConstructionType.apply(1), projectLink2.linkGeomSource, RoadType.PublicUnderConstructionRoad, "", 111, Some(""), Some("vvh_modified"),
        null, projectLink2.roadNumber, projectLink2.roadPartNumber, 2, -1, projectLink2.discontinuity.value,
        projectLink2.startAddrMValue, projectLink2.endAddrMValue, projectLink2.startMValue, projectLink2.endMValue,
        projectLink2.sideCode,
        projectLink2.calibrationPoints._1,
        projectLink2.calibrationPoints._2, Anomaly.None, projectLink2.lrmPositionId, projectLink2.status)

      projectService.addNewLinksToProject(Seq(p1), id, projectLink1.roadNumber, projectLink1.roadPartNumber, projectLink1.track.value.toLong, projectLink1.discontinuity.value.toLong, projectLink1.roadType.value)
      val links= ProjectDAO.getProjectLinks(id)
      links.size should be (1)


      projectService.addNewLinksToProject(Seq(p2), id, projectLink2.roadNumber, projectLink2.roadPartNumber, projectLink2.track.value.toLong, projectLink2.discontinuity.value.toLong, projectLink2.roadType.value)
      val linksAfter = ProjectDAO.getProjectLinks(id)
      linksAfter.size should be (2)
    }
  }

  test("error message when reserving already used road number&part (in other project ids). Empty error message if same road number&part but == proj id ") {
    runWithRollback {
      val idr = RoadAddressDAO.getNextRoadAddressId
      val id = Sequences.nextViitePrimaryKeySeqValue
      val rap = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("2700-01-01"), "TestUser", DateTime.parse("1972-03-03"), DateTime.parse("2700-01-01"), "Some additional info", List.empty[ReservedRoadPart], None)
      val projectLink = toProjectLink(rap)(RoadAddress(idr, 5, 203, RoadType.Unknown, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), Some(DateTime.parse("1902-01-01")), Option("tester"), 0, 12345L, 0.0, 9.8, SideCode.TowardsDigitizing, 0, (None, None), false,
        Seq(Point(0.0, 0.0), Point(0.0, 9.8)), LinkGeomSource.NormalLinkInterface))
      ProjectDAO.createRoadAddressProject(rap)

      val rap2 = RoadAddressProject(id + 1, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("2700-01-01"), "TestUser", DateTime.parse("1972-03-04"), DateTime.parse("2700-01-01"), "Some additional info", List.empty[ReservedRoadPart], None)
      val projectLink2 = toProjectLink(rap2)(RoadAddress(idr, 5, 999, RoadType.Unknown, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), Some(DateTime.parse("1902-01-01")), Option("tester"), 0, 12345L, 0.0, 9.8, SideCode.TowardsDigitizing, 0, (None, None), false,
        Seq(Point(0.0, 0.0), Point(0.0, 9.8)), LinkGeomSource.NormalLinkInterface))
      ProjectDAO.createRoadAddressProject(rap2)

      val projectLink3 = toProjectLink(rap)(RoadAddress(idr, 5, 999, RoadType.Unknown, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), Some(DateTime.parse("1902-01-01")), Option("tester"), 0, 12345L, 0.0, 9.8, SideCode.TowardsDigitizing, 0, (None, None), false,
        Seq(Point(0.0, 0.0), Point(0.0, 9.8)), LinkGeomSource.NormalLinkInterface))

      val p = ProjectAddressLink(idr, projectLink.linkId, projectLink.geometry,
        1, AdministrativeClass.apply(1), LinkType.apply(1), RoadLinkType.apply(1), ConstructionType.apply(1), projectLink.linkGeomSource, RoadType.PublicUnderConstructionRoad, "", 111, Some(""), Some("vvh_modified"),
        null, projectLink.roadNumber, projectLink.roadPartNumber, 2, -1, projectLink.discontinuity.value,
        projectLink.startAddrMValue, projectLink.endAddrMValue, projectLink.startMValue, projectLink.endMValue,
        projectLink.sideCode,
        projectLink.calibrationPoints._1,
        projectLink.calibrationPoints._2, Anomaly.None, projectLink.lrmPositionId, projectLink.status)

      val message1project1 = projectService.addNewLinksToProject(Seq(p), id, projectLink.roadNumber,
        projectLink.roadPartNumber, projectLink.track.value.toLong, projectLink.discontinuity.value.toLong, projectLink.roadType.value).getOrElse("")
      val links = ProjectDAO.getProjectLinks(id)
      links.size should be(0)
      message1project1 should be("TIE 5 OSA 203 on jo olemassa projektin alkupäivänä 03.03.1972, tarkista tiedot") //check that it is reserved in roadaddress table

      val message1project2 = projectService.addNewLinksToProject(Seq(p), id + 1, projectLink2.roadNumber,
        projectLink2.roadPartNumber, projectLink2.track.value.toLong, projectLink2.discontinuity.value.toLong, projectLink2.roadType.value)
      val links2 = ProjectDAO.getProjectLinks(id + 1)
      links2.size should be(1)
      message1project2 should be(None)

      val message2project1 = projectService.addNewLinksToProject(Seq(p), id, projectLink3.roadNumber,
        projectLink3.roadPartNumber, projectLink3.track.value.toLong, projectLink3.discontinuity.value.toLong, projectLink3.roadType.value).getOrElse("")
      val links3 = ProjectDAO.getProjectLinks(id)
      links3.size should be(0)
      message2project1 should be("TIE 5 OSA 999 on jo varattuna projektissa TestProject, tarkista tiedot")
    }
  }

  test("Project link direction change") {
    def prettyPrint(links: List[ProjectLink]) = {

      val sortedLinks = links.sortBy(_.id)
      sortedLinks.foreach{ link =>
        println(s""" ${link.linkId} trackCode ${link.track.value} -> |--- (${link.startAddrMValue}, ${link.endAddrMValue}) ---|  MValue = """ + (link.endMValue-link.startMValue))
      }
      println("\n Total length (0+1/2):" + (sortedLinks.filter(_.track != Track.Combined).map(_.geometryLength).sum/2 +
        sortedLinks.filter(_.track == Track.Combined).map(_.geometryLength).sum))
    }
    def toGeom(json: Option[Any]): List[Point] = {
      json.get.asInstanceOf[List[Map[String, Double]]].map(m => Point(m("x"), m("y"), m("z")))
    }

    runWithRollback {
      val points6117675 = "[{\"x\": 347362.773,\"y\": 6689481.461,\"z\": 67.55100000000675}, {\"x\": 347362.685,\"y\": 6689482.757,\"z\": 67.42900000000373}, {\"x\": 347361.105,\"y\": 6689506.288,\"z\": 66.12099999999919}, {\"x\": 347357.894,\"y\": 6689536.08,\"z\": 65.97999999999593}, {\"x\": 347356.682,\"y\": 6689566.328,\"z\": 66.05199999999604}, {\"x\": 347356.982,\"y\": 6689596.011,\"z\": 66.38800000000629}, {\"x\": 347359.306,\"y\": 6689619.386,\"z\": 66.45799999999872}, {\"x\": 347362.853,\"y\": 6689631.277,\"z\": 66.1649999999936}, {\"x\": 347369.426,\"y\": 6689645.863,\"z\": 65.88700000000244}, {\"x\": 347372.682,\"y\": 6689657.912,\"z\": 66.06600000000617}, {\"x\": 347371.839,\"y\": 6689672.648,\"z\": 66.73399999999674}, {\"x\": 347368.015,\"y\": 6689689.588,\"z\": 67.28399999999965}, {\"x\": 347361.671,\"y\": 6689711.619,\"z\": 68.07300000000396}, {\"x\": 347356.726,\"y\": 6689730.288,\"z\": 68.57300000000396}, {\"x\": 347350.081,\"y\": 6689751.062,\"z\": 69.09399999999732}, {\"x\": 347343.06,\"y\": 6689773.574,\"z\": 68.86900000000605}, {\"x\": 347337.186,\"y\": 6689793.763,\"z\": 68.30999999999767}, {\"x\": 347328.501,\"y\": 6689814.216,\"z\": 67.84900000000198}, {\"x\": 347320.495,\"y\": 6689828.259,\"z\": 67.35000000000582}, {\"x\": 347308.837,\"y\": 6689849.704,\"z\": 66.91300000000047}, {\"x\": 347297.244,\"y\": 6689866.546,\"z\": 66.88499999999476}, {\"x\": 347282.117,\"y\": 6689886.857,\"z\": 66.67999999999302}, {\"x\": 347268.669,\"y\": 6689905.865,\"z\": 66.65899999999965}, {\"x\": 347253.307,\"y\": 6689927.723,\"z\": 66.75400000000081}, {\"x\": 347238.302,\"y\": 6689951.051,\"z\": 66.63099999999395}, {\"x\": 347224.587,\"y\": 6689973.39,\"z\": 66.65200000000186}, {\"x\": 347211.253,\"y\": 6689997.583,\"z\": 66.38800000000629}, {\"x\": 347205.387,\"y\": 6690009.305,\"z\": 66.6030000000028}, {\"x\": 347202.742,\"y\": 6690023.57,\"z\": 66.18600000000151}, {\"x\": 347204.208,\"y\": 6690034.352,\"z\": 65.58400000000256}, {\"x\": 347208.566,\"y\": 6690044.173,\"z\": 65.14599999999336}, {\"x\": 347216.713,\"y\": 6690056.021,\"z\": 64.80599999999686}, {\"x\": 347220.291,\"y\": 6690066.224,\"z\": 64.93600000000151}, {\"x\": 347222.248,\"y\": 6690080.454,\"z\": 65.24400000000605}, {\"x\": 347220.684,\"y\": 6690096.777,\"z\": 65.56500000000233}, {\"x\": 347217.723,\"y\": 6690116.388,\"z\": 65.8920000000071}\n\t\t\t]"
      val points6638374 = "[{\"x\": 347217.723,\"y\": 6690116.388,\"z\": 65.8920000000071}, {\"x\": 347214.916,\"y\": 6690134.934,\"z\": 66.25999999999476}, {\"x\": 347213.851,\"y\": 6690152.423,\"z\": 66.79899999999907}, {\"x\": 347213.827,\"y\": 6690152.93,\"z\": 66.82300000000396}\n\t\t\t]"
      val points6638371 = "[{\"x\": 347213.827,\"y\": 6690152.93,\"z\": 66.82300000000396}, {\"x\": 347212.547,\"y\": 6690180.465,\"z\": 67.92699999999604}, {\"x\": 347210.573,\"y\": 6690206.911,\"z\": 68.28800000000047}, {\"x\": 347209.134,\"y\": 6690230.456,\"z\": 67.83299999999872}\n\t\t\t]"
      val points6638357 = "[{\"x\": 347209.134,\"y\": 6690230.456,\"z\": 67.83299999999872}, {\"x\": 347208.897,\"y\": 6690234.161,\"z\": 67.80599999999686}, {\"x\": 347205.6,\"y\": 6690271.347,\"z\": 67.84200000000419}, {\"x\": 347203.144,\"y\": 6690303.761,\"z\": 66.14299999999639}, {\"x\": 347200.251,\"y\": 6690347.968,\"z\": 63.921000000002095}, {\"x\": 347198.697,\"y\": 6690365.988,\"z\": 63.820000000006985}, {\"x\": 347197.349,\"y\": 6690400.247,\"z\": 64.25500000000466}, {\"x\": 347197.166,\"y\": 6690417.044,\"z\": 64.45799999999872}, {\"x\": 347197.532,\"y\": 6690435.302,\"z\": 64.53800000000047}, {\"x\": 347198.261,\"y\": 6690455.932,\"z\": 64.63700000000244}, {\"x\": 347197.615,\"y\": 6690476.327,\"z\": 65.04899999999907}, {\"x\": 347194.793,\"y\": 6690493.361,\"z\": 65.16099999999278}, {\"x\": 347191.141,\"y\": 6690509.063,\"z\": 65.49199999999837}, {\"x\": 347180.369,\"y\": 6690544.848,\"z\": 66.14400000000023}, {\"x\": 347174.891,\"y\": 6690563.471,\"z\": 66.61299999999756}\n\t\t\t]"
      val points6117732 = "[{\"x\": 347174.891,\"y\": 6690563.471,\"z\": 66.61299999999756}, {\"x\": 347169.843,\"y\": 6690582.738,\"z\": 67.47599999999511}, {\"x\": 347166.225,\"y\": 6690601.016,\"z\": 68.7670000000071}, {\"x\": 347165.843,\"y\": 6690612.347,\"z\": 69.56200000000536}\n\t\t\t]"
      val points6117725 = "[{\"x\": 347165.843,\"y\": 6690612.347,\"z\": 69.56200000000536}, {\"x\": 347167.477,\"y\": 6690629.902,\"z\": 70.70500000000175}, {\"x\": 347170.578,\"y\": 6690644.885,\"z\": 71.59399999999732}, {\"x\": 347176.414,\"y\": 6690664.977,\"z\": 72.79399999999441}, {\"x\": 347184.98,\"y\": 6690684.201,\"z\": 73.8920000000071}, {\"x\": 347194.603,\"y\": 6690703.442,\"z\": 74.57399999999325}, {\"x\": 347212.124,\"y\": 6690731.843,\"z\": 74.42999999999302}, {\"x\": 347225.858,\"y\": 6690748.726,\"z\": 74.8469999999943}\n\t\t\t]"
      val points6638363 = "[{\"x\": 347225.858,\"y\": 6690748.726,\"z\": 74.8469999999943}, {\"x\": 347232.073,\"y\": 6690754.294,\"z\": 75.07499999999709}, {\"x\": 347243.715,\"y\": 6690765.894,\"z\": 75.11699999999837}, {\"x\": 347254.071,\"y\": 6690775.234,\"z\": 74.75299999999697}, {\"x\": 347267.791,\"y\": 6690791.279,\"z\": 74.37600000000384}, {\"x\": 347281.366,\"y\": 6690808.298,\"z\": 74.34900000000198}, {\"x\": 347291.699,\"y\": 6690819.847,\"z\": 74.12200000000303}, {\"x\": 347303.653,\"y\": 6690832.815,\"z\": 73.45699999999488}, {\"x\": 347317.525,\"y\": 6690848.754,\"z\": 72.03599999999278}, {\"x\": 347329.296,\"y\": 6690864.119,\"z\": 70.91700000000128}, {\"x\": 347339.883,\"y\": 6690882.667,\"z\": 69.96099999999569}, {\"x\": 347354.706,\"y\": 6690911.347,\"z\": 68.72500000000582}, {\"x\": 347356.35,\"y\": 6690915.047,\"z\": 68.6140000000014}\n\t\t\t]"
      val points6117633 = "[{\"x\": 347356.35,\"y\": 6690915.047,\"z\": 68.6140000000014}, {\"x\": 347369.27,\"y\": 6690940.397,\"z\": 68.20699999999488}, {\"x\": 347378.369,\"y\": 6690956.751,\"z\": 68.01900000000023}, {\"x\": 347390.322,\"y\": 6690972.648,\"z\": 67.98699999999371}, {\"x\": 347404.727,\"y\": 6690989.705,\"z\": 68.12799999999697}, {\"x\": 347416.172,\"y\": 6691004.385,\"z\": 68.15499999999884}, {\"x\": 347428.658,\"y\": 6691024.734,\"z\": 68.24300000000221}, {\"x\": 347444.421,\"y\": 6691050.043,\"z\": 68.63199999999779}, {\"x\": 347456.181,\"y\": 6691071.866,\"z\": 68.99199999999837}, {\"x\": 347468.537,\"y\": 6691089.503,\"z\": 69.02700000000186}, {\"x\": 347482.425,\"y\": 6691101.472,\"z\": 68.6820000000007}\n\t\t\t]"
      val points6117621 = "[{\"x\": 347482.425,\"y\": 6691101.472,\"z\": 68.6820000000007}, {\"x\": 347482.004,\"y\": 6691116.142,\"z\": 69.1710000000021}, {\"x\": 347477.901,\"y\": 6691138.644,\"z\": 69.45200000000477}, {\"x\": 347467.696,\"y\": 6691173.502,\"z\": 69.49400000000605}, {\"x\": 347463.345,\"y\": 6691196.774,\"z\": 69.23200000000361}, {\"x\": 347462.146,\"y\": 6691209.426,\"z\": 69.03100000000268}, {\"x\": 347462.459,\"y\": 6691219.919,\"z\": 68.83900000000722}, {\"x\": 347462.513,\"y\": 6691220.325,\"z\": 68.82700000000477}\n\t\t\t]"
      val points6638300 = "[{\"x\": 347225.858,\"y\": 6690748.726,\"z\": 74.8469999999943}, {\"x\": 347226.867,\"y\": 6690762.837,\"z\": 75.01900000000023}, {\"x\": 347227.161,\"y\": 6690771.948,\"z\": 75.16999999999825}, {\"x\": 347226.7,\"y\": 6690786.654,\"z\": 75.30000000000291}, {\"x\": 347227.131,\"y\": 6690799.799,\"z\": 75.40200000000186}, {\"x\": 347230.363,\"y\": 6690815.529,\"z\": 75.29899999999907}, {\"x\": 347236.181,\"y\": 6690836.43,\"z\": 75.36299999999756}, {\"x\": 347243.358,\"y\": 6690863.377,\"z\": 75.95699999999488}, {\"x\": 347244.89,\"y\": 6690869.357,\"z\": 76.20600000000559}, {\"x\": 347245.719,\"y\": 6690874.941,\"z\": 76.49899999999616}, {\"x\": 347245.728,\"y\": 6690882.712,\"z\": 76.80199999999604}, {\"x\": 347243.45,\"y\": 6690897.118,\"z\": 77.18099999999686}, {\"x\": 347241.137,\"y\": 6690918.745,\"z\": 77.59100000000035}, {\"x\": 347237.904,\"y\": 6690943.525,\"z\": 78.78200000000652}, {\"x\": 347235.534,\"y\": 6690971.968,\"z\": 80.66700000000128}, {\"x\": 347234.241,\"y\": 6690992.008,\"z\": 81.91400000000431}, {\"x\": 347235.526,\"y\": 6691007.878,\"z\": 81.33599999999569}, {\"x\": 347237.475,\"y\": 6691021.321,\"z\": 80.84399999999732}, {\"x\": 347238.276,\"y\": 6691035.857,\"z\": 80.14299999999639}, {\"x\": 347237.701,\"y\": 6691047.139,\"z\": 79.40600000000268}, {\"x\": 347235.322,\"y\": 6691063.305,\"z\": 77.7670000000071}, {\"x\": 347227.128,\"y\": 6691070.636,\"z\": 76.79399999999441}, {\"x\": 347215.484,\"y\": 6691076.458,\"z\": 75.79799999999523}, {\"x\": 347192.844,\"y\": 6691084.436,\"z\": 74.4890000000014}\n\t\t\t]"
      val points6117634 = "[{\"x\": 347192.844,\"y\": 6691084.436,\"z\": 74.4890000000014}, {\"x\": 347195.215,\"y\": 6691095.648,\"z\": 75.3530000000028}, {\"x\": 347196.811,\"y\": 6691103.099,\"z\": 75.49400000000605}, {\"x\": 347205.175,\"y\": 6691113.893,\"z\": 75.48200000000361}, {\"x\": 347213.149,\"y\": 6691121.525,\"z\": 75.04799999999523}, {\"x\": 347222.515,\"y\": 6691129.117,\"z\": 74.36599999999453}\n\t\t\t]"
      val points6117622 = "[{\"x\": 347222.515,\"y\": 6691129.117,\"z\": 74.36599999999453}, {\"x\": 347296.059,\"y\": 6691163.552,\"z\": 73.25999999999476}, {\"x\": 347329.959,\"y\": 6691183.572,\"z\": 72.48200000000361}, {\"x\": 347375.14,\"y\": 6691205.863,\"z\": 72.48799999999756}, {\"x\": 347389.638,\"y\": 6691212.811,\"z\": 72.50900000000547}, {\"x\": 347399.44,\"y\": 6691215.717,\"z\": 72.32399999999325}, {\"x\": 347408.855,\"y\": 6691217.184,\"z\": 72.49899999999616}, {\"x\": 347428.039,\"y\": 6691217.13,\"z\": 72.18600000000151}, {\"x\": 347462.513,\"y\": 6691220.325,\"z\": 68.82700000000477}\n\t\t\t]"
      val points6638330 = "[{\"x\": 347462.513,\"y\": 6691220.325,\"z\": 68.82700000000477}, {\"x\": 347465.816,\"y\": 6691233.667,\"z\": 68.30000000000291}, {\"x\": 347469.693,\"y\": 6691245.746,\"z\": 67.46199999999953}\n\t\t\t]"
      val points6638318 = "[{\"x\": 347469.693,\"y\": 6691245.746,\"z\": 67.46199999999953}, {\"x\": 347474.552,\"y\": 6691259.384,\"z\": 66.81200000000536}, {\"x\": 347481.131,\"y\": 6691278.714,\"z\": 66.34799999999814}, {\"x\": 347489.276,\"y\": 6691301.289,\"z\": 66.16800000000512}, {\"x\": 347508.206,\"y\": 6691356.431,\"z\": 66.41899999999441}, {\"x\": 347510.825,\"y\": 6691363.505,\"z\": 66.48699999999371}, {\"x\": 347517.822,\"y\": 6691383.715,\"z\": 66.65499999999884}, {\"x\": 347523.459,\"y\": 6691411.166,\"z\": 66.71400000000722}, {\"x\": 347529.779,\"y\": 6691465.798,\"z\": 65.80599999999686}, {\"x\": 347531.806,\"y\": 6691490.947,\"z\": 65.40899999999965}, {\"x\": 347532.674,\"y\": 6691502.775,\"z\": 65.3350000000064}, {\"x\": 347536.133,\"y\": 6691514.845,\"z\": 65.5219999999972}, {\"x\": 347540.827,\"y\": 6691527.15,\"z\": 65.86299999999756}, {\"x\": 347543.003,\"y\": 6691531.606,\"z\": 65.91199999999662}\n\t\t\t]"
      val points6117602 = "[{\"x\": 347543.003,\"y\": 6691531.606,\"z\": 65.91199999999662}, {\"x\": 347549.505,\"y\": 6691541.083,\"z\": 66.16999999999825}, {\"x\": 347558.624,\"y\": 6691553.305,\"z\": 66.45600000000559}, {\"x\": 347566.507,\"y\": 6691565.246,\"z\": 66.63800000000629}, {\"x\": 347569.67,\"y\": 6691574.842,\"z\": 66.4780000000028}, {\"x\": 347570.568,\"y\": 6691586.054,\"z\": 66.2670000000071}, {\"x\": 347568.041,\"y\": 6691610.28,\"z\": 66.2719999999972}, {\"x\": 347565.481,\"y\": 6691630.916,\"z\": 66.46700000000419}, {\"x\": 347562.544,\"y\": 6691652.941,\"z\": 66.52999999999884}, {\"x\": 347561.214,\"y\": 6691669.255,\"z\": 66.74199999999837}, {\"x\": 347562.115,\"y\": 6691680.721,\"z\": 66.70299999999406}, {\"x\": 347566.876,\"y\": 6691702.688,\"z\": 66.37099999999919}, {\"x\": 347572.721,\"y\": 6691728.019,\"z\": 65.76200000000244}, {\"x\": 347577.691,\"y\": 6691742.972,\"z\": 65.99300000000221}, {\"x\": 347589.125,\"y\": 6691757.394,\"z\": 66.46700000000419}\n\t\t\t]"
      val points6638304 = "[{\"x\": 347589.125,\"y\": 6691757.394,\"z\": 66.46700000000419}, {\"x\": 347605.166,\"y\": 6691763.643,\"z\": 66.93600000000151}, {\"x\": 347621.417,\"y\": 6691767.811,\"z\": 67.88700000000244}, {\"x\": 347631.827,\"y\": 6691772.695,\"z\": 68.62300000000687}, {\"x\": 347644.334,\"y\": 6691781.978,\"z\": 69.44400000000314}, {\"x\": 347655.168,\"y\": 6691792.186,\"z\": 69.64400000000023}, {\"x\": 347666.21,\"y\": 6691800.729,\"z\": 69.82399999999325}, {\"x\": 347681.001,\"y\": 6691809.27,\"z\": 70.12399999999616}, {\"x\": 347698.488,\"y\": 6691813.432,\"z\": 70.43300000000454}, {\"x\": 347714.753,\"y\": 6691815.104,\"z\": 71.29700000000594}, {\"x\": 347735.586,\"y\": 6691816.771,\"z\": 73.25400000000081}, {\"x\": 347752.67,\"y\": 6691822.397,\"z\": 74.45200000000477}, {\"x\": 347769.753,\"y\": 6691831.563,\"z\": 76.11599999999453}, {\"x\": 347782.671,\"y\": 6691841.772,\"z\": 77.9149999999936}, {\"x\": 347794.175,\"y\": 6691857.513,\"z\": 77.91999999999825}, {\"x\": 347804.113,\"y\": 6691870.818,\"z\": 77.25}, {\"x\": 347816.213,\"y\": 6691894.065,\"z\": 77.00199999999313}, {\"x\": 347818.504,\"y\": 6691907.815,\"z\": 76.95799999999872}, {\"x\": 347815.796,\"y\": 6691921.149,\"z\": 76.63899999999558}, {\"x\": 347812.255,\"y\": 6691935.107,\"z\": 76.23500000000058}, {\"x\": 347808.504,\"y\": 6691948.441,\"z\": 75.87099999999919}, {\"x\": 347807.463,\"y\": 6691963.025,\"z\": 75.77700000000186}, {\"x\": 347808.087,\"y\": 6691979.9,\"z\": 75.65799999999581}, {\"x\": 347808.296,\"y\": 6691994.484,\"z\": 75.32000000000698}, {\"x\": 347807.045,\"y\": 6692008.235,\"z\": 75.09900000000198}, {\"x\": 347804.962,\"y\": 6692018.026,\"z\": 74.73099999999977}, {\"x\": 347798.921,\"y\": 6692026.985,\"z\": 74.33299999999872}, {\"x\": 347790.795,\"y\": 6692039.068,\"z\": 74.06100000000151}, {\"x\": 347784.753,\"y\": 6692048.652,\"z\": 73.76399999999558}, {\"x\": 347780.586,\"y\": 6692057.818,\"z\": 73.55100000000675}, {\"x\": 347777.461,\"y\": 6692070.319,\"z\": 73.46799999999348}, {\"x\": 347774.336,\"y\": 6692088.861,\"z\": 73.45699999999488}, {\"x\": 347771.836,\"y\": 6692111.361,\"z\": 73.25599999999395}, {\"x\": 347770.957,\"y\": 6692132.505,\"z\": 73.46499999999651}, {\"x\": 347770.586,\"y\": 6692154.696,\"z\": 73.65700000000652}, {\"x\": 347767.669,\"y\": 6692177.821,\"z\": 73.69599999999627}, {\"x\": 347765.585,\"y\": 6692201.989,\"z\": 74.3530000000028}, {\"x\": 347764.961,\"y\": 6692214.696,\"z\": 74.52099999999336}, {\"x\": 347766.419,\"y\": 6692223.864,\"z\": 74.40899999999965}, {\"x\": 347771.133,\"y\": 6692242.917,\"z\": 74.57200000000012}, {\"x\": 347773.409,\"y\": 6692254.612,\"z\": 74.51399999999558}, {\"x\": 347779.769,\"y\": 6692295.669,\"z\": 74.72500000000582}\n\t\t\t]"

      val geom6117675 = toGeom(JSON.parseFull(points6117675))
      val geom6638374 = toGeom(JSON.parseFull(points6638374))
      val geom6638371 = toGeom(JSON.parseFull(points6638371))
      val geom6638357 = toGeom(JSON.parseFull(points6638357))
      val geom6117732 = toGeom(JSON.parseFull(points6117732))
      val geom6117725 = toGeom(JSON.parseFull(points6117725))
      val geom6638363 = toGeom(JSON.parseFull(points6638363))
      val geom6117633 = toGeom(JSON.parseFull(points6117633))
      val geom6117621 = toGeom(JSON.parseFull(points6117621))
      val geom6638300 = toGeom(JSON.parseFull(points6638300))
      val geom6117634 = toGeom(JSON.parseFull(points6117634))
      val geom6117622 = toGeom(JSON.parseFull(points6117622))
      val geom6638330 = toGeom(JSON.parseFull(points6638330))
      val geom6638318 = toGeom(JSON.parseFull(points6638318))
      val geom6117602 = toGeom(JSON.parseFull(points6117602))
      val geom6638304 = toGeom(JSON.parseFull(points6638304))
      val mappedGeoms = Map(
        6117675l -> geom6117675,
        6638374l -> geom6638374,
        6638371l -> geom6638371,
        6638357l -> geom6638357,
        6117732l -> geom6117732,
        6117725l -> geom6117725,
        6638363l -> geom6638363,
        6117633l -> geom6117633,
        6117621l -> geom6117621,
        6638300l -> geom6638300,
        6117634l -> geom6117634,
        6117622l -> geom6117622,
        6638330l -> geom6638330,
        6638318l -> geom6638318,
        6117602l -> geom6117602,
        6638304l -> geom6638304
      )

      val links=ProjectDAO.getProjectLinks(7081807)
      links.nonEmpty should be (true)
      val geomToLinks:List[ProjectLink] = links.map{l =>
        val geom = mappedGeoms(l.linkId)
        l.copy(geometry = geom,
          geometryLength = GeometryUtils.geometryLength(geom),
          endMValue = GeometryUtils.geometryLength(geom)
        )
      }
      ProjectDAO.updateProjectLinksToDB(geomToLinks, "-")

      reset(mockRoadLinkService)
      when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean], any[Boolean])).thenAnswer(
        toMockAnswer(geomToLinks.map(toRoadLink))
      )
      projectService.changeDirection(7081807, 77997, 1)
      val changedLinks = ProjectDAO.getProjectLinks(7081807)

      geomToLinks.foreach { l =>
        GeometryUtils.geometryEndpoints(mappedGeoms(l.linkId)) should be (GeometryUtils.geometryEndpoints(l.geometry))
      }
//      prettyPrint(geomToLinks)
//      prettyPrint(changedLinks)

      val linksFirst = links.sortBy(_.id).head
      val linksLast = links.sortBy(_.id).last
      val changedLinksFirst = changedLinks.sortBy(_.id).head
      val changedLinksLast = changedLinks.sortBy(_.id).last

      geomToLinks.sortBy(_.id).zip(changedLinks.sortBy(_.id)).foreach{
        case (oldLink, newLink) =>
          oldLink.startAddrMValue should be ((linksLast.endAddrMValue - newLink.endAddrMValue) +- 1)
          oldLink.endAddrMValue should be ((linksLast.endAddrMValue - newLink.startAddrMValue) +- 1)
          val trackChangeCorrect = (oldLink.track, newLink.track) match {
            case (Track.Combined, Track.Combined) => true
            case (Track.RightSide, Track.LeftSide) => true
            case (Track.LeftSide, Track.RightSide) => true
            case _ => false
          }
          trackChangeCorrect should be (true)
      }

      linksFirst.id should be (changedLinksFirst.id)
      linksLast.id should be (changedLinksLast.id)
      linksLast.geometryLength should be (changedLinks.sortBy(_.id).last.geometryLength)
      linksLast.endMValue should be (changedLinks.sortBy(_.id).last.endMValue)
      linksFirst.endMValue should be (changedLinksFirst.endMValue)
      linksLast.endMValue should be (changedLinksLast.endMValue)
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
      val roadAddressProject = RoadAddressProject(0, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List.empty[ReservedRoadPart], None, Some(-1L))
      val (project, projLinkOpt, formLines, str) = projectService.createRoadLinkProject(roadAddressProject)
      project.ely.get should be(-1)
      val result = projectService.setProjectEly(project.id, 2)
      result should be (None)
      val result2 = projectService.setProjectEly(project.id, 2)
      result2 should be (None)
      val result3 = projectService.setProjectEly(project.id, 3)
      result3.isEmpty should be (false)
    }
  }

  test("Project link direction change should remain after adding new links") {
    runWithRollback {
      sqlu"DELETE FROM ROAD_ADDRESS WHERE ROAD_NUMBER=75 AND ROAD_PART_NUMBER=2".execute
      val id = Sequences.nextViitePrimaryKeySeqValue
      val rap = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("1901-01-01"), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List.empty, None)
      ProjectDAO.createRoadAddressProject(rap)

      val points5176552 = "[{\"x\":537869.292,\"y\":6997722.466,\"z\":110.39800000000105},"+
        "{\"x\":538290.056,\"y\":6998265.169,\"z\":85.4429999999993}]"
      val points5176512 = "[{\"x\":537152.306,\"y\":6996873.826,\"z\":108.27700000000186}," +
        "{\"x\":537869.292,\"y\":6997722.466,\"z\":110.39800000000105}]"
      val geom512 = JSON.parseFull(points5176512).get.asInstanceOf[List[Map[String, Double]]].map(m => Point(m("x"), m("y"), m("z")))
      val geom552 = JSON.parseFull(points5176552).get.asInstanceOf[List[Map[String, Double]]].map(m => Point(m("x"), m("y"), m("z")))

      val addProjectAddressLink512 = ProjectAddressLink(NewRoadAddress, 5176512, geom512, GeometryUtils.geometryLength(geom512),
        State, Motorway, RoadLinkType.NormalRoadLinkType, ConstructionType.InUse, LinkGeomSource.NormalLinkInterface,
        RoadType.PublicRoad, "X", 749, None, None, Map.empty, 75, 2, 0L, 8L, 5L, 0L, 0L, 0.0, GeometryUtils.geometryLength(geom512),
        SideCode.TowardsDigitizing, None, None, Anomaly.None, 0L, LinkStatus.New)
      val addProjectAddressLink552 = ProjectAddressLink(NewRoadAddress, 5176552, geom552, GeometryUtils.geometryLength(geom552),
        State, Motorway, RoadLinkType.NormalRoadLinkType, ConstructionType.InUse, LinkGeomSource.NormalLinkInterface,
        RoadType.PublicRoad, "X", 749, None, None, Map.empty, 75, 2, 0L, 8L, 5L, 0L, 0L, 0.0, GeometryUtils.geometryLength(geom552),
        SideCode.TowardsDigitizing, None, None, Anomaly.None, 0L, LinkStatus.New)
      val addresses = Seq(addProjectAddressLink512, addProjectAddressLink552)
      when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(addresses.map(_.linkId).toSet, false, false)).thenReturn(addresses.map(toRoadLink))
      projectService.addNewLinksToProject(addresses, id, 75, 2, 0L, 5L, 5L) should be (None)
      val links=ProjectDAO.getProjectLinks(id)
      links.map(_.linkId).toSet should be (addresses.map(_.linkId).toSet)
      val sideCodes = links.map(l => l.id -> l.sideCode).toMap
      projectService.changeDirection(id, 75, 2) should be (None)
      val changedLinks = ProjectDAO.getProjectLinksById(links.map{l => l.id})
      changedLinks.foreach(cl => cl.sideCode should not be (sideCodes(cl.id)))

      val points5176584 = "[{\"x\":538290.056,\"y\":6998265.169,\"z\":85.4429999999993}," +
        "{\"x\":538418.3307786948,\"y\":6998426.422734798,\"z\":88.17597963771014}]"
      val geom584 = JSON.parseFull(points5176584).get.asInstanceOf[List[Map[String, Double]]].map(m => Point(m("x"), m("y"), m("z")))
      val addProjectAddressLink584 = ProjectAddressLink(NewRoadAddress, 5176584, geom584, GeometryUtils.geometryLength(geom584),
        State, Motorway, RoadLinkType.NormalRoadLinkType, ConstructionType.InUse, LinkGeomSource.NormalLinkInterface,
        RoadType.PublicRoad, "X", 749, None, None, Map.empty, 75, 2, 0L, 8L, 5L, 0L, 0L, 0.0, GeometryUtils.geometryLength(geom584),
        SideCode.TowardsDigitizing, None, None, Anomaly.None, 0L, LinkStatus.New)

      when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(addresses.map(_.linkId).toSet, false, false)).thenReturn(addresses.map(toRoadLink))
      projectService.addNewLinksToProject(Seq(addProjectAddressLink584), id, 75, 2, 0L, 5L, 5L) should be (None)

      val linksAfter=ProjectDAO.getProjectLinks(id)
      linksAfter should have size (links.size + 1)
      linksAfter.find(_.linkId == addProjectAddressLink584.linkId).map(_.sideCode) should be (Some(AgainstDigitizing))
      linksAfter.find(_.linkId == 5176512).get.endAddrMValue should be (2003)
      linksAfter.find(_.linkId == 5176512).get.startAddrMValue should be (892)
      linksAfter.find(_.linkId == 5176584).get.startAddrMValue should be (0)
    }
  }

  test("Project links direction change shouldn't work due to unchanged links on road"){
    runWithRollback {
      sqlu"DELETE FROM ROAD_ADDRESS WHERE ROAD_NUMBER=75 AND ROAD_PART_NUMBER=2".execute
      val id = Sequences.nextViitePrimaryKeySeqValue
      val rap = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("1901-01-01"), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List.empty, None)
      ProjectDAO.createRoadAddressProject(rap)

      val points5176552 = "[{\"x\":537869.292,\"y\":6997722.466,\"z\":110.39800000000105}," +
        "{\"x\":538290.056,\"y\":6998265.169,\"z\":85.4429999999993}]"
      val points5176512 = "[{\"x\":537152.306,\"y\":6996873.826,\"z\":108.27700000000186}," +
        "{\"x\":537869.292,\"y\":6997722.466,\"z\":110.39800000000105}]"
      val geom512 = JSON.parseFull(points5176512).get.asInstanceOf[List[Map[String, Double]]].map(m => Point(m("x"), m("y"), m("z")))
      val geom552 = JSON.parseFull(points5176552).get.asInstanceOf[List[Map[String, Double]]].map(m => Point(m("x"), m("y"), m("z")))

      val addProjectAddressLink512 = ProjectAddressLink(NewRoadAddress, 5176512, geom512, GeometryUtils.geometryLength(geom512),
        State, Motorway, RoadLinkType.NormalRoadLinkType, ConstructionType.InUse, LinkGeomSource.NormalLinkInterface,
        RoadType.PublicRoad, "X", 749, None, None, Map.empty, 75, 2, 0L, 8L, 5L, 0L, 0L, 0.0, GeometryUtils.geometryLength(geom512),
        SideCode.TowardsDigitizing, None, None, Anomaly.None, 0L, LinkStatus.New)
      val addProjectAddressLink552 = ProjectAddressLink(NewRoadAddress, 5176552, geom552, GeometryUtils.geometryLength(geom552),
        State, Motorway, RoadLinkType.NormalRoadLinkType, ConstructionType.InUse, LinkGeomSource.NormalLinkInterface,
        RoadType.PublicRoad, "X", 749, None, None, Map.empty, 75, 2, 0L, 8L, 5L, 0L, 0L, 0.0, GeometryUtils.geometryLength(geom552),
        SideCode.TowardsDigitizing, None, None, Anomaly.None, 0L, LinkStatus.New)
      val addresses = Seq(addProjectAddressLink512, addProjectAddressLink552)
      when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(addresses.map(_.linkId).toSet, false, false)).thenReturn(addresses.map(toRoadLink))
      projectService.addNewLinksToProject(addresses, id, 75, 2, 0L, 5L, 5L) should be(None)
      val links = ProjectDAO.getProjectLinks(id)
      ProjectDAO.updateProjectLinkStatus(Set(links.head.id), LinkStatus.UnChanged, "test")
      links.map(_.linkId).toSet should be(addresses.map(_.linkId).toSet)
      val result = projectService.changeDirection(id, 75, 2)
      result should be (Some("Tieosalle ei voi tehdä kasvusuunnan kääntöä, koska tieosalla on linkkejä, jotka on tässä projektissa määritelty säilymään ennallaan."))
    }
  }

  test("Project should not allow adding branching links") {
    runWithRollback {
      sqlu"DELETE FROM ROAD_ADDRESS WHERE ROAD_NUMBER=75 AND ROAD_PART_NUMBER=2".execute
      val id = Sequences.nextViitePrimaryKeySeqValue
      val rap = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("1901-01-01"), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List.empty, None)
      ProjectDAO.createRoadAddressProject(rap)

      val points5176552 = "[{\"x\":537869.292,\"y\":6997722.466,\"z\":110.39800000000105},"+
        "{\"x\":538290.056,\"y\":6998265.169,\"z\":85.4429999999993}]"
      val points5176512 = "[{\"x\":537152.306,\"y\":6996873.826,\"z\":108.27700000000186}," +
        "{\"x\":538290.056,\"y\":6998265.169,\"z\":85.4429999999993}]"
      val points5176584 = "[{\"x\":538290.056,\"y\":6998265.169,\"z\":85.4429999999993}," +
        "{\"x\":538418.3307786948,\"y\":6998426.422734798,\"z\":88.17597963771014}]"
      val geom512 = JSON.parseFull(points5176512).get.asInstanceOf[List[Map[String, Double]]].map(m => Point(m("x"), m("y"), m("z")))
      val geom552 = JSON.parseFull(points5176552).get.asInstanceOf[List[Map[String, Double]]].map(m => Point(m("x"), m("y"), m("z")))
      val geom584 = JSON.parseFull(points5176584).get.asInstanceOf[List[Map[String, Double]]].map(m => Point(m("x"), m("y"), m("z")))

      val addProjectAddressLink512 = ProjectAddressLink(NewRoadAddress, 5176512, geom512, GeometryUtils.geometryLength(geom512),
        State, Motorway, RoadLinkType.NormalRoadLinkType, ConstructionType.InUse, LinkGeomSource.NormalLinkInterface,
        RoadType.PublicRoad, "X", 749, None, None, Map.empty, 75, 2, 0L, 8L, 5L, 0L, 0L, 0.0, GeometryUtils.geometryLength(geom512),
        SideCode.TowardsDigitizing, None, None, Anomaly.None, 0L, LinkStatus.New)
      val addProjectAddressLink552 = ProjectAddressLink(NewRoadAddress, 5176552, geom552, GeometryUtils.geometryLength(geom552),
        State, Motorway, RoadLinkType.NormalRoadLinkType, ConstructionType.InUse, LinkGeomSource.NormalLinkInterface,
        RoadType.PublicRoad, "X", 749, None, None, Map.empty, 75, 2, 0L, 8L, 5L, 0L, 0L, 0.0, GeometryUtils.geometryLength(geom552),
        SideCode.TowardsDigitizing, None, None, Anomaly.None, 0L, LinkStatus.New)
      val addProjectAddressLink584 = ProjectAddressLink(NewRoadAddress, 5176584, geom584, GeometryUtils.geometryLength(geom584),
        State, Motorway, RoadLinkType.NormalRoadLinkType, ConstructionType.InUse, LinkGeomSource.NormalLinkInterface,
        RoadType.PublicRoad, "X", 749, None, None, Map.empty, 75, 2, 0L, 8L, 5L, 0L, 0L, 0.0, GeometryUtils.geometryLength(geom584),
        SideCode.TowardsDigitizing, None, None, Anomaly.None, 0L, LinkStatus.New)

      val addresses = Seq(addProjectAddressLink512, addProjectAddressLink552, addProjectAddressLink584)
      when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(addresses.map(_.linkId).toSet, false, false)).thenReturn(addresses.map(toRoadLink))
      projectService.addNewLinksToProject(addresses, id, 75, 2, 0L, 5L, 5L) should be (Some("Valittu tiegeometria sisältää haarautumia ja pitää käsitellä osina. Tallennusta ei voi tehdä."))
      val links=ProjectDAO.getProjectLinks(id)
      links.size should be (0)
    }
  }
}
