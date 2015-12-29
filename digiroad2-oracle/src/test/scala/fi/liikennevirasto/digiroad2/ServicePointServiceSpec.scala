package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.pointasset.oracle.{IncomingService, IncomingServicePoint}
import fi.liikennevirasto.digiroad2.util.TestTransactions
import org.joda.time.DateTime
import org.scalatest.{FunSuite, Matchers}

class ServicePointServiceSpec extends FunSuite with Matchers {

  val service = new ServicePointService {
    override def withDynTransaction[T](f: => T): T = f
    override def withDynSession[T](f: => T): T = f
  }

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback(service.dataSource)(test)

  test("Can fetch by bounding box") {

  }

  test("Create new service point") {
    // TODO: Rollback not working properly
    runWithRollback {
      val id = service.create(IncomingServicePoint(374128.0,6677512.0,Set(IncomingService(6,Some("Testipalvelu1"),Some("Lisätieto1"),Some(3),Some(100)),IncomingService(8,Some("Testipalvelu2"),Some("Lisätieto2"),None,Some(200)))),235,"jakke")
      val assets = service.getPersistedAssetsByIds(Set(id))

      assets.size should be(1)

      val asset = assets.head
      val services = asset.services
      // TODO: Test service content

      asset.id should be(id)
      asset.lon should be(374128)
      asset.lat should be(6677512)
      asset.createdBy should be(Some("jakke"))
      asset.createdAt shouldBe defined

    }
  }

  test("Expire service point") {

  }

  test("Update service point") {

  }
}
