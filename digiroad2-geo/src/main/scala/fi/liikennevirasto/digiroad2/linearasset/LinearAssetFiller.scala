package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.asset.SideCode

object LinearAssetFiller {
  case class MValueAdjustment(assetId: Long, linkId: String, startMeasure: Double, endMeasure: Double,timeStamp: Long=0)
  case class VVHChangesAdjustment(assetId: Long, linkId: String, startMeasure: Double, endMeasure: Double, timeStamp: Long=0)
  case class SideCodeAdjustment(assetId: Long, sideCode: SideCode, typeId: Int)
  case class ValueAdjustment(asset: PieceWiseLinearAsset)
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

    def filterGeneratedAssets: ChangeSet = {
      ChangeSet(this.droppedAssetIds.filterNot(_ <= 0),
        this.adjustedMValues.filterNot(_.assetId <= 0),
        this.adjustedVVHChanges.filterNot(_.assetId <= 0),
        this.adjustedSideCodes.filterNot(_.assetId <= 0),
        this.expiredAssetIds.filterNot(_ <= 0),
        this.valueAdjustments.filterNot(_.asset.id <= 0))
    }
  }
}
