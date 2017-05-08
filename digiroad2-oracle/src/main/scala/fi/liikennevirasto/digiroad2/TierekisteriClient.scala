package fi.liikennevirasto.digiroad2

import java.text.{ParseException, SimpleDateFormat}
import java.util.Date
import fi.liikennevirasto.digiroad2.asset.{Property, PropertyValue}
import fi.liikennevirasto.digiroad2.masstransitstop.oracle.Queries
import fi.liikennevirasto.digiroad2.util.{RoadAddress, RoadSide, TierekisteriAuthPropertyReader, Track}
import org.apache.http.HttpStatus
import org.apache.http.client.methods._
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
  case object Unknown extends TRRoadSide { def value = "ei_tietoa"; def propertyValues = Set(0) }
}

sealed trait Operation {
  def value: Int
}
object Operation {
  val values = Set(Create, Update, Expire, Remove, Noop)

  def apply(intValue: Int): Operation = {
    values.find(_.value == intValue).getOrElse(Noop)
  }

  case object Create extends Operation { def value = 0 }
  case object Update extends Operation { def value = 1 }
  case object Expire extends Operation { def value = 2 }
  case object Remove extends Operation { def value = 3 }
  case object Noop extends Operation { def value = 3 }
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

case class TierekisteriAssetData(roadNumber: Long,
                                 roadPartNumber: Long,
                                 starMValue: Double,
                                 endMValue: Double,
                                 ktv: Int)

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

  protected def request[T](url: String): Either[T, TierekisteriError] = {
    val request = new HttpGet(url)
    request.addHeader("X-OTH-Authorization", "Basic " + auth.getAuthInBase64)
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

  protected def post(url: String, trMassTransitStop: TierekisteriType, createJson: (TierekisteriType) => StringEntity): Option[TierekisteriError] = {
    val request = new HttpPost(url)
    request.addHeader("X-OTH-Authorization", "Basic " + auth.getAuthInBase64)
    request.setEntity(createJson(trMassTransitStop))
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

  protected def put(url: String, tnMassTransitStop: TierekisteriType, createJson: (TierekisteriType) => StringEntity): Option[TierekisteriError] = {
    val request = new HttpPut(url)
    request.addHeader("X-OTH-Authorization", "Basic " + auth.getAuthInBase64)
    request.setEntity(createJson(tnMassTransitStop))
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
    request.addHeader("X-OTH-Authorization", "Basic " + auth.getAuthInBase64)
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

  //TODO: ignore case sensitive
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

class TierekisteriMassTransitStopClient(_tierekisteriRestApiEndPoint: String, _tierekisteriEnabled: Boolean, _client: CloseableHttpClient) extends TierekisteriClient{

  override def tierekisteriRestApiEndPoint: String = _tierekisteriRestApiEndPoint
  override def tierekisteriEnabled: Boolean = _tierekisteriEnabled
  override def client: CloseableHttpClient = _client
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
  def createMassTransitStop(trMassTransitStop: TierekisteriMassTransitStop): Unit ={
    logger.info("Creating stop %s in Tierekisteri".format(trMassTransitStop.liviId))
    post(serviceUrl, trMassTransitStop, createJson) match {
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

class TierekisteriAssetDataClient(_tierekisteriRestApiEndPoint: String, _tierekisteriEnabled: Boolean, _client: CloseableHttpClient) extends TierekisteriClient {

  override def tierekisteriRestApiEndPoint: String = _tierekisteriRestApiEndPoint
  override def tierekisteriEnabled: Boolean = _tierekisteriEnabled
  override def client: CloseableHttpClient = _client
  type TierekisteriType = TierekisteriAssetData

  private val serviceName = "tietolajit/"
  private val trKTV = "KVL"
  private val trRoadNumber = "TIE"
  private val trRoadPartNumber = "OSA"
  private val trStartMValue = "ETAISYYS"
  private val trEndMValue = "LET"

  private val serviceUrl : String = tierekisteriRestApiEndPoint + serviceName
  private def serviceUrl(assetType: String, roadNumber: Long) : String = serviceUrl + assetType + "/" + roadNumber
  private def serviceUrl(assetType: String, roadNumber: Long, roadPartNumber: Long) : String = serviceUrl + assetType + "/" + roadNumber + "/" + roadPartNumber
  private def serviceUrl(assetType: String, roadNumber: Long, roadPartNumber: Long, startDistance: Int) : String =
    serviceUrl + assetType + "/" + roadNumber + "/" + roadPartNumber + "/" + startDistance
  private def serviceUrl(assetType: String, roadNumber: Long, roadPartNumber: Long, startDistance: Int, endPart: Int, endDistance: Int) : String =
    serviceUrl + assetType + "/" + roadNumber + "/" + roadPartNumber + "/" + startDistance + "/" + endPart + "/" + endDistance

  override def mapFields(data: Map[String, Any]): TierekisteriAssetData = {
    //Mandatory field
    val ktv = convertToInt(getMandatoryFieldValue(data, trKTV)).get
    val roadNumber = convertToLong(getMandatoryFieldValue(data, trRoadNumber)).get
    val roadPartNumber = convertToLong(getMandatoryFieldValue(data, trRoadPartNumber)).get
    val starMValue = convertToLong(getMandatoryFieldValue(data, trStartMValue)).get
    val endMValue = convertToDouble(getMandatoryFieldValue(data, trEndMValue)).get

    TierekisteriAssetData(roadNumber, roadPartNumber, starMValue, endMValue, ktv)
  }

  /**
    * Return all asset data currently active from Tierekisteri
    * Tierekisteri REST API endpoint: GET /trrest/tietolajit/{tietolaji}/{tie}
    *
    * @return
    */
  def fetchActiveAssetData(assetType: String, roadNumber: Long): Seq[TierekisteriAssetData] = {
    request[Map[String,List[Map[String, Any]]]](serviceUrl(assetType, roadNumber)) match {
      case Left(content) => {
        content("Data").map{
          asset => mapFields(asset)
        }
      }
      case Right(null) => Seq()
      case Right(error) => throw new TierekisteriClientException("Tierekisteri error: " + error.content.get("error").get.toString)
    }
  }

  def fetchActiveAssetData(assetType: String, roadNumber: Long, roadPartNumber: Long): Seq[TierekisteriAssetData] = {
    request[List[Map[String, Any]]](serviceUrl(assetType, roadNumber, roadPartNumber)) match {
      case Left(content) =>
        content.map{
          asset => mapFields(asset)
        }
      case Right(null) => Seq()
      case Right(error) => throw new TierekisteriClientException("Tierekisteri error: " + error.content.get("error").get.toString)
    }
  }

  def fetchActiveAssetData(assetType: String, roadNumber: Long, roadPartNumber: Long, startDistance: Int): Seq[TierekisteriAssetData] = {
    request[List[Map[String, Any]]](serviceUrl(assetType, roadNumber, roadPartNumber, startDistance)) match {
      case Left(content) =>
        content.map{
          asset => mapFields(asset)
        }
      case Right(null) => Seq()
      case Right(error) => throw new TierekisteriClientException("Tierekisteri error: " + error.content.get("error").get.toString)
    }
  }

  def fetchActiveAssetData(assetType: String, roadNumber: Long, roadPartNumber: Long, startDistance: Int, endPart: Int, endDistance: Int): Seq[TierekisteriAssetData] = {
    request[List[Map[String, Any]]](serviceUrl(assetType, roadNumber, roadPartNumber, startDistance, endPart, endDistance)) match {
      case Left(content) =>
        content.map{
          asset => mapFields(asset)
        }
      case Right(null) => Seq()
      case Right(error) => throw new TierekisteriClientException("Tierekisteri error: " + error.content.get("error").get.toString)
    }
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