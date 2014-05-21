package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset.{PropertyTypes, PropertyValue, Property, AssetWithProperties}

trait AssetPropertiesReader {
  protected def calculateActualBearing(validityDirection: Int, bearing: Int): Int = {
    if (validityDirection != 3) {
      bearing
    } else {
      val flippedBearing = bearing - 180
      if (flippedBearing < 0) {
        flippedBearing + 360
      } else {
        flippedBearing
      }
    }
  }

  protected def extractPropertyValueOption(asset: AssetWithProperties, propertyPublicId: String): Option[String] = {
    asset.propertyData
      .find(property => property.publicId == propertyPublicId)
      .flatMap(property => property.values.headOption)
      .map(value => value.propertyValue)
  }

  protected def getPropertyValuesByPublicId(name: String, properties: Seq[Property]): Seq[PropertyValue] = {
    try {
      val property = properties.find(x => x.publicId == name).get
      sanitizedPropertyValues(property.propertyType, property.values)
    }
    catch {
      case e: Exception => println(s"""$name with $properties"""); throw e
    }
  }

  private def sanitizePropertyDisplayValue(displayValue: Option[String]): Option[String] = {
    displayValue.map { value => value.replace("\n", " ") }
  }

  private def sanitizedPropertyValues(propertyType: String, values: Seq[PropertyValue]): Seq[PropertyValue] = {
    propertyType match {
      case PropertyTypes.Text | PropertyTypes.LongText => values.map { value =>
        value.copy(propertyDisplayValue = sanitizePropertyDisplayValue(value.propertyDisplayValue))
      }
      case _ => values
    }
  }
}
