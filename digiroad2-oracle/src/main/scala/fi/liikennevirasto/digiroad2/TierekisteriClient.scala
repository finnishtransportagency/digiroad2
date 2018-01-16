package fi.liikennevirasto.digiroad2

import java.text.{ParseException, SimpleDateFormat}
import java.util.Date

import fi.liikennevirasto.digiroad2.asset.{Property, PropertyValue}
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.Queries
import fi.liikennevirasto.digiroad2.util.{RoadAddress, RoadSide, TierekisteriAuthPropertyReader, Track}
import org.apache.http.HttpStatus
import org.apache.http.client.methods.{HttpRequestBase, _}
import org.apache.http.entity.{ContentType, StringEntity}
import org.apache.http.impl.client.CloseableHttpClient
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization
import org.json4s.{DefaultFormats, Formats, StreamInput}
import org.slf4j.LoggerFactory

/**
  * Values for Stop type (Pysäkin tyyppi) enumeration
  */
sealed trait StopType {
  def value: String
  def propertyValues: Set[Int]
}
object StopType {
  val values = Set[StopType](Commuter, LongDistance, Combined, Virtual, Unknown)

  def apply(value: String): StopType = {
    values.find(_.value == value).getOrElse(Unknown)
  }

  def propertyValues() : Set[Int] = {
    values.flatMap(_.propertyValues)
  }

  case object Commuter extends StopType { def value = "paikallis"; def propertyValues = Set(2); }
  case object LongDistance extends StopType { def value = "kauko"; def propertyValues = Set(3); }
  case object Combined extends StopType { def value = "molemmat"; def propertyValues = Set(2,3); }
  case object Virtual extends StopType { def value = "virtuaali"; def propertyValues = Set(5); }
  case object Unknown extends StopType { def value = "tuntematon"; def propertyValues = Set(99); }  // Should not be passed on interface
}

/**
  * Values for Existence (Olemassaolo) enumeration
  */
sealed trait Existence {
  def value: String
  def propertyValue: Int
}
object Existence {
  val values = Set(Yes, No, Unknown)

  def apply(value: String): Existence = {
    values.find(_.value == value).getOrElse(Unknown)
  }

  def fromPropertyValue(value: String): Existence = {
    value match {
      case "1" => No
      case "2" => Yes
      case _ => Unknown
    }
  }

  case object Yes extends Existence { def value = "on"; def propertyValue = 2; }
  case object No extends Existence { def value = "ei"; def propertyValue = 1; }
  case object Unknown extends Existence { def value = "ei_tietoa"; def propertyValue = 99; }
}

/**
  * Values for Equipment (Varuste) enumeration
  */
sealed trait Equipment {
  def value: String
  def publicId: String
  def isMaster: Boolean
}
object Equipment {
  val values = Set[Equipment](Timetable, TrashBin, BikeStand, Lighting, Seat, Roof, RoofMaintainedByAdvertiser, ElectronicTimetables, CarParkForTakingPassengers, RaisedBusStop)

  def apply(value: String): Equipment = {
    values.find(_.value == value).getOrElse(Unknown)
  }

  def fromPublicId(value: String): Equipment = {
    values.find(_.publicId == value).getOrElse(Unknown)
  }

  case object Timetable extends Equipment { def value = "aikataulu"; def publicId = "aikataulu"; def isMaster = true; }
  case object TrashBin extends Equipment { def value = "roskis"; def publicId = "roska_astia"; def isMaster = true; }
  case object BikeStand extends Equipment { def value = "pyorateline"; def publicId = "pyorateline"; def isMaster = true; }
  case object Lighting extends Equipment { def value = "valaistus"; def publicId = "valaistus"; def isMaster = true; }
  case object Seat extends Equipment { def value = "penkki"; def publicId = "penkki"; def isMaster = true; }
  case object Roof extends Equipment { def value = "katos"; def publicId = "katos"; def isMaster = false; }
  case object RoofMaintainedByAdvertiser extends Equipment { def value = "mainoskatos"; def publicId = "mainoskatos"; def isMaster = false; }
  case object ElectronicTimetables extends Equipment { def value = "sahk_aikataulu"; def publicId = "sahkoinen_aikataulunaytto"; def isMaster = false; }
  case object CarParkForTakingPassengers extends Equipment { def value = "saattomahd"; def publicId = "saattomahdollisuus_henkiloautolla"; def isMaster = false; }
  case object RaisedBusStop extends Equipment { def value = "korotus"; def publicId = "korotettu"; def isMaster = false; }
  case object Unknown extends Equipment { def value = "UNKNOWN"; def publicId = "tuntematon"; def isMaster = false; }
}

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
  case object TelematicSpeedLimit extends TRTrafficSignType { def value = 0;  def trafficSignType = TrafficSignType.SpeedLimit; }
  case object SpeedLimit extends TRTrafficSignType { def value = 361;  def trafficSignType = TrafficSignType.SpeedLimit; }
  case object EndSpeedLimit extends TRTrafficSignType { def value = 362;  def trafficSignType = TrafficSignType.EndSpeedLimit; }
  case object SpeedLimitZone extends TRTrafficSignType { def value = 363;  def trafficSignType = TrafficSignType.SpeedLimitZone; }
  case object EndSpeedLimitZone extends TRTrafficSignType { def value = 364;  def trafficSignType = TrafficSignType.EndSpeedLimitZone; }
  case object UrbanArea extends TRTrafficSignType { def value = 571;  def trafficSignType = TrafficSignType.UrbanArea; }
  case object EndUrbanArea extends TRTrafficSignType { def value = 572;  def trafficSignType = TrafficSignType.EndUrbanArea; }
  case object PedestrianCrossing extends TRTrafficSignType { def value = 511;  def trafficSignType = TrafficSignType.PedestrianCrossing; }
  case object MaximumLength extends TRTrafficSignType { def value = 343;  def trafficSignType = TrafficSignType.MaximumLength; }
  case object Warning extends TRTrafficSignType { def value = 189;  def trafficSignType = TrafficSignType.Warning; }
  case object NoLeftTurn extends TRTrafficSignType { def value = 332;  def trafficSignType = TrafficSignType.NoLeftTurn; }
  case object NoRightTurn extends TRTrafficSignType { def value = 333;  def trafficSignType = TrafficSignType.NoRightTurn; }
  case object NoUTurn extends TRTrafficSignType { def value = 334;  def trafficSignType = TrafficSignType.NoUTurn; }
  case object ClosedToAllVehicles extends TRTrafficSignType { def value = 311;  def trafficSignType = TrafficSignType.ClosedToAllVehicles; }
  case object NoPowerDrivenVehicles extends TRTrafficSignType { def value = 312;  def trafficSignType = TrafficSignType.NoPowerDrivenVehicles; }
  case object NoLorriesAndVans extends TRTrafficSignType { def value = 313;  def trafficSignType = TrafficSignType.NoLorriesAndVans; }
  case object NoVehicleCombinations extends TRTrafficSignType { def value = 314;  def trafficSignType = TrafficSignType.NoVehicleCombinations; }
  case object NoAgriculturalVehicles extends TRTrafficSignType { def value = 315;  def trafficSignType = TrafficSignType.NoAgriculturalVehicles; }
  case object NoMotorCycles extends TRTrafficSignType { def value = 316;  def trafficSignType = TrafficSignType.NoMotorCycles; }
  case object NoMotorSledges extends TRTrafficSignType { def value = 317;  def trafficSignType = TrafficSignType.NoMotorSledges; }
  case object NoVehiclesWithDangerGoods extends TRTrafficSignType { def value = 318;  def trafficSignType = TrafficSignType.NoVehiclesWithDangerGoods; }
  case object NoBuses extends TRTrafficSignType { def value = 319;  def trafficSignType = TrafficSignType.NoBuses; }
  case object NoMopeds extends TRTrafficSignType { def value = 321;  def trafficSignType = TrafficSignType.NoMopeds; }
  case object NoCyclesOrMopeds extends TRTrafficSignType { def value = 322;  def trafficSignType = TrafficSignType.NoCyclesOrMopeds; }
  case object NoPedestrians extends TRTrafficSignType { def value = 323;  def trafficSignType = TrafficSignType.NoPedestrians; }
  case object NoPedestriansCyclesMopeds extends TRTrafficSignType { def value = 324;  def trafficSignType = TrafficSignType.NoPedestriansCyclesMopeds; }
  case object NoRidersOnHorseback extends TRTrafficSignType { def value = 325;  def trafficSignType = TrafficSignType.NoRidersOnHorseback; }
  case object NoEntry extends TRTrafficSignType { def value = 331;  def trafficSignType = TrafficSignType.NoEntry; }
  case object OvertakingProhibited extends TRTrafficSignType { def value = 351;  def trafficSignType = TrafficSignType.OvertakingProhibited; }
  case object EndProhibitionOfOvertaking extends TRTrafficSignType { def value = 352;  def trafficSignType = TrafficSignType.EndProhibitionOfOvertaking; }
  case object MaxWidthExceeding extends TRTrafficSignType { def value = 341;  def trafficSignType = TrafficSignType.NoWidthExceeding; }
  case object MaxHeightExceeding extends TRTrafficSignType { def value = 342;  def trafficSignType = TrafficSignType.MaxHeightExceeding; }
  case object MaxLadenExceeding extends TRTrafficSignType { def value = 344;  def trafficSignType = TrafficSignType.MaxLadenExceeding; }
  case object MaxMassCombineVehiclesExceeding extends TRTrafficSignType { def value = 345;  def trafficSignType = TrafficSignType.MaxMassCombineVehiclesExceeding; }
  case object MaxTonsOneAxleExceeding extends TRTrafficSignType { def value = 346;  def trafficSignType = TrafficSignType.MaxTonsOneAxleExceeding; }
  case object MaxTonsOnBogieExceeding extends TRTrafficSignType { def value = 347;  def trafficSignType = TrafficSignType.MaxTonsOnBogieExceeding; }
  case object WRightBend extends TRTrafficSignType { def value = 111;  def trafficSignType = TrafficSignType.WRightBend; }
  case object WLeftBend extends TRTrafficSignType { def value = 112;  def trafficSignType = TrafficSignType.WLeftBend; }
  case object WSeveralBendsRight extends TRTrafficSignType { def value = 113;  def trafficSignType = TrafficSignType.WSeveralBendsRight ; }
  case object WSeveralBendsLeft extends TRTrafficSignType { def value = 114;  def trafficSignType = TrafficSignType.WSeveralBendsLeft; }
  case object WDangerousDescent extends TRTrafficSignType { def value = 115;  def trafficSignType = TrafficSignType.WDangerousDescent; }
  case object WSteepAscent extends TRTrafficSignType { def value = 116;  def trafficSignType = TrafficSignType.WSteepAscent; }
  case object WUnevenRoad extends TRTrafficSignType { def value = 141;  def trafficSignType = TrafficSignType.WUnevenRoad; }
  case object WChildren extends TRTrafficSignType { def value = 152;  def trafficSignType = TrafficSignType.WChildren; }
  case object Unknown extends TRTrafficSignType { def value = 999999;  def trafficSignType = TrafficSignType.Unknown; }
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

case class TierekisteriMassTransitStop(nationalId: Long,
                                       liviId: String,
                                       roadAddress: RoadAddress,
                                       roadSide: TRRoadSide,
                                       stopType: StopType,
                                       express: Boolean,
                                       equipments: Map[Equipment, Existence] = Map(),
                                       stopCode: Option[String],
                                       nameFi: Option[String],
                                       nameSe: Option[String],
                                       modifiedBy: String,
                                       operatingFrom: Option[Date],
                                       operatingTo: Option[Date],
                                       removalDate: Option[Date],
                                       inventoryDate: Date)

trait TierekisteriAssetData {
  val roadNumber: Long
  val startRoadPartNumber: Long
  val endRoadPartNumber: Long
  val startAddressMValue: Long
  val endAddressMValue: Long
  val track: Track
}


case class TierekisteriTrafficData(roadNumber: Long, startRoadPartNumber: Long, endRoadPartNumber: Long,
                                   track: Track, startAddressMValue: Long, endAddressMValue: Long, assetValue: Int) extends TierekisteriAssetData

case class TierekisteriRoadWidthData(roadNumber: Long, startRoadPartNumber: Long, endRoadPartNumber: Long,
                                     track: Track, startAddressMValue: Long, endAddressMValue: Long, assetValue: Int) extends TierekisteriAssetData

case class TierekisteriLightingData(roadNumber: Long, startRoadPartNumber: Long, endRoadPartNumber: Long,
                                track: Track, startAddressMValue: Long, endAddressMValue: Long) extends TierekisteriAssetData

case class TierekisteriTrafficSignData(roadNumber: Long, startRoadPartNumber: Long, endRoadPartNumber: Long,
                                    track: Track, startAddressMValue: Long, endAddressMValue: Long, roadSide: RoadSide, assetType: TRTrafficSignType, assetValue: String, liikvast: Option[Int] = None) extends TierekisteriAssetData

case class TierekisteriPavedRoadData(roadNumber: Long, startRoadPartNumber: Long, endRoadPartNumber: Long,
                                    track: Track, startAddressMValue: Long, endAddressMValue: Long, pavementType: TRPavedRoadType) extends TierekisteriAssetData

case class TierekisteriMassTransitLaneData(roadNumber: Long, startRoadPartNumber: Long, endRoadPartNumber: Long,
                                     track: Track, startAddressMValue: Long, endAddressMValue: Long, laneType: TRLaneArrangementType) extends TierekisteriAssetData

case class TierekisteriDamagedByThawData(roadNumber: Long, startRoadPartNumber: Long, endRoadPartNumber: Long,
                                           track: Track, startAddressMValue: Long, endAddressMValue: Long) extends TierekisteriAssetData

case class TierekisteriEuropeanRoadData(roadNumber: Long, startRoadPartNumber: Long, endRoadPartNumber: Long,
                                     track: Track, startAddressMValue: Long, endAddressMValue: Long, assetValue: String) extends TierekisteriAssetData

case class TierekisteriSpeedLimitData(roadNumber: Long, startRoadPartNumber: Long, endRoadPartNumber: Long,
                                      track: Track, startAddressMValue: Long, endAddressMValue: Long, assetValue: Int, roadSide: RoadSide) extends TierekisteriAssetData

case class TierekisteriUrbanAreaData(roadNumber: Long, startRoadPartNumber: Long, endRoadPartNumber: Long,
                                     track: Track, startAddressMValue: Long, endAddressMValue: Long, assetValue: String) extends TierekisteriAssetData

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

  def mapFields(data: Map[String, Any]): TierekisteriType

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

class TierekisteriMassTransitStopClient(trEndPoint: String, trEnabled: Boolean, httpClient: CloseableHttpClient) extends TierekisteriClient{

  override def tierekisteriRestApiEndPoint: String = trEndPoint
  override def tierekisteriEnabled: Boolean = trEnabled
  override def client: CloseableHttpClient = httpClient
  type TierekisteriType = TierekisteriMassTransitStop

  private val serviceName = "pysakit/"

  private val trNationalId = "valtakunnallinen_id"
  private val trRoadNumber = "tie"        // tienumero
  private val trRoadPartNumber = "aosa"   // tieosanumero
  private val trLane = "ajr"              // ajorata
  private val trDistance = "aet"          // etaisyys
  private val trSide = "puoli"
  private val trStopCode = "pysakin_tunnus"
  private val trNameFi = "nimi_fi"
  private val trStopType = "pysakin_tyyppi"
  private val trIsExpress = "pikavuoro"
  private val trOperatingFrom = "alkupvm"
  private val trOperatingTo = "loppupvm"
  private val trRemovalDate = "lakkautuspvm"
  private val trLiviId = "livitunnus"
  private val trNameSe = "nimi_se"
  private val trEquipment = "varusteet"
  private val trUser = "kayttajatunnus"
  private val trInventoryDate = "inventointipvm"
  private val serviceUrl : String = tierekisteriRestApiEndPoint + serviceName

  private def serviceUrl(id: String) : String = serviceUrl + id
  private def serviceUrl(replaceIdOption: Option[String]) : String = {
    replaceIdOption match {
      case Some(replaceId) => serviceUrl + "?replace=" + replaceId
      case _ => serviceUrl
    }
  }

  private def booleanCodeToBoolean: Map[String, Boolean] = Map("on" -> true, "ei" -> false)
  private def booleanToBooleanCode: Map[Boolean, String] = Map(true -> "on", false -> "ei")


  private val toIso8601 = DateTimeFormat.forPattern("yyyy-MM-dd")

  /**
    * Return all bus stops currently active from Tierekisteri
    * Tierekisteri REST API endpoint: GET /pysakit/
    *
    * @return
    */
  def fetchActiveMassTransitStops(): Seq[TierekisteriMassTransitStop] = {
    request[List[Map[String, Any]]](serviceUrl) match {
      case Left(content) =>
        content.map{
          stopAsset =>
            mapFields(stopAsset)
        }
      case Right(error) => throw new TierekisteriClientException("Tierekisteri error: " + error.content.get("error").get.toString)
    }
  }

  /**
    * Returns the anwser to the question "Is Tierekisteri Enabled?".
    *
    * @return Type: Boolean - If TR client is enabled
    */
  def isTREnabled : Boolean = {
    tierekisteriEnabled
  }

  /**
    * Returns a bus stop based on OTH "yllapitajan_koodi" id
    * Tierekisteri REST API endpoint: GET /pysakit/{livitunn}
    *
    * @param id
    * @return
    */
  def fetchMassTransitStop(id: String): Option[TierekisteriMassTransitStop] = {
    logger.info("Requesting stop %s from Tierekisteri".format(id))
    request[Map[String, Any]](serviceUrl(id)) match {
      case Left(content) =>
        Some(mapFields(content))
      case Right(null) =>
        None
      case Right(error) => throw new TierekisteriClientException("Tierekisteri error: " + error.content.get("error").get.toString)
    }
  }

  /**
    * Creates a new bus stop to Tierekisteri.
    * Tierekisteri REST API endpoint: POST /pysakit/
    *
    * @param trMassTransitStop
    */
  def createMassTransitStop(trMassTransitStop: TierekisteriMassTransitStop, replaceLiviId: Option[String] = None): Unit ={
    logger.info("Creating stop %s in Tierekisteri".format(trMassTransitStop.liviId))
    post(serviceUrl(replaceLiviId), trMassTransitStop, createJson) match {
      case Some(error) => throw new TierekisteriClientException("Tierekisteri error: " + error.content.get("error").get.toString)
      case _ => ; // do nothing
    }
  }

  /**
    * Updates and/or invalidates a stop. (If valid_to is set, the stop is invalidated. Other data may be updated at the time, too)
    * Tierekisteri REST API endpoint: PUT /pysakit/{livitunn}
    *
    * @param trMassTransitStop
    */
  def updateMassTransitStop(trMassTransitStop: TierekisteriMassTransitStop, overrideLiviIdOption: Option[String], overrideUserNameOption: Option[String] = None): Unit ={
    val liviId = overrideLiviIdOption.getOrElse(trMassTransitStop.liviId)
    val trStop = trMassTransitStop.copy(modifiedBy = overrideUserNameOption.getOrElse(trMassTransitStop.modifiedBy))
    logger.info("Updating stop %s in Tierekisteri".format(liviId))
    put(serviceUrl(liviId), trStop, createJson) match {
      case Some(error) => throw new TierekisteriClientException("Tierekisteri error: " + error.content.get("error").get.toString)
      case _ => ;
    }
  }

  /**
    * Marks a bus stop to be removed. Only for error correcting purposes, for example when bus stop was accidentally added.
    * Tierekisteri REST API endpoint: DELETE /pysakit/{livitunn}
    *
    * @param id
    */
  def deleteMassTransitStop(id: String): Unit ={
    logger.info("REMOVING stop %s in Tierekisteri".format(id))
    delete(serviceUrl(id)) match {
      case Some(error) => throw new TierekisteriClientException("Tierekisteri error: " + error.content.get("error").get.toString)
      case _ => ;
    }
  }


  protected def createJson(trMassTransitStop: TierekisteriType) = {

    val jsonObj = Map(
      trNationalId -> trMassTransitStop.nationalId,
      trLiviId -> trMassTransitStop.liviId,
      trRoadNumber -> trMassTransitStop.roadAddress.road,
      trRoadPartNumber -> trMassTransitStop.roadAddress.roadPart,
      trSide -> trMassTransitStop.roadSide.value,
      trLane -> trMassTransitStop.roadAddress.track.value,
      trDistance -> trMassTransitStop.roadAddress.mValue,
      trStopCode -> trMassTransitStop.stopCode,
      trIsExpress -> booleanToBooleanCode.get(trMassTransitStop.express),
      trNameFi -> trMassTransitStop.nameFi,
      trNameSe -> trMassTransitStop.nameSe,
      trUser -> trMassTransitStop.modifiedBy,
      trOperatingFrom -> convertDateToString(trMassTransitStop.operatingFrom),
      trOperatingTo -> convertDateToString(trMassTransitStop.operatingTo),
      trRemovalDate -> convertDateToString(trMassTransitStop.removalDate),
      trInventoryDate -> convertDateToString(trMassTransitStop.inventoryDate),
      trEquipment -> trMassTransitStop.equipments.map{
        case (equipment, existence) =>
          equipment.value -> existence.value
      }
    )

    val stopType: Map[String, Any] = trMassTransitStop.stopType match {
      case StopType.Unknown => Map()
      case _ => Map(trStopType -> trMassTransitStop.stopType.value)
    }

    val json = Serialization.write(jsonObj ++ stopType)
    // Print JSON sent to Tierekisteri for testing purposes
    logger.info("Stop in JSON: %s".format(json))

    new StringEntity(json, ContentType.APPLICATION_JSON)
  }

  override def mapFields(data: Map[String, Any]): TierekisteriMassTransitStop = {

    //Mandatory fields
    val nationalId = convertToLong(getMandatoryFieldValue(data, trNationalId)).get
    val roadSide = TRRoadSide.apply(getMandatoryFieldValue(data, trSide).get)
    val express = booleanCodeToBoolean.getOrElse(getMandatoryFieldValue(data, trIsExpress).get, throw new TierekisteriClientException("The boolean code '%s' is not supported".format(getFieldValue(data, trIsExpress))))
    val liviId = getMandatoryFieldValue(data, trLiviId).get
    val stopType = StopType.apply(getMandatoryFieldValue(data, trStopType).get)
    val modifiedBy = getMandatoryFieldValue(data, trUser).get
    val roadAddress = RoadAddress(None, convertToInt(getMandatoryFieldValue(data, trRoadNumber)).get,
      convertToInt(getMandatoryFieldValue(data, trRoadPartNumber)).get,Track.Combined,convertToInt(getMandatoryFieldValue(data, trDistance)).get,None)

    //Not mandatory fields
    val equipments = extractEquipment(data)
    val stopCode = getFieldValue(data, trStopCode)
    val nameFi = getFieldValue(data, trNameFi)
    val nameSe = getFieldValue(data, trNameSe)
    val operatingFrom = convertToDate(getFieldValue(data, trOperatingFrom))
    val operatingTo = convertToDate(getFieldValue(data, trOperatingTo))
    val removalDate = convertToDate(getFieldValue(data, trRemovalDate))
    val inventoryDate = convertToDate(Some(getFieldValue(data, trInventoryDate).getOrElse(toIso8601.print(DateTime.now())))).get

    TierekisteriMassTransitStop(nationalId,liviId, roadAddress, roadSide, stopType, express, equipments,
      stopCode, nameFi, nameSe, modifiedBy, operatingFrom, operatingTo, removalDate, inventoryDate)
  }

  private def extractEquipment(data: Map[String, Any]) : Map[Equipment, Existence] = {
    val equipmentData: Map[String, String] = data.get(trEquipment).nonEmpty match {
      case true => data.get(trEquipment).get.asInstanceOf[Map[String, String]]
      case false => Map()
    }

    Equipment.values.flatMap{ equipment =>
      equipmentData.get(equipment.value) match{
        case Some(value) =>
          Some(equipment -> Existence.apply(value))
        case None =>
          None
      }
    }.toMap
  }
}

trait TierekisteriAssetDataClient extends TierekisteriClient {

  private val serviceName = "tietolajit/"
  protected val trRoadNumber = "TIE"
  protected val trRoadPartNumber = "OSA"
  protected val trEndRoadPartNumber = "LOSA"
  protected val trStartMValue = "ETAISYYS"
  protected val trEndMValue = "LET"
  protected val trTrackCode = "AJORATA"
  protected def trAssetType : String

  private val serviceUrl : String = tierekisteriRestApiEndPoint + serviceName

  override type TierekisteriType <: TierekisteriAssetData

  def queryString(changeDate: Option[DateTime]) : String = {
    changeDate match {
      case Some(value) => "?muutospvm="+changeDate.get.toString("yyyy-MM-dd") + "%20" + changeDate.get.toString("hh:mm:ss")
      case _ => ""
    }
  }

  private def serviceUrl(assetType: String, roadNumber: Long) : String = serviceUrl + assetType + "/" + roadNumber
  private def serviceUrl(assetType: String, roadNumber: Long, roadPartNumber: Long) : String = serviceUrl + assetType + "/" + roadNumber + "/" + roadPartNumber
  private def serviceUrl(assetType: String, roadNumber: Long, roadPartNumber: Long, startDistance: Int) : String =
    serviceUrl + assetType + "/" + roadNumber + "/" + roadPartNumber + "/" + startDistance
  private def serviceUrl(assetType: String, roadNumber: Long, roadPartNumber: Long, startDistance: Int, endPart: Int, endDistance: Int) : String =
    serviceUrl + assetType + "/" + roadNumber + "/" + roadPartNumber + "/" + startDistance + "/" + endPart + "/" + endDistance

  private def serviceHistoryUrl(assetType: String, roadNumber: Long, changeDate:  Option[DateTime]) : String = serviceUrl + assetType + "/" + roadNumber + queryString(changeDate)
  private def serviceHistoryUrl(assetType: String, roadNumber: Long, roadPartNumber: Long, changeDate: Option[DateTime]) : String = serviceUrl + assetType + "/" + roadNumber + "/" + roadPartNumber + queryString(changeDate)
  private def serviceHistoryUrl(assetType: String, roadNumber: Long, roadPartNumber: Long, startDistance: Int, changeDate: Option[DateTime]) : String =
    serviceUrl + assetType + "/" + roadNumber + "/" + roadPartNumber + "/" + startDistance + queryString(changeDate)
  private def serviceHistoryUrl(assetType: String, roadNumber: Long, roadPartNumber: Long, startDistance: Int, endPart: Int, endDistance: Int, changeDate: Option[DateTime]) : String =
    serviceUrl + assetType + "/" + roadNumber + "/" + roadPartNumber + "/" + startDistance + "/" + endPart + "/" + endDistance + queryString(changeDate)

  /**
    * Return all asset data currently active from Tierekisteri
    * Tierekisteri REST API endpoint: GET /trrest/tietolajit/{tietolaji}/{tie}
    *
    * @return
    */
  def fetchActiveAssetData(roadNumber: Long): Seq[TierekisteriType] = {
    request[Map[String,List[Map[String, Any]]]](serviceUrl(trAssetType, roadNumber)) match {
      case Left(content) => {
        content("Data").map{
          asset => mapFields(asset)
        }
      }
      case Right(null) => Seq()
      case Right(error) => throw new TierekisteriClientException("Tierekisteri error: " + error.content.get("error").get.toString)
    }
  }

  def fetchActiveAssetData(roadNumber: Long, roadPartNumber: Long): Seq[TierekisteriType] = {
    request[Map[String,List[Map[String, Any]]]](serviceUrl(trAssetType, roadNumber, roadPartNumber)) match {
      case Left(content) =>
        content("Data").map{
          asset => mapFields(asset)
        }
      case Right(null) => Seq()
      case Right(error) => throw new TierekisteriClientException("Tierekisteri error: " + error.content.get("error").get.toString)
    }
  }

  def fetchActiveAssetData(roadNumber: Long, roadPartNumber: Long, startDistance: Int): Seq[TierekisteriType] = {
    request[Map[String,List[Map[String, Any]]]](serviceUrl(trAssetType, roadNumber, roadPartNumber, startDistance)) match {
      case Left(content) =>
        content("Data").map{
          asset => mapFields(asset)
        }
      case Right(null) => Seq()
      case Right(error) => throw new TierekisteriClientException("Tierekisteri error: " + error.content.get("error").get.toString)
    }
  }

  def fetchActiveAssetData(roadNumber: Long, roadPartNumber: Long, startDistance: Int, endPart: Int, endDistance: Int): Seq[TierekisteriType] = {
    request[Map[String,List[Map[String, Any]]]](serviceUrl(trAssetType, roadNumber, roadPartNumber, startDistance, endPart, endDistance)) match {
      case Left(content) =>
        content("Data").map{
          asset => mapFields(asset)
        }
      case Right(null) => Seq()
      case Right(error) => throw new TierekisteriClientException("Tierekisteri error: " + error.content.get("error").get.toString)
    }
  }

  def fetchHistoryAssetData(roadNumber: Long, changeDate:  Option[DateTime]): Seq[TierekisteriType] = {
    request[Map[String,List[Map[String, Any]]]](serviceHistoryUrl(trAssetType, roadNumber, changeDate)) match {
      case Left(content) => {
        content("Data").map{
          asset => mapFields(asset)
        }
      }
      case Right(null) => Seq()
      case Right(error) => throw new TierekisteriClientException("Tierekisteri error: " + error.content.get("error").get.toString)
    }
  }

  def fetchHistoryAssetData(roadNumber: Long, roadPartNumber: Long, changeDate: Option[DateTime]): Seq[TierekisteriType] = {
    request[Map[String,List[Map[String, Any]]]](serviceHistoryUrl(trAssetType, roadNumber, roadPartNumber, changeDate)) match {
      case Left(content) =>
        content("Data").map{
          asset => mapFields(asset)
        }
      case Right(null) => Seq()
      case Right(error) => throw new TierekisteriClientException("Tierekisteri error: " + error.content.get("error").get.toString)
    }
  }

  def fetchHistoryAssetData(roadNumber: Long, roadPartNumber: Long, startDistance: Int, changeDate: Option[DateTime]): Seq[TierekisteriType] = {
    request[Map[String,List[Map[String, Any]]]](serviceHistoryUrl(trAssetType, roadNumber, roadPartNumber, startDistance, changeDate)) match {
      case Left(content) =>
        content("Data").map{
          asset => mapFields(asset)
        }
      case Right(null) => Seq()
      case Right(error) => throw new TierekisteriClientException("Tierekisteri error: " + error.content.get("error").get.toString)
    }
  }

  def fetchHistoryAssetData(roadNumber: Long, roadPartNumber: Long, startDistance: Int, endPart: Int, endDistance: Int, changeDate: Option[DateTime]): Seq[TierekisteriType] = {
    request[Map[String,List[Map[String, Any]]]](serviceHistoryUrl(trAssetType, roadNumber, roadPartNumber, startDistance, endPart, endDistance, changeDate)) match {
      case Left(content) =>
        content("Data").map{
          asset => mapFields(asset)
        }
      case Right(null) => Seq()
      case Right(error) => throw new TierekisteriClientException("Tierekisteri error: " + error.content.get("error").get.toString)
    }
  }
}

class TierekisteriTrafficVolumeAssetClient(trEndPoint: String, trEnable: Boolean, httpClient: CloseableHttpClient) extends TierekisteriAssetDataClient {
  override def tierekisteriRestApiEndPoint: String = trEndPoint
  override def tierekisteriEnabled: Boolean = trEnable
  override def client: CloseableHttpClient = httpClient
  type TierekisteriType = TierekisteriTrafficData

  override val trAssetType = "tl201"
  private val trKVL = "KVL"

  override def mapFields(data: Map[String, Any]): TierekisteriTrafficData = {
    //Mandatory field
    val assetValue = convertToInt(getMandatoryFieldValue(data, trKVL)).get
    val roadNumber = convertToLong(getMandatoryFieldValue(data, trRoadNumber)).get
    val roadPartNumber = convertToLong(getMandatoryFieldValue(data, trRoadPartNumber)).get
    val endRoadPartNumber = convertToLong(getMandatoryFieldValue(data, trEndRoadPartNumber)).getOrElse(roadPartNumber)
    val startMValue = convertToLong(getMandatoryFieldValue(data, trStartMValue)).get
    val endMValue = convertToLong(getMandatoryFieldValue(data, trEndMValue)).get
    val track = convertToInt(getMandatoryFieldValue(data, trTrackCode)).map(Track.apply).getOrElse(Track.Unknown)

    TierekisteriTrafficData(roadNumber, roadPartNumber, endRoadPartNumber, track, startMValue, endMValue, assetValue)
  }
}

class TierekisteriLightingAssetClient(trEndPoint: String, trEnable: Boolean, httpClient: CloseableHttpClient) extends TierekisteriAssetDataClient {
  override def tierekisteriRestApiEndPoint: String = trEndPoint
  override def tierekisteriEnabled: Boolean = trEnable
  override def client: CloseableHttpClient = httpClient
  type TierekisteriType = TierekisteriLightingData

  override val trAssetType = "tl167"

  override def mapFields(data: Map[String, Any]): TierekisteriLightingData = {
    //Mandatory field
    val roadNumber = convertToLong(getMandatoryFieldValue(data, trRoadNumber)).get
    val roadPartNumber = convertToLong(getMandatoryFieldValue(data, trRoadPartNumber)).get
    val endRoadPartNumber = convertToLong(getMandatoryFieldValue(data, trEndRoadPartNumber)).getOrElse(roadPartNumber)
    val startMValue = convertToLong(getMandatoryFieldValue(data, trStartMValue)).get
    val endMValue = convertToLong(getMandatoryFieldValue(data, trEndMValue)).get
    val track = convertToInt(getMandatoryFieldValue(data, trTrackCode)).map(Track.apply).getOrElse(Track.Unknown)

    TierekisteriLightingData(roadNumber, roadPartNumber, endRoadPartNumber, track, startMValue, endMValue)
  }
}

class TierekisteriRoadWidthAssetClient(trEndPoint: String, trEnable: Boolean, httpClient: CloseableHttpClient) extends TierekisteriAssetDataClient{
  override def tierekisteriRestApiEndPoint: String = trEndPoint
  override def tierekisteriEnabled: Boolean = trEnable
  override def client: CloseableHttpClient = httpClient
  type TierekisteriType = TierekisteriRoadWidthData

  override val trAssetType = "tl136"
  private val trALEV = "ALEV"

  override def mapFields(data: Map[String, Any]): TierekisteriRoadWidthData = {
    //Mandatory field
    val assetValue = convertToInt(getMandatoryFieldValue(data, trALEV)).get * 10 //To convert to cm
    val roadNumber = convertToLong(getMandatoryFieldValue(data, trRoadNumber)).get
    val roadPartNumber = convertToLong(getMandatoryFieldValue(data, trRoadPartNumber)).get
    val endRoadPartNumber = convertToLong(getMandatoryFieldValue(data, trEndRoadPartNumber)).getOrElse(roadPartNumber)
    val startMValue = convertToLong(getMandatoryFieldValue(data, trStartMValue)).get
    val endMValue = convertToLong(getMandatoryFieldValue(data, trEndMValue)).get
    val track = convertToInt(getMandatoryFieldValue(data, trTrackCode)).map(Track.apply).getOrElse(Track.Unknown)

    TierekisteriRoadWidthData(roadNumber, roadPartNumber, endRoadPartNumber, track, startMValue, endMValue, assetValue)
  }
}

class TierekisteriTrafficSignAssetClient(trEndPoint: String, trEnable: Boolean, httpClient: CloseableHttpClient) extends TierekisteriAssetDataClient{
  override def tierekisteriRestApiEndPoint: String = trEndPoint
  override def tierekisteriEnabled: Boolean = trEnable
  override def client: CloseableHttpClient = httpClient
  type TierekisteriType = TierekisteriTrafficSignData

  override val trAssetType = "tl506"
  private val trLMNUMERO = "LMNUMERO"
  private val trLMTEKSTI = "LMTEKSTI"
  private val trPUOLI = "PUOLI"
  private val trLIIKVAST = "LIIKVAST"
  private val trNOPRA506 = "NOPRA506"
  private val wrongSideOfTheRoad = "1"

  override def mapFields(data: Map[String, Any]): TierekisteriTrafficSignData = {

    //TODO remove the orElse and ignrore the all row when we give support for that on TierekisteriClient base implementation
    val assetNumber = convertToInt(getFieldValue(data, trLMNUMERO).orElse(Some("99"))).get
    val roadNumber = convertToLong(getMandatoryFieldValue(data, trRoadNumber)).get
    val roadPartNumber = convertToLong(getMandatoryFieldValue(data, trRoadPartNumber)).get
    val startMValue = convertToLong(getMandatoryFieldValue(data, trStartMValue)).get
    val track = convertToInt(getMandatoryFieldValue(data, trTrackCode)).map(Track.apply).getOrElse(Track.Unknown)
    val roadSide = convertToInt(getMandatoryFieldValue(data, trPUOLI)).map(RoadSide.apply).getOrElse(RoadSide.Unknown)

    getFieldValue(data, trLIIKVAST) match {
      case Some(info) =>
        val assetValue = getFieldValue(data, trLMTEKSTI).getOrElse("").trim
        if (info == wrongSideOfTheRoad && Seq(TRTrafficSignType.SpeedLimit, TRTrafficSignType.SpeedLimitZone, TRTrafficSignType.UrbanArea).contains(TRTrafficSignType.apply(assetNumber)))
          TierekisteriTrafficSignData(roadNumber, roadPartNumber, roadPartNumber, track, startMValue, startMValue, roadSide, TRTrafficSignType.Unknown, assetValue)
        else
          TierekisteriTrafficSignData(roadNumber, roadPartNumber, roadPartNumber, track, startMValue, startMValue, roadSide, TRTrafficSignType.apply(assetNumber), assetValue)
      case _ =>
        val assetValue = getFieldValue(data, trNOPRA506).getOrElse("").trim
        TierekisteriTrafficSignData(roadNumber, roadPartNumber, roadPartNumber, track, startMValue, startMValue, roadSide, TRTrafficSignType.apply(assetNumber), assetValue)
    }
  }
}

class TierekisteriUrbanAreaClient(trEndPoint: String, trEnable: Boolean, httpClient: CloseableHttpClient) extends TierekisteriAssetDataClient {
  override def tierekisteriRestApiEndPoint: String = trEndPoint
  override def tierekisteriEnabled: Boolean = trEnable
  override def client: CloseableHttpClient = httpClient
  type TierekisteriType = TierekisteriUrbanAreaData

  override val trAssetType = "tl139"
  private val trUrbanAreaValue = "TIENAS"
  private val defaultUrbanAreaValue = "9"

  override def mapFields(data: Map[String, Any]): TierekisteriUrbanAreaData = {
    val assetValue = getFieldValue(data, trUrbanAreaValue).orElse(Some(defaultUrbanAreaValue)).get
    val roadNumber = convertToLong(getMandatoryFieldValue(data, trRoadNumber)).get
    val roadPartNumber = convertToLong(getMandatoryFieldValue(data, trRoadPartNumber)).get
    val startMValue = convertToLong(getMandatoryFieldValue(data, trStartMValue)).get
    val endMValue = convertToLong(getMandatoryFieldValue(data, trEndMValue)).get
    val track = convertToInt(getMandatoryFieldValue(data, trTrackCode)).map(Track.apply).getOrElse(Track.Unknown)

    TierekisteriUrbanAreaData(roadNumber, roadPartNumber, roadPartNumber, track, startMValue, endMValue, assetValue)
  }
}

class TierekisteriTelematicSpeedLimitClient(trEndPoint: String, trEnable: Boolean, httpClient: CloseableHttpClient) extends TierekisteriAssetDataClient {
  override def tierekisteriRestApiEndPoint: String = trEndPoint
  override def tierekisteriEnabled: Boolean = trEnable
  override def client: CloseableHttpClient = httpClient
  type TierekisteriType = TierekisteriTrafficSignData

  override val trAssetType = "tl523"
  private val trTecPointType = "TEKTYYPPI"
  private val trPUOLI = "PUOLI"
  private val trTelematicSpeedLimitAsset = "34"

  override def mapFields(data: Map[String, Any]): TierekisteriTrafficSignData = {
    val roadNumber = convertToLong(getMandatoryFieldValue(data, trRoadNumber)).get
    val roadPartNumber = convertToLong(getMandatoryFieldValue(data, trRoadPartNumber)).get
    val startMValue = convertToLong(getMandatoryFieldValue(data, trStartMValue)).get
    val track = convertToInt(getMandatoryFieldValue(data, trTrackCode)).map(Track.apply).getOrElse(Track.Unknown)
    val roadSide = convertToInt(getMandatoryFieldValue(data, trPUOLI)).map(RoadSide.apply).getOrElse(RoadSide.Unknown)

    getMandatoryFieldValue(data, trTecPointType) match {
      case Some(tecType) if tecType == trTelematicSpeedLimitAsset =>
        TierekisteriTrafficSignData(roadNumber, roadPartNumber, roadPartNumber, track, startMValue, startMValue, roadSide, TRTrafficSignType.TelematicSpeedLimit,"")
      case _ =>
        TierekisteriTrafficSignData(roadNumber, roadPartNumber, roadPartNumber, track, startMValue, startMValue, roadSide, TRTrafficSignType.Unknown,"")
    }
  }
}

class TierekisteriPavedRoadAssetClient(trEndPoint: String, trEnable: Boolean, httpClient: CloseableHttpClient) extends TierekisteriAssetDataClient {
  override def tierekisteriRestApiEndPoint: String = trEndPoint
  override def tierekisteriEnabled: Boolean = trEnable
  override def client: CloseableHttpClient = httpClient
  type TierekisteriType = TierekisteriPavedRoadData

  override val trAssetType = "tl137"
  private val trPavementType = "PAALLUOK"

  override def mapFields(data: Map[String, Any]): TierekisteriPavedRoadData = {
    //Mandatory field
    val roadNumber = convertToLong(getMandatoryFieldValue(data, trRoadNumber)).get
    val roadPartNumber = convertToLong(getMandatoryFieldValue(data, trRoadPartNumber)).get
    val endRoadPartNumber = convertToLong(getMandatoryFieldValue(data, trEndRoadPartNumber)).getOrElse(roadPartNumber)
    val startMValue = convertToLong(getMandatoryFieldValue(data, trStartMValue)).get
    val endMValue = convertToLong(getMandatoryFieldValue(data, trEndMValue)).get
    val track = convertToInt(getMandatoryFieldValue(data, trTrackCode)).map(Track.apply).getOrElse(Track.Unknown)
    val pavementType = convertToInt(getMandatoryFieldValue(data, trPavementType)).get

    TierekisteriPavedRoadData(roadNumber, roadPartNumber, endRoadPartNumber, track, startMValue, endMValue, TRPavedRoadType.apply(pavementType))
  }
}

class TierekisteriMassTransitLaneAssetClient(trEndPoint: String, trEnable: Boolean, httpClient: CloseableHttpClient) extends TierekisteriAssetDataClient {
  override def tierekisteriRestApiEndPoint: String = trEndPoint
  override def tierekisteriEnabled: Boolean = trEnable
  override def client: CloseableHttpClient = httpClient
  type TierekisteriType = TierekisteriMassTransitLaneData

  override val trAssetType = "tl161"
  private val trLaneType = "KAISTATY"

  override def mapFields(data: Map[String, Any]): TierekisteriMassTransitLaneData = {
    //Mandatory field
    val roadNumber = convertToLong(getMandatoryFieldValue(data, trRoadNumber)).get
    val roadPartNumber = convertToLong(getMandatoryFieldValue(data, trRoadPartNumber)).get
    val endRoadPartNumber = convertToLong(getMandatoryFieldValue(data, trEndRoadPartNumber)).getOrElse(roadPartNumber)
    val startMValue = convertToLong(getMandatoryFieldValue(data, trStartMValue)).get
    val endMValue = convertToLong(getMandatoryFieldValue(data, trEndMValue)).get
    val track = convertToInt(getMandatoryFieldValue(data, trTrackCode)).map(Track.apply).getOrElse(Track.Unknown)
    val laneType = convertToInt(getMandatoryFieldValue(data, trLaneType)).get

    TierekisteriMassTransitLaneData(roadNumber, roadPartNumber, endRoadPartNumber, track, startMValue, endMValue, TRLaneArrangementType.apply(laneType))
  }
}

class TierekisteriDamagedByThawAssetClient(trEndPoint: String, trEnable: Boolean, httpClient: CloseableHttpClient) extends TierekisteriAssetDataClient {
  override def tierekisteriRestApiEndPoint: String = trEndPoint
  override def tierekisteriEnabled: Boolean = trEnable
  override def client: CloseableHttpClient = httpClient
  type TierekisteriType = TierekisteriDamagedByThawData

  override val trAssetType = "tl162"

  override def mapFields(data: Map[String, Any]): TierekisteriDamagedByThawData = {
    //Mandatory field
    val roadNumber = convertToLong(getMandatoryFieldValue(data, trRoadNumber)).get
    val roadPartNumber = convertToLong(getMandatoryFieldValue(data, trRoadPartNumber)).get
    val endRoadPartNumber = convertToLong(getMandatoryFieldValue(data, trEndRoadPartNumber)).getOrElse(roadPartNumber)
    val startMValue = convertToLong(getMandatoryFieldValue(data, trStartMValue)).get
    val endMValue = convertToLong(getMandatoryFieldValue(data, trEndMValue)).get
    val track = convertToInt(getMandatoryFieldValue(data, trTrackCode)).map(Track.apply).getOrElse(Track.Unknown)

    TierekisteriDamagedByThawData(roadNumber, roadPartNumber, endRoadPartNumber, track, startMValue, endMValue)
  }
}

class TierekisteriEuropeanRoadAssetClient(trEndPoint: String, trEnable: Boolean, httpClient: CloseableHttpClient) extends TierekisteriAssetDataClient{
  override def tierekisteriRestApiEndPoint: String = trEndPoint
  override def tierekisteriEnabled: Boolean = trEnable
  override def client: CloseableHttpClient = httpClient
  type TierekisteriType = TierekisteriEuropeanRoadData

  override val trAssetType = "tl130"
  private val trEuropeanRoadNumber = "EURONRO"

  override def mapFields(data: Map[String, Any]): TierekisteriEuropeanRoadData = {
    val assetValue =
      getFieldValue(data, trEuropeanRoadNumber) match {
        case Some(value) => value
        case None => " "
      }

    //Mandatory field
    val roadNumber = convertToLong(getMandatoryFieldValue(data, trRoadNumber)).get
    val roadPartNumber = convertToLong(getMandatoryFieldValue(data, trRoadPartNumber)).get
    val endRoadPartNumber = convertToLong(getMandatoryFieldValue(data, trEndRoadPartNumber)).getOrElse(roadPartNumber)
    val startMValue = convertToLong(getMandatoryFieldValue(data, trStartMValue)).get
    val endMValue = convertToLong(getMandatoryFieldValue(data, trEndMValue)).get
    val track = convertToInt(getMandatoryFieldValue(data, trTrackCode)).map(Track.apply).getOrElse(Track.Unknown)

    TierekisteriEuropeanRoadData(roadNumber, roadPartNumber, endRoadPartNumber, track, startMValue, endMValue, assetValue)
  }
}

class TierekisteriSpeedLimitAssetClient(trEndPoint: String, trEnable: Boolean, httpClient: CloseableHttpClient) extends TierekisteriAssetDataClient {
  override def tierekisteriRestApiEndPoint: String = trEndPoint
  override def tierekisteriEnabled: Boolean = trEnable
  override def client: CloseableHttpClient = httpClient
  type TierekisteriType = TierekisteriSpeedLimitData

  override val trAssetType = "tl168"
  private val trSpeedLimitValue = "NOPRAJ"
  private val trSide = "PUOLI"


  override def mapFields(data: Map[String, Any]): TierekisteriSpeedLimitData = {

    //Mandatory field
    val assetValue = convertToInt(getMandatoryFieldValue(data, trSpeedLimitValue)).get
    val roadNumber = convertToLong(getMandatoryFieldValue(data, trRoadNumber)).get
    val roadPartNumber = convertToLong(getMandatoryFieldValue(data, trRoadPartNumber)).get
    val endRoadPartNumber = convertToLong(getMandatoryFieldValue(data, trEndRoadPartNumber)).getOrElse(roadPartNumber)
    val startMValue = convertToLong(getMandatoryFieldValue(data, trStartMValue)).get
    val endMValue = convertToLong(getMandatoryFieldValue(data, trEndMValue)).get
    val track = convertToInt(getMandatoryFieldValue(data, trTrackCode)).map(Track.apply).getOrElse(Track.Unknown)
    val roadSide = convertToInt(getMandatoryFieldValue(data, trSide)).map(RoadSide.apply).getOrElse(RoadSide.Unknown)


    TierekisteriSpeedLimitData(roadNumber, roadPartNumber, endRoadPartNumber, track, startMValue, endMValue, assetValue, roadSide)
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
/**
  * A class to transform data between the interface bus stop format and OTH internal bus stop format
  */
object TierekisteriBusStopMarshaller {

  private val liviIdPublicId = "yllapitajan_koodi"
  private val stopTypePublicId = "pysakin_tyyppi"
  private val nameFiPublicId = "nimi_suomeksi"
  private val nameSePublicId = "nimi_ruotsiksi"
  private val stopCode = "matkustajatunnus"
  private val InventoryDatePublicId = "inventointipaiva"
  private val FirstDayValidPublicId = "ensimmainen_voimassaolopaiva"
  private val LastDayValidPublicId = "viimeinen_voimassaolopaiva"
  private val expressPropertyValue = 4
  private val typeId: Int = 10
  private val dateFormat = "yyyy-MM-dd"

  private def convertDateToString(date: Option[Date]): Option[String] = {
    date.map(dv => new SimpleDateFormat(dateFormat).format(dv))
  }

  private def convertStringToDate(str: Option[String]): Option[Date] = {
    if(str.exists(_.trim.nonEmpty))
      Some(new SimpleDateFormat(dateFormat).parse(str.get))
    else
      None
  }

  def getAllPropertiesAvailable(AssetTypeId : Int): Seq[Property] = {
    Queries.availableProperties(AssetTypeId)
  }


  def getPropertyOption(propertyData: Seq[Property], publicId: String): Option[String] = {
    propertyData.find(p => p.publicId == publicId).flatMap(_.values.headOption.map(_.propertyValue))
  }
  def getPropertyDateOption(propertyData: Seq[Property], publicId: String): Option[Date] = {
    convertStringToDate(propertyData.find(p => p.publicId == publicId).flatMap(_.values.headOption.map(_.propertyValue)))
  }

  def toTRRoadSide(roadSide: RoadSide) = {
    roadSide match {
      case RoadSide.Right => TRRoadSide.Right
      case RoadSide.Left => TRRoadSide.Left
      case RoadSide.End => TRRoadSide.Off
      case _ => TRRoadSide.Unknown
    }
  }
  def toTierekisteriMassTransitStop(massTransitStop: PersistedMassTransitStop, roadAddress: RoadAddress,
                                    roadSideOption: Option[RoadSide], expireDate: Option[Date] = None, overrideLiviId: Option[String] = None): TierekisteriMassTransitStop = {
    val inventoryDate = convertStringToDate(getPropertyOption(massTransitStop.propertyData, InventoryDatePublicId)).getOrElse(new Date)
    val startingDate = convertStringToDate(getPropertyOption(massTransitStop.propertyData, FirstDayValidPublicId))
    val lastDate = if (expireDate.nonEmpty) expireDate else convertStringToDate(getPropertyOption(massTransitStop.propertyData, LastDayValidPublicId))
    TierekisteriMassTransitStop(massTransitStop.nationalId, findLiViId(massTransitStop.propertyData).getOrElse(overrideLiviId.getOrElse("")),
      roadAddress, roadSideOption.map(toTRRoadSide).getOrElse(TRRoadSide.Unknown), findStopType(massTransitStop.stopTypes),
      massTransitStop.stopTypes.contains(expressPropertyValue), mapEquipments(massTransitStop.propertyData),
      getPropertyOption(massTransitStop.propertyData, stopCode),
      getPropertyOption(massTransitStop.propertyData, nameFiPublicId),
      getPropertyOption(massTransitStop.propertyData, nameSePublicId),
      massTransitStop.modified.modifier.getOrElse(massTransitStop.created.modifier.get),
      startingDate, lastDate, None, inventoryDate)
  }

  // Map Seq(2) => local, Seq(2,3) => Combined, Seq(3) => Long distance, Seq(5) => Virtual
  def findStopType(stopTypes: Seq[Int]): StopType = {
    // remove from OTH stoptypes the values that are not supported by tierekisteri
    val codesOfInterest = StopType.values.flatMap(st => st.propertyValues)
    val availableStopTypes = StopType.values.map(st => st.propertyValues -> st ).toMap
    availableStopTypes.get(stopTypes.toSet.intersect(codesOfInterest)) match {
      case Some(stopType) =>
        stopType
      case None =>
        StopType.Unknown
    }
  }

  private def mapEquipments(properties: Seq[Property]): Map[Equipment, Existence] = {
    properties.map(p => mapPropertyToEquipment(p) -> mapPropertyValueToExistence(p.values)).
      filterNot(p => p._1.equals(Equipment.Unknown)).toMap
  }

  private def mapPropertyToEquipment(p: Property) = {
    Equipment.fromPublicId(p.publicId)
  }

  private def mapPropertyValueToExistence(values: Seq[PropertyValue]) = {
    val v = values.map(pv => Existence.fromPropertyValue(pv.propertyValue)).distinct
    v.size match {
      case 1 => v.head
      case _ => Existence.Unknown // none or mismatching
    }
  }

  private def findLiViId(properties: Seq[Property]) = {
    properties.find(p =>
      p.publicId.equals(liviIdPublicId) && p.values.nonEmpty).map(_.values.head.propertyValue)
  }

  private def mapEquipmentProperties(equipments: Map[Equipment, Existence], allProperties: Seq[Property]): Seq[Property] = {
    equipments.map {
      case (equipment, existence) =>
        val equipmentProperties = allProperties.find(p => p.publicId.equals(equipment.publicId)).get
        Property(equipmentProperties.id, equipment.publicId, equipmentProperties.propertyType, equipmentProperties.required, Seq(PropertyValue(existence.propertyValue.toString)))
    }.toSeq
  }

  private def mapStopTypeProperties(stopType: StopType, isExpress: Boolean, allProperties: Seq[Property]): Seq[Property] = {
    var propertyValues = stopType.propertyValues.map { value =>
      PropertyValue(value.toString)
    }
    if (isExpress)
      propertyValues += PropertyValue(expressPropertyValue.toString)

    val stopTypeProperties = allProperties.find(p => p.publicId.equals(stopTypePublicId)).get

    Seq (Property(stopTypeProperties.id, stopTypePublicId, stopTypeProperties.propertyType, stopTypeProperties.required, propertyValues.toSeq))
  }

  private def mapLiViIdProperties(liViId: String, allProperties: Seq[Property]): Seq[Property] = {
    val liViIdProperties = allProperties.find(p => p.publicId.equals(liviIdPublicId)).get

    Seq(Property(liViIdProperties.id, liviIdPublicId, liViIdProperties.propertyType, liViIdProperties.required, Seq(PropertyValue(liViId))))
  }

  private def mapNameFiProperties(nameFi: String, allProperties: Seq[Property]): Seq[Property] = {
    val nameFiProperties = allProperties.find(p => p.publicId.equals(nameFiPublicId)).get

    Seq(Property(nameFiProperties.id, nameFiPublicId, nameFiProperties.propertyType, nameFiProperties.required, Seq(PropertyValue(nameFi))))
  }

  private def mapNameSeProperties(nameSe: String, allProperties: Seq[Property]): Seq[Property] = {
    val nameSeProperties = allProperties.find(p => p.publicId.equals(nameSePublicId)).get

    Seq(Property(nameSeProperties.id, nameSePublicId, nameSeProperties.propertyType, nameSeProperties.required, Seq(PropertyValue(nameSe))))
  }
}