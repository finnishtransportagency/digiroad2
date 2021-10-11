package fi.liikennevirasto.digiroad2.client.tierekisteri.importer

import fi.liikennevirasto.digiroad2.asset.{PavedRoad, PavementClass, RoadRegistry, SideCode}
import fi.liikennevirasto.digiroad2.client.tierekisteri.TierekisteriPavedRoadAssetClient
import fi.liikennevirasto.digiroad2.client.vvh.VVHRoadlink
import fi.liikennevirasto.digiroad2.dao.{Queries, RoadAddress => ViiteRoadAddress}
import fi.liikennevirasto.digiroad2.dao.Queries.insertSingleChoiceProperty
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.linearasset.Measures
import org.apache.http.impl.client.HttpClientBuilder
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.util.Digiroad2Properties

class PavedRoadTierekisteriImporter extends LinearAssetTierekisteriImporterOperations {

  override def typeId: Int = PavedRoad.typeId
  override def assetName = "pavedRoad"
  override type TierekisteriClientType = TierekisteriPavedRoadAssetClient
  override def withDynSession[T](f: => T): T = PostGISDatabase.withDynSession(f)
  override def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)
  override val tierekisteriClient = new TierekisteriPavedRoadAssetClient(Digiroad2Properties.tierekisteriRestApiEndPoint,
    Digiroad2Properties.tierekisteriEnabled,
    HttpClientBuilder.create().build())

  val pavementClassPropertyId = "paallysteluokka"

  protected override def filterTierekisteriAssets(tierekisteriAssetData: TierekisteriAssetData): Boolean = {
    tierekisteriAssetData.pavementClass != PavementClass.Unknown
  }

  override protected def createLinearAsset(vvhRoadlink: VVHRoadlink, roadAddress: ViiteRoadAddress, section: AddressSection, measures: Measures, trAssetData: TierekisteriAssetData): Unit = {
    if (trAssetData.pavementClass != PavementClass.Unknown) {
      val assetId = linearAssetService.dao.createLinearAsset(typeId, vvhRoadlink.linkId, false, SideCode.BothDirections.value,
        measures, "batch_process_" + assetName, vvhClient.roadLinkData.createVVHTimeStamp(), Some(vvhRoadlink.linkSource.value), informationSource = Some(RoadRegistry.value))

      insertSingleChoiceProperty(assetId, Queries.getPropertyIdByPublicId(pavementClassPropertyId), trAssetData.pavementClass.value.toLong).execute
      println(s"Created OTH $assetName assets for ${vvhRoadlink.linkId} from TR data with assetId $assetId")
    }
  }
}

