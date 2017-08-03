package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.user.{Configuration, User}
import org.scalatest.{BeforeAndAfter, FunSuite, Tag}
import org.scalatra.test.scalatest.ScalatraSuite
import org.scalatest.FunSuite
import org.json4s._
import org.json4s.jackson.Json
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization.write

class ViiteApiSpec extends FunSuite with ScalatraSuite with BeforeAndAfter{
  protected implicit val jsonFormats: Formats = DefaultFormats + DiscontinuitySerializer

  test("Conversion from JSON to Road Address Project") {
    val str = "{\"id\":0,\"status\":1,\"name\":\"erwgerg\",\"startDate\":\"22.4.2017\",\"additionalInfo\":\"\",\"roadPartList\":[{\"roadPartNumber\":205,\"roadNumber\":5,\"ely\":5,\"length\":6730,\"roadPartId\":30,\"discontinuity\":\"Jatkuva\"}]}"
    val json = parse(str)
    json.extract[RoadAddressProjectExtractor].roadPartList should have size(1)
  }

  test("Conversion from extracted project with too long name is successful") {
    val str = "{\"id\":0,\"status\":1,\"name\":\"ABCDEFGHIJKLMNOPQRSTUVWXYZ01234567890\",\"startDate\":\"22.4.2017\",\"additionalInfo\":\"\",\"roadPartList\":[]}"
    val json = parse(str)
    val extracted = json.extract[RoadAddressProjectExtractor]
    extracted.name should have length(37)
    val project = ProjectConverter.toRoadAddressProject(extracted, User(1, "user", Configuration()))
    project.name should have length(32)
  }
}
