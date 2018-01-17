package fi.liikennevirasto.viite.process

import fi.liikennevirasto.digiroad2.asset.LinkGeomSource
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset.SideCode.TowardsDigitizing
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.{Queries, Sequences}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import fi.liikennevirasto.viite.RoadType
import fi.liikennevirasto.viite.RoadType.PublicRoad
import fi.liikennevirasto.viite.dao.Discontinuity.{Continuous, MinorDiscontinuity}
import fi.liikennevirasto.viite.dao._
import org.joda.time.DateTime
import org.scalatest.{FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import fi.liikennevirasto.viite.util.toProjectLink
import fi.liikennevirasto.viite.util.toTransition

class ProjectDeltaCalculatorSpec  extends FunSuite with Matchers{
  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  def runWithRollback[T](f: => T): T = {
    Database.forDataSource(OracleDatabase.ds).withDynTransaction {
      val t = f
      dynamicSession.rollback()
      t
    }
  }

  private def createRoadAddress(start: Long, distance: Long) = {
    RoadAddress(id = start, roadNumber = 5, roadPartNumber = 205, roadType = PublicRoad, track = Track.Combined,
      discontinuity = Continuous, startAddrMValue = start, endAddrMValue = start+distance, lrmPositionId = start, linkId = start,
      startMValue = 0.0, endMValue = distance.toDouble, sideCode = TowardsDigitizing, adjustedTimestamp = 0L,
      geometry = Seq(Point(0.0, start), Point(0.0, start+distance)), linkGeomSource = NormalLinkInterface, ely = 8)
  }
  private val project: RoadAddressProject = RoadAddressProject(13L, ProjectState.Incomplete, "foo", "user", DateTime.now(), "user", DateTime.now(),
    DateTime.now(), "", Seq(), None, None)
  private def createTransferProjectLink(start: Long, distance: Long) = {
    toProjectLinkWithMove(project, LinkStatus.Transfer)(createRoadAddress(start, distance))
  }

  private def toProjectLinkWithMove(project: RoadAddressProject, status: LinkStatus)(roadAddress: RoadAddress): ProjectLink = {
    ProjectLink(roadAddress.id, roadAddress.roadNumber, roadAddress.roadPartNumber, roadAddress.track,
      roadAddress.discontinuity, roadAddress.startAddrMValue + project.id, roadAddress.endAddrMValue + project.id, roadAddress.startDate,
      roadAddress.endDate, modifiedBy=Option(project.createdBy), 0L, roadAddress.linkId, roadAddress.startMValue, roadAddress.endMValue,
      roadAddress.sideCode, roadAddress.calibrationPoints, floating=false, roadAddress.geometry, project.id, status,
      roadAddress.roadType, roadAddress.linkGeomSource, GeometryUtils.geometryLength(roadAddress.geometry), roadAddress.id, roadAddress.ely, false,
      None, 748800L)
  }

  test("Multiple transfers on single road part") {
    val transfer1 = (0 to 10).map(x => (createRoadAddress(x*10, 10), createTransferProjectLink(x*10, 10)))
    val transfer2 = (12 to 15).map(x => (createRoadAddress(x*10, 10), createTransferProjectLink(x*10, 10)))
    val mapping =
      ProjectDeltaCalculator.partition(transfer1 ++ transfer2)
    mapping.foreach{ elem =>
      elem._1.startMAddr should be (elem._2.startMAddr - project.id)
      elem._1.endMAddr should be (elem._2.endMAddr - project.id)
      elem._1.track should be (elem._2.track)
    }
  }

  test("Unchanged + transfer + transfer") {
    val addresses = (0 to 10).map(i => createRoadAddress(i*10, 10L))
    val addresses2 = (11 to 21).map(i => createRoadAddress(i*10, 10L)).map(a => a.copy(roadPartNumber=206, startAddrMValue = a.startAddrMValue-110L,
      endAddrMValue = a.endAddrMValue - 110L))
    val (transferLinks1, transferLinks2) = addresses2.map(toTransition(project, LinkStatus.Transfer)).partition(_._2.startAddrMValue == 0L)
    val projectLinks = addresses.map(toTransition(project, LinkStatus.UnChanged)) ++
      transferLinks1.map(l => (l._1, l._2.copy(roadPartNumber = 205, startAddrMValue = 110L, endAddrMValue=120L))) ++
      transferLinks2.map(l => (l._1, l._2.copy(startAddrMValue = l._2.startAddrMValue - 10L, endAddrMValue = l._2.endAddrMValue - 10L)))

    val partitions = ProjectDeltaCalculator.partition(projectLinks)
    partitions should have size(3)
    val start205 = partitions.find(p => p._1.roadPartNumberStart == 205 && p._2.roadPartNumberStart == 205)
    val to205 = partitions.find(p => p._1.roadPartNumberStart == 206 && p._2.roadPartNumberStart == 205)
    val remain205 = partitions.find(p => p._1.roadPartNumberStart == 206 && p._2.roadPartNumberStart == 206)

    start205.size should be (1)
    to205.size should be (1)
    remain205.size should be (1)

    start205.map(x => (x._1.startMAddr, x._2.startMAddr, x._1.endMAddr, x._2.endMAddr)) should be (Some((0L, 0L, 110L, 110L)))
  }

  test("Numbering operation") {
    val addresses = (0 to 10).map(i => createRoadAddress(i*10, 10L))
    val numberingLinks = addresses.map(a => (a, a.copy(roadNumber = 12345, roadPartNumber = 1))).map(x => (x._1, toProjectLink(project, LinkStatus.Numbering)(x._2)))

    val partitions = ProjectDeltaCalculator.partition(numberingLinks)
    partitions should have size(1)
    val correctRoadNumber = partitions.find(p => p._1.roadNumber == 5 && p._2.roadNumber == 12345)
    val correctRoadPartNumber = partitions.find(p => p._1.roadPartNumberStart == 205 && p._1.roadPartNumberEnd == 205 && p._2.roadPartNumberStart == 1 && p._2.roadPartNumberEnd == 1)
    correctRoadNumber.size should be (1)
    correctRoadPartNumber.size should be (1)

    correctRoadNumber.get._1.track should be (correctRoadNumber.get._2.track)
    correctRoadNumber.get._1.discontinuity should be (correctRoadNumber.get._2.discontinuity)
    correctRoadNumber.map(x => (x._1.startMAddr, x._2.startMAddr, x._1.endMAddr, x._2.endMAddr)) should be (Some((0L, 0L, 110L, 110L)))
  }

  test("2 track termination + transfer") {
    val addresses = (0 to 9).map(i => createRoadAddress(i*12, 12L)).map(_.copy(track = Track.RightSide))
    val addresses2 = (0 to 11).map(i => createRoadAddress(i*10, 10L)).map(l => l.copy(track = Track.LeftSide, id=l.id+1))
    val terminations = Seq(addresses.head, addresses2.head)
    val transfers = (addresses.tail ++ addresses2.tail).map(t => {
      val d = if (t.track == Track.RightSide) 12 else 10
      (t, toProjectLink(project, LinkStatus.Transfer)(t.copy(startAddrMValue = t.startAddrMValue - d,
        endAddrMValue = t.endAddrMValue - d)))
    })

    val termPart = ProjectDeltaCalculator.partition(terminations)
    termPart should have size(2)
    termPart.foreach(x => {
      x.startMAddr should be (0L)
      x.endMAddr should be (11L)
    })

    val transferParts = ProjectDeltaCalculator.partition(transfers)
    transferParts should have size(2)
    transferParts.foreach(x => {
      val (fr, to) = x
      fr.startMAddr should be (11L)
      to.startMAddr should be (0L)
      fr.endMAddr should be (120L)
      to.endMAddr should be (109L)
    })
  }

  test("unchanged + 2 track termination") {
    val addresses = (0 to 9).map(i => createRoadAddress(i*12, 12L)).map(_.copy(track = Track.RightSide))
    val addresses2 = (0 to 11).map(i => createRoadAddress(i*10, 10L)).map(l => l.copy(track = Track.LeftSide, id=l.id+1))
    val terminations = Seq(addresses.last, addresses2.last)
    val unchanged = (addresses.init ++ addresses2.init).map(toTransition(project, LinkStatus.UnChanged))

    val termPart = ProjectDeltaCalculator.partition(terminations)
    termPart should have size(2)
    termPart.foreach(x => {
      x.startMAddr should be (109L)
      x.endMAddr should be (120L)
    })

    val transferParts = ProjectDeltaCalculator.partition(unchanged)
    transferParts should have size(2)
    transferParts.foreach(x => {
      val (fr, to) = x
      fr.startMAddr should be (to.startMAddr)
      fr.endMAddr should be (to.endMAddr)
    })
  }

  test("unchanged + 2 track termination + transfer") {
    val addresses = (0 to 9).map(i => createRoadAddress(i*12, 12L)).map(_.copy(track = Track.RightSide))
    val addresses2 = (0 to 11).map(i => createRoadAddress(i*10, 10L)).map(l => l.copy(track = Track.LeftSide, id=l.id+1))
    val terminations = Seq(addresses(4), addresses(5), addresses2(5), addresses2(6))
    val unchanged = (addresses.take(3) ++ addresses2.take(4)).map(toTransition(project, LinkStatus.UnChanged))
    val transfers = (addresses.drop(5) ++ addresses2.drop(6)).map(toTransition(project, LinkStatus.Transfer)).map {
      case (a, pl) =>
        val d = if (pl.track == Track.RightSide) 11L else 7L
        (a, pl.copy(startAddrMValue = pl.startAddrMValue - d, endAddrMValue = pl.endAddrMValue - d))
    }

    val newLinks = Seq(ProjectLink(981, 5, 205, Track.RightSide,
      Discontinuity.MinorDiscontinuity, 36, 49, None, None,
      modifiedBy=Option(project.createdBy), 0L, 981, 0.0, 12.1,
      TowardsDigitizing, (None, None), floating=false, Seq(Point(0.0, 36.0), Point(0.0, 48.1)), project.id, LinkStatus.New,
      RoadType.PublicRoad, LinkGeomSource.NormalLinkInterface, 12.1, -1L, 8,false, None, 85900L),
      ProjectLink(982, 5, 205, Track.LeftSide,
      Discontinuity.MinorDiscontinuity, 40, 53, None, None,
      modifiedBy=Option(project.createdBy), 0L, 982, 0.0, 12.2,
      TowardsDigitizing, (None, None), floating=false, Seq(Point(0.0, 36.0), Point(0.0, 48.2)), project.id, LinkStatus.New,
      RoadType.PublicRoad, LinkGeomSource.NormalLinkInterface, 12.2, -1L, 8,false, None, 85900L),
      ProjectLink(983, 5, 205, Track.RightSide,
      Discontinuity.MinorDiscontinuity, 109, 124, None, None,
      modifiedBy=Option(project.createdBy), 0L, 983, 0.0, 15.2,
      TowardsDigitizing, (None, None), floating=false, Seq(Point(0.0, 120.0), Point(0.0, 135.2)), project.id, LinkStatus.New,
      RoadType.PublicRoad, LinkGeomSource.NormalLinkInterface, 15.2, -1L, 8,false, None, 85900L),
      ProjectLink(984, 5, 205, Track.LeftSide,
      Discontinuity.MinorDiscontinuity, 113, 127, None, None,
      modifiedBy=Option(project.createdBy), 0L, 984, 0.0, 14.2,
      TowardsDigitizing, (None, None), floating=false, Seq(Point(0.0, 120.0), Point(0.0, 135.2)), project.id, LinkStatus.New,
      RoadType.PublicRoad, LinkGeomSource.NormalLinkInterface, 14.2, -1L, 8,false, None, 85900L)
    )
    val termPart = ProjectDeltaCalculator.partition(terminations)
    termPart should have size(2)
    termPart.foreach(x => {
      x.startMAddr should be (49L)
      x.endMAddr should be (71L)
    })

    val uncParts = ProjectDeltaCalculator.partition(unchanged)
    uncParts should have size(2)
    uncParts.foreach(x => {
      val (fr, to) = x
      fr.startMAddr should be (0L)
      fr.endMAddr should be (38L)
    })

    val transferParts = ProjectDeltaCalculator.partition(transfers)
    transferParts should have size(2)
    transferParts.foreach(x => {
      val (fr, to) = x
      fr.startMAddr should be (60L)
      fr.endMAddr should be (120L)
      to.startMAddr should be (51L)
      to.endMAddr should be (111L)
    })
    val newParts = ProjectDeltaCalculator.partition(newLinks)
    newParts should have size(4)
    newParts.filter(_.startMAddr < 100).foreach(to => {
      to.startMAddr should be (38)
      to.endMAddr should be (51)
    })
    newParts.filter(_.startMAddr >= 100).foreach(to => {
      to.startMAddr should be (111)
      to.endMAddr should be (125)
    })
  }

  test("unchanged with road type change in between + new") {
    val addresses = (0 to 9).map(i => createRoadAddress(i*12, 12L)).map( ra =>
      if (ra.id > 50)
        ra
      else
        ra.copy(roadType = RoadType.MunicipalityStreetRoad)
    )
    val unchanged = addresses.map(a => (a, toProjectLink(project, LinkStatus.UnChanged)(a)))

    val newLinks = Seq(ProjectLink(981, 5, 205, Track.Combined,
      Discontinuity.MinorDiscontinuity, 120, 130, None, None,
      modifiedBy=Option(project.createdBy), 0L, 981, 0.0, 12.1,
      TowardsDigitizing, (None, None), floating=false, Seq(Point(0.0, 36.0), Point(0.0, 48.1)), project.id, LinkStatus.New,
      RoadType.PublicRoad, LinkGeomSource.NormalLinkInterface, 12.1, -1L, 8,false,
      None, 748800L))
    val uncParts = ProjectDeltaCalculator.partition(unchanged)
    uncParts should have size(2)
    uncParts.foreach(x => {
      val (fr, to) = x
      (fr.startMAddr == 60 || fr.endMAddr == 60) should be (true)
      (to.startMAddr == 60 || to.endMAddr == 60) should be (true)
      if (fr.startMAddr == 0L)
        fr.roadType should be (RoadType.MunicipalityStreetRoad)
      else
        fr.roadType should be (RoadType.PublicRoad)
      if (to.startMAddr == 0L)
        to.roadType should be (RoadType.MunicipalityStreetRoad)
      else
        to.roadType should be (RoadType.PublicRoad)
    })
    val newParts = ProjectDeltaCalculator.partition(newLinks)
    newParts should have size(1)
    newParts.foreach(to => {
      to.startMAddr should be (120)
      to.endMAddr should be (130)
    })
  }

  test("Calculate delta for split suravage link") {
    runWithRollback {
      val reservationId = Sequences.nextViitePrimaryKeySeqValue
      val lrms = Queries.fetchLrmPositionIds(4)
      val ids = (0 until 4).map(_ => Sequences.nextViitePrimaryKeySeqValue)
      val project = RoadAddressProject(Sequences.nextViitePrimaryKeySeqValue, ProjectState.apply(1), "TestProject", "TestUser", DateTime.parse("2999-01-01"), "TestUser", DateTime.parse("2999-01-01"), DateTime.parse("2999-01-01"), "Some additional info", Seq(), None , None)
      ProjectDAO.createRoadAddressProject(project)
      sqlu"""INSERT INTO PROJECT_RESERVED_ROAD_PART(id, road_number, road_part_number, project_id, created_by)
            values ($reservationId, 6591, 1, ${project.id}, '-')
          """.execute
      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,
            MODIFIED_DATE,LINK_SOURCE) values (${lrms(0)},null,'3','0',86.818,null,'6550673','1476392565000',
            sysdate,'1')""".execute
      sqlu"""Insert into ROAD_ADDRESS (ID,ROAD_NUMBER,ROAD_PART_NUMBER,TRACK_CODE,DISCONTINUITY,START_ADDR_M,END_ADDR_M,
            LRM_POSITION_ID,START_DATE,END_DATE,CREATED_BY,VALID_FROM,CALIBRATION_POINTS,FLOATING,GEOMETRY,VALID_TO) values
            (${ids(0)},'6591','1','0','5','0','85',${lrms(0)},to_date('01.01.1996','DD.MM.RRRR'),null,'tr',
            to_date('16.10.1998','DD.MM.RRRR'),'0','0',MDSYS.SDO_GEOMETRY(4002,3067,NULL,MDSYS.SDO_ELEM_INFO_ARRAY(1,2,1),
            MDSYS.SDO_ORDINATE_ARRAY(445889.442,7004298.67,0,0,445956.884,7004244.253,0,85)),null)""".execute
      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE,LINK_SOURCE)
            values (${lrms(1)},null,'3','0',63.926,null,'499972936','0',sysdate,'3')""".execute
      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE,LINK_SOURCE)
            values (${lrms(2)},null,'3',63.926,307.99,null,'499972936','0',sysdate,'3')""".execute
      sqlu"""Insert into LRM_POSITION (ID,LANE_CODE,SIDE_CODE,START_MEASURE,END_MEASURE,MML_ID,LINK_ID,ADJUSTED_TIMESTAMP,MODIFIED_DATE,LINK_SOURCE)
            values (${lrms(3)},null,'3',63.752,86.818,null,'6550673','0',sysdate,'1')""".execute
      sqlu"""Insert into PROJECT_LINK (ID,PROJECT_ID,TRACK_CODE,DISCONTINUITY_TYPE,ROAD_NUMBER,ROAD_PART_NUMBER,
            START_ADDR_M,END_ADDR_M,LRM_POSITION_ID,CREATED_BY,MODIFIED_BY,CREATED_DATE,MODIFIED_DATE,STATUS,
            CALIBRATION_POINTS,ROAD_TYPE,ROAD_ADDRESS_ID,CONNECTED_LINK_ID, GEOMETRY)
            values (${ids(1)},${project.id},'0','5','6591','1','0','62',${lrms(1)},'silari',null,
            to_date('20.10.2017','DD.MM.RRRR'),null,'1','0','1',${ids(0)},'6550673','')""".execute
      sqlu"""Insert into PROJECT_LINK (ID,PROJECT_ID,TRACK_CODE,DISCONTINUITY_TYPE,ROAD_NUMBER,ROAD_PART_NUMBER,
            START_ADDR_M,END_ADDR_M,LRM_POSITION_ID,CREATED_BY,MODIFIED_BY,CREATED_DATE,MODIFIED_DATE,STATUS,
            CALIBRATION_POINTS,ROAD_TYPE,ROAD_ADDRESS_ID,CONNECTED_LINK_ID, GEOMETRY)
             values (${ids(2)},${project.id},'0','5','6591','1','62','85',${lrms(2)},'silari',null,
             to_date('20.10.2017','DD.MM.RRRR'),null,'2','0','1',${ids(0)},'6550673','')""".execute
      sqlu"""Insert into PROJECT_LINK (ID,PROJECT_ID,TRACK_CODE,DISCONTINUITY_TYPE,ROAD_NUMBER,ROAD_PART_NUMBER,
            START_ADDR_M,END_ADDR_M,LRM_POSITION_ID,CREATED_BY,MODIFIED_BY,CREATED_DATE,MODIFIED_DATE,STATUS,
            CALIBRATION_POINTS,ROAD_TYPE,ROAD_ADDRESS_ID,CONNECTED_LINK_ID, GEOMETRY)
            values (${ids(3)},${project.id},'0','5','6591','1','62','85',${lrms(3)},'silari',null,
            to_date('20.10.2017','DD.MM.RRRR'),null,'5','2','9',${ids(0)},'499972936','')""".execute
      val delta = ProjectDeltaCalculator.delta(project)
      delta.terminations should have size (1)
      delta.unChanged.mapping should have size (1)
      delta.newRoads should have size (1)
      val term = delta.terminations.head
      term.startAddrMValue should be (62)
      term.endAddrMValue should be (85)
      term.id should be (ids(0))
      val (uncSource, uncTarget) = delta.unChanged.mapping.head
      uncSource.startAddrMValue should be (0)
      uncSource.endAddrMValue should be (62)
      uncSource.id should be (ids(0))
      val cre = delta.newRoads.head
      cre.startAddrMValue should be (62)
      cre.endAddrMValue should be (85)
      cre.id should be (ids(2))
    }
  }


  test ("road with ely change") {
    val addresses = (0 to 9).map(i => createRoadAddress(i*12, 12L))
    val links = addresses.filter(_.endAddrMValue < 61).map(a => (a, toProjectLink(project, LinkStatus.UnChanged)(a.copy(ely = 5))))
    val partitioned = ProjectDeltaCalculator.partition(links)
    partitioned.size should be (1)
    val (fr, to) = partitioned.head
    fr.startMAddr should be (to.startMAddr)
    fr.endMAddr should be (to.endMAddr)
    fr.ely should be (8)
    to.ely should be (5)
  }

  test("road with discontinuity change") {
    val addresses = (0 to 9).map(i => createRoadAddress(i*12, 12L))
    val links = addresses.map(a => {
      if (a.endAddrMValue == 60) {
        (a, toProjectLink(project, LinkStatus.UnChanged)(a.copy(discontinuity = Discontinuity.MinorDiscontinuity)))
      } else {
        toTransition(project, LinkStatus.UnChanged)(a)
      }
    })
    val partitioned = ProjectDeltaCalculator.partition(links)
    partitioned.size should be (2)
    partitioned.foreach(x => {
      val (fr, to) = x
      if (fr.startMAddr == 0) {
        fr.discontinuity should be (Discontinuity.Continuous)
        to.discontinuity should be (Discontinuity.MinorDiscontinuity)
      } else {
        fr.discontinuity should be (Discontinuity.Continuous)
        to.discontinuity should be (Discontinuity.Continuous)
      }
    })
  }

  test("Multiple transfers with reversal and discontinuity") {
    val transfer = Seq((createRoadAddress(0, 502).copy(discontinuity = MinorDiscontinuity),
      createTransferProjectLink(1524, 502).copy(reversed = true)),
      (createRoadAddress(502, 1524),
        createTransferProjectLink(0, 1524).copy(discontinuity = MinorDiscontinuity, reversed = true)))
    val mapping =
      ProjectDeltaCalculator.partition(transfer)
    mapping should have size (2)
    mapping.foreach{case (from, to) =>
      from.endMAddr - from.startMAddr should be (to.endMAddr - to.startMAddr)
      if (from.discontinuity != Continuous)
        to.discontinuity should be (Continuous)
      else
        to.discontinuity should be (MinorDiscontinuity)
    }
  }
}
