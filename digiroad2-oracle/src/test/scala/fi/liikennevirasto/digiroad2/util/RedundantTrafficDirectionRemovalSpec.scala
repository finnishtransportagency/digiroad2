package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset.{Municipality, TrafficDirection}
import fi.liikennevirasto.digiroad2.client.vvh.{FeatureClass, RoadLinkFetched}
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

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback(PostGISDatabase.ds)(test)

  val roadLinkWithRedundantTrafficDirection: RoadLinkFetched = RoadLinkFetched(1, 91, Nil, Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)
  val roadLinkWithValidTrafficDirection: RoadLinkFetched = RoadLinkFetched(2, 91, Nil, Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)

  when(mockedRoadLinkService.fetchVVHRoadlinks(any[Set[Long]], any[Boolean])).thenReturn(Seq(roadLinkWithRedundantTrafficDirection, roadLinkWithValidTrafficDirection))

  test("A redundant traffic direction is removed, but a valid is not") {
    val linkSet = mockedRoadLinkService.fetchVVHRoadlinks(Set(1, 2))
    runWithRollback {
      RoadLinkDAO.insert(RoadLinkDAO.TrafficDirection, linkSet.head.linkId, None, linkSet.head.trafficDirection.value)
      RoadLinkDAO.insert(RoadLinkDAO.TrafficDirection, linkSet.last.linkId, None, TrafficDirection.TowardsDigitizing.value)
      val linkIdsBeforeRemoval = RoadLinkDAO.TrafficDirectionDao.getLinkIds()
      linkIdsBeforeRemoval should contain(linkSet.head.linkId)
      linkIdsBeforeRemoval should contain(linkSet.last.linkId)
      mockedTrafficDirectionRemoval.deleteRedundantTrafficDirectionFromDB()
      val linkIdsAfterRemoval = RoadLinkDAO.TrafficDirectionDao.getLinkIds()
      linkIdsAfterRemoval should not contain linkSet.head.linkId
      linkIdsAfterRemoval should contain(linkSet.last.linkId)
    }
  }
}
