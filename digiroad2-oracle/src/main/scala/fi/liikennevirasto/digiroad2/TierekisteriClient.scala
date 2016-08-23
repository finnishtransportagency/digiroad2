package fi.liikennevirasto.digiroad2

import java.util.Date

import fi.liikennevirasto.digiroad2.asset.{Property, PropertyValue}
import fi.liikennevirasto.digiroad2.util.{RoadAddress, RoadSide, Track}
import org.apache.http.client.methods.{HttpGet, HttpPost, HttpPut}
import org.apache.http.impl.client.HttpClientBuilder
import org.json4s.jackson.JsonMethods._
import org.json4s.{DefaultFormats, Formats, StreamInput}

sealed trait StopType {
  def value: String
}
object StopType {
  val values = Set(Commuter, LongDistance, Combined, Virtual, Unknown)

  def apply(value: String): StopType = {
    values.find(_.value == value).getOrElse(Unknown)
  }

  case object Commuter extends StopType { def value = "paikallis" }
  case object LongDistance extends StopType { def value = "kauko" }
  case object Combined extends StopType { def value = "molemmat" }
  case object Virtual extends StopType { def value = "virtuaali" }
  case object Unknown extends StopType { def value = "tuntematon" }  // Should not be passed on interface
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

  private val serviceName = "pysakit"

  //TODO validate the property names
  private val tnNationalId = "valtakunnallinen_id"
  private val tnRoadNumber = "tienumero" //Not sure
  private val tnRoadPartNumber = "tieosanumero"
  private val tnLane = "ajorata" //Not sure
  private val tnDistance = "etaisyys"
  private val tnSide = "puoli"
  private val tnStopId = "pysakin_tunnus"
  private val tnNameFi = "nimi_fi"
  private val tnStopType = "pysakin_tyyppi"
  private val tnIsExpress = "pikavuoro"
  private val tnStartDate = "alkupvm"
  private val tnLiviId = "livitunnus"
  private val tnNameSe = "nimi_se"
  private val tnEquipment = "varusteet"
  private val tnUser = "kayttajatunnus"

  private def booleanCodeToBoolean: Map[String, Boolean] = Map("on" -> true, "ei" -> false)
  private def booleanToBooleanCode: Map[Boolean, String] = Map(true -> "on", false -> "ei")

  private def serviceUrl(id: Long) : String = tierekisteriRestApiEndPoint + serviceName + "/" + id

  // TODO: Method for returning all active mass transit stops

  /**
    * Returns mass transit stop data by livitunnus.
    * Tierekisteri GET /pysakit/{livitunn}
    *
    * @param id   livitunnus
    * @return
    */
  def fetchMassTransitStop(id: Long): TierekisteriMassTransitStop = {

    request(serviceUrl(id)) match {
      case Left(content) => throw new NotImplementedError

      case Right(error) => throw new TierekisteriClientException(error.toString)
    }
  }

  /**
    * Creates a new mass transit stop to Tierekisteri from OTH document.
    * Tierekisteri POST /pysakit/
    *
    * @param tnMassTransitStop
    */
  def create(tnMassTransitStop: TierekisteriMassTransitStop): Unit ={
    throw new NotImplementedError
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

  private def request(url: String): Either[Map[String, Any], TierekisteriError] = {
    val request = new HttpGet(url)
    val client = HttpClientBuilder.create().build()
    val response = client.execute(request)

    try {
      val statusCode = response.getStatusLine.getStatusCode
      if (statusCode >= 400)
        return Right(TierekisteriError(Map("error" -> "Request returned HTTP Error %d".format(statusCode)), url))
      val content: Map[String, Any] = parse(StreamInput(response.getEntity.getContent)).values.asInstanceOf[Map[String, Any]]
      Left(content)
    } catch {
      case e: Exception => Right(TierekisteriError(Map("error" -> e.getMessage), url))
    } finally {
      response.close()
    }
  }

  //TODO change the parameters as we need
  private def createFormPost() ={
    throw new NotImplementedError
  }

  //TODO change the parameters and the return as we need
  private def post(url: String): Either[Map[String, Any], TierekisteriError] = {
    val request = new HttpPost(url)
    request.setEntity(createFormPost())
    val client = HttpClientBuilder.create().build()
    val response = client.execute(request)
    try {
     throw new NotImplementedError
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
    request.setEntity(createFormPost())
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

  private val tnLiviId = "livitunnus"

  // TODO: Or variable type: persisted mass transit stop?
  def toTierekisteriMassTransitStop(massTransitStop: MassTransitStopWithProperties): TierekisteriMassTransitStop = {
    TierekisteriMassTransitStop(massTransitStop.nationalId, findLiViId(massTransitStop.propertyData).getOrElse(""),
      RoadAddress(None, 1,1,Track.Combined,1,None), RoadSide.Right, findStopType(massTransitStop.stopTypes), false,
      mapEquipments(massTransitStop.propertyData), "", "", "", "", new Date, new Date, new Date)
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
    properties.find(p => p.publicId.equals(tnLiviId)).map(_.values.head.propertyValue)
  }

  def fromTierekisteriMassTransitStop(massTransitStop: TierekisteriMassTransitStop): MassTransitStopWithProperties = {
    MassTransitStopWithProperties(1, 1, Seq(), 0.0, 0.0, None, None, None, false, Seq())
  }
}