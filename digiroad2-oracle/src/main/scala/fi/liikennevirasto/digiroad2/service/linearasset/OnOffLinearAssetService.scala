package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.dao.linearasset.PostGISLinearAssetDao
import fi.liikennevirasto.digiroad2.linearasset._
import fi.liikennevirasto.digiroad2.DigiroadEventBus
import fi.liikennevirasto.digiroad2.dao.{MunicipalityDao, PostGISAssetDao}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import fi.liikennevirasto.digiroad2.util.PolygonTools
import org.joda.time.DateTime


class OnOffLinearAssetService(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends LinearAssetService(roadLinkServiceImpl, eventBusImpl){
  override def roadLinkService: RoadLinkService = roadLinkServiceImpl
  override def dao: PostGISLinearAssetDao = new PostGISLinearAssetDao()
  override def municipalityDao: MunicipalityDao = new MunicipalityDao
  override def eventBus: DigiroadEventBus = eventBusImpl
  override def polygonTools: PolygonTools = new PolygonTools()
  override def assetDao: PostGISAssetDao = new PostGISAssetDao

  override def create(newLinearAssets: Seq[NewLinearAsset], typeId: Int, username: String, timeStamp: Long = createTimeStamp()): Seq[Long] = {
    withDynTransaction {
      val roadlinks = roadLinkService.getRoadLinksAndComplementariesByLinkIds(newLinearAssets.map(_.linkId).toSet, false)
      newLinearAssets.flatMap{ newAsset =>
        if (newAsset.value == NumericValue(1)) {
          Some(createWithoutTransaction(typeId, newAsset.linkId, newAsset.value, newAsset.sideCode, Measures(newAsset.startMeasure, newAsset.endMeasure), username, timeStamp, roadlinks.find(_.linkId == newAsset.linkId)))
        } else {
          None
        }
      }
    }
  }

  override def updateWithoutTransaction(ids: Seq[Long], value: Value, username: String, timeStamp: Option[Long] = None, sideCode: Option[Int] = None, measures: Option[Measures] = None, informationSource: Option[Int] = None): Seq[Long] = {
    if (ids.isEmpty)
      return ids

    val updatedAssets = ids.flatMap { id =>
      val oldLinearAsset = dao.fetchLinearAssetsByIds(Set(id), LinearAssetTypes.numericValuePropertyId).head
      val roadLink = roadLinkService.fetchNormalOrComplimentaryRoadLinkByLinkId(oldLinearAsset.linkId).getOrElse(throw new IllegalStateException("Road link no longer available"))
      val measure = measures.getOrElse(Measures(oldLinearAsset.startMeasure, oldLinearAsset.endMeasure))

      value match {
        case NumericValue(intValue) =>
          if (intValue == 0 && measures.nonEmpty) {
            dao.updateExpiration(id)

            Seq(Measures(oldLinearAsset.startMeasure, measure.startMeasure), Measures(measure.endMeasure, oldLinearAsset.endMeasure)).flatMap {
              m =>
                if (m.endMeasure - m.startMeasure > 0.01)
                  Some(createWithoutTransaction(oldLinearAsset.typeId, oldLinearAsset.linkId, NumericValue(1), sideCode.getOrElse(oldLinearAsset.sideCode), m, username, timeStamp.getOrElse(createTimeStamp()), Some(roadLink), true, oldLinearAsset.createdBy, Some(oldLinearAsset.createdDateTime.getOrElse(DateTime.now())), Some(username), verifiedBy = oldLinearAsset.verifiedBy, informationSource = None))
                else
                  None
            }
          }
          else  {
            Some(createWithoutTransaction(oldLinearAsset.typeId, oldLinearAsset.linkId, value, sideCode.getOrElse(oldLinearAsset.sideCode),
             measure, username, timeStamp.getOrElse(createTimeStamp()), Some(roadLink), true, oldLinearAsset.createdBy, Some(oldLinearAsset.createdDateTime.getOrElse(DateTime.now())), informationSource = None))
          }
        case _ => Some(id)
      }
    }
    if(updatedAssets.isEmpty)
      ids.foreach(dao.updateExpiration(_, true, username))

    updatedAssets
  }

  override def update(ids: Seq[Long], value: Value, username: String, timeStamp: Option[Long] = None, sideCode: Option[Int] = None, measures: Option[Measures] = None): Seq[Long] = {
    withDynTransaction {
      updateWithoutTransaction(ids, value, username, timeStamp, sideCode, measures)
    }
  }
}
