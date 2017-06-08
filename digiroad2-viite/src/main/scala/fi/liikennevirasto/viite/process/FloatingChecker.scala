package fi.liikennevirasto.viite.process

import fi.liikennevirasto.digiroad2.{GeometryUtils, RoadLinkService, VVHRoadlink}
import fi.liikennevirasto.viite.dao.{RoadAddress, RoadAddressDAO}
import fi.liikennevirasto.viite.MaxMoveDistanceBeforeFloating


class FloatingChecker(roadLinkService: RoadLinkService) {

  def checkRoadPart(roadNumber: Long)(roadPartNumber: Long) = {
    def outsideOfGeometry(ra: RoadAddress, roadLinks: Seq[VVHRoadlink]): Boolean = {
      !roadLinks.exists(rl => GeometryUtils.geometryLength(rl.geometry) > ra.startMValue) ||
        !roadLinks.exists(rl => GeometryUtils.areAdjacent(
          GeometryUtils.truncateGeometry2D(rl.geometry, ra.startMValue, ra.endMValue),
          ra.geom))
    }

    /**
      * Check if road address geometry is moved by road link geometry change at leas MaxMoveDistanceBeforeFloating
      * meters. Because road address geometry isn't directed check for fit either way
      * @param roadLink Road link for road address list
      * @param roadAddresses Sequence of road addresses to check
      * @return true, if geometry has changed for any of the links beyond tolerance
      */
    def validateGeometryChange(roadLink : VVHRoadlink, roadAddresses: Seq[RoadAddress]) : Boolean = {
      roadAddresses.exists(ra =>
        GeometryUtils.geometryMoved(MaxMoveDistanceBeforeFloating)(
          GeometryUtils.truncateGeometry2D(roadLink.geometry, ra.startMValue, ra.endMValue), // 2D = don't care about changing height values
          ra.geom) &&
          GeometryUtils.geometryMoved(MaxMoveDistanceBeforeFloating)(
            GeometryUtils.truncateGeometry2D(roadLink.geometry, ra.startMValue, ra.endMValue),
            ra.geom.reverse)      // Road Address geometry isn't necessarily directed: start and end may not be aligned by side code
      )
    }

    def checkGeometryChangeOfSegments(roadAddressList: Seq[RoadAddress], roadLinks : Map[Long, Seq[VVHRoadlink]]): Seq[RoadAddress] = {
      val sortedRoadAddresses = roadAddressList.groupBy(ra=> ra.linkId)
      val movedRoadAddresses = sortedRoadAddresses.filter(ra => roadLinks.getOrElse(ra._1, Seq()).isEmpty || validateGeometryChange(roadLinks.getOrElse(ra._1, Seq()).head, ra._2)).flatMap(_._2).toSeq
      movedRoadAddresses
    }

    val roadAddressList = RoadAddressDAO.fetchByRoadPart(roadNumber, roadPartNumber, includeFloating = true)
    assert(roadAddressList.groupBy(ra => (ra.roadNumber, ra.roadPartNumber)).keySet.size == 1, "Mixed roadparts present!")
    val roadLinks = roadLinkService.getCurrentAndComplementaryVVHRoadLinks(roadAddressList.map(_.linkId).toSet).groupBy(_.linkId)
    val floatingSegments = roadAddressList.filter(ra => roadLinks.get(ra.linkId).isEmpty || outsideOfGeometry(ra, roadLinks.getOrElse(ra.linkId, Seq())))
    val floatings = checkGeometryChangeOfSegments(roadAddressList, roadLinks)
    (floatingSegments ++ floatings).distinct
  }

  def checkRoad(roadNumber: Long) = {
    println(s"Checking road: $roadNumber")
    val roadPartNumbers = RoadAddressDAO.getValidRoadParts(roadNumber)
    roadPartNumbers.flatMap(checkRoadPart(roadNumber))
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
}
