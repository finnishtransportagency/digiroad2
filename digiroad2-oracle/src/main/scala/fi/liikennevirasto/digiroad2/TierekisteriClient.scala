package fi.liikennevirasto.digiroad2

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date

import org.apache.http.message.BasicNameValuePair
import fi.liikennevirasto.digiroad2.asset.{Property, PropertyValue}
import fi.liikennevirasto.digiroad2.util.{RoadAddress, RoadSide, Track}
import org.apache.http.NameValuePair
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.{HttpGet, HttpPost, HttpPut}
import org.apache.http.entity.{ContentType, StringEntity}
import org.apache.http.impl.client.HttpClientBuilder
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization
import org.json4s.{DefaultFormats, Formats, StreamInput}

sealed trait StopType {
  def value: String
  def propertyValues: Set[Int]
}
object StopType {
  val values = Set(Commuter, LongDistance, Combined, Virtual, Unknown)

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

sealed trait Existence {
  def value: String
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

  case object Yes extends Existence { def value = "on" }
  case object No extends Existence { def value = "ei" }
  case object Unknown extends Existence { def value = "ei_tietoa" }
}

sealed trait Equipment {
  def value: String
  def publicId: String
}
object Equipment {
  val values = Set(Timetable, TrashBin, BikeStand, Lighting, Seat, Roof, RoofMaintainedByAdvertiser, ElectronicTimetables, CarParkForTakingPassengers, RaisedBusStop)

  def apply(value: String): Equipment = {
    values.find(_.value == value).getOrElse(Unknown)
  }

  def fromPublicId(value: String): Equipment = {
    values.find(_.publicId == value).getOrElse(Unknown)
  }

  case object Timetable extends Equipment { def value = "aikataulu"; def publicId = "aikataulu"; }
  case object TrashBin extends Equipment { def value = "roskis"; def publicId = "roska-astia"; }
  case object BikeStand extends Equipment { def value = "pyorateline"; def publicId = "pyorateline"; }
  case object Lighting extends Equipment { def value = "valaistus"; def publicId = "valaistus"; }
  case object Seat extends Equipment { def value = "penkki"; def publicId = "penkki"; }
  case object Roof extends Equipment { def value = "katos"; def publicId = "katos"; }
  case object RoofMaintainedByAdvertiser extends Equipment { def value = "mainoskatos"; def publicId = "mainoskatos"; }
  case object ElectronicTimetables extends Equipment { def value = "sahk_aikataulu"; def publicId = "sahkoinen_aikataulunaytto"; }
  case object CarParkForTakingPassengers extends Equipment { def value = "saattomahd"; def publicId = "saattomahdollisuus_henkiloautolla"; }
  case object RaisedBusStop extends Equipment { def value = "korotus"; def publicId = "roska-astia"; }
  case object Unknown extends Equipment { def value = "UNKNOWN"; def publicId = "tuntematon"; }
}

case class TierekisteriMassTransitStop(nationalId: Long, liViId: String, roadAddress: RoadAddress,
                                       roadSide: RoadSide, stopType: StopType, express: Boolean,
                                       equipments: Map[Equipment, Existence] = Map(),
                                       stopCode: String, nameFi: String, nameSe: String, modifiedBy: String,
                                       operatingFrom: Date, operatingTo: Date, removalDate: Date)

case class TierekisteriError(content: Map[String, Any], url: String)

/**
  * Utility for using Tierekisteri mass transit stop interface in OTH.
  *
  * @param tierekisteriRestApiEndPoint
  */
class TierekisteriClient(tierekisteriRestApiEndPoint: String) {
  class TierekisteriClientException(response: String) extends RuntimeException(response)
  protected implicit val jsonFormats: Formats = DefaultFormats

  private val dateFormat = "yyyy-MM-dd"
  private val serviceName = "pysakit"

  private val trNationalId = "valtakunnallinen_id"
  private val trRoadNumber = "tienumero"
  private val trRoadPartNumber = "tieosanumero"
  private val trLane = "ajorata"
  private val trDistance = "etaisyys"
  private val trSide = "puoli"
  private val trStopId = "pysakin_tunnus"
  private val trNameFi = "nimi_fi"
  private val trStopType = "pysakin_tyyppi"
  private val trIsExpress = "pikavuoro"
  private val trStartDate = "alkupvm"
  private val trEndDate = "loppupvm"
  private val trRemovalDate = "lakkautuspvm"
  private val trLiviId = "livitunnus"
  private val trNameSe = "nimi_se"
  private val trEquipment = "varusteet"
  private val trUser = "kayttajatunnus"

  private def serviceUrl() : String = tierekisteriRestApiEndPoint + serviceName
  private def serviceUrl(id: Long) : String = serviceUrl + "/" + id

  /**
    * Returns all active mass transit stops.
    * Tierekisteri GET /pysakit/
    *
    * @return
    */
  def fetchActiveMassTransitStops(): Seq[TierekisteriMassTransitStop] = {
    request[List[Map[String, Any]]](serviceUrl) match {
      case Left(content) =>
        content.map{
          asset =>
            null
        }
      case Right(error) => throw new TierekisteriClientException(error.toString)
    }
  }

  private def convertToInt(value: Option[Any]): Option[Int] = {
    value.map {
      case x: Object =>
        try {
          x.toString.toInt
        } catch {
          case e: NumberFormatException =>
            throw new TierekisteriClientException("Invalid value in response: Int expected, got '%s'".format(x))
        }
      case _ => throw new TierekisteriClientException("Invalid value in response: Int expected, got '%s'".format(value.get))
    }
  }

  private def convertToDouble(value: Option[Any]): Option[Double] = {
    value.map {
      case x: Object =>
        try {
          x.toString.toDouble
        } catch {
          case e: NumberFormatException =>
            throw new TierekisteriClientException("Invalid value in response: Double expected, got '%s'".format(x))
        }
      case _ => throw new TierekisteriClientException("Invalid value in response: Double expected, got '%s'".format(value.get))
    }
  }

  private def convertToDate(value: Option[Any]): Option[Date] = {
    value.map {
      case x: Object =>
        try {
          new SimpleDateFormat(dateFormat).parse(x.toString)
        } catch {
          case e: ParseException =>
            throw new TierekisteriClientException("Invalid value in response: Date expected, got '%s'".format(x))
        }
      case _ => throw new TierekisteriClientException("Invalid value in response: Date expected, got '%s'".format(value.get))
    }
  }

  private def convertDateToString(date: Date): String = {
    new SimpleDateFormat(dateFormat).format(date);
  }

  private def mapFields(): Unit ={

  }

  /**
    * Returns mass transit stop data by livitunnus.
    * Tierekisteri GET /pysakit/{livitunn}
    *
    * @param id   livitunnus
    * @return
    */
  def fetchMassTransitStop(id: Long): TierekisteriMassTransitStop = {

    request[Map[String, Any]](serviceUrl(id)) match {
      case Left(content) => throw new NotImplementedError

      case Right(error) => throw new TierekisteriClientException(error.toString)
    }
  }

  /**
    * Creates a new mass transit stop to Tierekisteri from OTH document.
    * Tierekisteri POST /pysakit/
    *
    * @param trMassTransitStop
    */
  def createMassTransitStop(url: String, trMassTransitStop: TierekisteriMassTransitStop): Unit ={
    post(url, trMassTransitStop) match {
      case Some(error) => throw new TierekisteriClientException(error.toString)
      case _ => ; // do nothing
    }
  }

  /**
    * Updates mass transit stop data and ends it, if there is valid to date in OTH JSON document.
    * Tierekisteri PUT /pysakit/{livitunn}
    *
    * @param tnMassTransitStop
    */
  def update(tnMassTransitStop: TierekisteriMassTransitStop): Unit ={
    throw new NotImplementedError
  }

  /**
    * Deletes mass transit stop from Tierekisteri by livitunnus. Used for
    *
    * @param tnMassTransitStopId
    */
  def delete(tnMassTransitStopId: String): Unit ={
    throw new NotImplementedError
  }

  private def request[T](url: String): Either[T, TierekisteriError] = {
    val request = new HttpGet(url)
    val client = HttpClientBuilder.create().build()
    val response = client.execute(request)

    try {
      val statusCode = response.getStatusLine.getStatusCode
      if (statusCode >= 400)
        return Right(TierekisteriError(Map("error" -> "Request returned HTTP Error %d".format(statusCode)), url))
      Left(parse(StreamInput(response.getEntity.getContent)).values.asInstanceOf[T])
    } catch {
      case e: Exception => Right(TierekisteriError(Map("error" -> e.getMessage), url))
    } finally {
      response.close()
    }
  }

  private def createJson(trMassTransitStop: TierekisteriMassTransitStop) = {

    val jsonObj = Map(
      trNationalId -> trMassTransitStop.nationalId,
      trLiviId -> trMassTransitStop.liViId,
      trRoadNumber -> trMassTransitStop.roadAddress.road,
      trRoadPartNumber -> trMassTransitStop.roadAddress.roadPart,
      trSide -> trMassTransitStop.roadSide.value,
      trLane -> trMassTransitStop.roadAddress.track.value,
      trDistance -> trMassTransitStop.roadAddress.mValue,
      trStopId -> trMassTransitStop.stopCode,
      trIsExpress -> trMassTransitStop.express,
      trNameFi -> trMassTransitStop.nameFi,
      trNameSe -> trMassTransitStop.nameSe,
      trUser -> trMassTransitStop.modifiedBy,
      trStartDate -> convertDateToString(trMassTransitStop.operatingFrom),
      trEndDate -> convertDateToString(trMassTransitStop.operatingTo),
      trRemovalDate -> convertDateToString(trMassTransitStop.removalDate),
      trEquipment -> trMassTransitStop.equipments.map{
        case (equipment, existence) =>
          equipment.value -> existence.value
      }
    )

      if(trMassTransitStop.stopType == StopType.Unknown)
        jsonObj++trStopType -> trMassTransitStop.stopType

    new StringEntity(Serialization.write(jsonObj), ContentType.APPLICATION_JSON)
  }

  private def post(url: String, trMassTransitStop: TierekisteriMassTransitStop): Option[TierekisteriError] = {
    val request = new HttpPost(url)
    request.setEntity(createJson(trMassTransitStop))
    val client = HttpClientBuilder.create().build()
    val response = client.execute(request)
    try {
      val statusCode = response.getStatusLine.getStatusCode
      if (statusCode >= 400)
        return Some(TierekisteriError(Map("error" -> "Request returned HTTP Error %d".format(statusCode)), url))
     None
    } catch {
      case e: Exception => Some(TierekisteriError(Map("error" -> e.getMessage), url))
    } finally {
      response.close()
    }
  }

  //TODO change the parameters as we need
  private def createFormPut() ={
    throw new NotImplementedError
  }

  //TODO change the parameters and the return as we need
  private def put(url: String): Either[Map[String, Any], TierekisteriError] = {
    val request = new HttpPut(url)
    request.setEntity(createFormPut())
    val client = HttpClientBuilder.create().build()
    val response = client.execute(request)
    try {
      throw new NotImplementedError
    } finally {
      response.close()
    }
  }
}

/**
  * A class to transform data between the interface bus stop format and OTH internal bus stop format
  */
class TierekisteriBusStopMarshaller {

  private val liviIdPublicId = "yllapitajan_koodi"
  private val expressPropertyValue = 4

  // TODO: Or variable type: persisted mass transit stop?
  def toTierekisteriMassTransitStop(massTransitStop: MassTransitStopWithProperties): TierekisteriMassTransitStop = {
    TierekisteriMassTransitStop(massTransitStop.nationalId, findLiViId(massTransitStop.propertyData).getOrElse(""),
      RoadAddress(None, 1,1,Track.Combined,1,None), RoadSide.Right, findStopType(massTransitStop.stopTypes),
      massTransitStop.stopTypes.contains(expressPropertyValue), mapEquipments(massTransitStop.propertyData),
      "", "", "", "", new Date, new Date, new Date)
  }

  // TODO: Implementation
  private def findStopType(stopTypes: Seq[Int]): StopType = {
    StopType.Unknown
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
    properties.find(p => p.publicId.equals(liviIdPublicId)).map(_.values.head.propertyValue)
  }

  // TODO: Implementation
  def fromTierekisteriMassTransitStop(massTransitStop: TierekisteriMassTransitStop): MassTransitStopWithProperties = {
    MassTransitStopWithProperties(1, 1, Seq(), 0.0, 0.0, None, None, None, false, Seq())
  }
}