package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.asset.SideCode

object LinearAssetFiller {
  case class MValueAdjustment(assetId: Long, linkId: String, startMeasure: Double, endMeasure: Double)
  case class VVHChangesAdjustment(assetId: Long, linkId: String, startMeasure: Double, endMeasure: Double, timeStamp: Long)
  case class SideCodeAdjustment(assetId: Long, sideCode: SideCode, typeId: Int)
  case class ValueAdjustment(asset: PersistedLinearAsset)
  case class ChangeSet(droppedAssetIds: Set[Long],
                       adjustedMValues: Seq[MValueAdjustment],
                       adjustedVVHChanges: Seq[VVHChangesAdjustment],
                       adjustedSideCodes: Seq[SideCodeAdjustment],
                       expiredAssetIds: Set[Long],
                       valueAdjustments: Seq[ValueAdjustment]){
    def isEmpty: Boolean = {
      this.droppedAssetIds.isEmpty &&
        this.adjustedMValues.isEmpty &&
        this.adjustedVVHChanges.isEmpty &&
        this.adjustedSideCodes.isEmpty &&
        this.expiredAssetIds.isEmpty &&
        this.valueAdjustments.isEmpty
    }
  }
}
