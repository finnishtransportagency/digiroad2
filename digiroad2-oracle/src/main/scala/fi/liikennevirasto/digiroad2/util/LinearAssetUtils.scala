package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset.{LinkGeomSource, SideCode, TrafficDirection}
import fi.liikennevirasto.digiroad2.linearasset.{LinearAsset, PersistedLinearAsset, PieceWiseLinearAsset, RoadLink}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.ChangeInfo

object LinearAssetUtils {
  /**
    * Return true if the vvh time stamp is older than change time stamp
    * and asset may need projecting
    * @param asset SpeedLimit under consideration
    * @param change Change information
    * @return true if speed limit may be outdated
    */
  @Deprecated
  def newChangeInfoDetected(asset : LinearAsset, change: Seq[ChangeInfo]) = {
    change.exists(c =>
      c.vvhTimeStamp > asset.vvhTimeStamp && (c.oldId.getOrElse(0) == asset.linkId || c.newId.getOrElse(0) == asset.linkId)
    )
  }

  def newChangeInfoDetected(asset : LinearAsset, changes: Map[Long, Seq[ChangeInfo]]) = {
    changes.getOrElse(asset.linkId, Seq()).exists(c =>
      c.vvhTimeStamp > asset.vvhTimeStamp && (c.oldId.getOrElse(0) == asset.linkId || c.newId.getOrElse(0) == asset.linkId)
    )
  }

  @Deprecated
  def newChangeInfoDetected(a: PersistedLinearAsset, changes: Seq[ChangeInfo]): Boolean = {
    newChangeInfoDetected(persistedLinearAssetToLinearAsset(a), changes)
  }

  def newChangeInfoDetected(a: PersistedLinearAsset, changes: Map[Long, Seq[ChangeInfo]]): Boolean = {
    newChangeInfoDetected(persistedLinearAssetToLinearAsset(a), changes)
  }

  /* Filter to only those Ids that are no longer present on map and not referred to in change information
     Used by LinearAssetService and SpeedLimitService
   */
  @Deprecated
  def deletedRoadLinkIds(change: Seq[ChangeInfo], current: Seq[RoadLink]): Seq[Long] = {
    change.filter(_.oldId.nonEmpty).flatMap(_.oldId).filterNot(id => current.exists(rl => rl.linkId == id)).
      filterNot(id => change.exists(ci => ci.newId.getOrElse(0) == id))
  }

  def deletedRoadLinkIds(changes: Map[Long, Seq[ChangeInfo]], currentLinkIds: Set[Long]): Seq[Long] = {
    currentLinkIds.flatMap { linkId =>
      val someChange = changes.get(linkId)
      if (someChange.nonEmpty) {
        someChange.get.flatMap { x => !x.oldId.contains(linkId) -> x.oldId.get
          if (x.oldId.contains(linkId)) None else Some(x.oldId)
        }.flatten
      } else
        None
    }.toSeq
  }

  def getMappedChanges(changes: Seq[ChangeInfo]): Map[Long, Seq[ChangeInfo]] = {
    (changes.filter(_.oldId.nonEmpty).map(c => c.oldId.get -> c) ++ changes.filter(_.newId.nonEmpty)
      .map(c => c.newId.get -> c)).groupBy(_._1).mapValues(_.map(_._2))
  }

  private def persistedLinearAssetToLinearAsset(persisted: PersistedLinearAsset) = {
    PieceWiseLinearAsset(id = persisted.id, linkId = persisted.linkId, sideCode = SideCode.apply(persisted.sideCode),
      value = persisted.value, geometry = Seq(), expired = persisted.expired, startMeasure = persisted.startMeasure,
      endMeasure = persisted.endMeasure, endpoints = Set(), modifiedBy = persisted.modifiedBy, modifiedDateTime = persisted.modifiedDateTime,
      createdBy = persisted.createdBy, createdDateTime = persisted.createdDateTime, typeId = persisted.typeId,
      trafficDirection = TrafficDirection.UnknownDirection, vvhTimeStamp = persisted.vvhTimeStamp, geomModifiedDate = persisted.geomModifiedDate,
      linkSource = persisted.linkSource, administrativeClass = Unknown, verifiedBy = persisted.verifiedBy, verifiedDate = persisted.verifiedDate, informationSource = persisted.informationSource)
  }
}
