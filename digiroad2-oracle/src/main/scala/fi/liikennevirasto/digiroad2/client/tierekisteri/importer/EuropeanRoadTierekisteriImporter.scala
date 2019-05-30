package fi.liikennevirasto.digiroad2.client.tierekisteri.importer

import fi.liikennevirasto.digiroad2.asset.{EuropeanRoads, SideCode}
import fi.liikennevirasto.digiroad2.client.tierekisteri.TierekisteriEuropeanRoadAssetClient
import fi.liikennevirasto.digiroad2.client.vvh.VVHRoadlink
import fi.liikennevirasto.digiroad2.dao.{RoadAddressTEMP, RoadAddress => ViiteRoadAddress}
import fi.liikennevirasto.digiroad2.linearasset.RoadLinkLike
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.linearasset.{LinearAssetTypes, Measures}
import org.apache.http.impl.client.HttpClientBuilder

class EuropeanRoadTierekisteriImporter extends LinearAssetTierekisteriImporterOperations {

  override def typeId: Int = EuropeanRoads.typeId
  override def assetName = "europeanRoads"
  override type TierekisteriClientType = TierekisteriEuropeanRoadAssetClient
  override def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)

  override val tierekisteriClient = new TierekisteriEuropeanRoadAssetClient(getProperty("digiroad2.tierekisteriRestApiEndPoint"),
    getProperty("digiroad2.tierekisteri.enabled").toBoolean,
    HttpClientBuilder.create().build())


  override protected def createLinearAssetVKM(vvhRoadlink: RoadLinkLike, roadAddress: RoadAddressTEMP, section: AddressSection, measures: Measures, trAssetData: TierekisteriAssetData): Unit = {
    ???
  }

  protected override def filterTierekisteriAssets(tierekisteriAssetData: TierekisteriAssetData): Boolean = {
    tierekisteriAssetData.assetValue != null && tierekisteriAssetData.assetValue.trim.nonEmpty
  }

  override protected def createLinearAsset(vvhRoadlink: RoadLinkLike, roadAddress: ViiteRoadAddress, section: AddressSection, measures: Measures, trAssetData: TierekisteriAssetData): Unit = {
    val assetId = linearAssetService.dao.createLinearAsset(typeId, vvhRoadlink.linkId, false, SideCode.BothDirections.value,
      measures, "batch_process_" + assetName, vvhClient.roadLinkData.createVVHTimeStamp(), Some(vvhRoadlink.linkSource.value))

    linearAssetService.dao.insertValue(assetId, LinearAssetTypes.europeanRoadPropertyId, trAssetData.assetValue)
    println(s"Created OTH $assetName assets for ${vvhRoadlink.linkId} from TR data with assetId $assetId")
  }
}

