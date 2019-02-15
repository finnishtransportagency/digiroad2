package fi.liikennevirasto.digiroad2.util

import com.jolbox.bonecp.{BoneCPConfig, BoneCPDataSource}
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh._
import fi.liikennevirasto.digiroad2.dao.{MunicipalityDao, OracleAssetDao}
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.ProhibitionService
import fi.liikennevirasto.digiroad2.service.pointasset.{IncomingTrafficSign, TrafficSignInfo}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.{FunSuite, Matchers}
import org.scalatest.mockito.MockitoSugar
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{StaticQuery => Q}

class TrafficSgingLinearAssetGeneratorProcessSpec extends FunSuite with Matchers {
  lazy val dataSource = {
    val cfg = new BoneCPConfig(OracleDatabase.loadProperties("/bonecp.properties"))
    new BoneCPDataSource(cfg)
  }

  private def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)


  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockVVHClient = MockitoSugar.mock[VVHClient]
  val mockVVHRoadLinkClient = MockitoSugar.mock[VVHRoadLinkClient]
  val mockPolygonTools = MockitoSugar.mock[PolygonTools]

  when(mockVVHClient.roadLinkData).thenReturn(mockVVHRoadLinkClient)
  when(mockVVHRoadLinkClient.fetchByLinkId(388562360l)).thenReturn(Some(VVHRoadlink(388562360l, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockVVHRoadLinkClient.fetchByLinkIds(any[Set[Long]])).thenReturn(Seq(VVHRoadlink(388562360l, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))
  when(mockVVHClient.fetchRoadLinkByLinkId(any[Long])).thenReturn(Some(VVHRoadlink(388562360l, 235, Seq(Point(0, 0), Point(10, 0)), Municipality, TrafficDirection.UnknownDirection, FeatureClass.AllOthers)))

  val roadLinkWithLinkSource = RoadLink(
    1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
    1, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "SURFACETYPE" -> BigInt(2)), ConstructionType.InUse, LinkGeomSource.NormalLinkInterface)
  when(mockRoadLinkService.getRoadLinksAndChangesFromVVH(any[BoundingRectangle], any[Set[Int]])).thenReturn((List(roadLinkWithLinkSource), Nil))
  when(mockRoadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(any[Int])).thenReturn((List(roadLinkWithLinkSource), Nil))
  when(mockRoadLinkService.getRoadLinksAndComplementariesFromVVH(any[Set[Long]], any[Boolean])).thenReturn(Seq(roadLinkWithLinkSource))

  val mockLinearAssetDao = MockitoSugar.mock[OracleLinearAssetDao]
  val mockEventBus = MockitoSugar.mock[DigiroadEventBus]
  val linearAssetDao = new OracleLinearAssetDao(mockVVHClient, mockRoadLinkService)
  val mockMunicipalityDao = MockitoSugar.mock[MunicipalityDao]

  val vvhRoadLinkNameA = VVHRoadlink(1000, 235, Seq(Point(10.0, 20.0), Point(30.0, 20.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, attributes = Map("ROADNAME_FI" -> "Name A"))
  val vvhRoadLinkNameB1 = VVHRoadlink(1005, 235, Seq(Point(30.0, 20.0), Point(40.0, 20.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, attributes = Map("ROADNAME_FI" -> "Name B"))
  val vvhRoadLinkNameB2 = VVHRoadlink(1010, 235, Seq(Point(40.0, 20.0), Point(50.0, 20.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, attributes = Map("ROADNAME_FI" -> "Name B"))
  val vvhRoadLinkNameB3 = VVHRoadlink(1015, 235, Seq(Point(50.0, 20.0), Point(60.0, 20.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, attributes = Map("ROADNAME_FI" -> "Name B"))
  val vvhRoadLinkNameC = VVHRoadlink(1020, 235, Seq(Point(60.0, 20.0), Point(70.0, 20.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, attributes = Map("ROADNAME_FI" -> "Name C"))


  //  test("should just return adjacents with the same road name to create prohibition "){
//    runWithRollback{
//      val service = new ProhibitionService(mockRoadLinkService, new DummyEventBus) {
//        override def withDynTransaction[T](f: => T): T = f
//        override def vvhClient: VVHClient = mockVVHClient
//      }
//
//      val sourceRoadLink =  RoadLink(1000, Seq(Point(0.0, 0.0), Point(0.0, 100.0)), GeometryUtils.geometryLength(Seq(Point(0.0, 0.0), Point(0.0, 100.0))), Municipality, 6, TrafficDirection.TowardsDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "ROADNAME_FI"->"RoadName_fi"))
//      val roadLink = RoadLink(1001, Seq(Point(0, 100), Point(0, 250)), GeometryUtils.geometryLength(Seq(Point(0, 0), Point(0, 250))), Municipality, 6, TrafficDirection.TowardsDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "ROADNAME_FI"->"RoadName_fi"))
//      val roadLink1 =  RoadLink(1002, Seq(Point(0, 250), Point(0, 500)), GeometryUtils.geometryLength(Seq(Point(0, 0), Point(0, 500))), Municipality, 6, TrafficDirection.TowardsDigitizing, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "ROADNAME_FI"->"RoadName"))
//
//      val properties = Set(
//        SimpleTrafficSignProperty("trafficSigns_type", List(TextPropertyValue( ClosedToAllVehicles.OTHvalue.toString))))
//
//      when(mockRoadLinkService.getRoadLinkEndDirectionPoints(any[RoadLink], any[Option[Int]])).thenReturn(Seq(Point(0, 100)))
//
//      when(mockRoadLinkService.getAdjacent(1000, Seq(Point(0, 100)), false)).thenReturn(Seq(roadLink))
//      when(mockRoadLinkService.getAdjacent(1001,  Seq(Point(0, 250)), false)).thenReturn(Seq(roadLink1))
//      when(mockRoadLinkService.getAdjacent(1002,  Seq(Point(0, 500)), false)).thenReturn(Seq.empty)
//
//      val id = trafficSignService.create(IncomingTrafficSign(0, 50, 1000, properties, 2, None), "test_username", sourceRoadLink)
//      val asset = trafficSignService.getPersistedAssetsByIds(Set(id)).head
//
//      val prohibitionIds = service.createBasedOnTrafficSign(TrafficSignInfo(asset.id, asset.linkId, asset.validityDirection, ClosedToAllVehicles.OTHvalue , asset.mValue, sourceRoadLink, Seq()), false)
//      val prohibitions = service.getPersistedAssetsByIds(Prohibition.typeId, prohibitionIds.toSet)
//      prohibitions.length should be (2)
//
//      val first = prohibitions.find(_.linkId == 1000).get
//      first.startMeasure should be (50)
//      first.endMeasure should be (100)
//
//      val second =  prohibitions.find(_.linkId == 1001).get
//      second.startMeasure should be (0)
//      second.endMeasure should be (250)
//    }
//  }

  def toRoadLink(l: VVHRoadlink) = {
    RoadLink(l.linkId, l.geometry, GeometryUtils.geometryLength(l.geometry),
      l.administrativeClass, 1, l.trafficDirection, UnknownLinkType, None, None, l.attributes + ("MUNICIPALITYCODE" -> BigInt(l.municipalityCode)))
  }

  test("create linear asset according traffic sign without pair on same road name") {
    runWithRollback {
      val roadLinkNameB1 = RoadLink(1005, Seq(Point(30.0, 20.0), Point(40.0, 20.0)), GeometryUtils.geometryLength(Seq(Point(30.0, 20.0), Point(40.0, 20.0))), Municipality, 6, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "ROADNAME_FI" -> "Name B"))

      when(mockRoadLinkService.getRoadLinkByLinkIdFromVVH(any[Long], any[Boolean])).thenReturn(Some(roadLinkNameB1))
      when(mockRoadLinkService.fetchVVHRoadlinks(Set("Name B"), "ROADNAME_FI")).thenReturn(Seq(vvhRoadLinkNameB1, vvhRoadLinkNameB2, vvhRoadLinkNameB3))

      val properties = Seq(TrafficSignProperty(0, "trafficSigns_type", "", false, Seq(TextPropertyValue(NoPowerDrivenVehicles.OTHvalue.toString))))

      val trafficSign = PersistedTrafficSign(1, 1005, 32, 20, 2, false, 0, 235, properties, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)
      //      val pairedTrafficSign = PersistedTrafficSign(1, 1015, 48, 20, 2, false, 0, 235, properties, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)

//      when(mockTrafficSignService.getTrafficSign(any[Seq[Long]])).thenReturn(Seq(trafficSign))

      TrafficSgingLinearAssetGeneratorProcess.createLinearXXXX(trafficSign, roadLinkNameB1)

    }
  }

  test("create linear asset according traffic sign with pair on same road name"){
    runWithRollback{
      val roadLinkNameB1 = RoadLink(1005, Seq(Point(30.0, 20.0), Point(40.0, 20.0)), GeometryUtils.geometryLength(Seq(Point(30.0, 20.0), Point(40.0, 20.0))), Municipality, 6, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "ROADNAME_FI" -> "Name B"))

      val vvhRoadLinkNameA = VVHRoadlink(1000, 235,Seq(Point(10.0, 20.0), Point(30.0, 20.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, attributes = Map("ROADNAME_FI" -> "Name A"))
      val vvhRoadLinkNameB1 = VVHRoadlink(1005, 235, Seq(Point(30.0, 20.0), Point(40.0, 20.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, attributes = Map( "ROADNAME_FI" -> "Name B"))
      val vvhRoadLinkNameB2 = VVHRoadlink(1010, 235, Seq(Point(40.0, 20.0), Point(50.0, 20.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, attributes = Map("ROADNAME_FI" -> "Name B"))
      val vvhRoadLinkNameB3 = VVHRoadlink(1015, 235, Seq(Point(50.0, 20.0), Point(60.0, 20.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, attributes = Map( "ROADNAME_FI" -> "Name B"))
      val vvhRoadLinkNameC = VVHRoadlink(1020, 235, Seq(Point(60.0, 20.0), Point(70.0, 20.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, attributes = Map("ROADNAME_FI" -> "Name C"))

      //when(mockRoadLinkService.getRoadLinkByLinkIdFromVVH(any[Long], any[Boolean])).thenReturn(Some(roadLinkNameB1))
      when(mockRoadLinkService.fetchVVHRoadlinks(Set("Name B"), "ROADNAME_FI" )).thenReturn(Seq(vvhRoadLinkNameB1, vvhRoadLinkNameB2, vvhRoadLinkNameB3))
      when(mockRoadLinkService.enrichRoadLinksFromVVH(any[Seq[VVHRoadlink]], any[Seq[ChangeInfo]])).thenReturn(Seq())

      val properties = Seq(TrafficSignProperty(0, "trafficSigns_type", "", false, Seq(TextPropertyValue(NoPowerDrivenVehicles.OTHvalue.toString))))

      val trafficSign = PersistedTrafficSign(1, 1005, 32, 20, 2, false, 0, 235, properties, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)
      val pairedTrafficSign = PersistedTrafficSign(2, 1015, 58, 20, 8, false, 0, 235, properties, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)

//      when(mockTrafficSignService.getTrafficSign(any[Seq[Long]])).thenReturn(Seq(trafficSign, pairedTrafficSign))

      val result = TrafficSgingLinearAssetGeneratorProcess(trafficSign, roadLinkNameB1)
      println(result)
    }
  }

  test("test combine assets"){
    runWithRollback{
      val roadLinkNameB1 = RoadLink(1005, Seq(Point(30.0, 20.0), Point(40.0, 20.0)), GeometryUtils.geometryLength(Seq(Point(30.0, 20.0), Point(40.0, 20.0))), Municipality, 6, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "ROADNAME_FI" -> "Name B"))

      val vvhRoadLinkNameA = VVHRoadlink(1000, 235,Seq(Point(10.0, 20.0), Point(30.0, 20.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, attributes = Map("ROADNAME_FI" -> "Name A"))
      val vvhRoadLinkNameB1 = VVHRoadlink(1005, 235, Seq(Point(30.0, 20.0), Point(40.0, 20.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, attributes = Map( "ROADNAME_FI" -> "Name B"))
      val vvhRoadLinkNameB2 = VVHRoadlink(1010, 235, Seq(Point(40.0, 20.0), Point(50.0, 20.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, attributes = Map("ROADNAME_FI" -> "Name B"))
      val vvhRoadLinkNameB3 = VVHRoadlink(1015, 235, Seq(Point(50.0, 20.0), Point(60.0, 20.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, attributes = Map( "ROADNAME_FI" -> "Name B"))
      val vvhRoadLinkNameC = VVHRoadlink(1020, 235, Seq(Point(60.0, 20.0), Point(70.0, 20.0)), Municipality, TrafficDirection.BothDirections, FeatureClass.AllOthers, attributes = Map("ROADNAME_FI" -> "Name C"))

      //when(mockRoadLinkService.getRoadLinkByLinkIdFromVVH(any[Long], any[Boolean])).thenReturn(Some(roadLinkNameB1))
      when(mockRoadLinkService.fetchVVHRoadlinks(Set("Name B"), "ROADNAME_FI" )).thenReturn(Seq(vvhRoadLinkNameB1, vvhRoadLinkNameB2, vvhRoadLinkNameB3))
      when(mockRoadLinkService.enrichRoadLinksFromVVH(any[Seq[VVHRoadlink]], any[Seq[ChangeInfo]])).thenReturn(Seq())

      val properties = Seq(TrafficSignProperty(0, "trafficSigns_type", "", false, Seq(TextPropertyValue(NoPowerDrivenVehicles.OTHvalue.toString))))

      val trafficSign = PersistedTrafficSign(1, 1005, 32, 20, 2, false, 0, 235, properties, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)
      val pairedTrafficSign = PersistedTrafficSign(2, 1015, 58, 20, 8, false, 0, 235, properties, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)

//      when(mockTrafficSignService.getTrafficSign(any[Seq[Long]])).thenReturn(Seq(trafficSign, pairedTrafficSign))

      val result = TrafficSgingLinearAssetGeneratorProcess.createLinearXXXX(trafficSign, roadLinkNameB1)
      println(result)
    }
  }
}
