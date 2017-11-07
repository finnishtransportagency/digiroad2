package fi.liikennevirasto.viite.dao

import java.sql.PreparedStatement

import fi.liikennevirasto.viite.RoadType
import com.github.tototoshi.slick.MySQLJodaSupport._
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.viite.process.{Delta, ProjectDeltaCalculator, RoadAddressSection}
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedResult, StaticQuery => Q}


sealed trait AddressChangeType {
  def value: Int
}

object AddressChangeType {
  val values = Set(Unchanged, New, Transfer, ReNumeration, Termination)

  def apply(intValue: Int): AddressChangeType = {
    values.find(_.value == intValue).getOrElse(Unknown)
  }

  /*
      Unchanged is a no-operation, tells TR that some road part or section stays intact but it needs
        to be included in the message for other changes
      New is a road address placing to a road that did not have road address before
      Transfer is an adjustment of a road address, such as extending a road 100 meters from the start:
        all the addresses on the first part are transferred with +100 to each start and end address M values.
      ReNumeration is a change in road addressing but no physical or length changes. A road part gets a new
        road and/or road part number.
      Termination is for ending a road address (and possibly assigning the previously used road address
        to a new physical location at the same time)
   */

  case object NotHandled extends AddressChangeType { def value = 0 }
  case object Unchanged extends AddressChangeType { def value = 1 }
  case object New extends AddressChangeType { def value = 2 }
  case object Transfer extends AddressChangeType { def value = 3 }
  case object ReNumeration extends AddressChangeType { def value = 4 }
  case object Termination extends AddressChangeType { def value = 5 }
  case object Unknown extends AddressChangeType { def value = 99 }

}

case class RoadAddressChangeSection(roadNumber: Option[Long], trackCode: Option[Long], startRoadPartNumber: Option[Long],
                                    endRoadPartNumber: Option[Long], startAddressM: Option[Long], endAddressM:Option[Long], roadType: Option[RoadType], discontinuity: Option[Discontinuity], ely: Option[Long])
case class RoadAddressChangeSectionTR(roadNumber: Option[Long], trackCode: Option[Long], startRoadPartNumber: Option[Long],
                                    endRoadPartNumber: Option[Long], startAddressM: Option[Long], endAddressM:Option[Long])

case class RoadAddressChangeInfo(changeType: AddressChangeType, source: RoadAddressChangeSection, target: RoadAddressChangeSection,
                                 discontinuity: Discontinuity, roadType: RoadType, reversed: Boolean)
case class ProjectRoadAddressChange(projectId: Long, projectName: Option[String], ely: Long, user: String, changeDate: DateTime,
                                    changeInfo: RoadAddressChangeInfo, projectStartDate: DateTime, rotatingTRId:Option[Long])
case class ChangeRow(projectId: Long, projectName: Option[String], createdBy: String, createdDate: Option[DateTime],
                     startDate: Option[DateTime], modifiedBy: String, modifiedDate: Option[DateTime], targetEly: Long,
                     changeType: Int, sourceRoadNumber: Option[Long], sourceTrackCode: Option[Long],
                     sourceStartRoadPartNumber: Option[Long], sourceEndRoadPartNumber: Option[Long],
                     sourceStartAddressM: Option[Long], sourceEndAddressM: Option[Long], targetRoadNumber: Option[Long],
                     targetTrackCode: Option[Long], targetStartRoadPartNumber: Option[Long], targetEndRoadPartNumber: Option[Long],
                     targetStartAddressM:Option[Long], targetEndAddressM:Option[Long], targetDiscontinuity: Option[Int], targetRoadType: Option[Int],
                     sourceRoadType: Option[Int], sourceDiscontinuity: Option[Int], sourceEly: Option[Long],
                     rotatingTRId: Option[Long], reversed: Boolean)

object RoadAddressChangesDAO {

  implicit val getDiscontinuity = GetResult[Discontinuity]( r=> Discontinuity.apply(r.nextInt()))

  implicit val getAddressChangeType = GetResult[AddressChangeType](r=> AddressChangeType.apply(r.nextInt()))

  implicit val getRoadType = GetResult[RoadType]( r=> RoadType.apply(r.nextInt()))

  implicit val getRoadAddressChangeRow = new GetResult[ChangeRow] {
    def apply(r: PositionedResult) = {
      val projectId = r.nextLong
      val projectName = r.nextStringOption
      val createdBy = r.nextString
      val createdDate = r.nextDateOption.map(new DateTime(_))
      val startDate = r.nextDateOption.map(new DateTime(_))
      val modifiedBy = r.nextString
      val modifiedDate = r.nextDateOption.map(new DateTime(_))
      val targetEly = r.nextLong
      val changeType = r.nextInt
      val sourceRoadNumber = r.nextLongOption
      val sourceTrackCode = r.nextLongOption
      val sourceStartRoadPartNumber = r.nextLongOption
      val sourceEndRoadPartNumber = r.nextLongOption
      val sourceStartAddressM = r.nextLongOption
      val sourceEndAddressM = r.nextLongOption
      val targetRoadNumber = r.nextLongOption
      val targetTrackCode = r.nextLongOption
      val targetStartRoadPartNumber = r.nextLongOption
      val targetEndRoadPartNumber = r.nextLongOption
      val targetStartAddressM = r.nextLongOption
      val targetEndAddressM = r.nextLongOption
      val targetDiscontinuity = r.nextIntOption
      val targetRoadType = r.nextIntOption
      val sourceRoadType = r.nextIntOption
      val sourceDiscontinuity = r.nextIntOption
      val sourceEly = r.nextLongOption
      val rotatingTRIdr = r.nextLongOption
      val reversed = r.nextBoolean

      ChangeRow(projectId, projectName:Option[String], createdBy:String, createdDate:Option[DateTime], startDate:Option[DateTime], modifiedBy:String, modifiedDate:Option[DateTime], targetEly:Long, changeType :Int, sourceRoadNumber:Option[Long],
        sourceTrackCode :Option[Long],sourceStartRoadPartNumber:Option[Long], sourceEndRoadPartNumber:Option[Long], sourceStartAddressM:Option[Long], sourceEndAddressM:Option[Long],
        targetRoadNumber:Option[Long], targetTrackCode:Option[Long], targetStartRoadPartNumber:Option[Long], targetEndRoadPartNumber:Option[Long], targetStartAddressM:Option[Long],
        targetEndAddressM:Option[Long], targetDiscontinuity: Option[Int], targetRoadType: Option[Int], sourceRoadType: Option[Int], sourceDiscontinuity: Option[Int], sourceEly: Option[Long],
        rotatingTRIdr:Option[Long], reversed: Boolean)
    }
  }

  val logger = LoggerFactory.getLogger(getClass)

  private def toRoadAddressChangeRecipient(row: ChangeRow) = {
    RoadAddressChangeSection(row.targetRoadNumber, row.targetTrackCode, row.targetStartRoadPartNumber, row.targetEndRoadPartNumber, row.targetStartAddressM, row.targetEndAddressM,
      Some(RoadType.apply(row.targetRoadType.getOrElse(RoadType.Unknown.value))), Some(Discontinuity.apply(row.targetDiscontinuity.getOrElse(Discontinuity.Continuous.value))), Some(row.targetEly) )
  }
  private def toRoadAddressChangeSource(row: ChangeRow) = {
    RoadAddressChangeSection(row.sourceRoadNumber, row.sourceTrackCode, row.sourceStartRoadPartNumber, row.sourceEndRoadPartNumber, row.sourceStartAddressM, row.sourceEndAddressM,
      Some(RoadType.apply(row.sourceRoadType.getOrElse(RoadType.Unknown.value))), Some(Discontinuity.apply(row.sourceDiscontinuity.getOrElse(Discontinuity.Continuous.value))), row.sourceEly)
  }
  private def toRoadAddressChangeInfo(row: ChangeRow) = {
    val source = toRoadAddressChangeSource(row)
    val target = toRoadAddressChangeRecipient(row)
    RoadAddressChangeInfo(AddressChangeType.apply(row.changeType), source, target, Discontinuity.apply(row.targetDiscontinuity.getOrElse(Discontinuity.Continuous.value)), RoadType.apply(row.targetRoadType.getOrElse(RoadType.Unknown.value)), row.reversed)
  }

  // TODO: cleanup after modification dates and modified by are populated correctly
  private def getUserAndModDate(row: ChangeRow): (String, DateTime) = {
    val user = if (row.modifiedDate.isEmpty) {
      row.createdBy
    } else {
      if (row.modifiedDate.get.isAfter(row.createdDate.get)) {
        // modifiedBy currently always returns empty
        row.createdBy
      } else row.createdBy
    }
    val date = if (row.modifiedDate.isEmpty) {
      row.createdDate.get
    } else {
      if (row.modifiedDate.get.isAfter(row.createdDate.get)) {
        row.modifiedDate.get
      } else row.createdDate.get
    }
    (user, date)
  }

  private def queryList(query: String) = {
    val resultList = Q.queryNA[ChangeRow](query).list
    resultList.map { row => {
      val changeInfo = toRoadAddressChangeInfo(row)
      val (user, date) = getUserAndModDate(row)
      ProjectRoadAddressChange(row.projectId, row.projectName, row.targetEly, user, date, changeInfo, row.startDate.get,
        row.rotatingTRId)
    }
    }
  }

  def fetchRoadAddressChanges(projectIds: Set[Long]):List[ProjectRoadAddressChange] = {
    if (projectIds.isEmpty)
      return List()
    val projectIdsString = projectIds.mkString(",")
    val withProjectIds = s""" where rac.project_id in ($projectIdsString)"""
    val query = s"""Select p.id as project_id, p.name, p.created_by, p.created_date, p.start_date, p.modified_by,
                p.modified_date, rac.new_ely, rac.change_type, rac.old_road_number, rac.old_track_code,
                rac.old_road_part_number, rac.old_road_part_number,
                rac.old_start_addr_m, rac.old_end_addr_m, rac.new_road_number, rac.new_track_code,
                rac.new_road_part_number, rac.new_road_part_number,
                rac.new_start_addr_m, rac.new_end_addr_m, rac.new_discontinuity, rac.new_road_type, rac.old_road_type,
                rac.old_discontinuity, rac.old_ely, p.tr_id, rac.reversed
                From Road_Address_Changes rac Inner Join Project p on rac.project_id = p.id
                $withProjectIds
                ORDER BY rac.old_road_number, rac.OLD_ROAD_PART_NUMBER, rac.old_start_addr_m, rac.old_track_code DESC"""
    queryList(query)
  }

  def clearRoadChangeTable(projectId: Long): Unit = {
    sqlu"""DELETE FROM ROAD_ADDRESS_CHANGES WHERE project_id = $projectId""".execute
  }

  def insertDeltaToRoadChangeTable(delta: Delta, projectId: Long): Boolean= {
    def addToBatch(roadAddressSection: RoadAddressSection, ely: Long, addressChangeType: AddressChangeType,
                   roadAddressChangePS: PreparedStatement) = {
      addressChangeType match {
        case AddressChangeType.New =>
          roadAddressChangePS.setNull(3, java.sql.Types.INTEGER)
          roadAddressChangePS.setLong(4, roadAddressSection.roadNumber)
          roadAddressChangePS.setNull(5, java.sql.Types.INTEGER)
          roadAddressChangePS.setLong(6, roadAddressSection.roadPartNumberStart)
          roadAddressChangePS.setNull(7, java.sql.Types.INTEGER)
          roadAddressChangePS.setLong(8, roadAddressSection.track.value)
          roadAddressChangePS.setNull(9, java.sql.Types.INTEGER)
          roadAddressChangePS.setLong(10, roadAddressSection.startMAddr)
          roadAddressChangePS.setNull(11, java.sql.Types.INTEGER)
          roadAddressChangePS.setLong(12, roadAddressSection.endMAddr)
        case AddressChangeType.Termination =>
          roadAddressChangePS.setLong(3, roadAddressSection.roadNumber)
          roadAddressChangePS.setNull(4, java.sql.Types.INTEGER)
          roadAddressChangePS.setLong(5, roadAddressSection.roadPartNumberStart)
          roadAddressChangePS.setNull(6, java.sql.Types.INTEGER)
          roadAddressChangePS.setLong(7, roadAddressSection.track.value)
          roadAddressChangePS.setNull(8, java.sql.Types.INTEGER)
          roadAddressChangePS.setLong(9, roadAddressSection.startMAddr)
          roadAddressChangePS.setNull(10, java.sql.Types.INTEGER)
          roadAddressChangePS.setLong(11, roadAddressSection.endMAddr)
          roadAddressChangePS.setNull(12, java.sql.Types.INTEGER)
        case _ =>
          roadAddressChangePS.setLong(3, roadAddressSection.roadNumber)
          roadAddressChangePS.setLong(4, roadAddressSection.roadNumber)
          roadAddressChangePS.setLong(5, roadAddressSection.roadPartNumberStart)
          roadAddressChangePS.setLong(6, roadAddressSection.roadPartNumberStart)
          roadAddressChangePS.setLong(7, roadAddressSection.track.value)
          roadAddressChangePS.setLong(8, roadAddressSection.track.value)
          roadAddressChangePS.setLong(9, roadAddressSection.startMAddr)
          roadAddressChangePS.setLong(10, roadAddressSection.startMAddr)
          roadAddressChangePS.setLong(11, roadAddressSection.endMAddr)
          roadAddressChangePS.setLong(12, roadAddressSection.endMAddr)
      }
      roadAddressChangePS.setLong(1, projectId)
      roadAddressChangePS.setLong(2, addressChangeType.value)
      roadAddressChangePS.setLong(13, roadAddressSection.discontinuity.value)
      roadAddressChangePS.setLong(14, roadAddressSection.roadType.value)
      roadAddressChangePS.setLong(15, ely)
      roadAddressChangePS.setLong(16, roadAddressSection.roadType.value)
      roadAddressChangePS.setLong(17, roadAddressSection.discontinuity.value)
      roadAddressChangePS.setLong(18, ely)
      roadAddressChangePS.setLong(19, if (roadAddressSection.reversed) 1 else 0)
      roadAddressChangePS.addBatch()
    }

    def addToBatchWithOldValues(oldRoadAddressSection: RoadAddressSection, newRoadAddressSection:RoadAddressSection, ely: Long, addressChangeType: AddressChangeType, roadAddressChangePS: PreparedStatement) = {
      roadAddressChangePS.setLong(1, projectId)
      roadAddressChangePS.setLong(2, addressChangeType.value)
      roadAddressChangePS.setLong(3, oldRoadAddressSection.roadNumber)
      roadAddressChangePS.setLong(4, newRoadAddressSection.roadNumber)
      roadAddressChangePS.setLong(5, oldRoadAddressSection.roadPartNumberStart)
      roadAddressChangePS.setLong(6, newRoadAddressSection.roadPartNumberStart)
      roadAddressChangePS.setLong(7, oldRoadAddressSection.track.value)
      roadAddressChangePS.setLong(8, newRoadAddressSection.track.value)
      roadAddressChangePS.setDouble(9, oldRoadAddressSection.startMAddr)
      roadAddressChangePS.setDouble(10, newRoadAddressSection.startMAddr)
      roadAddressChangePS.setDouble(11, oldRoadAddressSection.endMAddr)
      roadAddressChangePS.setDouble(12, newRoadAddressSection.endMAddr)
      roadAddressChangePS.setLong(13, newRoadAddressSection.discontinuity.value)
      roadAddressChangePS.setLong(14, newRoadAddressSection.roadType.value)
      roadAddressChangePS.setLong(15, ely)
      roadAddressChangePS.setLong(16, oldRoadAddressSection.roadType.value)
      roadAddressChangePS.setLong(17, oldRoadAddressSection.discontinuity.value)
      roadAddressChangePS.setLong(18, oldRoadAddressSection.ely)
      roadAddressChangePS.setLong(19, if(newRoadAddressSection.reversed) 1 else 0)
      roadAddressChangePS.addBatch()
    }

    val startTime = System.currentTimeMillis()
    logger.info("Starting delta insertion in ChangeTable ")
    ProjectDAO.getRoadAddressProjectById(projectId) match {
      case Some(project) => {
        project.ely match {
          case Some(ely) => {
            val roadAddressChangePS = dynamicSession.prepareStatement("INSERT INTO ROAD_ADDRESS_CHANGES " +
              "(project_id,change_type,old_road_number,new_road_number,old_road_part_number,new_road_part_number, " +
              "old_track_code,new_track_code,old_start_addr_m,new_start_addr_m,old_end_addr_m,new_end_addr_m," +
              "new_discontinuity,new_road_type,new_ely, old_road_type, old_discontinuity, old_ely, reversed) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")
            ProjectDeltaCalculator.partition(delta.terminations).foreach { case (roadAddressSection) =>
              addToBatch(roadAddressSection, ely, AddressChangeType.Termination, roadAddressChangePS)
            }
            ProjectDeltaCalculator.partition(delta.newRoads).foreach { case (roadAddressSection) =>
              addToBatch(roadAddressSection, ely, AddressChangeType.New, roadAddressChangePS)
            }
            ProjectDeltaCalculator.partition(delta.unChanged.mapping).foreach { case (roadAddressSection1, roadAddressSection2) =>
              addToBatchWithOldValues(roadAddressSection1, roadAddressSection2, ely, AddressChangeType.Unchanged, roadAddressChangePS)
            }

            ProjectDeltaCalculator.partition(delta.transferred.mapping).foreach{ case (roadAddressSection1, roadAddressSection2) =>
              addToBatchWithOldValues(roadAddressSection1, roadAddressSection2 , ely, AddressChangeType.Transfer, roadAddressChangePS)
            }

            ProjectDeltaCalculator.partition(delta.numbering.mapping).foreach{ case (roadAddressSection1, roadAddressSection2) =>
              addToBatchWithOldValues(roadAddressSection1, roadAddressSection2, ely, AddressChangeType.ReNumeration, roadAddressChangePS)
            }

            roadAddressChangePS.executeBatch()
            roadAddressChangePS.close()
            val endTime = System.currentTimeMillis()
            logger.info("Ended delta insertion in ChangeTable in %.3f sec".format((endTime - startTime)*0.001))
            true
          }
          case _=>  false
        }
      } case _=> false
    }
  }
}
