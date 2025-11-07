package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.{AssetTypeInfo, Lanes, RoadLinkProperties}
import fi.liikennevirasto.digiroad2.dao.Queries
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase

trait ComplimentaryRoadLinkExpiringOperations {
  def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)
}

class ComplimentaryRoadLinkExpiringProcess(eventbus: DigiroadEventBus) extends ComplimentaryRoadLinkExpiringOperations {
    override def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)

    /**
     * Expires linear assets and sets point assets as floating for given link IDs.
     * Used in Complementary road link CSV import's delete feature.
     */
    def handleAssetsOnDeletedComplementaryRoadLinks(deletedLinkIds: Set[String], username: String): Unit = {
      withDynTransaction {
      val linearAssetTypeIds: Set[Int] = AssetTypeInfo.linearAssets
        .filterNot(asset => asset.typeId == Lanes.typeId || asset.typeId == RoadLinkProperties.typeId)
        .map(_.typeId)
        .toSet

      val pointAssetTypeIds: Set[Int] = AssetTypeInfo.pointAssets.map(_.typeId).toSet

      val linearAssetIds: Seq[Long] = Queries.getAssetIdsByTypesAndLinkId(linearAssetTypeIds, deletedLinkIds.toSeq)
      val pointAssetIds: Seq[Long] = Queries.getAssetIdsByTypesAndLinkId(pointAssetTypeIds, deletedLinkIds.toSeq)

      Queries.updateFloating(pointAssetIds, floating = true, username)
      Queries.expireAssetsByIds(linearAssetIds, username)
    }
  }
}
