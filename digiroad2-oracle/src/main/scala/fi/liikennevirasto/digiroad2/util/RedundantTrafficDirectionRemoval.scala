package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.client.RoadLinkFetched
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.dao.RoadLinkOverrideDAO

class RedundantTrafficDirectionRemoval(roadLinkService: RoadLinkService) {

  def deleteRedundantTrafficDirectionFromDB(): Unit = {
    val linkIds = RoadLinkOverrideDAO.TrafficDirectionDao.getLinkIds().toSet
    val vvhRoadlinks = roadLinkService.fetchVVHRoadlinks(linkIds)
    vvhRoadlinks.foreach(vvhRoadLink => findAndDeleteRedundant(vvhRoadLink))

    def findAndDeleteRedundant(roadLinkFetched: RoadLinkFetched): Unit = {
      val optionalExistingValue: Option[Int] = RoadLinkOverrideDAO.get(RoadLinkOverrideDAO.TrafficDirection, roadLinkFetched.linkId)
      val optionalVVHValue: Option[Int] = RoadLinkOverrideDAO.getVVHValue(RoadLinkOverrideDAO.TrafficDirection, roadLinkFetched)
      (optionalExistingValue, optionalVVHValue) match {
        case (Some(existingValue), Some(vvhValue)) =>
          if (existingValue == vvhValue) {
            RoadLinkOverrideDAO.delete(RoadLinkOverrideDAO.TrafficDirection, roadLinkFetched.linkId)
          }
        case (_, _) =>
          //do nothing
      }
    }
  }
}
