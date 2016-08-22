package fi.liikennevirasto.digiroad2

import org.apache.http.client.methods.{HttpPut, HttpPost, HttpGet}
import org.apache.http.impl.client.HttpClientBuilder
import org.json4s.jackson.JsonMethods._
import org.json4s.{StreamInput, DefaultFormats, Formats}

sealed trait StopType {
  def value: Int
}
object StopType {
  val values = Set(Tram, LocalTransport, LongDistance, Express, Virtual, Unknown)

  def apply(value: Int): StopType = {
    values.find(_.value == value).getOrElse(Unknown)
  }

  //TODO validate those objects
  case object Tram extends StopType { def value = 1 } //not sure Raitiovaunu
  case object LocalTransport extends StopType { def value = 2 }
  case object LongDistance extends StopType { def value = 3 }
  case object Express extends StopType { def value = 4 }
  case object Virtual extends StopType { def value = 5 }
  case object Unknown extends StopType { def value = 99 }
}

case class TierekisteriMassTransitStop(nationalId: Long, liViId: String, stopType: Set[StopType], equipments: Map[String, Any] = Map())
case class TierekisteriError(content: Map[String, Any], url: String)

class TierekisteriClient(tierekisteriRestApiEndPoint: String) {
  class TierekisteriClientException(response: String) extends RuntimeException(response)
  protected implicit val jsonFormats: Formats = DefaultFormats

  private val ServiceName = "pysakit"

  //TODO validate the property names
  private val TnNationalId = "valtakunnallinen_id"
  private val TnRoadLinkId = "tienumero" //Not sure
  private val TnQuestion = "tieosanumero"
  private val TnRoad = "ajorata" //Not sure
  private val TnDistance = "etaisyys"
  private val TnSide = "puoli"
  private val TnStopId = "pysakin_tunnus"
  private val TnNameFi = "nimi_fi"
  private val TnStopType = "pysakin_tyyppi"
  private val TnIsExpress = "pikavuoro"
  private val TnStartDate = "alkupvm"
  private val TnLiViId = "livitunnus"
  private val TnNameSw = "nimi_se"
  private val TnEquipement = "varusteet"
  private val TnSchedule = "aikataulu" //Not sure
  private val TnSeats = "penkki"
  private val TnQuestion2 = "pyorateline"
  private val TnQuestion3 = "sahkoinen_aikataulunaytto"
  private val TnTrash = "roskis"
  private val TnQuestion4 = "katos"
  private val TnQuestion5 = "mainoskatos"
  private val TnQuestion6 = "saattomahd"
  private val TnQuestion7 = "korotus"
  private val TnLight = "valaistus"
  private val TnUser = "kayttajatunnus"

  private def booleanCodeToBoolean: Map[String, Boolean] = Map("on" -> true, "ei" -> false)
  private def booleanToBooleanCodee: Map[Boolean, String] = Map(true -> "on", false -> "ei")

  private def serviceUrl(id: Long) : String = tierekisteriRestApiEndPoint + ServiceName + "/" + id

  def fetchMassTransitStop(id: Long): TierekisteriMassTransitStop = {

    request(serviceUrl(id)) match {
      case Left(content) =>

      case Right(error) => throw new TierekisteriClientException(error.toString)
    }
  }

  def create(tnMassTransitStop: TierekisteriMassTransitStop): Unit ={
    throw new NotImplementedError
  }

  def update(tnMassTransitStop: TierekisteriMassTransitStop): Unit ={
    throw new NotImplementedError
  }

  def delete(tnMassTransitStopId: String): Unit ={
    throw new NotImplementedError
  }

  private def extractEquipement(data: Map[String, Any]) = {
    val equipements = data.get(TnEquipement).asInstanceOf[Map[String, Any]]


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
