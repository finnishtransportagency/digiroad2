package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.DigiroadEventBus
import fi.liikennevirasto.digiroad2.linearasset.{AssetFiller, OneWayAssetFiller}
import fi.liikennevirasto.digiroad2.service.RoadLinkService

class ParkingProhibitionService(roadLinkServiceImpl: RoadLinkService, eventBusImpl: DigiroadEventBus) extends DynamicLinearAssetService(roadLinkServiceImpl, eventBusImpl) {
  override def assetFiller: AssetFiller = new OneWayAssetFiller
}
