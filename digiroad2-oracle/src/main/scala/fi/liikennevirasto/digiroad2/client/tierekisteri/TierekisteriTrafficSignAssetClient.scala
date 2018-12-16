package fi.liikennevirasto.digiroad2.client.tierekisteri


import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.util.{RoadSide, Track}
import org.apache.http.impl.client.CloseableHttpClient

case class TierekisteriTrafficSignData(roadNumber: Long, startRoadPartNumber: Long, endRoadPartNumber: Long,
                                       track: Track, startAddressMValue: Long, endAddressMValue: Long, roadSide: RoadSide, assetType: TrafficSignType, assetValue: String) extends TierekisteriAssetData

class TierekisteriTrafficSignAssetClient(trEndPoint: String, trEnable: Boolean, httpClient: CloseableHttpClient) extends TierekisteriAssetDataClient {
  override def tierekisteriRestApiEndPoint: String = trEndPoint

  override def tierekisteriEnabled: Boolean = trEnable

  override def client: CloseableHttpClient = httpClient

  type TierekisteriType = TierekisteriTrafficSignData

  override val trAssetType = "tl506"
  protected val trLMNUMERO = "LMNUMERO"
  protected val trLMTEKSTI = "LMTEKSTI"
  protected val trPUOLI = "PUOLI"
  protected val trLIIKVAST = "LIIKVAST"
  protected val wrongSideOfTheRoad = "1"

  override def mapFields(data: Map[String, Any]): Option[TierekisteriTrafficSignData] = {
    val assetValue = getFieldValue(data, trLMTEKSTI).getOrElse("").trim
    val assetNumber = convertToInt(getFieldValue(data, trLMNUMERO).orElse(Some("99"))).get
    val roadNumber = convertToLong(getMandatoryFieldValue(data, trRoadNumber)).get
    val roadPartNumber = convertToLong(getMandatoryFieldValue(data, trRoadPartNumber)).get
    val startMValue = convertToLong(getMandatoryFieldValue(data, trStartMValue)).get
    val track = convertToInt(getMandatoryFieldValue(data, trTrackCode)).map(Track.apply).getOrElse(Track.Unknown)

    val roadSide: RoadSide = getFieldValue(data, trLIIKVAST) match {
      case Some(sideInfo) if sideInfo == wrongSideOfTheRoad  =>
        RoadSide.switch(convertToInt(getMandatoryFieldValue(data, trPUOLI)).map(RoadSide.apply).getOrElse(RoadSide.Unknown))
      case _ =>
        convertToInt(getMandatoryFieldValue(data, trPUOLI)).map(RoadSide.apply).getOrElse(RoadSide.Unknown)
    }
    Some(TierekisteriTrafficSignData(roadNumber, roadPartNumber, roadPartNumber, track, startMValue, startMValue, roadSide, TrafficSignType.applyvalue(assetNumber), assetValue))
  }
}
class TierekisteriTrafficSignSpeedLimitClient(trEndPoint: String, trEnable: Boolean, httpClient: CloseableHttpClient) extends TierekisteriTrafficSignAssetClient(trEndPoint, trEnable, httpClient) {

  private val trNOPRA506 = "NOPRA506"

  override def mapFields(data: Map[String, Any]): Option[TierekisteriTrafficSignData] = {
    val assetNumber = convertToInt(getFieldValue(data, trLMNUMERO).orElse(Some("99"))).get

    //Check if the traffic sign is in SpeedLimits group
    if (TrafficSignType.applyvalue(assetNumber).group == TrafficSignTypeGroup.SpeedLimits) {
      val roadNumber = convertToLong(getMandatoryFieldValue(data, trRoadNumber)).get
      val roadPartNumber = convertToLong(getMandatoryFieldValue(data, trRoadPartNumber)).get
      val startMValue = convertToLong(getMandatoryFieldValue(data, trStartMValue)).get
      val track = convertToInt(getMandatoryFieldValue(data, trTrackCode)).map(Track.apply).getOrElse(Track.Unknown)
      val roadSide = convertToInt(getMandatoryFieldValue(data, trPUOLI)).map(RoadSide.apply).getOrElse(RoadSide.Unknown)
      val assetValue = getFieldValue(data, trLMTEKSTI).getOrElse(getFieldValue(data, trNOPRA506).getOrElse("")).trim

      getFieldValue(data, trLIIKVAST) match {
        case Some(sideInfo) if sideInfo == wrongSideOfTheRoad && Seq(SpeedLimitSign, SpeedLimitZone, UrbanArea).contains(TrafficSignType.applyvalue(assetNumber)) =>
          None
        case _ =>
          Some(TierekisteriTrafficSignData(roadNumber, roadPartNumber, roadPartNumber, track, startMValue, startMValue, roadSide, TrafficSignType.applyvalue(assetNumber), assetValue))
      }
    }else
      None
  }
}