package fi.liikennevirasto.digiroad2.client

import com.vividsolutions.jts.geom.GeometryFactory
import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.{BoundingRectangle, LinkGeomSource}
import fi.liikennevirasto.digiroad2.client.kgv.{KgvCollection, KgvMunicipalityBorderClient, KgvRoadLinkClient}
import org.geotools.geometry.jts.GeometryBuilder
import org.joda.time.DateTime
import org.scalatest.{FunSuite, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class KgvRoadLinkClientSpec extends FunSuite with Matchers{

  val geomFact= new GeometryFactory()
  val geomBuilder = new GeometryBuilder(geomFact)
  val (collection,linkGeomSourceValue)=(
    Some(KgvCollection.Frozen),
    Some(LinkGeomSource.FrozenLinkInterface))
  // Run these test manually by changing ignore -> test
  ignore("Fetch roadlinks with polygon string 2") {
    val polygonTest2 = geomBuilder.polygon(
      6673093.5, 25496486.5,
      6672845.5, 25496668.5,
      6672879.5, 25496731.5,
      6673107.5, 25496557.5,
      6673093.5, 25496486.5
    )

    val roadLinkClient = new KgvRoadLinkClient(collection,linkGeomSourceValue)
    val result = roadLinkClient.fetchByPolygonF(polygonTest2)

    val result1 = Await.result(result, atMost = Duration.Inf)
    result1.size should be > 1
  }

  ignore("Fetch roadlinks with id") {
    val roadLinkClient = new KgvRoadLinkClient(collection,linkGeomSourceValue)
    val result = roadLinkClient.fetchByLinkId("0001c8d6-96d2-4006-9d0b-5d56530f9442:1")
    result.size should be(1)
  }

  ignore("Fetch roadlinks with two id") {
    val roadLinkClient = new KgvRoadLinkClient(collection,linkGeomSourceValue)
    val result = roadLinkClient.fetchByLinkIds(Set("0001c8d6-96d2-4006-9d0b-5d56530f9442:1", "00018b38-fc36-4468-a3a8-a71f7f88de34:1"))
    result.size should be(2)
  }

  ignore("Fetch roadlink with bbox") {
    val roadLinkClient = new KgvRoadLinkClient(collection,linkGeomSourceValue)
    val result = roadLinkClient.fetchByMunicipalitiesAndBounds(BoundingRectangle(Point(380310.0517099907, 6677916.351437694), Point(380580.0517099907, 6678083.226437694)), Set())
    result.size should be(26)
  }

  ignore("Fetch roadlink with bbox, Kuopio 200m") {
    val roadLinkClient = new KgvRoadLinkClient(collection,linkGeomSourceValue)
    val result = roadLinkClient.fetchByMunicipalitiesAndBounds(BoundingRectangle(Point(531785.8749662223, 6972615.888297602), Point(536105.8749662223, 6975285.888297602)), Set())
    result.size should be(2307)
  }

  ignore("Fetch roadlink with bbox in municipality border area, select link only from one") {
    val roadLinkClient = new KgvRoadLinkClient(collection,linkGeomSourceValue)
    val result = roadLinkClient.fetchByMunicipalitiesAndBounds(BoundingRectangle(Point(380310.0517099907, 6677916.351437694), Point(380580.0517099907, 6678083.226437694)), Set(49))
    result.size should be(23)
  }
  ignore("Fetch roadlink with bbox and municipality 49 and 91") {
    val roadLinkClient = new KgvRoadLinkClient(collection,linkGeomSourceValue)
    val result = roadLinkClient.fetchByMunicipalitiesAndBounds(BoundingRectangle(Point(380310.0517099907, 6677916.351437694), Point(380580.0517099907, 6678083.226437694)), Set(49, 91))
    result.size should be(26)
  }

  ignore("Fetch roadlink with municipality ") {
    val roadLinkClient = new KgvRoadLinkClient(collection,linkGeomSourceValue)
    val begin = System.currentTimeMillis()
    val result: Seq[RoadLinkFetched] = roadLinkClient.fetchByMunicipality(297)
    val duration = System.currentTimeMillis() - begin
    println(s"roadlink with municipality completed in $duration ms and in second ${duration / 1000}")
    // in old environment Kuopio has 62432 link
    result.size should be(61272)
  }

  ignore("Fetch roadlink with municipality small") {
    val roadLinkClient = new KgvRoadLinkClient(collection,linkGeomSourceValue)
    val begin = System.currentTimeMillis()
    val result = roadLinkClient.fetchByMunicipality(766)
    println(result.head.linkId)
    val duration = System.currentTimeMillis() - begin
    println(s"roadlink with municipality completed in $duration ms and in second ${duration / 1000}")
    result.size should be(269)
  }

  ignore("Fetch roadlink with municipality mtk") {
    val roadLinkClient = new KgvRoadLinkClient(collection,linkGeomSourceValue)
    val begin = System.currentTimeMillis()
    val result = roadLinkClient.fetchWalkwaysByMunicipalitiesF(297)
    val result1 = Await.result(result, atMost = Duration.Inf)
    val duration = System.currentTimeMillis() - begin
    println(s"roadlink with municipality completed in $duration ms and in second ${duration / 1000}")
    result1.size should be(3342)
  }

  ignore("fetch by fetchByChangesDates") {
    val roadLinkClient = new KgvRoadLinkClient(collection,linkGeomSourceValue)
    val begin = System.currentTimeMillis()
    val result = roadLinkClient.fetchByChangesDates(DateTime.parse("2022-03-30T00:00:00.000Z"), DateTime.parse("2022-04-1T12:31:12.000Z"))
    val duration = System.currentTimeMillis() - begin
    println(s"roadlink with municipality completed in $duration ms and in second ${duration / 1000}")
    result.size should be(62432)
  }

  ignore("fetch by date fetchByDatetime") {
    val roadLinkClient = new KgvRoadLinkClient(collection,linkGeomSourceValue)
    val begin = System.currentTimeMillis()
    val result = roadLinkClient.fetchByDatetime(DateTime.parse("2018-02-12T00:00:00Z"), DateTime.parse("2018-02-13T12:31:12Z"))
    val duration = System.currentTimeMillis() - begin
    println(s"roadlink with municipality completed in $duration ms and in second ${duration / 1000}")
    result.size should be(4447)
  }
}
