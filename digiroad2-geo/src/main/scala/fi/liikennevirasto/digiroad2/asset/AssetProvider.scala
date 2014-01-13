package fi.liikennevirasto.digiroad2.asset

import fi.liikennevirasto.digiroad2.mtk.{Point, MtkRoadLink}

trait AssetProvider {
  def getAssetTypes: Seq[AssetType]
  def getAssetById(assetId: Long): Option[Asset]
  def getAssets(assetTypeId: Long, municipalityNumber: Option[Long] = None, bounds: Option[BoundingCircle] = None): Seq[Asset]
  def updateAssetProperty(assetId: Long, propertyId: String, propertyValues: Seq[PropertyValue])
  def deleteAssetProperty(assetId: Long, propertyId: String)
  def getEnumeratedPropertyValues(assetTypeId: Long): Seq[EnumeratedPropertyValue]
  def updateAssetLocation(asset: Asset): Asset
  def getRoadLinks(municipalityNumber: Option[Int] = None): Seq[RoadLink]
  def getImage(imageId: Long): Array[Byte]
  def updateRoadLinks(roadlinks: Seq[MtkRoadLink]): Unit
}
case class BoundingCircle(centreX: Double, centreY: Double, radius: Double)
