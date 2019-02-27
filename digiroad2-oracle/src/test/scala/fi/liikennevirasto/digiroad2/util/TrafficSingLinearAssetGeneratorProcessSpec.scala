package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh._
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import fi.liikennevirasto.digiroad2.linearasset.{Prohibitions, RoadLink}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class TrafficSingLinearAssetGeneratorProcessSpec extends FunSuite with Matchers {
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockVVHClient = MockitoSugar.mock[VVHClient]
  val mockLinearAssetDao = MockitoSugar.mock[OracleLinearAssetDao]

  def service : TrafficSingLinearAssetGeneratorProcess = new TrafficSingLinearAssetGeneratorProcess(mockRoadLinkService) {
    override val oracleLinearAssetDao: OracleLinearAssetDao = mockLinearAssetDao
    override def withDynTransaction[T](f: => T): T = f
  }

  private def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  test("generate segments pieces pair sign"){
    val roadLinkNameB1 = RoadLink(1005, Seq(Point(0.0, 0.0), Point(0.0, 10.0)), 0, Municipality, 6, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "ROADNAME_FI" -> "Name B"))
    val roadLinkNameB2 = RoadLink(1010, Seq(Point(20.0, 0.0), Point(25.0, 10.0), Point(0.0, 10.0)), 0, Municipality, 6, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "ROADNAME_FI" -> "Name B"))

    val propertiesA = Seq(TrafficSignProperty(0, "trafficSigns_type", "", false, Seq(TextPropertyValue(NoPowerDrivenVehicles.OTHvalue.toString))))
    val trafficSign = PersistedTrafficSign(1, 1005, 0, 0, 0, false, 0, 235, propertiesA, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)
    val pairedTrafficSign = PersistedTrafficSign(2, 1010, 20, 0, 0, false, 0, 235, propertiesA, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)

    val allRoadLinks = Seq(roadLinkNameB1, roadLinkNameB2)
    when(mockRoadLinkService.getAdjacentTemp(1005)).thenReturn(Seq(roadLinkNameB1))
    when(mockRoadLinkService.getAdjacentTemp(1010)).thenReturn(Seq(roadLinkNameB2))

    val result= service.segmentsManager(allRoadLinks, Seq(trafficSign, pairedTrafficSign), Seq()).toSeq.sortBy(_.roadLink.linkId)
    result.size should be (2)
    result.head.roadLink.linkId should be (1005)
    result.head.startMeasure should be (0)
    result.head.endMeasure should be (10)
    result.head.sideCode should be (SideCode.BothDirections)
    result.last.roadLink.linkId should be (1010)
    result.last.startMeasure should be (0)
    result.last.endMeasure should be (GeometryUtils.geometryLength(Seq(Point(20.0, 0.0), Point(25.0, 10.0), Point(0.0, 10.0))))
    result.last.sideCode should be (SideCode.BothDirections)
  }

  test("generate segments pieces pair and unpair"){
    val roadLinkNameA = RoadLink(1000, Seq(Point(0.0, 0.0), Point(0.0, 20.0)), 0, Municipality, 6, TrafficDirection.BothDirections, Motorway, None, None, Map("ROADNAME_FI" -> "Name A"))
    val roadLinkNameB1 = RoadLink(1005, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 0, Municipality, 6, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "ROADNAME_FI" -> "Name B"))
    val roadLinkNameB2 = RoadLink(1010, Seq(Point(10.0, 0.0), Point(20.0, 0.0)), 0, Municipality, 6, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "ROADNAME_FI" -> "Name B"))
    val roadLinkNameB3 = RoadLink(1015, Seq(Point(20.0, 0.0), Point(40.0, 0.0)), 0, Municipality, 6, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "ROADNAME_FI" -> "Name B"))
    val roadLinkNameC = RoadLink(1020, Seq(Point(40.0, 0.0), Point(0.0, 20.0)), 0,  Municipality, 6, TrafficDirection.BothDirections, Motorway, None, None, Map("ROADNAME_FI" -> "Name C"))

    val propertiesA = Seq(TrafficSignProperty(0, "trafficSigns_type", "", false, Seq(TextPropertyValue(NoPowerDrivenVehicles.OTHvalue.toString))))
    val propertiesB = Seq(TrafficSignProperty(0, "trafficSigns_type", "", false, Seq(TextPropertyValue(NoLorriesAndVans.OTHvalue.toString)))) //value 6
    val trafficSign = PersistedTrafficSign(1, 1005, 0, 0, 0, false, 0, 235, propertiesA, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)
    val pairedTrafficSign = PersistedTrafficSign(2, 1015, 20, 0, 0, false, 0, 235, propertiesA, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)
    val unPairedTrafficSign = PersistedTrafficSign(3, 1010, 10, 0, 8, false, 0, 235, propertiesB, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)

    val allRoadLinks = Seq(roadLinkNameB1, roadLinkNameB2, roadLinkNameB3)
    when(mockRoadLinkService.getAdjacentTemp(1005)).thenReturn(Seq(roadLinkNameB1, roadLinkNameA))
    when(mockRoadLinkService.getAdjacentTemp(1010)).thenReturn(Seq())
    when(mockRoadLinkService.getAdjacentTemp(1015)).thenReturn(Seq(roadLinkNameB3, roadLinkNameC))

    val result = service.segmentsManager(allRoadLinks, Seq(trafficSign, pairedTrafficSign, unPairedTrafficSign), Seq()).toSeq.sortBy(_.roadLink.linkId)
    result.size should be (5)
    result.head.roadLink.linkId should be (1005)
    result.head.startMeasure should be (0)
    result.head.endMeasure should be (10)
    result.head.sideCode should be (SideCode.BothDirections)
    result.tail.head.roadLink.linkId should be (1010)
    val tailResult = result.tail.sortBy(_.startMeasure)
    tailResult.head.startMeasure should be (0)
    tailResult.head.endMeasure should be (8)
    tailResult.head.sideCode should be (SideCode.BothDirections)
    tailResult.last.startMeasure should be (8)
    tailResult.last.endMeasure should be (10)
    tailResult.last.sideCode should be (SideCode.TowardsDigitizing)
    result.last.roadLink.linkId should be (1015)
    result.last.startMeasure should be (0)
    result.last.endMeasure should be (20)
    result.last.sideCode should be (SideCode.TowardsDigitizing)
  }

  test("generate segments pieces 2 pair signs"){
    val roadLinkNameA = RoadLink(1000, Seq(Point(0.0, 0.0), Point(0.0, 20.0)), 0, Municipality, 6, TrafficDirection.BothDirections, Motorway, None, None, Map("ROADNAME_FI" -> "Name A"))
    val roadLinkNameB1 = RoadLink(1005, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 0, Municipality, 6, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "ROADNAME_FI" -> "Name B"))
    val roadLinkNameB2 = RoadLink(1010, Seq(Point(10.0, 0.0), Point(20.0, 0.0)), 0, Municipality, 6, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "ROADNAME_FI" -> "Name B"))
    val roadLinkNameB3 = RoadLink(1015, Seq(Point(20.0, 0.0), Point(40.0, 0.0)), 0, Municipality, 6, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "ROADNAME_FI" -> "Name B"))
    val roadLinkNameC = RoadLink(1020, Seq(Point(40.0, 0.0), Point(0.0, 20.0)), 0,  Municipality, 6, TrafficDirection.BothDirections, Motorway, None, None, Map("ROADNAME_FI" -> "Name C"))

    val propertiesA = Seq(TrafficSignProperty(0, "trafficSigns_type", "", false, Seq(TextPropertyValue(NoPowerDrivenVehicles.OTHvalue.toString))))
    val propertiesB = Seq(TrafficSignProperty(0, "trafficSigns_type", "", false, Seq(TextPropertyValue(NoLorriesAndVans.OTHvalue.toString))))
    val trafficSign1 = PersistedTrafficSign(1, 1005, 0, 0, 0, false, 0, 235, propertiesA, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)
    val trafficSign2 = PersistedTrafficSign(3, 1005, 8, 0, 8, false, 0, 235, propertiesB, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)
    val pairedSign1 = PersistedTrafficSign(2, 1015, 20, 0, 0, false, 0, 235, propertiesA, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)
    val pairedSign2 = PersistedTrafficSign(4, 1015, 30, 0, 10, false, 0, 235, propertiesB, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)

    val allRoadLinks = Seq(roadLinkNameB1, roadLinkNameB2, roadLinkNameB3)
    when(mockRoadLinkService.getAdjacentTemp(1005)).thenReturn(Seq(roadLinkNameB1, roadLinkNameA))
    when(mockRoadLinkService.getAdjacentTemp(1010)).thenReturn(Seq())
    when(mockRoadLinkService.getAdjacentTemp(1015)).thenReturn(Seq(roadLinkNameB3, roadLinkNameC))

    val result = service.segmentsManager(allRoadLinks, Seq(trafficSign1, trafficSign2, pairedSign1, pairedSign2), Seq()).toSeq.sortBy(_.roadLink.linkId)
    result.size should be (4)
    val resultB1 = result.filter(_.roadLink.linkId == 1005).sortBy(_.startMeasure)
    resultB1.head.startMeasure should be (0)
    resultB1.head.endMeasure should be (8)
    resultB1.head.sideCode should be (SideCode.BothDirections)
    resultB1.head.value.asInstanceOf[Prohibitions].prohibitions.head.typeId should be (2)
    resultB1.last.startMeasure should be (8)
    resultB1.last.endMeasure should be (10)
    resultB1.last.sideCode should be (SideCode.BothDirections)
    resultB1.last.value.asInstanceOf[Prohibitions].prohibitions.forall(x => Seq(2,6).contains(x.typeId)) should be (true)
    val resultB2 = result.find(_.roadLink.linkId == 1010).get
    resultB2.startMeasure should be (0)
    resultB2.endMeasure should be (10)
    resultB2.sideCode should be (SideCode.BothDirections)
    resultB2.value.asInstanceOf[Prohibitions].prohibitions.forall(x => Seq(2,6).contains(x.typeId))  should be (true)
    val resultB3 = result.find(_.roadLink.linkId == 1015).get
    resultB3.startMeasure should be (0)
    resultB3.endMeasure should be (10)
    resultB3.sideCode should be (SideCode.BothDirections)
    resultB3.value.asInstanceOf[Prohibitions].prohibitions.head.typeId should be (6)
  }

  test("generate segments pieces on a endRoadLink BothDirections"){
    val roadLinkNameA = RoadLink(1000, Seq(Point(0.0, 0.0), Point(0.0, 20.0)), 0, Municipality, 6, TrafficDirection.BothDirections, Motorway, None, None, Map("ROADNAME_FI" -> "Name A"))
    val roadLinkNameB1 = RoadLink(1005, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 0, Municipality, 6, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "ROADNAME_FI" -> "Name B"))
    val roadLinkNameB2 = RoadLink(1010, Seq(Point(10.0, 0.0), Point(20.0, 0.0)), 0, Municipality, 6, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "ROADNAME_FI" -> "Name B"))

    val propertiesA = Seq(TrafficSignProperty(0, "trafficSigns_type", "", false, Seq(TextPropertyValue(NoPowerDrivenVehicles.OTHvalue.toString))))
    val trafficSign = PersistedTrafficSign(1, 1005, 5, 0, 5, false, 0, 235, propertiesA, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)

    val allRoadLinks = Seq(roadLinkNameB1, roadLinkNameB2)
    when(mockRoadLinkService.getAdjacentTemp(1005)).thenReturn(Seq(roadLinkNameB2, roadLinkNameA))
    when(mockRoadLinkService.getAdjacentTemp(1010)).thenReturn(Seq(roadLinkNameB1))

    val result = service.segmentsManager(allRoadLinks, Seq(trafficSign), Seq())
    result.size should be (2)
    val resultB1 = result.find(_.roadLink.linkId == 1005).get
    resultB1.startMeasure should be (5)
    resultB1.endMeasure should be (10)
    resultB1.sideCode should be (SideCode.BothDirections)
    resultB1.value.asInstanceOf[Prohibitions].prohibitions.head.typeId should be (2)
    val resultB2 = result.find(_.roadLink.linkId == 1010).get
    resultB2.startMeasure should be (0)
    resultB2.endMeasure should be (10)
    resultB2.sideCode should be (SideCode.BothDirections)
    resultB2.value.asInstanceOf[Prohibitions].prohibitions.head.typeId should be (2)
  }
}

