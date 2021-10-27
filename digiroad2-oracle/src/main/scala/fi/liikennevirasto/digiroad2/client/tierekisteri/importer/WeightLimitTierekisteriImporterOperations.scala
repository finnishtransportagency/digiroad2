package fi.liikennevirasto.digiroad2.client.tierekisteri.importer

import fi.liikennevirasto.digiroad2.GeometryUtils
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.client.tierekisteri.TierekisteriWeightLimitAssetClient
import fi.liikennevirasto.digiroad2.client.vvh.{VVHClient, VVHRoadlink}
import fi.liikennevirasto.digiroad2.dao.pointasset.{PostGISAxleWeightLimitDao, PostGISBogieWeightLimitDao, PostGISTrailerTruckWeightLimitDao, PostGISWeightLimitDao}
import fi.liikennevirasto.digiroad2.dao.{RoadAddress => ViiteRoadAddress}
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.pointasset.{IncomingAxleWeightLimit, IncomingBogieWeightLimit, IncomingTrailerTruckWeightLimit, IncomingWeightLimit}
import fi.liikennevirasto.digiroad2.util.Digiroad2Properties
import org.apache.http.impl.client.HttpClientBuilder

trait WeightLimitTierekisteriImporterOperations extends PointAssetTierekisteriImporterOperations {

  override type TierekisteriClientType = TierekisteriWeightLimitAssetClient
  override def withDynSession[T](f: => T): T = PostGISDatabase.withDynSession(f)
  override def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)

  override val tierekisteriClient = new TierekisteriWeightLimitAssetClient(Digiroad2Properties.tierekisteriRestApiEndPoint,
    Digiroad2Properties.tierekisteriEnabled,
    HttpClientBuilder.create().build())
}

class TotalWeightLimitTierekisteriImporter extends WeightLimitTierekisteriImporterOperations {

  override def typeId: Int = TrWeightLimit.typeId
  override def assetName = "totalWeightLimit"

  override protected def filterTierekisteriAssets(tierekisteriAssetData: TierekisteriAssetData): Boolean = {
    tierekisteriAssetData.totalWeight.isDefined
  }

  protected override def createPointAsset(roadAddress: ViiteRoadAddress, vvhRoadlink: VVHRoadlink, mValue: Double, trAssetData: TierekisteriAssetData): Unit = {
    GeometryUtils.calculatePointFromLinearReference(vvhRoadlink.geometry, mValue).map{
      point =>
        val weightLimit = IncomingWeightLimit(point.x, point.y, vvhRoadlink.linkId, trAssetData.totalWeight.get*1000, //convert Ton to Kg
          SideCode.BothDirections.value, Some(GeometryUtils.calculateBearing(vvhRoadlink.geometry)))
        PostGISWeightLimitDao.create(weightLimit, mValue, vvhRoadlink.municipalityCode, s"batch_process_$assetName",
          VVHClient.createVVHTimeStamp(), vvhRoadlink.linkSource)
    }
  }
}

class TrailerTruckWeightLimitTierekisteriImporter extends WeightLimitTierekisteriImporterOperations {

  override def typeId: Int = TrTrailerTruckWeightLimit.typeId
  override def assetName = "trailerTruckWeightLimit"

  override protected def filterTierekisteriAssets(tierekisteriAssetData: TierekisteriAssetData): Boolean = {
    tierekisteriAssetData.trailerTruckWeight.isDefined
  }

  protected override def createPointAsset(roadAddress: ViiteRoadAddress, vvhRoadlink: VVHRoadlink, mValue: Double, trAssetData: TierekisteriAssetData): Unit = {
    GeometryUtils.calculatePointFromLinearReference(vvhRoadlink.geometry, mValue).map{
      point =>
        val trailerTruckWeightLimit = IncomingTrailerTruckWeightLimit(point.x, point.y, vvhRoadlink.linkId, trAssetData.trailerTruckWeight.get*1000, //convert Ton to Kg
          SideCode.BothDirections.value, Some(GeometryUtils.calculateBearing(vvhRoadlink.geometry)))
        PostGISTrailerTruckWeightLimitDao.create(trailerTruckWeightLimit, mValue, vvhRoadlink.municipalityCode, s"batch_process_$assetName",
          VVHClient.createVVHTimeStamp(), vvhRoadlink.linkSource)
    }
  }
}

class AxleWeightLimitTierekisteriImporter extends WeightLimitTierekisteriImporterOperations {

  override def typeId: Int = TrAxleWeightLimit.typeId
  override def assetName = "axleWeightLimit"

  override protected def filterTierekisteriAssets(tierekisteriAssetData: TierekisteriAssetData): Boolean = {
    tierekisteriAssetData.axleWeight.isDefined
  }

  protected override def createPointAsset(roadAddress: ViiteRoadAddress, vvhRoadlink: VVHRoadlink, mValue: Double, trAssetData: TierekisteriAssetData): Unit = {
    GeometryUtils.calculatePointFromLinearReference(vvhRoadlink.geometry, mValue).map{
      point =>
        val axleWeightLimit = IncomingAxleWeightLimit(point.x, point.y, vvhRoadlink.linkId, trAssetData.axleWeight.get*1000, //convert Ton to Kg
          SideCode.BothDirections.value, Some(GeometryUtils.calculateBearing(vvhRoadlink.geometry)))
        PostGISAxleWeightLimitDao.create(axleWeightLimit, mValue, vvhRoadlink.municipalityCode, s"batch_process_$assetName",
          VVHClient.createVVHTimeStamp(), vvhRoadlink.linkSource)
    }
  }
}

class BogieWeightLimitTierekisteriImporter extends WeightLimitTierekisteriImporterOperations {

  override def typeId: Int = TrBogieWeightLimit.typeId
  override def assetName = "bogieWeightLimit"

  override protected def filterTierekisteriAssets(tierekisteriAssetData: TierekisteriAssetData): Boolean = {
    tierekisteriAssetData.bogieWeight.isDefined
  }

  protected override def createPointAsset(roadAddress: ViiteRoadAddress, vvhRoadlink: VVHRoadlink, mValue: Double, trAssetData: TierekisteriAssetData): Unit = {
    GeometryUtils.calculatePointFromLinearReference(vvhRoadlink.geometry, mValue).map{
      point =>
        val bogieWeightLimit = IncomingBogieWeightLimit(point.x, point.y, vvhRoadlink.linkId, trAssetData.bogieWeight.get*1000, //convert Ton to Kg
          SideCode.BothDirections.value, Some(GeometryUtils.calculateBearing(vvhRoadlink.geometry)))
        PostGISBogieWeightLimitDao.create(bogieWeightLimit, mValue, vvhRoadlink.municipalityCode, s"batch_process_$assetName",
          VVHClient.createVVHTimeStamp(), vvhRoadlink.linkSource)
    }
  }
}
