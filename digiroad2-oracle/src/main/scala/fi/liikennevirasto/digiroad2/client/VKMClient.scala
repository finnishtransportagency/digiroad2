package fi.liikennevirasto.digiroad2.client

import fi.liikennevirasto.digiroad2

import java.net.URLEncoder
import java.security.cert.X509Certificate
import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.service.RoadAddressForLink
import fi.liikennevirasto.digiroad2.util._
import fi.liikennevirasto.digiroad2.{Feature, FeatureCollection, Point, RoadAddress, RoadAddressException, Track, Vector3d}
import org.apache.http.{HttpStatus, NameValuePair}
import org.apache.http.client.config.{CookieSpecs, RequestConfig}
import org.apache.http.client.entity.UrlEncodedFormEntity

import javax.net.ssl.{HostnameVerifier, SSLSession, X509TrustManager}
import org.apache.http.client.methods.{HttpGet, HttpPost}
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.{CloseableHttpClient, DefaultHttpClient, HttpClientBuilder}
import org.apache.http.message.BasicNameValuePair
import org.apache.http.params.HttpParams
import org.apache.http.util.EntityUtils
import org.json4s.jackson.JsonMethods.parse
import org.json4s.jackson.Serialization
import org.json4s.{DefaultFormats, Formats, StreamInput}
import org.slf4j.LoggerFactory

import java.nio.charset.Charset
import java.util.ArrayList


case class MassQueryParamsCoord(identifier: String, point: Point, roadNumber: Option[Int], roadPartNumber: Option[Int], track: Option[Track] = None)
case class DeterminateSide(identifier: String, points: Seq[Point], roadNumber: Int, roadPartNumber: Int, track: Option[Track] = None)
case class PointAssetForConversion(id: Long, coord: Point, heading: Option[Int], mValue: Double, linkId: String, sideCode: Option[Int], municipalityCode: Option[Int] = None, road: Option[Int] = None)
case class MassQueryResolve(asset: Long, coord: Point, heading: Option[Int], sideCode: SideCode, road: Option[Int] = None, roadPart: Option[Int] = None, includePedestrian: Option[Boolean] = Option(false))
case class RoadAddressBoundToAsset(asset: Long, address: RoadAddress, side: RoadSide)
case class AddrWithIdentifier(identifier: String, roadAddress: RoadAddress)
case class PointWithIdentifier(identifier: String, point: Point)

object VKMClient { // singleton client
 lazy val client: CloseableHttpClient = ClientUtils.clientBuilder(5000,5000)
}

class VKMClient {
  case class VKMError(content: Map[String, Any], url: String)
  protected implicit val jsonFormats: Formats = DefaultFormats
  private def VkmRoad = "tie"
  private def VkmRoadPart = "osa"
  private def VkmRoadPartEnd = "osa_loppu"
  private def VkmDistance = "etaisyys"
  private def VkmDistanceEnd = "etaisyys_loppu"
  private def VkmMValueStart = "m_arvo"
  private def VkmMValueEnd = "m_arvo_loppu"
  private def VkmTrackCodes = "ajorata"
  private def VkmTrackCode = "ajorata"
  private def VkmLinkId = "link_id"
  private def VkmLinkIdEnd = "link_id_loppu"
  private def VkmKmtkId = "kmtk_id"
  private def VkmKmtkIdEnd = "kmtk_id_loppu"
  private def VkmSearchRadius = "sade"
  private def VkmQueryIdentifier = "tunniste"
  private def VkmMunicipalityCode = "kuntakoodi"
  private def VkmRangeSearch = "valihaku"
  private def VkmResponseValues = "palautusarvot"
  private def NonPedestrianRoadNumbers = "1-62999"
  private def AllRoadNumbers = "1-99999"
  private def DefaultToleranceMeters = 20.0

  private def coordinateFrameWorkValue = 1
  private def roadAddressFrameWorkValue = 2
  private def linearLocationFrameWorkValue = 6


  private def VkmMaxBatchSize =1000

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

    val client = VKMClient.client

    val response = client.execute(request)
    try {
      if (response.getStatusLine.getStatusCode >= HttpStatus.SC_BAD_REQUEST) {
        logger.error(s"Error Response body was ${EntityUtils.toString(response.getEntity , Charset.forName("UTF-8"))}")
        return Right(VKMError(Map("error" -> "Request returned HTTP Error %d".format(response.getStatusLine.getStatusCode)), url))
      }
      
      val aux = response.getEntity.getContent
      val content: FeatureCollection = parse(StreamInput(aux).stream).extract[FeatureCollection]
      val contentFiltered = content.copy(features = content.features.filterNot(_.properties.contains("virheet")))
      Left(contentFiltered)
    } catch {
      case e: Exception => Right(VKMError(Map("error" -> e.getMessage), url))
    } finally {
      response.close()
    }
  }

  private def request(url: String): Either[FeatureCollection, VKMError] = {
    ClientUtils.retry(5, logger, commentForFailing = s"Failing url: $url") {requestBase(url)}
  }
  private def requestBase(url: String): Either[FeatureCollection, VKMError] = {
    val request = new HttpGet(url)
    request.addHeader("X-API-Key", Digiroad2Properties.vkmApiKey)
    val client = VKMClient.client
    val response = client.execute(request)
    try {
      if (response.getStatusLine.getStatusCode >= 400) {
        logger.error(s"Error Response body was ${EntityUtils.toString(response.getEntity , Charset.forName("UTF-8"))}")
        return Right(VKMError(Map("error" -> "Request returned HTTP Error %d".format(response.getStatusLine.getStatusCode)), url))
      }
      val aux = response.getEntity.getContent
      val content:FeatureCollection = parse(StreamInput(aux).stream).extract[FeatureCollection]
      val (errorFeatures, okFeatures) = content.features.partition(_.properties.contains("virheet"))
      if (errorFeatures.nonEmpty && okFeatures.isEmpty) {
        return Right(VKMError(Map("error" -> errorFeatures.head.properties("virheet")), url))
      }
      Left(content.copy(features = okFeatures))
    } catch {
      case e: Exception =>
        logger.error(e.getMessage)
        Right(VKMError(Map("error" -> e.getMessage), url))
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

  def fetchRoadAddressesByLinkIds(linkIds: Seq[String]): Seq[RoadAddressForLink] = {
    val params = linkIds.map(linkId =>
      Map(
        VkmQueryIdentifier -> "TestiTunniste",
        VkmLinkId -> linkId,
        VkmRangeSearch -> "true",
        VkmResponseValues -> s"$roadAddressFrameWorkValue,$linearLocationFrameWorkValue"
    ))
    val response = baseRequest(params)
    val result = response match {
      case Left(address) => address.features.flatMap(feature => mapRoadAddressForLinkFields(feature))
      case Right(error) => throw new RoadAddressException(error.toString)
    }
    result
  }

  def fetchRoadAddressByLrmPosition(linkId: String, mValue: Double): Seq[RoadAddressForLink] = {
    val params = Seq(Map(
      VkmLinkId -> linkId,
      VkmMValueStart -> mValue,
      VkmRangeSearch -> "true",
      VkmResponseValues -> s"$roadAddressFrameWorkValue,$linearLocationFrameWorkValue"
    ))

    val response = baseRequest(params)
    val result = response match {
      case Left(address) => address.features.flatMap(feature => mapRoadAddressForLinkFields(feature))
      case Right(error) => throw new RoadAddressException(error.toString)
    }
    result
  }

  def fetchLinkIdsBetweenTwoRoadLinks(startLinkId: String, endLinkId: String, roadNumber: Long): Set[String] = {
    val params = Map(
      VkmLinkId -> Some(startLinkId),
      VkmLinkIdEnd -> Some(endLinkId),
      VkmRoad -> Some(roadNumber)
    )

    request(vkmBaseUrl + "muunna?valihaku=true&palautusarvot=6&" + urlParams(params)) match {
      case Left(featureCollection) => featureCollection.features.map(_.properties(VkmLinkId).asInstanceOf[String]).toSet
      case Right(error) => throw new RoadAddressException(error.toString)
    }
  }

  // TODO VKM does not provide all road links when transforming road address range to roadLinks, only start and end links
  //  Support for this is coming to VKM in early 2023, change implementation to use the feature when it's available.
  def fetchStartAndEndLinkIdForAddrRange(roadAddressRange: RoadAddressRange): Set[(String, String)] = {
    val params = Map(
      VkmRoad -> Some(roadAddressRange.roadNumber),
      VkmRoadPart -> Some(roadAddressRange.startRoadPartNumber),
      VkmDistance -> Some(roadAddressRange.startAddrMValue),
      VkmRoadPartEnd -> Some(roadAddressRange.endRoadPartNumber),
      VkmDistanceEnd -> Some(roadAddressRange.endAddrMValue),
      VkmTrackCode -> {
        if(roadAddressRange.track.isDefined) Some(roadAddressRange.track.get.value)
        else None
      }
    )

    request(vkmBaseUrl + "muunna?valihaku=true&palautusarvot=6&" + urlParams(params)) match {
      case Left(featureCollection) =>
        featureCollection.features.map(feature => (feature.properties(VkmLinkId).asInstanceOf[String],
          feature.properties(VkmLinkIdEnd).asInstanceOf[String])).toSet
      case Right(error) => throw new RoadAddressException(error.toString)
    }
  }
  /**
    * 
    * @param coords
    * @param searchDistance Default in new VKM is 100
    * @return
    */
  def coordToAddressMassQuery(coords: Seq[MassQueryParamsCoord], searchDistance: Option[Double] = None): Map[String, RoadAddress] = {
    val params = coords.map(coord => Map(
      VkmQueryIdentifier -> coord.identifier,
      VkmRoad -> coord.roadNumber,
      VkmRoadPart -> coord.roadPartNumber,
      VkmTrackCodes -> coord.track.map(_.value),
      "x" -> coord.point.x,
      "y" -> coord.point.y,
      VkmSearchRadius -> searchDistance
    ))
      new Parallel().operation(params.grouped(VkmMaxBatchSize).toList.par,3){
        _.flatMap(validateRange).toList
      }.toMap
  }
  
  private def validateRange(params: Seq[Map[String, Any]]): Map[String, RoadAddress] ={
   val response = baseRequest(params)
    val result = response match {
      case Left(address) => address.features.map(feature => mapMassQueryFields(feature))
      case Right(error) => throw new RoadAddressException(error.toString)
    }

    result.filter(_.isDefined).flatMap(_.get).groupBy(_._1)
      .map(a => {
        val id = a._1
        val address = a._2.map(_._2)
        if (address.size >= 2) {
          // TODO fix this logic same time you fix coordToAddress method.
          logger.info(s"Search distance was too big to identify single response, identifier was $id and result: ${address.mkString(",")}")
        }
        (id, address.head)
      })
  }

  private def baseRequest(params: Seq[Map[String, Any]]): Either[FeatureCollection, VKMError] = {
    val jsonValue = Serialization.write(params)
    val url = vkmBaseUrl + "muunna/"
    ClientUtils.retry(5, logger, commentForFailing = s"JSON payload for failing: $jsonValue") {postRequest(url, jsonValue)}
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
    val parameterString = "muunna?sade=500&" + urlParams(params)
    
    request(vkmBaseUrl + parameterString) match {
      case Left(address) => 
        if (address.features.length >= 2)
          logger.info(s"Search distance was too big to identify single response, request parameters: $parameterString and result: ${address.features.map(mapFields).mkString(",")}")
        //TODO this is wrong way to select values, there can be two or more value dependent on how many track road has.  
        mapFields(address.features.head)
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
      val (behind: Point, front: Point) = calculatePointAfterAndBeforeRoadAddressPosition(coord, Some(heading), sideCode)
      val addresses = coordsToAddresses(Seq(behind, coord, front), road, roadPart, includePedestrian = includePedestrian)
      determinateRoadSide(addresses)
    }
  }

  def resolveAddressAndLocations(assets: Seq[MassQueryResolve]): Seq[RoadAddressBoundToAsset] = {
    val roadAddress = LogUtils.time(logger, s"TEST LOG coordToAddressMassQuery") {
      coordToAddressMassQuery(assets.map(a => MassQueryParamsCoord(a.asset.toString, a.coord, a.road, a.roadPart)))
      .map(convertToDeterminateSide(assets, _)).toSeq
    }
    LogUtils.time(logger, s"TEST LOG checkRoadSide") {
        roadAddress.map(checkRoadSide)
    }
  }
  /**
    *  assume that point seq is ordered
    * @param asset
    * @return
    */
  private def checkRoadSide(asset: DeterminateSide): RoadAddressBoundToAsset = {
    val errorMessage = "Did not get needed Road address for the determinateRoadSide method"
    
    val params = Seq(
      MassQueryParamsCoord(s"${asset.identifier}:behind", asset.points(0), Some(asset.roadNumber), Some(asset.roadPartNumber), asset.track),
      MassQueryParamsCoord(s"${asset.identifier}:correct", asset.points(1), Some(asset.roadNumber), Some(asset.roadPartNumber), asset.track),
      MassQueryParamsCoord(s"${asset.identifier}:front", asset.points(2), Some(asset.roadNumber), Some(asset.roadPartNumber), asset.track)
    )
    
    val addresses = {
     val response =  coordToAddressMassQuery(params, searchDistance = Some(5.0)).toSeq
      if (response.size != 3) 
        coordToAddressMassQuery(params, searchDistance = Some(100)).toSeq // widen search back to initial values
      else response
    }
    
    val behind = addresses.find(_._1.split(":")(1) == "behind").getOrElse(throw new RoadAddressException(errorMessage))._2
    val correct = addresses.find(_._1.split(":")(1) == "correct").getOrElse(throw new RoadAddressException(errorMessage))._2
    val front = addresses.find(_._1.split(":")(1) == "front").getOrElse(throw new RoadAddressException(errorMessage))._2
    
    val selected = determinateRoadSide(Seq(behind, correct, front))
    RoadAddressBoundToAsset(asset.identifier.toLong, selected._1, selected._2)
  }
  
  private def convertToDeterminateSide(assets: Seq[MassQueryResolve], assetAndRoadAddress: (String, RoadAddress)) = {
    val assets2 = assets.find(_.asset.toString == assetAndRoadAddress._1).get
    val (behind: Point, front: Point) = calculatePointAfterAndBeforeRoadAddressPosition(assets2.coord, assets2.heading, assets2.sideCode)
    DeterminateSide(assets2.asset.toString, Seq(behind, assets2.coord, front), assetAndRoadAddress._2.road, assetAndRoadAddress._2.roadPart)
  }
  
  private def determinateRoadSide(addresses: Seq[RoadAddress]): (RoadAddress, RoadSide) = {
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
  private def calculatePointAfterAndBeforeRoadAddressPosition(coord: Point, heading: Option[Int], sideCode: SideCode): (Point, Point) = {
    val degrees = sideCode match {
      case SideCode.AgainstDigitizing => 90 - heading.get + 180
      case _ => 90 - heading.get
    }
    val rad = degrees * Math.PI / 180.0
    val stepVector = Vector3d(3 * Math.cos(rad), 3 * Math.sin(rad), 0.0)
    val behind = coord - stepVector
    val front = coord + stepVector
    (behind, front)
  }

  private def mapRoadAddressForLinkFields(data: Feature): Some[RoadAddressForLink] = {
    val roadNumber = validateAndConvertToInt(VkmRoad, data.properties)
    val roadPartNumber = validateAndConvertToInt(VkmRoadPart, data.properties)
    val trackCode = Track.apply(validateAndConvertToInt(VkmTrackCode, data.properties))
    val startAddrM = validateAndConvertToInt(VkmDistance, data.properties)
    val endAddrM = validateAndConvertToInt(VkmDistanceEnd, data.properties)
    val linkId = data.properties(VkmLinkId).asInstanceOf[String]
    val startMValue = validateAndConvertToDouble(VkmMValueStart, data.properties)
    val endMValue = validateAndConvertToDouble(VkmMValueEnd, data.properties)

    val sideCode = if(startAddrM <= endAddrM) SideCode.TowardsDigitizing else SideCode.AgainstDigitizing

    Some(RoadAddressForLink(0, roadNumber, roadPartNumber, trackCode, startAddrM, endAddrM, None, None, linkId, startMValue, endMValue, sideCode, Seq(), expired = false, None, None, None))
  }

  private def mapMassQueryFields(data: Feature): Option[Map[String, RoadAddress]] = {
    try{
      val municipalityCode = data.properties.get(VkmMunicipalityCode).asInstanceOf[Option[String]]
      val road = validateAndConvertToInt(VkmRoad, data.properties)
      val roadPart = validateAndConvertToInt(VkmRoadPart, data.properties)
      val track = validateAndConvertToInt(VkmTrackCode, data.properties)
      val mValue = validateAndConvertToInt(VkmDistance, data.properties)
      val queryIdentifier = data.properties(VkmQueryIdentifier).asInstanceOf[String]
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
    val municipalityCode = data.properties.get(VkmMunicipalityCode).asInstanceOf[Option[String]]
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
          val x = addr.properties("x").asInstanceOf[String].toDouble
          val y = addr.properties("y").asInstanceOf[String].toDouble
          PointWithIdentifier(queryIdentifier.asInstanceOf[String], Point(x,y))
      }
    } catch {
      case ex: Exception => throw new RoadAddressException("Could not convert response from VKM: %s".format(ex.getMessage))
    }
  }

  private def mapCoordinates(data: FeatureCollection) = {

    try {
      data.features.map {
        addr =>
          val x = addr.properties("x").asInstanceOf[String].toDouble
          val y = addr.properties("y").asInstanceOf[String].toDouble
          Point(x,y)
      }
    } catch {
      case ex: Exception => throw new RoadAddressException("Could not convert response from VKM: %s".format(ex.getMessage))
    }
  }

  private def validateAndConvertToInt(fieldName: String, map: Map[String, Any]) = {
    def value = map.get(fieldName).asInstanceOf[Option[BigInt]]
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

  private def validateAndConvertToDouble(fieldName: String, map: Map[String, Any]) = {
    def value = map.get(fieldName).asInstanceOf[Option[Double]]
    if (value.isEmpty) {
      throw new RoadAddressException(
        "Missing mandatory field in response: %s".format(
          fieldName))
    }
    try {
      value.get
    } catch {
      case e: NumberFormatException =>
        throw new RoadAddressException("Invalid value in response: %s, Double expected, got '%s'".format(fieldName, value.get))
    }
  }
}
