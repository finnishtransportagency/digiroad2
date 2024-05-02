package fi.liikennevirasto.digiroad2.dao.lane

import fi.liikennevirasto.digiroad2.dao.Sequences
import org.joda.time.DateTime
import org.json4s.{DefaultFormats, Formats}
import org.json4s.jackson.{JsonMethods, Serialization}
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedResult}

import java.sql.Timestamp

case class AutoProcessedLanesWorkListItem(id: Long, linkId: String, propertyName: String, oldValue: Int, newValue: Int,
                                          startDates: Seq[String], createdDate: DateTime, createdBy: String)

class AutoProcessedLanesWorkListDAO {
  protected implicit val jsonFormats: Formats = DefaultFormats

  implicit val getItemResult: GetResult[AutoProcessedLanesWorkListItem] = new GetResult[AutoProcessedLanesWorkListItem] {
    def apply(result: PositionedResult): AutoProcessedLanesWorkListItem = {
      val id = result.nextLong()
      val linkId = result.nextString()
      val propertyName = result.nextString()
      val oldValue: Int = result.nextInt()
      val newValue: Int = result.nextInt()
      val startDatesString = result.nextString()
      val createdDate: DateTime = new DateTime(result.nextTimestamp())
      val createdBy: String = result.nextString()

      val startDates = JsonMethods.parse(startDatesString).extract[Seq[String]]
      AutoProcessedLanesWorkListItem(id, linkId, propertyName, oldValue, newValue, startDates, createdDate, createdBy)
    }
  }

  def getAllItems: Seq[AutoProcessedLanesWorkListItem] = {
    sql"""SELECT id, link_id, property, old_value, new_value, start_dates, created_date, created_by FROM automatically_processed_lanes_work_list""".as[AutoProcessedLanesWorkListItem].list
  }

  def insertItem(item: AutoProcessedLanesWorkListItem): Unit = {
    val id = Sequences.nextPrimaryKeySeqValue
    val linkId = item.linkId
    val propertyName = item.propertyName
    val oldValue = item.oldValue
    val newValue = item.newValue
    val startDates = Serialization.write(item.startDates)(DefaultFormats)
    val createdBy = item.createdBy
    val createdDate = new Timestamp(item.createdDate.toDate.getTime)
    sqlu"""INSERT INTO automatically_processed_lanes_work_list (id, link_id, property, old_value, new_value, start_dates, created_date, created_by)
          values($id, $linkId, $propertyName, $oldValue, $newValue, $startDates, $createdDate, $createdBy)""".execute
  }

  def deleteItemsById(ids: Set[Long]): Unit = {
    sqlu"""DELETE FROM automatically_processed_lanes_work_list WHERE id IN (#${ids.mkString(",")})""".execute
  }
}
