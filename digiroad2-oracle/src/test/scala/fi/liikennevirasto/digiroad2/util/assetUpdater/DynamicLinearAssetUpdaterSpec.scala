package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.DigiroadEventBus
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.{RoadLinkClient, RoadLinkFetched}
import fi.liikennevirasto.digiroad2.dao.DynamicLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset.{DynamicAssetValue, DynamicValue}
import fi.liikennevirasto.digiroad2.service.linearasset.{DynamicLinearAssetService, Measures}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.{FunSuite, Matchers}

class DynamicLinearAssetUpdaterSpec extends FunSuite with Matchers with UtilsSuite {
  
  val serviceDynamic = new DynamicLinearAssetService(mockRoadLinkService, mockEventBus)

  object TestDynamicLinearAssetUpdater extends DynamicLinearAssetUpdater(serviceDynamic) {
    override def withDynTransaction[T](f: => T): T = f
    override def dao: PostGISLinearAssetDao = linearAssetDao
    override def eventBus: DigiroadEventBus = mockEventBus
    override def roadLinkClient: RoadLinkClient = mockRoadLinkClient
    override def dynamicLinearAssetDao: DynamicLinearAssetDao = new DynamicLinearAssetDao
  }
  
  val assetValues = DynamicValue(DynamicAssetValue(Seq(
    DynamicProperty("kelirikko", "number", false, Seq(DynamicPropertyValue(10))),
    DynamicProperty("spring_thaw_period", "number", false, Seq()),
    DynamicProperty("annual_repetition", "number", false, Seq()),
    DynamicProperty("suggest_box", "checkbox", false, List())
  )))
 
  test("case 1 links under asset is split, smoke test") {
    val linkId = linkId5
    val newLinks = newLinks1_2_4
    val changes = roadLinkChangeClient.convertToRoadLinkChange(source)
    
    runWithRollback {
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(linkId).get
      val oldRoadLinkRaw = roadLinkService.getExpiredRoadLinkByLinkIdNonEncrished(linkId)
      when(mockRoadLinkService.fetchRoadlinkAndComplementary(linkId)).thenReturn(oldRoadLinkRaw)
      when(mockRoadLinkService.fetchRoadlinksByIds(any[Set[String]])).thenReturn(Seq.empty[RoadLinkFetched])

      val id = serviceDynamic.createWithoutTransaction(DamagedByThaw.typeId, linkId,
        assetValues, SideCode.BothDirections.value, Measures(0, 56.061), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val assetsBefore = serviceDynamic.getPersistedAssetsByIds(DamagedByThaw.typeId, Set(id), false)

      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestDynamicLinearAssetUpdater.updateByRoadLinks(DamagedByThaw.typeId, changes)
      val assetsAfter = serviceDynamic.getPersistedAssetsByLinkIds(DamagedByThaw.typeId, newLinks, false)
      assetsAfter.size should be(3)
      val sorted = assetsAfter.sortBy(_.endMeasure)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(9.334)

      sorted(1).startMeasure should be(0)
      sorted(1).endMeasure should be(11.841)

      sorted(2).startMeasure should be(0)
      sorted(2).endMeasure should be(34.906)

      assetsAfter.map(v => v.value.isEmpty should be(false))
      assetsAfter.map(v => v.value.get.equals(assetValues))
    }
  }
}

