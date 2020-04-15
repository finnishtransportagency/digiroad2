package fi.liikennevirasto.digiroad2.service.pointasset

import java.util.NoSuchElementException

import fi.liikennevirasto.digiroad2.{DummyEventBus, Point}
import fi.liikennevirasto.digiroad2.asset.{PropertyValue, SimplePointAssetProperty}
import fi.liikennevirasto.digiroad2.dao.ServicePoint
import fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop.{NewMassTransitStop, ServicePointStopService}
import org.scalatest.{BeforeAndAfter, FunSuite, Matchers}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession

class ServicePointStopServiceSpec extends FunSuite with Matchers with BeforeAndAfter{
  val assetType = 10

  val testMassTransitStopService = new ServicePointStopService(new DummyEventBus)

  val dummyProperties = List(
    SimplePointAssetProperty("pysakin_tyyppi", List(PropertyValue("7"))),
    SimplePointAssetProperty("palvelu", List(PropertyValue("11"))),
    SimplePointAssetProperty("tarkenne", List(PropertyValue("5"))),
    SimplePointAssetProperty("palvelun_nimi", List(PropertyValue("name"))),
    SimplePointAssetProperty("palvelun_lisätieto", List(PropertyValue("additional info"))),
    SimplePointAssetProperty("viranomaisdataa", List(PropertyValue("Kyllä")))
  )
  val dummyPoint = Point(532963.6175279296, 6995180.002037556)
  val dummyNewMassTransitStop = NewMassTransitStop(dummyPoint.x, dummyPoint.y, 0, 0, dummyProperties)

  def getPropertyValueByPublicId(asset: ServicePoint, publicId: String): String = {
    asset.propertyData.filter(_.publicId == publicId).head.values.head.asInstanceOf[PropertyValue].propertyValue
  }

  test("Create new Service Point as mass transit stop"){
    OracleDatabase.withDynTransaction {
      val createdServicePointId = testMassTransitStopService.create(dummyPoint.x, dummyPoint.y, dummyProperties, "ServicePointBusStopServiceSpec", 749, false)
      val createdServicePoint = testMassTransitStopService.fetchAsset(createdServicePointId)

      getPropertyValueByPublicId(createdServicePoint, "pysakin_tyyppi") should be("7")
      getPropertyValueByPublicId(createdServicePoint, "palvelu") should be("11")
      getPropertyValueByPublicId(createdServicePoint, "tarkenne") should be("5")
      getPropertyValueByPublicId(createdServicePoint, "palvelun_nimi") should be("name")
      getPropertyValueByPublicId(createdServicePoint, "palvelun_lisätieto") should be("additional info")
      getPropertyValueByPublicId(createdServicePoint, "viranomaisdataa") should be("Kyllä")
      createdServicePoint.propertyData.size should be(53)
      createdServicePoint.stopTypes.head should be(7)

      dynamicSession.rollback()
    }
  }

  test("Update Service Point as mass transit stop"){
    OracleDatabase.withDynTransaction {
      val createdServicePointId = testMassTransitStopService.create(dummyPoint.x, dummyPoint.y, dummyProperties, "ServicePointBusStopServiceSpec", 749, false)
      val createdServicePoint = testMassTransitStopService.fetchAsset(createdServicePointId)

      val newProperty = SimplePointAssetProperty("palvelun_lisätieto", List(PropertyValue("updated info")))
      val newProperty1 = SimplePointAssetProperty("palvelun_nimi", List(PropertyValue("updated name")))

      val updatedServicePoint = testMassTransitStopService.update(createdServicePointId, Point(createdServicePoint.lon, createdServicePoint.lat), Seq(newProperty, newProperty1), "ServicePointBusStopServiceSpec", 749, false)

      getPropertyValueByPublicId(updatedServicePoint, "pysakin_tyyppi") should be(getPropertyValueByPublicId(createdServicePoint, "pysakin_tyyppi"))
      getPropertyValueByPublicId(updatedServicePoint, "palvelu") should be(getPropertyValueByPublicId(createdServicePoint, "palvelu"))
      getPropertyValueByPublicId(updatedServicePoint, "tarkenne") should be(getPropertyValueByPublicId(createdServicePoint, "tarkenne"))
      getPropertyValueByPublicId(updatedServicePoint, "palvelun_nimi") should be(newProperty1.values.head.asInstanceOf[PropertyValue].propertyValue)
      getPropertyValueByPublicId(updatedServicePoint, "palvelun_lisätieto") should be(newProperty.values.head.asInstanceOf[PropertyValue].propertyValue)
      getPropertyValueByPublicId(updatedServicePoint, "viranomaisdataa") should be(getPropertyValueByPublicId(createdServicePoint, "viranomaisdataa"))
      updatedServicePoint.stopTypes.head should be(7)

      dynamicSession.rollback()
    }
  }

  test("Delete Service Point as mass transit stop"){
    OracleDatabase.withDynTransaction {
      val createdServicePointId = testMassTransitStopService.create(dummyPoint.x, dummyPoint.y, dummyProperties, "ServicePointBusStopServiceSpec", 749, false)
      val createdServicePoint = testMassTransitStopService.fetchAsset(createdServicePointId)

      testMassTransitStopService.expire(createdServicePoint, "ServicePointBusStopServiceSpec", false)
      assertThrows[NoSuchElementException] {testMassTransitStopService.fetchAsset(createdServicePointId)}

      dynamicSession.rollback()
    }
  }
}
