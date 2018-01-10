package fi.liikennevirasto.viite.process

import fi.liikennevirasto.digiroad2.linearasset.RoadLinkLike
import fi.liikennevirasto.digiroad2.{GeometryUtils, RoadLinkService}
import fi.liikennevirasto.viite.dao.{RoadAddress, RoadAddressDAO}
import fi.liikennevirasto.viite.MaxMoveDistanceBeforeFloating


class FloatingChecker(roadLinkService: RoadLinkService) {
  private def pretty(ra: RoadAddress): String = {
    val (startM, endM) = (BigDecimal(ra.startMValue).setScale(3), BigDecimal(ra.endMValue).setScale(3))
    s"${ra.roadNumber}/${ra.roadPartNumber}/${ra.track.value}/${ra.startAddrMValue}-${ra.endAddrMValue} " +
      s"($startM - $endM, ${ra.sideCode})"
  }
  def checkRoadPart(roadNumber: Long)(roadPartNumber: Long): List[RoadAddress] = {
    def outsideOfGeometry(ra: RoadAddress, roadLinks: Seq[RoadLinkLike]): Boolean = {
      !roadLinks.exists(rl => GeometryUtils.geometryLength(rl.geometry) > ra.startMValue) ||
        !roadLinks.exists(rl => GeometryUtils.areAdjacent(
          GeometryUtils.truncateGeometry2D(rl.geometry, ra.startMValue, ra.endMValue),
          GeometryUtils.truncateGeometry2D(ra.geometry, ra.startMValue, ra.endMValue)))
    }

    val roadAddressList = RoadAddressDAO.fetchByRoadPart(roadNumber, roadPartNumber, includeFloating = true)
    assert(roadAddressList.groupBy(ra => (ra.roadNumber, ra.roadPartNumber)).keySet.size == 1, "Mixed roadparts present!")
    val roadLinks = roadLinkService.getCurrentAndComplementaryVVHRoadLinks(roadAddressList.map(_.linkId).toSet).groupBy(_.linkId)
    val floatingSegments = roadAddressList.filter(ra => roadLinks.get(ra.linkId).isEmpty || outsideOfGeometry(ra, roadLinks.getOrElse(ra.linkId, Seq())))
    floatingSegments.foreach(ra =>
      if (roadLinks.get(ra.linkId).isEmpty)
        println(s"${pretty(ra)} moved to floating, road link no longer exists")
      else {
        val rl = roadLinks(ra.linkId).head
        val len = GeometryUtils.geometryLength(rl.geometry)
        println(s"${pretty(ra)} moved to floating, outside of road link geometry (link is $len m)")
        //TODO: Testing - No update
        println(s"Road Address: ${ra.geometry}")
        println(s"Road Address ID: ${ra.id}")
        println(s"Road Address StartM: ${ra.startMValue}")
        println(s"Road Address EndM: ${ra.endAddrMValue}")
        println(s"Road Address Created/Modified: ${ra.modifiedBy}")
        println(s"Road Link: ${rl.geometry}")
        println("---------")
      }
    )
    val floatings = checkGeometryChangeOfSegments(roadAddressList, roadLinks)
    (floatingSegments ++ floatings).distinct
  }

  def checkRoad(roadNumber: Long): List[RoadAddress] = {
    try {
      val roadPartNumbers = RoadAddressDAO.getValidRoadParts(roadNumber)
      roadPartNumbers.flatMap(checkRoadPart(roadNumber))
    } catch {
      case ex: AssertionError =>
        println(s"Assert failed: ${ex.getMessage} on road $roadNumber on Floating Check")
        List()
    }
  }

  def checkRoadNetwork(username: String = ""): List[RoadAddress] = {
    val roadNumbers = username.startsWith("dr2dev") || username.startsWith("dr2test") match {
      case true => RoadAddressDAO.getValidRoadNumbersWithFilterToTestAndDevEnv
      case _ => RoadAddressDAO.getCurrentValidRoadNumbers()
    }

    println(s"Got ${roadNumbers.size} roads")
    val groupSize = 1+(roadNumbers.size-1)/4
    roadNumbers.sliding(groupSize, groupSize).toSeq.par.flatMap(l => l.flatMap(checkRoad)).toList
  }

  /**
    * Check if road address geometry is moved by road link geometry change at least MaxMoveDistanceBeforeFloating
    * meters. Because road address geometry isn't directed check for fit either way. Public for testing.
    * @param roadLink Road link for road address list
    * @param roadAddresses Sequence of road addresses to check
    * @return true, if geometry has changed for any of the addresses beyond tolerance
    */
  def isGeometryChange(roadLink : RoadLinkLike, roadAddresses: Seq[RoadAddress]) : Boolean = {
    roadAddresses.exists(ra => {
      GeometryUtils.geometryMoved(MaxMoveDistanceBeforeFloating)(
        GeometryUtils.truncateGeometry3D(roadLink.geometry, ra.startMValue, ra.endMValue),
        ra.geometry) &&
        GeometryUtils.geometryMoved(MaxMoveDistanceBeforeFloating)(
          GeometryUtils.truncateGeometry3D(roadLink.geometry, ra.startMValue, ra.endMValue),
          ra.geometry.reverse) // Road Address geometry isn't necessarily directed: start and end may not be aligned by side code
    }
    ) || Math.abs(roadAddresses.maxBy(_.endMValue).endMValue - GeometryUtils.geometryLength(roadLink.geometry)) > MaxMoveDistanceBeforeFloating
  }

  private def checkGeometryChangeOfSegments(roadAddressList: Seq[RoadAddress], roadLinks : Map[Long, Seq[RoadLinkLike]]): Seq[RoadAddress] = {
    val sortedRoadAddresses = roadAddressList.groupBy(ra=> ra.linkId)
    val movedRoadAddresses = sortedRoadAddresses.filter(ra =>
      roadLinks.getOrElse(ra._1, Seq()).isEmpty || isGeometryChange(roadLinks.getOrElse(ra._1, Seq()).head, ra._2)).flatMap(_._2).toSeq
    movedRoadAddresses.foreach{ ra =>{
      println(s"${pretty(ra)} moved to floating, geometry has changed")
      //TODO: Testing - No update
      println(s"Moved Geometry: ${ra.geometry}")
      println(s"Moved Address ID: ${ra.id}")
      println(s"Moved Address Created/Modified: ${ra.modifiedBy}")
      println("---------")
    }
    }
    movedRoadAddresses
  }

}
