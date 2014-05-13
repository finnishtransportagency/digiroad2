package fi.liikennevirasto.digiroad2.vallu

import fi.liikennevirasto.digiroad2.asset.AssetWithProperties
import scala.xml._
import fi.liikennevirasto.digiroad2.asset.AssetWithProperties
import scala.Some

object ValluStoreStopChangeMessage {

  def create(asset: AssetWithProperties): String = {
    val mandatoryProperties = Seq[Node]() :+ <StopId>{asset.externalId.get}</StopId>
    val optionalProperties = List(("yllapitajan_tunnus", <AdminStopId/>), ("matkustajatunnus", <StopCode/>))

    val nameElements = elementListFromProperties(Seq[Node](), List(("nimi_suomeksi", <Name/>)), {(propertyPublicId, wrapperElement) =>
      propertyValueToXmlElement(asset, propertyPublicId, wrapperElement)
        .map(_.copy(attributes = new UnprefixedAttribute("lang", "fi", Null)))
    })
    val namesElement = <Names/>.copy(child = nameElements)

    val childElements = elementListFromProperties(mandatoryProperties, optionalProperties, {(propertyPublicId, wrapperElement) =>
      propertyValueToXmlElement(asset, propertyPublicId, wrapperElement)
    }) :+ namesElement

    val stopElement= <Stop/>.copy(child = childElements)
    val message = <Stops/>.copy(child = stopElement)

    """<?xml version="1.0" encoding="UTF-8"?>""" + message.toString
  }

  private def elementListFromProperties(headElements: Seq[Node], properties: Seq[(String, Elem)], elementGenerator: (String, Elem) => Option[Elem]): Seq[Node] = {
    properties.foldLeft(headElements) {(elements, property) =>
      val (propertyPublicId, wrapperElement) = property
      val optionalElement = elementGenerator(propertyPublicId, wrapperElement)
      optionalElement match {
        case Some(element) => elements :+ element
        case _ => elements
      }
    }
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
