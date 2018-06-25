package fi.liikennevirasto.digiroad2.client.viite

import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.dao.RoadAddress
import fi.liikennevirasto.digiroad2.util.Track
import org.apache.http.entity.{ContentType, StringEntity}
import org.apache.http.impl.client.CloseableHttpClient
import org.json4s.jackson.Serialization

class SearchViiteClient(vvhRestApiEndPoint: String, httpClient: CloseableHttpClient) extends ViiteClientOperations {

  override type ViiteType = RoadAddress

  override protected def client: CloseableHttpClient = httpClient

  override protected def restApiEndPoint: String = vvhRestApiEndPoint

  override protected def serviceName: String = vvhRestApiEndPoint + "search/"

  def fetchAllRoadNumbers(): Seq[Long] = {
    get[Seq[Long]](serviceName + "road_numbers") match {
      case Left(roadNumbers) => roadNumbers
      case Right(error) => throw new ViiteClientException(error.toString)
    }
  }

  def fetchAllByRoadNumber(roadNumber: Long, tracks: Seq[Track]) = {
    fetchRoadAddress(serviceName + "road_address/" + roadNumber, tracks.map(t => "tracks" -> t.value.toString).toMap)
  }

  def fetchAllBySection(roadNumber: Long, roadPartNumbers: Seq[Long], tracks: Seq[Track]) = {
    post[Map[String, Any], List[Map[String, Any]]]("road_address/" + roadNumber, Map("roadParts" -> roadPartNumbers, "tracks" -> tracks), ra => new StringEntity(Serialization.write(ra), ContentType.APPLICATION_JSON)) match {
      case Left(roadAddresses) => roadAddresses.flatMap(mapFields)
      case Right(error) => throw new ViiteClientException(error.toString)
    }
  }

  def fetchAllBySection(roadNumber: Long, roadPartNumber: Long, tracks: Seq[Track]) = {
    fetchRoadAddress(serviceName + "road_address/" + roadNumber + "/" + roadPartNumber, tracks.map(t => "tracks" -> t.value.toString).toMap)
  }

  def fetchAllBySection(roadNumber: Long, roadPartNumber: Long, addrM: Long, tracks: Seq[Track]) = {
    fetchRoadAddress(serviceName + "road_address/" + roadNumber + "/" + roadPartNumber + "/" + addrM, tracks.map(t => "tracks" -> t.value.toString).toMap)
  }

  def fetchAllBySection(roadNumber: Long, roadPartNumber: Long, startAddrM: Long, endAddrM: Long) = {
    fetchRoadAddress(serviceName + "road_address/" + roadNumber + "/" + roadPartNumber + "/" + startAddrM + "/" + endAddrM)
  }

  def fetchByLrmPosition(linkId: Long, startMeasure: Double) = {
    fetchRoadAddress(serviceName + "road_address/", Map("linkId" -> linkId.toString, "startMeasure" -> startMeasure.toString))
  }

  def fetchByLrmPositions(linkId: Long, startMeasure: Double, endMeasure: Double) = {
    fetchRoadAddress(serviceName + "road_address/", Map("linkId" -> linkId.toString, "startMeasure" -> startMeasure.toString, "endMeasure" -> endMeasure.toString))
  }

  def fetchAllByLinkIds(linkIds: Seq[Long]): Seq[RoadAddress] = {
    post[Seq[Long], List[Map[String, Any]]](serviceName + "road_address", linkIds, ids => new StringEntity(Serialization.write(ids), ContentType.APPLICATION_JSON)) match {
      case Left(roadAddresses) => roadAddresses.flatMap(mapFields)
      case Right(error) => throw new ViiteClientException(error.toString)
    }
  }

  protected def fetchRoadAddress(url: String, params: Map[String, String] = Map()) : Seq[RoadAddress] = {
    def withQueryString() = {
      if(params.nonEmpty)
        url + "?"+params.map(p => p._1 + "="+ p._2).mkString("&")
      else
        url
    }
    get[List[Map[String, Any]]](withQueryString()) match {
      case Left(roadAddresses) => roadAddresses.flatMap(mapFields)
      case Right(error) => throw new ViiteClientException(error.toString)
    }
  }

  protected def mapFields(data: Map[String, Any]): Option[RoadAddress] = {
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

    Some(RoadAddress(id, roadNumber, roadPartNumber, trackCode, startAddrM, endAddrM, None, None, linkId, startMValue, endMValue, SideCode.TowardsDigitizing, floating, Seq(), false, None, None, None ))
  }

}
