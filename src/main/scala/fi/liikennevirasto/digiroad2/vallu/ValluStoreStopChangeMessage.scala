package fi.liikennevirasto.digiroad2.vallu

import scala.xml._
import fi.liikennevirasto.digiroad2.asset.AssetWithProperties
import scala.Some

object ValluStoreStopChangeMessage {
  def create(asset: AssetWithProperties): String = {
    val mandatoryElements: List[Node] = List(
      <StopId>{asset.externalId.get}</StopId>,
      <Coordinate>
        <xCoordinate>{asset.wgslon}</xCoordinate>
        <yCoordinate>{asset.wgslat}</yCoordinate>
      </Coordinate>,
      <Bearing>{asset.bearing.flatMap { bearing =>
        asset.validityDirection.map { validityDirection =>
          ValluTransformer.calculateActualBearing(validityDirection, bearing)
        }
      }.getOrElse("")}</Bearing>)

    val optionalProperties =
      List(
        ("yllapitajan_tunnus", <AdminStopId/>),
        ("matkustajatunnus", <StopCode/>))
    val nameProperties = List(("nimi_suomeksi", "fi"), ("nimi_ruotsiksi", "sv"))

    val nameElements = createOptionalElements(nameProperties.map { property =>
      val (propertyPublicId, attributeValue) = property
      propertyValueToXmlElement(asset, propertyPublicId, <Name/>)
        .map(_.copy(attributes = new UnprefixedAttribute("lang", attributeValue, Null)))
    })

    val namesElement: Seq[Node] = nameElements match {
      case x :: _ => <Names/>.copy(child = nameElements)
      case _ => Seq()
    }

    val optionalElements = createOptionalElements(optionalProperties.map { property =>
      val (propertyPublicId, wrapperElement) = property
      propertyValueToXmlElement(asset, propertyPublicId, wrapperElement)
    })

    val stopElement= <Stop/>.copy(child = mandatoryElements ++ optionalElements ++ namesElement)
    val message = <Stops/>.copy(child = stopElement)

    """<?xml version="1.0" encoding="UTF-8"?>""" + message.toString
  }

  private def createOptionalElements(optionalElements: Seq[Option[Elem]]): Seq[Node] = {
    optionalElements.foldLeft(Seq[Node]()) { (elements, optionalElement) =>
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
