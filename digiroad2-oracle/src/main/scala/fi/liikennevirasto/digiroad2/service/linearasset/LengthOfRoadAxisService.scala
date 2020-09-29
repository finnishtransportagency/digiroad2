package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.DigiroadEventBus
import fi.liikennevirasto.digiroad2.client.vvh.VVHClient
import fi.liikennevirasto.digiroad2.dao.{DynamicLinearAssetDao, MunicipalityDao, OracleAssetDao}
import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset.{DynamicValue, LengthOfRoadAxisCreate, LengthOfRoadAxisUpdate, NewLinearAsset, RoadLinkLike, Value}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.PolygonTools
import org.apache.commons.lang3.NotImplementedException
import org.joda.time.DateTime



class LengthOfRoadAxisService(roadLinkServiceImpl: RoadLinkService,
                              eventBusImpl: DigiroadEventBus) extends DynamicLinearAssetService(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) {

  //crud

  override def createWithoutTransaction(typeId: Int, linkId: Long, value: Value, sideCode: Int, measures: Measures, username: String,
                                        vvhTimeStamp: Long = vvhClient.roadLinkData.createVVHTimeStamp(),
                                        roadLink: Option[RoadLinkLike], fromUpdate: Boolean = false,
                                        createdByFromUpdate: Option[String] = Some(""),
                                        createdDateTimeFromUpdate: Option[DateTime] = Some(DateTime.now()),
                                        verifiedBy: Option[String] = None, informationSource: Option[Int] = None): Long = {

    val id = dao.createLinearAsset(typeId, linkId, expired = false, sideCode, measures, username,
      vvhTimeStamp, getLinkSource(roadLink), fromUpdate, createdByFromUpdate,
      createdDateTimeFromUpdate, verifiedBy, informationSource = informationSource)

    value match {
      case DynamicValue(multiTypeProps) =>
        val properties = setPropertiesDefaultValues(multiTypeProps.properties, roadLink)
        val defaultValues = dynamicLinearAssetDao.propertyDefaultValues(typeId).filterNot(
                            defaultValue => properties.exists(_.publicId == defaultValue.publicId))
        val props = properties ++ defaultValues.toSet
        validateRequiredProperties(typeId, props)
        dynamicLinearAssetDao.updateAssetProperties(id, props, typeId)
      case _ => None
    }
    id
  }

  /// create
  def createRoadwayLinear
  (listOfAsset: List[LengthOfRoadAxisCreate],
   username: String,
   vvhTimeStamp: Long = vvhClient.roadLinkData.
     createVVHTimeStamp()) = {
    withDynTransaction {
      for (item <- listOfAsset) yield {
        item.assetSequence.map{asset=>
          val roadlink = roadLinkService.getRoadLinksAndComplementariesFromVVH(Set(asset.linkId), false).
            find(_.linkId == asset.linkId)
          super.createWithoutTransaction(item.typeId,asset.linkId ,asset.value,sideCode = 0,
            measures = Measures(asset.startMeasure,asset.startMeasure),
            username, vvhTimeStamp, roadlink,
            verifiedBy = getVerifiedBy(username, item.typeId))
        }
      }
    }
  }
  //TODO unit test
  def expireRoadwayLinear
  (listOfAsset: List[LengthOfRoadAxisUpdate],
   username: String
  ) = {
   withDynTransaction{
      for (item <- listOfAsset) yield {super.expire(item.ids, username)}
    }
  }

  /// Update
  def updateRoadwayLinear
  (listOfAsset: List[LengthOfRoadAxisUpdate],
   username: String
  ) = {
     withDynTransaction{
       for (item <- listOfAsset) yield {super.updateWithoutTransaction(item.ids, item.value, username)}
     }
  }
}