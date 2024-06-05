package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.client.RoadLinkFetched
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.dao.RoadLinkOverrideDAO

class RedundantTrafficDirectionRemoval(roadLinkService: RoadLinkService) {

  def deleteRedundantTrafficDirectionFromDB(): Unit = {
    val linkIds = RoadLinkOverrideDAO.TrafficDirectionDao.getLinkIds().toSet
    val roadlinks = roadLinkService.fetchRoadlinksByIds(linkIds)
    roadlinks.foreach(roadLink => findAndDeleteRedundant(roadLink))

    def findAndDeleteRedundant(roadLinkFetched: RoadLinkFetched): Unit = {
      val optionalExistingValue: Option[Int] = RoadLinkOverrideDAO.get(RoadLinkOverrideDAO.TrafficDirection, roadLinkFetched.linkId)
      val optionalMasterDataValue: Option[Int] = RoadLinkOverrideDAO.getMasterDataValue(RoadLinkOverrideDAO.TrafficDirection, roadLinkFetched)
      (optionalExistingValue, optionalMasterDataValue) match {
        case (Some(existingValue), Some(masterDataValue)) =>
          if (existingValue == masterDataValue) {
            RoadLinkOverrideDAO.delete(RoadLinkOverrideDAO.TrafficDirection, roadLinkFetched.linkId)
          }
        case (_, _) =>
          //do nothing
      }
    }
  }
}
