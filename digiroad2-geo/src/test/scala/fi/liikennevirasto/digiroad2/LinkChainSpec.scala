package fi.liikennevirasto.digiroad2

import org.scalatest._

class LinkChainSpec extends FunSuite with Matchers {
  val identityLinkChain = LinkChain(_: Seq[(Point, Point)], identity[(Point, Point)])

  test("generate position indices for segment sequence of one segment") {
    val segments = List((Point(373028.812006694, 6678475.44858997), Point(373044.204553789, 6678442.81292882)))
    identityLinkChain(segments).map{ link => (link.linkIndex, link.linkPosition) }.sortBy(_._1).map(_._2) shouldBe Seq(0)
  }

  test("generate position indices for segment sequence where segments are in order") {
    val segments = List((Point(374134.233471419,6677240.50731189), Point(374120.876216048,6677240.61213817)), (Point(374120.876216048,6677240.61213817), Point(374083.159979821,6677239.66865146)))
    identityLinkChain(segments).map{ link => (link.linkIndex, link.linkPosition) }.sortBy(_._1).map(_._2) shouldBe Seq(1, 0)
  }

  test("generate position indices for segment sequence where segments are not in order") {
    val segments = List((Point(374120.876216048,6677240.61213817), Point(374083.159979821,6677239.66865146)), (Point(374134.233471419,6677240.50731189), Point(374120.876216048,6677240.61213817)))
    identityLinkChain(segments).map{ link => (link.linkIndex, link.linkPosition) }.sortBy(_._1).map(_._2) shouldBe Seq(0, 1)
  }

  test("generate position indices for three segment sequence") {
    val segments = List(
      (Point(372573, 6678018), Point(372565, 6678036)),
      (Point(372565, 6678036), Point(372450, 6678051)),
      (Point(372574, 6678008), Point(372573, 6678018))
    )
    identityLinkChain(segments).map{ link => (link.linkIndex, link.linkPosition) }.sortBy(_._1).map(_._2) shouldBe Seq(1, 0, 2)
  }

  test("generate position indices for all segments") {
    val segments = List(
      (Point(224768.5023,6826396.2046),Point(224819.13222717855,6826478.298482074)),
      (Point(224726.0543,6826327.3731),Point(224768.5023,6826396.2046)),
      (Point(224643.4389,6826195.211),Point(224787.5725,6826020.8164)),
      (Point(224685.8604,6826262.2064),Point(224726.05413146524,6826327.372826754)),
      (Point(224643.4389,6826195.211),Point(224685.86023184704,6826262.206127376))
    )
    identityLinkChain(segments).map{ link => (link.linkIndex, link.linkPosition) }.sortBy(_._1).map(_._2).length shouldBe 5
  }

  test("generate position indices and calculate gap for two segment sequence with a gap") {
    val testSegments = List(
      List((Point(0, 0), Point(0, 100)), (Point(0, 200), Point(0, 300))),
      List((Point(0, 0), Point(0, 100)), (Point(0, 300), Point(0, 200))),
      List((Point(0, 100), Point(0, 0)), (Point(0, 200), Point(0, 300))),
      List((Point(0, 100), Point(0, 0)), (Point(0, 300), Point(0, 200))))

    testSegments.foreach { segments =>
      identityLinkChain(segments).linkGaps() shouldBe Seq(100.0)
      identityLinkChain(segments).map{ link => (link.linkIndex, link.linkPosition) }.sortBy(_._1).map(_._2) shouldBe Seq(0, 1)
    }
  }

  test("provides empty middle segment sequence on one segment sequence") {
    val segments = List((Point(0, 0), Point(100, 0)))
    identityLinkChain(segments).withoutEndSegments().links should be(empty)
  }

  test("provides empty middle segment sequence on two segment sequence") {
    val segments = List((Point(0, 0), Point(100, 0)), (Point(100, 0), Point(150, 0)))
    identityLinkChain(segments).withoutEndSegments().links should be(empty)
  }

  test("provides one middle segment on three segment sequence") {
    val segments = List((Point(0, 0), Point(100, 0)), (Point(100, 0), Point(150, 0)), (Point(150, 0), Point(200, 0)))
    identityLinkChain(segments).withoutEndSegments().links.map(_.rawLink) should be(List((Point(100, 0), Point(150, 0))))
  }
}
