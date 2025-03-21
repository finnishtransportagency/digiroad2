package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.DigiroadEventBus
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.{RoadLinkChangeType, RoadLinkClient, RoadLinkFetched}
import fi.liikennevirasto.digiroad2.dao.DynamicLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset.{DynamicAssetValue, DynamicValue, NumericValue}
import fi.liikennevirasto.digiroad2.service.linearasset.{DamagedByThawService, DynamicLinearAssetService, Measures}
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.{FunSuite, Matchers}

class DynamicLinearAssetUpdaterSpec extends FunSuite with Matchers with UpdaterUtilsSuite {
  
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
      assetsAfter.map(_.id).contains(id)

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

  val cyclingAndWalkingValue = DynamicValue(DynamicAssetValue(List(DynamicProperty("cyclingAndWalking_type","single_choice",true,List(DynamicPropertyValue(3))))))
  val cyclingAndWalkingValue2 = DynamicValue(DynamicAssetValue(List(DynamicProperty("cyclingAndWalking_type","single_choice",true,List(DynamicPropertyValue(8))))))

  test("a walking and cycling asset is removed when the link is removed") {
    val oldLinkId = "7766bff4-5f02-4c30-af0b-42ad3c0296aa:1"
    val changes = roadLinkChangeClient.convertToRoadLinkChange(source)

    runWithRollback {
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkId).get
      val oldRoadLinkRaw = roadLinkService.getExpiredRoadLinkByLinkIdNonEncrished(oldLinkId)
      when(mockRoadLinkService.fetchRoadlinkAndComplementary(oldLinkId)).thenReturn(oldRoadLinkRaw)
      when(mockRoadLinkService.fetchRoadlinksByIds(any[Set[String]])).thenReturn(Seq.empty[RoadLinkFetched])

      val id = serviceDynamic.createWithoutTransaction(CyclingAndWalking.typeId, oldLinkId,
        cyclingAndWalkingValue, SideCode.BothDirections.value, Measures(0, 30.928), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val assetsBefore = service.getPersistedAssetsByIds(CyclingAndWalking.typeId, Set(id), false)

      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestDynamicLinearAssetUpdater.updateByRoadLinks(CyclingAndWalking.typeId, changes)
      val assetsAfter = serviceDynamic.getPersistedAssetsByLinkIds(CyclingAndWalking.typeId, Seq(oldLinkId), false)

      assetsAfter.size should be(0)
    }
  }

  test("a walking and cycling asset is transferred to a new link") {
    val oldLinkId = "4f8a33d6-a939-4934-847d-d7b10193b7e9:1"
    val newLinkId = "4f8a33d6-a939-4934-847d-d7b10193b7e9:2"
    val changes = roadLinkChangeClient.convertToRoadLinkChange(source)

    runWithRollback {
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkId).get
      val oldRoadLinkRaw = roadLinkService.getExpiredRoadLinkByLinkIdNonEncrished(oldLinkId)
      when(mockRoadLinkService.fetchRoadlinkAndComplementary(oldLinkId)).thenReturn(oldRoadLinkRaw)
      when(mockRoadLinkService.fetchRoadlinksByIds(any[Set[String]])).thenReturn(Seq.empty[RoadLinkFetched])

      val id = serviceDynamic.createWithoutTransaction(CyclingAndWalking.typeId, oldLinkId,
        cyclingAndWalkingValue, SideCode.BothDirections.value, Measures(0, 16.465), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val assetsBefore = serviceDynamic.getPersistedAssetsByIds(CyclingAndWalking.typeId, Set(id), false)

      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestDynamicLinearAssetUpdater.updateByRoadLinks(CyclingAndWalking.typeId, changes)
      val assetsAfter = serviceDynamic.getPersistedAssetsByLinkIds(CyclingAndWalking.typeId, Seq(newLinkId), false)

      assetsAfter.size should be(1)
      assetsAfter.head.id should be(id)
      assetsAfter.head.value.get.toString should be(cyclingAndWalkingValue.toString)
    }
  }

  test("a walking and cycling asset is split when the road link is split") {
    val oldLinkId = "f8fcc994-6e3e-41b5-bb0f-ae6089fe6acc:1"
    val newLinkIds = Seq("753279ca-5a4d-4713-8609-0bd35d6a30fa:1", "c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1", "c3beb1ca-05b4-44d6-8d69-2a0e09f22580:1")
    val changes = roadLinkChangeClient.convertToRoadLinkChange(source)

    runWithRollback {
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkId).get
      val oldRoadLinkRaw = roadLinkService.getExpiredRoadLinkByLinkIdNonEncrished(oldLinkId)
      when(mockRoadLinkService.fetchRoadlinkAndComplementary(oldLinkId)).thenReturn(oldRoadLinkRaw)
      when(mockRoadLinkService.fetchRoadlinksByIds(any[Set[String]])).thenReturn(Seq.empty[RoadLinkFetched])

      val id = serviceDynamic.createWithoutTransaction(CyclingAndWalking.typeId, oldLinkId,
        cyclingAndWalkingValue, SideCode.BothDirections.value, Measures(0, 56.061), "testuser", 0L, Some(oldRoadLink), true, Some("testCreator"),
        Some(DateTime.parse("2020-01-01")), Some("testModifier"), Some(DateTime.parse("2022-01-01")))
      val assetsBefore = serviceDynamic.getPersistedAssetsByIds(CyclingAndWalking.typeId, Set(id), false)

      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestDynamicLinearAssetUpdater.updateByRoadLinks(CyclingAndWalking.typeId, changes)
      val assetsAfter = serviceDynamic.getPersistedAssetsByLinkIds(CyclingAndWalking.typeId, newLinkIds, false)

      assetsAfter.size should be(3)
      assetsAfter.map(_.id).contains(id)
      assetsAfter.forall(_.createdBy.get == "testCreator") should be(true)
      assetsAfter.forall(_.createdDateTime.get.toString().startsWith("2020-01-01")) should be(true)
      assetsAfter.forall(_.modifiedBy.get == "testModifier") should be(true)
      assetsAfter.forall(_.modifiedDateTime.get.toString().startsWith("2022-01-01")) should be(true)
      assetsAfter.map(a => a.value.get.toString should be(cyclingAndWalkingValue.toString))
      assetsAfter.map(_.startMeasure) should be(List(0.0, 0.0, 0.0))
      assetsAfter.map(_.endMeasure).sorted should be(List(9.334, 11.841, 34.906))
    }
  }

  test("a cut walking and cycling asset is moved to a lengthened road link") {
    val oldLinkId = "84e223f4-b349-4103-88ee-03b96153ef85:1"
    val newLinkId = "a1ebe866-e704-411d-8c31-53522bc04a37:1"
    val changes = roadLinkChangeClient.convertToRoadLinkChange(source)

    runWithRollback {
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkId).get
      val oldRoadLinkRaw = roadLinkService.getExpiredRoadLinkByLinkIdNonEncrished(oldLinkId)
      when(mockRoadLinkService.fetchRoadlinkAndComplementary(oldLinkId)).thenReturn(oldRoadLinkRaw)
      when(mockRoadLinkService.fetchRoadlinksByIds(any[Set[String]])).thenReturn(Seq.empty[RoadLinkFetched])

      val id = serviceDynamic.createWithoutTransaction(CyclingAndWalking.typeId, oldLinkId,
        cyclingAndWalkingValue, SideCode.BothDirections.value, Measures(0, 16.465), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val id2 = serviceDynamic.createWithoutTransaction(CyclingAndWalking.typeId, oldLinkId,
        cyclingAndWalkingValue2, SideCode.BothDirections.value, Measures(16.465, 36.189), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val assetsBefore = serviceDynamic.getPersistedAssetsByIds(CyclingAndWalking.typeId, Set(id, id2), false)

      assetsBefore.size should be(2)
      assetsBefore.head.expired should be(false)

      TestDynamicLinearAssetUpdater.updateByRoadLinks(CyclingAndWalking.typeId, changes)
      val assetsAfter = serviceDynamic.getPersistedAssetsByLinkIds(CyclingAndWalking.typeId, Seq(newLinkId), false)

      assetsAfter.size should be(2)
      assetsAfter.map(_.id).sorted should be(assetsBefore.map(_.id).sorted)
      val sortedAssets = assetsAfter.sortBy(_.startMeasure)
      sortedAssets.head.value.get.toString should be(cyclingAndWalkingValue.toString)
      sortedAssets.head.startMeasure should be(0.0)
      sortedAssets.head.endMeasure should be(20.759)
      sortedAssets.last.value.get.toString should be(cyclingAndWalkingValue2.toString)
      sortedAssets.last.startMeasure should be(20.759)
      sortedAssets.last.endMeasure should be(45.628)
    }
  }

  test("a projected walking and cycling asset falls outside the geometry of a shortened link, the remaining asset is made road link long") {
    val oldLinkId = "875766ca-83b1-450b-baf1-db76d59176be:1"
    val newLinkId = "6eec9a4a-bcac-4afb-afc8-f4e6d40ec571:1"
    val changes = roadLinkChangeClient.convertToRoadLinkChange(source)

    runWithRollback {
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkId).get
      val oldRoadLinkRaw = roadLinkService.getExpiredRoadLinkByLinkIdNonEncrished(oldLinkId)
      when(mockRoadLinkService.fetchRoadlinkAndComplementary(oldLinkId)).thenReturn(oldRoadLinkRaw)
      when(mockRoadLinkService.fetchRoadlinksByIds(any[Set[String]])).thenReturn(Seq.empty[RoadLinkFetched])

      val id = serviceDynamic.createWithoutTransaction(CyclingAndWalking.typeId, oldLinkId,
        cyclingAndWalkingValue, SideCode.BothDirections.value, Measures(0, 43.265), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val id2 = serviceDynamic.createWithoutTransaction(CyclingAndWalking.typeId, oldLinkId,
        cyclingAndWalkingValue2, SideCode.BothDirections.value, Measures(43.265, 45.317), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val assetsBefore = serviceDynamic.getPersistedAssetsByIds(CyclingAndWalking.typeId, Set(id, id2), false)

      assetsBefore.size should be(2)
      assetsBefore.head.expired should be(false)

      TestDynamicLinearAssetUpdater.updateByRoadLinks(CyclingAndWalking.typeId, changes)
      val assetsAfter = serviceDynamic.getPersistedAssetsByLinkIds(CyclingAndWalking.typeId, Seq(newLinkId), false)

      assetsAfter.size should be(1)
      assetsAfter.head.id should be(id)
      assetsAfter.head.value.get.toString should be(cyclingAndWalkingValue.toString)
      assetsAfter.head.startMeasure should be(0.0)
      assetsAfter.head.endMeasure should be(35.212)
    }
  }

  test("subsequent cycling and walking assets with the same value on a merged road lind should be merged") {
    val oldLinkId1 = "9d23b85a-d7bf-4c6f-83ff-aa391ff4879f:1"
    val oldLinkId2 = "41a67ca5-886f-44ff-a9ca-92d7d9bea129:1"
    val oldLinkId3 = "b05075a5-45e1-447e-9813-752ba3e07fe5:1"
    val oldLinkId4 = "7ce91531-42e8-424b-a528-b72af5cb1180:1"
    val newLinkId = "fbea6a9c-6682-4a1b-9807-7fb11a67e227:1"
    val changes = roadLinkChangeClient.convertToRoadLinkChange(source)

    runWithRollback {
      val oldRoadLink1 = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkId1).get
      val oldRoadLink2 = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkId2).get
      val oldRoadLink3 = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkId3).get
      val oldRoadLink4 = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkId4).get
      val oldRoadLinkRaw1 = roadLinkService.getExpiredRoadLinkByLinkIdNonEncrished(oldLinkId1)
      val oldRoadLinkRaw2 = roadLinkService.getExpiredRoadLinkByLinkIdNonEncrished(oldLinkId2)
      val oldRoadLinkRaw3 = roadLinkService.getExpiredRoadLinkByLinkIdNonEncrished(oldLinkId3)
      val oldRoadLinkRaw4 = roadLinkService.getExpiredRoadLinkByLinkIdNonEncrished(oldLinkId4)
      when(mockRoadLinkService.fetchRoadlinkAndComplementary(oldLinkId1)).thenReturn(oldRoadLinkRaw1)
      when(mockRoadLinkService.fetchRoadlinkAndComplementary(oldLinkId2)).thenReturn(oldRoadLinkRaw2)
      when(mockRoadLinkService.fetchRoadlinkAndComplementary(oldLinkId3)).thenReturn(oldRoadLinkRaw3)
      when(mockRoadLinkService.fetchRoadlinkAndComplementary(oldLinkId4)).thenReturn(oldRoadLinkRaw4)
      when(mockRoadLinkService.fetchRoadlinksByIds(any[Set[String]])).thenReturn(Seq.empty[RoadLinkFetched])

      val id = serviceDynamic.createWithoutTransaction(CyclingAndWalking.typeId, oldLinkId1,
        cyclingAndWalkingValue, SideCode.BothDirections.value, Measures(0, 580.215), "testuser", 0L, Some(oldRoadLink1), false, None, None)
      val id2 = serviceDynamic.createWithoutTransaction(CyclingAndWalking.typeId, oldLinkId2,
        cyclingAndWalkingValue, SideCode.BothDirections.value, Measures(0, 43.347), "testuser", 0L, Some(oldRoadLink2), false, None, None)
      val id3 = serviceDynamic.createWithoutTransaction(CyclingAndWalking.typeId, oldLinkId3,
        cyclingAndWalkingValue2, SideCode.BothDirections.value, Measures(0, 42.591), "testuser", 0L, Some(oldRoadLink3), false, None, None)
      val id4 = serviceDynamic.createWithoutTransaction(CyclingAndWalking.typeId, oldLinkId4,
        cyclingAndWalkingValue2, SideCode.BothDirections.value, Measures(0, 34.384), "testuser", 0L, Some(oldRoadLink4), false, None, None)
      val assetsBefore = serviceDynamic.getPersistedAssetsByIds(CyclingAndWalking.typeId, Set(id, id2, id3, id4), false)

      assetsBefore.size should be(4)
      assetsBefore.head.expired should be(false)

      TestDynamicLinearAssetUpdater.updateByRoadLinks(CyclingAndWalking.typeId, changes)
      val assetsAfter = serviceDynamic.getPersistedAssetsByLinkIds(CyclingAndWalking.typeId, Seq(newLinkId), false)

      assetsAfter.size should be(2)
      assetsAfter.forall(a => assetsBefore.map(_.id).contains(a.id))
      val sortedAssets = assetsAfter.sortBy(_.startMeasure)
      sortedAssets.head.value.get.toString should be(cyclingAndWalkingValue.toString)
      sortedAssets.head.startMeasure should be(0.0)
      sortedAssets.head.endMeasure should be(623.562)
      sortedAssets.last.value.get.toString should be(cyclingAndWalkingValue2.toString)
      sortedAssets.last.startMeasure should be(623.562)
      sortedAssets.last.endMeasure should be(700.537)
    }
  }

  test("subsequent cycling and walking assets with different value on a merged road lind should not be merged") {
    val oldLinkId1 = "9d23b85a-d7bf-4c6f-83ff-aa391ff4879f:1"
    val oldLinkId2 = "41a67ca5-886f-44ff-a9ca-92d7d9bea129:1"
    val oldLinkId3 = "b05075a5-45e1-447e-9813-752ba3e07fe5:1"
    val oldLinkId4 = "7ce91531-42e8-424b-a528-b72af5cb1180:1"
    val newLinkId = "fbea6a9c-6682-4a1b-9807-7fb11a67e227:1"
    val changes = roadLinkChangeClient.convertToRoadLinkChange(source)

    runWithRollback {
      val oldRoadLink1 = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkId1).get
      val oldRoadLink2 = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkId2).get
      val oldRoadLink3 = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkId3).get
      val oldRoadLink4 = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkId4).get
      val oldRoadLinkRaw1 = roadLinkService.getExpiredRoadLinkByLinkIdNonEncrished(oldLinkId1)
      val oldRoadLinkRaw2 = roadLinkService.getExpiredRoadLinkByLinkIdNonEncrished(oldLinkId2)
      val oldRoadLinkRaw3 = roadLinkService.getExpiredRoadLinkByLinkIdNonEncrished(oldLinkId3)
      val oldRoadLinkRaw4 = roadLinkService.getExpiredRoadLinkByLinkIdNonEncrished(oldLinkId4)
      when(mockRoadLinkService.fetchRoadlinkAndComplementary(oldLinkId1)).thenReturn(oldRoadLinkRaw1)
      when(mockRoadLinkService.fetchRoadlinkAndComplementary(oldLinkId2)).thenReturn(oldRoadLinkRaw2)
      when(mockRoadLinkService.fetchRoadlinkAndComplementary(oldLinkId3)).thenReturn(oldRoadLinkRaw3)
      when(mockRoadLinkService.fetchRoadlinkAndComplementary(oldLinkId4)).thenReturn(oldRoadLinkRaw4)
      when(mockRoadLinkService.fetchRoadlinksByIds(any[Set[String]])).thenReturn(Seq.empty[RoadLinkFetched])

      val id = serviceDynamic.createWithoutTransaction(CyclingAndWalking.typeId, oldLinkId1,
        cyclingAndWalkingValue, SideCode.BothDirections.value, Measures(0, 580.215), "testuser", 0L, Some(oldRoadLink1), false, None, None)
      val id2 = serviceDynamic.createWithoutTransaction(CyclingAndWalking.typeId, oldLinkId2,
        cyclingAndWalkingValue2, SideCode.BothDirections.value, Measures(0, 43.347), "testuser", 0L, Some(oldRoadLink2), false, None, None)
      val id3 = serviceDynamic.createWithoutTransaction(CyclingAndWalking.typeId, oldLinkId3,
        cyclingAndWalkingValue, SideCode.BothDirections.value, Measures(0, 42.591), "testuser", 0L, Some(oldRoadLink3), false, None, None)
      val id4 = serviceDynamic.createWithoutTransaction(CyclingAndWalking.typeId, oldLinkId4,
        cyclingAndWalkingValue2, SideCode.BothDirections.value, Measures(0, 34.384), "testuser", 0L, Some(oldRoadLink4), false, None, None)
      val assetsBefore = serviceDynamic.getPersistedAssetsByIds(CyclingAndWalking.typeId, Set(id, id2, id3, id4), false)

      assetsBefore.size should be(4)
      assetsBefore.head.expired should be(false)

      TestDynamicLinearAssetUpdater.updateByRoadLinks(CyclingAndWalking.typeId, changes)
      val assetsAfter = serviceDynamic.getPersistedAssetsByLinkIds(CyclingAndWalking.typeId, Seq(newLinkId), false)

      assetsAfter.size should be(4)
      assetsAfter.map(_.id).sorted should be(assetsBefore.map(_.id).sorted)
      val sortedAssets = assetsAfter.sortBy(_.startMeasure)
      sortedAssets.head.value.get.toString should be(cyclingAndWalkingValue.toString)
      sortedAssets.head.startMeasure should be(0.0)
      sortedAssets.head.endMeasure should be(580.215)
      sortedAssets(1).value.get.toString should be(cyclingAndWalkingValue2.toString)
      sortedAssets(1).startMeasure should be(580.215)
      sortedAssets(1).endMeasure should be(623.562)
      sortedAssets(2).value.get.toString should be(cyclingAndWalkingValue.toString)
      sortedAssets(2).startMeasure should be(623.562)
      sortedAssets(2).endMeasure should be(666.153)
      sortedAssets.last.value.get.toString should be(cyclingAndWalkingValue2.toString)
      sortedAssets.last.startMeasure should be(666.153)
      sortedAssets.last.endMeasure should be(700.537)
    }
  }

  test("Split. Given a Road Link that is split into 2 new links;" +
                  "When the old link has a careClass asset;" +
                  "Then that asset should be moved to a new link and a new one should be created for the other") {
    val oldLinkID = "2cfd9e51-d5b6-4199-981c-9e49cd82400b:1"
    val newLink1ID = "3fca223a-66ba-498c-b8ed-b900525df0ea:1"
    val newLink2ID = "6c9b4c7c-f1e9-4649-b286-5635f83e0fda:1"
    val changes = roadLinkChangeClient.convertToRoadLinkChange(source).filter(change => change.changeType == RoadLinkChangeType.Split && change.oldLink.get.linkId == oldLinkID)

    runWithRollback {
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkID).get
      val newRoadLink1 = roadLinkService.getRoadLinkByLinkId(newLink1ID).get
      val newRoadLink2 = roadLinkService.getRoadLinkByLinkId(newLink2ID).get
      val careClassValue = DynamicValue(DynamicAssetValue(Seq(
        DynamicProperty("hoitoluokat_talvihoitoluokka", "single_choice", false, Seq(DynamicPropertyValue(7))),
        DynamicProperty("hoitoluokat_viherhoitoluokka", "single_choice", false, Seq(DynamicPropertyValue(3)))
      )))
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set(newLink1ID, newLink2ID), false)).thenReturn(Seq(newRoadLink1, newRoadLink2))

      val id = serviceDynamic.createWithoutTransaction(CareClass.typeId, oldLinkID, careClassValue, SideCode.BothDirections.value, Measures(0.0, 84.770), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val assetsBefore = service.assetDao.getAssetsByTypesAndLinkId(Set(CareClass.typeId), Seq(oldLinkID))
      assetsBefore.size should be(1)

      TestDynamicLinearAssetUpdater.updateByRoadLinks(CareClass.typeId, changes)

      val assetsOnOldLink = serviceDynamic.assetDao.getAssetsByTypesAndLinkId(Set(CareClass.typeId), Seq(oldLinkID))
      val assetsOnNewLink1 = serviceDynamic.assetDao.getAssetsByTypesAndLinkId(Set(CareClass.typeId), Seq(newLink1ID))
      val assetsOnNewLink2 = serviceDynamic.assetDao.getAssetsByTypesAndLinkId(Set(CareClass.typeId), Seq(newLink2ID))

      assetsOnOldLink.size should be(0)
      assetsOnNewLink1.size should be(1)
      assetsOnNewLink2.size should be(1)

      val asset1 = serviceDynamic.getPersistedAssetsByIds(CareClass.typeId, Set(id), false)
      val asset2 = serviceDynamic.getPersistedAssetsByIds(CareClass.typeId, Set(assetsOnNewLink2.head.id), false)

      asset1.head.startMeasure should be(0)
      asset1.head.endMeasure should be(newRoadLink1.length)

      asset2.head.startMeasure should be(0)
      asset2.head.endMeasure should be(newRoadLink2.length)
    }
  }

  test("When change type is Replace and asset type is RoadWidth, After samuutus processes the externalIds of the asset should stay with the projected asset and externalIds should be included in the report.") {
    val oldLinkID = "deb91a05-e182-44ae-ad71-4ba169d57e41:1"
    val newLinkID = "0a4cb6e7-67c3-411e-9446-975c53c0d054:1"
    val assetTypeId = RoadWidth.typeId
    val allChanges = roadLinkChangeClient.convertToRoadLinkChange(source)
    val changes = allChanges.filter(change => change.changeType == RoadLinkChangeType.Replace && change.oldLink.get.linkId == oldLinkID)

    runWithRollback {
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkID).get
      val newRoadLink = roadLinkService.getRoadLinkByLinkId(newLinkID).get
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set(newLinkID), false)).thenReturn(Seq(newRoadLink))
      val id = service.createWithoutTransaction(assetTypeId, oldLinkID, NumericValue(50), SideCode.BothDirections.value, Measures(0.0, 20.0), "testuser", 0L, Some(oldRoadLink), false, None, None, externalIds = Seq("externalId"))

      val assetsBefore = service.getPersistedAssetsByIds(assetTypeId, Set(id), false)

      assetsBefore.size should be(1)
      assetsBefore.head.linkId should be(oldLinkID)
      assetsBefore.head.externalIds should be(Seq("externalId"))

      TestDynamicLinearAssetUpdater.updateByRoadLinks(assetTypeId, changes)
      val assetsAfter = service.getPersistedAssetsByIds(assetTypeId, Set(id), false)
      assetsAfter.head.linkId should be(newLinkID)
      assetsAfter.head.externalIds should be(assetsBefore.head.externalIds)

      val assets = TestDynamicLinearAssetUpdater.getReport().map(a => PairAsset(a.before, a.after.headOption, a.changeType))
      assets.head.oldAsset.get.externalIds.size should be(1)
      assets.head.newAsset.get.externalIds.size should be(1)
    }
  }

  test("When change type is Replace and asset type is Maintenance Road, After samuutus processes the externalIds of the asset should stay with the projected asset and externalIds should be included in the report.") {
    val oldLinkID = "deb91a05-e182-44ae-ad71-4ba169d57e41:1"
    val newLinkID = "0a4cb6e7-67c3-411e-9446-975c53c0d054:1"
    val assetTypeId = MaintenanceRoadAsset.typeId
    val allChanges = roadLinkChangeClient.convertToRoadLinkChange(source)
    val changes = allChanges.filter(change => change.changeType == RoadLinkChangeType.Replace && change.oldLink.get.linkId == oldLinkID)

    runWithRollback {
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkID).get
      val newRoadLink = roadLinkService.getRoadLinkByLinkId(newLinkID).get
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set(newLinkID), false)).thenReturn(Seq(newRoadLink))
      val id = service.createWithoutTransaction(assetTypeId, oldLinkID, NumericValue(50), SideCode.BothDirections.value, Measures(0.0, 20.0), "testuser", 0L, Some(oldRoadLink), false, None, None, externalIds = Seq("externalId"))

      val assetsBefore = service.getPersistedAssetsByIds(assetTypeId, Set(id), false)

      assetsBefore.size should be(1)
      assetsBefore.head.linkId should be(oldLinkID)
      assetsBefore.head.externalIds should be(Seq("externalId"))

      TestDynamicLinearAssetUpdater.updateByRoadLinks(assetTypeId, changes)
      val assetsAfter = service.getPersistedAssetsByIds(assetTypeId, Set(id), false)
      assetsAfter.head.linkId should be(newLinkID)
      assetsAfter.head.externalIds should be(assetsBefore.head.externalIds)

      val assets = TestDynamicLinearAssetUpdater.getReport().map(a => PairAsset(a.before, a.after.headOption, a.changeType))
      assets.head.oldAsset.get.externalIds.size should be(1)
      assets.head.newAsset.get.externalIds.size should be(1)
    }
  }
}
