package fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop

import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.tierekisteri.{Equipment, TierekisteriBusStopMarshaller, TierekisteriMassTransitStop}
import fi.liikennevirasto.digiroad2.dao.{AssetPropertyConfiguration, MassTransitStopDao, Queries, Sequences}
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.DataFixture.massTransitStopService.massTransitStopEnumeratedPropertyValues
import fi.liikennevirasto.digiroad2.util.GeometryTransform
import org.slf4j.LoggerFactory

import scala.util.Try

object TierekisteriBusStopStrategyOperations{

  /**
    * Verify if the stop is relevant to Tierekisteri: Must be non-virtual and must be administered by ELY or HSL.
    * Convenience method
    *
    */
  def isStoredInTierekisteri(properties: Seq[AbstractProperty], administrativeClass: Option[AdministrativeClass]): Boolean = {
    val administrationProperty = properties.find(_.publicId == MassTransitStopOperations.AdministratorInfoPublicId)
    val stopType = properties.find(pro => pro.publicId == MassTransitStopOperations.MassTransitStopTypePublicId)
    val elyAdministrated = administrationProperty.exists(_.values.headOption.exists(_.asInstanceOf[PropertyValue].propertyValue == MassTransitStopOperations.CentralELYPropertyValue))
    val isVirtualStop = stopType.exists(_.values.exists(_.asInstanceOf[PropertyValue].propertyValue == MassTransitStopOperations.VirtualBusStopPropertyValue))
    val isHSLAdministrated =  administrationProperty.exists(_.values.headOption.exists(_.asInstanceOf[PropertyValue].propertyValue == MassTransitStopOperations.HSLPropertyValue))
    val isAdminClassState = administrativeClass.contains(State)
    val isOnTerminatedRoad = properties.find(_.publicId == MassTransitStopOperations.FloatingReasonPublicId).exists(_.values.headOption.exists(_.asInstanceOf[PropertyValue].propertyValue == FloatingReason.TerminatedRoad.value.toString))
    !isVirtualStop && (elyAdministrated || (isHSLAdministrated && isAdminClassState)) && !isOnTerminatedRoad
  }
}



class TierekisteriBusStopStrategy(typeId : Int, massTransitStopDao: MassTransitStopDao, roadLinkService: RoadLinkService, eventbus: DigiroadEventBus, geometryTransform: GeometryTransform) extends BusStopStrategy(typeId, massTransitStopDao, roadLinkService, eventbus, geometryTransform)
{
  lazy val logger = LoggerFactory.getLogger(getClass)
  override def is(newProperties: Set[SimplePointAssetProperty], roadLink: Option[RoadLink], existingAssetOption: Option[PersistedMassTransitStop]): Boolean = {
    val properties = existingAssetOption match {
      case Some(existingAsset) =>
        (existingAsset.propertyData.
          filterNot(property => newProperties.exists(_.publicId == property.publicId)).
          map(property => SimplePointAssetProperty(property.publicId, property.values)) ++ newProperties).
          filterNot(property => AssetPropertyConfiguration.commonAssetProperties.exists(_._1 == property.publicId))
      case _ => newProperties.toSeq
    }
    val trSave = newProperties.
      find(property => property.publicId == AssetPropertyConfiguration.TrSaveId).
      flatMap(property => property.values.headOption).map(p => p.asInstanceOf[PropertyValue].propertyValue)
    val tierekisteriStrategia =  trSave.isEmpty && TierekisteriBusStopStrategyOperations.isStoredInTierekisteri(properties, roadLink.map(_.administrativeClass))
    if(tierekisteriStrategia){
      if(existingAssetOption.isDefined){
        logger.info(s"Is former tierekisteri asset with id: ${existingAssetOption.get.id}")
      }else {
        logger.info(s"Is former tierekisteri asset")
      }
      
    }
    tierekisteriStrategia
  }

  override def enrichBusStop(persistedStop: PersistedMassTransitStop, roadLinkOption: Option[RoadLinkLike] = None): (PersistedMassTransitStop, Boolean) = {
    val enrichPersistedStop = { super.enrichBusStop(persistedStop, roadLinkOption)._1 }

    (enrichPersistedStop,true)
  }
  /**
    * Override the properties values passed as parameter using override operations
    *
    * @param tierekisteriStop         Tierekisteri Asset
    * @param persistedMassTransitStop Asset properties
    * @return Sequence of overridden properties
    */
  private def enrichWithTierekisteriInfo(persistedMassTransitStop: PersistedMassTransitStop, tierekisteriStop: TierekisteriMassTransitStop): PersistedMassTransitStop = {
    val overridePropertyValueOperations: Seq[(TierekisteriMassTransitStop, Property) => Property] = Seq(
      setEquipments,
      setTextPropertyValueIfEmpty(MassTransitStopOperations.nameFiPublicId, { ta => ta.nameFi.getOrElse("") }),
      setTextPropertyValueIfEmpty(MassTransitStopOperations.nameSePublicId, { ta => ta.nameSe.getOrElse("") })
      //In the future if we need to override some property just add here the operation
    )

    persistedMassTransitStop.copy(propertyData = persistedMassTransitStop.propertyData.map {
      property =>
        overridePropertyValueOperations.foldLeft(property) { case (prop, operation) =>
          operation(tierekisteriStop, prop)
        }
    })
  }

  private def setTextPropertyValueIfEmpty(publicId: String, getValue: TierekisteriMassTransitStop => String)(tierekisteriStop: TierekisteriMassTransitStop, property: Property): Property = {
    if (property.publicId == publicId && property.values.isEmpty) {
      val propertyValueString = getValue(tierekisteriStop)
      property.copy(values = Seq(new PropertyValue(propertyValueString, Some(propertyValueString))))
    } else {
      property
    }
  }

  /**
    * Override property values of all equipment properties
    *
    * @param tierekisteriStop Tierekisteri Asset
    * @param property         Asset property
    * @return Property passed as parameter if have no match with equipment property or property overriden with tierekisteri values
    */
  private def setEquipments(tierekisteriStop: TierekisteriMassTransitStop, property: Property) = {
    if (tierekisteriStop.equipments.isEmpty) {
      property
    } else {
      val equipment = Equipment.fromPublicId(property.publicId)
      val existence = tierekisteriStop.equipments.get(equipment)
      existence.isEmpty || !equipment.isMaster match {
        case true => property
        case false =>
          val propertyValueString = existence.get.propertyValue.toString
          val propertyOverrideValue = massTransitStopEnumeratedPropertyValues.
            get(property.publicId).get.find(_.asInstanceOf[PropertyValue].propertyValue == propertyValueString).get
          property.copy(values = Seq(propertyOverrideValue))
      }
    }
  }

}
