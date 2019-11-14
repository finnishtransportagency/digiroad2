package fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop

import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.dao.{AssetPropertyConfiguration, MassTransitStopDao, Sequences}
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.GeometryTransform

class ServicePointBusStopStrategy(typeId : Int, massTransitStopDao: MassTransitStopDao, roadLinkService: RoadLinkService, eventbus: DigiroadEventBus, geometryTransform: GeometryTransform) extends BusStopStrategy(typeId, massTransitStopDao, roadLinkService, eventbus, geometryTransform){

  override def is(newProperties: Set[SimpleProperty], roadLink: Option[RoadLink], existingAssetOption: Option[PersistedMassTransitStop]): Boolean = {
    val properties = existingAssetOption match {
      case Some(existingAsset) =>
        (existingAsset.propertyData.
          filterNot(property => newProperties.exists(_.publicId == property.publicId)).
          map(property => SimpleProperty(property.publicId, property.values)) ++ newProperties).
          filterNot(property => AssetPropertyConfiguration.commonAssetProperties.exists(_._1 == property.publicId))
      case _ => newProperties.toSeq
    }

    properties.exists(p => p.publicId == MassTransitStopOperations.MassTransitStopTypePublicId &&
      p.values.exists(v => v.propertyValue == BusStopType.ServicePoint.value.toString))
  }

  override def isFloating(persistedAsset: PersistedMassTransitStop, roadLinkOption: Option[RoadLinkLike]): (Boolean, Option[FloatingReason]) = {
    (false, None)
  }

  override def publishSaveEvent(publishInfo: AbstractPublishInfo): Unit = {
    super.publishSaveEvent(publishInfo)
    eventbus.publish("service_point:saved", publishInfo)
  }
}
