package fi.liikennevirasto.digiroad2

import org.scalatest.{Matchers, FunSuite}
import scala.slick.driver.JdbcDriver.backend.Database
import scala.slick.driver.JdbcDriver.backend.Database.dynamicSession

class TotalWeightLimitServiceSpec extends FunSuite with Matchers {

  object PassThroughService extends TotalWeightLimitOperations {
    override def withDynTransaction[T](f: => T): T = f
  }

  def runWithCleanup(test: => Unit): Unit = {
    Database.forDataSource(PassThroughService.dataSource).withDynTransaction {
      test
      dynamicSession.rollback()
    }
  }

  test("Expire weight limit") {
    runWithCleanup {
      PassThroughService.updateTotalWeightLimit(11111, None, true, "lol")
      val limit = PassThroughService.getById(11111)
      limit.get.limit should be (4000)
      limit.get.expired should be (true)
    }
  }

  test("Update weight limit") {
    runWithCleanup {
      PassThroughService.updateTotalWeightLimit(11111, Some(2000), false, "lol")
      val limit = PassThroughService.getById(11111)
      limit.get.limit should be (2000)
      limit.get.expired should be (false)
    }
  }
}
