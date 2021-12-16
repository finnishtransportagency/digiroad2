package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.{DummyEventBus, DummySerializer}
import fi.liikennevirasto.digiroad2.client.vvh.{VVHClient, VVHRoadlink}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.dao.RoadLinkDAO
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase

object RedundantTrafficDirectionRemoval {
  lazy val vvhClient: VVHClient = {
    new VVHClient(Digiroad2Properties.vvhRestApiEndPoint)
  }

  lazy val roadLinkService: RoadLinkService = {
    new RoadLinkService(vvhClient, new DummyEventBus, new DummySerializer)
  }

  def withDynTransaction(f: => Unit): Unit = PostGISDatabase.withDynTransaction(f)

  def deleteRedundantTrafficDirectionFromDB(): Unit = {
    withDynTransaction {
      val linkIds = RoadLinkDAO.TrafficDirectionDao.getLinkIds().toSet
      val vvhRoadlinks = roadLinkService.fetchVVHRoadlinks(linkIds = linkIds)
      vvhRoadlinks.foreach(vvhRoadLink => findAndDeleteRedundant(vvhRoadLink))
    }

    def findAndDeleteRedundant(vvhRoadlink: VVHRoadlink): Unit = {
      val optionalExistingValue: Option[Int] = RoadLinkDAO.get("traffic_direction", vvhRoadlink.linkId)
      val optionalVVHValue: Option[Int] = RoadLinkDAO.getVVHValue("traffic_direction", vvhRoadlink)
      (optionalExistingValue, optionalVVHValue) match {
        case (Some(existingValue), Some(vvhValue)) =>
          if (existingValue == vvhValue) {
            RoadLinkDAO.delete("traffic_direction", vvhRoadlink.linkId)
          }
        case (_, _) =>
          //do nothing
      }
    }
  }
}
