package fi.liikennevirasto.digiroad2.client.tierekisteri.importer

import fi.liikennevirasto.digiroad2.asset.{CarryingCapacity, SideCode}
import fi.liikennevirasto.digiroad2.client.tierekisteri.TierekisteriCarryingCapacityAssetClient
import fi.liikennevirasto.digiroad2.client.vvh.VVHRoadlink
import fi.liikennevirasto.digiroad2.dao.Queries.{insertDateProperty, insertSingleChoiceProperty, insertTextProperty}
import fi.liikennevirasto.digiroad2.dao.{Queries, RoadAddress => ViiteRoadAddress}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.linearasset.Measures
import org.apache.http.impl.client.HttpClientBuilder
import org.joda.time.DateTime




import slick.driver.JdbcDriver.backend.Database
import Database.dynamicSession
import _root_.oracle.sql.STRUCT
import com.github.tototoshi.slick.MySQLJodaSupport._
import slick.jdbc.StaticQuery.interpolation
import slick.jdbc.{GetResult, PositionedParameters, PositionedResult, SetParameter, StaticQuery => Q}

class CarryingCapacityTierekisteriImporter extends LinearAssetTierekisteriImporterOperations {

  override def typeId: Int = CarryingCapacity.typeId
  override def assetName = "carryingCapacity"
  override type TierekisteriClientType = TierekisteriCarryingCapacityAssetClient
  override def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)

  override val tierekisteriClient = new TierekisteriCarryingCapacityAssetClient(getProperty("digiroad2.tierekisteriRestApiEndPoint"),
    getProperty("digiroad2.tierekisteri.enabled").toBoolean,
    HttpClientBuilder.create().build())


  override protected def createLinearAsset(vvhRoadlink: VVHRoadlink, roadAddress: ViiteRoadAddress, section: AddressSection, measures: Measures, trAssetData: TierekisteriAssetData): Unit = {
    val assetId = multiValuelinearAssetService.dao.createLinearAsset(typeId, vvhRoadlink.linkId, false, SideCode.BothDirections.value, measures, "batch_process_" + assetName,
      vvhClient.roadLinkData.createVVHTimeStamp(), Some(vvhRoadlink.linkSource.value))

    insertTextProperty(assetId, Queries.getPropertyIdByPublicId("kevatkantavuus"), trAssetData.springCapacity.getOrElse("")).execute
    insertSingleChoiceProperty(assetId, Queries.getPropertyIdByPublicId("routivuuskerroin"), trAssetData.factorValue).execute
    trAssetData.measurementDate match {
      case Some(mDate) =>
        insertDateProperty(assetId, Queries.getPropertyIdByPublicId("mittauspaiva"), new DateTime(mDate)).execute
      case _ => None
    }

    println(s"Created OTH $assetName assets for ${vvhRoadlink.linkId} from TR data with assetId $assetId")
  }
}

