package fi.liikennevirasto.digiroad2

import org.apache.http.client.methods.{HttpPut, HttpPost, HttpGet}
import org.apache.http.impl.client.HttpClientBuilder
import org.json4s.jackson.JsonMethods._
import org.json4s.{StreamInput, DefaultFormats, Formats}

//TODO add TierekisteriMassTransitStop instance variables
case class TierekisteriMassTransitStop()
case class TierekisteriError(content: Map[String, Any], url: String)

class TierekisteriClient(tierekisteriRestApiEndPoint: String) {
  class TierekisteriClientException(response: String) extends RuntimeException(response)
  protected implicit val jsonFormats: Formats = DefaultFormats

  private val serviceName = "pysakit"

  private def serviceUrl(id: Long) : String = tierekisteriRestApiEndPoint + serviceName + "/" + id

  def fetchMassTransitStop(id: Long): TierekisteriMassTransitStop = {

    request(serviceUrl(id)) match {
      case Left(result) => throw new NotImplementedError
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
