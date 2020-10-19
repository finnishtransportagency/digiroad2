package fi.liikennevirasto.digiroad2.service.linearasset

import java.text.SimpleDateFormat
import java.util.Date

import fi.liikennevirasto.digiroad2.asset.{DynamicProperty, _}
import fi.liikennevirasto.digiroad2.client.vvh.{FeatureClass, VVHClient, VVHRoadLinkClient, VVHRoadlink}
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.{DynamicLinearAssetDao, MunicipalityDao, OracleAssetDao}
import fi.liikennevirasto.digiroad2.linearasset.{DynamicAssetValue, DynamicValue, NewLinearAsset, RoadLink}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.{PolygonTools, TestTransactions}
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, Point}
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
  val mockDynamicLinearAssetDao: DynamicLinearAssetDao = new DynamicLinearAssetDao

 val formatter = new SimpleDateFormat("dd.MM.yyyy")

  def createValue(PT_regulatory_number: String = "1", PT_lane_number: String = "1",
                  PT_lane_type: String = "test", PT_lane_location: String = "00",
                  PT_material: String = "1", PT_length: String = "10",
                  PT_width: String = "10", PT_profile_mark: String = "1",
                  PT_additional_information: String = "test", PT_state: String = "1",
                  PT_end_date: String = formatter.format(new Date()), PT_start_date: String =formatter.format(new Date()),
                  PT_milled: String = "1", PT_condition: String = "1", PT_rumble_strip: String = "1"): DynamicAssetValue = {
    DynamicAssetValue(Seq(
      DynamicProperty("PT_regulatory_number", "single_choice", required = true, Seq(DynamicPropertyValue(PT_regulatory_number))),
      DynamicProperty("PT_lane_number", "number", required = false, Seq(DynamicPropertyValue(PT_lane_number))),
      DynamicProperty("PT_lane_type", "text", required = false, Seq(DynamicPropertyValue(PT_lane_type))),
      DynamicProperty("PT_lane_location", "number", required = true, Seq(DynamicPropertyValue(PT_lane_location))),
      DynamicProperty("PT_material", "single_choice", required = false, Seq(DynamicPropertyValue(PT_material))),
      DynamicProperty("PT_length", "number", required = false, Seq(DynamicPropertyValue(PT_length))),
      DynamicProperty("PT_width", "number", required = false, Seq(DynamicPropertyValue(PT_width))),
      DynamicProperty("PT_profile_mark", "single_choice", required = false, Seq(DynamicPropertyValue(PT_profile_mark))),
      DynamicProperty("PT_additional_information", "long_text", required = false, Seq(DynamicPropertyValue(PT_additional_information))),
      DynamicProperty("PT_state", "single_choice", required = false, Seq(DynamicPropertyValue(PT_state))),
      DynamicProperty("PT_end_date", "date", required = false, Seq(DynamicPropertyValue(PT_end_date))),
      DynamicProperty("PT_start_date", "date", required = false, Seq(DynamicPropertyValue(PT_start_date))),
      DynamicProperty("PT_milled", "single_choice", required = false, Seq(DynamicPropertyValue(PT_milled))),
      DynamicProperty("PT_condition", "single_choice", required = false, Seq(DynamicPropertyValue(PT_condition))),
      DynamicProperty("PT_rumble_strip", "single_choice", required = false, Seq(DynamicPropertyValue(PT_rumble_strip)))
    ))
  }

  object PassThroughService extends LengthOfRoadAxisService(mockRoadLinkService, mockEventBus) {
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

  object ServiceWithDao extends LengthOfRoadAxisService(mockRoadLinkService, mockEventBus) {
    override def withDynTransaction[T](f: => T): T = f
    override def roadLinkService: RoadLinkService = mockRoadLinkService
    override def dao: OracleLinearAssetDao = linearAssetDao
    override def eventBus: DigiroadEventBus = mockEventBus
    override def vvhClient: VVHClient = mockVVHClient
    override def polygonTools: PolygonTools = mockPolygonTools
    override def municipalityDao: MunicipalityDao = mockMunicipalityDao
    override def assetDao: OracleAssetDao = mockAssetDao
    override def dynamicLinearAssetDao: DynamicLinearAssetDao = mockDynamicLinearAssetDao

    override def getUncheckedLinearAssets(areas: Option[Set[Int]]) = throw new UnsupportedOperationException("Not supported method")
    override def getInaccurateRecords(typeId: Int, municipalities: Set[Int] = Set(), adminClass: Set[AdministrativeClass] = Set()) = throw new UnsupportedOperationException("Not supported method")
  }
  // create new asset
  test("create new LengthOfRoadAxis asset, one DynamicValue") {

    val roadLink1 = RoadLink(388562360, Seq(Point(0.0, 10.0), Point(10, 10.0)), 5, Municipality, 1,
      TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
    val roadLink2 = RoadLink(388562360, Seq(Point(10.0, 10.0), Point(10, 5.0)), 10.0, Municipality, 1,
      TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
    val roadLink3 = RoadLink(388562360, Seq(Point(10.0, 0.0), Point(10.0, 5.0)), 5.0, Municipality, 1,
      TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
    val roadLinkSequence: Seq[RoadLink] = Seq(roadLink1, roadLink2, roadLink3)
    when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(388562360), false))
      .thenReturn(roadLinkSequence)

    runWithRollback {
      val asset = NewLinearAsset(linkId = 388562360, startMeasure = 0, endMeasure = 10, value = DynamicValue(createValue()), sideCode = 1, 0, None)
      val newLinearAssets = Seq(asset)
      val id = ServiceWithDao.create(newLinearAssets, typeId = 460, username = "testuser", mockVVHClient.createVVHTimeStamp())
      id should not be (null)
    }

  }

  test("create new LengthOfRoadAxis asset, save two item to one roadlink") {

    val roadLink1 = RoadLink(388562360, Seq(Point(0.0, 10.0), Point(10, 10.0)), 5, Municipality, 1,
      TrafficDirection.BothDirections, SingleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
    val roadLinkSequence: Seq[RoadLink] = Seq(roadLink1 )
    when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(388562360), false))
      .thenReturn(roadLinkSequence)

    runWithRollback {
      val asset = NewLinearAsset(linkId = 388562360, startMeasure = 0, endMeasure = 10, value = DynamicValue(createValue()), sideCode = 1, 0, None)
      val newLinearAssets = Seq(asset)
      val id1 = ServiceWithDao.create(newLinearAssets, typeId = 460, username = "testuser", mockVVHClient.createVVHTimeStamp())
      val id2 = ServiceWithDao.create(newLinearAssets, typeId = 460, username = "testuser", mockVVHClient.createVVHTimeStamp())
      id1 should not be (null)
      id2 should not be (null)
    }
  }
  test("create new LengthOfRoadAxis asset, save two item to one roadlink, get two item from one roadlin by using getPersistedAssetsByIds, test with rounabout") {

    val roadLinks = Seq(
      RoadLink(442445, Seq(Point(384970, 6671649), Point(384968, 6671653), Point(384969, 6671660), Point(384975, 6671664)),
        2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(442443, Seq(Point(384985, 6671649), Point(384980, 6671646), Point(384972, 6671647), Point(384970, 6671649)),
        2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(442442, Seq(Point(384985, 6671649), Point(384986, 6671650), Point(384987, 6671657), Point(384986, 6671660)),
        2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(442444, Seq(Point(384986, 6671660), Point(384983, 6671663), Point(384976, 6671665), Point(384975, 6671664)),
        2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None)
    )
    when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(442445,442443,442442,442444), false))
      .thenReturn(roadLinks)

    runWithRollback {
      val newLinearAssets = Seq( NewLinearAsset(linkId = 442445, startMeasure = 0, endMeasure = 10, value = DynamicValue(createValue()), sideCode = 1, 0, None),
        NewLinearAsset(linkId = 442443, startMeasure = 0, endMeasure = 10, value = DynamicValue(createValue()), sideCode = 1, 0, None),
        NewLinearAsset(linkId = 442442, startMeasure = 0, endMeasure = 10, value = DynamicValue(createValue()), sideCode = 1, 0, None),
        NewLinearAsset(linkId = 442444, startMeasure = 0, endMeasure = 10, value = DynamicValue(createValue()), sideCode = 1, 0, None))
      val newLinearAssets2 = Seq(NewLinearAsset(linkId = 442445, startMeasure = 0, endMeasure = 10, value = DynamicValue(createValue(PT_regulatory_number="2")), sideCode = 1, 0, None),
        NewLinearAsset(linkId = 442443, startMeasure = 0, endMeasure = 10, value = DynamicValue(createValue(PT_regulatory_number="2")), sideCode = 1, 0, None),
        NewLinearAsset(linkId = 442442, startMeasure = 0, endMeasure = 10, value = DynamicValue(createValue(PT_regulatory_number="2")), sideCode = 1, 0, None),
        NewLinearAsset(linkId = 442444, startMeasure = 0, endMeasure = 10, value = DynamicValue(createValue(PT_regulatory_number="2")), sideCode = 1, 0, None))
      val id1 = ServiceWithDao.create(newLinearAssets, typeId = 460, username = "testuser", mockVVHClient.createVVHTimeStamp())
      val id2 = ServiceWithDao.create(newLinearAssets2, typeId = 460, username = "testuser", mockVVHClient.createVVHTimeStamp())
      val ids = id1 ++ id2
      val assetReturn =ServiceWithDao.getPersistedAssetsByIds(460,ids.toSet)
      assetReturn should not be null
      assetReturn(0).value should not be null
      assetReturn(1).value should not be null
    }
  }

  test("create new LengthOfRoadAxis asset, save two item to one roadlink, get two item from one getPersistedAssetsByLinkIds, test with rounabout") {

    val roadLinks = Seq(
      RoadLink(442445, Seq(Point(384970, 6671649), Point(384968, 6671653), Point(384969, 6671660), Point(384975, 6671664)),
        2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(442443, Seq(Point(384985, 6671649), Point(384980, 6671646), Point(384972, 6671647), Point(384970, 6671649)),
        2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(442442, Seq(Point(384985, 6671649), Point(384986, 6671650), Point(384987, 6671657), Point(384986, 6671660)),
        2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None),
      RoadLink(442444, Seq(Point(384986, 6671660), Point(384983, 6671663), Point(384976, 6671665), Point(384975, 6671664)),
        2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None)
    )
    when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(442445,442443,442442,442444), false))
      .thenReturn(roadLinks)

    runWithRollback {
      val newLinearAssets = Seq(
        NewLinearAsset(linkId = 442445, startMeasure = 0, endMeasure = 10, value = DynamicValue(createValue()), sideCode = 1, 0, None),
        NewLinearAsset(linkId = 442443, startMeasure = 0, endMeasure = 10, value = DynamicValue(createValue()), sideCode = 1, 0, None),
        NewLinearAsset(linkId = 442442, startMeasure = 0, endMeasure = 10, value = DynamicValue(createValue()), sideCode = 1, 0, None),
        NewLinearAsset(linkId = 442444, startMeasure = 0, endMeasure = 10, value = DynamicValue(createValue()), sideCode = 1, 0, None))
      val newLinearAssets2 = Seq(
        NewLinearAsset(linkId = 442445, startMeasure = 0, endMeasure = 10, value = DynamicValue(createValue(PT_regulatory_number="2")), sideCode = 1, 0, None),
        NewLinearAsset(linkId = 442443, startMeasure = 0, endMeasure = 10, value = DynamicValue(createValue(PT_regulatory_number="2")), sideCode = 1, 0, None),
        NewLinearAsset(linkId = 442442, startMeasure = 0, endMeasure = 10, value = DynamicValue(createValue(PT_regulatory_number="2")), sideCode = 1, 0, None),
        NewLinearAsset(linkId = 442444, startMeasure = 0, endMeasure = 10, value = DynamicValue(createValue(PT_regulatory_number="2")), sideCode = 1, 0, None))
      ServiceWithDao.create(newLinearAssets, typeId = 460, username = "testuser", mockVVHClient.createVVHTimeStamp())
      ServiceWithDao.create(newLinearAssets2, typeId = 460, username = "testuser", mockVVHClient.createVVHTimeStamp())
      val assetReturn =ServiceWithDao.getPersistedAssetsByLinkIds(460,roadLinks.map(roadLinks =>roadLinks.linkId))
      val values =(index:Int)=>assetReturn(index).value.getOrElse("error").asInstanceOf[DynamicValue].value.properties
      assetReturn should not be null
      values(0) should not be null
      values(1) should  not be null
    }
  }
  test("Fail if regulatory number already exist int the roadlink") {

    val roadLinks = Seq(
      RoadLink(442445, Seq(Point(384970, 6671649), Point(384968, 6671653), Point(384969, 6671660), Point(384975, 6671664)),
        2, Municipality, 1, TrafficDirection.BothDirections, Roundabout, None, None)
    )
    when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(Set(442445), false))
      .thenReturn(roadLinks)

    runWithRollback {
      val newLinearAssets = Seq(
        NewLinearAsset(linkId = 442445, startMeasure = 0, endMeasure = 10, value = DynamicValue(createValue()), sideCode = 1, 0, None))
      val newLinearAssets2 = Seq(
        NewLinearAsset(linkId = 442445, startMeasure = 0, endMeasure = 10, value = DynamicValue(createValue(PT_regulatory_number="2")), sideCode = 1, 0, None))
      val newLinearAssets3 = Seq(
        NewLinearAsset(linkId = 442445, startMeasure = 0, endMeasure = 10, value = DynamicValue(createValue(PT_regulatory_number="2")), sideCode = 1, 0, None))
      ServiceWithDao.create(newLinearAssets, typeId = 460, username = "testuser", mockVVHClient.createVVHTimeStamp())
      ServiceWithDao.create(newLinearAssets2, typeId = 460, username = "testuser", mockVVHClient.createVVHTimeStamp())

      val thrown = intercept[Exception]{
        ServiceWithDao.validateCondition(newLinearAssets3(0))
      }
      assert(thrown.getMessage === "This regulatory number already exist in this roadlink.")
    }
  }
}