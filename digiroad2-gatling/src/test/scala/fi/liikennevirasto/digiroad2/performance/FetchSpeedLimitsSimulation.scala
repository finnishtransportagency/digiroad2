package fi.liikennevirasto.digiroad2.performance

import java.util.concurrent.TimeUnit
import io.gatling.core.Predef._
import io.gatling.core.feeder.RecordSeqFeederBuilder
import io.gatling.http.Predef._
import scala.concurrent.duration.FiniteDuration

object FetchBoundingBox {
  private val kauniainenBoundingBoxes: Array[Map[String, String]] = Array(
    Map("bbox" -> "370731,6676352,377587,6679304"),
    Map("bbox" -> "370383,6676384,377239,6679336"),
    Map("bbox" -> "369867,6676300,376723,6679252"),
    Map("bbox" -> "370363,6676792,377219,6679744"),
    Map("bbox" -> "370811,6676876,377667,6679828"),
    Map("bbox" -> "371415,6677368,378271,6680320"),
    Map("bbox" -> "371666,6676220,378522,6679172"),
    Map("bbox" -> "371611,6675404,378467,6678356"),
    Map("bbox" -> "370651,6675205,377507,6678157"),
    Map("bbox" -> "371322,6675148,378178,6678100"))

  val feeder: RecordSeqFeederBuilder[String] = kauniainenBoundingBoxes.random

  val fetch =
    group("load speed limits") {
      feed(feeder)
      .exec(http("Fetch road links").get("/roadlinks2?bbox=${bbox}"))
      .exec(http("Fetch speed limits").get("/speedlimits?bbox=${bbox}"))
    }
}

class FetchSpeedLimitsSimulation extends Simulation {
  val httpConf = http
    .baseURL(TestConfiguration.apiUrl)
    .header("Cookie", "testusername=tarutest")
    .header("OAM_REMOTE_USER", TestConfiguration.username)

  val scn = scenario("Fetch speed limits by bounding box on maximum zoom level")
    .exec(FetchBoundingBox.fetch)

  private val userCount: Int = TestConfiguration.users
  private val duration: FiniteDuration = FiniteDuration(userCount, TimeUnit.SECONDS)

  setUp(scn.inject(rampUsers(userCount) over duration)).protocols(httpConf)
}
