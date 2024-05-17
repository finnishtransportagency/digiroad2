package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset.ProhibitionClass.{Bus => _, _}
import fi.liikennevirasto.digiroad2.dao.pointasset.PersistedTrafficSign
import org.mockito.Mockito._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}
import fi.liikennevirasto.digiroad2.asset.HazmatTransportProhibitionClass.{HazmatProhibitionTypeA, HazmatProhibitionTypeB}
import fi.liikennevirasto.digiroad2.client.RoadLinkClient
import fi.liikennevirasto.digiroad2.service.linearasset.ProhibitionService
import org.mockito.ArgumentMatchers.any

class TrafficSignLinearGeneratorSpec extends FunSuite with Matchers {
  val mockRoadLinkService = MockitoSugar.mock[RoadLinkService]
  val linearAssetDao = new PostGISLinearAssetDao()
  val mockProhibitionService = MockitoSugar.mock[ProhibitionService]

  class TestTrafficSignProhibitionGenerator extends TrafficSignProhibitionGenerator(mockRoadLinkService) {
    override def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)
    override def withDynSession[T](f: => T): T = PostGISDatabase.withDynSession(f)
    override lazy val postGisLinearAssetDao: PostGISLinearAssetDao = linearAssetDao
    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
    override lazy val prohibitionService = mockProhibitionService

    case class createdObjectTest(id: Long, linkId: String, value: Value, sideCode: Int, startMeasure: Double, endMeasure: Double, roadLink: RoadLink)
    var createAssetRelationObject: Seq[(Long, Long)] = Seq()

    override def createAssetRelation(linearAssetId: Long, trafficSignId: Long): Unit = {
      createAssetRelationObject = List.concat(createAssetRelationObject , Seq((linearAssetId, trafficSignId)))
    }

    def getCreateAssetRelationInfo: Seq[(Long, Long)] = createAssetRelationObject

    var createAssetObject: Seq[createdObjectTest] = Seq()

    override def createLinearAsset(newSegment: TrafficSignToLinear, username: String) : Long = {
      val id = if (createAssetObject.nonEmpty) createAssetObject.maxBy(_.id).id + 100 else 100
      createAssetObject = List.concat(createAssetObject , Seq(createdObjectTest(id, newSegment.roadLink.linkId, newSegment.value, newSegment.sideCode.value, newSegment.startMeasure, newSegment.endMeasure, newSegment.roadLink)))
      id
    }

    def getCreatedInfo: Seq[createdObjectTest] = createAssetObject

    var updateAssetObject: Seq[(Long, Value)] = Seq()

    override def updateLinearAsset(oldAssetId: Long, newValue: Value, username: String) : Seq[Long] = {
      updateAssetObject = List.concat(updateAssetObject , Seq((oldAssetId, newValue)))
      Seq(oldAssetId)
    }

    def getUpdatedInfo: Seq[(Long, Value)] = updateAssetObject

    var deleteAssetObject: Seq[Long] = Seq()

    override def deleteLinearAssets(existingSeg: Seq[TrafficSignToLinear]) : Unit = {
      deleteAssetObject = List.concat(deleteAssetObject , existingSeg.flatMap(_.oldAssetId))
    }

    def getDeletedInfo: Seq[Long] = deleteAssetObject
  }

  val (linkIdB1, linkIdB2, linkIdB3, linkIdA, linkIdC) =
    (LinkIdGenerator.generateRandom(), LinkIdGenerator.generateRandom(), LinkIdGenerator.generateRandom(),
      LinkIdGenerator.generateRandom(), LinkIdGenerator.generateRandom())

  val roadLinkNameB1 = RoadLink(linkIdB1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 0, Municipality, 6, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "ROADNAME_FI" -> "Name B"))
  val roadLinkNameB2 = RoadLink(linkIdB2, Seq(Point(10.0, 0.0), Point(20.0, 0.0)), 0, Municipality, 6, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "ROADNAME_FI" -> "Name B"))
  val roadLinkNameB3 = RoadLink(linkIdB3, Seq(Point(20.0, 0.0), Point(40.0, 0.0)), 0, Municipality, 6, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "ROADNAME_FI" -> "Name B"))
  val roadLinkNameA = RoadLink(linkIdA, Seq(Point(0.0, 0.0), Point(0.0, 20.0)), 0, Municipality, 6, TrafficDirection.BothDirections, Motorway, None, None, Map("ROADNAME_FI" -> "Name A"))
  val roadLinkNameC = RoadLink(linkIdC, Seq(Point(40.0, 0.0), Point(0.0, 20.0)), 0,  Municipality, 6, TrafficDirection.BothDirections, Motorway, None, None, Map("ROADNAME_FI" -> "Name C"))

  private def runWithRollback(test: => Unit): Unit = TestTransactions.runWithRollback()(test)

  val prohibitionGenerator = new TestTrafficSignProhibitionGenerator()

  test("generate segments pieces pair sign"){
    val roadLinkNameB1 = RoadLink(linkIdB1, Seq(Point(0.0, 0.0), Point(0.0, 10.0)), 0, Municipality, 6, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "ROADNAME_FI" -> "Name B"))
    val roadLinkNameB2 = RoadLink(linkIdB2, Seq(Point(20.0, 0.0), Point(25.0, 10.0), Point(0.0, 10.0)), 0, Municipality, 6, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "ROADNAME_FI" -> "Name B"))

    val propertiesA = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(NoPowerDrivenVehicles.OTHvalue.toString))))
    val trafficSign = PersistedTrafficSign(1, linkIdB1, 0, 0, 0, false, 0, 235, propertiesA, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)
    val pairedTrafficSign = PersistedTrafficSign(2, linkIdB2, 20, 0, 0, false, 0, 235, propertiesA, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)

    val allRoadLinks = Seq(roadLinkNameB1, roadLinkNameB2)
    when(mockRoadLinkService.getAdjacent(linkIdB1, false)).thenReturn(Seq(roadLinkNameB2))
    when(mockRoadLinkService.getAdjacent(linkIdB2, false)).thenReturn(Seq(roadLinkNameB1))
    when(mockRoadLinkService.getAdjacent(linkIdB1, Seq(Point(0.0, 10.0, 0.0)), false)).thenReturn(Seq(roadLinkNameB2))
    when(mockRoadLinkService.getAdjacent(linkIdB2, Seq(Point(0.0, 10.0, 0.0)), false)).thenReturn(Seq(roadLinkNameB1))

    val result= prohibitionGenerator.segmentsManager(allRoadLinks, Seq(trafficSign, pairedTrafficSign), Seq())
    result.size should be (2)

    val resultB1 = result.find(_.roadLink.linkId == linkIdB1)
    resultB1 should not be None
    resultB1.get.startMeasure should be (0)
    resultB1.get.endMeasure should be (10)
    resultB1.get.sideCode should be (SideCode.BothDirections)

    val resultB2 = result.find(_.roadLink.linkId == linkIdB2)
    resultB2 should not be None
    resultB2.get.startMeasure should be (0)
    resultB2.get.endMeasure should be ("%.3f".formatLocal(java.util.Locale.US, GeometryUtils.geometryLength(Seq(Point(20.0, 0.0), Point(25.0, 10.0), Point(0.0, 10.0)))).toDouble)
    resultB2.get.sideCode should be (SideCode.BothDirections)
  }

  test("generate segments pieces pair and unpair"){
    val propertiesA = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(NoPowerDrivenVehicles.OTHvalue.toString))))
    val propertiesB = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(NoLorriesAndVans.OTHvalue.toString)))) //value 6
    val trafficSign = PersistedTrafficSign(1, linkIdB1, 0, 0, 0, false, 0, 235, propertiesA, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)
    val pairedTrafficSign = PersistedTrafficSign(2, linkIdB3, 30, 0, 10, false, 0, 235, propertiesA, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)
    val unPairedTrafficSign = PersistedTrafficSign(3, linkIdB2, 10, 0, 8, false, 0, 235, propertiesB, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)

    val allRoadLinks = Seq(roadLinkNameB1, roadLinkNameB2, roadLinkNameB3)
    when(mockRoadLinkService.getAdjacent(linkIdB1, Seq(Point(10.0, 0.0, 0.0)), false)).thenReturn(Seq(roadLinkNameB2))
    when(mockRoadLinkService.getAdjacent(linkIdB2, Seq(Point(20.0, 0.0, 0.0)), false)).thenReturn(Seq(roadLinkNameB3))
    when(mockRoadLinkService.getAdjacent(linkIdB3, Seq(Point(40.0, 0.0, 0.0)), false)).thenReturn(Seq(roadLinkNameC))
    when(mockRoadLinkService.getAdjacent(linkIdB3, Seq(Point(20.0, 0.0, 0.0)), false)).thenReturn(Seq(roadLinkNameB2))
    when(mockRoadLinkService.getAdjacent(linkIdB2, Seq(Point(10.0, 0.0, 0.0)), false)).thenReturn(Seq(roadLinkNameB1))
    when(mockRoadLinkService.getAdjacent(linkIdB1, Seq(Point(0.0, 0.0, 0.0)), false)).thenReturn(Seq(roadLinkNameA))
    when(mockRoadLinkService.getAdjacent(linkIdB1, false)).thenReturn(Seq(roadLinkNameA, roadLinkNameB2))
    when(mockRoadLinkService.getAdjacent(linkIdB2, false)).thenReturn(Seq(roadLinkNameB1, roadLinkNameB3))
    when(mockRoadLinkService.getAdjacent(linkIdB3, false)).thenReturn(Seq(roadLinkNameB2, roadLinkNameC))

    val result = prohibitionGenerator.segmentsManager(allRoadLinks, Seq(trafficSign, pairedTrafficSign, unPairedTrafficSign), Seq()).toSeq
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
    val propertiesA = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(NoPowerDrivenVehicles.OTHvalue.toString))))
    val propertiesB = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(NoLorriesAndVans.OTHvalue.toString))))
    val trafficSign1 = PersistedTrafficSign(1, linkIdB1, 0, 0, 0, false, 0, 235, propertiesA, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)
    val trafficSign2 = PersistedTrafficSign(3, linkIdB1, 8, 0, 8, false, 0, 235, propertiesB, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)
    val pairedSign1 = PersistedTrafficSign(2, linkIdB3, 20, 0, 0, false, 0, 235, propertiesA, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)
    val pairedSign2 = PersistedTrafficSign(4, linkIdB3, 30, 0, 10, false, 0, 235, propertiesB, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)

    val allRoadLinks = Seq(roadLinkNameB1, roadLinkNameB2, roadLinkNameB3)
    when(mockRoadLinkService.getAdjacent(linkIdB1, Seq(Point(10.0, 0.0, 0.0)), false)).thenReturn(Seq(roadLinkNameB2))
    when(mockRoadLinkService.getAdjacent(linkIdB2, Seq(Point(20.0, 0.0, 0.0)), false)).thenReturn(Seq(roadLinkNameB3))
    when(mockRoadLinkService.getAdjacent(linkIdB3, Seq(Point(40.0, 0.0, 0.0)), false)).thenReturn(Seq(roadLinkNameC))
    when(mockRoadLinkService.getAdjacent(linkIdB3, Seq(Point(20.0, 0.0, 0.0)), false)).thenReturn(Seq(roadLinkNameB2))
    when(mockRoadLinkService.getAdjacent(linkIdB2, Seq(Point(10.0, 0.0, 0.0)), false)).thenReturn(Seq(roadLinkNameB1))
    when(mockRoadLinkService.getAdjacent(linkIdB1, Seq(Point(0.0, 0.0, 0.0)), false)).thenReturn(Seq(roadLinkNameA))
    when(mockRoadLinkService.getAdjacent(linkIdB1, false)).thenReturn(Seq(roadLinkNameA, roadLinkNameB2))
    when(mockRoadLinkService.getAdjacent(linkIdB2, false)).thenReturn(Seq(roadLinkNameB3))
    when(mockRoadLinkService.getAdjacent(linkIdB3, false)).thenReturn(Seq(roadLinkNameB2, roadLinkNameC))

    val result = prohibitionGenerator.segmentsManager(allRoadLinks, Seq(trafficSign1, trafficSign2, pairedSign1, pairedSign2), Seq()).toSeq
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
    val propertiesA = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(NoPowerDrivenVehicles.OTHvalue.toString))))
    val trafficSign = PersistedTrafficSign(1, linkIdB1, 5, 0, 5, false, 0, 235, propertiesA, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)

    val allRoadLinks = Seq(roadLinkNameB1, roadLinkNameB2)
    when(mockRoadLinkService.getAdjacent(linkIdB1, Seq(Point(10.0, 0.0, 0.0)), false)).thenReturn(Seq(roadLinkNameB2))
    when(mockRoadLinkService.getAdjacent(linkIdB2, Seq(Point(10.0, 0.0, 0.0)), false)).thenReturn(Seq(roadLinkNameB1))
    when(mockRoadLinkService.getAdjacent(linkIdB1, false)).thenReturn(Seq(roadLinkNameB2, roadLinkNameA))
    when(mockRoadLinkService.getAdjacent(linkIdB2, false)).thenReturn(Seq(roadLinkNameB1))

    val result = prohibitionGenerator.segmentsManager(allRoadLinks, Seq(trafficSign), Seq())
    result.size should be (2)
    val resultB1 = result.find(_.roadLink.linkId == linkIdB1).get
    resultB1.startMeasure should be (5)
    resultB1.endMeasure should be (10)
    resultB1.sideCode should be (SideCode.BothDirections)
    resultB1.value.asInstanceOf[Prohibitions].prohibitions.head.typeId should be (2)
    val resultB2 = result.find(_.roadLink.linkId == linkIdB2).get
    resultB2.startMeasure should be (0)
    resultB2.endMeasure should be (10)
    resultB2.sideCode should be (SideCode.BothDirections)
    resultB2.value.asInstanceOf[Prohibitions].prohibitions.head.typeId should be (2)
  }

  test("prohibition ends in the next intersection") {
    val prohibitionGenerator = new TestTrafficSignProhibitionGenerator()
    val propertiesA = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(NoPowerDrivenVehicles.OTHvalue.toString))))
    val trafficSign = PersistedTrafficSign(1, linkIdB1, 5, 0, 5, false, 0, 235, propertiesA, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)
    val roadLinkNameD = RoadLink(linkIdB1, Seq(Point(10.0, 0.0), Point(10.0, 10.0)), 0, Municipality, 6, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "ROADNAME_FI" -> "Name D"))

    val allRoadLinks = Seq(roadLinkNameB1, roadLinkNameB2)
    when(mockRoadLinkService.getAdjacent(linkIdB1, Seq(Point(10.0, 0.0, 0.0)), false)).thenReturn(Seq(roadLinkNameB2, roadLinkNameD))
    when(mockRoadLinkService.getAdjacent(linkIdB1, false)).thenReturn(Seq(roadLinkNameB2, roadLinkNameD))

    val result = prohibitionGenerator.segmentsManager(allRoadLinks, Seq(trafficSign), Seq())
    result.size should be(1)
    val resultB1 = result.find(_.roadLink.linkId == linkIdB1).get
    resultB1.startMeasure should be(5)
    resultB1.endMeasure should be(10)
    resultB1.sideCode should be(SideCode.TowardsDigitizing)
    resultB1.value.asInstanceOf[Prohibitions].prohibitions.head.typeId should be(2)
  }

  test("zero length prohibition is discarded") {
    val prohibitionGenerator = new TestTrafficSignProhibitionGenerator()
    val propertiesA = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(NoPowerDrivenVehicles.OTHvalue.toString))))
    val trafficSign = PersistedTrafficSign(1, linkIdB1, 0, 0, 0, false, 0, 235, propertiesA, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)

    val allRoadLinks = Seq(roadLinkNameB1)
    when(mockRoadLinkService.getAdjacent(linkIdB1, Seq(Point(0.0, 0.0, 0.0)), false)).thenReturn(Nil)

    val result = prohibitionGenerator.segmentsManager(allRoadLinks, Seq(trafficSign), Seq())
    result should be(Set.empty)
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
      val simpleProp = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(sign.OTHvalue.toString))))

      val trafficSign = PersistedTrafficSign(1, linkIdA, 100, 0, 50, false, 0, 235, simpleProp, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)
      val prohibitions = Prohibitions(prohibitionsType.map { prohibitionType => ProhibitionValue(prohibitionType.value, Set(), Set())})
      val prohibitionsResult = prohibitionGenerator.createValue(Seq(trafficSign))
      withClue("trafficSign sign " + sign) {
        Some(prohibitions) should be (prohibitionsResult)
      }
    }
  }

  test("create prohibitions values based on trafficSigns with additional Panels") {
    val additionalPanel = Seq(AdditionalPanel(ValidMonFri.OTHvalue, "", "9-10", 1, "", 99, 99, 99),
                            AdditionalPanel(ValidSat.OTHvalue, "", "(11-12)",2, "", 99, 99, 99),
                            AdditionalPanel(ValidMultiplePeriod.OTHvalue, "", "(17-18)", 3, "", 99, 99, 99),
                            AdditionalPanel(ValidMultiplePeriod.OTHvalue, "", "17-18", 4, "", 99, 99, 99))

    val prohibitionPeriod = Set(ValidityPeriod(9, 10, ValidityPeriodDayOfWeek.Weekday),
                            ValidityPeriod(11, 12, ValidityPeriodDayOfWeek.Saturday),
                            ValidityPeriod(17, 18, ValidityPeriodDayOfWeek.Sunday) )

    relationSignProhibition.foreach { case (sign, prohibitionsType) =>
      val simpleProp = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(sign.OTHvalue.toString))) , Property(0, "additional_panel", "", false, additionalPanel))
      val trafficSign = PersistedTrafficSign(1, linkIdA, 100, 0, 50, false, 0, 235, simpleProp, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)
      val prohibitions = Prohibitions(prohibitionsType.map { prohibitionType => ProhibitionValue(prohibitionType.value, prohibitionPeriod, Set())})

      val prohibitionsResult = prohibitionGenerator.createValue(Seq(trafficSign))
      withClue("trafficSign sign " + sign) {
        Some(prohibitions) should be (prohibitionsResult)
      }
    }
  }

  test("insert values  on trafficSigns with additional Panels") {
    val additionalPanel = Seq(AdditionalPanel(ValidMonFri.OTHvalue, "", "9-10", 1, "", 99, 99, 99),
                          AdditionalPanel(ValidSat.OTHvalue, "", "(11-12)", 2, "", 99, 99, 99),
                          AdditionalPanel(ValidMultiplePeriod.OTHvalue, "", "(17-18)", 3, "", 99, 99, 99),
                          AdditionalPanel(ValidMultiplePeriod.OTHvalue, "", "17-18", 4, "", 99, 99, 99))

    val prohibitionPeriod = Set(ValidityPeriod(9, 10, ValidityPeriodDayOfWeek.Weekday),
                            ValidityPeriod(11, 12, ValidityPeriodDayOfWeek.Saturday),
                            ValidityPeriod(17, 18, ValidityPeriodDayOfWeek.Sunday) )

    relationSignProhibition.foreach { case (sign, prohibitionsType) =>
      val simpleProp = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(sign.OTHvalue.toString))) , Property(0, "additional_panel", "", false, additionalPanel))
      val trafficSign = PersistedTrafficSign(1, linkIdA, 100, 0, 50, false, 0, 235, simpleProp, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)
      val prohibitions = Prohibitions(prohibitionsType.map { prohibitionType => ProhibitionValue(prohibitionType.value, prohibitionPeriod, Set())})

      val prohibitionsResult = prohibitionGenerator.createValue(Seq(trafficSign))
      withClue("trafficSign sign " + sign) {
        Some(prohibitions) should be (prohibitionsResult)
      }
    }
  }

  test("test value change old segments") {
    //when a linear asset already exist and a trafficSign was added with different value
    val existingValue = Seq(ProhibitionValue(3, Set(), Set()))
    val existingSegments = Seq(TrafficSignToLinear(roadLinkNameB1, Prohibitions(existingValue), SideCode.BothDirections, 0, 10, Set(), Some(100)))
    val value = Seq(ProhibitionValue(23, Set(), Set()))
    val segments = Set(TrafficSignToLinear(roadLinkNameB1, Prohibitions(value ++ existingValue), SideCode.BothDirections, 0, 10, Set(1), Some(100)))

    prohibitionGenerator.applyChangesBySegments(segments, existingSegments)
    val updatedInfo = prohibitionGenerator.getUpdatedInfo
    updatedInfo.size should be(1)
    updatedInfo.head._1 should be (100)
    updatedInfo.head._2 should be(Prohibitions(value ++ existingValue))

    val relationInfo = prohibitionGenerator.getCreateAssetRelationInfo
    relationInfo.head._1 should be(100)
    relationInfo.head._2 should be(1)
    prohibitionGenerator.getCreatedInfo.isEmpty should be (true)
    prohibitionGenerator.getDeletedInfo.isEmpty should be (true)
  }

  test("test relation change old segments") {
    val prohibitionGenerator = new TestTrafficSignProhibitionGenerator()
    //when a linear asset already exist and trafficSign was added with exactly same value
    val value = Prohibitions(Seq(ProhibitionValue(3, Set(), Set())))
    val existingSegments = Seq(TrafficSignToLinear(roadLinkNameB1, value, SideCode.BothDirections, 0, 10, Set(), Some(100)))
    val segments = Set(TrafficSignToLinear(roadLinkNameB1, value, SideCode.BothDirections, 0, 10, Set(1), Some(100)))

    prohibitionGenerator.applyChangesBySegments(segments, existingSegments)

    val relationInfo = prohibitionGenerator.getCreateAssetRelationInfo.sortBy(_._1)
    relationInfo.size should be(1)
    relationInfo.head._1 should be(100)
    relationInfo.head._2 should be(1)

    prohibitionGenerator.getUpdatedInfo.isEmpty should be (true)
    prohibitionGenerator.getCreatedInfo.isEmpty should be (true)
    prohibitionGenerator.getDeletedInfo.isEmpty should be (true)
  }

  test("test new traffic sign measures doesn't match with old segments (shortness)") {
    val prohibitionGenerator = new TestTrafficSignProhibitionGenerator()
    val existingValue = Seq(ProhibitionValue(3, Set(), Set()))
    val existingSegments = Seq(TrafficSignToLinear(roadLinkNameB1, Prohibitions(existingValue), SideCode.BothDirections, 0, 10, Set(), Some(500)))

    val newValue = Seq(ProhibitionValue(23, Set(), Set()))
    val segments = Set(TrafficSignToLinear(roadLinkNameB1, Prohibitions(newValue ++ existingValue), SideCode.BothDirections, 0, 5, Set(1), Some(500)),
      TrafficSignToLinear(roadLinkNameB2, Prohibitions(existingValue), SideCode.BothDirections, 5, 10, Set(1), Some(500)))

    prohibitionGenerator.applyChangesBySegments(segments, existingSegments)
    val createdInfo = prohibitionGenerator.getCreatedInfo
    createdInfo.size should be (2)
    createdInfo.exists(_.id == 100) should be (true)
    createdInfo.exists(_.id == 200) should be (true)
    val sorted = createdInfo.sortBy(_.startMeasure)
    sorted.head.startMeasure should be (0)
    sorted.head.endMeasure should be (5)
    sorted.last.startMeasure should be (5)
    sorted.last.endMeasure should be (10)

    val deletedInfo = prohibitionGenerator.getDeletedInfo
    deletedInfo.size should be (1)
    deletedInfo.head should be (500)

    val relationInfo = prohibitionGenerator.getCreateAssetRelationInfo.sortBy(_._1)
    relationInfo.size should be(2)
    relationInfo.head._1 should be(100)
    relationInfo.head._2 should be(1)
    relationInfo.last._1 should be(200)
    relationInfo.last._2 should be(1)

    prohibitionGenerator.getUpdatedInfo.isEmpty should be (true)
  }

  test("test new traffic sign measures doesn't match with old segments (extends)") {
    val prohibitionGenerator = new TestTrafficSignProhibitionGenerator()
    val existingValue = Seq(ProhibitionValue(3, Set(), Set()))
    val existingSegments = Seq(TrafficSignToLinear(roadLinkNameB1, Prohibitions(existingValue), SideCode.BothDirections, 5, 10, Set(), Some(500)))

    val newValue = Seq(ProhibitionValue(23, Set(), Set()))
    val segments = Set(TrafficSignToLinear(roadLinkNameB1, Prohibitions(newValue ++ existingValue), SideCode.BothDirections, 0, 10, Set(1), Some(500)))

    prohibitionGenerator.applyChangesBySegments(segments, existingSegments)
    val createdInfo = prohibitionGenerator.getCreatedInfo
    createdInfo.size should be (1)
    createdInfo.head.startMeasure should be (0)
    createdInfo.head.endMeasure should be (10)

    val deletedInfo = prohibitionGenerator.getDeletedInfo
    deletedInfo.size should be (1)
    deletedInfo.head should be (500)

    val relationInfo = prohibitionGenerator.getCreateAssetRelationInfo.sortBy(_._1)
    relationInfo.size should be(1)
    relationInfo.head._1 should be(100)
    relationInfo.head._2 should be(1)

    prohibitionGenerator.getUpdatedInfo.isEmpty should be (true)
  }

  test("test create without old segments") {
    val prohibitionGenerator = new TestTrafficSignProhibitionGenerator()
    val value = Seq(ProhibitionValue(23, Set(), Set()))
    val segments = Set(TrafficSignToLinear(roadLinkNameB1, Prohibitions(value), SideCode.BothDirections, 0, 10, Set(1), Some(500)))

    prohibitionGenerator.applyChangesBySegments(segments, Seq())
    val createdInfo = prohibitionGenerator.getCreatedInfo
    createdInfo.size should be (1)
    createdInfo.head.startMeasure should be (0)
    createdInfo.head.endMeasure should be (10)

    val relationInfo = prohibitionGenerator.getCreateAssetRelationInfo.sortBy(_._1)
    relationInfo.size should be(1)
    relationInfo.head._1 should be(100)
    relationInfo.head._2 should be(1)

    prohibitionGenerator.getUpdatedInfo.isEmpty should be (true)
    prohibitionGenerator.getDeletedInfo.isEmpty should be (true)
  }

  class TestTrafficSignHazmatTransportProhibitionGenerator extends TrafficSignHazmatTransportProhibitionGenerator(mockRoadLinkService) {
    override def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)
    override def withDynSession[T](f: => T): T = PostGISDatabase.withDynSession(f)
    override lazy val postGisLinearAssetDao: PostGISLinearAssetDao = linearAssetDao
    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
  }

  val hazmatTransportProhibitionGenerator = new TestTrafficSignHazmatTransportProhibitionGenerator()

  test("create  hazmat Transport Prohibition values based on trafficSigns"){
    val signPropA = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(NoVehiclesWithDangerGoods.OTHvalue.toString))),
      Property(1, "additional_panel", "", false, Seq(AdditionalPanel(HazmatProhibitionA.OTHvalue, "", "", 1, "", 99, 99, 99))))
    val trafficSignA = PersistedTrafficSign(1, linkIdA, 100, 0, 50, false, 0, 235, signPropA, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)
    val hazmatValueA = Prohibitions(Seq(ProhibitionValue(HazmatProhibitionTypeA.value, Set(), Set())))
    val hazmatResultA = hazmatTransportProhibitionGenerator.createValue(Seq(trafficSignA))
    hazmatResultA should be (Some(hazmatValueA))

    val signPropB = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(NoVehiclesWithDangerGoods.OTHvalue.toString))),
      Property(1, "additional_panel", "", false, Seq(AdditionalPanel(HazmatProhibitionB.OTHvalue, "", "", 1, "", 99, 99, 99))))
    val trafficSignB = PersistedTrafficSign(1, linkIdA, 100, 0, 50, false, 0, 235, signPropB, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)
    val hazmatValueB = Prohibitions(Seq(ProhibitionValue(HazmatProhibitionTypeB.value, Set(), Set())))
    val hazmatResultB = hazmatTransportProhibitionGenerator.createValue(Seq(trafficSignB))
    hazmatResultB should be (Some(hazmatValueB))

    val signProp = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(NoVehiclesWithDangerGoods.OTHvalue.toString))))
    val trafficSign = PersistedTrafficSign(1, linkIdA, 100, 0, 50, false, 0, 235, signProp, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)
    hazmatTransportProhibitionGenerator.createValue(Seq(trafficSign)).isEmpty should be (true)
  }

  class TestTrafficSignParkingProhibitionGenerator extends TrafficSignParkingProhibitionGenerator(mockRoadLinkService) {
    override def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)

    override def withDynSession[T](f: => T): T = PostGISDatabase.withDynSession(f)

    override lazy val postGisLinearAssetDao: PostGISLinearAssetDao = linearAssetDao
    override lazy val roadLinkService: RoadLinkService = mockRoadLinkService
  }

  test("parking generate segments additional panel DistanceFromSignToPointWhichSignApplies") {
    val parkingProhibitionGenerator = new TestTrafficSignParkingProhibitionGenerator()
    val signProperty = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(StandingAndParkingProhibited.OTHvalue.toString))),
      Property(1, "additional_panel", "", false, Seq(AdditionalPanel(DistanceWhichSignApplies.OTHvalue, "", "15", 1, "", 99, 99, 99))))

    val trafficSign = PersistedTrafficSign(1, linkIdB1, 0, 0, 0, false, 0, 235, signProperty, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)

    val allRoadLinks = Seq(roadLinkNameB1, roadLinkNameB2, roadLinkNameB3)
    when(mockRoadLinkService.getAdjacent(linkIdB1, false)).thenReturn(Seq(roadLinkNameB2))
    when(mockRoadLinkService.getAdjacent(linkIdB2, false)).thenReturn(Seq(roadLinkNameB1, roadLinkNameB3))
    when(mockRoadLinkService.getAdjacent(linkIdB3, false)).thenReturn(Seq(roadLinkNameB2))
    when(mockRoadLinkService.getAdjacent(linkIdB1, Seq(Point(10, 0)))).thenReturn(Seq(roadLinkNameB2))

    val result = parkingProhibitionGenerator.segmentsManager(allRoadLinks, Seq(trafficSign), Seq()).toSeq
    result.size should be(2)

    val resultB1 = result.find(_.roadLink.linkId == linkIdB1)
    resultB1 should not be None
    resultB1.get.startMeasure should be(0)
    resultB1.get.endMeasure should be(10)
    resultB1.get.sideCode should be(SideCode.BothDirections)

    val resultB2 = result.find(_.roadLink.linkId == linkIdB2)
    resultB2 should not be None
    resultB2.get.startMeasure should be(0)
    resultB2.get.endMeasure should be(5)
    resultB2.get.sideCode should be(SideCode.BothDirections)
  }

  test("parking generate segments additional panel RegulationEndsToTheSign") {
    val parkingProhibitionGenerator = new TestTrafficSignParkingProhibitionGenerator()
    val signProperty = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(StandingAndParkingProhibited.OTHvalue.toString))))
    val trafficSign = PersistedTrafficSign(1, linkIdB1, 0, 0, 0, false, 0, 235, signProperty, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)

    val endSignProperty = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(StandingAndParkingProhibited.OTHvalue.toString))),
      Property(1, "additional_panel", "", false, Seq(AdditionalPanel(RegulationEndsToTheSign.OTHvalue, "", "", 1, "", 99, 99, 99))))
    val endTrafficSign = PersistedTrafficSign(2, linkIdB2, 0, 7, 7, false, 0, 235, endSignProperty, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)

    val allRoadLinks = Seq(roadLinkNameB1, roadLinkNameB2, roadLinkNameB3)
    when(mockRoadLinkService.getAdjacent(linkIdB1, false)).thenReturn(Seq(roadLinkNameB2))
    when(mockRoadLinkService.getAdjacent(linkIdB2, false)).thenReturn(Seq(roadLinkNameB1, roadLinkNameB3))
    when(mockRoadLinkService.getAdjacent(linkIdB3, false)).thenReturn(Seq(roadLinkNameB2))
    when(mockRoadLinkService.getAdjacent(linkIdB1, Seq(Point(10, 0)))).thenReturn(Seq(roadLinkNameB2))

    val result = parkingProhibitionGenerator.segmentsManager(allRoadLinks, Seq(trafficSign, endTrafficSign), Seq()).toSeq
    result.size should be(2)

    val resultB1 = result.find(_.roadLink.linkId == linkIdB1)
    resultB1 should not be None
    resultB1.get.startMeasure should be(0)
    resultB1.get.endMeasure should be(10)
    resultB1.get.sideCode should be(SideCode.BothDirections)

    val resultB2 = result.find(_.roadLink.linkId == linkIdB2)
    resultB2 should not be None
    resultB2.get.startMeasure should be(0)
    resultB2.get.endMeasure should be(7)
    resultB2.get.sideCode should be(SideCode.BothDirections)
  }

  test("parking generate segments until next intersection link") {
    val linkIdB31 = LinkIdGenerator.generateRandom()
    val roadLinkNameB31 = RoadLink(linkIdB31, Seq(Point(20.0, 0.0), Point(40.0, 20.0)), 0, Municipality, 6, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "ROADNAME_FI" -> "Name B"))

    val parkingProhibitionGenerator = new TestTrafficSignParkingProhibitionGenerator()
    val signProperty = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(StandingAndParkingProhibited.OTHvalue.toString))))
    val trafficSign = PersistedTrafficSign(1, linkIdB1, 0, 0, 0, false, 0, 235, signProperty, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)

    val allRoadLinks = Seq(roadLinkNameB1, roadLinkNameB2, roadLinkNameB3)
    when(mockRoadLinkService.getAdjacent(linkIdB1, false)).thenReturn(Seq(roadLinkNameB2))
    when(mockRoadLinkService.getAdjacent(linkIdB2, false)).thenReturn(Seq(roadLinkNameB1, roadLinkNameB3))
    when(mockRoadLinkService.getAdjacent(linkIdB3, false)).thenReturn(Seq(roadLinkNameB2))
    when(mockRoadLinkService.getAdjacent(linkIdB1, Seq(Point(10, 0)))).thenReturn(Seq(roadLinkNameB2))
    when(mockRoadLinkService.getAdjacent(linkIdB2, Seq(Point(20, 0)))).thenReturn(Seq(roadLinkNameB3, roadLinkNameB31))

    val result = parkingProhibitionGenerator.segmentsManager(allRoadLinks, Seq(trafficSign), Seq()).toSeq
    result.size should be(2)

    val resultB1 = result.find(_.roadLink.linkId == linkIdB1)
    resultB1 should not be None
    resultB1.get.startMeasure should be(0)
    resultB1.get.endMeasure should be(10)
    resultB1.get.sideCode should be(SideCode.BothDirections)

    val resultB2 = result.find(_.roadLink.linkId == linkIdB2)
    resultB2 should not be None
    resultB2.get.startMeasure should be(0)
    resultB2.get.endMeasure should be(10)
    resultB2.get.sideCode should be(SideCode.BothDirections)
  }

  test("generate segments standing and parking in same linkId") {
    val prohibitionGenerator = new TestTrafficSignParkingProhibitionGenerator()

    val propertiesA = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(StandingAndParkingProhibited.OTHvalue.toString))))

    val propertiesB = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(ParkingProhibited.OTHvalue.toString))))


    val trafficSignA1 = PersistedTrafficSign(1, linkIdB1, 0, 0, 0, false, 0, 235, propertiesA, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)
    val trafficSignB1 = PersistedTrafficSign(3, linkIdB1, 5, 0, 5, false, 0, 235, propertiesB, None, None, None, None, SideCode.TowardsDigitizing.value, None, NormalLinkInterface)

    val allRoadLinks = Seq(roadLinkNameB1, roadLinkNameB2, roadLinkNameB3)
    when(mockRoadLinkService.getAdjacent(linkIdB1, false)).thenReturn(Seq(roadLinkNameB2, roadLinkNameA))
    when(mockRoadLinkService.getAdjacent(linkIdB2, false)).thenReturn(Seq(roadLinkNameB1, roadLinkNameB3))
    when(mockRoadLinkService.getAdjacent(linkIdB3, false)).thenReturn(Seq(roadLinkNameB2))
    when(mockRoadLinkService.getAdjacent(any[String], any[Seq[Point]], any[Boolean])).thenReturn(Seq())

    val result = prohibitionGenerator.segmentsManager(allRoadLinks, Seq(trafficSignA1, trafficSignB1), Seq()).toSeq
    result.size should be(4)

    val resultB1 = result.filter(_.roadLink.linkId == linkIdB1).sortBy(_.startMeasure)
    resultB1.size should be(2)
    resultB1.head.startMeasure should be(0)
    resultB1.head.endMeasure should be(5)
    resultB1.head.sideCode should be(SideCode.TowardsDigitizing)
    resultB1.last.startMeasure should be(5)
    resultB1.last.endMeasure should be(10)
    resultB1.last.sideCode should be(SideCode.TowardsDigitizing)

    val resultB2 = result.find(_.roadLink.linkId == linkIdB2)
    resultB2 should not be None
    resultB2.get.startMeasure should be(0)
    resultB2.get.endMeasure should be(10)
    resultB2.get.sideCode should be(SideCode.BothDirections)

    val resultB3 = result.find(_.roadLink.linkId == linkIdB3)
    resultB3 should not be None
    resultB3.get.startMeasure should be(0)
    resultB3.get.endMeasure should be(20)
    resultB3.get.sideCode should be(SideCode.BothDirections)
  }

  test("generate parking prohibitions against digitizing starting from an intermediate location on the first link and ending to intersection") {
    val linkIdB11 = LinkIdGenerator.generateRandom()
    val roadLinkNameB11 = RoadLink(linkIdB11, Seq(Point(10.0, 0.0), Point(10.0, 20.0)), 0, Municipality, 6, TrafficDirection.BothDirections, Motorway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235), "ROADNAME_FI" -> "Name B"))

    val parkingProhibitionGenerator = new TestTrafficSignParkingProhibitionGenerator()
    val signProperty = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(StandingAndParkingProhibited.OTHvalue.toString))))
    val trafficSign = PersistedTrafficSign(1, linkIdB3, 40, 20, 5, false, 0, 235, signProperty, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)

    val allRoadLinks = Seq(roadLinkNameB1, roadLinkNameB2, roadLinkNameB3)
    when(mockRoadLinkService.getAdjacent(linkIdB3, Seq(Point(20, 0)))).thenReturn(Seq(roadLinkNameB2))
    when(mockRoadLinkService.getAdjacent(linkIdB2, Seq(Point(10, 0)))).thenReturn(Seq(roadLinkNameB1, roadLinkNameB11))

    val result = parkingProhibitionGenerator.segmentsManager(allRoadLinks, Seq(trafficSign), Seq()).toSeq
    result.size should be(2)

    val resultB3 = result.find(_.roadLink.linkId == linkIdB3)
    resultB3 should not be None
    resultB3.get.startMeasure should be(0)
    resultB3.get.endMeasure should be(5)
    resultB3.get.sideCode should be(SideCode.AgainstDigitizing)

    val resultB2 = result.find(_.roadLink.linkId == linkIdB2)
    resultB2 should not be None
    resultB2.get.startMeasure should be(0)
    resultB2.get.endMeasure should be(10)
    resultB2.get.sideCode should be(SideCode.AgainstDigitizing)
  }

  test("Create a MotorVehicle prohibition value with an EmergencyVehicle exception") {
    val additionalPanel = Seq(AdditionalPanel(AdditionalPanelWithText.OTHvalue, "sepustus", " jatkuu ", 1, " vielä.", 99, 99, 99))
    val simpleProp = Seq(Property(0, "trafficSigns_type", "", false, Seq(PropertyValue(ClosedToAllVehicles.OTHvalue.toString))), Property(0, "additional_panel", "", false, additionalPanel))
    val trafficSign = PersistedTrafficSign(1, linkIdA, 100, 0, 50, false, 0, 235, simpleProp, None, None, None, None, SideCode.AgainstDigitizing.value, None, NormalLinkInterface)
    val prohibitions = Prohibitions(Seq(ProhibitionValue(ProhibitionClass.MotorVehicle.value, Set(), Set(29))))
    val prohibitionsResult = prohibitionGenerator.createValue(Seq(trafficSign))
    withClue("trafficSign sign " + ClosedToAllVehicles) {
        Some(prohibitions) should be(prohibitionsResult)
      }
  }
}