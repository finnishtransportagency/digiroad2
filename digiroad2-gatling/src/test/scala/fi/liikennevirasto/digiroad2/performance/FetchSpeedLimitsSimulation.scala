package fi.liikennevirasto.digiroad2.performance

import io.gatling.core.Predef._
import io.gatling.http.Predef._

class FetchSpeedLimitsSimulation extends Simulation {
  val httpConf = http
    .baseURL("http://10.129.47.148:8080/digiroad/api/")
    .header("Cookie", "testusername=test2")

  val scn = scenario("Fetch speed limits by bounding box on maximum zoom level")
    .exec(http("Fetch road links").get("roadlinks2?bbox=372056,6674877,375624,6680169"))
    .exec(http("Fetch speed limits").get("speedlimits?bbox=372056,6674877,375624,6680169"))

  setUp(scn.inject(atOnceUsers(1))).protocols(httpConf)
}
