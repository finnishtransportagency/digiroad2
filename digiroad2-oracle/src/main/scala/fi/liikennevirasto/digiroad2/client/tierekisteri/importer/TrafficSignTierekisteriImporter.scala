package fi.liikennevirasto.digiroad2.client.tierekisteri.importer

import fi.liikennevirasto.digiroad2.GeometryUtils
import fi.liikennevirasto.digiroad2.asset.{PropertyValue, SimpleProperty}
import fi.liikennevirasto.digiroad2.client.tierekisteri.{TRTrafficSignType, TierekisteriTrafficSignAssetClient}
import fi.liikennevirasto.digiroad2.client.vvh.{VVHClient, VVHRoadlink}
import fi.liikennevirasto.digiroad2.dao.{RoadAddress => ViiteRoadAddress}
import fi.liikennevirasto.digiroad2.dao.pointasset.OracleTrafficSignDao
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.pointasset.{IncomingTrafficSign, TrafficSignTypeGroup}
import org.apache.http.impl.client.HttpClientBuilder
import org.joda.time.DateTime

class TrafficSignTierekisteriImporter extends PointAssetTierekisteriImporterOperations {
  override def typeId: Int = 300
  override def assetName = "trafficSigns"
  override type TierekisteriClientType = TierekisteriTrafficSignAssetClient
  override def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  override val tierekisteriClient = new TierekisteriTrafficSignAssetClient(getProperty("digiroad2.tierekisteriRestApiEndPoint"),
    getProperty("digiroad2.tierekisteri.enabled").toBoolean,
    HttpClientBuilder.create().build())

  private val typePublicId = "trafficSigns_type"
  private val valuePublicId = "trafficSigns_value"
  private val infoPublicId = "trafficSigns_info"

  private val additionalInfoTypeGroups = Set(TrafficSignTypeGroup.GeneralWarningSigns, TrafficSignTypeGroup.ProhibitionsAndRestrictions)
  private val supportedTrafficSigns = Set[TRTrafficSignType](TRTrafficSignType.SpeedLimit, TRTrafficSignType.EndSpeedLimit, TRTrafficSignType.SpeedLimitZone, TRTrafficSignType.EndSpeedLimitZone,
    TRTrafficSignType.UrbanArea, TRTrafficSignType.EndUrbanArea, TRTrafficSignType.PedestrianCrossing, TRTrafficSignType.MaximumLength, TRTrafficSignType.Warning,
    TRTrafficSignType.NoLeftTurn, TRTrafficSignType.NoRightTurn, TRTrafficSignType.NoUTurn, TRTrafficSignType.ClosedToAllVehicles, TRTrafficSignType.NoPowerDrivenVehicles,
    TRTrafficSignType.NoLorriesAndVans, TRTrafficSignType.NoVehicleCombinations, TRTrafficSignType.NoAgriculturalVehicles, TRTrafficSignType.NoMotorCycles, TRTrafficSignType.NoMotorSledges,
    TRTrafficSignType.NoVehiclesWithDangerGoods, TRTrafficSignType.NoBuses, TRTrafficSignType.NoMopeds, TRTrafficSignType.NoCyclesOrMopeds, TRTrafficSignType.NoPedestrians,
    TRTrafficSignType.NoPedestriansCyclesMopeds, TRTrafficSignType.NoRidersOnHorseback, TRTrafficSignType.NoEntry, TRTrafficSignType.OvertakingProhibited, TRTrafficSignType.EndProhibitionOfOvertaking,
    TRTrafficSignType.MaxWidthExceeding, TRTrafficSignType.MaxHeightExceeding, TRTrafficSignType.MaxLadenExceeding, TRTrafficSignType.MaxMassCombineVehiclesExceeding, TRTrafficSignType.MaxTonsOneAxleExceeding,
    TRTrafficSignType.MaxTonsOnBogieExceeding, TRTrafficSignType.WRightBend, TRTrafficSignType.WLeftBend, TRTrafficSignType.WSeveralBendsRight, TRTrafficSignType.WSeveralBendsLeft,
    TRTrafficSignType.WDangerousDescent, TRTrafficSignType.WSteepAscent, TRTrafficSignType.WUnevenRoad, TRTrafficSignType.WChildren)

  private def generateProperties(trAssetData: TierekisteriAssetData) = {
    val trafficType = trAssetData.assetType.trafficSignType
    val typeProperty = SimpleProperty(typePublicId, Seq(PropertyValue(trafficType.value.toString)))
    val valueProperty = additionalInfoTypeGroups.exists(group => group == trafficType.group) match {
      case true => SimpleProperty(infoPublicId, Seq(PropertyValue(trAssetData.assetValue)))
      case _ => SimpleProperty(valuePublicId, Seq(PropertyValue(trAssetData.assetValue)))
    }

    Set(typeProperty, valueProperty)
  }

  protected override def getAllTierekisteriHistoryAddressSection(roadNumber: Long, lastExecution: DateTime) = {
    println("\nFetch " + assetName + " History by Road Number " + roadNumber)
    val trAsset = tierekisteriClient.fetchHistoryAssetData(roadNumber, Some(lastExecution)).filter(_.assetType != TRTrafficSignType.Unknown)

    trAsset.foreach { tr => println(s"TR: address ${tr.roadNumber}/${tr.startRoadPartNumber}-${tr.endRoadPartNumber}/${tr.track.value}/${tr.startAddressMValue}-${tr.endAddressMValue}") }
    trAsset.map(_.asInstanceOf[TierekisteriAssetData]).flatMap(getRoadAddressSections)
  }

  protected override def getAllTierekisteriAddressSections(roadNumber: Long) = {
    println("\nFetch Tierekisteri " + assetName + " by Road Number " + roadNumber)
    val trAsset = tierekisteriClient.fetchActiveAssetData(roadNumber).filter(_.assetType != TRTrafficSignType.Unknown)

    trAsset.foreach { tr => println(s"TR: address ${tr.roadNumber}/${tr.startRoadPartNumber}-${tr.endRoadPartNumber}/${tr.track.value}/${tr.startAddressMValue}-${tr.endAddressMValue}") }
    trAsset.map(_.asInstanceOf[TierekisteriAssetData]).flatMap(getRoadAddressSections)
  }

  protected override def getAllTierekisteriAddressSections(roadNumber: Long, roadPart: Long): Seq[(AddressSection, TierekisteriAssetData)] = {
    println("\nFetch Tierekisteri " + assetName + " by Road Number " + roadNumber)
    val trAsset = tierekisteriClient.fetchActiveAssetData(roadNumber, roadPart).filter(_.assetType != TRTrafficSignType.Unknown)

    trAsset.foreach { tr => println(s"TR: address ${tr.roadNumber}/${tr.startRoadPartNumber}-${tr.endRoadPartNumber}/${tr.track.value}/${tr.startAddressMValue}-${tr.endAddressMValue}") }
    trAsset.map(_.asInstanceOf[TierekisteriAssetData]).flatMap(getRoadAddressSections)
  }

  protected override def createPointAsset(roadAddress: ViiteRoadAddress, vvhRoadlink: VVHRoadlink, mValue: Double, trAssetData: TierekisteriAssetData): Unit = {
    if(supportedTrafficSigns.contains(trAssetData.assetType))
      GeometryUtils.calculatePointFromLinearReference(vvhRoadlink.geometry, mValue).map{
        point =>
          val trafficSign = IncomingTrafficSign(point.x, point.y, vvhRoadlink.linkId, generateProperties(trAssetData),
            getSideCode(roadAddress.sideCode, trAssetData.roadSide).value, Some(GeometryUtils.calculateBearing(vvhRoadlink.geometry)))
          OracleTrafficSignDao.create(trafficSign, mValue, "batch_process_trafficSigns", vvhRoadlink.municipalityCode,
            VVHClient.createVVHTimeStamp(), vvhRoadlink.linkSource)
      }
  }
}
