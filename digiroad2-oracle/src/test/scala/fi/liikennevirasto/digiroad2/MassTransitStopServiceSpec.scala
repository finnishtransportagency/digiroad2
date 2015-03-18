package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.{ValidityPeriod, BoundingRectangle}
import fi.liikennevirasto.digiroad2.user.{Configuration, User}
import org.scalatest.{Matchers, FunSuite}
import org.scalatest.mock.MockitoSugar
import org.mockito.Mockito._
import org.mockito.Matchers.any

import scala.slick.jdbc.{StaticQuery => Q}

class MassTransitStopServiceSpec extends FunSuite with Matchers {
  val boundingBoxWithKauniainenAssets = BoundingRectangle(Point(374000,6677000), Point(374800,6677600))
  val userWithKauniainenAuthorization = User(
    id = 1,
    username = "Hannu",
    configuration = Configuration(authorizedMunicipalities = Set(235)))
  val roadLinkService = MockitoSugar.mock[RoadLinkService]
  when(roadLinkService.fetchVVHRoadlinks(any[BoundingRectangle], any[Set[Int]])).thenReturn(List((1140018963l, 235, Nil), (388554364l, 235, Nil)))

  test("Calculate mass transit stop validity periods") {
    val massTransitStops = MassTransitStopService.getByBoundingBox(userWithKauniainenAuthorization, boundingBoxWithKauniainenAssets, roadLinkService)
    massTransitStops.find(_.id == 300000).map(_.validityPeriod) should be(Some(ValidityPeriod.Current))
    massTransitStops.find(_.id == 300001).map(_.validityPeriod) should be(Some(ValidityPeriod.Past))
    massTransitStops.find(_.id == 300003).map(_.validityPeriod) should be(Some(ValidityPeriod.Future))
  }

  test("Get stops by bounding box") {
    val stops = MassTransitStopService.getByBoundingBox(userWithKauniainenAuthorization, BoundingRectangle(Point(374443, 6677245), Point(374444, 6677246)), roadLinkService)
    stops.size shouldBe 1
  }

  test("Filter stops by authorization") {
    val stops = MassTransitStopService.getByBoundingBox(User(0, "test", Configuration()), boundingBoxWithKauniainenAssets, roadLinkService)
    stops should be(empty)
  }

  test("Stop floats if road link does not exist") {
    val stops = MassTransitStopService.getByBoundingBox(userWithKauniainenAuthorization, boundingBoxWithKauniainenAssets, roadLinkService)
    stops.find(_.id == 300000).map(_.floating) should be(Some(true))
  }

}
