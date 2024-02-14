package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.{DigiroadEventBus, MValueCalculator}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client._
import fi.liikennevirasto.digiroad2.dao.RoadLinkOverrideDAO.LinkTypeDao
import fi.liikennevirasto.digiroad2.dao.RoadLinkDAO
import fi.liikennevirasto.digiroad2.dao.linearasset.{PostGISLinearAssetDao, PostGISSpeedLimitDao}
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.{Measures, SpeedLimitService}
import fi.liikennevirasto.digiroad2.util.LinkIdGenerator
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.{FunSuite, Matchers}

class SpeedLimitUpdaterSpec extends FunSuite with Matchers with UpdaterUtilsSuite {
  val speedLimitDao = new PostGISSpeedLimitDao(roadLinkService)
  val speedLimitService = new SpeedLimitService(mockEventBus,mockRoadLinkService)
  val speedLimitServiceNoMock = new SpeedLimitService(mockEventBus,roadLinkService)
  protected def roadLinkDAO: RoadLinkDAO = new RoadLinkDAO

  object TestLinearAssetUpdater extends SpeedLimitUpdater(speedLimitService) {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def eventBus: DigiroadEventBus = mockEventBus
    override def roadLinkClient: RoadLinkClient = mockRoadLinkClient
  }

  object TestLinearAssetUpdaterNoRoadLinkMock extends SpeedLimitUpdater(speedLimitServiceNoMock) {
    override def withDynTransaction[T](f: => T): T = f
    override def eventBus: DigiroadEventBus = mockEventBus
  }
  
  def createSpeedLimit(measures: Measures, value: Value, link: RoadLink, sideCode:SideCode = SideCode.BothDirections ): Long = {
    speedLimitService.createWithoutTransaction(SpeedLimitAsset.typeId, link.linkId, value, sideCode.value,
      measures, "testuser", 0L, Some(link), false, None, None)
  }

  override def changeRemove(oldRoadLinkId: String): RoadLinkChange = {
    val generatedGeometry = generateGeometry(0, 10)
    RoadLinkChange(
      changeType = RoadLinkChangeType.Remove,
      oldLink = Some(RoadLinkInfo(linkId = oldRoadLinkId, linkLength = generatedGeometry._2,
        geometry = generatedGeometry._1, roadClass = MTKClassWidth.CarRoad_Ia.value,
        adminClass = Municipality,
        municipality = Some(0),
        trafficDirection = TrafficDirection.BothDirections)),
      newLinks = Seq.empty[RoadLinkInfo],
      replaceInfo = Seq.empty[ReplaceInfo])
  }

  override def changeReplaceNewVersion(oldRoadLinkId: String, newRoadLikId: String): RoadLinkChange = {
    val (oldLinkGeometry, oldId) = (generateGeometry(0, 9), oldRoadLinkId)
    val (newLinkGeometry1, newLinkId1) = (generateGeometry(0, 9), newRoadLikId)

    RoadLinkChange(
      changeType = RoadLinkChangeType.Replace,
      oldLink = Some(RoadLinkInfo(linkId = oldId, linkLength = oldLinkGeometry._2,
        geometry = oldLinkGeometry._1, roadClass = MTKClassWidth.CarRoad_Ia.value,
        adminClass = Municipality,
        municipality = Some(60),
        trafficDirection = TrafficDirection.BothDirections)),
      newLinks = Seq(
        RoadLinkInfo(
          linkId = newLinkId1,
          linkLength = newLinkGeometry1._2,
          geometry = newLinkGeometry1._1,
          roadClass = MTKClassWidth.CarRoad_Ia.value,
          adminClass = Municipality,
          municipality = Some(60),
          trafficDirection = TrafficDirection.BothDirections
        )),
      replaceInfo =
        List(
          ReplaceInfo(Option(oldId), Option(newLinkId1),
            oldFromMValue = Option(0.0), oldToMValue = Option(8), newFromMValue = Option(0.0), newToMValue = Option(newLinkGeometry1._2), false))
    )
  }
  
  def changeReplacechangeReplaceLengthenedFromEndBothDirections(oldRoadLinkId: String): RoadLinkChange = {
    val (oldLinkGeometry, oldId) = (generateGeometry(0, 5), oldRoadLinkId)
    val (newLinkGeometry1, newLinkId1) = (generateGeometry(0, 10), linkId1)

    RoadLinkChange(
      changeType = RoadLinkChangeType.Replace,
      oldLink = Some(RoadLinkInfo(linkId = oldId, linkLength = oldLinkGeometry._2,
        geometry = oldLinkGeometry._1, roadClass = MTKClassWidth.CarRoad_Ia.value,
        adminClass = Municipality,
        municipality = Some(60),
        trafficDirection = TrafficDirection.TowardsDigitizing)),
      newLinks = Seq(
        RoadLinkInfo(
          linkId = newLinkId1,
          linkLength = newLinkGeometry1._2,
          geometry = newLinkGeometry1._1,
          roadClass = MTKClassWidth.CarRoad_Ia.value,
          adminClass = Municipality,
          municipality = Some(60),
          trafficDirection = TrafficDirection.BothDirections
        )),
      replaceInfo =
        List(
          ReplaceInfo(Option(oldRoadLinkId), Option(newLinkId1),
            oldFromMValue = Option(0.0), oldToMValue = Option(4), newFromMValue = Option(0.0), newToMValue = Option(newLinkGeometry1._2), false))
    )
  }
  
  test("case 1 links under asset is split, smoke test") {
    val linkId = linkId5
    val newLinks = newLinks1_2_4
    val changes = roadLinkChangeClient.convertToRoadLinkChange(source)

    runWithRollback {
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(linkId).get
      val oldRoadLinkRaw = roadLinkService.getExpiredRoadLinkByLinkIdNonEncrished(linkId)
      when(mockRoadLinkService.fetchRoadlinkAndComplementary(linkId)).thenReturn(oldRoadLinkRaw)
      when(mockRoadLinkService.fetchRoadlinksByIds(any[Set[String]])).thenReturn(Seq.empty[RoadLinkFetched])
      val id = speedLimitDao.createSpeedLimit("testCreator", linkId, Measures(0, 56.061), SideCode.BothDirections,
        SpeedLimitValue(30), Some(0L), Some(DateTime.parse("2020-01-01")), Some("testModifier"), Some(DateTime.parse("2022-01-01")), LinkGeomSource.NormalLinkInterface).get
      val assetsBefore = speedLimitService.getPersistedAssetsByIds(SpeedLimitAsset.typeId, Set(id), false)
      
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdaterNoRoadLinkMock.updateByRoadLinks(SpeedLimitAsset.typeId, changes)
      val assetsAfter = speedLimitService.getPersistedAssetsByLinkIds(SpeedLimitAsset.typeId, newLinks, false)
      assetsAfter.size should be(3)
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
      assetsAfter.map(v => v.value.get should be(SpeedLimitValue(30)))
    }
  }
  
  test("Create unknown speed limit to a new car road, check that no unknown limit is generated to illegal targets, "){
    val changes = roadLinkChangeClient.convertToRoadLinkChange(source).filter(_.changeType == RoadLinkChangeType.Add)

    runWithRollback {
      val newCarRoadId = "624df3a8-b403-4b42-a032-41d4b59e1840:1"
      LinkTypeDao.insertValues(newCarRoadId, Some("test"), SingleCarriageway.value)
      val newPedestrianRoadId = "f2eba575-f306-4c37-b49d-a4d27a3fc049:1"
      LinkTypeDao.insertValues(newPedestrianRoadId, Some("test"), CycleOrPedestrianPath.value)
      val newHardShoulderId = "e24e9e6c-e011-4889-9ca4-4a13734d0f41: 1"
      LinkTypeDao.insertValues(newHardShoulderId, Some("test"), HardShoulder.value)
      val newTractorRoadId = "a15cf59b-c17c-4b6d-8e9b-a558143d0d47:1"
      LinkTypeDao.insertValues(newTractorRoadId, Some("test"), TractorRoad.value)
      when(mockRoadLinkService.fetchRoadlinksByIds(any[Set[String]])).thenReturn(Seq.empty[RoadLinkFetched])
      TestLinearAssetUpdaterNoRoadLinkMock.updateByRoadLinks(SpeedLimitAsset.typeId, changes)
      TestLinearAssetUpdaterNoRoadLinkMock.updateByRoadLinks(SpeedLimitAsset.typeId, changes) // check that no overlapping assets are created even if the process is run twice
      val unknown = speedLimitService.getUnknownByLinkIds(Set(newCarRoadId, newPedestrianRoadId, newHardShoulderId),newTransaction = false)
      unknown.size should be(1)
      unknown.head.linkId should be(newCarRoadId)
    }
  }
  
  test("Remove unknown speed limit when links is removed") {
    val linkId = generateRandomLinkId()
    val geometry = generateGeometry(0, 10)
    val oldRoadLink = createRoadLink(linkId,geometry,functionalClassI = FunctionalClass5)
    val change = changeRemove(linkId)

    runWithRollback {
      when(mockRoadLinkService.getRoadLinksAndComplementariesByLinkIds(Set(linkId))).thenReturn(Seq(oldRoadLink))
      when(mockRoadLinkService.fetchRoadlinksByIds(any[Set[String]])).thenReturn(Seq.empty[RoadLinkFetched])
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set.empty[String], false)).thenReturn(Seq.empty[RoadLink])
      speedLimitService.persistUnknown(Seq(UnknownSpeedLimit(linkId,60,Municipality)))
      val unknownBefore = speedLimitService.getUnknownByLinkIds(Set(linkId))
      unknownBefore .size should be(1)
      TestLinearAssetUpdater.updateByRoadLinks(SpeedLimitAsset.typeId, Seq(change))
      val unknown = speedLimitService.getUnknownByLinkIds(Set(linkId), newTransaction = false)
      unknown .size should be(0)
    }
  }

  test("Move unknown speed limit into new links") {
    val linkId = generateRandomKmtkId()
    val linkIdVersion1 = s"$linkId:1"
    val linkIdVersion2 = s"$linkId:2"
    val geometry = generateGeometry(0, 9)
    val newRoadLink = createRoadLink(linkIdVersion2,geometry,functionalClassI = FunctionalClass5)
    val change = changeReplaceNewVersion(linkIdVersion1, linkIdVersion2)

    runWithRollback {
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set(linkIdVersion2))).thenReturn(Seq(newRoadLink))
      when(mockRoadLinkService.fetchRoadlinksByIds(any[Set[String]])).thenReturn(Seq.empty[RoadLinkFetched])
      speedLimitService.persistUnknown(Seq(UnknownSpeedLimit(linkIdVersion1, 60, Municipality)))
      val unknownBefore = speedLimitService.getUnknownByLinkIds(Set(linkIdVersion1))
      unknownBefore.size should be(1)

      TestLinearAssetUpdater.updateByRoadLinks(SpeedLimitAsset.typeId, Seq(change))
      val unknown = speedLimitService.getUnknownByLinkIds(Set(linkIdVersion2),newTransaction = false)
      unknown.size should be(1)
    }
  }
  
  test("case 8.1 Road changes to two ways raise links into work list"){
    val linkId = generateRandomLinkId()
    val geometry = generateGeometry(0, 5)
    val geometryNew = generateGeometry(0, 10)
    val oldRoadLink =  createRoadLink(linkId,geometry,functionalClassI = FunctionalClass5,trafficDirection = TrafficDirection.TowardsDigitizing)
    val newLink = createRoadLink(linkId1,geometryNew,functionalClassI = FunctionalClass5,trafficDirection = TrafficDirection.BothDirections)
    val change = changeReplacechangeReplaceLengthenedFromEndBothDirections(linkId)

    runWithRollback {
      when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(linkId, false)).thenReturn(Some(oldRoadLink))
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set(linkId1))).thenReturn(Seq(newLink))
      when(mockRoadLinkService.fetchRoadlinksByIds(any[Set[String]])).thenReturn(Seq.empty[RoadLinkFetched])
      when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(linkId1, false)).thenReturn(Some(newLink))
      TestLinearAssetUpdater.updateByRoadLinks(SpeedLimitAsset.typeId, Seq(change))
      val unknown =  speedLimitService.getUnknownByLinkIds(Set(linkId1),newTransaction = false)
      unknown .size should be(1)
    }
  }

  test("Replace. Given a Road Link that is replaced with a New Link; " +
    "when the New Link has grown outside of Old Link geometry from the beginning; " +
    "then the Speed Limit Asset on New Link should be New Link's length") {
    val oldLinkID = "be36fv60-6813-4b01-a57b-67136dvv6862:1"
    val newLinkID = "007b3d46-526d-46c0-91a5-9e624cbb073b:1"

    val allChanges = roadLinkChangeClient.convertToRoadLinkChange(source)
    val changes = allChanges.filter(change => change.changeType == RoadLinkChangeType.Replace && change.oldLink.get.linkId == oldLinkID)

    runWithRollback {
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkID).get
      val fetchedRoadLinks = roadLinkDAO.fetchExpiredRoadLink(oldLinkID)
      when(mockRoadLinkService.fetchRoadlinkAndComplementary(oldLinkID)).thenReturn(Some(fetchedRoadLinks.head))
      val newRoadLink = roadLinkService.getRoadLinkByLinkId(newLinkID).get
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set(newLinkID), false)).thenReturn(Seq(newRoadLink))
      when(mockRoadLinkService.fetchRoadlinksByIds(any[Set[String]])).thenReturn(Seq.empty[RoadLinkFetched])
      val id = speedLimitService.createWithoutTransaction(SpeedLimitAsset.typeId, oldLinkID, SpeedLimitValue(50), SideCode.BothDirections.value, Measures(0.0, oldRoadLink.length), "testuser", 0L, Some(oldRoadLink), false, None, None)

      val assetsBefore = speedLimitService.getPersistedAssetsByIds(SpeedLimitAsset.typeId, Set(id), false)
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks(SpeedLimitAsset.typeId, changes)
      val assetsAfter = speedLimitService.getPersistedAssetsByIds(SpeedLimitAsset.typeId, Set(id), false)
      assetsAfter.size should be(1)

      val assetLength = (assetsAfter.head.endMeasure - assetsAfter.head.startMeasure)
      assetsAfter.head.linkId should be(newLinkID)
      assetLength should be(newRoadLink.length)
    }
  }

  test("Replace. Given a Road Link that is replaced with a New Link; " +
    "when the New Link has grown outside of Old Link geometry from the end; " +
    "then the Speed Limit Asset on New Link should be New Link's length") {
    val oldLinkID = "18ce7a01-0ddc-47a2-9df1-c8e1be193516:1"
    val newLinkID = "016200a1-5dd4-47cc-8f4f-38ab4934eef9:1"

    val allChanges = roadLinkChangeClient.convertToRoadLinkChange(source)
    val changes = allChanges.filter(change => change.changeType == RoadLinkChangeType.Replace && change.oldLink.get.linkId == oldLinkID)

    runWithRollback {
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkID).get
      val newRoadLink = roadLinkService.getRoadLinkByLinkId(newLinkID).get
      val fetchedRoadLinks = roadLinkDAO.fetchExpiredRoadLink(oldLinkID)
      when(mockRoadLinkService.fetchRoadlinkAndComplementary(oldLinkID)).thenReturn(Some(fetchedRoadLinks.head))
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set(newLinkID), false)).thenReturn(Seq(newRoadLink))
      when(mockRoadLinkService.fetchRoadlinksByIds(any[Set[String]])).thenReturn(Seq.empty[RoadLinkFetched])
      val id = speedLimitService.createWithoutTransaction(SpeedLimitAsset.typeId, oldLinkID, SpeedLimitValue(50), SideCode.BothDirections.value, Measures(0.0, oldRoadLink.length), "testuser", 0L, Some(oldRoadLink), false, None, None)

      val assetsBefore = speedLimitService.getPersistedAssetsByIds(SpeedLimitAsset.typeId, Set(id), false)
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks(SpeedLimitAsset.typeId, changes)
      val assetsAfter = speedLimitService.getPersistedAssetsByIds(SpeedLimitAsset.typeId, Set(id), false)
      assetsAfter.size should be(1)

      val assetLength = (assetsAfter.head.endMeasure - assetsAfter.head.startMeasure)
      assetsAfter.head.linkId should be(newLinkID)
      assetLength should be(newRoadLink.length)
    }
  }

  test("Replace. Given two Road Links that are replaced with a single New Link; " +
    "When the Old Links leave a gap between them; " +
    "Then the assets on the New Link should not grow to fill the gap") {
    val oldLinkID = "be36fv60-6813-4b01-a57b-67136dvv6862:1"
    val oldLinkID2 = "38ebf780-ae0c-49f3-8679-a6e45ff8f56f:1"
    val newLinkID = "007b3d46-526d-46c0-91a5-9e624cbb073b:1"

    val allChanges = roadLinkChangeClient.convertToRoadLinkChange(source)
    val changes = allChanges.filter(change => change.newLinks.map(_.linkId).contains(newLinkID) && change.changeType == RoadLinkChangeType.Replace)

    runWithRollback {
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkID).get
      val oldRoadLink2 = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkID2).get
      val newRoadLink = roadLinkService.getRoadLinkByLinkId(newLinkID).get
      val fetchedRoadLinks = roadLinkDAO.fetchExpiredRoadLink(oldLinkID)
      when(mockRoadLinkService.fetchRoadlinkAndComplementary(oldLinkID)).thenReturn(Some(fetchedRoadLinks.head))
      val fetchedRoadLinks2 = roadLinkDAO.fetchExpiredRoadLink(oldLinkID2)
      when(mockRoadLinkService.fetchRoadlinkAndComplementary(oldLinkID2)).thenReturn(Some(fetchedRoadLinks2.head))
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set(newLinkID), false)).thenReturn(Seq(newRoadLink))
      when(mockRoadLinkService.fetchRoadlinksByIds(any[Set[String]])).thenReturn(Seq.empty[RoadLinkFetched])

      val id = speedLimitService.createWithoutTransaction(SpeedLimitAsset.typeId, oldLinkID, SpeedLimitValue(40), SideCode.BothDirections.value, Measures(0.0, oldRoadLink.length), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val id2 = speedLimitService.createWithoutTransaction(SpeedLimitAsset.typeId, oldLinkID2, SpeedLimitValue(50), SideCode.BothDirections.value, Measures(0.0, oldRoadLink2.length), "testuser", 0L, Some(oldRoadLink2), false, None, None)

      val assetsBefore = speedLimitService.getPersistedAssetsByIds(SpeedLimitAsset.typeId, Set(id, id2), false)
      assetsBefore.size should be(2)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks(SpeedLimitAsset.typeId, changes)
      val assetsAfter = speedLimitService.getPersistedAssetsByIds(SpeedLimitAsset.typeId, Set(id, id2), false).sortBy(_.startMeasure)
      assetsAfter.size should be(2)

      val startAsset = assetsAfter.head
      val endAsset = assetsAfter.last
      val startAssetLength = (startAsset.endMeasure - startAsset.startMeasure)
      val endAssetLength = (endAsset.endMeasure - endAsset.startMeasure)
      val assetGap = (endAsset.startMeasure - startAsset.endMeasure)

      startAsset.linkId should be(newLinkID)
      endAsset.linkId should be(newLinkID)
      MValueCalculator.roundMeasure(startAssetLength,3) should be(304.332)
      MValueCalculator.roundMeasure(endAssetLength,3) should be(66.325)
      MValueCalculator.roundMeasure(assetGap, 3) should be(64.968)
    }
  }

  test("Dont create unknown speed limit for WinterRoads"){
    val newLinkId = LinkIdGenerator.generateRandom()
    val changes = Seq(changeAddWinterRoad(newLinkId))

    runWithRollback {
      val unknownsBefore =  speedLimitService.getUnknownByLinkIds(Set(newLinkId),newTransaction = false)
      unknownsBefore.size should be(0)
      TestLinearAssetUpdaterNoRoadLinkMock.updateByRoadLinks(SpeedLimitAsset.typeId, changes)
      val unknownsAfter =  speedLimitService.getUnknownByLinkIds(Set(newLinkId),newTransaction = false)
      unknownsAfter.size should be(0)
    }
  }

  test("Split. The second new link changes to one way. The side code of the speed limit should change as well.") {
    val oldLinkID = "609d430a-de96-4cb7-987c-3caa9c72c08d:1"
    val newLinkID = "4b061477-a7bc-4b72-b5fe-5484e3cec03d:1"
    val newLinkID2 = "b0b54052-7a0e-4714-80e0-9de0ea62a347:1"

    val allChanges = roadLinkChangeClient.convertToRoadLinkChange(source)
    val changes = allChanges.filter(change => change.changeType == RoadLinkChangeType.Split && change.oldLink.get.linkId == oldLinkID)

    runWithRollback {
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkID).get
      val newRoadLink1 = roadLinkService.getRoadLinkByLinkId(newLinkID).get
      val newRoadLink2 = roadLinkService.getRoadLinkByLinkId(newLinkID2).get
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set(newLinkID, newLinkID2), false)).thenReturn(Seq(newRoadLink1, newRoadLink2))
      when(mockRoadLinkService.fetchRoadlinksByIds(any[Set[String]])).thenReturn(Seq.empty[RoadLinkFetched])

      val id = speedLimitDao.createSpeedLimit("testuser", oldLinkID, Measures(0, 45.230), SideCode.BothDirections, SpeedLimitValue(40), Some(1L), None, None, None, LinkGeomSource.NormalLinkInterface)

      val assetsBefore = speedLimitService.getPersistedAssetsByIds(SpeedLimitAsset.typeId, Set(id.get), false)
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks(SpeedLimitAsset.typeId, changes)
      val assetsAfter = speedLimitService.fetchExistingAssetsByLinksIdsString(SpeedLimitAsset.typeId, Set(newLinkID, newLinkID2), Set(), false).sortBy(_.linkId)
      assetsAfter.size should be(2)

      assetsAfter.head.sideCode should be(SideCode.BothDirections.value)
      assetsAfter.last.sideCode should be(SideCode.AgainstDigitizing.value)
    }
  }

}
