package fi.liikennevirasto.digiroad2.client.tierekisteri.importer

import fi.liikennevirasto.digiroad2.{GeometryUtils, PointAssetOperations}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.tierekisteri.{TRTrafficSignType, TierekisteriTrafficSignAssetClient}
import fi.liikennevirasto.digiroad2.client.vvh.{VVHClient, VVHRoadlink}
import fi.liikennevirasto.digiroad2.dao.{RoadAddress => ViiteRoadAddress}
import fi.liikennevirasto.digiroad2.dao.pointasset.OracleTrafficSignDao
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.linearasset.{ManoeuvreCreationException, ManoeuvreProvider, ManoeuvreService}
import fi.liikennevirasto.digiroad2.service.pointasset.{IncomingTrafficSign, TrafficSignService, TrafficSignTypeGroup}
import org.apache.http.impl.client.HttpClientBuilder
import org.joda.time.DateTime

class TrafficSignTierekisteriImporter extends PointAssetTierekisteriImporterOperations {

  lazy val trafficSignService: TrafficSignService = new TrafficSignService(roadLinkService, userProvider, eventbus)
  lazy val manoeuvreService: ManoeuvreService = new ManoeuvreService(roadLinkService)

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

  private val additionalInfoTypeGroups = Set(TrafficSignTypeGroup.GeneralWarningSigns, TrafficSignTypeGroup.ProhibitionsAndRestrictions, TrafficSignTypeGroup.AdditionalPanels)

  private def generateProperties(trAssetData: TierekisteriAssetData) = {
    val trafficType = trAssetData.assetType.trafficSignType
    val typeProperty = SimpleTrafficSignProperty(typePublicId, Seq(TextPropertyValue(trafficType.value.toString)))
    val valueProperty = additionalInfoTypeGroups.exists(group => group == trafficType.group) match {
      case true => SimpleTrafficSignProperty(infoPublicId, Seq(TextPropertyValue(trAssetData.assetValue)))
      case _ => SimpleTrafficSignProperty(valuePublicId, Seq(TextPropertyValue(trAssetData.assetValue)))
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
    if(TRTrafficSignType.apply(trAssetData.assetType.value).source.contains("TRimport"))
      GeometryUtils.calculatePointFromLinearReference(vvhRoadlink.geometry, mValue).map{
        point =>
          val trafficSign = IncomingTrafficSign(point.x, point.y, vvhRoadlink.linkId, generateProperties(trAssetData),
            getSideCode(roadAddress, trAssetData.track, trAssetData.roadSide).value, Some(GeometryUtils.calculateBearing(vvhRoadlink.geometry)))

          val newId =  OracleTrafficSignDao.create(trafficSign, mValue, "batch_process_trafficSigns", vvhRoadlink.municipalityCode,
            VVHClient.createVVHTimeStamp(), vvhRoadlink.linkSource)

          roadLinkService.getRoadLinkAndComplementaryFromVVH(vvhRoadlink.linkId, newTransaction = false).map{
            link =>
              if (trafficSignService.belongsToTurnRestriction(trafficSign)) {
                println(s"Creating manoeuvre on linkId: ${vvhRoadlink.linkId} from import traffic sign with id $newId" )
                try {
                  manoeuvreService.createManoeuvreBasedOnTrafficSign(ManoeuvreProvider(trafficSignService.getPersistedAssetsByIdsWithoutTransaction(Set(newId)).head, link), newTransaction = false)
                }catch {
                  case e: ManoeuvreCreationException =>
                    println("Manoeuvre creation error: " + e.response.mkString(" "))
                }
              }
              newId
          }
      }
    println(s"Created OTH $assetName asset on link ${vvhRoadlink.linkId} from TR data")
  }

  protected override def expireAssets(linkIds: Seq[Long]): Unit = {
    val trafficSignsIds = assetDao.getAssetIdByLinks(typeId, linkIds)
    trafficSignsIds.foreach( sign => trafficSignService.expireAssetWithoutTransaction(sign, "batch_process_trafficSigns"))
  }
}
