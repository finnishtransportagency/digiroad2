package fi.liikennevirasto.digiroad2.client.tierekisteri.importer

import fi.liikennevirasto.digiroad2.GeometryUtils
import fi.liikennevirasto.digiroad2.asset.{SideCode, TrWidthLimit}
import fi.liikennevirasto.digiroad2.client.tierekisteri.TierekisteriWidthLimitAssetClient
import fi.liikennevirasto.digiroad2.client.vvh.{VVHClient, VVHRoadlink}
import fi.liikennevirasto.digiroad2.dao.pointasset.PostGISWidthLimitDao
import fi.liikennevirasto.digiroad2.dao.{RoadAddress => ViiteRoadAddress}
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.pointasset.IncomingWidthLimit
import fi.liikennevirasto.digiroad2.util.Digiroad2Properties
import org.apache.http.impl.client.HttpClientBuilder

class WidthLimitTierekisteriImporter extends PointAssetTierekisteriImporterOperations {

  override def typeId: Int = TrWidthLimit.typeId
  override def assetName = "widthLimits"
  override type TierekisteriClientType = TierekisteriWidthLimitAssetClient
  override def withDynSession[T](f: => T): T = PostGISDatabase.withDynSession(f)
  override def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)

  override val tierekisteriClient = new TierekisteriWidthLimitAssetClient(Digiroad2Properties.tierekisteriRestApiEndPoint,
    Digiroad2Properties.tierekisteriEnabled,
    HttpClientBuilder.create().build())

  protected override def createPointAsset(roadAddress: ViiteRoadAddress, vvhRoadlink: VVHRoadlink, mValue: Double, trAssetData: TierekisteriAssetData): Unit = {
    println("create " + roadAddress.linkId)
      GeometryUtils.calculatePointFromLinearReference(vvhRoadlink.geometry, mValue).map{
        point =>
          val widthLimit = IncomingWidthLimit(point.x, point.y, vvhRoadlink.linkId, trAssetData.width, trAssetData.reason,
            SideCode.BothDirections.value, Some(GeometryUtils.calculateBearing(vvhRoadlink.geometry)))
          PostGISWidthLimitDao.create(widthLimit, mValue, vvhRoadlink.municipalityCode, s"batch_process_$assetName",
            VVHClient.createVVHTimeStamp(), vvhRoadlink.linkSource)
      }
  }
}
