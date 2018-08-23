package fi.liikennevirasto.digiroad2.service

import fi.liikennevirasto.digiroad2.dao.UserNotificationDao
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import org.joda.time.DateTime

case class UserNotification(id: Long, createdDate: DateTime, heading: String, content: String)

class UserNotificationService() {

  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  def dao: UserNotificationDao = new UserNotificationDao

  def getAllUserNotifications: Seq[UserNotification] = {
    withDynSession {
      dao.getAllUserNotifications
    }
  }
}
