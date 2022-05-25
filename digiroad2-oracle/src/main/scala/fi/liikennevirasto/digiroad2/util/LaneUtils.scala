package fi.liikennevirasto.digiroad2.util

import java.util.Properties
import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.client.VKMClient
import fi.liikennevirasto.digiroad2.client.viite.SearchViiteClient
import fi.liikennevirasto.digiroad2.client.vvh.{ChangeInfo, VVHClient, VVHRoadlink}
import fi.liikennevirasto.digiroad2.dao.{RoadAddressTEMP, RoadLinkTempDAO}
import fi.liikennevirasto.digiroad2.lane.LaneNumber.MainLane
import fi.liikennevirasto.digiroad2.lane.{LaneEndPoints, LaneRoadAddressInfo, NewLane, PersistedLane}
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.lane.LaneService
import fi.liikennevirasto.digiroad2.service.{RoadAddressService, RoadLinkService}
import fi.liikennevirasto.digiroad2.{DummyEventBus, DummySerializer}
import org.apache.http.impl.client.HttpClientBuilder
import org.joda.time.DateTime


case class LaneUtils(){
  // DROTH-3057: Remove after lanes csv import is disabled
  def processNewLanesByRoadAddress(newLanes: Set[NewLane], laneRoadAddressInfo: LaneRoadAddressInfo,
                                   sideCode: Int, username: String, withTransaction: Boolean = true): Set[Long] = {
    LaneUtils.processNewLanesByRoadAddress(newLanes, laneRoadAddressInfo, sideCode, username, withTransaction)
  }
}
object LaneUtils {
  lazy val roadLinkTempDAO: RoadLinkTempDAO = new RoadLinkTempDAO
  val eventbus = new DummyEventBus
  def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)

  lazy val laneService: LaneService = new LaneService(roadLinkService, eventbus, roadAddressService)
  lazy val roadLinkService: RoadLinkService = new RoadLinkService(vvhClient, eventbus, new DummySerializer)
  lazy val vvhClient: VVHClient = { new VVHClient(Digiroad2Properties.vvhRestApiEndPoint) }
  lazy val viiteClient: SearchViiteClient = { new SearchViiteClient(Digiroad2Properties.viiteRestApiEndPoint, HttpClientBuilder.create().build()) }
  lazy val roadAddressService: RoadAddressService = new RoadAddressService(viiteClient)


  lazy val MAIN_LANES = Seq(MainLane.towardsDirection, MainLane.againstDirection, MainLane.motorwayMaintenance)

  // DROTH-3057: Remove after lanes csv import is disabled
  def processNewLanesByRoadAddress(newLanes: Set[NewLane], laneRoadAddressInfo: LaneRoadAddressInfo,
                                   sideCode: Int, username: String, withTransaction: Boolean = true): Set[Long] = {
    // Main process
    def process() = {

      val (filteredRoadAddresses, vvhRoadLinks) = getRoadAddressToProcess(laneRoadAddressInfo)
      val addressesByLinks = filteredRoadAddresses.groupBy(_.linkId)

      //Get only the lanes to create
      val lanesToInsert = newLanes.filter(_.id == 0)


      val allLanesToCreate = addressesByLinks.flatMap { case (linkId, addressesOnLink) =>
        val vvhTimeStamp = vvhClient.roadLinkData.createVVHTimeStamp()
        val linkLength = vvhRoadLinks.find(_.linkId == linkId) match {
          case Some(roadLink) => roadLink.length
          case _ => addressesOnLink.head.endMValue
        }

        lanesToInsert.flatMap { lane =>
          val laneCode = laneService.getLaneCode(lane).toInt
          laneService.validateStartDate(lane, laneCode)

          val isTwoDigitLaneCode = laneCode.toString.length > 1
          val finalSideCode = laneService.fixSideCode( addressesOnLink.head, laneCode.toString )
          val laneCodeOneDigit = if (isTwoDigitLaneCode) laneCode.toString.substring(1).toInt
                                 else laneCode

          calculateStartAndEndPoint(laneRoadAddressInfo, addressesOnLink, linkLength) match {
            case Some(endPoints) =>
              Some(PersistedLane(0, linkId, finalSideCode.value, laneCodeOneDigit, addressesOnLink.head.municipalityCode.getOrElse(0).toLong,
                endPoints.start, endPoints.end, Some(username), Some(DateTime.now()), None, None, None, None, expired = false,
                vvhTimeStamp, None, lane.properties))

            case _ => None
            }
        }
      }.toSet

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

  def getRoadAddressToProcess(laneRoadAddressInfo: LaneRoadAddressInfo): (Set[RoadAddressTEMP], Seq[VVHRoadlink]) = {

    // Generate a sequence from startRoadPart to endRoadPart
    // If startRoadPart = 1 and endRoadPart = 4
    // Result will be Seq(1,2,3,4)
    val roadParts = laneRoadAddressInfo.startRoadPart to laneRoadAddressInfo.endRoadPart

    // Get the road address information from Viite and convert the data to RoadAddressesTEMP
    val roadAddresses = roadAddressService.getAllByRoadNumberAndParts(laneRoadAddressInfo.roadNumber, roadParts, Seq(Track.apply(laneRoadAddressInfo.track)))
      .map (elem => RoadAddressTEMP (elem.linkId, elem.roadNumber, elem.roadPartNumber, elem.track,
        elem.startAddrMValue, elem.endAddrMValue, elem.startMValue, elem.endMValue,elem.geom, Some(elem.sideCode), Some(0) )
      )

    // Get the road address information from our DB and convert the data to RoadAddressesTEMP
    val vkmRoadAddress = roadLinkTempDAO.getByRoadNumberRoadPartTrack(laneRoadAddressInfo.roadNumber.toInt, laneRoadAddressInfo.track, roadParts.toSet)


    val vkmLinkIds = vkmRoadAddress.map(_.linkId)

    // Remove from Viite the information we have updated in our DB and add ou information
    val allRoadAddress = (roadAddresses.filterNot( ra => vkmLinkIds.contains( ra.linkId)) ++ vkmRoadAddress).toSet

    // Group road addresses that exist on same link and exist on same road and road part
    val groupedAddresses = roadAddressService.groupRoadAddressTEMP(allRoadAddress)

    // Get all updated information from VVH
    val roadLinks = roadLinkService.fetchVVHRoadlinks(allRoadAddress.map(_.linkId))
    val mappedRoadLinks = roadLinks.groupBy(_.linkId)

    val finalRoads = groupedAddresses.filter { elem =>       // Remove the links that are not in VVH and roadPart between our initial and end
      val existsInVVH = mappedRoadLinks.contains(elem.linkId)
      val roadPartNumber = elem.roadPart
      val inInitialAndEndRoadPart = roadPartNumber >= laneRoadAddressInfo.startRoadPart && roadPartNumber <= laneRoadAddressInfo.endRoadPart

      existsInVVH && inInitialAndEndRoadPart
    }
      .map{ elem =>             //In case we don't have municipalityCode we will get it from VVH info
        if (elem.municipalityCode.getOrElse(0) == 0)
          elem.copy( municipalityCode = Some( mappedRoadLinks(elem.linkId).head.municipalityCode) )
        else
          elem
      }

    (finalRoads, roadLinks)
  }

  /**
   * Calculate lane start and end values from all road addresses on a link.
   * Returns one start and end value per link or None.
   */
  def calculateStartAndEndPoint(selection: LaneRoadAddressInfo, addressesOnLink: Set[RoadAddressTEMP],
                                linkLength: Double): Option[LaneEndPoints] = {
    val (linkAddressM, linkMValue) = // Determine total address length and m value within link
      addressesOnLink.foldLeft(0L, 0.0){ (result, address) =>
        (result._1 + address.endAddressM - address.startAddressM, result._2 + address.endMValue - address.startMValue)
      }

    // Viite uses frozen links, so determine new measure that matches current link length
    val viiteMeasure = linkMValue / linkAddressM
    val adjustedMeasure = linkLength / linkAddressM

    val startAndEndValues = addressesOnLink.flatMap { road =>
      val startMValue = road.startMValue / viiteMeasure * adjustedMeasure
      val endMValue = road.endMValue / viiteMeasure * adjustedMeasure
      val startDifferenceM = (selection.startDistance - road.startAddressM) * adjustedMeasure
      val endDifferenceM = (road.endAddressM - selection.endDistance) * adjustedMeasure
      val positiveStartDiff = startDifferenceM > 0
      val positiveEndDiff = endDifferenceM > 0
      val towardsDigitizing = road.sideCode.getOrElse(SideCode.TowardsDigitizing) == SideCode.TowardsDigitizing

      val startPoint = // Calculated start point for cases when lane does not start from the start of road address
        if (towardsDigitizing && positiveStartDiff) startMValue + startDifferenceM
        else if (!towardsDigitizing && positiveEndDiff) startMValue + endDifferenceM
        else startMValue
      val endPoint = // Calculated end point for cases when lane ends before the end of road address
        if (towardsDigitizing && positiveEndDiff) endMValue - endDifferenceM
        else if (!towardsDigitizing && positiveStartDiff) endMValue - startDifferenceM
        else endMValue

      val roadStartsAfterSelectionEnd =     road.startAddressM >= selection.endDistance
      val roadEndsBeforeSelectionStart =    road.endAddressM <= selection.startDistance
      val roadStartsBeforeSelectionStart =  road.startAddressM < selection.startDistance
      val roadEndsAfterSelectionEnd =       road.endAddressM > selection.endDistance

      // Determine if what endpoint values to use or if road address should be skipped
      // If adjusted start or end point is used and road side code is againstDigitising, the opposite value is adjusted
      road.roadPart match {
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

  def getMappedChanges(changes: Seq[ChangeInfo]): Map[Long, Seq[ChangeInfo]] = {
    (changes.filter(_.oldId.nonEmpty).map(c => c.oldId.get -> c) ++ changes.filter(_.newId.nonEmpty)
      .map(c => c.newId.get -> c)).groupBy(_._1).mapValues(_.map(_._2))
  }

  def deletedRoadLinkIds(changes: Map[Long, Seq[ChangeInfo]], currentLinkIds: Set[Long]): Seq[Long] = {
    changes.filter(c =>
      !c._2.exists(ci => ci.newId.contains(c._1)) &&
        !currentLinkIds.contains(c._1)
    ).keys.toSeq
  }

  def newChangeInfoDetected(lane : PersistedLane, changes: Map[Long, Seq[ChangeInfo]]): Boolean = {
    changes.getOrElse(lane.linkId, Seq()).exists(c =>
      c.vvhTimeStamp > lane.vvhTimeStamp && (c.oldId.getOrElse(0) == lane.linkId || c.newId.getOrElse(0) == lane.linkId)
    )
  }


}
