package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset.ProhibitionClass.{Bus => _, _}
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import org.mockito.Mockito._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh._
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset.{Value, _}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}
import slick.driver.JdbcDriver.backend.Database.dynamicSession
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{StaticQuery => Q}
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import com.vividsolutions.jts.util.Assert
import fi.liikennevirasto.digiroad2.service.linearasset.{LinearAssetTypes, Measures, ProhibitionService}
import org.mockito.ArgumentMatchers.any

class TrafficSignLinearGeneratorSpec extends FunSuite with Matchers {
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val mockVVHClient = MockitoSugar.mock[VVHClient]
  val linearAssetDao = new OracleLinearAssetDao(mockVVHClient, mockRoadLinkService)
  val mockProhibitionService = MockitoSugar.mock[ProhibitionService]

  class TestTrafficSignProhibitionGenerator extends TrafficSignProhibitionGenerator(mockRoadLinkService) {
    override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)

    override def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)

    override lazy val oracleLinearAssetDao: OracleLinearAssetDao = linearAssetDao
    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
    override lazy val vvhClient: VVHClient = mockVVHClient

    override lazy val prohibitionService = mockProhibitionService

    var createAssetRelationObject: Seq[(Long, Long)] = Seq()

    override def createAssetRelation(linearAssetId: Long, trafficSignId: Long): Unit = {
      createAssetRelationObject = List.concat(createAssetRelationObject , Seq((linearAssetId, trafficSignId)))
    }

    def getCreateAssetRelationInfo: Seq[(Long, Long)] = {
      createAssetRelationObject
    }

    var createAssetObject: Seq[(Long, Value, Int,  Measures, String, Option[RoadLinkLike])] = Seq()

    override def createLinearAsset(newSegment: TrafficSignToLinear, username: String) : Long = {
      createAssetObject = List.concat(createAssetObject , Seq((newSegment.roadLink.linkId, newSegment.value, newSegment.sideCode.value, Measures(newSegment.startMeasure, newSegment.endMeasure), username, Some(newSegment.roadLink))))
     0L
    }

    def getCreateInfo: Seq[(Long, Value, Int,  Measures, String, Option[RoadLinkLike])] = {
      createAssetObject
    }

    var updateAssetObject: Seq[(Seq[Long], Value, String)] = Seq()

    override def updateLinearAsset(newSegment: TrafficSignToLinear, username: String) : Seq[Long] = {
    updateAssetObject = List.concat(updateAssetObject , Seq((Seq(newSegment.oldAssetId.get),  newSegment.value, username)))
      Seq(newSegment.oldAssetId.get)
    }

    def getUpdateInfo: Seq[(Seq[Long], Value, String)] = {
      updateAssetObject
    }

    var deleteAssetObject: Seq[(Seq[Long])] = Seq()

    override def deleteLinearAssets(existingSeg: Seq[TrafficSignToLinear]) : Unit = {
      deleteAssetObject = List.concat(deleteAssetObject , Seq(existingSeg.flatMap(_.oldAssetId)))
    }

    def getDeleteInfo: Seq[(Seq[Long])] = {
      deleteAssetObject
    }




  }

  val prohibitionGenerator : TrafficSignProhibitionGenerator = new TestTrafficSignProhibitionGenerator

  val roadLinkNameB1 = RoadLink(1005, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 0, Municipality, 6, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "ROADNAME_FI" -> "Name B"))
  val roadLinkNameB2 = RoadLink(1010, Seq(Point(10.0, 0.0), Point(20.0, 0.0)), 0, Municipality, 6, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "ROADNAME_FI" -> "Name B"))
  val roadLinkNameB3 = RoadLink(1015, Seq(Point(20.0, 0.0), Point(40.0, 0.0)), 0, Municipality, 6, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "ROADNAME_FI" -> "Name B"))
  val roadLinkNameA = RoadLink(1000, Seq(Point(0.0, 0.0), Point(0.0, 20.0)), 0, Municipality, 6, TrafficDirection.BothDirections, Motorway, None, None, Map("ROADNAME_FI" -> "Name A"))
  val roadLinkNameC = RoadLink(1020, Seq(Point(40.0, 0.0), Point(0.0, 20.0)), 0,  Municipality, 6, TrafficDirection.BothDirections, Motorway, None, None, Map("ROADNAME_FI" -> "Name C"))


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

    val result= prohibitionGenerator.segmentsManager(allRoadLinks, Seq(trafficSign, pairedTrafficSign), Seq()).toSeq.sortBy(_.roadLink.linkId)
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

    val propertiesA = Seq(TrafficSignProperty(0, "trafficSigns_type", "", false, Seq(TextPropertyValue(NoPowerDrivenVehicles.OTHvalue.toString))))
    val propertiesB = Seq(TrafficSignProperty(0, "trafficSigns_type", "", false, Seq(TextPropertyValue(NoLorriesAndVans.OTHvalue.toString)))) //value 6
    val trafficSign = PersistedTrafficSign(1, 1005, 0, 0, 0, false, 0, 235, propertiesA, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)
    val pairedTrafficSign = PersistedTrafficSign(2, 1015, 30, 0, 10, false, 0, 235, propertiesA, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)
    val unPairedTrafficSign = PersistedTrafficSign(3, 1010, 10, 0, 8, false, 0, 235, propertiesB, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)

    val allRoadLinks = Seq(roadLinkNameB1, roadLinkNameB2, roadLinkNameB3)
    when(mockRoadLinkService.getAdjacentTemp(1005)).thenReturn(Seq(roadLinkNameA, roadLinkNameB2))
    when(mockRoadLinkService.getAdjacentTemp(1010)).thenReturn(Seq())
    when(mockRoadLinkService.getAdjacentTemp(1015)).thenReturn(Seq(roadLinkNameB2, roadLinkNameC))

    val result = prohibitionGenerator.segmentsManager(allRoadLinks, Seq(trafficSign, pairedTrafficSign, unPairedTrafficSign), Seq()).toSeq.sortBy(_.roadLink.linkId)
    result.size should be (7)
    val resultB1 = result.filter(_.roadLink == roadLinkNameB1).head
    resultB1.startMeasure should be (0)
    resultB1.endMeasure should be (10)
    resultB1.sideCode should be (SideCode.BothDirections)
    val resultB2 = result.filter(_.roadLink == roadLinkNameB2).sortBy(x => (x.startMeasure, x.sideCode.value))
    resultB2.head.startMeasure should be (0)
    resultB2.head.endMeasure should be (8)
    resultB2.head.sideCode should be (SideCode.BothDirections)
    resultB2.tail.head.startMeasure should be (8)
    resultB2.tail.head.endMeasure should be (10)
    resultB2.tail.head.sideCode should be (SideCode.TowardsDigitizing)
    resultB2.last.startMeasure should be (8)
    resultB2.last.endMeasure should be (10)
    resultB2.last.sideCode should be (SideCode.AgainstDigitizing)
    val resultB3 = result.filter(_.roadLink == roadLinkNameB3).sortBy(x => (x.startMeasure, x.sideCode.value))
    resultB3.head.startMeasure should be (0)
    resultB3.head.endMeasure should be (10)
    resultB3.head.sideCode should be (SideCode.TowardsDigitizing)
    resultB3.tail.head.startMeasure should be (0)
    resultB3.tail.head.endMeasure should be (10)
    resultB3.tail.head.sideCode should be (SideCode.AgainstDigitizing)
    resultB3.last.startMeasure should be (10)
    resultB3.last.endMeasure should be (20)
    resultB3.last.sideCode should be (SideCode.TowardsDigitizing)
  }

  test("generate segments pieces 2 pair signs"){
//    val roadLinkNameA = RoadLink(1000, Seq(Point(0.0, 0.0), Point(0.0, 20.0)), 0, Municipality, 6, TrafficDirection.BothDirections, Motorway, None, None, Map("ROADNAME_FI" -> "Name A"))
//    val roadLinkNameB1 = RoadLink(1005, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 0, Municipality, 6, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "ROADNAME_FI" -> "Name B"))
//    val roadLinkNameB2 = RoadLink(1010, Seq(Point(10.0, 0.0), Point(20.0, 0.0)), 0, Municipality, 6, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "ROADNAME_FI" -> "Name B"))
//    val roadLinkNameB3 = RoadLink(1015, Seq(Point(20.0, 0.0), Point(40.0, 0.0)), 0, Municipality, 6, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "ROADNAME_FI" -> "Name B"))
    val roadLinkNameC = RoadLink(1020, Seq(Point(40.0, 0.0), Point(0.0, 20.0)), 0,  Municipality, 6, TrafficDirection.BothDirections, Motorway, None, None, Map("ROADNAME_FI" -> "Name C"))

    val propertiesA = Seq(TrafficSignProperty(0, "trafficSigns_type", "", false, Seq(TextPropertyValue(NoPowerDrivenVehicles.OTHvalue.toString))))
    val propertiesB = Seq(TrafficSignProperty(0, "trafficSigns_type", "", false, Seq(TextPropertyValue(NoLorriesAndVans.OTHvalue.toString))))
    val trafficSign1 = PersistedTrafficSign(1, 1005, 0, 0, 0, false, 0, 235, propertiesA, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)
    val trafficSign2 = PersistedTrafficSign(3, 1005, 8, 0, 8, false, 0, 235, propertiesB, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)
    val pairedSign1 = PersistedTrafficSign(2, 1015, 20, 0, 0, false, 0, 235, propertiesA, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)
    val pairedSign2 = PersistedTrafficSign(4, 1015, 30, 0, 10, false, 0, 235, propertiesB, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)

    val allRoadLinks = Seq(roadLinkNameB1, roadLinkNameB2, roadLinkNameB3)
    when(mockRoadLinkService.getAdjacentTemp(1005)).thenReturn(Seq(roadLinkNameA, roadLinkNameB2))
    when(mockRoadLinkService.getAdjacentTemp(1010)).thenReturn(Seq())
    when(mockRoadLinkService.getAdjacentTemp(1015)).thenReturn(Seq(roadLinkNameB2, roadLinkNameC))

    val result = prohibitionGenerator.segmentsManager(allRoadLinks, Seq(trafficSign1, trafficSign2, pairedSign1, pairedSign2), Seq()).toSeq.sortBy(_.roadLink.linkId)
    result.size should be (4)
    val resultB1 = result.filter(_.roadLink == roadLinkNameB1).sortBy(_.startMeasure)
    resultB1.head.startMeasure should be (0)
    resultB1.head.endMeasure should be (8)
    resultB1.head.sideCode should be (SideCode.BothDirections)
    resultB1.head.value.asInstanceOf[Prohibitions].prohibitions.head.typeId should be (2)
    resultB1.last.startMeasure should be (8)
    resultB1.last.endMeasure should be (10)
    resultB1.last.sideCode should be (SideCode.BothDirections)
    resultB1.last.value.asInstanceOf[Prohibitions].prohibitions.forall(x => Seq(2,4,6).contains(x.typeId)) should be (true)
    val resultB2 = result.find(_.roadLink == roadLinkNameB2).get
    resultB2.startMeasure should be (0)
    resultB2.endMeasure should be (10)
    resultB2.sideCode should be (SideCode.BothDirections)
    resultB2.value.asInstanceOf[Prohibitions].prohibitions.forall(x => Seq(2,4, 6).contains(x.typeId))  should be (true)
    val resultB3 = result.find(_.roadLink == roadLinkNameB3).get
    resultB3.startMeasure should be (0)
    resultB3.endMeasure should be (10)
    resultB3.sideCode should be (SideCode.BothDirections)
    resultB3.value.asInstanceOf[Prohibitions].prohibitions.head.typeId should be (6)
  }

  test("generate segments pieces on a endRoadLink BothDirections"){
//    val roadLinkNameA = RoadLink(1000, Seq(Point(0.0, 0.0), Point(0.0, 20.0)), 0, Municipality, 6, TrafficDirection.BothDirections, Motorway, None, None, Map("ROADNAME_FI" -> "Name A"))
//    val roadLinkNameB1 = RoadLink(1005, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 0, Municipality, 6, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "ROADNAME_FI" -> "Name B"))
//    val roadLinkNameB2 = RoadLink(1010, Seq(Point(10.0, 0.0), Point(20.0, 0.0)), 0, Municipality, 6, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "ROADNAME_FI" -> "Name B"))

    val propertiesA = Seq(TrafficSignProperty(0, "trafficSigns_type", "", false, Seq(TextPropertyValue(NoPowerDrivenVehicles.OTHvalue.toString))))
    val trafficSign = PersistedTrafficSign(1, 1005, 5, 0, 5, false, 0, 235, propertiesA, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)

    val allRoadLinks = Seq(roadLinkNameB1, roadLinkNameB2)
    when(mockRoadLinkService.getAdjacentTemp(1005)).thenReturn(Seq(roadLinkNameB2, roadLinkNameA))
    when(mockRoadLinkService.getAdjacentTemp(1010)).thenReturn(Seq(roadLinkNameB1))

    val result = prohibitionGenerator.segmentsManager(allRoadLinks, Seq(trafficSign), Seq())
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

  val relationSignProhibition : Map[ProhibitionsAndRestrictionsType, Seq[ProhibitionClass]] = Map(
    NoLorriesAndVans ->	Seq(ProhibitionClass.DeliveryCar, ProhibitionClass.Truck),
    NoVehicleCombinations -> Seq(ProhibitionClass.ArticulatedVehicle),
    NoAgriculturalVehicles -> Seq(ProhibitionClass.TractorFarmVehicle),
    NoMotorCycles -> Seq(ProhibitionClass.Motorcycle),
    NoMotorSledges -> Seq(ProhibitionClass.SnowMobile),
    NoBuses	-> Seq(ProhibitionClass.Bus),
    NoMopeds ->	Seq(ProhibitionClass.Moped),
    NoCyclesOrMopeds ->	Seq(ProhibitionClass.Moped, ProhibitionClass.Bicycle),
    NoPedestrians -> Seq(ProhibitionClass.Pedestrian),
    NoPedestriansCyclesMopeds -> Seq(ProhibitionClass.Moped,	ProhibitionClass.Bicycle, ProhibitionClass.Pedestrian),
    NoRidersOnHorseback	-> Seq(ProhibitionClass.HorseRiding)
  )

  test("create prohibitions values based on trafficSigns"){
    relationSignProhibition.foreach { case (sign, prohibitionsType) =>
      val simpleProp = Seq(TrafficSignProperty(0, "trafficSigns_type", "", false, Seq(TextPropertyValue(sign.OTHvalue.toString))))

      val trafficSign = PersistedTrafficSign(1, 1000, 100, 0, 50, false, 0, 235, simpleProp, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)
      val prohibitions = Prohibitions(prohibitionsType.map { prohibitionType => ProhibitionValue(prohibitionType.value, Set(), Set())})
      val prohibitionsResult = prohibitionGenerator.createValue(trafficSign)
      withClue("trafficSign sign " + sign) {
        prohibitions should be (prohibitionsResult)
      }
    }
  }

  test("create prohibitions values based on trafficSigns with additional Panels") {
    val additionalPanel = Seq(AdditionalPanel(ValidMonFri.OTHvalue, "9-10","", 1), AdditionalPanel(ValidSat.OTHvalue, "(11-12)","", 2), AdditionalPanel(ValidMultiplePeriod.OTHvalue, "15-16 (17-18) 19-20","", 3), AdditionalPanel(ValidMultiplePeriod.OTHvalue, "17-18","", 4))
    val prohibitionPeriod = Set(ValidityPeriod(9, 10, ValidityPeriodDayOfWeek.Weekday), ValidityPeriod(11, 12, ValidityPeriodDayOfWeek.Saturday), ValidityPeriod(17, 18, ValidityPeriodDayOfWeek.Sunday)
      ,ValidityPeriod(15, 16, ValidityPeriodDayOfWeek.Weekday) ,ValidityPeriod(17, 18, ValidityPeriodDayOfWeek.Saturday) ,ValidityPeriod(19, 20, ValidityPeriodDayOfWeek.Sunday))
    relationSignProhibition.foreach { case (sign, prohibitionsType) =>
      val simpleProp = Seq(TrafficSignProperty(0, "trafficSigns_type", "", false, Seq(TextPropertyValue(sign.OTHvalue.toString))) , TrafficSignProperty(0, "additional_panel", "", false, additionalPanel))


      val trafficSign = PersistedTrafficSign(1, 1000, 100, 0, 50, false, 0, 235, simpleProp, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)

      val prohibitions = Prohibitions(prohibitionsType.map { prohibitionType => ProhibitionValue(prohibitionType.value, prohibitionPeriod, Set())})
      val prohibitionsResult = prohibitionGenerator.createValue(trafficSign)
      withClue("trafficSign sign " + sign) {
        prohibitions should be (prohibitionsResult)
      }
    }
  }

  test("insert values  on trafficSigns with additional Panels") {
    val additionalPanel = Seq(AdditionalPanel(ValidMonFri.OTHvalue, "9-10","", 1), AdditionalPanel(ValidSat.OTHvalue, "(11-12)","", 2), AdditionalPanel(ValidMultiplePeriod.OTHvalue, "15-16 (17-18) 19-20","", 3), AdditionalPanel(ValidMultiplePeriod.OTHvalue, "17-18","", 4))
    val prohibitionPeriod = Set(ValidityPeriod(9, 10, ValidityPeriodDayOfWeek.Weekday), ValidityPeriod(11, 12, ValidityPeriodDayOfWeek.Saturday), ValidityPeriod(17, 18, ValidityPeriodDayOfWeek.Sunday)
      ,ValidityPeriod(15, 16, ValidityPeriodDayOfWeek.Weekday) ,ValidityPeriod(17, 18, ValidityPeriodDayOfWeek.Saturday) ,ValidityPeriod(19, 20, ValidityPeriodDayOfWeek.Sunday))
    relationSignProhibition.foreach { case (sign, prohibitionsType) =>
      val simpleProp = Seq(TrafficSignProperty(0, "trafficSigns_type", "", false, Seq(TextPropertyValue(sign.OTHvalue.toString))) , TrafficSignProperty(0, "additional_panel", "", false, additionalPanel))


      val trafficSign = PersistedTrafficSign(1, 1000, 100, 0, 50, false, 0, 235, simpleProp, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)

      val prohibitions = Prohibitions(prohibitionsType.map { prohibitionType => ProhibitionValue(prohibitionType.value, prohibitionPeriod, Set())})
      val prohibitionsResult = prohibitionGenerator.createValue(trafficSign)
      withClue("trafficSign sign " + sign) {
        prohibitions should be (prohibitionsResult)
      }
    }
  }

  test("test with old segments") {
//    val additionalPanel = Seq(AdditionalPanel(ValidMonFri.OTHvalue, "9-10","", 1), AdditionalPanel(ValidSat.OTHvalue, "(11-12)","", 2), AdditionalPanel(ValidMultiplePeriod.OTHvalue, "15-16 (17-18) 19-20","", 3), AdditionalPanel(ValidMultiplePeriod.OTHvalue, "17-18","", 4))
//    val prohibitionPeriod = Set(ValidityPeriod(9, 10, ValidityPeriodDayOfWeek.Weekday), ValidityPeriod(11, 12, ValidityPeriodDayOfWeek.Saturday), ValidityPeriod(17, 18, ValidityPeriodDayOfWeek.Sunday)
//      ,ValidityPeriod(15, 16, ValidityPeriodDayOfWeek.Weekday) ,ValidityPeriod(17, 18, ValidityPeriodDayOfWeek.Saturday) ,ValidityPeriod(19, 20, ValidityPeriodDayOfWeek.Sunday))
//    relationSignProhibition.foreach { case (sign, prohibitionsType) =>
//      val simpleProp = Seq(TrafficSignProperty(0, "trafficSigns_type", "", false, Seq(TextPropertyValue(sign.OTHvalue.toString))) , TrafficSignProperty(0, "additional_panel", "", false, additionalPanel))
//
//
//      val trafficSign = PersistedTrafficSign(1, 1000, 100, 0, 50, false, 0, 235, simpleProp, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)
//
//      val prohibitions = Prohibitions(prohibitionsType.map { prohibitionType => ProhibitionValue(prohibitionType.value, prohibitionPeriod, Set())})
//      val prohibitionsResult = service.createValue(trafficSign)
//      withClue("trafficSign sign " + sign) {
//        prohibitions should be (prohibitionsResult)
//      }
//    }
  }

//  test("test change with geometry change with old segments") {
  ////    //    val additionalPanel = Seq(AdditionalPanel(ValidMonFri.OTHvalue, "9-10","", 1), AdditionalPanel(ValidSat.OTHvalue, "(11-12)","", 2), AdditionalPanel(ValidMultiplePeriod.OTHvalue, "15-16 (17-18) 19-20","", 3), AdditionalPanel(ValidMultiplePeriod.OTHvalue, "17-18","", 4))
  ////    //    val prohibitionPeriod = Set(ValidityPeriod(9, 10, ValidityPeriodDayOfWeek.Weekday), ValidityPeriod(11, 12, ValidityPeriodDayOfWeek.Saturday), ValidityPeriod(17, 18, ValidityPeriodDayOfWeek.Sunday)
  ////    //      ,ValidityPeriod(15, 16, ValidityPeriodDayOfWeek.Weekday) ,ValidityPeriod(17, 18, ValidityPeriodDayOfWeek.Saturday) ,ValidityPeriod(19, 20, ValidityPeriodDayOfWeek.Sunday))
  ////    //    relationSignProhibition.foreach { case (sign, prohibitionsType) =>
  ////    //      val simpleProp = Seq(TrafficSignProperty(0, "trafficSigns_type", "", false, Seq(TextPropertyValue(sign.OTHvalue.toString))) , TrafficSignProperty(0, "additional_panel", "", false, additionalPanel))
  ////
  ////  }

  test("test delete with old segments") {
    runWithRollback {
      val value = Prohibitions(Seq(ProhibitionValue(2, Set(), Set())))
      val existingAsset = PersistedLinearAsset(1, 1005l, 1, Some(value), 0, 10, None, None, None, None, false, 30, 0, None, LinkGeomSource.NormalLinkInterface, None, None, None)
      when(mockRoadLinkService.getRoadLinksAndComplementaryByRoadNameFromVVH(any[String], any[Set[String]], any[Boolean])).thenReturn(Seq(roadLinkNameB1, roadLinkNameB2, roadLinkNameB3))
      when(mockProhibitionService.getPersistedAssetsByLinkIds(any[Int], any[Seq[Long]], any[Boolean])).thenReturn(Seq(existingAsset))
      when(mockRoadLinkService.getAdjacentTemp(1005)).thenReturn(Seq(roadLinkNameB2, roadLinkNameA))
      when(mockRoadLinkService.getAdjacentTemp(1010)).thenReturn(Seq(roadLinkNameB1, roadLinkNameB3))
      when(mockRoadLinkService.getAdjacentTemp(1010)).thenReturn(Seq(roadLinkNameB2, roadLinkNameC))

      val roadLinks = Seq(roadLinkNameB1)
      val result = prohibitionGenerator.iterativeProcess(roadLinks, Seq())

    }


    //    val additionalPanel = Seq(AdditionalPanel(ValidMonFri.OTHvalue, "9-10","", 1), AdditionalPanel(ValidSat.OTHvalue, "(11-12)","", 2), AdditionalPanel(ValidMultiplePeriod.OTHvalue, "15-16 (17-18) 19-20","", 3), AdditionalPanel(ValidMultiplePeriod.OTHvalue, "17-18","", 4))
    //    val prohibitionPeriod = Set(ValidityPeriod(9, 10, ValidityPeriodDayOfWeek.Weekday), ValidityPeriod(11, 12, ValidityPeriodDayOfWeek.Saturday), ValidityPeriod(17, 18, ValidityPeriodDayOfWeek.Sunday)
    //      ,ValidityPeriod(15, 16, ValidityPeriodDayOfWeek.Weekday) ,ValidityPeriod(17, 18, ValidityPeriodDayOfWeek.Saturday) ,ValidityPeriod(19, 20, ValidityPeriodDayOfWeek.Sunday))
    //    relationSignProhibition.foreach { case (sign, prohibitionsType) =>
    //      val simpleProp = Seq(TrafficSignProperty(0, "trafficSigns_type", "", false, Seq(TextPropertyValue(sign.OTHvalue.toString))) , TrafficSignProperty(0, "additional_panel", "", false, additionalPanel))

  }

  test("test create without old segments") {
    //    val additionalPanel = Seq(AdditionalPanel(ValidMonFri.OTHvalue, "9-10","", 1), AdditionalPanel(ValidSat.OTHvalue, "(11-12)","", 2), AdditionalPanel(ValidMultiplePeriod.OTHvalue, "15-16 (17-18) 19-20","", 3), AdditionalPanel(ValidMultiplePeriod.OTHvalue, "17-18","", 4))
    //    val prohibitionPeriod = Set(ValidityPeriod(9, 10, ValidityPeriodDayOfWeek.Weekday), ValidityPeriod(11, 12, ValidityPeriodDayOfWeek.Saturday), ValidityPeriod(17, 18, ValidityPeriodDayOfWeek.Sunday)
    //      ,ValidityPeriod(15, 16, ValidityPeriodDayOfWeek.Weekday) ,ValidityPeriod(17, 18, ValidityPeriodDayOfWeek.Saturday) ,ValidityPeriod(19, 20, ValidityPeriodDayOfWeek.Sunday))
    //    relationSignProhibition.foreach { case (sign, prohibitionsType) =>
    //      val simpleProp = Seq(TrafficSignProperty(0, "trafficSigns_type", "", false, Seq(TextPropertyValue(sign.OTHvalue.toString))) , TrafficSignProperty(0, "additional_panel", "", false, additionalPanel))

  }

  test("test update without old segments") {
    //    val additionalPanel = Seq(AdditionalPanel(ValidMonFri.OTHvalue, "9-10","", 1), AdditionalPanel(ValidSat.OTHvalue, "(11-12)","", 2), AdditionalPanel(ValidMultiplePeriod.OTHvalue, "15-16 (17-18) 19-20","", 3), AdditionalPanel(ValidMultiplePeriod.OTHvalue, "17-18","", 4))
    //    val prohibitionPeriod = Set(ValidityPeriod(9, 10, ValidityPeriodDayOfWeek.Weekday), ValidityPeriod(11, 12, ValidityPeriodDayOfWeek.Saturday), ValidityPeriod(17, 18, ValidityPeriodDayOfWeek.Sunday)
    //      ,ValidityPeriod(15, 16, ValidityPeriodDayOfWeek.Weekday) ,ValidityPeriod(17, 18, ValidityPeriodDayOfWeek.Saturday) ,ValidityPeriod(19, 20, ValidityPeriodDayOfWeek.Sunday))
    //    relationSignProhibition.foreach { case (sign, prohibitionsType) =>
    //      val simpleProp = Seq(TrafficSignProperty(0, "trafficSigns_type", "", false, Seq(TextPropertyValue(sign.OTHvalue.toString))) , TrafficSignProperty(0, "additional_panel", "", false, additionalPanel))

  }

  test("test change with geometry change without old segments") {
    //    val additionalPanel = Seq(AdditionalPanel(ValidMonFri.OTHvalue, "9-10","", 1), AdditionalPanel(ValidSat.OTHvalue, "(11-12)","", 2), AdditionalPanel(ValidMultiplePeriod.OTHvalue, "15-16 (17-18) 19-20","", 3), AdditionalPanel(ValidMultiplePeriod.OTHvalue, "17-18","", 4))
    //    val prohibitionPeriod = Set(ValidityPeriod(9, 10, ValidityPeriodDayOfWeek.Weekday), ValidityPeriod(11, 12, ValidityPeriodDayOfWeek.Saturday), ValidityPeriod(17, 18, ValidityPeriodDayOfWeek.Sunday)
    //      ,ValidityPeriod(15, 16, ValidityPeriodDayOfWeek.Weekday) ,ValidityPeriod(17, 18, ValidityPeriodDayOfWeek.Saturday) ,ValidityPeriod(19, 20, ValidityPeriodDayOfWeek.Sunday))
    //    relationSignProhibition.foreach { case (sign, prohibitionsType) =>
    //      val simpleProp = Seq(TrafficSignProperty(0, "trafficSigns_type", "", false, Seq(TextPropertyValue(sign.OTHvalue.toString))) , TrafficSignProperty(0, "additional_panel", "", false, additionalPanel))

  }

  test("test delete without old segments") {
    //    val additionalPanel = Seq(AdditionalPanel(ValidMonFri.OTHvalue, "9-10","", 1), AdditionalPanel(ValidSat.OTHvalue, "(11-12)","", 2), AdditionalPanel(ValidMultiplePeriod.OTHvalue, "15-16 (17-18) 19-20","", 3), AdditionalPanel(ValidMultiplePeriod.OTHvalue, "17-18","", 4))
    //    val prohibitionPeriod = Set(ValidityPeriod(9, 10, ValidityPeriodDayOfWeek.Weekday), ValidityPeriod(11, 12, ValidityPeriodDayOfWeek.Saturday), ValidityPeriod(17, 18, ValidityPeriodDayOfWeek.Sunday)
    //      ,ValidityPeriod(15, 16, ValidityPeriodDayOfWeek.Weekday) ,ValidityPeriod(17, 18, ValidityPeriodDayOfWeek.Saturday) ,ValidityPeriod(19, 20, ValidityPeriodDayOfWeek.Sunday))
    //    relationSignProhibition.foreach { case (sign, prohibitionsType) =>
    //      val simpleProp = Seq(TrafficSignProperty(0, "trafficSigns_type", "", false, Seq(TextPropertyValue(sign.OTHvalue.toString))) , TrafficSignProperty(0, "additional_panel", "", false, additionalPanel))

  }

  test("assets are not generated for roads without names"){
    val roadLinkNameB1 = RoadLink(1005, Seq(Point(0.0, 0.0), Point(0.0, 10.0)), 0, Municipality, 6, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
    val roadLinkNameB2 = RoadLink(1010, Seq(Point(20.0, 0.0), Point(25.0, 10.0), Point(0.0, 10.0)), 0, Municipality, 6, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))

    val propertiesA = Seq(TrafficSignProperty(0, "trafficSigns_type", "", false, Seq(TextPropertyValue(NoPowerDrivenVehicles.OTHvalue.toString))))
    val trafficSign = PersistedTrafficSign(1, 1005, 0, 0, 0, false, 0, 235, propertiesA, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)
    val pairedTrafficSign = PersistedTrafficSign(2, 1010, 20, 0, 0, false, 0, 235, propertiesA, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)

    val allRoadLinks = Seq(roadLinkNameB1, roadLinkNameB2)
    when(mockRoadLinkService.getAdjacentTemp(1005)).thenReturn(Seq(roadLinkNameB1))
    when(mockRoadLinkService.getAdjacentTemp(1010)).thenReturn(Seq(roadLinkNameB2))

    val result= prohibitionGenerator.segmentsManager(allRoadLinks, Seq(trafficSign, pairedTrafficSign), Seq()).toSeq.sortBy(_.roadLink.linkId)
    result.size should be (0)
  }
}

