package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.{LinkChain, Point}

object LinearAsset {
  def calculateEndPoints(links: List[(Point, Point)]): Set[Point] = {
    val endPoints = LinkChain(links, identity[(Point, Point)]).endPoints()
    Set(endPoints._1, endPoints._2)
  }
}
