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
      val headerFirst = "Regione expetenda vituperatoribus mei no. Ei nam summo iusto, timeam."
      val contentFirst = "<p>Te velit mazim usu! Augue fierent ut vix, eu ius meis minim consequuntur. Tibique gubergren mnesarchum id sea? Cu aperiam maiestatis " +
      "posidonium eam, posse libris dignissim nec an, sea summo erroribus ocurreret ex? Eripuit referrentur consectetuer usu in, pri id affert " +
      "petentium accusamus!<//p><p>Duo no essent malorum theophrastus. Eos magna atqui an, duo congue putent tritani ea. At iudicabit torquatos " +
      "est, ad duis solum suscipit mei! Epicuri accumsan vituperata in vix! Ut dicat offendit mea, novum consul vidisse sit at! Et est pertinax " +
      "dissentiet, usu case delectus ex, ut animal tamquam usu. Graeco scriptorem cum ea.<//p>"

      val headerLast = "'Pri te mediocrem adipiscing, est ea lobortis quaestio electram. Mea in dissentias reformidans signiferumque, no eam melius tincidunt. At nam."
      val contentLast = "<p>Sed facilisis lacus nec scelerisque tempus. Quisque bibendum pulvinar accumsan. Pellentesque habitant morbi " +
        "tristique senectus et netus et malesuada fames ac turpis egestas. Praesent scelerisque egestas felis id tristique. Curabitur " +
        "bibendum faucibus est. Quisque ut pellentesque neque, a gravida purus. Duis eget condimentum eros. In bibendum mattis est " +
        "eu volutpat. Nam dignissim vestibulum nulla, ac commodo urna pulvinar vitae. Mauris eget augue suscipit, tempor tortor nec, " +
        "malesuada metus. Vivamus semper id leo id vestibulum. Praesent at nisl vel lacus molestie bibendum. Praesent et elementum lacus, " +
        "pretium consequat sem. Nunc eget sem non purus tempus vehicula at a arcu. Integer condimentum, ligula sed vehicula aliquam, est " +
        "eros molestie lorem, ut aliquet risus velit a ligula.</p>"

      val userNotificationInfo = ServiceWithDao.getAllUserNotifications
      userNotificationInfo should have size 3
      userNotificationInfo.head.heading equals headerFirst
      userNotificationInfo.head.content equals headerFirst
      userNotificationInfo.last.heading equals headerLast
      userNotificationInfo.last.content equals contentLast
    }
  }
}
