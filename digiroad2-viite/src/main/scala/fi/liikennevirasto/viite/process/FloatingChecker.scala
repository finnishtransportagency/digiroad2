package fi.liikennevirasto.viite.process

import fi.liikennevirasto.digiroad2.{GeometryUtils, RoadLinkService, VVHRoadlink}
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.viite.dao.{RoadAddress, RoadAddressDAO}


class FloatingChecker(roadLinkService: RoadLinkService) {

  def checkRoadPart(roadNumber: Long)(roadPartNumber: Long) = {
    def outsideOfGeometry(ra: RoadAddress, roadLinks: Seq[VVHRoadlink]): Boolean = {
      !roadLinks.exists(rl => GeometryUtils.geometryLength(rl.geometry) > ra.startMValue)
    }
    val roadAddressList = RoadAddressDAO.fetchByRoadPart(roadNumber, roadPartNumber)
    assert(roadAddressList.groupBy(ra => (ra.roadNumber, ra.roadPartNumber)).keySet.size == 1, "Mixed roadparts present!")
    val roadLinks = roadLinkService.fetchVVHRoadlinks(roadAddressList.map(_.linkId).toSet).groupBy(_.linkId)
    val floatingSegments = roadAddressList.filter(ra => roadLinks.get(ra.linkId).isEmpty || outsideOfGeometry(ra, roadLinks.getOrElse(ra.linkId, Seq())))
    floatingSegments
  }

  def checkRoad(roadNumber: Long) = {
    val roadPartNumbers = RoadAddressDAO.getValidRoadParts(roadNumber)
    roadPartNumbers.flatMap(checkRoadPart(roadNumber))
  }

  def checkRoadNetwork() = {
    val roadNumbers = RoadAddressDAO.getValidRoadNumbers
    roadNumbers.flatMap(checkRoad)
  }
}
