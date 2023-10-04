package fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop

import java.util.NoSuchElementException

import fi.liikennevirasto.digiroad2.{DigiroadEventBus, FloatingReason, Point}
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, Position, Property, PropertyValue, SimplePointAssetProperty}
import fi.liikennevirasto.digiroad2.dao.{AssetPropertyConfiguration, MassTransitStopDao}
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop.MassTransitStopOperations._
import fi.liikennevirasto.digiroad2.util.GeometryTransform

class TerminatedBusStopStrategy(typeId: Int, massTransitStopDao: MassTransitStopDao, roadLinkService: RoadLinkService, eventbus: DigiroadEventBus, geometryTransform: GeometryTransform) extends BusStopStrategy(typeId, massTransitStopDao, roadLinkService, eventbus, geometryTransform) {
  val defaultChanges: Seq[String] = Seq(InventoryDateId, RoadName_FI, RoadName_SE, MassTransitStopTypePublicId)
  val allowedChanges: Seq[String] = Seq(EndDate, AdministratorInfoPublicId)

  def allowedPropertyChanges(properties: Set[SimplePointAssetProperty]): Boolean = {
    properties.map(_.publicId).filterNot(defaultChanges.contains).forall(allowedChanges.contains)
  }
  def changesOnDefaultValues(properties: Set[SimplePointAssetProperty], asset: PersistedMassTransitStop): Boolean = {
    properties.filter(prop => defaultChanges.contains(prop.publicId)).map(_.values).toSeq.diff(asset.propertyData.filter(prop => defaultChanges.contains(prop.publicId)).map(_.values)).nonEmpty
  }

  override def enrichBusStopsOperation(persistedStops: Seq[PersistedMassTransitStop], links: Seq[RoadLink]): Seq[PersistedMassTransitStop] = {
    persistedStops
  }
  
  override def create(asset: NewMassTransitStop, username: String, point: Point, roadLink: RoadLink): (PersistedMassTransitStop, AbstractPublishInfo) = {
    throw new UnsupportedOperationException
  }
  override def pickRoadLink(optRoadLink: Option[RoadLink], optHistoric: Option[RoadLink]): RoadLink = {
    optHistoric.getOrElse(throw new NoSuchElementException)
  }
  override def is(newProperties: Set[SimplePointAssetProperty], roadLink: Option[RoadLink], existingAssetOption: Option[PersistedMassTransitStop]): Boolean = {
    val properties = existingAssetOption match {
      case Some(existingAsset) =>
        (existingAsset.propertyData.
          filterNot(property => newProperties.exists(_.publicId == property.publicId)).
          map(property => SimplePointAssetProperty(property.publicId, property.values)) ++ newProperties).
          filterNot(property => AssetPropertyConfiguration.commonAssetProperties.exists(_._1 == property.publicId))
      case _ => newProperties.toSeq
    }
    val isOnTerminatedRoad = properties.find(_.publicId == MassTransitStopOperations.FloatingReasonPublicId).exists(_.values.headOption.exists(_.asInstanceOf[PropertyValue].propertyValue == FloatingReason.TerminatedRoad.value.toString))
    isOnTerminatedRoad
  }
  override def update(asset: PersistedMassTransitStop, optionalPosition: Option[Position], properties: Set[SimplePointAssetProperty], username: String, municipalityValidation: (Int, AdministrativeClass) => Unit, roadLink: RoadLink): (PersistedMassTransitStop, AbstractPublishInfo) = {
    if(!allowedPropertyChanges(properties) || changesOnDefaultValues(properties, asset))
      throw new UnsupportedOperationException("Changes on bus stops on terminated roads only allowed on: " + allowedChanges.mkString(","))

    municipalityValidation(asset.municipalityCode, roadLink.administrativeClass)
    massTransitStopDao.updateAssetLastModified(asset.id, username)
    updatePropertiesForAsset(asset.id, properties.toSeq, roadLink.administrativeClass, asset.nationalId)

    val resultAsset = enrichBusStop(fetchAsset(asset.id))._1
    (resultAsset, PublishInfo(Some(resultAsset)))
  }

  override def isFloating(persistedAsset: PersistedMassTransitStop, roadLinkOption: Option[RoadLinkLike]): (Boolean, Option[FloatingReason]) = { (true, Some(FloatingReason.TerminatedRoad)) }
}
