package fi.liikennevirasto.digiroad2.performance

import io.gatling.core.Predef._
import io.gatling.http.Predef._

class PingSimulation extends Simulation {
  val httpConf = http.baseURL(TestConfiguration.apiUrl + "/ping")
  val scn = scenario("PingSimulation").exec(http("ping").get("/"))

  setUp(scn.inject(atOnceUsers(1))).protocols(httpConf)
}

