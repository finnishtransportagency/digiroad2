package fi.liikennevirasto.digiroad2.vallu

import org.scalatest._
import fi.liikennevirasto.digiroad2.asset.{PropertyValue, PropertyTypes, Property, AssetWithProperties}
import scala.xml.{NodeSeq, Node, Elem, XML}

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

  it must "exclude optional elements" in {
    val stopElement = parseTestAssetMessage(testAsset)
    stopElement \ "AdminStopId" must be ('empty)
    stopElement \ "StopCode" must be ('empty)
  }

  it must "specify external id" in {
    val stopElement = parseTestAssetMessage(testAsset)
    val stopId = stopElement \ "StopId"
    stopId.text must equal("123")
  }

  it must "specify administrator stop id" in {
    val stopElement = parseTestAssetMessage(testAssetWithProperties(List(("yllapitajan_tunnus", "Livi83857"))))
    val stopId = stopElement \ "AdminStopId"
    stopId.text must equal("Livi83857")
  }

  it must "specify stop code for stop" in {
    val stopElement = parseTestAssetMessage(testAssetWithProperties(List(("matkustajatunnus", "Poliisilaitos"))))
    val stopCode = stopElement \ "StopCode"
    stopCode.text must equal("Poliisilaitos")
  }

  it must "specify stop name in Finnish and Swedish" in {
    val stopElement = parseTestAssetMessage(testAssetWithProperties(List(("nimi_suomeksi", "Puutarhatie"), ("nimi_ruotsiksi", "Trädgårdvägen"))))
    val nameElements = stopElement \ "Names" \ "Name"
    val nameFi = nameElements filter { _ \ "@lang" exists(_.text == "fi") }
    val nameSv = nameElements filter { _ \ "@lang" exists(_.text == "sv") }
    nameFi.text must equal("Puutarhatie")
    nameSv.text must equal("Trädgårdvägen")
  }

  private def parseTestAssetMessage(asset: AssetWithProperties): NodeSeq = {
    val message = ValluStoreStopChangeMessage.create(asset)
    XML.loadString(message) \ "Stop"
  }

  private def testAssetWithProperties(properties: List[(String, String)]) = {
    testAsset.copy(propertyData = properties.map { property =>
      Property(
        id = 1,
        publicId = property._1,
        propertyType = PropertyTypes.Text,
        values = List(PropertyValue(property._2))
      )
    }.toSeq)
  }
}
