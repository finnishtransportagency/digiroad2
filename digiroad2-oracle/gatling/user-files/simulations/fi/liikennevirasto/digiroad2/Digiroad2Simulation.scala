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
//      .baseURL("http://10.129.47.148:8080/digiroad")
      .baseURL("http://localhost:8080")
      .acceptHeader("*/*")
  val authHeader = Map("OAM_REMOTE_USER" -> "testpirkanmaa")

  val scn = scenario("Load bus stops and road links")
      .repeat(10) {
        exec(http("Get bus stops 1").get("""/api/assets?assetTypeId=10&bbox=367799,6676551,370171,6677735""").headers(authHeader))
//        .exec(http("Get bus stops 2").get("""/api/assets?assetTypeId=10&bbox=366777,6676051,371521,6678419""").headers(authHeader))
//        .exec(http("Get bus stops 3").get("""/api/assets?assetTypeId=10&bbox=364405,6674867,373893,6679603""").headers(authHeader))
        .exec(http("Get road links 1").get("""/api/roadlinks?bbox=325936,6821541,330680,6823549""").headers(authHeader))
//        .exec(http("Get road links 2").get("""/api/roadlinks?bbox=366777,6676051,371521,6678419""").headers(authHeader))
//        .exec(http("Get road links 3").get("""/api/roadlinks?bbox=364405,6674867,373893,6679603""").headers(authHeader))
      }
  setUp(scn.inject(atOnce(1 user))).protocols(httpProtocol)
}
