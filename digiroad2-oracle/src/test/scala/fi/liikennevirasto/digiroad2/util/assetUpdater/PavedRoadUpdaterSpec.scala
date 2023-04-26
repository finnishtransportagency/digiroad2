
package fi.liikennevirasto.digiroad2.util.assetUpdater

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.{RoadLinkChangeClient, RoadLinkChangeType, RoadLinkClient}
import fi.liikennevirasto.digiroad2.client.vvh.ChangeType.{CombinedRemovedPart, Removed}
import fi.liikennevirasto.digiroad2.client.vvh.ChangeInfo
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.{DynamicLinearAssetDao, MunicipalityDao, PostGISAssetDao}
import fi.liikennevirasto.digiroad2.linearasset.{DynamicAssetValue, DynamicValue, NumericValue, RoadLink}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.{DynamicLinearAssetService, LinearAssetService, Measures}
import fi.liikennevirasto.digiroad2.service.pointasset.PavedRoadService
import fi.liikennevirasto.digiroad2.util.{Digiroad2Properties, LinkIdGenerator, PolygonTools, TestTransactions}
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, DummySerializer, Point}
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class PavedRoadUpdaterSpec extends FunSuite with Matchers{
  val mockRoadLinkService: RoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockEventBus: DigiroadEventBus = MockitoSugar.mock[DigiroadEventBus]
  val mockRoadLinkClient: RoadLinkClient = MockitoSugar.mock[RoadLinkClient]
  val linearAssetDao = new PostGISLinearAssetDao()
  val mockMunicipalityDao = MockitoSugar.mock[MunicipalityDao]

  val mockPolygonTools = MockitoSugar.mock[PolygonTools]
  val service = new DynamicLinearAssetService(mockRoadLinkService, mockEventBus)
  lazy val roadLinkClient: RoadLinkClient = {
    new RoadLinkClient(Digiroad2Properties.vvhRestApiEndPoint)
  }
  
  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  object Service extends PavedRoadService(mockRoadLinkService, mockEventBus) {
    override def polygonTools: PolygonTools = mockPolygonTools
    override def municipalityDao: MunicipalityDao = mockMunicipalityDao
    override def assetDao: PostGISAssetDao = new PostGISAssetDao
    override def dynamicLinearAssetDao: DynamicLinearAssetDao = new DynamicLinearAssetDao
    override def getInaccurateRecords(typeId: Int, municipalities: Set[Int] = Set(), adminClass: Set[AdministrativeClass] = Set()) = throw new UnsupportedOperationException("Not supported method")
  }

  object TestPavedRoadUpdater extends PavedRoadUpdater(Service) {
    override def withDynTransaction[T](f: => T): T = f
    override def dao: PostGISLinearAssetDao = linearAssetDao
  }

  val roadLinkChangeClient = new RoadLinkChangeClient
  
  lazy val source = scala.io.Source.fromFile("digiroad2-oracle/src/test/resources/smallChangeSet.json").mkString

  test("Create new paved") {

    val changes = roadLinkChangeClient.convertToRoadLinkChange(source).filter(_.changeType == RoadLinkChangeType.Add)

    runWithRollback {
      TestPavedRoadUpdater.updateByRoadLinks(PavedRoad.typeId, changes)
      val assetsAfter = service.getPersistedAssetsByLinkIds(PavedRoad.typeId, Seq("624df3a8-b403-4b42-a032-41d4b59e1840:1"), false)
      assetsAfter.size should be(1)
      val sorted = assetsAfter.sortBy(_.endMeasure)
      sorted.head.startMeasure should be(0)
      sorted.head.endMeasure should be(2.910)

      assetsAfter.head.value.get.asInstanceOf[DynamicValue].value.properties.nonEmpty should be(true)
      val properties = assetsAfter.head.value.get.asInstanceOf[DynamicValue].value.properties
      properties.head.values.head.value should be("99")
      
    }
  }
  
}

