package fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop

import java.util.NoSuchElementException

import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.{BoundingRectangle, LinkGeomSource, MassTransitStopAsset, Modification, PositionCoordinates, Property, PropertyValue, ServicePointsClass, SimplePointAssetProperty}
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
        servicePointBusStopDao.expire(asset.id, username)
      }
    } else {
      servicePointBusStopDao.expire(asset.id, username)
    }
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

  def update(assetId: Long, position: PositionCoordinates, properties: Seq[SimplePointAssetProperty], username: String, municipalityCode: Int, withTransaction: Boolean = true): ServicePoint = {
    def update = {
      servicePointBusStopDao.updateAssetLastModified(assetId, username)
      updatePosition(assetId, position, municipalityCode)
      servicePointBusStopDao.update(assetId, properties, username)

      val resultAsset = fetchAsset(assetId)
      resultAsset
    }

    if(withTransaction){
      withDynTransaction {
        update
      }
    }else{
      update
    }
  }

  def transformToPersistedMassTransitStop(servicePoints :Seq[ServicePoint]): Seq[PersistedMassTransitStop] = {
    servicePoints.map { sp =>
      val serviceTypeId = sp.propertyData.find(_.publicId == "palvelu").get.values.head.asInstanceOf[PropertyValue].propertyValue.toInt
      val serviceType = ServicePointsClass.values.find(_.value == serviceTypeId).get

      val servicePointName = if(serviceType == ServicePointsClass.RailwayStation) {
        val serviceSubType = sp.propertyData.find(_.publicId == "tarkenne").get.values.head.asInstanceOf[PropertyValue].propertyValue.toInt
        val subTypes = serviceType.subTypeName.map(_.swap)

        subTypes(serviceSubType)
      } else
        serviceType.labelName

      val servicePointNameProperty = Property(0, "nimi_suomeksi", "name", values = Seq(PropertyValue(servicePointName)))

      PersistedMassTransitStop(sp.id, sp.nationalId, 0, sp.stopTypes, sp.municipalityCode, sp.lon, sp.lat, 0, None, None, None,
        floating = false, 0L, sp.created, sp.modified, sp.propertyData :+ servicePointNameProperty, LinkGeomSource.Unknown)
    }
  }
}
