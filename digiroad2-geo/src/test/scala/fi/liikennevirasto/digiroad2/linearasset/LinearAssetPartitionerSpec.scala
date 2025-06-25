package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.NormalLinkInterface
import fi.liikennevirasto.digiroad2.asset.SideCode.BothDirections
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

  private def linearAsset(id: Int, linkId: String, value: Option[Value], geometry: Seq[Point], typeId: Int, attributes: Map[String, Any] = Map(), adminClass: AdministrativeClass = AdministrativeClass.apply(1)) = {
    PieceWiseLinearAsset(id, linkId, SideCode.BothDirections, value, geometry, false, 0.0, 100.0, Set(geometry.last),None, None, None, None, typeId,TrafficDirection.BothDirections, 0, None, LinkGeomSource.NormalLinkInterface,adminClass, attributes, None, None, None,0, Seq())
  }

  private def roadLinkForAsset(roadIdentifier: Either[Long, String], administrativeClass: AdministrativeClass = Unknown): RoadLink = {
    val municipalityCode = "MUNICIPALITYCODE" -> BigInt(235)
    val ri = roadIdentifier match {
      case Left(number) => "ROADNUMBER" -> number
      case Right(name) => "ROADNAME_FI" -> name
    }
    RoadLink(
      linkId2, Seq(Point(1.0, 0.0), Point(2.0, 0.0)), 1.0, administrativeClass,
      1, TrafficDirection.BothDirections, Motorway, None, None, Map(municipalityCode, ri))
  }

  test("doesn't group assets with different prohibition validity periods") {
    val prohibitionAssets = Seq(
      linearAsset(1, linkId1,
        Some(Prohibitions(Seq(ProhibitionValue(1, Set(ValidityPeriod(1, 2, ValidityPeriodDayOfWeek.Weekday, 0, 0)), Set.empty)))),
        Seq(Point(0, 0), Point(10, 0)), 190),
      linearAsset(2, linkId2,
        Some(Prohibitions(Seq(ProhibitionValue(1, Set(ValidityPeriod(1, 3, ValidityPeriodDayOfWeek.Weekday, 0, 0)), Set.empty)))),
        Seq(Point(10, 0), Point(20, 0)), 190))

    val links = Map(
      linkId1 -> roadLinkForAsset(Left(1)),
      linkId2 -> roadLinkForAsset(Left(1)))
    val groupedLinks = LinearAssetPartitioner.enrichAndPartition(prohibitionAssets, links)
    groupedLinks should have size 2
    groupedLinks(0) should have size 1
    groupedLinks(1) should have size 1
  }

  test("groups assets with equal prohibition validity periods") {
    val prohibitionAssets = Seq(
      linearAsset(1, linkId1, Some(Prohibitions(Seq(ProhibitionValue(1, Set(ValidityPeriod(1, 3, ValidityPeriodDayOfWeek.Weekday, 0, 0)), Set.empty)))), Seq(Point(1.0, 0.0), Point(2.0, 0.0)),190, Map(), AdministrativeClass.apply("State")),
      linearAsset(2, linkId2, Some(Prohibitions(Seq(ProhibitionValue(1, Set(ValidityPeriod(1, 3, ValidityPeriodDayOfWeek.Weekday, 0, 0)), Set.empty)))), Seq(Point(2.0, 0.0), Point(3.0, 0.0)),190, Map(), AdministrativeClass.apply("State")))
    val links = Map(
      linkId1 -> roadLinkForAsset(Left(1)),
      linkId2 -> roadLinkForAsset(Left(1)))
    val groupedLinks = LinearAssetPartitioner.enrichAndPartition(prohibitionAssets, links)
    groupedLinks should have size 1
    groupedLinks(0) should have size 2
  }

  test("group speed limits with same limit value and road number") {
    val linearAssets = Seq(
      linearAsset(1, linkId1, Some(NumericValue(50)), Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 20, Map("ROADNUMBER" -> Some(1L))),
      linearAsset(2, linkId2, Some(NumericValue(50)), Seq(Point(10.2, 0.0), Point(20.0, 0.0)), 20, Map("ROADNUMBER" -> Some(1L))))

    val groupedLinks = LinearAssetPartitioner.partition(linearAssets)
    groupedLinks should have size 1
    groupedLinks.head should have size 2
    groupedLinks.head.map(_.linkId).toSet should be(linearAssets.map(_.linkId).toSet)
  }

  test("separate link with different limit value") {
    val linearAssets = Seq(
      linearAsset(1, linkId1, Some(NumericValue(50)), Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 20, Map("ROADNUMBER" -> Some(1L))),
      linearAsset(2, linkId2, Some(NumericValue(60)), Seq(Point(10.2, 0.0), Point(20.0, 0.0)), 20, Map("ROADNUMBER" -> Some(1L))))

    val groupedLinks = LinearAssetPartitioner.partition(linearAssets)
    groupedLinks should have size 2
  }

  test("separate link with different road number") {
    val linearAssets = Seq(
      linearAsset(1, linkId1, Some(NumericValue(50)), Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 20, Map("ROADNUMBER" -> Some(1L))),
      linearAsset(2, linkId2, Some(NumericValue(50)), Seq(Point(10.2, 0.0), Point(20.0, 0.0)), 20, Map("ROADNUMBER" -> Some(2L))))

    val groupedLinks = LinearAssetPartitioner.partition(linearAssets)
    groupedLinks should have size 2
  }

  test("separate links with gap in between") {
    val linearAssets = Seq(
      linearAsset(1, linkId1, Some(NumericValue(50)), Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 20),
      linearAsset(2, linkId2, Some(NumericValue(50)), Seq(Point(11.2, 0.0), Point(20.0, 0.0)),20))

    val groupedLinks = LinearAssetPartitioner.partition(linearAssets)
    groupedLinks should have size 2
  }

  test("separate links with different administrative classes") {
    val linearAssets = Seq(
      linearAsset(1, linkId1, Some(NumericValue(50)), Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 20, Map("ROADNUMBER" -> Some(1L)), AdministrativeClass.apply("State")),
      linearAsset(2, linkId2, Some(NumericValue(50)), Seq(Point(10.2, 0.0), Point(20.0, 0.0)), 20, Map("ROADNUMBER" -> Some(1L)), AdministrativeClass.apply("Municipality")))

    val groupedLinks = LinearAssetPartitioner.partition(linearAssets)
    groupedLinks should have size 2
  }

  test("group links without road numbers into separate groups") {
     val linearAssets = Seq(
      linearAsset(1, linkId1, Some(NumericValue(50)), Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 20, Map(), AdministrativeClass.apply("State")),
      linearAsset(2, linkId2, Some(NumericValue(50)), Seq(Point(10.2, 0.0), Point(20.0, 0.0)), 20, Map(), AdministrativeClass.apply("State")))

    val groupedLinks = LinearAssetPartitioner.partition(linearAssets)
    groupedLinks should have size 2
  }

  test("group speed limits with same limit value and road name") {
    val linearAssets = Seq(
      linearAsset(1, linkId1, Some(NumericValue(50)), Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 20, Map("ROADNAME_FI" -> Some("Opastinsilta"))),
      linearAsset(2, linkId2, Some(NumericValue(50)), Seq(Point(10.2, 0.0), Point(20.0, 0.0)), 20, Map("ROADNAME_FI" -> Some("Opastinsilta"))))

    val groupedLinks = LinearAssetPartitioner.partition(linearAssets)
    groupedLinks should have size 1
    groupedLinks.head should have size 2
    groupedLinks.head.map(_.linkId) should contain only(linearAssets.map(_.linkId): _*)
  }

  test("separate links with different road name") {
    val linearAssets = Seq(
      linearAsset(1, linkId1, Some(NumericValue(50)), Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 20, Map("ROADNAME_FI" -> Some("Opastinsilta"))),
      linearAsset(2, linkId2, Some(NumericValue(50)), Seq(Point(10.2, 0.0), Point(20.0, 0.0)), 20, Map("ROADNAME_FI" -> Some("Ratamestarinkatu"))))

    val groupedLinks = LinearAssetPartitioner.partition(linearAssets)
    groupedLinks should have size 2
  }

  test("separate unknown and existing asset") {
    val existingLinearAssets = linearAsset(1, linkId1, Some(NumericValue(50)), Seq(Point(0.0, 0.0), Point(10.0, 0.0)), 20, Map("ROADNAME_FI" -> Some("Opastinsilta"), "ROADNUMBER" -> Some(1L)))
    val unknownLinearAsset = PieceWiseLinearAsset(0, linkId1, BothDirections, None, Seq(Point(10.2, 0.0), Point(20.0, 0.0)),false, 0.0, 9.8, Set(Point(20.0, 0.0)),None,None,None,None,20, TrafficDirection.BothDirections, 0, None, NormalLinkInterface, State, Map("ROADNAME_FI" -> Some("Opastinsilta"), "ROADNUMBER" -> Some(1L)),None, None, None, 0, Seq())

    val groupedLinks = LinearAssetPartitioner.partition(Seq(existingLinearAssets,unknownLinearAsset))
    groupedLinks should have size 2
  }
}
