package fi.liikennevirasto.digiroad2.service.pointasset

import com.jolbox.bonecp.{BoneCPConfig, BoneCPDataSource}
import fi.liikennevirasto.digiroad2.asset.{BoundingRectangle, ServicePointsClass}
import fi.liikennevirasto.digiroad2.dao.pointasset.{IncomingServicePoint, PostGISServicePointDao, ServicePoint}
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.util.Digiroad2Properties

class ServicePointService {
  val typeId: Int = 250

  def create(asset: IncomingServicePoint, municipalityCode: Int, username: String, newTransaction: Boolean = true) = {
    if(newTransaction) {
      withDynTransaction {
        checkAuthorityData(asset)
        PostGISServicePointDao.create(asset, municipalityCode, username)
      }
    } else {
      checkAuthorityData(asset)
      PostGISServicePointDao.create(asset, municipalityCode, username)
    }
  }

  def update(id: Long, updatedAsset: IncomingServicePoint, municipalityCode: Int, username: String): Long = {
    withDynTransaction {
      checkAuthorityData(updatedAsset)
      PostGISServicePointDao.update(id, updatedAsset, municipalityCode, username)
    }
  }

  def expire(id: Long, username: String): Long = {
    withDynTransaction {
      PostGISServicePointDao.expire(id, username)
    }
  }

  def get: Set[ServicePoint] = {
    withDynSession {
      PostGISServicePointDao.get
    }
  }

  def getById(id: Long): ServicePoint = {
    withDynSession {
      PostGISServicePointDao.getById(id).headOption.getOrElse(throw new NoSuchElementException("Asset not found"))
    }
  }


  def getByMunicipality(municipalityNumber: Int): Set[ServicePoint] = {
    withDynSession {
      PostGISServicePointDao.getByMunicipality(municipalityNumber)
    }
  }

  def get(boundingBox: BoundingRectangle): Set[ServicePoint] = {
    withDynSession {
      PostGISServicePointDao.get(boundingBox)
    }
  }

  lazy val dataSource = {
    val cfg = new BoneCPConfig(Digiroad2Properties.bonecpProperties)
    new BoneCPDataSource(cfg)
  }

  def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)

  def withDynSession[T](f: => T): T = PostGISDatabase.withDynSession(f)

  def getPersistedAssetsByIds(ids: Set[Long]): Set[ServicePoint] = {
    withDynSession {
      val idsStr = ids.toSeq.mkString(",")
      val filter = s"a.id in ($idsStr)"
      fetchPointAssets(filter)
    }
  }

  def fetchPointAssets(filter: String): Set[ServicePoint] = PostGISServicePointDao.fetchByFilter(filter)


  def checkAuthorityData(incomingServicePoint: IncomingServicePoint) = {
    incomingServicePoint.services.foreach { service =>
      val shouldBe = ServicePointsClass.apply(service.serviceType)
      if(shouldBe != service.isAuthorityData)
        throw new ServicePointException(s"The authority data is not matching for type ${service.serviceType}")
    }
  }
}
class ServicePointException(val servicePointException: String) extends RuntimeException { override def getMessage: String = servicePointException }


