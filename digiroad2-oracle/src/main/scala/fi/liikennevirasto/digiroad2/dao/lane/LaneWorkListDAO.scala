package fi.liikennevirasto.digiroad2.dao.lane

import fi.liikennevirasto.digiroad2.dao.Sequences
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedResult, StaticQuery}

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
    val query = """SELECT id, link_id, property, old_value, new_value, modified_date FROM lane_work_list"""
    StaticQuery.queryNA[LaneWorkListItem](query)(getItemResult).iterator.toSeq
  }

  def insertItem(item: LaneWorkListItem): Long = {
    val id = Sequences.nextPrimaryKeySeqValue
    val linkId = item.linkId
    val propertyName = item.propertyName
    val oldValue = item.oldValue
    val newValue = item.newValue
    val modifiedDate = item.modifiedDate
    sqlu"""INSERT INTO lane_work_list (id, link_id, property, old_value, new_value, modified_date)
          values($id, $linkId, $propertyName, $oldValue, $newValue, $modifiedDate)"""

    id
  }

  def deleteItemsById(ids: Seq[Long]): Unit = {
    sqlu"""DELETE FROM lane_work_list WHERE id IN $ids""".execute
  }

}
