package fi.liikennevirasto.digiroad2.service.linearasset

import java.util.NoSuchElementException

import fi.liikennevirasto.digiroad2.dao.linearasset.OracleLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.DigiroadEventBus
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import org.joda.time.DateTime


class OnOffLinearAssetService(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus, dao: OracleLinearAssetDao) extends LinearAssetService(roadLinkServiceImpl, eventBusImpl){

  override def create(newLinearAssets: Seq[NewLinearAsset], typeId: Int, username: String, vvhTimeStamp: Long = vvhClient.roadLinkData.createVVHTimeStamp()): Seq[Long] = {
    withDynTransaction {
      val roadlinks = roadLinkService.getRoadLinksAndComplementariesFromVVH(newLinearAssets.map(_.linkId).toSet, false)
      newLinearAssets.flatMap{ newAsset =>
        if (newAsset.value.toJson == 1) {
          Some(createWithoutTransaction(typeId, newAsset.linkId, newAsset.value, newAsset.sideCode, Measures(newAsset.startMeasure, newAsset.endMeasure), username, vvhTimeStamp, roadlinks.find(_.linkId == newAsset.linkId)))
        } else {
          None
        }
      }
    }
  }
  override def updateValueByExpiration(assetId: Long, valueToUpdate: Value, valuePropertyId: String, username: String, measures: Option[Measures], vvhTimeStamp: Option[Long], sideCode: Option[Int]): Option[Long] = {
    val measure = measures.getOrElse(throw new NoSuchElementException("Missing measures from asset."))

    //Get Old Asset
    val oldAsset =
      valueToUpdate match {
        case NumericValue(intValue) =>
          dao.fetchLinearAssetsByIds(Set(assetId), valuePropertyId).head
        case TextualValue(textValue) =>
          dao.fetchAssetsWithTextualValuesByIds(Set(assetId), valuePropertyId).head
        case _ => return None
      }

    if ((measure.startMeasure == oldAsset.startMeasure) && (measure.endMeasure == oldAsset.endMeasure) && oldAsset.value.contains(valueToUpdate) && vvhTimeStamp.contains(oldAsset.vvhTimeStamp))
      return Some(assetId)

    //Expire the old asset
    dao.updateExpiration(assetId, expired = true, username)
    val roadlink = roadLinkService.getRoadLinkAndComplementaryFromVVH(oldAsset.linkId, false)
        if (valueToUpdate.toJson == 0){
          Seq(Measures(oldAsset.startMeasure, measure.startMeasure), Measures(measure.endMeasure, oldAsset.endMeasure)).map {
            m =>
              if (m.endMeasure - m.startMeasure > 0.01)
                createWithoutTransaction(oldAsset.typeId, oldAsset.linkId, valueToUpdate, sideCode.getOrElse(oldAsset.sideCode),
                  m, username, vvhTimeStamp.getOrElse(vvhClient.roadLinkData.createVVHTimeStamp()), roadlink, true, oldAsset.createdBy, Some(oldAsset.createdDateTime.getOrElse(DateTime.now())), verifiedBy = oldAsset.verifiedBy)
          }
          Some(0L)
        }else{
          Some(createWithoutTransaction(oldAsset.typeId, oldAsset.linkId, valueToUpdate, sideCode.getOrElse(oldAsset.sideCode),
            measure, username, vvhTimeStamp.getOrElse(vvhClient.roadLinkData.createVVHTimeStamp()), roadlink, true, oldAsset.createdBy, Some(oldAsset.createdDateTime.getOrElse(DateTime.now()))))
        }

  }
}
