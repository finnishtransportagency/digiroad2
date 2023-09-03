package fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop

import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.dao.{AssetPropertyConfiguration, MassTransitStopDao, Sequences}
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.GeometryTransform
import org.slf4j.{Logger, LoggerFactory}


object OthBusStopLifeCycleBusStopStrategy{

  /**
    * Verify if the stop is relevant to OthLiviId: Must be non-virtual and must be administered by ELY or HSL.
    * Convenience method
    *
    */
  def isOthLiviId(properties: Seq[AbstractProperty], administrativeClass: Option[AdministrativeClass]): Boolean = {
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

class OthBusStopLifeCycleBusStopStrategy(typeId : Int, massTransitStopDao: MassTransitStopDao, roadLinkService: RoadLinkService, eventbus: DigiroadEventBus, geometryTransform: GeometryTransform) extends BusStopStrategy(typeId, massTransitStopDao, roadLinkService, eventbus, geometryTransform)
{
  override lazy val logger: Logger = LoggerFactory.getLogger(getClass)

  val toLiviId = "OTHJ%d"
  val MaxMovementDistanceMeters = 50
  
  override def is(newProperties: Set[SimplePointAssetProperty], roadLink: Option[RoadLink], existingAssetOption: Option[PersistedMassTransitStop]): Boolean = {
    val properties = existingAssetOption match {
      case Some(existingAsset) =>
        (existingAsset.propertyData.
          filterNot(property => newProperties.exists(_.publicId == property.publicId)).
          map(property => SimplePointAssetProperty(property.publicId, property.values)) ++ newProperties).
          filterNot(property => AssetPropertyConfiguration.commonAssetProperties.exists(_._1 == property.publicId))
      case _ => newProperties.toSeq
    }
    val liviIdSave = newProperties.
      find(property => property.publicId == AssetPropertyConfiguration.LiviIdSave).
      flatMap(property => property.values.headOption).map(p => p.asInstanceOf[PropertyValue].propertyValue)
    liviIdSave.isEmpty && OthBusStopLifeCycleBusStopStrategy.isOthLiviId(properties, roadLink.map(_.administrativeClass))
  }

  override def was(existingAsset: PersistedMassTransitStop): Boolean = {
    val administrationClass = MassTransitStopOperations.getAdministrationClass(existingAsset.propertyData)
    val stopLiviId = existingAsset.propertyData.
      find(property => property.publicId == MassTransitStopOperations.LiViIdentifierPublicId).
      flatMap(property => property.values.headOption).map(p => p.asInstanceOf[PropertyValue].propertyValue)

  stopLiviId.isDefined && OthBusStopLifeCycleBusStopStrategy.isOthLiviId(existingAsset.propertyData, administrationClass)
  }

  override def undo(existingAsset: PersistedMassTransitStop, newProperties: Set[SimplePointAssetProperty], username: String): Unit = {
    //Remove the Livi ID
    massTransitStopDao.updateTextPropertyValue(existingAsset.id, MassTransitStopOperations.LiViIdentifierPublicId, null)
  }

  override def enrichBusStop(persistedStop: PersistedMassTransitStop, roadLinkOption: Option[RoadLinkLike] = None): (PersistedMassTransitStop, Boolean) = {
    val enrichPersistedStop = { super.enrichBusStop(persistedStop, roadLinkOption)._1 }
    if(enrichPersistedStop.id !=0 ){
      (enrichPersistedStop,true)
    }else{
      (enrichPersistedStop,false)
    }
    
  }

  override def enrichBusStopsOperation(persistedStops: Seq[PersistedMassTransitStop], links: Seq[RoadLink]): Seq[PersistedMassTransitStop] = {
    persistedStops
  }
  
  override def publishSaveEvent(publishInfo: AbstractPublishInfo): Unit = {
    super.publishSaveEvent(publishInfo)
  }

  override def create(asset: NewMassTransitStop, username: String, point: Point, roadLink: RoadLink): (PersistedMassTransitStop, AbstractPublishInfo) ={
    
    validateBusStopDirections(asset.properties, roadLink) 

    val assetId = Sequences.nextPrimaryKeySeqValue
    val lrmPositionId = Sequences.nextLrmPositionPrimaryKeySeqValue
    val nationalId = massTransitStopDao.getNationalBusStopId
    val mValue = GeometryUtils.calculateLinearReferenceFromPoint(point, roadLink.geometry)
    val newAssetPoint = GeometryUtils.calculatePointFromLinearReference(roadLink.geometry, mValue).getOrElse(Point(asset.lon, asset.lat))
    val floating = !PointAssetOperations.coordinatesWithinThreshold(Some(point), GeometryUtils.calculatePointFromLinearReference(roadLink.geometry, mValue))
    massTransitStopDao.insertLrmPosition(lrmPositionId, mValue, asset.linkId, roadLink.linkSource)
    massTransitStopDao.insertAsset(assetId, nationalId, newAssetPoint.x, newAssetPoint.y, asset.bearing, username, roadLink.municipalityCode, floating)
    massTransitStopDao.insertAssetLink(assetId, lrmPositionId)
    
    val properties = MassTransitStopOperations.setPropertiesDefaultValues(asset.properties, roadLink)
    
    val defaultValues = massTransitStopDao.propertyDefaultValues(typeId).filterNot(defaultValue => properties.exists(_.publicId == defaultValue.publicId))
    if (MassTransitStopOperations.mixedStoptypes(properties.toSet))
      throw new IllegalArgumentException
    
    massTransitStopDao.updateAssetProperties(assetId, properties ++ defaultValues.toSet)
    updateAdministrativeClassValue(assetId, roadLink.administrativeClass)
    
    val liviId = toLiviId.format(nationalId)
    massTransitStopDao.updateTextPropertyValue(assetId, MassTransitStopOperations.LiViIdentifierPublicId, liviId)
    
    val persistedAsset = fetchAsset(assetId)
    (persistedAsset, PublishInfo(Some(persistedAsset)))
  }

  override def update(asset: PersistedMassTransitStop, optionalPosition: Option[Position], props: Set[SimplePointAssetProperty], username: String, municipalityValidation: (Int, AdministrativeClass) => Unit, roadLink: RoadLink): (PersistedMassTransitStop, AbstractPublishInfo) = {
    if(props.exists(prop => prop.publicId == "vaikutussuunta")) {
      validateBusStopDirections(props.toSeq, roadLink)
    }

    val properties = MassTransitStopOperations.setPropertiesDefaultValues(props.toSeq, roadLink).toSet

    if (MassTransitStopOperations.mixedStoptypes(properties))
      throw new IllegalArgumentException

    municipalityValidation(asset.municipalityCode, roadLink.administrativeClass)

    // Enrich properties with old administrator, if administrator value is empty in CSV import
    val verifiedProperties = MassTransitStopOperations.getVerifiedProperties(properties, asset.propertyData)

    //Remove from common assets the side code property
    val commonAssetProperties = AssetPropertyConfiguration.commonAssetProperties.
      filterNot(prop => prop._1 == AssetPropertyConfiguration.ValidityDirectionId || prop._1 == AssetPropertyConfiguration.ValidToId)

    val mergedProperties = (asset.propertyData.
      filterNot(property => properties.exists(_.publicId == property.publicId)).
      map(property => SimplePointAssetProperty(property.publicId, property.values)) ++ properties).
      filterNot(property => commonAssetProperties.exists(_._1 == property.publicId))

    //If it all ready has liviId
    if (was(asset)) {
      val liviId = getLiviIdValue(asset.propertyData).orElse(getLiviIdValue(properties.toSeq)).getOrElse(throw new NoSuchElementException)
      if (calculateMovedDistance(asset, optionalPosition) > MaxMovementDistanceMeters) {
        //Expires the current asset and creates a new one in OTH with new liviId
        val position = optionalPosition.get
        massTransitStopDao.expireMassTransitStop(username, asset.id)
        super.publishExpiringEvent(PublishInfo(Option(asset)))
        create(NewMassTransitStop(position.lon, position.lat, roadLink.linkId, position.bearing.getOrElse(asset.bearing.get),
          mergedProperties), username, Point(position.lon, position.lat), roadLink)

      }else{
        //Updates the asset in OTH
        update(asset, optionalPosition, verifiedProperties.toSeq, roadLink, liviId,
          username)
      }
    }else{
      //Updates the asset in OTH with new liviId
      update(asset, optionalPosition, verifiedProperties.toSeq, roadLink, toLiviId.format(asset.nationalId),
        username)
    }
  }


  private def update(asset: PersistedMassTransitStop, optionalPosition: Option[Position], properties: Seq[SimplePointAssetProperty], roadLink: RoadLink, liviId: String, username: String): (PersistedMassTransitStop, AbstractPublishInfo) = {
    optionalPosition.map(updatePositionWithBearing(asset.id, roadLink))
    massTransitStopDao.updateAssetLastModified(asset.id, username)
    massTransitStopDao.updateAssetProperties(asset.id, properties)
    massTransitStopDao.updateTextPropertyValue(asset.id, MassTransitStopOperations.LiViIdentifierPublicId, liviId)
    updateAdministrativeClassValue(asset.id, roadLink.administrativeClass)

    val persistedAsset = enrichBusStop(fetchAsset(asset.id))._1

    (persistedAsset, PublishInfo(Some(persistedAsset)))
  }
  
  private def calculateMovedDistance(asset: PersistedMassTransitStop, optionalPosition: Option[Position]): Double = {
    optionalPosition match {
      case Some(position) =>
        val assetPoint = Point(asset.lon, asset.lat)
        val newPoint = Point(position.lon, position.lat)
        assetPoint.distance2DTo(newPoint)
      case _ => 0
    }
  }
  override def delete(asset: PersistedMassTransitStop): Option[AbstractPublishInfo] = {
    super.delete(asset)
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
