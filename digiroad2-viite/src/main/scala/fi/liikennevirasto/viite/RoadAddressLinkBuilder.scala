package fi.liikennevirasto.viite

import fi.liikennevirasto.digiroad2.RoadLinkType.{ComplementaryRoadLinkType, FloatingRoadLinkType, NormalRoadLinkType, UnknownRoadLinkType}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point, VVHHistoryRoadLink}
import fi.liikennevirasto.viite.RoadType._
import fi.liikennevirasto.viite.dao._
import fi.liikennevirasto.viite.model.{Anomaly, ProjectAddressLink, RoadAddressLink}
import fi.liikennevirasto.viite.process.InvalidAddressDataException
import org.joda.time.format.DateTimeFormat
import org.joda.time.{DateTime, DateTimeZone}

object RoadAddressLinkBuilder {
  val RoadNumber = "ROADNUMBER"
  val RoadPartNumber = "ROADPARTNUMBER"
  val ComplementarySubType = 3
  val formatter = DateTimeFormat.forPattern("dd.MM.yyyy")
  /*Temporary restriction from PO: Filler limit on modifications
                                            (LRM adjustments) is limited to 1 meter. If there is a need to fill /
                                            cut more than that then nothing is done to the road address LRM data.
                                            */
  lazy val municipalityMapping = OracleDatabase.withDynSession {
    MunicipalityDAO.getMunicipalityMapping
  }
  lazy val municipalityRoadMaintainerMapping = OracleDatabase.withDynSession {
    MunicipalityDAO.getMunicipalityRoadMaintainers
  }

  def getRoadType(administrativeClass: AdministrativeClass, linkType: LinkType): RoadType = {
    (administrativeClass, linkType) match {
      case (State, CableFerry) => FerryRoad
      case (State, _) => PublicRoad
      case (Municipality, _) => MunicipalityStreetRoad
      case (Private, _) => PrivateRoadType
      case (_, _) => UnknownOwnerRoad
    }
  }

  def fuseRoadAddress(roadAddresses: Seq[RoadAddress]): Seq[RoadAddress] = {
    if (roadAddresses.size == 1) {
      roadAddresses
    } else {
      val groupedRoadAddresses = roadAddresses.groupBy(record =>
        (record.roadNumber, record.roadPartNumber, record.track.value, record.startDate, record.endDate, record.linkId))

      groupedRoadAddresses.flatMap { case (_, record) =>
        RoadAddressLinkBuilder.fuseRoadAddressInGroup(record.sortBy(_.startMValue))
      }.toSeq
    }
  }

  def build(roadLink: RoadLink, roadAddress: RoadAddress, floating: Boolean = false, newGeometry: Option[Seq[Point]] = None) = {
    val roadLinkType = (floating, roadLink.linkSource) match {
      case (true, _) => FloatingRoadLinkType
      case (false, LinkGeomSource.ComplimentaryLinkInterface) => ComplementaryRoadLinkType
      case (false, _) => NormalRoadLinkType
    }
    val geom = GeometryUtils.truncateGeometry3D(roadLink.geometry, roadAddress.startMValue, roadAddress.endMValue)
    val length = GeometryUtils.geometryLength(geom)
    RoadAddressLink(roadAddress.id, roadLink.linkId, geom,
      length, roadLink.administrativeClass, roadLink.linkType, roadLinkType, roadLink.constructionType, roadLink.linkSource, getRoadType(roadLink.administrativeClass, roadLink.linkType), extractModifiedAtVVH(roadLink.attributes), Some("vvh_modified"),
      roadLink.attributes, roadAddress.roadNumber, roadAddress.roadPartNumber, roadAddress.track.value, municipalityRoadMaintainerMapping.getOrElse(roadLink.municipalityCode, -1), roadAddress.discontinuity.value,
      roadAddress.startAddrMValue, roadAddress.endAddrMValue, roadAddress.startDate.map(formatter.print).getOrElse(""), roadAddress.endDate.map(formatter.print).getOrElse(""), roadAddress.startMValue, roadAddress.endMValue,
      roadAddress.sideCode,
      roadAddress.calibrationPoints._1,
      roadAddress.calibrationPoints._2,Anomaly.None, roadAddress.lrmPositionId)

  }

  def build(roadLink: RoadLink, projectLink: ProjectLink): ProjectAddressLink = {
    val roadLinkType = if (roadLink.linkSource==LinkGeomSource.ComplimentaryLinkInterface)
      ComplementaryRoadLinkType
    else
      NormalRoadLinkType

    val geom = roadLink.geometry
    val length = GeometryUtils.geometryLength(geom)
    ProjectAddressLink(projectLink.id, roadLink.linkId, geom,
      length, roadLink.administrativeClass, roadLink.linkType, roadLinkType, roadLink.constructionType, roadLink.linkSource, getRoadType(roadLink.administrativeClass, roadLink.linkType), extractModifiedAtVVH(roadLink.attributes), Some("vvh_modified"),
      roadLink.attributes, projectLink.roadNumber, projectLink.roadPartNumber, projectLink.track.value, municipalityRoadMaintainerMapping.getOrElse(roadLink.municipalityCode, -1), projectLink.discontinuity.value,
      projectLink.startAddrMValue, projectLink.endAddrMValue, projectLink.startMValue, projectLink.endMValue,
      projectLink.sideCode,
      projectLink.calibrationPoints._1,
      projectLink.calibrationPoints._2,Anomaly.None, projectLink.lrmPositionId, projectLink.status)

  }

  def build(roadLink: RoadLink, missingAddress: MissingRoadAddress) = {
    val geom = GeometryUtils.truncateGeometry3D(roadLink.geometry, missingAddress.startMValue.getOrElse(0.0), roadLink.length)
    val length = GeometryUtils.geometryLength(geom)
    val roadLinkRoadNumber = roadLink.attributes.get(RoadNumber).map(toIntNumber).getOrElse(0)
    val roadLinkRoadPartNumber = roadLink.attributes.get(RoadPartNumber).map(toIntNumber).getOrElse(0)
    RoadAddressLink(0, roadLink.linkId, geom,
      length, roadLink.administrativeClass, roadLink.linkType, UnknownRoadLinkType, roadLink.constructionType, LinkGeomSource.Unknown, getRoadType(roadLink.administrativeClass, roadLink.linkType),
      extractModifiedAtVVH(roadLink.attributes), Some("vvh_modified"),
      roadLink.attributes, missingAddress.roadNumber.getOrElse(roadLinkRoadNumber),
      missingAddress.roadPartNumber.getOrElse(roadLinkRoadPartNumber), Track.Unknown.value, municipalityRoadMaintainerMapping.getOrElse(roadLink.municipalityCode, -1), Discontinuity.Continuous.value,
      0, 0, "", "", 0.0, length, SideCode.Unknown, None, None, missingAddress.anomaly, 0)
  }

  def build(historyRoadLink: VVHHistoryRoadLink, roadAddress: RoadAddress): RoadAddressLink = {

    val roadLinkType = FloatingRoadLinkType

    val geom = GeometryUtils.truncateGeometry3D(historyRoadLink.geometry, roadAddress.startMValue, roadAddress.endMValue)
    val length = GeometryUtils.geometryLength(geom)
    RoadAddressLink(roadAddress.id, historyRoadLink.linkId, geom,
      length, historyRoadLink.administrativeClass, UnknownLinkType, roadLinkType, ConstructionType.UnknownConstructionType, LinkGeomSource.HistoryLinkInterface, getRoadType(historyRoadLink.administrativeClass, UnknownLinkType), extractModifiedAtVVH(historyRoadLink.attributes), Some("vvh_modified"),
      historyRoadLink.attributes, roadAddress.roadNumber, roadAddress.roadPartNumber, roadAddress.track.value, municipalityRoadMaintainerMapping.getOrElse(historyRoadLink.municipalityCode, -1), roadAddress.discontinuity.value,
      roadAddress.startAddrMValue, roadAddress.endAddrMValue, roadAddress.startDate.map(formatter.print).getOrElse(""), roadAddress.endDate.map(formatter.print).getOrElse(""), roadAddress.startMValue, roadAddress.endMValue,
      roadAddress.sideCode,
      roadAddress.calibrationPoints._1,
      roadAddress.calibrationPoints._2, Anomaly.None, roadAddress.lrmPositionId)
  }

  def capToGeometry(geomLength: Double, sourceSegments: Seq[RoadAddressLink]): Seq[RoadAddressLink] = {
    val (overflowingSegments, passThroughSegments) = sourceSegments.partition(x => (x.endMValue - MaxAllowedMValueError > geomLength))
    val cappedSegments = overflowingSegments.map { s =>
      (s.copy(endMValue = geomLength))
    }
    (passThroughSegments ++ cappedSegments)
  }

  def extendToGeometry(geomLength: Double, sourceSegments: Seq[RoadAddressLink]): Seq[RoadAddressLink] = {
    if (sourceSegments.isEmpty)
      return sourceSegments
    val sorted = sourceSegments.sortBy(_.endMValue)(Ordering[Double].reverse)
    val lastSegment = sorted.head
    val restSegments = sorted.tail
    val adjustments = (lastSegment.endMValue < geomLength - MaxAllowedMValueError) match {
      case true => (restSegments ++ Seq(lastSegment.copy(endMValue = geomLength)))
      case _ => sourceSegments
    }
    adjustments
  }

  def dropShort(geomLength: Double, sourceSegments: Seq[RoadAddressLink]): Seq[RoadAddressLink] = {
    if (sourceSegments.size < 2)
      return sourceSegments
    val passThroughSegments = sourceSegments.partition(s => s.length >= MinAllowedRoadAddressLength)._1
    passThroughSegments
  }

  def dropSegmentsOutsideGeometry(geomLength: Double, sourceSegments: Seq[RoadAddressLink]): Seq[RoadAddressLink] = {
    val passThroughSegments = sourceSegments.partition(x => x.startMValue + Epsilon <= geomLength)._1
    passThroughSegments
  }

  def adjustRoadAddressTopology(expectedTargetsNumber: Int, startCp: Option[CalibrationPoint], endCp: Option[CalibrationPoint],
                                maxEndMValue: Double, minStartMAddress: Long, maxEndMAddress: Long, source: RoadAddressLink,
                                currentTarget: RoadAddressLink, roadAddresses: Seq[RoadAddressLink], username: String): Seq[RoadAddressLink] = {
    val tempId = fi.liikennevirasto.viite.NewRoadAddress
    val sorted = roadAddresses.sortBy(_.endAddressM)(Ordering[Long].reverse)
    val previousTarget = sorted.head
    val startAddressM = if (roadAddresses.exists(_.id != 0))
      previousTarget.endAddressM
    else
      minStartMAddress

    val endAddressM = if (roadAddresses.count(_.id != 0) == expectedTargetsNumber-1)
      maxEndMAddress
    else
      startAddressM + GeometryUtils.geometryLength(currentTarget.geometry).toLong


    val calibrationPointS = if (roadAddresses.count(_.id != 0) == 0)
      startCp.map(_.copy(linkId = currentTarget.linkId, segmentMValue = 0.0))
    else
      None

    val calibrationPointE = if (roadAddresses.count(_.id != 0) == expectedTargetsNumber - 1)
      endCp.map(_.copy(linkId = currentTarget.linkId, segmentMValue = currentTarget.length))
    else
      None


    val newRoadAddress = Seq(RoadAddressLink(tempId, currentTarget.linkId, currentTarget.geometry,
      GeometryUtils.geometryLength(currentTarget.geometry), source.administrativeClass, source.linkType, NormalRoadLinkType,
      source.constructionType, source.roadLinkSource, source.roadType, source.modifiedAt, Option(username),
      currentTarget.attributes, source.roadNumber, source.roadPartNumber, source.trackCode, source.elyCode, source.discontinuity,
      startAddressM, endAddressM, source.startDate, source.endDate, currentTarget.startMValue,
      GeometryUtils.geometryLength(currentTarget.geometry), source.sideCode, calibrationPointS, calibrationPointE, Anomaly.None, 0))
    val newIds = newRoadAddress.map(_.linkId)
    roadAddresses.filterNot(ra => newIds.contains(ra.linkId))++newRoadAddress
  }

  private def toIntNumber(value: Any) = {
    try {
      value.asInstanceOf[String].toInt
    } catch {
      case e: Exception => 0
    }
  }

  private def extractModifiedAtVVH(attributes: Map[String, Any]): Option[String] = {
    def toLong(anyValue: Option[Any]) = {
      anyValue.map(_.asInstanceOf[BigInt].toLong)
    }
    def compareDateMillisOptions(a: Option[Long], b: Option[Long]): Option[Long] = {
      (a, b) match {
        case (Some(firstModifiedAt), Some(secondModifiedAt)) =>
          if (firstModifiedAt > secondModifiedAt)
            Some(firstModifiedAt)
          else
            Some(secondModifiedAt)
        case (Some(firstModifiedAt), None) => Some(firstModifiedAt)
        case (None, Some(secondModifiedAt)) => Some(secondModifiedAt)
        case (None, None) => None
      }
    }
    val toIso8601 = DateTimeFormat.forPattern("dd.MM.yyyy HH:mm:ss")
    val createdDate = toLong(attributes.get("CREATED_DATE"))
    val lastEditedDate = toLong(attributes.get("LAST_EDITED_DATE"))
    val geometryEditedDate = toLong(attributes.get("GEOMETRY_EDITED_DATE"))
    val endDate = toLong(attributes.get("END_DATE"))
    val latestDate = compareDateMillisOptions(lastEditedDate, geometryEditedDate)
    val withHistoryLatestDate = compareDateMillisOptions(latestDate, endDate)
    val timezone = DateTimeZone.forOffsetHours(0)
    val latestDateString = withHistoryLatestDate.orElse(createdDate).map(modifiedTime => new DateTime(modifiedTime, timezone)).map(toIso8601.print(_))
    latestDateString
  }

  /**
    * Fuse recursively
    *
    * @param unprocessed road addresses ordered by the startMValue
    * @param ready recursive value
    * @return road addresses fused in reverse order
    */
  private def fuseRoadAddressInGroup(unprocessed: Seq[RoadAddress], ready: Seq[RoadAddress] = Nil): Seq[RoadAddress] = {
    if (ready.isEmpty)
      fuseRoadAddressInGroup(unprocessed.tail, Seq(unprocessed.head))
    else if (unprocessed.isEmpty)
      ready
    else
    {
      fuseRoadAddressInGroup(unprocessed.tail, fuseTwo(unprocessed.head, ready.head) ++ ready.tail)
    }
  }

  /**
    * Fusing Two RoadAddresses in One
    *
    * @param nextSegment
    * @param previousSegment
    * @return A sequence of RoadAddresses, 1 if possible to fuse, 2 if they are unfusable
    */
  private def fuseTwo(nextSegment: RoadAddress, previousSegment: RoadAddress): Seq[RoadAddress] = {

    // Test that at the road addresses lap at least partially or are connected (one extends another)
    def addressConnected(nextSegment: RoadAddress, previousSegment: RoadAddress) = {
      (nextSegment.startAddrMValue == previousSegment.endAddrMValue ||
        previousSegment.startAddrMValue == nextSegment.endAddrMValue) ||
        (nextSegment.startAddrMValue >= previousSegment.startAddrMValue &&
          nextSegment.startAddrMValue <= previousSegment.endAddrMValue) ||
        (previousSegment.startAddrMValue >= nextSegment.startAddrMValue &&
          previousSegment.startAddrMValue <= nextSegment.endAddrMValue)
    }
    val cpNext = nextSegment.calibrationPoints
    val cpPrevious = previousSegment.calibrationPoints
    def getMValues[T](leftMValue: T, rightMValue: T, op: (T, T) => T,
                      getValue: (Option[CalibrationPoint], Option[CalibrationPoint]) => Option[T])={
      /*  Take the value from Calibration Point if available or then use the given operation
          Starting calibration point from previous segment if available or then it's the starting calibration point for
          the next segment. If neither, use the min or max operation given as an argument.
          Similarily for ending calibration points. Cases where the calibration point truly is between segments is
          left unprocessed.
       */
      getValue(cpPrevious._1.orElse(cpNext._1), cpNext._2.orElse(cpPrevious._2)).getOrElse(op(leftMValue,rightMValue))
    }

    val tempId = fi.liikennevirasto.viite.NewRoadAddress

    if(nextSegment.roadNumber     == previousSegment.roadNumber &&
      nextSegment.roadPartNumber  == previousSegment.roadPartNumber &&
      nextSegment.track.value     == previousSegment.track.value &&
      nextSegment.startDate       == previousSegment.startDate &&
      nextSegment.endDate         == previousSegment.endDate &&
      nextSegment.linkId          == previousSegment.linkId &&
      addressConnected(nextSegment, previousSegment) &&
      !(cpNext._1.isDefined && cpPrevious._2.isDefined)) { // Check that the calibration point isn't between these segments


      val startAddrMValue = getMValues[Long](nextSegment.startAddrMValue, previousSegment.startAddrMValue, Math.min, (cpp, _) => cpp.map(_.addressMValue))
      val endAddrMValue = getMValues[Long](nextSegment.endAddrMValue, previousSegment.endAddrMValue, Math.max, (_, cpn) => cpn.map(_.addressMValue))
      val startMValue = getMValues[Double](nextSegment.startMValue, previousSegment.startMValue, Math.min, (_, _) => None)
      val endMValue = getMValues[Double](nextSegment.endMValue, previousSegment.endMValue, Math.max, (_, _) => None)

      val calibrationPoints: (Option[CalibrationPoint], Option[CalibrationPoint]) = {
        val left = Seq(cpNext._1, cpPrevious._1).flatten.sortBy(_.segmentMValue).headOption
        val right = Seq(cpNext._2, cpPrevious._2).flatten.sortBy(_.segmentMValue).lastOption
        (left.map(_.copy(segmentMValue = if (nextSegment.sideCode == SideCode.AgainstDigitizing) endMValue else startMValue)),
          right.map(_.copy(segmentMValue = if (nextSegment.sideCode == SideCode.AgainstDigitizing) startMValue else endMValue)))
      }

      if(nextSegment.sideCode.value != previousSegment.sideCode.value)
        throw new InvalidAddressDataException(s"Road Address ${nextSegment.id} and Road Address ${previousSegment.id} cannot have different side codes.")
      val combinedGeometry: Seq[Point] = GeometryUtils.truncateGeometry3D(Seq(previousSegment.geom.head, nextSegment.geom.last), startMValue, endMValue)
      val discontinuity = {
        if(nextSegment.endMValue > previousSegment.endMValue) {
          nextSegment.discontinuity
        } else
          previousSegment.discontinuity
      }

      Seq(RoadAddress(tempId, nextSegment.roadNumber, nextSegment.roadPartNumber,
        nextSegment.track, discontinuity, startAddrMValue,
        endAddrMValue, nextSegment.startDate, nextSegment.endDate, nextSegment.modifiedBy, nextSegment.lrmPositionId, nextSegment.linkId,
        startMValue, endMValue,
        nextSegment.sideCode, nextSegment.adjustedTimestamp, calibrationPoints, false, combinedGeometry, nextSegment.linkGeomSource))

    } else Seq(nextSegment, previousSegment)

  }


}

//TIETYYPPI (1= yleinen tie, 2 = lauttaväylä yleisellä tiellä, 3 = kunnan katuosuus, 4 = yleisen tien työmaa, 5 = yksityistie, 9 = omistaja selvittämättä)
sealed trait RoadType {
  def value: Int
  def displayValue: String
}
object RoadType {
  val values = Set(PublicRoad, FerryRoad, MunicipalityStreetRoad, PublicUnderConstructionRoad, PrivateRoadType, UnknownOwnerRoad)

  def apply(intValue: Int): RoadType = {
    values.find(_.value == intValue).getOrElse(UnknownOwnerRoad)
  }

  case object PublicRoad extends RoadType { def value = 1; def displayValue = "Yleinen tie" }
  case object FerryRoad extends RoadType { def value = 2; def displayValue = "Lauttaväylä yleisellä tiellä" }
  case object MunicipalityStreetRoad extends RoadType { def value = 3; def displayValue = "Kunnan katuosuus" }
  case object PublicUnderConstructionRoad extends RoadType { def value = 4; def displayValue = "Yleisen tien työmaa" }
  case object PrivateRoadType extends RoadType { def value = 5; def displayValue = "Yksityistie" }
  case object UnknownOwnerRoad extends RoadType { def value = 9; def displayValue = "Omistaja selvittämättä" }
  case object Unknown extends RoadType { def value = 99; def displayValue = "Ei määritelty" }
}
