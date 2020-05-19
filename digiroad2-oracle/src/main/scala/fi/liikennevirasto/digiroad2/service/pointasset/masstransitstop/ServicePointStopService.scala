package fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop

import java.util.NoSuchElementException

import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.{BoundingRectangle, LinkGeomSource, MassTransitStopAsset, Modification, Property, SimplePointAssetProperty}
import fi.liikennevirasto.digiroad2.dao.Queries.updateAssetGeometry
import fi.liikennevirasto.digiroad2.dao.{Sequences, ServicePoint, ServicePointBusStopDao}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.user.User

case class PersistedPublicTransportStop(id: Long, nationalId: Long, stopTypes: Seq[Int], municipalityCode: Int, lon: Double, lat: Double, validityPeriod: Option[String],
                                        vvhTimeStamp: Long, created: Modification, modified: Modification, propertyData: Seq[Property])

class ServicePointStopService(eventbus: DigiroadEventBus) {
  lazy val servicePointBusStopDao = new ServicePointBusStopDao()

  def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)

  def withId(id: Long)(query: String): String = {
    query + s" and a.id = $id"
  }

  def withNationalId(id: Long)(query: String): String = {
    query + s" and a.external_id = $id"
  }

  def create(lon: Double, lat: Double, properties: Seq[SimplePointAssetProperty], username: String, municipalityCode: Int, withTransaction: Boolean = true): Long = {
    def createAssetAndProperties = {
      val assetId = Sequences.nextPrimaryKeySeqValue
      servicePointBusStopDao.insertAsset(assetId, lon, lat, username, municipalityCode)
      servicePointBusStopDao.updateAssetProperties(assetId, properties)
      assetId
    }

    if (withTransaction) {
      withDynTransaction {
        createAssetAndProperties
      }
    } else {
      createAssetAndProperties
    }
  }

  def getById(id: Long): Option[ServicePoint] = {
    withDynSession {
      servicePointBusStopDao.fetchAsset(withId(id)).headOption
    }
  }

  def getByNationalId(nationalId: Long): Option[ServicePoint] = {
    withDynSession {
      servicePointBusStopDao.fetchAsset(withNationalId(nationalId)).headOption
    }
  }

  def fetchAsset(id: Long): ServicePoint = {
    servicePointBusStopDao.fetchAsset(withId(id)).headOption.getOrElse(throw new NoSuchElementException)
  }

  def expire(asset: ServicePoint, username: String, withTransaction: Boolean = true) = {
    if (withTransaction) {
      withDynTransaction {
        servicePointBusStopDao.expireMassTransitStop(username, asset.id)
      }
    } else {
      servicePointBusStopDao.expireMassTransitStop(username, asset.id)
    }
  }

  protected def updatePosition(id: Long, position: Point, municipalityCode: Int) = {
    servicePointBusStopDao.updateMunicipality(id, municipalityCode)
    updateAssetGeometry(id, position)
  }


  def enrichBusStop(asset: ServicePoint): (ServicePoint, Boolean) = {
    (asset.copy(propertyData = asset.propertyData), false)
  }

  def withFilter(filter: String)(query: String): String = {
    query + " " + filter
  }

  def getByBoundingBox(user: User, bounds: BoundingRectangle): Seq[ServicePoint] = {
    withDynSession {
      val boundingBoxFilter = OracleDatabase.boundingBoxFilter(bounds, "a.geometry")
      val filter = s"and a.asset_type_id = ${MassTransitStopAsset.typeId} and $boundingBoxFilter"
      servicePointBusStopDao.fetchAsset(withFilter(filter))
    }
  }

  def getByMunicipality(municipalityCode: Int): Seq[ServicePoint] = {
    withDynSession {
      val filter = s"and a.asset_type_id = ${MassTransitStopAsset.typeId}  and a.municipality_code = $municipalityCode"
      servicePointBusStopDao.fetchAsset(withFilter(filter))
    }
  }

  def update(assetId: Long, position: Point, properties: Seq[SimplePointAssetProperty], username: String, municipalityCode: Int, withTransaction: Boolean = true): ServicePoint = {
    def updateAssetAndProperties = {
      updatePosition(assetId, position, municipalityCode)
      servicePointBusStopDao.update(assetId, properties, username)
      fetchAsset(assetId)
    }

    if(withTransaction){
      withDynTransaction {
        updateAssetAndProperties
      }
    }else{
      updateAssetAndProperties
    }
  }

  def transformToPersistedMassTransitStop(servicePoints :Seq[ServicePoint]): Seq[PersistedMassTransitStop] = {
    servicePoints.map { sp =>
      PersistedMassTransitStop(sp.id, sp.nationalId, 0, sp.stopTypes, sp.municipalityCode, sp.lon, sp.lat, 0, None, None, None,
        floating = false, 0L, sp.created, sp.modified, sp.propertyData, LinkGeomSource.Unknown)
    }
  }
}
