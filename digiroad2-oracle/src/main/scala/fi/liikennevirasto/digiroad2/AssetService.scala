package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.AssetTypeInfo
import fi.liikennevirasto.digiroad2.dao.{MunicipalityDao, PostGISAssetDao}
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase

trait AssetOperations {

  def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)
  def assetDao: PostGISAssetDao
  def municipalityDao: MunicipalityDao

  def getMunicipalityById(id: Int): Seq[Int] = {
    withDynTransaction {
      municipalityDao.getMunicipalityById(id)
    }
  }
}

class AssetService(eventbus: DigiroadEventBus) extends AssetOperations {
    override def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)
    override def assetDao: PostGISAssetDao = new PostGISAssetDao
    override def municipalityDao: MunicipalityDao = new MunicipalityDao

    /**
     * Expires linear assets and sets point assets as floating for given link IDs.
     * Used in Complementary road link CSV import's delete feature.
     */
    def handleAssetsOnDeletedComplementaryRoadLinks(deletedLinkIds: Set[String], username: String): Unit = {
      withDynTransaction {
      // Linear asset types, excluding 450 (lanes) and 460 (road links)
      val linearAssetTypeIds: Set[Int] = AssetTypeInfo.linearAssets
        .filterNot(asset => asset.typeId == 450 || asset.typeId == 460)
        .map(_.typeId)
        .toSet

      // Point asset types
      val pointAssetTypeIds: Set[Int] = AssetTypeInfo.pointAssets.map(_.typeId).toSet

      val linearAssetIds: Seq[Long] = assetDao.getAssetIdsByTypesAndLinkId(linearAssetTypeIds, deletedLinkIds.toSeq)
      val pointAssetIds: Seq[Long] = assetDao.getAssetIdsByTypesAndLinkId(pointAssetTypeIds, deletedLinkIds.toSeq)

      assetDao.updateFloating(pointAssetIds, floating = true, username)
    }
  }
}
