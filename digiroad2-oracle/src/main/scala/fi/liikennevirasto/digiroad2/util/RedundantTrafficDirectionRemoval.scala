package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.client.vvh.RoadLinkFetched
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.dao.RoadLinkDAO

class RedundantTrafficDirectionRemoval(roadLinkService: RoadLinkService) {

  def deleteRedundantTrafficDirectionFromDB(): Unit = {
    val linkIds = RoadLinkDAO.TrafficDirectionDao.getLinkIds().toSet
    val vvhRoadlinks = roadLinkService.fetchVVHRoadlinks(linkIds)
    vvhRoadlinks.foreach(vvhRoadLink => findAndDeleteRedundant(vvhRoadLink))

    def findAndDeleteRedundant(roadLinkFetched: RoadLinkFetched): Unit = {
      val optionalExistingValue: Option[Int] = RoadLinkDAO.get(RoadLinkDAO.TrafficDirection, roadLinkFetched.linkId)
      val optionalVVHValue: Option[Int] = RoadLinkDAO.getVVHValue(RoadLinkDAO.TrafficDirection, roadLinkFetched)
      (optionalExistingValue, optionalVVHValue) match {
        case (Some(existingValue), Some(vvhValue)) =>
          if (existingValue == vvhValue) {
            RoadLinkDAO.delete(RoadLinkDAO.TrafficDirection, roadLinkFetched.linkId)
          }
        case (_, _) =>
          //do nothing
      }
    }
  }
}
