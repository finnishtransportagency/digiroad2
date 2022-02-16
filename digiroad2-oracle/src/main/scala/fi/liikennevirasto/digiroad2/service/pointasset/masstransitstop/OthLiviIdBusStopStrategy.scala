package fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop

import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.dao.{AssetPropertyConfiguration, MassTransitStopDao, Queries, RoadAddress, Sequences}
import fi.liikennevirasto.digiroad2.linearasset.{RoadLink, RoadLinkLike}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.GeometryTransform
import org.joda.time.format.DateTimeFormat
import org.slf4j.LoggerFactory

import java.util.Date












/**
  * Values for Existence (Olemassaolo) enumeration
  */
sealed trait Existence {
  def value: String
  def propertyValue: Int
}
object Existence {
  val values = Set(Yes, No, Unknown)

  def apply(value: String): Existence = {
    values.find(_.value == value).getOrElse(Unknown)
  }

  def fromPropertyValue(value: String): Existence = {
    value match {
      case "1" => No
      case "2" => Yes
      case _ => Unknown
    }
  }

  case object Yes extends Existence { def value = "on"; def propertyValue = 2; }
  case object No extends Existence { def value = "ei"; def propertyValue = 1; }
  case object Unknown extends Existence { def value = "ei_tietoa"; def propertyValue = 99; }
}


/**
  * Values for Equipment (Varuste) enumeration
  */
sealed trait Equipment {
  def value: String
  def publicId: String
  def isMaster: Boolean
}
object Equipment {
  val values = Set[Equipment](Timetable, TrashBin, BikeStand, Lighting, Seat, Roof, RoofMaintainedByAdvertiser, ElectronicTimetables, CarParkForTakingPassengers, RaisedBusStop)

  def apply(value: String): Equipment = {
    values.find(_.value == value).getOrElse(Unknown)
  }

  def fromPublicId(value: String): Equipment = {
    values.find(_.publicId == value).getOrElse(Unknown)
  }

  case object Timetable extends Equipment { def value = "aikataulu"; def publicId = "aikataulu"; def isMaster = true; }
  case object TrashBin extends Equipment { def value = "roskis"; def publicId = "roska_astia"; def isMaster = true; }
  case object BikeStand extends Equipment { def value = "pyorateline"; def publicId = "pyorateline"; def isMaster = true; }
  case object Lighting extends Equipment { def value = "valaistus"; def publicId = "valaistus"; def isMaster = true; }
  case object Seat extends Equipment { def value = "penkki"; def publicId = "penkki"; def isMaster = true; }
  case object Roof extends Equipment { def value = "katos"; def publicId = "katos"; def isMaster = false; }
  case object RoofMaintainedByAdvertiser extends Equipment { def value = "mainoskatos"; def publicId = "mainoskatos"; def isMaster = false; }
  case object ElectronicTimetables extends Equipment { def value = "sahk_aikataulu"; def publicId = "sahkoinen_aikataulunaytto"; def isMaster = false; }
  case object CarParkForTakingPassengers extends Equipment { def value = "saattomahd"; def publicId = "saattomahdollisuus_henkiloautolla"; def isMaster = false; }
  case object RaisedBusStop extends Equipment { def value = "korotus"; def publicId = "korotettu"; def isMaster = false; }
  case object Unknown extends Equipment { def value = "UNKNOWN"; def publicId = "tuntematon"; def isMaster = false; }
}

/**
  * Values for Stop type (Pysäkin tyyppi) enumeration
  */
sealed trait StopType {
  def value: String
  def propertyValues: Set[Int]
}
object StopType {
  val values = Set[StopType](Commuter, LongDistance, Combined, Virtual, Unknown)

  def apply(value: String): StopType = {
    values.find(_.value == value).getOrElse(Unknown)
  }

  def propertyValues() : Set[Int] = {
    values.flatMap(_.propertyValues)
  }

  case object Commuter extends StopType { def value = "paikallis"; def propertyValues = Set(2); }
  case object LongDistance extends StopType { def value = "kauko"; def propertyValues = Set(3); }
  case object Combined extends StopType { def value = "molemmat"; def propertyValues = Set(2,3); }
  case object Virtual extends StopType { def value = "virtuaali"; def propertyValues = Set(5); }
  case object Unknown extends StopType { def value = "tuntematon"; def propertyValues = Set(99); }  // Should not be passed on interface
}

case class TierekisteriMassTransitStop(nationalId: Long,
                                       liviId: String,
                                       roadAddress: RoadAddress,
                                       roadSide: TRRoadSide,
                                       stopType: StopType,
                                       express: Boolean,
                                       equipments: Map[Equipment, Existence] = Map(),
                                       stopCode: Option[String],
                                       nameFi: Option[String],
                                       nameSe: Option[String],
                                       modifiedBy: String,
                                       operatingFrom: Option[Date],
                                       operatingTo: Option[Date],
                                       removalDate: Option[Date],
                                       inventoryDate: Date)



/**
  * Values for Road side (Puoli) enumeration
  */
sealed trait TRRoadSide {
  def value: String
  def propertyValues: Set[Int]
}
object TRRoadSide {
  val values = Set(Right, Left, Off, Unknown)

  def apply(value: String): TRRoadSide = {
    values.find(_.value == value).getOrElse(Unknown)
  }

  def propertyValues() : Set[Int] = {
    values.flatMap(_.propertyValues)
  }

  case object Right extends TRRoadSide { def value = "oikea"; def propertyValues = Set(1) }
  case object Left extends TRRoadSide { def value = "vasen"; def propertyValues = Set(2) }
  case object Off extends TRRoadSide { def value = "paassa"; def propertyValues = Set(99) } // Not supported by OTH
  case object Unknown extends TRRoadSide { def value = "ei tietoa"; def propertyValues = Set(0) }
}

sealed trait TRLaneArrangementType {
  def value: Int
}
object TRLaneArrangementType {
  val values = Set(MassTransitLane)

  def apply(value: Int): TRLaneArrangementType = {
    values.find(_.value == value).getOrElse(Unknown)
  }

  case object MassTransitLane extends TRLaneArrangementType { def value = 5; }
  case object Unknown extends TRLaneArrangementType { def value = 99; }
}

sealed trait TRFrostHeavingFactorType {
  def value: Int
  def pavedRoadType: String
}
object TRFrostHeavingFactorType {
  val values = Set(VeryFrostHeaving, MiddleValue50to60, FrostHeaving, MiddleValue60to80, NoFrostHeaving)

  def apply(value: Int): TRFrostHeavingFactorType = {
    values.find(_.value == value).getOrElse(Unknown)
  }

  case object VeryFrostHeaving extends TRFrostHeavingFactorType { def value = 40; def pavedRoadType = "Very Frost Heaving";}
  case object MiddleValue50to60 extends TRFrostHeavingFactorType { def value = 50; def pavedRoadType = "Middle value 50...60";}
  case object FrostHeaving extends TRFrostHeavingFactorType { def value = 60; def pavedRoadType = "Frost Heaving";}
  case object MiddleValue60to80 extends TRFrostHeavingFactorType { def value = 70; def pavedRoadType = "Middle Value 60...80";}
  case object NoFrostHeaving extends TRFrostHeavingFactorType { def value = 80; def pavedRoadType = "No Frost Heaving";}
  case object Unknown extends TRFrostHeavingFactorType { def value = 999;  def pavedRoadType = "No information";}
}



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

class OthLiviIdBusStopStrategy(typeId : Int, massTransitStopDao: MassTransitStopDao, roadLinkService: RoadLinkService, eventbus: DigiroadEventBus, geometryTransform: GeometryTransform) extends BusStopStrategy(typeId, massTransitStopDao, roadLinkService, eventbus, geometryTransform)
{
  lazy val logger = LoggerFactory.getLogger(getClass)

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
    val trSave = newProperties.
      find(property => property.publicId == AssetPropertyConfiguration.TrSaveId).
      flatMap(property => property.values.headOption).map(p => p.asInstanceOf[PropertyValue].propertyValue)
    val othLiviIdBusStopStrategy =  trSave.isEmpty && TierekisteriBusStopStrategyOperations.isStoredInTierekisteri(properties, roadLink.map(_.administrativeClass))
    if(othLiviIdBusStopStrategy){
      if(existingAssetOption.isDefined){
        logger.info(s"Is former tierekisteri asset with id: ${existingAssetOption.get.id}")
      }else {
        logger.info(s"Is former tierekisteri asset")
      }
      
    }
    othLiviIdBusStopStrategy
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
        //Expires the current asset and creates a new one in OTH and expire with new liviId
        val position = optionalPosition.get
        massTransitStopDao.expireMassTransitStop(username, asset.id)
        super.publishExpiringEvent(PublishInfo(Option(asset)))
        create(NewMassTransitStop(position.lon, position.lat, roadLink.linkId, position.bearing.getOrElse(asset.bearing.get),
          mergedProperties), username, Point(position.lon, position.lat), roadLink)

      }else{
        //Updates the asset in OTH with new liviId
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
