package fi.liikennevirasto.digiroad2.lane

import fi.liikennevirasto.digiroad2.Point
import fi.liikennevirasto.digiroad2.asset.AdministrativeClass
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

case class NewLane ( linkId: Long, startMeasure: Double, endMeasure: Double, value: LanePropertyValue, sideCode: Int,
                          vvhTimeStamp: Long, geomModifiedDate: Option[DateTime] )

case class NewIncomeLane ( id: Long, startMeasure: Double, endMeasure: Double, municipalityCode : Long,
                           isExpired: Boolean = false, isDeleted: Boolean = false, properties: Seq[LaneProperty] )

sealed trait LaneValue {
  def toJson: Any
}

case class LanePropertyValue(value: Any)
case class LaneProperty(publicId: String,  values: Seq[LanePropertyValue])

case class LaneRoadAddressInfo ( roadNumber: Long, initialRoadPartNumber: Long, initialDistance: Long,
                                 endRoadPartNumber: Long, endDistance: Long, track: Int )

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
                  Reversible, Combined, Walking, Cycling, Unknown)

  def apply(value: Int): LaneType = {
    values.find(_.value == value).getOrElse(Unknown)
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
  case object Combined extends LaneType { def value = 20; def typeDescription = "Combined walking and cycling lane"; def finnishDescription = "Yhdistetty jalankulun ja pyöräilyn kaista"; }
  case object Walking extends LaneType { def value = 21; def typeDescription = "Walking lane"; def finnishDescription = "Jalankulun kaista"; }
  case object Cycling extends LaneType { def value = 22; def typeDescription = "Cycling lane"; def finnishDescription = "Pyöräilykaista"; }
  case object Unknown extends LaneType { def value = 99;  def typeDescription = "Unknown"; def finnishDescription = "Tuntematon"; }
}

/**
  * Values for lane continuity
  */
sealed trait LaneContinuity {
  def value: Int
  def typeDescription: String
  def finnishDescription: String
}
object LaneContinuity {
  val values = Set(Continuous, ContinuesOtherNumber, Turns, Ends, ContinuousTurningRight, ContinuousTurningLeft, Unknown)

  def apply(value: Int): LaneContinuity = {
    values.find(_.value == value).getOrElse(Unknown)
  }

  case object Continuous extends LaneContinuity { def value = 1; def typeDescription = "Continuous lane"; def finnishDescription = "Jatkuva "; }
  case object ContinuesOtherNumber extends LaneContinuity { def value = 2; def typeDescription = "Lane continues with other lane number"; def finnishDescription = "Jatkuu toisella kaistanumerolla"; }
  case object Turns extends LaneContinuity { def value = 3; def typeDescription = "Lane turns"; def finnishDescription = "Kääntyvä"; }
  case object Ends extends LaneContinuity { def value = 4; def typeDescription = "Lane ends"; def finnishDescription = "Päättyvä"; }
  case object ContinuousTurningRight extends LaneContinuity { def value = 5; def typeDescription = "Continuous and turning right possible"; def finnishDescription = "Jatkuva, osoitettu myös oikealle kääntyville"; }
  case object ContinuousTurningLeft extends LaneContinuity { def value = 6; def typeDescription = "Continuous and turning left possible"; def finnishDescription = "Jatkuva, osoitettu myös vasemmalle kääntyville"; }
  case object Unknown extends LaneContinuity { def value = 99;  def typeDescription = "Unknown"; def finnishDescription = "Tuntematon"; }
}

/**
  * Values for changeType of lanes
  */
sealed trait LaneChangeType {
  def value: Int
  def description: String
}
object LaneChangeType {
  val values = Set(Add, Lengthened, Shortened, Expired, Unknown)

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