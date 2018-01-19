package fi.liikennevirasto.digiroad2.client.tierekisteri.importer

import fi.liikennevirasto.digiroad2.client.tierekisteri.TierekisteriSpeedLimitAssetClient
import fi.liikennevirasto.digiroad2.client.vvh.VVHRoadlink
import fi.liikennevirasto.digiroad2.dao.{RoadAddress => ViiteRoadAddress}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.linearasset.{Measures, SpeedLimitService}
import org.apache.http.impl.client.HttpClientBuilder

class SpeedLimitTierekisteriImporter extends LinearAssetTierekisteriImporterOperations{

  lazy val speedLimitService: SpeedLimitService = new SpeedLimitService(eventbus, vvhClient, roadLinkService)

  override def typeId: Int = 20
  override def assetName = "speedlimit"
  override type TierekisteriClientType = TierekisteriSpeedLimitAssetClient
  override def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)

  override val tierekisteriClient = new TierekisteriSpeedLimitAssetClient(getProperty("digiroad2.tierekisteriRestApiEndPoint"),
    getProperty("digiroad2.tierekisteri.enabled").toBoolean,
    HttpClientBuilder.create().build())

  protected def createLinearAsset(vvhRoadlink: VVHRoadlink, roadAddress: ViiteRoadAddress, section: AddressSection, measures: Measures, trAssetData: TierekisteriAssetData): Unit = {
    if (measures.startMeasure != measures.endMeasure) {
      val assetId = speedLimitService.dao.createSpeedLimit("batch_process_speedlimit", vvhRoadlink.linkId, measures, getSideCode(roadAddress.sideCode, trAssetData.roadSide),
        trAssetData.assetValue, Some(vvhClient.roadLinkData.createVVHTimeStamp()), None, None, None, vvhRoadlink.linkSource)

      println(s"Created OTH $assetName assets for ${vvhRoadlink.linkId} from TR data with assetId $assetId")
    }
  }

}

