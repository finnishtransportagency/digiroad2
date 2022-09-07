package fi.liikennevirasto.digiroad2.dao.lane

import fi.liikennevirasto.digiroad2.dao.Sequences
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedResult, StaticQuery}

import java.sql.Date

case class LaneWorkListItem(id: Long, linkId: Long, propertyName: String, oldValue: Int, newValue: Int, modifiedDate: DateTime)

class LaneWorkListDAO {

  implicit val getItemResult: GetResult[LaneWorkListItem] = new GetResult[LaneWorkListItem] {
    def apply(result: PositionedResult): LaneWorkListItem = {
      val id = result.nextLong()
      val linkId = result.nextLong()
      val propertyName = result.nextString()
      val oldValue: Int = result.nextInt()
      val newValue: Int = result.nextInt()
      val modifiedDate: DateTime = new DateTime(result.nextDate())

      LaneWorkListItem(id, linkId, propertyName, oldValue, newValue, modifiedDate)
    }
  }

  def getAllItems: Seq[LaneWorkListItem] = {
    sql"""SELECT id, link_id, property, old_value, new_value, modified_date FROM lane_work_list""".as[LaneWorkListItem].list
  }

  def insertItem(item: LaneWorkListItem): Unit = {
    val id = Sequences.nextPrimaryKeySeqValue
    val linkId = item.linkId
    val propertyName = item.propertyName
    val oldValue = item.oldValue
    val newValue = item.newValue
    val modifiedDate = new Date(item.modifiedDate.toDate.getTime)
    sqlu"""INSERT INTO lane_work_list (id, link_id, property, old_value, new_value, modified_date)
          values($id, $linkId, $propertyName, $oldValue, $newValue, $modifiedDate)""".execute
  }

  def deleteItemsById(ids: Set[Long]): Unit = {
    sqlu"""DELETE FROM lane_work_list WHERE id IN (#${ids.mkString(",")})""".execute
  }

}
