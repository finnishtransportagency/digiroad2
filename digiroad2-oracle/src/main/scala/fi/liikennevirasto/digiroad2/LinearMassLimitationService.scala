package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.{BoundingRectangle, SideCode}
import fi.liikennevirasto.digiroad2.dao.MassLimitationDao
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.linearasset.LinearAssetTypes

case class MassLimitationAsset(geometry: Seq[Point], sideCode: Int, value: Option[Value])

class LinearMassLimitationService(roadLinkService: RoadLinkService, dao: MassLimitationDao) {
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
        val geometrySegment = GeometryUtils.truncateGeometry2D(roadLinks.find(_.linkId == assets.head.linkId).get.geometry, assets.minBy(_.startMeasure).startMeasure, assets.maxBy(_.endMeasure).endMeasure)

        assetSplitSideCodes(assets).groupBy(_.sideCode).map {
          case (side, assetsBySide) => getAssetBySideCode(assetsBySide, geometrySegment)
        }
    }.toSeq
  }

  private def getAllAssetsByLinkIds(typeIds: Seq[Int], linkIds: Seq[Long]): Seq[PersistedLinearAsset] = {
    withDynTransaction {
      dao.fetchLinearAssetsByLinkIds(typeIds, linkIds, LinearAssetTypes.numericValuePropertyId)
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

  private def getAssetBySideCode(assets: Seq[PersistedLinearAsset], geometry: Seq[Point]): MassLimitationAsset = {
      val values = assets.map(a => AssetTypes(a.typeId, a.value.getOrElse(NumericValue(0)).asInstanceOf[NumericValue].value.toString ))
      MassLimitationAsset(geometry, assets.head.sideCode, Some(MassLimitationValue(values)))
  }
}