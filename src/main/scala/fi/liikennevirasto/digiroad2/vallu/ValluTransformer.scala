package fi.liikennevirasto.digiroad2.vallu

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
}
