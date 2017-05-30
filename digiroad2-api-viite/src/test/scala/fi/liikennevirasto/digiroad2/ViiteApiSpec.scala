package fi.liikennevirasto.digiroad2

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
}
