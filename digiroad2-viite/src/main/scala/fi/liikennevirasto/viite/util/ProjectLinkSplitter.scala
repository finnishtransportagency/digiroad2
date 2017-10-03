package fi.liikennevirasto.viite.util

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.viite.dao.{LinkStatus, ProjectLink}
import fi.liikennevirasto.viite.model.ProjectAddressLink

/**
  * Split suravage link together with project link template
  */
object ProjectLinkSplitter {
  def split(suravage: ProjectAddressLink, templateLink: ProjectAddressLink, split: SplitOptions): Seq[ProjectLink] = {
    Seq()
  }
}

case class SplitOptions(splitPoint: Point, statusA: LinkStatus, statusB: LinkStatus,
                        roadNumber: Long, roadPartNumber: Long, trackCode: Int, discontinuity: Int, ely: Long,
                        roadLinkSource: Int, roadType: Int)

