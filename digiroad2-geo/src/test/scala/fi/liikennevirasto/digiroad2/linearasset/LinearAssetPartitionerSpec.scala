package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset._
import org.scalatest._

class LinearAssetPartitionerSpec extends FunSuite with Matchers {
  private def linearAsset(mmlId: Long, value: Int, geometry: Seq[Point]) = {
    LinearAsset(0, mmlId, SideCode.BothDirections.value, Some(value), geometry, false, 0.0, 0.0, Set.empty[Point], None, None, None, None, 30)
  }

  private def roadLinkForSpeedLimit(roadIdentifier: Either[Int, String], administrativeClass: AdministrativeClass = Unknown): VVHRoadLinkWithProperties = {
    val municipalityCode = "MUNICIPALITYCODE" -> BigInt(235)
    val ri = roadIdentifier match {
      case Left(number) => "ROADNUMBER" -> BigInt(number)
      case Right(name) => "ROADNAME_FI" -> name
    }
    VVHRoadLinkWithProperties(
      2, Seq(Point(1.0, 0.0), Point(2.0, 0.0)), 1.0, administrativeClass,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map(municipalityCode, ri))
  }

  test("group speed limits with same limit value and road number") {
    val linearAssets = Seq(
      linearAsset(1, 50, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      linearAsset(2, 50, Seq(Point(10.2, 0.0), Point(20.0, 0.0))))
    val roadLinksForSpeedLimits = Map(1l -> roadLinkForSpeedLimit(Left(1)), 2l -> roadLinkForSpeedLimit(Left(1)))

    val groupedLinks = LinearAssetPartitioner.partition(linearAssets, roadLinksForSpeedLimits)
    groupedLinks should have size 1
    groupedLinks.head should have size 2
    groupedLinks.head.map(_.mmlId).toSet should be(linearAssets.map(_.mmlId).toSet)
  }

  test("separate link with different limit value") {
    val linearAssets = Seq(
      linearAsset(1, 50, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      linearAsset(2, 60, Seq(Point(10.2, 0.0), Point(20.0, 0.0))))
    val roadLinksForSpeedLimits = Map(1l -> roadLinkForSpeedLimit(Left(1)), 2l -> roadLinkForSpeedLimit(Left(1)))

    val groupedLinks = LinearAssetPartitioner.partition(linearAssets, roadLinksForSpeedLimits)
    groupedLinks should have size 2
  }

  test("separate link with different road number") {
    val linearAssets = Seq(
      linearAsset(1, 50, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      linearAsset(2, 50, Seq(Point(10.2, 0.0), Point(20.0, 0.0))))
    val roadLinksForSpeedLimits = Map(1l -> roadLinkForSpeedLimit(Left(1)))

    val groupedLinks = LinearAssetPartitioner.partition(linearAssets, roadLinksForSpeedLimits)
    groupedLinks should have size 2
  }

  test("separate links with gap in between") {
    val linearAssets = Seq(
      linearAsset(1, 50, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      linearAsset(2, 50, Seq(Point(11.2, 0.0), Point(20.0, 0.0))))
    val roadLinksForSpeedLimits = Map(1l -> roadLinkForSpeedLimit(Left(1)), 2l -> roadLinkForSpeedLimit(Left(1)))

    val groupedLinks = LinearAssetPartitioner.partition(linearAssets, roadLinksForSpeedLimits)
    groupedLinks should have size 2
  }

  test("separate links with different administrative classes") {
    val linearAssets = Seq(
      linearAsset(1, 50, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      linearAsset(2, 50, Seq(Point(10.2, 0.0), Point(20.0, 0.0))))
    val roadLinksForSpeedLimits = Map(1l -> roadLinkForSpeedLimit(Left(1), Municipality), 2l -> roadLinkForSpeedLimit(Left(1), State))

    val groupedLinks = LinearAssetPartitioner.partition(linearAssets, roadLinksForSpeedLimits)
    groupedLinks should have size 2
  }

  test("group links without road numbers into separate groups") {
     val linearAssets = Seq(
      linearAsset(1, 50, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      linearAsset(2, 50, Seq(Point(10.2, 0.0), Point(20.0, 0.0))))
    val roadLinksForSpeedLimits = Map.empty[Long, VVHRoadLinkWithProperties]

    val groupedLinks = LinearAssetPartitioner.partition(linearAssets, roadLinksForSpeedLimits)
    groupedLinks should have size 2
  }

  test("group speed limits with same limit value and road name") {
    val linearAssets = Seq(
      linearAsset(1, 50, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      linearAsset(2, 50, Seq(Point(10.2, 0.0), Point(20.0, 0.0))))
    val roadLinksForSpeedLimits = Map(1l -> roadLinkForSpeedLimit(Right("Opastinsilta")), 2l -> roadLinkForSpeedLimit(Right("Opastinsilta")))

    val groupedLinks = LinearAssetPartitioner.partition(linearAssets, roadLinksForSpeedLimits)
    groupedLinks should have size 1
    groupedLinks.head should have size 2
    groupedLinks.head.map(_.mmlId) should contain only(linearAssets.map(_.mmlId): _*)
  }

  test("separate links with different road name") {
    val linearAssets = Seq(
      linearAsset(1, 50, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      linearAsset(2, 50, Seq(Point(10.2, 0.0), Point(20.0, 0.0))))
    val roadLinksForSpeedLimits = Map(1l -> roadLinkForSpeedLimit(Right("Opastinsilta")), 2l -> roadLinkForSpeedLimit(Right("Ratamestarinkatu")))

    val groupedLinks = LinearAssetPartitioner.partition(linearAssets, roadLinksForSpeedLimits)
    groupedLinks should have size 2
  }
}
