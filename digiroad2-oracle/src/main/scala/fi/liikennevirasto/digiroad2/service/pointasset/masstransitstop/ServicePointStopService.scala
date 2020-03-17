package fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop

import java.util.NoSuchElementException

import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, BoundingRectangle, MassTransitStopAsset, Modification, PositionCoordinates, Property, PropertyValue, SimplePointAssetProperty}
import fi.liikennevirasto.digiroad2.dao.Queries.updateAssetGeometry
import fi.liikennevirasto.digiroad2.dao.{AssetPropertyConfiguration, Sequences, ServicePoint, ServicePointBusStopDao}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.user.User

case class ServicePointPublishInfo(asset: Option[ServicePoint], detachAsset: Seq[Long], attachedAsset: Seq[Long])
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

  def create(lon: Double, lat: Double, properties: Seq[SimplePointAssetProperty], username: String, municipalityCode: Int): Long = {
    withDynTransaction {
      val assetId = Sequences.nextPrimaryKeySeqValue
      servicePointBusStopDao.insertAsset(assetId, lon, lat, username, municipalityCode)
      servicePointBusStopDao.updateAssetProperties(assetId, properties)

      assetId
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

  def expire(asset: ServicePoint, username: String) = {
    servicePointBusStopDao.expire(asset.id, username)
  }

  protected def updatePosition(id: Long, position: PositionCoordinates, municipalityCode: Int) = {
    val point = Point(position.lon, position.lat)
    servicePointBusStopDao.updateMunicipality(id, municipalityCode)
    updateAssetGeometry(id, point)
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
      val filter = s"and a.asset_type_id = ${MassTransitStopAsset.typeId} and $boundingBoxFilter and ( a.valid_to is null or a.valid_to > sysdate)"
      servicePointBusStopDao.fetchAsset(withFilter(filter))
    }
  }

  def getByMunicipality(municipalityCode: Int): Seq[ServicePoint] = {
    withDynSession {
      val filter = s"and a.asset_type_id = ${MassTransitStopAsset.typeId}  and a.municipality_code = $municipalityCode and ( a.valid_to is null or a.valid_to > sysdate)"
      servicePointBusStopDao.fetchAsset(withFilter(filter))
    }
  }

  def update(assetId: Long, position: PositionCoordinates, properties: Seq[SimplePointAssetProperty], username: String, municipalityCode: Int): ServicePoint = {
    withDynSession {
      servicePointBusStopDao.updateAssetLastModified(assetId, username)
      updatePosition(assetId, position, municipalityCode)
      servicePointBusStopDao.update(assetId, properties, username)

      val resultAsset = fetchAsset(assetId)
      resultAsset
    }
  }
}
