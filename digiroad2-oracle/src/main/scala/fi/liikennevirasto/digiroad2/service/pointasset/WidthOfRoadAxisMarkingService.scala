package fi.liikennevirasto.digiroad2.service.pointasset

import fi.liikennevirasto.digiroad2.PointAssetFiller.AssetAdjustment
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.{BoundingRectangle, PropertyValue, SimplePointAssetProperty, WidthOfRoadAxisMarkings}
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.dao.pointasset._
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.user.User

case class IncomingWidthOfRoadAxisMarking(lon: Double, lat: Double, linkId: Long, propertyData: Set[SimplePointAssetProperty]) extends IncomingPointAsset

class WidthOfRoadAxisMarkingService(val roadLinkService: RoadLinkService) extends PointAssetOperations {
  type IncomingAsset = IncomingWidthOfRoadAxisMarking
  type PersistedAsset = WidthOfRoadAxisMarking

  override def typeId: Int = WidthOfRoadAxisMarkings.typeId
  val regulationNumberPublicId = "widthOfRoadAxisMarking_regulation_number"

  override def fetchPointAssets(queryFilter: String => String, roadLinks: Seq[RoadLinkLike]): Seq[WidthOfRoadAxisMarking] = OracleWidthOfRoadAxisMarkingDao.fetchByFilter(queryFilter)
  override def fetchPointAssetsWithExpired(queryFilter: String => String, roadLinks: Seq[RoadLinkLike]): Seq[WidthOfRoadAxisMarking] = OracleWidthOfRoadAxisMarkingDao.fetchByFilterWithExpired(queryFilter)
  override def fetchPointAssetsWithExpiredLimited(queryFilter: String => String, token: Option[String]): Seq[WidthOfRoadAxisMarking] = OracleWidthOfRoadAxisMarkingDao.fetchByFilterWithExpiredLimited(queryFilter, token)

  override def setFloating(persistedAsset: WidthOfRoadAxisMarking, floating: Boolean) : WidthOfRoadAxisMarking = {
    persistedAsset.copy(floating = floating)
  }

  override def setAssetPosition(asset: IncomingWidthOfRoadAxisMarking, geometry: Seq[Point], mValue: Double): IncomingWidthOfRoadAxisMarking = {
    GeometryUtils.calculatePointFromLinearReference(geometry, mValue) match {
      case Some(point) =>
        asset.copy(lon = point.x, lat = point.y)
      case _ =>
        asset
    }
  }

  override def create(asset: IncomingWidthOfRoadAxisMarking, username: String, roadLink: RoadLink, newTransaction: Boolean): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(asset.lon, asset.lat), roadLink.geometry)
    if(newTransaction) {
      withDynTransaction {
        OracleWidthOfRoadAxisMarkingDao.create(setAssetPosition(asset, roadLink.geometry, mValue), mValue, username, roadLink.municipalityCode, VVHClient.createVVHTimeStamp(), roadLink.linkSource)
      }
    } else {
      OracleWidthOfRoadAxisMarkingDao.create(setAssetPosition(asset, roadLink.geometry, mValue), mValue, username, roadLink.municipalityCode, VVHClient.createVVHTimeStamp(), roadLink.linkSource)
    }
  }

  def createFromCoordinates(incomingWidthOfRoadAxisMarking: IncomingWidthOfRoadAxisMarking, roadLink: RoadLink, username: String, isFloating: Boolean): Long = {
    if(isFloating)
      createFloatingWithoutTransaction(incomingWidthOfRoadAxisMarking.copy(linkId = 0), username, roadLink)
    else {
      checkDuplicates(incomingWidthOfRoadAxisMarking) match {
        case Some(existingAsset) =>
          updateWithoutTransaction(existingAsset.id, incomingWidthOfRoadAxisMarking, roadLink, username, Some(existingAsset.mValue), Some(existingAsset.vvhTimeStamp))
        case _ =>
          create(incomingWidthOfRoadAxisMarking, username, roadLink, false)
      }
    }
  }

  def checkDuplicates(incomingWidthOfRoadAxisMarking: IncomingWidthOfRoadAxisMarking, withDynSession: Boolean = false): Option[WidthOfRoadAxisMarking] = {
    val position = Point(incomingWidthOfRoadAxisMarking.lon, incomingWidthOfRoadAxisMarking.lat)
    val widthOfRoadAxisMarkingType = getProperty(incomingWidthOfRoadAxisMarking.propertyData.toSeq, regulationNumberPublicId).get.propertyValue.toString
    val widthOfRoadAxisInRadius = OracleWidthOfRoadAxisMarkingDao.fetchByFilter(withBoundingBoxFilter(position, TwoMeters), withDynSession).filter(
      asset =>
        GeometryUtils.geometryLength(Seq(position, Point(asset.lon, asset.lat))) <= TwoMeters &&
          widthOfRoadAxisMarkingType == getProperty(asset.propertyData, regulationNumberPublicId).get.propertyValue.toString
    )

    if (widthOfRoadAxisInRadius.nonEmpty) Some(getLatestModifiedAsset(widthOfRoadAxisInRadius).asInstanceOf[WidthOfRoadAxisMarking]) else None
  }

  def createFloatingWithoutTransaction(asset: IncomingWidthOfRoadAxisMarking, username: String, roadLink: RoadLink): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(asset.lon, asset.lat), roadLink.geometry)
    OracleWidthOfRoadAxisMarkingDao.create(setAssetPosition(asset, roadLink.geometry, mValue), mValue, username, roadLink.municipalityCode, VVHClient.createVVHTimeStamp(), roadLink.linkSource)
  }

  override def update(id: Long, updatedAsset: IncomingWidthOfRoadAxisMarking, roadLink: RoadLink, username: String): Long = {
    withDynTransaction {
      updateWithoutTransaction(id, updatedAsset, roadLink, username, None, None)
    }
  }

  def updateWithoutTransaction(id: Long, updatedAsset: IncomingWidthOfRoadAxisMarking, roadLink: RoadLink, username: String, mValue: Option[Double], vvhTimeStamp: Option[Long]): Long = {
    val value = mValue.getOrElse(GeometryUtils.calculateLinearReferenceFromPoint(Point(updatedAsset.lon, updatedAsset.lat), roadLink.geometry))
    getPersistedAssetsByIdsWithoutTransaction(Set(id)).headOption.getOrElse(throw new NoSuchElementException("Asset not found")) match {
      case old if old.lat != updatedAsset.lat || old.lon != updatedAsset.lon =>
        expireWithoutTransaction(id)
        OracleWidthOfRoadAxisMarkingDao.create(setAssetPosition(updatedAsset, roadLink.geometry, value), value, username, roadLink.municipalityCode, vvhTimeStamp.getOrElse(VVHClient.createVVHTimeStamp()), roadLink.linkSource, old.createdBy, old.createdAt)
      case _ =>
        OracleWidthOfRoadAxisMarkingDao.update(id, setAssetPosition(updatedAsset, roadLink.geometry, value), value, username, roadLink.municipalityCode, Some(vvhTimeStamp.getOrElse(VVHClient.createVVHTimeStamp())), roadLink.linkSource)
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
    val updated = IncomingWidthOfRoadAxisMarking(adjustment.lon, adjustment.lat, adjustment.linkId, persistedAsset.propertyData.map(prop => SimplePointAssetProperty(prop.publicId, prop.values)).toSet)
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

  def getFloatingWidthOfRoadAxisMarkings(floating: Int, lastIdUpdate: Long, batchSize: Int): Seq[WidthOfRoadAxisMarking] = {
    OracleWidthOfRoadAxisMarkingDao.selectFloatings(floating, lastIdUpdate, batchSize)
  }

  def updateFloatingAsset(widthOfRoadAxisMarkingUpdated: WidthOfRoadAxisMarking): Unit = {
    OracleWidthOfRoadAxisMarkingDao.updateFloatingAsset(widthOfRoadAxisMarkingUpdated)
  }

  override def toIncomingAsset(asset: IncomePointAsset, link: RoadLink) : Option[IncomingWidthOfRoadAxisMarking] = {
    GeometryUtils.calculatePointFromLinearReference(link.geometry, asset.mValue).map {
      point =>  IncomingWidthOfRoadAxisMarking(point.x, point.y, link.linkId, asset.asInstanceOf[IncomingWidthOfRoadAxisMarking].propertyData)
    }
  }
}


