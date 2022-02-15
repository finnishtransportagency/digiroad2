package fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop

import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.dao.{AssetPropertyConfiguration, MassTransitStopDao, Queries, Sequences}
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.GeometryTransform
import org.slf4j.LoggerFactory

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

class FormerTierekisteriFutureVelhoBusStopStrategy(typeId : Int, massTransitStopDao: MassTransitStopDao, roadLinkService: RoadLinkService, eventbus: DigiroadEventBus, geometryTransform: GeometryTransform) extends BusStopStrategy(typeId, massTransitStopDao, roadLinkService, eventbus, geometryTransform)
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

  override def was(existingAsset: PersistedMassTransitStop): Boolean = {
    val administrationClass = MassTransitStopOperations.getAdministrationClass(existingAsset.propertyData)
    val stopLiviId = existingAsset.propertyData.
      find(property => property.publicId == MassTransitStopOperations.LiViIdentifierPublicId).
      flatMap(property => property.values.headOption).map(p => p.asInstanceOf[PropertyValue].propertyValue)

    val tierekisteriStrategia =  stopLiviId.isDefined && TierekisteriBusStopStrategyOperations.isStoredInTierekisteri(existingAsset.propertyData, administrationClass)
    if(tierekisteriStrategia){
        logger.info(s"Is former tierekisteri asset with id: ${existingAsset.id}")
    }
    tierekisteriStrategia
  }
// check why digiroadApi test goes to terminated 
  override def enrichBusStop(persistedStop: PersistedMassTransitStop, roadLinkOption: Option[RoadLinkLike] = None): (PersistedMassTransitStop, Boolean) = {
    val enrichPersistedStop = { super.enrichBusStop(persistedStop, roadLinkOption)._1 }

    (enrichPersistedStop,true)
  }
  

  val toLiviId = "OTHJ%d"
  val MaxMovementDistanceMeters = 50
  
  override def publishSaveEvent(publishInfo: AbstractPublishInfo): Unit = {
    super.publishSaveEvent(publishInfo)
  }

  override def create(asset: NewMassTransitStop, username: String, point: Point, roadLink: RoadLink): (PersistedMassTransitStop, AbstractPublishInfo) ={
    validateBusStopDirections(asset.properties, roadLink) //is in supper

    val assetId = Sequences.nextPrimaryKeySeqValue //is in supper
    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue //is in supper
    val nationalId = massTransitStopDao.getNationalBusStopId //is in supper
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(point, roadLink.geometry) //is in supper
    val newAssetPoint = GeometryUtils.calculatePointFromLinearReference(roadLink.geometry, mValue).getOrElse(Point(asset.lon, asset.lat)) //is in supper
    val floating = !PointAssetOperations.coordinatesWithinThreshold(Some(point), GeometryUtils.calculatePointFromLinearReference(roadLink.geometry, mValue)) //is in supper
    massTransitStopDao.insertLrmPosition(lrmPositionId, mValue, asset.linkId, roadLink.linkSource) //is in supper
    massTransitStopDao.insertAsset(assetId, nationalId, newAssetPoint.x, newAssetPoint.y, asset.bearing, username, roadLink.municipalityCode, floating) //is in supper
    massTransitStopDao.insertAssetLink(assetId, lrmPositionId) //is in supper

    val properties = MassTransitStopOperations.setPropertiesDefaultValues(asset.properties, roadLink) //is in supper
    val defaultValues = massTransitStopDao.propertyDefaultValues(typeId).filterNot(defaultValue => properties.exists(_.publicId == defaultValue.publicId)) //is in supper
    if (MassTransitStopOperations.mixedStoptypes(properties.toSet)) //is in supper
      throw new IllegalArgumentException
    massTransitStopDao.updateAssetProperties(assetId, properties ++ defaultValues.toSet) //is in supper
    updateAdministrativeClassValue(assetId, roadLink.administrativeClass) //is in supper
    val liviId = toLiviId.format(nationalId)
    massTransitStopDao.updateTextPropertyValue(assetId, MassTransitStopOperations.LiViIdentifierPublicId, liviId)
    val persistedAsset = fetchAsset(assetId) //is in supper

    (persistedAsset, PublishInfo(Some(persistedAsset))) //is in supper
  }

  override def update(asset: PersistedMassTransitStop, optionalPosition: Option[Position], props: Set[SimplePointAssetProperty], username: String, municipalityValidation: (Int, AdministrativeClass) => Unit, roadLink: RoadLink): (PersistedMassTransitStop, AbstractPublishInfo) = {
    if(props.exists(prop => prop.publicId == "vaikutussuunta")) {
      validateBusStopDirections(props.toSeq, roadLink) //is in supper
    }
    val properties = MassTransitStopOperations.setPropertiesDefaultValues(props.toSeq, roadLink).toSet //is in supper
    if (MassTransitStopOperations.mixedStoptypes(properties)) //is in supper
      throw new IllegalArgumentException

    municipalityValidation(asset.municipalityCode, roadLink.administrativeClass) //is in supper

    // Enrich properties with old administrator, if administrator value is empty in CSV import
    val verifiedProperties = MassTransitStopOperations.getVerifiedProperties(properties, asset.propertyData)
    
    val liviId = getLiviIdValue(asset.propertyData).orElse(getLiviIdValue(properties.toSeq)).getOrElse(throw new NoSuchElementException)//livi specific

    optionalPosition.map(updatePositionWithBearing(asset.id, roadLink)) //is in supper
    massTransitStopDao.updateAssetLastModified(asset.id, username) //is in supper
    massTransitStopDao.updateAssetProperties(asset.id, verifiedProperties.toSeq) //is in supper
    massTransitStopDao.updateTextPropertyValue(asset.id, MassTransitStopOperations.LiViIdentifierPublicId, liviId) //livi specific
    updateAdministrativeClassValue(asset.id, roadLink.administrativeClass) //is in supper
    val resultAsset = enrichBusStop(fetchAsset(asset.id))._1
    (resultAsset, PublishInfo(Some(resultAsset)))
  }

  override def delete(asset: PersistedMassTransitStop): Option[AbstractPublishInfo] = {
    super.delete(asset)  //is in supper
  }
  
  private def getLiviIdValue(properties: Seq[AbstractProperty]) = {
    properties.find(_.publicId == MassTransitStopOperations.LiViIdentifierPublicId).flatMap(prop => prop.values.headOption).map(_.asInstanceOf[PropertyValue].propertyValue)
  }
  
  override def isFloating(persistedAsset: PersistedMassTransitStop, roadLinkOption: Option[RoadLinkLike]): (Boolean, Option[FloatingReason]) = {
    val floatingReason = persistedAsset.propertyData.find(_.publicId == MassTransitStopOperations.FloatingReasonPublicId).map(_.values).getOrElse(Seq()).headOption

    if(persistedAsset.floating && floatingReason.nonEmpty && floatingReason.get.asInstanceOf[PropertyValue].propertyValue == FloatingReason.TerminatedRoad.value.toString)
      (persistedAsset.floating, Some(FloatingReason.TerminatedRoad))
    else {
      (false, None)
    }
  }
  
}
