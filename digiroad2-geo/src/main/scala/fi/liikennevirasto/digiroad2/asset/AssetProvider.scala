package fi.liikennevirasto.digiroad2.asset

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.mtk.MtkRoadLink
import fi.liikennevirasto.digiroad2.user.User
import org.joda.time.LocalDate

trait AssetProvider {
  def getAssetById(assetId: Long): Option[AssetWithProperties]
  def getAssetByExternalId(assetId: Long): Option[AssetWithProperties]
  def getAssetPositionByExternalId(externalId: Long): Option[Point]
  def getAssetsByMunicipality(municipality: Int): Iterable[AssetWithProperties]
  def getAssetsByIds(ids: List[Long]): Seq[AssetWithProperties]
  def getAssets(user: User, bounds: Option[BoundingRectangle] = None, validFrom: Option[LocalDate] = None, validTo: Option[LocalDate] = None): Seq[Asset]
  def createAsset(assetTypeId: Long, lon: Double, lat: Double, roadLinkId: Long, bearing: Int, creator: String, properties: Seq[SimpleProperty]): AssetWithProperties
  def updateAsset(assetId: Long, position: Option[Position] = None, properties: Seq[SimpleProperty] = Seq()): AssetWithProperties
  def updateAssetByExternalId(externalId: Long, properties: Seq[SimpleProperty] = Seq()): AssetWithProperties
  def updateAssetByExternalIdLimitedByRoadType(externalId: Long, properties: Seq[SimpleProperty] = Seq(), roadTypeLimitations: Set[RoadLinkType]): Either[RoadLinkType, AssetWithProperties]
  def removeAsset(assetId: Long): Unit
  def getEnumeratedPropertyValues(assetTypeId: Long): Seq[EnumeratedPropertyValue]
  @deprecated def getRoadLinkById(roadLinkId: Long): Option[RoadLink]
  def getImage(imageId: Long): Array[Byte]
  def updateRoadLinks(roadlinks: Seq[MtkRoadLink]): Unit
  def availableProperties(assetTypeId: Long): Seq[Property]
  def assetPropertyNames(language: String): Map[String, String]
}
class AssetNotFoundException(externalId: Long) extends RuntimeException
class LRMPositionDeletionFailed(val reason: String) extends RuntimeException

case class BoundingRectangle(leftBottom: Point, rightTop: Point)