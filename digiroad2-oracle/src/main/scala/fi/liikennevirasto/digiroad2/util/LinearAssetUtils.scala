package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset.{LinkGeomSource, SideCode, TrafficDirection}
import fi.liikennevirasto.digiroad2.linearasset.{LinearAsset, PersistedLinearAsset, PieceWiseLinearAsset, RoadLink}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.{RoadLinkChange, RoadLinkChangeType}
import fi.liikennevirasto.digiroad2.client.vvh.ChangeInfo
import org.joda.time.{DateTime, DateTimeZone}

object LinearAssetUtils {
  /**
    * Return true if the current asset time stamp is older than change time stamp
    * and asset may need projecting
    * @param asset SpeedLimit under consideration
    * @param change Change information
    * @return true if speed limit may be outdated
    */
  @Deprecated
  def newChangeInfoDetected(asset : LinearAsset, change: Seq[ChangeInfo]) = {
    change.exists(c =>
      c.timeStamp > asset.timeStamp && (c.oldId.getOrElse(0) == asset.linkId || c.newId.getOrElse(0) == asset.linkId)
    )
  }

  def newChangeInfoDetected(asset : LinearAsset, changes: Map[String, Seq[ChangeInfo]]) = {
    changes.getOrElse(asset.linkId, Seq()).exists(c =>
      c.timeStamp > asset.timeStamp && (c.oldId.getOrElse(0) == asset.linkId || c.newId.getOrElse(0) == asset.linkId)
    )
  }

  @Deprecated
  def newChangeInfoDetected(a: PersistedLinearAsset, changes: Seq[ChangeInfo]): Boolean = {
    newChangeInfoDetected(persistedLinearAssetToLinearAsset(a), changes)
  }

  def newChangeInfoDetected(a: PersistedLinearAsset, changes: Map[String, Seq[ChangeInfo]]): Boolean = {
    newChangeInfoDetected(persistedLinearAssetToLinearAsset(a), changes)
  }

  /* Filter to only those Ids that are no longer present on map and not referred to in change information
     Used by LinearAssetService and SpeedLimitService
   */
  @Deprecated
  def deletedRoadLinkIds(change: Seq[ChangeInfo], current: Seq[RoadLink]): Seq[String] = {
    change.filter(_.oldId.nonEmpty).flatMap(_.oldId).filterNot(id => current.exists(rl => rl.linkId == id)).
      filterNot(id => change.exists(ci => ci.newId.getOrElse(0) == id))
  }

  def deletedRoadLinkIds(changes: Map[String, Seq[ChangeInfo]], currentLinkIds: Set[String]): Seq[String] = {
    changes.filter(c =>
      !c._2.exists(ci => ci.newId.contains(c._1)) &&
        !currentLinkIds.contains(c._1)
    ).keys.toSeq
  }

  def getMappedChanges(changes: Seq[ChangeInfo]): Map[String, Seq[ChangeInfo]] = {
    (changes.filter(_.oldId.nonEmpty).map(c => c.oldId.get -> c) ++ changes.filter(_.newId.nonEmpty)
      .map(c => c.newId.get -> c)).groupBy(_._1).mapValues(_.map(_._2))
  }

  private def persistedLinearAssetToLinearAsset(persisted: PersistedLinearAsset) = {
    PieceWiseLinearAsset(id = persisted.id, linkId = persisted.linkId, sideCode = SideCode.apply(persisted.sideCode),
      value = persisted.value, geometry = Seq(), expired = persisted.expired, startMeasure = persisted.startMeasure,
      endMeasure = persisted.endMeasure, endpoints = Set(), modifiedBy = persisted.modifiedBy, modifiedDateTime = persisted.modifiedDateTime,
      createdBy = persisted.createdBy, createdDateTime = persisted.createdDateTime, typeId = persisted.typeId,
      trafficDirection = TrafficDirection.UnknownDirection, timeStamp = persisted.timeStamp, geomModifiedDate = persisted.geomModifiedDate,
      linkSource = persisted.linkSource, administrativeClass = Unknown, verifiedBy = persisted.verifiedBy, verifiedDate = persisted.verifiedDate, informationSource = persisted.informationSource)
  }

  def createTimeStamp(offsetHours: Int = 5): Long = {
    val oneHourInMs = 60 * 60 * 1000L
    val utcTime = DateTime.now().minusHours(offsetHours).getMillis
    val curr = utcTime + DateTimeZone.getDefault.getOffset(utcTime)
    curr - (curr % (24L*oneHourInMs))
  }
}
