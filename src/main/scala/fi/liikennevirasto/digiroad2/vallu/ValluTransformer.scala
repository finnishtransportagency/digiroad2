package fi.liikennevirasto.digiroad2.vallu

import org.joda.time.format.{ISODateTimeFormat, DateTimeFormat}
import fi.liikennevirasto.digiroad2.asset.{PropertyTypes, PropertyValue, Property, AssetWithProperties}

object ValluTransformer {
  def calculateActualBearing(validityDirection: Int, bearing: Int): Int = {
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

  def transformToISODate(dateOption: Option[String]) = {
    val inputDateFormat = DateTimeFormat.forPattern("yyyy-MM-dd")
    dateOption.map(date => ISODateTimeFormat.dateHourMinuteSecond().print(inputDateFormat.parseDateTime(date))).getOrElse("")
  }

  def getReachability(asset: AssetWithProperties): String = {
    val result = List(
      extractPropertyValueOption(asset, "saattomahdollisuus_henkiloautolla").map { continuousParking =>
        if (continuousParking == "2") "Liityntäpysäköinti" else ""
      }.filterNot(_.isEmpty),
      extractPropertyValueOption(asset, "liityntapysakointipaikkojen_maara").map { parkingSpaces =>
        parkingSpaces + " pysäköintipaikkaa"
      },
      extractPropertyValueOption(asset, "liityntapysakoinnin_lisatiedot")).flatten.mkString(", ")
    result
  }

  def getEquipment(asset: AssetWithProperties): String = {
    val result = List(
      extractPropertyValueOption(asset, "aikataulu").map { x =>
        if (x == "2") "Aikataulu" else ""
      }.filterNot(_.isEmpty),
      extractPropertyValueOption(asset, "katos").map { x =>
        if (x == "2") "Katos" else ""
      }.filterNot(_.isEmpty),
      extractPropertyValueOption(asset, "mainoskatos").map { x =>
        if (x == "2") "Mainoskatos" else ""
      }.filterNot(_.isEmpty),
      extractPropertyValueOption(asset, "penkki").map { x =>
        if (x == "2") "Penkki" else ""
      }.filterNot(_.isEmpty),
      extractPropertyValueOption(asset, "pyorateline").map { x =>
        if (x == "2") "Pyöräteline" else ""
      }.filterNot(_.isEmpty),
      extractPropertyValueOption(asset, "sahkoinen_aikataulunaytto").map { x =>
        if (x == "2") "Sähköinen aikataulunnäyttö" else ""
      }.filterNot(_.isEmpty),extractPropertyValueOption(asset, "valaistus").map { x =>
        if (x == "2") "Valaistus" else ""
      }.filterNot(_.isEmpty)).flatten.mkString(", ")
    result
  }

  def extractPropertyValueOption(asset: AssetWithProperties, propertyPublicId: String): Option[String] = {
    asset.propertyData
      .find(property => property.publicId == propertyPublicId)
      .flatMap(property => property.values.headOption)
      .map(value => value.propertyValue)
  }

  def getBusStopTypes(asset: AssetWithProperties) = {
    val busstopType: Seq[Long] = getPropertyValuesByPublicId("pysakin_tyyppi", asset.propertyData).map(x => x.propertyValue.toLong)
    val local = (if (busstopType.contains(2)) "1" else "0")
    val express = (if (busstopType.contains(3)) "1" else "0")
    val nonStopExpress = (if (busstopType.contains(4)) "1" else "0")
    val virtual = (if (busstopType.contains(5)) "1" else "0")
    (local, express, nonStopExpress, virtual)
  }

  def getPropertyValuesByPublicId(name: String, properties: Seq[Property]): Seq[PropertyValue] = {
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
