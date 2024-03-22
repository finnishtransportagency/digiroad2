package fi.liikennevirasto.digiroad2


trait ILinearReference {
 val  linkId: String
 val startMValue: Double
 val endMValue: Option[Double]
  val sideCode: Option[Int]
}

/**
  * For point like asset mark [[endMValue]] None
  *
  * @param linkId            Road Link id
  * @param startMValue       start point
  * @param endMValue         end point, zero for point assets
  * @param sideCode          for linear assets
  */

sealed case class LinearReference(linkId: String, startMValue: Double, endMValue: Option[Double], sideCode: Option[Int] = None) extends ILinearReference
