package fi.liikennevirasto.viite.process

import fi.liikennevirasto.digiroad2.GeometryUtils
import fi.liikennevirasto.viite.dao.LinkStatus._
import fi.liikennevirasto.viite.dao.ProjectLink

object RoadAddressSplitMapper extends RoadAddressMapper {
  def createAddressMap(splitProjectLinks: Seq[ProjectLink]): Seq[RoadAddressMapping] = {
    splitProjectLinks.groupBy(_.roadAddressId).flatMap { case (_, seq) =>
      val templateTerm = seq.find(_.status == Terminated)
      val kept = seq.find(pl => pl.status == Transfer || pl.status == UnChanged)
      (templateTerm, kept) match {
        case (Some(t), Some(k)) if GeometryUtils.areAdjacent(t.geometry.head, k.geometry.last) =>
          // t extends k
          Seq(RoadAddressMapping(t.linkId, t.linkId, t.startMValue, t.endMValue, t.startMValue, t.endMValue,
            t.geometry, t.geometry, Some(k.linkGeometryTimeStamp)),
            RoadAddressMapping(t.linkId, k.linkId, 0.0, t.startMValue, k.startMValue, k.endMValue,
              k.geometry, k.geometry, Some(k.linkGeometryTimeStamp)))
        case (Some(t), Some(k)) if GeometryUtils.areAdjacent(k.geometry.head, t.geometry.last) =>
          // k extends t
          Seq(RoadAddressMapping(t.linkId, t.linkId, t.startMValue, t.endMValue, t.startMValue, t.endMValue,
            t.geometry, t.geometry, Some(k.linkGeometryTimeStamp)),
            // old endM value on terminated link was the terminated part endM plus the length of the kept part
            RoadAddressMapping(t.linkId, k.linkId, t.endMValue, t.endMValue + (k.endMValue - k.startMValue),
              k.startMValue, k.endMValue, k.geometry, k.geometry, Some(k.linkGeometryTimeStamp)))
        case (Some(t), Some(k)) if GeometryUtils.areAdjacent(k.geometry.last, t.geometry.last) =>
          // tail-to-tail meet (suravage has differing side code from template) -> startM and endM swapped
          Seq(RoadAddressMapping(t.linkId, t.linkId, t.startMValue, t.endMValue, t.startMValue, t.endMValue,
            t.geometry, t.geometry, Some(k.linkGeometryTimeStamp)),
            RoadAddressMapping(t.linkId, k.linkId, t.endMValue, t.endMValue + (k.endMValue - k.startMValue),
              k.endMValue, k.startMValue, k.geometry, k.geometry, Some(k.linkGeometryTimeStamp)))
        case (Some(t), Some(k)) if GeometryUtils.areAdjacent(k.geometry.head, t.geometry.head) =>
        // head-to-head meet (suravage has differing side code from template) -> startM and endM swapped
          Seq(RoadAddressMapping(t.linkId, t.linkId, t.startMValue, t.endMValue, t.startMValue, t.endMValue,
            t.geometry, t.geometry, Some(k.linkGeometryTimeStamp)),
            RoadAddressMapping(t.linkId, k.linkId, 0.0, t.startMValue, k.startMValue, k.endMValue,
              k.geometry, k.geometry, Some(k.linkGeometryTimeStamp)))
        case _ => throw new InvalidAddressDataException("No termination or transfer/unchanged found for split")
      }
    }.toSeq
  }
}
