package fi.liikennevirasto.digiroad2

import org.scalatest._
import fi.liikennevirasto.digiroad2.PointAssetOperations._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.PointAssetFiller._
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.client.vvh.{ChangeInfo, FeatureClass, VVHRoadlink}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink

class PointAssetOperationsSpec extends FunSuite with Matchers {

  case class testPersistedPointAsset(id: Long, lon: Double, lat: Double, municipalityCode: Int, linkId: Long, mValue: Double, floating: Boolean, vvhTimeStamp: Long, linkSource: LinkGeomSource) extends PersistedPointAsset

  test ("Calculate bearing for point: horizontal") {
    val bearing = calculateBearing(Point(0,0,0), Seq(Point(1,-1,0), Point(1,1,0)))
    bearing should be (0)
  }

  test ("Calculate bearing for point: horizontal down") {
    val bearing = calculateBearing(Point(0.1,0.5,0), Seq(Point(1,1,0), Point(1,-1,0)))
    bearing should be (180)
  }

  test("Calculate bearing for point: vertical") {
    val bearing = calculateBearing(Point(0,0,0), Seq(Point(-1,1,0), Point(1,1,0)))
    bearing should be (90)
  }

  test("Calculate bearing for point: diagonal") {
    val bearing = calculateBearing(Point(0,0,0), Seq(Point(-1,-2,0), Point(1,0,0)))
    bearing should be (45)
  }

  test("Calculate bearing for point: diagonal down") {
    val bearing = calculateBearing(Point(0,0,0), Seq(Point(1,-2,0), Point(-1,0,0)))
    bearing should be (315)
  }

  test("Calculate bearing for point: corner case") {
    val bearing = calculateBearing(Point(0.5,0,0), Seq(Point(-1,1,0), Point(0, 0.95, 0), Point(1,1,0)))
    bearing should not be (90)
  }

  test("Import bearing should not change") {
    // DR1 OPAS_ID 21362
    val point = Point(328016.508929236,6809755.93310803)
    val segment = Seq(Point(327999.7172,6809675.9779),
      Point(328008.0779,6809716.6553),
      Point(328015.3301,6809750.1494),
      Point(328019.2966,6809769.6103))
    val bearing = PointAssetOperations.calculateBearing(point, segment)
    bearing should be (12)
  }

  test("Import bearing should work for floaters") {
    // DR1 OPAS_ID 5485
    val point = Point(678936.983911151, 6900134.73125385)
    val segment =  Seq(Point(678909.7176,6900140.9987),
      Point(678969.6109,6900127.2316),
      Point(679018.0323,6900120.6011),
      Point(679048.2324,6900117.3428),
      Point(679088.8085,6900114.5805),
      Point(679134.4375,6900111.3205),
      Point(679168.803,6900107.1623))
    val bearing = PointAssetOperations.calculateBearing(point, segment)
    bearing should be (103)
  }

  test("Check floating status when using three-dimensional road data") {
    val persistedAsset = PersistedMassTransitStop(22668828, 1234, 1234, Seq(2), 172, 453487.304243636, 6845919.0252246,
      17.292, Option(2), Option(78), None, true, 0, Modification(None, None),
      Modification(None, None), Seq(), NormalLinkInterface)

    val geometry = List(Point(453466.069,6845915.849,108.81900000000314),
      Point(453479.783,6845917.468,109.3920000000071), Point(453492.22,6845920.043,109.88400000000547),
      Point(453503.379,6845924.05,110.27499999999418), Point(453516.924,6845931.413,110.72000000000116),
      Point(453529.99,6845939.093,111.24300000000221), Point(453544.01,6845948.531,111.84100000000035),
      Point(453552.295,6845953.492,112.22000000000116), Point(453563.45,6845959.573,112.68300000000454),
      Point(453585.919,6845972.216,113.33699999999953), Point(453610.303,6845984.065,113.6530000000057),
      Point(453638.671,6845997.516,114.12300000000687), Point(453650.524,6846003.514,114.4030000000057))

    val roadLink = Some(VVHRoadlink(100,172, geometry, State, TrafficDirection.BothDirections, FeatureClass.DrivePath))

    PointAssetOperations.isFloating(persistedAsset, roadLink)._1 should be (true)

    val point = Point(persistedAsset.lon, persistedAsset.lat)
    val updatedAsset = persistedAsset.copy(mValue = GeometryUtils.calculateLinearReferenceFromPoint(point, geometry))

    PointAssetOperations.isFloating(updatedAsset, roadLink)._1 should be (false)
  }

  test("Auto Correct Floating Point in case Geometry has been combined and the Asset its in Modified Part") {
    val modifiedLinkId = 6001
    val removedLinkId = 6002
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway

    val persistedAsset = testPersistedPointAsset(11, 10.0, 5.0, 235, modifiedLinkId, 2.0, true, 0, NormalLinkInterface)

    val newRoadLinks = Seq(
      RoadLink(modifiedLinkId, List(Point(3.0, 5.0), Point(20.0, 5.0)), 150.0, administrativeClass, functionalClass,
        trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode))),
      RoadLink(removedLinkId, List(Point(8.0, 5.0), Point(20.0, 5.0)), 100.0, administrativeClass, functionalClass,
        trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))
    )

    val changeInfo = Seq(
      ChangeInfo(Some(modifiedLinkId), Some(modifiedLinkId), 12345, 1, Some(0), Some(5.0), Some(0), Some(5.0), 144000000),
      ChangeInfo(Some(removedLinkId), Some(modifiedLinkId), 12346, 2, Some(0), Some(12.0), Some(5.0), Some(17.0), 144000000)
    )

    val newAssetAdjusted = PointAssetFiller.correctedPersistedAsset(persistedAsset, newRoadLinks, changeInfo)

    newAssetAdjusted.isEmpty should be(true)
  }

  test("Auto Correct Floating Point that has been Divided and with more than one ChangeInfo") {
    val cmpLinkId = 6001
    val crpLinkId = 6002
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway

    val persistedAsset = testPersistedPointAsset(11, 10.0, 5.0, municipalityCode, cmpLinkId, 2.0, floating = true, 133999999, NormalLinkInterface)

    val changeInfo = Seq(
      ChangeInfo(Some(cmpLinkId), Some(cmpLinkId), 12345, 3, Some(0), Some(72), Some(6), Some(78), 144000000),
      ChangeInfo(None, Some(cmpLinkId), 12346, 4, None, None, Some(0), Some(6), 144000000),

      ChangeInfo(Some(cmpLinkId), Some(cmpLinkId), 12345, 3, Some(0), Some(78), Some(8), Some(80), 155000000),
      ChangeInfo(None, Some(cmpLinkId), 12346, 4, None, None, Some(0), Some(8), 155000000)
    )

    val newRoadLinks = Seq(
      RoadLink(cmpLinkId, List(Point(0, 0), Point(0, 200.0)), 200.0, administrativeClass, functionalClass,
        trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode))),
      RoadLink(crpLinkId, List(Point(8.0, 5.0), Point(20.0, 5.0)), 100.0, administrativeClass, functionalClass,
        trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))
    )

    val newAssetAdjusted = PointAssetFiller.correctedPersistedAsset(persistedAsset, newRoadLinks, changeInfo)

    newAssetAdjusted.isEmpty should be(false)
    newAssetAdjusted.map { asset =>
      asset.assetId should equal(11)
      asset.lon should equal(0)
      asset.lat should equal(16)
      asset.linkId should equal(cmpLinkId)
      asset.mValue should equal(16.0)
      asset.floating should be(false)
    }
  }

  test("Auto Correct Floating Point that has been Divided and with Asset filtering by vvhTimeStamp") {
    val cmpLinkId = 6001
    val crpLinkId = 6002
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway

    val persistedAsset = testPersistedPointAsset(11, 10.0, 5.0, municipalityCode, cmpLinkId, 2.0, floating = true, 144000000, NormalLinkInterface)

    val changeInfo = Seq(
      ChangeInfo(Some(cmpLinkId), Some(cmpLinkId), 12345, 3, Some(0), Some(72), Some(6), Some(78), 144000000),
      ChangeInfo(None, Some(cmpLinkId), 12346, 4, None, None, Some(0), Some(6), 144000000),
      ChangeInfo(Some(cmpLinkId), Some(cmpLinkId), 12345, 3, Some(0), Some(78), Some(8), Some(80), 155000000),
      ChangeInfo(None, Some(cmpLinkId), 12346, 4, None, None, Some(0), Some(8), 155000000)
    )

    val newRoadLinks = Seq(
      RoadLink(cmpLinkId, List(Point(0, 0), Point(0, 200.0)), 200.0, administrativeClass, functionalClass,
        trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode))),
      RoadLink(crpLinkId, List(Point(8.0, 5.0), Point(20.0, 5.0)), 100.0, administrativeClass, functionalClass,
        trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))
    )

    val newAssetAdjusted = PointAssetFiller.correctedPersistedAsset(persistedAsset, newRoadLinks, changeInfo)

    newAssetAdjusted.isEmpty should be(false)
    newAssetAdjusted.map { asset =>
      asset.assetId should equal(11)
      asset.lon should equal(0)
      asset.lat should equal(10)
      asset.linkId should equal(cmpLinkId)
      asset.mValue should equal(10.0)
      asset.floating should be(false)
    }
  }

  test("Auto Correct Floating Point that has been Shortened with more than one ChangeInfo and considering max distance allowed") {
    val cmpLinkId = 6001
    val crpLinkId = 6002
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway

    val persistedAsset = testPersistedPointAsset(11, 0, 3, municipalityCode, cmpLinkId, 3.0, floating = true, 133999999, NormalLinkInterface)

    val changeInfo = Seq(
      ChangeInfo(Some(cmpLinkId), Some(cmpLinkId), 12345, 7, Some(2), Some(121), Some(0), Some(119), 144000000),
      ChangeInfo(Some(cmpLinkId), None, 12346, 8, Some(0), Some(2), None, None, 144000000),
      ChangeInfo(Some(cmpLinkId), None, 12346, 8, Some(121), Some(127), None, None, 144000000),
      ChangeInfo(Some(cmpLinkId), Some(cmpLinkId), 12345, 7, Some(2), Some(115), Some(0), Some(113), 144000000),
      ChangeInfo(Some(cmpLinkId), None, 12346, 8, Some(0), Some(2), None, None, 144000000),
      ChangeInfo(Some(cmpLinkId), None, 12346, 8, Some(115), Some(121), None, None, 144000000)

    )

    val newRoadLinks = Seq(
      RoadLink(cmpLinkId, List(Point(0, 0), Point(0, 200.0)), 200.0, administrativeClass, functionalClass,
        trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode))),
      RoadLink(crpLinkId, List(Point(8.0, 5.0), Point(20.0, 5.0)), 100.0, administrativeClass, functionalClass,
        trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))
    )

    val newAssetAdjusted = PointAssetFiller.correctedPersistedAsset(persistedAsset, newRoadLinks, changeInfo)

    newAssetAdjusted.isEmpty should be(false)
    newAssetAdjusted.map { asset =>
      asset.assetId should equal(11)
      asset.lon should equal(0)
      asset.lat should equal(0)
      asset.linkId should equal(cmpLinkId)
      asset.mValue should equal(0.0)
      asset.floating should be(false)
    }
  }

  test("Auto Correct Floating Point that has been Shortened with more than one ChangeInfo and filtered by vvhTimeStamp") {
    val cmpLinkId = 6001
    val crpLinkId = 6002
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway

    val persistedAsset = testPersistedPointAsset(11, 0, 3, municipalityCode, cmpLinkId, 3, floating = true, 144000000, NormalLinkInterface)

    val changeInfo = Seq(
      ChangeInfo(Some(cmpLinkId), Some(cmpLinkId), 12345, 7, Some(2), Some(121), Some(0), Some(119), 144000000),
      ChangeInfo(Some(cmpLinkId), None, 12346, 8, Some(0), Some(2), None, None, 144000000),
      ChangeInfo(Some(cmpLinkId), None, 12346, 8, Some(121), Some(127), None, None, 144000000),
      ChangeInfo(Some(cmpLinkId), Some(cmpLinkId), 12345, 7, Some(2), Some(115), Some(0), Some(113), 155000000),
      ChangeInfo(Some(cmpLinkId), None, 12346, 8, Some(0), Some(2), None, None, 155000000),
      ChangeInfo(Some(cmpLinkId), None, 12346, 8, Some(115), Some(121), None, None, 155000000)
    )

    val newRoadLinks = Seq(
      RoadLink(cmpLinkId, List(Point(0, 0), Point(0, 200.0)), 200.0, administrativeClass, functionalClass,
        trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode))),
      RoadLink(crpLinkId, List(Point(8.0, 5.0), Point(20.0, 5.0)), 100.0, administrativeClass, functionalClass,
        trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))
    )

    val newAssetAdjusted = PointAssetFiller.correctedPersistedAsset(persistedAsset, newRoadLinks, changeInfo)

    newAssetAdjusted.isEmpty should be(false)
    newAssetAdjusted.map { asset =>
      asset.assetId should equal(11)
      asset.lon should equal(0)
      asset.lat should equal(1)
      asset.linkId should equal(cmpLinkId)
      asset.mValue should equal(1.0)
      asset.floating should be(false)
    }
  }

  test("Auto Correct Floating Point in case Geometry has been combined and the Asset its in Removed Part") {
    val modifiedLinkId = 6001
    val removedLinkId = 6002
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway

    val persistedAsset = testPersistedPointAsset(11, 10.0, 5.0, 235, removedLinkId, 2.0, true, 0, NormalLinkInterface)

    val newRoadLinks = Seq(
      RoadLink(modifiedLinkId, List(Point(3.0, 5.0), Point(20.0, 5.0)), 150.0, administrativeClass, functionalClass,
        trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode))),
      RoadLink(removedLinkId, List(Point(8.0, 5.0), Point(20.0, 5.0)), 100.0, administrativeClass, functionalClass,
        trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))
    )

    val changeInfo = Seq(
      ChangeInfo(Some(modifiedLinkId), Some(modifiedLinkId), 12345, 1, Some(0), Some(5.0), Some(0), Some(5.0), 144000000),
      ChangeInfo(Some(removedLinkId), Some(modifiedLinkId), 12346, 2, Some(0), Some(12.0), Some(5.0), Some(17.0), 144000000)
    )

    val newAssetAdjusted = PointAssetFiller.correctedPersistedAsset(persistedAsset, newRoadLinks, changeInfo)

    newAssetAdjusted.isEmpty should be(false)
    newAssetAdjusted.map { asset =>
      asset.assetId should equal(11)
      asset.lon should equal(10.0)
      asset.lat should equal(5.0)
      asset.linkId should equal(modifiedLinkId)
      asset.mValue should equal(7.0)
      asset.floating should be(false)
    }
  }

  test("Auto Correct Floating Point in case Geometry has been divided and the Asset its in Modified Part") {
    val modifiedLinkId = 6001
    val newLinkId = 6002
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway

    val persistedAsset = testPersistedPointAsset(11, 5.0, 5.0, 235, modifiedLinkId, 2.0, true, 0, NormalLinkInterface)

    val newRoadLinks = Seq(
      RoadLink(modifiedLinkId, List(Point(3.0, 5.0), Point(8.0, 5.0)), 150.0, administrativeClass, functionalClass,
        trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode))),
      RoadLink(newLinkId, List(Point(8.0, 5.0), Point(20.0, 5.0)), 100.0, administrativeClass, functionalClass,
        trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))
    )

    val changeInfo = Seq(
      ChangeInfo(Some(modifiedLinkId), Some(modifiedLinkId), 12345, 5, Some(0), Some(17.0), Some(0), Some(5.0), 144000000),
      ChangeInfo(Some(modifiedLinkId), Some(newLinkId), 12346, 6, Some(0), Some(0), Some(5.0), Some(17.0), 144000000)
    )

    val newAssetAdjusted = PointAssetFiller.correctedPersistedAsset(persistedAsset, newRoadLinks, changeInfo)

    newAssetAdjusted.isEmpty should be(false)
    newAssetAdjusted.map { asset =>
      asset.assetId should equal(11)
      asset.lon should equal(5.0)
      asset.lat should equal(5.0)
      asset.linkId should equal(modifiedLinkId)
      asset.mValue should equal(2.0)
      asset.floating should be(false)
    }
  }

  test("Auto Correct Floating Point in case Geometry has been divided and the Asset its in New Part") {
    val newLinkId = 6002
    val newLinkId2 = 6001
    val oldLinkId = 106
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway

    val persistedAsset = testPersistedPointAsset(11, 10.0, 5.0, 235, oldLinkId, 7.0, true, 0, NormalLinkInterface)

    val newRoadLinks = Seq(
      RoadLink(newLinkId2, List(Point(3.0, 5.0), Point(8.0, 5.0)), 150.0, administrativeClass, functionalClass,
        trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode))),
      RoadLink(newLinkId, List(Point(8.0, 5.0), Point(20.0, 5.0)), 100.0, administrativeClass, functionalClass,
        trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))
    )

    val changeInfo = Seq(
      ChangeInfo(Some(oldLinkId), Some(newLinkId2), 12345, 5, Some(5), Some(17.0), Some(0), Some(12.0), 144000000),
      ChangeInfo(Some(oldLinkId), Some(newLinkId), 12346, 6, Some(0), Some(5), Some(0), Some(5.0), 144000000)
    )

    val newAssetAdjusted = PointAssetFiller.correctedPersistedAsset(persistedAsset, newRoadLinks, changeInfo)

    newAssetAdjusted.isEmpty should be(false)
    newAssetAdjusted.map { asset =>
      asset.assetId should equal(11)
      asset.lon should equal(5.0)
      asset.lat should equal(5.0)
      asset.linkId should equal(newLinkId2)
      asset.mValue should equal(2.0)
      asset.floating should be(false)
    }
  }

  test("Auto Correct Floating Point in case Geometry has been Lengthened and the Asset its in Common Part") {
    val CommonLinkId = 6001
    val newLinkId = 6002
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway

    val persistedAsset = testPersistedPointAsset(11, 5.0, 5.0, 235, CommonLinkId, 7.0, true, 0, NormalLinkInterface)

    val newRoadLinks = Seq(
      RoadLink(CommonLinkId, List(Point(3.0, 5.0), Point(8.0, 5.0)), 150.0, administrativeClass, functionalClass,
        trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode))),
      RoadLink(newLinkId, List(Point(8.0, 5.0), Point(20.0, 5.0)), 100.0, administrativeClass, functionalClass,
        trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))
    )

    val changeInfo = Seq(
      ChangeInfo(Some(CommonLinkId), Some(CommonLinkId), 12345, 3, Some(0), Some(17.0), Some(5.0), Some(17.0), 144000000),
      ChangeInfo(None, Some(CommonLinkId), 12346, 4, None, None, Some(0), Some(5.0), 144000000)
    )

    val newAssetAdjusted = PointAssetFiller.correctedPersistedAsset(persistedAsset, newRoadLinks, changeInfo)

    newAssetAdjusted.isEmpty should be(true)
  }

  test("Auto Correct Floating Point in case Geometry has been Lengthened and the Asset its in New Part") {
    val CommonLinkId = 6001
    val newLinkId = 6002
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway

    val persistedAsset = testPersistedPointAsset(11, 10.0, 5.0, 235, CommonLinkId, 2.0, true, 0, NormalLinkInterface)

    val newRoadLinks = Seq(
      RoadLink(newLinkId, List(Point(3.0, 5.0), Point(8.0, 5.0)), 150.0, administrativeClass, functionalClass,
        trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode))),
      RoadLink(CommonLinkId, List(Point(8.0, 5.0), Point(20.0, 5.0)), 100.0, administrativeClass, functionalClass,
        trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))
    )

    val changeInfo = Seq(
      ChangeInfo(Some(CommonLinkId), Some(CommonLinkId), 12345, 3, Some(0), Some(17.0), Some(5.0), Some(17.0), 144000000),
      ChangeInfo(None, Some(CommonLinkId), 12346, 4, None, None, Some(0), Some(5.0), 144000000)
    )

    val newAssetAdjusted = PointAssetFiller.correctedPersistedAsset(persistedAsset, newRoadLinks, changeInfo)

    newAssetAdjusted.isEmpty should be(false)
    newAssetAdjusted.map { asset =>
      asset.assetId should equal(11)
      asset.lon should equal(15.0)
      asset.lat should equal(5.0)
      asset.linkId should equal(CommonLinkId)
      asset.mValue should equal(7.0)
      asset.floating should be(false)
    }
  }

  test("Auto Correct Floating Point in case Geometry has been Replaced and the Asset its in Common Part") {
    val linkId = 6002
    val municipalityCode = 235
    val administrativeClass = Municipality
    val trafficDirection = TrafficDirection.BothDirections
    val functionalClass = 1
    val linkType = Freeway

    val persistedAsset = testPersistedPointAsset(11, 10.0, 5.0, 235, linkId, 2.0, true, 0, NormalLinkInterface)

    val newRoadLinks = Seq(
      RoadLink(linkId, List(Point(0, 0), Point(0, 40)), 40.0, administrativeClass, functionalClass,
        trafficDirection, linkType, None, None, Map("MUNICIPALITYCODE" -> BigInt(municipalityCode)))
    )

    val changeInfo = Seq(
      ChangeInfo(Some(linkId), Some(linkId), 12345, 13, Some(0), Some(17.0), Some(0), Some(15.0), 144000000),
      ChangeInfo(Some(linkId), None, 12346, 15, Some(15), Some(16), None, None, 144000000)
    )

    val newAssetAdjusted = PointAssetFiller.correctedPersistedAsset(persistedAsset, newRoadLinks, changeInfo)

    newAssetAdjusted.isEmpty should be(false)
    newAssetAdjusted.map { asset =>
      asset.assetId should equal(11)
      asset.lon should equal(0)
      asset.lat should equal(2)
      asset.linkId should equal(linkId)
      asset.mValue should equal(2)
      asset.floating should be(false)
    }
  }

  test("Auto correct floating point: join floating point to first roadlink geometry point") {
    val changeInfo = ChangeInfo(Some(1611552), Some(1611552), 12345, 7, Some(10), Some(100), Some(0), Some(100), 144000000)
    val persistedAsset =  testPersistedPointAsset(11, 0, 8, 24, 1611552, 8, false, 0, NormalLinkInterface)

    val roadLinks = Seq(
      RoadLink(1611552, List(Point(0,10), Point(0,200)), 40.0, Municipality, 1, TrafficDirection.BothDirections, MultipleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235))),
      RoadLink(1611558, List(Point(0.0, 0.0), Point(370.0, 0.0)), 370.0, Municipality, 1, TrafficDirection.BothDirections, PedestrianZone, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
    )

    val updPersistedAsset = PointAssetFiller.correctShortened(persistedAsset, roadLinks, changeInfo, None)

    updPersistedAsset.isEmpty should be (false)
    updPersistedAsset.map{ s =>
      s.linkId should equal(1611552)
      s.lon should equal(0)
      s.lat should equal(10)
      s.mValue should equal(0)
      s.floating should be (false)
    }
  }

  test("Auto correct floating point: join floating point to last roadlink geometry point") {
    val changeInfo = ChangeInfo(Some(1611552), Some(1611552), 12345, 7, Some(10), Some(80), Some(0), Some(70), 144000000)
    val persistedAsset =  testPersistedPointAsset(11, 0, 82, 24, 1611552, 82, false, 0, NormalLinkInterface)

    val roadLinks = Seq(
      RoadLink(1611552, List(Point(0,0), Point(0,70)), 40.0, Municipality, 1, TrafficDirection.BothDirections, MultipleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235))),
      RoadLink(1611558, List(Point(0.0, 0.0), Point(370.0, 0.0)), 370.0, Municipality, 1, TrafficDirection.BothDirections, PedestrianZone, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
    )

    val updPersistedAsset = PointAssetFiller.correctShortened(persistedAsset, roadLinks, changeInfo, None)

    updPersistedAsset.isEmpty should be (false)
    updPersistedAsset.map{ s =>
      s.linkId should equal(1611552)
      s.lon should equal(0)
      s.lat should equal(70)
      s.mValue should equal(70)
      s.floating should be (false)
    }
  }

  test("Auto correct floating point: join floating point to last roadlink geometry point (3 < startPoint)") {
    val changeInfo = ChangeInfo(Some(1611552), Some(1611552), 12345, 7, Some(10), Some(100), Some(0), Some(100), 144000000)
    val persistedAsset =  testPersistedPointAsset(11, 0, 5, 24, 1611552, 5, false, 0, NormalLinkInterface)

    val roadLinks = Seq(
      RoadLink(1611552, List(Point(0,10), Point(0,200)), 40.0, Municipality, 1, TrafficDirection.BothDirections, MultipleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235))),
      RoadLink(1611558, List(Point(0.0, 0.0), Point(370.0, 0.0)), 370.0, Municipality, 1, TrafficDirection.BothDirections, PedestrianZone, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
    )

    val updPersistedAsset = PointAssetFiller.correctShortened(persistedAsset, roadLinks, changeInfo, None)

    updPersistedAsset.isEmpty should be (true)
  }

  test("Auto correct floating point: join floating point to last roadlink geometry point (endPoint > 3) ") {
    val changeInfo = ChangeInfo(Some(1611552), Some(1611552), 12345, 7, Some(10), Some(80), Some(0), Some(70), 144000000)
    val persistedAsset =  testPersistedPointAsset(11, 0, 85, 24, 1611552, 85, false, 0, NormalLinkInterface)

    val roadLinks = Seq(
      RoadLink(1611552, List(Point(0,0), Point(0,70)), 40.0, Municipality, 1, TrafficDirection.BothDirections, MultipleCarriageway, None, None, Map("MUNICIPALITYCODE" -> BigInt(235))),
      RoadLink(1611558, List(Point(0.0, 0.0), Point(370.0, 0.0)), 370.0, Municipality, 1, TrafficDirection.BothDirections, PedestrianZone, None, None, Map("MUNICIPALITYCODE" -> BigInt(235)))
    )

    val updPersistedAsset = PointAssetFiller.correctShortened(persistedAsset, roadLinks, changeInfo, None)

    updPersistedAsset.isEmpty should be (true)
  }
}
