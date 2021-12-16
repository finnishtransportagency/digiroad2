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
    withDynTransaction {
      RoadLinkDAO.insert("traffic_direction", 1681765, None, 2)
      // the second traffic direction is set so that it can't incidentally match with VVH traffic direction value
      RoadLinkDAO.insert("traffic_direction", 6510465, None, 10)
      val linkIdsBeforeRemoval = RoadLinkDAO.TrafficDirectionDao.getLinkIds()
      linkIdsBeforeRemoval should contain(1681765)
      linkIdsBeforeRemoval should contain(6510465)
    }
    RedundantTrafficDirectionRemoval.deleteRedundantTrafficDirectionFromDB()
    withDynTransaction {
      val linkIdsAfterRemoval = RoadLinkDAO.TrafficDirectionDao.getLinkIds()
      linkIdsAfterRemoval should not contain(1681765)
      linkIdsAfterRemoval should contain(6510465)
      RoadLinkDAO.delete("traffic_direction", 6510465)
      val linkIdsAfterCleanUp = RoadLinkDAO.TrafficDirectionDao.getLinkIds()
      linkIdsAfterCleanUp should not contain(6510465)
    }
  }
}
