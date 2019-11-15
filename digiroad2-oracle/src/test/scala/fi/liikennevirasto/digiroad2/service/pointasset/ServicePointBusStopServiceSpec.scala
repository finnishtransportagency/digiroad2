package fi.liikennevirasto.digiroad2.service.pointasset

import fi.liikennevirasto.digiroad2.{DummyEventBus, Point}
import fi.liikennevirasto.digiroad2.asset.{PropertyValue, SimpleProperty}
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

  val dummyProperties = List(
    SimpleProperty("pysakin_tyyppi", List(PropertyValue("7"))),
    SimpleProperty("palvelu", List(PropertyValue("11"))),
    SimpleProperty("tarkenne", List(PropertyValue("5"))),
    SimpleProperty("palvelun_nimi", List(PropertyValue("name"))),
    SimpleProperty("palvelun_lisätieto", List(PropertyValue("additional info"))),
    SimpleProperty("viranomaisdataa", List(PropertyValue("Kylla")))
  )
  val dummyPoint = Point(532963.6175279296, 6995180.002037556)
  val dummyNewMassTransitStop = NewMassTransitStop(dummyPoint.x, dummyPoint.y, 0, 0, dummyProperties)

  test("Create new Service Point as mass transit stop"){
    runWithRollback{
      val (servicePoint, publishInfo) = TestMassTransitStopService.create(dummyNewMassTransitStop, "ServicePointBusStopServiceSpec", dummyPoint)

      servicePoint.propertyData.filter(_.publicId == "pysakin_tyyppi").head.values.head.propertyValue should be("7")
      servicePoint.propertyData.filter(_.publicId == "palvelu").head.values.head.propertyValue should be("11")
      servicePoint.propertyData.filter(_.publicId == "tarkenne").head.values.head.propertyValue should be("5")
      servicePoint.propertyData.filter(_.publicId == "palvelun_nimi").head.values.head.propertyValue should be("name")
      servicePoint.propertyData.filter(_.publicId == "palvelun_lisätieto").head.values.head.propertyValue should be("additional info")
      servicePoint.propertyData.filter(_.publicId == "viranomaisdataa").head.values.head.propertyValue should be("Kylla")
      servicePoint.propertyData.size should be(51)
      servicePoint.stopTypes.head should be (7)
    }
  }

  test("Update Service Point as mass transit stop"){
    runWithRollback{
      val (servicePoint, publishInfo) = TestMassTransitStopService.create(dummyNewMassTransitStop, "ServicePointBusStopServiceSpec", dummyPoint)

      val newProperty = SimpleProperty("palvelun_lisätieto", List(PropertyValue("updated info")))
      val newProperty1 = SimpleProperty("palvelun_nimi", List(PropertyValue("updated name")))

      val (updatedServicePoint, updatedPublishInfo) = TestMassTransitStopService.update(servicePoint, Set(newProperty, newProperty1), "ServicePointBusStopServiceSpec")

      updatedServicePoint.propertyData.filter(_.publicId == "pysakin_tyyppi").head.values.head.propertyValue should be(servicePoint.propertyData.filter(_.publicId == "pysakin_tyyppi").head.values.head.propertyValue)
      updatedServicePoint.propertyData.filter(_.publicId == "palvelu").head.values.head.propertyValue should be(servicePoint.propertyData.filter(_.publicId == "palvelu").head.values.head.propertyValue)
      updatedServicePoint.propertyData.filter(_.publicId == "tarkenne").head.values.head.propertyValue should be(servicePoint.propertyData.filter(_.publicId == "tarkenne").head.values.head.propertyValue)
      updatedServicePoint.propertyData.filter(_.publicId == "palvelun_nimi").head.values.head.propertyValue should be(newProperty1.values.head.propertyValue)
      updatedServicePoint.propertyData.filter(_.publicId == "palvelun_lisätieto").head.values.head.propertyValue should be(newProperty.values.head.propertyValue)
      updatedServicePoint.propertyData.filter(_.publicId == "viranomaisdataa").head.values.head.propertyValue should be(servicePoint.propertyData.filter(_.publicId == "viranomaisdataa").head.values.head.propertyValue)
      updatedServicePoint.stopTypes.head should be (7)
    }
  }

  test("Delete Service Point as mass transit stop"){
    runWithRollback{
      val (servicePoint, publishInfo) = TestMassTransitStopService.create(dummyNewMassTransitStop, "ServicePointBusStopServiceSpec", dummyPoint)

      TestMassTransitStopService.delete(servicePoint, "ServicePointBusStopServiceSpec")

      TestMassTransitStopService.fetchAssetByNationalId(servicePoint.nationalId) should be(None)
    }
  }
}
