package fi.liikennevirasto.digiroad2.performance

import io.gatling.core.Predef._
import io.gatling.http.Predef._

class PingSimulation extends Simulation {
  val httpConf = http.baseURL("http://10.129.47.148:8080/digiroad/api/ping")
  val scn = scenario("PingSimulation").exec(http("ping").get("/"))

  setUp(scn.inject(atOnceUsers(1))).protocols(httpConf)
}

