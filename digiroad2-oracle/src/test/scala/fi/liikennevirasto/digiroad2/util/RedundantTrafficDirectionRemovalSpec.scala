package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset.{Municipality, TrafficDirection}
import fi.liikennevirasto.digiroad2.client.{FeatureClass, RoadLinkFetched}
import fi.liikennevirasto.digiroad2.dao.RoadLinkOverrideDAO
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

  val linkId1 = LinkIdGenerator.generateRandom()
  val linkId2 = LinkIdGenerator.generateRandom()

  val roadLinkWithRedundantTrafficDirection: RoadLinkFetched = RoadLinkFetched(linkId1, 91, Nil, Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)
  val roadLinkWithValidTrafficDirection: RoadLinkFetched = RoadLinkFetched(linkId2, 91, Nil, Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers)

  when(mockedRoadLinkService.fetchRoadlinksFromDB(any[Set[String]])).thenReturn(Seq(roadLinkWithRedundantTrafficDirection, roadLinkWithValidTrafficDirection))

  test("A redundant traffic direction is removed, but a valid is not") {
    val linkSet = mockedRoadLinkService.fetchRoadlinksFromDB(Set(linkId1, linkId2))
    runWithRollback {
      RoadLinkOverrideDAO.insert(RoadLinkOverrideDAO.TrafficDirection, linkSet.head.linkId, None, linkSet.head.trafficDirection.value)
      RoadLinkOverrideDAO.insert(RoadLinkOverrideDAO.TrafficDirection, linkSet.last.linkId, None, TrafficDirection.TowardsDigitizing.value)
      val linkIdsBeforeRemoval = RoadLinkOverrideDAO.TrafficDirectionDao.getLinkIds()
      linkIdsBeforeRemoval should contain(linkSet.head.linkId)
      linkIdsBeforeRemoval should contain(linkSet.last.linkId)
      mockedTrafficDirectionRemoval.deleteRedundantTrafficDirectionFromDB()
      val linkIdsAfterRemoval = RoadLinkOverrideDAO.TrafficDirectionDao.getLinkIds()
      linkIdsAfterRemoval should not contain linkSet.head.linkId
      linkIdsAfterRemoval should contain(linkSet.last.linkId)
    }
  }
}
