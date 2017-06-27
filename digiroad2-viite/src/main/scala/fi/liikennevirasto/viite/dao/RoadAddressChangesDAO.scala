package fi.liikennevirasto.viite.dao

import fi.liikennevirasto.viite.RoadType
import com.github.tototoshi.slick.MySQLJodaSupport._
import fi.liikennevirasto.viite.process.{Delta, ProjectDeltaCalculator}
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedResult, StaticQuery => Q}

/**
  * Created by pedrosag on 16-05-2017.
  */

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

  case object Unchanged extends AddressChangeType { def value = 1 }
  case object New extends AddressChangeType { def value = 2 }
  case object Transfer extends AddressChangeType { def value = 3 }
  case object ReNumeration extends AddressChangeType { def value = 4 }
  case object Termination extends AddressChangeType { def value = 5 }
  case object Unknown extends AddressChangeType { def value = 99 }

}

case class RoadAddressChangeRecipient(roadNumber: Option[Long], trackCode: Option[Long], startRoadPartNumber: Option[Long],
                                      endRoadPartNumber: Option[Long], startAddressM: Option[Long], endAddressM:Option[Long])
case class RoadAddressChangeInfo(changeType: AddressChangeType, source: RoadAddressChangeRecipient, target: RoadAddressChangeRecipient, discontinuity: Discontinuity, roadType: RoadType)
case class ProjectRoadAddressChange(projectId: Long, projectName: Option[String], ely: Long, user: String, changeDate: DateTime, changeInfo: RoadAddressChangeInfo, projectStartDate: DateTime)
case class RoadAddressChangeRow(projectId:Long, projectName:Option[String], createdBy:String, createdDate:Option[DateTime], startDate:Option[DateTime], modifiedBy:String, modifiedDate:Option[DateTime], ely:Long, changeType :Int, sourceRoadNumber:Option[Long],
                                 sourceTrackCode :Option[Long],sourceStartRoadPartNumber:Option[Long], sourceEndRoadPartNumber:Option[Long], sourceStartAddressM:Option[Long], sourceEndAddressM:Option[Long],
                                 targetRoadNumber:Option[Long], targetTrackCode:Option[Long], targetStartRoadPartNumber:Option[Long], targetEndRoadPartNumber:Option[Long], targetStartAddressM:Option[Long],
                                 targetEndAddressM:Option[Long], discontinuity: Int, roadType: Int)

object RoadAddressChangesDAO {

  implicit val getDiscontinuity = GetResult[Discontinuity]( r=> Discontinuity.apply(r.nextInt()))

  implicit val getAddressChangeType = GetResult[AddressChangeType](r=> AddressChangeType.apply(r.nextInt()))

  implicit val getRoadType = GetResult[RoadType]( r=> RoadType.apply(r.nextInt()))

  implicit val getRoadAddressChangeRow = new GetResult[RoadAddressChangeRow] {
    def apply(r: PositionedResult) = {
      val projectId = r.nextLong
      val projectName = r.nextStringOption
      val createdBy = r.nextString
      val createdDate = r.nextDateOption.map(new DateTime(_))
      val startDate = r.nextDateOption.map(new DateTime(_))
      val modifiedBy = r.nextString
      val modifiedDate = r.nextDateOption.map(new DateTime(_))
      val ely = r.nextLong
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
      val discontinuity = r.nextInt
      val roadType = r.nextInt

      RoadAddressChangeRow(projectId, projectName:Option[String], createdBy:String, createdDate:Option[DateTime], startDate:Option[DateTime], modifiedBy:String, modifiedDate:Option[DateTime], ely:Long, changeType :Int, sourceRoadNumber:Option[Long],
        sourceTrackCode :Option[Long],sourceStartRoadPartNumber:Option[Long], sourceEndRoadPartNumber:Option[Long], sourceStartAddressM:Option[Long], sourceEndAddressM:Option[Long],
        targetRoadNumber:Option[Long], targetTrackCode:Option[Long], targetStartRoadPartNumber:Option[Long], targetEndRoadPartNumber:Option[Long], targetStartAddressM:Option[Long],
        targetEndAddressM:Option[Long], discontinuity: Int, roadType: Int)
    }
  }

  private def toRoadAddressChangeRecipient(row: RoadAddressChangeRow) = {
    RoadAddressChangeRecipient(row.sourceRoadNumber, row.sourceTrackCode, row.sourceStartRoadPartNumber, row.sourceEndRoadPartNumber, row.sourceStartAddressM, row.sourceEndAddressM)
  }
  private def toRoadAddressChangeInfo(row: RoadAddressChangeRow) = {
    val source = toRoadAddressChangeRecipient(row)
    val target = toRoadAddressChangeRecipient(row)
    RoadAddressChangeInfo(AddressChangeType.apply(row.changeType), source, target, Discontinuity.apply(row.discontinuity), RoadType.apply(row.roadType))
  }

  // TODO: cleanup after modification dates and modified by are populated correctly
  private def getUserAndModDate(row: RoadAddressChangeRow): (String, DateTime) = {
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
    val resultList = Q.queryNA[RoadAddressChangeRow](query).list
    resultList.map { row => {
      val changeInfo = toRoadAddressChangeInfo(row)
      val (user, date) = getUserAndModDate(row)
      ProjectRoadAddressChange(row.projectId, row.projectName, row.ely, user, date, changeInfo, row.startDate.get)
    }
    }
  }

  def fetchRoadAddressChanges(projectIds: Set[Long]):List[ProjectRoadAddressChange] = {

    val projectIdsString = projectIds.mkString(", ")
    val withProjectIds = projectIds.isEmpty match {
      case true => return List()
      case false => s""" where rac.project_id in ($projectIdsString)"""
    }
    val query = s"""Select p.id as project_id, p.name, p.created_by, p.created_date, p.start_date, p.modified_by, p.modified_date, rac.new_ely, rac.change_type, rac.old_road_number, rac.old_track_code, Min(rac.old_road_part_number) as old_start_road_part_number, Max(rac.old_road_part_number) as old_end_road_part_number, rac.old_start_addr_m, rac.old_end_addr_m, rac.new_road_number, rac.new_track_code, Min(rac.new_road_part_number) as new_start_road_part_number, Max(rac.new_road_part_number) as new_end_road_part_number, rac.new_start_addr_m, rac.new_end_addr_m, rac.new_discontinuity, rac.new_road_type
                      From Road_Address_Changes rac Inner Join Project p on rac.project_id = p.id
                      $withProjectIds
                      Group By p.id, p.name, p.created_by, p.created_date, p.start_date, p.modified_by, p.modified_date, rac.new_ely, rac.change_type, rac.old_road_number, rac.old_track_code, rac.old_start_addr_m, rac.old_end_addr_m, rac.new_road_number, rac.new_track_code,
                      rac.new_start_addr_m, rac.new_end_addr_m, rac.new_discontinuity, rac.new_road_type, rac.OLD_ROAD_PART_NUMBER ORDER BY rac.old_road_number, rac.OLD_ROAD_PART_NUMBER, rac.old_start_addr_m, rac.old_track_code DESC"""
  queryList(query)
  }

  def clearRoadChangeTable(projectId: Long): Unit = {
    sqlu"""DELETE FROM ROAD_ADDRESS_CHANGES WHERE project_id = $projectId""".execute
  }

  def insertDeltaToRoadChangeTable(delta: Delta, projectId: Long): Boolean= {
    val roadType = 9 //TODO missing
    ProjectDAO.getRoadAddressProjectById(projectId) match {
      case Some(project) => {
        project.ely match {
          case Some(ely) => {
            val roadAddressChangePS = dynamicSession.prepareStatement("INSERT INTO ROAD_ADDRESS_CHANGES " +
              "(project_id,change_type,old_road_number,new_road_number,old_road_part_number,new_road_part_number, " +
              "old_track_code,new_track_code,old_start_addr_m,new_start_addr_m,old_end_addr_m,new_end_addr_m," +
              "new_discontinuity,new_road_type,new_ely) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,? )")
            ProjectDeltaCalculator.partition(delta.terminations).foreach { case (roadAddressSection) =>
              roadAddressChangePS.setLong(1, projectId)
              roadAddressChangePS.setLong(2, AddressChangeType.Termination.value)
              roadAddressChangePS.setLong(3, roadAddressSection.roadNumber)
              roadAddressChangePS.setLong(4, roadAddressSection.roadNumber)
              roadAddressChangePS.setLong(5, roadAddressSection.roadPartNumberStart)
              roadAddressChangePS.setLong(6, roadAddressSection.roadPartNumberStart)
              roadAddressChangePS.setLong(7, roadAddressSection.track.value)
              roadAddressChangePS.setLong(8, roadAddressSection.track.value)
              roadAddressChangePS.setDouble(9, roadAddressSection.startMAddr)
              roadAddressChangePS.setDouble(10, roadAddressSection.startMAddr)
              roadAddressChangePS.setDouble(11, roadAddressSection.endMAddr)
              roadAddressChangePS.setDouble(12, roadAddressSection.endMAddr)
              roadAddressChangePS.setLong(13, roadAddressSection.discontinuity.value)
              roadAddressChangePS.setLong(14, roadType)
              roadAddressChangePS.setLong(15, ely)
              roadAddressChangePS.addBatch()
            }
            roadAddressChangePS.executeBatch()
            roadAddressChangePS.close()
            true
          }
          case _=>  false
        }
      } case _=> false
    }
  }

}
