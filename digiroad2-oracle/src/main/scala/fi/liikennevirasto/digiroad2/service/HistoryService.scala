package fi.liikennevirasto.digiroad2.service

import fi.liikennevirasto.digiroad2.asset.{AssetTypeInfo, Manoeuvres}
import fi.liikennevirasto.digiroad2.dao.HistoryDAO
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase

/**
  * This class performs operations related to assets history
  */
class HistoryService {
  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  def historyDao: HistoryDAO = new HistoryDAO

  /**
    * Get expired assets by asset type and year gap
    * @param assetType Filter assets by asset type
    * @param yearGap Filter assets expired by expired date before the year gap
    * @return Expired assets ids
    */
  def getExpiredAssetsIdsByAssetTypeAndYearGap(assetType: AssetTypeInfo, yearGap: Int) = {
    assetType match {
      case Manoeuvres =>
        historyDao.getExpiredManoeuvresIdsByYearGap(yearGap)
      case _ =>
        historyDao.getExpiredAssetsIdsByAssetTypeIdAndYearGap(assetType.typeId, yearGap)
    }
  }

  /**
    * Copy asset from the main tables to the history tables
    * @param assetId Asset id of the asset to be transferred
    * @param assetType Asset type correspondent of the asset being transferred
    */
  def transferExpiredAssetToHistoryById(assetId: Long, assetType: AssetTypeInfo) = {
    assetType match {
      case Manoeuvres =>
        historyDao.copyManoeuvreToHistory(assetId)
        historyDao.deleteManoeuvre(assetId)
      case _ =>
        historyDao.copyAssetToHistory(assetId, assetType)
        historyDao.deleteAsset(assetId, assetType)
    }
  }
}
