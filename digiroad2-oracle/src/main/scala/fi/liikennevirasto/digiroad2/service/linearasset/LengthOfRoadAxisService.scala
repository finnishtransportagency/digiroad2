package fi.liikennevirasto.digiroad2.service.linearasset

import fi.liikennevirasto.digiroad2.DigiroadEventBus
import fi.liikennevirasto.digiroad2.service.RoadLinkService

class LengthOfRoadAxisService(roadLinkServiceImpl: RoadLinkService,
                              eventBusImpl: DigiroadEventBus)
  extends DynamicLinearAssetService(roadLinkServiceImpl: RoadLinkService,
    eventBusImpl: DigiroadEventBus) {


}