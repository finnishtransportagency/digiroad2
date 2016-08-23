package fi.liikennevirasto.digiroad2

import org.apache.http.client.methods.{HttpPut, HttpPost, HttpGet}
import org.apache.http.impl.client.HttpClientBuilder
import org.json4s.jackson.JsonMethods._
import org.json4s.{StreamInput, DefaultFormats, Formats}

sealed trait StopType {
  def value: Int
}
object StopType {
  val values = Set(Commuter, LongDistance, LongDistanceAndCommuter, Virtual, Unknown)

  def apply(value: Int): StopType = {
    values.find(_.value == value).getOrElse(Unknown)
  }


  // TODO: Stop type should have values 'kauko', 'paikallis', 'molemmat' or 'virtual', no number values
  case object Commuter extends StopType { def value = 2 }               // paikallis
  case object LongDistance extends StopType { def value = 3 }           // kauko
  case object LongDistanceAndCommuter extends StopType { def value = 4 }// molemmat (paikkallis ja kauko)
  case object Virtual extends StopType { def value = 5 }                // virtuaali
  case object Unknown extends StopType { def value = 99 }               // ei tietoa
}

case class TierekisteriMassTransitStop(nationalId: Long, liViId: String, stopType: StopType, equipment: Map[String, Any] = Map())

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
  private val tnSchedule = "aikataulu"
  private val tnSeat = "penkki"
  private val tnBicycleStand = "pyorateline"
  private val tnElectronicScheduleScreen = "sahkoinen_aikataulunaytto"
  private val tnTrashBin = "roskis"
  private val tnShelter = "katos"
  private val tnAdvertisementShelter = "mainoskatos"
  private val tnEscortOption = "saattomahd"
  private val tnPlatform = "korotus"
  private val tnLightning = "valaistus"
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
      case Left(content) =>

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

  private def extractEquipment(data: Map[String, Any]) = {
    val equipment = data.get(tnEquipment).asInstanceOf[Map[String, Any]]


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
