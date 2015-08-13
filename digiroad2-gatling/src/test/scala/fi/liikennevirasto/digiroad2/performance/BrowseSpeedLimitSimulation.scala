package fi.liikennevirasto.digiroad2.performance

import java.util.concurrent.TimeUnit

import io.gatling.core.Predef._
import io.gatling.http.Predef._

import scala.concurrent.duration.FiniteDuration

class BrowseSpeedLimitSimulation extends Simulation {

  val scn = scenario("Browse speed limits")
    .exec(http("load page")
    .get("/"))
    .pause(5)
    .exec(http("roadlinks highest level").get("/api/roadlinks2?bbox=370624,6670024,375424,6673260")
    .resources(
      http("roadlinks zoom 1")
        .get("/api/roadlinks2?bbox=370908,6670016,375708,6673252"),
      http("speedlimits highest level")
        .get("/api/speedlimits?bbox=370624,6670024,375424,6673260")))
    .pause(1)
    .exec(http("speedlimits zoom 1")
    .get("/api/speedlimits?bbox=370908,6670016,375708,6673252"))
    .pause(1)
    .exec(http("roadlinks zoom 2").get("/api/roadlinks2?bbox=372111.84599685,6670825.1540032,374511.84599685,6672443.1540032")
    .resources(
      http("roadlinks zoom 3")
        .get("/api/roadlinks2?bbox=370911.84599685,6670016.1540032,375711.84599685,6673252.1540032"),
      http("roadlinks zoom 4")
        .get("/api/roadlinks2?bbox=372711.84599685,6671229.6540032,373911.84599685,6672038.6540032"),
      http("speedlimits zoom 4")
        .get("/api/speedlimits?bbox=372711.84599685,6671229.6540032,373911.84599685,6672038.6540032"),
      http("speedlimits zoom 4")
        .get("/api/speedlimits?bbox=370911.84599685,6670016.1540032,375711.84599685,6673252.1540032"),
      http("roadlinks zoom 5")
        .get("/api/roadlinks2?bbox=373038.45438104,6670989.8570632,374238.45438104,6671798.8570632"),
      http("roadlinks zoom 6")
        .get("/api/roadlinks2?bbox=373181.01037753,6670726.3252418,374381.01037753,6671535.3252418"),
      http("roadlinks zoom 7")
        .get("/api/roadlinks2?bbox=373154.09395843,6670428.6954418,374354.09395843,6671237.6954418"),
      http("speedlimits zoom 5")
        .get("/api/speedlimits?bbox=373038.45438104,6670989.8570632,374238.45438104,6671798.8570632"),
      http("speedlimits zoom 6")
        .get("/api/speedlimits?bbox=373181.01037753,6670726.3252418,374381.01037753,6671535.3252418"),
      http("speedlimits zoom 7")
        .get("/api/speedlimits?bbox=373154.09395843,6670428.6954418,374354.09395843,6671237.6954418"),
      http("roadlinks zoom 8")
        .get("/api/roadlinks2?bbox=373377.67487558,6670219.9872393,374577.67487558,6671028.9872393"),
      http("speedlimits zoom 8")
        .get("/api/speedlimits?bbox=373377.67487558,6670219.9872393,374577.67487558,6671028.9872393")))

  private val duration: FiniteDuration = FiniteDuration(TestConfiguration.users, TimeUnit.SECONDS)
  setUp(scn.inject(rampUsers(TestConfiguration.users) over duration)).protocols(TestConfiguration.httpConf)
}