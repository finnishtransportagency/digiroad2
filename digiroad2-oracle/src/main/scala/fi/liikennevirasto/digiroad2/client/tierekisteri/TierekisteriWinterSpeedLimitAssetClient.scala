package fi.liikennevirasto.digiroad2.client.tierekisteri

import fi.liikennevirasto.digiroad2.util.Track
import org.apache.http.impl.client.CloseableHttpClient

case class TierekisteriWinterSpeedLimitData(roadNumber: Long, startRoadPartNumber: Long, endRoadPartNumber: Long,
                                            track: Track, startAddressMValue: Long, endAddressMValue: Long, speedLimit: Int) extends TierekisteriAssetData

class TierekisteriWinterSpeedLimitAssetClient(trEndPoint: String, trEnable: Boolean, httpClient: CloseableHttpClient) extends TierekisteriAssetDataClient{
  override def tierekisteriRestApiEndPoint: String = trEndPoint
  override def tierekisteriEnabled: Boolean = trEnable
  override def client: CloseableHttpClient = httpClient
  type TierekisteriType = TierekisteriWinterSpeedLimitData

  override val trAssetType = "tl169"
  private val trTALVINOP = "TALVINOP"

  override def mapFields(data: Map[String, Any]): Option[TierekisteriWinterSpeedLimitData] = {
    val assetValue = convertToInt(getFieldValue(data, trTALVINOP)).get

    //Mandatory field
    val roadNumber = convertToLong(getMandatoryFieldValue(data, trRoadNumber)).get
    val roadPartNumber = convertToLong(getMandatoryFieldValue(data, trRoadPartNumber)).get
    val endRoadPartNumber = convertToLong(getMandatoryFieldValue(data, trEndRoadPartNumber)).getOrElse(roadPartNumber)
    val startMValue = convertToLong(getMandatoryFieldValue(data, trStartMValue)).get
    val endMValue = convertToLong(getMandatoryFieldValue(data, trEndMValue)).get
    val track = convertToInt(getMandatoryFieldValue(data, trTrackCode)).map(Track.apply).getOrElse(Track.Unknown)

    Some(TierekisteriWinterSpeedLimitData(roadNumber, roadPartNumber, endRoadPartNumber, track, startMValue, endMValue, assetValue))
  }
}
