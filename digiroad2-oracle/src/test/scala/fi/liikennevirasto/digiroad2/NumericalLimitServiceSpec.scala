package fi.liikennevirasto.digiroad2

import org.scalatest.{Matchers, FunSuite}
import scala.slick.driver.JdbcDriver.backend.Database
import scala.slick.driver.JdbcDriver.backend.Database.dynamicSession

class NumericalLimitServiceSpec extends FunSuite with Matchers {

  object PassThroughService extends NumericalLimitOperations {
    override def withDynTransaction[T](f: => T): T = f
  }

  def runWithCleanup(test: => Unit): Unit = {
    Database.forDataSource(PassThroughService.dataSource).withDynTransaction {
      test
      dynamicSession.rollback()
    }
  }

  test("Expire numerical limit") {
    runWithCleanup {
      PassThroughService.updateNumericalLimit(11111, None, true, "lol")
      val limit = PassThroughService.getById(11111)
      limit.get.value should be (4000)
      limit.get.expired should be (true)
    }
  }

  test("Update numerical limit") {
    runWithCleanup {
      PassThroughService.updateNumericalLimit(11111, Some(2000), false, "lol")
      val limit = PassThroughService.getById(11111)
      limit.get.value should be (2000)
      limit.get.expired should be (false)
    }
  }
}
