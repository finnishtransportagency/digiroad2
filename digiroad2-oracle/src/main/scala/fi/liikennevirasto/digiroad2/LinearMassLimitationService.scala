package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, AssetTypeInfo, AxleWeightLimit, BogieWeightLimit, BoundingRectangle, SideCode, TotalWeightLimit, TrailerTruckWeightLimit}
import fi.liikennevirasto.digiroad2.dao.DynamicLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService

case class MassLimitationAsset(linkId: Long, administrativeClass: AdministrativeClass, sideCode: Int, value: Option[Value], geometry: Seq[Point],
                              attributes: Map[String, Any] = Map())

class LinearMassLimitationService(roadLinkService: RoadLinkService, dynamicDao: DynamicLinearAssetDao) {
  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)

  final val MassLimitationAssetTypes = Seq(TotalWeightLimit.typeId, TrailerTruckWeightLimit.typeId, AxleWeightLimit.typeId, BogieWeightLimit.typeId)

  def getByBoundingBox(bounds: BoundingRectangle, municipalities: Set[Int] = Set()): Seq[Seq[MassLimitationAsset]] = {
    val roadLinks = roadLinkService.getRoadLinksFromVVH(bounds, municipalities)
    Seq(getByRoadLinks(MassLimitationAssetTypes, roadLinks))
  }

  def getWithComplementaryByBoundingBox(bounds: BoundingRectangle, municipalities: Set[Int] = Set()): Seq[Seq[MassLimitationAsset]] = {
    val roadLinks = roadLinkService.getRoadLinksWithComplementaryFromVVH(bounds, municipalities)
    Seq(getByRoadLinks(MassLimitationAssetTypes, roadLinks))
  }

  def getByRoadLinks(typeIds: Seq[Int], roadLinks: Seq[RoadLink]): Seq[MassLimitationAsset] = {
    val linkIds = roadLinks.map(_.linkId)
    val allAssets = getAllAssetsByLinkIds(typeIds, linkIds)

    allAssets.groupBy(_.linkId).flatMap {
      case (linkId, assets) =>
        val roadLink = roadLinks.find(_.linkId == assets.head.linkId).get
        val geometrySegment = GeometryUtils.truncateGeometry2D(roadLink.geometry, assets.minBy(_.startMeasure).startMeasure, assets.maxBy(_.endMeasure).endMeasure)

        assetSplitSideCodes(assets).groupBy(_.sideCode).map {
          case (side, assetsBySide) => getAssetBySideCode(assetsBySide, geometrySegment, roadLink)
        }
    }.toSeq
  }

  private def getAllAssetsByLinkIds(typeIds: Seq[Int], linkIds: Seq[Long]): Seq[PersistedLinearAsset] = {
    withDynTransaction {
      MassLimitationAssetTypes.flatMap(dynamicDao.fetchDynamicLinearAssetsByLinkIds(_ , linkIds))
    }.filterNot(_.expired)
  }

  private def assetSplitSideCodes(assets: Seq[PersistedLinearAsset]): Seq[PersistedLinearAsset] = {
   if  (assets.exists(_.sideCode != SideCode.BothDirections.value) && assets.exists(_.sideCode == SideCode.BothDirections.value))
      assets.filter(_.sideCode == SideCode.BothDirections.value).flatMap(asset_sideCode =>
        Seq(asset_sideCode.copy(sideCode = SideCode.AgainstDigitizing.value), asset_sideCode.copy(sideCode = SideCode.TowardsDigitizing.value))
      ) ++ assets.filterNot(_.sideCode == SideCode.BothDirections.value)
   else assets
  }

  def getDynamicValue(value: DynamicAssetValue, publicID: String) : Option[String] = {
    value.properties.find(_.publicId == publicID).flatMap(_.values.map(_.value.toString).headOption)
  }

  private def getAssetBySideCode(assets: Seq[PersistedLinearAsset], geometry: Seq[Point], roadLink: RoadLink): MassLimitationAsset = {
    val values = assets.map{asset =>
      val value = asset.value match {
        case Some(DynamicValue(value)) if asset.typeId == BogieWeightLimit.typeId => getDynamicValue(value, "bogie_weight_2_axel").getOrElse(getDynamicValue(value, "bogie_weight_3_axel").getOrElse(""))
        case Some(DynamicValue(value)) if Seq(TrailerTruckWeightLimit.typeId, TotalWeightLimit.typeId, AxleWeightLimit.typeId).contains(asset.typeId) => getDynamicValue(value, "weight").getOrElse("")
        case _ => throw new NumberFormatException(s"Value format not supported on asset type ${AssetTypeInfo.apply(asset.typeId).label}")
      }
      val isSuggested = asset.value match {
        case Some(DynamicValue(value)) =>
          getDynamicValue(value, "suggest_box").getOrElse("0").toInt
        case _ => throw new NumberFormatException(s"Value format not supported on asset type ${AssetTypeInfo.apply(asset.typeId).label}")
      }

      AssetTypes(asset.typeId , value, isSuggested)
    }
    MassLimitationAsset(assets.head.linkId, roadLink.administrativeClass, assets.head.sideCode, Some(MassLimitationValue(values)), geometry)
  }
}
