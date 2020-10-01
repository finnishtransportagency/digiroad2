package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.asset.{DynamicProperty, _}
import fi.liikennevirasto.digiroad2.client.vvh.{FeatureClass, VVHClient, VVHRoadLinkClient, VVHRoadlink}
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.{MunicipalityDao, OracleAssetDao}
import fi.liikennevirasto.digiroad2.linearasset.{DynamicAssetValue, DynamicValues, NewLinearAsset, RoadLink}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.{PolygonTools, TestTransactions}
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, Point}
import org.mockito.ArgumentCaptor
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

  val mockLinearAssetDao: OracleLinearAssetDao = MockitoSugar.mock[OracleLinearAssetDao]
  val mockEventBus: DigiroadEventBus = MockitoSugar.mock[DigiroadEventBus]
  val linearAssetDao = new OracleLinearAssetDao(mockVVHClient, mockRoadLinkService)
  val mockMunicipalityDao: MunicipalityDao = MockitoSugar.mock[MunicipalityDao]
  val mockAssetDao: OracleAssetDao = MockitoSugar.mock[OracleAssetDao]

  def createValue(PT_regulatory_number: String="K1", PT_lane_number: String="",
                  PT_lane_type: String="", PT_lane_location: String="00",
                  PT_material: String="", PT_length: String="",
                  PT_width: String="", PT_profile_mark: String="",
                  PT_additional_information: String="", PT_state: String="",
                  PT_end_date: String="", PT_start_date: String="",
                  PT_milled: String="", PT_condition: String="", PT_rumble_strip: String=""): DynamicAssetValue = {
    DynamicAssetValue(Seq(
      DynamicProperty("PT_regulatory_number", "single_choice", required = true, Seq(DynamicPropertyValue(PT_regulatory_number))),
      DynamicProperty("PT_lane_number", "number", required = false, Seq(DynamicPropertyValue(PT_lane_number))),
      DynamicProperty("PT_lane_type", "string", required = false, Seq(DynamicPropertyValue(PT_lane_type))),
      DynamicProperty("PT_lane_location", "number", required = true, Seq(DynamicPropertyValue(PT_lane_location))),
      DynamicProperty("PT_material", "single_choice", required = false, Seq(DynamicPropertyValue(PT_material))),
      DynamicProperty("PT_length", "number", required = false, Seq(DynamicPropertyValue(PT_length))),
      DynamicProperty("PT_width", "number", required = false, Seq(DynamicPropertyValue(PT_width))),
      DynamicProperty("PT_profile_mark", "single_choice", required = false, Seq(DynamicPropertyValue(PT_profile_mark))),
      DynamicProperty("PT_additional_information", "string", required = false, Seq(DynamicPropertyValue(PT_additional_information))),
      DynamicProperty("PT_state", "single_choice", required = false, Seq(DynamicPropertyValue(PT_state))),
      DynamicProperty("PT_end_date", "date", required = false, Seq(DynamicPropertyValue(PT_end_date))),
      DynamicProperty("PT_start_date", "date", required = false, Seq(DynamicPropertyValue(PT_start_date))),
      DynamicProperty("PT_milled", "single_choice", required = false, Seq(DynamicPropertyValue(PT_milled))),
      DynamicProperty("PT_condition", "single_choice", required = false, Seq(DynamicPropertyValue(PT_condition))),
      DynamicProperty("PT_rumble_strip", "single_choice", required = false, Seq(DynamicPropertyValue(PT_rumble_strip)))
    ))
  }

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
    val asset = NewLinearAsset(linkId = 388562360, startMeasure = 0, endMeasure = 10, value = DynamicValues(Seq(createValue(),createValue())), sideCode = 1, 0, None)
    val newLinearAssets = Seq(asset)
    val service = new LengthOfRoadAxisService(eventBusImpl = mockEventBus, roadLinkServiceImpl = mockRoadLinkService)

    val id=  OracleDatabase.withDynSession{
      newLinearAssets.map { newAsset =>
        val roadLink = mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(newLinearAssets.map(_.linkId).toSet, false)
        service.createWithoutTransaction(typeId = 460,
          newAsset.linkId, newAsset.value, newAsset.sideCode, Measures(newAsset.startMeasure, newAsset.endMeasure), username = "test",
          mockVVHClient.roadLinkData.createVVHTimeStamp(), roadLink.find(_.linkId == newAsset.linkId)
        )
      }
    }

    id should not be (null)
  }

  test("update new LengthOfRoadAxis asset") {
    val roadLink1 = RoadLink(388562360, Seq(Point(0.0, 10.0), Point(10, 10.0)), 5, Municipality, 1,
      TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
    val roadLink2 = RoadLink(388562360, Seq(Point(10.0, 10.0), Point(10, 5.0)), 10.0, Municipality, 1,
      TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
    val roadLink3 = RoadLink(388562360, Seq(Point(10.0, 0.0), Point(10.0, 5.0)), 5.0, Municipality, 1,
      TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
    val roadLinkSequence: Seq[RoadLink] = Seq(roadLink1, roadLink2, roadLink3)

    when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(388562360), false))
      .thenReturn(roadLinkSequence)

    val roadLinkVvh = Some(VVHRoadlink(388562360L, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers))
    //this mocking seems to be not wokrking
    when(mockVVHClient.fetchRoadLinkByLinkId(1611690L))
      .thenReturn(roadLinkVvh)
    val ids = Seq(0L, 1L, 1L)
    val values =  DynamicValues(Seq(createValue(),createValue()))
    val username = "testuser"
    val measure = Option(Measures(10, 20))
    val service = new LengthOfRoadAxisService(eventBusImpl = mockEventBus, roadLinkServiceImpl = mockRoadLinkService)

    OracleDatabase.withDynSession{
      service.updateWithoutTransaction(ids, values,  "testuser", sideCode = Option(1), measures = measure)
    }

  }
}

