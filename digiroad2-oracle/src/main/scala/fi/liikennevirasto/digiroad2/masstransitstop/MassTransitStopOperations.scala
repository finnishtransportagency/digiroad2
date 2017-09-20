package fi.liikennevirasto.digiroad2.masstransitstop

import fi.liikennevirasto.digiroad2.asset.{AbstractProperty, SimpleProperty, _}
import fi.liikennevirasto.digiroad2.linearasset.RoadLinkLike
import fi.liikennevirasto.digiroad2.{FloatingReason, PersistedMassTransitStop}

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

  val nameFiPublicId = "nimi_suomeksi"
  val nameSePublicId = "nimi_ruotsiksi"

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

  def getTerminalMassTransitStopChildren(properties: Seq[SimpleProperty]) : Seq[Long] = {
    properties.find(_.publicId == terminalChildrenPublicId).map(_.values).getOrElse(Seq()).map(_.propertyValue).foldLeft(Seq.empty[Long]) { (result, child) =>
      result ++ Seq(child.toLong)
    }
  }
}