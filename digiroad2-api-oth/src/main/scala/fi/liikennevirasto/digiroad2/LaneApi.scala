package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.Digiroad2Context.roadLinkService.getRoadLinksAndChangesWithPolygon
import fi.liikennevirasto.digiroad2.Digiroad2Context.{laneService, roadAddressService, roadLinkService}
import fi.liikennevirasto.digiroad2.asset.WalkingAndCyclingPath
import fi.liikennevirasto.digiroad2.client.{AddrWithIdentifier, VKMClient}
import fi.liikennevirasto.digiroad2.lane.LanePartitioner.{LaneWithContinuingLanes, getConnectedLanes, getLaneRoadIdentifierByUsingViiteRoadNumber, getStartingLanes}
import fi.liikennevirasto.digiroad2.lane.{LanePartitioner, PieceWiseLane}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.service.{RoadAddressService, RoadLinkService}
import fi.liikennevirasto.digiroad2.util.LaneUtils.pwLanesTwoDigitLaneCode
import fi.liikennevirasto.digiroad2.util.RoadAddress.isCarTrafficRoadAddress
import fi.liikennevirasto.digiroad2.util.{PolygonTools, RoadAddress, Track}
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.{BadRequest, ScalatraServlet}
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.swagger.{Swagger, SwaggerSupport}


class LaneApi(val swagger: Swagger, val roadLinkService: RoadLinkService, val roadAddressService: RoadAddressService)
  extends ScalatraServlet with JacksonJsonSupport with SwaggerSupport {
  lazy val vkmClient = new VKMClient
  lazy val polygonTools = new PolygonTools
  val apiId = "lane-api"

  case class ApiRoad(roadNumber: Long, roadParts: Seq[ApiRoadPart])
  case class ApiRoadPart(roadNumber: Option[Long], roadPartNumber: Long, apiLanes: Seq[ApiLane])
  case class ApiLane(laneCode: Int, track: Int, startAddressM: Long, endAddressM: Long)
  case class RangeParameters(roadNumber: Long, track: Track, startRoadPartNumber: Long, endRoadPartNumber: Long,
                             startAddrM: Long, endAddrM: Long)
  case class InvalidRoadNumberException(msg: String) extends Exception(msg)

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
        queryParam[Int]("track").description("Track code for search"),
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

  get("/lanes_in_range", operation(getLanesInRoadAddressRange)) {
    contentType = formats("json") + "; charset=utf-8"
    ApiUtils.avoidRestrictions(apiId + "_range", request, params) { params =>
      val roadNumber = params.getOrElse("road_number", halt(BadRequest("Missing parameters")))
      val track = params.getOrElse("track", halt(BadRequest("Missing parameters")))
      val startRoadPartNumber = params.getOrElse("start_part", halt(BadRequest("Missing parameters")))
      val startAddrM = params.getOrElse("start_addrm", halt(BadRequest("Missing parameters")))
      val endRoadPartNumber = params.getOrElse("end_part", halt(BadRequest("Missing parameters")))
      val endAddrM = params.getOrElse("end_addrm", halt(BadRequest("Missing parameters")))

      val parameters = try {
        val params = RangeParameters(roadNumber.toLong, Track(track.toInt), startRoadPartNumber.toLong,
          endRoadPartNumber.toLong, startAddrM.toLong, endAddrM.toLong)

        validateRangeParameters(params)
        params
      }
      catch {
        case invalidRoadNumberException: InvalidRoadNumberException => halt(BadRequest(invalidRoadNumberException.getMessage))
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
//TODO add more validations
  def validateRangeParameters(parameters: RangeParameters): Unit = {
    if (!isCarTrafficRoadAddress(parameters.roadNumber)) throw InvalidRoadNumberException("Invalid road number. Road number must be in range 1 to 62999")
  }

  def lanesInMunicipalityToApi(municipalityNumber: Int): Seq[Map[String, Any]] = {
    val roadLinks = roadLinkService.getRoadLinksByMunicipalityUsingCache(municipalityNumber)
    val roadLinksFiltered = roadLinks.filter(_.functionalClass != WalkingAndCyclingPath.value)
    val lanes = laneService.getLanesByRoadLinks(roadLinksFiltered, adjust = false)

    val lanesWithRoadAddress = roadAddressService.laneWithRoadAddress(lanes).filter(roadLink => {
      val roadNumber = roadLink.attributes.get("ROAD_NUMBER").asInstanceOf[Option[Long]]
      roadNumber match {
        case None => false
        case Some(rn) => isCarTrafficRoadAddress(rn)
      }
    })
    val twoDigitLanes = pwLanesTwoDigitLaneCode(lanesWithRoadAddress)
    val apiLanes = lanesToApiFormat(twoDigitLanes, roadLinksFiltered.groupBy(_.linkId).mapValues(_.head))

    apiLanes.map { apiLane =>
      Map("roadNumber" -> apiLane.roadNumber,
        "roadParts" -> apiLane.roadParts
      )
    }
  }

  def lanesInRoadAddressRangeToApi(params: RangeParameters): Seq[Map[String, Any]] = {
    val roadAddresses = roadAddressService.getAllByRoadNumber(params.roadNumber)
    val roadAddressesFiltered = roadAddresses.filter(_.track == params.track)

    if(roadAddressesFiltered.nonEmpty){
      val roadPartMaxLengths = roadAddressesFiltered.groupBy(_.roadPartNumber).mapValues(_.maxBy(_.endAddrMValue)).values
      val roadPartRange = params.startRoadPartNumber to params.endRoadPartNumber
      val filteredMaxLengths = roadPartMaxLengths.filter(address => roadPartRange contains address.roadPartNumber)

      val roadAddressesToTransform = filteredMaxLengths.flatMap(roadPart => {
        val roadPartNumber = roadPart.roadPartNumber

        val maxLength = if (roadPartNumber == params.endRoadPartNumber) params.endAddrM
        else roadPart.endAddrMValue

        val startLength = if (roadPartNumber == params.startRoadPartNumber) params.startAddrM
        else 0

        val transFormInterval = 50
        val transformsForRoadPart = ((maxLength - startLength) / transFormInterval + 1).toInt

        val roadAddresses = for (i <- 0 to transformsForRoadPart)
          yield AddrWithIdentifier(roadPartNumber + "/" + i, RoadAddress(None, params.roadNumber.toInt, roadPartNumber.toInt, params.track, (startLength + (i * transFormInterval)).toInt))
        roadAddresses
      }).toSeq

      val roadAddressesSplit = roadAddressesToTransform.grouped(1000).toSeq
      val coordinatesAndIdentifiers = roadAddressesSplit.flatMap(roadAddressGroup => vkmClient.addressToCoordsMassQuery(roadAddressGroup))
      val polygon = polygonTools.createPolygonFromCoordinates(coordinatesAndIdentifiers)

      val roadLinks = getRoadLinksAndChangesWithPolygon(polygon)._1
      val roadLinksFiltered = roadLinks.filter(_.functionalClass != WalkingAndCyclingPath.value)
      val roadLinksWithRoadAddress = roadAddressService.roadLinkWithRoadAddress(roadLinksFiltered).filter(_.attributes.contains("ROAD_NUMBER"))

      val correctLinks = roadLinksWithRoadAddress.filter(roadLink => roadLink.attributes("ROAD_NUMBER") == params.roadNumber
        && (roadPartRange contains roadLink.attributes("ROAD_PART_NUMBER")) && roadLink.attributes("TRACK") == params.track.value)

      val lanesOnRoadLinks = laneService.getLanesByRoadLinks(correctLinks, adjust = false)
      val lanesWithRoadAddress = roadAddressService.laneWithRoadAddress(lanesOnRoadLinks).filter(_.attributes.contains("ROAD_NUMBER"))

      val twoDigitLanes = pwLanesTwoDigitLaneCode(lanesWithRoadAddress)

      val apiLanes = lanesToApiFormat(twoDigitLanes, correctLinks.groupBy(_.linkId).mapValues(_.head))
      apiLanes.map { apiLane =>
        Map("roadNumber" -> apiLane.roadNumber,
          "roadParts" -> apiLane.roadParts
        )
      }
    }
    else Seq()

  }

  def lanesToApiFormat(twoDigitLanes: Seq[PieceWiseLane], roadLinks: Map[String, RoadLink]): Seq[ApiRoad] = {
    val roadNumbers = twoDigitLanes.map(_.attributes("ROAD_NUMBER")).distinct.asInstanceOf[Seq[Long]]
    val lanesGroupedByRoadNumber = twoDigitLanes.groupBy(_.attributes("ROAD_NUMBER")).values
    val lanesGroupedByRoadNumberAndPartNumber = lanesGroupedByRoadNumber.flatMap(_.groupBy(_.attributes("ROAD_PART_NUMBER")).values)

    val allRoadParts = lanesGroupedByRoadNumberAndPartNumber.map(lanesOnRoadPart => {
      val roadPartNumber = lanesOnRoadPart.head.attributes("ROAD_PART_NUMBER").asInstanceOf[Long]
      val roadNumber = Some(lanesOnRoadPart.head.attributes("ROAD_NUMBER").asInstanceOf[Long])
      val lanesGroupedByLaneCode = lanesOnRoadPart.groupBy(_.laneAttributes.find(_.publicId == "lane_code")).values

      val lanesWithContinuing = lanesGroupedByLaneCode.map(laneGroup => {
        laneGroup.map(lane => {
          val roadIdentifier = getLaneRoadIdentifierByUsingViiteRoadNumber(lane, roadLinks(lane.linkId))
          val continuingLanes = LanePartitioner.getContinuingWithIdentifier(lane, roadIdentifier, laneGroup, roadLinks, true)
          LaneWithContinuingLanes(lane, continuingLanes)
        })
      }).toSeq


      val connectedGroups = lanesWithContinuing.flatMap(LanePartitioner.getConnectedGroups)

      val lanes = connectedGroups.map(group => {
        val laneCode = group.head.laneAttributes.find(_.publicId == "lane_code").get.values.head.value.asInstanceOf[Int]
        val trackNumber = group.head.attributes("TRACK").asInstanceOf[Int]
        val startLane = group.minBy(_.attributes("START_ADDR").asInstanceOf[Long])
        val startAddressM = startLane.attributes("START_ADDR").asInstanceOf[Long] + startLane.startMeasure.asInstanceOf[Long]

        val endLane = group.maxBy(_.attributes("END_ADDR").asInstanceOf[Long])
        val endLaneLength = (roadLinks(endLane.linkId).length - endLane.endMeasure).asInstanceOf[Long]
        val endAddressM = endLane.attributes("END_ADDR").asInstanceOf[Long] - endLaneLength

        ApiLane(laneCode, trackNumber, startAddressM, endAddressM)
      })

      ApiRoadPart(roadNumber, roadPartNumber, lanes)
    }).toSeq

    roadNumbers.map(roadNumber => {
      val roadParts = allRoadParts.filter(_.roadNumber.get == roadNumber)
      val roadPartsWithOutRoadNumber = roadParts.map(_.copy(roadNumber = None))
      ApiRoad(roadNumber, roadPartsWithOutRoadNumber)
    })
  }

}
