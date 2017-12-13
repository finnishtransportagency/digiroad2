package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.LinkGeomSource
import fi.liikennevirasto.digiroad2.user.{Configuration, User}
import fi.liikennevirasto.digiroad2.util.{DigiroadSerializers, Track}
import fi.liikennevirasto.viite.RoadType
import fi.liikennevirasto.viite.dao.{Discontinuity, LinkStatus, ProjectCoordinates}
import fi.liikennevirasto.viite.util.SplitOptions
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization
import org.scalatest.{BeforeAndAfter, FunSuite}
import org.scalatra.test.scalatest.ScalatraSuite

class ViiteApiSpec extends FunSuite with ScalatraSuite with BeforeAndAfter{
  protected implicit val jsonFormats: Formats = DigiroadSerializers.jsonFormats

  test("Conversion from JSON to Road Address Project") {
    val str = "{\"id\":0,\"status\":1,\"name\":\"erwgerg\",\"startDate\":\"22.4.2017\",\"additionalInfo\":\"\",\"projectEly\":5,\"roadPartList\":[{\"roadPartNumber\":205,\"roadNumber\":5,\"ely\":5,\"roadLength\":6730,\"roadPartId\":30,\"discontinuity\":\"Jatkuva\"}],\"resolution\":8}"
    val json = parse(str)
    json.extract[RoadAddressProjectExtractor].roadPartList should have size(1)
  }

  test("Conversion from extracted project with too long name is successful") {
    val str = "{\"id\":0,\"projectEly\":5,\"status\":1,\"name\":\"ABCDEFGHIJKLMNOPQRSTUVWXYZ01234567890\",\"startDate\":\"22.4.2017\",\"additionalInfo\":\"\",\"roadPartList\":[],\"resolution\":8}"
    val json = parse(str)
    val extracted = json.extract[RoadAddressProjectExtractor]
    extracted.name should have length(37)
    val project = ProjectConverter.toRoadAddressProject(extracted, User(1, "user", Configuration()))
    project.name should have length(32)
  }

  test("Conversion of SplitOptions objects") {
    val options = SplitOptions(Point(123,456,789), LinkStatus.New, LinkStatus.Terminated, 4L, 5L, Track.Combined,
      Discontinuity.MinorDiscontinuity, 9L, LinkGeomSource.SuravageLinkInterface, RoadType.PublicRoad, 4L, ProjectCoordinates(0, 1, 1))
    val s = Serialization.write(options)
    val json=parse(s)
    val read = json.extract[SplitOptions]
    read should be (options)
  }

  test("Conversion of Point objects without z") {
    val point = Point(0.2,5.0)
    val str = "{\"x\":0.2,\"y\":5.0}"
    val json=parse(str)
    val read = json.extract[Point]
    read should be (point)
  }
}
