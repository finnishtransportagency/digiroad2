package fi.liikennevirasto.digiroad2.service.pointasset


import fi.liikennevirasto.digiroad2.{IncomingPointAsset, PointAssetOperations}
import fi.liikennevirasto.digiroad2.asset.{BoundingRectangle, OtherRoadwayMarkings, PropertyValue, SimplePointAssetProperty}
import fi.liikennevirasto.digiroad2.dao.pointasset._
import fi.liikennevirasto.digiroad2.linearasset.RoadLinkLike
import fi.liikennevirasto.digiroad2.service.RoadLinkService


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




}



