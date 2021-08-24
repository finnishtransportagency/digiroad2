package fi.liikennevirasto.digiroad2

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
}
