package fi.liikennevirasto.digiroad2.util

object RoadAddressUtils {
  lazy val walkingCyclingRoadNumbers: Seq[Int] = 70001 to 99999
  lazy val carTrafficRoadNumbers: Seq[Int] = 1 to 62999

  def isCarTrafficRoadAddress(roadNumber: Long): Boolean = {
    carTrafficRoadNumbers.contains(roadNumber)
  }

}
