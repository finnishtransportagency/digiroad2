package fi.liikennevirasto.digiroad2.service.pointasset

import fi.liikennevirasto.digiroad2.PointAssetFiller.AssetAdjustment
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.{BoundingRectangle, PropertyValue, SimplePointAssetProperty}
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.dao.pointasset.{DirectionalTrafficSign, Obstacle, OracleDirectionalTrafficSignDao, OracleObstacleDao}
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.user.User

case class IncomingObstacle(lon: Double, lat: Double, linkId: Long, propertyData: Set[SimplePointAssetProperty]) extends IncomingPointAsset

case class IncomingObstacleAsset(linkId: Long, mValue: Long, propertyData: Set[SimplePointAssetProperty])  extends IncomePointAsset


class ObstacleService(val roadLinkService: RoadLinkService) extends PointAssetOperations {
  type IncomingAsset = IncomingObstacle
  type PersistedAsset = Obstacle

  override def typeId: Int = 220
  val typePublicId = "esterakennelma"

  override def fetchPointAssets(queryFilter: String => String, roadLinks: Seq[RoadLinkLike]): Seq[Obstacle] = OracleObstacleDao.fetchByFilter(queryFilter)
  override def fetchPointAssetsWithExpired(queryFilter: String => String, roadLinks: Seq[RoadLinkLike]): Seq[Obstacle] = OracleObstacleDao.fetchByFilterWithExpired(queryFilter)

  override def setFloating(persistedAsset: Obstacle, floating: Boolean) = {
    persistedAsset.copy(floating = floating)
  }

  override def setAssetPosition(asset: IncomingObstacle, geometry: Seq[Point], mValue: Double): IncomingObstacle = {
    GeometryUtils.calculatePointFromLinearReference(geometry, mValue) match {
      case Some(point) =>
        asset.copy(lon = point.x, lat = point.y)
      case _ =>
        asset
    }
  }

  override def create(asset: IncomingObstacle, username: String, roadLink: RoadLink, newTransaction: Boolean): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(asset.lon, asset.lat), roadLink.geometry)
    if(newTransaction) {
      withDynTransaction {
        OracleObstacleDao.create(setAssetPosition(asset, roadLink.geometry, mValue), mValue, username, roadLink.municipalityCode, VVHClient.createVVHTimeStamp(), roadLink.linkSource)
      }
    } else {
        OracleObstacleDao.create(setAssetPosition(asset, roadLink.geometry, mValue), mValue, username, roadLink.municipalityCode, VVHClient.createVVHTimeStamp(), roadLink.linkSource)
    }
  }

  def createFromCoordinates(incomingObstacle: IncomingObstacle, roadLink: RoadLink, username: String, isFloating: Boolean): Long = {
    if(isFloating)
      createFloatingWithoutTransaction(incomingObstacle.copy(linkId = 0), username, roadLink)
    else {
      checkDuplicates(incomingObstacle) match {
        case Some(existingAsset) =>
          updateWithoutTransaction(existingAsset.id, incomingObstacle, roadLink, username, Some(existingAsset.mValue), Some(existingAsset.vvhTimeStamp))
        case _ =>
          create(incomingObstacle, username, roadLink, false)
      }
    }
  }

  def getProperty(incomingObstacle: IncomingObstacle, property: String) : Option[PropertyValue] = {
    incomingObstacle.propertyData.find(p => p.publicId == property).get.values.map(_.asInstanceOf[PropertyValue]).headOption
  }

  def checkDuplicates(incomingObstacle: IncomingObstacle): Option[Obstacle] = {
    val position = Point(incomingObstacle.lon, incomingObstacle.lat)

    val obstacleType = getProperty(incomingObstacle, typePublicId).get.propertyValue.toString
    val signsInRadius = OracleObstacleDao.fetchByFilter(withBoundingBoxFilter(position, TwoMeters)).filter(
      asset =>
        GeometryUtils.geometryLength(Seq(position, Point(asset.lon, asset.lat))) <= TwoMeters &&
          obstacleType == getProperty(asset, typePublicId).get.propertyValue.toString
    )
    if(signsInRadius.nonEmpty) Some(getLatestModifiedAsset(signsInRadius)) else None
  }

  def getLatestModifiedAsset(signs: Seq[Obstacle]): Obstacle = {
    signs.maxBy(sign => sign.modifiedAt.getOrElse(sign.createdAt.get).getMillis)
  }

  def createFloatingWithoutTransaction(asset: IncomingObstacle, username: String, roadLink: RoadLink): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(asset.lon, asset.lat), roadLink.geometry)
    OracleObstacleDao.create(setAssetPosition(asset, roadLink.geometry, mValue), mValue, username, roadLink.municipalityCode, VVHClient.createVVHTimeStamp(), roadLink.linkSource)
  }

  override def update(id: Long, updatedAsset: IncomingObstacle, roadLink: RoadLink, username: String): Long = {
    withDynTransaction {
      updateWithoutTransaction(id, updatedAsset, roadLink, username, None, None)
    }
  }

  def updateWithoutTransaction(id: Long, updatedAsset: IncomingObstacle, roadLink: RoadLink, username: String, mValue : Option[Double], vvhTimeStamp: Option[Long]): Long = {
    val value = mValue.getOrElse(GeometryUtils.calculateLinearReferenceFromPoint(Point(updatedAsset.lon, updatedAsset.lat), roadLink.geometry))
    getPersistedAssetsByIdsWithoutTransaction(Set(id)).headOption.getOrElse(throw new NoSuchElementException("Asset not found"))  match {
      case old if old.lat != updatedAsset.lat || old.lon != updatedAsset.lon =>
        expireWithoutTransaction(id)
        OracleObstacleDao.create(setAssetPosition(updatedAsset, roadLink.geometry, value), value, username, roadLink.municipalityCode, vvhTimeStamp.getOrElse(VVHClient.createVVHTimeStamp()), roadLink.linkSource, old.createdBy, old.createdAt)
      case _ =>
        OracleObstacleDao.update(id, setAssetPosition(updatedAsset, roadLink.geometry, value), value, username, roadLink.municipalityCode, Some(vvhTimeStamp.getOrElse(VVHClient.createVVHTimeStamp())), roadLink.linkSource)
    }
  }

  override def getByBoundingBox(user: User, bounds: BoundingRectangle) : Seq[PersistedAsset] = {
    val (roadLinks, changeInfo) = roadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(bounds)
    super.getByBoundingBox(user, bounds, roadLinks, changeInfo, floatingAdjustment(adjustmentOperation, createOperation))
  }

  private def createOperation(asset: PersistedAsset, adjustment: AssetAdjustment): PersistedAsset = {
    createPersistedAsset(asset, adjustment)
  }

  private def adjustmentOperation(persistedAsset: PersistedAsset, adjustment: AssetAdjustment, roadLink: RoadLink): Long = {
    val updated = IncomingObstacle(adjustment.lon, adjustment.lat, adjustment.linkId, persistedAsset.propertyData.map(prop => SimplePointAssetProperty(prop.publicId, prop.values)).toSet)
    updateWithoutTransaction(adjustment.assetId, updated, roadLink, "vvh_generated", Some(adjustment.mValue), Some(adjustment.vvhTimeStamp))
  }

  override def getByMunicipality(municipalityCode: Int): Seq[PersistedAsset] = {
    val (roadLinks, changeInfo) = roadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(municipalityCode)
    val mapRoadLinks = roadLinks.map(l => l.linkId -> l).toMap
    getByMunicipality(mapRoadLinks, roadLinks, changeInfo, floatingAdjustment(adjustmentOperation, createOperation), withMunicipality(municipalityCode))
  }

  private def createPersistedAsset[T](persistedStop: PersistedAsset, asset: AssetAdjustment) = {

    new PersistedAsset(asset.assetId, asset.linkId, asset.lon, asset.lat,
      asset.mValue, asset.floating, persistedStop.vvhTimeStamp, persistedStop.municipalityCode, persistedStop.propertyData, persistedStop.createdBy,
      persistedStop.createdAt, persistedStop.modifiedBy, persistedStop.modifiedAt, linkSource = persistedStop.linkSource)

  }

  def getFloatingObstacles(floating: Int, lastIdUpdate: Long, batchSize: Int): Seq[Obstacle] = {
    OracleObstacleDao.selectFloatings(floating, lastIdUpdate, batchSize)
  }

  def updateFloatingAsset(obstacleUpdated: Obstacle): Unit = {
    OracleObstacleDao.updateFloatingAsset(obstacleUpdated)
  }

  override def toIncomingAsset(asset: IncomePointAsset, link: RoadLink) : Option[IncomingObstacle] = {
     GeometryUtils.calculatePointFromLinearReference(link.geometry, asset.mValue).map {
       point =>  IncomingObstacle(point.x, point.y, link.linkId, asset.asInstanceOf[IncomingObstacle].propertyData)
     }
  }
}


