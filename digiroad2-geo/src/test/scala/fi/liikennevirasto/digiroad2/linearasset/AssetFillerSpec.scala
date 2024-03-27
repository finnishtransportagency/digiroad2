package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset.SideCode.BothDirections
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.{ChangeSet, MValueAdjustment, SideCodeAdjustment}
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import org.joda.time.DateTime
import org.scalatest._

import java.util.UUID
import scala.util.Random

class AssetFillerSpec extends FunSuite with Matchers {

  object assetFiller extends AssetFiller {
    override def combine(roadLink: RoadLinkForFillTopology, segments: Seq[PieceWiseLinearAsset], changeSet: ChangeSet): (Seq[PieceWiseLinearAsset], ChangeSet) = {
      super.combine(roadLink,segments,changeSet)
    }
    override def fuse(roadLink: RoadLinkForFillTopology, linearAssets: Seq[PieceWiseLinearAsset], changeSet: ChangeSet): (Seq[PieceWiseLinearAsset], ChangeSet) = {
      super.fuse(roadLink,linearAssets,changeSet)
    }
    override def expireOverlappingSegments(roadLink: RoadLinkForFillTopology, segments: Seq[PieceWiseLinearAsset], changeSet: ChangeSet): (Seq[PieceWiseLinearAsset], ChangeSet) = {
      super.expireOverlappingSegments(roadLink,segments,changeSet)
    }
    override def expireSegmentsOutsideGeometry(roadLink: RoadLinkForFillTopology, assets: Seq[PieceWiseLinearAsset], changeSet: ChangeSet): (Seq[PieceWiseLinearAsset], ChangeSet) = {
      super.expireSegmentsOutsideGeometry(roadLink,assets,changeSet) 
    }
      
    override def capToGeometry(roadLink: RoadLinkForFillTopology, segments: Seq[PieceWiseLinearAsset], changeSet: ChangeSet): (Seq[PieceWiseLinearAsset], ChangeSet) = {
      super.capToGeometry(roadLink,segments,changeSet)
    }
    override def dropShortSegments(roadLink: RoadLinkForFillTopology, assets: Seq[PieceWiseLinearAsset], changeSet: ChangeSet): (Seq[PieceWiseLinearAsset], ChangeSet) = {
      super.dropShortSegments(roadLink,assets,changeSet)
    }
    
    override def adjustAssets(roadLink: RoadLinkForFillTopology, linearAssets: Seq[PieceWiseLinearAsset], changeSet: ChangeSet): (Seq[PieceWiseLinearAsset], ChangeSet) = {
      super.adjustAssets(roadLink,linearAssets,changeSet)
    }
  }


  case class Measure(startMeasure: Double, endMeasure: Double)
  
  def createAsset(id: Long, linkId1: String, measure: Measure, value: Option[Value]) = {
    PieceWiseLinearAsset(id = id, linkId = linkId1, sideCode = SideCode.BothDirections, value = value, geometry = Nil, expired = false, startMeasure =  measure.startMeasure, endMeasure =  measure.endMeasure,
      endpoints = Set(Point(measure.endMeasure,0.0)), modifiedBy = None, modifiedDateTime = None, createdBy = Some("guy"), 
      createdDateTime = Some(DateTime.now()), typeId = 140, trafficDirection = TrafficDirection.BothDirections, timeStamp = 0L, 
      geomModifiedDate = None, linkSource = NormalLinkInterface, administrativeClass = State, attributes = Map(), verifiedBy = None, verifiedDate = None, informationSource = None)
  }
  def createAssetWithAddDay(id: Long, linkId1: String, measure: Measure, value: Option[Value], addDay: Int = 0) = {
    PieceWiseLinearAsset(id = id, linkId = linkId1, sideCode = SideCode.BothDirections, value = value, geometry = Nil, expired = false, startMeasure = measure.startMeasure, endMeasure = measure.endMeasure,
      endpoints = Set(Point(measure.endMeasure, 0.0)), modifiedBy = None, modifiedDateTime = None, createdBy = Some("guy"),
      createdDateTime = Some(DateTime.now().plusDays(addDay)), typeId = 140, trafficDirection = TrafficDirection.BothDirections, timeStamp = 0L,
      geomModifiedDate = None, linkSource = NormalLinkInterface, administrativeClass = State, attributes = Map(), verifiedBy = None, verifiedDate = None, informationSource = None)
  }
  def createAsset(id: Long, linkId1: String, measure: Measure, sideCode: SideCode, value: Option[Value],trafficDirection:TrafficDirection = TrafficDirection.BothDirections, typeId: Int = NumberOfLanes.typeId) = {
    PieceWiseLinearAsset(id = id, linkId = linkId1, sideCode = sideCode, value = value, geometry = Nil, expired = false, startMeasure = measure.startMeasure, endMeasure = measure.endMeasure,
      endpoints = Set(Point(measure.endMeasure,0.0)), modifiedBy = None, modifiedDateTime = None, createdBy = Some("guy"),
      createdDateTime = Some(DateTime.now()), typeId = typeId, trafficDirection = trafficDirection, timeStamp = 0L,
      geomModifiedDate = None, linkSource = NormalLinkInterface, administrativeClass = State, attributes = Map(), verifiedBy = None, verifiedDate = None, informationSource = None)
  }
  
  def createRoadLink(id: String, length: Double) = {
    RoadLink(id, Seq(Point(0.0, 0.0), Point(length, 0.0)), length, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
      TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
  }

  private def toSegment(persistedLinearAsset: PieceWiseLinearAsset) = {
    (persistedLinearAsset.startMeasure, persistedLinearAsset.endMeasure)
  }
  
  case class ChangeInfo(oldId: Option[String],
                        newId: Option[String],
                        mmlId: Long,
                        changeType: Int,
                        oldStartMeasure: Option[Double],
                        oldEndMeasure: Option[Double],
                        newStartMeasure: Option[Double],
                        newEndMeasure: Option[Double],
                        timeStamp: Option[Long])

  private def generateRandomLinkId(): String = s"${UUID.randomUUID()}:${Random.nextInt(100)}"

  val (linkId1, linkId2, linkId3) = (generateRandomLinkId(), generateRandomLinkId(), generateRandomLinkId())

  val initChangeSet = LinearAssetFiller.emptyChangeSet

  test("create non-existent linear assets on empty road links") {
    val topology = Seq(
      RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
        1, TrafficDirection.BothDirections, Motorway, None, None))
    val changeSet = LinearAssetFiller.emptyChangeSet
    val linearAssets = Map.empty[String, Seq[PieceWiseLinearAsset]]
    val filledTopology = assetFiller.generateUnknowns(topology.map(assetFiller.toRoadLinkForFillTopology), linearAssets, 30)._1

    filledTopology should have size 1
    filledTopology.map(_.sideCode) should be(Seq(BothDirections))
    filledTopology.map(_.value) should be(Seq(None))
    filledTopology.map(_.id) should be(Seq(0))
    filledTopology.map(_.linkId) should be(Seq(linkId1))
    filledTopology.map(_.geometry) should be(Seq(Seq(Point(0.0, 0.0), Point(10.0, 0.0))))
  }
  
  test("generate unknown asset when two-way road link is half-covered") {
    val topology = Seq(
      RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
        1, TrafficDirection.BothDirections, Motorway, None, None))

    val assets = assetFiller.toLinearAssetsOnMultipleLinks(Seq(PersistedLinearAsset(1l, linkId1, SideCode.TowardsDigitizing.value, Some(NumericValue(1)),
      0.0, 10.0, None, None, None, None, false, 110, 0, None, linkSource = NormalLinkInterface, None, None, None)),
      topology.map(assetFiller.toRoadLinkForFillTopology))

    val linearAssets = Map(
      linkId1 -> assets)

    val (filledTopology, changeSet) = assetFiller.generateUnknowns(topology.map(assetFiller.toRoadLinkForFillTopology), linearAssets, 110)
    filledTopology should have size 2

    filledTopology.filter(_.id == 1).map(_.sideCode) should be(Seq(SideCode.TowardsDigitizing))
    filledTopology.filter(_.id == 1).map(_.value) should be(Seq(Some(NumericValue(1))))
    filledTopology.filter(_.id == 1).map(_.geometry) should be(Seq(Seq(Point(0.0, 0.0), Point(10.0, 0.0))))

    filledTopology.filter(_.id == 0).map(_.sideCode) should be(Seq(SideCode.AgainstDigitizing))
    filledTopology.filter(_.id == 0).map(_.geometry) should be(Seq(Seq(Point(0.0, 0.0), Point(10.0, 0.0))))

    changeSet.adjustedSideCodes.size should be(0)
  }

  test("expire assets that fall completely outside topology") {
    val roadLink =
      RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
        1, TrafficDirection.BothDirections, Motorway, None, None)
    val assets = Map(
      linkId1 -> Seq(PieceWiseLinearAsset(1l, linkId1, SideCode.BothDirections, Some(NumericValue(1)), Seq(Point(10.0, 0.0), Point(15.0, 0.0)), false, 10.0, 15.0,
        Set(Point(10.0, 0.0), Point(15.0, 0.0)), None, None, None, None, 110, TrafficDirection.BothDirections, 0, None, linkSource = NormalLinkInterface, Municipality, Map(), None, None, None)))

    val (methodTest, methodTestChangeSet) = assetFiller.expireSegmentsOutsideGeometry(assetFiller.toRoadLinkForFillTopology(roadLink), assets.head._2, initChangeSet)
   
    methodTestChangeSet.expiredAssetIds should be(Set(1l))
    methodTestChangeSet.droppedAssetIds should be(Set())

  }

  test("cap assets that go over roadlink geometry") {
    val roadLink = RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality,
      1, TrafficDirection.BothDirections, Motorway, None, None)
    val assets = Map(
      linkId1 -> Seq(PieceWiseLinearAsset(1l, linkId1, SideCode.BothDirections, Some(NumericValue(1)), Seq(Point(0.0, 0.0), Point(15.0, 0.0)), false, 0.0, 15.0,
        Set(Point(0.0, 0.0), Point(15.0, 0.0)), None, None, None, None, 110, TrafficDirection.BothDirections, 0, None, linkSource = NormalLinkInterface, Municipality, Map(), None, None, None)))

    val (methodTest, methodTestChangeSet) = assetFiller.capToGeometry(assetFiller.toRoadLinkForFillTopology(roadLink), assets.head._2, initChangeSet)

    val filledTopology = methodTest
    val changeSet = methodTestChangeSet
    filledTopology should have size 1
    filledTopology.map(_.sideCode) should be(Seq(BothDirections))
    filledTopology.map(_.value) should be(Seq(Some(NumericValue(1))))
    filledTopology.map(_.id) should be(Seq(1))
    filledTopology.map(_.linkId) should be(Seq(linkId1))
    filledTopology.map(_.geometry) should be(Seq(Seq(Point(0.0, 0.0), Point(10.0, 0.0))))

    changeSet.droppedAssetIds should be(Set())
    changeSet.expiredAssetIds should be(Set())
    changeSet.adjustedMValues should be(Seq(MValueAdjustment(1l, linkId1, 0.0, 10.0)))
    
  }
  
  test("drop segments less than 2 meters"){
    val roadLink = RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
      TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    val assets = assetFiller.toLinearAsset(Seq(
      PersistedLinearAsset(1, linkId1, SideCode.BothDirections.value, Some(NumericValue(2)), 0.0, 1.9, Some("guy"),
        Some(DateTime.now()), None, None, expired = false, 140, 0, None, linkSource = NormalLinkInterface, None, None, None),
      PersistedLinearAsset(2, linkId1, SideCode.BothDirections.value, Some(NumericValue(2)), 8.0, 10.0, Some("guy"),
        Some(DateTime.now()), None, None, expired = false, 140, 0, None, linkSource = NormalLinkInterface, None, None, None)
    ), assetFiller.toRoadLinkForFillTopology(roadLink))

    val (methodTest, methodTestChangeSet) = assetFiller.dropShortSegments(assetFiller.toRoadLinkForFillTopology(roadLink), assets, initChangeSet)
    val (testWholeProcess, changeSet) = assetFiller.fillTopology(Seq(roadLink).map(assetFiller.toRoadLinkForFillTopology), Map(linkId1 -> assets), 140)
    
    methodTestChangeSet.expiredAssetIds should have size 1
    methodTestChangeSet.expiredAssetIds.head should be(1)

    changeSet.expiredAssetIds should have size 1
    changeSet.expiredAssetIds.head should be(1)
  }

  test("Don't drop segments less than 2 meters on a road link with length less that 2 meters"){
    val roadLink = RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(1.9, 0.0)), 1.9, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
      TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    val assets = assetFiller.toLinearAsset(Seq(
      PersistedLinearAsset(1, linkId1, SideCode.BothDirections.value, Some(NumericValue(2)), 0.0, 1.9, Some("guy"),
        Some(DateTime.now()), None, None, expired = false, 140, 0, None, linkSource = NormalLinkInterface, None, None, None)
    ), assetFiller.toRoadLinkForFillTopology(roadLink))

    val (methodTest, methodTestChangeSet) = assetFiller.dropShortSegments(assetFiller.toRoadLinkForFillTopology(roadLink), assets, initChangeSet)
    val (testWholeProcess, changeSet) = assetFiller.fillTopology(Seq(roadLink).map(assetFiller.toRoadLinkForFillTopology), Map(linkId1 -> assets), 140)

    methodTest should have size 1
    methodTest.map(_.id) should be(Seq(1))
    methodTestChangeSet.droppedAssetIds should have size 0

    testWholeProcess should have size 1
    testWholeProcess.map(_.id) should be(Seq(1))
    changeSet.droppedAssetIds should have size 0
  }
  
  test("adjust two overlapping segments") {
    val roadLink = createRoadLink(linkId1,10)
    
    val assets = Seq(
      createAssetWithAddDay(1,linkId1,Measure(0.0,4.0),Some(NumericValue(1)),addDay = 1),
      createAssetWithAddDay(2,linkId1,Measure(3.0,5.0),Some(NumericValue(3)),addDay = 2)
    )

    val (filledTopology, change) = assetFiller.expireOverlappingSegments(assetFiller.toRoadLinkForFillTopology(roadLink), assets, initChangeSet)
    
    filledTopology should have size 2
    GeometryUtils.overlap(toSegment(filledTopology.head), toSegment(filledTopology.last)).nonEmpty should be(false)
    
  }

  test("Adjust segments and drop wrong direction of asset and retain correct side code") {
    val roadLink = RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
      TrafficDirection.TowardsDigitizing, LinkType.apply(3), None, None, Map())
    val assets = assetFiller.toLinearAsset(Seq(
      PersistedLinearAsset(1, linkId1, SideCode.TowardsDigitizing.value, Some(NumericValue(2)), 0.0, 10, Some("guy"),
        Some(DateTime.now()), None, None, expired = false, 140, 0, None, linkSource = NormalLinkInterface, None, None, None),
      PersistedLinearAsset(2, linkId1, SideCode.AgainstDigitizing.value, Some(NumericValue(3)), 0.0, 10.0, Some("guy"),
        Some(DateTime.now()), None, None, expired = false, 140, 0, None, linkSource = NormalLinkInterface, None, None, None))
      , assetFiller.toRoadLinkForFillTopology(roadLink))

    val (methodTest1, methodTestChangeSet1) =  assetFiller.droppedSegmentWrongDirection(assetFiller.toRoadLinkForFillTopology(roadLink),assets,initChangeSet)
    val (methodTest, methodTestChangeSet) = assetFiller.adjustSegmentSideCodes(assetFiller.toRoadLinkForFillTopology(roadLink), methodTest1, methodTestChangeSet1)

    methodTest should have size 1
    methodTest.map(_.sideCode) should be(Seq(SideCode.TowardsDigitizing))
    methodTest.map(_.value) should be(Seq(Some(NumericValue(2))))
    methodTestChangeSet.droppedAssetIds should have size 1
    methodTestChangeSet.droppedAssetIds.head should be(2)

  }

  test("Adjust segments and drop wrong direction of asset and retain correct side versio 2") {
    val roadLink = RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
      TrafficDirection.AgainstDigitizing, LinkType.apply(3), None, None, Map())
    val assets = assetFiller.toLinearAsset(Seq(
      PersistedLinearAsset(1, linkId1, SideCode.TowardsDigitizing.value, Some(NumericValue(2)), 0.0, 10, Some("guy"),
        Some(DateTime.now()), None, None, expired = false, 140, 0, None, linkSource = NormalLinkInterface, None, None, None),
      PersistedLinearAsset(2, linkId1, SideCode.AgainstDigitizing.value, Some(NumericValue(3)), 0.0, 10.0, Some("guy"),
        Some(DateTime.now()), None, None, expired = false, 140, 0, None, linkSource = NormalLinkInterface, None, None, None))
      , assetFiller.toRoadLinkForFillTopology(roadLink))

    val (methodTest1, methodTestChangeSet1) = assetFiller.droppedSegmentWrongDirection(assetFiller.toRoadLinkForFillTopology(roadLink), assets, initChangeSet)
    val (methodTest, methodTestChangeSet) = assetFiller.adjustSegmentSideCodes(assetFiller.toRoadLinkForFillTopology(roadLink), methodTest1, methodTestChangeSet1)
    
    methodTest should have size 1
    methodTest.map(_.sideCode) should be(Seq(SideCode.AgainstDigitizing))
    methodTest.map(_.value) should be(Seq(Some(NumericValue(3))))
    methodTestChangeSet.droppedAssetIds should have size 1
    methodTestChangeSet.droppedAssetIds.head should be(1)

  }

  test("Adjust segments and drop wrong direction of asset and retain correct side versio 3") {
    val roadLink = RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
      TrafficDirection.AgainstDigitizing, LinkType.apply(3), None, None, Map())
    val assets = assetFiller.toLinearAsset(Seq(
      PersistedLinearAsset(1, linkId1, SideCode.TowardsDigitizing.value, Some(NumericValue(2)), 0.0, 4.5, Some("guy"),
        Some(DateTime.now()), None, None, expired = false, 140, 0, None, linkSource = NormalLinkInterface, None, None, None),
      PersistedLinearAsset(2, linkId1, SideCode.AgainstDigitizing.value, Some(NumericValue(3)), 4.5, 10.0, Some("guy"),
        Some(DateTime.now()), None, None, expired = false, 140, 0, None, linkSource = NormalLinkInterface, None, None, None))
      , assetFiller.toRoadLinkForFillTopology(roadLink))

    val (methodTest1, methodTestChangeSet1) = assetFiller.droppedSegmentWrongDirection(assetFiller.toRoadLinkForFillTopology(roadLink), assets, initChangeSet)
    val (methodTest, methodTestChangeSet) = assetFiller.adjustSegmentSideCodes(assetFiller.toRoadLinkForFillTopology(roadLink), methodTest1, methodTestChangeSet1)

    methodTest should have size 1
    methodTest.map(_.sideCode) should be(Seq(SideCode.AgainstDigitizing))
    methodTest.map(_.value) should be(Seq(Some(NumericValue(3))))
    methodTestChangeSet.droppedAssetIds should have size 1
    methodTestChangeSet.droppedAssetIds.head should be(1)
  }
  
  test("Fuse two consecutive segments  with same value in same RoadLink") {
    val roadLink = RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
      TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    val assets = assetFiller.toLinearAsset(Seq(
      PersistedLinearAsset(1, linkId1, SideCode.BothDirections.value, Some(NumericValue(2)), 0.0, 4.5, Some("guy"),
        Some(DateTime.now()), None, None, expired = false, 140, 0, None, linkSource = NormalLinkInterface, None, None, None),
      PersistedLinearAsset(2, linkId1, SideCode.BothDirections.value, Some(NumericValue(2)), 4.5, 10.0, Some("guy"),
        Some(DateTime.now()), None, None, expired = false, 140, 0, None, linkSource = NormalLinkInterface, None, None, None))
    ,assetFiller.toRoadLinkForFillTopology(roadLink))

    val (methodTest, methodTestChangeSet) = assetFiller.fuse(assetFiller.toRoadLinkForFillTopology(roadLink),  assets, initChangeSet)
    val (testWholeProcess, changeSet) = assetFiller.fillTopology(Seq(roadLink).map(assetFiller.toRoadLinkForFillTopology), Map(linkId1 -> assets), 160)

    methodTest should have size 1
    methodTest.map(_.sideCode) should be(Seq(SideCode.BothDirections))
    methodTest.map(_.value) should be(Seq(Some(NumericValue(2))))
    methodTest.map(_.typeId) should be(List(140))
    methodTest.map(_.startMeasure) should be(List(0.0))
    methodTest.map(_.endMeasure) should be(List(10.0))
    methodTestChangeSet.adjustedMValues should have size 1
    methodTestChangeSet.adjustedSideCodes should be(List())

    testWholeProcess should have size 1
    testWholeProcess.map(_.sideCode) should be(Seq(SideCode.BothDirections))
    testWholeProcess.map(_.value) should be(Seq(Some(NumericValue(2))))
    testWholeProcess.map(_.typeId) should be(List(140))
    testWholeProcess.map(_.startMeasure) should be(List(0.0))
    testWholeProcess.map(_.endMeasure) should be(List(10.0))
    changeSet.adjustedMValues should have size 1
    changeSet.adjustedSideCodes should be(List())
    
  }

  test("Fuse asset when values are None and side code are sames, three consecutive") {
    val roadLink = RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(15.0, 0.0)), 15.0, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
    TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    val assets = assetFiller.toLinearAsset(Seq(
      PersistedLinearAsset(1, linkId1, SideCode.BothDirections.value, None, 0.0, 4.5, Some("guy"),
        Some(DateTime.now()), None, None, expired = false, 160, 0, None, linkSource = NormalLinkInterface, None, None, None),
      PersistedLinearAsset(2, linkId1, SideCode.BothDirections.value, None, 4.5, 9.0, Some("guy"),
        Some(DateTime.now().minusDays(2)), None, None, expired = false, 160, 0, None, linkSource = NormalLinkInterface, None, None, None),
      PersistedLinearAsset(3, linkId1, SideCode.BothDirections.value, None, 9.0, 15.0, Some("guy"),
        Some(DateTime.now().minusDays(1)), None, None, expired = false, 160, 0, None, linkSource = NormalLinkInterface, None, None, None)
    ),assetFiller.toRoadLinkForFillTopology(roadLink))

    val (methodTest, methodTestChangeSet) = assetFiller.fuse(assetFiller.toRoadLinkForFillTopology(roadLink), assets, initChangeSet)
    val (testWholeProcess, changeSet) = assetFiller.fillTopology(Seq(roadLink).map(assetFiller.toRoadLinkForFillTopology), Map(linkId1 -> assets), 160)

    methodTest.length should be(1)
    methodTest.head.id should be(1)
    methodTest.head.endMeasure should be(15.0)
    methodTest.head.startMeasure should be(0.0)
    methodTestChangeSet.adjustedMValues.length should be(1)
    methodTestChangeSet.adjustedMValues.head.endMeasure should be(15.0)
    methodTestChangeSet.adjustedMValues.head.startMeasure should be(0.0)
    methodTestChangeSet.expiredAssetIds should be(Set(2, 3))

    testWholeProcess.length should be(1)
    testWholeProcess.head.id should be(1)
    testWholeProcess.head.endMeasure should be(15.0)
    testWholeProcess.head.startMeasure should be(0.0)
    changeSet.adjustedMValues.length should be(1)
    changeSet.adjustedMValues.head.endMeasure should be(15.0)
    changeSet.adjustedMValues.head.startMeasure should be(0.0)
    changeSet.expiredAssetIds should be(Set(2, 3))
    
  }

  test("Fuse asset when value and side code are sames, three consecutive") {
    val roadLink = RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(15.0, 0.0)), 15.0, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
      TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    val assets = assetFiller.toLinearAsset(Seq(
      PersistedLinearAsset(1, linkId1, SideCode.BothDirections.value, Some(NumericValue(10)), 0.0, 4.5, Some("guy"),
        Some(DateTime.now()), None, None, expired = false, 160, 0, None, linkSource = NormalLinkInterface, None, None, None),
      PersistedLinearAsset(2, linkId1, SideCode.BothDirections.value, Some(NumericValue(10)), 4.5, 9.0, Some("guy"),
        Some(DateTime.now().minusDays(2)), None, None, expired = false, 160, 0, None, linkSource = NormalLinkInterface, None, None, None),
      PersistedLinearAsset(3, linkId1, SideCode.BothDirections.value, Some(NumericValue(10)), 9.0, 15.0, Some("guy"),
        Some(DateTime.now().minusDays(1)), None, None, expired = false, 160, 0, None, linkSource = NormalLinkInterface, None, None, None)
    ), assetFiller.toRoadLinkForFillTopology(roadLink))

    val (methodTest, methodTestChangeSet) = assetFiller.fuse(assetFiller.toRoadLinkForFillTopology(roadLink), assets, initChangeSet)
    val (testWholeProcess, changeSet) = assetFiller.fillTopology(Seq(roadLink).map(assetFiller.toRoadLinkForFillTopology), Map(linkId1 -> assets), 160)
    Seq((methodTest, methodTestChangeSet), (testWholeProcess, changeSet)).foreach(item => {
      val filledTopology = item._1
      val changeSet = item._2
      filledTopology.length should be(1)
      filledTopology.head.id should be(1)
      filledTopology.head.endMeasure should be(15.0)
      filledTopology.head.startMeasure should be(0.0)
      changeSet.adjustedMValues.length should be(1)
      changeSet.adjustedMValues.head.endMeasure should be(15.0)
      changeSet.adjustedMValues.head.startMeasure should be(0.0)
      changeSet.expiredAssetIds should be(Set(2, 3))
    })
  }

  test("Merge asset when value and side code is same") {
    val roadLink = RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
      TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    val assets = assetFiller.toLinearAsset(Seq(
      PersistedLinearAsset(1, linkId1, SideCode.AgainstDigitizing.value, Some(NumericValue(10)), 0.0, 5.0, Some("guy"),
        Some(DateTime.now()), None, None, expired = false, 180, 0, None, linkSource = NormalLinkInterface, None, None, None),
      PersistedLinearAsset(2, linkId1, SideCode.AgainstDigitizing.value, Some(NumericValue(10)), 5.0, 10.0, Some("guy"),
        Some(DateTime.now().minusDays(2)), None, None, expired = false, 180, 0, None, linkSource = NormalLinkInterface, None, None, None),
      PersistedLinearAsset(3, linkId1, SideCode.TowardsDigitizing.value, Some(NumericValue(20)), 0.0, 4.0, Some("guy"),
        Some(DateTime.now().minusDays(3)), None, None, expired = false, 180, 0, None, linkSource = NormalLinkInterface, None, None, None),
      PersistedLinearAsset(4, linkId1, SideCode.TowardsDigitizing.value, Some(NumericValue(20)), 4.0, 10.0, Some("guy"),
        Some(DateTime.now().minusDays(4)), None, None, expired = false, 180, 0, None, linkSource = NormalLinkInterface, None, None, None)
    ), assetFiller.toRoadLinkForFillTopology(roadLink))

    val (methodTest, methodTestChangeSet) = assetFiller.fuse(assetFiller.toRoadLinkForFillTopology(roadLink), assets, initChangeSet)
    val (testWholeProcess, changeSet) = assetFiller.fillTopology(Seq(roadLink).map(assetFiller.toRoadLinkForFillTopology), Map(linkId1 -> assets), 180)
    Seq((methodTest, methodTestChangeSet), (testWholeProcess, changeSet)).foreach(item => {
      val filledTopology = item._1
      val changeSet = item._2
      filledTopology.length should be(2)
      filledTopology.head.startMeasure should be(0.0)
      filledTopology.head.endMeasure should be(10.00)
      filledTopology.last.startMeasure should be(0.0)
      filledTopology.last.endMeasure should be(10.00)
      changeSet.adjustedMValues.length should be(2)
      changeSet.adjustedMValues.head.endMeasure should be(10.0)
      changeSet.adjustedMValues.head.startMeasure should be(0.0)
      changeSet.adjustedMValues.last.endMeasure should be(10.0)
      changeSet.adjustedMValues.last.startMeasure should be(0.0)
    })
  }

  test("combine two segments in same place and same value in RoadLink with different side code") {
    val roadLinks = Seq(
      RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
        TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    )
    
    val assets = Seq(createAsset(1, linkId1, Measure(0.0, 10), SideCode.TowardsDigitizing, Some(NumericValue(2)),TrafficDirection.BothDirections),
      createAsset(2, linkId1, Measure(0.0, 10), SideCode.AgainstDigitizing, Some(NumericValue(2)),TrafficDirection.BothDirections))
    
      val (methodTest, methodTestChangeSet) = assetFiller.combine(roadLinks.map(assetFiller.toRoadLinkForFillTopology).head, assets, initChangeSet)
      val (testWholeProcess, changeSet) = assetFiller.fillTopology(roadLinks.map(assetFiller.toRoadLinkForFillTopology), Map(linkId1 -> assets), 140)
    
    Seq((methodTest,methodTestChangeSet),(testWholeProcess,changeSet)).foreach(item =>{
      val filledTopology = item._1
      val changeSet = item._2
      filledTopology should have size 1
      filledTopology.map(_.sideCode) should be(Seq(SideCode.BothDirections))
      filledTopology.map(_.value) should be(Seq(Some(NumericValue(2))))
      filledTopology.map(_.createdBy) should be(Seq(Some("guy")))
      filledTopology.map(_.typeId) should be(List(140))
      filledTopology.map(_.startMeasure) should be(List(0.0))
      filledTopology.map(_.endMeasure) should be(List(10.0))
      changeSet.adjustedMValues should be(List())
      changeSet.adjustedSideCodes.length should be(1)
      changeSet.adjustedSideCodes.map(_.sideCode) should be(List(SideCode.BothDirections))
      changeSet.droppedAssetIds should be(Set())
      changeSet.expiredAssetIds.size should be(1)
    })
  
  }

  test("combine two segments in same place and same value None in RoadLink with different side code") {
    val roadLinks = Seq(
      RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
        TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    )

    val assets = 
      Seq(createAsset(1, linkId1, Measure(0.0, 10), SideCode.TowardsDigitizing, None, TrafficDirection.BothDirections),
        createAsset(2, linkId1, Measure(0.0, 10), SideCode.AgainstDigitizing, None, TrafficDirection.BothDirections))

    val (methodTest, methodTestChangeSet) = assetFiller.combine(roadLinks.map(assetFiller.toRoadLinkForFillTopology).head, assets, initChangeSet)
    val (testWholeProcess, changeSet) = assetFiller.fillTopology(roadLinks.map(assetFiller.toRoadLinkForFillTopology), Map(linkId1 -> assets), 140)

    methodTest should have size 1
    methodTest.map(_.sideCode) should be(Seq(SideCode.BothDirections))
    methodTest.map(_.value) should be(Seq(None))
    methodTest.map(_.createdBy) should be(Seq(Some("guy")))
    methodTest.map(_.typeId) should be(List(140))
    methodTest.map(_.startMeasure) should be(List(0.0))
    methodTest.map(_.endMeasure) should be(List(10.0))
    methodTestChangeSet.adjustedMValues should be(List())
    methodTestChangeSet.adjustedSideCodes.length should be(1)
    methodTestChangeSet.adjustedSideCodes.map(_.sideCode) should be(List(SideCode.BothDirections))
    methodTestChangeSet.droppedAssetIds should be(Set())
    methodTestChangeSet.expiredAssetIds.size should be(1)

    testWholeProcess should have size 1
    testWholeProcess.map(_.sideCode) should be(Seq(SideCode.BothDirections))
    testWholeProcess.map(_.value) should be(Seq(None))
    testWholeProcess.map(_.createdBy) should be(Seq(Some("guy")))
    testWholeProcess.map(_.typeId) should be(List(140))
    testWholeProcess.map(_.startMeasure) should be(List(0.0))
    testWholeProcess.map(_.endMeasure) should be(List(10.0))
    changeSet.adjustedMValues should be(List())
    changeSet.adjustedSideCodes.length should be(1)
    changeSet.adjustedSideCodes.map(_.sideCode) should be(List(SideCode.BothDirections))
    changeSet.droppedAssetIds should be(Set())
    changeSet.expiredAssetIds.size should be(1)
  }

  test("Adjust asset length when it is shorter than link, from end") {
    val roadLinks = Seq(
      RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(11.0, 0.0)), 11.0, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
        TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    )

    val assets = Seq(createAsset(1, linkId1, Measure(0.0, 10), SideCode.TowardsDigitizing, None, TrafficDirection.BothDirections))

    val (methodTest, combineTestChangeSet) = assetFiller.adjustAssets(roadLinks.map(assetFiller.toRoadLinkForFillTopology).head, assets, initChangeSet)
    val (testWholeProcess, changeSet) = assetFiller.fillTopology(roadLinks.map(assetFiller.toRoadLinkForFillTopology), Map(linkId1 -> assets), 140)

    Seq((methodTest, combineTestChangeSet), (testWholeProcess, changeSet)).foreach(item => {
      val changeSet = item._2
      changeSet.adjustedMValues.head.startMeasure should be(0)
      changeSet.adjustedMValues.head.endMeasure should be(11)
    })
  }
  
  test("Do not adjust asset length when link difference is bigger than 2m") {
    val roadLinks = Seq(
      RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(15.0, 0.0)), 15.0, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
        TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    )

    val assets = Seq(createAsset(1, linkId1, Measure(0, 11), SideCode.BothDirections, None, TrafficDirection.BothDirections))
    
    val (methodTest, combineTestChangeSet) = assetFiller.adjustAssets(roadLinks.map(assetFiller.toRoadLinkForFillTopology).head, assets, initChangeSet)
    val (testWholeProcess, changeSet) = assetFiller.fillTopology(roadLinks.map(assetFiller.toRoadLinkForFillTopology), Map(linkId1 -> assets), 140)

    Seq((methodTest, combineTestChangeSet), (testWholeProcess, changeSet)).foreach(item => {
      val filledTopology = item._1
      val changeSet = item._2
      changeSet.adjustedMValues.size should be(0)
      filledTopology.head.startMeasure should be(0)
      filledTopology.head.endMeasure should be(11)
    })
  }

  test("Adjust road link long asset length when link difference is bigger than 2m from both ends") {
    val roadLinks = Seq(
      RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(15.0, 0.0)), 15.0, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
        TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    )

    val assets = Seq(createAsset(1, linkId1, Measure(3, 11), SideCode.BothDirections, None, TrafficDirection.BothDirections, ExitNumbers.typeId))

    val (methodTest, combineTestChangeSet) = assetFiller.adjustAssets(roadLinks.map(assetFiller.toRoadLinkForFillTopology).head, assets, initChangeSet)
    val (testWholeProcess, changeSet) = assetFiller.fillTopology(roadLinks.map(assetFiller.toRoadLinkForFillTopology), Map(linkId1 -> assets), 140)

    Seq((methodTest, combineTestChangeSet), (testWholeProcess, changeSet)).foreach(item => {
      val filledTopology = item._1
      val changeSet = item._2
      changeSet.adjustedMValues.size should be(1)
      filledTopology.head.startMeasure should be(0)
      filledTopology.head.endMeasure should be(15)
    })
  }

  test("Adjust road link long asset length when link difference is bigger than 2m from the beginning") {
    val roadLinks = Seq(
      RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(15.0, 0.0)), 15.0, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
        TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    )

    val assets = Seq(createAsset(1, linkId1, Measure(3, 15), SideCode.BothDirections, None, TrafficDirection.BothDirections, CyclingAndWalking.typeId))

    val (methodTest, combineTestChangeSet) = assetFiller.adjustAssets(roadLinks.map(assetFiller.toRoadLinkForFillTopology).head, assets, initChangeSet)
    val (testWholeProcess, changeSet) = assetFiller.fillTopology(roadLinks.map(assetFiller.toRoadLinkForFillTopology), Map(linkId1 -> assets), 140)

    Seq((methodTest, combineTestChangeSet), (testWholeProcess, changeSet)).foreach(item => {
      val filledTopology = item._1
      val changeSet = item._2
      changeSet.adjustedMValues.size should be(1)
      filledTopology.head.startMeasure should be(0)
      filledTopology.head.endMeasure should be(15)
    })
  }

  test("Adjust road link long asset length when link difference is bigger than 2m from the end") {
    val roadLinks = Seq(
      RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(15.0, 0.0)), 15.0, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
        TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    )

    val assets = Seq(createAsset(1, linkId1, Measure(0, 11), SideCode.BothDirections, None, TrafficDirection.BothDirections, CareClass.typeId))

    val (methodTest, combineTestChangeSet) = assetFiller.adjustAssets(roadLinks.map(assetFiller.toRoadLinkForFillTopology).head, assets, initChangeSet)
    val (testWholeProcess, changeSet) = assetFiller.fillTopology(roadLinks.map(assetFiller.toRoadLinkForFillTopology), Map(linkId1 -> assets), 140)

    Seq((methodTest, combineTestChangeSet), (testWholeProcess, changeSet)).foreach(item => {
      val filledTopology = item._1
      val changeSet = item._2
      changeSet.adjustedMValues.size should be(1)
      filledTopology.head.startMeasure should be(0)
      filledTopology.head.endMeasure should be(15)
    })
  }

  test("adjust two-sided winter speed limits from both ends leaving the middle limit intact") {
    val roadLinks = Seq(
      RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
        TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    )
    val speedLimit1 = PieceWiseLinearAsset(1, linkId1, SideCode.BothDirections, Some(NumericValue(80)), Seq(Point(0.2, 0.0), Point(2.0, 0.0)),
      false, 0.2, 2.0, Set(Point(0.2, 0.0), Point(2.0, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimit2 = PieceWiseLinearAsset(2, linkId1, SideCode.BothDirections, Some(NumericValue(60)), Seq(Point(2.0, 0.0), Point(4.9, 0.0)),
      false, 2.0, 4.9, Set(Point(2.0, 0.0), Point(4.9, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimit3 = PieceWiseLinearAsset(3, linkId1, SideCode.BothDirections, Some(NumericValue(100)), Seq(Point(4.9, 0.0), Point(9.8, 0.0)),
      false, 4.9, 9.8, Set(Point(4.9, 0.0), Point(9.8, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimits = Map(linkId1 -> Seq(speedLimit1, speedLimit2, speedLimit3))
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(roadLinks.map(SpeedLimitFiller.toRoadLinkForFillTopology), speedLimits, SpeedLimitAsset.typeId)
    val sortedFilledTopology = filledTopology.sortBy(_.startMeasure)
    sortedFilledTopology.length should be(3)
    sortedFilledTopology.head.geometry should be(Seq(Point(0.0, 0.0), Point(2.0, 0.0)))
    sortedFilledTopology.head.startMeasure should be(0.0)
    sortedFilledTopology.head.endMeasure should be(2.0)
    sortedFilledTopology(1).geometry should be(Seq(Point(2.0, 0.0), Point(4.9, 0.0)))
    sortedFilledTopology(1).startMeasure should be(2.0)
    sortedFilledTopology(1).endMeasure should be(4.9)
    sortedFilledTopology.last.geometry should be(Seq(Point(4.9, 0.0), Point(10.0, 0.0)))
    sortedFilledTopology.last.startMeasure should be(4.9)
    sortedFilledTopology.last.endMeasure should be(10.0)
    changeSet.droppedAssetIds should be(Set.empty)
    changeSet.adjustedSideCodes should be(Nil)
    changeSet.expiredAssetIds should be(Set.empty)
    changeSet.valueAdjustments should be(Nil)
    val adjustedMValues = changeSet.adjustedMValues
    adjustedMValues.sortBy(_.assetId) should be(Seq(MValueAdjustment(1, linkId1, 0, 2.0), MValueAdjustment(3, linkId1, 4.9, 10.0)))
  }


  test("adjust one-sided winter limits when there is one on one side and two on the other") {
    val roadLinks = Seq(
      RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
        TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    )
    val speedLimit1 = PieceWiseLinearAsset(1, linkId1, SideCode.TowardsDigitizing, Some(NumericValue(80)), Seq(Point(0.2, 0.0), Point(2.9, 0.0)),
      false, 0.2, 2.9, Set(Point(0.2, 0.0), Point(2.9, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimit2 = PieceWiseLinearAsset(2, linkId1, SideCode.TowardsDigitizing, Some(NumericValue(60)), Seq(Point(2.9, 0.0), Point(8.9, 0.0)),
      false, 2.9, 8.9, Set(Point(2.9, 0.0), Point(8.9, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimit3 = PieceWiseLinearAsset(3, linkId1, SideCode.AgainstDigitizing, Some(NumericValue(100)), Seq(Point(0.9, 0.0), Point(9.8, 0.0)),
      false, 0.9, 9.8, Set(Point(0.9, 0.0), Point(9.8, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimits = Map(linkId1 -> Seq(speedLimit1, speedLimit2, speedLimit3))
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(roadLinks.map(SpeedLimitFiller.toRoadLinkForFillTopology), speedLimits, SpeedLimitAsset.typeId)
    val sortedFilledTopology = filledTopology.sortBy(_.id)
    sortedFilledTopology.length should be(3)
    sortedFilledTopology.head.geometry should be(Seq(Point(0.0, 0.0), Point(2.9, 0.0)))
    sortedFilledTopology.head.startMeasure should be(0.0)
    sortedFilledTopology.head.endMeasure should be(2.9)
    sortedFilledTopology(1).geometry should be(Seq(Point(2.9, 0.0), Point(10.0, 0.0)))
    sortedFilledTopology(1).startMeasure should be(2.9)
    sortedFilledTopology(1).endMeasure should be(10.0)
    sortedFilledTopology.last.geometry should be(Seq(Point(0.0, 0.0), Point(10.0, 0.0)))
    sortedFilledTopology.last.startMeasure should be(0.0)
    sortedFilledTopology.last.endMeasure should be(10.0)
    changeSet.droppedAssetIds should be(Set.empty)
    changeSet.adjustedSideCodes should be(Nil)
    changeSet.expiredAssetIds should be(Set.empty)
    changeSet.valueAdjustments should be(Nil)
    val adjustedMValues = changeSet.adjustedMValues
    adjustedMValues.sortBy(_.assetId) should be(Seq(MValueAdjustment(1, linkId1, 0, 2.9), MValueAdjustment(2, linkId1, 2.9, 10.0),
      MValueAdjustment(3, linkId1, 0.0, 10.0)))
  }

  test("two opposite side directions with smallest start measure are adjusted") {
    val roadLinks = Seq(
      RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
        TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    )
    val speedLimit1 = PieceWiseLinearAsset(1, linkId1, SideCode.TowardsDigitizing, Some(NumericValue(80)), Seq(Point(0.2, 0.0), Point(2.9, 0.0)),
      false, 0.2, 2.9, Set(Point(0.2, 0.0), Point(2.9, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimit2 = PieceWiseLinearAsset(2, linkId1, SideCode.AgainstDigitizing, Some(NumericValue(60)), Seq(Point(0.7, 0.0), Point(2.9, 0.0)),
      false, 0.7, 2.9, Set(Point(0.7, 0.0), Point(2.9, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimit3 = PieceWiseLinearAsset(3, linkId1, SideCode.BothDirections, Some(NumericValue(100)), Seq(Point(2.9, 0.0), Point(10.0, 0.0)),
      false, 2.9, 10.0, Set(Point(2.9, 0.0), Point(10.0, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimits = Map(linkId1 -> Seq(speedLimit1, speedLimit2, speedLimit3))
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(roadLinks.map(SpeedLimitFiller.toRoadLinkForFillTopology), speedLimits, SpeedLimitAsset.typeId)
    val sortedFilledTopology = filledTopology.sortBy(_.id)
    sortedFilledTopology.length should be(3)
    sortedFilledTopology.head.geometry should be(Seq(Point(0.0, 0.0), Point(2.9, 0.0)))
    sortedFilledTopology.head.startMeasure should be(0.0)
    sortedFilledTopology.head.endMeasure should be(2.9)
    sortedFilledTopology(1).geometry should be(Seq(Point(0.0, 0.0), Point(2.9, 0.0)))
    sortedFilledTopology(1).startMeasure should be(0.0)
    sortedFilledTopology(1).endMeasure should be(2.9)
    sortedFilledTopology.last.geometry should be(Seq(Point(2.9, 0.0), Point(10.0, 0.0)))
    sortedFilledTopology.last.startMeasure should be(2.9)
    sortedFilledTopology.last.endMeasure should be(10.0)
    changeSet.droppedAssetIds should be(Set.empty)
    changeSet.adjustedSideCodes should be(Nil)
    changeSet.expiredAssetIds should be(Set.empty)
    changeSet.valueAdjustments should be(Nil)
    val adjustedMValues = changeSet.adjustedMValues
    adjustedMValues.sortBy(_.assetId) should be(Seq(MValueAdjustment(1, linkId1, 0.0, 2.9), MValueAdjustment(2, linkId1, 0.0, 2.9)))
  }

  test("only the one-sided limit with the smallest start measure is adjusted when the two smallest are on the same side") {
    val roadLinks = Seq(
      RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
        TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    )
    val speedLimit1 = PieceWiseLinearAsset(1, linkId1, SideCode.TowardsDigitizing, Some(NumericValue(80)), Seq(Point(0.2, 0.0), Point(2.9, 0.0)),
      false, 0.2, 2.9, Set(Point(0.2, 0.0), Point(2.9, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimit2 = PieceWiseLinearAsset(2, linkId1, SideCode.TowardsDigitizing, Some(NumericValue(60)), Seq(Point(2.9, 0.0), Point(5.9, 0.0)),
      false, 2.9, 5.9, Set(Point(2.9, 0.0), Point(5.9, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimit3 = PieceWiseLinearAsset(3, linkId1, SideCode.BothDirections, Some(NumericValue(100)), Seq(Point(5.9, 0.0), Point(10.0, 0.0)),
      false, 5.9, 10.0, Set(Point(5.9, 0.0), Point(10.0, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimits = Map(linkId1 -> Seq(speedLimit1, speedLimit2, speedLimit3))
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(roadLinks.map(SpeedLimitFiller.toRoadLinkForFillTopology), speedLimits, SpeedLimitAsset.typeId)
    val (generated, adjusted) = filledTopology.partition(_.id == 0)
    val sortedFilledTopology = adjusted.sortBy(_.id)
    sortedFilledTopology.length should be(3)
    sortedFilledTopology.head.geometry should be(Seq(Point(0.0, 0.0), Point(2.9, 0.0)))
    sortedFilledTopology.head.startMeasure should be(0.0)
    sortedFilledTopology.head.endMeasure should be(2.9)
    sortedFilledTopology(1).geometry should be(Seq(Point(2.9, 0.0), Point(5.9, 0.0)))
    sortedFilledTopology(1).startMeasure should be(2.9)
    sortedFilledTopology(1).endMeasure should be(5.9)
    sortedFilledTopology.last.geometry should be(Seq(Point(5.9, 0.0), Point(10.0, 0.0)))
    sortedFilledTopology.last.startMeasure should be(5.9)
    sortedFilledTopology.last.endMeasure should be(10.0)
    changeSet.droppedAssetIds should be(Set.empty)
    changeSet.adjustedSideCodes.sortBy(_.assetId) should be(Seq(SideCodeAdjustment(1,linkId1,BothDirections,20), SideCodeAdjustment(2,linkId1,BothDirections,20)))
    changeSet.expiredAssetIds should be(Set.empty)
    changeSet.valueAdjustments should be(Nil)
    val adjustedMValues = changeSet.adjustedMValues
    adjustedMValues.sortBy(_.assetId) should be(Seq(MValueAdjustment(1, linkId1, 0.0, 2.9)))
  }

  test("two opposite side directions with largest end measure and the two-sided limit in the beginning are adjusted") {
    val roadLinks = Seq(
      RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
        TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    )
    val speedLimit1 = PieceWiseLinearAsset(1, linkId1, SideCode.BothDirections, Some(NumericValue(80)), Seq(Point(0.2, 0.0), Point(2.9, 0.0)),
      false, 0.2, 2.9, Set(Point(0.2, 0.0), Point(2.9, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimit2 = PieceWiseLinearAsset(2, linkId1, SideCode.AgainstDigitizing, Some(NumericValue(60)), Seq(Point(2.9, 0.0), Point(9.9, 0.0)),
      false, 2.9, 9.9, Set(Point(2.9, 0.0), Point(9.9, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimit3 = PieceWiseLinearAsset(3, linkId1, SideCode.TowardsDigitizing, Some(NumericValue(100)), Seq(Point(2.9, 0.0), Point(8.0, 0.0)),
      false, 2.9, 8.0, Set(Point(2.9, 0.0), Point(8.0, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimits = Map(linkId1 -> Seq(speedLimit1, speedLimit2, speedLimit3))
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(roadLinks.map(SpeedLimitFiller.toRoadLinkForFillTopology), speedLimits, SpeedLimitAsset.typeId)
    val sortedFilledTopology = filledTopology.sortBy(_.id)
    sortedFilledTopology.length should be(3)
    sortedFilledTopology.head.geometry should be(Seq(Point(0.0, 0.0), Point(2.9, 0.0)))
    sortedFilledTopology.head.startMeasure should be(0.0)
    sortedFilledTopology.head.endMeasure should be(2.9)
    sortedFilledTopology(1).geometry should be(Seq(Point(2.9, 0.0), Point(10.0, 0.0)))
    sortedFilledTopology(1).startMeasure should be(2.9)
    sortedFilledTopology(1).endMeasure should be(10.0)
    sortedFilledTopology.last.geometry should be(Seq(Point(2.9, 0.0), Point(10.0, 0.0)))
    sortedFilledTopology.last.startMeasure should be(2.9)
    sortedFilledTopology.last.endMeasure should be(10.0)
    changeSet.droppedAssetIds should be(Set.empty)
    changeSet.adjustedSideCodes should be(Nil)
    changeSet.expiredAssetIds should be(Set.empty)
    changeSet.valueAdjustments should be(Nil)
    val adjustedMValues = changeSet.adjustedMValues
    adjustedMValues.sortBy(_.assetId) should be(Seq(MValueAdjustment(1, linkId1, 0, 2.9), MValueAdjustment(2, linkId1, 2.9, 10.0), MValueAdjustment(3, linkId1, 2.9, 10.0)))
  }

  test("only the one-sided limit with the largest end measure is adjusted when the two largest are on the same side") {
    val roadLinks = Seq(
      RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
        TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    )
    val speedLimit1 = PieceWiseLinearAsset(1, linkId1, SideCode.BothDirections, Some(SpeedLimitValue(80)), Seq(Point(0.2, 0.0), Point(2.9, 0.0)),
      false, 0.2, 2.9, Set(Point(0.2, 0.0), Point(2.9, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimit2 = PieceWiseLinearAsset(2, linkId1, SideCode.TowardsDigitizing, Some(SpeedLimitValue(60)), Seq(Point(2.9, 0.0), Point(5.9, 0.0)),
      false, 2.9, 5.9, Set(Point(2.9, 0.0), Point(5.9, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimit3 = PieceWiseLinearAsset(3, linkId1, SideCode.TowardsDigitizing, Some(SpeedLimitValue(100)), Seq(Point(5.9, 0.0), Point(9.5, 0.0)),
      false, 5.9, 9.5, Set(Point(5.9, 0.0), Point(9.5, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimits = Map(linkId1 -> Seq(speedLimit1, speedLimit2, speedLimit3))
    val (filledTopology, changeSet) = SpeedLimitFiller.fillTopology(roadLinks.map(SpeedLimitFiller.toRoadLinkForFillTopology), speedLimits, SpeedLimitAsset.typeId)
    val (generated, adjusted) = filledTopology.partition(_.id == 0)
    val sortedFilledTopology = adjusted.sortBy(_.id)
    sortedFilledTopology.length should be(3)
    sortedFilledTopology.head.geometry should be(Seq(Point(0.0, 0.0), Point(2.9, 0.0)))
    sortedFilledTopology.head.startMeasure should be(0.0)
    sortedFilledTopology.head.endMeasure should be(2.9)
    sortedFilledTopology(1).geometry should be(Seq(Point(2.9, 0.0), Point(5.9, 0.0)))
    sortedFilledTopology(1).startMeasure should be(2.9)
    sortedFilledTopology(1).endMeasure should be(5.9)
    sortedFilledTopology.last.geometry should be(Seq(Point(5.9, 0.0), Point(10.0, 0.0)))
    sortedFilledTopology.last.startMeasure should be(5.9)
    sortedFilledTopology.last.endMeasure should be(10.0)
    changeSet.droppedAssetIds should be(Set.empty)
    changeSet.adjustedSideCodes.sortBy(_.assetId) should be(Seq(SideCodeAdjustment(2, linkId1, BothDirections,20), SideCodeAdjustment(3, linkId1, BothDirections,20)))
    changeSet.expiredAssetIds should be(Set.empty)
    changeSet.valueAdjustments should be(Nil)
    val adjustedMValues = changeSet.adjustedMValues
    adjustedMValues.sortBy(_.assetId) should be(Seq(MValueAdjustment(1, linkId1, 0, 2.9), MValueAdjustment(3, linkId1, 5.9, 10.0)))
  }

  test("Adjust start and end m-value when difference is 0.001") {
    val roadLinks = Seq(
      RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(36.783, 0.0)), 36.783, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
        TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    )

    val assets = Seq(
      createAsset(1, linkId1, Measure(0.001, 36.782), SideCode.BothDirections, None, TrafficDirection.BothDirections)
    )

    val (methodTest, combineTestChangeSet) = assetFiller.adjustAssets(roadLinks.map(assetFiller.toRoadLinkForFillTopology).head, assets, initChangeSet)

    val sorted = methodTest.sortBy(_.endMeasure)

    sorted.size should be(1)
    //107.093
    sorted(0).startMeasure should be(0)
    sorted(0).endMeasure should be(36.783)

  }

  test("Do not fill whole in start of link when difference is two big") {
    val roadLinks = Seq(
      RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(107.093, 0.0)), 107.093, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
        TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    )

    val assets = Seq(
      createAsset(1, linkId1, Measure(101.42, 103.841), SideCode.BothDirections, None, TrafficDirection.BothDirections),
      createAsset(2, linkId1, Measure(103.841, 107.093), SideCode.BothDirections, None, TrafficDirection.BothDirections)
    )

    val (methodTest, combineTestChangeSet) = assetFiller.fillHolesTwoSteps(roadLinks.map(assetFiller.toRoadLinkForFillTopology).head, assets, initChangeSet)
    
    val sorted = methodTest.sortBy(_.endMeasure)

    sorted.size should be(2)
    
    sorted(0).startMeasure should be(101.42)
    sorted(0).endMeasure should be(103.841)

    sorted(1).startMeasure should be(103.841)
    sorted(1).endMeasure should be(107.093)
  }

  test("Do not fill whole in end of link when difference is two big") {
    val roadLinks = Seq(
      RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(36.783, 0.0)), 36.783, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
        TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    )

    val assets = Seq(
      createAsset(1, linkId1, Measure(0, 18.082), SideCode.BothDirections, None, TrafficDirection.BothDirections)
    )

    val (methodTest, combineTestChangeSet) = assetFiller.fillHolesWithFuse(roadLinks.map(assetFiller.toRoadLinkForFillTopology).head, assets, initChangeSet)

    val sorted = methodTest.sortBy(_.endMeasure)

    sorted.size should be(1)
    sorted(0).startMeasure should be(0)
    sorted(0).endMeasure should be(18.082)

  }
  private def roadLink(linkId: String, geometry: Seq[Point], administrativeClass: AdministrativeClass = Unknown): RoadLink = {
    val municipalityCode = "MUNICIPALITYCODE" -> BigInt(235)
    RoadLink(
      linkId, geometry, GeometryUtils.geometryLength(geometry), administrativeClass, 1,
      TrafficDirection.BothDirections, Motorway, None, None, Map(municipalityCode))
  }
  
  test("Overlapping assets, asset is both direction and full length of link, split so it does not overlap") {
    val topology = Seq(
      roadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0))))

    val speedLimit1 = PieceWiseLinearAsset(1, linkId1, SideCode.BothDirections, Some(SpeedLimitValue(80)), Seq(Point(0.0, 0.0), Point(10, 0.0)),
      false, 0, 10, Set(Point(0.0, 0.0), Point(10, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimit2 = PieceWiseLinearAsset(2, linkId1, SideCode.TowardsDigitizing, Some(SpeedLimitValue(50)), Seq(Point(3, 0.0), Point(5, 0.0)),
      false, 3, 5, Set(Point(3, 0.0), Point(5, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)
    val speedLimit3 = PieceWiseLinearAsset(3, linkId1, SideCode.AgainstDigitizing, Some(SpeedLimitValue(60)), Seq(Point(3, 0.0), Point(5, 0.0)),
      false, 3, 5, Set(Point(3, 0.0), Point(5, 0.0)), None, None, None, None, SpeedLimitAsset.typeId, TrafficDirection.BothDirections,
      0, None, NormalLinkInterface, Unknown, Map(), None, None, None)

    val speedLimits = Map(linkId1 -> Seq(speedLimit1, speedLimit2, speedLimit3))
    val (filledTopology, changeSet) = assetFiller.fillTopology(topology.map(SpeedLimitFiller.toRoadLinkForFillTopology), speedLimits, SpeedLimitAsset.typeId)
    val sortedFilledTopology = filledTopology.sortBy(_.endMeasure)

    sortedFilledTopology.length should be(4)
    sortedFilledTopology.head.geometry should be(Seq(Point(0.0, 0.0), Point(3, 0.0)))
    sortedFilledTopology.head.startMeasure should be(0.0)
    sortedFilledTopology.head.endMeasure should be(3)
    sortedFilledTopology.head.sideCode should be(SideCode.BothDirections)
    sortedFilledTopology.head.value should be(Some(SpeedLimitValue(80)))

    sortedFilledTopology.exists(_.sideCode == SideCode.TowardsDigitizing) should be(true)
    
    sortedFilledTopology.find(_.sideCode == SideCode.TowardsDigitizing).get.geometry should be(Seq(Point(3.0, 0.0), Point(5, 0.0)))
    sortedFilledTopology.find(_.sideCode == SideCode.TowardsDigitizing).get.startMeasure should be(3.0)
    sortedFilledTopology.find(_.sideCode == SideCode.TowardsDigitizing).get.endMeasure should be(5)
    sortedFilledTopology.find(_.sideCode == SideCode.TowardsDigitizing).get.value should be(Some(SpeedLimitValue(50)))

    sortedFilledTopology.exists(_.sideCode == SideCode.AgainstDigitizing) should be(true)
    
    sortedFilledTopology.find(_.sideCode == SideCode.AgainstDigitizing).get.geometry should be(Seq(Point(3.0, 0.0), Point(5, 0.0)))
    sortedFilledTopology.find(_.sideCode == SideCode.AgainstDigitizing).get.startMeasure should be(3.0)
    sortedFilledTopology.find(_.sideCode == SideCode.AgainstDigitizing).get.endMeasure should be(5)
    sortedFilledTopology.find(_.sideCode == SideCode.AgainstDigitizing).get.value should be(Some(SpeedLimitValue(60)))
    
    
    sortedFilledTopology.last.geometry should be(Seq(Point(5, 0.0), Point(10.0, 0.0)))
    sortedFilledTopology.last.startMeasure should be(5)
    sortedFilledTopology.last.endMeasure should be(10.0)
    sortedFilledTopology.last.sideCode should be(SideCode.BothDirections)
    sortedFilledTopology.last.value should be(Some(SpeedLimitValue(80)))
  }
  

  test("Fill hole in middle of links") {
    val roadLinks = Seq(
      RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(223.872, 0.0)), 223.872, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
        TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    )

    val assets = Seq(
      createAsset(1, linkId1, Measure(0, 102.482), SideCode.BothDirections, None, TrafficDirection.BothDirections),
      createAsset(2, linkId1, Measure(102.482, 207.303), SideCode.BothDirections, None, TrafficDirection.BothDirections),
      createAsset(3, linkId1, Measure(207.304, 215.304), SideCode.BothDirections, None, TrafficDirection.BothDirections),
      createAsset(4, linkId1, Measure(215.304, 223.872), SideCode.BothDirections, None, TrafficDirection.BothDirections)
    )

    val (methodTest, combineTestChangeSet) = assetFiller.fillHolesTwoSteps(roadLinks.map(assetFiller.toRoadLinkForFillTopology).head, assets, initChangeSet)

    val sorted = methodTest.sortBy(_.endMeasure)

    sorted.size should be(4)
    
    sorted(0).startMeasure should be(0)
    sorted(0).endMeasure should be(102.482)

    sorted(1).startMeasure should be(102.482)
    sorted(1).endMeasure should be(207.304)
    
    sorted(2).startMeasure should be(207.304)
    sorted(2).endMeasure should be(215.304)
    
    sorted(3).startMeasure should be(215.304)
    sorted(3).endMeasure should be(223.872)
    
  }

  test("Fill hole in middle of links, roadlink long assets") {
    val roadLinks = Seq(
      RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(223.872, 0.0)), 223.872, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
        TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    )

    val assets = Seq(
      createAsset(1, linkId1, Measure(0, 200.303), SideCode.BothDirections, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId),
      createAsset(2, linkId1, Measure(207.304, 215.304), SideCode.BothDirections, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId)
    )

    val (methodTest, combineTestChangeSet) = assetFiller.fillHolesTwoSteps(roadLinks.map(assetFiller.toRoadLinkForFillTopology).head, assets, initChangeSet)

    val sorted = methodTest.sortBy(_.endMeasure)

    sorted.size should be(2)

    sorted(0).startMeasure should be(0)
    sorted(0).endMeasure should be(207.304)

    sorted(1).startMeasure should be(207.304)
    sorted(1).endMeasure should be(215.304)

    val adjustedMValues = combineTestChangeSet.adjustedMValues
    adjustedMValues.sortBy(_.assetId) should be(Seq(MValueAdjustment(1, linkId1, 0,207.304)))

  }

  test("Fill hole in middle of links and merge similar parts, roadlink long assets") {
    val roadLinks = Seq(
      RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(400, 0.0)), 400, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
        TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    )

    val assets = Seq(
      createAsset(1, linkId1, Measure(0, 200.00), SideCode.BothDirections, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId),
      createAsset(2, linkId1, Measure(207.00, 215.00), SideCode.BothDirections, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId),
      createAsset(3, linkId1, Measure(240.00, 265.00), SideCode.BothDirections, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId),
      createAsset(4, linkId1, Measure(265.00, 300.00), SideCode.BothDirections, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId)
    )
    val (filledTopology, combineTestChangeSet) = assetFiller.fillTopologyChangesGeometry(roadLinks.map(assetFiller.toRoadLinkForFillTopology),assets.groupBy(_.linkId), RoadWidth.typeId)

    filledTopology.size should be(1)

    filledTopology.head.startMeasure should be(0)
    filledTopology.head.endMeasure should be(400)
    filledTopology.head.value should be(Some(NumericValue(1)))

    val adjustedMValues = combineTestChangeSet.adjustedMValues
    adjustedMValues.sortBy(_.assetId) should be(Seq(
      MValueAdjustment(filledTopology.head.id, linkId1, 0, 400))
    )

    val expiredAssetIds = assets.map(_.id).filterNot(id => id == filledTopology.head.id).sorted.toSet

    combineTestChangeSet.expiredAssetIds.toSeq.sortBy(a=> a).toSet should be (expiredAssetIds)
  }
  test("Fill hole in middle of links and merge similar parts, last part has different value, roadlink long assets") {
    val roadLinks = Seq(
      RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(400, 0.0)), 400, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
        TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    )

    val assets = Seq(
      createAsset(1, linkId1, Measure(0, 200.00), SideCode.BothDirections, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId),
      createAsset(2, linkId1, Measure(207.00, 215.00), SideCode.BothDirections, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId),
      createAsset(3, linkId1, Measure(240.00, 260.00), SideCode.BothDirections, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId),
      createAsset(4, linkId1, Measure(265.00, 300.00), SideCode.BothDirections, Some(NumericValue(3)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId)
    )
    val (filledTopology, combineTestChangeSet) = assetFiller.fillTopologyChangesGeometry(roadLinks.map(assetFiller.toRoadLinkForFillTopology), assets.groupBy(_.linkId), RoadWidth.typeId)

    val sorted = filledTopology.sortBy(_.endMeasure)

    sorted.size should be(2)

    sorted(0).startMeasure should be(0)
    sorted(0).endMeasure should be(260)
    sorted(0).value should be(Some(NumericValue(1)))

    sorted(1).startMeasure should be(265)
    sorted(1).endMeasure should be(400)
    sorted(1).value should be(Some(NumericValue(3)))

    val adjustedMValues = combineTestChangeSet.adjustedMValues
    adjustedMValues.sortBy(_.assetId) should be(Seq(
      MValueAdjustment(1, linkId1, 0, 260),
      MValueAdjustment(4, linkId1, 265, 400)
    ))

    combineTestChangeSet.expiredAssetIds.toSeq.sortBy(a => a).toSet should be(Set(2, 3))
  }

  test("Fill hole in middle of links and merge similar parts, middle part has different value, roadlink long assets") {
    val roadLinks = Seq(
      RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(400, 0.0)), 400, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
        TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    )

    val assets = Seq(
      createAsset(1, linkId1, Measure(0, 200.00), SideCode.BothDirections, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId),
      createAsset(2, linkId1, Measure(207.00, 215.00), SideCode.BothDirections, Some(NumericValue(3)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId),
      createAsset(3, linkId1, Measure(240.00, 260.00), SideCode.BothDirections, Some(NumericValue(3)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId),
      createAsset(4, linkId1, Measure(265.00, 300.00), SideCode.BothDirections, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId)
    )
    val (filledTopology, combineTestChangeSet) = assetFiller.fillTopologyChangesGeometry(roadLinks.map(assetFiller.toRoadLinkForFillTopology), assets.groupBy(_.linkId), RoadWidth.typeId)

    val sorted = filledTopology.sortBy(_.endMeasure)

    sorted.size should be(3)

    sorted(0).startMeasure should be(0)
    sorted(0).endMeasure should be(200)
    sorted(0).value should be(Some(NumericValue(1)))

    sorted(1).startMeasure should be(207)
    sorted(1).endMeasure should be(260)
    sorted(1).value should be(Some(NumericValue(3)))

    sorted(2).startMeasure should be(265)
    sorted(2).endMeasure should be(400)
    sorted(2).value should be(Some(NumericValue(1)))

    val adjustedMValues = combineTestChangeSet.adjustedMValues
    adjustedMValues.sortBy(_.assetId) should be(Seq(
      MValueAdjustment(2, linkId1, 207, 260),
      MValueAdjustment(4, linkId1, 265, 400)
    ))

    combineTestChangeSet.expiredAssetIds.toSeq.sortBy(a => a).toSet should be(Set(3))
  }

  test("Fill hole in middle of links, multiple hole, roadlink long assets, just test fillHole method") {
    val roadLinks = Seq(
      RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(400, 0.0)), 400, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
        TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    )

    val assets = Seq(
      createAsset(1, linkId1, Measure(0, 200.00), SideCode.BothDirections, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId),
      createAsset(2, linkId1, Measure(207.00, 215.00), SideCode.BothDirections, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId),
      createAsset(3, linkId1, Measure(240.00, 265.00), SideCode.BothDirections, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId),
      createAsset(4, linkId1, Measure(265.00, 300.00), SideCode.BothDirections, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId)
    )
    
    val (methodTest, combineTestChangeSet) = assetFiller.fillHolesTwoSteps(roadLinks.map(assetFiller.toRoadLinkForFillTopology).head, assets, initChangeSet)

    val sorted = methodTest.sortBy(_.endMeasure)

    sorted.size should be(4)

    sorted(0).startMeasure should be(0)
    sorted(0).endMeasure should be(207)
    sorted(0).value should be(Some(NumericValue(1)))

    sorted(1).startMeasure should be(207)
    sorted(1).endMeasure should be(240)
    sorted(1).value should be(Some(NumericValue(1)))

    sorted(2).startMeasure should be(240)
    sorted(2).endMeasure should be(265)
    sorted(2).value should be(Some(NumericValue(1)))

    sorted(3).startMeasure should be(265)
    sorted(3).endMeasure should be(300)
    sorted(3).value should be(Some(NumericValue(1)))

    val adjustedMValues = combineTestChangeSet.adjustedMValues
    adjustedMValues.sortBy(_.assetId) should be(Seq(
      MValueAdjustment(1, linkId1, 0, 207),
      MValueAdjustment(2, linkId1, 207, 240)
    ))
  }

  test("Fill hole in middle of links, multiple hole and last part has different value, roadlink long assets") {
    val roadLinks = Seq(
      RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(400, 0.0)), 400, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
        TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    )

    val assets = Seq(
      createAsset(1, linkId1, Measure(0, 200.00), SideCode.BothDirections, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId),
      createAsset(2, linkId1, Measure(207.00, 215.00), SideCode.BothDirections, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId),
      createAsset(3, linkId1, Measure(240.00, 250.00), SideCode.BothDirections, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId),
      createAsset(4, linkId1, Measure(265.00, 300.00), SideCode.BothDirections, Some(NumericValue(3)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId)
    )

    val (methodTest, combineTestChangeSet) = assetFiller.fillHolesTwoSteps(roadLinks.map(assetFiller.toRoadLinkForFillTopology).head, assets, initChangeSet)

    val sorted = methodTest.sortBy(_.endMeasure)

    sorted.size should be(4)

    sorted(0).startMeasure should be(0)
    sorted(0).endMeasure should be(207)
    sorted(0).value should be(Some(NumericValue(1)))

    sorted(1).startMeasure should be(207)
    sorted(1).endMeasure should be(240)
    sorted(1).value should be(Some(NumericValue(1)))

    sorted(2).startMeasure should be(240)
    sorted(2).endMeasure should be(250)
    sorted(2).value should be(Some(NumericValue(1)))

    sorted(3).startMeasure should be(265)
    sorted(3).endMeasure should be(300)
    sorted(3).value should be(Some(NumericValue(3)))

    val adjustedMValues = combineTestChangeSet.adjustedMValues
    adjustedMValues.sortBy(_.assetId) should be(Seq(
      MValueAdjustment(1, linkId1, 0, 207),
      MValueAdjustment(2, linkId1, 207, 240)
    ))
  }


  test("Fill hole in middle of links when on TowardsDigitizing and AgainstDigitizing side has hole, roadlink long assets") {
    val roadLinks = Seq(
      RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(400, 0.0)), 400, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
        TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    )

    val assets = Seq(
      // TowardsDigitizing
      createAsset(1, linkId1, Measure(0, 200.00), SideCode.TowardsDigitizing, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = SpeedLimitAsset.typeId),
      createAsset(2, linkId1, Measure(210, 400.00), SideCode.TowardsDigitizing, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = SpeedLimitAsset.typeId),
      // AgainstDigitizing
      createAsset(3, linkId1, Measure(0, 310.00), SideCode.AgainstDigitizing, Some(NumericValue(2)), TrafficDirection.BothDirections, typeId = SpeedLimitAsset.typeId),
      createAsset(4, linkId1, Measure(320, 400.00), SideCode.AgainstDigitizing, Some(NumericValue(2)), TrafficDirection.BothDirections, typeId = SpeedLimitAsset.typeId)
    )

    val (methodTest, combineTestChangeSet) = assetFiller.fillHolesTwoSteps(roadLinks.map(assetFiller.toRoadLinkForFillTopology).head, assets, initChangeSet)
    
    val sorted =  methodTest.filter(_.sideCode == SideCode.TowardsDigitizing).sortBy(_.endMeasure)
    val sorted2 = methodTest.filter(_.sideCode == SideCode.AgainstDigitizing).sortBy(_.endMeasure)
    
    sorted.size should be(2)
    sorted2.size should be(2)

    sorted(0).startMeasure should be(0)
    sorted(0).endMeasure should be(210)
    sorted(0).value should be(Some(NumericValue(1)))

    sorted(1).startMeasure should be(210)
    sorted(1).endMeasure should be(400)
    sorted(1).value should be(Some(NumericValue(1)))

    sorted2(0).startMeasure should be(0)
    sorted2(0).endMeasure should be(320)
    sorted2(0).value should be(Some(NumericValue(2)))

    sorted2(1).startMeasure should be(320)
    sorted2(1).endMeasure should be(400)
    sorted2(1).value should be(Some(NumericValue(2)))

    val adjustedMValues = combineTestChangeSet.adjustedMValues
    adjustedMValues.sortBy(_.assetId) should be(Seq(
      MValueAdjustment(1, linkId1, 0, 210),
      MValueAdjustment(3, linkId1, 0, 320)
    ))
  }

  test("Fill hole in middle of link and do not fill when AgainstDigitizing side has multiple values, roadlink long assets") {
    val roadLinks = Seq(
      RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(400, 0.0)), 400, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
        TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    )

    val assets = Seq(
      // TowardsDigitizing
      createAsset(1, linkId1, Measure(0, 200.00), SideCode.TowardsDigitizing, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = SpeedLimitAsset.typeId),
      createAsset(2, linkId1, Measure(210, 400.00), SideCode.TowardsDigitizing, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = SpeedLimitAsset.typeId),
      // AgainstDigitizing
      createAsset(3, linkId1, Measure(0, 310.00), SideCode.AgainstDigitizing, Some(NumericValue(2)), TrafficDirection.BothDirections, typeId = SpeedLimitAsset.typeId),
      createAsset(4, linkId1, Measure(320, 400.00), SideCode.AgainstDigitizing, Some(NumericValue(3)), TrafficDirection.BothDirections, typeId = SpeedLimitAsset.typeId)
    )

    val (methodTest, combineTestChangeSet) = assetFiller.fillHolesTwoSteps(roadLinks.map(assetFiller.toRoadLinkForFillTopology).head, assets, initChangeSet)

    val sorted = methodTest.filter(_.sideCode == SideCode.TowardsDigitizing).sortBy(_.endMeasure)
    val sorted2 = methodTest.filter(_.sideCode == SideCode.AgainstDigitizing).sortBy(_.endMeasure)

    sorted.size should be(2)
    sorted2.size should be(2)

    sorted(0).startMeasure should be(0)
    sorted(0).endMeasure should be(210)
    sorted(0).value should be(Some(NumericValue(1)))

    sorted(1).startMeasure should be(210)
    sorted(1).endMeasure should be(400)
    sorted(1).value should be(Some(NumericValue(1)))

    sorted2(0).startMeasure should be(0)
    sorted2(0).endMeasure should be(310)
    sorted2(0).value should be(Some(NumericValue(2)))

    sorted2(1).startMeasure should be(320)
    sorted2(1).endMeasure should be(400)
    sorted2(1).value should be(Some(NumericValue(3)))

    val adjustedMValues = combineTestChangeSet.adjustedMValues
    adjustedMValues.sortBy(_.assetId) should be(Seq(
      MValueAdjustment(1, linkId1, 0, 210)
    ))
  }

  test("Fill hole in middle of links and merge similar parts, side codes are not continues, roadlink long assets") {
    val roadLinks = Seq(
      RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(400, 0.0)), 400, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
        TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    )

    val assets = Seq(
      createAsset(1, linkId1, Measure(0, 200.00), SideCode.TowardsDigitizing, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId),
      createAsset(2, linkId1, Measure(207.00, 215.00), SideCode.BothDirections, Some(NumericValue(3)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId),
      createAsset(3, linkId1, Measure(240.00, 260.00), SideCode.BothDirections, Some(NumericValue(3)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId),
      createAsset(4, linkId1, Measure(265.00, 300.00), SideCode.TowardsDigitizing, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId)
    )
    val (filledTopology, combineTestChangeSet) = assetFiller.fillTopologyChangesGeometry(roadLinks.map(assetFiller.toRoadLinkForFillTopology), assets.groupBy(_.linkId), RoadWidth.typeId)

    val sorted = filledTopology.sortBy(_.endMeasure)

    sorted.size should be(3)

    sorted(0).startMeasure should be(0)
    sorted(0).endMeasure should be(200)
    sorted(0).value should be(Some(NumericValue(1)))

    sorted(1).startMeasure should be(207)
    sorted(1).endMeasure should be(260)
    sorted(1).value should be(Some(NumericValue(3)))

    sorted(2).startMeasure should be(265)
    sorted(2).endMeasure should be(400)
    sorted(2).value should be(Some(NumericValue(1)))

    val adjustedMValues = combineTestChangeSet.adjustedMValues
    adjustedMValues.sortBy(_.assetId) should be(Seq(
      MValueAdjustment(2, linkId1, 207, 260),
      MValueAdjustment(4, linkId1, 265, 400)
    ))

    combineTestChangeSet.expiredAssetIds.toSeq.sortBy(a => a).toSet should be(Set(3))
  }

  test("Fill hole in middle of links and merge similar parts, side codes are not continues, check just fillHoles, roadlink long assets") {
    val roadLinks = Seq(
      RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(400, 0.0)), 400, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
        TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    )

    val assets = Seq(
      createAsset(1, linkId1, Measure(0, 200.00), SideCode.TowardsDigitizing, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId),
      createAsset(2, linkId1, Measure(207.00, 215.00), SideCode.AgainstDigitizing, Some(NumericValue(3)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId),
      createAsset(3, linkId1, Measure(240.00, 260.00), SideCode.AgainstDigitizing, Some(NumericValue(3)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId),
      createAsset(4, linkId1, Measure(265.00, 300.00), SideCode.TowardsDigitizing, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId)
    )
    val (methodTest, combineTestChangeSet) = assetFiller.fillHolesTwoSteps(roadLinks.map(assetFiller.toRoadLinkForFillTopology).head, assets, initChangeSet)

    val sorted = methodTest.sortBy(_.endMeasure)

    sorted.size should be(4)

    sorted(0).startMeasure should be(0)
    sorted(0).endMeasure should be(200)
    sorted(0).value should be(Some(NumericValue(1)))
    sorted(0).sideCode.value should be(SideCode.TowardsDigitizing.value) 

    sorted(1).startMeasure should be(207)
    sorted(1).endMeasure should be(240)
    sorted(1).value should be(Some(NumericValue(3)))
    sorted(1).sideCode.value should be(SideCode.AgainstDigitizing.value)

    sorted(2).startMeasure should be(240)
    sorted(2).endMeasure should be(260)
    sorted(2).value should be(Some(NumericValue(3)))
    sorted(2).sideCode.value should be(SideCode.AgainstDigitizing.value)

    sorted(3).startMeasure should be(265)
    sorted(3).endMeasure should be(300)
    sorted(3).value should be(Some(NumericValue(1)))
    sorted(3).sideCode.value should be(SideCode.TowardsDigitizing.value)

    val adjustedMValues = combineTestChangeSet.adjustedMValues
    adjustedMValues.sortBy(_.assetId) should be(Seq(
      MValueAdjustment(2, linkId1, 207, 240)
    ))
    
  }
  test("Fill hole in middle of links and merge similar parts, hole is only in one side, roadlink long assets2") {
    val roadLinks = Seq(
      RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(400, 0.0)), 400, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
        TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    )

    val assets = Seq(
      createAsset(1, linkId1, Measure(0, 400.00), SideCode.TowardsDigitizing, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId),
      createAsset(2, linkId1, Measure(207.00, 215.00), SideCode.AgainstDigitizing, Some(NumericValue(3)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId),
      createAsset(3, linkId1, Measure(240.00, 400.00), SideCode.AgainstDigitizing, Some(NumericValue(3)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId)
    )
    val (methodTest, combineTestChangeSet) = assetFiller.fillHolesTwoSteps(roadLinks.map(assetFiller.toRoadLinkForFillTopology).head, assets, initChangeSet)
    
    val sorted = methodTest.filter(_.sideCode == SideCode.TowardsDigitizing).sortBy(_.endMeasure)
    val sorted2 = methodTest.filter(_.sideCode == SideCode.AgainstDigitizing).sortBy(_.endMeasure)

    sorted.size should be(1)
    sorted2.size should be(2)

    sorted(0).startMeasure should be(0)
    sorted(0).endMeasure should be(400)
    sorted(0).value should be(Some(NumericValue(1)))
    sorted(0).sideCode.value should be(SideCode.TowardsDigitizing.value)

    sorted2(0).startMeasure should be(207)
    sorted2(0).endMeasure should be(240)
    sorted2(0).value should be(Some(NumericValue(3)))
    sorted2(0).sideCode.value should be(SideCode.AgainstDigitizing.value)

    sorted2(1).startMeasure should be(240)
    sorted2(1).endMeasure should be(400)
    sorted2(1).value should be(Some(NumericValue(3)))

    val adjustedMValues = combineTestChangeSet.adjustedMValues
    adjustedMValues.sortBy(_.assetId) should be(Seq(
      MValueAdjustment(2, linkId1, 207, 240)
    ))

  }

  test("Fill hole in middle of links, side codes are not continues but values are same, roadlink long assets") {
    val roadLinks = Seq(
      RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(400, 0.0)), 400, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
        TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    )

    val assets = Seq(
      createAsset(1, linkId1, Measure(0, 200.00), SideCode.TowardsDigitizing, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId),
      createAsset(2, linkId1, Measure(207.00, 215.00), SideCode.BothDirections, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId),
      createAsset(3, linkId1, Measure(240.00, 260.00), SideCode.BothDirections, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId),
      createAsset(4, linkId1, Measure(265.00, 300.00), SideCode.TowardsDigitizing, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId)
    )
    val (methodTest, combineTestChangeSet) = assetFiller.fillHolesTwoSteps(roadLinks.map(assetFiller.toRoadLinkForFillTopology).head, assets, initChangeSet)

    val sorted = methodTest.sortBy(_.endMeasure)

    sorted.size should be(4)

    sorted(0).startMeasure should be(0)
    sorted(0).endMeasure should be(207)
    sorted(0).value should be(Some(NumericValue(1)))
    sorted(0).sideCode.value should be(SideCode.TowardsDigitizing.value)

    sorted(1).startMeasure should be(207)
    sorted(1).endMeasure should be(240)
    sorted(1).value should be(Some(NumericValue(1)))
    sorted(1).sideCode.value should be(SideCode.BothDirections.value)

    sorted(2).startMeasure should be(240)
    sorted(2).endMeasure should be(265)
    sorted(2).value should be(Some(NumericValue(1)))
    sorted(2).sideCode.value should be(SideCode.BothDirections.value)

    sorted(3).startMeasure should be(265)
    sorted(3).endMeasure should be(300)
    sorted(3).value should be(Some(NumericValue(1)))
    sorted(3).sideCode.value should be(SideCode.TowardsDigitizing.value)

    val adjustedMValues = combineTestChangeSet.adjustedMValues
    adjustedMValues.sortBy(_.assetId) should be(Seq(
      MValueAdjustment(1,linkId1,0.0,207.0,0), MValueAdjustment(2,linkId1,207.0,240.0,0), MValueAdjustment(3,linkId1,240.0,265.0,0)
    ))

  }

  test("Fill hole in middle of links, side codes are not continues but values are same, roadlink long assets, merge similar parts") {
    val roadLinks = Seq(
      RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(400, 0.0)), 400, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
        TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    )

    val assets = Seq(
      createAsset(1, linkId1, Measure(0, 200.00), SideCode.TowardsDigitizing, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId),
      createAsset(2, linkId1, Measure(207.00, 215.00), SideCode.BothDirections, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId),
      createAsset(3, linkId1, Measure(240.00, 260.00), SideCode.BothDirections, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId),
      createAsset(4, linkId1, Measure(265.00, 300.00), SideCode.TowardsDigitizing, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId)
    )
    val (methodTest, combineTestChangeSet) = assetFiller.fillHolesWithFuse(roadLinks.map(assetFiller.toRoadLinkForFillTopology).head, assets, initChangeSet)

    val sorted = methodTest.sortBy(_.endMeasure)

    sorted.size should be(3)

    sorted(0).startMeasure should be(0)
    sorted(0).endMeasure should be(207)
    sorted(0).value should be(Some(NumericValue(1)))
    sorted(0).sideCode.value should be(SideCode.TowardsDigitizing.value)

    sorted(1).startMeasure should be(207)
    sorted(1).endMeasure should be(265)
    sorted(1).value should be(Some(NumericValue(1)))
    sorted(1).sideCode.value should be(SideCode.BothDirections.value)

    sorted(2).startMeasure should be(265)
    sorted(2).endMeasure should be(300)
    sorted(2).value should be(Some(NumericValue(1)))
    sorted(2).sideCode.value should be(SideCode.TowardsDigitizing.value)

    val adjustedMValues = combineTestChangeSet.adjustedMValues
    adjustedMValues.sortBy(_.assetId) should be(Seq(
      MValueAdjustment(1,linkId1,0.0,207.0,0), MValueAdjustment(2,linkId1,207.0,265.0,0)
    ))

  }

  test("Fill hole in middle of links and merge similar parts, side codes are not continues but values are same, roadlink long assets") {
    val roadLinks = Seq(
      RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(400, 0.0)), 400, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
        TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    )

    val assets = Seq(
      createAsset(1, linkId1, Measure(0, 200.00), SideCode.TowardsDigitizing, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId),
      createAsset(2, linkId1, Measure(207.00, 215.00), SideCode.BothDirections, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId),
      createAsset(3, linkId1, Measure(240.00, 260.00), SideCode.BothDirections, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId),
      createAsset(4, linkId1, Measure(265.00, 300.00), SideCode.TowardsDigitizing, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId)
    )
    val (methodTest, combineTestChangeSet) = assetFiller.fillHolesWithFuse(roadLinks.map(assetFiller.toRoadLinkForFillTopology).head, assets, initChangeSet)

    val sorted = methodTest.sortBy(_.endMeasure)

    sorted.size should be(3)

    sorted(0).startMeasure should be(0)
    sorted(0).endMeasure should be(207.0)
    sorted(0).value should be(Some(NumericValue(1)))
    sorted(0).sideCode.value should be(SideCode.TowardsDigitizing.value)

    sorted(1).startMeasure should be(207.0)
    sorted(1).endMeasure should be(265)
    sorted(1).value should be(Some(NumericValue(1)))
    sorted(1).sideCode.value should be(SideCode.BothDirections.value)

    sorted(2).startMeasure should be(265)
    sorted(2).endMeasure should be(300)
    sorted(2).value should be(Some(NumericValue(1)))
    sorted(2).sideCode.value should be(SideCode.TowardsDigitizing.value)

    val adjustedMValues = combineTestChangeSet.adjustedMValues
    adjustedMValues.sortBy(_.assetId) should be(Seq(
      MValueAdjustment(1,linkId1,0.0,207.0,0), MValueAdjustment(2,linkId1,207.0,265.0,0)
    ))

  }

  test("Fill hole in middle of links and merge similar parts, side codes are not continues and values are not same in middle, roadlink long assets") {
    val roadLinks = Seq(
      RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(400, 0.0)), 400, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
        TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    )

    val assets = Seq(
      createAsset(1, linkId1, Measure(0, 200.00), SideCode.TowardsDigitizing, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId),
      createAsset(2, linkId1, Measure(207.00, 215.00), SideCode.BothDirections, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId),
      createAsset(3, linkId1, Measure(240.00, 260.00), SideCode.BothDirections, Some(NumericValue(2)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId),
      createAsset(4, linkId1, Measure(265.00, 300.00), SideCode.TowardsDigitizing, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId)
    )
    val (methodTest, combineTestChangeSet) = assetFiller.fillHolesWithFuse(roadLinks.map(assetFiller.toRoadLinkForFillTopology).head, assets, initChangeSet)

    val sorted = methodTest.sortBy(_.endMeasure)

    sorted.size should be(4)

    sorted(0).startMeasure should be(0)
    sorted(0).endMeasure should be(207.0)
    sorted(0).value should be(Some(NumericValue(1)))
    sorted(0).sideCode.value should be(SideCode.TowardsDigitizing.value)

    sorted(1).startMeasure should be(207.0)
    sorted(1).endMeasure should be(215)
    sorted(1).value should be(Some(NumericValue(1)))
    sorted(1).sideCode.value should be(SideCode.BothDirections.value)

    sorted(2).startMeasure should be(240.0)
    sorted(2).endMeasure should be(260)
    sorted(2).value should be(Some(NumericValue(2)))
    sorted(2).sideCode.value should be(SideCode.BothDirections.value)

    sorted(3).startMeasure should be(265)
    sorted(3).endMeasure should be(300)
    sorted(3).value should be(Some(NumericValue(1)))
    sorted(3).sideCode.value should be(SideCode.TowardsDigitizing.value)

    val adjustedMValues = combineTestChangeSet.adjustedMValues
    adjustedMValues.sortBy(_.assetId) should be(Seq(
      MValueAdjustment(1,linkId1,0.0,207.0,0)
    ))

  }

  test("There is small hole, side codes and value different in roadlink long assets") {
    val roadLinks = Seq(
      RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(733.973, 0.0)), 733.973, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
        TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    )

    val assets = Seq(
      createAsset(1, linkId1, Measure(0, 41.093), SideCode.BothDirections, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId),
      createAsset(2, linkId1, Measure(41.28, 173.96), SideCode.AgainstDigitizing, Some(NumericValue(2)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId),
      createAsset(3, linkId1, Measure(41.28, 173.96), SideCode.TowardsDigitizing, Some(NumericValue(3)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId),
      createAsset(4, linkId1, Measure(173.96, 733.973), SideCode.BothDirections, Some(NumericValue(4)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId)
    )
    val (methodTest, combineTestChangeSet) = assetFiller.fillHolesWithFuse(roadLinks.map(assetFiller.toRoadLinkForFillTopology).head, assets, initChangeSet)

    val sorted = methodTest.sortBy(_.endMeasure)

    sorted.size should be(4)

    sorted.head.sideCode should be(SideCode.BothDirections)
    sorted.head.startMeasure should be(0)
    sorted.head.endMeasure should be(41.28)

    sorted.filter(_.sideCode == SideCode.AgainstDigitizing).head.startMeasure should be(41.28)
    sorted.filter(_.sideCode == SideCode.AgainstDigitizing).head.endMeasure should be(173.96)

    sorted.filter(_.sideCode == SideCode.TowardsDigitizing).head.startMeasure should be(41.28)
    sorted.filter(_.sideCode == SideCode.TowardsDigitizing).head.endMeasure should be(173.96)

    sorted.last.sideCode should be(SideCode.BothDirections)
    sorted.last.startMeasure should be(173.96)
    sorted.last.endMeasure should be(733.973)

  }

  test("There is big hole, side codes and value different in roadlink long assets") {
    val roadLinks = Seq(
      RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(733.973, 0.0)), 733.973, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
        TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    )

    val assets = Seq(
      createAsset(1, linkId1, Measure(0, 41.093), SideCode.BothDirections, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId),
      createAsset(2, linkId1, Measure(43.28, 173.96), SideCode.AgainstDigitizing, Some(NumericValue(2)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId),
      createAsset(3, linkId1, Measure(43.28, 173.96), SideCode.TowardsDigitizing, Some(NumericValue(3)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId),
      createAsset(4, linkId1, Measure(173.96, 733.973), SideCode.BothDirections, Some(NumericValue(4)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId)
    )
    val (methodTest, combineTestChangeSet) = assetFiller.fillHolesWithFuse(roadLinks.map(assetFiller.toRoadLinkForFillTopology).head, assets, initChangeSet)

    val sorted = methodTest.sortBy(_.endMeasure)

    sorted.size should be(4)

    sorted.head.sideCode should be(SideCode.BothDirections)
    sorted.head.startMeasure should be(0)
    sorted.head.endMeasure should be(41.093)

    sorted.filter(_.sideCode == SideCode.AgainstDigitizing).head.startMeasure should be(43.28)
    sorted.filter(_.sideCode == SideCode.AgainstDigitizing).head.endMeasure should be(173.96)

    sorted.filter(_.sideCode == SideCode.TowardsDigitizing).head.startMeasure should be(43.28)
    sorted.filter(_.sideCode == SideCode.TowardsDigitizing).head.endMeasure should be(173.96)

    sorted.last.sideCode should be(SideCode.BothDirections)
    sorted.last.startMeasure should be(173.96)
    sorted.last.endMeasure should be(733.973)

  }

  test("There is small hole, side codes and value different in roadlink long assets, fillTopologyChangesGeometry") {
    val roadLinks = Seq(
      RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(733.973, 0.0)), 733.973, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
        TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    )

    val assets = Seq(
      createAsset(1, linkId1, Measure(0, 41.093), SideCode.BothDirections, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId),
      createAsset(2, linkId1, Measure(41.28, 173.96), SideCode.AgainstDigitizing, Some(NumericValue(2)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId),
      createAsset(3, linkId1, Measure(41.28, 173.96), SideCode.TowardsDigitizing, Some(NumericValue(3)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId),
      createAsset(4, linkId1, Measure(173.96, 733.973), SideCode.BothDirections, Some(NumericValue(4)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId)
    )
    val (methodTest, combineTestChangeSet) = assetFiller.fillTopologyChangesGeometry(roadLinks.map(assetFiller.toRoadLinkForFillTopology), assets.groupBy(_.linkId), RoadWidth.typeId)

    val sorted = methodTest.sortBy(_.endMeasure)

    sorted.size should be(4)

    sorted.head.sideCode should be(SideCode.BothDirections)
    sorted.head.startMeasure should be(0)
    sorted.head.endMeasure should be(41.28)

    sorted.filter(_.sideCode == SideCode.AgainstDigitizing).head.startMeasure should be(41.28)
    sorted.filter(_.sideCode == SideCode.AgainstDigitizing).head.endMeasure should be(173.96)

    sorted.filter(_.sideCode == SideCode.TowardsDigitizing).head.startMeasure should be(41.28)
    sorted.filter(_.sideCode == SideCode.TowardsDigitizing).head.endMeasure should be(173.96)

    sorted.last.sideCode should be(SideCode.BothDirections)
    sorted.last.startMeasure should be(173.96)
    sorted.last.endMeasure should be(733.973)
  }

  test("Fill hole in middle of link, different value each sides, roadlink long assets") {
    val roadLinks = Seq(
      RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(400, 0.0)), 400, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
        TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    )

    val assets = Seq(
      // TowardsDigitizing
      createAsset(1, linkId1, Measure(0, 200.00), SideCode.TowardsDigitizing, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = SpeedLimitAsset.typeId),
      createAsset(2, linkId1, Measure(210, 400.00), SideCode.TowardsDigitizing, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = SpeedLimitAsset.typeId),
      // AgainstDigitizing
      createAsset(3, linkId1, Measure(0, 200.00), SideCode.AgainstDigitizing, Some(NumericValue(3)), TrafficDirection.BothDirections, typeId = SpeedLimitAsset.typeId),
      createAsset(4, linkId1, Measure(210, 400.00), SideCode.AgainstDigitizing, Some(NumericValue(3)), TrafficDirection.BothDirections, typeId = SpeedLimitAsset.typeId)
    )

    val (methodTest, combineTestChangeSet) = assetFiller.fillHolesTwoSteps(roadLinks.map(assetFiller.toRoadLinkForFillTopology).head, assets, initChangeSet)

    val sorted = methodTest.filter(_.sideCode == SideCode.TowardsDigitizing).sortBy(_.endMeasure)
    val sorted2 = methodTest.filter(_.sideCode == SideCode.AgainstDigitizing).sortBy(_.endMeasure)

    sorted.size should be(2)
    sorted2.size should be(2)

    sorted(0).startMeasure should be(0)
    sorted(0).endMeasure should be(210)
    sorted(0).value should be(Some(NumericValue(1)))

    sorted(1).startMeasure should be(210)
    sorted(1).endMeasure should be(400)
    sorted(1).value should be(Some(NumericValue(1)))

    sorted2(0).startMeasure should be(0)
    sorted2(0).endMeasure should be(210)
    sorted2(0).value should be(Some(NumericValue(3)))

    sorted2(1).startMeasure should be(210)
    sorted2(1).endMeasure should be(400)
    sorted2(1).value should be(Some(NumericValue(3)))

    val adjustedMValues = combineTestChangeSet.adjustedMValues
    adjustedMValues.sortBy(_.assetId) should be(Seq(
      MValueAdjustment(1, linkId1, 0, 210),
      MValueAdjustment(3, linkId1, 0, 210)
    ))
  }

  test("Fill hole in middle of link, different value each sides, roadlink long assets, finally merge") {
    val roadLinks = Seq(
      RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(400, 0.0)), 400, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
        TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    )

    val assets = Seq(
      // TowardsDigitizing
      createAsset(1, linkId1, Measure(0, 200.00), SideCode.TowardsDigitizing, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = SpeedLimitAsset.typeId),
      createAsset(2, linkId1, Measure(210, 400.00), SideCode.TowardsDigitizing, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = SpeedLimitAsset.typeId),
      // AgainstDigitizing
      createAsset(3, linkId1, Measure(0, 200.00), SideCode.AgainstDigitizing, Some(NumericValue(3)), TrafficDirection.BothDirections, typeId = SpeedLimitAsset.typeId),
      createAsset(4, linkId1, Measure(210, 400.00), SideCode.AgainstDigitizing, Some(NumericValue(3)), TrafficDirection.BothDirections, typeId = SpeedLimitAsset.typeId)
    )

    val (methodTest, combineTestChangeSet) = assetFiller.fillHolesWithFuse(roadLinks.map(assetFiller.toRoadLinkForFillTopology).head, assets, initChangeSet)

    val sorted = methodTest.filter(_.sideCode == SideCode.TowardsDigitizing).sortBy(_.endMeasure)
    val sorted2 = methodTest.filter(_.sideCode == SideCode.AgainstDigitizing).sortBy(_.endMeasure)

    sorted.size should be(1)
    sorted2.size should be(1)

    sorted(0).startMeasure should be(0)
    sorted(0).endMeasure should be(400)
    sorted(0).value should be(Some(NumericValue(1)))

    sorted2(0).startMeasure should be(0)
    sorted2(0).endMeasure should be(400)
    sorted2(0).value should be(Some(NumericValue(3)))

    val adjustedMValues = combineTestChangeSet.adjustedMValues
    adjustedMValues.sortBy(_.assetId) should be(Seq(
      MValueAdjustment(1, linkId1, 0, 400),
      MValueAdjustment(3, linkId1, 0, 400)
    ))
  }
  
  test("Fill hole in middle of links when AgainstDigitizing side has multiple values, roadlink long assets") {
    val roadLinks = Seq(
      RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(400, 0.0)), 400, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
        TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    )

    val assets = Seq(
      // TowardsDigitizing
      createAsset(1, linkId1, Measure(0, 200.00), SideCode.TowardsDigitizing, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = SpeedLimitAsset.typeId),
      createAsset(2, linkId1, Measure(210, 400.00), SideCode.TowardsDigitizing, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = SpeedLimitAsset.typeId),
      // AgainstDigitizing
      createAsset(3, linkId1, Measure(0, 310.00), SideCode.AgainstDigitizing, Some(NumericValue(2)), TrafficDirection.BothDirections, typeId = SpeedLimitAsset.typeId),
      createAsset(4, linkId1, Measure(320, 360.00), SideCode.AgainstDigitizing, Some(NumericValue(3)), TrafficDirection.BothDirections, typeId = SpeedLimitAsset.typeId),
      createAsset(5, linkId1, Measure(370, 400.00), SideCode.AgainstDigitizing, Some(NumericValue(3)), TrafficDirection.BothDirections, typeId = SpeedLimitAsset.typeId)
    )

    val (methodTest, combineTestChangeSet) = assetFiller.fillHolesTwoSteps(roadLinks.map(assetFiller.toRoadLinkForFillTopology).head, assets, initChangeSet)

    val sorted = methodTest.filter(_.sideCode == SideCode.TowardsDigitizing).sortBy(_.endMeasure)
    val sorted2 = methodTest.filter(_.sideCode == SideCode.AgainstDigitizing).sortBy(_.endMeasure)

    sorted.size should be(2)
    sorted2.size should be(3)

    sorted(0).startMeasure should be(0)
    sorted(0).endMeasure should be(210)
    sorted(0).value should be(Some(NumericValue(1)))

    sorted(1).startMeasure should be(210)
    sorted(1).endMeasure should be(400)
    sorted(1).value should be(Some(NumericValue(1)))

    sorted2(0).startMeasure should be(0)
    sorted2(0).endMeasure should be(310)
    sorted2(0).value should be(Some(NumericValue(2)))

    sorted2(1).startMeasure should be(320)
    sorted2(1).endMeasure should be(370)
    sorted2(1).value should be(Some(NumericValue(3)))

    sorted2(2).startMeasure should be(370)
    sorted2(2).endMeasure should be(400)
    sorted2(2).value should be(Some(NumericValue(3)))

    val adjustedMValues = combineTestChangeSet.adjustedMValues
    adjustedMValues.sortBy(_.assetId) should be(Seq(
      MValueAdjustment(1, linkId1, 0, 210),
      MValueAdjustment(4, linkId1, 320, 370)
    ))
  }

  test("Fill hole in middle of links when AgainstDigitizing side has multiple values, roadlink long assets, finally Merge") {
    val roadLinks = Seq(
      RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(400, 0.0)), 400, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
        TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    )

    val assets = Seq(
      // TowardsDigitizing
      createAsset(1, linkId1, Measure(0, 200.00), SideCode.TowardsDigitizing, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = SpeedLimitAsset.typeId),
      createAsset(2, linkId1, Measure(210, 400.00), SideCode.TowardsDigitizing, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = SpeedLimitAsset.typeId),
      // AgainstDigitizing
      createAsset(3, linkId1, Measure(0, 310.00), SideCode.AgainstDigitizing, Some(NumericValue(2)), TrafficDirection.BothDirections, typeId = SpeedLimitAsset.typeId),
      createAsset(4, linkId1, Measure(320, 360.00), SideCode.AgainstDigitizing, Some(NumericValue(3)), TrafficDirection.BothDirections, typeId = SpeedLimitAsset.typeId),
      createAsset(5, linkId1, Measure(370, 400.00), SideCode.AgainstDigitizing, Some(NumericValue(3)), TrafficDirection.BothDirections, typeId = SpeedLimitAsset.typeId)
    )

    val (methodTest, combineTestChangeSet) = assetFiller.fillHolesWithFuse(roadLinks.map(assetFiller.toRoadLinkForFillTopology).head, assets, initChangeSet)

    val sorted = methodTest.filter(_.sideCode == SideCode.TowardsDigitizing).sortBy(_.endMeasure)
    val sorted2 = methodTest.filter(_.sideCode == SideCode.AgainstDigitizing).sortBy(_.endMeasure)

    sorted.size should be(1)
    sorted2.size should be(2)

    sorted(0).startMeasure should be(0)
    sorted(0).endMeasure should be(400)
    sorted(0).value should be(Some(NumericValue(1)))
    

    sorted2(0).startMeasure should be(0)
    sorted2(0).endMeasure should be(310)
    sorted2(0).value should be(Some(NumericValue(2)))

    sorted2(1).startMeasure should be(320)
    sorted2(1).endMeasure should be(400)
    sorted2(1).value should be(Some(NumericValue(3)))

    val adjustedMValues = combineTestChangeSet.adjustedMValues
    adjustedMValues.sortBy(_.assetId) should be(Seq(
      MValueAdjustment(1, linkId1, 0, 400),
      MValueAdjustment(4, linkId1, 320, 400)
    ))
  }
  
  test("Do not fill hole if values are different either side of hole, roadlink lenth assets") {
    val roadLinks = Seq(
      RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(223.872, 0.0)), 223.872, AdministrativeClass.apply(1), UnknownFunctionalClass.value,
        TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())
    )

    val assets = Seq(
      createAsset(1, linkId1, Measure(0, 200.303), SideCode.BothDirections, Some(NumericValue(1)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId),
      createAsset(2, linkId1, Measure(207.304, 215.304), SideCode.BothDirections, Some(NumericValue(3)), TrafficDirection.BothDirections, typeId = RoadWidth.typeId)
    )

    val (methodTest, combineTestChangeSet) = assetFiller.fillHolesWithFuse(roadLinks.map(assetFiller.toRoadLinkForFillTopology).head, assets, initChangeSet)

    val sorted = methodTest.sortBy(_.endMeasure)

    sorted.size should be(2)

    sorted(0).startMeasure should be(0)
    sorted(0).endMeasure should be(200.303)
    sorted(0).value should be(Some(NumericValue(1)))

    sorted(1).startMeasure should be(207.304)
    sorted(1).endMeasure should be(215.304)
    sorted(1).value should be(Some(NumericValue(3)))

    val adjustedMValues = combineTestChangeSet.adjustedMValues
    adjustedMValues.size should be (0)

  }
  
  private def makeAssetsList(linkId: String) = {
    Seq(
      (18083292,linkId,1,None,0,85.545,0,"04.05.2016 14:32:37,294105000"),
      (18083302,linkId,1,None,0,85.472,0,"04.05.2016 14:32:37,321328000"),
      (18083410,linkId,1,None,0,85.545,0,"04.05.2016 14:32:37,598152000"),
      (18083420,linkId,1,None,0,85.472,0,"04.05.2016 14:32:37,624683000"),
      (18116162,linkId,1,None,0,85.545,0,"04.05.2016 14:34:00,372695000"),
      (18116172,linkId,1,None,0,85.472,0,"04.05.2016 14:34:00,395510000"),
      (18116266,linkId,1,None,0,85.545,0,"04.05.2016 14:34:00,597215000"),
      (18116357,linkId,1,None,0,85.618,0,"04.05.2016 14:34:00,879024000"),
      (18116367,linkId,1,None,0,85.545,0,"04.05.2016 14:34:00,900251000"),
      (18088733,linkId,1,None,0,85.472,0,"04.05.2016 14:32:51,480673000"),
      (18088743,linkId,1,None,0,85.399,0,"04.05.2016 14:32:51,504387000"),
      (18088833,linkId,1,None,0,85.545,0,"04.05.2016 14:32:51,740964000"),
      (18088843,linkId,1,None,0,85.472,0,"04.05.2016 14:32:51,762947000"),
      (18088929,linkId,1,None,0,85.545,0,"04.05.2016 14:32:52,025372000"),
      (18088939,linkId,1,None,0,85.472,0,"04.05.2016 14:32:52,047985000"),
      (18100314,linkId,1,None,0,85.545,0,"04.05.2016 14:33:19,084481000"),
      (18100324,linkId,1,None,0,85.472,0,"04.05.2016 14:33:19,107717000"),
      (18100422,linkId,1,None,0,85.618,0,"04.05.2016 14:33:19,350112000"),
      (18100432,linkId,1,None,0,85.545,0,"04.05.2016 14:33:19,374252000"),
      (18104278,linkId,1,None,0,85.472,0,"04.05.2016 14:33:29,181521000"),
      (18104288,linkId,1,None,0,85.399,0,"04.05.2016 14:33:29,202715000"),
      (18108314,linkId,1,None,0,85.545,0,"04.05.2016 14:33:38,909732000"),
      (18108324,linkId,1,None,0,85.472,0,"04.05.2016 14:33:38,932204000"),
      (18108414,linkId,1,None,0,85.618,0,"04.05.2016 14:33:39,127050000"),
      (18108424,linkId,1,None,0,85.545,0,"04.05.2016 14:33:39,148784000"),
      (18108510,linkId,1,None,0,85.618,0,"04.05.2016 14:33:39,332110000"),
      (18110169,linkId,1,None,0,85.691,0,"04.05.2016 14:33:43,757209000"),
      (18110179,linkId,1,None,0,85.618,0,"04.05.2016 14:33:43,778582000"),
      (18110392,linkId,1,None,0,85.545,0,"04.05.2016 14:33:44,435067000"),
      (18110403,linkId,1,None,0,85.472,0,"04.05.2016 14:33:44,474105000"),
      (18095576,linkId,1,None,0,85.545,0,"04.05.2016 14:33:08,156219000"),
      (18095586,linkId,1,None,0,85.472,0,"04.05.2016 14:33:08,179633000"),
      (18095676,linkId,1,None,0,85.545,0,"04.05.2016 14:33:08,375868000"),
      (18095686,linkId,1,None,0,85.472,0,"04.05.2016 14:33:08,406218000"),
      (18113289,linkId,1,None,0,85.618,0,"04.05.2016 14:33:52,298744000"),
      (18113299,linkId,1,None,0,85.545,0,"04.05.2016 14:33:52,322262000"),
      (18113386,linkId,1,None,0,85.618,0,"04.05.2016 14:33:52,628802000"),
      (18113396,linkId,1,None,0,85.545,0,"04.05.2016 14:33:52,657721000"),
      (18113496,linkId,1,None,0,85.618,0,"04.05.2016 14:33:52,868395000"),
      (18113506,linkId,1,None,0,85.545,0,"04.05.2016 14:33:52,889889000"),
      (18116455,linkId,1,None,0,85.691,0,"04.05.2016 14:34:01,113740000"),
      (18116465,linkId,1,None,0,85.618,0,"04.05.2016 14:34:01,137544000"),
      (18116549,linkId,1,None,0,85.691,0,"04.05.2016 14:34:01,330175000"),
      (18116559,linkId,1,None,0,85.618,0,"04.05.2016 14:34:01,356920000"),
      (18089026,linkId,1,None,0,85.618,0,"04.05.2016 14:32:52,251967000"),
      (18089036,linkId,1,None,0,85.545,0,"04.05.2016 14:32:52,276910000"),
      (18097591,linkId,1,None,0,85.399,0,"04.05.2016 14:33:12,695338000"),
      (18104378,linkId,1,None,0,85.545,0,"04.05.2016 14:33:29,398479000"),
      (18104388,linkId,1,None,0,85.472,0,"04.05.2016 14:33:29,420256000"),
      (18104474,linkId,1,None,0,85.545,0,"04.05.2016 14:33:29,599962000"),
      (18104484,linkId,1,None,0,85.472,0,"04.05.2016 14:33:29,621285000"),
      (18104571,linkId,1,None,0,85.618,0,"04.05.2016 14:33:29,907514000"),
      (18104581,linkId,1,None,0,85.545,0,"04.05.2016 14:33:29,929864000"),
      (18108521,linkId,1,None,0,85.545,0,"04.05.2016 14:33:39,362341000"),
      (18108605,linkId,1,None,0,85.691,0,"04.05.2016 14:33:39,574920000"),
      (18108615,linkId,1,None,0,85.618,0,"04.05.2016 14:33:39,596652000"),
      (18110489,linkId,1,None,0,85.618,0,"04.05.2016 14:33:44,862098000"),
      (18110499,linkId,1,None,0,85.545,0,"04.05.2016 14:33:44,991041000"),
      (18110586,linkId,1,None,0,85.545,0,"04.05.2016 14:33:45,316189000"),
      (18110596,linkId,1,None,0,85.472,0,"04.05.2016 14:33:45,339087000"),
      (18110686,linkId,1,None,0,85.618,0,"04.05.2016 14:33:45,539095000"),
      (18110697,linkId,1,None,0,85.545,0,"04.05.2016 14:33:45,648053000"),
      (18095773,linkId,1,None,0,85.618,0,"04.05.2016 14:33:08,608873000"),
      (18095783,linkId,1,None,0,85.545,0,"04.05.2016 14:33:08,630669000"),
      (18095869,linkId,1,None,0,85.545,0,"04.05.2016 14:33:08,818292000"),
      (18095879,linkId,1,None,0,85.472,0,"04.05.2016 14:33:08,842189000"),
      (18095969,linkId,1,None,0,85.618,0,"04.05.2016 14:33:09,038384000"),
      (18095980,linkId,1,None,0,85.545,0,"04.05.2016 14:33:09,062307000"),
      (18112994,linkId,1,None,0,85.618,0,"04.05.2016 14:33:51,517163000"),
      (18113004,linkId,1,None,0,85.545,0,"04.05.2016 14:33:51,540468000"),
      (18113088,linkId,1,None,0,85.691,0,"04.05.2016 14:33:51,746267000"),
      (18113098,linkId,1,None,0,85.618,0,"04.05.2016 14:33:51,769794000"),
      (18113189,linkId,1,None,0,85.545,0,"04.05.2016 14:33:52,068287000"),
      (18113199,linkId,1,None,0,85.472,0,"04.05.2016 14:33:52,089633000"),
      (18113590,linkId,1,None,0,85.691,0,"04.05.2016 14:33:53,093850000"),
      (18113600,linkId,1,None,0,85.618,0,"04.05.2016 14:33:53,115195000"),
      (18097716,linkId,1,None,0,85.472,0,"04.05.2016 14:33:12,972516000"),
      (18097726,linkId,1,None,0,85.399,0,"04.05.2016 14:33:12,995363000"),
      (18097828,linkId,1,None,0,85.545,0,"04.05.2016 14:33:13,251951000"),
      (18097838,linkId,1,None,0,85.472,0,"04.05.2016 14:33:13,273916000"),
      (18097947,linkId,1,None,0,85.545,0,"04.05.2016 14:33:13,526303000"),
      (18097957,linkId,1,None,0,85.472,0,"04.05.2016 14:33:13,550084000"),
      (18101077,linkId,1,None,0,85.399,0,"04.05.2016 14:33:20,949363000"),
      (18104673,linkId,1,None,0,85.327,0,"04.05.2016 14:33:30,152687000"),
      (18104764,linkId,1,None,0,85.472,0,"04.05.2016 14:33:30,488304000"),
      (18104774,linkId,1,None,0,85.399,0,"04.05.2016 14:33:30,510131000"),
      (18104868,linkId,1,None,0,85.472,0,"04.05.2016 14:33:30,729555000"),
      (18093187,linkId,1,None,0,85.399,0,"04.05.2016 14:33:02,676768000"),
      (18093278,linkId,1,None,0,85.472,0,"04.05.2016 14:33:02,883259000"),
      (18093288,linkId,1,None,0,85.399,0,"04.05.2016 14:33:02,906669000"),
      (18096066,linkId,1,None,0,85.618,0,"04.05.2016 14:33:09,267470000"),
      (18096076,linkId,1,None,0,85.545,0,"04.05.2016 14:33:09,291838000"),
      (18083519,linkId,1,None,0,85.618,0,"04.05.2016 14:32:37,916210000"),
      (18083529,linkId,1,None,0,85.545,0,"04.05.2016 14:32:37,939781000"),
      (18083539,linkId,1,None,0,85.327,0,"04.05.2016 14:32:37,964926000"),
      (18083641,linkId,1,None,0,85.472,0,"04.05.2016 14:32:38,223678000"),
      (18083651,linkId,1,None,0,85.399,0,"04.05.2016 14:32:38,246850000"),
      (18104959,linkId,1,None,0,85.545,0,"04.05.2016 14:33:31,021034000"),
      (18104969,linkId,1,None,0,85.472,0,"04.05.2016 14:33:31,046063000"),
      (18105059,linkId,1,None,0,85.618,0,"04.05.2016 14:33:31,261130000"),
      (18105069,linkId,1,None,0,85.545,0,"04.05.2016 14:33:31,286566000"),
      (18105155,linkId,1,None,0,85.618,0,"04.05.2016 14:33:31,551582000"),
      (18105165,linkId,1,None,0,85.545,0,"04.05.2016 14:33:31,572779000"),
      (18083764,linkId,1,None,0,85.472,0,"04.05.2016 14:32:38,543091000"),
      (18083774,linkId,1,None,0,85.399,0,"04.05.2016 14:32:38,567032000"),
      (18083872,linkId,1,None,0,85.545,0,"04.05.2016 14:32:38,801752000"),
      (18083882,linkId,1,None,0,85.472,0,"04.05.2016 14:32:38,825025000"),
      (18083968,linkId,1,None,0,85.472,0,"04.05.2016 14:32:39,106161000"),
      (18083978,linkId,1,None,0,85.399,0,"04.05.2016 14:32:39,129966000"),
      (18070511,linkId,1,None,0,85.327,0,"04.05.2016 12:13:34,780316000"),
      (18089671,linkId,1,None,0,85.472,0,"04.05.2016 14:32:54,075251000"),
      (18089681,linkId,1,None,0,85.399,0,"04.05.2016 14:32:54,097594000"),
      (18089783,linkId,1,None,0,85.545,0,"04.05.2016 14:32:54,329385000"),
      (18089793,linkId,1,None,0,85.472,0,"04.05.2016 14:32:54,352883000"),
      (18101167,linkId,1,None,0,85.545,0,"04.05.2016 14:33:21,146233000"),
      (18101177,linkId,1,None,0,85.472,0,"04.05.2016 14:33:21,169146000"),
      (18105250,linkId,1,None,0,85.691,0,"04.05.2016 14:33:31,774098000"),
      (18105260,linkId,1,None,0,85.618,0,"04.05.2016 14:33:31,797724000"),
      (18105270,linkId,1,None,0,85.399,0,"04.05.2016 14:33:31,825349000"),
      (18084069,linkId,1,None,0,85.545,0,"04.05.2016 14:32:39,367565000"),
      (18084079,linkId,1,None,0,85.472,0,"04.05.2016 14:32:39,391009000"),
      (18084165,linkId,1,None,0,85.545,0,"04.05.2016 14:32:39,621852000"),
      (18084175,linkId,1,None,0,85.472,0,"04.05.2016 14:32:39,644690000"),
      (18084262,linkId,1,None,0,85.618,0,"04.05.2016 14:32:39,894894000"),
      (18084272,linkId,1,None,0,85.545,0,"04.05.2016 14:32:39,923043000"),
      (18070689,linkId,1,None,0,85.399,0,"04.05.2016 12:14:12,476116000"),
      (18070700,linkId,1,None,0,85.327,0,"04.05.2016 12:14:12,502899000"),
      (18079383,linkId,1,None,0,85.399,0,"04.05.2016 14:32:15,651628000"),
      (18079393,linkId,1,None,0,85.327,0,"04.05.2016 14:32:15,701297000"),
      (18079495,linkId,1,None,0,85.472,0,"04.05.2016 14:32:16,090261000"),
      (18079505,linkId,1,None,0,85.399,0,"04.05.2016 14:32:16,119956000"),
      (18079613,linkId,1,None,0,85.472,0,"04.05.2016 14:32:16,454580000"),
      (18089902,linkId,1,None,0,85.545,0,"04.05.2016 14:32:54,614898000"),
      (18089912,linkId,1,None,0,85.472,0,"04.05.2016 14:32:54,639063000"),
      (18090010,linkId,1,None,0,85.618,0,"04.05.2016 14:32:54,886514000"),
      (18090020,linkId,1,None,0,85.545,0,"04.05.2016 14:32:54,912846000"),
      (18070867,linkId,1,None,0,85.399,0,"04.05.2016 12:14:56,599938000"),
      (18101601,linkId,1,None,0,85.618,0,"04.05.2016 14:33:22,225243000"),
      (18084394,linkId,1,None,0,85.472,0,"04.05.2016 14:32:40,266093000"),
      (18084404,linkId,1,None,0,85.399,0,"04.05.2016 14:32:40,298127000"),
      (18084507,linkId,1,None,0,85.545,0,"04.05.2016 14:32:40,649121000"),
      (18084517,linkId,1,None,0,85.472,0,"04.05.2016 14:32:40,674922000"),
      (18079623,linkId,1,None,0,85.399,0,"04.05.2016 14:32:16,485462000"),
      (18079721,linkId,1,None,0,85.545,0,"04.05.2016 14:32:16,766620000"),
      (18079731,linkId,1,None,0,85.472,0,"04.05.2016 14:32:16,794372000"),
      (18081451,linkId,1,None,0,85.399,0,"04.05.2016 14:32:31,716524000"),
      (18070877,linkId,1,None,0,85.327,0,"04.05.2016 12:14:56,655809000"),
      (18070975,linkId,1,None,0,85.472,0,"04.05.2016 12:14:56,944500000"),
      (18070985,linkId,1,None,0,85.399,0,"04.05.2016 12:14:56,970281000"),
      (18101611,linkId,1,None,0,85.545,0,"04.05.2016 14:33:22,269387000"),
      (18101695,linkId,1,None,0,85.691,0,"04.05.2016 14:33:22,458014000"),
      (18101705,linkId,1,None,0,85.618,0,"04.05.2016 14:33:22,481337000"),
      (18105977,linkId,1,None,0,85.472,0,"04.05.2016 14:33:33,759059000"),
      (18105987,linkId,1,None,0,85.399,0,"04.05.2016 14:33:33,781145000"),
      (18084625,linkId,1,None,0,85.545,0,"04.05.2016 14:32:40,974522000"),
      (18084635,linkId,1,None,0,85.472,0,"04.05.2016 14:32:40,999042000"),
      (18084734,linkId,1,None,0,85.618,0,"04.05.2016 14:32:41,225169000"),
      (18084744,linkId,1,None,0,85.545,0,"04.05.2016 14:32:41,248414000"),
      (18081541,linkId,1,None,0,85.472,0,"04.05.2016 14:32:32,012561000"),
      (18081551,linkId,1,None,0,85.399,0,"04.05.2016 14:32:32,038437000"),
      (18081641,linkId,1,None,0,85.545,0,"04.05.2016 14:32:32,358911000"),
      (18081651,linkId,1,None,0,85.472,0,"04.05.2016 14:32:32,386040000"),
      (18081737,linkId,1,None,0,85.545,0,"04.05.2016 14:32:32,613244000"),
      (18081747,linkId,1,None,0,85.472,0,"04.05.2016 14:32:32,639975000"),
      (18098055,linkId,1,None,0,85.618,0,"04.05.2016 14:33:13,795694000"),
      (18098065,linkId,1,None,0,85.545,0,"04.05.2016 14:33:13,821401000"),
      (18098157,linkId,1,None,0,85.327,0,"04.05.2016 14:33:14,028900000"),
      (18098260,linkId,1,None,0,85.472,0,"04.05.2016 14:33:14,283962000"),
      (18098270,linkId,1,None,0,85.399,0,"04.05.2016 14:33:14,304989000"),
      (18106073,linkId,1,None,0,85.545,0,"04.05.2016 14:33:33,974380000"),
      (18106084,linkId,1,None,0,85.472,0,"04.05.2016 14:33:33,996270000"),
      (18106170,linkId,1,None,0,85.472,0,"04.05.2016 14:33:34,199966000"),
      (18106180,linkId,1,None,0,85.399,0,"04.05.2016 14:33:34,226580000"),
      (18106270,linkId,1,None,0,85.545,0,"04.05.2016 14:33:34,445508000"),
      (18106280,linkId,1,None,0,85.472,0,"04.05.2016 14:33:34,467275000"),
      (18093378,linkId,1,None,0,85.545,0,"04.05.2016 14:33:03,117196000"),
      (18093388,linkId,1,None,0,85.472,0,"04.05.2016 14:33:03,140517000"),
      (18093475,linkId,1,None,0,85.545,0,"04.05.2016 14:33:03,357902000"),
      (18093485,linkId,1,None,0,85.472,0,"04.05.2016 14:33:03,378845000"),
      (18114020,linkId,1,None,0,85.472,0,"04.05.2016 14:33:54,245891000"),
      (18114110,linkId,1,None,0,85.545,0,"04.05.2016 14:33:54,452251000"),
      (18114120,linkId,1,None,0,85.472,0,"04.05.2016 14:33:54,475868000"),
      (18081833,linkId,1,None,0,85.618,0,"04.05.2016 14:32:32,927915000"),
      (18081843,linkId,1,None,0,85.545,0,"04.05.2016 14:32:32,957308000"),
      (18081853,linkId,1,None,0,85.327,0,"04.05.2016 14:32:32,999162000"),
      (18081943,linkId,1,None,0,85.472,0,"04.05.2016 14:32:33,292163000"),
      (18081953,linkId,1,None,0,85.399,0,"04.05.2016 14:32:33,319084000"),
      (18082043,linkId,1,None,0,85.472,0,"04.05.2016 14:32:33,630804000"),
      (18082053,linkId,1,None,0,85.399,0,"04.05.2016 14:32:33,655887000"),
      (18102161,linkId,1,None,0,85.618,0,"04.05.2016 14:33:23,563165000"),
      (18102171,linkId,1,None,0,85.545,0,"04.05.2016 14:33:23,584388000"),
      (18102255,linkId,1,None,0,85.691,0,"04.05.2016 14:33:23,778992000"),
      (18102265,linkId,1,None,0,85.618,0,"04.05.2016 14:33:23,799149000"),
      (18102356,linkId,1,None,0,85.545,0,"04.05.2016 14:33:24,007715000"),
      (18102366,linkId,1,None,0,85.472,0,"04.05.2016 14:33:24,030391000"),
      (18106367,linkId,1,None,0,85.545,0,"04.05.2016 14:33:34,674564000"),
      (18106377,linkId,1,None,0,85.472,0,"04.05.2016 14:33:34,696060000"),
      (18106463,linkId,1,None,0,85.618,0,"04.05.2016 14:33:34,879901000"),
      (18106473,linkId,1,None,0,85.545,0,"04.05.2016 14:33:34,900071000"),
      (18106577,linkId,1,None,0,85.545,0,"04.05.2016 14:33:35,116401000"),
      (18093571,linkId,1,None,0,85.618,0,"04.05.2016 14:33:03,579189000"),
      (18093581,linkId,1,None,0,85.545,0,"04.05.2016 14:33:03,605754000"),
      (18093673,linkId,1,None,0,85.327,0,"04.05.2016 14:33:03,813211000"),
      (18093764,linkId,1,None,0,85.472,0,"04.05.2016 14:33:04,020303000"),
      (18093774,linkId,1,None,0,85.399,0,"04.05.2016 14:33:04,043755000"),
      (18079857,linkId,1,None,0,85.399,0,"04.05.2016 14:32:17,250079000"),
      (18079867,linkId,1,None,0,85.327,0,"04.05.2016 14:32:17,281743000"),
      (18079969,linkId,1,None,0,85.472,0,"04.05.2016 14:32:17,578733000"),
      (18114211,linkId,1,None,0,85.618,0,"04.05.2016 14:33:54,710199000"),
      (18114221,linkId,1,None,0,85.545,0,"04.05.2016 14:33:54,732262000"),
      (18114307,linkId,1,None,0,85.618,0,"04.05.2016 14:33:54,925503000"),
      (18114317,linkId,1,None,0,85.545,0,"04.05.2016 14:33:54,947430000"),
      (18114401,linkId,1,None,0,85.691,0,"04.05.2016 14:33:55,276289000"),
      (18114411,linkId,1,None,0,85.618,0,"04.05.2016 14:33:55,323716000"),
      (18082139,linkId,1,None,0,85.545,0,"04.05.2016 14:32:33,901783000"),
      (18082149,linkId,1,None,0,85.472,0,"04.05.2016 14:32:33,925942000"),
      (18082235,linkId,1,None,0,85.472,0,"04.05.2016 14:32:34,162466000"),
      (18082245,linkId,1,None,0,85.399,0,"04.05.2016 14:32:34,186150000"),
      (18082335,linkId,1,None,0,85.545,0,"04.05.2016 14:32:34,411441000"),
      (18082345,linkId,1,None,0,85.472,0,"04.05.2016 14:32:34,437912000"),
      (18102456,linkId,1,None,0,85.618,0,"04.05.2016 14:33:24,254441000"),
      (18102466,linkId,1,None,0,85.545,0,"04.05.2016 14:33:24,276613000"),
      (18102552,linkId,1,None,0,85.618,0,"04.05.2016 14:33:24,463176000"),
      (18102563,linkId,1,None,0,85.545,0,"04.05.2016 14:33:24,485745000"),
      (18106588,linkId,1,None,0,85.472,0,"04.05.2016 14:33:35,137792000"),
      (18106678,linkId,1,None,0,85.545,0,"04.05.2016 14:33:35,326558000"),
      (18106688,linkId,1,None,0,85.472,0,"04.05.2016 14:33:35,347897000"),
      (18106774,linkId,1,None,0,85.618,0,"04.05.2016 14:33:35,532703000"),
      (18106784,linkId,1,None,0,85.545,0,"04.05.2016 14:33:35,553014000"),
      (18093868,linkId,1,None,0,85.472,0,"04.05.2016 14:33:04,266984000"),
      (18093958,linkId,1,None,0,85.545,0,"04.05.2016 14:33:04,463525000"),
      (18093968,linkId,1,None,0,85.472,0,"04.05.2016 14:33:04,484395000"),
      (18094059,linkId,1,None,0,85.618,0,"04.05.2016 14:33:04,682422000"),
      (18094069,linkId,1,None,0,85.545,0,"04.05.2016 14:33:04,703094000"),
      (18096614,linkId,1,None,0,85.472,0,"04.05.2016 14:33:10,540650000"),
      (18096624,linkId,1,None,0,85.399,0,"04.05.2016 14:33:10,564244000"),
      (18096714,linkId,1,None,0,85.545,0,"04.05.2016 14:33:10,764299000"),
      (18096724,linkId,1,None,0,85.472,0,"04.05.2016 14:33:10,787419000"),
      (18096811,linkId,1,None,0,85.545,0,"04.05.2016 14:33:10,979280000"),
      (18096821,linkId,1,None,0,85.472,0,"04.05.2016 14:33:11,001074000"),
      (18079979,linkId,1,None,0,85.399,0,"04.05.2016 14:32:17,603588000"),
      (18080087,linkId,1,None,0,85.472,0,"04.05.2016 14:32:17,911524000"),
      (18080097,linkId,1,None,0,85.399,0,"04.05.2016 14:32:17,937023000"),
      (18080195,linkId,1,None,0,85.545,0,"04.05.2016 14:32:18,184750000"),
      (18080205,linkId,1,None,0,85.472,0,"04.05.2016 14:32:18,210897000"),
      (18114687,linkId,1,None,0,85.545,0,"04.05.2016 14:33:56,537399000"),
      (18114698,linkId,1,None,0,85.472,0,"04.05.2016 14:33:56,643952000"),
      (18082431,linkId,1,None,0,85.545,0,"04.05.2016 14:32:34,685872000"),
      (18082441,linkId,1,None,0,85.472,0,"04.05.2016 14:32:34,713688000"),
      (18082527,linkId,1,None,0,85.618,0,"04.05.2016 14:32:34,935268000"),
      (18082537,linkId,1,None,0,85.545,0,"04.05.2016 14:32:34,960216000"),
      (18087182,linkId,1,None,0,85.399,0,"04.05.2016 14:32:47,504238000"),
      (18087305,linkId,1,None,0,85.472,0,"04.05.2016 14:32:47,812275000"),
      (18098939,linkId,1,None,0,85.472,0,"04.05.2016 14:33:15,942737000"),
      (18098949,linkId,1,None,0,85.399,0,"04.05.2016 14:33:15,964876000"),
      (18099047,linkId,1,None,0,85.545,0,"04.05.2016 14:33:16,182580000"),
      (18099057,linkId,1,None,0,85.472,0,"04.05.2016 14:33:16,203972000"),
      (18099143,linkId,1,None,0,85.472,0,"04.05.2016 14:33:16,412361000"),
      (18102783,linkId,1,None,0,85.545,0,"04.05.2016 14:33:25,005860000"),
      (18102793,linkId,1,None,0,85.472,0,"04.05.2016 14:33:25,034081000"),
      (18102884,linkId,1,None,0,85.618,0,"04.05.2016 14:33:25,419122000"),
      (18102894,linkId,1,None,0,85.545,0,"04.05.2016 14:33:25,466368000"),
      (18102981,linkId,1,None,0,85.618,0,"04.05.2016 14:33:25,825351000"),
      (18106871,linkId,1,None,0,85.545,0,"04.05.2016 14:33:35,745508000"),
      (18106881,linkId,1,None,0,85.472,0,"04.05.2016 14:33:35,766844000"),
      (18106971,linkId,1,None,0,85.618,0,"04.05.2016 14:33:35,965199000"),
      (18106981,linkId,1,None,0,85.545,0,"04.05.2016 14:33:35,989117000"),
      (18107067,linkId,1,None,0,85.618,0,"04.05.2016 14:33:36,173433000"),
      (18107077,linkId,1,None,0,85.545,0,"04.05.2016 14:33:36,194265000"),
      (18108801,linkId,1,None,0,85.472,0,"04.05.2016 14:33:40,026181000"),
      (18108892,linkId,1,None,0,85.545,0,"04.05.2016 14:33:40,241452000"),
      (18108902,linkId,1,None,0,85.472,0,"04.05.2016 14:33:40,262901000"),
      (18108992,linkId,1,None,0,85.618,0,"04.05.2016 14:33:40,464929000"),
      (18109002,linkId,1,None,0,85.545,0,"04.05.2016 14:33:40,488022000"),
      (18094155,linkId,1,None,0,85.618,0,"04.05.2016 14:33:04,919671000"),
      (18094165,linkId,1,None,0,85.545,0,"04.05.2016 14:33:04,941936000"),
      (18094250,linkId,1,None,0,85.691,0,"04.05.2016 14:33:05,135597000"),
      (18094260,linkId,1,None,0,85.618,0,"04.05.2016 14:33:05,160425000"),
      (18094270,linkId,1,None,0,85.399,0,"04.05.2016 14:33:05,182583000"),
      (18096907,linkId,1,None,0,85.618,0,"04.05.2016 14:33:11,185907000"),
      (18096917,linkId,1,None,0,85.545,0,"04.05.2016 14:33:11,207026000"),
      (18096987,linkId,1,None,0,85.691,0,"04.05.2016 14:33:11,366747000"),
      (18096997,linkId,1,None,0,85.618,0,"04.05.2016 14:33:11,387635000"),
      (18097088,linkId,1,None,0,85.545,0,"04.05.2016 14:33:11,579355000"),
      (18097098,linkId,1,None,0,85.472,0,"04.05.2016 14:33:11,600741000"),
      (18114788,linkId,1,None,0,85.618,0,"04.05.2016 14:33:56,882678000"),
      (18114798,linkId,1,None,0,85.545,0,"04.05.2016 14:33:56,906679000"),
      (18114885,linkId,1,None,0,85.618,0,"04.05.2016 14:33:57,113773000"),
      (18114895,linkId,1,None,0,85.545,0,"04.05.2016 14:33:57,137318000"),
      (18114979,linkId,1,None,0,85.691,0,"04.05.2016 14:33:57,334058000"),
      (18114989,linkId,1,None,0,85.618,0,"04.05.2016 14:33:57,355262000"),
      (18082627,linkId,1,None,0,85.472,0,"04.05.2016 14:32:35,196236000"),
      (18082637,linkId,1,None,0,85.399,0,"04.05.2016 14:32:35,228841000"),
      (18082727,linkId,1,None,0,85.545,0,"04.05.2016 14:32:35,749186000"),
      (18082737,linkId,1,None,0,85.472,0,"04.05.2016 14:32:35,774433000"),
      (18082823,linkId,1,None,0,85.545,0,"04.05.2016 14:32:36,028675000"),
      (18082833,linkId,1,None,0,85.472,0,"04.05.2016 14:32:36,071091000"),
      (18087315,linkId,1,None,0,85.399,0,"04.05.2016 14:32:47,886844000"),
      (18087417,linkId,1,None,0,85.545,0,"04.05.2016 14:32:48,123558000"),
      (18087427,linkId,1,None,0,85.472,0,"04.05.2016 14:32:48,146885000"),
      (18087536,linkId,1,None,0,85.545,0,"04.05.2016 14:32:48,404613000"),
      (18087546,linkId,1,None,0,85.472,0,"04.05.2016 14:32:48,430272000"),
      (18099153,linkId,1,None,0,85.399,0,"04.05.2016 14:33:16,435911000"),
      (18099244,linkId,1,None,0,85.545,0,"04.05.2016 14:33:16,647620000"),
      (18099254,linkId,1,None,0,85.472,0,"04.05.2016 14:33:16,667992000"),
      (18099340,linkId,1,None,0,85.545,0,"04.05.2016 14:33:16,866848000"),
      (18099350,linkId,1,None,0,85.472,0,"04.05.2016 14:33:16,888438000"),
      (18102991,linkId,1,None,0,85.545,0,"04.05.2016 14:33:25,866957000"),
      (18103075,linkId,1,None,0,85.691,0,"04.05.2016 14:33:26,188614000"),
      (18103085,linkId,1,None,0,85.618,0,"04.05.2016 14:33:26,208973000"),
      (18107756,linkId,1,None,0,85.472,0,"04.05.2016 14:33:37,673559000"),
      (18107842,linkId,1,None,0,85.545,0,"04.05.2016 14:33:37,868205000"),
      (18107852,linkId,1,None,0,85.472,0,"04.05.2016 14:33:37,889336000"),
      (18107939,linkId,1,None,0,85.618,0,"04.05.2016 14:33:38,077075000"),
      (18107949,linkId,1,None,0,85.545,0,"04.05.2016 14:33:38,099742000"),
      (18109787,linkId,1,None,0,85.472,0,"04.05.2016 14:33:42,427992000"),
      (18109878,linkId,1,None,0,85.545,0,"04.05.2016 14:33:42,786610000"),
      (18109888,linkId,1,None,0,85.472,0,"04.05.2016 14:33:42,825849000"))
  }

  test("huge assets") {
    val randomLinkId = generateRandomLinkId()
    val rl = RoadLink(randomLinkId, Seq(Point(0.0, 0.0), Point(0.0, 85.398)), 85.398, AdministrativeClass.apply(1), 1, TrafficDirection.BothDirections, LinkType.apply(1), modifiedAt = None, modifiedBy = None, attributes=Map())
    val assets = makeAssetsList(randomLinkId)
    val linearAssets = assetFiller.toLinearAsset(assets.map( a =>
      PersistedLinearAsset(a._1, a._2, a._3, a._4, a._5, a._6, Option("k123"), None, Option("k345"), Option(DateTime.parse(a._8.dropRight(6), DateParser.DateTimePropertyFormatMs)), expired=false, 100, a._7, None, linkSource = NormalLinkInterface, None, None, None)
    ), assetFiller.toRoadLinkForFillTopology(rl))
    val (outputAssets, changeSet) = assetFiller.fillTopology(Seq(rl).map(assetFiller.toRoadLinkForFillTopology), linearAssets.groupBy(_.linkId), 110)
    changeSet.adjustedMValues.size == 1 should be (true)
    outputAssets.size should be (1)
    changeSet.expiredAssetIds should have size 317
    outputAssets.head.id should be (18116559L)
  }

  test("clean lots of adjustments") {
    val roadLink = assetFiller.toRoadLinkForFillTopology(RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None))
    val adjustedMValues = List.tabulate(20000)(id => MValueAdjustment(id, generateRandomLinkId(), 0, Random.nextDouble() * 100))
    val adjustedSideCodes = List.tabulate(20000)(id => SideCodeAdjustment(id, linkId1, BothDirections, RoadWidth.typeId))
    val changeSet = ChangeSet(Set(), adjustedMValues, adjustedSideCodes, Set(), Seq())
    val result = assetFiller.clean(roadLink, Seq(), changeSet)
    result._2.adjustedMValues.size should be(20000)
    result._2.adjustedSideCodes.size should be(20000)
  }

  test("clean takes the latest adjustment for each asset") {
    val roadLink = assetFiller.toRoadLinkForFillTopology(RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 10.0, Municipality, 1, TrafficDirection.BothDirections, Motorway, None, None))
    val adjustedMValuesId1 = List.tabulate(10)(id => MValueAdjustment(1, generateRandomLinkId(), 0, Random.nextDouble() * 100))
    val adjustedMValuesId2 = List.tabulate(10)(id => MValueAdjustment(2, generateRandomLinkId(), 0, Random.nextDouble() * 100))
    val adjustedMValuesId3 = List.tabulate(10)(id => MValueAdjustment(3, generateRandomLinkId(), 0, Random.nextDouble() * 100))
    val changeSet = ChangeSet(Set(), adjustedMValuesId1 ++ adjustedMValuesId2 ++ adjustedMValuesId3, Nil, Set(), Seq())
    val result = assetFiller.clean(roadLink, Seq(), changeSet)
    result._2.adjustedMValues.filter(_.assetId == 1).size should be(1)
    result._2.adjustedMValues.filter(_.assetId == 1).head should be(adjustedMValuesId1.last)
    result._2.adjustedMValues.filter(_.assetId == 2).size should be(1)
    result._2.adjustedMValues.filter(_.assetId == 2).head should be(adjustedMValuesId2.last)
    result._2.adjustedMValues.filter(_.assetId == 3).size should be(1)
    result._2.adjustedMValues.filter(_.assetId == 3).head should be(adjustedMValuesId3.last)
  }

  test("one-way asset without a validity direction on a two-way link is changed to two-way, an asset with validity directions remains one-way") {
    val roadLink = RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(20, 0.0)), 20, AdministrativeClass.apply(2), UnknownFunctionalClass.value,
      TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())

    val assets = Seq(
      createAsset(1, linkId1, Measure(0, 20), SideCode.TowardsDigitizing, None, TrafficDirection.TowardsDigitizing, NumberOfLanes.typeId),
      createAsset(2, linkId1, Measure(0, 20), SideCode.TowardsDigitizing, None, TrafficDirection.TowardsDigitizing, TrafficVolume.typeId)
    )

    val (adjustedAssets, changeSet) = assetFiller.adjustSegmentSideCodes(assetFiller.toRoadLinkForFillTopology(roadLink), assets, initChangeSet)

    val sortedAssets = adjustedAssets.sortBy(_.id)

    sortedAssets.size should be(2)
    sortedAssets.head.sideCode should be(SideCode.TowardsDigitizing)
    sortedAssets.last.sideCode should be(SideCode.BothDirections)

    changeSet.adjustedSideCodes.size should be(1)
    changeSet.adjustedSideCodes.head.assetId should be(2)
    changeSet.adjustedSideCodes.head.sideCode should be(SideCode.BothDirections)
    changeSet.adjustedSideCodes.head.typeId should be(TrafficVolume.typeId)
  }

  test("do not change side code to BothDirections if there is an asset on the other side") {
    val roadLink = RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(20, 0.0)), 20, AdministrativeClass.apply(2), UnknownFunctionalClass.value,
      TrafficDirection.BothDirections, LinkType.apply(3), None, None, Map())

    val assets = Seq(
      createAsset(1, linkId1, Measure(0, 20), SideCode.TowardsDigitizing, None, TrafficDirection.BothDirections, WinterSpeedLimit.typeId),
      createAsset(2, linkId1, Measure(0, 20), SideCode.AgainstDigitizing, None, TrafficDirection.BothDirections, WinterSpeedLimit.typeId)
    )

    val (adjustedAssets, changeSet) = assetFiller.adjustSegmentSideCodes(assetFiller.toRoadLinkForFillTopology(roadLink), assets, initChangeSet)

    val sortedAssets = adjustedAssets.sortBy(_.id)

    sortedAssets.size should be(2)
    sortedAssets.head.sideCode should be(SideCode.TowardsDigitizing)
    sortedAssets.last.sideCode should be(SideCode.AgainstDigitizing)

    changeSet.adjustedSideCodes.size should be(0)
  }

  test("Change one sided abstract asset side code if the traffic direction has changed") {
    val roadLink1 = RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(20, 0.0)), 20, AdministrativeClass.apply(2), UnknownFunctionalClass.value,
      TrafficDirection.AgainstDigitizing, LinkType.apply(3), None, None, Map())
    val roadLink2 = RoadLink(linkId2, Seq(Point(20.0, 0.0), Point(40, 0.0)), 20, AdministrativeClass.apply(2), UnknownFunctionalClass.value,
      TrafficDirection.TowardsDigitizing, LinkType.apply(3), None, None, Map())
    val roadLinks = Seq(roadLink1, roadLink2)

    val assets = Seq(
      createAsset(1, linkId1, Measure(0, 20), SideCode.TowardsDigitizing, None, TrafficDirection.BothDirections, WinterSpeedLimit.typeId),
      createAsset(2, linkId2, Measure(0, 20), SideCode.AgainstDigitizing, None, TrafficDirection.BothDirections, WinterSpeedLimit.typeId)
    )

    val (adjustedAssets, changeSet) = assetFiller.fillTopologyChangesGeometry(roadLinks.map(assetFiller.toRoadLinkForFillTopology), assets.groupBy(_.linkId), RoadWidth.typeId)

    val sortedAssets = adjustedAssets.sortBy(_.id)

    sortedAssets.size should be(2)
    sortedAssets.head.sideCode should be(SideCode.AgainstDigitizing)
    sortedAssets.last.sideCode should be(SideCode.TowardsDigitizing)

    changeSet.adjustedSideCodes.size should be(2)
  }

  test("Don't change one sided physical asset side code if the traffic direction has changed") {
    val roadLink1 = RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(20, 0.0)), 20, AdministrativeClass.apply(2), UnknownFunctionalClass.value,
      TrafficDirection.AgainstDigitizing, LinkType.apply(3), None, None, Map())
    val roadLink2 = RoadLink(linkId2, Seq(Point(20.0, 0.0), Point(40, 0.0)), 20, AdministrativeClass.apply(2), UnknownFunctionalClass.value,
      TrafficDirection.TowardsDigitizing, LinkType.apply(3), None, None, Map())
    val roadLinks = Seq(roadLink1, roadLink2)

    val assets = Seq(
      createAsset(1, linkId1, Measure(0, 20), SideCode.TowardsDigitizing, None, TrafficDirection.BothDirections, ParkingProhibition.typeId),
      createAsset(2, linkId2, Measure(0, 20), SideCode.AgainstDigitizing, None, TrafficDirection.BothDirections, ParkingProhibition.typeId)
    )

    val (adjustedAssets, changeSet) = assetFiller.fillTopologyChangesGeometry(roadLinks.map(assetFiller.toRoadLinkForFillTopology), assets.groupBy(_.linkId), RoadWidth.typeId)

    val sortedAssets = adjustedAssets.sortBy(_.id)

    sortedAssets.size should be(2)
    sortedAssets.head.sideCode should be(SideCode.TowardsDigitizing)
    sortedAssets.last.sideCode should be(SideCode.AgainstDigitizing)

    changeSet.adjustedSideCodes.size should be(0)
  }

  test("Don't change one sided abstract asset side code on traffic direction change if there is an asset on the other side." +
    "Drop the asset heading to the wrong direction") {
    val roadLink1 = RoadLink(linkId1, Seq(Point(0.0, 0.0), Point(20, 0.0)), 20, AdministrativeClass.apply(2), UnknownFunctionalClass.value,
      TrafficDirection.AgainstDigitizing, LinkType.apply(3), None, None, Map())
    val roadLinks = Seq(roadLink1)

    val assets = Seq(
      createAsset(1, linkId1, Measure(0, 20), SideCode.TowardsDigitizing, None, TrafficDirection.BothDirections, WinterSpeedLimit.typeId),
      createAsset(2, linkId1, Measure(0, 20), SideCode.AgainstDigitizing, None, TrafficDirection.BothDirections, WinterSpeedLimit.typeId)
    )

    val (adjustedAssets, changeSet) = assetFiller.fillTopologyChangesGeometry(roadLinks.map(assetFiller.toRoadLinkForFillTopology), assets.groupBy(_.linkId), RoadWidth.typeId)

    adjustedAssets.size should be(1)
    adjustedAssets.head.sideCode should be(SideCode.AgainstDigitizing)

    changeSet.adjustedSideCodes.size should be(0)
    changeSet.expiredAssetIds.size should be(1)
    changeSet.expiredAssetIds.head should be(1)
  }
}
