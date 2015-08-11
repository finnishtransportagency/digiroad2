package fi.liikennevirasto.digiroad2.asset

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.user.User
import org.joda.time.LocalDate

trait AssetProvider {
  def getAssetById(assetId: Long): Option[AssetWithProperties]
  def getAssetByExternalId(assetId: Long): Option[AssetWithProperties]
  def getAssetPositionByExternalId(externalId: Long): Option[Point]
  def createAsset(assetTypeId: Long, lon: Double, lat: Double, roadLinkId: Long, bearing: Int, creator: String, properties: Seq[SimpleProperty]): AssetWithProperties
  def updateAsset(assetId: Long, position: Option[Position] = None, properties: Seq[SimpleProperty] = Seq()): AssetWithProperties
  def updateAssetByExternalIdLimitedByRoadType(externalId: Long, properties: Seq[SimpleProperty] = Seq(), roadTypeLimitations: Set[AdministrativeClass]): Either[AdministrativeClass, AssetWithProperties]
  def getEnumeratedPropertyValues(assetTypeId: Long): Seq[EnumeratedPropertyValue]
  def availableProperties(assetTypeId: Long): Seq[Property]
  def assetPropertyNames(language: String): Map[String, String]
}
class AssetNotFoundException(externalId: Long) extends RuntimeException
class LRMPositionDeletionFailed(val reason: String) extends RuntimeException

case class BoundingRectangle(leftBottom: Point, rightTop: Point)