package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.asset.SideCode

object LinearAssetFiller {
  case class MValueAdjustment(assetId: Long, linkId: Long, startMeasure: Double, endMeasure: Double)
  case class VVHChangesAdjustment(assetId: Long, linkId: Long, startMeasure: Double, endMeasure: Double, vvhTimestamp: Long)
  case class SideCodeAdjustment(assetId: Long, sideCode: SideCode, typeId: Int)
  case class ValueAdjustment(asset: PersistedLinearAsset)
  case class ChangeSet(droppedAssetIds: Set[Long],
                       adjustedMValues: Seq[MValueAdjustment],
                       adjustedVVHChanges: Seq[VVHChangesAdjustment],
                       adjustedSideCodes: Seq[SideCodeAdjustment],
                       expiredAssetIds: Set[Long],
                       valueAdjustments: Seq[ValueAdjustment])
}
