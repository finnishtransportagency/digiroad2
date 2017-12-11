package fi.liikennevirasto.digiroad2.service.pointasset.masstransitstop

import fi.liikennevirasto.digiroad2.asset.{AbstractProperty, SimpleProperty, _}
import fi.liikennevirasto.digiroad2.linearasset.RoadLinkLike
import fi.liikennevirasto.digiroad2.FloatingReason
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

sealed trait BusStopType {
  def value: Int
}
object BusStopType {
  val values = Set(Virtual, Commuter, LongDistance, Terminal, Unknown)

  def apply(intValue: Int): BusStopType = {
    values.find(_.value == intValue).getOrElse(Unknown)
  }

  case object Tram extends BusStopType { def value = 1 }
  case object Commuter extends BusStopType { def value = 2 }
  case object LongDistance extends BusStopType { def value = 3 }
  case object Local extends BusStopType { def value = 4 }
  case object Virtual extends BusStopType { def value = 5 }
  case object Terminal extends BusStopType { def value = 6 }
  case object Unknown extends BusStopType { def value = 99 }
}


object MassTransitStopOperations {
  val toIso8601 = DateTimeFormat.forPattern("yyyy-MM-dd")

  val StateOwned: Set[AdministrativeClass] = Set(State)
  val OtherOwned: Set[AdministrativeClass] = Set(Municipality, Private)
  val CommuterBusStopPropertyValue: String = "2"
  val LongDistanceBusStopPropertyValue: String = "3"
  val VirtualBusStopPropertyValue: String = "5"
  val MassTransitStopTypePublicId = "pysakin_tyyppi"
  val MassTransitStopAdminClassPublicId = "linkin_hallinnollinen_luokka"

  val CentralELYPropertyValue = "2"
  val HSLPropertyValue = "3"
  val AdminClassStatePropertyValue = "1"
  val AdministratorInfoPublicId = "tietojen_yllapitaja"
  val LiViIdentifierPublicId = "yllapitajan_koodi"
  val InventoryDateId = "inventointipaiva"
  val RoadName_FI = "osoite_suomeksi"
  val RoadName_SE = "osoite_ruotsiksi"

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
    val simpleProperty = persistedAsset.propertyData.map{x => SimpleProperty(x.publicId , x.values)}

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

  def isVirtualBusStop(properties: Set[SimpleProperty]): Boolean = {
    properties.find(pro => pro.publicId == MassTransitStopTypePublicId).exists(_.values.exists(_.propertyValue == VirtualBusStopPropertyValue))
  }

  def getAdministrationClass(properties: Seq[AbstractProperty]): Option[AdministrativeClass] = {
    val propertyValueOption = properties.find(_.publicId == MassTransitStopAdminClassPublicId)
      .map(_.values).getOrElse(Seq()).headOption

    propertyValueOption match {
      case None => None
      case Some(propertyValue) if propertyValue.propertyValue.isEmpty => None
      case Some(propertyValue) if propertyValue.propertyValue.nonEmpty =>
        Some(AdministrativeClass.apply(propertyValue.propertyValue.toInt))
    }
  }

  /**
    * Checks that virtualTransitStop doesn't have any other types selected
    *
    * @param stopProperties Properties of the mass transit stop
    * @return true, if any stop types included with a virtual stop type
    */
  def mixedStoptypes(stopProperties: Set[SimpleProperty]): Boolean =
  {
    val propertiesSelected = stopProperties.filter(p => MassTransitStopTypePublicId.equalsIgnoreCase(p.publicId))
      .flatMap(_.values).map(_.propertyValue)
    propertiesSelected.contains(VirtualBusStopPropertyValue) && propertiesSelected.exists(!_.equals(VirtualBusStopPropertyValue))
  }

  def getVerifiedProperties(properties: Set[SimpleProperty], assetProperties: Seq[AbstractProperty]): Set[SimpleProperty] = {
    val administrationFromProperties = properties.find(_.publicId == AdministratorInfoPublicId)

    administrationFromProperties.flatMap(_.values.headOption.map(_.propertyValue)) match {
      case Some(value) => properties
      case None =>
        properties.filterNot(p => p.publicId == AdministratorInfoPublicId) ++ assetProperties.find(_.publicId == AdministratorInfoPublicId).
          map( p => SimpleProperty(AdministratorInfoPublicId, p.values))
    }
  }

  def isValidBusStopDirections(properties: Seq[SimpleProperty], roadLink: Option[RoadLinkLike]) = {
    val roadLinkDirection = roadLink.map(dir => dir.trafficDirection).getOrElse(throw new IllegalStateException("Road link no longer available"))

    properties.find(prop => prop.publicId == "vaikutussuunta").flatMap(_.values.headOption.map(_.propertyValue)) match {
      case Some(busDir) =>
        !((roadLinkDirection != TrafficDirection.BothDirections) && (roadLinkDirection.toString != SideCode.apply(busDir.toInt).toString))
      case None => false
    }
  }

  def getTerminalMassTransitStopChildren(properties: Seq[SimpleProperty]) : Seq[Long] = {
    properties.find(_.publicId == terminalChildrenPublicId).map(_.values).getOrElse(Seq()).map(_.propertyValue).foldLeft(Seq.empty[Long]) { (result, child) =>
      result ++ Seq(child.toLong)
    }
  }

  def extractStopType(asset: PersistedMassTransitStop): Option[BusStopType] ={
    extractStopTypes(asset.propertyData).headOption
  }

  def extractStopTypes(properties: Seq[AbstractProperty]): Seq[BusStopType] ={
    properties.find(p=> p.publicId == MassTransitStopOperations.MassTransitStopTypePublicId) match {
      case Some(property) =>
        property.values.map(p => BusStopType.apply(p.propertyValue.toInt))
      case _ =>
        Seq()
    }
  }


  def extractStopName(properties: Seq[Property]): String = {
    properties
      .filter { property => property.publicId.equals("nimi_suomeksi") }
      .filterNot { property => property.values.isEmpty }
      .map(_.values.head)
      .map(_.propertyValue)
      .headOption
      .getOrElse("")
  }

  def setPropertiesDefaultValues(properties: Seq[SimpleProperty], roadLink: RoadLinkLike): Seq[SimpleProperty] = {
    val arrayProperties = Seq(MassTransitStopOperations.InventoryDateId, MassTransitStopOperations.RoadName_FI, MassTransitStopOperations.RoadName_SE )
    val defaultproperties =  arrayProperties.flatMap{
      key =>
        if(!properties.exists(_.publicId == key))
          Some(SimpleProperty(publicId = key,  values = Seq.empty[PropertyValue]))
        else
          None
    } ++ properties

    defaultproperties.map { parameter =>
      if (parameter.values.isEmpty || parameter.values.exists(_.propertyValue == "")) {
        parameter.publicId match {
          case MassTransitStopOperations.RoadName_FI => parameter.copy(values = Seq(PropertyValue(roadLink.attributes.getOrElse("ROADNAME_FI", "").toString)))
          case MassTransitStopOperations.RoadName_SE => parameter.copy(values = Seq(PropertyValue(roadLink.attributes.getOrElse("ROADNAME_SE", "").toString)))
          case MassTransitStopOperations.InventoryDateId => parameter.copy(values = Seq(PropertyValue(toIso8601.print(DateTime.now()))))
          case _ => parameter
        }
      } else
        parameter

    }
  }
}