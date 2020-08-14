package fi.liikennevirasto.digiroad2.util

import java.util.Properties
import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.client.viite.SearchViiteClient
import fi.liikennevirasto.digiroad2.client.vvh.{ChangeInfo, VVHClient}
import fi.liikennevirasto.digiroad2.dao.{RoadAddressTEMP, RoadLinkTempDAO}
import fi.liikennevirasto.digiroad2.lane.LaneNumber.MainLane
import fi.liikennevirasto.digiroad2.lane.{LaneRoadAddressInfo, NewIncomeLane, PersistedLane}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.lane.LaneService
import fi.liikennevirasto.digiroad2.service.{RoadAddressService, RoadLinkService}
import fi.liikennevirasto.digiroad2.{DummyEventBus, DummySerializer}
import org.apache.http.impl.client.HttpClientBuilder
import org.joda.time.DateTime


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

  def processNewLanesByRoadAddress(newIncomeLanes: Set[NewIncomeLane], laneRoadAddressInfo: LaneRoadAddressInfo,
                                   sideCode: Int, username: String, withTransaction: Boolean = true): Any = {

    def getRoadAddressToProcess(): Set[RoadAddressTEMP] = {

      // Generate a sequence from initialRoadPartNumber to endRoadPartNumber
      // If initialRoadPartNumber = 1 and endRoadPartNumber = 4
      // Result will be Seq(1,2,3,4)
      val roadParts = laneRoadAddressInfo.initialRoadPartNumber to laneRoadAddressInfo.endRoadPartNumber

      // Get the road address information from Viite and convert the data to RoadAddressesAux
      val roadAddresses = roadAddressService.getAllByRoadNumberAndParts(laneRoadAddressInfo.roadNumber, roadParts, Seq(Track.apply(laneRoadAddressInfo.track)))
                                            .map (elem => RoadAddressTEMP (elem.linkId, elem.roadNumber, elem.roadPartNumber, elem.track,
                                              elem.startAddrMValue, elem.endAddrMValue, elem.startMValue, elem.endMValue,elem.geom, Some(elem.sideCode), Some(0) )
                                            )

      // Get the road address information from our DB and convert the data to RoadAddressesAux
      val vkmRoadAddress = roadLinkTempDAO.getByRoadNumberRoadPartTrack(laneRoadAddressInfo.roadNumber.toInt, laneRoadAddressInfo.track, roadParts.toSet)


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
                                              if (elem.municipalityCode.getOrElse(0) == 0)
                                                elem.copy( municipalityCode = Some( mappedRoadLinks(elem.linkId).head.municipalityCode) )
                                              else
                                                elem
                                        }

      finalRoads
    }

    def calculateStartAndEndPoint(road: RoadAddressTEMP, startPoint: Double, endPoint: Double )= {

      val (start, end) = if (road.roadPart > laneRoadAddressInfo.initialRoadPartNumber && road.roadPart < laneRoadAddressInfo.endRoadPartNumber) {
        ( road.startMValue, road.endMValue)

      }
      else if (road.roadPart == laneRoadAddressInfo.initialRoadPartNumber && road.roadPart == laneRoadAddressInfo.endRoadPartNumber) {

        if (!(road.endAddressM > laneRoadAddressInfo.initialDistance && road.startAddressM < laneRoadAddressInfo.endDistance))
          (None, None)

        else if (road.startAddressM <= laneRoadAddressInfo.initialDistance && road.endAddressM >= laneRoadAddressInfo.endDistance) {
          ( startPoint, endPoint )

        }
        else if (road.startAddressM <= laneRoadAddressInfo.initialDistance && road.endAddressM < laneRoadAddressInfo.endDistance) {
          ( startPoint, road.endMValue )

        }
        else if (road.startAddressM > laneRoadAddressInfo.initialDistance && road.endAddressM >= laneRoadAddressInfo.endDistance) {
          ( road.startMValue, endPoint )

        }
        else {
          ( road.startMValue, road.endMValue )

        }

      }
      else if (road.roadPart == laneRoadAddressInfo.initialRoadPartNumber) {
        if (road.endAddressM <= laneRoadAddressInfo.initialDistance) {
          (None, None)

        } else if (road.startAddressM < laneRoadAddressInfo.initialDistance) {
          ( startPoint, road.endMValue )

        } else {
          ( road.startMValue, road.endMValue )
        }

      }
      else if (road.roadPart == laneRoadAddressInfo.endRoadPartNumber) {
        if (road.startAddressM >= laneRoadAddressInfo.endDistance) {
          (None, None)

        } else if (road.endAddressM > laneRoadAddressInfo.endDistance) {
          ( road.startMValue, endPoint )

        } else {
          ( road.startMValue, road.endMValue )

        }
      }
      else {
        (None, None)
      }

      //Fix the start and end point when the roadAddress SideCode is AgainstDigitizing
      (start, end) match {
        case (_: Double , e: Double) =>  if (road.sideCode.getOrElse(SideCode.TowardsDigitizing) == SideCode.AgainstDigitizing)
                                          (road.endMValue - e, road.endMValue )
                                        else
                                          (start, end)
        case _  => (None, None)
      }
    }

    // Main process
    def process() = {

      val filteredRoadAddresses = getRoadAddressToProcess()

      //Get only the lanes to create
      val lanesToInsert = laneService.populateStartDate(newIncomeLanes.filter(_.id == 0))


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

          val startDifferenceAddr = laneRoadAddressInfo.initialDistance - road.startAddressM
          val startPoint = if (isMainLane || startDifferenceAddr <= 0) road.startMValue else startDifferenceAddr
          val endDifferenceAddr = road.endAddressM - laneRoadAddressInfo.endDistance
          val endPoint = if (isMainLane || endDifferenceAddr <= 0) road.endMValue else road.endMValue - endDifferenceAddr

          val finalSideCode = laneService.fixSideCode( road, laneCode.toString )

          calculateStartAndEndPoint(road, startPoint, endPoint) match {
            case (start: Double, end: Double) =>
              Some(PersistedLane(0, road.linkId, finalSideCode.value, laneCode, road.municipalityCode.getOrElse(0).toLong,
                start, end, Some(username), Some(DateTime.now()), None, None, None, None, expired = false,
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
