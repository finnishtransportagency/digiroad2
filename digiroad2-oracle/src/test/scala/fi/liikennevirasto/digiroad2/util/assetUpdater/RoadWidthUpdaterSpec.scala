package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client._
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset.MTKClassWidth.CarRoad_IIIa
import fi.liikennevirasto.digiroad2.linearasset.{DynamicAssetValue, DynamicValue, MTKClassWidth}
import fi.liikennevirasto.digiroad2.service.linearasset.{Measures, RoadWidthService}
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
        valueDynamic, SideCode.BothDirections.value, Measures(0, 56.061), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val assetsBefore = roadWidthService.getPersistedAssetsByIds(RoadWidth.typeId, Set(id), false)
      
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestRoadWidthUpdaterNoRoadLinkMock.updateByRoadLinks(RoadWidth.typeId, changes)
      val assetsAfter = roadWidthService.getPersistedAssetsByLinkIds(RoadWidth.typeId, newLinks, false)
      assetsAfter.size should be(3)
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
}
