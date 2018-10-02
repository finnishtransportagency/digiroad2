package fi.liikennevirasto.digiroad2.client.tierekisteri

import java.util.Date
import fi.liikennevirasto.digiroad2.util.Track
import org.apache.http.impl.client.CloseableHttpClient

case class TierekisteriCarryingCapacityData(roadNumber: Long, startRoadPartNumber: Long, endRoadPartNumber: Long,
                                        track: Track, startAddressMValue: Long, endAddressMValue: Long, springCapacity: Option[String],
                                        factorValue: Int, measurementDate: Option[Date]) extends TierekisteriAssetData

class TierekisteriCarryingCapacityAssetClient(trEndPoint: String, trEnable: Boolean, httpClient: CloseableHttpClient) extends TierekisteriAssetDataClient{
  override def tierekisteriRestApiEndPoint: String = trEndPoint
  override def tierekisteriEnabled: Boolean = trEnable
  override def client: CloseableHttpClient = httpClient
  type TierekisteriType = TierekisteriCarryingCapacityData

  override val trAssetType = "tl210"
  private val trSpringCarryingCapacity = "KEVKANT"
  private val trFrostHeavingFactor = "K2"
  private val trDateOfMeasurement = "ALKUPVM"

  override def mapFields(data: Map[String, Any]): Option[TierekisteriCarryingCapacityData] = {
    val springCapacity = getFieldValue(data, trSpringCarryingCapacity)
    val factorValue = convertToInt(getFieldValue(data, trFrostHeavingFactor)).orElse(Some(TRFrostHeavingFactorType.Unknown.value)).get
    val measurementDate = convertToDate(getFieldValue(data, trDateOfMeasurement))

    val roadNumber = convertToLong(getMandatoryFieldValue(data, trRoadNumber)).get
    val roadPartNumber = convertToLong(getMandatoryFieldValue(data, trRoadPartNumber)).get
    val startMValue = convertToLong(getMandatoryFieldValue(data, trStartMValue)).get
    val endMValue = convertToLong(getMandatoryFieldValue(data, trEndMValue)).get
    val track = convertToInt(getMandatoryFieldValue(data, trTrackCode)).map(Track.apply).getOrElse(Track.Unknown)

    Some(TierekisteriCarryingCapacityData(roadNumber, roadPartNumber, roadPartNumber, track, startMValue, endMValue, springCapacity, factorValue, measurementDate))
  }
}

