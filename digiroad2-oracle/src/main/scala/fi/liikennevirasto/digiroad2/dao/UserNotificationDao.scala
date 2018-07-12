package fi.liikennevirasto.digiroad2.dao

import fi.liikennevirasto.digiroad2.service.UserNotification
import org.joda.time.DateTime
import slick.jdbc.{GetResult, PositionedResult}
import slick.jdbc.StaticQuery.interpolation
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession

class UserNotificationDao {

  implicit val getUserNotification = new GetResult[UserNotification] {
    def apply(r: PositionedResult) = {
      val id = r.nextLong()
      val createdDate = new DateTime(r.nextDate())
      val heading = r.nextString()
      val content = r.nextString()

      UserNotification(id, createdDate, heading, content)
    }
  }

  def getAllUserNotifications : Seq[UserNotification] = {
    sql"""
       SELECT id, created_date, heading, content
       FROM user_notification
       ORDER BY created_date DESC
      """.as[UserNotification].list
  }
}
