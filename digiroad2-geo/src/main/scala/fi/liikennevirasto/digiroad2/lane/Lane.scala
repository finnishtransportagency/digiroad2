package fi.liikennevirasto.digiroad2.lane

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.ConstructionType.UnknownConstructionType
import fi.liikennevirasto.digiroad2.asset.SideCode.{AgainstDigitizing, BothDirections, TowardsDigitizing, values}
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, SideCode, TrafficDirection}
import fi.liikennevirasto.digiroad2.linearasset.{PolyLine, RoadLink}
import org.joda.time.DateTime
import org.json4s._

trait Lane extends PolyLine{
  val id: Long
  val linkId: String
  val sideCode: Int
  val timeStamp: Long
  val geomModifiedDate: Option[DateTime]
  val laneAttributes: Seq[LaneProperty]
}
case class LaneChange(lane: PersistedLane, oldLane: Option[PersistedLane], changeType: LaneChangeType, roadLink: Option[RoadLink], historyEventOrderNumber: Option[Int])
case class ChangedSegment(startMeasure: Double, startAddrM: Int, endMeasure: Double, endAddrM: Int, segmentChangeType: LaneChangeType)

case class LightLane ( value: Int, expired: Boolean,  sideCode: Int )

case class PieceWiseLane ( id: Long, linkId: String, sideCode: Int, expired: Boolean, geometry: Seq[Point],
                                startMeasure: Double, endMeasure: Double,
                                endpoints: Set[Point], modifiedBy: Option[String], modifiedDateTime: Option[DateTime],
                                createdBy: Option[String], createdDateTime: Option[DateTime],
                                timeStamp: Long, geomModifiedDate: Option[DateTime], administrativeClass: AdministrativeClass,
                           laneAttributes: Seq[LaneProperty],  attributes: Map[String, Any] = Map() ) extends Lane

case class PersistedLane ( id: Long, linkId: String, sideCode: Int, laneCode: Int, municipalityCode: Long,
                           startMeasure: Double, endMeasure: Double,
                           createdBy: Option[String], createdDateTime: Option[DateTime],
                           modifiedBy: Option[String], modifiedDateTime: Option[DateTime],
                           expiredBy: Option[String], expiredDateTime: Option[DateTime], expired: Boolean,
                           timeStamp: Long, geomModifiedDate: Option[DateTime], attributes: Seq[LaneProperty] )

case class PersistedHistoryLane(id: Long, newId: Long, oldId: Long, linkId: String, sideCode: Int, laneCode: Int, municipalityCode: Long,
                                startMeasure: Double, endMeasure: Double,
                                createdBy: Option[String], createdDateTime: Option[DateTime],
                                modifiedBy: Option[String], modifiedDateTime: Option[DateTime], expired: Boolean,
                                timeStamp: Long, geomModifiedDate: Option[DateTime], attributes: Seq[LaneProperty],
                                historyCreatedDate: DateTime, historyCreatedBy: String, changeEventOrderNumber: Option[Int])

case class NewLane(id: Long, startMeasure: Double, endMeasure: Double, municipalityCode : Long,
                   isExpired: Boolean = false, isDeleted: Boolean = false, properties: Seq[LaneProperty], sideCode:Option[Int]=None, newLaneCode: Option[Int] = None )

case class ViewOnlyLane(linkId: String, startMeasure: Double, endMeasure: Double, sideCode: Int, trafficDirection: TrafficDirection,
                        geometry: Seq[Point], lanes: Seq[Int], linkType: Int, constructionType: Int = UnknownConstructionType.value)

case class SideCodesForLinkIds(linkId: String, sideCode: Int)

sealed trait LaneValue {
  def toJson: Any
}

case class LanePropertyValue(value: Any) {
  def toJson: JValue = JString(value.toString)
}
case class LaneProperty(publicId: String,  values: Seq[LanePropertyValue]) {
  def toJson = List(JField("publicId", JString(publicId)), JField("values", JArray(values.map(_.toJson).toList)))
}

case class LaneRoadAddressInfo ( roadNumber: Long, startRoadPart: Long, startDistance: Long,
                                 endRoadPart: Long, endDistance: Long, track: Int )
case class LaneEndPoints ( start: Double, end: Double )

/**
  * Values for lane numbers
  */
sealed trait LaneNumber {
  def towardsDirection: Int
  def againstDirection : Int
  def oneDigitLaneCode: Int
}



object LaneNumber {
  val values = Set(MainLane, FirstLeftAdditional, FirstRightAdditional, SecondLeftAdditional, SecondRightAdditional,
    ThirdLeftAdditional, ThirdRightAdditional, FourthLeftAdditional, FourthRightAdditional, Unknown)

  def apply(value: Int): LaneNumber = {
    val valueAsStr = value.toString

    if(valueAsStr.length != 2 ) {
      Unknown

    } else {
      val index = valueAsStr.substring(0, 1).toInt

      if (index == 3)
        MainLane
      else if (index == 1)
        values.find(_.towardsDirection == value).getOrElse(Unknown)
      else
        values.find(_.againstDirection == value).getOrElse(Unknown)
    }
  }

  def isMainLane (laneCode : Int): Boolean = {
    val mainLanes = Seq(MainLane.towardsDirection, MainLane.againstDirection, MainLane.motorwayMaintenance, MainLane.oneDigitLaneCode)

    mainLanes.contains(laneCode)
  }

  def isValidLaneNumber (laneCode: Int): Boolean = {
    val lanesNumbers = values.filterNot(_ == Unknown)
    lanesNumbers.exists(x => x.againstDirection == laneCode || x.towardsDirection == laneCode || x.oneDigitLaneCode == laneCode) || MainLane.motorwayMaintenance == laneCode
  }

  def getTwoDigitLaneCode(roadAddressSideCode: SideCode, laneSideCode: SideCode, oneDigitLaneCode: Int): Int = {
    val laneCodeFirstDigit = roadAddressSideCode match {
      case TowardsDigitizing if laneSideCode == TowardsDigitizing => 1
      case TowardsDigitizing if laneSideCode == AgainstDigitizing => 2

      case AgainstDigitizing if laneSideCode == TowardsDigitizing => 2
      case AgainstDigitizing if laneSideCode == AgainstDigitizing => 1
      case _ if laneSideCode == BothDirections => 3
    }
    val twoDigitLaneCode = laneCodeFirstDigit.toString.concat(oneDigitLaneCode.toString).toInt

    twoDigitLaneCode
  }

  case object MainLane extends LaneNumber {
    def towardsDirection = 11
    def againstDirection = 21
    def motorwayMaintenance: Int = 31
    def oneDigitLaneCode = 1
  }

  case object FirstLeftAdditional extends LaneNumber {
    def towardsDirection = 12
    def againstDirection = 22
    def oneDigitLaneCode = 2
  }

  case object FirstRightAdditional extends LaneNumber {
    def towardsDirection = 13
    def againstDirection = 23
    def oneDigitLaneCode = 3
  }

  case object SecondLeftAdditional extends LaneNumber {
    def towardsDirection = 14
    def againstDirection = 24
    def oneDigitLaneCode = 4
  }

  case object SecondRightAdditional extends LaneNumber {
    def towardsDirection = 15
    def againstDirection = 25
    def oneDigitLaneCode = 5
  }

  case object ThirdLeftAdditional extends LaneNumber {
    def towardsDirection = 16
    def againstDirection = 26
    def oneDigitLaneCode = 6
  }

  case object ThirdRightAdditional extends LaneNumber {
    def towardsDirection = 17
    def againstDirection = 27
    def oneDigitLaneCode = 7
  }

  case object FourthLeftAdditional extends LaneNumber {
    def towardsDirection = 18
    def againstDirection = 28
    def oneDigitLaneCode = 8
  }

  case object FourthRightAdditional extends LaneNumber {
    def towardsDirection = 19
    def againstDirection = 29
    def oneDigitLaneCode = 9
  }

  case object Unknown extends LaneNumber {
    def towardsDirection = 99
    def againstDirection = 99
    def oneDigitLaneCode = 99
  }
}

/**
  * Values for lane types
  */
sealed trait LaneType {
  def value: Int
  def typeDescription: String
  def finnishDescription: String
}
object LaneType {
  val values = Set(Main, Passing, TurnRight, TurnLeft, Through, Acceleration, Deceleration, OperationalAuxiliary, MassTransitTaxi, Truckway,
                  Reversible, BicycleLane, Combined, Sidewalk, CyclePath, PedestrianZone, BicycleStreet, Unknown)

  def apply(value: Int): LaneType = {
    values.find(_.value == value).getOrElse(getDefault)
  }

  def getDefault: LaneType = Unknown

  case object Main extends LaneType { def value = 1; def typeDescription = "Main lane"; def finnishDescription = "Pääkaista"; }
  case object Passing extends LaneType { def value = 2; def typeDescription = "Passing lane"; def finnishDescription = "Ohituskaista"; }
  case object TurnRight extends LaneType { def value = 3; def typeDescription = "Turn lane to right"; def finnishDescription = "Kääntymiskaista oikealle"; }
  case object TurnLeft extends LaneType { def value = 4; def typeDescription = "Turn lane to left"; def finnishDescription = "Kääntymiskaista vasemmalle"; }
  case object Through extends LaneType { def value = 5; def typeDescription = "Through lane"; def finnishDescription = "Lisäkaista suoraan ajaville"; }
  case object Acceleration extends LaneType { def value = 6; def typeDescription = "Acceleration lane"; def finnishDescription = "Liittymiskaista"; }
  case object Deceleration extends LaneType { def value = 7; def typeDescription = "Deceleration lane"; def finnishDescription = "Erkanemiskaista"; }
  case object OperationalAuxiliary extends LaneType { def value = 8; def typeDescription = "Operational or auxiliary lane"; def finnishDescription = "Sekoittumiskaista"; }
  case object MassTransitTaxi extends LaneType { def value = 9; def typeDescription = "Mass transit or taxi lane"; def finnishDescription = "Joukkoliikenteen kaista / taksikaista"; }
  case object Truckway extends LaneType { def value = 10; def typeDescription = "Truckway"; def finnishDescription = "Raskaan liikenteen kaista"; }
  case object Reversible extends LaneType { def value = 11; def typeDescription = "Reversible lane"; def finnishDescription = "Vaihtuvasuuntainen kaista"; }
  case object BicycleLane extends LaneType { def value = 12; def typeDescription = "Bicycle lane"; def finnishDescription = "Pyöräkaista"; }
  case object Combined extends LaneType { def value = 20; def typeDescription = "Combined bike path and sidewalk"; def finnishDescription = "Yhdistetty pyörätie ja jalkakäytävä"; }
  case object Sidewalk extends LaneType { def value = 21; def typeDescription = "Sidewalk"; def finnishDescription = "Jalkakäytävä"; }
  case object CyclePath extends LaneType { def value = 22; def typeDescription = "Cycle path"; def finnishDescription = "Pyörätie"; }
  case object PedestrianZone extends LaneType { def value = 23; def typeDescription = "Pedestrian  zone"; def finnishDescription = "Kävelykatu"; }
  case object BicycleStreet extends LaneType { def value = 24; def typeDescription = "Bicycle street"; def finnishDescription = "Pyöräkatu"; }
  case object Unknown extends LaneType { def value = 99;  def typeDescription = "Unknown"; def finnishDescription = "Tuntematon"; }
}

/**
  * Values for changeType of lanes
  */
sealed trait LaneChangeType {
  def value: Int
  def description: String
}
object LaneChangeType {
  val values = Set(Add, Lengthened, Shortened, Expired, LaneCodeTransfer, AttributesChanged, Divided, Unknown)

  def apply(value: Int): LaneChangeType = {
    values.find(_.value == value).getOrElse(Unknown)
  }

  case object Add extends LaneChangeType { def value = 1; def description = "A new lane is added from scratch";}
  case object Lengthened extends LaneChangeType {def value = 2; def description = "The old lane is replaced with a longer new lane";}
  case object Shortened extends LaneChangeType {def value = 3; def description = "The old lane is replaced with a shorter new lane";}
  case object Expired extends LaneChangeType {def value = 4; def description = "A lane is expired with no replacements";}
  case object LaneCodeTransfer extends LaneChangeType {def value = 5; def description = "The lane code is changed";}
  case object AttributesChanged extends LaneChangeType {def value = 6; def description = "Some of the lane attributes are changed";}
  case object Divided extends LaneChangeType {def value = 7; def description = "The old lane is replaced with two or more lane pieces";}
  case object Unknown extends LaneChangeType {def value = 99; def description = "Unknown lane change";}
}

sealed trait LaneSegmentMeasuresChangeType {
  def measureBoolean(newLane: PersistedLane, oldLane: PersistedLane): Boolean
  def description: String
  def segmentMeasuresAndAddressM(newLane: PersistedLane, oldLane: PersistedLane, laneStartAddrM: Int, laneEndAddrM: Int,
                                 oldLaneStartAddrM: Int, oldLaneEndAddrM: Int, roadAddressSideCode: SideCode): ChangedSegment

}
object LaneSegmentMeasuresChangeType {
  val digitizingStartChanges = Seq(ShortenedFromStart, LengthenedFromStart)
  val digitizingEndChanges = Seq(ShortenedFromEnd, LengthenedFromEnd)

  def getDigitizingStartChangeType(newLane: PersistedLane, oldLane: PersistedLane): Option[LaneSegmentMeasuresChangeType] = {
    digitizingStartChanges.find(_.measureBoolean(newLane, oldLane))
  }

  def getDigitizingEndChangeType(newLane: PersistedLane, oldLane: PersistedLane): Option[LaneSegmentMeasuresChangeType] = {
    digitizingEndChanges.find(_.measureBoolean(newLane, oldLane))
  }

  case object ShortenedFromStart extends LaneSegmentMeasuresChangeType {
    def measureBoolean(newLane: PersistedLane, oldLane: PersistedLane): Boolean = newLane.startMeasure > oldLane.startMeasure
    def description = "Lane cut from digitizing direction start"

    def segmentMeasuresAndAddressM(newLane: PersistedLane, oldLane: PersistedLane, laneStartAddrM: Int, laneEndAddrM: Int,
                                   oldLaneStartAddrM: Int, oldLaneEndAddrM: Int, roadAddressSideCode: SideCode): ChangedSegment = {
      roadAddressSideCode match {
        case SideCode.TowardsDigitizing => ChangedSegment(oldLane.startMeasure, oldLaneStartAddrM, newLane.startMeasure, laneStartAddrM, LaneChangeType.Expired)
        case SideCode.AgainstDigitizing => ChangedSegment(oldLane.startMeasure, laneEndAddrM, newLane.startMeasure, oldLaneEndAddrM, LaneChangeType.Expired)
      }
    }
  }
  case object ShortenedFromEnd extends LaneSegmentMeasuresChangeType {
    def measureBoolean(newLane: PersistedLane, oldLane: PersistedLane): Boolean = newLane.endMeasure < oldLane.endMeasure
    def description = "Lane cut from digitizing direction end"

    def segmentMeasuresAndAddressM(newLane: PersistedLane, oldLane: PersistedLane, laneStartAddrM: Int, laneEndAddrM: Int,
                                   oldLaneStartAddrM: Int, oldLaneEndAddrM: Int, roadAddressSideCode: SideCode): ChangedSegment = {
      roadAddressSideCode match {
        case SideCode.TowardsDigitizing => ChangedSegment(newLane.endMeasure, laneEndAddrM, oldLane.endMeasure, oldLaneEndAddrM, LaneChangeType.Expired)
        case SideCode.AgainstDigitizing => ChangedSegment(newLane.endMeasure, laneStartAddrM, oldLane.endMeasure, oldLaneStartAddrM, LaneChangeType.Expired)
      }
    }
  }
  case object LengthenedFromStart extends LaneSegmentMeasuresChangeType {
    def measureBoolean(newLane: PersistedLane, oldLane: PersistedLane): Boolean = newLane.startMeasure < oldLane.startMeasure
    def description = "Lane lengthened from digitizing direction start"

    def segmentMeasuresAndAddressM(newLane: PersistedLane, oldLane: PersistedLane, laneStartAddrM: Int, laneEndAddrM: Int,
                                   oldLaneStartAddrM: Int, oldLaneEndAddrM: Int, roadAddressSideCode: SideCode): ChangedSegment = {
      roadAddressSideCode match {
        case SideCode.TowardsDigitizing => ChangedSegment(newLane.startMeasure, laneStartAddrM, oldLane.startMeasure, oldLaneStartAddrM, LaneChangeType.Add)
        case SideCode.AgainstDigitizing => ChangedSegment(newLane.startMeasure, oldLaneEndAddrM, oldLane.startMeasure, laneEndAddrM, LaneChangeType.Add)
      }
    }
  }
  case object LengthenedFromEnd extends LaneSegmentMeasuresChangeType {
    def measureBoolean(newLane: PersistedLane, oldLane: PersistedLane): Boolean = newLane.endMeasure > oldLane.endMeasure
    def description = "Lane lengthened from digitizing direction end"

    def segmentMeasuresAndAddressM(newLane: PersistedLane, oldLane: PersistedLane, laneStartAddrM: Int, laneEndAddrM: Int,
                                   oldLaneStartAddrM: Int, oldLaneEndAddrM: Int, roadAddressSideCode: SideCode): ChangedSegment = {
      roadAddressSideCode match {
        case SideCode.TowardsDigitizing => ChangedSegment(oldLane.endMeasure, oldLaneEndAddrM, newLane.endMeasure, laneEndAddrM, LaneChangeType.Add)
        case SideCode.AgainstDigitizing => ChangedSegment(oldLane.endMeasure, laneStartAddrM, newLane.endMeasure, oldLaneStartAddrM, LaneChangeType.Add)
      }
    }
  }

}
