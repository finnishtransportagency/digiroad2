package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.DigiroadEventBus
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.{VVHClient, VVHRoadLinkClient}
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset.{DynamicAssetValue, DynamicValue}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.PolygonTools
import org.scalatest.{FunSuite, Matchers}
import org.scalatest.mockito.MockitoSugar

class DynamicValueAssetServiceSpec extends FunSuite with Matchers {
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockVVHClient = MockitoSugar.mock[VVHClient]
  val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
  val mockPolygonTools = MockitoSugar.mock[PolygonTools]

  val mockLinearAssetDao = MockitoSugar.mock[OracleLinearAssetDao]
  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  val linearAssetDao = new OracleLinearAssetDao(mockVVHClient, mockRoadLinkService)

  object ServiceWithDao extends TextValueLinearAssetService(mockRoadLinkService, mockEventBus) {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: OracleLinearAssetDao = linearAssetDao
    override def eventBus: DigiroadEventBus = mockEventBus
    override def vvhClient: VVHClient = mockVVHClient
    override def polygonTools: PolygonTools = mockPolygonTools
  }

  test("equals returns false when objects are not matching"){
    val asset1 = DynamicAssetValue(Seq(DynamicProperty("name", PropertyTypes.Text, required = false, Seq(DynamicPropertyValue("Asset1Name")))))
    val asset2 = DynamicAssetValue(Seq(DynamicProperty("name", PropertyTypes.Text, required = false, Seq(DynamicPropertyValue("Asset2Name")))))
    val asset3 = DynamicAssetValue(Seq(DynamicProperty("name", PropertyTypes.Text, required = false, Seq(DynamicPropertyValue("BigName"), DynamicPropertyValue("ExtraName")))))
    val asset4 = DynamicAssetValue(Seq(DynamicProperty("name", PropertyTypes.Text, required = false,Seq(DynamicPropertyValue("BigName")))))
    val asset5 = DynamicAssetValue(Seq(DynamicProperty("name", PropertyTypes.ReadOnlyNumber, required = false, Seq(DynamicPropertyValue("BiggerName")))))
    val asset6 = DynamicAssetValue(Seq(DynamicProperty("name", PropertyTypes.Text, required = false, Seq(DynamicPropertyValue("BiggerName")))))
    asset1.equals(asset2) should be (false)
    asset3.equals(asset4) should be (false)
    asset5.equals(asset6) should be (false)
  }

  test("equals returns true when objects are matching"){
    val ratings = DynamicProperty("ratings", PropertyTypes.ReadOnlyNumber, required = false, Seq(DynamicPropertyValue(1), DynamicPropertyValue(10)))
    val choice1 = DynamicProperty("choices", PropertyTypes.Text, required = false,Seq(DynamicPropertyValue("Strawberry"), DynamicPropertyValue("Chocolate"), DynamicPropertyValue("Vanilla")))
    val choice2 = DynamicProperty("choices", PropertyTypes.Text, required = false,Seq(DynamicPropertyValue("Vanilla"), DynamicPropertyValue("Strawberry"), DynamicPropertyValue("Chocolate")))
    val asset1 = DynamicValue(DynamicAssetValue(Seq(choice1, ratings)))
    val asset2 = DynamicValue(DynamicAssetValue(Seq(ratings, choice2)))
    asset1.equals(asset2) should be (true)
  }
}