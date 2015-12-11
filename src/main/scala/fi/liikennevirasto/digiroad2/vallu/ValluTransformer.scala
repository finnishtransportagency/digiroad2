package fi.liikennevirasto.digiroad2.vallu

import fi.liikennevirasto.digiroad2.asset.Property
import fi.liikennevirasto.digiroad2.util.AssetPropertiesReader
import org.joda.time.format.{DateTimeFormat, ISODateTimeFormat}
import scala.language.reflectiveCalls

object ValluTransformer extends AssetPropertiesReader {
  def transformToISODate(dateOption: Option[String]) = {
    val inputDateFormat = DateTimeFormat.forPattern("yyyy-MM-dd")
    dateOption.map(date => ISODateTimeFormat.dateHourMinuteSecond().print(inputDateFormat.parseDateTime(date))).getOrElse("")
  }

  def describeReachability(asset: {val propertyData: Seq[Property]}): String = {
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

  def describeEquipments(asset: {val propertyData: Seq[Property]}): String = {
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
}
