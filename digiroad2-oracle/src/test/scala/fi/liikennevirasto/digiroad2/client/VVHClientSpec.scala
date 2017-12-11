package fi.liikennevirasto.digiroad2.client

import java.util.Properties

import com.vividsolutions.jts.geom.GeometryFactory
import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import org.geotools.geometry.jts.GeometryBuilder
import org.scalatest.{FunSuite, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class VVHClientSpec extends FunSuite with Matchers{
  lazy val properties: Properties = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/digiroad2.properties"))
    props
  }

  val geomFact= new GeometryFactory()
  val geomBuilder = new GeometryBuilder(geomFact)

  /**
    * Checks that VVH history bounding box search works uses API example bounding box so it should receive results
    */
  test("Tries to connect VVH history API and retrive result") {
    val vvhClient= new VVHClient(properties.getProperty("digiroad2.VVHRestApiEndPoint"))
    val result= vvhClient.historyData.fetchByMunicipalitiesAndBounds(BoundingRectangle(Point(564000, 6930000),Point(566000, 6931000)), Set(420))
    result.size should be >1
  }

  test("Fetch roadlinks with polygon string ") {
    val vvhClient= new VVHClient(properties.getProperty("digiroad2.VVHRestApiEndPoint"))
    val result= vvhClient.roadLinkData.fetchByPolygon(geomBuilder.polygon(564000,6930000,566000,6931000,567000,6933000))
    result.size should be >1
  }

  test("Fetch roadlinks with empty polygon string") {
    val vvhClient= new VVHClient(properties.getProperty("digiroad2.VVHRestApiEndPoint"))
    val result= vvhClient.roadLinkData.fetchByPolygon(geomBuilder.polygon())
    result.size should be (0)
  }
  /**
    * Checks that VVH history link id search works and returns something
    */
  test("Test VVH History LinkId API") {
    val vvhClient= new VVHClient(properties.getProperty("digiroad2.VVHRestApiEndPoint"))
    val result = vvhClient.historyData.fetchVVHRoadLinkByLinkIds(Set(440484,440606,440405,440489))
    result.nonEmpty should be (true)
  }
  test("Fetch changes with polygon string ") {
    val vvhClient= new VVHClient(properties.getProperty("digiroad2.VVHRestApiEndPoint"))
    val result= vvhClient.roadLinkChangeInfo.fetchByPolygon(geomBuilder.polygon(528428,6977212,543648,6977212,543648,7002668,528428,7002668))
    result.size should be >1
  }
  test("Fetch changes with empty polygon string") {
    val vvhClient= new VVHClient(properties.getProperty("digiroad2.VVHRestApiEndPoint"))
    val result= vvhClient.roadLinkChangeInfo.fetchByPolygon(geomBuilder.polygon())
    result.size should be (0)
  }
  test("Fetch changes with by bounding box and municipalities") {
    val vvhClient= new VVHClient(properties.getProperty("digiroad2.VVHRestApiEndPoint"))
    val result= vvhClient.roadLinkChangeInfo.fetchByBoundsAndMunicipalities(BoundingRectangle(Point(532578.3338013917,6993401.605560873,0.0),Point(532978.3338013917,6994261.605560873,0.0)), Set.empty[Int])
    result.size should be >1
  }
  /**
    * Test for Suravage API
    */
  ignore("Fetch suravage by boundingbox"){ //When writing this test no test area was provided in VVH documentation, so area might fail because area has no links anymore
    val vvhClient= new VVHClient(properties.getProperty("digiroad2.VVHRestApiEndPoint"))
    Await.result(vvhClient.suravageData.fetchSuravageByMunicipalitiesAndBoundsF(BoundingRectangle(Point(445000, 7000000),Point(446000, 7005244)), Set(931)), Duration.Inf).size should be > 0
  }

  /**
    * Test for frozen december 15.12.2016 VVH API: No test cases writen to documentation so test might fail for not having any links
  */

  test("Frozen In Time API test ") {
    val frozenApiEnabled = properties.getProperty("digiroad2.VVHRoadlink.frozen")
    if (frozenApiEnabled=="true") { //Api only exists in QA and Production
      val vvhClient= new VVHClient(properties.getProperty("digiroad2.VVHRestApiEndPoint"))
      val result= vvhClient.frozenTimeRoadLinkData.fetchByBounds(BoundingRectangle(Point(445000, 7000000),Point(446000, 7005244)))
      result.size should be >1
    }
  }

  test("Test Change Info fetch by LinkId") {
    val vvhClient= new VVHClient(properties.getProperty("digiroad2.VVHRestApiEndPoint"))
    val result = vvhClient.roadLinkChangeInfo.fetchByLinkIds(Set(5176799))
    result.nonEmpty should be (true)
  }
}

