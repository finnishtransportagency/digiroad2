package fi.liikennevirasto.digiroad2.client.tierekisteri

import fi.liikennevirasto.digiroad2.util.{RoadSide, Track}
import org.apache.http.impl.client.CloseableHttpClient

class TierekisteriTelematicSpeedLimitClient(trEndPoint: String, trEnable: Boolean, httpClient: CloseableHttpClient) extends TierekisteriAssetDataClient {
  override def tierekisteriRestApiEndPoint: String = trEndPoint
  override def tierekisteriEnabled: Boolean = trEnable
  override def client: CloseableHttpClient = httpClient
  type TierekisteriType = TierekisteriTrafficSignData

  override val trAssetType = "tl523"
  private val trTecPointType = "TEKTYYPPI"
  private val trPUOLI = "PUOLI"
  private val trTelematicSpeedLimitAsset = "34"

  override def mapFields(data: Map[String, Any]): Option[TierekisteriTrafficSignData] = {
    val roadNumber = convertToLong(getMandatoryFieldValue(data, trRoadNumber)).get
    val roadPartNumber = convertToLong(getMandatoryFieldValue(data, trRoadPartNumber)).get
    val startMValue = convertToLong(getMandatoryFieldValue(data, trStartMValue)).get
    val track = convertToInt(getMandatoryFieldValue(data, trTrackCode)).map(Track.apply).getOrElse(Track.Unknown)
    val roadSide = convertToInt(getMandatoryFieldValue(data, trPUOLI)).map(RoadSide.apply).getOrElse(RoadSide.Unknown)

    getMandatoryFieldValue(data, trTecPointType) match {
      case Some(tecType) if tecType == trTelematicSpeedLimitAsset =>
        Some(TierekisteriTrafficSignData(roadNumber, roadPartNumber, roadPartNumber, track, startMValue, startMValue, roadSide, TRTrafficSignType.TelematicSpeedLimit,""))
      case _ =>
        None
    }
  }
}

