package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.dao.{MunicipalityDao, OracleAssetDao}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase

trait AssetOperations {

  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  def assetDao: OracleAssetDao
  def municipalityDao: MunicipalityDao

  def getMunicipalityById(id: Int): Seq[Int] = {
    withDynTransaction {
      municipalityDao.getMunicipalityById(id)
    }
  }
}

class AssetService(eventbus: DigiroadEventBus) extends AssetOperations {
    override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
    override def assetDao: OracleAssetDao = new OracleAssetDao
    override def municipalityDao: MunicipalityDao = new MunicipalityDao
}
