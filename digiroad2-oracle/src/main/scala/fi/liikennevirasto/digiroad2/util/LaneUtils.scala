package fi.liikennevirasto.digiroad2.util

import java.util.Properties
import fi.liikennevirasto.digiroad2.client.viite.SearchViiteClient
import fi.liikennevirasto.digiroad2.client.vvh.{ChangeInfo, VVHClient}
import fi.liikennevirasto.digiroad2.dao.RoadLinkTempDAO
import fi.liikennevirasto.digiroad2.lane.LaneNumber.MainLane
import fi.liikennevirasto.digiroad2.lane.{LaneRoadAddressInfo, NewIncomeLane, PersistedLane}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.lane.LaneService
import fi.liikennevirasto.digiroad2.service.{RoadAddressService, RoadLinkService}
import fi.liikennevirasto.digiroad2.{DummyEventBus, DummySerializer}
import org.apache.http.impl.client.HttpClientBuilder
import org.joda.time.DateTime

case class RoadAddressesAux( linkId: Long, roadNumber: Long, roadPart: Long, track: Track, municipalityCode: Int,
                          startMValue: Double, endMValue: Double, startAddrMValue:Long, endAddrMValue: Long)
case class LaneUtils(){
  def processNewLanesByRoadAddress(newIncomeLanes: Set[NewIncomeLane], laneRoadAddressInfo: LaneRoadAddressInfo,
    sideCode: Int, username: String, withTransaction: Boolean = true): Any = {
    LaneUtils.processNewLanesByRoadAddress(newIncomeLanes, laneRoadAddressInfo, sideCode, username, withTransaction)
  }
}
object LaneUtils {
  lazy val roadLinkTempDAO: RoadLinkTempDAO = new RoadLinkTempDAO
  val eventbus = new DummyEventBus
  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)

  lazy val laneService: LaneService = new LaneService(roadLinkService, eventbus)
  lazy val roadLinkService: RoadLinkService = new RoadLinkService(vvhClient, eventbus, new DummySerializer)
  lazy val vvhClient: VVHClient = { new VVHClient(getProperty("digiroad2.VVHRestApiEndPoint")) }
  lazy val viiteClient: SearchViiteClient = { new SearchViiteClient(getProperty("digiroad2.viiteRestApiEndPoint"), HttpClientBuilder.create().build()) }
  lazy val roadAddressService: RoadAddressService = new RoadAddressService(viiteClient)

  lazy val MAIN_LANES = Seq(MainLane.towardsDirection, MainLane.againstDirection, MainLane.motorwayMaintenance)

  lazy val dr2properties: Properties = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/digiroad2.properties"))
    props
  }

  protected def getProperty(name: String) = {
    val property = dr2properties.getProperty(name)
    if(property != null)
      property
    else
      throw new RuntimeException(s"cannot find property $name")
  }

  def separateNewIncomeLanes( newIncomeLanes: Set[NewIncomeLane]) : (Set[Long],Set[Long],Set[NewIncomeLane],Set[NewIncomeLane]) = {

    val lanesWithoutFlags = newIncomeLanes.filterNot( lane => lane.isDeleted  || lane.isExpired ) // Remove records with those flags as true

    val toDelete = newIncomeLanes.filter( _.isDeleted == true ).map( _.id )
    val toHistory = newIncomeLanes.filter( _.isExpired == true ).map( _.id )
    val (toInsert, toUpdate) = lanesWithoutFlags.partition(_.id == 0)

    (toDelete, toHistory, toUpdate, toInsert)
  }


  def processNewIncomeLanes(newIncomeLanes: Set[NewIncomeLane], linkIds: Set[Long],
                             sideCode: Int, username: String) = {

    val (toDelete, toHistory, toUpdate, toInsert) = separateNewIncomeLanes(newIncomeLanes)
    laneService.deleteMultipleLanes(toDelete)
    laneService.multipleLanesToHistory(toHistory, username)
    laneService.create(toInsert.toSeq, linkIds, sideCode, username)
    laneService.update(toUpdate.toSeq, linkIds, sideCode, username)

  }

  def processNewLanesByRoadAddress(newIncomeLanes: Set[NewIncomeLane], laneRoadAddressInfo: LaneRoadAddressInfo,
                                   sideCode: Int, username: String, withTransaction: Boolean = true): Any = {

    def getRoadAddressToProcess(): Set[RoadAddressesAux] = {

      // Generate a sequence from initialRoadPartNumber to endRoadPartNumber
      // If initialRoadPartNumber = 1 and endRoadPartNumber = 4
      // Result will be Seq(1,2,3,4)
      val roadParts = laneRoadAddressInfo.initialRoadPartNumber to laneRoadAddressInfo.endRoadPartNumber

      // Get the road address information from Viite and convert the data to RoadAddressesAux
      val roadAddresses = roadAddressService.getAllByRoadNumberAndParts(laneRoadAddressInfo.roadNumber, roadParts, Seq(Track.apply(laneRoadAddressInfo.track)))
                                            .map (elem => RoadAddressesAux(elem.linkId, elem.roadNumber, elem.roadPartNumber,
                                              elem.track, 0, elem.startMValue, elem.endMValue, elem.startAddrMValue,
                                              elem.endAddrMValue)
                                            )
      // Get the road address information from our DB and convert the data to RoadAddressesAux
      val vkmRoadAddress = roadLinkTempDAO.getByRoadNumberRoadPartTrack(laneRoadAddressInfo.roadNumber.toInt, laneRoadAddressInfo.track, roadParts.toSet)
                                          .map(elem => RoadAddressesAux(elem.linkId, elem.road, elem.roadPart,
                                            elem.track, elem.municipalityCode.getOrElse(0), elem.startMValue, elem.endMValue,
                                            elem.startAddressM, elem.endAddressM)
                                          )

      val vkmLinkIds = vkmRoadAddress.map(_.linkId)

      // Remove from Viite the information we have updated in our DB and add ou information
      val allRoadAddress = (roadAddresses.filterNot( ra => vkmLinkIds.contains( ra.linkId)) ++ vkmRoadAddress).toSet

      // Get all updated information from VVH
      val mappedRoadLinks = roadLinkService.fetchVVHRoadlinks(allRoadAddress.map(_.linkId))
                                           .groupBy(_.linkId)

      val finalRoads = allRoadAddress.filter { elem =>       // Remove the links that are not in VVH and roadPart between our initial and end
                                                val existsInVVH = mappedRoadLinks.contains(elem.linkId)
                                                val roadPartNumber = elem.roadPart
                                                val inInitialAndEndRoadPart = roadPartNumber >= laneRoadAddressInfo.initialRoadPartNumber && roadPartNumber <= laneRoadAddressInfo.endRoadPartNumber

                                                existsInVVH && inInitialAndEndRoadPart
                                            }
                                    .map{ elem =>             //In case we don't have municipalityCode we will get it from VVH info
                                              if (elem.municipalityCode == 0)
                                                elem.copy( municipalityCode = mappedRoadLinks(elem.linkId).head.municipalityCode )
                                              else
                                                elem
                                        }

      finalRoads
    }

    def calculateStartAndEndPoint(road: RoadAddressesAux, startPoint: Double, endPoint: Double )= {

      if (road.roadPart > laneRoadAddressInfo.initialRoadPartNumber && road.roadPart < laneRoadAddressInfo.endRoadPartNumber) {
        ( road.startMValue, road.endMValue)

      }
      else if (road.roadPart == laneRoadAddressInfo.initialRoadPartNumber && road.roadPart == laneRoadAddressInfo.endRoadPartNumber) {

        if (!(road.endAddrMValue > laneRoadAddressInfo.initialDistance && road.startAddrMValue < laneRoadAddressInfo.endDistance))
          (None, None)

        else if (road.startAddrMValue <= laneRoadAddressInfo.initialDistance && road.endAddrMValue >= laneRoadAddressInfo.endDistance) {
          ( startPoint, endPoint )

        }
        else if (road.startAddrMValue <= laneRoadAddressInfo.initialDistance && road.endAddrMValue < laneRoadAddressInfo.endDistance) {
          ( startPoint, road.endMValue )

        }
        else if (road.startAddrMValue > laneRoadAddressInfo.initialDistance && road.endAddrMValue >= laneRoadAddressInfo.endDistance) {
          ( road.startMValue, endPoint )

        }
        else {
          ( road.startMValue, road.endMValue )

        }

      }
      else if (road.roadPart == laneRoadAddressInfo.initialRoadPartNumber) {
        if (road.endAddrMValue <= laneRoadAddressInfo.initialDistance) {
          (None, None)

        } else if (road.startAddrMValue < laneRoadAddressInfo.initialDistance) {
          ( startPoint, road.endMValue )

        } else {
          ( road.startMValue, road.endMValue )
        }

      }
      else if (road.roadPart == laneRoadAddressInfo.endRoadPartNumber) {
        if (road.startAddrMValue >= laneRoadAddressInfo.endDistance) {
          (None, None)

        } else if (road.endAddrMValue > laneRoadAddressInfo.endDistance) {
          ( road.startMValue, endPoint )

        } else {
          ( road.startMValue, road.endMValue )

        }
      }
      else {
        (None, None)
      }
    }

    // Main process
    def process() = {

      val filteredRoadAddresses = getRoadAddressToProcess()

      //Get only the lanes to create
      val lanesToInsert = newIncomeLanes.filter(_.id == 0)


      val allLanesToCreate = filteredRoadAddresses.flatMap { road =>
        val vvhTimeStamp = vvhClient.roadLinkData.createVVHTimeStamp()

        lanesToInsert.flatMap { lane =>
          val laneCodeProperty = lane.properties.find(_.publicId == "lane_code")
                                                .getOrElse(throw new IllegalArgumentException("Lane Code attribute not found!"))

          val laneCodeValue = laneCodeProperty.values.head.value
          val laneCode = if ( laneCodeValue != None && laneCodeValue.toString.trim.nonEmpty )
                          laneCodeValue.toString.trim.toInt
                         else
                          throw new IllegalArgumentException("Lane Code attribute Empty!")

          val isMainLane = MAIN_LANES.contains(laneCode)

          val startDifferenceAddr = laneRoadAddressInfo.initialDistance - road.startAddrMValue
          val startPoint = if (isMainLane || startDifferenceAddr <= 0) road.startMValue else startDifferenceAddr
          val endDifferenceAddr = road.endAddrMValue - laneRoadAddressInfo.endDistance
          val endPoint = if (isMainLane || endDifferenceAddr <= 0) road.endMValue else road.endMValue - endDifferenceAddr

          calculateStartAndEndPoint(road, startPoint, endPoint) match{
            case (start: Double, end: Double) =>  Some(PersistedLane(0, road.linkId, sideCode, laneCode, road.municipalityCode,
                                                      start, end, Some(username), Some(DateTime.now()),
                                                      None, None, None,None, expired = false,
                                                      vvhTimeStamp, None, lane.properties))
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
