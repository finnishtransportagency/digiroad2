package fi.liikennevirasto.viite

import fi.liikennevirasto.digiroad2.{GeometryUtils, Point, RoadLinkType}
import fi.liikennevirasto.digiroad2.RoadLinkType._
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource._
import fi.liikennevirasto.digiroad2.asset.{LinkGeomSource, LinkType, SideCode, UnknownLinkType}
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.viite.dao._
import fi.liikennevirasto.viite.model.{Anomaly, ProjectAddressLink}

object ProjectAddressLinkBuilder extends AddressLinkBuilder {
  def build(roadLink: RoadLinkLike, projectLink: ProjectLink): ProjectAddressLink = {
    val roadLinkType = roadLink.linkSource match {
      case NormalLinkInterface => NormalRoadLinkType
      case ComplimentaryLinkInterface => ComplementaryRoadLinkType
      case SuravageLinkInterface => SuravageRoadLink
      case FrozenLinkInterface => NormalRoadLinkType
      case HistoryLinkInterface => FloatingRoadLinkType
      case LinkGeomSource.Unknown => UnknownRoadLinkType
    }

    val geom = roadLink.geometry
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
    build(roadLink, projectLink.id, geom, length, roadNumber, roadPartNumber, trackCode, roadName, municipalityCode,
      linkType, roadLinkType, projectLink.discontinuity, projectLink.startAddrMValue, projectLink.endAddrMValue, projectLink.startMValue, projectLink.endMValue,
      projectLink.sideCode,
      projectLink.calibrationPoints._1,
      projectLink.calibrationPoints._2,Anomaly.None, projectLink.lrmPositionId, projectLink.status, projectLink.roadAddressId)
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
      linkType, UnknownRoadLinkType, Discontinuity.Continuous, missingAddress.startAddrMValue.getOrElse(0), missingAddress.endAddrMValue.getOrElse(0),
      missingAddress.startMValue.getOrElse(0.0), missingAddress.endMValue.getOrElse(0.0),SideCode.Unknown,
      None, None, Anomaly.None, 0, LinkStatus.Unknown, 0)
  }

  private def build(roadLink: RoadLinkLike, id: Long, geom: Seq[Point], length: Double, roadNumber: Long, roadPartNumber: Long,
                    trackCode: Int, roadName: String, municipalityCode: Int, linkType: LinkType, roadLinkType: RoadLinkType,
                    discontinuity: Discontinuity,
                    startAddrMValue: Long, endAddrMValue: Long, startMValue: Double, endMValue: Double,
                    sideCode: SideCode, startCalibrationPoint: Option[CalibrationPoint], endCalibrationPoint: Option[CalibrationPoint],
                    anomaly: Anomaly, lrmPositionId: Long, status: LinkStatus, roadAddressId : Long): ProjectAddressLink = {

    ProjectAddressLink(id, roadLink.linkId, geom,
      length, roadLink.administrativeClass, linkType, roadLinkType, roadLink.constructionType, roadLink.linkSource,
      getRoadType(roadLink.administrativeClass, linkType), roadName, municipalityCode, extractModifiedAtVVH(roadLink.attributes), Some("vvh_modified"),
      roadLink.attributes, roadNumber, roadPartNumber, trackCode, municipalityRoadMaintainerMapping.getOrElse(roadLink.municipalityCode, -1), discontinuity.value,
      startAddrMValue, endAddrMValue, startMValue, endMValue, sideCode, startCalibrationPoint, endCalibrationPoint, anomaly, lrmPositionId, status, roadAddressId)
  }

}
