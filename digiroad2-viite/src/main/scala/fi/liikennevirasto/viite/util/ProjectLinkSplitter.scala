package fi.liikennevirasto.viite.util

import fi.liikennevirasto.digiroad2.util.Track
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import fi.liikennevirasto.viite.RoadType
import fi.liikennevirasto.viite.dao.{Discontinuity, LinkStatus, ProjectLink}
import fi.liikennevirasto.viite.model.ProjectAddressLink

/**
  * Split suravage link together with project link template
  */
object ProjectLinkSplitter {
  def split(suravage: ProjectLink, templateLink: ProjectLink, split: SplitOptions): Seq[ProjectLink] = {
    val suravageM = GeometryUtils.calculateLinearReferenceFromPoint(split.splitPoint, suravage.geometry)
    val templateM = GeometryUtils.calculateLinearReferenceFromPoint(split.splitPoint, templateLink.geometry)
    val (splittedA, splittedB, template) = (split.statusA, split.statusB) match {
      case (LinkStatus.UnChanged, LinkStatus.Terminated) => {
        val splitAddressM = Math.round(templateM / templateLink.geometryLength *
          (templateLink.endAddrMValue - templateLink.startAddrMValue))
        (suravage.copy(roadNumber = split.roadNumber,
          roadPartNumber = split.roadPartNumber,
          track = Track.apply(split.trackCode),
          discontinuity = Discontinuity.apply(split.discontinuity),
//          ely = split.ely,
          roadType = RoadType.apply(split.roadType),
          startMValue = 0.0,
          endMValue = suravageM,
          startAddrMValue = templateLink.startAddrMValue,
          endAddrMValue = splitAddressM,
          status = LinkStatus.UnChanged
        ), suravage.copy(roadNumber = split.roadNumber,
          roadPartNumber = split.roadPartNumber,
          track = Track.apply(split.trackCode),
          discontinuity = Discontinuity.apply(split.discontinuity),
//          ely = split.ely,
          roadType = RoadType.apply(split.roadType),
          startMValue = suravageM,
          endMValue = suravage.geometryLength,
          startAddrMValue = splitAddressM,
          endAddrMValue = templateLink.endAddrMValue,
          status = LinkStatus.New
        ), templateLink.copy(
          startMValue = templateM,
          endMValue = templateLink.geometryLength,
          startAddrMValue = splitAddressM,
          status = LinkStatus.Terminated
        ))
      }
        Seq(splittedA,splittedB,template)
    }
    Seq()
  }
}

case class SplitOptions(splitPoint: Point, statusA: LinkStatus, statusB: LinkStatus,
                        roadNumber: Long, roadPartNumber: Long, trackCode: Int, discontinuity: Int, ely: Long,
                        roadLinkSource: Int, roadType: Int)

