package fi.liikennevirasto.digiroad2.util

import java.util.Properties
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, DummySerializer, GeometryUtils, Point, Track}
import fi.liikennevirasto.digiroad2.asset.{DateParser, SideCode, State}
import fi.liikennevirasto.digiroad2.client.{RoadLinkClient, VKMClient}
import fi.liikennevirasto.digiroad2.client.viite.SearchViiteClient
import fi.liikennevirasto.digiroad2.dao.{Queries, RoadAddressTEMP, RoadLinkTempDAO}
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.{RoadAddressService, RoadLinkService}
import org.apache.http.impl.client.HttpClientBuilder
import org.joda.time.format.DateTimeFormat
import org.joda.time.{DateTime, Instant}

import scala.util.Try

case class RoadAddressTEMPwithPoint(firstP: Point, lastP: Point, roadAddress: RoadAddressTEMP)

trait ResolvingFrozenRoadLinks {

  lazy val viiteClient: SearchViiteClient = {
    new SearchViiteClient(Digiroad2Properties.viiteRestApiEndPoint, HttpClientBuilder.create().build())
  }

  lazy val roadAddressService: RoadAddressService = {
    new RoadAddressService(viiteClient)
  }

  lazy val vkmClient: VKMClient = {
    new VKMClient()
  }

  lazy val eventbus: DigiroadEventBus = {
    new DigiroadEventBus
  }

  lazy val roadLinkClient: RoadLinkClient = {
    new RoadLinkClient(Digiroad2Properties.vvhRestApiEndPoint)
  }

  lazy val roadLinkService: RoadLinkService = {
    new RoadLinkService(roadLinkClient, eventbus, new DummySerializer)
  }

  lazy val roadLinkTempDao: RoadLinkTempDAO = {
    new RoadLinkTempDAO
  }

  def getSideCode(adjacentOrder: Option[RoadAddressTEMPwithPoint], adjacentReverse: Option[RoadAddressTEMPwithPoint]): Option[SideCode] = {
    if (adjacentOrder.nonEmpty && adjacentOrder.head.roadAddress.sideCode.nonEmpty) {
      adjacentOrder.head.roadAddress.sideCode
    } else if (adjacentReverse.nonEmpty && adjacentReverse.head.roadAddress.sideCode.nonEmpty) {
      Some(SideCode.switch(adjacentReverse.head.roadAddress.sideCode.head))
    } else
      None
  }

  def getTrackAndSideCodeFirst(frozenAddress: RoadAddressTEMPwithPoint, mappedAddresses: Seq[RoadAddressTEMPwithPoint], frozenAddresses: Seq[RoadAddressTEMPwithPoint]): Seq[RoadAddressTEMPwithPoint] = {
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

        (track, getSideCode(adjacentOrder.headOption, adjacentReverse.headOption))

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
                              frozenAddresses: Seq[RoadAddressTEMPwithPoint]): Seq[RoadAddressTEMPwithPoint] = {

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
        val track = if (adjacents.exists(_.roadAddress.track == Track.RightSide) && adjacents.exists(_.roadAddress.track == Track.LeftSide))
          Some(Track.Combined)
        else if (adjacents.exists(_.roadAddress.track == Track.Combined) && adjacents.exists(_.roadAddress.track == Track.LeftSide))
          Some(Track.RightSide)
        else
          Some(Track.LeftSide)

        (track, getSideCode(adjacentOrder.headOption, adjacentReverse.headOption))

      case (1, 1) =>
        val track = Some(adjacents.head.roadAddress.track)

        (track, getSideCode(adjacentOrder.headOption, adjacentReverse.headOption))
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
      val address = vkmClient.coordsToAddresses(Seq(p1, p2), roadNumber, roadPartNumber, includePedestrian = Some(true))

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

  def cleanner(adjRoadLinks: Map[(Point, Point, RoadLink), Seq[RoadLink]], mappedAddresses: Seq[RoadAddressTEMPwithPoint]): Seq[RoadAddressTEMPwithPoint] = {
    println(s"Try to solve ${adjRoadLinks.keys.size}")

    adjRoadLinks.map { adjRoadLink =>
      val ((first, last, missing), roadLinks) = adjRoadLink

      val adjacentFirstLast = mappedAddresses.filter { address =>
        Try(missing.roadNumber.map(_.toInt).head).toOption.contains(address.roadAddress.road) &&
          Try(missing.roadPartNumber.map(_.toInt).head).toOption.contains(address.roadAddress.roadPart) && (
          GeometryUtils.areAdjacent(first, address.lastP) )
      }

      val adjacentFirstFirst = mappedAddresses.filter { address =>
        Try(missing.roadNumber.map(_.toInt).head).toOption.contains(address.roadAddress.road) &&
          Try(missing.roadPartNumber.map(_.toInt).head).toOption.contains(address.roadAddress.roadPart) && (
          GeometryUtils.areAdjacent(first, address.firstP) )
      }

      val adjacentLastLast = mappedAddresses.filter { address =>
        Try(missing.roadNumber.map(_.toInt).head).toOption.contains(address.roadAddress.road) &&
          Try(missing.roadPartNumber.map(_.toInt).head).toOption.contains(address.roadAddress.roadPart) && (
          GeometryUtils.areAdjacent(last, address.lastP))
      }

      val adjacentLastFirst = mappedAddresses.filter { address =>
        Try(missing.roadNumber.map(_.toInt).head).toOption.contains(address.roadAddress.road) &&
          Try(missing.roadPartNumber.map(_.toInt).head).toOption.contains(address.roadAddress.roadPart) && (
          GeometryUtils.areAdjacent(last, address.firstP))
      }

      val frozenAddress =
        if ((adjacentFirstLast ++ adjacentFirstFirst).nonEmpty && (adjacentLastLast++adjacentLastFirst).nonEmpty) {

          val startAddressM = (adjacentFirstLast.nonEmpty, adjacentFirstFirst.nonEmpty) match {
            case (true, false) => adjacentFirstLast.head.roadAddress.endAddressM
            case _ => adjacentFirstFirst.head.roadAddress.startAddressM
          }

          val endAddressM = (adjacentLastFirst.nonEmpty, adjacentLastLast.nonEmpty) match {
            case (true, false) => adjacentLastFirst.head.roadAddress.startAddressM
            case _ => adjacentLastLast.head.roadAddress.endAddressM
          }

          val address = (adjacentFirstLast ++ adjacentFirstFirst).head.roadAddress
          Some(RoadAddressTEMPwithPoint(first, last, RoadAddressTEMP(missing.linkId, address.road, address.roadPart, Track.Unknown,
            startAddressM, endAddressM, 0, GeometryUtils.geometryLength(missing.geometry), missing.geometry, None, municipalityCode = address.municipalityCode)))
        } else
          None

      recalculateTrackAndSideCode(mappedAddresses, frozenAddress.toSeq, Seq())

    }.toSeq.flatten
  }

  def processing(municipality: Int) : (Seq[RoadAddressTEMPwithPoint], Seq[RoadLink]) = {
    println(s"Working on municipality : $municipality")

    val tempRoadAddress = roadLinkTempDao.getByMunicipality(municipality)

    val roadLinks = roadLinkService.getRoadLinksByMunicipality(municipality, false).filter(_.administrativeClass == State)

    val allRoadAddress = roadAddressService.getAllByLinkIds(roadLinks.map(_.linkId))
    val missingRoadLinks = roadLinks.filterNot(road => allRoadAddress.map(_.linkId).contains(road.linkId))

    val frozenRoadLinks = missingRoadLinks.filter { road =>
      val matchedAddress = tempRoadAddress.find(_.linkId == road.linkId)
      val vvhtimestamp = road.attributes.getOrElse("LAST_EDITED_DATE", road.attributes.getOrElse("CREATED_DATE", BigInt(0))).asInstanceOf[BigInt].longValue()

      val formatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss.SSSSSS")

      matchedAddress.isEmpty || (vvhtimestamp > DateTime.parse(matchedAddress.get.createdDate.head, formatter).getMillis)
    }

    val addressToDelete = tempRoadAddress.map(_.linkId).diff(missingRoadLinks.map(_.linkId)) ++ frozenRoadLinks.map(_.linkId)
    if (addressToDelete.nonEmpty)
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

          val address = vkmClient.coordsToAddresses(Seq(first, last), roadNumber, roadPartNumber, includePedestrian = Some(true))
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


    (newAddress, frozenRoadLinks.filterNot( frozen => newAddress.map(_.roadAddress.linkId).contains(frozen.linkId)) )
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
    val municipalities: Seq[Int] = PostGISDatabase.withDynSession {
      Queries.getMunicipalities
    }

    municipalities.foreach { municipality =>
      PostGISDatabase.withDynTransaction {
        val (toCreate, missing) = processing(municipality)

        val cleanningResult = cleaning(missing, toCreate)

        (cleanningResult ++ toCreate.map(_.roadAddress)).foreach { frozen =>
          roadLinkTempDao.insertInfo(frozen, "batch_process_temp_road_address")
          //        println(s"linkId: ${frozen.linkId} road ${frozen.roadPart} roadPart ${frozen.roadPart} track ${frozen.track}  etays ${frozen.startAddressM} let ${frozen.endAddressM} ")
        }
      }
    }

    println("\n")
    println("Complete at time: ")
    println(DateTime.now())
    println("\n")
  }

  def cleaning(missing: Seq[RoadLink], toCreate: Seq[RoadAddressTEMPwithPoint]) : Seq[RoadAddressTEMP] = {

    val adjRoadLinks = missing.map { roadLink =>
      val (first, last) = GeometryUtils.geometryEndpoints(roadLink.geometry)
      (first, last, roadLink) -> roadLinkService.getAdjacent(roadLink.linkId, false)
    }.toMap

    val allRoadAddress = roadAddressService.getAllByLinkIds(adjRoadLinks.flatMap(_._2.map(_.linkId)).toSeq.distinct)

    val mappedAddresses = allRoadAddress.flatMap { address =>
      adjRoadLinks.flatMap(_._2).find(_.linkId == address.linkId).map { roadLink =>
        val (first, last) = GeometryUtils.geometryEndpoints(roadLink.geometry)
        RoadAddressTEMPwithPoint(first, last, RoadAddressTEMP(address.linkId, address.roadNumber,
          address.roadPartNumber, address.track, address.startAddrMValue, address.endAddrMValue,
          address.startMValue, address.endMValue, roadLink.geometry, Some(address.sideCode), Some(roadLink.municipalityCode)))
      }
    }

    cleanning(adjRoadLinks, mappedAddresses ++ toCreate, Seq())

  }

  def cleanning(missingRoadLinks: Map[(Point, Point, RoadLink), Seq[RoadLink]], roadAddress: Seq[RoadAddressTEMPwithPoint], result: Seq[RoadAddressTEMPwithPoint]) : Seq[RoadAddressTEMP] ={

    val newResult = cleanner(missingRoadLinks, roadAddress)

    if(newResult.nonEmpty)
      cleanning(missingRoadLinks.filterNot(x => newResult.map(_.roadAddress.linkId).contains(x._1._3.linkId)), roadAddress ++ newResult, result ++ newResult)
    else
      result.map(_.roadAddress)

  }
}

object ResolvingFrozenRoadLinks extends  ResolvingFrozenRoadLinks