package fi.liikennevirasto.digiroad2.service.pointasset

import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.{AutoGeneratedUsername, BoundingRectangle, LinkGeomSource, PropertyValue, SimplePointAssetProperty}
import fi.liikennevirasto.digiroad2.client.RoadLinkInfo
import fi.liikennevirasto.digiroad2.dao.pointasset.{PostGISTrafficLightDao, TrafficLight}
import fi.liikennevirasto.digiroad2.linearasset.{LinkId, RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.user.User
import org.joda.time.DateTime

case class IncomingTrafficLight(lon: Double, lat: Double, linkId: String, propertyData: Set[SimplePointAssetProperty], validityDirection: Option[Int] = None, bearing: Option[Int] = None, mValue: Option[Double] = None) extends IncomingPointAsset
case class IncomingTrafficLightAsset(linkId: String, mValue: Long, propertyData: Set[SimplePointAssetProperty]) extends IncomePointAsset

class TrafficLightService(val roadLinkService: RoadLinkService) extends PointAssetOperations {
  type IncomingAsset = IncomingTrafficLight
  type PersistedAsset = TrafficLight

  override def typeId: Int = 280
  override def fetchPointAssetsWithExpiredLimited(queryFilter: String => String, token: Option[String]): Seq[TrafficLight] = throw new UnsupportedOperationException("Not Supported Method")
  override def setAssetPosition(asset: IncomingTrafficLight, geometry: Seq[Point], mValue: Double): IncomingTrafficLight = {
    GeometryUtils.calculatePointFromLinearReference(geometry, mValue) match {
      case Some(point) =>
        asset.copy(lon = point.x, lat = point.y)
      case _ =>
        asset
    }
  }

  override def update(id: Long, updatedAsset: IncomingTrafficLight, roadLink: RoadLink, username: String): Long = {
    withDynTransaction {
      updateWithoutTransaction(id, updatedAsset, roadLink, username, updatedAsset.mValue, None)
    }
  }

  def updateWithoutTransaction(id: Long, updatedAsset: IncomingTrafficLight, roadLink: RoadLink, username: String, mValue : Option[Double], timeStamp: Option[Long]): Long = {
    updateWithoutTransaction(id, updatedAsset, roadLink.geometry, roadLink.municipalityCode, username, mValue,
                             timeStamp, roadLink.linkSource)
  }

  def updateWithoutTransaction(id: Long, updatedAsset: IncomingTrafficLight, linkGeometry: Seq[Point], municipality: Int,
                               username: String, mValue: Option[Double], timeStamp: Option[Long],
                               linkSource: LinkGeomSource, fromPointAssetUpdater: Boolean = false): Long = {
    val value = mValue.getOrElse(GeometryUtils.calculateLinearReferenceFromPoint(Point(updatedAsset.lon, updatedAsset.lat), linkGeometry))
    getPersistedAssetsByIdsWithoutTransaction(Set(id)).headOption.getOrElse(throw new NoSuchElementException("Asset not found")) match {
      case old if (old.lat != updatedAsset.lat || old.lon != updatedAsset.lon) && !fromPointAssetUpdater =>
        expireWithoutTransaction(id)
        PostGISTrafficLightDao.create(setAssetPosition(updatedAsset, linkGeometry, value), value, username, municipality,
          timeStamp.getOrElse(createTimeStamp()), linkSource, old.createdBy, old.createdAt, old.externalIds, fromPointAssetUpdater, old.modifiedBy, old.modifiedAt)
      case _ =>
        PostGISTrafficLightDao.update(id, updatedAsset, value, username, municipality,
          Some(timeStamp.getOrElse(createTimeStamp())), linkSource, fromPointAssetUpdater)
    }
  }

  def createFromCoordinates(incomingTrafficLight: IncomingTrafficLight, roadLink: RoadLink, username: String, isFloating: Boolean): Long = {
    if(isFloating)
      createFloatingWithoutTransaction(incomingTrafficLight.copy(linkId = LinkId.Unknown.value), username, roadLink)
    else
      create(incomingTrafficLight, username, roadLink, false)
  }

  def getLatestModifiedAsset(signs: Seq[TrafficLight]): TrafficLight = {
    signs.maxBy(sign => sign.modifiedAt.getOrElse(sign.createdAt.get).getMillis)
  }

  def createFloatingWithoutTransaction(asset: IncomingTrafficLight, username: String, roadLink: RoadLink): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(asset.lon, asset.lat), roadLink.geometry)
    PostGISTrafficLightDao.create(setAssetPosition(asset, roadLink.geometry, mValue), mValue, username, roadLink.municipalityCode, createTimeStamp(), roadLink.linkSource)
  }

  override def setFloating(persistedAsset: TrafficLight, floating: Boolean) = {
    persistedAsset.copy(floating = floating)
  }

  override def fetchPointAssets(queryFilter: (String) => String, roadLinks: Seq[RoadLinkLike]): Seq[TrafficLight] = {
    PostGISTrafficLightDao.fetchByFilter(queryFilter)
  }

  override def create(asset: IncomingTrafficLight, username: String, roadLink: RoadLink, newTransaction: Boolean): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(asset.lon, asset.lat), roadLink.geometry)
    if(newTransaction) {
      withDynTransaction {
        PostGISTrafficLightDao.create(setAssetPosition(asset, roadLink.geometry, mValue), mValue, username, roadLink.municipalityCode, createTimeStamp(), roadLink.linkSource)
      }
    } else {
      PostGISTrafficLightDao.create(setAssetPosition(asset, roadLink.geometry, mValue), mValue, username, roadLink.municipalityCode, createTimeStamp(), roadLink.linkSource)
    }
  }

  override def getByBoundingBox(user: User, bounds: BoundingRectangle) : Seq[PersistedAsset] = {
    val roadLinks = roadLinkService.getRoadLinksWithComplementary(bounds,asyncMode = false)
    super.getByBoundingBox(user, bounds, roadLinks)
  }

  override def createOperation(asset: PersistedAsset, adjustment: AssetUpdate): PersistedAsset = {
    new PersistedAsset(adjustment.assetId, adjustment.linkId, adjustment.lon, adjustment.lat, adjustment.mValue,
      adjustment.floating, asset.timeStamp, asset.municipalityCode, asset.propertyData, asset.createdBy,
      asset.createdAt, asset.modifiedBy, asset.modifiedAt, asset.linkSource, asset.externalIds)
  }

  override def adjustmentOperation(persistedAsset: PersistedAsset, adjustment: AssetUpdate, roadLink: RoadLinkInfo): Long = {
    val propertyData = persistedAsset.propertyData.map(property =>
      SimplePointAssetProperty(property.publicId, property.values, property.groupedId)
      ).toSet
    val updated = IncomingTrafficLight(adjustment.lon, adjustment.lat, adjustment.linkId, propertyData)
    updateWithoutTransaction(adjustment.assetId, updated, roadLink.geometry, roadLink.municipality.getOrElse(throw new NoSuchElementException(s"${roadLink.linkId} does not have municipality code")), AutoGeneratedUsername.generatedInUpdate,
                             Some(adjustment.mValue), Some(adjustment.timeStamp), persistedAsset.linkSource, true)
  }

  override def toIncomingAsset(asset: IncomePointAsset, link: RoadLink) : Option[IncomingTrafficLight] = {
    GeometryUtils.calculatePointFromLinearReference(link.geometry, asset.mValue).map {
      point =>  IncomingTrafficLight(point.x, point.y, link.linkId, asset.asInstanceOf[IncomingTrafficLight].propertyData)
    }
  }

  def fetchSuitableForMergeByRadius(centralPoint: Point, radius: Int): Seq[TrafficLight] = {
    val maxTrafficLights = 6
    val existingTrafficLights = PostGISTrafficLightDao.fetchByRadius(centralPoint, radius)
    existingTrafficLights.filter { trafficLight => trafficLight.propertyData.count(_.publicId == "trafficLight_type") < maxTrafficLights && !trafficLight.propertyData.exists(_.groupedId == 0 )}
  }

  override def getChanged(sinceDate: DateTime, untilDate: DateTime, token: Option[String] = None): Seq[ChangedPointAsset] = { throw new UnsupportedOperationException("Not Supported Method") }

  override def fetchPointAssetsWithExpired(queryFilter: String => String, roadLinks: Seq[RoadLinkLike]): Seq[TrafficLight] =  { throw new UnsupportedOperationException("Not Supported Method") }
}
