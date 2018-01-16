package fi.liikennevirasto.viite

import fi.liikennevirasto.digiroad2.service.RoadLinkType
import fi.liikennevirasto.viite.dao.{BaseRoadAddress, ProjectLink, RoadAddress}
import fi.liikennevirasto.viite.model.{Anomaly, RoadAddressLink}

/**
  * Created by venholat on 14.6.2017.
  */
package object util {
  // used for debugging when needed
  def prettyPrint(l: RoadAddressLink): String = {

    s"""${if (l.id == -1000) { "NEW!" } else { l.id }} link: ${l.linkId} road address: ${l.roadNumber}/${l.roadPartNumber}/${l.trackCode}/${l.startAddressM}-${l.endAddressM} length: ${l.length} dir: ${l.sideCode}
       |${if (l.startCalibrationPoint.nonEmpty) { " <- " + l.startCalibrationPoint.get.addressMValue + " "} else ""}
       |${if (l.endCalibrationPoint.nonEmpty) { " " + l.endCalibrationPoint.get.addressMValue + " ->"} else ""}
       |${if (l.anomaly != Anomaly.None) { " " + l.anomaly } else ""}
       |${if (l.roadLinkType != RoadLinkType.NormalRoadLinkType) { " " + l.roadLinkType } else ""}
     """.stripMargin.replace("\n", "")
  }

  // used for debugging when needed
  def prettyPrint(l: RoadAddress): String = {

    s"""${if (l.id == -1000) { "NEW!" } else { l.id }} link: ${l.linkId} ${setPrecision(l.startMValue)}-${setPrecision(l.endMValue)} road address: ${l.roadNumber}/${l.roadPartNumber}/${l.track.value}/${l.startAddrMValue}-${l.endAddrMValue} length: ${setPrecision(l.endMValue - l.startMValue)} dir: ${l.sideCode}
       |${if (l.startCalibrationPoint.nonEmpty) { " <- " + l.startCalibrationPoint.get.addressMValue + " "} else ""}
       |${if (l.endCalibrationPoint.nonEmpty) { " " + l.endCalibrationPoint.get.addressMValue + " ->"} else ""}
       |${l.startDate.map(_.toString(" d.MM.YYYY -")).getOrElse("")} ${l.endDate.map(_.toString("d.MM.YYYY")).getOrElse("")}
       |${if (l.terminated.value != 0) " ✞" else ""}
     """.stripMargin.replace("\n", "")
  }

  // used for debugging when needed
  def prettyPrint(l: BaseRoadAddress): String = {
    l match {
      case pl: ProjectLink =>
        s"""${if (l.id == -1000) { "NEW!" } else { l.id }} link: ${l.linkId} ${pl.status} ${setPrecision(l.startMValue)}-${setPrecision(l.endMValue)} road address: ${l.roadNumber}/${l.roadPartNumber}/${l.track.value}/${l.startAddrMValue}-${l.endAddrMValue} length: ${setPrecision(l.endMValue - l.startMValue)} dir: ${l.sideCode}
     """.replace("\n", "")
      case _ =>
        s"""${if (l.id == -1000) { "NEW!" } else { l.id }} link: ${l.linkId} ${setPrecision(l.startMValue)}-${setPrecision(l.endMValue)} road address: ${l.roadNumber}/${l.roadPartNumber}/${l.track.value}/${l.startAddrMValue}-${l.endAddrMValue} length: ${setPrecision(l.endMValue - l.startMValue)} dir: ${l.sideCode}
          """.replace("\n", "")
    }
  }
  private def setPrecision(d: Double) = {
    BigDecimal(d).setScale(3, BigDecimal.RoundingMode.HALF_UP).toDouble
  }

  def toTransition(project: RoadAddressProject, status: LinkStatus)(roadAddress: RoadAddress): (RoadAddress, ProjectLink) = {
    (roadAddress, toProjectLink(project, status)(roadAddress))
  }
  def toProjectLink(project: RoadAddressProject, status: LinkStatus)(roadAddress: RoadAddress): ProjectLink = {
    if (status == LinkStatus.New) {
      ProjectLink(roadAddress.id, roadAddress.roadNumber, roadAddress.roadPartNumber, roadAddress.track,
        roadAddress.discontinuity, roadAddress.startAddrMValue, roadAddress.endAddrMValue, roadAddress.startDate,
        roadAddress.endDate, modifiedBy=Option(project.createdBy), 0L, roadAddress.linkId, roadAddress.startMValue, roadAddress.endMValue,
        roadAddress.sideCode, roadAddress.calibrationPoints, floating=false, roadAddress.geometry, project.id, status, roadAddress.roadType,
        roadAddress.linkGeomSource, GeometryUtils.geometryLength(roadAddress.geometry), 0, roadAddress.ely,false,
        None, roadAddress.adjustedTimestamp)
    } else {
      ProjectLink(roadAddress.id, roadAddress.roadNumber, roadAddress.roadPartNumber, roadAddress.track,
        roadAddress.discontinuity, roadAddress.startAddrMValue, roadAddress.endAddrMValue, roadAddress.startDate,
        roadAddress.endDate, modifiedBy=Option(project.createdBy), 0L, roadAddress.linkId, roadAddress.startMValue, roadAddress.endMValue,
        roadAddress.sideCode, roadAddress.calibrationPoints, floating=false, roadAddress.geometry, project.id, status, roadAddress.roadType,
        roadAddress.linkGeomSource, GeometryUtils.geometryLength(roadAddress.geometry), roadAddress.id, roadAddress.ely,false,
        None, roadAddress.adjustedTimestamp)
    }
  }

  def toProjectLink(project: RoadAddressProject)(roadAddress: RoadAddress): ProjectLink = {
    ProjectLink(roadAddress.id, roadAddress.roadNumber, roadAddress.roadPartNumber, roadAddress.track,
      roadAddress.discontinuity, roadAddress.startAddrMValue, roadAddress.endAddrMValue, roadAddress.startDate,
      roadAddress.endDate, modifiedBy=Option(project.createdBy), 0L, roadAddress.linkId, roadAddress.startMValue, roadAddress.endMValue,
      roadAddress.sideCode, roadAddress.calibrationPoints, floating=false, roadAddress.geometry, project.id, LinkStatus.NotHandled, RoadType.PublicRoad,
      roadAddress.linkGeomSource, GeometryUtils.geometryLength(roadAddress.geometry), roadAddress.id, roadAddress.ely,false,
      None, roadAddress.adjustedTimestamp)
  }

  def toProjectAddressLink(ral: RoadAddressLinkLike): ProjectAddressLink = {
    ProjectAddressLink(ral.id, ral.linkId, ral.geometry, ral.length, ral.administrativeClass, ral.linkType, ral.roadLinkType,
      ral.constructionType, ral.roadLinkSource, ral.roadType, ral.roadName, ral.municipalityCode, ral.modifiedAt, ral.modifiedBy,
      ral.attributes, ral.roadNumber, ral.roadPartNumber, ral.trackCode, ral.elyCode, ral.discontinuity,
      ral.startAddressM, ral.endAddressM, ral.startMValue, ral.endMValue, ral.sideCode, ral.startCalibrationPoint, ral.endCalibrationPoint,
      ral.anomaly, ral.lrmPositionId, LinkStatus.Unknown, ral.id)
  }

  def backToProjectLink(project: RoadAddressProject)(rl: ProjectAddressLink): ProjectLink = {
    ProjectLink(rl.id, rl.roadNumber, rl.roadPartNumber, Track.apply(rl.trackCode.toInt),
      Discontinuity.apply(rl.discontinuity), rl.startAddressM, rl.endAddressM, None,
      None, rl.modifiedBy, 0L, rl.linkId, rl.startMValue, rl.endMValue,
      rl.sideCode, (rl.startCalibrationPoint, rl.endCalibrationPoint), floating=false, rl.geometry, project.id,
      LinkStatus.NotHandled, RoadType.PublicRoad,
      rl.roadLinkSource, GeometryUtils.geometryLength(rl.geometry), if (rl.status == LinkStatus.New ) 0 else rl.id, rl.elyCode,false,
      None, rl.vvhTimeStamp)
  }




}
