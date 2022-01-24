package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.Digiroad2Context.laneService.lanesWithConsistentRoadAddress
import fi.liikennevirasto.digiroad2.Digiroad2Context.roadLinkService.{getRoadLinksAndChangesFromVVHWithPolygon, roadLinksWithConsistentAddress}
import fi.liikennevirasto.digiroad2.Digiroad2Context.{laneService, roadAddressService, roadLinkService}
import fi.liikennevirasto.digiroad2.client.{AddrWithIdentifier, VKMClient}
import fi.liikennevirasto.digiroad2.lane.LanePartitioner.{LaneWithContinuingLanes, getConnectedLanes, getStartingLanes}
import fi.liikennevirasto.digiroad2.lane.{LanePartitioner, PieceWiseLane}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.util.{PolygonTools, RoadAddress, Track}
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.{BadRequest, ScalatraServlet}
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.swagger.{Swagger, SwaggerSupport}

class LaneApi(val swagger: Swagger) extends ScalatraServlet with JacksonJsonSupport with SwaggerSupport {
  lazy val vkmClient = new VKMClient
  lazy val polygonTools = new PolygonTools

  case class ApiRoad(roadNumber: Long, roadParts: Seq[ApiRoadPart])
  case class ApiRoadPart(roadNumber: Option[Long], roadPartNumber: Long, apiLanes: Seq[ApiLane])
  case class ApiLane(laneCode: Int, track: Int, startAddressM: Long, endAddressM: Long)
  case class RangeParameters(roadNumber: Long, track: Track, startRoadPartNumber: Long, endRoadPartNumber: Long,
                             startAddrM: Long, endAddrM: Long)

  override protected def applicationDescription: String = "Lanes API"
  override protected implicit def jsonFormats: Formats = DefaultFormats

  val getLanesInRoadAddressRange =
    (apiOperation[Long]("getLanesInRoadAddressRange")
      .parameters(
        queryParam[Int]("road_number").description("Road Number for the range"),
        queryParam[Int]("track").description("Track code for search"),
        queryParam[Int]("start_part").description("Starting road part number for search"),
        queryParam[Int]("start_addrm").description("Starting distance on starting roadPart for search"),
        queryParam[Int]("end_part").description("Ending road part number for search"),
        queryParam[Int]("end_addrm").description("Ending distance on last road part for search"),
        pathParam[String]("lanes_in_range").description("Path for getting lanes in given range")
      )
      tags "LaneApi"
      summary "Get lanes in given road address range in road address format"
      authorizations "Contact your service provider for more information"
      description "Example URL: /externalApi/lanes/lanes_in_range?road_number=9&track=1&start_part=208&start_addrm=8500&end_part=208&end_addrm=9000"
      )

  val getLanesInMunicipality =
    (apiOperation[Long]("getLanesInMunicipality")
      .parameters(
        queryParam[Int]("municipality").description("Municipality Code where we will get lanes from"),
        pathParam[String]("lanes_in_municipality").description("Path for getting lanes in municipality")
      )
      tags "LaneApi"
      summary "Get lanes in given municipality"
      authorizations "Contact your service provider for more information"
      description "Example URL: /externalApi/lanes/lanes_in_municipality?municipality=235"
      )

  get("/lanes_in_range", operation(getLanesInRoadAddressRange)) {
    contentType = formats("json") + "; charset=utf-8"

    try {
      val roadNumber = params("road_number").toLong
      val track = Track(params("track").toInt)
      val startRoadPartNumber = params("start_part").toLong
      val startAddrM = params("start_addrm").toLong
      val endRoadPartNumber = params("end_part").toLong
      val endAddrM = params("end_addrm").toLong

      val parameters = RangeParameters(roadNumber, track, startRoadPartNumber, endRoadPartNumber, startAddrM, endAddrM)
      lanesInRoadAddressRangeToApi(parameters)
    }
    catch {
      case _: NumberFormatException => BadRequest("Invalid parameters")
      case _: NoSuchElementException => BadRequest("Missing parameters")
    }
  }

  get("/lanes_in_municipality", operation(getLanesInMunicipality)) {
    contentType = formats("json") + "; charset=utf-8"
    try {
      val municipalityParameter = params.get("municipality")
      if (municipalityParameter.isEmpty) BadRequest("Missing municipality parameter")
      else {
        val municipalityNumber = municipalityParameter.get.toInt
        lanesInMunicipalityToApi(municipalityNumber)
      }
    }
    catch {
      case _: NumberFormatException => BadRequest("Missing or invalid municipality parameter")
    }
  }

  def lanesInMunicipalityToApi(municipalityNumber: Int): Seq[Map[String, Any]] = {
    val roadLinks = roadLinkService.getRoadLinksFromVVH(municipalityNumber)
    val lanes = laneService.getLanesByRoadLinks(roadLinks)

    val (lanesWithViiteAddress, noRoadAddress) = roadAddressService.laneWithRoadAddress(Seq(lanes)).flatten.partition(_.attributes.contains("VIITE_ROAD_NUMBER"))
    val lanesWithTempAddress = roadAddressService.experimentalLaneWithRoadAddress(Seq(noRoadAddress)).flatten.filter(_.attributes.contains("TEMP_ROAD_NUMBER"))
    val lanesWithRoadAddress = lanesWithViiteAddress ++ lanesWithTempAddress
    val lanesWithNormalRoadAddress = lanesWithConsistentRoadAddress(lanesWithRoadAddress)

    val twoDigitLanes = laneService.pieceWiseLanesToTwoDigitWithMassQuery(lanesWithNormalRoadAddress).flatten
    val apiLanes = lanesToApiFormat(twoDigitLanes, roadLinks.groupBy(_.linkId).mapValues(_.head))

    apiLanes.map { apiLane =>
      Map("roadNumber" -> apiLane.roadNumber,
        "roadParts" -> apiLane.roadParts
      )
    }
  }

  def lanesInRoadAddressRangeToApi(params: RangeParameters): Seq[Map[String, Any]] = {
    val roadAddresses = roadAddressService.getAllByRoadNumber(params.roadNumber).filter(_.track == params.track)
    val roadPartMaxLengths = roadAddresses.groupBy(_.roadPartNumber).mapValues(_.maxBy(_.endAddrMValue)).values
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

    val roadLinks = getRoadLinksAndChangesFromVVHWithPolygon(polygon)._1
    val (roadLinksWithViiteAddress, noRoadAddress) = roadAddressService.roadLinkWithRoadAddress(roadLinks).partition(_.attributes.contains("VIITE_ROAD_NUMBER"))
    val roadLinksWithTempAddress = roadAddressService.roadLinkWithRoadAddressTemp(noRoadAddress).filter(_.attributes.contains("TEMP_ROAD_NUMBER"))
    val roadLinksWithRoadAddress = roadLinksWithViiteAddress ++ roadLinksWithTempAddress
    val roadLinksWithConsistentRoadAddress = roadLinksWithConsistentAddress(roadLinksWithRoadAddress)

    val correctLinks = roadLinksWithConsistentRoadAddress.filter(roadLink => roadLink.attributes("VIITE_ROAD_NUMBER") == params.roadNumber
      && (roadPartRange contains roadLink.attributes("VIITE_ROAD_PART_NUMBER")) && roadLink.attributes("VIITE_TRACK") == params.track.value)

    val lanesOnRoadLinks = laneService.getLanesByRoadLinks(correctLinks)
    val (lanesWithViiteAddress, lanesWithoutRoadAddress) = roadAddressService.laneWithRoadAddress(Seq(lanesOnRoadLinks))
      .flatten.partition(_.attributes.contains("VIITE_ROAD_NUMBER"))
    val lanesWithTempAddress = roadAddressService.experimentalLaneWithRoadAddress(Seq(lanesWithoutRoadAddress)).flatten
      .filter(_.attributes.contains("TEMP_ROAD_NUMBER"))
    val lanesWithRoadAddress = lanesWithViiteAddress ++ lanesWithTempAddress

    val lanesWithNormalRoadAddress = lanesWithConsistentRoadAddress(lanesWithRoadAddress)
    val twoDigitLanes = laneService.pieceWiseLanesToTwoDigitWithMassQuery(lanesWithNormalRoadAddress).flatten

    val apiLanes = lanesToApiFormat(twoDigitLanes, correctLinks.groupBy(_.linkId).mapValues(_.head))
    apiLanes.map { apiLane =>
      Map("roadNumber" -> apiLane.roadNumber,
        "roadParts" -> apiLane.roadParts
      )
    }
  }

  def lanesToApiFormat(twoDigitLanes: Seq[PieceWiseLane], roadLinks: Map[Long, RoadLink]): Seq[ApiRoad] = {
    val roadNumbers = twoDigitLanes.map(_.attributes("ROAD_NUMBER")).distinct.asInstanceOf[Seq[Long]]
    val lanesGroupedByRoadNumber = twoDigitLanes.groupBy(_.attributes("ROAD_NUMBER")).values
    val lanesGroupedByRoadNumberAndPartNumber = lanesGroupedByRoadNumber.flatMap(_.groupBy(_.attributes("ROAD_PART_NUMBER")).values)

    val allRoadParts = lanesGroupedByRoadNumberAndPartNumber.map(lanesOnRoadPart => {
      val roadPartNumber = lanesOnRoadPart.head.attributes("ROAD_PART_NUMBER").asInstanceOf[Long]
      val roadNumber = Some(lanesOnRoadPart.head.attributes("ROAD_NUMBER").asInstanceOf[Long])
      val lanesGroupedByLaneCode = lanesOnRoadPart.groupBy(_.laneAttributes.find(_.publicId == "lane_code")).values

      val lanesWithContinuing = lanesGroupedByLaneCode.map(laneGroup => {
        laneGroup.map(lane => {
          val roadIdentifier = roadLinks(lane.linkId).roadIdentifier
          val continuingLanes = LanePartitioner.getContinuingWithIdentifier(lane, roadIdentifier, laneGroup, roadLinks, true)
          LaneWithContinuingLanes(lane, continuingLanes)
        })
      }).toSeq


      val connectedGroups = lanesWithContinuing.flatMap(laneGroup => {
        val startingLanes = getStartingLanes(laneGroup)
        val connectedLanes = startingLanes.map(startingLane => {
          getConnectedLanes(Seq(startingLane), laneGroup).sortBy(_.lane.id)
        }).distinct
        connectedLanes.map(_.map(_.lane))
      })

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
