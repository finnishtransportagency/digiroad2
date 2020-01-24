package fi.liikennevirasto.digiroad2.service.pointasset

import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.LinkGeomSource
import fi.liikennevirasto.digiroad2.dao.MunicipalityDao
import fi.liikennevirasto.digiroad2.dao.pointasset.{DirectionalTrafficSign, OracleDirectionalTrafficSignDao}
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import org.joda.time.DateTime

case class IncomingDirectionalTrafficSign(lon: Double, lat: Double, linkId: Long, validityDirection: Int, text: Option[String], bearing: Option[Int]) extends IncomingPointAsset


class DirectionalTrafficSignService(val roadLinkService: RoadLinkService) extends PointAssetOperations {
  type IncomingAsset = IncomingDirectionalTrafficSign
  type PersistedAsset = DirectionalTrafficSign

  override def typeId: Int = 240
  override def fetchPointAssetsWithExpired(queryFilter: String => String, roadLinks: Seq[RoadLinkLike]): Seq[DirectionalTrafficSign] = { throw new UnsupportedOperationException("Not Supported Method") }
  override def fetchPointAssetsWithExpiredLimited(queryFilter: String => String, pageNumber: Option[Int]): Seq[DirectionalTrafficSign] =  throw new UnsupportedOperationException("Not Supported Method")

  override def setAssetPosition(asset: IncomingDirectionalTrafficSign, geometry: Seq[Point], mValue: Double): IncomingDirectionalTrafficSign = {
    GeometryUtils.calculatePointFromLinearReference(geometry, mValue) match {
      case Some(point) =>
        asset.copy(lon = point.x, lat = point.y)
      case _ =>
        asset
    }
  }

  override def fetchPointAssets(queryFilter: String => String, roadLinks: Seq[RoadLinkLike]): Seq[DirectionalTrafficSign] = {
    val assets = OracleDirectionalTrafficSignDao.fetchByFilter(queryFilter)
    assets.map { asset =>
      asset.copy(geometry = roadLinks.find(_.linkId == asset.linkId).map(_.geometry).getOrElse(Nil))}
  }

  def createFromCoordinates(incomingDirectionalTrafficSign: IncomingDirectionalTrafficSign, roadLink: RoadLink, username: String, isFloating: Boolean): Long = {
    if(isFloating)
      createFloatingWithoutTransaction(incomingDirectionalTrafficSign.copy(linkId = 0), username, roadLink)
    else {
      checkDuplicates(incomingDirectionalTrafficSign) match {
        case Some(existingAsset) =>
          updateWithoutTransaction(existingAsset.id, incomingDirectionalTrafficSign, roadLink, username)
        case _ =>
          create(incomingDirectionalTrafficSign, username, roadLink, false)
      }
    }
  }

  def checkDuplicates(incomingDirectionalTrafficSign: IncomingDirectionalTrafficSign): Option[DirectionalTrafficSign] = {
    val position = Point(incomingDirectionalTrafficSign.lon, incomingDirectionalTrafficSign.lat)
    val signsInRadius = OracleDirectionalTrafficSignDao.fetchByFilter(withBoundingBoxFilter(position, TwoMeters))
      .filter(sign => GeometryUtils.geometryLength(Seq(position, Point(sign.lon, sign.lat))) <= TwoMeters
        && Math.abs(sign.bearing.getOrElse(0) - incomingDirectionalTrafficSign.bearing.getOrElse(0)) <= BearingLimit)
    if(signsInRadius.nonEmpty)
      return Some(getLatestModifiedAsset(signsInRadius))
    None
  }

  def getLatestModifiedAsset(signs: Seq[DirectionalTrafficSign]): DirectionalTrafficSign = {
    signs.maxBy(sign => sign.modifiedAt.getOrElse(sign.createdAt.get).getMillis)
  }


  override def setFloating(persistedAsset: DirectionalTrafficSign, floating: Boolean) = {
    persistedAsset.copy(floating = floating)
  }

  override def create(asset: IncomingDirectionalTrafficSign, username: String, roadLink: RoadLink, newTransaction: Boolean): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(asset.lon, asset.lat), roadLink.geometry)
    if(newTransaction) {
      withDynTransaction {
        OracleDirectionalTrafficSignDao.create(setAssetPosition(asset, roadLink.geometry, mValue), mValue, roadLink.municipalityCode ,username, false)
      }
    } else {
      OracleDirectionalTrafficSignDao.create(setAssetPosition(asset, roadLink.geometry, mValue), mValue, roadLink.municipalityCode ,username, false)
    }
  }

  override def update(id: Long, updatedAsset: IncomingDirectionalTrafficSign, roadLink: RoadLink, username: String): Long = {
    withDynTransaction {
      updateWithoutTransaction(id, updatedAsset, roadLink, username)
    }
  }

  def createFloatingWithoutTransaction(asset: IncomingDirectionalTrafficSign, username: String, roadLink: RoadLink): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(asset.lon, asset.lat), roadLink.geometry)
    OracleDirectionalTrafficSignDao.create(setAssetPosition(asset, roadLink.geometry, mValue), mValue, roadLink.municipalityCode ,username, true)
  }

  def updateWithoutTransaction(id: Long, updatedAsset: IncomingDirectionalTrafficSign, roadLink: RoadLink,  username: String): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(updatedAsset.lon, updatedAsset.lat), roadLink.geometry)
    getPersistedAssetsByIdsWithoutTransaction(Set(id)).headOption.getOrElse(throw new NoSuchElementException("Asset not found")) match {
      case old if old.bearing != updatedAsset.bearing || ( old.lat != updatedAsset.lat || old.lon != updatedAsset.lon) =>
        expireWithoutTransaction(id)
        OracleDirectionalTrafficSignDao.create(setAssetPosition(updatedAsset, roadLink.geometry, mValue), mValue, roadLink.municipalityCode, username, old.createdBy, old.createdAt)
      case _ =>
        OracleDirectionalTrafficSignDao.update(id, setAssetPosition(updatedAsset, roadLink.geometry, mValue), mValue, roadLink.municipalityCode, username)
    }
  }
  override def getChanged(sinceDate: DateTime, untilDate: DateTime, pageNumber: Option[Int] = None): Seq[ChangedPointAsset] = { throw new UnsupportedOperationException("Not Supported Method") }
}


