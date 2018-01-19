package fi.liikennevirasto.digiroad2.client.tierekisteri

import fi.liikennevirasto.digiroad2.util.{RoadSide, Track}
import org.apache.http.impl.client.CloseableHttpClient

case class TierekisteriTrafficSignData(roadNumber: Long, startRoadPartNumber: Long, endRoadPartNumber: Long,
                                       track: Track, startAddressMValue: Long, endAddressMValue: Long, roadSide: RoadSide, assetType: TRTrafficSignType, assetValue: String) extends TierekisteriAssetData

class TierekisteriTrafficSignAssetClient(trEndPoint: String, trEnable: Boolean, httpClient: CloseableHttpClient) extends TierekisteriAssetDataClient {
  override def tierekisteriRestApiEndPoint: String = trEndPoint

  override def tierekisteriEnabled: Boolean = trEnable

  override def client: CloseableHttpClient = httpClient

  type TierekisteriType = TierekisteriTrafficSignData

  override val trAssetType = "tl506"
  private val trLMNUMERO = "LMNUMERO"
  private val trLMTEKSTI = "LMTEKSTI"
  private val trPUOLI = "PUOLI"
  private val trLIIKVAST = "LIIKVAST"
  private val trNOPRA506 = "NOPRA506"
  private val wrongSideOfTheRoad = "1"

  override def mapFields(data: Map[String, Any]): Option[TierekisteriTrafficSignData] = {
    val assetValue = getFieldValue(data, trLMTEKSTI).getOrElse("").trim
    //TODO remove the orElse and ignrore the all row when we give support for that on TierekisteriClient base implementation
    val assetNumber = convertToInt(getFieldValue(data, trLMNUMERO).orElse(Some("99"))).get
    val roadNumber = convertToLong(getMandatoryFieldValue(data, trRoadNumber)).get
    val roadPartNumber = convertToLong(getMandatoryFieldValue(data, trRoadPartNumber)).get
    val startMValue = convertToLong(getMandatoryFieldValue(data, trStartMValue)).get
    val track = convertToInt(getMandatoryFieldValue(data, trTrackCode)).map(Track.apply).getOrElse(Track.Unknown)
    val roadSide = convertToInt(getMandatoryFieldValue(data, trPUOLI)).map(RoadSide.apply).getOrElse(RoadSide.Unknown)

    //    if (Seq(TRTrafficSignType.SpeedLimit, TRTrafficSignType.SpeedLimitZone, TRTrafficSignType.UrbanArea).contains(TRTrafficSignType.apply(assetNumber)))
    //    getFieldValue(data, trLIIKVAST) match {
    //      case Some(info) if !(info == wrongSideOfTheRoad) =>
    //        Some(TierekisteriTrafficSignData(roadNumber, roadPartNumber, roadPartNumber, track, startMValue, startMValue, roadSide, TRTrafficSignType.apply(assetNumber), assetValue))
    //      case None =>
    //        val assetValue = getFieldValue(data, trNOPRA506).getOrElse("").trim
    //        Some(TierekisteriTrafficSignData(roadNumber, roadPartNumber, roadPartNumber, track, startMValue, startMValue, roadSide, TRTrafficSignType.apply(assetNumber), assetValue))
    //      case _ => None
    //    } else
    Some(TierekisteriTrafficSignData(roadNumber, roadPartNumber, roadPartNumber, track, startMValue, startMValue, roadSide, TRTrafficSignType.apply(assetNumber), assetValue))

  }
}

