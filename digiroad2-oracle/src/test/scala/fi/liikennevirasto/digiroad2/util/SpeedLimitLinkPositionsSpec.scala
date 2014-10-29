package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.Point

import org.scalatest.FunSuite
import org.scalatest.Matchers

class SpeedLimitLinkPositionsSpec extends FunSuite with Matchers {
  test("generate position indices for segment sequence of one segment") {
    val segments = List((Point(373028.812006694, 6678475.44858997), Point(373044.204553789, 6678442.81292882)))
    SpeedLimitLinkPositions.generate(segments) shouldBe Seq(0)
  }

  test("generate position indices for segment sequence where segments are in order") {
    val segments = List((Point(374134.233471419,6677240.50731189), Point(374120.876216048,6677240.61213817)), (Point(374120.876216048,6677240.61213817), Point(374083.159979821,6677239.66865146)))
    SpeedLimitLinkPositions.generate(segments) shouldBe Seq(0, 1)
  }

  test("generate position indices for segment sequence where segments are not in order") {
    val segments = List((Point(374120.876216048,6677240.61213817), Point(374083.159979821,6677239.66865146)), (Point(374134.233471419,6677240.50731189), Point(374120.876216048,6677240.61213817)))
    SpeedLimitLinkPositions.generate(segments) shouldBe Seq(1, 0)
  }

  test("generate position indices for three segment sequence") {
    val segments = List(
      (Point(372572.589549587, 6678017.88260562), Point(372564.91838001, 6678035.95670311)),
      (Point(372564.918268001, 6678035.95699387), Point(372450.464234144, 6678051.64592463)),
      (Point(372573.640063694, 6678008.0175942), Point(372572.589549587, 6678017.88260562))
    )
    SpeedLimitLinkPositions.generate(segments) shouldBe Seq(1, 2, 0)
  }

  test("generate position indices for all segments") {
    val segments = List(
      (Point(224768.5023,6826396.2046),Point(224819.13222717855,6826478.298482074)),
      (Point(224726.0543,6826327.3731),Point(224768.5023,6826396.2046)),
      (Point(224643.4389,6826195.211),Point(224787.5725,6826020.8164)),
      (Point(224685.8604,6826262.2064),Point(224726.05413146524,6826327.372826754)),
      (Point(224643.4389,6826195.211),Point(224685.86023184704,6826262.206127376))
    )
    SpeedLimitLinkPositions.generate(segments).length shouldBe 5
  }
}
