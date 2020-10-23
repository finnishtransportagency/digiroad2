package fi.liikennevirasto.digiroad2.service.pointasset


import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.{BoundingRectangle, OtherRoadwayMarkings, PropertyValue, SimplePointAssetProperty}
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.dao.pointasset._
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.user.User



case class IncomingOtherRoadwayMarking(lon: Double, lat: Double, linkId: Long, propertyData: Set[SimplePointAssetProperty]) extends IncomingPointAsset

class OtherRoadwayMarkingService(val roadLinkService: RoadLinkService) extends PointAssetOperations {
  type IncomingAsset = IncomingOtherRoadwayMarking
  type PersistedAsset = OtherRoadwayMarking

  override def typeId: Int = OtherRoadwayMarkings.typeId
  val regulationNumberPublicId = "otherRoadwayMarking_regulation_number"

  override def fetchPointAssets(queryFilter: String => String, roadLinks: Seq[RoadLinkLike]): Seq[OtherRoadwayMarking] = OracleOtherRoadwayMarkingDao.fetchByFilter(queryFilter)

  override def fetchPointAssetsWithExpired(queryFilter: String => String, roadLinks: Seq[RoadLinkLike]): Seq[OtherRoadwayMarking] = OracleOtherRoadwayMarkingDao.fetchByFilterWithExpired(queryFilter)

  override def fetchPointAssetsWithExpiredLimited(queryFilter: String => String, token: Option[String]): Seq[OtherRoadwayMarking] = OracleOtherRoadwayMarkingDao.fetchByFilterWithExpiredLimited(queryFilter, token)

  override def setFloating(persistedAsset: OtherRoadwayMarking, floating: Boolean) : OtherRoadwayMarking = {
    persistedAsset.copy(floating = floating)
  }

  override def setAssetPosition(asset: IncomingOtherRoadwayMarking, geometry: Seq[Point], mValue: Double): IncomingOtherRoadwayMarking = {
    GeometryUtils.calculatePointFromLinearReference(geometry, mValue) match {
      case Some(point) =>
        asset.copy(lon = point.x, lat = point.y)
      case _ =>
        asset
    }
  }

  override def create(asset: IncomingOtherRoadwayMarking, username: String, roadLink: RoadLink, newTransaction: Boolean): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(asset.lon,asset.lat), roadLink.geometry)
    if(newTransaction) {
      withDynTransaction {
        OracleOtherRoadwayMarkingDao.create(setAssetPosition(asset, roadLink.geometry, mValue), mValue, username, roadLink.municipalityCode, VVHClient.createVVHTimeStamp(), roadLink.linkSource)
      }
    } else {
       OracleOtherRoadwayMarkingDao.create(setAssetPosition(asset, roadLink.geometry, mValue), mValue, username, roadLink.municipalityCode, VVHClient.createVVHTimeStamp(), roadLink.linkSource)
    }
  }

  /*def createFromCoordinates(incomingOtherRoadwayMarking: IncomingOtherRoadwayMarking, roadLink: RoadLink, username: String, isFloating: Boolean): Long = {
    if(isFloating)
      createFloatingWithoutTransaction(incomingOtherRoadwayMarking.copy(linkId = 0), username, roadLink)
    else {
      CheckDuplicates(incomingOtherRoadwayMarking) match {
        case Some(existingAsset) =>
          updateWithoutTransaction(existingAsset.id, incomingOtherRoadwayMarking, roadLink, username, Some(existingAsset.mValue), Some(existingAsset.vvhTimeStamp))
        case _ =>
          create(incomingOtherRoadwayMarking, username, roadLink, false)
      }
    }
    }
    */


  // tarkistetaan onko duplikaatteja
  /**def checkDuplicates(incomingOtherRoadwayMarking: IncomingOtherRoadwayMarking, withDynSession: Boolean = false): Option[OtherRoadwayMarking] = {
    *val position = Point(incomingOtherRoadwayMarking.lon, incomingOtherRoadwayMarking.lat)
    *val otherRoadwayMarkingType = getProperty(incomingOtherRoadwayMarking.propertyData.toSeq, regulationNumberPublicId).get.propertyValue.toString
    *val otherRoadwayInRadius = OracleOtherRoadwayMarkingDao.fetchByFilter(withBoundingBoxFilter(position, TwoMeters), withDynSession).filter (
      *asset =>
        *GeometryUtils.geometryLength(Seq(position, Point(asset.lon, asset.lat))) <= TwoMeters &&
          *otherRoadwayMarkingType == getProperty(asset.propertyData, requlationNumberPublicId).get.propertyValue.toString
    *)
 *
 *if (otherRoadwayInRadius.nonEmpty) Some(getLatestModifiedAsset(otherRoadwayInRadius).asInstanceOf[OtherRoadwayMarking]) else None
  *}
 *
 *def createFloatingWithoutTransaction(asset: IncomingOtherRoadwayMarking, username: String, roadLink: RoadLink): Long = {
    *val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(asset.lon, asset.lat), roadLink.geometry)
    *OracleOtherRoadwayMarkingDao.create(setAssetPosition(asset, roadLink.geometry, mValue), mValue, username, roadLink.municipalityCode, VVHClient.createVVHTimeStamp(), roadLink.linkSource)
  *}
 *
 *override def update (id: Long, updatedAsset: IncomingOtherRoadwayMarking, roadLink: RoadLink, username: String): Long = {
    *withDynTransaction {
      *updateWithoutTransaction(id, updatedAsset, roadLink, username, None, None)
    *}
  *}
 *
 *def updateWithoutTransaction(id: Long, updatedAsset: IncomingOtherRoadwayMarking, roadLink: RoadLink, username: String, mValue: Option[Double], vvhTimeStamp: Option[Long]): Long = {
    *val value = mValue.getOrElse(GeometryUtils.calculateLinearReferenceFromPoint(Point(updatedAsset.lon, updatedAsset.lat), roadLink.geometry))
    *getPersistedAssetsByIdsWithoutTransaction(Set(id)).headOption.getOrElse(throw new NoSuchElementException("Asset not found")) match {
      *case old if old.lat != updatedAsset.lat || old.lon != updatedAsset.lon =>
        *expireWithoutTransaction(id)
        *OracleOtherRoadwayMarkingDao.create(setAssetPosition(updatedAsset, roadLink.geometry, value), value, username, roadLink.municipalityCode, vvhTimeStamp.getOrElse(VVHClient.createVVHTimeStamp()), roadLink.linkSource, old.createdBy, old.createdAt)
      *case _ =>
        *OracleOtherRoadwayMarkingDao.update(id, setAssetPosition(updatedAsset, roadLink.geometry, value), value, username, roadLink.municipalityCode, Some(vvhTimeStamp.getOrElse(VVHClient.createVVHTimeStamp())), roadLink.linkSource)
    *}
  *}
 *
 *override def getByBoundingBox(user: User, bounds: BoundingRectangle) : Seq[PersistedAsset] = {
    *val (roadLinks, changeInfo) = roadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(bounds)
    *super.getByBoundingBox(user, bounds, roadLinks, changeInfo, floatingAdjustment(adjustmentOperation, createOperation))
  *}
 *
 *private def createOperation(asset: PersistedAsset, adjustment: AssetAdjustment): PersistedAsset = {
    *createPersistedAsset(asset, adjustment)
  *}
 *
 *private def adjustmentOperation(persistedAsset: PersistedAsset, adjustment: AssetAdjustment, roadLink: RoadLink): Long = {
    *val updated = IncomingOtherRoadwayMarking(adjustment.lon, adjustment.lat, adjustment.linkId, persistedAsset.propertyData.map(prop => SimplePointAssetProperty(prop.publicId, prop.values)).toSet)
    *updateWithoutTransaction(adjustment.assetId, updated, roadLink, "vvh_generated", Some(adjustment.mValue), Some(adjustment.vvhTimeStamp))
  *}
 *
 *override def getByMunicipality(municipalityCode: Int): Seq[PersistedAsset] = {
    *val (roadLinks, changeInfo) = roadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(municipalityCode)
    *val mapRoadLinks = roadLinks.map(l => l.linkId -> l).toMap
    *getByMunicipality(mapRoadLinks, roadLinks, changeInfo, floatingAdjustment(adjustmentOperation, createOperation), withMunicipality(municipalityCode))
  *}
 *
 *private def createPersistedAsset[T](persistedStop: PersistedAsset, asset: AssetAdjustment) = {
    *new PersistedAsset(asset.assetId, asset.linkId, asset.lon, asset.lat,
      *asset.mValue, asset.floating, persistedStop.vvhTimeStamp, persistedStop.municipalityCode, persistedStop.propertyData, persistedStop.createdBy,
      *persistedStop.createdAt, persistedStop.modifiedBy, persistedStop.modifiedAt, linkSource = persistedStop.linkSource)
 *
 *}
 *
 *def getFloatingWidthOfRoadAxisMarkings(floating: Int, lastIdUpdate: Long, batchSize: Int): Seq[OtherRoadwayMarking] = {
    *OracleOtherRoadwayMarkingDao.selectFloatings(floating, lastIdUpdate, batchSize)
  *}
 *
 *def updateFloatingAsset(widthOfRoadAxisMarkingUpdated: OtherRoadwayMarking): Unit = {
    *OracleOtherRoadwayMarkingDao.updateFloatingAsset(widthOfRoadAxisMarkingUpdated)
  *}
 *
 *override def toIncomingAsset(asset: IncomePointAsset, link: RoadLink) : Option[IncomingOtherRoadwayMarking] = {
    *GeometryUtils.calculatePointFromLinearReference(link.geometry, asset.mValue).map {
      *point =>  IncomingOtherRoadwayMarking(point.x, point.y, link.linkId, asset.asInstanceOf[IncomingOtherRoadwayMarking].propertyData)
    * }
    * }
    *
    *
    * */
  override def update(id: Long, updatedAsset: IncomingOtherRoadwayMarking, roadLink: RoadLink, username: String): Long = ???
}





