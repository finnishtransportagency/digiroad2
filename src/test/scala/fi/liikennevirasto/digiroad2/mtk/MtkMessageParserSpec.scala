package fi.liikennevirasto.digiroad2.mtk

import org.scalatest.{MustMatchers, BeforeAndAfter, FlatSpec}
import scala.io.Source
import org.joda.time.DateTime

class MtkMessageParserSpec extends FlatSpec with MustMatchers with BeforeAndAfter {
  var source: Source = null

  before {
    source = Source.fromInputStream(this.getClass.getResourceAsStream("/mtk_example.xml"))
  }

  val pointList = List(Point(375295.264, 6678420.423, 36.410), Point(375290.952, 6678385.118, 35.027))
  val pointList2 = List(Point(372979.766, 6678584.604, 31.882), Point(373001.741, 6678580.943, 31.752))
  val pointList3 = List(Point(375295.264, 6678420.423, 36.410), Point(375290.952, 6678385.118, 35.027))
  val pointList4 = List(Point(372979.766, 6678584.604, 31.882), Point(373001.741, 6678580.943, 31.752))
  val roadlinkWithEnddate = MtkRoadLink(1457964389L, new DateTime(2010, 12, 30, 0, 0, 0 ,0),
                                       Some(new DateTime(2013, 9, 10, 0, 0, 0 ,0)), 49, pointList)
  val roadlinkWithEnddate2 = MtkRoadLink(1457942735L, new DateTime(2010, 12, 30, 0, 0, 0 ,0),
                                       Some(new DateTime(2013, 9, 10, 0, 0, 0 ,0)), 235, pointList2)
  val roadlinkWithoutEnddate = MtkRoadLink(1140002832L, new DateTime(2013, 9, 10, 0, 0, 0 ,0),
                                       None, 49, pointList3)
  val roadlinkWithoutEnddate2 = MtkRoadLink(362957313L, new DateTime(2013, 9, 10, 0, 0, 0 ,0),
                                       None, 235, pointList4)

  "Mtk message parsing" must "parse items with end date correctly" in {
    val expected = List(roadlinkWithEnddate, roadlinkWithEnddate2)
    val roadlinks = MtkMessageParser.parseMtkMessage(source)
    roadlinks.take(2) must equal(expected)
  }

  it must "parse items without end date correctly" in {
    val expected = List(roadlinkWithoutEnddate, roadlinkWithoutEnddate2)
    val roadlinks = MtkMessageParser.parseMtkMessage(source)
    roadlinks.drop(2).take(2) must equal(expected)
  }

  it must "parse all roadlinks" in {
    val roadlinks = MtkMessageParser.parseMtkMessage(source)
    roadlinks.size must equal(6)
  }

  "MtkRoadLinkUtils" must "form correct ordinates include" in {
    val value = MtkRoadLinkUtils.fromPointListToStoringGeometry(roadlinkWithEnddate)
    value must equal("375295.264, 6678420.423, 36.410, null, 375290.952, 6678385.118, 35.027, null")
  }

  it must "form correct ordinates include with differen values" in {
    val value = MtkRoadLinkUtils.fromPointListToStoringGeometry(roadlinkWithoutEnddate)
    value must equal("375295.264, 6678420.423, 36.410, null, 375290.952, 6678385.118, 35.027, null")
  }
}
