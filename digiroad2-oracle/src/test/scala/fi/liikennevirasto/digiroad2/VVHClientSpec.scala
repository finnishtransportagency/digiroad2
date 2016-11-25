package fi.liikennevirasto.digiroad2

import java.util.Properties

import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import org.scalatest.{FunSuite, Matchers}

class VVHClientSpec extends FunSuite with Matchers{
  lazy val properties: Properties = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/digiroad2.properties"))
    props
  }


  /**
    * Checks that VVH history bounding box search works uses API example bounding box so it should receive results
    */
  test("Tries to connect VVH history API and retrive result") {
    val vvhClient= new VVHClient(properties.getProperty("digiroad2.VVHRestApiEndPoint"))
    val result= vvhClient.historyData.fetchVVHRoadlinkHistoryByBoundsAndMunicipalities(BoundingRectangle(Point(564000, 6930000),Point(566000, 6931000)), Set(420))
     result.size should be >1
    }
}
