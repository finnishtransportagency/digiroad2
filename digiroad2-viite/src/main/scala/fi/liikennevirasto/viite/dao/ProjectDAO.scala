package fi.liikennevirasto.viite.dao
import java.sql.Timestamp

import com.github.tototoshi.slick.MySQLJodaSupport._
import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.{LinkGeomSource, SideCode}
import fi.liikennevirasto.digiroad2.linearasset.PolyLine
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.Sequences
import fi.liikennevirasto.digiroad2.oracle.MassQuery
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.viite.dao.CalibrationCode.{AtBeginning, AtBoth, AtEnd, No}
import fi.liikennevirasto.viite.{ReservedRoadPart, RoadType}
import fi.liikennevirasto.viite.process.{Delta, ProjectDeltaCalculator}
import fi.liikennevirasto.viite.util.CalibrationPointsUtils
import oracle.net.aso.p
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{StaticQuery => Q}

sealed trait ProjectState{
  def value: Int
  def description: String
}

object ProjectState{

  val values = Set(Closed, Incomplete,Sent2TR,ErroredInTR,TRProcessing,Saved2TR,Unknown)

  // These states are final
  val nonActiveStates = Set(ProjectState.Closed.value, ProjectState.Saved2TR.value)

  def apply(value: Long): ProjectState = {
    values.find(_.value == value).getOrElse(Closed)
  }

  case object Closed extends ProjectState {def value = 0; def description = "Suljettu"}
  case object Incomplete extends ProjectState { def value = 1; def description = "Keskeneräinen"}
  case object Sent2TR extends ProjectState {def value=2; def description ="Lähetetty tierekisteriin"}
  case object ErroredInTR extends ProjectState {def value=3; def description ="Virhe tierekisterissä"}
  case object TRProcessing extends ProjectState {def value=4; def description="Tierekisterissä käsittelyssä"}
  case object Saved2TR extends ProjectState{def value=5;def description ="Viety tierekisteriin"}
  case object Unknown extends ProjectState{def value=99;def description ="Tuntematon"}
}

sealed trait LinkStatus {
  def value: Int
}

object LinkStatus {
  val values = Set(NotHandled, Terminated, New, UnChanged, Unknown)
  case object NotHandled extends LinkStatus {def value = 0}
  case object Terminated extends LinkStatus {def value = 1}
  case object New extends LinkStatus {def value = 2}
  case object UnChanged extends LinkStatus {def value = 4}
  case object Unknown extends LinkStatus {def value = 99}
  def apply(intValue: Int): LinkStatus = {
    values.find(_.value == intValue).getOrElse(Unknown)
  }
}

case class RoadAddressProject(id: Long, status: ProjectState, name: String, createdBy: String, createdDate: DateTime,
                              modifiedBy: String, startDate: DateTime, dateModified: DateTime, additionalInfo: String,
                              reservedParts: Seq[ReservedRoadPart], statusInfo: Option[String], ely: Option[Long] = None)

case class ProjectLink(id: Long, roadNumber: Long, roadPartNumber: Long, track: Track,
                       discontinuity: Discontinuity, startAddrMValue: Long, endAddrMValue: Long, startDate: Option[DateTime] = None,
                       endDate: Option[DateTime] = None, modifiedBy: Option[String] = None, lrmPositionId : Long, linkId: Long, startMValue: Double, endMValue: Double, sideCode: SideCode,
                       calibrationPoints: (Option[CalibrationPoint], Option[CalibrationPoint]) = (None, None), floating: Boolean = false,
                       geometry: Seq[Point], projectId: Long, status: LinkStatus, roadType: RoadType, linkGeomSource: LinkGeomSource = LinkGeomSource.NormalLinkInterface, geometryLength: Double)
  extends BaseRoadAddress with PolyLine

case class ProjectFormLine(startingLinkId: Long, projectId: Long, roadNumber: Long, roadPartNumber: Long, roadLength: Long, ely : Long, discontinuity: String, isDirty: Boolean = false)

object ProjectDAO {

  def create(roadAddresses: Seq[ProjectLink]): Seq[Long] = {
    val lrmPositionPS = dynamicSession.prepareStatement("insert into lrm_position (ID, link_id, SIDE_CODE, start_measure, end_measure, adjusted_timestamp, link_source) values (?, ?, ?, ?, ?, ?, ?)")
    val addressPS = dynamicSession.prepareStatement("insert into PROJECT_LINK (id, project_id, lrm_position_id, " +
      "road_number, road_part_number, " +
      "track_code, discontinuity_type, START_ADDR_M, END_ADDR_M, created_by, " +
      "calibration_points, status) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
    val ids = sql"""SELECT lrm_position_primary_key_seq.nextval FROM dual connect by level <= ${roadAddresses.size}""".as[Long].list
    roadAddresses.zip(ids).foreach { case ((address), (lrmId)) =>
      RoadAddressDAO.createLRMPosition(lrmPositionPS, lrmId, address.linkId, address.sideCode.value, address.startMValue, address.endMValue, 0, address.linkGeomSource.value)
      addressPS.setLong(1, if (address.id == fi.liikennevirasto.viite.NewRoadAddress) {
        Sequences.nextViitePrimaryKeySeqValue
      } else address.id)
      addressPS.setLong(2, address.projectId)
      addressPS.setLong(3, lrmId)
      addressPS.setLong(4, address.roadNumber)
      addressPS.setLong(5, address.roadPartNumber)
      addressPS.setLong(6, address.track.value)
      addressPS.setLong(7, address.discontinuity.value)
      addressPS.setLong(8, address.startAddrMValue)
      addressPS.setLong(9, address.endAddrMValue)
      addressPS.setString(10, address.modifiedBy.get)
      addressPS.setDouble(11, CalibrationCode.getFromAddress(address).value)
      addressPS.setLong(12, address.status.value)
      addressPS.addBatch()
    }
    lrmPositionPS.executeBatch()
    addressPS.executeBatch()
    lrmPositionPS.close()
    addressPS.close()
    roadAddresses.map(_.id)
  }

  /*
  id: Long, roadNumber: Long, roadPartNumber: Long, track: Track,
                       discontinuity: Discontinuity, startAddrMValue: Long, endAddrMValue: Long, startDate: Option[DateTime] = None,
                       endDate: Option[DateTime] = None, modifiedBy: Option[String] = None, lrmPositionId : Long, linkId: Long, startMValue: Double, endMValue: Double, sideCode: SideCode,
                       calibrationPoints: (Option[CalibrationPoint], Option[CalibrationPoint]) = (None, None), floating: Boolean = false,
                       geometry: Seq[Point], projectId: Long, status: LinkStatus, roadType: RoadType, linkGeomSource: LinkGeomSource = LinkGeomSource.NormalLinkInterface, geometryLength: Double
   */


  def updateProjectLinksToDB( projectLinks: Seq[ProjectLink]): Unit ={
    val projectLinkPS = dynamicSession.prepareStatement("update project_link SET ROAD_NUMBER = ?,  ROAD_PART_NUMBER = ?, TRACK_CODE=?, DISCONTINUITY_TYPE = ?, START_ADDR_M=?, END_ADDR_M=? ,MODIFIED_BY=?,LRM_POSITION_ID=?, sidecode=?, calibrationPoints=?,projectid=?  WHERE id = ?")
    val lrmPS = dynamicSession.prepareStatement("update LRM_POSITION  SET SIDE_CODE=?, WHERE LINK_ID = ?")
    for (projectLink <-projectLinks){
      projectLinkPS.setLong(1,projectLink.roadNumber)
      projectLinkPS.setLong(2,projectLink.roadPartNumber)
      projectLinkPS.setInt(3,projectLink.track.value )
      projectLinkPS.setInt(4,projectLink.discontinuity.value)
      projectLinkPS.setLong(5,projectLink.startAddrMValue)
      projectLinkPS.setLong(6,projectLink.endAddrMValue)
/*   //add sysdate for modifieddate
      projectLinkPS.setDate(8 ,new java.sql.Date(projectLink.endDate.orNull.toDate.getTime))*/
      projectLinkPS.setString(7,projectLink.modifiedBy.getOrElse(""))
      projectLinkPS.setLong(8,projectLink.lrmPositionId)
      projectLinkPS.setInt(12,projectLink.sideCode.value)
      projectLinkPS.setLong(13, projectLink.projectId)   //.calibrations(CalibrationCode.apply(calibrationPoints.toInt))
      projectLinkPS.setInt(14, CalibrationCode.getFromProjectLink(projectLink).value)
      projectLinkPS.setInt(15, projectLink.status.value)
      projectLinkPS.setInt(15, projectLink.roadType.value)
    }
  }


  def createRoadAddressProject(roadAddressProject: RoadAddressProject): Unit = {
    val test = roadAddressProject.startDate
    sqlu"""
         insert into project (id, state, name, ely, created_by, created_date, start_date ,modified_by, modified_date, add_info)
         values (${roadAddressProject.id}, ${roadAddressProject.status.value}, ${roadAddressProject.name}, -1, ${roadAddressProject.createdBy}, sysdate ,${roadAddressProject.startDate}, '-' , sysdate, ${roadAddressProject.additionalInfo})
         """.execute
  }

  def getProjectLinks(projectId: Long, linkStatusFilter: Option[LinkStatus] = None): List[ProjectLink] = {
    val filter = if (linkStatusFilter.isEmpty) "" else s"PROJECT_LINK.STATUS = ${linkStatusFilter.get.value} AND"
    val query =
      s"""select PROJECT_LINK.ID, PROJECT_LINK.PROJECT_ID, PROJECT_LINK.TRACK_CODE, PROJECT_LINK.DISCONTINUITY_TYPE,
          PROJECT_LINK.ROAD_NUMBER, PROJECT_LINK.ROAD_PART_NUMBER, PROJECT_LINK.START_ADDR_M, PROJECT_LINK.END_ADDR_M,
          LRM_POSITION.START_MEASURE, LRM_POSITION.END_MEASURE, LRM_POSITION.SIDE_CODE, PROJECT_LINK.LRM_POSITION_ID,
          PROJECT_LINK.CREATED_BY, PROJECT_LINK.MODIFIED_BY, lrm_position.link_id,
          (LRM_POSITION.END_MEASURE - LRM_POSITION.START_MEASURE) as length, PROJECT_LINK.CALIBRATION_POINTS, PROJECT_LINK.STATUS,
          PROJECT_LINK.ROAD_TYPE, LRM_POSITION.LINK_SOURCE as source
                from PROJECT_LINK join LRM_POSITION
                on LRM_POSITION.ID = PROJECT_LINK.LRM_POSITION_ID
                where $filter (PROJECT_LINK.PROJECT_ID = $projectId ) order by PROJECT_LINK.ROAD_NUMBER, PROJECT_LINK.ROAD_PART_NUMBER, PROJECT_LINK.END_ADDR_M """
    Q.queryNA[(Long, Long, Int, Int, Long, Long, Long, Long, Long, Long, Long, Long, String, String, Long, Double, Long, Int, Int, Int)](query).list.map {
      case (projectLinkId, projectId, trackCode, discontinuityType, roadNumber, roadPartNumber, startAddrM, endAddrM,
      startMValue, endMValue, sideCode , lrmPositionId, createdBy, modifiedBy, linkId, length, calibrationPoints, status, roadType, source) =>
        ProjectLink(projectLinkId, roadNumber, roadPartNumber, Track.apply(trackCode), Discontinuity.apply(discontinuityType),
          startAddrM, endAddrM, None, None, None, lrmPositionId, linkId, startMValue, endMValue, SideCode.apply(sideCode.toInt),
          CalibrationPointsUtils.calibrations(CalibrationCode.apply(calibrationPoints.toInt),linkId,startMValue,endMValue,startAddrM,endAddrM, SideCode.apply(sideCode.toInt)),
          false, Seq.empty[Point], projectId, LinkStatus.apply(status), RoadType.apply(roadType),LinkGeomSource.apply(source),length)
    }
  }

  def getProjectLinksById(projectLinkId: Seq[Long]): List[ProjectLink] = {
    val query =
      s"""select PROJECT_LINK.ID, PROJECT_LINK.PROJECT_ID, PROJECT_LINK.TRACK_CODE, PROJECT_LINK.DISCONTINUITY_TYPE,
          PROJECT_LINK.ROAD_NUMBER, PROJECT_LINK.ROAD_PART_NUMBER, PROJECT_LINK.START_ADDR_M, PROJECT_LINK.END_ADDR_M,
          LRM_POSITION.START_MEASURE, LRM_POSITION.END_MEASURE, LRM_POSITION.SIDE_CODE, PROJECT_LINK.LRM_POSITION_ID,
          PROJECT_LINK.CREATED_BY, PROJECT_LINK.MODIFIED_BY, lrm_position.link_id,
          (LRM_POSITION.END_MEASURE - LRM_POSITION.START_MEASURE) as length, PROJECT_LINK.CALIBRATION_POINTS, PROJECT_LINK.STATUS,
          PROJECT_LINK.ROAD_TYPE, LRM_POSITION.LINK_SOURCE as source
                from PROJECT_LINK join LRM_POSITION
                on LRM_POSITION.ID = PROJECT_LINK.LRM_POSITION_ID
                where project_link.id in (${projectLinkId.mkString(",")}) order by PROJECT_LINK.ROAD_NUMBER, PROJECT_LINK.ROAD_PART_NUMBER, PROJECT_LINK.END_ADDR_M """
    Q.queryNA[(Long, Long, Int, Int, Long, Long, Long, Long, Long, Long, Long, Long, String, String, Long, Double, Long, Int, Int, Int)](query).list.map {
      case (projectLinkId, projectId, trackCode, discontinuityType, roadNumber, roadPartNumber, startAddrM, endAddrM,
      startMValue, endMValue, sideCode , lrmPositionId, createdBy, modifiedBy, linkId, length, calibrationPoints, status, roadType, source) =>
        ProjectLink(projectLinkId, roadNumber, roadPartNumber, Track.apply(trackCode), Discontinuity.apply(discontinuityType),
          startAddrM, endAddrM, None, None, None, lrmPositionId, linkId, startMValue, endMValue, SideCode.apply(sideCode.toInt),
          CalibrationPointsUtils.calibrations(CalibrationCode.apply(calibrationPoints.toInt),linkId,startMValue,endMValue,startAddrM,endAddrM, SideCode.apply(sideCode.toInt)),
          false, Seq.empty[Point], projectId, LinkStatus.apply(status), RoadType.apply(roadType),LinkGeomSource.apply(source),length)
    }
  }

  def getProjectByLinkId(projectLinkId: Long): List[ProjectLink] = {
    val query =
      s"""select PROJECT_LINK.ID, PROJECT_LINK.PROJECT_ID, PROJECT_LINK.TRACK_CODE, PROJECT_LINK.DISCONTINUITY_TYPE,
          PROJECT_LINK.ROAD_NUMBER, PROJECT_LINK.ROAD_PART_NUMBER, PROJECT_LINK.START_ADDR_M, PROJECT_LINK.END_ADDR_M,
          LRM_POSITION.START_MEASURE, LRM_POSITION.END_MEASURE, LRM_POSITION.SIDE_CODE, PROJECT_LINK.LRM_POSITION_ID,
          PROJECT_LINK.CREATED_BY, PROJECT_LINK.MODIFIED_BY, lrm_position.link_id,
          (LRM_POSITION.END_MEASURE - LRM_POSITION.START_MEASURE) as length, PROJECT_LINK.CALIBRATION_POINTS, PROJECT_LINK.STATUS,
          PROJECT_LINK.ROAD_TYPE, LRM_POSITION.LINK_SOURCE as source
                from PROJECT_LINK join LRM_POSITION
                on LRM_POSITION.ID = PROJECT_LINK.LRM_POSITION_ID
                where LRM_POSITION.link_id = ${projectLinkId} order by PROJECT_LINK.ROAD_NUMBER, PROJECT_LINK.ROAD_PART_NUMBER, PROJECT_LINK.END_ADDR_M """
    Q.queryNA[(Long, Long, Int, Int, Long, Long, Long, Long, Long, Long, Long, Long, String, String, Long, Double, Long, Int, Int, Int)](query).list.map {
      case (projectLinkId, projectId, trackCode, discontinuityType, roadNumber, roadPartNumber, startAddrM, endAddrM,
      startMValue, endMValue, sideCode , lrmPositionId, createdBy, modifiedBy, linkId, length, calibrationPoints, status, roadType, source) =>
        ProjectLink(projectLinkId, roadNumber, roadPartNumber, Track.apply(trackCode), Discontinuity.apply(discontinuityType),
          startAddrM, endAddrM, None, None, None, lrmPositionId, linkId, startMValue, endMValue, SideCode.apply(sideCode.toInt),
          CalibrationPointsUtils.calibrations(CalibrationCode.apply(calibrationPoints.toInt),linkId,startMValue,endMValue,startAddrM,endAddrM, SideCode.apply(sideCode.toInt)),
          false, Seq.empty[Point], projectId, LinkStatus.apply(status), RoadType.apply(roadType),LinkGeomSource.apply(source),length)
    }
  }

  def fetchByProjectNewRoadPart(roadNumber: Long, roadPartNumber: Long, projectId: Long, b: Boolean= false) = {
    val filter = s"PROJECT_LINK.ROAD_NUMBER = $roadNumber AND PROJECT_LINK.ROAD_PART_NUMBER = $roadPartNumber AND"
    val query =
      s"""select PROJECT_LINK.ID, PROJECT_LINK.PROJECT_ID, PROJECT_LINK.TRACK_CODE, PROJECT_LINK.DISCONTINUITY_TYPE,
          PROJECT_LINK.ROAD_NUMBER, PROJECT_LINK.ROAD_PART_NUMBER, PROJECT_LINK.START_ADDR_M, PROJECT_LINK.END_ADDR_M,
          LRM_POSITION.START_MEASURE, LRM_POSITION.END_MEASURE, LRM_POSITION.SIDE_CODE, PROJECT_LINK.LRM_POSITION_ID,
          PROJECT_LINK.CREATED_BY, PROJECT_LINK.MODIFIED_BY, lrm_position.link_id,
          (LRM_POSITION.END_MEASURE - LRM_POSITION.START_MEASURE) as length, PROJECT_LINK.CALIBRATION_POINTS, PROJECT_LINK.STATUS,
          PROJECT_LINK.ROAD_TYPE, LRM_POSITION.LINK_SOURCE as source
                from PROJECT_LINK join LRM_POSITION
                on LRM_POSITION.ID = PROJECT_LINK.LRM_POSITION_ID
                where $filter (PROJECT_LINK.PROJECT_ID = $projectId ) order by PROJECT_LINK.ROAD_NUMBER, PROJECT_LINK.ROAD_PART_NUMBER, PROJECT_LINK.END_ADDR_M """
    Q.queryNA[(Long, Long, Int, Int, Long, Long, Long, Long, Long, Long, Long, Long, String, String, Long, Double, Long, Int, Int, Int)](query).list.map {
      case (projectLinkId, projectId, trackCode, discontinuityType, roadNumber, roadPartNumber, startAddrM, endAddrM,
      startMValue, endMValue, sideCode , lrmPositionId, createdBy, modifiedBy, linkId, length, calibrationPoints, status, roadType, source) =>
        ProjectLink(projectLinkId, roadNumber, roadPartNumber, Track.apply(trackCode), Discontinuity.apply(discontinuityType),
          startAddrM, endAddrM, None, None, None, lrmPositionId, linkId, startMValue, endMValue, SideCode.apply(sideCode.toInt),
          CalibrationPointsUtils.calibrations(CalibrationCode.apply(calibrationPoints.toInt),linkId,startMValue,endMValue,startAddrM,endAddrM, SideCode.apply(sideCode.toInt)),
          false, Seq.empty[Point], projectId, LinkStatus.apply(status), RoadType.apply(roadType),LinkGeomSource.apply(source),length)
    }
  }

  def updateAddrMValues(projectLink: ProjectLink): Unit = {
      sqlu"""update project_link set modified_date = sysdate, start_addr_m = ${projectLink.startAddrMValue}, end_addr_m = ${projectLink.endAddrMValue} where id = ${projectLink.id}
          """.execute
  }

  def updateRoadAddressProject(roadAddressProject: RoadAddressProject): Unit = {
    sqlu"""
         update project set state = ${roadAddressProject.status.value}, name = ${roadAddressProject.name}, modified_by = '-' ,modified_date = sysdate, add_info=${roadAddressProject.additionalInfo}, start_date=${roadAddressProject.startDate} where id = ${roadAddressProject.id}
         """.execute
  }

  def getProjectEly(roadAddressProjectId: Long): Option[Long] = {
    val query =
      s"""
         SELECT ELY
         FROM project
         WHERE id=$roadAddressProjectId
       """
    Q.queryNA[Long](query).firstOption match
    {
      case Some(number) => Some(number)
      case None => Some(-1)
    }
  }

  def updateProjectEly(roadAddressProjectId: Long, ely: Long): Unit = {
    sqlu"""
       update project set ely = $ely, modified_date = sysdate where id =  ${roadAddressProjectId}
      """.execute
  }

  def getRoadAddressProjectById(id: Long): Option[RoadAddressProject] = {
    val where = s""" where id =${id}"""
    val query =
      s"""SELECT id, state, name, created_by, created_date, start_date, modified_by, COALESCE(modified_date, created_date), add_info, ely, status_info
          FROM project $where"""
    Q.queryNA[(Long, Long, String, String, DateTime, DateTime, String, DateTime, String, Option[Long], Option[String])](query).list.map {
      case (id, state, name, createdBy, createdDate, start_date, modifiedBy, modifiedDate, addInfo, ely, statusInfo) =>
        RoadAddressProject(id, ProjectState.apply(state), name, createdBy,createdDate, modifiedBy, start_date, modifiedDate, addInfo, List.empty[ReservedRoadPart], statusInfo, ely)
    }.headOption
  }

  def getRoadAddressProjects(projectId: Long = 0): List[RoadAddressProject] = {
    val filter = projectId match {
      case 0 => ""
      case _ => s""" where id =$projectId """
    }
    val query =
      s"""SELECT id, state, name, created_by, created_date, start_date, modified_by, COALESCE(modified_date, created_date), add_info, status_info, ely
          FROM project $filter order by name, id """
    Q.queryNA[(Long, Long, String, String, DateTime, DateTime, String, DateTime, String, Option[String], Option[Long])](query).list.map {
      case (id, state, name, createdBy, createdDate, start_date, modifiedBy, modifiedDate, addInfo, statusInfo, ely) =>
        RoadAddressProject(id, ProjectState.apply(state), name, createdBy, createdDate, modifiedBy, start_date, modifiedDate, addInfo, List.empty[ReservedRoadPart], statusInfo, ely)
    }
  }

  def roadPartReservedByProject(roadNumber: Long, roadPart: Long, projectId: Long = 0, withProjectId: Boolean = false): Option[String] = {
    val states = ProjectState.nonActiveStates.mkString(",")
    val filter = if(withProjectId && projectId !=0) s" AND project_id != ${projectId} " else ""
    val query =
      s"""SELECT p.name
              FROM project p
           INNER JOIN project_link l
           ON l.PROJECT_ID =  p.ID
           WHERE l.road_number=$roadNumber AND road_part_number=$roadPart AND p.state NOT IN ($states) $filter AND rownum < 2 """
    Q.queryNA[String](query).firstOption
  }

  def getProjectStatus(projectID: Long): Option[ProjectState] = {
    val query =
      s""" SELECT state
            FROM project
            WHERE id=$projectID
   """
    Q.queryNA[Long](query).firstOption match
    {
      case Some(statenumber)=> Some(ProjectState.apply(statenumber))
      case None=>None
    }
  }

  def getCheckCounter(projectID: Long): Option[Long] = {
    val query =
      s"""
         SELECT CHECK_COUNTER
         FROM project
         WHERE id=$projectID
       """
    Q.queryNA[Long](query).firstOption match
    {
      case Some(number) => Some(number)
      case None => Some(0)
    }
  }

  def setCheckCounter(projectID: Long, counter: Long) = {
    sqlu"""UPDATE project SET check_counter=$counter WHERE id=$projectID""".execute
  }

  def incrementCheckCounter(projectID:Long, increment: Long) = {
    sqlu"""UPDATE project SET check_counter = check_counter + $increment WHERE id=$projectID""".execute
  }

  def updateProjectLinkStatus(projectLinkIds: Set[Long], linkStatus: LinkStatus, userName: String): Unit = {
    val user = userName.replaceAll("[^A-Za-z0-9\\-]+", "")
    MassQuery.withIds(projectLinkIds) {
      s: String =>
        val sql = s"UPDATE PROJECT_LINK SET STATUS = ${linkStatus.value}, MODIFIED_BY='$user' WHERE ID IN (SELECT ID FROM $s)"
        Q.updateNA(sql).execute
    }
  }

  def flipProjectLinksSideCodes(projectId : Long, roadNumber : Long, roadPartNumber : Long): Unit = {
    val updateLRM = "update lrm_position set side_code = (CASE side_code WHEN 2 THEN 3 ELSE 2 END)" +
      " where id in (select lrm_position.id from project_link join " +
      s"LRM_Position on project_link.LRM_POSITION_ID = lrm_position.id where (side_code = 2 or side_code = 3) and project_link.project_id = $projectId and project_link.road_number = $roadNumber and project_link.road_part_number = $roadPartNumber)"
    Q.updateNA(updateLRM).execute
    val updateProjectLink = s"update project_link set calibration_points = (CASE calibration_points WHEN 0 THEN 0 WHEN 1 THEN 2 WHEN 2 THEN 1 ELSE 3 END), " +
      s"track_code = (CASE track_code WHEN 0 THEN 0 WHEN 1 THEN 2 WHEN 2 THEN 1 ELSE 3 END) " +
      s"where project_link.project_id = $projectId and project_link.road_number = $roadNumber and project_link.road_part_number = $roadPartNumber"
    Q.updateNA(updateProjectLink).execute
  }

  def projectLinksExist(projectId: Long, roadNumber: Long, roadPartNumber: Long):List[Long] =
  {
    val query= s"""
         SELECT Project_id
         FROM Project_link
         WHERE project_id = $projectId and road_number = $roadNumber and road_part_number = $roadPartNumber
       """
    Q.queryNA[Long](query).list
  }

  def updateProjectStatus(projectID:Long,state:ProjectState,errorMessage:String) {
    val projectstate=state.value
    sqlu""" update project set state=$projectstate, status_info=$errorMessage  WHERE id=$projectID""".execute
  }

  def getProjectsWithWaitingTRStatus(): List[Long] = {
    val query= s"""
         SELECT id
         FROM project
         WHERE state=${ProjectState.Sent2TR.value} OR state=${ProjectState.TRProcessing.value}
       """
    Q.queryNA[Long](query).list
  }

  private def deleteProjectLinks(ids: Set[Long]): Int = {
    if (ids.size > 900)
      ids.grouped(900).map(deleteProjectLinks).sum
    else {
      val deleteLinks =
        s"""
         DELETE FROM PROJECT_LINK WHERE id IN (${ids.mkString(",")})
       """
      val count = Q.updateNA(deleteLinks).first
      val deleteLrm =
        s"""
         DELETE FROM LRM_POSITION WHERE id IN (SELECT lrm_position_id from PROJECT_LINK where id in (${ids.mkString(",")}))
       """
      Q.updateNA(deleteLrm).execute
      count
    }
  }

  def removeProjectLinksById(projectLinkIds: Set[Long]): Int = {
    if (projectLinkIds.nonEmpty)
      deleteProjectLinks(projectLinkIds)
    else
      0
  }

  private def removeProjectLinks(projectId: Long, roadNumber: Option[Long], roadPartNumber: Option[Long],
                                 linkIds: Set[Long] = Set()): Int = {
    if (linkIds.size > 900) {
      linkIds.grouped(900).map(g => removeProjectLinks(projectId, roadNumber, roadPartNumber, g)).sum
    } else {
      val roadFilter = roadNumber.map(l => s"AND road_number = $l").getOrElse("")
      val roadPartFilter = roadPartNumber.map(l => s"AND road_part_number = $l").getOrElse("")
      val linkIdFilter = if (linkIds.isEmpty) {
        ""
      } else {
        s"AND pos.LINK_ID IN (${linkIds.mkString(",")})"
      }
      val query = s"""SELECT pl.id FROM PROJECT_LINK pl JOIN LRM_POSITION pos ON (pl.lrm_position_id = pos.id) WHERE
        project_id = $projectId $roadFilter $roadPartFilter $linkIdFilter"""
      val ids = Q.queryNA[Long](query).iterator.toSet
      if (ids.nonEmpty)
        deleteProjectLinks(ids)
      else
        0
    }
  }

  def removeProjectLinksByProjectAndRoadNumber(projectId: Long, roadNumber: Long, roadPartNumber: Long): Int = {
    removeProjectLinks(projectId, Some(roadNumber), Some(roadPartNumber))
  }

  def removeProjectLinksByProject(projectId : Long): Int = {
    removeProjectLinks(projectId, None, None)
  }

  def removeProjectLinksByLinkId(projectId: Long, linkIds: Set[Long]): Int = {
    removeProjectLinks(projectId, None, None, linkIds)
  }

  def toTimeStamp(dateTime: Option[DateTime]) = {
    dateTime.map(dt => new Timestamp(dt.getMillis))
  }
}
