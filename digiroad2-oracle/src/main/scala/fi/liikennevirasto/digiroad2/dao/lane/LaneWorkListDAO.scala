package fi.liikennevirasto.digiroad2.dao.lane

import fi.liikennevirasto.digiroad2.dao.Sequences
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedResult, StaticQuery}

import java.sql.{Date, Timestamp}
import java.time.LocalDateTime

case class LaneWorkListItem(id: Long, linkId: String, propertyName: String, oldValue: Int, newValue: Int,  createdDate: DateTime, createdBy: String)

class LaneWorkListDAO {

  implicit val getItemResult: GetResult[LaneWorkListItem] = new GetResult[LaneWorkListItem] {
    def apply(result: PositionedResult): LaneWorkListItem = {
      val id = result.nextLong()
      val linkId = result.nextString()
      val propertyName = result.nextString()
      val oldValue: Int = result.nextInt()
      val newValue: Int = result.nextInt()
      val createdDate: DateTime = new DateTime(result.nextTimestamp())
      val createdBy: String = result.nextString()

      LaneWorkListItem(id, linkId, propertyName, oldValue, newValue,  createdDate, createdBy)
    }
  }

  def getAllItems: Seq[LaneWorkListItem] = {
    sql"""SELECT id, link_id, property, old_value, new_value, created_date, created_by FROM lane_work_list""".as[LaneWorkListItem].list
  }

  def insertItem(item: LaneWorkListItem): Unit = {
    val id = Sequences.nextPrimaryKeySeqValue
    val linkId = item.linkId
    val propertyName = item.propertyName
    val oldValue = item.oldValue
    val newValue = item.newValue
    val createdBy = item.createdBy
    val createdDate = new Timestamp(item.createdDate.toDate.getTime)
    sqlu"""INSERT INTO lane_work_list (id, link_id, property, old_value, new_value, created_date, created_by)
          values($id, $linkId, $propertyName, $oldValue, $newValue, $createdDate, $createdBy)""".execute
  }

  def deleteItemsById(ids: Set[Long]): Unit = {
    sqlu"""DELETE FROM lane_work_list WHERE id IN (#${ids.mkString(",")})""".execute
  }

}
