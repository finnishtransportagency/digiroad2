package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset._
import org.joda.time.DateTime
import org.scalatest._
import java.util.UUID
import scala.util.Random

class LinearAssetPartitionerSpec extends FunSuite with Matchers {
  case class TestLinearAsset(id: Long, linkId: String, sideCode: SideCode, value: Option[NumericValue], geometry: Seq[Point], timeStamp: Long = 0, geomModifiedDate: Option[DateTime] = None) extends LinearAsset
  case class TestProhibitionAsset(id: Long, linkId: String, sideCode: SideCode, value: Option[Prohibitions], geometry: Seq[Point], timeStamp: Long = 0, geomModifiedDate: Option[DateTime] = None) extends LinearAsset

  private def generateRandomLinkId(): String = s"${UUID.randomUUID()}:${Random.nextInt(100)}"
  val linkId1: String = generateRandomLinkId()
  val linkId2: String = generateRandomLinkId()

  private def linearAsset(linkId: String, value: Int, geometry: Seq[Point]) = {
    TestLinearAsset(0, linkId, SideCode.BothDirections, Some(NumericValue(value)), geometry)
  }

  private def roadLinkForAsset(roadIdentifier: Either[Int, String], administrativeClass: AdministrativeClass = Unknown): RoadLink = {
    val municipalityCode = "MUNICIPALITYCODE" -> BigInt(235)
    val ri = roadIdentifier match {
      case Left(number) => "ROADNUMBER" -> BigInt(number)
      case Right(name) => "ROADNAME_FI" -> name
    }
    RoadLink(
      linkId2, Seq(Point(1.0, 0.0), Point(2.0, 0.0)), 1.0, administrativeClass,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map(municipalityCode, ri))
  }

  test("doesn't group assets with different prohibition validity periods") {
    val prohibitionAssets = Seq(
      TestProhibitionAsset(1, linkId1, SideCode.BothDirections,
        Some(Prohibitions(Seq(ProhibitionValue(1, Set(ValidityPeriod(1, 2, ValidityPeriodDayOfWeek.Weekday, 0, 0)), Set.empty)))),
        Seq(Point(0, 0), Point(10, 0))),
      TestProhibitionAsset(2, linkId2, SideCode.BothDirections,
        Some(Prohibitions(Seq(ProhibitionValue(1, Set(ValidityPeriod(1, 3, ValidityPeriodDayOfWeek.Weekday, 0, 0)), Set.empty)))),
        Seq(Point(10, 0), Point(20, 0))))
    val links = Map(
      linkId1 -> roadLinkForAsset(Left(1)),
      linkId2 -> roadLinkForAsset(Left(1)))
    val groupedLinks = LinearAssetPartitioner.partition(prohibitionAssets, links)
    groupedLinks should have size 2
    groupedLinks(0) should have size 1
    groupedLinks(1) should have size 1
  }

  test("groups assets with equal prohibition validity periods") {
    val prohibitionAssets = Seq(
      TestProhibitionAsset(1, linkId1, SideCode.BothDirections,
        Some(Prohibitions(Seq(ProhibitionValue(1, Set(ValidityPeriod(1, 3, ValidityPeriodDayOfWeek.Weekday, 0, 0)), Set.empty)))),
        Seq(Point(0, 0), Point(10, 0))),
      TestProhibitionAsset(2, linkId2, SideCode.BothDirections,
        Some(Prohibitions(Seq(ProhibitionValue(1, Set(ValidityPeriod(1, 3, ValidityPeriodDayOfWeek.Weekday, 0, 0)), Set.empty)))),
        Seq(Point(10, 0), Point(20, 0))))
    val links = Map(
      linkId1 -> roadLinkForAsset(Left(1)),
      linkId2 -> roadLinkForAsset(Left(1)))
    val groupedLinks = LinearAssetPartitioner.partition(prohibitionAssets, links)
    groupedLinks should have size 1
    groupedLinks(0) should have size 2
  }

  test("group speed limits with same limit value and road number") {
    val linearAssets = Seq(
      linearAsset(linkId1, 50, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      linearAsset(linkId2, 50, Seq(Point(10.2, 0.0), Point(20.0, 0.0))))
    val roadLinksForSpeedLimits = Map(linkId1 -> roadLinkForAsset(Left(1)), linkId2 -> roadLinkForAsset(Left(1)))

    val groupedLinks = LinearAssetPartitioner.partition(linearAssets, roadLinksForSpeedLimits)
    groupedLinks should have size 1
    groupedLinks.head should have size 2
    groupedLinks.head.map(_.linkId).toSet should be(linearAssets.map(_.linkId).toSet)
  }

  test("separate link with different limit value") {
    val linearAssets = Seq(
      linearAsset(linkId1, 50, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      linearAsset(linkId2, 60, Seq(Point(10.2, 0.0), Point(20.0, 0.0))))
    val roadLinksForSpeedLimits = Map(linkId1 -> roadLinkForAsset(Left(1)), linkId2 -> roadLinkForAsset(Left(1)))

    val groupedLinks = LinearAssetPartitioner.partition(linearAssets, roadLinksForSpeedLimits)
    groupedLinks should have size 2
  }

  test("separate link with different road number") {
    val linearAssets = Seq(
      linearAsset(linkId1, 50, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      linearAsset(linkId2, 50, Seq(Point(10.2, 0.0), Point(20.0, 0.0))))
    val roadLinksForSpeedLimits = Map(linkId1 -> roadLinkForAsset(Left(1)))

    val groupedLinks = LinearAssetPartitioner.partition(linearAssets, roadLinksForSpeedLimits)
    groupedLinks should have size 2
  }

  test("separate links with gap in between") {
    val linearAssets = Seq(
      linearAsset(linkId1, 50, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      linearAsset(linkId2, 50, Seq(Point(11.2, 0.0), Point(20.0, 0.0))))
    val roadLinksForSpeedLimits = Map(linkId1 -> roadLinkForAsset(Left(1)), linkId2 -> roadLinkForAsset(Left(1)))

    val groupedLinks = LinearAssetPartitioner.partition(linearAssets, roadLinksForSpeedLimits)
    groupedLinks should have size 2
  }

  test("separate links with different administrative classes") {
    val linearAssets = Seq(
      linearAsset(linkId1, 50, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      linearAsset(linkId2, 50, Seq(Point(10.2, 0.0), Point(20.0, 0.0))))
    val roadLinksForSpeedLimits = Map(linkId1 -> roadLinkForAsset(Left(1), Municipality), linkId2 -> roadLinkForAsset(Left(1), State))

    val groupedLinks = LinearAssetPartitioner.partition(linearAssets, roadLinksForSpeedLimits)
    groupedLinks should have size 2
  }

  test("group links without road numbers into separate groups") {
     val linearAssets = Seq(
      linearAsset(linkId1, 50, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      linearAsset(linkId2, 50, Seq(Point(10.2, 0.0), Point(20.0, 0.0))))
    val roadLinksForSpeedLimits = Map.empty[String, RoadLink]

    val groupedLinks = LinearAssetPartitioner.partition(linearAssets, roadLinksForSpeedLimits)
    groupedLinks should have size 2
  }

  test("group speed limits with same limit value and road name") {
    val linearAssets = Seq(
      linearAsset(linkId1, 50, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      linearAsset(linkId2, 50, Seq(Point(10.2, 0.0), Point(20.0, 0.0))))
    val roadLinksForSpeedLimits = Map(linkId1 -> roadLinkForAsset(Right("Opastinsilta")), linkId2 -> roadLinkForAsset(Right("Opastinsilta")))

    val groupedLinks = LinearAssetPartitioner.partition(linearAssets, roadLinksForSpeedLimits)
    groupedLinks should have size 1
    groupedLinks.head should have size 2
    groupedLinks.head.map(_.linkId) should contain only(linearAssets.map(_.linkId): _*)
  }

  test("separate links with different road name") {
    val linearAssets = Seq(
      linearAsset(linkId1, 50, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      linearAsset(linkId2, 50, Seq(Point(10.2, 0.0), Point(20.0, 0.0))))
    val roadLinksForSpeedLimits = Map(linkId1 -> roadLinkForAsset(Right("Opastinsilta")), linkId2 -> roadLinkForAsset(Right("Ratamestarinkatu")))

    val groupedLinks = LinearAssetPartitioner.partition(linearAssets, roadLinksForSpeedLimits)
    groupedLinks should have size 2
  }

  test("separate unknown and existing asset") {
    val linearAssets = Seq(
      TestLinearAsset(0, linkId1, SideCode.BothDirections, None, Seq(Point(0.0, 0.0), Point(10.0, 0.0))),
      TestLinearAsset(1, linkId2, SideCode.BothDirections, None, Seq(Point(10.2, 0.0), Point(20.0, 0.0))))
    val roadLinksForSpeedLimits = Map(linkId1 -> roadLinkForAsset(Left(1)), linkId2 -> roadLinkForAsset(Left(1)))

    val groupedLinks = LinearAssetPartitioner.partition(linearAssets, roadLinksForSpeedLimits)
    groupedLinks should have size 2
  }
}
