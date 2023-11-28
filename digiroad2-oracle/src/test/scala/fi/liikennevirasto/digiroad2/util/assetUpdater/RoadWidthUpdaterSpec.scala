package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client._
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset.MTKClassWidth.CarRoad_IIIa
import fi.liikennevirasto.digiroad2.linearasset.{DynamicAssetValue, DynamicValue, MTKClassWidth}
import fi.liikennevirasto.digiroad2.service.linearasset.{Measures, RoadWidthService}
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.{BeforeAndAfter, FunSuite, Matchers}
class RoadWidthUpdaterSpec extends FunSuite with BeforeAndAfter with Matchers with UpdaterUtilsSuite  {
  
  val roadWidthService = new RoadWidthService(mockRoadLinkService, mockEventBus)
  object TestRoadWidthUpdater extends RoadWidthUpdater(roadWidthService) {
    override def roadLinkService = mockRoadLinkService
    override def withDynTransaction[T](f: => T): T = f
    override def dao: PostGISLinearAssetDao = linearAssetDao
  }

  object TestRoadWidthUpdaterNoRoadLinkMock extends RoadWidthUpdater(roadWidthService) {
    override def withDynTransaction[T](f: => T): T = f
    override def dao: PostGISLinearAssetDao = linearAssetDao
  }
  before {
    TestRoadWidthUpdaterNoRoadLinkMock.resetReport()
    TestRoadWidthUpdater.resetReport()
  }
  def changeReplaceNewVersionChangeWidth(oldRoadLinkId: String, newRoadLikId: String): RoadLinkChange = {
    val (oldLinkGeometry, oldId) = (generateGeometry(0, 9), oldRoadLinkId)
    val (newLinkGeometry1, newLinkId1) = (generateGeometry(0, 9), newRoadLikId)

    RoadLinkChange(
      changeType = RoadLinkChangeType.Replace,
      oldLink = Some(RoadLinkInfo(linkId = oldId, linkLength = oldLinkGeometry._2,
        geometry = oldLinkGeometry._1, roadClass = MTKClassWidth.CarRoad_IIIa.value,
        adminClass = Municipality,
        municipality = 0,
        trafficDirection = TrafficDirection.BothDirections)),
      newLinks = Seq(
        RoadLinkInfo(
          linkId = newLinkId1,
          linkLength = newLinkGeometry1._2,
          geometry = newLinkGeometry1._1,
          roadClass = MTKClassWidth.CarRoad_IIb.value,
          adminClass = Municipality,
          municipality = 0,
          trafficDirection = TrafficDirection.BothDirections
        )),
      replaceInfo =
        List(
          ReplaceInfo(Option(oldId), Option(newLinkId1),
            oldFromMValue = Option(0.0), oldToMValue = Option(8), newFromMValue = Option(0.0), newToMValue = Option(newLinkGeometry1._2), false))
    )
  }

  test("Create new RoadWith") {

    val changes = roadLinkChangeClient.convertToRoadLinkChange(source).filter(_.changeType== RoadLinkChangeType.Add)
    
    runWithRollback {
      TestRoadWidthUpdaterNoRoadLinkMock.updateByRoadLinks(RoadWidth.typeId, changes)
      val assetsAfter = roadWidthService.getPersistedAssetsByLinkIds(RoadWidth.typeId, Seq("624df3a8-b403-4b42-a032-41d4b59e1840:1"), false)
      assetsAfter.size should be(1)
      val sorted = assetsAfter.sortBy(_.endMeasure)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(2.910)

      assetsAfter.head.value.get.asInstanceOf[DynamicValue].value.properties.nonEmpty should be(true)
      val properties = assetsAfter.head.value.get.asInstanceOf[DynamicValue].value.properties
      properties.filter(_.publicId=="width").head.values.head.value should be("650")
    }
  }
  
  val valueDynamic = DynamicValue(DynamicAssetValue(List(DynamicProperty("suggest_box","checkbox",false,List()), DynamicProperty("width","integer",true,List(DynamicPropertyValue(400))))))
  val valueDynamic2 = DynamicValue(DynamicAssetValue(List(DynamicProperty("suggest_box","checkbox",false,List()), DynamicProperty("width","integer",true,List(DynamicPropertyValue(650))))))
  val valueDynamic3 = DynamicValue(DynamicAssetValue(List(DynamicProperty("suggest_box","checkbox",false,List()), DynamicProperty("width","integer",true,List(DynamicPropertyValue(650))))))

  test("case 1 links under asset is split, smoke test") {
    val linkId = linkId5
    val newLinks = newLinks1_2_4
    val changes = roadLinkChangeClient.convertToRoadLinkChange(source)
    
    runWithRollback {
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(linkId).get
      val oldRoadLinkRaw = roadLinkService.getExpiredRoadLinkByLinkIdNonEncrished(linkId)
      when(mockRoadLinkService.fetchRoadlinkAndComplementary(linkId)).thenReturn(oldRoadLinkRaw)
      when(mockRoadLinkService.fetchRoadlinksByIds(any[Set[String]])).thenReturn(Seq.empty[RoadLinkFetched])

      val id = roadWidthService.createWithoutTransaction(RoadWidth.typeId, linkId,
        valueDynamic, SideCode.BothDirections.value, Measures(0, 56.061), "testuser", 0L, Some(oldRoadLink), true, Some("testCreator"),
        Some(DateTime.parse("2020-01-01")), Some("testModifier"), Some(DateTime.parse("2022-01-01")))
      val assetsBefore = roadWidthService.getPersistedAssetsByIds(RoadWidth.typeId, Set(id), false)
      
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestRoadWidthUpdaterNoRoadLinkMock.updateByRoadLinks(RoadWidth.typeId, changes)
      val assetsAfter = roadWidthService.getPersistedAssetsByLinkIds(RoadWidth.typeId, newLinks, false)
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
      assetsAfter.map(v => extractPropertyValue("width",v.value.get.asInstanceOf[DynamicValue].value.properties).head should be("400"))
    }
  }
  private def extractPropertyValue(key: String, properties: Seq[DynamicProperty]) = {
   properties.filter { property => property.publicId == key }.flatMap { property =>
      property.values.map { value =>
        value.value.toString
      }
    }
  }
  test("case 6 links version and width, move to new version and update width") {
    val linkId = generateRandomKmtkId()
    val linkIdVersion1 = s"$linkId:1"
    val linkIdVersion2 = s"$linkId:2"
    val geometry = generateGeometry(0, 9)
    val oldRoadLink = createRoadLink(linkIdVersion1, generateGeometry(0, 9))
    val newLink = createRoadLink(linkIdVersion2, geometry,roadClass = MTKClassWidth.CarRoad_IIb)
    val change = changeReplaceNewVersionChangeWidth(linkIdVersion1, linkIdVersion2)

    runWithRollback {
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linkIdVersion1, false)).thenReturn(Some(oldRoadLink))
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set(linkIdVersion2))).thenReturn(Seq(newLink))
      val id1 = roadWidthService.createWithoutTransaction(RoadWidth.typeId, linkIdVersion1, valueDynamic, SideCode.BothDirections.value, Measures(0, geometry._2), AutoGeneratedUsername.mtkClassDefault, 0L, Some(oldRoadLink), false, None, None)

      val assetsBefore = roadWidthService.getPersistedAssetsByIds(RoadWidth.typeId, Set(id1), false)
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestRoadWidthUpdater.updateByRoadLinks(RoadWidth.typeId, Seq(change))
      val assetsAfter = roadWidthService.getPersistedAssetsByLinkIds(RoadWidth.typeId, Seq(linkIdVersion2), false)
      assetsAfter.size should be(1)
      assetsAfter.head.linkId should be(linkIdVersion2)
      val sorted = assetsAfter.sortBy(_.startMeasure)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(8)
      assetsAfter.head.value.isEmpty should be(false)
      extractPropertyValue("width",assetsAfter.head.value.get.asInstanceOf[DynamicValue].value.properties).head should be("650")

      val oldIds = Seq(id1)
      val assets = TestRoadWidthUpdater.getReport().map(a => PairAsset(a.before, a.after.headOption,a.changeType))
      assets.size should be(1)
      assets.map(a => {
        a.oldAsset.isDefined should be(true)
        oldIds.contains(a.oldAsset.get.assetId) should be(true)
      })
    }
  }

  def changeReplaceMergeLongerLinkWidthChange(): Seq[RoadLinkChange] = {
    val (oldLinkGeometry1, oldId1) = (generateGeometry(0, 6), linkId1)
    val (oldLinkGeometry2, oldId2) = (generateGeometry(6, 6), linkId6)
    val (newLinkGeometry1, newLinkId1) = (generateGeometry(0, 15), linkId2)
    Seq(RoadLinkChange(
      changeType = RoadLinkChangeType.Replace,
      oldLink = Some(RoadLinkInfo(
        linkId = oldId1,
        linkLength = oldLinkGeometry1._2,
        geometry = oldLinkGeometry1._1,
        roadClass = MTKClassWidth.CarRoad_IIIa.value,
        adminClass = Municipality,
        municipality = 0,
        trafficDirection = TrafficDirection.BothDirections)),
      newLinks = Seq(
        RoadLinkInfo(
          linkId = newLinkId1,
          linkLength = newLinkGeometry1._2,
          geometry = newLinkGeometry1._1,
          roadClass = MTKClassWidth.CarRoad_IIb.value,
          adminClass = Municipality,
          municipality = 0,
          trafficDirection = TrafficDirection.BothDirections
        )),
      replaceInfo =
        List(
          ReplaceInfo(Option(oldId1), Option(newLinkId1),
            oldFromMValue = Option(0.0), oldToMValue = Option(5), newFromMValue = Option(0.0), newToMValue = Option(5), false)
        )
    ),
      RoadLinkChange(
        changeType = RoadLinkChangeType.Replace,
        oldLink = Some(RoadLinkInfo(
          linkId = oldId2,
          linkLength = oldLinkGeometry2._2,
          geometry = oldLinkGeometry2._1,
          roadClass = MTKClassWidth.CarRoad_IIIa.value,
          adminClass = Municipality,
          municipality = 0,
          trafficDirection = TrafficDirection.BothDirections)),
        newLinks = Seq(
          RoadLinkInfo(
            linkId = newLinkId1,
            linkLength = newLinkGeometry1._2,
            geometry = newLinkGeometry1._1,
            roadClass = MTKClassWidth.CarRoad_IIb.value,
            adminClass = Municipality,
            municipality = 0,
            trafficDirection = TrafficDirection.BothDirections
          )),
        replaceInfo =
          List(ReplaceInfo(Option(oldId2), Option(newLinkId1),
            oldFromMValue = Option(0.0), oldToMValue = Option(5), newFromMValue = Option(5.0), newToMValue = Option(newLinkGeometry1._2), false))
      )
    )
  }

  val linkChangeWidth = createRoadLink(linkId2, generateGeometry(0, 15),roadClass = MTKClassWidth.CarRoad_IIb)
  
  test("case 2.1 links under asset is merged, longer one, remove one part of width") {
    val linksid1 = linkId1
    val linksid2 = linkId6
    val linkGeometry1 = generateGeometry(0, 6)
    val linkGeometry2 = generateGeometry(6, 6)

    val oldRoadLink = createRoadLink(linksid1, generateGeometry(0, 6),roadClass = CarRoad_IIIa)
    val oldRoadLink2 = createRoadLink(linksid2, generateGeometry(6, 6),roadClass = CarRoad_IIIa)
    val change = changeReplaceMergeLongerLinkWidthChange()

    runWithRollback {

      val id1 = roadWidthService.createWithoutTransaction(RoadWidth.typeId, linksid1, valueDynamic, SideCode.BothDirections.value, Measures(0, linkGeometry1._2),  AutoGeneratedUsername.mtkClassDefault, 0L, Some(oldRoadLink), false, None, None)
      val id2 = roadWidthService.createWithoutTransaction(RoadWidth.typeId, linksid2, valueDynamic, SideCode.BothDirections.value, Measures(0, linkGeometry2._2),  AutoGeneratedUsername.mtkClassDefault, 0L, Some(oldRoadLink2), false, None, None)
      when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(linksid1, false)).thenReturn(Some(oldRoadLink))
      when(mockRoadLinkService.getExistingOrExpiredRoadLinkByLinkId(linksid2, false)).thenReturn(Some(oldRoadLink2))
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set(linkId2))).thenReturn(Seq(linkChangeWidth))
      val assetsBefore = roadWidthService.getPersistedAssetsByIds(RoadWidth.typeId, Set(id1, id2), false)
      assetsBefore.size should be(2)
      assetsBefore.head.expired should be(false)

      TestRoadWidthUpdater.updateByRoadLinks(RoadWidth.typeId, change)
      val assetsAfter = roadWidthService.getPersistedAssetsByLinkIds(RoadWidth.typeId, Seq(linkId2), false)
      assetsAfter.size should be(1)

      val sorted = assetsAfter.sortBy(_.endMeasure)

      sorted.head.linkId should be(change.head.replaceInfo.head.newLinkId.get)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(14)
      assetsAfter.head.value.isEmpty should be(false)
      extractPropertyValue("width",assetsAfter.head.value.get.asInstanceOf[DynamicValue].value.properties).head should be("650")

      val oldIds = Seq(id1, id2)
      
     val assets = TestRoadWidthUpdater.getReport().map(a => PairAsset(a.before, a.after.headOption,a.changeType))
      assets.size should be(2)
      val (before,emptyBefore) = assets.partition(_.changeType != ChangeTypeReport.Deletion)
      emptyBefore.size should be(1)
      before.map(a => {
        a.oldAsset.isDefined should be(true)
        oldIds.contains(a.oldAsset.get.assetId) should be(true)
      })
    }
  }


  test("case 10.7 links under asset is split, update width for only part of road") {
    val linkId = linkId9
    val newLinks = linkId1011
    val changes = roadLinkChangeClient.convertToRoadLinkChange(source)

    runWithRollback {
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(linkId).get
      val id = roadWidthService.createWithoutTransaction(RoadWidth.typeId, linkId, valueDynamic2, SideCode.AgainstDigitizing.value, Measures(0, 45.230),  AutoGeneratedUsername.mtkClassDefault, 0L, Some(oldRoadLink), false, None, None)
      val id2 = roadWidthService.createWithoutTransaction(RoadWidth.typeId, linkId, valueDynamic2, SideCode.TowardsDigitizing.value, Measures(0, 45.230),  AutoGeneratedUsername.mtkClassDefault, 0L, Some(oldRoadLink), false, None, None)

      val assetsBefore = roadWidthService.getPersistedAssetsByIds(RoadWidth.typeId, Set(id, id2), false)
      assetsBefore.size should be(2)
      assetsBefore.head.expired should be(false)

      TestRoadWidthUpdaterNoRoadLinkMock.updateByRoadLinks(RoadWidth.typeId, changes)
      val assetsAfter = roadWidthService.getPersistedAssetsByLinkIds(RoadWidth.typeId, Seq(linkId10), false) //650
      val assetsAfter2 = roadWidthService.getPersistedAssetsByLinkIds(RoadWidth.typeId, Seq(linkId11), false) //400
      assetsAfter.size should be(2)
      assetsAfter2.size should be(1)
      extractPropertyValue("width",assetsAfter2.head.value.get.asInstanceOf[DynamicValue].value.properties).head should be("400")
      extractPropertyValue("width",assetsAfter.head.value.get.asInstanceOf[DynamicValue].value.properties).head should be("650")
      
      val oldIds = Seq(id, id2)
      val assets = TestRoadWidthUpdaterNoRoadLinkMock.getReport().filter(p=>newLinks.contains(p.after.head.linearReference.get.linkId)).map(a => PairAsset(a.before, a.after.headOption,a.changeType))
      assets.size should be(3)
      assets.map(a => {
        a.oldAsset.isDefined should be(true)
        oldIds.contains(a.oldAsset.get.assetId) should be(true)
      })

    }
  }

  test("case 6 links version and width, move to new version, human user") {
    val linkId = generateRandomKmtkId()
    val linkIdVersion1 = s"$linkId:1"
    val linkIdVersion2 = s"$linkId:2"
    val geometry = generateGeometry(0, 9)
    val oldRoadLink = createRoadLink(linkIdVersion1, generateGeometry(0, 9))
    val newLink = createRoadLink(linkIdVersion2, geometry, roadClass = MTKClassWidth.CarRoad_IIb)
    val change = changeReplaceNewVersionChangeWidth(linkIdVersion1, linkIdVersion2)

    runWithRollback {
      when(mockRoadLinkService.getRoadLinkAndComplementaryByLinkId(linkIdVersion1, false)).thenReturn(Some(oldRoadLink))
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set(linkIdVersion2))).thenReturn(Seq(newLink))
      val id1 = roadWidthService.createWithoutTransaction(RoadWidth.typeId, linkIdVersion1, valueDynamic, SideCode.BothDirections.value, Measures(0, geometry._2), "testuser", 0L, Some(oldRoadLink), false, None, None)

      val assetsBefore = roadWidthService.getPersistedAssetsByIds(RoadWidth.typeId, Set(id1), false)
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestRoadWidthUpdater.updateByRoadLinks(RoadWidth.typeId, Seq(change))
      val assetsAfter = roadWidthService.getPersistedAssetsByLinkIds(RoadWidth.typeId, Seq(linkIdVersion2), false)
      assetsAfter.size should be(1)
      assetsAfter.head.linkId should be(linkIdVersion2)
      val sorted = assetsAfter.sortBy(_.startMeasure)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(8)
      assetsAfter.head.value.isEmpty should be(false)
      assetsAfter.head.value.get.equals(valueDynamic)
    }
  }

  test("Replace. Given a Road Link that is replaced with a New Link; " +
    "when the New Link has grown outside of Old Link geometry from the beginning; " +
    "then the Road Width Asset on New Link should be New Link's length") {
    val oldLinkID = "deb91a05-e182-44ae-ad71-4ba169d57e41:1"
    val newLinkID = "0a4cb6e7-67c3-411e-9446-975c53c0d054:1"

    val allChanges = roadLinkChangeClient.convertToRoadLinkChange(source)
    val changes = allChanges.filter(change => change.changeType == RoadLinkChangeType.Replace && change.oldLink.get.linkId == oldLinkID)

    runWithRollback {
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkID).get
      val newRoadLink = roadLinkService.getRoadLinkByLinkId(newLinkID).get
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set(newLinkID), false)).thenReturn(Seq(newRoadLink))
      val id = roadWidthService.createWithoutTransaction(RoadWidth.typeId, oldLinkID, valueDynamic, SideCode.BothDirections.value, Measures(0.0, oldRoadLink.length), "testuser", 0L, Some(oldRoadLink), false, None, None)

      val assetsBefore = service.getPersistedAssetsByIds(RoadWidth.typeId, Set(id), false)
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestRoadWidthUpdaterNoRoadLinkMock.updateByRoadLinks(RoadWidth.typeId, changes)
      val assetsAfter = service.getPersistedAssetsByIds(RoadWidth.typeId, Set(id), false)
      assetsAfter.size should be(1)

      val assetLength = (assetsAfter.head.endMeasure - assetsAfter.head.startMeasure)
      assetsAfter.head.linkId should be(newLinkID)
      assetLength should be(newRoadLink.length)
    }
  }

  test("Replace. Given a Road Link that is replaced with a New Link; " +
    "when the New Link has grown outside of Old Link geometry from the end; " +
    "then the Road Width Asset on New Link should be New Link's length") {
    val oldLinkID = "18ce7a01-0ddc-47a2-9df1-c8e1be193516:1"
    val newLinkID = "016200a1-5dd4-47cc-8f4f-38ab4934eef9:1"

    val allChanges = roadLinkChangeClient.convertToRoadLinkChange(source)
    val changes = allChanges.filter(change => change.changeType == RoadLinkChangeType.Replace && change.oldLink.get.linkId == oldLinkID)

    runWithRollback {
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(oldLinkID).get
      val newRoadLink = roadLinkService.getRoadLinkByLinkId(newLinkID).get
      when(mockRoadLinkService.getExistingAndExpiredRoadLinksByLinkIds(Set(newLinkID), false)).thenReturn(Seq(newRoadLink))
      when(mockRoadLinkService.fetchRoadlinksByIds(any[Set[String]])).thenReturn(Seq.empty[RoadLinkFetched])
      val id = service.createWithoutTransaction(SpeedLimitAsset.typeId, oldLinkID, valueDynamic, SideCode.BothDirections.value, Measures(0.0, oldRoadLink.length), "testuser", 0L, Some(oldRoadLink), false, None, None)

      val assetsBefore = service.getPersistedAssetsByIds(SpeedLimitAsset.typeId, Set(id), false)
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestRoadWidthUpdaterNoRoadLinkMock.updateByRoadLinks(SpeedLimitAsset.typeId, changes)
      val assetsAfter = service.getPersistedAssetsByIds(SpeedLimitAsset.typeId, Set(id), false)
      assetsAfter.size should be(1)

      val assetLength = (assetsAfter.head.endMeasure - assetsAfter.head.startMeasure)
      assetsAfter.head.linkId should be(newLinkID)
      assetLength should be(newRoadLink.length)
    }
  }

  test("New Roadlink is referenced in Split and Replace change, correct RoadWidth assets should cover correct parts of new links") {
    val oldSplitLinkId = "dbeea36b-16b4-4ddb-b7b7-3ea4fa4b3667:1"
    val newSplitLinkId1 = "4a9f1948-8bae-4cc9-9f11-218079aac595:1"
    val newSplitLinkId2 = "254ed5a2-bc16-440a-88f1-23868011975b:1"

    val oldReplacedLinkId = "45887aef-cb0f-4542-b738-cf44e76709ac:1"
    val newReplacedLinkId = "4a9f1948-8bae-4cc9-9f11-218079aac595:1"

    val changes = roadLinkChangeClient.convertToRoadLinkChange(source)

    runWithRollback {
      val oldRoadLinkSplit = roadLinkService.getExpiredRoadLinkByLinkId(oldSplitLinkId).get
      val oldRoadLinkReplaced = roadLinkService.getExpiredRoadLinkByLinkId(oldReplacedLinkId).get
      val id1 = roadWidthService.createWithoutTransaction(RoadWidth.typeId, oldSplitLinkId, valueDynamic,
        SideCode.BothDirections.value, Measures(0.0, oldRoadLinkSplit.length), "testuser",
        0L, Some(oldRoadLinkSplit), false, None, None) // Width 400 on old Split link
      val id2 = roadWidthService.createWithoutTransaction(RoadWidth.typeId, oldReplacedLinkId, valueDynamic2,
        SideCode.BothDirections.value, Measures(0.0, oldRoadLinkReplaced.length), "testuser",
        0L, Some(oldRoadLinkReplaced), false, None, None) // Width 650 on old Replaced link


      val assetsBefore = roadWidthService.getPersistedAssetsByLinkIds(RoadWidth.typeId, Seq(oldSplitLinkId, oldReplacedLinkId), false)
      assetsBefore.size should be(2)
      assetsBefore.head.expired should be(false)

      TestRoadWidthUpdaterNoRoadLinkMock.updateByRoadLinks(RoadWidth.typeId, changes)
      val assetsAfter = roadWidthService.getPersistedAssetsByLinkIds(RoadWidth.typeId, Seq(newSplitLinkId1, newSplitLinkId2, newReplacedLinkId), false)
      assetsAfter.size should equal(3)
      val assetsOnSharedNewLink = assetsAfter.filter(_.linkId == newReplacedLinkId)
      assetsOnSharedNewLink.size should equal(2)
      assetsOnSharedNewLink.exists(asset => {
        val props = asset.value.get.asInstanceOf[DynamicValue].value.properties
        val widthValue = props.filter(_.publicId == "width").head.values.head.value
        asset.startMeasure == 0.0 && asset.endMeasure == 68.417 && widthValue == "400"
      }) should equal (true)
      assetsOnSharedNewLink.exists(asset => {
        val props = asset.value.get.asInstanceOf[DynamicValue].value.properties
        val widthValue = props.filter(_.publicId == "width").head.values.head.value
        asset.startMeasure == 68.417 && asset.endMeasure == 132.982 && widthValue == "650"
      }) should equal (true)


      val assetOnSplitLink = assetsAfter.find(_.linkId == newSplitLinkId2).get
      assetOnSplitLink.startMeasure should equal(0.0)
      assetOnSplitLink.endMeasure should equal(89.351)
      val assetOnSplitLinkProperties = assetOnSplitLink.value.get.asInstanceOf[DynamicValue].value.properties
      assetOnSplitLinkProperties.filter(_.publicId=="width").head.values.head.value should be("400")
    }

  }
}
