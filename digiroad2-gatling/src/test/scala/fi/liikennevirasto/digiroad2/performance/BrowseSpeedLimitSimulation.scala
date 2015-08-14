package fi.liikennevirasto.digiroad2.performance

import java.util.concurrent.TimeUnit

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.core.feeder.RecordSeqFeederBuilder

import scala.concurrent.duration.FiniteDuration

class BrowseSpeedLimitSimulation extends Simulation {

  val deltas = Seq(Seq(-284.0, 8.0, -284.0, 8.0),
      Seq(-1487.84599685, -801.154003200121, 912.154003150004, 816.845996799879),
      Seq(-287.845996849996, 7.84599679987878, -287.845996849996, 7.84599679987878),
      Seq(-2087.84599685, -1205.65400320012, 1512.15400315, 1221.34599679988),
      Seq(-2414.45438104001, -965.857063200325, 1185.54561895999, 1461.14293679968),
      Seq(-2557.01037753001, -702.325241800398, 1042.98962246999, 1724.6747581996),
      Seq(-2530.09395842999, -404.69544180017, 1069.90604157001, 2022.30455819983),
      Seq(-2753.67487558001, -195.987239300273, 846.325124419993, 2231.01276069973))

  private val espooBoundingBox: Seq[Double] = Seq(370624.0, 6670024.0, 375424.0, 6673260.0)
  private val haminaBoundingBox: Seq[Double] = Seq(507434,6713400,514290,6716352)
  private val hankoBoundingBox: Seq[Double] = Seq(270506,6638124,277362,6641076)
  private val hollolaBoundingBox: Seq[Double] = Seq(416050,6760868,422906,6763820)
  private val huittinenBoundingBox: Seq[Double] = Seq(265350,6789536,272206,6792488)
  private val hyvinkaaBoundingBox: Seq[Double] = Seq(379622,6721932,386478,6724884)
  private val iisalmiBoundingBox: Seq[Double] = Seq(506487.0,7046614.0,513343.0,7049566.0)
  private val iittiBoundingBox: Seq[Double] = Seq(460783.0,6749306.0,467639.0,6752258.0)
  private val ilmajokiBoundingBox: Seq[Double] = Seq(270655.0,6961910.0,277511.0,6964862.0)
  private val jyvaskylaBoundingBox: Seq[Double] = Seq(431483.0,6900333.0,438339.0,6903285.0)

  val startBoundingBoxes = IndexedSeq(
    espooBoundingBox,
    haminaBoundingBox,
    hankoBoundingBox,
    hollolaBoundingBox,
    huittinenBoundingBox,
    hyvinkaaBoundingBox,
    iisalmiBoundingBox,
    iittiBoundingBox,
    ilmajokiBoundingBox,
    jyvaskylaBoundingBox)

  val feeder = startBoundingBoxes.map { startBoundingBox =>
    val adjustedBoundingBoxes = deltas.map(_.zip(startBoundingBox).map { x => x._2 - x._1 })
    val boundingBoxes = (startBoundingBox +: adjustedBoundingBoxes).map(_.mkString(","))
    Map("boundingBoxes" -> boundingBoxes)
  }.circular

  val scn = scenario("Browse speed limits").exec(feed(feeder)
    .exec(http("load page")
    .get("/"))
    .pause(5)
    .exec(http("roadlinks highest level").get("/api/roadlinks2?bbox=${boundingBoxes(0)}")
    .resources(
      http("roadlinks zoom 1")
        .get("/api/roadlinks2?bbox=${boundingBoxes(1)}"),
      http("speedlimits highest level")
        .get("/api/speedlimits?bbox=${boundingBoxes(0)}")))
    .pause(1)
    .exec(http("speedlimits zoom 1")
    .get("/api/speedlimits?bbox=${boundingBoxes(1)}"))
    .pause(1)
    .exec(http("roadlinks zoom 2").get("/api/roadlinks2?bbox=${boundingBoxes(2)}")
    .resources(
      http("roadlinks zoom 3")
        .get("/api/roadlinks2?bbox=${boundingBoxes(3)}"),
      http("roadlinks zoom 4")
        .get("/api/roadlinks2?bbox=${boundingBoxes(4)}"),
      http("speedlimits zoom 4")
        .get("/api/speedlimits?bbox=${boundingBoxes(4)}"),
      http("speedlimits zoom 4")
        .get("/api/speedlimits?bbox=${boundingBoxes(4)}"),
      http("roadlinks zoom 5")
        .get("/api/roadlinks2?bbox=${boundingBoxes(5)}"),
      http("roadlinks zoom 6")
        .get("/api/roadlinks2?bbox=${boundingBoxes(6)}"),
      http("roadlinks zoom 7")
        .get("/api/roadlinks2?bbox=${boundingBoxes(7)}"),
      http("speedlimits zoom 5")
        .get("/api/speedlimits?bbox=${boundingBoxes(5)}"),
      http("speedlimits zoom 6")
        .get("/api/speedlimits?bbox=${boundingBoxes(6)}"),
      http("speedlimits zoom 7")
        .get("/api/speedlimits?bbox=${boundingBoxes(7)}"),
      http("roadlinks zoom 8")
        .get("/api/roadlinks2?bbox=${boundingBoxes(8)}"),
      http("speedlimits zoom 8")
        .get("/api/speedlimits?bbox=${boundingBoxes(8)}"))))

  private val duration: FiniteDuration = FiniteDuration(TestConfiguration.users, TimeUnit.SECONDS)
  setUp(scn.inject(rampUsers(TestConfiguration.users) over duration)).protocols(TestConfiguration.httpConf)

}
