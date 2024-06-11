package fi.liikennevirasto.digiroad2.util

import fi.liikennevirasto.digiroad2.asset.{LinkGeomSource, SideCode, TrafficDirection}
import fi.liikennevirasto.digiroad2.linearasset.{LinearAsset, PersistedLinearAsset, PieceWiseLinearAsset, RoadLink}
import fi.liikennevirasto.digiroad2.asset._
import org.joda.time.{DateTime, DateTimeZone}

object LinearAssetUtils {

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
