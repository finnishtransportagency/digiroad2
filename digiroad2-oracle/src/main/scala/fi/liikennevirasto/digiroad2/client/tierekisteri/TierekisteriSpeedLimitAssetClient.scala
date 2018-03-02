package fi.liikennevirasto.digiroad2.client.tierekisteri

import fi.liikennevirasto.digiroad2.util.{RoadSide, Track}
import org.apache.http.impl.client.CloseableHttpClient

case class TierekisteriSpeedLimitData(roadNumber: Long, startRoadPartNumber: Long, endRoadPartNumber: Long,
                                      track: Track, startAddressMValue: Long, endAddressMValue: Long, assetValue: Int, roadSide: RoadSide) extends TierekisteriAssetData

class TierekisteriSpeedLimitAssetClient(trEndPoint: String, trEnable: Boolean, httpClient: CloseableHttpClient) extends TierekisteriAssetDataClient {
  override def tierekisteriRestApiEndPoint: String = trEndPoint
  override def tierekisteriEnabled: Boolean = trEnable
  override def client: CloseableHttpClient = httpClient
  type TierekisteriType = TierekisteriSpeedLimitData

  override val trAssetType = "tl168"
  private val trSpeedLimitValue = "NOPRAJ"
  private val trSide = "PUOLI"

  override def mapFields(data: Map[String, Any]): Option[TierekisteriSpeedLimitData] = {

    //Mandatory field
    val assetValue = convertToInt(getMandatoryFieldValue(data, trSpeedLimitValue)).get
    val roadNumber = convertToLong(getMandatoryFieldValue(data, trRoadNumber)).get
    val roadPartNumber = convertToLong(getMandatoryFieldValue(data, trRoadPartNumber)).get
    val endRoadPartNumber = convertToLong(getMandatoryFieldValue(data, trEndRoadPartNumber)).getOrElse(roadPartNumber)
    val startMValue = convertToLong(getMandatoryFieldValue(data, trStartMValue)).get
    val endMValue = convertToLong(getMandatoryFieldValue(data, trEndMValue)).get
    val track = convertToInt(getMandatoryFieldValue(data, trTrackCode)).map(Track.apply).getOrElse(Track.Unknown)
    val roadSide = convertToInt(getMandatoryFieldValue(data, trSide)).map(RoadSide.apply).getOrElse(RoadSide.Unknown)

    Some(TierekisteriSpeedLimitData(roadNumber, roadPartNumber, endRoadPartNumber, track, startMValue, endMValue, assetValue, roadSide))
  }
}

