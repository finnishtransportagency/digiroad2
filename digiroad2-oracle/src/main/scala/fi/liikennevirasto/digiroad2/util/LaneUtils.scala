package fi.liikennevirasto.digiroad2.util

import java.util.Properties
import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.client.VKMClient
import fi.liikennevirasto.digiroad2.client.viite.SearchViiteClient
import fi.liikennevirasto.digiroad2.client.{RoadLinkClient, RoadLinkFetched}
import fi.liikennevirasto.digiroad2.client.vvh.ChangeInfo
import fi.liikennevirasto.digiroad2.lane.LaneNumber.MainLane
import fi.liikennevirasto.digiroad2.lane.{LaneEndPoints, LaneRoadAddressInfo, NewLane, PersistedLane}
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.lane.LaneService
import fi.liikennevirasto.digiroad2.service.{RoadAddressForLink, RoadAddressService, RoadLinkService}
import fi.liikennevirasto.digiroad2.{DummyEventBus, DummySerializer}
import org.apache.http.impl.client.HttpClientBuilder
import org.joda.time.DateTime

case class RoadLinkWithAddresses(linkId: String, link: RoadLinkFetched, addresses: Set[RoadAddressForLink])

case class LaneUtils(){
  // DROTH-3057: Remove after lanes csv import is disabled
  def processNewLanesByRoadAddress(newLanes: Set[NewLane], laneRoadAddressInfo: LaneRoadAddressInfo,
                                   sideCode: Int, username: String, withTransaction: Boolean = true): Set[Long] = {
    LaneUtils.processNewLanesByRoadAddress(newLanes, laneRoadAddressInfo, sideCode, username, withTransaction)
  }
}
object LaneUtils {
  val eventbus = new DummyEventBus
  def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)

  lazy val laneService: LaneService = new LaneService(roadLinkService, eventbus, roadAddressService)
  lazy val roadLinkService: RoadLinkService = new RoadLinkService(roadLinkClient, eventbus, new DummySerializer)
  lazy val roadLinkClient: RoadLinkClient = { new RoadLinkClient(Digiroad2Properties.vvhRestApiEndPoint) }
  lazy val viiteClient: SearchViiteClient = { new SearchViiteClient(Digiroad2Properties.viiteRestApiEndPoint, HttpClientBuilder.create().build()) }
  lazy val roadAddressService: RoadAddressService = new RoadAddressService(viiteClient)


  lazy val MAIN_LANES = Seq(MainLane.towardsDirection, MainLane.againstDirection, MainLane.motorwayMaintenance)

  // DROTH-3057: Remove after lanes csv import is disabled
  def processNewLanesByRoadAddress(newLanes: Set[NewLane], laneRoadAddressInfo: LaneRoadAddressInfo,
                                   sideCode: Int, username: String, withTransaction: Boolean = true): Set[Long] = {
    // Main process
    def process() = {

      val linksWithAddresses = getRoadAddressToProcess(laneRoadAddressInfo)

      //Get only the lanes to create
      val lanesToInsert = newLanes.filter(_.id == 0)


      val allLanesToCreate = linksWithAddresses.flatMap { link =>
        val timeStamp = LinearAssetUtils.createTimeStamp()

        lanesToInsert.flatMap { lane =>
          val laneCode = laneService.getLaneCode(lane).toInt
          laneService.validateStartDate(lane, laneCode)

          val isTwoDigitLaneCode = laneCode.toString.length > 1
          val finalSideCode = laneService.fixSideCode( link.addresses.head, laneCode.toString )
          val laneCodeOneDigit = if (isTwoDigitLaneCode) laneCode.toString.substring(1).toInt
                                 else laneCode

          calculateStartAndEndPoint(laneRoadAddressInfo, link.addresses, link.link.length) match {
            case Some(endPoints) =>
              Some(PersistedLane(0, link.linkId, finalSideCode.value, laneCodeOneDigit, link.link.municipalityCode,
                endPoints.start, endPoints.end, Some(username), Some(DateTime.now()), None, None, None, None, expired = false,
                timeStamp, None, lane.properties))

            case _ => None
            }
        }
      }

      //Create lanes
      allLanesToCreate.map(laneService.createWithoutTransaction(_, username))

    }

    if(withTransaction) {
      withDynTransaction {
        process()
      }
    }else{
      process()
    }

  }

  def getRoadAddressToProcess(laneRoadAddressInfo: LaneRoadAddressInfo): Set[RoadLinkWithAddresses] = {

    // Generate a sequence from startRoadPart to endRoadPart
    // If startRoadPart = 1 and endRoadPart = 4
    // Result will be Seq(1,2,3,4)
    val roadParts = laneRoadAddressInfo.startRoadPart to laneRoadAddressInfo.endRoadPart

    // Get the road address information from Viite
    val roadAddresses = roadAddressService.getAllByRoadNumberAndParts(laneRoadAddressInfo.roadNumber, roadParts, Seq(Track.apply(laneRoadAddressInfo.track))).toSet

    // Group road addresses that exist on same link and exist on same road and road part
    val groupedAddresses = roadAddressService.groupRoadAddress(roadAddresses.toSeq)

    // Get all updated information from VVH
    val roadLinks = roadLinkService.fetchRoadlinksByIds(roadAddresses.map(_.linkId))

    val finalRoads = groupedAddresses.filter { elem =>  // Remove addresses which roadPart is not between our start and end
      val roadPartNumber = elem.roadPartNumber
      val inStartAndEndRoadPart = roadPartNumber >= laneRoadAddressInfo.startRoadPart && roadPartNumber <= laneRoadAddressInfo.endRoadPart

      inStartAndEndRoadPart
    }.toSet

    finalRoads.flatMap { road =>
      roadLinks.find(_.linkId == road.linkId) match {
        case Some(link) =>
          val allAddressesOnLink = roadAddresses.filter(_.linkId == road.linkId)
          Some(RoadLinkWithAddresses(road.linkId, link, allAddressesOnLink))
        case _ => None // Remove links (and addresses) that are not in VVH
      }
    }
  }

  /**
   * Calculate lane start and end values from all road addresses on a link.
   * Returns one start and end value per link or None.
   */
  def calculateStartAndEndPoint(selection: LaneRoadAddressInfo, addressesOnLink: Set[RoadAddressForLink],
                                linkLength: Double): Option[LaneEndPoints] = {
    val (linkAddressM, linkMValue) = // Determine total address length and m value within link
      addressesOnLink.foldLeft(0L, 0.0){ (result, address) =>
        (result._1 + address.endAddrMValue - address.startAddrMValue, result._2 + address.endMValue - address.startMValue)
      }

    // Viite uses frozen links, so determine new measure that matches current link length
    val viiteMeasure = linkMValue / linkAddressM
    val adjustedMeasure = linkLength / linkAddressM

    val startAndEndValues = addressesOnLink.flatMap { road =>
      val startMValue = road.startMValue / viiteMeasure * adjustedMeasure
      val endMValue = road.endMValue / viiteMeasure * adjustedMeasure
      val startDifferenceM = (selection.startDistance - road.startAddrMValue) * adjustedMeasure
      val endDifferenceM = (road.endAddrMValue - selection.endDistance) * adjustedMeasure
      val positiveStartDiff = startDifferenceM > 0
      val positiveEndDiff = endDifferenceM > 0
      val towardsDigitizing = road.sideCode == SideCode.TowardsDigitizing

      val startPoint = // Calculated start point for cases when lane does not start from the start of road address
        if (towardsDigitizing && positiveStartDiff) startMValue + startDifferenceM
        else if (!towardsDigitizing && positiveEndDiff) startMValue + endDifferenceM
        else startMValue
      val endPoint = // Calculated end point for cases when lane ends before the end of road address
        if (towardsDigitizing && positiveEndDiff) endMValue - endDifferenceM
        else if (!towardsDigitizing && positiveStartDiff) endMValue - startDifferenceM
        else endMValue

      val roadStartsAfterSelectionEnd =     road.startAddrMValue >= selection.endDistance
      val roadEndsBeforeSelectionStart =    road.endAddrMValue <= selection.startDistance
      val roadStartsBeforeSelectionStart =  road.startAddrMValue < selection.startDistance
      val roadEndsAfterSelectionEnd =       road.endAddrMValue > selection.endDistance

      // Determine if what endpoint values to use or if road address should be skipped
      // If adjusted start or end point is used and road side code is againstDigitising, the opposite value is adjusted
      road.roadPartNumber match {
        case part if part > selection.startRoadPart && part < selection.endRoadPart =>
          Some(startMValue, endMValue)

        case part if part == selection.startRoadPart && part == selection.endRoadPart =>
          if (roadEndsBeforeSelectionStart || roadStartsAfterSelectionEnd)
            None
          else if (roadStartsBeforeSelectionStart && roadEndsAfterSelectionEnd)
            Some( startPoint, endPoint )
          else if (roadStartsBeforeSelectionStart)
            if (towardsDigitizing) Some( startPoint, endMValue ) else Some( startMValue, endPoint )
          else if (roadEndsAfterSelectionEnd)
            if (towardsDigitizing) Some( startMValue, endPoint ) else Some( startPoint, endMValue )
          else
            Some( startMValue, endMValue)

        case part if part == selection.startRoadPart =>
          if (roadEndsBeforeSelectionStart)
            None
          else if (roadStartsBeforeSelectionStart)
            if (towardsDigitizing) Some( startPoint, endMValue ) else Some( startMValue, endPoint)
          else
            Some(startMValue, endMValue)

        case part if part == selection.endRoadPart =>
          if (roadStartsAfterSelectionEnd)
            None
          else if (roadEndsAfterSelectionEnd)
            if (towardsDigitizing) Some( startMValue, endPoint ) else Some( startPoint, endMValue )
          else
            Some(startMValue, endMValue)

        case _ => None
      }
    }

    startAndEndValues.size match {// Returns one start and end point per link or None
      case size if size == 1 =>
        Some(LaneEndPoints(roundMeasure(startAndEndValues.head._1), roundMeasure(startAndEndValues.head._2)))
      case size if size > 1 => // If link has multiple endpoints return smallest start value and biggest end value
        Some(LaneEndPoints(roundMeasure(startAndEndValues.minBy(_._1)._1), roundMeasure(startAndEndValues.maxBy(_._2)._2)))
      case _ =>
        None
    }
  }

  // Rounds double value to given number of decimals. Used with lane start and end measures
  def roundMeasure(measure: Double, numberOfDecimals: Int = 3): Double = {
    val exponentOfTen = Math.pow(10, numberOfDecimals)
    Math.round(measure * exponentOfTen).toDouble / exponentOfTen
  }

  def getMappedChanges(changes: Seq[ChangeInfo]): Map[String, Seq[ChangeInfo]] = {
    (changes.filter(_.oldId.nonEmpty).map(c => c.oldId.get -> c) ++ changes.filter(_.newId.nonEmpty)
      .map(c => c.newId.get -> c)).groupBy(_._1).mapValues(_.map(_._2))
  }

  def deletedRoadLinkIds(changes: Map[String, Seq[ChangeInfo]], currentLinkIds: Set[String]): Seq[String] = {
    changes.filter(c =>
      !c._2.exists(ci => ci.newId.contains(c._1)) &&
        !currentLinkIds.contains(c._1)
    ).keys.toSeq
  }

  def newChangeInfoDetected(lane : PersistedLane, changes: Map[String, Seq[ChangeInfo]]): Boolean = {
    changes.getOrElse(lane.linkId, Seq()).exists(c =>
      c.timeStamp > lane.timeStamp && (c.oldId.getOrElse(0) == lane.linkId || c.newId.getOrElse(0) == lane.linkId)
    )
  }


}
