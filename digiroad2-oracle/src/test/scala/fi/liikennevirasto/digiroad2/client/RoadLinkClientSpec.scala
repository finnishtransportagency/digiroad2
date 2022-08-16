package fi.liikennevirasto.digiroad2.client

import com.vividsolutions.jts.geom.GeometryFactory
import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import fi.liikennevirasto.digiroad2.client.vvh.RoadLinkClient
import fi.liikennevirasto.digiroad2.util.Digiroad2Properties
import org.geotools.geometry.jts.GeometryBuilder
import org.scalatest.{FunSuite, Matchers}

class RoadLinkClientSpec extends FunSuite with Matchers{

  val geomFact= new GeometryFactory()
  val geomBuilder = new GeometryBuilder(geomFact)

  /**
    * Checks that VVH history bounding box search works uses API example bounding box so it should receive results
    */
  test("Tries to connect VVH history API and retrive result") {
    val roadLinkClient= new RoadLinkClient(Digiroad2Properties.vvhRestApiEndPoint)
    val result= roadLinkClient.historyData.fetchByMunicipalitiesAndBounds(BoundingRectangle(Point(564000, 6930000),Point(566000, 6931000)), Set(420))
    result.size should be >1
  }

  ignore("Fetch roadlinks with polygon string ") {
    val roadLinkClient= new RoadLinkClient(Digiroad2Properties.vvhRestApiEndPoint)
    val result= roadLinkClient.roadLinkData.fetchByPolygon(geomBuilder.polygon(564000,6930000,566000,6931000,567000,6933000))
    result.size should be >1
  }

  test("Fetch roadlinks with empty polygon string") {
    val roadLinkClient= new RoadLinkClient(Digiroad2Properties.vvhRestApiEndPoint)
    val result= roadLinkClient.roadLinkData.fetchByPolygon(geomBuilder.polygon())
    result.size should be (0)
  }
  /**
    * Checks that VVH history link id search works and returns something
    */
  test("Test VVH History LinkId API") {
    val roadLinkClient = new RoadLinkClient(Digiroad2Properties.vvhRestApiEndPoint)
    val result = roadLinkClient.historyData.fetchVVHRoadLinkByLinkIds(Set("440484","440606","440405","440489"))
    result.nonEmpty should be (true)
  }
  //Ignored due to DROTH-3311, enable again when change info is fetched
  ignore("Fetch changes with polygon string ") {
    val roadLinkClient= new RoadLinkClient(Digiroad2Properties.vvhRestApiEndPoint)
    val result= roadLinkClient.roadLinkChangeInfo.fetchByPolygon(geomBuilder.polygon(528428,6977212,543648,6977212,543648,7002668,528428,7002668))
    result.size should be >1
  }
  //Ignored due to DROTH-3311, enable again when change info is fetched
  ignore("Empty polygon should not return anything") {
    val roadLinkClient= new RoadLinkClient(Digiroad2Properties.vvhRestApiEndPoint)
    val result= roadLinkClient.roadLinkChangeInfo.fetchByPolygon(geomBuilder.polygon())
    result.size should be (0)
  }
  //Ignored due to DROTH-3311, enable again when change info is fetched
  ignore("Fetch changes with by bounding box and municipalities") {
    val roadLinkClient = new RoadLinkClient(Digiroad2Properties.vvhRestApiEndPoint)
    val result= roadLinkClient.roadLinkChangeInfo.fetchByBoundsAndMunicipalities(BoundingRectangle(Point(532578.3338013917,6993401.605560873,0.0),Point(532978.3338013917,6994261.605560873,0.0)), Set.empty[Int])
    result.size should be >1
  }

  /**
    * Test for frozen december 15.12.2016 VVH API: No test cases writen to documentation so test might fail for not having any links
  */

  test("Frozen In Time API test ") {
    val frozenApiEnabled = Digiroad2Properties.vvhRoadlinkFrozen
    if (frozenApiEnabled=="true") { //Api only exists in QA and Production
      val roadLinkClient = new RoadLinkClient(Digiroad2Properties.vvhRestApiEndPoint)
      val result= roadLinkClient.frozenTimeRoadLinkData.fetchByBounds(BoundingRectangle(Point(445000, 7000000),Point(446000, 7005244)))
      result.size should be >1
    }
  }

  //Ignored due to DROTH-3311, enable again when change info is fetched
  ignore("Test Change Info fetch by LinkId") {
    val roadLinkClient = new RoadLinkClient(Digiroad2Properties.vvhRestApiEndPoint)
    val result = roadLinkClient.roadLinkChangeInfo.fetchByLinkIds(Set("5176799"))
    result.nonEmpty should be (true)
  }
}

