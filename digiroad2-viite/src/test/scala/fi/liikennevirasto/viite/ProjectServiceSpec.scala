package fi.liikennevirasto.viite

import java.net.ConnectException
import java.util.Properties

import fi.liikennevirasto.viite.util.prettyPrint
import fi.liikennevirasto.digiroad2.asset.ConstructionType.InUse
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, TowardsDigitizing}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, SpeedLimitFiller}
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.Sequences
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, Point, RoadLinkService, _}
import fi.liikennevirasto.viite.dao.Discontinuity.Discontinuous
import fi.liikennevirasto.viite.dao.{Discontinuity, ProjectState, RoadAddressProject, _}
import fi.liikennevirasto.viite.model.{Anomaly, ProjectAddressLink, RoadAddressLinkLike}
import fi.liikennevirasto.viite.process.ProjectDeltaCalculator
import fi.liikennevirasto.viite.util.StaticTestData
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

  private def toProjectLink(project: RoadAddressProject, status: LinkStatus)(roadAddress: RoadAddress): ProjectLink = {
    ProjectLink(id = NewRoadAddress, roadAddress.roadNumber, roadAddress.roadPartNumber, roadAddress.track,
      roadAddress.discontinuity, roadAddress.startAddrMValue, roadAddress.endAddrMValue, roadAddress.startDate,
      roadAddress.endDate, modifiedBy=Option(project.createdBy), 0L, roadAddress.linkId, roadAddress.startMValue, roadAddress.endMValue,
      roadAddress.sideCode, roadAddress.calibrationPoints, floating = false, roadAddress.geometry, project.id, status, RoadType.PublicRoad, roadAddress.linkGeomSource, GeometryUtils.geometryLength(roadAddress.geometry))
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
      val addresses = List(ReservedRoadPart(5: Long, 203: Long, 203: Long, 5: Double, 5: Long, Discontinuity.apply("jatkuva"), 8: Long, None: Option[DateTime], None: Option[DateTime]))
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
    when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean],any[Boolean])).thenReturn(Seq(roadLink))
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
      projectService.getRoadAddressSingleProject(savedProject.id).nonEmpty should be (true)
      projectService.getRoadAddressSingleProject(savedProject.id).get.reservedParts.nonEmpty should be (true)
      val projectLinks = ProjectDAO.getProjectLinks(savedProject.id)
      val partitioned = projectLinks.partition(_.roadPartNumber == 205)
      val linkIds205 = partitioned._1.map(_.linkId).toSet
      val linkIds206 = partitioned._2.map(_.linkId).toSet
      reset(mockRoadLinkService)
      when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean], any[Boolean])).thenAnswer(
        toMockAnswer(projectLinks, roadLink)
      )
      projectService.updateProjectLinks(savedProject.id, linkIds205, LinkStatus.UnChanged,"-")
      projectService.projectLinkPublishable(savedProject.id) should be(false)
      projectService.updateProjectLinks(savedProject.id, linkIds206, LinkStatus.UnChanged, "-")
      projectService.projectLinkPublishable(savedProject.id) should be(true)
      projectService.updateProjectLinks(savedProject.id, Set(5168573), LinkStatus.Terminated, "-")
      projectService.projectLinkPublishable(savedProject.id) should be(true)
      val changeProjectOpt = projectService.getChangeProject(savedProject.id)
      val change = changeProjectOpt.get
      val updatedProjectLinks=ProjectDAO.getProjectLinks(savedProject.id)
      updatedProjectLinks.exists { x=> x.status==LinkStatus.UnChanged } should be(true)
      updatedProjectLinks.exists { x=> x.status==LinkStatus.Terminated } should be(true)
      updatedProjectLinks.filter( pl => pl.linkId==5168579).head.calibrationPoints should be ((None,Some(CalibrationPoint(5168579,15.173,4681))))
      projectService.updateProjectLinks(savedProject.id, Set(5168579), LinkStatus.Terminated, "-")
      val updatedProjectLinks2=ProjectDAO.getProjectLinks(savedProject.id)
      updatedProjectLinks2.filter( pl => pl.linkId==5168579).head.calibrationPoints should be ((None,None))
      updatedProjectLinks2.filter( pl => pl.linkId==5168583).head.calibrationPoints should be ((None,Some(CalibrationPoint(5168583,63.8,4666))))
      updatedProjectLinks2.filter(pl => pl.roadPartNumber==205 ).exists { x=> x.status==LinkStatus.Terminated } should be( false)
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
    when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean],any[Boolean])).thenReturn(Seq(roadLink))
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
      val highestDistanceStart= projectLinks.map(p=>p.startAddrMValue).max
      val highestDistanceEnd= projectLinks.map(p=>p.endAddrMValue).max
      val linkIds207 = partitioned._1.map(_.linkId).toSet
      reset(mockRoadLinkService)
      when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean], any[Boolean])).thenAnswer(
        toMockAnswer(projectLinks, roadLink)
      )
      projectService.updateProjectLinks(savedProject.id, linkIds207, LinkStatus.Transfer, "-")
      projectService.updateProjectLinks(savedProject.id, Set(5168510), LinkStatus.Terminated, "-")
      projectService.projectLinkPublishable(savedProject.id) should be(true)
      val changeProjectOpt = projectService.getChangeProject(savedProject.id)
      val change = changeProjectOpt.get
      val updatedProjectLinks=ProjectDAO.getProjectLinks(savedProject.id)
      updatedProjectLinks.exists { x=> x.status==LinkStatus.Transfer } should be(true)
      updatedProjectLinks.exists { x=> x.status==LinkStatus.Terminated } should be(true)
      updatedProjectLinks.filter( pl => pl.linkId==5168540).head.calibrationPoints should be ((Some(CalibrationPoint(5168540,0.0,0)),None))
      updatedProjectLinks.filter( pl => pl.linkId==6463199).head.calibrationPoints should be ((None,Some(CalibrationPoint(6463199,442.89,highestDistanceEnd-projectLinks.filter( pl => pl.linkId==5168510).head.endAddrMValue))))  //we terminated link with distance 172
      projectService.updateProjectLinks(savedProject.id, Set(5168540), LinkStatus.Terminated, "-")
      val updatedProjectLinks2=ProjectDAO.getProjectLinks(savedProject.id)
      updatedProjectLinks2.filter( pl => pl.linkId==6463199).head.calibrationPoints should be (None,Some(CalibrationPoint(6463199,442.89,highestDistanceEnd-projectLinks.filter( pl => pl.linkId==5168510).head.endAddrMValue-updatedProjectLinks.filter( pl => pl.linkId==5168540).head.endAddrMValue)))
    }
    runWithRollback {
      projectService.getRoadAddressAllProjects()
    } should have size (count - 1)
  }

  test("Terminate then transfer ") {
    var count = 0
    val roadLink = RoadLink(5170939L, Seq(Point(535605.272, 6982204.22, 85.90899999999965))
      , 540.3960283713503, State, 99, TrafficDirection.AgainstDigitizing, UnknownLinkType, Some("25.06.2015 03:00:00"), Some("vvh_modified"), Map("MUNICIPALITYCODE" -> BigInt.apply(749)),
      InUse, NormalLinkInterface)
    when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean],any[Boolean])).thenReturn(Seq(roadLink))
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
      val highestDistanceStart= projectLinks.map(p=>p.startAddrMValue).max
      val highestDistanceEnd= projectLinks.map(p=>p.endAddrMValue).max
      val linkIds207 = partitioned._1.map(_.linkId).toSet
      reset(mockRoadLinkService)
      when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean], any[Boolean])).thenAnswer(
        toMockAnswer(projectLinks, roadLink)
      )
      projectService.updateProjectLinks(savedProject.id, Set(5168510), LinkStatus.Terminated, "-")
      projectService.updateProjectLinks(savedProject.id, linkIds207-5168510, LinkStatus.Transfer, "-")
      projectService.projectLinkPublishable(savedProject.id) should be(true)
      val changeProjectOpt = projectService.getChangeProject(savedProject.id)
      val change = changeProjectOpt.get
      val updatedProjectLinks=ProjectDAO.getProjectLinks(savedProject.id)
      updatedProjectLinks.exists { x=> x.status==LinkStatus.Transfer } should be(true)
      updatedProjectLinks.exists { x=> x.status==LinkStatus.Terminated } should be(true)
      updatedProjectLinks.filter( pl => pl.linkId==5168540).head.calibrationPoints should be ((Some(CalibrationPoint(5168540,0.0,0)),None))
      updatedProjectLinks.filter( pl => pl.linkId==6463199).head.calibrationPoints should be ((None,Some(CalibrationPoint(6463199,442.89,highestDistanceEnd-172))))  //we terminated link with distance 172
      projectService.updateProjectLinks(savedProject.id, Set(5168540), LinkStatus.Terminated, "-")
      val updatedProjectLinks2=ProjectDAO.getProjectLinks(savedProject.id)
      updatedProjectLinks2.filter( pl => pl.linkId==6463199).head.calibrationPoints should be (None,Some(CalibrationPoint(6463199,442.89,highestDistanceEnd-projectLinks.filter( pl => pl.linkId==5168510).head.endAddrMValue-updatedProjectLinks.filter( pl => pl.linkId==5168540).head.endAddrMValue)))
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
      val addresses: List[ReservedRoadPart] = List(ReservedRoadPart(0: Long, 5: Long, 205: Long, 5: Double, 5: Long, Discontinuity.apply("jatkuva"), 8: Long, None: Option[DateTime], None: Option[DateTime]))
      val roadAddressProject = RoadAddressProject(0, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", addresses, None)
      val savedProject = projectService.createRoadLinkProject(roadAddressProject)
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
      val projectLinks = ProjectDAO.fetchByProjectNewRoadPart(5, 205, saved.id)
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
      val projectLinks = ProjectDAO.fetchByProjectNewRoadPart(5, 205, saved.id)
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
      projectService.updateProjectLinks(saved.id, linkIds, LinkStatus.Terminated, "-")
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
      projectService.updateProjectLinks(saved.id, linkIds, LinkStatus.Numbering, "-", 99999, 1)
      val afterNumberingLinks = ProjectDAO.getProjectLinks(saved.id)
      afterNumberingLinks.foreach(l => (l.roadNumber == 99999 && l.roadPartNumber == 1 ) should be (true))
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
        List(ReservedRoadPart(0: Long, roadNumber: Long, roadPartNumber: Long, 5: Double, 5: Long, Discontinuity.apply("jatkuva"),
          8: Long, None: Option[DateTime], None: Option[DateTime])), None)
      val proj = projectService.createRoadLinkProject(project)
      projectId = proj.id
      val projectLinkId = proj.reservedParts.head.startingLinkId.get
      val link = ProjectDAO.getProjectByLinkId(projectLinkId).head
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

  test("get change table test with update change table on every road link change") {
    var count = 0
    val roadLink = RoadLink(5170939L, Seq(Point(535605.272, 6982204.22, 85.90899999999965))
      , 540.3960283713503, State, 99, TrafficDirection.AgainstDigitizing, UnknownLinkType, Some("25.06.2015 03:00:00"), Some("vvh_modified"), Map("MUNICIPALITYCODE" -> BigInt.apply(749)),
      InUse, NormalLinkInterface)
    when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean], any[Boolean])).thenReturn(Seq(roadLink))
    runWithRollback {
      val countCurrentProjects = projectService.getRoadAddressAllProjects()
      val id = 0
      val addresses = List(ReservedRoadPart(5: Long, 5: Long, 205: Long, 5: Double, 5L, Discontinuity.apply("jatkuva"), 8: Long, None: Option[DateTime], None: Option[DateTime]),
          ReservedRoadPart(5: Long, 5: Long, 206: Long, 5: Double, 5L, Discontinuity.apply("jatkuva"), 8: Long, None: Option[DateTime], None: Option[DateTime]))
      val roadAddressProject = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", addresses, None)
      val saved = projectService.createRoadLinkProject(roadAddressProject)
      saved.reservedParts should have size (2)
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

      projectService.updateProjectLinks(saved.id, linkIds205, LinkStatus.Terminated, "-")
      projectService.projectLinkPublishable(saved.id) should be(false)


      projectService.updateProjectLinks(saved.id, linkIds206, LinkStatus.Terminated, "-")
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
        sTie should be(Some(5))
        sAosa.isEmpty should be(false)
        sAjr.isEmpty should be(false)
        sAet.isEmpty should be(false)
        sLet.isEmpty should be(false)
        tTie should be(None)
        tAosa.isEmpty should be(true)
        tAjr.isEmpty should be(true)
        tAet.isEmpty should be(true)
        tLet.isEmpty should be(true)
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
      val projectLink = toProjectLink(rap, LinkStatus.New)(RoadAddress(idr, 1943845, 1, RoadType.Unknown, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), Some(DateTime.parse("1902-01-01")), Option("tester"), 0, 12345L, 0.0, 9.8, SideCode.TowardsDigitizing, 0, (None, None), false,
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

      val projectLink1 = toProjectLink(rap, LinkStatus.New)(RoadAddress(idr1, 1943845, 1, RoadType.Unknown, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), Some(DateTime.parse("1902-01-01")), Option("tester"), 0, 5175306L, 0.0, 9.8, SideCode.TowardsDigitizing, 0, (None, None), false,
        Seq(Point(535602.222, 6982200.25, 89.9999),Point(535605.272, 6982204.22, 85.90899999999965)), LinkGeomSource.NormalLinkInterface))

      val projectLink2 = toProjectLink(rap, LinkStatus.New)(RoadAddress(idr2, 1943845, 1, RoadType.Unknown, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), Some(DateTime.parse("1902-01-01")), Option("tester"), 0, 1610976L, 0.0, 5.8, SideCode.TowardsDigitizing, 0, (None, None), false,
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
      val links=ProjectDAO.getProjectLinks(7081807)
      links.nonEmpty should be (true)
      val mappedGeoms = StaticTestData.mappedGeoms(links.map(_.linkId))
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
      val oldgeom512 = JSON.parseFull(points5176512).get.asInstanceOf[List[Map[String, Double]]].map(m => Point(m("x"), m("y"), m("z")))
      val oldgeom552 = JSON.parseFull(points5176552).get.asInstanceOf[List[Map[String, Double]]].map(m => Point(m("x"), m("y"), m("z")))

      val geometries = StaticTestData.mappedGeoms(Set(5176552, 5176512))
      val geom512 = geometries(5176512)
      val geom552 = geometries(5176552)

      oldgeom512 should be (geom512)
      oldgeom552 should be (geom552)
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

      val geom584 = StaticTestData.mappedGeoms(Seq(5176584L)).values.head
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

      val geometries = StaticTestData.mappedGeoms(Set(5176552, 5176512, 5176584))
      val geom512 = geometries(5176512)
      val geom552 = geometries(5176552)

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
      ProjectDAO.updateProjectLinks(Set(links.head.id), LinkStatus.UnChanged, "test")
      links.map(_.linkId).toSet should be(addresses.map(_.linkId).toSet)
      val result = projectService.changeDirection(id, 75, 2)
      result should be (Some("Tieosalle ei voi tehdä kasvusuunnan kääntöä, koska tieosalla on linkkejä, jotka on tässä projektissa määritelty säilymään ennallaan."))
    }
  }

  test("Growing direction should be same after adding new links to a reserved part") {
    runWithRollback {

      def toGeom(json: Option[Any]): List[Point] = {
        json.get.asInstanceOf[List[Map[String, Double]]].map(m => Point(m("x"), m("y"), m("z")))
      }

      val id = Sequences.nextViitePrimaryKeySeqValue
      val reservedRoadPart1 = ReservedRoadPart(164, 77, 35, 5405, 5405, Discontinuity.EndOfRoad, 8, None, None)
      val rap = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.now(), DateTime.now(), "Some additional info", Seq(reservedRoadPart1), None)
      ProjectDAO.createRoadAddressProject(rap)
      ProjectDAO.reserveRoadPart(rap.id, 77, 35, "TestUser")
      val addressesOnPart = RoadAddressDAO.fetchByRoadPart(77, 35, false)
      ProjectDAO.create(addressesOnPart.map(address => {
        toProjectLink(rap, LinkStatus.NotHandled)(address)
      }))

      val linksBefore = ProjectDAO.fetchByProjectNewRoadPart(77, 35, id).groupBy(_.linkId).map(_._2.head).toList

      val points5170271 = "[ {\"x\": 530492.408, \"y\": 6994103.892, \"z\": 114.60400000000664},{\"x\": 530490.492, \"y\": 6994104.815, \"z\": 114.63800000000629},{\"x\": 530459.903, \"y\": 6994118.958, \"z\": 114.97299999999814},{\"x\": 530427.446, \"y\": 6994134.189, \"z\": 115.30400000000373},{\"x\": 530392.422, \"y\": 6994153.545, \"z\": 115.721000000005},{\"x\": 530385.114, \"y\": 6994157.976, \"z\": 115.71099999999569},{\"x\": 530381.104, \"y\": 6994161.327, \"z\": 115.77000000000407},{\"x\": 530367.101, \"y\": 6994170.075, \"z\": 115.93099999999686},{\"x\": 530330.275, \"y\": 6994195.603, \"z\": 116.37200000000303}]"
      val points5170414 = "[ {\"x\": 531540.842, \"y\": 6993806.017, \"z\": 114.1530000000057},{\"x\": 531515.135, \"y\": 6993815.644, \"z\": 114.74400000000605}]"
      val points5170067 = "[ {\"x\": 529169.924, \"y\": 6994631.929, \"z\": 121.52999999999884},{\"x\": 529158.557, \"y\": 6994635.609, \"z\": 121.47999999999593},{\"x\": 529149.47, \"y\": 6994638.618, \"z\": 121.43300000000454}]"
      val points5170066 = "[ {\"x\": 529149.47, \"y\": 6994638.618, \"z\": 121.43300000000454},{\"x\": 529147.068, \"y\": 6994639.416, \"z\": 121.45200000000477},{\"x\": 529142.91, \"y\": 6994640.794, \"z\": 121.41700000000128},{\"x\": 529116.198, \"y\": 6994650.179, \"z\": 121.32600000000093},{\"x\": 529099.946, \"y\": 6994655.993, \"z\": 121.2670000000071}]"
      val points5170074 = "[ {\"x\": 528982.934, \"y\": 6994703.835, \"z\": 120.9030000000057},{\"x\": 528972.656, \"y\": 6994708.219, \"z\": 120.87699999999313},{\"x\": 528948.747, \"y\": 6994719.171, \"z\": 120.72999999999593},{\"x\": 528924.998, \"y\": 6994730.062, \"z\": 120.64500000000407},{\"x\": 528915.753, \"y\": 6994734.337, \"z\": 120.62799999999697}]"
      val points5170057 = "[ {\"x\": 529099.946, \"y\": 6994655.993, \"z\": 121.2670000000071},{\"x\": 529090.588, \"y\": 6994659.353, \"z\": 121.22400000000198},{\"x\": 529065.713, \"y\": 6994668.98, \"z\": 121.1469999999972},{\"x\": 529037.245, \"y\": 6994680.687, \"z\": 121.11000000000058},{\"x\": 529015.841, \"y\": 6994689.617, \"z\": 121.03100000000268},{\"x\": 528994.723, \"y\": 6994698.806, \"z\": 120.93499999999767},{\"x\": 528982.934, \"y\": 6994703.835, \"z\": 120.9030000000057}]"
      val points5170208 = "[ {\"x\": 531208.529, \"y\": 6993930.35, \"z\": 113.57600000000093},{\"x\": 531206.956, \"y\": 6993930.852, \"z\": 113.52400000000489},{\"x\": 531206.551, \"y\": 6993930.982, \"z\": 113.51799999999639},{\"x\": 531152.258, \"y\": 6993947.596, \"z\": 112.50900000000547},{\"x\": 531097.601, \"y\": 6993961.148, \"z\": 111.63300000000163},{\"x\": 531035.674, \"y\": 6993974.085, \"z\": 111.00199999999313},{\"x\": 531000.05, \"y\": 6993980.598, \"z\": 110.81100000000151},{\"x\": 530972.845, \"y\": 6993985.159, \"z\": 110.65600000000268}]"
      val points5170419 = "[ {\"x\": 531580.116, \"y\": 6993791.375, \"z\": 113.05299999999988},{\"x\": 531559.788, \"y\": 6993798.928, \"z\": 113.63000000000466}]"
      val points5170105 = "[ {\"x\": 528699.202, \"y\": 6994841.305, \"z\": 119.86999999999534},{\"x\": 528679.331, \"y\": 6994852.48, \"z\": 119.7390000000014},{\"x\": 528655.278, \"y\": 6994865.047, \"z\": 119.68700000000536},{\"x\": 528627.407, \"y\": 6994880.448, \"z\": 119.5679999999993},{\"x\": 528605.245, \"y\": 6994891.79, \"z\": 119.5219999999972},{\"x\": 528580.964, \"y\": 6994906.041, \"z\": 119.48200000000361}]"
      val points5170278 = "[ {\"x\": 530685.408, \"y\": 6994033.6, \"z\": 112.65899999999965},{\"x\": 530681.24, \"y\": 6994034.74, \"z\": 112.66800000000512},{\"x\": 530639.419, \"y\": 6994047.211, \"z\": 113.10400000000664},{\"x\": 530635.275, \"y\": 6994048.447, \"z\": 113.14400000000023},{\"x\": 530624.882, \"y\": 6994051.624, \"z\": 113.22599999999511},{\"x\": 530603.496, \"y\": 6994059.168, \"z\": 113.48699999999371},{\"x\": 530570.252, \"y\": 6994070.562, \"z\": 113.73600000000442},{\"x\": 530537.929, \"y\": 6994083.499, \"z\": 114.09399999999732},{\"x\": 530512.29, \"y\": 6994094.305, \"z\": 114.38899999999558},{\"x\": 530508.822, \"y\": 6994095.977, \"z\": 114.39999999999418}]"
      val points5170104 = "[ {\"x\": 528833.042, \"y\": 6994773.324, \"z\": 120.32499999999709},{\"x\": 528806.698, \"y\": 6994786.487, \"z\": 120.21099999999569},{\"x\": 528778.343, \"y\": 6994800.373, \"z\": 120.12699999999313},{\"x\": 528754.485, \"y\": 6994812.492, \"z\": 120.03200000000652},{\"x\": 528728.694, \"y\": 6994826.297, \"z\": 119.9210000000021},{\"x\": 528710.804, \"y\": 6994835.775, \"z\": 119.86599999999453},{\"x\": 528700.208, \"y\": 6994840.792, \"z\": 119.8640000000014},{\"x\": 528699.202, \"y\": 6994841.305, \"z\": 119.86999999999534}]"
      val points5170250 = "[ {\"x\": 530972.845, \"y\": 6993985.159, \"z\": 110.65600000000268},{\"x\": 530934.626, \"y\": 6993989.73, \"z\": 110.67999999999302},{\"x\": 530884.749, \"y\": 6993996.905, \"z\": 110.87300000000687},{\"x\": 530849.172, \"y\": 6994001.746, \"z\": 111.07600000000093},{\"x\": 530787.464, \"y\": 6994011.154, \"z\": 111.68300000000454}]"
      val points5170274 = "[ {\"x\": 530508.822, \"y\": 6994095.977, \"z\": 114.39999999999418},{\"x\": 530492.408, \"y\": 6994103.892, \"z\": 114.60400000000664}]"
      val points5170253 = "[ {\"x\": 530787.464, \"y\": 6994011.154, \"z\": 111.68300000000454},{\"x\": 530735.969, \"y\": 6994021.569, \"z\": 112.14800000000105},{\"x\": 530685.408, \"y\": 6994033.6, \"z\": 112.65899999999965}]"
      val points5170071 = "[ {\"x\": 528915.753, \"y\": 6994734.337, \"z\": 120.62799999999697},{\"x\": 528870.534, \"y\": 6994755.246, \"z\": 120.46899999999732},{\"x\": 528853.387, \"y\": 6994763.382, \"z\": 120.41899999999441}]"
      val points5170200 = "[ {\"x\": 531515.135, \"y\": 6993815.644, \"z\": 114.74400000000605},{\"x\": 531490.088, \"y\": 6993825.357, \"z\": 115.1469999999972},{\"x\": 531434.788, \"y\": 6993847.717, \"z\": 115.81100000000151},{\"x\": 531382.827, \"y\": 6993867.291, \"z\": 115.9320000000007},{\"x\": 531341.785, \"y\": 6993883.123, \"z\": 115.70500000000175},{\"x\": 531279.229, \"y\": 6993906.106, \"z\": 114.83800000000338},{\"x\": 531263.983, \"y\": 6993911.659, \"z\": 114.55400000000373},{\"x\": 531244.769, \"y\": 6993918.512, \"z\": 114.25299999999697},{\"x\": 531235.891, \"y\": 6993921.64, \"z\": 114.028999999995},{\"x\": 531208.529, \"y\": 6993930.35, \"z\": 113.57600000000093}]"
      val points5167598 = "[ {\"x\": 528349.166, \"y\": 6995051.88, \"z\": 119.27599999999802},{\"x\": 528334.374, \"y\": 6995062.151, \"z\": 119.37900000000081},{\"x\": 528318.413, \"y\": 6995072.576, \"z\": 119.49800000000687},{\"x\": 528296.599, \"y\": 6995087.822, \"z\": 119.59200000000419},{\"x\": 528278.343, \"y\": 6995100.519, \"z\": 119.69999999999709},{\"x\": 528232.133, \"y\": 6995133.027, \"z\": 119.97299999999814},{\"x\": 528212.343, \"y\": 6995147.292, \"z\": 120.07700000000477},{\"x\": 528190.409, \"y\": 6995162.14, \"z\": 120.19000000000233},{\"x\": 528161.952, \"y\": 6995182.369, \"z\": 120.3579999999929},{\"x\": 528137.864, \"y\": 6995199.658, \"z\": 120.34200000000419},{\"x\": 528105.957, \"y\": 6995221.607, \"z\": 120.3530000000028}]"
      val points5170095 = "[ {\"x\": 528580.964, \"y\": 6994906.041, \"z\": 119.48200000000361},{\"x\": 528562.314, \"y\": 6994917.077, \"z\": 119.4030000000057},{\"x\": 528545.078, \"y\": 6994926.326, \"z\": 119.37200000000303},{\"x\": 528519.958, \"y\": 6994942.165, \"z\": 119.23099999999977},{\"x\": 528497.113, \"y\": 6994955.7, \"z\": 119.18600000000151},{\"x\": 528474.271, \"y\": 6994969.872, \"z\": 119.07200000000012},{\"x\": 528452.7, \"y\": 6994983.398, \"z\": 119.05400000000373},{\"x\": 528435.576, \"y\": 6994994.982, \"z\": 119.01900000000023},{\"x\": 528415.274, \"y\": 6995007.863, \"z\": 119.0460000000021},{\"x\": 528398.486, \"y\": 6995018.309, \"z\": 119.07399999999325},{\"x\": 528378.206, \"y\": 6995031.988, \"z\": 119.12799999999697},{\"x\": 528355.441, \"y\": 6995047.458, \"z\": 119.2390000000014},{\"x\": 528349.166, \"y\": 6995051.88, \"z\": 119.27599999999802}]"
      val points5170060 = "[ {\"x\": 528853.387, \"y\": 6994763.382, \"z\": 120.41899999999441},{\"x\": 528843.513, \"y\": 6994768.09, \"z\": 120.37399999999616},{\"x\": 528833.042, \"y\": 6994773.324, \"z\": 120.32499999999709}]"
      val points5169973 = "[ {\"x\": 530293.785, \"y\": 6994219.573, \"z\": 116.8070000000007},{\"x\": 530284.91, \"y\": 6994225.31, \"z\": 116.93399999999383},{\"x\": 530236.998, \"y\": 6994260.627, \"z\": 117.38700000000244},{\"x\": 530201.104, \"y\": 6994288.586, \"z\": 117.58599999999569},{\"x\": 530151.371, \"y\": 6994326.968, \"z\": 117.95799999999872},{\"x\": 530124.827, \"y\": 6994345.782, \"z\": 118.0399999999936},{\"x\": 530085.669, \"y\": 6994374.285, \"z\": 118.43399999999383},{\"x\": 530046.051, \"y\": 6994399.019, \"z\": 118.89900000000489},{\"x\": 530004.759, \"y\": 6994422.268, \"z\": 119.39900000000489}]"
      val points5170344 = "[ {\"x\": 531642.975, \"y\": 6993763.489, \"z\": 110.8579999999929},{\"x\": 531600.647, \"y\": 6993781.993, \"z\": 112.40600000000268},{\"x\": 531580.116, \"y\": 6993791.375, \"z\": 113.05299999999988}]"
      val points5170036 = "[ {\"x\": 530004.759, \"y\": 6994422.268, \"z\": 119.39900000000489},{\"x\": 529971.371, \"y\": 6994440.164, \"z\": 119.82799999999406},{\"x\": 529910.61, \"y\": 6994469.099, \"z\": 120.69400000000314},{\"x\": 529849.474, \"y\": 6994494.273, \"z\": 121.42600000000675},{\"x\": 529816.479, \"y\": 6994506.294, \"z\": 121.8350000000064},{\"x\": 529793.423, \"y\": 6994513.982, \"z\": 122.00699999999779},{\"x\": 529746.625, \"y\": 6994527.76, \"z\": 122.31900000000314},{\"x\": 529708.779, \"y\": 6994537.658, \"z\": 122.49700000000303},{\"x\": 529696.431, \"y\": 6994540.722, \"z\": 122.54099999999744},{\"x\": 529678.274, \"y\": 6994544.52, \"z\": 122.57200000000012},{\"x\": 529651.158, \"y\": 6994549.764, \"z\": 122.63700000000244},{\"x\": 529622.778, \"y\": 6994555.281, \"z\": 122.65899999999965},{\"x\": 529605.13, \"y\": 6994557.731, \"z\": 122.6929999999993},{\"x\": 529530.471, \"y\": 6994567.94, \"z\": 122.75500000000466},{\"x\": 529502.649, \"y\": 6994571.568, \"z\": 122.74199999999837}]"
      val points5170418 = "[ {\"x\": 531559.788, \"y\": 6993798.928, \"z\": 113.63000000000466},{\"x\": 531558.07, \"y\": 6993799.566, \"z\": 113.67799999999988},{\"x\": 531540.842, \"y\": 6993806.017, \"z\": 114.1530000000057}]"
      val points5170114 = "[ {\"x\": 532675.864, \"y\": 6993667.121, \"z\": 119.63899999999558},{\"x\": 532585, \"y\": 6993623.826, \"z\": 119.29899999999907},{\"x\": 532524.074, \"y\": 6993601.11, \"z\": 119.1420000000071},{\"x\": 532471.813, \"y\": 6993584.678, \"z\": 118.99300000000221},{\"x\": 532432.652, \"y\": 6993575.034, \"z\": 118.85099999999511},{\"x\": 532390.813, \"y\": 6993567.143, \"z\": 118.47699999999895},{\"x\": 532344.481, \"y\": 6993559.882, \"z\": 117.69999999999709},{\"x\": 532300.07, \"y\": 6993555.626, \"z\": 116.75400000000081},{\"x\": 532254.457, \"y\": 6993553.43, \"z\": 115.49499999999534},{\"x\": 532213.217, \"y\": 6993553.879, \"z\": 114.13999999999942},{\"x\": 532166.868, \"y\": 6993558.077, \"z\": 112.27599999999802},{\"x\": 532123.902, \"y\": 6993564.359, \"z\": 110.53599999999278},{\"x\": 532078.039, \"y\": 6993574.524, \"z\": 108.90499999999884},{\"x\": 532026.264, \"y\": 6993589.43, \"z\": 107.60099999999511},{\"x\": 531990.015, \"y\": 6993602.5, \"z\": 106.84299999999348},{\"x\": 531941.753, \"y\": 6993623.417, \"z\": 106.15499999999884},{\"x\": 531885.2, \"y\": 6993648.616, \"z\": 105.94100000000617},{\"x\": 531847.551, \"y\": 6993667.432, \"z\": 106.03100000000268},{\"x\": 531829.085, \"y\": 6993676.017, \"z\": 106.096000000005},{\"x\": 531826.495, \"y\": 6993677.286, \"z\": 106.17600000000675},{\"x\": 531795.338, \"y\": 6993692.819, \"z\": 106.59100000000035},{\"x\": 531750.277, \"y\": 6993714.432, \"z\": 107.46099999999569},{\"x\": 531702.109, \"y\": 6993736.085, \"z\": 108.73500000000058},{\"x\": 531652.731, \"y\": 6993759.226, \"z\": 110.49000000000524},{\"x\": 531642.975, \"y\": 6993763.489, \"z\": 110.8579999999929}]"
      val points5170266 = "[ {\"x\": 530330.275, \"y\": 6994195.603, \"z\": 116.37200000000303},{\"x\": 530328.819, \"y\": 6994196.919, \"z\": 116.34900000000198},{\"x\": 530293.785, \"y\": 6994219.573, \"z\": 116.8070000000007}]"
      val points5170076 = "[ {\"x\": 529502.649, \"y\": 6994571.568, \"z\": 122.74199999999837},{\"x\": 529488.539, \"y\": 6994573.408, \"z\": 122.75999999999476},{\"x\": 529461.147, \"y\": 6994576.534, \"z\": 122.63099999999395},{\"x\": 529432.538, \"y\": 6994579.398, \"z\": 122.49700000000303},{\"x\": 529402.112, \"y\": 6994583.517, \"z\": 122.36199999999371},{\"x\": 529383.649, \"y\": 6994585.553, \"z\": 122.22500000000582},{\"x\": 529366.46, \"y\": 6994587.58, \"z\": 122.16700000000128},{\"x\": 529340.392, \"y\": 6994591.142, \"z\": 122.0679999999993},{\"x\": 529316.184, \"y\": 6994596.203, \"z\": 121.92500000000291},{\"x\": 529292.004, \"y\": 6994600.827, \"z\": 121.79200000000128},{\"x\": 529274.998, \"y\": 6994603.419, \"z\": 121.74300000000221},{\"x\": 529245.538, \"y\": 6994610.622, \"z\": 121.74899999999616},{\"x\": 529215.54, \"y\": 6994618.628, \"z\": 121.68499999999767},{\"x\": 529200.025, \"y\": 6994623.205, \"z\": 121.58400000000256},{\"x\": 529182.346, \"y\": 6994628.596, \"z\": 121.5109999999986},{\"x\": 529172.437, \"y\": 6994631.118, \"z\": 121.50999999999476},{\"x\": 529169.924, \"y\": 6994631.929, \"z\": 121.52999999999884}]"
      val points5171309 = "[ {\"x\": 532675.864, \"y\": 6993667.121, \"z\": 119.63899999999558},{\"x\": 532683.902, \"y\": 6993675.669, \"z\": 119.55599999999686},{\"x\": 532705.617, \"y\": 6993689.231, \"z\": 119.68700000000536},{\"x\": 532738.146, \"y\": 6993711.117, \"z\": 120.0170000000071},{\"x\": 532746.793, \"y\": 6993717.431, \"z\": 120.10199999999895}]"
      val points5171311 = "[ {\"x\": 532746.793, \"y\": 6993717.431, \"z\": 120.10199999999895},{\"x\": 532772.872, \"y\": 6993736.47, \"z\": 120.65099999999802},{\"x\": 532796.699, \"y\": 6993755.46, \"z\": 121.12600000000384},{\"x\": 532823.779, \"y\": 6993779.309, \"z\": 121.846000000005},{\"x\": 532851.887, \"y\": 6993806.211, \"z\": 122.5},{\"x\": 532872.336, \"y\": 6993827.537, \"z\": 123.10000000000582},{\"x\": 532888.184, \"y\": 6993844.293, \"z\": 123.59900000000198}]"
      val points5171041 = "[ {\"x\": 532900.164, \"y\": 6993858.933, \"z\": 123.9600000000064},{\"x\": 532900.464, \"y\": 6993859.263, \"z\": 123.96099999999569},{\"x\": 532913.982, \"y\": 6993873.992, \"z\": 124.37900000000081},{\"x\": 532945.588, \"y\": 6993907.014, \"z\": 125.26499999999942},{\"x\": 532967.743, \"y\": 6993930.553, \"z\": 125.91099999999278}]"
      val points5171044 = "[ {\"x\": 532888.184, \"y\": 6993844.293, \"z\": 123.59900000000198},{\"x\": 532895.422, \"y\": 6993852.852, \"z\": 123.8179999999993},{\"x\": 532900.164, \"y\": 6993858.933, \"z\": 123.9600000000064}]"
      val points5171310 = "[ {\"x\": 532752.967, \"y\": 6993710.487, \"z\": 120.50299999999697},{\"x\": 532786.845, \"y\": 6993735.945, \"z\": 121.07200000000012},{\"x\": 532821.582, \"y\": 6993764.354, \"z\": 121.84900000000198},{\"x\": 532852.237, \"y\": 6993791.247, \"z\": 122.63400000000547},{\"x\": 532875.743, \"y\": 6993813.072, \"z\": 123.15700000000652},{\"x\": 532895.051, \"y\": 6993834.921, \"z\": 123.71400000000722}]"
      val points5171042 = "[ {\"x\": 532895.051, \"y\": 6993834.921, \"z\": 123.71400000000722},{\"x\": 532904.782, \"y\": 6993844.523, \"z\": 123.94599999999627},{\"x\": 532911.053, \"y\": 6993850.749, \"z\": 124.0789999999979}]"
      val points5171040 = "[ {\"x\": 532911.053, \"y\": 6993850.749, \"z\": 124.0789999999979},{\"x\": 532915.004, \"y\": 6993854.676, \"z\": 124.16400000000431},{\"x\": 532934.432, \"y\": 6993875.496, \"z\": 124.625},{\"x\": 532952.144, \"y\": 6993896.59, \"z\": 125.21799999999348},{\"x\": 532976.907, \"y\": 6993922.419, \"z\": 125.43700000000536}]"
      val points5171308 = "[ {\"x\": 532675.864, \"y\": 6993667.121, \"z\": 119.63899999999558},{\"x\": 532706.975, \"y\": 6993682.696, \"z\": 119.89999999999418},{\"x\": 532731.983, \"y\": 6993696.366, \"z\": 120.1820000000007},{\"x\": 532752.967, \"y\": 6993710.487, \"z\": 120.50299999999697}]"

      val geom5170271 = toGeom(JSON.parseFull(points5170271))
      val geom5170414 = toGeom(JSON.parseFull(points5170414))
      val geom5170067 = toGeom(JSON.parseFull(points5170067))
      val geom5170066 = toGeom(JSON.parseFull(points5170066))
      val geom5170074 = toGeom(JSON.parseFull(points5170074))
      val geom5170057 = toGeom(JSON.parseFull(points5170057))
      val geom5170208 = toGeom(JSON.parseFull(points5170208))
      val geom5170419 = toGeom(JSON.parseFull(points5170419))
      val geom5170105 = toGeom(JSON.parseFull(points5170105))
      val geom5170278 = toGeom(JSON.parseFull(points5170278))
      val geom5170104 = toGeom(JSON.parseFull(points5170104))
      val geom5170250 = toGeom(JSON.parseFull(points5170250))
      val geom5170274 = toGeom(JSON.parseFull(points5170274))
      val geom5170253 = toGeom(JSON.parseFull(points5170253))
      val geom5170071 = toGeom(JSON.parseFull(points5170071))
      val geom5170200 = toGeom(JSON.parseFull(points5170200))
      val geom5167598 = toGeom(JSON.parseFull(points5167598))
      val geom5170095 = toGeom(JSON.parseFull(points5170095))
      val geom5170060 = toGeom(JSON.parseFull(points5170060))
      val geom5169973 = toGeom(JSON.parseFull(points5169973))
      val geom5170344 = toGeom(JSON.parseFull(points5170344))
      val geom5170036 = toGeom(JSON.parseFull(points5170036))
      val geom5170418 = toGeom(JSON.parseFull(points5170418))
      val geom5170114 = toGeom(JSON.parseFull(points5170114))
      val geom5170266 = toGeom(JSON.parseFull(points5170266))
      val geom5170076 = toGeom(JSON.parseFull(points5170076))
      val geom5171309 = toGeom(JSON.parseFull(points5171309))
      val geom5171311 = toGeom(JSON.parseFull(points5171311))
      val geom5171041 = toGeom(JSON.parseFull(points5171041))
      val geom5171044 = toGeom(JSON.parseFull(points5171044))
      val geom5171310 = toGeom(JSON.parseFull(points5171310))
      val geom5171042 = toGeom(JSON.parseFull(points5171042))
      val geom5171040 = toGeom(JSON.parseFull(points5171040))
      val geom5171308 = toGeom(JSON.parseFull(points5171308))

      val mappedGeoms = Map(
        5170271l -> geom5170271,
        5170414l -> geom5170414,
        5170067l -> geom5170067,
        5170066l -> geom5170066,
        5170074l -> geom5170074,
        5170057l -> geom5170057,
        5170208l -> geom5170208,
        5170419l -> geom5170419,
        5170105l -> geom5170105,
        5170278l -> geom5170278,
        5170104l -> geom5170104,
        5170250l -> geom5170250,
        5170274l -> geom5170274,
        5170253l -> geom5170253,
        5170071l -> geom5170071,
        5170200l -> geom5170200,
        5167598l -> geom5167598,
        5170095l -> geom5170095,
        5170060l -> geom5170060,
        5169973l -> geom5169973,
        5170344l -> geom5170344,
        5170036l -> geom5170036,
        5170418l -> geom5170418,
        5170114l -> geom5170114,
        5170266l -> geom5170266,
        5170076l -> geom5170076,
        5171309l -> geom5171309,
        5171311l -> geom5171311,
        5171041l -> geom5171041,
        5171044l -> geom5171044,
        5171310l -> geom5171310,
        5171042l -> geom5171042,
        5171040l -> geom5171040,
        5171308l -> geom5171308
      )

      //links.nonEmpty should be (true)
      val geomToLinks:List[ProjectLink] = linksBefore.map{l =>
        val geom = mappedGeoms(l.linkId)
        l.copy(geometry = geom,
          geometryLength = GeometryUtils.geometryLength(geom),
          endMValue = GeometryUtils.geometryLength(geom)
        )
      }

      val points = "[{\"x\": 528105.957, \"y\": 6995221.607, \"z\": 120.3530000000028}," +
                          "{\"x\": 528104.681, \"y\": 6995222.485, \"z\": 120.35099999999511}," +
                          "{\"x\": 528064.931, \"y\": 6995249.45, \"z\": 120.18099999999686}," +
                          "{\"x\": 528037.789, \"y\": 6995266.234, \"z\": 120.03100000000268}," +
                          "{\"x\": 528008.332, \"y\": 6995285.521, \"z\": 119.8969999999972}," +
                          "{\"x\": 527990.814, \"y\": 6995296.039, \"z\": 119.77300000000105}," +
                          "{\"x\": 527962.009, \"y\": 6995313.215, \"z\": 119.57099999999627}," +
                          "{\"x\": 527926.972, \"y\": 6995333.398, \"z\": 119.18799999999464}," +
                          "{\"x\": 527890.962, \"y\": 6995352.332, \"z\": 118.82200000000012}," +
                          "{\"x\": 527867.18, \"y\": 6995364.458, \"z\": 118.5219999999972}," +
                          "{\"x\": 527843.803, \"y\": 6995376.389, \"z\": 118.35099999999511}," +
                          "{\"x\": 527815.902, \"y\": 6995389.54, \"z\": 117.94599999999627}," +
                          "{\"x\": 527789.731, \"y\": 6995401.53, \"z\": 117.6420000000071}," +
                          "{\"x\": 527762.707, \"y\": 6995413.521, \"z\": 117.2960000000021}," +
                          "{\"x\": 527737.556, \"y\": 6995424.518, \"z\": 117.09799999999814}," +
                          "{\"x\": 527732.52, \"y\": 6995426.729, \"z\": 116.98600000000442}]"

      val reversedPoints = "[{\"x\": 527752.52, \"y\": 6995555.729, \"z\": 118.98600000000442}," +
                            "{\"x\": 527742.972, \"y\": 6995532.398, \"z\": 117.18799999999464},"+
                            "{\"x\": 527732.52, \"y\": 6995426.729, \"z\": 116.98600000000442}]"

      val geom = JSON.parseFull(points).get.asInstanceOf[List[Map[String, Double]]].map(m => Point(m("x"), m("y"), m("z")))

      val reverserGeom = JSON.parseFull(reversedPoints).get.asInstanceOf[List[Map[String, Double]]].map(m => Point(m("x"), m("y"), m("z")))

      val newLink = ProjectAddressLink(NewRoadAddress, 5167571, geom, GeometryUtils.geometryLength(geom),
        State, Motorway, RoadLinkType.NormalRoadLinkType, ConstructionType.InUse, LinkGeomSource.NormalLinkInterface,
        RoadType.PublicRoad, "X", 749, None, None, Map.empty, 77, 35, 0L, 8L, 5L, 0L, 0L, 0.0, GeometryUtils.geometryLength(geom),
        SideCode.Unknown, None, None, Anomaly.None, 0L, LinkStatus.New)

      val newLink2 = ProjectAddressLink(NewRoadAddress, 5167559, reverserGeom, GeometryUtils.geometryLength(reverserGeom),
        State, Motorway, RoadLinkType.NormalRoadLinkType, ConstructionType.InUse, LinkGeomSource.NormalLinkInterface,
        RoadType.PublicRoad, "X", 749, None, None, Map.empty, 77, 35, 0L, 8L, 5L, 0L, 0L, 0.0, GeometryUtils.geometryLength(reverserGeom),
        SideCode.Unknown, None, None, Anomaly.None, 0L, LinkStatus.New)

      when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean], any[Boolean])).thenReturn(geomToLinks.map(toRoadLink))
      projectService.addNewLinksToProject(Seq(newLink), id, 77, 35, 0L, 5L) should be (None)

      val linksAfter = ProjectDAO.fetchByProjectNewRoadPart(77, 35, id)
      linksAfter should have size (linksBefore.size + 1)
      linksAfter.filterNot(la => { linksBefore.exists( lb => {lb.linkId == la.linkId && lb.sideCode.value == la.sideCode.value } )}).size should be (1)

      projectService.addNewLinksToProject(Seq(newLink2), id, 77, 35, 0L, 5L) should be (None)
      val linksAfter2 = ProjectDAO.fetchByProjectNewRoadPart(77, 35, id)
      linksAfter2 should have size (linksBefore.size + 1)
      linksAfter2.head.linkId should be (5167559)
      linksAfter2.head.startAddrMValue should be (0)
      //Validate the new link sideCode
      linksAfter2.head.sideCode should be (TowardsDigitizing)
      //Validate second link sideCode
      linksAfter2.tail.head.sideCode should be (AgainstDigitizing)
    }
  }

  test("Project should not allow adding branching links") {
    runWithRollback {
      sqlu"DELETE FROM ROAD_ADDRESS WHERE ROAD_NUMBER=75 AND ROAD_PART_NUMBER=2".execute
      val id = Sequences.nextViitePrimaryKeySeqValue
      val rap = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("1901-01-01"), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List.empty, None)
      ProjectDAO.createRoadAddressProject(rap)

      // Alternate geometries for these links
      val points5176552 = "[{\"x\":537869.292,\"y\":6997722.466,\"z\":110.39800000000105},"+
        "{\"x\":538290.056,\"y\":6998265.169,\"z\":85.4429999999993}]"
      val points5176512 = "[{\"x\":537152.306,\"y\":6996873.826,\"z\":108.27700000000186}," +
        "{\"x\":538290.056,\"y\":6998265.169,\"z\":85.4429999999993}]"
      val points5176584 = "[{\"x\":538290.056,\"y\":6998265.169,\"z\":85.4429999999993}," +
        "{\"x\":538418.3307786948,\"y\":6998426.422734798,\"z\":88.17597963771014}]"
      val geometries =
        Map(5176584 -> StaticTestData.toGeom(JSON.parseFull(points5176584)),
          5176552 -> StaticTestData.toGeom(JSON.parseFull(points5176552)),
          5176512 -> StaticTestData.toGeom(JSON.parseFull(points5176512)))

      val addProjectAddressLink512 = ProjectAddressLink(NewRoadAddress, 5176512, geometries(5176512), GeometryUtils.geometryLength(geometries(5176512)),
        State, Motorway, RoadLinkType.NormalRoadLinkType, ConstructionType.InUse, LinkGeomSource.NormalLinkInterface,
        RoadType.PublicRoad, "X", 749, None, None, Map.empty, 75, 2, 0L, 8L, 5L, 0L, 0L, 0.0, GeometryUtils.geometryLength(geometries(5176512)),
        SideCode.TowardsDigitizing, None, None, Anomaly.None, 0L, LinkStatus.New)
      val addProjectAddressLink552 = ProjectAddressLink(NewRoadAddress, 5176552, geometries(5176552), GeometryUtils.geometryLength(geometries(5176552)),
        State, Motorway, RoadLinkType.NormalRoadLinkType, ConstructionType.InUse, LinkGeomSource.NormalLinkInterface,
        RoadType.PublicRoad, "X", 749, None, None, Map.empty, 75, 2, 0L, 8L, 5L, 0L, 0L, 0.0, GeometryUtils.geometryLength(geometries(5176552)),
        SideCode.TowardsDigitizing, None, None, Anomaly.None, 0L, LinkStatus.New)
      val addProjectAddressLink584 = ProjectAddressLink(NewRoadAddress, 5176584, geometries(5176584), GeometryUtils.geometryLength(geometries(5176584)),
        State, Motorway, RoadLinkType.NormalRoadLinkType, ConstructionType.InUse, LinkGeomSource.NormalLinkInterface,
        RoadType.PublicRoad, "X", 749, None, None, Map.empty, 75, 2, 0L, 8L, 5L, 0L, 0L, 0.0, GeometryUtils.geometryLength(geometries(5176584)),
        SideCode.TowardsDigitizing, None, None, Anomaly.None, 0L, LinkStatus.New)

      val addresses = Seq(addProjectAddressLink512, addProjectAddressLink552, addProjectAddressLink584)
      when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(addresses.map(_.linkId).toSet, false, false)).thenReturn(addresses.map(toRoadLink))
      projectService.addNewLinksToProject(addresses, id, 75, 2, 0L, 5L, 5L) should be (Some("Valittu tiegeometria sisältää haarautumia ja pitää käsitellä osina. Tallennusta ei voi tehdä."))
      val links=ProjectDAO.getProjectLinks(id)
      links.size should be (0)
    }
  }

  test("Adding new link in beginning and transfer the remaining") {
    runWithRollback{

      def toGeom(json: Option[Any]): List[Point] = {
        json.get.asInstanceOf[List[Map[String, Double]]].map(m => Point(m("x"), m("y"), m("z")))
      }

      val reservedRoadPart1 = ReservedRoadPart(164, 77, 35, 5405, 5405, Discontinuity.EndOfRoad, 8, None, None)
      val rap = RoadAddressProject(0, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.now(), DateTime.now(), "Some additional info", Seq(reservedRoadPart1), None , None)
      val addressesOnPart = RoadAddressDAO.fetchByRoadPart(77, 35, false)
      val l = addressesOnPart.map(address => {
        toProjectLink(rap, LinkStatus.NotHandled)(address)
      })
      when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean], any[Boolean])).thenReturn(l.map(toRoadLink))
      val project = projectService.createRoadLinkProject(rap)

      val linksBefore = ProjectDAO.fetchByProjectNewRoadPart(77, 35, project.id).groupBy(_.linkId).map(_._2.head).toList

      val mappedGeoms = StaticTestData.mappedGeoms(l.map(_.linkId))

      val geomToLinks:List[ProjectLink] = linksBefore.map{l =>
        val geom = mappedGeoms(l.linkId)
        l.copy(geometry = geom,
          geometryLength = GeometryUtils.geometryLength(geom),
          endMValue = GeometryUtils.geometryLength(geom)
        )
      }

      val points = "[{\"x\": 528105.957, \"y\": 6995221.607, \"z\": 120.3530000000028}," +
        "{\"x\": 528104.681, \"y\": 6995222.485, \"z\": 120.35099999999511}," +
        "{\"x\": 528064.931, \"y\": 6995249.45, \"z\": 120.18099999999686}," +
        "{\"x\": 528037.789, \"y\": 6995266.234, \"z\": 120.03100000000268}," +
        "{\"x\": 528008.332, \"y\": 6995285.521, \"z\": 119.8969999999972}," +
        "{\"x\": 527990.814, \"y\": 6995296.039, \"z\": 119.77300000000105}," +
        "{\"x\": 527962.009, \"y\": 6995313.215, \"z\": 119.57099999999627}," +
        "{\"x\": 527926.972, \"y\": 6995333.398, \"z\": 119.18799999999464}," +
        "{\"x\": 527890.962, \"y\": 6995352.332, \"z\": 118.82200000000012}," +
        "{\"x\": 527867.18, \"y\": 6995364.458, \"z\": 118.5219999999972}," +
        "{\"x\": 527843.803, \"y\": 6995376.389, \"z\": 118.35099999999511}," +
        "{\"x\": 527815.902, \"y\": 6995389.54, \"z\": 117.94599999999627}," +
        "{\"x\": 527789.731, \"y\": 6995401.53, \"z\": 117.6420000000071}," +
        "{\"x\": 527762.707, \"y\": 6995413.521, \"z\": 117.2960000000021}," +
        "{\"x\": 527737.556, \"y\": 6995424.518, \"z\": 117.09799999999814}," +
        "{\"x\": 527732.52, \"y\": 6995426.729, \"z\": 116.98600000000442}]"
      val geom = JSON.parseFull(points).get.asInstanceOf[List[Map[String, Double]]].map(m => Point(m("x"), m("y"), m("z")))

      val newLink = ProjectAddressLink(NewRoadAddress, 5167571, geom, GeometryUtils.geometryLength(geom),
        State, Motorway, RoadLinkType.NormalRoadLinkType, ConstructionType.InUse, LinkGeomSource.NormalLinkInterface,
        RoadType.PublicRoad, "X", 749, None, None, Map.empty, 77, 35, 0L, 8L, 5L, 0L, 0L, 0.0, GeometryUtils.geometryLength(geom),
        SideCode.AgainstDigitizing, None, None, Anomaly.None, 0L, LinkStatus.New)

      when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean], any[Boolean])).thenReturn(geomToLinks.map(toRoadLink))
      projectService.addNewLinksToProject(Seq(newLink), project.id, 77, 35, 0L, 5L) should be (None)

      val allLinks = ProjectDAO.getProjectLinks(project.id)
      val newLinks = allLinks.filter(_.status == LinkStatus.New)
      val transferLinks = allLinks.filter(_.status != LinkStatus.New)

      when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(allLinks.map(_.linkId).toSet,false, false)).thenReturn(geomToLinks.map(toRoadLink) ++ Seq(toRoadLink(newLink)))
      projectService.updateProjectLinks(project.id, transferLinks.map(_.linkId).toSet, LinkStatus.Transfer, "Test") should be (None)
      newLinks.head.calibrationPoints._1 should not be (None)
      transferLinks.head.calibrationPoints._1 should be (None)
      allLinks.size should be (newLinks.size + transferLinks.size)
    }
  }

  test("Terminate link and new link in the beginning of road part, transfer to the rest of road part") {
    runWithRollback{
      val reservedRoadPart1 = ReservedRoadPart(3192, 847, 6, 3192, 3192, Discontinuity.EndOfRoad, 12, None, None)
      val rap = RoadAddressProject(0, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.now(), DateTime.now(), "Some additional info", Seq(reservedRoadPart1), None , None)
      val addressesOnPart = RoadAddressDAO.fetchByRoadPart(847, 6, false)
      val l = addressesOnPart.map(address => {
        toProjectLink(rap, LinkStatus.NotHandled)(address)
      })
      when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean], any[Boolean])).thenReturn(l.map(toRoadLink))
      val project = projectService.createRoadLinkProject(rap)

      val linksBefore = ProjectDAO.fetchByProjectNewRoadPart(847, 6, project.id).groupBy(_.linkId).map(_._2.head).toList
      val mappedGeoms2 = StaticTestData.mappedGeoms(l.map(_.linkId))

      val geomToLinks:List[ProjectLink] = linksBefore.map{l =>
        val geom = mappedGeoms2(l.linkId)
        l.copy(geometry = geom,
          geometryLength = GeometryUtils.geometryLength(geom),
          endMValue = GeometryUtils.geometryLength(geom)
        )
      }
      val geom = StaticTestData.mappedGeoms(Seq(3730091L)).head._2

      val newLink = ProjectAddressLink(NewRoadAddress, 3730091L, geom, GeometryUtils.geometryLength(geom),
        State, Motorway, RoadLinkType.NormalRoadLinkType, ConstructionType.InUse, LinkGeomSource.NormalLinkInterface,
        RoadType.PublicRoad, "X", 749, None, None, Map.empty, 847, 6, 0L, 12L, 5L, 0L, 0L, 0.0, GeometryUtils.geometryLength(geom),
        SideCode.Unknown, None, None, Anomaly.None, 0L, LinkStatus.New)

      val linkToTerminate = geomToLinks.sortBy(_.startAddrMValue).head
      when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean], any[Boolean])).thenReturn(geomToLinks.map(toRoadLink))
      projectService.updateProjectLinks(project.id, Set(linkToTerminate.linkId), LinkStatus.Terminated, "Test User") should be (None)

      projectService.addNewLinksToProject(Seq(newLink), project.id, 847, 6, 0L, 12L) should be (None)

      val allLinks = ProjectDAO.getProjectLinks(project.id)
      val newLinks = allLinks.filter(_.status == LinkStatus.New)
      val terminatedLinks = allLinks.filter(_.status == LinkStatus.Terminated)
      val transferLinks = allLinks.filter(al => {al.status != LinkStatus.New && al.status != LinkStatus.Terminated})

      when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(allLinks.map(_.linkId).toSet,false, false)).thenReturn(geomToLinks.map(toRoadLink) ++ Seq(toRoadLink(newLink)))
      projectService.updateProjectLinks(project.id, transferLinks.map(_.linkId).toSet, LinkStatus.Transfer, "Test") should be (None)

      newLinks.head.calibrationPoints._1 should not be (None)
      transferLinks.head.calibrationPoints._1 should be (None)
      terminatedLinks.head.calibrationPoints._1 should be (None)
      allLinks.size should be (newLinks.size + transferLinks.size + terminatedLinks.size)
    }
  }

  test("Numbering change on transfer operation with same road number") {
    runWithRollback {
      val address1 = RoadAddressDAO.fetchByRoadPart(5, 206, false)
      val address2 = RoadAddressDAO.fetchByRoadPart(5, 207, false)
      val reservedRoadPart1 = ReservedRoadPart(address1.head.id, address1.head.roadNumber, address1.head.roadPartNumber, address1.last.endAddrMValue, address1.last.endAddrMValue, address1.head.discontinuity, 8, None, None)
      val reservedRoadPart2 = ReservedRoadPart(address2.head.id, address2.head.roadNumber, address2.head.roadPartNumber, address2.last.endAddrMValue, address2.last.endAddrMValue, address2.head.discontinuity, 8, None, None)
      val rap = RoadAddressProject(0, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.now(), DateTime.now(), "Some additional info", Seq(reservedRoadPart1) ++ Seq(reservedRoadPart2), None , None)

      val links = (address1 ++ address2).map(address => {
        toProjectLink(rap, LinkStatus.NotHandled)(address)
      })

      when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean], any[Boolean])).thenReturn(links.map(toRoadLink))
      val project = projectService.createRoadLinkProject(rap)

      //Unchanged + Transfer
      val transferLinkId = address2.sortBy(_.startAddrMValue).head.linkId
      projectService.updateProjectLinks(project.id, address1.map(_.linkId).toSet, LinkStatus.UnChanged, "TestUser") should be (None)
      projectService.updateProjectLinks(project.id, Set(transferLinkId), LinkStatus.Transfer, "TestUser", 5, 206) should be (None)
      val firstTransferLinks = ProjectDAO.getProjectLinks(project.id)
      firstTransferLinks.filter(_.roadPartNumber == 206).map(_.endAddrMValue).max should be (address1.map(_.endAddrMValue).max +
        address2.find(_.linkId == transferLinkId).map(a => a.endAddrMValue - a.startAddrMValue).get)
      //Transfer the rest
      projectService.updateProjectLinks(project.id, address2.sortBy(_.startAddrMValue).tail.map(_.linkId).toSet, LinkStatus.Transfer, "TestUser", 5, 207) should be (None)
      val secondTransferLinks = ProjectDAO.getProjectLinks(project.id)
      secondTransferLinks.filter(_.roadPartNumber == 207).sortBy(_.startAddrMValue).last.endAddrMValue should be (address2.sortBy(_.startAddrMValue).last.endAddrMValue - address2.sortBy(_.startAddrMValue).head.endAddrMValue)
      val mappedLinks = links.groupBy(_.linkId)
      val mapping = secondTransferLinks.filter(_.roadPartNumber == 207).map(tl => tl -> mappedLinks(tl.linkId)).filterNot(_._2.size > 1)
      mapping.foreach{ case (link, l) =>
        val before = l.head
        before.endAddrMValue - before.startAddrMValue should be (link.endAddrMValue - link.startAddrMValue +- 1)
      }
      secondTransferLinks.groupBy(_.roadPartNumber).mapValues(_.map(_.endAddrMValue).max).values.sum should be
      (address1.map(_.endAddrMValue).max + address2.map(_.endAddrMValue).max)
    }
  }

  test("Numbering change on transfer operation with different road number") {
    runWithRollback {

      val address1 = RoadAddressDAO.fetchByRoadPart(11, 8, false).sortBy(_.startAddrMValue)
      val address2 = RoadAddressDAO.fetchByRoadPart(259, 1, false).sortBy(_.startAddrMValue)
      val reservedRoadPart1 = ReservedRoadPart(address1.head.id, address1.head.roadNumber, address1.head.roadPartNumber, address1.last.endAddrMValue, address1.last.endAddrMValue, address1.head.discontinuity, 8, None, None)
      val reservedRoadPart2 = ReservedRoadPart(address2.head.id, address2.head.roadNumber, address2.head.roadPartNumber, address2.last.endAddrMValue, address1.last.endAddrMValue, address2.head.discontinuity, 8, None, None)
      val rap = RoadAddressProject(0, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.now(), DateTime.now(), "Some additional info", Seq(reservedRoadPart1) ++ Seq(reservedRoadPart2), None , None)

      val links = (address1 ++ address2).map(address => {
        toProjectLink(rap, LinkStatus.NotHandled)(address)
      })

      when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean], any[Boolean])).thenReturn(links.map(toRoadLink))
      val project = projectService.createRoadLinkProject(rap)

      val transferLink = address2.sortBy(_.startAddrMValue).head
      projectService.updateProjectLinks(project.id, address1.map(_.linkId).toSet, LinkStatus.UnChanged, "TestUser") should be (None)
      projectService.updateProjectLinks(project.id, Set(transferLink.linkId), LinkStatus.Transfer, "TestUser", 11, 8) should be (None)

      val updatedLinks = ProjectDAO.getProjectLinks(project.id)
      val linksRoad11 = updatedLinks.filter(_.roadNumber == 11).sortBy(_.startAddrMValue)
      val linksRoad259 = updatedLinks.filter(_.roadNumber == 259).sortBy(_.startAddrMValue)

      linksRoad259.head.calibrationPoints._1 should not be (None)
      linksRoad11.last.calibrationPoints._2 should not be (None)
      linksRoad11.size should be (address1.size + 1)
      linksRoad259.size should be (address2.size - 1)
    }
  }

  ignore("Termination and new in the middle of the road part"){
    runWithRollback {
      val roadPart = RoadAddressDAO.fetchByRoadPart(259, 1, false).sortBy(_.startAddrMValue)
      val reservedRoadPart = ReservedRoadPart(roadPart.head.id, roadPart.head.roadNumber, roadPart.head.roadPartNumber, roadPart.last.endAddrMValue, roadPart.last.endAddrMValue, roadPart.head.discontinuity, 8, None, None)

      val link1 = ProjectAddressLink(-1000, 5502405, Seq(), 299.1713293226726, AdministrativeClass.apply(3),LinkType.apply(1), RoadLinkType.apply(1), ConstructionType.apply(1), LinkGeomSource.NormalLinkInterface, RoadType.PublicRoad, "TEST", 790,
        None, None, Map.empty, 259, 1, 0, 4, 0, 0, 0, 0.0, 299.1713293226726, SideCode.Unknown, None, None, Anomaly.None,0L, LinkStatus.Unknown)
      val link2 = ProjectAddressLink(-1000, 5502487, Seq(), 106.72735157765564, AdministrativeClass.apply(3),LinkType.apply(1), RoadLinkType.apply(1), ConstructionType.apply(1), LinkGeomSource.NormalLinkInterface, RoadType.PublicRoad, "TEST", 790,
        None, None, Map.empty, 259, 1, 0, 4, 0, 0, 0, 0.0, 106.72735157765564, SideCode.Unknown, None, None, Anomaly.None,0L, LinkStatus.Unknown)
      val link3 = ProjectAddressLink(-1000, 5502450, Seq(), 13.920170042652735, AdministrativeClass.apply(3),LinkType.apply(1), RoadLinkType.apply(1), ConstructionType.apply(1), LinkGeomSource.NormalLinkInterface, RoadType.PublicRoad, "TEST", 790,
        None, None, Map.empty, 259, 1, 0, 4, 0, 0, 0, 0.0, 13.920170042652735, SideCode.Unknown, None, None, Anomaly.None,0L, LinkStatus.Unknown)
      val link4 = ProjectAddressLink(-1000, 5502488, Seq(), 14.71305736017408, AdministrativeClass.apply(3),LinkType.apply(1), RoadLinkType.apply(1), ConstructionType.apply(1), LinkGeomSource.NormalLinkInterface, RoadType.PublicRoad, "TEST", 790,
        None, None, Map.empty, 259, 1, 0, 4, 0, 0, 0, 0.0, 14.71305736017408, SideCode.Unknown, None, None, Anomaly.None,0L, LinkStatus.Unknown)
      val link5 = ProjectAddressLink(-1000, 5502446, Seq(), 21.098137837276074, AdministrativeClass.apply(3),LinkType.apply(1), RoadLinkType.apply(1), ConstructionType.apply(1), LinkGeomSource.NormalLinkInterface, RoadType.PublicRoad, "TEST", 790,
        None, None, Map.empty, 259, 1, 0, 4, 0, 0, 0, 0.0, 21.098137837276074, SideCode.Unknown, None, None, Anomaly.None,0L, LinkStatus.Unknown)
      val link6 = ProjectAddressLink(-1000, 5502444, Seq(), 42.51504393698132, AdministrativeClass.apply(3),LinkType.apply(1), RoadLinkType.apply(1), ConstructionType.apply(1), LinkGeomSource.NormalLinkInterface, RoadType.PublicRoad, "TEST", 790,
        None, None, Map.empty, 259, 1, 0, 4, 0, 0, 0, 0.0, 42.51504393698132, SideCode.Unknown, None, None, Anomaly.None,0L, LinkStatus.Unknown)
      val link7 = ProjectAddressLink(-1000, 5502441, Seq(), 75.00455028294714, AdministrativeClass.apply(3),LinkType.apply(1), RoadLinkType.apply(1), ConstructionType.apply(1), LinkGeomSource.NormalLinkInterface, RoadType.PublicRoad, "TEST", 790,
        None, None, Map.empty, 259, 1, 0, 4, 0, 0, 0, 0.0, 75.00455028294714, SideCode.Unknown, None, None, Anomaly.None,0L, LinkStatus.Unknown)
      val mappedGeoms2 = StaticTestData.mappedGeoms(List(5502405,5502487,5502450,5502488,5502446,5502444,5502441))

      val newLinksWithGeom = List(link1,link2,link3,link4,link5,link6,link7).map{l =>
        val geom = mappedGeoms2(l.linkId)
        l.copy(geometry = geom,
          length = GeometryUtils.geometryLength(geom),
          endMValue = GeometryUtils.geometryLength(geom)
        )
      }

      val rap = RoadAddressProject(0, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("2999-01-01"), "TestUser", DateTime.parse("2999-01-01"), DateTime.parse("2999-01-01"), "Some additional info", Seq(reservedRoadPart), None , None)
      val links = roadPart.map(road => {
        toProjectLink(rap, LinkStatus.NotHandled)(road)
      })

      when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean], any[Boolean])).thenReturn(links.map(toRoadLink))
      val project = projectService.createRoadLinkProject(rap)

      val unchangedLinks = roadPart.filter(rp => List(5502468, 5502479, 5502483, 5502490, 5502505, 5502500, 5502745, 5502741).contains(rp.linkId)).sortBy(_.startAddrMValue)
      val linksToTerminate = roadPart.filter(rp => List(5502402,5502394, 5502436).contains(rp.linkId)).sortBy(_.startAddrMValue)

      projectService.updateProjectLinks(project.id, unchangedLinks.map(_.linkId).toSet, LinkStatus.UnChanged, "TestUser") should be (None)
      projectService.updateProjectLinks(project.id, linksToTerminate.map(_.linkId).toSet, LinkStatus.Terminated, "TestUser") should be (None)
      projectService.addNewLinksToProject(newLinksWithGeom, project.id, 259, 1, 0, 5L) should be (None)

      val linksAfter = ProjectDAO.getProjectLinks(project.id).sortBy(_.startAddrMValue)
      val newLinksAfter = linksAfter.filter(la => newLinksWithGeom.exists(_.linkId == la.linkId) && la.status != LinkStatus.NotHandled).sortBy(_.startAddrMValue)
      linksAfter.size should be (roadPart.size + newLinksWithGeom.size)
      newLinksAfter.head.startAddrMValue should be (unchangedLinks.last.endAddrMValue)
      }
  }
}
