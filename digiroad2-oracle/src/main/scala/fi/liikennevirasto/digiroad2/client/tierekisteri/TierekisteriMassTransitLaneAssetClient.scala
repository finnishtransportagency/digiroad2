package fi.liikennevirasto.digiroad2.client.tierekisteri

import fi.liikennevirasto.digiroad2.util.{RoadSide, Track}
import org.apache.http.impl.client.CloseableHttpClient

case class TierekisteriMassTransitLaneData(roadNumber: Long, startRoadPartNumber: Long, endRoadPartNumber: Long,
                                           track: Track, startAddressMValue: Long, endAddressMValue: Long, laneType: TRLaneArrangementType, roadSide: RoadSide) extends TierekisteriAssetData

class TierekisteriMassTransitLaneAssetClient(trEndPoint: String, trEnable: Boolean, httpClient: CloseableHttpClient) extends TierekisteriAssetDataClient {
  override def tierekisteriRestApiEndPoint: String = trEndPoint
  override def tierekisteriEnabled: Boolean = trEnable
  override def client: CloseableHttpClient = httpClient
  type TierekisteriType = TierekisteriMassTransitLaneData

  override val trAssetType = "tl161"
  private val trLaneType = "KAISTATY"
  private val trRoadSide = "PUOLI"

  override def mapFields(data: Map[String, Any]): Option[TierekisteriMassTransitLaneData] = {
    //Mandatory field
    val roadNumber = convertToLong(getMandatoryFieldValue(data, trRoadNumber)).get
    val roadPartNumber = convertToLong(getMandatoryFieldValue(data, trRoadPartNumber)).get
    val endRoadPartNumber = convertToLong(getMandatoryFieldValue(data, trEndRoadPartNumber)).getOrElse(roadPartNumber)
    val startMValue = convertToLong(getMandatoryFieldValue(data, trStartMValue)).get
    val endMValue = convertToLong(getMandatoryFieldValue(data, trEndMValue)).get
    val track = convertToInt(getMandatoryFieldValue(data, trTrackCode)).map(Track.apply).getOrElse(Track.Unknown)
    val laneType = convertToInt(getMandatoryFieldValue(data, trLaneType)).get
    val roadSide = convertToInt(getMandatoryFieldValue(data, trRoadSide)).map(RoadSide.apply).getOrElse(RoadSide.Unknown)

    Some(TierekisteriMassTransitLaneData(roadNumber, roadPartNumber, endRoadPartNumber, track, startMValue, endMValue, TRLaneArrangementType.apply(laneType), roadSide))
  }
}

