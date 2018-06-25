package fi.liikennevirasto.digiroad2.client.tierekisteri

import fi.liikennevirasto.digiroad2.util.Track
import org.apache.http.impl.client.CloseableHttpClient

case class TierekisteriPedestrianCrossingAssetData(roadNumber: Long, startRoadPartNumber: Long, endRoadPartNumber: Long,
                                                track: Track, startAddressMValue: Long, endAddressMValue: Long) extends TierekisteriAssetData

class TierekisteriPedestrianCrossingAssetClient(trEndPoint: String, trEnable: Boolean, httpClient: CloseableHttpClient) extends TierekisteriAssetDataClient{
  override def tierekisteriRestApiEndPoint: String = trEndPoint
  override def tierekisteriEnabled: Boolean = trEnable
  override def client: CloseableHttpClient = httpClient
  override type TierekisteriType = TierekisteriPedestrianCrossingAssetData

  override val trAssetType = "tl310"

  override def mapFields(data: Map[String, Any]): Option[TierekisteriPedestrianCrossingAssetData] = {

    //Mandatory field
    val roadNumber = convertToLong(getMandatoryFieldValue(data, trRoadNumber)).get
    val roadPartNumber = convertToLong(getMandatoryFieldValue(data, trRoadPartNumber)).get
    val startMValue = convertToLong(getMandatoryFieldValue(data, trStartMValue)).get
    val track = convertToInt(getMandatoryFieldValue(data, trTrackCode)).map(Track.apply).getOrElse(Track.Unknown)

    Some(TierekisteriPedestrianCrossingAssetData(roadNumber, roadPartNumber, roadPartNumber, track, startMValue, startMValue))
  }
}
