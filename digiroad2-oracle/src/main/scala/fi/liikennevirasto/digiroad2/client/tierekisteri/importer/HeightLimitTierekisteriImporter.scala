package fi.liikennevirasto.digiroad2.client.tierekisteri.importer

import fi.liikennevirasto.digiroad2.asset.TrHeightLimit
import fi.liikennevirasto.digiroad2.client.tierekisteri.TierekisteriHeightLimitAssetClient
import fi.liikennevirasto.digiroad2.client.vvh.VVHRoadlink
import fi.liikennevirasto.digiroad2.dao.{RoadAddress => ViiteRoadAddress}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import org.apache.http.impl.client.HttpClientBuilder

//TODO change this to point asset importer operations
class HeightLimitTierekisteriImporter extends PointAssetTierekisteriImporterOperations {

  override def typeId: Int = TrHeightLimit.typeId
  override def assetName = "heightLimits"
  override type TierekisteriClientType = TierekisteriHeightLimitAssetClient
  override def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)

  override val tierekisteriClient = new TierekisteriHeightLimitAssetClient(getProperty("digiroad2.tierekisteriRestApiEndPoint"),
    getProperty("digiroad2.tierekisteri.enabled").toBoolean,
    HttpClientBuilder.create().build())

  /*
  override protected def createLinearAsset(vvhRoadlink: VVHRoadlink, roadAddress: ViiteRoadAddress, section: AddressSection, measures: Measures, trAssetData: TierekisteriAssetData): Unit = {
    val assetId =  .dao.createLinearAsset(typeId, vvhRoadlink.linkId, false, SideCode.BothDirections.value,
      measures, "batch_process_" + assetName, vvhClient.roadLinkData.createVVHTimeStamp(), Some(vvhRoadlink.linkSource.value))

    linearAssetService.dao.insertValue(assetId, LinearAssetTypes.numericValuePropertyId, trAssetData.height)
    println(s"Created OTH $assetName assets for ${vvhRoadlink.linkId} from TR data with assetId $assetId")
  }
  */
  private def generateProperties(trAssetData: TierekisteriAssetData) = {


    Set()
  }

  override protected def createPointAsset(roadAddress: ViiteRoadAddress, vvhRoadlink: VVHRoadlink, mValue: Double, trAssetData: TierekisteriAssetData): Unit = {
/*
    GeometryUtils.calculatePointFromLinearReference(vvhRoadlink.geometry, mValue).map{
      point =>
        val trafficSign = IncomingHeightLimit(point.x, point.y, vvhRoadlink.linkId, generateProperties(trAssetData),
          getSideCode(roadAddress.sideCode, trAssetData.roadSide).value, Some(GeometryUtils.calculateBearing(vvhRoadlink.geometry)))
        OracleTrafficSignDao.create(trafficSign, mValue, "batch_process_trafficSigns", vvhRoadlink.municipalityCode,
          VVHClient.createVVHTimeStamp(), vvhRoadlink.linkSource)
    }*/

    //throw new NotImplementedError
  }
}








