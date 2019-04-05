package fi.liikennevirasto.digiroad2.client.tierekisteri.importer

import fi.liikennevirasto.digiroad2.asset.{CarryingCapacity, SideCode}
import fi.liikennevirasto.digiroad2.client.tierekisteri.TierekisteriCarryingCapacityAssetClient
import fi.liikennevirasto.digiroad2.client.vvh.VVHRoadlink
import fi.liikennevirasto.digiroad2.dao.Queries.{insertDateProperty, insertNumberProperty, insertSingleChoiceProperty}
import fi.liikennevirasto.digiroad2.dao.{DynamicLinearAssetDao, Queries, RoadAddress => ViiteRoadAddress}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.linearasset.{DynamicLinearAssetService, Measures}
import org.apache.http.impl.client.HttpClientBuilder
import org.joda.time.DateTime
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.linearasset.RoadLinkLike

class CarryingCapacityTierekisteriImporter extends LinearAssetTierekisteriImporterOperations {

  lazy val service: DynamicLinearAssetService = new DynamicLinearAssetService(roadLinkService, eventbus)
  lazy val dao: DynamicLinearAssetDao = new DynamicLinearAssetDao

  override def typeId: Int = CarryingCapacity.typeId
  override def assetName = "carryingCapacity"
  override type TierekisteriClientType = TierekisteriCarryingCapacityAssetClient
  override def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)

  override val tierekisteriClient = new TierekisteriCarryingCapacityAssetClient(getProperty("digiroad2.tierekisteriRestApiEndPoint"),
    getProperty("digiroad2.tierekisteri.enabled").toBoolean,
    HttpClientBuilder.create().build())

  override protected def createLinearAsset(vvhRoadlink: RoadLinkLike, roadAddress: ViiteRoadAddress, section: AddressSection, measures: Measures, trAssetData: TierekisteriAssetData): Unit = {
    val assetId = service.dao.createLinearAsset(typeId, vvhRoadlink.linkId, false, SideCode.BothDirections.value, measures, "batch_process_" + assetName,
      vvhClient.roadLinkData.createVVHTimeStamp(), Some(vvhRoadlink.linkSource.value))

    insertNumberProperty(assetId, Queries.getPropertyIdByPublicId("kevatkantavuus"),  trAssetData.springCapacity.getOrElse("").toDouble).execute
    insertSingleChoiceProperty(assetId, Queries.getPropertyIdByPublicId("routivuuskerroin"), trAssetData.factorValue).execute
    trAssetData.measurementDate match {
      case Some(mDate) =>
        insertDateProperty(assetId, Queries.getPropertyIdByPublicId("mittauspaiva"), new DateTime(mDate)).execute
      case _ => None
    }

    println(s"Created OTH $assetName assets for ${vvhRoadlink.linkId} from TR data with assetId $assetId")
  }
}

