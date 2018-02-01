package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.dao.OracleAssetDao
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase

trait AssetOperations {

  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  def assetDao: OracleAssetDao

  def getMunicipalityById(id: Long): Seq[Long] = {
    withDynTransaction {
      assetDao.getMunicipalityById(id)
    }
  }
}

class AssetService(eventbus: DigiroadEventBus) extends AssetOperations {
    override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
    override def assetDao: OracleAssetDao = new OracleAssetDao
}
