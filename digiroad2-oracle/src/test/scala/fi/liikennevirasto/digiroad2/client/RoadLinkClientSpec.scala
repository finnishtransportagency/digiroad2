package fi.liikennevirasto.digiroad2.client

import com.vividsolutions.jts.geom.GeometryFactory
import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.BoundingRectangle
import fi.liikennevirasto.digiroad2.client.vvh.{RoadLinkClient, RoadlinkFetchedMtk}
import fi.liikennevirasto.digiroad2.util.Digiroad2Properties
import org.geotools.geometry.jts.GeometryBuilder
import org.joda.time.DateTime
import org.scalatest.{FunSuite, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

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

  test("Fetch roadlinks with polygon string ") {
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
    val result = roadLinkClient.historyData.fetchVVHRoadLinkByLinkIds(Set(440484,440606,440405,440489))
    result.nonEmpty should be (true)
  }
  test("Fetch changes with polygon string ") {
    val roadLinkClient= new RoadLinkClient(Digiroad2Properties.vvhRestApiEndPoint)
    val result= roadLinkClient.roadLinkChangeInfo.fetchByPolygon(geomBuilder.polygon(528428,6977212,543648,6977212,543648,7002668,528428,7002668))
    result.size should be >1
  }
  test("Fetch changes with empty polygon string") {
    val roadLinkClient= new RoadLinkClient(Digiroad2Properties.vvhRestApiEndPoint)
    val result= roadLinkClient.roadLinkChangeInfo.fetchByPolygon(geomBuilder.polygon())
    result.size should be (0)
  }
  test("Fetch changes with by bounding box and municipalities") {
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

  test("Test Change Info fetch by LinkId") {
    val roadLinkClient = new RoadLinkClient(Digiroad2Properties.vvhRestApiEndPoint)
    val result = roadLinkClient.roadLinkChangeInfo.fetchByLinkIds(Set(5176799))
    result.nonEmpty should be (true)
  }

  ignore("Fetch roadlinks with polygon string 2") {
    val polygonTest2 =geomBuilder.polygon(
      6673093.5,25496486.5,
      6672845.5,25496668.5,
      6672879.5,25496731.5,
      6673107.5,25496557.5,
      6673093.5,25496486.5
    )

    val vvhClient= new RoadLinkClient(Digiroad2Properties.kmtkEndpoint)
    val result= vvhClient.roadLinkData2.fetchByPolygonF(polygonTest2)

    val result1 = Await.result(result,atMost = Duration.Inf)
    result1.size should be >1
  }

  ignore("Fetch roadlinks with id") {
    val vvhClient= new RoadLinkClient(Digiroad2Properties.kmtkEndpoint)
    val result= vvhClient.roadLinkData2.fetchByLinkId("000253b3-4dc1-42de-b4b8-f469e4c65283:1")
    result.size should be (1)
  }

  ignore("Fetch roadlinks with two id") {
    val vvhClient= new RoadLinkClient(Digiroad2Properties.kmtkEndpoint)
    val result= vvhClient.roadLinkData2.fetchByLinkIds(Set("000253b3-4dc1-42de-b4b8-f469e4c65283:1","0023d7fc-18b9-4d67-bfdc-5c4176c80fba:1"))
    result.size should be (2)
  }

  ignore("Fetch roadlink with bbox") {
    val vvhClient= new RoadLinkClient(Digiroad2Properties.kmtkEndpoint)
    val result= vvhClient.roadLinkData2.fetchByMunicipalitiesAndBounds(BoundingRectangle(Point(380310.0517099907, 6677916.351437694),Point(380580.0517099907, 6678083.226437694)),Set())
    result.size should be (26)
  }

  ignore("Fetch roadlink with bbox, Kuopio 200m") {
    val vvhClient= new RoadLinkClient(Digiroad2Properties.kmtkEndpoint)
    val result= vvhClient.roadLinkData2.fetchByMunicipalitiesAndBounds(BoundingRectangle(Point(531785.8749662223,6972615.888297602),Point(536105.8749662223,6975285.888297602)),Set())
    result.size should be (2307)
  }

  ignore("Fetch roadlink with bbox in municipality border area, select link only from one") {
    val vvhClient= new RoadLinkClient(Digiroad2Properties.kmtkEndpoint)
    val result= vvhClient.roadLinkData2.fetchByMunicipalitiesAndBounds(BoundingRectangle(Point(380310.0517099907, 6677916.351437694),Point(380580.0517099907, 6678083.226437694)), Set(49))
    result.size should be (23)
  }
  ignore("Fetch roadlink with bbox and municipality 49 and 91") {
    val vvhClient= new RoadLinkClient(Digiroad2Properties.kmtkEndpoint)
    val result= vvhClient.roadLinkData2.fetchByMunicipalitiesAndBounds(BoundingRectangle(Point(380310.0517099907, 6677916.351437694),Point(380580.0517099907, 6678083.226437694)), Set(49,91))
    result.size should be (26)
  }

  ignore("Fetch roadlink with municipality ") {
    val vvhClient= new RoadLinkClient(Digiroad2Properties.kmtkEndpoint)
    val begin = System.currentTimeMillis()
    val result:Seq[RoadlinkFetchedMtk]= vvhClient.roadLinkData2.fetchByMunicipality(297)
    val duration = System.currentTimeMillis() - begin
    println(s"roadlink with municipality completed in $duration ms and in second ${duration / 1000}")
    // in old environment Kuopio has 62432 link
    result.size should be (61272)
  }

  ignore("Fetch roadlink with municipality small") {
    val vvhClient= new RoadLinkClient(Digiroad2Properties.kmtkEndpoint)
    val begin = System.currentTimeMillis()
    val result= vvhClient.roadLinkData2.fetchByMunicipality(766)
    println(result.head.linkId)
    val duration = System.currentTimeMillis() - begin
    println(s"roadlink with municipality completed in $duration ms and in second ${duration / 1000}")
    result.size should be (269)
  }

  ignore("Fetch roadlink with municipality mtk") {
    val vvhClient= new RoadLinkClient(Digiroad2Properties.kmtkEndpoint)
    val begin = System.currentTimeMillis()
    val result= vvhClient.roadLinkData2.fetchWalkwaysByMunicipalitiesF(297)
    val result1 = Await.result(result,atMost = Duration.Inf)
    val duration = System.currentTimeMillis() - begin
    println(s"roadlink with municipality completed in $duration ms and in second ${duration / 1000}")
    result1.size should be (3342)
  }

  ignore("Fetch roadlink with roadname") {
    val vvhClient= new RoadLinkClient(Digiroad2Properties.kmtkEndpoint)
    val begin = System.currentTimeMillis()
    val result= vvhClient.roadLinkData2.fetchByRoadNames("roadnamefin",Set("Rantatie"))
    val duration = System.currentTimeMillis() - begin
    println(s"roadlink with municipality completed in $duration ms and in second ${duration / 1000}")
    result.size should be (2286)
  }
  ignore("Fetch roadlink with two road name") {
    val vvhClient= new RoadLinkClient(Digiroad2Properties.kmtkEndpoint)
    val begin = System.currentTimeMillis()
    val result= vvhClient.roadLinkData2.fetchByRoadNames("roadnamefin",Set("Rantatie","Mannerheimintie"))
    val duration = System.currentTimeMillis() - begin
    println(s"roadlink with municipality completed in $duration ms and in second ${duration / 1000}")
    result.size should be (2473)
  }

  ignore("Fetch roadlink with roadname Tarkk'ampujankuja") {
    val vvhClient= new RoadLinkClient(Digiroad2Properties.kmtkEndpoint)
    val begin = System.currentTimeMillis()
    val result= vvhClient.roadLinkData2.fetchByRoadNames("roadnamefin",Set("Tarkk'ampujankuja"))
    val duration = System.currentTimeMillis() - begin
    println(s"roadlink with municipality completed in $duration ms and in second ${duration / 1000}")
    result.size should be (2286)
  }

  ignore("fetch by fetchByChangesDates"){
    val vvhClient= new RoadLinkClient(Digiroad2Properties.kmtkEndpoint)
    val begin = System.currentTimeMillis()
    val result= vvhClient.roadLinkData2.fetchByChangesDates(DateTime.parse("2022-03-30T00:00:00.000Z"), DateTime.parse("2022-04-1T12:31:12.000Z"))
    val duration = System.currentTimeMillis() - begin
    println(s"roadlink with municipality completed in $duration ms and in second ${duration / 1000}")
    result.size should be (62432)
  }

  ignore("fetch by date fetchByDatetime"){
    val vvhClient= new RoadLinkClient(Digiroad2Properties.kmtkEndpoint)
    val begin = System.currentTimeMillis()
    val result= vvhClient.roadLinkData2.fetchByDatetime(DateTime.parse("2018-02-12T00:00:00Z"), DateTime.parse("2018-02-13T12:31:12Z"))
    val duration = System.currentTimeMillis() - begin
    println(s"roadlink with municipality completed in $duration ms and in second ${duration / 1000}")
    result.size should be (4447)
  }
}

