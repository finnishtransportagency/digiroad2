package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset.{Municipality, TrafficDirection}
import fi.liikennevirasto.digiroad2.client.vvh.{FeatureClass, VVHRoadlink}
import fi.liikennevirasto.digiroad2.dao.RoadLinkDAO
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class RedundantTrafficDirectionRemovalSpec extends FunSuite with Matchers {

  val mockedRoadLinkService: RoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockedTrafficDirectionRemoval: RedundantTrafficDirectionRemoval = new RedundantTrafficDirectionRemoval(mockedRoadLinkService)

  def withDynTransaction(f: => Unit): Unit = PostGISDatabase.withDynTransaction(f)

  val roadLinkWithRedundantTrafficDirection: VVHRoadlink = VVHRoadlink(1, 91, Nil, Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)
  val roadLinkWithValidTrafficDirection: VVHRoadlink = VVHRoadlink(2, 91, Nil, Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)

  when(mockedRoadLinkService.fetchVVHRoadlinks(any[Set[Long]], any[Boolean])).thenReturn(Seq(roadLinkWithRedundantTrafficDirection, roadLinkWithValidTrafficDirection))

  test("A redundant traffic direction is removed, but a valid is not") {
    val linkSet = mockedRoadLinkService.fetchVVHRoadlinks(Set(1, 2))
    withDynTransaction {
      RoadLinkDAO.insert("traffic_direction", linkSet.head.linkId, None, linkSet.head.trafficDirection.value)
      RoadLinkDAO.insert("traffic_direction", linkSet.last.linkId, None, TrafficDirection.TowardsDigitizing.value)
      val linkIdsBeforeRemoval = RoadLinkDAO.TrafficDirectionDao.getLinkIds()
      linkIdsBeforeRemoval should contain(linkSet.head.linkId)
      linkIdsBeforeRemoval should contain(linkSet.last.linkId)
    }
    mockedTrafficDirectionRemoval.deleteRedundantTrafficDirectionFromDB()
    withDynTransaction {
      val linkIdsAfterRemoval = RoadLinkDAO.TrafficDirectionDao.getLinkIds()
      linkIdsAfterRemoval should not contain linkSet.head.linkId
      linkIdsAfterRemoval should contain(linkSet.last.linkId)
      RoadLinkDAO.delete("traffic_direction", linkSet.last.linkId)
      val linkIdsAfterCleanUp = RoadLinkDAO.TrafficDirectionDao.getLinkIds()
      linkIdsAfterCleanUp should not contain linkSet.last.linkId
    }
  }
}