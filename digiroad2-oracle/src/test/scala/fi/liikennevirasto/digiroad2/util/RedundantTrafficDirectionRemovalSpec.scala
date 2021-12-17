package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.{DummyEventBus, DummySerializer}
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.dao.RoadLinkDAO
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import org.scalatest.{FunSuite, Matchers}

class RedundantTrafficDirectionRemovalSpec extends FunSuite with Matchers {
  lazy val vvhClient: VVHClient = {
    new VVHClient(Digiroad2Properties.vvhRestApiEndPoint)
  }

  lazy val roadLinkService: RoadLinkService = {
    new RoadLinkService(vvhClient, new DummyEventBus, new DummySerializer)
  }

  def withDynTransaction(f: => Unit): Unit = PostGISDatabase.withDynTransaction(f)

  test("A redundant traffic direction is removed, but a valid is not") {
    val linkSet = PostGISDatabase.withDynTransaction(roadLinkService.fetchVVHRoadlinks(Set(1681765,1611347,1611181,1611380,1611265,1610928,1611183,1611675,1610953,1611480)))
    if (linkSet.isEmpty) {
      cancel("No roadlinks found, so test canceled.")
    }
    val linkWithRedundantTrafficDirection = linkSet.head
    withDynTransaction {
      // put the vvh value of the first received link to the traffic direction table, so that it's certainly redundant
      RoadLinkDAO.insert("traffic_direction", linkWithRedundantTrafficDirection.linkId, None, linkWithRedundantTrafficDirection.trafficDirection.value)
      // the second traffic direction is set as 10 that it can't incidentally match with any VVH traffic direction value
      RoadLinkDAO.insert("traffic_direction", 6510465, None, 10)
      val linkIdsBeforeRemoval = RoadLinkDAO.TrafficDirectionDao.getLinkIds()
      linkIdsBeforeRemoval should contain(linkWithRedundantTrafficDirection.linkId)
      linkIdsBeforeRemoval should contain(6510465)
    }
    RedundantTrafficDirectionRemoval.deleteRedundantTrafficDirectionFromDB()
    withDynTransaction {
      val linkIdsAfterRemoval = RoadLinkDAO.TrafficDirectionDao.getLinkIds()
      linkIdsAfterRemoval should not contain  linkWithRedundantTrafficDirection.linkId
      linkIdsAfterRemoval should contain(6510465)
      RoadLinkDAO.delete("traffic_direction", 6510465)
      val linkIdsAfterCleanUp = RoadLinkDAO.TrafficDirectionDao.getLinkIds()
      linkIdsAfterCleanUp should not contain 6510465
    }
  }
}
