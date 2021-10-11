package fi.liikennevirasto.digiroad2.service.pointasset

import fi.liikennevirasto.digiroad2.PointAssetFiller.AssetAdjustment
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.dao.InaccurateAssetDAO
import fi.liikennevirasto.digiroad2.dao.pointasset.{PostGISPedestrianCrossingDao, PedestrianCrossing}
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.process.AssetValidatorInfo
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.user.User
import org.joda.time.DateTime

case class IncomingPedestrianCrossing(lon: Double, lat: Double, linkId: Long, propertyData: Set[SimplePointAssetProperty]) extends IncomingPointAsset
case class IncomingPedestrianCrossingAsset(linkId: Long, mValue: Long, propertyData: Set[SimplePointAssetProperty]) extends IncomePointAsset

class PedestrianCrossingService(val roadLinkService: RoadLinkService, eventBus: DigiroadEventBus) extends PointAssetOperations {
  type IncomingAsset = IncomingPedestrianCrossing
  type PersistedAsset = PedestrianCrossing

  def inaccurateDAO: InaccurateAssetDAO = new InaccurateAssetDAO
  lazy val dao = new PostGISPedestrianCrossingDao()

  override def typeId: Int = 200

  override def fetchPointAssets(queryFilter: String => String, roadLinks: Seq[RoadLinkLike]): Seq[PedestrianCrossing] = dao.fetchByFilter(queryFilter)
  override def fetchPointAssetsWithExpired(queryFilter: String => String, roadLinks: Seq[RoadLinkLike]): Seq[PedestrianCrossing] = dao.fetchByFilterWithExpired(queryFilter)
  override def fetchPointAssetsWithExpiredLimited(queryFilter: String => String, token: Option[String]): Seq[PedestrianCrossing] = dao.fetchByFilterWithExpiredLimited(queryFilter, token)

  override def setFloating(persistedAsset: PedestrianCrossing, floating: Boolean) : PedestrianCrossing = {
    persistedAsset.copy(floating = floating)
  }

  override def setAssetPosition(asset: IncomingPedestrianCrossing, geometry: Seq[Point], mValue: Double): IncomingPedestrianCrossing = {
      GeometryUtils.calculatePointFromLinearReference(geometry, mValue) match {
        case Some(point) =>
          asset.copy(lon = point.x, lat = point.y)
        case _ =>
          asset
      }
  }

  def createFromCoordinates(incomingPedestrianCrossing: IncomingPedestrianCrossing, roadLink: RoadLink, username: String, isFloating: Boolean): Long = {
    if(isFloating)
      createFloatingWithoutTransaction(incomingPedestrianCrossing.copy(linkId = 0), username, roadLink)
    else {
      checkDuplicates(incomingPedestrianCrossing) match {
        case Some(existingAsset) =>
          updateWithoutTransaction(existingAsset.id, incomingPedestrianCrossing, roadLink, username, Some(existingAsset.mValue), Some(existingAsset.vvhTimeStamp))
        case _ =>
          create(incomingPedestrianCrossing, username, roadLink, false)
      }
    }
  }

  def checkDuplicates(incomingPedestrianCrossing: IncomingPedestrianCrossing): Option[PedestrianCrossing] = {
    val position = Point(incomingPedestrianCrossing.lon, incomingPedestrianCrossing.lat)
    val signsInRadius = dao.fetchByFilter(withBoundingBoxFilter(position, TwoMeters))
      .filter(sign => GeometryUtils.geometryLength(Seq(position, Point(sign.lon, sign.lat))) <= TwoMeters)

    if(signsInRadius.nonEmpty) Some(getLatestModifiedAsset(signsInRadius)) else None
  }

  def getLatestModifiedAsset(signs: Seq[PedestrianCrossing]): PedestrianCrossing = {
    signs.maxBy(sign => sign.modifiedAt.getOrElse(sign.createdAt.get).getMillis)
  }

  def createFloatingWithoutTransaction(asset: IncomingPedestrianCrossing, username: String, roadLink: RoadLink): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(asset.lon, asset.lat), roadLink.geometry)
    dao.create(setAssetPosition(asset, roadLink.geometry, mValue), mValue, username, roadLink.municipalityCode, VVHClient.createVVHTimeStamp(), roadLink.linkSource)
  }

  override def create(asset: IncomingPedestrianCrossing, username: String, roadLink: RoadLink, newTransaction: Boolean): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(asset.lon, asset.lat), roadLink.geometry)
    val pedestrianId =
      if(newTransaction) {
        withDynTransaction {
          dao.create(setAssetPosition(asset, roadLink.geometry, mValue), mValue, username, roadLink.municipalityCode, VVHClient.createVVHTimeStamp(), roadLink.linkSource)
        }
      } else {
        dao.create(setAssetPosition(asset, roadLink.geometry, mValue), mValue, username, roadLink.municipalityCode, VVHClient.createVVHTimeStamp(), roadLink.linkSource)
      }
    pedestrianCrossingValidatorActor(Set(pedestrianId))
    pedestrianId
  }

  override def update(id: Long, updatedAsset: IncomingPedestrianCrossing, roadLink: RoadLink, username: String): Long = {
    val pedestrianIdUpdated =
    withDynTransaction {
      updateWithoutTransaction(id, updatedAsset, roadLink, username, None, None)
    }
    pedestrianCrossingValidatorActor(Set(id, pedestrianIdUpdated))
    pedestrianIdUpdated
  }

  def updateWithoutTransaction(id: Long, updatedAsset: IncomingPedestrianCrossing,roadLink: RoadLink, username: String, mValue : Option[Double], vvhTimeStamp: Option[Long]): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(updatedAsset.lon, updatedAsset.lat), roadLink.geometry)
    getPersistedAssetsByIdsWithoutTransaction(Set(id)).headOption.getOrElse(throw new NoSuchElementException("Asset not found")) match {
      case old if  old.lat != updatedAsset.lat || old.lon != updatedAsset.lon =>
        expireWithoutTransaction(id)
        dao.create(setAssetPosition(updatedAsset, roadLink.geometry, mValue), mValue, username, roadLink.municipalityCode, vvhTimeStamp.getOrElse(VVHClient.createVVHTimeStamp()), roadLink.linkSource, old.createdBy, old.createdAt)
      case _ =>
        dao.update(id, setAssetPosition(updatedAsset, roadLink.geometry, mValue), mValue, username, roadLink.municipalityCode, Some(vvhTimeStamp.getOrElse(VVHClient.createVVHTimeStamp())), roadLink.linkSource)
    }
  }

  override def getByBoundingBox(user: User, bounds: BoundingRectangle) : Seq[PersistedAsset] = {
    val (roadLinks, changeInfo) = roadLinkService.getRoadLinksWithComplementaryAndChangesFromVVH(bounds)
    super.getByBoundingBox(user, bounds, roadLinks, changeInfo, floatingAdjustment(adjustmentOperation, createOperation)).filterNot(_.expired)
  }

  private def createOperation(asset: PersistedAsset, adjustment: AssetAdjustment): PersistedAsset = {
    createPersistedAsset(asset, adjustment)
  }

  private def adjustmentOperation(persistedAsset: PersistedAsset, adjustment: AssetAdjustment, roadLink: RoadLink): Long = {
    val updated = IncomingPedestrianCrossing(adjustment.lon, adjustment.lat, adjustment.linkId, persistedAsset.propertyData.map(prop => SimplePointAssetProperty(prop.publicId, prop.values)).toSet)
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
      asset.mValue, asset.floating, persistedStop.vvhTimeStamp, persistedStop.municipalityCode, persistedStop.propertyData, persistedStop.createdBy,
      persistedStop.createdAt, persistedStop.modifiedBy, persistedStop.modifiedAt, linkSource = persistedStop.linkSource)
  }

  override def toIncomingAsset(asset: IncomePointAsset, link: RoadLink) : Option[IncomingPedestrianCrossing] = {
    GeometryUtils.calculatePointFromLinearReference(link.geometry, asset.mValue).map {
      point =>  IncomingPedestrianCrossing(point.x, point.y, link.linkId, asset.asInstanceOf[IncomingPedestrianCrossing].propertyData)
    }
  }

  override def getInaccurateRecords(typeId: Int, municipalities: Set[Int] = Set(), adminClass: Set[AdministrativeClass] = Set()): Map[String, Map[String, Any]] = {
    withDynTransaction {
      inaccurateDAO.getInaccurateAsset(typeId, municipalities, adminClass)
        .groupBy(_.municipality)
        .mapValues {
          _.groupBy(_.administrativeClass)
            .mapValues(_.map{values => Map("assetId" -> values.assetId, "linkId" -> values.linkId)})
        }
    }
  }

  private def pedestrianCrossingValidatorActor(ids: Set[Long]): Unit = {
    eventBus.publish("pedestrianCrossing:Validator", AssetValidatorInfo(ids))
  }
}

