package fi.liikennevirasto.digiroad2.service.pointasset

import fi.liikennevirasto.digiroad2.DummyEventBus
import fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop.{NewMassTransitStop, ServicePointBusStopService}
import fi.liikennevirasto.digiroad2.util.TestTransactions
import org.scalatest.{BeforeAndAfter, FunSuite, Matchers}

class ServicePointBusStopServiceSpec extends FunSuite with Matchers with BeforeAndAfter{
  val assetType = 10

  object TestMassTransitStopService extends ServicePointBusStopService(assetType, new DummyEventBus)

  val assetLock = "Used to prevent deadlocks"
  def runWithRollback(test: => Unit): Unit = assetLock.synchronized {
    TestTransactions.runWithRollback()(test)
  }

  //val dummyServicePointBusStop = NewMassTransitStop(532963.6175279296, 6995180.002037556,)

  //TODO
  test("Create new Service Point as mass transit stop"){
    runWithRollback{

    }
  }

  test("Update Service Point as mass transit stop"){
    runWithRollback{

    }
  }

  test("Delete Service Point as mass transit stop"){
    runWithRollback{

    }
  }

  test("Fetch by id Service Point as mass transit stop"){

  }

  test("Fetch by national id Service Point as mass transit stop"){

  }

  test("Enrich Service Point as mass transit stop"){

  }
}
