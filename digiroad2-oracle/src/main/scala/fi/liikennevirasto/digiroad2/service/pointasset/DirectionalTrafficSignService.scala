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

  override def setFloating(persistedAsset: DirectionalTrafficSign, floating: Boolean) = {
    persistedAsset.copy(floating = floating)
  }

  override def create(asset: IncomingDirectionalTrafficSign, username: String, roadLink: RoadLink): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(asset.lon, asset.lat), roadLink.geometry)
    withDynTransaction {
      OracleDirectionalTrafficSignDao.create(setAssetPosition(asset, roadLink.geometry, mValue), mValue, roadLink.municipalityCode ,username)
    }
  }

  override def update(id: Long, updatedAsset: IncomingDirectionalTrafficSign, roadLink: RoadLink, username: String): Long = {
    withDynTransaction {
      updateWithoutTransaction(id, updatedAsset, roadLink, username)
    }
  }

  def updateWithoutTransaction(id: Long, updatedAsset: IncomingDirectionalTrafficSign, roadLink: RoadLink,  username: String): Long = {
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(Point(updatedAsset.lon, updatedAsset.lat), roadLink.geometry)
    getPersistedAssetsByIdsWithoutTransaction(Set(id)).headOption.getOrElse(throw new NoSuchElementException("Asset not found")) match {
      case old if old.bearing != updatedAsset.bearing || ( old.lat != updatedAsset.lat || old.lon != updatedAsset.lon) =>
        expireWithoutTransaction(id)
        OracleDirectionalTrafficSignDao.create(setAssetPosition(updatedAsset, roadLink.geometry, mValue), mValue, roadLink.municipalityCode, username,old.createdBy, old.createdAt)
      case _ =>
        OracleDirectionalTrafficSignDao.update(id, setAssetPosition(updatedAsset, roadLink.geometry, mValue), mValue, roadLink.municipalityCode, username)
    }
  }

  override def getUnverifiedPointAssets(municipalities: Set[Int]): Map[String, List[(Long, String)]] = {
    withDynSession {
      val municipalityDao = new MunicipalityDao
      val unverifiedAssets = OracleDirectionalTrafficSignDao.getUnverifiedAssets(typeId)
      val filteredByUserAssets = if(municipalities.nonEmpty) unverifiedAssets.filter(asset => municipalities.contains(asset._2)) else unverifiedAssets
      filteredByUserAssets.map(asset => (asset._1, municipalityDao.getMunicipalityNameByCode(asset._2))).groupBy(_._2)
    }
  }

  override def getChanged(sinceDate: DateTime, untilDate: DateTime): Seq[ChangedPointAsset] = { throw new UnsupportedOperationException("Not Supported Method") }

  override def updateVerifiedInfo(assetId: Long, user: String): Long = {
    withDynSession {
      OracleDirectionalTrafficSignDao.updateVerifiedInfo(assetId, user)
    }
  }
}


