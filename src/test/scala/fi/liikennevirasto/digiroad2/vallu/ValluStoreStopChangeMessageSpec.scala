package fi.liikennevirasto.digiroad2.vallu

import org.scalatest._
import fi.liikennevirasto.digiroad2.asset.{PropertyValue, PropertyTypes, Property, AssetWithProperties}
import scala.xml.XML

class ValluStoreStopChangeMessageSpec extends FlatSpec with MustMatchers {
  val testAsset = AssetWithProperties(
    id = 1,
    externalId = Some(123),
    assetTypeId = 1,
    lon = 1,
    lat = 1,
    roadLinkId = 1,
    wgslon = 1,
    wgslat = 1)

  it must "specify encoding" in {
    val message: String = ValluStoreStopChangeMessage.create(testAsset)
    message startsWith("""<?xml version="1.0" encoding="UTF-8"?>""")
  }

  it must "specify external id" in {
    val message: String = ValluStoreStopChangeMessage.create(testAsset)
    val rootElement = XML.loadString(message)
    val stopId = rootElement \ "Stop" \ "StopId"
    stopId.text must equal("123")
  }

  it must "specify administrator stop id" in {
    val asset = testAssetWithProperty("yllapitajan_tunnus", "Livi83857")
    val message: String = ValluStoreStopChangeMessage.create(asset)
    val rootElement = XML.loadString(message)
    val stopId = rootElement \ "Stop" \ "AdminStopId"
    stopId.text must equal("Livi83857")
  }

  private def testAssetWithProperty(propertyPublicId: String, propertyValue: String) = {
    testAsset.copy(propertyData = List(Property(
      id = 1,
      publicId = propertyPublicId,
      propertyType = PropertyTypes.Text,
      values = List(PropertyValue(propertyValue)))))
  }
}
