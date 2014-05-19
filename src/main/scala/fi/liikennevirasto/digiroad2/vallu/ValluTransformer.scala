package fi.liikennevirasto.digiroad2.vallu

import org.joda.time.format.{ISODateTimeFormat, DateTimeFormat}

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
}
