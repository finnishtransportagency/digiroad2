package fi.liikennevirasto.digiroad2.service.pointasset

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.{BoundingRectangle, ServicePointsClass}
import fi.liikennevirasto.digiroad2.dao.pointasset.{IncomingService, IncomingServicePoint}
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.util.TestTransactions
import org.scalatest.{FunSuite, Matchers}

class ServicePointServiceSpec extends FunSuite with Matchers {

  val service = new ServicePointService {
    override def withDynTransaction[T](f: => T): T = f
    override def withDynSession[T](f: => T): T = f
  }

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback(PostGISDatabase.ds)(test)

  test("Can fetch by bounding box") {
    runWithRollback {
      val result = service.get(BoundingRectangle(Point(374127.5, 6677511.5), Point(374128.5, 6677512.5))).head
      result.id should equal(600061)
      result.lon should equal(374128)
      result.lat should equal(6677512)
    }
  }

  test("Create new service point") {
    runWithRollback {
      val id = service.create(IncomingServicePoint(374128.0,6677512.0,Set(IncomingService(6,Some("Testipalvelu1"),Some("Lisätieto1"),Some(3),Some(100), isAuthorityData = true, None),IncomingService(8,Some("Testipalvelu2"),Some("Lisätieto2"),None,Some(200), isAuthorityData = true, None)), Set()),235, "jakke")
      val assets = service.getPersistedAssetsByIds(Set(id))

      assets.size should be(1)

      val asset = assets.head
      val services = asset.services
      val firstService =  services.head
      val secondService =  services.last

      asset.id should be(id)
      asset.lon should be(374128)
      asset.lat should be(6677512)
      asset.createdBy should be(Some("jakke"))
      asset.createdAt shouldBe defined

      firstService.serviceType shouldBe ServicePointsClass.RestArea.value
      firstService.name shouldBe Some("Testipalvelu1")
      firstService.additionalInfo shouldBe Some("Lisätieto1")
      firstService.typeExtension shouldBe Some(3)
      firstService.parkingPlaceCount shouldBe Some(100)

      secondService.serviceType shouldBe ServicePointsClass.Airport.value
      secondService.name shouldBe Some("Testipalvelu2")
      secondService.additionalInfo shouldBe Some("Lisätieto2")
      secondService.parkingPlaceCount shouldBe Some(200)
    }
  }

  test("Expire service point") {
    runWithRollback {
      val id = service.create(IncomingServicePoint(374128.0,6677512.0,Set(IncomingService(6,Some("Testipalvelu1"),Some("Lisätieto1"),Some(3),Some(100), isAuthorityData = true, None),IncomingService(8,Some("Testipalvelu2"),Some("Lisätieto2"),None,Some(200), isAuthorityData = true, None)), Set()),235,"jakke")
      val assets = service.getPersistedAssetsByIds(Set(id))
      val asset = assets.head
      service.expire(asset.id, "jakke")
      service.getPersistedAssetsByIds(Set(asset.id)) should be ('empty)
    }
  }

  test("Update service point") {
    runWithRollback {

      val result = service.get(BoundingRectangle(Point(374127.5, 6677511.5), Point(374128.5, 6677512.5))).head
      result.id should equal(600061)

      val updated = IncomingServicePoint(result.lon,result.lat, Set(IncomingService(6,Some("Testipalvelu1"),Some("Lisätieto1"),Some(3),Some(100), isAuthorityData = true, None),IncomingService(8,Some("Testipalvelu2"),Some("Lisätieto2"),None,Some(200), isAuthorityData = true, None)), Set())

      service.update(result.id, updated, 235, "unit_test")
      val updatedServicePoint = service.get(BoundingRectangle(Point(374127.5, 6677511.5), Point(374128.5, 6677512.5))).head

      updatedServicePoint.id should equal (result.id)
      updatedServicePoint.modifiedBy should equal(Some("unit_test"))
      updatedServicePoint.modifiedAt shouldBe defined
    }
  }

  test("Create service point with wrong authority data ") {
    runWithRollback {
      intercept[ServicePointException] {
        service.create(IncomingServicePoint(374128.0, 6677512.0, Set(IncomingService(10, Some("Testipalvelu1"), Some("Lisätieto1"), Some(3), Some(100), isAuthorityData = true, None)), Set()), 235, "jakke")
      }
    }
  }

  test("Create service point with right authority data ") {
    runWithRollback {
      val id = service.create(IncomingServicePoint(374128.0,6677512.0,Set(IncomingService(6,Some("Testipalvelu1"),Some("Lisätieto1"),Some(3),Some(100), isAuthorityData = true, None),IncomingService(8,Some("Testipalvelu2"),Some("Lisätieto2"),None,Some(200), isAuthorityData = true, None)), Set()),235,"jakke")
      val assets = service.getPersistedAssetsByIds(Set(id))
      assets.size should be(1)

      val asset = assets.head
      asset.id should be(id)
      asset.lon should be(374128)
      asset.lat should be(6677512)
      asset.createdBy should be(Some("jakke"))
      asset.createdAt shouldBe defined
      val servicePoint =  asset.services.find(_.serviceType == ServicePointsClass.RestArea.value).get
      servicePoint.isAuthorityData should be (true)
      servicePoint.serviceType should be (ServicePointsClass.RestArea.value)
      val servicePoint1 =  asset.services.find(_.serviceType == ServicePointsClass.Airport.value).get
      servicePoint1.isAuthorityData should be (true)
      servicePoint1.serviceType should be (ServicePointsClass.Airport.value)
    }
  }
}
