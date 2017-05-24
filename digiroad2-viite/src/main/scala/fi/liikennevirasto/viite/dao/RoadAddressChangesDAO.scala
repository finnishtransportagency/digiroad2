package fi.liikennevirasto.viite.dao

import fi.liikennevirasto.viite.RoadType
import com.github.tototoshi.slick.MySQLJodaSupport._
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, StaticQuery => Q}

/**
  * Created by pedrosag on 16-05-2017.
  */

sealed trait ChangeType {
  def value: Int
}

object ChangeType {
  val values = Set(Unchanged, New, Transfer, Numeration, Expiration)

  def apply(intValue: Int): ChangeType = {
    values.find(_.value == intValue).getOrElse(Unknown)
  }

  case object Unchanged extends ChangeType { def value = 1 }
  case object New extends ChangeType { def value = 2 }
  case object Transfer extends ChangeType { def value = 3 }
  case object Numeration extends ChangeType { def value = 4 }
  case object Expiration extends ChangeType { def value = 5 }
  case object Unknown extends ChangeType { def value = 99 }

}

case class RoadAddressChangeRecipient(roadNumber: Option[Long], trackCode: Option[Long], startRoadPartNumber: Option[Long],
                                      endRoadPartNumber: Option[Long], startAddressM: Option[Long], endAddressM:Option[Long])
case class RoadAddressChangeInfo(changeType: ChangeType, source: RoadAddressChangeRecipient, target: RoadAddressChangeRecipient, discontinuity: Discontinuity, roadType: RoadType)
case class ProjectRoadAddressChange(projectId: Long, projectName: Option[String], ely: Long, user: String, changeDate: DateTime, changeInfo: RoadAddressChangeInfo)

object RoadAddressChangesDAO {

  implicit val getDiscontinuity = GetResult[Discontinuity]( r=> Discontinuity.apply(r.nextInt()))

  implicit val getChangeType = GetResult[ChangeType]( r=> ChangeType.apply(r.nextInt()))

  implicit val getRoadType = GetResult[RoadType]( r=> RoadType.apply(r.nextInt()))

  private def queryList(query: String) = {
    val tuples = Q.queryNA[(Long, Option[String], String, Option[DateTime],
      String, Option[DateTime], Long, Int, Option[Long], Option[Long],
      Option[Long], Option[Long], Option[Long], Option[Long], Option[Long],
      Option[Long], Option[Long], Option[Long], Option[Long], Option[Long], Int, Int)](query).list
      tuples.map{
      case (projectId, projectName, createdBy, createdDate, modifiedBy, modifiedDate, ely, changeType, sourceRoadNumber,
      sourceTrackCode ,sourceStartRoadPartNumber, sourceEndRoadPartNumber, sourceStartAddressM, sourceEndAddressM,
      targetRoadNumber, targetTrackCode, targetStartRoadPartNumber, targetEndRoadPartNumber, targetStartAddressM,
      targetEndAddressM, discontinuity, roadType) =>
        val source = RoadAddressChangeRecipient(sourceRoadNumber, sourceTrackCode, sourceStartRoadPartNumber, sourceEndRoadPartNumber, sourceStartAddressM, sourceEndAddressM)
        val target = RoadAddressChangeRecipient(targetRoadNumber, targetTrackCode, targetStartRoadPartNumber, targetEndRoadPartNumber, targetStartAddressM, targetEndAddressM)
        val changeInfo = RoadAddressChangeInfo(ChangeType.apply(changeType), source, target, Discontinuity.apply(discontinuity), RoadType.apply(roadType))
        val user = if(modifiedDate.isEmpty){
          createdBy
        } else {
          if(modifiedDate.get.isAfter(createdDate.get)){
            modifiedBy
          } else createdBy
        }
        val date = if(modifiedDate.isEmpty){
          createdDate.get
        } else {
          if(modifiedDate.get.isAfter(createdDate.get)){
            modifiedDate.get
          } else createdDate.get
        }
        ProjectRoadAddressChange(projectId, projectName, ely, user, date, changeInfo)
    }
  }

  def fetchRoadAddressChanges(projectIds: Set[Long]):List[ProjectRoadAddressChange] = {

    val projectIdsString = projectIds.mkString(", ")
    val withProjectIds = projectIds.isEmpty match {
      case true => return List()
      case false => s""" where rac.project_id in ($projectIdsString)"""
    }
    val query = s"""Select p.id as project_id, p.name, p.created_by, p.created_date, p.modified_by, p.modified_date, rac.new_ely, rac.change_type, rac.old_road_number, rac.old_track_code, Min(rac.old_road_part_number) as old_start_road_part_number, Max(rac.old_road_part_number) as old_end_road_part_number, rac.old_start_addr_m, rac.old_end_addr_m, rac.new_road_number, rac.new_track_code, Min(rac.new_road_part_number) as new_start_road_part_number, Max(rac.new_road_part_number) as new_end_road_part_number, rac.new_start_addr_m, rac.new_end_addr_m, rac.new_discontinuity, rac.new_road_type
                      From Road_Address_Changes rac Inner Join Project p on rac.project_id = p.id
                      $withProjectIds
                      Group By p.id, p.name, p.created_by, p.created_date, p.modified_by, p.modified_date, rac.new_ely, rac.change_type, rac.old_road_number, rac.old_track_code, rac.old_start_addr_m, rac.old_end_addr_m, rac.new_road_number, rac.new_track_code,
                      rac.new_start_addr_m, rac.new_end_addr_m, rac.new_discontinuity, rac.new_road_type"""
    queryList(query)
  }
}
