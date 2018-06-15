package fi.liikennevirasto.digiroad2.service

import com.jolbox.bonecp.{BoneCPConfig, BoneCPDataSource}
import fi.liikennevirasto.digiroad2.dao.UserNotificationDao
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.util.TestTransactions
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation

class UserNotificationServiceSpec extends FunSuite with Matchers {

  lazy val dataSource = {
    val cfg = new BoneCPConfig(OracleDatabase.loadProperties("/bonecp.properties"))
    new BoneCPDataSource(cfg)
  }

  object ServiceWithDao extends UserNotificationService(){
    override def withDynTransaction[T](f: => T): T = f
    override def withDynSession[T](f: => T): T = f
    override def dao: UserNotificationDao = new UserNotificationDao
  }

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback(dataSource)(test)

  test("get user notifications info"){
    runWithRollback {
      val header1 = "Neque porro quisquam est qui dolorem ipsum quia dolor sit amet, consectetur, adipisci velit. - 1 -"
      val content1 = "<p>Lorem ipsum dolor sit amet, consectetur adipiscing elit. Morbi in porttitor orci, vel consectetur ante. " +
        "Etiam nec mauris vel urna facilisis pretium. Nulla pulvinar pharetra finibus. Aenean eget molestie nibh. Nunc vitae nibh " +
        "eget leo ultricies iaculis vel sed ligula. Morbi id sagittis nunc. Phasellus gravida, lacus quis maximus venenatis, nibh " +
        "odio viverra nisi, ac vehicula dui lorem iaculis augue. Nulla at lacus lacus.</p>" +
        "<p>Nulla tortor dui, eleifend et varius vitae, faucibus eget felis. Cras blandit volutpat posuere. Donec imperdiet tortor " +
        "rutrum pellentesque luctus. Etiam leo tortor, congue in sodales at, cursus eget eros. Vivamus semper sodales lacus nec " +
        "tristique. Cras eget ultricies felis. Morbi porttitor blandit metus ac pellentesque. Vivamus a dolor posuere, viverra elit " +
        "a, bibendum urna. Aenean nec interdum nisi, sodales gravida nibh. Pellentesque pulvinar odio sit amet quam fermentum, at " +
        "tempor massa sodales. Sed elementum nunc a nisl tincidunt suscipit. Morbi at turpis enim.</p>"

      val header2 = "Neque porro quisquam est qui dolorem ipsum quia dolor sit amet, consectetur, adipisci velit. - 2 -"
      val content2 = "<p>Sed facilisis lacus nec scelerisque tempus. Quisque bibendum pulvinar accumsan. Pellentesque habitant morbi " +
        "tristique senectus et netus et malesuada fames ac turpis egestas. Praesent scelerisque egestas felis id tristique. Curabitur " +
        "bibendum faucibus est. Quisque ut pellentesque neque, a gravida purus. Duis eget condimentum eros. In bibendum mattis est " +
        "eu volutpat. Nam dignissim vestibulum nulla, ac commodo urna pulvinar vitae. Mauris eget augue suscipit, tempor tortor nec, " +
        "malesuada metus. Vivamus semper id leo id vestibulum. Praesent at nisl vel lacus molestie bibendum. Praesent et elementum lacus, " +
        "pretium consequat sem. Nunc eget sem non purus tempus vehicula at a arcu. Integer condimentum, ligula sed vehicula aliquam, est " +
        "eros molestie lorem, ut aliquet risus velit a ligula.</p>"

      val id1 = sql"""select notification_seq.nextval from dual""".as[Long].first
      sqlu"""insert into notification (id, heading, content)
      values ( $id1, $header1,  $content1)""".execute
      val id2 = sql"""select notification_seq.nextval from dual""".as[Long].first
      sqlu"""insert into notification (id, created_date, heading, content)
      values ( $id2, (sysdate - interval '1' month ), $header2,  $content2)""".execute

      val userNotificationInfo = ServiceWithDao.getAllUserNotifications
      userNotificationInfo should have size 2
      userNotificationInfo.head.heading equals header2
      userNotificationInfo.head.content equals  content2
      userNotificationInfo.last.heading equals  header1
      userNotificationInfo.last.content equals  content1

    }
  }
}
