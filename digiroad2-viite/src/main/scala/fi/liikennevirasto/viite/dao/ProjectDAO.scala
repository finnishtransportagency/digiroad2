package fi.liikennevirasto.viite.dao

import java.sql.{Timestamp, Types}
import java.util.Date

import com.github.tototoshi.slick.MySQLJodaSupport._
import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.{LinkGeomSource, SideCode}
import fi.liikennevirasto.digiroad2.linearasset.PolyLine
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.Sequences
import fi.liikennevirasto.digiroad2.oracle.MassQuery
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.viite.dao.CalibrationCode.{AtBeginning, AtBoth, AtEnd, No}
import fi.liikennevirasto.viite.dao.ProjectState.Incomplete
import fi.liikennevirasto.viite.model.ProjectAddressLink
import fi.liikennevirasto.viite.{ReservedRoadPart, RoadType}
import fi.liikennevirasto.viite.util.CalibrationPointsUtils
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedResult, StaticQuery => Q}

sealed trait ProjectState {
  def value: Int

  def description: String
}

object ProjectState {

  val values = Set(Closed, Incomplete, Sent2TR, ErroredInTR, TRProcessing, Saved2TR, Failed2GenerateTRIdInViite, Unknown)

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
  case object Failed2GenerateTRIdInViite extends ProjectState { def value = 6; def description = "Tierekisteri ID:tä ei voitu muodostaa"}
  case object Unknown extends ProjectState{def value=99;def description ="Tuntematon"}
}

sealed trait LinkStatus {
  def value: Int
}

object LinkStatus {
  val values = Set(NotHandled, Terminated, New, Transfer, UnChanged, Numbering, Unknown)
  case object NotHandled extends LinkStatus {def value = 0}
  case object UnChanged  extends LinkStatus {def value = 1}
  case object New extends LinkStatus {def value = 2}
  case object Transfer extends LinkStatus {def value = 3}
  case object Numbering extends LinkStatus {def value = 4}
  case object Terminated extends LinkStatus {def value = 5}
  case object Rollbacked extends LinkStatus {def value = 42}
  case object Unknown extends LinkStatus {def value = 99}
  def apply(intValue: Int): LinkStatus = {
    values.find(_.value == intValue).getOrElse(Unknown)
  }
}

case class RoadAddressProject(id: Long, status: ProjectState, name: String, createdBy: String, createdDate: DateTime,
                              modifiedBy: String, startDate: DateTime, dateModified: DateTime, additionalInfo: String,
                              reservedParts: Seq[ReservedRoadPart], statusInfo: Option[String], ely: Option[Long] = None) {
  def isReserved(roadNumber: Long, roadPartNumber: Long): Boolean = {
    reservedParts.exists(p => p.roadNumber == roadNumber && p.roadPartNumber == roadPartNumber)
  }
}

case class ProjectLink(id: Long, roadNumber: Long, roadPartNumber: Long, track: Track,
                       discontinuity: Discontinuity, startAddrMValue: Long, endAddrMValue: Long, startDate: Option[DateTime] = None,
                       endDate: Option[DateTime] = None, modifiedBy: Option[String] = None, lrmPositionId: Long, linkId: Long, startMValue: Double, endMValue: Double, sideCode: SideCode,
                       calibrationPoints: (Option[CalibrationPoint], Option[CalibrationPoint]) = (None, None), floating: Boolean = false,
                       geometry: Seq[Point], projectId: Long, status: LinkStatus, roadType: RoadType,
                       linkGeomSource: LinkGeomSource = LinkGeomSource.NormalLinkInterface, geometryLength: Double, roadAddressId: Long,
                       ely: Long, reversed: Boolean, connectedLinkId: Option[Long] = None)
  extends BaseRoadAddress with PolyLine {
  lazy val startingPoint = if (sideCode == SideCode.AgainstDigitizing) geometry.last else geometry.head
  lazy val endPoint = if (sideCode == SideCode.AgainstDigitizing) geometry.head else geometry.last
  lazy val isSplit: Boolean = connectedLinkId.nonEmpty || connectedLinkId.contains(0L)

  def copyWithGeometry(newGeometry: Seq[Point]) = {
    this.copy(geometry = newGeometry)
  }
}

object ProjectDAO {

  private val projectLinkQueryBase =
    s"""select PROJECT_LINK.ID, PROJECT_LINK.PROJECT_ID, PROJECT_LINK.TRACK_CODE, PROJECT_LINK.DISCONTINUITY_TYPE,
  PROJECT_LINK.ROAD_NUMBER, PROJECT_LINK.ROAD_PART_NUMBER, PROJECT_LINK.START_ADDR_M, PROJECT_LINK.END_ADDR_M,
  LRM_POSITION.START_MEASURE, LRM_POSITION.END_MEASURE, LRM_POSITION.SIDE_CODE, PROJECT_LINK.LRM_POSITION_ID,
  PROJECT_LINK.CREATED_BY, PROJECT_LINK.MODIFIED_BY, lrm_position.link_id,
  (LRM_POSITION.END_MEASURE - LRM_POSITION.START_MEASURE) as length, PROJECT_LINK.CALIBRATION_POINTS, PROJECT_LINK.STATUS,
  PROJECT_LINK.ROAD_TYPE, LRM_POSITION.LINK_SOURCE as source, PROJECT_LINK.ROAD_ADDRESS_ID, PROJECT_LINK.ELY, PROJECT_LINK.REVERSED, PROJECT_LINK.CONNECTED_LINK_ID,
  CASE
    WHEN STATUS = ${LinkStatus.NotHandled.value} THEN null
    WHEN STATUS IN (${LinkStatus.Terminated.value}, ${LinkStatus.UnChanged.value}) THEN ROAD_ADDRESS.START_DATE
    ELSE PRJ.START_DATE END as start_date,
  CASE WHEN STATUS = ${LinkStatus.Terminated.value} THEN PRJ.START_DATE ELSE null END as end_date
  from PROJECT prj JOIN PROJECT_LINK ON (prj.id = PROJECT_LINK.PROJECT_ID) join LRM_POSITION
    on (LRM_POSITION.ID = PROJECT_LINK.LRM_POSITION_ID) LEFT JOIN ROAD_ADDRESS ON (ROAD_ADDRESS.ID = PROJECT_LINK.ROAD_ADDRESS_ID)"""
  implicit val getProjectLinkRow = new GetResult[ProjectLink] {
    def apply(r: PositionedResult) = {
      val projectLinkId = r.nextLong()
      val projectId = r.nextLong()
      val trackCode = Track.apply(r.nextInt())
      val discontinuityType = Discontinuity.apply(r.nextInt())
      val roadNumber = r.nextLong()
      val roadPartNumber = r.nextLong()
      val startAddrM = r.nextLong()
      val endAddrM = r.nextLong()
      val startMValue = r.nextDouble()
      val endMValue = r.nextDouble()
      val sideCode = SideCode.apply(r.nextInt)
      val lrmPositionId = r.nextLong()
      val createdBy = r.nextString()
      val modifiedBy = r.nextString()
      val linkId = r.nextLong()
      val length = r.nextDouble()
      val calibrationPoints =
        CalibrationPointsUtils.calibrations(CalibrationCode.apply(r.nextInt), linkId, startMValue, endMValue,
          startAddrM, endAddrM, sideCode)
      val status = LinkStatus.apply(r.nextInt())
      val roadType = RoadType.apply(r.nextInt())
      val source = LinkGeomSource.apply(r.nextInt())
      val roadAddressId = r.nextLong()
      val ely = r.nextLong()
      val reversed = r.nextBoolean()
      val connectedLinkId = r.nextLongOption()
      val startDate = r.nextDateOption().map(d => new DateTime(d.getTime))
      val endDate = r.nextDateOption().map(d => new DateTime(d.getTime))

      ProjectLink(projectLinkId, roadNumber, roadPartNumber, trackCode, discontinuityType, startAddrM, endAddrM, startDate, endDate,
        None, lrmPositionId, linkId, startMValue, endMValue, sideCode, calibrationPoints, false, Seq.empty[Point], projectId,
        status, roadType, source, length, roadAddressId, ely, reversed, connectedLinkId)
    }
  }

  private def listQuery(query: String) = {
    Q.queryNA[ProjectLink](query).iterator.toSeq
  }

  def create(roadAddresses: Seq[ProjectLink]): Seq[Long] = {
    val lrmPositionPS = dynamicSession.prepareStatement("insert into lrm_position (ID, link_id, SIDE_CODE, start_measure, end_measure, adjusted_timestamp, link_source) values (?, ?, ?, ?, ?, ?, ?)")
    val addressPS = dynamicSession.prepareStatement("insert into PROJECT_LINK (id, project_id, lrm_position_id, " +
      "road_number, road_part_number, " +
      "track_code, discontinuity_type, START_ADDR_M, END_ADDR_M, created_by, " +
      "calibration_points, status, road_type, road_address_id, connected_link_id, ely, reversed) values " +
      "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
    val lrmIds = sql"""SELECT lrm_position_primary_key_seq.nextval FROM dual connect by level <= ${roadAddresses.size}""".as[Long].list
    val (ready, idLess) = roadAddresses.partition(_.id != fi.liikennevirasto.viite.NewRoadAddress)
    val plIds = Sequences.fetchViitePrimaryKeySeqValues(idLess.size)
    val projectLinks = ready ++ idLess.zip(plIds).map(x =>
      x._1.copy(id = x._2)
    )
    projectLinks.toList.zip(lrmIds).foreach { case (address, lrmId) =>
      RoadAddressDAO.createLRMPosition(lrmPositionPS, lrmId, address.linkId, address.sideCode.value, address.startMValue, address.endMValue, 0, address.linkGeomSource.value)
      addressPS.setLong(1, address.id)
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
      addressPS.setLong(13, address.roadType.value)
      if (address.roadAddressId == 0)
        addressPS.setString(14, null)
      else
        addressPS.setLong(14, address.roadAddressId)
      if (address.connectedLinkId.isDefined)
        addressPS.setLong(15, address.connectedLinkId.get)
      else
        addressPS.setString(15, null)
      addressPS.setLong(16, address.ely)
      addressPS.setBoolean(17, address.reversed)
      addressPS.addBatch()
    }
    lrmPositionPS.executeBatch()
    addressPS.executeBatch()
    lrmPositionPS.close()
    addressPS.close()
    projectLinks.map(_.id)
  }


  def updateProjectLinksToDB(projectLinks: Seq[ProjectLink], modifier: String): Unit = {
    val projectLinkPS = dynamicSession.prepareStatement("UPDATE project_link SET ROAD_NUMBER = ?,  ROAD_PART_NUMBER = ?, TRACK_CODE=?, " +
      "DISCONTINUITY_TYPE = ?, START_ADDR_M=?, END_ADDR_M=?, MODIFIED_DATE= ? , MODIFIED_BY= ?, LRM_POSITION_ID= ?, PROJECT_ID= ? , CALIBRATION_POINTS= ? , " +
      "STATUS=?,  ROAD_TYPE=?, REVERSED = ? WHERE id = ?")
    val lrmPS = dynamicSession.prepareStatement("UPDATE LRM_POSITION SET SIDE_CODE=?, START_MEASURE=?, END_MEASURE=?, LANE_CODE=?, MODIFIED_DATE=? WHERE ID = ?")

    for (projectLink <- projectLinks) {
      projectLinkPS.setLong(1, projectLink.roadNumber)
      projectLinkPS.setLong(2, projectLink.roadPartNumber)
      projectLinkPS.setInt(3, projectLink.track.value)
      projectLinkPS.setInt(4, projectLink.discontinuity.value)
      projectLinkPS.setLong(5, projectLink.startAddrMValue)
      projectLinkPS.setLong(6, projectLink.endAddrMValue)
      projectLinkPS.setDate(7, new java.sql.Date(new Date().getTime))
      projectLinkPS.setString(8, modifier)
      projectLinkPS.setLong(9, projectLink.lrmPositionId)
      projectLinkPS.setLong(10, projectLink.projectId)
      projectLinkPS.setInt(11, CalibrationCode.getFromAddress(projectLink).value)
      projectLinkPS.setInt(12, projectLink.status.value)
      projectLinkPS.setInt(13, projectLink.roadType.value)
      projectLinkPS.setInt(14, if (projectLink.reversed) 1 else 0)
      projectLinkPS.setLong(15, projectLink.id)
      projectLinkPS.addBatch()
      lrmPS.setInt(1, projectLink.sideCode.value)
      lrmPS.setDouble(2, projectLink.startMValue)
      lrmPS.setDouble(3, projectLink.endMValue)
      lrmPS.setInt(4, projectLink.track.value)
      lrmPS.setDate(5, new java.sql.Date(new Date().getTime))
      lrmPS.setLong(6, projectLink.lrmPositionId)
      lrmPS.addBatch()
    }
    projectLinkPS.executeBatch()
    lrmPS.executeBatch()
    lrmPS.close()
    projectLinkPS.close()
  }


  def createRoadAddressProject(roadAddressProject: RoadAddressProject): Unit = {
    sqlu"""
         insert into project (id, state, name, ely, created_by, created_date, start_date ,modified_by, modified_date, add_info)
         values (${roadAddressProject.id}, ${roadAddressProject.status.value}, ${roadAddressProject.name}, null, ${roadAddressProject.createdBy}, sysdate, ${roadAddressProject.startDate}, '-' , sysdate, ${roadAddressProject.additionalInfo})
         """.execute
  }

  def getProjectLinks(projectId: Long, linkStatusFilter: Option[LinkStatus] = None): Seq[ProjectLink] = {
    val filter = if (linkStatusFilter.isEmpty) "" else s"PROJECT_LINK.STATUS = ${linkStatusFilter.get.value} AND"
    val query =
      s"""$projectLinkQueryBase
                where $filter (PROJECT_LINK.PROJECT_ID = $projectId ) order by PROJECT_LINK.ROAD_NUMBER, PROJECT_LINK.ROAD_PART_NUMBER, PROJECT_LINK.END_ADDR_M """
    listQuery(query)
  }

  //TODO: support for bigger queries than 1000 ids
  def getProjectLinksByIds(ids: Iterable[Long]): Seq[ProjectLink] = {
    if (ids.isEmpty)
      List()
    else {
      val query =
        s"""$projectLinkQueryBase
                where project_link.id in (${ids.mkString(",")}) order by PROJECT_LINK.ROAD_NUMBER, PROJECT_LINK.ROAD_PART_NUMBER, PROJECT_LINK.END_ADDR_M """
      listQuery(query)
    }
  }

  def getProjectLinksByLinkId(projectLinkId: Long): Seq[ProjectLink] = {
    val query =
      s"""$projectLinkQueryBase
                where LRM_POSITION.link_id = $projectLinkId order by PROJECT_LINK.ROAD_NUMBER, PROJECT_LINK.ROAD_PART_NUMBER, PROJECT_LINK.END_ADDR_M """
    listQuery(query)
  }

  def getProjectLinksByProjectAndLinkId(linkIds: Iterable[Long], projectId: Long): Seq[ProjectLink] = {
    if (linkIds.isEmpty)
      List()
    else {
      val query =
        s"""$projectLinkQueryBase
                where link_id in (${linkIds.mkString(",")}) AND (PROJECT_LINK.PROJECT_ID = $projectId )   order by PROJECT_LINK.ROAD_NUMBER, PROJECT_LINK.ROAD_PART_NUMBER, PROJECT_LINK.END_ADDR_M """
      listQuery(query)
    }
  }


  def fetchByProjectRoadPart(roadNumber: Long, roadPartNumber: Long, projectId: Long): Seq[ProjectLink] = {
    val filter = s"PROJECT_LINK.ROAD_NUMBER = $roadNumber AND PROJECT_LINK.ROAD_PART_NUMBER = $roadPartNumber AND"
    val query =
      s"""$projectLinkQueryBase
                where $filter (PROJECT_LINK.PROJECT_ID = $projectId ) order by PROJECT_LINK.ROAD_NUMBER, PROJECT_LINK.ROAD_PART_NUMBER, PROJECT_LINK.END_ADDR_M """
    listQuery(query)
  }


  def fetchByProjectRoadParts(roadParts: Set[(Long, Long)], projectId: Long): Seq[ProjectLink] = {
    if (roadParts.isEmpty)
      return Seq()
    val roadPartsCond = roadParts.map{case (road, part) => s"(PROJECT_LINK.ROAD_NUMBER = $road AND PROJECT_LINK.ROAD_PART_NUMBER = $part)"}
    val filter = s"${roadPartsCond.mkString("(", " OR ", ")")} AND"
    val query =
      s"""$projectLinkQueryBase
                where $filter (PROJECT_LINK.PROJECT_ID = $projectId) order by PROJECT_LINK.ROAD_NUMBER, PROJECT_LINK.ROAD_PART_NUMBER, PROJECT_LINK.END_ADDR_M """
    listQuery(query)
  }

  def isRoadPartNotHandled(roadNumber: Long, roadPartNumber: Long, projectId: Long): Boolean = {
    val filter = s"PROJECT_LINK.ROAD_NUMBER = $roadNumber AND PROJECT_LINK.ROAD_PART_NUMBER = $roadPartNumber " +
      s"AND PROJECT_LINK.PROJECT_ID = $projectId AND PROJECT_LINK.STATUS = ${LinkStatus.NotHandled.value}"
    val query =
      s"""select PROJECT_LINK.ID from PROJECT_LINK
                where $filter AND ROWNUM < 2 """
    Q.queryNA[Long](query).firstOption.nonEmpty
  }


  //Should be only one
  def getProjectsWithGivenLinkId(linkId: Long): Seq[Long] = {
    val query =
      s"""SELECT P.ID
               FROM PROJECT P
              JOIN PROJECT_LINK PL ON P.ID=PL.PROJECT_ID
              JOIN LRM_POSITION L ON PL.LRM_POSITION_ID=L.ID
              WHERE P.STATE = ${Incomplete.value} AND L.LINK_ID=$linkId"""
    Q.queryNA[(Long)](query).list
  }


  def updateAddrMValues(projectLink: ProjectLink): Unit = {
    sqlu"""update project_link set modified_date = sysdate, start_addr_m = ${projectLink.startAddrMValue}, end_addr_m = ${projectLink.endAddrMValue}, calibration_points = ${CalibrationCode.getFromAddress(projectLink).value} where id = ${projectLink.id}
          """.execute
  }

  def updateRoadAddressProject(roadAddressProject: RoadAddressProject): Unit = {
    sqlu"""
         update project set state = ${roadAddressProject.status.value}, name = ${roadAddressProject.name}, modified_by = '-' ,modified_date = sysdate, add_info=${roadAddressProject.additionalInfo}, start_date=${roadAddressProject.startDate} where id = ${roadAddressProject.id}
         """.execute
    roadAddressProject.reservedParts.foreach(updateReservedRoadPart)
  }

  /**
    * Removes reserved road part and deletes the project links associated to it
    *
    * @param projectId        Project's id
    * @param reservedRoadPart Road part to be removed
    */
  def removeReservedRoadPart(projectId: Long, reservedRoadPart: ReservedRoadPart): Unit = {
    sqlu"""
           DELETE FROM PROJECT_LINK WHERE PROJECT_ID = $projectId AND ROAD_NUMBER = ${reservedRoadPart.roadNumber}
           AND ROAD_PART_NUMBER = ${reservedRoadPart.roadPartNumber}
           """.execute
    sqlu"""
         DELETE FROM PROJECT_RESERVED_ROAD_PART WHERE id = ${reservedRoadPart.id}
         """.execute
  }

  def getProjectEly(roadAddressProjectId: Long): Option[Long] = {
    val query =
      s"""
         SELECT ELY
         FROM project
         WHERE id=$roadAddressProjectId
       """
    Q.queryNA[Option[Long]](query).firstOption.getOrElse(None)
  }

  def updateProjectEly(roadAddressProjectId: Long, ely: Long): Unit = {
    sqlu"""
       update project set ely = $ely, modified_date = sysdate where id =  ${roadAddressProjectId}
      """.execute
  }

  def getRoadAddressProjectById(projectId: Long): Option[RoadAddressProject] = {
    val where = s""" where id =${projectId}"""
    val query =
      s"""SELECT id, state, name, created_by, created_date, start_date, modified_by, COALESCE(modified_date, created_date), add_info, ely, status_info
          FROM project $where"""
    Q.queryNA[(Long, Long, String, String, DateTime, DateTime, String, DateTime, String, Option[Long], Option[String])](query).list.map {
      case (id, state, name, createdBy, createdDate, start_date, modifiedBy, modifiedDate, addInfo,
      ely, statusInfo) if ely.contains(-1L) =>
        RoadAddressProject(id, ProjectState.apply(state), name, createdBy, createdDate, modifiedBy, start_date, modifiedDate,
          addInfo, fetchReservedRoadParts(id), statusInfo, None)
      case (id, state, name, createdBy, createdDate, start_date, modifiedBy, modifiedDate, addInfo,
      ely, statusInfo) =>
        RoadAddressProject(id, ProjectState.apply(state), name, createdBy, createdDate, modifiedBy, start_date, modifiedDate,
          addInfo, fetchReservedRoadParts(id), statusInfo, ely)
    }.headOption
  }

  def fetchReservedRoadParts(projectId: Long): Seq[ReservedRoadPart] = {
    Q.queryNA[(Long, Long, Long, Double, Long, Int, Long, Option[Long], Option[Long])](
      "SELECT id, road_number, road_part_number, road_length, ADDRESS_LENGTH, discontinuity, ely, first_link_id," +
        s"(select PROJECT_LINK.ID from PROJECT_LINK WHERE PROJECT_LINK.ROAD_NUMBER = rp.ROAD_NUMBER AND " +
        s"PROJECT_LINK.ROAD_PART_NUMBER = rp.ROAD_PART_NUMBER AND " +
        s"PROJECT_LINK.PROJECT_ID = rp.PROJECT_ID AND ROWNUM < 2) as pl_id " +
        s"FROM " +
        s"PROJECT_RESERVED_ROAD_PART rp WHERE project_id = $projectId ORDER BY road_number, road_part_number").list.map {
      case (id, road, part, length, addrLength, discontinuity, ely, linkId, plId) => ReservedRoadPart(id, road, part,
        length, addrLength, Discontinuity.apply(discontinuity), ely, None, None, linkId, plId.nonEmpty)
    }
  }

  def fetchReservedRoadPart(roadNumber: Long, roadPartNumber: Long): Option[ReservedRoadPart] = {
    val sql = "SELECT id, road_number, road_part_number, road_length, ADDRESS_LENGTH, discontinuity, ely, first_link_id, project_id," +
      s"(select PROJECT_LINK.ID from PROJECT_LINK WHERE PROJECT_LINK.ROAD_NUMBER = PROJECT_RESERVED_ROAD_PART.ROAD_NUMBER AND " +
      s"PROJECT_LINK.ROAD_PART_NUMBER = PROJECT_RESERVED_ROAD_PART.ROAD_PART_NUMBER AND " +
      s"PROJECT_LINK.PROJECT_ID = PROJECT_RESERVED_ROAD_PART.PROJECT_ID AND ROWNUM < 2) as pl_id " +
      s"FROM " +
      s"PROJECT_RESERVED_ROAD_PART WHERE road_number = $roadNumber AND road_part_number=$roadPartNumber"
    Q.queryNA[(Long, Long, Long, Double, Long, Int, Long, Option[Long], Long, Option[Long])](sql).firstOption.map {
      case (id, road, part, length, addrLength, discontinuity, ely, linkId, projectId, plId) => ReservedRoadPart(id, road, part,
        length, addrLength, Discontinuity.apply(discontinuity), ely, None, None, linkId, plId.nonEmpty)
    }
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
        RoadAddressProject(id, ProjectState.apply(state), name, createdBy, createdDate, modifiedBy, start_date,
          modifiedDate, addInfo, fetchReservedRoadParts(projectId), statusInfo, ely)
    }
  }

  def roadPartReservedTo(roadNumber: Long, roadPart: Long): Option[Long] = {
    val query =
      s"""SELECT p.id
              FROM project p
              JOIN PROJECT_RESERVED_ROAD_PART l
           ON l.PROJECT_ID =  p.ID
           WHERE l.road_number=$roadNumber AND road_part_number=$roadPart"""
    Q.queryNA[Long](query).firstOption
  }

  def roadPartReservedByProject(roadNumber: Long, roadPart: Long, projectId: Long = 0, withProjectId: Boolean = false): Option[String] = {
    val filter = if (withProjectId && projectId != 0) s" AND project_id != ${projectId} " else ""
    val query =
      s"""SELECT p.name
              FROM project p
              JOIN PROJECT_RESERVED_ROAD_PART l
           ON l.PROJECT_ID =  p.ID
           WHERE l.road_number=$roadNumber AND road_part_number=$roadPart $filter"""
    Q.queryNA[String](query).firstOption
  }

  def getProjectStatus(projectID: Long): Option[ProjectState] = {
    val query =
      s""" SELECT state
            FROM project
            WHERE id=$projectID
   """
    Q.queryNA[Long](query).firstOption match {
      case Some(statenumber) => Some(ProjectState.apply(statenumber))
      case None => None
    }
  }

  def getCheckCounter(projectID: Long): Option[Long] = {
    val query =
      s"""
         SELECT CHECK_COUNTER
         FROM project
         WHERE id=$projectID
       """
    Q.queryNA[Long](query).firstOption match {
      case Some(number) => Some(number)
      case None => Some(0)
    }
  }

  def setCheckCounter(projectID: Long, counter: Long) = {
    sqlu"""UPDATE project SET check_counter=$counter WHERE id=$projectID""".execute
  }

  def incrementCheckCounter(projectID: Long, increment: Long) = {
    sqlu"""UPDATE project SET check_counter = check_counter + $increment WHERE id=$projectID""".execute
  }

  def updateProjectLinkNumbering(projectId: Long, roadNumber: Long, roadPart: Long, linkStatus: LinkStatus, newRoadNumber: Long, newRoadPart: Long, userName: String): Unit = {
    val user = userName.replaceAll("[^A-Za-z0-9\\-]+", "")
    val sql = s"UPDATE PROJECT_LINK SET STATUS = ${linkStatus.value}, MODIFIED_BY='$user', ROAD_NUMBER = $newRoadNumber, ROAD_PART_NUMBER = $newRoadPart " +
      s"WHERE PROJECT_ID = $projectId  AND ROAD_NUMBER = $roadNumber AND ROAD_PART_NUMBER = $roadPart AND STATUS != ${LinkStatus.Terminated.value}"
    Q.updateNA(sql).execute
  }

  def updateProjectLinkRoadTypeDiscontinuity(projectLinkIds: Set[Long], linkStatus: LinkStatus, userName: String, roadType: Long, discontinuity: Option[Long]): Unit = {
    val user = userName.replaceAll("[^A-Za-z0-9\\-]+", "")
    if (discontinuity.isEmpty) {
      projectLinkIds.grouped(500).foreach {
        grp =>
          val sql = s"UPDATE PROJECT_LINK SET STATUS = ${linkStatus.value}, MODIFIED_BY='$user', ROAD_TYPE= $roadType " +
            s"WHERE ID IN ${grp.mkString("(", ",", ")")}"
          Q.updateNA(sql).execute
      }
    } else {
      val sql = s"UPDATE PROJECT_LINK SET STATUS = ${linkStatus.value}, MODIFIED_BY='$user', ROAD_TYPE= $roadType, DISCONTINUITY_TYPE = ${discontinuity.get} " +
        s"WHERE ID = ${projectLinkIds.head}"
      Q.updateNA(sql).execute
    }
  }

  def updateProjectLinks(projectLinkIds: Set[Long], linkStatus: LinkStatus, userName: String): Unit = {
    val user = userName.replaceAll("[^A-Za-z0-9\\-]+", "")
    projectLinkIds.grouped(500).foreach {
      grp =>
        val sql = s"UPDATE PROJECT_LINK SET STATUS = ${linkStatus.value}, MODIFIED_BY='$user' " +
          s"WHERE ID IN ${grp.mkString("(", ",", ")")}"
        Q.updateNA(sql).execute
    }
  }

  def addRotatingTRProjectId(projectId: Long) = {
    Q.updateNA(s"UPDATE PROJECT SET TR_ID = VIITE_PROJECT_SEQ.nextval WHERE ID= $projectId").execute
  }

  def removeRotatingTRProjectId(projectId: Long) = {
    Q.updateNA(s"UPDATE PROJECT SET TR_ID = NULL WHERE ID= $projectId").execute
  }

  def updateProjectStateInfo(stateInfo: String, projectId: Long) = {
    Q.updateNA(s"UPDATE PROJECT SET STATUS_INFO = '$stateInfo' WHERE ID= $projectId").execute
  }

  def getRotatingTRProjectId(projectId: Long) = {
    Q.queryNA[Long](s"Select tr_id From Project WHERE Id=$projectId AND tr_id IS NOT NULL ").list
  }


  def updateProjectLinkValues(projectId: Long, roadAddress: RoadAddress) = {
    val updateProjectLink = s"UPDATE PROJECT_LINK SET ROAD_NUMBER = ${roadAddress.roadNumber}, " +
      s" ROAD_PART_NUMBER = ${roadAddress.roadPartNumber}, TRACK_CODE = ${roadAddress.track.value}, " +
      s" DISCONTINUITY_TYPE = ${roadAddress.discontinuity.value}, ROAD_TYPE = ${roadAddress.roadType.value}, " +
      s" STATUS = ${LinkStatus.NotHandled.value}, START_ADDR_M = ${roadAddress.startAddrMValue}, END_ADDR_M = ${roadAddress.endAddrMValue}, " +
      s" CALIBRATION_POINTS = ${CalibrationCode.getFromAddress(roadAddress).value}, CONNECTED_LINK_ID = null, REVERSED = 0 " +
      s" WHERE ROAD_ADDRESS_ID = ${roadAddress.id} AND PROJECT_ID = $projectId"
    Q.updateNA(updateProjectLink).execute

    val updateLRMPosition = s"UPDATE LRM_POSITION SET SIDE_CODE = ${roadAddress.sideCode.value}, " +
      s"start_measure = ${roadAddress.startMValue}, end_measure = ${roadAddress.endMValue} where " +
      s"id in (SELECT LRM_POSITION_ID FROM PROJECT_LINK WHERE ROAD_ADDRESS_ID = ${roadAddress.id} )"
    Q.updateNA(updateLRMPosition).execute
  }

  /**
    * Reverses the road part in project. Switches side codes 2 <-> 3, updates calibration points start <-> end,
    * updates track codes 1 <-> 2
    * @param projectId
    * @param roadNumber
    * @param roadPartNumber
    */
  def reverseRoadPartDirection(projectId: Long, roadNumber: Long, roadPartNumber: Long): Unit = {
    val roadPartMaxAddr =
      sql"""SELECT MAX(END_ADDR_M) FROM PROJECT_LINK
         where project_link.project_id = $projectId and project_link.road_number = $roadNumber and project_link.road_part_number = $roadPartNumber
         and project_link.status != ${LinkStatus.Terminated.value}
         """.as[Long].firstOption.getOrElse(0L)
    val updateLRM = "update lrm_position set side_code = (CASE side_code WHEN 2 THEN 3 ELSE 2 END)" +
      " where id in (select lrm_position.id from project_link join " +
      s" LRM_Position on project_link.LRM_POSITION_ID = lrm_position.id where (side_code = 2 or side_code = 3) and " +
      s" project_link.project_id = $projectId and project_link.road_number = $roadNumber and project_link.road_part_number = $roadPartNumber" +
      s" and project_link.status != ${LinkStatus.Terminated.value})"
    Q.updateNA(updateLRM).execute
    val updateProjectLink = s"update project_link set calibration_points = (CASE calibration_points WHEN 0 THEN 0 WHEN 1 THEN 2 WHEN 2 THEN 1 ELSE 3 END), " +
      s"track_code = (CASE track_code WHEN 0 THEN 0 WHEN 1 THEN 2 WHEN 2 THEN 1 ELSE 3 END), " +
      s"(start_addr_m, end_addr_m) = (SELECT $roadPartMaxAddr - pl2.end_addr_m, $roadPartMaxAddr - pl2.start_addr_m FROM PROJECT_LINK pl2 WHERE pl2.id = project_link.id) " +
      s"where project_link.project_id = $projectId and project_link.road_number = $roadNumber and project_link.road_part_number = $roadPartNumber " +
      s"and project_link.status != ${LinkStatus.Terminated.value}"
    Q.updateNA(updateProjectLink).execute
  }

  def fetchProjectLinkIds(projectId: Long, roadNumber: Long, roadPartNumber: Long, status: Option[LinkStatus] = None,
                          maxResults: Option[Int] = None): List[Long] =
  {
    val filter = status.map(s => s" AND status = ${s.value}").getOrElse("")
    val limit = maxResults.map(s => s" AND ROWNUM <= $s").getOrElse("")
    val query= s"""
         SELECT LRM_position.link_id
         FROM Project_link JOIN LRM_Position on project_link.LRM_POSITION_ID = lrm_position.id
         WHERE project_id = $projectId and road_number = $roadNumber and road_part_number = $roadPartNumber $filter $limit
       """
    Q.queryNA[Long](query).list
  }

  def reserveRoadPart(projectId: Long, roadNumber: Long, roadPartNumber: Long, user: String,ely:Long): Unit = {
    sqlu"""INSERT INTO PROJECT_RESERVED_ROAD_PART(id, road_number, road_part_number, project_id, created_by, ely)
      SELECT viite_general_seq.nextval, $roadNumber, $roadPartNumber, $projectId, $user, $ely FROM DUAL""".execute
  }

  def updateReservedRoadPart(reserved: ReservedRoadPart): Unit = {
    sqlu"""UPDATE PROJECT_RESERVED_ROAD_PART SET ROAD_LENGTH = ${reserved.roadLength},
            ADDRESS_LENGTH = ${reserved.addressLength}, discontinuity = ${reserved.discontinuity.value},
            ely = ${reserved.ely}, FIRST_LINK_ID = ${reserved.startingLinkId} WHERE id = ${reserved.id}""".execute
  }

  def countLinksUnchangedUnhandled(projectId: Long, roadNumber: Long, roadPartNumber: Long): Long = {
    var query =
      s"""
         select count(id) from project_link
          WHERE project_id = $projectId and road_number = $roadNumber and road_part_number = $roadPartNumber and
          (status = ${LinkStatus.UnChanged.value} or status = ${LinkStatus.NotHandled.value})
       """
    Q.queryNA[Long](query).first
  }

  def updateProjectStatus(projectID: Long, state: ProjectState) {
    sqlu""" update project set state=${state.value} WHERE id=$projectID""".execute
  }

  def getProjectsWithWaitingTRStatus(): List[Long] = {
    val query =
      s"""
         SELECT id
         FROM project
         WHERE state=${ProjectState.Sent2TR.value} OR state=${ProjectState.TRProcessing.value}
       """
    Q.queryNA[Long](query).list
  }

  def getContinuityCodes(projectId: Long, roadNumber: Long, roadPartNumber: Long): Map[Long, Discontinuity] = {
    sql""" SELECT END_ADDR_M, DISCONTINUITY_TYPE FROM PROJECT_LINK WHERE PROJECT_ID = $projectId AND
         ROAD_NUMBER = $roadNumber AND ROAD_PART_NUMBER = $roadPartNumber AND STATUS != ${LinkStatus.Terminated.value}
         AND (DISCONTINUITY_TYPE != ${Discontinuity.Continuous.value} OR END_ADDR_M =
         (SELECT MAX(END_ADDR_M) FROM PROJECT_LINK WHERE PROJECT_ID = $projectId AND
           ROAD_NUMBER = $roadNumber AND ROAD_PART_NUMBER = $roadPartNumber AND STATUS != ${LinkStatus.Terminated.value}))
       """.as[(Long, Int)].list.map(x => x._1 -> Discontinuity.apply(x._2)).toMap
  }

  def fetchFirstLink(projectId: Long, roadNumber: Long, roadPartNumber: Long): Option[ProjectLink] = {
    val query = s"""$projectLinkQueryBase
    where PROJECT_LINK.ROAD_PART_NUMBER=$roadPartNumber AND PROJECT_LINK.ROAD_NUMBER=$roadNumber AND
      PROJECT_LINK.START_ADDR_M = (SELECT MIN(START_ADDR_M) FROM PROJECT_LINK WHERE
      PROJECT_LINK.PROJECT_ID = $projectId AND PROJECT_LINK.ROAD_PART_NUMBER=$roadPartNumber AND PROJECT_LINK.ROAD_NUMBER=$roadNumber)
    order by PROJECT_LINK.ROAD_NUMBER, PROJECT_LINK.ROAD_PART_NUMBER, PROJECT_LINK.START_ADDR_M, PROJECT_LINK.TRACK_CODE"""
    listQuery(query).headOption
  }

  private def deleteProjectLinks(ids: Set[Long]): Int = {
    if (ids.size > 900)
      ids.grouped(900).map(deleteProjectLinks).sum
    else {
      val lrmIds = Q.queryNA[Long](s"SELECT LRM_POSITION_ID FROM PROJECT_LINK WHERE ID IN (${ids.mkString(",")})").list
      val deleteLinks =
        s"""
         DELETE FROM PROJECT_LINK WHERE id IN (${ids.mkString(",")})
       """
      val count = Q.updateNA(deleteLinks).first
      val deleteLrm =
        s"""
         DELETE FROM LRM_POSITION WHERE id IN (${lrmIds.mkString(",")})
      """
      Q.updateNA(deleteLrm).execute
      count
    }
  }

  def removeProjectLinksById(ids: Set[Long]): Int = {
    if (ids.nonEmpty)
      deleteProjectLinks(ids)
    else
      0
  }

  def removeProjectLinksByLinkIds(projectId: Long, roadNumber: Option[Long], roadPartNumber: Option[Long],
                                  linkIds: Set[Long] = Set()): Int = {
    if (linkIds.size > 900 || linkIds.isEmpty) {
      linkIds.grouped(900).map(g => removeProjectLinks(projectId, roadNumber, roadPartNumber, g)).sum
    } else {
      removeProjectLinks(projectId, roadNumber, roadPartNumber, linkIds)
    }
  }
  private def removeProjectLinks(projectId: Long, roadNumber: Option[Long], roadPartNumber: Option[Long],
                                 linkIds: Set[Long] = Set()): Int = {
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

  def moveProjectLinksToHistory(projectId: Long): Unit = {
    sqlu"""INSERT INTO PROJECT_LINK_HISTORY (SELECT ID,
       PROJECT_ID, TRACK_CODE, DISCONTINUITY_TYPE, ROAD_NUMBER, ROAD_PART_NUMBER, START_ADDR_M,
       END_ADDR_M, LRM_POSITION_ID, CREATED_BY, MODIFIED_BY, CREATED_DATE, MODIFIED_DATE,
       STATUS, CALIBRATION_POINTS, ROAD_TYPE FROM PROJECT_LINK WHERE PROJECT_ID = $projectId)""".execute
    sqlu"""DELETE FROM PROJECT_LINK WHERE PROJECT_ID = $projectId""".execute
    sqlu"""DELETE FROM PROJECT_RESERVED_ROAD_PART WHERE PROJECT_ID = $projectId""".execute
  }

  def removeProjectLinksByProjectAndRoadNumber(projectId: Long, roadNumber: Long, roadPartNumber: Long): Int = {
    removeProjectLinks(projectId, Some(roadNumber), Some(roadPartNumber))
  }

  def removeProjectLinksByProject(projectId: Long): Int = {
    removeProjectLinks(projectId, None, None)
  }

  def removeProjectLinksByLinkId(projectId: Long, linkIds: Set[Long]): Int = {
    if (linkIds.nonEmpty)
      removeProjectLinks(projectId, None, None, linkIds)
    else
      0
  }

  def fetchSplitLinks(projectId: Long, linkId: Long): Seq[ProjectLink] = {
    val query =
      s"""$projectLinkQueryBase
                where PROJECT_LINK.PROJECT_ID = $projectId AND (LRM_POSITION.LINK_ID = $linkId OR PROJECT_LINK.CONNECTED_LINK_ID = $linkId)"""
    listQuery(query)
  }

  def toTimeStamp(dateTime: Option[DateTime]) = {
    dateTime.map(dt => new Timestamp(dt.getMillis))
  }
}
