package fi.liikennevirasto.digiroad2

/**
  * For point like asset mark [[endMValue]] None
  *
  * @param linkId            Road Link id
  * @param startMValue       start point
  * @param endMValue         end point, zero for point assets
  * @param sideCode          for linear assets
  */
sealed case class LinearReference(linkId: String, startMValue: Double, endMValue: Option[Double], sideCode: Option[Int] = None)
