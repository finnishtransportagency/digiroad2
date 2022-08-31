package fi.liikennevirasto.digiroad2.client

import java.net.URLEncoder
import java.security.cert.X509Certificate
import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.util._
import fi.liikennevirasto.digiroad2.{Feature, FeatureCollection, Point, Vector3d}
import org.apache.http.{HttpStatus, NameValuePair}
import org.apache.http.client.config.{CookieSpecs, RequestConfig}
import org.apache.http.client.entity.UrlEncodedFormEntity

import javax.net.ssl.{HostnameVerifier, SSLSession, X509TrustManager}
import org.apache.http.client.methods.{HttpGet, HttpPost}
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.{DefaultHttpClient, HttpClientBuilder}
import org.apache.http.message.BasicNameValuePair
import org.apache.http.params.HttpParams
import org.json4s.jackson.JsonMethods.parse
import org.json4s.jackson.Serialization
import org.json4s.{DefaultFormats, Formats, StreamInput}
import org.slf4j.LoggerFactory

import java.util.ArrayList

case class MassQueryParams(identifier: String, point: Point, roadNumber: Option[Long], roadPartNumber: Option[Long])
case class AddrWithIdentifier(identifier: String, roadAddress: RoadAddress)
case class PointWithIdentifier(identifier: String, point: Point)

class VKMClient {
  case class VKMError(content: Map[String, Any], url: String)
  protected implicit val jsonFormats: Formats = DefaultFormats
  private def VkmRoad = "tie"
  private def VkmRoadPart = "osa"
  private def VkmDistance = "etaisyys"
  private def VkmTrackCodes = "ajorata"
  private def VkmTrackCode = "ajorata"
  private def VkmSearchRadius = "sade"
  private def VkmQueryIdentifier = "tunniste"
  private def VkmMunicipalityCode = "kuntakoodi"
  private def NonPedestrianRoadNumbers = "1-62999"
  private def AllRoadNumbers = "1-99999"
  private def DefaultToleranceMeters = 20.0

  private val logger = LoggerFactory.getLogger(getClass)
  private def vkmBaseUrl = Digiroad2Properties.vkmUrl + "/viitekehysmuunnin/"

  def urlParams(paramMap: Map[String, Option[Any]]) = {
    paramMap.filter(entry => entry._2.nonEmpty).map(entry => URLEncoder.encode(entry._1, "UTF-8")
      + "=" + URLEncoder.encode(entry._2.get.toString, "UTF-8")).mkString("&")
  }

  def urlParamsReverse(paramMap: Map[String, Any]) = {
    paramMap.map(entry => URLEncoder.encode(entry._1, "UTF-8")
      + "=" + URLEncoder.encode(entry._2.toString, "UTF-8")).mkString("&")
  }

  private def postRequest(url: String, json: String): Either[FeatureCollection, VKMError] = {
    val nvps = new ArrayList[NameValuePair]()
    nvps.add(new BasicNameValuePair("json", json))

    val request = new HttpPost(url)
    request.addHeader("Content-type", "application/x-www-form-urlencoded; charset=UTF-8")
    request.addHeader("X-API-Key", Digiroad2Properties.vkmApiKey)
    request.setEntity(new UrlEncodedFormEntity(nvps, "utf-8"))

    val client = HttpClientBuilder.create().setDefaultRequestConfig(RequestConfig.custom()
      .setCookieSpec(CookieSpecs.STANDARD).build()).build()

    val response = client.execute(request)
    try {
      if (response.getStatusLine.getStatusCode >= HttpStatus.SC_BAD_REQUEST)
        return Right(VKMError(Map("error" -> "Request returned HTTP Error %d".format(response.getStatusLine.getStatusCode)), url))
      val aux = response.getEntity.getContent
      val content: FeatureCollection = parse(StreamInput(aux)).extract[FeatureCollection]
      val contentFiltered = content.copy(features = content.features.filterNot(_.properties.contains("virheet")))
      Left(contentFiltered)
    } catch {
      case e: Exception => Right(VKMError(Map("error" -> e.getMessage), url))
    } finally {
      response.close()
    }
  }

  private def request(url: String): Either[FeatureCollection, VKMError] = {
    val request = new HttpGet(url)
    request.addHeader("X-API-Key", Digiroad2Properties.vkmApiKey)
    val client = HttpClientBuilder.create() .setDefaultRequestConfig(RequestConfig.custom()
      .setCookieSpec(CookieSpecs.STANDARD).build()).build()
    val response = client.execute(request)
    try {
      if (response.getStatusLine.getStatusCode >= 400)
        return Right(VKMError(Map("error" -> "Request returned HTTP Error %d".format(response.getStatusLine.getStatusCode)), url))
      val aux = response.getEntity.getContent
      val content:FeatureCollection = parse(StreamInput(aux)).extract[FeatureCollection]
      if(content.features.head.properties.contains("virheet")){
        return Right(VKMError(Map("error" -> content.features.head.properties("virheet")), url))
      }
      Left(content)
    } catch {
      case e: Exception => Right(VKMError(Map("error" -> e.getMessage), url))
    } finally {
      response.close()
    }
  }

  private def roadNumberInterval(pedestrianIncluded: Option[Boolean], road: Option[Int]) = {
    if (road.nonEmpty)
      road.map(_.toString)
    else if (pedestrianIncluded.getOrElse(false))
      Option(AllRoadNumbers)
    else
      Option(NonPedestrianRoadNumbers)
  }

  object TrustAll extends X509TrustManager {
    val getAcceptedIssuers = null

    def checkClientTrusted(x509Certificates: Array[X509Certificate], s: String) = {}

    def checkServerTrusted(x509Certificates: Array[X509Certificate], s: String) = {}
  }

  // Verifies all host names by simply returning true.
  object VerifiesAllHostNames extends HostnameVerifier {
    def verify(s: String, sslSession: SSLSession) = true
  }

  def coordToAddressMassQuery(coords: Seq[MassQueryParams]): Map[String,RoadAddress] = {
    val params = coords.map(coord => Map(
      VkmQueryIdentifier -> coord.identifier,
      VkmRoad -> coord.roadNumber,
      VkmRoadPart -> coord.roadPartNumber,
      "x" -> coord.point.x,
      "y" -> coord.point.y
    ))
    val jsonValue = Serialization.write(params)
    val url = vkmBaseUrl + "muunna/"
    val response = postRequest(url, jsonValue)

    val result = response match {
      case Left(address) => address.features.map(feature => mapMassQueryFields(feature))
      case Right(error) => throw new RoadAddressException(error.toString)
    }
    result.flatten.flatten.toMap
  }

  def coordToAddress(coord: Point, road: Option[Int] = None, roadPart: Option[Int] = None,
                     distance: Option[Int] = None, track: Option[Track] = None, searchDistance: Option[Double] = None,
                     includePedestrian: Option[Boolean] = Option(false)) = {

    val params = Map(
            VkmRoad -> road,
            VkmRoadPart -> roadPart,
            VkmTrackCodes -> track.map(_.value),
            "x" -> Option(coord.x),
            "y" -> Option(coord.y),
            VkmSearchRadius -> searchDistance //Default in new VKM is 100
      )

    request(vkmBaseUrl + "muunna?sade=500&" + urlParams(params)) match {
      case Left(address) => mapFields(address.features.head)
      case Right(error) => throw new RoadAddressException(error.toString)
    }
  }

  def coordsToAddresses(coords: Seq[Point], road: Option[Int] = None, roadPart: Option[Int] = None,
                        distance: Option[Int] = None, track: Option[Track] = None, searchDistance: Option[Double] = None,
                        includePedestrian: Option[Boolean] = Option(false)) : Seq[RoadAddress] = {

    coords.map( coord => coordToAddress(coord, road, roadPart, distance, track, searchDistance, includePedestrian) )
  }

  def addressToCoordsMassQuery(addresses: Seq[AddrWithIdentifier]): Seq[PointWithIdentifier] = {
    val params = addresses.map(roadAddress => {
      Map(
        VkmQueryIdentifier -> roadAddress.identifier,
        VkmRoad -> roadAddress.roadAddress.road,
        VkmRoadPart -> roadAddress.roadAddress.roadPart,
        VkmTrackCodes -> roadAddress.roadAddress.track.value,
        VkmDistance -> roadAddress.roadAddress.addrM
      )
    })
    val jsonValue = Serialization.write(params)
    val url = vkmBaseUrl + "muunna/"
    val response = postRequest(url, jsonValue)

    val result = response match {
      case Left(address) => mapCoordinatesWithIdentifier(address)
      case Right(error) => throw new RoadAddressException(error.toString)
    }
    result
  }

  def addressToCoords(roadAddress: RoadAddress) : Seq[Point] = {
    val params = Map(
      VkmRoad -> roadAddress.road,
      VkmRoadPart -> roadAddress.roadPart,
      VkmTrackCodes -> roadAddress.track.value,
      VkmDistance -> roadAddress.addrM
    )

   request(vkmBaseUrl + "muunna?" + urlParamsReverse(params)) match  {
      case Left(addressData) =>
        if (addressData.features.nonEmpty)
          mapCoordinates(addressData)
        else
          throw new RoadAddressException("empty response")

      case Right(error) =>
        throw new RoadAddressException(error.toString)
    }
  }

  /**
    * Resolve side code as well as road address
    *
    * @param coord Coordinates of location
    * @param heading Geographical heading in degrees (North = 0, West = 90, ...)
    * @param road Road we want to find (optional)
    * @param roadPart Road part we want to find (optional)
    * @param sideCode The side code
    */
  def resolveAddressAndLocation(coord: Point, heading: Int, sideCode: SideCode, road: Option[Int] = None,
                                roadPart: Option[Int] = None,
                                includePedestrian: Option[Boolean] = Option(false)): (RoadAddress, RoadSide) = {
    if (road.isEmpty || roadPart.isEmpty) {
      val roadAddress = coordToAddress(coord, road, roadPart, includePedestrian = includePedestrian)
      resolveAddressAndLocation(coord, heading, sideCode, Option(roadAddress.road), Option(roadAddress.roadPart))
    } else {
      val degrees = sideCode match{
        case SideCode.AgainstDigitizing => 90-heading+180
        case _ => 90-heading
      }
      val rad = degrees * Math.PI/180.0
      val stepVector = Vector3d(3*Math.cos(rad), 3*Math.sin(rad), 0.0)
      val behind = coord - stepVector
      val front = coord + stepVector
      val addresses = coordsToAddresses(Seq(behind, coord, front), road, roadPart, includePedestrian = includePedestrian)
      val mValues = addresses.map(ra => ra.addrM)
      val (first, second, third) = (mValues(0), mValues(1), mValues(2))
      if (first <= second && second <= third && first != third) {
        (addresses(1), RoadSide.Right)
      } else if (first >= second && second >= third && first != third) {
        (addresses(1), RoadSide.Left)
      } else {
        (addresses(1), RoadSide.Unknown)
      }
    }
  }

  private def mapMassQueryFields(data: Feature): Option[Map[String, RoadAddress]] = {
    try{
      val municipalityCode = data.properties.get(VkmMunicipalityCode)
      val road = validateAndConvertToInt(VkmRoad, data.properties)
      val roadPart = validateAndConvertToInt(VkmRoadPart, data.properties)
      val track = validateAndConvertToInt(VkmTrackCode, data.properties)
      val mValue = validateAndConvertToInt(VkmDistance, data.properties)
      val queryIdentifier = data.properties(VkmQueryIdentifier)
      if (Track.apply(track).eq(Track.Unknown)) {
        throw new RoadAddressException("Invalid value for Track (%s): %d".format(VkmTrackCode, track))
      }
      Some(Map(queryIdentifier -> RoadAddress(municipalityCode, road, roadPart, Track.apply(track), mValue)))
    }
    catch {
      case rae: RoadAddressException =>
        logger.error("Error mapping to VKM data to road address: " + rae.getMessage)
        None
    }
  }

  private def mapFields(data: Feature) = {
    val municipalityCode = data.properties.get(VkmMunicipalityCode)
    val road = validateAndConvertToInt(VkmRoad, data.properties)
    val roadPart = validateAndConvertToInt(VkmRoadPart, data.properties)
    val track = validateAndConvertToInt(VkmTrackCode, data.properties)
    val mValue = validateAndConvertToInt(VkmDistance, data.properties)
    if (Track.apply(track).eq(Track.Unknown)) {
      throw new RoadAddressException("Invalid value for Track (%s): %d".format(VkmTrackCode, track))
    }
    RoadAddress(municipalityCode, road, roadPart, Track.apply(track), mValue)
  }

  private def mapCoordinatesWithIdentifier(data: FeatureCollection): Seq[PointWithIdentifier] = {

    try {
      data.features.map {
        addr =>
          val queryIdentifier = addr.properties(VkmQueryIdentifier)
          val x = addr.properties("x").toDouble
          val y = addr.properties("y").toDouble
          PointWithIdentifier(queryIdentifier, Point(x,y))
      }
    } catch {
      case ex: Exception => throw new RoadAddressException("Could not convert response from VKM: %s".format(ex.getMessage))
    }
  }

  private def mapCoordinates(data: FeatureCollection) = {

    try {
      data.features.map {
        addr =>
          val x = addr.properties("x").toDouble
          val y = addr.properties("y").toDouble
          Point(x,y)
      }
    } catch {
      case ex: Exception => throw new RoadAddressException("Could not convert response from VKM: %s".format(ex.getMessage))
    }
  }

  private def validateAndConvertToInt(fieldName: String, map: Map[String, String]) = {
    def value = map.get(fieldName)
    if (value.isEmpty) {
      throw new RoadAddressException(
        "Missing mandatory field in response: %s".format(
          fieldName))
    }
    try {
      value.get.toInt
    } catch {
      case e: NumberFormatException =>
        throw new RoadAddressException("Invalid value in response: %s, Int expected, got '%s'".format(fieldName, value.get))
    }
  }

  private def convertToDouble(value: Option[Any]): Option[Double] = {
    value.map {
      case x: Object =>
        try {
          x.toString.toDouble
        } catch {
          case e: NumberFormatException =>
            throw new RoadAddressException("Invalid value in response: Double expected, got '%s'".format(x))
        }
      case _ => throw new RoadAddressException("Invalid value in response: Double expected, got '%s'".format(value.get))
    }
  }
}
