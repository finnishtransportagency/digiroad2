package fi.liikennevirasto.digiroad2.vallu

import fi.liikennevirasto.digiroad2.asset.AssetWithProperties
import scala.xml.{Node, XML, Elem}

object ValluStoreStopChangeMessage {

  def create(asset: AssetWithProperties): String = {
    val mandatoryElements = Seq[Node]() :+ <StopId>{asset.externalId.get}</StopId>
    val optionalProperties = List(("yllapitajan_tunnus", <AdminStopId/>))

    val childElements = optionalProperties
      .foldLeft(mandatoryElements) {
      (elements, optionalProperty) =>
        val (propertyPublicId, wrapperElement) = optionalProperty
        val optionalElement = propertyValueToXmlElement(asset, propertyPublicId, wrapperElement)
        optionalElement match {
          case Some(element) => elements :+ element
          case _ => elements
        }
    }

    val stopElement= <Stop/>.copy(child = childElements)
    val message = <Stops/>.copy(child = stopElement)

    """<?xml version="1.0" encoding="UTF-8"?>""" + message.toString
  }

  private def propertyValueToXmlElement(asset: AssetWithProperties, propertyPublicId: String, wrapperElement: Elem): Option[Elem] = {
    extractPropertyValue(asset, propertyPublicId).map(value => {
      wrapperElement.copy(child = List(scala.xml.Text(value)))
    })
  }

  private def extractPropertyValue(asset: AssetWithProperties, propertyPublicId: String): Option[String] = {
    asset.propertyData
      .find(property => property.publicId == propertyPublicId)
      .flatMap(property => property.values.headOption)
      .map(value => value.propertyValue)
  }
}
