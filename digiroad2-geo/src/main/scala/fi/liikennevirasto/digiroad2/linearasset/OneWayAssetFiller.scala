package fi.liikennevirasto.digiroad2.linearasset

class OneWayAssetFiller extends AssetFiller {
  override protected def combineEqualValues(segmentPieces: Seq[SegmentPiece], segments: Seq[PieceWiseLinearAsset]): Seq[SegmentPiece] = {
    if(segmentPieces.size < 2)
      return segmentPieces

    super.combineEqualValues(segmentPieces, segments)
  }
}
