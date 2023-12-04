package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.WalkingAndCyclingPath
import fi.liikennevirasto.digiroad2.client.VKMClient
import fi.liikennevirasto.digiroad2.lane.LanePartitioner.LaneWithContinuingLanes
import fi.liikennevirasto.digiroad2.lane.{LanePartitioner, PieceWiseLane}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.service.lane.LaneService
import fi.liikennevirasto.digiroad2.service.{RoadAddressService, RoadLinkService}
import fi.liikennevirasto.digiroad2.util.LaneUtils.pwLanesTwoDigitLaneCode
import fi.liikennevirasto.digiroad2.RoadAddress.isCarTrafficRoadAddress
import fi.liikennevirasto.digiroad2.util.{PolygonTools, RoadAddressRange}
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.swagger.{Swagger, SwaggerSupport}
import org.scalatra.{BadRequest, ScalatraServlet}

import scala.collection.mutable


class LaneApi(val swagger: Swagger, val roadLinkService: RoadLinkService, val roadAddressService: RoadAddressService)
  extends ScalatraServlet with JacksonJsonSupport with SwaggerSupport {
  lazy val vkmClient = new VKMClient
  lazy val polygonTools = new PolygonTools
  lazy val laneService = new LaneService(roadLinkService, new DummyEventBus, roadAddressService)
  val apiId = "lane-api"

  case class HomogenizedLane(laneCode: Long, laneTypeCode: Long, roadNumber: Long, roadPartNumber: Long, track: Long, startAddressM: Long, endAddressM: Long)
  case class InvalidRoadAddressRangeParamaterException(msg: String) extends Exception(msg)

  override protected def applicationDescription: String = "Lanes API"
  override protected implicit def jsonFormats: Formats = DefaultFormats

  after() {
    response.setHeader("Access-Control-Allow-Origin", request.getHeader("Origin"));
    response.setHeader("Access-Control-Allow-Methods",  "OPTIONS,POST,GET");
    response.setHeader("Access-Control-Allow-Headers", request.getHeader("Access-Control-Request-Headers"));
  }


  val getLanesInRoadAddressRange =
    (apiOperation[Long]("getLanesInRoadAddressRange")
      .parameters(
        queryParam[Int]("road_number").description("Road Number for the range"),
        queryParam[Int]("track").description("Track code for search").optional,
        queryParam[Int]("start_part").description("Starting road part number for search"),
        queryParam[Int]("start_addrm").description("Starting distance on starting roadPart for search"),
        queryParam[Int]("end_part").description("Ending road part number for search"),
        queryParam[Int]("end_addrm").description("Ending distance on last road part for search")
      )
      tags "LaneApi"
      summary "Get lanes in given road address range in road address format"
      authorizations "Contact your service provider for more information"
      description "Example URL: /externalApi/lanes/lanes_in_range?road_number=9&track=1&start_part=208&start_addrm=8500&end_part=208&end_addrm=9000"
      )

  val getLanesInMunicipality =
    (apiOperation[Long]("getLanesInMunicipality")
      .parameters(
        queryParam[Int]("municipality").description("Municipality Code where we will get lanes from")
      )
      tags "LaneApi"
      summary "Get lanes in given municipality"
      authorizations "Contact your service provider for more information"
      description "Example URL: /externalApi/lanes/lanes_in_municipality?municipality=235"
      )

  val getLanesOnPoint =
    (apiOperation[Long]("getLanesOnPoint")
      .parameters(
        queryParam[String]("linkId").description("LinkID for linear referencing"),
        queryParam[Double]("mValue").description("M-Value for linear referencing, three decimal point accuracy")
      )
      tags "LaneApi"
      summary "Get lanes on given linearly referenced point"
      authorizations "Contact your service provider for more information"
      description "Example URL: /externalApi/lanes/lanes_on_point?linkId=abb902fe-96a2-4423-b619-2af7f06f410a:1&mValue=50.567"
      )

  get("/lanes_in_range", operation(getLanesInRoadAddressRange)) {
    contentType = formats("json") + "; charset=utf-8"
    ApiUtils.avoidRestrictions(apiId + "_range", request, params) { params =>
      val roadNumber = params.getOrElse("road_number", halt(BadRequest("Missing parameters")))
      val track = params.get("track")
      val startRoadPartNumber = params.getOrElse("start_part", halt(BadRequest("Missing parameters")))
      val startAddrM = params.getOrElse("start_addrm", halt(BadRequest("Missing parameters")))
      val endRoadPartNumber = params.getOrElse("end_part", halt(BadRequest("Missing parameters")))
      val endAddrM = params.getOrElse("end_addrm", halt(BadRequest("Missing parameters")))

      val parameters = try {
        val trackParam = if(track.isDefined) Some(Track(track.get.toInt))
        else None
        val params = RoadAddressRange(roadNumber.toLong, trackParam, startRoadPartNumber.toLong,
          endRoadPartNumber.toLong, startAddrM.toLong, endAddrM.toLong)

        validateRangeParameters(params)
        params
      }
      catch {
        case invalidRoadNumberException: InvalidRoadAddressRangeParamaterException => halt(BadRequest(invalidRoadNumberException.getMessage))
        case _: NumberFormatException => halt(BadRequest("Invalid parameters"))
      }

      lanesInRoadAddressRangeToApi(parameters)
    }
  }

  get("/lanes_in_municipality", operation(getLanesInMunicipality)) {
    contentType = formats("json") + "; charset=utf-8"
    ApiUtils.avoidRestrictions(apiId + "_municipality", request, params) { params =>
      try {
        val municipalityParameter = params.get("municipality")
        if (municipalityParameter.isEmpty) halt(BadRequest("Missing municipality parameter"))
        else {
          val municipalityNumber = municipalityParameter.get.toInt
          lanesInMunicipalityToApi(municipalityNumber)
        }
      }
      catch {
        case _: NumberFormatException => halt(BadRequest("Missing or invalid municipality parameter"))
      }
    }
  }

  get("/lanes_on_point", operation(getLanesOnPoint)) {
    contentType = formats("json") + "; charset=utf-8"
    ApiUtils.avoidRestrictions(apiId + "_point", request, params) { params =>
      try {
        val linkIdParam = params.get("linkId")
        val mValueParam = params.get("mValue")
        if (linkIdParam.isEmpty) halt(BadRequest("Missing linkId parameter"))
        if (mValueParam.isEmpty) halt(BadRequest("Missing mValue parameter"))
        else {
          val linkId = linkIdParam.get
          val mValue = mValueParam.get.toDouble
          lanesOnPointToApi(linkId, mValue)
        }
      }
      catch {
        case _: NoSuchElementException => halt(BadRequest("No road link found on given linkID"))
        case _: NumberFormatException => halt(BadRequest("Invalid mValue parameter"))
        case _: Exception => halt(BadRequest("Something went wrong"))
      }
    }
  }

  def validateRangeParameters(parameters: RoadAddressRange): Unit = {
    if (!isCarTrafficRoadAddress(parameters.roadNumber)) throw InvalidRoadAddressRangeParamaterException("Invalid road number. Road number must be in range 1 to 62999")
    if (parameters.track.contains(Track.Unknown)) throw InvalidRoadAddressRangeParamaterException("Invalid track number, allowed Track values are: 0, 1, 2")
    if (parameters.startRoadPartNumber > parameters.endRoadPartNumber) throw InvalidRoadAddressRangeParamaterException("Start part number must be smaller than end part number")
    if (!RoadAddress.roadPartNumberRange.contains(parameters.startRoadPartNumber) ||
      !RoadAddress.roadPartNumberRange.contains(parameters.endRoadPartNumber)) throw InvalidRoadAddressRangeParamaterException("Road part numbers must be in range 1 - 1000")
    if(parameters.startAddrMValue >= parameters.endAddrMValue && parameters.startRoadPartNumber == parameters.endRoadPartNumber) {
      throw InvalidRoadAddressRangeParamaterException("StartAddrM value must be less than EndAddrM value if both are on the same road part")
    }
  }

  def lanesInMunicipalityToApi(municipalityNumber: Int): Seq[mutable.LinkedHashMap[String, Long]] = {
    val roadLinks = roadLinkService.getRoadLinksByMunicipalityUsingCache(municipalityNumber)
    val roadLinksFiltered = roadLinks.filter(_.functionalClass != WalkingAndCyclingPath.value)
    val roadLinksGrouped = roadLinksFiltered.groupBy(_.linkId).mapValues(_.head)
    val lanes = laneService.getLanesByRoadLinks(roadLinksFiltered)

    val lanesWithRoadAddress = roadAddressService.laneWithRoadAddress(lanes).filter(roadLink => {
      val roadNumber = roadLink.attributes.get("ROAD_NUMBER").asInstanceOf[Option[Long]]
      roadNumber match {
        case None => false
        case Some(rn) => isCarTrafficRoadAddress(rn)
      }
    })
    val twoDigitLanes = pwLanesTwoDigitLaneCode(lanesWithRoadAddress)
    val homogenousLanes = homogenizeTwoDigitLanes(twoDigitLanes, roadLinksGrouped)
    homogenizedLanesToApi(homogenousLanes)
  }

  def lanesInRoadAddressRangeToApi(roadAddressRange: RoadAddressRange): Seq[mutable.LinkedHashMap[String, Long]] = {
    val (lanesWithRoadAddress, roadLinksGrouped) = try {
      laneService.getLanesInRoadAddressRange(roadAddressRange)
    } catch {
      case rae: RoadAddressException => halt(BadRequest("Could not fetch lanes for given road address range, check that given road address range is valid"))
    }
    val twoDigitLanes = pwLanesTwoDigitLaneCode(lanesWithRoadAddress)
    val homogenousLanes = homogenizeTwoDigitLanes(twoDigitLanes, roadLinksGrouped)
    homogenizedLanesToApi(homogenousLanes)
  }

  def homogenizedLanesToApi(lanes: Seq[HomogenizedLane]): Seq[mutable.LinkedHashMap[String, Long]] = {
    lanes.sortBy(lane => (lane.roadNumber, lane.roadPartNumber, lane.track)).map(lane => {
      mutable.LinkedHashMap(
        "roadNumber" -> lane.roadNumber,
        "roadPartNumber" -> lane.roadPartNumber,
        "track" -> lane.track,
        "startAddrMValue" -> lane.startAddressM,
        "endAddrMValue" -> lane.endAddressM,
        "laneCode" -> lane.laneCode,
        "laneType" -> lane.laneTypeCode)
    })
  }

  def homogenizeTwoDigitLanes(twoDigitLanes: Seq[PieceWiseLane], roadLinks: Map[String, RoadLink]): Seq[HomogenizedLane] = {
    val lanesWithAccurateAddrMValues = laneService.calculateAccurateAddrMValuesForCutLanes(twoDigitLanes, roadLinks)

    // Form groups of lanes by meaningful attributes and geometrical connectivity, i.e. homogenize lanes for api response
    val lanesGroupedByAttributes = groupLanesByMeaningfulAttributes(lanesWithAccurateAddrMValues)
    val groupedAndConnectedLanes = lanesGroupedByAttributes.map(laneGroup => {
      val lanesWithContinuingLanes = laneGroup.map(lane => {
        val identifier = LanePartitioner.getLaneRoadIdentifierByUsingViiteRoadNumber(lane, roadLinks(lane.linkId))
        val continuingLanes = LanePartitioner.getContinuingWithIdentifier(lane, identifier, laneGroup, roadLinks, sideCodesCorrected = true)
        LaneWithContinuingLanes(lane, continuingLanes)
      })
      LanePartitioner.getConnectedGroups(lanesWithContinuingLanes)
    })

    groupedAndConnectedLanes.flatMap(lanesGroups => lanesGroups.map(lanes => {
      val laneCode = laneService.getLaneCode(lanes.head)
      val laneType = laneService.getPropertyValue(lanes.head, "lane_type").get.value.toString.toInt
      val roadNumber = lanes.head.attributes("ROAD_NUMBER").asInstanceOf[Long]
      val roadPartNumber = lanes.head.attributes("ROAD_PART_NUMBER").asInstanceOf[Long]
      val track = lanes.head.attributes("TRACK").asInstanceOf[Int]
      val startAddrM = lanes.minBy(_.attributes("START_ADDR").asInstanceOf[Long]).attributes("START_ADDR").asInstanceOf[Long]
      val endAddrM = lanes.maxBy(_.attributes("END_ADDR").asInstanceOf[Long]).attributes("END_ADDR").asInstanceOf[Long]

      HomogenizedLane(laneCode, laneType, roadNumber, roadPartNumber, track, startAddrM, endAddrM)
    }))
  }

  def groupLanesByMeaningfulAttributes(lanes: Seq[PieceWiseLane]): Seq[Seq[PieceWiseLane]] = {
    lanes.groupBy(lane => {
      (lane.attributes("ROAD_NUMBER").asInstanceOf[Long],
        lane.attributes("ROAD_PART_NUMBER").asInstanceOf[Long],
        lane.attributes("TRACK").asInstanceOf[Int],
        laneService.getPropertyValue(lane.laneAttributes, "lane_code").asInstanceOf[Int],
        laneService.getPropertyValue(lane.laneAttributes, "lane_type").toString.toInt
        )
    }).values.toSeq
  }

  def lanesOnPointToApi(linkId: String, mValue: Double): Seq[mutable.LinkedHashMap[String, Any]] = {
    val (lanes, roadLink) = laneService.getLanesAndRoadLinkOnLinearReferencePoint(linkId, mValue)
    lanes.map(laneToApi => {
      val municipalityCode = roadLink.municipalityCode
      val roadName = roadLink.roadNameIdentifier.getOrElse("")
      val roadNumber = laneToApi.roadNumber.getOrElse("")
      val roadPartNumber = laneToApi.roadPartNumber.getOrElse("")
      val track = laneToApi.track.getOrElse("")
      val startAddrM = laneToApi.startAddrM.getOrElse("")
      val endAddrM = laneToApi.endAddrM.getOrElse("")
      val laneCode = laneService.getPropertyValue(laneToApi.laneAttributes, "lane_code").asInstanceOf[Int]
      val laneType = laneService.getPropertyValue(laneToApi.laneAttributes, "lane_type").toString.toInt

      mutable.LinkedHashMap(
        "laneCode" -> laneCode,
        "laneType" -> laneType,
        "municipality" -> municipalityCode,
        "linkId" -> laneToApi.linkId,
        "startMeasure" -> laneToApi.startMeasure,
        "endMeasure" -> laneToApi.endMeasure,
        "sideCode" -> laneToApi.sideCode,
        "roadName" -> roadName,
        "roadNumber" -> roadNumber,
        "roadPartNumber" -> roadPartNumber,
        "track" -> track,
        "startAddr" -> startAddrM,
        "endAddr" -> endAddrM
      )
    })
  }

}
