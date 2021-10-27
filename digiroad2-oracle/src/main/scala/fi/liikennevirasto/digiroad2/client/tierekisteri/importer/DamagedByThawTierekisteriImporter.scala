package fi.liikennevirasto.digiroad2.client.tierekisteri.importer

import fi.liikennevirasto.digiroad2.asset.{DamagedByThaw, SideCode}
import fi.liikennevirasto.digiroad2.client.tierekisteri.TierekisteriDamagedByThawAssetClient
import fi.liikennevirasto.digiroad2.client.vvh.VVHRoadlink
import fi.liikennevirasto.digiroad2.dao.{RoadAddress => ViiteRoadAddress}
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.linearasset.{LinearAssetTypes, Measures}
import fi.liikennevirasto.digiroad2.util.Digiroad2Properties
import org.apache.http.impl.client.HttpClientBuilder

class DamagedByThawTierekisteriImporter extends LinearAssetTierekisteriImporterOperations {

  override def typeId: Int = DamagedByThaw.typeId
  override def assetName = "damagedByThaw"
  override type TierekisteriClientType = TierekisteriDamagedByThawAssetClient
  override def withDynSession[T](f: => T): T = PostGISDatabase.withDynSession(f)
  override def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)

  override val tierekisteriClient = new TierekisteriDamagedByThawAssetClient(Digiroad2Properties.tierekisteriRestApiEndPoint,
    Digiroad2Properties.tierekisteriEnabled,
    HttpClientBuilder.create().build())

  override protected def createLinearAsset(vvhRoadlink: VVHRoadlink, roadAddress: ViiteRoadAddress, section: AddressSection, measures: Measures, trAssetData: TierekisteriAssetData): Unit = {
    val assetId = linearAssetService.dao.createLinearAsset(typeId, vvhRoadlink.linkId, false, SideCode.BothDirections.value,
      measures, "batch_process_" + assetName, vvhClient.roadLinkData.createVVHTimeStamp(), Some(vvhRoadlink.linkSource.value))

    linearAssetService.dao.insertValue(assetId, LinearAssetTypes.numericValuePropertyId, 1)
    trAssetData.weight match {
      case Some(value) =>
        linearAssetService.dao.insertValue(assetId, LinearAssetTypes.damagedByThawPropertyId, value * 1000) //convert Ton to Kg
      case _ => None
    }
    println(s"Created OTH $assetName assets for ${vvhRoadlink.linkId} from TR data with assetId $assetId")
  }
}
