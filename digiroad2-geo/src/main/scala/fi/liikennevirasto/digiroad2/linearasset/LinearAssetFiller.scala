package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.asset.SideCode
object LinearAssetFiller {
  case class MValueAdjustment(assetId: Long, linkId: String, startMeasure: Double, endMeasure: Double,timeStamp: Long=0,oldLinkId:Option[String] = None)
  case class VVHChangesAdjustment(assetId: Long, linkId: String, startMeasure: Double, endMeasure: Double, timeStamp: Long=0)
  case class SideCodeAdjustment(assetId: Long, sideCode: SideCode, typeId: Int,oldLinkId:Option[String] = None,oldId:Long = 0 )
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

  def cleanRedundantMValueAdjustments(changeSet: ChangeSet, originalAssets: Seq[PieceWiseLinearAsset]): ChangeSet = {
    val redundantFiltered = changeSet.adjustedMValues.filterNot(adjustment => {
      val originalAsset = originalAssets.find(_.id == adjustment.assetId).get
      originalAsset.startMeasure == adjustment.startMeasure && originalAsset.endMeasure == adjustment.endMeasure
    })
    changeSet.copy(adjustedMValues = redundantFiltered)
  }
  
  val emptyChangeSet = ChangeSet(droppedAssetIds = Set.empty[Long],
    expiredAssetIds = Set.empty[Long],
    adjustedMValues = Seq.empty[MValueAdjustment],
    adjustedVVHChanges = Seq.empty[VVHChangesAdjustment],
    adjustedSideCodes = Seq.empty[SideCodeAdjustment],
    valueAdjustments = Seq.empty[ValueAdjustment])
  def combineChangeSets: (ChangeSet, ChangeSet) => ChangeSet = (a, z) => {
    a.copy(
      droppedAssetIds = a.droppedAssetIds ++ z.droppedAssetIds,
      expiredAssetIds = (a.expiredAssetIds ++ z.expiredAssetIds),
      adjustedMValues = (a.adjustedMValues ++ z.adjustedMValues).distinct,
      adjustedVVHChanges = (a.adjustedVVHChanges ++ z.adjustedVVHChanges).distinct,
      adjustedSideCodes = (a.adjustedSideCodes ++ z.adjustedSideCodes).distinct,
      valueAdjustments = (a.valueAdjustments ++ z.valueAdjustments).distinct
    );
  }
}
