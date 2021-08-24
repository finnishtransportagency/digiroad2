package fi.liikennevirasto.digiroad2.service.pointasset

import fi.liikennevirasto.digiroad2.PointAssetFiller.AssetAdjustment
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.{BoundingRectangle, PropertyValue, SimplePointAssetProperty}
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.dao.pointasset.{PostGISRailwayCrossingDao, RailwayCrossing}
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.user.User
import org.joda.time.DateTime

case class IncomingRailwayCrossing(lon: Double, lat: Double, linkId: Long, propertyData: Set[SimplePointAssetProperty]) extends IncomingPointAsset
case class IncomingRailwayCrossingtAsset(linkId: Long, mValue: Long, propertyData: Set[SimplePointAssetProperty])  extends IncomePointAsset

class RailwayCrossingService(val roadLinkService: RoadLinkService) extends PointAssetOperations {
  type IncomingAsset = IncomingRailwayCrossing
  type PersistedAsset = RailwayCrossing

  override def typeId: Int = 230

  val namePublicId = "rautatien_tasoristeyksen_nimi"
  val safetyEquipmentPublicId = "turvavarustus"
  val codePublicId = "tasoristeystunnus"

  override def setAssetPosition(asset: IncomingRailwayCrossing, geometry: Seq[Point], mValue: Double): IncomingRailwayCrossing = {
    GeometryUtils.calculatePointFromLinearReference(geometry, mValue) match {
      case Some(point) =>
        asset.copy(lon = point.x, lat = point.y)
      case _ =>
        asset
    }
  }

  override def fetchPointAssets(queryFilter: String => String, roadLinks: Seq[RoadLinkLike]): Seq[RailwayCrossing] = PostGISRailwayCrossingDao.fetchByFilter(queryFilter)

  override def setFloating(persistedAsset: RailwayCrossing, floating: Boolean) = {
    persistedAsset.copy(floating = floating)
  }

  override def getByBoundingBox(user: User, bounds: BoundingRectangle) : Seq[PersistedAsset] = {
    val (roadLinks, changeInfo) = roadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(bounds)
    super.getByBoundingBox(user, bounds, roadLinks, changeInfo, floatingAdjustment(adjustmentOperation, createOperation))
  }

  private def createOperation(asset: PersistedAsset, adjustment: AssetAdjustment): PersistedAsset = {
    createPersistedAsset(asset, adjustment)
  }

  private def adjustmentOperation(persistedAsset: PersistedAsset, adjustment: AssetAdjustment, roadLink: RoadLink): Long = {
    val updated = IncomingRailwayCrossing(adjustment.lon, adjustment.lat, adjustment.linkId, persistedAsset.propertyData.map(prop => SimplePointAssetProperty(prop.publicId, prop.values)).toSet)
    updateWithoutTransaction(adjustment.assetId, updated, roadLink, "vvh_generated",
                             Some(adjustment.mValue), Some(adjustment.vvhTimeStamp))
  }

  override def getByMunicipality(municipalityCode: Int): Seq[PersistedAsset] = {
    val (roadLinks, changeInfo) = roadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(municipalityCode)
    val mapRoadLinks = roadLinks.map(l => l.linkId -> l).toMap
    getByMunicipality(mapRoadLinks, roadLinks, changeInfo, floatingAdjustment(adjustmentOperation, createOperation), withMunicipality(municipalityCode))
  }

  private def createPersistedAsset[T](persistedStop: PersistedAsset, asset: AssetAdjustment) = {

    new PersistedAsset(asset.assetId, asset.linkId, asset.lon, asset.lat,
      asset.mValue, asset.floating, persistedStop.vvhTimeStamp, persistedStop.municipalityCode, persistedStop.propertyData,
      persistedStop.createdBy, persistedStop.createdAt, persistedStop.modifiedBy, persistedStop.modifiedAt, persistedStop.linkSource)
  }

  def createFromCoordinates(incomingRailwayCrossing: IncomingRailwayCrossing, roadLink: RoadLink, username: String, isFloating: Boolean): Long = {
    if(isFloating)
      createFloatingWithoutTransaction(incomingRailwayCrossing.copy(linkId = 0), username, roadLink)
    else {
      checkDuplicates(incomingRailwayCrossing) match {
        case Some(existingAsset) =>
          updateWithoutTransaction(existingAsset.id, incomingRailwayCrossing, roadLink, username, Some(existingAsset.mValue), Some(existingAsset.vvhTimeStamp))
        case _ =>
          create(incomingRailwayCrossing, username, roadLink, false)
      }
    }
  }

  def getProperty(incomingRailwayCrossing: IncomingRailwayCrossing, property: String) : Option[PropertyValue] = {
    incomingRailwayCrossing.propertyData.find(p => p.publicId == property).get.values.map(_.asInstanceOf[PropertyValue]).headOption
  }

  def checkDuplicates(incomingRailwayCrossing: IncomingRailwayCrossing): Option[RailwayCrossing] = {
    val position = Point(incomingRailwayCrossing.lon, incomingRailwayCrossing.lat)
    val safetyEquipmentType = getProperty(incomingRailwayCrossing, safetyEquipmentPublicId).get.propertyValue.toString
    val signsInRadius = PostGISRailwayCrossingDao.fetchByFilter(withBoundingBoxFilter(position, TwoMeters)).filter(
      asset =>
        GeometryUtils.geometryLength(Seq(position, Point(asset.lon, asset.lat))) <= TwoMeters &&
          safetyEquipmentType == getProperty(asset.propertyData, safetyEquipmentPublicId).get.propertyValue.toString
    )
    if(signsInRadius.nonEmpty) Some(getLatestModifiedAsset(signsInRadius)) else None
  }

  def getLatestModifiedAsset(signs: Seq[RailwayCrossing]): RailwayCrossing = {
    signs.maxBy(sign => sign.modifiedAt.getOrElse(sign.createdAt.get).getMillis)
  }

  def createFloatingWithoutTransaction(asset: IncomingRailwayCrossing, username: String, roadLink: RoadLink): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(asset.lon, asset.lat), roadLink.geometry)
    PostGISRailwayCrossingDao.create(setAssetPosition(asset, roadLink.geometry, mValue), mValue, roadLink.municipalityCode, username, VVHClient.createVVHTimeStamp(), roadLink.linkSource)
  }

  override def create(asset: IncomingRailwayCrossing, username: String, roadLink: RoadLink, newTransaction: Boolean): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(asset.lon, asset.lat), roadLink.geometry)
    if(newTransaction) {
      withDynTransaction {
        PostGISRailwayCrossingDao.create(setAssetPosition(asset, roadLink.geometry, mValue), mValue, roadLink.municipalityCode, username, VVHClient.createVVHTimeStamp(), roadLink.linkSource)
      }
    } else {
      PostGISRailwayCrossingDao.create(setAssetPosition(asset, roadLink.geometry, mValue), mValue, roadLink.municipalityCode, username, VVHClient.createVVHTimeStamp(), roadLink.linkSource)
    }
  }

  override def update(id: Long, updatedAsset: IncomingRailwayCrossing, roadLink: RoadLink, username: String): Long = {
    withDynTransaction {
      updateWithoutTransaction(id, updatedAsset, roadLink, username, None, None)
    }
  }

  def updateWithoutTransaction(id: Long, updatedAsset: IncomingRailwayCrossing,roadLink: RoadLink, username: String, mValue : Option[Double], vvhTimeStamp: Option[Long]): Long = {
    val value = mValue.getOrElse(GeometryUtils.calculateLinearReferenceFromPoint(Point(updatedAsset.lon, updatedAsset.lat), roadLink.geometry))
    getPersistedAssetsByIdsWithoutTransaction(Set(id)).headOption.getOrElse(throw new NoSuchElementException("Asset not found")) match {
      case old if  old.lat != updatedAsset.lat || old.lon != updatedAsset.lon=>
        expireWithoutTransaction(id)
        PostGISRailwayCrossingDao.create(setAssetPosition(updatedAsset, roadLink.geometry, value), value, roadLink.municipalityCode, username, vvhTimeStamp.getOrElse(VVHClient.createVVHTimeStamp()), roadLink.linkSource, old.createdBy, old.createdAt)
      case _ =>
        PostGISRailwayCrossingDao.update(id, setAssetPosition(updatedAsset,roadLink.geometry, value), value, roadLink.municipalityCode, username, Some(vvhTimeStamp.getOrElse(VVHClient.createVVHTimeStamp())), roadLink.linkSource)
    }
  }

  override def toIncomingAsset(asset: IncomePointAsset, link: RoadLink) : Option[IncomingRailwayCrossing] = {
    GeometryUtils.calculatePointFromLinearReference(link.geometry, asset.mValue).map {
      point =>  IncomingRailwayCrossing(point.x, point.y, link.linkId, asset.asInstanceOf[IncomingRailwayCrossingtAsset].propertyData)
    }
  }

  def getCodeMaxSize : Long  =   withDynTransaction { PostGISRailwayCrossingDao.getCodeMaxSize }

  override def getChanged(sinceDate: DateTime, untilDate: DateTime, token: Option[String] = None): Seq[ChangedPointAsset] = { throw new UnsupportedOperationException("Not Supported Method") }

  override def fetchPointAssetsWithExpired(queryFilter: String => String, roadLinks: Seq[RoadLinkLike]): Seq[RailwayCrossing] =  { throw new UnsupportedOperationException("Not Supported Method") }

  override def fetchPointAssetsWithExpiredLimited(queryFilter: String => String, token: Option[String]): Seq[RailwayCrossing] = { throw new UnsupportedOperationException("Not Supported Method") }
}


