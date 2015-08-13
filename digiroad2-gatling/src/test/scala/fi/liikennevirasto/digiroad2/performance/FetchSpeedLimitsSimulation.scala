package fi.liikennevirasto.digiroad2.performance

import java.util.concurrent.TimeUnit
import io.gatling.core.Predef._
import io.gatling.core.feeder.RecordSeqFeederBuilder
import io.gatling.http.Predef._
import scala.concurrent.duration.FiniteDuration

object FetchBoundingBox {
  private val kauniainenBoundingBoxes: Array[Map[String, String]] = Array(
    Map("bbox" -> "372863,6677493,374577,6678231"),
    Map("bbox" -> "372683,6677691,374397,6678429"),
    Map("bbox" -> "372365,6677455,374079,6678193"),
    Map("bbox" -> "372513,6677164,374227,6677902"),
    Map("bbox" -> "372716,6676936,374430,6677674"),
    Map("bbox" -> "372979,6676853,374693,6677591"),
    Map("bbox" -> "373320,6676868,375034,6677606"),
    Map("bbox" -> "373680,6676910,375394,6677648"),
    Map("bbox" -> "373845,6677212,375559,6677950"),
    Map("bbox" -> "373848,6677471,375562,6678209"))

  val feeder: RecordSeqFeederBuilder[String] = kauniainenBoundingBoxes.random

  val fetch = feed(feeder)
    .exec(http("Fetch road links").get("/roadlinks2?bbox=${bbox}"))
    .exec(http("Fetch speed limits").get("/speedlimits?bbox=${bbox}"))
}

class FetchSpeedLimitsSimulation extends Simulation {
  val httpConf = http
    .baseURL(TestConfiguration.apiUrl)
    .header("OAM_REMOTE_USER", TestConfiguration.username)

  val scn = scenario("Fetch speed limits by bounding box on maximum zoom level")
    .exec(FetchBoundingBox.fetch)

  private val userCount: Int = TestConfiguration.users
  private val duration: FiniteDuration = FiniteDuration(userCount, TimeUnit.SECONDS)

  setUp(scn.inject(rampUsers(userCount) over duration)).protocols(httpConf)
}
