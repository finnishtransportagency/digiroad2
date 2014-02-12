package fi.liikennevirasto.digiroad2.asset

import org.joda.time.LocalDate
import fi.liikennevirasto.digiroad2.mtk.MtkRoadLink

trait AssetProvider {
  def getAssetTypes: Seq[AssetType]
  def getAssetById(assetId: Long): Option[Asset]
  def getAssets(assetTypeId: Long, municipalityNumbers: Seq[Long] = Nil, bounds: Option[BoundingCircle] = None, validFrom: Option[LocalDate] = None, validTo: Option[LocalDate] = None): Seq[ListedAsset]
  def createAsset(assetTypeId: Long, lon: Double, lat: Double, roadLinkId: Long, bearing: Int, creator: String): Asset
  def updateAssetProperty(assetId: Long, propertyId: String, propertyValues: Seq[PropertyValue])
  def deleteAssetProperty(assetId: Long, propertyId: String)
  def getEnumeratedPropertyValues(assetTypeId: Long): Seq[EnumeratedPropertyValue]
  def updateAssetLocation(asset: ListedAsset): Asset
  def getRoadLinks(municipalityNumbers: Seq[Long] = Seq(), bounds: Option[BoundingCircle] = None): Seq[RoadLink]
  def getRoadLinkById(roadLinkId: Long): Option[RoadLink]
  def getImage(imageId: Long): Array[Byte]
  def updateRoadLinks(roadlinks: Seq[MtkRoadLink]): Unit
  def availableProperties(assetTypeId: Long): Seq[Property]
}
case class BoundingCircle(centreLat: Double, centreLon: Double, radiusM: Double)
