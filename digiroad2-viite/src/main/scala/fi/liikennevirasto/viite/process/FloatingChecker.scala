package fi.liikennevirasto.viite.process

import fi.liikennevirasto.digiroad2.{GeometryUtils, RoadLinkService, VVHRoadlink}
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.viite.dao.{RoadAddress, RoadAddressDAO}


class FloatingChecker(roadLinkService: RoadLinkService) {

  def checkRoadPart(roadNumber: Long)(roadPartNumber: Long) = {
    def outsideOfGeometry(ra: RoadAddress, roadLinks: Seq[VVHRoadlink]): Boolean = {
      !roadLinks.exists(rl => GeometryUtils.geometryLength(rl.geometry) > ra.startMValue) ||
        !roadLinks.exists(rl => GeometryUtils.areAdjacent(
          GeometryUtils.truncateGeometry2D(rl.geometry, ra.startMValue, ra.endMValue),
          ra.geom))
    }
    val roadAddressList = RoadAddressDAO.fetchByRoadPart(roadNumber, roadPartNumber)
    assert(roadAddressList.groupBy(ra => (ra.roadNumber, ra.roadPartNumber)).keySet.size == 1, "Mixed roadparts present!")
    val roadLinks = roadLinkService.getCurrentAndComplementaryVVHRoadLinks(roadAddressList.map(_.linkId).toSet).groupBy(_.linkId)
    val floatingSegments = roadAddressList.filter(ra => roadLinks.get(ra.linkId).isEmpty || outsideOfGeometry(ra, roadLinks.getOrElse(ra.linkId, Seq())))
    floatingSegments
  }

  def checkRoad(roadNumber: Long) = {
    println(s"Checking road: $roadNumber")
    val roadPartNumbers = RoadAddressDAO.getValidRoadParts(roadNumber)
    roadPartNumbers.flatMap(checkRoadPart(roadNumber))
  }

  def checkRoadNetwork(): List[RoadAddress] = {
    val roadNumbers = RoadAddressDAO.getValidRoadNumbers
    println(s"Got ${roadNumbers.size} roads")
    val groupSize = 1+(roadNumbers.size-1)/4
    roadNumbers.sliding(groupSize, groupSize).toSeq.par.flatMap(l => {println(l)
      l.flatMap(checkRoad) }).toList
  }
}
