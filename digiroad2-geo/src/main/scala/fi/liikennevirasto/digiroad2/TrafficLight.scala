package fi.liikennevirasto.digiroad2

sealed trait TrafficLightType {
  def value: Double
  def description: String
}
object TrafficLightType {
  val values = Set(Basic, ArrowRight, ArrowLeft, Triangle, PublicTransport, BasicBicycle, ArrowBicycle, Pedestrians, LaneSpecific)

  def apply(doubleValue: Double):TrafficLightType = {
    values.find(_.value == doubleValue).getOrElse(getDefault)
  }

  def getDefault: TrafficLightType = Basic

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

sealed trait TrafficLightSoundSignal {
  def value: Int
  def description: String
}
object TrafficLightSoundSignal {
  val values = Set(Unknown, No, Yes)

  def apply(intValue: Int):TrafficLightSoundSignal = {
    values.find(_.value == intValue).getOrElse(getDefault)
  }

  def getDefault: TrafficLightSoundSignal = Unknown

  case object No extends TrafficLightSoundSignal { def value = 1; def description = "Ei"  }
  case object Yes extends TrafficLightSoundSignal { def value = 2; def description = "Kyllä" }
  case object Unknown extends TrafficLightSoundSignal { def value = 99; def description = "Ei tiedossa" }
}

sealed trait TrafficLightVehicleDetection {
  def value: Int
  def description: String
}
object TrafficLightVehicleDetection {
  val values = Set(Coil, InfraRed, Radar, Other, Unknown)

  def apply(intValue: Int):TrafficLightVehicleDetection = {
    values.find(_.value == intValue).getOrElse(getDefault)
  }

  def getDefault: TrafficLightVehicleDetection = Unknown

  case object Coil extends TrafficLightVehicleDetection { def value = 1; def description = "Silmukka"  }
  case object InfraRed extends TrafficLightVehicleDetection { def value = 2; def description = "Infrapunailmaisin" }
  case object Radar extends TrafficLightVehicleDetection { def value = 3; def description = "Tutka eli mikroaaltoilmaisin" }
  case object Other extends TrafficLightVehicleDetection { def value = 4; def description = "Muu" }
  case object Unknown extends TrafficLightVehicleDetection { def value = 99; def description = "Ei tiedossa" }
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

  def getDefault: TrafficLightPushButton = Unknown

  case object No extends TrafficLightPushButton { def value = 1; def description = "Ei"  }
  case object Yes extends TrafficLightPushButton { def value = 2; def description = "Kyllä" }
  case object Unknown extends TrafficLightPushButton { def value = 99; def description = "Ei tiedossa" }
}

sealed trait TrafficLightRelativePosition {
  def value: Int
  def description: String
}
object TrafficLightRelativePosition {
  val values = Set(RightSideOfRoad, AboveLane, TrafficIslandOrTrafficDivider, Unknown)

  def apply(intValue: Int):TrafficLightRelativePosition = {
    values.find(_.value == intValue).getOrElse(getDefault)
  }

  def getDefault: TrafficLightRelativePosition = RightSideOfRoad

  case object RightSideOfRoad extends TrafficLightRelativePosition { def value = 1; def description = "Ajoradan oikea puoli"  }
  case object AboveLane extends TrafficLightRelativePosition { def value = 2; def description = "Kaistojen yläpuolella" }
  case object TrafficIslandOrTrafficDivider extends TrafficLightRelativePosition { def value = 3; def description = "Keskisaareke tai liikenteenjakaja" }
  case object Unknown extends TrafficLightRelativePosition { def value = 99; def description = "Ei tiedossa" }
}