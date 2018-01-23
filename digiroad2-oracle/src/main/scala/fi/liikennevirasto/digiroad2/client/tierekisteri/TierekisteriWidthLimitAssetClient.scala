package fi.liikennevirasto.digiroad2.client.tierekisteri

import fi.liikennevirasto.digiroad2.dao.pointasset.WidthLimitReason
import fi.liikennevirasto.digiroad2.util.{RoadSide, Track}
import org.apache.http.impl.client.CloseableHttpClient

case class TierekisteriWidthLimitData(roadNumber: Long, startRoadPartNumber: Long, endRoadPartNumber: Long,
                                      track: Track, startAddressMValue: Long, endAddressMValue: Long, roadSide: RoadSide, width: Int, reason: WidthLimitReason) extends TierekisteriAssetData

class TierekisteriWidthLimitAssetClient(trEndPoint: String, trEnable: Boolean, httpClient: CloseableHttpClient) extends TierekisteriAssetDataClient{
  override def tierekisteriRestApiEndPoint: String = trEndPoint
  override def tierekisteriEnabled: Boolean = trEnable
  override def client: CloseableHttpClient = httpClient
  type TierekisteriType = TierekisteriWidthLimitData

  override val trAssetType = "tl264"
  private val trWidth = "MAXLEV"
  private val trRoadSide = "PUOLI"
  private val trReason = "LEVRAJTY"

  override def mapFields(data: Map[String, Any]): Option[TierekisteriWidthLimitData] = {
    val widthValue = convertToInt(getFieldValue(data, trWidth)).get

    //Mandatory field
    val roadNumber = convertToLong(getMandatoryFieldValue(data, trRoadNumber)).get
    val roadPartNumber = convertToLong(getMandatoryFieldValue(data, trRoadPartNumber)).get
    val endRoadPartNumber = convertToLong(getMandatoryFieldValue(data, trEndRoadPartNumber)).getOrElse(roadPartNumber)
    val startMValue = convertToLong(getMandatoryFieldValue(data, trStartMValue)).get
    val endMValue = convertToLong(getMandatoryFieldValue(data, trEndMValue)).get
    val track = convertToInt(getMandatoryFieldValue(data, trTrackCode)).map(Track.apply).getOrElse(Track.Unknown)
    val roadSide = convertToInt(getMandatoryFieldValue(data, trRoadSide)).map(RoadSide.apply).getOrElse(RoadSide.Unknown)
    val reason = convertToInt(getMandatoryFieldValue(data, trReason)).map(WidthLimitReason.apply).getOrElse(WidthLimitReason.Unknown)

    Some(TierekisteriWidthLimitData(roadNumber, roadPartNumber, endRoadPartNumber, track, startMValue, endMValue, roadSide, widthValue, reason))
  }
}