package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.asset.oracle.OracleAssetDao
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase

trait AssetOperations {

  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  def assetDao: OracleAssetDao

  def getMunicipalityById(id: Long): Seq[Long] = {
    withDynTransaction {
      assetDao.getMunicipalityById(id)
    }
  }

  def getVerifiableAssets: Seq[String] = {
    withDynTransaction {
      assetDao.getVerifiableAssetTypes
    }
  }

  def getMunicipalitiesNameByCode(municipalityCodes: Set[Int]): Map[String, Seq[String]] = {
    val municipalities = withDynTransaction {
      assetDao.getMunicipalitiesNameByCode(municipalityCodes)
    }
    Map("municipality" -> municipalities.sorted)
  }
}

class AssetService(eventbus: DigiroadEventBus) extends AssetOperations {
    override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
    override def assetDao: OracleAssetDao = new OracleAssetDao
}
