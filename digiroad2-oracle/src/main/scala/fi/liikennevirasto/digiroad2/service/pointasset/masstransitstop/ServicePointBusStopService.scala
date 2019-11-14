package fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop

import java.util.NoSuchElementException

import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, SimpleProperty}
import fi.liikennevirasto.digiroad2.dao.{MassTransitStopDao, Sequences, ServicePoint, ServicePointBusStopDao}
import fi.liikennevirasto.digiroad2.util.GeometryTransform

class ServicePointBusStopService(typeId : Int, eventbus: DigiroadEventBus, servicePointBusStopDao: ServicePointBusStopDao = new ServicePointBusStopDao, massTransitStopDao: MassTransitStopDao = new MassTransitStopDao) {

  private val validityDirectionPublicId = "vaikutussuunta"

  def create(asset: NewMassTransitStop, username: String, point: Point): (ServicePoint, AbstractPublishInfo) = {
    val assetId = Sequences.nextPrimaryKeySeqValue
    val nationalId = massTransitStopDao.getNationalBusStopId
    val newAssetPoint = Point(asset.lon, asset.lat)
    massTransitStopDao.insertAsset(assetId, nationalId, newAssetPoint.x, newAssetPoint.y, username,749/*, TODO municipality id*/, floating = false)

    val defaultValues = massTransitStopDao.propertyDefaultValues(typeId).filterNot(defaultValue => asset.properties.exists(_.publicId == defaultValue.publicId))
    if (asset.properties.find(p => p.publicId == MassTransitStopOperations.MassTransitStopTypePublicId).get.values.exists(v => v.propertyValue != MassTransitStopOperations.ServicePointBusStopPropertyValue))
      throw new IllegalArgumentException

    massTransitStopDao.updateAssetProperties(assetId, asset.properties.filterNot(p =>  p.publicId == validityDirectionPublicId) ++ defaultValues.toSet)
    val resultAsset = fetchAsset(assetId)
    (resultAsset,PublishInfo(None)/*TODO PublishInfo(Some(resultAsset))*/)
  }

  def fetchAsset(id: Long): ServicePoint = {
    servicePointBusStopDao.fetchAsset(massTransitStopDao.withId(id)).headOption.getOrElse(throw new NoSuchElementException)
  }

  def fetchAssetByNationalId(id: Long): Option[ServicePoint] = {
    servicePointBusStopDao.fetchAsset(massTransitStopDao.withNationalId(id)).headOption
  }

  def delete(asset: ServicePoint, username: String): Option[AbstractPublishInfo] = {
    servicePointBusStopDao.expire(asset.id, username)
    None
  }

  def update(asset: ServicePoint, properties: Set[SimpleProperty], username: String, municipalityValidation: (Int, AdministrativeClass) => Unit): (ServicePoint, AbstractPublishInfo) = {

    if (asset.propertyData.find(p => p.publicId == MassTransitStopOperations.MassTransitStopTypePublicId).get.values.exists(v => v.propertyValue != MassTransitStopOperations.ServicePointBusStopPropertyValue))
      throw new IllegalArgumentException

    //Enrich properties with old administrator, if administrator value is empty in CSV import
    val verifiedProperties = MassTransitStopOperations.getVerifiedProperties(properties, asset.propertyData)

    val id = asset.id
    massTransitStopDao.updateAssetLastModified(id, username)
    massTransitStopDao.updateAssetProperties(id, verifiedProperties.filterNot(p =>  p.publicId == validityDirectionPublicId).toSeq)

    val resultAsset = enrichBusStop(fetchAsset(id))._1
    (resultAsset, PublishInfo(None))
  }

  def enrichBusStop(asset: ServicePoint): (ServicePoint, Boolean) = {
    (asset.copy(propertyData = asset.propertyData), false)
  }
}
