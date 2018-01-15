package fi.liikennevirasto.viite

import java.sql.SQLIntegrityConstraintViolationException

import fi.liikennevirasto.digiroad2.{DigiroadEventBus, GeometryUtils}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.viite.AddressConsistencyValidator.AddressError.{InconsistentTopology, OverlappingRoadAddresses}
import fi.liikennevirasto.viite.dao._
import org.slf4j.LoggerFactory


class RoadNetworkService {

  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)

  def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)

  val logger = LoggerFactory.getLogger(getClass)

  def checkRoadAddressNetwork(options: RoadCheckOptions): Unit = {

    def checkCalibrationPoints(road1: RoadAddress, road2: RoadAddress): Boolean = {
      road1.track != road2.track && road1.calibrationPoints._1.isEmpty && road1.calibrationPoints._2.isEmpty &&
        road2.calibrationPoints._1.isEmpty && road2.calibrationPoints._2.isEmpty
    }

    def checkOverlapping(road1: RoadAddress, road2: RoadAddress) = {
      road1.endAddrMValue != road2.startAddrMValue && road1.track.value == road2.track.value match {
        case true => RoadNetworkDAO.addRoadNetworkError(road1.id, OverlappingRoadAddresses.value)
        case _ => None
      }
    }

    def checkTopology(road1: RoadAddress, road2: RoadAddress) = {
      !GeometryUtils.areAdjacent(road1.geometry, road2.geometry) &&
        (road1.discontinuity != Discontinuity.MinorDiscontinuity || road1.discontinuity != Discontinuity.Discontinuous ||
          road1.discontinuity != Discontinuity.MinorDiscontinuity || road1.discontinuity != Discontinuity.Discontinuous) &&
        checkCalibrationPoints(road1, road2)
      match {
        case true => RoadNetworkDAO.addRoadNetworkError(road1.id, InconsistentTopology.value)
        case _ => None
      }
    }

    withDynTransaction {
      try {
        ExportLockDAO.insert
        RoadAddressDAO.lockRoadAddressWriting
        RoadNetworkDAO.removeNetworkErrors
        val allRoads = RoadAddressDAO.fetchAllCurrentRoads(options).groupBy(_.roadNumber).flatMap(road => {
          val groupedRoadParts = road._2.groupBy(_.roadPartNumber).toSeq.sortBy(_._1)
          val lastRoadAddress = groupedRoadParts.last._2.sortBy(g => (g.track.value, g.startAddrMValue)).last
          groupedRoadParts.map(roadPart => {
            if (roadPart._2.last.roadPartNumber == lastRoadAddress.roadPartNumber && lastRoadAddress.discontinuity != Discontinuity.EndOfRoad) {
              RoadNetworkDAO.addRoadNetworkError(lastRoadAddress.id, InconsistentTopology.value)
            }
            val sortedRoads = roadPart._2.sortBy(s => (s.track.value, s.startAddrMValue))
            sortedRoads.zip(sortedRoads.tail).foreach(r => {
              checkOverlapping(r._1, r._2)
              checkTopology(r._1, r._2)
            })
            roadPart
          })
        })
        if (!RoadNetworkDAO.hasRoadNetworkErrors) {
          RoadNetworkDAO.expireRoadNetwork
          RoadNetworkDAO.createPublishedRoadNetwork
          allRoads.foreach(r => r._2.foreach(p => RoadNetworkDAO.createPublishedRoadAddress(p.id)))
        }
        ExportLockDAO.delete
      } catch {
        case e: SQLIntegrityConstraintViolationException => logger.info("A road network check is already running")
        case _: Exception => {
            logger.error("Error during road address network check")
          ExportLockDAO.delete
        }
      }
    }
  }
}
case class RoadCheckOptions(roadNumbers: Seq[Long])
