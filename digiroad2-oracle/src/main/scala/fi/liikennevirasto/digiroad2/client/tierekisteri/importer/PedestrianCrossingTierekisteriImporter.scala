package fi.liikennevirasto.digiroad2.client.tierekisteri.importer

import fi.liikennevirasto.digiroad2.GeometryUtils
import fi.liikennevirasto.digiroad2.asset.PedestrianCrossings
import fi.liikennevirasto.digiroad2.client.tierekisteri.TierekisteriPedestrianCrossingAssetClient
import fi.liikennevirasto.digiroad2.client.vvh.{VVHClient, VVHRoadlink}
import fi.liikennevirasto.digiroad2.dao.pointasset.OraclePedestrianCrossingDao
import fi.liikennevirasto.digiroad2.dao.{RoadAddress => ViiteRoadAddress}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.service.pointasset.IncomingPedestrianCrossing
import org.apache.http.impl.client.HttpClientBuilder


class PedestrianCrossingTierekisteriImporter extends PointAssetTierekisteriImporterOperations {
  override def typeId: Int = PedestrianCrossings.typeId
  override def assetName: String = PedestrianCrossings.label
  override type TierekisteriClientType = TierekisteriPedestrianCrossingAssetClient
  override def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)
  override def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)

  lazy val dao = new OraclePedestrianCrossingDao()

  override val tierekisteriClient = new TierekisteriPedestrianCrossingAssetClient(getProperty("digiroad2.tierekisteriRestApiEndPoint"),
    getProperty("digiroad2.tierekisteri.enabled").toBoolean,
    HttpClientBuilder.create().build())


  protected override def createPointAsset(roadAddress: ViiteRoadAddress, vvhRoadlink: VVHRoadlink, mValue: Double, trAssetData: TierekisteriAssetData): Unit = {

    GeometryUtils.calculatePointFromLinearReference(vvhRoadlink.geometry, mValue).map{
      point =>
        val pedestrianCrossing = IncomingPedestrianCrossing(point.x, point.y, vvhRoadlink.linkId, Set())

        dao.create(pedestrianCrossing, mValue, s"batch_process_$assetName", vvhRoadlink.municipalityCode,
          VVHClient.createVVHTimeStamp(), vvhRoadlink.linkSource)

        println(s"Created OTH $assetName assets for ${vvhRoadlink.linkId} from TR data with assetId $typeId")
    }
  }
}
