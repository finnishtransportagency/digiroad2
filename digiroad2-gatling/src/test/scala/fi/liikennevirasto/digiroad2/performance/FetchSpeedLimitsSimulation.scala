package fi.liikennevirasto.digiroad2.performance

import java.util.concurrent.TimeUnit
import io.gatling.core.Predef._
import io.gatling.core.feeder.RecordSeqFeederBuilder
import io.gatling.http.Predef._
import scala.concurrent.duration.FiniteDuration

object FetchBoundingBox {
  val feeder: RecordSeqFeederBuilder[String] = Array(
    Map("bbox" -> "372056,6674877,375624,6680169"),
    Map("bbox" -> "372547,6675156,376115,6680448"),
    Map("bbox" -> "371332,6676345,374900,6681637")).random

  val fetch = feed(feeder)
    .exec(http("Fetch road links").get("/roadlinks2?bbox=${bbox}"))
    .exec(http("Fetch speed limits").get("/speedlimits?bbox=${bbox}"))
}

class FetchSpeedLimitsSimulation extends Simulation {
  val httpConf = http
    .baseURL(TestConfiguration.apiUrl)
    .header("Cookie", "testusername=tarutest")

  val scn = scenario("Fetch speed limits by bounding box on maximum zoom level")
    .exec(FetchBoundingBox.fetch)

  private val userCount: Int = TestConfiguration.users
  private val duration: FiniteDuration = FiniteDuration(userCount, TimeUnit.SECONDS)

  setUp(scn.inject(rampUsers(userCount) over duration)).protocols(httpConf)
}
