package fi.liikennevirasto.viite

import fi.liikennevirasto.digiroad2.{GeometryUtils, Point, RoadLinkType}
import fi.liikennevirasto.digiroad2.RoadLinkType._
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.viite.dao._
import fi.liikennevirasto.viite.model.{Anomaly, ProjectAddressLink}

object ProjectAddressLinkBuilder extends AddressLinkBuilder {

  def build(pl: ProjectLink, splitPart: Option[ProjectLink] = None): ProjectAddressLink = {
    val roadLinkType = pl.linkGeomSource match {
      case NormalLinkInterface => NormalRoadLinkType
      case ComplimentaryLinkInterface => ComplementaryRoadLinkType
      case SuravageLinkInterface => SuravageRoadLink
      case FrozenLinkInterface => NormalRoadLinkType
      case HistoryLinkInterface => FloatingRoadLinkType
      case LinkGeomSource.Unknown => UnknownRoadLinkType
    }

    val roadName = s"${pl.roadNumber}/${pl.roadPartNumber}/${pl.track.value}"

    val linkType = UnknownLinkType

    val originalGeometry =
      if (pl.isSplit)
        if (splitPart.nonEmpty)
          combineGeometries(pl, splitPart.get)
        else
          // TODO Is this case needed?
          Some(pl.geometry)
      else
        None

    ProjectAddressLink(pl.id, pl.linkId, pl.geometry,
      pl.geometryLength, fi.liikennevirasto.digiroad2.asset.Unknown, linkType, roadLinkType, ConstructionType.UnknownConstructionType,
      pl.linkGeomSource, pl.roadType, roadName, 0L, None, Some("vvh_modified"),
      Map(), pl.roadNumber, pl.roadPartNumber, pl.track.value, pl.ely, pl.discontinuity.value,
      pl.startAddrMValue, pl.endAddrMValue, pl.startMValue, pl.endMValue, pl.sideCode, pl.calibrationPoints._1,
      pl.calibrationPoints._2, Anomaly.None, pl.lrmPositionId, pl.status, pl.roadAddressId,
      pl.reversed, pl.connectedLinkId, originalGeometry)
  }

  @Deprecated
  def build(roadLink: RoadLinkLike, projectLink: ProjectLink): ProjectAddressLink = {
    val roadLinkType = roadLink.linkSource match {
      case NormalLinkInterface => NormalRoadLinkType
      case ComplimentaryLinkInterface => ComplementaryRoadLinkType
      case SuravageLinkInterface => SuravageRoadLink
      case FrozenLinkInterface => NormalRoadLinkType
      case HistoryLinkInterface => FloatingRoadLinkType
      case LinkGeomSource.Unknown => UnknownRoadLinkType
    }

    val geom = if (projectLink.isSplit)
      GeometryUtils.truncateGeometry3D(roadLink.geometry, projectLink.startMValue, projectLink.endMValue)
    else
      roadLink.geometry
    val length = GeometryUtils.geometryLength(geom)
    val roadNumber = projectLink.roadNumber match {
      case 0 => roadLink.attributes.getOrElse(RoadNumber, projectLink.roadNumber).asInstanceOf[Number].longValue()
      case _ => projectLink.roadNumber
    }
    val roadPartNumber = projectLink.roadPartNumber match {
      case 0 => roadLink.attributes.getOrElse(RoadPartNumber, projectLink.roadPartNumber).asInstanceOf[Number].longValue()
      case _ => projectLink.roadPartNumber
    }
    val trackCode = projectLink.track.value match {
      case 99 => roadLink.attributes.getOrElse(TrackCode, projectLink.track.value).asInstanceOf[Number].intValue()
      case _ => projectLink.track.value
    }
    val roadName = roadLink.attributes.getOrElse(FinnishRoadName, roadLink.attributes.getOrElse(SwedishRoadName, "none")).toString
    val municipalityCode = roadLink.municipalityCode

    val linkType = roadLink match {
      case rl: RoadLink => rl.linkType
      case _ => UnknownLinkType
    }

    val originalGeometry =
      if (projectLink.isSplit)
        Some(roadLink.geometry)
      else
        None

    build(roadLink, projectLink.id, geom, length, roadNumber, roadPartNumber, trackCode, roadName, municipalityCode,
      linkType, roadLinkType, projectLink.roadType,  projectLink.discontinuity, projectLink.startAddrMValue, projectLink.endAddrMValue,
      projectLink.startMValue, projectLink.endMValue, projectLink.sideCode,
      projectLink.calibrationPoints._1, projectLink.calibrationPoints._2,
      Anomaly.None, projectLink.lrmPositionId, projectLink.status, projectLink.roadAddressId, projectLink.ely, projectLink.reversed, projectLink.connectedLinkId,
      originalGeometry
    )
  }

  def build(roadLink: RoadLinkLike, missingAddress: MissingRoadAddress): ProjectAddressLink = {
    val geom = GeometryUtils.truncateGeometry3D(roadLink.geometry, missingAddress.startMValue.getOrElse(0.0), missingAddress.endMValue.getOrElse(roadLink.length))
    val length = GeometryUtils.geometryLength(geom)
    val roadLinkRoadNumber: Long = roadLink.attributes.get(RoadNumber).map(toLongNumber).getOrElse(0L)
    val roadLinkRoadPartNumber: Long = roadLink.attributes.get(RoadPartNumber).map(toLongNumber).getOrElse(0L)
    val roadLinkTrackCode: Int = roadLink.attributes.get(TrackCode).map(toIntNumber).getOrElse(0)
    val roadName = roadLink.attributes.getOrElse(FinnishRoadName, roadLink.attributes.getOrElse(SwedishRoadName, "none")).toString
    val municipalityCode = roadLink.municipalityCode
    val linkType = roadLink match {
      case rl: RoadLink => rl.linkType
      case _ => UnknownLinkType
    }
    build(roadLink, 0L, geom, length, roadLinkRoadNumber, roadLinkRoadPartNumber, roadLinkTrackCode, roadName, municipalityCode,
      linkType, UnknownRoadLinkType, getRoadType(roadLink.administrativeClass, linkType), Discontinuity.Continuous, missingAddress.startAddrMValue.getOrElse(0), missingAddress.endAddrMValue.getOrElse(0),
      missingAddress.startMValue.getOrElse(0.0), missingAddress.endMValue.getOrElse(0.0),SideCode.Unknown,
      None, None, Anomaly.None, 0, LinkStatus.Unknown, 0, municipalityRoadMaintainerMapping.getOrElse(roadLink.municipalityCode, -1), reversed= false, None, None)
  }

  private def build(roadLink: RoadLinkLike, id: Long, geom: Seq[Point], length: Double, roadNumber: Long, roadPartNumber: Long,
                    trackCode: Int, roadName: String, municipalityCode: Int, linkType: LinkType, roadLinkType: RoadLinkType,
                    roadType: RoadType, discontinuity: Discontinuity,
                    startAddrMValue: Long, endAddrMValue: Long, startMValue: Double, endMValue: Double,
                    sideCode: SideCode, startCalibrationPoint: Option[CalibrationPoint], endCalibrationPoint: Option[CalibrationPoint],
                    anomaly: Anomaly, lrmPositionId: Long, status: LinkStatus, roadAddressId: Long, ely:Long, reversed:Boolean, connectedLinkId: Option[Long],
                    originalGeometry: Option[Seq[Point]]): ProjectAddressLink = {

    val linkId =
      if (connectedLinkId.nonEmpty && status == LinkStatus.New)
        0L - roadLink.linkId
      else
        roadLink.linkId
    ProjectAddressLink(id, linkId, geom,
      length, roadLink.administrativeClass, linkType, roadLinkType, roadLink.constructionType, roadLink.linkSource,
      roadType, roadName, municipalityCode, extractModifiedAtVVH(roadLink.attributes), Some("vvh_modified"),
      roadLink.attributes, roadNumber, roadPartNumber, trackCode, ely, discontinuity.value,
      startAddrMValue, endAddrMValue, startMValue, endMValue, sideCode, startCalibrationPoint, endCalibrationPoint, anomaly, lrmPositionId, status, roadAddressId,
      reversed, connectedLinkId, originalGeometry)
  }

  private def combineGeometries(split1: ProjectLink, split2: ProjectLink) = {
    def safeTail(seq: Seq[Point]) = {
      if (seq.isEmpty)
        Seq()
      else
        seq.tail
    }
    if (split1.startMValue < split2.startMValue)
      Some(split1.geometry ++ safeTail(split2.geometry))
    else
      Some(split2.geometry ++ safeTail(split1.geometry))
  }
}
