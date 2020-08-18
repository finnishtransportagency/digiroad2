package fi.liikennevirasto.digiroad2.client.tierekisteri


import fi.liikennevirasto.digiroad2._
import fi.liikennevirasto.digiroad2.util.{RoadSide, Track}
import org.apache.http.impl.client.CloseableHttpClient

case class TierekisteriTrafficSignData(roadNumber: Long, startRoadPartNumber: Long, endRoadPartNumber: Long,
                                       track: Track, startAddressMValue: Long, endAddressMValue: Long, roadSide: RoadSide, assetType: TrafficSignType, assetValue: String, signSidePlacement: Option[String] = None) extends TierekisteriAssetData

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
  protected val againstTraffic = "1"
  protected val alongTheRoad = "2"
  protected val trNOPRA506 = "NOPRA506"

  override def mapFields(data: Map[String, Any]): Option[TierekisteriTrafficSignData] = {
    val assetValue = getFieldValue(data, trLMTEKSTI).getOrElse("").trim
    val assetNumber = convertToInt(getFieldValue(data, trLMNUMERO).orElse(Some("99"))).get
    val roadNumber = convertToLong(getMandatoryFieldValue(data, trRoadNumber)).get
    val roadPartNumber = convertToLong(getMandatoryFieldValue(data, trRoadPartNumber)).get
    val startMValue = convertToLong(getMandatoryFieldValue(data, trStartMValue)).get
    val track = convertToInt(getMandatoryFieldValue(data, trTrackCode)).map(Track.apply).getOrElse(Track.Unknown)
    val signSidePlacement = getFieldValue(data, trLIIKVAST).orElse(Some("0"))

    val roadSide: RoadSide = signSidePlacement match {
      case Some(sideInfo) if sideInfo == againstTraffic  =>
        RoadSide.switch(convertToInt(getMandatoryFieldValue(data, trPUOLI)).map(RoadSide.apply).getOrElse(RoadSide.Unknown))
      case _ =>
        convertToInt(getMandatoryFieldValue(data, trPUOLI)).map(RoadSide.apply).getOrElse(RoadSide.Unknown)
    }

    Some(TierekisteriTrafficSignData(roadNumber, roadPartNumber, roadPartNumber, track, startMValue, startMValue, roadSide, TrafficSignType.applyTRValue(assetNumber), assetValue, signSidePlacement))
  }
}
class TierekisteriTrafficSignAssetSpeedLimitClient(trEndPoint: String, trEnable: Boolean, httpClient: CloseableHttpClient) extends TierekisteriTrafficSignAssetClient(trEndPoint, trEnable, httpClient) {

  override def mapFields(data: Map[String, Any]): Option[TierekisteriTrafficSignData] = {
    val assetNumber = convertToInt(getFieldValue(data, trLMNUMERO).orElse(Some("99"))).get
    val signSidePlacement = getFieldValue(data, trLIIKVAST)

    //Check if the traffic sign is in SpeedLimits group
    val sign = TrafficSignType.applyTRValue(assetNumber)
    if ( sign.isSpeedLimit || !signSidePlacement.contains(alongTheRoad)) {
      val roadNumber = convertToLong(getMandatoryFieldValue(data, trRoadNumber)).get
      val roadPartNumber = convertToLong(getMandatoryFieldValue(data, trRoadPartNumber)).get
      val startMValue = convertToLong(getMandatoryFieldValue(data, trStartMValue)).get
      val track = convertToInt(getMandatoryFieldValue(data, trTrackCode)).map(Track.apply).getOrElse(Track.Unknown)
      val roadSide = convertToInt(getMandatoryFieldValue(data, trPUOLI)).map(RoadSide.apply).getOrElse(RoadSide.Unknown)
      val assetValue = getFieldValue(data, trLMTEKSTI).getOrElse(getFieldValue(data, trNOPRA506).getOrElse("")).trim
      val signSidePlacement = getFieldValue(data, trLIIKVAST).orElse(Some("0"))

      signSidePlacement match {
        case Some(sideInfo) if sideInfo == againstTraffic && Seq(SpeedLimitSign, SpeedLimitZone, UrbanArea).contains(sign) =>
          None
        case _ =>
          Some(TierekisteriTrafficSignData(roadNumber, roadPartNumber, roadPartNumber, track, startMValue, startMValue, roadSide, sign, assetValue, signSidePlacement))
      }
    }else
      None
  }
}

class TierekisteriTrafficSignGroupClient(trEndPoint: String, trEnable: Boolean, httpClient: CloseableHttpClient)(filterGroupCondition: Int => Boolean) extends TierekisteriTrafficSignAssetClient(trEndPoint: String, trEnable: Boolean, httpClient: CloseableHttpClient) {

  def getAssetValue(data: Map[String, Any], assetNumber: Int): String = {
    getFieldValue(data, trLMTEKSTI).getOrElse("").trim
  }

  override def mapFields(data: Map[String, Any]): Option[TierekisteriTrafficSignData] = {
    val assetNumber = convertToInt(getFieldValue(data, trLMNUMERO).orElse(Some("99"))).get
    val signSidePlacement = getFieldValue(data, trLIIKVAST)

    if (filterGroupCondition(assetNumber) || !signSidePlacement.contains(alongTheRoad)) {
      val assetValue = getAssetValue(data, assetNumber)
      val roadNumber = convertToLong(getMandatoryFieldValue(data, trRoadNumber)).get
      val roadPartNumber = convertToLong(getMandatoryFieldValue(data, trRoadPartNumber)).get
      val startMValue = convertToLong(getMandatoryFieldValue(data, trStartMValue)).get
      val track = convertToInt(getMandatoryFieldValue(data, trTrackCode)).map(Track.apply).getOrElse(Track.Unknown)
      val signSidePlacement = getFieldValue(data, trLIIKVAST).orElse(Some("0"))

      val roadSide: RoadSide = signSidePlacement match {
        case Some(sideInfo) if sideInfo == againstTraffic =>
          RoadSide.switch(convertToInt(getMandatoryFieldValue(data, trPUOLI)).map(RoadSide.apply).getOrElse(RoadSide.Unknown))
        case _ =>
          convertToInt(getMandatoryFieldValue(data, trPUOLI)).map(RoadSide.apply).getOrElse(RoadSide.Unknown)
      }
      Some(TierekisteriTrafficSignData(roadNumber, roadPartNumber, roadPartNumber, track, startMValue, startMValue, roadSide, TrafficSignType.applyTRValue(assetNumber), assetValue, signSidePlacement))
    } else
      None
  }
}

class SpeedLimitTrafficSignClient(trEndPoint: String, trEnable: Boolean, httpClient: CloseableHttpClient)(filterGroupCondition: Int => Boolean) extends TierekisteriTrafficSignGroupClient(trEndPoint: String, trEnable: Boolean, httpClient: CloseableHttpClient)(filterGroupCondition: Int => Boolean)  {
  val speedLimitSignsWithValue: Seq[Int] = Seq(SpeedLimitSign.TRvalue, EndSpeedLimit.TRvalue, SpeedLimitZone.TRvalue, EndSpeedLimitZone.TRvalue)
  override def getAssetValue(data: Map[String, Any], assetNumber: Int): String = {
    if(speedLimitSignsWithValue.contains(assetNumber))
      getFieldValue(data, trNOPRA506).getOrElse(getFieldValue(data, trLMTEKSTI).getOrElse("").trim)
    else
      super.getAssetValue(data, assetNumber)
  }
}