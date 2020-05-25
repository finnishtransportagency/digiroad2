package fi.liikennevirasto.digiroad2.util

import java.util.Properties

import fi.liikennevirasto.digiroad2.{DigiroadEventBus, DummySerializer, GeometryUtils, Point}
import fi.liikennevirasto.digiroad2.asset.{DateParser, SideCode, State}
import fi.liikennevirasto.digiroad2.client.viite.SearchViiteClient
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.dao.{Queries, RoadAddressTEMP, RoadLinkTempDAO}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.{RoadAddressService, RoadLinkService}
import org.apache.http.impl.client.HttpClientBuilder
import org.joda.time.format.DateTimeFormat
import org.joda.time.{DateTime, Instant}

import scala.util.Try

case class RoadAddressTEMPwithPoint(firstP: Point, lastP: Point, roadAddress: RoadAddressTEMP)

trait ResolvingFrozenRoadLinks {
  lazy val dr2properties: Properties = {
    val props = new Properties()
    props.load(getClass.getResourceAsStream("/digiroad2.properties"))
    props
  }

  lazy val viiteClient: SearchViiteClient = {
    new SearchViiteClient(dr2properties.getProperty("digiroad2.viiteRestApiEndPoint"), HttpClientBuilder.create().build())
  }

  lazy val roadAddressService: RoadAddressService  = {
    new RoadAddressService(viiteClient)
  }

  lazy val geometryVKMTransform: VKMGeometryTransform = {
    new VKMGeometryTransform()
  }

  lazy val eventbus: DigiroadEventBus = {
    new DigiroadEventBus
  }

  lazy val vvhClient: VVHClient = {
    new VVHClient(dr2properties.getProperty("digiroad2.VVHRestApiEndPoint"))
  }

  lazy val roadLinkService: RoadLinkService = {
    new RoadLinkService(vvhClient, eventbus, new DummySerializer)
  }

  lazy val roadLinkTempDao : RoadLinkTempDAO = {
    new RoadLinkTempDAO
  }

  def getSideCode(adjacentOrder: Option[RoadAddressTEMPwithPoint], adjacentReverse: Option[RoadAddressTEMPwithPoint]): Option[SideCode] = {
    if(adjacentOrder.nonEmpty && adjacentOrder.head.roadAddress.sideCode.nonEmpty) {
      adjacentOrder.head.roadAddress.sideCode
    } else if(adjacentReverse.nonEmpty && adjacentReverse.head.roadAddress.sideCode.nonEmpty) {
      Some(SideCode.switch(adjacentReverse.head.roadAddress.sideCode.head))
    } else
      None
  }

  def getTrackAndSideCodeFirst(frozenAddress: RoadAddressTEMPwithPoint, mappedAddresses: Seq[RoadAddressTEMPwithPoint], frozenAddresses: Seq[RoadAddressTEMPwithPoint]) : Seq[RoadAddressTEMPwithPoint] = {
    val adjacentOrder = mappedAddresses.filter { address =>
      GeometryUtils.areAdjacent(frozenAddress.firstP, address.lastP)
    }

    val adjacentReverse = mappedAddresses.filter { address =>
      GeometryUtils.areAdjacent(frozenAddress.firstP, address.firstP)
    }

    val frozenAdjacents = frozenAddresses.filter { frozen =>
      GeometryUtils.areAdjacent(frozenAddress.firstP, frozen.lastP) || GeometryUtils.areAdjacent(frozenAddress.firstP, frozen.firstP)
    }
    val adjacents = adjacentOrder ++ adjacentReverse

    val trackAndSideInfo = (adjacents.size, frozenAdjacents.size) match {
      case (2, 0) =>
        val track: Option[Track] =
          if (adjacents.exists(_.roadAddress.track == Track.RightSide) && adjacents.exists(_.roadAddress.track == Track.LeftSide))
            Some(Track.Combined)
          else if (adjacents.exists(_.roadAddress.track == Track.Combined) && adjacents.exists(_.roadAddress.track == Track.LeftSide))
            Some(Track.RightSide)
          else
            Some(Track.LeftSide)

        (track, getSideCode(adjacentOrder.headOption, adjacentReverse.headOption))

      case (1, 0) =>
        val track = Some(adjacents.head.roadAddress.track)

        (track , getSideCode(adjacentOrder.headOption, adjacentReverse.headOption))

      case _ =>
        (None, None)
    }

    trackAndSideInfo match {
      case (Some(track), Some(sideCode)) =>
        Seq(frozenAddress.copy(roadAddress = frozenAddress.roadAddress.copy(track = track, sideCode = Some(sideCode))))

      case (_, Some(sideCode)) =>
        Seq(frozenAddress.copy(roadAddress = frozenAddress.roadAddress.copy(sideCode = Some(sideCode))))

      case _ => Seq()
    }
  }

  def getTrackAndSideCodeLast(frozenAddress: RoadAddressTEMPwithPoint, mappedAddresses: Seq[RoadAddressTEMPwithPoint],
                              frozenAddresses: Seq[RoadAddressTEMPwithPoint]) : Seq[RoadAddressTEMPwithPoint] = {

    val adjacentOrder = mappedAddresses.filter { address =>
      GeometryUtils.areAdjacent(frozenAddress.lastP, address.firstP)
    }

    val adjacentReverse = mappedAddresses.filter { address =>
      GeometryUtils.areAdjacent(frozenAddress.lastP, address.lastP)
    }

    val frozenAdjacents = frozenAddresses.filter { frozen =>
      GeometryUtils.areAdjacent(frozenAddress.lastP, frozen.firstP) || GeometryUtils.areAdjacent(frozenAddress.lastP, frozen.lastP)
    }

    val adjacents = adjacentOrder ++ adjacentReverse

    val trackAndSideInfo = (adjacents.size, frozenAdjacents.size) match {
      case (2, 1) =>
        val track =  if (adjacents.exists(_.roadAddress.track == Track.RightSide) && adjacents.exists(_.roadAddress.track == Track.LeftSide))
          Some(Track.Combined)
        else if (adjacents.exists(_.roadAddress.track == Track.Combined) && adjacents.exists(_.roadAddress.track == Track.LeftSide))
          Some(Track.RightSide)
        else
          Some(Track.LeftSide)

        (track, getSideCode(adjacentOrder.headOption, adjacentReverse.headOption))

      case (1, 1) =>
        val track = Some(adjacents.head.roadAddress.track)

        (track , getSideCode(adjacentOrder.headOption, adjacentReverse.headOption))
      case _ =>
        (None, None)
    }

    trackAndSideInfo match {
      case (Some(track), Some(sideCode)) =>
        Seq(frozenAddress.copy(roadAddress = frozenAddress.roadAddress.copy(track = track, sideCode = Some(sideCode))))

      case (_, Some(sideCode)) =>
        Seq(frozenAddress.copy(roadAddress = frozenAddress.roadAddress.copy(sideCode = Some(sideCode))))

      case _ => Seq()
    }
  }

  def recalculateAddress(frozen: RoadLink): Seq[RoadAddressTEMPwithPoint] = {
    println(s"Recalculate Address on linkId ${frozen.linkId}")
    frozen.geometry.zip(frozen.geometry.tail).foldLeft(Seq.empty[RoadAddressTEMPwithPoint]) { case (result, (p1, p2)) =>
      val roadNumber = Try(frozen.roadNumber.map(_.toInt).head).toOption
      val roadPartNumber = Try(frozen.roadPartNumber.map(_.toInt).head).toOption
      val address = geometryVKMTransform.coordsToAddresses(Seq(p1, p2), roadNumber, roadPartNumber, includePedestrian = Some(true))

      if (result.isEmpty) {
        val orderedAddress = address.sortBy(_.addrM)
        Seq(RoadAddressTEMPwithPoint(p1, p2, RoadAddressTEMP(frozen.linkId, orderedAddress.head.road, orderedAddress.head.roadPart, Track.Unknown,
          orderedAddress.head.addrM, orderedAddress.last.addrM, 0, GeometryUtils.geometryLength(Seq(p1, p2)), Seq(p1, p2), municipalityCode = Some(frozen.municipalityCode))))
      } else {
        val addressHead = address.head

        if (result.exists(x => x.roadAddress.road == addressHead.road && x.roadAddress.roadPart == addressHead.roadPart)) {
          val partOfEndValue = GeometryUtils.geometryLength(Seq(p1, p2))
          val resultToChange = result.filter(x => x.roadAddress.road == addressHead.road && x.roadAddress.roadPart == addressHead.roadPart).head
          result.filterNot(x => x.roadAddress.road == addressHead.road && x.roadAddress.roadPart == addressHead.roadPart) :+
            resultToChange.copy(roadAddress = resultToChange.roadAddress.copy(endAddressM = address.last.addrM, endMValue = partOfEndValue + resultToChange.roadAddress.endMValue, geom = Seq(p1, p2) ++ resultToChange.roadAddress.geom))
        } else {
          val startValue = result.maxBy(_.roadAddress.endMValue).roadAddress.endMValue
          result :+ RoadAddressTEMPwithPoint(p1, p2, RoadAddressTEMP(frozen.linkId, addressHead.road, addressHead.roadPart, Track.Unknown, addressHead.addrM, address.last.addrM,
            startValue, startValue + GeometryUtils.geometryLength(Seq(p1, p2)), Seq(p1, p2), municipalityCode = Some(frozen.municipalityCode)))
        }
      }
    }
  }

  def cleaningProcess(missingRoadLinks: Seq[RoadLink], vkmRoadAddress: Seq[RoadAddressTEMPwithPoint]): Seq[RoadAddressTEMPwithPoint] = {
    println(s"Try to solve ${missingRoadLinks.size}")

    val adjRoadLinks = missingRoadLinks.map { roadLink =>
      val (first, last) = GeometryUtils.geometryEndpoints(roadLink.geometry)
      ((first, last, roadLink)) -> roadLinkService.getAdjacent(roadLink.linkId, false)
    }.toMap

    val allRoadAddress = roadAddressService.getAllByLinkIds(adjRoadLinks.flatMap(_._2.map(_.linkId)).toSeq.distinct)

    val mappedAddresses = allRoadAddress.flatMap { address =>
      adjRoadLinks.flatMap(_._2).find(_.linkId == address.linkId).map { roadLink =>
        val (first, last) = GeometryUtils.geometryEndpoints(roadLink.geometry)
        RoadAddressTEMPwithPoint(first, last, RoadAddressTEMP(address.linkId, address.roadNumber,
          address.roadPartNumber, address.track, address.startAddrMValue, address.endAddrMValue,
          address.startMValue, address.endMValue, address.geom, Some(address.sideCode)))
      }
    } ++ vkmRoadAddress

    adjRoadLinks.map { adjRoadLink =>
      cleaning(mappedAddresses, adjRoadLink, adjRoadLinks.keys.toSeq.filterNot(_._3.linkId == adjRoadLink._1._3.linkId))
    }
  }.flatten.toSeq

  def cleaning(mappedAddresses : Seq[RoadAddressTEMPwithPoint], adjRoadLink: (((Point, Point, RoadLink), Seq[RoadLink])), missingRoadLinks: Seq[(Point, Point, RoadLink)]) = {
    val ((first, last, missing), roadLinks) = adjRoadLink


      val adjacentFirst = mappedAddresses.filter { address =>
        Try(missing.roadNumber.map(_.toInt).head).toOption.contains(address.roadAddress.road) && (
          GeometryUtils.areAdjacent(first, address.lastP) || GeometryUtils.areAdjacent(first, address.firstP))
      }

      val adjacentLast = mappedAddresses.filter { address =>
        Try(missing.roadNumber.map(_.toInt).head).toOption.contains(address.roadAddress.road) && (
          GeometryUtils.areAdjacent(last, address.lastP) || GeometryUtils.areAdjacent(last, address.firstP))
      }

      val existsMissing = missingRoadLinks.exists{ case (firstP, lastP, _)  =>
        GeometryUtils.areAdjacent(first, firstP) || GeometryUtils.areAdjacent(first, lastP) ||
          GeometryUtils.areAdjacent(last, firstP) || GeometryUtils.areAdjacent(last, lastP)
      }

      val frozenAddress = if(!existsMissing) {
        if(adjacentFirst.nonEmpty && adjacentLast.nonEmpty &&
          adjacentFirst.head.roadAddress.road == adjacentLast.head.roadAddress.road &&
          adjacentFirst.head.roadAddress.roadPart == adjacentLast.head.roadAddress.roadPart) {

          val address = adjacentFirst.head.roadAddress
          Some(RoadAddressTEMPwithPoint(first, last, RoadAddressTEMP(missing.linkId, address.road,
            address.roadPart, address.track, address.startAddressM, address.endAddressM,
            address.startMValue, address.endMValue, missing.geometry, None)))
        } else
          None
      } else
        None

      recalculateTrackAndSideCode(mappedAddresses, frozenAddress.toSeq, Seq())

  }
//
//  def cleaning(mappedAddresses : Seq[RoadAddressTEMPwithPoint], adjRoadLink: ((Point, Point, RoadLink), Seq[RoadLink]), result: Seq[RoadAddressTEMPwithPoint]) : Seq[RoadAddressTEMPwithPoint] = {
//    val newResult = cleanner(mappedAddresses , adjRoadLink)
//
//    if(newResult.nonEmpty) {
//      cleaning(mappedAddresses ++ newResult, adjRoadLink.filterNot { case ((_, _, frozen), _) => newResult.map(_.roadAddress.linkId).contains(frozen.linkId)}, result ++ newResult)
//    } else
//      result
//  }

  def processing(municipality: Int) : (Seq[RoadAddressTEMPwithPoint], Seq[RoadLink]) = {
    println(s"Working on municipality : $municipality")

    val tempRoadAddress = roadLinkTempDao.getByMunicipality(municipality)

    val roadLinks = roadLinkService.getRoadLinksFromVVHByMunicipality(municipality, false).filter(_.administrativeClass == State)

    val allRoadAddress = roadAddressService.getAllByLinkIds(roadLinks.map(_.linkId))
    val missingRoadLinks = roadLinks.filterNot(road => allRoadAddress.map(_.linkId).contains(road.linkId))

    val frozenRoadLinks = missingRoadLinks.filter { road =>
      val matchedAddress = tempRoadAddress.find(_.linkId == road.linkId)
      val vvhtimestamp = road.attributes.getOrElse("LAST_EDITED_DATE", road.attributes.getOrElse("CREATED_DATE", BigInt(0))).asInstanceOf[BigInt].longValue()

      val formatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss.SSSSSS")

      matchedAddress.isEmpty || (vvhtimestamp > DateTime.parse(matchedAddress.get.createdDate.head, formatter).getMillis)
    }

    val addressToDelete = tempRoadAddress.map(_.linkId).diff(missingRoadLinks.map(_.linkId)) ++ frozenRoadLinks.map(_.linkId)
    roadLinkTempDao.deleteInfoByLinkIds(addressToDelete.toSet)

    val groupedFrozenRoadLinks = frozenRoadLinks.groupBy(_.roadNameIdentifier.getOrElse(""))
    val groupedRoadLinks = roadLinks.groupBy(_.roadNameIdentifier.getOrElse(""))

    val newAddress = groupedFrozenRoadLinks.keys.flatMap { key =>
      val relevantLinkIds = groupedRoadLinks.getOrElse(key, Seq())

      val mappedAddresses = allRoadAddress.flatMap { address =>
        relevantLinkIds.find(x => x.linkId == address.linkId).map { roadLink =>
          val (first, last) = GeometryUtils.geometryEndpoints(roadLink.geometry)
          val roadAddressTemp = RoadAddressTEMP(address.linkId, address.roadNumber,
            address.roadPartNumber, address.track, address.startAddrMValue, address.endAddrMValue, address.startMValue, address.endMValue, roadLink.geometry, Some(address.sideCode))

          RoadAddressTEMPwithPoint(first, last, roadAddressTemp)
        }
      }

      val frozenAddresses = groupedFrozenRoadLinks(key).flatMap { frozen =>
        val (first, last) = GeometryUtils.geometryEndpoints(frozen.geometry)

        try {
          val roadNumber = Try(frozen.roadNumber.map(_.toInt).head).toOption
          val roadPartNumber = Try(frozen.roadPartNumber.map(_.toInt).head).toOption

          val address = geometryVKMTransform.coordsToAddresses(Seq(first, last), roadNumber, roadPartNumber, includePedestrian = Some(true))
          if (address.isEmpty || (address.nonEmpty && address.size != 2)) {
            println("problems in wonderland")
            Seq()
          } else {
            val grouped = address.groupBy(addr => (addr.road, addr.roadPart))
            if (grouped.keys.size > 1) {
              recalculateAddress(frozen).foreach { recalc =>
                println(s" more than one road -> linkId: ${recalc.roadAddress.linkId} road ${recalc.roadAddress.roadPart} " +
                  s"roadPart ${recalc.roadAddress.roadPart} track ${recalc.roadAddress.track}  etays ${recalc.roadAddress.startAddressM} " +
                  s"let ${recalc.roadAddress.endAddressM} start ${recalc.roadAddress.startMValue}  end let ${recalc.roadAddress.endMValue} ")
              }
              None
            } else {
              val orderedAddress = address.sortBy(_.addrM)
              val track = if (orderedAddress.head.track == orderedAddress.last.track) orderedAddress.head.track else Track.Unknown
              Some(RoadAddressTEMPwithPoint(first, last, RoadAddressTEMP(frozen.linkId, orderedAddress.head.road,
                orderedAddress.head.roadPart, track, orderedAddress.head.addrM, orderedAddress.last.addrM,
                0, GeometryUtils.geometryLength(frozen.geometry), frozen.geometry, municipalityCode = Some(frozen.municipalityCode))))
            }
          }
        } catch {
          case ex: Exception =>
            println(s"Exception in VKM for linkId ${frozen.linkId} exception $ex")
            None
        }
      }

      recalculateTrackAndSideCode(mappedAddresses, frozenAddresses, Seq())

    }.toSeq


    (newAddress, frozenRoadLinks)
  }

  def recalculateTrackAndSideCode(mappedAddresses: Seq[RoadAddressTEMPwithPoint], frozenAddresses: Seq[RoadAddressTEMPwithPoint], result: Seq[RoadAddressTEMPwithPoint]): Seq[RoadAddressTEMPwithPoint] = {

    val newResult = calculateTrackAndSideCode(mappedAddresses, frozenAddresses).filterNot(x => x.roadAddress.track == Track.Unknown || x.roadAddress.sideCode.isEmpty)

    if(newResult.isEmpty) {
      result
    } else {
      val newResultLinkIds = newResult.map(_.roadAddress.linkId)
      recalculateTrackAndSideCode(newResult ++ mappedAddresses, frozenAddresses.filterNot(x => newResultLinkIds.contains(x.roadAddress.linkId)), result ++ newResult)
    }
  }

  def calculateTrackAndSideCode(mappedAddresses: Seq[RoadAddressTEMPwithPoint], frozenAddresses: Seq[RoadAddressTEMPwithPoint]): Seq[RoadAddressTEMPwithPoint] = {

    frozenAddresses.flatMap {frozenAddress =>
      val missingFrozen = frozenAddresses.filterNot(x => x.roadAddress.linkId == frozenAddress.roadAddress.linkId)

      val frozenAddrFirst = getTrackAndSideCodeFirst(frozenAddress, mappedAddresses, missingFrozen)

      if (frozenAddrFirst.nonEmpty) {
        frozenAddrFirst
      } else
        getTrackAndSideCodeLast(frozenAddress, mappedAddresses, frozenAddresses)
    }
  }


  def process() {
    println("\nRefreshing information on municipality verification")
    println(DateTime.now())

    //Get All Municipalities
    val municipalities: Seq[Int] = OracleDatabase.withDynSession {
      Queries.getMunicipalities
    }

    val result = OracleDatabase.withDynTransaction {
      municipalities.map { municipality =>


        val (toCreate, missing) = processing(municipality)

        toCreate.map(_.roadAddress).foreach { frozen =>
          roadLinkTempDao.insertInfo(frozen, "batch_process_temp_road_address")
          //        println(s"linkId: ${frozen.linkId} road ${frozen.roadPart} roadPart ${frozen.roadPart} track ${frozen.track}  etays ${frozen.startAddressM} let ${frozen.endAddressM} ")
        }
        (toCreate, missing)
      }
    }

    OracleDatabase.withDynTransaction {
      val cleanningResult = cleaningProcess(result.flatMap(_._2), result.flatMap(_._1))

      (cleanningResult.map(_.roadAddress) ++ result.flatMap(_._1).map(_.roadAddress)).foreach { frozen =>
        roadLinkTempDao.insertInfo(frozen, "batch_process_temp_road_address")
        //        println(s"linkId: ${frozen.linkId} road ${frozen.roadPart} roadPart ${frozen.roadPart} track ${frozen.track}  etays ${frozen.startAddressM} let ${frozen.endAddressM} ")
      }
    }

    println("\n")
    println("Complete at time: ")
    println(DateTime.now())
    println("\n")
  }
}

object ResolvingFrozenRoadLinks extends  ResolvingFrozenRoadLinks
