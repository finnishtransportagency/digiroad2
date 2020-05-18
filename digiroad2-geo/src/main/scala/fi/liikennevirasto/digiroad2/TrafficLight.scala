package fi.liikennevirasto.digiroad2

sealed trait TrafficLightType {
  def value: Double
  def description: String
}
object TrafficLightType {
  val values = Set(Basic, ArrowRight, ArrowLeft, Triangle, PublicTransport, BasicBicycle, ArrowBicycle, Pedestrians, LaneSpecific)

  def apply(doubleValue: Double):TrafficLightType = {
    values.find(_.value == doubleValue).getOrElse(Basic)
  }

  case object Basic extends TrafficLightType { def value = 1; def description = "Valo-opastin"  }
  case object ArrowRight extends TrafficLightType { def value = 4.1; def description = "Nuolivalo oikealle" }
  case object ArrowLeft extends TrafficLightType { def value = 4.2; def description = "Nuolivalo vasemmalle" }
  case object Triangle extends TrafficLightType { def value = 5; def description = "Kolmio-opastin" }
  case object PublicTransport extends TrafficLightType { def value = 8; def description = "Joukkoliikenneopastin" }
  case object BasicBicycle extends TrafficLightType { def value = 9.1; def description = "Polkupyöräopastin" }
  case object ArrowBicycle extends TrafficLightType { def value = 9.2; def description = "Polkupyörän nuolivalo" }
  case object Pedestrians extends TrafficLightType { def value = 10; def description = "Jalankulkijan opastin" }
  case object LaneSpecific extends TrafficLightType { def value = 11; def description = "Ajokaistaopastin" }
}

sealed trait TrafficLightStructure {
  def value: Int
  def description: String
}
object TrafficLightStructure {
  val values = Set(Unknown, Pole, Wall, Bridge, Portal, HalfPortal, Barrier, Other )

  def apply(intValue: Int):TrafficLightStructure = {
    values.find(_.value == intValue).getOrElse(Unknown)
  }

  case object Pole extends TrafficLightStructure { def value = 1; def description = "Pylväs"  }
  case object Wall extends TrafficLightStructure { def value = 2; def description = "Seinä" }
  case object Bridge extends TrafficLightStructure { def value = 3; def description = "Silta" }
  case object Portal extends TrafficLightStructure { def value = 4; def description = "Portaali" }
  case object HalfPortal extends TrafficLightStructure { def value = 5; def description = "Puoliportaali" }
  case object Barrier extends TrafficLightStructure { def value = 6; def description = "Puomi tai muu esterakennelma" }
  case object Other extends TrafficLightStructure { def value = 7; def description = "Muu" }
  case object Unknown extends TrafficLightStructure { def value = 999; def description = "Ei tiedossa" }
}

sealed trait TrafficLightSoundSignal {
  def value: Int
  def description: String
}
object TrafficLightSoundSignal {
  val values = Set(Unknown, No, Yes)

  def apply(intValue: Int):TrafficLightSoundSignal = {
    values.find(_.value == intValue).getOrElse(Unknown)
  }

  case object No extends TrafficLightSoundSignal { def value = 1; def description = "Ei"  }
  case object Yes extends TrafficLightSoundSignal { def value = 2; def description = "Kyllä" }
  case object Unknown extends TrafficLightSoundSignal { def value = 999; def description = "Ei tiedossa" }
}

sealed trait TrafficLightVehicleDetection {
  def value: Int
  def description: String
}
object TrafficLightVehicleDetection {
  val values = Set(Coil, InfraRed, Radar, Other, Unknown)

  def apply(intValue: Int):TrafficLightVehicleDetection = {
    values.find(_.value == intValue).getOrElse(Unknown)
  }

  case object Coil extends TrafficLightVehicleDetection { def value = 1; def description = "Silmukka"  }
  case object InfraRed extends TrafficLightVehicleDetection { def value = 2; def description = "Infrapunailmaisin" }
  case object Radar extends TrafficLightVehicleDetection { def value = 3; def description = "Tutka eli mikroaaltoilmaisin" }
  case object Other extends TrafficLightVehicleDetection { def value = 4; def description = "Muu" }
  case object Unknown extends TrafficLightVehicleDetection { def value = 999; def description = "Ei tiedossa" }
}

sealed trait TrafficLightPushButton {
  def value: Int
  def description: String
}
object TrafficLightPushButton {
  val values = Set(No, Yes, Unknown)

  def apply(intValue: Int):TrafficLightPushButton = {
    values.find(_.value == intValue).getOrElse(Unknown)
  }

  case object No extends TrafficLightPushButton { def value = 1; def description = "Ei"  }
  case object Yes extends TrafficLightPushButton { def value = 2; def description = "Kyllä" }
  case object Unknown extends TrafficLightPushButton { def value = 999; def description = "Ei tiedossa" }
}

sealed trait TrafficLightRelativePosition {
  def value: Int
  def description: String
}
object TrafficLightRelativePosition {
  val values = Set(RightSideOfRoad, AboveLane, TrafficIslandOrTrafficDivider, Unknown)

  def apply(intValue: Int):TrafficLightRelativePosition = {
    values.find(_.value == intValue).getOrElse(RightSideOfRoad)
  }

  case object RightSideOfRoad extends TrafficLightRelativePosition { def value = 1; def description = "Ajoradan oikea puoli"  }
  case object AboveLane extends TrafficLightRelativePosition { def value = 2; def description = "Kaistojen yläpuolella" }
  case object TrafficIslandOrTrafficDivider extends TrafficLightRelativePosition { def value = 3; def description = "Keskisaareke tai liikenteenjakaja" }
  case object Unknown extends TrafficLightRelativePosition { def value = 999; def description = "Ei tiedossa" }
}

sealed trait TrafficLightState {
  def value: Int
  def description: String
}
object TrafficLightState{
  val values = Set(Unknown, Planned, UnderConstruction, PermanentlyInUse, TemporarilyInUse, TemporarilyOutOfService, OutgoingPermanentDevice )

  def apply(intValue: Int):TrafficLightState = {
    values.find(_.value == intValue).getOrElse(PermanentlyInUse)
  }

  case object Planned extends TrafficLightState { def value = 1; def description = "Suunnitteilla"  }
  case object UnderConstruction extends TrafficLightState { def value = 2; def description = "Rakenteilla" }
  case object PermanentlyInUse extends TrafficLightState { def value = 3; def description = "Käytössä pysyvästi" }
  case object TemporarilyInUse extends TrafficLightState { def value = 4; def description = "Käytössä tilapäisesti" }
  case object TemporarilyOutOfService extends TrafficLightState { def value = 5; def description = "Pois käytössä tilapäisesti" }
  case object OutgoingPermanentDevice extends TrafficLightState { def value = 6; def description = "Poistuva pysyvä laite" }
  case object Unknown extends TrafficLightState { def value = 999; def description = "Ei tiedossa" }
}