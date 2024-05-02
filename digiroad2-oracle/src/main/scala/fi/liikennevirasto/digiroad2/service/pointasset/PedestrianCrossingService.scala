package fi.liikennevirasto.digiroad2.service.pointasset

import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.RoadLinkInfo
import fi.liikennevirasto.digiroad2.dao.InaccurateAssetDAO
import fi.liikennevirasto.digiroad2.dao.pointasset.{PedestrianCrossing, PostGISPedestrianCrossingDao}
import fi.liikennevirasto.digiroad2.linearasset.{LinkId, RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.process.AssetValidatorInfo
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.user.User

case class IncomingPedestrianCrossing(lon: Double, lat: Double, linkId: String, propertyData: Set[SimplePointAssetProperty], mValue: Option[Double] = None) extends IncomingPointAsset
case class IncomingPedestrianCrossingAsset(linkId: String, mValue: Long, propertyData: Set[SimplePointAssetProperty]) extends IncomePointAsset

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
      createFloatingWithoutTransaction(incomingPedestrianCrossing.copy(linkId = LinkId.Unknown.value), username, roadLink)
    else {
      checkDuplicates(incomingPedestrianCrossing) match {
        case Some(existingAsset) =>
          updateWithoutTransaction(existingAsset.id, incomingPedestrianCrossing, roadLink, username, Some(existingAsset.mValue), Some(existingAsset.timeStamp))
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
    dao.create(setAssetPosition(asset, roadLink.geometry, mValue), mValue, username, roadLink.municipalityCode, createTimeStamp(), roadLink.linkSource)
  }

  override def create(asset: IncomingPedestrianCrossing, username: String, roadLink: RoadLink, newTransaction: Boolean): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(asset.lon, asset.lat), roadLink.geometry)
    val pedestrianId =
      if(newTransaction) {
        withDynTransaction {
          dao.create(setAssetPosition(asset, roadLink.geometry, mValue), mValue, username, roadLink.municipalityCode, createTimeStamp(), roadLink.linkSource)
        }
      } else {
        dao.create(setAssetPosition(asset, roadLink.geometry, mValue), mValue, username, roadLink.municipalityCode, createTimeStamp(), roadLink.linkSource)
      }
    pedestrianCrossingValidatorActor(Set(pedestrianId))
    pedestrianId
  }

  override def update(id: Long, updatedAsset: IncomingPedestrianCrossing, roadLink: RoadLink, username: String): Long = {
    val pedestrianIdUpdated =
    withDynTransaction {
      updateWithoutTransaction(id, updatedAsset, roadLink, username, updatedAsset.mValue, None)
    }
    pedestrianCrossingValidatorActor(Set(id, pedestrianIdUpdated))
    pedestrianIdUpdated
  }

  def updateWithoutTransaction(id: Long, updatedAsset: IncomingPedestrianCrossing, roadLink: RoadLink, username: String, mValue : Option[Double], timeStamp: Option[Long]): Long = {
    updateWithoutTransaction(id, updatedAsset, roadLink.geometry, roadLink.municipalityCode, roadLink.linkSource,
                             username, mValue, timeStamp)
  }

  def updateWithoutTransaction(id: Long, updatedAsset: IncomingPedestrianCrossing, linkGeom: Seq[Point], linkMunicipality: Int,
                               linkSource: LinkGeomSource, username: String, mValue : Option[Double], timeStamp: Option[Long], fromPointAssetUpdater: Boolean = false): Long = {
    val value = mValue.getOrElse(GeometryUtils.calculateLinearReferenceFromPoint(Point(updatedAsset.lon, updatedAsset.lat), linkGeom))
    getPersistedAssetsByIdsWithoutTransaction(Set(id)).headOption.getOrElse(throw new NoSuchElementException("Asset not found")) match {
      case old if (old.lat != updatedAsset.lat || old.lon != updatedAsset.lon) && !fromPointAssetUpdater =>
        expireWithoutTransaction(id)
        dao.create(setAssetPosition(updatedAsset, linkGeom, value), value, username, linkMunicipality,
          timeStamp.getOrElse(createTimeStamp()), linkSource, old.createdBy, old.createdAt, old.externalId, fromPointAssetUpdater, old.modifiedBy, old.modifiedAt)
      case _ =>
        dao.update(id, updatedAsset, value, username, linkMunicipality,
          Some(timeStamp.getOrElse(createTimeStamp())), linkSource, fromPointAssetUpdater)
    }
  }

  override def getByBoundingBox(user: User, bounds: BoundingRectangle) : Seq[PersistedAsset] = {
    val (roadLinks, _) = roadLinkService.getRoadLinksWithComplementaryAndChanges(bounds,asyncMode = false)
    super.getByBoundingBox(user, bounds, roadLinks).filterNot(_.expired)
  }

  override def createOperation(asset: PersistedAsset, adjustment: AssetUpdate): PersistedAsset = {
    new PersistedAsset(adjustment.assetId, adjustment.linkId, adjustment.lon, adjustment.lat,
      adjustment.mValue, adjustment.floating, asset.timeStamp, asset.municipalityCode, asset.propertyData, asset.createdBy,
      asset.createdAt, asset.modifiedBy, asset.modifiedAt, linkSource = asset.linkSource, externalId = asset.externalId)
  }

  override def adjustmentOperation(asset: PersistedAsset, adjustment: AssetUpdate, link: RoadLinkInfo): Long = {
    val updated = IncomingPedestrianCrossing(adjustment.lon, adjustment.lat, adjustment.linkId, asset.propertyData.map(prop => SimplePointAssetProperty(prop.publicId, prop.values)).toSet)
    updateWithoutTransaction(adjustment.assetId, updated, link.geometry, link.municipality.getOrElse(throw new NoSuchElementException(s"${link.linkId} does not have municipality code")), asset.linkSource,
      asset.createdBy.getOrElse(AutoGeneratedUsername.generatedInUpdate), Some(adjustment.mValue), Some(adjustment.timeStamp), true)
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

