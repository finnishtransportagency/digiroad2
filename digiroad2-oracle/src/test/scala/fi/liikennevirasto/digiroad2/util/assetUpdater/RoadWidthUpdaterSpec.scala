
package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.asset.{Municipality, RoadWidth, TrafficDirection, TrafficVolume}
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, DummySerializer, GeometryUtils, Point}
import fi.liikennevirasto.digiroad2.client.{ReplaceInfo, RoadLinkChange, RoadLinkChangeClient, RoadLinkChangeType, RoadLinkClient, RoadLinkInfo}
import fi.liikennevirasto.digiroad2.dao.DynamicLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset.{DynamicValue, MTKClassWidth, NumericValue}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.{LinearAssetService, RoadWidthService}
import fi.liikennevirasto.digiroad2.util.{Digiroad2Properties, LinkIdGenerator, TestTransactions}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}


class RoadWidthUpdaterSpec extends FunSuite with Matchers {

  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  val mockRoadLinkClient = MockitoSugar.mock[RoadLinkClient]
  val linearAssetDao = new PostGISLinearAssetDao()
  val service = new RoadWidthService(mockRoadLinkService, mockEventBus)

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

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

}


