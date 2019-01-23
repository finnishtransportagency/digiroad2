package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, BoundingRectangle, SideCode}
import fi.liikennevirasto.digiroad2.dao.{DynamicLinearAssetDao, MassLimitationDao}
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.LinearAssetTypes

case class MassLimitationAsset(linkId: Long, administrativeClass: AdministrativeClass, sideCode: Int, value: Option[Value], geometry: Seq[Point],
                              attributes: Map[String, Any] = Map())

class LinearMassLimitationService(roadLinkService: RoadLinkService, dao: MassLimitationDao, dynamicDao: DynamicLinearAssetDao) {
  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)

  val MassLimitationAssetTypes = Seq(LinearAssetTypes.TotalWeightLimits,
    LinearAssetTypes.TrailerTruckWeightLimits,
    LinearAssetTypes.AxleWeightLimits,
    LinearAssetTypes.BogieWeightLimits)

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
      dao.fetchLinearAssetsByLinkIds(typeIds, linkIds, LinearAssetTypes.numericValuePropertyId).filterNot(_.typeId == LinearAssetTypes.BogieWeightLimits) ++
      dynamicDao.fetchDynamicLinearAssetsByLinkIds(LinearAssetTypes.BogieWeightLimits, linkIds)
    }.filterNot(_.expired)
  }

  private def assetSplitSideCodes(assets: Seq[PersistedLinearAsset]): Seq[PersistedLinearAsset] = {
    assets.exists(_.sideCode != SideCode.BothDirections.value) && assets.exists(_.sideCode == SideCode.BothDirections.value) match {
      case true => assets.filter(_.sideCode == SideCode.BothDirections.value).flatMap(asset_sideCode =>
        Seq(asset_sideCode.copy(sideCode = SideCode.AgainstDigitizing.value), asset_sideCode.copy(sideCode = SideCode.TowardsDigitizing.value))
      ) ++ assets.filterNot(_.sideCode == SideCode.BothDirections.value)
      case false => assets
    }
  }

  def getDynamicValue(value: DynamicAssetValue, publicID: String) : Option[String] = {value.properties.find(_.publicId == publicID).map(_.values.head.value.toString)}

  private def getAssetBySideCode(assets: Seq[PersistedLinearAsset], geometry: Seq[Point], roadLink: RoadLink): MassLimitationAsset = {
    val values = assets.map{asset =>
      AssetTypes(asset.typeId ,asset.value match {
        case Some(NumericValue(value)) => value.toString
        case Some(DynamicValue(value)) => getDynamicValue(value , "bogie_weight_2_axel").getOrElse(getDynamicValue(value , "bogie_weight_3_axel").getOrElse(""))
        case _ => ""
      })}
    MassLimitationAsset(assets.head.linkId, roadLink.administrativeClass, assets.head.sideCode, Some(MassLimitationValue(values)), geometry)
  }
}
