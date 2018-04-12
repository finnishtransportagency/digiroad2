package fi.liikennevirasto.digiroad2.client.viite

import java.text.{ParseException, SimpleDateFormat}
import java.util.Date

import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.client.ErrorMessageConverter
import fi.liikennevirasto.digiroad2.client.tierekisteri.{TierekisteriClientException, TierekisteriError}
import fi.liikennevirasto.digiroad2.dao.RoadAddress
import fi.liikennevirasto.digiroad2.util.Track
import org.apache.http.HttpStatus
import org.apache.http.client.methods.{HttpGet, HttpPost, HttpRequestBase}
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.{CloseableHttpClient, HttpClientBuilder}
import org.json4s.jackson.JsonMethods.parse
import org.json4s.{DefaultFormats, Formats, StreamInput}
import org.slf4j.LoggerFactory

case class ViiteError(content: Map[String, Any], url: String)
class ViiteClientException(response: String) extends RuntimeException(response)

trait ViiteClientOperations {

  type ViiteType

  lazy val logger = LoggerFactory.getLogger(getClass)

  protected val dateFormat = "yyyy-MM-dd"
  protected def restApiEndPoint: String
  protected def serviceName: String
  protected def auth = new ViiteAuthPropertyReader
  protected def client: CloseableHttpClient

  protected implicit val jsonFormats: Formats = DefaultFormats

  protected def serviceUrl = restApiEndPoint + serviceName

  def mapFields(data: Map[String, Any]): Option[ViiteType]

  def addAuthorizationHeader(request: HttpRequestBase) = {
    request.addHeader("Authorization", "Basic " + auth.getAuthInBase64)
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
      Left(parse(StreamInput(response.getEntity.getContent)).values.asInstanceOf[T])
    } catch {
      case e: Exception => Right(ViiteError(Map("error" -> e.getMessage, "content" -> response.getEntity.getContent), url))
    } finally {
      response.close()
    }
  }

  protected def post(url: String, trEntity: ViiteType, createJson: (ViiteType) => StringEntity): Option[TierekisteriError] = {
    val request = new HttpPost(url)
    addAuthorizationHeader(request)
    request.setEntity(createJson(trEntity))
    val response = client.execute(request)
    try {
      val statusCode = response.getStatusLine.getStatusCode
      if (statusCode >= HttpStatus.SC_BAD_REQUEST) {
        return Some(TierekisteriError(Map("error" -> ErrorMessageConverter.convertJSONToError(response)), url))
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

  protected def convertToBoolean(value: Option[String]): Option[Boolean] = {
    try {
      value.map(_.toBoolean)
    } catch {
      case e: NumberFormatException =>
        throw new TierekisteriClientException("Invalid value in response: Int expected, got '%s'".format(value))
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

  protected def getMandatoryFieldValue(data: Map[String, Any], field: String): Option[String] = {
    val fieldValue = getFieldValue(data, field)
    if (fieldValue.isEmpty)
      throw new TierekisteriClientException("Missing mandatory field in response '%s'".format(field))
    fieldValue
  }
}

class SearchViiteClient(vvhRestApiEndPoint: String, httpClient: CloseableHttpClient) extends ViiteClientOperations {

  override type ViiteType = RoadAddress

  override protected def client: CloseableHttpClient = httpClient

  override protected def restApiEndPoint: String = vvhRestApiEndPoint

  override protected def serviceName: String = "/search/"

  def mapFields(data: Map[String, Any]): Option[ViiteType] = {
    val id = convertToLong(getMandatoryFieldValue(data, "id")).get
    val roadNumber = convertToLong(getMandatoryFieldValue(data, "roadNumber")).get
    val roadPartNumber = convertToLong(getMandatoryFieldValue(data, "roadPartNumber")).get
    val trackCode = Track.apply(convertToInt(getMandatoryFieldValue(data, "track")).get)
    val startAddrM = convertToLong(getMandatoryFieldValue(data, "startAddrM")).get
    val endAddrM = convertToLong(getMandatoryFieldValue(data, "endAddrM")).get
    val linkId = convertToLong(getMandatoryFieldValue(data, "linkId")).get
    val startMValue = convertToDouble(getMandatoryFieldValue(data, "startMValue")).get
    val endMValue = convertToDouble(getMandatoryFieldValue(data, "endMValue")).get
    val floating = convertToBoolean(getMandatoryFieldValue(data, "floating")).get
    //TODO lrm position id, discontinuaty, startMValue, endMValue, SideCode, expired, geometry,  can be delete also
    Some(RoadAddress(id, roadNumber, roadPartNumber, trackCode, startAddrM, endAddrM, None, None, linkId, startMValue, endMValue, SideCode.TowardsDigitizing, floating, Seq(), false, None, None, None ))
  }

}
