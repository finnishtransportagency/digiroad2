package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.Point

trait PolyLine {
  val geometry: Seq[Point]
}
