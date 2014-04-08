package fi.liikennevirasto.digiroad2.asset

import org.joda.time.LocalDate
import fi.liikennevirasto.digiroad2.mtk.MtkRoadLink
import fi.liikennevirasto.digiroad2.user.User

trait AssetProvider {
  def getAssetTypes: Seq[AssetType]
  def getAssetById(assetId: Long): Option[AssetWithProperties]
  def getAssetByExternalId(assetId: Long): Option[AssetWithProperties]
  def getAssetPositionByExternalId(externalId: Long): Option[(Double, Double)]
  def getAssetsByMunicipality(municipality: Int): Iterable[AssetWithProperties]
  def getAssets(assetTypeId: Long, user: User, bounds: Option[BoundingRectangle] = None, validFrom: Option[LocalDate] = None, validTo: Option[LocalDate] = None): Seq[Asset]
  def createAsset(assetTypeId: Long, lon: Double, lat: Double, roadLinkId: Long, bearing: Int, creator: String, properties: Seq[SimpleProperty]): AssetWithProperties
  def updateAssetProperty(assetId: Long, propertyId: String, propertyValues: Seq[PropertyValue])
  def deleteAssetProperty(assetId: Long, propertyId: String)
  def getEnumeratedPropertyValues(assetTypeId: Long): Seq[EnumeratedPropertyValue]
  def updateAssetLocation(id: Long, lon: Double, lat: Double, roadLinkId: Long, bearing: Option[Int]): AssetWithProperties
  def getRoadLinks(user: User, bounds: Option[BoundingRectangle] = None): Seq[RoadLink]
  def getRoadLinkById(roadLinkId: Long): Option[RoadLink]
  def getImage(imageId: Long): Array[Byte]
  def updateRoadLinks(roadlinks: Seq[MtkRoadLink]): Unit
  def availableProperties(assetTypeId: Long): Seq[Property]
  def assetPropertyNames(language: String): Map[String, String]
}
case class Point(x: Double, y: Double)
case class BoundingRectangle(leftBottom: Point, rightTop: Point)