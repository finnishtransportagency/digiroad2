package fi.liikennevirasto.digiroad2.client.tierekisteri

import java.text.{ParseException, SimpleDateFormat}
import java.util.Date

import fi.liikennevirasto.digiroad2.service.pointasset.{TrafficSignType, TrafficSignTypeGroup}
import fi.liikennevirasto.digiroad2.util.TierekisteriAuthPropertyReader
import org.apache.http.HttpStatus
import org.apache.http.client.methods.{HttpRequestBase, _}
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.CloseableHttpClient
import org.json4s.jackson.JsonMethods._
import org.json4s.{DefaultFormats, Formats, StreamInput}
import org.slf4j.LoggerFactory

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

/**
  * Values for traffic sign types enumeration
  */
sealed trait TRTrafficSignType {
  def value: Int
  def trafficSignType: TrafficSignType
}
object TRTrafficSignType {
  val values = Set(SpeedLimit, EndSpeedLimit, SpeedLimitZone, EndSpeedLimitZone, UrbanArea, EndUrbanArea, PedestrianCrossing, PedestrianCrossing, MaximumLength, Warning, NoLeftTurn, NoRightTurn, NoUTurn,
    ClosedToAllVehicles, NoPowerDrivenVehicles, NoLorriesAndVans, NoVehicleCombinations, NoAgriculturalVehicles, NoMotorCycles, NoMotorSledges, NoVehiclesWithDangerGoods,
    NoBuses, NoMopeds, NoCyclesOrMopeds, NoPedestrians, NoPedestriansCyclesMopeds, NoRidersOnHorseback, NoEntry, OvertakingProhibited, EndProhibitionOfOvertaking,
    MaxWidthExceeding, MaxHeightExceeding, MaxLadenExceeding, MaxMassCombineVehiclesExceeding, MaxTonsOneAxleExceeding, MaxTonsOnBogieExceeding, WRightBend, WLeftBend,
    WSeveralBendsRight, WSeveralBendsLeft, WDangerousDescent, WSteepAscent, WUnevenRoad, WChildren, TelematicSpeedLimit)

  def apply(value: Int): TRTrafficSignType = {
    values.find(_.value == value).getOrElse(Unknown)
  }
  case object TelematicSpeedLimit extends TRTrafficSignType { def value = 0;  def trafficSignType = TrafficSignType.TelematicSpeedLimit; def group = TrafficSignTypeGroup.SpeedLimits; }
  case object SpeedLimit extends TRTrafficSignType { def value = 361;  def trafficSignType = TrafficSignType.SpeedLimit; def group = TrafficSignTypeGroup.SpeedLimits; }
  case object EndSpeedLimit extends TRTrafficSignType { def value = 362;  def trafficSignType = TrafficSignType.EndSpeedLimit; def group = TrafficSignTypeGroup.SpeedLimits; }
  case object SpeedLimitZone extends TRTrafficSignType { def value = 363;  def trafficSignType = TrafficSignType.SpeedLimitZone; def group = TrafficSignTypeGroup.SpeedLimits;}
  case object EndSpeedLimitZone extends TRTrafficSignType { def value = 364;  def trafficSignType = TrafficSignType.EndSpeedLimitZone; def group = TrafficSignTypeGroup.SpeedLimits; }
  case object UrbanArea extends TRTrafficSignType { def value = 571;  def trafficSignType = TrafficSignType.UrbanArea; def group = TrafficSignTypeGroup.SpeedLimits; }
  case object EndUrbanArea extends TRTrafficSignType { def value = 572;  def trafficSignType = TrafficSignType.EndUrbanArea; def group = TrafficSignTypeGroup.SpeedLimits; }
  case object PedestrianCrossing extends TRTrafficSignType { def value = 511;  def trafficSignType = TrafficSignType.PedestrianCrossing; def group = TrafficSignTypeGroup.PedestrianCrossing; }
  case object MaximumLength extends TRTrafficSignType { def value = 343;  def trafficSignType = TrafficSignType.MaximumLength; def group = TrafficSignTypeGroup.MaximumRestrictions;  }
  case object Warning extends TRTrafficSignType { def value = 189;  def trafficSignType = TrafficSignType.Warning; def group = TrafficSignTypeGroup.GeneralWarningSigns; }
  case object NoLeftTurn extends TRTrafficSignType { def value = 332;  def trafficSignType = TrafficSignType.NoLeftTurn; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object NoRightTurn extends TRTrafficSignType { def value = 333;  def trafficSignType = TrafficSignType.NoRightTurn; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object NoUTurn extends TRTrafficSignType { def value = 334;  def trafficSignType = TrafficSignType.NoUTurn; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object ClosedToAllVehicles extends TRTrafficSignType { def value = 311;  def trafficSignType = TrafficSignType.ClosedToAllVehicles; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object NoPowerDrivenVehicles extends TRTrafficSignType { def value = 312;  def trafficSignType = TrafficSignType.NoPowerDrivenVehicles; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object NoLorriesAndVans extends TRTrafficSignType { def value = 313;  def trafficSignType = TrafficSignType.NoLorriesAndVans; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object NoVehicleCombinations extends TRTrafficSignType { def value = 314;  def trafficSignType = TrafficSignType.NoVehicleCombinations; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object NoAgriculturalVehicles extends TRTrafficSignType { def value = 315;  def trafficSignType = TrafficSignType.NoAgriculturalVehicles; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object NoMotorCycles extends TRTrafficSignType { def value = 316;  def trafficSignType = TrafficSignType.NoMotorCycles; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object NoMotorSledges extends TRTrafficSignType { def value = 317;  def trafficSignType = TrafficSignType.NoMotorSledges; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object NoVehiclesWithDangerGoods extends TRTrafficSignType { def value = 318;  def trafficSignType = TrafficSignType.NoVehiclesWithDangerGoods; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object NoBuses extends TRTrafficSignType { def value = 319;  def trafficSignType = TrafficSignType.NoBuses; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object NoMopeds extends TRTrafficSignType { def value = 321;  def trafficSignType = TrafficSignType.NoMopeds; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object NoCyclesOrMopeds extends TRTrafficSignType { def value = 322;  def trafficSignType = TrafficSignType.NoCyclesOrMopeds; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object NoPedestrians extends TRTrafficSignType { def value = 323;  def trafficSignType = TrafficSignType.NoPedestrians; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object NoPedestriansCyclesMopeds extends TRTrafficSignType { def value = 324;  def trafficSignType = TrafficSignType.NoPedestriansCyclesMopeds; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object NoRidersOnHorseback extends TRTrafficSignType { def value = 325;  def trafficSignType = TrafficSignType.NoRidersOnHorseback; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object NoEntry extends TRTrafficSignType { def value = 331;  def trafficSignType = TrafficSignType.NoEntry; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object OvertakingProhibited extends TRTrafficSignType { def value = 351;  def trafficSignType = TrafficSignType.OvertakingProhibited; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object EndProhibitionOfOvertaking extends TRTrafficSignType { def value = 352;  def trafficSignType = TrafficSignType.EndProhibitionOfOvertaking; def group = TrafficSignTypeGroup.ProhibitionsAndRestrictions; }
  case object MaxWidthExceeding extends TRTrafficSignType { def value = 341;  def trafficSignType = TrafficSignType.NoWidthExceeding; def group = TrafficSignTypeGroup.MaximumRestrictions;  }
  case object MaxHeightExceeding extends TRTrafficSignType { def value = 342;  def trafficSignType = TrafficSignType.MaxHeightExceeding; def group = TrafficSignTypeGroup.MaximumRestrictions;  }
  case object MaxLadenExceeding extends TRTrafficSignType { def value = 344;  def trafficSignType = TrafficSignType.MaxLadenExceeding; def group = TrafficSignTypeGroup.MaximumRestrictions;  }
  case object MaxMassCombineVehiclesExceeding extends TRTrafficSignType { def value = 345;  def trafficSignType = TrafficSignType.MaxMassCombineVehiclesExceeding; def group = TrafficSignTypeGroup.MaximumRestrictions;  }
  case object MaxTonsOneAxleExceeding extends TRTrafficSignType { def value = 346;  def trafficSignType = TrafficSignType.MaxTonsOneAxleExceeding; def group = TrafficSignTypeGroup.MaximumRestrictions;  }
  case object MaxTonsOnBogieExceeding extends TRTrafficSignType { def value = 347;  def trafficSignType = TrafficSignType.MaxTonsOnBogieExceeding; def group = TrafficSignTypeGroup.MaximumRestrictions;  }
  case object WRightBend extends TRTrafficSignType { def value = 111;  def trafficSignType = TrafficSignType.WRightBend; def group = TrafficSignTypeGroup.GeneralWarningSigns; }
  case object WLeftBend extends TRTrafficSignType { def value = 112;  def trafficSignType = TrafficSignType.WLeftBend; def group = TrafficSignTypeGroup.GeneralWarningSigns; }
  case object WSeveralBendsRight extends TRTrafficSignType { def value = 113;  def trafficSignType = TrafficSignType.WSeveralBendsRight; def group = TrafficSignTypeGroup.GeneralWarningSigns; }
  case object WSeveralBendsLeft extends TRTrafficSignType { def value = 114;  def trafficSignType = TrafficSignType.WSeveralBendsLeft; def group = TrafficSignTypeGroup.GeneralWarningSigns; }
  case object WDangerousDescent extends TRTrafficSignType { def value = 115;  def trafficSignType = TrafficSignType.WDangerousDescent; def group = TrafficSignTypeGroup.GeneralWarningSigns; }
  case object WSteepAscent extends TRTrafficSignType { def value = 116;  def trafficSignType = TrafficSignType.WSteepAscent; def group = TrafficSignTypeGroup.GeneralWarningSigns; }
  case object WUnevenRoad extends TRTrafficSignType { def value = 141;  def trafficSignType = TrafficSignType.WUnevenRoad; def group = TrafficSignTypeGroup.GeneralWarningSigns; }
  case object WChildren extends TRTrafficSignType { def value = 152;  def trafficSignType = TrafficSignType.WChildren; def group = TrafficSignTypeGroup.GeneralWarningSigns; }
  case object Unknown extends TRTrafficSignType { def value = 999999;  def trafficSignType = TrafficSignType.Unknown; def group = TrafficSignTypeGroup.Unknown; }
}

/**
  * Values for PavementRoad types enumeration
  */
sealed trait TRPavedRoadType {
  def value: Int
  def pavedRoadType: String
}
object TRPavedRoadType {
  val values = Set(CementConcrete, Cobblestone, HardAsphalt, SoftAsphalt)

  def apply(value: Int): TRPavedRoadType = {
    values.find(_.value == value).getOrElse(Unknown)
  }

  case object CementConcrete extends TRPavedRoadType { def value = 1; def pavedRoadType = "Cement Concrete";}
  case object Cobblestone extends TRPavedRoadType { def value = 2; def pavedRoadType = "Cobblestone";}
  case object HardAsphalt extends TRPavedRoadType { def value = 10; def pavedRoadType = "Hard Asphalt";}
  case object SoftAsphalt extends TRPavedRoadType { def value = 20; def pavedRoadType = "Soft Asphalt";}
  case object Unknown extends TRPavedRoadType { def value = 99;  def pavedRoadType = "Unknown";}
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
  case object Unknown extends TRFrostHeavingFactorType { def value = 999;  def pavedRoadType = "Unknown";}
}

case class TierekisteriError(content: Map[String, Any], url: String)

class TierekisteriClientException(response: String) extends RuntimeException(response)

class TierekisteriClientWarnings(response: String) extends RuntimeException(response)

trait TierekisteriClient{

  def tierekisteriRestApiEndPoint: String
  def tierekisteriEnabled: Boolean
  def client: CloseableHttpClient

  type TierekisteriType

  protected implicit val jsonFormats: Formats = DefaultFormats
  protected val dateFormat = "yyyy-MM-dd"
  protected val auth = new TierekisteriAuthPropertyReader
  protected lazy val logger = LoggerFactory.getLogger(getClass)

  def mapFields(data: Map[String, Any]): Option[TierekisteriType]

  def addAuthorizationHeader(request: HttpRequestBase) = {
    request.addHeader("X-OTH-Authorization", "Basic " + auth.getOldAuthInBase64)
    request.addHeader("X-Authorization", "Basic " + auth.getAuthInBase64)

  }
  protected def request[T](url: String): Either[T, TierekisteriError] = {
    val request = new HttpGet(url)
    addAuthorizationHeader(request)
    val response = client.execute(request)
    try {
      val statusCode = response.getStatusLine.getStatusCode
      if (statusCode == HttpStatus.SC_NOT_FOUND) {
        return Right(null)
      } else if (statusCode >= HttpStatus.SC_BAD_REQUEST) {
        return Right(TierekisteriError(Map("error" -> ErrorMessageConverter.convertJSONToError(response), "content" -> response.getEntity.getContent), url))
      }
      Left(parse(StreamInput(response.getEntity.getContent)).values.asInstanceOf[T])
    } catch {
      case e: Exception => Right(TierekisteriError(Map("error" -> e.getMessage, "content" -> response.getEntity.getContent), url))
    } finally {
      response.close()
    }
  }

  protected def post(url: String, trEntity: TierekisteriType, createJson: (TierekisteriType) => StringEntity): Option[TierekisteriError] = {
    val request = new HttpPost(url)
    addAuthorizationHeader(request)
    request.setEntity(createJson(trEntity))
    val response = client.execute(request)
    try {
      val statusCode = response.getStatusLine.getStatusCode
      val reason = response.getStatusLine.getReasonPhrase
      if (statusCode >= HttpStatus.SC_BAD_REQUEST) {
        logger.warn("Tierekisteri error: " + url + " " + statusCode + " " + reason)
        val error = ErrorMessageConverter.convertJSONToError(response)
        logger.warn("Json from Tierekisteri: " + error)
        return Some(TierekisteriError(Map("error" -> error), url))
      }
      None
    } catch {
      case e: Exception => Some(TierekisteriError(Map("error" -> e.getMessage), url))
    } finally {
      response.close()
    }
  }

  protected def put(url: String, trEntity: TierekisteriType, createJson: (TierekisteriType) => StringEntity): Option[TierekisteriError] = {
    val request = new HttpPut(url)
    addAuthorizationHeader(request)
    request.setEntity(createJson(trEntity))
    val response = client.execute(request)
    try {
      val statusCode = response.getStatusLine.getStatusCode
      val reason = response.getStatusLine.getReasonPhrase
      if (statusCode >= HttpStatus.SC_BAD_REQUEST) {
        logger.warn("Tierekisteri error: " + url + " " + statusCode + " " + reason)
        val error = ErrorMessageConverter.convertJSONToError(response)
        logger.warn("Json from Tierekisteri: " + error)
        return Some(TierekisteriError(Map("error" -> error), url))
      }
      None
    } catch {
      case e: Exception => Some(TierekisteriError(Map("error" -> e.getMessage), url))
    } finally {
      response.close()
    }
  }

  protected def delete(url: String): Option[TierekisteriError] = {
    val request = new HttpDelete(url)
    request.setHeader("content-type","application/json")
    addAuthorizationHeader(request)
    val response = client.execute(request)
    try {
      val statusCode = response.getStatusLine.getStatusCode
      val reason = response.getStatusLine.getReasonPhrase
      if (statusCode >= HttpStatus.SC_BAD_REQUEST) {
        logger.warn("Tierekisteri error: " + url + " " + statusCode + " " + reason)
        val error = ErrorMessageConverter.convertJSONToError(response)
        logger.warn("Json from Tierekisteri: " + error)
        return Some(TierekisteriError(Map("error" -> error), url))
      }
      None
    } catch {
      case e: Exception => Some(TierekisteriError(Map("error" -> e.getMessage), url))
    } finally {
      response.close()
    }
  }

  protected def convertToLong(value: Option[String]): Option[Long] = {
    try {
      value.map(_.toLong)
    } catch {
      case e: NumberFormatException =>
        throw new TierekisteriClientException("Invalid value in response: Long expected, got '%s'".format(value))
    }
  }

  protected def convertToDouble(value: Option[String]): Option[Double] = {
    try {
      value.map(_.toDouble)
    } catch {
      case e: NumberFormatException =>
        throw new TierekisteriClientException("Invalid value in response: Double expected, got '%s'".format(value))
    }
  }

  protected def convertToInt(value: Option[String]): Option[Int] = {
    try {
      value.map(_.toInt)
    } catch {
      case e: NumberFormatException =>
        throw new TierekisteriClientException("Invalid value in response: Int expected, got '%s'".format(value))
    }
  }

  protected def convertToDate(value: Option[String]): Option[Date] = {
    try {
      value.map(dv => new SimpleDateFormat(dateFormat).parse(dv))
    } catch {
      case e: ParseException =>
        throw new TierekisteriClientException("Invalid value in response: Date expected, got '%s'".format(value))
    }
  }

  protected def convertDateToString(date: Option[Date]): Option[String] = {
    date.map(dv => convertDateToString(dv))
  }

  protected def convertDateToString(date: Date): String = {
    new SimpleDateFormat(dateFormat).format(date)
  }

  protected def getFieldValue(data: Map[String, Any], field: String): Option[String] = {
    try {
      data.get(field).map(_.toString) match {
        case Some(value) => Some(value)
        case _ => None
      }
    } catch {
      case ex: NullPointerException => None
    }
  }
  protected def getMandatoryFieldValue(data: Map[String, Any], field: String): Option[String] = {
    val fieldValue = getFieldValue(data, field)
    if (fieldValue.isEmpty)
      throw new TierekisteriClientException("Missing mandatory field in response '%s'".format(field))
    fieldValue
  }
}

object ErrorMessageConverter {
  protected implicit val jsonFormats: Formats = DefaultFormats

  def convertJSONToError(response: CloseableHttpResponse) = {
    def inputToMap(json: StreamInput): Map[String, String] = {
      try {
        parse(json).values.asInstanceOf[Map[String, String]]
      } catch {
        case e: Exception => Map()
      }
    }
    def errorMessageFormat = "%d: %s"
    val message = inputToMap(StreamInput(response.getEntity.getContent)).getOrElse("message", "N/A")
    response.getStatusLine.getStatusCode match {
      case HttpStatus.SC_BAD_REQUEST => errorMessageFormat.format(HttpStatus.SC_BAD_REQUEST, message)
      case HttpStatus.SC_LOCKED => errorMessageFormat.format(HttpStatus.SC_LOCKED, message)
      case HttpStatus.SC_CONFLICT => errorMessageFormat.format(HttpStatus.SC_CONFLICT, message)
      case HttpStatus.SC_INTERNAL_SERVER_ERROR => errorMessageFormat.format(HttpStatus.SC_INTERNAL_SERVER_ERROR, message)
      case HttpStatus.SC_NOT_FOUND => errorMessageFormat.format(HttpStatus.SC_NOT_FOUND, message)
      case _ => "Unspecified error: %s".format(message)
    }
  }
}
