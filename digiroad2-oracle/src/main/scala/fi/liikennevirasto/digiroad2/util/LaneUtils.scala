package fi.liikennevirasto.digiroad2.util

import java.util.Properties

import fi.liikennevirasto.digiroad2.client.viite.SearchViiteClient
import fi.liikennevirasto.digiroad2.client.vvh.{ChangeInfo, VVHClient}
import fi.liikennevirasto.digiroad2.dao.RoadLinkTempDAO
import fi.liikennevirasto.digiroad2.lane.{LaneRoadAddressInfo, NewIncomeLane, PersistedLane}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.lane.LaneService
import fi.liikennevirasto.digiroad2.service.{RoadAddressService, RoadLinkService}
import fi.liikennevirasto.digiroad2.{DummyEventBus, DummySerializer}
import org.apache.http.impl.client.HttpClientBuilder
import org.joda.time.DateTime

object LaneUtils {
  lazy val roadLinkTempDAO: RoadLinkTempDAO = new RoadLinkTempDAO
  val eventbus = new DummyEventBus
  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)

  lazy val laneService = new LaneService(roadLinkService, eventbus)

  lazy val roadLinkService = new RoadLinkService(vvhClient, eventbus, new DummySerializer)
  lazy val vvhClient: VVHClient = { new VVHClient(getProperty("digiroad2.VVHRestApiEndPoint")) }

  lazy val viiteClient: SearchViiteClient = { new SearchViiteClient(getProperty("digiroad2.viiteRestApiEndPoint"), HttpClientBuilder.create().build()) }
  lazy val roadAddressService : RoadAddressService = new RoadAddressService(viiteClient)

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

    val lanesWithoutFlags = newIncomeLanes.filter( _.isDeleted == false)
                                        .filter(_.isExpired == false)

    val toDelete = newIncomeLanes.filter( _.isDeleted == true ).map( _.id )
    val toHistory = newIncomeLanes.filter( _.isExpired == true ).map( _.id )
    val toUpdate = lanesWithoutFlags.filter( _.id != 0 )
    val toInsert = lanesWithoutFlags.filter( _.id == 0 )

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

    //if (!newIncomeLanes.exists(_.id == 0) )
    // return Seq()


    // used TierekisteriAssetImporterOperations.importAssets as example
    def process() = {
      val roadParts = laneRoadAddressInfo.initialRoadPartNumber to laneRoadAddressInfo.endRoadPartNumber

      val roadAddresses = roadAddressService.getAllByRoadNumberAndParts(laneRoadAddressInfo.roadNumber, roadParts, Seq(Track.apply(laneRoadAddressInfo.track)))
      val vkmRoadAddress = roadLinkTempDAO.getByRoadNumberRoadPartTrack(laneRoadAddressInfo.roadNumber.toInt, laneRoadAddressInfo.track, roadParts.toSet)

      val mappedRoadLinks = roadLinkService.fetchVVHRoadlinks((roadAddresses.map(ra => ra.linkId) ++ vkmRoadAddress.map(_.linkId)).toSet)

      val filteredLinkIds = mappedRoadLinks.filter { x =>
        val roadPartNumber = x.attributes("ROADPARTNUMBER").toString.toInt
        roadPartNumber >= laneRoadAddressInfo.initialRoadPartNumber || roadPartNumber <= laneRoadAddressInfo.endRoadPartNumber
      }
        .map(_.linkId)


      val filteredRoadAddresses = roadAddresses.filter(x => filteredLinkIds.contains(x.linkId))

      val lanesToInsert = newIncomeLanes.filter(_.id == 0)

      val allLanesToCreate = filteredRoadAddresses.flatMap { road =>

        val vvhTimeStamp = vvhClient.roadLinkData.createVVHTimeStamp()
        val vvhRoadLink = mappedRoadLinks.find(_.linkId == road.linkId).get
        val municipalityCode = vvhRoadLink.municipalityCode

        lanesToInsert.map { lane =>

          val laneCodeProperty = lane.attributes.properties.find(_.publicId == "lane_code").getOrElse(throw new IllegalArgumentException("Lane Code attribute not found!"))
          val laneCode = laneCodeProperty.values.head.value.toString.toInt
          val isMainLane = laneCode.toString.charAt(1).getNumericValue == 1

          // Conversion from AddressMValue to MValue we used in LinkIds
          val startDifferenceAddr = road.endAddrMValue - laneRoadAddressInfo.initialDistance
          val startPoint = if (isMainLane) 0 else Math.abs(road.startMValue - (startDifferenceAddr * road.endMValue / road.endAddrMValue))
          val endDifferenceAddr = road.endAddrMValue - laneRoadAddressInfo.endDistance
          val endPoint = if (isMainLane) vvhRoadLink.length else Math.abs(road.startMValue - (endDifferenceAddr * road.endMValue / road.endAddrMValue))

          if (road.roadPartNumber > laneRoadAddressInfo.initialRoadPartNumber && road.roadPartNumber < laneRoadAddressInfo.endRoadPartNumber) {

            PersistedLane(0, road.linkId, sideCode, laneCode, municipalityCode,
              road.startMValue, road.endMValue,
              Some(username), Some(DateTime.now()),
              None, None, expired = false,
              vvhTimeStamp, None, lane.attributes)

          }
          else if (road.roadPartNumber == laneRoadAddressInfo.initialRoadPartNumber && road.roadPartNumber == laneRoadAddressInfo.endRoadPartNumber) {

            if (!(road.endAddrMValue > laneRoadAddressInfo.initialDistance && road.startAddrMValue < laneRoadAddressInfo.endDistance))
              None

            else if (road.startAddrMValue <= laneRoadAddressInfo.initialDistance && road.endAddrMValue >= laneRoadAddressInfo.endDistance) {

              PersistedLane(0, road.linkId, sideCode, laneCode, municipalityCode,
                startPoint, endPoint,
                Some(username), Some(DateTime.now()),
                None, None, expired = false,
                vvhTimeStamp, None, lane.attributes)
            }
            else if (road.startAddrMValue <= laneRoadAddressInfo.initialDistance && road.endAddrMValue < laneRoadAddressInfo.endDistance) {
              PersistedLane(0, road.linkId, sideCode, laneCode, municipalityCode,
                startPoint, road.endMValue,
                Some(username), Some(DateTime.now()),
                None, None, expired = false,
                vvhTimeStamp, None, lane.attributes)
            }
            else if (road.startAddrMValue > laneRoadAddressInfo.initialDistance && road.endAddrMValue >= laneRoadAddressInfo.endDistance) {
              PersistedLane(0, road.linkId, sideCode, laneCode, municipalityCode,
                road.startMValue, endPoint,
                Some(username), Some(DateTime.now()),
                None, None, expired = false,
                vvhTimeStamp, None, lane.attributes)
            }
            else {
              PersistedLane(0, road.linkId, sideCode, laneCode, municipalityCode,
                road.startMValue, road.endMValue,
                Some(username), Some(DateTime.now()),
                None, None, expired = false,
                vvhTimeStamp, None, lane.attributes)
            }

          }
          else if (road.roadPartNumber == laneRoadAddressInfo.initialRoadPartNumber) {
            if (road.endAddrMValue < laneRoadAddressInfo.initialDistance) {
              None
            } else if (road.startAddrMValue <= laneRoadAddressInfo.initialDistance) {
              PersistedLane(0, road.linkId, sideCode, laneCode, municipalityCode,
                startPoint, road.endMValue,
                Some(username), Some(DateTime.now()),
                None, None, expired = false,
                vvhTimeStamp, None, lane.attributes)
            } else {
              PersistedLane(0, road.linkId, sideCode, laneCode, municipalityCode,
                road.startMValue, road.endMValue,
                Some(username), Some(DateTime.now()),
                None, None, expired = false,
                vvhTimeStamp, None, lane.attributes)
            }

          }
          else if (road.roadPartNumber == laneRoadAddressInfo.endRoadPartNumber) {
            if (road.endAddrMValue < laneRoadAddressInfo.endDistance) {
              None
            } else if (road.endAddrMValue >= laneRoadAddressInfo.endDistance) {
              PersistedLane(0, road.linkId, sideCode, laneCode, municipalityCode,
                road.startMValue, endPoint,
                Some(username), Some(DateTime.now()),
                None, None, expired = false,
                vvhTimeStamp, None, lane.attributes)
            } else {
              PersistedLane(0, road.linkId, sideCode, laneCode, municipalityCode,
                road.startMValue, road.endMValue,
                Some(username), Some(DateTime.now()),
                None, None, expired = false,
                vvhTimeStamp, None, lane.attributes)
            }
          }
        }
      }

      //Create lanes
      allLanesToCreate.flatMap {
        case lane: PersistedLane => laneService.createWithoutTransaction(lane, username).toString
        case _ => None
      }
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
