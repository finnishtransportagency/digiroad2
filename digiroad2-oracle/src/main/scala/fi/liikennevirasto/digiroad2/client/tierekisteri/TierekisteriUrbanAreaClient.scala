package fi.liikennevirasto.digiroad2.client.tierekisteri

import fi.liikennevirasto.digiroad2.util.Track
import org.apache.http.impl.client.CloseableHttpClient

case class TierekisteriUrbanAreaData(roadNumber: Long, startRoadPartNumber: Long, endRoadPartNumber: Long,
                                     track: Track, startAddressMValue: Long, endAddressMValue: Long, assetValue: String) extends TierekisteriAssetData

class TierekisteriUrbanAreaClient(trEndPoint: String, trEnable: Boolean, httpClient: CloseableHttpClient) extends TierekisteriAssetDataClient {
  override def tierekisteriRestApiEndPoint: String = trEndPoint
  override def tierekisteriEnabled: Boolean = trEnable
  override def client: CloseableHttpClient = httpClient
  type TierekisteriType = TierekisteriUrbanAreaData

  override val trAssetType = "tl139"
  private val trUrbanAreaValue = "TIENAS"
  private val defaultUrbanAreaValue = "9"

  override def mapFields(data: Map[String, Any]): Option[TierekisteriUrbanAreaData] = {
    val assetValue = getFieldValue(data, trUrbanAreaValue).orElse(Some(defaultUrbanAreaValue)).get
    val roadNumber = convertToLong(getMandatoryFieldValue(data, trRoadNumber)).get
    val roadPartNumber = convertToLong(getMandatoryFieldValue(data, trRoadPartNumber)).get
    val startMValue = convertToLong(getMandatoryFieldValue(data, trStartMValue)).get
    val endMValue = convertToLong(getMandatoryFieldValue(data, trEndMValue)).get
    val track = convertToInt(getMandatoryFieldValue(data, trTrackCode)).map(Track.apply).getOrElse(Track.Unknown)

    Some(TierekisteriUrbanAreaData(roadNumber, roadPartNumber, roadPartNumber, track, startMValue, endMValue, assetValue))
  }
}

