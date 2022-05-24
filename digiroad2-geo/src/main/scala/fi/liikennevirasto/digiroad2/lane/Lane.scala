package fi.liikennevirasto.digiroad2.lane

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.{AdministrativeClass, TrafficDirection}
import fi.liikennevirasto.digiroad2.linearasset.PolyLine
import org.joda.time.DateTime

trait Lane extends PolyLine{
  val id: Long
  val linkId: Long
  val sideCode: Int
  val vvhTimeStamp: Long
  val geomModifiedDate: Option[DateTime]
  val laneAttributes: Seq[LaneProperty]
}

case class LightLane ( value: Int, expired: Boolean,  sideCode: Int )

case class PieceWiseLane ( id: Long, linkId: Long, sideCode: Int, expired: Boolean, geometry: Seq[Point],
                                startMeasure: Double, endMeasure: Double,
                                endpoints: Set[Point], modifiedBy: Option[String], modifiedDateTime: Option[DateTime],
                                createdBy: Option[String], createdDateTime: Option[DateTime],
                                vvhTimeStamp: Long, geomModifiedDate: Option[DateTime], administrativeClass: AdministrativeClass,
                           laneAttributes: Seq[LaneProperty],  attributes: Map[String, Any] = Map() ) extends Lane

case class PersistedLane ( id: Long, linkId: Long, sideCode: Int, laneCode: Int, municipalityCode: Long,
                           startMeasure: Double, endMeasure: Double,
                           createdBy: Option[String], createdDateTime: Option[DateTime],
                           modifiedBy: Option[String], modifiedDateTime: Option[DateTime],
                           expiredBy: Option[String], expiredDateTime: Option[DateTime], expired: Boolean,
                           vvhTimeStamp: Long, geomModifiedDate: Option[DateTime], attributes: Seq[LaneProperty] )

case class PersistedHistoryLane(id: Long, newId: Long, oldId: Long, linkId: Long, sideCode: Int, laneCode: Int, municipalityCode: Long,
                                startMeasure: Double, endMeasure: Double,
                                createdBy: Option[String], createdDateTime: Option[DateTime],
                                modifiedBy: Option[String], modifiedDateTime: Option[DateTime], expired: Boolean,
                                vvhTimeStamp: Long, geomModifiedDate: Option[DateTime], attributes: Seq[LaneProperty],
                                historyCreatedDate: DateTime, historyCreatedBy: String)

case class NewLane(id: Long, startMeasure: Double, endMeasure: Double, municipalityCode : Long,
                   isExpired: Boolean = false, isDeleted: Boolean = false, properties: Seq[LaneProperty], sideCode:Option[Int]=None )

case class ViewOnlyLane(linkId: Long, startMeasure: Double, endMeasure: Double, sideCode: Int, trafficDirection: TrafficDirection, geometry: Seq[Point], lanes: Seq[Int])

case class SideCodesForLinkIds(linkId: Long, sideCode: Int)

sealed trait LaneValue {
  def toJson: Any
}

case class LanePropertyValue(value: Any)
case class LaneProperty(publicId: String,  values: Seq[LanePropertyValue])

case class LaneRoadAddressInfo ( roadNumber: Long, startRoadPart: Long, startDistance: Long,
                                 endRoadPart: Long, endDistance: Long, track: Int )
case class LaneEndPoints ( start: Double, end: Double )

/**
  * Values for lane numbers
  */
sealed trait LaneNumber {
  def towardsDirection: Int
  def againstDirection : Int
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
    val mainLanes = Seq(MainLane.towardsDirection, MainLane.againstDirection, MainLane.motorwayMaintenance)

    mainLanes.contains(laneCode)
  }

  def isValidLaneNumber (laneCode: Int): Boolean = {
    val lanesNumbers = values.filterNot(_ == Unknown)
    lanesNumbers.exists(x => x.againstDirection == laneCode || x.towardsDirection == laneCode) || MainLane.motorwayMaintenance == laneCode
  }

  case object MainLane extends LaneNumber {
    def towardsDirection = 11
    def againstDirection = 21
    def motorwayMaintenance: Int = 31
  }

  case object FirstLeftAdditional extends LaneNumber {
    def towardsDirection = 12
    def againstDirection = 22
  }

  case object FirstRightAdditional extends LaneNumber {
    def towardsDirection = 13
    def againstDirection = 23
  }

  case object SecondLeftAdditional extends LaneNumber {
    def towardsDirection = 14
    def againstDirection = 24
  }

  case object SecondRightAdditional extends LaneNumber {
    def towardsDirection = 15
    def againstDirection = 25
  }

  case object ThirdLeftAdditional extends LaneNumber {
    def towardsDirection = 16
    def againstDirection = 26
  }

  case object ThirdRightAdditional extends LaneNumber {
    def towardsDirection = 17
    def againstDirection = 27
  }

  case object FourthLeftAdditional extends LaneNumber {
    def towardsDirection = 18
    def againstDirection = 28
  }

  case object FourthRightAdditional extends LaneNumber {
    def towardsDirection = 19
    def againstDirection = 29
  }

  case object Unknown extends LaneNumber {
    def towardsDirection = 99
    def againstDirection = 99
  }
}


sealed trait LaneNumberOneDigit {
  def laneCode: Int
}

object LaneNumberOneDigit {
  val values = Set(MainLane, FirstLeftAdditional, FirstRightAdditional, SecondLeftAdditional, SecondRightAdditional,
    ThirdLeftAdditional, ThirdRightAdditional, FourthLeftAdditional, FourthRightAdditional, Unknown)

  def apply(value: Int): LaneNumberOneDigit = {
    val valueAsStr = value.toString

    if(valueAsStr.length != 1 ) {
      Unknown

    } else {
      values.find(_.laneCode == value).getOrElse(Unknown)
    }
  }


  def isMainLane (laneCode : Int): Boolean = {
    if (laneCode == 1) true
    else{
      false
    }
  }

  def isValidLaneNumber (laneCode: Int): Boolean = {
    val lanesNumbers = values.filterNot(_ == Unknown)
    lanesNumbers.exists(x => x.laneCode == laneCode)
  }

  case object MainLane extends LaneNumberOneDigit {
    def laneCode = 1

  }

  case object FirstLeftAdditional extends LaneNumberOneDigit {
    def laneCode = 2
  }

  case object FirstRightAdditional extends LaneNumberOneDigit {
    def laneCode = 3
  }

  case object SecondLeftAdditional extends LaneNumberOneDigit {
    def laneCode = 4
  }

  case object SecondRightAdditional extends LaneNumberOneDigit {
    def laneCode = 5
  }

  case object ThirdLeftAdditional extends LaneNumberOneDigit {
    def laneCode = 6
  }

  case object ThirdRightAdditional extends LaneNumberOneDigit {
    def laneCode = 7
  }

  case object FourthLeftAdditional extends LaneNumberOneDigit {
    def laneCode = 8
  }

  case object FourthRightAdditional extends LaneNumberOneDigit {
    def laneCode = 9
  }

  case object Unknown extends LaneNumberOneDigit{
    def laneCode = 99
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

  case object Add extends LaneChangeType { def value = 1; def description = "Lane is added normally";}
  case object Lengthened extends LaneChangeType {def value = 2; def description = "Old lane is deleted and then a new lane is created with more length";}
  case object Shortened extends LaneChangeType {def value = 3; def description = "Old lane is deleted and then a new lane is created with less length";}
  case object Expired extends LaneChangeType {def value = 4; def description = "Lane is expired normally";}
  case object LaneCodeTransfer extends LaneChangeType {def value = 5; def description = "Lane with some code was changed to another code";}
  case object AttributesChanged extends LaneChangeType {def value = 6; def description = "Lane attributes were changed";}
  case object Divided extends LaneChangeType {def value = 7; def description = "Old lane is deleted and then two more appear in same lane code";}
  case object Unknown extends LaneChangeType {def value = 99; def description = "Unknown change to lane";}
}
