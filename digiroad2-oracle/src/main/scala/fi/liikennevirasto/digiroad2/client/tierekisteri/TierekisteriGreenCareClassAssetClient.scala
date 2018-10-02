package fi.liikennevirasto.digiroad2.client.tierekisteri

import fi.liikennevirasto.digiroad2.asset.{DynamicProperty, DynamicPropertyValue, PropertyTypes}
import fi.liikennevirasto.digiroad2.util.Track
import org.apache.http.impl.client.CloseableHttpClient

case class TierekisteriGreenCareClassAssetData(roadNumber: Long, startRoadPartNumber: Long, endRoadPartNumber: Long,
                                                track: Track, startAddressMValue: Long, endAddressMValue: Long, assetValue: Int, publicId: String) extends TierekisteriAssetData

class TierekisteriGreenCareClassAssetClient(trEndPoint: String, trEnable: Boolean, httpClient: CloseableHttpClient) extends TierekisteriAssetDataClient{
  override def tierekisteriRestApiEndPoint: String = trEndPoint
  override def tierekisteriEnabled: Boolean = trEnable
  override def client: CloseableHttpClient = httpClient
  override type TierekisteriType = TierekisteriGreenCareClassAssetData

  override val trAssetType = "tl322"
  private val trCareClass = "VIHERLK"
  private val allowedValues = Seq(1,2,3,4,5,6,7,8)

  override def mapFields(data: Map[String, Any]): Option[TierekisteriGreenCareClassAssetData] = {

    val publicId = "hoitoluokat_viherhoitoluokka"
    val assetValue = convertToInt(getMandatoryFieldValue(data, trCareClass)).get

    //Mandatory field
    val roadNumber = convertToLong(getMandatoryFieldValue(data, trRoadNumber)).get
    val roadPartNumber = convertToLong(getMandatoryFieldValue(data, trRoadPartNumber)).get
    val endRoadPartNumber = convertToLong(getMandatoryFieldValue(data, trEndRoadPartNumber)).getOrElse(roadPartNumber)
    val startMValue = convertToLong(getMandatoryFieldValue(data, trStartMValue)).get
    val endMValue = convertToLong(getMandatoryFieldValue(data, trEndMValue)).get
    val track = convertToInt(getMandatoryFieldValue(data, trTrackCode)).map(Track.apply).getOrElse(Track.Unknown)

    if(allowedValues.contains(assetValue)) {
      Some(TierekisteriGreenCareClassAssetData(roadNumber, roadPartNumber, endRoadPartNumber, track, startMValue, endMValue, assetValue, publicId))
    } else {
      println(s"Tierekisteri ($trAssetType) asset ignored with value $assetValue at $roadNumber / $roadPartNumber")
      None
    }

  }
}
