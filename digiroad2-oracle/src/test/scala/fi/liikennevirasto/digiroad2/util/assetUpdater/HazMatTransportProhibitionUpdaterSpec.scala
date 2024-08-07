
package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.DigiroadEventBus
import fi.liikennevirasto.digiroad2.asset.{HazmatTransportProhibition, SideCode}
import fi.liikennevirasto.digiroad2.client.{RoadLinkClient, RoadLinkFetched}
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset.{ProhibitionValue, Prohibitions}
import fi.liikennevirasto.digiroad2.service.linearasset.{HazmatTransportProhibitionService, Measures}
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.{FunSuite, Matchers}

class HazMatTransportProhibitionUpdaterSpec extends FunSuite with Matchers with UpdaterUtilsSuite {
  
  val serviceHazmat = new HazmatTransportProhibitionService(mockRoadLinkService, mockEventBus)
  object TestHazMatProhibitionUpdater extends HazMatTransportProhibitionUpdater(serviceHazmat) {
    override def withDynTransaction[T](f: => T): T = f
    override def dao: PostGISLinearAssetDao = linearAssetDao
    override def eventBus: DigiroadEventBus = mockEventBus
    override def roadLinkClient: RoadLinkClient = mockRoadLinkClient
  }
  
  val assetValues = Prohibitions(Seq(ProhibitionValue(2, Set.empty, Set.empty)))

  test("case 1 links under asset is split, smoke test") {
    val linkId = linkId5
    val newLinks = newLinks1_2_4
    val changes = roadLinkChangeClient.convertToRoadLinkChange(source)

    runWithRollback {
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(linkId).get
      val oldRoadLinkRaw = roadLinkService.getExpiredRoadLinkByLinkIdNonEncrished(linkId)
      when(mockRoadLinkService.fetchRoadlinkAndComplementary(linkId)).thenReturn(oldRoadLinkRaw)
      when(mockRoadLinkService.fetchRoadlinksByIds(any[Set[String]])).thenReturn(Seq.empty[RoadLinkFetched])

      val id = serviceHazmat.createWithoutTransaction(HazmatTransportProhibition.typeId, linkId,
        assetValues, SideCode.BothDirections.value, Measures(0, 56.061), "testuser", 0L, Some(oldRoadLink), true, Some("testCreator"),
        Some(DateTime.parse("2020-01-01")), Some("testModifier"), Some(DateTime.parse("2022-01-01")))
      val assetsBefore = serviceHazmat.getPersistedAssetsByIds(HazmatTransportProhibition.typeId, Set(id), false)

      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)
      TestHazMatProhibitionUpdater.updateByRoadLinks(HazmatTransportProhibition.typeId, changes)
      val assetsAfter = serviceHazmat.getPersistedAssetsByLinkIds(HazmatTransportProhibition.typeId, newLinks,false)
      assetsAfter.size should be(3)
      assetsAfter.map(_.id).contains(id) should be(true)
      assetsAfter.forall(_.createdBy.get == "testCreator") should be(true)
      assetsAfter.forall(_.createdDateTime.get.toString().startsWith("2020-01-01")) should be(true)
      assetsAfter.forall(_.modifiedBy.get == "testModifier") should be(true)
      assetsAfter.forall(_.modifiedDateTime.get.toString().startsWith("2022-01-01")) should be(true)
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
85
  test("a hazmat prohibition asset is made road link long") {
    val oldLinkId = "875766ca-83b1-450b-baf1-db76d59176be:1"
    val newLinkId = "6eec9a4a-bcac-4afb-afc8-f4e6d40ec571:1"
    val changes = roadLinkChangeClient.convertToRoadLinkChange(source)

    runWithRollback {
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkId).get
      val oldRoadLinkRaw = roadLinkService.getExpiredRoadLinkByLinkIdNonEncrished(oldLinkId)
      when(mockRoadLinkService.fetchRoadlinkAndComplementary(oldLinkId)).thenReturn(oldRoadLinkRaw)
      when(mockRoadLinkService.fetchRoadlinksByIds(any[Set[String]])).thenReturn(Seq.empty[RoadLinkFetched])

      val id = serviceHazmat.createWithoutTransaction(HazmatTransportProhibition.typeId, oldLinkId,
        assetValues, SideCode.BothDirections.value, Measures(0, 10), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val assetsBefore = serviceHazmat.getPersistedAssetsByIds(HazmatTransportProhibition.typeId, Set(id), false)

      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestHazMatProhibitionUpdater.updateByRoadLinks(HazmatTransportProhibition.typeId, changes)
      val assetsAfter = serviceHazmat.getPersistedAssetsByLinkIds(HazmatTransportProhibition.typeId, Seq(newLinkId), false)

      assetsAfter.size should be(1)
      assetsAfter.head.value.get.asInstanceOf[Prohibitions].prohibitions.head should be(assetValues.prohibitions.head)
      assetsAfter.head.startMeasure should be(0.0)
      assetsAfter.head.endMeasure should be(35.212)
    }
  }
}
