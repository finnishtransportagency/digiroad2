package fi.liikennevirasto.digiroad2

import org.scalatest.{Matchers, FunSuite}
import scala.slick.driver.JdbcDriver.backend.Database
import scala.slick.driver.JdbcDriver.backend.Database.dynamicSession

class TotalWeightLimitServiceSpec extends FunSuite with Matchers {

  object PassThroughService extends TotalWeightLimitOperations {
    override def withDynTransaction[T](f: => T): T = f
  }

  test("Expire weight limit") {
    Database.forDataSource(PassThroughService.dataSource).withDynTransaction {
      PassThroughService.updateTotalWeightLimit(11111, None, true, "lol")
      val limit = PassThroughService.getById(11111)
      limit.get.limit should be (4000)
      limit.get.expired should be (true)
      dynamicSession.rollback()
    }
  }

  test("Update weight limit") {
    Database.forDataSource(PassThroughService.dataSource).withDynTransaction {
      PassThroughService.updateTotalWeightLimit(11111, Some(2000), false, "lol")
      val limit = PassThroughService.getById(11111)
      limit.get.limit should be (2000)
      limit.get.expired should be (false)
      dynamicSession.rollback()
    }
  }
}
