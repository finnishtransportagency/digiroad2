package fi.liikennevirasto.digiroad2.roadaddress.oracle

import fi.liikennevirasto.digiroad2.DigiroadEventBus
import fi.liikennevirasto.digiroad2.oracle.OracleDatabase
import fi.liikennevirasto.digiroad2.util.VVHSerializer
import org.joda.time.{DateTime, DateTimeZone}
import org.slf4j.LoggerFactory

case class ChangedRoadAddress(address: RoadAddress, value: Long, createdAt: String, changeType: String)

class RoadAddressesService(val eventbus: DigiroadEventBus) {

  val logger = LoggerFactory.getLogger(getClass)

  def withDynTransaction[T](f: => T): T = OracleDatabase.withDynTransaction(f)
  def withDynSession[T](f: => T): T = OracleDatabase.withDynSession(f)

  def getChanged(startDate: DateTime): Seq[ChangedRoadAddress] = {
    val roadAddressDAO = new RoadAddressDAO()

    val roadAddresses =
      withDynTransaction {
        roadAddressDAO.getRoadNumberChangesByDate(startDate)
      }

    roadAddresses.map { roadAddress =>
      ChangedRoadAddress(
        address = roadAddress,
        value = roadAddress.roadNumber,
        createdAt = roadAddress.startDate.get.toString(),
        changeType = "Modify"
      )
    }
  }
}
