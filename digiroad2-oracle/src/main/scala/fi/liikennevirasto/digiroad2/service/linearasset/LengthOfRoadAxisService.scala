package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.DigiroadEventBus
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.dao.{DynamicLinearAssetDao, MunicipalityDao, OracleAssetDao}
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset.{NewLinearAsset, Value}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.PolygonTools
import org.apache.commons.lang3.NotImplementedException

case class assestCreateDTO(typeId: Int, assetSequence: Seq[NewLinearAsset]) {}
case class assestUpdateDTO(ids:Seq[Long], value:Value) {}
case class assestExpireDTO(ids:Seq[Long]) {}

class LengthOfRoadAxisService(roadLinkServiceImpl: RoadLinkService,
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
  def getRaodwayLinear(): Unit = {


    val id: Seq[Long] = Seq(0, 1)
    id
    throw new NotImplementedError
  }

  /// create
  //[typeId:int, assetSequence: Seq[NewLinearAsset]]
  def createRoadwayLinear
  (listOfAsset: List[assestCreateDTO],
   username: String,
   vvhTimeStamp: Long = vvhClient.roadLinkData.
     createVVHTimeStamp()) = {
    val addedElementId = for (item <- listOfAsset) yield {
       super.create(item.assetSequence, item.typeId, username, vvhTimeStamp)
    }
    addedElementId
  }

  def expireRoadwayLinear
  (mapOfAsset: List[assestUpdateDTO],
   username: String
  ) = {
    val expiredElement=   for (item <- mapOfAsset) yield {super.expire(item.ids, username)}
    expiredElement
  }

  /// Update
  def updateRoadwayLinear
  (mapOfAsset: List[assestUpdateDTO],
   username: String
  ) = {
   val modifiedElement=   for (item <- mapOfAsset) yield {super.update(item.ids, item.value, username)}
    modifiedElement
  }
}