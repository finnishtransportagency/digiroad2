package fi.liikennevirasto.viite

import java.net.ConnectException
import java.util.Properties

import fi.liikennevirasto.digiroad2
import fi.liikennevirasto.digiroad2.asset.ConstructionType.InUse
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
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
import org.mockito.Mockito.when
import org.mockito.Matchers._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

class ProjectServiceSpec  extends FunSuite with Matchers {
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
      val boundingRectangle = roadLinkService.fetchViiteVVHRoadlinks(Set(startingLinkId)).map { vrl =>
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
      count = countCurrentProjects.size + 1
      countAfterInsertProjects.size should be(count)
      sqlu"""UPDATE Project_link set status = 1""".execute
      val terminations = ProjectDeltaCalculator.delta(saved.id).terminations
      terminations should have size (66)
      sqlu"""UPDATE Project_link set status = 2""".execute
      val newCreations = ProjectDeltaCalculator.delta(saved.id).newRoads
      newCreations should have size (66)
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
      count = countCurrentProjects.size + 1
      countAfterInsertProjects.size should be(count)
      sqlu"""UPDATE Project_link set status = 1""".execute
      val terminations = ProjectDeltaCalculator.delta(saved.id).terminations
      terminations should have size (66)
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
    val roadlink = RoadLink(5170939L, Seq(Point(535605.272, 6982204.22, 85.90899999999965))
      , 540.3960283713503, State, 99, TrafficDirection.AgainstDigitizing, UnknownLinkType, Some("25.06.2015 03:00:00"), Some("vvh_modified"), Map("MUNICIPALITYCODE" -> BigInt.apply(749)),
      InUse, NormalLinkInterface)
    when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(Set(5170939L))).thenReturn(Seq(roadlink))
    runWithRollback {
      val countCurrentProjects = projectService.getRoadAddressAllProjects()
      val id = 0
      val addresses = List(ReservedRoadPart(5: Long, 5: Long, 205: Long, 5: Double, Discontinuity.apply("jatkuva"), 8: Long, None: Option[DateTime], None: Option[DateTime]))
      val roadAddressProject = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", addresses, None)
      val saved = projectService.createRoadLinkProject(roadAddressProject)._1
      val countAfterInsertProjects = projectService.getRoadAddressAllProjects()
      count = countCurrentProjects.size + 1
      countAfterInsertProjects.size should be(count)
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

  test("process roadChange data and expire the roadLinks") {
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

      val roadsAfterChanges = RoadAddressDAO.fetchByLinkId(Set(linkId)).head
      roadsBeforeChanges.linkId should be(roadsAfterChanges.linkId)
      roadsBeforeChanges.roadNumber should be(roadsAfterChanges.roadNumber)
      roadsBeforeChanges.roadPartNumber should be(roadsAfterChanges.roadPartNumber)
      roadsAfterChanges.endDate.nonEmpty should be(true)
      roadsAfterChanges.endDate.get.toString("yyyy-MM-dd") should be("1990-01-01")
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
      val countCurrentProjects = projectService.getRoadAddressAllProjects()
      val id = 0
      val addresses: List[ReservedRoadPart] = List(ReservedRoadPart(5: Long, 203: Long, 203: Long, 5: Double, Discontinuity.apply("jatkuva"), 8: Long, None: Option[DateTime], None: Option[DateTime]))
      val roadAddressProject = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", addresses, None)
      projectService.createRoadLinkProject(roadAddressProject)
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
      val id = 0
      val addresses: List[ReservedRoadPart] = List(ReservedRoadPart(5: Long, 203: Long, 203: Long, 5: Double, Discontinuity.apply("jatkuva"), 8: Long, None: Option[DateTime], None: Option[DateTime]))
      val roadAddressProject = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", addresses, None)
      projectService.createRoadLinkProject(roadAddressProject)
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
      when(mockRoadLinkService.getRoadLinksByLinkIdsFromVVH(Set(linkId))).thenReturn(Seq(RoadLink(linkId, ra.head.geometry, 9.8, State, 1, TrafficDirection.BothDirections,
        Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(167)))))
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
    when(mockRoadLinkService.getViiteRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean],any[Boolean])).thenReturn(Seq(roadLink))
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
      val projectLinks = ProjectDAO.getProjectLinks(saved.id).partition(_.roadPartNumber == 205)
      val linkIds205 = projectLinks._1.map(_.linkId).toSet
      val linkIds206 = projectLinks._2.map(_.linkId).toSet

      projectService.updateProjectLinkStatus(saved.id, linkIds205, LinkStatus.Terminated, "-")
      projectService.projectLinkPublishable(saved.id) should be(false)

      projectService.getChangeProject(saved.id).map(_.changeInfoSeq).getOrElse(Seq()) should have size (0)

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
      change.changeInfoSeq.foreach(_.roadType should be(RoadType.UnknownOwnerRoad))
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

      projectService.addNewLinksToProject(Seq(p), id, projectLink.roadNumber, projectLink.roadPartNumber, projectLink.track.value.toLong, projectLink.discontinuity.value.toLong)
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

      projectService.addNewLinksToProject(Seq(p1), id, projectLink1.roadNumber, projectLink1.roadPartNumber, projectLink1.track.value.toLong, projectLink1.discontinuity.value.toLong)
      val links= ProjectDAO.getProjectLinks(id)
      links.size should be (1)


      projectService.addNewLinksToProject(Seq(p2), id, projectLink2.roadNumber, projectLink2.roadPartNumber, projectLink2.track.value.toLong, projectLink2.discontinuity.value.toLong)
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
        projectLink.roadPartNumber, projectLink.track.value.toLong, projectLink.discontinuity.value.toLong).getOrElse("")
      val links = ProjectDAO.getProjectLinks(id)
      links.size should be(0)
      message1project1 should be("TIE 5 OSA 203 on jo olemassa projektin alkupäivänä 03.03.1972, tarkista tiedot") //check that it is reserved in roadaddress table

      val message1project2 = projectService.addNewLinksToProject(Seq(p), id + 1, projectLink2.roadNumber,
        projectLink2.roadPartNumber, projectLink2.track.value.toLong, projectLink2.discontinuity.value.toLong)
      val links2 = ProjectDAO.getProjectLinks(id + 1)
      links2.size should be(1)
      message1project2 should be(None)

      val message2project1 = projectService.addNewLinksToProject(Seq(p), id, projectLink3.roadNumber,
        projectLink3.roadPartNumber, projectLink3.track.value.toLong, projectLink3.discontinuity.value.toLong).getOrElse("")
      val links3 = ProjectDAO.getProjectLinks(id)
      links3.size should be(0)
      message2project1 should be("TIE 5 OSA 999 on jo varattuna projektissa TestProject, tarkista tiedot")
    }
  }

  test("flipping success message") {
    runWithRollback {
      val id = Sequences.nextViitePrimaryKeySeqValue
      val rap = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("1901-01-01"),
        "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List.empty, None)
      ProjectDAO.createRoadAddressProject(rap)
      val addresses = RoadAddressDAO.fetchByRoadPart(5, 203).map(toProjectLink(rap))
      ProjectDAO.create(addresses)
      val links=ProjectDAO.getProjectLinks(id)
      val errorMessage=  projectService.changeDirection(id, 5, 203).getOrElse("")
      errorMessage should be("")
    }
  }

  test("Project link direction change") {
    runWithRollback {
      val id = Sequences.nextViitePrimaryKeySeqValue
      val rap = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("1901-01-01"), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List.empty, None)
      ProjectDAO.createRoadAddressProject(rap)
      val addresses = RoadAddressDAO.fetchByRoadPart(5, 203).map(toProjectLink(rap))
      ProjectDAO.create(addresses)
      val links=ProjectDAO.getProjectLinks(id)
      projectService.changeDirection(id, 5, 203)
      val changedLinks = ProjectDAO.getProjectLinksById(links.map{l => l.id})
      links.head.sideCode should not be(changedLinks.head.sideCode)
      links.head.endMValue should be(changedLinks.last.endMValue)
      links.last.endMValue should be(changedLinks.head.endMValue)
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
}
