package fi.liikennevirasto.digiroad2.asset

import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.Base64
import fi.liikennevirasto.digiroad2._
import org.joda.time.DateTime
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}
import scala.util.Try


sealed trait LinkGeomSource{
  def value: Int
}

//LINKIN LÄHDE (1 = tielinkkien rajapinta, 2 = täydentävien linkkien rajapinta, 3 = suunnitelmalinkkien rajapinta, 4 = jäädytettyjen linkkien rajapinta, 5 = historialinkkien rajapinta)

object LinkGeomSource{
  val values = Set(NormalLinkInterface, ComplimentaryLinkInterface , SuravageLinkInterface, FrozenLinkInterface, HistoryLinkInterface)

  def apply(intValue: Int): LinkGeomSource = values.find(_.value == intValue).getOrElse(Unknown)

  case object NormalLinkInterface extends LinkGeomSource {def value = 1;}
  case object ComplimentaryLinkInterface extends LinkGeomSource {def value = 2;}
  case object SuravageLinkInterface extends LinkGeomSource {def value = 3;}
  case object FrozenLinkInterface extends LinkGeomSource {def value = 4;}
  case object HistoryLinkInterface extends LinkGeomSource {def value = 5;}
  case object Unknown extends LinkGeomSource { def value = 99 }
}

sealed trait ConstructionType {
  def value: Int
}

object ConstructionType{
  val values = Set[ConstructionType](InUse, UnderConstruction, Planned, TemporarilyOutOfUse, UnknownConstructionType)

  def apply(intValue: Int): ConstructionType = {
    values.find(_.value == intValue).getOrElse(InUse)
  }
  
  case object Planned extends ConstructionType { def value = 1 }
  case object UnderConstruction extends ConstructionType { def value = 2 }
  case object InUse extends ConstructionType { def value = 3 }
  case object TemporarilyOutOfUse extends ConstructionType { def value = 4 }
  case object UnknownConstructionType extends ConstructionType { def value = 99 }
}

sealed trait LinkType
{
  def value: Int
}
object LinkType {
  val values = Set(Motorway, MultipleCarriageway, SingleCarriageway, Freeway, Roundabout, SlipRoad,
                   RestArea, CycleOrPedestrianPath, PedestrianZone, ServiceOrEmergencyRoad, EnclosedTrafficArea,
                   TractorRoad, ServiceAccess, CableFerry, SpecialTransportWithoutGate, SpecialTransportWithGate,
                   HardShoulder, BidirectionalLaneCarriageWay, UnknownLinkType)

  def apply(value: Int): LinkType = {
    values.find(_.value == value).getOrElse(UnknownLinkType)
  }
}
case object Motorway extends LinkType { def value = 1 }
case object MultipleCarriageway extends LinkType { def value = 2 }
case object SingleCarriageway extends LinkType { def value = 3 }
case object Freeway extends LinkType { def value = 4 }
case object Roundabout extends LinkType { def value = 5 }
case object SlipRoad extends LinkType { def value = 6 }
case object RestArea extends LinkType { def value = 7 }
case object CycleOrPedestrianPath extends LinkType { def value = 8 }
case object PedestrianZone extends LinkType { def value = 9 }
case object ServiceOrEmergencyRoad extends LinkType { def value = 10 }
case object EnclosedTrafficArea extends LinkType { def value = 11 }
case object TractorRoad extends LinkType { def value = 12 }
case object ServiceAccess extends LinkType { def value = 13 }
case object SpecialTransportWithoutGate extends LinkType { def value = 14 }
case object SpecialTransportWithGate extends LinkType { def value = 15 }
case object HardShoulder extends LinkType { def value = 16 }
case object CableFerry extends LinkType { def value = 21 }
case object BidirectionalLaneCarriageWay extends  LinkType { def value = 22}
case object UnknownLinkType extends LinkType { def value = 99 }

sealed trait FunctionalClass
{
  def value: Int
}
object FunctionalClass {
  val values = Set(FunctionalClass1, FunctionalClass2, FunctionalClass3, FunctionalClass4, FunctionalClass5,
                   AnotherPrivateRoad, PrimitiveRoad, WalkingAndCyclingPath, FunctionalClass9, UnknownFunctionalClass)

  def apply(value: Int): FunctionalClass = {
    values.find(_.value == value).getOrElse(UnknownFunctionalClass)
  }
}
case object FunctionalClass1 extends FunctionalClass { def value = 1 }
case object FunctionalClass2 extends FunctionalClass { def value = 2 }
case object FunctionalClass3 extends FunctionalClass { def value = 3 }
case object FunctionalClass4 extends FunctionalClass { def value = 4 }
case object FunctionalClass5 extends FunctionalClass { def value = 5 }
case object AnotherPrivateRoad extends FunctionalClass { def value = 6 }
case object PrimitiveRoad extends FunctionalClass { def value = 7 }
case object WalkingAndCyclingPath extends FunctionalClass { def value = 8 }
case object FunctionalClass9 extends FunctionalClass { def value = 9 }
case object UnknownFunctionalClass extends FunctionalClass { def value = 99 }

sealed trait AdministrativeClass {
  def value: Int
}
object AdministrativeClass {
  val values = Set(State, Municipality, Private, Unknown)

  def apply(value: Int): AdministrativeClass = {
    values.find(_.value == value).getOrElse(Unknown)
  }

  def apply(stringValue: String): AdministrativeClass = {
    values.find(_.toString == stringValue).getOrElse(Unknown)
  }
}
case object State extends AdministrativeClass { def value = 1 }
case object Municipality extends AdministrativeClass { def value = 2 }
case object Private extends AdministrativeClass { def value = 3 }
case object Unknown extends AdministrativeClass { def value = 99 }



sealed trait InformationSource {
  def value: Int
}

object InformationSource{
  val values = Set(RoadRegistry, MunicipalityMaintenainer, MmlNls, UnknownSource)

  def apply(value: Int): InformationSource = {
    values.find(_.value == value).getOrElse(UnknownSource)
  }
}

//1 = FTA/ Road registry (Liikennevirasto)
case object RoadRegistry extends InformationSource { def value = 1 }
//2 = Maintainer (municipality maintainer)
case object MunicipalityMaintenainer extends InformationSource { def value = 2 }
//3 = MML/NLS (Maanmittauslaitos)
case object MmlNls extends InformationSource { def value = 3 }

case object UnknownSource extends InformationSource { def value = 99 }

sealed trait TrafficDirection {
  def value: Int
  def isOneWay =
    this == TrafficDirection.AgainstDigitizing ||
    this == TrafficDirection.TowardsDigitizing
}
object TrafficDirection {
  val values = Set(BothDirections, AgainstDigitizing, TowardsDigitizing, UnknownDirection)

  def apply(intValue: Int): TrafficDirection = {
    values.find(_.value == intValue).getOrElse(UnknownDirection)
  }

  def apply(optionalValue: Option[Int]): TrafficDirection = {
    optionalValue.map { value => values.find(_.value == value).getOrElse(UnknownDirection) }.getOrElse(UnknownDirection)
  }

  def apply(stringValue: String): TrafficDirection = {
    values.find(_.toString == stringValue).getOrElse(UnknownDirection)
  }

  def toSideCode(trafficDirection: TrafficDirection): SideCode = {
    trafficDirection match {
      case TowardsDigitizing => SideCode.TowardsDigitizing
      case AgainstDigitizing => SideCode.AgainstDigitizing
      case BothDirections => SideCode.BothDirections
      case UnknownDirection => SideCode.Unknown
    }
  }

  case object BothDirections extends TrafficDirection { def value = 2 }
  case object AgainstDigitizing extends TrafficDirection { def value = 3 }
  case object TowardsDigitizing extends TrafficDirection { def value = 4 }
  case object UnknownDirection extends TrafficDirection { def value = 99 }


}

sealed trait PointAssetState {
  def value: Int
  def description: String
}
object PointAssetState {
  val values = Set(Unknown, Planned, UnderConstruction, PermanentlyInUse, TemporarilyInUse, TemporarilyOutOfService, OutgoingPermanentDevice )

  def apply(intValue: Int):PointAssetState = {
    values.find(_.value == intValue).getOrElse(getDefault)
  }

  def getDefault: PointAssetState = PermanentlyInUse

  case object Planned extends PointAssetState { def value = 1; def description = "Suunnitteilla"  }
  case object UnderConstruction extends PointAssetState { def value = 2; def description = "Rakenteilla" }
  case object PermanentlyInUse extends PointAssetState { def value = 3; def description = "Käytössä pysyvästi" }
  case object TemporarilyInUse extends PointAssetState { def value = 4; def description = "Käytössä tilapäisesti" }
  case object TemporarilyOutOfService extends PointAssetState { def value = 5; def description = "Pois käytössä tilapäisesti" }
  case object OutgoingPermanentDevice extends PointAssetState { def value = 6; def description = "Poistuva pysyvä laite" }
  case object Unknown extends PointAssetState { def value = 99; def description = "Ei tiedossa" }
}

sealed trait PointAssetStructure {
  def value: Int
  def description: String
}
object PointAssetStructure {
  val values = Set(Unknown, Pole, Wall, Bridge, Portal, HalfPortal, Barrier, Other )

  def apply(intValue: Int):PointAssetStructure = {
    values.find(_.value == intValue).getOrElse(getDefault)
  }

  def getDefault: PointAssetStructure = Pole

  case object Pole extends PointAssetStructure { def value = 1; def description = "Pylväs"  }
  case object Wall extends PointAssetStructure { def value = 2; def description = "Seinä" }
  case object Bridge extends PointAssetStructure { def value = 3; def description = "Silta" }
  case object Portal extends PointAssetStructure { def value = 4; def description = "Portaali" }
  case object HalfPortal extends PointAssetStructure { def value = 5; def description = "Puoliportaali" }
  case object Barrier extends PointAssetStructure { def value = 6; def description = "Puomi tai muu esterakennelma" }
  case object Other extends PointAssetStructure { def value = 7; def description = "Muu" }
  case object Unknown extends PointAssetStructure { def value = 99; def description = "Ei tiedossa" }
}

case class SideCodeException(msg:String) extends Exception(msg)
sealed trait SideCode {
  def value: Int
}
object SideCode {
  val values = Set(BothDirections, TowardsDigitizing, AgainstDigitizing, Unknown, DoesNotAffectRoadLink)

  def apply(intValue: Int): SideCode = {
    values.find(_.value == intValue).getOrElse(Unknown)
  }

  def switch(sideCode: SideCode): SideCode = {
    sideCode match {
      case TowardsDigitizing => AgainstDigitizing
      case AgainstDigitizing => TowardsDigitizing
      case _ => sideCode
    }
  }

  def toTrafficDirection(sideCode: SideCode): TrafficDirection = {
    sideCode match {
      case TowardsDigitizing => TrafficDirection.TowardsDigitizing
      case AgainstDigitizing => TrafficDirection.AgainstDigitizing
      case BothDirections => TrafficDirection.BothDirections
      case Unknown => TrafficDirection.UnknownDirection
      case DoesNotAffectRoadLink => throw SideCodeException("Cannot convert SideCode 0 into TrafficDirection")
    }
  }
  def toTrafficDirectionForTrafficSign(sideCode: SideCode): Int = {
    sideCode match {
      case TowardsDigitizing => TrafficDirection.TowardsDigitizing.value
      case AgainstDigitizing => TrafficDirection.AgainstDigitizing.value
      case BothDirections => TrafficDirection.BothDirections.value
      case Unknown => TrafficDirection.UnknownDirection.value
      case DoesNotAffectRoadLink => DoesNotAffectRoadLink.value
    }
  }
  
  case object DoesNotAffectRoadLink extends SideCode { def value = 0 }
  case object BothDirections extends SideCode { def value = 1 }
  case object TowardsDigitizing extends SideCode { def value = 2 }
  case object AgainstDigitizing extends SideCode { def value = 3 }
  case object Unknown extends SideCode { def value = 99 }
}

/**
  * Values for PavementClass types enumeration
  */
sealed trait PavementClass {
  def value: Int
  def typeDescription: String
}
object PavementClass {
  val values = Set(CementConcrete, Cobblestone, HardAsphalt, SoftAsphalt, GravelSurface, GravelWearLayer, OtherCoatings, Unknown)

  def apply(value: Int): PavementClass = {
    values.find(_.value == value).getOrElse(Unknown)
  }

  case object CementConcrete extends PavementClass { def value = 1; def typeDescription = "Cement Concrete";}
  case object Cobblestone extends PavementClass { def value = 2; def typeDescription = "Cobblestone";}
  case object HardAsphalt extends PavementClass { def value = 10; def typeDescription = "Hard Asphalt";}
  case object SoftAsphalt extends PavementClass { def value = 20; def typeDescription = "Soft Asphalt";}
  case object GravelSurface extends PavementClass { def value = 30; def typeDescription = "Gravel Surface";}
  case object GravelWearLayer extends PavementClass { def value = 40; def typeDescription = "Gravel Wear Layer";}
  case object OtherCoatings extends PavementClass { def value = 50; def typeDescription = "Other Coatings";}
  case object Unknown extends PavementClass { def value = 99;  def typeDescription = "Unknown";}
}


sealed trait ServicePointsClass {
  def value: Int
  def isAuthorityData: Boolean
  val labelName: String
  val subTypeName: Map[String, Int] = Map.empty
}
object ServicePointsClass {
  val values = Set(Customs, BorderCrossing, RestArea, Airport, FerryTerminal, RailwayStation, ParkingArea, TerminalForLoadingCars,
                  ParkingAreaBusesAndTrucks, ParkingGarage, BusStation, TaxiStation, Culvert, Unknown)

  def apply(value: Int): Boolean = {
    values.find(_.value == value).getOrElse(Unknown).isAuthorityData
  }

  def apply(value: String): Int = {
    values.find { servicePoint =>
      stringNormalizerToCsvDataImport(servicePoint.labelName) == value
    }.getOrElse(Unknown).value
  }

  def stringNormalizerToCsvDataImport(value: String): String = {
    Normalizer.normalize(value, Normalizer.Form.NFD)
      .replaceAll("[^\\p{ASCII}]", "")
      .replaceAll("-|\\s", "").toLowerCase
  }

  def getTypeExtensionValue(typeExtension: String, serviceType: Int): Option[Int] = {
    val serviceTypeClass = values.find(_.value == serviceType)

    val normalizedValue = stringNormalizerToCsvDataImport(typeExtension)

    val normalizedSubTypes = serviceTypeClass.get.subTypeName.map { subType =>
      (stringNormalizerToCsvDataImport(subType._1), subType._2)
    }

    normalizedSubTypes.get(normalizedValue)
  }

  case object Customs extends ServicePointsClass { def value = 4;  def isAuthorityData = true; val labelName = "Tulli";}
  case object BorderCrossing extends ServicePointsClass { def value = 5; def isAuthorityData = true; val labelName = "Rajanylityspaikka";}
  case object RestArea extends ServicePointsClass { def value = 6;  def isAuthorityData = true; val labelName = "Lepoalue"; override val subTypeName = Map("Kattava varustelu" -> 1, "Perusvarustelu" -> 2, "Yksityinen palvelualue" -> 3, "Ei tietoa" -> 4)}
  case object Airport extends ServicePointsClass { def value = 8;  def isAuthorityData = true; val labelName = "Lentokenttä";}
  case object FerryTerminal extends ServicePointsClass { def value = 9;  def isAuthorityData = true; val labelName = "Laivaterminaali";}
  case object RailwayStation extends ServicePointsClass { def value = 11;  def isAuthorityData = true; val labelName = "Rautatieasema"; override val subTypeName = Map("Merkittävä rautatieasema" -> 5,"Vähäisempi rautatieasema" -> 6, "Maanalainen/metroasema" -> 7)}
  case object ParkingArea extends ServicePointsClass { def value = 12;  def isAuthorityData = true; val labelName = "Pysäköintialue"; override val subTypeName = Map("Kattava varustelu" -> 1, "Perusvarustelu" -> 2, "Yksityinen palvelualue" -> 3, "Ei tietoa" -> 4)}
  case object TerminalForLoadingCars extends ServicePointsClass { def value = 13;   def isAuthorityData = true; val labelName = "Autojen lastausterminaali";}
  case object ParkingAreaBusesAndTrucks extends ServicePointsClass { def value = 14;   def isAuthorityData = true; val labelName = "Linja- ja kuorma-autojen pysäköintialue"; override val subTypeName = Map("Kattava varustelu" -> 1, "Perusvarustelu" -> 2, "Yksityinen palvelualue" -> 3, "Ei tietoa" -> 4)}
  case object ParkingGarage extends ServicePointsClass { def value = 15;   def isAuthorityData = true; val labelName = "Pysäköintitalo";}
  case object BusStation extends ServicePointsClass { def value = 16;  def isAuthorityData = true; val labelName = "Linja-autoasema";}
  case object TaxiStation extends ServicePointsClass { def value = 10;  def isAuthorityData = false; val labelName = "Taksiasema";}
  case object Culvert extends ServicePointsClass { def value = 19; def isAuthorityData = false; val labelName = "Tierumpu";}
  case object Unknown extends ServicePointsClass { def value = 99;  def isAuthorityData = true; val labelName = "Unknown";}
}


/**
  * Values for AnimalWarningTypes types enumeration
  */
sealed trait AnimalWarningsType {
  def value: Int
  def typeDescription: String
}
object AnimalWarningsType {
  val values = Set(MooseWarningArea, MooseFence, DeerWarningArea, Unknown)

  def apply(value: Int): AnimalWarningsType = {
    values.find(_.value == value).getOrElse(Unknown)
  }

  case object MooseWarningArea extends AnimalWarningsType { def value = 1; def typeDescription = "Moose Warning Area";}
  case object MooseFence extends AnimalWarningsType { def value = 2; def typeDescription = "Moose Fence";}
  case object DeerWarningArea extends AnimalWarningsType { def value = 3; def typeDescription = "Deer Warning Area";}
  case object Unknown extends AnimalWarningsType { def value = 99;  def typeDescription = "Unknown";}
}

sealed trait TimePeriodClass {
  def value: Int

  val trafficSign: TrafficSignType
}

object TimePeriodClass {
  val values = Set(ValidMultiplePeriodTime, ValidSatTime, ValidMonFriTime)

  def apply(value: Int): TimePeriodClass = {
    values.find(_.value == value).getOrElse(Unknown)
  }

  def fromTrafficSign(trafficSign: TrafficSignType): Set[TimePeriodClass] = {
    values.find(_.trafficSign == trafficSign).toSet
  }

  case object ValidMultiplePeriodTime extends TimePeriodClass {
    def value: Int = 1

    override val trafficSign: TrafficSignType = ValidMultiplePeriod
  }

  case object ValidSatTime extends TimePeriodClass {
    def value: Int = 7

    override val trafficSign: TrafficSignType = ValidSat
  }

  case object ValidMonFriTime extends TimePeriodClass {
    def value: Int = 2

    override val trafficSign: TrafficSignType = ValidMonFri
  }

  case object Unknown extends TimePeriodClass {
    def value: Int = 99

    override val trafficSign: TrafficSignType = TrafficSignType.Unknown
  }
}
sealed trait ParkingProhibitionClass {
  def value: Int
  val trafficSign: TrafficSignType
}
object ParkingProhibitionClass {
  val values = Set(StandingAndParkingProhibition, ParkingProhibition, Unknown)

  def fromTrafficSign(trafficSign: TrafficSignType): Set[ParkingProhibitionClass] = {
    values.find(_.trafficSign == trafficSign).toSet
  }
  def apply(value: Int): ParkingProhibitionClass =
    values.find(_.value == value).getOrElse(Unknown)

  def toTrafficSign(prohibitionValue: Int): TrafficSignType =
    ParkingProhibitionClass.apply(prohibitionValue).trafficSign

  case object StandingAndParkingProhibition extends ParkingProhibitionClass {
    def value: Int = 1
    override val trafficSign: TrafficSignType = StandingAndParkingProhibited
  }

  case object ParkingProhibition extends ParkingProhibitionClass {
    def value: Int = 2
    override val trafficSign: TrafficSignType = ParkingProhibited
  }

  case object Unknown extends ParkingProhibitionClass {
    override def value: Int = 99
    override val trafficSign: TrafficSignType = TrafficSignType.Unknown
  }
}

sealed trait HazmatTransportProhibitionClass {
  def value: Int
  val trafficSign: TrafficSignType
}
object HazmatTransportProhibitionClass {
  val values = Set(HazmatProhibitionTypeA, HazmatProhibitionTypeB, Unknown)

  def fromTrafficSign(trafficSign: TrafficSignType): Set[HazmatTransportProhibitionClass] = {
    values.find(_.trafficSign == trafficSign).toSet
  }
  def apply(value: Int): HazmatTransportProhibitionClass =
    values.find(_.value == value).getOrElse(Unknown)

  def toTrafficSign(prohibitionValue: Int): TrafficSignType =
    HazmatTransportProhibitionClass.apply(prohibitionValue).trafficSign

  case object HazmatProhibitionTypeA extends HazmatTransportProhibitionClass {
    def value: Int = 24
    override val trafficSign: TrafficSignType = HazmatProhibitionA
  }

  case object HazmatProhibitionTypeB extends HazmatTransportProhibitionClass {
    def value: Int = 25
    override val trafficSign: TrafficSignType = HazmatProhibitionB
  }

  case object Unknown extends HazmatTransportProhibitionClass {
    override def value: Int = 99
    override val trafficSign: TrafficSignType = TrafficSignType.Unknown
  }
}


sealed trait RoadWorksClass {
  def value: Int
  val trafficSign: TrafficSignType
}
object RoadWorksClass {
  val values = Set(RoadWorksType, Unknown)

  def fromTrafficSign(trafficSign: TrafficSignType): Set[RoadWorksClass] = {
    values.find(_.trafficSign == trafficSign).toSet
  }
  def apply(value: Int): RoadWorksClass =
    values.find(_.value == value).getOrElse(Unknown)

  def toTrafficSign(roadWorkValue: Int): TrafficSignType =
    RoadWorksClass.apply(roadWorkValue).trafficSign

  case object RoadWorksType extends RoadWorksClass {
    def value: Int = 24
    override val trafficSign: TrafficSignType = RoadWorks
  }

  case object Unknown extends RoadWorksClass {
    override def value: Int = 99
    override val trafficSign: TrafficSignType = TrafficSignType.Unknown
  }
}

sealed trait ProhibitionClass {
  def value: Int
  def typeDescription: String
  def rosatteType: String
  val trafficSign: Seq[TrafficSignType] = Seq()
}
object ProhibitionClass {
  val values = Set(Vehicle, MotorVehicle, PassageThrough, Pedestrian, Bicycle, HorseRiding, Moped, Motorcycle, SnowMobile, Bus,
                   Taxi, PassengerCar, DeliveryCar, Truck, RecreationalVehicle, MilitaryVehicle, ArticulatedVehicle, TractorFarmVehicle,
                   OversizedTransport, DrivingInServicePurpose, DrivingToALot, Unknown)

  def apply(value: Int): ProhibitionClass = {
    values.find(_.value == value).getOrElse(Unknown)
  }

  def fromTrafficSign(trafficSign: TrafficSignType): Set[ProhibitionClass] = {
    values.filter(_.trafficSign.contains(trafficSign)).toSet
  }

  case object Vehicle extends ProhibitionClass {
    def value = 2
    def typeDescription = "Vehicle"
    def rosatteType = "AllVehicle"
    override val trafficSign: Seq[TrafficSignType] = Seq(NoPowerDrivenVehicles)
  }
  case object MotorVehicle extends ProhibitionClass {
    def value = 3
    def typeDescription = "MotorVehicle"
    def rosatteType = "AllVehicle"
    override val trafficSign: Seq[TrafficSignType] = Seq(ClosedToAllVehicles)
  }
  case object PassageThrough extends ProhibitionClass {
    def value = 23
    def typeDescription = "PassageThrough"
    def rosatteType = ""
  }
  case object Pedestrian extends ProhibitionClass {
    def value = 12
    def typeDescription = "Pedestrian"
    def rosatteType = "Pedestrian"
    override val trafficSign: Seq[TrafficSignType] = Seq(NoPedestrians, NoPedestriansCyclesMopeds)
  }
  case object Bicycle extends ProhibitionClass {
    def value = 11
    def typeDescription = "Bicycle"
    def rosatteType = "Bicycle"
    override val trafficSign: Seq[TrafficSignType] = Seq(NoCyclesOrMopeds, NoPedestriansCyclesMopeds)
  }
  case object HorseRiding extends ProhibitionClass {
    def value = 26
    def typeDescription = "HorseRiding"
    def rosatteType = ""
    override val trafficSign: Seq[TrafficSignType] = Seq(NoRidersOnHorseback)
  }
  case object Moped extends ProhibitionClass {
    def value = 10
    def typeDescription = "Moped"
    def rosatteType = "Moped"
    override val trafficSign: Seq[TrafficSignType] = Seq(NoMopeds, NoCyclesOrMopeds, NoPedestriansCyclesMopeds)
  }
  case object Motorcycle extends ProhibitionClass {
    def value = 9
    def typeDescription = "Motorcycle"
    def rosatteType = "Motorcycle"
    override val trafficSign: Seq[TrafficSignType] = Seq(NoMotorCycles)
  }
  case object SnowMobile extends ProhibitionClass {
    def value = 27
    def typeDescription = "SnowMobile"
    def rosatteType = ""
    override val trafficSign: Seq[TrafficSignType] = Seq(NoMotorSledges)
  }
  case object Bus extends ProhibitionClass {
    def value = 5
    def typeDescription = "Bus"
    def rosatteType = "PublicBus + PrivateBus"
    override val trafficSign: Seq[TrafficSignType] = Seq(NoBuses)
  }
  case object Taxi extends ProhibitionClass {
    def value = 8
    def typeDescription = "Taxi"
    def rosatteType = "Taxi"
  }
  case object PassengerCar extends ProhibitionClass {
    def value = 7
    def typeDescription = "PassengerCar"
    def rosatteType = "PassangerCar"
  }
  case object DeliveryCar extends ProhibitionClass {
    def value = 6
    def typeDescription = "DeliveryCar"
    def rosatteType = "DeliveryTruck"
    override val trafficSign: Seq[TrafficSignType] = Seq(NoLorriesAndVans)
  }
  case object Truck extends ProhibitionClass {
    def value = 4
    def typeDescription = "Truck"
    def rosatteType = "TransportTruck"
    override val trafficSign: Seq[TrafficSignType] = Seq(NoLorriesAndVans)
  }
  case object RecreationalVehicle extends ProhibitionClass {
    def value = 15
    def typeDescription = "RecreationalVehicle"
    def rosatteType = ""
  }
  case object MilitaryVehicle extends ProhibitionClass {
    def value = 19
    def typeDescription = "MilitaryVehicle"
    def rosatteType = "MilitaryVehicle"
  }
  case object ArticulatedVehicle extends ProhibitionClass {
    def value = 13
    def typeDescription = "ArticulatedVehicle"
    def rosatteType = "CarWithTrailer"
    override val trafficSign: Seq[TrafficSignType] = Seq(NoVehicleCombinations)
  }
  case object TractorFarmVehicle extends ProhibitionClass {
    def value = 14
    def typeDescription = "TractorFarmVehicle"
    def rosatteType = "FarmVehicle"
    override val trafficSign: Seq[TrafficSignType] = Seq(NoAgriculturalVehicles)
  }
  case object OversizedTransport extends ProhibitionClass {
    def value = 28
    def typeDescription = "OversizedTransport"
    def rosatteType = ""
  }
  case object DrivingInServicePurpose extends ProhibitionClass {
    def value = 21
    def typeDescription = "DrivingInServicePurpose"
    def rosatteType = "DeliveryTruck + EmergencyVehicle + FacilityVehicle + MailVehicle"
  }
  case object DrivingToALot extends ProhibitionClass {
    def value = 22
    def typeDescription = "DrivingToALot"
    def rosatteType = "ResidentialVehicle"
  }
  case object Unknown extends ProhibitionClass {
    def value = 99
    def typeDescription = "Unknown"
    def rosatteType = ""
  }
}

sealed trait ProhibitionExceptionClass {
  def value: Int
  val trafficSign: Seq[TrafficSignType] = Seq()
}

object ProhibitionExceptionClass {
  val values = Set(MopedException, ServiceVehicles, Motorbike, BusException, Sedan, VanException, LorryException, Caravan, Unknown)

  def apply(value: Int): ProhibitionExceptionClass = {
    values.find(_.value == value).getOrElse(Unknown)
  }

  def fromTrafficSign(trafficSign: Seq[TrafficSignType]): Set[Int] = {
    values.filter(_.trafficSign.exists( exception => trafficSign.contains(exception))).map(_.value).filterNot(_ == Unknown.value)
  }

  case object MopedException extends ProhibitionExceptionClass {
    def value = 10
    override val trafficSign: Seq[TrafficSignType] = Seq(Moped)
  }

  case object ServiceVehicles extends ProhibitionExceptionClass {
    def value = 21
    override val trafficSign: Seq[TrafficSignType] = Seq(DrivingInServicePurposesAllowed)
  }

  case object Motorbike extends ProhibitionExceptionClass {
    def value = 9
    override val trafficSign: Seq[TrafficSignType] = Seq(MotorCycle)
  }

  case object BusException extends ProhibitionExceptionClass {
    def value = 5
    override val trafficSign: Seq[TrafficSignType] = Seq(Bus)
  }

  case object Sedan extends ProhibitionExceptionClass {
    def value = 7
    override val trafficSign: Seq[TrafficSignType] = Seq(PassengerCar)
  }

  case object VanException extends ProhibitionExceptionClass {
    def value = 6
    override val trafficSign: Seq[TrafficSignType] = Seq(Van)
  }

  case object LorryException extends ProhibitionExceptionClass {
    def value = 4
    override val trafficSign: Seq[TrafficSignType] = Seq(Lorry)
  }

  case object Caravan extends ProhibitionExceptionClass {
    def value = 15
    override val trafficSign: Seq[TrafficSignType] = Seq(HusvagnCaravan)
  }

  case object Unknown extends ProhibitionExceptionClass {
    override def value: Int = 99
    override val trafficSign: Seq[TrafficSignType] = Seq(TrafficSignType.Unknown)
  }
}

trait NationalStop { val nationalId: Long }
trait RoadLinkStop {
  val linkId: Option[String]
  val mValue: Option[Double]
}
trait TimeStamps {
  val created: Modification
  val modified: Modification
}
trait FloatingAsset {
  val id: Long
  val floating: Boolean
}
case class AssetType(id: Long, assetTypeName: String, geometryType: String)

object DateParser {
  val DateTimePropertyFormat = DateTimeFormat.forPattern("dd.MM.yyyy HH:mm:ss")
  val DatePropertyFormat = DateTimeFormat.forPattern("dd.MM.yyyy")
  val DateTimePropertyFormatMs = DateTimeFormat.forPattern("dd.MM.yyyy HH:mm:ss,SSS")
  val DateTimeSimplifiedFormat = DateTimeFormat.forPattern("yyyyMMddHHmm")
  val DateTimePropertyFormatMsTimeZone = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss.SSSZZ")
  val DateTimePropertyFormatMsTimeZoneWithT = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZZ")

  def dateToString(date: DateTime, dateFormatter: DateTimeFormatter): String = {
    date.toString(dateFormatter)
  }

  def stringToDate(date: String, formatter: DateTimeFormatter): DateTime = {
    formatter.parseDateTime(date)
  }

}

case class Modification(modificationTime: Option[DateTime], modifier: Option[String])
case class SimplePointAssetProperty(publicId: String, values: Seq[PointAssetValue], groupedId: Long = 0) extends AbstractProperty
case class DynamicProperty(publicId: String, propertyType: String, required: Boolean = false, values: Seq[DynamicPropertyValue])

abstract class AbstractProperty {
  def publicId: String
  def values: Seq[PointAssetValue]
}

sealed trait PointAssetValue {
  def toJson: Any
}

case class Property(id: Long, publicId: String, propertyType: String, required: Boolean = false, values: Seq[PointAssetValue], numCharacterMax: Option[Int] = None, groupedId: Long = 0) extends AbstractProperty

case class AdditionalPanel(panelType: Int, panelInfo: String, panelValue: String, formPosition: Int, text: String, size: Int, coating_type: Int, additional_panel_color: Int) extends PointAssetValue {
  override def toJson: Any = this
  def verifyCorrectInputOnAdditionalPanel: Unit = {
    if(AdditionalPanelColor.apply(additional_panel_color).isEmpty) throw new NoSuchElementException(s"Incorrect input for additional panel color: ${additional_panel_color}")
    if(AdditionalPanelSize.apply(size).isEmpty) throw new NoSuchElementException(s"Incorrect input for additional panel size: ${size}")
    if(AdditionalPanelCoatingType.apply(coating_type).isEmpty) throw new NoSuchElementException(s"Incorrect input for additional panel coating type: ${coating_type}")
  }
}
sealed trait AdditionalPanelSize {
  def value: Int
  def propertyDisplayValue : String
}
object AdditionalPanelSize {
  val values = Set(SizeOption1, SizeOption2, SizeOption3, SizeOption99)

  def apply(value: Int): Option[AdditionalPanelSize] = {
    values.find(_.value == value)
  }

  def getDefault: AdditionalPanelSize = SizeOption99
}
case object SizeOption1 extends AdditionalPanelSize { def value = 1; def propertyDisplayValue = "Pienikokoinen merkki"}
case object SizeOption2 extends AdditionalPanelSize { def value = 2; def propertyDisplayValue = "Normaalikokoinen merkki"}
case object SizeOption3 extends AdditionalPanelSize { def value = 3; def propertyDisplayValue = "Suurikokoinen merkki"}
case object SizeOption99 extends AdditionalPanelSize { def value = 99; def propertyDisplayValue = "Ei tietoa"}

sealed trait AdditionalPanelCoatingType {
  def value: Int
  def propertyDisplayValue : String
}
object AdditionalPanelCoatingType {
  val values = Set(CoatingTypeOption1, CoatingTypeOption2, CoatingTypeOption3, CoatingTypeOption99)

  def apply(value: Int): Option[AdditionalPanelCoatingType] = {
    values.find(_.value == value)
  }

  def getDefault: AdditionalPanelCoatingType = CoatingTypeOption99
}
case object CoatingTypeOption1 extends AdditionalPanelCoatingType { def value = 1; def propertyDisplayValue = "R1-luokan kalvo"}
case object CoatingTypeOption2 extends AdditionalPanelCoatingType { def value = 2; def propertyDisplayValue = "R2-luokan kalvo"}
case object CoatingTypeOption3 extends AdditionalPanelCoatingType { def value = 3; def propertyDisplayValue = "R3-luokan kalvo"}
case object CoatingTypeOption99 extends AdditionalPanelCoatingType { def value = 99; def propertyDisplayValue = "Ei tietoa"}

sealed trait AdditionalPanelColor {
  def value: Int
  def propertyDisplayValue : String
}
object AdditionalPanelColor {
  val values = Set(ColorOption1, ColorOption2, ColorOption99)

  def apply(value: Int): Option[AdditionalPanelColor] = {
    values.find(_.value == value)
  }

  def getDefault: AdditionalPanelColor = ColorOption99
}
case object ColorOption1 extends AdditionalPanelColor { def value = 1; def propertyDisplayValue = "Sininen"}
case object ColorOption2 extends AdditionalPanelColor { def value = 2; def propertyDisplayValue = "Keltainen"}
case object ColorOption99 extends AdditionalPanelColor { def value = 99; def propertyDisplayValue = "Ei tietoa"}

case class PropertyValue(propertyValue: String, propertyDisplayValue: Option[String] = None, checked: Boolean = false) extends PointAssetValue {
  override def toJson: Any = this
}

case class DynamicPropertyValue(value: Any)
case class ValidityPeriodValue(days: Int, startHour: Int, endHour: Int, startMinute: Int, endMinute: Int, periodType: Option[Int] = None)
case class EnumeratedPropertyValue(propertyId: Long, publicId: String, propertyName: String, propertyType: String, required: Boolean = false, values: Seq[PointAssetValue]) extends AbstractProperty
case class Position(lon: Double, lat: Double, linkId: String, bearing: Option[Int])
case class DatePeriodValue(startDate: String, endDate: String)
object DatePeriodValue {
  val formatter = DateTimeFormat.forPattern("dd.MM.yyyy")
  def fromMap(map: Map[String, String]): DatePeriodValue = {
    DatePeriodValue(
      getPropertyValuesByKey("startDate", map).get,
      getPropertyValuesByKey("endDate", map).get)
  }
  def toMap(period: DatePeriodValue): Map[String, String] = {
    Map("startDate" -> period.startDate,
        "endDate" -> period.endDate)
  }

  def getPropertyValuesByKey(property: String, mapValue: Map[String, String]): Option[String] = {
    mapValue.get(property) match {
      case Some(x) => Some(x.toString)
      case _ => None
    }
  }
}

object ValidityPeriodValue {
  def fromMap(map: Map[String, Any]): ValidityPeriodValue = {
    ValidityPeriodValue(
        getPropertyValuesByPublicId("days", map),
        getPropertyValuesByPublicId("startHour", map),
        getPropertyValuesByPublicId("endHour", map),
        getPropertyValuesByPublicId("startMinute", map),
        getPropertyValuesByPublicId("endMinute", map),
        getOptionalPropertyValuesByPublicId("periodType", map))
  }

  def getPropertyValuesByPublicId(property: String, mapValue: Map[String, Any]): Int = {
    Try(mapValue(property).asInstanceOf[BigInt].toInt).getOrElse(mapValue(property).asInstanceOf[Int])
  }

  def getOptionalPropertyValuesByPublicId(property: String, mapValue: Map[String, Any]): Option[Int] = {
    mapValue.get(property) match {
      case Some(value) => Try(value.asInstanceOf[Option[BigInt]].map(_.toInt)).getOrElse(value.asInstanceOf[Option[Int]])
      case _ => None
    }
  }

  def toMap(value: ValidityPeriodValue):  Map[String, Any] = {
    Map(
      "days" -> value.days,
      "startHour" -> value.startHour,
      "endHour" -> value.endHour,
      "startMinute" -> value.startMinute,
      "endMinute" -> value.endMinute
    )
  }

  def duration(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int ): Int = {
    val startHourAndMinutes: Double = (startMinute / 60.0) + startHour
    val endHourAndMinutes: Double = (endMinute / 60.0) + endHour

    if (endHourAndMinutes > startHourAndMinutes) {
      Math.ceil(endHourAndMinutes - startHourAndMinutes).toInt
    } else {
      Math.ceil(24 - startHourAndMinutes + endHourAndMinutes).toInt
    }
  }
}

object PropertyTypes {
  val SingleChoice = "single_choice"
  val MultipleChoice = "multiple_choice"
  val Text = "text"
  val LongText = "long_text"
  val ReadOnlyText = "read_only_text"
  val ReadOnlyNumber = "read_only_number"
  val Date = "date"
  val ReadOnly = "read-only"
  val CheckBox = "checkbox"
  val Number = "number"
  val IntegerProp = "integer"
  val TimePeriod = "time_period"
  val AdditionalPanelType = "additional_panel_type"
  val DatePeriodType = "date_period"
}

object MassTransitStopValidityPeriod {
  val Past = "past"
  val Current = "current"
  val Future = "future"
}

case class BoundingRectangle(leftBottom: Point, rightTop: Point) {
  def diagonal: Vector3d = leftBottom - rightTop
  def area: Double = diagonal.x*diagonal.y
}

sealed trait AssetTypeInfo {
  val typeId: Int
  def geometryType: String
  val label: String
  val layerName: String
  val nameFI: String
}
//TODO change the type to be optional since manoeuvre are stored in a separated table and geometry type can be a type and the label can be a toString override
object AssetTypeInfo {
  val values =  Set(MassTransitStopAsset, SpeedLimitAsset,TotalWeightLimit, TrailerTruckWeightLimit, AxleWeightLimit, BogieWeightLimit,
                    HeightLimit, LengthLimit, WidthLimit, LitRoad, PavedRoad, RoadWidth, DamagedByThaw, NumberOfLanes, MassTransitLane,
                    TrafficVolume, WinterSpeedLimit, Prohibition, PedestrianCrossings, HazmatTransportProhibition, Obstacles,
                    RailwayCrossings, DirectionalTrafficSigns, ServicePoints, EuropeanRoads, ExitNumbers, TrafficLights,
                    MaintenanceRoadAsset, TrafficSigns, StateSpeedLimit, TrWeightLimit, TrTrailerTruckWeightLimit, TrAxleWeightLimit,
                    TrBogieWeightLimit, TrHeightLimit, TrWidthLimit, Manoeuvres, CareClass, CarryingCapacity, AnimalWarnings, RoadWorksAsset,
                    ParkingProhibition, CyclingAndWalking, Lanes,
                    UnknownAssetTypeId)

  /**
    * assets which use side code
    */
  val updateSideCodes: Seq[AssetTypeInfo] = Seq(
    SpeedLimitAsset,WinterSpeedLimit,
    TotalWeightLimit,TrailerTruckWeightLimit,AxleWeightLimit,
    BogieWeightLimit,HeightLimit,LengthLimit,WidthLimit,RoadWorksAsset,ParkingProhibition,
    NumberOfLanes,
    HazmatTransportProhibition,Prohibition,CyclingAndWalking
  )

  def apply(value: Int): AssetTypeInfo = {
    values.find(_.typeId == value).getOrElse(UnknownAssetTypeId)
  }

  def apply(stringValue: String): AssetTypeInfo = {
    values.find(_.toString == stringValue).getOrElse(UnknownAssetTypeId)
  }
}
case object MassTransitStopAsset extends AssetTypeInfo { val typeId = 10; def geometryType = "point"; val label = "MassTransitStop"; val layerName = "massTransitStop"; val nameFI = "Bussipysäkit"}
case object SpeedLimitAsset extends AssetTypeInfo { val typeId = 20; def geometryType = "linear"; val label = "SpeedLimit"; val layerName = "speedLimits"; val nameFI = "Nopeusrajoitukset"}
case object TotalWeightLimit extends AssetTypeInfo { val typeId = 30; def geometryType = "linear"; val label = "TotalWeightLimit" ; val layerName = "totalWeightLimit"; val nameFI = "Kokonaispainorajoitukset"}
case object TrailerTruckWeightLimit extends AssetTypeInfo { val typeId = 40; def geometryType = "linear"; val label = "TrailerTruckWeightLimit"; val layerName = "trailerTruckWeightLimit"; val nameFI = "Ajoneuvoyhdistelmän suurin sallittu massa" }
case object AxleWeightLimit extends AssetTypeInfo { val typeId = 50; def geometryType = "linear"; val label = "AxleWeightLimit"; val layerName = "axleWeightLimit"; val nameFI = "Ajoneuvon suurin sallittu akselimassa" }
case object BogieWeightLimit extends AssetTypeInfo { val typeId = 60; def geometryType = "linear"; val label =  "BogieWeightLimit"; val layerName = "bogieWeightLimit"; val nameFI = "Ajoneuvon suurin sallittu telimassa" }
case object HeightLimit extends AssetTypeInfo { val typeId = 70; def geometryType = "linear"; val label = "HeightLimit"; val layerName = "heightLimit"; val nameFI = "Ajoneuvon suurin sallittu korkeus" }
case object LengthLimit extends AssetTypeInfo { val typeId = 80; def geometryType = "linear"; val label = "LengthLimit"; val layerName = "lengthLimit"; val nameFI = "Ajoneuvon tai -yhdistelmän suurin sallittu pituus" }
case object WidthLimit extends AssetTypeInfo { val typeId = 90; def geometryType = "linear"; val label = "WidthLimit"; val layerName = "widthLimit"; val nameFI = "Ajoneuvon suurin sallittu leveys" }
case object LitRoad extends AssetTypeInfo { val typeId = 100; def geometryType = "linear"; val label = "LitRoad"; val layerName = "litRoad"; val nameFI = "Valaistu tie" }
case object PavedRoad extends AssetTypeInfo { val typeId = 110; def geometryType = "linear"; val label = "PavedRoad"; val layerName = "pavedRoad"; val nameFI = "Päällystetty tie" }
case object RoadWidth extends AssetTypeInfo { val typeId = 120; def geometryType = "linear"; val label =  "RoadWidth"; val layerName = "roadWidth"; val nameFI = "Tien leveys"}
case object DamagedByThaw extends AssetTypeInfo { val typeId = 130; def geometryType = "linear"; val label = "DamagedByThaw"; val layerName = "roadDamagedByThaw"; val nameFI = "Kelirikko" }
case object NumberOfLanes extends AssetTypeInfo { val typeId = 140; def geometryType = "linear"; val label = "NumberOfLanes"; val layerName = "numberOfLanes"; val nameFI = "Kaistojen lukumäärä" }
case object MassTransitLane extends AssetTypeInfo { val typeId = 160; def geometryType = "linear"; val label = "MassTransitLane"; val layerName = "massTransitLanes"; val nameFI = "Joukkoliikennekaistat"  }
case object TrafficVolume extends AssetTypeInfo { val typeId = 170; def geometryType = "linear"; val label = "TrafficVolume"; val layerName = "trafficVolume"; val nameFI = "Liikennemäärä" }
case object WinterSpeedLimit extends AssetTypeInfo { val typeId = 180; def geometryType = "linear"; val label = "WinterSpeedLimit"; val layerName = "winterSpeedLimits"; val nameFI = "Talvinopeusrajoitukset"  }
case object Prohibition extends AssetTypeInfo { val typeId = 190; def geometryType = "linear"; val label = ""; val layerName = "prohibition"; val nameFI = "Ajokielto" }
case object PedestrianCrossings extends AssetTypeInfo { val typeId = 200; def geometryType = "point"; val label = "PedestrianCrossings"; val layerName = "pedestrianCrossings"; val nameFI = "Suojatie" }
case object HazmatTransportProhibition extends AssetTypeInfo { val typeId = 210; def geometryType = "linear"; val label = "HazmatTransportProhibition"; val layerName = "hazardousMaterialTransportProhibition"; val nameFI = "VAK-rajoitus" }
case object Obstacles extends AssetTypeInfo { val typeId = 220; def geometryType = "point"; val label = ""; val layerName = "obstacles"; val nameFI = "Esterakennelma" }
case object RailwayCrossings extends AssetTypeInfo { val typeId = 230; def geometryType = "point"; val label = ""; val layerName = "railwayCrossings"; val nameFI = "Rautatien tasoristeys" }
case object DirectionalTrafficSigns extends AssetTypeInfo { val typeId = 240; def geometryType = "point"; val label = ""; val layerName = "directionalTrafficSigns"; val nameFI = "Opastustaulu ja sen informaatio" }
case object ServicePoints extends AssetTypeInfo { val typeId = 250; def geometryType = "point"; val label = ""; val layerName = "servicePoints"; val nameFI = "Palvelupiste" }
case object EuropeanRoads extends AssetTypeInfo { val typeId = 260; def geometryType = "linear"; val label = ""; val layerName = "europeanRoads"; val nameFI = "Eurooppatienumero" }
case object ExitNumbers extends AssetTypeInfo { val typeId = 270; def geometryType = "linear"; val label = ""; val layerName = "exitNumbers"; val nameFI = "Liittymänumero" }
case object TrafficLights extends AssetTypeInfo { val typeId = 280; def geometryType = "point"; val label =  ""; val layerName = "trafficLights"; val nameFI = "Liikennevalo"}
case object MaintenanceRoadAsset extends AssetTypeInfo { val typeId = 290; def geometryType = "linear"; val label = ""; val layerName = "maintenanceRoads"; val nameFI = "Huoltotie" }
case object TrafficSigns extends AssetTypeInfo { val typeId = 300; def geometryType = "point"; val label = ""; val layerName = "trafficSigns"; val nameFI = "Liikennemerkki"}
case object StateSpeedLimit extends AssetTypeInfo { val typeId = 310; def geometryType = "linear"; val label = "StateSpeedLimit"; val layerName = "totalWeightLimit"; val nameFI = "Tierekisteri Nopeusrajoitukset" }
case object UnknownAssetTypeId extends  AssetTypeInfo {val typeId = 99; def geometryType = ""; val label = ""; val layerName = ""; val nameFI = ""}
case object TrWeightLimit extends  AssetTypeInfo {val typeId = 320; def geometryType = "point"; val label = "TrWeightLimit"; val layerName = "trWeightLimits"; val nameFI = "Tierekisteri Suurin sallittu massa"}
case object TrTrailerTruckWeightLimit extends  AssetTypeInfo {val typeId = 330; def geometryType = "point"; val label = "TrTrailerTruckWeightLimit"; val layerName = "trWeightLimits"; val nameFI = "Tierekisteri Yhdistelmän suurin sallittu massa"}
case object TrAxleWeightLimit extends  AssetTypeInfo {val typeId = 340; def geometryType = "point"; val label = "TrAxleWeightLimit"; val layerName = "trWeightLimits"; val nameFI = "Tierekisteri Suurin sallittu akselimassa"}
case object TrBogieWeightLimit extends  AssetTypeInfo {val typeId = 350; def geometryType = "point"; val label = "TrBogieWeightLimit"; val layerName = "trWeightLimits"; val nameFI = "Tierekisteri Suurin sallittu telimassa"}
case object TrHeightLimit extends  AssetTypeInfo {val typeId = 360; def geometryType = "point"; val label = "TrHeightLimit"; val layerName = "trHeightLimits"; val nameFI = "Tierekisteri Suurin sallittu korkeus"}
case object TrWidthLimit extends  AssetTypeInfo {val typeId = 370; def geometryType = "point"; val label = "TrWidthLimit"; val layerName = "trWidthLimits"; val nameFI = "Tierekisteri Suurin sallittu leveys"} //TODO TR asset are hidden for now 
case object Manoeuvres extends AssetTypeInfo { val typeId = 380; def geometryType = "linear"; val label = "Manoeuvre"; val layerName = "manoeuvre"; val nameFI = "Kääntymisrajoitus" }
case object CareClass extends  AssetTypeInfo {val typeId = 390; def geometryType = "linear"; val label = "CareClass"; val layerName = "careClass"; val nameFI = "Hoitoluokat"}
case object CarryingCapacity extends AssetTypeInfo { val typeId = 400; def geometryType = "linear"; val label = "CarryingCapacity" ; val layerName = "carryingCapacity"; val nameFI = "Kantavuus"}
case object AnimalWarnings extends AssetTypeInfo { val typeId = 410; def geometryType = "linear"; val label = "AnimalWarnings" ; val layerName = "animalWarnings"; val nameFI = "Elainvaroitukset"}
case object RoadWorksAsset extends AssetTypeInfo { val typeId = 420; def geometryType = "linear"; val label = "RoadWorks" ; val layerName = "roadWorks"; val nameFI = "Tietyot"}
case object ParkingProhibition extends AssetTypeInfo { val typeId = 430; def geometryType = "linear"; val label = "ParkingProhibition" ; val layerName = "parkingProhibition"; val nameFI = "Pysäköintikielto"}
case object CyclingAndWalking extends AssetTypeInfo { val typeId = 440; def geometryType = "linear"; val label = "CyclingAndWalking" ; val layerName = "cyclingAndWalking"; val nameFI = "Käpy tietolaji"}
case object Lanes extends AssetTypeInfo { val typeId = 450; def geometryType = "linear"; val label = "Lanes" ; val layerName = "lanes"; val nameFI = "Kaista"}


object AutoGeneratedUsername {

  val dr1Conversion = "dr1_conversion"
  val automaticCorrection = "automatic_correction"
  val excelDataMigration = "excel_data_migration"
  val automaticGeneration = "automatic_generation"
  val generatedInUpdate = "generated_in_update"
  val automaticAdjustment = "automatic_adjustment"
  val mtkClassDefault = "mtk_class_default"
  val autoGeneratedLane = "auto_generated_lane"
  val startDateImporter = "start_date_importer"
  val annualUpdate = "annually_updated_period"
  val splitSpeedLimitPrefix = "split_speedlimit_"
  val batchProcessPrefix = "batch_process_"

  val values =
    Seq(dr1Conversion, automaticCorrection, excelDataMigration, automaticGeneration, generatedInUpdate, automaticAdjustment,
      mtkClassDefault, autoGeneratedLane, startDateImporter, annualUpdate)

  val prefixes = Seq(splitSpeedLimitPrefix, batchProcessPrefix)

}

object Decode {
  def getPageAndRecordNumber(src: String): (Int, Int) = {
    val values = new String(Base64.getDecoder.decode(src), StandardCharsets.UTF_8).split(',')
      .flatMap(_.split(':').map(_.trim) match {
        case Array(s: String, i: String) => Map(s -> i.toInt)
      }).toMap

    val startNum = values("recordNumber") * (values("pageNumber") - 1) + 1
    val endNum = values("pageNumber") * values("recordNumber")

    (startNum, endNum)
  }
}

sealed trait Ely {
  val id: Int
  val nameFi: String
  val nameSV: String
}
object Ely {
  val values =  Set(
    AhvenanmaaEly, LappiEly, PohjoisPohjanmaaEly, EtelaPohjanmaaEly, KeskiSuomiEly, PohjoisSavoEly,
    PirkanmaaEly, KaakkoisSuomiEly, UusimaaEly, VarsinaisSuomiEly
  )
}
case object AhvenanmaaEly extends Ely { val id = 0; val nameFi = "Ahvenanmaa"; val nameSV = "Aland"}
case object LappiEly extends Ely { val id = 1; val nameFi = "Lappi"; val nameSV = "Lappland"}
case object PohjoisPohjanmaaEly extends Ely { val id = 2; val nameFi = "Pohjois-Pohjanmaa"; val nameSV = "Norra Osterbotten"}
case object EtelaPohjanmaaEly extends Ely { val id = 3; val nameFi = "Etela-Pohjanmaa"; val nameSV = "Sodra Osterbotten"}
case object KeskiSuomiEly extends Ely { val id = 4; val nameFi = "Keski-Suomi"; val nameSV = "Mellersta Finland"}
case object PohjoisSavoEly extends Ely { val id = 5; val nameFi = "Pohjois-Savo"; val nameSV = "Norra Savolax"}
case object PirkanmaaEly extends Ely { val id = 6; val nameFi = "Pirkanmaa"; val nameSV = "Birkaland"}
case object KaakkoisSuomiEly extends Ely { val id = 7; val nameFi = "Kaakkois-Suomi"; val nameSV = "Sydvastra Finland"}
case object UusimaaEly extends Ely { val id = 8; val nameFi = "Uusimaa"; val nameSV = "Nyland"}
case object VarsinaisSuomiEly extends Ely { val id = 9; val nameFi = "Varsinais-Suomi"; val nameSV = "Egentliga Finland"}
