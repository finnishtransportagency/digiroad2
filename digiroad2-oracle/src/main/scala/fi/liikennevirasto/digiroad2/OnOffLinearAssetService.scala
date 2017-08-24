package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.linearasset.oracle.OracleLinearAssetDao
import org.joda.time.DateTime


class OnOffLinearAssetService(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus, dao: OracleLinearAssetDao) extends LinearAssetService(roadLinkServiceImpl, eventBusImpl){

  override def updateValueByExpiration(assetId: Long, valueToUpdate: Value, valuePropertyId: String, username: String, measures: Option[Measures], vvhTimeStamp: Option[Long], sideCode: Option[Int]): Option[Long] = {
    //Get Old Asset
    val oldAsset =
      valueToUpdate match {
        case NumericValue(intValue) =>
          dao.fetchLinearAssetsByIds(Set(assetId), valuePropertyId).head
        case TextualValue(textValue) =>
          dao.fetchAssetsWithTextualValuesByIds(Set(assetId), valuePropertyId).head
        case maintenanceRoad: MaintenanceRoad =>
          dao.fetchMaintenancesByIds(valuePropertyId.toInt, Set(assetId)).head
        case _ => return None
      }

    measures.foreach { measure =>
      if ((measure.startMeasure == oldAsset.startMeasure) && (measure.endMeasure == oldAsset.endMeasure) && oldAsset.value.contains(valueToUpdate) && vvhTimeStamp.contains(oldAsset.vvhTimeStamp))
        return Some(assetId)
    }

    //Expire the old asset
    dao.updateExpiration(assetId, expired = true, username)

    measures.map {
      measure =>
        if (valueToUpdate.toJson == 0){
          Seq(Measures(oldAsset.startMeasure, measure.startMeasure), Measures(measure.endMeasure, oldAsset.endMeasure)).map {
            m =>
              if (m.endMeasure - m.startMeasure > 0.01)
                createWithoutTransaction(oldAsset.typeId, oldAsset.linkId, valueToUpdate, sideCode.getOrElse(oldAsset.sideCode),
                  m, username, vvhTimeStamp.getOrElse(vvhClient.roadLinkData.createVVHTimeStamp()), getLinkSource(oldAsset.linkId), true, oldAsset.createdBy, Some(oldAsset.createdDateTime.getOrElse(DateTime.now())))
          }
        }
        createWithoutTransaction(oldAsset.typeId, oldAsset.linkId, valueToUpdate, sideCode.getOrElse(oldAsset.sideCode),
          measure, username, vvhTimeStamp.getOrElse(vvhClient.roadLinkData.createVVHTimeStamp()), getLinkSource(oldAsset.linkId), true, oldAsset.createdBy, Some(oldAsset.createdDateTime.getOrElse(DateTime.now())))
    }
  }
}
