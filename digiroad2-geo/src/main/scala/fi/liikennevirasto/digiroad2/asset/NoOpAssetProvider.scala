package fi.liikennevirasto.digiroad2.asset

import org.joda.time.LocalDate
import fi.liikennevirasto.digiroad2.mtk.MtkRoadLink

class NoOpAssetProvider extends AssetProvider {
  def updateAssetProperty(assetId: Long, propertyId: String, propertyValues: Seq[PropertyValue]) {}
  def deleteAssetProperty(assetId: Long, propertyId: String) {}
  def getRoadLinks(municipalityNumber: Option[Int], bounds: Option[BoundingCircle]): Seq[RoadLink] = List()
  def getAssetById(assetId: Long): Option[Asset] = None
  def getAssets(assetTypeId: Long, municipalityNumber: Option[Long], bounds: Option[BoundingCircle], validFrom: Option[LocalDate], validTo: Option[LocalDate]): Seq[Asset] = List()
  def createAsset(assetTypeId: Long, lon: Double, lat: Double, roadLinkId: Long, creator: String) = null
  def getAssetTypes = List()
  def getEnumeratedPropertyValues(assetTypeId: Long): Seq[EnumeratedPropertyValue] = List()
  def updateAssetLocation(asset: Asset): Asset = asset
  def getImage(imageId: Long): Array[Byte] = new Array[Byte](0)
  def updateRoadLinks(roadlinks: Seq[MtkRoadLink]) { }
}