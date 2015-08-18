package fi.liikennevirasto.digiroad2.performance

import java.util.concurrent.TimeUnit

import io.gatling.core.Predef._
import io.gatling.http.Predef._

import scala.collection.immutable.IndexedSeq
import scala.concurrent.duration.FiniteDuration

object BoundingBoxProvider {
  def getStartBoundingBoxes: IndexedSeq[Seq[Double]] = {
    val source = scala.io.Source.fromFile("./boundingBoxes.csv")
    val startBoundingBoxes = source.getLines().map { l =>
      val cols = l.split(',').toSeq
      cols.map(_.toDouble)
    }.toIndexedSeq
    source.close()
    startBoundingBoxes
  }
}

class BrowseSpeedLimitSimulation extends Simulation {

  val deltas = Seq(Seq(-284.0, 8.0, -284.0, 8.0),
    Seq(-1487.84599685, -801.154003200121, 912.154003150004, 816.845996799879),
    Seq(-287.845996849996, 7.84599679987878, -287.845996849996, 7.84599679987878),
    Seq(-2087.84599685, -1205.65400320012, 1512.15400315, 1221.34599679988),
    Seq(-2414.45438104001, -965.857063200325, 1185.54561895999, 1461.14293679968),
    Seq(-2557.01037753001, -702.325241800398, 1042.98962246999, 1724.6747581996),
    Seq(-2530.09395842999, -404.69544180017, 1069.90604157001, 2022.30455819983),
    Seq(-2753.67487558001, -195.987239300273, 846.325124419993, 2231.01276069973))

  val startBoundingBoxes = BoundingBoxProvider.getStartBoundingBoxes

  val feeder = startBoundingBoxes.map { startBoundingBox =>
    val adjustedBoundingBoxes = deltas.map(_.zip(startBoundingBox).map { x => x._2 - x._1 })
    val boundingBoxes = (startBoundingBox +: adjustedBoundingBoxes).map(_.mkString(","))
    Map("boundingBoxes" -> boundingBoxes)
  }.circular

  val scn = scenario("Browse speed limits").exec(feed(feeder)
    .exec(http("load page")
    .get("/"))
    .pause(5)
    .exec(http("speedlimits highest level")
    .get("/api/speedlimits?bbox=${boundingBoxes(0)}")))
    .pause(1)
    .exec(http("speedlimits zoom 1")
    .get("/api/speedlimits?bbox=${boundingBoxes(1)}"))
    .pause(1)
    .exec(http("speedlimits zoom 4")
    .get("/api/speedlimits?bbox=${boundingBoxes(4)}"))
    .exec(http("speedlimits zoom 5")
    .get("/api/speedlimits?bbox=${boundingBoxes(5)}"))
    .exec(http("speedlimits zoom 6")
    .get("/api/speedlimits?bbox=${boundingBoxes(6)}"))
    .exec(http("speedlimits zoom 7")
    .get("/api/speedlimits?bbox=${boundingBoxes(7)}"))
    .exec(http("speedlimits zoom 8")
    .get("/api/speedlimits?bbox=${boundingBoxes(8)}"))

  private val duration: FiniteDuration = FiniteDuration(TestConfiguration.users, TimeUnit.SECONDS)
  setUp(scn.inject(rampUsers(TestConfiguration.users) over duration)).protocols(TestConfiguration.httpConf)

}
