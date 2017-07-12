package fi.liikennevirasto.viite

import java.net.ConnectException
import java.util.Properties

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
    ProjectLink(id=NewRoadAddress, roadAddress.roadNumber, roadAddress.roadPartNumber, roadAddress.track,
      roadAddress.discontinuity, roadAddress.startAddrMValue, roadAddress.endAddrMValue, roadAddress.startDate,
      roadAddress.endDate, modifiedBy=Option(project.createdBy), 0L, roadAddress.linkId, roadAddress.startMValue, roadAddress.endMValue,
      roadAddress.sideCode, roadAddress.calibrationPoints, floating=false, roadAddress.geom, project.id, LinkStatus.NotHandled, RoadType.PublicRoad)
  }

  test ("create road link project without road parts") {
    runWithRollback{
      val roadAddressProject = RoadAddressProject(0, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List.empty[ReservedRoadPart], None)
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
      val roadAddressProject = RoadAddressProject(0, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List.empty[ReservedRoadPart], None)
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
      val addresses:List[ReservedRoadPart]= List(ReservedRoadPart(5:Long, 203:Long, 203:Long, 5:Double, Discontinuity.apply("jatkuva"), 8:Long, None:Option[DateTime], None:Option[DateTime]))
      val roadAddressProject = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", addresses, None)
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
      val addresses = List(ReservedRoadPart(5:Long, 203:Long, 203:Long, 5:Double, Discontinuity.apply("jatkuva"), 8:Long, None:Option[DateTime], None:Option[DateTime]))
      val roadAddressProject = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List(), None)
      val saved = projectService.createRoadLinkProject(roadAddressProject)._1
      val changed = saved.copy(reservedParts = addresses)
      projectService.saveRoadLinkProject(changed)
      val countAfterInsertProjects = projectService.getRoadAddressAllProjects()
      count = countCurrentProjects.size + 1
      countAfterInsertProjects.size should be (count)
    }
    runWithRollback { projectService.getRoadAddressAllProjects() } should have size (count - 1)
  }

  ignore("Fetch project links") { // Needs more of mocking because of Futures + transactions disagreeing
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
      val addresses:List[ReservedRoadPart]= List(ReservedRoadPart(0:Long, 5:Long, 205:Long, 5:Double, Discontinuity.apply("jatkuva"), 8:Long, None:Option[DateTime], None:Option[DateTime]))
      val roadAddressProject = RoadAddressProject(0, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", addresses, None)
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

  test("Validate road part dates with project date - startDate") {
    val projDate = DateTime.parse("2015-01-01")
    val addresses = List(ReservedRoadPart(5:Long, 5:Long, 205:Long, 5:Double, Discontinuity.apply("jatkuva"), 8:Long, Option(DateTime.parse("2017-01-01")):Option[DateTime], None:Option[DateTime]))
    val errorMsg = projectService.projDateValidation(addresses, projDate)
    errorMsg should not be (None)
  }

  test("Validate road part dates with project date - startDate valid") {
    val projDate = DateTime.parse("2015-01-01")
    val addresses = List(ReservedRoadPart(5:Long, 5:Long, 205:Long, 5:Double, Discontinuity.apply("jatkuva"), 8:Long, Option(DateTime.parse("2010-01-01")):Option[DateTime], None:Option[DateTime]))
    val errorMsg = projectService.projDateValidation(addresses, projDate)
    errorMsg should be (None)
  }

  test("Validate road part dates with project date - startDate and endDate") {
    val projDate = DateTime.parse("2015-01-01")
    val addresses = List(ReservedRoadPart(5:Long, 5:Long, 205:Long, 5:Double, Discontinuity.apply("jatkuva"), 8:Long, Option(DateTime.parse("2010-01-01")):Option[DateTime], Option(DateTime.parse("2017-01-01")):Option[DateTime]))
    val errorMsg = projectService.projDateValidation(addresses, projDate)
    errorMsg should not be (None)
  }

  test("Validate road part dates with project date - startDate and endDate valid") {
    val projDate = DateTime.parse("2018-01-01")
    val addresses = List(ReservedRoadPart(5:Long, 5:Long, 205:Long, 5:Double, Discontinuity.apply("jatkuva"), 8:Long, Option(DateTime.parse("2010-01-01")):Option[DateTime], Option(DateTime.parse("2017-01-01")):Option[DateTime]))
    val errorMsg = projectService.projDateValidation(addresses, projDate)
    errorMsg should be (None)
  }

  test("Calculate delta for project") {
    var count = 0
    val roadlink = RoadLink(5170939L,Seq(Point(535605.272,6982204.22,85.90899999999965))
      ,540.3960283713503,State,99,TrafficDirection.AgainstDigitizing,UnknownLinkType,Some("25.06.2015 03:00:00"), Some("vvh_modified"),Map("MUNICIPALITYCODE" -> BigInt.apply(749)),
      InUse,NormalLinkInterface)
    when(mockRoadLinkService.getRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean])).thenReturn(Seq(roadlink))
    runWithRollback {
      val countCurrentProjects = projectService.getRoadAddressAllProjects()
      val addresses = List(ReservedRoadPart(0L, 5L, 205L, 5.0, Discontinuity.apply("jatkuva"), 8:Long, None:Option[DateTime], None:Option[DateTime]))
      val roadAddressProject = RoadAddressProject(0, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List(), None)
      val saved = projectService.createRoadLinkProject(roadAddressProject)._1
      val changed = saved.copy(reservedParts = addresses)
      projectService.saveRoadLinkProject(changed)
      val countAfterInsertProjects = projectService.getRoadAddressAllProjects()
      count = countCurrentProjects.size + 1
      countAfterInsertProjects.size should be (count)
      sqlu"""UPDATE Project_link set status = 1""".execute
      val terminations = ProjectDeltaCalculator.delta(saved.id).terminations
      terminations should have size(66)
      val sections = ProjectDeltaCalculator.partition(terminations)
      sections should have size (2)
      sections.exists(_.track == Track.LeftSide) should be (true)
      sections.exists(_.track == Track.RightSide) should be (true)
      sections.groupBy(_.endMAddr).keySet.size should be (1)
    }
    runWithRollback { projectService.getRoadAddressAllProjects() } should have size (count - 1)
  }

  test("Calculate delta for project with discontinuity") {
    var count = 0
    val roadlink = RoadLink(5170939L,Seq(Point(535605.272,6982204.22,85.90899999999965))
      ,540.3960283713503,State,99,TrafficDirection.AgainstDigitizing,UnknownLinkType,Some("25.06.2015 03:00:00"), Some("vvh_modified"),Map("MUNICIPALITYCODE" -> BigInt.apply(749)),
      InUse,NormalLinkInterface)
    when(mockRoadLinkService.getRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean])).thenReturn(Seq(roadlink))
    runWithRollback {
      val countCurrentProjects = projectService.getRoadAddressAllProjects()
      val addresses = List(ReservedRoadPart(0L, 5L, 205L, 5.0, Discontinuity.apply("jatkuva"), 8:Long, None:Option[DateTime], None:Option[DateTime]))
      val roadAddressProject = RoadAddressProject(0, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List(), None)
      val saved = projectService.createRoadLinkProject(roadAddressProject)._1
      val changed = saved.copy(reservedParts = addresses)
      projectService.saveRoadLinkProject(changed)
      val countAfterInsertProjects = projectService.getRoadAddressAllProjects()
      count = countCurrentProjects.size + 1
      countAfterInsertProjects.size should be (count)
      sqlu"""UPDATE Project_link set status = 1""".execute
      val terminations = ProjectDeltaCalculator.delta(saved.id).terminations
      terminations should have size(66)
      val modTerminations = terminations.map(t =>
        if (t.endAddrMValue == 4529)
          t.copy(discontinuity = Discontinuity.MinorDiscontinuity)
        else
          t
      )
      val sections = ProjectDeltaCalculator.partition(modTerminations)
      sections should have size (4)
      sections.exists(_.track == Track.LeftSide) should be (true)
      sections.exists(_.track == Track.RightSide) should be (true)
      sections.groupBy(_.endMAddr).keySet should have size (2)
    }
    runWithRollback { projectService.getRoadAddressAllProjects() } should have size (count - 1)
  }

  test("update project link status and check project status") {
    var count = 0
    val roadlink = RoadLink(5170939L,Seq(Point(535605.272,6982204.22,85.90899999999965))
      ,540.3960283713503,State,99,TrafficDirection.AgainstDigitizing,UnknownLinkType,Some("25.06.2015 03:00:00"), Some("vvh_modified"),Map("MUNICIPALITYCODE" -> BigInt.apply(749)),
      InUse,NormalLinkInterface)
    when(mockRoadLinkService.getRoadLinksByLinkIdsFromVVH(Set(5170939L))).thenReturn(Seq(roadlink))
    runWithRollback {
      val countCurrentProjects = projectService.getRoadAddressAllProjects()
      val id = 0
      val addresses = List(ReservedRoadPart(5:Long, 5:Long, 205:Long, 5:Double, Discontinuity.apply("jatkuva"), 8:Long, None:Option[DateTime], None:Option[DateTime]))
      val roadAddressProject = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", addresses, None)
      val saved = projectService.createRoadLinkProject(roadAddressProject)._1
      val countAfterInsertProjects = projectService.getRoadAddressAllProjects()
      count = countCurrentProjects.size + 1
      countAfterInsertProjects.size should be (count)
      projectService.projectLinkPublishable(saved.id) should be (false)
      val linkIds = ProjectDAO.getProjectLinks(saved.id).map(_.linkId).toSet
      projectService.updateProjectLinkStatus(saved.id, linkIds, LinkStatus.Terminated, "-")
      projectService.projectLinkPublishable(saved.id) should be (true)
    }
    runWithRollback { projectService.getRoadAddressAllProjects() } should have size (count - 1)
  }

  test("fetch project data and send it to TR") {
    assume(testConnection)
    runWithRollback{
      val project = RoadAddressProject(1,ProjectState.Incomplete,"testiprojekti","Test",DateTime.now(),"Test",DateTime.now(),DateTime.now(),"info",List(ReservedRoadPart(5:Long, 203:Long, 203:Long, 5:Double, Discontinuity.apply("jatkuva"), 8:Long, None:Option[DateTime], None:Option[DateTime])), None)
      ProjectDAO.createRoadAddressProject(project)
      sqlu""" insert into road_address_changes(project_id,change_type,new_road_number,new_road_part_number,new_track_code,new_start_addr_m,new_end_addr_m,new_discontinuity,new_road_type,new_ely) Values(1,1,6,1,1,0,10.5,1,1,8) """.execute
      //Assuming that there is data to show
      val responses = projectService.getRoadAddressChangesAndSendToTR(Set(1))
      responses.projectId should be( 1)
    }
  }

  test ("update ProjectStatus when TR saved")
  {
    val sent2TRState=ProjectState.apply(2) //notfinnished
  val savedState=ProjectState.apply(5)
    val projectId=0
    val addresses = List(ReservedRoadPart(5:Long, 203:Long, 203:Long, 5:Double, Discontinuity.apply("jatkuva"), 8:Long, None:Option[DateTime], None:Option[DateTime]))
    val roadAddressProject = RoadAddressProject(projectId, ProjectState.apply(2), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List(), None)
    runWithRollback{
      val saved = projectService.createRoadLinkProject(roadAddressProject)._1
      val stateaftercheck= projectService.updateProjectStatusIfNeeded(sent2TRState,savedState,"",saved.id)
      stateaftercheck.description should be (ProjectState.Saved2TR.description)
    }

  }

  test ("Update to TRerror state")
  {
    val sent2TRState=ProjectState.apply(2) //notfinnished
  val savedState=ProjectState.apply(3)
    val projectId=0
    val addresses = List(ReservedRoadPart(5:Long, 203:Long, 203:Long, 5:Double, Discontinuity.apply("jatkuva"), 8:Long, None:Option[DateTime], None:Option[DateTime]))
    val roadAddressProject = RoadAddressProject(projectId, ProjectState.apply(2), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", List(), None)
    runWithRollback{
      val saved = projectService.createRoadLinkProject(roadAddressProject)._1
      val stateaftercheck= projectService.updateProjectStatusIfNeeded(sent2TRState,savedState,"failed",saved.id)
      stateaftercheck.description should be (ProjectState.ErroredInTR.description)
    }

  }

  test("process roadChange data and expire the roadLinks"){
    //First Create Mock Project, RoadLinks and

    runWithRollback{
      var projectId = 0L
      val roadNumber = 1943845
      val roadPartNumber = 1
      val linkId = 12345L
      //Creation of Test road
      val id = RoadAddressDAO.getNextRoadAddressId
      val ra = Seq(RoadAddress(id, roadNumber, roadPartNumber, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), None, Option("tester"), 0, linkId, 0.0, 9.8, SideCode.TowardsDigitizing,0 , (None, None), false,
        Seq(Point(0.0, 0.0), Point(0.0, 9.8)), LinkGeomSource.NormalLinkInterface))
      RoadAddressDAO.create(ra)
      val roadsBeforeChanges = RoadAddressDAO.fetchByLinkId(Set(linkId)).head

      when(mockRoadLinkService.getRoadLinksByLinkIdsFromVVH(Set(linkId))).thenReturn(Seq(RoadLink(linkId, ra.head.geom, 9.8, State, 1, TrafficDirection.BothDirections,
        Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(167)))))
      //Creation of test project with test links
      val project = RoadAddressProject(projectId,ProjectState.Incomplete,"testiprojekti","Test",DateTime.now(),"Test",
        DateTime.parse("1990-01-01"),DateTime.now(),"info",
        List(ReservedRoadPart(0:Long, roadNumber:Long, roadPartNumber:Long, 5:Double, Discontinuity.apply("jatkuva"),
          8:Long, None:Option[DateTime], None:Option[DateTime])), None)
      val (proj, projectLink, _, errmsg) = projectService.createRoadLinkProject(project)
      projectLink.isEmpty should be (false)
      errmsg should be ("ok")
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
      roadsAfterChanges.endDate.nonEmpty should be (true)
      roadsAfterChanges.endDate.get.toString("yyyy-MM-dd") should be("1990-01-01")
    }
  }

  test("verify existence of roadAddressNumbersAndSEParts") {
    val roadNumber = 1943845
    val roadStartPart = 1
    val roadEndPart = 2
    runWithRollback{
      val id1 = RoadAddressDAO.getNextRoadAddressId
      val id2 = RoadAddressDAO.getNextRoadAddressId
      val ra = Seq(RoadAddress(id1, roadNumber, roadStartPart, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), None, Option("tester"), 0, 12345L, 0.0, 9.8, SideCode.TowardsDigitizing,0 , (None, None), false,
        Seq(Point(0.0, 0.0), Point(0.0, 9.8)), LinkGeomSource.NormalLinkInterface))
      val rb = Seq(RoadAddress(id2, roadNumber, roadEndPart, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), None, Option("tester"), 0, 12345L, 0.0, 9.8, SideCode.TowardsDigitizing,0 , (None, None), false,
        Seq(Point(0.0, 0.0), Point(0.0, 9.8)), LinkGeomSource.NormalLinkInterface))
      val shouldNotExist = projectService.checkRoadAddressNumberAndSEParts(roadNumber, roadStartPart, roadEndPart)
      shouldNotExist.get should be("Tienumeroa ei ole olemassa, tarkista tiedot")
      RoadAddressDAO.create(ra)
      val roadNumberShouldNotExist = projectService.checkRoadAddressNumberAndSEParts(roadNumber, roadStartPart+1, roadEndPart)
      roadNumberShouldNotExist.get should be("Tiellä ei ole olemassa valittua alkuosaa, tarkista tiedot")
      val endingPartShouldNotExist = projectService.checkRoadAddressNumberAndSEParts(roadNumber, roadStartPart, roadEndPart)
      endingPartShouldNotExist.get should be("Tiellä ei ole olemassa valittua loppuosaa, tarkista tiedot")
      RoadAddressDAO.create(rb)
      val allIsOk = projectService.checkRoadAddressNumberAndSEParts(roadNumber, roadStartPart, roadEndPart)
      allIsOk should be(None)
    }
  }

  test("check reservability of a road") {
    val roadNumber = 1943845
    val roadStartPart = 1
    val roadEndPart = 2
    val roadlink = RoadLink(12345L,Seq(Point(535605.272,6982204.22,85.90899999999965))
      ,540.3960283713503,State,99,TrafficDirection.AgainstDigitizing,UnknownLinkType,Some("25.06.2015 03:00:00"), Some("vvh_modified"),Map("MUNICIPALITYCODE" -> BigInt.apply(749)),
      InUse,NormalLinkInterface)
    when(mockRoadLinkService.getRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean])).thenReturn(Seq(roadlink))
    runWithRollback {
      val id1 = RoadAddressDAO.getNextRoadAddressId
      val ra = Seq(RoadAddress(id1, roadNumber, roadStartPart, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), None, Option("tester"), 0, 12345L, 0.0, 9.8, SideCode.TowardsDigitizing,0 , (None, None), false,
        Seq(Point(0.0, 0.0), Point(0.0, 9.8)), LinkGeomSource.NormalLinkInterface))
      val reservation = projectService.checkReservability(roadNumber, roadStartPart, roadEndPart)
      reservation.right.get.size should be (0)
      RoadAddressDAO.create(ra)
      val id2 = RoadAddressDAO.getNextRoadAddressId
      val rb = Seq(RoadAddress(id2, roadNumber, roadEndPart, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), None, Option("tester"), 0, 12345L, 0.0, 9.8, SideCode.TowardsDigitizing,0 , (None, None), false,
        Seq(Point(0.0, 0.0), Point(0.0, 9.8)), LinkGeomSource.NormalLinkInterface))
      RoadAddressDAO.create(rb)
      val reservationAfterB = projectService.checkReservability(roadNumber, roadStartPart, roadEndPart)
      reservationAfterB.right.get.size should be (2)
      reservationAfterB.right.get.map(_.roadNumber).distinct.size should be(1)
      reservationAfterB.right.get.map(_.roadNumber).distinct.head should be (roadNumber)
    }
  }

  test("get the road address project") {
    var count = 0
    runWithRollback {
      val countCurrentProjects = projectService.getRoadAddressAllProjects()
      val id = 0
      val addresses:List[ReservedRoadPart]= List(ReservedRoadPart(5:Long, 203:Long, 203:Long, 5:Double, Discontinuity.apply("jatkuva"), 8:Long, None:Option[DateTime], None:Option[DateTime]))
      val roadAddressProject = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", addresses, None)
      projectService.createRoadLinkProject(roadAddressProject)
      val countAfterInsertProjects = projectService.getRoadAddressAllProjects()
      count = countCurrentProjects.size + 1
      countAfterInsertProjects.size should be (count)
      val project = projectService.getRoadAddressSingleProject(id)
      project.size should be(1)
      project.head.name should be ("TestProject")
    }
    runWithRollback {
      projectService.getRoadAddressAllProjects().size should be (count-1)
    }
  }

  test("get the project with it's reserved road parts") {
    var projectId = 0L
    val roadNumber = 1943845
    val roadPartNumber = 1
    val linkId = 12345L

    runWithRollback{

      //Creation of Test road
      val id = RoadAddressDAO.getNextRoadAddressId
      val ra = Seq(RoadAddress(id, roadNumber, roadPartNumber, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), None, Option("tester"), 0, linkId, 0.0, 9.8, SideCode.TowardsDigitizing,0 , (None, None), false,
        Seq(Point(0.0, 0.0), Point(0.0, 9.8)), LinkGeomSource.NormalLinkInterface))
      when(mockRoadLinkService.getRoadLinksByLinkIdsFromVVH(Set(linkId))).thenReturn(Seq(RoadLink(linkId, ra.head.geom, 9.8, State, 1, TrafficDirection.BothDirections,
        Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(167)))))
      RoadAddressDAO.create(ra)

      //Creation of test project with test links
      val project = RoadAddressProject(projectId,ProjectState.Incomplete,"testiprojekti","Test",DateTime.now(),"Test",
        DateTime.now(),DateTime.now(),"info",
        List(ReservedRoadPart(0:Long, roadNumber:Long, roadPartNumber:Long, 5:Double, Discontinuity.apply("jatkuva"),
          8:Long, None:Option[DateTime], None:Option[DateTime])), None)
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
    val roadLink = RoadLink(5170939L,Seq(Point(535605.272,6982204.22,85.90899999999965))
      ,540.3960283713503,State,99,TrafficDirection.AgainstDigitizing,UnknownLinkType,Some("25.06.2015 03:00:00"), Some("vvh_modified"),Map("MUNICIPALITYCODE" -> BigInt.apply(749)),
      InUse,NormalLinkInterface)
    when(mockRoadLinkService.getRoadLinksByLinkIdsFromVVH(any[Set[Long]], any[Boolean])).thenReturn(Seq(roadLink))
    runWithRollback {
      val countCurrentProjects = projectService.getRoadAddressAllProjects()
      val id = 0
      val addresses = List(ReservedRoadPart(5:Long, 5:Long, 205:Long, 5:Double, Discontinuity.apply("jatkuva"), 8:Long, None:Option[DateTime], None:Option[DateTime]),
        ReservedRoadPart(5:Long, 5:Long, 206:Long, 5:Double, Discontinuity.apply("jatkuva"), 8:Long, None:Option[DateTime], None:Option[DateTime]))
      val roadAddressProject = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.now(), "TestUser", DateTime.parse("1901-01-01"), DateTime.now(), "Some additional info", addresses, None)
      val (saved, _, formLines, errMsg) = projectService.createRoadLinkProject(roadAddressProject)
      errMsg should be ("ok")
      formLines should have size (2)
      val countAfterInsertProjects = projectService.getRoadAddressAllProjects()
      count = countCurrentProjects.size + 1
      countAfterInsertProjects.size should be (count)
      projectService.projectLinkPublishable(saved.id) should be (false)
      val projectLinks = ProjectDAO.getProjectLinks(saved.id).partition(_.roadPartNumber==205)
      val linkIds205 = projectLinks._1.map(_.linkId).toSet
      val linkIds206 = projectLinks._2.map(_.linkId).toSet

      projectService.updateProjectLinkStatus(saved.id, linkIds205, LinkStatus.Terminated, "-")
      projectService.projectLinkPublishable(saved.id) should be (false)

      projectService.getChangeProject(saved.id).map(_.changeInfoSeq).getOrElse(Seq()) should have size (0)

      projectService.updateProjectLinkStatus(saved.id, linkIds206, LinkStatus.Terminated, "-")
      projectService.projectLinkPublishable(saved.id) should be (true)

      val changeProjectOpt = projectService.getChangeProject(saved.id)
      changeProjectOpt.map(_.changeInfoSeq).getOrElse(Seq()) should have size (5)

      val change = changeProjectOpt.get

      change.changeDate should be (roadAddressProject.startDate.toString("YYYY-MM-DD"))
      change.ely should be (8)
      change.user should be ("TestUser")
      change.name should be ("TestProject")
      change.changeInfoSeq.foreach(rac => {
        val s = rac.source
        val t = rac.target
        val (sTie, sAosa, sAjr, sAet, sLet) = (s.roadNumber, s.startRoadPartNumber, s.trackCode, s.startAddressM, s.endAddressM)
        val (tTie, tAosa, tAjr, tAet, tLet) = (t.roadNumber, t.startRoadPartNumber, t.trackCode, t.startAddressM, t.endAddressM)
        sTie should be (tTie)
        sAosa should be (tAosa)
        sAjr should be (tAjr)
        sAet should be (tAet)
        sLet should be (tLet)
      })

      change.changeInfoSeq.foreach(_.changeType should be (AddressChangeType.Termination))
      change.changeInfoSeq.foreach(_.discontinuity should be (Discontinuity.Continuous))
      // TODO: When road types are properly generated
      change.changeInfoSeq.foreach(_.roadType should be (RoadType.UnknownOwnerRoad))
    }
    runWithRollback { projectService.getRoadAddressAllProjects() } should have size (count - 1)
  }
  test("add nonexisting roadlink to project"){
    runWithRollback {
      val idr = RoadAddressDAO.getNextRoadAddressId
      val id = Sequences.nextViitePrimaryKeySeqValue
      val rap = RoadAddressProject(id, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("2700-01-01"), "TestUser", DateTime.parse("2700-01-01"), DateTime.now(), "Some additional info", List.empty[ReservedRoadPart], None)
      val projectLink= toProjectLink(rap)(RoadAddress(idr, 1943845, 1, Track.Combined, Discontinuous, 0L, 10L, Some(DateTime.parse("1901-01-01")), Some(DateTime.parse("1902-01-01")), Option("tester"), 0, 12345L, 0.0, 9.8, SideCode.TowardsDigitizing, 0, (None, None), false,
        Seq(Point(0.0, 0.0), Point(0.0, 9.8)), LinkGeomSource.NormalLinkInterface))
      ProjectDAO.createRoadAddressProject(rap)
      projectService.addNewLinkToProject(projectLink, id)
     val links= ProjectDAO.getProjectLinks(id)
      links.size should be (1)
    }
  }
}
