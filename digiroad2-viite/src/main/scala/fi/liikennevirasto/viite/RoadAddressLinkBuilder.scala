package fi.liikennevirasto.viite

import fi.liikennevirasto.digiroad2.RoadLinkType.{apply => _, _}
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource.{Unknown => _, apply => _}
import fi.liikennevirasto.digiroad2.asset.{LinkGeomSource, _}
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.viite.dao._
import fi.liikennevirasto.viite.model.{Anomaly, RoadAddressLink}

object RoadAddressLinkBuilder extends AddressLinkBuilder {
  /*Temporary restriction from PO: Filler limit on modifications
                                            (LRM adjustments) is limited to 1 meter. If there is a need to fill /
                                            cut more than that then nothing is done to the road address LRM data.
                                            */
  def build(roadLink: RoadLink, roadAddress: RoadAddress, floating: Boolean = false, newGeometry: Option[Seq[Point]] = None): RoadAddressLink = {
    val roadLinkType = (floating, roadLink.linkSource) match {
      case (true, _) => FloatingRoadLinkType
      case (false, LinkGeomSource.ComplimentaryLinkInterface) => ComplementaryRoadLinkType
      case (false, _) => NormalRoadLinkType
    }
    val geom = GeometryUtils.truncateGeometry3D(roadLink.geometry, roadAddress.startMValue, roadAddress.endMValue)
    val length = GeometryUtils.geometryLength(geom)
    val roadName = roadLink.attributes.getOrElse(FinnishRoadName, roadLink.attributes.getOrElse(SwedishRoadName, "none")).toString
    val municipalityCode = roadLink.attributes.getOrElse("MUNICIPALITYCODE",0).asInstanceOf[Number].intValue()
    val roadType = roadAddress.roadType match {
      case RoadType.Unknown => getRoadType(roadLink.administrativeClass, roadLink.linkType)
      case _ => roadAddress.roadType
    }
    RoadAddressLink(roadAddress.id, roadLink.linkId, geom,
      length, roadLink.administrativeClass, roadLink.linkType, roadLinkType, roadLink.constructionType, roadLink.linkSource, roadType, roadName, municipalityCode, extractModifiedAtVVH(roadLink.attributes), Some("vvh_modified"),
      roadLink.attributes, roadAddress.roadNumber, roadAddress.roadPartNumber, roadAddress.track.value, municipalityRoadMaintainerMapping.getOrElse(roadLink.municipalityCode, -1), roadAddress.discontinuity.value,
      roadAddress.startAddrMValue, roadAddress.endAddrMValue, roadAddress.startDate.map(formatter.print).getOrElse(""), roadAddress.endDate.map(formatter.print).getOrElse(""), roadAddress.startMValue, roadAddress.endMValue,
      roadAddress.sideCode,
      roadAddress.calibrationPoints._1,
      roadAddress.calibrationPoints._2,Anomaly.None, roadAddress.lrmPositionId)

  }

  def build(roadLink: VVHRoadlink, roadAddress: RoadAddress): RoadAddressLink = {
    val roadLinkType = SuravageRoadLink
    val geom = GeometryUtils.truncateGeometry3D(roadLink.geometry, roadAddress.startMValue, roadAddress.endMValue)
    val length = GeometryUtils.geometryLength(geom)
    val roadName = roadLink.attributes.getOrElse(FinnishRoadName, roadLink.attributes.getOrElse(SwedishRoadName, "none")).toString
    val municipalityCode = roadLink.attributes.getOrElse("MUNICIPALITYCODE",roadLink.municipalityCode).asInstanceOf[Number].intValue()
    val linkType = getLinkType(roadLink)
    val roadType = roadAddress.roadType match {
      case RoadType.Unknown => getRoadType(roadLink.administrativeClass, linkType)
      case _ => roadAddress.roadType
    }

    RoadAddressLink(roadAddress.id, roadLink.linkId, geom,
      length, roadLink.administrativeClass, linkType, roadLinkType, roadLink.constructionType, roadLink.linkSource, roadType, roadName, municipalityCode, extractModifiedAtVVH(roadLink.attributes), Some("vvh_modified"),
      roadLink.attributes, roadAddress.roadNumber, roadAddress.roadPartNumber, roadAddress.track.value, municipalityRoadMaintainerMapping.getOrElse(roadLink.municipalityCode, -1), roadAddress.discontinuity.value,
      roadAddress.startAddrMValue, roadAddress.endAddrMValue, roadAddress.startDate.map(formatter.print).getOrElse(""), roadAddress.endDate.map(formatter.print).getOrElse(""), roadAddress.startMValue, roadAddress.endMValue,
      roadAddress.sideCode,
      roadAddress.calibrationPoints._1,
      roadAddress.calibrationPoints._2,Anomaly.None, roadAddress.lrmPositionId)
  }

  def buildSimpleLink(roadAddress: RoadAddress): RoadAddressLink = {
    val roadLinkType = NormalRoadLinkType

    val geom = roadAddress.geometry
    val length = GeometryUtils.geometryLength(geom)
    val roadName = ""
    val municipalityCode = 0
    val roadType = roadAddress.roadType
    RoadAddressLink(roadAddress.id, roadAddress.linkId, geom,
      length, AdministrativeClass.apply(1), LinkType.apply(99), roadLinkType, ConstructionType.apply(0), LinkGeomSource.apply(1), roadType, roadName, municipalityCode, Some(""), Some("vvh_modified"),
      Map(), roadAddress.roadNumber, roadAddress.roadPartNumber, roadAddress.track.value, 0, roadAddress.discontinuity.value,
      roadAddress.startAddrMValue, roadAddress.endAddrMValue, roadAddress.startDate.map(formatter.print).getOrElse(""), roadAddress.endDate.map(formatter.print).getOrElse(""), roadAddress.startMValue, roadAddress.endMValue,
      roadAddress.sideCode,
      roadAddress.calibrationPoints._1,
      roadAddress.calibrationPoints._2,Anomaly.None, roadAddress.lrmPositionId)

  }

  def build(roadLink: RoadLinkLike, missingAddress: MissingRoadAddress): RoadAddressLink = {
    roadLink match {
      case rl: RoadLink => buildRoadLink(rl, missingAddress)
      case rl: RoadLinkLike => throw new NotImplementedError(s"No support for building missing road address links on RoadLinkLike subclass ${rl.getClass}")
    }
  }

  private def buildRoadLink(roadLink: RoadLink, missingAddress: MissingRoadAddress): RoadAddressLink = {
    val geom = GeometryUtils.truncateGeometry3D(roadLink.geometry, missingAddress.startMValue.getOrElse(0.0), missingAddress.endMValue.getOrElse(roadLink.length))
    val length = GeometryUtils.geometryLength(geom)
    val roadLinkRoadNumber = roadLink.attributes.get(RoadNumber).map(toIntNumber).getOrElse(0)
    val roadLinkRoadPartNumber = roadLink.attributes.get(RoadPartNumber).map(toIntNumber).getOrElse(0)
    val roadName = roadLink.attributes.getOrElse(FinnishRoadName, roadLink.attributes.getOrElse(SwedishRoadName, "none")).toString
    val municipalityCode = roadLink.attributes.getOrElse(MunicipalityCode,0).asInstanceOf[Number].intValue()
    val roadType = missingAddress.roadType match {
      case RoadType.Unknown => getRoadType(roadLink.administrativeClass, roadLink.linkType)
      case _ => missingAddress.roadType
    }
    RoadAddressLink(0, roadLink.linkId, geom,
      length, roadLink.administrativeClass, roadLink.linkType, UnknownRoadLinkType, roadLink.constructionType, roadLink.linkSource, roadType,
      roadName, municipalityCode, extractModifiedAtVVH(roadLink.attributes), Some("vvh_modified"),
      roadLink.attributes, missingAddress.roadNumber.getOrElse(roadLinkRoadNumber),
      missingAddress.roadPartNumber.getOrElse(roadLinkRoadPartNumber), Track.Unknown.value, municipalityRoadMaintainerMapping.getOrElse(roadLink.municipalityCode, -1), Discontinuity.Continuous.value,
      0, 0, "", "", 0.0, length, SideCode.Unknown, None, None, missingAddress.anomaly, 0)
  }


  def buildSuravageRoadAddressLink(roadLink: VVHRoadlink, roadAddress: Option[RoadAddress] = None): RoadAddressLink = {
    val geom = GeometryUtils.truncateGeometry3D(roadLink.geometry,  0.0, roadLink.length)
    val length = GeometryUtils.geometryLength(geom)
    val sideCode= if (roadLink.trafficDirection==TrafficDirection.TowardsDigitizing) SideCode.TowardsDigitizing else if(roadLink.trafficDirection==TrafficDirection.AgainstDigitizing) SideCode.AgainstDigitizing
    else if(roadLink.trafficDirection==TrafficDirection.BothDirections) SideCode.BothDirections else SideCode.Unknown
    val roadLinkRoadNumber = toLongNumber(roadAddress.map(_.roadNumber), roadLink.attributes.get(RoadNumber))
    val roadLinkRoadPartNumber = toLongNumber(roadAddress.map(_.roadNumber), roadLink.attributes.get(RoadPartNumber))
    val roadName = roadLink.attributes.getOrElse(FinnishRoadName, roadLink.attributes.getOrElse(SwedishRoadName, "none")).toString
    val municipalityCode = roadLink.municipalityCode
    val anomalyType= {if (roadLinkRoadNumber!=0 && roadLinkRoadPartNumber!=0) Anomaly.None else Anomaly.NoAddressGiven}
    RoadAddressLink(0, roadLink.linkId, geom,
      length, roadLink.administrativeClass, getLinkType(roadLink), SuravageRoadLink, roadLink.constructionType, roadLink.linkSource, getRoadType(roadLink.administrativeClass, getLinkType(roadLink)),
      roadName, municipalityCode, extractModifiedAtVVH(roadLink.attributes), Some("vvh_modified"),
      roadLink.attributes,roadLinkRoadNumber,
      roadLinkRoadPartNumber, Track.Unknown.value, municipalityRoadMaintainerMapping.getOrElse(roadLink.municipalityCode, -1), Discontinuity.Continuous.value,
      0, 0, "", "", 0.0, length, sideCode, None, None, anomalyType, 0)
  }

  def build(historyRoadLink: VVHHistoryRoadLink, roadAddress: RoadAddress): RoadAddressLink = {
    val roadLinkType = FloatingRoadLinkType
    val geom = GeometryUtils.truncateGeometry3D(historyRoadLink.geometry, roadAddress.startMValue, roadAddress.endMValue)
    val length = GeometryUtils.geometryLength(geom)
    val roadName = historyRoadLink.attributes.getOrElse(FinnishRoadName, historyRoadLink.attributes.getOrElse(SwedishRoadName, "none")).toString
    val municipalityCode = historyRoadLink.attributes.getOrElse(MunicipalityCode,0).asInstanceOf[Number].intValue()
    val roadType = roadAddress.roadType match {
      case RoadType.Unknown => getRoadType(historyRoadLink.administrativeClass, UnknownLinkType)
      case _ => roadAddress.roadType
    }
    RoadAddressLink(roadAddress.id, historyRoadLink.linkId, geom,
      length, historyRoadLink.administrativeClass, UnknownLinkType, roadLinkType, ConstructionType.UnknownConstructionType, LinkGeomSource.HistoryLinkInterface, roadType, roadName, municipalityCode, extractModifiedAtVVH(historyRoadLink.attributes), Some("vvh_modified"),
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
