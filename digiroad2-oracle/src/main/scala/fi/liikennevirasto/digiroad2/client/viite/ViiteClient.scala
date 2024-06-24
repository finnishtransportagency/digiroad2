package fi.liikennevirasto.digiroad2.client.viite

import fi.liikennevirasto.digiroad2.client.ErrorMessageConverter
import fi.liikennevirasto.digiroad2.util.Digiroad2Properties
import org.apache.http.HttpStatus
import org.apache.http.client.methods.{HttpGet, HttpPost, HttpRequestBase}
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.CloseableHttpClient
import org.json4s.jackson.JsonMethods.parse
import org.json4s.{DefaultFormats, Formats, StreamInput}
import org.slf4j.LoggerFactory

import java.text.{ParseException, SimpleDateFormat}
import java.util.Date

case class ViiteError(content: Map[String, Any], url: String)
class ViiteClientException(response: String) extends RuntimeException(response)

trait ViiteClientOperations {

  type ViiteType

  lazy val logger = LoggerFactory.getLogger(getClass)

  protected val dateFormat = "yyyy-MM-dd"
  protected def restApiEndPoint: String
  protected def serviceName: String
  protected def client: CloseableHttpClient

  protected implicit val jsonFormats: Formats = DefaultFormats

  protected def serviceUrl = restApiEndPoint + serviceName

  protected def viiteApiKey = Digiroad2Properties.viiteApiKey

  protected def mapFields(data: Map[String, Any]): Option[ViiteType]
  protected def mapFields[A](data:A): Option[List[ViiteType]]

  def addAuthorizationHeader(request: HttpRequestBase) = {
    request.addHeader("X-API-Key", viiteApiKey)
  }

  protected def get[T](url: String): Either[T, ViiteError] = {
    val request = new HttpGet(url)
    addAuthorizationHeader(request)
    val response = client.execute(request)
    try {
      val statusCode = response.getStatusLine.getStatusCode
      if (statusCode == HttpStatus.SC_NOT_FOUND) {
        return Right(null)
      } else if (statusCode >= HttpStatus.SC_BAD_REQUEST) {
        return Right(ViiteError(Map("error" -> ErrorMessageConverter.convertJSONToError(response), "content" -> response.getEntity.getContent), url))
      }
      Left(parse(StreamInput(response.getEntity.getContent).stream).values.asInstanceOf[T])
    } catch {
      case e: Exception => Right(ViiteError(Map("error" -> e.getMessage, "content" -> response.getEntity.getContent), url))
    } finally {
      response.close()
    }
  }

  protected def post[T, O](url: String, trEntity: T, createJson: (T) => StringEntity): Either[O, ViiteError] = {
    val request = new HttpPost(url)
    addAuthorizationHeader(request)
    request.setEntity(createJson(trEntity))
    val response = client.execute(request)
    try {
      val statusCode = response.getStatusLine.getStatusCode
      if (statusCode == HttpStatus.SC_NOT_FOUND) {
        return Right(null)
      } else if (statusCode >= HttpStatus.SC_BAD_REQUEST) {
        return Right(ViiteError(Map("error" -> ErrorMessageConverter.convertJSONToError(response), "content" -> response.getEntity.getContent), url))
      }
      Left(parse(StreamInput(response.getEntity.getContent).stream).values.asInstanceOf[O])
    } catch {
      case e: Exception => Right(ViiteError(Map("error" -> e.getMessage, "content" -> response.getEntity.getContent), url))
    } finally {
      response.close()
    }
  }

  protected def convertToLong(value: Option[String]): Option[Long] = {
    try {
      value.map(_.toLong)
    } catch {
      case e: NumberFormatException =>
        throw new ViiteClientException("Invalid value in response: Long expected, got '%s'".format(value))
    }
  }

  protected def convertToDouble(value: Option[String]): Option[Double] = {
    try {
      value.map(_.toDouble)
    } catch {
      case e: NumberFormatException =>
        throw new ViiteClientException("Invalid value in response: Double expected, got '%s'".format(value))
    }
  }

  protected def convertToInt(value: Option[String]): Option[Int] = {
    try {
      value.map(_.toInt)
    } catch {
      case e: NumberFormatException =>
        throw new ViiteClientException("Invalid value in response: Int expected, got '%s'".format(value))
    }
  }

  protected def convertToDate(value: Option[String]): Option[Date] = {
    try {
      value.map(dv => new SimpleDateFormat(dateFormat).parse(dv))
    } catch {
      case e: ParseException =>
        throw new ViiteClientException("Invalid value in response: Date expected, got '%s'".format(value))
    }
  }

  protected def convertDateToString(date: Option[Date]): Option[String] = {
    date.map(dv => convertDateToString(dv))
  }

  protected def convertDateToString(date: Date): String = {
    new SimpleDateFormat(dateFormat).format(date)
  }

  protected def convertToBoolean(value: Option[String]): Option[Boolean] = {
    try {
      value.map(_.toBoolean)
    } catch {
      case e: NumberFormatException =>
        throw new ViiteClientException("Invalid value in response: Int expected, got '%s'".format(value))
    }
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

  protected def getFieldGeneric[A](data: Map[String, Any], field: String): Option[A] = {
    try {
      data.get(field) match {
        case Some(value) => Some(value.asInstanceOf[A])
        case _ => None
      }
    } catch {
      case _: NullPointerException => None
    }
  }
  
  protected def getMandatoryFieldValue(data: Map[String, Any], field: String): Option[String] = {
    val fieldValue = getFieldValue(data, field)
    if (fieldValue.isEmpty)
      throw new ViiteClientException("Missing mandatory field in response '%s'".format(field))
    fieldValue
  }
}