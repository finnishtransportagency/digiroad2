package fi.liikennevirasto.digiroad2.client.tierekisteri.importer

import fi.liikennevirasto.digiroad2.asset.{InformationSource, RoadRegistry, RoadWidth, SideCode, SpeedLimitAsset}
import fi.liikennevirasto.digiroad2.client.tierekisteri.TierekisteriRoadWidthAssetClient
import fi.liikennevirasto.digiroad2.client.vvh.VVHRoadlink
import fi.liikennevirasto.digiroad2.dao.Queries.{insertNumberProperty, insertSingleChoiceProperty}
import fi.liikennevirasto.digiroad2.dao.{Queries, RoadAddress => ViiteRoadAddress}
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.linearasset.{LinearAssetTypes, Measures}
import org.apache.http.impl.client.HttpClientBuilder
import slick.jdbc.StaticQuery
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.util.Digiroad2Properties


class RoadWidthTierekisteriImporter extends LinearAssetTierekisteriImporterOperations {

  override def typeId: Int = RoadWidth.typeId
  override def assetName = "roadWidth"
  override type TierekisteriClientType = TierekisteriRoadWidthAssetClient
  override def withDynSession[T](f: => T): T = PostGISDatabase.withDynSession(f)
  override def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)

  override val tierekisteriClient = new TierekisteriRoadWidthAssetClient(Digiroad2Properties.tierekisteriRestApiEndPoint,
    Digiroad2Properties.tierekisteriEnabled,
    HttpClientBuilder.create().build())

  override protected def createLinearAsset(vvhRoadlink: VVHRoadlink, roadAddress: ViiteRoadAddress, section: AddressSection, measures: Measures, trAssetData: TierekisteriAssetData): Unit = {
    if (measures.startMeasure != measures.endMeasure) {
      val assetId = linearAssetService.dao.createLinearAsset(typeId, vvhRoadlink.linkId, false, SideCode.BothDirections.value,
        measures, "batch_process_" + assetName, vvhClient.roadLinkData.createVVHTimeStamp(), Some(vvhRoadlink.linkSource.value), verifiedBy = Some("batch_process_" + assetName), informationSource = Some(RoadRegistry.value), geometry = vvhRoadlink.geometry)

      val propertyId = StaticQuery.query[(String, Int), Long](Queries.propertyIdByPublicIdAndTypeId).apply("width", typeId).first
      insertNumberProperty(assetId, propertyId, trAssetData.assetValue.toLong).execute

      println(s"Created OTH $assetName assets for ${vvhRoadlink.linkId} from TR data with assetId $assetId")
    }
  }
}

