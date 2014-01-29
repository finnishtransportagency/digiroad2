package fi.liikennevirasto.digiroad2.asset

import org.joda.time.LocalDate
import fi.liikennevirasto.digiroad2.mtk.MtkRoadLink

trait AssetProvider {
  def getAssetTypes: Seq[AssetType]
  def getAssetById(assetId: Long): Option[Asset]
  def getAssets(assetTypeId: Long, municipalityNumber: Option[Long] = None, bounds: Option[BoundingCircle] = None, validFrom: Option[LocalDate] = None, validTo: Option[LocalDate] = None): Seq[Asset]
  def createAsset(assetTypeId: Long, lon: Double, lat: Double, roadLinkId: Long, creator: String): Asset
  def updateAssetProperty(assetId: Long, propertyId: String, propertyValues: Seq[PropertyValue])
  def deleteAssetProperty(assetId: Long, propertyId: String)
  def getEnumeratedPropertyValues(assetTypeId: Long): Seq[EnumeratedPropertyValue]
  def updateAssetLocation(asset: Asset): Asset
  def getRoadLinks(municipalityNumber: Option[Int] = None, bounds: Option[BoundingCircle] = None): Seq[RoadLink]
  def getRoadLinkById(roadLinkId: Long): Option[RoadLink]
  def getImage(imageId: Long): Array[Byte]
  def updateRoadLinks(roadlinks: Seq[MtkRoadLink]): Unit
}
case class BoundingCircle(centreLat: Double, centreLon: Double, radiusM: Double)
