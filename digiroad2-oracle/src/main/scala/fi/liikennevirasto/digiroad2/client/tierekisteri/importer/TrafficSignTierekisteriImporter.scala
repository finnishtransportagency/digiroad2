package fi.liikennevirasto.digiroad2.client.tierekisteri.importer

import fi.liikennevirasto.digiroad2.{DummyEventBus, GeometryUtils}
import fi.liikennevirasto.digiroad2.asset.{TrafficSignType, _}
import fi.liikennevirasto.digiroad2.client.tierekisteri.{TRTrafficSignType, TierekisteriTrafficSignAssetClient}
import fi.liikennevirasto.digiroad2.client.vvh.{VVHClient, VVHRoadlink}
import fi.liikennevirasto.digiroad2.dao.{RoadAddress => ViiteRoadAddress}
import fi.liikennevirasto.digiroad2.dao.pointasset.OracleTrafficSignDao
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.linearasset._
import fi.liikennevirasto.digiroad2.service.pointasset.{IncomingTrafficSign, TrafficSignCreateAsset, TrafficSignService}
import org.apache.http.impl.client.HttpClientBuilder
import org.joda.time.DateTime

class TrafficSignTierekisteriImporter extends PointAssetTierekisteriImporterOperations {

  lazy val trafficSignService: TrafficSignService = new TrafficSignService(roadLinkService, userProvider, eventbus)
  lazy val manoeuvreService: ManoeuvreService = new ManoeuvreService(roadLinkService)
  lazy val prohibitionService: ProhibitionService = new ProhibitionService(roadLinkService, eventbus)

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
    if(TRTrafficSignType.apply(trAssetData.assetType.value).source.contains("TRimport"))
      GeometryUtils.calculatePointFromLinearReference(vvhRoadlink.geometry, mValue).map{
        point =>
          val trafficSign = IncomingTrafficSign(point.x, point.y, vvhRoadlink.linkId, generateProperties(trAssetData),
            getSideCode(roadAddress, trAssetData.track, trAssetData.roadSide).value, Some(GeometryUtils.calculateBearing(vvhRoadlink.geometry)))

          val newId =  OracleTrafficSignDao.create(trafficSign, mValue, "batch_process_trafficSigns", vvhRoadlink.municipalityCode,
            VVHClient.createVVHTimeStamp(), vvhRoadlink.linkSource)

          roadLinkService.getRoadLinkAndComplementaryFromVVH(vvhRoadlink.linkId, newTransaction = false).map{
            link =>
              TrafficSignType.linkedWith(trafficSignService.getTrafficSignsProperties(trafficSign, typePublicId).get.propertyValue.toInt).foreach { assetTypeInfo =>
                println(s"Creating ${assetTypeInfo.layerName} on linkId: ${vvhRoadlink.linkId} from import traffic sign with id $newId")
                try {
                  createAssetBased(assetTypeInfo.typeId, newId, link)
                } catch {
                  case e: AssetCreationException =>
                    println(s"${assetTypeInfo.layerName} creation error: " + e.response.mkString(" "))
                }
              }
              newId
          }
      }
    println(s"Created OTH $assetName asset on link ${vvhRoadlink.linkId} from TR data")
  }

  protected override def expireAssets(linkIds: Seq[Long]): Unit = {
    val trafficSignsIds = assetDao.getAssetIdByLinks(typeId, linkIds)
    trafficSignsIds.foreach { sign =>
      trafficSignService.expireAssetWithoutTransaction(sign, "batch_process_trafficSigns")
      prohibitionService.deleteFromSign(sign)
      manoeuvreService.deleteManoeuvreFromSign(sign)
    }
  }

  private def createAssetBased(assetType: Int, newId: Long, roadLink: RoadLink):Seq[Long] = {
    assetType match {
      case Prohibition.typeId => prohibitionService.createBasedOnTrafficSign(TrafficSignCreateAsset(trafficSignService.getPersistedAssetsByIdsWithoutTransaction(Set(newId)).head, roadLink), newTransaction = false)
      case Manoeuvres.typeId => manoeuvreService.createBasedOnTrafficSign(TrafficSignCreateAsset(trafficSignService.getPersistedAssetsByIdsWithoutTransaction(Set(newId)).head, roadLink), newTransaction = false)
      case _ => throw new UnsupportedOperationException("Asset property type: " + assetType + " not supported")
    }
  }
}
