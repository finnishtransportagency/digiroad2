package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.asset.{DynamicProperty, DynamicPropertyValue, RoadWidth, SideCode}
import fi.liikennevirasto.digiroad2.client.{RoadLinkChangeClient, RoadLinkChangeType, RoadLinkClient, RoadLinkFetched}
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset.{DynamicAssetValue, DynamicValue}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.{Measures, RoadWidthService}
import fi.liikennevirasto.digiroad2.util.{Digiroad2Properties, TestTransactions}
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, DummySerializer}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}


class RoadWidthUpdaterSpec extends FunSuite with Matchers {

  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
  val linearAssetDao = new PostGISLinearAssetDao()
  val service = new RoadWidthService(mockRoadLinkService, mockEventBus)

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)
  lazy val roadLinkClient: RoadLinkClient = {
    new RoadLinkClient(Digiroad2Properties.vvhRestApiEndPoint)
  }
  lazy val roadLinkService: RoadLinkService = {
    new RoadLinkService(roadLinkClient, mockEventBus, new DummySerializer)
  }

  object TestRoadWidthUpdater extends RoadWidthUpdater(service) {
    override def withDynTransaction[T](f: => T): T = f
    override def dao: PostGISLinearAssetDao = linearAssetDao
  }

  val roadLinkChangeClient = new RoadLinkChangeClient

  lazy val source = scala.io.Source.fromFile("digiroad2-oracle/src/test/resources/smallChangeSet.json").mkString

  test("Create new RoadWith") {

    val changes = roadLinkChangeClient.convertToRoadLinkChange(source).filter(_.changeType== RoadLinkChangeType.Add)
    
    runWithRollback {
      TestRoadWidthUpdater.updateByRoadLinks(RoadWidth.typeId, changes)
      val assetsAfter = service.getPersistedAssetsByLinkIds(RoadWidth.typeId, Seq("624df3a8-b403-4b42-a032-41d4b59e1840:1"), false)
      assetsAfter.size should be(1)
      val sorted = assetsAfter.sortBy(_.endMeasure)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(2.910)

      assetsAfter.head.value.get.asInstanceOf[DynamicValue].value.properties.nonEmpty should be(true)
      val properties = assetsAfter.head.value.get.asInstanceOf[DynamicValue].value.properties
      properties.filter(_.publicId=="width").head.values.head.value should be("650")
    }
  }
  
  val valueDynamic = DynamicValue(DynamicAssetValue(List(DynamicProperty("suggest_box","checkbox",false,List()), DynamicProperty("width","integer",true,List(DynamicPropertyValue(20))))))

  test("case 1 links under asset is split, smoke test") {
    val linksid = "f8fcc994-6e3e-41b5-bb0f-ae6089fe6acc:1"
    val newLinks = Seq("753279ca-5a4d-4713-8609-0bd35d6a30fa:1", "c83d66e9-89fe-4b19-8f5b-f9f2121e3db7:1", "c3beb1ca-05b4-44d6-8d69-2a0e09f22580:1")
    val changes = roadLinkChangeClient.convertToRoadLinkChange(source)


    runWithRollback {
      val oldRoadLink = roadLinkService.getExpiredRoadLinkByLinkId(linksid).get
      val oldRoadLinkRaw = roadLinkService.getExpiredRoadLinkByLinkIdNonEncrished(linksid)
      when(mockRoadLinkService.fetchRoadlinkAndComplementary(linksid)).thenReturn(oldRoadLinkRaw)
      when(mockRoadLinkService.fetchRoadlinksByIds(any[Set[String]])).thenReturn(Seq.empty[RoadLinkFetched])

      val id = service.createWithoutTransaction(RoadWidth.typeId, linksid,
        valueDynamic, SideCode.BothDirections.value, Measures(0, 56.061), "testuser", 0L, Some(oldRoadLink), false, None, None)
      val assetsBefore = service.getPersistedAssetsByIds(RoadWidth.typeId, Set(id), false)
      
      assetsBefore.size should be(1)
      assetsBefore.head.expired should be(false)

      TestRoadWidthUpdater.updateByRoadLinks(RoadWidth.typeId, changes)
      val assetsAfter = service.getPersistedAssetsByLinkIds(RoadWidth.typeId, newLinks, false)
      assetsAfter.size should be(3)
      val sorted = assetsAfter.sortBy(_.endMeasure)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(9.334)

      sorted(1).startMeasure should be(0)
      sorted(1).endMeasure should be(11.841)

      sorted(2).startMeasure should be(0)
      sorted(2).endMeasure should be(34.906)

      assetsAfter.map(v => v.value.isEmpty should be(false))
      assetsAfter.map(v => v.value.get.equals(valueDynamic))
    }
  }

}


