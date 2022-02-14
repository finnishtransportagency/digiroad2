package fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop

import java.util.{Date, NoSuchElementException}
import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.tierekisteri.{Equipment, TierekisteriBusStopMarshaller, TierekisteriMassTransitStop, TierekisteriMassTransitStopClient}
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



class TierekisteriBusStopStrategy(typeId : Int, massTransitStopDao: MassTransitStopDao, roadLinkService: RoadLinkService, tierekisteriClient: TierekisteriMassTransitStopClient, eventbus: DigiroadEventBus, geometryTransform: GeometryTransform) extends BusStopStrategy(typeId, massTransitStopDao, roadLinkService, eventbus, geometryTransform)
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
  
}
