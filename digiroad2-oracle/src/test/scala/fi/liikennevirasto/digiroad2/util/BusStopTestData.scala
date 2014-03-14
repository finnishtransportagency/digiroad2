package fi.liikennevirasto.digiroad2.util

import org.joda.time.LocalDate
import fi.liikennevirasto.digiroad2.util.AssetDataImporter.SimpleBusStop

object BusStopTestData {
  def generateTestData = {
    List(
      SimpleBusStop(2, 1, Seq(2), 17, LocalDate.now().minusYears(1), Some(LocalDate.now().plusYears(3))),
      SimpleBusStop(1, 2, Seq(3), 27, LocalDate.now().minusYears(1), Some(LocalDate.now().plusYears(3))),
      SimpleBusStop(2, 85755, Seq(2, 3), 36, LocalDate.now().minusYears(1), Some(LocalDate.now().plusYears(3))), // with national bus stop id
      SimpleBusStop(2, 4, Seq(2, 3), 37, LocalDate.now.plusYears(1), Some(LocalDate.now().plusYears(3))), // in the future
      SimpleBusStop(1, 5, Seq(2, 3, 4), 38, LocalDate.now.minusYears(2), Some(LocalDate.now().minusYears(1))), // in the past
      SimpleBusStop(1, 6, Seq(2), 42, LocalDate.now.minusYears(1), Some(LocalDate.now.plusYears(3)))) // Maps to road link with end date
  }
}

