package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.PointAssetOperations._
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.{FeatureClass, RoadLinkFetched}
import fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop.PersistedMassTransitStop
import org.scalatest._

class PointAssetOperationsSpec extends FunSuite with Matchers {

  case class testPersistedPointAsset(id: Long, lon: Double, lat: Double, municipalityCode: Int, linkId: String, mValue: Double, floating: Boolean, timeStamp: Long, linkSource: LinkGeomSource, propertyData: Seq[Property] = Seq()) extends PersistedPointAsset

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
    val persistedAsset = PersistedMassTransitStop(22668828, 1234, "1234", Seq(2), 172, 453487.304243636, 6845919.0252246,
      17.292, Option(2), Option(78), None, true, 0, Modification(None, None),
      Modification(None, None), Seq(), NormalLinkInterface)

    val geometry = List(Point(453466.069,6845915.849,108.81900000000314),
      Point(453479.783,6845917.468,109.3920000000071), Point(453492.22,6845920.043,109.88400000000547),
      Point(453503.379,6845924.05,110.27499999999418), Point(453516.924,6845931.413,110.72000000000116),
      Point(453529.99,6845939.093,111.24300000000221), Point(453544.01,6845948.531,111.84100000000035),
      Point(453552.295,6845953.492,112.22000000000116), Point(453563.45,6845959.573,112.68300000000454),
      Point(453585.919,6845972.216,113.33699999999953), Point(453610.303,6845984.065,113.6530000000057),
      Point(453638.671,6845997.516,114.12300000000687), Point(453650.524,6846003.514,114.4030000000057))

    val roadLink = Some(RoadLinkFetched("100",172, geometry, State, TrafficDirection.BothDirections, FeatureClass.DrivePath))

    PointAssetOperations.isFloating(persistedAsset, roadLink)._1 should be (true)

    val point = Point(persistedAsset.lon, persistedAsset.lat)
    val updatedAsset = persistedAsset.copy(mValue = GeometryUtils.calculateLinearReferenceFromPoint(point, geometry))

    PointAssetOperations.isFloating(updatedAsset, roadLink)._1 should be (false)
  }
}
