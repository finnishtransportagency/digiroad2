package fi.liikennevirasto.digiroad2.client.viite

import fi.liikennevirasto.digiroad2.Track
import fi.liikennevirasto.digiroad2.asset.SideCode
import fi.liikennevirasto.digiroad2.service.RoadAddressForLink
import org.apache.http.entity.{ContentType, StringEntity}
import org.apache.http.impl.client.CloseableHttpClient
import org.json4s.jackson.Serialization

class SearchViiteClient(viiteRestApiEndPoint: String, httpClient: CloseableHttpClient) extends ViiteClientOperations {

  override type ViiteType = RoadAddressForLink

  override protected def client: CloseableHttpClient = httpClient

  override protected def restApiEndPoint: String = viiteRestApiEndPoint

  override protected def serviceName: String = viiteRestApiEndPoint + "search/"

  def fetchAllRoadNumbers(): Seq[Long] = {
    get[Seq[BigInt]](serviceName + "road_numbers") match {
      case Left(roadNumbers) => roadNumbers.map(_.longValue())
      case Right(error) => throw new ViiteClientException(error.toString)
    }
  }

  def fetchAllByRoadNumber(roadNumber: Long, tracks: Seq[Track]) = {
    fetchRoadAddress(serviceName + "road_address/" + roadNumber, tracks.map(t => "tracks" -> t.value.toString).toMap)
  }

  def fetchAllBySection(roadNumber: Long, roadPartNumbers: Seq[Long], tracks: Seq[Track]) = {
    post[Map[String, Any], List[Map[String, Any]]](serviceName + "road_address/" + roadNumber, Map("roadParts" -> roadPartNumbers, "tracks" -> tracks.map( _.value) ),
      ra => new StringEntity(Serialization.write(ra), ContentType.APPLICATION_JSON)) match {
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

  def fetchByLrmPosition(linkId: String, startMeasure: Double) = {
    fetchRoadAddress(serviceName + "road_address/", Map("linkId" -> linkId, "startMeasure" -> startMeasure.toString))
  }

  def fetchByLrmPositions(linkId: String, startMeasure: Double, endMeasure: Double) = {
    fetchRoadAddress(serviceName + "road_address/", Map("linkId" -> linkId, "startMeasure" -> startMeasure.toString, "endMeasure" -> endMeasure.toString))
  }

  def fetchAllByLinkIds(linkIds: Seq[String]): Seq[RoadAddressForLink] = {
    post[Seq[String], List[Map[String, Any]]](serviceName + "road_address", linkIds, ids => new StringEntity(s"[${ids.map(id => s""""$id"""").mkString(",")}]", ContentType.APPLICATION_JSON)) match {
      case Left(roadAddresses) => roadAddresses.flatMap(mapFields)
      case Right(error) => throw new ViiteClientException(error.toString)
    }
  }

  protected def fetchRoadAddress(url: String, params: Map[String, String] = Map()) : Seq[RoadAddressForLink] = {
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

  protected def mapFields(data: Map[String, Any]): Option[RoadAddressForLink] = {
    val id = convertToLong(getMandatoryFieldValue(data, "id")).get
    val roadNumber = convertToLong(getMandatoryFieldValue(data, "roadNumber")).get
    val roadPartNumber = convertToLong(getMandatoryFieldValue(data, "roadPartNumber")).get
    val trackCode = Track.apply(convertToInt(getMandatoryFieldValue(data, "track")).get)
    val startAddrM = convertToLong(getMandatoryFieldValue(data, "startAddrM")).get
    val endAddrM = convertToLong(getMandatoryFieldValue(data, "endAddrM")).get
    val linkId = getMandatoryFieldValue(data, "linkId").get
    val startMValue = convertToDouble(getMandatoryFieldValue(data, "startMValue")).get
    val sideCode = convertToInt(getMandatoryFieldValue(data, "sideCode")).get
    val endMValue = convertToDouble(getMandatoryFieldValue(data, "endMValue")).get

    Some(RoadAddressForLink(id, roadNumber, roadPartNumber, trackCode, startAddrM, endAddrM, None, None, linkId, startMValue, endMValue, SideCode.apply(sideCode), Seq(), false, None, None, None ))
  }
  override protected def mapFields[A](data: A): Option[List[RoadAddressForLink]] = ???
}
