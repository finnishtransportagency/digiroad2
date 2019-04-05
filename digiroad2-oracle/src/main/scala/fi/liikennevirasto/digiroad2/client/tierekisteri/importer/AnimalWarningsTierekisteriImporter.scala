package fi.liikennevirasto.digiroad2.client.tierekisteri.importer

import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.tierekisteri.TierekisteriAnimalWarningsAssetClient
import fi.liikennevirasto.digiroad2.client.vvh.VVHRoadlink
import fi.liikennevirasto.digiroad2.dao.{Queries, RoadAddress => ViiteRoadAddress}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.linearasset.{LinearAssetTypes, Measures}
import org.apache.http.impl.client.HttpClientBuilder
import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import fi.liikennevirasto.digiroad2.dao.Queries.insertSingleChoiceProperty
import fi.liikennevirasto.digiroad2.linearasset.RoadLinkLike

class AnimalWarningsTierekisteriImporter extends LinearAssetTierekisteriImporterOperations {

  override def typeId: Int = AnimalWarnings.typeId
  override def assetName = "animalWarnings"
  override type TierekisteriClientType = TierekisteriAnimalWarningsAssetClient
  override def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  override val tierekisteriClient = new TierekisteriAnimalWarningsAssetClient(getProperty("digiroad2.tierekisteriRestApiEndPoint"),
    getProperty("digiroad2.tierekisteri.enabled").toBoolean,
    HttpClientBuilder.create().build())

  val animalWarningPropertyId = "hirvivaro"

  override protected def createLinearAsset(vvhRoadlink: RoadLinkLike, roadAddress: ViiteRoadAddress, section: AddressSection, measures: Measures, trAssetData: TierekisteriAssetData): Unit = {
    val assetId = linearAssetService.dao.createLinearAsset(typeId, vvhRoadlink.linkId, false, SideCode.BothDirections.value,
      measures, "batch_process_" + assetName, vvhClient.roadLinkData.createVVHTimeStamp(), Some(vvhRoadlink.linkSource.value), informationSource = Some(RoadRegistry.value))

    insertSingleChoiceProperty(assetId, Queries.getPropertyIdByPublicId(animalWarningPropertyId), trAssetData.animalWarningValue.value.toLong).execute
    println(s"Created OTH $assetName assets for ${vvhRoadlink.linkId} from TR data with assetId $assetId")
  }
}

