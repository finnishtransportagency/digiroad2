package fi.liikennevirasto.digiroad2.performance

import java.util.concurrent.TimeUnit
import io.gatling.core.Predef._
import io.gatling.core.feeder.RecordSeqFeederBuilder
import io.gatling.http.Predef._
import scala.concurrent.duration.FiniteDuration

object FetchBoundingBox {
  val feeder = BoundingBoxProvider.getStartBoundingBoxes.map { bbox => Map("bbox" -> bbox.mkString(",")) }.circular

  val fetch =
    group("load speed limits") {
      feed(feeder)
      .exec(http("Fetch road links").get("/api/roadlinks2?bbox=${bbox}"))
      .exec(http("Fetch speed limits").get("/api/speedlimits?bbox=${bbox}"))
    }
}

class FetchSpeedLimitsSimulation extends Simulation {

  val scn = scenario("Fetch speed limits by bounding box on maximum zoom level")
    .exec(FetchBoundingBox.fetch)

  private val userCount: Int = TestConfiguration.users
  private val duration: FiniteDuration = FiniteDuration(userCount, TimeUnit.SECONDS)

  setUp(scn.inject(rampUsers(userCount) over duration)).protocols(TestConfiguration.httpConf)
}
