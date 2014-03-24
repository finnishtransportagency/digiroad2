package fi.liikennevirasto.digiroad2.util

import org.joda.time.LocalDate
import fi.liikennevirasto.digiroad2.util.AssetDataImporter.SimpleBusStop

object BusStopTestData {
  def generateTestData = {
    List(
      SimpleBusStop(2, Some(300000), Some(1), Seq(2), 17, LocalDate.now().minusYears(1), Some(LocalDate.now().plusYears(3))),
      SimpleBusStop(1, Some(300004), Some(2), Seq(3), 27, LocalDate.now().minusYears(1), Some(LocalDate.now().plusYears(3))), // with national bus stop id
      SimpleBusStop(2, Some(300008), Some(85755), Seq(2, 3), 36, LocalDate.now().minusYears(1), Some(LocalDate.now().plusYears(3))), // with national bus stop id
      SimpleBusStop(2, Some(300003), Some(4), Seq(2, 3), 37, LocalDate.now.plusYears(1), Some(LocalDate.now().plusYears(3))), // in the future
      SimpleBusStop(1, Some(300001), Some(5), Seq(2, 3, 4), 38, LocalDate.now.minusYears(2), Some(LocalDate.now().minusYears(1))), // in the past
      SimpleBusStop(1, Some(300005), Some(6), Seq(2), 42, LocalDate.now.minusYears(1), Some(LocalDate.now.plusYears(3)))) // Maps to road link with end date
  }
}

