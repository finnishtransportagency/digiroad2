package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.client.vvh.VVHRoadlink
import fi.liikennevirasto.digiroad2.{GeometryUtils, Point}
import org.geotools.filter.function.GeometryTransformation

import scala.collection.mutable.ListBuffer
import scala.collection.mutable.ArrayBuffer

/**
  * This class is used to filter history road links from VVH. Filtered history links are used to show history information on map (DROTH-10).
  *
  * @param includeCurrentLinks If true, compare history with current road links within tolerance
  * @param minimumChange Tolerance minimum value
  * @param maximumChange Tolerance maximum value
  */
class VVHRoadLinkHistoryProcessor(includeCurrentLinks: Boolean = false, minimumChange: Double = 1.0, maximumChange: Double = 50.0) {

  /**
    * This method returns the latest history links that:
    * - have been deleted
    * - have been changed and are within tolerance (when includeCurrentLinks flag is set as true)
    *
    * @param historyRoadLinks All history road links
    * @param roadLinks Current road links
    * @return Filtered history links
    */
  def process(historyRoadLinks:Seq[VVHRoadlink], roadLinks :Seq[VVHRoadlink]) : Seq[VVHRoadlink] ={
    def endDate(vvhRoadlink: VVHRoadlink) =
      vvhRoadlink.attributes.getOrElse("END_DATE", BigInt(0)).asInstanceOf[BigInt].longValue()

    def newLinkId(vvhRoadlink: VVHRoadlink) : Option[BigInt] = {
      vvhRoadlink.attributes.get("LINKID_NEW") match {
        case Some(linkId) =>
          Some(linkId.asInstanceOf[BigInt].longValue())
        case _ =>
          None
      }
    }

    def hasNewLinkId(vvhRoadlink: VVHRoadlink) = newLinkId(vvhRoadlink).isEmpty

    // If several history link items have the same linkId, pick the one with latest endDate
    val latestHistory = historyRoadLinks.groupBy(_.linkId).mapValues(rl => rl.maxBy(endDate)).values

    // Deleted = history link has newLinkId and it's linkId is not found in current links
    val deletedRoadlinks = latestHistory.filter(hasNewLinkId).filterNot(rl => roadLinks.exists(rl.linkId == _.linkId))

    // Changed = linkId of history link is found in current links
    val changedRoadlinks = latestHistory.filter{
      rl =>
        val roadlink = roadLinks.find(r => Some(r.linkId) == newLinkId(rl) || (r.linkId == rl.linkId && includeCurrentLinks))
        roadlink.exists(r =>
          compareGeometry(r.geometry, rl.geometry)
        )
    }

    (changedRoadlinks ++ deletedRoadlinks).toSeq
  }

  /**
    *
    * @param geom1 geometry 1
    * @param geom2 geometry 2
    * @return true when distance is greater than minimum but less than maximum else false. Also true if number of geometries is different
    */
  private def compareGeometry(geom1: Seq[Point], geom2: Seq[Point]): Boolean = {
    geom1.size == geom2.size &&
      geom1.zip(geom2).exists {
        case (p1, p2) => {
          val lenght = GeometryUtils.geometryLength(Seq(p1,p2))
          lenght >= minimumChange && lenght <= maximumChange
        }
        case _ => false
      }
  }
}