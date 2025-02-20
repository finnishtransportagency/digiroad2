package fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop

import fi.liikennevirasto.digiroad2.asset.{AbstractProperty, SimplePointAssetProperty, _}
import fi.liikennevirasto.digiroad2.linearasset.RoadLinkLike
import fi.liikennevirasto.digiroad2.{EventBusMassTransitStop, FloatingReason}
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

sealed trait BusStopType {
  def value: Int
}
object BusStopType {
  val values = Set(Tram, Virtual, Bus, Terminal, ServicePoint, Unknown)

  def apply(intValue: Int): BusStopType = {
    values.find(_.value == intValue).getOrElse(Unknown)
  }

  case object Tram extends BusStopType { def value = 1 }
  case object Bus extends BusStopType { def value = 2 }
  case object Virtual extends BusStopType { def value = 5 }
  case object Terminal extends BusStopType { def value = 6 }
  case object ServicePoint extends BusStopType { def value = 7 }
  case object Unknown extends BusStopType { def value = 99 }
}


object MassTransitStopOperations {
  val toIso8601 = DateTimeFormat.forPattern("yyyy-MM-dd")

  val StateOwned: Set[AdministrativeClass] = Set(State)
  val OtherOwned: Set[AdministrativeClass] = Set(Municipality, Private)
  val TramPropertyValue: String = "1"
  val BusStopPropertyValue: String = "2"
  val VirtualBusStopPropertyValue: String = "5"
  val ServicePointBusStopPropertyValue: String = "7"
  val MassTransitStopTypePublicId = "pysakin_tyyppi"
  val MassTransitStopAdminClassPublicId = "linkin_hallinnollinen_luokka"

  val CentralELYPropertyValue = "2"
  val HSLPropertyValue = "3"
  val MunicipalityPropertyValue = "1"
  val AdminClassStatePropertyValue = "1"
  val AdministratorInfoPublicId = "tietojen_yllapitaja"
  val LiViIdentifierPublicId = "yllapitajan_koodi"
  val InventoryDateId = "inventointipaiva"
  val RoadName_FI = "osoite_suomeksi"
  val RoadName_SE = "osoite_ruotsiksi"
  val FloatingReasonPublicId = "kellumisen_syy"
  val EndDate = "viimeinen_voimassaolopaiva"

  val nameFiPublicId = "nimi_suomeksi"
  val nameSePublicId = "nimi_ruotsiksi"
  val roofPublicId = "katos"
  val raisePublicId = "korotettu"

  val terminalChildrenPublicId = "liitetyt_pysakit"

  /**
    * Check for administrative class change: road link has differing owner other than Unknown.
    *
    * @param administrativeClass MassTransitStop administrative class
    * @param roadLinkAdminClassOption RoadLink administrative class
    * @return true, if mismatch (owner known and non-compatible)
    */
  def administrativeClassMismatch(administrativeClass: AdministrativeClass,
                                  roadLinkAdminClassOption: Option[AdministrativeClass]) = {
    val rlAdminClass = roadLinkAdminClassOption.getOrElse(Unknown)
    val mismatch = StateOwned.contains(rlAdminClass) && OtherOwned.contains(administrativeClass) ||
      OtherOwned.contains(rlAdminClass) && StateOwned.contains(administrativeClass)
    administrativeClass != Unknown && roadLinkAdminClassOption.nonEmpty && mismatch
  }

  def isFloating(administrativeClass: AdministrativeClass, roadLinkOption: Option[RoadLinkLike]): (Boolean, Option[FloatingReason]) = {

    val roadLinkAdminClass = roadLinkOption.map(_.administrativeClass)
    if (administrativeClassMismatch(administrativeClass, roadLinkAdminClass))
      (true, Some(FloatingReason.RoadOwnerChanged))
    else
      (false, None)
  }

  def isFloating(persistedAsset: PersistedMassTransitStop, roadLinkOption: Option[RoadLinkLike]): (Boolean, Option[FloatingReason]) = {
    val simpleProperty = persistedAsset.propertyData.map{x => SimplePointAssetProperty(x.publicId , x.values)}

    if(persistedAsset.propertyData.exists(_.publicId == "vaikutussuunta") &&
      isValidBusStopDirections(simpleProperty, roadLinkOption))
      (false, None)
    else
      (true, Some(FloatingReason.TrafficDirectionNotMatch))
    }


  def floatingReason(administrativeClass: AdministrativeClass, roadLink: RoadLinkLike): Option[String] = {
    if (administrativeClassMismatch(administrativeClass, Some(roadLink.administrativeClass)))
      Some("Road link administrative class has changed from %d to %d".format(roadLink.administrativeClass.value, administrativeClass.value))
    else
      None
  }

  def isVirtualBusStop(properties: Set[SimplePointAssetProperty]): Boolean = {
    properties.find(pro => pro.publicId == MassTransitStopTypePublicId).exists(_.values.exists(_.asInstanceOf[PropertyValue].propertyValue == VirtualBusStopPropertyValue))
  }

  def getAdministrationClass(properties: Seq[AbstractProperty]): Option[AdministrativeClass] = {
    val propertyValueOption = properties.find(_.publicId == MassTransitStopAdminClassPublicId)
      .map(_.values).getOrElse(Seq()).headOption

    propertyValueOption match {
      case None => None
      case Some(propertyValue) if propertyValue.asInstanceOf[PropertyValue].propertyValue.isEmpty => None
      case Some(propertyValue) if propertyValue.asInstanceOf[PropertyValue].propertyValue.nonEmpty =>
        Some(AdministrativeClass.apply(propertyValue.asInstanceOf[PropertyValue].propertyValue.toInt))
    }
  }

  /**
    * Checks that virtualTransitStop doesn't have any other types selected
    *
    * @param stopProperties Properties of the mass transit stop
    * @return true, if any stop types included with a virtual stop type
    */
  def mixedStoptypes(stopProperties: Set[SimplePointAssetProperty]): Boolean =
  {
    val propertiesSelected = stopProperties.filter(p => MassTransitStopTypePublicId.equalsIgnoreCase(p.publicId))
      .flatMap(_.values).map(_.asInstanceOf[PropertyValue].propertyValue)
    propertiesSelected.contains(VirtualBusStopPropertyValue) && propertiesSelected.exists(!_.equals(VirtualBusStopPropertyValue))
  }

  def getVerifiedProperties(properties: Set[SimplePointAssetProperty], assetProperties: Seq[AbstractProperty]): Set[SimplePointAssetProperty] = {
    val administrationFromProperties = properties.find(_.publicId == AdministratorInfoPublicId)

    administrationFromProperties.flatMap(_.values.headOption.map(_.asInstanceOf[PropertyValue].propertyValue)) match {
      case Some(value) => properties
      case None =>
        properties.filterNot(p => p.publicId == AdministratorInfoPublicId) ++ assetProperties.find(_.publicId == AdministratorInfoPublicId).
          map( p => SimplePointAssetProperty(AdministratorInfoPublicId, p.values))
    }
  }

  def isValidBusStopDirections(properties: Seq[SimplePointAssetProperty], roadLink: Option[RoadLinkLike]) = {
    val roadLinkDirection = roadLink.map(dir => dir.trafficDirection).getOrElse(throw new IllegalStateException("Road link no longer available"))
    val stopTypes = properties.filter(prop => prop.publicId == MassTransitStopTypePublicId).flatMap(_.values).map(_.asInstanceOf[PropertyValue].propertyValue)
    val anyDirectionIsValidBecauseOfTram = stopTypes.contains(TramPropertyValue)

    if (anyDirectionIsValidBecauseOfTram) {
      true
    } else {
      properties.find(prop => prop.publicId == "vaikutussuunta").flatMap(_.values.headOption.map(_.asInstanceOf[PropertyValue].propertyValue)) match {
        case Some(busDir) =>
          !((roadLinkDirection != TrafficDirection.BothDirections) && (roadLinkDirection.toString != SideCode.apply(busDir.toInt).toString))
        case None => false
      }
    }
  }

  def getTerminalMassTransitStopChildren(properties: Seq[SimplePointAssetProperty]) : Seq[Long] = {
    properties.find(_.publicId == terminalChildrenPublicId).map(_.values).getOrElse(Seq()).map(_.asInstanceOf[PropertyValue].propertyValue).foldLeft(Seq.empty[Long]) { (result, child) =>
      result ++ Seq(child.toLong)
    }
  }

  def extractStopType(asset: PersistedMassTransitStop): Option[BusStopType] ={
    extractStopTypes(asset.propertyData).headOption
  }

  def extractStopTypes(properties: Seq[AbstractProperty]): Seq[BusStopType] ={
    properties.find(p=> p.publicId == MassTransitStopOperations.MassTransitStopTypePublicId) match {
      case Some(property) =>
        property.values.map(p => BusStopType.apply(p.asInstanceOf[PropertyValue].propertyValue.toInt))
      case _ =>
        Seq()
    }
  }


  def extractStopName(properties: Seq[Property]): String = {
    properties
      .filter { property => property.publicId.equals("nimi_suomeksi") }
      .filterNot { property => property.values.isEmpty }
      .map(_.values.head)
      .map(_.asInstanceOf[PropertyValue].propertyValue)
      .headOption
      .getOrElse("")
  }

  def setPropertiesDefaultValues(properties: Seq[SimplePointAssetProperty], roadLink: RoadLinkLike): Seq[SimplePointAssetProperty] = {
    val arrayProperties = Seq(MassTransitStopOperations.RoadName_FI, MassTransitStopOperations.RoadName_SE )
    val defaultproperties =  arrayProperties.flatMap{
      key =>
        if(!properties.exists(_.publicId == key))
          Some(SimplePointAssetProperty(publicId = key,  values = Seq.empty[PropertyValue]))
        else
          None
    } ++ properties

    defaultproperties.map { parameter =>
      if ((parameter.values.exists(_.asInstanceOf[PropertyValue].propertyValue == "") && parameter.values.exists(_.asInstanceOf[PropertyValue].propertyDisplayValue.nonEmpty)) || parameter.values.exists(_.asInstanceOf[PropertyValue].propertyValue == "" && parameter.values.exists(_.asInstanceOf[PropertyValue].propertyDisplayValue == None))) {
        parameter.publicId match {
          case MassTransitStopOperations.RoadName_FI => parameter.copy(values = Seq(PropertyValue(roadLink.attributes.getOrElse("ROADNAME_FI", "").toString)))
          case MassTransitStopOperations.RoadName_SE => parameter.copy(values = Seq(PropertyValue(roadLink.attributes.getOrElse("ROADNAME_SE", "").toString)))
          case _ => parameter
        }
      } else
        parameter

    }
  }

  def eventBusMassTransitStop(stop: PersistedMassTransitStop, municipalityName: String) = {
    EventBusMassTransitStop(municipalityNumber = stop.municipalityCode, municipalityName = municipalityName,
      nationalId = stop.nationalId, lon = stop.lon, lat = stop.lat, bearing = stop.bearing,
      validityDirection = stop.validityDirection, created = stop.created, modified = stop.modified,
      propertyData = stop.propertyData)
  }
}