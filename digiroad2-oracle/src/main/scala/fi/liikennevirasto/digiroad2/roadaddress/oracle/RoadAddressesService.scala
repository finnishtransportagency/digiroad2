package fi.liikennevirasto.digiroad2.roadaddress.oracle

import fi.liikennevirasto.digiroad2.FeatureClass.TractorRoad
import fi.liikennevirasto.digiroad2.asset.CycleOrPedestrianPath
import fi.liikennevirasto.digiroad2.linearasset.RoadLink
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, RoadLinkService}
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.util.VVHSerializer
import org.joda.time.{DateTime, DateTimeZone}
import org.slf4j.LoggerFactory

case class ChangedRoadAddress(address: RoadAddress, value: Long, createdAt: String, changeType: String, link: RoadLink)

class RoadAddressesService(val eventbus: DigiroadEventBus, roadLinkServiceImplementation: RoadLinkService) {

  val logger = LoggerFactory.getLogger(getClass)

  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)

  def getChanged(startDate: DateTime): Seq[ChangedRoadAddress] = {
    val roadAddressDAO = new RoadAddressDAO()

    val roadAddresses =
      withDynTransaction {
        roadAddressDAO.getRoadNumberChangesByDate(startDate)
      }

    val roadLinks = roadLinkServiceImplementation.getRoadLinksAndComplementaryByLinkIdsFromVVH(roadAddresses.map(_.linkId).toSet)
    val roadLinksWithoutWalkways = roadLinks.filterNot(_.linkType == CycleOrPedestrianPath).filterNot(_.linkType == TractorRoad)

    roadAddresses.map { roadAddress =>
      roadLinksWithoutWalkways.find(_.linkId == roadAddress.linkId).map { roadLink =>
        ChangedRoadAddress(
          address = roadAddress,
          value = roadAddress.roadNumber,
          createdAt = roadAddress.startDate.get.toString(),
          changeType = "Modify",
          link = roadLink
        )
      }
    }
  }
}
