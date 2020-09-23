package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.{FeatureClass, VVHClient, VVHRoadLinkClient, VVHRoadlink}
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.{MunicipalityDao, OracleAssetDao}
import fi.liikennevirasto.digiroad2.linearasset.{DynamicAssetValue, DynamicValue, NewLinearAsset, NumericValue, RoadLink, Value, LengthOfRoadAxisCreate, LengthOfRoadAxisUpdate}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.{PolygonTools, TestTransactions}
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, Point}
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class LengthOfRoadAxisSpecSupport extends FunSuite with Matchers {
  val mockRoadLinkService: RoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockLengthOfRoadAxisService: LengthOfRoadAxisService = MockitoSugar.mock[LengthOfRoadAxisService]
  val mockVVHClient: VVHClient = MockitoSugar.mock[VVHClient]
  val mockVVHRoadLinkClient: VVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
  val mockPolygonTools: PolygonTools = MockitoSugar.mock[PolygonTools]

  when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
  when(mockVVHRoadLinkClient.fetchByLinkId(388562360L))
    .thenReturn(Some(VVHRoadlink(388562360L, 235, Seq(Point(0, 0), Point(10, 0)),
      Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockVVHRoadLinkClient.fetchByLinkIds(any[Set[Long]]))
    .thenReturn(Seq(VVHRoadlink(388562360L, 235, Seq(Point(0, 0), Point(10, 0)),
      Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockVVHClient.fetchRoadLinkByLinkId(any[Long]))
    .thenReturn(Some(VVHRoadlink(388562360L, 235, Seq(Point(0, 0), Point(10, 0)),
      Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))


  val mockLinearAssetDao: OracleLinearAssetDao = MockitoSugar.mock[OracleLinearAssetDao]
  val mockEventBus: DigiroadEventBus = MockitoSugar.mock[DigiroadEventBus]
  //val linearAssetDao = new OracleLinearAssetDao(mockVVHClient, mockLengthOfRoadAxisService)
  val linearAssetDao = new OracleLinearAssetDao(mockVVHClient, mockRoadLinkService)
  val mockMunicipalityDao: MunicipalityDao = MockitoSugar.mock[MunicipalityDao]
  val mockAssetDao: OracleAssetDao = MockitoSugar.mock[OracleAssetDao]

  object PassThroughService extends LinearAssetOperations {
    override def withDynTransaction[T](f: => T): T = f
    def lengthOfRoadAxisService: LengthOfRoadAxisService = mockLengthOfRoadAxisService
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: OracleLinearAssetDao = mockLinearAssetDao
    override def eventBus: DigiroadEventBus = mockEventBus
    override def vvhClient: VVHClient = mockVVHClient
    override def polygonTools: PolygonTools = mockPolygonTools
    override def municipalityDao: MunicipalityDao = mockMunicipalityDao
    override def assetDao: OracleAssetDao = mockAssetDao

    override def getUncheckedLinearAssets(areas: Option[Set[Int]]) =
      throw new UnsupportedOperationException("Not supported method")
    override def getInaccurateRecords(typeId: Int, municipalities: Set[Int] =
    Set(), adminClass: Set[AdministrativeClass]
                                      = Set()) = throw new UnsupportedOperationException("Not supported method")
  }

  def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback(PassThroughService.dataSource)(test)

}

class LengthOfRoadAxisServiceSpec extends LengthOfRoadAxisSpecSupport {

  // create new asset
  test("create new LengthOfRoadAxis asset") {

    val roadLink1 = RoadLink(388562360, Seq(Point(0.0, 10.0), Point(10, 10.0)), 5, Municipality, 1,
      TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
    val roadLink2 = RoadLink(388562360, Seq(Point(10.0, 10.0), Point(10, 5.0)), 10.0, Municipality, 1,
      TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
    val roadLink3 = RoadLink(388562360, Seq(Point(10.0, 0.0), Point(10.0, 5.0)), 5.0, Municipality, 1,
      TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
    val roadLinkSequence: Seq[RoadLink] = Seq(roadLink1, roadLink2, roadLink3)

    when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(388562360), false))
      .thenReturn(roadLinkSequence)

    val addItem2 = NewLinearAsset(linkId = 388562360, startMeasure = 0, endMeasure = 10, value = NumericValue(1), sideCode = 1, 0, None)
    val listOfElement: List[LengthOfRoadAxisCreate] = List(
      LengthOfRoadAxisCreate(440, Seq(addItem2)),
      LengthOfRoadAxisCreate(440, Seq(addItem2)),
      LengthOfRoadAxisCreate(440, Seq(addItem2))
    )
    val service = new LengthOfRoadAxisService(eventBusImpl = mockEventBus, roadLinkServiceImpl = mockRoadLinkService)
    val result2 = service.createRoadwayLinear(
      listOfElement, "testuser",
      vvhTimeStamp = mockVVHClient.roadLinkData.createVVHTimeStamp())
    assert(result2.nonEmpty)
    result2.foreach(item => {
      println(item)
    })
  }


  // get new asset
  test("get new LengthOfRoadAxis asset") {

  }

  // update new asset

  test("update new LengthOfRoadAxis asset") {
    val roadLink1 = RoadLink(388562360, Seq(Point(0.0, 10.0), Point(10, 10.0)), 5, Municipality, 1,
      TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
    val roadLink2 = RoadLink(388562360, Seq(Point(10.0, 10.0), Point(10, 5.0)), 10.0, Municipality, 1,
      TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
    val roadLink3 = RoadLink(388562360, Seq(Point(10.0, 0.0), Point(10.0, 5.0)), 5.0, Municipality, 1,
      TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
    val roadLinkSequence: Seq[RoadLink] = Seq(roadLink1, roadLink2, roadLink3)

    val roadLinkVvh = Some(VVHRoadlink(2l, 235, Nil, Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers))

    //when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(388562360), false))
      //.thenReturn(roadLinkSequence)

    when(mockVVHClient.fetchRoadLinkByLinkId(1611690L))
      .thenReturn(roadLinkVvh)


    val valueTest = DynamicProperty(publicId = "test", propertyType = PropertyTypes.Number, required = false,
      values = Seq(DynamicPropertyValue(0)))
    val valuesAssetTest= DynamicAssetValue(Seq(valueTest))
    val listOfElement: List[LengthOfRoadAxisUpdate] = List(
      LengthOfRoadAxisUpdate(Seq(200293),DynamicValue(valuesAssetTest)),
      LengthOfRoadAxisUpdate(Seq(200293L),DynamicValue(valuesAssetTest)) ,
      LengthOfRoadAxisUpdate(Seq(200293L), DynamicValue(valuesAssetTest))
    )
    val service = new LengthOfRoadAxisService(eventBusImpl = mockEventBus, roadLinkServiceImpl = mockRoadLinkService)
    val result2 = service.updateRoadwayLinear(
      listOfElement, "testuser"
    )
    assert(result2.nonEmpty)
    result2.foreach(item => {
      println(item)
    })


  }

  // delete new asset

  test("delete new LengthOfRoadAxis asset") {

  }

}


