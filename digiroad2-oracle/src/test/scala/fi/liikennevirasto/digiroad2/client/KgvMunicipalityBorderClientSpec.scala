package fi.liikennevirasto.digiroad2.client

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource
import fi.liikennevirasto.digiroad2.client.kgv.{KgvCollection, KgvMunicipalityBorderClient}

import org.scalatest.{FunSuite, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class KgvMunicipalityBorderClientSpec extends FunSuite with Matchers{

  test("Should fetch municipalities") {
    val (collection, linkGeomSourceValue) = (
      Some(KgvCollection.MunicipalityBorders),
      Some(LinkGeomSource.Unknown))
    val municipalityBorderClient = new KgvMunicipalityBorderClient(collection, linkGeomSourceValue)
    val begin = System.currentTimeMillis()
    val municipalities = Await.result(municipalityBorderClient.fetchAllMunicipalitiesF(), atMost = Duration.Inf)
    val duration = System.currentTimeMillis() - begin
    println(s"all Municipalities fetched in $duration ms and in second ${duration / 1000}")
    municipalities.size should be(309)
  }

  test("Should find correct Municipality with a given point") {
    val (collection, linkGeomSourceValue) = (
      Some(KgvCollection.MunicipalityBorders),
      Some(LinkGeomSource.Unknown))
    val municipalityBorderClient = new KgvMunicipalityBorderClient(collection, linkGeomSourceValue)
    val begin = System.currentTimeMillis()
    val municipalities = Await.result(municipalityBorderClient.fetchAllMunicipalitiesF(), atMost = Duration.Inf)
    val point = Point(594921.82429447, 7342797.0430049)
    val municipality = municipalityBorderClient.findMunicipalityForPoint(point, municipalities)
    val duration = System.currentTimeMillis() - begin
    println(s"correct Municipality found in $duration ms and in second ${duration / 1000}")
    municipality should not be (None)
    municipality.get.municipalityCode should be(305)
  }
}
