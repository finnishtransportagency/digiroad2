package fi.liikennevirasto.viite.dao

import java.sql.{Timestamp, Types}
import java.util.Date
import java.util.regex.{Matcher, Pattern}

import com.github.tototoshi.slick.MySQLJodaSupport._
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import fi.liikennevirasto.digiroad2.asset.{LinkGeomSource, SideCode}
import fi.liikennevirasto.digiroad2.linearasset.PolyLine
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.Sequences
import fi.liikennevirasto.digiroad2.oracle.MassQuery
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.viite.dao.CalibrationCode.{AtBeginning, AtBoth, AtEnd, No}
import fi.liikennevirasto.viite.dao.ProjectState.Incomplete
import fi.liikennevirasto.viite.model.ProjectAddressLink
import fi.liikennevirasto.viite.process.InvalidAddressDataException
import fi.liikennevirasto.viite._
import fi.liikennevirasto.viite.dao.LinkStatus.{NotHandled, UnChanged}
import fi.liikennevirasto.viite.util.CalibrationPointsUtils
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedResult, StaticQuery => Q}

import scala.util.control.NonFatal

sealed trait ProjectState {
  def value: Int

  def description: String
}

object ProjectState {

  val values = Set(Closed, Incomplete, Sent2TR, ErroredInTR, TRProcessing, Saved2TR, Failed2GenerateTRIdInViite, Deleted, Unknown)

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
  case object Deleted extends ProjectState {def value = 7; def description = "Poistettu projekti"}
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
  case object Unknown extends LinkStatus {def value = 99}
  def apply(intValue: Int): LinkStatus = {
    values.find(_.value == intValue).getOrElse(Unknown)
  }
}

case class RoadAddressProject(id: Long, status: ProjectState, name: String, createdBy: String, createdDate: DateTime,
                              modifiedBy: String, startDate: DateTime, dateModified: DateTime, additionalInfo: String,
                              reservedParts: Seq[ReservedRoadPart], statusInfo: Option[String], ely: Option[Long] = None, coordinates: Option[ProjectCoordinates] = None) {
  def isReserved(roadNumber: Long, roadPartNumber: Long): Boolean = {
    reservedParts.exists(p => p.roadNumber == roadNumber && p.roadPartNumber == roadPartNumber)
  }
}

case class ProjectCoordinates(x: Double, y: Double, zoom: Int)

case class ProjectLink(id: Long, roadNumber: Long, roadPartNumber: Long, track: Track,
                       discontinuity: Discontinuity, startAddrMValue: Long, endAddrMValue: Long, startDate: Option[DateTime] = None,
                       endDate: Option[DateTime] = None, modifiedBy: Option[String] = None, lrmPositionId: Long, linkId: Long, startMValue: Double, endMValue: Double, sideCode: SideCode,
                       calibrationPoints: (Option[CalibrationPoint], Option[CalibrationPoint]) = (None, None), floating: Boolean = false,
                       geometry: Seq[Point], projectId: Long, status: LinkStatus, roadType: RoadType,
                       linkGeomSource: LinkGeomSource = LinkGeomSource.NormalLinkInterface, geometryLength: Double, roadAddressId: Long,
                       ely: Long, reversed: Boolean, connectedLinkId: Option[Long] = None, linkGeometryTimeStamp: Long)
  extends BaseRoadAddress with PolyLine {
  lazy val startingPoint = if (sideCode == SideCode.AgainstDigitizing) geometry.last else geometry.head
  lazy val endPoint = if (sideCode == SideCode.AgainstDigitizing) geometry.head else geometry.last
  lazy val isSplit: Boolean = connectedLinkId.nonEmpty || connectedLinkId.contains(0L)

  def copyWithGeometry(newGeometry: Seq[Point]) = {
    this.copy(geometry = newGeometry)
  }

  def toNewRoadAddress(): RoadAddress = {
    RoadAddress(NewRoadAddress, roadNumber, this.roadPartNumber, roadType, track, discontinuity, startAddrMValue,
      endAddrMValue, startDate, endDate, modifiedBy, 0L, linkId, startMValue, endMValue, sideCode, linkGeometryTimeStamp,
      calibrationPoints, floating, Seq(GeometryUtils.geometryEndpoints(geometry)._1, GeometryUtils.geometryEndpoints(geometry)._2),
      linkGeomSource, ely, if (LinkStatus.Terminated == status) TerminationCode.Termination else TerminationCode.NoTermination)
  }

  def addrAt(a: Double) = {
    val coefficient = (endAddrMValue - startAddrMValue) / (endMValue - startMValue)
    sideCode match {
      case SideCode.AgainstDigitizing =>
        endAddrMValue - Math.round((a-startMValue) * coefficient)
      case SideCode.TowardsDigitizing =>
        startAddrMValue + Math.round((a-startMValue) * coefficient)
      case _ => throw new InvalidAddressDataException(s"Bad sidecode $sideCode on project link")
    }
  }
}

object ProjectDAO {

  private val projectLinkQueryBase =
    s"""select PROJECT_LINK.ID, PROJECT_LINK.PROJECT_ID, PROJECT_LINK.TRACK_CODE, PROJECT_LINK.DISCONTINUITY_TYPE,
  PROJECT_LINK.ROAD_NUMBER, PROJECT_LINK.ROAD_PART_NUMBER, PROJECT_LINK.START_ADDR_M, PROJECT_LINK.END_ADDR_M,
  LRM_POSITION.START_MEASURE, LRM_POSITION.END_MEASURE, LRM_POSITION.SIDE_CODE, PROJECT_LINK.LRM_POSITION_ID,
  PROJECT_LINK.CREATED_BY, PROJECT_LINK.MODIFIED_BY, lrm_position.link_id, PROJECT_LINK.GEOMETRY,
  (LRM_POSITION.END_MEASURE - LRM_POSITION.START_MEASURE) as length, PROJECT_LINK.CALIBRATION_POINTS, PROJECT_LINK.STATUS,
  PROJECT_LINK.ROAD_TYPE, LRM_POSITION.LINK_SOURCE as source, PROJECT_LINK.ROAD_ADDRESS_ID, PROJECT_LINK.ELY, PROJECT_LINK.REVERSED, PROJECT_LINK.CONNECTED_LINK_ID,
  CASE
    WHEN STATUS = ${LinkStatus.NotHandled.value} THEN null
    WHEN STATUS IN (${LinkStatus.Terminated.value}, ${LinkStatus.UnChanged.value}) THEN ROAD_ADDRESS.START_DATE
    ELSE PRJ.START_DATE END as start_date,
  CASE WHEN STATUS = ${LinkStatus.Terminated.value} THEN PRJ.START_DATE ELSE null END as end_date,
  LRM_POSITION.ADJUSTED_TIMESTAMP
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
      val createdBy = r.nextStringOption()
      val modifiedBy = r.nextStringOption()
      val linkId = r.nextLong()
      val geom=r.nextStringOption()
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
      val geometryTimeStamp = r.nextLong()

      ProjectLink(projectLinkId, roadNumber, roadPartNumber, trackCode, discontinuityType, startAddrM, endAddrM, startDate, endDate,
        modifiedBy, lrmPositionId, linkId, startMValue, endMValue, sideCode, calibrationPoints, false, parseStringGeometry(geom.getOrElse("")), projectId,
        status, roadType, source, length, roadAddressId, ely, reversed, connectedLinkId, geometryTimeStamp)
    }
  }


  private def parseStringGeometry(geomString: String): Seq[Point] = {
    if (geomString.nonEmpty)
      toGeometry(geomString)
    else
      Seq()
  }

  private def listQuery(query: String) = {
    Q.queryNA[ProjectLink](query).iterator.toSeq
  }

  def create(links: Seq[ProjectLink]): Seq[Long] = {
    val lrmPositionPS = dynamicSession.prepareStatement("insert into lrm_position (ID, link_id, SIDE_CODE, start_measure, " +
      "end_measure, adjusted_timestamp, link_source) values (?, ?, ?, ?, ?, ?, ?)")
    val addressPS = dynamicSession.prepareStatement("insert into PROJECT_LINK (id, project_id, lrm_position_id, " +
      "road_number, road_part_number, " +
      "track_code, discontinuity_type, START_ADDR_M, END_ADDR_M, created_by, " +
      "calibration_points, status, road_type, road_address_id, connected_link_id, ely, reversed, geometry) values " +
      "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
    val lrmIds = sql"""SELECT lrm_position_primary_key_seq.nextval FROM dual connect by level <= ${links.size}""".as[Long].list
    val (ready, idLess) = links.partition(_.id != NewRoadAddress)
    val plIds = Sequences.fetchViitePrimaryKeySeqValues(idLess.size)
    val projectLinks = ready ++ idLess.zip(plIds).map(x =>
      x._1.copy(id = x._2)
    )
    projectLinks.toList.zip(lrmIds).foreach { case (pl, lrmId) =>
      RoadAddressDAO.createLRMPosition(lrmPositionPS, lrmId, pl.linkId, pl.sideCode.value, pl.startMValue,
        pl.endMValue, pl.linkGeometryTimeStamp, pl.linkGeomSource.value)
      addressPS.setLong(1, pl.id)
      addressPS.setLong(2, pl.projectId)
      addressPS.setLong(3, lrmId)
      addressPS.setLong(4, pl.roadNumber)
      addressPS.setLong(5, pl.roadPartNumber)
      addressPS.setLong(6, pl.track.value)
      addressPS.setLong(7, pl.discontinuity.value)
      addressPS.setLong(8, pl.startAddrMValue)
      addressPS.setLong(9, pl.endAddrMValue)
      addressPS.setString(10, pl.modifiedBy.getOrElse(null))
      addressPS.setDouble(11, CalibrationCode.getFromAddress(pl).value)
      addressPS.setLong(12, pl.status.value)
      addressPS.setLong(13, pl.roadType.value)
      if (pl.roadAddressId == 0)
        addressPS.setString(14, null)
      else
        addressPS.setLong(14, pl.roadAddressId)
      if (pl.connectedLinkId.isDefined)
        addressPS.setLong(15, pl.connectedLinkId.get)
      else
        addressPS.setString(15, null)
      addressPS.setLong(16, pl.ely)
      addressPS.setBoolean(17, pl.reversed)
      addressPS.setString(18, toGeomString(pl.geometry))
     addressPS.addBatch()
    }
    lrmPositionPS.executeBatch()
    addressPS.executeBatch()
    lrmPositionPS.close()
    addressPS.close()
    projectLinks.map(_.id)
  }

  def updateProjectLinksToDB(projectLinks: Seq[ProjectLink], modifier: String): Unit = {
    val nonUpdatingStatus = Set[LinkStatus](NotHandled, UnChanged)
    val addresses = RoadAddressDAO.fetchByIdMassQuery(projectLinks.map(_.roadAddressId).toSet).map(ra => ra.id -> ra).toMap
    val links = projectLinks.map{ pl =>
      if (!pl.isSplit && nonUpdatingStatus.contains(pl.status) && addresses.contains(pl.roadAddressId)) {
        val ra = addresses(pl.roadAddressId)
        // Discontinuity, road type and calibration points may change with Unchanged (and NotHandled) status
        pl.copy(roadNumber = ra.roadNumber, roadPartNumber = ra.roadPartNumber, track = ra.track,
          startAddrMValue = ra.startAddrMValue, endAddrMValue = ra.endAddrMValue,
          reversed = false)
      } else
        pl
    }
    val projectLinkPS = dynamicSession.prepareStatement("UPDATE project_link SET ROAD_NUMBER = ?,  ROAD_PART_NUMBER = ?, TRACK_CODE=?, " +
      "DISCONTINUITY_TYPE = ?, START_ADDR_M=?, END_ADDR_M=?, MODIFIED_DATE= ? , MODIFIED_BY= ?, LRM_POSITION_ID= ?, PROJECT_ID= ?, " +
      "CALIBRATION_POINTS= ? , STATUS=?,  ROAD_TYPE=?, REVERSED = ?, GEOMETRY = ? WHERE id = ?")
    val lrmPS = dynamicSession.prepareStatement("UPDATE LRM_POSITION SET SIDE_CODE=?, START_MEASURE=?, END_MEASURE=?, LANE_CODE=?, " +
      "MODIFIED_DATE=? WHERE ID = ?")

    for (projectLink <- links) {
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
      projectLinkPS.setString(15, toGeomString(projectLink.geometry))
      projectLinkPS.setLong(16, projectLink.id)
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


  def updateProjectLinksGeometry(projectLinks: Seq[ProjectLink], modifier: String): Unit = {
    val projectLinkPS = dynamicSession.prepareStatement("UPDATE project_link SET  GEOMETRY = ?, MODIFIED_BY= ? WHERE id = ?")
    val lrmPS = dynamicSession.prepareStatement("UPDATE LRM_POSITION SET ADJUSTED_TIMESTAMP = ? WHERE ID = ?")

    for (projectLink <- projectLinks) {
      projectLinkPS.setString(1, toGeomString(projectLink.geometry))
      projectLinkPS.setString(2, modifier)
      projectLinkPS.setLong(3, projectLink.id)
      projectLinkPS.addBatch()
      lrmPS.setLong(1, projectLink.linkGeometryTimeStamp)
      lrmPS.setLong(2, projectLink.lrmPositionId)
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

  def getElyFromProjectLinks(projectId:Long): Option[Long]= {
    val query =
      s"""SELECT ELY FROM PROJECT_LINK WHERE PROJECT_ID=$projectId AND ELY IS NOT NULL AND ROWNUM < 2"""
    Q.queryNA[Long](query).firstOption
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

  def getInvalidUnchangedOperationProjectLinks(roadNumber: Long, roadPartNumber: Long) : Seq[ProjectLink]= {
    if(roadNumber == 0 || roadPartNumber == 0)
      return Seq()
    val query =
      s"""
         $projectLinkQueryBase
                where ROAD_ADDRESS.road_number = $roadNumber and ROAD_ADDRESS.road_part_number = $roadPartNumber and ROAD_ADDRESS.TRACK_CODE = PROJECT_LINK.TRACK_CODE and PROJECT_LINK.status = ${LinkStatus.UnChanged.value}
                and (ROAD_ADDRESS.valid_to IS NULL OR ROAD_ADDRESS.valid_to > sysdate) AND (ROAD_ADDRESS.valid_from IS NULL OR ROAD_ADDRESS.valid_from <= sysdate)
                and ROAD_ADDRESS.start_addr_m in
                (select ra.end_addr_m from road_address ra, project_link pl
                where ra.id = pl.road_address_id and ra.road_number = $roadNumber and ra.road_part_number = $roadPartNumber and ra.TRACK_CODE = pl.TRACK_CODE and pl.status NOT IN (${LinkStatus.NotHandled.value}, ${LinkStatus.UnChanged.value})
                and (ra.valid_to IS NULL OR ra.valid_to > sysdate) AND (ra.valid_from IS NULL OR ra.valid_from <= sysdate)) order by START_ADDR_M
       """
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
         update project set state = ${roadAddressProject.status.value}, name = ${roadAddressProject.name}, modified_by = '-' ,modified_date = sysdate, add_info=${roadAddressProject.additionalInfo}, start_date=${roadAddressProject.startDate}, ely = ${roadAddressProject.ely} where id = ${roadAddressProject.id}
         """.execute
  }

  /**
    * Removes reserved road part and deletes the project links associated to it.
    * Requires links that have been transferred to this road part to be reverted before
    * or this will fail.
    *
    * @param projectId        Project's id
    * @param reservedRoadPart Road part to be removed
    */
  def removeReservedRoadPart(projectId: Long, reservedRoadPart: ReservedRoadPart): Unit = {
    sqlu"""
           DELETE FROM PROJECT_LINK WHERE PROJECT_ID = $projectId AND
           (EXISTS (SELECT 1 FROM ROAD_ADDRESS RA WHERE RA.ID = ROAD_ADDRESS_ID AND
           RA.ROAD_NUMBER = ${reservedRoadPart.roadNumber} AND RA.ROAD_PART_NUMBER = ${reservedRoadPart.roadPartNumber}))
           OR (ROAD_NUMBER = ${reservedRoadPart.roadNumber} AND ROAD_PART_NUMBER = ${reservedRoadPart.roadPartNumber}
           AND (STATUS = ${LinkStatus.New.value} OR STATUS = ${LinkStatus.Numbering.value}))
           """.execute
    sqlu"""
         DELETE FROM PROJECT_RESERVED_ROAD_PART WHERE id = ${reservedRoadPart.id}
         """.execute
  }

  def removeReservedRoadPartsByProject(projectId: Long): Unit = {
    sqlu"""
         DELETE FROM PROJECT_RESERVED_ROAD_PART WHERE project_id = ${projectId}
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
      s"""SELECT id, state, name, created_by, created_date, start_date, modified_by, COALESCE(modified_date, created_date),
           add_info, ely, status_info, coord_x, coord_y, zoom
           FROM project $where"""
    Q.queryNA[(Long, Long, String, String, DateTime, DateTime, String, DateTime, String, Option[Long], Option[String], Double, Double, Int)](query).list.map {
      case (id, state, name, createdBy, createdDate, start_date, modifiedBy, modifiedDate, addInfo,
      ely, statusInfo, coordX, coordY, zoom) if ely.contains(-1L) =>
        RoadAddressProject(id, ProjectState.apply(state), name, createdBy, createdDate, modifiedBy, start_date, modifiedDate,
          addInfo, fetchReservedRoadParts(id), statusInfo, None, Some(ProjectCoordinates(coordX, coordY, zoom)))
      case (id, state, name, createdBy, createdDate, start_date, modifiedBy, modifiedDate, addInfo,
      ely, statusInfo, coordX, coordY, zoom) =>
        RoadAddressProject(id, ProjectState.apply(state), name, createdBy, createdDate, modifiedBy, start_date, modifiedDate,
          addInfo, fetchReservedRoadParts(id), statusInfo, ely, Some(ProjectCoordinates(coordX, coordY, zoom)))
    }.headOption
  }

  def fetchReservedRoadParts(projectId: Long): Seq[ReservedRoadPart] = {
    val sql =
      s"""
        SELECT id, road_number, road_part_number, length, length_new,
          ely, ely_new,
          (SELECT DISCONTINUITY FROM ROAD_ADDRESS ra WHERE ra.road_number = gr.road_number AND
            ra.road_part_number = gr.road_part_number AND RA.END_DATE IS NULL AND RA.VALID_TO IS NULL
            AND END_ADDR_M = gr.length and ROWNUM < 2) as discontinuity,
          (SELECT DISCONTINUITY_TYPE FROM PROJECT_LINK pl WHERE pl.project_id = gr.project_id
            AND pl.road_number = gr.road_number AND pl.road_part_number = gr.road_part_number
            AND PL.STATUS != 5 AND PL.TRACK_CODE IN (0,1)
            AND END_ADDR_M = gr.length_new AND ROWNUM < 2) as discontinuity_new,
          (SELECT LINK_ID FROM PROJECT_LINK pl JOIN LRM_POSITION lrm ON (lrm.id = pl.LRM_POSITION_ID)
            WHERE pl.project_id = gr.project_id
            AND pl.road_number = gr.road_number AND pl.road_part_number = gr.road_part_number
            AND PL.STATUS != 5 AND PL.TRACK_CODE IN (0,1) AND pl.START_ADDR_M = 0
            AND pl.END_ADDR_M > 0 AND ROWNUM < 2) as first_link
          FROM (
            SELECT rp.id, rp.project_id, rp.road_number, rp.road_part_number,
              MAX(ra.END_ADDR_M) as length,
              MAX(pl.END_ADDR_M) as length_new,
              MAX(ra.ely) as ELY,
              MAX(pl.ely) as ELY_NEW
              FROM PROJECT_RESERVED_ROAD_PART rp LEFT JOIN
              ROAD_ADDRESS ra ON (ra.road_number = rp.road_number AND ra.road_part_number = rp.road_part_number)
              LEFT JOIN
              PROJECT_LINK pl ON (pl.project_id = rp.project_id AND pl.road_number = rp.road_number AND
                pl.road_part_number = rp.road_part_number AND pl.status != 5)
              WHERE
                rp.project_id = $projectId AND
                RA.END_DATE IS NULL AND RA.VALID_TO IS NULL
                GROUP BY rp.id, rp.project_id, rp.road_number, rp.road_part_number
            ) gr"""
    Q.queryNA[(Long, Long, Long, Option[Long], Option[Long], Option[Long], Option[Long], Option[Long],
      Option[Long], Option[Long])](sql).list.map {
      case (id, road, part, length, newLength, ely, newEly, discontinuity, newDiscontinuity, linkId) =>
        ReservedRoadPart(id, road, part, length, discontinuity.map(Discontinuity.apply), ely, newLength,
          newDiscontinuity.map(Discontinuity.apply), newEly, linkId)
    }
  }

  def fetchReservedRoadPart(roadNumber: Long, roadPartNumber: Long): Option[ReservedRoadPart] = {
    val sql =
      s"""
        SELECT id, road_number, road_part_number, length, length_new,
          ely, ely_new,
          (SELECT DISCONTINUITY FROM ROAD_ADDRESS ra WHERE ra.road_number = gr.road_number AND
          ra.road_part_number = gr.road_part_number AND RA.END_DATE IS NULL AND RA.VALID_TO IS NULL
          AND END_ADDR_M = gr.length and ROWNUM < 2) as discontinuity,
          (SELECT DISCONTINUITY_TYPE FROM PROJECT_LINK pl WHERE pl.project_id = gr.project_id
          AND pl.road_number = gr.road_number AND pl.road_part_number = gr.road_part_number
          AND PL.STATUS != 5 AND PL.TRACK_CODE IN (0,1)
          AND END_ADDR_M = gr.length_new and ROWNUM < 2) as discontinuity_new,
          (SELECT LINK_ID FROM PROJECT_LINK pl JOIN LRM_POSITION lrm ON (lrm.id = pl.LRM_POSITION_ID)
            WHERE pl.project_id = gr.project_id
            AND pl.road_number = gr.road_number AND pl.road_part_number = gr.road_part_number
            AND PL.STATUS != 5 AND PL.TRACK_CODE IN (0,1) AND pl.START_ADDR_M = 0
            AND pl.END_ADDR_M > 0 AND ROWNUM < 2) as first_link
          FROM (
            SELECT rp.id, rp.project_id, rp.road_number, rp.road_part_number,
              MAX(ra.END_ADDR_M) as length,
              MAX(pl.END_ADDR_M) as length_new,
              MAX(ra.ely) as ELY,
              MAX(pl.ely) as ELY_NEW
              FROM PROJECT_RESERVED_ROAD_PART rp LEFT JOIN
              ROAD_ADDRESS ra ON (ra.road_number = rp.road_number AND ra.road_part_number = rp.road_part_number)
              LEFT JOIN
              PROJECT_LINK pl ON (pl.project_id = rp.project_id AND pl.road_number = rp.road_number AND pl.road_part_number = rp.road_part_number)
              WHERE
                rp.road_number = $roadNumber AND rp.road_part_number = $roadPartNumber AND
                RA.END_DATE IS NULL AND RA.VALID_TO IS NULL AND
                (PL.STATUS IS NULL OR (PL.STATUS != 5 AND PL.TRACK_CODE IN (0,1)))
              GROUP BY rp.id, rp.project_id, rp.road_number, rp.road_part_number
              ) gr"""
    Q.queryNA[(Long, Long, Long, Option[Long], Option[Long], Option[Long], Option[Long], Option[Long],
      Option[Long], Option[Long])](sql).firstOption.map {
      case (id, road, part, length, newLength, ely, newEly, discontinuity, newDiscontinuity, linkId) =>
        ReservedRoadPart(id, road, part, length, discontinuity.map(Discontinuity.apply), ely, newLength,
          newDiscontinuity.map(Discontinuity.apply), newEly, linkId)
    }
  }

  def getRoadAddressProjects(projectId: Long = 0, withNullElyFilter: Boolean = false): List[RoadAddressProject] = {
    val filter = projectId match {
      case 0 => if (withNullElyFilter) s""" where ELY IS NULL """ else ""
      case _ => if(withNullElyFilter) s""" where id =$projectId AND ELY IS NULL """ else s""" where id =$projectId """
    }

    val query =
      s"""SELECT id, state, name, created_by, created_date, start_date, modified_by, COALESCE(modified_date, created_date),
           add_info, status_info, ely, coord_x, coord_y, zoom
          FROM project $filter order by ely nulls first, name, id """
    Q.queryNA[(Long, Long, String, String, DateTime, DateTime, String, DateTime, String, Option[String], Option[Long], Double, Double, Int)](query).list.map {
      case (id, state, name, createdBy, createdDate, start_date, modifiedBy, modifiedDate, addInfo, statusInfo, ely, coordX, coordY, zoom) => {
        RoadAddressProject(id, ProjectState.apply(state), name, createdBy, createdDate, modifiedBy, start_date,
          modifiedDate, addInfo, fetchReservedRoadParts(id), statusInfo, ely, Some(ProjectCoordinates(coordX, coordY, zoom)))
      }
    }
  }

  def roadPartReservedTo(roadNumber: Long, roadPart: Long): Option[(Long, String)] = {
    val query =
      s"""SELECT p.id, p.name
              FROM project p
              JOIN PROJECT_RESERVED_ROAD_PART l
           ON l.PROJECT_ID =  p.ID
           WHERE l.road_number=$roadNumber AND road_part_number=$roadPart"""
    Q.queryNA[(Long, String)](query).firstOption
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

  def updateProjectLinksToTerminated(projectLinkIds: Set[Long], userName: String): Unit = {
    val user = userName.replaceAll("[^A-Za-z0-9\\-]+", "")
    projectLinkIds.grouped(500).foreach {
      grp =>
        val sql = s"UPDATE PROJECT_LINK SET STATUS = ${LinkStatus.Terminated.value}, CALIBRATION_POINTS = 0, MODIFIED_BY='$user' " +
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

  def updateProjectCoordinates(projectId: Long, coordinates: ProjectCoordinates) = {
    Q.updateNA(s"UPDATE PROJECT SET COORD_X = ${coordinates.x},COORD_Y = ${coordinates.y}, ZOOM = ${coordinates.zoom} WHERE ID= $projectId").execute
  }

  def getRotatingTRProjectId(projectId: Long) = {
    Q.queryNA[Long](s"Select tr_id From Project WHERE Id=$projectId AND tr_id IS NOT NULL ").list
  }


  def updateProjectLinkValues(projectId: Long, roadAddress: RoadAddress, updateGeom : Boolean = true) = {
    val updateGeometry = if(updateGeom) s" ,GEOMETRY = '${toGeomString(roadAddress.geometry)}'" else s""

    val updateProjectLink = s"UPDATE PROJECT_LINK SET ROAD_NUMBER = ${roadAddress.roadNumber}, " +
      s" ROAD_PART_NUMBER = ${roadAddress.roadPartNumber}, TRACK_CODE = ${roadAddress.track.value}, " +
      s" DISCONTINUITY_TYPE = ${roadAddress.discontinuity.value}, ROAD_TYPE = ${roadAddress.roadType.value}, " +
      s" STATUS = ${LinkStatus.NotHandled.value}, START_ADDR_M = ${roadAddress.startAddrMValue}, END_ADDR_M = ${roadAddress.endAddrMValue}, " +
      s" CALIBRATION_POINTS = ${CalibrationCode.getFromAddress(roadAddress).value}, CONNECTED_LINK_ID = null, REVERSED = 0 $updateGeometry" +
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
    *
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

  def reserveRoadPart(projectId: Long, roadNumber: Long, roadPartNumber: Long, user: String): Unit = {
    sqlu"""INSERT INTO PROJECT_RESERVED_ROAD_PART(id, road_number, road_part_number, project_id, created_by)
      SELECT viite_general_seq.nextval, $roadNumber, $roadPartNumber, $projectId, $user FROM DUAL""".execute
  }

  @Deprecated
  def updateReservedRoadPart(reserved: ReservedRoadPart): Unit = {
    // TODO: remove after current merges are done and no method calls this
    throw new RuntimeException("Deprecated method")
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

  implicit val getDiscontinuity = new GetResult[Option[Discontinuity]] {
    def apply(r: PositionedResult) = {
      r.nextLongOption().map(l => Discontinuity.apply(l))
    }
  }
}
