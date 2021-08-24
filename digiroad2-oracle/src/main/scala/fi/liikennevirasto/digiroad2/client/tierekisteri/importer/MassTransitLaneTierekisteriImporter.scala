package fi.liikennevirasto.digiroad2.client.tierekisteri.importer

import fi.liikennevirasto.digiroad2.asset.MassTransitLane
import fi.liikennevirasto.digiroad2.client.tierekisteri.{TRLaneArrangementType, TierekisteriMassTransitLaneAssetClient}
import fi.liikennevirasto.digiroad2.client.vvh.VVHRoadlink
import fi.liikennevirasto.digiroad2.dao.{RoadAddress => ViiteRoadAddress}
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.linearasset.{LinearAssetTypes, Measures}
import fi.liikennevirasto.digiroad2.util.Digiroad2Properties
import org.apache.http.impl.client.HttpClientBuilder

class MassTransitLaneTierekisteriImporter extends LinearAssetTierekisteriImporterOperations {

  override def typeId: Int = MassTransitLane.typeId
  override def assetName = "massTransitLane"
  override type TierekisteriClientType = TierekisteriMassTransitLaneAssetClient
  override def withDynSession[T](f: => T): T = PostGISDatabase.withDynSession(f)
  override def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)

  override val tierekisteriClient = new TierekisteriMassTransitLaneAssetClient(Digiroad2Properties.tierekisteriRestApiEndPoint,
    Digiroad2Properties.tierekisteriEnabled,
    HttpClientBuilder.create().build())

  protected override def filterTierekisteriAssets(tierekisteriAssetData: TierekisteriAssetData): Boolean = {
    tierekisteriAssetData.laneType != TRLaneArrangementType.Unknown
  }

  override protected def createLinearAsset(vvhRoadlink: VVHRoadlink, roadAddress: ViiteRoadAddress, section: AddressSection, measures: Measures, trAssetData: TierekisteriAssetData): Unit = {

    val assetId = linearAssetService.dao.createLinearAsset(typeId, vvhRoadlink.linkId, false, getSideCode(roadAddress, trAssetData.track, trAssetData.roadSide).value,
      measures, "batch_process_" + assetName, vvhClient.roadLinkData.createVVHTimeStamp(), Some(vvhRoadlink.linkSource.value), verifiedBy = Some("batch_process_" + assetName))

    linearAssetService.dao.insertValue(assetId, LinearAssetTypes.numericValuePropertyId, 1)
    println(s"Created OTH $assetName assets for ${vvhRoadlink.linkId} from TR data with assetId $assetId")

  }
}

