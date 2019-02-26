package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.DigiroadEventBus
import fi.liikennevirasto.digiroad2.linearasset.{PersistedLinearAsset, RoadLinkLike, Value}
import fi.liikennevirasto.digiroad2.service.RoadLinkService
import org.joda.time.DateTime

class RoadWorkService(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends DynamicLinearAssetService(roadLinkServiceImpl, eventBusImpl) {

  override protected def createWithoutTransaction(typeId: Int, linkId: Long, value: Value, sideCode: Int, measures: Measures, username: String, vvhTimeStamp: Long, roadLink: Option[RoadLinkLike], fromUpdate: Boolean = false,
                                                  createdByFromUpdate: Option[String] = Some(""),
                                                  createdDateTimeFromUpdate: Option[DateTime] = Some(DateTime.now()), verifiedBy: Option[String] = None, informationSource: Option[Int] = None, trafficSignId: Option[Long] = None): Long = {

    //validate the data here

    super.createWithoutTransaction(typeId, linkId, value, sideCode, measures, username, vvhTimeStamp, roadLink,
      fromUpdate, createdByFromUpdate, createdDateTimeFromUpdate, verifiedBy, informationSource, trafficSignId)
  }

  override def enrichPersistedLinearAssetProperties(persistedLinearAsset: Seq[PersistedLinearAsset]): Seq[PersistedLinearAsset] = {
    val assetIds = persistedLinearAsset.map(_.id)

    if (assetIds.nonEmpty) {
      val properties = dynamicLinearAssetDao.getDatePeriodPropertyValue(assetIds.toSet, persistedLinearAsset.head.typeId)
      enrichWithProperties(properties, persistedLinearAsset)
    } else {
      Seq.empty[PersistedLinearAsset]
    }
  }

}
