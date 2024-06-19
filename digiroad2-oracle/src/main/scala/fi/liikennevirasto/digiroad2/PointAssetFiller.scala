package fi.liikennevirasto.digiroad2


object PointAssetFiller {
// DELETE ME
  case class AssetAdjustment(assetId: Long, lon: Double, lat: Double, linkId: String, mValue: Double, floating: Boolean, timeStamp: Long)
  private val MaxDistanceDiffAllowed = 3.0
  
}