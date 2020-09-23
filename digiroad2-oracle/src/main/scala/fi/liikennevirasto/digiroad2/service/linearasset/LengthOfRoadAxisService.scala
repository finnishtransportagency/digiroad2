package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.DigiroadEventBus
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.dao.{DynamicLinearAssetDao, MunicipalityDao, OracleAssetDao}
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset.{NewLinearAsset, Value, LengthOfRoadAxisCreate, LengthOfRoadAxisUpdate}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.PolygonTools
import org.apache.commons.lang3.NotImplementedException



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
  (listOfAsset: List[LengthOfRoadAxisCreate],
   username: String,
   vvhTimeStamp: Long = vvhClient.roadLinkData.
     createVVHTimeStamp()) = {
    val addedElementId = for (item <- listOfAsset) yield {
      item.assetSequence.map{asset=>
        val roadlink = roadLinkService.getRoadLinksAndComplementariesFromVVH(Set(asset.linkId), false)
        super.createWithoutTransaction(item.typeId,asset.linkId ,asset.value,sideCode = 0,measures = Measures(asset.startMeasure,asset.startMeasure),
          username, vvhTimeStamp, roadlink.find(_.linkId == asset.linkId), verifiedBy = getVerifiedBy(username, item.typeId))
      }
      }

    eventBusImpl.publish("LengthOfRoadAxisService:create",addedElementId.toSet)
    addedElementId
  }

  def expireRoadwayLinear
  (mapOfAsset: List[LengthOfRoadAxisUpdate],
   username: String
  ) = {
    val expiredElement=   for (item <- mapOfAsset) yield {super.expire(item.ids, username)}
    eventBusImpl.publish("LengthOfRoadAxisService:expire",expiredElement.toSet)
    expiredElement
  }

  /// Update
  def updateRoadwayLinear
  (mapOfAsset: List[LengthOfRoadAxisUpdate],
   username: String
  ) = {
   val modifiedElement=   for (item <- mapOfAsset) yield {super.update(item.ids, item.value, username)}

    eventBusImpl.publish("LengthOfRoadAxisService:update",modifiedElement.toSet)
    modifiedElement
  }
}