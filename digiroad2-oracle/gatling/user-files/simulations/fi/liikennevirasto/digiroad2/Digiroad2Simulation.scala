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
        exec(http("Get bus stops L1").get("""/api/assets?assetTypeId=10&bbox=327316,6821857,332052,6824433""").headers(authHeader))
        .exec(http("Get bus stops L2").get("""/api/assets?assetTypeId=10&bbox=326784,6820903,331520,6823479""").headers(authHeader))
        .exec(http("Get bus stops L3").get("""/api/assets?assetTypeId=10&bbox=326478,6820155,331214,6822731""").headers(authHeader))
        .exec(http("Get bus stops M1").get("""/api/assets?assetTypeId=10&bbox=326990,6821918,329358,6823206""").headers(authHeader))
        .exec(http("Get bus stops M2").get("""/api/assets?assetTypeId=10&bbox=327010,6822456,329378,6823744""").headers(authHeader))
        .exec(http("Get bus stops M3").get("""/api/assets?assetTypeId=10&bbox=327090,6822855,329458,6824143""").headers(authHeader))
        .exec(http("Get road links L1").get("""/api/roadlinks?bbox=327316,6821857,332052,6824433""").headers(authHeader))
        .exec(http("Get road links L2").get("""/api/roadlinks?bbox=326784,6820903,331520,6823479""").headers(authHeader))
        .exec(http("Get road links L3").get("""/api/roadlinks?bbox=326478,6820155,331214,6822731""").headers(authHeader))
        .exec(http("Get road links M1").get("""/api/roadlinks?bbox=326990,6821918,329358,6823206""").headers(authHeader))
        .exec(http("Get road links M2").get("""/api/roadlinks?bbox=327010,6822456,329378,6823744""").headers(authHeader))
        .exec(http("Get road links M3").get("""/api/roadlinks?bbox=327090,6822855,329458,6824143""").headers(authHeader))
      }
  setUp(scn.inject(atOnce(10 user))).protocols(httpProtocol)
}
