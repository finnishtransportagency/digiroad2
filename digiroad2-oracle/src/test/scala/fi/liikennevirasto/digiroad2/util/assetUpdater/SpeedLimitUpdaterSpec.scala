package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.{DigiroadEventBus, MValueCalculator}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.RoadLinkChangeType.{Replace, Split}
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
      assetsAfter.map(v => v.value.get should be(SpeedLimitValue(30)))
    }
  }
  
  test("Create unknown speed limit to a new car road, check that no unknown limit is generated to illegal targets."){
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
      val newPlannedCarRoadId = "16692115-663f-4401-8bac-41456b73b4c2:1"
      LinkTypeDao.insertValues(newPlannedCarRoadId, Some("test"), SingleCarriageway.value)
      when(mockRoadLinkService.fetchRoadlinksByIds(any[Set[String]])).thenReturn(Seq.empty[RoadLinkFetched])
      TestLinearAssetUpdaterNoRoadLinkMock.updateByRoadLinks(SpeedLimitAsset.typeId, changes)
      TestLinearAssetUpdaterNoRoadLinkMock.updateByRoadLinks(SpeedLimitAsset.typeId, changes) // check that no overlapping assets are created even if the process is run twice
      val unknown = speedLimitService.getUnknownByLinkIds(Set(newCarRoadId, newPedestrianRoadId, newHardShoulderId, newPlannedCarRoadId),newTransaction = false)
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
      when(mockRoadLinkService.getRoadLinksByLinkIds(Set.empty, false)).thenReturn(Seq.empty[RoadLink])
      speedLimitService.persistUnknown(Seq(UnknownSpeedLimit(linkId,60,Municipality)))
      val unknownBefore = speedLimitService.getUnknownByLinkIds(Set(linkId), newTransaction = false)
      unknownBefore.size should be(1)
      TestLinearAssetUpdater.updateByRoadLinks(SpeedLimitAsset.typeId, Seq(change))
      val unknown = speedLimitService.getUnknownByLinkIds(Set(linkId), newTransaction = false)
      unknown.size should be(0)
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
      when(mockRoadLinkService.getRoadLinksByLinkIds(Set(linkIdVersion2), false)).thenReturn(Seq(newRoadLink))
      speedLimitService.persistUnknown(Seq(UnknownSpeedLimit(linkIdVersion1, 60, Municipality)))
      val unknownBefore = speedLimitService.getUnknownByLinkIds(Set(linkIdVersion1), newTransaction = false)
      unknownBefore.size should be(1)

      TestLinearAssetUpdater.updateByRoadLinks(SpeedLimitAsset.typeId, Seq(change))
      val unknown = speedLimitService.getUnknownByLinkIds(Set(linkIdVersion2),newTransaction = false)
      unknown.size should be(1)
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
      val roadLinkFetched = roadLinkDAO.fetchExpiredRoadLink(oldLinkID).head
      val roadLink = RoadLink(linkId = roadLinkFetched.linkId, geometry = roadLinkFetched.geometry, length = roadLinkFetched.length,
        administrativeClass = roadLinkFetched.administrativeClass, functionalClass = FunctionalClass3.value,
        trafficDirection = roadLinkFetched.trafficDirection, linkType = SingleCarriageway, modifiedAt = None,
        modifiedBy = None, attributes = Map("MUNICIPALITYCODE" -> BigInt(99)), constructionType = roadLinkFetched.constructionType, linkSource = roadLinkFetched.linkSource)

      when(mockRoadLinkService.fetchRoadlinkAndComplementary(oldLinkID)).thenReturn(Some(roadLinkFetched))
      when(mockRoadLinkService.enrichFetchedRoadLinks(Seq(roadLinkFetched))).thenReturn(Seq(roadLink))
      val newRoadLink = roadLinkService.getRoadLinkByLinkId(newLinkID).get
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set(newLinkID), false)).thenReturn(Seq(newRoadLink))
      when(mockRoadLinkService.fetchRoadlinksByIds(any[Set[String]])).thenReturn(Seq.empty[RoadLinkFetched])
      when(mockRoadLinkService.getRoadLinksByLinkIds(Set.empty, false)).thenReturn(Seq.empty[RoadLink])
      val id = speedLimitService.createWithoutTransaction(SpeedLimitAsset.typeId, oldLinkID, SpeedLimitValue(50), SideCode.BothDirections.value, Measures(0.0, oldRoadLink.length), "testuser", 0L, Some(oldRoadLink), false, None, None)

      val assetsBefore = speedLimitService.getPersistedAssetsByIds(SpeedLimitAsset.typeId, Set(id), false)
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks(SpeedLimitAsset.typeId, changes)
      val assetsAfter = speedLimitService.getPersistedAssetsByIds(SpeedLimitAsset.typeId, Set(id), false)
      assetsAfter.size should be(1)
      assetsAfter.head.id should be(id)

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
      val roadLinkFetched = roadLinkDAO.fetchExpiredRoadLink(oldLinkID).head
      val roadLink = RoadLink(linkId = roadLinkFetched.linkId, geometry = roadLinkFetched.geometry, length = roadLinkFetched.length,
        administrativeClass = roadLinkFetched.administrativeClass, functionalClass = FunctionalClass3.value,
        trafficDirection = roadLinkFetched.trafficDirection, linkType = SingleCarriageway, modifiedAt = None,
        modifiedBy = None, attributes = Map("MUNICIPALITYCODE" -> BigInt(99)), constructionType = roadLinkFetched.constructionType,
        linkSource = roadLinkFetched.linkSource)

      when(mockRoadLinkService.fetchRoadlinkAndComplementary(oldLinkID)).thenReturn(Some(roadLinkFetched))
      when(mockRoadLinkService.enrichFetchedRoadLinks(Seq(roadLinkFetched))).thenReturn(Seq(roadLink))
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set(newLinkID), false)).thenReturn(Seq(newRoadLink))
      when(mockRoadLinkService.fetchRoadlinksByIds(any[Set[String]])).thenReturn(Seq.empty[RoadLinkFetched])
      when(mockRoadLinkService.getRoadLinksByLinkIds(Set.empty, false)).thenReturn(Seq.empty[RoadLink])
      val id = speedLimitService.createWithoutTransaction(SpeedLimitAsset.typeId, oldLinkID, SpeedLimitValue(50), SideCode.BothDirections.value, Measures(0.0, oldRoadLink.length), "testuser", 0L, Some(oldRoadLink), false, None, None)

      val assetsBefore = speedLimitService.getPersistedAssetsByIds(SpeedLimitAsset.typeId, Set(id), false)
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks(SpeedLimitAsset.typeId, changes)
      val assetsAfter = speedLimitService.getPersistedAssetsByIds(SpeedLimitAsset.typeId, Set(id), false)
      assetsAfter.size should be(1)
      assetsAfter.head.id should be(id)

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
      val roadLinkFetched = roadLinkDAO.fetchExpiredRoadLink(oldLinkID).head
      val roadLink = RoadLink(linkId = roadLinkFetched.linkId, geometry = roadLinkFetched.geometry, length = roadLinkFetched.length,
        administrativeClass = roadLinkFetched.administrativeClass, functionalClass = FunctionalClass3.value,
        trafficDirection = roadLinkFetched.trafficDirection, linkType = SingleCarriageway, modifiedAt = None,
        modifiedBy = None, attributes = Map("MUNICIPALITYCODE" -> BigInt(99)), constructionType = roadLinkFetched.constructionType,
        linkSource = roadLinkFetched.linkSource)

      when(mockRoadLinkService.fetchRoadlinkAndComplementary(oldLinkID)).thenReturn(Some(roadLinkFetched))
      when(mockRoadLinkService.enrichFetchedRoadLinks(Seq(roadLinkFetched))).thenReturn(Seq(roadLink))

      val roadLinkFetched2 = roadLinkDAO.fetchExpiredRoadLink(oldLinkID2).head
      val roadLink2 = RoadLink(linkId = roadLinkFetched2.linkId, geometry = roadLinkFetched2.geometry, length = roadLinkFetched2.length,
        administrativeClass = roadLinkFetched2.administrativeClass, functionalClass = FunctionalClass3.value,
        trafficDirection = roadLinkFetched2.trafficDirection, linkType = SingleCarriageway, modifiedAt = None,
        modifiedBy = None, attributes = Map("MUNICIPALITYCODE" -> BigInt(99)), constructionType = roadLinkFetched2.constructionType,
        linkSource = roadLinkFetched2.linkSource)

      when(mockRoadLinkService.fetchRoadlinkAndComplementary(oldLinkID2)).thenReturn(Some(roadLinkFetched2))
      when(mockRoadLinkService.enrichFetchedRoadLinks(Seq(roadLinkFetched2))).thenReturn(Seq(roadLink2))
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set(newLinkID), false)).thenReturn(Seq(newRoadLink))
      when(mockRoadLinkService.fetchRoadlinksByIds(any[Set[String]])).thenReturn(Seq.empty[RoadLinkFetched])
      when(mockRoadLinkService.getRoadLinksByLinkIds(Set.empty, false)).thenReturn(Seq.empty[RoadLink])

      val id = speedLimitService.createWithoutTransaction(SpeedLimitAsset.typeId, oldLinkID, SpeedLimitValue(40), SideCode.BothDirections.value, Measures(0.0, oldRoadLink.length), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val id2 = speedLimitService.createWithoutTransaction(SpeedLimitAsset.typeId, oldLinkID2, SpeedLimitValue(50), SideCode.BothDirections.value, Measures(0.0, oldRoadLink2.length), "testuser", 0L, Some(oldRoadLink2), false, None, None)

      val assetsBefore = speedLimitService.getPersistedAssetsByIds(SpeedLimitAsset.typeId, Set(id, id2), false)
      assetsBefore.size should be(2)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks(SpeedLimitAsset.typeId, changes)
      val assetsAfter = speedLimitService.getPersistedAssetsByIds(SpeedLimitAsset.typeId, Set(id, id2), false).sortBy(_.startMeasure)
      assetsAfter.size should be(2)
      assetsAfter.map(_.id).sorted should be(assetsBefore.map(_.id).sorted)

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
      when(mockRoadLinkService.getRoadLinksByLinkIds(Set.empty, false)).thenReturn(Seq.empty[RoadLink])

      val id = speedLimitDao.createSpeedLimit("testuser", oldLinkID, Measures(0, 45.230), SideCode.BothDirections, SpeedLimitValue(40), Some(1L), None, None, None, LinkGeomSource.NormalLinkInterface)

      val assetsBefore = speedLimitService.getPersistedAssetsByIds(SpeedLimitAsset.typeId, Set(id.get), false)
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks(SpeedLimitAsset.typeId, changes)
      val assetsAfter = speedLimitService.fetchExistingAssetsByLinksIdsString(SpeedLimitAsset.typeId, Set(newLinkID, newLinkID2), Set(), false).sortBy(_.linkId)
      assetsAfter.size should be(2)
      assetsAfter.map(_.id).contains(id)

      assetsAfter.head.sideCode should be(SideCode.BothDirections.value)
      assetsAfter.last.sideCode should be(SideCode.AgainstDigitizing.value)
    }
  }

  test("Check that new unknowns are generated to only legal targets and old unknowns are removed") {
    val oldCarRoad = "4f8a33d6-a939-4934-847d-d7b10193b7e9:1"
    val newCarRoad = "4f8a33d6-a939-4934-847d-d7b10193b7e9:2"
    val oldCarRoadWithExistingLimit = "875766ca-83b1-450b-baf1-db76d59176be:1"
    val newCarRoadWithExistingLimit = "6eec9a4a-bcac-4afb-afc8-f4e6d40ec571:1"
    val oldPedestrian = "3a832249-d3b9-4c22-9b08-c7e9cd98cbd7:1"
    val newPedestrian1 = "581687d9-f4d5-4fa5-9e44-87026eb74774:1"
    val newPedestrian2 = "6ceeebf4-2351-46f0-b151-3ed02c7cfc05:1"
    val oldTractor = "6d283aeb-a0f5-4606-8154-c6702bd69f2e:1"
    val newTractor = "769e6848-e77b-44d3-9d38-e63cdc7b1677:1"
    val oldPrivate = "b9073cbd-5fb7-4dcb-9a0d-b368c948f1d5:1"
    val newPrivate = "7da294fc-cb5e-48e3-8a2d-47addf586147:1"

    val oldLinkIds = Seq(oldCarRoad, oldCarRoadWithExistingLimit, oldPedestrian, oldTractor, oldPrivate)
    val newLinkIds = Seq(newCarRoad, newCarRoadWithExistingLimit, newPedestrian1, newPedestrian2, newTractor, newPrivate)

    val allChanges = roadLinkChangeClient.convertToRoadLinkChange(source)
    val changes = allChanges.filter(change => (change.changeType == Split || change.changeType == Replace) && oldLinkIds.contains(change.oldLink.get.linkId))

    runWithRollback {
      LinkTypeDao.insertValues(oldCarRoad, Some("test"), SingleCarriageway.value)
      LinkTypeDao.insertValues(newCarRoad, Some("test"), SingleCarriageway.value)
      LinkTypeDao.insertValues(oldCarRoadWithExistingLimit, Some("test"), SingleCarriageway.value)
      LinkTypeDao.insertValues(newCarRoadWithExistingLimit, Some("test"), SingleCarriageway.value)
      LinkTypeDao.insertValues(oldPedestrian, Some("test"), CycleOrPedestrianPath.value)
      LinkTypeDao.insertValues(newPedestrian1, Some("test"), CycleOrPedestrianPath.value)
      LinkTypeDao.insertValues(newPedestrian2, Some("test"), CycleOrPedestrianPath.value)
      LinkTypeDao.insertValues(oldCarRoad, Some("test"), TractorRoad.value)
      LinkTypeDao.insertValues(newCarRoad, Some("test"), TractorRoad.value)
      LinkTypeDao.insertValues(oldPrivate, Some("test"), SingleCarriageway.value)
      LinkTypeDao.insertValues(newPrivate, Some("test"), SingleCarriageway.value)

      val oldLinks = TestLinearAssetUpdaterNoRoadLinkMock.roadLinkService.getExistingAndExpiredRoadLinksByLinkIds(oldLinkIds.toSet, false)

      speedLimitService.persistUnknown(oldLinks.map(link => UnknownSpeedLimit(link.linkId, link.municipalityCode, link.administrativeClass)), false)
      speedLimitDao.createSpeedLimit("testuser", oldCarRoadWithExistingLimit, Measures(0, 36.783), SideCode.BothDirections, SpeedLimitValue(40), Some(1L), None, None, None, LinkGeomSource.NormalLinkInterface)
      val unknownsBefore = speedLimitService.getUnknownByLinkIds((oldLinkIds ++ newLinkIds).toSet, false)
      unknownsBefore.size should be(5)

      TestLinearAssetUpdaterNoRoadLinkMock.updateByRoadLinks(SpeedLimitAsset.typeId, changes)
      val unknownsAfter = speedLimitService.getUnknownByLinkIds((oldLinkIds ++ newLinkIds).toSet, false)
      unknownsAfter.size should be(1)
      unknownsAfter.head.linkId should be(newCarRoad)
    }
  }

  test("Split. Given a Road Link that is split into 2 new Links where there is no equivalence in the beginning; when the beginning of the link is expired; then the Linear Asset (SpeedLimit) on the expired Link should be moved to new link and no expired asset should have been created or expired and the length should be changed according to the new link.") {
    val oldLinkID = "cc2bc598-8527-4600-861f-4c70eaacae61:1"
    val newLinkID = "46b888db-6fcd-4e8c-afbd-6532023a1759:1"
    val changes = roadLinkChangeClient.convertToRoadLinkChange(source).filter(change => change.changeType == RoadLinkChangeType.Split && change.oldLink.get.linkId == oldLinkID)

    runWithRollback {
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkID).get
      val newRoadLink = roadLinkService.getRoadLinkByLinkId(newLinkID).get
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set(newLinkID), false)).thenReturn(Seq(newRoadLink))
      when(mockRoadLinkService.fetchRoadlinksByIds(any[Set[String]])).thenReturn(Seq.empty[RoadLinkFetched])
      when(mockRoadLinkService.getRoadLinksByLinkIds(Set.empty, false)).thenReturn(Seq.empty[RoadLink])

      val id = speedLimitDao.createSpeedLimit("testuser", oldLinkID, Measures(0, oldRoadLink.length), SideCode.BothDirections, SpeedLimitValue(40), Some(1L), None, None, None, LinkGeomSource.NormalLinkInterface)

      val assetsBefore = speedLimitService.getPersistedAssetsByIds(SpeedLimitAsset.typeId, Set(id.get), false)
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestLinearAssetUpdater.updateByRoadLinks(SpeedLimitAsset.typeId, changes)

      val assetsOnOldLink = speedLimitService.getPersistedAssetsByLinkIds(SpeedLimitAsset.typeId, Seq(oldLinkID), false)
      val assetsOnNewLink = speedLimitService.getPersistedAssetsByLinkIds(SpeedLimitAsset.typeId, Seq(newLinkID), false)

      assetsOnOldLink.size should be(0)

      assetsOnNewLink.size should be(1)
      assetsOnNewLink.head.endMeasure should be(newRoadLink.length)
    }
  }

  test("When change type is Replace. After samuutus processing, the externalIds should remain with the asset that is projected onto the new link.") {
    val oldLinkID = "deb91a05-e182-44ae-ad71-4ba169d57e41:1"
    val newLinkID = "0a4cb6e7-67c3-411e-9446-975c53c0d054:1"
    val assetTypeId = SpeedLimitAsset.typeId
    val allChanges = roadLinkChangeClient.convertToRoadLinkChange(source)
    val changes = allChanges.filter(change => change.changeType == RoadLinkChangeType.Replace && change.oldLink.get.linkId == oldLinkID)

    runWithRollback {
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkID).get
      val newRoadLink = roadLinkService.getRoadLinkByLinkId(newLinkID).get
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set(newLinkID), false)).thenReturn(Seq(newRoadLink))
      when(mockRoadLinkService.getRoadLinksByLinkIds(Set.empty, false)).thenReturn(Seq.empty[RoadLink])
      val id = speedLimitDao.createSpeedLimit("testuser", oldLinkID, Measures(0, 242.651), SideCode.BothDirections, SpeedLimitValue(40), Some(1L), None, None, None, LinkGeomSource.NormalLinkInterface, Seq("externalId"))

      val assetsBefore = speedLimitService.getPersistedAssetsByIds(assetTypeId, Set(id.get), false)

      assetsBefore.size should be(1)
      assetsBefore.head.linkId should be(oldLinkID)
      assetsBefore.head.externalIds should be(Seq("externalId"))

      TestLinearAssetUpdater.updateByRoadLinks(assetTypeId, changes)

      val assetsAfter = speedLimitService.getPersistedAssetsByIds(assetTypeId, Set(id.get), false)
      assetsAfter.head.linkId should be(newLinkID)
      assetsAfter.head.externalIds should be(assetsBefore.head.externalIds)
    }
  }

  test("When change type is Split. After samuutus processing, the externalIds should remain with the asset that is projected onto the new link.") {
    val oldLinkID = "cc2bc598-8527-4600-861f-4c70eaacae61:1"
    val newLinkID = "46b888db-6fcd-4e8c-afbd-6532023a1759:1"
    val changes = roadLinkChangeClient.convertToRoadLinkChange(source).filter(change => change.changeType == RoadLinkChangeType.Split && change.oldLink.get.linkId == oldLinkID)

    runWithRollback {
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkID).get
      val newRoadLink = roadLinkService.getRoadLinkByLinkId(newLinkID).get
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set(newLinkID), false)).thenReturn(Seq(newRoadLink))
      when(mockRoadLinkService.fetchRoadlinksByIds(any[Set[String]])).thenReturn(Seq.empty[RoadLinkFetched])
      when(mockRoadLinkService.getRoadLinksByLinkIds(Set.empty, false)).thenReturn(Seq.empty[RoadLink])

      val id = speedLimitDao.createSpeedLimit("testuser", oldLinkID, Measures(0, oldRoadLink.length), SideCode.BothDirections, SpeedLimitValue(40), Some(1L), None, None, None, LinkGeomSource.NormalLinkInterface, Seq("externalId"))

      val assetsBefore = speedLimitService.getPersistedAssetsByIds(SpeedLimitAsset.typeId, Set(id.get), false)
      assetsBefore.head.linkId should be(oldLinkID)
      assetsBefore.head.externalIds should be(Seq("externalId"))

      TestLinearAssetUpdater.updateByRoadLinks(SpeedLimitAsset.typeId, changes)

      val assetsAfter = speedLimitService.getPersistedAssetsByIds(SpeedLimitAsset.typeId, Set(id.get), false)
      assetsAfter.head.linkId should be(newLinkID)
      assetsAfter.head.externalIds should be(assetsBefore.head.externalIds)
    }
  }

  test("When change type is Split and 1 link is split into 2 new links. After samuutus processing, a linear asset that is the full length of the old link is split onto both new links and BOTH new assets inherit BOTH externalIDs.") {
    val oldLinkID = "609d430a-de96-4cb7-987c-3caa9c72c08d:1"
    val newLinkID1 = "4b061477-a7bc-4b72-b5fe-5484e3cec03d:1"
    val newLinkID2 = "b0b54052-7a0e-4714-80e0-9de0ea62a347:1"
    val changes = roadLinkChangeClient.convertToRoadLinkChange(source).filter(change => change.changeType == RoadLinkChangeType.Split && change.oldLink.get.linkId == oldLinkID)
    val testExternalIds: Seq[String] = Seq("testID1", "testID2")

    runWithRollback {
      val newRoadLink1 = roadLinkService.getRoadLinkByLinkId(newLinkID1).get
      val newRoadLink2 = roadLinkService.getRoadLinkByLinkId(newLinkID2).get
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set(newLinkID1, newLinkID2), false)).thenReturn(Seq(newRoadLink1, newRoadLink2))
      when(mockRoadLinkService.fetchRoadlinksByIds(any[Set[String]])).thenReturn(Seq.empty[RoadLinkFetched])
      when(mockRoadLinkService.getRoadLinksByLinkIds(Set.empty, false)).thenReturn(Seq.empty[RoadLink])

      val id = speedLimitDao.createSpeedLimit("testuser", oldLinkID, Measures(0, 45.230), SideCode.BothDirections, SpeedLimitValue(40), Some(1L), None, None, None, LinkGeomSource.NormalLinkInterface, testExternalIds)

      val assetsBefore = speedLimitService.getPersistedAssetsByIds(SpeedLimitAsset.typeId, Set(id.get), false)
      assetsBefore.head.linkId should be(oldLinkID)
      assetsBefore.head.externalIds should be(testExternalIds)

      TestLinearAssetUpdater.updateByRoadLinks(SpeedLimitAsset.typeId, changes)

      val assetsOnNewLinks = speedLimitService.getPersistedAssetsByLinkIds(SpeedLimitAsset.typeId, Seq(newLinkID1, newLinkID2), false)
      assetsOnNewLinks.size should be(2)
      assetsOnNewLinks.head.externalIds should be(testExternalIds)
      assetsOnNewLinks.last.externalIds should be(testExternalIds)
    }
  }
}
