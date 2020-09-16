package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.DigiroadEventBus
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.dao.{DynamicLinearAssetDao, MunicipalityDao, OracleAssetDao}
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset.{NewLinearAsset, Value}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.PolygonTools
import org.apache.commons.lang3.NotImplementedException

class LengthOfRoadAxisService (roadLinkServiceImpl: RoadLinkService,
                         eventBusImpl: DigiroadEventBus) extends DynamicLinearAssetService(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) {
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl

  override def dao: OracleLinearAssetDao = new OracleLinearAssetDao(roadLinkServiceImpl.vvhClient, roadLinkServiceImpl)

  override def municipalityDao: MunicipalityDao = new MunicipalityDao

  override def eventBus: DigiroadEventBus = eventBusImpl

  override def vvhClient: VVHClient = roadLinkServiceImpl.vvhClient

  override def polygonTools: PolygonTools = new PolygonTools()

  override def assetDao: OracleAssetDao = new OracleAssetDao

  override def dynamicLinearAssetDao: DynamicLinearAssetDao = new DynamicLinearAssetDao

  //crud

  /// get
  def get(): Unit = {


    val id: Seq[Long] =Seq(0,1)
    id
    throw new NotImplementedError
  }

  /// create
  def create[typeId: Int, assetSequence: Seq[NewLinearAsset]]
  (mapOfAsset: Map[typeId, assetSequence],
   username: String,
   vvhTimeStamp: Long = vvhClient.roadLinkData.
     createVVHTimeStamp())= {
    try{
      for ((typeId: Int, assetSequence: Seq[NewLinearAsset]) <- mapOfAsset) {
        super.create(assetSequence, typeId, username, vvhTimeStamp)
      }
    }catch {
      case e:Exception =>println("error"+e.getMessage)
    }
  }

  /// Delete
  //override def expire(ids: Seq[Long], username: String): Seq[Long] = {
   // if (ids.nonEmpty)
    //  logger.info("Expiring ids " + ids.mkString(", "))
   // withDynTransaction {
    //  ids.foreach(dao.updateExpiration(_, true, username))
     // ids
   // }
  //}

  /// Update
  def update[value: Value, ids: Seq[Long]]
  (mapOfAsset: Map[value, ids],
   username: String
  )= {
    try{
      for ((value: Value, ids: Seq[Long]) <- mapOfAsset) {
        super.update(ids, value, username)
      }
    }catch {
      case e:Exception =>println("error"+e.getMessage)
    }
  }
}