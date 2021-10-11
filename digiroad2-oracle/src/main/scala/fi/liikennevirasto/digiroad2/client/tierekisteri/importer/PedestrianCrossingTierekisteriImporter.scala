package fi.liikennevirasto.digiroad2.client.tierekisteri.importer

import fi.liikennevirasto.digiroad2.GeometryUtils
import fi.liikennevirasto.digiroad2.asset.PedestrianCrossings
import fi.liikennevirasto.digiroad2.client.tierekisteri.TierekisteriPedestrianCrossingAssetClient
import fi.liikennevirasto.digiroad2.client.vvh.{VVHClient, VVHRoadlink}
import fi.liikennevirasto.digiroad2.dao.pointasset.PostGISPedestrianCrossingDao
import fi.liikennevirasto.digiroad2.dao.{RoadAddress => ViiteRoadAddress}
import fi.liikennevirasto.digiroad2.postgis.PostGISDatabase
import fi.liikennevirasto.digiroad2.service.pointasset.IncomingPedestrianCrossing
import fi.liikennevirasto.digiroad2.util.Digiroad2Properties
import org.apache.http.impl.client.HttpClientBuilder


class PedestrianCrossingTierekisteriImporter extends PointAssetTierekisteriImporterOperations {
  override def typeId: Int = PedestrianCrossings.typeId
  override def assetName: String = PedestrianCrossings.label
  override type TierekisteriClientType = TierekisteriPedestrianCrossingAssetClient
  override def withDynSession[T](f: => T): T = PostGISDatabase.withDynSession(f)
  override def withDynTransaction[T](f: => T): T = PostGISDatabase.withDynTransaction(f)

  lazy val dao = new PostGISPedestrianCrossingDao()

  override val tierekisteriClient = new TierekisteriPedestrianCrossingAssetClient(Digiroad2Properties.tierekisteriRestApiEndPoint,
    Digiroad2Properties.tierekisteriEnabled,
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
