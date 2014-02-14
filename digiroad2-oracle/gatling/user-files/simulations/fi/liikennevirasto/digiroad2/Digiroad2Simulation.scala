package fi.liikennevirasto.digiroad2

import io.gatling.core.Predef._
import io.gatling.core.session.Expression
import io.gatling.http.Predef._
import io.gatling.jdbc.Predef._
import io.gatling.http.Headers.Names._
import io.gatling.http.Headers.Values._
import scala.concurrent.duration._
import bootstrap._
import assertions._

class Digiroad2Simulation extends Simulation {

  val httpProtocol = http
      .baseURL("http://localhost:8080")
      .acceptHeader("*/*")

  val scn = scenario("Load bus stops and road links")
      .repeat(10) {
        exec(http("Get road links for Kauniainen").get("""/api/roadlinks?municipalityNumber=235"""))
        .exec(http("Get bus stops").get("""/api/assets?assetTypeId=10"""))
        .exec(http("Get road links for Kerava").get("""/api/roadlinks?municipalityNumber=245"""))
      }
  setUp(scn.inject(atOnce(10 user))).protocols(httpProtocol)
}

