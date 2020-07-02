package fi.liikennevirasto.digiroad2.util

import java.net.URLEncoder
import java.security.cert.X509Certificate
import java.util.Properties
import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.dao.{RoadAddress => RoadAddressDTO}
import fi.liikennevirasto.digiroad2.service.RoadAddressService
import fi.liikennevirasto.digiroad2.{Point, Vector3d}
import javax.net.ssl.{HostnameVerifier, HttpsURLConnection, SSLContext, SSLSession, TrustManager, X509TrustManager}
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
import org.json4s._
import org.json4s.jackson.JsonMethods._

/**
  * A road consists of 1-2 tracks (fi: "ajorata"). 2 tracks are separated by a fence or grass for example.
  * Left and Right are relative to the advancing direction (direction of growing m values)
  */
sealed trait Track {
  def value: Int
}
object Track {
  val values = Set(Combined, RightSide, LeftSide, Unknown)

  def apply(intValue: Int): Track = {
    values.find(_.value == intValue).getOrElse(Unknown)
  }

  /**
    * Switch left to right and vice versa
    * @param track Track value to switch
    * @return
    */
  def switch(track: Track) = {
    track match {
      case RightSide => LeftSide
      case LeftSide => RightSide
      case _ => track
    }
  }

  case object Combined extends Track { def value = 0 }
  case object RightSide extends Track { def value = 1 }
  case object LeftSide extends Track { def value = 2 }
  case object Unknown extends Track { def value = 99 }
}

/**
  * Road Side (fi: "puoli") tells the relation of an object and the track. For example, the side
  * where the bus stop is.
  */
sealed trait RoadSide {
  def value: Int
}
object RoadSide {
  val values = Set(Right, Left, Between, End, Middle, Across, Unknown)

  def apply(intValue: Int): RoadSide = {
    values.find(_.value == intValue).getOrElse(Unknown)
  }

  def switch(side: RoadSide) = {
    side match {
      case Right => Left
      case Left => Right
      case _ => side
    }
  }

  case object Right extends RoadSide { def value = 1 }
  case object Left extends RoadSide { def value = 2 }
  case object Between extends RoadSide { def value = 3 }
  case object End extends RoadSide { def value = 7 }
  case object Middle extends RoadSide { def value = 8 }
  case object Across extends RoadSide { def value = 9 }
  case object Unknown extends RoadSide { def value = 0 }
}

case class RoadAddress(municipalityCode: Option[String], road: Int, roadPart: Int, track: Track, addrM: Int)
class RoadAddressException(response: String) extends RuntimeException(response)
class RoadPartReservedException(response: String) extends RoadAddressException(response)

/**
  * A class to transform ETRS89-FI coordinates to road network addresses
  */

class GeometryTransform(roadAddressService: RoadAddressService) {
  // see page 16: http://www.liikennevirasto.fi/documents/20473/143621/tieosoitej%C3%A4rjestelm%C3%A4.pdf/

  lazy val vkmGeometryTransform: VKMGeometryTransform = {
    new VKMGeometryTransform()
  }

  def resolveAddressAndLocation(coord: Point, heading: Int, mValue: Double, linkId: Long, assetSideCode: Int, municipalityCode: Option[Int] = None, road: Option[Int] = None): (RoadAddress, RoadSide) = {

    def againstDigitizing(addr: RoadAddressDTO) = {
      val addressLength: Long = addr.endAddrMValue - addr.startAddrMValue
      val lrmLength: Double = Math.abs(addr.endMValue - addr.startMValue)
      val newMValue = (addr.endAddrMValue - ((mValue-addr.startMValue) * addressLength / lrmLength)).toInt
      RoadAddress(Some(municipalityCode.toString), addr.roadNumber.toInt, addr.roadPartNumber.toInt, addr.track, newMValue)
    }

    def towardsDigitizing (addr: RoadAddressDTO) = {
      val addressLength: Long = addr.endAddrMValue - addr.startAddrMValue
      val lrmLength: Double = Math.abs(addr.endMValue - addr.startMValue)
      val newMValue = (((mValue-addr.startMValue) * addressLength) / lrmLength + addr.startAddrMValue).toInt
      RoadAddress(Some(municipalityCode.toString), addr.roadNumber.toInt, addr.roadPartNumber.toInt, addr.track, newMValue)
    }

    val roadAddress = roadAddressService.getByLrmPosition(linkId, mValue)

    //If there is no roadAddress in VIITE try to find it in VKM
    if(roadAddress.isEmpty && road.isDefined)
      return vkmGeometryTransform.resolveAddressAndLocation(coord, heading, SideCode.apply(assetSideCode), road)

    val roadSide = roadAddress match {
      case Some(addrSide) if addrSide.sideCode.value == assetSideCode => RoadSide.Right //TowardsDigitizing //
      case Some(addrSide) if addrSide.sideCode.value != assetSideCode => RoadSide.Left //AgainstDigitizing //
      case _ => RoadSide.Unknown
    }

    val address = roadAddress match {
      case Some(addr) if addr.track.eq(Track.Unknown) => throw new RoadAddressException("Invalid value for Track: %d".format(addr.track.value))
      case Some(addr) if addr.sideCode.value != assetSideCode =>
        if (assetSideCode == SideCode.AgainstDigitizing.value) towardsDigitizing(addr) else againstDigitizing(addr)
      case Some(addr) =>
        if (assetSideCode == SideCode.TowardsDigitizing.value) towardsDigitizing(addr) else againstDigitizing(addr)
      case None => throw new RoadAddressException("No road address found")
    }

    (address, roadSide )
  }
}

//TODO remove VKM when VIITE is 100% done
class VKMGeometryTransform {
  case class VKMError(content: Map[String, Any], url: String)

  protected implicit val jsonFormats: Formats = DefaultFormats
  private def VkmRoad = "tie"
  private def VkmRoadPart = "osa"
  private def VkmDistance = "etaisyys"
  private def VkmTrackCodes = "ajoradat"
  private def VkmTrackCode = "ajorata"
  private def VkmSearchRadius = "sade"
  private def VkmQueryIdentifier = "tunniste"
  private def VkmMunicipalityCode = "kuntakoodi"
  private def NonPedestrianRoadNumbers = "1-62999"
  private def AllRoadNumbers = "1-99999"
  private def DefaultToleranceMeters = 20.0

  private def vkmBaseUrl = {
    val properties = new Properties()
    properties.load(getClass.getResourceAsStream("/digiroad2.properties"))
    properties.getProperty("digiroad2.VKMUrl") + "/vkm-api/"
  }

  def urlParams(paramMap: Map[String, Option[Any]]) = {
    paramMap.filter(entry => entry._2.nonEmpty).map(entry => URLEncoder.encode(entry._1, "UTF-8")
      + "=" + URLEncoder.encode(entry._2.get.toString, "UTF-8")).mkString("&")
  }

  def urlParamsReverse(paramMap: Map[String, Any]) = {
    paramMap.map(entry => URLEncoder.encode(entry._1, "UTF-8")
      + "=" + URLEncoder.encode(entry._2.toString, "UTF-8")).mkString("&")
  }


  private def request(url: String): Either[List[Map[String, Any]], VKMError] = {
    val request = new HttpGet(url)
    val client = HttpClientBuilder.create().build()
    val response = client.execute(request)
    try {
      if (response.getStatusLine.getStatusCode >= 400)
        return Right(VKMError(Map("error" -> "Request returned HTTP Error %d".format(response.getStatusLine.getStatusCode)), url))
      val aux = response.getEntity.getContent
      val content:List[Map[String, Any]] = parse(StreamInput(aux)).values.asInstanceOf[List[Map[String, Any]]]
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

  // xyhaku
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

    request(vkmBaseUrl + "xyhaku?sade=500&" + urlParams(params)) match {
      case Left(address) => mapFields(address.head)
      case Right(error) => throw new RoadAddressException(error.toString)
    }
  }

  // xyhaku
  def coordsToAddresses(coords: Seq[Point], road: Option[Int] = None, roadPart: Option[Int] = None,
                        distance: Option[Int] = None, track: Option[Track] = None, searchDistance: Option[Double] = None,
                        includePedestrian: Option[Boolean] = Option(false)) : Seq[RoadAddress] = {

    coords.map( coord => coordToAddress(coord, road, roadPart, distance, track, searchDistance, includePedestrian) )

  }

  // tieosoitehaku
  def addressToCoords(roadAddress: RoadAddress) : Seq[Point] = {
    val params = Map(
      VkmRoad -> roadAddress.road,
      VkmRoadPart -> roadAddress.roadPart,
      VkmTrackCodes -> roadAddress.track.value,
      VkmDistance -> roadAddress.addrM
    )

    request(vkmBaseUrl + "tieosoitehaku?" + urlParamsReverse(params)) match  {
      case Left(addressData) =>
        if (addressData.nonEmpty)
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

  private def extractRoadAddresses(data: List[Map[String, Any]]) = {
    data.sortBy(_.getOrElse(VkmQueryIdentifier, Int.MaxValue).toString.toInt).map(mapFields)
  }

  private def mapFields(data: Map[String, Any]) = {
    val municipalityCode = data.get(VkmMunicipalityCode)
    val road = validateAndConvertToInt(VkmRoad, data)
    val roadPart = validateAndConvertToInt(VkmRoadPart, data)
    val track = validateAndConvertToInt(VkmTrackCode, data)
    val mValue = validateAndConvertToInt(VkmDistance, data)
    if (Track.apply(track).eq(Track.Unknown)) {
      throw new RoadAddressException("Invalid value for Track (%s): %d".format(VkmTrackCode, track))
    }
    RoadAddress(municipalityCode.map(_.toString), road, roadPart, Track.apply(track), mValue)
  }

  private def mapCoordinates(data: List[Map[String, Any]]) = {

    try {
      data.map {
        addr =>
          val x = addr("x").asInstanceOf[Double]
          val y = addr("y").asInstanceOf[Double]
          Point(x,y)
      }
    } catch {
      case ex: Exception => throw new RoadAddressException("Could not convert response from VKM: %s".format(ex.getMessage))
    }
  }

  private def validateAndConvertToInt(fieldName: String, map: Map[String, Any]) = {
    def value = map.get(fieldName)
    if (value.isEmpty) {
      throw new RoadAddressException(
        "Missing mandatory field in response: %s".format(
          fieldName))
    }
    try {
      value.get.toString.toInt
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